package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterloggedIndexTest {
    private static final UUID OVERWORLD = UUID.randomUUID();
    private static final UUID NETHER = UUID.randomUUID();
    private static final ClientVersion V26_2 = ClientVersion.V_26_2;

    @Test
    void packsLocalPositionsWithNegativeHeights() {
        for (int[] at : new int[][] {{0, -64, 0}, {15, 319, 15}, {-1, -1, -17}, {37, 5, -300}, {-33, -60, 47}}) {
            int packed = WaterloggedIndex.pack(at[0], at[1], at[2]);
            assertEquals(at[0] & 15, WaterloggedIndex.localX(packed));
            assertEquals(at[1], WaterloggedIndex.y(packed));
            assertEquals(at[2] & 15, WaterloggedIndex.localZ(packed));
        }
    }

    @Test
    void savedFormIsSortedWithoutRepeats() {
        int a = WaterloggedIndex.pack(1, 10, 2);
        int b = WaterloggedIndex.pack(3, -20, 4);
        assertArrayEquals(new int[] {b, a}, WaterloggedIndex.normalize(new int[] {a, b, a}));
        assertEquals(0, WaterloggedIndex.normalize(null).length);
        assertArrayEquals(new int[] {b, a}, WaterloggedBlocks.merge(new int[] {a}, new int[] {b, a}));
    }

    @Test
    void addRemoveAndSaveRoundTrip() {
        WaterloggedIndex index = new WaterloggedIndex();
        assertFalse(index.mayContain(-1, 3));
        int[] saved = index.add(OVERWORLD, -5, -30, 60);
        assertArrayEquals(new int[] {WaterloggedIndex.pack(-5, -30, 60)}, saved);
        assertNull(index.add(OVERWORLD, -5, -30, 60), "already marked");
        assertTrue(index.contains(OVERWORLD, -5, -30, 60));
        assertFalse(index.contains(NETHER, -5, -30, 60));
        assertFalse(index.contains(OVERWORLD, -5, -29, 60));
        assertTrue(index.mayContain(-1, 3));
        saved = index.add(OVERWORLD, -16, 100, 48);
        assertEquals(2, saved.length);

        // What the chunk saves comes back as the same marks after an unload / load.
        index.unload(OVERWORLD, -1, 3);
        assertFalse(index.mayContain(-1, 3));
        assertFalse(index.contains(OVERWORLD, -5, -30, 60));
        index.load(OVERWORLD, -1, 3, saved);
        assertTrue(index.contains(OVERWORLD, -5, -30, 60));
        assertTrue(index.contains(OVERWORLD, -16, 100, 48));
        assertEquals(2, index.size());

        assertArrayEquals(new int[] {WaterloggedIndex.pack(-16, 100, 48)}, index.remove(OVERWORLD, -5, -30, 60));
        assertNull(index.remove(OVERWORLD, -5, -30, 60));
        assertEquals(0, index.remove(OVERWORLD, -16, 100, 48).length);
        assertFalse(index.mayContain(-1, 3), "an empty chunk is no longer looked at");
    }

    @Test
    void positionLookupIsAcrossWorlds() {
        WaterloggedIndex index = new WaterloggedIndex();
        index.add(OVERWORLD, 1, 1, 1);
        index.add(NETHER, 2, 2, 2);
        assertTrue(index.mayContain(0, 0));
        index.clearWorld(OVERWORLD);
        assertTrue(index.mayContain(0, 0), "the nether chunk is still there");
        index.clearWorld(NETHER);
        assertFalse(index.mayContain(0, 0));
        index.add(NETHER, 2, 2, 2);
        index.clear();
        assertEquals(0, index.size());
        assertEquals(0, index.positions(null, 0, 0).length);
    }

    /** mussel (tripwire 700) -> kelp 800; thin bone (note 500) -> leaves 900 dry -> waterlogged leaves 950. */
    @Test
    void chunkRewriteSendsMarkedPositionsWet() {
        StateIdRemap dry = StateIdRemap.of(Map.of(500, 900, 900, 906));
        StateIdRemap wet = StateIdRemap.of(Map.of(700, 800, 900, 950));
        int minY = -64;
        BitStorage storage = new BitStorage(4, 4096);
        DataPalette states = new DataPalette(new ListPalette(4, new int[] {0, 500, 700, 1}), storage, PaletteType.CHUNK);
        states.set(1, 2, 3, 500);
        states.set(4, 5, 6, 700);
        states.set(7, 8, 9, 700);
        states.set(0, 0, 0, 1);
        Chunk_v1_18 section = new Chunk_v1_18(V26_2, 4, 0, states, DataPalette.createForBiome());
        Chunk_v1_18 air = new Chunk_v1_18(V26_2, 0, 0, new DataPalette(new SingletonPalette(0), null, PaletteType.CHUNK),
            DataPalette.createForBiome());
        // Section index 1: y -48..-33.
        Column column = new Column(0, 0, true, new BaseChunk[] {air, section}, new TileEntity[0]);
        assertEquals(1, PacketEventsRemap.rewriteColumn(column, dry));
        int[] marked = WaterloggedIndex.normalize(new int[] {
            WaterloggedIndex.pack(1, -48 + 2, 3),      // thin bone in water
            WaterloggedIndex.pack(4, -48 + 5, 6),      // mussel in water
            WaterloggedIndex.pack(0, -48, 0),          // stale mark on stone: left alone
            WaterloggedIndex.pack(3, -64, 3),          // stale mark in an air section
            WaterloggedIndex.pack(3, 400, 3)});        // outside the column
        assertEquals(2, PacketEventsRemap.rewriteWet(column, minY, marked, wet));
        assertEquals(950, section.getBlockId(1, 2, 3));
        assertEquals(800, section.getBlockId(4, 5, 6));
        assertEquals(700, section.getBlockId(7, 8, 9), "an unmarked mussel stays dry");
        assertEquals(1, section.getBlockId(0, 0, 0));
        assertEquals(2, section.getFluidCount(), "each wet block adds a fluid");
        assertEquals(0, air.getBlockId(3, 0, 3));
        assertEquals(0, air.getFluidCount());
    }

    @Test
    void wetRewriteGrowsASingletonSection() {
        StateIdRemap wet = StateIdRemap.of(Map.of(700, 800));
        Chunk_v1_18 section = new Chunk_v1_18(V26_2, 4096, 0, new DataPalette(new SingletonPalette(700), null, PaletteType.CHUNK),
            DataPalette.createForBiome());
        Column column = new Column(0, 0, true, new BaseChunk[] {section}, new TileEntity[0]);
        assertEquals(1, PacketEventsRemap.rewriteWet(column, 0, new int[] {WaterloggedIndex.pack(5, 7, 9)}, wet));
        assertEquals(800, section.getBlockId(5, 7, 9));
        assertEquals(700, section.getBlockId(5, 7, 10));
    }
}
