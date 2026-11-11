package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Predicate;

public final class MissionModule implements PluginModule {

    private final BigCasares plugin;

    private MissionCatalog catalog;
    private MissionStorage storage;
    private PlayerMissionService playerMissionService;
    private VaultEconomyGateway economyGateway;
    private MissionHudController hudController;
    private boolean enabled;
    private RuntimeRegistrationScope compatibilityScope;
    private final Map<UUID, MissionPlayerState> stateCache = new LinkedHashMap<>();
    private final Set<UUID> dirtyPlayers = new LinkedHashSet<>();

    public MissionModule(BigCasares plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "mission-system";
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        this.compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        Economy economy = VaultEconomyGateway.resolveOrThrow(plugin);
        this.catalog = new MissionCatalogLoader().load(plugin.getConfig());
        this.storage = new YamlMissionStorage(plugin.getDataFolder().toPath().resolve("data").resolve("missions").resolve("players"));
        this.economyGateway = new VaultEconomyGateway(plugin, economy);
        this.playerMissionService = new PlayerMissionService(
            catalog,
            new MissionRotationPolicy(new java.util.Random()),
            Instant::now,
            plugin.getConfig().getInt("mission-system.daily.count", 3),
            plugin.getConfig().getInt("mission-system.weekly.count", 2),
            this::nextDailyReset,
            this::nextWeeklyReset
        );
        this.hudController = new MissionHudController(this, economyGateway);

        scope.register("module-state", this::clearRuntimeState);
        scope.register("hud-open-views", hudController::closeOpenViews);
        registrations.registerListener("mission-listener", new MissionListener(this, plugin));
        registrations.registerListener("hud-listener", hudController);
        registrations.scheduleRepeating("mission-state-flush", this::flushDirtyStates, 600L, 600L);
        this.enabled = true;
    }

    @Override
    public void onDisable() {
        this.enabled = false;
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void openHud(Player player, MissionHudView view) {
        if (!enabled || hudController == null) {
            player.sendMessage("§cEl sistema de misiones no esta disponible.");
            return;
        }
        revalidatePassiveMissions(player);
        hudController.open(player, view);
    }

    public void claimAll(Player player) {
        MissionPlayerState state = getOrCreateState(player);
        MissionPlayerState claimed = playerMissionService.claimAllCompleted(player.getUniqueId(), state, economyGateway);
        storage.save(claimed);
        stateCache.put(player.getUniqueId(), claimed);
        dirtyPlayers.remove(player.getUniqueId());
        double amount = sumClaimables(state);
        if (amount > 0.0) {
            player.sendMessage("§aReclamaste " + economyGateway.format(amount) + " en recompensas.");
        } else {
            player.sendMessage("§eNo tenes recompensas pendientes.");
        }
    }

    public void reroll(Player player) {
        MissionPlayerState fresh = playerMissionService.getOrCreateState(player.getUniqueId(), null);
        storage.save(fresh);
        stateCache.put(player.getUniqueId(), fresh);
        dirtyPlayers.remove(player.getUniqueId());
    }

    public void rerollDaily(Player player) {
        MissionPlayerState state = getOrCreateState(player);

        if (state.dailyRerolledAt() != null) {
            ZoneId zone = ZoneId.systemDefault();
            int hour = plugin.getConfig().getInt("mission-system.reset.daily-hour", 0);
            LocalDateTime nowLdt = LocalDateTime.now(zone);
            LocalDateTime periodStart = nowLdt.withHour(hour).withMinute(0).withSecond(0).withNano(0);
            if (periodStart.isAfter(nowLdt)) {
                periodStart = periodStart.minusDays(1);
            }
            Instant periodStartInstant = periodStart.atZone(zone).toInstant();

            if (!state.dailyRerolledAt().isBefore(periodStartInstant)) {
                player.sendMessage("§cYa usaste tu rerol diario. Espera al reinicio diario.");
                return;
            }
        }

        MissionPlayerState rerolled = playerMissionService.rerollDailyAssignments(player.getUniqueId(), state, Instant.now());
        storage.save(rerolled);
        stateCache.put(player.getUniqueId(), rerolled);
        dirtyPlayers.remove(player.getUniqueId());
        player.sendMessage("§aTus misiones diarias fueron reasignadas.");
    }

    public void reset(Player player) {
        reroll(player);
    }

    public MissionPlayerState getOrCreateState(Player player) {
        return stateCache.compute(player.getUniqueId(), (id, cached) -> {
            MissionPlayerState source = cached == null ? storage.load(id).orElse(null) : cached;
            MissionPlayerState resolved = playerMissionService.getOrCreateState(id, source);
            if (resolved != source) dirtyPlayers.add(id);
            return resolved;
        });
    }

    public int countClaimables(MissionPlayerState state) {
        return claimableAssignments(state).size();
    }

    public List<MissionAssignment> claimableAssignments(MissionPlayerState state) {
        List<MissionAssignment> claimables = new ArrayList<>();
        appendClaimables(claimables, state.dailyAssignments());
        appendClaimables(claimables, state.weeklyAssignments());
        return claimables;
    }

    public double sumClaimables(MissionPlayerState state) {
        return claimableAssignments(state).stream()
            .mapToDouble(assignment -> assignment.definition().reward())
            .sum();
    }

    public void incrementMatchingMissions(Player player, MissionType missionType, int delta) {
        updateMatchingMissions(player, missionType, ProgressUpdateMode.INCREMENT, delta, null, null, null);
    }

    public void completeRenameMission(Player player, Material itemType, String newName) {
        updateMatchingMissions(player, MissionType.RENAME_ITEM_TO_EXACT_NAME, ProgressUpdateMode.CONDITIONAL_SET, 1, itemType, newName, null);
    }

    public void completeFinalHitMission(Player player, Material weapon) {
        updateMatchingMissions(player, MissionType.FINAL_HIT_PLAYER_WITH_ITEM, ProgressUpdateMode.INCREMENT, 1, weapon, null, null);
    }

    public void completeMobKillMission(Player player, String entityType, Material weapon) {
        updateMatchingMissions(player, MissionType.KILL_ENTITY_WITH_ITEM_ONLY, ProgressUpdateMode.INCREMENT, 1, weapon, null, entityType);
    }

    public void completeEntityNameMission(Player player, Entity entity, String customName) {
        MissionPlayerState state = getOrCreateState(player);
        MissionPlayerState updated = state;
        for (MissionScope scope : MissionScope.values()) {
            for (MissionAssignment assignment : assignmentsFor(updated, scope).values()) {
                MissionDefinition definition = assignment.definition();
                if (definition.type() == MissionType.NAME_ENTITY_AFTER_PLAYER
                    && matchesCondition(player, definition, null, customName, entity.getType().name())) {
                    // Keyed by entity so renaming the same pig again does not count twice.
                    updated = playerMissionService.addProgressMarker(updated, scope, definition.id(), entity.getUniqueId().toString());
                }
            }
        }
        cacheUpdated(updated);
    }

    /** Adds {@code delta} to every assigned mission of the given type whose definition passes the filter. */
    public void incrementWhere(Player player, MissionType type, Predicate<MissionDefinition> filter, int delta) {
        MissionPlayerState state = getOrCreateState(player);
        MissionPlayerState updated = state;
        for (MissionScope scope : MissionScope.values()) {
            for (MissionAssignment assignment : assignmentsFor(updated, scope).values()) {
                MissionDefinition definition = assignment.definition();
                if (definition.type() == type && filter.test(definition)) {
                    updated = playerMissionService.updateAbsoluteProgress(
                        updated, scope, definition.id(), assignment.snapshot().progress() + delta);
                }
            }
        }
        if (updated != state) {
            cacheUpdated(updated);
        }
    }

    public void recordTargetEvent(Player player, MissionType type, List<String> targetNames) {
        incrementWhere(player, type, definition -> MissionTargets.matches(definition.params().get("target"), targetNames), 1);
    }

    public void recordSameTarget(Player player, MissionType type, String targetKey) {
        MissionPlayerState state = getOrCreateState(player);
        MissionPlayerState updated = state;
        for (MissionScope scope : MissionScope.values()) {
            for (MissionAssignment assignment : assignmentsFor(updated, scope).values()) {
                if (assignment.definition().type() == type) {
                    updated = playerMissionService.recordSameTargetProgress(updated, scope, assignment.definition().id(), targetKey);
                }
            }
        }
        if (updated != state) {
            cacheUpdated(updated);
        }
    }

    /**
     * Completes the player's assigned {@link MissionType#ADMIN_APPROVAL} missions, or only the given
     * mission id when one is supplied. Returns how many missions were completed.
     */
    public int approveMissions(Player player, String missionId) {
        MissionPlayerState state = getOrCreateState(player);
        MissionPlayerState updated = state;
        int approved = 0;
        for (MissionScope scope : MissionScope.values()) {
            for (MissionAssignment assignment : assignmentsFor(updated, scope).values()) {
                MissionDefinition definition = assignment.definition();
                boolean selected = missionId == null
                    ? definition.type() == MissionType.ADMIN_APPROVAL
                    : definition.id().equalsIgnoreCase(missionId);
                if (selected && !assignment.snapshot().completed()) {
                    updated = playerMissionService.forceComplete(updated, scope, definition.id());
                    approved++;
                }
            }
        }
        if (approved > 0) {
            storage.save(updated);
            stateCache.put(player.getUniqueId(), updated);
            dirtyPlayers.remove(player.getUniqueId());
        }
        return approved;
    }

    public boolean areTeammates(Player first, Player second) {
        var teamModule = plugin.getTeamModule();
        if (teamModule == null) {
            return false;
        }
        return teamModule.service()
            .flatMap(service -> service.findByMember(first.getUniqueId()))
            .map(team -> team.containsMember(second.getUniqueId()))
            .orElse(false);
    }

    private static Map<String, MissionAssignment> assignmentsFor(MissionPlayerState state, MissionScope scope) {
        return scope == MissionScope.DAILY ? state.dailyAssignments() : state.weeklyAssignments();
    }

    public void revalidatePassiveMissions(Player player) {
        MissionPlayerState state = getOrCreateState(player);
        MissionPlayerState updated = state;

        for (MissionAssignment assignment : state.dailyAssignments().values()) {
            updated = revalidateAssignment(player, updated, MissionScope.DAILY, assignment);
        }
        for (MissionAssignment assignment : state.weeklyAssignments().values()) {
            updated = revalidateAssignment(player, updated, MissionScope.WEEKLY, assignment);
        }

        cacheUpdated(updated);
    }

    private MissionPlayerState revalidateAssignment(Player player, MissionPlayerState state, MissionScope scope, MissionAssignment assignment) {
        MissionDefinition definition = assignment.definition();
        int progress = assignment.snapshot().progress();

        switch (definition.type()) {
            case HOLD_EXACT_ITEM_COUNT -> {
                Material material = Material.valueOf(String.valueOf(definition.params().get("material")));
                progress = MissionProgressEngine.countInventoryMaterial(player, material);
            }
            case EQUIP_SPECIFIC_ITEM -> {
                Material material = Material.valueOf(String.valueOf(definition.params().get("material")));
                boolean requiresCurse = Boolean.parseBoolean(String.valueOf(definition.params().getOrDefault("binding-curse", false)));
                boolean equipped = requiresCurse
                    ? MissionProgressEngine.isWearingCursed(player, material)
                    : MissionProgressEngine.isWearing(player, material);
                Object minY = definition.params().get("min-y");
                if (equipped && minY != null) {
                    equipped = player.getLocation().getBlockY() >= Integer.parseInt(String.valueOf(minY));
                }
                progress = equipped ? definition.goal() : 0;
            }
            case STAND_ON_BLOCK_AT_Y -> {
                Material material = Material.valueOf(String.valueOf(definition.params().get("material")));
                int yLevel = Integer.parseInt(String.valueOf(definition.params().get("y")));
                progress = MissionProgressEngine.isStandingOn(player, material, yLevel) ? definition.goal() : 0;
            }
            default -> {
                return state;
            }
        }

        return playerMissionService.updateAbsoluteProgress(state, scope, definition.id(), progress);
    }

    private void updateMatchingMissions(
        Player player,
        MissionType missionType,
        ProgressUpdateMode mode,
        int value,
        Material material,
        String text,
        String entityType
    ) {
        MissionPlayerState state = getOrCreateState(player);
        MissionPlayerState updated = state;
        updated = updateScope(player, updated, MissionScope.DAILY, missionType, mode, value, material, text, entityType);
        updated = updateScope(player, updated, MissionScope.WEEKLY, missionType, mode, value, material, text, entityType);
        cacheUpdated(updated);
    }

    public void recordOutboundProgress(
        Player player,
        MissionType type,
        String marker,
        double metric,
        String entityType
    ) {
        MissionPlayerState state = getOrCreateState(player);
        MissionPlayerState updated = updateOutboundScope(state, MissionScope.DAILY, type, marker, metric, entityType);
        updated = updateOutboundScope(updated, MissionScope.WEEKLY, type, marker, metric, entityType);
        cacheUpdated(updated);
    }

    public void flush(UUID playerId) {
        MissionPlayerState state = stateCache.remove(playerId);
        if (state != null && dirtyPlayers.remove(playerId)) storage.save(state);
    }

    private MissionPlayerState updateOutboundScope(
        MissionPlayerState state, MissionScope scope, MissionType type, String marker, double metric, String entityType
    ) {
        Map<String, MissionAssignment> assignments = scope == MissionScope.DAILY
            ? state.dailyAssignments() : state.weeklyAssignments();
        MissionPlayerState updated = state;
        for (MissionAssignment assignment : assignments.values()) {
            MissionDefinition definition = assignment.definition();
            if (definition.type() != type || !matchesOutbound(definition, marker, metric, entityType)) continue;
            if (type == MissionType.VISIT_BIOME_SET) {
                updated = playerMissionService.addProgressMarker(updated, scope, definition.id(), marker);
            } else if (type == MissionType.CATCH_FISH_IN_BIOME || type == MissionType.KILL_ENTITY_IN_BIOME) {
                updated = playerMissionService.updateAbsoluteProgress(
                    updated, scope, definition.id(), assignment.snapshot().progress() + 1);
            } else {
                updated = playerMissionService.updateAbsoluteProgress(updated, scope, definition.id(), definition.goal());
            }
        }
        return updated;
    }

    private boolean matchesOutbound(MissionDefinition definition, String marker, double metric, String entityType) {
        return switch (definition.type()) {
            case VISIT_BIOME, CATCH_FISH_IN_BIOME -> marker != null
                && marker.equalsIgnoreCase(String.valueOf(definition.params().get("biome")));
            case VISIT_BIOME_SET -> marker != null && ((List<?>) definition.params().getOrDefault("biomes", List.of()))
                .stream().map(String::valueOf).anyMatch(marker::equalsIgnoreCase);
            case REACH_DISTANCE_FROM_SPAWN -> metric >= Double.parseDouble(String.valueOf(definition.params().get("distance")));
            case ENTER_ENVIRONMENT -> marker != null
                && marker.equalsIgnoreCase(String.valueOf(definition.params().get("environment")));
            case OPEN_LOOT_TABLE -> marker != null
                && marker.endsWith(String.valueOf(definition.params().get("loot-table")));
            case KILL_ENTITY_IN_BIOME -> marker != null && entityType != null
                && marker.equalsIgnoreCase(String.valueOf(definition.params().get("biome")))
                && entityType.equalsIgnoreCase(String.valueOf(definition.params().get("entity")));
            default -> false;
        };
    }

    private void cacheUpdated(MissionPlayerState state) {
        stateCache.put(state.playerId(), state);
        dirtyPlayers.add(state.playerId());
    }

    private void flushDirtyStates() {
        for (UUID playerId : List.copyOf(dirtyPlayers)) {
            MissionPlayerState state = stateCache.get(playerId);
            if (state != null) storage.save(state);
            dirtyPlayers.remove(playerId);
        }
    }

    private MissionPlayerState updateScope(
        Player player,
        MissionPlayerState state,
        MissionScope scope,
        MissionType missionType,
        ProgressUpdateMode mode,
        int value,
        Material material,
        String text,
        String entityType
    ) {
        Map<String, MissionAssignment> assignments = scope == MissionScope.DAILY ? state.dailyAssignments() : state.weeklyAssignments();
        MissionPlayerState updated = state;

        for (MissionAssignment assignment : assignments.values()) {
            MissionDefinition definition = assignment.definition();
            if (definition.type() != missionType || !matchesCondition(player, definition, material, text, entityType)) {
                continue;
            }

            int nextProgress = switch (mode) {
                case INCREMENT -> assignment.snapshot().progress() + value;
                case CONDITIONAL_SET -> value;
            };
            updated = playerMissionService.updateAbsoluteProgress(updated, scope, definition.id(), nextProgress);
        }

        return updated;
    }

    private boolean matchesCondition(Player player, MissionDefinition definition, Material material, String text, String entityType) {
        return switch (definition.type()) {
            case FINAL_HIT_PLAYER_WITH_ITEM -> material != null
                && material.name().equalsIgnoreCase(String.valueOf(definition.params().getOrDefault("material", material.name())));
            case KILL_ENTITY_WITH_ITEM_ONLY -> material != null
                && material.name().equalsIgnoreCase(String.valueOf(definition.params().getOrDefault("material", material.name())))
                && entityType != null
                && entityType.equalsIgnoreCase(String.valueOf(definition.params().getOrDefault("entity", entityType)));
            case RENAME_ITEM_TO_EXACT_NAME -> material != null
                && material.name().equalsIgnoreCase(String.valueOf(definition.params().getOrDefault("material", material.name())))
                && text != null
                && ChatColor.stripColor(text).trim().equals(String.valueOf(definition.params().getOrDefault("name", text)).trim());
            case NAME_ENTITY_AFTER_PLAYER -> {
                if (text == null) {
                    yield false;
                }
                String name = ChatColor.stripColor(text).trim();
                String expectedEntity = String.valueOf(definition.params().getOrDefault("entity", "PIG"));
                boolean entityMatches = entityType == null || entityType.equalsIgnoreCase(expectedEntity);
                boolean otherPlayerOnly = Boolean.parseBoolean(String.valueOf(definition.params().getOrDefault("other-player", false)));
                if (name.equalsIgnoreCase(player.getName())) {
                    yield entityMatches && !otherPlayerOnly;
                }
                // getOfflinePlayerIfCached never blocks on a profile lookup, unlike getOfflinePlayer(String).
                var known = Bukkit.getOfflinePlayerIfCached(name);
                boolean playerNameMatches = Bukkit.getPlayerExact(name) != null
                    || known != null && known.hasPlayedBefore();
                yield entityMatches && playerNameMatches;
            }
            default -> true;
        };
    }

    private void appendClaimables(List<MissionAssignment> claimables, Map<String, MissionAssignment> assignments) {
        for (MissionAssignment assignment : assignments.values()) {
            if (assignment.snapshot().completed() && !assignment.snapshot().claimed()) {
                claimables.add(assignment);
            }
        }
    }

    private void clearRuntimeState() {
        if (storage != null) flushDirtyStates();
        stateCache.clear();
        dirtyPlayers.clear();
        enabled = false;
        hudController = null;
        playerMissionService = null;
        economyGateway = null;
        storage = null;
        catalog = null;
    }

    private Instant nextDailyReset() {
        int hour = plugin.getConfig().getInt("mission-system.reset.daily-hour", 0);
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next.atZone(zone).toInstant();
    }

    private Instant nextWeeklyReset() {
        String configuredDay = plugin.getConfig().getString("mission-system.reset.weekly-day", "MONDAY");
        int hour = plugin.getConfig().getInt("mission-system.reset.weekly-hour", 0);
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(configuredDay.toUpperCase(Locale.ROOT));
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime next = now.with(TemporalAdjusters.nextOrSame(dayOfWeek)).withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusWeeks(1);
        }
        return next.atZone(zone).toInstant();
    }

    private enum ProgressUpdateMode {
        INCREMENT,
        CONDITIONAL_SET
    }
}
