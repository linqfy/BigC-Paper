package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SchemaFourTest {
    private static String quality(String preset,int width,int height) {
        return "{\"preset\":\""+preset+"\",\"width\":"+width+",\"height\":"+height+"}";
    }
    private static String element(String id,String kind,double x,double width,int layer,String area) {
        return "{\"id\":\""+id+"\",\"text\":\"\",\"x\":"+x+",\"y\":0,\"width\":"+width+",\"height\":0.2,\"kind\":\""+kind+"\",\"action\":\""+(kind.equals("button")?"close":"")+"\",\"shape\":\"rectangle\",\"border\":false,\"messageCondition\":null,\"background\":true,\"image\":null,\"style\":{\"opacity\":0.5,\"fillColor\":\"#28384b\",\"borderColor\":\"#ffffff\",\"textColor\":\"#ffffff\",\"shadow\":true},\"layer\":"+layer+",\"actionArea\":"+area+"}";
    }
    private static String screen(String quality,String... elements) {
        return "{\"version\":4,\"id\":\"advanced\",\"title\":\"ADVANCED\",\"quality\":"+quality+",\"elements\":["+String.join(",",elements)+"]}";
    }
    @Test void supportedProfilesAndExplicitStyleRoundtripAsVersionFour() {
        for (String q:List.of(quality("low",512,288),quality("medium",1920,1080),quality("high",2560,1440),quality("custom",3840,2160))) {
            String input=screen(q,element("a","button",0,.4,2,"null"));
            Screen decoded=ScreenCodec.decode(input);
            String encoded=ScreenCodec.encode(decoded);
            assertTrue(encoded.contains("\"version\":4"));assertTrue(encoded.contains("\"quality\":"+q));
            assertTrue(encoded.contains("\"opacity\":0.5"));assertTrue(encoded.contains("\"shadow\":true"));
            assertEquals(decoded,ScreenCodec.decode(encoded));
        }
    }
    @Test void customQualityIsBoundedExactSixteenByNineAndPresetsCannotLie() {
        assertDoesNotThrow(()->ScreenCodec.decode(screen(quality("custom",256,144))));
        for(String q:List.of(quality("high",1920,1080),quality("medium",2560,1440),quality("low",1024,576),
                quality("legacy",1024,576),quality("custom",3841,2160),quality("custom",256,143),quality("custom",240,135),
                quality("custom",257,144),quality("custom",4096,2304),quality("ultra",3840,2160)))
            assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(screen(q)),q);
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(screen(quality("high",2560,1440)).replace("\"width\":2560","\"width\":2560.1")));
    }
    @Test void advancedAllows128DecorativeElementsAndStillBoundsArrays() {
        String[] elements=new String[128];for(int i=0;i<elements.length;i++)elements[i]=element("d"+i,"decoration",0,1,i%257-128,"null");
        var decoded=ScreenCodec.decode(screen(quality("low",512,288),elements));assertEquals(128,decoded.elements().size());
        assertEquals(decoded,ScreenCodec.decode(ScreenCodec.encode(decoded)));
        var tooMany=Arrays.copyOf(elements,129);tooMany[128]=element("d128","decoration",0,1,0,"null");
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(screen(quality("low",512,288),tooMany)));
    }
    @Test void clickInteriorsCannotOverlapButVisualBoundsCanAndTouchingEdgesAreAllowed() {
        String q=quality("low",512,288);
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(screen(q,element("a","button",0,1,0,"null"),element("b","button",.25,1,1,"null"))));
        assertDoesNotThrow(()->ScreenCodec.decode(screen(q,element("a","button",-.25,.5,0,"null"),element("b","button",.25,.5,1,"null"))));
        assertDoesNotThrow(()->ScreenCodec.decode(screen(q,element("a","button",0,1,0,"{\"x\":-0.5,\"y\":0,\"width\":0.2,\"height\":0.2}"),element("b","button",0,1,1,"{\"x\":0.5,\"y\":0,\"width\":0.2,\"height\":0.2}"))));
        assertDoesNotThrow(()->ScreenCodec.decode(screen(q,element("a","label",0,1,0,"null"),element("b","decoration",0,1,1,"null"))));
    }
    @Test void strictFieldsAndFiniteStylesRejectTyposAndUnsafeDecorationActions() {
        String valid=screen(quality("low",512,288),element("a","decoration",0,1,0,"null"));
        for(String malformed:List.of(valid.replace("\"opacity\":0.5","\"opacity\":1.01"),valid.replace("\"fillColor\":\"#28384b\"","\"fillColor\":\"red\""),
                valid.replace("\"shadow\":true","\"shadow\":1"),valid.replace("\"layer\":0","\"layer\":128.1"),valid.replace("\"layer\":0","\"layer\":129"),
                valid.replace("\"action\":\"\"","\"action\":\"close\""),valid.replace("\"image\":null,",""),valid.replace("\"shadow\":true","\"shadow\":true,\"script\":\"op\"")))
            assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(malformed));
    }
    @Test void oldJsonStillExportsVersionThreeAndRetainsLegacyLimit() {
        String legacy=ScreenCodec.encode(Screen.demos().get("menu"));assertTrue(legacy.startsWith("{\"version\":3,"));
        assertFalse(legacy.contains("\"quality\""));assertEquals(Screen.demos().get("menu"),ScreenCodec.decode(legacy));
        String element=legacy.substring(legacy.indexOf("[{" )+1,legacy.indexOf("},{")+1);
        String many=String.join(",",java.util.stream.IntStream.range(0,25).mapToObj(i->element.replace("\"heading\"","\"d"+i+"\"")).toList());
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode("{\"version\":3,\"id\":\"menu\",\"title\":\"MENU\",\"elements\":["+many+"]}"));
    }
    @Test void effectiveTargetsAreStableByLayerAndUseAbsoluteCustomAreas() {
        var s=ScreenCodec.decode(screen(quality("high",2560,1440),element("top","button",-.6,.2,5,"null"),
                element("first","button",0,1,-2,"{\"x\":0.5,\"y\":0.2,\"width\":0.2,\"height\":0.1}"),
                element("second","button",.8,.2,-2,"null"),element("decor","decoration",0,1,128,"null")));
        assertEquals(List.of("first","second","top"),s.actionTargets().stream().map(t->t.id()).toList());
        var area=s.actionTargets().getFirst();assertEquals(.5,area.x());assertEquals(.2,area.y());assertEquals(.2,area.width());assertEquals(.1,area.height());
        var moved=s.move("first",-.2,-.2);assertEquals(RenderQuality.high(),moved.quality());
        var movedArea=moved.actionTargets().getFirst();assertEquals(.3,movedArea.x(),1e-12);assertEquals(0,movedArea.y(),1e-12);
        assertEquals(.2,movedArea.width());assertEquals(.1,movedArea.height());
        assertEquals(s.elements().get(1).style(),moved.elements().get(1).style());assertEquals(-2,moved.elements().get(1).layer());
        assertThrows(IllegalArgumentException.class,()->s.move("first",.8,0));
        assertEquals(.5,s.actionTargets().getFirst().x());assertEquals(.2,s.actionTargets().getFirst().y());
        assertEquals(RenderQuality.high(),s.delete("decor").quality());
        assertEquals(RenderQuality.high(),s.delete("decor").add(s.elements().getLast()).quality());
    }
    @Test void advancedEncodeRejectsOverlapsAndLegacyExportRejectsUnrepresentableStyles() {
        var a=new Screen.Element("a","",0,0,1,.2,"button","close");
        var b=new Screen.Element("b","",0,0,1,.2,"button","close");
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.encode(new Screen("advanced","ADVANCED",List.of(a,b),RenderQuality.low())));
        assertDoesNotThrow(()->ScreenCodec.encode(new Screen("legacy","LEGACY",List.of(a,b))));
        var styled=ScreenCodec.decode(screen(quality("low",512,288),element("a","button",0,.4,2,"null"))).elements().getFirst();
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.encode(new Screen("legacy","LEGACY",List.of(styled))));
    }
    @Test void decimalTouchingAreasUseTheSameArithmeticAsEditorAndPositiveOverlapStillRejects() {
        String q=quality("low",512,288);
        assertDoesNotThrow(()->ScreenCodec.decode(screen(q,element("a","button",-1,.07,0,"null"),element("b","button",-.68,.57,0,"null"))));
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(screen(q,element("a","button",-1,.07,0,"null"),element("b","button",-.68000001,.57,0,"null"))));
    }
    @Test void zeroDeltaMoveDoesNotDriftAnIndependentAreaIntoAnAdjacentTarget() {
        var s=ScreenCodec.decode(screen(quality("low",512,288),element("a","button",.7,.2,0,"{\"x\":0.2,\"y\":0.3,\"width\":0.2,\"height\":0.2}")));
        assertEquals(s,s.move("a",.7,0));
    }
}
