package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Objects;

/**
 * La definicion de un mob mitico (sin servidor): su entidad vanilla base (IA, hitbox y lo que ve Bedrock sin modelo),
 * sus atributos, su conducta extra, lo que suelta y cuanto pesa en el sorteo de spawns de su mazmorra.
 *
 * @param summonId    el mob de la misma cultura que invoca un {@link MythicBehaviour#SUMMONER} ({@code ""} si no invoca)
 * @param scale       atributo {@code scale} de la base (1 = tamano vanilla)
 * @param floating    sin gravedad (fantasmas y voladores)
 * @param phasesThroughWalls atraviesa bloques a proposito (solo el Fantasma): su base no choca con los bloques o su
 *                    conducta lo teletransporta detras del objetivo. Todos los demas chocan con las paredes.
 */
public record MythicMobType(
    String id,
    MythicCulture culture,
    String displayName,
    EntityType baseEntity,
    double health,
    double damage,
    double speed,
    double scale,
    double armor,
    double knockbackResistance,
    MythicBehaviour behaviour,
    MythicProjectile projectile,
    List<MythicEffect> onHit,
    String summonId,
    double fragmentChance,
    double weight,
    int xp,
    List<MythicDrop> drops,
    boolean elite,
    boolean floating,
    boolean phasesThroughWalls
) {

    public MythicMobType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(culture, "culture");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(baseEntity, "baseEntity");
        Objects.requireNonNull(behaviour, "behaviour");
        Objects.requireNonNull(projectile, "projectile");
        Objects.requireNonNull(summonId, "summonId");
        onHit = List.copyOf(onHit);
        drops = List.copyOf(drops);
        if (!(health > 0.0) || damage < 0.0 || !(speed > 0.0) || !(scale > 0.0) || armor < 0.0
            || knockbackResistance < 0.0 || knockbackResistance > 1.0 || xp < 0) {
            throw new IllegalArgumentException("invalid stats for mythic mob " + id);
        }
        if (fragmentChance < 0.0 || fragmentChance > 1.0 || !(weight > 0.0)) {
            throw new IllegalArgumentException("invalid fragment chance or weight for mythic mob " + id);
        }
    }

    /** Clave de BetterModel: {@code bigcasares_myth_<id>}. */
    public String modelKey() {
        return MythicCulture.mobModelKey(id);
    }

    /** Ancho de la hitbox ya escalada (la vanilla de la base por {@link #scale()}). */
    public double hitboxWidth() {
        return MythicBody.scaled(baseEntity, scale).width();
    }

    /** Alto de la hitbox ya escalada. */
    public double hitboxHeight() {
        return MythicBody.scaled(baseEntity, scale).height();
    }

    /** Si el modulo lo dirige volando (base voladora con la IA vanilla apagada). */
    public boolean steeredFlyer() {
        return MythicBody.steeredFlyer(baseEntity);
    }

    /** La misma definicion con los numeros que se pueden cambiar desde {@code mythic-dungeons.yml}. */
    public MythicMobType withTuning(double health, double damage, double speed, double fragmentChance, double weight) {
        return new MythicMobType(id, culture, displayName, baseEntity, health, damage, speed, scale, armor,
            knockbackResistance, behaviour, projectile, onHit, summonId, fragmentChance, weight, xp, drops, elite,
            floating, phasesThroughWalls);
    }
}
