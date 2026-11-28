package dev.linqfy.bigCasares.modules.tideframe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Player input for the two BigCasares menus. They do not occupy an inventory or change game mode. */
public final class NativeMenus implements Listener {
    private final Plugin plugin;
    private final Predicate<UUID> packLoaded;
    private final Predicate<UUID> bedrock;
    private final Map<UUID,View> views = new HashMap<>();
    private BukkitTask tick;
    private long ticks;
    private static final Title.Times TIMES = Title.Times.times(Duration.ZERO, Duration.ofSeconds(6), Duration.ZERO);

    private static final class View {
        final Player player;
        Panel panel;
        Component body;
        int x=128,y=72;
        double cursorX=128,cursorY=72;
        List<Panel.Hover> highlights = List.of();
        boolean closeHover;
        boolean dirty=true;
        long lastClick=-1;
        /**
         * The game sends nothing for a right click on air with an empty hand, and the cursor follows the head, so the
         * player usually looks at the sky when right-clicking a product. An invisible interaction box around the player,
         * shown only to them, holds their eyes: the game then aims every click at it and a right click always arrives
         * as a use of that entity.
         */
        Interaction catcher;
        /** Set when the box could not be created (for example a protection cancelled it): no retry until reopened. */
        boolean catcherRefused;
        View(Player player) { this.player=player; }
    }

    public NativeMenus(Plugin plugin,Predicate<UUID> packLoaded,Predicate<UUID> bedrock) {
        this.plugin=plugin;
        this.packLoaded=packLoaded;
        this.bedrock=bedrock;
    }
    void enable() {
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        tick=plugin.getServer().getScheduler().runTaskTimer(plugin,this::update,1,1);
    }
    void disable() {
        if(tick!=null) tick.cancel();
        tick=null;
        HandlerList.unregisterAll(this);
        for(View view:List.copyOf(views.values())) close(view.player);
    }
    public boolean available(Player player) {
        return tick!=null && player!=null && player.isOnline() && !player.isDead() && !player.isInsideVehicle()
            && !bedrock.test(player.getUniqueId()) && packLoaded.test(player.getUniqueId());
    }
    public boolean current(Player player,Panel panel) {
        View view=views.get(player.getUniqueId());
        return view!=null && view.player==player && view.panel==panel && available(player);
    }
    public boolean isOpen(Player player,String group) {
        View view=views.get(player.getUniqueId());
        return view!=null && view.player==player && view.panel.group().equals(group) && available(player);
    }
    public boolean show(Player player,Panel panel) {
        if(!available(player)) return false;
        Component body=PanelText.body(panel);
        View view=views.get(player.getUniqueId());
        if(view==null) {
            player.closeInventory();
            view=new View(player);
            views.put(player.getUniqueId(),view);
            keepCatcher(view,player.getLocation());
        } else if(!view.panel.group().equals(panel.group())) {
            view.panel.closed();
        }
        view.panel=panel;
        view.body=body;
        draw(view);
        return true;
    }
    public void close(Player player) {
        View view=views.remove(player.getUniqueId());
        if(view!=null) {
            if(view.catcher!=null) view.catcher.remove();
            if(player.isOnline()) player.clearTitle();
            view.panel.closed();
        }
    }
    public void closeGroup(String group) {
        for(View v:List.copyOf(views.values())) if(v.panel.group().equals(group)) close(v.player);
    }
    private void update() {
        ticks++;
        for(View view:List.copyOf(views.values())) {
            if(!available(view.player)) { close(view.player); continue; }
            if(ticks%20==0 && (view.catcher==null || !view.catcher.isValid())) keepCatcher(view,view.player.getLocation());
            if(ticks%80==0) { draw(view); continue; }
            if(!view.dirty) continue;
            view.dirty=false;
            List<Panel.Hover> h=highlights(view);
            boolean close=PanelText.closeAt(view.x,view.y);
            if(close!=view.closeHover || !h.equals(view.highlights)) draw(view);
            else view.player.sendTitlePart(TitlePart.SUBTITLE,PanelText.cursor(view.x,view.y));
        }
    }
    /** Moves the box with the player, and creates it again if another plugin removed it or refused to move it. */
    private void keepCatcher(View v,Location feet) {
        if(v.catcher!=null && v.catcher.isValid() && v.catcher.teleport(catcherAt(feet))) return;
        if(v.catcher!=null) v.catcher.remove();
        v.catcher=null;
        if(!v.catcherRefused) v.catcher=catcher(v.player,feet);
        v.catcherRefused=v.catcher==null;
    }
    private Interaction catcher(Player player,Location feet) {
        try {
            Interaction catcher=player.getWorld().spawn(catcherAt(feet),Interaction.class,box -> {
                box.setPersistent(false);
                box.setVisibleByDefault(false);
                // Not responsive: the game neither swings the arm nor reports a left click for a right click on it.
                box.setResponsive(false);
                box.setInteractionWidth(CATCHER_WIDTH);
                box.setInteractionHeight(CATCHER_HEIGHT);
            });
            if(!catcher.isValid()) {
                plugin.getLogger().warning("Otro plugin canceló la caja de clics del panel para "+player.getName()
                    +"; el clic derecho al aire con la mano vacía no va a llegar.");
                return null;
            }
            player.showEntity(plugin,catcher);
            return catcher;
        } catch(RuntimeException unavailable) {
            // Without it the panel still works when a block, an entity or a held item takes the right click.
            plugin.getLogger().warning("No se pudo crear la caja de clics del panel para "+player.getName()+": "+unavailable);
            return null;
        }
    }
    private static final float CATCHER_WIDTH=3f, CATCHER_HEIGHT=4f;
    /** One block below the feet, so the eyes stay inside while jumping or crouching. */
    private static Location catcherAt(Location feet) {
        return new Location(feet.getWorld(),feet.getX(),feet.getY()-1,feet.getZ());
    }
    private static List<Panel.Hover> highlights(View view) {
        return view.panel.hovers.stream().filter(h->h.box().contains(view.x,view.y)).toList();
    }
    private static void draw(View view) {
        view.highlights=highlights(view);
        view.closeHover=PanelText.closeAt(view.x,view.y);
        Component art=view.highlights.isEmpty()&&!view.closeHover?view.body:PanelText.render(view.panel,view.x,view.y);
        view.player.showTitle(Title.title(art,PanelText.cursor(view.x,view.y),TIMES));
        view.dirty=false;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) {
        View v=views.get(event.getPlayer().getUniqueId());
        if(v==null || event instanceof PlayerTeleportEvent) return;
        if(event.hasChangedPosition() && (v.catcher!=null || !v.catcherRefused)) keepCatcher(v,event.getTo());
        if(!event.hasChangedOrientation()) return;
        double dx=Math.IEEEremainder(event.getTo().getYaw()-event.getFrom().getYaw(),360);
        double dy=event.getTo().getPitch()-event.getFrom().getPitch();
        v.cursorX=Math.clamp(v.cursorX+dx*2,0,255);
        v.cursorY=Math.clamp(v.cursorY+dy*2,0,143);
        int x=(int)Math.round(v.cursorX), y=(int)Math.round(v.cursorY);
        if(x!=v.x || y!=v.y) { v.x=x; v.y=y; v.dirty=true; }
    }
    private void click(View v,boolean right) {
        if(!available(v.player)) { close(v.player); return; }
        if(v.lastClick==ticks) return;
        v.lastClick=ticks;
        if(PanelText.closeAt(v.x,v.y)) { close(v.player); return; }
        for(Panel.Button button:v.panel.buttons.reversed()) {
            if(button.box().contains(v.x,v.y)) { button.action().accept(right); return; }
        }
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void interact(PlayerInteractEvent event) {
        View v=views.get(event.getPlayer().getUniqueId());
        if(v==null || event.getAction()==Action.PHYSICAL) return;
        event.setCancelled(true);
        if(event.getHand()==EquipmentSlot.HAND && event.getAction().isRightClick()) click(v,true);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void swing(PlayerAnimationEvent event) {
        View v=views.get(event.getPlayer().getUniqueId());
        if(v!=null && event.getAnimationType()==PlayerAnimationType.ARM_SWING) { event.setCancelled(true); click(v,false); }
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void entityUse(PlayerInteractEntityEvent event) {
        View v=views.get(event.getPlayer().getUniqueId());
        if(v==null) return;
        event.setCancelled(true);
        // One right click arrives as a single packet that the server may report as an "interact at" and an "interact"
        // in the same tick; the first one clicks and click() ignores the second.
        if(event.getHand()==EquipmentSlot.HAND) click(v,true);
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void attack(EntityDamageByEntityEvent event) {
        if(event.getDamager() instanceof Player p && views.containsKey(p.getUniqueId())) event.setCancelled(true);
    }
    @EventHandler public void drop(PlayerDropItemEvent event) {
        if(views.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler public void inventory(InventoryOpenEvent event) {
        if(event.getPlayer() instanceof Player p) close(p);
    }
    @EventHandler public void sneak(PlayerToggleSneakEvent event) { if(event.isSneaking()) close(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { close(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) { close(event.getEntity()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) { close(event.getPlayer()); }
}
