package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.PassiveHudState;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/**
 * Player-owner HUD. No camera, mount, input hook or movement lock is created. One main HUD page, and since 0.23 more
 * pages drawn over it: HUD pages added on top of the others (presentation "stack", the world's HUD list, the API) and
 * toasts, which leave on their own after a while. Each page keeps its own runtime layers and live text; a toast slides
 * in and out with its page's open and close animation.
 */
final class PassiveHudSession implements AutoCloseable {
    /** Most pages drawn over the main one at once. */
    static final int MAX_EXTRAS = 6;
    private final Player player;
    private final HudAssets assets;
    private final BooleanSupplier menuActive;
    private final PassiveHudState state=new PassiveHudState();
    private final BossBar boss=BossBar.bossBar(Component.empty(),0,BossBar.Color.RED,BossBar.Overlay.NOTCHED_20);
    private final SchedulerBridge.Ticket task;
    private final java.util.function.LongSupplier clock;
    private HudRenderer renderer;
    // Read by a menu's Netty cursor redraw when composing the underlay.
    private volatile String canvas="";
    private volatile boolean closed;
    private boolean bossShown;
    /** config.yml boss-bar: the bar flashes and / or shakes for a moment when the boss loses health. */
    private volatile boolean bossFlash,bossShake;
    /** Owner thread: the boss health last drawn, and ticks since the last hit (-1: none). */
    private float bossDrawn=-1;private int bossHit=-1;
    void setBossEffects(boolean flash,boolean shake){bossFlash=flash;bossShake=shake;}
    private int ticks;
    /** Owner thread: clock of the last tick (pages over the HUD wait while a menu covers it). */
    private long lastTick;
    /** Owner thread: a menu or its closing slide covered the HUD at the last tick. */
    private boolean wasCovered;
    private final ViewportFit viewport;
    private volatile int displayMultiplier=4; // quarters
    /** Pages over the main one, in drawing order (later ones on top). */
    private final CopyOnWriteArrayList<Extra> extras=new CopyOnWriteArrayList<>();
    /** Told when a page leaves by itself (a toast whose time is up, after its closing slide). */
    private volatile Consumer<String> expired=id->{};

    /** A page over the main one. {@code leavesAt}: nanoTime its closing starts (Long.MAX_VALUE: stays). */
    private final class Extra {
        final Screen screen;final int quarters;final String canvas;volatile long openedAt,leavesAt;
        volatile ViewState view;volatile Map<String,String> live=Map.of();
        Extra(Screen screen,long now,long leavesAt){
            this.screen=screen;this.quarters=quartersOf(screen);
            this.canvas=assets.frame(screen,null,8,quarters);this.openedAt=now;this.leavesAt=leavesAt;this.view=ViewState.initial(screen,now);
        }
        /** A menu covered the HUD for {@code nanos}: the page's slide and its time wait, so the player sees all of it. */
        void hold(long nanos){openedAt+=nanos;if(leavesAt!=Long.MAX_VALUE)leavesAt+=nanos;}
        /** Sideways offset (logical px) of the page's slide: in when opening, out when its time is up. */
        int offset(long now){
            var animation=screen.logic().animation();
            if(now-leavesAt>=0)return animation.close().exitOffset((now-leavesAt)/1e6);
            return animation.open().offset((now-openedAt)/1e6);
        }
        /** True once a page with a time limit has finished leaving. */
        boolean gone(long now){
            if(leavesAt==Long.MAX_VALUE||now-leavesAt<0)return false;
            var close=screen.logic().animation().close();
            return !close.effect().equals("slide")||(now-leavesAt)/1e6>=close.durationMs();
        }
        Component title(long now){
            int dx=offset(now)*screen.presentation().scale();
            Component body=HudText.layer(assets,canvas,quarters,screen).append(DynamicLayer.build(assets,screen,view,null,now,quarters))
                .append(LiveLayer.build(assets,screen,live,quarters,view));
            if(dx==0)return body;
            Key font=Key.key(ViewportFit.font(quarters));
            return Component.text(assets.displayText(HudAssets.hudSpace(dx),quarters)).font(font).append(body)
                .append(Component.text(assets.displayText(HudAssets.hudSpace(-dx),quarters)).font(font));
        }
        String key(long now){return screen.id()+'\u0005'+offset(now)+'\u0004'+live+'\u0004'+DynamicLayer.key(assets,screen,view,null,now);}
    }

    PassiveHudSession(SchedulerBridge scheduler,Player player,HudAssets assets,BooleanSupplier menuActive,Runnable invalid) {
        this(scheduler,player,assets,menuActive,invalid,null);
    }
    PassiveHudSession(SchedulerBridge scheduler,Player player,HudAssets assets,BooleanSupplier menuActive,Runnable invalid,ViewportFit viewport) {
        this(work->scheduler.repeat(player,work),System::nanoTime,player,assets,menuActive,invalid,viewport);
    }
    /** {@code ticker} runs the given work every tick; {@code clock} gives nanoTime (tests drive both themselves). */
    PassiveHudSession(java.util.function.Function<Runnable,SchedulerBridge.Ticket> ticker,java.util.function.LongSupplier clock,Player player,HudAssets assets,
                      BooleanSupplier menuActive,Runnable invalid,ViewportFit viewport) {
        this.player=player;this.assets=assets;this.menuActive=menuActive;
        this.viewport=viewport;this.clock=clock;lastTick=clock.getAsLong();
        task=ticker.apply(()->{
            if(closed)return;
            if(!player.isOnline()||player.isDead()||!player.hasPermission("tideframe.use")){invalid.run();return;}
            // While a menu covers the HUD the pages over it wait: a toast sent from a menu slides in and counts its time once seen.
            long now=clock.getAsLong();boolean covered=menuActive.getAsBoolean();
            if(covered)for(var extra:extras)extra.hold(Math.max(0,Math.min(now-lastTick,now-extra.openedAt)));
            lastTick=now;
            expire();
            if(renderer!=null&&!covered)renderer.passiveFrame(sliding()||wasCovered);
            wasCovered=covered;
            var shown=screen;
            // Bars animate (damage trail) every tick; text (and visible-if conditions) refresh 4 times a second.
            boolean bars=shown!=null&&shown.elements().stream().anyMatch(dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText::isBar)
                ||extras.stream().anyMatch(extra->extra.screen.elements().stream().anyMatch(dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText::isBar));
            if(++ticks%5==0||bars)refreshLive();
            if(ticks%20==0)refreshValues();
            // A hit plays out tick by tick, then the bar is drawn plain again.
            if(bossHit>=0&&bossShown){
                bossHit=bossHit+1>=EpicBossPresentation.HIT_TICKS?-1:bossHit+1;
                var value=state.snapshot();
                if(value.bossVisible())EpicBossPresentation.apply(boss,value,bossHit,bossFlash,bossShake);
            }
        });
    }
    PassiveHudState state(){return state;}
    boolean closed(){return closed;}
    String glyphs(){return closed?"":canvas;}
    Component layer(){
        if(closed)return Component.empty();
        var shown=screen;long now=clock.getAsLong();
        Component out=main(canvas,shown);
        if(shown!=null){var current=view;out=out.append(DynamicLayer.build(assets,shown,current,null,now,displayMultiplier)).append(LiveLayer.build(assets,shown,liveValues,displayMultiplier,current));}
        for(var extra:extras)out=out.append(extra.title(now));
        return out;
    }
    /** Runtime layers of the overlay (shown/hidden, offsets, tints, animations, list rows). */
    private volatile ViewState view=ViewState.EMPTY;
    ViewState view(){return view;}
    /** The runtime layers of page {@code screenId} (the main one or one over it); EMPTY when it is not shown. */
    ViewState view(String screenId){
        var shown=screen;
        if(shown!=null&&shown.id().equals(screenId))return view;
        for(var extra:extras)if(extra.screen.id().equals(screenId))return extra.view;
        return ViewState.EMPTY;
    }
    /** Owner thread. */
    void updateView(java.util.function.UnaryOperator<ViewState> change){
        if(closed)return;
        var next=java.util.Objects.requireNonNull(change.apply(view));
        if(next==view)return;
        view=next;
        if(renderer!=null&&!menuActive.getAsBoolean())renderer.passiveFrame(true);
    }
    /** Owner thread: the runtime layers of page {@code screenId}. */
    void updateView(String screenId,java.util.function.UnaryOperator<ViewState> change){
        if(closed)return;
        var shown=screen;
        if(shown!=null&&shown.id().equals(screenId)){updateView(change);return;}
        for(var extra:extras)if(extra.screen.id().equals(screenId)){
            var next=java.util.Objects.requireNonNull(change.apply(extra.view));
            if(next==extra.view)return;
            extra.view=next;
            if(renderer!=null&&!menuActive.getAsBoolean())renderer.passiveFrame(true);
            return;
        }
    }
    /** Owner thread: variables or list data changed. */
    void refreshLiveNow(){
        if(closed)return;
        if(refreshLive()&&renderer!=null&&!menuActive.getAsBoolean())renderer.passiveFrame(true);
    }
    private volatile Screen screen;
    private volatile Map<String,String> liveValues=Map.of();
    private volatile UiSession.LiveResolver liveResolver=s->Map.of();
    void setLiveResolver(UiSession.LiveResolver value){liveResolver=java.util.Objects.requireNonNull(value);}
    void setExpiryListener(Consumer<String> value){expired=java.util.Objects.requireNonNull(value);}
    /** Owner thread; returns true when the live text of any page changed. */
    private boolean refreshLive(){
        var shown=screen;boolean changed=false;
        var next=shown==null||shown.elements().stream().noneMatch(dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText::isDynamic)&&view.runtime().isEmpty()?Map.<String,String>of():Map.copyOf(liveResolver.resolve(shown));
        if(!next.equals(liveValues)){liveValues=next;changed=true;}
        for(var extra:extras){
            var values=extra.screen.elements().stream().noneMatch(dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText::isDynamic)&&extra.view.runtime().isEmpty()?Map.<String,String>of():Map.copyOf(liveResolver.resolve(extra.screen));
            if(!values.equals(extra.live)){extra.live=values;changed=true;}
        }
        return changed;
    }
    /** Ids of the pages shown: the main one first, then the ones over it. */
    List<String> screenIds(){
        var out=new java.util.ArrayList<String>();
        var shown=screen;if(shown!=null)out.add(shown.id());
        for(var extra:extras)out.add(extra.screen.id());
        return List.copyOf(out);
    }
    /** Replaces every HUD page with {@code screen} (the classic single HUD). */
    void showScreen(Screen screen) {
        if(!screen.presentation().mode().equals("overlay"))throw new IllegalArgumentException("Choose a passive overlay screen.");
        assets.requireKnown(screen);
        var gone=List.copyOf(extras);
        extras.clear();
        setMain(screen);
        reportGone(gone);
    }
    /**
     * Toasts that left some other way than by their own time (taken away, replaced) are reported like expired ones, so
     * the plugin's toast queue goes on. Called once the HUD is settled: the listener may bring the next toast in at once.
     */
    private void reportGone(List<Extra> gone){for(var extra:gone)if(extra.leavesAt!=Long.MAX_VALUE)expired.accept(extra.screen.id());}
    /** Size (quarters) a page is drawn at: the reference size when the auto-fit shader scales it, else the player's size. */
    private int quartersOf(Screen screen){
        return assets.fitted(screen)?HudAssets.REFERENCE_QUARTERS:viewport==null?4:viewport.multiplier(screen,assets.sizes());
    }
    /**
     * The main page's frame glyphs, with the auto-fit marker when {@code shown} is fitted (no main page: plain). Everything
     * else is appended to what this returns (the page's own layers, other pages, a menu over the HUD), so a fitted layer,
     * whose shadow is off, is wrapped: an enlarged page or menu after it must not inherit that.
     */
    private Component main(String glyphs,Screen shown){
        if(shown==null||!assets.fitted(shown))return HudText.layer(assets,glyphs,displayMultiplier);
        return Component.empty().append(HudText.layer(assets,glyphs,displayMultiplier,shown));
    }
    private void setMain(Screen screen){
        displayMultiplier=quartersOf(screen);
        String next=assets.frame(screen,null,8,displayMultiplier);
        view=ViewState.initial(screen,clock.getAsLong());
        this.screen=screen;refreshLive();
        if(renderer==null){renderer=new HudRenderer(assets,screen,new HudRenderer.Channel(){
            public void show(String glyphs){
                var shown=PassiveHudSession.this.screen;var current=view;long now=clock.getAsLong();
                Component content=main(glyphs,shown)
                    .append(shown==null?Component.empty():DynamicLayer.build(assets,shown,current,null,now,displayMultiplier))
                    .append(shown==null?Component.empty():LiveLayer.build(assets,shown,liveValues,displayMultiplier,current));
                for(var extra:extras)content=content.append(extra.title(now));
                player.showTitle(Title.title(content,Component.empty(),Title.Times.times(Duration.ZERO,Duration.ofMillis(750),Duration.ZERO)));
            }
            public void clear(){if(!menuActive.getAsBoolean())player.clearTitle();}
        });renderer.setLiveKey(()->{var shown=PassiveHudSession.this.screen;long now=clock.getAsLong();
            var key=new StringBuilder(liveValues.toString()).append(shown==null?"":'\u0004'+DynamicLayer.key(assets,shown,view,null,now));
            for(var extra:extras)key.append('\u0006').append(extra.key(now));
            return key.toString();});}
        else renderer.replaceScreen(screen);
        renderer.hideMain(false);
        renderer.setQuarters(displayMultiplier);
        canvas=next;refresh();
    }
    /** No main page: only the pages over it (toasts) are drawn. */
    private void hideMain(){
        this.screen=null;canvas="";view=ViewState.EMPTY;liveValues=Map.of();
        if(renderer!=null){renderer.hideMain(true);if(!menuActive.getAsBoolean())renderer.passiveFrame(true);}
    }
    /** No HUD page at all: the title is left alone (the session may stay for the balance or the boss bar). */
    private void noPages(){
        if(renderer!=null){renderer.close();renderer=null;}
        this.screen=null;canvas="";view=ViewState.EMPTY;liveValues=Map.of();
    }
    /**
     * Adds {@code screen} over the HUD pages already shown (or as the main one when none is); a page already shown is
     * shown again from its start. {@code seconds} above 0 makes it a toast that leaves by itself after that long.
     */
    void addScreen(Screen screen,double seconds){
        if(!screen.presentation().mode().equals("overlay"))throw new IllegalArgumentException("Choose a passive overlay screen.");
        assets.requireKnown(screen);
        // A toast never takes the place of a page that stays: when its time was up it would take that page away too.
        if(seconds>0&&staying(screen.id()))throw new IllegalStateException(screen.id()+" is already on the HUD to stay");
        long now=clock.getAsLong(),leavesAt=seconds>0?now+(long)(seconds*1e9):Long.MAX_VALUE;
        var shown=this.screen;
        var replaced=extras.stream().filter(extra->extra.screen.id().equals(screen.id())).toList();
        // A page that stays where there is no main page (or the main page again) becomes the main page; a toast of the
        // same page goes (reported, so its runtime closes and the queue goes on) instead of staying drawn twice.
        if(seconds<=0&&(shown==null||shown.id().equals(screen.id()))){extras.removeAll(replaced);setMain(screen);reportGone(replaced);return;}
        extras.removeAll(replaced);
        if(extras.size()>=MAX_EXTRAS)throw new IllegalStateException("At most "+(MAX_EXTRAS+1)+" HUD pages at once");
        if(renderer==null){
            // A toast with no HUD page under it: the renderer starts on it with its main canvas hidden.
            setMain(screen);hideMain();
        }
        extras.add(new Extra(screen,now,leavesAt));
        refreshLive();
        if(!menuActive.getAsBoolean())renderer.passiveFrame(true);
        reportGone(replaced);
    }
    /** Takes page {@code screenId} away. Returns true while any HUD page is still shown. */
    boolean removeScreen(String screenId){
        if(closed)return false;
        var gone=extras.stream().filter(extra->extra.screen.id().equals(screenId)).toList();
        if(!gone.isEmpty()){
            extras.removeAll(gone);
            if(!hasPages())noPages();
            else if(renderer!=null&&!menuActive.getAsBoolean())renderer.passiveFrame(true);
            reportGone(gone);
            return hasPages();
        }
        var shown=screen;
        if(shown!=null&&shown.id().equals(screenId)){
            var promoted=extras.stream().filter(extra->extra.leavesAt==Long.MAX_VALUE).findFirst().orElse(null);
            if(promoted!=null){
                extras.remove(promoted);setMain(promoted.screen);
                // Its runtime stays attached: it keeps what its actions did (offsets, visibility, tints, playback).
                view=promoted.view;
                if(renderer!=null&&!menuActive.getAsBoolean())renderer.passiveFrame(true);
            }
            else if(extras.isEmpty())noPages();
            else hideMain();
        }
        return hasPages();
    }
    boolean hasPages(){return screen!=null||!extras.isEmpty();}
    /** True when page {@code screenId} is on the HUD to stay: the main page, or one added without a time limit. */
    boolean staying(String screenId){
        var shown=screen;
        if(shown!=null&&shown.id().equals(screenId))return true;
        for(var extra:extras)if(extra.leavesAt==Long.MAX_VALUE&&extra.screen.id().equals(screenId))return true;
        return false;
    }
    /** True while a page slides, so the title follows it every tick. */
    private boolean sliding(){
        long now=clock.getAsLong();
        for(var extra:extras)if(extra.offset(now)!=0)return true;
        return false;
    }
    /** Owner thread: toasts whose time is up and whose closing slide has ended leave; the plugin is told. */
    private void expire(){
        long now=clock.getAsLong();
        for(var extra:extras)if(extra.gone(now)){
            extras.remove(extra);
            if(!hasPages())noPages();
            expired.accept(extra.screen.id());
        }
    }
    void refresh() {
        if(closed)return;
        if(renderer!=null&&!menuActive.getAsBoolean())renderer.passiveFrame(true);
        refreshValues();
    }
    private String sentBalance="";
    private void refreshValues() {
        var value=state.snapshot();
        // Only a supplied balance uses the shared action bar; it fades after ~3s, so refresh every 2s.
        if(value.balanceVisible()) {
            if(!value.balanceText().equals(sentBalance)||ticks%40==0) {
                player.sendActionBar(Component.text(value.balanceText()).font(Key.key("tideframe:ui")));sentBalance=value.balanceText();
            }
        } else if(!sentBalance.isEmpty()) {player.sendActionBar(Component.empty());sentBalance="";}
        if(value.bossVisible()){
            // A hit plays to its end: damage every tick (poison, fire) pulses every 6 ticks instead of shaking nonstop.
            if(bossShown&&bossHit<0&&value.bossProgress()<bossDrawn-1e-6f&&(bossFlash||bossShake))bossHit=0;
            bossDrawn=value.bossProgress();
            EpicBossPresentation.apply(boss,value,bossHit,bossFlash,bossShake);
            if(!bossShown){player.showBossBar(boss);bossShown=true;}
        }else if(bossShown){player.hideBossBar(boss);bossShown=false;bossDrawn=-1;bossHit=-1;}
    }
    static void showTitle(Player player,String glyphs) {
        showTitle(player,glyphs,"tideframe:hud");
    }
    static void showTitle(Player player,String glyphs,String font) {
        var content=Component.text(glyphs).font(Key.key(font)).color(NamedTextColor.WHITE)
            .decoration(TextDecoration.BOLD,false).decoration(TextDecoration.ITALIC,false);
        player.showTitle(Title.title(content,Component.empty(),Title.Times.times(Duration.ZERO,Duration.ofMillis(750),Duration.ZERO)));
    }
    @Override public void close(){
        if(closed)return;closed=true;
        SessionCleanup.run(task::cancel,()->{if(renderer!=null)renderer.close();},
            ()->{if(!sentBalance.isEmpty())player.sendActionBar(Component.empty());},()->{if(bossShown)player.hideBossBar(boss);});
    }
    void abandon(){if(closed)return;closed=true;SessionCleanup.run(task::cancel,()->{if(renderer!=null)renderer.abandon();});}
}
