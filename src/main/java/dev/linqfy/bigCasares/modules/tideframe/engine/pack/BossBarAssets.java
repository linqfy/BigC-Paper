package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Original rose-and-gold boss frame with an optional operator-provided PNG face.
 * The built-in guardian is original; custom pixels retain their source owner's
 * rights and require an appropriate license. No remote assets are fetched here.
 * Decode and build the immutable registry on the resource-pack worker.
 * The 256-by-40 GUI footprint follows vanilla GUI scale, not physical pixels.
 * Glyph bearing is calculated for the native boss title; client overlap still
 * requires visual acceptance for each supported Minecraft version.
 */
public final class BossBarAssets {
    private static final int WIDTH=1024,HEIGHT=160,GUI_WIDTH=256,TILE_COUNT=4;
    private static final int GLYPH_START=0xf000,FLASH_START=0xf060,SPACE_START=0xe800,SPACE_MIN=-512,SPACE_MAX=512;
    private final Map<String,byte[]> entries;
    private final Map<Character,Integer> advances;
    private final String[] frames,flashes;
    private static final class Holder { private static final BossBarAssets INSTANCE=build(); }

    private BossBarAssets(Map<String,byte[]> entries,Map<Character,Integer> advances,String[] frames,String[] flashes) {
        this.entries=Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.advances=Map.copyOf(advances);this.frames=frames.clone();this.flashes=flashes.clone();
    }

    /**
     * Frame designs (config.yml boss-bar.style), all original and drawn from the same shapes, so every design has the
     * same glyphs and advances: rose (the rose crystal and gold of earlier versions), frost (ice and silver) and ember
     * (embers in dark iron).
     */
    public static final java.util.List<String> STYLES=java.util.List.of("rose","frost","ember");
    /** Every colour the frame painter uses; {@code face} is the built-in guardian (skin, shade, light, eye, cheeks, crest). */
    private record Palette(Color backing,int outline,Color bevelTop,Color bevelBottom,Color highlight,Color lowerEdge,Color groove,
                           Color trackTop,Color trackBottom,Color fillTop,Color fillBottom,Color fillShine,Color fillShade,Color edge,Color edgeGlow,
                           Color divider,int stud,int tip,int tipShine,Color medalTop,Color medalBottom,int seat,int[] face) { }
    private static final Palette ROSE=new Palette(new Color(0x0f1119),0x15101b,new Color(0xf7e6ae),new Color(0x9a6a2d),new Color(255,248,220,150),
            new Color(0x6b4420),new Color(0x1a1020),new Color(0x40203a),new Color(0x1f0f1f),new Color(0xffa8d1),new Color(0xc42a73),
            new Color(255,226,240,170),new Color(0x8f1d55),new Color(255,236,246,235),new Color(255,150,200,90),new Color(20,8,18,150),
            0xffe9ad,0xf6a3cb,0xffe3f0,new Color(0xe0b862),new Color(0xb88535),0x182532,new int[]{0x637a8c,0x273b50,0xadb5b4,0xff91bf,0x8be0d6,0xe7c57d});
    private static final Palette FROST=new Palette(new Color(0x0d1420),0x0f1a26,new Color(0xf2f8ff),new Color(0x7f93ab),new Color(240,250,255,150),
            new Color(0x4d5f75),new Color(0x101b29),new Color(0x1f3550),new Color(0x0f1c2c),new Color(0xbff3ff),new Color(0x2f8fdc),
            new Color(230,250,255,170),new Color(0x1d5f9a),new Color(240,252,255,235),new Color(150,220,255,90),new Color(8,14,24,150),
            0xe8f6ff,0x9fe3ff,0xeefcff,new Color(0xd4e2ee),new Color(0x8ea3b8),0x14202e,new int[]{0x6f8aa0,0x24394f,0xc4d2dc,0x8fdcff,0xb8f0ff,0xdfe9f2});
    private static final Palette EMBER=new Palette(new Color(0x140d0b),0x120a08,new Color(0xb9b2ad),new Color(0x4a4442),new Color(235,228,220,140),
            new Color(0x2e2826),new Color(0x1a0e0a),new Color(0x3d1a10),new Color(0x1f0c07),new Color(0xffd36b),new Color(0xd8431f),
            new Color(255,240,200,170),new Color(0x9a2a12),new Color(255,246,220,235),new Color(255,170,80,90),new Color(24,10,6,150),
            0xffb35c,0xff8a3c,0xffe0a8,new Color(0x8e8782),new Color(0x5b5450),0x1c1412,new int[]{0x7a6a62,0x3a2a24,0xc9bdb4,0xff9a3c,0xffc46b,0xffd88a});
    private static Palette palette(String style) {
        return switch(style){case "frost"->FROST;case "ember"->EMBER;case "rose"->ROSE;default->throw new IllegalArgumentException("Boss bar style must be "+STYLES);};
    }

    public static BossBarAssets create() { return Holder.INSTANCE; }

    /** Decode one normalized portrait for all frames, on a resource-pack worker. */
    public static BossBarAssets create(ImageAsset portrait) { return create(portrait,"rose"); }
    /** As {@link #create(ImageAsset)} in frame design {@code style} (one of {@link #STYLES}). */
    public static BossBarAssets create(ImageAsset portrait,String style) {
        // Runtime frame/title selection uses these shared layouts too. Warm their
        // PNG registry here on the pack worker, never at the player's first boss tick.
        BossBarAssets standard=create();
        var palette=palette(style);
        return portrait==null&&palette==ROSE?standard:build(decode(portrait),palette);
    }

    public Map<String,byte[]> entries() {
        var result=new LinkedHashMap<String,byte[]>();
        entries.forEach((name,bytes)->result.put(name,bytes.clone()));
        return Collections.unmodifiableMap(result);
    }

    /** Nearest five-percent visual frame. The supplied native health stays exact. */
    public static int frameIndex(float progress) {
        if(!Float.isFinite(progress)||progress<0||progress>1)
            throw new IllegalArgumentException("Boss progress must be finite and between 0 and 1.");
        return Math.round(progress*20);
    }

    /** Four frame tiles, with a stable 256 GUI-unit advance at every health. */
    public static String frame(float progress) { int frame=frameIndex(progress);return create().frames[frame]; }

    /**
     * As {@link #title(String, float)}, during a hit: {@code flash} lights the health fill white and {@code shake} moves
     * the whole bar sideways by that many GUI pixels (the advance stays 256, so the bar does not recentre). Right is
     * kept to 1 pixel: the plate leaves only that margin over the native bar under it.
     */
    public static String title(String label,float progress,int shake,boolean flash) {
        if(shake>1||shake<-8)throw new IllegalArgumentException("Boss shake must be -8 to 1 GUI pixels");
        String plain=title(label,progress);
        if(!flash&&shake==0)return plain;
        String frame=frame(progress),lit=flash?space(-GUI_WIDTH)+create().flashes[frameIndex(progress)]:"";
        return (shake==0?"":String.valueOf(space(shake)))+frame+lit+plain.substring(frame.length())+(shake==0?"":String.valueOf(space(-shake)));
    }

    /** Adds a bounded English ASCII heading above the artwork without recentering it. */
    public static String title(String label,float progress) {
        if(label==null)throw new IllegalArgumentException("Boss label must be specified");
        int width=0;
        for(char character:label.toCharArray())width+=asciiAdvance(character);
        if(width>GUI_WIDTH)throw new IllegalArgumentException("Boss heading exceeds 256 GUI units");
        int left=(GUI_WIDTH-width)/2;
        return frame(progress)+space(-GUI_WIDTH+left)+label+space(GUI_WIDTH-left-width);
    }

    /** Actual advances used by this registry; useful for pack/metric validation. */
    public int advance(String sequence) {
        int result=0;
        for(char character:sequence.toCharArray()) {
            Integer mapped=advances.get(character);
            result+=mapped==null?asciiAdvance(character):mapped;
        }
        return result;
    }

    private static int asciiAdvance(char character) {
        if(character<32||character>126)throw new IllegalArgumentException("Boss label must use English ASCII");
        if(character==' ')return 4;
        String[] rows=PackGenerator.pattern(character).split("/");
        int width=0;
        for(String row:rows)for(int x=0;x<row.length();x++)if(row.charAt(x)=='1')width=Math.max(width,x+1);
        return width+1;
    }

    private static char space(int advance) {
        if(advance<SPACE_MIN||advance>SPACE_MAX)throw new IllegalArgumentException("Boss spacing exceeds bounds");
        return (char)(SPACE_START+advance-SPACE_MIN);
    }

    static BossBarAssets build() {
        return build(null,ROSE);
    }

    private static BossBarAssets build(BufferedImage portrait,Palette palette) {
        var entries=new LinkedHashMap<String,byte[]>();var advances=new LinkedHashMap<Character,Integer>();
        var providers=new StringBuilder("{\"providers\":[{\"type\":\"space\",\"advances\":{");
        for(int value=SPACE_MIN;value<=SPACE_MAX;value++) {
            if(value!=SPACE_MIN)providers.append(',');
            char character=space(value);advances.put(character,value);
            providers.append('"').append(escape(character)).append("\":").append(value);
        }
        providers.append("}},{\"type\":\"reference\",\"id\":\"tideframe:ui\"}");
        var frames=new String[21];
        for(int frame=0;frame<=20;frame++) {
            var raster=previewRaster(frame/20f,portrait,palette);var sequence=new StringBuilder();
            for(int tile=0;tile<TILE_COUNT;tile++) {
                var image=raster.getSubimage(tile*256,0,256,HEIGHT);
                String path="font/boss/frame_"+frame+"_"+tile+".png";
                entries.put("assets/tideframe/textures/"+path,png(image));
                char character=(char)(GLYPH_START+frame*TILE_COUNT+tile);
                int inkWidth=0;
                for(int x=0;x<image.getWidth();x++)for(int y=0;y<image.getHeight();y++)
                    if((image.getRGB(x,y)>>>24)!=0)inkWidth=Math.max(inkWidth,x+1);
                int advance=Math.round(inkWidth*.25f)+1;advances.put(character,advance);
                sequence.append(character).append(space(64-advance));
                providers.append(",{\"type\":\"bitmap\",\"file\":\"tideframe:").append(path)
                    .append("\",\"ascent\":2,\"height\":40,\"chars\":[\"").append(escape(character)).append("\"]}");
            }
            frames[frame]=sequence.toString();
        }
        // The flash of a hit: white over each frame's health fill, in the frame's own tiles and places (empty tiles are spaces).
        var flashes=new String[21];
        for(int frame=0;frame<=20;frame++) {
            var raster=flashRaster(frame);var sequence=new StringBuilder();
            for(int tile=0;tile<TILE_COUNT;tile++) {
                var image=raster.getSubimage(tile*256,0,256,HEIGHT);
                int inkWidth=0;
                for(int x=0;x<image.getWidth();x++)for(int y=0;y<image.getHeight();y++)
                    if((image.getRGB(x,y)>>>24)!=0)inkWidth=Math.max(inkWidth,x+1);
                if(inkWidth==0){sequence.append(space(64));continue;}
                String path="font/boss/flash_"+frame+"_"+tile+".png";
                entries.put("assets/tideframe/textures/"+path,png(image));
                char character=(char)(FLASH_START+frame*TILE_COUNT+tile);
                int advance=Math.round(inkWidth*.25f)+1;advances.put(character,advance);
                sequence.append(character).append(space(64-advance));
                providers.append(",{\"type\":\"bitmap\",\"file\":\"tideframe:").append(path)
                    .append("\",\"ascent\":2,\"height\":40,\"chars\":[\"").append(escape(character)).append("\"]}");
            }
            flashes[frame]=sequence.toString();
        }
        entries.put("assets/tideframe/font/boss.json",providers.append("]}\n").toString().getBytes(StandardCharsets.UTF_8));
        return new BossBarAssets(entries,advances,frames,flashes);
    }
    /** The white flash over frame {@code frame}'s health fill (the fill's own rectangle). */
    private static BufferedImage flashRaster(int frame) {
        var image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try {
            g.scale(4,4);
            int filled=(int)Math.round(204*frame/20.0);
            if(filled>0){g.setColor(new Color(255,255,255,205));g.fillRect(41,8,filled,7);g.setColor(new Color(255,255,255,120));g.fillRect(41,7,filled,1);g.fillRect(41,15,filled,1);}
        } finally { g.dispose(); }
        return image;
    }

    private static String escape(char character) { return String.format(java.util.Locale.ROOT,"\\u%04x",(int)character); }
    private static byte[] png(BufferedImage image) {
        try(var output=new ByteArrayOutputStream()) {
            if(!ImageIO.write(image,"PNG",output))throw new IllegalStateException("PNG encoder unavailable");
            return output.toByteArray();
        } catch(IOException error) { throw new IllegalStateException("Unable to encode boss artwork",error); }
    }

    /** Standalone original artwork preview, not a captured Minecraft screenshot. */
    public static BufferedImage preview(float progress) {
        return previewRaster(progress,null,ROSE);
    }

    /** Optional source-licensed face inside the original frame; worker only. */
    public static BufferedImage preview(float progress,ImageAsset portrait) {
        frameIndex(progress);
        return previewRaster(progress,decode(portrait),ROSE);
    }
    /** The frame in design {@code style}, with the flash of a hit over it when {@code flash}; worker only. */
    public static BufferedImage preview(float progress,ImageAsset portrait,String style,boolean flash) {
        frameIndex(progress);
        var image=previewRaster(progress,decode(portrait),palette(style));
        if(flash){var g=image.createGraphics();try{g.drawImage(flashRaster(frameIndex(progress)),0,0,null);}finally{g.dispose();}}
        return image;
    }

    private static BufferedImage decode(ImageAsset portrait) {
        if(portrait==null)return null;
        try(var input=new ByteArrayInputStream(portrait.png())) {
            var decoded=ImageIO.read(input);
            if(decoded==null)throw new IOException("PNG decoder unavailable");
            return decoded;
        } catch(IOException error) { throw new IllegalStateException("Unable to decode normalized boss portrait",error); }
    }

    private static BufferedImage previewRaster(float progress,BufferedImage portrait,Palette p) {
        int frame=frameIndex(progress);
        var image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(4,4);
            // Opaque backing covers the native 182-by-5 bar under this glyph.
            g.setColor(p.backing());g.fillRect(36,3,218,17);
            // Frame: dark outline, metal bevel with a top highlight, then a dark groove holding the track.
            polygon(g,p.outline(),33,1,248,1,255,8,255,15,248,22,33,22,27,15,27,8);
            polygonPaint(g,new GradientPaint(0,2,p.bevelTop(),0,21,p.bevelBottom()),34,2,247,2,254,9,254,14,247,21,34,21,28,15,28,9);
            g.setColor(p.highlight());g.fillRect(36,2,210,1);
            g.setColor(p.lowerEdge());g.fillRect(36,20,210,1);
            g.setColor(p.groove());g.fillRect(38,6,210,11);
            g.setPaint(new GradientPaint(0,7,p.trackTop(),0,16,p.trackBottom()));g.fillRect(40,7,206,9);
            int filled=(int)Math.round(204*frame/20.0);
            if(filled>0) {
                g.setPaint(new GradientPaint(0,8,p.fillTop(),0,15,p.fillBottom()));
                g.fillRect(41,8,filled,7);
                g.setColor(p.fillShine());g.fillRect(41,8,filled,1);
                g.setColor(p.fillShade());g.fillRect(41,14,filled,1);
                // A bright leading edge shows where the health ends, even on a small screen.
                if(filled<204){g.setColor(p.edge());g.fillRect(41+filled-1,8,1,7);
                    g.setColor(p.edgeGlow());g.fillRect(41+filled-3,8,2,7);}
            }
            // Fifths: a dark line with a light edge, over the fill and the empty track alike.
            for(int k=1;k<5;k++) {
                int x=41+(int)Math.round(204*k/5.0);
                g.setColor(p.divider());g.fillRect(x,8,1,7);
                g.setColor(new Color(255,255,255,26));g.fillRect(x+1,8,1,7);
                stud(g,x,4,p);stud(g,x,19,p);
            }
            // Right tip: a crystal set in the metal point.
            polygon(g,p.outline(),247,9,253,12,247,15,244,12);
            polygon(g,p.tip(),247,10,251,12,247,14,245,12);
            polygon(g,p.tipShine(),246,11,247,10,248,11,247,12);
            // The medallion: a soft shadow, dark outline, metal bevel and a dark seat for the face.
            g.setColor(new Color(0,0,0,110));g.fill(new Polygon(new int[]{4,13,30,41,44,33,14,3,1},new int[]{10,3,3,11,27,40,42,33,19},9));
            polygon(g,p.outline(),3,8,12,1,29,1,40,9,43,25,32,37,13,39,3,30,0,17);
            polygonPaint(g,new GradientPaint(0,1,p.bevelTop(),0,38,p.bevelBottom()),5,9,13,3,28,3,38,10,41,24,31,35,14,37,5,29,2,17);
            polygonPaint(g,new GradientPaint(0,3,p.medalTop(),0,36,p.medalBottom()),6,10,14,4,27,4,37,11,39,24,30,33,15,35,7,28,4,17);
            polygon(g,p.seat(),8,12,16,7,26,7,34,13,36,24,28,31,16,32,10,26,7,18);
            if(portrait==null) {
                int[] face=p.face();
                polygon(g,face[0],10,11,14,5,20,12,27,5,32,12,31,23,24,30,17,29,11,22);
                polygon(g,face[1],14,14,20,18,28,13,29,22,23,28,18,27,13,22);
                polygon(g,face[2],13,12,18,15,15,17,12,15);
                polygon(g,face[3],23,15,29,11,28,16,24,18);
                polygon(g,face[4],13,18,18,19,17,21,14,21);
                polygon(g,face[4],24,19,29,17,27,21,24,22);
                polygon(g,face[5],18,23,22,24,25,22,24,26,21,28,18,26);
            } else drawPortrait(g,portrait);
        } finally { g.dispose(); }
        return image;
    }

    /** Fit without stretching, clipping only to the interior of our medallion. */
    private static void drawPortrait(Graphics2D graphics,BufferedImage portrait) {
        var previousClip=graphics.getClip();
        graphics.clip(new Polygon(new int[]{8,16,26,34,36,28,16,10,7},
                new int[]{12,7,7,13,24,31,32,26,18},9));
        double scale=Math.min(29.0/portrait.getWidth(),25.0/portrait.getHeight());
        double x=7+(29-portrait.getWidth()*scale)/2;
        double y=7+(25-portrait.getHeight()*scale)/2;
        var transform=AffineTransform.getTranslateInstance(x,y);
        transform.scale(scale,scale);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(portrait,transform,null);
        graphics.setClip(previousClip);
    }

    /** A small diamond set in the frame's rim; {@code (cx, cy)} is its centre. */
    private static void stud(Graphics2D graphics,int cx,int cy,Palette p) {
        polygon(graphics,p.outline(),cx,cy-2,cx+3,cy,cx,cy+2,cx-3,cy);
        polygon(graphics,p.stud(),cx,cy-1,cx+2,cy,cx,cy+1,cx-2,cy);
    }
    private static void polygonPaint(Graphics2D graphics,java.awt.Paint paint,int... coordinates) {
        int[] x=new int[coordinates.length/2],y=new int[x.length];
        for(int index=0;index<x.length;index++){x[index]=coordinates[index*2];y[index]=coordinates[index*2+1];}
        graphics.setPaint(paint);graphics.fill(new Polygon(x,y,x.length));
    }
    private static void polygon(Graphics2D graphics,int rgb,int... coordinates) {
        int[] x=new int[coordinates.length/2],y=new int[x.length];
        for(int index=0;index<x.length;index++){x[index]=coordinates[index*2];y[index]=coordinates[index*2+1];}
        graphics.setColor(new Color(rgb));graphics.fill(new Polygon(x,y,x.length));
    }
}
