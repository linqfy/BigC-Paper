package dev.linqfy.bigCasares.modules.mutango;

import java.util.List;

/**
 * El aviso que ve todo el servidor cuando vencen a Neguechen (sin Bukkit): "(Jugador) le ha ganado a la diosa mapuche
 * Neguechen". Si ganaron varios, "A, B y C le han ganado...". Si son muchos, nombra a los primeros y cuenta el resto.
 */
final class MutangoVictoryMessage {

    /** A cuantos nombra como mucho antes de decir "y N mas". */
    static final int MAX_NAMES = 4;

    private MutangoVictoryMessage() {
    }

    /** El texto sin colores; names son los que pelearon (en el orden en que se quiere nombrarlos). */
    static String of(List<String> names) {
        if (names.isEmpty()) {
            return "Neguechen fue vencida";
        }
        String who;
        if (names.size() == 1) {
            who = names.get(0);
        } else if (names.size() <= MAX_NAMES) {
            who = String.join(", ", names.subList(0, names.size() - 1)) + " y " + names.get(names.size() - 1);
        } else {
            who = String.join(", ", names.subList(0, MAX_NAMES)) + " y " + (names.size() - MAX_NAMES) + " más";
        }
        return who + (names.size() == 1 ? " le ha ganado" : " le han ganado") + " a la diosa mapuche Neguechen";
    }
}
