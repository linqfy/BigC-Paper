package dev.linqfy.bigCasares.modules.mutango;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Las victorias contra Neguechen y los premios (Botas de Neguechen y Cruz Andina). Cada una es una adjudicacion
 * (MutangoPending): se anota en el archivo al ganar y se entrega con MutangoAwardClaim, que aplica victoria, items y recibo
 * en el jugador, lo guarda y recien ahi la saca de la cola. Si el jugador esta desconectado, muerto o no le entran los
 * premios, sigue pendiente y se reintenta al entrar, al revivir y (si faltaba lugar) cada tanto.
 */
final class MutangoRewards implements Listener {

    /** Los recibos que se recuerdan por jugador (los mas nuevos). */
    private static final int MAX_RECEIPTS = 64;

    private final JavaPlugin plugin;
    private final MutangoKeys keys;
    private final MutangoPending pending;
    private final MutangoRelics relics;
    private final MutangoTasks tasks;
    /** Conectados a quienes les falto lugar en el inventario: se les reintenta cada tanto hasta que entren. */
    private final Set<UUID> waitingForRoom = new HashSet<>();

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
     * Vencieron a Neguechen: a cada uno que peleo en esa pelea (el que la empezo y los que le pegaron a alguno) le anota
     * una victoria y un juego de premios (los que miraban de lejos no). Las dos cosas se guardan en el archivo antes de
     * tocar a nadie; la victoria se aplica ya mismo a los conectados y los premios se entregan al cerrarse la grieta
     * (deliverRewards) o, a quien no esta, cuando vuelva.
     */
    void registerVictory(MutangoFight fight) {
        for (UUID id : fight.fighters) {
            try {
                pending.add(id, 1, 0);
                pending.add(id, 0, 1);
            } catch (IllegalStateException unsaved) {
                // quedan en la cola de esta corrida igual; se avisa que no se pudo guardar el archivo
                plugin.getLogger().log(Level.SEVERE, "[Mutango] No se pudo guardar la adjudicacion", unsaved);
            }
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                claim(player, false, false);
            }
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
        var legacy = LegacyComponentSerializer.legacySection();
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

    /** Entrega todo lo que le falta a este jugador (con aviso si algo no entra en el inventario). */
    void claimPending(Player player) {
        claim(player, true, true);
    }

    /** Reintenta a los conectados a quienes les falto lugar (sin avisar de nuevo cada vez). */
    void retryOnline() {
        for (UUID id : new ArrayList<>(waitingForRoom)) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null) {
                waitingForRoom.remove(id);
            } else {
                claim(player, false, true);
            }
        }
    }

    /**
     * Entrega las adjudicaciones de ese jugador. includeRewards: tambien las de premios (si no, solo las victorias).
     * notify: avisa cuando los premios no entran. Si esta muerto o desconectado espera.
     */
    private void claim(Player player, boolean notify, boolean includeRewards) {
        if (!player.isOnline() || player.isDead() || !pending.has(player.getUniqueId())) {
            waitingForRoom.remove(player.getUniqueId());
            return;
        }
        boolean gave = false;
        boolean noRoom = false;
        for (MutangoPending.Award award : pending.of(player.getUniqueId())) {
            if (!includeRewards && award.rewards() > 0) {
                continue;
            }
            MutangoAwardClaim.Result result;
            try {
                result = MutangoAwardClaim.claim(award, receiptsOf(player), targetOf(player), this::confirm);
            } catch (RuntimeException failure) {
                // algo fallo a mitad: la adjudicacion sigue en la cola y se reintenta (el recibo evita repetirla)
                plugin.getLogger().log(Level.WARNING, "[Mutango] No se pudo entregar una adjudicacion de "
                    + player.getName() + ", se reintenta", failure);
                return;
            }
            gave |= result == MutangoAwardClaim.Result.CLAIMED && award.rewards() > 0;
            noRoom |= result == MutangoAwardClaim.Result.NO_ROOM;
        }
        if (noRoom) {
            waitingForRoom.add(player.getUniqueId());
        } else {
            waitingForRoom.remove(player.getUniqueId());
        }
        if (gave) {
            player.sendMessage("§6Recibiste las Botas de Neguechen y la Cruz Andina.");
        }
        if (noRoom && notify) {
            player.sendMessage("§eTenés premios de Neguechen esperando: liberá lugar en el inventario "
                + "(2 casilleros libres por juego) y los recibís solos.");
        }
    }

    private void confirm(UUID awardId) {
        try {
            pending.confirm(awardId);
        } catch (IllegalStateException unsaved) {
            // ya esta aplicada y con recibo en el jugador: si la cola no se pudo guardar, al reiniciar solo se reconfirma
            plugin.getLogger().log(Level.WARNING, "[Mutango] No se pudo guardar la cola de premios", unsaved);
        }
    }

    private MutangoAwardClaim.Receipts receiptsOf(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        return new MutangoAwardClaim.Receipts() {
            @Override
            public boolean has(UUID award) {
                return read().contains(award.toString());
            }

            @Override
            public void add(UUID award) {
                Set<String> all = read();
                all.add(award.toString());
                List<String> list = new ArrayList<>(all);
                if (list.size() > MAX_RECEIPTS) {
                    list = list.subList(list.size() - MAX_RECEIPTS, list.size());
                }
                data.set(keys.receipts, PersistentDataType.STRING, String.join(",", list));
            }

            @Override
            public void remove(UUID award) {
                Set<String> all = read();
                if (all.remove(award.toString())) {
                    data.set(keys.receipts, PersistentDataType.STRING, String.join(",", all));
                }
            }

            private Set<String> read() {
                String raw = data.get(keys.receipts, PersistentDataType.STRING);
                Set<String> set = new LinkedHashSet<>();
                if (raw != null && !raw.isBlank()) {
                    set.addAll(Arrays.asList(raw.split(",")));
                }
                return set;
            }
        };
    }

    private MutangoAwardClaim.Target targetOf(Player player) {
        return new MutangoAwardClaim.Target() {
            /** Como estaba el jugador al empezar la entrega (para deshacerla si falla antes de guardar). */
            private int winsBefore;
            private ItemStack[] storageBefore = new ItemStack[0];

            @Override
            public void begin() {
                winsBefore = wins(player);
                ItemStack[] contents = player.getInventory().getStorageContents();
                storageBefore = new ItemStack[contents.length];
                for (int i = 0; i < contents.length; i++) {
                    storageBefore[i] = contents[i] == null ? null : contents[i].clone();
                }
            }

            @Override
            public void rollback() {
                player.getPersistentDataContainer().set(keys.wins, PersistentDataType.INTEGER, winsBefore);
                player.getInventory().setStorageContents(storageBefore);
            }

            @Override
            public boolean hasRoom(int rewards) {
                return relics.hasRoomFor(player, rewards);
            }

            @Override
            public void addWins(int amount) {
                addWin(player, amount);
            }

            @Override
            public void giveRewards(int rewards) {
                for (int i = 0; i < rewards; i++) {
                    relics.giveToInventory(player);
                }
            }

            @Override
            public void persist() {
                player.saveData();
            }
        };
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
