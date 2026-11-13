package dev.linqfy.bigCasares.modules.resourcepack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResourcePackBuilder {
    private static final Pattern HEADER_UUID = Pattern.compile(
        "\\\"header\\\"\\s*:\\s*\\{.*?\\\"uuid\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
        Pattern.DOTALL
    );

    private final Path sourceRoot;
    private final Path outputDirectory;

    public ResourcePackBuilder(Path sourceRoot, Path outputDirectory) {
        this.sourceRoot = sourceRoot.toAbsolutePath().normalize();
        this.outputDirectory = outputDirectory.toAbsolutePath().normalize();
    }

    public ResourcePackBuildResult build() throws IOException {
        Path registryFile = sourceRoot.resolve("shared/registry.yml");
        Path javaSource = sourceRoot.resolve("java");
        Path bedrockSource = sourceRoot.resolve("bedrock");
        ResourcePackRegistry registry = ResourcePackRegistry.load(registryFile);

        String inputSha = treeDigest(sourceRoot, "SHA-256");
        Path manifestFile = outputDirectory.resolve("manifest.json");
        Path javaPack = outputDirectory.resolve("bigcasares-java.zip");
        Path bedrockPack = outputDirectory.resolve("bigcasares-bedrock.mcpack");
        if (Files.isRegularFile(manifestFile) && Files.isRegularFile(javaPack) && Files.isRegularFile(bedrockPack)) {
            ResourcePackManifest previous = ResourcePackManifest.fromJson(Files.readString(manifestFile));
            if (previous.inputSha256().equals(inputSha)) {
                return new ResourcePackBuildResult(previous, true);
            }
        }

        Files.createDirectories(outputDirectory);
        Path assembledRoot = outputDirectory.resolve("assembled");
        Path javaRoot = assembledRoot.resolve("java");
        Path bedrockRoot = assembledRoot.resolve("bedrock");
        copyTree(javaSource, javaRoot);
        copyTree(bedrockSource, bedrockRoot);
        new SharedResourcePackAssembler().assemble(sourceRoot, javaRoot, bedrockRoot, registry);
        new LegendaryItemModels().compile(sourceRoot, javaRoot, bedrockRoot);
        new ResourcePackValidator().validate(registry, javaRoot, bedrockRoot);
        new JavaResourcePackBuilder().build(javaRoot, outputDirectory);
        new BedrockResourcePackBuilder().build(bedrockRoot, outputDirectory);
        String javaSha = fileDigest(javaPack, "SHA-1");
        UUID bedrockUuid = readBedrockUuid(bedrockRoot.resolve("manifest.json"));
        ResourcePackManifest manifest = new ResourcePackManifest(
            inputSha.substring(0, 12), inputSha, javaSha, bedrockUuid
        );
        Files.writeString(manifestFile, manifest.toJson(), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("checksums.yml"), """
            version: %s
            input-sha256: %s
            java:
              file: bigcasares-java.zip
              sha1: %s
            bedrock:
              file: bigcasares-bedrock.mcpack
              sha256: %s
              uuid: %s
            """.formatted(
                manifest.version(),
                inputSha,
                javaSha,
                fileDigest(bedrockPack, "SHA-256"),
                bedrockUuid
            ), StandardCharsets.UTF_8);
        return new ResourcePackBuildResult(manifest, false);
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static UUID readBedrockUuid(Path manifest) throws IOException {
        Matcher matcher = HEADER_UUID.matcher(Files.readString(manifest));
        if (!matcher.find()) {
            throw new IllegalArgumentException("Bedrock manifest is missing header.uuid: " + manifest);
        }
        return UUID.fromString(matcher.group(1));
    }

    private static String treeDigest(Path root, String algorithm) throws IOException {
        MessageDigest digest = digest(algorithm);
        digest.update(LegendaryItemModels.VERSION.getBytes(StandardCharsets.UTF_8));
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')))
                .toList();
        }
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            update(digest, file);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String fileDigest(Path file, String algorithm) throws IOException {
        MessageDigest digest = digest(algorithm);
        update(digest, file);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Required digest algorithm is unavailable: " + algorithm, ex);
        }
    }
}
