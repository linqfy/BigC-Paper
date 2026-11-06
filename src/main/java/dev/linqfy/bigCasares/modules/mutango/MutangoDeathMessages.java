package dev.linqfy.bigCasares.modules.mutango;

import java.util.List;

/**
 * Lo que se lee cuando te mata un Mutango o Neguechen (sin Bukkit). Cada mensaje se elige al azar. Los de los Mutangos
 * llevan el nombre de quien te mato (Lef, Lihuen o Lican); los de Neguechen (y su Sol, su Luna, sus manos y sus
 * animales) son de ella.
 */
final class MutangoDeathMessages {

    /** %s es el nombre del Mutango. */
    static final List<String> MUTANGO = List.of(
        "%s te ha matado",
        "Moriste a pies de %s",
        "El poder mapuche de %s te mató");

    static final List<String> NEGUECHEN = List.of(
        "Neguechen te mató",
        "Neguechen te destruyó",
        "Se nota que sos tan malo que una diosa te ganó",
        "Sos malísimo, te ganó una mapuche");

    /**
     * Si te mata el mega rayo de Warden por quedarte en una zona protegida para que no te ataquen (se ve en la pantalla
     * de muerte).
     */
    static final String COWARD = "Moriste por cagón, las zonas protegidas como que no son para pelear, eh";

    private MutangoDeathMessages() {
    }

    /** Lo que se le dice a todo el servidor cuando alguien muere por quedarse en una zona protegida. */
    static String cowardAnnouncement(String player) {
        return "Hola Gente, %s es un cagón, no entiende que la idea de las peleas es pelear y no es meterse en zonas "
            .formatted(player) + "protegidas para evitar ataques, no sean como %s".formatted(player);
    }

    /** El mensaje numero roll (cualquier numero: se da la vuelta) de los de un Mutango, con su nombre. */
    static String byMutango(String name, int roll) {
        return MUTANGO.get(Math.floorMod(roll, MUTANGO.size())).formatted(name);
    }

    /** El mensaje numero roll (cualquier numero: se da la vuelta) de los de Neguechen. */
    static String byNeguechen(int roll) {
        return NEGUECHEN.get(Math.floorMod(roll, NEGUECHEN.size()));
    }
}
