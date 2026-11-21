package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mod {@code VesperEntity}: a bat the size of a person with 16 health that hangs from ceilings, flies about and dives at
 * what is under it (players within 20 blocks, gloomoths within 32; from a ceiling, whatever stands under it). It bites
 * for 3 and circles above its prey before the next bite; a bite on a raised shield, or a killed gloomoth that was
 * circling a light, grounds it for a few seconds.
 */
final class Vesper extends ForlornMobs.Base implements ForlornMobs.Sacrifice {
    private boolean flying;
    private boolean hanging;
    private int groundedFor;
    private int timeFlying;
    private int timeHanging;
    private boolean validHangingPos;
    private int checkHangingTime;
    private Block prevHangBlock;
    private int lastTargetId = -1;
    // attack goal
    private Vector startOrbitFrom;
    private int orbitTime;
    private int maxOrbitTime = 80;
    // sacrifice
    private boolean beingSacrificed;
    private int sacrificeTime;
    private final Goals goals;

    Vesper(LivingEntity self) {
        super(self);
        if (self.getTicksLived() <= 5 && dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService.isNatural(self)) {
            doInitialPosing();
        }
        if (self.getTicksLived() > 20 && self.isInsideVehicle()) {
            self.leaveVehicle();
        }
        goals = new Goals(List.of(new Attack(), new Stroll(), new FlyAndHang()));
    }

    /** Mod {@code doInitialPosing}: up to the ceiling (or 100 blocks), where it hangs, or flies if there is none. */
    private void doInitialPosing() {
        Location at = self.getLocation();
        World world = at.getWorld();
        int y = at.getBlockY();
        for (int k = 0; k < 100 && y < world.getMaxHeight() - 1 && ForlornMobs.air(world, at.getBlockX(), y, at.getBlockZ()); k++) {
            y++;
        }
        if (ForlornMobs.air(world, at.getBlockX(), y, at.getBlockZ())) {
            flying = true;
        } else {
            hanging = true;
        }
        self.teleport(new Location(world, at.getBlockX() + 0.5, y - self.getHeight(), at.getBlockZ() + 0.5,
            at.getYaw(), at.getPitch()));
    }

    @Override
    public void triggerSacrificeIn(int ticks) {
        beingSacrificed = true;
        sacrificeTime = ticks;
    }

    @Override
    public boolean isValidSacrifice(int distanceFromGround) {
        return ForlornRules.vesperSacrificeValid(distanceFromGround, hanging);
    }

    @Override
    public String soundSuffix(LivingEntity entity, String suffix) {
        return suffix.equals("idle") && hanging ? "quiet_idle" : suffix;
    }

    @Override
    String attackAnimation() {
        return "bite";
    }

    boolean isHanging() {
        return hanging;
    }

    @Override
    public void onDamaged(LivingEntity entity, ModMobContext ctx, EntityDamageEvent event) {
        // Mod: no fall damage.
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        super.onDamaged(entity, ctx, event);
    }

    @Override
    public void tick(LivingEntity entity, ModMobContext ctx, int ticks) {
        context = ctx;
        age += ticks;
        boolean captured = entity.isInsideVehicle();
        if (captured) {
            flying = false;
            hanging = false;
            goals.reset();
            stopNav();
            entity.setGravity(true);
        } else {
            upkeep(entity, ticks);
        }
        // Mod getters: captured by a zealot, hanging asleep from the ceiling, flying.
        animator.update(entity, ctx, ticks, captured ? "captured" : hanging ? "sleep" : flying ? "fly" : null);
        groundedFor = Math.max(0, groundedFor - ticks);
        if (!captured) {
            beingSacrificed = false;
        }
        if (beingSacrificed && captured) {
            sacrificeTime -= ticks;
            if (sacrificeTime < 10) {
                entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, entity.getLocation().add(0, 0.5, 0), 6, 0.6, 0.5, 0.6, 0.02);
            }
            if (sacrificeTime < 0) {
                transform(entity, ctx);
                return;
            }
        }
        if (!captured) {
            if (advanceAnimation(ticks, 8)) {
                bite(entity);
            }
            goals.tick();
        }
    }

    /**
     * Mod: the sacrifice turns it into the Forsaken, to the Underzealot's thanks. When the boss cannot be made (a
     * cancelled spawn) the vesper is let go instead.
     */
    private void transform(LivingEntity entity, ModMobContext ctx) {
        beingSacrificed = false;
        Location at = entity.getLocation();
        LivingEntity carrier = entity.getVehicle() instanceof LivingEntity vehicle ? vehicle : null;
        entity.leaveVehicle();
        goals.reset();
        var forsaken = ctx.mobs().spawn(ForlornMobs.FORSAKEN.id(), at);
        if (forsaken.isPresent()) {
            // Mod: the Underzealot's thanks and cooldown (here only once the boss exists).
            if (carrier != null) {
                ctx.mobs().behaviourOf(carrier).ifPresent(behaviour -> {
                    if (behaviour instanceof Underzealot underzealot) {
                        underzealot.postSacrifice();
                        underzealot.triggerIdleDigging();
                    }
                });
            }
            at.getWorld().playSound(at, "alexscaves:forsaken_spawn", 8.0F, 1.0F);
            ctx.mobs().behaviourOf(forsaken.get()).ifPresent(behaviour -> {
                if (behaviour instanceof Forsaken boss) {
                    boss.summoned();
                }
            });
            entity.remove();
        }
    }

    /** The server tick of the mod's entity: hanging, flying and screaming at a new target. */
    private void upkeep(LivingEntity entity, int ticks) {
        if (!hanging) {
            timeHanging = 0;
            validHangingPos = false;
            prevHangBlock = null;
        } else {
            Block above = posAbove();
            if ((checkHangingTime -= ticks) < 0 || ThreadLocalRandom.current().nextFloat() < 0.1F || !above.equals(prevHangBlock)) {
                validHangingPos = canHangFrom(above);
                checkHangingTime = 5 + ThreadLocalRandom.current().nextInt(5);
                prevHangBlock = above;
            }
            if (validHangingPos) {
                entity.setGravity(false);
                entity.setVelocity(new Vector());
            } else {
                hanging = false;
                flying = true;
            }
            timeHanging += ticks;
        }
        if (flying) {
            if (timeFlying % 10 < ticks) {
                sound("vesper_flap", 1.0F);
            }
            timeFlying += ticks;
            entity.setGravity(false);
            if (groundedFor > 0) {
                flying = false;
            }
        } else {
            timeFlying = 0;
            if (!hanging) {
                entity.setGravity(true);
            }
        }
        if (target != null && attackable(target)) {
            if (target.getEntityId() != lastTargetId) {
                lastTargetId = target.getEntityId();
                sound("vesper_scream", 3.0F);
            }
        } else {
            lastTargetId = -1;
        }
    }

    private static boolean attackable(LivingEntity who) {
        return ForlornMobs.attackable(who);
    }

    /** Mod {@code posAbove}: the block just over its head. */
    Block posAbove() {
        Location at = self.getLocation();
        return self.getWorld().getBlockAt(at.getBlockX(), (int) Math.floor(self.getBoundingBox().getMaxY() + 0.1F), at.getBlockZ());
    }

    /** Mod {@code canHangFrom}: a block with a solid underside and two free blocks under it. */
    boolean canHangFrom(Block block) {
        BlockData data = block.getBlockData();
        return data.isFaceSturdy(BlockFace.DOWN, org.bukkit.block.BlockSupport.FULL)
            && ForlornMobs.air(block.getWorld(), block.getX(), block.getY() - 1, block.getZ())
            && ForlornMobs.air(block.getWorld(), block.getX(), block.getY() - 2, block.getZ());
    }

    @Override
    int experience() {
        return 5;
    }

    @Override
    public void close(LivingEntity entity, ModMobContext ctx) {
        goals.reset();
    }

    // --- bite ----------------------------------------------------------------------------------------------

    /** Tick 8 of the bite animation. */
    private void bite(LivingEntity entity) {
        LivingEntity prey = target;
        if (prey == null || !attackable(prey) || !entity.hasLineOfSight(prey)) {
            return;
        }
        boolean blocking = prey instanceof Player player && player.isBlocking();
        double damage = ForlornMobs.damageOf(ForlornMobs.VESPER);
        prey.damage(damage, entity);
        if (prey.isDead() && context.mobs().behaviourOf(prey).orElse(null) instanceof Gloomoth gloomoth && gloomoth.hasLight()
            && ForlornMobs.distanceSquared(prey.getLocation(), gloomoth.lightLocation()) < 32.0) {
            groundedFor = 100 + ThreadLocalRandom.current().nextInt(40);
            flying = false;
        }
        maxOrbitTime = 60 + ThreadLocalRandom.current().nextInt(80);
        startOrbitFrom = prey.getEyeLocation().toVector();
        orbitTime = 0;
        if (blocking) {
            if (prey instanceof Player player) {
                damageShield(player, damage);
            }
            groundedFor = 60 + ThreadLocalRandom.current().nextInt(40);
            flying = false;
            startOrbitFrom = null;
        }
    }

    /** Mod {@code damageShieldFor}: a hit of 3 or more wears the shield by 1 plus the damage. */
    private static void damageShield(Player holder, double damage) {
        if (damage < 3.0) {
            return;
        }
        ItemStack shield = holder.getActiveItem();
        if (shield != null && shield.getType() == org.bukkit.Material.SHIELD) {
            shield.damage(1 + (int) Math.floor(damage), holder);
            if (shield.getAmount() <= 0 || shield.getType().isAir()) {
                holder.getWorld().playSound(holder.getLocation(), org.bukkit.Sound.ITEM_SHIELD_BREAK, 0.8F,
                    0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F);
            }
        }
    }

    // --- targeting -----------------------------------------------------------------------------------------

    /** Mod {@code VesperTargetUnderneathEntities}: players and gloomoths below it (from a ceiling: in a column under it). */
    private void scanUnderneath() {
        if (target != null || ThreadLocalRandom.current().nextInt(5) != 0) {
            return;
        }
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double follow = ForlornMobs.attr(self, org.bukkit.attribute.Attribute.FOLLOW_RANGE, 52.0);
        for (Entity near : searchBox()) {
            boolean player = near instanceof Player;
            boolean moth = near instanceof LivingEntity living && context.mobs().behaviourOf(living).orElse(null) instanceof Gloomoth;
            if (!(player || moth) || !ForlornMobs.attackable(near)) {
                continue;
            }
            double distance = ForlornMobs.distanceSquared(self.getLocation(), near.getLocation());
            double range = hanging ? follow : player ? 20.0 : 32.0;
            if (distance > range * range || distance >= bestDistance || !self.hasLineOfSight(near)) {
                continue;
            }
            best = (LivingEntity) near;
            bestDistance = distance;
        }
        if (best != null && (hanging || canReach(best))) {
            target = best;
            unseen = 0;
        }
    }

    private Iterable<Entity> searchBox() {
        var box = self.getBoundingBox();
        World world = self.getWorld();
        if (hanging) {
            return world.getNearbyEntities(new org.bukkit.util.BoundingBox(box.getMinX() - 2.0, world.getMinHeight() - 5,
                box.getMinZ() - 2.0, box.getMaxX() + 2.0, box.getMaxY() + 1.0, box.getMaxZ() + 2.0));
        }
        return world.getNearbyEntities(box.clone().expand(32.0));
    }

    // --- goals ---------------------------------------------------------------------------------------------

    /** Mod {@code VesperAttackGoal}: dive at the target, bite, then circle above it. */
    private final class Attack implements Goals.Goal {
        @Override
        public boolean canUse() {
            if (!self.isInsideVehicle()) {
                scanUnderneath();
            }
            return target != null && ForlornMobs.attackable(target) && !self.isInsideVehicle();
        }

        @Override
        public boolean canContinue() {
            return keepTarget(ForlornMobs.attr(self, org.bukkit.attribute.Attribute.FOLLOW_RANGE, 52.0), true, 2)
                && !self.isInsideVehicle();
        }

        @Override
        public void start() {
            orbitTime = 0;
            maxOrbitTime = 80;
            startOrbitFrom = null;
        }

        @Override
        public void tick() {
            if (groundedFor <= 0) {
                if (hanging) {
                    hanging = false;
                    flying = true;
                } else if (!flying) {
                    flying = true;
                }
            } else {
                flying = false;
            }
            double speed = flying ? 2.5 : 1.0;
            if (startOrbitFrom == null) {
                goTo(target, speed);
            } else if (orbitTime < maxOrbitTime && groundedFor <= 0) {
                orbitTime += 2;
                double[] offset = ForlornRules.vesperOrbit(orbitTime, maxOrbitTime);
                Location orbit = new Location(self.getWorld(), startOrbitFrom.getX() + offset[0],
                    startOrbitFrom.getY() + offset[1], startOrbitFrom.getZ() + offset[2]);
                goTo(orbit, speed);
            } else {
                orbitTime = 0;
                startOrbitFrom = null;
            }
            double reach = self.getWidth() + target.getWidth() + 0.5;
            if (ForlornMobs.distance(self, target) < reach) {
                tryAnimation(null);
            }
        }

        @Override
        public void stop() {
            stopNav();
        }
    }

    /** Mod's {@code RandomStrollGoal(1.0, 15)} that only runs on the ground. */
    private final class Stroll implements Goals.Goal {
        @Override
        public boolean canUse() {
            if (flying || hanging || self.isInsideVehicle() || ThreadLocalRandom.current().nextInt(8) != 0) {
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
            return !flying && !hanging && !navDone();
        }

        @Override
        public void tick() {
        }

        @Override
        public void stop() {
            stopNav();
        }
    }

    /** Mod {@code VesperFlyAndHangGoal}: flits about the cave and, after 15 seconds in the air, looks for a ceiling. */
    private final class FlyAndHang implements Goals.Goal {
        private boolean wantsToHang;
        private Location goal;
        private int hangCheckIn;

        @Override
        public boolean canUse() {
            if (self.isInsideVehicle() || target != null && ForlornMobs.attackable(target) || hanging || groundedFor > 0) {
                return false;
            }
            if (!flying && ThreadLocalRandom.current().nextInt(35) != 0) {
                return false;
            }
            wantsToHang = timeFlying > 300;
            Location pick = wantsToHang ? findHangFromPos() : null;
            if (pick == null) {
                pick = findFlightPos();
            }
            if (pick == null) {
                return false;
            }
            goal = pick;
            return true;
        }

        @Override
        public boolean canContinue() {
            if (target != null && ForlornMobs.attackable(target) || self.isInsideVehicle()) {
                return false;
            }
            return wantsToHang ? !navDone() && !hanging && groundedFor <= 0 : flying && !navDone() && groundedFor <= 0;
        }

        @Override
        public void start() {
            flying = true;
            hanging = false;
            hangCheckIn = 0;
            goTo(goal, 1.0);
        }

        @Override
        public void tick() {
            if (wantsToHang && (hangCheckIn -= 2) < 0) {
                hangCheckIn = 5 + ThreadLocalRandom.current().nextInt(5);
                if (!hanging && canHangFrom(posAbove())) {
                    hanging = true;
                    flying = false;
                }
            }
            if (flying && self.isOnGround() && timeFlying > 40) {
                flying = false;
            }
            goTo(goal, 1.0);
        }

        @Override
        public void stop() {
            if (wantsToHang) {
                stopNav();
            }
            wantsToHang = false;
        }

        /** Mod findFlightPos with a range of 13. */
        private Location findFlightPos() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int range = 13;
            Location here = self.getLocation();
            World world = here.getWorld();
            Location spot = here.clone().add(random.nextInt(range * 2) - range, 0, random.nextInt(range * 2) - range);
            if (!world.isChunkLoaded(spot.getBlockX() >> 4, spot.getBlockZ() >> 4)) {
                return null;
            }
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

        /** Mod findHangFromPos: of 15 spots within 7 blocks, the last whose ceiling it could hang from in plain sight. */
        private Location findHangFromPos() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location best = null;
            World world = self.getWorld();
            int range = 14;
            for (int i = 0; i < 15; i++) {
                int x = self.getLocation().getBlockX() + random.nextInt(range) - range / 2;
                int z = self.getLocation().getBlockZ() + random.nextInt(range) - range / 2;
                int y = self.getLocation().getBlockY();
                if (!ForlornMobs.air(world, x, y, z)) {
                    continue;
                }
                while (y < world.getMaxHeight() - 1 && ForlornMobs.air(world, x, y, z)) {
                    y++;
                }
                Block ceiling = ForlornMobs.blockAt(world, x, y, z);
                if (ceiling != null && y > self.getLocation().getY() - 1.0 && canHangFrom(ceiling) && lineOfSightTo(ceiling)) {
                    best = ceiling.getLocation().add(0.5, 0.5, 0.5);
                }
            }
            return best;
        }

        private boolean lineOfSightTo(Block block) {
            Location eye = self.getEyeLocation();
            Vector to = block.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector());
            double length = to.length();
            if (length < 1.0E-3) {
                return true;
            }
            RayTraceResult hit = self.getWorld().rayTraceBlocks(eye, to.normalize(), length, FluidCollisionMode.NEVER, true);
            return hit == null || hit.getHitBlock() == null || hit.getHitBlock().equals(block);
        }
    }
}
