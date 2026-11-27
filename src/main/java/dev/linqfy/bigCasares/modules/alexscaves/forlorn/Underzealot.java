package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mod {@code UnderzealotEntity}: a 20-health cultist that comes in groups of four or five, digs itself into the ground and
 * out again, breaks torches and lanterns, hits for 4 and sinks into the ground after a hit. Its purpose is the ritual:
 * one of the group catches a gloomoth near the ground and carries it to an altar spot; the others fall in behind it,
 * form a ring and pray; after 10 seconds of chanting with three praying, the sacrifice turns into a Watcher in 15
 * seconds (a Vesper turns into the Forsaken instead).
 */
final class Underzealot extends ForlornMobs.Base {
    static final int MAX_WORSHIP_TIME = 500;
    static final NamespacedKey REINFORCED = new NamespacedKey("bigcasares", "ac_underzealot_reinforced");
    /** The Vesper's sacrifice makes the Forsaken: the Underzealots take Vespers as well as gloomoths. */
    static final boolean SACRIFICES_VESPERS = true;

    private boolean buried;
    private float buriedProgress = 20.0F;
    private boolean carrying;
    private boolean praying;
    private Location sacrificePos;
    private Location particlePos;
    private int worshipTime;
    private int sacrificeCooldown;
    private int cloudCooldown;
    private int idleBuryIn = 400 + ThreadLocalRandom.current().nextInt(800);
    private int reemergeTime;
    private Location reemergePos;
    private double buryBaseY;
    private Underzealot prior;
    private Underzealot after;
    private String animKind = "attack";
    private Block doorToOpen;
    private int chantSound;
    private boolean shouldBurrow;
    private boolean setUp;
    private final Goals goals;

    Underzealot(LivingEntity self) {
        super(self);
        buryBaseY = self.getLocation().getY();
        if (self.getTicksLived() > 20 && ForlornMobs.recoverFromRock(self)) {
            // It unloaded buried: it comes back on the surface (the state lived in fields only).
            buryBaseY = self.getLocation().getY();
            buriedProgress = 0.0F;
        } else if (self.getTicksLived() > 20) {
            buriedProgress = 0.0F;
        }
        goals = new Goals(List.of(new Sacrifice(), new Melee(), new Capture(), new Procession(), new BreakLight(),
            new Stroll()));
    }

    // --- state the other mobs read --------------------------------------------------------------------------

    /** Mod: melee picks ANIMATION_ATTACK_0 or _1 at random; the door and torch goals play BREAKTORCH. */
    @Override
    String attackAnimation() {
        return animKind.equals("attack") ? (ThreadLocalRandom.current().nextBoolean() ? "attack_0" : "attack_1") : "breaktorch";
    }

    boolean isBuried() {
        return buried;
    }

    boolean diggingInProgress() {
        return buriedProgress > 0.0F && buriedProgress < 20.0F;
    }

    boolean isCarrying() {
        return carrying;
    }

    boolean isPraying() {
        return praying;
    }

    boolean isPackFollower() {
        return prior != null;
    }

    boolean hasPackFollower() {
        return after != null;
    }

    Underzealot packLeader() {
        Underzealot leader = this;
        int guard = 0;
        while (leader.prior != null && leader.prior != this && guard++ < 64) {
            leader = leader.prior;
        }
        return leader;
    }

    int packSize() {
        int size = 1;
        int guard = 0;
        for (Underzealot member = packLeader(); member.after != null && guard++ < 64; member = member.after) {
            size++;
        }
        return size;
    }

    boolean isInPack(Underzealot other) {
        int guard = 0;
        for (Underzealot member = packLeader(); member != null && guard++ < 64; member = member.after) {
            if (member == other) {
                return true;
            }
        }
        return false;
    }

    /** Mod {@code isValidLeader}: it carries a sacrifice, leads and is alive. */
    boolean isValidLeader() {
        return carrying && !isPackFollower() && self.isValid() && !self.isDead();
    }

    void joinPackOf(Underzealot head) {
        prior = head;
        head.after = this;
    }

    void leavePack() {
        if (prior != null) {
            prior.after = null;
        }
        prior = null;
    }

    /** Mod {@code isTargetingBlocked}: those in a ritual do not fight. */
    boolean isTargetingBlocked() {
        if (isPackFollower()) {
            return packLeader().isCarrying();
        }
        return carrying || praying;
    }

    void triggerIdleDigging() {
        idleBuryIn = 0;
    }

    /** Mod {@code postSacrifice}: the transformation sound and a cooldown of 5 to 10 minutes. */
    void postSacrifice() {
        self.getWorld().playSound(self.getLocation(), "alexscaves:underzealot_transformation", 8.0F, 1.0F);
        sacrificeCooldown = 6000 + ThreadLocalRandom.current().nextInt(6000);
    }

    boolean isSurroundedByPrayers() {
        int prayers = 0;
        int guard = 0;
        for (Underzealot member = packLeader().after; member != null && guard++ < 64; member = member.after) {
            if (member.praying) {
                prayers++;
            }
        }
        return ForlornRules.surroundedByPrayers(prayers);
    }

    private boolean digsIdle() {
        return !carrying && !isPackFollower() && !praying && animTick < 0 && (target == null || !ForlornMobs.attackable(target));
    }

    @Override
    public String soundSuffix(LivingEntity entity, String suffix) {
        return suffix.equals("idle") && (carrying || praying || buried) ? null : suffix;
    }

    // --- hooks ---------------------------------------------------------------------------------------------

    @Override
    public void enrage(LivingEntity enemy) {
        if (!isTargetingBlocked()) {
            super.enrage(enemy);
        }
    }

    @Override
    boolean ignoresDamageFrom(LivingEntity attacker) {
        return context != null && context.mobs().typeOf(attacker)
            .filter(type -> type.equals(ForlornMobs.UNDERZEALOT) || type.equals(ForlornMobs.WATCHER)
                || type.equals(ForlornMobs.FORSAKEN)).isPresent();
    }

    @Override
    void gotHurtBy(LivingEntity attacker) {
        if (isTargetingBlocked()) {
            return;
        }
        super.gotHurtBy(attacker);
        // Mod alertOthers: the same kind within its follow range join in, unless they are in the ritual.
        double range = ForlornMobs.attr(self, org.bukkit.attribute.Attribute.FOLLOW_RANGE, 20.0);
        for (Entity near : self.getNearbyEntities(range, 10, range)) {
            if (near instanceof LivingEntity other && other != attacker && context.mobs().behaviourOf(other).orElse(null)
                instanceof Underzealot zealot && zealot.target == null && !zealot.isTargetingBlocked()) {
                zealot.target = attacker;
            }
        }
    }

    @Override
    public void onDamaged(LivingEntity entity, ModMobContext ctx, EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION || buried
            && event.getCause() != EntityDamageEvent.DamageCause.VOID && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
            return;
        }
        super.onDamaged(entity, ctx, event);
        // Mod hurt: a hit while carrying drops the sacrifice 65% of the time.
        if (!entity.getPassengers().isEmpty() && ThreadLocalRandom.current().nextFloat() < 0.65F) {
            entity.eject();
        }
    }

    // --- tick ----------------------------------------------------------------------------------------------

    @Override
    public void tick(LivingEntity entity, ModMobContext ctx, int ticks) {
        context = ctx;
        age += ticks;
        if (!setUp) {
            setUp = true;
            spawnReinforcements(entity, ctx);
        }
        tickBurial(entity, ticks);
        sacrificeCooldown = Math.max(0, sacrificeCooldown - ticks);
        cloudCooldown = Math.max(0, cloudCooldown - ticks);
        carrying = !entity.getPassengers().isEmpty();
        if (carrying && isPackFollower()) {
            entity.eject();
            carrying = false;
        }
        if (target != null) {
            keepTarget(ForlornMobs.attr(entity, org.bukkit.attribute.Attribute.FOLLOW_RANGE, 20.0), true, ticks);
        }
        if (target == null && !isTargetingBlocked() && ThreadLocalRandom.current().nextInt(20) == 0) {
            Player found = nearestPlayer(12.0, true, true);
            if (found != null) {
                target = found;
                unseen = 0;
            }
        }
        if (praying && (chantSound -= ticks) <= 0) {
            chantSound = 80;
            sound("underzealot_chant", 2.0F);
        }
        joinPack(entity);
        // Mod getters: buried (and digging in or out), carrying a sacrifice, praying.
        animator.update(entity, ctx, ticks, buried || diggingInProgress() ? "buried" : carrying ? "carrying" : praying ? "praying" : null);
        if (buried || diggingInProgress()) {
            praying = false;
            return;
        }
        openDoor(entity, ticks);
        if (animTick >= 0) {
            boolean light = animKind.equals("torch");
            if (advanceAnimation(ticks, light ? 10 : 8)) {
                animationHit(entity);
            }
        }
        goals.tick();
    }

    /** Mod {@code spawnReinforcements}: three or four more join a fresh natural spawn. */
    private void spawnReinforcements(LivingEntity entity, ModMobContext ctx) {
        var data = entity.getPersistentDataContainer();
        if (entity.getTicksLived() > 20 || data.has(REINFORCED, PersistentDataType.BYTE)
            || !dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService.isNatural(entity)) {
            return;
        }
        data.set(REINFORCED, PersistentDataType.BYTE, (byte) 1);
        int friends = 3 + ThreadLocalRandom.current().nextInt(2);
        for (int i = 0; i < friends; i++) {
            ctx.mobs().spawn(ForlornMobs.UNDERZEALOT.id(), entity.getLocation()).ifPresent(friend ->
                friend.getPersistentDataContainer().set(REINFORCED, PersistentDataType.BYTE, (byte) 1));
        }
    }

    /** Mod: sinks into the ground and rises out of it (the model goes with the entity, 2 blocks down). */
    private void tickBurial(LivingEntity entity, int ticks) {
        float before = buriedProgress;
        if (buriedProgress == 0.0F && buried || buriedProgress == 20.0F && !buried) {
            sound("underzealot_dig", 1.0F);
        }
        if (buried && buriedProgress < 20.0F) {
            buriedProgress = Math.min(20.0F, buriedProgress + 1.5F * ticks);
        }
        if (!buried && buriedProgress > 0.0F) {
            buriedProgress = Math.max(0.0F, buriedProgress - 1.5F * ticks);
        }
        if (buried && buriedProgress >= 20.0F) {
            if ((reemergeTime -= ticks) < 0 && reemergePos != null) {
                buryBaseY = reemergePos.getY();
                entity.teleport(new Location(reemergePos.getWorld(), reemergePos.getBlockX() + 0.5,
                    buryBaseY - 2.0, reemergePos.getBlockZ() + 0.5, entity.getLocation().getYaw(), 0),
                    TeleportFlag.EntityState.RETAIN_PASSENGERS);
                idleBuryIn = 400 + ThreadLocalRandom.current().nextInt(800);
                reemergeTime = 0;
                buried = false;
            }
        } else if (digsIdle() && (idleBuryIn -= ticks) < 0 && entity.isOnGround() && !buried && buriedProgress == 0.0F) {
            bury(findReemergePos(entity.getLocation(), 10), 40 + ThreadLocalRandom.current().nextInt(60));
            idleBuryIn = 0;
        }
        boolean under = buried || buriedProgress > 0.0F;
        if (under) {
            if (before == 0.0F && buried) {
                buryBaseY = entity.getLocation().getY();
            }
            stopNav();
            if (entity instanceof Mob mob) {
                mob.setAware(false);
            }
            entity.setGravity(false);
            entity.setVelocity(new Vector());
            Location at = entity.getLocation();
            double y = buryBaseY - 2.0 * (buriedProgress / 20.0);
            if (Math.abs(at.getY() - y) > 0.01) {
                at.setY(y);
                entity.teleport(at, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            }
            if (diggingInProgress()) {
                Block on = at.clone().add(0, 2.0 * (buriedProgress / 20.0) - 0.5, 0).getBlock();
                if (on.getType().isOccluding()) {
                    entity.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 2.0 * (buriedProgress / 20.0), 0), 3, 0.4, 0.1, 0.4,
                        0.1, on.getBlockData());
                }
            }
        } else if (before > 0.0F) {
            if (entity instanceof Mob mob) {
                mob.setAware(true);
            }
            entity.setGravity(true);
        }
    }

    void bury(Location emergeAt, int time) {
        buried = true;
        reemergePos = emergeAt;
        reemergeTime = time;
    }

    /** Mod {@code findReemergePos}: of 15 spots within the range, one that stands on solid ground. */
    Location findReemergePos(Location origin, int range) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = origin.getWorld();
        for (int i = 0; i < 15; i++) {
            int x = origin.getBlockX() + random.nextInt(range) - range / 2;
            int y = origin.getBlockY() + random.nextInt(range) - range / 2;
            int z = origin.getBlockZ() + random.nextInt(range) - range / 2;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            while (!ForlornMobs.air(world, x, y, z) && y < world.getMaxHeight() - 1) {
                y++;
            }
            while (ForlornMobs.air(world, x, y - 1, z) && y > world.getMinHeight()) {
                y--;
            }
            double dx = x - origin.getBlockX();
            double dy = y - origin.getBlockY();
            double dz = z - origin.getBlockZ();
            if (!ForlornMobs.air(world, x, y - 1, z) && dx * dx + dy * dy + dz * dz < range * range + 10) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return origin.clone();
    }

    /** Mod {@code AnimalJoinPackGoal(60, 11)}: join the tail of a pack that carries a sacrifice within 30 blocks. */
    private void joinPack(LivingEntity entity) {
        if (isPackFollower()) {
            if (!packLeader().isValidLeader() || prior == null || !prior.self.isValid()
                || ForlornMobs.distanceSquared(entity.getLocation(), prior.self.getLocation()) > 676.0) {
                leavePack();
            }
            return;
        }
        if (hasPackFollower() || ThreadLocalRandom.current().nextInt(5) != 0) {
            return;
        }
        Underzealot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity near : entity.getNearbyEntities(30, 30, 30)) {
            if (near instanceof LivingEntity other && context.mobs().behaviourOf(other).orElse(null) instanceof Underzealot zealot
                && !zealot.hasPackFollower() && zealot.packLeader().isValidLeader() && zealot != this && !zealot.isInPack(this)
                && zealot.packSize() < 11) {
                double distance = ForlornMobs.distanceSquared(entity.getLocation(), other.getLocation());
                if (distance <= bestDistance) {
                    best = zealot;
                    bestDistance = distance;
                }
            }
        }
        if (best != null && bestDistance >= 1.0) {
            joinPackOf(best);
        }
    }

    /** Mod {@code UnderzealotOpenDoorGoal}: opens a wooden door in its way (it does not shut it again). */
    private void openDoor(LivingEntity entity, int ticks) {
        if (doorToOpen != null) {
            if (animTick < 0 || !animKind.equals("door")) {
                doorToOpen = null;
            }
            return;
        }
        if (!(entity instanceof Mob mob) || !mob.getPathfinder().hasPath() || animTick >= 0) {
            return;
        }
        var path = mob.getPathfinder().getCurrentPath();
        Location next = path == null ? null : path.getNextPoint();
        if (next == null) {
            return;
        }
        for (int dy = 0; dy <= 1; dy++) {
            Block block = next.getBlock().getRelative(0, dy, 0);
            if (block.getBlockData() instanceof Door door && !door.isOpen()
                && block.getType() != org.bukkit.Material.IRON_DOOR
                && entity.getLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) < 4.0) {
                doorToOpen = block;
                animKind = "door";
                tryAnimation(null);
                return;
            }
        }
    }

    private void animationHit(LivingEntity entity) {
        switch (animKind) {
            case "door" -> {
                if (doorToOpen != null && doorToOpen.getBlockData() instanceof Door door && !door.isOpen()) {
                    door.setOpen(true);
                    // Claim plugins may veto what a mob changes (the mod itself ignores mobGriefing here).
                    if (!new org.bukkit.event.entity.EntityChangeBlockEvent(entity, doorToOpen, door).callEvent()) {
                        return;
                    }
                    doorToOpen.setBlockData(door);
                    entity.getWorld().playSound(doorToOpen.getLocation(), org.bukkit.Sound.BLOCK_WOODEN_DOOR_OPEN, 1.0F, 1.0F);
                }
            }
            case "torch" -> {
                if (light != null) {
                    Block block = light.getBlock();
                    if (BlockScan.valueAt(block.getWorld(), block.getX(), block.getY(), block.getZ(), BlockScan.Kind.UNDERZEALOT_LIGHT) > 0) {
                        if (new org.bukkit.event.entity.EntityChangeBlockEvent(entity, block,
                            org.bukkit.Material.AIR.createBlockData()).callEvent()) {
                            block.breakNaturally();
                        }
                    }
                }
            }
            default -> {
                LivingEntity prey = target;
                if (prey != null && ForlornMobs.attackable(prey) && entity.hasLineOfSight(prey)
                    && ForlornMobs.distance(entity, prey) < entity.getWidth() + prey.getWidth() + 1.0) {
                    double damage = ForlornMobs.damageOf(ForlornMobs.UNDERZEALOT);
                    hit(prey, damage, 0.5);
                    shouldBurrow = ThreadLocalRandom.current().nextBoolean();
                    hitVehicle(prey, damage);
                }
            }
        }
    }

    private Location light;

    @Override
    public void close(LivingEntity entity, ModMobContext ctx) {
        goals.reset();
        if (entity != null && entity.isValid() && !entity.isDead() && (buried || buriedProgress > 0.0F)) {
            // Unloading while buried: back to the surface, or it would reload stuck in the ground.
            Location at = entity.getLocation();
            at.setY(buryBaseY);
            entity.teleport(at, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            entity.setGravity(true);
            if (entity instanceof Mob mob) {
                mob.setAware(true);
            }
        }
        leavePack();
        if (after != null) {
            after.prior = null;
        }
    }

    // --- goals ---------------------------------------------------------------------------------------------

    /** Mod {@code UnderzealotSacrificeGoal}: the carrier goes to the altar spot and worships, the pack rings it and prays. */
    private final class Sacrifice implements Goals.Goal {
        private int executionCooldown = 10;
        private int attemptToFollow;
        private Location center;

        @Override
        public boolean canUse() {
            if (sacrificeCooldown > 0) {
                return false;
            }
            if (isPackFollower()) {
                Underzealot leader = packLeader();
                if (leader.isCarrying() && leader.sacrificePos != null
                    && ForlornMobs.distanceSquared(leader.self.getLocation(), center(leader.sacrificePos)) < 2.5) {
                    center = leader.sacrificePos;
                    return true;
                }
            } else if (carrying) {
                if ((executionCooldown -= 2) > 0) {
                    return false;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                executionCooldown = 20 + random.nextInt(100);
                Location pos = null;
                if (sacrificePos != null) {
                    if (validSpot(sacrificePos)) {
                        executionCooldown = 10;
                        pos = sacrificePos;
                    } else {
                        sacrificePos = null;
                    }
                }
                if (pos == null) {
                    pos = findNearestSpot();
                }
                if (pos != null) {
                    center = pos;
                    sacrificePos = center;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinue() {
            if (center == null || sacrificeCooldown > 0) {
                return false;
            }
            if (!isPackFollower()) {
                return carrying;
            }
            Underzealot leader = packLeader();
            return leader.isCarrying() && ForlornMobs.distanceSquared(leader.self.getLocation(), center(center)) < 5.0;
        }

        private Location center(Location block) {
            return new Location(block.getWorld(), block.getBlockX() + 0.5, block.getBlockY() + 0.5, block.getBlockZ() + 0.5);
        }

        /** Mod isValidSacrificePos: a solid block (the mod's bad-spot loop only ever looks at one corner). */
        private boolean validSpot(Location pos) {
            Block block = ForlornMobs.blockAt(pos.getWorld(), pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
            return block != null && !block.getType().isAir();
        }

        private Location findNearestSpot() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location here = self.getLocation();
            World world = here.getWorld();
            Location below = here.clone().add(0, -1, 0);
            if (validSpot(below)) {
                return below.getBlock().getLocation();
            }
            for (int i = 0; i < 20; i++) {
                int x = here.getBlockX() + random.nextInt(20) - 10;
                int y = here.getBlockY() + 5;
                int z = here.getBlockZ() + random.nextInt(20) - 10;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                while (ForlornMobs.air(world, x, y, z) && y > world.getMinHeight()) {
                    y--;
                }
                Location spot = new Location(world, x, y, z);
                if (validSpot(spot) && reachable(spot)) {
                    return spot;
                }
            }
            return null;
        }

        private boolean reachable(Location spot) {
            if (!(self instanceof Mob mob)) {
                return false;
            }
            var path = mob.getPathfinder().findPath(spot.clone().add(0.5, 1, 0.5));
            if (path == null || path.getFinalPoint() == null) {
                return false;
            }
            Location end = path.getFinalPoint();
            double dx = end.getBlockX() - spot.getBlockX();
            double dy = end.getBlockY() - spot.getBlockY();
            double dz = end.getBlockZ() - spot.getBlockZ();
            return dx * dx + dy * dy + dz * dz <= 3.0;
        }

        @Override
        public void tick() {
            if (center == null) {
                return;
            }
            Location centerPoint = center(center);
            if (isPackFollower()) {
                followerTick(centerPoint);
            } else {
                leaderTick(centerPoint);
            }
        }

        private void followerTick(Location centerPoint) {
            Underzealot leader = packLeader();
            double[] ring = ForlornRules.ringOffset(packPosition(), packSize());
            Location at = ForlornMobs.ground(centerPoint.clone().add(ring[0], 0, ring[1]));
            goTo(at, 1.0);
            if (praying) {
                attemptToFollow = 0;
            } else if ((navDone() || attemptToFollow > 60) && ForlornMobs.distanceSquared(self.getLocation(), at) > 8.0) {
                bury(findNear(at), 20 + ThreadLocalRandom.current().nextInt(20));
                return;
            }
            if (leader.sacrificeCooldown > 0) {
                sacrificeCooldown = leader.sacrificeCooldown;
            }
            if (ForlornMobs.distanceSquared(self.getLocation(), at) < 4.0) {
                praying = true;
                nudge(at);
                face(centerPoint);
                particlePos = leader.sacrificePos == null ? null : leader.sacrificePos.clone().add(0, 5, 0);
                if (worshipTime % 10 < 2) {
                    magic();
                }
                worshipTime += 2;
            } else {
                worshipTime = 0;
                attemptToFollow += 2;
            }
        }

        private void leaderTick(Location centerPoint) {
            particlePos = center.clone().add(0, 5, 0);
            if (ForlornMobs.distanceSquared(self.getLocation(), centerPoint) < 4.0) {
                nudge(centerPoint);
                worshipTime += 2;
                if (worshipTime > 200 && cloudCooldown <= 0 && isSurroundedByPrayers()
                    && !self.getPassengers().isEmpty()
                    && context.mobs().behaviourOf((LivingEntity) self.getPassengers().get(0)).orElse(null)
                    instanceof ForlornMobs.Sacrifice sacrifice) {
                    sacrifice.triggerSacrificeIn(300);
                    cloudCooldown = 400;
                    self.getWorld().spawnParticle(Particle.LARGE_SMOKE, particlePos, 30, 1.0, 1.0, 1.0, 0.02);
                }
            } else {
                worshipTime = 0;
            }
            goTo(centerPoint.clone().add(-0.5, -0.5, -0.5), 1.0);
        }

        /** A slight pull towards the spot it stands at. */
        private void nudge(Location at) {
            Vector pull = at.toVector().subtract(self.getLocation().toVector());
            if (pull.length() > 1.0) {
                pull.normalize();
            }
            self.setVelocity(self.getVelocity().add(pull.multiply(0.04)));
        }

        private void face(Location point) {
            Location look = self.getLocation().setDirection(point.toVector().subtract(self.getEyeLocation().toVector()));
            self.setRotation(look.getYaw(), look.getPitch());
        }

        /** Mod's particle event 61: magic from both hands to the altar's sky. */
        private void magic() {
            Location eye = self.getEyeLocation();
            for (double side : new double[] {0.8, -0.8}) {
                Vector offset = new Vector(side, 0, 0).rotateAroundY(-Math.toRadians(eye.getYaw()));
                self.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, eye.clone().add(offset), 3, 0.1, 0.1, 0.1, 0.03);
            }
        }

        private int packPosition() {
            int position = 1;
            int guard = 0;
            for (Underzealot member = packLeader(); member.after != null && guard++ < 64; member = member.after, position++) {
                if (member.after == Underzealot.this) {
                    return position;
                }
            }
            return position;
        }

        private Location findNear(Location at) {
            return at.clone().add(0, 1, 0);
        }

        @Override
        public void stop() {
            attemptToFollow = 0;
            worshipTime = 0;
            if (sacrificeCooldown == 0) {
                sacrificeCooldown = 100;
            }
            stopNav();
            self.eject();
            praying = false;
            particlePos = null;
        }
    }

    /** Mod {@code UnderzealotMeleeGoal}: run at the target, hit it for 4, and often sink and come up near it. */
    private final class Melee implements Goals.Goal {
        @Override
        public boolean canUse() {
            return target != null && ForlornMobs.attackable(target);
        }

        @Override
        public void tick() {
            LivingEntity prey = target;
            double distance = ForlornMobs.distance(self, prey);
            double reach = self.getWidth() + prey.getWidth();
            if (shouldBurrow && self.isOnGround()) {
                shouldBurrow = false;
                bury(findReemergePos(prey.getLocation(), 15), 20 + ThreadLocalRandom.current().nextInt(60));
            } else if (!buried) {
                if (diggingInProgress()) {
                    stopNav();
                } else {
                    goTo(prey, 1.3);
                    if (distance < reach + 1.0 && animTick < 0) {
                        animKind = "attack";
                        tryAnimation("underzealot_attack");
                    }
                }
            }
        }

        @Override
        public void stop() {
            shouldBurrow = false;
        }
    }

    /** Mod {@code UnderzealotCaptureSacrificeGoal}: catch a gloomoth near the ground and pick it up. */
    private final class Capture implements Goals.Goal {
        private LivingEntity sacrifice;
        private int validTimeCheck;

        @Override
        public boolean canUse() {
            if (carrying || isPackFollower() || sacrificeCooldown > 0) {
                return false;
            }
            boolean fiveth = age % 10 < 2 && ThreadLocalRandom.current().nextInt(3) == 0;
            boolean fighting = target != null && ForlornMobs.attackable(target);
            if (!fiveth && !fighting) {
                return false;
            }
            LivingEntity closest = null;
            for (Entity near : self.getNearbyEntities(20, 20, 20)) {
                if (near instanceof LivingEntity living && validSacrifice(living)
                    && (isVesper(living) || ThreadLocalRandom.current().nextInt(4) == 0)
                    && (closest == null || ForlornMobs.distanceSquared(self.getLocation(), living.getLocation())
                    < ForlornMobs.distanceSquared(self.getLocation(), closest.getLocation()))
                    && self.hasLineOfSight(living)) {
                    closest = living;
                }
            }
            sacrifice = closest;
            return sacrifice != null;
        }

        private boolean isVesper(LivingEntity living) {
            return context.mobs().behaviourOf(living).orElse(null) instanceof Vesper;
        }

        private boolean validSacrifice(LivingEntity living) {
            var behaviour = context.mobs().behaviourOf(living).orElse(null);
            if (living.isInsideVehicle() || !(behaviour instanceof ForlornMobs.Sacrifice offering)) {
                return false;
            }
            if (behaviour instanceof Vesper && !SACRIFICES_VESPERS) {
                return false;
            }
            return offering.isValidSacrifice(distanceToGround(living));
        }

        private int distanceToGround(LivingEntity living) {
            int down = 0;
            Location at = living.getLocation();
            World world = at.getWorld();
            int y = at.getBlockY();
            while (y > world.getMinHeight() && ForlornMobs.air(world, at.getBlockX(), y, at.getBlockZ())) {
                y--;
                down++;
            }
            return down;
        }

        @Override
        public boolean canContinue() {
            return sacrifice != null && sacrifice.isValid() && !sacrifice.isInsideVehicle() && !isPackFollower()
                && ForlornMobs.distance(self, sacrifice) < 32.0 && sacrificeCooldown <= 0;
        }

        @Override
        public void start() {
            validTimeCheck = 0;
        }

        @Override
        public void tick() {
            double distance = ForlornMobs.distance(self, sacrifice);
            goTo(sacrifice, 1.0);
            if (distance < 1.4) {
                self.addPassenger(sacrifice);
            }
            Vector sub = sacrifice.getLocation().toVector().subtract(self.getLocation().toVector());
            if (!buried && sub.getY() > 0.5 && Math.hypot(sub.getX(), sub.getZ()) < 2.0 && self.isOnGround()) {
                self.setVelocity(self.getVelocity().setY(0.42));
            }
            validTimeCheck += 2;
            if (validTimeCheck % 100 == 0 && !validSacrifice(sacrifice)) {
                sacrifice = null;
            }
        }

        @Override
        public void stop() {
            stopNav();
            sacrifice = null;
        }
    }

    /** Mod {@code UnderzealotProcessionGoal}: followers line up behind the one in front, digging in to catch up. */
    private final class Procession implements Goals.Goal {
        private int attempt;

        @Override
        public boolean canUse() {
            return ThreadLocalRandom.current().nextInt(10) == 0 && isPackFollower() && prior != null;
        }

        @Override
        public boolean canContinue() {
            return isPackFollower() && prior != null;
        }

        @Override
        public void tick() {
            if (buried || diggingInProgress() || prior == null) {
                praying = false;
                return;
            }
            LivingEntity ahead = prior.self;
            double distance = ForlornMobs.distance(self, ahead);
            if (distance > self.getWidth() + 0.5) {
                Vector toward = ahead.getLocation().toVector().subtract(self.getLocation().toVector()).normalize()
                    .multiply(Math.max(distance - 1.0, 0.0));
                goTo(self.getLocation().add(toward), 1.0);
            }
            if (distance > 8.0) {
                attempt += 2;
                if (navDone() || attempt > 60) {
                    bury(ahead.getLocation(), 20 + ThreadLocalRandom.current().nextInt(20));
                }
            } else {
                attempt = 0;
            }
        }

        @Override
        public void stop() {
            stopNav();
            attempt = 0;
        }
    }

    /** Mod {@code UnderzealotBreakLightGoal}: every 25 to 75 seconds it goes to a torch within 32 blocks and breaks it. */
    private final class BreakLight implements Goals.Goal {
        private int nextStart = 500;
        private BlockScan.Found found;
        private int tryTicks;

        @Override
        public boolean canUse() {
            if (nextStart > 0) {
                nextStart -= 2;
                return false;
            }
            nextStart = 500 + ThreadLocalRandom.current().nextInt(1000);
            found = BlockScan.nearest(self.getLocation(), 32, 32, BlockScan.Kind.UNDERZEALOT_LIGHT, this::shines);
            if (found == null && BlockScan.lastSearchIncomplete()) {
                nextStart = 4;
            }
            return found != null && !blocked(found);
        }

        private boolean shines(BlockScan.Found candidate) {
            return self.getWorld().getBlockAt(candidate.x(), candidate.y(), candidate.z()).getBlockData().getLightEmission() > 0;
        }

        private boolean blocked(BlockScan.Found spot) {
            Location eye = self.getEyeLocation();
            Vector to = new Vector(spot.x() + 0.5, spot.y() + 0.5, spot.z() + 0.5).subtract(eye.toVector());
            double length = to.length();
            if (length < 1.0E-3) {
                return false;
            }
            RayTraceResult hit = self.getWorld().rayTraceBlocks(eye, to.normalize(), length, FluidCollisionMode.NEVER, true);
            return hit != null && hit.getHitBlock() != null && !(hit.getHitBlock().getX() == spot.x()
                && hit.getHitBlock().getY() == spot.y() && hit.getHitBlock().getZ() == spot.z());
        }

        @Override
        public boolean canContinue() {
            return found != null && !isPackFollower() && !carrying && tryTicks <= 1200
                && BlockScan.valueAt(self.getWorld(), found.x(), found.y(), found.z(), BlockScan.Kind.UNDERZEALOT_LIGHT) > 0;
        }

        @Override
        public void start() {
            tryTicks = 0;
        }

        @Override
        public void tick() {
            tryTicks += 2;
            Location spot = new Location(self.getWorld(), found.x() + 0.5, found.y() + 0.5, found.z() + 0.5);
            Location look = self.getLocation().setDirection(spot.toVector().subtract(self.getEyeLocation().toVector()));
            self.setRotation(look.getYaw(), look.getPitch());
            if (ForlornMobs.distanceSquared(self.getLocation(), spot) < 4.0) {
                stopNav();
                if (animTick < 0) {
                    animKind = "torch";
                    light = spot;
                    tryAnimation(null);
                }
            } else {
                goTo(spot, 1.0);
            }
        }

        @Override
        public void stop() {
            found = null;
            stopNav();
        }
    }

    /** Mod's {@code RandomStrollGoal(1.0, 100)}. */
    private final class Stroll implements Goals.Goal {
        @Override
        public boolean canUse() {
            if (self.isInsideVehicle() || ThreadLocalRandom.current().nextInt(50) != 0) {
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
            return !navDone();
        }

        @Override
        public void tick() {
        }

        @Override
        public void stop() {
            stopNav();
        }
    }

    /** Positions of the pack from the leader, for the tests and the ring. */
    List<Underzealot> pack() {
        List<Underzealot> members = new ArrayList<>();
        int guard = 0;
        for (Underzealot member = packLeader(); member != null && guard++ < 64; member = member.after) {
            members.add(member);
        }
        return members;
    }
}
