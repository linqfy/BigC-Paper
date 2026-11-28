package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.blocks.MushroomState;
import dev.linqfy.bigCasares.dimension.NamespacedId;

import java.util.Arrays;
import java.util.Optional;

/**
 * Los bloques propios del hotel. Cada uno es un estado de hongo reservado ({@code down=true}, el pool que el mundo
 * vanilla nunca genera) que el resource pack dibuja con una textura de {@code caichet}; asi el aspecto del resto del
 * juego no cambia. <b>El estado de respaldo es dato persistente de los mundos del hotel: no reasignar nunca.</b>
 * Todos salen del pool rojo; The Sift usa ahi solo {@code red:D, ND, ED, SD, WD, UD, NED}.
 */
enum HotelBlock {
    WALLPAPER_BEIGE("wallpaper_beige", "red:ESD", "Papel Tapiz Beige"),
    WAINSCOT("wainscot", "red:EWD", "Zocalo"),
    CARPET_MUSTARD("carpet_mustard", "red:EUD", "Alfombra Mostaza"),
    CEILING_WARM("ceiling_warm", "red:NSD", "Techo Calido"),
    WALLPAPER_YELLOW("wallpaper_yellow", "red:NWD", "Papel Tapiz Amarillo"),
    CARPET_YELLOW("carpet_yellow", "red:NUD", "Alfombra Amarilla"),
    CEILING_YELLOW("ceiling_yellow", "red:SWD", "Techo Amarillo"),
    WOOD_PANEL("wood_panel", "red:SUD", "Panel de Madera"),
    CARPET_OLIVE("carpet_olive", "red:WUD", "Alfombra Oliva"),
    POOL_MOSAIC("pool_mosaic", "red:NESD", "Mosaico de Piscina"),
    CREAM_LINES("cream_lines", "red:NEWD", "Lineas Crema"),
    TILES_WHITE("tiles_white", "red:NEUD", "Azulejos Blancos"),
    TILES_DIRTY("tiles_dirty", "red:NSWD", "Azulejos Sucios"),
    /** El panel encendido; la luz la da un bloque {@code minecraft:light} en el aire de abajo (los hongos no emiten). */
    LIGHT_PANEL("light_panel", "red:NSUD", "Panel de Luz"),
    /** El panel apagado: el que se ve cuando una luz titila. */
    LIGHT_PANEL_OFF("light_panel_off", "red:NWUD", "Panel de Luz Apagado");

    private final String path;
    private final MushroomState state;
    private final String displayName;

    HotelBlock(String path, String state, String displayName) {
        this.path = path;
        this.state = MushroomState.parse(state);
        this.displayName = displayName;
    }

    /** El nombre sin namespace; es tambien el modelo {@code caichet:block/<path>} y la textura del mismo nombre. */
    String path() {
        return path;
    }

    NamespacedId id() {
        return NamespacedId.of(CaiChetBlocks.NAMESPACE, path);
    }

    MushroomState state() {
        return state;
    }

    String displayName() {
        return displayName;
    }

    /** El bloque del hotel que respalda este estado de hongo, si lo hay. */
    static Optional<HotelBlock> of(MushroomState state) {
        return Arrays.stream(values()).filter(block -> block.state.equals(state)).findFirst();
    }
}
