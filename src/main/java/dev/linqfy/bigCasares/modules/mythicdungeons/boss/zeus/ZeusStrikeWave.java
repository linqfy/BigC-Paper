package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import java.util.Objects;
import java.util.Set;

/**
 * Una tanda de rayos de un patron: sus baldosas se marcan en el piso desde {@code startTick} (relativo al comienzo
 * del patron) durante {@code telegraphTicks} y caen todas juntas en {@link #strikeTick()}.
 */
public record ZeusStrikeWave(int startTick, int telegraphTicks, Set<ZeusCell> cells) {

    public ZeusStrikeWave {
        if (startTick < 0) {
            throw new IllegalArgumentException("startTick must not be negative");
        }
        if (telegraphTicks < 1) {
            throw new IllegalArgumentException("telegraphTicks must be positive");
        }
        cells = Set.copyOf(Objects.requireNonNull(cells, "cells"));
    }

    public int strikeTick() {
        return startTick + telegraphTicks;
    }
}
