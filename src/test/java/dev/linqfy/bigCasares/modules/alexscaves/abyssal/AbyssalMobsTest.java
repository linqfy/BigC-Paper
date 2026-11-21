package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.ModSpawnTables;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbyssalMobsTest {
    @Test
    void lanternfishDepthUsesVanillaClamp() {
        // By day the band's top (floor + 15%) is under its bottom (floor + 25%): vanilla Mth.clamp lets the minimum win.
        assertEquals(25, AbyssalMobs.Lanternfish.clamp(10, 25, 15));
        assertEquals(15, AbyssalMobs.Lanternfish.clamp(40, 25, 15));
        assertEquals(30, AbyssalMobs.Lanternfish.clamp(30, 20, 40));
    }

    @Test
    void seaCreaturesAreInTheModSpawnTable() {
        for (var type : AbyssalMobs.ALL) {
            if (type == AbyssalMobs.MINE_GUARDIAN || type == AbyssalMobs.MINE_GUARDIAN_ANCHOR) {
                continue;
            }
            assertTrue(ModSpawnTables.all(CaveBiomeId.ABYSSAL_CHASM).stream().anyMatch(entry -> entry.mobId().equals(type.id())),
                type.id());
        }
    }

    @Test
    void mineGuardiansOnlySpawnInTheirRuins() {
        ModMobCatalog catalog = new ModMobCatalog();
        AbyssalMobs.register(catalog, null, null);
        assertTrue(catalog.inWater(AbyssalMobs.MINE_GUARDIAN.id()));
        assertTrue(catalog.structureSpawns(CaveBiomeId.ABYSSAL_CHASM).stream().allMatch(ModMobCatalog.StructureSpawns::water));
        assertTrue(ModSpawnTables.all(CaveBiomeId.ABYSSAL_CHASM).stream().noneMatch(entry -> entry.mobId().equals("mine_guardian")));
    }
}
