package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModVoronoi;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.SauropodRules.Anim;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.SauropodRules.Attack;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Luxtructosaurus (mod {@code LuxtructosaurusEntity}, {@code LuxtructosaurusMeleeGoal} and
 * {@code SauropodBaseEntity}): the Primordial Caves boss, a 600-health sauropod that a volcanic core summons when an
 * ominous catalyst falls into it. It stomps (cracking fissures through the ground when enraged), kicks, whips its tail,
 * spews flames and tephra, roars to enrage itself and scare other mobs, and leaps when it cannot reach its target.
 * While it erupts the volcano's magma lights up; when it dies, after a 120-tick animation, it throws its tectonic
 * shards, and the world remembers it was defeated (Atlatitans and amber monoliths depend on it).
 *
 * <p><b>Body.</b> A ravager scaled 3.5 times (about 6.8 by 7.7 blocks, the mod's is 6 by 8.5) with no AI that this class
 * walks by velocity, with seven {@link SauropodBody} parts for the head, neck and tail. The mod's pathfinding for tall
 * walkers is replaced by walking straight at the target and leaping when it looks out of reach.
 */
public final class Luxtructosaurus {
    static final double SCALE = 3.5;
    /** Mod hitbox, for the parts' pose. */
    static final double HEIGHT = 8.5;
    static final double WIDTH = 6.0;
    /** Mod {@code step height} of the sauropods. */
    static final double STEP_HEIGHT = 3.2;
    /** Blocks a tick it walks (the mod's 0.325 attribute is paced by its stride). */
    static final double WALK_SPEED = 0.2;
    /** The boss bar shows to players within this many blocks (the mod's tracking range is 12 chunks). */
    static final double BAR_RANGE = 128.0;
    /** Mod config {@code luxtructosaurus_block_drop_chance}. */
    static final double BLOCK_DROP_CHANCE = 0.75;
    /** Mod config {@code atlatitan_max_block_explosion_resistance}, used by the stomp crush. */
    static final float CRUSH_RESISTANCE = 10.0F;

    public static final List<MobDrop> LOOT = List.of(MobDrop.of("ac_tectonic_shard", 7, 11));

    public static final ModMobType LUXTRUCTOSAURUS = new ModMobType("luxtructosaurus", CaveBiomeId.PRIMORDIAL_CAVES, "ravager",
        SpawnCategory.MONSTER,
        Map.of("max_health", SauropodRules.LUX_HEALTH, "armor", SauropodRules.LUX_ARMOR, "attack_damage",
            SauropodRules.LUX_ATTACK, "movement_speed", SauropodRules.LUX_SPEED, "knockback_resistance", 1.0, "follow_range",
            SauropodRules.LUX_FOLLOW_RANGE, "scale", SCALE, "step_height", STEP_HEIGHT),
        "alexscaves_luxtructosaurus", LOOT).asBoss();

    /** The rolled loot of a boss that is dying, saved with it so a restart in the middle of the death loses nothing. */
    static final NamespacedKey PENDING = new NamespacedKey("bigcasares", "ac_luxtructosaurus_loot");
    static final NamespacedKey LOOTED = new NamespacedKey("bigcasares", "ac_luxtructosaurus_looted");
    private static final NamespacedKey CUSTOM_ENTITY = new NamespacedKey("bigcasares", "custom_entity");

    private Luxtructosaurus() {
    }

    public static void register(ModMobCatalog catalog, PrimordialBlocks blocks) {
        catalog.register(LUXTRUCTOSAURUS, (entity, type) -> {
            if (entity instanceof Mob mob) {
                Bukkit.getMobGoals().removeAllGoals(mob);
                mob.setAware(false);
                mob.setDespawnInPeacefulOverride(TriState.FALSE);
            }
            return new Behaviour(blocks, entity);
        });
    }

    /** The boss itself or one of its parts (what its tephra and its blasts pass through). */
    static boolean isBossPart(Entity entity) {
        if (SauropodBody.partOwner(entity).isPresent()) {
            return true;
        }
        String tag = entity.getPersistentDataContainer().get(CUSTOM_ENTITY, PersistentDataType.STRING);
        return LUXTRUCTOSAURUS.customEntityTag().equals(tag);
    }

    /** Mod {@code VolcanicCoreBlockEntity.spawnBoss}: the boss rises, roaring, at the top of the volcano. */
    public static Optional<LivingEntity> summon(ModMobService mobs, Location at) {
        Optional<LivingEntity> spawned = mobs.spawn(LUXTRUCTOSAURUS.id(), at);
        spawned.ifPresent(boss -> mobs.behaviourOf(boss).ifPresent(behaviour -> {
            if (behaviour instanceof Behaviour lux) {
                lux.beginSummon(boss);
            }
        }));
        return spawned;
    }

    // --- behaviour ---

    static final class Behaviour implements ModMobBehaviour {
        /** Its own death sequence already played the mod's death animation before the entity really dies. */
        @Override
        public String deathAnimation(org.bukkit.entity.LivingEntity entity) {
            return null;
        }

        private final PrimordialBlocks blocks;
        private final SauropodBody body = new SauropodBody(HEIGHT, 1.0);
        private final Random random = new Random();
        private ModMobContext context;
        private BossBar bar;
        /** Id of the boss as registered in {@link PrimordialBoss}; entity may already be gone when closing. */
        private UUID bossId;
        private final java.util.Set<Player> viewers = new java.util.HashSet<>();

        private final PrimordialAnimator animator = new PrimordialAnimator();
        private boolean speakPending;
        private boolean dancing;
        private Anim anim = Anim.NONE;
        private int animTick;
        private boolean enraged;
        private int enragedFor;
        private int flamesCooldown;
        private int roarCooldown;
        private int jumpCooldown;
        private int navigationCheck;
        private int reducedDamageTicks;
        private int postStopTicks;
        private boolean stompMakesFissures;
        private boolean crushed;
        private Location lastStomp;
        private Vector jumpTarget;
        private final SauropodLegs legs = new SauropodLegs();
        private boolean prevOnGround = true;
        private int age;
        private int scareStamp;

        private UUID target;
        private Location wanderGoal;
        private int wanderTime;
        private Location lastProgress;
        private int stuck;

        private boolean dying;
        private boolean finishing;
        private List<ItemStack> loot = new ArrayList<>();
        private UUID killer;
        private boolean active;

        Behaviour(PrimordialBlocks blocks, LivingEntity entity) {
            this.blocks = blocks;
            // A boss that unloaded while dying comes back to finish dying and to throw its saved loot.
            byte[] pending = entity.getPersistentDataContainer().get(PENDING, PersistentDataType.BYTE_ARRAY);
            if (pending != null && !entity.getPersistentDataContainer().has(LOOTED, PersistentDataType.BYTE)) {
                loot = new ArrayList<>(List.of(ItemStack.deserializeItemsFromBytes(pending)));
                dying = true;
                anim = Anim.EPIC_DEATH;
                animTick = 100;
                enraged = true;
            }
        }

        void beginSummon(LivingEntity entity) {
            setAnim(entity, Anim.SUMMON);
            enragedFor = 100;
            enraged = true;
        }

        // --- tick ---

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            this.context = context;
            age += ticks;
            if (dying) {
                tickDying(entity, ticks);
                return;
            }
            Location at = entity.getLocation();
            body.ensure(entity, at, at.getYaw());
            body.forwardHits(entity);
            updateBar(entity);
            if (reducedDamageTicks > 0) {
                reducedDamageTicks = Math.max(0, reducedDamageTicks - ticks);
            }
            boolean summoning = anim == Anim.SUMMON;
            int previous = animTick;
            if (anim != Anim.NONE) {
                animTick += ticks;
            }
            if (summoning && SauropodRules.crossed(previous, animTick, 5)) {
                play(entity, "luxtructosaurus_summon", 3.0F);
            }
            animator.track(entity, ticks);
            dancing = anim == Anim.NONE && target() == null && animator.dancing(entity, ticks);
            if (!summoning) {
                think(entity, ticks, previous);
            }
            events(entity, ticks, previous);
            if (anim != Anim.NONE && animTick >= anim.duration) {
                anim = Anim.NONE;
                animTick = 0;
            }
            // Mod m_8107_: the enrage runs out unless it is hurt badly; a roar sets it for a while.
            if (enragedFor > 0) {
                enragedFor = Math.max(0, enragedFor - ticks);
            } else if (enraged && SauropodRules.calmsDown(enragedFor, entity.getHealth(), max(entity))) {
                enraged = false;
            }
            if (age % 4 == 0) {
                breakBlocks(entity);
            }
            if (enraged && ThreadLocalRandom.current().nextInt(4) == 0) {
                entity.getWorld().spawnParticle(Particle.FLAME, at.clone().add(0, 0.5, 0), 3, 2.0, 0.2, 2.0, 0.02);
            }
            // Mod: it heals 6 every 10 ticks while its target is not a player and none is within 150 blocks.
            if (age % 10 < ticks && entity.getHealth() < max(entity)) {
                LivingEntity prey = target();
                if (prey != null && !(prey instanceof Player) && !playerNear(entity, 150.0)) {
                    entity.setHealth(Math.min(max(entity), entity.getHealth() + 6.0));
                }
            }
            if (speakPending && anim == Anim.NONE) {
                // Mod SauropodBaseEntity.getAmbientSound: speaking starts the speak animation.
                setAnim(entity, Anim.SPEAK);
            }
            speakPending = false;
            boolean legsBack = legs.update(animator.speed() * ticks, animator.moving());
            animator.base(context, entity, PrimordialPose.sauropodBase(PrimordialPose.baseLoop(PrimordialPose.Kind.SAUROPOD,
                new PrimordialPose.State(false, dancing, false, false, false, false, false, false, entity.isOnGround(),
                    animator.moving())), legsBack));
            body.follow(entity.getLocation(), entity.getLocation().getYaw());
            prevOnGround = entity.isOnGround();
        }

        private static double max(LivingEntity entity) {
            org.bukkit.attribute.AttributeInstance health = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            return health == null ? SauropodRules.LUX_HEALTH : health.getValue();
        }

        private boolean playerNear(LivingEntity entity, double range) {
            for (Player player : entity.getWorld().getPlayers()) {
                if (player.getGameMode() != GameMode.SPECTATOR && player.getLocation().distanceSquared(entity.getLocation())
                    < range * range) {
                    return true;
                }
            }
            return false;
        }

        // --- goals ---

        /** Mod {@code LuxtructosaurusMeleeGoal} and the target goals, with its random stroll. */
        private void think(LivingEntity entity, int ticks, int previousTick) {
            pickTarget(entity);
            LivingEntity prey = target();
            if (flamesCooldown > 0) {
                flamesCooldown = Math.max(0, flamesCooldown - ticks);
            }
            if (roarCooldown > 0) {
                roarCooldown = Math.max(0, roarCooldown - ticks);
            }
            if (jumpCooldown > 0) {
                jumpCooldown = Math.max(0, jumpCooldown - ticks);
            }
            if (prey == null) {
                if (!dancing) {
                    wander(entity, ticks);
                }
                return;
            }
            wanderGoal = null;
            Location at = entity.getLocation();
            Location goal = prey.getLocation();
            double distance = at.distance(goal);
            double attackDistance = WIDTH + prey.getWidth();
            boolean spinning = anim.whip() || anim.kick() || anim == Anim.SPEW_FLAMES;
            if (spinning && !anim.whip()) {
                // Mod turningFast: it swings round to face its target, and looks at it.
                entity.setRotation(SauropodBody.approachDegrees(at.getYaw(),
                    SauropodBody.yawTo(goal.getX() - at.getX(), goal.getZ() - at.getZ()), 30.0F), 0.0F);
            }
            if (distance > attackDistance && anim != Anim.JUMP && !anim.whip()) {
                SauropodBody.walk(entity, goal.getX(), goal.getZ(), WALK_SPEED, 6.0F);
                trackProgress(at, ticks);
            }
            if (anim == Anim.NONE) {
                boolean far = false;
                if (distance < attackDistance + 4.0) {
                    Attack attack = SauropodRules.luxAttack(random.nextFloat(), distance, attackDistance,
                        entity.isOnGround());
                    switch (attack) {
                        case STOMP -> {
                            setAnim(entity, Anim.STOMP);
                            play(entity, "luxtructosaurus_attack_stomp", 3.0F);
                        }
                        case KICK -> {
                            play(entity, "luxtructosaurus_kick", 3.0F);
                            setAnim(entity, random.nextBoolean() ? Anim.LEFT_KICK : Anim.RIGHT_KICK);
                        }
                        default -> {
                            play(entity, "luxtructosaurus_tail", 3.0F);
                            setAnim(entity, random.nextBoolean() ? Anim.RIGHT_WHIP : Anim.LEFT_WHIP);
                            whipYaw = entity.getLocation().getYaw();
                        }
                    }
                } else {
                    far = true;
                    if (enraged && flamesCooldown == 0) {
                        flamesCooldown = SauropodRules.flamesCooldown(random);
                        setAnim(entity, Anim.SPEW_FLAMES);
                    }
                }
                int chance = SauropodRules.roarChance(far, enraged);
                boolean lowHealth = !enraged && entity.getHealth() < max(entity) * 0.25F;
                if ((anim == Anim.NONE || anim == Anim.SPEW_FLAMES)
                    && (roarCooldown == 0 && random.nextDouble() < SauropodRules.perTick(chance, ticks) || lowHealth)) {
                    roarCooldown = SauropodRules.roarCooldown(random);
                    setAnim(entity, Anim.ROAR);
                    enragedFor = SauropodRules.enragedFor(random);
                    enraged = true;
                }
            }
            // Mod: it leaps when the target looks out of reach (here: far off, well above it, or no progress toward it).
            navigationCheck -= ticks;
            if (jumpCooldown <= 0 && navigationCheck < 0 && (entity.isOnGround() || entity.isInLava())) {
                navigationCheck = SauropodRules.navigationCheck(random);
                if (!canReach(entity, prey, distance, attackDistance) && anim == Anim.NONE) {
                    setAnim(entity, Anim.JUMP);
                    play(entity, "luxtructosaurus_jump", 5.0F);
                    jumpTarget = prey.getLocation().toVector();
                    jumpCooldown = SauropodRules.jumpCooldown(random);
                }
            }
        }

        private float whipYaw;

        /** Mod {@code canReach}: not farther than 50 blocks and the path ends at the target. */
        private boolean canReach(LivingEntity entity, LivingEntity prey, double distance, double attackDistance) {
            if (distance > 50.0) {
                return false;
            }
            double rise = prey.getLocation().getY() - entity.getLocation().getY();
            return rise < 5.0 && stuck < 40;
        }

        private void trackProgress(Location at, int ticks) {
            if (lastProgress == null || lastProgress.getWorld() != at.getWorld()
                || lastProgress.distanceSquared(at) > 1.0) {
                lastProgress = at.clone();
                stuck = 0;
            } else {
                stuck += ticks;
            }
        }

        private void wander(LivingEntity entity, int ticks) {
            Location at = entity.getLocation();
            if (wanderGoal == null) {
                // Mod RandomStrollGoal(this, 1.0, 10): about one try in ten each tick, to a spot 30 blocks away.
                if (random.nextDouble() < SauropodRules.perTick(10, ticks)) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double length = 10 + random.nextDouble() * 20;
                    wanderGoal = at.clone().add(Math.cos(angle) * length, 0, Math.sin(angle) * length);
                    wanderTime = 0;
                }
                return;
            }
            wanderTime += ticks;
            if (wanderTime > 300 || at.distanceSquared(wanderGoal) < 9.0) {
                wanderGoal = null;
                return;
            }
            SauropodBody.walk(entity, wanderGoal.getX(), wanderGoal.getZ(), WALK_SPEED * 0.7, 4.0F);
        }

        // --- target ---

        private LivingEntity target() {
            if (target == null || context == null) {
                return null;
            }
            Entity found = context.plugin().getServer().getEntity(target);
            return found instanceof LivingEntity living && living.isValid() && !living.isDead() && attackable(living) ? living
                : null;
        }

        private static boolean attackable(LivingEntity living) {
            return !(living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR));
        }

        /** Mod target goals: the closest player in 3D within the follow range, else (now and then) another dinosaur. */
        private void pickTarget(LivingEntity entity) {
            LivingEntity current = target();
            if (current != null && current.getWorld() == entity.getWorld()
                && current.getLocation().distanceSquared(entity.getLocation()) < SauropodRules.LUX_FOLLOW_RANGE
                * SauropodRules.LUX_FOLLOW_RANGE) {
                return;
            }
            target = null;
            if (age % 10 >= 2) {
                return;
            }
            Player closest = null;
            double best = Double.MAX_VALUE;
            for (Player player : entity.getWorld().getPlayers()) {
                if (!attackable(player)) {
                    continue;
                }
                double distance = player.getLocation().distanceSquared(entity.getLocation());
                if (distance < best && distance < SauropodRules.LUX_FOLLOW_RANGE * SauropodRules.LUX_FOLLOW_RANGE) {
                    best = distance;
                    closest = player;
                }
            }
            if (closest != null) {
                target = closest.getUniqueId();
            } else if (age % 200 < 2) {
                LivingEntity near = null;
                for (Entity candidate : entity.getNearbyEntities(64, 32, 64)) {
                    if (candidate instanceof LivingEntity living && !isBossPart(candidate) && isDinosaur(living)
                        && (near == null || living.getLocation().distanceSquared(entity.getLocation())
                        < near.getLocation().distanceSquared(entity.getLocation()))) {
                        near = living;
                    }
                }
                if (near != null) {
                    target = near.getUniqueId();
                }
            }
        }

        private boolean isDinosaur(LivingEntity living) {
            return context.mobs().typeOf(living).map(type -> PrimordialMobs.ALL.contains(type)
                && type != PrimordialMobs.TRILOCARIS && type != PrimordialMobs.SUBTERRANODON || type == Atlatitan.ATLATITAN)
                .orElse(false);
        }

        // --- animation events ---

        private void events(LivingEntity entity, int ticks, int previous) {
            Location at = entity.getLocation();
            double attack = SauropodRules.LUX_ATTACK;
            switch (anim) {
                case JUMP -> jump(entity, ticks, at, attack);
                case ROAR -> roar(entity, ticks, previous);
                case STOMP -> {
                    if (SauropodRules.crossed(previous, animTick, 30) && postStopTicks <= 0) {
                        stompMakesFissures = enraged;
                        crushed = false;
                        postStopTicks = SauropodRules.postStopTicks(stompMakesFissures);
                        play(entity, "luxtructosaurus_stomp", 3.0F);
                        lastStomp = at.clone();
                        SauropodBody.hurtAround(entity, at, 10.0F, (float) (attack * 1.2), 2.0F, false, false);
                    }
                }
                case RIGHT_KICK -> kick(entity, previous, at, -2.0, attack);
                case LEFT_KICK -> kick(entity, previous, at, 2.0, attack);
                case LEFT_WHIP, RIGHT_WHIP -> whip(entity, ticks, at, attack);
                case SPEW_FLAMES -> {
                    if (animTick > 10 && animTick < 70) {
                        burnWithBreath(entity, 13.0F);
                    }
                }
                default -> {
                }
            }
            for (int i = 0; i < ticks && postStopTicks > 0; i++) {
                postStopTicks--;
                tickStomp(entity);
            }
        }

        private void kick(LivingEntity entity, int previous, Location at, double side, double attack) {
            if (SauropodRules.crossed(previous, animTick, 8)) {
                double yaw = Math.toRadians(at.getYaw());
                // Mod: arm at (side, 0, 2.5) rotated by the body yaw.
                double fx = -Math.sin(yaw);
                double fz = Math.cos(yaw);
                Location arm = at.clone().add(fx * 2.5 - fz * side, 0, fz * 2.5 + fx * side);
                SauropodBody.hurtAround(entity, arm, 5.0F, (float) (attack * 0.8), 2.0F, false, false);
            }
        }

        /** Mod {@code SauropodBaseEntity}: the whip spins the whole body up to 230 degrees, then it turns back. */
        private void whip(LivingEntity entity, int ticks, Location at, double attack) {
            float sign = anim == Anim.RIGHT_WHIP ? -1.0F : 1.0F;
            float yaw;
            if (animTick > 30) {
                yaw = SauropodBody.approachDegrees(at.getYaw(), whipYaw, 15.0F * ticks);
            } else {
                float spin = animTick > 10 ? (animTick - 10) / 30.0F * 230.0F : 0.0F;
                yaw = SauropodBody.approachDegrees(at.getYaw(), whipYaw + sign * spin, 90.0F * ticks);
            }
            entity.setRotation(yaw, 0.0F);
            if (animTick > 20 && animTick < 30) {
                Vector tail = body.center(entity.getLocation(), yaw, SauropodBody.TAIL_2);
                SauropodBody.hurtAround(entity, tail.toLocation(entity.getWorld()), 12.0F, (float) attack, 2.0F, enraged, true);
            }
        }

        /** Mod jump: from tick 15 to 25 it is thrown toward the target; it hurts where it lands. */
        private void jump(LivingEntity entity, int ticks, Location at, double attack) {
            Vector velocity = entity.getVelocity();
            if (animTick >= 15 && animTick <= 25 && jumpTarget != null) {
                double[] v = SauropodRules.jumpVelocity(jumpTarget.getX() - at.getX(), jumpTarget.getZ() - at.getZ(),
                    velocity.getX(), velocity.getZ(), animTick);
                entity.setVelocity(new Vector(v[0], v[1], v[2]));
            } else {
                if (entity.isOnGround() && !prevOnGround) {
                    SauropodBody.hurtAround(entity, at, 10.0F, (float) (attack * 0.5), 2.0F, false, false);
                }
                entity.setVelocity(velocity.setY(velocity.getY() - 0.2 * ticks));
            }
        }

        /** Mod roar: tephra falls from the ceiling every 5 ticks from tick 10 to 54 and the other mobs flee. */
        private void roar(LivingEntity entity, int ticks, int previous) {
            if (SauropodRules.crossed(previous, animTick, 2)) {
                play(entity, "luxtructosaurus_roar", 5.0F);
            }
            for (int t = previous + 1; t <= animTick; t++) {
                if (t >= 10 && t < 55 && t % 5 == 0) {
                    dropTephra(entity);
                }
            }
            if (animTick > 10 && animTick < 50) {
                scareMobs(entity);
            }
        }

        private void dropTephra(LivingEntity entity) {
            Tephra.get(context.plugin()).ifPresent(tephra -> {
                World world = entity.getWorld();
                Location at = entity.getLocation();
                int x = at.getBlockX() + random.nextInt(20) - 10;
                int z = at.getBlockZ() + random.nextInt(20) - 10;
                int y = at.getBlockY() + 2;
                int top = Math.min(world.getMaxHeight(), at.getBlockY() + 100);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    return;
                }
                while (y < top && !world.getBlockAt(x, y, z).getType().isSolid()) {
                    y++;
                }
                Location from = new Location(world, x + 0.5, y - 1 + 0.5, z + 0.5);
                Vector direction = new Vector(random.nextFloat() - 0.5, -1.0, random.nextFloat() - 0.5).normalize()
                    .multiply(random.nextInt(20) + 20);
                float scale = 1.0F + 2.0F * random.nextFloat();
                tephra.launch(from, Tephra.shoot(direction.getX(), direction.getY(), direction.getZ(),
                    5.0F + random.nextFloat() * 2.0F, 1.0F + random.nextFloat() * 0.5F, random), scale, entity, null);
            });
        }

        /** Mod {@code scareMobs}: every mob within 64 blocks forgets its target and runs (the boss's own kind is immune). */
        private void scareMobs(LivingEntity entity) {
            boolean stop = age - scareStamp > 5;
            if (stop) {
                scareStamp = age;
            }
            int handled = 0;
            for (Entity near : entity.getNearbyEntities(64, 20, 64)) {
                if (handled >= 60) {
                    break;
                }
                if (!(near instanceof Mob mob) || isBossPart(near) || resistsRoar(mob)) {
                    continue;
                }
                handled++;
                mob.setTarget(null);
                if (mob.isOnGround()) {
                    Vector shake = new Vector((random.nextFloat() - 0.5) * 0.1, 0, (random.nextFloat() - 0.5) * 0.1);
                    Vector velocity = mob.getVelocity();
                    mob.setVelocity(new Vector(velocity.getX() * 0.7, velocity.getY(), velocity.getZ() * 0.7).add(shake));
                }
                if (stop) {
                    mob.getPathfinder().stopPathfinding();
                }
                if (mob.getPathfinder().getCurrentPath() == null) {
                    Vector away = mob.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() > 1.0E-6) {
                        Location flee = mob.getLocation().add(away.normalize().multiply(30));
                        mob.getPathfinder().moveTo(flee, 2.0);
                    }
                }
            }
        }

        /** Mod tag {@code resists_tremorsaurus_roar}. */
        private boolean resistsRoar(Mob mob) {
            return context.mobs().typeOf(mob).map(type -> type == PrimordialMobs.TREMORSAURUS
                || type == LUXTRUCTOSAURUS || type.id().equals("tremorzilla")).orElse(false);
        }

        // --- stomp ---

        private void tickStomp(LivingEntity entity) {
            Location at = lastStomp == null ? entity.getLocation() : lastStomp;
            if (stompMakesFissures) {
                placeFissureRing(entity, SauropodRules.fissureWidth(postStopTicks), at);
            } else if (postStopTicks >= 15 && !crushed) {
                crushed = true;
                crushBlocks(entity, 12, at, 0.2F);
            }
        }

        private boolean griefing(World world) {
            return !Boolean.FALSE.equals(world.getGameRuleValue(GameRules.MOB_GRIEFING));
        }

        /** Mod {@code crushBlocksInRing}: blocks of low resistance within the ring and 6 above the feet are crushed. */
        private void crushBlocks(LivingEntity entity, int width, Location center, float dropChance) {
            World world = entity.getWorld();
            if (!griefing(world)) {
                return;
            }
            int feetY = entity.getLocation().getBlockY();
            for (int y = 0; y <= 6; y++) {
                for (int i = -width - 1; i <= width + 1; i++) {
                    for (int j = -width - 1; j <= width + 1; j++) {
                        int x = entity.getLocation().getBlockX() + i;
                        int z = entity.getLocation().getBlockZ() + j;
                        double dx = x - center.getBlockX();
                        double dz = z - center.getBlockZ();
                        if (Math.sqrt(dx * dx + dz * dz) > width || !world.isChunkLoaded(x >> 4, z >> 4)) {
                            continue;
                        }
                        Block block = world.getBlockAt(x, feetY + y, z);
                        Material type = block.getType();
                        if (type.isAir() || block.isLiquid() || block.isReplaceable() || PrimordialBlocks.unmovable(type)
                            || type.getBlastResistance() > CRUSH_RESISTANCE) {
                            continue;
                        }
                        destroy(entity, block, dropChance);
                    }
                }
            }
        }

        /** Breaks a block through {@link EntityChangeBlockEvent} (protection plugins can veto it), dropping with a chance. */
        private boolean destroy(LivingEntity entity, Block block, double dropChance) {
            if (!new EntityChangeBlockEvent(entity, block, Material.AIR.createBlockData()).callEvent()) {
                return false;
            }
            if (random.nextDouble() < dropChance) {
                block.breakNaturally();
            } else {
                block.setType(Material.AIR, false);
            }
            return true;
        }

        /** Mod {@code placeFissureRing}: a ring of the stomp's cracks, a block wider each tick. */
        private void placeFissureRing(LivingEntity entity, int width, Location center) {
            World world = entity.getWorld();
            if (!griefing(world)) {
                return;
            }
            Location here = entity.getLocation();
            int eyeY = (int) Math.floor(here.getY() + entity.getHeight() * 0.5);
            int cx = center.getBlockX();
            int cz = center.getBlockZ();
            for (int i = -width - 1; i <= width + 1; i++) {
                for (int j = -width - 1; j <= width + 1; j++) {
                    int x = here.getBlockX() + i;
                    int z = here.getBlockZ() + j;
                    double dist = Math.sqrt((x - cx) * (double) (x - cx) + (z - cz) * (double) (z - cz));
                    if (dist > width + 2 || dist <= width - 2 || !world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    int y = eyeY;
                    while (y > world.getMinHeight() && canFissureMoveThrough(world.getBlockAt(x, y, z))) {
                        y--;
                    }
                    if (placeFissureBlock(entity, world, x, y + 1, z)) {
                        world.spawnParticle(Particle.EXPLOSION, x + random.nextFloat(), y + 1.5 + random.nextFloat(),
                            z + random.nextFloat(), 0);
                    }
                }
            }
        }

        private boolean canFissureMoveThrough(Block block) {
            if (block.getY() <= block.getWorld().getMinHeight()) {
                return false;
            }
            Material type = block.getType();
            if (!type.isSolid() || Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type) || block.isPassable()) {
                return true;
            }
            // Mod: anything whose collision shape is not a full block (slabs, fences) is passed through too.
            java.util.Collection<org.bukkit.util.BoundingBox> shape = block.getCollisionShape().getBoundingBoxes();
            return !(shape.size() == 1 && shape.iterator().next().getVolume() >= 0.999);
        }

        /** Mod {@code placeFissureBlock}: along the edges of a Voronoi pattern, five blocks down become a fissure. */
        private boolean placeFissureBlock(LivingEntity entity, World world, int x, int top, int z) {
            double scale = 0.08F;
            int depth = 4;
            double[] distances = ModVoronoi.get2Distances(42L, 1.0, x * scale, z * scale);
            if (!(distances[1] - scale * 4.0F < distances[0])) {
                return false;
            }
            if (!griefing(world)) {
                return true;
            }
            int y = top;
            for (int i = 0; i <= depth; i++) {
                Block block = world.getBlockAt(x, y, z);
                if (y <= world.getMinHeight() || PrimordialBlocks.unmovable(block.getType()) || blocks.isFissure(block.getBlockData())) {
                    break;
                }
                boolean regenerates = blocks.regenerates(block);
                if (i < depth && !regenerates) {
                    destroy(entity, block, BLOCK_DROP_CHANCE);
                } else if (new EntityChangeBlockEvent(entity, block, Material.AIR.createBlockData()).callEvent()) {
                    if (i == depth) {
                        block.setBlockData(blocks.fissure[Math.max(0, Math.min(4, i - 1))], false);
                        BossTerrain.get(context.plugin()).ifPresent(terrain -> terrain.addFissure(block));
                    } else {
                        block.setType(Material.AIR, false);
                    }
                }
                y--;
            }
            return true;
        }

        // --- flames ---

        private void burnWithBreath(LivingEntity entity, float maxDistance) {
            World world = entity.getWorld();
            Location at = entity.getLocation();
            Vector head = body.center(at, at.getYaw(), SauropodBody.HEAD);
            double yaw = Math.toRadians(at.getYaw());
            Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
            for (double[] step : SauropodRules.breathSteps(maxDistance)) {
                double distance = step[0];
                double width = step[1];
                Vector burn = head.clone().add(forward.clone().multiply(distance));
                Location burnAt = burn.toLocation(world);
                world.spawnParticle(Particle.FLAME, burnAt, 3, width * 0.3, 0.3, width * 0.3, 0.02);
                if (random.nextFloat() < 0.5F * (1.0F - (maxDistance - distance) / maxDistance)) {
                    int x = (int) Math.floor(burn.getX() + (random.nextFloat() - 0.5F) * 2.0F * distance);
                    int z = (int) Math.floor(burn.getZ() + (random.nextFloat() - 0.5F) * 2.0F * distance);
                    if (world.isChunkLoaded(x >> 4, z >> 4)) {
                        int y = (int) Math.floor(burn.getY());
                        while (y > world.getMinHeight() && canFissureMoveThrough(world.getBlockAt(x, y, z))) {
                            y--;
                        }
                        Block fire = world.getBlockAt(x, y + 1, z);
                        if (fire.getType().isAir() || fire.isReplaceable() && !fire.isLiquid()) {
                            if (new BlockIgniteEvent(fire, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, entity).callEvent()) {
                                fire.setType(Material.FIRE, true);
                            }
                        }
                    }
                }
                SauropodBody.hurtAround(entity, burnAt, (float) width, 3.0F, 0.3F, true, false);
            }
        }

        // --- blocks in the way ---

        /** Mod {@code m_8107_}: leaves, logs and pewen wood in its way are broken, and water it wades in turns to stone. */
        private void breakBlocks(LivingEntity entity) {
            World world = entity.getWorld();
            // Mod: it breaks things when it runs into them or wades in water even with mob griefing off (still through events).
            boolean collided = stuck >= 4;
            if (!(griefing(world) || entity.isInWater() || collided) || anim == Anim.JUMP && animTick > 24 && entity.isOnGround()) {
                return;
            }
            org.bukkit.util.BoundingBox box = entity.getBoundingBox().expand(0.2);
            int minX = (int) Math.floor(box.getMinX() - 1.0);
            int maxX = (int) Math.ceil(box.getMaxX() + 1.0);
            int minY = (int) Math.floor(box.getMinY() - 1.0);
            int maxY = (int) Math.ceil(box.getMaxY() + 2.0);
            int minZ = (int) Math.floor(box.getMinZ() - 1.0);
            int maxZ = (int) Math.ceil(box.getMaxZ() + 1.0);
            int broken = 0;
            for (int x = minX; x <= maxX && broken < 200; x++) {
                for (int z = minZ; z <= maxZ && broken < 200; z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    for (int y = minY; y <= maxY; y++) {
                        Block block = world.getBlockAt(x, y, z);
                        Material type = block.getType();
                        if (type.isAir()) {
                            continue;
                        }
                        if (blocks.breaks(block)) {
                            if (destroy(entity, block, BLOCK_DROP_CHANCE)) {
                                broken++;
                            }
                        } else if (type == Material.WATER
                            && new EntityChangeBlockEvent(entity, block, Material.STONE.createBlockData()).callEvent()) {
                            block.setType(Material.STONE, true);
                            world.playSound(block.getLocation().add(0.5, 0.5, 0.5), "minecraft:block.fire.extinguish",
                                SoundCategory.BLOCKS, 0.5F, 2.6F);
                        }
                    }
                }
            }
        }

        // --- bar and eruption ---

        private void updateBar(LivingEntity entity) {
            if (bar == null) {
                bar = BossBar.bossBar(Component.text("Luxtruyesaurio"), 1.0F, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                bar.addFlag(BossBar.Flag.PLAY_BOSS_MUSIC);
            }
            bar.progress((float) Math.max(0.0, Math.min(1.0, entity.getHealth() / max(entity))));
            World world = entity.getWorld();
            java.util.Set<Player> near = new java.util.HashSet<>();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(entity.getLocation()) <= BAR_RANGE * BAR_RANGE) {
                    near.add(player);
                }
            }
            for (Player player : new ArrayList<>(viewers)) {
                if (!near.contains(player)) {
                    player.hideBossBar(bar);
                    viewers.remove(player);
                }
            }
            for (Player player : near) {
                if (viewers.add(player)) {
                    player.showBossBar(bar);
                }
            }
            // Mod: it tracks as erupting while a player sees it and stops when none does (and when it dies).
            boolean erupting = !viewers.isEmpty() && !dying;
            if (erupting != active) {
                active = erupting;
                bossId = entity.getUniqueId();
                PrimordialBoss.setActive(entity.getUniqueId(), world.getUID(), erupting);
            }
        }

        private void hideBar(LivingEntity entity) {
            if (bar != null) {
                for (Player player : viewers) {
                    player.hideBossBar(bar);
                }
                viewers.clear();
            }
            UUID id = entity != null ? entity.getUniqueId() : bossId;
            if (id != null) {
                PrimordialBoss.setActive(id, new UUID(0, 0), false);
            }
            bossId = null;
            active = false;
        }

        // --- damage ---

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            this.context = context;
            if (finishing) {
                return;
            }
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.KILL || cause == EntityDamageEvent.DamageCause.VOID) {
                return;
            }
            if (dying || cause == EntityDamageEvent.DamageCause.SUFFOCATION || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
                // The mod's boss is fire-immune and walks in walls without harm.
                event.setCancelled(true);
                return;
            }
            Entity damager = null;
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                damager = byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                    ? shooter : byEntity.getDamager();
                boolean dinosaur = isDinosaurAttacker(damager);
                boolean golem = context.mobs().typeOf(damager).isEmpty() && (damager.getType() == EntityType.IRON_GOLEM
                    || damager.getType() == EntityType.SNOW_GOLEM || damager.getType() == EntityType.COPPER_GOLEM);
                boolean warden = damager.getType() == EntityType.WARDEN;
                event.setDamage(SauropodRules.luxDamage((float) event.getDamage(), reducedDamageTicks > 0, dinosaur, golem, warden,
                    byEntity.getDamager() instanceof Projectile));
            } else if (reducedDamageTicks > 0) {
                event.setDamage(event.getDamage() * 0.35);
            }
            if (reducedDamageTicks == 0) {
                reducedDamageTicks = 10;
            }
            if (damager instanceof LivingEntity living && living != entity && !isBossPart(living) && attackable(living)) {
                target = living.getUniqueId();
            }
            if (event.getFinalDamage() >= entity.getHealth()) {
                event.setCancelled(true);
                startDying(entity, context, damager);
            }
        }

        private boolean isDinosaurAttacker(Entity damager) {
            return context.mobs().typeOf(damager).map(type -> PrimordialMobs.ALL.contains(type)
                && type != PrimordialMobs.TRILOCARIS || type == Atlatitan.ATLATITAN).orElse(false);
        }

        // --- death ---

        /** Mod {@code tickDeath}: 120 ticks of the epic death, then the loot is thrown out and the boss is gone. */
        private void startDying(LivingEntity entity, ModMobContext context, Entity cause) {
            dying = true;
            setAnim(entity, Anim.EPIC_DEATH);
            enraged = true;
            target = null;
            killer = cause == null ? null : cause.getUniqueId();
            entity.setHealth(Math.min(1.0, entity.getHealth()));
            Player credit = cause instanceof Player player ? player : null;
            boolean looted = entity.getPersistentDataContainer().has(LOOTED, PersistentDataType.BYTE);
            loot = looted ? new ArrayList<>() : new ArrayList<>(context.mobs().rollDrops(LUXTRUCTOSAURUS, credit, false));
            if (!loot.isEmpty()) {
                entity.getPersistentDataContainer().set(PENDING, PersistentDataType.BYTE_ARRAY,
                    ItemStack.serializeItemsAsBytes(loot));
            }
            hideBar(entity);
            play(entity, "luxtructosaurus_death", 3.0F);
            // Mod createWitherRose (called when the killer is a living thing): the first defeat is remembered.
            if (cause instanceof LivingEntity && PrimordialBoss.markDefeated(entity.getWorld())) {
                Component message = Component.text("Unas nuevas figuras toman forma dentro de los monolitos de ámbar...",
                    NamedTextColor.GOLD);
                for (Player player : entity.getWorld().getPlayers()) {
                    player.sendActionBar(message);
                }
            }
        }

        private void tickDying(LivingEntity entity, int ticks) {
            int previous = animTick;
            animTick += ticks;
            Vector velocity = entity.getVelocity();
            entity.setVelocity(new Vector(0, velocity.getY(), 0));
            body.follow(entity.getLocation(), entity.getLocation().getYaw());
            if (animTick >= 100 && animTick <= 110) {
                entity.getWorld().spawnParticle(Particle.ASH, entity.getLocation().add(0, 3, 0), 50, 3.0, 3.0, 3.0, 0.0);
            }
            if (animTick > 110 && !finishing) {
                throwLoot(entity);
                finishing = true;
                entity.getWorld().spawnParticle(Particle.POOF, entity.getLocation().add(0, 3, 0), 60, 3.0, 3.0, 3.0, 0.02);
                Entity cause = killer == null ? null : entity.getServer().getEntity(killer);
                entity.damage(1.0E6, cause);
                if (entity.isValid() && !entity.isDead()) {
                    entity.setHealth(0.0);
                }
            }
        }

        /** Mod {@code spawnAtLocation}: each item flung out above the boss, glowing, from a random spot of its body. */
        private void throwLoot(LivingEntity entity) {
            entity.getPersistentDataContainer().set(LOOTED, PersistentDataType.BYTE, (byte) 1);
            entity.getPersistentDataContainer().remove(PENDING);
            for (ItemStack stack : loot) {
                for (int i = 0; i < stack.getAmount(); i++) {
                    ItemStack one = stack.clone();
                    one.setAmount(1);
                    Location at = entity.getLocation().add(3.0 - 6.0 * random.nextFloat(), 4.0, 3.0 - 6.0 * random.nextFloat());
                    Vector delta = new Vector(1.0F - random.nextFloat(), 0.0, 1.0F - random.nextFloat());
                    if (delta.lengthSquared() > 1.0E-6) {
                        delta.normalize().multiply(random.nextFloat() * 0.4F + 0.4F);
                    }
                    delta.setY(0.2);
                    entity.getWorld().dropItem(at, one, (Item item) -> {
                        item.setVelocity(delta);
                        item.setGlowing(true);
                    });
                }
            }
            loot.clear();
        }

        @Override
        public void onDeathDrops(LivingEntity entity, List<ItemStack> drops) {
            // It threw its loot out while it died (or it was saved as looted): nothing more comes out.
            if (entity.getPersistentDataContainer().has(LOOTED, PersistentDataType.BYTE)) {
                drops.clear();
            } else if (dying) {
                // Killed some other way before it threw the loot out: what it had rolled comes out now, once.
                drops.clear();
                drops.addAll(loot);
                loot.clear();
                entity.getPersistentDataContainer().set(LOOTED, PersistentDataType.BYTE, (byte) 1);
                entity.getPersistentDataContainer().remove(PENDING);
            }
        }

        @Override
        public int experience(LivingEntity entity, int vanilla) {
            return 0;
        }

        @Override
        public float soundVolume() {
            return 3.0F;
        }

        @Override
        public String soundSuffix(LivingEntity entity, String suffix) {
            if (suffix.equals("idle")) {
                speakPending = true;
            }
            return dying && !suffix.equals("idle") ? null : suffix;
        }

        // --- helpers ---

        private void setAnim(LivingEntity entity, Anim next) {
            anim = next;
            animTick = 0;
            String name = PrimordialPose.sauropod(next);
            if (name != null && context != null) {
                // Mod: the epic death is its own animation (the boss never plays the vanilla fall).
                animator.action(context, entity, name);
            }
        }


        private void play(LivingEntity entity, String sound, float volume) {
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:" + sound, SoundCategory.HOSTILE, volume,
                0.9F + random.nextFloat() * 0.2F);
        }

        @Override
        public void close(LivingEntity entity, ModMobContext context) {
            this.context = context == null ? this.context : context;
            animator.close();
            // A boss that unloads while dying keeps its loot saved (see PENDING) and finishes when it loads again.
            body.close();
            hideBar(entity);
        }
    }
}
