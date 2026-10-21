package dev.linqfy.bigCasares.modules.mutango;

/**
 * La secuencia del despertar del santuario (sin Bukkit), en ticks desde el click: el cubo se corre, sube una esferita del
 * agujero, se divide en tres que van a las estatuas, las tocan, el cubo se vuelve a cerrar y cobran vida. Aca estan los
 * tiempos y como avanza cada tramo (de 0 a 1).
 */
final class MutangoShrineTimeline {

    /** El cubo se corre durante estos ticks. */
    static final int SLIDE_TICKS = 20;
    /** La esferita termina de subir. */
    static final int RISE_END = 50;
    /** Se divide en tres (y la esferita se queda flotando hasta aca). */
    static final int SPLIT_AT = 60;
    /** Las tres llegan a las estatuas. */
    static final int ARRIVE_AT = 90;
    /** El cubo termino de cerrarse y las estatuas cobran vida. */
    static final int CLOSE_END = 105;

    private MutangoShrineTimeline() {
    }

    /** 1) El cubo se corre un bloque, frenando: de 0 a 1 mientras tick <= SLIDE_TICKS. */
    static double slide(int tick) {
        return 1 - Math.pow(1 - clamp(tick / (double) SLIDE_TICKS), 3);
    }

    /** 2) La esferita sube del agujero: de 0 a 1 entre el fin del corrimiento y RISE_END (y se queda arriba). */
    static double rise(int tick) {
        double s = clamp((tick - SLIDE_TICKS) / (double) (RISE_END - SLIDE_TICKS));
        return 1 - Math.pow(1 - s, 2);
    }

    /** 3) Las tres esferitas viajan a las estatuas: de 0 a 1 entre SPLIT_AT y ARRIVE_AT. */
    static double travel(int tick) {
        return clamp((tick - SPLIT_AT) / (double) (ARRIVE_AT - SPLIT_AT));
    }

    /** 4) El cubo vuelve a cerrarse: de 0 a 1 entre ARRIVE_AT y CLOSE_END. */
    static double close(int tick) {
        return 1 - Math.pow(1 - clamp((tick - ARRIVE_AT) / (double) (CLOSE_END - ARRIVE_AT)), 3);
    }

    private static double clamp(double s) {
        return Math.max(0.0, Math.min(1.0, s));
    }
}
