package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LegendaryLegacyDataTest {
    @TempDir Path root;

    @Test void importsLevelsAndOwnershipWithoutChangingTheOriginalOrReplacingActiveData() throws Exception {
        Path old = root.resolve("LegendaryWeapons/data/legendary/state.properties");
        var store = new LegendaryStateStore(old);
        UUID player = UUID.randomUUID();
        new LegendaryPvpLevels(store).set(player, LegendaryWeapon.BLOODLUST, 4);
        store.put("unique.bloodlust", "claimed");
        byte[] original = Files.readAllBytes(old);
        Path imported = LegendaryLegacyData.migrate(root.resolve("BigCasares"), root.resolve("LegendaryWeapons"));
        assertArrayEquals(original, Files.readAllBytes(imported));
        var migrated = new LegendaryStateStore(imported);
        assertEquals(4, new LegendaryPvpLevels(migrated).level(player, LegendaryWeapon.BLOODLUST));
        assertEquals("claimed", migrated.get("unique.bloodlust"));
        new LegendaryPvpLevels(migrated).set(player, LegendaryWeapon.BLOODLUST, 5);
        LegendaryLegacyData.migrate(root.resolve("BigCasares"), root.resolve("LegendaryWeapons"));
        assertEquals(5, new LegendaryPvpLevels(new LegendaryStateStore(imported)).level(player, LegendaryWeapon.BLOODLUST));
        assertArrayEquals(original, Files.readAllBytes(old));
    }

    @Test void freshServerDoesNotCreateALegacyFolderOrAnEmptyJournal() throws Exception {
        Path target = LegendaryLegacyData.migrate(root.resolve("BigCasares"), root.resolve("LegendaryWeapons"));
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(root.resolve("LegendaryWeapons")));
    }

    @Test void loreUsesTheActualPlayerLevelAndKeepsFiveControlsVisible() {
        var zero = LegendaryWeaponLore.describe(LegendaryWeapon.BLOODLUST, 0);
        var three = LegendaryWeaponLore.describe(LegendaryWeapon.BLOODLUST, 3);
        assertEquals(8, zero.size());
        assertEquals(0, zero.stream().filter(s -> s.startsWith("§a")).count());
        assertEquals(3, three.stream().filter(s -> s.startsWith("§a")).count());
        assertEquals("§a  ✓ N3: Blood Trail — Shift + F/G", three.get(5));
    }
}
