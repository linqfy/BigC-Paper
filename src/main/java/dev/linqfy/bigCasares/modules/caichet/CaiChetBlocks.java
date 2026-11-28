package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition.ToolTier;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition.ToolType;
import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.dimension.json.Json;

import java.util.Arrays;
import java.util.List;

/**
 * Los bloques del hotel en el sistema de bloques propios de BigC (el mismo que usa The Sift): una
 * {@link CustomBlockDefinition} por {@link HotelBlock}, respaldada por un estado de hongo reservado.
 *
 * <p>Son las paredes, pisos y techos del hotel, asi que romperlos no suelta nada. La dureza y la herramienta son las
 * del bloque de cobre que reemplazan (3.0, pico de piedra), con su sonido; el item "a si mismo" no existe porque no
 * hay item de contenido para estos ids, y con la silk touch apagada no se intenta soltarlo.
 */
public final class CaiChetBlocks {

    public static final String NAMESPACE = "caichet";

    private static final float HARDNESS = 3.0F;
    private static final String SOUND = "block.copper";
    private static final List<CustomBlockDefinition> DEFINITIONS = Arrays.stream(HotelBlock.values())
        .map(CaiChetBlocks::definition)
        .toList();

    private CaiChetBlocks() {
    }

    /** Los bloques del hotel, en el orden de {@link HotelBlock}. */
    public static List<CustomBlockDefinition> definitions() {
        return DEFINITIONS;
    }

    /** Los registra los que todavia no esten (el modulo puede prenderse mas de una vez por ejecucion). */
    static void register(CustomBlockRegistry registry) {
        for (CustomBlockDefinition definition : DEFINITIONS) {
            if (registry.byId(definition.id()).isEmpty()) {
                registry.register(definition);
            }
        }
    }

    private static CustomBlockDefinition definition(HotelBlock block) {
        return new CustomBlockDefinition(block.id(), block.state(),
            Json.object().put("model", NAMESPACE + ":block/" + block.path()).build(),
            HARDNESS, ToolType.PICKAXE, ToolTier.STONE, true, List.of(), false, 0, 0, SOUND);
    }
}
