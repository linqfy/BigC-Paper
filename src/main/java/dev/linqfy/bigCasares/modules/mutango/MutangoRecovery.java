package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Lo que se arregla despues de una caida: devuelve los bloques que la grieta y las ondas dejaron sacados (el diario
 * MutangoBlockJournal) y sube a quien entra incrustado en bloques.
 */
final class MutangoRecovery implements Listener {

    private final JavaPlugin plugin;
    private final MutangoBlockJournal journal;
    /** Bloques por devolver que estan en chunks sin cargar, por chunk (se devuelven cuando carga). */
    private final Map<String, List<MutangoBlockJournal.Entry>> waiting = new HashMap<>();

    private final MutangoTasks tasks;

    MutangoRecovery(JavaPlugin plugin, MutangoBlockJournal journal, MutangoTasks tasks) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.journal = journal;
    }

    /**
     * Si el servidor se cayo con la grieta abierta o las ondas arrancando bloques, devuelve los que faltan (los que
     * siguen siendo aire o la luz verde de la grieta; si alguien puso algo ahi, se respeta). Se llama al arrancar: nadie
     * pelea todavia (Neguechen retomada tiene la grieta ya cerrada). Solo toca los chunks que ya estan cargados (no carga
     * ninguno de forma sincrona al arrancar): los bloques de los demas siguen anotados y se devuelven cuando su chunk carga.
     */
    void restoreLeftoverBlocks() {
        int restored = 0;
        int waitingForChunk = 0;
        for (MutangoBlockJournal.Entry entry : journal.entries()) {
            World world = plugin.getServer().getWorld(entry.world());
            if (world == null) {
                continue; // ese mundo no esta cargado ahora: queda anotado
            }
            if (!world.isChunkLoaded(entry.x() >> 4, entry.z() >> 4)) {
                waiting.computeIfAbsent(chunkKey(entry.world(), entry.x() >> 4, entry.z() >> 4),
                    key -> new ArrayList<>()).add(entry);
                waitingForChunk++;
                continue;
            }
            restored += restore(world, entry);
        }
        flush();
        if (restored > 0) {
            plugin.getLogger().warning("Se devolvieron " + restored + " bloques que quedaron sacados (el servidor se cerro con "
                + "la grieta o las ondas de las manos abiertas).");
        }
        if (waitingForChunk > 0) {
            plugin.getLogger().info("[Mutango] " + waitingForChunk + " bloques por devolver estan en chunks sin cargar: se "
                + "devuelven cuando cada chunk cargue.");
        }
    }

    /** Un chunk carga: se devuelven los bloques que habian quedado esperandolo. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (waiting.isEmpty()) {
            return;
        }
        List<MutangoBlockJournal.Entry> entries = waiting.remove(chunkKey(event.getWorld().getUID(),
            event.getChunk().getX(), event.getChunk().getZ()));
        if (entries == null) {
            return;
        }
        int restored = 0;
        for (MutangoBlockJournal.Entry entry : entries) {
            restored += restore(event.getWorld(), entry);
        }
        flush();
        if (restored > 0) {
            plugin.getLogger().info("[Mutango] Se devolvieron " + restored + " bloques que habian quedado sacados en un chunk "
                + "que recien cargo.");
        }
    }

    /** Devuelve ese bloque si sigue siendo aire o la luz de la grieta. Devuelve 1 si lo cambio. */
    private int restore(World world, MutangoBlockJournal.Entry entry) {
        int changed = 0;
        try {
            Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
            Material now = block.getType();
            if (now.isAir() || now == Material.VERDANT_FROGLIGHT) {
                block.setBlockData(plugin.getServer().createBlockData(entry.data()), false);
                changed = 1;
            }
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning("Bloque por devolver ilegible: " + entry);
        }
        // ya devuelto (o ya estaba): sigue anotado hasta que el mundo se guarde con el bloque devuelto
        journal.restored(entry.world(), entry.x(), entry.y(), entry.z());
        return changed;
    }

    private void flush() {
        try {
            journal.flush();
        } catch (IllegalStateException failed) {
            plugin.getLogger().warning(failed.getMessage());
        }
    }

    private static String chunkKey(UUID world, int chunkX, int chunkZ) {
        return world + "|" + chunkX + "|" + chunkZ;
    }

    /**
     * Si al entrar el jugador quedo incrustado en bloques (salio mientras estaba dentro de la grieta o de un hueco de las
     * ondas y despues se devolvieron los bloques), lo sube a la superficie, igual que a los que estan conectados.
     */
    private void rescueIfStuck(Player player) {
        if (!player.isOnline() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Location at = player.getLocation();
        Block feet = at.getBlock();
        Block head = player.getEyeLocation().getBlock();
        if (feet.isPassable() || head.isPassable()) {
            return;
        }
        Location up = at.clone();
        up.setY(MutangoGround.y(at) + 1.1);
        player.teleport(up);
        player.setFallDistance(0);
        player.sendMessage("§7Habías quedado dentro de unos bloques: te subí a la superficie.");
    }

    /** El mundo se guardo: los bloques devueltos hasta ahora ya estan guardados y se tachan del diario. */
    @EventHandler
    public void onWorldSave(WorldSaveEvent event) {
        journal.worldSaved(event.getWorld().getUID());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tasks.later("rescue-if-stuck", () -> rescueIfStuck(player), 5L);
    }
}
