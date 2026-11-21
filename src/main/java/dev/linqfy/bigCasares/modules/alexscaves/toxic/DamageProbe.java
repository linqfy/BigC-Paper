package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.forlorn.HarmVerdictCache;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** Tells whether a hit really reached its victim, so radiation is only applied where protections let damage through. */
final class DamageProbe {
    /** Clouds ask every few ticks; plugins must not see a fake hit each time (combat tags, deny messages). */
    private static final HarmVerdictCache VERDICTS = new HarmVerdictCache();

    private DamageProbe() {
    }

    /** Health plus absorption: what a hit takes from. */
    static double pool(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    /** A hit landed when it took something from the victim or killed it. */
    static boolean landed(double before, double after, boolean dead) {
        return dead || after < before;
    }

    /**
     * For radiation that is not carried by a damage call (clouds, licks): whether {@code source} could hurt the victim.
     * Only players are protected by claims and zones, so every other victim is let through without raising an event.
     */
    static boolean mayHarm(Entity source, LivingEntity victim) {
        if (!(victim instanceof Player)) {
            return true;
        }
        if (source == null || !source.isValid()) {
            return false;
        }
        return VERDICTS.allowed(source.getUniqueId(), victim.getUniqueId(), victim.getWorld().getGameTime(), () -> {
            DamageSource damage = DamageSource.builder(DamageType.GENERIC).withCausingEntity(source).withDirectEntity(source).build();
            return new EntityDamageByEntityEvent(source, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage, 1.0).callEvent();
        });
    }
}
