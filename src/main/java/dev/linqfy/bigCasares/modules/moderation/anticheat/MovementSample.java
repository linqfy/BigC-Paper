package dev.linqfy.bigCasares.modules.moderation.anticheat;

/**
 * Un PlayerMoveEvent ya traducido: cuanto se movio, en cuantos ticks del servidor (Paper no dispara el evento por
 * movimientos minimos, asi que un evento puede cubrir varios ticks del cliente) y los limites fisicos del jugador.
 *
 * @param elapsedTicks     ticks del servidor desde el evento anterior de este jugador
 * @param movementSpeed    valor del atributo MOVEMENT_SPEED (ya incluye Speed, Slowness, sprint y modificadores de items)
 * @param sprinting        si el servidor lo ve corriendo (si no, se asume que pudo empezar a correr)
 * @param jumpVelocity     impulso vertical del salto: atributo JUMP_STRENGTH mas Jump Boost
 * @param stepHeight       atributo STEP_HEIGHT (subir un escalon sin saltar)
 * @param exempt           el jugador esta en una situacion que este check no sabe medir (agua, hielo, gracia...)
 */
public record MovementSample(
    int elapsedTicks,
    double horizontal,
    double vertical,
    boolean onGround,
    double movementSpeed,
    boolean sprinting,
    double jumpVelocity,
    double stepHeight,
    boolean exempt
) {
}
