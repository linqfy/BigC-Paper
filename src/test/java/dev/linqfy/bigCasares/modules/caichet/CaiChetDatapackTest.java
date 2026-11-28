package dev.linqfy.bigCasares.modules.caichet;

import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.dimension.datapack.DatapackInstaller;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los JSON del datapack viajan en el jar, son validos y se instalan una sola vez. */
class CaiChetDatapackTest {

    private static final ClassLoader LOADER = CaiChetDatapackTest.class.getClassLoader();

    @Test
    void everyListedFileIsAValidJsonResource() throws IOException {
        for (String file : CaiChetDatapack.FILES) {
            try (InputStream stream = LOADER.getResourceAsStream(CaiChetDatapack.RESOURCE_ROOT + file)) {
                assertNotNull(stream, "falta " + file);
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    assertDoesNotThrow(() -> JsonParser.parseReader(reader), "JSON invalido: " + file);
                }
            }
        }
    }

    @Test
    void theBuiltPackHoldsEveryFilePlusItsMeta() throws IOException {
        GeneratedDatapack pack = CaiChetDatapack.build(LOADER);
        Set<String> expected = new HashSet<>(CaiChetDatapack.FILES);
        expected.add("pack.mcmeta");
        assertEquals(expected, new HashSet<>(pack.paths()));
    }

    @Test
    void installingTwiceLeavesTheSecondRunUnchanged(@TempDir Path level) throws IOException {
        Path backups = level.resolve("backups");
        DatapackInstaller.InstallResult first = CaiChetDatapack.install(LOADER, level, backups);
        assertEquals(DatapackInstaller.Outcome.INSTALLED, first.outcome());
        Path pack = level.resolve("datapacks").resolve(CaiChetDatapack.FOLDER);
        assertTrue(Files.isRegularFile(pack.resolve("pack.mcmeta")));
        assertTrue(Files.isRegularFile(pack.resolve("data/caichet/dimension/cai_chet.json")));

        DatapackInstaller.InstallResult second = CaiChetDatapack.install(LOADER, level, backups);
        assertEquals(DatapackInstaller.Outcome.UNCHANGED, second.outcome());
        assertEquals(first.installedHash(), second.installedHash());
    }
}
