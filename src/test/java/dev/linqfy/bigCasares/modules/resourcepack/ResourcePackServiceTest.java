package dev.linqfy.bigCasares.modules.resourcepack;

import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackServiceTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000071");
    private static final UUID BEDROCK_PACK_ID = UUID.fromString("7e1cc35b-7a9f-4af4-b930-93f936f9cb64");

    @Test
    void javaPackIdIsStableForTheManifestVersionAndChangesWithIt() {
        ResourcePackManifest first = manifest("075a3f4dec4b");
        ResourcePackManifest sameVersion = manifest("075a3f4dec4b");
        ResourcePackManifest nextVersion = manifest("175a3f4dec4b");

        assertEquals(first.javaUuid(), sameVersion.javaUuid());
        assertEquals(UUID.fromString("ee96c3af-3ab9-363d-9ca3-903cf0bbe165"), first.javaUuid());
        assertFalse(first.javaUuid().equals(nextVersion.javaUuid()));
        assertFalse(first.javaUuid().equals(first.bedrockUuid()));
    }

    @Test
    void requestsTheJavaPackWithTheManifestDerivedId() {
        ResourcePackManifest manifest = manifest("075a3f4dec4b");
        AtomicReference<UUID> requestedPackId = new AtomicReference<>();
        ResourcePackService service = service(manifest,
            (playerId, packId, uri, sha1, prompt, required) -> requestedPackId.set(packId));

        assertTrue(service.requestFor(PLAYER_ID));
        assertEquals(manifest.javaUuid(), requestedPackId.get());
        assertTrue(service.isBigCasaresPack(manifest.javaUuid()));
        assertFalse(service.isBigCasaresPack(UUID.fromString("00000000-0000-0000-0000-000000000099")));
    }

    @Test
    void aBedrockPlayerNeverOwesTheRequiredJavaPack() {
        ResourcePackManifest manifest = manifest("075a3f4dec4b");
        AtomicInteger requests = new AtomicInteger();
        ResourcePackGateway gateway = (playerId, packId, uri, sha1, prompt, required) -> requests.incrementAndGet();
        ResourcePackService bedrock = service(manifest, gateway, true, true, ignored -> ClientPlatform.BEDROCK);
        ResourcePackService java = service(manifest, gateway, true, true, ignored -> ClientPlatform.JAVA);

        assertFalse(bedrock.forceRequestFor(PLAYER_ID));
        assertFalse(bedrock.owesRequiredPack(PLAYER_ID));
        assertTrue(java.forceRequestFor(PLAYER_ID));
        assertTrue(java.owesRequiredPack(PLAYER_ID));
        java.updateState(manifest.javaUuid(), PLAYER_ID, ResourcePackPlayerState.LOADED);
        assertFalse(java.owesRequiredPack(PLAYER_ID));
        assertEquals(1, requests.get());
        assertFalse(service(manifest, gateway, false, true, ignored -> ClientPlatform.JAVA).owesRequiredPack(PLAYER_ID));
    }

    @Test
    void manualRequestForcesDeliveryWhenAutomaticResendIsDisabled() {
        ResourcePackManifest manifest = manifest("075a3f4dec4b");
        AtomicInteger requests = new AtomicInteger();
        ResourcePackService service = service(manifest,
            (playerId, packId, uri, sha1, prompt, required) -> requests.incrementAndGet(), false);

        assertTrue(service.requestFor(PLAYER_ID));
        assertFalse(service.requestFor(PLAYER_ID));
        assertTrue(service.forceRequestFor(PLAYER_ID));
        assertEquals(2, requests.get());
    }

    @Test
    void transientDownloadFailuresAreRetriedTwiceThenStop() {
        ResourcePackManifest manifest = manifest("075a3f4dec4b");
        AtomicInteger requests = new AtomicInteger();
        ResourcePackService service = service(manifest,
            (playerId, packId, uri, sha1, prompt, required) -> requests.incrementAndGet());

        assertTrue(service.requestFor(PLAYER_ID));
        service.updateState(manifest.javaUuid(), PLAYER_ID, ResourcePackPlayerState.FAILED);
        assertTrue(service.retryFailedFor(PLAYER_ID));
        service.updateState(manifest.javaUuid(), PLAYER_ID, ResourcePackPlayerState.FAILED);
        assertTrue(service.retryFailedFor(PLAYER_ID));
        service.updateState(manifest.javaUuid(), PLAYER_ID, ResourcePackPlayerState.FAILED);
        assertFalse(service.retryFailedFor(PLAYER_ID));

        assertEquals(3, requests.get());
    }

    @Test
    void oneLoadedPackDoesNotResetRetriesForAnotherFailingPackInTheStack() {
        ResourcePackManifest manifest = manifest("075a3f4dec4b");
        UUID betterModelId = UUID.fromString("00000000-0000-0000-0000-000000000081");
        AtomicInteger requests = new AtomicInteger();
        ResourcePackService service = service(manifest,
            (playerId, packId, uri, sha1, prompt, required) -> requests.incrementAndGet());
        service.activateStack(manifest, URI.create("https://example.com/bigcasares.zip"), List.of(
            new JavaPackDelivery(betterModelId, URI.create("https://example.com/bettermodel.zip"),
                new byte[20], "bettermodel")
        ));

        assertTrue(service.requestFor(PLAYER_ID));
        for (int attempt = 0; attempt < 2; attempt++) {
            service.updateState(betterModelId, PLAYER_ID, ResourcePackPlayerState.LOADED);
            service.updateState(manifest.javaUuid(), PLAYER_ID, ResourcePackPlayerState.FAILED);
            assertTrue(service.retryFailedFor(PLAYER_ID));
        }
        service.updateState(betterModelId, PLAYER_ID, ResourcePackPlayerState.LOADED);
        service.updateState(manifest.javaUuid(), PLAYER_ID, ResourcePackPlayerState.FAILED);

        assertFalse(service.retryFailedFor(PLAYER_ID));
        assertEquals(6, requests.get());
    }

    @Test
    void exposesEveryManualDownloadAndChangesRevisionWithThePackStack() throws Exception {
        ResourcePackManifest manifest = manifest("075a3f4dec4b");
        ResourcePackService service = service(manifest,
            (playerId, packId, uri, sha1, prompt, required) -> { });
        Method downloads = ResourcePackService.class.getMethod("currentDownloads");
        Method revision = ResourcePackService.class.getMethod("currentRevision");
        String baseRevision = (String) revision.invoke(service);

        service.activateStack(manifest, URI.create("https://example.com/bigcasares.zip"), List.of(
            new JavaPackDelivery(UUID.fromString("00000000-0000-0000-0000-000000000081"),
                URI.create("https://example.com/bettermodel.zip"), new byte[20], "bettermodel")
        ));

        assertEquals(2, ((List<?>) downloads.invoke(service)).size());
        assertFalse(baseRevision.equals(revision.invoke(service)));
        assertTrue(((String) revision.invoke(service)).matches("[0-9a-f]{16}"));
    }

    @Test
    void ignoresStatusEventsForResourcePacksOwnedByOtherPlugins() {
        ResourcePackManifest manifest = manifest("075a3f4dec4b");
        ResourcePackService service = service(manifest, (playerId, packId, uri, sha1, prompt, required) -> { });
        ResourcePackStatusHandler handler = new ResourcePackStatusHandler(service);

        handler.handle(
            UUID.fromString("00000000-0000-0000-0000-000000000099"),
            PLAYER_ID,
            "SUCCESSFULLY_LOADED",
            () -> { }
        );

        assertEquals(ResourcePackPlayerState.NOT_REQUESTED, service.state(PLAYER_ID));
    }

    @Test
    void acceptsStatusEventsForTheBigCasaresPack() {
        ResourcePackManifest manifest = manifest("075a3f4dec4b");
        ResourcePackService service = service(manifest, (playerId, packId, uri, sha1, prompt, required) -> { });
        ResourcePackStatusHandler handler = new ResourcePackStatusHandler(service);

        handler.handle(
            manifest.javaUuid(),
            PLAYER_ID,
            "SUCCESSFULLY_LOADED",
            () -> { }
        );

        assertEquals(ResourcePackPlayerState.LOADED, service.state(PLAYER_ID));
    }

    @Test
    void transitionRecognizesButDoesNotApplyLatePreviousPackStatus() {
        ResourcePackManifest previous = manifest("075a3f4dec4b");
        ResourcePackManifest current = manifest("175a3f4dec4b");
        ResourcePackService service = service(previous, (playerId, packId, uri, sha1, prompt, required) -> { });
        ResourcePackStatusHandler handler = new ResourcePackStatusHandler(service);
        service.activate(current, URI.create("https://example.com/current.zip"));

        handler.handle(previous.javaUuid(), PLAYER_ID, "SUCCESSFULLY_LOADED", () -> { });

        assertTrue(service.isBigCasaresPack(previous.javaUuid()));
        assertTrue(service.isBigCasaresPack(current.javaUuid()));
        assertEquals(ResourcePackPlayerState.NOT_REQUESTED, service.state(PLAYER_ID));

        handler.handle(current.javaUuid(), PLAYER_ID, "SUCCESSFULLY_LOADED", () -> { });

        assertEquals(ResourcePackPlayerState.LOADED, service.state(PLAYER_ID));
    }

    @Test
    void listenerRejectsForeignPackIdsBeforeReadingPlayerStatus() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/dev/linqfy/bigCasares/modules/resourcepack/ResourcePackStatusListener.java"
        ));

        int idGuard = source.indexOf("if (!service.isBigCasaresPack(event.getID()))");
        int playerAccess = source.indexOf("event.getPlayer()", idGuard);

        assertTrue(idGuard >= 0, "listener must guard status events by pack ID");
        assertTrue(playerAccess > idGuard, "foreign pack events must return before player status handling");
    }

    private static ResourcePackService service(ResourcePackManifest manifest, ResourcePackGateway gateway) {
        return service(manifest, gateway, true);
    }

    private static ResourcePackService service(
        ResourcePackManifest manifest,
        ResourcePackGateway gateway,
        boolean resendOnVersionChange
    ) {
        return service(manifest, gateway, false, resendOnVersionChange, ignored -> ClientPlatform.JAVA);
    }

    private static ResourcePackService service(
        ResourcePackManifest manifest,
        ResourcePackGateway gateway,
        boolean required,
        boolean resendOnVersionChange,
        ClientPlatformGateway platforms
    ) {
        return new ResourcePackService(
            new ResourcePackSettings(
                required,
                resendOnVersionChange,
                "BigCasares pack",
                new ResourcePackPublisher(ResourcePackPublisher.Mode.EXTERNAL_URL,
                    URI.create("https://example.com/bigcasares.zip"))
            ),
            manifest,
            gateway,
            platforms
        );
    }

    private static ResourcePackManifest manifest(String version) {
        return new ResourcePackManifest(
            version,
            "0".repeat(64),
            "1".repeat(40),
            BEDROCK_PACK_ID
        );
    }

}
