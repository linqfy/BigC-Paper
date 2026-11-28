package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Anota en el chunk que bloques son portales. Los bloques no tienen PersistentDataContainer, asi que cada chunk
 * guarda una lista de texto "D:x,y,z;M:x,y,z" con las posiciones. Asi un bloque de concreto normal nunca es portal.
 */
public final class PortalRegistry {

    private static final NamespacedKey KEY = new NamespacedKey("caichet", "portals");

    private PortalRegistry() {
    }

    public static PortalKind get(Block block) {
        String suffix = position(block);
        for (String entry : read(block)) {
            if (entry.substring(2).equals(suffix)) {
                PortalKind kind = PortalKind.byCode(entry.charAt(0));
                if (kind != null && block.getType() == kind.material) {
                    return kind;
                }
                // el bloque ya no es el del portal (lo saco una explosion, un piston, otro plugin): la anotacion
                // vieja se borra, asi un concreto comun puesto despues en ese lugar no hereda el portal
                remove(block);
                return null;
            }
        }
        return null;
    }

    public static void put(Block block, PortalKind kind) {
        List<String> entries = read(block);
        String suffix = position(block);
        entries.removeIf(entry -> entry.substring(2).equals(suffix));
        entries.add(kind.code + ":" + suffix);
        write(block, entries);
    }

    /** @return el tipo que tenia el bloque, o null si no era un portal */
    public static PortalKind remove(Block block) {
        List<String> entries = read(block);
        String suffix = position(block);
        PortalKind removed = null;
        for (String entry : entries) {
            if (entry.substring(2).equals(suffix)) {
                removed = PortalKind.byCode(entry.charAt(0));
            }
        }
        if (entries.removeIf(entry -> entry.substring(2).equals(suffix))) {
            write(block, entries);
        }
        return removed;
    }

    private static String position(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private static List<String> read(Block block) {
        String raw = block.getChunk().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        List<String> entries = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String entry : raw.split(";")) {
                if (entry.length() > 2) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    private static void write(Block block, List<String> entries) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        if (entries.isEmpty()) {
            pdc.remove(KEY);
        } else {
            pdc.set(KEY, PersistentDataType.STRING, String.join(";", entries));
        }
    }
}
