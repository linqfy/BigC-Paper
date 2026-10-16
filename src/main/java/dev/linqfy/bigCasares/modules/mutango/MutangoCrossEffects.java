package dev.linqfy.bigCasares.modules.mutango;

import java.util.List;

/**
 * Lo que da la Cruz Andina cuando te salva (como un totem, pero mejor: dura mas y da mas cosas). Sin Bukkit: los
 * efectos van por su nombre de Minecraft y MutangoRelics los busca en el registro.
 */
final class MutangoCrossEffects {

    /** Un efecto: nombre de Minecraft, nivel (0 = I) y cuanto dura en ticks. */
    record Effect(String key, int amplifier, int ticks) {
    }

    /** Lo que da el totem comun (para comparar): Regeneracion II 45 s, Absorcion II 5 s y Resistencia al fuego 40 s. */
    static final List<Effect> TOTEM = List.of(
        new Effect("regeneration", 1, 45 * 20),
        new Effect("absorption", 1, 5 * 20),
        new Effect("fire_resistance", 0, 40 * 20));

    static final List<Effect> CROSS = List.of(
        new Effect("regeneration", 2, 60 * 20),
        new Effect("absorption", 3, 60 * 20),
        new Effect("fire_resistance", 0, 120 * 20),
        new Effect("resistance", 1, 30 * 20),
        new Effect("speed", 1, 30 * 20),
        new Effect("strength", 0, 30 * 20),
        new Effect("water_breathing", 0, 60 * 20));

    /** El totem comun te deja en medio corazon (1 de vida); la cruz te deja con la mitad de la vida. */
    static final double HEALTH_FRACTION = 0.5;

    private MutangoCrossEffects() {
    }

    /** La vida con la que te deja la cruz, segun tu vida maxima (nunca menos que el totem comun). */
    static double healthAfter(double maxHealth) {
        return Math.max(1.0, maxHealth * HEALTH_FRACTION);
    }
}
