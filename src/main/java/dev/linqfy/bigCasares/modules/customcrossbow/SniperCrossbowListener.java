package dev.linqfy.bigCasares.modules.customcrossbow;

import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SniperCrossbowListener implements Listener, AutoCloseable {

    private static final double RANGE = 128.0;
    private final Server server;
    private final CrossbowEnchantmentData data;
    private final SniperDamageService damage;
    private final SniperScopeView view;
    private final SniperAimService aims = new SniperAimService();
    private final Map<UUID, Scope> scopes = new HashMap<>();
    private final Set<UUID> inventoryOpen = new HashSet<>();
    private final Set<UUID> creativeResync = new HashSet<>();
    private int heartbeat;

    SniperCrossbowListener(Server server, CrossbowEnchantmentData data,
        SniperDamageService damage, SniperScopeView view) {
        this.server = server;
        this.data = data;
        this.damage = damage;
        this.view = view;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            beginScope(event.getPlayer());
        } else {
            cancel(event.getPlayer().getUniqueId());
        }
    }

    private void beginScope(Player player) {
        if (inventoryOpen.contains(player.getUniqueId()) || scopes.containsKey(player.getUniqueId()) || !player.isOnline() || player.isDead()
            || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        EquipmentSlot hand = EquipmentSlot.HAND;
        UUID weapon = data.weaponId(player.getInventory().getItem(hand));
        if (weapon == null) {
            hand = EquipmentSlot.OFF_HAND;
            weapon = data.weaponId(player.getInventory().getItem(hand));
        }
        if (weapon == null || !view.show(player, hand)) {
            return;
        }
        data.refreshSniperLore(player.getInventory().getItem(hand));
        scopes.put(player.getUniqueId(), new Scope(weapon, hand, player.getInventory().getHeldItemSlot(),
            player.getWorld().getUID()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            || event.useItemInHand() == Event.Result.DENY || event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            beginScope(player);
        }
        Scope scope = scopes.get(player.getUniqueId());
        if (scope != null && !valid(player, scope)) {
            cancel(player.getUniqueId());
            scope = null;
        }
        boolean clickedSniper = data.enchantment(player.getInventory().getItem(event.getHand())) == CrossbowEnchantment.SNIPER;
        if (scope == null && !clickedSniper) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        if (scope == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        // A shield, food or bow in the main hand must not consume the trigger
        // before Minecraft tries a scoped Sniper held in the offhand.
        if (player.hasCooldown(Material.CROSSBOW)) {
            return;
        }
        if (!aims.isLoading(player.getUniqueId())) {
            aims.begin(player.getUniqueId(), scope.weapon(), player.getWorld().getGameTime());
            view.using(player, scope.bowHand(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRelease(PlayerStopUsingItemEvent event) {
        release(event.getPlayer(), event.getItem());
    }

    void release(Player player, ItemStack released) {
        Scope scope = scopes.get(player.getUniqueId());
        if (scope == null || !valid(player, scope) || player.hasCooldown(Material.CROSSBOW)
            || !scope.weapon().equals(data.weaponId(released))) {
            aims.cancel(player.getUniqueId());
            return;
        }
        int hearts = aims.fire(player.getUniqueId(), scope.weapon(), player.getWorld().getGameTime(), player.getHealth());
        view.using(player, scope.bowHand(), false);
        if (hearts <= 0) {
            return;
        }
        double cost = CrossbowEnchantmentRules.sniperDamage(hearts);
        player.setHealth(player.getHealth() - cost);
        ItemStack crossbow = player.getInventory().getItem(scope.bowHand());
        player.getInventory().setItem(scope.bowHand(), crossbow.damage(3, player));
        player.setCooldown(Material.CROSSBOW, CrossbowEnchantmentRules.SNIPER_COOLDOWN_TICKS);
        fire(player, cost);
        if (!valid(player, scope)) {
            cancel(player.getUniqueId());
        }
    }

    private void fire(Player player, double cost) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        RayTraceResult hit = player.getWorld().rayTrace(start, direction, RANGE, FluidCollisionMode.NEVER,
            true, 0.15, entity -> entity instanceof LivingEntity living && living != player && !living.isDead()
                && (!(living instanceof Player target) || target.getGameMode() != GameMode.SPECTATOR));
        double distance = hit == null ? RANGE : hit.getHitPosition().distance(start.toVector());
        player.getWorld().playSound(start, Sound.ITEM_CROSSBOW_SHOOT, 1.5f, 0.6f);
        for (double offset = 0.5; offset < distance; offset += 2.0) {
            player.getWorld().spawnParticle(Particle.CRIT, start.clone().add(direction.clone().multiply(offset)),
                1, 0, 0, 0, 0);
        }
        if (hit != null && hit.getHitEntity() instanceof LivingEntity target && damage.damage(player, target, cost)) {
            if (!target.isDead()) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
            player.playSound(start, Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.2f);
        }
    }

    void tick() {
        heartbeat++;
        for (UUID playerId : List.copyOf(scopes.keySet())) {
            Player player = server.getPlayer(playerId);
            Scope scope = scopes.get(playerId);
            if (player == null || !valid(player, scope)) {
                cancel(playerId);
                continue;
            }
            long tick = player.getWorld().getGameTime();
            if (aims.isLoading(playerId) && !view.isUsing(player, scope.bowHand())) {
                aims.cancel(playerId);
                view.using(player, scope.bowHand(), false);
            }
            if (tick % 5 == 0) {
                int hearts = aims.hearts(playerId, scope.weapon(), tick, player.getHealth());
                view.charge(player, hearts);
            }
        }
        // Also handle equipping while already crouching and plugin reloads.
        if (heartbeat % 5 == 0) {
            for (Player player : server.getOnlinePlayers()) {
                if (player.isSneaking()) {
                    beginScope(player);
                }
            }
        }
    }

    private boolean valid(Player player, Scope scope) {
        return player.isOnline() && !player.isDead() && player.isSneaking() && player.getGameMode() != GameMode.SPECTATOR
            && player.getWorld().getUID().equals(scope.world())
            && player.getInventory().getHeldItemSlot() == scope.slot()
            && scope.weapon().equals(data.weaponId(player.getInventory().getItem(scope.bowHand())));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLoad(EntityLoadCrossbowEvent event) {
        if (data.enchantment(event.getCrossbow()) == CrossbowEnchantment.SNIPER) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onNativeShot(EntityShootBowEvent event) {
        if (data.enchantment(event.getBow()) == CrossbowEnchantment.SNIPER) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) { cancel(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { cancel(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) { cancel(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { cancel(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
        inventoryOpen.remove(event.getPlayer().getUniqueId());
        creativeResync.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) { cancel(event.getEntity().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            inventoryOpen.add(player.getUniqueId());
            cancel(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean creative = event instanceof InventoryCreativeEvent;
        if (scopes.containsKey(player.getUniqueId())
            || creative && (creativeResync.contains(player.getUniqueId()) || NmsSniperScopeView.isProxy(event.getCursor()))) {
            event.setCancelled(true);
            if (creative) {
                // A creative move emits multiple packets, including an unmarked empty source.
                // Reject the entire stale batch until this inventory has been closed.
                creativeResync.add(player.getUniqueId());
            }
            inventoryOpen.add(player.getUniqueId());
            cancel(player.getUniqueId());
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && scopes.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            inventoryOpen.add(player.getUniqueId());
            cancel(player.getUniqueId());
            player.updateInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        inventoryOpen.remove(event.getPlayer().getUniqueId());
        creativeResync.remove(event.getPlayer().getUniqueId());
    }

    private void cancel(UUID player) {
        Scope removed = scopes.remove(player);
        aims.cancel(player);
        Player viewer = server.getPlayer(player);
        if (removed != null && viewer != null && viewer.isOnline()) {
            // Remove the token before clearing use: cancellation must never fire a shot.
            view.hide(viewer);
        }
    }

    @Override
    public void close() {
        for (UUID id : List.copyOf(scopes.keySet())) {
            cancel(id);
        }
        aims.clear();
        inventoryOpen.clear();
        creativeResync.clear();
    }

    private record Scope(UUID weapon, EquipmentSlot bowHand, int slot, UUID world) { }
}
