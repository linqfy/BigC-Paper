package dev.linqfy.bigCasares.modules.tideframe.engine.core;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ViewportFitTest {
    private final Screen screen=new Screen("test","TEST",List.of(),RenderQuality.medium());
    private final List<Integer> sizes=HudAssets.DEFAULT_SIZES;
    private int fit(int w,int h,int gui){return new ViewportFit(w,h,gui).multiplier(screen,sizes);}
    @Test void fitPicksTheSmallestSizeThatFillsTheScreen() {
        assertEquals(8,fit(1920,1080,0),"2x at 1080p GUI Auto: 512 of 480 GUI pixels, edge to edge");
        assertEquals(11,fit(1920,1080,3),"2.75x covers 640x360 GUI pixels");
        assertEquals(11,fit(2560,1440,4));
        assertEquals(7,fit(2560,1440,0));
        assertEquals(7,fit(1280,720,0));
        assertEquals(7,fit(3840,2160,0));
        assertEquals(7,fit(1366,768,0),"2x would hide a few safe-area pixels on 1366x768");
    }
    @Test void everyChosenSizeCoversTheScreenAndKeepsTheSafeAreaVisible() {
        // The common screens are covered edge to edge...
        for(int[] c:new int[][]{{1920,1080,0},{1920,1080,3},{2560,1440,0},{2560,1440,4},{1280,720,0},{3840,2160,0},{3840,2160,6}}) {
            var v=new ViewportFit(c[0],c[1],c[2]);int g=v.effectiveGuiScale();
            int w=(c[0]+g-1)/g,h=(c[1]+g-1)/g,q=v.multiplier(screen,sizes);
            assertTrue(ViewportFit.covers(w,h,q),c[0]+"x"+c[1]+" gui "+c[2]+" fills the screen at "+q);
        }
        // ...and on every screen the safe area (all text and buttons) stays visible.
        for(int[] c:new int[][]{{1920,1080,0},{1920,1080,3},{1920,1080,2},{2560,1440,0},{1280,720,0},{1366,768,0},{1600,900,0},{3840,2160,0},{2560,1440,4},{2560,1440,5},{1680,1050,0}}) {
            var v=new ViewportFit(c[0],c[1],c[2]);int g=v.effectiveGuiScale();
            int w=(c[0]+g-1)/g,h=(c[1]+g-1)/g,q=v.multiplier(screen,sizes);
            assertTrue(ViewportFit.safeAreaVisible(w,h,q),c[0]+"x"+c[1]+" gui "+c[2]+" keeps the safe area at "+q);
        }
    }
    @Test void canvasIsCentredAtOneAndNearlyCentredAtEverySize() {
        assertEquals(-72,HudAssets.canvasTopGui(1));
        assertEquals(-108,HudAssets.canvasTopGuiQuarters(6),"1.5x is exactly centred too");
        for(int q=4;q<=36;q++){int top=HudAssets.canvasTopGuiQuarters(q),bottom=top+36*q;
            assertTrue(Math.abs(bottom+top)/2.0<=.05*36*q,"size "+q/4.0+" off-centre by "+(bottom+top)/2);}
    }
    @Test void vanillaAutoGuiScaleMatchesTheClient() {
        assertEquals(4,new ViewportFit(1920,1080,0).effectiveGuiScale());
        assertEquals(3,new ViewportFit(1280,720,0).effectiveGuiScale());
        assertEquals(6,new ViewportFit(2560,1440,0).effectiveGuiScale());
        assertEquals(4,new ViewportFit(1920,1080,9).effectiveGuiScale());
    }
    @Test void fixedSizesSnapDownToPackSizesAndPersist() {
        assertEquals(7,ViewportFit.fixed(7).multiplier(screen,sizes));
        assertEquals(8,ViewportFit.fixed(9).multiplier(screen,sizes),"2.25x is not in the pack: 2x");
        assertEquals(4,ViewportFit.fixed(5).multiplier(screen,sizes));
        var fixed=ViewportFit.fixed(9);
        assertEquals(fixed,ViewportFit.decode(fixed.encode()));
        var fit=new ViewportFit(1920,1080,2);
        assertEquals(fit,ViewportFit.decode(fit.encode()));
        assertEquals(ViewportFit.fixed(8),ViewportFit.decode("size:2"),"Values stored by 0.10.0 previews still load");
        var twice=new Screen("two","TWO",List.of(),RenderQuality.medium(),new Screen.Presentation("menu",2,"block"));
        assertEquals(4,ViewportFit.fixed(7).multiplier(twice,sizes),"A 2x layout never shrinks");
        assertThrows(IllegalArgumentException.class,()->ViewportFit.fixed(17));
    }
    @Test void sizesParseFromCommandsAndConfig() {
        assertEquals(7,ViewportFit.parseSize("1.75"));
        assertEquals(7,ViewportFit.parseSize("1,75"));
        assertEquals(8,ViewportFit.parseSize("2"));
        assertEquals(6,ViewportFit.parseSize("150%"));
        assertThrows(IllegalArgumentException.class,()->ViewportFit.parseSize("1.3"));
        assertThrows(IllegalArgumentException.class,()->ViewportFit.parseSize("0.5"));
        assertEquals("1.75",ViewportFit.label(7));assertEquals("2",ViewportFit.label(8));
    }
    @Test void invalidReportsAreRejectedAndFontNamesAreBounded() {
        assertThrows(IllegalArgumentException.class,()->new ViewportFit(0,1080,2));
        assertThrows(IllegalArgumentException.class,()->new ViewportFit(1920,1080,99));
        assertEquals("tideframe:hud",ViewportFit.font(4));
        assertEquals("tideframe:hud_q7",ViewportFit.font(7));
    }
}
