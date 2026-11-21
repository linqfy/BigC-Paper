package dev.linqfy.bigCasares.modules.alexscaves.core.damage;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Damage types shipped in the module datapack. Until the server restarts with
 * the datapack loaded they fall back to a vanilla type, so the mechanics still
 * work with a generic death message.
 */
public final class ModDamageTypes {
    public static final String RADIATION = "radiation";
    public static final String ACID = "acid";
    public static final String NUKE = "nuke";
    public static final String RAYGUN = "raygun";

    private final Map<String, DamageType> resolved = new HashMap<>();
    private final boolean loaded;

    private ModDamageTypes(Map<String, DamageType> resolved, boolean loaded) {
        this.resolved.putAll(resolved);
        this.loaded = loaded;
    }

    public static ModDamageTypes lookup() {
        Registry<DamageType> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE);
        Map<String, DamageType> found = new HashMap<>();
        for (String id : new String[] {RADIATION, ACID, NUKE, RAYGUN, "dark_arrow", "desolate_dagger",
            "forsaken_sonic_boom", "gumball", "intentional_game_design", "spirit_dinosaur", "tremorzilla_beam"}) {
            DamageType type = registry.get(new NamespacedKey("alexscaves", id));
            if (type != null) {
                found.put(id, type);
            }
        }
        return new ModDamageTypes(found, found.containsKey(RADIATION));
    }

    /** True once the datapack damage types are available (otherwise vanilla fallbacks are used). */
    public boolean loaded() {
        return loaded;
    }

    public DamageType type(String id) {
        DamageType type = resolved.get(id);
        if (type != null) {
            return type;
        }
        return switch (id) {
            case NUKE -> DamageType.EXPLOSION;
            case ACID -> DamageType.ON_FIRE;
            default -> DamageType.MAGIC;
        };
    }

    public DamageSource source(String id) {
        return DamageSource.builder(type(id)).build();
    }

    public DamageSource source(String id, Entity cause) {
        return DamageSource.builder(type(id)).withCausingEntity(cause).withDirectEntity(cause).build();
    }
}
