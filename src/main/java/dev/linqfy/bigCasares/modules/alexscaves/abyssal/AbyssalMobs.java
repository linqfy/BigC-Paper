package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobAnimator;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sea creatures of the Abyssal Chasm with the mod's stats, drops and behaviour: lanternfish schooling into bait balls
 * (on a cod, which already schools), sea pigs crawling on the floor and digesting clay, mud or muck, tripodfish that
 * stand on the floor on their fins for a while, gossamer worms, and mine guardians chained to an anchor that wake up,
 * scan for players and explode next to them. The swimmers flee for a moment when hurt.
 */
public final class AbyssalMobs {
    static final CaveBiomeId BIOME = CaveBiomeId.ABYSSAL_CHASM;
    static final int SEA_LEVEL = 63;

    public static final ModMobType LANTERNFISH = new ModMobType("lanternfish", BIOME, "cod", SpawnCategory.WATER_AMBIENT,
        Map.of("max_health", 2.0, "movement_speed", 0.3), "alexscaves_lanternfish",
        List.of(new MobDrop("ac_lanternfish", 1, 1, false, 1.0, 0.0, false)));
    public static final ModMobType SEA_PIG = new ModMobType("sea_pig", BIOME, "cod", SpawnCategory.DEEP_SEA_CREATURE,
        Map.of("max_health", 8.0, "movement_speed", 0.05), "alexscaves_sea_pig",
        List.of(new MobDrop("ac_sea_pig", 1, 1, false, 1.0, 0.0, false)));
    public static final ModMobType TRIPODFISH = new ModMobType("tripodfish", BIOME, "cod", SpawnCategory.DEEP_SEA_CREATURE,
        Map.of("max_health", 8.0, "movement_speed", 0.3), "alexscaves_tripodfish",
        List.of(new MobDrop("ac_tripodfish", 1, 1, false, 1.0, 0.0, false)));
    public static final ModMobType GOSSAMER_WORM = new ModMobType("gossamer_worm", BIOME, "cod", SpawnCategory.DEEP_SEA_CREATURE,
        Map.of("max_health", 10.0, "movement_speed", 0.1), "alexscaves_gossamer_worm",
        List.of(MobDrop.of("ac_bioluminesscence", 0, 3)));
    /** On a guardian (a sea monster) with its own AI turned off: this class drives it. */
    public static final ModMobType MINE_GUARDIAN = new ModMobType("mine_guardian", BIOME, "guardian", SpawnCategory.MONSTER,
        Map.of("max_health", 20.0, "movement_speed", 0.25, "attack_damage", 1.0), "alexscaves_mine_guardian",
        List.of(MobDrop.of("ac_depth_charge", 0, 2)));
    /** The mine guardian's anchor: a still, invisible armor stand on the floor with the anchor model. */
    public static final ModMobType MINE_GUARDIAN_ANCHOR = new ModMobType("mine_guardian_anchor", BIOME, "armor_stand",
        SpawnCategory.AMBIENT, Map.of(), "alexscaves_mine_guardian_anchor", List.of());

    public static final List<ModMobType> ALL = List.of(LANTERNFISH, SEA_PIG, TRIPODFISH, GOSSAMER_WORM, MINE_GUARDIAN,
        MINE_GUARDIAN_ANCHOR);

    private AbyssalMobs() {
    }

    public static void register(ModMobCatalog catalog, BlockData muck, BlockData abyssmarine) {
        catalog.register(LANTERNFISH, (entity, type) -> {
            ownGoals(entity);
            floorPose(entity, -2.0);
            return new Lanternfish();
        });
        catalog.register(SEA_PIG, (entity, type) -> {
            ownGoals(entity);
            floorPose(entity, 1.0);
            return new SeaPig();
        });
        catalog.register(TRIPODFISH, (entity, type) -> {
            ownGoals(entity);
            floorPose(entity, 1.0);
            return new Tripodfish();
        });
        catalog.register(GOSSAMER_WORM, (entity, type) -> {
            ownGoals(entity);
            floorPose(entity, 0.33 + ThreadLocalRandom.current().nextDouble() * 0.33);
            return new GossamerWorm();
        });
        catalog.register(MINE_GUARDIAN, (entity, type) -> {
            if (entity instanceof Mob mob) {
                mob.setAware(false);
            }
            return new MineGuardian();
        });
        catalog.register(MINE_GUARDIAN_ANCHOR, (entity, type) -> {
            if (entity instanceof ArmorStand stand) {
                stand.setInvisible(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setBasePlate(false);
                stand.setMarker(true);
                // Not saved: the guardian makes a new one under it when it loads again (no orphans or duplicates).
                stand.setPersistent(false);
                for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
                    for (ArmorStand.LockType lock : ArmorStand.LockType.values()) {
                        stand.addEquipmentLock(slot, lock);
                    }
                }
            }
            return new Anchor();
        });
        // Mod spawn rules: in water, deep enough under the sea, and a coin flip (tripodfish) or 1 in 3 (gossamer worms).
        catalog.spawnRule(LANTERNFISH.id(), (block, random) -> block.getY() < SEA_LEVEL - 30);
        catalog.spawnRule(SEA_PIG.id(), (block, random) -> block.getY() < SEA_LEVEL - 25);
        catalog.spawnRule(TRIPODFISH.id(), (block, random) -> block.getY() < SEA_LEVEL - 30 && random.nextBoolean());
        catalog.spawnRule(GOSSAMER_WORM.id(), (block, random) -> block.getY() < SEA_LEVEL - 25 && random.nextInt(3) == 0);
        catalog.spawnsInWater(MINE_GUARDIAN.id());
        // Mod abyssal_ruins spawn_overrides: monsters spawning in the ruins are mine guardians (1-2), with their rule.
        catalog.waterStructureSpawn(BIOME, 2, (spot, random) -> {
            if (!StructureMarks.inside(spot.getLocation(), StructureMarks.Kind.ABYSSAL_RUINS)) {
                return null;
            }
            return mineGuardianRule(spot, random, muck, abyssmarine) ? MINE_GUARDIAN.id() : "";
        });
    }

    /**
     * Mod {@code checkMineGuardianSpawnRules}: not in peaceful, 1 in 10, more than 50 under the sea, more than 15 blocks
     * of water above, and over muck, abyssmarine or deepslate.
     */
    static boolean mineGuardianRule(Block spot, java.util.random.RandomGenerator random, BlockData muck, BlockData abyssmarine) {
        World world = spot.getWorld();
        if (world.getDifficulty() == Difficulty.PEACEFUL || random.nextInt(10) != 0 || spot.getY() >= SEA_LEVEL - 50) {
            return false;
        }
        int up = spot.getY();
        while (up < SEA_LEVEL && fluid(world.getBlockAt(spot.getX(), up, spot.getZ()))) {
            up++;
        }
        if (up - spot.getY() <= 15) {
            return false;
        }
        int down = spot.getY();
        while (down > world.getMinHeight() && fluid(world.getBlockAt(spot.getX(), down, spot.getZ()))) {
            down--;
        }
        BlockData ground = world.getBlockAt(spot.getX(), down, spot.getZ()).getBlockData();
        return ground.equals(muck) || ground.equals(abyssmarine) || ground.getMaterial() == Material.DEEPSLATE;
    }

    /**
     * The mod's fish have their own goals only (no fleeing players, panicking or schooling with cod): the cod's are
     * removed and the behaviour moves them. Awareness comes back too (a tripodfish may have unloaded standing).
     */
    static void ownGoals(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            org.bukkit.Bukkit.getMobGoals().removeAllGoals(mob);
            mob.setAware(true);
        }
    }

    static boolean fluid(Block block) {
        return block.getType() == Material.WATER || block.getType() == Material.BUBBLE_COLUMN
            || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    /**
     * Mod {@code doInitialPosing} for natural spawns: down through the water to the floor, then {@code share} of the way
     * back up (1 = on the floor; a negative share is that many blocks above it).
     */
    static void floorPose(LivingEntity entity, double share) {
        if (entity.getTicksLived() > 5) {
            return;
        }
        Location at = entity.getLocation();
        int down = at.getBlockY();
        while (down > at.getWorld().getMinHeight() && fluid(at.getWorld().getBlockAt(at.getBlockX(), down, at.getBlockZ()))) {
            down--;
        }
        double height = at.getBlockY() - down;
        double y = share < 0 ? down - share : share >= 1.0 ? down + 1 : down + height * share;
        entity.teleport(new Location(at.getWorld(), at.getBlockX() + 0.5, y, at.getBlockZ() + 0.5, at.getYaw(), at.getPitch()));
    }

    static void moveTo(LivingEntity entity, Location target, double speed) {
        if (entity instanceof Mob mob && target != null) {
            mob.getPathfinder().moveTo(target, speed);
        }
    }

    /** Mod {@code findWaterBlock}: of 15 random spots within {@code range}, the last one in water. */
    static Location randomWater(LivingEntity entity, int range) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location base = entity.getLocation();
        Location result = null;
        for (int i = 0; i < 15; i++) {
            Block block = base.getBlock().getRelative(random.nextInt(range) - range / 2, random.nextInt(range) - range / 2,
                random.nextInt(range) - range / 2);
            if (block.getType() == Material.WATER && block.getY() > base.getWorld().getMinHeight() + 1) {
                result = block.getLocation().add(0.5, 0.5, 0.5);
            }
        }
        return result;
    }

    /** Mod {@code DefaultRandomPos.getPosAway}: a spot in water up to 16 away (7 up or down) from where it was hurt. */
    static Location awayFrom(LivingEntity entity, Location hurt) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector away = entity.getLocation().toVector().subtract(hurt.toVector()).setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        }
        away.normalize();
        for (int i = 0; i < 10; i++) {
            double distance = 4 + random.nextDouble() * 12;
            Location spot = entity.getLocation().add(away.clone().multiply(distance)).add(
                random.nextDouble(-3, 3), random.nextInt(-7, 8), random.nextDouble(-3, 3));
            if (spot.getBlock().getType() == Material.WATER) {
                return spot;
            }
        }
        return null;
    }

    /** Mod {@code AvoidHurtGoal}: after a hit, 40-80 ticks swimming away from where it happened, at 1.6 speed. */
    static class Fleeing implements ModMobBehaviour {
        int fleeFor;
        Location hurtAt;
        Location fleeTarget;
        final MobAnimator anim = new MobAnimator();

        /** The state loop of the mod's model for this mob right now, or null for plain idle and walk. */
        String animState(LivingEntity entity) {
            return null;
        }

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            fleeFor = 40 + ThreadLocalRandom.current().nextInt(40);
            hurtAt = entity.getLocation();
            fleeTarget = null;
        }

        /** @return true while fleeing (the other goals wait) */
        boolean flee(LivingEntity entity, int ticks) {
            if (fleeFor <= 0 || hurtAt == null) {
                return false;
            }
            fleeFor -= ticks;
            if (fleeFor <= 0) {
                hurtAt = null;
                return false;
            }
            if (fleeTarget == null || entity.getLocation().distanceSquared(fleeTarget) < 6.0) {
                fleeTarget = awayFrom(entity, hurtAt);
            }
            moveTo(entity, fleeTarget, 1.6);
            return true;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            anim.update(entity, context, ticks, animState(entity));
            flee(entity, ticks);
        }
    }

    /** Mod {@code GossamerWormEntity}: it flees when hurt and otherwise swims to random spots (1 time in 20 a tick). */
    static final class GossamerWorm extends Fleeing {
        /** Mod: it squishes whenever it is out of the water. */
        @Override
        String animState(LivingEntity entity) {
            return entity.isInWater() ? null : "squish";
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            anim.update(entity, context, ticks, animState(entity));
            if (!flee(entity, ticks) && entity.isInWater() && ThreadLocalRandom.current().nextInt(10) == 0) {
                moveTo(entity, randomWater(entity, 24), 1.0);
            }
        }
    }

    /**
     * Mod {@code LanternfishEntity}: the school's leader now and then (every 100-250 ticks) picks a spot and the fish
     * near it swim around it for 100-300 ticks in a shrinking circle (a bait ball); otherwise they swim to a random spot
     * high in the water at night and low by day.
     */
    static final class Lanternfish implements ModMobBehaviour {
        private Location circleCenter;
        private int circleTime;
        private int maxCircleTime;
        private float circleSpeed;
        private float circleDistance = 3.0F;
        private boolean clockwise;
        private int baitballCooldown;
        private int schoolCheck;
        private List<LivingEntity> school = List.of();
        private final MobAnimator anim = new MobAnimator();

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            // Mod: grounded (on the floor out of the water) is the land pose.
            anim.update(entity, context, ticks, entity.isOnGround() && !entity.isInWater() ? "land" : null);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (baitballCooldown > 0) {
                baitballCooldown -= ticks;
            }
            if (!entity.isInWater()) {
                return;
            }
            schoolCheck -= ticks;
            if (schoolCheck <= 0) {
                schoolCheck = 10;
                school = school(entity, context);
            }
            if (circleCenter != null) {
                circleTime += ticks;
                if (circleTime > maxCircleTime) {
                    circleCenter = null;
                } else {
                    moveTo(entity, orbit(entity), circleSpeed);
                    return;
                }
            }
            LivingEntity leaderFish = null;
            for (LivingEntity other : school) {
                if (other.isValid() && (leaderFish == null || other.getEntityId() < leaderFish.getEntityId())) {
                    leaderFish = other;
                }
            }
            boolean leader = leaderFish == null || leaderFish.getEntityId() > entity.getEntityId();
            if (leader && baitballCooldown <= 0) {
                baitballCooldown = 100 + random.nextInt(150);
                start(swimTarget(entity, 3), 100 + random.nextInt(200), 0);
                for (LivingEntity other : school) {
                    if (context.mobs().behaviourOf(other).orElse(null) instanceof Lanternfish fish && fish.circleCenter == null) {
                        fish.start(circleCenter, maxCircleTime, circleTime);
                    }
                }
            } else if (leader ? random.nextInt(20) == 0 : random.nextInt(40) == 0) {
                // Mod SwimInSchoolGoal: the leader (or a fish alone) wanders, followers sometimes too.
                moveTo(entity, swimTarget(entity, 6), 1.0);
            } else if (!leader && schoolCheck == 10 && entity.getLocation().distanceSquared(leaderFish.getLocation()) > 4.0) {
                // Mod JoinSchoolGoal: followers swim to their leader every 10 ticks.
                moveTo(entity, leaderFish.getLocation(), 1.0);
            }
        }

        private void start(Location center, int max, int time) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            circleCenter = center;
            circleTime = time;
            maxCircleTime = max;
            circleDistance = 1.0F + random.nextFloat() * 1.5F;
            circleSpeed = 0.75F + random.nextFloat() * 0.5F;
            clockwise = random.nextBoolean();
        }

        /** The other lanternfish within 8 blocks. */
        private static List<LivingEntity> school(LivingEntity entity, ModMobContext context) {
            List<LivingEntity> school = new ArrayList<>();
            for (Entity other : entity.getNearbyEntities(8, 8, 8)) {
                if (other instanceof LivingEntity living && context.mobs().typeOf(living).filter(LANTERNFISH::equals).isPresent()) {
                    school.add(living);
                }
            }
            return school;
        }

        /** Mod {@code orbitAroundPos}. */
        private Location orbit(LivingEntity entity) {
            float progress = 1.0F - (float) circleTime / maxCircleTime;
            float angle = (float) (Math.PI / 18) * circleSpeed * (clockwise ? -circleTime : circleTime);
            double radius = circleDistance * progress + 1.75F;
            double extraX = radius * Math.sin(angle);
            double extraY = Math.sin(1.0F + entity.getEntityId() * 0.2F + circleTime * 0.2F);
            double extraZ = radius * progress * Math.cos(angle);
            return new Location(entity.getWorld(), circleCenter.getBlockX() + 0.5 + extraX,
                Math.max(circleCenter.getBlockY() + 0.5 + extraY, -62.0), circleCenter.getBlockZ() + 0.5 + extraZ);
        }

        /** Mod {@code findSwimToPos}: near the fish, at a height of the water column that depends on the time of day. */
        private static Location swimTarget(LivingEntity entity, int range) {
            World world = entity.getWorld();
            Location at = entity.getLocation();
            int x = at.getBlockX();
            int z = at.getBlockZ();
            int fishY = at.getBlockY();
            int up = (int) Math.floor(at.getY() + 1.0);
            while (up < world.getMaxHeight() && world.getBlockAt(x, up, z).getType() == Material.WATER) {
                up += 5;
            }
            int down = (int) Math.floor(at.getY() - 1.0);
            while (down > world.getMinHeight() && world.getBlockAt(x, down, z).getType() == Material.WATER) {
                down -= 5;
            }
            int height = up - down;
            long time = world.getTime();
            boolean night = time >= 13000 && time < 23000;
            int y = night ? clamp(fishY, down + (int) (height * 0.85F), up)
                : clamp(fishY, down + (int) (height * 0.25F), up - (int) (height * 0.85F));
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 15; i++) {
                Block block = world.getBlockAt(x + random.nextInt(range) - range / 2, y + random.nextInt(range) - range / 2,
                    z + random.nextInt(range) - range / 2);
                if (block.getType() == Material.WATER && block.getY() > world.getMinHeight() + 1) {
                    return block.getLocation().add(0.5, 0.5, 0.5);
                }
            }
            return new Location(world, x + 0.5, y + 0.5, z + 0.5);
        }

        /** Vanilla {@code Mth.clamp(int)}: the minimum wins when it is above the maximum. */
        static int clamp(int value, int min, int max) {
            return value < min ? min : Math.min(value, max);
        }
    }

    /**
     * Mod {@code SeaPigEntity}: it sinks in the water and crawls along the floor to spots picked now and then; fed clay,
     * mud or muck it digests it in a second into marine snow, muck or a pearl ({@code gameplay/sea_pig_digestion}).
     */
    static final class SeaPig implements ModMobBehaviour {
        private Location target;
        private int digesting = -1;
        private final MobAnimator anim = new MobAnimator();

        /** @return false when it is already digesting */
        boolean feed(LivingEntity entity) {
            if (digesting >= 0) {
                return false;
            }
            digesting = 0;
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:sea_pig_eat", 1.0F, 1.0F);
            return true;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            anim.update(entity, context, ticks, entity.isOnGround() && !entity.isInWater() ? "squish" : null);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (digesting >= 0) {
                digesting += ticks;
                if (digesting >= 20) {
                    digesting = -1;
                    digest(entity, context);
                }
            }
            if (entity.isInWater() && !entity.isOnGround()) {
                // Mod travel: it sinks 0.03 a tick unless it is climbing a wall.
                entity.setVelocity(entity.getVelocity().add(new Vector(0, -0.007 * ticks, 0)));
            }
            if (target != null && entity.getLocation().distanceSquared(target) > 4.0) {
                moveTo(entity, target, 1.0);
            } else if (random.nextInt(25) == 0) {
                target = floorTarget(entity);
            }
        }

        /** Mod {@code WanderGoal.getPosition}: a random water spot, then down onto the floor under it. */
        private static Location floorTarget(LivingEntity entity) {
            Location water = randomWater(entity, 10);
            if (water == null) {
                return null;
            }
            Block block = water.getBlock();
            while (block.getRelative(0, -1, 0).getType() == Material.WATER && block.getY() > entity.getWorld().getMinHeight() + 1) {
                block = block.getRelative(0, -1, 0);
            }
            return block.getLocation().add(0.5, 0.5, 0.5);
        }

        /** Mod loot table {@code gameplay/sea_pig_digestion}: muck 1-2 (2), a pearl (1), marine snow 1-2 (15). */
        private static void digest(LivingEntity entity, ModMobContext context) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int roll = random.nextInt(18);
            String item = roll < 2 ? "ac_muck" : roll < 3 ? "ac_pearl" : "ac_marine_snow";
            int count = item.equals("ac_pearl") ? 1 : 1 + random.nextInt(2);
            context.mobs().createItem(item, count).ifPresent(stack -> entity.getWorld().dropItemNaturally(entity.getLocation(), stack));
        }
    }

    /**
     * Mod {@code TripodfishEntity}: it swims to spots up to 10 away and, after swimming a while (or 1 time in 5), stops on
     * the floor and stands on its fins for 400-800 ticks, still, until it swims off again.
     */
    static final class Tripodfish extends Fleeing {
        private boolean standing;
        private int timeSwimming;
        private int timeStanding;
        private int standFor = 300;
        private Location target;
        private boolean wantsToStand;

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            super.onDamaged(entity, context, event);
            stand(entity, false);
        }

        private void stand(LivingEntity entity, boolean stand) {
            if (standing == stand) {
                return;
            }
            standing = stand;
            standFor = 400 + ThreadLocalRandom.current().nextInt(400);
            timeStanding = 0;
            timeSwimming = 0;
            if (entity instanceof Mob mob) {
                mob.setAware(!stand);
                if (stand) {
                    mob.getPathfinder().stopPathfinding();
                }
            }
        }

        /** Mod: standing on its fins, otherwise the land pose out of the water. */
        @Override
        String animState(LivingEntity entity) {
            return standing ? "stand" : entity.isInWater() ? null : "land";
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            anim.update(entity, context, ticks, animState(entity));
            if (flee(entity, ticks)) {
                return;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (standing) {
                timeStanding += ticks;
                Vector velocity = entity.getVelocity();
                entity.setVelocity(new Vector(velocity.getX() * 0.5, entity.isOnGround() ? 0 : velocity.getY() - 0.05 * ticks,
                    velocity.getZ() * 0.5));
                if (timeStanding >= standFor || random.nextInt(50) == 0 || !entity.isInWater()) {
                    stand(entity, false);
                    target = null;
                }
                return;
            }
            timeSwimming += ticks;
            if (target == null) {
                target = randomWater(entity, 20);
                wantsToStand = timeSwimming > 300 || random.nextFloat() < 0.2F;
                if (target != null) {
                    moveTo(entity, target, 1.0);
                }
                return;
            }
            boolean arrived = entity.getLocation().distanceSquared(target) <= 9.0
                || entity instanceof Mob mob && !mob.getPathfinder().hasPath();
            if (arrived) {
                target = null;
                if (wantsToStand && floorWithin(entity, 2)) {
                    stand(entity, true);
                    entity.setVelocity(new Vector());
                }
            }
        }

        private static boolean floorWithin(LivingEntity entity, int blocks) {
            Block block = entity.getLocation().getBlock();
            for (int down = 0; down <= blocks; down++) {
                if (block.getRelative(0, -down, 0).getType() != Material.WATER) {
                    return true;
                }
            }
            return false;
        }
    }

    static final NamespacedKey ANCHOR_KEY = new NamespacedKey("bigcasares", "ac_mine_anchor");
    static final NamespacedKey GUARDIAN_KEY = new NamespacedKey("bigcasares", "ac_mine_guardian");
    static final NamespacedKey CHAIN_KEY = new NamespacedKey("bigcasares", "ac_mine_chain");

    /**
     * Mod {@code MineGuardianEntity} and {@code MineGuardianAnchorEntity}: a sea mine chained 7-12 blocks above its
     * anchor (5 more when it chases). It sleeps with its eye closed; after 200-300 ticks without a target (or when hit)
     * it scans for 100-200 ticks, looking 8 blocks ahead every 5 ticks for a player; then it swims at the player and, 2
     * blocks away, swells and explodes (radius 5, digging underwater too). After 300 ticks of chasing it gives up and
     * sleeps again.
     */
    static final class MineGuardian implements ModMobBehaviour {
        private UUID anchorId;
        private int anchorMissing;
        private int maxChain;
        private boolean eyeClosed = true;
        private boolean scanning;
        private int scanTime;
        private int maxScanTime;
        private int maxSleepTime = 200 + ThreadLocalRandom.current().nextInt(100);
        private int lastScanTick;
        private int timeSinceHadTarget = 10;
        private int chaseTime;
        private UUID target;
        private boolean exploding;
        private float explodeProgress;
        private int age;
        private final MobAnimator anim = new MobAnimator();
        private final List<BlockDisplay> chain = new ArrayList<>();

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            age += ticks;
            anim.update(entity, context, ticks, exploding ? "explode" : scanning ? "scan" : null);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            PersistentDataContainer data = entity.getPersistentDataContainer();
            if (maxChain == 0) {
                maxChain = data.getOrDefault(CHAIN_KEY, PersistentDataType.INTEGER, 7 + random.nextInt(6));
                data.set(CHAIN_KEY, PersistentDataType.INTEGER, maxChain);
                anchorId = null;
            }
            Entity anchor = anchorId == null ? null : entity.getServer().getEntity(anchorId);
            if (anchor == null || !anchor.isValid()) {
                anchorMissing += ticks;
                if (anchorId == null || anchorMissing > 20) {
                    anchor = context.mobs().spawn(MINE_GUARDIAN_ANCHOR.id(), entity.getLocation().add(0, 0.5, 0)).orElse(null);
                    if (anchor != null) {
                        anchorId = anchor.getUniqueId();
                        data.set(ANCHOR_KEY, PersistentDataType.STRING, anchorId.toString());
                        anchor.getPersistentDataContainer().set(GUARDIAN_KEY, PersistentDataType.STRING, entity.getUniqueId().toString());
                    }
                    anchorMissing = 0;
                }
            } else {
                anchorMissing = 0;
            }
            if (exploding) {
                explodeProgress += 0.5F * ticks;
                Vector velocity = entity.getVelocity();
                entity.setVelocity(new Vector(velocity.getX() * 0.3, velocity.getY(), velocity.getZ() * 0.3));
                if (explodeProgress >= 10.0F) {
                    Location center = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
                    entity.remove();
                    MineExplosion.explode(entity, entity, center, 5.0F);
                    return;
                }
            }
            if (!entity.isInWater() && entity.isOnGround()) {
                // Out of the water it flops about.
                entity.setVelocity(new Vector((random.nextFloat() * 2 - 1) * 0.6, 0.6, (random.nextFloat() * 2 - 1) * 0.6));
                entity.setRotation(random.nextFloat() * 360.0F, 0);
            }
            LivingEntity chased = target(entity);
            if (chased != null) {
                timeSinceHadTarget = 0;
            } else {
                timeSinceHadTarget += ticks;
            }
            if (scanning) {
                eyeClosed = false;
                if (scanTime < maxScanTime) {
                    // The mod turns its look at random while scanning; here it sweeps around.
                    Location look = entity.getLocation();
                    entity.setRotation(look.getYaw() + 6.0F * ticks, (float) Math.sin(age * 0.05) * 20.0F);
                    if (scanTime % 20 < ticks) {
                        entity.getWorld().playSound(entity.getLocation(), "alexscaves:mine_guardian_scan", 1.0F, 1.0F);
                    }
                    if (scanTime % 5 < ticks) {
                        Player found = scan(entity);
                        if (found != null) {
                            target = found.getUniqueId();
                            scanning = false;
                        }
                    }
                    scanTime += ticks;
                } else {
                    scanTime = 0;
                    lastScanTick = age;
                    scanning = false;
                }
            } else if (eyeClosed) {
                if (timeSinceHadTarget == 0 || !entity.isInWater()) {
                    eyeClosed = false;
                } else if (timeSinceHadTarget > maxSleepTime && age - lastScanTick > 200 || entity.getNoDamageTicks() > 10) {
                    maxSleepTime = 200 + random.nextInt(100);
                    scanning = true;
                    scanTime = 0;
                    maxScanTime = 100 + random.nextInt(100);
                }
            } else if (entity.isInWater() && timeSinceHadTarget > 100) {
                eyeClosed = true;
            }
            if (chased != null && !exploding) {
                chase(entity, chased, ticks);
            } else {
                chaseTime = 0;
            }
            if (anchor != null) {
                tether(entity, anchor, chased != null);
                drawChain(entity, anchor);
            }
        }

        private LivingEntity target(LivingEntity entity) {
            if (target == null) {
                return null;
            }
            Entity found = entity.getServer().getEntity(target);
            if (found instanceof LivingEntity living && living.isValid() && !living.isDead() && living.getWorld().equals(entity.getWorld())
                && !(living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR))
                && living.getLocation().distanceSquared(entity.getLocation()) < 64 * 64) {
                return living;
            }
            target = null;
            return null;
        }

        /** Mod {@code MeleeGoal}: swim at the target and, 2 blocks away, start exploding; give up after 300 ticks. */
        private void chase(LivingEntity entity, LivingEntity chased, int ticks) {
            chaseTime += ticks;
            Vector to = chased.getEyeLocation().toVector().subtract(entity.getLocation().toVector());
            Location look = entity.getLocation().setDirection(to);
            entity.setRotation(look.getYaw(), look.getPitch());
            if (entity.getLocation().distance(chased.getLocation()) > 2.0) {
                if (entity.isInWater()) {
                    Vector velocity = entity.getVelocity().add(to.normalize().multiply(0.04 * ticks));
                    if (velocity.length() > 0.3) {
                        velocity.normalize().multiply(0.3);
                    }
                    entity.setVelocity(velocity);
                }
            } else {
                exploding = true;
            }
            if (chaseTime > 300) {
                lastScanTick = age;
                timeSinceHadTarget = 5;
                eyeClosed = true;
                target = null;
            }
        }

        /** Mod scan: the first player 8 blocks ahead, or the nearest one within 3 of where the look ends. */
        private static Player scan(LivingEntity entity) {
            Location eye = entity.getEyeLocation();
            Vector direction = eye.getDirection();
            RayTraceResult hit = entity.getWorld().rayTrace(eye, direction, 8.0, org.bukkit.FluidCollisionMode.NEVER, true, 0.0,
                other -> other != entity && validTarget(other));
            if (hit != null && hit.getHitEntity() instanceof Player player && entity.hasLineOfSight(player)) {
                return player;
            }
            Vector end = hit == null ? eye.toVector().add(direction.multiply(8.0)) : hit.getHitPosition();
            Player found = null;
            for (Entity near : entity.getWorld().getNearbyEntities(end.toLocation(entity.getWorld()), 3.5, 3.5, 3.5)) {
                if (near instanceof Player player && validTarget(player) && entity.hasLineOfSight(player)
                    && (found == null || player.getLocation().distanceSquared(entity.getLocation())
                    < found.getLocation().distanceSquared(entity.getLocation()))) {
                    found = player;
                }
            }
            return found;
        }

        private static boolean validTarget(Entity entity) {
            return entity instanceof Player player && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
        }

        /** Mod anchor tick: it bobs above the anchor, and the chain pulls it back past its length. */
        private void tether(LivingEntity entity, Entity anchor, boolean hasTarget) {
            Location from = anchor.getLocation().add(0, 1.0, 0);
            double distance = anchor.getLocation().distance(entity.getLocation());
            double goal = (entity.isInWater() ? maxChain + Math.sin(age * 0.1F + maxChain * 0.5F) * 0.25 : 5.0) + (hasTarget ? 5 : 0);
            Vector velocity = entity.getVelocity();
            if (entity.isInWater() && !hasTarget) {
                double x = anchor.getLocation().getX() - Math.sin(age * 0.025F + maxChain) * 0.5;
                double z = anchor.getLocation().getZ() + Math.cos(age * 0.025F + maxChain) * 0.5;
                double y = anchor.getLocation().getY() + goal;
                // Twice the mod's 0.005 a tick: this runs every 2 ticks.
                velocity.add(new Vector(x, y, z).subtract(entity.getLocation().toVector()).multiply(0.01));
            }
            if (distance > goal) {
                double pull = Math.min(distance - goal, 1.0) * 0.1;
                Vector toAnchor = from.toVector().subtract(entity.getLocation().toVector());
                if (toAnchor.length() > 1.0) {
                    toAnchor.normalize();
                }
                if (!hasTarget) {
                    velocity.multiply(0.64);
                }
                velocity.add(toAnchor.multiply(pull * 2));
            }
            entity.setVelocity(velocity);
        }

        /** The chain from the anchor to the guardian, one chain block display per link. */
        private void drawChain(LivingEntity entity, Entity anchor) {
            Location from = anchor.getLocation().add(0, 1.0, 0);
            Location to = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
            Vector line = to.toVector().subtract(from.toVector());
            double length = line.length();
            int links = Math.max(1, Math.min(24, (int) Math.ceil(length)));
            while (chain.size() < links) {
                chain.add(entity.getWorld().spawn(from, BlockDisplay.class, display -> {
                    display.setBlock(Material.IRON_CHAIN.createBlockData());
                    display.setPersistent(false);
                    display.setTeleportDuration(2);
                    display.setInterpolationDuration(2);
                }));
            }
            while (chain.size() > links) {
                chain.remove(chain.size() - 1).remove();
            }
            if (length < 1.0E-3) {
                return;
            }
            Vector3f direction = new Vector3f((float) line.getX(), (float) line.getY(), (float) line.getZ()).normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction);
            float linkLength = (float) (length / links);
            Vector3f centre = rotation.transform(new Vector3f(-0.5F, 0, -0.5F));
            Transformation transformation = new Transformation(centre, rotation, new Vector3f(1, linkLength, 1), new Quaternionf());
            for (int i = 0; i < links; i++) {
                BlockDisplay link = chain.get(i);
                if (!link.isValid()) {
                    continue;
                }
                link.teleport(from.clone().add(line.clone().multiply((double) i / links)));
                link.setInterpolationDelay(0);
                link.setTransformation(transformation);
            }
        }

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                Entity damager = byEntity.getDamager();
                if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                    damager = shooter;
                }
                if (damager instanceof LivingEntity living && living != entity) {
                    // Mod HurtByTargetGoal.
                    target = living.getUniqueId();
                    scanning = false;
                    eyeClosed = false;
                }
            }
        }

        @Override
        public void close(LivingEntity entity, ModMobContext context) {
            for (BlockDisplay link : chain) {
                link.remove();
            }
            chain.clear();
        }
    }

    /** The anchor stays put on the floor; it goes when its guardian is gone. */
    static final class Anchor implements ModMobBehaviour {
        private boolean settled;
        private int alone;

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            if (!settled) {
                settled = true;
                Location at = entity.getLocation();
                int down = at.getBlockY();
                while (down > at.getWorld().getMinHeight() && fluid(at.getWorld().getBlockAt(at.getBlockX(), down - 1, at.getBlockZ()))) {
                    down--;
                }
                entity.teleport(new Location(at.getWorld(), at.getX(), down, at.getZ(), ThreadLocalRandom.current().nextFloat() * 360.0F, 0));
            }
            String guardian = entity.getPersistentDataContainer().get(GUARDIAN_KEY, PersistentDataType.STRING);
            Entity found = guardian == null ? null : entity.getServer().getEntity(UUID.fromString(guardian));
            if (found == null || !found.isValid()) {
                alone += ticks;
                if (alone > 20) {
                    entity.remove();
                }
            } else {
                alone = 0;
            }
        }
    }

    /** The sea pig behaviour of a mob, for feeding it. */
    static Optional<SeaPig> seaPig(ModMobContext context, Entity entity) {
        return context.mobs().behaviourOf(entity).filter(SeaPig.class::isInstance).map(SeaPig.class::cast);
    }
}
