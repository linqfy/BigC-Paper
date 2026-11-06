package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.RuntimeDemoCatalog;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CloseControlTest {
    private static Screen empty(){return new Screen("screen","SCREEN",List.of(),RenderQuality.low());}
    private static Screen.Element label(String id){return new Screen.Element(id,"",0,0,.1,.1,"label","");}
    @Test void menusReceiveVisibleTopmostCloseWithoutMutatingOriginal() {
        var original=empty();var result=CloseControl.prepare(original);
        assertEquals(0,original.elements().size());assertEquals(1,result.elements().size());var close=result.elements().getFirst();
        assertEquals("tideframe_close",close.id());assertEquals("X",close.text());assertEquals("close",close.action());assertEquals("button",close.kind());assertTrue(close.background());
        assertEquals(.84,close.x());assertEquals(.79,close.y());assertEquals(.12,close.width());assertEquals(.16,close.height());assertEquals(128,close.layer());
        assertEquals("#173b48",close.style().fillColor());assertEquals("#8bdfc9",close.style().borderColor());assertEquals("#edf8f4",close.style().textColor());assertEquals(1,close.style().brightness());
        assertEquals(result,ScreenCodec.decode(ScreenCodec.encode(result)));assertDoesNotThrow(result::validateActionAreas);
    }
    @Test void existingXAndPassiveOverlaysAreReturnedUnchanged() {
        var x=new Screen.Element("close_x","X",.9,.8,.1,.1,"button","close");var existing=new Screen("with_x","WITH X",List.of(x),RenderQuality.low());
        assertSame(existing,CloseControl.prepare(existing));assertEquals(1,existing.elements().size());
        var overlay=new Screen("overlay","OVERLAY",List.of(label("tideframe_close")),RenderQuality.low(),new Screen.Presentation("overlay",1,"text"));
        assertSame(overlay,CloseControl.prepare(overlay));
    }
    @Test void reservedIdCollisionRejectsAtomically() {
        var original=new Screen("collision","COLLISION",List.of(label("tideframe_close")),RenderQuality.low());
        assertThrows(IllegalArgumentException.class,()->CloseControl.prepare(original));assertEquals(1,original.elements().size());
    }
    @Test void explicitActionAreaCannotOverlapNewCloseEvenWhenVisualIsElsewhere() {
        var e=new Screen.Element("button","GO",0,0,.1,.1,"button","message:GO","rectangle",true,null,false,null,Screen.Style.defaults(),0,new Screen.ActionArea(.9,.9,.2,.2));
        var original=new Screen("overlap","OVERLAP",List.of(e),RenderQuality.low());
        assertThrows(IllegalArgumentException.class,()->CloseControl.prepare(original));assertEquals(1,original.elements().size());
    }
    @Test void full128ElementMenuRejectsWithoutDroppingUserElements() {
        var list=new ArrayList<Screen.Element>();for(int i=0;i<128;i++)list.add(label("e"+i));var original=new Screen("full","FULL",list,RenderQuality.low());
        assertThrows(IllegalArgumentException.class,()->CloseControl.prepare(original));assertEquals(128,original.elements().size());
    }
    @Test void legacy24PlusClosePromotesExplicitQualityAndRoundtripsAllElements() {
        var list=new ArrayList<Screen.Element>();for(int i=0;i<24;i++)list.add(label("e"+i));var original=new Screen("legacy","LEGACY",list);
        var prepared=CloseControl.prepare(original);assertEquals(25,prepared.elements().size());assertEquals(RenderQuality.custom(1024,576),prepared.quality());assertEquals(RenderQuality.legacy(),original.quality());
        assertEquals(prepared,ScreenCodec.decode(ScreenCodec.encode(prepared)));for(int i=0;i<24;i++)assertSame(original.elements().get(i),prepared.elements().get(i));
    }
    @Test void legacy23PlusCloseRetainsPreviousFontBaselineAndV3Publication() {
        var list=new ArrayList<Screen.Element>();for(int i=0;i<23;i++)list.add(label("e"+i));var original=new Screen("legacy","LEGACY",list);var prepared=CloseControl.prepare(original);
        assertEquals(RenderQuality.legacy(),prepared.quality());assertEquals(24,prepared.elements().size());assertTrue(ScreenCodec.encode(prepared).startsWith("{\"version\":3"));assertEquals(prepared,ScreenCodec.decode(ScreenCodec.encode(prepared)));
        assertEquals(Screen.Style.defaults(),prepared.elements().getLast().style());assertEquals(0,prepared.elements().getLast().layer());
    }
    @Test void runtimeExamplesAlreadyContainCloseControlsAndRemainUnmodified() {
        for(var screen:RuntimeDemoCatalog.screens().values()) {
            var input=screen.id().equals("overlay_demo")?new Screen(screen.id(),screen.title(),screen.elements(),screen.quality(),new Screen.Presentation("overlay",1,"text")):screen;
            assertSame(input,CloseControl.prepare(input),input.id());
        }
    }
    @Test void addingXPreservesExistingLegacyOverlapsWithoutIntroducingAnyNewOverlap() {
        var landscape=new Screen.Element("landscape","",0,.06,1.52,1.52,"button","message:Image");
        var back=new Screen.Element("back","BACK",.7,-.78,.45,.18,"button","open:menu");
        var original=new Screen("gallery","GALLERY",List.of(landscape,back));
        assertThrows(IllegalArgumentException.class,original::validateActionAreas);
        var prepared=CloseControl.prepare(original);
        assertSame(landscape,prepared.elements().get(0));assertSame(back,prepared.elements().get(1));
        assertEquals(3,prepared.elements().size());
        assertEquals(prepared,ScreenCodec.decode(ScreenCodec.encode(prepared)));
    }
}
