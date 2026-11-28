package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Los items sueltos en la dimension flotan hacia arriba y desaparecen: a los 40 bloques de altura sobre donde
 * estaban, o antes si algo (un techo) los frena. Se llama una vez por tick. No se les saca la gravedad (eso se guarda
 * en el item y quedaria flotando para siempre si el chunk se descarga o el modulo se apaga): cada tick se los empuja
 * hacia arriba un poco mas de lo que la gravedad los baja.
 */
final class ItemFloater implements Runnable {

    /** Bloques por tick. */
    private static final double RISE_SPEED = 0.08;
    /** Lo que la gravedad de un item le resta a su velocidad en cada tick, antes de moverlo. */
    private static final double ITEM_GRAVITY = 0.04;
    private static final double VANISH_AFTER = 40.0;
    /** Ticks seguidos sin subir (topan con un techo) antes de desaparecer. */
    private static final int STUCK_TICKS = 20;

    private static final class Track {
        final Item item;
        final double startY;
        double lastY;
        int stuck;

        Track(Item item, double y) {
            this.item = item;
            startY = y;
            lastY = y;
        }
    }

    private final Map<UUID, Track> tracks = new HashMap<>();

    @Override
    public void run() {
        World world = PortalListener.dimension();
        if (world == null) {
            tracks.clear();
            return;
        }
        Vector up = new Vector(0, RISE_SPEED + ITEM_GRAVITY, 0);
        tracks.values().removeIf(track -> !track.item.isValid());
        for (Item item : world.getEntitiesByClass(Item.class)) {
            Track track = tracks.get(item.getUniqueId());
            double y = item.getLocation().getY();
            if (track == null) {
                track = new Track(item, y);
                tracks.put(item.getUniqueId(), track);
                if (!item.hasGravity()) {
                    item.setGravity(true); // los que dejo sin gravedad una version anterior
                }
            }
            track.stuck = y - track.lastY < RISE_SPEED / 4 ? track.stuck + 1 : 0;
            track.lastY = y;
            if (y - track.startY >= VANISH_AFTER || track.stuck >= STUCK_TICKS) {
                world.spawnParticle(Particle.POOF, item.getLocation(), 6, 0.1, 0.1, 0.1, 0.02);
                tracks.remove(item.getUniqueId());
                item.remove();
                continue;
            }
            item.setVelocity(up);
        }
    }

    /** Al apagar el modulo: los olvida. Nunca se les saco la gravedad, asi que caen solos. */
    void restoreAll() {
        tracks.clear();
    }
}
