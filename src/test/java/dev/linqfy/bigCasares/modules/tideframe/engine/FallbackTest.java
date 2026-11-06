package dev.linqfy.bigCasares.modules.tideframe.engine;
import dev.linqfy.bigCasares.modules.tideframe.engine.compat.FallbackModel;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class FallbackTest {
    @Test void allElementsRemainAvailableInTheAlternativeMenu() {
        var entries=FallbackModel.entries(Screen.demos().get("menu"));
        assertEquals(5,entries.size());assertEquals("TIDEFRAME",entries.getFirst().text());
        assertEquals("close",entries.getLast().action());
    }
    @Test void nativeButtonsHaveTheSameAllowlistedActionsAsCursorButtons() {
        var menu=Screen.demos().get("menu");
        assertEquals("open:profile",FallbackModel.action(menu,1).orElseThrow());
        assertEquals("close",FallbackModel.action(menu,4).orElseThrow());
        assertTrue(FallbackModel.action(menu,0).isEmpty());
    }
    @Test void bottomInventoryAndOutsideSlotsHaveNoAction() {
        var menu=Screen.demos().get("menu");
        assertTrue(FallbackModel.action(menu,-1).isEmpty());assertTrue(FallbackModel.action(menu,27).isEmpty());
    }
}
