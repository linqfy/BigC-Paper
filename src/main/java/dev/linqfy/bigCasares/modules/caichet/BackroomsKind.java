package dev.linqfy.bigCasares.modules.caichet;

import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Las cinco entidades de los backrooms. El orden es el de las imagenes de la fuente de sustos
 * ({@link JumpscareFrames}): el numero de cada una es su imagen.
 */
enum BackroomsKind {
    SONRISA("sonrisa", "La Sonrisa", CaiChetBedrockEntities.SONRISA, "no la mires...", 0xE0B030, List.of(
        "{j} se quedó mirando a la Sonrisa demasiado tiempo",
        "{j} le devolvió la sonrisa a la oscuridad",
        "{j} no pudo dejar de mirarla",
        "La Sonrisa se llevó a {j} con los dientes")),
    E666("666", "la Entidad 666", CaiChetBedrockEntities.E666, "corre", 0xB0B0B0, List.of(
        "{j} no corrió lo suficiente de la Entidad 666",
        "{j} sintió el latido demasiado cerca",
        "La Entidad 666 alcanzó a {j} y abrió la boca",
        "{j} miró para atrás y la Entidad 666 ya estaba ahí")),
    JUANCARLOS("juancarlos", "Juan Carlos", CaiChetBedrockEntities.JUANCARLOS, "hola %s", 0xE8E0C8, List.of(
        "hola {j}. chau {j}.",
        "{j} no pudo soltarse de Juan Carlos",
        "Juan Carlos levantó a {j} hasta el techo",
        "Juan Carlos saludó a {j} por última vez")),
    ARANA("arana", "la Araña", CaiChetBedrockEntities.ARANA, "corre", 0x8A2BE2, List.of(
        "{j} escuchó el clic-clic demasiado tarde",
        "{j} quedó entre las patas de la Araña",
        "La Araña corrió más rápido que {j}",
        "{j} no llegó a doblar la esquina antes que la Araña")),
    PAYASO("payaso", "el Payaso", CaiChetBedrockEntities.PAYASO, "date vuelta y alejate", 0x3060D0, List.of(
        "{j} se acercó a ver qué espiaba el Payaso",
        "{j} no se dio vuelta a tiempo",
        "El Payaso se rió de {j} por última vez",
        "{j} jugó a las escondidas con el Payaso y perdió"));

    /** Como se escribe en el comando. */
    final String commandName;
    /** Con articulo, para el mensaje de muerte ("fue atrapado por ..."). */
    final String deathName;
    final CaiChetBedrockEntities.Entry bedrockEntry;
    /** La pista de la actionbar (un %s se reemplaza por el nombre del jugador). */
    final String hint;

    /** El color del mensaje de muerte (RGB). */
    final int deathColor;
    /** Los mensajes de muerte posibles; {j} es el nombre del jugador. */
    final List<String> deathMessages;

    BackroomsKind(String commandName, String deathName, CaiChetBedrockEntities.Entry bedrockEntry, String hint,
                  int deathColor, List<String> deathMessages) {
        this.commandName = commandName;
        this.deathName = deathName;
        this.bedrockEntry = bedrockEntry;
        this.hint = hint;
        this.deathColor = deathColor;
        this.deathMessages = deathMessages;
    }

    /** El numero de su imagen en la fuente de sustos. */
    int frame() {
        return ordinal();
    }

    /** El mensaje numero index (se da la vuelta si se pasa) que reemplaza al de muerte vanilla. */
    String deathMessage(String playerName, int index) {
        return deathMessages.get(Math.floorMod(index, deathMessages.size())).replace("{j}", playerName);
    }

    /** Uno de sus mensajes de muerte, al azar. */
    String deathMessage(String playerName, RandomGenerator random) {
        return deathMessage(playerName, random.nextInt(deathMessages.size()));
    }

    /** La entidad que se llama asi en el comando, o null. */
    static BackroomsKind byCommandName(String name) {
        if (name == null) {
            return null;
        }
        String wanted = name.toLowerCase(Locale.ROOT);
        for (BackroomsKind kind : values()) {
            if (kind.commandName.equals(wanted)) {
                return kind;
            }
        }
        return null;
    }
}
