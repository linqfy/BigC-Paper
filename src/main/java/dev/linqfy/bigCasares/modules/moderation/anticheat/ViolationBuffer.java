package dev.linqfy.bigCasares.modules.moderation.anticheat;

/**
 * Nivel de violacion con decaimiento. Una muestra sospechosa suma, una muestra limpia resta: solo se reporta cuando las
 * muestras malas se acumulan mas rapido de lo que se olvidan, asi un pico suelto (lag, un empujon que no vimos) no alcanza.
 */
final class ViolationBuffer {
    private final double threshold;
    private final double decay;
    private final double afterFlag;
    private double level;

    ViolationBuffer(double threshold, double decay, double afterFlag) {
        if (threshold <= 0 || decay < 0 || afterFlag < 0 || afterFlag >= threshold) {
            throw new IllegalArgumentException("Invalid violation buffer");
        }
        this.threshold = threshold;
        this.decay = decay;
        this.afterFlag = afterFlag;
    }

    /** Suma una violacion; devuelve true si con ella se cruza el umbral (y el nivel baja a afterFlag). */
    boolean add(double amount) {
        level += Math.max(0, amount);
        if (level >= threshold) {
            level = afterFlag;
            return true;
        }
        return false;
    }

    void decay() {
        level = Math.max(0, level - decay);
    }

    void reset() {
        level = 0;
    }

    double level() {
        return level;
    }
}
