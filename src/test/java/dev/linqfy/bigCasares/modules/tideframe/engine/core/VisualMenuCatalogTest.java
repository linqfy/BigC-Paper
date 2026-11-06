package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.RuntimeDemoCatalog;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudRaster;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class VisualMenuCatalogTest {
    @Test void originalMenuHasDistinctImagesAndValidNonoverlappingActions() {
        var screen = VisualMenuCatalog.screen();
        assertEquals("visual_menu09", screen.id());
        assertEquals(RenderQuality.custom(1024, 576), screen.quality(), "Authored at the atlas size: shown 1:1 at GUI 4");
        assertEquals(4, screen.elements().stream().filter(e -> e.image() != null).count());
        var hashes = new HashSet<String>();
        screen.elements().stream().filter(e -> e.image() != null).forEach(e -> assertTrue(hashes.add(e.image().sha256())));
        assertEquals(5, screen.actionTargets().size());
        assertDoesNotThrow(screen::validateActionAreas);
        for (var element : screen.elements()) {
            assertTrue(element.x()-element.width()/2 >= -1-1e-9 && element.x()+element.width()/2 <= 1+1e-9, element.id());
            assertTrue(element.y()-element.height()/2 >= -1-1e-9 && element.y()+element.height()/2 <= 1+1e-9, element.id());
            if (element.action().startsWith("open:")) assertTrue(RuntimeDemoCatalog.screens().containsKey(element.action().substring(5)),element.action());
        }
        assertEquals(screen, ScreenCodec.decode(ScreenCodec.encode(screen)));
    }

    @Test void everyBoxAndImageLandsOnWholeAtlasPixels() {
        var screen = VisualMenuCatalog.screen();
        for (var element : screen.elements()) {
            double left = (element.x() - element.width() / 2 + 1) * 512, width = element.width() * 512;
            double top = (1 - element.y() - element.height() / 2) * 288, height = element.height() * 288;
            for (double v : new double[]{left, width, top, height}) assertEquals(Math.rint(v), v, 1e-6, element.id());
            if (element.image() != null) {
                assertEquals(Math.round(width) * VisualMenuCatalog.DETAIL, element.image().width(), element.id() + " image is painted at an exact multiple of its 1x size");
                assertEquals(Math.round(height) * VisualMenuCatalog.DETAIL, element.image().height(), element.id());
            }
        }
        for (String id : new String[]{"sequence", "roulette", "hud"}) {
            var card = screen.elements().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
            var art = screen.elements().stream().filter(e -> e.id().equals(id + "_art")).findFirst().orElseThrow();
            assertEquals(card.x(), art.x(), 1e-9);
            assertEquals(screen.elements().stream().filter(e -> e.id().equals("sequence")).findFirst().orElseThrow().y(), card.y(), 1e-9);
        }
    }

    @Test void authoredTextDoesNotClipAndHoverRecoloursTheCardRing() {
        var screen = VisualMenuCatalog.screen();
        for (var element : screen.elements()) {
            if (element.text().isEmpty()) continue;
            int expectedWidth=element.text().length()*6-1;
            assertTrue(expectedWidth <= Math.round(element.width()*128),element.id()+" clips own font");
        }
        var raster=HudRaster.render(screen,null,8);
        assertEquals(1024,raster.getWidth()); assertEquals(576,raster.getHeight());
        assertEquals(255,raster.getRGB(512,160)>>>24);
        var hover=HudRaster.render(screen,"sequence",8);
        // Ring pixel on the left edge of the first card (GUI x=8, y=106).
        assertNotEquals(raster.getRGB(8*4,106*4),hover.getRGB(8*4,106*4),"Hover recolours the card outline");
        assertEquals(raster.getRGB(40*4,96*4),hover.getRGB(40*4,96*4),"Card art is not covered by the hover");
    }

    @Test void completeDemoCollectionFitsNativeGlyphAnd64MiBPackLimits() {
        var screens=new ArrayList<>(RuntimeDemoCatalog.screens().values());
        screens.addAll(Screen.demos().values()); screens.add(VisualMenuCatalog.screen());
        assertEquals(58,screens.size());
        var assets=HudAssets.create(screens);
        assertTrue(assets.entries().values().stream().mapToLong(bytes -> bytes.length).sum()<HudAssets.MAX_PACK_BYTES);
        var screen=VisualMenuCatalog.screen();
        assertEquals(assets.frame(screen,null,8),assets.frame(screen,null,0),"Entrance is the native title fade, not baked frames");
        for(int step=1;step<=8;step++)assertFalse(assets.frame(screen,null,step).isEmpty());
        for(var target:screen.actionTargets())assertFalse(assets.frame(screen,target.id(),8).isEmpty());
    }
}
