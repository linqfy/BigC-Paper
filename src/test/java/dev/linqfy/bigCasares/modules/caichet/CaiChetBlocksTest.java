package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.blocks.MushroomState;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los bloques del hotel: estados reservados fijos, sin choques con The Sift, y que no sueltan nada. */
class CaiChetBlocksTest {

    @Test
    void thereIsOneDefinitionPerHotelBlockWithItsFixedState() {
        List<CustomBlockDefinition> definitions = CaiChetBlocks.definitions();
        assertEquals(HotelBlock.values().length, definitions.size());
        assertEquals(15, definitions.size());
        // La asignacion es dato persistente de los mundos: este test la fija.
        assertEquals("red:ESD", HotelBlock.WALLPAPER_BEIGE.state().compact());
        assertEquals("red:NSUD", HotelBlock.LIGHT_PANEL.state().compact());
        assertEquals("red:NWUD", HotelBlock.LIGHT_PANEL_OFF.state().compact());
        for (int i = 0; i < definitions.size(); i++) {
            HotelBlock block = HotelBlock.values()[i];
            assertEquals("caichet:" + block.path(), definitions.get(i).id().asString());
            assertEquals(block.state(), definitions.get(i).state());
            assertTrue(definitions.get(i).state().isReservable(), block + " usa el pool reservado");
            assertEquals(MushroomState.Base.RED, definitions.get(i).state().base());
        }
    }

    @Test
    void theyNeverClashWithTheSiftOrWithEachOther() {
        CustomBlockRegistry registry = new CustomBlockRegistry();
        SiftBlocks.definitions().forEach(registry::register);
        CaiChetBlocks.register(registry);
        CaiChetBlocks.register(registry); // registrar dos veces no duplica
        assertEquals(SiftBlocks.definitions().size() + 15, registry.definitions().size());
        Set<MushroomState> states = new HashSet<>();
        CaiChetBlocks.definitions().forEach(definition -> assertTrue(states.add(definition.state())));
    }

    @Test
    void wallsDropNothingEvenWithSilkTouchAndMineLikeCopper() {
        for (CustomBlockDefinition definition : CaiChetBlocks.definitions()) {
            assertTrue(definition.drops().isEmpty(), definition.id() + " no suelta items");
            assertFalse(definition.silkTouchDropsSelf(), definition.id() + " no se suelta a si mismo");
            assertEquals(0, definition.maxExperience());
            assertEquals(3.0F, definition.hardness());
            assertEquals(CustomBlockDefinition.ToolType.PICKAXE, definition.tool());
            assertEquals("block.copper", definition.soundGroup());
        }
    }

    @Test
    void everyHotelBlockIsFoundByItsBackingState() {
        for (HotelBlock block : HotelBlock.values()) {
            assertEquals(java.util.Optional.of(block), HotelBlock.of(block.state()));
        }
        assertEquals(java.util.Optional.empty(), HotelBlock.of(MushroomState.parse("red:D")));
    }
}
