package dev.linqfy.bigCasares.modules.tideframe.engine;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;

class SchemaThreeTest {
    @TempDir Path dir;
    @Test void newElementsAreTransparentWithoutHidingBorders() {
        var e=new Screen.Element("a","A",0,0,1,.2,"label","");
        assertFalse(e.background());assertTrue(e.border());assertNull(e.image());
        String json=ScreenCodec.encode(new Screen("menu","MENU",List.of(e)));
        assertTrue(json.contains("\"version\":3"));assertTrue(json.contains("\"background\":false"));
        assertEquals(e,ScreenCodec.decode(json).elements().getFirst());
    }
    @Test void oldSchemasMigrateToTransparentBackgrounds() {
        String one="{\"version\":1,\"id\":\"menu\",\"title\":\"MENU\",\"elements\":[{\"id\":\"a\",\"text\":\"A\",\"x\":0,\"y\":0,\"width\":1,\"height\":0.2,\"kind\":\"label\",\"action\":\"\"}]}";
        assertFalse(ScreenCodec.decode(one).elements().getFirst().background());
        String two=one.replace("\"version\":1","\"version\":2").replace("\"action\":\"\"","\"action\":\"\",\"shape\":\"ellipse\",\"border\":true,\"messageCondition\":null");
        var old=ScreenCodec.decode(two).elements().getFirst();assertFalse(old.background());assertEquals("ellipse",old.shape());
    }
    @Test void backgroundAndImagesAreStrictSchemaFields() {
        var e=new Screen.Element("a","A",0,0,1,.2,"button","close","rounded",false,null,true,null);
        String json=ScreenCodec.encode(new Screen("menu","MENU",List.of(e)));
        assertTrue(ScreenCodec.decode(json).elements().getFirst().background());
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(json.replace("\"background\":true","\"background\":\"true\"")));
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(json.replace("\"image\":null","\"image\":\"https://example.invalid/image.png\"")));
        assertThrows(IllegalArgumentException.class,()->ScreenCodec.decode(json.replace("\"version\":3","\"version\":3.0000000000000000001")));
    }
    @Test void movingPreservesExplicitBackground() {
        var e=new Screen.Element("a","A",0,0,1,.2,"button","close","ellipse",true,null,true,null);
        assertTrue(e.moved(.2,.3).background());assertEquals(e.image(),e.moved(.2,.3).image());
    }
    @Test void pngPixelsAndAlphaSurviveSchemaStorageAndMovement() throws Exception {
        var image=ImageAsset.fromPng(PngFixtures.rgba(2,1,0x00aabbcc,0x8066aaff));
        var original=new Screen.Element("art","",.2,-.3,.8,.5,"button","close","rectangle",true,null,false,image);
        var screen=new Screen("image_demo","IMAGES",List.of(original));
        assertEquals(screen,ScreenCodec.decode(ScreenCodec.encode(screen)));
        var store=new ScreenStore(dir);store.save(screen);assertEquals(screen,store.load("image_demo"));
        assertEquals(image,original.moved(-.4,.7).image());assertFalse(original.moved(-.4,.7).background());
    }
    @Test void imagesBeyondTheOldStringAndFileLimitLoadAndLegacyElementBoundsKeepSavesAtomic() throws Exception {
        int[] pixels=new int[1024*384];var random=new Random(42);for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt();
        var image=ImageAsset.fromPng(PngFixtures.rgba(1024,384,pixels));
        var art=new Screen.Element("art","",0,0,1,1,"label","","rectangle",true,null,false,image);
        var screen=new Screen("menu","LARGE IMAGE",List.of(art));
        String json=ScreenCodec.encode(screen);assertTrue(json.length()>65536);assertEquals(screen,ScreenCodec.decode(json));
        var store=new ScreenStore(dir);store.save(screen);
        var tooManyBytes=new ArrayList<Screen.Element>();
        for(int i=0;i<25;i++)tooManyBytes.add(new Screen.Element("art"+i,"",0,0,1,1,"label","","rectangle",true,null,false,image));
        assertThrows(IllegalArgumentException.class,()->store.save(new Screen("menu","TOO LARGE",tooManyBytes)));
        assertEquals(screen,store.load("menu"));
    }
    @Test void storageListsValidatedIdsDeterministicallyAndBoundsTheLibrary() throws Exception {
        var store=new ScreenStore(dir);store.save(Screen.demos().get("profile"));store.save(Screen.demos().get("menu"));
        Files.writeString(dir.resolve(".screen-temporary.tmp"),"ignored");
        assertEquals(List.of("menu","profile"),store.ids());
        for(int i=0;i<95;i++)Files.writeString(dir.resolve("s"+i+".json"),"{}");
        assertThrows(IllegalArgumentException.class,store::ids);
    }
}
