package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The white screen of a nuclear blast: for every player in the bomb's world, a text display with a white background
 * far bigger than the screen, held half a block in front of their eyes and seen by them alone. Only its opacity
 * changes ({@link #update}), so the white can rise at once and fade away. Players who come into the world while it
 * lasts get one too; players who leave it lose theirs.
 */
final class NukeWhiteout {
    /** How far in front of the eyes the display hangs; billboards face the camera, so this is its depth. */
    private static final double DISTANCE = 0.6;
    /** A space is about 0.1 x 0.225 blocks per unit of scale: this covers any screen at that distance. */
    private static final float SCALE = 80.0F;

    private final Plugin plugin;
    private final World world;
    private final Map<UUID, TextDisplay> overlays = new HashMap<>();

    NukeWhiteout(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    /** Sets the opacity (0 to 255) for everyone in the world and keeps the displays in front of their eyes. */
    void update(int alpha) {
        Iterator<Map.Entry<UUID, TextDisplay>> iterator = overlays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TextDisplay> entry = iterator.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || !player.getWorld().equals(world) || !entry.getValue().isValid()) {
                entry.getValue().remove();
                iterator.remove();
            }
        }
        for (Player player : world.getPlayers()) {
            if (!overlays.containsKey(player.getUniqueId())) {
                overlays.put(player.getUniqueId(), spawn(player));
            }
        }
        for (Player player : world.getPlayers()) {
            TextDisplay overlay = overlays.get(player.getUniqueId());
            overlay.setBackgroundColor(Color.fromARGB(alpha, 255, 255, 255));
            overlay.teleport(front(player));
        }
    }

    private static Location front(Player player) {
        Location eyes = player.getEyeLocation();
        Vector look = eyes.getDirection().multiply(DISTANCE);
        return eyes.clone().add(look);
    }

    private TextDisplay spawn(Player player) {
        TextDisplay overlay = world.spawn(front(player), TextDisplay.class, display -> {
            // Never shown to anyone by default: only the player it covers sees it.
            display.setVisibleByDefault(false);
            display.setPersistent(false);
            display.text(Component.text(" "));
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0, 255, 255, 255));
            display.setBillboard(Display.Billboard.CENTER);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(2.0F);
            display.setTeleportDuration(1);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f()));
        });
        player.showEntity(plugin, overlay);
        return overlay;
    }

    /** Removes every display. */
    void close() {
        overlays.values().forEach(TextDisplay::remove);
        overlays.clear();
    }
}
