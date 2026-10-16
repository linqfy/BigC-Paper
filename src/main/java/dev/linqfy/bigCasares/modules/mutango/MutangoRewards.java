package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Las victorias contra Neguechen y los premios (Botas de Neguechen y Cruz Andina): se anotan al ganar (MutangoPending), se
 * entregan en el inventario cuando termina la pelea y, si no se pudo (desconectado, muerto, servidor apagado), al volver.
 */
final class MutangoRewards implements Listener {

    private final JavaPlugin plugin;
    private final MutangoKeys keys;
    private final MutangoPending pending;
    private final MutangoRelics relics;

    private final MutangoTasks tasks;

    MutangoRewards(JavaPlugin plugin, MutangoKeys keys, MutangoPending pending, MutangoRelics relics,
                   MutangoTasks tasks) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.keys = keys;
        this.pending = pending;
        this.relics = relics;
    }

    /** Cuantas veces le gano a Neguechen. */
    int wins(Player player) {
        return player.getPersistentDataContainer().getOrDefault(keys.wins, PersistentDataType.INTEGER, 0);
    }

    /**
     * Vencieron a Neguechen: a cada uno que peleo en esa pelea (el que la empezo y los que le pegaron a alguno) le suma
     * una victoria (los que miraban de lejos no) y le anota los premios como pendientes. Todo se guarda ya mismo: si el
     * servidor se apaga o sacan la pelea antes de que se entreguen los premios, no se pierde nada. A quien no esta
     * conectado le queda anotado para cuando vuelva.
     */
    void registerVictory(MutangoFight fight) {
        for (UUID id : fight.fighters) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                addWin(player, 1);
            } else {
                pending.addWin(id);
            }
            pending.addReward(id);
        }
        fight.rewardsPending = true;
        announceVictory(fight);
    }

    /** Le avisa a todo el servidor quien le gano a Neguechen (los que pelearon, conectados o no). */
    private void announceVictory(MutangoFight fight) {
        List<String> names = new ArrayList<>();
        for (UUID id : fight.fighters) {
            String name = plugin.getServer().getOfflinePlayer(id).getName();
            if (name != null) {
                names.add(name);
            }
        }
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        plugin.getServer().broadcast(legacy.deserialize("§6§l" + MutangoVictoryMessage.of(names)));
    }

    /** Les da los premios pendientes de esa pelea a los que estan conectados (los demas los reciben al entrar). */
    void deliverRewards(MutangoFight fight) {
        if (!fight.rewardsPending) {
            return;
        }
        fight.rewardsPending = false;
        for (UUID id : fight.fighters) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                claimPending(player);
            }
        }
    }

    /**
     * Le da a este jugador lo que le falta recibir: victorias que ganaron mientras no estaba y premios (en el inventario;
     * lo que no entra, al piso). Si esta muerto espera a que reviva.
     */
    void claimPending(Player player) {
        if (!player.isOnline() || player.isDead() || !pending.has(player.getUniqueId())) {
            return;
        }
        MutangoPending.Entry entry = pending.take(player.getUniqueId());
        if (entry.wins() > 0) {
            addWin(player, entry.wins());
        }
        boolean overflow = false;
        for (int i = 0; i < entry.rewards(); i++) {
            overflow |= relics.give(player);
        }
        if (entry.rewards() > 0) {
            player.sendMessage("§6Recibiste las Botas de Neguechen y la Cruz Andina."
                + (overflow ? " §7(No te entraban en el inventario: lo que sobro quedo en el piso, a tus pies.)" : ""));
        }
    }

    private void addWin(Player player, int amount) {
        int wins = wins(player) + amount;
        player.getPersistentDataContainer().set(keys.wins, PersistentDataType.INTEGER, wins);
        player.sendMessage("§6Ya le ganaste §f" + wins + "§6 " + (wins == 1 ? "vez" : "veces")
            + " a Neguechen. §7La proxima vez que los despiertes van a ser mas fuertes.");
    }

    /** Si ganaste mientras no estabas (o se apago el servidor antes de darte los premios), te lo dan al volver. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        tasks.next("claim-on-respawn", () -> claimPending(player));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tasks.later("claim-on-join", () -> claimPending(player), 40L);
    }
}
