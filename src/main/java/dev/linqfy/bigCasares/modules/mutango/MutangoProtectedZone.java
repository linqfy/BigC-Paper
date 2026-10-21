package dev.linqfy.bigCasares.modules.mutango;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Las zonas protegidas frente a Neguechen: no puede tirarle las manos a alguien que se metio en una (las manos no
 * pueden tocar bloques ahi), asi que le avisa con una cuenta regresiva de 5 segundos para que salga y, si no sale, le
 * cae un mega rayo de Warden. Cada pelea tiene la suya.
 */
final class MutangoProtectedZone {

    /** Cuanto tiene para salir de una zona protegida antes del rayo (5 segundos). */
    private static final int WARNING_TICKS = 100;
    /** Dano del mega rayo de Warden (no lo frena la armadura); solo le pega a quien se quedo en la zona protegida. */
    private static final double MEGA_RAY_DAMAGE = 200.0;
    private static final Particle.DustOptions WARDEN_BLUE = new Particle.DustOptions(Color.fromRGB(40, 220, 200), 1.6f);

    private final Predicate<Block> allowed;
    private final MutangoDifficulty difficulty;
    /** Los que se metieron en una zona protegida: hasta que tick tienen para salir. */
    private final Map<UUID, Integer> warnings = new HashMap<>();

    MutangoProtectedZone(Predicate<Block> allowed, MutangoDifficulty difficulty) {
        this.allowed = allowed;
        this.difficulty = difficulty;
    }

    /** El jugador esta parado en una zona protegida (donde las manos no pueden tocar bloques). */
    boolean contains(Player player) {
        Block feet = player.getLocation().getBlock();
        return !allowed.test(feet) || !allowed.test(feet.getRelative(0, -1, 0));
    }

    /** Le avisa con una cuenta regresiva de 5 segundos (en la pantalla) para que salga; si ya lo avisaron no hace nada. */
    void warn(Player player, int now) {
        if (warnings.containsKey(player.getUniqueId())) {
            return;
        }
        warnings.put(player.getUniqueId(), now + WARNING_TICKS);
        player.sendMessage("§cTenés 5 segundos para salir de la zona protegida, o te cae un rayo.");
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSEST, 1.5f, 0.8f);
        showCountdown(player, WARNING_TICKS / 20);
    }

    /** Un tick de las cuentas regresivas: si salio, se cancela; si se acabo el tiempo, el rayo. Sin jefe se cancelan. */
    void tick(int now, Entity boss) {
        if (warnings.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Integer>> it = warnings.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || boss == null || !MutangoTargets.targetable(player)) {
                it.remove();
                continue;
            }
            int left = entry.getValue() - now;
            if (now % 5 == 0 && !contains(player)) {
                it.remove();
                player.sendActionBar(Component.text("§aSaliste a tiempo"));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
                continue;
            }
            if (left <= 0) {
                it.remove();
                megaRay(player, boss);
            } else if (left % 20 == 0) {
                showCountdown(player, left / 20);
            }
        }
    }

    /** Se cancelan todas las cuentas (termino la pelea o la vencieron). */
    void clear() {
        warnings.clear();
    }

    /** El numero grande en el medio de la pantalla y el aviso completo en la barra de abajo. */
    private void showCountdown(Player player, int seconds) {
        var legacy = LegacyComponentSerializer.legacySection();
        player.showTitle(Title.title(legacy.deserialize("§c§l" + seconds),
            legacy.deserialize("§eSalí de la zona protegida"),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(100))));
        player.sendActionBar(legacy.deserialize("§cTenés §l" + seconds + "§c segundos para salir de la zona protegida"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, 0.6f);
    }

    /**
     * El mega rayo de Warden: una columna de luz azul del cielo al jugador, trueno y estallido sonico. Le hace dano solo a
     * el (no toca bloques ni a nadie alrededor) y no lo frena la armadura.
     */
    private void megaRay(Player player, Entity boss) {
        World world = player.getWorld();
        Location at = player.getLocation();
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_CHARGE, 2f, 0.8f);
        for (double y = 0; y <= 40; y += 0.6) {
            Location point = at.clone().add(0, y, 0);
            world.spawnParticle(Particle.DUST, point, 3, 0.25, 0.1, 0.25, 0, WARDEN_BLUE);
            world.spawnParticle(Particle.END_ROD, point, 1, 0.1, 0.1, 0.1, 0.02);
            if (y % 3 < 0.6) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, point, 4, 0.4, 0.2, 0.4, 0.3);
            }
        }
        world.spawnParticle(Particle.SONIC_BOOM, at.clone().add(0, 1, 0), 1);
        world.strikeLightningEffect(at);
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.9f);
        MutangoDamage.sonicBoom(player, difficulty.damage(MEGA_RAY_DAMAGE), boss);
    }
}
