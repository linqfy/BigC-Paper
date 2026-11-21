package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Las balas de Enma como simulacion pura (sin entidades): viajan en linea recta y horizontal, se rompen contra
 * cualquier bloque solido (no atraviesan paredes), al salir de la sala, al tocar a un jugador o por edad. El
 * servidor solo las dibuja con particulas y aplica el dano de {@link Step#hits()}.
 */
public final class EnmaBullets {

    public static final int MAX_BULLETS = 160;
    public static final int MAX_AGE = 200;
    /** Distancia horizontal al centro del jugador que cuenta como impacto. */
    public static final double HIT_RADIUS = 0.65;
    /** Alto del cuerpo del jugador sobre sus pies que cuenta (y un poco por debajo). */
    public static final double HIT_HEIGHT = 1.9;
    public static final double HIT_BELOW = 0.3;

    /** El mundo visto por las balas: {@code true} si el bloque frena una bala. */
    @FunctionalInterface
    public interface Solid {
        boolean solid(int x, int y, int z);
    }

    /** Un jugador como blanco: sus pies. */
    public record Target(UUID id, double x, double y, double z) {
    }

    /** Donde esta una bala (para dibujarla). */
    public record Position(double x, double y, double z, int age) {
    }

    /**
     * @param hits  jugadores tocados en este paso (cada bala pega a uno solo)
     * @param pops  lugares donde una bala se rompio contra un bloque (para el efecto)
     */
    public record Step(List<UUID> hits, List<Position> pops) {
    }

    private final List<Bullet> bullets = new ArrayList<>();
    private final EnmaArena arena;

    public EnmaBullets(EnmaArena arena) {
        this.arena = Objects.requireNonNull(arena, "arena");
    }

    /** Agrega una bala; devuelve {@code false} si ya hay demasiadas o nace adentro de un bloque. */
    public boolean fire(double x, double y, double z, double angle, double speed, Solid solid) {
        if (bullets.size() >= MAX_BULLETS || blocked(solid, x, y, z) || !arena.containsHorizontally(x, z)) {
            return false;
        }
        bullets.add(new Bullet(x, y, z, Math.cos(angle) * speed, Math.sin(angle) * speed));
        return true;
    }

    public Step step(Solid solid, List<Target> targets) {
        List<UUID> hits = new ArrayList<>();
        List<Position> pops = new ArrayList<>();
        Iterator<Bullet> iterator = bullets.iterator();
        while (iterator.hasNext()) {
            Bullet bullet = iterator.next();
            bullet.x += bullet.vx;
            bullet.z += bullet.vz;
            bullet.age++;
            if (bullet.age > MAX_AGE || !arena.containsHorizontally(bullet.x, bullet.z)) {
                iterator.remove();
                continue;
            }
            if (blocked(solid, bullet.x, bullet.y, bullet.z)) {
                pops.add(new Position(bullet.x, bullet.y, bullet.z, bullet.age));
                iterator.remove();
                continue;
            }
            for (Target target : targets) {
                double dx = target.x() - bullet.x;
                double dz = target.z() - bullet.z;
                if (dx * dx + dz * dz <= HIT_RADIUS * HIT_RADIUS
                    && bullet.y >= target.y() - HIT_BELOW && bullet.y <= target.y() + HIT_HEIGHT) {
                    hits.add(target.id());
                    iterator.remove();
                    break;
                }
            }
        }
        return new Step(hits, pops);
    }

    public List<Position> positions() {
        List<Position> list = new ArrayList<>(bullets.size());
        for (Bullet bullet : bullets) {
            list.add(new Position(bullet.x, bullet.y, bullet.z, bullet.age));
        }
        return list;
    }

    public int size() {
        return bullets.size();
    }

    public void clear() {
        bullets.clear();
    }

    private static boolean blocked(Solid solid, double x, double y, double z) {
        return solid.solid((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    private static final class Bullet {
        private double x;
        private final double y;
        private double z;
        private final double vx;
        private final double vz;
        private int age;

        private Bullet(double x, double y, double z, double vx, double vz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vz = vz;
        }
    }
}
