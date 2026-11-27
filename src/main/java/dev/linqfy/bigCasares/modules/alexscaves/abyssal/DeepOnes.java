package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobAnimator;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Deep Ones of the Abyssal Chasm (mod {@code DeepOneEntity}, {@code DeepOneKnightEntity}, {@code DeepOneMageEntity}
 * over {@code DeepOneBaseEntity} and its {@code DeepOne*Goal}s). They are amphibious: they swim in any direction in
 * water and walk on the floor. What they do to a player depends on the reputation the player has with them: they stalk
 * (and vanish behind an ink bomb when cornered), turn on the aggressive, keep their distance from the neutral and help
 * the friends. They also barter: a pearl left on an {@link AbyssalAltar} is taken and paid for with their loot.
 *
 * <p>Each Deep One runs the mod's goals in the mod's priority order (attack, barter, react to player, disappear,
 * wander); a goal with a lower number interrupts the running one. The mod's summoned state (magic conch) is not
 * ported.
 */
public final class DeepOnes {
    static final String BIOME_BASE = "drowned";

    public static final ModMobType DEEP_ONE = new ModMobType("deep_one", AbyssalMobs.BIOME, BIOME_BASE, SpawnCategory.MONSTER,
        Map.of("max_health", 30.0, "movement_speed", 0.25, "attack_damage", 3.0, "scale", 1.13, "spawn_reinforcements", 0.0),
        "alexscaves_deep_one", List.of());
    public static final ModMobType KNIGHT = new ModMobType("deep_one_knight", AbyssalMobs.BIOME, BIOME_BASE, SpawnCategory.MONSTER,
        Map.of("max_health", 60.0, "movement_speed", 0.25, "attack_damage", 5.0, "scale", 1.28, "spawn_reinforcements", 0.0),
        "alexscaves_deep_one_knight", List.of());
    public static final ModMobType MAGE = new ModMobType("deep_one_mage", AbyssalMobs.BIOME, BIOME_BASE, SpawnCategory.MONSTER,
        Map.of("max_health", 80.0, "movement_speed", 0.25, "attack_damage", 4.0, "scale", 1.28, "spawn_reinforcements", 0.0),
        "alexscaves_deep_one_mage", List.of());
    public static final List<ModMobType> ALL = List.of(DEEP_ONE, KNIGHT, MAGE);

    static final String SQUIRT = "minecraft:entity.squid.squirt";
    static final int TRADE_DURATION = 55;
    static final int MAGE_TRADE_DURATION = 75;

    private DeepOnes() {
    }

    public static void register(ModMobCatalog catalog) {
        catalog.register(DEEP_ONE, (entity, type) -> {
            own(entity);
            return new Plain();
        });
        catalog.register(KNIGHT, (entity, type) -> {
            own(entity);
            Knight knight = new Knight(entity);
            if (entity.getTicksLived() < 5 && entity.getEquipment() != null && entity.getEquipment().getItemInMainHand().isEmpty()) {
                // Mod finalizeSpawn: an ortholance 3 times in 10, otherwise a trident.
                if (ThreadLocalRandom.current().nextFloat() > 0.7F) {
                    knight.pendingOrtholance = true;
                } else {
                    entity.getEquipment().setItemInMainHand(new ItemStack(Material.TRIDENT));
                }
            }
            return knight;
        });
        catalog.register(MAGE, (entity, type) -> {
            own(entity);
            return new Mage();
        });
        for (ModMobType type : ALL) {
            catalog.spawnsInWater(type.id());
            // Mod checkDeepOneSpawnRules: water below, not in peaceful, 1 in 110 and more than 80 under the sea.
            catalog.spawnRule(type.id(), (block, random) -> block.getType() == Material.WATER
                && block.getRelative(0, -1, 0).getType() == Material.WATER
                // Monster.isDarkEnoughToSpawn: sky light above a random 0..31 stops it (block light: ModSpawner.fits).
                && block.getLightFromSky() <= random.nextInt(32)
                && block.getWorld().getDifficulty() != Difficulty.PEACEFUL && random.nextInt(110) == 0
                && block.getY() < AbyssalMobs.SEA_LEVEL - 80);
        }
    }

    /** The Deep Ones run their own goals: the base mob's (drowned) are removed. */
    static void own(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            Bukkit.getMobGoals().removeAllGoals(mob);
            Bukkit.getMobGoals().removeAllGoals(mob, com.destroystokyo.paper.entity.ai.GoalType.TARGET);
            mob.setAware(true);
        }
    }

    // --- shared rules --------------------------------------------------------------------------------------

    /** Mod {@code isAlliedTo}: a Deep One is allied to the other Deep Ones and to the players they find helpful. */
    static boolean friendly(ModMobContext context, LivingEntity owner, Entity other) {
        if (context.mobs().typeOf(other).filter(ALL::contains).isPresent()) {
            return true;
        }
        if (context.mobs().typeOf(owner).filter(ALL::contains).isPresent() && other instanceof Player player) {
            return DeepOneReputation.of(context.plugin()).reactionOf(player.getUniqueId()) == DeepOneReaction.HELPFUL;
        }
        return other.equals(owner);
    }

    /** Mod {@code addReputation}: changes the reputation and tells the player when the way Deep Ones see them changes. */
    public static void addReputation(ModMobContext context, UUID player, int amount) {
        DeepOneReputation store = DeepOneReputation.of(context.plugin());
        DeepOneReaction before = store.reactionOf(player);
        store.add(player, amount);
        DeepOneReaction after = store.reactionOf(player);
        if (before != after) {
            Player online = Bukkit.getPlayer(player);
            if (online != null) {
                online.sendActionBar(Component.text(reactionMessage(after)));
            }
        }
    }

    static String reactionMessage(DeepOneReaction reaction) {
        return switch (reaction) {
            case STALKING -> "Los profundos ahora te tratan con cautela";
            case AGGRESSIVE -> "Los profundos ahora te tratan con agresividad";
            case NEUTRAL -> "Los profundos ahora te tratan con neutralidad";
            case HELPFUL -> "Los profundos ahora te tratan como un amigo";
        };
    }

    /** One barter result as an item stack (with its random enchantment or potion). */
    static Optional<ItemStack> lootStack(ModMobContext context, DeepOneBarter.Result result) {
        DeepOneBarter.Entry entry = result.entry();
        Optional<ItemStack> made = context.mobs().createItem(entry.item(), result.count());
        if (made.isEmpty()) {
            return made;
        }
        ItemStack stack = made.get();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (entry.special() == DeepOneBarter.Special.ENCHANT) {
            List<Enchantment> options = new ArrayList<>();
            if (entry.detail().isEmpty()) {
                // Vanilla enchant_randomly without a list: the discoverable enchantments that fit the item.
                for (Enchantment enchantment : Registry.ENCHANTMENT) {
                    if (enchantment.isDiscoverable() && (stack.getType() == Material.BOOK || enchantment.canEnchantItem(stack))) {
                        options.add(enchantment);
                    }
                }
            } else {
                for (String name : entry.detail().split(",")) {
                    Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));
                    if (enchantment != null) {
                        options.add(enchantment);
                    }
                }
            }
            if (!options.isEmpty()) {
                Enchantment pick = options.get(random.nextInt(options.size()));
                int level = pick.getStartLevel() + random.nextInt(pick.getMaxLevel() - pick.getStartLevel() + 1);
                if (stack.getType() == Material.BOOK) {
                    // Vanilla enchant_randomly turns a book into an enchanted book.
                    stack = new ItemStack(Material.ENCHANTED_BOOK, stack.getAmount());
                    EnchantmentStorageMeta storage = (EnchantmentStorageMeta) stack.getItemMeta();
                    storage.addStoredEnchant(pick, level, true);
                    stack.setItemMeta(storage);
                } else {
                    stack.addUnsafeEnchantment(pick, level);
                }
            }
        } else if (entry.special() == DeepOneBarter.Special.POTION && stack.getItemMeta() instanceof PotionMeta potion) {
            potion.setBasePotionType(PotionType.valueOf(entry.detail().toUpperCase(Locale.ROOT)));
            stack.setItemMeta(potion);
        }
        return Optional.of(stack);
    }

    /** Distance between two spots, or infinity when they are in different worlds (where Location.distance would throw). */
    static double distance(Location a, Location b) {
        return a.getWorld() == b.getWorld() ? a.distance(b) : Double.POSITIVE_INFINITY;
    }

    static double distanceSquared(Location a, Location b) {
        return a.getWorld() == b.getWorld() ? a.distanceSquared(b) : Double.POSITIVE_INFINITY;
    }

    // --- the behaviour -------------------------------------------------------------------------------------

    enum Mode { NONE, ATTACK, BARTER, REACT, DISAPPEAR, WANDER }

    /** Mod {@code DeepOneBaseEntity} with its goals. */
    abstract static class Base implements ModMobBehaviour {
        // animation
        String animation;
        int animationTick;
        int animationDuration;
        // state
        long age;
        int tradingLocked;
        UUID cornering;
        UUID lastHurtBy;
        long lastHurtAge;
        boolean angry;
        boolean swimming;
        final MobAnimator anim = new MobAnimator();
        Location lastAltar;
        boolean spawnedLoot;
        ItemStack swapped = ItemStack.empty();
        Mode mode = Mode.NONE;
        org.bukkit.World currentWorld;
        // movement
        Location destination;
        double destinationSpeed = 1.0;
        long lastPath;
        // barter goal
        Location altarPos;
        int barterCooldown = 10;
        // react goal
        Player reactPlayer;
        DeepOneReaction reaction;
        boolean following;
        int refreshReaction;
        boolean beingLookedAt;
        int lookAtTime;
        int chaseTime;
        int friendlyLook;
        int executionTime;
        Vector moveTarget;
        // disappear goal
        int bombCooldown;
        int disappearIn;
        // wander goal
        Location wanderGoal;
        boolean groundTarget;

        abstract ModMobType type();

        abstract String tradeAnimation();

        abstract int tradeDuration();

        abstract List<DeepOneBarter.Entry> barterTable();

        /** Mod {@code startAttackBehavior}. */
        abstract void attack(LivingEntity entity, ModMobContext context, LivingEntity target);

        /** Mod {@code startDisappearBehavior}: true once the Deep One is done (it threw the bomb or vanished). */
        abstract boolean disappear(LivingEntity entity, ModMobContext context, Player player);

        boolean flying(LivingEntity entity) {
            return false;
        }

        // --- tick ---

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            if (!(entity instanceof Mob mob)) {
                return;
            }
            this.context = context;
            currentWorld = entity.getWorld();
            // Base loop: idle or walk, and the swim pose while it swims along (the mod's swim amount).
            anim.updateWhenMoving(entity, context, ticks, swimming && entity.isInWater() ? "swim" : null);
            age += ticks;
            tradingLocked = Math.max(0, tradingLocked - ticks);
            if (animation != null && (animationTick += ticks) >= animationDuration) {
                animation = null;
                animationTick = 0;
            }
            if (reactPlayer != null && reactPlayer.getWorld() != entity.getWorld()) {
                reactPlayer = null;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && (!target.isValid() || target.isDead() || target.getWorld() != entity.getWorld()
                || target instanceof Player player && player.getGameMode() == GameMode.SPECTATOR
                || tooFar(entity, target))) {
                mob.setTarget(null);
            }
            scanHostilePlayers(entity, context, mob, ticks);
            tickTrade(entity, context);
            selectGoal(entity, context, mob);
            if (mode != Mode.NONE) {
                tickGoal(entity, context, mob, ticks);
            } else {
                lookAtNearestPlayer(entity, mob);
            }
            drive(entity, mob, ticks);
            if (angry && ThreadLocalRandom.current().nextInt(40) == 0) {
                context.mobs().playSound(entity, type(), "hostile");
            }
        }

        private static DeepOneReaction reaction(ModMobContext context, Player player) {
            return DeepOneReputation.of(context.plugin()).reactionOf(player.getUniqueId());
        }

        /** Vanilla TargetGoal.canContinueToUse: a target further than the follow range is dropped. */
        private static boolean tooFar(LivingEntity entity, LivingEntity target) {
            AttributeInstance range = entity.getAttribute(Attribute.FOLLOW_RANGE);
            double follow = range == null ? 32.0 : range.getValue();
            return DeepOnes.distanceSquared(entity.getLocation(), target.getLocation()) > follow * follow;
        }

        /**
         * Mod DeepOneTargetHostilePlayersGoal: every so often (1 tick in 10) the nearest player within the follow
         * range whose reputation makes the Deep Ones aggressive becomes the target (not in creative).
         */
        private void scanHostilePlayers(LivingEntity entity, ModMobContext context, Mob mob, int ticks) {
            if (!noTarget(mob) || ThreadLocalRandom.current().nextInt(Math.max(1, 10 / ticks)) != 0) {
                return;
            }
            AttributeInstance range = entity.getAttribute(Attribute.FOLLOW_RANGE);
            double follow = range == null ? 32.0 : range.getValue();
            Player nearest = null;
            for (Player player : entity.getWorld().getNearbyPlayers(entity.getLocation(), follow)) {
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || player.isDead()
                    || reaction(context, player) != DeepOneReaction.AGGRESSIVE) {
                    continue;
                }
                if (nearest == null || DeepOnes.distanceSquared(entity.getLocation(), player.getLocation())
                    < DeepOnes.distanceSquared(entity.getLocation(), nearest.getLocation())) {
                    nearest = player;
                }
            }
            if (nearest != null) {
                mob.setTarget(nearest);
            }
        }

        // --- mod DeepOneBaseEntity.tick: the barter payment ---

        private void tickTrade(LivingEntity entity, ModMobContext context) {
            AbyssalAltar altars = AbyssalAltar.get(context.plugin()).orElse(null);
            if (altars != null && tradeAnimation().equals(animation) && altars.isBarterItem(entity.getEquipment().getItemInMainHand())
                && lastAltar != null) {
                Location center = lastAltar.clone().add(0.5, 0.5, 0.5);
                if (animationTick > animationDuration - 10) {
                    if (!spawnedLoot && altars.isAltar(lastAltar.getBlock())) {
                        // The altar's record is dropped once it is empty: make it again to leave the loot.
                        AbyssalAltar.Altar altar = altars.ensure(lastAltar.getBlock());
                        ItemStack loot = DeepOnes.lootStack(context, DeepOneBarter.roll(barterTable(), ThreadLocalRandom.current()))
                            .orElse(ItemStack.empty());
                        if (altar != null && altar.isEmpty()) {
                            altar.setItem(loot);
                            altar.onEntityInteract(entity, false);
                        } else if (!loot.isEmpty()) {
                            lastAltar.getWorld().dropItem(center.clone().add(0, 0.5, 0), loot);
                        }
                        spawnedLoot = true;
                    }
                    restoreSwapped(entity);
                }
                lookAt(entity, center);
            }
            if (spawnedLoot && !tradeAnimation().equals(animation)) {
                spawnedLoot = false;
            }
        }

        /** Mod altar hand-over for a Deep One: it admires the pearl in its hand and the player earns 5 reputation. */
        void receiveBarterItem(LivingEntity entity, ItemStack stack, UUID placingPlayer) {
            swapItemsForAnimation(entity, stack);
            setAnimation(entity, tradeAnimation(), tradeDuration());
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:" + type().id() + "_admire", 1.0F, 1.0F);
            if (placingPlayer != null) {
                ModMobContext context = contextOf(entity);
                if (context != null) {
                    addReputation(context, placingPlayer, 5);
                }
            }
        }

        ModMobContext context;

        ModMobContext contextOf(LivingEntity entity) {
            return context;
        }

        // --- goal selector ---

        private void selectGoal(LivingEntity entity, ModMobContext context, Mob mob) {
            this.context = context;
            if (mode != Mode.NONE && !canContinue(entity, context, mob, mode)) {
                stop(entity, mob, mode);
                mode = Mode.NONE;
            }
            for (Mode candidate : new Mode[] {Mode.ATTACK, Mode.BARTER, Mode.REACT, Mode.DISAPPEAR, Mode.WANDER}) {
                if (mode != Mode.NONE && candidate.ordinal() >= mode.ordinal()) {
                    break;
                }
                if (canUse(entity, context, mob, candidate)) {
                    if (mode != Mode.NONE) {
                        stop(entity, mob, mode);
                    }
                    mode = candidate;
                    start(entity, mob, candidate);
                    break;
                }
            }
        }

        private boolean noTarget(Mob mob) {
            LivingEntity target = mob.getTarget();
            return target == null || !target.isValid() || target.isDead();
        }

        private boolean canUse(LivingEntity entity, ModMobContext context, Mob mob, Mode goal) {
            switch (goal) {
                case ATTACK:
                    return !noTarget(mob) && tradingLocked <= 0;
                case BARTER:
                    return canUseBarter(entity, context, mob);
                case REACT:
                    return canUseReact(entity, context, mob);
                case DISAPPEAR: {
                    Player player = cornered();
                    return player != null && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                        && DeepOnes.distance(entity.getLocation(), player.getLocation()) < 10.0 && tradingLocked <= 0;
                }
                case WANDER:
                    return entity.isInWater() && noTarget(mob) && ThreadLocalRandom.current().nextInt(12) == 0 && tradingLocked <= 0;
                default:
                    return false;
            }
        }

        private boolean canContinue(LivingEntity entity, ModMobContext context, Mob mob, Mode goal) {
            switch (goal) {
                case BARTER:
                    return altarPos != null && (hasPearls(context, altarPos, false) || tradeAnimation().equals(animation)) && noTarget(mob);
                case REACT:
                    return canContinueReact(entity, mob);
                case WANDER:
                    return wanderGoal != null && destination != null && ThreadLocalRandom.current().nextInt(200) != 0 && tradingLocked <= 0;
                default:
                    return canUse(entity, context, mob, goal);
            }
        }

        private void start(LivingEntity entity, Mob mob, Mode goal) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            switch (goal) {
                case REACT:
                    chaseTime = 0;
                    executionTime = 0;
                    refreshReaction(entity, mob);
                    break;
                case DISAPPEAR:
                    swimming = false;
                    bombCooldown = 10;
                    disappearIn = 20;
                    break;
                case WANDER:
                    groundTarget = entity.isOnGround() ? random.nextFloat() < 0.7F : random.nextFloat() < 0.2F;
                    wanderGoal = findSwimToPos(entity);
                    break;
                default:
                    break;
            }
        }

        private void stop(LivingEntity entity, Mob mob, Mode goal) {
            switch (goal) {
                case ATTACK:
                    angry = false;
                    break;
                case REACT:
                    chaseTime = 0;
                    executionTime = 0;
                    following = false;
                    beingLookedAt = false;
                    moveTarget = null;
                    angry = false;
                    break;
                case DISAPPEAR:
                    cornering = null;
                    break;
                default:
                    break;
            }
        }

        private void tickGoal(LivingEntity entity, ModMobContext context, Mob mob, int ticks) {
            switch (mode) {
                case ATTACK -> {
                    LivingEntity target = mob.getTarget();
                    if (target != null) {
                        lookAt(entity, target.getEyeLocation());
                        attack(entity, context, target);
                        angry = true;
                    }
                }
                case BARTER -> tickBarter(entity, context, mob);
                case REACT -> tickReact(entity, context, mob, ticks);
                case DISAPPEAR -> tickDisappear(entity, context, ticks);
                case WANDER -> {
                    if (wanderGoal != null) {
                        goTo(entity, wanderGoal, 1.0);
                        if (groundTarget) {
                            if (entity.isOnGround()) {
                                swimming = false;
                            } else if (entity.getLocation().distanceSquared(wanderGoal) < 4.0) {
                                entity.setVelocity(entity.getVelocity().multiply(0.8).add(new Vector(0, -0.1, 0)));
                            }
                        } else {
                            swimming = true;
                        }
                        if (entity.getLocation().distanceSquared(wanderGoal) < 2.25) {
                            destination = null;
                        }
                    }
                }
                default -> {
                }
            }
        }

        // --- barter goal ---

        private static boolean hasPearls(ModMobContext context, Location pos, boolean timed) {
            AbyssalAltar altars = AbyssalAltar.get(context.plugin()).orElse(null);
            if (altars == null) {
                return false;
            }
            Block block = pos.getBlock();
            AbyssalAltar.Altar altar = altars.at(block).orElse(null);
            if (altar == null) {
                return false;
            }
            if (timed && block.getWorld().getFullTime() - altar.lastInteractionTime() < 100L) {
                return false;
            }
            return altars.isBarterItem(altar.item()) && altars.isAltar(block) && !altar.active();
        }

        private boolean canUseBarter(LivingEntity entity, ModMobContext context, Mob mob) {
            AbyssalAltar altars = AbyssalAltar.get(context.plugin()).orElse(null);
            if (altars == null || !noTarget(mob) || animation != null) {
                return false;
            }
            barterCooldown -= 2;
            if (barterCooldown > 0) {
                return false;
            }
            barterCooldown = 150 + ThreadLocalRandom.current().nextInt(100);
            Location pos = null;
            if (lastAltar != null) {
                if (altars.isAltar(lastAltar.getBlock())) {
                    barterCooldown = 10;
                    AbyssalAltar.Altar altar = altars.at(lastAltar.getBlock()).orElse(null);
                    if (altar != null && altars.isBarterItem(altar.item())) {
                        pos = lastAltar;
                    }
                } else {
                    lastAltar = null;
                }
            }
            if (pos == null) {
                AbyssalAltar.Altar best = null;
                double bestDistance = Double.MAX_VALUE;
                for (AbyssalAltar.Altar altar : altars.near(entity.getLocation(), 64)) {
                    Block block = altar.block();
                    if (block == null || !hasPearls(context, block.getLocation(), true)) {
                        continue;
                    }
                    double distance = block.getLocation().distanceSquared(entity.getLocation());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = altar;
                    }
                }
                if (best != null) {
                    pos = best.block().getLocation();
                }
            }
            if (pos != null) {
                altarPos = pos;
                lastAltar = pos;
                return true;
            }
            return false;
        }

        private void tickBarter(LivingEntity entity, ModMobContext context, Mob mob) {
            Location center = altarPos.clone().add(0.5, 0.5, 0.5);
            Location bottom = altarPos.clone().add(0.5, 0, 0.5);
            double horizontal = Math.hypot(bottom.getX() - entity.getX(), bottom.getZ() - entity.getZ());
            if (horizontal < 8.0) {
                lookAt(entity, center);
            }
            if (horizontal > 3.0) {
                goTo(entity, bottom, 1.0);
                return;
            }
            lastAltar = altarPos;
            tradingLocked = Math.max(tradingLocked, 50);
            stopMoving(mob);
            AbyssalAltar altars = AbyssalAltar.get(context.plugin()).orElse(null);
            AbyssalAltar.Altar altar = altars == null ? null : altars.at(altarPos.getBlock()).orElse(null);
            if (altar != null && altars.isBarterItem(altar.item()) && altar.queueItemDrop(altar.item())) {
                altar.onEntityInteract(entity, true);
                altar.setItem(null);
            }
        }

        // --- react to player goal ---

        DeepOneReaction reactionTo(ModMobContext context, Player player) {
            return DeepOneReputation.of(context.plugin()).reactionOf(player.getUniqueId());
        }

        private boolean canUseReact(LivingEntity entity, ModMobContext context, Mob mob) {
            long worldTime = entity.getWorld().getFullTime() % 20L;
            if (!((worldTime < 2 || ThreadLocalRandom.current().nextInt(15) == 0) && noTarget(mob) && cornering == null)) {
                return false;
            }
            DeepOneReputation store = DeepOneReputation.of(context.plugin());
            Player closest = null;
            int highest = Integer.MIN_VALUE;
            for (Player scanning : entity.getWorld().getNearbyPlayers(entity.getLocation(), 80)) {
                if (scanning.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if ((closest == null || scanning.getLocation().distanceSquared(entity.getLocation())
                    < closest.getLocation().distanceSquared(entity.getLocation()) || store.get(scanning.getUniqueId()) > highest)
                    && entity.hasLineOfSight(scanning)) {
                    closest = scanning;
                    highest = store.get(scanning.getUniqueId());
                }
            }
            reactPlayer = closest;
            if (closest == null) {
                return false;
            }
            DeepOneReaction reaction1 = DeepOneReaction.fromReputation(highest);
            return (reaction1 != DeepOneReaction.AGGRESSIVE || closest.getGameMode() != GameMode.CREATIVE)
                && reaction1.validPlayer(entity.getY(), entity.isInWater(), closest.getY(), closest.isInWater());
        }

        private boolean canContinueReact(LivingEntity entity, Mob mob) {
            if (reactPlayer == null || !reactPlayer.isValid() || reactPlayer.getGameMode() == GameMode.SPECTATOR || reaction == null
                || cornering != null || !noTarget(mob) || tradingLocked > 0) {
                return false;
            }
            double distance = DeepOnes.distance(entity.getLocation(), reactPlayer.getLocation());
            return distance >= reaction.minDistance() && distance <= reaction.maxDistance()
                && (reaction != DeepOneReaction.AGGRESSIVE || reactPlayer.getGameMode() != GameMode.CREATIVE)
                && reaction.validPlayer(entity.getY(), entity.isInWater(), reactPlayer.getY(), reactPlayer.isInWater());
        }

        private void refreshReaction(LivingEntity entity, Mob mob) {
            if (reactPlayer != null) {
                DeepOneReaction previous = reaction;
                reaction = reactionTo(context, reactPlayer);
                if (previous != reaction) {
                    stopMoving(mob);
                }
                refreshReaction = 20 + ThreadLocalRandom.current().nextInt(40);
            }
        }

        private void tickReact(LivingEntity entity, ModMobContext context, Mob mob, int ticks) {
            executionTime += ticks;
            if ((refreshReaction -= ticks) < 0) {
                refreshReaction(entity, mob);
            }
            switch (reaction) {
                case STALKING -> tickStalking(entity, mob, ticks);
                case AGGRESSIVE -> {
                    if (reactPlayer.getGameMode() != GameMode.CREATIVE) {
                        mob.setTarget(reactPlayer);
                    }
                }
                case NEUTRAL -> tickFollow(entity, mob, 0.1F, ticks);
                case HELPFUL -> {
                    copyTarget(entity, context, mob, reactPlayer);
                    tickFollow(entity, mob, 0.4F, ticks);
                }
            }
            angry = reaction == DeepOneReaction.AGGRESSIVE;
            if (destination == null && (moveTarget == null || moveTarget.getY() < entity.getY() + 2.0)) {
                swimming = !entity.isOnGround() && entity.isInWater();
            }
        }

        /** Mod {@code copyTarget}: a helped player's attacker becomes the Deep One's target. */
        void copyTarget(LivingEntity entity, ModMobContext context, Mob mob, Player player) {
            if (!noTarget(mob)) {
                return;
            }
            LivingEntity target = null;
            if (player.getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof LivingEntity attacker) {
                target = attacker;
            }
            if (target != null && target.isValid() && !friendly(context, entity, target) && !target.equals(player)) {
                mob.setTarget(target);
            }
        }

        private void tickFollow(LivingEntity entity, Mob mob, float propensity, int ticks) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            float f = 0.1F;
            if (context.mobs().itemId(reactPlayer.getInventory().getItemInMainHand()).filter("ac_pearl"::equals).isPresent()
                || reactPlayer.getInventory().getItemInMainHand().getType() == Material.HEART_OF_THE_SEA
                || reactPlayer.getInventory().getItemInOffHand().getType() == Material.HEART_OF_THE_SEA
                || context.mobs().itemId(reactPlayer.getInventory().getItemInOffHand()).filter("ac_pearl"::equals).isPresent()) {
                f = 0.2F;
            }
            double distance = DeepOnes.distance(entity.getLocation(), reactPlayer.getLocation());
            if (random.nextFloat() < propensity * f * ticks && friendlyLook <= 0) {
                friendlyLook = 10 + random.nextInt(20);
            }
            if (friendlyLook > 0) {
                lookAt(entity, reactPlayer.getEyeLocation());
                friendlyLook -= ticks;
            }
            if (following) {
                if (distance < 4.0) {
                    following = false;
                    stopMoving(mob);
                } else {
                    goTo(entity, reactPlayer.getLocation(), 1.0);
                }
            } else if (distance > 10.0 && random.nextFloat() < propensity * 0.2F * ticks) {
                following = true;
            }
        }

        private void tickStalking(LivingEntity entity, Mob mob, int ticks) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Location playerAt = reactPlayer.getLocation();
            double distance = DeepOnes.distance(entity.getLocation(), playerAt);
            double distanceXZ = Math.hypot(entity.getX() - playerAt.getX(), entity.getZ() - playerAt.getZ());
            if (distance <= 8.0 && beingLookedAt) {
                chaseTime += ticks;
            }
            if (distance > 40.0 && chaseTime > 0) {
                chaseTime = 0;
            }
            // Vanilla getLastHurtByMob forgets the attacker 100 ticks after the hit.
            boolean hurtByPlayer = reactPlayer.getUniqueId().equals(lastHurtBy) && age - lastHurtAge <= 100;
            if (chaseTime >= (hurtByPlayer ? 10 : 60)) {
                cornering = reactPlayer.getUniqueId();
                return;
            }
            if ((lookAtTime -= ticks) < 0) {
                boolean looking = lookingAt(reactPlayer, entity, 1.2);
                if (looking != beingLookedAt) {
                    stopMoving(mob);
                    moveTarget = null;
                }
                beingLookedAt = looking;
                lookAtTime = 5 + random.nextInt(5);
            }
            if (!beingLookedAt && random.nextInt(100) != 0) {
                for (int i = 0; (moveTarget == null || moveTarget.distance(entity.getLocation().toVector()) < 3.0) && i < 10; i++) {
                    Vector towards = randomPos(entity, 15, 15, playerAt, Math.PI / 2, false);
                    if (towards != null) {
                        double up = this instanceof Mage ? 1 + random.nextInt(2) : 0.0;
                        moveTarget = new Vector(towards.getX(), distanceXZ < 20.0 ? playerAt.getY() + up : towards.getY(), towards.getZ());
                    }
                }
                lookAt(entity, reactPlayer.getEyeLocation());
                if (distance < 12.0) {
                    stopMoving(mob);
                    if (entity.isOnGround()) {
                        swimming = false;
                    }
                }
            } else {
                for (int i = 0; (moveTarget == null || moveTarget.distance(entity.getLocation().toVector()) < 3.0) && i < 10; i++) {
                    Vector away = randomPos(entity, 40, 15, playerAt, Math.PI / 2, true);
                    if (away != null) {
                        moveTarget = away;
                    }
                }
            }
            if (moveTarget != null && moveTarget.distance(entity.getLocation().toVector()) > 3.0 && (beingLookedAt || distance >= 12.0)) {
                if (moveTarget.getY() > entity.getY() + 1.0) {
                    swimming = entity.isInWater();
                }
                double up = this instanceof Mage ? 2.0 : 0.0;
                goTo(entity, new Location(entity.getWorld(), moveTarget.getX(), moveTarget.getY() + up, moveTarget.getZ()),
                    beingLookedAt ? 2.0 : 1.0);
            }
        }

        /** Mod {@code isEntityLookingAt}. */
        private static boolean lookingAt(LivingEntity looker, LivingEntity seen, double degree) {
            degree *= 1.0 + DeepOnes.distance(looker.getLocation(), seen.getLocation()) * 0.1;
            Vector look = looker.getLocation().getDirection().normalize();
            Vector toward = new Vector(seen.getX() - looker.getX(), seen.getBoundingBox().getMinY() + seen.getEyeHeight()
                - (looker.getY() + looker.getEyeHeight()), seen.getZ() - looker.getZ());
            double length = toward.length();
            if (length < 1.0E-6) {
                return true;
            }
            return look.dot(toward.normalize()) > 1.0 - degree / length && looker.hasLineOfSight(seen);
        }

        /**
         * Mod {@code DefaultRandomPos.getPosTowards/getPosAway}: a spot in the water (or on the floor) up to {@code range}
         * away within {@code angle} of the direction toward (or away from) a point.
         */
        private Vector randomPos(LivingEntity entity, int range, int vertical, Location point, double angle, boolean away) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double base = Math.atan2(point.getZ() - entity.getZ(), point.getX() - entity.getX()) + (away ? Math.PI : 0.0);
            for (int i = 0; i < 10; i++) {
                double theta = base + (random.nextDouble() * 2 - 1) * angle;
                double distance = Math.sqrt(random.nextDouble()) * range;
                Location spot = entity.getLocation().add(Math.cos(theta) * distance, random.nextInt(-vertical, vertical + 1),
                    Math.sin(theta) * distance);
                Block block = spot.getBlock();
                boolean ok = entity.isInWater() ? block.getType() == Material.WATER
                    : block.isPassable() && !block.getRelative(0, -1, 0).isPassable();
                if (ok && spot.getY() > entity.getWorld().getMinHeight() + 1) {
                    return spot.toVector();
                }
            }
            return null;
        }

        // --- disappear goal ---

        private Player cornered() {
            Player player = cornering == null ? null : Bukkit.getPlayer(cornering);
            if (player != null && player.getWorld() != currentWorld) {
                cornering = null;
                return null;
            }
            return player;
        }

        private void tickDisappear(LivingEntity entity, ModMobContext context, int ticks) {
            Player player = cornered();
            if (player == null) {
                return;
            }
            lookAt(entity, player.getEyeLocation());
            bombCooldown -= ticks;
            if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)) {
                if ((disappearIn -= ticks) < 0) {
                    entity.remove();
                }
            } else if (bombCooldown < 0 && !swimming && disappear(entity, context, player)) {
                bombCooldown = 10;
                disappearIn = 0;
            }
        }

        /** Mod {@code DeepOneBaseEntity.startDisappearBehavior}: throws the ink bomb at the player (-1 reputation). */
        boolean throwInkBomb(LivingEntity entity, Player player) {
            Vector velocity = player.getVelocity();
            Vector toward = new Vector(player.getX() + velocity.getX() - entity.getX(),
                player.getEyeLocation().getY() + velocity.getY() - entity.getEyeLocation().getY(),
                player.getZ() + velocity.getZ() - entity.getZ());
            double distance = toward.length();
            DeepOneItems.throwInkBomb(context, entity, entity.getEyeLocation(), toward, 0.5 + 0.2 * distance, 12.0, false);
            entity.getWorld().playSound(entity.getLocation(), SQUIRT, 1.0F, 0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F);
            addReputation(context, player.getUniqueId(), -1);
            return true;
        }

        // --- retaliation (mod HurtByHostileTargetGoal) ---

        @Override
        public void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
            if (!(event instanceof EntityDamageByEntityEvent byEntity) || !(entity instanceof Mob mob)) {
                return;
            }
            Entity damager = byEntity.getDamager();
            if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            if (!(damager instanceof LivingEntity attacker) || friendly(context, entity, attacker)) {
                return;
            }
            if (attacker instanceof Player player) {
                lastHurtBy = player.getUniqueId();
                lastHurtAge = age;
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }
            }
            mob.setTarget(attacker);
            for (Entity near : entity.getNearbyEntities(32, 10, 32)) {
                if (near instanceof Mob other && other != entity && other.getTarget() == null
                    && context.mobs().typeOf(other).filter(ALL::contains).isPresent() && !friendly(context, other, attacker)) {
                    other.setTarget(attacker);
                }
            }
        }

        // --- movement ---

        void goTo(LivingEntity entity, Location target, double speed) {
            if (!(entity instanceof Mob mob)) {
                return;
            }
            boolean changed = destination == null || destination.getWorld() != target.getWorld()
                || destination.distanceSquared(target) > 4.0;
            destination = target.clone();
            destinationSpeed = speed;
            if (changed || age - lastPath >= 20 || !mob.getPathfinder().hasPath()) {
                lastPath = age;
                mob.getPathfinder().moveTo(target, speed);
            }
        }

        void stopMoving(Mob mob) {
            destination = null;
            mob.getPathfinder().stopPathfinding();
        }

        /**
         * Mod {@code VerticalSwimmingMoveControl}: in water it swims straight along its path in any direction (up to
         * about 3 blocks a second at speed 1); on land the base mob's walking does the job. It sinks when it is not
         * swimming.
         */
        void drive(LivingEntity entity, Mob mob, int ticks) {
            boolean inWater = entity.isInWater();
            entity.setGravity(!flying(entity));
            if (flying(entity)) {
                flyTo(entity, mob, ticks);
                return;
            }
            if (!inWater) {
                return;
            }
            Vector velocity = entity.getVelocity();
            if (destination != null) {
                Location next = destination;
                var path = mob.getPathfinder().getCurrentPath();
                if (path != null && path.getNextPoint() != null) {
                    next = path.getNextPoint().clone().add(0.5, 0.0, 0.5);
                }
                Vector toward = next.toVector().subtract(entity.getLocation().toVector());
                double length = toward.length();
                if (length < 0.6 && next == destination || !mob.getPathfinder().hasPath() && length < 1.5) {
                    destination = null;
                } else if (length > 1.0E-6) {
                    // Mod VerticalSwimmingMoveControl with water drag 0.8: horizontally 5 * (0.2 * speed)^2 a tick (the
                    // speed enters twice: as the push and as the input), vertically 0.4 * speed * (rise / distance).
                    double horizontal = Math.hypot(toward.getX(), toward.getZ());
                    double along = horizontal < 0.3 ? 0.0 : 0.2 * destinationSpeed * destinationSpeed
                        * (horizontal < entity.getWidth() + 1.4 ? 0.7 : 1.0);
                    double rise = 0.4 * destinationSpeed * toward.getY() / length;
                    // Two ticks pass between calls and the drag takes 0.2 off the second.
                    entity.setVelocity(new Vector(horizontal < 1.0E-6 ? 0 : toward.getX() / horizontal * along / 0.9, rise / 0.9,
                        horizontal < 1.0E-6 ? 0 : toward.getZ() / horizontal * along / 0.9));
                    return;
                }
            }
            if (!swimming) {
                // Mod: it sinks 0.05 a tick and slows down when it is not swimming.
                velocity.multiply(0.8).setY(velocity.getY() - 0.05 * ticks);
                entity.setVelocity(velocity);
            }
        }

        void flyTo(LivingEntity entity, Mob mob, int ticks) {
        }

        void setAnimation(LivingEntity entity, String name, int duration) {
            animation = name;
            animationTick = 0;
            animationDuration = duration;
            if (context != null) {
                // The mage's own attack animation is called attack_anim in the model (ANIMATION_ATTACK).
                anim.action(entity, context, "attack".equals(name) ? "attack_anim" : name);
            }
        }

        /** Mod {@code DeepOneWanderGoal.findSwimToPos}: a water spot within 18 of a height 40 under the surface or higher. */
        private Location findSwimToPos(LivingEntity entity) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            var world = entity.getWorld();
            Location around = entity.getLocation();
            int surface = around.getBlockY();
            while (surface < world.getMaxHeight() && world.getBlockAt(around.getBlockX(), surface, around.getBlockZ()).getType() == Material.WATER) {
                surface += 5;
            }
            int baseY = Math.max(surface - 40, around.getBlockY());
            int range = 18;
            for (int i = 0; i < 15; i++) {
                Block block = world.getBlockAt(around.getBlockX() + random.nextInt(range) - range / 2,
                    baseY + random.nextInt(range) - range / 2, around.getBlockZ() + random.nextInt(range) - range / 2);
                if (block.getType() == Material.WATER && block.getY() > world.getMinHeight() + 1
                    && world.rayTraceBlocks(entity.getEyeLocation(), block.getLocation().add(0.5, 0.5, 0.5).toVector()
                        .subtract(entity.getEyeLocation().toVector()).normalize(), Math.max(0.1,
                        block.getLocation().add(0.5, 0.5, 0.5).distance(entity.getEyeLocation())), FluidCollisionMode.NEVER, true) == null) {
                    Block target = block;
                    if (groundTarget) {
                        while (target.getRelative(0, -1, 0).getType() == Material.WATER && target.getY() > world.getMinHeight()) {
                            target = target.getRelative(0, -1, 0);
                        }
                        if (this instanceof Mage) {
                            target = target.getRelative(0, 1 + random.nextInt(2), 0);
                        }
                    }
                    return target.getLocation().add(0.5, 0.5, 0.5);
                }
            }
            return around;
        }

        // --- helpers ---

        static void lookAt(LivingEntity entity, Location point) {
            if (entity instanceof Mob mob) {
                mob.lookAt(point.getX(), point.getY(), point.getZ());
            }
        }

        private void lookAtNearestPlayer(LivingEntity entity, Mob mob) {
            if (age % 10 != 0) {
                return;
            }
            for (Player player : entity.getWorld().getNearbyPlayers(entity.getLocation(), 16)) {
                if (player.getGameMode() != GameMode.SPECTATOR && !player.isInvisible()) {
                    lookAt(entity, player.getEyeLocation());
                    return;
                }
            }
        }

        void swapItemsForAnimation(LivingEntity entity, ItemStack item) {
            var equipment = entity.getEquipment();
            if (equipment == null) {
                return;
            }
            if (!equipment.getItemInMainHand().isEmpty()) {
                swapped = equipment.getItemInMainHand().clone();
            }
            equipment.setItemInMainHand(item);
        }

        void restoreSwapped(LivingEntity entity) {
            if (entity.getEquipment() != null) {
                entity.getEquipment().setItemInMainHand(swapped);
            }
        }

        /** Mod {@code checkAndDealMeleeDamage}. */
        boolean meleeDamage(LivingEntity entity, LivingEntity target, float multiplier, float knockback) {
            if (!entity.hasLineOfSight(target)
                || DeepOnes.distance(entity.getLocation(), target.getLocation()) >= entity.getWidth() + target.getWidth() + 5.0) {
                return false;
            }
            AttributeInstance attack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            float damage = (float) (attack == null ? 1.0 : attack.getValue()) * multiplier;
            target.damage(damage, entity);
            target.knockback(knockback * multiplier, entity.getX() - target.getX(), entity.getZ() - target.getZ());
            return true;
        }

        @Override
        public void close(LivingEntity entity, ModMobContext context) {
            if (entity != null) {
                entity.setGravity(true);
            }
        }
    }

    // --- the three kinds -----------------------------------------------------------------------------------

    /** Mod {@code DeepOneEntity}: bites and scratches. */
    static final class Plain extends Base {
        @Override
        ModMobType type() {
            return DEEP_ONE;
        }

        @Override
        String tradeAnimation() {
            return "trade";
        }

        @Override
        int tradeDuration() {
            return TRADE_DURATION;
        }

        @Override
        List<DeepOneBarter.Entry> barterTable() {
            return DeepOneBarter.DEEP_ONE;
        }

        @Override
        void attack(LivingEntity entity, ModMobContext context, LivingEntity target) {
            double distance = DeepOnes.distance(entity.getLocation(), target.getLocation());
            float f = (float) (entity.getWidth() + target.getWidth());
            if (distance < f + 1.0 && animation == null) {
                boolean scratch = ThreadLocalRandom.current().nextBoolean();
                setAnimation(entity, scratch ? "scratch" : "bite", scratch ? 22 : 8);
                context.mobs().playSound(entity, type(), "attack");
            }
            if (distance > f + 4.0 || destination == null && distance > f + 1.0) {
                goTo(entity, target.getLocation(), 1.3);
            }
            meleeWindows(entity, target);
        }

        void meleeWindows(LivingEntity entity, LivingEntity target) {
            if ("scratch".equals(animation) && (animationTick > 5 && animationTick < 9 || animationTick > 12 && animationTick < 16)) {
                meleeDamage(entity, target, 1.0F, 0.25F);
            }
            if ("bite".equals(animation) && animationTick > 3 && animationTick <= 7) {
                meleeDamage(entity, target, 1.0F, 0.25F);
            }
        }

        @Override
        boolean disappear(LivingEntity entity, ModMobContext context, Player player) {
            lookAt(entity, player.getEyeLocation());
            var equipment = entity.getEquipment();
            if (equipment != null && equipment.getItemInMainHand().isEmpty()) {
                context.mobs().createItem("ac_ink_bomb", 1).ifPresent(equipment::setItemInMainHand);
            }
            if (animation == null) {
                setAnimation(entity, "throw", 20);
            } else if ("throw".equals(animation) && animationTick > 10) {
                if (equipment != null && context.mobs().itemId(equipment.getItemInMainHand()).filter("ac_ink_bomb"::equals).isPresent()) {
                    equipment.setItemInMainHand(ItemStack.empty());
                }
                return throwInkBomb(entity, player);
            }
            return false;
        }
    }

    /** Mod {@code DeepOneKnightEntity}: melee with a trident or an ortholance, or throws the trident and goes to fetch it. */
    static final class Knight extends Base {
        private static final NamespacedKey TRIDENT_KEY = new NamespacedKey("bigcasares", "ac_deep_one_trident");
        private UUID lastThrownTrident;
        private boolean melee = ThreadLocalRandom.current().nextBoolean();
        boolean pendingOrtholance;

        Knight(LivingEntity entity) {
            // Mod LastTridentUUID: the trident it threw survives unloading the chunk.
            String saved = entity.getPersistentDataContainer().get(TRIDENT_KEY, org.bukkit.persistence.PersistentDataType.STRING);
            if (saved != null) {
                try {
                    lastThrownTrident = UUID.fromString(saved);
                } catch (IllegalArgumentException invalid) {
                    lastThrownTrident = null;
                }
            }
        }

        private void rememberTrident(LivingEntity entity, UUID trident) {
            lastThrownTrident = trident;
            if (trident == null) {
                entity.getPersistentDataContainer().remove(TRIDENT_KEY);
            } else {
                entity.getPersistentDataContainer().set(TRIDENT_KEY, org.bukkit.persistence.PersistentDataType.STRING, trident.toString());
            }
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            if (pendingOrtholance) {
                pendingOrtholance = false;
                context.mobs().createItem("ac_ortholance", 1).ifPresent(item -> entity.getEquipment().setItemInMainHand(item));
            }
            super.tick(entity, context, ticks);
            if (entity instanceof Mob mob) {
                LivingEntity target = mob.getTarget();
                if ((target == null || !target.isValid()) && lastThrownTrident != null) {
                    pickUpTrident(entity, mob);
                }
            }
        }

        @Override
        ModMobType type() {
            return KNIGHT;
        }

        @Override
        String tradeAnimation() {
            return "trade";
        }

        @Override
        int tradeDuration() {
            return TRADE_DURATION;
        }

        @Override
        List<DeepOneBarter.Entry> barterTable() {
            return DeepOneBarter.KNIGHT;
        }

        private boolean holdsTrident(LivingEntity entity) {
            return entity.getEquipment() != null && entity.getEquipment().getItemInMainHand().getType() == Material.TRIDENT;
        }

        private boolean pickUpTrident(LivingEntity entity, Mob mob) {
            if (lastThrownTrident == null) {
                return false;
            }
            Entity found = entity.getServer().getEntity(lastThrownTrident);
            if (found instanceof Trident trident && trident.isValid()) {
                if (DeepOnes.distance(entity.getLocation(), trident.getLocation()) < 2.0) {
                    if (animation == null) {
                        setAnimation(entity, "scratch", 22);
                    }
                    entity.getEquipment().setItemInMainHand(new ItemStack(Material.TRIDENT));
                    trident.remove();
                    stopMoving(mob);
                    rememberTrident(entity, null);
                } else {
                    goTo(entity, trident.getLocation(), 1.5);
                }
                return true;
            }
            rememberTrident(entity, null);
            return false;
        }

        @Override
        void attack(LivingEntity entity, ModMobContext context, LivingEntity target) {
            if (!(entity instanceof Mob mob)) {
                return;
            }
            double distance = DeepOnes.distance(entity.getLocation(), target.getLocation());
            float f = (float) (entity.getWidth() + target.getWidth());
            boolean meleeOnly = context.mobs().itemId(entity.getEquipment().getItemInMainHand()).filter("ac_ortholance"::equals).isPresent();
            if ((distance > 15.0 || !melee) && !meleeOnly) {
                melee = false;
                if (!pickUpTrident(entity, mob) && holdsTrident(entity)) {
                    if (entity.hasLineOfSight(target) && distance < 35.0) {
                        stopMoving(mob);
                        if (animation == null) {
                            setAnimation(entity, "throw", 20);
                        } else if ("throw".equals(animation) && animationTick > 8) {
                            melee = true;
                            throwTrident(entity, target);
                            entity.getEquipment().setItemInMainHand(ItemStack.empty());
                        }
                    } else {
                        goTo(entity, target.getLocation(), 1.2);
                    }
                }
            }
            if (melee || meleeOnly) {
                if (distance < f + 1.0F) {
                    if (animation == null) {
                        boolean scratch = ThreadLocalRandom.current().nextBoolean();
                        setAnimation(entity, scratch ? "scratch" : "bite", scratch ? 22 : 8);
                        context.mobs().playSound(entity, type(), "attack");
                    }
                } else {
                    goTo(entity, target.getLocation(), 1.2);
                }
            }
            boolean hit = false;
            if ("scratch".equals(animation) && (animationTick > 5 && animationTick < 9 || animationTick > 12 && animationTick < 16)
                || "bite".equals(animation) && animationTick > 3 && animationTick <= 7) {
                hit = meleeDamage(entity, target, 1.0F, 0.25F);
            }
            if (hit) {
                melee = ThreadLocalRandom.current().nextBoolean();
            }
        }

        private void throwTrident(LivingEntity entity, LivingEntity target) {
            Location from = entity.getEyeLocation().add(0, -0.1, 0);
            double dx = target.getX() - entity.getX();
            double dy = target.getY() + target.getHeight() * 0.3333333 - from.getY();
            double dz = target.getZ() - entity.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            Vector direction = new Vector(dx, dy + flat * 0.2F, dz).normalize();
            double spread = (14 - entity.getWorld().getDifficulty().getValue() * 4) * 0.0075;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Vector velocity = direction.multiply(1.6).add(new Vector(random.nextGaussian() * spread,
                random.nextGaussian() * spread, random.nextGaussian() * spread));
            Trident trident = entity.getWorld().spawn(from, Trident.class, spawned -> {
                spawned.setShooter(entity);
                spawned.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                spawned.setItemStack(new ItemStack(Material.TRIDENT));
                spawned.setVelocity(velocity);
            });
            entity.getWorld().playSound(entity.getLocation(), "minecraft:item.trident.throw", 1.0F,
                1.0F / (random.nextFloat() * 0.4F + 0.8F));
            rememberTrident(entity, trident.getUniqueId());
        }

        @Override
        boolean disappear(LivingEntity entity, ModMobContext context, Player player) {
            lookAt(entity, player.getEyeLocation());
            if (!entity.getEquipment().getItemInMainHand().isEmpty()) {
                context.mobs().createItem("ac_ink_bomb", 1).ifPresent(bomb -> swapItemsForAnimation(entity, bomb));
            }
            if (animation == null) {
                setAnimation(entity, "throw", 20);
            } else if ("throw".equals(animation) && animationTick > 10) {
                if (context.mobs().itemId(entity.getEquipment().getItemInMainHand()).filter("ac_ink_bomb"::equals).isPresent()) {
                    restoreSwapped(entity);
                }
                return throwInkBomb(entity, player);
            }
            return false;
        }
    }

    /** Mod {@code DeepOneMageEntity}: waves and water bolts from a distance, a spin when something gets close. */
    static final class Mage extends Base {
        private int spinCooldown;
        private int rangedCooldown;
        private Vector strafeTarget;
        private int attackCharge;

        @Override
        ModMobType type() {
            return MAGE;
        }

        @Override
        String tradeAnimation() {
            return "trade";
        }

        @Override
        int tradeDuration() {
            return MAGE_TRADE_DURATION;
        }

        @Override
        List<DeepOneBarter.Entry> barterTable() {
            return DeepOneBarter.MAGE;
        }

        /** Mod {@code isNoGravity}: out of the water the mage floats. */
        @Override
        boolean flying(LivingEntity entity) {
            return !entity.isInWater();
        }

        @Override
        void flyTo(LivingEntity entity, Mob mob, int ticks) {
            // Mod FlightMoveController: it bobs a little and is pushed 0.025 a tick (times the speed) toward its goal.
            Vector velocity = entity.getVelocity();
            velocity.setY(velocity.getY() + Math.sin(age * 0.1) * 0.005 * ticks);
            if (destination != null && destination.getWorld() == entity.getWorld()) {
                Vector toward = destination.toVector().subtract(entity.getLocation().toVector());
                double length = toward.length();
                if (length < entity.getWidth() * 0.3) {
                    destination = null;
                } else {
                    velocity.add(toward.multiply(destinationSpeed * 0.025 * ticks / length));
                }
            }
            velocity.multiply(0.91);
            entity.setVelocity(velocity);
        }

        @Override
        public void tick(LivingEntity entity, ModMobContext context, int ticks) {
            super.tick(entity, context, ticks);
            if (!(entity instanceof Mob mob)) {
                return;
            }
            // Mod tick: out of the water the mage keeps a Bubbled effect (200 ticks); back in the water it loses it.
            boolean bubbled = context.effects().has(entity, dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType.BUBBLED);
            if (!entity.isInWater() && !bubbled) {
                context.effects().apply(entity, dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType.BUBBLED, 0, 200);
            } else if (entity.isInWater() && bubbled) {
                context.effects().remove(entity, dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType.BUBBLED);
            }
            if ("spin".equals(animation)) {
                Vector velocity = entity.getVelocity().multiply(0.6);
                LivingEntity target = mob.getTarget();
                if (target != null) {
                    velocity.add(target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(0.1));
                }
                entity.setVelocity(velocity);
                if (animationTick % 6 < ticks) {
                    for (Entity near : entity.getNearbyEntities(2.45, 1.25, 2.45)) {
                        if (near instanceof LivingEntity other && !friendly(context, entity, other)) {
                            meleeDamage(entity, other, 0.4F, 1.0F);
                        }
                    }
                }
            }
            if ("attack".equals(animation)) {
                LivingEntity target = mob.getTarget();
                if (target != null && target.isValid()) {
                    if (animationTick >= 16 && attackCharge == 0) {
                        attackCharge = 1;
                        useMagicAttack(entity, context, target);
                    } else if (animationTick < 16) {
                        magicParticles(entity);
                    }
                    lookAt(entity, target.getEyeLocation());
                }
            } else {
                attackCharge = 0;
            }
            spinCooldown = Math.max(0, spinCooldown - ticks);
            rangedCooldown = Math.max(0, rangedCooldown - ticks);
        }

        private void magicParticles(LivingEntity entity) {
            Location at = entity.getLocation().add(0, entity.getHeight() * 0.5 + 0.15, 0);
            entity.getWorld().spawnParticle(Particle.WITCH, at, 2, 0.5, 0.2, 0.5, 0.05);
        }

        void useMagicAttack(LivingEntity entity, ModMobContext context, LivingEntity target) {
            magicParticles(entity);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (random.nextBoolean()) {
                int lifespan = (int) Math.floor(DeepOnes.distance(entity.getLocation(), target.getLocation())) + 10;
                double dx = target.getX() - entity.getX();
                double dz = target.getZ() - entity.getZ();
                for (int i = -2; i <= 2; i++) {
                    float yaw = (float) (-(Math.atan2(dx, dz) * 180.0 / Math.PI) + i * 10);
                    DeepOneMagic.wave(context, entity, new Location(entity.getWorld(), entity.getX(), target.getY(), entity.getZ()), yaw,
                        lifespan);
                }
            } else {
                Location from = entity.getEyeLocation();
                double dx = target.getX() - entity.getX();
                double dy = target.getY() + target.getHeight() * 0.3333333 - from.getY();
                double dz = target.getZ() - entity.getZ();
                double flat = Math.sqrt(dx * dx + dz * dz);
                DeepOneMagic.bolt(context, entity, from, new Vector(dx, dy + flat * 0.67F, dz), 0.6, 30.0, random.nextInt(2) == 0,
                    target.getUniqueId());
            }
        }

        @Override
        void attack(LivingEntity entity, ModMobContext context, LivingEntity target) {
            double distance = DeepOnes.distance(entity.getLocation(), target.getLocation());
            float f = (float) (entity.getWidth() + target.getWidth());
            if (distance > 20.0F + f) {
                goTo(entity, target.getLocation(), 1.2);
            } else if (distance < 2.0F + f && spinCooldown <= 0) {
                if (animation == null) {
                    setAnimation(entity, "spin", 70);
                    spinCooldown = 1000 + ThreadLocalRandom.current().nextInt(60);
                }
            } else {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                if (strafeTarget != null && strafeTarget.distance(entity.getLocation().toVector()) >= 4.0) {
                    goTo(entity, strafeTarget.toLocation(entity.getWorld()), 1.5);
                } else {
                    Vector possible = target.getLocation().toVector().add(new Vector(random.nextInt(20) - 10, random.nextInt(2),
                        random.nextInt(20) - 10));
                    if (!blocked(entity, possible)) {
                        strafeTarget = possible;
                    }
                }
                if (rangedCooldown <= 0 && animation == null && entity.hasLineOfSight(target)) {
                    setAnimation(entity, "attack", 25);
                    context.mobs().playSound(entity, type(), "attack");
                    rangedCooldown = 30 + random.nextInt(20);
                }
                lookAt(entity, target.getEyeLocation());
            }
        }

        private boolean blocked(LivingEntity entity, Vector point) {
            Location eye = entity.getEyeLocation();
            Vector direction = point.clone().subtract(eye.toVector());
            double length = direction.length();
            if (length < 1.0E-6) {
                return false;
            }
            RayTraceResult hit = entity.getWorld().rayTraceBlocks(eye, direction.normalize(), length, FluidCollisionMode.NEVER, true);
            return hit != null;
        }

        @Override
        boolean disappear(LivingEntity entity, ModMobContext context, Player player) {
            lookAt(entity, player.getEyeLocation());
            if (entity instanceof Mob mob) {
                stopMoving(mob);
            }
            if (animation == null) {
                setAnimation(entity, "disappear", 55);
            }
            if ("disappear".equals(animation)) {
                if (animationTick > 50) {
                    Location at = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
                    at.getWorld().spawnParticle(Particle.WITCH, at, 16, 0.5, 0.5, 0.5, 0.2);
                    at.getWorld().spawnParticle(Particle.BUBBLE_POP, at, 16, 0.5, 0.5, 0.5, 0.2);
                    entity.remove();
                    return true;
                }
                entity.getWorld().spawnParticle(Particle.WITCH, entity.getLocation().add(0, entity.getHeight(), 0), 3, 0.5, 0.2, 0.5, 0.0);
            }
            return false;
        }
    }
}
