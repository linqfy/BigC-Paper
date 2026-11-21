package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.dimension.datapack.DatapackBuilder;
import dev.linqfy.bigCasares.dimension.datapack.DatapackInstaller;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * El datapack del modulo: la dimension del desierto ({@code caichet:cai_chet}) con su tipo, su terreno y su bioma, y
 * los biomas del Sueño (los colores de cielo, pasto y bruma). Los JSON viajan en el jar y se instalan en la carpeta
 * datapacks del mundo con el instalador de BigCasares. Minecraft solo lee dimensiones y biomas al arrancar: la primera
 * vez (o cuando cambian) hace falta reiniciar el servidor.
 */
final class CaiChetDatapack {

    /** Carpeta del pack dentro de datapacks del mundo. */
    static final String FOLDER = "bigcasares-caichet";
    /** Donde estan los JSON dentro del jar. */
    static final String RESOURCE_ROOT = "caichet/datapack/";
    static final List<String> FILES = List.of(
        "data/caichet/dimension/cai_chet.json",
        "data/caichet/dimension_type/cai_chet.json",
        "data/caichet/worldgen/noise_settings/desert.json",
        "data/caichet/worldgen/density_function/desert_base.json",
        "data/caichet/worldgen/biome/desierto.json",
        "data/caichet/worldgen/biome/sueno_barrio.json",
        "data/caichet/worldgen/biome/sueno_campo.json",
        "data/caichet/worldgen/biome/sueno_collage.json",
        "data/caichet/worldgen/biome/sueno_ojos.json",
        "data/caichet/worldgen/biome/sueno_plaza.json",
        "data/caichet/worldgen/biome/sueno_puertas.json");

    private CaiChetDatapack() {
    }

    /** Arma el pack con los JSON del jar. */
    static GeneratedDatapack build(ClassLoader loader) throws IOException {
        DatapackBuilder builder = new DatapackBuilder();
        for (String file : FILES) {
            try (InputStream stream = loader.getResourceAsStream(RESOURCE_ROOT + file)) {
                if (stream == null) {
                    throw new IOException("Falta en el jar: " + RESOURCE_ROOT + file);
                }
                builder.file(file, stream.readAllBytes());
            }
        }
        return builder.build("BigCasares: dimension cai chet y biomas del Sueno");
    }

    /**
     * Deja el pack en la carpeta datapacks del mundo (si ya esta igual, no toca nada).
     *
     * @param levelDirectory la carpeta del mundo principal
     * @param backups donde se guarda la version anterior cuando el pack cambia
     */
    static DatapackInstaller.InstallResult install(ClassLoader loader, Path levelDirectory, Path backups)
        throws IOException {
        GeneratedDatapack pack = build(loader);
        return new DatapackInstaller(Clock.systemDefaultZone())
            .install(pack, levelDirectory.resolve("datapacks"), FOLDER, backups);
    }
}
