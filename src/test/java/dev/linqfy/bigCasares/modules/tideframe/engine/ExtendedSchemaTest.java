package dev.linqfy.bigCasares.modules.tideframe.engine;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class ExtendedSchemaTest {
    @Test void shippedDemosExerciseEveryShapeAndBothPermissionMessages(){
        var all=Screen.demos().values().stream().flatMap(s->s.elements().stream()).toList();
        assertEquals(java.util.Set.of("rectangle","rounded","ellipse"),all.stream().map(Screen.Element::shape).collect(java.util.stream.Collectors.toSet()));
        assertTrue(all.stream().filter(e->e.kind().equals("label")).allMatch(Screen.Element::border));
        assertTrue(all.stream().anyMatch(e->e.messageCondition()!=null));
    }
    private Screen.Element conditional(){return new Screen.Element("access","ACCESS",.2,-.1,.8,.2,"button","conditional-message","ellipse",true,
            new Screen.MessageCondition("tideframe.edit","Access granted.","Access denied."));}
    @Test void schemaTwoPreservesShapesBordersAndConditions(){
        var original=new Screen("menu","TIDEFRAME",List.of(conditional()));
        assertEquals(original,ScreenCodec.decode(ScreenCodec.encode(original)));
    }
    @Test void permissionSelectsExactlyOneLiteralMessage(){
        assertEquals("message:Access granted.",MessageRules.action(conditional(),p -> p.equals("tideframe.edit")));
        assertEquals("message:Access denied.",MessageRules.action(conditional(),p -> false));
        var literal=new Screen.Element("a","A",0,0,.8,.2,"button","message:{input:secret}");
        assertEquals("message:{input:secret}",MessageRules.action(literal,p -> {throw new AssertionError("Unexpected permission lookup");}));
    }
    @Test void conditionsAndShapesAreStrictAndCannotBecomeScripts(){
        assertThrows(IllegalArgumentException.class,() -> new Screen.MessageCondition("a b","Yes","No"));
        assertThrows(IllegalArgumentException.class,() -> new Screen.MessageCondition("a","\nNo","No"));
        assertThrows(IllegalArgumentException.class,() -> new Screen.Element("a","A",0,0,.8,.2,"button","close","triangle",true,null));
        assertThrows(IllegalArgumentException.class,() -> new Screen.Element("a","A",0,0,.8,.2,"button","conditional-message","rectangle",true,null));
        assertThrows(IllegalArgumentException.class,() -> new Screen.Element("a","A",0,0,.8,.2,"label","conditional-message","rectangle",true,conditional().messageCondition()));
    }
    @Test void schemaOneFilesKeepWorkingWithVisibleRectangleBorders(){
        String old="{\"version\":1,\"id\":\"menu\",\"title\":\"MENU\",\"elements\":[{\"id\":\"a\",\"text\":\"A\",\"x\":0,\"y\":0,\"width\":1,\"height\":0.2,\"kind\":\"label\",\"action\":\"\"}]}";
        var e=ScreenCodec.decode(old).elements().getFirst();assertEquals("rectangle",e.shape());assertTrue(e.border());assertNull(e.messageCondition());
    }
    @Test void movementDoesNotLoseAppearanceOrMessageRules(){
        var e=conditional().moved(-.2,.3);assertEquals("ellipse",e.shape());assertTrue(e.border());assertEquals(conditional().messageCondition(),e.messageCondition());
    }
}
