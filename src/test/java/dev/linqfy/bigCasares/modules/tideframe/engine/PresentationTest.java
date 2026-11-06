package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudRaster;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PresentationTest {
    private static String json(String presentation, double brightness) {
        return "{\"version\":6,\"id\":\"presentation\",\"title\":\"PRESENTATION\",\"quality\":{\"preset\":\"low\",\"width\":512,\"height\":288},\"presentation\":"+presentation+",\"elements\":["
            +"{\"id\":\"box\",\"text\":\"\",\"x\":0,\"y\":0,\"width\":1,\"height\":0.5,\"kind\":\"button\",\"action\":\"close\",\"shape\":\"rectangle\",\"border\":false,\"messageCondition\":null,\"background\":true,\"image\":null,\"style\":{\"opacity\":0.5,\"fillColor\":\"#204080\",\"borderColor\":\"#abcdef\",\"textColor\":\"#fedcba\",\"shadow\":false,\"rotation\":0,\"radius\":0.08,\"outlineWidth\":1,\"hoverColor\":null,\"hoverEffect\":\"none\",\"textAlign\":\"center\",\"wrapWidth\":0,\"brightness\":"+brightness+"},\"layer\":0,\"actionArea\":null}]}";
    }
    private static final String DEFAULT="{\"mode\":\"menu\",\"scale\":1,\"cursor\":\"cross\"}";
    @Test void schemaSixRoundtripsPresentationBrightnessAndRetainsOldEncoding() {
        var screen=ScreenCodec.decode(json("{\"mode\":\"overlay\",\"scale\":3,\"cursor\":\"text\"}",1.5));
        String encoded=ScreenCodec.encode(screen);assertTrue(encoded.startsWith("{\"version\":6"));assertEquals(screen,ScreenCodec.decode(encoded));
        assertTrue(ScreenCodec.encode(ScreenCodec.decode(json(DEFAULT,1))).startsWith("{\"version\":4"));
        assertTrue(ScreenCodec.encode(new Screen("old","OLD",List.of())).startsWith("{\"version\":3"));
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(json(DEFAULT,1).replace("\"scale\":1","\"scale\":1.00000000000000000001")));
        for(String p:List.of(DEFAULT.replace("menu","script"),DEFAULT.replace(":1",":4"),DEFAULT.replace("cross","foreign"),DEFAULT.replace(",\"cursor\":\"cross\"","")))assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(json(p,1)));
        for(double brightness:new double[]{-.1,2.1})assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(json(DEFAULT,brightness)));
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(json(DEFAULT,1).replace(",\"brightness\":1.0","")));
    }
    @Test void brightnessScalesElementCompositeRgbAndPreservesAlpha() {
        for(double brightness:new double[]{0,.5,1,2}) {
            var rendered=HudRaster.render(ScreenCodec.decode(json(DEFAULT,brightness)),null,8);
            int expected=0x80000000 | (int)Math.round(32*brightness)<<16 | (int)Math.round(64*brightness)<<8 | Math.min(255,(int)Math.round(128*brightness));
            assertEquals(expected,rendered.getRGB(256,144));
        }
    }
    @Test void brightnessCoversPhotoTextOutlineWithoutChangingUnderlyingElements() throws Exception {
        String sample=json(DEFAULT,2).replace("\"border\":false","\"border\":true").replace("\"opacity\":0.5","\"opacity\":1").replace("\"text\":\"\"","\"text\":\"A\"")
            .replace("\"image\":null","\"image\":\""+ImageAsset.fromPng(PngFixtures.rgba(1,1,new int[]{0xff204080})).base64()+"\"");
        var rendered=HudRaster.render(ScreenCodec.decode(sample),null,8);
        assertEquals(0xff4080ff,rendered.getRGB(256,160));assertEquals(0xffffffff,rendered.getRGB(128,144));assertEquals(0xfffffffF,rendered.getRGB(256,136));
        String background=sample.replace("\"brightness\":2.0","\"brightness\":0.0");
        var front=ScreenCodec.decode(background);var under=new Screen.Element("under","",0,0,2,2,"decoration","","rectangle",false,null,true,null,new Screen.Style(1,"#00ff00","#ffffff","#ffffff",false),-1,null);
        var layers=new ArrayList<Screen.Element>();layers.add(under);layers.addAll(front.elements());
        var over=HudRaster.render(new Screen(front.id(),front.title(),layers,front.quality()),null,8);
        assertEquals(0xff00ff00,over.getRGB(10,10));assertEquals(0xff000000,over.getRGB(256,144));
    }
}
