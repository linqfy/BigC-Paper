package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobAnimator;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Gum Worm (mod {@code GumWormEntity}): a 150-health worm that swims through the ground (anything but the blocks
 * withers cannot break), dragging 15-19 body segments behind it. It is neutral: it wanders underground and now and then
 * leaps out; what hurts it is chased through the ground, leapt at from more than 13 blocks away and bitten (9 damage,
 * strong knockback) by its mouth. Players who hook it with a Candy Cane Hook ride its fourth segment and steer it: it
 * goes forwards, turns with the strafe keys, leaps with jump and bites when the rider swings.
 *
 * <p>The head is a ravager without AI moved by the module; the segments are interactions carrying the segment
 * model, and hitting one hurts the worm.
 */
public final class CandyGumWorm {
    private static final CaveBiomeId BIOME = CaveBiomeId.CANDY_CAVITY;

    public static final ModMobType GUM_WORM = new ModMobType("gum_worm", BIOME, "ravager", SpawnCategory.MONSTER,
        Map.of("max_health", 150.0, "armor", 10.0, "attack_damage", 9.0, "movement_speed", 0.25, "follow_range", 128.0),
        "alexscaves_gum_worm", List.of(new MobDrop("ac_sweet_tooth", 2, 4, false, 1.0, 0.0, false)));
    /** Segment interactions: the worm head they belong to. */
    static final NamespacedKey SEGMENT_OF = new NamespacedKey("bigcasares", "ac_gum_worm_segment");
    /** Mod {@code getIdealPosition}: segments trail 2.5 blocks apart. */
    static final double SEGMENT_SPACING = 2.5;
    static final int RIDING_SEGMENT = 3;

    private CandyGumWorm() {
    }

    public static void register(ModMobCatalog catalog) {
        catalog.register(GUM_WORM, (entity, type) -> {
            entity.setGravity(false);
            entity.setAI(false);
            entity.setCollidable(false);
            if (entity instanceof org.bukkit.entity.Raider raider) {
                raider.setCanJoinRaid(false);
            }
            return new GumWorm(type);
        });
        // Mod checkGumWormSpawnRules: monster spawn rules and 1 in 6. A worm is ~20 entities, so the module also keeps
        // them 64 blocks apart.
        catalog.spawnRule(GUM_WORM.id(), (block, random) -> random.nextInt(6) == 0
            && block.getWorld().getNearbyEntities(block.getLocation(), 64, 64, 64,
            found -> found.getPersistentDataContainer().has(SEGMENT_OF)).isEmpty());
    }

    /** Mod {@code canDigBlock}: everything but the blocks withers cannot break (tag {@code unmovable}). */
    static boolean diggable(Block block) {
        return block.getType().isAir() || !Tag.WITHER_IMMUNE.isTagged(block.getType());
    }

    /** The worm head a segment interaction belongs to, if it is one. */
    static Optional<java.util.UUID> headOf(Entity entity) {
        String id = entity.getPersistentDataContainer().get(SEGMENT_OF, PersistentDataType.STRING);
        return id == null ? Optional.empty() : Optional.of(java.util.UUID.fromString(id));
    }

    static final class GumWorm implements ModMobBehaviour {
        private final ModMobType type;
        private final List<Interaction> segments = new ArrayList<>();
        private final List<JavaModelHandle> segmentModels = new ArrayList<>();
        private final List<Long> lastHits = new ArrayList<>();
        private final MobAnimator animation = new MobAnimator();
        /** Loop last started on the segment models; null makes the next sync reach every segment (new ones attach). */
        private String segmentAnimation;
        private Vector heading = new Vector(1, 0, 0);
        private LivingEntity target;
        private Location wanderTo;
        private int wanderTime;
        private int leapTicks = -1;
        private int leapLength;
        private Vector leapFrom;
        private Vector leapTo;
        private int leapCooldown = 200;
        private int biteCooldown;
        private int forcedBite;
        private int riderLeap = -1;
        private int digSound;
        private int randomLeap = 400 + ThreadLocalRandom.current().nextInt(400);
        private int outOfGround;
        private Location lastHead;

        GumWorm(ModMobType type) {
            this.type = type;
        }

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            switch (event.getCause()) {
                // Mod: immune to being in walls, falling and cramming, and fire immune (EntityType fireImmune).
                case SUFFOCATION, FALL, FLY_INTO_WALL, CRAMMING, DROWNING, FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE -> {
                    entity.setFireTicks(0);
                    event.setCancelled(true);
                    return;
                }
                default -> {
                }
            }
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                Entity damager = byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                    ? shooter : byEntity.getDamager();
                if (damager instanceof Player player && player.equals(rider())) {
                    event.setCancelled(true);
                    return;
                }
                if (damager instanceof LivingEntity living && living != entity && !isSegment(living)) {
                    target = living;
                }
            }
        }

        /** The rider forces the mouth open by swinging (mod {@code isMouthForcedOpen}). */
        void forceBite() {
            forcedBite = 10;
        }

        Player rider() {
            if (segments.size() <= RIDING_SEGMENT) {
                return null;
            }
            for (Entity passenger : segments.get(RIDING_SEGMENT).getPassengers()) {
                if (passenger instanceof Player player) {
                    return player;
                }
            }
            return null;
        }

        /** Seats a player on the riding segment (mod {@code onMounted}). */
        boolean mount(Player player) {
            if (segments.size() <= RIDING_SEGMENT || rider() != null) {
                return false;
            }
            return segments.get(RIDING_SEGMENT).addPassenger(player);
        }

        Location ridingSegmentLocation() {
            return segments.size() <= RIDING_SEGMENT ? null : segments.get(RIDING_SEGMENT).getLocation();
        }

        private boolean isSegment(Entity entity) {
            return entity instanceof Interaction && segments.contains(entity);
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            if (segments.isEmpty()) {
                spawnSegments(entity, context);
            }
            replaceLostSegments(entity, context);
            forwardSegmentHits(entity);
            leapCooldown = Math.max(0, leapCooldown - ticks);
            biteCooldown = Math.max(0, biteCooldown - ticks);
            forcedBite = Math.max(0, forcedBite - ticks);
            if (target != null && (!target.isValid() || target.isDead() || target.getWorld() != entity.getWorld()
                || target.getLocation().distance(entity.getLocation()) > 128.0
                || target instanceof Player player && (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR))) {
                target = null;
            }
            Player rider = rider();
            for (int t = 0; t < ticks; t++) {
                Vector step = rider != null ? riding(entity, rider) : leapTicks >= 0 ? leap(entity) : target != null
                    ? chase(entity, context) : wander(entity);
                move(entity, step);
            }
            boolean mouthOpen = rider != null ? forcedBite > 0
                : target != null && target.getLocation().distance(entity.getLocation()) < 15.0 + target.getWidth();
            if (mouthOpen && biteCooldown <= 0 && bite(entity, rider)) {
                biteCooldown = leapTicks >= 0 ? 100 : 20;
                leapCooldown = Math.max(leapCooldown, 300);
                context.mobs().playSound(entity, type, "attack");
            }
            boolean moving = animation.moving();
            // Mod GumWormEntity: the head opens its mouth to bite; the segments only walk or idle.
            animation.update(entity, context, ticks, mouthOpen ? "mouth_open" : null);
            syncSegments(context, moving ? "walk" : "idle");
            dig(entity, context, ticks);
            followSegments(entity);
        }

        private void syncSegments(ModMobContext context, String wanted) {
            if (wanted.equals(segmentAnimation)) {
                return;
            }
            segmentAnimation = wanted;
            for (JavaModelHandle handle : segmentModels) {
                if (handle != null) {
                    context.models().animate(handle, wanted);
                }
            }
        }

        /** Segments whose chunk unloaded (or that were killed) grow back behind the one in front. */
        private void replaceLostSegments(LivingEntity entity, ModMobContext context) {
            Location front = entity.getLocation();
            for (int i = 0; i < segments.size(); i++) {
                Interaction segment = segments.get(i);
                if (!segment.isValid()) {
                    Location at = front.clone().add(heading.clone().multiply(-SEGMENT_SPACING));
                    if (!at.isChunkLoaded()) {
                        return;
                    }
                    if (segmentModels.get(i) != null) {
                        context.models().close(segmentModels.get(i));
                    }
                    segment = newSegment(entity, at);
                    segments.set(i, segment);
                    lastHits.set(i, 0L);
                    segmentModels.set(i, attachModel(context, segment, i));
                    segmentAnimation = null;
                }
                front = segment.getLocation();
            }
        }

        private static Interaction newSegment(LivingEntity entity, Location at) {
            return at.getWorld().spawn(at, Interaction.class, spawned -> {
                spawned.setInteractionWidth(2.0F);
                spawned.setInteractionHeight(2.0F);
                spawned.setResponsive(true);
                spawned.setPersistent(false);
                spawned.getPersistentDataContainer().set(SEGMENT_OF, PersistentDataType.STRING, entity.getUniqueId().toString());
            });
        }

        private static JavaModelHandle attachModel(ModMobContext context, Interaction segment, int index) {
            try {
                // Mod GumWormSegmentRenderer: the two textures alternate along the body.
                return context.models().attach(segment, index % 2 == 0 ? "alexscaves_gum_worm_segment"
                    : "alexscaves_gum_worm_segment_alt");
            } catch (RuntimeException failure) {
                return null;
            }
        }

        private void spawnSegments(LivingEntity entity, ModMobContext context) {
            int count = 15 + ThreadLocalRandom.current().nextInt(5);
            Location at = entity.getLocation();
            Vector back = heading.clone().multiply(-SEGMENT_SPACING);
            for (int i = 0; i < count; i++) {
                at = at.clone().add(back);
                if (!at.isChunkLoaded()) {
                    at = entity.getLocation();
                }
                Interaction segment = newSegment(entity, at);
                segments.add(segment);
                lastHits.add(0L);
                segmentModels.add(attachModel(context, segment, i));
            }
            segmentAnimation = null;
        }

        /** Mod: hurting a segment hurts the worm. */
        private void forwardSegmentHits(LivingEntity entity) {
            for (int i = 0; i < segments.size(); i++) {
                Interaction segment = segments.get(i);
                Interaction.PreviousInteraction attack = segment.getLastAttack();
                if (attack == null || attack.getTimestamp() == lastHits.get(i)) {
                    continue;
                }
                lastHits.set(i, attack.getTimestamp());
                if (attack.getPlayer() instanceof Player player && player.isOnline() && !player.equals(rider())) {
                    // Vanilla attack strength: 0.2 + cooldown^2 * 0.8 of the damage.
                    float charge = player.getAttackCooldown();
                    entity.damage(CandyMobs.attribute(player, Attribute.ATTACK_DAMAGE, 1.0) * (0.2 + charge * charge * 0.8), player);
                    player.resetCooldown();
                }
            }
        }

        /** Mod {@code GumWormRidingGoal}: forwards at the rider's turn, hugging the ground; jump leaps. */
        private Vector riding(LivingEntity entity, Player rider) {
            org.bukkit.Input input = rider.getCurrentInput();
            double turn = (input.isLeft() ? -1 : 0) + (input.isRight() ? 1 : 0);
            Vector flat = heading.clone().setY(0);
            if (flat.lengthSquared() < 1.0E-4) {
                flat = rider.getLocation().getDirection().setY(0);
            }
            flat.normalize().rotateAroundY(Math.toRadians(-turn * 8.0));
            if (riderLeap < 0 && input.isJump() && leapCooldown <= 0) {
                riderLeap = 0;
                leapCooldown = 40;
            }
            if (riderLeap >= 0) {
                riderLeap++;
                double f = riderLeap / 15.0;
                if (riderLeap > 15) {
                    riderLeap = -1;
                }
                heading = flat;
                return flat.clone().multiply(1.0).setY(Math.sin(f * Math.PI * 1.5) * 1.0);
            }
            heading = flat;
            // Mod getRiderPosition keeps the rider at the surface: the ridden worm swims just under the ground ahead,
            // and does not go into a wall it cannot climb.
            Location ahead = entity.getLocation().add(flat.clone().multiply(0.6));
            Double surface = surfaceNear(ahead, 3);
            if (surface == null) {
                return new Vector();
            }
            double vertical = Math.max(-0.8, Math.min(0.8, surface - 1.5 - entity.getLocation().getY()));
            return flat.clone().multiply(0.6).setY(vertical);
        }

        /** Mod {@code GumWormAttackGoal}: dig towards the target, or leap at it from more than 13 blocks away. */
        private Vector chase(LivingEntity entity, ModMobContext context) {
            Vector toward = target.getLocation().toVector().subtract(entity.getLocation().toVector());
            double distance = toward.length();
            if (distance > 13.0 && leapCooldown <= 0 && underground(entity)) {
                // Leaps reach at most 32 blocks (the mod's leap pushes about that far).
                Vector aim = toward.clone().multiply(Math.min(1.0, 32.0 / distance));
                startLeap(entity, entity.getLocation().toVector().add(aim), 15 + ThreadLocalRandom.current().nextInt(8));
                leapCooldown = 330;
                return new Vector();
            }
            steer(toward);
            return heading.clone().multiply(0.35);
        }

        /** Mod {@code GumWormDigRandomlyGoal} and {@code LeapRandomlyGoal}: wander underground, leaping out now and then. */
        private Vector wander(LivingEntity entity) {
            if (--randomLeap <= 0) {
                randomLeap = 400 + ThreadLocalRandom.current().nextInt(400);
                Location surface = surfaceAbove(entity.getLocation(), 8);
                if (surface != null) {
                    Vector flat = heading.clone().setY(0);
                    Vector out = surface.toVector().add(flat.lengthSquared() < 1.0E-4 ? new Vector()
                        : flat.normalize().multiply(12.0));
                    startLeap(entity, out, 20);
                    return new Vector();
                }
            }
            if (wanderTo == null || --wanderTime <= 0 || wanderTo.distanceSquared(entity.getLocation()) < 9.0) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                Location from = entity.getLocation();
                Location surface = surfaceAbove(from, 24);
                double top = surface == null ? from.getY() : surface.getY() - 4;
                wanderTo = from.clone().add(random.nextInt(-24, 25), 0, random.nextInt(-24, 25));
                wanderTo.setY(Math.max(from.getWorld().getMinHeight() + 6, top - random.nextInt(12)));
                wanderTime = 200;
            }
            steer(wanderTo.toVector().subtract(entity.getLocation().toVector()));
            return heading.clone().multiply(0.25);
        }

        private void steer(Vector desired) {
            if (desired.lengthSquared() < 1.0E-6) {
                return;
            }
            heading = heading.clone().multiply(0.85).add(desired.clone().normalize().multiply(0.15)).normalize();
        }

        private void startLeap(LivingEntity entity, Vector to, int length) {
            leapFrom = entity.getLocation().toVector();
            leapTo = to.clone();
            leapLength = length;
            leapTicks = 0;
        }

        /** A leap: an arc up to 8 blocks above the line from where it starts to where it aims. */
        private Vector leap(LivingEntity entity) {
            leapTicks++;
            double f = Math.min(1.0, (double) leapTicks / leapLength);
            Vector along = leapTo.clone().subtract(leapFrom);
            Vector at = leapFrom.clone().add(along.clone().multiply(f)).add(new Vector(0, Math.sin(f * Math.PI) * 8.0, 0));
            if (leapTicks >= leapLength) {
                leapTicks = -1;
                leapCooldown = Math.max(leapCooldown, 300);
            }
            Vector step = at.subtract(entity.getLocation().toVector());
            if (step.lengthSquared() > 1.0E-6) {
                heading = step.clone().normalize();
            }
            return step;
        }

        /** The walkable surface (air over solid) of a column within {@code range} blocks of the location, or null. */
        private static Double surfaceNear(Location at, int range) {
            if (!at.isChunkLoaded()) {
                return null;
            }
            int x = at.getBlockX();
            int z = at.getBlockZ();
            int y = at.getBlockY();
            for (int d = 0; d <= range; d++) {
                for (int sign : new int[] {1, -1}) {
                    int cy = y + d * sign;
                    Block block = at.getWorld().getBlockAt(x, cy, z);
                    if (!block.getType().isSolid() && block.getRelative(0, -1, 0).getType().isSolid()
                        && !block.getRelative(0, 1, 0).getType().isSolid()) {
                        return (double) cy;
                    }
                }
            }
            return null;
        }

        private static boolean underground(LivingEntity entity) {
            return entity.getLocation().getBlock().getType().isSolid();
        }

        private static Location surfaceAbove(Location from, int range) {
            Location at = from.clone();
            for (int i = 0; i < range; i++) {
                at.add(0, 1, 0);
                if (at.getBlockY() >= at.getWorld().getMaxHeight() - 1) {
                    return null;
                }
                if (!at.getBlock().getType().isSolid() && at.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                    return at;
                }
            }
            return null;
        }

        private void move(LivingEntity entity, Vector step) {
            Location next = entity.getLocation().add(step);
            // Never load chunks: it stops at the edge of the loaded world.
            if (!next.isChunkLoaded() || !next.getChunk().isEntitiesLoaded()) {
                wanderTo = null;
                heading.multiply(-1);
                return;
            }
            if (!diggable(next.getBlock())) {
                // Mod: blocks it cannot dig turn it up (or away).
                heading.setY(Math.abs(heading.getY()) + 0.8).normalize();
                next = entity.getLocation().add(0, 0.5, 0);
                if (!diggable(next.getBlock())) {
                    return;
                }
            }
            // Mod outOfGroundTime: out of the ground (and not leaping or ridden) for more than 10 ticks, it sinks back.
            boolean inAir = !next.getBlock().getType().isSolid() && !next.clone().add(0, -1, 0).getBlock().getType().isSolid();
            outOfGround = leapTicks < 0 && rider() == null && inAir ? outOfGround + 1 : 0;
            if (outOfGround > 10) {
                next.add(0, -0.5, 0);
            }
            next.setDirection(heading.lengthSquared() > 0 ? heading : new Vector(1, 0, 0));
            if (next.getY() < entity.getWorld().getMinHeight() + 2) {
                next.setY(entity.getWorld().getMinHeight() + 2);
            }
            entity.teleport(next, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }

        /** Mod {@code attackAllAroundMouth}: hurts everything around the head (3 blocks out while leaping). */
        private boolean bite(LivingEntity entity, Player rider) {
            // The mod's worm is 3.5 wide and 2.7 tall (the ravager base is smaller).
            Location at = entity.getLocation();
            double reach = leapTicks >= 0 ? 3.0 : 1.0;
            BoundingBox box = new BoundingBox(at.getX() - 1.75, at.getY(), at.getZ() - 1.75, at.getX() + 1.75, at.getY() + 2.7,
                at.getZ() + 1.75).expand(reach);
            boolean hitTarget = false;
            double damage = CandyMobs.attribute(entity, Attribute.ATTACK_DAMAGE, 9.0);
            for (Entity near : entity.getWorld().getNearbyEntities(box)) {
                if (!(near instanceof LivingEntity living) || near == entity || near.equals(rider) || isSegment(near)
                    || living instanceof org.bukkit.entity.ArmorStand
                    || living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR)
                    || near.getPersistentDataContainer().has(SEGMENT_OF)
                    || near.getPersistentDataContainer().has(new NamespacedKey("bigcasares", "custom_entity"))
                    && "alexscaves:gum_worm".equals(near.getPersistentDataContainer().get(
                    new NamespacedKey("bigcasares", "custom_entity"), PersistentDataType.STRING))) {
                    continue;
                }
                // Ridden, the bite is the rider's: PvP and protection rules see a player attacking.
                if (CandyMobs.abilityDamage(living, damage, rider != null ? rider : entity)) {
                    CandyMobs.knock(living, entity, 2.0);
                }
                hitTarget |= near.equals(target);
            }
            return hitTarget || rider != null;
        }

        private void dig(LivingEntity entity, ModMobContext context, int ticks) {
            Block head = entity.getLocation().getBlock();
            if (!head.getType().isSolid()) {
                return;
            }
            entity.getWorld().spawnParticle(Particle.BLOCK, entity.getLocation().add(0, 1, 0), 8, 1.2, 1.0, 1.2, 0,
                head.getBlockData());
            Location surface = surfaceAbove(entity.getLocation(), 6);
            if (surface != null) {
                surface.getWorld().spawnParticle(Particle.BLOCK, surface, 4, 1.0, 0.1, 1.0, 0,
                    surface.clone().add(0, -1, 0).getBlock().getBlockData());
            }
            if ((digSound -= ticks) <= 0) {
                digSound = 40;
                entity.getWorld().playSound(entity.getLocation(), "alexscaves:gum_worm_dig_loop", 1.0f, 1.0f);
            }
        }

        /** Mod segments: each keeps 2.5 blocks behind the one in front, facing it. */
        private void followSegments(LivingEntity entity) {
            Location front = entity.getLocation();
            if (lastHead != null && lastHead.getWorld() == front.getWorld() && lastHead.distanceSquared(front) < 1.0E-4) {
                return;
            }
            lastHead = front.clone();
            for (Interaction segment : segments) {
                if (!segment.isValid()) {
                    continue;
                }
                Vector offset = segment.getLocation().toVector().subtract(front.toVector());
                if (offset.lengthSquared() < 1.0E-4) {
                    offset = heading.clone().multiply(-1);
                }
                Location at = front.clone().add(offset.normalize().multiply(SEGMENT_SPACING));
                at.setDirection(front.toVector().subtract(at.toVector()));
                segment.teleport(at, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                front = at;
            }
        }

        @Override
        public void close(LivingEntity entity, ModMobContext context) {
            for (JavaModelHandle handle : segmentModels) {
                if (handle != null) {
                    context.models().close(handle);
                }
            }
            for (Interaction segment : segments) {
                segment.eject();
                segment.remove();
            }
            segments.clear();
            segmentModels.clear();
            lastHits.clear();
        }
    }
}
