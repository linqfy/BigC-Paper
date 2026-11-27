package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mod {@code GloomothEntity}: a 4-health moth that flits about the caves, flies to a light source within 32 blocks and
 * circles it, runs from moth balls, lands now and then, and is a sacrifice for the Underzealots: carried to their altar
 * it turns into a Watcher. Dies without loot to a Vesper.
 */
final class Gloomoth extends ForlornMobs.Base implements ForlornMobs.Sacrifice {
    private boolean flying;
    private BlockScan.Found lightPos;
    private int refreshLightPosIn;
    private int flapTime;
    private boolean beingSacrificed;
    private int sacrificeTime;
    private Location lastSpot;
    private int stuckFor;
    private final Goals goals;

    Gloomoth(LivingEntity self) {
        super(self);
        if (self.getTicksLived() <= 5 && dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService.isNatural(self)) {
            doInitialPosing();
        }
        if (self.getTicksLived() > 20 && self.isInsideVehicle()) {
            // The ritual does not survive a restart: it would ride forever.
            self.leaveVehicle();
        }
        goals = new Goals(List.of(new FleeMothBalls(), new FindLight(), new Flight()));
    }

    /** Mod {@code doInitialPosing}: a natural spawn starts 3-7 blocks up in the air, flying. */
    private void doInitialPosing() {
        Location at = self.getLocation();
        World world = at.getWorld();
        int up = 3 + ThreadLocalRandom.current().nextInt(5);
        int y = at.getBlockY();
        for (int k = 0; k < up && ForlornMobs.air(world, at.getBlockX(), y + 1, at.getBlockZ()); k++) {
            y++;
        }
        flying = true;
        self.teleport(new Location(world, at.getBlockX() + 0.5, y, at.getBlockZ() + 0.5, at.getYaw(), at.getPitch()));
    }

    @Override
    public void triggerSacrificeIn(int ticks) {
        beingSacrificed = true;
        sacrificeTime = ticks;
    }

    @Override
    public boolean isValidSacrifice(int distanceFromGround) {
        return ForlornRules.gloomothSacrificeValid(distanceFromGround);
    }

    @Override
    public String soundSuffix(LivingEntity entity, String suffix) {
        // The mod's moth has no idle sound.
        return suffix.equals("idle") ? null : suffix;
    }

    @Override
    int experience() {
        return 0;
    }

    /** The moth dust does not anger the moths. */
    @Override
    public void enrage(LivingEntity enemy) {
    }

    @Override
    public void onDamaged(LivingEntity entity, ModMobContext ctx, EntityDamageEvent event) {
        // Mod causeFallDamage: false.
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDeathDrops(LivingEntity entity, List<org.bukkit.inventory.ItemStack> drops) {
        // Mod dropFromLootTable: nothing when a Vesper (or the Forsaken) killed it.
        if (entity.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity
            && ForlornMobs.VESPER.id().equals(context == null ? null : context.mobs().typeOf(byEntity.getDamager())
            .map(type -> type.id()).orElse(null))) {
            drops.clear();
        }
    }

    boolean hasLight() {
        return lightPos != null;
    }

    Location lightLocation() {
        return lightPos == null ? null : new Location(self.getWorld(), lightPos.x() + 0.5, lightPos.y() + 0.5, lightPos.z() + 0.5);
    }

    @Override
    public void tick(LivingEntity entity, ModMobContext ctx, int ticks) {
        context = ctx;
        age += ticks;
        animator.update(entity, ctx, ticks, flying ? "fly" : null);
        if (entity.isInsideVehicle()) {
            // Carried to the altar.
            goals.reset();
            stopNav();
            flying = false;
        } else {
            moveAbout(entity, ticks);
        }
        if (beingSacrificed && !entity.isInsideVehicle()) {
            // Dropped (the zealot was hit) or left: the ritual is over, it must not turn into a watcher on its own.
            beingSacrificed = false;
        }
        if (beingSacrificed) {
            sacrificeTime -= ticks;
            if (sacrificeTime < 10) {
                entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, entity.getLocation().add(0, 0.5, 0), 6, 0.6, 0.5, 0.6, 0.02);
            }
            if (sacrificeTime < 0) {
                transform(entity, ctx);
            }
        }
    }

    private void moveAbout(LivingEntity entity, int ticks) {
        if (lightPos != null && --refreshLightPosIn < 0) {
            refreshLightPosIn = 40 + ThreadLocalRandom.current().nextInt(100);
            Location light = lightLocation();
            if (ForlornMobs.distanceSquared(entity.getLocation(), light) >= 256.0
                || BlockScan.valueAt(entity.getWorld(), lightPos.x(), lightPos.y(), lightPos.z(), BlockScan.Kind.GLOOMOTH_LIGHT) <= 0
                || !lightStillShines()) {
                lightPos = null;
            }
        }
        Vector velocity = entity.getVelocity();
        boolean moving = velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > 0.0004;
        if (flying && moving && (flapTime -= ticks) <= 0) {
            flapTime = 8 + ThreadLocalRandom.current().nextInt(10);
            sound("gloomoth_flap", 0.7F);
        }
        Location here = entity.getLocation();
        stuckFor = lastSpot != null && lastSpot.getWorld() == here.getWorld() && lastSpot.distanceSquared(here) < 0.0004
            ? stuckFor + ticks : 0;
        lastSpot = here;
        goals.tick();
    }

    /** Mod {@code getLightEmission(pos) > 0}: the block gives off light itself. */
    private boolean lightStillShines() {
        return self.getWorld().getBlockAt(lightPos.x(), lightPos.y(), lightPos.z()).getBlockData().getLightEmission() > 0;
    }

    /** Mod: the sacrifice turns it into a Watcher, to the Underzealot's thanks. */
    private void transform(LivingEntity entity, ModMobContext ctx) {
        Location at = entity.getLocation();
        if (entity.getVehicle() instanceof LivingEntity vehicle) {
            ctx.mobs().behaviourOf(vehicle).ifPresent(behaviour -> {
                if (behaviour instanceof Underzealot underzealot) {
                    underzealot.postSacrifice();
                    underzealot.triggerIdleDigging();
                }
            });
        }
        entity.leaveVehicle();
        goals.reset();
        entity.remove();
        ctx.mobs().spawn(ForlornMobs.WATCHER.id(), at);
        at.getWorld().playSound(at, "alexscaves:watcher_spawn", 8.0F, 1.0F);
    }

    @Override
    public void close(LivingEntity entity, ModMobContext ctx) {
        goals.reset();
        beingSacrificed = false;
    }

    // --- goals ---------------------------------------------------------------------------------------------

    /** Mod {@code GloomothFleeMothBallsGoal}: away from moth balls until about 5 blocks from them. */
    private final class FleeMothBalls implements Goals.Goal {
        private BlockScan.Found ball;
        private Location retreatTo;

        @Override
        public boolean canUse() {
            // Mod: 1 in 20 each goal tick, and always on every tenth game tick.
            if (ThreadLocalRandom.current().nextInt(4) != 0) {
                return false;
            }
            BlockScan.Found found = ForlornMobs.nearestMothBall(self.getLocation(), 20);
            if (found != null) {
                ball = found;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinue() {
            return ball != null && ForlornMobs.distanceSquared(self.getLocation(),
                new Location(self.getWorld(), ball.x() + 0.5, ball.y() + 0.5, ball.z() + 0.5)) < 32.0;
        }

        @Override
        public void start() {
            flying = true;
        }

        @Override
        public void tick() {
            Location center = new Location(self.getWorld(), ball.x() + 0.5, ball.y() + 0.5, ball.z() + 0.5);
            if (retreatTo != null && ForlornMobs.distanceSquared(self.getLocation(), retreatTo) >= 4.0) {
                goTo(retreatTo, 1.0);
                return;
            }
            for (int i = 0; i < 15; i++) {
                Location away = ForlornMobs.awayFrom(self.getLocation(), 32, 15, center, ThreadLocalRandom.current());
                if (away != null) {
                    retreatTo = away;
                    break;
                }
            }
        }

        @Override
        public void stop() {
            retreatTo = null;
            ball = null;
        }
    }

    /** Mod {@code GloomothFindLightGoal}: every 2.5 to 5 seconds without a light it looks for one within 32 blocks. */
    private final class FindLight implements Goals.Goal {
        private int nextStart = 50;
        private BlockScan.Found found;
        private int tryTicks;

        @Override
        public boolean canUse() {
            if (lightPos != null) {
                return false;
            }
            if (nextStart > 0) {
                nextStart -= 2;
                return false;
            }
            nextStart = 50 + ThreadLocalRandom.current().nextInt(50);
            found = BlockScan.nearest(self.getLocation(), 32, 32, BlockScan.Kind.GLOOMOTH_LIGHT, this::valid);
            if (found == null && BlockScan.lastSearchIncomplete()) {
                nextStart = 4;
            }
            return found != null && !blocked(found);
        }

        /** Mod isValidTarget: it shines, and no moth ball scares within 10 (a fifth of a ball counts 2). */
        private boolean valid(BlockScan.Found candidate) {
            Block block = self.getWorld().getBlockAt(candidate.x(), candidate.y(), candidate.z());
            if (block.getBlockData().getLightEmission() <= 0 && block.getType() != Material.BEACON) {
                return false;
            }
            return ForlornMobs.nearestMothBall(block.getLocation(), 10) == null;
        }

        private boolean blocked(BlockScan.Found target) {
            Location eye = self.getEyeLocation();
            Vector to = new Vector(target.x() + 0.5, target.y() + 0.5, target.z() + 0.5).subtract(eye.toVector());
            double length = to.length();
            if (length < 1.0E-3) {
                return false;
            }
            RayTraceResult hit = self.getWorld().rayTraceBlocks(eye, to.normalize(), length, FluidCollisionMode.NEVER, true);
            return hit != null && hit.getHitBlock() != null
                && !(hit.getHitBlock().getX() == target.x() && hit.getHitBlock().getY() == target.y()
                && hit.getHitBlock().getZ() == target.z());
        }

        @Override
        public boolean canContinue() {
            return found != null && lightPos == null && tryTicks <= 1200
                && BlockScan.valueAt(self.getWorld(), found.x(), found.y(), found.z(), BlockScan.Kind.GLOOMOTH_LIGHT) > 0;
        }

        @Override
        public void start() {
            flying = true;
            tryTicks = 0;
        }

        @Override
        public void tick() {
            tryTicks += 2;
            Location center = new Location(self.getWorld(), found.x() + 0.5, found.y() + 0.5, found.z() + 0.5);
            // Mod getAcceptedDistance: its width plus 1.
            if (ForlornMobs.distanceSquared(self.getLocation(), center) < Math.pow(self.getWidth() + 1.0, 2)) {
                lightPos = found;
                refreshLightPosIn = 40 + ThreadLocalRandom.current().nextInt(100);
            } else {
                goTo(center, 1.0);
            }
        }

        @Override
        public void stop() {
            found = null;
        }
    }

    /** Mod {@code GloomothFlightGoal}: flits about, or circles its light within 1-5 blocks of it. */
    private final class Flight implements Goals.Goal {
        private Location goal;

        @Override
        public boolean canUse() {
            if (self.isInsideVehicle()) {
                return false;
            }
            if (self.isOnGround() && !flying && ThreadLocalRandom.current().nextInt(4) != 0) {
                return false;
            }
            Location pick = generate();
            if (pick == null) {
                return false;
            }
            goal = pick;
            return true;
        }

        @Override
        public boolean canContinue() {
            return goal != null && ForlornMobs.distanceSquared(self.getLocation(), goal) > 3.0 && !navDone();
        }

        @Override
        public void start() {
            flying = true;
            goTo(goal, 1.0);
        }

        @Override
        public void tick() {
            if (stuckFor >= 10 || ForlornMobs.distanceSquared(self.getLocation(), goal) > 100.0) {
                Location pick = generate();
                if (pick != null) {
                    goal = pick;
                }
                stuckFor = 0;
            }
            double speed = 1.0;
            if (lightPos != null) {
                flying = true;
                speed = 1.1;
            } else if (self.isOnGround()) {
                flying = false;
            }
            goTo(goal, speed);
        }

        @Override
        public void stop() {
            stopNav();
        }

        private Location generate() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (lightPos != null) {
                Vector offset = new Vector(random.nextFloat() * 4.0 + 1.0, random.nextFloat() * 2.0, 0)
                    .rotateAroundY(Math.PI * 2 * random.nextFloat());
                return lightLocation().add(offset);
            }
            Location flightPos = flightPos();
            if (overWaterOrVoid()) {
                return flightPos.add(0, random.nextFloat() * 8.0F, 0);
            }
            return random.nextInt(20) != 0 ? flightPos : ForlornMobs.ground(flightPos);
        }

        /** Mod findFlightPos: a spot within 5 blocks, 4-6 above the ground in the open or part way to the ceiling. */
        private Location flightPos() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location here = self.getLocation();
            World world = here.getWorld();
            Location spot = here.clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
            Location ground = ForlornMobs.ground(spot);
            double y;
            if (ForlornMobs.canSeeSky(world, spot.getBlockX(), spot.getBlockY(), spot.getBlockZ())) {
                y = ground.getY() + 4.0 + random.nextInt(3);
            } else {
                int ceiling = ground.getBlockY() + 2;
                while (ceiling < world.getMaxHeight() - 1 && !ForlornMobs.solid(world, spot.getBlockX(), ceiling, spot.getBlockZ())) {
                    ceiling++;
                }
                y = ground.getY() + (ceiling - ground.getY()) * (0.3F + random.nextFloat() * 0.5F);
            }
            Location pick = new Location(world, spot.getX(), y, spot.getZ());
            Location eye = self.getEyeLocation();
            Vector to = pick.toVector().subtract(eye.toVector());
            double length = to.length();
            if (length > 1.0E-3) {
                RayTraceResult hit = world.rayTraceBlocks(eye, to.normalize(), length, FluidCollisionMode.NEVER, true);
                if (hit != null) {
                    return hit.getHitPosition().toLocation(world);
                }
            }
            return pick;
        }

        private boolean overWaterOrVoid() {
            Location here = self.getLocation();
            World world = here.getWorld();
            int y = here.getBlockY();
            while (y > world.getMinHeight() && ForlornMobs.air(world, here.getBlockX(), y, here.getBlockZ())) {
                y--;
            }
            Block block = ForlornMobs.blockAt(world, here.getBlockX(), y, here.getBlockZ());
            return y <= world.getMinHeight() || block != null && (block.isLiquid());
        }
    }
}
