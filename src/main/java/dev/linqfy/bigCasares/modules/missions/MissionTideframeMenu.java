package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.modules.tideframe.TideframeClick;
import dev.linqfy.bigCasares.modules.tideframe.TideframeScreenHandler;
import dev.linqfy.bigCasares.modules.tideframe.TideframeUi;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The missions as a Tideframe screen for Java players: the daily or weekly list with each mission's progress, status
 * and reward. "Reclamar" claims every ready reward through the module's own claim (the one the inventory menu uses,
 * which pays only what is not claimed yet) and redraws the list in place. The texts come from the same state the
 * inventory menu reads. The X keeps Tideframe's own close.
 */
final class MissionTideframeMenu implements TideframeScreenHandler {
    static final String SCREEN = "bigc_misiones";
    static final String LIST = "bigc_misiones";
    /** Rows the screen's list shows; never more data rows than this (Tideframe's row index is then the data index). */
    static final int ROWS = 6;

    private final Function<Player, MissionPlayerState> states;
    private final Function<MissionPlayerState, Integer> claimables;
    private final DoubleFunction<String> money;
    private final Supplier<TideframeUi> ui;
    private final Consumer<Player> claim;
    private final Function<Player, MissionReroll.Check> rerollCheck;
    private final Consumer<Player> reroll;
    private final LongSupplier clock;
    /** The tab's extra line: the streak on Diarias, this week's claimed money on Semanales ("" for none). */
    private final java.util.function.BiFunction<Player, MissionScope, String> summary;
    private final Map<UUID, MissionScope> tabs = new ConcurrentHashMap<>();
    /** When each player first tapped "Cambiar" ({@link System#nanoTime()}): a second tap soon after confirms it. */
    private final Map<UUID, Long> confirming = new ConcurrentHashMap<>();
    private AutoCloseable registration = () -> { };

    /** Without "Cambiar" (the button stays hidden). */
    MissionTideframeMenu(Function<Player, MissionPlayerState> states, Function<MissionPlayerState, Integer> claimables,
                         DoubleFunction<String> money, Supplier<TideframeUi> ui, Consumer<Player> claim) {
        this(states, claimables, money, ui, claim, player -> MissionReroll.Check.USED_TODAY, player -> { }, System::nanoTime,
            (player, scope) -> "");
    }

    /**
     * {@code claim}: pays the player's ready rewards at once (and tells them in chat). {@code rerollCheck} and
     * {@code reroll}: whether the player may change their daily missions, and the change itself.
     */
    MissionTideframeMenu(Function<Player, MissionPlayerState> states, Function<MissionPlayerState, Integer> claimables,
                         DoubleFunction<String> money, Supplier<TideframeUi> ui, Consumer<Player> claim,
                         Function<Player, MissionReroll.Check> rerollCheck, Consumer<Player> reroll, LongSupplier clock,
                         java.util.function.BiFunction<Player, MissionScope, String> summary) {
        this.states = Objects.requireNonNull(states, "states");
        this.claimables = Objects.requireNonNull(claimables, "claimables");
        this.money = Objects.requireNonNull(money, "money");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.claim = Objects.requireNonNull(claim, "claim");
        this.rerollCheck = Objects.requireNonNull(rerollCheck, "rerollCheck");
        this.reroll = Objects.requireNonNull(reroll, "reroll");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    /**
     * Shows {@code scope}'s missions; false when Tideframe cannot show the screen now (the caller opens the inventory
     * menu). When the opening fails later (pack refused, timeout), {@code fallback} runs instead.
     */
    boolean open(Player player, MissionScope scope, Runnable fallback) {
        TideframeUi tideframe = ui.get();
        if (!tideframe.canShow(player, SCREEN)) {
            return false;
        }
        registration = tideframe.handle(SCREEN, this);
        UUID playerId = player.getUniqueId();
        tideframe.open(player, SCREEN, () -> {
            tabs.put(playerId, scope);
            render(tideframe, player, scope);
        }, opened -> {
            if (!opened) {
                tabs.remove(playerId);
                if (player.isOnline()) {
                    fallback.run();
                }
            }
        });
        return true;
    }

    @Override
    public boolean click(Player player, TideframeClick click) {
        MissionScope current = tabs.get(player.getUniqueId());
        if (current == null) {
            return false;
        }
        TideframeUi tideframe = ui.get();
        switch (click.elementId()) {
            case "diarias" -> show(tideframe, player, MissionScope.DAILY);
            case "semanales" -> show(tideframe, player, MissionScope.WEEKLY);
            case "reclamar" -> {
                // Left click only. Cancelled either way, so a screen still carrying the old "close" stays open.
                if (!click.rightClick()) {
                    claim.accept(player);
                    show(tideframe, player, current);
                }
            }
            case "cambiar" -> {
                // Daily tab only, and only when allowed; the second tap within a few seconds changes them.
                UUID playerId = player.getUniqueId();
                if (current == MissionScope.DAILY && !click.rightClick()) {
                    MissionReroll.Check check = rerollCheck.apply(player);
                    Long first = confirming.remove(playerId);
                    if (check != MissionReroll.Check.ALLOWED) {
                        player.sendMessage(MissionReroll.refusal(check));
                    } else if (first != null && clock.getAsLong() - first <= MissionReroll.CONFIRM_NANOS) {
                        reroll.accept(player);
                    } else {
                        confirming.put(playerId, clock.getAsLong());
                    }
                }
                show(tideframe, player, current);
            }
            // The X ("cerrar") keeps Tideframe's own close action; closed() hears it.
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void closed(Player player) {
        tabs.remove(player.getUniqueId());
        confirming.remove(player.getUniqueId());
    }

    /** BigCasares reloads: forget the open screens (the Tideframe module closes them) and stop receiving clicks. */
    void close() {
        tabs.clear();
        confirming.clear();
        try {
            registration.close();
        } catch (Exception ignored) {
            // The registration only removes a map entry.
        }
    }

    private void show(TideframeUi tideframe, Player player, MissionScope scope) {
        tabs.put(player.getUniqueId(), scope);
        render(tideframe, player, scope);
    }

    private void render(TideframeUi tideframe, Player player, MissionScope scope) {
        MissionPlayerState state = states.apply(player);
        boolean daily = scope == MissionScope.DAILY;
        Collection<MissionAssignment> assignments = (daily ? state.dailyAssignments() : state.weeklyAssignments()).values();
        Instant resetsAt = daily ? state.dailyResetsAt() : state.weeklyResetsAt();
        List<Map<String, String>> rows = rows(assignments, money);
        tideframe.setList(player, LIST, rows);
        tideframe.setVariable(player, "bigc_titulo", daily ? "Diarias" : "Semanales");
        int hidden = assignments.size() - rows.size();
        String extra = summary.apply(player, scope);
        tideframe.setVariable(player, "bigc_reinicio", "se renuevan en " + MissionTexts.formatTimeRemaining(Duration.between(Instant.now(), resetsAt))
            + (extra.isEmpty() ? "" : " · " + extra)
            + (hidden > 0 ? " · +" + hidden + " en el menú clásico" : ""));
        int pending = claimables.apply(state);
        tideframe.setVariable(player, "bigc_pendientes", pending == 0 ? "Nada para cobrar"
            : pending + " para cobrar · " + plain(money.apply(MissionOrder.pendingReward(state))));
        tideframe.setVariable(player, "bigc_cobrables", String.valueOf(pending));
        tideframe.setVariable(player, "bigc_vacio", rows.isEmpty() ? "No hay misiones activas" : "");
        // "Cambiar" shows on the daily tab while the change is possible; after one tap it asks to confirm.
        boolean canChange = daily && rerollCheck.apply(player) == MissionReroll.Check.ALLOWED;
        Long first = confirming.get(player.getUniqueId());
        boolean asking = first != null && clock.getAsLong() - first <= MissionReroll.CONFIRM_NANOS;
        tideframe.setVariable(player, "bigc_puede_cambiar", canChange ? "1" : "0");
        tideframe.setVariable(player, "bigc_cambiar", asking ? "¿Seguro?" : "Cambiar");
    }

    /** The list rows: at most {@link #ROWS}, ready to claim first (so they are never the hidden ones), then the rest. */
    static List<Map<String, String>> rows(Collection<MissionAssignment> assignments, DoubleFunction<String> money) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (MissionAssignment assignment : MissionOrder.readyFirst(assignments)) {
            if (rows.size() == ROWS) {
                break;
            }
            MissionDefinition definition = assignment.definition();
            MissionProgressSnapshot snapshot = assignment.snapshot();
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("titulo", plain(definition.title()));
            String count = Math.min(snapshot.progress(), definition.goal()) + "/" + definition.goal();
            row.put("estado", snapshot.claimed() ? "Cobrada" : snapshot.completed() ? "Para cobrar" : "En curso");
            // One column: the count while it runs, then whether it is ready to claim or already claimed.
            row.put("progreso", snapshot.claimed() ? "Cobrada" : snapshot.completed() ? "¡Lista!" : count);
            row.put("premio", plain(money.apply(definition.reward())));
            rows.add(row);
        }
        return rows;
    }

    /** Text without Minecraft colour codes (Tideframe draws its own colours). */
    static String plain(String text) {
        return text == null ? "" : text.replaceAll("(?i)[§&][0-9a-fk-orx]", "").strip();
    }
}
