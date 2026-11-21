package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mod {@code MineExplosion}, for the mine guardian (radius 5) and the depth charge (radius 2): a vanilla-shaped
 * explosion whose rays lose nothing to water, so it digs underwater too. Water stays where it was. Blocks go through
 * {@link EntityExplodeEvent}, so protection plugins and the module's own block drops see them.
 */
final class MineExplosion {
    private MineExplosion() {
    }

    /**
     * @param source the exploding entity (spared, like vanilla)
     * @param cause  who is to blame (the thrower of a depth charge), for the damage source
     */
    static void explode(Entity source, Entity cause, Location center, float radius) {
        World world = center.getWorld();
        boolean underwater = center.getBlock().getType() == Material.WATER;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean griefing = !Boolean.FALSE.equals(world.getGameRuleValue(GameRules.MOB_GRIEFING));
        List<Block> blocks = griefing ? rays(world, center, radius, random) : new ArrayList<>();
        world.playSound(center, underwater ? "alexscaves:mine_guardian_explode" : "alexscaves:mine_guardian_land_explode",
            SoundCategory.BLOCKS, 4.0F, (1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F) * 0.7F);
        world.spawnParticle(underwater ? Particle.BUBBLE_POP : Particle.EXPLOSION, center, (int) (5 + radius * 10), radius, radius,
            radius, 0.0);
        if (underwater) {
            world.spawnParticle(Particle.EXPLOSION, center, 1 + (int) radius, radius * 0.5, radius * 0.5, radius * 0.5, 0.0);
        }
        hurt(source, cause, center, radius);
        if (blocks.isEmpty()) {
            return;
        }
        // DESTROY_WITH_DECAY: each block drops with a chance of 1 / radius (vanilla explosion decay rule).
        boolean decay = !Boolean.FALSE.equals(world.getGameRuleValue(GameRules.MOB_EXPLOSION_DROP_DECAY));
        EntityExplodeEvent event = new EntityExplodeEvent(source, center, blocks, decay ? 1.0F / radius : 1.0F,
            decay ? org.bukkit.ExplosionResult.DESTROY_WITH_DECAY : org.bukkit.ExplosionResult.DESTROY);
        if (!event.callEvent()) {
            return;
        }
        List<Block> toBlow = new ArrayList<>(event.blockList());
        java.util.Collections.shuffle(toBlow);
        for (Block block : toBlow) {
            Material type = block.getType();
            if (type.isAir() || holdsFluid(block)) {
                // Mod: only blocks without a fluid are broken (water, waterlogged blocks, kelp and seagrass stay).
                continue;
            }
            if (random.nextFloat() < event.getYield()) {
                for (ItemStack drop : block.getDrops()) {
                    world.dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
                }
            }
            block.setType(Material.AIR, true);
        }
    }

    /** Vanilla explosion rays (16x16x16 surface directions); fluids cost nothing, unbreakable blocks stop the ray. */
    static List<Block> rays(World world, Location center, float radius, ThreadLocalRandom random) {
        Set<Block> found = new LinkedHashSet<>();
        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                for (int l = 0; l < 16; l++) {
                    if (j != 0 && j != 15 && k != 0 && k != 15 && l != 0 && l != 15) {
                        continue;
                    }
                    double dx = j / 15.0 * 2.0 - 1.0;
                    double dy = k / 15.0 * 2.0 - 1.0;
                    double dz = l / 15.0 * 2.0 - 1.0;
                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= length;
                    dy /= length;
                    dz /= length;
                    float power = radius * (0.7F + random.nextFloat() * 0.6F);
                    double x = center.getX();
                    double y = center.getY();
                    double z = center.getZ();
                    while (power > 0.0F) {
                        int bx = (int) Math.floor(x);
                        int by = (int) Math.floor(y);
                        int bz = (int) Math.floor(z);
                        if (by < world.getMinHeight() || by >= world.getMaxHeight() || !world.isChunkLoaded(bx >> 4, bz >> 4)) {
                            break;
                        }
                        Block block = world.getBlockAt(bx, by, bz);
                        Material type = block.getType();
                        if (type.getHardness() < 0) {
                            // Mod tag unmoveable (bedrock, barriers, portals...).
                            break;
                        }
                        if (!holdsFluid(block)) {
                            power -= (type.getBlastResistance() + 0.3F) * 0.3F;
                        }
                        if (!type.isAir()) {
                            found.add(block);
                        }
                        x += dx * 0.3;
                        y += dy * 0.3;
                        z += dz * 0.3;
                        power -= 0.22500001F;
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** A block with a fluid state: water, lava, waterlogged blocks and the always-wet plants. */
    static boolean holdsFluid(Block block) {
        Material type = block.getType();
        return type == Material.WATER || type == Material.LAVA || type == Material.BUBBLE_COLUMN || type == Material.KELP
            || type == Material.KELP_PLANT || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS
            || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    /** Vanilla explosion damage and push within twice the radius (exposure from the centre to the entity's eyes). */
    private static void hurt(Entity source, Entity cause, Location center, float radius) {
        double reach = radius * 2.0;
        DamageSource damage = DamageSource.builder(DamageType.EXPLOSION).withDirectEntity(source).withCausingEntity(cause)
            .withDamageLocation(center).build();
        for (Entity entity : center.getWorld().getNearbyEntities(center, reach + 1, reach + 1, reach + 1)) {
            if (entity == source || !(entity instanceof LivingEntity living) || !living.isValid()
                || living instanceof org.bukkit.entity.ArmorStand stand && stand.isMarker()) {
                continue;
            }
            boolean creative = living instanceof Player player && (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR);
            if (living instanceof Player player && player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double distance = living.getLocation().distance(center) / reach;
            if (distance > 1.0) {
                continue;
            }
            Vector push = living.getEyeLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() == 0) {
                continue;
            }
            double exposure = seen(center, living) ? 1.0 : 0.0;
            double impact = (1.0 - distance) * exposure;
            if (impact <= 0) {
                continue;
            }
            if (!creative) {
                living.damage((int) ((impact * impact + impact) / 2.0 * 7.0 * reach + 1.0), damage);
            }
            // Vanilla ProtectionEnchantment.getExplosionKnockbackAfterDampener: 15 % less push per Blast Protection level.
            int blast = 0;
            if (living.getEquipment() != null) {
                for (org.bukkit.inventory.ItemStack worn : living.getEquipment().getArmorContents()) {
                    if (worn != null) {
                        blast = Math.max(blast, worn.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.BLAST_PROTECTION));
                    }
                }
            }
            double knockback = blast > 0 ? impact * Math.max(0.0, 1.0 - blast * 0.15) : impact;
            living.setVelocity(living.getVelocity().add(push.normalize().multiply(knockback)));
        }
    }

    private static boolean seen(Location center, LivingEntity living) {
        Vector to = living.getEyeLocation().toVector().subtract(center.toVector());
        double length = to.length();
        if (length < 0.01) {
            return true;
        }
        var hit = center.getWorld().rayTraceBlocks(center, to.normalize(), length, org.bukkit.FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }
}
