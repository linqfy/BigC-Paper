package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The vessels in the world: an invisible marker stand carrying the brass jar model (Java) and a floating name with
 * the vessel state (both editions). The world is never changed: Bedrock players, who cannot see the model, get a
 * client-side decorated pot block in the vessel spot ({@link Player#sendBlockChange}), re-sent every few seconds and
 * set back to the real block when the fight ends.
 */
final class IblisVesselProps {

    static final String MODEL = "bigcasares_myth_iblis_vessel";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final BlockData POT = Material.DECORATED_POT.createBlockData();

    private static final class Prop {
        private final Location spot;
        private final ArmorStand anchor;
        private final ArmorStand label;
        private final JavaModelHandle model;
        private String loop = "idle";
        private String text = "";

        private Prop(Location spot, ArmorStand anchor, ArmorStand label, JavaModelHandle model) {
            this.spot = spot;
            this.anchor = anchor;
            this.label = label;
            this.model = model;
        }
    }

    private final MythicBossSupport support;
    private final UUID bossId;
    private final List<Prop> props = new ArrayList<>();
    private final Set<UUID> potViewers = new HashSet<>();
    private boolean warnedModel;

    IblisVesselProps(MythicBossSupport support, UUID bossId) {
        this.support = support;
        this.bossId = bossId;
    }

    /** {@code spots} are the block positions (the air block over the floor) of each vessel. */
    void spawn(List<Location> spots) {
        for (Location spot : spots) {
            Location at = spot.clone().add(0.5, 0.0, 0.5);
            ArmorStand anchor = support.spawnOwned(bossId, at, ArmorStand.class, IblisVesselProps::marker);
            ArmorStand label = support.spawnOwned(bossId, at.clone().add(0.0, 1.7, 0.0), ArmorStand.class, stand -> {
                marker(stand);
                stand.setCustomNameVisible(true);
            });
            JavaModelHandle model = null;
            try {
                model = support.models().attach(anchor, MODEL);
                support.models().animate(model, "idle");
            } catch (RuntimeException | LinkageError missing) {
                if (!warnedModel) {
                    warnedModel = true;
                    support.logger().log(Level.WARNING, "[Iblis] Sin modelo " + MODEL
                        + ": las vasijas se ven solo como particulas y nombre en Java.", missing);
                }
            }
            props.add(new Prop(spot.clone(), anchor, label, model));
        }
    }

    int size() {
        return props.size();
    }

    /** Centre of the vessel at floor level. */
    Location center(int vessel) {
        return props.get(vessel).spot.clone().add(0.5, 0.0, 0.5);
    }

    void loop(int vessel, String animation) {
        Prop prop = props.get(vessel);
        if (prop.model == null || prop.loop.equals(animation)) {
            return;
        }
        try {
            if (!prop.loop.equals("idle")) {
                support.models().stopAnimation(prop.model, prop.loop);
            }
            support.models().animate(prop.model, animation);
        } catch (RuntimeException ignored) {
            // cosmetico
        }
        prop.loop = animation;
    }

    void label(int vessel, String text) {
        Prop prop = props.get(vessel);
        if (text.equals(prop.text) || !prop.label.isValid()) {
            return;
        }
        prop.text = text;
        prop.label.customName(LEGACY.deserialize(text));
    }

    /** Sends the stand-in pot of every vessel to these Bedrock players (only over air, never over a real block). */
    void showBedrock(List<Player> bedrock) {
        for (Player player : bedrock) {
            for (Prop prop : props) {
                if (prop.spot.getWorld().equals(player.getWorld()) && prop.spot.getBlock().getType().isAir()) {
                    player.sendBlockChange(prop.spot, POT);
                }
            }
            potViewers.add(player.getUniqueId());
        }
    }

    /** Closes the models, removes the stands and gives the Bedrock viewers their real blocks back; idempotent. */
    void close(JavaPlugin plugin) {
        for (Prop prop : props) {
            if (prop.model != null) {
                try {
                    support.models().close(prop.model);
                } catch (RuntimeException ignored) {
                    // cosmetico
                }
            }
            if (prop.anchor.isValid()) {
                prop.anchor.remove();
            }
            if (prop.label.isValid()) {
                prop.label.remove();
            }
        }
        for (UUID id : potViewers) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null) {
                continue; // the fake block lives only in that client and is gone after a reconnect
            }
            for (Prop prop : props) {
                if (prop.spot.getWorld().equals(player.getWorld())) {
                    player.sendBlockChange(prop.spot, prop.spot.getBlock().getBlockData());
                }
            }
        }
        potViewers.clear();
        props.clear();
    }

    private static void marker(ArmorStand stand) {
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setPersistent(false);
    }
}
