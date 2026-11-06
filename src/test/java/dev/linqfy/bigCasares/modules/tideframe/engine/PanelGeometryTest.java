package dev.linqfy.bigCasares.modules.tideframe.engine;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.PanelGeometry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PanelGeometryTest {
    @Test void visibleQuadDimensionsFollowTheSameNormalizedBoundsAsHitTesting(){
        var p=PanelGeometry.panel(.3,-.4,.8,.2);
        assertEquals(.36,p.x(),1e-9);assertEquals(-.28,p.y(),1e-9);
        assertEquals(.96,p.sx()*.4,1e-9);assertEquals(.14,p.sy()*.4,1e-9);
    }
    @Test void editingOneDimensionDoesNotResizeTheOther(){
        var a=PanelGeometry.panel(0,0,.4,.2);var b=PanelGeometry.panel(0,0,.8,.2);
        assertEquals(a.sx()*2,b.sx(),1e-9);assertEquals(a.sy(),b.sy(),1e-9);
    }
}
