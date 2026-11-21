package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.UUID;

/**
 * What the Luxtructosaurus and the Atlatitan share (mod {@code SauropodBaseEntity}): the seven hit parts (three neck
 * pieces, the head and three tail pieces, as {@link Interaction} boxes that hurt the body when hit), the walking and the
 * area hit {@code hurtEntitiesAround}. The body is a scaled vanilla mob with no AI that the behaviour drives by velocity.
 */
final class SauropodBody {
    static final NamespacedKey PART_OF = new NamespacedKey("bigcasares", "ac_sauropod_part");
    /** Mod parts: neck 1 (3 by 3), neck 2 (2 by 2), neck 3 (2 by 1.5), head (2 by 2), tail 1 (3 by 2), 2 (2.5 by 1.5), 3 (2 by 1). */
    static final double[][] SIZES = {{3, 3}, {2, 2}, {2, 1.5}, {2, 2}, {3, 2}, {2.5, 1.5}, {2, 1}};
    static final int HEAD = 3;
    static final int TAIL_2 = 5;
    /** Mod pose along the body (forward, up) of each part from the one before it (neck 1 and tail 1 from the body). */
    private static final double[][] OFFSETS = {{5.0, 2.0}, {2.5, 0.3}, {2.0, 0.25}, {2.5, 0.3}, {-3.5, -0.5}, {-3.25, -0.25},
        {-2.5, 0.0}};

    private final Interaction[] parts = new Interaction[SIZES.length];
    private final long[] lastHits = new long[SIZES.length];
    private final double bodyHeight;
    private final double lift;
    /** Size of the whole pose relative to the adult (the Atlatitan's young are a fraction of it). */
    private double scale = 1.0;

    /**
     * @param bodyHeight the mod's hitbox height (the body centre is half of it)
     * @param lift extra height of the neck pose (the raised neck of the idle pose)
     */
    SauropodBody(double bodyHeight, double lift) {
        this.bodyHeight = bodyHeight;
        this.lift = lift;
    }

    /** Resizes the pose and the parts that already exist; 1 is the adult. */
    void setScale(double next) {
        if (Math.abs(next - scale) < 1.0E-6) {
            return;
        }
        scale = next;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && parts[i].isValid()) {
                parts[i].setInteractionWidth((float) (SIZES[i][0] * scale));
                parts[i].setInteractionHeight((float) (SIZES[i][1] * scale));
            }
        }
    }

    /** Centres of the seven parts for a body at {@code at} facing {@code yaw}. */
    Vector[] centers(Location at, double yaw) {
        double fx = -Math.sin(Math.toRadians(yaw));
        double fz = Math.cos(Math.toRadians(yaw));
        Vector body = at.toVector().add(new Vector(0, bodyHeight * 0.5 * scale, 0));
        Vector[] centers = new Vector[SIZES.length];
        Vector neck = body;
        for (int i = 0; i <= HEAD; i++) {
            neck = neck.clone().add(new Vector(fx * OFFSETS[i][0] * scale, (OFFSETS[i][1] + (i == 0 ? lift : 0)) * scale,
                fz * OFFSETS[i][0] * scale));
            centers[i] = neck;
        }
        Vector tail = body;
        for (int i = HEAD + 1; i < SIZES.length; i++) {
            tail = tail.clone().add(new Vector(fx * OFFSETS[i][0] * scale, OFFSETS[i][1] * scale, fz * OFFSETS[i][0] * scale));
            centers[i] = tail;
        }
        return centers;
    }

    void ensure(LivingEntity body, Location at, double yaw) {
        Vector[] centers = centers(at, yaw);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && parts[i].isValid()) {
                continue;
            }
            Location spot = location(at.getWorld(), centers[i], i);
            if (!spot.isChunkLoaded()) {
                continue;
            }
            double width = SIZES[i][0] * scale;
            double height = SIZES[i][1] * scale;
            parts[i] = at.getWorld().spawn(spot, Interaction.class, part -> {
                part.setInteractionWidth((float) width);
                part.setInteractionHeight((float) height);
                part.setResponsive(true);
                part.setPersistent(false);
                part.getPersistentDataContainer().set(PART_OF, PersistentDataType.STRING, body.getUniqueId().toString());
            });
            lastHits[i] = 0L;
        }
    }

    private Location location(World world, Vector center, int index) {
        return new Location(world, center.getX(), center.getY() - SIZES[index][1] * scale * 0.5, center.getZ());
    }

    void follow(Location at, double yaw) {
        Vector[] centers = centers(at, yaw);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && parts[i].isValid()) {
                parts[i].teleport(location(at.getWorld(), centers[i], i));
            }
        }
    }

    /** Centre of a part (the head to burn from, the tail to whip with). */
    Vector center(Location at, double yaw, int part) {
        return centers(at, yaw)[part];
    }

    /** Mod {@code MultipartEntityMessage}: a player hitting a part hits the body. */
    void forwardHits(LivingEntity body) {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null || !parts[i].isValid()) {
                continue;
            }
            Interaction.PreviousInteraction attack = parts[i].getLastAttack();
            if (attack == null || attack.getTimestamp() == lastHits[i]) {
                continue;
            }
            lastHits[i] = attack.getTimestamp();
            if (attack.getPlayer() instanceof Player player && player.isOnline() && !body.getPassengers().contains(player)) {
                player.attack(body);
            }
        }
    }

    void close() {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null) {
                parts[i].remove();
                parts[i] = null;
            }
        }
    }

    private static final NamespacedKey CUSTOM_ENTITY = new NamespacedKey("bigcasares", "custom_entity");

    /** Whether two entities are of the same kind: the same module mob, or (neither one) the same vanilla type. */
    static boolean sameKind(Entity a, Entity b) {
        String first = a.getPersistentDataContainer().get(CUSTOM_ENTITY, PersistentDataType.STRING);
        String second = b.getPersistentDataContainer().get(CUSTOM_ENTITY, PersistentDataType.STRING);
        if (first != null || second != null) {
            return first != null && first.equals(second);
        }
        return a.getType() == b.getType();
    }

    /** The sauropod an {@link Interaction} part belongs to. */
    static Optional<UUID> partOwner(Entity entity) {
        String id = entity.getPersistentDataContainer().get(PART_OF, PersistentDataType.STRING);
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
    }

    // --- movement and hits ---

    /** Mod {@code Mth.approachDegrees}: moves an angle toward a target by at most {@code step}. */
    static float approachDegrees(float current, float target, float step) {
        float delta = target - current;
        delta = delta - 360.0F * (float) Math.floor((delta + 180.0F) / 360.0F);
        return current + Math.max(-step, Math.min(step, delta));
    }

    static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Walks toward a point at {@code speed} blocks a tick, turning at most {@code turn} degrees a step; the horizontal
     * velocity is set (the vertical one is left to gravity and jumps), 1.3 times over because friction takes a share
     * of every step in between.
     */
    static void walk(LivingEntity body, double x, double z, double speed, float turn) {
        Location at = body.getLocation();
        double dx = x - at.getX();
        double dz = z - at.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 0.3) {
            return;
        }
        float yaw = approachDegrees(at.getYaw(), yawTo(dx, dz), turn);
        body.setRotation(yaw, 0.0F);
        Vector velocity = body.getVelocity();
        double factor = speed * 1.3 / length;
        // Only move along where it faces when it has turned toward the goal.
        double aligned = Math.abs(approachDegrees(yaw, yawTo(dx, dz), 360.0F) - yaw) < 25.0 ? 1.0 : 0.25;
        body.setVelocity(new Vector(dx * factor * aligned, velocity.getY(), dz * factor * aligned));
    }

    /**
     * Mod {@code hurtEntitiesAround}: hurts the living things within a sphere (not itself, nor its own kind, nor allies),
     * knocks them back and optionally sets them alight or disables their shields.
     */
    static boolean hurtAround(LivingEntity source, Location center, float radius, float amount, float knockback,
                              boolean fire, boolean disableShields) {
        boolean hurt = false;
        World world = center.getWorld();
        DamageSource damage = DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(source)
            .withDirectEntity(source).build();
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living == source || living.isDead()
                || sameKind(source, living) || entity instanceof Interaction
                || living.getLocation().add(0, living.getHeight() * 0.5, 0).distanceSquared(center) > radius * radius
                || living instanceof Player player && player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (disableShields && living instanceof Player player && player.isBlocking()) {
                player.setCooldown(Material.SHIELD, 100);
            }
            double before = living.getHealth();
            living.damage(amount, damage);
            if (living.getHealth() < before || living.isDead()) {
                hurt = true;
                if (fire) {
                    living.setFireTicks(200);
                }
                Location at = living.getLocation();
                living.knockback(knockback, center.getX() - at.getX(), center.getZ() - at.getZ());
            }
        }
        return hurt;
    }
}
