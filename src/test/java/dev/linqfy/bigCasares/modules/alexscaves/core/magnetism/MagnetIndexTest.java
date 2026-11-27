package dev.linqfy.bigCasares.modules.alexscaves.core.magnetism;

import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetForce.Polarity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagnetIndexTest {
    private final UUID world = UUID.randomUUID();

    @Test
    void findsMagnetsAcrossChunkBordersWithinTheRadius() {
        MagnetIndex index = new MagnetIndex();
        index.putChunk(world, 0, 0, List.of(new MagnetIndex.Magnet(15, 64, 8, Polarity.SCARLET)));
        index.putChunk(world, 1, 0, List.of(new MagnetIndex.Magnet(17, 64, 8, Polarity.AZURE)));
        assertEquals(2, index.near(world, 16.5, 64.5, 8.5, 5).size());
        assertEquals(1, index.near(world, 12.5, 64.5, 8.5, 4).size());
        assertTrue(index.near(UUID.randomUUID(), 16.5, 64.5, 8.5, 5).isEmpty(), "other worlds are separate");
    }

    @Test
    void placeAndBreakUpdateTheIndex() {
        MagnetIndex index = new MagnetIndex();
        index.add(world, new MagnetIndex.Magnet(-3, 10, -3, Polarity.SCARLET));
        index.add(world, new MagnetIndex.Magnet(-3, 10, -3, Polarity.AZURE));
        List<MagnetIndex.Magnet> found = index.near(world, -2.5, 10.5, -2.5, 1);
        assertEquals(1, found.size(), "one magnet per position");
        assertEquals(Polarity.AZURE, found.get(0).polarity());
        index.remove(world, -3, 10, -3);
        assertEquals(0, index.chunkCount());
        index.putChunk(world, 0, 0, List.of());
        assertEquals(0, index.chunkCount());
    }

    @Test
    void ferromagneticMaterialsFollowTheModTag() {
        assertTrue(MagnetismService.isFerromagneticMaterial("IRON_CHESTPLATE"));
        assertTrue(MagnetismService.isFerromagneticMaterial("NETHERITE_SWORD"));
        assertTrue(MagnetismService.isFerromagneticMaterial("MINECART"));
        assertTrue(MagnetismService.isFerromagneticMaterial("IRON_NUGGET"));
        assertFalse(MagnetismService.isFerromagneticMaterial("GOLD_NUGGET"));
        assertFalse(MagnetismService.isFerromagneticMaterial("DIAMOND_PICKAXE"));
        assertFalse(MagnetismService.isFerromagneticMaterial("LEATHER_BOOTS"));
        assertFalse(MagnetismService.isFerromagneticMaterial("SHIELD"), "vanilla shields and the gauntlet are not");
    }
}
