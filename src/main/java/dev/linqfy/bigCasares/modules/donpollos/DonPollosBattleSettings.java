package dev.linqfy.bigCasares.modules.donpollos;

import java.util.Objects;

public record DonPollosBattleSettings(
    int hitsToWin,
    int lives,
    int startWindowTicks,
    int minWindowTicks,
    int pauseTicks,
    DonPollosItem reward,
    double maxDistance
) {

    public DonPollosBattleSettings {
        Objects.requireNonNull(reward, "reward");
        if (hitsToWin < 1 || lives < 1) {
            throw new IllegalArgumentException("salsero.pvp needs hits-to-win and lives >= 1");
        }
        if (minWindowTicks < 5 || startWindowTicks < minWindowTicks) {
            throw new IllegalArgumentException("salsero.pvp needs 5 <= min-window-ticks <= window-ticks");
        }
        if (pauseTicks < 0 || maxDistance <= 0) {
            throw new IllegalArgumentException("salsero.pvp pause-ticks and max-distance must be positive");
        }
    }

    public static DonPollosBattleSettings defaults() {
        return new DonPollosBattleSettings(12, 3, 50, 22, 12, new DonPollosItem("DIAMOND", 5), 16.0);
    }
}
