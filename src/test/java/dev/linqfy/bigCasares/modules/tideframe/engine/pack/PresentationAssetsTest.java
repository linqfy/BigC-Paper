package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class PresentationAssetsTest {
    private static Screen screen(String id,int scale,String cursor) {
        return new Screen(id,"PRESENTATION",List.of(new Screen.Element("canvas","",0,0,2,2,"decoration","","rectangle",false,null,true,null)),RenderQuality.low(),new Screen.Presentation("menu",scale,cursor));
    }
    private record Bitmap(int ascent,int height,int width,int imageHeight,int advance,String path){}
    private static final Pattern PROVIDERS=Pattern.compile("\"file\":\"tideframe:([^\"]+)\",\"ascent\":(-?[0-9]+),\"height\":([0-9]+),\"chars\":\\[\"\\\\u([0-9a-f]{4})\"\\]");
    private static Map<Character,Bitmap> bitmaps(Map<String,byte[]> entries,String font) throws Exception {
        var values=new HashMap<Character,Bitmap>();var providers=PROVIDERS.matcher(font);
        while(providers.find()) {
            String path="assets/tideframe/textures/"+providers.group(1);var image=ImageIO.read(new ByteArrayInputStream(entries.get(path)));int ink=0;
            assertTrue(image.getWidth()<=256 && image.getHeight()<=256);
            for(int x=0;x<image.getWidth();x++)for(int y=0;y<image.getHeight();y++)if((image.getRGB(x,y)>>>24)!=0)ink=Math.max(ink,x+1);
            int ascent=Integer.parseInt(providers.group(2)),height=Integer.parseInt(providers.group(3));assertTrue(ascent<=height);
            values.put((char)Integer.parseInt(providers.group(4),16),new Bitmap(ascent,height,image.getWidth(),image.getHeight(),Math.round(ink*((float)height/image.getHeight()))+1,path));
        }
        return values;
    }
    private static Map<Character,Double> spaces(String font) {
        var values=new HashMap<Character,Double>();var match=Pattern.compile("\"\\\\u([0-9a-f]{4})\":(-?[0-9.]+)").matcher(font);
        while(match.find())values.put((char)Integer.parseInt(match.group(1),16),Double.parseDouble(match.group(2)));return values;
    }
    @Test void scaleMultipliesFootprintCursorOffsetsAndFontMetricsWithZeroAdvance() throws Exception {
        for(int scale=1;scale<=3;scale++) {
            var screen=screen("scaled",scale,"cross");var assets=HudAssets.create(List.of(screen),HudAssets.DEFAULT_SIZES,1.0);var entries=assets.entries();String font=new String(entries.get("assets/tideframe/font/hud.json"),StandardCharsets.UTF_8);
            var bitmap=bitmaps(entries,font);var space=spaces(font);String frame=assets.frame(screen,null,8);
            assertEquals(-32.0*scale,space.get(frame.charAt(0)));var first=bitmap.get(frame.charAt(1));assertEquals(HudAssets.frameAscent(scale),first.ascent);assertEquals(32*scale,first.height);
            double right=0;for(int i=0;i<frame.length();i+=3){var tile=bitmap.get(frame.charAt(i+1));right=Math.max(right,space.get(frame.charAt(i))*4+tile.width*4.0*tile.height/tile.imageHeight);}
            assertEquals(128.0*scale,right,.00001);
            String cursor=assets.cursor(screen,.5,1);assertEquals(15.25*scale,space.get(cursor.charAt(0)),"x .5 plus the 1-quarter left crop of the cross");assertEquals(HudAssets.frameAscent(scale),bitmap.get(cursor.charAt(1)).ascent,"Cursor shares the canvas frame");
            for(String sequence:List.of(frame,cursor,assets.cursor(screen,-1,-1),assets.cursor(screen,1,1))) {double sum=0;for(char c:sequence.toCharArray())sum+=space.containsKey(c)?space.get(c):bitmap.get(c).advance;assertEquals(0,sum,.00001);}
        }
    }
    @Test void internalLegacyResolutionAlsoKeepsCursorAndCanvasScaleTogether() throws Exception {
        var source=screen("legacy",2,"block");
        var screen=new Screen(source.id(),source.title(),source.elements(),RenderQuality.legacy(),source.presentation());
        var assets=HudAssets.create(List.of(screen));var entries=assets.entries();String font=new String(entries.get("assets/tideframe/font/hud.json"),StandardCharsets.UTF_8);
        var bitmap=bitmaps(entries,font);var first=bitmap.get(assets.frame(screen,null,8).charAt(1));assertEquals(32,first.height);assertEquals(32,first.ascent);
        assertThrows(IllegalArgumentException.class,()->dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec.encode(screen),"Externalv6 exports still requireexplicit quality");
    }

    @Test void newCursorFamiliesHaveOriginalShapesAndAreOnlyGeneratedWhenSelected() throws Exception {
        for(String type:List.of("block","text")) {
            var screen=screen(type,1,type);var assets=HudAssets.create(List.of(screen),HudAssets.DEFAULT_SIZES,1.0);var entries=assets.entries();String font=new String(entries.get("assets/tideframe/font/hud.json"),StandardCharsets.UTF_8);var bitmap=bitmaps(entries,font);
            String cursor=assets.cursor(screen,0,0);int painted=0;
            for(int i=0;i<cursor.length();i+=3) {var tile=ImageIO.read(new ByteArrayInputStream(entries.get(bitmap.get(cursor.charAt(i+1)).path)));for(int y=0;y<tile.getHeight();y++)for(int x=0;x<tile.getWidth();x++)if((tile.getRGB(x,y)>>>24)!=0)painted++;}
            assertEquals(type.equals("block")?720:272,painted);
            assertTrue(entries.keySet().stream().anyMatch(path->path.contains("/cursor_"+type+"_")));
            assertFalse(entries.keySet().stream().anyMatch(path->path.contains("/cursor_"+(type.equals("block")?"text":"block")+"_")));
            assertTrue(bitmap.size()<800,"One requested cursor family must not allocate unusedcross variants");
        }
    }
    @Test void cursorFamiliesDeduplicateAcrossScreensButDistinguishScalesAndTypes() throws Exception {
        var a=screen("a",2,"block");var same=screen("same",2,"block");var b=screen("b",3,"text");var assets=HudAssets.create(List.of(a,same,b));
        assertEquals(assets.cursor(a,.2,.3),assets.cursor(same,.2,.3));assertNotEquals(assets.cursor(a,.2,.3),assets.cursor(b,.2,.3));
        String font=new String(assets.entries().get("assets/tideframe/font/hud.json"),StandardCharsets.UTF_8);int count=bitmaps(assets.entries(),font).size();assertTrue(count<1500,"Two usedfamilies fit well below4352providers");
        System.out.println("v0.8 two-family glyph providers="+count+", pack entries="+assets.entries().size());
    }

    @Test void theDefaultCursorIsMuchSmallerThanTheOriginalDesign() throws Exception {
        for(String type:List.of("arrow","cross","block")) {
            var screen=screen(type,1,type);
            int big=paintedCursorPixels(HudAssets.create(List.of(screen),HudAssets.DEFAULT_SIZES,1.0),screen);
            int small=paintedCursorPixels(HudAssets.create(List.of(screen)),screen);
            assertTrue(small>0 && small*3<big,type+": default "+small+" vs original "+big);
        }
        assertThrows(IllegalArgumentException.class,()->HudAssets.create(List.of(screen("x",1,"arrow")),HudAssets.DEFAULT_SIZES,2.0));
    }
    private static int paintedCursorPixels(HudAssets assets,Screen screen) throws Exception {
        var entries=assets.entries();String font=new String(entries.get("assets/tideframe/font/hud.json"),StandardCharsets.UTF_8);var bitmap=bitmaps(entries,font);
        String cursor=assets.cursor(screen,0,0);int painted=0;
        for(int i=0;i<cursor.length();i+=3) {var tile=ImageIO.read(new ByteArrayInputStream(entries.get(bitmap.get(cursor.charAt(i+1)).path)));for(int y=0;y<tile.getHeight();y++)for(int x=0;x<tile.getWidth();x++)if((tile.getRGB(x,y)>>>24)>16)painted++;}
        return painted;
    }
}
