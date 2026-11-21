package dev.linqfy.bigCasares.modules.resourcepack;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.alexscaves.AlexsCavesPackLayer;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import org.bukkit.event.HandlerList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.net.URI;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;
import kr.toxicity.model.api.event.ModelEventListener;
import kr.toxicity.model.api.event.PluginEndReloadEvent;

public final class ResourcePackModule implements PluginModule {
    private final BigCasares plugin;
    private final ClientPlatformGateway platformGateway;
    private ResourcePackService service;
    private ResourcePackStatusListener listener;
    private RuntimeRegistrationScope compatibilityScope;
    private PackArtifactPublisher artifactPublisher;
    private PackReloadCoordinator packReloadCoordinator;
    private ExecutorService packWorker;
    private Executor commitExecutor;
    private EmbeddedPackHttpServer httpServer;
    private ResourcePackSettings settings;
    private Path contentPackRoot;
    private Path packsRoot;
    private List<JavaPackDelivery> betterModelPacks = List.of();
    private ModelEventListener betterModelReloadListener;
    private boolean suppressBetterModelHook;

    public ResourcePackModule(BigCasares plugin) {
        this(plugin, ignored -> ClientPlatform.JAVA);
    }

    public ResourcePackModule(BigCasares plugin, ClientPlatformGateway platformGateway) {
        this.plugin = plugin;
        this.platformGateway = platformGateway == null ? ignored -> ClientPlatform.JAVA : platformGateway;
    }

    @Override
    public String getId() {
        return "resource-pack-system";
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
            return;
        }
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        scope.register("module-state", this::clearRuntimeState);
        this.settings = ResourcePackSettings.load(plugin.getConfig());
        this.contentPackRoot = plugin.getDataFolder().toPath().resolve("content/pack");
        this.packsRoot = plugin.getDataFolder().toPath().resolve("packs");
        seedRuntimeContent();
        this.artifactPublisher = new PackArtifactPublisher(packsRoot);
        startHttpServer(scope);
        ActivePackManifest active = buildOrReuseActivePack();
        this.betterModelPacks = reloadAndPublishBetterModel();
        this.service = new ResourcePackService(
            settings,
            active.deliveryManifest(),
            new BukkitResourcePackGateway(plugin.getServer()),
            platformGateway
        );
        service.activateStack(active.deliveryManifest(), currentJavaUri(active), betterModelPacks);
        this.listener = new ResourcePackStatusListener(plugin, service, registrations);
        registrations.registerListener("resource-pack-status-listener", listener);
        this.packReloadCoordinator = new PackReloadCoordinator();
        this.packWorker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bigcasares-pack-builder");
            thread.setDaemon(true);
            return thread;
        });
        this.commitExecutor = runnable -> registrations.scheduleImmediate("pack-reload-commit", runnable);
        subscribeToBetterModelReloads(scope);
        scope.register("pack-reload-runtime", this::shutdownPackRuntime);
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        clearRuntimeState();
    }

    public Optional<ResourcePackService> service() {
        return Optional.ofNullable(service);
    }

    public CompletableFuture<PackReloadResult> reloadPack() {
        PackReloadCoordinator coordinator = packReloadCoordinator;
        ExecutorService worker = packWorker;
        Executor mainThread = commitExecutor;
        if (coordinator == null || worker == null || mainThread == null) {
            return CompletableFuture.completedFuture(new PackReloadResult(
                UUID.randomUUID(), PackReloadStatus.CANCELLED, null, java.time.Duration.ZERO,
                activeManifest().orElse(null), null, List.of(), null));
        }
        try {
            this.betterModelPacks = reloadAndPublishBetterModel();
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(new PackReloadResult(
                UUID.randomUUID(), PackReloadStatus.FAILED, PackReloadPhase.BUILD,
                java.time.Duration.ZERO, activeManifest().orElse(null), null, List.of(), failure
            ));
        }
        CompletableFuture<PackReloadResult> result = coordinator.execute(new PackReloadOperation() {
            @Override
            public Optional<ActivePackManifest> activeManifest() throws Exception {
                return artifactPublisher.activeManifest();
            }

            @Override
            public PackPublication prepare(UUID jobId) throws Exception {
                try (StagedPackCandidate candidate = new StagedPackBuilder(
                    contentPackRoot, packsRoot, javaLayers()).build(jobId)) {
                    try {
                        return artifactPublisher.prepare(candidate, ResourcePackModule.this::javaArtifactUri);
                    } catch (Throwable failure) {
                        throw new PackReloadException(PackReloadPhase.PUBLICATION, failure);
                    }
                }
            }

            @Override
            public void commit(PackPublication publication) throws Exception {
                artifactPublisher.activate(publication);
                ActivePackManifest manifest = publication.manifest();
                service.activateStack(manifest.deliveryManifest(), currentJavaUri(manifest), betterModelPacks);
                if (publication.changed()) {
                    resendChangedPackToAll();
                }
            }
        }, worker, mainThread);
        return result.thenApplyAsync(completed -> completed, mainThread).whenComplete((completed, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Pack reload completion failed", failure);
            } else if (completed.status() == PackReloadStatus.FAILED) {
                plugin.getLogger().log(Level.SEVERE,
                    "Pack reload failed during " + completed.failurePhase(), completed.failure());
            }
        });
    }

    public Optional<ActivePackManifest> activeManifest() {
        try {
            return artifactPublisher == null ? Optional.empty() : artifactPublisher.activeManifest();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "No se pudo leer active-pack.json", exception);
            return Optional.empty();
        }
    }

    public boolean sendPack(UUID playerId) {
        return service != null && service.forceRequestFor(playerId);
    }

    public List<JavaPackDelivery> enableManualDownloads(Player player) {
        if (service == null || player == null) {
            return List.of();
        }
        String revision = service.currentRevision();
        var desired = ManualResourcePackTracker.tagsFor(player.getScoreboardTags(), revision);
        for (String tag : List.copyOf(player.getScoreboardTags())) {
            if (ManualResourcePackTracker.isManagedTag(tag) && !desired.contains(tag)) {
                player.removeScoreboardTag(tag);
            }
        }
        desired.stream().filter(ManualResourcePackTracker::isManagedTag)
            .forEach(player::addScoreboardTag);
        return service.currentDownloads();
    }

    public boolean isManualPlayer(Player player) {
        return player != null && ManualResourcePackTracker.isManual(player.getScoreboardTags());
    }

    public boolean notifyManualUpdate(Player player) {
        if (service == null || player == null
            || !ManualResourcePackTracker.needsUpdate(player.getScoreboardTags(), service.currentRevision())) {
            return false;
        }
        player.sendMessage(ChatColor.YELLOW + "El resource pack fue actualizado. Ejecutá "
            + ChatColor.AQUA + "/resourcepack" + ChatColor.YELLOW
            + " para descargar la versión nueva.");
        return true;
    }

    public int resendToAll() {
        if (service == null) {
            return 0;
        }
        int sent = 0;
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            if (service.forceRequestFor(player.getUniqueId())) {
                sent++;
            }
        }
        return sent;
    }

    public boolean isPackReloadRunning() {
        return packReloadCoordinator != null && packReloadCoordinator.isRunning();
    }

    private void seedRuntimeContent() {
        try (var seed = plugin.getResource("seed-content/bigcasares-content-seed.zip")) {
            if (seed == null) {
                throw new IllegalStateException("Missing packaged runtime content seed");
            }
            RuntimePackSeedResult result = new RuntimePackSourceSeeder().seed(seed, contentPackRoot);
            if (result.copiedFiles() > 0) {
                plugin.getLogger().info("Se inicializaron " + result.copiedFiles() + " archivos de contenido del pack.");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not seed runtime pack content", exception);
        }
    }

    private ActivePackManifest buildOrReuseActivePack() {
        try (StagedPackCandidate candidate = new StagedPackBuilder(
            contentPackRoot, packsRoot, javaLayers()).build(UUID.randomUUID())) {
            return artifactPublisher.publish(candidate, this::javaArtifactUri).manifest();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not build or reuse runtime pack", exception);
        }
    }

    private void startHttpServer(RuntimeRegistrationScope scope) {
        if (settings.publisher().mode() != ResourcePackPublisher.Mode.EMBEDDED_HTTP) {
            return;
        }
        if (settings.embeddedHttp().publicBaseUri() == null) {
            throw new IllegalArgumentException("embedded-http publishing requires public-url");
        }
        this.httpServer = new EmbeddedPackHttpServer(
            packsRoot.resolve("artifacts"), settings.embeddedHttp());
        try {
            httpServer.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start embedded resource-pack HTTP server", exception);
        }
        scope.register("embedded-pack-http", httpServer::close);
    }

    private URI javaArtifactUri(String artifactFile) {
        return switch (settings.publisher().mode()) {
            case COPY_ONLY -> null;
            case EXTERNAL_URL -> resolveExternalArtifactUri(artifactFile, false);
            case EMBEDDED_HTTP -> httpServer.publicUri(artifactFile);
        };
    }

    private URI currentJavaUri(ActivePackManifest active) {
        return PackDeliveryUriResolver.resolve(active, this::javaArtifactUri);
    }

    /**
     * Optional layers never fail the pack: a broken Alex's Caves layer only means module blocks
     * look like note blocks, while an exception here would roll back every module at startup.
     */
    private List<JavaPackLayerProvider> javaLayers() {
        try {
            return AlexsCavesPackLayer.provide(plugin, packsRoot.resolve("layers")).map(List::of).orElseGet(List::of);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not build the Alex's Caves pack layer; building the pack without it",
                exception);
            return List.of();
        }
    }

    private List<JavaPackDelivery> reloadAndPublishBetterModel() {
        suppressBetterModelHook = true;
        try {
            BetterModelPlatform.ReloadResult result = BetterModel.platform().reload();
            if (!(result instanceof BetterModelPlatform.ReloadResult.Success success)) {
                throw new IllegalStateException("BetterModel reload failed: " + result);
            }
            return publishBetterModelDirectory(success.packResult().directory().toPath());
        } finally {
            suppressBetterModelHook = false;
        }
    }

    private List<JavaPackDelivery> publishBetterModelDirectory(Path directory) {
        try {
            return artifactPublisher.publishBetterModel(directory, this::betterModelArtifactUri)
                .map(List::of).orElseGet(List::of);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not publish BetterModel resource pack", exception);
        }
    }

    private URI betterModelArtifactUri(String artifactFile) {
        return switch (settings.publisher().mode()) {
            case COPY_ONLY -> null;
            case EMBEDDED_HTTP -> httpServer.publicUri(artifactFile);
            case EXTERNAL_URL -> {
                yield resolveExternalArtifactUri(artifactFile, true);
            }
        };
    }

    private URI resolveExternalArtifactUri(String artifactFile, boolean betterModel) {
        if (betterModel) {
            String explicit = plugin.getConfig().getString(
                "resource-pack-system.publishing.bettermodel-public-url", "").trim();
            if (!explicit.isEmpty()) return URI.create(explicit);
        }
        String configuredBase = plugin.getConfig().getString(
            "resource-pack-system.publishing.public-base-url", "").trim();
        if (configuredBase.isEmpty()) return settings.publisher().publicUri();
        return URI.create(configuredBase.endsWith("/") ? configuredBase + artifactFile : configuredBase + "/" + artifactFile);
    }

    private void subscribeToBetterModelReloads(RuntimeRegistrationScope scope) {
        betterModelReloadListener = BetterModel.eventBus().subscribe(
            BetterModel.platform(), PluginEndReloadEvent.class, event -> {
                if (suppressBetterModelHook || !(event.result() instanceof BetterModelPlatform.ReloadResult.Success success)) {
                    return;
                }
                CompletableFuture.supplyAsync(
                    () -> publishBetterModelDirectory(success.packResult().directory().toPath()), packWorker
                ).thenAcceptAsync(packs -> {
                    betterModelPacks = packs;
                    activeManifest().ifPresent(active -> service.activateStack(
                        active.deliveryManifest(), currentJavaUri(active), betterModelPacks));
                    resendChangedPackToAll();
                }, commitExecutor).exceptionally(failure -> {
                    plugin.getLogger().log(Level.SEVERE, "BetterModel pack republish failed", failure);
                    return null;
                });
            }
        );
        scope.register("bettermodel-reload-listener", () -> {
            if (betterModelReloadListener != null) betterModelReloadListener.unregister();
            betterModelReloadListener = null;
        });
    }

    private void shutdownPackRuntime() {
        if (packReloadCoordinator != null) {
            packReloadCoordinator.retire();
        }
        if (packWorker != null) {
            packWorker.shutdownNow();
        }
        if (httpServer != null) {
            httpServer.close();
        }
    }

    private int resendChangedPackToAll() {
        if (service == null || !settings.resendOnVersionChange()) {
            return 0;
        }
        int sent = 0;
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            if (isManualPlayer(player)) {
                notifyManualUpdate(player);
                continue;
            }
            if (service.requestFor(player.getUniqueId())) {
                sent++;
            }
        }
        return sent;
    }

    private void clearRuntimeState() {
        shutdownPackRuntime();
        listener = null;
        service = null;
        artifactPublisher = null;
        packReloadCoordinator = null;
        packWorker = null;
        commitExecutor = null;
        httpServer = null;
        settings = null;
        contentPackRoot = null;
        packsRoot = null;
        betterModelPacks = List.of();
        betterModelReloadListener = null;
        suppressBetterModelHook = false;
    }
}
