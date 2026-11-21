package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import java.util.List;
import java.util.Objects;

/** Un patron completo de rayos (lineas, cruz, anillos...): sus tandas en orden de inicio. */
public record ZeusPattern(ZeusPatternKind kind, List<ZeusStrikeWave> waves) {

    public ZeusPattern {
        Objects.requireNonNull(kind, "kind");
        waves = List.copyOf(Objects.requireNonNull(waves, "waves"));
        if (waves.isEmpty()) {
            throw new IllegalArgumentException("a pattern needs at least one wave");
        }
    }

    /** Tick (relativo) del ultimo rayo: despues de eso el patron termino. */
    public int lastStrikeTick() {
        return waves.stream().mapToInt(ZeusStrikeWave::strikeTick).max().orElse(0);
    }
}
