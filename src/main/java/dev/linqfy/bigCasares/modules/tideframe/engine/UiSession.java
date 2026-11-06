package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.ImageGlyphs;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.key.Key;

/**
 * Owner-thread session, plus one Netty fast path: rotation packets redraw the cursor as soon as
 * they arrive instead of waiting for the next server tick. Mutable cursor/render state is guarded
 * by {@link #lock}; Bukkit state is only read on the owner thread.
 */
public final class UiSession implements AutoCloseable {
    /** Rotation packets this recent mean the fast path owns sampling; ticks only revalidate. */
    private static final long FAST_PATH_FRESH_NANOS = 150_000_000L;
    /** Packets carrying the pre-reset pitch can still arrive briefly after the menu opens. */
    private static final long LEVEL_GRACE_NANOS = 400_000_000L;
    private final Object lock=new Object();
    private volatile int ping;
    private volatile long lastFastNanos;
    private volatile boolean fastPathBroken;
    private ScreenCursorInteraction.Frame lastFrame;
    private final Player player;
    private final Plugin plugin;
    private final SchedulerBridge scheduler;
    /** The last frame sent, without the cursor: what a closing slide keeps moving after the menu closed. */
    private volatile Component lastFrameContent;
    /** Closing slides still playing, by player: a menu opened meanwhile stops its player's slide. */
    private static final Map<UUID,SchedulerBridge.Ticket> OUTROS=new java.util.concurrent.ConcurrentHashMap<>();
    /** The player left: a closing slide still playing for them stops (the scheduler may never run its own end again). */
    static void cancelOutro(UUID player){var outro=OUTROS.remove(player);if(outro!=null)outro.cancel();}
    /** True while a closing slide still shows the old menu (the HUD waits for it like for a menu). */
    static boolean outroPlaying(UUID player){return OUTROS.containsKey(player);}
    /** The plugin stops: every closing slide stops. */
    static void cancelOutros(){for(var id:List.copyOf(OUTROS.keySet()))cancelOutro(id);}
    private volatile Screen screen;
    private ScreenCursorInteraction interaction;
    private final HudRenderer renderer;
    private final CameraRig camera;
    private final CameraPackets.InputLease input;
    private final PluginSettings settings;
    private final Runnable advanceFrame;
    private final Consumer<String> action;
    private final Location initial;
    private volatile long started=System.nanoTime();
    /** Native title fade-in used as the default entrance animation (also on in-session navigation). */
    static final long ENTRANCE_NANOS=200_000_000L;
    /** How long the screen's opening animation lasts (fade or slide; 0 for none). Clicks wait for it. */
    static long entranceNanos(Screen screen) {
        var open=screen.logic().animation().open();
        return open.effect().equals("none")?0:open.durationMs()*1_000_000L;
    }
    /** The native title fade-in of the screen's opening (0 for a slide or none: a moving title cannot fade). */
    static long fadeNanos(Screen screen) {
        var open=screen.logic().animation().open();
        return open.effect().equals("fade")?open.durationMs()*1_000_000L:0;
    }
    /** Sideways offset (logical px times the presentation scale) of an opening slide, {@code ageNanos} after it began. */
    static int slideUnits(Screen screen,long ageNanos) {
        return screen.logic().animation().open().offset(ageNanos/1_000_000.0)*screen.presentation().scale();
    }
    /** Sideways offset of a closing slide {@code ageNanos} after the menu closed (0 for a fade or none). */
    static int exitUnits(Screen screen,long ageNanos) {
        return screen.logic().animation().close().exitOffset(ageNanos/1_000_000.0)*screen.presentation().scale();
    }
    /** The whole title moved sideways by {@code units} with zero net advance (it stays balanced and centred). */
    static Component shifted(Component content,int units,int quarters) {
        if(units==0)return content;
        Key hud=Key.key(HudAssets.fontName(quarters));
        return Component.text(HudAssets.hudSpace(units)).font(hud).append(content).append(Component.text(HudAssets.hudSpace(-units)).font(hud));
    }
    private volatile boolean entranceSent;
    private volatile long entranceAt;
    /** Brief "pressed" flash after an activated click: the hovered button shows its normal frame. */
    private volatile long pressedUntil;
    private final int displayQuarters;
    /** Owner-thread hooks for audible feedback; no-ops by default. */
    public interface Feedback { void hover(); void click(); }
    private volatile Feedback feedback=new Feedback(){public void hover(){} public void click(){}};
    private String soundedHover;
    private final java.util.concurrent.atomic.AtomicInteger rotationPackets=new java.util.concurrent.atomic.AtomicInteger();
    private volatile int rotationRate;
    /** Owner thread resolves live text; any thread reads the latest immutable values. */
    public interface LiveResolver { Map<String,String> resolve(Screen screen); }
    private volatile LiveResolver liveResolver=screen->Map.of();
    private volatile Map<String,String> liveValues=Map.of();
    private int liveTicks;
    /** Games (timers, prompts) need live values every tick, not 4 times a second. */
    private volatile java.util.function.BooleanSupplier liveEveryTick=()->false;
    public void setLiveEveryTick(java.util.function.BooleanSupplier value) {liveEveryTick=Objects.requireNonNull(value);}
    /** Owner thread: clicks on no button go here first (games); return true to consume. */
    private volatile java.util.function.BooleanSupplier emptyClick=()->false;
    public void setEmptyClick(java.util.function.BooleanSupplier value) {emptyClick=Objects.requireNonNull(value);}
    private long rateWindowStart=System.nanoTime();
    private final BossBar boss=BossBar.bossBar(Component.text("TIDEFRAME | LEFT CLICK TO SELECT | SNEAK TO CLOSE").font(Key.key("tideframe:ui")),1,BossBar.Color.GREEN,BossBar.Overlay.PROGRESS);
    private SchedulerBridge.Ticket task;
    private volatile String hover;
    private volatile PlaneProjection.Point cursor=new PlaneProjection.Point(0,0);
    private final AtomicBoolean closed=new AtomicBoolean();
    private long lastClick,lastRightClick;
    /** Runtime layers (shown/hidden, offsets, tints, animations, list rows); replaced whole on the owner thread. */
    private volatile ViewState view;
    public enum Click { LEFT, RIGHT }
    /**
     * Owner thread: what a click or wheel step does. {@code target} is an element id, a list row ("id#2") or null
     * (empty space); {@code legacyAction} is the element's schema 1-8 action (permission rules applied).
     */
    public interface Router {
        void click(Screen screen,String target,Click type,String legacyAction);
        void scroll(Screen screen,String target,int direction);
    }
    private volatile Router router;
    public void setRouter(Router value) {router=value;}
    /** Owner thread: called with (previous, current) whenever the hovered target changes; either may be null. */
    private volatile java.util.function.BiConsumer<String,String> hoverListener=(previous,current)->{};
    public void setHoverListener(java.util.function.BiConsumer<String,String> value) {hoverListener=Objects.requireNonNull(value);}
    private String notifiedHover;
    /** Owner thread: Q, F and L seen on the network while this menu is open. */
    private volatile Consumer<String> keyListener=key->{};
    public void setKeyListener(Consumer<String> value) {keyListener=Objects.requireNonNull(value);}
    public UiSession(Plugin plugin,SchedulerBridge scheduler,Player player,Screen screen,PluginSettings settings,Consumer<String> action,ImageGlyphs images,HudAssets hudAssets) {
        this(plugin,scheduler,player,screen,settings,action,images,hudAssets,()->{},()->"");
    }
    public UiSession(Plugin plugin,SchedulerBridge scheduler,Player player,Screen screen,PluginSettings settings,Consumer<String> action,ImageGlyphs images,HudAssets hudAssets,Runnable advanceFrame,Supplier<String> underlay) {
        this(plugin,scheduler,player,screen,settings,action,images,hudAssets,advanceFrame,underlay,4);
    }
    public UiSession(Plugin plugin,SchedulerBridge scheduler,Player player,Screen screen,PluginSettings settings,Consumer<String> action,ImageGlyphs images,HudAssets hudAssets,Runnable advanceFrame,Supplier<String> underlay,int displayQuarters) {
        this(plugin,scheduler,player,screen,settings,action,images,hudAssets,advanceFrame,underlay,displayQuarters,null);
    }
    public UiSession(Plugin plugin,SchedulerBridge scheduler,Player player,Screen screen,PluginSettings settings,Consumer<String> action,ImageGlyphs images,HudAssets hudAssets,Runnable advanceFrame,Supplier<String> underlay,int displayQuarters,Function<String,Component> composition) {
        this(plugin,scheduler,player,screen,settings,action,images,hudAssets,advanceFrame,underlay,displayQuarters,composition,null);
    }
    /** {@code model}: a client-only Mannequin centred in front of the camera (experimental.mannequin-test), or null. */
    public UiSession(Plugin plugin,SchedulerBridge scheduler,Player player,Screen screen,PluginSettings settings,Consumer<String> action,ImageGlyphs images,HudAssets hudAssets,Runnable advanceFrame,Supplier<String> underlay,int displayQuarters,Function<String,Component> composition,CameraPackets.Model model) {
        this.model=model;
        ViewportFit.font(displayQuarters);
        this.displayQuarters=displayQuarters;
        Function<String,Component> frameComposition=composition==null?glyphs->HudText.layer(hudAssets,glyphs,displayQuarters,UiSession.this.screen):composition;
        ImagePolicy.requireKnown(screen,images);
        hudAssets.requireKnown(screen);
        this.player=player;this.plugin=plugin;this.scheduler=scheduler;this.screen=screen;this.action=action;
        // A new menu takes the title over from a closing slide that is still playing.
        var outro=OUTROS.remove(player.getUniqueId());
        if(outro!=null)outro.cancel();
        this.settings=Objects.requireNonNull(settings);this.advanceFrame=Objects.requireNonNull(advanceFrame);
        // Level the view first: a player looking far up/down is near the +-90 pitch limit and
        // could not move the cursor to the matching edge.
        if(Math.abs(player.getLocation().getPitch())>1) player.setRotation(player.getLocation().getYaw(),0);
        initial=player.getEyeLocation();
        view=ViewState.initial(screen,System.nanoTime());
        var targets=view.targets(screen);
        interaction=new ScreenCursorInteraction(initial.getYaw(),initial.getPitch(),targets,settings.predictionMs(),settings.snapRadius());
        interaction.setSensitivity(pageSensitivity(1,screen));
        ping=Math.max(0,player.getPing());
        renderer=new HudRenderer(hudAssets,screen,new HudRenderer.Channel() {
            public void show(String glyphs) {show(glyphs,"");}
            public void show(String canvas,String cursorGlyphs) {
                long now=System.nanoTime();
                boolean entrance=!entranceSent;
                var shownScreen=UiSession.this.screen;var shownView=view;
                long fade=fadeNanos(shownScreen);
                // While the native fade-in runs, a new title packet would restart it: hold updates.
                if(!entrance && now-entranceAt<fade)return;
                // Canvas, then runtime layers and live text, then the cursor on top of everything.
                Component frame=frameComposition.apply(canvas)
                    .append(DynamicLayer.build(hudAssets,shownScreen,shownView,shownHover(now),now,displayQuarters))
                    .append(LiveLayer.build(hudAssets,shownScreen,liveValues,displayQuarters,shownView));
                lastFrameContent=frame;
                Component content=frame.append(cursorGlyphs.isEmpty()?Component.empty():HudText.layer(hudAssets,cursorGlyphs,displayQuarters,shownScreen));
                // An opening slide moves everything sideways: the whole title stays balanced (zero net advance).
                // No game drop shadow: it drew a dimmed copy of every glyph (text and button sprites) offset behind the
                // menu. Set on the root, so every layer inherits it; screens draw their own shadows when they want one.
                content=Shadow.off(shifted(content,slideUnits(shownScreen,now-started),displayQuarters));
                player.showTitle(Title.title(content,Component.empty(),
                    Title.Times.times(entrance&&fade>0?Duration.ofNanos(fade):Duration.ZERO,Duration.ofMillis(750),Duration.ZERO)));
                if(entrance){entranceSent=true;entranceAt=now;}
            }
            public void clear() {
                // A fade-out close lets the client fade the last frame on its own; the player is free at once.
                var close=UiSession.this.screen.logic().animation().close();
                if(close.effect().equals("slide")&&close.durationMs()>0&&lastFrameContent!=null&&plugin.isEnabled())
                    slideOut(UiSession.this.screen,lastFrameContent);
                else if(close.effect().equals("fade")&&close.durationMs()>0)
                    player.sendTitlePart(net.kyori.adventure.title.TitlePart.TIMES,Title.Times.times(Duration.ZERO,Duration.ZERO,Duration.ofMillis(close.durationMs())));
                else player.clearTitle();
            }
        },underlay);
        renderer.setLiveKey(()->{long now=System.nanoTime();
            return liveValues.toString()+'\u0004'+DynamicLayer.key(hudAssets,UiSession.this.screen,view,shownHover(now),now)
                +'\u0005'+slideUnits(UiSession.this.screen,now-started);});
        renderer.setQuarters(displayQuarters);
        input=CameraPackets.installInput(player,new CameraPackets.InputCallbacks(()->scheduler.entity(player,this::click),
            ()->{noteRightClick();scheduler.entity(player,this::rightClick);},key->scheduler.entity(player,()->{if(!closed.get())keyListener.accept(key);})),this::onRotation);
        camera=SessionCleanup.openCamera(()->{
            if(model==null)return CameraPackets.channel(player);
            var channel=CameraPackets.channel(player,model);modelChannel=channel;spinYaw=ModelPlacement.facing(initial.getYaw());return channel;
        },input);
        try {
            // No vanilla boss bar: menus fill the screen and carry their own hints (it used to cover the top edge).
            task=scheduler.repeat(player,this::tick);
        } catch(RuntimeException | Error failure) {
            try {SessionCleanup.close(()->{if(task!=null)task.cancel();},camera,input,renderer,()->player.hideBossBar(boss));}
            catch(RuntimeException | Error cleanup) {if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    private void tick() {
        if(closed.get())return;
        if(!player.isOnline()){closeInvalid();return;}
        SessionCleanup.advanceFrame(advanceFrame,this::closeInvalid);
        spinModel();
        long now=System.nanoTime();
        ping=Math.max(0,player.getPing());
        if(now-rateWindowStart>=1_000_000_000L){rotationRate=rotationPackets.getAndSet(0);rateWindowStart=now;}
        if(++liveTicks%5==0||liveEveryTick.getAsBoolean()||screen.elements().stream().anyMatch(dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText::isBar))refreshLive();
        if (refreshInput(now).isEmpty()) return;
        synchronized(lock) {renderer.frame(cursor,shownHover(now),progress(now));}
        String current=hover;
        if(current!=null && !current.equals(soundedHover) && entranceComplete(screen,now-started)) feedback.hover();
        soundedHover=current;
        if(!Objects.equals(current,notifiedHover)){String previous=notifiedHover;notifiedHover=current;hoverListener.accept(previous,current);}
    }
    private final CameraPackets.Model model;
    private volatile CameraPackets.ModelChannel modelChannel;
    private float spinYaw;
    /** Degrees the spinning model turns per tick: one turn every 3 seconds. */
    static final float SPIN_DEGREES=6;
    /** True when this session was opened with a model (it never navigates to another screen in place). */
    public boolean hasModel() {return model!=null;}
    /** Why the requested model is not shown (null when it is, or none was requested). */
    public String modelProblem() {var channel=modelChannel;return model==null||channel==null?null:channel.problem();}
    /** Owner thread: one step of the spinning model. A failed send stops the spin, never the menu. */
    private void spinModel() {
        var channel=modelChannel;
        if(model==null||!model.spin()||channel==null||channel.problem()!=null)return;
        spinYaw=ModelPlacement.wrap(spinYaw+SPIN_DEGREES);
        try {channel.turn(spinYaw);}
        catch(Exception failure) {modelChannel=null;plugin.getLogger().warning("TIDEFRAME_MODEL spin stopped: "+failure);}
    }
    /**
     * Closing slide: the last frame (without the cursor) keeps moving sideways every tick until the slide's duration has
     * passed, then the title is cleared. The camera, input and the session itself are already released.
     */
    private void slideOut(Screen closing,Component frame) {
        long begin=System.nanoTime();
        long duration=closing.logic().animation().close().durationMs()*1_000_000L;
        var id=player.getUniqueId();
        var ticket=new java.util.concurrent.atomic.AtomicReference<SchedulerBridge.Ticket>();
        Runnable stop=()->{var own=ticket.get();if(own!=null){own.cancel();OUTROS.remove(id,own);}};
        SchedulerBridge.Ticket started;
        try {
            started=scheduler.repeat(player,()->{
                long age=System.nanoTime()-begin;
                if(age>=duration||!player.isOnline()){stop.run();player.clearTitle();return;}
                player.showTitle(Title.title(Shadow.off(shifted(frame,exitUnits(closing,age),displayQuarters)),Component.empty(),
                    Title.Times.times(Duration.ZERO,Duration.ofMillis(750),Duration.ZERO)));
            });
        } catch(RuntimeException unavailable) {player.clearTitle();return;}
        ticket.set(started);
        var previous=OUTROS.put(id,started);
        if(previous!=null)previous.cancel();
    }
    /** Owner thread: changes the runtime layers (and the click targets they cover) and redraws. */
    public void updateView(java.util.function.UnaryOperator<ViewState> change) {
        if(closed.get())return;
        synchronized(lock) {
            var before=view;var next=Objects.requireNonNull(change.apply(before));
            if(next==before)return;
            var targets=next.targets(screen);
            if(!targets.equals(before.targets(screen))){interaction.replaceTargets(targets);lastFrame=null;}
            view=next;
            long now=System.nanoTime();
            renderer.frame(cursor,shownHover(now),progress(now));
        }
    }
    public ViewState view() {return view;}
    public Screen screen() {return screen;}
    /** The hovered target (element id or list row "id#2"), or null. */
    public String hover() {return hover;}
    public void setFeedback(Feedback value) {feedback=Objects.requireNonNull(value);}
    public void setLiveResolver(LiveResolver value) {liveResolver=Objects.requireNonNull(value);refreshLive();}
    /** Owner thread. Screens with runtime layers resolve too: their resolver also re-checks visible-if conditions. */
    private void refreshLive() {
        var current=screen;
        var next=current.elements().stream().anyMatch(dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText::isDynamic)||!view.runtime().isEmpty()
            ?Map.copyOf(liveResolver.resolve(current)):Map.<String,String>of();
        if(!next.equals(liveValues))liveValues=next;
    }
    /** Owner thread: variables or list data changed, recompute live text now instead of at the next refresh. */
    public void refreshLiveNow() {
        if(closed.get())return;
        refreshLive();
        long now=System.nanoTime();
        synchronized(lock) {renderer.frame(cursor,shownHover(now),progress(now));}
    }
    /** Owner thread: return false to suppress a button's action (screen id, element id, action). */
    public interface ClickObserver { boolean allow(String screenId,String elementId,String action); }
    private volatile ClickObserver clickObserver=(s,e,a)->true;
    public void setClickObserver(ClickObserver value) {clickObserver=Objects.requireNonNull(value);}
    /** The player's own cursor sensitivity; each page's cursorSpeed multiplies it. */
    private volatile double playerSensitivity=1;
    public void setSensitivity(double value) {playerSensitivity=value;applySensitivity(screen);}
    /** Sensitivity on {@code page}: the player's own times the page's cursorSpeed, kept inside the allowed range. */
    static double pageSensitivity(double player,Screen page) {
        return Math.max(ScreenCursorInteraction.MIN_SENSITIVITY,Math.min(ScreenCursorInteraction.MAX_SENSITIVITY,player*page.presentation().cursorSpeed()));
    }
    private void applySensitivity(Screen page) {synchronized(lock){interaction.setSensitivity(pageSensitivity(playerSensitivity,page));}}
    /** One line for /tideframe debug. */
    public String debugLine() {
        return "PING "+ping+"MS | MOUSE "+rotationRate+"/S | FAST "+(fastPathBroken||!CameraPackets.rotationFastPath()?"OFF":"ON")
            +" | SIZE "+ViewportFit.label(displayQuarters)+" | "+screen.id().toUpperCase(java.util.Locale.ROOT);
    }
    private String shownHover(long now) {return now<pressedUntil?null:hover;}
    public int displayQuarters() {return displayQuarters;}
    /** Owner thread. Switch to another prepared menu inside this session: no camera/input reopen, entrance replays. */
    public void navigate(Screen next) {
        if(closed.get())return;
        replaceScreen(next);
        refreshLive();
        synchronized(lock) {started=System.nanoTime();pressedUntil=0;entranceSent=false;}
    }
    private double progress(long now) {return Math.min(1,(now-started)/400_000_000.0);}
    private boolean fastPathFresh(long now) {return !fastPathBroken && now-lastFastNanos<FAST_PATH_FRESH_NANOS;}
    /** Validates on the owner thread; samples the server-side pose only when no fresh packet did. */
    private Optional<ScreenCursorInteraction.Frame> refreshInput(long now) {
        if (closed.get()) return Optional.empty();
        if (!player.isOnline() || !player.hasPermission("tideframe.use") || player.isDead() || player.isInsideVehicle()) {
            closeInvalid();return Optional.empty();
        }
        Location eye=player.getEyeLocation();
        if (!Objects.equals(player.getWorld(),initial.getWorld())
                || !Objects.equals(eye.getWorld(),initial.getWorld())) {
            closeInvalid();return Optional.empty();
        }
        synchronized(lock) {
            if(closed.get()) return Optional.empty();
            var frame=fastPathFresh(now)&&lastFrame!=null?interaction.current(now,ping)
                :sample(eye.getYaw(),eye.getPitch(),now);
            return Optional.of(frame);
        }
    }
    /** Lock held. */
    private ScreenCursorInteraction.Frame sample(double yaw,double pitch,long now) {
        if(now-started<LEVEL_GRACE_NANOS && Math.abs(pitch-initial.getPitch())>2) interaction.rebase(yaw,pitch);
        var frame=interaction.refresh(yaw,pitch,now,ping);
        lastFrame=frame;cursor=frame.cursor();hover=frame.hover().orElse(null);
        return frame;
    }
    /** Netty event loop. No Bukkit state; sending a title packet is thread-safe. */
    private void onRotation(float yaw,float pitch) {
        if(closed.get()||fastPathBroken||!Float.isFinite(yaw)||!Float.isFinite(pitch))return;
        try {
            synchronized(lock) {
                if(closed.get())return;
                long now=System.nanoTime();
                sample(yaw,Math.max(-90,Math.min(90,pitch)),now);
                lastFastNanos=now;rotationPackets.incrementAndGet();
                renderer.frame(cursor,shownHover(now),progress(now));
            }
        } catch(Throwable failure) {fastPathBroken=true;} // The owner tick keeps the menu working.
    }
    /** Owner thread: the session closed itself (death, permission, world change, vehicle); defaults to the "close" action. */
    private volatile Runnable invalidListener;
    public void setInvalidListener(Runnable value) {invalidListener=Objects.requireNonNull(value);}
    private void closeInvalid() {
        // Cleanup itself must not depend on the caller retaining interaction permission.
        var listener=invalidListener;
        SessionCleanup.run(this::close,() -> {if(listener!=null)listener.run();else action.accept("close");});
    }
    public void click() {press(Click.LEFT);}
    public void rightClick() {press(Click.RIGHT);}
    /**
     * A right click the client takes as done (an entity or item used) makes it swing the arm right after, and a swing
     * is otherwise a left click: without this, one right click in a shop sold and then bought. A swing that follows a
     * right click within {@link SwingFilter#WINDOW_NANOS} is that echo, not a click.
     */
    private final SwingFilter swings=new SwingFilter(System::nanoTime);
    /** Any thread, as the right click arrives (before it is scheduled): the next swing may be its echo. */
    public void noteRightClick() {swings.rightClick();}
    /**
     * Any thread, as the swing arrives: true when it is a left click (schedule {@link #click()}), false when it echoes a
     * right click. Decided on arrival, never when the scheduled task runs, so a slow queue cannot turn an echo into a click.
     */
    public boolean swingIsClick() {return swings.isClick();}
    private void press(Click type) {
        long now=System.nanoTime();
        var frame=refreshInput(now);
        if (frame.isEmpty()) return;
        synchronized(lock) {renderer.frame(cursor,shownHover(now),progress(now));}
        var selected=frame.get().selection();
        if(!entranceComplete(screen,now-started))return;
        var current=screen;var route=router;
        if(type==Click.LEFT) {
            if (selected.isEmpty() && now-lastClick>=120_000_000 && emptyClick.getAsBoolean()) {lastClick=now;return;}
            if (now-lastClick<200_000_000) return;
            if (selected.isEmpty()) {if(route!=null){lastClick=now;route.click(current,null,type,null);}return;}
            lastClick=now;
        } else {
            if (now-lastRightClick<200_000_000) return;
            lastRightClick=now;
            if (selected.isEmpty()) {if(route!=null)route.click(current,null,type,null);return;}
        }
        pressedUntil=now+90_000_000L;
        feedback.click();
        synchronized(lock) {renderer.frame(cursor,null,progress(now));}
        String target=selected.get();
        current.element(ListRows.template(target)).ifPresent(e -> {
            String resolved=MessageRules.action(e,player::hasPermission);
            if(route!=null){route.click(current,target,type,resolved);return;}
            if(type==Click.LEFT && clickObserver.allow(current.id(),e.id(),resolved)) action.accept(resolved);
        });
    }
    /** Owner thread: keyboard navigation (W A S D) moves the cursor onto the nearest button that way; false when there is none. */
    public boolean navigate(int dx,int dy) {
        long now=System.nanoTime();
        if(refreshInput(now).isEmpty())return false;
        synchronized(lock) {
            var frame=interaction.focus(dx,dy,now,ping);
            if(frame.isEmpty())return false;
            lastFrame=frame.get();cursor=lastFrame.cursor();hover=lastFrame.hover().orElse(null);
            renderer.frame(cursor,shownHover(now),progress(now));
            return true;
        }
    }
    /** Owner thread: one mouse-wheel step (+1 down, -1 up) over whatever the cursor hovers. */
    public void scroll(int direction) {
        if(closed.get())return;
        var route=router;
        if(route!=null)route.scroll(screen,hover,direction);
    }
    public PlaneProjection.Point cursor() { return cursor; }
    /** Owner thread only. Runtime frames must have been baked into the immutable resource pack. */
    public void replaceScreen(Screen next) {
        if(closed.get())return;
        Objects.requireNonNull(next);
        synchronized(lock) {
            renderer.replaceScreen(next);
            var fresh=ViewState.initial(next,System.nanoTime());
            interaction.replaceTargets(fresh.targets(next));
            screen=next;view=fresh;hover=null;lastFrame=null;notifiedHover=null;
            interaction.setSensitivity(pageSensitivity(playerSensitivity,next));
        }
    }
    /** Clicks wait for the opening animation so a stray click cannot hit a button the player has not seen yet. */
    static boolean entranceComplete(Screen screen,long ageNanos) {
        return ageNanos >= entranceNanos(screen);
    }
    @Override public void close() {
        if (!closed.compareAndSet(false,true)) return;
        synchronized(lock) { } // Let an in-flight Netty redraw finish before the title is cleared.
        SessionCleanup.close(() -> {if(task!=null)task.cancel();},camera,input,renderer,() -> player.hideBossBar(boss));
    }
    public void abandonOnShutdown() {
        if (!closed.compareAndSet(false,true)) return;
        SessionCleanup.abandon(() -> {if(task!=null)task.cancel();},camera,input,renderer);
        // Folia has already disabled plugin scheduling here. Do not mutate the
        // remote player from the global shutdown thread; hot-disable needs acceptance.
    }
}
