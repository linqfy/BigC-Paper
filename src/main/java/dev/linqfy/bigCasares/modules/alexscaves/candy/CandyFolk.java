package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobAnimator;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The people of the Candy Cavity: the Gingerbread Man on a fox (it steals sweets from players, runs off and stores
 * them in barrels, hits and runs when hurt) and the Licowitch on a witch (spinning peppermints, ground hexes,
 * possessed candy mobs and hunger potions). Both spawn only in their structures, as the mod's {@code spawn_overrides}:
 * 4 Gingerbread Men in gingerbread towns, a Licowitch (70%) or a witch (30%) in licowitch towers.
 */
public final class CandyFolk {
    private static final CaveBiomeId BIOME = CaveBiomeId.CANDY_CAVITY;

    public static final ModMobType GINGERBREAD_MAN = new ModMobType("gingerbread_man", BIOME, "fox", SpawnCategory.MONSTER,
        Map.of("max_health", 10.0, "attack_damage", 2.0, "movement_speed", 0.45, "follow_range", 48.0),
        "alexscaves_gingerbread_man", List.of(MobDrop.of("ac_gingerbread_crumbs", 0, 1)));
    /** The ice cream scoop (one of three flavours) is rolled by {@link CandyMobListener}. */
    public static final ModMobType LICOWITCH = new ModMobType("licowitch", BIOME, "witch", SpawnCategory.MONSTER,
        Map.of("max_health", 40.0, "attack_damage", 3.0, "movement_speed", 0.25, "follow_range", 48.0),
        "alexscaves_licowitch", List.of(new MobDrop("ac_radiant_essence", 0, 1, false, 1.0, 0.0, false),
        MobDrop.rare("ac_sugar_staff", 0.025, 0.01)));
    static final String[] SCOOPS = {"ac_vanilla_ice_cream_scoop", "ac_chocolate_ice_cream_scoop", "ac_sweetberry_ice_cream_scoop"};

    /** Mod tag {@code gingerbread_man_steals} (the module's items among them). */
    static final Set<String> STEALS = Set.of("ac_candy_cane", "ac_gumball_pile", "ac_sweet_tooth", "ac_caramel_apple",
        "ac_radiant_essence", "minecraft:cake", "minecraft:cookie", "minecraft:pumpkin_pie");
    static final NamespacedKey POSSESSED_BY = new NamespacedKey("bigcasares", "ac_possessed_by");

    private CandyFolk() {
    }

    public static void register(ModMobCatalog catalog, ModBlockPalette palette) {
        // The Gummy Bear's back scratching goes for these.
        CandyMobs.trackLicoroot(List.of(palette.get("licoroot", "x"), palette.get("licoroot", "y"), palette.get("licoroot", "z")));
        catalog.register(GINGERBREAD_MAN, (entity, type) -> {
            if (entity instanceof Mob mob) {
                stripFoxGoals(mob);
            }
            return new GingerbreadMan(type);
        });
        catalog.register(LICOWITCH, (entity, type) -> {
            if (entity instanceof Raider raider) {
                raider.setCanJoinRaid(false);
            }
            return new Licowitch(type);
        });
        // Mod spawn_overrides: monsters spawning inside a gingerbread town piece are 4 Gingerbread Men; inside a licowitch
        // tower a Licowitch (70%) or a witch (30%), passing 1 time in 2 and only with fewer than 4 witches within 32
        // blocks (checkLicowitchSpawnRules). The pieces are recorded when they generate (StructureMarks).
        catalog.structureSpawn(BIOME, 4, (spot, random) ->
            StructureMarks.inside(spot.getLocation(), StructureMarks.Kind.GINGERBREAD_TOWN) ? GINGERBREAD_MAN.id() : null);
        catalog.structureSpawn(BIOME, 1, (spot, random) -> {
            if (!StructureMarks.inside(spot.getLocation(), StructureMarks.Kind.LICOWITCH_TOWER)) {
                return null;
            }
            if (!random.nextBoolean() || spot.getWorld().getNearbyEntities(spot.getLocation(), 32, 32, 32,
                found -> found instanceof org.bukkit.entity.Witch).size() >= 4) {
                return "";
            }
            return random.nextInt(10) < 7 ? LICOWITCH.id() : "minecraft:witch";
        });
    }

    static boolean steals(ModMobContext context, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return context.mobs().itemId(stack).map(STEALS::contains)
            .orElse(STEALS.contains("minecraft:" + stack.getType().getKey().getKey()));
    }

    /** Friends of a mob: what it has possessed and its possessor (mod {@code isFriendlyFire}). */
    static boolean friendly(LivingEntity witch, Entity other) {
        String owner = other.getPersistentDataContainer().get(POSSESSED_BY, PersistentDataType.STRING);
        return owner != null && owner.equals(witch.getUniqueId().toString());
    }

    /** Barrels of gingerbread towns (mod: the gingerbarrel point of interest). */
    static final String TOWN_BARREL = "alexscaves:chests/gingerbread_town";
    /** Limbs a Gingerbread Man has lost, as {@link CandyAnimations} flags. */
    static final NamespacedKey LOST_LIMBS = new NamespacedKey("bigcasares", "ac_gingerbread_lost_limbs");
    static final NamespacedKey CARRIED = new NamespacedKey("bigcasares", "ac_gingerbread_carried");

    /** What a Gingerbread Man carries (kept in its data, so the fox never eats it), or null. */
    static ItemStack carried(LivingEntity entity) {
        byte[] bytes = entity.getPersistentDataContainer().get(CARRIED, PersistentDataType.BYTE_ARRAY);
        return bytes == null ? null : ItemStack.deserializeBytes(bytes);
    }

    static void carry(LivingEntity entity, ItemStack stack) {
        if (stack == null) {
            entity.getPersistentDataContainer().remove(CARRIED);
        } else {
            entity.getPersistentDataContainer().set(CARRIED, PersistentDataType.BYTE_ARRAY, stack.serializeAsBytes());
        }
        // A Gingerbread Man carrying something is saved with its chunk (module mobs otherwise are not), so the item
        // is never lost when it unloads.
        if (entity instanceof Mob mob) {
            mob.setPersistent(stack != null);
            mob.setRemoveWhenFarAway(stack == null);
        }
    }

    /** Vanilla fox goals that would fight the Gingerbread Man's own AI (running from players, sleeping, hunting). */
    private static final Set<String> FOX_GOALS_REMOVED = Set.of("avoid", "sleep", "breed", "stalk", "pounce", "berr",
        "search", "seek_shelter", "follow_parent", "defend_trusted", "hunt", "perch", "fox_float_down", "faceplant",
        "eat", "panic");

    static void stripFoxGoals(Mob mob) {
        var goals = org.bukkit.Bukkit.getMobGoals();
        for (var goal : List.copyOf(goals.getAllGoals(mob))) {
            String key = goal.getKey().getNamespacedKey().getKey();
            if (FOX_GOALS_REMOVED.stream().anyMatch(key::contains)) {
                goals.removeGoal(mob, goal);
            }
        }
        for (var goal : List.copyOf(goals.getAllGoals(mob, com.destroystokyo.paper.entity.ai.GoalType.TARGET))) {
            goals.removeGoal(mob, goal);
        }
    }

    /**
     * Gingerbread Man (mod {@code GingerbreadManStealGoal}, {@code FleeGoal}, {@code StoreStolenItemsGoal},
     * {@code AttackGoal}): with empty hands, now and then (1 in 60 a tick, then 120-240 ticks of rest) it goes for the
     * nearest player within 20 blocks carrying sweets, takes one and runs from them for 120-180 ticks; then it puts what
     * it carries in a gingerbread town barrel within 32 blocks (giving up after 10 seconds). Hurt, it fights back: one
     * hit and it runs again. Possessed by a Licowitch it fights her target.
     */
    static final class GingerbreadMan implements ModMobBehaviour {
        private final ModMobType type;
        private int stealCooldown;
        private Player stealing;
        private LivingEntity fighting;
        private Entity fleeing;
        private int fleeFor;
        private Location barrel;
        private int barrelCooldown;
        private int barrelTime;
        private int attackCooldown;
        private final MobAnimator animation = new MobAnimator();
        private int sitFor;
        private int idleAction;
        private int idleElapsed;
        private boolean idleJump;
        private int lostLimbs;
        private boolean limbsLoaded;

        GingerbreadMan(ModMobType type) {
            this.type = type;
        }

        /** Mod {@code isCarryingItem} / {@code isSitting}: carrying a stolen sweet, or resting. */
        static String state(boolean carrying, boolean sitting) {
            return state(carrying, sitting, false);
        }

        /**
         * With a limb lost (mod {@code hasLostLimb}) the loop that hides them plays unless it carries or sits. The model
         * animation hides all four limbs, as it was sampled with every flag set.
         */
        static String state(boolean carrying, boolean sitting, boolean lostLimb) {
            if (carrying) {
                return "carry_item";
            }
            if (sitting) {
                return "sit";
            }
            return lostLimb ? "lost_limb" : null;
        }

        /** Mod idle animations: not sitting jump 25%, fall over 25%, wave left or right 25% each; sitting only waves. */
        static String idleAnimation(float roll, boolean sitting) {
            if (!sitting) {
                if (roll < 0.25F) {
                    return "idle_jump";
                }
                if (roll < 0.5F) {
                    return "idle_fall_over";
                }
                return roll < 0.75F ? "idle_wave_left" : "idle_wave_right";
            }
            return roll < 0.5F ? "idle_wave_left" : "idle_wave_right";
        }

        /** Mod animation lengths in ticks (jump 20, fall over 50, waves 35). */
        static int idleTicks(String animation) {
            return switch (animation) {
                case "idle_jump" -> 20;
                case "idle_fall_over" -> 50;
                default -> 35;
            };
        }

        /** Mod {@code tick}: sits for 100-180 ticks or plays an idle animation, 1 in 150 a tick while still and empty-handed. */
        private void idle(Fox fox, ModMobContext context, int ticks, boolean busy, boolean moving) {
            idleAction = Math.max(0, idleAction - ticks);
            if (idleJump) {
                idleElapsed += ticks;
                if (idleElapsed >= 5) {
                    idleJump = false;
                    // Mod: the hop happens on tick 5 of the animation, from the ground.
                    if (fox.isOnGround()) {
                        fox.setVelocity(fox.getVelocity().setY(0.42));
                    }
                }
            }
            sitFor = Math.max(0, sitFor - ticks);
            if (busy) {
                sitFor = 0;
                return;
            }
            if (sitFor > 0) {
                fox.getPathfinder().stopPathfinding();
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (!moving && idleAction <= 0 && random.nextDouble() < 1.0 - Math.pow(149.0 / 150.0, ticks)) {
                if (random.nextInt(3) == 0 && sitFor == 0) {
                    sitFor = 100 + random.nextInt(80);
                    return;
                }
                String idle = idleAnimation(random.nextFloat(), sitFor > 0);
                idleAction = idleTicks(idle);
                idleElapsed = 0;
                idleJump = idle.equals("idle_jump");
                animation.action(fox, context, idle);
            }
        }

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            loseLimb(entity, context, event);
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                Entity damager = byEntity.getDamager() instanceof org.bukkit.entity.Projectile projectile
                    && projectile.getShooter() instanceof Entity shooter ? shooter : byEntity.getDamager();
                if (damager instanceof LivingEntity attacker && attacker != entity) {
                    fighting = attacker;
                    stealing = null;
                }
            }
        }

        private void loadLimbs(LivingEntity entity) {
            if (!limbsLoaded) {
                limbsLoaded = true;
                lostLimbs = entity.getPersistentDataContainer().getOrDefault(LOST_LIMBS, PersistentDataType.INTEGER, 0);
            }
        }

        /** Mod {@code hurt}: a hit may cost a leg or an arm (kept in the mob's data), with a burst of crumbs. */
        private void loseLimb(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            if (event.isCancelled()) {
                return;
            }
            loadLimbs(entity);
            double damage = event.getFinalDamage();
            int after = CandyAnimations.afterHit(lostLimbs, damage, entity.getHealth() - damage <= 0.0,
                ThreadLocalRandom.current());
            if (after == lostLimbs) {
                return;
            }
            lostLimbs = after;
            entity.getPersistentDataContainer().set(LOST_LIMBS, PersistentDataType.INTEGER, after);
            context.mobs().createItem("ac_gingerbread_crumbs", 1).ifPresent(crumbs -> entity.getWorld().spawnParticle(
                Particle.ITEM, entity.getLocation().add(0, entity.getHeight() * 0.5, 0), 8, 0.2, 0.2, 0.2, 0.05, crumbs));
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            if (!(entity instanceof Fox fox)) {
                return;
            }
            loadLimbs(fox);
            fox.setSleeping(false);
            fox.setSitting(false);
            boolean carrying = fox.getPersistentDataContainer().has(CARRIED, PersistentDataType.BYTE_ARRAY);
            boolean moving = animation.moving();
            idle(fox, context, ticks, carrying || fleeFor > 0 || fighting != null || stealing != null, moving);
            animation.update(fox, context, ticks, state(carrying, sitFor > 0, lostLimbs != 0));
            stealCooldown = Math.max(0, stealCooldown - ticks);
            barrelCooldown = Math.max(0, barrelCooldown - ticks);
            attackCooldown = Math.max(0, attackCooldown - ticks);
            if (fox.getPersistentDataContainer().has(POSSESSED_BY) && fox.getTarget() != null && fox.getTarget().isValid()) {
                fighting = fox.getTarget();
            }
            if (fleeFor > 0) {
                flee(fox, ticks);
                return;
            }
            if (fighting != null) {
                fight(fox, context);
                return;
            }
            ItemStack held = carried(fox);
            if (held != null) {
                store(fox, held, ticks);
                return;
            }
            if (stealing != null) {
                steal(fox, context);
            } else if (stealCooldown <= 0 && ThreadLocalRandom.current().nextDouble() < 1.0 - Math.pow(59.0 / 60.0, ticks)) {
                stealCooldown = 120 + ThreadLocalRandom.current().nextInt(120);
                stealing = findTarget(fox, context);
            }
        }

        private static boolean robbable(Player player) {
            return player.isValid() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
        }

        private Player findTarget(Fox fox, ModMobContext context) {
            Player best = null;
            double bestDistance = 20.0;
            for (Player player : fox.getWorld().getPlayers()) {
                if (!robbable(player)) {
                    continue;
                }
                double distance = player.getLocation().distance(fox.getLocation());
                if (distance < bestDistance && hasSweets(player, context)) {
                    best = player;
                    bestDistance = distance;
                }
            }
            return best;
        }

        /** Mod: the player's main inventory (not armour or the off hand). */
        private static boolean hasSweets(Player player, ModMobContext context) {
            for (ItemStack stack : player.getInventory().getStorageContents()) {
                if (steals(context, stack)) {
                    return true;
                }
            }
            return false;
        }

        private void steal(Fox fox, ModMobContext context) {
            if (!robbable(stealing) || stealing.getWorld() != fox.getWorld() || !hasSweets(stealing, context)
                || stealing.getLocation().distance(fox.getLocation()) > 24.0) {
                stealing = null;
                return;
            }
            double distance = fox.getLocation().distance(stealing.getLocation());
            if (distance < stealing.getWidth() + 1.0 && fox.hasLineOfSight(stealing)) {
                List<Integer> slots = new ArrayList<>();
                ItemStack[] contents = stealing.getInventory().getStorageContents();
                for (int i = 0; i < contents.length; i++) {
                    if (steals(context, contents[i])) {
                        slots.add(i);
                    }
                }
                if (!slots.isEmpty()) {
                    int slot = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
                    ItemStack stack = contents[slot];
                    ItemStack one = stack.clone();
                    one.setAmount(1);
                    stack.setAmount(stack.getAmount() - 1);
                    stealing.getInventory().setItem(slot, stack.getAmount() <= 0 ? null : stack);
                    carry(fox, one);
                    context.mobs().playSound(fox, type, "talk");
                    fleeFrom(stealing);
                }
                stealing = null;
            } else {
                fox.getPathfinder().moveTo(stealing, 1.0);
            }
        }

        private void fight(Fox fox, ModMobContext context) {
            if (!fighting.isValid() || fighting.isDead() || fighting.getWorld() != fox.getWorld()
                || fighting.getLocation().distance(fox.getLocation()) > 32.0
                || fighting instanceof Player player && !robbable(player)) {
                fighting = null;
                return;
            }
            double distance = fox.getLocation().distance(fighting.getLocation());
            if (distance < fox.getWidth() + fighting.getWidth() + 1.0 && fox.hasLineOfSight(fighting)) {
                if (attackCooldown <= 0) {
                    attackCooldown = 10;
                    CandyMobs.abilityDamage(fighting, CandyMobs.attribute(fox, org.bukkit.attribute.Attribute.ATTACK_DAMAGE, 2.0), fox);
                    context.mobs().playSound(fox, type, "attack");
                    animation.action(fox, context, CandyAnimations.mainHandSwing(fox.isLeftHanded()));
                    fleeFrom(fighting);
                    fighting = null;
                }
            } else {
                fox.getPathfinder().moveTo(fighting, 1.0);
            }
        }

        private void fleeFrom(Entity from) {
            fleeing = from;
            fleeFor = 120 + ThreadLocalRandom.current().nextInt(60);
        }

        private void flee(Fox fox, int ticks) {
            fleeFor -= ticks;
            if (fleeing == null || !fleeing.isValid() || fleeing.getWorld() != fox.getWorld()) {
                fleeFor = 0;
                return;
            }
            Vector away = fox.getLocation().toVector().subtract(fleeing.getLocation().toVector()).setY(0);
            if (away.lengthSquared() < 1.0E-4) {
                away = new Vector(1, 0, 0);
            }
            double distance = fox.getLocation().distance(fleeing.getLocation());
            Location to = fox.getLocation().add(away.normalize().multiply(8.0));
            fox.getPathfinder().moveTo(to, distance > 5.0 ? 1.0 : 1.5);
        }

        /** Mod {@code GingerbreadManStoreStolenItemsGoal}: the nearest town barrel with room within 32 blocks. */
        private void store(Fox fox, ItemStack held, int ticks) {
            if (barrel != null && barrel.getWorld() != fox.getWorld()) {
                barrel = null;
            }
            if (barrel == null && barrelCooldown <= 0) {
                barrelCooldown = 20 + ThreadLocalRandom.current().nextInt(30);
                barrel = nearestBarrel(fox.getLocation(), held);
                barrelTime = 0;
            }
            if (barrel == null) {
                return;
            }
            barrelTime += ticks;
            Barrel container = townBarrel(barrel.getBlock(), held);
            if (container == null || barrelTime > 200) {
                // Gone, full or out of reach for 10 seconds: look again later.
                barrel = null;
                barrelCooldown = 100;
                return;
            }
            if (fox.getLocation().distance(barrel.clone().add(0.5, 0.5, 0.5)) < 2.0) {
                var leftover = container.getInventory().addItem(held.clone());
                carry(fox, leftover.isEmpty() ? null : leftover.values().iterator().next());
                barrel = null;
            } else {
                fox.getPathfinder().moveTo(barrel.clone().add(0.5, 1, 0.5), 1.0);
            }
        }

        /** A gingerbread town barrel already opened (no loot left to roll) with room for the item, or null. */
        private static Barrel townBarrel(Block block, ItemStack held) {
            if (!(block.getState(false) instanceof Barrel container) || container.getLootTable() != null
                || !TOWN_BARREL.equals(container.getPersistentDataContainer().get(
                dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomePopulator.STRUCTURE_CONTAINER,
                PersistentDataType.STRING))) {
                return null;
            }
            return hasRoom(container.getInventory(), held) ? container : null;
        }

        private static Location nearestBarrel(Location from, ItemStack held) {
            Location best = null;
            double bestDistance = 32.0 * 32.0;
            int cx = from.getBlockX() >> 4;
            int cz = from.getBlockZ() >> 4;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (!from.getWorld().isChunkLoaded(cx + dx, cz + dz)) {
                        continue;
                    }
                    for (BlockState state : from.getWorld().getChunkAt(cx + dx, cz + dz).getTileEntities(false)) {
                        if (state instanceof Barrel && townBarrel(state.getBlock(), held) != null) {
                            double distance = state.getLocation().distanceSquared(from);
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                best = state.getLocation();
                            }
                        }
                    }
                }
            }
            return best;
        }

        private static boolean hasRoom(Inventory inventory, ItemStack stack) {
            for (ItemStack slot : inventory.getContents()) {
                if (slot == null || slot.getType().isAir()
                    || slot.isSimilar(stack) && slot.getAmount() < slot.getMaxStackSize()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Licowitch (mod {@code LicowitchAttackGoal}): with the target in sight it casts, at most one spell at a time,
     * spinning peppermints (every 80 ticks: three peppermints that circle out around the target or fly in a fan, 3
     * damage each) or a hex (every 200 ticks, within 7 blocks: half the time, while it has fewer than 3, it possesses
     * a new candy mob next to the target; otherwise a hex that crawls along the ground towards the target, hurting 1 a
     * tick and 6 when it bursts). Its potions are Hunger V splashes ({@link CandyMobListener}). Its possessed mobs
     * fight for it and die with it.
     */
    static final class Licowitch implements ModMobBehaviour {
        private final ModMobType type;
        private int peppermintCooldown;
        private int hexCooldown;
        private int casting = -1;
        private int spell;
        private final Set<UUID> possessed = new HashSet<>();
        private final MobAnimator animation = new MobAnimator();
        private int uncrossed;

        /** Mod {@code ANIMATION_SPELL_0} (peppermints, 45 ticks), {@code SPELL_1} (hex, 50) and the potion swing (20). */
        static final int SPELL_0_TICKS = 45;
        static final int SPELL_1_TICKS = 50;
        static final int SWING_TICKS = 20;

        /** Mod {@code areArmsCrossed}: they unfold for every spell and swing, and fold again afterwards. */
        static String state(int uncrossedTicks) {
            return uncrossedTicks > 0 ? "arms_uncrossed" : null;
        }

        /** Mod {@code LicowitchAttackGoal}: the potion throw swings the main arm (a witch is right-handed). */
        void potionSwing(LivingEntity entity, ModMobContext context) {
            uncrossed = Math.max(uncrossed, SWING_TICKS);
            animation.base(entity, context, state(uncrossed));
            animation.action(entity, context, CandyAnimations.mainHandSwing(entity instanceof Mob mob && mob.isLeftHanded()));
        }

        private void cast(LivingEntity entity, ModMobContext context, int spellType) {
            spell = spellType;
            casting = 0;
            int length = spellType == 1 ? SPELL_0_TICKS : SPELL_1_TICKS;
            uncrossed = Math.max(uncrossed, length);
            animation.base(entity, context, state(uncrossed));
            animation.action(entity, context, spellType == 1 ? "spell_0" : "spell_1");
        }

        Licowitch(ModMobType type) {
            this.type = type;
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            peppermintCooldown = Math.max(0, peppermintCooldown - ticks);
            hexCooldown = Math.max(0, hexCooldown - ticks);
            uncrossed = Math.max(0, uncrossed - ticks);
            animation.update(entity, context, ticks, state(uncrossed));
            LivingEntity target = entity instanceof Mob mob ? mob.getTarget() : null;
            commandPossessed(entity, target);
            if (target == null || !target.isValid() || target.getWorld() != entity.getWorld()) {
                casting = -1;
                return;
            }
            double distance = entity.getLocation().distance(target.getLocation());
            if (casting >= 0) {
                casting += ticks;
                if (spell == 1 && casting >= 18) {
                    peppermints(entity, context, target);
                    casting = -1;
                } else if (spell == 2 && casting >= 36) {
                    hex(entity, context, target);
                    casting = -1;
                }
                return;
            }
            if (!entity.hasLineOfSight(target)) {
                return;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (peppermintCooldown <= 0 && distance < 10.0 && random.nextBoolean()) {
                peppermintCooldown = 80;
                cast(entity, context, 1);
            } else if (hexCooldown <= 0 && distance < 7.0 && random.nextBoolean()) {
                hexCooldown = 200;
                cast(entity, context, 2);
            }
        }

        private void commandPossessed(LivingEntity entity, LivingEntity target) {
            possessed.removeIf(id -> {
                Entity mob = entity.getServer().getEntity(id);
                if (!(mob instanceof Mob living) || !mob.isValid()) {
                    return true;
                }
                if (target != null && target.isValid() && living.getTarget() != target) {
                    living.setTarget(target);
                }
                mob.getWorld().spawnParticle(Particle.DUST, mob.getLocation().add(0, mob.getHeight(), 0), 1, 0.2, 0.2, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(0x9B, 0x3F, 0xC9), 1.0f));
                return false;
            });
        }

        @Override
        public void close(LivingEntity entity, ModMobContext context) {
            // Mod: the possessed die with the Licowitch (not when it only unloads).
            if (entity != null && entity.isDead()) {
                for (UUID id : possessed) {
                    Entity mob = entity.getServer().getEntity(id);
                    if (mob instanceof LivingEntity living && living.isValid()) {
                        living.setHealth(0.0);
                    }
                }
            }
            possessed.clear();
        }

        /** Mod {@code createRandomPossessedMob}. */
        static String randomPossessed(float chance) {
            if (chance < 0.1F) {
                return "candicorn";
            } else if (chance < 0.2F) {
                return "gummy_bear";
            } else if (chance < 0.5F) {
                return "gumbeeper";
            } else if (chance < 0.65F) {
                return "caramel_cube";
            }
            return chance < 0.75F ? "gingerbread_man" : "caniac";
        }

        private void hex(LivingEntity entity, ModMobContext context, LivingEntity target) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (possessed.size() < 3 && random.nextBoolean()) {
                Location spot = summonSpot(target, 10);
                if (spot != null) {
                    String id = randomPossessed(random.nextFloat());
                    var summoned = context.mobs().spawn(id, spot);
                    if (summoned.isPresent()) {
                        LivingEntity mob = summoned.get();
                        mob.getPersistentDataContainer().set(POSSESSED_BY, PersistentDataType.STRING, entity.getUniqueId().toString());
                        if (mob instanceof Mob living) {
                            living.setTarget(target);
                        }
                        possessed.add(mob.getUniqueId());
                        entity.getWorld().playSound(entity.getLocation(), "alexscaves:licowitch_cast_summon", 0.3f, 1.0f);
                        entity.getWorld().spawnParticle(Particle.WITCH, spot.clone().add(0, 0.5, 0), 30, 0.4, 0.6, 0.4, 0.1);
                        return;
                    }
                }
            }
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:licowitch_cast_hex", 0.3f, 1.0f);
            context.tasks().repeat(new Hex(entity, ground(entity.getLocation()), ground(target.getLocation()), context), 0L, 1L,
                () -> { });
        }

        private static Location ground(Location from) {
            Location at = from.clone();
            for (int i = 0; i < 16 && at.getBlockY() > at.getWorld().getMinHeight() && at.getBlock().isPassable(); i++) {
                at.add(0, -1, 0);
            }
            return at.add(0, 1, 0);
        }

        private static Location summonSpot(LivingEntity target, int range) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 15; i++) {
                Location ground = ground(target.getLocation().add(random.nextInt(range * 2) - range,
                    target.getEyeHeight() + random.nextInt(4), random.nextInt(range * 2) - range));
                if (target.hasLineOfSight(ground.clone().add(0, 1, 0)) && ground.getBlock().isPassable()) {
                    return ground;
                }
            }
            return null;
        }

        private void peppermints(LivingEntity entity, ModMobContext context, LivingEntity target) {
            boolean spinning = ThreadLocalRandom.current().nextBoolean();
            Vector toward = target.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0);
            double yaw = Math.atan2(toward.getZ(), toward.getX());
            ItemStack look = context.mobs().createItem("ac_large_peppermint", 1).orElse(new ItemStack(Material.SNOWBALL));
            for (int i = 0; i < 3; i++) {
                Peppermint peppermint = new Peppermint(entity, target, look, spinning, yaw + Math.toRadians((i - 1) * 30.0), i * 120.0, context);
                context.tasks().repeat(peppermint, 0L, 1L, peppermint::discard);
            }
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:licowitch_cast_peppermint", 0.3f, 1.0f);
        }
    }

    static boolean hurtableBy(LivingEntity witch, Entity other, ModMobContext context) {
        return other instanceof LivingEntity living && other != witch && !friendly(witch, other) && living.isValid()
            && !(living instanceof Player player && (player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR))
            // Mod isFriendlyFire: witches (Licowitches among them) are never hurt by her spells.
            && !(living instanceof org.bukkit.entity.Witch);
    }

    /**
     * Mod {@code SugarStaffHexEntity}: a hex sliding along the ground from the witch towards where the target stood
     * (a quarter of the way each tick, slowing), hurting 1 within its reach for 100 ticks and 6 with a knockback in its
     * last 5 ticks.
     */
    static final class Hex extends BukkitRunnable {
        private final LivingEntity owner;
        private final Location at;
        private Vector velocity;
        private final ModMobContext context;
        private int age;

        Hex(LivingEntity owner, Location from, Location to, ModMobContext context) {
            this.owner = owner;
            this.at = from.clone();
            this.at.setY(to.getY());
            this.velocity = to.toVector().subtract(from.toVector()).multiply(new Vector(0.25, 0, 0.25));
            this.context = context;
        }

        @Override
        public void run() {
            if (age++ >= 100 || at.getWorld() == null || !at.isChunkLoaded()) {
                cancel();
                return;
            }
            boolean burst = age > 95;
            at.add(velocity);
            velocity.multiply(0.7);
            at.getWorld().spawnParticle(burst ? Particle.WITCH : Particle.DUST, at.clone().add(0, 0.1, 0), burst ? 12 : 3, 1.2, 0.05, 1.2, 0,
                burst ? null : new Particle.DustOptions(Color.fromRGB(0xB0, 0x4E, 0xE0), 1.3f));
            for (Entity near : at.getWorld().getNearbyEntities(at, 2.0, 1.0, 2.0)) {
                if (hurtableBy(owner, near, context) && near.getLocation().distance(at) <= 4.0) {
                    LivingEntity living = (LivingEntity) near;
                    if (CandyMobs.abilityDamage(living, burst ? 6.0 : 1.0, owner) && burst) {
                        knockFrom(living, at, 0.9);
                    }
                }
            }
        }
    }

    /** Peppermints still flying, removed when the module stops. */
    static final Set<ItemDisplay> LIVE_PEPPERMINTS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void removePeppermints() {
        for (ItemDisplay display : List.copyOf(LIVE_PEPPERMINTS)) {
            display.remove();
        }
        LIVE_PEPPERMINTS.clear();
    }

    /**
     * Mod {@code SpinningPeppermintEntity} as cast by the Licowitch: spinning ones circle (radius 3, 7 degrees a tick)
     * around a point that starts where they appear and creeps towards the target's eyes; straight ones fly out in a fan,
     * fall and bounce along the ground. They hurt 3 with a small knockback, stop at blocks, and last 200 ticks.
     */
    static final class Peppermint extends BukkitRunnable {
        private final LivingEntity owner;
        private final LivingEntity target;
        private final ItemDisplay display;
        private final boolean spinning;
        private final double startAngle;
        private final ModMobContext context;
        private final Location centre;
        private Vector velocity;
        private double spin;
        private int age;

        Peppermint(LivingEntity owner, LivingEntity target, ItemStack look, boolean spinning, double yaw, double startAngle,
                   ModMobContext context) {
            this.owner = owner;
            this.target = target;
            this.spinning = spinning;
            this.startAngle = startAngle;
            this.context = context;
            Location from = owner.getLocation().add(0, 1.2, 0);
            this.centre = from.clone();
            this.velocity = new Vector(Math.cos(yaw), 0, Math.sin(yaw)).multiply(0.08);
            this.display = from.getWorld().spawn(from, ItemDisplay.class, shown -> {
                shown.setItemStack(look);
                shown.setPersistent(false);
                shown.setTeleportDuration(1);
            });
            LIVE_PEPPERMINTS.add(display);
        }

        void discard() {
            display.remove();
            LIVE_PEPPERMINTS.remove(display);
        }

        @Override
        public void run() {
            if (age++ >= 200 || !display.isValid() || !owner.isValid() || display.getWorld() != owner.getWorld()) {
                discard();
                cancel();
                return;
            }
            Location at = display.getLocation();
            Vector step;
            if (spinning) {
                if (target.isValid() && target.getWorld() == at.getWorld()) {
                    Vector toward = target.getEyeLocation().toVector().subtract(centre.toVector());
                    if (toward.length() > 0.05) {
                        centre.add(toward.normalize().multiply(0.05));
                    }
                }
                double radius = 3.0 * Math.min(1.0, age / 30.0);
                spin += 7.0;
                double angle = Math.toRadians(startAngle + spin);
                Location circle = centre.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                step = circle.toVector().subtract(at.toVector()).multiply(0.05 * 7.0);
            } else {
                velocity.add(velocity.clone().setY(0).normalize().multiply(0.08)).multiply(0.9).add(new Vector(0, -0.08, 0));
                if (!at.clone().add(0, velocity.getY(), 0).getBlock().isPassable()) {
                    velocity.setY(velocity.getY() + 0.9).multiply(new Vector(0.4, 1.0, 0.4));
                }
                step = velocity;
            }
            Location next = at.clone().add(step);
            if (next.getBlock().isPassable()) {
                display.teleport(next);
                at = next;
            } else if (!spinning) {
                velocity.setX(0).setZ(0);
            }
            for (Entity near : at.getWorld().getNearbyEntities(at, 0.5, 0.5, 0.5)) {
                if (hurtableBy(owner, near, context) && CandyMobs.abilityDamage((LivingEntity) near, 3.0, owner)) {
                    knockFrom((LivingEntity) near, at, 0.3);
                }
            }
        }
    }

    /** Vanilla knockback away from a point (the peppermint or the hex, as in the mod). */
    static void knockFrom(LivingEntity target, Location from, double strength) {
        Vector away = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (away.lengthSquared() > 1.0E-6) {
            target.setVelocity(target.getVelocity().add(away.normalize().multiply(strength))
                .setY(Math.min(0.4, target.getVelocity().getY() + strength)));
        }
    }
}
