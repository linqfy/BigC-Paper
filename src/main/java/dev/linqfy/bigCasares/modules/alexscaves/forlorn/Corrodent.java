package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mod {@code CorrodentEntity}: a 16-health rodent that digs through the ground (every block but the ones withers cannot
 * break, and glass). It fears light (more than 7 of block light), flees it and buries itself; it hunts players by
 * burrowing under them, popping out to bite for 4.5 and ducking back into the ground. Immune to being in walls; falls
 * hurt it 5 less.
 *
 * <p>The vanilla pig under it walks; while it digs the behaviour moves it through the blocks itself (like the gum worm).
 */
final class Corrodent extends ForlornMobs.Base {
    private static final int LIGHT_THRESHOLD = 7;

    private boolean digging;
    private boolean inDigMode;
    private boolean afraid;
    private int timeDigging;
    private int fleeLightFor;
    private final Vector digVelocity = new Vector();
    private Location digGoal;
    private int outOfRock;
    private int digSound;
    // attack goal
    private boolean burrowing;
    private int burrowCheckTime;
    private int evadeFor;
    // fear goal / dig randomly
    private final Goals goals;

    Corrodent(LivingEntity self) {
        super(self);
        if (self.getTicksLived() > 5) {
            // It unloaded mid-dig: it comes back frozen in the rock with no gravity.
            ForlornMobs.recoverFromRock(self);
        }
        goals = new Goals(List.of(new FearLight(), new Attack(), new DigRandomly(), new Stroll()));
    }

    /** Mod {@code canDigBlock}: solid and dry, other than the blocks withers cannot break and glass. */
    static boolean canDig(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return ForlornRules.diggable(type.isOccluding(), block.isLiquid(),
            Tag.WITHER_IMMUNE.isTagged(type) || Tag.IMPERMEABLE.isTagged(type));
    }

    /** Mod {@code isSafeDig}: the block and the one under it can be dug. */
    static boolean safeDig(World world, int x, int y, int z) {
        return canDig(ForlornMobs.blockAt(world, x, y, z)) && canDig(ForlornMobs.blockAt(world, x, y - 1, z));
    }

    private boolean inWall() {
        Block head = ForlornMobs.blockAt(self.getWorld(), self.getEyeLocation().getBlockX(), self.getEyeLocation().getBlockY(),
            self.getEyeLocation().getBlockZ());
        return head != null && !head.isPassable() && head.getType().isOccluding();
    }

    private void setDigging(boolean value) {
        if (digging && !value) {
            sound("corrodent_dig_stop", 1.0F);
        }
        digging = value;
    }

    @Override
    public void onDamaged(LivingEntity entity, ModMobContext ctx, EntityDamageEvent event) {
        switch (event.getCause()) {
            // Mod: immune to being in a wall.
            case SUFFOCATION -> {
                event.setCancelled(true);
                return;
            }
            case FALL -> {
                event.setDamage(ForlornRules.corrodentFallDamage((float) event.getDamage()));
                if (event.getDamage() <= 0) {
                    event.setCancelled(true);
                    return;
                }
            }
            default -> {
            }
        }
        super.onDamaged(entity, ctx, event);
    }

    @Override
    public void tick(LivingEntity entity, ModMobContext ctx, int ticks) {
        context = ctx;
        age += ticks;
        animator.update(entity, ctx, ticks, digging ? "dig" : afraid ? "afraid" : null);
        upkeep(entity, ticks);
        if (fleeLightFor > 0) {
            fleeLightFor = Math.max(0, fleeLightFor - ticks);
        }
        if (target != null) {
            keepTarget(ForlornMobs.attr(entity, org.bukkit.attribute.Attribute.FOLLOW_RANGE, 16.0), false, ticks);
        }
        if (target == null && ThreadLocalRandom.current().nextInt(5) == 0) {
            // Mod MobTarget3DGoal: any player in a cube, seen or not.
            Player found = nearestPlayer(ForlornMobs.attr(entity, org.bukkit.attribute.Attribute.FOLLOW_RANGE, 16.0), false, false);
            if (found != null) {
                target = found;
            }
        }
        if (advanceAnimation(ticks, 8)) {
            bitePrey();
        }
        goals.tick();
    }

    /** The server tick of the mod's entity: switching between walking and digging. */
    private void upkeep(LivingEntity entity, int ticks) {
        if (digging) {
            timeDigging += ticks;
            if (!inDigMode) {
                enterDig(entity);
            }
            if (timeDigging > 40 && !inWall()) {
                setDigging(false);
                entity.teleport(entity.getLocation().add(0, 1, 0));
                entity.setVelocity(entity.getVelocity().add(new Vector(0, 0.35, 0)));
            } else {
                Location at = entity.getLocation();
                if (!safeDig(at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ())) {
                    if (canDig(ForlornMobs.blockAt(at.getWorld(), at.getBlockX(), at.getBlockY() + 1, at.getBlockZ()))) {
                        digVelocity.setY(digVelocity.getY() + 0.1 * ticks);
                    }
                    if (canDig(ForlornMobs.blockAt(at.getWorld(), at.getBlockX(), at.getBlockY() - 1, at.getBlockZ()))) {
                        digVelocity.setY(digVelocity.getY() - 0.08 * ticks);
                    }
                }
                if ((digSound -= ticks) <= 0) {
                    digSound = 40;
                    sound("corrodent_dig_loop", 1.0F);
                }
            }
        } else {
            timeDigging = 0;
            outOfRock = 0;
            if (inDigMode) {
                exitDig(entity);
            }
        }
    }

    private void enterDig(LivingEntity entity) {
        inDigMode = true;
        stopNav();
        if (entity instanceof Mob mob) {
            mob.setAware(false);
        }
        entity.setGravity(false);
        entity.setVelocity(new Vector());
        digVelocity.zero();
        burst(entity);
    }

    private void exitDig(LivingEntity entity) {
        inDigMode = false;
        digGoal = null;
        if (entity instanceof Mob mob) {
            mob.setAware(true);
        }
        entity.setGravity(true);
        burst(entity);
    }

    /** Mod event 77: a ring of block particles from the ground around it. */
    private void burst(LivingEntity entity) {
        Block ground = entity.getLocation().getBlock().getRelative(0, -1, 0);
        if (ground.getType().isOccluding()) {
            entity.getWorld().spawnParticle(Particle.BLOCK, entity.getLocation().add(0, 0.2, 0), 26, 0.8, 0.2, 0.8, 0.3,
                ground.getBlockData());
        }
    }

    /**
     * Mod {@code DiggingMoveControl}: pushes towards the spot, slowly while it still goes under (the first 2 seconds),
     * through anything diggable. Returns true when it arrived or cannot go on.
     */
    private boolean digToward(Location goal, double speed, int ticks) {
        LivingEntity entity = self;
        World world = entity.getWorld();
        double burySpeed = timeDigging < 40 ? 0.25 : 1.0;
        for (int t = 0; t < ticks; t++) {
            Location at = entity.getLocation();
            Vector toGoal = goal.toVector().subtract(at.toVector());
            double distance = toGoal.length();
            if (distance < entity.getWidth() * 0.15) {
                return true;
            }
            Vector push = toGoal.multiply(speed * burySpeed * 0.025 / distance);
            digVelocity.add(push).multiply(0.9 * 0.91);
            Location next = at.clone().add(digVelocity);
            Block cell = ForlornMobs.blockAt(world, next.getBlockX(), next.getBlockY(), next.getBlockZ());
            Block head = ForlornMobs.blockAt(world, next.getBlockX(), next.getBlockY() + 1, next.getBlockZ());
            if (cell == null || !(cell.getType().isAir() || cell.isPassable() || canDig(cell)) || head == null
                || next.getY() <= world.getMinHeight() + 1) {
                // Mod: an undiggable block turns it up and ends the move.
                digVelocity.setY(0.3 * 0.7).multiply(0.7);
                return true;
            }
            outOfRock = !canDig(cell) && !canDig(head) ? outOfRock + 1 : 0;
            if (outOfRock > 12) {
                setDigging(false);
                return true;
            }
            if (distance >= entity.getWidth()) {
                next.setYaw((float) Math.toDegrees(-Math.atan2(digVelocity.getX(), digVelocity.getZ())));
                next.setPitch((float) -Math.toDegrees(Math.atan2(digVelocity.getY(), Math.hypot(digVelocity.getX(), digVelocity.getZ()))));
            } else {
                next.setYaw(at.getYaw());
            }
            entity.teleport(next, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            Block here = next.getBlock();
            if (here.getType().isOccluding() && ThreadLocalRandom.current().nextInt(3) == 0) {
                world.spawnParticle(Particle.BLOCK, next.clone().add(0, 0.5, 0), 4, 0.4, 0.3, 0.4, 0.1, here.getBlockData());
            }
        }
        return false;
    }

    /** Mod {@code checkAndDealDamage} with a multiplier of 1.5. */
    private void bitePrey() {
        setDigging(false);
        LivingEntity prey = target;
        if (prey == null || !ForlornMobs.attackable(prey)) {
            return;
        }
        if (self.hasLineOfSight(prey) && ForlornMobs.distance(self, prey) < self.getWidth() + prey.getWidth() + 1.0) {
            double damage = ForlornMobs.damageOf(ForlornMobs.CORRODENT) * 1.5;
            hit(prey, damage, 0.2 + 0.3 * 1.5);
            hitVehicle(prey, damage);
            burrowing = true;
            if (ThreadLocalRandom.current().nextBoolean()) {
                evadeFor = 60 + ThreadLocalRandom.current().nextInt(40);
            }
        }
    }

    @Override
    boolean hurtByTarget(LivingEntity attacker) {
        return ForlornMobs.attackable(attacker);
    }

    @Override
    public void close(LivingEntity entity, ModMobContext ctx) {
        goals.reset();
        if (entity != null && inDigMode) {
            exitDig(entity);
            if (entity.isValid() && !entity.isDead()) {
                ForlornMobs.recoverFromRock(entity);
            }
        }
    }

    @Override
    String attackAnimation() {
        return "bite";
    }

    boolean isAfraid() {
        return afraid;
    }

    // --- goals ---------------------------------------------------------------------------------------------

    private int blockLight() {
        return self.getLocation().getBlock().getLightFromBlocks();
    }

    /** Mod {@code CorrodentFearLightGoal}: walk away from the light, and dig in when stuck or when out of it. */
    private final class FearLight implements Goals.Goal {
        private Location retreatTo;
        private int tryDigTime;
        private Location tryDigPos;

        @Override
        public boolean canUse() {
            return ForlornRules.corrodentFearsLight(blockLight()) && !digging;
        }

        @Override
        public void tick() {
            fleeLightFor = 50;
            if (retreatTo != null && ForlornMobs.distanceSquared(self.getLocation(), retreatTo) >= 6.0) {
                afraid = true;
                goTo(retreatTo, 1.0);
                if (self.isOnGround() && (tryDigTime += 2) > 20) {
                    tryDigTime = 0;
                    if (tryDigPos != null && tryDigPos.getWorld() == self.getWorld()
                        && tryDigPos.distanceSquared(self.getLocation()) < 2.25) {
                        setDigging(true);
                    }
                    tryDigPos = self.getLocation();
                }
            } else {
                for (int i = 0; i < 15; i++) {
                    Location pick = ForlornMobs.awayFrom(self.getLocation(), 30, 15, self.getLocation(), ThreadLocalRandom.current());
                    if (pick != null && pick.getBlock().getLightFromBlocks() < LIGHT_THRESHOLD) {
                        retreatTo = pick;
                        break;
                    }
                }
            }
        }

        @Override
        public void stop() {
            afraid = false;
            if (self.isOnGround()) {
                fleeLightFor = 50;
                setDigging(true);
            }
            stopNav();
            tryDigPos = null;
            tryDigTime = 0;
        }
    }

    /** Mod {@code CorrodentAttackGoal}: chase, burrow under and surface to bite. */
    private final class Attack implements Goals.Goal {
        @Override
        public boolean canUse() {
            return target != null && ForlornMobs.attackable(target) && fleeLightFor <= 0;
        }

        @Override
        public void tick() {
            LivingEntity prey = target;
            double distance = ForlornMobs.distance(self, prey);
            double reach = self.getWidth() + prey.getWidth();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if ((burrowCheckTime += 2) > 40 && animTick < 0) {
                burrowCheckTime = 0;
                if (!burrowing) {
                    if (self.isOnGround() && distance > reach && (!canReach(prey) || distance > 20.0 || random.nextInt(20) == 0)) {
                        burrowing = true;
                        evadeFor = 60 + random.nextInt(40);
                    }
                } else if (distance < reach + 1.0 || random.nextInt(10) == 0) {
                    burrowing = false;
                    evadeFor = 0;
                }
            }
            if (evadeFor > 0) {
                evadeFor -= 2;
                burrowing = true;
                setDigging(true);
                if (digGoal == null) {
                    digGoal = evadePosition(prey.getLocation());
                }
                if (digGoal != null && inDigMode && digToward(digGoal, 1.0, 2)) {
                    digGoal = null;
                }
            } else if (burrowing) {
                if (self.isOnGround()) {
                    setDigging(true);
                }
                digGoal = null;
                if (inDigMode) {
                    digToward(prey.getLocation(), 2.0, 2);
                } else {
                    goTo(prey, 2.0);
                }
                if (!inWall() && inDigMode && timeDigging > 40) {
                    setDigging(false);
                    burrowing = false;
                }
            } else {
                if (!inWall()) {
                    setDigging(false);
                } else {
                    setDigging(true);
                    digVelocity.setY(digVelocity.getY() + 0.1);
                }
                if (inDigMode) {
                    digToward(prey.getLocation(), 1.5, 2);
                } else {
                    goTo(prey, 1.5);
                }
            }
            if (distance < reach + 1.0) {
                tryAnimation("corrodent_attack");
            }
            if (animTick >= 0) {
                setDigging(false);
            }
        }

        /** Mod generateEvadePosition: a safe place to dig to within 8 blocks of the target. */
        private Location evadePosition(Location around) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            World world = around.getWorld();
            for (int i = 0; i < 10; i++) {
                int x = around.getBlockX() + random.nextInt(16) - 8;
                int y = around.getBlockY() + random.nextInt(16) - 8;
                int z = around.getBlockZ() + random.nextInt(16) - 8;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    break;
                }
                while (ForlornMobs.air(world, x, y, z) && y > world.getMinHeight()) {
                    y--;
                }
                if (safeDig(world, x, y, z)) {
                    return new Location(world, x + 0.5, y + 0.5, z + 0.5);
                }
            }
            return null;
        }

        @Override
        public void stop() {
            burrowing = false;
            burrowCheckTime = 0;
            evadeFor = 0;
            digGoal = null;
            stopNav();
        }
    }

    /** Mod {@code CorrodentDigRandomlyGoal}: wanders underground, coming up after 15 seconds of it. */
    private final class DigRandomly implements Goals.Goal {
        private boolean surface;

        @Override
        public boolean canUse() {
            if (target != null && ForlornMobs.attackable(target) || self.isInsideVehicle()
                || !(digging || self.isOnGround() || inWall())) {
                return false;
            }
            if (!digging && !inWall() && ThreadLocalRandom.current().nextInt(20) != 0) {
                return false;
            }
            if (digging && timeDigging > 300) {
                surface = true;
            }
            Location pick = generate();
            if (pick == null) {
                return false;
            }
            digGoal = pick;
            return true;
        }

        @Override
        public boolean canContinue() {
            return digging && digGoal != null;
        }

        @Override
        public void start() {
            setDigging(true);
        }

        @Override
        public void tick() {
            if (!inDigMode) {
                return;
            }
            if (surface && ForlornMobs.distanceSquared(self.getLocation(), digGoal) < 4.0) {
                setDigging(false);
                return;
            }
            if (digToward(digGoal, 1.0, 2)) {
                digGoal = null;
            }
        }

        @Override
        public void stop() {
            surface = false;
            digGoal = null;
        }

        private Location generate() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            World world = self.getWorld();
            Location here = self.getLocation();
            for (int i = 0; i < 20; i++) {
                int x = here.getBlockX() + random.nextInt(32) - 16;
                int y = here.getBlockY() + random.nextInt(32) - 16;
                int z = here.getBlockZ() + random.nextInt(32) - 16;
                if (y < world.getMinHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
                    break;
                }
                if (surface) {
                    while (y < world.getMaxHeight() - 1 && !ForlornMobs.air(world, x, y, z) && ForlornMobs.blockAt(world, x, y, z) != null) {
                        y++;
                    }
                    if (ForlornMobs.air(world, x, y, z)) {
                        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
                    }
                } else {
                    while (ForlornMobs.air(world, x, y, z) && y > world.getMinHeight()) {
                        y--;
                    }
                    if (safeDig(world, x, y, z)) {
                        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
                    }
                }
            }
            return null;
        }
    }

    /** Mod's {@code RandomStrollGoal(1.0, 20)}. */
    private final class Stroll implements Goals.Goal {
        @Override
        public boolean canUse() {
            if (digging || self.isInsideVehicle() || ThreadLocalRandom.current().nextInt(10) != 0) {
                return false;
            }
            Location spot = ForlornMobs.stroll(self.getLocation(), 10, 7, ThreadLocalRandom.current());
            if (spot == null) {
                return false;
            }
            goTo(spot, 1.0);
            return true;
        }

        @Override
        public boolean canContinue() {
            return !digging && !navDone();
        }

        @Override
        public void tick() {
        }

        @Override
        public void stop() {
            stopNav();
        }
    }
}
