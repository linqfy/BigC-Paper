package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatapackInstallerTest {
    @TempDir
    Path temp;

    @Test
    void installsOnceAndThenOnlyWhenSomethingChanges() throws IOException {
        Map<String, String> files = bundle("pack.mcmeta\ndata/a.json\n");
        files.put(DatapackInstaller.RESOURCE_ROOT + "pack.mcmeta", "{}");
        files.put(DatapackInstaller.RESOURCE_ROOT + "data/a.json", "{\"a\":1}");
        DatapackInstaller installer = new DatapackInstaller(source(files));

        DatapackInstaller.InstallResult first = installer.install(temp);
        assertTrue(first.changed());
        assertEquals(2, first.written());
        assertEquals("{\"a\":1}", Files.readString(temp.resolve(DatapackInstaller.FOLDER_NAME).resolve("data/a.json")));

        assertFalse(installer.install(temp).changed());

        files.put(DatapackInstaller.RESOURCE_ROOT + "data/a.json", "{\"a\":2}");
        DatapackInstaller.InstallResult third = installer.install(temp);
        assertEquals(1, third.written());
    }

    @Test
    void relativeDatapackFolderLikePapersDefaultWorks() throws IOException {
        Map<String, String> files = bundle("pack.mcmeta\ndata/a.json\n");
        files.put(DatapackInstaller.RESOURCE_ROOT + "pack.mcmeta", "{}");
        files.put(DatapackInstaller.RESOURCE_ROOT + "data/a.json", "{}");
        Path cwd = Path.of("").toAbsolutePath();
        Path relative = Path.of(".").resolve(cwd.relativize(temp.toAbsolutePath()));
        DatapackInstaller.InstallResult result = new DatapackInstaller(source(files)).install(relative);
        assertEquals(2, result.written());
        assertTrue(Files.isRegularFile(temp.resolve(DatapackInstaller.FOLDER_NAME).resolve("data/a.json")));
    }

    @Test
    void filesNoLongerShippedAreRemoved() throws IOException {
        Map<String, String> files = bundle("pack.mcmeta\n");
        files.put(DatapackInstaller.RESOURCE_ROOT + "pack.mcmeta", "{}");
        Path stale = temp.resolve(DatapackInstaller.FOLDER_NAME).resolve("data/old.json");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "old");
        DatapackInstaller.InstallResult result = new DatapackInstaller(source(files)).install(temp);
        assertEquals(1, result.removed());
        assertFalse(Files.exists(stale));
    }

    @Test
    void packagingMistakesFailLoudly() {
        assertThrows(IllegalStateException.class, () -> new DatapackInstaller(source(new HashMap<>())).bundledFiles());
        assertThrows(IllegalStateException.class, () -> new DatapackInstaller(source(bundle("data/a.json\n"))).bundledFiles());
        assertThrows(IllegalStateException.class, () -> new DatapackInstaller(source(bundle("pack.mcmeta\n../evil.json\n"))).bundledFiles());
        Map<String, String> missing = bundle("pack.mcmeta\n");
        assertThrows(IllegalStateException.class, () -> new DatapackInstaller(source(missing)).verifyPackaged());
    }

    @Test
    void levelRootIsFoundAboveDimensionFolders() throws IOException {
        Path level = temp.resolve("world");
        Path dimension = level.resolve("dimensions/minecraft/overworld");
        Files.createDirectories(dimension);
        assertTrue(DatapackInstaller.findLevelRoot(dimension).isEmpty());
        Files.writeString(level.resolve("level.dat"), "x");
        assertEquals(level, DatapackInstaller.findLevelRoot(dimension).orElseThrow());
        assertEquals(level, DatapackInstaller.findLevelRoot(level).orElseThrow());
    }

    private static Map<String, String> bundle(String manifest) {
        Map<String, String> files = new HashMap<>();
        files.put(DatapackInstaller.MANIFEST, manifest);
        return files;
    }

    private static DatapackInstaller.ResourceSource source(Map<String, String> files) {
        return path -> files.containsKey(path)
            ? new ByteArrayInputStream(files.get(path).getBytes(StandardCharsets.UTF_8))
            : null;
    }
}
