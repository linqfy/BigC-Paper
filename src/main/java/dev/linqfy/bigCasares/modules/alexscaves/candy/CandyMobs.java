package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.AnimatedMob;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobAnimator;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mobs of the Candy Cavity with the mod's stats, drops and abilities, each on a vanilla base with a close AI: the
 * Caniac on a vindicator (spinning candy cane arms and a lunge), the Gumbeeper on a creeper (it shoots bouncing
 * gumballs and bursts into them when it runs out), the Caramel Cube on a slime (three sizes, it splits), the Sweetish
 * Fish on a cod, the Candicorn on a horse (it charges what hurts it) and the Gummy Bear on a polar bear.
 */
public final class CandyMobs {
    private static final CaveBiomeId BIOME = CaveBiomeId.CANDY_CAVITY;

    public static final ModMobType CANIAC = new ModMobType("caniac", BIOME, "vindicator", SpawnCategory.MONSTER,
        Map.of("max_health", 38.0, "attack_damage", 2.0, "movement_speed", 0.25, "follow_range", 48.0),
        "alexscaves_caniac", List.of(MobDrop.of("ac_candy_cane", 0, 2), MobDrop.of("ac_sharpened_candy_cane", 0, 1)));
    public static final ModMobType GUMBEEPER = new ModMobType("gumbeeper", BIOME, "creeper", SpawnCategory.MONSTER,
        Map.of("max_health", 14.0, "armor", 4.0, "attack_damage", 4.0, "movement_speed", 0.2, "follow_range", 32.0),
        "alexscaves_gumbeeper", List.of(MobDrop.of("ac_gumball_pile", 0, 2), MobDrop.of("gunpowder", 0, 1)));
    /** Drops only from the smallest cube ({@link CandyMobListener}): the mod drops loot only at size 0. */
    public static final ModMobType CARAMEL_CUBE = new ModMobType("caramel_cube", BIOME, "slime", SpawnCategory.MONSTER,
        Map.of("max_health", 4.0, "attack_damage", 2.0, "movement_speed", 0.25),
        "alexscaves_caramel_cube", List.of());
    /** Mod {@code sweetish_fish.json}: one fish, without Looting. */
    public static final ModMobType SWEETISH_FISH = new ModMobType("sweetish_fish", BIOME, "cod", SpawnCategory.WATER_AMBIENT,
        Map.of("max_health", 4.0, "movement_speed", 0.25),
        "alexscaves_sweetish_fish", List.of(new MobDrop("ac_sweetish_fish_red", 1, 1, false, 1.0, 0.0, false)));
    public static final ModMobType CANDICORN = new ModMobType("candicorn", BIOME, "horse", SpawnCategory.CAVE_CREATURE,
        Map.of("max_health", 30.0, "attack_damage", 6.0, "movement_speed", 0.25, "follow_range", 64.0),
        "alexscaves_candicorn", List.of(MobDrop.of("ac_sweet_puff", 0, 1)));
    public static final ModMobType GUMMY_BEAR = new ModMobType("gummy_bear", BIOME, "polar_bear", SpawnCategory.CAVE_CREATURE,
        Map.of("max_health", 36.0, "attack_damage", 4.0, "movement_speed", 0.25),
        "alexscaves_gummy_bear", List.of(MobDrop.of("ac_gelatin_red", 0, 3), MobDrop.of("ac_sweetish_fish_red", 0, 1)));

    public static final List<ModMobType> ALL = List.of(CANIAC, GUMBEEPER, CARAMEL_CUBE, SWEETISH_FISH, CANDICORN, GUMMY_BEAR);

    /** Mod {@code GumbeeperEntity}: gumballs it can shoot before it has to burst. */
    static final int GUMBALLS = 6;
    static final MobDrop CARAMEL_DROP = MobDrop.of("ac_caramel", 0, 2);

    private CandyMobs() {
    }

    /**
     * @param frostedChocolate the mod's Gummy Bear spawn rule: only on frosted chocolate
     */
    public static void register(ModMobCatalog catalog, BlockData frostedChocolate) {
        catalog.register(CANIAC, (entity, type) -> {
            if (entity instanceof Raider raider) {
                raider.setCanJoinRaid(false);
            }
            return new Caniac(type);
        });
        catalog.register(GUMBEEPER, (entity, type) -> new Gumbeeper());
        catalog.register(CARAMEL_CUBE, (entity, type) -> {
            CaramelCube.applySize(entity, CaramelCube.size(entity).orElse(ThreadLocalRandom.current().nextInt(3)), false);
            return new CaramelCube();
        });
        catalog.register(SWEETISH_FISH, (entity, ignored) -> new AnimatedMob(CandyMobs::stranded));
        catalog.register(CANDICORN, (entity, type) -> new Candicorn(type));
        catalog.register(GUMMY_BEAR, (entity, ignored) -> new GummyBear());
        // Mod spawn rules: Caniacs and Gumbeepers pass 1 time in 10 (checkCaniacSpawnRules / checkGumbeeperSpawnRules),
        // Gummy Bears only spawn on frosted chocolate.
        catalog.spawnRule(CANIAC.id(), (block, random) -> random.nextInt(10) == 0);
        catalog.spawnRule(GUMBEEPER.id(), (block, random) -> random.nextInt(10) == 0);
        catalog.spawnRule(GUMMY_BEAR.id(), (block, random) -> frostedChocolate.equals(block.getRelative(0, -1, 0).getBlockData()));
    }

    /** Mod {@code landProgress}: a fish out of the water flops on its side ({@code land}). */
    static String stranded(LivingEntity entity) {
        return !entity.isInWater() && entity.isOnGround() ? "land" : null;
    }

    static double attribute(LivingEntity entity, Attribute attribute, double fallback) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    static void setBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Targets the mod's arm swings and charges hurt: living things that are not this mob's kind, its owner, players in
     * creative or spectator, or entities riding with it.
     */
    static boolean hurtable(LivingEntity self, Entity other, ModMobContext context) {
        if (!(other instanceof LivingEntity living) || other == self || !living.isValid() || living.isDead()) {
            return false;
        }
        if (living instanceof Player player && (player.getGameMode() == org.bukkit.GameMode.CREATIVE
            || player.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
            return false;
        }
        if (self instanceof Tameable tameable && tameable.getOwner() != null && tameable.getOwner().getUniqueId().equals(living.getUniqueId())) {
            return false;
        }
        if (self.getPassengers().contains(living) || living.getPassengers().contains(self)) {
            return false;
        }
        // Mod isFriendlyFire: a Licowitch's possessed mobs spare her and each other.
        String owner = self.getPersistentDataContainer().get(CandyFolk.POSSESSED_BY, PersistentDataType.STRING);
        if (owner != null && (owner.equals(living.getUniqueId().toString())
            || owner.equals(living.getPersistentDataContainer().get(CandyFolk.POSSESSED_BY, PersistentDataType.STRING)))) {
            return false;
        }
        return context.mobs().typeOf(living).map(type -> !type.equals(context.mobs().typeOf(self).orElse(null))).orElse(true);
    }

    /** Hits done by the module's own abilities, so the Caniac's plain vindicator melee can be told apart. */
    static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

    static boolean abilityDamage(LivingEntity target, double amount, Entity source) {
        double before = target.getHealth();
        int noDamage = target.getNoDamageTicks();
        ABILITY_HIT.set(true);
        try {
            target.damage(amount, source);
        } finally {
            ABILITY_HIT.set(false);
        }
        return target.getHealth() < before || target.getNoDamageTicks() > noDamage || target.isDead();
    }

    /**
     * The Caniac spins its candy cane arms faster the longer it chases (up to 30 degrees a tick); while an arm is past
     * 75 degrees of its half turn it hurts what is in a box in front of that arm and within 3.15 blocks (1 in 10 hits
     * strong: 1.5 times the damage and more knockback). It runs at 0.4 while chasing. From 4 to 12 blocks away it may
     * lunge (1 in 15 a tick): it leaps at the target, pulls it in and hits it for 3. It only goes for players and
     * whatever hurt it ({@link CandyMobListener}).
     */
    static final class Caniac implements ModMobBehaviour {
        /** Mod {@code ANIMATION_LUNGE}: 35 ticks; the goal holds still for all of them. */
        static final int LUNGE_TICKS = 35;
        private final ModMobType type;
        private final MobAnimator animation = new MobAnimator();
        private int chaseTime;
        private float leftArm;
        private float rightArm;
        private boolean secondArm;
        private int swingSound;
        private int lunge = -1;

        Caniac(ModMobType type) {
            this.type = type;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            LivingEntity target = entity instanceof Mob mob ? mob.getTarget() : null;
            if (target == null || !target.isValid() || target.getWorld() != entity.getWorld()) {
                chaseTime = 0;
                secondArm = false;
                lunge = -1;
                setBase(entity, Attribute.MOVEMENT_SPEED, 0.25);
                animation.update(entity, context, ticks, null);
                return;
            }
            setBase(entity, Attribute.MOVEMENT_SPEED, 0.4);
            double distance = entity.getLocation().distance(target.getLocation());
            // Mod CaniacMeleeGoal: setRunning(true) while it closes in (not during the lunge, not once in reach).
            animation.update(entity, context, ticks,
                lunge < 0 && distance > entity.getWidth() + target.getWidth() ? "run" : null);
            if (lunge >= 0) {
                lunge(entity, target, distance, ticks);
                return;
            }
            chaseTime += ticks;
            float spin = Math.min(30.0F, chaseTime * 5.0F);
            if ((swingSound -= ticks) < 0) {
                swingSound = 5 + ThreadLocalRandom.current().nextInt(10);
                context.mobs().playSound(entity, type, "swing");
            }
            for (int t = 0; t < ticks; t++) {
                leftArm += spin;
                if (leftArm % 360.0F > 180.0F) {
                    secondArm = true;
                }
                if (secondArm) {
                    rightArm += spin;
                }
                if (wrapped(leftArm) % 180.0F > 75.0F) {
                    swing(entity, context, true);
                }
                if (secondArm && wrapped(rightArm) % 180.0F > 75.0F) {
                    swing(entity, context, false);
                }
            }
            if (distance < 12.0 && distance > 4.0 && ThreadLocalRandom.current().nextDouble() < 1.0 - Math.pow(14.0 / 15.0, ticks)) {
                lunge = 0;
                context.mobs().playSound(entity, type, "attack");
                animation.action(entity, context, "lunge");
            }
        }

        /** Vanilla {@code Mth.wrapDegrees}. */
        static float wrapped(float degrees) {
            float wrapped = degrees % 360.0F;
            if (wrapped >= 180.0F) {
                wrapped -= 360.0F;
            }
            if (wrapped < -180.0F) {
                wrapped += 360.0F;
            }
            return wrapped;
        }

        /** Mod {@code hurtMobsFromArmSwing}: a 2-block box at (±0.75, 1, 1.25) from the Caniac, turned with its body. */
        private void swing(LivingEntity entity, ModMobContext context, boolean left) {
            boolean strong = ThreadLocalRandom.current().nextFloat() < 0.1F;
            double damage = attribute(entity, Attribute.ATTACK_DAMAGE, 2.0) * (strong ? 1.5 : 1.0);
            double knockback = strong ? 1.0 : 0.1;
            double yaw = Math.toRadians(entity.getLocation().getYaw());
            double ox = left ? 0.75 : -0.75;
            double oz = 1.25;
            // Vec3.yRot(-yaw): x' = x cos + z sin, z' = z cos - x sin, with Minecraft's yaw (south = 0).
            double rx = ox * Math.cos(-yaw) + oz * Math.sin(-yaw);
            double rz = oz * Math.cos(-yaw) - ox * Math.sin(-yaw);
            Location arm = entity.getLocation().add(rx, 1.0, rz);
            BoundingBox box = new BoundingBox(arm.getX() - 1, arm.getY() - 1, arm.getZ() - 1, arm.getX() + 1, arm.getY() + 1,
                arm.getZ() + 1);
            for (Entity near : entity.getWorld().getNearbyEntities(box)) {
                if (hurtable(entity, near, context) && near.getLocation().distance(entity.getLocation()) < 3.15) {
                    LivingEntity living = (LivingEntity) near;
                    if (abilityDamage(living, damage, entity)) {
                        knock(living, entity, knockback);
                    }
                }
            }
        }

        /** Mod {@code ANIMATION_LUNGE}: leap at ticks 10-13, pull and hit for 3 at ticks 19-22 within 3.5 blocks, over 35 ticks. */
        private void lunge(LivingEntity entity, LivingEntity target, double distance, int ticks) {
            for (int t = 0; t < ticks; t++) {
                lunge++;
                if (lunge > 10 && lunge <= 13) {
                    Vector toward = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
                    entity.setVelocity(entity.getVelocity().add(toward.multiply(1.3).add(new Vector(0, 0.35, 0))));
                } else if (lunge > 15 && distance < 10.0) {
                    Vector delta = entity.getLocation().toVector().subtract(target.getLocation().toVector());
                    target.setVelocity(target.getVelocity().multiply(0.3).add(delta.multiply(0.1)));
                    if (lunge > 19 && lunge <= 22 && distance < 3.5 && entity.hasLineOfSight(target)) {
                        abilityDamage(target, 3.0, entity);
                    }
                }
            }
            if (lunge >= LUNGE_TICKS) {
                lunge = -1;
                chaseTime = 0;
            }
        }
    }

    static void knock(LivingEntity target, LivingEntity from, double strength) {
        Vector away = target.getLocation().toVector().subtract(from.getLocation().toVector()).setY(0);
        if (away.lengthSquared() > 1.0E-6) {
            target.setVelocity(target.getVelocity().add(away.normalize().multiply(strength)).setY(Math.min(0.4, target.getVelocity().getY() + strength)));
        }
    }

    /**
     * The Gumbeeper (mod {@code GumbeeperEntity.AttackGoal}) charges its shot while it sees the target (0.1 a tick, 0.3
     * when charged by lightning) and turns its dial towards 450 degrees times the charge at 10 degrees a tick; it shoots
     * when both are full: one gumball, or three when charged (which then uses up a gumball only 1 time in 3). With none
     * left it rushes in and bursts ({@link CandyMobListener}); flint and steel makes it burst at once.
     */
    static final class Gumbeeper implements ModMobBehaviour {
        static final NamespacedKey LEFT = new NamespacedKey("bigcasares", "ac_gumballs_left");
        /** Mod {@code postShootTime}: the shooting pose lasts 5 ticks after a shot. */
        static final int SHOOT_TICKS = 5;
        private final MobAnimator animation = new MobAnimator();
        private float charge;
        private float dial;
        private int shooting;

        /** Mod {@code isShooting} / {@code isExploding}: the shot recoil, and the swelling once out of gumballs. */
        static String state(int shootingTicks, int gumballsLeft, boolean fusing) {
            if (gumballsLeft <= 0 && fusing) {
                return "explode";
            }
            return shootingTicks > 0 ? "shoot" : null;
        }

        static int left(LivingEntity entity) {
            Integer stored = entity.getPersistentDataContainer().get(LEFT, PersistentDataType.INTEGER);
            return stored == null ? GUMBALLS : stored;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            if (!(entity instanceof Creeper creeper)) {
                return;
            }
            LivingEntity target = creeper.getTarget();
            int left = left(entity);
            shooting = Math.max(0, shooting - ticks);
            animation.update(entity, context, ticks, state(shooting, left, creeper.getFuseTicks() > 0 || creeper.isIgnited()));
            if (left > 0) {
                // While it has gumballs it does not swell (mod: it only explodes once it is out of them).
                creeper.setIgnited(false);
                creeper.setFuseTicks(0);
            }
            boolean sees = target != null && target.isValid() && target.getWorld() == entity.getWorld() && left > 0
                && entity.hasLineOfSight(target);
            if (sees && entity.getLocation().distance(target.getLocation()) < 16.0) {
                creeper.getPathfinder().stopPathfinding();
                creeper.lookAt(target);
            }
            for (int t = 0; t < ticks; t++) {
                if (sees) {
                    charge = Math.min(1.0F, charge + (creeper.isPowered() ? 0.3F : 0.1F));
                } else {
                    charge = 0;
                }
                float goal = 450.0F * charge;
                dial = dial < goal ? Math.min(goal, dial + 10.0F) : Math.max(goal, dial - 10.0F);
                if (sees && dial >= 450.0F && charge >= 1.0F && left(entity) > 0) {
                    shoot(creeper, context, target);
                    shooting = SHOOT_TICKS;
                    if (!creeper.isPowered() || ThreadLocalRandom.current().nextFloat() < 0.33F) {
                        entity.getPersistentDataContainer().set(LEFT, PersistentDataType.INTEGER, left(entity) - 1);
                    }
                    charge = 0;
                }
            }
        }

        private void shoot(Creeper creeper, ModMobContext context, LivingEntity target) {
            creeper.getWorld().playSound(creeper.getLocation(), "alexscaves:gumball_launch", 1.0f, 1.0f);
            int shots = creeper.isPowered() ? 3 : 1;
            double damage = attribute(creeper, Attribute.ATTACK_DAMAGE, 4.0) + (creeper.isPowered() ? 2.0 : 0.0);
            Location from = creeper.getLocation().add(creeper.getLocation().getDirection().setY(0).normalize().multiply(0.4)).add(0, 0.3, 0);
            // Mod: inaccuracy 14 - difficulty * 4 (vanilla shoot spreads by 0.0075 per point).
            double inaccuracy = 14 - creeper.getWorld().getDifficulty().ordinal() * 4;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < shots; i++) {
                Vector aim = target.getLocation().add(0, target.getHeight() * 0.6, 0).toVector();
                if (shots > 1 && i != shots / 2) {
                    Vector side = creeper.getLocation().getDirection().setY(0).normalize().crossProduct(new Vector(0, 1, 0));
                    aim.add(side.multiply(i < shots / 2 ? 3.0 : -3.0));
                }
                Vector d = aim.subtract(from.toVector());
                double horizontal = Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ());
                Vector velocity = new Vector(d.getX(), d.getY() + horizontal * 0.2, d.getZ()).normalize()
                    .add(new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).multiply(0.0075 * inaccuracy))
                    .multiply(1.2);
                CandyMobListener.launchGumball(context, creeper, from, velocity, damage, creeper.isPowered() ? 10 : 5);
            }
        }
    }

    /**
     * Caramel cube sizes (mod {@code setSlimeSize}): health 4 + 6 a size, speed 0.25 + 0.1, damage 2 + 2; hitboxes of
     * vanilla slime sizes 2, 3 and 7 (the mod's 0.8, 1.5 and 3.5 blocks) and the model drawn 1, 2 or 4 times bigger.
     */
    static final class CaramelCube implements ModMobBehaviour {
        static final NamespacedKey SIZE = new NamespacedKey("bigcasares", "ac_caramel_size");
        private static final int[] SLIME_SIZES = {2, 3, 7};
        private static final float[] MODEL_SCALES = {1.0F, 2.0F, 4.0F};
        private final MobAnimator animation = new MobAnimator();
        private boolean scaled;
        private int airborne;
        private int landed;

        /** Mod {@code jumpProgress} / {@code squishProgress}: in the air it stretches, just landed it squishes. */
        static String state(boolean jumping, boolean recentlyLanded) {
            if (jumping) {
                return "jump";
            }
            return recentlyLanded ? "squish" : null;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            boolean onGround = entity.isOnGround();
            if (onGround) {
                landed = airborne > 0 ? 10 : Math.max(0, landed - ticks);
                airborne = 0;
            } else {
                airborne += ticks;
                landed = 0;
            }
            // Mod: it only counts as jumping after more than 4 ticks off the ground.
            animation.update(entity, context, ticks, state(!onGround && airborne > 4, landed > 0));
            if (!scaled) {
                context.model(entity.getUniqueId()).ifPresent(handle -> {
                    context.models().scale(handle, MODEL_SCALES[size(entity).orElse(0)]);
                    scaled = true;
                });
            }
        }

        static Optional<Integer> size(LivingEntity entity) {
            return Optional.ofNullable(entity.getPersistentDataContainer().get(SIZE, PersistentDataType.INTEGER));
        }

        static void applySize(LivingEntity entity, int size, boolean heal) {
            int clamped = Math.max(0, Math.min(2, size));
            boolean first = size(entity).isEmpty();
            entity.getPersistentDataContainer().set(SIZE, PersistentDataType.INTEGER, clamped);
            double health = entity.getHealth();
            if (entity instanceof Slime slime && slime.getSize() != SLIME_SIZES[clamped]) {
                slime.setSize(SLIME_SIZES[clamped]);
            }
            double maxHealth = 4.0 + 6.0 * clamped;
            setBase(entity, Attribute.MAX_HEALTH, maxHealth);
            setBase(entity, Attribute.MOVEMENT_SPEED, 0.25 + 0.1 * clamped);
            setBase(entity, Attribute.ATTACK_DAMAGE, 2.0 + 2.0 * clamped);
            // setSize resets health: keep what the cube had unless it is new or asked to heal.
            entity.setHealth(heal || first ? maxHealth : Math.min(maxHealth, Math.max(0.5, health)));
        }
    }

    /**
     * The Candicorn (mod {@code CandicornMeleeGoal}) charges what hurts it from up to 15 blocks: it runs at where the
     * target stood for up to 100 ticks, and everything within the reach of its horn (a point one block ahead) takes
     * 7 + (7 - squared distance) damage and a strong knockback; close up it stabs for its attack damage.
     */
    static final class Candicorn implements ModMobBehaviour {
        private final ModMobType type;
        private LivingEntity target;
        private Vector chargeFrom;
        private Vector chargeTo;
        private int chargeTime;
        /** Mod {@code ANIMATION_STAB}: 25 ticks. */
        static final int STAB_TICKS = 25;
        private final MobAnimator animation = new MobAnimator();
        private int cooldown;
        private int stab;
        private int idleAction;

        Candicorn(ModMobType type) {
            this.type = type;
        }

        /**
         * Mod {@code CandicornEntity}: charging, carrying a rider (the saddle hides under the rider), running at a
         * target, or standing saddled ({@code saddled} keeps the saddle on the model); otherwise idle or walk.
         */
        static String state(boolean charging, boolean ridden, boolean running, boolean saddled, boolean moving) {
            if (charging) {
                return "charge";
            }
            if (ridden) {
                return "vehicle";
            }
            if (running) {
                return "run";
            }
            return saddled && !moving ? "saddled" : null;
        }

        /** Mod idle animations (1 in 200 a tick while standing still): buck 15%, tail flicks 45%, nibble 40%. */
        static String idleAnimation(float roll, boolean flickFirst) {
            if (roll < 0.15F) {
                return "buck";
            }
            if (roll < 0.6F) {
                return flickFirst ? "tail_flick_1" : "tail_flick_2";
            }
            return "nibble_idle";
        }

        /** Mod animation lengths in ticks (buck 25, tail flicks 12, nibble 35). */
        static int idleTicks(String animation) {
            return switch (animation) {
                case "buck" -> 25;
                case "nibble_idle" -> 35;
                default -> 12;
            };
        }

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                Entity damager = byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                    ? shooter : byEntity.getDamager();
                if (hurtable(entity, damager, context)) {
                    target = (LivingEntity) damager;
                }
            }
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            cooldown = Math.max(0, cooldown - ticks);
            stab = Math.max(0, stab - ticks);
            idleAction = Math.max(0, idleAction - ticks);
            boolean ridden = !entity.getPassengers().isEmpty();
            boolean saddled = entity instanceof AbstractHorse horse && horse.getInventory().getSaddle() != null;
            boolean moving = animation.moving();
            boolean running = target != null && chargeTo == null && target.isValid()
                && target.getWorld() == entity.getWorld() && target.getLocation().distance(entity.getLocation()) > 5.0;
            String state = state(chargeTo != null, ridden, running, saddled, moving);
            animation.update(entity, context, ticks, state);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (!moving && state == null && target == null && idleAction <= 0 && !ridden
                && random.nextDouble() < 1.0 - Math.pow(199.0 / 200.0, ticks)) {
                String idle = idleAnimation(random.nextFloat(), random.nextBoolean());
                idleAction = idleTicks(idle);
                animation.action(entity, context, idle);
            }
            if (target == null || !target.isValid() || target.isDead() || target.getWorld() != entity.getWorld()
                || target.getLocation().distance(entity.getLocation()) > 32.0 || !entity.getPassengers().isEmpty()) {
                target = null;
                chargeTo = null;
                return;
            }
            double distance = entity.getLocation().distance(target.getLocation());
            double attackDistance = entity.getWidth() + target.getWidth() + 0.5;
            if (chargeTo != null) {
                charge(entity, context, distance, attackDistance, ticks);
            } else if (distance > attackDistance && distance < 15.0 && cooldown <= 0 && entity.hasLineOfSight(target)) {
                chargeFrom = entity.getLocation().toVector();
                chargeTo = target.getLocation().toVector();
                chargeTime = 0;
                context.mobs().playSound(entity, type, "charge_start");
            } else if (distance <= attackDistance && stab <= 0 && entity.hasLineOfSight(target)) {
                stab = STAB_TICKS;
                animation.action(entity, context, "stab");
                if (abilityDamage(target, attribute(entity, Attribute.ATTACK_DAMAGE, 6.0), entity)) {
                    knock(target, entity, 0.6);
                }
            } else if (entity instanceof Mob mob && distance > attackDistance) {
                mob.getPathfinder().moveTo(target, 1.3);
            }
        }

        private void charge(LivingEntity entity, ModMobContext context, double distance, double attackDistance, int ticks) {
            chargeTime += ticks;
            Vector direction = chargeTo.clone().subtract(chargeFrom).setY(0);
            if (direction.lengthSquared() < 1.0E-6 || chargeTime > 100) {
                chargeTo = null;
                return;
            }
            direction.normalize();
            // Mod: 0.85 a tick on top of 0.9 of the last motion, slowed by ground friction.
            Vector velocity = entity.getVelocity();
            entity.setVelocity(new Vector(direction.getX() * 0.85, velocity.getY(), direction.getZ() * 0.85));
            Location focal = entity.getLocation().add(direction);
            for (Entity near : entity.getWorld().getNearbyEntities(entity.getBoundingBox().expand(3.5))) {
                if (!hurtable(entity, near, context)) {
                    continue;
                }
                double distSq = near.getLocation().distanceSquared(focal);
                if (distSq < 7.0) {
                    LivingEntity living = (LivingEntity) near;
                    if (abilityDamage(living, 7.0 + (7.0 - distSq), entity)) {
                        knock(living, entity, 2.0);
                    }
                }
            }
            if ((distance < attackDistance || entity.getLocation().toVector().distanceSquared(chargeTo) < attackDistance * attackDistance)
                && chargeTime >= 5) {
                cooldown = 100;
                chargeTo = null;
            }
            if (chargeTime % 10 < ticks) {
                entity.getWorld().spawnParticle(Particle.END_ROD, entity.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0.02);
                context.mobs().playSound(entity, type, "charge_loop");
            }
        }
    }

    /**
     * The Gummy Bear (mod {@code GummyBearMeleeGoal}) maims or swipes at what it attacks, half and half; mauling it
     * rears up on its hind legs ({@code stand}). Its eating, fishing, back scratching, sitting and sleeping are not
     * part of the module's AI.
     */
    static final class GummyBear implements ModMobBehaviour {
        /** Mod {@code ANIMATION_MAUL} / {@code ANIMATION_SWIPE}: 25 ticks each. */
        static final int STRIKE_TICKS = 25;
        /** A bear that swallowed a potion: it goes for a licoroot to scratch its back some time later. */
        static final NamespacedKey DIGESTING = new NamespacedKey("bigcasares", "ac_gummy_bear_digesting");
        /** Ticks it digests before looking for a tree (the mod sleeps it for days first; the module does not sleep it). */
        static final int DIGEST_DELAY = 200;
        private static final int TREE_RANGE = 20;
        private static final int TREE_HEIGHT = 6;
        private final MobAnimator animation = new MobAnimator();
        private int standing;
        private int digested;
        private int treeSearch;
        private int treeChase;
        private Location treeStand;
        private Location tree;
        private int scratching = -1;
        private UUID prey;
        private int preySearch = 20;
        private int preyChase;
        private int fishing = -1;
        private int eating = -1;
        private UUID held;

        /** Mod {@code isStandingAnimation}: it stands for the whole maul. */
        static String state(int standingTicks) {
            return standingTicks > 0 ? "stand" : null;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            standing = Math.max(0, standing - ticks);
            animation.update(entity, context, ticks, state(standing));
            if (!(entity instanceof Mob mob)) {
                return;
            }
            if (backScratch(mob, context, ticks)) {
                return;
            }
            hunt(mob, context, ticks);
        }

        /**
         * Mod {@code GummyBearBackScratchGoal}: a grown bear that is digesting goes to a licoroot (within 20 blocks, 6
         * up or down, with free ground beside it), stands against it and scratches its back for 90 ticks; the digestion
         * ends with the animation.
         *
         * @return whether it is busy with it
         */
        private boolean backScratch(Mob bear, ModMobContext context, int ticks) {
            boolean digesting = bear.getPersistentDataContainer().has(DIGESTING, PersistentDataType.BYTE);
            if (!digesting) {
                digested = 0;
                treeStand = null;
                scratching = -1;
                return false;
            }
            if (scratching >= 0) {
                scratching += ticks;
                bear.getPathfinder().stopPathfinding();
                if (scratching >= CandyAnimations.BACKSCRATCH_TICKS - 5) {
                    bear.getPersistentDataContainer().remove(DIGESTING);
                    scratching = -1;
                    treeStand = null;
                    digested = 0;
                }
                return true;
            }
            boolean baby = bear instanceof org.bukkit.entity.Ageable ageable && !ageable.isAdult();
            if (!CandyAnimations.scratches(baby, false, false, true) || fishing >= 0 || eating >= 0 || held != null
                || bear.getTarget() != null) {
                return false;
            }
            digested += ticks;
            if (digested < DIGEST_DELAY) {
                return false;
            }
            treeSearch -= ticks;
            if (treeStand == null && treeSearch <= 0) {
                treeSearch = 100;
                treeChase = 0;
                findTree(bear);
            }
            if (treeStand == null) {
                return false;
            }
            if (treeStand.getWorld() != bear.getWorld() || (treeChase += ticks) > 400) {
                treeStand = null;
                return false;
            }
            Location here = bear.getLocation();
            double dx = here.getX() - treeStand.getX();
            double dz = here.getZ() - treeStand.getZ();
            double dy = here.getY() - treeStand.getY();
            if (dx * dx + dy * dy + dz * dz < 4.0) {
                bear.getPathfinder().stopPathfinding();
                double toX = tree.getX() - here.getX();
                double toZ = tree.getZ() - here.getZ();
                bear.setRotation((float) Math.toDegrees(Math.atan2(-toX, toZ)), 0.0F);
                scratching = 0;
                animation.action(bear, context, "backscratch");
            } else {
                bear.getPathfinder().moveTo(treeStand, 1.0);
            }
            return true;
        }

        /** The nearest licoroot with a free side to stand on (air beside it, solid ground under that), or none. */
        private void findTree(Mob bear) {
            Location from = bear.getLocation();
            var world = from.getWorld();
            double best = Double.MAX_VALUE;
            treeStand = null;
            for (int x = from.getBlockX() - TREE_RANGE; x <= from.getBlockX() + TREE_RANGE; x++) {
                for (int z = from.getBlockZ() - TREE_RANGE; z <= from.getBlockZ() + TREE_RANGE; z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    for (int y = from.getBlockY() - TREE_HEIGHT; y <= from.getBlockY() + TREE_HEIGHT; y++) {
                        var block = world.getBlockAt(x, y, z);
                        if (!isLicoroot(block)) {
                            continue;
                        }
                        for (var face : new org.bukkit.block.BlockFace[] {org.bukkit.block.BlockFace.NORTH,
                            org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
                            var side = block.getRelative(face);
                            if (!side.isPassable() || side.isLiquid() || !side.getRelative(org.bukkit.block.BlockFace.DOWN).isSolid()) {
                                continue;
                            }
                            Location stand = side.getLocation().add(0.5, 0.0, 0.5);
                            double distance = stand.distanceSquared(from);
                            if (distance < best) {
                                best = distance;
                                treeStand = stand;
                                tree = block.getLocation().add(0.5, 0.5, 0.5);
                            }
                        }
                    }
                }
            }
        }

        /**
         * Mod {@code GummyBearMeleeGoal} against a Sweetish Fish (its target goal, one scan in about 100 ticks): it chases
         * the fish at 1.3; within 5 blocks it eats a fish at its mouth, otherwise it fishes: on tick 16 of the 35 it jumps,
         * lunges at the fish until tick 20 and catches it if within 1.5 blocks, then eats it for 40 ticks, biting it.
         */
        private void hunt(Mob bear, ModMobContext context, int ticks) {
            if (held != null) {
                eat(bear, ticks);
                return;
            }
            LivingEntity fish = prey == null ? null : bear.getServer().getEntity(prey) instanceof LivingEntity found ? found : null;
            if (fish != null && (!fish.isValid() || fish.getWorld() != bear.getWorld()
                || (preyChase += ticks) > 600 || fish.getLocation().distanceSquared(bear.getLocation()) > 24.0 * 24.0)) {
                fish = null;
            }
            if (fish == null) {
                prey = null;
                fishing = -1;
                preySearch -= ticks;
                if (preySearch <= 0 && bear.getTarget() == null && scratching < 0) {
                    preySearch = 60 + ThreadLocalRandom.current().nextInt(80);
                    findPrey(bear, context);
                }
                return;
            }
            double distance = bear.getLocation().distance(fish.getLocation());
            bear.lookAt(fish);
            if (fishing >= 0) {
                fish(bear, context, fish, distance, ticks);
                return;
            }
            if (distance >= 5.0) {
                bear.getPathfinder().moveTo(fish.getLocation(), 1.3);
                return;
            }
            bear.getPathfinder().stopPathfinding();
            String action = CandyAnimations.huntAction(distance);
            if (action == null) {
                return;
            }
            if (action.equals("eat")) {
                grab(bear, fish, context);
            } else {
                fishing = 0;
                animation.action(bear, context, "fish");
            }
        }

        private void findPrey(Mob bear, ModMobContext context) {
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity near : bear.getWorld().getNearbyEntities(bear.getLocation(), 16, 8, 16)) {
                if (near instanceof LivingEntity living && living.isValid()
                    && context.mobs().typeOf(living).filter(SWEETISH_FISH::equals).isPresent()) {
                    double distance = living.getLocation().distanceSquared(bear.getLocation());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = living;
                    }
                }
            }
            if (best != null) {
                prey = best.getUniqueId();
                preyChase = 0;
            }
        }

        private void fish(Mob bear, ModMobContext context, LivingEntity fish, double distance, int ticks) {
            int before = fishing;
            fishing += ticks;
            Vector motion = bear.getVelocity();
            if (CandyAnimations.fishJumps(before, fishing) && bear.isOnGround()) {
                bear.setVelocity(motion.add(new Vector(0, 0.3, 0)));
            }
            if (CandyAnimations.fishLunges(fishing)) {
                Vector toward = fish.getLocation().toVector().subtract(bear.getLocation().toVector());
                if (toward.length() > 1.0) {
                    toward.normalize();
                }
                bear.setVelocity(bear.getVelocity().add(toward.multiply(0.3 * ticks)));
            }
            if (CandyAnimations.fishCatches(fishing, distance)) {
                grab(bear, fish, context);
                return;
            }
            if (fishing >= CandyAnimations.FISH_TICKS) {
                fishing = -1;
            }
        }

        /** Mod {@code setHeldMobId}: the fish is held at the mouth while the bear eats it. */
        private void grab(Mob bear, LivingEntity fish, ModMobContext context) {
            held = fish.getUniqueId();
            eating = 0;
            fishing = -1;
            animation.action(bear, context, "eat");
        }

        private void eat(Mob bear, int ticks) {
            Entity fish = bear.getServer().getEntity(held);
            if (fish instanceof LivingEntity living && living.isValid() && living.getWorld() == bear.getWorld()
                && eating < CandyAnimations.EAT_TICKS) {
                int before = eating;
                eating += ticks;
                Location mouth = bear.getEyeLocation().add(bear.getLocation().getDirection().setY(0).normalize().multiply(0.5))
                    .add(0, 0.25, 0);
                living.teleport(mouth);
                living.setVelocity(new Vector());
                if (before / 15 != eating / 15) {
                    CandyMobs.abilityDamage(living, 2.0, bear);
                }
                return;
            }
            held = null;
            prey = null;
            eating = -1;
        }

        @Override
        public void onAttack(LivingEntity entity, ModMobContext context, LivingEntity target) {
            if (ThreadLocalRandom.current().nextBoolean()) {
                standing = STRIKE_TICKS;
                animation.base(entity, context, state(standing));
                animation.action(entity, context, "maul");
            } else {
                animation.action(entity, context, "swipe");
            }
        }
    }

    /** The module's licoroot blocks (every axis), set up with the block palette. */
    private static volatile java.util.Set<BlockData> licoroot = java.util.Set.of();
    private static volatile java.util.Set<org.bukkit.Material> licorootMaterials = java.util.Set.of();

    static void trackLicoroot(java.util.Collection<BlockData> states) {
        licoroot = java.util.Set.copyOf(states);
        licorootMaterials = states.stream().map(BlockData::getMaterial).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static boolean isLicoroot(org.bukkit.block.Block block) {
        return licorootMaterials.contains(block.getType()) && licoroot.contains(block.getBlockData());
    }

    static ItemStack gumballItem(ModMobContext context) {
        return context.mobs().createItem("ac_gumball_pile", 1).orElse(new ItemStack(org.bukkit.Material.SLIME_BALL));
    }

    /** True for the module's gumball projectiles. */
    static boolean isGumball(Entity entity) {
        return entity instanceof Snowball && entity.getPersistentDataContainer().has(CandyMobListener.GUMBALL_DAMAGE,
            PersistentDataType.DOUBLE);
    }
}
