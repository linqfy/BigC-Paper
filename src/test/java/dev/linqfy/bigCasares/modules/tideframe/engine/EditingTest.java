package dev.linqfy.bigCasares.modules.tideframe.engine;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;

class EditingTest {
    @TempDir Path dir;
    @Test void roundtripPreservesCoordinatesTextAndActions() {
        Screen s=Screen.demos().get("menu").move("profile",.25,-.3);
        assertEquals(s,ScreenCodec.decode(ScreenCodec.encode(s)));
    }
    @Test void rejectsDuplicateUnknownKeysAndWrongTypes() {
        String valid=ScreenCodec.encode(Screen.demos().get("menu"));
        assertThrows(IllegalArgumentException.class,() -> ScreenCodec.decode(valid.replace("\"version\":3","\"version\":3,\"version\":3")));
        assertThrows(IllegalArgumentException.class,() -> ScreenCodec.decode(valid.replace("\"version\":3","\"version\":3,\"script\":\"op\"")));
        assertThrows(IllegalArgumentException.class,() -> ScreenCodec.decode(valid.replace("\"version\":3","\"version\":3.0000000000000000001")));
        assertThrows(IllegalArgumentException.class,() -> ScreenCodec.decode(valid.replace("\"x\":0.0","\"x\":\"0\"")));
    }
    @Test void boundsSizeDepthAndNonJsonNumbers() {
        assertThrows(IllegalArgumentException.class,() -> ScreenCodec.decode(" ".repeat(ScreenCodec.MAX_JSON_BYTES+1)));
        assertThrows(IllegalArgumentException.class,() -> ScreenCodec.decode("[".repeat(10)+"0"+"]".repeat(10)));
        assertThrows(IllegalArgumentException.class,() -> ScreenCodec.decode("{\"version\":NaN}"));
    }
    @Test void photoLayoutsUse128MibUtf8BudgetWhilePreservingParserBounds() {
        assertEquals(134_217_728, ScreenCodec.MAX_JSON_BYTES);
        String valid=ScreenCodec.encode(Screen.demos().get("menu"));
        assertEquals("menu", ScreenCodec.decode(" ".repeat(2_097_152)+valid).id());
        var oversized=assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode("é".repeat(67_108_865)));
        assertTrue(oversized.getMessage().contains("128 MiB"));
    }
    @Test void realFilesRoundtripAndCannotEscapeTheScreenDirectory() throws Exception {
        var store=new ScreenStore(dir);
        var screen=Screen.demos().get("profile");store.save(screen);
        assertEquals(screen,store.load("profile"));
        assertThrows(IllegalArgumentException.class,() -> store.load("../outside"));
        Files.writeString(dir.resolve("bad.json"),"{broken");
        assertThrows(IllegalArgumentException.class,() -> store.load("bad"));
    }
    @Test void gameEditorCreatesMovesAndDeletesUsingTheSameSchema() {
        var p=new PlaneProjection.Point(.4,.2);
        Screen initial=Screen.demos().get("menu");
        Screen made=InGameEditor.apply(initial,new String[]{"create","new_button","NEW BUTTON"},p);
        assertEquals(6,made.elements().size());
        Screen moved=InGameEditor.apply(made,new String[]{"move","new_button","-.2",".1"},p);
        assertEquals(-.2,moved.elements().getLast().x());
        assertEquals(initial,InGameEditor.apply(moved,new String[]{"delete","new_button"},p));
        assertThrows(IllegalArgumentException.class,() -> InGameEditor.apply(initial,new String[]{"move","missing"},p));
    }
    @Test void actualBrowserDownloadLoadsByItsOwnFilenameWithoutRenaming() throws Exception {
        // Fixture captured from a real browser download of the editor (formerly read from evidence/).
        String filename;
        try(var name=getClass().getResourceAsStream("/fixtures/browser-export-filename.txt");var json=getClass().getResourceAsStream("/fixtures/browser-export.json")) {
            assertNotNull(name);assertNotNull(json);
            filename=new String(name.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
            Files.copy(json,dir.resolve(filename));
        }
        Screen loaded=new ScreenStore(dir).load("menu");
        assertEquals(4,loaded.elements().size());
        var accept=loaded.elements().stream().filter(e -> e.id().equals("accept")).findFirst().orElseThrow();
        assertEquals(.25,accept.x());assertEquals(-.3,accept.y());assertEquals("message:Accepted.",accept.action());
    }
}
