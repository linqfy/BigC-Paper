package dev.linqfy.bigCasares.modules.tideframe;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Places the panel artwork in the title and the independently moving pointer in the subtitle. */
final class PanelText {
    static final Key FONT = Key.key("bigcasares:pantalla");
    private PanelText() {}

    static Component body(Panel panel) {
        return render(panel, -1, -1);
    }

    /**
     * The panel with the pointer at {@code x, y}: background, the highlights under it (slots and tabs keep their
     * item in front), the artwork and icons, then the hover art over everything (tooltips, buttons, the X).
     */
    static Component render(Panel panel, int x, int y) {
        var result = base();
        StringBuilder line = new StringBuilder();
        stamp(line,-128,PanelPack.background(panel.shop,panel.selected,panel.tabs),PanelPack.advance(256));
        result.append(Component.text(titleSpan(line)));
        PixelImage foreground = new PixelImage(256,144);
        for (Panel.Hover hover : panel.hovers) {
            if (hover.under() && hover.box().contains(x,y)) foreground.blit(hover.image(),hover.x(),hover.y());
        }
        for (Panel.Paint paint : panel.paints) {
            if (paint instanceof Panel.Picture pic) {
                foreground.blit(pic.image(),pic.x(),pic.y());
            } else if (paint instanceof Panel.Item item) {
                appendPixels(result,foreground);
                foreground = new PixelImage(256,144);
                appendIcon(result, item);
            }
        }
        // Hover art replaces the pixels under it: a blend (a tooltip over a neighbour's count) would make a colour
        // the shader does not know, and those runs would not be drawn at all, leaving holes in the tooltip.
        for (Panel.Hover hover : panel.hovers) {
            if (!hover.under() && hover.box().contains(x,y)) foreground.cover(hover.image(),hover.x(),hover.y());
        }
        if (closeAt(x, y)) foreground.cover(ScreenArt.fitted(ScreenArt.closeHover(), "X", ScreenArt.WHITE), 227, 41);
        appendPixels(result,foreground);
        return result.build();
    }

    private static void appendIcon(TextComponent.Builder result, Panel.Item item) {
        int index = PanelPack.iconIndex(item.id());
        PanelPack.Icon icon = PanelPack.ICONS.get(index);
        for (int face = 0; face < icon.faces(); face++) {
            StringBuilder glyphs = new StringBuilder();
            stamp(glyphs,item.x()-128,PanelPack.icon(index,face,item.y()),PanelPack.advance(icon.columns()));
            int marker = icon.cube() ? PanelColours.cubeMarker(face, item.y() % 4) : PanelColours.iconMarker(item.y() % 4);
            result.append(Component.text(titleSpan(glyphs), TextColor.color(marker)));
        }
    }
    static boolean closeAt(int x,int y) { return x>=227 && x<238 && y>=41 && y<52; }
    static Component cursor(int x,int y) {
        StringBuilder glyphs=new StringBuilder();
        // Subtitle is drawn at 2x. The four ink columns advance three subtitle units.
        stamp(glyphs,2*(x-128),PanelPack.cursor(y),12);
        return base().content(PanelPack.glyph(PanelPack.POINTER_START) + glyphs
                + PanelPack.glyph(PanelPack.POINTER_END)).build();
    }
    private static String titleSpan(StringBuilder glyphs) {
        return PanelPack.glyph(PanelPack.TITLE_START) + glyphs + PanelPack.glyph(PanelPack.TITLE_END);
    }

    private static TextComponent.Builder base() {
        return Component.text().font(FONT).color(TextColor.color(PanelColours.marker(0xffffff))).shadowColor(ShadowColor.shadowColor(0));
    }
    private static void appendPixels(TextComponent.Builder result,PixelImage pixels) {
        Map<Integer,StringBuilder> colours=new LinkedHashMap<>();
        for(int y=0;y<pixels.height();y++) {
            int x=0;
            while(x<pixels.width()) {
                int argb=pixels.get(x,y);
                if ((argb>>>24)<25) { x++; continue; }
                int end=x+1;
                while(end<pixels.width() && pixels.get(end,y)==argb) end++;
                var line=colours.computeIfAbsent(argb & 0xffffff, key -> new StringBuilder());
                while(x<end) {
                    int power=Math.min(7,31-Integer.numberOfLeadingZeros(end-x));
                    int width=1<<power;
                    line.appendCodePoint(PanelPack.runStart(y,power));
                    // Every run cell is solid and 128 wide, so the game advances each run glyph as a 128-pixel one.
                    stamp(line,x-128,PanelPack.run(y,power,PanelPack.opacity(argb>>>24)),PanelPack.advance(128));
                    line.appendCodePoint(PanelPack.runEnd(y,power));
                    x+=width;
                }
            }
        }
        colours.forEach((rgb,text)-> result.append(Component.text(titleSpan(text),TextColor.color(PanelColours.marker(rgb)))));
    }
    private static void stamp(StringBuilder target,int offset,int glyph,int advance) {
        target.appendCodePoint(PanelPack.space(offset)).appendCodePoint(glyph)
            .appendCodePoint(PanelPack.space(-offset-advance));
    }
}
