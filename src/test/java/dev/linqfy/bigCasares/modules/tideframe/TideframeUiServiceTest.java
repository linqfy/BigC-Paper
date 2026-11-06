package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.testsupport.FakePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TideframeUiServiceTest {
    /** Tideframe's API as the bridge exposes it, recording every call. */
    private static final class FakeBridge implements TideframeBridge {
        final Set<String> screens = new HashSet<>(Set.of("bigc_tienda"));
        boolean packLoaded = true;
        /** Thrown by isPackLoaded: a Tideframe whose API turned out different. */
        LinkageError changed;
        CompletableFuture<Boolean> opening = CompletableFuture.completedFuture(true);
        /** Whether an opening puts the screen up (false: Tideframe answered true only for a HUD page the player has). */
        boolean showsScreen = true;
        RuntimeException openFailure;
        java.util.function.BiPredicate<Player, String> allow = (player, screen) -> true;
        boolean dispatchOpen = true;
        final Map<UUID, String> current = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        @Override public Set<String> screenIds() { return Set.copyOf(screens); }
        @Override public boolean isPackLoaded(Player player) {
            if (changed != null) throw changed;
            return packLoaded;
        }
        @Override public CompletableFuture<Boolean> openWhenReady(Player player, String screenId) {
            if (openFailure != null) throw openFailure;
            calls.add("open " + screenId);
            if (dispatchOpen && allow.test(player, screenId) && showsScreen) current.put(player.getUniqueId(), screenId);
            return opening;
        }
        @Override public Optional<String> currentScreen(Player player) { return Optional.ofNullable(current.get(player.getUniqueId())); }
        @Override public void closeScreen(Player player) { calls.add("close " + current.remove(player.getUniqueId())); }
        @Override public void setVariable(Player player, String name, String value) { calls.add("var " + name + "=" + value); }
        @Override public void setList(Player player, String source, List<Map<String, String>> rows) { calls.add("list " + source + " " + rows.size()); }
    }

    private final FakeBridge bridge = new FakeBridge();
    private final Map<UUID, ClientPlatform> platforms = new HashMap<>();
    private final List<Runnable> mainThread = new ArrayList<>();
    private boolean ownPack = true;
    private final List<LinkageError> incompatibilities = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(5_000_000_000L);
    private final TideframeUiService ui = new TideframeUiService(bridge,
        id -> platforms.getOrDefault(id, ClientPlatform.JAVA), () -> ownPack, mainThread::add, incompatibilities::add, clock::get);
    private final FakePlayer juan = new FakePlayer("juan");

    @BeforeEach
    void relayTheProvidersOpenEvent() {
        bridge.allow = ui::allowOpen;
    }

    @Test
    void theStatusCountsWhoCanSeeTheScreensAndNamesTheMissingOnes() {
        FakePlayer ana = new FakePlayer("ana");
        FakePlayer pepe = new FakePlayer("pepe");
        platforms.put(pepe.id, ClientPlatform.BEDROCK);
        TideframeStatus status = ui.status(List.of(juan.player, ana.player, pepe.player),
            List.of("bigc_misiones", "bigc_tienda"), true, false);
        assertEquals(new TideframeStatus(true, false, true, true, false, List.of("bigc_misiones"), 2, 2, 1, Map.of()), status);

        bridge.packLoaded = false;
        ownPack = false;
        assertEquals(0, ui.status(List.of(juan.player), List.of(), true, true).javaWithPack());
        assertFalse(ui.status(List.of(), List.of(), true, true).ownPack());
    }

    @Test
    void theStatusCountsEveryReasonAMenuTookItsClassicForm() {
        juan.onGround = false;
        ui.canShow(juan.player, "bigc_tienda");
        juan.onGround = true;
        bridge.packLoaded = false;
        ui.canShow(juan.player, "bigc_tienda");
        ui.canShow(juan.player, "bigc_tienda");
        bridge.packLoaded = true;
        ui.canShow(juan.player, "bigc_misiones");
        ui.canShow(juan.player, "visual_menu09");
        assertTrue(ui.canShow(juan.player, "bigc_tienda"));

        assertEquals(Map.of(TideframeStatus.Fallback.MOVING, 1L, TideframeStatus.Fallback.PACK_NOT_LOADED, 2L,
            TideframeStatus.Fallback.SCREEN_MISSING, 1L), ui.status(List.of(), List.of(), true, true).fallbacks(),
            "another plugin's screen and a successful check count nothing");
    }

    @Test
    void aScreenShowsOnlyToAJavaPlayerWithTideframesPackAndBothFeetDown() {
        assertTrue(ui.canShow(juan.player, "bigc_tienda"));
        assertFalse(ui.canShow(juan.player, "bigc_misiones"), "Tideframe does not have that screen (yet)");
        assertFalse(ui.canShow(juan.player, "visual_menu09"), "only BigCasares' own screens");
        bridge.packLoaded = false;
        assertFalse(ui.canShow(juan.player, "bigc_tienda"), "Tideframe's own pack is not loaded");
        bridge.packLoaded = true;
        platforms.put(juan.id, ClientPlatform.BEDROCK);
        assertFalse(ui.canShow(juan.player, "bigc_tienda"), "Bedrock keeps its forms");
        platforms.put(juan.id, ClientPlatform.UNKNOWN);
        assertFalse(ui.canShow(juan.player, "bigc_tienda"));
        platforms.put(juan.id, ClientPlatform.JAVA);
        juan.gliding = true;
        assertFalse(ui.canShow(juan.player, "bigc_tienda"), "never freeze a glider mid-flight");
        juan.gliding = false;
        juan.vehicle = true;
        assertFalse(ui.canShow(juan.player, "bigc_tienda"));
        juan.vehicle = false;
        juan.onGround = false;
        assertFalse(ui.canShow(juan.player, "bigc_tienda"), "mid-air (a grappling hook, a fall)");
        juan.flying = true;
        assertTrue(ui.canShow(juan.player, "bigc_tienda"), "creative flight can stop");
        juan.online = false;
        assertFalse(ui.canShow(juan.player, "bigc_tienda"));
    }

    @Test
    void withTideframesPackSendOffNoLoadedPackCountsAsItsOwn() {
        ownPack = false;
        assertFalse(ui.canShow(juan.player, "bigc_tienda"), "any server pack counts in Tideframe then, even BigCasares' alone");
        ownPack = true;
        assertTrue(ui.canShow(juan.player, "bigc_tienda"));
    }

    @Test
    void theOpeningAnswersOnTheMainThreadAndFalseWhenTideframeCouldNot() {
        List<Boolean> answers = new ArrayList<>();
        ui.open(juan.player, "bigc_tienda", answers::add);
        assertEquals(List.of(), answers, "not from Tideframe's thread");
        mainThread.removeFirst().run();
        assertEquals(List.of(true), answers);

        bridge.current.clear();
        bridge.showsScreen = false;
        bridge.opening = CompletableFuture.completedFuture(false);
        ui.open(juan.player, "bigc_tienda", answers::add);
        mainThread.removeFirst().run();
        bridge.opening = CompletableFuture.failedFuture(new IllegalStateException("timeout"));
        ui.open(juan.player, "bigc_tienda", answers::add);
        mainThread.removeFirst().run();
        bridge.opening = new CompletableFuture<>();
        ui.open(juan.player, "bigc_tienda", answers::add);
        juan.online = false;
        bridge.opening.complete(true);
        mainThread.removeFirst().run();
        assertEquals(List.of(true, false, false, false), answers, "refused, failed, or the player left");

        juan.online = true;
        bridge.current.clear();
        bridge.showsScreen = false;
        bridge.opening = CompletableFuture.completedFuture(true);
        ui.open(juan.player, "bigc_tienda", answers::add);
        mainThread.removeFirst().run();
        assertEquals(false, answers.getLast(), "Tideframe said true for a HUD page while the menu did not open: fall back");

        bridge.openFailure = new IllegalArgumentException("unknown screen");
        ui.open(juan.player, "bigc_tienda", answers::add);
        assertEquals(false, answers.getLast(), "a refusal answers at once");
    }

    @Test
    void bigCasaresScreensOpenOnlyThroughBigCasares() {
        assertTrue(ui.allowOpen(juan.player, "visual_menu09"), "other plugins' screens are not BigCasares' to stop");
        assertFalse(ui.allowOpen(juan.player, "bigc_tienda"), "Tideframe reopening it by itself after a reload, or /tideframe open");
        assertEquals(1, juan.messages.size(), "the player is told how it opens");

        CompletableFuture<Boolean> first = new CompletableFuture<>(), second = new CompletableFuture<>();
        bridge.opening = first;
        ui.open(juan.player, "bigc_tienda", answer -> { });
        bridge.opening = second;
        ui.open(juan.player, "bigc_tienda", answer -> { });
        assertTrue(ui.allowOpen(juan.player, "bigc_tienda"), "BigCasares' own opening");
        assertFalse(ui.allowOpen(juan.player, "bigc_misiones"), "only the screen being opened");
        first.complete(true);
        assertTrue(ui.allowOpen(juan.player, "bigc_tienda"), "a second /shop still on its way");
        second.complete(true);
        assertFalse(ui.allowOpen(juan.player, "bigc_tienda"), "both answered: Tideframe opening it again alone is refused");

        bridge.openFailure = new IllegalArgumentException("unknown screen");
        ui.open(juan.player, "bigc_tienda", answer -> { });
        assertFalse(ui.allowOpen(juan.player, "bigc_tienda"), "a refused opening leaves nothing allowed behind");
    }

    @Test
    void anOpeningOvertakenByANewerOneNeitherFallsBackNorUndoesIt() {
        bridge.screens.add("bigc_misiones");
        List<String> answers = new ArrayList<>();
        CompletableFuture<Boolean> shop = new CompletableFuture<>(), missions = new CompletableFuture<>();
        bridge.opening = shop;
        ui.open(juan.player, "bigc_tienda", ok -> answers.add("tienda " + ok));
        bridge.opening = missions;
        ui.open(juan.player, "bigc_misiones", ok -> answers.add("misiones " + ok));
        shop.complete(false);
        missions.complete(true);
        mainThread.forEach(Runnable::run);
        assertEquals(List.of("tienda true", "misiones true"), answers,
            "/shop then /misiones: no inventory shop over the missions now shown, and its state is not dropped");
    }

    @Test
    void anOpeningTideframeDroppedStopsBeingAllowedAfterAWhileOrWhenThePlayerLeaves() {
        bridge.opening = new CompletableFuture<>();
        ui.open(juan.player, "bigc_tienda", ok -> { });
        assertTrue(ui.allowOpen(juan.player, "bigc_tienda"));
        clock.addAndGet(TideframeUiService.OPENING_NANOS + 1);
        assertFalse(ui.allowOpen(juan.player, "bigc_tienda"), "never answered (the player died before it ran): not forever");

        List<String> forgotten = new ArrayList<>();
        ui.handle("bigc_tienda", new TideframeScreenHandler() {
            @Override public boolean click(Player player, TideframeClick click) { return false; }
            @Override public void closed(Player player) { forgotten.add(player.getName()); }
        });
        ui.open(juan.player, "bigc_tienda", ok -> { });
        ui.quit(juan.player);
        assertFalse(ui.allowOpen(juan.player, "bigc_tienda"), "a player who leaves and comes back is not still allowed");
        assertEquals(List.of("juan"), forgotten, "and every feature forgets what it showed them");
    }

    @Test
    void aScreenReplacedEvenByItselfKeepsWhatTheFeaturesShowedAndAWholeCloseForgetsIt() throws Exception {
        List<String> closes = new ArrayList<>();
        AutoCloseable old = ui.handle("bigc_tienda", (player, click) -> true);
        ui.handle("bigc_tienda", new TideframeScreenHandler() {
            @Override public boolean click(Player player, TideframeClick click) { return false; }
            @Override public void closed(Player player) { closes.add("tienda"); }
        });
        ui.handle("bigc_misiones", new TideframeScreenHandler() {
            @Override public boolean click(Player player, TideframeClick click) { return false; }
            @Override public void closed(Player player) { closes.add("misiones"); }
        });
        // /shop again with the shop open: Tideframe reports the old one REPLACED after BigCasares sent the new rows.
        ui.closed(juan.player, "bigc_tienda", false);
        assertEquals(List.of(), closes, "the rows just sent stay valid");
        // The X, Escape, death, a reload: the whole UI closes from whichever screen was last, even another plugin's.
        ui.closed(juan.player, "visual_menu09", true);
        assertEquals(Set.of("tienda", "misiones"), Set.copyOf(closes));
        old.close();
        ui.closed(juan.player, "bigc_tienda", true);
        assertEquals(4, closes.size(), "closing an old registration leaves the newer handler in place");
    }

    @Test
    void textsAreGlobalVariablesAndOnlyBigCasaresNamesAreAccepted() {
        ui.setVariable(juan.player, "bigc_tienda_saldo", "$10");
        ui.setVariable(juan.player, "bigc_vacio", null);
        ui.setList(juan.player, "bigc_tienda", List.of(Map.of("id", "a")));
        assertEquals(List.of("var *bigc_tienda_saldo=$10", "var *bigc_vacio=", "list bigc_tienda 1"), bridge.calls);
        assertThrows(IllegalArgumentException.class, () -> ui.setVariable(juan.player, "coins", "1"));
        assertThrows(IllegalArgumentException.class, () -> ui.setList(juan.player, "servers", List.of()));
        assertThrows(IllegalArgumentException.class, () -> ui.handle("visual_menu09", (player, click) -> true));
    }

    @Test
    void closingNeverTouchesAnotherPluginsScreen() {
        bridge.current.put(juan.id, "visual_menu09");
        ui.close(juan.player, "bigc_tienda");
        ui.closeOwnScreens(List.of(juan.player));
        assertEquals(List.of(), bridge.calls, "Tideframe's own menu stays open");
        bridge.current.put(juan.id, "bigc_tienda");
        assertTrue(ui.showing(juan.player, "bigc_tienda"));
        ui.closeOwnScreens(List.of(juan.player));
        assertEquals(List.of("close bigc_tienda"), bridge.calls);
    }

    @Test
    void clicksGoToTheFeatureOwningTheScreenAndNowhereElse() {
        List<String> seen = new ArrayList<>();
        ui.handle("bigc_tienda", (player, click) -> seen.add(click.elementId() + "#" + click.row()));
        assertTrue(ui.click(juan.player, new TideframeClick(juan.id, "bigc_tienda", "fila", 2, false, false)));
        assertFalse(ui.click(juan.player, new TideframeClick(juan.id, "visual_menu09", "fila", 2, false, false)), "other screens are not ours");
        assertFalse(ui.click(juan.player, new TideframeClick(juan.id, "bigc_misiones", "diarias", -1, false, false)), "nobody handles it");
        assertEquals(List.of("fila#2"), seen);
    }

    @Test
    void anApiThatTurnsOutDifferentFallsBackToTheUsualMenusForGood() {
        bridge.changed = new NoSuchMethodError("isPackLoaded");
        assertFalse(ui.canShow(juan.player, "bigc_tienda"), "the inventory menu opens instead of a failing command");
        assertEquals(1, incompatibilities.size());
        bridge.changed = null;
        assertFalse(ui.canShow(juan.player, "bigc_tienda"), "and Tideframe is not asked again");
        List<Boolean> answers = new ArrayList<>();
        ui.open(juan.player, "bigc_tienda", answers::add);
        ui.setVariable(juan.player, "bigc_tienda_saldo", "$1");
        assertEquals(List.of(false), answers, "an opening answers false at once: the caller falls back");
        assertEquals(List.of(), bridge.calls);
        assertEquals(1, incompatibilities.size(), "reported once");
    }

    @Test
    void preparesOnlyTheLatestAuthorizedViewAndRechecksThePackAtOpening() {
        bridge.dispatchOpen = false;
        bridge.screens.add("bigc_misiones");
        bridge.opening = new CompletableFuture<>();
        List<String> prepared = new ArrayList<>();
        ui.open(juan.player, "bigc_misiones", () -> prepared.add("misiones"), ignored -> { });
        ui.open(juan.player, "bigc_tienda", () -> prepared.add("tienda"), ignored -> { });
        assertEquals(List.of(), prepared, "an on-close command only queues a view; it does not prepare it yet");
        assertFalse(ui.allowOpen(juan.player, "bigc_misiones"), "an older request cannot replace the latest navigation");
        ownPack = false;
        assertFalse(ui.allowOpen(juan.player, "bigc_tienda"), "the pack became unsafe before the deferred opening");
        assertEquals(List.of(), prepared);
        ownPack = true;
        assertTrue(ui.allowOpen(juan.player, "bigc_tienda"));
        assertEquals(List.of("tienda"), prepared);
    }

    @Test
    void aViewThatCannotBePreparedIsNotAllowedToLockTheCamera() {
        bridge.dispatchOpen = false;
        bridge.opening = new CompletableFuture<>();
        List<Boolean> answers = new ArrayList<>();
        ui.open(juan.player, "bigc_tienda", () -> { throw new IllegalStateException("catalog unavailable"); }, answers::add);
        assertFalse(ui.allowOpen(juan.player, "bigc_tienda"));
        bridge.opening.complete(false);
        mainThread.removeFirst().run();
        assertEquals(List.of(false), answers, "the caller opens its inventory fallback");
    }

    @Test
    void classicNavigationReplacesAnOlderDeferredShopWithoutOpeningUnderItsCamera() {
        bridge.current.put(juan.id, "bigc_tienda");
        List<String> opened = new ArrayList<>();
        ui.openClassic(juan.player, () -> opened.add("old shop"));
        ui.openClassic(juan.player, () -> opened.add("new missions"));
        assertEquals(List.of(), opened);
        bridge.current.clear();
        ui.closed(juan.player, "bigc_tienda", true);
        mainThread.removeFirst().run();
        assertEquals(List.of("new missions"), opened);
    }
}
