package dev.linqfy.bigCasares.modules.celular;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelularRecipeTest {

    @Test
    void eightIronIngotsAroundOneGlass() {
        assertEquals(Map.of("GLASS", 1, "IRON_INGOT", 8), CelularRecipe.materialCount());
        assertEquals("GLASS", CelularRecipe.INGREDIENTS.get(CelularRecipe.SHAPE.get(1).charAt(1)));
    }

    @Test
    void shapeIsAFullThreeByThreeGridOfRealMaterials() {
        assertEquals(3, CelularRecipe.SHAPE.size());
        for (String row : CelularRecipe.SHAPE) {
            assertEquals(3, row.length());
            for (char symbol : row.toCharArray()) {
                assertTrue(CelularRecipe.INGREDIENTS.containsKey(symbol), "symbol without ingredient: " + symbol);
            }
        }
        for (String material : CelularRecipe.INGREDIENTS.values()) {
            assertNotNull(Material.matchMaterial(material), material);
        }
    }
}
