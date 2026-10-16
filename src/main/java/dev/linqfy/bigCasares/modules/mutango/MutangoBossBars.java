package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Una barra de boss por Mutango (cada uno tiene su propia vida), visible para los jugadores cercanos. */
final class MutangoBossBars {

    /** Quien es quien: el modelo de 1, 2 y 3 ojos, su nombre y el color de su barra. */
    record Identity(String model, String name, BarColor color) {
        String title() {
            String code = switch (color) {
                case RED -> "§c";
                case YELLOW -> "§e";
                case PURPLE -> "§d";
                case BLUE -> "§9";
                case GREEN -> "§a";
                case PINK -> "§d";
                case WHITE -> "§f";
            };
            return code + "§l" + name;
        }
    }

    static final Identity LEF = new Identity("bigcasares_boss_mutango", "Lef", BarColor.RED);
    static final Identity LIHUEN = new Identity("bigcasares_boss_mutango_2ojos", "Lihuen", BarColor.YELLOW);
    static final Identity LICAN = new Identity("bigcasares_boss_mutango_3ojos", "Lican", BarColor.PURPLE);
    static final Identity NEGUECHEN = new Identity(MutangoPhaseThree.NEGUECHEN, "Neguechen", BarColor.WHITE);

    static Identity of(String model) {
        for (Identity identity : new Identity[] {LEF, LIHUEN, LICAN, NEGUECHEN}) {
            if (identity.model().equals(model)) {
                return identity;
            }
        }
        return null;
    }

    private static final double VIEW_RADIUS = 64.0;

    /** La barra y quienes la estan viendo (se lleva la cuenta aca: BossBar.getPlayers() arma una copia en cada llamada). */
    private record Entry(BossBar bar, Set<UUID> viewers) {
    }

    private final Map<UUID, Entry> bars = new HashMap<>();

    void show(LivingEntity mutango, Identity identity) {
        bars.computeIfAbsent(mutango.getUniqueId(),
            id -> new Entry(Bukkit.createBossBar(identity.title(), identity.color(), BarStyle.SEGMENTED_10),
                new HashSet<>()));
        update(mutango, false);
    }

    /** Vida actual (vacia si esta caido) y quienes la ven (los jugadores a menos de 64 bloques). */
    void update(LivingEntity mutango, boolean downed) {
        Entry entry = bars.get(mutango.getUniqueId());
        if (entry == null) {
            return;
        }
        BossBar bar = entry.bar();
        Set<UUID> viewers = entry.viewers();
        var maxHealth = mutango.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        bar.setProgress(downed ? 0.0 : Math.max(0.0, Math.min(1.0, mutango.getHealth() / max)));
        // los que se desconectaron ya no estan en la barra (el servidor los saco): se los olvida
        viewers.removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean near = player.getWorld() == mutango.getWorld()
                && player.getLocation().distanceSquared(mutango.getLocation()) <= VIEW_RADIUS * VIEW_RADIUS;
            if (near && viewers.add(player.getUniqueId())) {
                bar.addPlayer(player);
            } else if (!near && viewers.remove(player.getUniqueId())) {
                bar.removePlayer(player);
            }
        }
    }

    void remove(UUID id) {
        Entry entry = bars.remove(id);
        if (entry != null) {
            entry.bar().removeAll();
        }
    }

    void removeAll() {
        bars.values().forEach(entry -> entry.bar().removeAll());
        bars.clear();
    }
}
