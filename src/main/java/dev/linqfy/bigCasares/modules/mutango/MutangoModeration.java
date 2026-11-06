package dev.linqfy.bigCasares.modules.mutango;

import dev.linqfy.bigCasares.modules.moderation.AbuseSignal;
import dev.linqfy.bigCasares.modules.moderation.ModerationModule;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Puente con el sistema de moderacion de BigC (modulo "moderation-system"). Mutango no resuelve los intentos de cheat
 * que ve: se los manda a ese sistema como una senal de abuso (puntos y evidencia), y es el que decide si avisa al staff o
 * hace algo. El modulo de moderacion se crea despues que este, asi que se pide recien al momento de avisar; si todavia
 * no esta (o esta apagado), el aviso no hace nada.
 */
final class MutangoModeration {

    private final Supplier<Consumer<AbuseSignal>> sink;
    private final Supplier<ModerationModule> module;
    private final Logger logger;
    private boolean failedOnce;

    MutangoModeration(Supplier<Consumer<AbuseSignal>> sink, Supplier<ModerationModule> module, Logger logger) {
        this.sink = sink;
        this.module = module;
        this.logger = logger;
    }

    /**
     * El permiso de volar de las botas es solo el mecanismo para detectar el doble salto: se le dice a moderacion para que
     * ese permiso no exima al jugador del detector de movimiento (el vuelo de verdad, isFlying, sigue exento y se avisa
     * aparte).
     */
    void technicalFlight(Player player, boolean technical) {
        ModerationModule moderation = module.get();
        if (moderation != null) {
            moderation.setTechnicalFlight(player.getUniqueId(), technical);
        }
    }

    /** El impulso del doble salto no es un movimiento anomalo: se exime solo esa ventana corta. */
    void exemptJump(Player player) {
        ModerationModule moderation = module.get();
        if (moderation != null) {
            moderation.exemptMovement(player.getUniqueId(), Duration.ofSeconds(2));
        }
    }

    /**
     * Le manda a BigC una senal de abuso de este jugador: rule es el nombre de la regla (por ejemplo
     * "mutango-boots-flight"), points cuanto suma al puntaje y evidence lo que se vio. Devuelve true si BigC la recibio.
     */
    boolean report(Player player, String rule, int points, String evidence) {
        Consumer<AbuseSignal> target = sink.get();
        if (target == null) {
            return false;
        }
        try {
            target.accept(new AbuseSignal(Instant.now(), player.getUniqueId(), player.getName(), rule, points, evidence));
            return true;
        } catch (RuntimeException failure) {
            if (!failedOnce) {
                failedOnce = true;
                logger.log(Level.WARNING, "[Mutango] No se pudo avisar al sistema de moderacion de BigC", failure);
            }
            return false;
        }
    }
}
