package dev.linqfy.bigCasares.modules.tideframe.engine.pack;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BundledBossPortraitTest {
    @Test void actualBundledPngIsReadableHasAlphaAndChangesTheFaceOnly() throws Exception {
        byte[] source;
        try(var resource=dev.linqfy.bigCasares.modules.tideframe.engine.TideframeResources.open("boss-face.png")) {
            assertNotNull(resource);source=resource.readAllBytes();
        }
        var portrait=ImageAsset.fromPng(source);
        assertTrue(portrait.width()>=512&&portrait.height()>=512);
        var image=ImageIO.read(new ByteArrayInputStream(portrait.png()));
        assertEquals(0,image.getRGB(0,0)>>>24);
        assertTrue((image.getRGB(image.getWidth()/2,image.getHeight()/2)>>>24)>200);
        var original=BossBarAssets.create().entries();var imported=BossBarAssets.create(portrait).entries();
        assertArrayEquals(original.get("assets/tideframe/font/boss.json"),imported.get("assets/tideframe/font/boss.json"));
        assertFalse(java.util.Arrays.equals(original.get("assets/tideframe/textures/font/boss/frame_15_0.png"),imported.get("assets/tideframe/textures/font/boss/frame_15_0.png")));
        assertArrayEquals(original.get("assets/tideframe/textures/font/boss/frame_15_1.png"),imported.get("assets/tideframe/textures/font/boss/frame_15_1.png"));
    }
}
