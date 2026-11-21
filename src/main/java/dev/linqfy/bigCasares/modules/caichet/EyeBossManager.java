package dev.linqfy.bigCasares.modules.caichet;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * La pelea contra el ojo con alas: se invoca tocando una de las puertas sueltas del Sueño y se le pega con la mano
 * (3 golpes) cuando baja. Uno por jugador, y solo
 * lo ve quien lo invoco. Desaparece si te moris, salis del Sueño o te alejas mucho. Corre una vez por tick.
 */
public final class EyeBossManager implements Listener, Runnable {

    /** Si te alejas mas que esto del ojo, se va. */
    private static final double LEASH = 70;

    private final Plugin plugin;
    private final Map<UUID, EyeBoss> bosses = new HashMap<>();

    EyeBossManager(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Iterator<EyeBoss> it = bosses.values().iterator(); it.hasNext(); ) {
            EyeBoss boss = it.next();
            Player p = boss.owner;
            boolean gone = !p.isOnline() || p.isDead() || !DreamWorld.isDream(p.getWorld())
                    || p.getLocation().distance(boss.position()) > LEASH;
            if (gone) {
                boss.remove();
                it.remove();
                if (boss.defeated() && p.isOnline() && !p.isDead()) {
                    EyeBoss.wakeUp(p); // ya habia ganado: se despierta igual
                }
            } else {
                boolean alive;
                try {
                    alive = boss.tick();
                } catch (RuntimeException e) {
                    // un ojo roto se va solo: no frena a los de los demas jugadores ni repite el error
                    plugin.getLogger().log(Level.WARNING, "[CaiChet] El ojo de " + p.getName() + " fallo y se retira", e);
                    boss.remove();
                    alive = false;
                }
                if (!alive) {
                    it.remove();
                }
            }
        }
    }

    void removeAll() {
        for (EyeBoss boss : bosses.values()) {
            boss.remove();
            if (boss.defeated() && boss.owner.isOnline() && !boss.owner.isDead()) {
                EyeBoss.wakeUp(boss.owner);
            }
        }
        bosses.clear();
    }

    /**
     * Le habia ganado al ojo pero no llego a despertarse (se desconecto, se murio o se apago el servidor durante la
     * despedida): si vuelve dentro del Sueño, se despierta; si ya esta afuera, no queda nada pendiente.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        settlePending(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        CaiChetRuntime.tasks().next(() -> settlePending(player));
    }

    private void settlePending(Player player) {
        if (!player.getPersistentDataContainer().has(EyeBoss.WAKE_PENDING, PersistentDataType.BYTE)) {
            return;
        }
        if (!DreamWorld.isDream(player.getWorld())) {
            player.getPersistentDataContainer().remove(EyeBoss.WAKE_PENDING);
            return;
        }
        CaiChetRuntime.tasks().later(() -> {
            if (player.isOnline() && !player.isDead() && DreamWorld.isDream(player.getWorld())) {
                EyeBoss.wakeUp(player);
            }
        }, 20L);
    }

    /** Tocar una puerta suelta del Sueño (no las de las casas) invoca al ojo. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDoor(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !DreamWorld.isDream(block.getWorld()) || !Tag.DOORS.isTagged(block.getType())
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!DreamLayout.isStandaloneDoor(block.getWorld().getSeed(), block.getX(), block.getY(), block.getZ())) {
            return;
        }
        event.setCancelled(true);
        summon(event.getPlayer(), block.getLocation().add(0.5, 3, 0.5));
    }

    /** @return false si ese jugador ya tiene un ojo */
    boolean summon(Player player, Location at) {
        if (bosses.containsKey(player.getUniqueId())) {
            return false;
        }
        bosses.put(player.getUniqueId(), new EyeBoss(plugin, player, at));
        return true;
    }

    /** Pegarle al ojo, con la mano o con cualquier cosa. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        EyeBoss boss = bosses.get(event.getPlayer().getUniqueId());
        if (boss != null && boss.isBody(event.getAttacked())) {
            event.setCancelled(true);
            boss.hit();
        }
    }

    /**
     * Un puñetazo (o golpe con lo que tengas en la mano) apuntandole al ojo a menos de 5 bloques: cuenta aunque la caja
     * de choque no haya llegado a tiempo (el ojo se mueve rapido cuando baja).
     */
    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        EyeBoss boss = bosses.get(player.getUniqueId());
        if (boss == null) {
            return;
        }
        Location eye = player.getEyeLocation();
        Vector from = eye.toVector();
        Vector to = from.clone().add(eye.getDirection().multiply(5));
        if (Watchers.distanceToSegment(boss.position().toVector(), from, to) <= 1.9) {
            boss.hit();
        }
    }
}
