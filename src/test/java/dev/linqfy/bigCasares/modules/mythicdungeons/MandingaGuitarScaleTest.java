package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.items.catalog.CustomItemCatalog;
import dev.linqfy.bigCasares.items.catalog.CustomItemDefinition;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MandingaGuitarScaleTest {

    @Test
    void consecutiveShotsNeverRepeatTheNote() {
        Random random = new Random(3);
        int previous = -1;
        Set<Integer> used = new HashSet<>();
        for (int shot = 0; shot < 500; shot++) {
            int next = MandingaGuitarScale.next(previous, random);
            assertNotEquals(previous, next);
            assertTrue(next >= 0 && next < MandingaGuitarScale.size());
            used.add(next);
            previous = next;
        }
        assertEquals(MandingaGuitarScale.size(), used.size(), "the whole scale gets played");
    }

    @Test
    void pitchesStayInTheNoteBlockRangeAndRise() {
        assertEquals(0.5f, MandingaGuitarScale.pitch(0), 1.0e-6);
        assertEquals(2.0f, MandingaGuitarScale.pitch(MandingaGuitarScale.size() - 1), 1.0e-6);
        for (int degree = 1; degree < MandingaGuitarScale.size(); degree++) {
            assertTrue(MandingaGuitarScale.pitch(degree) > MandingaGuitarScale.pitch(degree - 1));
        }
    }

    @Test
    void oneNotePerSecondPerPlayer() {
        MandingaGuitarCooldown cooldown = new MandingaGuitarCooldown();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(cooldown.tryFire(a, 100));
        assertFalse(cooldown.tryFire(a, 119));
        assertTrue(cooldown.tryFire(b, 119));
        assertTrue(cooldown.tryFire(a, 120));
        cooldown.note(a, 4);
        assertEquals(4, cooldown.lastNote(a));
        cooldown.forget(a);
        assertEquals(-1, cooldown.lastNote(a));
        assertTrue(cooldown.tryFire(a, 121));
        assertEquals(20, MandingaGuitarCooldown.TICKS);
        assertEquals(12.0, MandingaGuitar.NOTE_DAMAGE);
    }

    @Test
    void theCatalogItemIsAnUnstackableGuitarWithTheGuitarMechanic(@TempDir Path dir) throws Exception {
        Path items = Files.createDirectories(dir.resolve("items"));
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("content/items/mandinga_guitar.yml")) {
            assertNotNull(stream);
            Files.copy(stream, items.resolve("mandinga_guitar.yml"));
        }
        CustomItemCatalog catalog = new ItemCatalogLoader().load(items);
        CustomItemDefinition guitar = catalog.require(MandingaGuitar.ITEM_ID);
        assertEquals(MandingaGuitar.MECHANIC, guitar.mechanic());
        assertEquals(1, guitar.maxStackSize());
        assertEquals("CROSSBOW", guitar.material());
        assertTrue(guitar.recipeDefinition().isEmpty(), "only Mandinga gives it");
        assertEquals("mandinga_pact", MandingaGuitar.PACT_KEY.getKey());
    }
}
