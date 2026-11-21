package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.BigCasares;

import net.kyori.adventure.text.Component;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

final class LegendaryPvpProgression implements Listener {
    private record Context(UUID player, LegendaryWeapon weapon) {}

    private final BigCasares plugin;
    private final LegendaryPvpLevels levels;
    private final LegendaryKillCredits credits = new LegendaryKillCredits();

    private record Hit(UUID attacker, LegendaryWeapon weapon, long life) {}

    private final Map<EntityDamageByEntityEvent, Hit> pending = new IdentityHashMap<>();
    private Context current;

    LegendaryPvpProgression(BigCasares plugin, LegendaryPvpLevels levels) {
        this.plugin = plugin;
        this.levels = levels;
    }

    <T> T attribute(Player player, LegendaryWeapon weapon, Supplier<T> action) {
        Context previous = current;
        current = new Context(player.getUniqueId(), weapon);
        try {
            return action.get();
        } finally {
            current = previous;
        }
    }

    boolean inAbilityDamage() {
        return current != null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void capture(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)
                || !(event.getDamageSource().getCausingEntity() instanceof Player attacker)) return;
        // Snapshot before other listeners can swap equipment. Ability context belongs to
        // its original cast, even if the owner is holding a different weapon now.
        LegendaryWeapon weapon =
                current != null && current.player().equals(attacker.getUniqueId())
                        ? current.weapon()
                        : event.getDamager() instanceof Player
                                ? plugin.getCustomItemRegistry()
                                        .resolveItemId(attacker.getInventory().getItemInMainHand())
                                        .flatMap(LegendaryWeapon::find)
                                        .orElse(null)
                                : null;
        pending.put(
                event,
                new Hit(attacker.getUniqueId(), weapon, credits.generation(victim.getUniqueId())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void hit(EntityDamageByEntityEvent event) {
        Hit captured = pending.remove(event);
        if (!(event.getEntity() instanceof Player victim)
                || event.isCancelled()
                || event.getFinalDamage() <= 0) return;
        if (!(event.getDamageSource().getCausingEntity() instanceof Player attacker)
                || captured == null
                || !captured.attacker().equals(attacker.getUniqueId())) {
            credits.clear(victim.getUniqueId());
            return;
        }
        if (attacker.getGameMode() == GameMode.CREATIVE
                || attacker.getGameMode() == GameMode.SPECTATOR
                || victim.getGameMode() == GameMode.CREATIVE
                || victim.getGameMode() == GameMode.SPECTATOR) {
            credits.clear(victim.getUniqueId());
            return;
        }
        credits.record(
                victim.getUniqueId(),
                captured.attacker(),
                captured.weapon(),
                System.currentTimeMillis(),
                captured.life());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) {
        Player victim = event.getEntity(), killer = victim.getKiller();
        var credit =
                credits.consume(
                        victim.getUniqueId(),
                        killer == null ? null : killer.getUniqueId(),
                        System.currentTimeMillis());
        if (credit.isEmpty()) return;
        var earned = credit.orElseThrow();
        try {
            int old = levels.level(earned.killer(), earned.weapon());
            int next = levels.kill(earned.killer(), victim.getUniqueId(), earned.weapon());
            if (next > old && killer != null)
                killer.sendMessage(
                        Component.text(
                                "✦ " + earned.weapon().displayName + " — nivel " + next + "/5"));
            else if (old < LegendaryPvpLevels.MAX_LEVEL && killer != null)
                killer.sendActionBar(Component.text("El mismo rival cuenta una vez cada 10 minutos, entre todas las armas."));
        } catch (IOException failure) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "No se pudo guardar el nivel PvP; se conserva el diario.",
                            failure);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        credits.clear(event.getPlayer().getUniqueId());
    }
}
