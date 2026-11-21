package dev.linqfy.bigCasares.modules.caichet;

import java.util.List;

/**
 * Los objetos del modulo tal como los necesita la integracion con Geyser: en Bedrock cada uno se ve con su propio
 * dibujo en lugar del objeto base. El icono es la clave que el pack Bedrock de BigCasares genera para la entrada
 * {@code caichet-<id>} de {@code resourcepack/shared/registry.yml}.
 */
public final class CaiChetBedrockItems {

    /**
     * Un objeto: el objeto vanilla sobre el que esta hecho, su item_model en Java y el nombre que se ve; si se bebe,
     * cuanto tarda el trago en milisegundos (0 si no se bebe).
     */
    public record Item(String javaBaseItem, String itemModel, String icon, String displayName, long drinkMillis) {
        public Item(String javaBaseItem, String itemModel, String icon, String displayName) {
            this(javaBaseItem, itemModel, icon, displayName, 0);
        }
    }

    /** El mismo orden y los mismos ids que usan las fabricas de objetos del modulo. */
    private static final List<Item> ALL = List.of(
        item("stick", "arma", "Arma de Fuego"),
        drink("botella", "Botella de Agua de Almendras", HotelLoot.BOTTLE_DRINK_MS),
        drink("jarra", "Jarra de Memorias", HotelLoot.JAR_DRINK_MS),
        item("paper", "llave", "Llave"),
        item("clock", "reloj_suenos", "Reloj de los Sueños"),
        item("totem_of_undying", "despertar", "Despertar"));

    private CaiChetBedrockItems() {
    }

    public static List<Item> all() {
        return ALL;
    }

    private static Item drink(String id, String name, long drinkMillis) {
        Item base = item("paper", id, name);
        return new Item(base.javaBaseItem(), base.itemModel(), base.icon(), base.displayName(), drinkMillis);
    }

    private static Item item(String base, String id, String name) {
        return new Item("minecraft:" + base, "caichet:" + id, "bigcasares.caichet_" + id, name);
    }
}
