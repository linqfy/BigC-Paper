package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetrifiedSlabDropsTest {
    private static final class FakeDrop implements PetrifiedSlabDrops.Drop {
        private final Material type;
        private final boolean module;
        private final int amount;
        private final List<Integer> replaced = new ArrayList<>();

        FakeDrop(Material type, boolean module, int amount) {
            this.type = type;
            this.module = module;
            this.amount = amount;
        }

        @Override
        public Material type() {
            return type;
        }

        @Override
        public boolean moduleItem() {
            return module;
        }

        @Override
        public int amount() {
            return amount;
        }

        @Override
        public void replaceWithModuleSlab(int amount) {
            replaced.add(amount);
        }
    }

    @Test
    void singleSlabDropsOneModuleSlab() {
        FakeDrop drop = new FakeDrop(Material.PETRIFIED_OAK_SLAB, false, 1);
        assertEquals(1, PetrifiedSlabDrops.convert(Material.PETRIFIED_OAK_SLAB, List.of(drop)));
        assertEquals(List.of(1), drop.replaced);
    }

    @Test
    void doubleSlabKeepsTheTwoSlabs() {
        FakeDrop drop = new FakeDrop(Material.PETRIFIED_OAK_SLAB, false, 2);
        PetrifiedSlabDrops.convert(Material.PETRIFIED_OAK_SLAB, List.of(drop));
        assertEquals(List.of(2), drop.replaced);
    }

    @Test
    void otherBlocksAndOtherDropsAreLeftAlone() {
        FakeDrop slab = new FakeDrop(Material.PETRIFIED_OAK_SLAB, false, 1);
        assertEquals(0, PetrifiedSlabDrops.convert(Material.SMOOTH_SANDSTONE_SLAB, List.of(slab)));
        FakeDrop stick = new FakeDrop(Material.STICK, false, 1);
        FakeDrop already = new FakeDrop(Material.PETRIFIED_OAK_SLAB, true, 1);
        assertEquals(0, PetrifiedSlabDrops.convert(Material.PETRIFIED_OAK_SLAB, List.of(stick, already)));
        assertEquals(List.of(), slab.replaced);
        assertEquals(List.of(), stick.replaced);
        assertEquals(List.of(), already.replaced);
    }

    @Test
    void noDropsMeansNothingToConvert() {
        assertEquals(0, PetrifiedSlabDrops.convert(Material.PETRIFIED_OAK_SLAB, List.of()));
    }
}
