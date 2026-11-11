package dev.linqfy.bigCasares.modules.tideframe;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TideframeUiModuleTest {
    private final UUID java = UUID.randomUUID();
    private final UUID bedrock = UUID.randomUUID();
    private final Set<UUID> withPack = new HashSet<>(Set.of(java, bedrock));
    private final TideframeUiModule module = new TideframeUiModule(withPack::contains, bedrock::equals);

    @Test
    void offByDefaultTheTitlesStayPlain() {
        MenuTitle title = module.title(java, MenuScreen.MISSIONS, "§2Misiones");
        assertFalse(title.drawsBackground());
        assertEquals("§2Misiones", title.text());
    }

    @Test
    void aJavaPlayerWithThePackGetsTheBackgroundAndTheSameText() {
        module.onEnable();
        MenuTitle title = module.title(java, MenuScreen.SHOP_ITEMS, "§6Comida");
        assertTrue(title.drawsBackground());
        assertEquals("§6Comida", title.text(), "the menu's own title is kept");
        assertEquals("" + MenuTitle.BACK_TO_EDGE + MenuScreen.SHOP_ITEMS.glyph() + MenuTitle.BACK_TO_TITLE, title.background());
    }

    @Test
    void withoutThePackOnBedrockOrWhenTurnedOffTheTitlesStayPlain() {
        module.onEnable();
        withPack.remove(java);
        assertFalse(module.title(java, MenuScreen.MISSIONS, "x").drawsBackground(), "pack not loaded: the glyph would be a box");
        assertFalse(module.title(bedrock, MenuScreen.MISSIONS, "x").drawsBackground(), "Bedrock has no Java font");
        withPack.add(java);
        module.onDisable();
        assertFalse(module.title(java, MenuScreen.MISSIONS, "x").drawsBackground());
        assertFalse(module.title((UUID) null, MenuScreen.MISSIONS, "x").drawsBackground());
    }

    @Test
    void theStyleGivenToTheMenusFollowsTheModule() {
        MenuTitleStyle style = module.style();
        assertFalse(style.title(null, MenuScreen.MISSIONS, "x").drawsBackground());
        assertFalse(MenuTitleStyle.PLAIN.title(null, MenuScreen.MISSIONS, "x").drawsBackground());
    }
}
