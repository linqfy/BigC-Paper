package dev.linqfy.bigCasares.dimension.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockStateSpecTest {

    @Test
    void parseRoundTripsAndSharesCachedInstances() {
        String state = "minecraft:snow[layers=3]";

        BlockStateSpec first = BlockStateSpec.parse(state);

        assertEquals(state, first.asString());
        assertEquals("3", first.properties().get("layers"));
        assertSame(first, BlockStateSpec.parse(state));
    }

    @Test
    void cacheDoesNotBypassValidation() {
        assertThrows(IllegalArgumentException.class, () -> BlockStateSpec.parse("minecraft:snow[Layers=3]"));
        assertThrows(IllegalArgumentException.class, () -> BlockStateSpec.parse("minecraft:snow[Layers=3]"));
        assertThrows(IllegalArgumentException.class, () -> BlockStateSpec.parse("minecraft:snow[layers=3"));
    }
}
