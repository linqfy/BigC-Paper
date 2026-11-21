package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Las criaturas del Sueño (figuras planas en estilo collage, ninguna persona) y lo que se siente estando ahi:
 * <ul>
 *   <li>ojos gigantes flotando en el cielo (dc1, collage);</li>
 *   <li>ojos con alas que vuelan dando vueltas y aletean (collage; algunos tambien en las puertas);</li>
 *   <li>hongos con ojo en el pasto y ventanas de Windows con "GET OUT OF MY HEAD" (collage);</li>
 *   <li>la mancha negra enorme en el cielo (images (9)) y arcoiris pegados en el cielo (dc2, images (9));</li>
 *   <li>nubes de algodon bajas y puertas abiertas flotando en el aire;</li>
 *   <li>frases en pantalla de vez en cuando ("Are you real?") y una cajita de musica lejana.</li>
 * </ul>
 * Aparecen alrededor de cada jugador segun el bioma donde esta y desaparecen lejos. Corre una vez por tick.
 */
final class DreamEntities implements Runnable {

    enum Kind { EYE, WINGED, MUSHROOM, WINDOW, VOID, RAINBOW, CLOUD, DOOR }

    private static final double DESPAWN_DISTANCE = 160;

    private static final class Sprite {
        final ItemDisplay display;
        final Kind kind;
        final Location anchor;
        final double radius;
        final double speed;
        double phase;
        boolean wingsUp;
        float size;
        boolean standFailed;
        /** Para Bedrock: cuerpo vanilla que lo reemplaza (solo lo ve el). Null si no le toca. */
        Entity stand;
        /** Como lo muestra Geyser (el modelo 3D); null si no tiene (el arcoiris). */
        CaiChetBedrockEntities.Entry bedrockEntry;
        /** Solo el arcoiris: la copia acercada que ve cada jugador de Bedrock (ver {@link RainbowPlacement}). */
        final Map<UUID, Entity> rainbowStands = new HashMap<>();

        Sprite(ItemDisplay display, Kind kind, Location anchor, double radius, double speed, double phase) {
            this.display = display;
            this.kind = kind;
            this.anchor = anchor;
            this.radius = radius;
            this.speed = speed;
            this.phase = phase;
        }
    }

    private static final List<String> PHRASES = List.of(
            "Are you real?", "Is this real?", "Who is real?", "Wake up.", "Do you remember this place?",
            "You have been here before.", "GET OUT OF MY HEAD", "Don't wake up.", "This is not a dream.");
    private static final TextColor[] PASTEL = {TextColor.color(0xFFFFFF), TextColor.color(0xFFC6E8),
            TextColor.color(0xC9E8FF), TextColor.color(0xFFF3B0)};

    private final List<Sprite> sprites = new ArrayList<>();
    private final Map<UUID, Integer> nextPhrase = new HashMap<>();
    private final Map<UUID, Integer> nextMusic = new HashMap<>();
    private final Map<UUID, Integer> nextVhs = new HashMap<>();
    private int tick;
    /** Cada cuanto se redibujan los arcoiris para Bedrock (ticks); el polvo dura lo suficiente para no parpadear. */
    private static final int RAINBOW_PERIOD_TICKS = 20;
    /** Hasta que distancia del arcoiris se le muestra a un jugador de Bedrock. */
    private static final double RAINBOW_VIEW_DISTANCE = 90;

    @Override
    public void run() {
        tick++;
        World world = DreamWorld.get();
        if (world == null) {
            return;
        }
        move(world);
        if (tick % 40 == 0) {
            populate(world);
            forgetAbsent(world);
        }
        for (Player player : world.getPlayers()) {
            mood(player);
        }
        if (tick % 10 == 0) {
            bedrockDoors(world);
            bedrockBodies(world);
        }
    }

    private static final Particle.DustOptions[] RAINBOW = {
            new Particle.DustOptions(Color.fromRGB(255, 40, 40), 4f),
            new Particle.DustOptions(Color.fromRGB(255, 160, 0), 4f),
            new Particle.DustOptions(Color.fromRGB(255, 255, 0), 4f),
            new Particle.DustOptions(Color.fromRGB(0, 200, 0), 4f),
            new Particle.DustOptions(Color.fromRGB(0, 120, 255), 4f),
            new Particle.DustOptions(Color.fromRGB(160, 0, 255), 4f)};

    /** Cuerpo vanilla que ve Bedrock en lugar de cada sprite (el arcoiris no tiene uno comun: ver {@link #bedrockRainbow}). */
    private static EntityType standType(Kind kind) {
        return switch (kind) {
            // solo mobs que se pueden crear en dificultad Pacifico (los monstruos no)
            case EYE, CLOUD -> EntityType.HAPPY_GHAST;
            case WINGED -> EntityType.ALLAY;
            case MUSHROOM -> EntityType.MOOSHROOM;
            case WINDOW, DOOR -> EntityType.ARMOR_STAND;
            case VOID -> EntityType.SQUID;
            case RAINBOW -> null;
        };
    }

    /** El modelo de Bedrock de cada tipo (el modelo de Java de los ojos y su lado de Bedrock salen del sorteo). */
    static CaiChetBedrockEntities.Entry bedrockEntry(Kind kind, String model) {
        return switch (kind) {
            case EYE -> CaiChetBedrockEntities.dreamEye(model);
            case CLOUD -> CaiChetBedrockEntities.DREAM_CLOUD;
            case WINGED -> CaiChetBedrockEntities.DREAM_WINGED;
            case MUSHROOM -> CaiChetBedrockEntities.DREAM_MUSHROOM;
            case WINDOW -> CaiChetBedrockEntities.DREAM_WINDOW;
            case DOOR -> CaiChetBedrockEntities.DREAM_DOOR;
            case VOID -> CaiChetBedrockEntities.DREAM_VOID;
            case RAINBOW -> CaiChetBedrockEntities.DREAM_RAINBOW;
        };
    }

    private static double standScale(Kind kind, float size) {
        if (CaiChetBedrockEntities.available() && kind == Kind.MUSHROOM) {
            return Math.max(0.3, Math.min(16.0, size / 4.0)); // el modelo del hongo mide 4 bloques a escala 1
        }
        double scale = switch (kind) {
            case EYE, CLOUD -> size / 4.0;
            case WINGED -> size / 1.5;
            case WINDOW, DOOR -> size / 2.5;
            case VOID -> size / 1.5;
            default -> 1.0;
        };
        return Math.max(0.3, Math.min(16.0, scale));
    }

    /**
     * Bedrock no ve los sprites (son displays): ve un cuerpo vanilla quieto en el mismo lugar, y el sprite queda
     * escondido para el. Los arcoiris se dibujan con particulas. Cada 10 ticks.
     */
    private void bedrockBodies(World world) {
        List<Player> viewers = Bedrock.in(world);
        if (viewers.isEmpty()) {
            // nadie de Bedrock: los cuerpos sobran y no se les gasta un teleport por tick
            for (Sprite s : sprites) {
                if (s.stand != null) {
                    CaiChetBedrockEntities.discard(s.stand);
                    s.stand = null;
                }
                discardRainbowStands(s);
            }
            return;
        }
        Plugin plugin = Bedrock.plugin();
        for (Sprite s : sprites) {
            if (!s.display.isValid()) {
                continue;
            }
            EntityType type = standType(s.kind);
            if (type != null && s.stand == null && !s.standFailed) {
                try {
                    s.stand = spawnStand(s, type);
                } catch (RuntimeException e) {
                    s.standFailed = true; // no insistir cada 10 ticks
                    Bedrock.plugin().getLogger().warning("No pude crear el cuerpo de Bedrock de " + s.kind + ": " + e.getMessage());
                }
            }
            for (Player p : viewers) {
                if (p.canSee(s.display)) {
                    p.hideEntity(plugin, s.display);
                }
                if (s.stand != null && s.stand.isValid() && !p.canSee(s.stand)) {
                    p.showEntity(plugin, s.stand);
                }
            }
            if (s.kind == Kind.RAINBOW) {
                if (CaiChetBedrockEntities.available()) {
                    bedrockRainbow(s, viewers, plugin);
                } else {
                    discardRainbowStands(s);
                }
                if (!CaiChetBedrockEntities.available() && tick % RAINBOW_PERIOD_TICKS == 0) {
                    for (Player p : viewers) {
                        if (p.getLocation().distanceSquared(s.anchor) < RAINBOW_VIEW_DISTANCE * RAINBOW_VIEW_DISTANCE) {
                            drawRainbow(p, s);
                        }
                    }
                }
            }
        }
    }

    /**
     * El arcoiris esta mas lejos de lo que el servidor manda entidades: cada jugador de Bedrock cercano tiene su propia
     * copia, mas cerca y mas grande en proporcion (ver {@link RainbowPlacement}), que solo ve el. Se coloca de nuevo en
     * cada pasada y se borra cuando el jugador se aleja, cambia de mundo o se va.
     */
    private static void bedrockRainbow(Sprite s, List<Player> viewers, Plugin plugin) {
        Set<UUID> inRange = new HashSet<>();
        for (Player p : viewers) {
            if (p.isOnline() && p.getWorld().equals(s.anchor.getWorld())
                    && p.getLocation().distanceSquared(s.anchor) < RAINBOW_VIEW_DISTANCE * RAINBOW_VIEW_DISTANCE) {
                inRange.add(p.getUniqueId());
            }
        }
        for (Iterator<Map.Entry<UUID, Entity>> it = s.rainbowStands.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Entity> own = it.next();
            if (!inRange.contains(own.getKey()) || !own.getValue().isValid()) {
                CaiChetBedrockEntities.discard(own.getValue());
                it.remove();
            }
        }
        for (Player p : viewers) {
            if (!inRange.contains(p.getUniqueId())) {
                continue;
            }
            Location eye = p.getEyeLocation();
            RainbowPlacement.Placement at = RainbowPlacement.place(eye.getX(), eye.getY(), eye.getZ(),
                    s.anchor.getX(), s.anchor.getY(), s.anchor.getZ(), s.size);
            Location where = new Location(s.anchor.getWorld(), at.x(), at.y(), at.z(), at.yaw(), 0f);
            Entity stand = s.rainbowStands.get(p.getUniqueId());
            if (stand == null) {
                try {
                    stand = spawnRainbowStand(where, at.scale());
                } catch (RuntimeException e) {
                    Bedrock.plugin().getLogger().warning("No pude crear el arcoiris de Bedrock: " + e.getMessage());
                    continue;
                }
                s.rainbowStands.put(p.getUniqueId(), stand);
            } else {
                stand.teleport(where);
                setScale(stand, at.scale());
            }
            if (!p.canSee(stand)) {
                p.showEntity(plugin, stand);
            }
        }
    }

    private static Entity spawnRainbowStand(Location where, double scale) {
        Entity e = where.getWorld().spawnEntity(where, EntityType.valueOf(CaiChetBedrockEntities.DREAM_RAINBOW.javaEntityType()),
                CreatureSpawnEvent.SpawnReason.CUSTOM, spawned -> {
                    spawned.setPersistent(false);
                    spawned.setVisibleByDefault(false);
                    Bedrock.markStand(spawned);
                    CaiChetBedrockEntities.present(spawned, CaiChetBedrockEntities.DREAM_RAINBOW);
                });
        e.setSilent(true);
        e.setGravity(false);
        e.setInvulnerable(true);
        setScale(e, scale);
        return e;
    }

    private static void setScale(Entity e, double scale) {
        if (e instanceof LivingEntity living) {
            AttributeInstance attribute = living.getAttribute(Attribute.SCALE);
            if (attribute != null && attribute.getBaseValue() != scale) {
                attribute.setBaseValue(scale);
            }
        }
    }

    private static void discardRainbowStands(Sprite s) {
        s.rainbowStands.values().forEach(CaiChetBedrockEntities::discard);
        s.rainbowStands.clear();
    }

    private static Entity spawnStand(Sprite s, EntityType type) {
        // la marca y el "oculto" van en el consumer: tienen que estar puestos antes del evento de spawn
        Entity e = s.display.getWorld().spawnEntity(s.display.getLocation(), type,
                CreatureSpawnEvent.SpawnReason.CUSTOM, spawned -> {
                    spawned.setPersistent(false);
                    spawned.setVisibleByDefault(false);
                    Bedrock.markStand(spawned);
                    if (s.bedrockEntry != null) {
                        CaiChetBedrockEntities.present(spawned, s.bedrockEntry);
                    }
                });
        e.setSilent(true);
        e.setGravity(false);
        e.setInvulnerable(true);
        if (e instanceof Mob mob) {
            mob.setAI(false);
        }
        if (e instanceof LivingEntity living) {
            AttributeInstance scale = living.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(standScale(s.kind, s.size));
            }
        }
        return e;
    }

    private static void drawRainbow(Player p, Sprite s) {
        for (int band = 0; band < RAINBOW.length; band++) {
            double radius = s.size * (1 - band * 0.05);
            for (int k = 0; k <= 16; k++) {
                double a = Math.PI * k / 16.0;
                Location at = s.anchor.clone().add(-Math.cos(a) * radius, Math.sin(a) * radius, 0);
                p.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, RAINBOW[band]);
            }
        }
    }

    private static final Particle.DustOptions DOOR_BLACK =
            new Particle.DustOptions(Color.fromRGB(1, 1, 1), 3f);
    private static final Particle.DustOptions DOOR_RED =
            new Particle.DustOptions(Color.fromRGB(230, 20, 40), 3f);

    /** Bedrock no ve los huecos negros de las puertas sueltas (son displays): se marcan con particulas. */
    private void bedrockDoors(World world) {
        long seed = world.getSeed();
        for (Player player : Bedrock.in(world)) {
            int cx = player.getLocation().getBlockX() >> 4;
            int cz = player.getLocation().getBlockZ() >> 4;
            for (int x = cx - 2; x <= cx + 2; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    for (DreamLayout.DoorSpot door : DreamLayout.doorsInChunk(seed, x, z)) {
                        player.spawnParticle(Particle.DUST,
                                new Location(world, door.x() + 0.5, door.y() + 1.0, door.z() + 0.5), 16, 0.4, 0.9, 0.4, 0,
                                door.red() ? DOOR_RED : DOOR_BLACK);
                    }
                }
            }
        }
    }

    /** Los tiempos de frases, musica y VHS solo se guardan para quien sigue en el Sueño. */
    private void forgetAbsent(World world) {
        Set<UUID> inside = new HashSet<>();
        for (Player player : world.getPlayers()) {
            inside.add(player.getUniqueId());
        }
        nextPhrase.keySet().retainAll(inside);
        nextMusic.keySet().retainAll(inside);
        nextVhs.keySet().retainAll(inside);
    }

    void removeAll() {
        DreamLayout.clearDoorCache();
        nextPhrase.clear();
        nextMusic.clear();
        nextVhs.clear();
        sprites.forEach(DreamEntities::removeSprite);
        sprites.clear();
    }

    private static void removeSprite(Sprite s) {
        s.display.remove();
        CaiChetBedrockEntities.discard(s.stand);
        discardRainbowStands(s);
    }

    // ------------------------------------------------------------------ movimiento

    private void move(World world) {
        for (Iterator<Sprite> it = sprites.iterator(); it.hasNext(); ) {
            Sprite s = it.next();
            if (!s.display.isValid() || !s.display.getWorld().equals(world)) {
                removeSprite(s);
                it.remove();
                continue;
            }
            s.phase += s.speed;
            Location to = switch (s.kind) {
                // vuelan en circulos, subiendo y bajando
                case WINGED -> s.anchor.clone().add(Math.cos(s.phase) * s.radius,
                        Math.sin(s.phase * 2.3) * 2.5, Math.sin(s.phase) * s.radius);
                // flotan: se mecen despacio
                case EYE, WINDOW, VOID, DOOR -> s.anchor.clone().add(Math.cos(s.phase * 0.7) * s.radius,
                        Math.sin(s.phase) * 0.8, Math.sin(s.phase * 0.5) * s.radius);
                case CLOUD -> s.anchor.clone().add(Math.cos(s.phase * 0.2) * s.radius, 0, Math.sin(s.phase * 0.2) * s.radius);
                case MUSHROOM, RAINBOW -> null;
            };
            if (to != null) {
                s.display.teleport(to);
            }
            if (s.stand != null && s.stand.isValid()) {
                s.stand.teleport(to != null ? to : s.display.getLocation());
            }
            if (s.kind == Kind.WINGED && tick % 5 == 0) {
                s.wingsUp = !s.wingsUp;
                s.display.setItemStack(stack(s.wingsUp ? "dream_wing_up" : "dream_wing_down"));
            }
        }
    }

    // ------------------------------------------------------------------ aparicion por bioma

    private void populate(World world) {
        List<Player> players = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR) {
                players.add(p);
            }
        }
        // las que quedaron lejos de todos se van
        sprites.removeIf(s -> {
            for (Player p : players) {
                if (p.getLocation().distanceSquared(s.display.getLocation()) < DESPAWN_DISTANCE * DESPAWN_DISTANCE) {
                    return false;
                }
            }
            removeSprite(s);
            return true;
        });
        long seed = world.getSeed();
        for (Player p : players) {
            DreamLayout.Theme theme = DreamLayout.theme(seed, p.getLocation().getBlockX(), p.getLocation().getBlockZ());
            Map<Kind, Integer> wanted = new EnumMap<>(Kind.class);
            // en todo el Sueño: ojos mirando, angeles-ojo volando, hongos con ojo, nubes bajas, un arcoiris
            wanted.put(Kind.EYE, 4);
            wanted.put(Kind.WINGED, 2);
            wanted.put(Kind.MUSHROOM, 4);
            wanted.put(Kind.CLOUD, 5);
            wanted.put(Kind.RAINBOW, 1);
            wanted.put(Kind.DOOR, 1);
            switch (theme) {
                case OJOS -> wanted.put(Kind.EYE, 9);
                case PUERTAS -> {
                    wanted.put(Kind.DOOR, 3);
                    wanted.put(Kind.RAINBOW, 2);
                }
                case PLAZA -> wanted.put(Kind.CLOUD, 8);
                case CAMPO -> {
                    wanted.put(Kind.VOID, 1);
                    wanted.put(Kind.RAINBOW, 2);
                }
                case COLLAGE -> {
                    wanted.put(Kind.EYE, 6);
                    wanted.put(Kind.WINGED, 4);
                    wanted.put(Kind.MUSHROOM, 9);
                    wanted.put(Kind.WINDOW, 2);
                }
                case BARRIO -> wanted.put(Kind.WINGED, 3);
            }
            for (Map.Entry<Kind, Integer> entry : wanted.entrySet()) {
                int have = count(p, entry.getKey());
                for (int i = have; i < entry.getValue(); i++) {
                    spawn(world, p, entry.getKey());
                }
            }
        }
    }

    private int count(Player p, Kind kind) {
        int n = 0;
        for (Sprite s : sprites) {
            if (s.kind == kind && s.display.getLocation().distanceSquared(p.getLocation()) < 110 * 110) {
                n++;
            }
        }
        return n;
    }

    private void spawn(World world, Player p, Kind kind) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = r.nextDouble(Math.PI * 2);
        double distance = switch (kind) {
            case VOID, RAINBOW -> r.nextDouble(90, 140);
            case CLOUD -> r.nextDouble(25, 90);
            case DOOR -> r.nextDouble(18, 50);
            case MUSHROOM -> r.nextDouble(8, 40);
            case WINDOW -> r.nextDouble(14, 35);
            default -> r.nextDouble(20, 60);
        };
        double x = p.getX() + Math.cos(angle) * distance;
        double z = p.getZ() + Math.sin(angle) * distance;
        if (!world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            return;
        }
        int ground = DreamLayout.height(world.getSeed(), (int) Math.floor(x), (int) Math.floor(z));
        float size;
        double y;
        String model;
        Display.Billboard billboard = Display.Billboard.CENTER;
        double radius;
        double speed;
        switch (kind) {
            case EYE -> {
                size = (float) r.nextDouble(6, 11);
                y = ground + r.nextDouble(14, 34);
                model = "dream_eye" + (1 + r.nextInt(3));
                radius = r.nextDouble(0.5, 2);
                speed = r.nextDouble(0.01, 0.03);
            }
            case WINGED -> {
                size = (float) r.nextDouble(7, 11);
                y = ground + r.nextDouble(8, 20);
                model = "dream_wing_up";
                radius = r.nextDouble(6, 16);
                speed = r.nextDouble(0.02, 0.045) * (r.nextBoolean() ? 1 : -1);
            }
            case MUSHROOM -> {
                size = (float) r.nextDouble(2.6, 4.6);
                y = ground + 1;
                model = "dream_mushroom";
                billboard = Display.Billboard.VERTICAL;
                radius = 0;
                speed = 0;
            }
            case WINDOW -> {
                size = (float) r.nextDouble(3, 4.5);
                y = ground + r.nextDouble(3, 7);
                model = "dream_window";
                radius = r.nextDouble(0.2, 0.8);
                speed = r.nextDouble(0.01, 0.02);
            }
            case RAINBOW -> {
                size = (float) r.nextDouble(70, 110);
                y = ground + size * 0.28; // el pie del arco queda justo en el horizonte
                model = "dream_rainbow";
                billboard = Display.Billboard.VERTICAL;
                radius = 0;
                speed = 0;
            }
            case CLOUD -> {
                size = (float) r.nextDouble(12, 26);
                y = ground + r.nextDouble(14, 34);
                model = "dream_cloud";
                radius = r.nextDouble(3, 10);
                speed = r.nextDouble(0.004, 0.01);
            }
            case DOOR -> {
                size = (float) r.nextDouble(6, 10);
                y = ground + r.nextDouble(6, 22);
                model = "dream_door";
                radius = r.nextDouble(0.3, 1.2);
                speed = r.nextDouble(0.01, 0.02);
            }
            default -> {
                size = (float) r.nextDouble(30, 45);
                y = ground + r.nextDouble(45, 65);
                model = "dream_void";
                radius = r.nextDouble(0.5, 1.5);
                speed = 0.004;
            }
        }
        Location at = new Location(world, x, y, z);
        float s = size;
        Display.Billboard bb = billboard;
        ItemDisplay display = world.spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(stack(model));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(bb);
            float lift = kind == Kind.MUSHROOM ? s / 2f : 0f;
            d.setTransformation(new Transformation(new Vector3f(0f, lift, 0f), new AxisAngle4f(),
                    new Vector3f(s, s, s), new AxisAngle4f()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(2);
            d.setViewRange(kind == Kind.VOID || kind == Kind.RAINBOW || kind == Kind.CLOUD ? 6f : 3f);
            d.setPersistent(false);
        });
        Sprite sprite = new Sprite(display, kind, at, radius, speed, r.nextDouble(Math.PI * 2));
        sprite.size = s;
        sprite.bedrockEntry = bedrockEntry(kind, model);
        sprites.add(sprite);
    }

    private static ItemStack stack(String model) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("caichet", model));
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------------ frases y musica

    private static final long CHIMES_MIN_TICKS = 20L * 60;
    private static final long CHIMES_MAX_TICKS = 20L * 120;

    private void mood(Player p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        UUID id = p.getUniqueId();
        int phraseAt = nextPhrase.computeIfAbsent(id, k -> tick + 20 * r.nextInt(8, 20));
        if (tick >= phraseAt) {
            nextPhrase.put(id, tick + 20 * r.nextInt(25, 70));
            showPhrase(p);
        }
        if (Bedrock.is(p)) {
            // Bedrock no tiene el overlay VHS: cada tanto se ve "[ PLAY ]" y un parpadeo oscuro, como una cinta
            int vhsAt = nextVhs.computeIfAbsent(id, k -> tick + 20 * r.nextInt(10, 25));
            if (tick >= vhsAt) {
                nextVhs.put(id, tick + 20 * r.nextInt(20, 50));
                p.sendActionBar(Component.text("[ PLAY ]", TextColor.color(0xFFFFFF)));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 14, 0,
                        false, false, false));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 0.5f);
            }
        }
        // campanas lejanas cada 1-2 minutos; es aparte de la cajita de musica de abajo
        if (CaiChetSounds.due(id, "dream-chimes", tick, CHIMES_MIN_TICKS, CHIMES_MAX_TICKS, r)) {
            CaiChetSounds.playTo(p, p.getLocation(), CaiChetSounds.DREAM_CHIMES, SoundCategory.AMBIENT, 0.5f, 1f);
        }
        int musicAt = nextMusic.computeIfAbsent(id, k -> tick + 20 * r.nextInt(10, 30));
        if (tick >= musicAt) {
            nextMusic.put(id, tick + 20 * r.nextInt(30, 75));
            musicBox(p);
        }
    }

    /** Frases en pantalla, suaves, en colores pastel; a veces arriba, a veces abajo. */
    void showPhrase(Player p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String phrase = PHRASES.get(r.nextInt(PHRASES.size()));
        Component text = Component.text(phrase, PASTEL[r.nextInt(PASTEL.length)])
                .decoration(TextDecoration.ITALIC, r.nextBoolean());
        Title.Times times = Title.Times.times(Duration.ofMillis(900), Duration.ofMillis(2200), Duration.ofMillis(1300));
        if (r.nextBoolean()) {
            p.showTitle(Title.title(text, Component.empty(), times));
        } else {
            p.showTitle(Title.title(Component.empty(), text, times));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.5f);
    }

    /** Unas notas de cajita de musica, como de un recuerdo. */
    private void musicBox(Player p) {
        float[] notes = {0.6f, 0.8f, 0.9f, 1.2f, 1.0f, 0.8f};
        int start = ThreadLocalRandom.current().nextInt(3);
        for (int i = 0; i < 4; i++) {
            float pitch = notes[(start + i) % notes.length];
            CaiChetRuntime.tasks().later(() -> {
                if (p.isOnline()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, pitch);
                }
            }, i * 7L);
        }
    }
}
