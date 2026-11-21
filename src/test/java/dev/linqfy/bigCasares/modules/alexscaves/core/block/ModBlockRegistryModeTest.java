package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Without Paper's tripwire and chorus plant update options the moved blocks keep their note block states. */
class ModBlockRegistryModeTest {
    private static final String TEXT = """
        mussel:
          up_1: tripwire 32 legacy 600
        tube_worm:
          straight: chorus_plant 19 legacy 601
        galena:
          default: 60
        """;

    @Test
    void inactiveBasesKeepTheLegacyNoteBlockStates() {
        ModBlockRegistry registry = ModBlockRegistry.parse(TEXT, false);
        assertFalse(registry.shapedBasesActive());
        assertEquals(CustomBlockState.note(600), registry.state("mussel", "up_1").orElseThrow());
        assertEquals(CustomBlockState.note(601), registry.state("tube_worm", "straight").orElseThrow());
        assertTrue(registry.legacy().isEmpty(), "nothing to migrate");
        assertEquals(3, registry.usedStates().size());
        assertTrue(registry.usedStates().stream().allMatch(state -> state.base() == BlockBase.NOTE_BLOCK));
        assertTrue(registry.at(new CustomBlockState(BlockBase.TRIPWIRE, 32)).isEmpty(),
            "a string state is vanilla then, not a module block");
        assertEquals("mussel", registry.at(CustomBlockState.note(600)).orElseThrow().blockId());
    }

    @Test
    void activeBasesUseTheNewStatesAndMigrate() {
        ModBlockRegistry registry = ModBlockRegistry.parse(TEXT, true);
        assertTrue(registry.shapedBasesActive());
        assertEquals(new CustomBlockState(BlockBase.TRIPWIRE, 32), registry.state("mussel", "up_1").orElseThrow());
        assertEquals(2, registry.legacy().size());
        assertEquals(5, registry.usedStates().size());
    }

    @Test
    void aMovedStateWithoutALegacyOneCannotFallBack() {
        assertThrows(IllegalStateException.class, () -> ModBlockRegistry.parse("a:\n  x: tripwire 32\n", false));
    }

    @Test
    void shippedAllocationWorksInBothModes() throws IOException {
        String text = Files.readString(Path.of("src/main/resources/alexscaves/blocks.yml"));
        ModBlockRegistry on = ModBlockRegistry.parse(text, true);
        ModBlockRegistry off = ModBlockRegistry.parse(text, false);
        assertEquals(on.blockIds(), off.blockIds());
        for (String id : on.blockIds()) {
            assertEquals(on.variants(id), off.variants(id), id);
        }
        assertTrue(off.usedStates().stream().allMatch(state -> state.base() == BlockBase.NOTE_BLOCK));
        assertEquals(on.usedStates().size() - 85, off.usedStates().size(),
            "the 76 moved variants and the 9 plant states share one state each");
        on.legacy().forEach((old, current) -> assertEquals(old, off.state(on.at(old).orElseThrow().blockId(),
            on.at(old).orElseThrow().variant()).orElseThrow()));
    }

    @Test
    void bothPaperOptionsAreNeededForTheShapedBases() {
        assertTrue(NoteBlockService.shapedBasesActive(paper(true, true)));
        assertFalse(NoteBlockService.shapedBasesActive(paper(true, false)));
        assertFalse(NoteBlockService.shapedBasesActive(paper(false, true)));
        assertFalse(NoteBlockService.shapedBasesActive(new YamlConfiguration()), "a missing key reads as off");
        assertFalse(NoteBlockService.shapedBasesActive(null));
    }

    private static YamlConfiguration paper(boolean tripwire, boolean chorus) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("block-updates.disable-tripwire-updates", tripwire);
        config.set("block-updates.disable-chorus-plant-updates", chorus);
        return config;
    }
}
