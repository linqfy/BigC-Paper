package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/** Bukkit saveData is void and can log an IO failure without throwing it. */
final class LegendaryPlayerSaveVerifier {
    @FunctionalInterface interface Reader { InputStream open() throws IOException; }
    private static final int MAX_PLAYER_DATA = 64 * 1024 * 1024;
    private static final NamespacedKey CHECKPOINT = new NamespacedKey("bigcasares", "legendary_inventory_checkpoint");
    private LegendaryPlayerSaveVerifier() {}

    static void checkpoint(Player player) throws IOException {
        String nonce = UUID.randomUUID().toString();
        player.getPersistentDataContainer().set(CHECKPOINT, PersistentDataType.STRING, nonce);
        save(player, CHECKPOINT, nonce);
    }

    static void save(Player player, NamespacedKey key, String nonce) throws IOException {
        var file = playerFile(player.getServer().getWorlds().getFirst().getWorldFolder().toPath(), player.getUniqueId());
        saveAndVerify(player::saveData, () -> Files.newInputStream(file), key.toString(), nonce);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        LegendaryDurability.forceDirectory(file.getParent());
    }

    static java.nio.file.Path playerFile(java.nio.file.Path worldFolder, UUID player) throws IOException {
        // 26.3 separates dimension folders from shared players/data. Also
        // support the legacy playerdata directory for migrated installations.
        java.nio.file.Path root = worldFolder.toAbsolutePath().normalize();
        for (int depth = 0; root != null && depth < 4; depth++, root = root.getParent()) {
            for (String relative : java.util.List.of("players/data", "playerdata")) {
                var directory = root.resolve(relative);
                if (Files.isDirectory(directory)) return directory.resolve(player + ".dat");
            }
        }
        throw new IOException("No se encontró el directorio nativo de datos de jugadores.");
    }

    static void saveAndVerify(Runnable save, Reader reader, String key, String nonce) throws IOException {
        save.run();
        // Match a complete NBT STRING key/value pair. Merely finding the nonce
        // elsewhere, or seeing the file timestamp change, is not confirmation.
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(encoded)) {
            output.writeByte(8);
            output.writeUTF(key);
            output.writeUTF(nonce);
        }
        byte[] pattern = encoded.toByteArray();
        try (InputStream input = new GZIPInputStream(reader.open())) {
            byte[] data = input.readNBytes(MAX_PLAYER_DATA + 1);
            if (data.length > MAX_PLAYER_DATA) throw new IOException("Los datos del jugador exceden el límite de verificación.");
            for (int index = 0; index <= data.length - pattern.length; index++) {
                boolean match = true;
                for (int offset = 0; offset < pattern.length; offset++) {
                    if (data[index + offset] != pattern[offset]) { match = false; break; }
                }
                if (match) return;
            }
        }
        throw new IOException("No se pudo confirmar el inventario guardado del jugador; la transacción queda pendiente.");
    }
}
