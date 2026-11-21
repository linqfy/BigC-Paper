package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobAnimator;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * The mobs of the Forlorn Hollows with the mod's stats, drops, spawn rules and abilities: the Gloomoth (a moth that
 * circles lights and flees moth balls), the Vesper (a bat that dives at what is under it and hangs from ceilings), the
 * Corrodent (a rodent that digs through the ground and fears light), the Underzealot (a cultist that buries itself and
 * sacrifices gloomoths in a ritual) and the Watcher that the ritual brings. Each rides a vanilla base whose own goals
 * are removed; the behaviours drive them.
 */
public final class ForlornMobs {
    static final CaveBiomeId BIOME = CaveBiomeId.FORLORN_HOLLOWS;

    /** Mod: 4 health, 0.99 wide; a bee flies the way it does (0.6 flying speed gives its 0.12 blocks a tick). */
    public static final ModMobType GLOOMOTH = new ModMobType("gloomoth", BIOME, "bee", SpawnCategory.AMBIENT,
        Map.of("max_health", 4.0, "movement_speed", 0.2, "scale", 1.4),
        "alexscaves_gloomoth", List.of(MobDrop.withLooting("ac_moth_dust", 0, 2, 2)));
    /** Mod: 16 health, 3 damage, 52 blocks of follow range. A parrot grown to its size; flying speed 1.6 matches the mod's dive. */
    public static final ModMobType VESPER = new ModMobType("vesper", BIOME, "parrot", SpawnCategory.MONSTER,
        Map.of("max_health", 16.0, "movement_speed", 0.25, "attack_damage", 3.0, "follow_range", 52.0,
            "flying_speed", 1.6, "scale", 2.0),
        "alexscaves_vesper", List.of(new MobDrop("ac_guano", 0, 2, false, 1.0, 0.0, false),
            new MobDrop("ac_vesper_wing", 0, 1, false, 1.0, 0.0, false)));
    /** Mod: 0.9 by 0.9, 16 health, 2 armor, 3 damage; a pig is exactly that size. */
    public static final ModMobType CORRODENT = new ModMobType("corrodent", BIOME, "pig", SpawnCategory.MONSTER,
        Map.of("max_health", 16.0, "movement_speed", 0.25, "armor", 2.0, "attack_damage", 3.0, "follow_range", 16.0),
        "alexscaves_corrodent", List.of(MobDrop.of("ac_corrodent_teeth", 0, 1), MobDrop.of("coarse_dirt", 0, 2)));
    /** Mod: 0.8 by 1.2, 20 health, 4 damage, 20 blocks of follow range. */
    public static final ModMobType UNDERZEALOT = new ModMobType("underzealot", BIOME, "sheep", SpawnCategory.MONSTER,
        Map.of("max_health", 20.0, "movement_speed", 0.25, "attack_damage", 4.0, "follow_range", 20.0, "scale", 0.9),
        "alexscaves_underzealot", List.of(new MobDrop("ac_dark_tatters", 0, 2, false, 1.0, 0.0, false),
            MobDrop.rare("ac_desolate_dagger", 0.025, 0.01)));
    /** Mod: 0.9 by 1.9, 30 health, 4 damage, 256 blocks of follow range; a llama is that tall. It only comes from the ritual. */
    public static final ModMobType WATCHER = new ModMobType("watcher", BIOME, "llama", SpawnCategory.MONSTER,
        Map.of("max_health", 30.0, "movement_speed", 0.25, "attack_damage", 4.0, "follow_range", 256.0),
        "alexscaves_watcher", List.of(MobDrop.of("ac_occult_gem", 0, 1), MobDrop.of("ac_dark_tatters", 0, 2)));

    /**
     * Mod: 3.0 by 3.5, 250 health, 10 damage, 64 blocks of follow range, 0.6 knockback resistance; a ravager scaled to its
     * size. It only comes from the ritual (the mod has no natural spawn for it). Loot: 7 to 12 pure darkness, no looting.
     */
    public static final ModMobType FORSAKEN = new ModMobType("forsaken", BIOME, "ravager", SpawnCategory.MONSTER,
        Map.of("max_health", ForsakenRules.MAX_HEALTH, "movement_speed", ForsakenRules.WALK_SPEED,
            "attack_damage", ForsakenRules.ATTACK_DAMAGE, "follow_range", ForsakenRules.FOLLOW_RANGE,
            "knockback_resistance", 0.6, "scale", 1.55),
        "alexscaves_forsaken", List.of(new MobDrop("ac_pure_darkness", 7, 12, false, 1.0, 0.0, false)));

    public static final List<ModMobType> ALL = List.of(GLOOMOTH, VESPER, CORRODENT, UNDERZEALOT, WATCHER, FORSAKEN);

    /** Vanilla bases that animals' interactions (food, saddles, leads, shears) must not reach. */
    static final Set<String> BASES = Set.of("bee", "parrot", "pig", "sheep", "llama", "ravager");

    private ForlornMobs() {
    }

    public static void register(ModMobCatalog catalog, ModBlockPalette palette) {
        configureScans(palette);
        catalog.register(GLOOMOTH, (entity, type) -> new Gloomoth(entity));
        catalog.register(VESPER, (entity, type) -> new Vesper(entity));
        catalog.register(CORRODENT, (entity, type) -> new Corrodent(entity));
        catalog.register(UNDERZEALOT, (entity, type) -> new Underzealot(entity));
        catalog.register(WATCHER, (entity, type) -> new Watcher(entity));
        catalog.register(FORSAKEN, (entity, type) -> new Forsaken(entity));
        // Mod spawn rules: the gloomoth needs a dark spot with 5 free blocks above, the vesper the same with 6 and
        // monster rules, the corrodent and the underzealot monster rules. The watcher has no natural spawn.
        catalog.spawnRule(GLOOMOTH.id(), (block, random) -> ForlornRules.gloomothLightLevel(block.getLightFromSky(),
            block.getLightLevel(), random) && freeAbove(block, 5));
        catalog.spawnRule(VESPER.id(), (block, random) -> monsterRules(block, random) && freeAbove(block, 6));
        catalog.spawnRule(CORRODENT.id(), ForlornMobs::monsterRules);
        catalog.spawnRule(UNDERZEALOT.id(), ForlornMobs::monsterRules);
    }

    /** Mod {@code Monster.checkMonsterSpawnRules}: not in peaceful and dark enough. */
    static boolean monsterRules(Block block, RandomGenerator random) {
        return block.getWorld().getDifficulty() != Difficulty.PEACEFUL
            && ForlornRules.darkEnough(block.getLightFromSky(), block.getLightLevel(), random);
    }

    /** Mod spawn check: the spot and the {@code count} blocks over it are air. */
    static boolean freeAbove(Block block, int count) {
        for (int k = 0; k <= count; k++) {
            if (!air(block.getWorld(), block.getX(), block.getY() + k, block.getZ())) {
                return false;
            }
        }
        return true;
    }

    // --- block scans ---------------------------------------------------------------------------------------

    private static void configureScans(ModBlockPalette palette) {
        Set<Material> underzealot = new HashSet<>(Set.of(Material.TORCH, Material.WALL_TORCH, Material.LANTERN,
            Material.SOUL_TORCH, Material.SOUL_WALL_TORCH, Material.SOUL_LANTERN, Material.END_ROD));
        BlockScan.configure(BlockScan.Kind.UNDERZEALOT_LIGHT, underzealot, Map.of());
        Set<Material> gloomoth = new HashSet<>(underzealot);
        gloomoth.addAll(Tag.CAMPFIRES.getValues());
        gloomoth.addAll(Set.of(Material.JACK_O_LANTERN, Material.SEA_LANTERN, Material.GLOWSTONE, Material.SHROOMLIGHT,
            Material.OCHRE_FROGLIGHT, Material.VERDANT_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT, Material.BEACON));
        Map<BlockData, Integer> modLights = new HashMap<>();
        for (String id : palette.registry().blockIds()) {
            if (id.equals("ambersol") || id.equals("uranium_rod") || id.equals("block_of_uranium") || id.startsWith("radon_lamp")) {
                for (String variant : palette.registry().variants(id)) {
                    modLights.put(palette.get(id, variant), 1);
                }
            }
        }
        BlockScan.configure(BlockScan.Kind.GLOOMOTH_LIGHT, gloomoth, modLights);
        Map<BlockData, Integer> balls = new HashMap<>();
        for (String variant : palette.registry().variants("moth_ball")) {
            try {
                balls.put(palette.get("moth_ball", variant), Integer.parseInt(variant));
            } catch (NumberFormatException ignored) {
                // Not a ball count.
            }
        }
        BlockScan.configure(BlockScan.Kind.MOTH_BALL, Set.of(), balls);
    }

    /**
     * Mod {@code getNearestMothBall}: the closest moth ball whose balls make it scary at that distance from
     * {@code from} (integer block distance, as in the mod), searching {@code range} blocks around, or null.
     */
    static BlockScan.Found nearestMothBall(Location from, int range) {
        return BlockScan.nearest(from, range, range, BlockScan.Kind.MOTH_BALL, found -> {
            double dx = found.x() - from.getBlockX();
            double dy = found.y() - from.getBlockY();
            double dz = found.z() - from.getBlockZ();
            return ForlornRules.mothBallRepels(found.value(), range, dx * dx + dy * dy + dz * dz);
        });
    }

    // --- helpers -------------------------------------------------------------------------------------------

    static double attr(LivingEntity entity, Attribute attribute, double fallback) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    /** The mod's attack damage (the vanilla bases have none of their own). */
    static double damageOf(ModMobType type) {
        return type.attribute("attack_damage", 1.0);
    }

    static boolean attackable(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
            return false;
        }
        return !(living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR));
    }

    static double distance(Entity a, Entity b) {
        return a.getWorld() == b.getWorld() ? a.getLocation().distance(b.getLocation()) : Double.POSITIVE_INFINITY;
    }

    static double distanceSquared(Location a, Location b) {
        return a.getWorld() == b.getWorld() ? a.distanceSquared(b) : Double.POSITIVE_INFINITY;
    }

    /** Vanilla {@code knockback(strength, dx, dz)} from {@code from} towards {@code target}. */
    static void knock(LivingEntity target, Entity from, double strength) {
        strength *= 1.0 - attr(target, Attribute.KNOCKBACK_RESISTANCE, 0.0);
        if (strength <= 0.0) {
            return;
        }
        double dx = from.getLocation().getX() - target.getLocation().getX();
        double dz = from.getLocation().getZ() - target.getLocation().getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4) {
            dx = ThreadLocalRandom.current().nextDouble() - 0.5;
            dz = ThreadLocalRandom.current().nextDouble() - 0.5;
            length = Math.sqrt(dx * dx + dz * dz);
        }
        Vector velocity = target.getVelocity();
        target.setVelocity(new Vector(velocity.getX() / 2.0 - dx / length * strength,
            target.isOnGround() ? Math.min(0.4, velocity.getY() / 2.0 + strength) : velocity.getY(),
            velocity.getZ() / 2.0 - dz / length * strength));
    }

    /** The living thing behind a damage event (a projectile's shooter), or null. */
    static LivingEntity damagerOf(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        return damager instanceof LivingEntity living ? living : null;
    }

    static Block blockAt(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return world.getBlockAt(x, y, z);
    }

    static boolean air(World world, int x, int y, int z) {
        Block block = blockAt(world, x, y, z);
        return block != null && block.getType().isAir();
    }

    /** Mod {@code BlockState.blocksMotion}: a block you cannot walk through. */
    static boolean solid(World world, int x, int y, int z) {
        Block block = blockAt(world, x, y, z);
        return block != null && !block.isPassable() && !block.isLiquid();
    }

    static boolean canSeeSky(World world, int x, int y, int z) {
        return world.isChunkLoaded(x >> 4, z >> 4) && y >= world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
    }

    /** The first solid block at or below the point (its top is where something stands), or the world floor. */
    static Location ground(Location point) {
        World world = point.getWorld();
        int y = point.getBlockY();
        while (y > world.getMinHeight() && !solid(world, point.getBlockX(), y, point.getBlockZ())) {
            y--;
        }
        return new Location(world, point.getX(), y + 1, point.getZ());
    }

    /**
     * Mod {@code DefaultRandomPos.getPosAway}: a spot on the ground up to {@code range} sideways and {@code vertical} up
     * or down, in the half of the circle away from {@code avoid}; null when it finds none.
     */
    static Location awayFrom(Location from, int range, int vertical, Location avoid, RandomGenerator random) {
        World world = from.getWorld();
        for (int attempt = 0; attempt < 10; attempt++) {
            double base = Math.atan2(from.getZ() - avoid.getZ(), from.getX() - avoid.getX());
            double angle = base + (random.nextDouble() * 2 - 1) * (Math.PI / 2);
            double distance = Math.sqrt(random.nextDouble()) * Math.sqrt(2.0) * range;
            int x = (int) Math.floor(from.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(from.getZ() + Math.sin(angle) * distance);
            int y = from.getBlockY() + random.nextInt(2 * vertical + 1) - vertical;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            while (y < world.getMaxHeight() - 1 && solid(world, x, y, z)) {
                y++;
            }
            while (y > world.getMinHeight() && !solid(world, x, y - 1, z)) {
                y--;
            }
            if (y > world.getMinHeight() && !solid(world, x, y, z) && !solid(world, x, y + 1, z)
                && !world.getBlockAt(x, y, z).isLiquid()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    /** Mod {@code RandomStrollGoal} / {@code LandRandomPos.getPos}: a spot on the ground within the range. */
    static Location stroll(Location from, int range, int vertical, RandomGenerator random) {
        return awayFrom(from, range, vertical, from.clone().add(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5), random);
    }

    /**
     * A mob that unloaded while it was buried or digging comes back stuck in the rock with no gravity: this puts it on the
     * first standing spot at or above where it is (or leaves it if there is none within 48 blocks) and gives its gravity and
     * AI back.
     *
     * @return true when it was stuck and was recovered
     */
    static boolean recoverFromRock(LivingEntity entity) {
        Location at = entity.getLocation();
        World world = at.getWorld();
        boolean inRock = !entity.hasGravity() || solid(world, at.getBlockX(), at.getBlockY(), at.getBlockZ())
            || solid(world, at.getBlockX(), (int) Math.floor(entity.getEyeLocation().getY()), at.getBlockZ());
        if (!inRock) {
            return false;
        }
        for (int y = at.getBlockY(); y < Math.min(world.getMaxHeight() - 2, at.getBlockY() + 48); y++) {
            if (solid(world, at.getBlockX(), y - 1, at.getBlockZ()) && !solid(world, at.getBlockX(), y, at.getBlockZ())
                && !solid(world, at.getBlockX(), y + 1, at.getBlockZ())) {
                entity.teleport(new Location(world, at.getBlockX() + 0.5, y, at.getBlockZ() + 0.5, at.getYaw(), 0));
                break;
            }
        }
        entity.setGravity(true);
        entity.setVelocity(new Vector());
        if (entity instanceof Mob mob) {
            mob.setAware(true);
        }
        return true;
    }

    // --- behaviour base ------------------------------------------------------------------------------------

    /** Interface of the mobs the Underzealots sacrifice (mod {@code UnderzealotSacrifice}). */
    interface Sacrifice {
        void triggerSacrificeIn(int ticks);

        boolean isValidSacrifice(int distanceFromGround);
    }

    /** Mobs that go for whatever the moth dust points at (mod tag {@code moth_dust_enrages}). */
    interface Enrageable {
        void enrage(LivingEntity target);
    }

    /** What every behaviour of the biome shares: a clock, throttled pathfinding, a target and a bite animation. */
    abstract static class Base implements ModMobBehaviour, Enrageable {
        final LivingEntity self;
        ModMobContext context;
        long age;
        LivingEntity target;
        int unseen;
        /** Ticks into the running attack animation, or -1. */
        int animTick = -1;
        /** Base loop (idle, walk, state loops) and one-shot actions of the mod's model. */
        final MobAnimator animator = new MobAnimator();
        private Location navGoal;
        private double navSpeed;
        private long navAt = -1000;
        private LivingEntity navEntity;

        Base(LivingEntity self) {
            this.self = self;
        }

        Location here() {
            return self.getLocation();
        }

        // --- navigation ---

        /** Walks (or flies) to the spot, planning a new path when the goal moved, the speed changed or it ran out. */
        void goTo(Location to, double speed) {
            if (!(self instanceof Mob mob) || to == null || to.getWorld() != self.getWorld()) {
                return;
            }
            boolean changed = navGoal == null || navEntity != null || navGoal.getWorld() != to.getWorld()
                || navGoal.distanceSquared(to) > 2.25 || speed != navSpeed;
            if (changed || age - navAt >= 20 || !mob.getPathfinder().hasPath()) {
                navGoal = to.clone();
                navEntity = null;
                navSpeed = speed;
                navAt = age;
                mob.getPathfinder().moveTo(to, speed);
            }
        }

        /** Follows an entity, planning again every half second. */
        void goTo(LivingEntity who, double speed) {
            if (!(self instanceof Mob mob) || who.getWorld() != self.getWorld()) {
                return;
            }
            if (navEntity != who || speed != navSpeed || age - navAt >= 10 || !mob.getPathfinder().hasPath()) {
                navEntity = who;
                navGoal = null;
                navSpeed = speed;
                navAt = age;
                mob.getPathfinder().moveTo(who, speed);
            }
        }

        void stopNav() {
            if (self instanceof Mob mob) {
                mob.getPathfinder().stopPathfinding();
            }
            navGoal = null;
            navEntity = null;
        }

        boolean navDone() {
            return !(self instanceof Mob mob) || !mob.getPathfinder().hasPath();
        }

        // --- animation and sound ---

        /** Starts the attack animation (15 ticks, damage at tick 8) when none runs; true when it started. */
        boolean tryAnimation(String sound) {
            return tryAnimation(sound, attackAnimation());
        }

        /** The model animation of the mob's melee attack (the mod's Animation it starts), picked each time. */
        String attackAnimation() {
            return "attack";
        }

        /** As {@link #tryAnimation(String)}, playing the named model animation over the base loop. */
        boolean tryAnimation(String sound, String animation) {
            if (animTick >= 0) {
                return false;
            }
            animTick = 0;
            if (sound != null) {
                sound(sound, 1.0F);
            }
            animator.action(self, context, animation);
            return true;
        }

        /**
         * Advances the animation clock; true on the tick that reaches {@code hitTick}.
         */
        boolean advanceAnimation(int ticks, int hitTick) {
            if (animTick < 0) {
                return false;
            }
            int before = animTick;
            animTick += ticks;
            if (animTick >= 15) {
                animTick = -1;
            }
            return before < hitTick && (animTick < 0 || animTick >= hitTick) && before + ticks >= hitTick;
        }

        void sound(String name, float volume) {
            self.getWorld().playSound(self.getLocation(), "alexscaves:" + name, volume,
                0.9F + ThreadLocalRandom.current().nextFloat() * 0.2F);
        }

        // --- targeting ---

        /**
         * Mod {@code TargetGoal.canContinueToUse}: the target must be alive, attackable, within the follow range and
         * (when the goal needs sight) not unseen for more than 3 seconds.
         */
        boolean keepTarget(double followRange, boolean mustSee, int ticks) {
            if (target == null) {
                return false;
            }
            if (!attackable(target) || target.getWorld() != self.getWorld()
                || distanceSquared(here(), target.getLocation()) > followRange * followRange) {
                target = null;
                return false;
            }
            if (mustSee) {
                unseen = self.hasLineOfSight(target) ? 0 : unseen + ticks;
                if (unseen > 60) {
                    target = null;
                    return false;
                }
            }
            return true;
        }

        /** Mod {@code NearestAttackableTargetGoal} over players: the closest in a cube of {@code range}. */
        Player nearestPlayer(double range, boolean mustSee, boolean mustReach) {
            Player best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity near : self.getNearbyEntities(range, range, range)) {
                if (!(near instanceof Player player) || !attackable(player)) {
                    continue;
                }
                double distance = distanceSquared(here(), player.getLocation());
                if (distance > range * range || distance >= bestDistance || mustSee && !self.hasLineOfSight(player)) {
                    continue;
                }
                if (mustReach && !canReach(player)) {
                    continue;
                }
                best = player;
                bestDistance = distance;
            }
            return best;
        }

        /** Farthest a target is path-checked: beyond it the check is "no" (the search would cost more than it is worth). */
        static final double REACH_CHECK_RANGE = 64.0;
        /** How long a path check is trusted (1.5 seconds). */
        static final int REACH_CACHE_TICKS = 30;
        private java.util.UUID reachOf;
        private boolean reachResult;
        private long reachAt = -1000;

        /**
         * Mod {@code TargetGoal.canReach}: a path whose end is within 1.5 blocks of the target. Cached for 1.5 seconds per
         * target and not tried beyond 64 blocks.
         */
        boolean canReach(LivingEntity who) {
            if (!(self instanceof Mob mob)) {
                return false;
            }
            if (who.getUniqueId().equals(reachOf) && age - reachAt < REACH_CACHE_TICKS) {
                return reachResult;
            }
            reachOf = who.getUniqueId();
            reachAt = age;
            reachResult = false;
            if (distanceSquared(here(), who.getLocation()) > REACH_CHECK_RANGE * REACH_CHECK_RANGE) {
                return false;
            }
            var path = mob.getPathfinder().findPath(who);
            if (path == null || path.getFinalPoint() == null) {
                return false;
            }
            Location end = path.getFinalPoint();
            double dx = end.getBlockX() - who.getLocation().getBlockX();
            double dy = end.getBlockY() - who.getLocation().getBlockY();
            double dz = end.getBlockZ() - who.getLocation().getBlockZ();
            reachResult = dx * dx + dy * dy + dz * dz <= 2.25;
            return reachResult;
        }

        /** Mod {@code HurtByTargetGoal}: whoever hurt it becomes the target, apart from those the mob ignores. */
        boolean hurtByTarget(LivingEntity attacker) {
            return attackable(attacker) && !ignoresDamageFrom(attacker);
        }

        boolean ignoresDamageFrom(LivingEntity attacker) {
            return false;
        }

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext ctx, EntityDamageEvent event) {
            LivingEntity attacker = damagerOf(event);
            if (attacker != null && attacker != entity && hurtByTarget(attacker)) {
                gotHurtBy(attacker);
            }
        }

        /** Mod moth dust: it goes for whatever the dust was pointed at. */
        @Override
        public void enrage(LivingEntity enemy) {
            if (attackable(enemy) && enemy != self) {
                gotHurtBy(enemy);
            }
        }

        void gotHurtBy(LivingEntity attacker) {
            target = attacker;
            unseen = 0;
        }

        /** Mod {@code Monster} experience: 5 for monsters, nothing for the ambient moth. */
        int experience() {
            return 5;
        }

        @Override
        public int experience(LivingEntity entity, int vanilla) {
            return experience();
        }

        /** Deals melee damage and optional knockback the way the mod's goals do. */
        void hit(LivingEntity victim, double damage, double knockback) {
            victim.damage(damage, self);
            if (knockback > 0) {
                knock(victim, self, knockback);
            }
        }

        /** Mod: a rider's vehicle is thrown along and hurt for half. */
        void hitVehicle(LivingEntity victim, double damage) {
            Entity vehicle = victim.getVehicle();
            if (vehicle != null) {
                vehicle.setVelocity(victim.getVelocity());
                if (vehicle instanceof LivingEntity ridden) {
                    ridden.damage(damage * 0.5, self);
                }
            }
        }
    }
}
