package dev.linqfy.bigCasares.modules.tideframe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The two BigCasares panels use the same 256 by 144 pixel grid for drawing and hit testing. */
public final class Panel {
    record Box(int x, int y, int w, int h) {
        boolean contains(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    record Button(Box box, Consumer<Boolean> action) {}
    /** Art shown while the pointer is in {@code box}; {@code under} draws it behind the icons and artwork. */
    record Hover(Box box, PixelImage image, int x, int y, boolean under) {}
    sealed interface Paint permits Picture, Item {}
    record Picture(PixelImage image, int x, int y) implements Paint {}
    record Item(String id, int x, int y) implements Paint {}

    final boolean shop;
    final int selected;
    final int tabs;
    final List<Paint> paints = new ArrayList<>();
    final List<Button> buttons = new ArrayList<>();
    final List<Hover> hovers = new ArrayList<>();
    private Runnable closed = () -> {};

    public Panel(boolean shop, int selectedTab, int tabCount) {
        if (tabCount < 0 || tabCount > ScreenArt.MAX_TABS || selectedTab < -1 || selectedTab >= tabCount
                || !shop && tabCount != 2) throw new IllegalArgumentException("Invalid menu tabs");
        this.shop = shop;
        this.selected = selectedTab;
        this.tabs = tabCount;
    }

    public static boolean knowsItem(String id) { return PanelPack.iconIndex(id) >= 0; }
    public void image(PixelImage image, int x, int y) { paints.add(new Picture(image, x, y)); }
    public void item(String id, int x, int y) { paints.add(new Item(knowsItem(id) ? id : "barrier", x, y)); }
    public void text(String text, int x, int y, int colour) {
        PixelFont font = PixelFont.get();
        PixelImage label = new PixelImage(Math.max(1, font.width(PixelFont.sanitize(text)) + 1), 13);
        font.draw(label, text, 0, 2, colour);
        image(label, x, y - 2);
    }
    public void button(int x, int y, int w, int h, Consumer<Boolean> action) {
        buttons.add(new Button(new Box(x,y,w,h), action));
    }
    /** Hover art over everything: tooltips, button faces, outlines. */
    public void hover(int x, int y, int w, int h, PixelImage art, int artX, int artY) {
        hovers.add(new Hover(new Box(x,y,w,h), art, artX, artY, false));
    }
    /** A highlight behind the item and text, like a hovered inventory slot or tab. */
    public void highlight(int x, int y, int w, int h, PixelImage art, int artX, int artY) {
        hovers.add(new Hover(new Box(x,y,w,h), art, artX, artY, true));
    }
    public void onClose(Runnable callback) { closed = callback; }
    void closed() { closed.run(); }
    String group() { return shop ? "shop" : "missions"; }

    PixelImage background() {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < tabs; i++) positions.add(ScreenArt.tabX(i));
        positions.add(ScreenArt.NAV_X);
        int seed = shop ? 5 : 8;
        PixelImage base = ScreenArt.window(seed, positions);
        if (selected >= 0) ScreenArt.tab(base, ScreenArt.tabX(selected), true, seed);
        if (shop) {
            base.fill(126,41,60,11,0xff000000).outline(126,41,60,11,0xff7d7d79);
            for (int i=0; i<16; i++) base.slot(24+27*(i%8),52+30*(i/8));
        }
        PixelFont.get().centre(base,"X",232.5,43,ScreenArt.WHITE);
        return base;
    }
}
