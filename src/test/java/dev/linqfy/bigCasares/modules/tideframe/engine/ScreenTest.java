package dev.linqfy.bigCasares.modules.tideframe.engine;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;
class ScreenTest {
    @Test void demoProvidesEveryRequestedScreenKind() {
        var demo = Screen.demos();
        assertEquals(4,demo.size());
        assertEquals("PROFILE",demo.get("profile").title());
        assertEquals("POPUP",demo.get("popup").title());
        assertEquals("OVERLAY",demo.get("overlay").title());
        assertTrue(demo.get("menu").elements().stream().anyMatch(e -> e.action().equals("open:profile")));
    }
    @Test void rejectsDuplicateElementsAndUnknownActions() {
        var e = new Screen.Element("a","A",0,0,.4,.1,"button","close");
        assertThrows(IllegalArgumentException.class,() -> new Screen("menu","MENU",List.of(e,e)));
        assertThrows(IllegalArgumentException.class,() -> new Screen.Element("a","A",0,0,.4,.1,"button","console:op"));
        assertThrows(IllegalArgumentException.class,() -> new Screen.Element("a","A",Double.NaN,0,.4,.1,"button","close"));
    }
    @Test void editsReturnNewValidatedScreensAndPreserveOtherElements() {
        Screen s = Screen.demos().get("menu");
        Screen moved = s.move("profile",.2,.3);
        assertEquals(.2,moved.elements().stream().filter(e -> e.id().equals("profile")).findFirst().orElseThrow().x());
        assertNotEquals(s,moved);
        assertEquals(s.elements().size()-1,s.delete("profile").elements().size());
    }
}
