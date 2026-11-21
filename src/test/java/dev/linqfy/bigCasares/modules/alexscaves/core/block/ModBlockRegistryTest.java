package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModBlockRegistryTest {

    @Test
    void stateIndexMatchesTheGeneratorEncoding() {
        NoteBlockState first = new NoteBlockState(50);
        assertEquals("basedrum", first.instrument());
        assertEquals(0, first.note());
        assertFalse(first.powered());
        NoteBlockState state = new NoteBlockState(50 * 4 + 7 * 2 + 1);
        assertEquals("bass", state.instrument());
        assertEquals(7, state.note());
        assertTrue(state.powered());
        assertEquals("minecraft:note_block[instrument=bass,note=7,powered=true]", state.blockDataString());
        assertEquals(state, NoteBlockState.of("bass", 7, true).orElseThrow());
        assertFalse(new NoteBlockState(49).isCustom(), "every harp state stays vanilla");
        assertTrue(NoteBlockState.of("zombie", 1, false).isEmpty(), "head instruments are never allocated");
        assertThrows(IllegalArgumentException.class, () -> new NoteBlockState(800));
    }

    @Test
    void shippedAllocationParses() throws IOException {
        ModBlockRegistry registry = ModBlockRegistry.parse(Files.readString(Path.of("src/main/resources/alexscaves/blocks.yml")));
        assertTrue(registry.contains("galena"));
        assertEquals(Set.of("x", "y", "z"), registry.variants("galena_pillar"));
        CustomBlockState galena = registry.state("galena", "default").orElseThrow();
        assertEquals(new ModBlockRegistry.Placed("galena", "default"), registry.at(galena).orElseThrow());
    }

    @Test
    void brokenAllocationsFailLoudly() {
        assertThrows(IllegalStateException.class, () -> ModBlockRegistry.parse("a:\n  default: 60\nb:\n  default: 60\n"));
        assertThrows(IllegalStateException.class, () -> ModBlockRegistry.parse("a:\n  default: 3\n"));
        assertThrows(IllegalStateException.class, () -> ModBlockRegistry.parse("  default: 60\n"));
        assertThrows(IllegalStateException.class, () -> ModBlockRegistry.parse("a:\na:\n"));
        assertThrows(IllegalStateException.class, () -> ModBlockRegistry.parse("a:\n  default: nope\n"));
    }

    @Test
    void variantChoiceFollowsTheClickedFace() {
        assertEquals("default", BlockVariants.choose(Set.of("default"), "NORTH"));
        assertEquals("x", BlockVariants.choose(Set.of("x", "y", "z"), "EAST"));
        assertEquals("z", BlockVariants.choose(Set.of("x", "y", "z"), "SOUTH"));
        assertEquals("y", BlockVariants.choose(Set.of("x", "y", "z"), "UP"));
        Set<String> facing = Set.of("down", "up", "north", "south", "west", "east");
        assertEquals("west", BlockVariants.choose(facing, "WEST"));
        assertEquals("down", BlockVariants.choose(Set.of("up", "down"), "DOWN"));
        assertEquals("up", BlockVariants.choose(Set.of("up", "down"), "NORTH"));
    }
}
