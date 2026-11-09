package dev.linqfy.bigCasares.modules.customcrossbow;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SniperScopeInventoryTest {
    @Test
    void mainhandProxyOnlyReplacesTheSelectedSlotInThePlayerInventory() {
        SniperScopeInventory<String> view = new SniperScopeInventory<>(3, "scope");
        assertEquals("scope", view.playerSlot(3, "bow"));
        assertEquals("arrow", view.playerSlot(4, "arrow"));
        assertEquals("scope", view.containerSlot(0, 39, "bow"));
        assertEquals("armor", view.containerSlot(0, 5, "armor"));
        assertEquals("chest", view.containerSlot(2, 39, "chest"));
        assertEquals("cursor", view.containerSlot(-1, -1, "cursor"));
    }

    @Test
    void offhandProxySurvivesFullInventorySyncWithoutChangingTheOriginalPacket() {
        SniperScopeInventory<String> view = new SniperScopeInventory<>(40, "scope");
        List<String> original = Collections.nCopies(46, "original");
        List<String> presented = view.containerContents(0, original);
        assertEquals("scope", presented.get(45));
        assertEquals("original", presented.get(36));
        assertEquals("original", original.get(45));
        assertSame(original, view.containerContents(1, original));
        List<String> empty = List.of();
        assertSame(empty, view.containerContents(0, empty));
    }
}
