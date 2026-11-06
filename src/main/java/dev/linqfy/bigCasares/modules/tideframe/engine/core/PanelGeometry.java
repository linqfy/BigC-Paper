package dev.linqfy.bigCasares.modules.tideframe.engine.core;
public final class PanelGeometry {
    public record Transform(double x,double y,double sx,double sy){}
    public static Transform panel(double x,double y,double width,double height){
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(width)||!Double.isFinite(height)||width<=0||height<=0)
            throw new IllegalArgumentException("Panel bounds must be finite and positive");
        // Own glyph is 16 px square; display rendering uses 0.025 world units per pixel.
        return new Transform(x*1.2,y*.7,width*3,height*1.75);
    }
}
