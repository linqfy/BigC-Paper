package dev.linqfy.bigCasares.modules.legendary;

import java.util.List;

final class LegendaryCraftingMessages {
    private LegendaryCraftingMessages() { }
    static List<String> notice(LegendaryCraftingLedger.Notice notice, String dimension) {
        var session = notice.session();
        String name = session.weapon().displayName.toUpperCase(java.util.Locale.ROOT);
        return switch (notice.index()) {
            case 0 -> List.of("§6⚔ ¡SE HA INICIADO UNA FABRICACIÓN LEGENDARIA!", "§eArma: " + name,
                "§e⏳ Tiempo restante: 20 minutos.", "§e📍 La primera zona de búsqueda se revelará en 5 minutos.");
            case 1, 2 -> {
                var square = notice.index() == 1 ? session.areas().outer() : session.areas().inner();
                yield List.of(notice.index() == 1 ? "§e📍 ¡PRIMERA ZONA REVELADA!" : "§6⚡ ¡LA ZONA SE HA REDUCIDO!",
                    "§e⚔ Arma: " + name, "§e🗺 Área de búsqueda: " + square.size() + "×" + square.size() + " bloques",
                    "§eX: entre " + square.minX() + " y " + square.maxX(), "§eZ: entre " + square.minZ() + " y " + square.maxZ(),
                    "§eDimensión: " + dimension);
            }
            case 3 -> List.of("§c🚨 ¡UBICACIÓN LEGENDARIA REVELADA!", "§e⚔ Arma: " + name,
                "§e📍 X: " + session.x() + " | Y: " + session.y() + " | Z: " + session.z(),
                "§eDimensión: " + dimension, "§c⏳ ¡Quedan 5 minutos para que aparezca el arma!");
            case 4 -> List.of("§6⚔ ¡" + name + " HA APARECIDO!", "§e🏆 ¡El arma legendaria ya está disponible para reclamar!");
            case 5 -> List.of("§6⚔ ¡" + name + " HA SIDO RECLAMADA!");
            default -> throw new IllegalArgumentException("Aviso desconocido.");
        };
    }
}
