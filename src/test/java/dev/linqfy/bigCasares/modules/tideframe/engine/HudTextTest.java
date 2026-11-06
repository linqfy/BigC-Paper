package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.util.List;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudTextTest {
    @Test void composingMenuAndOverlayPreservesTheirIndependentFonts() {
        var screen=new Screen("test","TEST",List.of());var assets=HudAssets.create(List.of(screen));
        String glyphs=assets.cursor(screen,.25,.5);
        var overlay=HudText.layer(assets,glyphs,4);
        var menu=HudText.layer(assets,glyphs,7);
        var combined=overlay.append(menu);
        assertEquals("tideframe:hud",combined.font().asString());
        var child=(TextComponent)combined.children().getFirst();
        assertEquals("tideframe:hud_q7",child.font().asString());
        assertEquals(glyphs,((TextComponent)combined).content());
        assertEquals(assets.displayText(glyphs,7),child.content());
    }
}
