package dev.linqfy.bigCasares.modules.caichet;

import java.util.ArrayList;
import java.util.List;

/**
 * Los bloques del hotel tal como los necesita la integracion con Geyser. En Java son bloques de cobre encerado que el
 * resource pack dibuja con las texturas de {@code caichet}; Bedrock no lee ese pack y los mostraria como cobre. Cada
 * bloque trae sus estados Java, la textura (el nombre de un PNG de
 * {@code resourcepack/java/assets/caichet/textures/block}, que el pack Bedrock tambien trae como clave
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

    private static List<Block> build() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(plain("waxed_copper_block", "wallpaper_beige", "Papel Tapiz Beige"));
        blocks.add(plain("waxed_exposed_copper", "wainscot", "Zocalo"));
        blocks.add(plain("waxed_weathered_copper", "carpet_mustard", "Alfombra Mostaza"));
        blocks.add(plain("waxed_oxidized_copper", "ceiling_warm", "Techo Calido"));
        blocks.add(plain("waxed_cut_copper", "wallpaper_yellow", "Papel Tapiz Amarillo"));
        blocks.add(plain("waxed_exposed_cut_copper", "carpet_yellow", "Alfombra Amarilla"));
        blocks.add(plain("waxed_weathered_cut_copper", "ceiling_yellow", "Techo Amarillo"));
        blocks.add(plain("waxed_oxidized_cut_copper", "wood_panel", "Panel de Madera"));
        blocks.add(plain("waxed_chiseled_copper", "carpet_olive", "Alfombra Oliva"));
        blocks.add(plain("waxed_exposed_chiseled_copper", "pool_mosaic", "Mosaico de Piscina"));
        blocks.add(plain("waxed_weathered_chiseled_copper", "cream_lines", "Lineas Crema"));
        blocks.add(plain("waxed_oxidized_chiseled_copper", "tiles_white", "Azulejos Blancos"));
        // Las bombillas tienen lit y powered; el pack Java ya decide la cara solo por lit. La exposed encendida
        // conserva el modelo vanilla en Java, asi que queda sin mapear.
        blocks.add(new Block(bulb("waxed_exposed_copper_bulb", false), "caichet_tiles_dirty",
            "Azulejos Sucios", "tiles_dirty", 0));
        blocks.add(new Block(bulb("waxed_copper_bulb", true), "caichet_light_panel",
            "Panel de Luz", "light_panel", 15));
        blocks.add(new Block(bulb("waxed_weathered_copper_bulb", true), "caichet_light_panel_dim",
            "Panel de Luz Tenue", "light_panel", 8));
        List<String> off = new ArrayList<>(bulb("waxed_copper_bulb", false));
        off.addAll(bulb("waxed_weathered_copper_bulb", false));
        blocks.add(new Block(off, "caichet_light_panel_off", "Panel de Luz Apagado", "light_panel_off", 0));
        return List.copyOf(blocks);
    }

    private static Block plain(String block, String texture, String displayName) {
        return new Block(List.of("minecraft:" + block), "caichet_" + texture, displayName, texture, 0);
    }

    private static List<String> bulb(String block, boolean lit) {
        return List.of(
            "minecraft:" + block + "[lit=" + lit + ",powered=false]",
            "minecraft:" + block + "[lit=" + lit + ",powered=true]");
    }
}
