package dev.linqfy.bigCasares.modules.donpollos;

import java.util.List;
import java.util.Locale;

/** Lo que no depende del servidor del premio por ganarle al Don Pollo Boss (frases, paginas y nombres). */
final class DonPollosReward {

    /** Lo que dice, frase por frase, cuando le ganas. */
    static final List<String> DEFEAT_PHRASES = List.of(
        "Ya no hay video mi gente...",
        "Ni salsa...",
        "Ni picante...",
        "Mi gente triste...",
        "Elegi lo que quieras...");
    /** Lo que dice el Don Pollo Gordito cuando le haces click derecho (antes del menu del secreto). */
    static final List<String> SECRET_PHRASES = List.of(
        "Yo se cosas...",
        "Queres saberlas...",
        "Dame Salsa y Picante");
    /** La historia que cuenta el bloque del secreto antes de volverse malo. */
    static final List<String> BLOCK_STORY = List.of(
        "Era una vez...",
        "En el auto...",
        "Que el señor don pollo estaba yendo al KFC...",
        "Pero le habian subido el precio al pollito...",
        "\"No hay salsa, ni picante, ni nada mi gente\" dijo",
        "Desde ese dia algo cambio en Don Pollo...");
    /** Lo que dice cada Don Pollo cuando terminas de usar su menu (el Comun no dice nada). */
    static final java.util.Map<DonPolloVariant, String> FAREWELLS = java.util.Map.of(
        DonPolloVariant.AURA_67, "Lastima que alguien no tiene salsa y picante...",
        DonPolloVariant.FINO, "Como estan los precios...",
        DonPolloVariant.SALSERO, "A alguien le falta moverse...",
        DonPolloVariant.GORDITO, "Tene cuidado...");
    /** Lo que aparece la primera vez que entras a cada ciudad de Don Pollo. */
    static final String CITY_WELCOME = "En esta ciudad pasaron cosas...";
    /** Lugares que elegis vos (el primero de los 5 es siempre el balde de KFC). */
    static final int CHOICES = 4;
    static final int ITEMS_PER_PAGE = 45;

    /** Categorias del menu (parecidas a las del modo creativo), con el nombre del item que las representa. */
    enum Category {
        CONSTRUCCION("Bloques de construccion", "BRICKS"),
        DECORACION("Decoracion", "PEONY"),
        REDSTONE("Redstone", "REDSTONE"),
        TRANSPORTE("Transporte", "POWERED_RAIL"),
        COMIDA("Comida", "GOLDEN_APPLE"),
        HERRAMIENTAS("Herramientas", "DIAMOND_PICKAXE"),
        COMBATE("Combate", "NETHERITE_SWORD"),
        POCIONES("Pociones", "BREWING_STAND"),
        VARIOS("Varios", "LAVA_BUCKET");

        final String title;
        final String icon;

        Category(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static final java.util.Set<String> COMBAT = java.util.Set.of("BOW", "CROSSBOW", "TRIDENT", "MACE", "SHIELD",
        "ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW", "TOTEM_OF_UNDYING", "WOLF_ARMOR", "WIND_CHARGE", "END_CRYSTAL",
        "SNOWBALL", "EGG");
    private static final java.util.Set<String> BREWING = java.util.Set.of("GLASS_BOTTLE", "DRAGON_BREATH",
        "EXPERIENCE_BOTTLE", "BLAZE_POWDER", "BLAZE_ROD", "FERMENTED_SPIDER_EYE", "MAGMA_CREAM", "GHAST_TEAR",
        "GLISTERING_MELON_SLICE", "RABBIT_FOOT", "PHANTOM_MEMBRANE", "NETHER_WART", "BREWING_STAND", "CAULDRON",
        "TURTLE_SCUTE", "BREEZE_ROD");
    private static final java.util.Set<String> FOOD = java.util.Set.of("CAKE", "MILK_BUCKET");
    private static final java.util.Set<String> TOOLS = java.util.Set.of("SHEARS", "FLINT_AND_STEEL", "FISHING_ROD",
        "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK", "COMPASS", "RECOVERY_COMPASS", "CLOCK", "SPYGLASS", "BRUSH",
        "LEAD", "NAME_TAG", "MAP", "BUCKET", "FIREWORK_ROCKET", "GOAT_HORN", "WRITABLE_BOOK", "BOOK", "BUNDLE");
    private static final java.util.Set<String> TRANSPORT = java.util.Set.of("SADDLE", "ELYTRA");
    private static final List<String> REDSTONE_PARTS = List.of("REDSTONE", "PISTON", "REPEATER", "COMPARATOR",
        "OBSERVER", "HOPPER", "DROPPER", "DISPENSER", "LEVER", "_BUTTON", "PRESSURE_PLATE", "TRIPWIRE",
        "DAYLIGHT_DETECTOR", "TARGET", "SCULK_SENSOR", "LECTERN", "NOTE_BLOCK", "TNT", "TRAPPED_CHEST",
        "LIGHTNING_ROD", "CRAFTER", "COPPER_BULB", "SLIME_BLOCK", "HONEY_BLOCK");
    private static final List<String> BUILDING_SHAPES = List.of("_STAIRS", "_SLAB", "_WALL", "_FENCE", "_FENCE_GATE",
        "_DOOR", "_TRAPDOOR", "_PLANKS", "_LOG", "_WOOD", "_BRICKS", "_TILES");

    private DonPollosReward() {
    }

    /** A que categoria va un item, segun su nombre y si es bloque, solido, opaco o comestible. */
    static Category category(String name, boolean block, boolean solid, boolean occluding, boolean edible) {
        if (COMBAT.contains(name) || name.endsWith("_SWORD") || name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
            || name.endsWith("_HORSE_ARMOR")) {
            return Category.COMBATE;
        }
        if (BREWING.contains(name) || name.contains("POTION")) {
            return Category.POCIONES;
        }
        if (edible || FOOD.contains(name)) {
            return Category.COMIDA;
        }
        if (TOOLS.contains(name) || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
            || name.endsWith("_AXE") || name.endsWith("_BUCKET") || name.endsWith("_BUNDLE")
            || name.startsWith("MUSIC_DISC_")) {
            return Category.HERRAMIENTAS;
        }
        if (TRANSPORT.contains(name) || name.contains("MINECART") || name.contains("RAIL") || name.endsWith("_BOAT")
            || name.endsWith("_RAFT")) {
            return Category.TRANSPORTE;
        }
        if (REDSTONE_PARTS.stream().anyMatch(name::contains)) {
            return Category.REDSTONE;
        }
        if (block) {
            boolean shape = BUILDING_SHAPES.stream().anyMatch(name::endsWith);
            return shape || (solid && occluding) ? Category.CONSTRUCCION : Category.DECORACION;
        }
        return Category.VARIOS;
    }

    static int pageCount(int items, int perPage) {
        return Math.max(1, (items + perPage - 1) / perPage);
    }

    static <T> List<T> page(List<T> items, int page, int perPage) {
        int from = Math.max(0, page) * perPage;
        if (from >= items.size()) {
            return List.of();
        }
        return items.subList(from, Math.min(items.size(), from + perPage));
    }

    /** DIAMOND_SWORD -> Diamond Sword */
    static String pretty(String enumName) {
        StringBuilder out = new StringBuilder();
        for (String word : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
