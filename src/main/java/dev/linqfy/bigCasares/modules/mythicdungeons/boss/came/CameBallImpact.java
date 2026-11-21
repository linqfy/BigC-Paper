package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

/**
 * El pelotazo a quien no devolvio la pelota: la mitad de su vida maxima, sin armadura. Si con eso no le alcanza la vida,
 * es letal (el controlador lo mata por la via normal de daño, asi andan los totems y el mensaje de muerte).
 */
public record CameBallImpact(double remainingHealth, boolean lethal) {

    public static final double SHARE = 0.5;

    public static CameBallImpact against(double health, double maximumHealth) {
        double remaining = health - maximumHealth * SHARE;
        return remaining > 0.0 ? new CameBallImpact(remaining, false) : new CameBallImpact(0.0, true);
    }
}
