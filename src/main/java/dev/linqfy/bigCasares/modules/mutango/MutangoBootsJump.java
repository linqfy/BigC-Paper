package dev.linqfy.bigCasares.modules.mutango;

/**
 * El salto de las Botas de Neguechen, por jugador y sin Bukkit: en el aire podes saltar una vez mas. Al tocar el piso se
 * recarga.
 */
final class MutangoBootsJump {

    /** Velocidad para arriba del doble salto (unos 3 bloques mas) y cuanto te empuja para donde miras. */
    static final double JUMP_UP = 0.85;
    static final double JUMP_FORWARD = 0.35;

    private boolean jumped;

    /** Toco el piso: se recarga el doble salto. Devuelve true si venia de un doble salto. */
    boolean land() {
        boolean was = jumped;
        jumped = false;
        return was;
    }

    boolean canDoubleJump() {
        return !jumped;
    }

    /** Hizo el doble salto (solo uno por vez en el aire). Devuelve false si ya lo habia usado. */
    boolean doubleJump() {
        if (jumped) {
            return false;
        }
        jumped = true;
        return true;
    }

    boolean jumped() {
        return jumped;
    }
}
