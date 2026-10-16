package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El santuario en el medio de las ruinas: Lef, Lihuen y Lican como estatuas con los ojos cerrados y, delante, un cubo
 * de piedra tapando un agujero. Si haces click en el cubo:
 * <ol>
 *   <li>se corre un bloque hacia la izquierda (mirando a las estatuas), frenando, y deja ver el agujero;</li>
 *   <li>del agujero sube una esferita de particulas, se queda flotando y se divide en tres;</li>
 *   <li>las tres van hacia las estatuas; cuando las tocan el cubo vuelve a su lugar, las estatuas cobran vida y arranca
 *   la pelea (fase 1).</li>
 * </ol>
 * Las piezas (cubo y estatuas) son entidades con modelo; se reconocen por la marca "santuario" (id del santuario y
 * su papel), asi siguen funcionando despues de reiniciar el servidor.
 */
final class MutangoShrine {

    static final String CUBE = "bigcasares_ruinas_bloque";
    static final List<String> STATUES = MutangoModels.MODELS.stream().map(model -> model + "_dormido").toList();

    // tiempos de la secuencia (ticks desde el click)
    private static final int SLIDE_TICKS = MutangoShrineTimeline.SLIDE_TICKS;
    private static final int SPLIT_AT = MutangoShrineTimeline.SPLIT_AT;
    private static final int ARRIVE_AT = MutangoShrineTimeline.ARRIVE_AT;
    private static final int CLOSE_END = MutangoShrineTimeline.CLOSE_END;
    /** Cuanto sube la esferita desde el fondo del agujero antes de dividirse. */
    private static final double RISE_HEIGHT = 3.0;
    private static final double STATUE_SPACING = 2.0;
    private static final double STATUE_DISTANCE = 3.0;

    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 205, 80), 1.2f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(255, 250, 235), 1.0f);

    interface Host {
        /** Spawnea una pieza del santuario: entidad con modelo, quieta, sin IA, sin gravedad e invulnerable. */
        Mob spawnPart(String model, Location location);

        /**
         * Alguien corrio el cubo: empieza una pelea nueva (con la dificultad de ese jugador) y devuelve su id. Cada
         * santuario despierta su propia pelea, no depende de ninguna otra.
         */
        UUID beginAwakening(Player summoner, Location closed);

        /** Las estatuas cobraron vida: pone a Lef, Lihuen y Lican de esa pelea en esos lugares y arranca la fase 1. */
        void awaken(UUID fightId, List<Location> spots);

        /** Esa pelea sigue esperando a sus Mutangos (no la sacaron mientras el santuario los despertaba). */
        boolean isWaiting(UUID fightId);

        /** El despertar se corto (el cubo desaparecio): esa pelea ya no espera a nadie. */
        void cancelAwakening(UUID fightId);
    }

    /** Una secuencia de despertar en curso. */
    private static final class Awakening {
        final UUID cube;
        final List<UUID> statues;
        final Location closed;
        final Location open;
        final Location hole;
        /** La pelea que va a salir de este despertar. */
        UUID fight;
        final List<Location> targets = new ArrayList<>();
        int ticks;

        Awakening(UUID cube, List<UUID> statues, Location closed, Location open, Location hole) {
            this.cube = cube;
            this.statues = statues;
            this.closed = closed;
            this.open = open;
            this.hole = hole;
        }
    }

    private final Host host;
    private final NamespacedKey key;
    private final NamespacedKey leftKey;
    /** En el cubo, mientras esta abierto: donde estaba cerrado (x,y,z,giro), para poder volver a cerrarlo. */
    private final NamespacedKey closedKey;
    private final Map<UUID, Awakening> running = new HashMap<>();

    MutangoShrine(Host host, NamespacedKey key, NamespacedKey leftKey, NamespacedKey closedKey) {
        this.host = host;
        this.key = key;
        this.leftKey = leftKey;
        this.closedKey = closedKey;
    }

    boolean isPart(Entity entity) {
        return entity.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    boolean isCube(Entity entity) {
        String tag = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return tag != null && tag.endsWith("|bloque");
    }

    // ------------------------------------------------------------ armado

    /**
     * Arma el santuario sobre el agujero hole (un bloque del piso que queda vacio). left: hacia donde se corre el cubo
     * (la izquierda de quien mira a las estatuas); front: hacia donde miran las estatuas (hacia el jugador que llega).
     */
    void place(Block hole, Vector left, Vector front) {
        World world = hole.getWorld();
        Vector back = front.clone().multiply(-1);
        // lugar libre arriba del piso para el cubo y las estatuas, y piso firme donde se paran
        for (int a = -4; a <= 4; a++) {
            for (int b = -2; b <= (int) STATUE_DISTANCE + 2; b++) {
                Block floor = relative(hole, left, back, a, b, 0);
                if (!(a == 0 && b == 0) && !floor.getType().isSolid()) {
                    floor.setType(Material.STONE_BRICKS, false);
                }
                for (int h = 1; h <= 3; h++) {
                    relative(hole, left, back, a, b, h).setType(Material.AIR, false);
                }
            }
        }
        // el agujero: vacio, con el fondo oscuro
        hole.setType(Material.AIR, false);
        Block bottom = hole.getRelative(0, -1, 0);
        if (bottom.getType().isAir()) {
            bottom.setType(Material.POLISHED_BLACKSTONE, false);
        }

        String id = UUID.randomUUID().toString();
        float yaw = MutangoTargets.yaw(front);
        Location cubeAt = hole.getLocation().add(0.5, 1, 0.5);
        cubeAt.setYaw(yaw);
        Mob cube = host.spawnPart(CUBE, cubeAt);
        PersistentDataContainer data = cube.getPersistentDataContainer();
        data.set(key, PersistentDataType.STRING, id + "|bloque");
        data.set(leftKey, PersistentDataType.STRING, left.getBlockX() + "," + left.getBlockZ());
        // las estatuas: Lef a la izquierda, Lihuen en el medio y Lican a la derecha (mirando hacia ellas)
        for (int i = 0; i < STATUES.size(); i++) {
            double side = (1 - i) * STATUE_SPACING;
            Location at = hole.getLocation().add(0.5, 1, 0.5).add(left.clone().multiply(side))
                .add(back.clone().multiply(STATUE_DISTANCE));
            at.setYaw(yaw);
            Mob statue = host.spawnPart(STATUES.get(i), at);
            statue.getPersistentDataContainer().set(key, PersistentDataType.STRING, id + "|estatua|" + i);
        }
    }

    private static Block relative(Block origin, Vector left, Vector back, int a, int b, int h) {
        return origin.getRelative(left.getBlockX() * a + back.getBlockX() * b, h,
            left.getBlockZ() * a + back.getBlockZ() * b);
    }

    // ------------------------------------------------------------ el click

    /** Click en el cubo: si al lado estan las estatuas dormidas, arranca el despertar. */
    void click(Player player, Entity cube) {
        if (running.containsKey(cube.getUniqueId())) {
            return;
        }
        // si un despertar anterior se corto y el cubo quedo abierto, vuelve a su lugar antes de empezar de nuevo
        closeIfInterrupted(cube);
        String tag = cube.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        String left = cube.getPersistentDataContainer().get(leftKey, PersistentDataType.STRING);
        if (tag == null || left == null) {
            return;
        }
        String id = tag.substring(0, tag.indexOf('|'));
        List<Entity> statues = new ArrayList<>();
        for (Entity near : cube.getNearbyEntities(10, 5, 10)) {
            String other = near.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (other != null && other.startsWith(id + "|estatua|")) {
                statues.add(near);
            }
        }
        if (statues.size() < STATUES.size()) {
            // ya despertaron: el cubo no se mueve
            cube.getWorld().playSound(cube.getLocation(), Sound.BLOCK_STONE_HIT, 1f, 0.6f);
            return;
        }
        statues.sort(Comparator.comparingInt(statue -> Integer.parseInt(statue.getPersistentDataContainer()
            .get(key, PersistentDataType.STRING).substring(id.length() + "|estatua|".length()))));
        String[] xz = left.split(",");
        Vector slide = new Vector(Integer.parseInt(xz[0]), 0, Integer.parseInt(xz[1]));
        Location closed = cube.getLocation();
        Location hole = closed.clone().add(0, -1, 0);
        Awakening awakening = new Awakening(cube.getUniqueId(),
            statues.stream().map(Entity::getUniqueId).toList(), closed, closed.clone().add(slide), hole);
        cube.getPersistentDataContainer().set(closedKey, PersistentDataType.STRING, closed.getX() + "," + closed.getY()
            + "," + closed.getZ() + "," + closed.getYaw());
        awakening.fight = host.beginAwakening(player, closed);
        running.put(cube.getUniqueId(), awakening);
        World world = cube.getWorld();
        world.playSound(closed, Sound.BLOCK_GRINDSTONE_USE, 1.5f, 0.5f);
        world.playSound(closed, Sound.BLOCK_PISTON_EXTEND, 1.0f, 0.5f);
    }

    /**
     * Si el despertar se corto antes de que las estatuas cobraran vida (se descargo el chunk, se cayo el servidor...), el
     * cubo puede haber quedado corrido, con el agujero a la vista. Lo vuelve a su lugar cerrado y se puede volver a
     * abrir. Se llama al cargar el cubo y al clickearlo. Devuelve true si lo cerro.
     */
    boolean closeIfInterrupted(Entity cube) {
        if (running.containsKey(cube.getUniqueId())) {
            return false; // se esta abriendo ahora mismo
        }
        String saved = cube.getPersistentDataContainer().get(closedKey, PersistentDataType.STRING);
        if (saved == null) {
            return false;
        }
        cube.getPersistentDataContainer().remove(closedKey);
        try {
            String[] parts = saved.split(",");
            Location closed = new Location(cube.getWorld(), Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]), Float.parseFloat(parts[3]), 0f);
            cube.teleport(closed);
            cube.getWorld().playSound(closed, Sound.BLOCK_STONE_PLACE, 1.0f, 0.5f);
            return true;
        } catch (RuntimeException invalid) {
            return false; // una marca que no se entiende: se deja el cubo donde esta
        }
    }

    // ------------------------------------------------------------ la secuencia

    void tick() {
        for (Awakening run : new ArrayList<>(running.values())) {
            run.ticks++;
            if (!(Bukkit.getEntity(run.cube) instanceof Mob cube) || !cube.isValid()) {
                running.remove(run.cube);
                host.cancelAwakening(run.fight);
                continue;
            }
            step(run, cube);
        }
    }

    private void step(Awakening run, Mob cube) {
        int t = run.ticks;
        World world = run.closed.getWorld();
        Location bottom = run.hole.clone().add(0, 0.2, 0);
        Location top = run.hole.clone().add(0, RISE_HEIGHT, 0);

        // 1) el cubo se corre un bloque, frenando
        if (t <= SLIDE_TICKS) {
            double s = MutangoShrineTimeline.slide(t);
            cube.teleport(lerp(run.closed, run.open, s));
            if (t % 4 == 0) {
                world.spawnParticle(Particle.BLOCK, run.closed, 4, 0.4, 0.05, 0.4, 0,
                    Material.STONE.createBlockData());
            }
            if (t == SLIDE_TICKS) {
                world.playSound(run.open, Sound.BLOCK_STONE_PLACE, 1.5f, 0.6f);
                world.playSound(run.hole, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f);
            }
        }
        // 2) del agujero sube una esferita de particulas y queda flotando
        if (t > SLIDE_TICKS && t <= SPLIT_AT) {
            Location center = lerp(bottom, top, MutangoShrineTimeline.rise(t));
            sphere(world, center, 0.45, t);
            if (t % 10 == 0) {
                world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.2f);
            }
        }
        // 3) se divide en tres y cada una va hacia una estatua
        if (t == SPLIT_AT) {
            run.targets.clear();
            for (UUID id : run.statues) {
                Entity statue = Bukkit.getEntity(id);
                run.targets.add(statue == null ? top.clone() : statue.getLocation().add(0, 1.0, 0));
            }
            world.playSound(top, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.5f, 0.8f);
            world.spawnParticle(Particle.END_ROD, top, 25, 0.2, 0.2, 0.2, 0.08);
        }
        if (t > SPLIT_AT && t <= ARRIVE_AT) {
            double s = MutangoShrineTimeline.travel(t);
            for (Location target : run.targets) {
                Location center = lerp(top, target, s).add(0, Math.sin(Math.PI * s) * 1.2, 0);
                sphere(world, center, 0.25, t);
            }
        }
        // 4) las toca: las estatuas brillan y el cubo se vuelve a cerrar
        if (t == ARRIVE_AT) {
            for (Location target : run.targets) {
                world.spawnParticle(Particle.END_ROD, target, 30, 0.4, 0.6, 0.4, 0.08);
                world.spawnParticle(Particle.DUST, target, 30, 0.5, 0.7, 0.5, 0, GOLD);
                world.playSound(target, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.3f);
            }
            world.playSound(run.open, Sound.BLOCK_PISTON_CONTRACT, 1.0f, 0.5f);
        }
        if (t > ARRIVE_AT && t <= CLOSE_END) {
            double s = MutangoShrineTimeline.close(t);
            cube.teleport(lerp(run.open, run.closed, s));
        }
        // 5) cobran vida: arranca la pelea
        if (t == CLOSE_END) {
            cube.getPersistentDataContainer().remove(closedKey);
            if (!host.isWaiting(run.fight)) {
                // sacaron la pelea (/mutango quitar) mientras despertaba: las estatuas quedan y se puede volver a abrir
                running.remove(run.cube);
                return;
            }
            world.playSound(run.closed, Sound.BLOCK_STONE_PLACE, 1.5f, 0.5f);
            List<Location> spots = new ArrayList<>();
            for (UUID id : run.statues) {
                Entity statue = Bukkit.getEntity(id);
                if (statue != null) {
                    spots.add(statue.getLocation());
                    world.spawnParticle(Particle.BLOCK, statue.getLocation().add(0, 0.8, 0), 40, 0.4, 0.6, 0.4, 0,
                        Material.STONE.createBlockData());
                    statue.remove();
                }
            }
            world.playSound(run.closed, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.2f, 0.6f);
            running.remove(run.cube);
            if (spots.size() == STATUES.size()) {
                host.awaken(run.fight, spots);
            } else {
                host.cancelAwakening(run.fight);
            }
        }
    }

    /** Una esferita de puntos dorados y blancos que gira. */
    private static void sphere(World world, Location center, double radius, int t) {
        int points = radius > 0.3 ? 14 : 7;
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - 2 * (i + 0.5) / points;
            double ring = Math.sqrt(1 - y * y);
            double angle = golden * i + t * 0.25;
            Location point = center.clone().add(Math.cos(angle) * ring * radius, y * radius,
                Math.sin(angle) * ring * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, i % 2 == 0 ? GOLD : WHITE);
        }
        world.spawnParticle(Particle.END_ROD, center, 1, 0.05, 0.05, 0.05, 0.005);
    }

    private static Location lerp(Location from, Location to, double s) {
        Location point = from.clone().add(to.clone().subtract(from).toVector().multiply(s));
        point.setYaw(from.getYaw());
        return point;
    }
}
