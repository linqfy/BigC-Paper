package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import dev.linqfy.bigCasares.modules.alexscaves.AlexsCavesItemIds;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.ModSpawnTables;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticMobsTest {

    @Test
    void everySpawnTableMobOfTheBiomeIsImplemented() {
        ModMobCatalog catalog = new ModMobCatalog();
        MagneticMobs.register(catalog);
        ModSpawnTables.all(CaveBiomeId.MAGNETIC_CAVES)
            .forEach(entry -> assertTrue(catalog.contains(entry.mobId()), entry.mobId()));
        assertEquals(5, catalog.types().size());
    }

    @Test
    void statsMatchTheMod() {
        assertEquals(18.0, MagneticMobs.TELETOR.attribute("max_health", 0));
        assertEquals(20.0, MagneticMobs.BOUNDROID.attribute("armor", 0));
        assertEquals(30.0, MagneticMobs.MAGNETRON.attribute("max_health", 0));
        assertEquals(6.0, MagneticMobs.NOTOR.attribute("max_health", 0));
        assertEquals(10.0, MagneticMobs.Ferrouslime.stat(1, 10, 10, 100));
        assertEquals(100.0, MagneticMobs.Ferrouslime.stat(14, 10, 10, 100));
        assertEquals(10.0, MagneticMobs.Ferrouslime.stat(7, 2, 2, 10));
    }

    @Test
    void modelsArePackagedAndDropsExist() {
        for (ModMobType type : MagneticMobs.ALL) {
            assertNotNull(type.modelKey());
            assertTrue(Files.isRegularFile(Path.of("src/main/resources/bettermodel/models/" + type.modelKey() + ".bbmodel")),
                type.modelKey());
            for (MobDrop drop : type.drops()) {
                if (drop.item().startsWith("ac_")) {
                    assertTrue(AlexsCavesItemIds.contains(drop.item()), drop.item());
                } else {
                    assertNotNull(Material.matchMaterial(drop.item()), drop.item());
                }
            }
        }
    }
}
