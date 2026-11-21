package dev.linqfy.bigCasares.modules.pveboss;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Wraps an external {@link BossEncounterController} so that it starts at most once, stops exactly once and
 * can never break the runtime: every failure is reported and replaced by the neutral default behaviour.
 * After {@link #stop()} the controller is no longer consulted.
 */
final class GuardedBossEncounter {

    private final BossEncounterController controller;
    private final BiConsumer<String, RuntimeException> failures;
    private boolean started;
    private boolean stopped;

    GuardedBossEncounter(BossEncounterController controller, BiConsumer<String, RuntimeException> failures) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    /** Returns {@code false} when the controller threw while starting (the failure is reported). */
    boolean start() {
        if (started || stopped) {
            return !stopped;
        }
        started = true;
        try {
            controller.start();
            return true;
        } catch (RuntimeException failure) {
            failures.accept("start", failure);
            return false;
        }
    }

    void tick(Instant now) {
        if (stopped) {
            return;
        }
        try {
            controller.tick(now);
        } catch (RuntimeException failure) {
            failures.accept("tick", failure);
        }
    }

    void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        try {
            controller.stop();
        } catch (RuntimeException failure) {
            failures.accept("stop", failure);
        }
    }

    boolean isStopped() {
        return stopped;
    }

    boolean blocksProjectile(Projectile projectile) {
        if (stopped) {
            return false;
        }
        try {
            return controller.blocksProjectile(projectile);
        } catch (RuntimeException failure) {
            failures.accept("blocksProjectile", failure);
            return false;
        }
    }

    /** The admitted damage (see {@link BossEncounterRules#admittedDamage}); a failing filter lets the hit through. */
    double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        if (stopped) {
            return BossEncounterRules.admittedDamage(damage);
        }
        try {
            return BossEncounterRules.admittedDamage(controller.filterDamage(event, attacker, damage));
        } catch (RuntimeException failure) {
            failures.accept("filterDamage", failure);
            return BossEncounterRules.admittedDamage(damage);
        }
    }

    int phase(int healthPhase) {
        if (stopped) {
            return healthPhase;
        }
        try {
            OptionalInt override = controller.phaseOverride();
            return BossEncounterRules.resolvePhase(override == null ? OptionalInt.empty() : override, healthPhase);
        } catch (RuntimeException failure) {
            failures.accept("phaseOverride", failure);
            return healthPhase;
        }
    }

    boolean suspendsAbilities() {
        if (stopped) {
            return false;
        }
        try {
            return controller.suspendsAbilities();
        } catch (RuntimeException failure) {
            failures.accept("suspendsAbilities", failure);
            return false;
        }
    }

    boolean execute(BossAbilityEffectDefinition effect, List<UUID> targets) {
        if (stopped) {
            return false;
        }
        try {
            return controller.execute(effect, targets);
        } catch (RuntimeException failure) {
            failures.accept("execute " + effect.type(), failure);
            // The effect was meant for the controller; running the generic version instead would surprise.
            return true;
        }
    }
}
