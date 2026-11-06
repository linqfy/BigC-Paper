package dev.linqfy.bigCasares.modules.tideframe.engine;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.GlyphColor;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Every title layer carries its own font metrics, including when composed. */
final class HudText {
    private HudText() { }
    static Component layer(HudAssets assets,String glyphs,int multiplier) {
        return Component.text(assets.displayText(glyphs,multiplier)).font(Key.key(ViewportFit.font(multiplier)))
            .color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD,false).decoration(TextDecoration.ITALIC,false);
    }
    /** The same layer for {@code screen}: carries the auto-fit marker (and no shadow) when the screen is fitted. */
    static Component layer(HudAssets assets,String glyphs,int multiplier,Screen screen) {
        return paint(Component.text(assets.displayText(glyphs,multiplier)).font(Key.key(ViewportFit.font(multiplier))),assets,screen,NamedTextColor.WHITE);
    }
    /**
     * {@code glyphs} in {@code color} with bold and italic off. For an auto-fit screen the colour carries the
     * {@link GlyphColor} marker the pack's shader looks for, and the drop shadow is off; otherwise exactly as before.
     */
    static Component paint(Component glyphs,HudAssets assets,Screen screen,TextColor color) {
        if(!assets.fitted(screen))return glyphs.color(color).decoration(TextDecoration.BOLD,false).decoration(TextDecoration.ITALIC,false);
        return Shadow.off(glyphs.color(TextColor.color(GlyphColor.mark(color.value()))).decoration(TextDecoration.BOLD,false).decoration(TextDecoration.ITALIC,false));
    }
}
