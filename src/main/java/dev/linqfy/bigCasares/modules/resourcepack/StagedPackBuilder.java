package dev.linqfy.bigCasares.modules.resourcepack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StagedPackBuilder {

    private final Path sourceRoot;
    private final Path packsRoot;
    private final List<JavaPackLayerProvider> javaLayers;

    private final boolean panelsEnabled;

    public StagedPackBuilder(Path sourceRoot, Path packsRoot, List<JavaPackLayerProvider> javaLayers) {
        this(sourceRoot, packsRoot, javaLayers, false);
    }

    StagedPackBuilder(Path sourceRoot, Path packsRoot, List<JavaPackLayerProvider> javaLayers,
                      boolean panelsEnabled) {
        this.panelsEnabled = panelsEnabled;
        this.sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot").toAbsolutePath().normalize();
        this.packsRoot = Objects.requireNonNull(packsRoot, "packsRoot").toAbsolutePath().normalize();
        this.javaLayers = List.copyOf(javaLayers);
    }

    public StagedPackCandidate build(UUID jobId) throws IOException {
        Objects.requireNonNull(jobId, "jobId");
        Path staging = packsRoot.resolve(".staging").resolve(jobId.toString());
        if (Files.exists(staging)) {
            throw new IllegalStateException("Pack staging directory already exists: " + staging);
        }
        Files.createDirectories(staging);
        try {
            ResourcePackBuildResult base = new ResourcePackBuilder(sourceRoot, staging, panelsEnabled).build();
            Path javaPack = staging.resolve("bigcasares-java.zip");
            for (int index = 0; index < javaLayers.size(); index++) {
                JavaPackLayerProvider layer = javaLayers.get(index);
                String id = Objects.requireNonNull(layer.id(), "layer id").trim();
                Path archive = Objects.requireNonNull(layer.archive(), "layer archive");
                if (id.isEmpty() || !Files.isRegularFile(archive)) {
                    throw new IllegalArgumentException("Unavailable Java pack layer: " + id);
                }
                Path merged = staging.resolve("java-layer-" + index + ".zip");
                new MergedJavaResourcePackBuilder().merge(javaPack, archive, merged);
                Files.move(merged, javaPack, StandardCopyOption.REPLACE_EXISTING);
            }
            Path bedrockPack = staging.resolve("bigcasares-bedrock.mcpack");
            ResourcePackArchiveValidator archiveValidator = new ResourcePackArchiveValidator();
            archiveValidator.validateJava(javaPack);
            archiveValidator.validateBedrock(bedrockPack);
            String javaSha256 = PackFileDigests.digest(javaPack, "SHA-256");
            String javaSha1 = PackFileDigests.digest(javaPack, "SHA-1");
            String bedrockSha256 = PackFileDigests.digest(bedrockPack, "SHA-256");
            ResourcePackManifest finalManifest = new ResourcePackManifest(
                javaSha256.substring(0, 12),
                base.manifest().inputSha256(),
                javaSha1,
                base.manifest().bedrockUuid()
            );
            Files.writeString(staging.resolve("manifest.json"), finalManifest.toJson(), StandardCharsets.UTF_8);
            return new StagedPackCandidate(
                jobId, staging, javaPack, bedrockPack, finalManifest, javaSha256, bedrockSha256);
        } catch (Throwable failure) {
            try {
                StagedPackCandidate.deleteDirectory(staging);
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            if (failure instanceof IOException exception) {
                throw exception;
            }
            if (failure instanceof RuntimeException exception) {
                throw exception;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(failure);
        }
    }
}
