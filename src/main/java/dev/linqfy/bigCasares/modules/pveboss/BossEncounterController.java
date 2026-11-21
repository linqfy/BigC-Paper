package dev.linqfy.bigCasares.modules.pveboss;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Scripted mechanics of one live boss instance that the weighted ability list cannot express: phases that
 * change on events instead of health, windows in which the boss cannot be hurt, owned minions and so on.
 * The runtime keeps owning the Warden, the health pool, the boss bar and the model; a controller only
 * decides. Every method is called on the server thread.
 */
public interface BossEncounterController {

    /** Called once, right after the boss and its model exist. */
    default void start() {
    }

    /** Called every update of the runtime ({@code ui.update-ticks} of the definition). */
    default void tick(Instant now) {
    }

    /** Called once when the instance ends for any reason. Must be idempotent and remove owned entities. */
    default void stop() {
    }

    /** {@code true} cancels the hit and removes the projectile before any damage is counted. */
    default boolean blocksProjectile(Projectile projectile) {
        return false;
    }

    /**
     * Decides how much of an incoming hit reaches the health pool.
     *
     * @param attacker the player behind the hit, or {@code null}
     * @param damage   the final damage of the event
     * @return the damage to apply; zero or less negates the hit
     */
    default double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        return damage;
    }

    /** The phase to show and to feed to abilities instead of the health thresholds of the definition. */
    default OptionalInt phaseOverride() {
        return OptionalInt.empty();
    }

    /** While {@code true} the ability coordinator is not ticked (no new casts start). */
    default boolean suspendsAbilities() {
        return false;
    }

    /** {@code true} when the controller handled the effect; the generic runtime then skips it. */
    default boolean execute(BossAbilityEffectDefinition effect, List<UUID> targets) {
        return false;
    }
}
