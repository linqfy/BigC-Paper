package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.modules.pveboss.BossAbilityEffectDefinition;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterController;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Como termino cada jefe mitico: derrotado o sacado sin derrota (chunk descargado, {@code /bc reload}, modulo apagado,
 * reinicio, {@code /mazmorra quitar}). pve-boss no lo dice en el {@code stop()} del encuentro, pero en una derrota
 * llama al callback de premios ({@link #defeated}) justo antes de bajar la instancia, en el mismo tick; asi el proximo
 * {@code stop()} de esa cultura es el de la derrota y cualquier otro es una salida sin derrota. Sin servidor.
 */
final class MythicBossEndings {

    /** Lo que se avisa una sola vez por instancia, cuando termina. */
    @FunctionalInterface
    interface Listener {

        void ended(UUID bossInstanceId, MythicCulture culture, boolean defeated);
    }

    private final Map<MythicCulture, Integer> pendingDefeats = new EnumMap<>(MythicCulture.class);
    private final Listener listener;

    MythicBossEndings(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /** Lo llama el callback de premios de pve-boss: la instancia de esa cultura que termina ahora fue derrotada. */
    void defeated(MythicCulture culture) {
        pendingDefeats.merge(culture, 1, Integer::sum);
    }

    /** El mismo encuentro, que ademas avisa a {@link Listener} cuando termina. */
    BossEncounterControllerFactory wrap(MythicCulture culture, BossEncounterControllerFactory factory) {
        Objects.requireNonNull(culture, "culture");
        Objects.requireNonNull(factory, "factory");
        return context -> {
            BossEncounterController controller = factory.create(context);
            return controller == null ? null : new Tracked(controller, context.bossInstanceId(), culture);
        };
    }

    private void end(UUID bossInstanceId, MythicCulture culture) {
        Integer pending = pendingDefeats.get(culture);
        boolean defeated = pending != null;
        if (defeated) {
            if (pending <= 1) {
                pendingDefeats.remove(culture);
            } else {
                pendingDefeats.put(culture, pending - 1);
            }
        }
        listener.ended(bossInstanceId, culture, defeated);
    }

    private final class Tracked implements BossEncounterController {

        private final BossEncounterController delegate;
        private final UUID bossInstanceId;
        private final MythicCulture culture;
        private boolean ended;

        private Tracked(BossEncounterController delegate, UUID bossInstanceId, MythicCulture culture) {
            this.delegate = delegate;
            this.bossInstanceId = bossInstanceId;
            this.culture = culture;
        }

        @Override
        public void start() {
            delegate.start();
        }

        @Override
        public void tick(Instant now) {
            delegate.tick(now);
        }

        @Override
        public void stop() {
            try {
                delegate.stop();
            } finally {
                if (!ended) {
                    ended = true;
                    end(bossInstanceId, culture);
                }
            }
        }

        @Override
        public boolean blocksProjectile(Projectile projectile) {
            return delegate.blocksProjectile(projectile);
        }

        @Override
        public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
            return delegate.filterDamage(event, attacker, damage);
        }

        @Override
        public OptionalInt phaseOverride() {
            return delegate.phaseOverride();
        }

        @Override
        public boolean suspendsAbilities() {
            return delegate.suspendsAbilities();
        }

        @Override
        public boolean execute(BossAbilityEffectDefinition effect, List<UUID> targets) {
            return delegate.execute(effect, targets);
        }
    }
}
