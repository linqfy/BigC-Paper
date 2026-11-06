package dev.linqfy.bigCasares.modules.mutango;

/**
 * Los tiempos de un verso en pantalla (sin Bukkit): va apareciendo letra por letra (la maquina de escribir), se queda
 * un rato con el verso completo, se desvanece y deja un respiro antes del siguiente.
 */
final class MutangoVerseTiming {

    /** Letras que aparecen por tick. */
    static final double LETTERS_PER_TICK = 1.0;
    /** El verso completo se queda este rato... */
    static final int HOLD_TICKS = 55;
    /** ...y despues se desvanece. */
    static final int FADE_OUT_TICKS = 20;
    /** Respiro antes del siguiente. */
    static final int GAP_TICKS = 6;
    /** Cada cuanto se vuelve a mandar el verso por la barra de abajo (dura poco). */
    static final int ACTION_BAR_RESEND_TICKS = 20;

    /** En que momento del verso se esta. */
    enum Stage {
        /** Escribiendose: se muestran las primeras letras. */
        TYPING,
        /** El tick en que ya esta completo: se manda entero con su tiempo de espera y desvanecido. */
        COMPLETE,
        /** Esperando con el verso completo en pantalla. */
        HOLDING,
        /** Termino: sigue el siguiente. */
        FINISHED
    }

    private MutangoVerseTiming() {
    }

    /** Ticks que tarda en escribirse un verso de esa cantidad de letras. */
    static int reveal(int letters) {
        return (int) Math.ceil(letters / LETTERS_PER_TICK);
    }

    /** Ticks que dura el verso entero, hasta pasar al siguiente. */
    static int total(int letters) {
        return reveal(letters) + HOLD_TICKS + FADE_OUT_TICKS + GAP_TICKS;
    }

    /** Cuantas letras se ven en ese tick (contando desde 1). */
    static int shown(int letters, int tick) {
        return Math.min(letters, (int) Math.ceil(tick * LETTERS_PER_TICK));
    }

    static Stage stage(int letters, int tick) {
        int reveal = reveal(letters);
        if (tick <= reveal) {
            return Stage.TYPING;
        }
        if (tick >= total(letters)) {
            return Stage.FINISHED;
        }
        return tick == reveal + 1 ? Stage.COMPLETE : Stage.HOLDING;
    }

    /** En la barra de abajo hay que volver a mandar el verso mientras se queda en pantalla. */
    static boolean resendOnActionBar(int letters, int tick) {
        int reveal = reveal(letters);
        return tick > reveal + 1 && (tick - reveal) % ACTION_BAR_RESEND_TICKS == 0 && tick < reveal + HOLD_TICKS;
    }
}
