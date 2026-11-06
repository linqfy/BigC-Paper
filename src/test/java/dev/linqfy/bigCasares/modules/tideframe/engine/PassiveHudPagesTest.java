package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 0.23: several HUD pages at once (stacked pages, the world's list) and toasts that slide in, wait and leave. */
class PassiveHudPagesTest {
    private static final long MS = 1_000_000L;
    private static Screen hud(String id, double x, String logic) {
        var label = new Screen.Element(id + "_box", "", x, .6, .4, .2, "decoration", "", "rectangle", true, null, true, null);
        var screen = new Screen(id, id.toUpperCase(), List.of(label), RenderQuality.low()).withPresentation(new Screen.Presentation("overlay", 1, "text"));
        return logic == null ? screen : screen.withLogic(ActionParser.screen(ScreenCodec.parse(logic)));
    }
    private static final String SLIDE = "{\"animation\":{\"open\":{\"effect\":\"slide\",\"from\":\"right\",\"distance\":48,\"duration-ms\":300,\"easing\":\"linear\"},"
            + "\"close\":{\"effect\":\"slide\",\"from\":\"right\",\"distance\":48,\"duration-ms\":300,\"easing\":\"linear\"}}}";
    private static final Screen A = hud("hud_a", -.5, null), B = hud("hud_b", .5, null), TOAST = hud("aviso", 0, SLIDE);
    private static final HudAssets ASSETS = HudAssets.create(List.of(A, B, TOAST));

    /** A player that records titles, and a session whose ticks and clock the test drives. */
    private static final class Fixture {
        Fixture() { this(ASSETS); }
        Fixture(HudAssets assets) { this(assets, null); }
        Fixture(HudAssets assets, dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit viewport) { session = new PassiveHudSession(work -> { tick = work; return () -> { }; }, clock::get, player, assets, () -> menu, () -> { }, viewport); session.setExpiryListener(left::add); }
        boolean menu;
        final List<Title> titles = new ArrayList<>();
        final List<String> left = new ArrayList<>();
        net.kyori.adventure.bossbar.BossBar bossBar;
        int cleared;
        final AtomicLong clock = new AtomicLong(1_000 * MS);
        Runnable tick;
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "showTitle" -> { titles.add((Title) args[0]); yield null; }
            case "clearTitle" -> { cleared++; yield null; }
            case "isOnline", "hasPermission" -> true;
            case "isDead" -> false;
            case "getUniqueId" -> UUID.nameUUIDFromBytes(new byte[]{1});
            case "showBossBar" -> { bossBar = (net.kyori.adventure.bossbar.BossBar) args[0]; yield null; }
            case "sendActionBar", "hideBossBar" -> null;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "HUD test player";
            default -> throw new AssertionError("Unexpected player operation: " + method.getName());
        });
        final PassiveHudSession session;
        String lastTitle() { return text(titles.get(titles.size() - 1).title()); }
        void advance(long ms) { clock.addAndGet(ms * MS); tick.run(); }
    }
    private static String text(Component component) {
        var out = new StringBuilder(component instanceof TextComponent t ? t.content() : "");
        for (var child : component.children()) out.append(text(child));
        return out.toString();
    }
    private static String canvas(Screen screen) { return ASSETS.displayText(ASSETS.frame(screen, null, 8, 4), 4); }
    private static int count(String haystack, String needle) {
        int n = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) n++;
        return n;
    }

    @Test void stackedPagesDrawTogetherAndLeaveOneByOne() {
        var f = new Fixture();
        f.session.showScreen(A);
        f.session.addScreen(B, 0);
        assertEquals(List.of("hud_a", "hud_b"), f.session.screenIds());
        assertEquals(1, count(f.lastTitle(), canvas(A)));
        assertEquals(1, count(f.lastTitle(), canvas(B)), "the stacked page is drawn over the main one");
        f.session.addScreen(A, 0);
        assertEquals(List.of("hud_a", "hud_b"), f.session.screenIds(), "showing the main page again keeps the others");
        assertTrue(f.session.removeScreen("hud_a"));
        assertEquals(List.of("hud_b"), f.session.screenIds(), "the next page takes the main place");
        assertEquals(1, count(f.lastTitle(), canvas(B)));
        assertFalse(f.session.removeScreen("hud_b"));
        assertEquals(1, f.cleared, "no page left: the title is cleared");
        assertFalse(f.session.hasPages());
        f.session.showScreen(A);
        f.session.addScreen(B, 0);
        f.session.showScreen(A);
        assertEquals(List.of("hud_a"), f.session.screenIds(), "a page that does not stack replaces them all");
    }
    @Test void aToastSlidesInWaitsSlidesOutAndIsReported() {
        var f = new Fixture();
        f.session.addScreen(TOAST, 1);
        assertEquals(List.of("aviso"), f.session.screenIds());
        String first = f.lastTitle();
        assertEquals(1, count(first, canvas(TOAST)), "a toast alone is drawn once, without a main canvas under it");
        assertTrue(first.startsWith(ASSETS.displayText(HudAssets.hudSpace(48), 4)), "it starts 48 pixels to the right");
        f.advance(150);
        assertTrue(f.lastTitle().startsWith(ASSETS.displayText(HudAssets.hudSpace(24), 4)), "halfway in");
        f.advance(200);
        assertEquals(canvas(TOAST), f.lastTitle().substring(0, canvas(TOAST).length()), "in place: no offset");
        f.advance(650 + 150);
        assertTrue(f.lastTitle().startsWith(ASSETS.displayText(HudAssets.hudSpace(24), 4)), "its time is up: halfway out");
        assertTrue(f.left.isEmpty());
        f.advance(200);
        assertEquals(List.of("aviso"), f.left, "gone once the closing slide ends");
        assertEquals(List.of(), f.session.screenIds());
        assertEquals(1, f.cleared);
    }
    @Test void aToastSentFromAMenuWaitsUntilTheMenuCloses() {
        var f = new Fixture();
        f.menu = true;
        f.session.addScreen(TOAST, 1);
        int drawn = f.titles.size();
        for (int i = 0; i < 60; i++) f.advance(50);
        assertEquals(List.of("aviso"), f.session.screenIds(), "three seconds under a menu: the toast still waits");
        assertEquals(drawn, f.titles.size(), "nothing is drawn while the menu covers the HUD");
        assertTrue(f.left.isEmpty());
        f.menu = false;
        f.advance(50);
        assertTrue(f.lastTitle().startsWith(ASSETS.displayText(HudAssets.hudSpace(40), 4)), "once seen it starts sliding in");
        f.advance(950);
        assertTrue(f.left.isEmpty(), "its whole second counts from then");
        f.advance(300);
        assertEquals(List.of("aviso"), f.left);
    }
    @Test void aHitOnTheBossFlashesAndShakesTheBarThenItIsPlainAgain() {
        var f = new Fixture();
        f.session.state().setBoss("GOLEM", 90, 100);
        f.session.refresh();
        f.session.state().setBoss("GOLEM", 85, 100);
        f.session.refresh();
        assertEquals(dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets.title("GOLEM | 85%", .85f), text(f.bossBar.name()), "off unless config.yml turns them on");
        f.session.setBossEffects(true, true);
        f.session.state().setBoss("GOLEM", 60, 100);
        f.session.refresh();
        assertEquals(dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets.title("GOLEM | 60%", .6f, -2, true), text(f.bossBar.name()), "the hit: lit and 2 px to the left");
        f.advance(50);
        assertEquals(dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets.title("GOLEM | 60%", .6f, 1, true), text(f.bossBar.name()));
        f.session.state().setBoss("GOLEM", 59, 100);
        f.session.refresh();
        assertEquals(dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets.title("GOLEM | 59%", .59f, 1, true), text(f.bossBar.name()), "more damage during a hit lets it play on");
        f.session.state().setBoss("GOLEM", 60, 100);
        f.session.refresh();
        f.advance(50);
        assertEquals(dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets.title("GOLEM | 60%", .6f, -1, false), text(f.bossBar.name()), "the flash lasts two ticks");
        for (int i = 0; i < 4; i++) f.advance(50);
        assertEquals(dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets.title("GOLEM | 60%", .6f), text(f.bossBar.name()), "then the bar is plain again");
        f.session.state().setBoss("GOLEM", 70, 100);
        f.session.refresh();
        assertEquals(dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets.title("GOLEM | 70%", .7f), text(f.bossBar.name()), "healing is not a hit");
        f.session.setBossEffects(false, false);
        f.session.state().setBoss("GOLEM", 40, 100);
        f.session.refresh();
        assertEquals(dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets.title("GOLEM | 40%", .4f), text(f.bossBar.name()), "both effects off in config.yml: a plain bar");
    }
    @Test void aToastTakenAwayAnyOtherWayIsReportedOnceTheHudIsSettled() {
        var f = new Fixture();
        // The plugin's listener brings the next toast in at once (nextToast); the HUD must already be settled by then.
        var next = new ArrayList<Screen>();
        f.session.setExpiryListener(id -> { f.left.add(id); if (!next.isEmpty()) f.session.addScreen(next.remove(0), 4); });
        f.session.showScreen(A);
        f.session.addScreen(TOAST, 4);
        next.add(TOAST);
        assertTrue(f.session.removeScreen("aviso"), "hud remove, removeOverlay or the world's list");
        assertEquals(List.of("aviso"), f.left, "taken away by hand: reported, so the queue goes on");
        assertEquals(List.of("hud_a", "aviso"), f.session.screenIds(), "and the next toast is in");
        next.add(TOAST);
        f.session.showScreen(B);
        assertEquals(List.of("aviso", "aviso"), f.left, "replaced by a page that does not stack: reported");
        assertEquals(List.of("hud_b", "aviso"), f.session.screenIds(), "the next toast comes in over the new page");
        f.session.addScreen(TOAST, 0);
        assertEquals(List.of("aviso", "aviso", "aviso"), f.left, "the same page opened to stay: the toast it replaces is reported");
        assertEquals(List.of("hud_b", "aviso"), f.session.screenIds());
        f.session.removeScreen("aviso");
        f.session.addScreen(A, 0);
        f.session.removeScreen("hud_a");
        assertEquals(3, f.left.size(), "pages without a time limit are not toasts: nothing to report");
    }
    @Test void aToastNeverTakesThePlaceOfAPageThatStays() {
        var f = new Fixture();
        f.session.showScreen(A);
        assertThrows(IllegalStateException.class, () -> f.session.addScreen(A, 4), "not the main page");
        f.session.addScreen(B, 0);
        assertThrows(IllegalStateException.class, () -> f.session.addScreen(B, 4), "nor a page added to stay");
        assertEquals(List.of("hud_a", "hud_b"), f.session.screenIds());
        assertTrue(f.session.staying("hud_a") && f.session.staying("hud_b"));
        f.session.addScreen(TOAST, 4);
        assertFalse(f.session.staying("aviso"), "a toast does not stay");
        // Opened to stay while it is the toast on screen, with the same page next in the queue (nextToast): it stays.
        var next = new ArrayList<Screen>(List.of(TOAST));
        f.session.setExpiryListener(id -> { f.left.add(id); while (!next.isEmpty()) { var page = next.remove(0); assertThrows(IllegalStateException.class, () -> f.session.addScreen(page, 4)); } });
        f.session.addScreen(TOAST, 0);
        assertEquals(List.of("aviso"), f.left, "the toast it replaced is reported");
        assertTrue(f.session.staying("aviso"));
        for (int i = 0; i < 10; i++) f.advance(1000);
        assertEquals(List.of("hud_a", "hud_b", "aviso"), f.session.screenIds(), "still there long after any toast time");
        assertEquals(List.of("aviso"), f.left);
    }
    @Test void aPageAddedToStayTakesOverItsOwnToastAndAPromotedPageKeepsItsState() {
        var f = new Fixture();
        f.session.addScreen(TOAST, 4);
        f.session.addScreen(TOAST, 0);
        assertEquals(List.of("aviso"), f.session.screenIds(), "drawn once, not as a page and a toast");
        assertEquals(List.of("aviso"), f.left, "the toast it replaced is reported");
        for (int i = 0; i < 10; i++) f.advance(1000);
        assertEquals(List.of("aviso"), f.session.screenIds(), "and it stays");
        var shy = new Screen.Element("shy", "", .5, .6, .4, .2, "decoration", "", "rectangle", true, null, true, null)
                .withLogic(ActionParser.element(ScreenCodec.parse("{\"hidden\":true}")));
        var c = new Screen("hud_c", "HUD_C", List.of(shy), RenderQuality.low()).withPresentation(new Screen.Presentation("overlay", 1, "text"));
        var g = new Fixture(HudAssets.create(List.of(A, c)));
        g.session.showScreen(A);
        g.session.addScreen(c, 0);
        g.session.updateView("hud_c", view -> view.visibility(List.of("shy"), List.of(), List.of()));
        assertTrue(g.session.view("hud_c").shown("shy"));
        assertTrue(g.session.removeScreen("hud_a"));
        assertEquals(List.of("hud_c"), g.session.screenIds());
        assertTrue(g.session.view("hud_c").shown("shy"), "the promoted page keeps what its actions did");
    }
    @Test void aToastOverAHudLeavesTheHudAndPagesAreLimited() {
        var f = new Fixture();
        f.session.showScreen(A);
        f.session.addScreen(TOAST, 1);
        assertEquals(List.of("hud_a", "aviso"), f.session.screenIds());
        f.advance(1400);
        assertEquals(List.of("hud_a"), f.session.screenIds());
        assertEquals(0, f.cleared, "the HUD under the toast stays");
        assertEquals(1, count(f.lastTitle(), canvas(A)));
        assertEquals(0, count(f.lastTitle(), canvas(TOAST)));
        var many = new ArrayList<Screen>(List.of(A));
        for (int i = 0; i <= PassiveHudSession.MAX_EXTRAS; i++) many.add(hud("extra" + i, 0, null));
        var limited = new Fixture(HudAssets.create(many));
        limited.session.showScreen(A);
        for (int i = 1; i <= PassiveHudSession.MAX_EXTRAS; i++) limited.session.addScreen(many.get(i), 0);
        assertEquals(PassiveHudSession.MAX_EXTRAS + 1, limited.session.screenIds().size());
        assertThrows(IllegalStateException.class, () -> limited.session.addScreen(many.get(PassiveHudSession.MAX_EXTRAS + 1), 0), "a full HUD refuses more pages");
    }
    private static void colours(Component component, List<Integer> out) {
        if (component.style().color() != null) out.add(component.style().color().value());
        for (var child : component.children()) colours(child, out);
    }
    /** Children inherit their parent's style (the shadow too): unmarked glyphs never sit below marked ones. */
    private static void assertNoPlainGlyphsInsideFittedOnes(Component component, boolean insideFitted) {
        var color = component.style().color();
        boolean marked = color != null && dev.linqfy.bigCasares.modules.tideframe.engine.core.GlyphColor.marked(color.value());
        if (insideFitted && color != null) assertTrue(marked, "plain glyphs inside a fitted layer: " + Integer.toHexString(color.value()));
        for (var child : component.children()) assertNoPlainGlyphsInsideFittedOnes(child, insideFitted || marked);
    }
    @Test void withAutoFitHudPagesAreDrawnAtTheReferenceSizeWithTheMarkerWhateverSizeThePlayerHas() {
        var fitted = HudAssets.create(List.of(A, B, TOAST), List.of(4, 7, 8, 11), HudAssets.DEFAULT_CURSOR_SCALE, java.util.Map.of(), java.util.Map.of(), true);
        int reference = HudAssets.REFERENCE_QUARTERS;
        // A player who once picked 1.75x: the shader scales the HUD, so that choice no longer applies to it.
        var f = new Fixture(fitted, dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit.fixed(7));
        f.session.showScreen(A);
        f.session.addScreen(B, 0);
        f.session.addScreen(TOAST, 5);
        f.advance(400);
        String title = f.lastTitle();
        for (var page : List.of(A, B, TOAST))
            assertEquals(1, count(title, fitted.displayText(fitted.frame(page, null, 8, reference), reference)), page.id() + " at the reference size");
        assertEquals(0, count(title, fitted.displayText(fitted.frame(A, null, 8, 7), 7)), "not at the player's 1.75x");
        var drawn = new ArrayList<Integer>();
        colours(f.titles.get(f.titles.size() - 1).title(), drawn);
        colours(f.session.layer(), drawn);
        assertTrue(drawn.size() >= 6, "main page, stacked page and toast, in the title and under a menu: " + drawn);
        for (int colour : drawn) assertTrue(dev.linqfy.bigCasares.modules.tideframe.engine.core.GlyphColor.marked(colour), Integer.toHexString(colour));
        // An enlarged page (scale 2) stays as authored, and it is not drawn inside the fitted page's style (no shadow there).
        var big = new Screen("hud_big", "BIG", A.elements(), RenderQuality.low()).withPresentation(new Screen.Presentation("overlay", 2, "text"));
        var mixed = HudAssets.create(List.of(A, big), List.of(4, 7, 8, 11), HudAssets.DEFAULT_CURSOR_SCALE, java.util.Map.of(), java.util.Map.of(), true);
        var m = new Fixture(mixed, dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit.fixed(7));
        m.session.showScreen(A);
        m.session.addScreen(big, 0);
        assertNoPlainGlyphsInsideFittedOnes(m.titles.get(m.titles.size() - 1).title(), false);
        assertNoPlainGlyphsInsideFittedOnes(m.session.layer(), false);
        var bigColours = new ArrayList<Integer>();
        colours(m.session.layer(), bigColours);
        assertTrue(bigColours.stream().anyMatch(c -> !dev.linqfy.bigCasares.modules.tideframe.engine.core.GlyphColor.marked(c)), "the enlarged page keeps its plain colour: " + bigColours);
        // Without auto-fit the same player keeps 1.75x and plain white glyphs, exactly as before.
        var plainAssets = HudAssets.create(List.of(A, B, TOAST), List.of(4, 7, 8, 11), HudAssets.DEFAULT_CURSOR_SCALE, java.util.Map.of(), java.util.Map.of(), false);
        var plain = new Fixture(plainAssets, dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit.fixed(7));
        plain.session.showScreen(A);
        assertEquals(1, count(plain.lastTitle(), plainAssets.displayText(plainAssets.frame(A, null, 8, 7), 7)), "the player's 1.75x");
        var white = new ArrayList<Integer>();
        colours(plain.titles.get(plain.titles.size() - 1).title(), white);
        assertFalse(white.isEmpty());
        for (int colour : white) assertFalse(dev.linqfy.bigCasares.modules.tideframe.engine.core.GlyphColor.marked(colour), Integer.toHexString(colour));
    }
}
