package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.ModEnchantment;
import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.ModEnchantments;
import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetismService;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abilities of the Magnetic Caves items with the values of the mod:
 * Resistor Shield pulses, Galena Gauntlet remote tool and Seeking Arrows.
 */
public final class MagneticItemAbilities implements Listener {
    static final String SHIELD = "ac_resistor_shield";
    static final String GAUNTLET = "ac_galena_gauntlet";
    static final String SEEKING_ARROW = "ac_seeking_arrow";

    private final Plugin plugin;
    private final CustomItemRegistry items;
    private final ModEnchantments enchantments;
    private final NamespacedKey polarityKey;
    private final Map<UUID, Integer> shieldTicks = new HashMap<>();
    private final Map<UUID, GauntletUse> gauntlets = new HashMap<>();
    private final Map<UUID, AbstractArrow> seekingArrows = new HashMap<>();

    public MagneticItemAbilities(Plugin plugin, CustomItemRegistry items, ModEnchantments enchantments) {
        this.plugin = plugin;
        this.items = items;
        this.enchantments = enchantments;
        this.polarityKey = new NamespacedKey(plugin, "alexscaves_azure_polarity");
    }

    public void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack active = player.isHandRaised() ? player.getActiveItem() : null;
            Optional<String> activeId = active == null ? Optional.empty() : items.resolveItemId(active);
            tickShield(player, activeId.filter(SHIELD::equals).isPresent() ? active : null);
            tickGauntlet(player, activeId.filter(GAUNTLET::equals).isPresent() ? active : null);
        }
        tickArrows();
    }

    // --- Resistor Shield -------------------------------------------------------------------------

    private void tickShield(Player player, ItemStack shield) {
        if (shield == null) {
            shieldTicks.remove(player.getUniqueId());
            return;
        }
        int ticks = shieldTicks.merge(player.getUniqueId(), 1, Integer::sum);
        if (ticks < 10) {
            return;
        }
        boolean first = ticks == 10;
        if (!first && (ticks - 10) % 5 != 0) {
            return;
        }
        boolean azure = isAzure(shield);
        double damage = first ? MagneticFormulas.shieldSlamDamage(enchantments.level(shield, ModEnchantment.HEAVY_SLAM)) : 2.0;
        double knockback = first ? 0.5 : 0.2;
        for (Entity nearby : player.getNearbyEntities(5, 1, 5)) {
            if (nearby instanceof LivingEntity living && !nearby.equals(player)) {
                living.damage(damage, player);
                Vector away = living.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                if (away.lengthSquared() > 1e-4) {
                    away.normalize().multiply(azure ? -knockback : knockback);
                    living.setVelocity(living.getVelocity().add(away.setY(0.1)));
                }
            }
        }
        Location center = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, first ? 40 : 10, 2.5, 0.5, 2.5, 0.1);
        player.getWorld().playSound(center, first ? "alexscaves:resistor_shield_slam" : "alexscaves:resistor_shield_spin", 1f, 1f);
        if (first) {
            damage(player, shield, 1);
        }
    }

    /** Sneak + right click flips the shield's polarity (scarlet pushes away, azure pulls in). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getPlayer().isSneaking() || event.getHand() == null
            || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack held = event.getHand() == EquipmentSlot.OFF_HAND
            ? event.getPlayer().getInventory().getItemInOffHand() : event.getPlayer().getInventory().getItemInMainHand();
        if (items.resolveItemId(held).filter(SHIELD::equals).isEmpty()) {
            return;
        }
        ItemMeta meta = held.getItemMeta();
        boolean azure = !isAzure(held);
        meta.getPersistentDataContainer().set(polarityKey, PersistentDataType.BYTE, (byte) (azure ? 1 : 0));
        held.setItemMeta(meta);
        event.getPlayer().sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
            azure ? "§9Polaridad azul: atrae" : "§cPolaridad escarlata: repele"));
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, azure ? 1.4f : 0.9f);
    }

    private boolean isAzure(ItemStack shield) {
        Byte value = shield.getItemMeta().getPersistentDataContainer().get(polarityKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    // --- Galena Gauntlet -------------------------------------------------------------------------

    private void tickGauntlet(Player player, ItemStack gauntlet) {
        GauntletUse use = gauntlets.get(player.getUniqueId());
        // The gauntlet throws what the other hand holds, never itself.
        ItemStack tool = player.getActiveItemHand() == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        boolean crystallization = gauntlet != null && enchantments.level(gauntlet, ModEnchantment.CRYSTALLIZATION) > 0;
        if (gauntlet == null || !canLaunch(tool, crystallization)) {
            if (use != null) {
                stopGauntlet(player, use);
            }
            return;
        }
        if (use == null) {
            ItemStack shown = tool.clone();
            ItemDisplay display = player.getWorld().spawn(player.getEyeLocation().add(player.getLocation().getDirection()),
                ItemDisplay.class, entity -> {
                    entity.setItemStack(shown);
                    entity.setPersistent(false);
                    entity.setTeleportDuration(1);
                });
            use = new GauntletUse(display);
            gauntlets.put(player.getUniqueId(), use);
            damage(player, gauntlet, 1);
        }
        int range = 30 + 5 * enchantments.level(gauntlet, ModEnchantment.FIELD_EXTENSION);
        boolean haste = enchantments.level(gauntlet, ModEnchantment.FERROUS_HASTE) > 0;
        RayTraceResult hit = player.getWorld().rayTrace(player.getEyeLocation(), player.getLocation().getDirection(), range,
            FluidCollisionMode.NEVER, true, 0.5, entity -> entity instanceof LivingEntity && !entity.equals(player));
        Location goal = hit == null
            ? player.getEyeLocation().add(player.getLocation().getDirection().multiply(range))
            : hit.getHitPosition().toLocation(player.getWorld());
        Location current = use.display.getLocation();
        Vector step = goal.toVector().subtract(current.toVector());
        double distance = step.length();
        double speed = hit != null && hit.getHitEntity() != null ? 0.9 : 0.6;
        if (distance > 0.05) {
            current.add(step.multiply(Math.min(speed, distance) / distance));
            use.display.teleport(current);
        }
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, current, 1, 0.1, 0.1, 0.1, 0);
        use.cooldown--;
        if (hit != null && hit.getHitEntity() instanceof LivingEntity target
            && target.getLocation().add(0, target.getHeight() / 2, 0).distanceSquared(current) < 2.25) {
            if (use.cooldown <= 0) {
                double damage = player.getAttribute(Attribute.ATTACK_DAMAGE) == null ? 1.0
                    : player.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
                target.damage(damage, player);
                damage(player, tool, 1);
                use.cooldown = haste ? 3 : 5 + ThreadLocalRandom.current().nextInt(5);
            }
            return;
        }
        if (hit != null && hit.getHitBlock() != null && hit.getHitBlock().getLocation().add(0.5, 0.5, 0.5).distanceSquared(current) < 2.25) {
            mine(player, use, hit.getHitBlock(), tool, haste);
        } else {
            use.mining = null;
            use.progress = 0;
        }
    }

    private void mine(Player player, GauntletUse use, Block block, ItemStack tool, boolean haste) {
        float hardness = block.getType().getHardness();
        if (hardness < 0 || block.getType().isAir() || !block.isPreferredTool(tool)) {
            return;
        }
        if (!block.equals(use.mining)) {
            use.mining = block;
            use.progress = 0;
        }
        use.progress += MagneticFormulas.miningProgress(MagneticFormulas.digSpeed(tool.getType().name()), hardness, haste);
        if (use.progress >= 1.0) {
            player.breakBlock(block);
            use.mining = null;
            use.progress = 0;
        }
    }

    static boolean canLaunch(ItemStack tool, boolean crystallization) {
        if (tool == null || tool.getType().isAir()) {
            return false;
        }
        if (MagnetismService.isFerromagnetic(tool)) {
            return true;
        }
        String name = tool.getType().name().toLowerCase(Locale.ROOT);
        return crystallization && (name.startsWith("diamond") || name.contains("quartz") || name.contains("amethyst"));
    }

    private void stopGauntlet(Player player, GauntletUse use) {
        use.display.remove();
        gauntlets.remove(player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), "alexscaves:galena_gauntlet_stop", 1f, 1f);
    }

    /**
     * Arrow Inducting (mod {@code CommonEvents.livingAttack}): an arrow blocked by an enchanted
     * Resistor Shield turns into a seeking arrow of the blocker, flying back at 0.4 of its speed.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onArrowBlocked(EntityDamageByEntityEvent event) {
        // Arrows only: a trident (also an AbstractArrow) must go back to its thrower, not to the blocker.
        if (!(event.getDamager() instanceof Arrow || event.getDamager() instanceof SpectralArrow)
            || !(event.getDamager() instanceof AbstractArrow arrow) || seekingArrows.containsKey(arrow.getUniqueId())
            || !(event.getEntity() instanceof HumanEntity blocker) || !blocker.isBlocking()) {
            return;
        }
        ItemStack shield = blocker.getActiveItem();
        if (items.resolveItemId(shield).filter(SHIELD::equals).isEmpty()
            || enchantments.level(shield, ModEnchantment.ARROW_INDUCTING) <= 0) {
            return;
        }
        Vector back = arrow.getVelocity().multiply(-0.4);
        Arrow induced = arrow.getWorld().spawn(arrow.getLocation(), Arrow.class, spawned -> {
            spawned.setShooter(blocker);
            spawned.setVelocity(back);
            spawned.setPickupStatus(arrow.getPickupStatus());
            spawned.setItemStack(arrow.getItemStack());
            spawned.setDamage(arrow.getDamage());
        });
        seekingArrows.put(induced.getUniqueId(), induced);
        arrow.remove();
        event.setCancelled(true);
    }

    // --- Seeking Arrow ---------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof AbstractArrow arrow
            && items.resolveItemId(event.getConsumable()).filter(SEEKING_ARROW::equals).isPresent()) {
            ItemStack pickup = event.getConsumable().clone();
            pickup.setAmount(1);
            arrow.setItemStack(pickup);
            seekingArrows.put(arrow.getUniqueId(), arrow);
        }
    }

    private void tickArrows() {
        Iterator<AbstractArrow> iterator = seekingArrows.values().iterator();
        while (iterator.hasNext()) {
            AbstractArrow arrow = iterator.next();
            if (!arrow.isValid() || arrow.isInBlock() || arrow.getTicksLived() > 200) {
                iterator.remove();
                continue;
            }
            double radius = MagneticFormulas.seekRadius(arrow.getTicksLived());
            LivingEntity closest = null;
            double best = Double.MAX_VALUE;
            for (Entity nearby : arrow.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof LivingEntity living && !nearby.equals(arrow.getShooter()) && !(nearby instanceof Player target && target.getGameMode() == GameMode.SPECTATOR)) {
                    double distance = nearby.getLocation().distanceSquared(arrow.getLocation());
                    if (distance < best) {
                        best = distance;
                        closest = living;
                    }
                }
            }
            if (closest != null) {
                Vector velocity = arrow.getVelocity();
                double speed = velocity.length();
                Vector direction = closest.getLocation().add(0, closest.getHeight() / 2, 0).toVector()
                    .subtract(arrow.getLocation().toVector()).normalize().multiply(speed);
                arrow.setVelocity(velocity.multiply(0.3).add(direction.multiply(0.7)));
            }
        }
    }

    // --- Shared ----------------------------------------------------------------------------------

    private static void damage(Player player, ItemStack item, int amount) {
        if (player.getGameMode() == GameMode.CREATIVE || !(item.getItemMeta() instanceof Damageable)) {
            return;
        }
        item.damage(amount, player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shieldTicks.remove(event.getPlayer().getUniqueId());
        GauntletUse use = gauntlets.remove(event.getPlayer().getUniqueId());
        if (use != null) {
            use.display.remove();
        }
    }

    public void shutdown() {
        gauntlets.values().forEach(use -> use.display.remove());
        gauntlets.clear();
        shieldTicks.clear();
        seekingArrows.clear();
    }

    private static final class GauntletUse {
        private final ItemDisplay display;
        private int cooldown;
        private Block mining;
        private double progress;

        private GauntletUse(ItemDisplay display) {
            this.display = display;
        }
    }
}
