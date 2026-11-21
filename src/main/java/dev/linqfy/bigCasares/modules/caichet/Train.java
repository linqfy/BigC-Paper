package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * El tren: solo la locomotora del modelo Demon Train (resource pack en src/main/resourcepack), mostrada con un
 * ItemDisplay. Corre pegado al suelo, persigue a un jugador (girando de a poco, no teletransporta) y lo choca:
 * poco dano, lo manda muy alto, y la caida hace el resto. Despues de pasar sigue derecho un tramo y desaparece.
 */
final class Train {

    // ---- numeros para ajustar la sensacion ----
    /** Bloques por tick (1.1 = 22 bloques por segundo). */
    static final double SPEED = 1.1;
    /** Cuanto puede girar por tick al perseguir (grados). */
    static final double MAX_TURN_DEGREES = 4.0;
    /** A que distancia del jugador aparece el tren. */
    static final double SPAWN_DISTANCE = 70.0;
    /** Dano del choque en si (3 = un corazon y medio). */
    static final double HIT_DAMAGE = 3.0;
    /** Velocidad vertical al chocar: 1.7 son unos 14 bloques de altura, cuya caida saca ~11 de vida. */
    static final double LAUNCH_UP = 1.7;
    /** Empuje horizontal en la direccion del tren. */
    static final double LAUNCH_FORWARD = 0.5;
    /** Bloques que sigue derecho despues de pasar al objetivo. */
    static final double RUNOUT = 60.0;
    /** Tope de seguridad: si no termina antes, desaparece. */
    static final int MAX_TICKS = 20 * 30;

    /** Cada cuanto se vuelve a emitir el motor (el archivo dura ~22 s y se pide un poco antes de que termine). */
    static final int ENGINE_PERIOD_TICKS = 20 * 20;
    /** El motor solo se manda a quien esta a menos de esto. */
    static final double ENGINE_RADIUS = 100.0;
    /** Cada cuanto se sortea un soplido de vapor, y con que chance. */
    static final int STEAM_EVERY_TICKS = 30;
    static final int STEAM_CHANCE_PERCENT = 50;
    /** Segundo silbato cuando el tren ya esta cerca de su objetivo. */
    private static final double WHISTLE_NEAR = 40.0;

    /** El modelo original mide ~3 bloques de largo; se agranda este factor. */
    static final float MODEL_SCALE = 3.0f;
    /** Cuanto sobresalen las ruedas por debajo del centro del modelo (en bloques, antes de escalar). */
    private static final double MODEL_BELOW_CENTER = 1.215;
    /** Largo y ancho del modelo ya escalado, para el choque. */
    private static final double HALF_LENGTH = 1.48 * MODEL_SCALE;
    private static final double HALF_WIDTH = 0.4 * MODEL_SCALE;
    private static final double HEIGHT = 1.7 * MODEL_SCALE;

    static final NamespacedKey MODEL = new NamespacedKey("demon_train", "demon_train");

    private final World world;
    private final Supplier<Location> target;
    private final ItemDisplay display;
    private final Set<UUID> alreadyHit = new HashSet<>();
    /**
     * Para Bedrock (no ve el display): con las entidades de Geyser, una vagoneta que se ve como la locomotora; si no,
     * cuatro vagones comunes que siguen al tren. En los dos casos, mas humo.
     */
    private final List<Minecart> carts = new ArrayList<>();
    /** Los de Bedrock en el mundo, consultados cada 10 ticks (no cada tick). */
    private final List<Player> bedrockViewers = new ArrayList<>();
    /** Los integrantes del team de cada jugador, arriba de la locomotora (cada uno ve solo a los suyos). */
    private final TrainPassengers passengers;

    private double hx;
    private double hz;
    private double hy;
    private double dx;
    private double dz;
    private int age;
    private boolean passed;
    private double runoutLeft = RUNOUT;
    private boolean finished;
    private boolean whistledNear;

    /**
     * @param target de donde sacar a quien perseguir cada tick; si devuelve null el tren sigue derecho
     */
    Train(World world, double startX, double startZ, double dirX, double dirZ, Supplier<Location> target) {
        this.world = world;
        this.target = target;
        double len = Math.hypot(dirX, dirZ);
        this.dx = dirX / len;
        this.dz = dirZ / len;
        this.hx = startX;
        this.hz = startZ;
        this.hy = groundY(startX, startZ, 64);
        this.passengers = new TrainPassengers(world);
        this.display = spawn();
        updateVisual();
    }

    Location head() {
        return new Location(world, hx, hy, hz);
    }

    /** @return false cuando el tren termino y ya se borro */
    boolean tick() {
        if (finished) {
            return false;
        }
        age++;
        steer();
        hx += dx * SPEED;
        hz += dz * SPEED;
        double wantY = groundY(hx, hz, hy);
        hy += Math.max(-0.6, Math.min(0.6, wantY - hy));

        if (passed) {
            runoutLeft -= SPEED;
        }
        updateVisual();
        if (age % 10 == 2) {
            passengers.refresh(head());
            updateVisual(); // las figuras recien creadas van directo al techo
        }
        checkHits();
        sounds();

        if (age % 6 == 0) {
            world.playSound(head(), Sound.ENTITY_MINECART_RIDING, 4f, 0.5f);
        }
        if (runoutLeft <= 0 || age >= MAX_TICKS) {
            remove();
            return false;
        }
        return true;
    }

    /** El motor se emite al arrancar y despues cada {@link #ENGINE_PERIOD_TICKS}. */
    static boolean engineDue(int age) {
        return age % ENGINE_PERIOD_TICKS == 1;
    }

    /** Vapor a veces: solo en los multiplos de {@link #STEAM_EVERY_TICKS} y si el sorteo (0-99) sale bajo. */
    static boolean steamDue(int age, int roll) {
        return age > 0 && age % STEAM_EVERY_TICKS == 0 && roll < STEAM_CHANCE_PERCENT;
    }

    private void sounds() {
        if (age == 1) {
            whistle();
        }
        if (engineDue(age)) {
            engine();
        }
        if (steamDue(age, ThreadLocalRandom.current().nextInt(100))) {
            Location at = head().add(0, 4, 0);
            CaiChetSounds.play(world, at, CaiChetSounds.TRAIN_STEAM, SoundCategory.NEUTRAL, 3f, 1f);
            world.spawnParticle(Particle.CLOUD, at, 12, 0.6, 0.3, 0.6, 0.03);
        }
    }

    private void whistle() {
        CaiChetSounds.play(world, head(), CaiChetSounds.TRAIN_WHISTLE, SoundCategory.NEUTRAL, 8f, 1f);
    }

    /** El motor sigue al display de la locomotora; Bedrock no lo ve, asi que ahi suena en la posicion del tren. */
    private void engine() {
        Location at = head();
        double limit = ENGINE_RADIUS * ENGINE_RADIUS;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) > limit) {
                continue;
            }
            if (Bedrock.is(player) || !display.isValid()) {
                CaiChetSounds.playTo(player, at, CaiChetSounds.TRAIN_ENGINE, SoundCategory.NEUTRAL, 6f, 1f);
            } else {
                CaiChetSounds.follow(player, display, CaiChetSounds.TRAIN_ENGINE, SoundCategory.NEUTRAL, 6f, 1f);
            }
        }
    }

    void remove() {
        finished = true;
        display.remove();
        passengers.remove();
        carts.forEach(CaiChetBedrockEntities::discard);
        carts.clear();
    }

    /** Pone los vagones de Bedrock donde va el tren y les echa humo. Solo si hay alguien de Bedrock en el mundo. */
    private void updateBedrock(float yaw) {
        if (age % 10 == 1) {
            bedrockViewers.clear();
            bedrockViewers.addAll(Bedrock.in(world));
            if (bedrockViewers.isEmpty() && !carts.isEmpty()) {
                // se fueron todos: los vagones sobran
                carts.forEach(CaiChetBedrockEntities::discard);
                carts.clear();
            }
        }
        bedrockViewers.removeIf(p -> !p.isOnline());
        List<Player> viewers = bedrockViewers;
        if (viewers.isEmpty()) {
            return;
        }
        Plugin plugin = Bedrock.plugin();
        // con el modelo de Geyser basta una vagoneta, que es la locomotora entera; si no, cuatro vagones comunes
        boolean model = CaiChetBedrockEntities.available();
        if (carts.isEmpty()) {
            for (int i = 0; i < (model ? 1 : 4); i++) {
                carts.add(world.spawn(head(), Minecart.class, m -> {
                    m.setVisibleByDefault(false);
                    Bedrock.markStand(m);
                    CaiChetBedrockEntities.present(m, CaiChetBedrockEntities.TRAIN);
                    m.setGravity(false);
                    m.setSilent(true);
                    m.setInvulnerable(true);
                    m.setPersistent(false);
                }));
            }
        }
        for (Player player : viewers) {
            for (Minecart cart : carts) {
                if (!player.canSee(cart)) {
                    player.showEntity(plugin, cart);
                }
            }
            if (player.canSee(display)) {
                player.hideEntity(plugin, display); // el modelo del tren no lo ven en Bedrock
            }
        }
        if (model) {
            // el modelo de la locomotora mira igual que el display y tiene los pies en el suelo
            carts.getFirst().teleport(new Location(world, hx, hy, hz, yaw, 0f));
        } else {
            for (int i = 0; i < carts.size(); i++) {
                double offset = (1.5 - i) * 2.2; // 3,3 / 1,1 / -1,1 / -3,3 bloques desde la cabeza
                carts.get(i).teleport(new Location(world, hx - dx * offset, hy + 0.05, hz - dz * offset, yaw + 90f, 0f));
            }
        }
        if (age % 2 == 0) {
            for (Player player : viewers) {
                player.spawnParticle(Particle.LARGE_SMOKE, new Location(world, hx, hy + 5, hz), 4, 0.4, 0.3, 0.4, 0.02);
                player.spawnParticle(Particle.FLAME, new Location(world, hx, hy + 1.5, hz), 3, 1.2, 0.5, 1.2, 0.01);
            }
        }
    }

    private void steer() {
        if (passed) {
            return;
        }
        Location t = target.get();
        if (t == null) {
            passed = true;
            return;
        }
        double tx = t.getX() - hx;
        double tz = t.getZ() - hz;
        double dist = Math.hypot(tx, tz);
        if (!whistledNear && dist < WHISTLE_NEAR) {
            whistledNear = true;
            whistle();
        }
        if (dist < 0.001) {
            return;
        }
        double ux = tx / dist;
        double uz = tz / dist;
        double dot = dx * ux + dz * uz;
        if (dot < 0) {
            // el objetivo quedo atras: ya paso
            passed = true;
            return;
        }
        double cross = dx * uz - dz * ux;
        double max = Math.toRadians(MAX_TURN_DEGREES);
        double turn = Math.max(-max, Math.min(max, Math.atan2(cross, dot)));
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        double ndx = dx * cos - dz * sin;
        double ndz = dx * sin + dz * cos;
        double nl = Math.hypot(ndx, ndz);
        dx = ndx / nl;
        dz = ndz / nl;
    }

    private void updateVisual() {
        if (!display.isValid()) {
            return;
        }
        // el frente del modelo mira hacia -Z; el yaw de Bukkit 0 mira hacia +Z, por eso el +180
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + 180f;
        display.teleport(new Location(world, hx, hy, hz, yaw, 0f));
        passengers.update(hx, hy, hz, dx, dz, yaw - 180f); // el modelo mira al reves del avance
        updateBedrock(yaw);
    }

    private void checkHits() {
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || alreadyHit.contains(player.getUniqueId())) {
                continue;
            }
            Location pl = player.getLocation();
            double relX = pl.getX() - hx;
            double relZ = pl.getZ() - hz;
            double along = relX * dx + relZ * dz;
            double side = relX * dz - relZ * dx;
            if (Math.abs(along) < HALF_LENGTH + 0.3 && Math.abs(side) < HALF_WIDTH + 0.4
                    && pl.getY() > hy - 1.0 && pl.getY() < hy + HEIGHT) {
                hit(player);
            }
        }
    }

    private void hit(Player player) {
        alreadyHit.add(player.getUniqueId());
        CaiChetRuntime.logger().info("[CaiChet] Tren: choco a " + player.getName());
        player.damage(HIT_DAMAGE);
        player.setVelocity(new Vector(dx * LAUNCH_FORWARD, LAUNCH_UP, dz * LAUNCH_FORWARD));
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.8f);
    }

    private double groundY(double x, double z, double fallback) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return fallback;
        }
        return world.getHighestBlockYAt(bx, bz) + 1;
    }

    private ItemDisplay spawn() {
        ItemStack stack = new ItemStack(Material.STICK);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(MODEL);
        stack.setItemMeta(meta);
        Location at = new Location(world, hx, hy, hz);
        return world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, (float) (MODEL_BELOW_CENTER * MODEL_SCALE + 0.05), 0f),
                    new AxisAngle4f(),
                    new Vector3f(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE),
                    new AxisAngle4f()));
            d.setTeleportDuration(2);
            d.setViewRange(3f);
            d.setDisplayWidth(10f);
            d.setDisplayHeight(10f);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
        });
    }
}
