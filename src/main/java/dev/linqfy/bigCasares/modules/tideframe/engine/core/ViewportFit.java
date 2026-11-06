package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.util.List;
import java.util.Locale;

/**
 * Per-connection display size. Vanilla Java never reports the window or GUI scale to the
 * server, so this is either a size the player picked ({@code fixedQuarters}: 4 = 1x, 7 = 1.75x)
 * or one derived from the window/GUI scale the player typed. Geometry follows the client's title
 * renderer: the canvas is 256*S x 144*S GUI pixels, horizontally centred, vertically placed by
 * {@link HudAssets#canvasTopGuiQuarters(int)}. Sizes are chosen from those the pack has fonts for.
 */
public record ViewportFit(int width,int height,int guiScale,int fixedQuarters) {
    public ViewportFit {
        if(fixedQuarters!=0) {
            if(fixedQuarters<4||fixedQuarters>16) throw new IllegalArgumentException("Menu size must be between 1 and 4.");
            if(width!=0||height!=0||guiScale!=0) throw new IllegalArgumentException("A fixed menu size has no window dimensions.");
        } else if(width<320||width>16384||height<240||height>16384||guiScale<0||guiScale>16)
            throw new IllegalArgumentException("Use actual window width/height and GUI scale 0 (Auto) to 16.");
    }
    public ViewportFit(int width,int height,int guiScale) { this(width,height,guiScale,0); }
    public static ViewportFit fixed(int quarters) { return new ViewportFit(0,0,0,quarters); }
    /** Parses "1.75", "1,75", "2" or "175%" into quarters. */
    public static int parseSize(String text) {
        String value=text.trim().replace(',','.');
        double size=value.endsWith("%")?Double.parseDouble(value.substring(0,value.length()-1))/100:Double.parseDouble(value);
        if(!Double.isFinite(size)) throw new IllegalArgumentException("Invalid size");
        int quarters=(int)Math.round(size*4);
        if(quarters<4||quarters>16||Math.abs(quarters/4.0-size)>.01) throw new IllegalArgumentException("Size must be 1 to 4 in steps of 0.25.");
        return quarters;
    }
    public static String label(int quarters) {
        double value=quarters/4.0;
        return value==Math.rint(value)?String.valueOf((int)value):String.format(Locale.ROOT,"%s",value);
    }

    /** Vanilla Window.calculateScale: the largest scale keeping at least 320x240 GUI pixels. */
    public int effectiveGuiScale() {
        if(fixedQuarters!=0) throw new IllegalStateException("A fixed menu size has no GUI scale.");
        int maximum=1;
        while(maximum<width && maximum<height && width/(maximum+1)>=320 && height/(maximum+1)>=240) maximum++;
        return guiScale==0?maximum:Math.min(guiScale,maximum);
    }
    /** True when the whole canvas at a total size (quarters) is on screen. */
    public static boolean fits(int guiWidth,int guiHeight,int totalQuarters) {
        int top=guiHeight/2+HudAssets.canvasTopGuiQuarters(totalQuarters);
        return 64*totalQuarters<=guiWidth && top>=0 && top+36*totalQuarters<=guiHeight;
    }
    /** True when the canvas at a total size (quarters) covers the whole screen (edges may fall off, like a cover photo). */
    public static boolean covers(int guiWidth,int guiHeight,int totalQuarters) {
        int top=guiHeight/2+HudAssets.canvasTopGuiQuarters(totalQuarters);
        return 64*totalQuarters>=guiWidth && top<=0 && top+36*totalQuarters>=guiHeight;
    }
    /** Canvas rectangle (logical 256x144 pixels) that every menu keeps its text and buttons in. */
    public static final int SAFE_LEFT = 12, SAFE_RIGHT = 244, SAFE_TOP = 8, SAFE_BOTTOM = 133;
    /** True when the whole safe area of the canvas at a total size (quarters) is on screen. */
    public static boolean safeAreaVisible(int guiWidth,int guiHeight,int totalQuarters) {
        double unit=totalQuarters/4.0, left=(guiWidth-64.0*totalQuarters)/2, top=guiHeight/2.0+HudAssets.canvasTopGuiQuarters(totalQuarters);
        return left+SAFE_LEFT*unit>=0 && left+SAFE_RIGHT*unit<=guiWidth && top+SAFE_TOP*unit>=0 && top+SAFE_BOTTOM*unit<=guiHeight;
    }
    /**
     * Display size (quarters) applied on top of the screen's own presentation scale, from the pack's sizes.
     * A typed window gets the largest size whose safe area is fully visible: on the common screens that size covers
     * the whole window (only the frame's outer edge falls off); when even 1x hides part of it, 1x.
     */
    public int multiplier(Screen screen,List<Integer> available) {
        int base=screen.presentation().scale();
        if(fixedQuarters!=0) {
            // Largest pack size not above the requested total; a 2x/3x authored layout never shrinks below its own size.
            int best=4;
            for(int q:available) if(q>best && base*q<=fixedQuarters) best=q;
            return best;
        }
        int g=effectiveGuiScale();
        int w=(width+g-1)/g,h=(height+g-1)/g, best=4;
        for(int q:available) if(q>best && safeAreaVisible(w,h,base*q)) best=q;
        return best;
    }
    public String encode() { return fixedQuarters!=0?"q:"+fixedQuarters:"fit:"+width+":"+height+":"+guiScale; }
    public static ViewportFit decode(String value) {
        if(value==null) throw new IllegalArgumentException("Missing display size");
        String[] parts=value.split(":");
        if(parts.length==2&&parts[0].equals("q")) return fixed(Integer.parseInt(parts[1]));
        if(parts.length==2&&parts[0].equals("size")) return fixed(Integer.parseInt(parts[1])*4);
        if(parts.length==4&&parts[0].equals("fit")) return new ViewportFit(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]));
        throw new IllegalArgumentException("Unknown display size "+value);
    }
    public static String font(int quarters) {
        if(quarters<4||quarters>16)throw new IllegalArgumentException("Invalid display size");
        return HudAssets.fontName(quarters);
    }
}
