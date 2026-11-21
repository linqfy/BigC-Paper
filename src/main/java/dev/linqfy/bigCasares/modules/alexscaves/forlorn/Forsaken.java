package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockStates;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.forlorn.ForsakenRules.Anim;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Golem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Ravager;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mod {@code ForsakenEntity}: the boss of the Forlorn Hollows, a 250-health, 3 by 3.5 block horror that an Underzealot
 * pack makes out of a sacrificed Vesper. It hunts players (and every mob that is not of its cult), runs when it closes in,
 * leaps to reach what it cannot walk to, and fights with a bite, two slashes, a ground smash, a pickup that squeezes its
 * prey and two sonic attacks (a single scream, and a wave that hurts everything within 14 blocks). Below half health it
 * heals in the dark. Stuck in a wall it breaks the blocks around it. The mod gives it no boss bar, and neither does this.
 *
 * <p>It rides a vanilla ravager (3 by 3.5 with the {@code scale} attribute) whose own goals are removed. The mod's
 * animations are procedural; here the model only has its generic attack animation, played with every attack.
 */
final class Forsaken extends ForlornMobs.Base {
    private static final java.util.Set<String> IGNORED = ForsakenRules.IGNORED_MOBS;

    private Anim anim;
    private int atick;
    private boolean running;
    private boolean appliedRunning;
    private boolean leaping;
    private int leapTicks;
    private boolean sonicCharge;
    private LivingEntity held;
    private int darkness;
    private int healTimer;
    private int destroyBlocksTick = 10;
    private double stepDistance;
    private Location lastSpot;
    private int despawnIn = 100;
    /** Which target goal set the target: the player goal loses sight of it after 3 seconds, the mob goal gives way to it. */
    private enum Source { HURT, PLAYER, MOB }

    private Source source = Source.HURT;
    /** Path checks to the mobs it may hunt, trusted for 1.5 seconds each. */
    private final Map<UUID, long[]> reach = new HashMap<>();
    private int step = 2;
    private final Goals goals;

    Forsaken(LivingEntity self) {
        super(self);
        if (self instanceof Mob mob) {
            Bukkit.getMobGoals().removeAllGoals(mob);
            Bukkit.getMobGoals().removeAllGoals(mob, GoalType.TARGET);
            mob.setAware(true);
            // Mod removeWhenFarAway: only beyond 128 blocks (checked in tick), never the vanilla 32 block random despawn.
            mob.setRemoveWhenFarAway(false);
        }
        // The module spawns its mobs unsaved; the mod's boss is saved with its chunk like any mob.
        self.setPersistent(true);
        if (self instanceof Raider raider) {
            // Mod: no raids (a ravager would join a raid near it).
            raider.setCanJoinRaid(false);
            raider.setPatrolLeader(false);
        }
        setAttribute(Attribute.MOVEMENT_SPEED, ForsakenRules.WALK_SPEED);
        setAttribute(Attribute.STEP_HEIGHT, ForsakenRules.WALK_STEP);
        goals = new Goals(List.of(new Attack(), new RandomlyJump(), new Stroll()));
    }

    private void setAttribute(Attribute attribute, double value) {
        AttributeInstance instance = self.getAttribute(attribute);
        if (instance != null && instance.getBaseValue() != value) {
            instance.setBaseValue(value);
        }
    }

    /**
     * The vanilla ravager under it rewrites its own speed every tick (towards 0.3, or 0 when stunned) and has a shield stun
     * that ends in a roar; the mod has neither. Its speed is put back and the stun, roar and attack timers cleared on
     * every behaviour tick (a stun lasts 2 ticks at most, and without reaching its end there is no roar).
     */
    private void overrideRavager() {
        setAttribute(Attribute.MOVEMENT_SPEED, running ? ForsakenRules.RUN_SPEED : ForsakenRules.WALK_SPEED);
        if (self instanceof Ravager ravager) {
            if (ravager.getStunnedTicks() > 0) {
                ravager.setStunnedTicks(0);
            }
            if (ravager.getRoarTicks() > 0) {
                ravager.setRoarTicks(0);
            }
            if (ravager.getAttackTicks() > 0) {
                ravager.setAttackTicks(0);
            }
        }
    }

    /** The ritual's end: it rises with the summon animation (the sound is the Vesper's). */
    void summoned() {
        startAnimation(Anim.SUMMON);
    }

    @Override
    public float soundVolume() {
        // Mod getSoundVolume.
        return 2.5F;
    }

    @Override
    boolean ignoresDamageFrom(LivingEntity attacker) {
        return context != null && context.mobs().typeOf(attacker).filter(ForlornMobs.FORSAKEN::equals).isPresent();
    }

    @Override
    void gotHurtBy(LivingEntity attacker) {
        super.gotHurtBy(attacker);
        source = Source.HURT;
    }

    @Override
    public void onDamaged(LivingEntity entity, ModMobContext ctx, EntityDamageEvent event) {
        // Mod checkFallDamage: empty.
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        // Mod hurt: a warden's sonic boom does not hurt, it charges its own sonic attack.
        if (event.getDamageSource().getDamageType() == DamageType.SONIC_BOOM) {
            sonicCharge = true;
            event.setCancelled(true);
            return;
        }
        // Mod: anything from an AbstractGolem (iron golem, snow golem, shulker), also by its projectiles.
        if (ForlornMobs.damagerOf(event) instanceof Golem) {
            event.setDamage(ForsakenRules.golemDamage((float) event.getDamage()));
        }
        super.onDamaged(entity, ctx, event);
    }

    @Override
    public void close(LivingEntity entity, ModMobContext ctx) {
        goals.reset();
        held = null;
        anim = null;
    }

    @Override
    public void tick(LivingEntity entity, ModMobContext ctx, int ticks) {
        context = ctx;
        age += ticks;
        step = ticks;
        overrideRavager();
        if ((despawnIn -= ticks) <= 0) {
            despawnIn = 100;
            if (farFromEveryPlayer(entity)) {
                entity.remove();
                return;
            }
        }
        findTarget(entity, ticks);
        goals.tick();
        entityTick(entity, ticks);
        // Mod getters: the leap pose, and the run pose while it runs (only moving).
        if (leaping) {
            animator.update(entity, ctx, ticks, "leap");
        } else {
            animator.updateWhenMoving(entity, ctx, ticks, running ? "run" : null);
        }
        if (anim != null && (atick += ticks) >= anim.duration) {
            anim = null;
            atick = 0;
        }
    }

    /** Mod {@code removeWhenFarAway(dist > 16384)}: gone once the nearest player is over 128 blocks away. */
    private static boolean farFromEveryPlayer(LivingEntity entity) {
        double nearest = Double.MAX_VALUE;
        for (Player player : entity.getWorld().getPlayers()) {
            nearest = Math.min(nearest, ForlornMobs.distanceSquared(entity.getLocation(), player.getLocation()));
        }
        return nearest != Double.MAX_VALUE && nearest > 16384.0;
    }

    // --- targets -------------------------------------------------------------------------------------------

    private boolean ignored(Entity entity) {
        return entity.getType() == EntityType.BAT
            || context.mobs().typeOf(entity).filter(type -> IGNORED.contains(type.id())).isPresent();
    }

    /**
     * Mod target goals: whoever hurt it, then the nearest player it sees (a tenth of the ticks), then, a hundred and
     * twentieth of the ticks, the nearest mob it can walk to that is not of the cult.
     */
    private void findTarget(LivingEntity entity, int ticks) {
        if (target != null && !keepTarget(ForsakenRules.FOLLOW_RANGE, source == Source.PLAYER, ticks)) {
            target = null;
        }
        // Mod: the player goal (priority 2) pre-empts the mob goal (3); whoever hurt it (1) is kept.
        if (target != null && source != Source.MOB) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < ForsakenRules.perSpan(0.1, ticks)) {
            Player player = nearestVisiblePlayer(entity);
            if (player != null) {
                target = player;
                source = Source.PLAYER;
                unseen = 0;
                return;
            }
        }
        if (target != null) {
            return;
        }
        if (random.nextDouble() < ForsakenRules.perSpan(1.0 / 120.0, ticks)) {
            List<Mob> candidates = new ArrayList<>();
            for (Entity near : entity.getNearbyEntities(ForsakenRules.FOLLOW_RANGE, 4.0, ForsakenRules.FOLLOW_RANGE)) {
                if (near instanceof Mob mob && ForlornMobs.attackable(mob) && !ignored(mob)
                    && ForlornMobs.distanceSquared(here(), mob.getLocation()) <= ForsakenRules.FOLLOW_RANGE * ForsakenRules.FOLLOW_RANGE) {
                    candidates.add(mob);
                }
            }
            candidates.sort(Comparator.comparingDouble(mob -> ForlornMobs.distanceSquared(here(), mob.getLocation())));
            for (int i = 0; i < Math.min(candidates.size(), 4); i++) {
                if (reachable(candidates.get(i))) {
                    target = candidates.get(i);
                    source = Source.MOB;
                    unseen = 0;
                    return;
                }
            }
        }
    }

    /** Mod {@code NearestAttackableTargetGoal} over players: the nearest within 64 blocks that it sees (no entity scan). */
    private Player nearestVisiblePlayer(LivingEntity entity) {
        Player best = null;
        double bestDistance = ForsakenRules.FOLLOW_RANGE * ForsakenRules.FOLLOW_RANGE;
        for (Player player : entity.getWorld().getPlayers()) {
            double distance = ForlornMobs.distanceSquared(here(), player.getLocation());
            if (distance <= bestDistance && ForlornMobs.attackable(player) && entity.hasLineOfSight(player)) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Mod {@code canReach} for the mob goal: a path ending within 1.5 blocks, checked once every 1.5 seconds per mob. */
    private boolean reachable(Mob who) {
        long[] known = reach.get(who.getUniqueId());
        if (known != null && age - known[0] < ForlornMobs.Base.REACH_CACHE_TICKS) {
            return known[1] == 1;
        }
        if (reach.size() > 32) {
            reach.clear();
        }
        boolean result = false;
        if (self instanceof Mob me) {
            var path = me.getPathfinder().findPath(who);
            if (path != null && path.getFinalPoint() != null) {
                Location end = path.getFinalPoint();
                double dx = end.getBlockX() - who.getLocation().getBlockX();
                double dy = end.getBlockY() - who.getLocation().getBlockY();
                double dz = end.getBlockZ() - who.getLocation().getBlockZ();
                result = dx * dx + dy * dy + dz * dz <= 2.25;
            }
        }
        reach.put(who.getUniqueId(), new long[] {age, result ? 1 : 0});
        return result;
    }

    // --- animation -----------------------------------------------------------------------------------------

    /** Mod {@code tryAnimation}: starts it only when none runs. */
    private boolean startAnimation(Anim next) {
        if (anim != null) {
            return false;
        }
        anim = next;
        atick = 0;
        if (context != null) {
            // Every Anim has its own animation in the model (summon, bite, left_slash, sonic_blast...).
            animator.action(self, context, next.name().toLowerCase(java.util.Locale.ROOT));
        }
        return true;
    }

    private boolean in(Anim which) {
        return anim == which;
    }

    // --- the entity's own tick -----------------------------------------------------------------------------

    private void entityTick(LivingEntity entity, int ticks) {
        if (leaping) {
            leapTicks += ticks;
            if (entity.isOnGround() && leapTicks >= 5) {
                leaping = false;
            }
        } else {
            leapTicks = 0;
            if (in(Anim.PREPARE_JUMP) && entity.isOnGround() && ForsakenRules.ticksIn(atick, ticks, 8, 10) > 0) {
                leaping = true;
                sound("forsaken_leap", 2.5F);
            }
        }
        if (running != appliedRunning) {
            appliedRunning = running;
            setAttribute(Attribute.MOVEMENT_SPEED, running ? ForsakenRules.RUN_SPEED : ForsakenRules.WALK_SPEED);
            setAttribute(Attribute.STEP_HEIGHT, running ? ForsakenRules.RUN_STEP : ForsakenRules.WALK_STEP);
        }
        tickHeld(entity);
        tickDarkness(entity, ticks);
        tickWall(entity, ticks);
        tickEffects(entity);
    }

    /** Mod: the mob it grabs is held while the pickup plays and carried up to its hand. */
    private void tickHeld(LivingEntity entity) {
        LivingEntity prey = target;
        boolean pickup = anim != null && anim.pickup();
        if (prey != null && prey.isValid() && ForlornMobs.distance(prey, entity) < 10.0 && entity.hasLineOfSight(prey) && pickup) {
            if (held == null) {
                sound("forsaken_grab", 2.5F);
            }
            held = prey;
        } else if (held != null) {
            held = null;
        }
        LivingEntity grabbed = held;
        if (grabbed != null && grabbed.isValid() && ForlornMobs.distance(grabbed, entity) < 10.0) {
            grabbed.setFallDistance(0.0F);
            if (pickup && atick >= 10 && atick <= 38) {
                double[] hand = ForsakenRules.handPos(atick, in(Anim.LEFT_PICKUP));
                double yaw = Math.toRadians(entity.getLocation().getYaw());
                double cos = Math.cos(yaw);
                double sin = Math.sin(yaw);
                Location at = entity.getLocation();
                double gx = at.getX() + hand[0] * cos - hand[2] * sin;
                double gy = at.getY() + hand[1];
                double gz = at.getZ() + hand[2] * cos + hand[0] * sin;
                Location to = grabbed.getLocation();
                grabbed.setVelocity(new Vector(gx - to.getX(), gy - to.getY(), gz - to.getZ()).multiply(0.33));
            }
        }
    }

    /** Mod: below half health it heals 1 every 30 ticks in light of 4 or less, and for a second and a half after. */
    private void tickDarkness(LivingEntity entity, int ticks) {
        double max = ForlornMobs.attr(entity, Attribute.MAX_HEALTH, ForsakenRules.MAX_HEALTH);
        boolean below = entity.getHealth() < max * 0.5F;
        Block above = entity.getLocation().getBlock().getRelative(0, 1, 0);
        int light = below ? ForsakenRules.localBrightness(above.getLightFromBlocks(), above.getLightFromSky(),
            skyDarken(entity.getWorld())) : 15;
        darkness = ForsakenRules.darknessAfter(darkness, below, light, ticks);
        if (darkness > 0) {
            if ((healTimer += ticks) >= 30) {
                healTimer = 0;
                double health = Math.min(max, entity.getHealth() + 1.0);
                if (health > entity.getHealth()) {
                    EntityRegainHealthEvent event = new EntityRegainHealthEvent(entity, 1.0, EntityRegainHealthEvent.RegainReason.CUSTOM);
                    if (event.callEvent()) {
                        entity.setHealth(health);
                    }
                }
            }
            entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation().add(0, entity.getHeight() * 0.5, 0), 3,
                entity.getWidth() * 0.4, entity.getHeight() * 0.4, entity.getWidth() * 0.4, 0.01);
        } else {
            healTimer = 0;
        }
    }

    /** Mod {@code getSkyDarken}: how much the sky light is dimmed by the hour and the weather (none in worlds without sky). */
    private static int skyDarken(World world) {
        return world.getEnvironment() == World.Environment.NORMAL
            ? ForsakenRules.skyDarken(world.getTime(), world.hasStorm(), world.isThundering()) : 0;
    }

    /** Mod {@code customServerAiStep}: stuck in a wall, every 20 ticks it breaks the 3 by 3 by 4 blocks around it. */
    private void tickWall(LivingEntity entity, int ticks) {
        Location eye = entity.getEyeLocation();
        if (destroyBlocksTick <= 0 || !ForlornMobs.solid(entity.getWorld(), eye.getBlockX(), eye.getBlockY(), eye.getBlockZ())) {
            return;
        }
        destroyBlocksTick = Math.max(0, destroyBlocksTick - ticks);
        if (destroyBlocksTick > 0 || Boolean.FALSE.equals(entity.getWorld().getGameRuleValue(GameRules.MOB_GRIEFING))) {
            return;
        }
        Location at = entity.getLocation();
        boolean broke = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    Block block = ForlornMobs.blockAt(entity.getWorld(), at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz);
                    if (block != null && breakable(block)
                        && new EntityChangeBlockEvent(entity, block, Material.AIR.createBlockData()).callEvent()) {
                        block.breakNaturally();
                        broke = true;
                    }
                }
            }
        }
        if (broke) {
            entity.getWorld().playSound(at, org.bukkit.Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0F, 1.0F);
        }
        destroyBlocksTick = 20;
    }

    /** Mod {@code canEntityDestroy} and not {@code unmoveable}: whatever is not unbreakable or wither-proof (the module's note blocks are left). */
    static boolean breakable(Block block) {
        Material type = block.getType();
        return !type.isAir() && !block.isLiquid() && type != Material.NOTE_BLOCK && !ModBlockStates.isModuleShaped(block)
            && type.getHardness() >= 0
            && !Tag.WITHER_IMMUNE.isTagged(type);
    }

    /** Footsteps (the base is silent), and the particles the mod's client draws for its sonic attacks and ground smash. */
    private void tickEffects(LivingEntity entity) {
        Location here = entity.getLocation();
        if (lastSpot != null && lastSpot.getWorld() == here.getWorld() && entity.isOnGround()) {
            stepDistance += Math.hypot(here.getX() - lastSpot.getX(), here.getZ() - lastSpot.getZ());
            if (stepDistance > 1.5) {
                stepDistance = 0;
                entity.getWorld().playSound(here, "alexscaves:forsaken_step", 1.0F, 1.0F);
            }
        }
        lastSpot = here;
        World world = entity.getWorld();
        LivingEntity prey = target;
        if (in(Anim.SONIC_ATTACK) && prey != null && ForsakenRules.multiplesIn(atick, step, 4, 11, 29) > 0) {
            Location from = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
            Vector line = prey.getEyeLocation().toVector().subtract(from.toVector());
            double length = Math.min(line.length(), 20.0);
            if (length > 0.1) {
                line.normalize();
                for (double d = 2.0; d < length; d += 3.0) {
                    world.spawnParticle(Particle.SONIC_BOOM, from.clone().add(line.clone().multiply(d)), 1, 0, 0, 0, 0);
                }
            }
        }
        if (in(Anim.SONIC_BLAST) && ForsakenRules.multiplesIn(atick, step, 4, 11, 29) > 0) {
            Location from = entity.getLocation().add(0, 1.0, 0);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0;
                world.spawnParticle(Particle.SONIC_BOOM, from.clone().add(Math.cos(angle) * 5.0, 0, Math.sin(angle) * 5.0), 1, 0, 0, 0, 0);
            }
        }
        if (in(Anim.GROUND_SMASH) && ForsakenRules.ticksIn(atick, step, 12, 12) > 0) {
            Location smash = smashPos(entity);
            Block ground = ForlornMobs.ground(smash).getBlock().getRelative(0, -1, 0);
            if (!ground.getType().isAir()) {
                world.spawnParticle(Particle.BLOCK, smash.clone().add(0, 1.2, 0), 30, 1.4, 0.3, 1.4, 0.1, ground.getBlockData());
            }
        }
    }

    /** Mod: 3.5 blocks ahead of it (the smash is under its fists). */
    private static Location smashPos(LivingEntity entity) {
        Location at = entity.getLocation();
        double yaw = Math.toRadians(at.getYaw());
        return at.clone().add(-Math.sin(yaw) * 3.5, 0, Math.cos(yaw) * 3.5);
    }

    // --- shared by the attack goals ------------------------------------------------------------------------

    private void face(Location point) {
        Location look = self.getLocation().setDirection(point.toVector().subtract(self.getEyeLocation().toVector()));
        self.setRotation(look.getYaw(), look.getPitch());
    }

    /**
     * Mod jump: horizontally {@code 0.155} of the way to the spot plus a fifth of the current motion, and up with a push
     * that grows with the distance.
     */
    private void leapTowards(Location spot) {
        Vector velocity = self.getVelocity();
        Vector push = new Vector(spot.getBlockX() + 0.5 - self.getLocation().getX(), 0.0, spot.getBlockZ() + 0.5 - self.getLocation().getZ());
        if (push.length() > 100.0) {
            push = velocity.clone().normalize().multiply(100.0);
        }
        if (push.lengthSquared() > 1.0E-7) {
            push = push.multiply(0.155).add(velocity.clone().multiply(0.2));
        }
        self.setVelocity(new Vector(push.getX(), 0.2 + push.length() * 0.3, push.getZ()));
    }

    /** Mod {@code knockBackAngle}: vanilla knockback along the body direction turned by {@code angle} degrees. */
    private void knockAngle(LivingEntity victim, double strength, float angle) {
        strength *= 1.0 - ForlornMobs.attr(victim, Attribute.KNOCKBACK_RESISTANCE, 0.0);
        if (strength <= 0.0) {
            return;
        }
        double yaw = Math.toRadians(self.getLocation().getYaw() + angle);
        Vector velocity = victim.getVelocity();
        victim.setVelocity(new Vector(velocity.getX() / 2.0 - Math.sin(yaw) * strength,
            victim.isOnGround() ? Math.min(0.4, velocity.getY() / 2.0 + strength) : velocity.getY(),
            velocity.getZ() / 2.0 + Math.cos(yaw) * strength));
    }

    private static double lifeOf(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    private float sonicDamageAgainst(LivingEntity victim) {
        return ForsakenRules.sonicDamage(victim.getType() == EntityType.WARDEN);
    }

    // --- goals ---------------------------------------------------------------------------------------------

    /** Mod {@code ForsakenAttackGoal}. */
    private final class Attack implements Goals.Goal {
        private Location jumpTarget;
        private boolean jumpEnqueued;
        private boolean sonicEnqueued;
        private int navigationCheckCooldown;
        private int sonicDelay;

        @Override
        public boolean canUse() {
            return target != null && ForlornMobs.attackable(target);
        }

        @Override
        public void start() {
            navigationCheckCooldown = 0;
            jumpEnqueued = false;
            sonicEnqueued = ThreadLocalRandom.current().nextBoolean();
            sonicDelay = 0;
        }

        @Override
        public void stop() {
            jumpEnqueued = false;
            sonicEnqueued = false;
            jumpTarget = null;
            running = false;
            stopNav();
        }

        @Override
        public void tick() {
            LivingEntity prey = target;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double distance = ForlornMobs.distance(self, prey);
            double attackDistance = self.getWidth() + prey.getWidth();
            boolean inPursuit = anim == null || !anim.pickup();
            boolean sees = self.hasLineOfSight(prey);
            if (sonicDelay > 0) {
                sonicDelay -= step;
                if (sonicDelay <= 0) {
                    sonicDelay = 0;
                    if (sees) {
                        prey.damage(sonicDamageAgainst(prey), context.damageTypes().source("forsaken_sonic_boom", self));
                        knockAngle(prey, 1.0, 0.0F);
                    }
                }
            }
            if (sonicEnqueued && sees && distance < 200.0) {
                face(prey.getEyeLocation());
                stopNav();
                if (anim == null) {
                    boolean blast = ForsakenRules.sonicBlast(distance, random.nextFloat());
                    startAnimation(blast ? Anim.SONIC_BLAST : Anim.SONIC_ATTACK);
                    sound(blast ? "forsaken_aoe" : "forsaken_screech", 2.5F);
                }
                if (in(Anim.SONIC_ATTACK)) {
                    inPursuit = false;
                    if (ForsakenRules.ticksIn(atick, step, 10, 30) > 0 && sonicDelay <= 0) {
                        sonicDelay = Math.max(1, ForsakenRules.sonicDelay(distance));
                    }
                    if (atick > 30) {
                        sonicEnqueued = false;
                    }
                }
                if (in(Anim.SONIC_BLAST)) {
                    inPursuit = false;
                    for (int i = ForsakenRules.multiplesIn(atick, step, 5, 10, 30); i > 0; i--) {
                        blast(prey);
                    }
                    if (atick > 40) {
                        sonicEnqueued = false;
                    }
                }
            } else if (jumpEnqueued) {
                if (jumpTarget == null) {
                    jumpTarget = findJumpTarget(prey, distance > 20.0);
                } else {
                    inPursuit = false;
                    if (leaping) {
                        leapTowards(jumpTarget);
                        jumpEnqueued = false;
                        jumpTarget = null;
                    } else if (self.isOnGround()) {
                        face(jumpTarget.clone().add(0.5, 0.5, 0.5));
                        startAnimation(Anim.PREPARE_JUMP);
                    }
                }
            }
            if (inPursuit) {
                face(prey.getEyeLocation());
                goTo(prey, 1.0);
                if (distance < attackDistance + 1.0 && anim == null) {
                    Anim chosen = ForsakenRules.melee(random.nextFloat(), prey.getWidth(), random.nextBoolean());
                    startAnimation(chosen);
                    if (chosen == Anim.BITE) {
                        self.getWorld().playSound(self.getLocation(), "alexscaves:forsaken_bite", 1.0F, 1.0F);
                    }
                }
            } else {
                stopNav();
            }
            if ((distance > 20.0 && random.nextDouble() < ForsakenRules.perSpan(0.01, step) || sonicCharge) && !sonicEnqueued) {
                sonicEnqueued = true;
                sonicCharge = false;
            }
            if (distance > 30.0 && random.nextDouble() < ForsakenRules.perSpan(0.05, step) && !jumpEnqueued) {
                startCleanJump();
            }
            running = distance < 64.0 && distance > attackDistance && inPursuit;
            melee(prey);
            if ((navigationCheckCooldown -= step) < 0 && self.isOnGround()) {
                navigationCheckCooldown = 20 + random.nextInt(40);
                if (!canReach(prey)) {
                    startCleanJump();
                }
            }
        }

        /** Mod blast: every 5 ticks of the wave, all living things within 14 blocks (but the cult) take 65% of the scream. */
        private void blast(LivingEntity prey) {
            float damage = ForsakenRules.blastDamage(sonicDamageAgainst(prey));
            for (Entity near : self.getNearbyEntities(16.0, 8.0, 16.0)) {
                if (near instanceof LivingEntity living && living != self && !ignored(living)
                    && ForlornMobs.distance(living, self) <= 14.0) {
                    living.damage(damage, context.damageTypes().source("forsaken_sonic_boom", self));
                }
            }
        }

        /** The hits of the melee animations, on the ticks the mod lands them. */
        private void melee(LivingEntity prey) {
            if (anim != null && anim.pickup() && held == prey) {
                for (int i = ForsakenRules.ticksIn(atick, step, 30, anim.duration - 1); i > 0; i--) {
                    hurt(prey, 1.2F, 5.0F);
                }
            }
            if (in(Anim.RIGHT_SLASH) || in(Anim.LEFT_SLASH)) {
                float angle = in(Anim.RIGHT_SLASH) ? -90.0F : 90.0F;
                for (int i = ForsakenRules.ticksIn(atick, step, 15, 18); i > 0; i--) {
                    float knockback = 0.5F;
                    if (hurt(prey, 0.8F, 2.0F)) {
                        knockback = 3.0F;
                        self.getWorld().playSound(self.getLocation(), "alexscaves:forsaken_grab", 1.0F, 1.0F);
                    }
                    knockAngle(prey, knockback, angle);
                }
            }
            if (in(Anim.GROUND_SMASH)) {
                for (int i = ForsakenRules.ticksIn(atick, step, 10, 15); i > 0; i--) {
                    smash();
                }
            }
            if (in(Anim.BITE)) {
                for (int i = ForsakenRules.ticksIn(atick, step, 5, 8); i > 0; i--) {
                    float knockback = 0.0F;
                    if (hurt(prey, 1.0F, 1.0F)) {
                        knockback = 0.5F;
                    }
                    knockAngle(prey, knockback, 0.0F);
                }
            }
        }

        /** Mod ground smash: everything within 4 blocks of the spot under its fists is hurt and thrown up. */
        private void smash() {
            Location at = smashPos(self);
            boolean hit = false;
            BoundingBox box = new BoundingBox(at.getX() - 4.0, at.getY() - 2.0, at.getZ() - 4.0, at.getX() + 4.0, at.getY() + 3.0,
                at.getZ() + 4.0);
            for (Entity near : self.getWorld().getNearbyEntities(box)) {
                if (near instanceof LivingEntity living && living != self && !ignored(living)
                    && living.getLocation().distanceSquared(at) <= 16.0 && hurt(living, 0.8F, 3.0F) && living.isOnGround()) {
                    living.setVelocity(living.getVelocity().add(new Vector(0.0, 0.5, 0.0)));
                    hit = true;
                }
            }
            if (hit) {
                self.getWorld().playSound(self.getLocation(), "alexscaves:forsaken_grab", 1.0F, 1.0F);
            }
        }

        /**
         * Mod {@code checkAndDealDamage}: when it sees the target and is within its reach plus {@code extraRange}, a hit of
         * {@code multiplier} times its damage (half the time it then plans a leap, a fifth of the time a scream).
         *
         * @return whether the target lost health
         */
        private boolean hurt(LivingEntity victim, float multiplier, float extraRange) {
            if (!self.hasLineOfSight(victim)
                || !(ForlornMobs.distance(self, victim) < self.getWidth() + victim.getWidth() + extraRange)) {
                return false;
            }
            double before = lifeOf(victim);
            victim.damage(multiplier * ForlornMobs.attr(self, Attribute.ATTACK_DAMAGE, ForsakenRules.ATTACK_DAMAGE), self);
            boolean hit = lifeOf(victim) < before || victim.isDead();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (random.nextInt(2) == 0) {
                startCleanJump();
            }
            if (!sonicEnqueued && random.nextInt(5) == 0) {
                sonicEnqueued = true;
            }
            return hit;
        }

        private void startCleanJump() {
            jumpTarget = null;
            jumpEnqueued = true;
        }

        /**
         * Mod {@code findJumpTarget}: a standing spot on a ring around the target (4 to 5 blocks out when it is far, 15 to
         * 24 when it is near, reached by a spot within 20 blocks of it that is closest to that ring).
         */
        private Location findJumpTarget(LivingEntity prey, boolean far) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int radius = far ? random.nextInt(2) + 4 : random.nextInt(10) + 15;
            double angle = Math.PI * 2 * random.nextFloat();
            Location ring = prey.getLocation().add(Math.sin(angle) * radius, 0, Math.cos(angle) * radius);
            Location spot = null;
            if (far) {
                spot = standingSpot(ring);
            } else {
                double best = Double.MAX_VALUE;
                for (int i = 0; i < 5; i++) {
                    Location candidate = ForlornMobs.stroll(self.getLocation(), 20, 10, random);
                    if (candidate != null && ForlornMobs.distanceSquared(candidate, ring) < best) {
                        best = ForlornMobs.distanceSquared(candidate, ring);
                        spot = candidate;
                    }
                }
            }
            return spot != null && roomFor(spot) ? spot : null;
        }
    }

    /** The standing spot over the ground at a point (null when the column is not loaded). */
    private static Location standingSpot(Location point) {
        if (!point.getWorld().isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)) {
            return null;
        }
        return ForlornMobs.ground(point);
    }

    /** Mod jump spot test: a full block under it, not liquid, and room for its body there. */
    private boolean roomFor(Location spot) {
        World world = spot.getWorld();
        Block below = ForlornMobs.blockAt(world, spot.getBlockX(), spot.getBlockY() - 1, spot.getBlockZ());
        if (below == null || !below.getBlockData().isFaceSturdy(org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockSupport.FULL)
            || ForlornMobs.blockAt(world, spot.getBlockX(), spot.getBlockY(), spot.getBlockZ()) == null
            || world.getBlockAt(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ()).isLiquid()) {
            return false;
        }
        Location here = self.getLocation();
        BoundingBox moved = self.getBoundingBox().shift(spot.getX() + 0.5 - here.getX(), spot.getY() + 1.0 - here.getY(),
            spot.getZ() + 0.5 - here.getZ());
        return !world.hasCollisionsIn(moved);
    }

    /** Mod {@code ForsakenRandomlyJumpGoal}: with nothing to hunt, now and then a leap to a spot within 25 blocks. */
    private final class RandomlyJump implements Goals.Goal {
        private Location jumpTarget;
        private boolean performed;

        @Override
        public boolean canUse() {
            if (!self.isOnGround() || target != null && ForlornMobs.attackable(target) || anim != null
                || ThreadLocalRandom.current().nextDouble() >= ForsakenRules.perSpan(1.0 / 140.0, step)) {
                return false;
            }
            Location spot = ForlornMobs.stroll(self.getLocation(), 25, 10, ThreadLocalRandom.current());
            if (spot != null && roomFor(spot)) {
                jumpTarget = spot;
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            performed = false;
            stopNav();
            startAnimation(Anim.PREPARE_JUMP);
            face(jumpTarget.clone().add(0.5, 0.5, 0.5));
        }

        @Override
        public boolean canContinue() {
            return (in(Anim.PREPARE_JUMP) || leaping) && jumpTarget != null;
        }

        @Override
        public void tick() {
            if (leaping && !performed) {
                performed = true;
                leapTowards(jumpTarget);
            }
        }

        @Override
        public void stop() {
            jumpTarget = null;
        }
    }

    /** Mod's {@code RandomStrollGoal(1.0, 30)}. */
    private final class Stroll implements Goals.Goal {
        @Override
        public boolean canUse() {
            if (ThreadLocalRandom.current().nextInt(15) != 0) {
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
}
