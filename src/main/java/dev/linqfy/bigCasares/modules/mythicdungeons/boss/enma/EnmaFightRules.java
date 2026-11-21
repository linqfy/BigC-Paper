package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import org.bukkit.Material;

/**
 * Las dos fases de Enma y quien puede herirlo, sin Bukkit.
 *
 * <ul>
 *     <li>{@link Stage#MIRROR} (fase 1): mientras el Espejo de Johari este en pie Enma no recibe dano.</li>
 *     <li>{@link Stage#SHATTER}: el espejo se quebro; {@link #SHATTER_TICKS} de transicion (titulo, {@code rage}).</li>
 *     <li>{@link Stage#JUDGEMENT} (fase 2): Enma recibe dano de los jugadores.</li>
 * </ul>
 */
public final class EnmaFightRules {

    /** Ticks entre el quiebre del espejo y el primer golpe de la fase 2. */
    public static final int SHATTER_TICKS = 60;

    public enum Stage {
        MIRROR, SHATTER, JUDGEMENT
    }

    public enum Verdict {
        ADMITTED, MIRROR_SHIELD, TRANSITION, NO_ATTACKER
    }

    private Stage stage = Stage.MIRROR;
    private int shatteredAt;

    public Stage stage() {
        return stage;
    }

    /** Fase que muestra el runtime: 1 mientras dura el espejo (y la transicion), 2 en el juicio. */
    public int phase() {
        return stage == Stage.JUDGEMENT ? 2 : 1;
    }

    /** Avanza la fase; devuelve la etapa nueva si cambio en este tick, o {@code null}. */
    public Stage update(int tick, boolean mirrorBroken) {
        if (stage == Stage.MIRROR && mirrorBroken) {
            stage = Stage.SHATTER;
            shatteredAt = tick;
            return stage;
        }
        if (stage == Stage.SHATTER && tick - shatteredAt >= SHATTER_TICKS) {
            stage = Stage.JUDGEMENT;
            return stage;
        }
        return null;
    }

    /** Si un golpe a Enma cuenta. Sin atacante (asfixia, caida, fuego) nunca cuenta. */
    public Verdict admit(boolean hasAttacker) {
        return switch (stage) {
            case MIRROR -> Verdict.MIRROR_SHIELD;
            case SHATTER -> Verdict.TRANSITION;
            case JUDGEMENT -> hasAttacker ? Verdict.ADMITTED : Verdict.NO_ATTACKER;
        };
    }

    /** Si un objeto se le puede copiar a un reflejo: nada que lo salve de la muerte (totem o proteccion de muerte). */
    public static boolean mirrorable(Material type, boolean deathProtection) {
        return !deathProtection && type != Material.TOTEM_OF_UNDYING;
    }
}
