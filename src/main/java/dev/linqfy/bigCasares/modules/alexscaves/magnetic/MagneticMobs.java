package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobAnimator;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Bat;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Mobs of the Magnetic Caves with the stats and drops of the mod. */
public final class MagneticMobs {
    private static final CaveBiomeId BIOME = CaveBiomeId.MAGNETIC_CAVES;

    public static final ModMobType TELETOR = new ModMobType("teletor", BIOME, "blaze", SpawnCategory.MONSTER,
        Map.of("max_health", 18.0, "attack_damage", 2.0, "flying_speed", 1.0, "movement_speed", 0.2, "follow_range", 32.0),
        "alexscaves_teletor", List.of(MobDrop.of("ac_raw_azure_neodymium", 1, 2), MobDrop.of("ac_raw_scarlet_neodymium", 1, 2),
            MobDrop.of("ac_telecore", 0, 1)));
    public static final ModMobType MAGNETRON = new ModMobType("magnetron", BIOME, "zombie", SpawnCategory.MONSTER,
        Map.of("max_health", 30.0, "attack_damage", 2.0, "armor", 6.0, "movement_speed", 0.2, "follow_range", 32.0),
        "alexscaves_magnetron", List.of(MobDrop.of("ac_heart_of_iron", 1, 1)));
    public static final ModMobType BOUNDROID = new ModMobType("boundroid", BIOME, "spider", SpawnCategory.MONSTER,
        Map.of("max_health", 20.0, "attack_damage", 5.0, "armor", 20.0, "movement_speed", 0.2, "follow_range", 32.0),
        "alexscaves_boundroid", List.of(MobDrop.of("ac_heavyweight", 0, 1), MobDrop.of("iron_chain", 2, 3)));
    /** The boundroid's winch: a bat base with its own AI off, driven by this class; it carries the winch model. */
    public static final ModMobType BOUNDROID_WINCH = new ModMobType("boundroid_winch", BIOME, "bat", SpawnCategory.AMBIENT,
        Map.of("max_health", 20.0), "alexscaves_boundroid_winch", List.of());
    public static final ModMobType FERROUSLIME = new ModMobType("ferrouslime", BIOME, "slime", SpawnCategory.MONSTER,
        Map.of("max_health", 10.0, "attack_damage", 2.0, "movement_speed", 0.35),
        "alexscaves_ferrouslime", List.of(MobDrop.of("ac_ferrouslime_ball", 0, 1)));
    public static final ModMobType NOTOR = new ModMobType("notor", BIOME, "bat", SpawnCategory.AMBIENT,
        Map.of("max_health", 6.0, "movement_speed", 0.15),
        "alexscaves_notor", List.of(MobDrop.of("ac_notor_gizmo", 0, 1)));

    public static final List<ModMobType> ALL = List.of(TELETOR, MAGNETRON, BOUNDROID, BOUNDROID_WINCH, FERROUSLIME, NOTOR);

    private static final double GROUND_SPEED = 0.2;
    private static final double FLEE_SPEED = 0.26;
    private static final double CEILING_SPEED = 0.1;

    private MagneticMobs() {
    }

    public static void register(ModMobCatalog catalog) {
        catalog.register(TELETOR, (entity, type) -> new Teletor());
        catalog.register(MAGNETRON, (entity, type) -> new Magnetron());
        catalog.register(BOUNDROID, (entity, type) -> {
            // The mod's boundroid has no walking AI: the winch drags it, and this class picks its targets.
            if (entity instanceof Mob mob) {
                mob.setAware(false);
            }
            return new Boundroid();
        });
        catalog.register(BOUNDROID_WINCH, (entity, type) -> {
            if (entity instanceof Mob mob) {
                mob.setAware(false);
            }
            if (entity instanceof Bat bat) {
                bat.setAwake(true);
            }
            entity.setCollidable(false);
            entity.setPersistent(false);
            return new BoundroidWinch();
        });
        catalog.register(FERROUSLIME, (entity, type) -> new Ferrouslime(entity));
        catalog.register(NOTOR, (entity, type) -> new Notor());
    }

    private static LivingEntity target(LivingEntity entity) {
        return entity instanceof Mob mob ? mob.getTarget() : null;
    }

    /** Hovers and swings its floating iron tool at range (the blaze base's fireballs are cancelled). */
    static final class Teletor implements ModMobBehaviour {
        static final double RANGE = 10.0;
        private final MobAnimator animation = new MobAnimator();
        private int cooldown;

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            cooldown -= ticks;
            LivingEntity target = target(entity);
            animation.update(entity, context, ticks, controlling(entity, target) ? "control" : null);
            if (target == null || cooldown > 0 || !target.isValid()
                || target.getLocation().distanceSquared(entity.getLocation()) > RANGE * RANGE || !entity.hasLineOfSight(target)) {
                return;
            }
            cooldown = 20;
            Location from = entity.getEyeLocation();
            Vector step = target.getEyeLocation().toVector().subtract(from.toVector());
            double length = step.length();
            step.normalize().multiply(0.5);
            Location point = from.clone();
            for (double travelled = 0; travelled < length; travelled += 0.5) {
                point.add(step);
                entity.getWorld().spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
            }
            target.damage(entity.getAttribute(Attribute.ATTACK_DAMAGE) == null ? 2.0
                : entity.getAttribute(Attribute.ATTACK_DAMAGE).getValue() + 2.0, entity);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 1.4f);
            animation.action(entity, context, "attack");
        }

        /** Mod {@code controlProgress}: the arms-up pose while it controls its floating tool, i.e. has a live target in reach. */
        static boolean controlling(LivingEntity entity, LivingEntity target) {
            return target != null && target.isValid() && target.getWorld() == entity.getWorld()
                && target.getLocation().distanceSquared(entity.getLocation()) <= RANGE * RANGE;
        }
    }

    /** Rolls around until it sees a target, then builds a body of orbiting metal blocks (HP 30 -> 80). */
    static final class Magnetron implements ModMobBehaviour {
        private static final double FORM_RANGE = 16.0;
        private final List<BlockDisplay> body = new ArrayList<>();
        private final MobAnimator animation = new MobAnimator();
        private float angle;

        /** Mod: formed it shows {@code form}; still rolling on its wheel it leans ({@code roll_lean}) while moving. */
        static String state(boolean formed, boolean moving) {
            return formed ? "form" : moving ? "roll_lean" : null;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            LivingEntity target = target(entity);
            boolean moving = animation.moving();
            if (body.isEmpty() && target != null && target.getLocation().distanceSquared(entity.getLocation()) < FORM_RANGE * FORM_RANGE) {
                form(entity);
            }
            animation.update(entity, context, ticks, state(!body.isEmpty(), moving));
            if (body.isEmpty()) {
                return;
            }
            angle += 0.15f * ticks;
            Location center = entity.getLocation().add(0, 1.2, 0);
            for (int i = 0; i < body.size(); i++) {
                BlockDisplay display = body.get(i);
                double a = angle + i * (Math.PI * 2 / body.size());
                Location at = center.clone().add(Math.cos(a) * 1.1, (i % 3 - 1) * 0.6, Math.sin(a) * 1.1);
                display.teleport(at);
            }
            if (ThreadLocalRandom.current().nextInt(20) == 0) {
                entity.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 6, 0.6, 0.6, 0.6, 0.05);
            }
        }

        private void form(LivingEntity entity) {
            AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(80.0);
                entity.setHealth(Math.min(80.0, entity.getHealth() + 50.0));
            }
            BlockData[] parts = {Material.IRON_BLOCK.createBlockData(), Material.POLISHED_DEEPSLATE.createBlockData(),
                Material.IRON_CHAIN.createBlockData()};
            for (int i = 0; i < 6; i++) {
                BlockData data = parts[i % parts.length];
                body.add(entity.getWorld().spawn(entity.getLocation(), BlockDisplay.class, display -> {
                    display.setBlock(data);
                    display.setPersistent(false);
                    display.setTransformation(new Transformation(new Vector3f(-0.3f, -0.3f, -0.3f),
                        new AxisAngle4f(), new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
                    display.setTeleportDuration(2);
                }));
            }
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        }

        @Override
        public void close(LivingEntity entity, ModMobContext context) {
            body.forEach(Entity::remove);
            body.clear();
        }
    }

    static final NamespacedKey WINCH_KEY = new NamespacedKey("bigcasares", "ac_boundroid_winch");
    static final NamespacedKey HEAD_KEY = new NamespacedKey("bigcasares", "ac_boundroid_head");

    /**
     * Mod {@code BoundroidEntity}: the dangling head of a winch. It has no walking AI: its winch (which it creates when
     * it has none, as the mod does) crawls the ceiling and the chain drags the head along. When a target is within 1.2
     * blocks horizontally the head slams straight down (5 damage in a 3 block box), stays down 10 ticks and is then
     * reeled up again; it waits 30-49 ticks between slams. Immune to fall damage.
     */
    static final class Boundroid implements ModMobBehaviour {
        private static final double FOLLOW_RANGE = 32.0;
        private final MobAnimator animation = new MobAnimator();
        private UUID winchId;
        private int winchMissing;
        private UUID target;
        private int targetCheck;
        private boolean slamming;
        private int stopSlammingFor;
        private int stayOnGroundFor;

        /** Mod {@code groundProgress}: resting on the ground it stops swaying its head; moving it walks. */
        static String state(boolean onGround, boolean moving) {
            return onGround && !moving ? "ground" : null;
        }

        boolean stopPullingUp() {
            return BoundroidWinchRules.stopPullingUp(slamming, stayOnGroundFor);
        }

        /** Current live target, or null (also forgets one that is gone, far or no longer attackable). */
        LivingEntity chased(LivingEntity self) {
            if (target == null) {
                return null;
            }
            Entity found = self.getServer().getEntity(target);
            if (found instanceof LivingEntity living && living.isValid() && !living.isDead()
                && living.getWorld().equals(self.getWorld())
                && !(living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR))
                && living.getLocation().distanceSquared(self.getLocation()) <= FOLLOW_RANGE * FOLLOW_RANGE * 2.25) {
                return living;
            }
            target = null;
            return null;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            animation.update(entity, context, ticks, state(entity.isOnGround(), animation.moving()));
            ensureWinch(entity, context, ticks);
            LivingEntity chased = chased(entity);
            targetCheck -= ticks;
            if (chased == null && targetCheck <= 0) {
                targetCheck = 10;
                chased = nearestPlayer(entity);
                target = chased == null ? null : chased.getUniqueId();
            }
            if (chased != null && !slamming) {
                Location look = entity.getLocation().setDirection(chased.getEyeLocation().toVector()
                    .subtract(entity.getEyeLocation().toVector()));
                entity.setRotation(look.getYaw(), 0);
            }
            if (chased != null && !slamming) {
                double horizontal = Math.hypot(chased.getLocation().getX() - entity.getLocation().getX(),
                    chased.getLocation().getZ() - entity.getLocation().getZ());
                if (BoundroidWinchRules.startsSlam(true, horizontal, stopSlammingFor)) {
                    slamming = true;
                }
            } else if (slamming) {
                Vector velocity = entity.getVelocity();
                entity.setVelocity(new Vector(velocity.getX(), -1.0, velocity.getZ()));
                if (entity.isOnGround()) {
                    slam(entity, context);
                }
            }
            stopSlammingFor = Math.max(0, stopSlammingFor - ticks);
            stayOnGroundFor = Math.max(0, stayOnGroundFor - ticks);
        }

        private void slam(LivingEntity entity, ModMobContext context) {
            slamming = false;
            stayOnGroundFor = BoundroidWinchRules.STAY_ON_GROUND_TICKS;
            stopSlammingFor = BoundroidWinchRules.slamPause(ThreadLocalRandom.current().nextInt(BoundroidWinchRules.SLAM_PAUSE_RANDOM));
            Location at = entity.getLocation();
            entity.getWorld().playSound(at, "alexscaves:boundroid_slam", 1.0f, 1.0f);
            entity.getWorld().spawnParticle(Particle.BLOCK, at, 20, 0.6, 0.1, 0.6, Material.POLISHED_DEEPSLATE.createBlockData());
            animation.action(entity, context, "attack");
            // Mod box: -1.5..1.5 around the head, from 2 below to 1 above.
            for (Entity nearby : entity.getWorld().getNearbyEntities(at.clone().add(0, -0.5, 0), 1.5, 1.5, 1.5)) {
                if (nearby instanceof LivingEntity living && !nearby.equals(entity)
                    && context.mobs().typeOf(living).filter(type -> type == BOUNDROID || type == BOUNDROID_WINCH).isEmpty()) {
                    living.damage(5.0, entity);
                }
            }
        }

        /** Mod {@code MobTarget3DGoal}: the nearest player in follow range that the head can see. */
        private static LivingEntity nearestPlayer(LivingEntity entity) {
            Player best = null;
            double bestDistance = FOLLOW_RANGE * FOLLOW_RANGE;
            for (Player player : entity.getWorld().getPlayers()) {
                double distance = player.getLocation().distanceSquared(entity.getLocation());
                if (distance < bestDistance && player.getGameMode() != GameMode.CREATIVE
                    && player.getGameMode() != GameMode.SPECTATOR && !player.isDead() && entity.hasLineOfSight(player)) {
                    best = player;
                    bestDistance = distance;
                }
            }
            return best;
        }

        /** Mod: a head without a winch makes one (also how old boundroids get theirs); missing for a second it is replaced. */
        private void ensureWinch(LivingEntity entity, ModMobContext context, int ticks) {
            PersistentDataContainer data = entity.getPersistentDataContainer();
            if (winchId == null) {
                String saved = data.get(WINCH_KEY, PersistentDataType.STRING);
                if (saved != null) {
                    try {
                        winchId = UUID.fromString(saved);
                    } catch (IllegalArgumentException invalid) {
                        winchId = null;
                    }
                }
            }
            Entity winch = winchId == null ? null : entity.getServer().getEntity(winchId);
            if (winch != null && winch.isValid()) {
                winchMissing = 0;
                return;
            }
            winchMissing += ticks;
            if (winchId != null && winchMissing <= 20) {
                return;
            }
            // Without a winch nothing holds the head up (the latched winch had switched its gravity off).
            entity.setGravity(true);
            winchMissing = 0;
            winchId = context.mobs().spawn(BOUNDROID_WINCH.id(), entity.getLocation().add(0, 0.5, 0)).map(spawned -> {
                spawned.getPersistentDataContainer().set(HEAD_KEY, PersistentDataType.STRING, entity.getUniqueId().toString());
                data.set(WINCH_KEY, PersistentDataType.STRING, spawned.getUniqueId().toString());
                return spawned.getUniqueId();
            }).orElse(null);
        }

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
                return;
            }
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                Entity damager = byEntity.getDamager();
                if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                    damager = shooter;
                }
                // Mod HurtByTargetGoal.
                if (damager instanceof LivingEntity living && living != entity
                    && !(living instanceof Player player && player.getGameMode() == GameMode.CREATIVE)) {
                    target = living.getUniqueId();
                }
            }
        }
    }

    /**
     * Mod {@code BoundroidWinchEntity}: the winch that carries a boundroid. It crawls on the ground and, when it is
     * under a ceiling (hopping up to it when it is out of reach), latches to it with its legs ({@code latch} model
     * pose, progress 0-5). A chain hangs from it to the head: latched, the head dangles 1-1.5 blocks below; loose,
     * 3.5. A winch hit passes the damage to its head and is dazed (no latching for 60-119 ticks, so it falls). A
     * winch whose head is gone goes too, and it plays its death while the head dies.
     */
    static final class BoundroidWinch implements ModMobBehaviour {
        private static final int HEAD_GRACE_TICKS = 40;
        private static final int MAX_SCAN = 48;
        private final MobAnimator animation = new MobAnimator();
        private final List<BlockDisplay> chain = new ArrayList<>();
        private UUID headId;
        private int headMissing;
        private int age;
        private boolean latched;
        private int latchProgress;
        private int latchTimer;
        private int noLatchCooldown;
        private boolean goingUp;
        private boolean dying;
        private Location goal;
        private int goalAge;
        private int stepAge;

        @Override
        public String soundSuffix(LivingEntity entity, String suffix) {
            return null;
        }

        @Override
        public String deathAnimation(LivingEntity entity) {
            return null;
        }

        private LivingEntity head(LivingEntity entity) {
            if (headId == null) {
                String saved = entity.getPersistentDataContainer().get(HEAD_KEY, PersistentDataType.STRING);
                if (saved == null) {
                    return null;
                }
                try {
                    headId = UUID.fromString(saved);
                } catch (IllegalArgumentException invalid) {
                    return null;
                }
            }
            Entity found = entity.getServer().getEntity(headId);
            return found instanceof LivingEntity living && living.isValid() && living.getWorld().equals(entity.getWorld())
                ? living : null;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            age += ticks;
            LivingEntity head = head(entity);
            if (head == null) {
                headMissing += ticks;
                if (headMissing > HEAD_GRACE_TICKS) {
                    entity.remove();
                }
                return;
            }
            headMissing = 0;
            drawChain(entity, head);
            if (head.isDead()) {
                if (!dying) {
                    dying = true;
                    context.model(entity.getUniqueId()).ifPresent(handle -> context.models().animateOnce(handle, "death", () -> { }));
                }
                return;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Boundroid brain = context.mobs().behaviourOf(head).filter(Boundroid.class::isInstance)
                .map(Boundroid.class::cast).orElse(null);
            LivingEntity chased = brain == null ? null : brain.chased(head);
            boolean stopPulling = brain != null && brain.stopPullingUp();
            for (int i = 0; i < ticks; i++) {
                latchProgress = BoundroidWinchRules.stepLatchProgress(latchProgress, latched);
            }
            noLatchCooldown = Math.max(0, noLatchCooldown - ticks);
            animation.update(entity, context, ticks, BoundroidWinchRules.latchState(latchProgress));

            double distance = entity.getLocation().distance(head.getLocation());
            double length = BoundroidWinchRules.chainLength(latched, age);
            if (BoundroidWinchRules.phasesThroughBlocks(head.hasLineOfSight(entity), stopPulling, distance)) {
                // The mod switches the head's collision off to reel it in; here it is brought next to the winch.
                head.teleport(entity.getLocation().add(0, -BoundroidWinchRules.LATCHED_CHAIN, 0));
                distance = BoundroidWinchRules.LATCHED_CHAIN;
            }
            double pull = BoundroidWinchRules.pull(distance, length, stopPulling, chased != null);
            if (pull > 0) {
                Vector toWinch = entity.getLocation().toVector().subtract(head.getLocation().toVector());
                if (toWinch.length() > 1.0) {
                    toWinch.normalize();
                }
                Vector velocity = head.getVelocity();
                velocity.setX(velocity.getX() * Math.pow(0.95, ticks));
                velocity.setY(velocity.getY() * Math.pow(0.7, ticks));
                velocity.setZ(velocity.getZ() * Math.pow(0.95, ticks));
                head.setVelocity(velocity.add(toWinch.multiply(pull * ticks)));
            }

            double ceilingDistance = ceilingDistance(entity);
            boolean alive = entity.isValid();
            latchTimer = BoundroidWinchRules.nextLatchTimer(latched, latchTimer, ceilingDistance, alive, noLatchCooldown);
            if (latched) {
                entity.setGravity(false);
                head.setGravity(false);
                if (BoundroidWinchRules.flipsLatch(latchTimer)) {
                    latched = false;
                    latchTimer = 0;
                }
                // Pressed against the ceiling.
                entity.setVelocity(entity.getVelocity().add(new Vector(0, 0.14, 0)).multiply(0.85));
                goingUp = false;
            } else {
                entity.setGravity(true);
                head.setGravity(true);
                if (BoundroidWinchRules.flipsLatch(latchTimer)) {
                    latched = true;
                    latchTimer = 0;
                    goal = null;
                }
                if (goingUp && !blockAbove(entity)) {
                    goingUp = false;
                }
                if (goingUp) {
                    Vector velocity = entity.getVelocity();
                    entity.setVelocity(new Vector(velocity.getX(), 1.5, velocity.getZ()));
                } else if (BoundroidWinchRules.hopsUp(entity.isOnGround(), noLatchCooldown, alive,
                    random.nextInt(Math.max(1, 30 / ticks)), ceilingDistance, blockAbove(entity))) {
                    goingUp = true;
                }
            }
            if (!goingUp) {
                steer(entity, chased, ticks, random);
            }
        }

        /** Mod goals: latched it crawls under its target (or wanders the ceiling); loose it flees its target or strolls. */
        private void steer(LivingEntity entity, LivingEntity chased, int ticks, ThreadLocalRandom random) {
            Location here = entity.getLocation();
            goalAge += ticks;
            if (goal != null && (goalAge > 100 || flat(here, goal) < 0.7)) {
                goal = null;
            }
            double speed;
            if (chased != null) {
                if (latched) {
                    goal = chased.getLocation();
                } else {
                    Vector away = here.toVector().subtract(chased.getLocation().toVector()).setY(0);
                    if (away.lengthSquared() < 1.0E-4) {
                        away = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
                    }
                    goal = here.clone().add(away.normalize().multiply(15));
                }
                speed = latched ? CEILING_SPEED : FLEE_SPEED;
                goalAge = 0;
            } else {
                if (goal == null && random.nextInt(Math.max(1, (latched ? 10 : 45) / ticks)) == 0) {
                    int range = latched ? 16 : 10;
                    goal = here.clone().add(random.nextInt(range * 2 + 1) - range, 0, random.nextInt(range * 2 + 1) - range);
                    goalAge = 0;
                }
                speed = latched ? CEILING_SPEED : GROUND_SPEED;
            }
            if (goal == null) {
                return;
            }
            Vector toGoal = goal.toVector().subtract(here.toVector()).setY(0);
            if (toGoal.lengthSquared() < 1.0E-4) {
                return;
            }
            toGoal.normalize();
            Vector velocity = entity.getVelocity();
            double blend = 1.0 - Math.pow(0.65, ticks);
            velocity.setX(velocity.getX() + (toGoal.getX() * speed - velocity.getX()) * blend);
            velocity.setZ(velocity.getZ() + (toGoal.getZ() * speed - velocity.getZ()) * blend);
            entity.setVelocity(velocity);
            entity.setRotation((float) Math.toDegrees(Math.atan2(-toGoal.getX(), toGoal.getZ())), 0);
            stepAge += ticks;
            if (latched && stepAge > 6) {
                // Mod: a footstep on the ceiling's material.
                stepAge = 0;
                Block ceiling = here.getBlock().getRelative(0, (int) Math.ceil(entity.getHeight()), 0);
                if (!ceiling.getType().isAir()) {
                    entity.getWorld().playSound(ceiling.getLocation(), ceiling.getBlockData().getSoundGroup().getStepSound(), 1.0f, 0.5f);
                }
            }
        }

        private static double flat(Location a, Location b) {
            return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
        }

        /** Mod {@code calculateDistanceToCeiling}: the first block above with a sturdy underside, from the winch's top. */
        private static double ceilingDistance(LivingEntity entity) {
            Block block = entity.getLocation().getBlock();
            World world = entity.getWorld();
            int max = Math.min(world.getMaxHeight() - 1, block.getY() + MAX_SCAN);
            while (block.getY() < max && !block.getBlockData().isFaceSturdy(BlockFace.DOWN, BlockSupport.FULL)) {
                block = block.getRelative(BlockFace.UP);
            }
            return BoundroidWinchRules.ceilingDistance(block.getY(), entity.getBoundingBox().getMaxY());
        }

        /** Mod {@code hasBlockAbove}: something solid over the winch's eyes. */
        private static boolean blockAbove(LivingEntity entity) {
            Block block = entity.getEyeLocation().getBlock();
            int max = Math.min(entity.getWorld().getMaxHeight() - 1, block.getY() + MAX_SCAN);
            for (int y = block.getY(); y <= max; y++) {
                if (block.getWorld().getBlockAt(block.getX(), y, block.getZ()).getType().isSolid()) {
                    return true;
                }
            }
            return false;
        }

        /** The chain from the winch to the top of the head, one chain block display per link. */
        private void drawChain(LivingEntity entity, LivingEntity head) {
            Location from = entity.getLocation().add(0, 0.9, 0);
            Location to = head.getLocation().add(0, head.getHeight(), 0);
            Vector line = to.toVector().subtract(from.toVector());
            double length = line.length();
            int links = BoundroidWinchRules.links(length);
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

        /** Mod {@code hurt}: the winch takes nothing itself; the hit goes to the head, and the winch is dazed. */
        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.SUFFOCATION) {
                // What would end the winch on its own (the void, /kill, lava, being buried) still does: its head then
                // grows a new one.
                return;
            }
            event.setCancelled(true);
            LivingEntity head = head(entity);
            if (head == null || head.isDead() || !(event instanceof EntityDamageByEntityEvent byEntity)) {
                return;
            }
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            double before = head.getHealth();
            head.damage(event.getDamage(), damager);
            if (head.isDead() || head.getHealth() < before) {
                noLatchCooldown = BoundroidWinchRules.dazedTicks(ThreadLocalRandom.current().nextInt(BoundroidWinchRules.DAZED_RANDOM));
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

    /** Slime made of iron filings: HP 10 and damage 2 per head; merges with others up to 10 heads. */
    static final class Ferrouslime implements ModMobBehaviour {
        static final int MAX_HEADS = 10;
        private final MobAnimator animation = new MobAnimator();

        Ferrouslime(LivingEntity entity) {
            if (entity instanceof Slime slime) {
                heads(slime, Math.max(1, Math.min(slime.getSize(), 2)));
            }
        }

        static void heads(Slime slime, int heads) {
            int clamped = Math.max(1, Math.min(MAX_HEADS, heads));
            slime.setSize(clamped);
            // setSize resets movement speed by size; the mod keeps a flat 0.35.
            AttributeInstance speed = slime.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.35);
            }
            AttributeInstance health = slime.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(stat(clamped, 10, 10, 100));
                slime.setHealth(health.getValue());
            }
            AttributeInstance damage = slime.getAttribute(Attribute.ATTACK_DAMAGE);
            if (damage != null) {
                damage.setBaseValue(stat(clamped, 2, 2, 10));
            }
        }

        /** Mod formula: {@code clamp(heads * perHead, min, max)}. */
        static double stat(int heads, double perHead, double min, double max) {
            return Math.max(min, Math.min(max, heads * perHead));
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            animation.update(entity, context, ticks, null);
            if (!(entity instanceof Slime slime) || slime.getSize() >= MAX_HEADS || ThreadLocalRandom.current().nextInt(10) != 0) {
                return;
            }
            for (Entity nearby : entity.getNearbyEntities(0.8, 0.8, 0.8)) {
                if (nearby instanceof Slime other && other.isValid() && !other.equals(slime)
                    && context.mobs().typeOf(other).filter(type -> type == FERROUSLIME).isPresent()
                    && other.getSize() <= slime.getSize()) {
                    heads(slime, slime.getSize() + other.getSize());
                    other.remove();
                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 0.6f);
                    return;
                }
            }
        }
    }

    /** Harmless scanner: marks nearby players and sends the cave's monsters after them. */
    static final class Notor implements ModMobBehaviour {
        private static final double SCAN_RANGE = 12.0;
        private static final double ALERT_RANGE = 24.0;
        private final MobAnimator animation = new MobAnimator();
        private int cooldown = 40;

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            animation.update(entity, context, ticks, entity.isOnGround() ? "ground" : null);
            cooldown -= ticks;
            if (cooldown > 0) {
                return;
            }
            cooldown = 60;
            for (Entity nearby : entity.getNearbyEntities(SCAN_RANGE, SCAN_RANGE, SCAN_RANGE)) {
                if (nearby instanceof Player player && player.getGameMode() != GameMode.CREATIVE
                    && player.getGameMode() != GameMode.SPECTATOR && entity.hasLineOfSight(player)) {
                    scan(entity, player, context);
                    return;
                }
            }
        }

        private void scan(LivingEntity notor, Player player, ModMobContext context) {
            notor.getWorld().spawnParticle(Particle.GLOW, player.getLocation().add(0, 1, 0), 15, 0.4, 0.8, 0.4, 0);
            notor.getWorld().playSound(notor.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.6f, 2.0f);
            for (Entity nearby : notor.getNearbyEntities(ALERT_RANGE, ALERT_RANGE, ALERT_RANGE)) {
                if (nearby instanceof Mob mob && mob.getTarget() == null
                    && context.mobs().typeOf(mob).filter(type -> type.category() == SpawnCategory.MONSTER).isPresent()) {
                    mob.setTarget(player);
                }
            }
        }
    }
}
