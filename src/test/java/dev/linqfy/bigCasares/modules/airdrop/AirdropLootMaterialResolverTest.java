package dev.linqfy.bigCasares.modules.airdrop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirdropLootMaterialResolverTest {

    @Test
    void resolveAllReturnsValidatedMaterials() {
        Map<String, Material> resolved = new AirdropLootMaterialResolver().resolveAll();

        assertTrue(resolved.containsKey("TNT"));
        assertEquals(Material.TNT, resolved.get("TNT"));
    }

    @Test
    void invalidMaterialFailsFast() {
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new AirdropLootMaterialResolver().resolveOrThrow("NOT_A_REAL_MATERIAL")
        );

        assertTrue(thrown.getMessage().contains("NOT_A_REAL_MATERIAL"));
    }
}
