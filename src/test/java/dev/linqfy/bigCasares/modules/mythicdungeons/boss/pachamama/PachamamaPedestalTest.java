package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import dev.linqfy.bigCasares.modules.mythicdungeons.MandingaGuitar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PachamamaPedestalTest {

    private static final NamespacedKey ITEM_ID = new NamespacedKey("bigcasares", "item_id");
    private static final NamespacedKey MODEL = NamespacedKey.minecraft("crossbow");

    private Object previousServer;

    @BeforeEach
    void installServer() throws Exception {
        previousServer = serverField().get(null);
        serverField().set(null, proxy(Server.class, (name, args) -> "getItemFactory".equals(name)
            ? itemFactory() : null));
    }

    @AfterEach
    void restoreServer() throws Exception {
        serverField().set(null, previousServer);
    }

    @Test
    void theShownStackLooksLikeTheGuitarButIsNotIt() {
        ItemStack guitar = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = guitar.getItemMeta();
        meta.getPersistentDataContainer().set(ITEM_ID, PersistentDataType.STRING, MandingaGuitar.ITEM_ID);
        meta.setItemModel(MODEL);
        guitar.setItemMeta(meta);
        // MandingaGuitar.isGuitar reads exactly this tag (its Material.isAir check needs a live registry)
        assertEquals(MandingaGuitar.ITEM_ID, catalogId(guitar));

        ItemStack look = PachamamaPedestal.lookAlike(guitar);

        assertEquals(null, catalogId(look));
        assertEquals(Material.CROSSBOW, look.getType());
        assertEquals(MODEL, look.getItemMeta().getItemModel());
        assertTrue(look.getItemMeta().getPersistentDataContainer().getKeys().isEmpty());
    }

    @Test
    void thePaidDropNeverDespawnsBurnsOrGoesToSomeoneElse() {
        Map<String, Object> calls = new HashMap<>();
        Item item = proxy(Item.class, (name, args) -> {
            if (name.startsWith("set") && args != null && args.length == 1) {
                calls.put(name, args[0]);
            }
            return null;
        });
        UUID owner = UUID.randomUUID();

        PachamamaPaidDrop.protect(item, owner);

        assertEquals(owner, calls.get("setOwner"));
        assertEquals(true, calls.get("setUnlimitedLifetime"));
        assertEquals(true, calls.get("setInvulnerable"));
        assertEquals(true, calls.get("setPersistent"));
    }

    private static Object catalogId(ItemStack stack) {
        return stack.hasItemMeta()
            ? stack.getItemMeta().getPersistentDataContainer().get(ITEM_ID, PersistentDataType.STRING) : null;
    }

    // ----------------------------------------------------------------------- fakes

    private static Field serverField() throws Exception {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        return field;
    }

    private static ItemFactory itemFactory() {
        return proxy(ItemFactory.class, (name, args) -> switch (name) {
            case "getItemMeta" -> fakeMeta();
            case "isApplicable" -> true;
            case "asMetaFor" -> args[0];
            case "equals" -> args.length == 2 ? args[0] == args[1] : null;
            default -> null;
        });
    }

    private static ItemMeta fakeMeta() {
        Map<NamespacedKey, Object> data = new HashMap<>();
        NamespacedKey[] model = new NamespacedKey[1];
        PersistentDataContainer container = proxy(PersistentDataContainer.class, (name, args) -> switch (name) {
            case "set" -> data.put((NamespacedKey) args[0], args[2]);
            case "get" -> data.get(args[0]);
            case "has" -> data.containsKey(args[0]);
            case "getKeys" -> data.keySet();
            case "isEmpty" -> data.isEmpty();
            default -> null;
        });
        ItemMeta[] self = new ItemMeta[1];
        self[0] = proxy(ItemMeta.class, (name, args) -> switch (name) {
            case "getPersistentDataContainer" -> container;
            case "hasItemModel" -> model[0] != null;
            case "getItemModel" -> model[0];
            case "setItemModel" -> {
                model[0] = (NamespacedKey) args[0];
                yield null;
            }
            case "clone" -> self[0];
            default -> null;
        });
        return self[0];
    }

    private interface Handler {
        Object handle(String name, Object[] args);
    }

    private static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(PachamamaPedestalTest.class.getClassLoader(), new Class<?>[]{type},
            (self, method, args) -> {
                switch (method.getName()) {
                    case "hashCode" -> {
                        if (args == null) {
                            return System.identityHashCode(self);
                        }
                    }
                    case "toString" -> {
                        if (args == null) {
                            return type.getSimpleName();
                        }
                    }
                    case "equals" -> {
                        if (args != null && args.length == 1) {
                            return self == args[0];
                        }
                    }
                    default -> {
                    }
                }
                Object value = handler.handle(method.getName(), args == null ? new Object[0] : args);
                return value != null ? value : defaultValue(method.getReturnType());
            }));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
