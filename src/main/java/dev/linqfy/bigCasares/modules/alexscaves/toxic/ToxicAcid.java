package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.damage.ModDamageTypes;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Acid of the Toxic Caves. The plugin cannot add the mod's acid fluid, so water inside the Toxic Caves
 * biome is acid (the biome colours it like the mod's acid): it burns what swims in it and wears down armor
 * that is not Hazmat, with the mod's numbers ({@link AcidRules}). Checked every few ticks with the chances
 * of those ticks; damage still respects the usual hurt cooldown, as in the mod.
 */
public final class ToxicAcid implements Listener, Runnable {
    static final int CHECK_TICKS = 5;
    /** Acid only reaches what is near a player: the rest of the world is not simulated. */
    static final double SCAN_RADIUS = 48.0;
    private static final NamespacedKey TOXIC_CAVES = new NamespacedKey("alexscaves", "toxic_caves");

    private final ModMobService mobs;
    private long tick;

    public ToxicAcid(ModMobService mobs) {
        this.mobs = mobs;
    }

    @Override
    public void run() {
        if (++tick % CHECK_TICKS != 0) {
            return;
        }
        Set<String> worlds = Set.copyOf(mobs.settings().worldgen().worlds());
        Set<UUID> done = new HashSet<>();
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            World world = player.getWorld();
            if (!worlds.contains(world.getName())) {
                continue;
            }
            for (LivingEntity entity : world.getNearbyLivingEntities(player.getLocation(), SCAN_RADIUS)) {
                if (done.add(entity.getUniqueId()) && affected(entity) && entity.isInWater()
                    && inToxicCaves(entity.getLocation()) && !resists(entity)) {
                    burn(entity);
                }
            }
        }
    }

    /** Armor stands, NPCs of other plugins and invulnerable entities are not touched by the acid. */
    private static boolean affected(LivingEntity entity) {
        return !(entity instanceof ArmorStand) && !entity.isInvulnerable() && !entity.hasMetadata("NPC");
    }

    static boolean isToxicBiome(NamespacedKey biome) {
        return TOXIC_CAVES.equals(biome);
    }

    private static boolean inToxicCaves(Location location) {
        Biome biome = location.getWorld().getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return isToxicBiome(biome.getKey());
    }

    private boolean resists(LivingEntity entity) {
        if (entity instanceof Player player && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)) {
            return true;
        }
        String id = mobs.typeOf(entity).map(type -> "alexscaves:" + type.id()).orElse(entity.getType().getKey().toString());
        return AcidRules.RESISTS.contains(id);
    }

    private void burn(LivingEntity entity) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int hazmat = Hazmat.pieces(entity, mobs::itemId);
        boolean armored = wearArmor(entity, random);
        if (random.nextDouble() < AcidRules.overTicks(AcidRules.damageChance(hazmat), CHECK_TICKS)) {
            boolean weak = AcidRules.WEAK.contains(entity.getType().getKey().toString());
            entity.damage(AcidRules.damage(hazmat, armored, weak), mobs.damageTypes().source(ModDamageTypes.ACID));
            Location at = entity.getLocation();
            at.getWorld().spawnParticle(Particle.BUBBLE_POP, at.add(0, 0.5, 0), 4, 0.3, 0.3, 0.3, 0.02);
            at.getWorld().playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.6f);
        }
    }

    /** Non-Hazmat damageable armor takes the acid: 5 % per piece and tick to lose 1 durability. Returns if any is worn. */
    private boolean wearArmor(LivingEntity entity, ThreadLocalRandom random) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return false;
        }
        boolean armored = false;
        boolean worn = false;
        boolean creative = entity instanceof Player player && player.getGameMode() == GameMode.CREATIVE;
        double wearChance = AcidRules.overTicks(AcidRules.ARMOR_WEAR_CHANCE_PER_TICK, CHECK_TICKS);
        ItemStack[] armor = equipment.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            // Mod isDamageableItem: unbreakable armor does not shield from acid.
            if (piece == null || piece.isEmpty() || !(piece.getItemMeta() instanceof Damageable damageable)
                || damageable.isUnbreakable() || piece.getType().getMaxDurability() <= 0 && !damageable.hasMaxDamage()
                || mobs.itemId(piece).filter(Hazmat.PIECES::contains).isPresent()) {
                continue;
            }
            armored = true;
            if (!creative && random.nextDouble() < wearChance) {
                armor[i] = piece.damage(1, entity);
                worn = true;
            }
        }
        if (worn) {
            equipment.setArmorContents(armor);
        }
        return armored;
    }
}
