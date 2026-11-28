package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Las figuras del hotel: una foto recortada (plana, siempre mirando al jugador) que aparece lejos, en un lugar que
 * se ve.
 * <ul>
 *   <li>Si te acercas demasiado antes de tiempo, desaparece.</li>
 *   <li>Si la miras mas de 5 segundos, empieza a correr hacia vos (atraviesa paredes). Si te alcanza, desaparece
 *       con un susto.</li>
 *   <li>Si le pega el rayo del Arma de Fuego, muere y te da una llave.</li>
 * </ul>
 * Corre una vez por tick.
 */
final class Watchers implements Runnable {

    /** A que distancia aparecen (lo bastante cerca para verse, nunca encima). */
    private static final double MIN_DISTANCE = 18;
    private static final double MAX_DISTANCE = 34;
    /** Si el jugador se acerca a menos de esto (antes de que corra), se va. */
    private static final double TOO_CLOSE = 9;
    /** Cada cuanto se sortea una aparicion por jugador, y con que chance. */
    private static final int SPAWN_EVERY_TICKS = 20 * 8;
    private static final int SPAWN_CHANCE_PERCENT = 35;
    /** Mirandola este tiempo, sale corriendo hacia vos. */
    private static final int SEEN_BEFORE_CHASE_TICKS = 20 * 5;
    /** Bloques por tick mientras corre (0,6 = 12 bloques por segundo). */
    private static final double CHASE_SPEED = 0.6;
    /** A esta distancia te "alcanza". */
    private static final double CAUGHT = 1.3;
    /** Si nadie la ve (y no esta corriendo), se va despues de esto. */
    private static final int MAX_AGE_TICKS = 20 * 60;
    /** Angulo de vista (desde el centro de la pantalla) dentro del cual cuenta como "la estas viendo". */
    private static final double SEEN_ANGLE_DEGREES = 35;
    /** Alto de la figura (bloques). Si el techo es mas bajo, se achica para no atravesarlo. */
    private static final float SIZE = 3.5f;

    private static final List<String> FIGURES = List.of("watcher_moyan", "watcher_estevo");
    private static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(140, 0, 0), 2.0f);

    private static final class Watcher {
        final ItemDisplay display;
        final UUID player;
        final float size;
        final CaiChetBedrockEntities.Entry bedrockEntry;
        int seen;
        int age;
        boolean chasing;
        /** Para Bedrock: un golem quieto que hace de figura (el sprite plano no se ve). */
        IronGolem stand;

        void remove() {
            display.remove();
            CaiChetBedrockEntities.discard(stand);
        }

        Watcher(ItemDisplay display, UUID player, float size, CaiChetBedrockEntities.Entry bedrockEntry) {
            this.display = display;
            this.player = player;
            this.size = size;
            this.bedrockEntry = bedrockEntry;
        }
    }

    private final Map<UUID, Watcher> byPlayer = new HashMap<>();
    private int tick;

    @Override
    public void run() {
        tick++;
        World hotel = HotelWorld.get();
        for (Iterator<Watcher> it = byPlayer.values().iterator(); it.hasNext(); ) {
            Watcher w = it.next();
            Player p = Bukkit.getPlayer(w.player);
            if (p == null || hotel == null || !p.getWorld().equals(hotel) || !w.display.isValid()) {
                w.remove();
                it.remove();
                continue;
            }
            if (step(w, p)) {
                it.remove();
            } else if (w.stand != null) {
                // el golem sigue a la figura y la mira
                Location at = w.display.getLocation();
                Vector to = p.getLocation().toVector().subtract(at.toVector());
                w.stand.teleport(at.setDirection(to.setY(0)));
            }
        }
        if (hotel == null || tick % SPAWN_EVERY_TICKS != 0) {
            return;
        }
        for (Player player : hotel.getPlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR && !byPlayer.containsKey(player.getUniqueId())
                    && ThreadLocalRandom.current().nextInt(100) < SPAWN_CHANCE_PERCENT) {
                spawnFor(player);
            }
        }
    }

    /** @return true si la figura termino (hay que sacarla de la lista) */
    private boolean step(Watcher w, Player p) {
        w.age++;
        Location at = w.display.getLocation();
        double distance = horizontal(p.getLocation(), at);
        if (w.chasing) {
            if (distance <= CAUGHT) {
                scare(w, p);
                return true;
            }
            double step = Math.min(CHASE_SPEED, distance);
            double dx = (p.getX() - at.getX()) / distance * step;
            double dz = (p.getZ() - at.getZ()) / distance * step;
            w.display.teleport(at.add(dx, p.getY() - at.getY(), dz));
            return false;
        }
        if (distance < TOO_CLOSE) {
            vanish(w, "te acercaste");
            return true;
        }
        if (sees(p, at)) {
            w.seen++;
            if (w.seen >= SEEN_BEFORE_CHASE_TICKS) {
                w.chasing = true;
                p.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.4f);
                CaiChetSounds.playTo(p, at, CaiChetSounds.FIGURE_CHASE, SoundCategory.HOSTILE, 2.5f, 1f);
            }
        }
        if (w.age >= MAX_AGE_TICKS) {
            vanish(w, "nadie la vio");
            return true;
        }
        return false;
    }

    /** Si este jugador ya tiene una figura (las entidades de los backrooms no aparecen en ese caso). */
    boolean has(UUID player) {
        return byPlayer.containsKey(player);
    }

    void removeAll() {
        byPlayer.values().forEach(w -> w.remove());
        byPlayer.clear();
    }

    /**
     * El rayo del Arma de Fuego: mata a las figuras que esten cerca de la linea y por cada una le da una llave a
     * quien disparo.
     */
    int killAlong(Player shooter, Vector from, Vector to, double radius) {
        int killed = 0;
        List<UUID> dead = new ArrayList<>();
        for (Map.Entry<UUID, Watcher> entry : byPlayer.entrySet()) {
            Watcher w = entry.getValue();
            if (!w.display.isValid() || !w.display.getWorld().equals(shooter.getWorld())) {
                continue;
            }
            Vector center = w.display.getLocation().toVector().add(new Vector(0, w.size / 2, 0));
            if (distanceToSegment(center, from, to) > radius) {
                continue;
            }
            Location at = w.display.getLocation().add(0, w.size / 2, 0);
            World world = at.getWorld();
            world.spawnParticle(Particle.DUST, at, 60, 0.3, 0.8, 0.3, 0, BLOOD, true);
            world.spawnParticle(Particle.LARGE_SMOKE, at, 15, 0.2, 0.6, 0.2, 0.02, null, true);
            world.playSound(at, Sound.ENTITY_VEX_DEATH, 1.5f, 0.5f);
            w.remove();
            dead.add(entry.getKey());
            for (ItemStack rest : shooter.getInventory().addItem(HotelLoot.key()).values()) {
                world.dropItem(shooter.getLocation(), rest);
            }
            killed++;
        }
        dead.forEach(byPlayer::remove);
        return killed;
    }

    private boolean spawnFor(Player player) {
        Location spot = findSpot(player);
        if (spot == null) {
            return false;
        }
        spawnAt(player, spot);
        return true;
    }

    private void spawnAt(Player player, Location spot) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String figure = FIGURES.get(random.nextInt(FIGURES.size()));
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("caichet", figure));
        stack.setItemMeta(meta);
        float size = Math.min(SIZE, freeHeight(spot) - 0.15f);
        ItemDisplay display = spot.getWorld().spawn(spot, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, size / 2f, 0f), new AxisAngle4f(),
                    new Vector3f(size, size, size), new AxisAngle4f()));
            d.setTeleportDuration(2);
            d.setViewRange(2f);
            d.setPersistent(false);
        });
        Watcher watcher = new Watcher(display, player.getUniqueId(), size,
                CaiChetBedrockEntities.watcher(figure));
        if (Bedrock.is(player)) {
            Plugin plugin = Bedrock.plugin();
            player.hideEntity(plugin, display);
            watcher.stand = spot.getWorld().spawn(spot, IronGolem.class, e -> {
                e.setAI(false);
                e.setSilent(true);
                e.setGravity(false);
                e.setInvulnerable(true);
                e.setPersistent(false);
                e.setRemoveWhenFarAway(false);
                e.setVisibleByDefault(false);
                Bedrock.markStand(e);
                CaiChetBedrockEntities.present(e, watcher.bedrockEntry);
                AttributeInstance scale = e.getAttribute(Attribute.SCALE);
                if (scale != null) {
                    // el cartel de Bedrock mide 2,7 bloques a escala 1: con el, la escala es el tamaño tal cual
                    scale.setBaseValue(CaiChetBedrockEntities.available() ? size / 2.7 : Math.max(0.8, size / 2.7));
                }
            });
            player.showEntity(plugin, watcher.stand);
        }
        byPlayer.put(player.getUniqueId(), watcher);
        // respiro o fantasma: las dos variantes estan en el mismo evento y Minecraft sortea una
        CaiChetSounds.playTo(player, spot, CaiChetSounds.FIGURE_APPEAR, SoundCategory.HOSTILE, 2f, 1f);
    }

    /** Te alcanzo: grito, un instante de oscuridad, y desaparece. */
    private void scare(Watcher w, Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1.5f, 0.5f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0, false, false, false));
        w.remove();
    }

    private void vanish(Watcher w, String reason) {
        w.remove();
    }

    /** Un lugar lejos, mas o menos adelante del jugador, con piso, lugar para estar parado y que se vea. */
    private Location findSpot(Player player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        double yaw = Math.toRadians(eye.getYaw());
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = yaw + Math.toRadians(random.nextDouble(-60, 60));
            double distance = random.nextDouble(MIN_DISTANCE, MAX_DISTANCE);
            int x = (int) Math.floor(eye.getX() - Math.sin(angle) * distance);
            int z = (int) Math.floor(eye.getZ() + Math.cos(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = HotelLayout.FLOOR + 1;
            Block feet = world.getBlockAt(x, y, z);
            if (!world.getBlockAt(x, y - 1, z).getType().isSolid() || !standable(feet)
                    || !standable(feet.getRelative(0, 1, 0))) {
                continue;
            }
            Location spot = new Location(world, x + 0.5, y, z + 0.5);
            if (clearView(eye, spot.clone().add(0, 1.6, 0))) {
                return spot;
            }
        }
        return null;
    }

    /** Bloques libres desde los pies hasta el techo (como mucho 6). */
    private static float freeHeight(Location feet) {
        return Math.max(2, freeHeight(feet, 6));
    }

    /** Bloques libres desde los pies hasta el techo, contando como mucho max (sin minimo). */
    static int freeHeight(Location feet, int max) {
        Block block = feet.getBlock();
        int free = 0;
        while (free < max && standable(block.getRelative(0, free, 0))) {
            free++;
        }
        return free;
    }

    static boolean standable(Block block) {
        Material type = block.getType();
        return (type.isAir() || type == Material.LIGHT) && !block.isLiquid();
    }

    static boolean clearView(Location eye, Location target) {
        Vector direction = target.toVector().subtract(eye.toVector());
        double distance = direction.length();
        RayTraceResult hit = eye.getWorld().rayTraceBlocks(eye, direction.normalize(), distance,
                FluidCollisionMode.NEVER, true);
        return hit == null;
    }

    /** El jugador la tiene cerca del centro de la pantalla y nada la tapa. */
    private static boolean sees(Player player, Location feet) {
        return sees(player, feet.clone().add(0, 1.2, 0), SEEN_ANGLE_DEGREES);
    }

    /** El jugador tiene este punto a menos de angleDegrees del centro de la pantalla y nada lo tapa. */
    static boolean sees(Player player, Location target, double angleDegrees) {
        Location eye = player.getEyeLocation();
        Vector to = target.toVector().subtract(eye.toVector());
        if (to.lengthSquared() < 1e-6) {
            return true;
        }
        double angle = Math.toDegrees(eye.getDirection().angle(to));
        return angle <= angleDegrees && clearView(eye, target);
    }

    static double distanceToSegment(Vector p, Vector a, Vector b) {
        Vector ab = b.clone().subtract(a);
        double t = Math.max(0, Math.min(1, p.clone().subtract(a).dot(ab) / ab.lengthSquared()));
        return a.clone().add(ab.multiply(t)).distance(p);
    }

    private static double horizontal(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }
}
