package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mod {@code WatcherEntity}: a 30-health shade (the Gloomoth's sacrifice) with a follow range of 256 blocks. It runs at
 * its target, hits for 4 and falls back for a moment; when the target cannot be reached on foot it turns into a shade
 * and flies. The first sight of a player (and every 100 ticks away from the last spot) it possesses them: they cannot
 * move for about 8 seconds, are not hurt by anything but the watcher, and are scared at the end.
 *
 * <p>The mod moves the player's camera into the watcher; a server plugin cannot do that without changing their game
 * mode, so the possession here freezes the player and ends with the same scare sound.
 */
final class Watcher extends ForlornMobs.Base {
    /** Players held by a watcher, with the watcher that holds them (read by {@link ForlornListener}). */
    static final Map<UUID, UUID> POSSESSED_PLAYERS = new ConcurrentHashMap<>();
    /** World time of each player's last possession, for the cooldown. */
    private static final Map<UUID, Long> LAST_POSSESSED = new ConcurrentHashMap<>();
    /** Mod config {@code watcher_possession_cooldown}. */
    static final int POSSESSION_COOLDOWN = 300;

    private boolean running;
    private boolean shadeMode;
    private LivingEntity possessed;
    private boolean possessedWasAware;
    private ForlornRules.Possession possession;
    private Location lastPossessionSite;
    private long lastPossessionTimestamp;
    private final Goals goals;

    Watcher(LivingEntity self) {
        super(self);
        goals = new Goals(List.of(new Attack(), new Stroll()));
    }

    /** Whether it may possess the entity now (mod {@code canPossessTargetEntity}: players have a cooldown). */
    boolean canPossess(LivingEntity who) {
        if (who instanceof Player player) {
            long last = LAST_POSSESSED.getOrDefault(player.getUniqueId(), 0L);
            return player.getWorld().getFullTime() - last >= POSSESSION_COOLDOWN;
        }
        return true;
    }

    @Override
    boolean ignoresDamageFrom(LivingEntity attacker) {
        return context != null && context.mobs().typeOf(attacker)
            .filter(type -> type.equals(ForlornMobs.UNDERZEALOT) || type.equals(ForlornMobs.WATCHER)
                || type.equals(ForlornMobs.FORSAKEN)).isPresent();
    }

    @Override
    public void onDamaged(LivingEntity entity, ModMobContext ctx, EntityDamageEvent event) {
        // Mod causeFallDamage: false.
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
        if (shadeMode) {
            entity.setFallDistance(0.0F);
            entity.setGravity(false);
        } else {
            entity.setGravity(true);
        }
        tickPossession(entity, ticks);
        // Mod: the run pose while it runs (only moving); the shade form glides, so it keeps idle.
        animator.updateWhenMoving(entity, ctx, ticks, running && !shadeMode ? "run" : null);
        if (target != null) {
            keepTarget(ForlornMobs.attr(entity, org.bukkit.attribute.Attribute.FOLLOW_RANGE, 256.0), false, ticks);
        }
        if (target == null && ThreadLocalRandom.current().nextInt(5) == 0) {
            findPlayer(entity);
        }
        if (advanceAnimation(ticks, 8)) {
            strike(entity);
        }
        goals.tick();
    }

    /** Mod {@code WatcherAttackGoal}: ANIMATION_ATTACK_0 or _1 at random. */
    @Override
    String attackAnimation() {
        return ThreadLocalRandom.current().nextBoolean() ? "attack_0" : "attack_1";
    }

    /** Mod {@code MobTarget3DGoal} over players, only those it may possess. */
    private void findPlayer(LivingEntity entity) {
        double range = ForlornMobs.attr(entity, org.bukkit.attribute.Attribute.FOLLOW_RANGE, 256.0);
        Player best = null;
        double bestDistance = range * range;
        for (Player player : entity.getWorld().getPlayers()) {
            double distance = ForlornMobs.distanceSquared(entity.getLocation(), player.getLocation());
            if (distance < bestDistance && ForlornMobs.attackable(player) && canPossess(player)) {
                best = player;
                bestDistance = distance;
            }
        }
        if (best != null) {
            target = best;
            unseen = 0;
        }
    }

    // --- possession ----------------------------------------------------------------------------------------

    /** Mod {@code attemptPossession}. */
    private boolean attemptPossession(LivingEntity who) {
        if (age - lastPossessionTimestamp > 100
            && (lastPossessionSite == null || ForlornMobs.distanceSquared(lastPossessionSite, self.getLocation()) > 10.0)
            && canPossess(who)) {
            lastPossessionSite = self.getLocation();
            lastPossessionTimestamp = age;
            if (who instanceof Player player) {
                LAST_POSSESSED.put(player.getUniqueId(), player.getWorld().getFullTime());
                POSSESSED_PLAYERS.put(player.getUniqueId(), self.getUniqueId());
            }
            return true;
        }
        return false;
    }

    private void startPossession(LivingEntity who) {
        possessed = who;
        possession = new ForlornRules.Possession();
        if (who.isInsideVehicle()) {
            // A rider gets no move events, so it could not be held: the watcher pulls it off.
            who.leaveVehicle();
        }
        if (who instanceof Mob mob) {
            possessedWasAware = mob.isAware();
            mob.setAware(false);
        }
    }

    private void tickPossession(LivingEntity entity, int ticks) {
        if (possessed == null) {
            return;
        }
        if (!possessed.isValid() || possessed.isDead() || possessed.getWorld() != entity.getWorld()) {
            endPossession(false);
            return;
        }
        lastPossessionTimestamp = age;
        boolean over = false;
        double distance = ForlornMobs.distance(possessed, entity);
        for (int t = 0; t < ticks && !over; t++) {
            over = possession.step(distance);
        }
        if (over || !entity.isValid()) {
            endPossession(true);
        } else if (!(possessed instanceof Player)) {
            possessed.setVelocity(possessed.getVelocity().setX(0).setZ(0));
        }
    }

    /** The end of a possession: the scare, and the freed player or mob. */
    private void endPossession(boolean scare) {
        LivingEntity who = possessed;
        possessed = null;
        possession = null;
        if (who == null) {
            return;
        }
        if (who instanceof Mob mob) {
            mob.setAware(possessedWasAware);
        }
        if (who instanceof Player player) {
            POSSESSED_PLAYERS.remove(player.getUniqueId());
            LAST_POSSESSED.put(player.getUniqueId(), player.getWorld().getFullTime());
            if (scare) {
                player.playSound(player.getLocation(), "alexscaves:watcher_scare", 1.0F, 1.0F);
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getEyeLocation(), 25, 0.4, 0.4, 0.4, 0.02);
            }
        }
    }

    boolean possesses(UUID who) {
        return possessed != null && possessed.getUniqueId().equals(who);
    }

    // --- attack --------------------------------------------------------------------------------------------

    private void strike(LivingEntity entity) {
        LivingEntity prey = target;
        if (prey == null || !ForlornMobs.attackable(prey) || !entity.hasLineOfSight(prey)) {
            return;
        }
        hit(prey, ForlornMobs.damageOf(ForlornMobs.WATCHER), 0.5);
        Attack attack = attackGoal;
        if (attack != null) {
            attack.afterHit(prey);
        }
    }

    private Attack attackGoal;

    @Override
    public void close(LivingEntity entity, ModMobContext ctx) {
        goals.reset();
        endPossession(false);
    }

    /** Mod {@code WatcherAttackGoal}. */
    private final class Attack implements Goals.Goal {
        private int navigationCheckCooldown;
        private int possessions;
        private boolean canReachViaGround = true;
        private int retreatFor;
        private Location retreatTo;

        Attack() {
            attackGoal = this;
        }

        @Override
        public boolean canUse() {
            return target != null && ForlornMobs.attackable(target);
        }

        @Override
        public void start() {
            navigationCheckCooldown = 0;
            possessions = 0;
            retreatFor = 0;
            retreatTo = null;
        }

        void afterHit(LivingEntity prey) {
            retreatFor = 30 + ThreadLocalRandom.current().nextInt(30);
            for (int i = 0; i < 15; i++) {
                Location away = ForlornMobs.awayFrom(self.getLocation(), 30, 15, prey.getLocation(), ThreadLocalRandom.current());
                if (away != null) {
                    retreatTo = away;
                    break;
                }
            }
        }

        @Override
        public void tick() {
            LivingEntity prey = target;
            if ((navigationCheckCooldown -= 2) < 0) {
                canReachViaGround = canReach(prey);
                navigationCheckCooldown = 10 + ThreadLocalRandom.current().nextInt(40);
            }
            double distance = ForlornMobs.distance(self, prey);
            Location look = self.getLocation().setDirection(prey.getEyeLocation().toVector().subtract(self.getEyeLocation().toVector()));
            self.setRotation(look.getYaw(), look.getPitch());
            if (retreatFor > 0) {
                retreatFor -= 2;
                if (retreatTo == null) {
                    retreatFor = 0;
                } else {
                    Vector away = retreatTo.toVector().subtract(self.getLocation().toVector());
                    if (away.length() > 1.0) {
                        self.setVelocity(self.getVelocity().add(away.normalize().multiply(0.3)));
                        shadeMode = true;
                    } else {
                        retreatTo = null;
                    }
                }
            } else {
                shadeMode = !canReachViaGround;
                if (distance < 6.0 && self.hasLineOfSight(prey) && self.isOnGround()) {
                    shadeMode = false;
                }
                double reach = self.getWidth() + prey.getWidth() + 0.5;
                if (distance > reach) {
                    double speed = (running ? 1.3 : 1.0) + Math.min(Math.log(possessions + 1), 1.0) * 0.6;
                    if (shadeMode) {
                        fly(prey.getLocation(), speed);
                    } else {
                        goTo(prey, speed);
                    }
                } else {
                    if (self.hasLineOfSight(prey) && animTick < 0) {
                        tryAnimation("watcher_attack");
                    }
                    stopNav();
                }
            }
            if (possessions > 2 || distance < 20.0) {
                running = true;
            }
            if (attemptPossession(prey)) {
                if (possessed == null) {
                    possessions++;
                }
                startPossession(prey);
            }
        }

        /** Mod's shade {@code MoveController}: floats towards the spot, climbing and falling with it. */
        private void fly(Location to, double speed) {
            Vector toward = to.toVector().subtract(self.getLocation().toVector());
            double length = toward.length();
            if (length < self.getWidth() * 0.8) {
                return;
            }
            Vector push = toward.multiply(1.0 / length);
            Vector desired = new Vector(push.getX() * 0.28 * speed, Math.max(-0.35, Math.min(0.35, push.getY() * 0.5)),
                push.getZ() * 0.28 * speed);
            Vector velocity = self.getVelocity().multiply(0.6).add(desired.multiply(0.4));
            self.setVelocity(velocity);
            float yaw = (float) Math.toDegrees(-Math.atan2(push.getX(), push.getZ()));
            self.setRotation(yaw, 0);
        }

        @Override
        public void stop() {
            shadeMode = false;
            running = false;
            retreatFor = 0;
            retreatTo = null;
            stopNav();
        }
    }

    /** Mod's {@code RandomStrollGoal(1.0, 100)}. */
    private final class Stroll implements Goals.Goal {
        @Override
        public boolean canUse() {
            if (ThreadLocalRandom.current().nextInt(50) != 0) {
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
