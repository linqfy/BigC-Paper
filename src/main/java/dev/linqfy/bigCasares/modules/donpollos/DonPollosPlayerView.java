package dev.linqfy.bigCasares.modules.donpollos;

import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Lo que usa API de Paper/Adventure para mostrarle cosas al jugador (fuera del modulo, asi este carga en los tests). */
final class DonPollosPlayerView {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private DonPollosPlayerView() {
    }

    static void lookAtEyes(Mob mob, Player player) {
        Location eyes = player.getEyeLocation();
        mob.lookAt(eyes.getX(), eyes.getY(), eyes.getZ(), LookAnchor.EYES);
    }

    /** Texto abajo en la pantalla (subtitulo) y en el chat. */
    static void showLine(Player player, String subtitle, String chat) {
        player.showTitle(Title.title(Component.empty(), LEGACY.deserialize(subtitle),
            Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500))));
        player.sendMessage(LEGACY.deserialize(chat));
    }
}
