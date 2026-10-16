package dev.linqfy.bigCasares.modules.mutango;

import dev.linqfy.bigCasares.modules.moderation.AbuseSignal;
import org.bukkit.entity.Player;

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
    private final Logger logger;
    private boolean failedOnce;

    MutangoModeration(Supplier<Consumer<AbuseSignal>> sink, Logger logger) {
        this.sink = sink;
        this.logger = logger;
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
