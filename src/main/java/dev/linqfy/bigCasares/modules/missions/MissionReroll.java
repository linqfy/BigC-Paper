package dev.linqfy.bigCasares.modules.missions;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Whether a player may change their daily missions now: once per daily period, and never while a daily reward is
 * ready and unclaimed (the change replaces every daily mission, and that reward would be lost).
 */
final class MissionReroll {
    /** How long the second tap of "Cambiar" may take to confirm it. */
    static final long CONFIRM_NANOS = 3_000_000_000L;

    enum Check { ALLOWED, USED_TODAY, CLAIM_FIRST }

    private MissionReroll() {
    }

    /** The start of the daily period {@code now} is in (the period resets at {@code hour} in {@code zone}). */
    static Instant periodStart(Instant now, int hour, ZoneId zone) {
        LocalDateTime local = LocalDateTime.ofInstant(now, zone);
        LocalDateTime start = local.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (start.isAfter(local)) {
            start = start.minusDays(1);
        }
        return start.atZone(zone).toInstant();
    }

    static Check check(MissionPlayerState state, Instant periodStart) {
        if (state.dailyRerolledAt() != null && !state.dailyRerolledAt().isBefore(periodStart)) {
            return Check.USED_TODAY;
        }
        boolean readyDaily = state.dailyAssignments().values().stream()
            .anyMatch(assignment -> assignment.snapshot().completed() && !assignment.snapshot().claimed());
        return readyDaily ? Check.CLAIM_FIRST : Check.ALLOWED;
    }

    /** The chat line for a change that cannot happen. */
    static String refusal(Check check) {
        return check == Check.USED_TODAY ? "§cYa usaste tu rerol diario. Espera al reinicio diario."
            : "§cCobrá primero tus misiones diarias listas: cambiarlas las borraría.";
    }
}
