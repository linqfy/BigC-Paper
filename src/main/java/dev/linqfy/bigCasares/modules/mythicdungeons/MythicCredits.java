package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty.ThirdPartyCredits;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Los creditos de las obras de terceros que usan las mazmorras ({@code /mazmorra creditos}). Cada obra adaptada o
 * tomada como referencia agrega aca su entrada, con su licencia y que se uso de ella.
 */
public final class MythicCredits {

    /**
     * Una obra de terceros.
     *
     * @param used que se uso de ella (estructuras, diseno de salas, codigo...)
     */
    public record Credit(String name, String authors, String licence, String url, String used) {

        public Credit {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(authors, "authors");
            Objects.requireNonNull(licence, "licence");
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(used, "used");
        }
    }

    private static final List<Credit> ENTRIES = ThirdPartyCredits.ALL.stream()
        .map(credit -> new Credit(credit.name(), credit.authors(), credit.license(), credit.sourceUrl(),
            credit.usage() + " " + credit.modifications()))
        .toList();

    private MythicCredits() {
    }

    public static List<Credit> entries() {
        return ENTRIES;
    }

    /** Las lineas que muestra el comando (tambien sin entradas). */
    public static List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("§5§lMazmorras míticas §7- créditos");
        if (ENTRIES.isEmpty()) {
            lines.add("§7Todavía no hay obras de terceros registradas.");
            return lines;
        }
        for (Credit credit : ENTRIES) {
            lines.add("§e" + credit.name() + " §7de §f" + credit.authors() + " §7(" + credit.licence() + ")");
            lines.add("§7  " + credit.used() + " §8- " + credit.url());
        }
        return lines;
    }
}
