package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Diving suit abilities (mod {@code CommonEvents.livingTick}/{@code onEntityJoinWorld}, {@code MuckBlock} and
 * {@code DivingArmorItem}): the helmet keeps Water Breathing (810 ticks) topped up whenever the wearer's head is out
 * of the water, so a dive lasts 40 seconds; drowned spawning in the Abyssal Chasm may wear pieces of it (20 % each);
 * and the boots let their wearer cross muck at full speed, where it slows everything else by 15 %. The leggings'
 * swim speed and the armor itself are item data (see the item catalog).
 */
public final class DivingSuit implements Listener, Runnable, AutoCloseable {
    static final String HELMET = "ac_diving_helmet";
    static final String BOOTS = "ac_diving_boots";
    /** Mod config {@code drowned_diving_gear_spawn_chance} default. */
    static final double DROWNED_GEAR_CHANCE = 0.2;
    static final int CHECK_TICKS = 5;
    static final int BREATHING_TICKS = 810;
    /**
     * Mod {@code MuckBlock.entityInside}: horizontal velocity times 0.85 once a tick, after the move and before ground
     * friction (0.546): the stride goes from a / (1 - 0.546) = 2.20 a to a / (1 - 0.546 * 0.85) = 1.87 a, 15 % less (the
     * 0.85 is not applied twice, so it is not 28 %).
     */
    static final double MUCK_SLOWDOWN = -0.15;
    private static final NamespacedKey MUCK_KEY = new NamespacedKey("bigcasares", "ac_muck_slowdown");
    private static final NamespacedKey ABYSSAL_CHASM = new NamespacedKey("alexscaves", "abyssal_chasm");

    private final ModMobService mobs;
    private final BlockData muck;
    private long tick;
    /** Players whose head was out of the water at the last check (the helmet renews when they dive). */
    private final java.util.Set<java.util.UUID> dry = new java.util.HashSet<>();

    public DivingSuit(ModMobService mobs, BlockData muck) {
        this.mobs = mobs;
        this.muck = muck;
    }

    private boolean wears(ItemStack stack, String id) {
        return stack != null && !stack.isEmpty() && mobs.itemId(stack).filter(id::equals).isPresent();
    }

    /** Whether Water Breathing from the helmet should be renewed: not when a longer or stronger one is running. */
    static boolean shouldRenew(PotionEffect current) {
        return current == null || DivingRules.shouldRenew(current.getAmplifier(), current.getDuration());
    }

    @Override
    public void run() {
        if (++tick % CHECK_TICKS != 0) {
            return;
        }
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            EntityEquipment equipment = player.getEquipment();
            if (equipment == null) {
                continue;
            }
            if (wears(equipment.getHelmet(), HELMET)) {
                // Mod: out of the water the effect is pinned at 810 every tick, so what counts is that it is 810 when the
                // wearer dives. Here it is renewed the moment they go under (not every check, which would fire the
                // potion event all the time).
                boolean underwater = AbyssalMobs.fluid(player.getEyeLocation().getBlock());
                if (!underwater) {
                    dry.add(player.getUniqueId());
                } else if (dry.remove(player.getUniqueId())
                    && shouldRenew(player.getPotionEffect(PotionEffectType.WATER_BREATHING))) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, BREATHING_TICKS, 0, false, false, true));
                }
            } else {
                dry.remove(player.getUniqueId());
            }
            slowOnMuck(player, equipment);
        }
    }

    /** The slowdown modifier is saved with the player: clear a stale one on join and when the module goes. */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        clearSlowdown(event.getPlayer());
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        dry.remove(event.getPlayer().getUniqueId());
        clearSlowdown(event.getPlayer());
    }

    private static void clearSlowdown(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeModifier existing = speed == null ? null : speed.getModifier(MUCK_KEY);
        if (existing != null) {
            speed.removeModifier(existing);
        }
    }

    @Override
    public void close() {
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            clearSlowdown(player);
        }
        dry.clear();
    }

    private void slowOnMuck(Player player, EntityEquipment equipment) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean slowed = false;
        if (muck != null && player.isOnGround() && !wears(equipment.getBoots(), BOOTS)) {
            Block below = player.getLocation().subtract(0, 0.1, 0).getBlock();
            slowed = below.getBlockData().equals(muck);
        }
        AttributeModifier existing = speed.getModifier(MUCK_KEY);
        if (slowed && existing == null) {
            speed.addModifier(new AttributeModifier(MUCK_KEY, MUCK_SLOWDOWN, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        } else if (!slowed && existing != null) {
            speed.removeModifier(existing);
        }
    }

    /** Mod {@code onEntityJoinWorld}: a bare drowned of the Abyssal Chasm may wear each piece, dropping it half the time. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Drowned drowned) || mobs.typeOf(drowned).isPresent()
            || !ABYSSAL_CHASM.equals(drowned.getWorld().getBiome(drowned.getLocation()).getKey())) {
            return;
        }
        EntityEquipment equipment = drowned.getEquipment();
        if (equipment == null || hasArmor(equipment)) {
            return;
        }
        String[] pieces = {HELMET, "ac_diving_chestplate", "ac_diving_leggings", BOOTS};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < pieces.length; i++) {
            if (ThreadLocalRandom.current().nextFloat() < DROWNED_GEAR_CHANCE) {
                int index = i;
                mobs.createItem(pieces[index], 1).ifPresent(item -> {
                    equipment.setItem(slots[index], item);
                    equipment.setDropChance(slots[index], 0.5F);
                });
            }
        }
    }

    private static boolean hasArmor(EntityEquipment equipment) {
        for (ItemStack armor : equipment.getArmorContents()) {
            if (armor != null && !armor.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
