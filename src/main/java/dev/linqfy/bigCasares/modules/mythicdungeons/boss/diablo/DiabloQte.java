package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * El QTE de un jugador agarrado: tiene que apretar la tecla pedida {@code presses} veces antes de que se acabe el
 * tiempo, {@code rounds} veces seguidas, y cada pedido es una tecla distinta de las anteriores. Una pulsacion cuenta
 * solo si en ese tick se aprieta unicamente la tecla pedida; una tecla equivocada o de mas (fuera del margen de
 * reaccion) o quedarse sin tiempo es una falla: el pedido vuelve a empezar con la misma tecla.
 * Sin Bukkit: el encuentro le pasa en cada tick las teclas apretadas.
 *
 * <p>Muestreo: un encuentro no puede registrar listeners, asi que se lee {@code Player#getCurrentInput()} una vez por
 * tick y cada cambio de suelta a apretada entre dos muestras es una pulsacion. Alcanza porque el cliente tambien mira
 * sus teclas una vez por tick y manda el paquete de input solo cuando cambian: una pulsacion se pierde unicamente si
 * apretar y soltar llegan en el mismo tick del servidor (lag), y por eso los pedidos son cortos y holgados.</p>
 */
final class DiabloQte {

    enum Outcome {
        /** Nada cambio. */
        NONE,
        /** Una pulsacion correcta mas. */
        PROGRESS,
        /** Completo un pedido; hay otro con otra tecla. */
        PROMPT_CLEARED,
        /** Apreto otra tecla: el pedido reinicia. */
        FAILED_WRONG,
        /** Se acabo el tiempo: el pedido reinicia. */
        FAILED_TIMEOUT,
        /** Completo todos los pedidos. */
        COMPLETED
    }

    /**
     * @param rounds      pedidos a completar
     * @param presses     pulsaciones por pedido
     * @param promptTicks tiempo de cada pedido
     * @param graceTicks  margen tras cada pedido en el que una tecla equivocada no cuenta
     */
    record Settings(int rounds, int presses, int promptTicks, int graceTicks) {

        Settings {
            if (rounds < 1 || rounds > QteKey.values().length) {
                throw new IllegalArgumentException("rounds must be 1.." + QteKey.values().length);
            }
            if (presses < 1 || promptTicks < 1 || graceTicks < 0 || graceTicks >= promptTicks) {
                throw new IllegalArgumentException("invalid QTE timings");
            }
        }
    }

    private final Settings settings;
    private final boolean alternatives;
    private final RandomGenerator random;
    private final List<QteKey> used = new ArrayList<>();
    private Set<QteKey> previous;
    private QteKey prompt;
    private int count;
    private int cleared;
    private long promptStart;
    private boolean completed;

    /**
     * @param alternatives acepta tambien {@link QteKey#alternative()} (jugadores de Bedrock)
     */
    DiabloQte(Settings settings, boolean alternatives, RandomGenerator random) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.alternatives = alternatives;
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Empieza el primer pedido. */
    void begin(long tick) {
        used.clear();
        cleared = 0;
        completed = false;
        previous = null;
        nextPrompt(tick);
    }

    /**
     * Un tick del servidor.
     *
     * @param held las teclas apretadas ahora
     */
    Outcome tick(long tick, Set<QteKey> held) {
        if (prompt == null || completed) {
            return Outcome.NONE;
        }
        Set<QteKey> now = held.isEmpty() ? EnumSet.noneOf(QteKey.class) : EnumSet.copyOf(held);
        Set<QteKey> pressed = EnumSet.noneOf(QteKey.class);
        if (previous != null) {
            for (QteKey key : now) {
                if (!previous.contains(key)) {
                    pressed.add(key);
                }
            }
        }
        // el estado del primer tick es la base: lo que ya estaba apretado al aparecer el pedido no cuenta
        previous = now;
        if (tick - promptStart >= settings.promptTicks()) {
            restart(tick);
            return Outcome.FAILED_TIMEOUT;
        }
        if (alternatives) {
            // un stick analogico de Bedrock marca direcciones de costado sin querer: solo cuentan la tecla pedida,
            // salto y agacharse
            pressed.retainAll(EnumSet.of(prompt, QteKey.JUMP, QteKey.SNEAK));
        }
        if (pressed.isEmpty()) {
            return Outcome.NONE;
        }
        // vale solo si aprieta exactamente la tecla pedida (o su alternativa): apretar todas a la vez no cuenta
        if (pressed.equals(EnumSet.of(prompt))
            || (alternatives && prompt.alternative() != null && pressed.equals(EnumSet.of(prompt.alternative())))) {
            count++;
            if (count < settings.presses()) {
                return Outcome.PROGRESS;
            }
            cleared++;
            if (cleared >= settings.rounds()) {
                completed = true;
                return Outcome.COMPLETED;
            }
            nextPrompt(tick);
            return Outcome.PROMPT_CLEARED;
        }
        if (tick - promptStart >= settings.graceTicks()) {
            restart(tick);
            return Outcome.FAILED_WRONG;
        }
        return Outcome.NONE;
    }

    QteKey prompt() {
        return prompt;
    }

    int count() {
        return count;
    }

    int cleared() {
        return cleared;
    }

    boolean completed() {
        return completed;
    }

    Settings settings() {
        return settings;
    }

    boolean alternatives() {
        return alternatives;
    }

    /** Ticks que le quedan al pedido actual (0 o mas). */
    long ticksLeft(long tick) {
        return Math.max(0L, settings.promptTicks() - (tick - promptStart));
    }

    private void restart(long tick) {
        count = 0;
        promptStart = tick;
    }

    private void nextPrompt(long tick) {
        List<QteKey> options = new ArrayList<>(List.of(QteKey.values()));
        options.removeAll(used);
        prompt = options.get(random.nextInt(options.size()));
        used.add(prompt);
        restart(tick);
    }
}
