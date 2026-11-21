package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateIdRemapTest {
    /** note block 500 -> leaves 900; a player's leaves 900 -> the same leaves at distance 7 (906). */
    private static final StateIdRemap REMAP = StateIdRemap.of(Map.of(500, 900, 900, 906));

    @Test
    void appliesOnceWithoutChaining() {
        assertEquals(900, REMAP.apply(500), "module note block becomes the borrowed leaves");
        assertEquals(906, REMAP.apply(900), "a real leaves block in the borrowed state moves to distance 7");
        assertEquals(906, REMAP.apply(906));
        assertEquals(1, REMAP.apply(1));
        assertEquals(100_000, REMAP.apply(100_000));
        assertEquals(-1, REMAP.apply(-1));
        assertTrue(REMAP.remaps(500));
        assertFalse(REMAP.remaps(906));
        assertEquals(2, REMAP.size());
    }

    @Test
    void rewritesPaletteEntriesInPlace() {
        int[] palette = {0, 500, 12, 900, 906};
        assertTrue(REMAP.rewrite(palette));
        assertArrayEquals(new int[] {0, 900, 12, 906, 906}, palette);
        int[] untouched = {0, 1, 2};
        assertFalse(REMAP.rewrite(untouched));
        assertArrayEquals(new int[] {0, 1, 2}, untouched);
    }

    @Test
    void rejectsBrokenMappings() {
        assertThrows(IllegalArgumentException.class, () -> StateIdRemap.of(Map.of(5, 5)));
        assertThrows(IllegalArgumentException.class, () -> StateIdRemap.of(Map.of(-1, 5)));
        assertThrows(IllegalArgumentException.class, () -> StateIdRemap.of(Map.of(StateIdRemap.MAX_ID, 5)));
        assertTrue(StateIdRemap.of(Map.of()).isEmpty());
    }

    @Test
    void singletonSectionGetsANewSingleton() {
        DataPalette section = new DataPalette(new SingletonPalette(500), null, PaletteType.CHUNK);
        assertTrue(PacketEventsRemap.rewriteStates(section, REMAP));
        assertInstanceOf(SingletonPalette.class, section.palette);
        assertEquals(900, section.palette.idToState(0));
        DataPalette stone = new DataPalette(new SingletonPalette(1), null, PaletteType.CHUNK);
        assertFalse(PacketEventsRemap.rewriteStates(stone, REMAP));
        assertEquals(1, stone.palette.idToState(0));
    }

    @Test
    void indirectSectionOnlyChangesItsPalette() {
        BitStorage storage = new BitStorage(4, 4096);
        for (int i = 0; i < 4096; i++) {
            storage.set(i, i % 3);
        }
        long[] packed = storage.getData().clone();
        DataPalette section = new DataPalette(new ListPalette(4, new int[] {0, 500, 900}), storage, PaletteType.CHUNK);
        assertTrue(PacketEventsRemap.rewriteStates(section, REMAP));
        assertEquals(3, section.palette.size());
        assertEquals(0, section.palette.idToState(0));
        assertEquals(900, section.palette.idToState(1));
        assertEquals(906, section.palette.idToState(2));
        assertEquals(4, section.palette.getBits());
        assertArrayEquals(packed, section.storage.getData(), "the packed indices are untouched");
        assertEquals(900, section.get(1, 0, 0));

        DataPalette plain = new DataPalette(new ListPalette(4, new int[] {0, 1}), new BitStorage(4, 4096), PaletteType.CHUNK);
        assertFalse(PacketEventsRemap.rewriteStates(plain, REMAP));
    }

    @Test
    void globalSectionIsRewrittenEntryByEntry() {
        BitStorage storage = new BitStorage(15, 4096);
        storage.set(0, 500);
        storage.set(4095, 900);
        storage.set(7, 33);
        DataPalette section = new DataPalette(new GlobalPalette(), storage, PaletteType.CHUNK);
        assertTrue(PacketEventsRemap.rewriteStates(section, REMAP));
        assertEquals(900, storage.get(0));
        assertEquals(906, storage.get(4095));
        assertEquals(33, storage.get(7));
        DataPalette plain = new DataPalette(new GlobalPalette(), new BitStorage(15, 4096), PaletteType.CHUNK);
        assertFalse(PacketEventsRemap.rewriteStates(plain, REMAP), "an all-air global section is left alone");
    }
}
