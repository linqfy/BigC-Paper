package dev.linqfy.bigCasares.modules.nexus;

import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.teams.Team;
import dev.linqfy.bigCasares.modules.teams.TeamId;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import org.bukkit.inventory.ItemStack;

public final class NexusModule implements PluginModule {

    public static final String MODULE_ID = "nexus-system";

    private final JavaPlugin plugin;
    private final Function<UUID, Optional<TeamId>> playerTeamLookup;
    private final NexusTeamGateway explicitTeamGateway;
    private final Function<TeamId, Optional<String>> teamPresentationLookup;
    private final JavaModelGateway javaModels;
    private final Map<NexusId, StoredNexus> stored = new LinkedHashMap<>();
    private final NexusAttackTracker attackTracker = new NexusAttackTracker();
    private final List<NexusProtectedContainer> containers = new ArrayList<>();
    private final Map<NexusId, java.util.EnumSet<NexusUpgrade>> upgrades = new LinkedHashMap<>();

    private JavaNexusVisualGateway visualGateway;
    private NexusService service;
    private NexusListener listener;
    private NexusContainerListener containerListener;
    private NexusSettings settings;
    private NexusUpgradeSettings upgradeSettings;
    private File storageFile;
    private BukkitTask restoreTask;
    private BukkitTask attackTask;
    private NexusItem nexusItem;
    private RuntimeRegistrationScope compatibilityScope;

    public NexusModule(JavaPlugin plugin) {
        this(plugin, ignored -> Optional.empty());
    }

    public NexusModule(JavaPlugin plugin, Function<UUID, Optional<TeamId>> playerTeamLookup) {
        this(plugin, playerTeamLookup, ignored -> Optional.empty(), unavailableModels());
    }

    public NexusModule(JavaPlugin plugin, Function<UUID, Optional<TeamId>> playerTeamLookup,
                       Function<TeamId, Optional<String>> teamPresentationLookup) {
        this(plugin, playerTeamLookup, teamPresentationLookup, unavailableModels());
    }

    public NexusModule(JavaPlugin plugin, Function<UUID, Optional<TeamId>> playerTeamLookup,
                       Function<TeamId, Optional<String>> teamPresentationLookup, JavaModelGateway javaModels) {
        this.plugin = plugin;
        this.playerTeamLookup = Objects.requireNonNull(playerTeamLookup, "playerTeamLookup");
        this.teamPresentationLookup = Objects.requireNonNull(teamPresentationLookup, "teamPresentationLookup");
        this.explicitTeamGateway = null;
        this.javaModels = Objects.requireNonNull(javaModels, "javaModels");
    }

    public NexusModule(JavaPlugin plugin, NexusTeamGateway teamGateway) {
        this.plugin = plugin;
        this.playerTeamLookup = ignored -> Optional.empty();
        this.explicitTeamGateway = Objects.requireNonNull(teamGateway, "teamGateway");
        this.teamPresentationLookup = ignored -> Optional.empty();
        this.javaModels = unavailableModels();
    }

    private static JavaModelGateway unavailableModels() {
        return new JavaModelGateway() {
            @Override
            public JavaModelHandle attach(org.bukkit.entity.Entity anchor, String modelKey) {
                throw new IllegalStateException("Java model gateway is required for live Nexus rendering");
            }

            @Override
            public boolean animate(JavaModelHandle handle, String animationKey) {
                throw new IllegalStateException("Java model gateway is required for live Nexus rendering");
            }

            @Override
            public boolean animateOnce(JavaModelHandle handle, String animationKey, Runnable onEnd) {
                throw new IllegalStateException("Java model gateway is required for live Nexus rendering");
            }

            @Override
            public boolean scale(JavaModelHandle handle, float factor) {
                throw new IllegalStateException("Java model gateway is required for live Nexus rendering");
            }

            @Override
            public void close(JavaModelHandle handle) {
                throw new IllegalStateException("Java model gateway is required for live Nexus rendering");
            }
        };
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        this.compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        if (plugin == null) {
            throw new IllegalStateException("NexusModule needs a plugin instance before it can be enabled");
        }
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        scope.register("module-state", this::clearRuntimeState);
        org.bukkit.NamespacedKey itemKey = new org.bukkit.NamespacedKey(plugin, "nexus");
        if (plugin instanceof dev.linqfy.bigCasares.BigCasares bc) {
            this.nexusItem = new NexusItem(bc.getCustomItemRegistry(), itemKey);
            bc.getCustomItemRegistry().register(nexusItem);
            scope.register("custom-item", () -> bc.getCustomItemRegistry().unregister(NexusItem.ID));
        } else {
            throw new IllegalStateException("NexusModule requires the BigCasares item catalog");
        }
        settings = NexusSettings.fromConfig(plugin.getConfig());
        upgradeSettings = NexusUpgradeSettings.fromConfig(plugin.getConfig());
        storageFile = new File(plugin.getDataFolder(), "data/nexus/nexuses.yml");
        loadStoredNexuses();
        visualGateway = new JavaNexusVisualGateway(plugin, javaModels, registrations);
        JavaNexusVisualGateway ownedVisualGateway = visualGateway;
        scope.register("visual-gateway", ownedVisualGateway::shutdown);
        NexusTeamGateway teamGateway = explicitTeamGateway == null ? this::isMemberOfOwningTeam : explicitTeamGateway;
        service = new NexusService(
            settings.maximumHealth(), settings.damageMultipliers(), teamGateway, visualGateway,
            settings.damagePolicy(),
            this::persistDamageSnapshot);
        listener = new NexusListener(
            service,
            new NexusDamageResolver(),
            new NexusPlacementPolicy(settings.placement()),
            visualGateway,
            this::destroyNexus,
            (nid, attacker) -> {
                if (attackTracker.register(nid, attacker)) {
                    // New attack started
                    StoredNexus sn = stored.get(nid);
                    if (sn != null) {
                        plugin.getServer().broadcastMessage("§c¡El Nexus del equipo " + sn.teamName() + " está bajo ataque!");
                        if (plugin instanceof dev.linqfy.bigCasares.BigCasares activeBigCasares
                            && activeBigCasares.getTeamModule() != null) {
                            activeBigCasares.getTeamModule().service().ifPresent(ts -> {
                                var teamOpt = ts.findById(sn.teamId());
                                if (teamOpt.isPresent()) {
                                    for (UUID member : teamOpt.get().members().keySet()) {
                                        org.bukkit.entity.Player p = plugin.getServer().getPlayer(member);
                                        if (p != null && p.isOnline()) {
                                            p.playSound(p.getLocation(), org.bukkit.Sound.EVENT_MOB_EFFECT_RAID_OMEN, 1.0f, 1.0f);
                                            p.sendTitle("", "§c¡Tú Nexus está bajo ataque!", 10, 70, 20);
                                        }
                                    }
                                }
                            });
                        }
                        sendRaidAlert(sn, false);
                    }
                }
            },
            this::registerContainer
        );
        registrations.registerListener("nexus-listener", listener);
        containerListener = new NexusContainerListener(this);
        registrations.registerListener("nexus-container-listener", containerListener);
        restoreTask = registrations.scheduleImmediate("nexus-restore", this::restoreStoredNexuses);
        attackTask = registrations.scheduleRepeating("nexus-attack-state", this::refreshTransientState, 20L, 20L);
        registrations.scheduleRepeating("nexus-upgrade-effects", this::applyUpgradeEffects,
            upgradeSettings.intervalTicks(), upgradeSettings.intervalTicks());
        registrations.registerListener("nexus-upgrade-spawn-listener", new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
            public void onHostileSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
                if (blocksHostileSpawn(event)) {
                    event.setCancelled(true);
                }
            }
        });

        registrations.registerListener("nexus-placement-listener", new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
            public void onNexusPlace(org.bukkit.event.block.BlockPlaceEvent event) {
                if (nexusItem.matches(event.getItemInHand())) {
                    event.setCancelled(true);
                    org.bukkit.entity.Player player = event.getPlayer();
                    Optional<TeamId> teamIdOpt = playerTeamLookup.apply(player.getUniqueId());
                    if (teamIdOpt.isEmpty()) {
                        player.sendMessage("§cNecesitas estar en un equipo para colocar el Nexus.");
                        return;
                    }
                    if (plugin instanceof dev.linqfy.bigCasares.BigCasares bc) {
                        var teamModule = bc.getTeamModule();
                        if (teamModule != null) {
                            teamModule.service().ifPresent(ts -> {
                                var teamOpt = ts.findById(teamIdOpt.get());
                                if (teamOpt.isPresent()) {
                                    try {
                                        NexusPlacementAttempt attempt = placeNexus(event.getBlock().getLocation().add(0.5, 0, 0.5), teamOpt.get());
                                        if (attempt.result().allowed()) {
                                            ItemStack hand = event.getItemInHand();
                                            hand.setAmount(hand.getAmount() - 1);
                                            player.sendMessage("§aNexus colocado exitosamente.");
                                        } else {
                                            player.sendMessage("§cNo puedes colocar el Nexus aquí: " + attempt.result().rejection().name());
                                        }
                                    } catch (IllegalStateException ex) {
                                        player.sendMessage("§c" + ex.getMessage());
                                    }
                                }
                            });
                        }
                    }
                }
            }
        });

        plugin.getLogger().info("[NexusModule] Enabled with final-damage processing and YAML recovery.");
    }

    @Override
    public void onDisable() {
        if (restoreTask != null) {
            restoreTask.cancel();
            restoreTask = null;
        }
        if (attackTask != null) { attackTask.cancel(); attackTask = null; }
        saveStoredNexuses();
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (containerListener != null) { HandlerList.unregisterAll(containerListener); containerListener = null; }
        if (visualGateway != null) {
            visualGateway.shutdown();
        }
        if (plugin instanceof dev.linqfy.bigCasares.BigCasares bc) {
            bc.getCustomItemRegistry().unregister(NexusItem.ID);
        }
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        clearRuntimeState();
    }

    public NexusPlacementAttempt placeNexus(Location location, Team team) {
        if (listener == null || service == null || settings == null) {
            throw new IllegalStateException("Nexus System no está disponible.");
        }
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(team, "team");
        if (stored.values().stream().anyMatch(nexus -> nexus.teamId().equals(team.id()))) {
            throw new IllegalStateException("Tu equipo ya tiene un Nexus activo.");
        }
        UUID worldId = Objects.requireNonNull(location.getWorld(), "location world").getUID();
        NexusId nexusId = NexusId.random();
        NexusVisualRequest request = new NexusVisualRequest(
            nexusId,
            new NexusPosition(worldId, location.getX(), location.getY(), location.getZ(), location.getYaw()),
            team.color().legacyCode() + team.name().toUpperCase() + " [" + team.tag() + "]",
            settings.maximumHealth(),
            settings.maximumHealth(),
            "bigcasares:nexus"
        );
        NexusPlacementResult result = listener.placeNexus(request);
        if (result.allowed()) {
            stored.put(nexusId, new StoredNexus(team.id(), team.name(), request));
            saveStoredNexuses();
        }
        return new NexusPlacementAttempt(nexusId, result);
    }

    public NexusService getService() {
        return service;
    }

    public NexusListener getListener() {
        return listener;
    }

    public JavaNexusVisualGateway getVisualGateway() {
        return visualGateway;
    }

    private boolean isMemberOfOwningTeam(NexusId nexusId, UUID playerId) {
        StoredNexus nexus = stored.get(nexusId);
        return nexus != null && playerTeamLookup.apply(playerId).filter(nexus.teamId()::equals).isPresent();
    }

    private void destroyNexus(NexusId nexusId) {
        StoredNexus destroyed = stored.get(nexusId);
        if (destroyed != null) {
            sendRaidAlert(destroyed, true);
        }
        upgrades.remove(nexusId);
        attackTracker.clear(nexusId);
        containers.removeIf(container -> container.nexusId().equals(nexusId));
        stored.remove(nexusId);
        if (service != null) {
            service.remove(nexusId);
        }
        saveStoredNexuses();
    }

    private void refreshTransientState() {
        if (visualGateway == null) return;
        for (Map.Entry<NexusId, StoredNexus> entry : stored.entrySet()) {
            NexusPosition origin = entry.getValue().request().position();
            boolean attacked = attackTracker.retain(entry.getKey(), attackerId -> {
                org.bukkit.entity.Player player = plugin.getServer().getPlayer(attackerId);
                if (player == null || !player.isOnline() || player.isDead() || player.getWorld().getUID().compareTo(origin.worldId()) != 0) return false;
                double dx = player.getX() - origin.x(), dy = player.getY() - origin.y(), dz = player.getZ() - origin.z();
                return dx * dx + dy * dy + dz * dz <= 3600.0;
            });
            visualGateway.setUnderAttack(entry.getKey(), attacked);
            teamPresentationLookup.apply(entry.getValue().teamId())
                    .ifPresent(name -> visualGateway.updateTeamName(entry.getKey(), name));
        }
    }

    private void persistDamageSnapshot(NexusSnapshot snapshot) {
        StoredNexus current = stored.get(snapshot.nexusId());
        if (current == null) {
            return;
        }
        NexusVisualRequest base = current.request();
        NexusVisualRequest updated = new NexusVisualRequest(
            base.nexusId(),
            base.position(),
            base.teamName(),
            snapshot.currentHealth(),
            snapshot.maximumHealth(),
            base.itemModelId()
        );
        stored.put(snapshot.nexusId(), new StoredNexus(current.teamId(), current.teamName(), updated));
        saveStoredNexuses();
    }

    private void restoreStoredNexuses() {
        if (service == null || visualGateway == null) {
            return;
        }
        List<NexusVisualRequest> requests = stored.values().stream()
            .map(StoredNexus::request)
            .filter(request -> request.currentHealth() > 0.0)
            .filter(request -> plugin.getServer().getWorld(request.position().worldId()) != null)
            .toList();
        requests.forEach(service::restore);
        visualGateway.recover(requests);
    }

    private void loadStoredNexuses() {
        stored.clear();
        containers.clear();
        upgrades.clear();
        if (!storageFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection root = yaml.getConfigurationSection("nexuses");
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            try {
                ConfigurationSection section = Objects.requireNonNull(root.getConfigurationSection(rawId));
                NexusId id = NexusId.parse(rawId);
                TeamId teamId = new TeamId(UUID.fromString(section.getString("team-id")));
                NexusPosition position = new NexusPosition(
                    UUID.fromString(section.getString("world")),
                    section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw")
                );
                double health = section.getDouble("health", settings.maximumHealth());
                NexusVisualRequest request = new NexusVisualRequest(
                    id, position, section.getString("team-name", "EQUIPO"),
                    health, settings.maximumHealth(), "bigcasares:nexus");
                stored.put(id, new StoredNexus(teamId, section.getString("team-name", "EQUIPO"), request));
                java.util.EnumSet<NexusUpgrade> installed = java.util.EnumSet.noneOf(NexusUpgrade.class);
                for (String rawUpgrade : section.getStringList("upgrades")) {
                    NexusUpgrade.parse(rawUpgrade).ifPresentOrElse(installed::add, () -> plugin.getLogger()
                        .warning("Mejora de Nexus desconocida en " + rawId + ": " + rawUpgrade));
                }
                if (!installed.isEmpty()) {
                    upgrades.put(id, installed);
                }
            } catch (IllegalArgumentException | NullPointerException ex) {
                plugin.getLogger().warning("Nexus persistido inválido " + rawId + ": " + ex.getMessage());
            }
        }
        for (Map<?, ?> raw : yaml.getMapList("containers")) {
            try {
                containers.add(new NexusProtectedContainer(NexusId.parse(String.valueOf(raw.get("nexus-id"))),
                        new TeamId(UUID.fromString(String.valueOf(raw.get("team-id")))), UUID.fromString(String.valueOf(raw.get("world"))),
                        Integer.parseInt(String.valueOf(raw.get("x"))), Integer.parseInt(String.valueOf(raw.get("y"))), Integer.parseInt(String.valueOf(raw.get("z")))));
            } catch (RuntimeException ex) { plugin.getLogger().warning("Contenedor Nexus inválido: " + ex.getMessage()); }
        }
    }

    private void saveStoredNexuses() {
        if (storageFile == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<NexusId, StoredNexus> entry : stored.entrySet()) {
            NexusId id = entry.getKey();
            StoredNexus storedNexus = entry.getValue();
            NexusVisualRequest base = storedNexus.request();
            double health = service == null
                ? base.currentHealth()
                : service.find(id).map(NexusSnapshot::currentHealth).orElse(base.currentHealth());
            String path = "nexuses." + id;
            yaml.set(path + ".team-id", storedNexus.teamId().toString());
            yaml.set(path + ".team-name", storedNexus.teamName());
            yaml.set(path + ".world", base.position().worldId().toString());
            yaml.set(path + ".x", base.position().x());
            yaml.set(path + ".y", base.position().y());
            yaml.set(path + ".z", base.position().z());
            yaml.set(path + ".yaw", base.position().yaw());
            yaml.set(path + ".health", health);
            java.util.EnumSet<NexusUpgrade> installed = upgrades.get(id);
            if (installed != null && !installed.isEmpty()) {
                yaml.set(path + ".upgrades", installed.stream().map(NexusUpgrade::id).toList());
            }
        }
        yaml.set("containers", containers.stream().map(container -> Map.of(
                "nexus-id", container.nexusId().toString(), "team-id", container.teamId().toString(),
                "world", container.worldId().toString(), "x", container.x(), "y", container.y(), "z", container.z())).toList());
        try {
            File parent = storageFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            yaml.save(storageFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "No se pudieron guardar los Nexus", ex);
        }
    }

    private record StoredNexus(TeamId teamId, String teamName, NexusVisualRequest request) {
    }

    private void clearRuntimeState() {
        restoreTask = null;
        attackTask = null;
        listener = null;
        containerListener = null;
        nexusItem = null;
        service = null;
        visualGateway = null;
        settings = null;
        upgradeSettings = null;
        storageFile = null;
    }

    private boolean registerContainer(org.bukkit.block.Block block, UUID playerId) {
        StoredNexus owner = stored.values().stream().filter(nexus -> {
            NexusPosition p = nexus.request().position();
            return p.worldId().equals(block.getWorld().getUID())
                    && Math.abs(block.getX() - p.x()) <= settings.placement().containerClearanceHorizontal()
                    && Math.abs(block.getY() - p.y()) <= settings.placement().containerClearanceVertical()
                    && Math.abs(block.getZ() - p.z()) <= settings.placement().containerClearanceHorizontal();
        }).findFirst().orElse(null);
        if (owner == null || playerTeamLookup.apply(playerId).filter(owner.teamId()::equals).isEmpty()) return false;
        containers.removeIf(value -> value.matches(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
        NexusId nexusId = stored.entrySet().stream().filter(e -> e.getValue() == owner).map(Map.Entry::getKey).findFirst().orElseThrow();
        containers.add(new NexusProtectedContainer(nexusId, owner.teamId(), block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
        saveStoredNexuses();
        return true;
    }

    /**
     * Si la caja [minX..maxX] x [minY..maxY] x [minZ..maxZ] de ese mundo toca el volumen protegido de algun nexus
     * activo. Para modulos que modifican terreno (por ejemplo los pozos de Don Pollos). Con el modulo apagado no hay
     * nada que proteger.
     */
    public boolean intersectsProtectedVolume(UUID worldId, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        NexusService currentService = service;
        NexusSettings currentSettings = settings;
        if (currentService == null || currentSettings == null) {
            return false;
        }
        int horizontal = currentSettings.placement().containerClearanceHorizontal();
        int vertical = currentSettings.placement().containerClearanceVertical();
        return currentService.activePositions().stream()
                .filter(position -> position.worldId().equals(worldId))
                .map(NexusPosition::blockPosition)
                .anyMatch(nexus -> volumeTouches(nexus.x(), nexus.y(), nexus.z(), horizontal, vertical,
                        minX, maxX, minY, maxY, minZ, maxZ));
    }

    /** El volumen de un nexus (su posicion +- horizontal/vertical) toca la caja. */
    static boolean volumeTouches(int nexusX, int nexusY, int nexusZ, int horizontal, int vertical,
                                 int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return nexusX >= minX - horizontal && nexusX <= maxX + horizontal
                && nexusY >= minY - vertical && nexusY <= maxY + vertical
                && nexusZ >= minZ - horizontal && nexusZ <= maxZ + horizontal;
    }

    public boolean isProtectedContainer(org.bukkit.block.Block block) { return findContainer(block).isPresent(); }
    public boolean canUseContainer(UUID playerId, org.bukkit.block.Block block) {
        return findContainer(block).map(value -> playerTeamLookup.apply(playerId).filter(value.teamId()::equals).isPresent()).orElse(true);
    }
    public void unregisterContainer(org.bukkit.block.Block block) {
        containers.removeIf(value -> value.matches(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
        saveStoredNexuses();
    }
    private Optional<NexusProtectedContainer> findContainer(org.bukkit.block.Block block) {
        return containers.stream().filter(value -> value.matches(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ())).findFirst();
    }

    public boolean hasActiveNexus(TeamId teamId) {
        return stored.values().stream().anyMatch(nexus -> nexus.teamId().equals(teamId));
    }

    public boolean hasNexusItemInTeam(Team team) {
        if (!(plugin instanceof dev.linqfy.bigCasares.BigCasares)) {
            return false;
        }
        var registry = ((dev.linqfy.bigCasares.BigCasares) plugin).getCustomItemRegistry();
        var nexusItemOpt = registry.findById(NexusItem.ID);
        if (nexusItemOpt.isEmpty()) {
            return false;
        }
        var item = nexusItemOpt.get();
        for (UUID memberId : team.members().keySet()) {
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(memberId);
            if (player != null && player.isOnline()) {
                if (hasNexusItem(player, item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasNexusItem(org.bukkit.entity.Player player, dev.linqfy.bigCasares.items.CustomItem item) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && item.matches(stack)) {
                return true;
            }
        }
        for (ItemStack stack : player.getEnderChest().getContents()) {
            if (stack != null && item.matches(stack)) {
                return true;
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && item.matches(cursor)) {
            return true;
        }
        return false;
    }

    public void makeNexusTeamless(TeamId teamId) {
        TeamId teamlessId = new TeamId(new UUID(0, 0));
        // Update containers
        for (int i = 0; i < containers.size(); i++) {
            NexusProtectedContainer container = containers.get(i);
            if (container.teamId().equals(teamId)) {
                containers.set(i, new NexusProtectedContainer(
                    container.nexusId(),
                    teamlessId,
                    container.worldId(),
                    container.x(),
                    container.y(),
                    container.z()
                ));
            }
        }
        // Update stored
        StoredNexus target = null;
        NexusId nexusId = null;
        for (Map.Entry<NexusId, StoredNexus> entry : stored.entrySet()) {
            if (entry.getValue().teamId().equals(teamId)) {
                nexusId = entry.getKey();
                target = entry.getValue();
                break;
            }
        }
        if (target != null && nexusId != null) {
            NexusVisualRequest base = target.request();
            NexusVisualRequest updatedRequest = new NexusVisualRequest(
                base.nexusId(),
                base.position(),
                "SIN EQUIPO",
                base.currentHealth(),
                base.maximumHealth(),
                base.itemModelId()
            );
            stored.put(nexusId, new StoredNexus(teamlessId, "SIN EQUIPO", updatedRequest));
            if (visualGateway != null) {
                visualGateway.updateTeamName(nexusId, "SIN EQUIPO");
            }
            saveStoredNexuses();
        }
    }

    public Optional<NexusUpgradeSettings> upgradeSettings() {
        return Optional.ofNullable(upgradeSettings);
    }

    /** Mejoras instaladas en el Nexus activo del equipo (vacío si no tiene Nexus). */
    public java.util.Set<NexusUpgrade> upgradesOf(TeamId teamId) {
        return findNexusId(teamId)
            .map(upgrades::get)
            .<java.util.Set<NexusUpgrade>>map(java.util.EnumSet::copyOf)
            .orElse(java.util.Set.of());
    }

    /** Puede instalarse: el equipo tiene Nexus activo y todavía no tiene esta mejora. */
    public NexusUpgradeResult canInstall(TeamId teamId, NexusUpgrade upgrade) {
        Optional<NexusId> nexusId = findNexusId(teamId);
        if (nexusId.isEmpty()) {
            return NexusUpgradeResult.NO_ACTIVE_NEXUS;
        }
        java.util.EnumSet<NexusUpgrade> installed = upgrades.get(nexusId.get());
        return installed != null && installed.contains(upgrade)
            ? NexusUpgradeResult.ALREADY_INSTALLED
            : NexusUpgradeResult.INSTALLED;
    }

    public NexusUpgradeResult installUpgrade(TeamId teamId, NexusUpgrade upgrade) {
        Objects.requireNonNull(upgrade, "upgrade");
        NexusUpgradeResult check = canInstall(teamId, upgrade);
        if (check != NexusUpgradeResult.INSTALLED) {
            return check;
        }
        NexusId nexusId = findNexusId(teamId).orElseThrow();
        upgrades.computeIfAbsent(nexusId, ignored -> java.util.EnumSet.noneOf(NexusUpgrade.class)).add(upgrade);
        saveStoredNexuses();
        return NexusUpgradeResult.INSTALLED;
    }

    private Optional<NexusId> findNexusId(TeamId teamId) {
        return stored.entrySet().stream()
            .filter(entry -> entry.getValue().teamId().equals(teamId))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    private Optional<Location> nexusLocation(StoredNexus nexus) {
        NexusPosition position = nexus.request().position();
        org.bukkit.World world = plugin.getServer().getWorld(position.worldId());
        if (world == null || !world.isChunkLoaded((int) Math.floor(position.x()) >> 4, (int) Math.floor(position.z()) >> 4)) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, position.x(), position.y(), position.z()));
    }

    private void applyUpgradeEffects() {
        NexusUpgradeSettings current = upgradeSettings;
        if (current == null) {
            return;
        }
        for (Map.Entry<NexusId, java.util.EnumSet<NexusUpgrade>> entry : upgrades.entrySet()) {
            StoredNexus nexus = stored.get(entry.getKey());
            if (nexus == null || entry.getValue().isEmpty()) {
                continue;
            }
            Optional<Location> origin = nexusLocation(nexus);
            if (origin.isEmpty()) {
                continue;
            }
            if (entry.getValue().contains(NexusUpgrade.MOB_REPULSOR)) {
                repelHostiles(origin.get(), current);
            }
            if (entry.getValue().contains(NexusUpgrade.SATURATION_AURA)) {
                feedTeam(origin.get(), nexus.teamId(), current);
            }
        }
    }

    private void repelHostiles(Location origin, NexusUpgradeSettings current) {
        double radius = current.repulsorRadius();
        for (org.bukkit.entity.Entity entity : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (!isRepellable(entity) || entity.getLocation().distanceSquared(origin) > radius * radius) {
                continue;
            }
            double[] push = NexusUpgradeSettings.pushAway(
                entity.getX() - origin.getX(), entity.getZ() - origin.getZ(), current.repulsorStrength());
            entity.setVelocity(new org.bukkit.util.Vector(push[0], 0.35, push[1]));
            if (entity instanceof org.bukkit.entity.Mob mob) {
                mob.setTarget(null);
            }
        }
    }

    /** Solo mobs hostiles comunes: nada de bosses ni mobs con nombre (eventos y jefes del servidor). */
    private static boolean isRepellable(org.bukkit.entity.Entity entity) {
        return entity instanceof org.bukkit.entity.Enemy
            && entity instanceof org.bukkit.entity.LivingEntity
            && !(entity instanceof org.bukkit.entity.Boss)
            && !(entity instanceof org.bukkit.entity.Player)
            && entity.customName() == null
            && entity.isValid();
    }

    private void feedTeam(Location origin, TeamId teamId, NexusUpgradeSettings current) {
        double radiusSquared = current.saturationRadius() * current.saturationRadius();
        for (org.bukkit.entity.Player player : origin.getWorld().getPlayers()) {
            if (player.isDead() || player.getLocation().distanceSquared(origin) > radiusSquared
                || playerTeamLookup.apply(player.getUniqueId()).filter(teamId::equals).isEmpty()) {
                continue;
            }
            float[] fed = NexusUpgradeSettings.feed(player.getFoodLevel(), player.getSaturation(), current.foodPerPulse());
            player.setFoodLevel((int) fed[0]);
            player.setSaturation(fed[1]);
        }
    }

    private boolean blocksHostileSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        NexusUpgradeSettings current = upgradeSettings;
        if (current == null || !isRepellable(event.getEntity())) {
            return false;
        }
        var reason = event.getSpawnReason();
        if (reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL
            && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
            && reason != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL) {
            return false;
        }
        Location location = event.getLocation();
        double radiusSquared = current.repulsorRadius() * current.repulsorRadius();
        for (Map.Entry<NexusId, java.util.EnumSet<NexusUpgrade>> entry : upgrades.entrySet()) {
            StoredNexus nexus = stored.get(entry.getKey());
            if (nexus == null || !entry.getValue().contains(NexusUpgrade.MOB_REPULSOR)) {
                continue;
            }
            NexusPosition position = nexus.request().position();
            if (!position.worldId().equals(location.getWorld().getUID())) {
                continue;
            }
            double dx = location.getX() - position.x();
            double dy = location.getY() - position.y();
            double dz = location.getZ() - position.z();
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    /** Alerta a los miembros vinculados en Discord: canal privado del equipo o, si no hay, mensaje directo. */
    private void sendRaidAlert(StoredNexus nexus, boolean destroyed) {
        if (!(plugin instanceof dev.linqfy.bigCasares.BigCasares bc) || bc.getTeamModule() == null) {
            return;
        }
        var discordModule = bc.getDiscordIntegrationModule();
        if (discordModule == null || discordModule.gateway() == null || discordModule.linkService() == null) {
            return;
        }
        Optional<Team> team = bc.getTeamModule().service().flatMap(service -> service.findById(nexus.teamId()));
        if (team.isEmpty()) {
            return;
        }
        java.util.Set<Long> discordIds = new java.util.LinkedHashSet<>();
        for (UUID member : team.get().members().keySet()) {
            discordModule.linkService().getDiscordId(member).ifPresent(discordIds::add);
        }
        NexusPosition position = nexus.request().position();
        String coordinates = Math.round(position.x()) + ", " + Math.round(position.y()) + ", " + Math.round(position.z());
        String message = destroyed
            ? "💥 **¡NEXUS DESTRUIDO!** 💥\nEl Nexus de **" + team.get().name() + "** cayó en " + coordinates + "."
            : "🚨 **¡ALERTA DE RAIDEO!** 🚨\nEl Nexus de **" + team.get().name() + "** está siendo atacado en "
                + coordinates + ". ¡Conéctense rápido para defenderlo!";
        discordModule.gateway().sendTeamAlert(
            nexus.teamId().toString(), team.get().tag(), java.util.Set.copyOf(discordIds), message);
    }
}
