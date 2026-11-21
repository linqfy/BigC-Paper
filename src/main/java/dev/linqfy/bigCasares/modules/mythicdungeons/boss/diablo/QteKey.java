package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

/**
 * Las teclas que puede pedir el QTE de la fase 2. Paper sin mods de cliente no ve teclas arbitrarias: lo unico que el
 * cliente le manda es el paquete de input de movimiento, que Paper expone como {@code Player#getCurrentInput()}
 * (adelante, atras, izquierda, derecha, salto, agacharse y correr). Son las siete de este enum y nada mas; las teclas
 * se leen como estado (apretada o no), asi que una pulsacion es un flanco de subida entre dos ticks.
 *
 * <p>Bedrock: Geyser traduce el input de Bedrock al mismo paquete de Java, asi que se lee igual. Algunos controles
 * (tactil, mando con stick analogico) no siempre reportan las direcciones o correr como tecla; para esos jugadores
 * cada direccion acepta tambien su {@link #alternative()} (salto o agacharse), que Bedrock siempre reporta.</p>
 *
 * <p>Con "agacharse" o "correr" en modo alternar (toggle) del cliente, cada pulsacion cambia el estado, asi que solo
 * cuenta una de cada dos; el modo por defecto (mantener) cuenta todas.</p>
 */
public enum QteKey {
    FORWARD("§e§l¡W!", "W"),
    BACKWARD("§e§l¡S!", "S"),
    LEFT("§e§l¡A!", "A"),
    RIGHT("§e§l¡D!", "D"),
    JUMP("§e§l¡SALTÁ!", "ESPACIO"),
    SNEAK("§e§l¡AGACHATE!", "SHIFT"),
    SPRINT("§e§l¡CORRÉ!", "CTRL");

    private final String title;
    private final String keyName;

    QteKey(String title, String keyName) {
        this.title = title;
        this.keyName = keyName;
    }

    /** El titulo que se le muestra al jugador. */
    public String title() {
        return title;
    }

    /** Nombre corto de la tecla por defecto en Java (para el subtitulo). */
    public String keyName() {
        return keyName;
    }

    /** La tecla que se acepta en su lugar con controles que no reportan esta (null para salto y agacharse). */
    public QteKey alternative() {
        return switch (this) {
            case FORWARD, LEFT, SPRINT -> JUMP;
            case BACKWARD, RIGHT -> SNEAK;
            case JUMP, SNEAK -> null;
        };
    }
}
