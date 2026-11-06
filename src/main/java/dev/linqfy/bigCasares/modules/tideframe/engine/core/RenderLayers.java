package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.pack.ImageGlyphs;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.ShapeGlyphs;
import java.util.Objects;

/** Pure layer content and geometry shared by renderer state and regression tests. */
public final class RenderLayers {
    private RenderLayers() {}

    public static String fill(Screen.Element element) {
        return element.background()?String.valueOf(ShapeGlyphs.forShape(element.shape()).fill()):"";
    }
    public static String outline(Screen.Element element) {
        return element.border()?String.valueOf(ShapeGlyphs.forShape(element.shape()).outline()):"";
    }
    public static String image(Screen.Element element,ImageGlyphs images) {
        if(element.image()==null)return "";
        if(!images.contains(element.image()))throw new IllegalArgumentException(ImagePolicy.RESTART_REQUIRED);
        return String.valueOf(images.glyph(element.image()));
    }
    public static PanelGeometry.Transform imageGeometry(Screen.Element element) {
        return imageGeometry(element.x(),element.y(),element.width(),element.height(),element.image());
    }
    public static PanelGeometry.Transform imageGeometry(double x,double y,double width,double height,ImageAsset image) {
        Objects.requireNonNull(image,"image");
        var panel=PanelGeometry.panel(x,y,width,height);
        // PNG providers are 16 font pixels high and 16 * width / height pixels wide.
        // Correct only the horizontal scale; both visible bounds then match the element.
        return new PanelGeometry.Transform(panel.x(),panel.y(),panel.sx()*image.height()/image.width(),panel.sy());
    }
}
