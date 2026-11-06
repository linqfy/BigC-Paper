package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
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

public final class MissionModule implements PluginModule {

    private final BigCasares plugin;

    private MissionCatalog catalog;
    private MissionStorage storage;
    private PlayerMissionService playerMissionService;
    private VaultEconomyGateway economyGateway;
    private MissionHudController hudController;
    private MissionTideframeMenu tideframeMenu;
    private java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> tideframeNavigation =
        () -> dev.linqfy.bigCasares.modules.tideframe.TideframeUi.NONE;
    private java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> tideframe =
        () -> dev.linqfy.bigCasares.modules.tideframe.TideframeUi.NONE;
    private ClientPlatformGateway platforms = ignored -> ClientPlatform.JAVA;
    private MissionBedrockForms bedrockForms;
    /** Changes on every enable and disable, so an answer to a form sent before a reload is ignored. */
    private long bedrockGeneration;
    private boolean enabled;
    private RuntimeRegistrationScope compatibilityScope;
    private final Map<UUID, MissionPlayerState> stateCache = new LinkedHashMap<>();
    private final Set<UUID> dirtyPlayers = new LinkedHashSet<>();
    private final MissionReadyNotices readyNotices = new MissionReadyNotices();
    private final MissionRecords records;
    private BukkitRuntimeRegistrations registrations;
    private volatile MissionPlaceholderValues placeholderValues;
    private Object placeholderExpansion;
    /** When each Bedrock player first tapped "Cambiar las diarias" ({@link System#nanoTime()}). */
    private final Map<UUID, Long> bedrockRerollTaps = new java.util.concurrent.ConcurrentHashMap<>();

    public MissionModule(BigCasares plugin) {
        this.plugin = plugin;
        this.records = new MissionRecords(dev.linqfy.bigCasares.platform.PlayerStrings.of(plugin));
    }

    /** The day of the current daily period (it starts at the configured reset hour). */
    private java.time.LocalDate today() {
        int hour = plugin.getConfig().getInt("mission-system.reset.daily-hour", 0);
        ZoneId zone = ZoneId.systemDefault();
        return MissionReroll.periodStart(Instant.now(), hour, zone).atZone(zone).toLocalDate();
    }

    /** The extra line of a missions tab: the streak on the daily one, this week's claimed money on the weekly one. */
    String summary(Player player, MissionScope scope) {
        if (scope == MissionScope.DAILY) {
            int days = records.streak(player, today());
            return days == 0 ? "" : "racha " + days + (days == 1 ? " día" : " días");
        }
        double total = records.weekTotal(player, getOrCreateState(player).weeklyResetsAt());
        return total <= 0 || economyGateway == null ? "" : "esta semana " + MissionTideframeMenu.plain(economyGateway.format(total));
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
        // Java players with Tideframe see the lists on its screen; its Reclamar uses the same claim as the inventory menu.
        MissionTideframeMenu menu = new MissionTideframeMenu(this::getOrCreateState, this::countClaimables, economyGateway::format,
            () -> tideframe.get(), this::claimAll, this::rerollCheck, this::rerollDaily, System::nanoTime, this::summary);
        this.tideframeMenu = menu;

        scope.register("module-state", this::clearRuntimeState);
        scope.register("tideframe-menu", menu::close);
        scope.register("hud-open-views", hudController::closeOpenViews);
        registrations.registerListener("mission-listener", new MissionListener(this));
        registrations.registerListener("hud-listener", hudController);
        registrations.scheduleRepeating("mission-state-flush", this::flushDirtyStates, 600L, 600L);
        this.registrations = registrations;
        registerPlaceholders(scope, registrations);
        bedrockGeneration++;
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
        MissionScope scope = view == MissionHudView.WEEKLY_LIST ? MissionScope.WEEKLY : MissionScope.DAILY;
        // Bedrock players get the lists as a form with the same look, Reclamar included.
        if (view != MissionHudView.CLAIMABLES && openBedrock(player, scope)) {
            return;
        }
        // The lists open on Tideframe when the player can see it; claiming, and every other case, uses the inventory.
        MissionTideframeMenu menu = tideframeMenu;
        if (view != MissionHudView.CLAIMABLES && menu != null
            && menu.open(player, scope, () -> openClassicHud(player, view))) {
            return;
        }
        openClassicHud(player, view);
    }

    /** Bedrock: who is on Bedrock, and how to send them a form (Geyser). Without it they keep the inventory menu. */
    public void configureBedrock(ClientPlatformGateway platforms, MissionBedrockForms forms) {
        this.platforms = platforms == null ? ignored -> ClientPlatform.JAVA : platforms;
        this.bedrockForms = forms;
    }

    private boolean openBedrock(Player player, MissionScope scope) {
        MissionBedrockForms forms = bedrockForms;
        UUID playerId = player.getUniqueId();
        if (forms == null || economyGateway == null || platforms.resolvePlatform(playerId) != ClientPlatform.BEDROCK) {
            return false;
        }
        MissionPlayerState state = getOrCreateState(player);
        Long firstTap = bedrockRerollTaps.get(playerId);
        boolean confirming = firstTap != null && System.nanoTime() - firstTap <= MissionReroll.CONFIRM_NANOS;
        MissionBedrockForm.Page page = MissionBedrockForm.build(state, scope, countClaimables(state),
            economyGateway::format, forms.glyphs(), Instant.now(), rerollCheck(player), confirming, summary(player, scope));
        long generation = bedrockGeneration;
        return forms.send(playerId, page.title(), page.content(), page.texts(),
            index -> answerBedrock(playerId, generation, scope, page, index));
    }

    /** A button of the Bedrock form (on the server thread): tabs and missions show the list again, Reclamar pays and redraws. */
    private void answerBedrock(UUID playerId, long generation, MissionScope scope, MissionBedrockForm.Page page, int index) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (!enabled || generation != bedrockGeneration || player == null || !player.isOnline()
            || index < 0 || index >= page.buttons().size()) {
            return;
        }
        switch (page.buttons().get(index).kind()) {
            case DAILY -> openHud(player, MissionHudView.DAILY_LIST);
            case WEEKLY -> openHud(player, MissionHudView.WEEKLY_LIST);
            case MISSION -> openHud(player, scope == MissionScope.WEEKLY ? MissionHudView.WEEKLY_LIST : MissionHudView.DAILY_LIST);
            case CLAIM -> {
                claimAll(player);
                openHud(player, scope == MissionScope.WEEKLY ? MissionHudView.WEEKLY_LIST : MissionHudView.DAILY_LIST);
            }
            case REROLL -> {
                // The first tap asks to confirm (the form shows again saying so); a second one within seconds changes them.
                Long first = bedrockRerollTaps.remove(playerId);
                if (first != null && System.nanoTime() - first <= MissionReroll.CONFIRM_NANOS) {
                    rerollDaily(player);
                } else {
                    bedrockRerollTaps.put(playerId, System.nanoTime());
                }
                openHud(player, MissionHudView.DAILY_LIST);
            }
        }
    }

    /** The inventory menu, without Tideframe (its claim button, and the fallback when Tideframe cannot open). */
    public void openClassicHud(Player player, MissionHudView view) {
        if (!enabled || hudController == null) {
            return;
        }
        tideframeNavigation.get().openClassic(player, () -> hudController.open(player, view));
    }

    public void configureTideframeNavigation(java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> navigation) {
        this.tideframeNavigation = navigation;
    }

    /** Where the Tideframe screens come from (the Tideframe module, or none). */
    public void configureTideframe(java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> tideframe) {
        this.tideframe = tideframe == null ? () -> dev.linqfy.bigCasares.modules.tideframe.TideframeUi.NONE : tideframe;
    }

    public void claimAll(Player player) {
        MissionPlayerState state = getOrCreateState(player);
        MissionPlayerState claimed = playerMissionService.claimAllCompleted(player.getUniqueId(), state, economyGateway);
        storage.save(claimed);
        stateCache.put(player.getUniqueId(), claimed);
        dirtyPlayers.remove(player.getUniqueId());
        double amount = sumClaimables(state);
        // What really changed from ready to claimed: the base of the bonus, this week's total, and the streak once
        // every daily is claimed (the bonus counts the streak before today's).
        double paid = MissionOrder.pendingReward(state) - MissionOrder.pendingReward(claimed);
        java.time.LocalDate day = today();
        dev.linqfy.bigCasares.platform.HappyHour happy = dev.linqfy.bigCasares.platform.HappyHour.load(plugin.getConfig());
        double rate = MissionBonus.load(plugin.getConfig()).rate(records.streak(player, day),
            happy.active(java.time.ZonedDateTime.now()) ? happy.missionMultiplier() : 1);
        double bonus = paid > 0 ? Math.round(paid * rate * 100) / 100.0 : 0;
        if (bonus > 0) {
            try {
                economyGateway.deposit(player.getUniqueId(), bonus);
            } catch (IllegalStateException refused) {
                // The rewards themselves are already paid and saved; only the extra is lost, and the log says why.
                plugin.getLogger().warning("No se pudo pagar el bono de misiones a " + player.getName() + ": " + refused.getMessage());
                bonus = 0;
            }
        }
        records.addClaimed(player, claimed.weeklyResetsAt(), paid + bonus);
        if (!claimed.dailyAssignments().isEmpty()
            && claimed.dailyAssignments().values().stream().allMatch(assignment -> assignment.snapshot().claimed())) {
            records.dailiesDone(player, day);
        }
        if (amount > 0.0) {
            dev.linqfy.bigCasares.platform.FeedbackSounds.reward(player);
            player.sendMessage("§aReclamaste " + economyGateway.format(amount) + " en recompensas."
                + (bonus > 0 ? " §6+" + economyGateway.format(bonus) + " de bono." : ""));
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

    /** Whether {@code player} may change their daily missions now (see {@link MissionReroll}). */
    MissionReroll.Check rerollCheck(Player player) {
        int hour = plugin.getConfig().getInt("mission-system.reset.daily-hour", 0);
        return MissionReroll.check(getOrCreateState(player), MissionReroll.periodStart(Instant.now(), hour, ZoneId.systemDefault()));
    }

    public void rerollDaily(Player player) {
        MissionPlayerState state = getOrCreateState(player);
        MissionReroll.Check check = rerollCheck(player);
        if (check != MissionReroll.Check.ALLOWED) {
            player.sendMessage(MissionReroll.refusal(check));
            return;
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

    public void completePigNameMission(Player player, String customName) {
        updateMatchingMissions(player, MissionType.NAME_ENTITY_AFTER_PLAYER, ProgressUpdateMode.INCREMENT, 1, null, customName, "PIG");
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
        // Passive missions (hold, wear, stand on) complete only here.
        announceReady(player, state, updated);
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
        announceReady(player, state, updated);
    }

    /**
     * A moment after joining (so the join messages do not bury it), tells the player what they have to claim, if
     * anything.
     */
    void greet(Player player) {
        BukkitRuntimeRegistrations active = registrations;
        if (!enabled || active == null) {
            return;
        }
        active.scheduleDelayed("mission-greeting", () -> {
            if (!enabled || !player.isOnline() || economyGateway == null) {
                return;
            }
            MissionPlayerState state = getOrCreateState(player);
            MissionReadyNotices.greeting(countClaimables(state), MissionOrder.pendingReward(state), economyGateway::format)
                .ifPresent(player::sendMessage);
        }, GREETING_DELAY_TICKS);
    }

    /**
     * {@code %bigc_...%} for scoreboards, when PlaceholderAPI is installed: the expansion reads snapshots this module
     * refreshes every two seconds on the server thread.
     */
    private void registerPlaceholders(RuntimeRegistrationScope scope, BukkitRuntimeRegistrations registrations) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        MissionPlaceholderValues values = new MissionPlaceholderValues(playerId -> {
            var shop = plugin.getShopModule();
            return shop == null ? Optional.empty() : shop.history().latest(playerId);
        });
        try {
            Class<?> type = Class.forName("dev.linqfy.bigCasares.modules.missions.BigcPlaceholderExpansion", true,
                plugin.getClass().getClassLoader());
            Object expansion = type.getConstructor(MissionPlaceholderValues.class).newInstance(values);
            if ((boolean) type.getMethod("register").invoke(expansion)) {
                placeholderValues = values;
                placeholderExpansion = expansion;
                scope.register("bigc-placeholders", this::unregisterPlaceholders);
                registrations.scheduleRepeating("bigc-placeholder-refresh", this::refreshPlaceholders, 40L, 40L);
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                "PlaceholderAPI está instalado pero los placeholders %bigc_...% no pudieron registrarse.", ex);
        }
    }

    private void refreshPlaceholders() {
        MissionPlaceholderValues values = placeholderValues;
        if (values == null || economyGateway == null) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            MissionPlayerState state = stateCache.get(player.getUniqueId());
            if (state != null) {
                values.update(player.getUniqueId(), countClaimables(state),
                    MissionTideframeMenu.plain(economyGateway.format(MissionOrder.pendingReward(state))));
            }
        }
    }

    private void unregisterPlaceholders() {
        Object expansion = placeholderExpansion;
        placeholderExpansion = null;
        placeholderValues = null;
        if (expansion != null) {
            try {
                expansion.getClass().getMethod("unregister").invoke(expansion);
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().log(java.util.logging.Level.FINE, "No se pudieron desregistrar los placeholders %bigc_...%", ex);
            }
        }
    }

    /** Three seconds after joining. */
    static final long GREETING_DELAY_TICKS = 60L;

    /** Tells the player, once per mission and period, that it just became ready to claim (Java and Bedrock, in chat). */
    private void announceReady(Player player, MissionPlayerState before, MissionPlayerState after) {
        for (MissionDefinition ready : readyNotices.toAnnounce(before, after)) {
            player.sendMessage(MissionReadyNotices.message(ready, economyGateway::format));
        }
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
        announceReady(player, state, updated);
    }

    public void flush(UUID playerId) {
        readyNotices.forget(playerId);
        MissionPlaceholderValues values = placeholderValues;
        if (values != null) {
            values.forget(playerId);
        }
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
                && text.equals(definition.params().getOrDefault("name", text));
            case NAME_ENTITY_AFTER_PLAYER -> {
                if (text == null) {
                    yield false;
                }
                String expectedEntity = String.valueOf(definition.params().getOrDefault("entity", "PIG"));
                boolean entityMatches = entityType == null || entityType.equalsIgnoreCase(expectedEntity);
                boolean playerNameMatches = Bukkit.getOfflinePlayer(text).hasPlayedBefore()
                    || Bukkit.getPlayerExact(text) != null
                    || text.equalsIgnoreCase(player.getName());
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
        readyNotices.clear();
        bedrockRerollTaps.clear();
        registrations = null;
        enabled = false;
        bedrockGeneration++;
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
