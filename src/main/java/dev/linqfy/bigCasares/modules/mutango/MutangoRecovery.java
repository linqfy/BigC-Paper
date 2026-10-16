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
import org.bukkit.plugin.java.JavaPlugin;


/**
 * Lo que se arregla despues de una caida: devuelve los bloques que la grieta y las ondas dejaron sacados (el diario
 * MutangoBlockJournal) y sube a quien entra incrustado en bloques.
 */
final class MutangoRecovery implements Listener {

    private final JavaPlugin plugin;
    private final MutangoBlockJournal journal;

    private final MutangoTasks tasks;

    MutangoRecovery(JavaPlugin plugin, MutangoBlockJournal journal, MutangoTasks tasks) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.journal = journal;
    }

    /**
     * Si el servidor se cayo con la grieta abierta o las ondas arrancando bloques, devuelve los que faltan (los que
     * siguen siendo aire o la luz verde de la grieta; si alguien puso algo ahi, se respeta). Se llama al arrancar: nadie
     * pelea todavia (Neguechen retomada tiene la grieta ya cerrada).
     */
    void restoreLeftoverBlocks() {
        int restored = 0;
        for (MutangoBlockJournal.Entry entry : journal.entries()) {
            World world = plugin.getServer().getWorld(entry.world());
            if (world == null) {
                continue; // ese mundo no esta cargado ahora: queda anotado
            }
            try {
                Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
                Material now = block.getType();
                if (now.isAir() || now == Material.VERDANT_FROGLIGHT) {
                    block.setBlockData(plugin.getServer().createBlockData(entry.data()), false);
                    restored++;
                }
            } catch (IllegalArgumentException invalid) {
                plugin.getLogger().warning("Bloque por devolver ilegible: " + entry);
            }
            journal.release(entry.world(), entry.x(), entry.y(), entry.z());
        }
        try {
            journal.flush();
        } catch (IllegalStateException failed) {
            plugin.getLogger().warning(failed.getMessage());
        }
        if (restored > 0) {
            plugin.getLogger().warning("Se devolvieron " + restored + " bloques que quedaron sacados (el servidor se cerro con "
                + "la grieta o las ondas de las manos abiertas).");
        }
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tasks.later("rescue-if-stuck", () -> rescueIfStuck(player), 5L);
    }
}
