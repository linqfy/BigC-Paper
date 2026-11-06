package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.testsupport.FakePlayer;
import dev.linqfy.bigCasares.testsupport.FakeTideframeUi;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionTideframeMenuTest {
    private static final String SCREEN = MissionTideframeMenu.SCREEN;

    private static MissionAssignment assignment(String id, MissionScope scope, String title, int goal, double reward,
                                                int progress, boolean completed, boolean claimed) {
        var definition = new MissionDefinition(id, scope, title, "", MissionType.values()[0], goal, reward, 1, true, Map.of());
        return new MissionAssignment(definition, new MissionProgressSnapshot(progress, completed, claimed, Set.of()));
    }

    private static Map<String, MissionAssignment> byId(List<MissionAssignment> assignments) {
        Map<String, MissionAssignment> out = new LinkedHashMap<>();
        for (MissionAssignment assignment : assignments) out.put(assignment.definition().id(), assignment);
        return out;
    }

    private final FakeTideframeUi ui = new FakeTideframeUi();
    private final FakePlayer juan = new FakePlayer("juan");
    /** Who was paid: the fake claim marks every ready mission claimed, as the module's claim does. */
    private final List<String> paid = new ArrayList<>();
    private MissionPlayerState state = new MissionPlayerState(juan.id, Instant.now(), Instant.now().plus(Duration.ofHours(5)),
        Instant.now().plus(Duration.ofDays(3)),
        byId(List.of(
            assignment("piedra", MissionScope.DAILY, "§fPicar 64 de piedra", 64, 250, 23, false, false),
            assignment("zombis", MissionScope.DAILY, "Matar 10 zombis", 10, 400, 10, true, false),
            assignment("peces", MissionScope.DAILY, "Pescar 5 peces", 5, 150, 5, true, true))),
        byId(List.of(assignment("diamantes", MissionScope.WEEKLY, "Juntar 20 diamantes", 20, 2000, 3, false, false))),
        null);
    private final MissionTideframeMenu menu = new MissionTideframeMenu(player -> state, MissionTideframeMenuTest::ready,
        amount -> "$" + (int) amount, () -> ui, this::claim);

    private static int ready(MissionPlayerState of) {
        return (int) of.dailyAssignments().values().stream()
            .filter(a -> a.snapshot().completed() && !a.snapshot().claimed()).count();
    }

    private void claim(Player player) {
        Map<String, MissionAssignment> daily = new LinkedHashMap<>();
        state.dailyAssignments().forEach((id, a) -> daily.put(id, a.snapshot().completed() && !a.snapshot().claimed()
            ? new MissionAssignment(a.definition(), new MissionProgressSnapshot(a.snapshot().progress(), true, true, Set.of()))
            : a));
        if (ready(state) > 0) paid.add(player.getName());
        state = new MissionPlayerState(state.playerId(), state.generatedAt(), state.dailyResetsAt(), state.weeklyResetsAt(),
            daily, state.weeklyAssignments(), null);
    }

    private List<String> column(Player player, String field) {
        return ui.list(player, MissionTideframeMenu.LIST).stream().map(row -> row.get(field)).toList();
    }

    @Test
    void showsEachMissionsProgressStatusAndRewardFromThePlayersState() {
        assertTrue(menu.open(juan.player, MissionScope.DAILY, () -> { }));
        assertEquals(List.of("Matar 10 zombis", "Picar 64 de piedra", "Pescar 5 peces"), column(juan.player, "titulo"),
            "no colour codes; ready to claim first, then running, then claimed");
        assertEquals(List.of("¡Lista!", "23/64", "Cobrada"), column(juan.player, "progreso"));
        assertEquals(List.of("$400", "$250", "$150"), column(juan.player, "premio"));
        assertEquals("Diarias", ui.variable(juan.player, "bigc_titulo"));
        assertTrue(ui.variable(juan.player, "bigc_reinicio").startsWith("se renuevan en 4h") || ui.variable(juan.player, "bigc_reinicio").startsWith("se renuevan en 5h"));
        assertEquals("1 para cobrar · $400", ui.variable(juan.player, "bigc_pendientes"), "and how much Reclamar pays");
        assertEquals("1", ui.variable(juan.player, "bigc_cobrables"), "the screen shows Reclamar only then");
        assertEquals("", ui.variable(juan.player, "bigc_vacio"));
    }

    @Test
    void theTabsSwitchTheListAndNothingElseChanges() {
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        assertTrue(ui.click(juan.player, SCREEN, "semanales", -1, false));
        assertEquals(List.of("Juntar 20 diamantes"), column(juan.player, "titulo"));
        assertEquals("Semanales", ui.variable(juan.player, "bigc_titulo"));
        assertTrue(ui.click(juan.player, SCREEN, "diarias", -1, false));
        assertEquals(3, column(juan.player, "titulo").size());
        assertEquals(List.of(), paid, "the tabs claim nothing");
    }

    @Test
    void reclamarPaysOnTheScreenAndRedrawsTheList() {
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        assertTrue(ui.click(juan.player, SCREEN, "reclamar", -1, true), "a right click is swallowed");
        assertEquals(List.of(), paid);
        assertTrue(ui.click(juan.player, SCREEN, "reclamar", -1, false), "cancelled: the screen stays open");
        assertEquals(List.of("juan"), paid);
        assertEquals(List.of("23/64", "Cobrada", "Cobrada"), column(juan.player, "progreso"));
        assertEquals("Nada para cobrar", ui.variable(juan.player, "bigc_pendientes"));
        assertEquals("0", ui.variable(juan.player, "bigc_cobrables"), "Reclamar hides itself");
        assertEquals(List.of(), ui.closed, "BigCasares closes nothing");
    }

    @Test
    void aSecondReclamarPaysNothingMore() {
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        ui.click(juan.player, SCREEN, "reclamar", -1, false);
        ui.click(juan.player, SCREEN, "reclamar", -1, false);
        assertEquals(List.of("juan"), paid, "the claim pays only what is still unclaimed");
    }

    @Test
    void cambiarAsksOnceAndTheSecondTapWithinSecondsChangesTheDailies() {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(0);
        List<String> rerolled = new ArrayList<>();
        java.util.concurrent.atomic.AtomicReference<MissionReroll.Check> check =
            new java.util.concurrent.atomic.AtomicReference<>(MissionReroll.Check.ALLOWED);
        MissionTideframeMenu withReroll = new MissionTideframeMenu(player -> state, MissionTideframeMenuTest::ready,
            amount -> "$" + (int) amount, () -> ui, this::claim, player -> check.get(), player -> rerolled.add(player.getName()), clock::get,
            (player, scope) -> scope == MissionScope.DAILY ? "racha 3 días" : "esta semana $900");
        withReroll.open(juan.player, MissionScope.DAILY, () -> { });
        assertTrue(ui.variable(juan.player, "bigc_reinicio").endsWith(" · racha 3 días"), "the tab's summary line");
        assertEquals("1", ui.variable(juan.player, "bigc_puede_cambiar"));
        assertEquals("Cambiar", ui.variable(juan.player, "bigc_cambiar"));

        assertTrue(ui.click(juan.player, SCREEN, "cambiar", -1, false));
        assertEquals(List.of(), rerolled, "the first tap only asks");
        assertEquals("¿Seguro?", ui.variable(juan.player, "bigc_cambiar"));
        clock.addAndGet(MissionReroll.CONFIRM_NANOS - 1);
        ui.click(juan.player, SCREEN, "cambiar", -1, false);
        assertEquals(List.of("juan"), rerolled);
        assertEquals("Cambiar", ui.variable(juan.player, "bigc_cambiar"));

        ui.click(juan.player, SCREEN, "cambiar", -1, false);
        clock.addAndGet(MissionReroll.CONFIRM_NANOS + 1);
        ui.click(juan.player, SCREEN, "cambiar", -1, false);
        assertEquals(List.of("juan"), rerolled, "too late: that tap asks again");

        check.set(MissionReroll.Check.CLAIM_FIRST);
        ui.click(juan.player, SCREEN, "cambiar", -1, false);
        assertEquals(List.of("juan"), rerolled);
        assertEquals("0", ui.variable(juan.player, "bigc_puede_cambiar"), "the button hides while a reward is ready");
        assertTrue(juan.messages.getLast().contains("Cobrá primero"));

        check.set(MissionReroll.Check.ALLOWED);
        ui.click(juan.player, SCREEN, "semanales", -1, false);
        assertEquals("0", ui.variable(juan.player, "bigc_puede_cambiar"), "only on the daily tab");
        assertTrue(ui.variable(juan.player, "bigc_reinicio").endsWith(" · esta semana $900"));
        ui.click(juan.player, SCREEN, "cambiar", -1, false);
        ui.click(juan.player, SCREEN, "cambiar", -1, false);
        assertEquals(List.of("juan"), rerolled);
    }

    @Test
    void reclamarOnTheWeeklyTabStaysOnIt() {
        menu.open(juan.player, MissionScope.WEEKLY, () -> { });
        ui.click(juan.player, SCREEN, "reclamar", -1, false);
        assertEquals("Semanales", ui.variable(juan.player, "bigc_titulo"));
        assertEquals(List.of("Juntar 20 diamantes"), column(juan.player, "titulo"));
    }

    @Test
    void neverMoreRowsThanTheScreenShowsAndTheRestIsNamed() {
        List<MissionAssignment> many = new ArrayList<>();
        for (int i = 0; i < 8; i++) many.add(assignment("m" + i, MissionScope.DAILY, "Mision " + i, 70, 10, 90, false, false));
        state = new MissionPlayerState(juan.id, Instant.now(), Instant.now().plus(Duration.ofHours(1)), Instant.now(), byId(many), Map.of(), null);
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        assertEquals(MissionTideframeMenu.ROWS, column(juan.player, "titulo").size());
        assertEquals("70/70", column(juan.player, "progreso").getFirst(), "progress past the goal shows the goal");
        assertTrue(ui.variable(juan.player, "bigc_reinicio").endsWith("+2 en el menú clásico"));
    }

    @Test
    void whenTideframeCannotShowItTheInventoryMenuOpens() {
        ui.showable = false;
        List<String> fallbacks = new ArrayList<>();
        assertFalse(menu.open(juan.player, MissionScope.DAILY, () -> fallbacks.add("inventory")));
        ui.showable = true;
        ui.opens = null;
        assertTrue(menu.open(juan.player, MissionScope.WEEKLY, () -> fallbacks.add("inventory")));
        ui.finishOpen(false);
        assertEquals(List.of("inventory"), fallbacks, "a refused pack or a timeout falls back to the inventory menu");
        assertFalse(ui.click(juan.player, SCREEN, "diarias", -1, false));
        juan.online = false;
        ui.opens = null;
        menu.open(juan.player, MissionScope.WEEKLY, () -> fallbacks.add("inventory"));
        ui.finishOpen(false);
        assertEquals(1, fallbacks.size(), "nothing opens for a player who left");
    }

    @Test
    void theBundledScreenMatchesWhatThisMenuSendsAndHandles() throws Exception {
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        var contract = dev.linqfy.bigCasares.testsupport.TideframeScreenContract.load(SCREEN);
        contract.buttons(Set.of("diarias", "semanales", "reclamar", "cambiar"), Set.of());
        contract.lists(MissionTideframeMenu.LIST, MissionTideframeMenu.ROWS, Set.of("mision", "progreso", "premio"));
        contract.texts(ui.variables.get(juan.id).keySet());
    }

    @Test
    void theXIsLeftToTideframeAndClosingForgetsTheView() {
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        assertFalse(ui.click(juan.player, SCREEN, "cerrar", -1, false), "the X keeps Tideframe's own close action");
        ui.closedByPlayer(juan.player, SCREEN);
        assertFalse(ui.click(juan.player, SCREEN, "semanales", -1, false));
        menu.close();
        assertFalse(ui.handlers.containsKey(SCREEN));
    }

}
