package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.SauropodRules.Anim;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.SauropodRules.Attack;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The Atlatitan (mod {@code AtlatitanEntity}, {@code AtlatitanMeleeGoal}): a 400-health plant-eating sauropod that only
 * appears after the Luxtructosaurus has been defeated, neutral until it is hurt, then kicking and whipping its tail. The
 * body is an iron golem scaled 3 times (about 4.2 by 8.1 blocks; the mod's is 5 by 8), with the same seven hit parts as
 * the boss ({@link SauropodBody}) and no AI of its own.
 *
 * <p>Also from the mod: it nibbles the trees ({@code AtlatitanNibbleTreesGoal}: walks to the side of a high leaf block,
 * turns to it and bites the leaves, which only shows the break effect), follows a player holding a tree star
 * ({@code TemptGoal}) and breeds on tree stars (eggs and young in {@link PrimordialBreeding}; the young are 15 % of the
 * adult and bite lower trees). It is never tamed: a serene salad makes it rideable for 12000 ticks
 * ({@code onFeedMixture}); the rider walks it and jumps for the stomp once the meter has filled (400 ticks), which crushes
 * the blocks around its feet.
 */
public final class Atlatitan {
    static final double SCALE = 3.0;
    static final double WIDTH = 5.0;
    static final double HEIGHT = 8.0;
    static final double WALK_SPEED = 0.18;
    static final double FOLLOW_RANGE = 16.0;

    /** Mod {@code atlatitan.json}: 7 to 9 dinosaur chops (cooked when it burns) and 5 to 9 heavy bones. */
    public static final List<MobDrop> LOOT = List.of(MobDrop.of("ac_dinosaur_chop", 7, 9),
        MobDrop.withLooting("ac_heavy_bone", 5, 9, 2));

    /** Ticks of rideability left (mod {@code RIDEABLE_FOR}), counted only while it is loaded. */
    static final NamespacedKey RIDEABLE = new NamespacedKey("bigcasares", "ac_atlatitan_rideable");
    /** Mod config {@code atlatitan_max_block_explosion_resistance} and the most items one stomp drops (the mod has no cap). */
    static final float CRUSH_RESISTANCE = 10.0F;
    static final int CRUSH_WIDTH = 15;
    /** Most items one stomp drops (the mod has no cap); blocks past it stay. */
    static final int CRUSH_DROP_CAP = 160;
    /** A tool that makes every block drop, as the mod's crushed blocks do. */
    private static final ItemStack CRUSH_TOOL = new ItemStack(Material.NETHERITE_PICKAXE);

    public static final ModMobType ATLATITAN = new ModMobType("atlatitan", CaveBiomeId.PRIMORDIAL_CAVES, "iron_golem",
        SpawnCategory.CAVE_CREATURE,
        Map.of("max_health", SauropodRules.ATLATITAN_HEALTH, "attack_damage", SauropodRules.ATLATITAN_ATTACK,
            "movement_speed", SauropodRules.ATLATITAN_SPEED, "knockback_resistance", 1.0, "scale", SCALE, "step_height", 3.2),
        "alexscaves_atlatitan", LOOT);

    private Atlatitan() {
    }

    /** The body scale (the mod's young are 15 % of it). */
    static void applyScale(LivingEntity entity, boolean baby) {
        AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
        double wanted = SCALE * (baby ? SauropodRules.ATLATITAN_BABY_SCALE : 1.0);
        if (scale != null && Math.abs(scale.getBaseValue() - wanted) > 1.0E-4) {
            scale.setBaseValue(wanted);
        }
    }

    public static void register(ModMobCatalog catalog, PrimordialBlocks blocks) {
        catalog.register(ATLATITAN, (entity, type) -> {
            if (entity instanceof Mob mob) {
                Bukkit.getMobGoals().removeAllGoals(mob);
                mob.setAware(false);
                mob.setDespawnInPeacefulOverride(TriState.FALSE);
            }
            return new Behaviour(blocks);
        });
        // Mod checkPrehistoricPostBossSpawnRules: no natural Atlatitans until the boss has been defeated once.
        catalog.spawnRule(ATLATITAN.id(), (block, random) -> PrimordialBoss.defeatedOnce());
    }

    static final class Behaviour implements ModMobBehaviour {
        private final PrimordialBlocks blocks;
        private final SauropodBody body = new SauropodBody(HEIGHT, 1.0);
        private final Random random = new Random();
        private ModMobContext context;
        private NamespacedKey babyKey;
        private final PrimordialAnimator animator = new PrimordialAnimator();
        private boolean speakPending;
        private Anim anim = Anim.NONE;
        private int animTick;
        private int age;
        private float whipYaw;
        private UUID target;
        private int unseen;
        private Location wanderGoal;
        private int wanderTime;

        private boolean pinned;
        private boolean pinnedKnown;

        /** Stomp meter of the rider (mod {@code METER_AMOUNT}). */
        private float meter;
        private int barClock;
        private int temptClock;
        private UUID tempter;
        private final SauropodLegs legs = new SauropodLegs();
        private double stride;
        private Location lastSpot;

        // The tree it is nibbling (mod AtlatitanNibbleTreesGoal).
        private int nibbleCooldown;
        private int[] tree;
        private int nibbleTicks;
        private double bestTreeDistance = Double.MAX_VALUE;
        private int treeProgressTicks;
        private BlockData lastEaten;

        // The stomp's crushing, one layer of blocks a step.
        private int crushLayer = -1;
        private int crushX;
        private int crushZ;
        private int crushFeetY;
        private int crushDrops;

        Behaviour(PrimordialBlocks blocks) {
            this.blocks = blocks;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            this.context = context;
            age += ticks;
            if (babyKey == null) {
                babyKey = new NamespacedKey(context.plugin(), TamingRules.BABY_UNTIL_KEY);
            }
            boolean baby = baby(entity);
            applyScale(entity, baby);
            body.setScale(baby ? SauropodRules.ATLATITAN_BABY_SCALE : 1.0);
            Location at = entity.getLocation();
            body.ensure(entity, at, at.getYaw());
            body.forwardHits(entity);
            int previous = animTick;
            if (anim != Anim.NONE) {
                animTick += ticks;
            }
            Player rider = baby ? null : rider(entity);
            keepWhileRidden(entity, rider != null);
            rideable(entity, ticks, baby);
            meter = SauropodRules.meterAfter(meter, rider != null, ticks);
            LivingEntity prey = rider != null ? null : target();
            animator.track(entity, ticks);
            boolean dancing = rider == null && prey == null && animator.dancing(entity, ticks);
            if (rider != null) {
                target = null;
                stopNibble();
                ride(entity, rider, ticks, previous);
            } else if (prey == null) {
                target = null;
                if (dancing) {
                    // Mod travel(): a dancing dinosaur stands still.
                    stopNibble();
                } else {
                    idle(entity, ticks, baby, previous);
                }
            } else if (prey.getWorld() != entity.getWorld()
                || prey.getLocation().distanceSquared(at) > FOLLOW_RANGE * FOLLOW_RANGE * 4) {
                target = null;
            } else {
                stopNibble();
                melee(entity, prey, ticks, baby);
            }
            events(entity, ticks, previous);
            tickCrush(entity);
            if (anim == Anim.EAT_LEAVES) {
                crumbs(entity);
            }
            if (anim != Anim.NONE && animTick >= anim.duration) {
                anim = Anim.NONE;
                animTick = 0;
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
            stepSound(entity, baby);
            // Mod: it heals 2 every 100 ticks.
            if (age % 100 < ticks && entity.getHealth() < entity.getAttribute(Attribute.MAX_HEALTH).getValue()) {
                entity.setHealth(Math.min(entity.getAttribute(Attribute.MAX_HEALTH).getValue(),
                    entity.getHealth() + 2.0));
            }
            body.follow(entity.getLocation(), entity.getLocation().getYaw());
        }

        private boolean baby(LivingEntity entity) {
            return entity.getPersistentDataContainer().has(babyKey, PersistentDataType.LONG);
        }

        /** A ridden Atlatitan must not despawn under its rider; the flag is released once the rider is gone. */
        private void keepWhileRidden(LivingEntity entity, boolean ridden) {
            if (!(entity instanceof org.bukkit.entity.Mob mob)) {
                return;
            }
            if (!pinnedKnown) {
                // The flag is saved with the entity: one left by a rider before a restart must still be released.
                pinned = mob.isPersistent();
                pinnedKnown = true;
            }
            if (ridden == pinned) {
                return;
            }
            pinned = ridden;
            mob.setPersistent(ridden);
            mob.setRemoveWhenFarAway(!ridden);
        }

        private static Player rider(LivingEntity entity) {
            for (Entity passenger : entity.getPassengers()) {
                if (passenger instanceof Player player) {
                    return player;
                }
            }
            return null;
        }

        /** Mod {@code RIDEABLE_FOR}: counts down while loaded; the happiness particles show it at the head. */
        private void rideable(LivingEntity entity, int ticks, boolean baby) {
            Integer left = entity.getPersistentDataContainer().get(RIDEABLE, PersistentDataType.INTEGER);
            if (left == null) {
                return;
            }
            int next = Math.max(0, left - ticks);
            if (next == 0) {
                entity.getPersistentDataContainer().remove(RIDEABLE);
            } else {
                entity.getPersistentDataContainer().set(RIDEABLE, PersistentDataType.INTEGER, next);
            }
            if (!baby) {
                Vector head = body.center(entity.getLocation(), entity.getLocation().getYaw(), SauropodBody.HEAD);
                entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, head.getX() + (random.nextDouble() - 0.5) * 2.0,
                    head.getY() + random.nextDouble() * 2.0, head.getZ() + (random.nextDouble() - 0.5) * 2.0, 1, 0.05, 0.05,
                    0.05, 0.0);
            }
        }

        // --- riding ---

        /** Mod {@code tickRidden}/{@code getRiddenInput}; key 2 (jump) is the stomp once the meter is full. */
        private void ride(LivingEntity entity, Player rider, int ticks, int previous) {
            Input input = rider.getCurrentInput();
            Location look = rider.getLocation();
            if (input.isJump() && SauropodRules.canStomp(meter, anim != Anim.NONE)) {
                entity.setRotation(look.getYaw(), 0.0F);
                meter = 0.0F;
                setAnim(entity, Anim.STOMP);
            }
            barClock += ticks;
            if (barClock >= 10) {
                barClock = 0;
                int filled = Math.round(meter * 10);
                String bar = "§6Pisotón §e" + "▮".repeat(filled) + "§8" + "▯".repeat(10 - filled)
                    + (filled >= 10 ? " §a(salta)" : "");
                rider.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(bar));
            }
            Vector velocity = entity.getVelocity();
            if (anim == Anim.STOMP) {
                entity.setVelocity(new Vector(0, velocity.getY(), 0));
                return;
            }
            double forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
            double strafe = (input.isLeft() ? 1 : 0) - (input.isRight() ? 1 : 0);
            if (forward == 0 && strafe == 0) {
                entity.setVelocity(new Vector(0, velocity.getY(), 0));
                return;
            }
            entity.setRotation(look.getYaw(), 0.0F);
            double[] moved = SauropodRules.riddenInput(forward, strafe);
            double yaw = Math.toRadians(look.getYaw());
            double speed = WALK_SPEED * 1.3;
            double x = (-Math.sin(yaw) * moved[1] + Math.cos(yaw) * moved[0]) * speed;
            double z = (Math.cos(yaw) * moved[1] + Math.sin(yaw) * moved[0]) * speed;
            entity.setVelocity(new Vector(x, velocity.getY(), z));
        }

        // --- stomp crushing ---

        /** The stomp lands on animation tick 30: the sound, then the blocks around its feet crush layer by layer. */
        private void stompHit(LivingEntity entity) {
            play(entity, "atlatitan_stomp", 3.0F);
            Location at = entity.getLocation();
            crushX = at.getBlockX();
            crushZ = at.getBlockZ();
            crushFeetY = at.getBlockY();
            crushLayer = 0;
            crushDrops = 0;
        }

        private boolean griefing(LivingEntity entity) {
            // Mod: mob griefing, or a player riding it.
            return !Boolean.FALSE.equals(entity.getWorld().getGameRuleValue(GameRules.MOB_GRIEFING))
                || rider(entity) != null;
        }

        /** Mod {@code crushBlocksInRing(15, ..., 1.0)}: seven layers from the feet up, spread over the next steps. */
        private void tickCrush(LivingEntity entity) {
            if (crushLayer < 0) {
                return;
            }
            int y = crushLayer++;
            if (crushLayer > 6) {
                crushLayer = -1;
            }
            if (!griefing(entity)) {
                crushLayer = -1;
                return;
            }
            World world = entity.getWorld();
            Player rider = rider(entity);
            int width = CRUSH_WIDTH;
            for (int i = -width - 1; i <= width + 1; i++) {
                for (int j = -width - 1; j <= width + 1; j++) {
                    int x = crushX + i;
                    int z = crushZ + j;
                    // Mod: the distance to the ring's centre includes the height above the feet.
                    if (Math.sqrt(i * (double) i + j * (double) j + y * (double) y) > width
                        || !world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    int by = crushFeetY + y;
                    if (by < world.getMinHeight() || by >= world.getMaxHeight()) {
                        continue;
                    }
                    crush(entity, rider, world.getBlockAt(x, by, z));
                }
            }
        }

        /** One block of the stomp: what a protection plugin sees is the golem and, when ridden, the rider breaking it. */
        private void crush(LivingEntity entity, Player rider, Block block) {
            Material type = block.getType();
            if (type.isAir() || block.isLiquid() || block.isReplaceable() || PrimordialBlocks.unmovable(type)
                || type.getBlastResistance() > CRUSH_RESISTANCE) {
                return;
            }
            boolean container = block.getState() instanceof Container;
            // Past the cap the blocks stay: removing them would only destroy items, and a container would lose its contents.
            int items = container ? CRUSH_DROP_CAP : block.getDrops(CRUSH_TOOL).size();
            if (crushDrops + items > CRUSH_DROP_CAP && (container || crushDrops >= CRUSH_DROP_CAP)) {
                return;
            }
            if (!new EntityChangeBlockEvent(entity, block, Material.AIR.createBlockData()).callEvent()) {
                return;
            }
            boolean drops = true;
            if (rider != null) {
                BlockBreakEvent breakEvent = new BlockBreakEvent(block, rider);
                if (!breakEvent.callEvent()) {
                    return;
                }
                drops = breakEvent.isDropItems();
            }
            if (container) {
                // Contents spill like the mod's falling block entity; counted as a full stomp's worth.
                crushDrops = CRUSH_DROP_CAP;
            } else {
                crushDrops += items;
            }
            if (drops) {
                // Mod drop chance 1.0 whatever the tool.
                block.breakNaturally(CRUSH_TOOL);
            } else {
                block.setType(Material.AIR, true);
            }
        }

        // --- fighting ---

        /** Mod {@code AtlatitanMeleeGoal}. */
        private void melee(LivingEntity entity, LivingEntity prey, int ticks, boolean baby) {
            Location at = entity.getLocation();
            Location goal = prey.getLocation();
            double distance = at.distance(goal);
            double attackDistance = WIDTH * (baby ? SauropodRules.ATLATITAN_BABY_SCALE : 1.0) + prey.getWidth();
            if ((anim.kick()) ) {
                entity.setRotation(SauropodBody.approachDegrees(at.getYaw(),
                    SauropodBody.yawTo(goal.getX() - at.getX(), goal.getZ() - at.getZ()), 30.0F), 0.0F);
            }
            if (distance > attackDistance && !anim.whip()) {
                SauropodBody.walk(entity, goal.getX(), goal.getZ(), pace(baby), 6.0F);
            }
            if (anim == Anim.NONE && distance < attackDistance + 4.0) {
                Attack attack = SauropodRules.atlatitanAttack(random.nextFloat(), distance, attackDistance);
                if (attack == Attack.KICK) {
                    play(entity, "atlatitan_kick", 3.0F);
                    setAnim(entity, random.nextBoolean() ? Anim.LEFT_KICK : Anim.RIGHT_KICK);
                } else {
                    play(entity, "atlatitan_tail", 3.0F);
                    setAnim(entity, random.nextBoolean() ? Anim.RIGHT_WHIP : Anim.LEFT_WHIP);
                    whipYaw = at.getYaw();
                }
            }
        }

        /** Mod: the young walk at half the pace. */
        private static double pace(boolean baby) {
            return baby ? WALK_SPEED * 0.5 : WALK_SPEED;
        }

        private void events(LivingEntity entity, int ticks, int previous) {
            Location at = entity.getLocation();
            double attack = SauropodRules.ATLATITAN_ATTACK;
            if (anim == Anim.STOMP && SauropodRules.crossed(previous, animTick, SauropodRules.STOMP_HIT_TICK)) {
                stompHit(entity);
            }
            if (anim.kick() && SauropodRules.crossed(previous, animTick, 8)) {
                double side = anim == Anim.LEFT_KICK ? 2.0 : -2.0;
                double yaw = Math.toRadians(at.getYaw());
                double fx = -Math.sin(yaw);
                double fz = Math.cos(yaw);
                Location arm = at.clone().add(fx * 2.5 - fz * side, 0, fz * 2.5 + fx * side);
                SauropodBody.hurtAround(entity, arm, 5.0F, (float) (attack * 0.8), 1.0F, false, false);
            }
            if (anim.whip()) {
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
                    SauropodBody.hurtAround(entity, tail.toLocation(entity.getWorld()), 12.0F, (float) attack, 1.0F, false, false);
                }
            }
        }

        // --- idling: tree stars, trees, strolling ---

        /** Mod goal order: tempt (4), nibble the trees (5), stroll (6). */
        private void idle(LivingEntity entity, int ticks, boolean baby, int previous) {
            Player tempting = tempter(entity);
            if (tempting != null) {
                stopNibble();
                // Mod TemptGoal(1.1): follows at 1.1 and stops within 2.5 blocks.
                Location to = tempting.getLocation();
                Location at = entity.getLocation();
                if (at.distanceSquared(to) >= 6.25) {
                    SauropodBody.walk(entity, to.getX(), to.getZ(), pace(baby) * 1.1, 6.0F);
                }
                return;
            }
            if (tree != null) {
                nibble(entity, ticks, baby, previous);
                return;
            }
            nibbleCooldown -= ticks;
            if (nibbleCooldown <= 0) {
                nibbleCooldown = SauropodRules.nibbleCooldown(random);
                if (!entity.getWorld().getNearbyPlayers(entity.getLocation(), 96.0).isEmpty()) {
                    startNibble(entity, baby);
                }
            }
            if (tree == null) {
                wander(entity, ticks, baby);
            }
        }

        /** The player within 10 blocks holding a tree star that draws it, checked twice a second. */
        private Player tempter(LivingEntity entity) {
            temptClock -= 2;
            if (temptClock > 0) {
                Entity cached = tempter == null ? null : context.plugin().getServer().getEntity(tempter);
                return cached instanceof Player player && holdsStar(player)
                    && player.getWorld() == entity.getWorld() && player.getLocation().distanceSquared(entity.getLocation())
                    <= SauropodRules.TEMPT_RANGE * SauropodRules.TEMPT_RANGE ? player : null;
            }
            temptClock = 10;
            tempter = null;
            Player best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Player player : entity.getWorld().getNearbyPlayers(entity.getLocation(), SauropodRules.TEMPT_RANGE)) {
                double distance = player.getLocation().distanceSquared(entity.getLocation());
                if (player.getGameMode() != GameMode.SPECTATOR && holdsStar(player) && distance < bestDistance) {
                    best = player;
                    bestDistance = distance;
                }
            }
            tempter = best == null ? null : best.getUniqueId();
            return best;
        }

        private boolean holdsStar(Player player) {
            return context.mobs().itemId(player.getInventory().getItemInMainHand()).filter("ac_tree_star"::equals).isPresent()
                || context.mobs().itemId(player.getInventory().getItemInOffHand()).filter("ac_tree_star"::equals).isPresent();
        }

        // --- nibbling ---

        /** Mod tag {@code relicheirus_nibbles}: leaves and the archaic vines (cave vines here). */
        private boolean nibbles(Block block) {
            Material type = block.getType();
            return Tag.LEAVES.isTagged(type) || type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT
                || type == Material.MANGROVE_PROPAGULE || type == Material.COCOA || blocks.nibbles(block);
        }

        /** Mod {@code getHeightOfBlock}: how many blocks of leaves, air or logs there are straight down. */
        private int heightOf(World world, int x, int y, int z, int limit) {
            int height = 0;
            // The mod's checks stop under 20 (3 for the young), so nothing deeper matters.
            while (y > world.getMinHeight() && height < limit) {
                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (!(type.isAir() || nibbles(block) || type == Material.MANGROVE_ROOTS || blocks.breaks(block))) {
                    break;
                }
                height++;
                y--;
            }
            return height;
        }

        /** Mod {@code MoveToBlockGoal.findNearestBlock}: the nearest high leaf block within 30 across and 16 up or down. */
        private void startNibble(LivingEntity entity, boolean baby) {
            World world = entity.getWorld();
            Location at = entity.getLocation();
            int cx = at.getBlockX();
            int cy = at.getBlockY();
            int cz = at.getBlockZ();
            int offset = random.nextInt(2);
            double best = Double.MAX_VALUE;
            int[] found = null;
            // Every second column, the leaves of a canopy being wider than that.
            for (int dx = -SauropodRules.NIBBLE_RANGE + offset; dx <= SauropodRules.NIBBLE_RANGE; dx += 2) {
                for (int dz = -SauropodRules.NIBBLE_RANGE + offset; dz <= SauropodRules.NIBBLE_RANGE; dz += 2) {
                    int x = cx + dx;
                    int z = cz + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    for (int dy = -SauropodRules.NIBBLE_HEIGHT; dy <= SauropodRules.NIBBLE_HEIGHT; dy++) {
                        int y = cy + dy;
                        double distance = dx * (double) dx + dy * (double) dy + dz * (double) dz;
                        if (distance >= best || y < world.getMinHeight() || y >= world.getMaxHeight()
                            || !nibbles(world.getBlockAt(x, y, z))) {
                            continue;
                        }
                        if (SauropodRules.nibbleHighEnough(heightOf(world, x, y, z, baby ? 3 : 20), baby)) {
                            best = distance;
                            found = new int[] {x, y, z};
                        }
                    }
                }
            }
            if (found != null) {
                tree = found;
                nibbleTicks = 0;
                bestTreeDistance = Double.MAX_VALUE;
                treeProgressTicks = 0;
            }
        }

        private void stopNibble() {
            tree = null;
            nibbleTicks = 0;
        }

        /** Direction (yaw) from the body to the tree, snapped to the four sides. */
        private float treeSide(Location at, int[] target) {
            return SauropodRules.cardinalYaw(SauropodBody.yawTo(target[0] + 0.5 - at.getX(), target[2] + 0.5 - at.getZ()));
        }

        /** Mod {@code getStandAtTreePos}: this far back from the tree, on the side it is seen from. */
        private double[] standAt(Location at, int[] target, boolean baby) {
            double yaw = Math.toRadians(treeSide(at, target));
            int dirX = (int) Math.round(-Math.sin(yaw));
            int dirZ = (int) Math.round(Math.cos(yaw));
            int back = SauropodRules.standDistance(baby ? SauropodRules.ATLATITAN_BABY_SCALE : 1.0);
            int x = target[0];
            int z = target[2];
            if (at.getWorld().getBlockAt(target[0], target[1] - 1, target[2]).getType().isAir()) {
                x += dirX;
                z += dirZ;
            }
            return new double[] {x - dirX * back + 0.5, z - dirZ * back + 0.5};
        }

        /** Mod {@code AtlatitanNibbleTreesGoal.tick}. */
        private void nibble(LivingEntity entity, int ticks, boolean baby, int previous) {
            World world = entity.getWorld();
            Location at = entity.getLocation();
            nibbleTicks += ticks;
            boolean valid = nibbleTicks <= 1200 && world.isChunkLoaded(tree[0] >> 4, tree[2] >> 4)
                && nibbles(world.getBlockAt(tree[0], tree[1], tree[2]));
            if (!valid) {
                stopNibble();
                return;
            }
            double[] stand = standAt(at, tree, baby);
            double dx = stand[0] - at.getX();
            double dz = stand[1] - at.getZ();
            double distanceSquared = dx * dx + dz * dz;
            // Mod: it gives up when the path never gets closer (about every 50 to 150 ticks).
            if (distanceSquared < bestTreeDistance - 0.25) {
                bestTreeDistance = distanceSquared;
                treeProgressTicks = 0;
            } else if ((treeProgressTicks += ticks) > 150 && anim != Anim.EAT_LEAVES) {
                stopNibble();
                return;
            }
            double headDistance = Math.floor(14.0F * (baby ? SauropodRules.ATLATITAN_BABY_SCALE : 1.0));
            if (distanceSquared >= Math.max(headDistance, 1.0)) {
                SauropodBody.walk(entity, stand[0], stand[1], pace(baby), 6.0F);
                return;
            }
            if (!lockOnTree(entity, ticks, headDistance, baby)) {
                return;
            }
            if (anim == Anim.NONE) {
                setAnim(entity, Anim.EAT_LEAVES);
            } else if (anim == Anim.EAT_LEAVES) {
                if (animTick >= 35) {
                    stopNibble();
                    return;
                }
                if (SauropodRules.crossed(previous, animTick, 20)) {
                    // Mod: destroyBlock without drops and the same block put back: only the break effect shows.
                    BlockData back = world.getBlockAt(tree[0], tree[1], tree[2]).getBlockData();
                    lastEaten = back;
                    Location spot = new Location(world, tree[0] + 0.5, tree[1] + 0.5, tree[2] + 0.5);
                    world.spawnParticle(Particle.BLOCK, spot, 24, 0.4, 0.4, 0.4, 0.0, back);
                    world.playSound(spot, back.getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 1.0F, 0.8F);
                }
            }
        }

        /** Mod {@code lockTreePosition}: turns to face the tree and stays a head's length from it. */
        private boolean lockOnTree(LivingEntity entity, int ticks, double headDistance, boolean baby) {
            Location at = entity.getLocation();
            float wanted = treeSide(at, tree);
            float yaw = SauropodBody.approachDegrees(at.getYaw(), wanted, 10.0F * ticks);
            entity.setRotation(yaw, 0.0F);
            double[] stand = standAt(at, tree, baby);
            double dx = stand[0] - at.getX();
            double dz = stand[1] - at.getZ();
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared > 1.0) {
                SauropodBody.walk(entity, stand[0], stand[1], pace(baby), 6.0F);
            }
            float difference = Math.abs(SauropodBody.approachDegrees(yaw, wanted, 360.0F) - yaw);
            return distanceSquared < Math.max(headDistance, 1.0) && difference < 7.0F;
        }

        /** Mod client tick: crumbs of the eaten block at the head from animation tick 35 to 90. */
        private void crumbs(LivingEntity entity) {
            if (lastEaten == null || animTick <= 35 || animTick >= 90) {
                return;
            }
            Vector head = body.center(entity.getLocation(), entity.getLocation().getYaw(), SauropodBody.HEAD);
            entity.getWorld().spawnParticle(Particle.BLOCK, head.getX() + (random.nextDouble() - 0.5) * 2.0,
                head.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.2, head.getZ() + (random.nextDouble() - 0.5) * 2.0, 2, 0.05,
                0.05, 0.05, 0.0, lastEaten);
        }

        private void wander(LivingEntity entity, int ticks, boolean baby) {
            Location at = entity.getLocation();
            if (wanderGoal == null) {
                // Mod RandomStrollGoal(this, 1.0, 50): about one try in fifty each tick, to a spot 30 blocks away.
                if (random.nextDouble() < SauropodRules.perTick(50, ticks)) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double length = 10 + random.nextDouble() * 20;
                    wanderGoal = at.clone().add(Math.cos(angle) * length, 0, Math.sin(angle) * length);
                    wanderTime = 0;
                }
                return;
            }
            wanderTime += ticks;
            if (wanderTime > 400 || at.distanceSquared(wanderGoal) < 9.0) {
                wanderGoal = null;
                return;
            }
            SauropodBody.walk(entity, wanderGoal.getX(), wanderGoal.getZ(), pace(baby) * 0.6, 3.0F);
        }

        private LivingEntity target() {
            if (target == null || context == null) {
                return null;
            }
            Entity found = context.plugin().getServer().getEntity(target);
            return found instanceof LivingEntity living && living.isValid() && !living.isDead()
                && !(living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR)) ? living : null;
        }

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            this.context = context;
            if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
                event.setCancelled(true);
                return;
            }
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                Entity damager = byEntity.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof Entity shooter ? shooter : byEntity.getDamager();
                if (entity.getPassengers().contains(damager)) {
                    // Its rider's own blows do not land on it.
                    event.setCancelled(true);
                    return;
                }
                if (byEntity.getDamager() instanceof Projectile) {
                    event.setDamage(event.getDamage() * SauropodRules.SAUROPOD_PROJECTILE);
                }
                // Mod HurtByTargetGoal(this, Atlatitan): it answers whoever hurt it, but not its own kind.
                if (damager instanceof LivingEntity living && living != entity && !SauropodBody.partOwner(living).isPresent()
                    && !SauropodBody.sameKind(living, entity)) {
                    target = living.getUniqueId();
                }
            }
        }

        private void setAnim(LivingEntity entity, Anim next) {
            anim = next;
            animTick = 0;
            String name = PrimordialPose.sauropod(next);
            if (name != null) {
                animator.action(context, entity, name);
            }
        }

        @Override
        public String soundSuffix(LivingEntity entity, String suffix) {
            if (suffix.equals("idle")) {
                speakPending = true;
            }
            return suffix;
        }

        /** Mod {@code onStep}: the heavy step of an adult, once every stride. */
        private void stepSound(LivingEntity entity, boolean baby) {
            Location now = entity.getLocation();
            if (lastSpot != null && lastSpot.getWorld() == now.getWorld()) {
                double moved = Math.hypot(now.getX() - lastSpot.getX(), now.getZ() - lastSpot.getZ());
                stride = moved > 0.02 && moved < 3.0 ? stride + moved : 0.0;
                if (stride >= 4.0) {
                    stride = 0.0;
                    if (!baby && entity.isOnGround()) {
                        play(entity, "atlatitan_step", 2.0F);
                    }
                }
            }
            lastSpot = now;
        }

        private void play(LivingEntity entity, String sound, float volume) {
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:" + sound, SoundCategory.NEUTRAL, volume,
                0.9F + random.nextFloat() * 0.2F);
        }

        @Override
        public void close(LivingEntity entity, ModMobContext context) {
            animator.close();
            body.close();
            crushLayer = -1;
            tree = null;
        }
    }

    // --- player interaction ---

    /** Mod {@code mobInteract}: an adult that is rideable and free takes the player on its back. */
    static boolean mount(LivingEntity entity, Player player) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (data.getOrDefault(RIDEABLE, PersistentDataType.INTEGER, 0) <= 0 || !entity.getPassengers().isEmpty()
            || data.has(new NamespacedKey("bigcasares", TamingRules.BABY_UNTIL_KEY), PersistentDataType.LONG)) {
            return false;
        }
        return entity.addPassenger(player);
    }

    /**
     * Feeding a serene salad and riding (mod {@code AtlatitanEntity.mobInteract}): the salad makes it rideable for
     * 12000 ticks and the player then climbs on with any item that is not its breeding food (a tree star, which the
     * breeding listener takes). Right-clicking its neck or tail boxes counts as clicking the body.
     */
    public static final class Listener implements org.bukkit.event.Listener {
        private final ModMobService mobs;

        public Listener(ModMobService mobs) {
            this.mobs = mobs;
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
        public void onInteract(PlayerInteractEntityEvent event) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Entity clicked = event.getRightClicked();
            if (clicked instanceof Interaction part) {
                clicked = SauropodBody.partOwner(part).map(id -> mobs.plugin().getServer().getEntity(id)).orElse(null);
            }
            if (!(clicked instanceof LivingEntity entity) || mobs.typeOf(entity).filter(ATLATITAN::equals).isEmpty()) {
                return;
            }
            Player player = event.getPlayer();
            ItemStack held = player.getInventory().getItemInMainHand();
            String item = mobs.itemId(held).orElse(held.getType().isAir() ? "" : held.getType().name());
            boolean baby = entity.getPersistentDataContainer().has(
                new NamespacedKey(mobs.plugin(), TamingRules.BABY_UNTIL_KEY), PersistentDataType.LONG);
            if (item.equals("ac_serene_salad") && !baby) {
                event.setCancelled(true);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    held.subtract();
                }
                // Mod: the bowl is spawned at the dinosaur, even in creative.
                entity.getWorld().dropItemNaturally(entity.getLocation().add(0, entity.getHeight() * 0.5, 0),
                    new ItemStack(Material.BOWL));
                entity.getPersistentDataContainer().set(RIDEABLE, PersistentDataType.INTEGER, SauropodRules.RIDEABLE_TICKS);
                entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0, entity.getHeight() * 0.75, 0),
                    12, 2.0, 1.0, 2.0);
                return;
            }
            // A tree star is the breeding listener's (it mounts the player when the star cannot be eaten).
            if (!TamingRules.isBreedingFood(ATLATITAN, item) && mount(entity, player)) {
                event.setCancelled(true);
            }
        }
    }
}
