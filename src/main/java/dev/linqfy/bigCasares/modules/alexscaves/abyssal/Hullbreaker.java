package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockStates;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobAnimator;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Hullbreaker (mod {@code HullbreakerEntity}, {@code HullbreakerMeleeGoal}, {@code HullbreakerInspectMobGoal} and
 * {@code HullbreakerPartEntity}): a 400-health sea monster, 4.65 by 4.5 blocks, that lurks deep in the Abyssal Chasm. It
 * ignores players until something that glows lights up the dark, then studies it, circles it and finally attacks it,
 * biting or bashing for 16 (24) and smashing the blocks in its way. It dies slowly and spits its loot out of its mouth.
 *
 * <p><b>Body.</b> The mod makes it a multipart entity (a head and four tail parts). Here the main body is a ghast, the
 * biggest vanilla hitbox (4 by 4) and, unlike a monster of the sea, one that flies without gravity in the water; it has
 * no AI of its own and this class moves it. The five parts are {@link Interaction} entities of the mod's sizes placed
 * along the body ({@link #partCenters}); hitting one hurts the hullbreaker (as the mod's {@code MultipartEntityMessage}
 * does), the way the gum worm's segments do.
 */
public final class Hullbreaker {
    /** Mod {@code createAttributes}: 400 health, 16 attack damage (a ghast has no such attribute, so it is a constant). */
    static final double MAX_HEALTH = 400.0;
    static final double ATTACK_DAMAGE = 16.0;
    static final double MOVEMENT_SPEED = 0.3;
    /** Mod entity dimensions. */
    static final double WIDTH = 4.65;
    static final double HEIGHT = 4.5;
    /** Mod {@code VerticalSwimmingMoveControl(this, 0.7F, 30.0F)}. */
    static final double SECOND_SPEED_MODIFIER = 0.7;
    static final double MAX_ROT_CHANGE = 30.0;
    /** Mod {@code travel}: in water the velocity keeps 0.9 of itself every tick. */
    static final double DRAG = 0.9;
    /** Mod follow range (the default of a monster's attributes). */
    static final double FOLLOW_RANGE = 16.0;
    static final int SEA_LEVEL = AbyssalMobs.SEA_LEVEL;

    /** Mod {@code hullbreaker.json}: one roll of each pool, with no conditions and no looting. */
    static final List<MobDrop> LOOT = List.of(
        new MobDrop("ac_enigmatic_engine", 1, 1, false, 1.0, 0.0, false),
        new MobDrop("ac_immortal_embryo", 1, 1, false, 1.0, 0.0, false),
        new MobDrop("ac_sea_glass_shards", 2, 5, false, 1.0, 0.0, false),
        new MobDrop("copper_ingot", 2, 8, false, 1.0, 0.0, false),
        new MobDrop("ac_scrap_metal", 2, 4, false, 1.0, 0.0, false));

    public static final ModMobType HULLBREAKER = new ModMobType("hullbreaker", AbyssalMobs.BIOME, "ghast",
        SpawnCategory.UNDERGROUND_WATER_CREATURE, Map.of("max_health", MAX_HEALTH), "alexscaves_hullbreaker", LOOT).asBoss();

    /** Mod parts: head (3 by 2), then the four tail parts (2 by 2, 2 by 1.5, 2.5 by 1.5, 1.5 by 1). */
    static final double[][] PART_SIZES = {{3.0, 2.0}, {2.0, 2.0}, {2.0, 1.5}, {2.5, 1.5}, {1.5, 1.0}};
    /** Mod offsets along the body of each part from the one it hangs from (the head and the first tail from the centre). */
    static final double[] PART_OFFSETS = {3.5, -3.5, -2.0, -2.65, -3.0};

    static final NamespacedKey LOOTED = new NamespacedKey("bigcasares", "ac_hullbreaker_looted");
    static final NamespacedKey PART_OF = new NamespacedKey("bigcasares", "ac_hullbreaker_part");

    /** Mod {@code Animation.create(...)} lengths. */
    enum Anim {
        NONE(0), PUZZLE(60), BITE(20), BASH(25), DIE(50);

        final int duration;

        Anim(int duration) {
            this.duration = duration;
        }
    }

    /** Mod {@code ACTagRegistry.GLOWING_ENTITIES} (vanilla part); the module's own ids are in {@link #GLOWING_MOBS}. */
    static final Set<EntityType> GLOWING_VANILLA = Set.of(EntityType.GLOW_SQUID, EntityType.BLAZE, EntityType.ENDERMAN,
        EntityType.ALLAY, EntityType.MAGMA_CUBE, EntityType.PHANTOM, EntityType.WARDEN);
    static final Set<String> GLOWING_MOBS = Set.of("teletor", "magnetron", "boundroid", "notor", "nucleeper", "radgill",
        "raycat", "gossamer_worm", "tremorzilla");

    private Hullbreaker() {
    }

    public static void register(ModMobCatalog catalog) {
        catalog.register(HULLBREAKER, (entity, type) -> {
            if (entity instanceof Mob mob) {
                Bukkit.getMobGoals().removeAllGoals(mob);
                // The module steers it with velocities; a ghast would otherwise vanish in peaceful (the mod's does not).
                mob.setAware(false);
                mob.setDespawnInPeacefulOverride(TriState.FALSE);
            }
            return new Behaviour();
        });
        // Mod checkHullbreakerSpawnRules: in water and more than 25 blocks under the sea.
        catalog.spawnRule(HULLBREAKER.id(), (block, random) -> spawnAllowed(block.getY()));
    }

    static boolean spawnAllowed(int y) {
        return y < SEA_LEVEL - 25;
    }

    // --- pure rules ---

    /**
     * Mod {@code VerticalSwimmingMoveControl.tick} for a destination: the push along the facing, the rise and the most it
     * may turn in a tick. The speed enters twice (as the push and as the input, with the 0.98 the input loses), the rise is
     * {@code 0.4 * f1 * (dy / distance)}, and within {@code width + 1.4} blocks it slows to 0.7 and turns faster.
     *
     * @param speed the navigation speed modifier (1.0 wandering, 1.4 circling, 1.6 chasing)
     */
    static Drive drive(double dx, double dy, double dz, double speed) {
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-6) {
            return new Drive(0.0, 0.0, 0.0);
        }
        double horizontal = Math.hypot(dx, dz);
        double f1 = speed * MOVEMENT_SPEED * SECOND_SPEED_MODIFIER;
        double lift = f1 * (dy / distance) * 0.4;
        double turn = MAX_ROT_CHANGE;
        if (horizontal < WIDTH + 1.4) {
            f1 *= 0.7;
            turn = horizontal < 0.3 ? 0.0 : Math.max(40.0, MAX_ROT_CHANGE);
        }
        double push = distance > 0.3 ? f1 * f1 * 0.98 : 0.0;
        return new Drive(push, lift, turn);
    }

    /** @param push added along the facing each tick; @param lift added upwards; @param turn degrees it may turn a tick */
    record Drive(double push, double lift, double turn) {
    }

    /** Velocity carried from tick to tick (it keeps {@link #DRAG} of itself), so it speeds up and coasts as the mod's. */
    static final class Momentum {
        double x;
        double y;
        double z;

        /** Advances {@code ticks} ticks along {@code yaw} and returns the mean displacement of a tick. */
        Vector advance(Drive drive, double yaw, int ticks) {
            double forwardX = -Math.sin(Math.toRadians(yaw));
            double forwardZ = Math.cos(Math.toRadians(yaw));
            double sx = 0;
            double sy = 0;
            double sz = 0;
            for (int i = 0; i < ticks; i++) {
                x += forwardX * drive.push();
                z += forwardZ * drive.push();
                y += drive.lift();
                sx += x;
                sy += y;
                sz += z;
                x *= DRAG;
                y *= DRAG;
                z *= DRAG;
            }
            return new Vector(sx / ticks, sy / ticks, sz / ticks);
        }

        void scale(double factor) {
            x *= factor;
            y *= factor;
            z *= factor;
        }

        void add(Vector push) {
            x += push.getX();
            y += push.getY();
            z += push.getZ();
        }
    }

    /**
     * Mod {@code tickMultipart} without the swim wave (the model does not bend): centres of the head and the four tail
     * parts, along the body from the middle of the hullbreaker.
     */
    static Vector[] partCenters(double x, double y, double z, double yaw) {
        double forwardX = -Math.sin(Math.toRadians(yaw));
        double forwardZ = Math.cos(Math.toRadians(yaw));
        double centerX = x;
        double centerY = y + HEIGHT * 0.5;
        double centerZ = z;
        Vector[] centers = new Vector[PART_SIZES.length];
        double along = 0;
        for (int i = 0; i < centers.length; i++) {
            // The head and the first tail hang from the centre; each other part from the one before it.
            along = i <= 1 ? PART_OFFSETS[i] : along + PART_OFFSETS[i];
            centers[i] = new Vector(centerX + forwardX * along, centerY, centerZ + forwardZ * along);
        }
        return centers;
    }

    /** Mod {@code orbitAroundPos}: a point on the circle around the spot where it started circling, a block lower. */
    static Vector orbit(Vector around, double distance, boolean clockwise, int phaseTime, int maxPhaseTime) {
        double angle = 3.0 * Math.PI * (clockwise ? -phaseTime : phaseTime) / maxPhaseTime;
        return around.clone().add(new Vector(distance * Math.sin(angle), -1.0, distance * Math.cos(angle)));
    }

    /** Mod {@code HullbreakerInspectMobGoal} phase length: {@code 60 + 40 * min(0, 5 - interest)} (a quirk of the mod). */
    static int stareTime(int interest) {
        return 60 + 40 * Math.min(0, 5 - interest);
    }

    static int circleTime(int interest) {
        return 120 + 80 * Math.min(0, 5 - interest);
    }

    static double circleDistance(int interest) {
        return 12 + Math.min(0, 5 - interest) * 3;
    }

    /** Mod {@code HullbreakerEntity.m_6469_}: projectiles do 65 % to the body. */
    static double bodyDamage(double amount, boolean projectile) {
        return projectile ? amount * 0.65 : amount;
    }

    /** Whether a hit leaves it dead; the module then starts its death instead (see {@link Behaviour#startDying}). */
    static boolean lethal(double health, double finalDamage) {
        return finalDamage >= health;
    }

    /** Wrapped difference limited to {@code maxChange} (mod {@code MoveControl.rotlerp}). */
    static float rotate(float current, float target, float maxChange) {
        float delta = target - current;
        delta = delta - 360.0F * (float) Math.floor((delta + 180.0F) / 360.0F);
        return current + Math.max(-maxChange, Math.min(maxChange, delta));
    }

    static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    static float pitchTo(double dx, double dy, double dz) {
        return (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    /** Mod {@code GLOWING_TARGET}: in water, and glowing, of a glowing kind or (not here) riding a submarine with lights. */
    static boolean glowing(Entity entity, ModMobContext context) {
        if (!(entity instanceof LivingEntity living) || !living.isInWater()) {
            return false;
        }
        if (living.hasPotionEffect(PotionEffectType.GLOWING)) {
            return true;
        }
        Optional<String> module = context.mobs().typeOf(entity).map(ModMobType::id);
        return module.isPresent() ? GLOWING_MOBS.contains(module.get()) : GLOWING_VANILLA.contains(entity.getType());
    }

    static boolean attackable(Entity entity) {
        return entity instanceof LivingEntity living && living.isValid() && !living.isDead()
            && !(living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR));
    }

    /** The mod's hullbreaker the {@code Interaction} part belongs to. */
    public static Optional<UUID> partOwner(Entity entity) {
        String id = entity.getPersistentDataContainer().get(PART_OF, PersistentDataType.STRING);
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
    }

    // --- behaviour ---

    static final class Behaviour implements ModMobBehaviour {
        /** Its own death sequence already played the mod's death animation before the entity really dies. */
        @Override
        public String deathAnimation(org.bukkit.entity.LivingEntity entity) {
            return null;
        }

        private final Interaction[] parts = new Interaction[PART_SIZES.length];
        private final long[] lastHits = new long[PART_SIZES.length];
        private final Momentum momentum = new Momentum();
        private Anim animation = Anim.NONE;
        private int animationTick;
        private int interest;
        private int age;
        private int scanClock;
        private int blockBreakCooldown;
        private int dryTicks;
        private int stuck;
        private Location lastPosition;
        private Vector expectedVelocity = new Vector();
        private ModMobContext context;
        private final MobAnimator anim = new MobAnimator();

        private UUID target;
        private int unseen;
        /** Only a target taken for being hurt is forgotten at 16 blocks or 60 ticks unseen (mod HurtByTargetGoal). */
        private boolean targetByHurt = true;
        private boolean melee;

        private UUID inspecting;
        private boolean staring;
        private boolean clockwise;
        private int phaseTime;
        private int maxPhaseTime;
        private Vector startCircling;
        private Location stareGoal;

        private Location wanderGoal;

        private boolean dying;
        private boolean finishing;
        private int nextSpit;
        private List<ItemStack> spit = new ArrayList<>();
        private UUID killer;

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            this.context = context;
            age += ticks;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            boolean inWater = entity.isInWater();
            // Mod: grounded (on the floor out of the water) is the land pose.
            anim.update(entity, context, ticks, !inWater && entity.isOnGround() ? "land" : null);
            // A ghast never falls by itself: out of the water the fall is applied here (0.08 a tick, 0.98 drag).
            entity.setGravity(false);
            if (!inWater && !dying) {
                Vector fall = entity.getVelocity();
                if (entity.isOnGround()) {
                    entity.setVelocity(new Vector(fall.getX() * 0.6, Math.min(0.0, fall.getY()), fall.getZ() * 0.6));
                } else {
                    double y = fall.getY();
                    for (int i = 0; i < ticks; i++) {
                        y = (y - 0.08) * 0.98;
                    }
                    entity.setVelocity(new Vector(fall.getX(), y, fall.getZ()));
                }
            }
            if (inWater) {
                // A ghast breathes air; the mod's hullbreaker is a sea animal.
                entity.setRemainingAir(entity.getMaximumAir());
                dryTicks = 0;
            } else {
                dryTicks += ticks;
                // WaterAnimal.handleAirSupply: 300 ticks of air out of the water, then 2 damage every second.
                if (!dying && dryTicks > 320 && (dryTicks - 320) % 20 < ticks) {
                    entity.damage(2.0, DamageSource.builder(DamageType.DROWN).build());
                }
            }
            if (dying) {
                tickDying(entity, ticks);
                return;
            }
            ensureParts(entity);
            forwardPartHits(entity);
            validateTarget(entity, ticks);
            // Mod tick: partly out of the water (less than the height minus 1 of water) it sinks.
            if (inWater && !fluidAt(entity, HEIGHT - 1.0)) {
                momentum.y -= 0.05 * ticks;
            }
            LivingEntity prey = target();
            Drive drive;
            destination = null;
            if (prey != null) {
                if (!melee) {
                    melee = true;
                    interest = 6;
                    endInspect(false);
                }
                drive = melee(entity, prey);
            } else {
                if (melee) {
                    melee = false;
                    interest = 0;
                }
                drive = inspecting != null || inspectStarts(entity, context, ticks) ? inspect(entity, context, ticks)
                    : wander(entity, random, ticks);
            }
            if (animation == Anim.BASH && animationTick > 10 && animationTick <= 20) {
                breakBlocks(entity);
            }
            if (blockBreakCooldown > 0) {
                blockBreakCooldown -= ticks;
            }
            if (inWater) {
                steer(entity, drive, ticks);
            }
            followParts(entity);
            advanceAnimation(ticks);
        }

        // --- goals ---

        /** Mod {@code HullbreakerMeleeGoal}: hit when near (bite or bash), swim at the target when not that close. */
        private Drive melee(LivingEntity entity, LivingEntity prey) {
            double distance = distance(entity, prey);
            double f = WIDTH + prey.getWidth();
            if (distance < f + 7.0 && animation == Anim.NONE) {
                boolean bite = ThreadLocalRandom.current().nextBoolean() && entity.hasLineOfSight(prey);
                tryAnimation(entity, bite ? Anim.BITE : Anim.BASH);
            }
            Drive drive = null;
            if (distance > f + 2.0) {
                face(entity, prey.getEyeLocation(), true);
                drive = towards(entity, prey.getLocation(), 1.6);
            }
            if (animation == Anim.BITE && animationTick > 10 && animationTick <= 14) {
                deal(entity, prey, 1.0F);
            }
            if (animation == Anim.BASH && animationTick > 10 && animationTick <= 12) {
                deal(entity, prey, 1.5F);
            }
            return drive;
        }

        private boolean tryAnimation(LivingEntity entity, Anim next) {
            if (animation != Anim.NONE) {
                return false;
            }
            animation = next;
            animationTick = 0;
            switch (next) {
                case BITE -> anim.action(entity, context, "bite");
                case BASH -> anim.action(entity, context, "bash");
                case PUZZLE -> anim.action(entity, context, "puzzle");
                default -> { }
            }
            if (entity.isInWater() && next != Anim.PUZZLE) {
                entity.getWorld().playSound(entity.getLocation(), "alexscaves:hullbreaker_attack", 1.0F, 1.0F);
            }
            return true;
        }

        private void advanceAnimation(int ticks) {
            if (animation != Anim.NONE) {
                animationTick += ticks;
                if (animationTick >= animation.duration) {
                    animation = Anim.NONE;
                    animationTick = 0;
                }
            }
        }

        /** Mod {@code checkAndDealDamage}: sees it and is within width + 5; 16 times the multiplier, knockback, the mount too. */
        private void deal(LivingEntity entity, LivingEntity prey, float multiplier) {
            if (!entity.hasLineOfSight(prey) || distance(entity, prey) >= WIDTH + prey.getWidth() + 5.0) {
                return;
            }
            double damage = ATTACK_DAMAGE * multiplier;
            prey.damage(damage, entity);
            Location from = entity.getLocation();
            Location at = prey.getLocation();
            prey.knockback(0.8 + 0.5 * multiplier, from.getX() - at.getX(), from.getZ() - at.getZ());
            Entity vehicle = prey.getVehicle();
            if (vehicle != null) {
                vehicle.setVelocity(prey.getVelocity());
                if (vehicle instanceof LivingEntity ridden) {
                    ridden.damage(damage * 0.5, entity);
                }
            }
        }

        /**
         * Mod {@code HullbreakerInspectMobGoal.canUse}: now and then (1 in 60 a tick and every 10 ticks) it looks for the
         * closest glowing thing within 80 blocks that it can see.
         */
        private boolean inspectStarts(LivingEntity entity, ModMobContext context, int ticks) {
            scanClock += ticks;
            boolean due = scanClock >= 10 || ThreadLocalRandom.current().nextDouble() < 1.0 - Math.pow(59.0 / 60.0, ticks);
            if (!due) {
                return false;
            }
            if (scanClock >= 10) {
                scanClock = 0;
            }
            LivingEntity closest = null;
            for (Entity near : entity.getNearbyEntities(80, 80, 80)) {
                // The line of sight is the costly check: only for what would become the closest.
                if (near != entity && near instanceof LivingEntity living && glowing(living, context)
                    && !(living instanceof Player player && player.getGameMode() == GameMode.SPECTATOR)
                    && partOwner(living).isEmpty()
                    && (closest == null || distanceSquared(entity, living) < distanceSquared(entity, closest))
                    && entity.hasLineOfSight(living)) {
                    closest = living;
                }
            }
            if (closest == null) {
                return false;
            }
            inspecting = closest.getUniqueId();
            staring = true;
            clockwise = ThreadLocalRandom.current().nextBoolean();
            phaseTime = 0;
            maxPhaseTime = stareTime(interest);
            startCircling = null;
            stareGoal = null;
            return true;
        }

        private void endInspect(boolean resetInterest) {
            inspecting = null;
            stareGoal = null;
            if (resetInterest && target() == null) {
                interest = 0;
            }
        }

        /**
         * Mod {@code HullbreakerInspectMobGoal.tick}: it stares at what glows (with the puzzled animation when it can see it
         * from within 18 blocks) or circles it, a phase at a time; each phase it grows more interested, and at 5 it
         * attacks if the prey still glows.
         */
        private Drive inspect(LivingEntity entity, ModMobContext context, int ticks) {
            Entity found = inspecting == null ? null : entity.getServer().getEntity(inspecting);
            if (!(found instanceof LivingEntity prey) || !prey.isValid() || prey.isDead() || prey.getWorld() != entity.getWorld()) {
                endInspect(true);
                return null;
            }
            double distance = distance(entity, prey);
            if (interest >= 5 && glowing(prey, context)) {
                if (!(prey instanceof Player player && player.getGameMode() == GameMode.CREATIVE)) {
                    target = prey.getUniqueId();
                    targetByHurt = false;
                    unseen = 0;
                }
                endInspect(false);
                return null;
            }
            // Mod: 1 in 20 each tick, over the ticks since the last call.
            if (ThreadLocalRandom.current().nextDouble() < 1.0 - Math.pow(19.0 / 20.0, ticks) && !glowing(prey, context)) {
                endInspect(true);
                return null;
            }
            if (animation == Anim.PUZZLE && animationTick > 50) {
                phaseTime = maxPhaseTime;
            }
            phaseTime += ticks;
            if (phaseTime > maxPhaseTime) {
                interest++;
                staring = ThreadLocalRandom.current().nextBoolean() && !staring;
                phaseTime = 0;
                startCircling = prey.getEyeLocation().toVector();
                maxPhaseTime = staring ? 120 : circleTime(interest);
            }
            Drive drive = null;
            if (staring) {
                face(entity, prey.getEyeLocation(), true);
                if (preyWatching(entity, prey) && distance < 18.0) {
                    if (animation == Anim.NONE) {
                        tryAnimation(entity, Anim.PUZZLE);
                    }
                    stareGoal = null;
                } else {
                    if (stareGoal == null || entity.getLocation().distanceSquared(stareGoal) < 30.0) {
                        stareGoal = prey.getEyeLocation().add(prey.getEyeLocation().getDirection().multiply(12.0));
                    }
                    drive = towards(entity, stareGoal, 1.0);
                }
            } else {
                if (startCircling == null) {
                    startCircling = prey.getEyeLocation().toVector();
                }
                Vector circle = orbit(startCircling, circleDistance(interest), clockwise, phaseTime, maxPhaseTime);
                drive = towards(entity, circle.toLocation(entity.getWorld()), 1.4);
                // Mod: the head turns 30 degrees to the side it circles.
            }
            return drive;
        }

        /** Mod {@code isPreyWatching}: anything but a player counts; a player needs a clear line within 128 blocks. */
        private boolean preyWatching(LivingEntity entity, LivingEntity prey) {
            if (!(prey instanceof Player)) {
                return true;
            }
            Entity lowest = prey.getVehicle() == null ? prey : prey.getVehicle();
            Location eyes = entity.getLocation().add(0, entity.getEyeHeight(), 0);
            Location other = lowest.getLocation().add(0, lowest instanceof LivingEntity living ? living.getEyeHeight() : 0, 0);
            double distance = eyes.distance(other);
            if (distance > 128.0) {
                return false;
            }
            return distance < 1.0E-3 || entity.getWorld().rayTraceBlocks(eyes, other.toVector().subtract(eyes.toVector()).normalize(),
                distance, FluidCollisionMode.NEVER, true) == null;
        }

        /** Mod {@code AnimalRandomlySwimGoal(this, 10, 35, 15, 1.0)}: 1 tick in 10 it picks a water spot and swims there. */
        private Drive wander(LivingEntity entity, ThreadLocalRandom random, int ticks) {
            if (wanderGoal == null) {
                if (entity.isInWater() && random.nextDouble() < 1.0 - Math.pow(0.9, ticks)) {
                    wanderGoal = findSwimToPos(entity, random);
                }
                return null;
            }
            if (entity.getLocation().distanceSquared(wanderGoal) <= 30.0 || stuck > 20) {
                wanderGoal = null;
                stuck = 0;
                return null;
            }
            return towards(entity, wanderGoal, 1.0);
        }

        /** Mod {@code findSwimToPos}: 15 tries within 35 of a spot at least 15 under the surface, in water with a clear line. */
        private Location findSwimToPos(LivingEntity entity, ThreadLocalRandom random) {
            World world = entity.getWorld();
            Location at = entity.getLocation();
            int surface = at.getBlockY();
            while (surface < world.getMaxHeight() && AbyssalMobs.fluid(world.getBlockAt(at.getBlockX(), surface, at.getBlockZ()))) {
                surface += 5;
            }
            int baseY = Math.min(surface - 15, at.getBlockY());
            int range = 35;
            for (int i = 0; i < 15; i++) {
                Block block = world.getBlockAt(at.getBlockX() + random.nextInt(range) - range / 2, baseY + random.nextInt(range) - range / 2,
                    at.getBlockZ() + random.nextInt(range) - range / 2);
                if (AbyssalMobs.fluid(block) && block.getY() > world.getMinHeight() + 1) {
                    Location spot = block.getLocation().add(0.5, 0.5, 0.5);
                    Vector line = spot.toVector().subtract(at.toVector());
                    double length = line.length();
                    if (length < 1.0E-3 || world.rayTraceBlocks(at, line.normalize(), length, FluidCollisionMode.NEVER, true) == null) {
                        return spot;
                    }
                }
            }
            return new Location(world, at.getBlockX() + 0.5, baseY + 0.5, at.getBlockZ() + 0.5);
        }

        // --- target ---

        private LivingEntity target() {
            if (target == null) {
                return null;
            }
            Entity found = context.plugin().getServer().getEntity(target);
            return found instanceof LivingEntity living && attackable(living) ? living : null;
        }

        /** Mod {@code HurtByTargetGoal} (must see it, follow range 16, forgets it after 60 ticks unseen). */
        private void validateTarget(LivingEntity entity, int ticks) {
            if (target == null) {
                return;
            }
            LivingEntity prey = target();
            if (prey == null || prey.getWorld() != entity.getWorld()
                || targetByHurt && distanceSquared(entity, prey) > FOLLOW_RANGE * FOLLOW_RANGE) {
                target = null;
                return;
            }
            if (!targetByHurt) {
                return;
            }
            if (entity.hasLineOfSight(prey)) {
                unseen = 0;
            } else if ((unseen += ticks) > 60) {
                target = null;
            }
        }

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            if (finishing) {
                return;
            }
            if (dying) {
                event.setCancelled(true);
                return;
            }
            if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING && entity.isInWater()) {
                // A ghast would drown in the sea it lives in.
                event.setCancelled(true);
                return;
            }
            Entity damager = null;
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                damager = byEntity.getDamager() instanceof org.bukkit.entity.Projectile projectile
                    && projectile.getShooter() instanceof Entity shooter ? shooter : byEntity.getDamager();
                event.setDamage(bodyDamage(event.getDamage(), byEntity.getDamager() instanceof org.bukkit.entity.Projectile));
            }
            if (damager instanceof LivingEntity living && living != entity && attackable(living) && partOwner(living).isEmpty()) {
                target = living.getUniqueId();
                targetByHurt = true;
                unseen = 0;
            }
            if (lethal(entity.getHealth(), event.getFinalDamage())) {
                event.setCancelled(true);
                startDying(entity, context, damager);
            }
        }

        // --- death ---

        /**
         * Mod {@code tickDeath}: it does not drop dead at once. For 50 ticks it plays its death animation, spitting its loot
         * (one random item every 7 ticks from tick 10) out of its mouth, and goes at tick 45 without experience.
         */
        private void startDying(LivingEntity entity, ModMobContext context, Entity cause) {
            dying = true;
            animation = Anim.DIE;
            animationTick = 0;
            nextSpit = 14;
            target = null;
            inspecting = null;
            interest = 0;
            killer = cause == null ? null : cause.getUniqueId();
            entity.setHealth(Math.min(1.0, entity.getHealth()));
            Player credit = cause instanceof Player player ? player : null;
            // Saved with the mob: one that unloads while dying and comes back must not give its loot twice.
            boolean looted = entity.getPersistentDataContainer().has(LOOTED, PersistentDataType.BYTE);
            entity.getPersistentDataContainer().set(LOOTED, PersistentDataType.BYTE, (byte) 1);
            spit = looted ? new ArrayList<>() : new ArrayList<>(context.mobs().rollDrops(HULLBREAKER, credit, false));
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:hullbreaker_" + (entity.isInWater() ? "" : "land_") + "death",
                soundVolume(), 0.9F + ThreadLocalRandom.current().nextFloat() * 0.2F);
            // Mod ANIMATION_DIE (50 ticks), the same length as the dying phase.
            anim.action(entity, context, "die");
        }

        private void tickDying(LivingEntity entity, int ticks) {
            animationTick += ticks;
            momentum.scale(Math.pow(DRAG, ticks));
            if (entity.isInWater()) {
                entity.setVelocity(new Vector(momentum.x, momentum.y, momentum.z));
            }
            if (animationTick > 10 && animationTick >= nextSpit && !spit.isEmpty()) {
                nextSpit += 7;
                ItemStack item = spit.remove(ThreadLocalRandom.current().nextInt(spit.size()));
                spitOut(entity, item);
            }
            if (animationTick > 45) {
                finish(entity);
            }
        }

        /** Mod {@code spawnAtLocation}: out of the mouth, forward, glowing and lasting longer. */
        private void spitOut(LivingEntity entity, ItemStack stack) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Vector forward = entity.getLocation().getDirection().setY(0);
            Vector mouth = partCenters(entity.getLocation().getX(), entity.getLocation().getY(), entity.getLocation().getZ(),
                entity.getLocation().getYaw())[0];
            Location at = new Location(entity.getWorld(), mouth.getX() - forward.getX() * 0.5,
                mouth.getY() - PART_SIZES[0][1] * 0.5 + 0.5, mouth.getZ() - forward.getZ() * 0.5);
            Vector velocity = forward.clone().add(new Vector(random.nextFloat() * 0.2 - 0.1, random.nextFloat() * 0.2 - 0.1,
                random.nextFloat() * 0.2 - 0.1));
            if (velocity.lengthSquared() > 1.0E-6) {
                velocity.normalize().multiply(0.8 + random.nextFloat() * 0.3);
            }
            Vector shot = velocity;
            entity.getWorld().dropItem(at, stack, (Item item) -> {
                item.setVelocity(shot);
                item.setGlowing(true);
                // Mod setExtendedLifetime: 38768 ticks instead of 6000 (here it lasts until a task removes it).
                item.setUnlimitedLifetime(true);
                context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), () -> {
                    if (item.isValid()) {
                        item.remove();
                    }
                }, 38768L);
            });
        }

        /** Mod tick 45 of the death: gone in a puff (status 60), without experience (the mod gives none). */
        private void finish(LivingEntity entity) {
            finishing = true;
            entity.getWorld().spawnParticle(Particle.POOF, entity.getLocation().add(0, HEIGHT * 0.5, 0), 40, 1.5, 1.5, 1.5, 0.02);
            Entity cause = killer == null ? null : entity.getServer().getEntity(killer);
            entity.damage(1.0E6, cause);
            if (entity.isValid() && !entity.isDead()) {
                entity.setHealth(0.0);
            }
        }

        @Override
        public String soundSuffix(LivingEntity entity, String suffix) {
            if (dying && !suffix.equals("idle")) {
                // The death sound plays when it starts dying.
                return null;
            }
            return entity.isInWater() ? suffix : "land_" + suffix;
        }

        /** Mod {@code ACMath}/{@code getSoundVolume() + 2}. */
        @Override
        public float soundVolume() {
            return 3.0F;
        }

        @Override
        public void onDeathDrops(LivingEntity entity, List<ItemStack> drops) {
            if (dying) {
                // It spat its loot out while it died; what it had not spat yet (killed meanwhile) comes out now.
                drops.clear();
                drops.addAll(spit);
                spit.clear();
            }
        }

        /** Mod getExperienceReward: a water animal with no experience of its own gives none (a ghast gives 5). */
        @Override
        public int experience(LivingEntity entity, int vanilla) {
            return 0;
        }

        // --- movement ---

        /** Where to look and how to steer to get to a point (at a navigation speed). */
        private Drive towards(LivingEntity entity, Location point, double speed) {
            Location at = entity.getLocation();
            destination = point;
            return drive(point.getX() - at.getX(), point.getY() - at.getY(), point.getZ() - at.getZ(), speed);
        }

        /** Faces a point at once (mod {@code lookAt(Anchor.EYES, ...)}). */
        private void face(LivingEntity entity, Location point, boolean snap) {
            Location eyes = entity.getLocation().add(0, entity.getEyeHeight(), 0);
            double dx = point.getX() - eyes.getX();
            double dy = point.getY() - eyes.getY();
            double dz = point.getZ() - eyes.getZ();
            if (snap && Math.hypot(dx, dz) > 1.0E-3) {
                entity.setRotation(yawTo(dx, dz), pitchTo(dx, dy, dz));
            }
        }

        /** Turns towards the destination as the move control does, then moves with the carried velocity. */
        private void steer(LivingEntity entity, Drive drive, int ticks) {
            Location at = entity.getLocation();
            Vector actual = entity.getVelocity();
            // Knockback and pushes since the last call join the carried velocity.
            Vector kick = actual.clone().subtract(expectedVelocity);
            if (kick.length() > 0.1) {
                momentum.add(kick);
            }
            if (lastPosition != null && lastPosition.getWorld() == at.getWorld() && drive != null) {
                // Mod: it ran into something; what it carried is lost.
                double expected = Math.hypot(momentum.x, momentum.z);
                double moved = Math.hypot(at.getX() - lastPosition.getX(), at.getZ() - lastPosition.getZ());
                if (expected > 0.2 && moved < expected * ticks * 0.25) {
                    momentum.scale(0.3);
                    stuck += ticks;
                } else {
                    stuck = Math.max(0, stuck - ticks);
                }
            }
            lastPosition = at.clone();
            float yaw = at.getYaw();
            Drive step = drive == null ? new Drive(0, 0, 0) : drive;
            if (drive != null && destination != null) {
                double dx = destination.getX() - at.getX();
                double dz = destination.getZ() - at.getZ();
                if (Math.hypot(dx, dz) > 1.0E-3 && step.turn() > 0) {
                    yaw = rotate(yaw, yawTo(dx, dz), (float) (step.turn() * ticks));
                    entity.setRotation(yaw, at.getPitch());
                }
            }
            Vector displacement = momentum.advance(step, yaw, ticks);
            Vector velocity = displacement.multiply(1.0 / 0.9);
            expectedVelocity = velocity.clone().multiply(Math.pow(0.8, ticks));
            entity.setVelocity(velocity);
        }

        private Location destination;

        // --- blocks ---

        /**
         * Mod {@code breakBlock}: while it bashes (ticks 10 to 20) and goes for a player, the blocks in a box around its
         * head (grown 1.2 and stretched along its look) that have a collision, are not unbreakable and resist at most 15
         * are destroyed with their drops, slowing it. Blocks of the module itself are left alone: their resistance is not
         * known here.
         */
        private void breakBlocks(LivingEntity entity) {
            if (blockBreakCooldown > 0 || !(target() instanceof Player)) {
                return;
            }
            World world = entity.getWorld();
            if (Boolean.FALSE.equals(world.getGameRuleValue(GameRules.MOB_GRIEFING))) {
                return;
            }
            Location at = entity.getLocation();
            Vector head = partCenters(at.getX(), at.getY(), at.getZ(), at.getYaw())[0];
            BoundingBox box = BoundingBox.of(head.toLocation(world).toVector(), PART_SIZES[0][0] * 0.5, PART_SIZES[0][1] * 0.5,
                PART_SIZES[0][0] * 0.5).expand(1.2).expandDirectional(at.getDirection());
            boolean broke = false;
            for (int x = (int) Math.round(box.getMinX()); x <= (int) Math.round(box.getMaxX()); x++) {
                for (int y = (int) Math.round(box.getMinY()) - 1; y <= (int) Math.round(box.getMaxY()) + 1 && y <= 127; y++) {
                    for (int z = (int) Math.round(box.getMinZ()); z <= (int) Math.round(box.getMaxZ()); z++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (breakable(block) && new EntityChangeBlockEvent(entity, block, Material.AIR.createBlockData()).callEvent()) {
                            Vector velocity = entity.getVelocity();
                            momentum.x *= 0.6;
                            momentum.z *= 0.6;
                            entity.setVelocity(new Vector(velocity.getX() * 0.6, velocity.getY(), velocity.getZ() * 0.6));
                            block.breakNaturally();
                            broke = true;
                        }
                    }
                }
            }
            if (broke) {
                blockBreakCooldown = 3;
            }
        }

        // --- parts ---

        private void ensureParts(LivingEntity entity) {
            Location at = entity.getLocation();
            Vector[] centers = partCenters(at.getX(), at.getY(), at.getZ(), at.getYaw());
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null && parts[i].isValid()) {
                    continue;
                }
                Location spot = partLocation(entity.getWorld(), centers[i], i);
                if (!spot.isChunkLoaded()) {
                    continue;
                }
                double width = PART_SIZES[i][0];
                double height = PART_SIZES[i][1];
                parts[i] = entity.getWorld().spawn(spot, Interaction.class, part -> {
                    part.setInteractionWidth((float) width);
                    part.setInteractionHeight((float) height);
                    part.setResponsive(true);
                    part.setPersistent(false);
                    part.getPersistentDataContainer().set(PART_OF, PersistentDataType.STRING, entity.getUniqueId().toString());
                });
                lastHits[i] = 0L;
            }
        }

        private static Location partLocation(World world, Vector center, int index) {
            return new Location(world, center.getX(), center.getY() - PART_SIZES[index][1] * 0.5, center.getZ());
        }

        private void followParts(LivingEntity entity) {
            Location at = entity.getLocation();
            Vector[] centers = partCenters(at.getX(), at.getY(), at.getZ(), at.getYaw());
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null && parts[i].isValid()) {
                    parts[i].teleport(partLocation(at.getWorld(), centers[i], i));
                }
            }
        }

        /** Mod {@code MultipartEntityMessage}: hitting a part hurts the hullbreaker itself. */
        private void forwardPartHits(LivingEntity entity) {
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] == null || !parts[i].isValid()) {
                    continue;
                }
                Interaction.PreviousInteraction attack = parts[i].getLastAttack();
                if (attack == null || attack.getTimestamp() == lastHits[i]) {
                    continue;
                }
                lastHits[i] = attack.getTimestamp();
                if (attack.getPlayer() instanceof Player player && player.isOnline()) {
                    // The player hits the body: vanilla handles the strength, weapon wear, enchantments and events.
                    player.attack(entity);
                }
            }
        }

        @Override
        public void close(LivingEntity entity, ModMobContext context) {
            if (dying && !spit.isEmpty() && entity != null && entity.isValid() && !entity.isDead()) {
                // Unloading mid-death: the loot that was left comes out now rather than being lost.
                for (ItemStack item : spit) {
                    spitOut(entity, item);
                }
                spit.clear();
            }
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null) {
                    parts[i].remove();
                    parts[i] = null;
                }
            }
        }

        // --- helpers ---

        private static double distance(Entity a, Entity b) {
            return a.getLocation().distance(b.getLocation());
        }

        private static double distanceSquared(Entity a, Entity b) {
            return a.getLocation().distanceSquared(b.getLocation());
        }

        /** Whether water fills the column {@code up} blocks above the feet (the mod's fluid height check). */
        private static boolean fluidAt(LivingEntity entity, double up) {
            Location at = entity.getLocation().add(0, up, 0);
            return AbyssalMobs.fluid(at.getBlock());
        }
    }

    /** Mod condition: a collision, not wither-immune (the mod's {@code unmovable}), resistance at most 15. */
    static boolean breakable(Block block) {
        Material type = block.getType();
        return !type.isAir() && !block.isPassable() && !block.isLiquid() && type != Material.NOTE_BLOCK
            && !ModBlockStates.isModuleShaped(block) && type.getHardness() >= 0 && !Tag.WITHER_IMMUNE.isTagged(type) && type.getBlastResistance() <= 15.0F;
    }
}
