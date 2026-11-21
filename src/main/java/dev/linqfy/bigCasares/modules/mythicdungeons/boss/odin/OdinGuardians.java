package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Quienes defienden a Odín en la fase 1 y donde aparecen, sin Bukkit: los cuervos Hugin y Munin, los lobos Geri y
 * Freki y los einherjar (3 y uno mas por cada jugador extra, hasta 7). Las posiciones son relativas al frente de Odín
 * (angulo 0 = hacia adelante, positivo hacia su derecha) para que salgan entre el trono y los jugadores.
 */
public final class OdinGuardians {

    public static final int BASE_EINHERJAR = 3;
    public static final int MAX_EINHERJAR = 7;

    public enum Kind {
        RAVEN,
        WOLF,
        EINHERJAR
    }

    /**
     * @param angleDegrees respecto del frente de Odín
     * @param distance     en bloques desde Odín
     * @param height       bloques por encima del suelo (los cuervos vuelan)
     */
    public record Guardian(Kind kind, String name, double angleDegrees, double distance, double height) {

        public Guardian {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(name, "name");
        }
    }

    private OdinGuardians() {
    }

    public static int einherjarFor(int players) {
        int extra = Math.max(0, players - 1);
        return Math.min(MAX_EINHERJAR, BASE_EINHERJAR + extra);
    }

    /** Todos los guardianes para {@code players} jugadores (al menos uno se asume). */
    public static List<Guardian> plan(int players) {
        List<Guardian> guardians = new ArrayList<>();
        guardians.add(new Guardian(Kind.RAVEN, "Hugin", -35.0, 2.5, 2.5));
        guardians.add(new Guardian(Kind.RAVEN, "Munin", 35.0, 2.5, 2.5));
        guardians.add(new Guardian(Kind.WOLF, "Geri", -25.0, 3.5, 0.0));
        guardians.add(new Guardian(Kind.WOLF, "Freki", 25.0, 3.5, 0.0));
        int einherjar = einherjarFor(players);
        double spread = 110.0;
        for (int index = 0; index < einherjar; index++) {
            double angle = einherjar == 1 ? 0.0 : -spread / 2.0 + spread * index / (einherjar - 1);
            guardians.add(new Guardian(Kind.EINHERJAR, "Einherjar", angle, 5.0 + (index % 2) * 1.5, 0.0));
        }
        return List.copyOf(guardians);
    }
}
