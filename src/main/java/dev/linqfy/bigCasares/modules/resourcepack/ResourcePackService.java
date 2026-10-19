package dev.linqfy.bigCasares.modules.resourcepack;

import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;

import java.net.URI;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ResourcePackService {
    private final ResourcePackSettings settings;
    private final ResourcePackGateway gateway;
    private final ClientPlatformGateway platformGateway;
    private final Map<UUID, Map<UUID, ResourcePackPlayerState>> states = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> retryAttempts = new ConcurrentHashMap<>();
    private final AtomicReference<Delivery> delivery;
    private volatile Set<UUID> previousPackIds = Set.of();

    public ResourcePackService(
        ResourcePackSettings settings,
        ResourcePackManifest manifest,
        ResourcePackGateway gateway,
        ClientPlatformGateway platformGateway
    ) {
        this.settings = settings;
        this.gateway = gateway;
        this.platformGateway = platformGateway;
        this.delivery = new AtomicReference<>(new Delivery(
            manifest,
            singleDelivery(manifest, settings.publisher().javaPackUri().orElse(null))
        ));
    }

    public boolean requestFor(UUID playerId) {
        retryAttempts.remove(playerId);
        return requestFor(playerId, false);
    }

    public boolean forceRequestFor(UUID playerId) {
        retryAttempts.remove(playerId);
        return requestFor(playerId, true);
    }

    public boolean retryFailedFor(UUID playerId) {
        if (state(playerId) != ResourcePackPlayerState.FAILED) return false;
        int attempt = retryAttempts.computeIfAbsent(playerId, ignored -> new AtomicInteger()).incrementAndGet();
        if (attempt > 2) return false;
        return requestFor(playerId, true);
    }

    private boolean requestFor(UUID playerId, boolean force) {
        if (platformGateway.resolvePlatform(playerId) == ClientPlatform.BEDROCK) {
            return false;
        }
        Delivery currentDelivery = delivery.get();
        if (currentDelivery.packs().isEmpty()) {
            return false;
        }
        ResourcePackPlayerState current = state(playerId);
        if (!force && !settings.resendOnVersionChange()
            && current != ResourcePackPlayerState.NOT_REQUESTED) {
            return false;
        }
        ResourcePackManifest manifest = currentDelivery.manifest();
        Set<UUID> owned = new LinkedHashSet<>(previousPackIds);
        currentDelivery.packs().forEach(pack -> owned.add(pack.id()));
        owned.forEach(packId -> gateway.removeJavaPack(playerId, packId));
        Map<UUID, ResourcePackPlayerState> packStates = new ConcurrentHashMap<>();
        for (int index = 0; index < currentDelivery.packs().size(); index++) {
            JavaPackDelivery pack = currentDelivery.packs().get(index);
            gateway.requestJavaPack(playerId, pack.id(), pack.uri(), pack.sha1(),
                index == 0 ? settings.promptMessage() : "", settings.required());
            packStates.put(pack.id(), ResourcePackPlayerState.SENT);
        }
        states.put(playerId, packStates);
        return true;
    }

    public void updateState(UUID playerId, ResourcePackPlayerState state) {
        delivery.get().packs().forEach(pack -> states.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
            .put(pack.id(), state));
    }

    public boolean updateState(UUID packId, UUID playerId, ResourcePackPlayerState state) {
        if (delivery.get().packs().stream().noneMatch(pack -> pack.id().equals(packId))) {
            return false;
        }
        states.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).put(packId, state);
        if (state(playerId) == ResourcePackPlayerState.LOADED) retryAttempts.remove(playerId);
        return true;
    }

    public ResourcePackPlayerState state(UUID playerId) {
        Map<UUID, ResourcePackPlayerState> playerStates = states.get(playerId);
        if (playerStates == null || playerStates.isEmpty()) return ResourcePackPlayerState.NOT_REQUESTED;
        if (playerStates.values().stream().allMatch(state -> state == ResourcePackPlayerState.LOADED)) return ResourcePackPlayerState.LOADED;
        if (playerStates.values().stream().anyMatch(state -> state == ResourcePackPlayerState.DECLINED)) return ResourcePackPlayerState.DECLINED;
        if (playerStates.values().stream().anyMatch(state -> state == ResourcePackPlayerState.FAILED)) return ResourcePackPlayerState.FAILED;
        if (playerStates.values().stream().anyMatch(state -> state == ResourcePackPlayerState.DISCARDED)) return ResourcePackPlayerState.DISCARDED;
        if (playerStates.values().stream().anyMatch(state -> state == ResourcePackPlayerState.ACCEPTED)) return ResourcePackPlayerState.ACCEPTED;
        return ResourcePackPlayerState.SENT;
    }

    public boolean hasLoadedPack(UUID playerId) {
        return state(playerId) == ResourcePackPlayerState.LOADED;
    }

    public boolean isRequired() {
        return settings.required();
    }

    /**
     * Whether a required Java pack is still missing for the player, so the join timeout may kick them. A Bedrock
     * player is never sent the Java pack (Geyser gives them the Bedrock pack), so they never owe it; before this
     * check every Geyser player was kicked about 20 seconds after joining a server with a required pack.
     */
    public boolean owesRequiredPack(UUID playerId) {
        if (!settings.required() || platformGateway.resolvePlatform(playerId) == ClientPlatform.BEDROCK) {
            return false;
        }
        ResourcePackPlayerState state = state(playerId);
        return state == ResourcePackPlayerState.SENT || state == ResourcePackPlayerState.NOT_REQUESTED;
    }

    public boolean isBigCasaresPack(UUID packId) {
        return delivery.get().packs().stream().anyMatch(pack -> pack.id().equals(packId)) || previousPackIds.contains(packId);
    }

    public void activate(ResourcePackManifest manifest, URI uri) {
        activateStack(manifest, uri, List.of());
    }

    public void activateStack(ResourcePackManifest manifest, URI bigCasaresUri, List<JavaPackDelivery> precedingPacks) {
        List<JavaPackDelivery> packs = new java.util.ArrayList<>(precedingPacks);
        packs.addAll(singleDelivery(manifest, bigCasaresUri));
        Delivery previous = delivery.getAndSet(new Delivery(manifest, List.copyOf(packs)));
        previousPackIds = previous.packs().stream().map(JavaPackDelivery::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public ResourcePackManifest currentManifest() {
        return delivery.get().manifest();
    }

    public Optional<URI> currentUri() {
        return delivery.get().packs().stream().filter(pack -> pack.owner().equals("bigcasares")).map(JavaPackDelivery::uri).findFirst();
    }

    public List<JavaPackDelivery> currentDownloads() {
        return delivery.get().packs();
    }

    public String currentRevision() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (JavaPackDelivery pack : delivery.get().packs()) {
                digest.update(pack.id().toString().getBytes(StandardCharsets.UTF_8));
                digest.update(pack.sha1());
            }
            return HexFormat.of().formatHex(digest.digest(), 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public void forget(UUID playerId) {
        states.remove(playerId);
        retryAttempts.remove(playerId);
    }

    private static List<JavaPackDelivery> singleDelivery(ResourcePackManifest manifest, URI uri) {
        if (uri == null) return List.of();
        return List.of(new JavaPackDelivery(
            manifest.javaUuid(), uri, HexFormat.of().parseHex(manifest.javaSha1()), "bigcasares"
        ));
    }

    private record Delivery(ResourcePackManifest manifest, List<JavaPackDelivery> packs) {
    }
}
