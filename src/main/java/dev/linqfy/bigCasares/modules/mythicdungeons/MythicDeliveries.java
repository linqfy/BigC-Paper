package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Entrega lo que el modulo le da a un jugador (Invocador devuelto o premio de un jefe): ya si esta conectado, y si no
 * queda en {@link MythicPendingDeliveries} hasta que entre. Con el inventario lleno lo que no entra cae a sus pies.
 */
final class MythicDeliveries implements Listener {

    private final Server server;
    private final MythicPendingDeliveries pending;
    private final Function<String, Optional<ItemStack>> catalog;
    private final Logger logger;

    MythicDeliveries(Server server, MythicPendingDeliveries pending, Function<String, Optional<ItemStack>> catalog,
                     Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Da la entrega ahora si el jugador esta conectado; si no, la guarda para cuando entre. */
    void deliver(MythicPendingDeliveries.Delivery delivery) {
        Player player = server.getPlayer(delivery.playerId());
        if (player != null && player.isOnline() && give(player, delivery, false)) {
            return;
        }
        pending.add(delivery);
        logger.info("[Mazmorras] " + delivery.playerId() + " no estaba conectado: " + describe(delivery)
            + " queda guardado para cuando entre.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (MythicPendingDeliveries.Delivery delivery : pending.pending(player.getUniqueId())) {
            List<ItemStack> stacks = stacks(delivery);
            if (stacks == null) {
                continue; // el catalogo no puede crear el item ahora: queda pendiente
            }
            if (pending.take(delivery.id())) {
                hand(player, delivery, stacks, true);
            }
        }
    }

    private boolean give(Player player, MythicPendingDeliveries.Delivery delivery, boolean late) {
        List<ItemStack> stacks = stacks(delivery);
        if (stacks == null) {
            return false;
        }
        hand(player, delivery, stacks, late);
        return true;
    }

    private void hand(Player player, MythicPendingDeliveries.Delivery delivery, List<ItemStack> stacks,
                      boolean late) {
        if (delivery.xp() > 0) {
            player.giveExp(delivery.xp());
        }
        for (ItemStack stack : stacks) {
            player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        MythicCulture.MythicBoss boss = delivery.culture().requireBoss();
        String prefix = late ? "§7(mientras no estabas) " : "";
        player.sendMessage(switch (delivery.kind()) {
            case SUMMONER -> prefix + "§eTe devolvimos el Invocador: " + boss.displayName()
                + " §edesapareció sin ser derrotado.";
            case REWARD -> prefix + "§6Puesto #" + delivery.placement() + " contra " + boss.displayName()
                + "§6: §a+" + delivery.xp() + " de experiencia§6 y el botín del jefe.";
        });
    }

    /** Los items de la entrega, o {@code null} si el Invocador no se puede crear ahora. */
    private List<ItemStack> stacks(MythicPendingDeliveries.Delivery delivery) {
        List<ItemStack> stacks = new ArrayList<>();
        if (delivery.kind() == MythicPendingDeliveries.Kind.SUMMONER) {
            Optional<ItemStack> summoner = delivery.culture().summonerItemId().flatMap(catalog);
            if (summoner.isEmpty()) {
                return null;
            }
            stacks.add(summoner.get());
        }
        for (MythicDungeonSettings.RewardItem item : delivery.items()) {
            stacks.add(new ItemStack(item.material(), item.amount()));
        }
        return stacks;
    }

    private static String describe(MythicPendingDeliveries.Delivery delivery) {
        String boss = delivery.culture().requireBoss().id();
        return switch (delivery.kind()) {
            case SUMMONER -> "el Invocador de " + boss;
            case REWARD -> "su premio de " + boss;
        };
    }
}
