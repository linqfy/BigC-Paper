package dev.linqfy.bigCasares.modules.donpollos;

/**
 * Una ronda de las Batallas del Cubo.
 *
 * @param labubus        cuantos Labubus salen en la ronda
 * @param spawnSeconds   cada cuanto sale un Labubu (mas chico = mas rapido)
 * @param colonelSpeed   multiplicador de velocidad del Coronel (1 = zombie normal)
 * @param colonelDamage  dano extra del Coronel (se suma al de zombie con espada de netherite)
 */
public record DonPollosCubeRound(int labubus, double spawnSeconds, double colonelSpeed, double colonelDamage) {

    public DonPollosCubeRound {
        if (labubus < 0 || spawnSeconds < 0.05 || colonelSpeed <= 0 || colonelDamage < 0) {
            throw new IllegalArgumentException("ronda invalida: labubus >= 0, cada >= 0.05, velocidad > 0 y dano >= 0");
        }
    }

    long spawnTicks() {
        return Math.max(1, Math.round(spawnSeconds * 20));
    }
}
