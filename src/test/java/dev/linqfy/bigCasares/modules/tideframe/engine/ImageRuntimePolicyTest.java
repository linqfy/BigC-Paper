package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.ImageGlyphs;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.ShapeGlyphs;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageRuntimePolicyTest {
    private static ImageAsset png(int width,int height,int argb) throws Exception {
        var pixels=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels.setRGB(x,y,argb);
        var encoded=new ByteArrayOutputStream();
        assertTrue(ImageIO.write(pixels,"png",encoded));
        return ImageAsset.fromPng(encoded.toByteArray());
    }
    private static Screen.Element element(boolean background,boolean border,ImageAsset image) {
        return new Screen.Element("art","TEXT",.3,-.4,.8,.2,"label","","rounded",border,null,background,image);
    }

    @Test void transparencyEmptiesOnlyTheFillWhileAnEnabledBorderRemainsVisible() {
        var e=element(false,true,null);
        assertEquals("",RenderLayers.fill(e));
        assertEquals(String.valueOf(ShapeGlyphs.forShape("rounded").outline()),RenderLayers.outline(e));
    }
    @Test void anOptedInFillSurvivesWithAnIndependentlyHiddenBorder() {
        var e=element(true,false,null);
        assertEquals(String.valueOf(ShapeGlyphs.forShape("rounded").fill()),RenderLayers.fill(e));
        assertEquals("",RenderLayers.outline(e));
    }
    @Test void absentAndUnregisteredImagesNeverBecomeAnUnknownFontGlyph() throws Exception {
        var image=png(2,1,0x80ff0044);
        assertEquals("",RenderLayers.image(element(false,true,null),ImageGlyphs.empty()));
        assertThrows(IllegalArgumentException.class,() -> RenderLayers.image(element(false,true,image),ImageGlyphs.empty()));
        var registry=ImageGlyphs.create(List.of(image));
        assertEquals(String.valueOf(registry.glyph(image)),RenderLayers.image(element(false,true,image),registry));
    }
    @Test void anUnknownImageCannotPublishOrReopenOverTheExistingScreen() throws Exception {
        var known=png(2,1,0xff003344);var unseen=png(1,2,0xffaa6600);
        var current=new Screen("menu","OLD",List.of(element(false,true,known)));
        var published=new AtomicReference<>(current);var opens=new AtomicInteger();
        var candidate=new Screen("menu","NEW",List.of(element(false,true,known),
            new Screen.Element("newart","",0,0,.2,.3,"label","","rectangle",false,null,false,unseen)));
        var error=assertThrows(IllegalArgumentException.class,() -> ImagePolicy.publish(candidate,
            ImageGlyphs.create(List.of(known)),screen -> {published.set(screen);opens.incrementAndGet();}));
        assertEquals("New images require a server restart.",error.getMessage());
        assertSame(current,published.get());assertEquals(0,opens.get());
    }
    @Test void editsToAKnownImageCanPublishAndReopenWithoutRegeneratingThePack() throws Exception {
        var known=png(2,1,0xff003344);var copy=ImageAsset.fromPng(known.png());
        var changed=new Screen("menu","MOVED",List.of(
            new Screen.Element("art","UPDATED",-.7,.8,.4,.6,"button","close","ellipse",false,null,true,copy)));
        var published=new AtomicReference<Screen>();var opens=new AtomicInteger();
        ImagePolicy.publish(changed,ImageGlyphs.create(List.of(known)),screen -> {published.set(screen);opens.incrementAndGet();});
        assertSame(changed,published.get());assertEquals(1,opens.get());
    }
    @Test void landscapeImagesFillBothNormalizedBoundsUsingTheirActualGlyphAspect() throws Exception {
        var p=RenderLayers.imageGeometry(element(false,true,png(2,1,0xffffffff)));
        assertEquals(.36,p.x(),1e-9);assertEquals(-.28,p.y(),1e-9);
        assertEquals(1.2,p.sx(),1e-9);assertEquals(.35,p.sy(),1e-9);
        // 16 high and 32 wide font pixels, each occupying .025 world units.
        assertEquals(.96,p.sx()*32*.025,1e-9);assertEquals(.14,p.sy()*16*.025,1e-9);
    }
    @Test void portraitImagesUseTheirFullBoundsInsteadOfASquareGlyphTransform() throws Exception {
        var p=RenderLayers.imageGeometry(element(false,true,png(1,2,0xffffffff)));
        assertEquals(4.8,p.sx(),1e-9);assertEquals(.35,p.sy(),1e-9);
        assertEquals(.96,p.sx()*8*.025,1e-9);assertEquals(.14,p.sy()*16*.025,1e-9);
    }
}
