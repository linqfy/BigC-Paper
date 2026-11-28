package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.platform.ClientEntityPresentationRegistry;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las figuras 3D de cai chet tal como las necesita la integracion con Geyser. Bedrock no dibuja los displays, asi que
 * cada figura tiene un "cuerpo" vanilla quieto en su lugar (ver {@link Bedrock#markStand}). Si Geyser tiene las entidades
 * custom activas, ese cuerpo se anota aca con su marca y Geyser lo muestra como la entidad {@code caichet:*} del pack
 * Bedrock, con el modelo de Java; si no, Bedrock sigue viendo el mob comun de siempre.
 */
public final class CaiChetBedrockEntities {

    /**
     * Una entidad Bedrock y el cuerpo Java al que reemplaza.
     *
     * @param javaEntityType    el tipo del cuerpo vanilla (nombre de {@code EntityType})
     * @param bedrockIdentifier el identificador del client_entity del pack Bedrock
     * @param markerKey         la marca con la que se anota el cuerpo; distingue figuras sobre el mismo tipo Java
     */
    public record Entry(String javaEntityType, String bedrockIdentifier, String markerKey) {
    }

    public static final Entry BOSS_EYE = new Entry("ALLAY", "caichet:boss_eye", "caichet_boss_eye");
    public static final Entry WATCHER_MOYAN = new Entry("IRON_GOLEM", "caichet:watcher_moyan", "caichet_watcher_moyan");
    public static final Entry WATCHER_ESTEVO = new Entry("IRON_GOLEM", "caichet:watcher_estevo", "caichet_watcher_estevo");
    public static final Entry DREAM_EYE_1 = new Entry("HAPPY_GHAST", "caichet:dream_eye_1", "caichet_dream_eye_1");
    public static final Entry DREAM_EYE_2 = new Entry("HAPPY_GHAST", "caichet:dream_eye_2", "caichet_dream_eye_2");
    public static final Entry DREAM_EYE_3 = new Entry("HAPPY_GHAST", "caichet:dream_eye_3", "caichet_dream_eye_3");
    public static final Entry DREAM_CLOUD = new Entry("HAPPY_GHAST", "caichet:dream_cloud", "caichet_dream_cloud");
    public static final Entry DREAM_WINGED = new Entry("ALLAY", "caichet:dream_winged", "caichet_dream_winged");
    public static final Entry DREAM_MUSHROOM = new Entry("MOOSHROOM", "caichet:dream_mushroom", "caichet_dream_mushroom");
    public static final Entry DREAM_WINDOW = new Entry("ARMOR_STAND", "caichet:dream_window", "caichet_dream_window");
    public static final Entry DREAM_DOOR = new Entry("ARMOR_STAND", "caichet:dream_door", "caichet_dream_door");
    public static final Entry DREAM_VOID = new Entry("SQUID", "caichet:dream_void", "caichet_dream_void");
    public static final Entry DREAM_RAINBOW = new Entry("ARMOR_STAND", "caichet:dream_rainbow", "caichet_dream_rainbow");
    public static final Entry TRAIN = new Entry("MINECART", "caichet:demon_train", "caichet_demon_train");
    public static final Entry SONRISA = new Entry("ALLAY", "caichet:sonrisa", "caichet_sonrisa");
    public static final Entry E666 = new Entry("IRON_GOLEM", "caichet:e666", "caichet_e666");
    public static final Entry JUANCARLOS = new Entry("IRON_GOLEM", "caichet:juancarlos", "caichet_juancarlos");
    public static final Entry ARANA = new Entry("IRON_GOLEM", "caichet:arana", "caichet_arana");
    public static final Entry PAYASO = new Entry("IRON_GOLEM", "caichet:payaso", "caichet_payaso");

    private static final List<Entry> ALL = List.of(
        BOSS_EYE, WATCHER_MOYAN, WATCHER_ESTEVO, DREAM_EYE_1, DREAM_EYE_2, DREAM_EYE_3, DREAM_CLOUD, DREAM_WINGED,
        DREAM_MUSHROOM, DREAM_WINDOW, DREAM_DOOR, DREAM_VOID, DREAM_RAINBOW, TRAIN, SONRISA, E666, JUANCARLOS, ARANA,
        PAYASO);

    private static final Set<UUID> PRESENTED = ConcurrentHashMap.newKeySet();
    private static volatile boolean available;

    private CaiChetBedrockEntities() {
    }

    public static List<Entry> all() {
        return ALL;
    }

    /** Lo pone la integracion con Geyser: true solo si registro estas entidades custom. */
    public static void setAvailable(boolean value) {
        available = value;
    }

    /** Geyser muestra estas figuras con su modelo (si no, Bedrock ve los cuerpos vanilla de siempre). */
    public static boolean available() {
        return available;
    }

    /** El modelo de un ojo del Sueño ({@code dream_eye1} a {@code dream_eye3}) tiene su entidad. */
    static Entry dreamEye(String model) {
        return switch (model) {
            case "dream_eye1" -> DREAM_EYE_1;
            case "dream_eye2" -> DREAM_EYE_2;
            default -> DREAM_EYE_3;
        };
    }

    /** La figura del hotel con este modelo ({@code watcher_moyan} o {@code watcher_estevo}). */
    static Entry watcher(String model) {
        return "watcher_estevo".equals(model) ? WATCHER_ESTEVO : WATCHER_MOYAN;
    }

    /** Anota el cuerpo vanilla para que Geyser lo muestre como {@code entry}. No hace nada sin entidades custom. */
    static void present(Entity body, Entry entry) {
        present(body.getUniqueId(), entry);
    }

    static void present(UUID bodyId, Entry entry) {
        if (!available) {
            return;
        }
        ClientEntityPresentationRegistry.register(bodyId, entry.javaEntityType(), entry.markerKey());
        PRESENTED.add(bodyId);
    }

    /** Borra el cuerpo y su anotacion. Acepta null. */
    static void discard(Entity body) {
        if (body == null) {
            return;
        }
        release(body.getUniqueId());
        body.remove();
    }

    static void release(UUID id) {
        if (PRESENTED.remove(id)) {
            ClientEntityPresentationRegistry.unregister(id);
        }
    }

    /** Quita la anotacion de lo que quedo vivo (al apagar el modulo). */
    static void releaseAll() {
        for (UUID id : Set.copyOf(PRESENTED)) {
            release(id);
        }
    }
}
