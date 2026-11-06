package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * El dano y el empuje de los ataques a jugadores. El dano sale con la entidad que pega como causa directa (no solo un
 * numero), asi el escudo lo puede frenar, el mensaje de muerte sabe quien fue y el empuje respeta la resistencia al
 * retroceso. La causa no se marca como "causada por un mob": ese tipo se anula en dificultad pacifica y escala con la
 * dificultad del servidor, y la dificultad de las peleas ya la maneja MutangoDifficulty.
 */
final class MutangoDamage {

    private MutangoDamage() {
    }

    /** Le hace dano a victim con attacker como quien pego (si attacker ya no esta, dano comun). */
    static void hurt(Player victim, double amount, Entity attacker) {
        if (attacker == null || !attacker.isValid()) {
            victim.damage(amount);
            return;
        }
        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
            .withDirectEntity(attacker)
            .withDamageLocation(attacker.getLocation())
            .build();
        victim.damage(amount, source);
    }

    /**
     * El rayo de Warden: dano de "estallido sonico", que no lo frena la armadura (como el del Warden). Como en hurt, la
     * causa directa es attacker (asi el mensaje de muerte sabe quien fue) y no se marca como "causado por un mob".
     */
    static void sonicBoom(Player victim, double amount, Entity attacker) {
        if (attacker == null || !attacker.isValid()) {
            victim.damage(amount);
            return;
        }
        DamageSource source = DamageSource.builder(DamageType.SONIC_BOOM)
            .withDirectEntity(attacker)
            .withDamageLocation(attacker.getLocation())
            .build();
        victim.damage(amount, source);
    }

    /** Lo empuja con esa velocidad, menos lo que resiste al retroceso (por ejemplo con armadura de netherite). */
    static void push(Player victim, Vector velocity) {
        var resistance = victim.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        double kept = resistance == null ? 1.0 : Math.max(0.0, 1.0 - resistance.getValue());
        victim.setVelocity(velocity.multiply(kept));
    }
}
