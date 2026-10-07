package dev.linqfy.bigCasares.modules.donpollos;

/**
 * FASE FINAL del Don Pollo Boss ("Me haz hecho enojar, Papi Don Pollo esta enojado").
 *
 * @param growFactor           cuanto se agranda (1.25 = 25% mas grande)
 * @param beamCooldownSeconds  cada cuanto tira el rayo de warden
 * @param beamDamage           dano del rayo (el del warden es 10)
 * @param beamRange            largo del rayo
 * @param jumpCooldownSeconds  cada cuanto pega un mega salto
 * @param jumpLandDistance     a cuantos bloques tuyos cae
 * @param shockwaveRadius      hasta donde llega la onda expansiva
 * @param shockwaveDamage      dano de la onda pegada al impacto (en el borde hace un tercio)
 * @param colonelSeconds       cada cuanto invoca Sanders fase final
 * @param colonelsPerWave      cuantos por tanda
 * @param maxColonels          tope de Sanders fase final vivos
 * @param colonelSpeed         multiplicador de velocidad (1 = zombie)
 * @param colonelDamage        dano extra sobre el de zombie con espada de netherite
 * @param colonelHealth        vida de cada Sanders fase final
 */
public record DonPollosBossPhase4Settings(
    double growFactor,
    double beamCooldownSeconds,
    double beamDamage,
    double beamRange,
    double jumpCooldownSeconds,
    double jumpLandDistance,
    double shockwaveRadius,
    double shockwaveDamage,
    double colonelSeconds,
    int colonelsPerWave,
    int maxColonels,
    double colonelSpeed,
    double colonelDamage,
    double colonelHealth
) {

    public DonPollosBossPhase4Settings {
        if (growFactor < 1 || growFactor > 2 || beamCooldownSeconds < 3 || beamDamage < 0 || beamRange < 4 || beamRange > 64) {
            throw new IllegalArgumentException("boss.fase-final: grow-factor 1-2, beam-cooldown-seconds >= 3, beam-range 4-64");
        }
        if (jumpCooldownSeconds < 3 || jumpLandDistance < 2 || shockwaveRadius < 2 || shockwaveRadius > 32
            || shockwaveDamage < 0) {
            throw new IllegalArgumentException("boss.fase-final: jump-cooldown-seconds >= 3, land-distance >= 2, radio 2-32");
        }
        if (colonelSeconds < 1 || colonelsPerWave < 0 || maxColonels < 0 || colonelSpeed <= 0 || colonelDamage < 0
            || colonelHealth < 1) {
            throw new IllegalArgumentException("boss.fase-final: coroneles con colonel-seconds >= 1 y valores validos");
        }
    }

    public static DonPollosBossPhase4Settings defaults() {
        return new DonPollosBossPhase4Settings(1.25, 6, 10, 32, 9, 8, 12, 12, 10, 2, 6, 1.6, 8, 40);
    }
}
