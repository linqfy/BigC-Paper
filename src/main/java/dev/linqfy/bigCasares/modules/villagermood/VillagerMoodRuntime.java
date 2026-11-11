package dev.linqfy.bigCasares.modules.villagermood;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * Infinite villager trades with consequences. Villagers boxed into 1x1 cells slowly get depressed and charge more,
 * squeeze out through 1x1 holes, may walk off cliffs, riot against nearby players, and insult players who spam
 * trades. Mood lives in each villager's persistent data, so it survives restarts.
 */
final class VillagerMoodRuntime {

    /** Riots and cliff walks are driven at this cadence. */
    static final long ACTION_INTERVAL_TICKS = 10L;
    private static final long PUNCH_COOLDOWN_TICKS = 30L;
    private static final double PUNCH_REACH = 2.2;
    private static final double ANNOUNCE_RADIUS = 16.0;

    private final VillagerMoodSettings settings;
    private final VillagerMoodPolicy policy;
    private final String namespace;
    private final NamespacedKey confinedKey;
    private final NamespacedKey annoyanceKey;
    private final NamespacedKey golemKey;
    private final DoubleSupplier random;
    private final LongSupplier clock;
    private final Consumer<Runnable> nextTick;
    private final TradeSpamTracker spamTracker;
    private final Map<UUID, Riot> riots = new HashMap<>();
    private final Map<UUID, Long> riotCooldownUntil = new HashMap<>();
    private final Map<UUID, CliffWalk> cliffWalks = new HashMap<>();

    VillagerMoodRuntime(VillagerMoodSettings settings, NamespacedKey confinedKey, NamespacedKey annoyanceKey,
                        NamespacedKey golemKey, DoubleSupplier random, LongSupplier clock,
                        Consumer<Runnable> nextTick) {
        this.settings = settings;
        this.policy = new VillagerMoodPolicy(settings);
        this.namespace = confinedKey.getNamespace();
        this.confinedKey = confinedKey;
        this.annoyanceKey = annoyanceKey;
        this.golemKey = golemKey;
        this.random = random;
        this.clock = clock;
        this.nextTick = nextTick;
        this.spamTracker = new TradeSpamTracker(settings.spam().windowTicks(), settings.spam().maxTradesInWindow());
    }

    // --- trading ---------------------------------------------------------------------------------

    boolean infiniteTrading() {
        return settings.trading().infinite();
    }

    /**
     * Runs when a trade window opens: restocks every offer and adds the mood surcharge. Vanilla resets the
     * special price when trading stops, so the surcharge never stacks between openings.
     */
    void onTradeOpen(Player player, MerchantInventory inventory) {
        if (!(inventory.getMerchant() instanceof AbstractVillager merchant) || !isOrdinary(merchant)) {
            return;
        }
        int depression = 0;
        int annoyance = 0;
        if (merchant instanceof Villager villager) {
            depression = policy.depressionLevel(read(villager, confinedKey));
            annoyance = policy.annoyanceLevel(read(villager, annoyanceKey));
        }
        List<MerchantRecipe> recipes = merchant.getRecipes();
        for (int index = 0; index < recipes.size(); index++) {
            MerchantRecipe recipe = recipes.get(index);
            if (settings.trading().refreshOnOpen()) {
                recipe.setUses(0);
            }
            List<ItemStack> ingredients = recipe.getIngredients();
            int baseCost = ingredients.isEmpty() || ingredients.getFirst() == null ? 0 : ingredients.getFirst().getAmount();
            int extra = policy.surcharge(baseCost, depression, annoyance);
            if (extra > 0) {
                recipe.setSpecialPrice(recipe.getSpecialPrice() + extra);
            }
            merchant.setRecipe(index, recipe);
        }
        double fraction = policy.surchargeFraction(depression, annoyance);
        if (fraction > 0.0) {
            String reason = depression > 0 && annoyance > 0 ? "deprimido y harto de vos"
                : depression > 0 ? "deprimido (depresión " + depression + "/" + settings.depression().maxLevel() + ")"
                : "harto de vos";
            player.sendActionBar(ChatColor.GOLD + "El aldeano está " + reason + ": precios +"
                + Math.round(fraction * 100) + "%");
        }
    }

    /** Runs for every completed trade with a villager or wandering trader. */
    void onTrade(Player player, AbstractVillager merchant) {
        if (!(merchant instanceof Villager villager) || !isOrdinary(villager)) {
            return;
        }
        if (spamTracker.record(player.getUniqueId(), villager.getUniqueId(), clock.getAsLong())) {
            insult(player, villager);
        }
    }

    private void insult(Player player, Villager villager) {
        write(villager, annoyanceKey, policy.annoy(read(villager, annoyanceKey)));
        player.sendMessage(speaker() + pick(settings.insults()));
        villager.getWorld().playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        villager.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, villager.getEyeLocation(), 5, 0.3, 0.3, 0.3);
        player.sendActionBar(ChatColor.RED + "Te subió los precios por pesado ("
            + policy.annoyanceLevel(read(villager, annoyanceKey)) + "/" + settings.spam().maxLevel() + ")");
        // Close the window so the new prices apply when it is reopened.
        nextTick.accept(() -> {
            InventoryView view = player.getOpenInventory();
            if (view.getTopInventory() instanceof MerchantInventory open
                && open.getMerchant() instanceof Entity entity
                && entity.getUniqueId().equals(villager.getUniqueId())) {
                player.closeInventory();
            }
        });
    }

    // --- periodic mood check ---------------------------------------------------------------------

    /** Runs every {@code check-interval-ticks}. */
    void check(Iterable<World> worlds, Iterable<? extends Player> players) {
        long now = clock.getAsLong();
        spamTracker.cleanup(now);
        riotCooldownUntil.values().removeIf(until -> until <= now);
        for (World world : worlds) {
            BlockProbe probe = new BukkitBlockProbe(world);
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (isOrdinary(villager)) {
                    updateMood(villager, probe);
                }
            }
        }
        if (settings.riot().enabled()) {
            for (Player player : players) {
                considerRiot(player, now);
            }
        }
    }

    private void updateMood(Villager villager, BlockProbe probe) {
        Location at = villager.getLocation();
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        boolean inVehicle = villager.isInsideVehicle();
        boolean confined = inVehicle || VillagerMoodPolicy.isConfined(probe, x, y, z);
        long interval = settings.checkIntervalTicks();

        long before = read(villager, confinedKey);
        long confinedTicks = policy.nextConfinedTicks(before, confined, interval);
        write(villager, confinedKey, confinedTicks);
        write(villager, annoyanceKey, policy.decayAnnoyance(read(villager, annoyanceKey), interval));

        int previousLevel = policy.depressionLevel(before);
        int level = policy.depressionLevel(confinedTicks);
        if (level > previousLevel) {
            villager.getWorld().playSound(at, Sound.ENTITY_VILLAGER_AMBIENT, 1.0f, 0.5f);
            announce(at, ChatColor.GRAY + "Un aldeano se siente encerrado... (depresión " + level + "/"
                + settings.depression().maxLevel() + ")");
        }
        if (level > 0 && random.getAsDouble() < 0.5) {
            villager.getWorld().spawnParticle(Particle.FALLING_WATER, villager.getEyeLocation(), 3, 0.15, 0.05, 0.15);
        }

        VillagerMoodSettings.Escape escape = settings.escape();
        if (confined && !inVehicle && escape.enabled() && confinedTicks >= escape.minConfinedTicks()
            && random.getAsDouble() < escape.chancePerCheck()) {
            VillagerMoodPolicy.findEscapeHole(probe, x, y, z).ifPresent(cell -> escape(villager, cell));
            return;
        }
        VillagerMoodSettings.Cliff cliff = settings.cliff();
        if (!confined && cliff.enabled() && level >= cliff.minDepression()
            && !cliffWalks.containsKey(villager.getUniqueId()) && random.getAsDouble() < cliff.chancePerCheck()) {
            VillagerMoodPolicy.findCliffEdge(probe, x, y, z, cliff.searchRadius(), cliff.minDrop())
                .ifPresent(cell -> startCliffWalk(villager, cell));
        }
    }

    private void escape(Villager villager, VillagerMoodPolicy.Cell cell) {
        Location from = villager.getLocation();
        Location to = new Location(villager.getWorld(), cell.x() + 0.5, cell.y(), cell.z() + 0.5,
            from.getYaw(), from.getPitch());
        villager.getWorld().spawnParticle(Particle.POOF, from.clone().add(0, 1, 0), 10, 0.2, 0.4, 0.2, 0.02);
        villager.teleport(to);
        villager.getWorld().playSound(to, Sound.ENTITY_VILLAGER_CELEBRATE, 1.0f, 1.2f);
        announce(to, ChatColor.YELLOW + "¡Un aldeano se escapó por un agujero de la celda!");
    }

    // --- cliff -----------------------------------------------------------------------------------

    private void startCliffWalk(Villager villager, VillagerMoodPolicy.Cell cell) {
        Location edge = new Location(villager.getWorld(), cell.x() + 0.5, cell.y(), cell.z() + 0.5);
        cliffWalks.put(villager.getUniqueId(), new CliffWalk(villager, edge, settings.cliff().attemptTicks()));
        villager.getPathfinder().moveTo(edge, 0.6);
        announce(villager.getLocation(), ChatColor.DARK_GRAY + "Un aldeano deprimido camina hacia el precipicio...");
    }

    /** Runs every {@link #ACTION_INTERVAL_TICKS}: cliff walks and riots. */
    void tickActions() {
        tickCliffWalks();
        tickRiots();
    }

    private void tickCliffWalks() {
        Iterator<CliffWalk> iterator = cliffWalks.values().iterator();
        while (iterator.hasNext()) {
            CliffWalk walk = iterator.next();
            Villager villager = walk.villager;
            walk.remainingTicks -= ACTION_INTERVAL_TICKS;
            if (!villager.isValid() || walk.remainingTicks <= 0 || villager.getWorld() != walk.edge.getWorld()) {
                iterator.remove();
                continue;
            }
            Location at = villager.getLocation();
            double dx = walk.edge.getX() - at.getX();
            double dz = walk.edge.getZ() - at.getZ();
            if (dx * dx + dz * dz <= 2.5) {
                Vector push = new Vector(dx, 0, dz);
                if (push.lengthSquared() > 0.0) {
                    push.normalize().multiply(0.45);
                }
                villager.setVelocity(push.setY(0.25));
                villager.getWorld().playSound(at, Sound.ENTITY_VILLAGER_HURT, 1.0f, 0.6f);
                iterator.remove();
                continue;
            }
            villager.getPathfinder().moveTo(walk.edge, 0.6);
        }
    }

    // --- riots -----------------------------------------------------------------------------------

    private void considerRiot(Player player, long now) {
        UUID id = player.getUniqueId();
        if (riots.containsKey(id) || riotCooldownUntil.containsKey(id) || !player.isValid()
            || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        double radius = settings.riot().radius();
        List<Villager> unhappy = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Villager villager && isOrdinary(villager)
                && policy.joinsRiot(policy.depressionLevel(read(villager, confinedKey)),
                    policy.annoyanceLevel(read(villager, annoyanceKey)))) {
                unhappy.add(villager);
            }
        }
        if (unhappy.size() >= settings.riot().minRioters() && random.getAsDouble() < settings.riot().chancePerCheck()) {
            startRiot(player, unhappy);
        }
    }

    private void startRiot(Player player, List<Villager> rioters) {
        IronGolem golem = null;
        if (settings.riot().summonGolem()) {
            Location spawn = rioters.getFirst().getLocation();
            golem = spawn.getWorld().spawn(spawn, IronGolem.class, created -> {
                created.getPersistentDataContainer().set(golemKey, PersistentDataType.BYTE, (byte) 1);
                created.setPlayerCreated(false);
                created.setPersistent(false);
            });
            golem.setTarget(player);
        }
        riots.put(player.getUniqueId(), new Riot(player, List.copyOf(rioters), golem, settings.riot().durationTicks()));
        player.sendTitle(ChatColor.RED + "¡MOTÍN!", ChatColor.GOLD + "Los aldeanos se rebelan contra vos", 5, 50, 10);
        for (Villager rioter : rioters) {
            rioter.getWorld().playSound(rioter.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.7f);
        }
        player.sendMessage(speaker() + pick(settings.riotShouts()));
    }

    private void tickRiots() {
        long now = clock.getAsLong();
        Iterator<Riot> iterator = riots.values().iterator();
        while (iterator.hasNext()) {
            Riot riot = iterator.next();
            Player player = riot.player;
            riot.remainingTicks -= ACTION_INTERVAL_TICKS;
            if (!player.isOnline() || player.isDead() || riot.remainingTicks <= 0) {
                iterator.remove();
                endRiot(riot, now);
                continue;
            }
            for (Villager rioter : riot.rioters) {
                if (!rioter.isValid() || rioter.getWorld() != player.getWorld()) {
                    continue;
                }
                rioter.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, rioter.getEyeLocation(), 1, 0.3, 0.3, 0.3);
                double distance = rioter.getLocation().distance(player.getLocation());
                Long lastPunch = riot.lastPunch.get(rioter.getUniqueId());
                if (distance <= PUNCH_REACH && (lastPunch == null || now - lastPunch >= PUNCH_COOLDOWN_TICKS)) {
                    riot.lastPunch.put(rioter.getUniqueId(), now);
                    player.damage(settings.riot().damage(), rioter);
                    Vector knockback = player.getLocation().toVector().subtract(rioter.getLocation().toVector()).setY(0);
                    if (knockback.lengthSquared() > 0.0) {
                        player.setVelocity(player.getVelocity().add(knockback.normalize().multiply(0.3).setY(0.2)));
                    }
                } else if (distance > PUNCH_REACH && distance <= settings.riot().radius() * 2) {
                    rioter.getPathfinder().moveTo(player, 0.7);
                }
                if (random.getAsDouble() < 0.04) {
                    player.sendMessage(speaker() + pick(settings.riotShouts()));
                }
            }
            if (riot.golem != null && riot.golem.isValid()) {
                riot.golem.setTarget(player);
            }
        }
    }

    private void endRiot(Riot riot, long now) {
        if (riot.golem != null && riot.golem.isValid()) {
            riot.golem.remove();
        }
        riotCooldownUntil.put(riot.player.getUniqueId(), now + settings.riot().cooldownTicks());
        if (riot.player.isOnline()) {
            riot.player.sendMessage(ChatColor.GRAY + "El motín de los aldeanos se calmó... por ahora.");
        }
    }

    boolean isRiotGolem(Entity entity) {
        return entity instanceof IronGolem && entity.getPersistentDataContainer().has(golemKey, PersistentDataType.BYTE);
    }

    // --- lifecycle -------------------------------------------------------------------------------

    void forget(UUID playerId) {
        spamTracker.forgetPlayer(playerId);
        Riot riot = riots.remove(playerId);
        if (riot != null && riot.golem != null && riot.golem.isValid()) {
            riot.golem.remove();
        }
    }

    void shutdown() {
        for (Riot riot : riots.values()) {
            if (riot.golem != null && riot.golem.isValid()) {
                riot.golem.remove();
            }
        }
        riots.clear();
        riotCooldownUntil.clear();
        cliffWalks.clear();
        spamTracker.clear();
    }

    // --- helpers ---------------------------------------------------------------------------------

    /**
     * Ordinary villagers only: plugin NPCs (shop keepers, Don Pollos, Sift singers...) are invulnerable, have no AI
     * or carry this plugin's own persistent markers, and must keep their scripted behaviour.
     */
    boolean isOrdinary(AbstractVillager merchant) {
        if (!merchant.isValid() || merchant.isInvulnerable() || !merchant.hasAI()) {
            return false;
        }
        if (merchant instanceof Villager villager && !villager.isAdult()) {
            return false;
        }
        Set<NamespacedKey> keys = merchant.getPersistentDataContainer().getKeys();
        for (NamespacedKey key : keys) {
            if (key.getNamespace().equals(namespace) && !key.equals(confinedKey) && !key.equals(annoyanceKey)) {
                return false;
            }
        }
        return true;
    }

    private static long read(Entity entity, NamespacedKey key) {
        Long value = entity.getPersistentDataContainer().get(key, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    private static void write(Entity entity, NamespacedKey key, long value) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        if (value <= 0L) {
            container.remove(key);
        } else {
            container.set(key, PersistentDataType.LONG, value);
        }
    }

    private void announce(Location at, String message) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= ANNOUNCE_RADIUS * ANNOUNCE_RADIUS) {
                player.sendMessage(message);
            }
        }
    }

    private String pick(List<String> lines) {
        int index = (int) Math.min(lines.size() - 1, Math.floor(random.getAsDouble() * lines.size()));
        return lines.get(Math.max(0, index));
    }

    private static String speaker() {
        return ChatColor.GREEN + "[Aldeano] " + ChatColor.WHITE;
    }

    private static final class CliffWalk {
        private final Villager villager;
        private final Location edge;
        private long remainingTicks;

        private CliffWalk(Villager villager, Location edge, long remainingTicks) {
            this.villager = villager;
            this.edge = edge;
            this.remainingTicks = remainingTicks;
        }
    }

    private static final class Riot {
        private final Player player;
        private final List<Villager> rioters;
        private final IronGolem golem;
        private final Map<UUID, Long> lastPunch = new HashMap<>();
        private long remainingTicks;

        private Riot(Player player, List<Villager> rioters, IronGolem golem, long remainingTicks) {
            this.player = player;
            this.rioters = rioters;
            this.golem = golem;
            this.remainingTicks = remainingTicks;
        }
    }
}
