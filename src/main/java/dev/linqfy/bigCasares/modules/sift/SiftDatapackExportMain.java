package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.datapack.DatapackCompiler;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Build-time entry point ({@code gradlew generateSiftDatapack}): compiles The Sift
 * with default settings into {@code <out>/the_sift/} and a reproducible
 * {@code <out>/the_sift.zip}, so the generated JSON can be inspected or diffed
 * without starting a server. The runtime installs the same bytes.
 */
public final class SiftDatapackExportMain {

    private SiftDatapackExportMain() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: SiftDatapackExportMain <output-directory>");
        }
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Logger logger = Logger.getLogger("TheSift");
        SiftWorldgen worldgen = new SiftWorldgen(SiftData.fromClasspath(), logger);
        DimensionDefinition definition = SiftDimension.create(SiftSettings.defaults(), worldgen, List.of());
        GeneratedDatapack pack = new DatapackCompiler().compile(List.of(definition)).datapack();

        Path folder = output.resolve("the_sift");
        deleteRecursively(folder);
        Files.createDirectories(folder);
        pack.writeTo(folder);
        try (OutputStream zip = Files.newOutputStream(output.resolve("the_sift.zip"))) {
            pack.writeZip(zip);
        }
        System.out.println("Wrote " + pack.size() + " files to " + folder);
        System.out.println("Content hash " + pack.contentHash());
        System.out.println("Java-placed features: " + worldgen.customPlacements().size());
    }

    private static void deleteRecursively(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
