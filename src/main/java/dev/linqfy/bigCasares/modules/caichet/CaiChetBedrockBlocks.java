package dev.linqfy.bigCasares.modules.caichet;

import java.util.Arrays;
import java.util.List;

/**
 * Los bloques del hotel tal como los necesita la integracion con Geyser. En Java son bloques propios de BigC
 * ({@link CaiChetBlocks}: estados de hongo reservados) que el resource pack dibuja con las texturas de {@code caichet};
 * Bedrock no lee ese pack y los mostraria como hongos. Cada bloque trae su estado Java, la textura (el nombre de un PNG
 * de {@code resourcepack/java/assets/caichet/textures/block}, que el pack Bedrock tambien trae como clave
 * {@code caichet_<textura>} de terrain_texture.json) y la luz que da.
 */
public final class CaiChetBedrockBlocks {

    /**
     * Un bloque cubo con la misma textura en las seis caras.
     *
     * @param javaStates    los estados Java que se ven asi; el primero es el principal
     * @param name          nombre del bloque Bedrock, en minusculas
     * @param texture       nombre de la textura, igual al del PNG de Java
     * @param lightEmission luz de 0 a 15 que dibuja el cliente Bedrock
     */
    public record Block(List<String> javaStates, String name, String displayName, String texture, int lightEmission) {
        public Block {
            javaStates = List.copyOf(javaStates);
        }

        /** La clave de terrain_texture.json. */
        public String terrainKey() {
            return "caichet_" + texture;
        }
    }

    private static final List<Block> ALL = build();

    private CaiChetBedrockBlocks() {
    }

    public static List<Block> all() {
        return ALL;
    }

    /**
     * Ninguno emite luz: en Java los hongos no alumbran y la luz de un panel es el bloque {@code minecraft:light} del
     * aire de abajo, que Geyser ya muestra como bloque de luz de Bedrock. Si el panel emitiera tambien, el panel tenue
     * (luz 8) y el que no tiene aire debajo alumbrarian de mas.
     */
    private static List<Block> build() {
        return Arrays.stream(HotelBlock.values())
            .map(block -> new Block(List.of(block.state().asString()), "caichet_" + block.path(),
                block.displayName(), block.path(), 0))
            .toList();
    }
}
