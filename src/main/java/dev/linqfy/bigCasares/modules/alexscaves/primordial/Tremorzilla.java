package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.damage.ModDamageTypes;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.TremorzillaRules.Anim;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.NuclearExplosion;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.NukeMath;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * The Tremorzilla (mod {@code TremorzillaEntity}, {@code TremorzillaAttackGoal}, {@code TremorzillaWanderGoal},
 * {@code TremorzillaFollowOwnerGoal}, {@code TremorzillaPartEntity} and {@code TremorzillaEggBlock}): a 500-health
 * radioactive kaiju, 4.5 by 11 blocks. It never spawns by itself: its egg (crafted from a tremorsaurus egg, an immortal
 * embryo and tectonic shards) only hatches when a nuclear explosion carves the block it lies on.
 *
 * <p><b>Fighting.</b> What hurts it (or its owner's foe) is chased; near it, it scratches, stomps, whips its tail or bites
 * (10 damage each, breaking blocks around the blow), and roars, scaring every mob within 64 blocks. When its atomic charge
 * is full it may decide (1 in 100 a tick) to breathe: its six back spikes light one by one, it rears up and fires a
 * 100-block beam for 100-249 ticks that sweeps around its target, hurting (20, radiation, Irradiated III for 5 minutes)
 * and blasting blocks along it and detonating any nuclear bomb it touches; then it recharges for 1000 ticks. It heals
 * steadily (faster when hurt), eats Irradiated as health, takes 35 % from explosions, nothing from nukes or rayguns.
 *
 * <p><b>Taming.</b> Fed waste drums, from the fourth one it is tamed 1 in 3. The owner sneak-clicks to cycle wander, sit
 * and follow, and clicks to ride it (not a baby): it walks where the rider looks, jump fires the beam once charged
 * (the mod's beam key) and swinging attacks (the mod's attack key). Nuclear bombs make two of them lay an egg.
 *
 * <p><b>Body.</b> A giant without AI goals (the closest vanilla hitbox, scaled to 11 blocks), moved by its pathfinder and by
 * this class; the five tail segments are {@link Interaction} parts that forward hits to the body. Blocks are only changed
 * through {@link EntityExplodeEvent} (protection plugins and the module's own block drops see them) and only while
 * {@code mobGriefing} is on, as in the mod.
 */
public final class Tremorzilla {
    static final String ID = "tremorzilla";
    /** Giant hitbox 3.6 by 12: scaled to the mod's 11 blocks high. */
    static final double GIANT_SCALE = TremorzillaRules.HEIGHT / 12.0;

    /** Mod {@code tremorzilla.json}: 20-40 uranium and 10-20 uranium shards, each with Looting +0-3 a level. */
    static final List<MobDrop> LOOT = List.of(MobDrop.withLooting("ac_uranium", 20, 40, 3),
        MobDrop.withLooting("ac_uranium_shard", 10, 20, 3));

    public static final ModMobType TREMORZILLA = new ModMobType(ID, CaveBiomeId.TOXIC_CAVES, "giant",
        SpawnCategory.CAVE_CREATURE,
        Map.of("max_health", TremorzillaRules.MAX_HEALTH, "armor", TremorzillaRules.ARMOR,
            "attack_damage", TremorzillaRules.ATTACK_DAMAGE, "movement_speed", TremorzillaRules.MOVEMENT_SPEED,
            "follow_range", TremorzillaRules.FOLLOW_RANGE, "knockback_resistance", 1.0,
            "step_height", TremorzillaRules.STEP_HEIGHT),
        "alexscaves_tremorzilla", LOOT).asBoss();

    /** Mod tail parts (width, height) and offsets from the one before (the first from the body centre). */
    static final double[][] PART_SIZES = {{3.0, 3.0}, {2.5, 2.0}, {2.5, 1.5}, {2.5, 1.5}, {2.0, 1.0}};
    static final double[][] PART_OFFSETS = {{0.0, -4.0, -3.5}, {0.0, -0.25, -3.25}, {0.0, 0.0, -2.5}, {0.0, 0.0, -2.5},
        {0.0, 0.0, -2.0}};

    static final NamespacedKey PART_OF = new NamespacedKey("bigcasares", "ac_tremorzilla_part");
    static final NamespacedKey CHARGE = new NamespacedKey("bigcasares", "ac_tremorzilla_charge");
    static final NamespacedKey SPIKES = new NamespacedKey("bigcasares", "ac_tremorzilla_spikes");
    static final NamespacedKey BEAM_TRIGGER = new NamespacedKey("bigcasares", "ac_tremorzilla_beam_trigger");
    static final NamespacedKey TAME_ATTEMPTS = new NamespacedKey("bigcasares", "ac_tremorzilla_tame_attempts");
    static final NamespacedKey COMMAND = new NamespacedKey("bigcasares", "ac_tremorzilla_command");

    private static BlockData nuclearBomb;

    private Tremorzilla() {
    }

    public static void register(ModMobCatalog catalog, BlockData nuclearBombBlock) {
        nuclearBomb = nuclearBombBlock;
        catalog.register(TREMORZILLA, (entity, type) -> {
            if (entity instanceof Mob mob) {
                Bukkit.getMobGoals().removeAllGoals(mob);
                mob.setDespawnInPeacefulOverride(TriState.FALSE);
            }
            return new Behaviour();
        });
        // Mod: no natural spawn; it only hatches from its egg.
        catalog.spawnRule(ID, (block, random) -> false);
    }

    /** The tremorzilla an {@code Interaction} tail part belongs to. */
    static Optional<UUID> partOwner(Entity entity) {
        String id = entity.getPersistentDataContainer().get(PART_OF, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
    }

    // --- nuclear hatching ---

    /**
     * Mod {@code NuclearExplosionEntity}: a tremorzilla egg in the carved crater hatches instead of being destroyed. The
     * baby is wild (the mod's tremorzilla does not tame from hatching) and grows up in 20 minutes like other dinosaurs.
     *
     * @param inCrater whether a block is inside the crater the explosion carves
     */
    public static void hatchEggsInCrater(ModMobService mobs, World world, BoundingBox cube, Predicate<Block> inCrater) {
        NamespacedKey eggKey = new NamespacedKey(mobs.plugin(), "alexscaves_egg");
        NamespacedKey linkKey = new NamespacedKey(mobs.plugin(), "alexscaves_egg_link");
        NamespacedKey babyKey = new NamespacedKey(mobs.plugin(), TamingRules.BABY_UNTIL_KEY);
        for (Entity found : world.getNearbyEntities(cube, entity -> entity instanceof ItemDisplay
            && ID.equals(entity.getPersistentDataContainer().get(eggKey, PersistentDataType.STRING)))) {
            Block at = found.getLocation().getBlock();
            if (!inCrater.test(at)) {
                continue;
            }
            Location spot = at.getLocation().add(0.5, 0, 0.5);
            Optional<LivingEntity> spawned = mobs.spawn(ID, spot);
            if (spawned.isEmpty()) {
                // Peaceful or a cancelled spawn: the egg stays where it was rather than being lost.
                continue;
            }
            LivingEntity baby = spawned.get();
            baby.getPersistentDataContainer().set(babyKey, PersistentDataType.LONG, world.getGameTime() + TamingRules.BABY_TICKS);
            applyScale(baby, true);
            removeLink(mobs, found, linkKey);
            found.remove();
            world.playSound(spot, Sound.BLOCK_SNIFFER_EGG_HATCH, 0.7F, 0.9F + ThreadLocalRandom.current().nextFloat() * 0.2F);
        }
    }

    private static void removeLink(ModMobService mobs, Entity egg, NamespacedKey linkKey) {
        String link = egg.getPersistentDataContainer().get(linkKey, PersistentDataType.STRING);
        if (link != null) {
            try {
                Entity box = mobs.plugin().getServer().getEntity(UUID.fromString(link));
                if (box != null) {
                    box.remove();
                }
            } catch (IllegalArgumentException ignored) {
                // A broken link: the box is left to the breeding listener.
            }
        }
    }

    static void applyScale(LivingEntity entity, boolean baby) {
        AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
        double wanted = GIANT_SCALE * (baby ? TremorzillaRules.BABY_SCALE : 1.0);
        if (scale != null && Math.abs(scale.getBaseValue() - wanted) > 1.0E-4) {
            scale.setBaseValue(wanted);
        }
    }

    // --- geometry ---

    /** Mod {@code rotateOffsetVec}: pitch then yaw, in the mod's sense. */
    static Vector rotateOffset(double x, double y, double z, double pitch, double yaw) {
        return new Vector(x, y, z).rotateAroundX(Math.toRadians(pitch)).rotateAroundY(Math.toRadians(-yaw));
    }

    /** A point {@code (x, y, z)} in the body's frame (z forward) at a body yaw. */
    static Vector local(double x, double y, double z, double yaw) {
        return new Vector(x, y, z).rotateAroundY(Math.toRadians(-yaw));
    }

    static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    static float approachDegrees(float current, float target, float maxChange) {
        float delta = target - current;
        delta = delta - 360.0F * (float) Math.floor((delta + 180.0F) / 360.0F);
        return current + Math.max(-maxChange, Math.min(maxChange, delta));
    }

    // --- behaviour ---

    static final class Behaviour implements ModMobBehaviour {
        private final Interaction[] parts = new Interaction[PART_SIZES.length];
        private final long[] lastHits = new long[PART_SIZES.length];
        private final Vector[] partCenters = new Vector[PART_SIZES.length];
        private ModMobContext context;
        private boolean loaded;

        private final PrimordialAnimator animator = new PrimordialAnimator();
        private boolean speakPending;
        private boolean dancing;
        private Anim animation = Anim.NONE;
        private int animationTick;
        private int age;
        private float lastYawBeforeWhip;
        private float tailPitch;
        private float tailYaw;
        private final float[] yawBuffer = new float[64];
        private int yawPointer = -1;

        private int charge = TremorzillaRules.MAX_CHARGE;
        private float spikes;
        private boolean wantsBeam;
        private boolean firing;
        private int beamTime;
        private int maxBeamTime = 200;
        private double maxBeamBreakLength = 100.0;
        private Vector beamTarget;
        private Vector beamEnd;
        private int chargeSoundCooldown;
        private int beamSoundClock;
        private int timeWithoutTarget;
        private int roarCooldown;
        private int blockBreakCounter;
        private int lastScare;

        private Location lastNavigatedTo;
        private Location wanderGoal;
        private Location lastPosition;
        private double walkPosition;
        private UUID lastFed;

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            this.context = context;
            if (!(entity instanceof Mob mob)) {
                return;
            }
            load(entity);
            int previousAge = age;
            age += ticks;
            boolean baby = baby(entity);
            applyScale(entity, baby);
            // Mod: an animal; it never despawns (the breeding listener lets grown wild dinosaurs go).
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            int previousTick = animationTick;
            advanceAnimation(ticks);
            boolean swimming = waterHeight(entity) > 2.0;
            if (swimming) {
                entity.setRemainingAir(entity.getMaximumAir());
            }
            Player rider = rider(entity);
            LivingEntity target = validTarget(mob);
            animator.track(entity, ticks);
            dancing = rider == null && target == null && !firing && animation == Anim.NONE && animator.dancing(entity, ticks);

            ensureParts(entity);
            forwardPartHits(entity);
            rotateBody(entity, ticks);
            tickTail(entity, ticks, swimming);
            walkStomps(entity, ticks, baby, swimming);
            animationEffects(entity, previousTick, baby);

            if (rider != null) {
                ride(mob, rider, ticks, swimming);
            } else if (!sitting(entity)) {
                if (target != null) {
                    attackGoal(mob, target, ticks, baby, swimming);
                } else if (command(entity) == 2 && owner(entity).isPresent()) {
                    followOwner(mob, ticks);
                } else if (!dancing) {
                    // Mod travel(): a dancing dinosaur stands still.
                    wander(mob, ticks, swimming);
                }
                if (swimming) {
                    swim(mob, target);
                }
            }
            if (animation.tail() || animation.stomp() || firing && rider == null) {
                // Mod travel: standing still while whipping, stomping or breathing (unridden).
                mob.getPathfinder().stopPathfinding();
                Vector velocity = entity.getVelocity();
                entity.setVelocity(new Vector(0, velocity.getY(), 0));
            }

            tickBeam(mob, target, rider, ticks, baby);
            heal(entity, previousAge);
            eatRadiation(entity);
            if (blockBreakCounter > 0) {
                blockBreakCounter -= ticks;
            } else if (griefing(entity) && !baby) {
                breakBlocksInBoundingBox(entity, swimming);
                blockBreakCounter = 10;
            }
            if (roarCooldown > 0) {
                roarCooldown = Math.max(0, roarCooldown - ticks);
            }
            if (chargeSoundCooldown > 0) {
                chargeSoundCooldown -= ticks;
            }
            chew(entity, previousTick);
            if (speakPending && animation == Anim.NONE && !firing) {
                // Mod TremorzillaEntity.getAmbientSound: speaking starts the speak animation.
                setAnimation(entity, Anim.SPEAK);
            }
            speakPending = false;
            animator.base(context, entity, PrimordialPose.baseLoop(PrimordialPose.Kind.TREMORZILLA,
                new PrimordialPose.State(sitting(entity), dancing, false, false, false, false, swimming, firing,
                    entity.isOnGround(), animator.moving())));
            followParts(entity);
            save(entity);
        }

        // --- persistence ---

        private void load(LivingEntity entity) {
            if (loaded) {
                return;
            }
            loaded = true;
            PersistentDataContainer data = entity.getPersistentDataContainer();
            charge = data.getOrDefault(CHARGE, PersistentDataType.INTEGER, TremorzillaRules.MAX_CHARGE);
            spikes = data.getOrDefault(SPIKES, PersistentDataType.FLOAT, 0.0F);
            wantsBeam = data.getOrDefault(BEAM_TRIGGER, PersistentDataType.BYTE, (byte) 0) == 1;
            lastYawBeforeWhip = entity.getLocation().getYaw();
        }

        private void save(LivingEntity entity) {
            PersistentDataContainer data = entity.getPersistentDataContainer();
            data.set(CHARGE, PersistentDataType.INTEGER, charge);
            data.set(SPIKES, PersistentDataType.FLOAT, spikes);
            data.set(BEAM_TRIGGER, PersistentDataType.BYTE, (byte) (wantsBeam ? 1 : 0));
        }

        // --- animations ---

        private boolean setAnimation(LivingEntity entity, Anim next) {
            if (animation != Anim.NONE) {
                return false;
            }
            animation = next;
            animationTick = 0;
            if (next.tail() || next.stomp()) {
                lastYawBeforeWhip = entity.getLocation().getYaw();
            }
            // The tail whips have no model animation (the mod swings the whole body).
            String name = PrimordialPose.tremorzilla(next);
            if (name != null) {
                animator.action(context, entity, name);
            }
            return true;
        }

        @Override
        public String soundSuffix(LivingEntity entity, String suffix) {
            if (suffix.equals("idle")) {
                speakPending = true;
            }
            return suffix;
        }

        private void advanceAnimation(int ticks) {
            if (animation != Anim.NONE) {
                animationTick += ticks;
                if (animationTick >= animation.duration) {
                    animation = Anim.NONE;
                    animationTick = 0;
                }
            }
        }

        /** Mod {@code tick}: the blows land on their animation ticks, with the sound at tick 2. */
        private void animationEffects(LivingEntity entity, int previous, boolean baby) {
            int now = animationTick;
            Location at = entity.getLocation();
            float yaw = at.getYaw();
            double scale = baby ? TremorzillaRules.BABY_SCALE : 1.0;
            if (TremorzillaRules.crossed(previous, now, 2)) {
                String sound = animation.scratch() ? "tremorzilla_scratch_attack" : animation.stomp() ? "tremorzilla_stomp_attack"
                    : animation.tail() ? "tremorzilla_tail_attack" : animation == Anim.BITE ? "tremorzilla_bite" : null;
                if (sound != null) {
                    play(entity, sound, 4.0F, pitch());
                }
            }
            if (animation.scratch() && TremorzillaRules.crossed(previous, now, 18)) {
                Vector center = local(0, 5.0 * scale, 6.0 * scale, yaw).add(at.toVector());
                hurtAround(entity, center, 6.0, TremorzillaRules.MELEE_DAMAGE, 2.0, false, true, true);
                breakAround(entity, center, 3.0, false, 0.6F);
            }
            if (animation.tail() && now >= 10 && now < 25) {
                double[] radii = {4.0, 4.0, 4.0, 3.0, 3.0};
                double[] breaks = {2.0, 2.0, 2.0, 1.0, 1.0};
                for (int i = 0; i < partCenters.length; i++) {
                    if (partCenters[i] != null) {
                        hurtAround(entity, partCenters[i], radii[i], TremorzillaRules.MELEE_DAMAGE, 2.0, false, true, true);
                        breakAround(entity, partCenters[i], breaks[i], false, 0.6F);
                    }
                }
            }
            if (animation.stomp() && TremorzillaRules.crossed(previous, now, 18)) {
                stompEffect(entity, animation == Anim.LEFT_STOMP, 2.0F, 5.0F, 1.2F, 10.0F, scale);
            }
            if (animation == Anim.BITE && TremorzillaRules.crossed(previous, now, 10)) {
                Vector center = local(0, 7.0 * scale, 5.0 * scale, yaw).add(at.toVector());
                hurtAround(entity, center, 7.5, TremorzillaRules.MELEE_DAMAGE, 2.0, false, true, true);
                breakAround(entity, center, 4.0, false, 0.6F);
            }
            if (animation == Anim.ROAR_1 && now > 10 && now < 50 || animation == Anim.ROAR_2 && now > 15 && now < 50) {
                scareMobs(entity);
            }
            if (animation == Anim.CHEW && now <= 30 && previous / 6 != now / 6) {
                play(entity, "tremorzilla_eat", 4.0F, pitch());
                Location mouth = at.clone().add(local(0, 7.5 * scale, 2.5 * scale, yaw));
                entity.getWorld().spawnParticle(Particle.BLOCK, mouth, 8, 0.6, 0.3, 0.6, 0, Material.LIME_CONCRETE.createBlockData());
            }
        }

        /** Mod body rotation: the tail whip spins the body 170 degrees and back; the beam keeps it facing its end. */
        private void rotateBody(LivingEntity entity, int ticks) {
            Location at = entity.getLocation();
            float yaw = at.getYaw();
            if (animation.tail()) {
                float negative = animation == Anim.RIGHT_TAIL ? -1.0F : 1.0F;
                float target;
                if (animationTick < 5) {
                    target = animationTick / 5.0F * -10.0F;
                } else {
                    target = Math.max(0.0F, Math.min(1.0F, (animationTick - 10) / 15.0F)) * 170.0F;
                }
                float wanted = animationTick > 25 ? lastYawBeforeWhip : lastYawBeforeWhip + negative * target;
                float speed = animationTick > 25 ? 15.0F : 70.0F;
                entity.setRotation(approachDegrees(yaw, wanted, speed * ticks), at.getPitch());
            } else if (firing && beamEnd != null) {
                Vector shoot = shootFrom(entity);
                float beamYaw = yawTo(beamEnd.getX() - shoot.getX(), beamEnd.getZ() - shoot.getZ());
                float delta = Math.abs(approachDegrees(0, beamYaw - yaw, 360.0F));
                if (delta > 80.0F) {
                    entity.setRotation(approachDegrees(yaw, beamYaw, 10.0F * ticks), at.getPitch());
                    lastYawBeforeWhip = beamYaw;
                }
            } else {
                lastYawBeforeWhip = yaw;
            }
        }

        // --- tail ---

        /** Mod {@code tickMultipart}: the tail lags behind the body's turns, sways, and lifts while whipping. */
        private void tickTail(LivingEntity entity, int ticks, boolean swimming) {
            Location at = entity.getLocation();
            float bodyYaw = at.getYaw();
            if (yawPointer == -1) {
                java.util.Arrays.fill(yawBuffer, bodyYaw);
                yawPointer = 0;
            }
            for (int i = 0; i < ticks; i++) {
                yawPointer = (yawPointer + 1) % yawBuffer.length;
                yawBuffer[yawPointer] = bodyYaw;
            }
            float speed = (animation.tail() ? 25.0F : swimming ? 20.0F : 5.0F) * ticks;
            float targetPitch = animation.tail() && animationTick > 10 ? 45.0F : 0.0F;
            float targetYaw;
            if (sitting(entity)) {
                targetYaw = 90.0F;
            } else if (animation.tail()) {
                targetYaw = animationTick > 15 ? -70.0F : 70.0F;
            } else {
                int back = swimming ? 2 : 20;
                float lagged = yawBuffer[Math.floorMod(yawPointer - back, yawBuffer.length)];
                targetYaw = approachDegrees(0, lagged - bodyYaw, 360.0F) + (float) (Math.sin(age * 0.03) * 10.0)
                    + (swimming ? (float) (Math.sin(age * 0.4) * 25.0) : 0.0F);
            }
            tailPitch = approachDegrees(tailPitch, targetPitch, speed) % 360.0F;
            tailYaw = approachDegrees(tailYaw, targetYaw, speed) % 360.0F;
            double scale = baby(entity) ? TremorzillaRules.BABY_SCALE : 1.0;
            Vector center = at.toVector().add(new Vector(0, entity.getHeight() * 0.5, 0));
            float pitchStep = tailPitch / 5.0F;
            float yawStep = tailYaw / 5.0F;
            Vector previous = center;
            for (int i = 0; i < partCenters.length; i++) {
                double[] offset = PART_OFFSETS[i];
                double y = i == 0 && swimming ? 0.0 : offset[1];
                Vector step = rotateOffset(offset[0] * scale, y * scale, offset[2] * scale, pitchStep, bodyYaw + yawStep * (i + 1));
                partCenters[i] = previous.clone().add(step);
                previous = partCenters[i];
            }
        }

        private void ensureParts(LivingEntity entity) {
            if (partCenters[0] == null) {
                return;
            }
            double scale = baby(entity) ? TremorzillaRules.BABY_SCALE : 1.0;
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null && parts[i].isValid()) {
                    continue;
                }
                Location spot = partLocation(entity.getWorld(), i, scale);
                if (!spot.isChunkLoaded()) {
                    continue;
                }
                double width = PART_SIZES[i][0] * scale;
                double height = PART_SIZES[i][1] * scale;
                parts[i] = entity.getWorld().spawn(spot, Interaction.class, part -> {
                    part.setInteractionWidth((float) width);
                    part.setInteractionHeight((float) height);
                    part.setResponsive(true);
                    part.setPersistent(false);
                    part.getPersistentDataContainer().set(PART_OF, PersistentDataType.STRING, entity.getUniqueId().toString());
                });
                lastHits[i] = 0L;
            }
        }

        private Location partLocation(World world, int index, double scale) {
            Vector center = partCenters[index];
            return new Location(world, center.getX(), center.getY() - PART_SIZES[index][1] * scale * 0.5, center.getZ());
        }

        private void followParts(LivingEntity entity) {
            double scale = baby(entity) ? TremorzillaRules.BABY_SCALE : 1.0;
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null && parts[i].isValid() && partCenters[i] != null) {
                    parts[i].teleport(partLocation(entity.getWorld(), i, scale));
                    float width = (float) (PART_SIZES[i][0] * scale);
                    if (Math.abs(parts[i].getInteractionWidth() - width) > 1.0E-3) {
                        parts[i].setInteractionWidth(width);
                        parts[i].setInteractionHeight((float) (PART_SIZES[i][1] * scale));
                    }
                }
            }
        }

        /** Mod {@code MultipartEntityMessage}: hitting the tail hurts the tremorzilla. */
        private void forwardPartHits(LivingEntity entity) {
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] == null || !parts[i].isValid()) {
                    continue;
                }
                Interaction.PreviousInteraction attack = parts[i].getLastAttack();
                if (attack == null || attack.getTimestamp() == lastHits[i]) {
                    continue;
                }
                lastHits[i] = attack.getTimestamp();
                if (attack.getPlayer() instanceof Player player && player.isOnline() && !entity.getPassengers().contains(player)) {
                    player.attack(entity);
                }
            }
        }

        // --- goals ---

        /** Mod {@code TremorzillaAttackGoal}. */
        private void attackGoal(Mob mob, LivingEntity target, int ticks, boolean baby, boolean swimming) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double distance = mob.getLocation().distance(target.getLocation());
            double combined = TremorzillaRules.WIDTH * scale(mob) + target.getWidth();
            if (!firing) {
                if (charged() && !wantsBeam && !baby && !sitting(mob) && random.nextDouble() < TremorzillaRules.chanceOver(100, ticks)) {
                    wantsBeam = true;
                }
                if (!wantsBeam && !animation.tail() && !animation.stomp()) {
                    Location eye = target.getEyeLocation();
                    Location at = mob.getLocation();
                    mob.setRotation(approachDegrees(at.getYaw(), yawTo(eye.getX() - at.getX(), eye.getZ() - at.getZ()),
                        30.0F * ticks), at.getPitch());
                }
                if (lastNavigatedTo == null || !mob.getPathfinder().hasPath() && distance > combined + 1.0
                    || lastNavigatedTo.getWorld() != target.getWorld()
                    || lastNavigatedTo.distance(target.getLocation()) > TremorzillaRules.WIDTH - 1.0) {
                    mob.getPathfinder().moveTo(target, 1.0);
                    lastNavigatedTo = target.getLocation();
                }
            }
            if (distance < combined + 3.0 && !firing && animation == Anim.NONE) {
                setAnimation(mob, TremorzillaRules.meleeChoice(random.nextFloat(), random.nextBoolean(), swimming, baby));
            }
            if (!wantsBeam && !baby) {
                tryRoar(mob);
            }
        }

        /** Mod {@code TremorzillaFollowOwnerGoal}: from 14 blocks it walks to its owner until 6; past 32 it teleports. */
        private void followOwner(Mob mob, int ticks) {
            Player owner = owner(mob).map(id -> mob.getServer().getPlayer(id)).orElse(null);
            if (owner == null || owner.getWorld() != mob.getWorld() || owner.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            double distanceSquared = mob.getLocation().distanceSquared(owner.getLocation());
            if (distanceSquared >= 1024.0) {
                teleportNear(mob, owner);
            } else if (distanceSquared >= 14.0 * 14.0 || mob.getPathfinder().hasPath() && distanceSquared > 36.0) {
                if (age % 10 < ticks) {
                    mob.getPathfinder().moveTo(owner, 1.0);
                }
            } else if (distanceSquared <= 36.0) {
                mob.getPathfinder().stopPathfinding();
            }
        }

        /** Mod {@code teleportToOwner}: 10 tries within 10 blocks (not within 6) on walkable ground with room. */
        private void teleportNear(Mob mob, Player owner) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location base = owner.getLocation();
            for (int i = 0; i < 10; i++) {
                int dx = random.nextInt(21) - 10;
                int dy = random.nextInt(3) - 1;
                int dz = random.nextInt(21) - 10;
                if (Math.abs(dx) < 6 && Math.abs(dz) < 6) {
                    continue;
                }
                Block feet = base.getBlock().getRelative(dx, dy, dz);
                if (!feet.getRelative(0, -1, 0).getType().isSolid() || !roomFor(mob, feet)) {
                    continue;
                }
                mob.teleport(feet.getLocation().add(0.5, 0, 0.5).setDirection(mob.getLocation().getDirection()));
                mob.getPathfinder().stopPathfinding();
                return;
            }
        }

        private static boolean roomFor(Mob mob, Block feet) {
            int half = (int) Math.ceil(mob.getWidth() / 2.0);
            int height = (int) Math.ceil(mob.getHeight());
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    for (int y = 0; y < height; y++) {
                        if (!feet.getRelative(x, y, z).isPassable()) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        /** Mod {@code TremorzillaWanderGoal}: 1 in 40 a tick on land (always when swimming) it heads to a new spot. */
        private void wander(Mob mob, int ticks, boolean swimming) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (wanderGoal != null) {
                if (wanderGoal.getWorld() != mob.getWorld() || mob.getLocation().distanceSquared(wanderGoal) <= 8.0
                    || !swimming && !mob.getPathfinder().hasPath()) {
                    wanderGoal = null;
                }
                return;
            }
            if (!swimming && random.nextDouble() >= TremorzillaRules.chanceOver(40, ticks)) {
                if (mob.getVelocity().clone().setY(0).lengthSquared() < 0.0025 && animation == Anim.NONE && !firing
                    && random.nextDouble() < TremorzillaRules.chanceOver(800, ticks)) {
                    tryRoar(mob);
                }
                return;
            }
            boolean land = swimming ? random.nextFloat() < 0.1F : random.nextFloat() > 0.1F;
            Location goal = land ? landTarget(mob, random) : null;
            if (goal == null) {
                goal = waterTarget(mob, random);
            }
            if (goal != null) {
                wanderGoal = goal;
                mob.getPathfinder().moveTo(goal, 1.0);
            }
        }

        /** Mod {@code LandRandomPos.getPos(this, 30, 8)}: a standable spot within 30 blocks and 8 up or down. */
        private static Location landTarget(Mob mob, ThreadLocalRandom random) {
            Location at = mob.getLocation();
            for (int i = 0; i < 10; i++) {
                Block block = at.getBlock().getRelative(random.nextInt(61) - 30, random.nextInt(17) - 8, random.nextInt(61) - 30);
                for (int down = 0; down < 8 && block.isPassable() && block.getY() > mob.getWorld().getMinHeight(); down++) {
                    block = block.getRelative(0, -1, 0);
                }
                Block feet = block.getRelative(0, 1, 0);
                if (block.getType().isSolid() && feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
            return null;
        }

        /** Mod {@code findWaterBlock(20)}: 15 tries for water in sight within 10 blocks. */
        private static Location waterTarget(Mob mob, ThreadLocalRandom random) {
            Location at = mob.getLocation();
            for (int i = 0; i < 15; i++) {
                Block block = at.getBlock().getRelative(random.nextInt(20) - 10, random.nextInt(20) - 10, random.nextInt(20) - 10);
                if (block.getType() == Material.WATER && block.getY() > mob.getWorld().getMinHeight() + 1) {
                    return block.getLocation().add(0.5, 0.5, 0.5);
                }
            }
            return null;
        }

        /** The giant base walks on the bottom: in deep water it is pushed to where it is going (mod swim control 0.8). */
        private void swim(Mob mob, LivingEntity target) {
            Location goal = target != null ? target.getLocation() : wanderGoal;
            if (goal == null || goal.getWorld() != mob.getWorld()) {
                return;
            }
            Vector toward = goal.toVector().subtract(mob.getLocation().toVector());
            if (toward.lengthSquared() < 4.0) {
                return;
            }
            Vector push = toward.normalize().multiply(TremorzillaRules.MOVEMENT_SPEED * 0.8);
            mob.setVelocity(push);
            mob.setRotation(yawTo(toward.getX(), toward.getZ()), 0);
        }

        // --- riding ---

        /** Mod {@code tickRidden} and {@code getRiddenInput}: forward at 0.8 (half backwards), strafe 0.35, climb by look under water. */
        private void ride(Mob mob, Player rider, int ticks, boolean swimming) {
            mob.setTarget(null);
            org.bukkit.Input input = rider.getCurrentInput();
            if (input.isJump()) {
                riderBeam(mob, rider);
            }
            if (animation.tail() || animation.stomp()) {
                return;
            }
            double forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
            double strafe = (input.isLeft() ? 1 : 0) - (input.isRight() ? 1 : 0);
            if (forward == 0 && strafe == 0) {
                return;
            }
            Location look = rider.getLocation();
            mob.setRotation(look.getYaw(), look.getPitch() * 0.25F);
            double f = forward < 0 ? 0.5 : 1.0;
            // Vanilla ground movement settles at about 2.16 times the speed attribute a tick.
            double speed = TremorzillaRules.MOVEMENT_SPEED * 2.16;
            Vector move = local(strafe * 0.35, 0, forward * 0.8 * f, look.getYaw()).multiply(speed);
            double y = swimming ? look.getDirection().getY() * speed : mob.getVelocity().getY();
            mob.setVelocity(new Vector(move.getX(), y, move.getZ()));
        }

        /** Mod key 2: with a full charge and no animation, it rears up to fire for 200 ticks where the rider looks. */
        void riderBeam(Mob mob, Player rider) {
            if (charge >= TremorzillaRules.MAX_CHARGE && animation == Anim.NONE && !wantsBeam && !baby(mob)) {
                mob.setRotation(rider.getLocation().getYaw(), 0);
                wantsBeam = true;
                maxBeamTime = 200;
            }
        }

        /** Mod key 3: a scratch, stomp or bite where the rider looks. */
        void riderAttack(Mob mob, Player rider) {
            if (animation != Anim.NONE) {
                return;
            }
            mob.setRotation(rider.getLocation().getYaw(), rider.getLocation().getPitch());
            ThreadLocalRandom random = ThreadLocalRandom.current();
            setAnimation(mob, TremorzillaRules.riderChoice(random.nextFloat(), random.nextBoolean(), waterHeight(mob) > 2.0));
        }

        // --- beam ---

        private void tickBeam(Mob mob, LivingEntity target, Player rider, int ticks, boolean baby) {
            if (target != null) {
                timeWithoutTarget = 0;
            } else {
                timeWithoutTarget += ticks;
            }
            if (wantsBeam && (timeWithoutTarget > 100 && rider == null || sitting(mob))) {
                wantsBeam = false;
            }
            if (!firing) {
                if (wantsBeam && charged()) {
                    if (animation == Anim.PREPARE_BREATH && TremorzillaRules.spikesReady(spikes) && animationTick > 15) {
                        maxBeamTime = rider != null ? maxBeamTime : TremorzillaRules.maxBeamTime(ThreadLocalRandom.current().nextInt(150));
                        beamTarget = initialBeamTarget(mob, target, rider);
                        beamTime = 0;
                        firing = true;
                        maxBeamBreakLength = 100.0;
                    }
                    if (TremorzillaRules.spikesReady(spikes) && animation == Anim.NONE && !stunned(mob)) {
                        setAnimation(mob, Anim.PREPARE_BREATH);
                        play(mob, "tremorzilla_beam_start", 8.0F, 1.0F);
                    }
                    float before = spikes;
                    spikes = Math.min(spikes + 0.005F * ticks, 1.0F);
                    if (TremorzillaRules.chargeBeep(before, spikes) && chargeSoundCooldown <= 0) {
                        float shown = TremorzillaRules.spikesDownAmount(spikes, 6.0F);
                        play(mob, shown > 4.0F ? "tremorzilla_charge_complete" : "tremorzilla_charge_normal", 8.0F, 0.7F + spikes * 0.7F);
                        chargeSoundCooldown = 19;
                    }
                    if (age % 40 < ticks) {
                        // Mod: nuclear sirens within 256 blocks blare (the module has no siren block yet): its sound.
                        mob.getWorld().playSound(mob.getLocation(), "alexscaves:nuclear_siren", SoundCategory.HOSTILE, 6.0F, 1.0F);
                    }
                } else {
                    spikes = Math.max(spikes - 0.05F * ticks, 0.0F);
                }
            } else {
                wantsBeam = false;
                double distance = target == null ? 0.0 : target.getLocation().distance(mob.getLocation());
                beamTime += TremorzillaRules.beamTimeStep(rider != null, target != null, distance) * ticks;
                if (beamTime > maxBeamTime) {
                    stopBeam(mob);
                    play(mob, "tremorzilla_beam_end", 8.0F, 1.0F);
                } else {
                    if (!stunned(mob)) {
                        beamTargeting(mob, target, rider);
                    }
                    charge = TremorzillaRules.MAX_CHARGE;
                    breath(mob, ticks);
                }
            }
            if (!charged()) {
                charge = Math.min(TremorzillaRules.MAX_CHARGE, charge + ticks);
                spikes = 0.0F;
            }
        }

        private void stopBeam(Mob mob) {
            beamTime = 0;
            firing = false;
            beamTarget = null;
            beamEnd = null;
            charge = 0;
            mob.getWorld().stopSound(net.kyori.adventure.sound.SoundStop.named(
                net.kyori.adventure.key.Key.key("alexscaves", "tremorzilla_beam_loop")));
        }

        /** Mod {@code createInitialBeamVec}. */
        private Vector initialBeamTarget(Mob mob, LivingEntity target, Player rider) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            float yaw = mob.getLocation().getYaw();
            if (target != null && rider == null) {
                Vector randomRot = new Vector(-100.0 + random.nextFloat() * 200.0, 0.0, 15.0 + 15.0 * random.nextFloat())
                    .rotateAroundY(Math.toRadians(-yaw + 50.0 - random.nextFloat() * 100.0));
                boolean kaiju = context.mobs().typeOf(target).map(type -> TremorzillaRules.KAIJU.contains(type.id())).orElse(false);
                Location position = kaiju ? target.getEyeLocation() : target.getLocation();
                return position.toVector().add(randomRot);
            }
            if (rider != null) {
                return shootFrom(mob).add(local(0, 0, 10.0, yaw));
            }
            return shootFrom(mob).add(local(0, random.nextBoolean() ? 100.0 : 20.0, 6.0, yaw));
        }

        /** Mod {@code tickBeamTargeting}: follows the rider's look, sweeps around the target, or swings ahead. */
        private void beamTargeting(Mob mob, LivingEntity target, Player rider) {
            Vector current = beamTarget == null ? mob.getLocation().toVector() : beamTarget;
            Vector shoot = shootFrom(mob);
            float bodyYaw = mob.getLocation().getYaw();
            Vector goal;
            double rate;
            if (rider != null) {
                goal = shoot.clone().add(rider.getLocation().getDirection().multiply(100.0));
                rate = 0.2;
            } else if (target != null) {
                float accuracy = TremorzillaRules.beamAccuracy(beamTime, maxBeamTime);
                Vector swing = new Vector(Math.sin(age * 0.2) * 6.0, 0, Math.cos(age * 0.2) * -6.0)
                    .rotateAroundY(Math.toRadians(-bodyYaw)).multiply(accuracy);
                goal = target.getLocation().toVector().add(swing);
                rate = 0.1;
            } else {
                goal = shoot.clone().add(new Vector(Math.sin(age * 0.1) * 10.0, current.getY() - shoot.getY(), 6.0)
                    .rotateAroundY(Math.toRadians(-bodyYaw)));
                rate = 0.1;
            }
            // Mod: a tenth (a fifth ridden) of the way a tick; here over two ticks.
            double kept = Math.pow(1.0 - rate, 2);
            beamTarget = goal.clone().subtract(current).multiply(1.0 - kept).add(current);
        }

        /** Mod {@code tickBreath} (server): the beam ends at the first block within 100; every 3 ticks it blasts along. */
        private void breath(Mob mob, int ticks) {
            World world = mob.getWorld();
            Vector from = shootFrom(mob);
            if (beamTarget != null) {
                Vector direction = beamTarget.clone().subtract(from);
                if (direction.lengthSquared() > 1.0E-6) {
                    direction.normalize();
                    RayTraceResult hit = world.rayTraceBlocks(from.toLocation(world), direction, 100.0, FluidCollisionMode.NEVER, true);
                    beamEnd = hit == null ? from.clone().add(direction.multiply(100.0)) : hit.getHitPosition();
                }
            }
            if (beamEnd == null) {
                return;
            }
            drawBeam(world, from, beamEnd);
            if ((beamSoundClock -= ticks) <= 0) {
                world.playSound(mob.getLocation(), "alexscaves:tremorzilla_beam_loop", SoundCategory.HOSTILE, 8.0F, 1.0F);
                beamSoundClock = 60;
            }
            int previous = beamTime - ticks;
            if (Math.floorDiv(previous, 3) == Math.floorDiv(beamTime, 3)) {
                return;
            }
            boolean broke = false;
            double furthest = 10.0;
            Vector view = beamEnd.clone().subtract(from);
            if (view.lengthSquared() < 1.0E-6) {
                return;
            }
            view.normalize();
            double scale = 5.0;
            Vector point = from.clone();
            for (double walk = 1.0; walk < maxBeamBreakLength; walk += scale) {
                point.add(view.clone().multiply(scale * 1.5));
                if (!broke) {
                    // Mod config devastating_tremorzilla_beam (true by default): the full 5-block radius.
                    broke = breakAround(mob, point, scale, true, 0.08F);
                    furthest = point.distance(from);
                }
                hurtAround(mob, point, scale + 1.0, TremorzillaRules.BEAM_DAMAGE, 1.0, true, true, false);
            }
            hurtAround(mob, beamEnd, 6.0, TremorzillaRules.BEAM_DAMAGE, 1.0, true, true, false);
            if (Math.floorDiv(previous, 6) != Math.floorDiv(beamTime, 6)) {
                breakAround(mob, beamEnd, 4.0, true, 0.08F);
            }
            if (broke) {
                maxBeamBreakLength = Math.max(furthest, maxBeamBreakLength - 5.0);
            }
        }

        private static void drawBeam(World world, Vector from, Vector to) {
            Vector line = to.clone().subtract(from);
            double length = line.length();
            if (length < 1.0E-3) {
                return;
            }
            line.normalize();
            Particle.DustOptions outer = new Particle.DustOptions(Color.fromRGB(0x6B, 0xF2, 0x2E), 3.0F);
            // Not forced: only players within the normal particle range receive them.
            for (double d = 0; d < length; d += 3.0) {
                Location at = from.clone().add(line.clone().multiply(d)).toLocation(world);
                world.spawnParticle(Particle.DUST, at, 1, 0.25, 0.25, 0.25, 0, outer, false);
                if (d % 12.0 < 3.0) {
                    world.spawnParticle(Particle.END_ROD, at, 1, 0.1, 0.1, 0.1, 0.01, null, false);
                }
            }
            world.spawnParticle(Particle.EXPLOSION, to.toLocation(world), 1, 1.5, 1.5, 1.5, 0, null, false);
            world.spawnParticle(Particle.ELECTRIC_SPARK, to.toLocation(world), 6, 1.5, 1.5, 1.5, 0.2, null, false);
        }

        /** Mod {@code getBeamShootFrom}: 7.5 blocks (times the scale) above the feet. */
        private static Vector shootFrom(LivingEntity entity) {
            return entity.getLocation().toVector().add(new Vector(0, 7.5 * scale(entity), 0));
        }

        // --- hurting ---

        /** Mod {@code hurtEntitiesAround}. */
        private boolean hurtAround(LivingEntity self, Vector center, double radius, float damage, double knockback,
                                   boolean radioactive, boolean hurtsKaiju, boolean stretchY) {
            World world = self.getWorld();
            // Mod: a plain cube (the results of its setMinY/setMaxY are discarded); stretchY only drops the height from
            // the distance check.
            BoundingBox box = BoundingBox.of(center, radius, radius, radius);
            Player rider = rider(self);
            boolean flag = false;
            for (Entity found : world.getNearbyEntities(box, entity -> entity instanceof LivingEntity)) {
                LivingEntity living = (LivingEntity) found;
                if (living.equals(self) || !living.isValid() || allied(self, living) || partOwner(living).isPresent()
                    || living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR)) {
                    continue;
                }
                Optional<String> id = context.mobs().typeOf(living).map(ModMobType::id);
                if (id.filter(ID::equals).isPresent()) {
                    continue;
                }
                Location at = living.getLocation();
                double dx = at.getX() - center.getX();
                // Mod: with stretchY the height is not counted.
                double dy = stretchY ? 0.0 : at.getY() - center.getY();
                double dz = at.getZ() - center.getZ();
                if (dx * dx + dy * dy + dz * dz > radius * radius) {
                    continue;
                }
                boolean kaiju = id.filter(TremorzillaRules.KAIJU::contains).isPresent();
                if (radioactive && !seenBy(world, center, living) || !hurtsKaiju && kaiju) {
                    continue;
                }
                if (rider != null && (living instanceof Player || living instanceof Tameable pet && pet.isTamed()
                    || owner(living).isPresent()) && !riderCanHarm(rider, living)) {
                    // Ridden, its blows are the rider's: players and pets the rider may not hurt stay unhurt.
                    continue;
                }
                double before = living.getHealth();
                DamageType damageType = radioactive ? context.damageTypes().type("tremorzilla_beam") : DamageType.MOB_ATTACK;
                living.damage(damage, org.bukkit.damage.DamageSource.builder(damageType)
                    .withDirectEntity(self).withCausingEntity(rider != null ? rider : self).build());
                if (living.getHealth() >= before && living.isValid() && !living.isDead()) {
                    continue;
                }
                flag = true;
                knock(self, living, knockback, !kaiju);
                if (radioactive) {
                    context.effects().apply(living, ModEffectType.IRRADIATED, TremorzillaRules.BEAM_IRRADIATED_AMPLIFIER,
                        TremorzillaRules.BEAM_IRRADIATED_TICKS);
                }
            }
            return flag;
        }

        /** Asks the protections whether the rider may hurt the victim (a stand-in hit by the rider). */
        private static boolean riderCanHarm(Player rider, LivingEntity victim) {
            org.bukkit.damage.DamageSource source = org.bukkit.damage.DamageSource.builder(DamageType.GENERIC)
                .withCausingEntity(rider).withDirectEntity(rider).build();
            return new EntityDamageByEntityEvent(rider, victim, EntityDamageEvent.DamageCause.CUSTOM, source, 1.0).callEvent();
        }

        /** Mod {@code canEntityBeHurtByBeam}: nothing solid between the blast and the target's middle. */
        private static boolean seenBy(World world, Vector center, LivingEntity living) {
            Vector middle = living.getLocation().toVector().add(new Vector(0, living.getHeight() * 0.5, 0));
            Vector line = middle.clone().subtract(center);
            double length = line.length();
            return length < 1.0E-3
                || world.rayTraceBlocks(center.toLocation(world), line.normalize(), length, FluidCollisionMode.NEVER, true) == null;
        }

        /** Mod {@code knockbackTarget}. */
        private static void knock(LivingEntity self, LivingEntity target, double strength, boolean ignoreResistance) {
            if (!ignoreResistance) {
                AttributeInstance resistance = target.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                strength *= 1.0 - (resistance == null ? 0.0 : resistance.getValue());
            }
            if (strength <= 0.0) {
                return;
            }
            Vector away = new Vector(self.getLocation().getX() - target.getLocation().getX(), 0,
                self.getLocation().getZ() - target.getLocation().getZ());
            if (away.lengthSquared() < 1.0E-6) {
                return;
            }
            away.normalize().multiply(strength);
            Vector velocity = target.getVelocity();
            double y = self.isOnGround() ? Math.min(0.4, velocity.getY() / 2.0 + strength) : velocity.getY();
            target.setVelocity(new Vector(velocity.getX() / 2.0 - away.getX(), y, velocity.getZ() / 2.0 - away.getZ()));
        }

        /** Mod {@code stompEffect}: dust of the ground under the foot and a blow around it. */
        private void stompEffect(LivingEntity entity, boolean left, float size, float hurtSize, float forwards, float damage, double scale) {
            float particleRadius = 0.3F + size * (float) scale;
            Location at = entity.getLocation();
            Vector center = at.toVector().add(local(left ? 2.2 : -2.2, 0, forwards, at.getYaw()));
            Block ground = center.toLocation(entity.getWorld()).getBlock().getRelative(0, -1, 0);
            if (ground.getType().isSolid()) {
                entity.getWorld().spawnParticle(Particle.BLOCK, center.toLocation(entity.getWorld()).add(0, 0.5, 0),
                    20 + (int) (particleRadius * 10), particleRadius, 0.2, particleRadius, 0.1, ground.getBlockData());
            }
            hurtAround(entity, center, particleRadius + hurtSize, damage, 0.5, false, false, false);
        }

        /** Mod walking: each footfall shakes, sounds (6, pitch 0.7) and stomps lightly (2 damage) at a brisk pace. */
        private void walkStomps(LivingEntity entity, int ticks, boolean baby, boolean swimming) {
            Location at = entity.getLocation();
            double moved = lastPosition == null || lastPosition.getWorld() != at.getWorld() ? 0.0
                : Math.hypot(at.getX() - lastPosition.getX(), at.getZ() - lastPosition.getZ()) / ticks;
            lastPosition = at.clone();
            double amount = Math.min(moved * (entity.getPassengers().isEmpty() ? 4.0 : 1.5), 1.0);
            if (!entity.isOnGround() || swimming || baby || amount <= 0.1) {
                return;
            }
            double before = walkPosition;
            walkPosition += amount * ticks;
            // Mod: a footfall each time cos(position * 0.25 - 1.5) crosses zero (every 4 pi of walk position).
            if (Math.floor((before * 0.25 - 1.5 + Math.PI / 2) / Math.PI) != Math.floor((walkPosition * 0.25 - 1.5 + Math.PI / 2) / Math.PI)) {
                play(entity, "tremorzilla_stomp", 6.0F, 0.7F);
                if (amount > 0.5) {
                    boolean left = Math.sin(walkPosition * 0.25 - 1.0) > 0;
                    stompEffect(entity, left, 1.0F, 1.3F, (float) (0.4 + amount), TremorzillaRules.WALK_STOMP_DAMAGE, 1.0);
                }
            }
        }

        /** Mod {@code scareMobs}: mobs within 64 blocks drop their targets and run away; a tamed one makes them glow. */
        private void scareMobs(LivingEntity entity) {
            if (age - lastScare < 4) {
                return;
            }
            lastScare = age;
            boolean tamed = owner(entity).isPresent();
            int handled = 0;
            for (Entity found : entity.getNearbyEntities(64.0, 20.0, 64.0)) {
                if (!(found instanceof LivingEntity living) || allied(entity, living) || partOwner(found).isPresent()
                    || context.mobs().typeOf(found).filter(type -> TremorzillaRules.RESISTS_ROAR.contains(type.id())).isPresent()) {
                    continue;
                }
                if (++handled > 96) {
                    break;
                }
                if (found instanceof Mob mob && !(found instanceof Tameable tameable && tameable.isTamed() && sittingTameable(found))) {
                    mob.setTarget(null);
                    if (mob.isOnGround()) {
                        ThreadLocalRandom random = ThreadLocalRandom.current();
                        Vector velocity = mob.getVelocity();
                        mob.setVelocity(new Vector(velocity.getX() * 0.7 + (random.nextFloat() - 0.5) * 0.1, velocity.getY(),
                            velocity.getZ() * 0.7 + (random.nextFloat() - 0.5) * 0.1));
                    }
                    if (!mob.getPathfinder().hasPath()) {
                        Vector away = mob.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0);
                        if (away.lengthSquared() > 1.0E-4) {
                            Location flee = mob.getLocation().add(away.normalize().multiply(30.0));
                            mob.getPathfinder().moveTo(flee, 2.0);
                        }
                    }
                }
                if (tamed) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, true, true));
                }
            }
        }

        private static boolean sittingTameable(Entity entity) {
            return entity instanceof org.bukkit.entity.Sittable sittable && sittable.isSitting();
        }

        private void tryRoar(LivingEntity entity) {
            if (roarCooldown == 0 && animation == Anim.NONE && !firing && !stunned(entity) && !baby(entity)) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                setAnimation(entity, random.nextBoolean() ? Anim.ROAR_2 : Anim.ROAR_1);
                play(entity, "tremorzilla_roar", 8.0F, 1.0F);
                roarCooldown = TremorzillaRules.roarCooldown(random.nextInt(400));
            }
        }

        // --- blocks ---

        private static boolean griefing(LivingEntity entity) {
            return !Boolean.FALSE.equals(entity.getWorld().getGameRuleValue(GameRules.MOB_GRIEFING));
        }

        /**
         * Mod {@code breakBlocksAround}: solid blocks resisting at most 15 in a sphere; a {@code dropChance} of them drop.
         * A nuclear bomb in the way of the beam goes off at once (mod: a bomb entity with its fuse spent).
         */
        private boolean breakAround(LivingEntity entity, Vector center, double radius, boolean triggerExplosions, float dropChance) {
            if (baby(entity) || !griefing(entity)) {
                return false;
            }
            World world = entity.getWorld();
            List<Block> blocks = new ArrayList<>();
            List<Block> bombs = new ArrayList<>();
            int minX = (int) Math.floor(center.getX() - radius);
            int minY = Math.max(world.getMinHeight(), (int) Math.floor(center.getY() - radius));
            int minZ = (int) Math.floor(center.getZ() - radius);
            int maxX = (int) Math.floor(center.getX() + radius);
            int maxY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(center.getY() + radius));
            int maxZ = (int) Math.floor(center.getZ() + radius);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    for (int y = minY; y <= maxY; y++) {
                        double dx = x + 0.5 - center.getX();
                        double dy = y + 0.5 - center.getY();
                        double dz = z + 0.5 - center.getZ();
                        if (dx * dx + dy * dy + dz * dz >= radius * radius) {
                            continue;
                        }
                        Block block = world.getBlockAt(x, y, z);
                        if (nuclearBomb != null && block.getType() == Material.NOTE_BLOCK && nuclearBomb.equals(block.getBlockData())) {
                            bombs.add(block);
                        } else if (breakable(block, false)) {
                            blocks.add(block);
                        }
                    }
                }
            }
            boolean flag = false;
            for (Block bomb : bombs) {
                if (new EntityChangeBlockEvent(entity, bomb, Material.AIR.createBlockData()).callEvent()) {
                    bomb.setType(Material.AIR, false);
                    flag = true;
                    if (triggerExplosions) {
                        Location spot = bomb.getLocation().add(0.5, 0, 0.5);
                        context.plugin().getServer().getScheduler().runTask(context.plugin(),
                            () -> NuclearExplosion.detonate(context.mobs(), spot, NukeMath.BOMB_SIZE, true));
                    }
                    // Mod NuclearBombBlock: any other blow just destroys it (no item, no explosion).
                }
            }
            return destroy(entity, center.toLocation(world), blocks, dropChance) || flag;
        }

        /**
         * Mod {@code breakBlocksInBoundingBox}: every 10 ticks it walks through what is around it (2 blocks out, from just
         * above its feet): leaves, and anything higher than its feet with a collision and resisting at most 15 (cobwebs too).
         */
        private void breakBlocksInBoundingBox(LivingEntity entity, boolean swimming) {
            World world = entity.getWorld();
            BoundingBox box = entity.getBoundingBox().expand(2.0);
            int swimUp = swimming ? 3 : 1;
            int feetY = entity.getLocation().getBlockY();
            List<Block> blocks = new ArrayList<>();
            for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    for (int y = Math.max(world.getMinHeight(), (int) Math.floor(box.getMinY() + swimUp));
                         y <= Math.min(world.getMaxHeight() - 1, (int) Math.floor(box.getMaxY())); y++) {
                        Block block = world.getBlockAt(x, y, z);
                        Material type = block.getType();
                        if (type.isAir() || !(Tag.LEAVES.isTagged(type) || y > feetY)) {
                            continue;
                        }
                        if (type == Material.COBWEB || breakable(block, true)) {
                            blocks.add(block);
                        }
                    }
                }
            }
            destroy(entity, entity.getLocation(), blocks, 0.1F);
        }

        /** Mod conditions: not unbreakable (the mod's {@code nuke_proof}), solid, resisting at most 15. */
        private static boolean breakable(Block block, boolean collision) {
            Material type = block.getType();
            if (type.isAir() || block.isLiquid() || type.getHardness() < 0 || Tag.WITHER_IMMUNE.isTagged(type)
                || type.getBlastResistance() > 15.0F) {
                return false;
            }
            return collision ? !block.isPassable() : type.isSolid();
        }

        /** Breaks blocks through an {@link EntityExplodeEvent}: protection plugins trim it, module blocks drop their items. */
        private static boolean destroy(LivingEntity entity, Location center, List<Block> blocks, float dropChance) {
            if (blocks.isEmpty()) {
                return false;
            }
            EntityExplodeEvent event = new EntityExplodeEvent(entity, center, blocks, dropChance, org.bukkit.ExplosionResult.DESTROY);
            if (!event.callEvent()) {
                return false;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (Block block : List.copyOf(event.blockList())) {
                if (block.getType().isAir()) {
                    continue;
                }
                if (random.nextFloat() <= event.getYield()) {
                    block.breakNaturally();
                } else {
                    block.setType(Material.AIR, true);
                }
            }
            // Mod: it counts as having hit blocks when any were found, which shortens the beam's reach.
            return true;
        }

        // --- healing, radiation, taming ---

        private void heal(LivingEntity entity, int previousAge) {
            double max = maxHealth(entity);
            double fraction = entity.getHealth() / max;
            int period = TremorzillaRules.healPeriod(fraction);
            if (previousAge / period != age / period && !entity.isDead()) {
                entity.setHealth(Math.min(max, entity.getHealth() + TremorzillaRules.healAmount(fraction)));
            }
        }

        /** Mod: Irradiated is eaten at once, healing 12 a level. */
        private void eatRadiation(LivingEntity entity) {
            context.effects().get(entity, ModEffectType.IRRADIATED).ifPresent(effect -> {
                context.effects().remove(entity, ModEffectType.IRRADIATED);
                if (!entity.isDead()) {
                    entity.setHealth(Math.min(maxHealth(entity), entity.getHealth() + TremorzillaRules.irradiatedHeal(effect.amplifier())));
                }
            });
        }

        /** Starts eating a waste drum (mod {@code mobInteract}); the taming roll comes at tick 34. */
        boolean feed(LivingEntity entity, Player player) {
            if (animation != Anim.NONE) {
                return false;
            }
            setAnimation(entity, Anim.CHEW);
            lastFed = player.getUniqueId();
            return true;
        }

        private void chew(LivingEntity entity, int previous) {
            if (animation == Anim.CHEW && TremorzillaRules.crossed(previous, animationTick, 34)) {
                roll(entity);
            }
        }

        private void roll(LivingEntity entity) {
            UUID fed = lastFed;
            lastFed = null;
            if (fed == null || owner(entity).isPresent()) {
                return;
            }
            PersistentDataContainer data = entity.getPersistentDataContainer();
            int attempts = data.getOrDefault(TAME_ATTEMPTS, PersistentDataType.INTEGER, 0) + 1;
            data.set(TAME_ATTEMPTS, PersistentDataType.INTEGER, attempts);
            Player player = entity.getServer().getPlayer(fed);
            if (player != null && TremorzillaRules.tames(attempts, ThreadLocalRandom.current().nextInt(3))) {
                PrimordialTaming.markTamed(context.plugin(), entity, fed, false);
                data.set(COMMAND, PersistentDataType.INTEGER, 0);
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§a¡Domaste al Tremorzilla!"));
            } else {
                entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation().add(0, entity.getHeight(), 0), 7, 0.6, 0.6,
                    0.6, 0.02);
            }
        }

        // --- damage ---

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            this.context = context;
            DamageType type = event.getDamageSource().getDamageType();
            if (type.equals(context.damageTypes().type(ModDamageTypes.RAYGUN))
                || type.equals(context.damageTypes().type(ModDamageTypes.NUKE))) {
                // Mod: rayguns do not hurt it, nukes leave it unhurt.
                event.setCancelled(true);
                return;
            }
            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                event.setDamage(event.getDamage() * TremorzillaRules.EXPLOSION_FACTOR);
            }
            if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
                // Mod: it breathes under water and pushes through what it stands in.
                event.setCancelled(true);
                return;
            }
            if (!(event instanceof EntityDamageByEntityEvent byEntity) || !(entity instanceof Mob mob)) {
                return;
            }
            Entity damager = byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                ? shooter : byEntity.getDamager();
            if (damager instanceof LivingEntity living && !living.equals(entity) && !allied(entity, living)
                && !entity.getPassengers().contains(living)
                && !(living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR))) {
                // Mod HurtByTargetGoal.
                mob.setTarget(living);
            }
        }

        @Override
        public int experience(LivingEntity entity, int vanilla) {
            return TremorzillaRules.EXPERIENCE;
        }

        @Override
        public void close(LivingEntity entity, ModMobContext context) {
            animator.close();
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] != null) {
                    parts[i].remove();
                    parts[i] = null;
                }
            }
            if (entity != null && entity.isValid()) {
                save(entity);
            }
        }

        // --- helpers ---

        private LivingEntity validTarget(Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return null;
            }
            if (!target.isValid() || target.isDead() || target.getWorld() != mob.getWorld()
                || target.getLocation().distanceSquared(mob.getLocation()) > TremorzillaRules.FOLLOW_RANGE * TremorzillaRules.FOLLOW_RANGE
                || target instanceof Player player && (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) || allied(mob, target)) {
                mob.setTarget(null);
                return null;
            }
            return target;
        }

        private boolean charged() {
            return charge >= TremorzillaRules.MAX_CHARGE;
        }

        private boolean stunned(LivingEntity entity) {
            return context.effects().has(entity, ModEffectType.STUNNED);
        }

        private boolean baby(LivingEntity entity) {
            return entity.getPersistentDataContainer().has(new NamespacedKey(context.plugin(), TamingRules.BABY_UNTIL_KEY),
                PersistentDataType.LONG);
        }

        private static double scale(LivingEntity entity) {
            AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
            return scale == null ? 1.0 : scale.getValue() / GIANT_SCALE;
        }

        private boolean sitting(LivingEntity entity) {
            Byte value = entity.getPersistentDataContainer().get(new NamespacedKey(context.plugin(), TamingRules.SITTING_KEY),
                PersistentDataType.BYTE);
            return value != null && value == 1;
        }

        private int command(LivingEntity entity) {
            return entity.getPersistentDataContainer().getOrDefault(COMMAND, PersistentDataType.INTEGER, 0);
        }

        private Optional<UUID> owner(Entity entity) {
            return ownerOf(context.plugin(), entity);
        }

        /** Mod {@code isAlliedTo}: its owner, the owner's other pets and its rider. */
        private boolean allied(Entity self, Entity other) {
            if (self.getPassengers().contains(other)) {
                return true;
            }
            Optional<UUID> owner = owner(self);
            if (owner.isEmpty()) {
                return false;
            }
            if (other.getUniqueId().equals(owner.get()) || owner(other).filter(owner.get()::equals).isPresent()) {
                return true;
            }
            return other instanceof Tameable tameable && owner.get().equals(tameable.getOwnerUniqueId());
        }

        private static Player rider(LivingEntity entity) {
            for (Entity passenger : entity.getPassengers()) {
                if (passenger instanceof Player player) {
                    return player;
                }
            }
            return null;
        }

        private static double maxHealth(LivingEntity entity) {
            AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
            return attribute == null ? TremorzillaRules.MAX_HEALTH : attribute.getValue();
        }

        /** Height of the water column standing in its feet (mod {@code getMaxFluidHeight}). */
        private static double waterHeight(LivingEntity entity) {
            Block block = entity.getLocation().getBlock();
            int height = 0;
            while (height < 12 && block.getRelative(0, height, 0).getType() == Material.WATER) {
                height++;
            }
            return height;
        }

        private static float pitch() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
        }

        private static void play(LivingEntity entity, String sound, float volume, float pitch) {
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:" + sound, SoundCategory.HOSTILE, volume, pitch);
        }
    }

    static Optional<UUID> ownerOf(org.bukkit.plugin.Plugin plugin, Entity entity) {
        String value = entity.getPersistentDataContainer().get(new NamespacedKey(plugin, TamingRules.OWNER_KEY), PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
    }

    // --- player interaction ---

    /**
     * Feeding, commanding and riding (mod {@code mobInteract} of {@code DinosaurEntity} and {@code TremorzillaEntity}),
     * and the rider's attack key (the mod's key 3; here the swing).
     */
    public static final class Listener implements org.bukkit.event.Listener {
        private final ModMobService mobs;

        public Listener(ModMobService mobs) {
            this.mobs = mobs;
        }

        private Optional<Behaviour> behaviour(Entity entity) {
            return mobs.behaviourOf(entity).filter(Behaviour.class::isInstance).map(Behaviour.class::cast);
        }

        /** Before the breeding listener (nuclear bombs), which skips what this one cancels. */
        @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
        public void onInteract(PlayerInteractEntityEvent event) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Entity clicked = event.getRightClicked();
            if (clicked instanceof Interaction part) {
                Optional<UUID> body = partOwner(part);
                if (body.isEmpty()) {
                    return;
                }
                clicked = mobs.plugin().getServer().getEntity(body.get());
                if (clicked == null) {
                    return;
                }
            }
            if (!(clicked instanceof Mob entity)) {
                return;
            }
            Optional<Behaviour> behaviour = behaviour(entity);
            if (behaviour.isEmpty()) {
                return;
            }
            Player player = event.getPlayer();
            ItemStack held = player.getInventory().getItemInMainHand();
            String item = mobs.itemId(held).orElse("");
            Optional<UUID> owner = ownerOf(mobs.plugin(), entity);
            boolean baby = entity.getPersistentDataContainer().has(new NamespacedKey(mobs.plugin(), TamingRules.BABY_UNTIL_KEY),
                PersistentDataType.LONG);
            if (owner.isEmpty()) {
                if (item.equals("ac_waste_drum")) {
                    event.setCancelled(true);
                    if (behaviour.get().feed(entity, player) && player.getGameMode() != GameMode.CREATIVE) {
                        held.subtract();
                    }
                }
                return;
            }
            if (!owner.get().equals(player.getUniqueId()) || item.equals("ac_nuclear_bomb")) {
                return;
            }
            event.setCancelled(true);
            if (player.isSneaking()) {
                // Mod: wander, sit, follow.
                PersistentDataContainer data = entity.getPersistentDataContainer();
                int command = (data.getOrDefault(COMMAND, PersistentDataType.INTEGER, 0) + 1) % 3;
                data.set(COMMAND, PersistentDataType.INTEGER, command);
                boolean sit = command == 1;
                data.set(new NamespacedKey(mobs.plugin(), TamingRules.SITTING_KEY), PersistentDataType.BYTE, (byte) (sit ? 1 : 0));
                entity.setAware(!sit);
                entity.setTarget(null);
                entity.getPathfinder().stopPathfinding();
                String[] names = {"§eEl Tremorzilla deambula", "§eEl Tremorzilla se queda quieto", "§eEl Tremorzilla te sigue"};
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(names[command]));
            } else if (!baby && entity.getPassengers().isEmpty() && !item.equals("ac_waste_drum")) {
                entity.addPassenger(player);
            }
        }

        /** Mod: a tamable animal, not a monster: golems leave it alone (its giant base would draw them). */
        @EventHandler(ignoreCancelled = true)
        public void onGolemTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
            if (event.getEntity() instanceof org.bukkit.entity.Golem && event.getTarget() != null
                && mobs.typeOf(event.getTarget()).filter(TREMORZILLA::equals).isPresent()) {
                event.setCancelled(true);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onSwing(PlayerAnimationEvent event) {
            Player player = event.getPlayer();
            if (event.getAnimationType() == PlayerAnimationType.ARM_SWING && player.getVehicle() instanceof Mob mount
                && ownerOf(mobs.plugin(), mount).filter(player.getUniqueId()::equals).isPresent()) {
                behaviour(mount).ifPresent(behaviour -> behaviour.riderAttack(mount, player));
            }
        }

        /** Its rider's own blows do not land on it. */
        @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
        public void onRiderHit(EntityDamageByEntityEvent event) {
            if (event.getEntity().getPassengers().contains(event.getDamager()) && behaviour(event.getEntity()).isPresent()) {
                event.setCancelled(true);
            }
        }
    }
}
