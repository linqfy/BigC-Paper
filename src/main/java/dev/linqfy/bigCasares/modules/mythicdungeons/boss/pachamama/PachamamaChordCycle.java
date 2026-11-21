package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import java.util.Optional;

/**
 * When Mandinga strums (no server types): {@link Cue#STRUM} {@link PachamamaTuning#CHORD_WARN_TICKS} before
 * every {@link Cue#WAVE}, a wave every {@link PachamamaTuning#CHORD_PERIOD_TICKS}, the first one
 * {@link PachamamaTuning#FIRST_CHORD_TICKS} after the cycle starts.
 */
public final class PachamamaChordCycle {

    public enum Cue { STRUM, WAVE }

    private int untilWave = PachamamaTuning.FIRST_CHORD_TICKS;

    public Optional<Cue> advance() {
        untilWave--;
        if (untilWave == PachamamaTuning.CHORD_WARN_TICKS) {
            return Optional.of(Cue.STRUM);
        }
        if (untilWave <= 0) {
            untilWave = PachamamaTuning.CHORD_PERIOD_TICKS;
            return Optional.of(Cue.WAVE);
        }
        return Optional.empty();
    }
}
