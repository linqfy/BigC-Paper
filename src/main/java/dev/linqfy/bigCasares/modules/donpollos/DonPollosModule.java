package dev.linqfy.bigCasares.modules.donpollos;

import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Don Pollos: cinco variantes de Don Pollo (Comun, Gordito, Salsero, Aura 67 y Fino), ciudades de Don Pollo en el
 * mundo, el Don Pollo Boss con sus cuatro fases y soporte para jugadores de Bedrock. La configuracion vive en
 * {@code don-pollos.yml}; los items, texturas y sonidos van en el pack de BigCasares (resourcepack/java/assets/donpollos)
 * y los modelos en BetterModel (bettermodel/models).
 */
public final class DonPollosModule implements PluginModule {

    public static final String MODULE_ID = "don-pollos";
    static final String CONFIG_FILE = "don-pollos.yml";
    private static final double SALSERO_FOLLOW_SPEED = 0.8;

    private final JavaPlugin plugin;
    private final Map<UUID, TrackedPollo> loaded = new LinkedHashMap<>();
    private final Map<UUID, String> currentDance = new HashMap<>();
    private final Map<UUID, Villager> lastAura = new HashMap<>();

    private RuntimeRegistrationScope compatibilityScope;
    private DonPollosSettings settings;
    private DonPollosService service;
    private DonPollosSalseroPolicy salseroPolicy;
    private DonPollosEntityFactory factory;
    private DonPollosModelBinder binder;
    private DonPollosMenuController menus;
    private DonPollosItems items;
    private DonPollosRouletteMenu roulette;
    private DonPollosBattleManager battles;
    private DonPollosCityManager cities;
    private DonPollosSounds sounds;
    private DonPollosBossManager bosses;
    private DonPollosSecret secret;
    private DonPollosBedrock bedrock;
    private boolean bedrockModels;
    private NamespacedKey cityVisitKey;

    public DonPollosModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        if (plugin == null) {
            return;
        }
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.isFile()) {
            plugin.saveResource(CONFIG_FILE, false);
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
        this.settings = new DonPollosSettingsLoader().load(configuration);
        this.service = new DonPollosService();
        this.roulette = new DonPollosRouletteMenu(plugin, settings.roulette(),
            settings.variant(DonPolloVariant.FINO).menuTitle());
        this.salseroPolicy = new DonPollosSalseroPolicy(settings.salseroFollowRadius(), settings.salseroStopDistance());
        this.factory = new DonPollosEntityFactory(plugin);
        this.bedrock = new DonPollosBedrock();
        boolean modelEngine = plugin.getServer().getPluginManager().getPlugin(DonPollosBedrock.MODEL_ENGINE_PLUGIN) != null;
        this.bedrockModels = DonPollosBedrock.bedrockModels(configuration.getString("bedrock.modelos-3d", "auto"),
            modelEngine);
        plugin.getLogger().info(bedrockModels
            ? "[Don Pollos] Bedrock: los jugadores de Bedrock ven los modelos 3D (GeyserModelEngine)."
            : "[Don Pollos] Bedrock: los jugadores de Bedrock ven los mobs de abajo (falta GeyserModelEngine).");
        bedrock.install(plugin);
        this.binder = new DonPollosModelBinder(resolveModelGateway(), plugin.getLogger());
        this.sounds = new DonPollosSounds(plugin);
        this.items = new DonPollosItems(plugin);
        this.menus = new DonPollosMenuController(
            (player, trade) -> service.trade(inventory(player), trade),
            (player, offer) -> {
                DonPollosResult result = service.buy(inventory(player), offer);
                Villager aura = lastAura.get(player.getUniqueId());
                if (result.success() && aura != null && aura.isValid()) {
                    sounds.play(aura);
                }
                return result;
            },
            player -> inventory(player).count(DonPollosService.CURRENCY),
            items,
            this::startBattle);
        this.battles = new DonPollosBattleManager(plugin, binder, settings.battle(), settings.salseroDances(),
            villager -> factory.applyBehaviour(villager, settings.variant(DonPolloVariant.SALSERO)),
            villager -> sounds.play(villager));

        registrations.ownCleanup("don-pollos-runtime", this::shutdown);
        registrations.registerListener("don-pollos-listener", new DonPollosListener(this, factory));
        registrations.registerListener("don-pollos-menus", menus);
        registrations.registerListener("don-pollos-roulette", roulette);
        registrations.registerListener("don-pollos-battles", battles);
        battles.startTicking();
        try {
            cities = DonPollosCityManager.create(plugin, this::spawn, settings.cities());
            registrations.registerListener("don-pollos-cities", cities);
            cities.start();
        } catch (IOException exception) {
            plugin.getLogger().warning("[Don Pollos] Ciudades desactivadas: " + exception.getMessage());
        }
        bosses = new DonPollosBossManager(plugin, binder, sounds, settings.boss());
        registrations.registerListener("don-pollos-bosses", bosses);
        bosses.start();
        secret = new DonPollosSecret(plugin, items, bosses,
            player -> menus.openTrades(player, settings.variant(DonPolloVariant.GORDITO)));
        registrations.registerListener("don-pollos-secret", secret);
        secret.start();
        registrations.scheduleRepeating("don-pollos-salseros", this::tickSalseros, 20L, settings.salseroUpdateTicks());
        menus.onMenuClosed(this::farewell);
        roulette.onMenuClosed(player -> farewell(player, DonPolloVariant.FINO));
        secret.onMenuClosed(player -> farewell(player, DonPolloVariant.GORDITO));
        cityVisitKey = new NamespacedKey(plugin, "ciudades_visitadas");
        registrations.scheduleRepeating("don-pollos-city-visits", this::checkCityVisits, 40L, 20L);

        PluginCommand pluginCommand = plugin.getCommand("donpollo");
        if (pluginCommand == null) {
            throw new IllegalStateException("Required command is not declared: donpollo");
        }
        DonPollosCommand command = new DonPollosCommand(this);
        registrations.bindCommand("don-pollos-command", pluginCommand, command, command);

        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                factory.variant(villager).ifPresent(variant -> track(villager, variant));
            }
        }
        plugin.getLogger().info("[Don Pollos] Module enabled. Cargados: " + loaded.size() + ".");
    }

    @Override
    public void onDisable() {
        shutdown();
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    private void shutdown() {
        if (roulette != null) {
            roulette.shutdown();
            roulette = null;
        }
        if (battles != null) {
            battles.shutdown();
            battles = null;
        }
        if (cities != null) {
            cities.shutdown();
            cities = null;
        }
        if (bosses != null) {
            bosses.shutdown();
            bosses = null;
        }
        if (secret != null) {
            secret.stop();
            secret = null;
        }
        if (menus != null) {
            menus.closeAll();
        }
        for (TrackedPollo pollo : loaded.values()) {
            if (pollo.villager().isValid()) {
                pollo.villager().setInvisible(false);
            }
        }
        if (binder != null) {
            binder.releaseAll();
        }
        loaded.clear();
        currentDance.clear();
        lastAura.clear();
    }

    public Villager spawn(Location location, DonPolloVariant variant) {
        Villager villager = factory.spawn(location, settings.variant(variant));
        track(villager, variant);
        return villager;
    }

    void track(Villager villager, DonPolloVariant variant) {
        DonPollosVariantSettings variantSettings = settings.variant(variant);
        factory.applyBehaviour(villager, variantSettings);
        loaded.put(villager.getUniqueId(), new TrackedPollo(villager, variant));
        binder.bind(villager, variantSettings);
    }

    void untrack(UUID entityId) {
        currentDance.remove(entityId);
        if (sounds != null) {
            sounds.forget(entityId);
        }
        if (loaded.remove(entityId) != null) {
            binder.release(entityId);
        }
    }

    void giveProduct(Player player, DonPollosProduct product, int amount) {
        inventory(player).giveProduct(product, amount);
    }

    void playerQuit(Player player) {
        lastAura.remove(player.getUniqueId());
        if (roulette != null) {
            roulette.playerQuit(player);
        }
    }

    boolean removeNearest(Location location, double maxDistance) {
        double maxDistanceSquared = maxDistance * maxDistance;
        TrackedPollo nearest = null;
        double best = Double.MAX_VALUE;
        for (TrackedPollo pollo : loaded.values()) {
            Villager villager = pollo.villager();
            if (!villager.isValid() || villager.getWorld() != location.getWorld()) {
                continue;
            }
            double distance = villager.getLocation().distanceSquared(location);
            if (distance <= maxDistanceSquared && distance < best) {
                best = distance;
                nearest = pollo;
            }
        }
        if (nearest == null) {
            return false;
        }
        untrack(nearest.villager().getUniqueId());
        nearest.villager().remove();
        return true;
    }

    String loadedSummary() {
        Map<DonPolloVariant, Long> counts = new EnumMap<>(DonPolloVariant.class);
        for (TrackedPollo pollo : loaded.values()) {
            counts.merge(pollo.variant(), 1L, Long::sum);
        }
        if (counts.isEmpty()) {
            return "ninguno";
        }
        return counts.entrySet().stream()
            .map(entry -> entry.getKey().id() + "=" + entry.getValue())
            .collect(Collectors.joining(", "));
    }

    void handleInteraction(Player player, Villager villager, DonPolloVariant variant) {
        DonPollosPlayerView.lookAtEyes(villager, player);
        DonPollosVariantSettings variantSettings = settings.variant(variant);
        switch (variant) {
            case COMUN -> player.playSound(villager.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, 1.0f, 1.0f);
            case GORDITO -> secret.interact(player, villager);
            case AURA_67 -> {
                lastAura.put(player.getUniqueId(), villager);
                menus.openPolleria(player, variantSettings);
            }
            case SALSERO -> {
                if (battles.isBusy(villager.getUniqueId())) {
                    player.sendMessage(battles.isBattling(player.getUniqueId())
                        ? "§d¡Segui bailando!" : "§cEste Salsero esta en plena batalla de baile.");
                } else {
                    menus.openSalsero(player, variantSettings, villager.getUniqueId());
                }
            }
            case FINO -> {
                roulette.openBet(player);
                sounds.play(villager);
            }
        }
    }

    private void tickSalseros() {
        for (TrackedPollo pollo : new ArrayList<>(loaded.values())) {
            if ((pollo.variant() == DonPolloVariant.COMUN || pollo.variant() == DonPolloVariant.GORDITO)
                && pollo.villager().isValid()) {
                sounds.checkApproach(pollo.villager());
            }
            if (pollo.variant() != DonPolloVariant.SALSERO || !pollo.villager().isValid()) {
                continue;
            }
            Villager salsero = pollo.villager();
            if (battles.isBusy(salsero.getUniqueId()) || currentDance.containsKey(salsero.getUniqueId())) {
                salsero.getPathfinder().stopPathfinding();
                continue;
            }
            Player nearest = null;
            double best = Double.MAX_VALUE;
            for (Player player : salsero.getWorld().getPlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()
                    || battles.isBattling(player.getUniqueId())) {
                    continue;
                }
                double distance = player.getLocation().distanceSquared(salsero.getLocation());
                if (distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
            OptionalDouble distance = nearest == null ? OptionalDouble.empty() : OptionalDouble.of(best);
            switch (salseroPolicy.decide(distance)) {
                case FOLLOW -> salsero.getPathfinder().moveTo(nearest, SALSERO_FOLLOW_SPEED);
                case STOP -> {
                    salsero.getPathfinder().stopPathfinding();
                    DonPollosPlayerView.lookAtEyes(salsero, nearest);
                }
                case IDLE -> {
                }
            }
        }
    }

    /**
     * Frase de despedida del Don Pollo cuando terminas de usar su menu. Se espera un tick: si en ese tiempo se abrio
     * otro menu (por ejemplo, del secreto del Gordito a sus intercambios) todavia no terminaste.
     */
    void farewell(Player player, DonPolloVariant variant) {
        String phrase = DonPollosReward.FAREWELLS.get(variant);
        if (phrase == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getType() != InventoryType.CRAFTING) {
                return;
            }
            String name = settings.variant(variant).displayName();
            DonPollosPlayerView.showLine(player, "§e§o\"" + phrase + "\"", name + "§7: §e§o" + phrase);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.8f, 0.8f);
        });
    }

    private void checkCityVisits() {
        if (cities == null) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            cities.cityAt(player.getLocation()).ifPresent(city -> {
                PersistentDataContainer data = player.getPersistentDataContainer();
                String visited = data.getOrDefault(cityVisitKey, PersistentDataType.STRING, "");
                if (Arrays.asList(visited.split("\n")).contains(city)) {
                    return;
                }
                data.set(cityVisitKey, PersistentDataType.STRING, visited.isEmpty() ? city : visited + "\n" + city);
                DonPollosPlayerView.showLine(player, "§6§o" + DonPollosReward.CITY_WELCOME,
                    "§6§o" + DonPollosReward.CITY_WELCOME);
                player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1f, 1f);
            });
        }
    }

    DonPollosSecret secret() {
        return secret;
    }

    DonPollosBossManager bosses() {
        return bosses;
    }

    DonPollosCityManager cities() {
        return cities;
    }

    void startBattle(Player player, UUID salseroId) {
        TrackedPollo pollo = loaded.get(salseroId);
        if (pollo == null || pollo.variant() != DonPolloVariant.SALSERO || !pollo.villager().isValid()) {
            player.sendMessage("§cEse Salsero ya no esta.");
            return;
        }
        String commandDance = currentDance.remove(salseroId);
        if (commandDance != null) {
            binder.stopAnimation(salseroId, commandDance);
        }
        battles.start(player, pollo.villager());
    }

    List<String> danceNames() {
        return settings.salseroDances();
    }

    /**
     * Hace bailar (o parar, con dance == null) a los Salseros elegidos: el mas cercano a {@code near} dentro de
     * {@code radius}, o todos los cargados si near es null. Devuelve cuantos Salseros cambiaron.
     */
    int setDance(Location near, double radius, String dance) {
        List<Villager> targets = new ArrayList<>();
        Villager closest = null;
        double best = radius * radius;
        for (TrackedPollo pollo : loaded.values()) {
            Villager villager = pollo.villager();
            if (pollo.variant() != DonPolloVariant.SALSERO || !villager.isValid()) {
                continue;
            }
            if (near == null) {
                targets.add(villager);
            } else if (villager.getWorld() == near.getWorld()) {
                double distance = villager.getLocation().distanceSquared(near);
                if (distance <= best) {
                    best = distance;
                    closest = villager;
                }
            }
        }
        if (closest != null) {
            targets.add(closest);
        }
        int changed = 0;
        for (Villager salsero : targets) {
            UUID id = salsero.getUniqueId();
            String previous = currentDance.get(id);
            if (dance == null) {
                if (previous != null) {
                    binder.stopAnimation(id, previous);
                    currentDance.remove(id);
                    changed++;
                }
            } else if (binder.switchAnimation(id, previous, dance)) {
                currentDance.put(id, dance);
                salsero.getPathfinder().stopPathfinding();
                changed++;
            }
        }
        return changed;
    }

    org.bukkit.Server server() {
        return plugin.getServer();
    }

    private DonPollosModelGateway resolveModelGateway() {
        Plugin betterModel = plugin.getServer().getPluginManager().getPlugin("BetterModel");
        if (betterModel == null || !betterModel.isEnabled()) {
            plugin.getLogger().info("[Don Pollos] BetterModel no esta instalado: los Don Pollos se veran como aldeanos.");
            return DonPollosModelGateway.unavailable();
        }
        try {
            DonPollosModelInstaller.install(plugin);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[Don Pollos] No se pudieron instalar los modelos",
                exception);
        }
        return new BetterModelDonPollosModelGateway(id -> bedrockModels || !bedrock.isBedrock(id));
    }

    private DonPollosInventoryGateway inventory(Player player) {
        return new BukkitDonPollosInventoryGateway(player, items);
    }

    private record TrackedPollo(Villager villager, DonPolloVariant variant) {
    }
}
