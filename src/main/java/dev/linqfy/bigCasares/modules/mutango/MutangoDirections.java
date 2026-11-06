package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.util.Vector;

/**
 * Direcciones horizontales a partir del yaw de una entidad (yaw 0 mira a +Z, 90 a -X, como en Minecraft), sin tocar el
 * servidor. Los modelos de BetterModel tienen el frente en -Z del modelo y BetterModel los gira para que ese frente
 * mire hacia donde mira la entidad.
 */
final class MutangoDirections {

    private MutangoDirections() {
    }

    /** Hacia donde mira (el frente del modelo). */
    static Vector forward(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad));
    }

    /** El +X del modelo (su izquierda): mirandolo de frente, queda a la izquierda de quien mira. */
    static Vector leftOf(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.cos(rad), 0, -Math.sin(rad));
    }

    /** El yaw que mira hacia direction (solo cuenta lo horizontal). */
    static float yaw(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }
}
