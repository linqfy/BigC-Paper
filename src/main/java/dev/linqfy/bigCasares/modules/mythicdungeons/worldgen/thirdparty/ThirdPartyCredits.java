package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import java.util.List;
import java.util.Objects;

/** Attribution for the third-party work the mythic dungeons reuse (shown by {@code /mazmorra creditos}). */
public final class ThirdPartyCredits {

    /** One reused work: what it is, who made it, its licence and how BigCasares uses it. */
    public record Credit(String name, String authors, String license, String sourceUrl, String usage, String modifications) {
        public Credit {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(authors, "authors");
            Objects.requireNonNull(license, "license");
            Objects.requireNonNull(sourceUrl, "sourceUrl");
            Objects.requireNonNull(usage, "usage");
            Objects.requireNonNull(modifications, "modifications");
        }
    }

    public static final List<Credit> ALL = List.of(
        new Credit("YUNG's Better Dungeons", "YUNGNICKYOUNG, Acarii", "LGPL-3.0",
            "https://github.com/YUNG-GANG/YUNGs-Better-Dungeons",
            "Salas de mazmorra pequena, conchas del Nether, salas y entrada de la cripta zombi (plantillas .nbt originales)",
            "Bloques marcadores resueltos al generar el datapack, conectores jigsaw reescritos, puertas abiertas y"
                + " bloques retematizados con la paleta de cada cultura"),
        new Credit("Doomlike Dungeons", "JaredBGreat (Jared Blackburn), Xiroc", "MIT",
            "https://github.com/BlackJar72/DoomlikeDungeons",
            "Algoritmo de planificacion (nodos, rutas, crecimiento de salas, rasgos y temas) para mazmorras completas"
                + " y salas laterales",
            "Reimplementado en Java para producir plantillas de estructura de hasta 48 bloques con la paleta de cada"
                + " cultura en lugar de colocar bloques en el mundo"));

    private ThirdPartyCredits() {
    }
}
