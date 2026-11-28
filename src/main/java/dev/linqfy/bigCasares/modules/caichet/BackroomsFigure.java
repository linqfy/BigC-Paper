package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Display;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * El cuerpo de una entidad de los backrooms, visible solo para su jugador: varias {@link ItemDisplay} (una por parte de
 * {@link BackroomsRigs}) en el mismo lugar, o, para quien juega desde Bedrock, un cuerpo vanilla que Geyser muestra como
 * la entidad custom. Solo dibuja: lo que hace lo decide {@link BackroomsEncounters}.
 */
final class BackroomsFigure {

    /** Cada cuantos ticks se actualizan las rotaciones (los displays interpolan 3 ticks entre una y otra). */
    private static final int TRANSFORM_EVERY = 3;
    /** Con las entidades custom de Geyser el modelo ya trae su tamaño: escala 1. */
    private static final double CUSTOM_SCALE = 1.0;
    private static final double GOLEM_HEIGHT = 2.7;
    private static final double VANILLA_ALLAY_SCALE = 3.0;
    /** El centro de un allay esta a esto de sus pies. */
    private static final double ALLAY_CENTER = 0.3;

    private final Plugin plugin;
    private final Player owner;
    private final BackroomsKind kind;
    private final float fit;
    private final int side;
    private final List<BackroomsRigs.Part> parts;
    private final List<ItemDisplay> displays = new ArrayList<>();
    private final LivingEntity stand;

    /** Los pies de la figura (el centro de la imagen, para la Sonrisa). */
    private Location pos;
    private float yaw;
    private BackroomsRigs.Mode mode = BackroomsRigs.Mode.IDLE;
    private long tick;

    /**
     * @param at   donde van los pies (el centro, para la Sonrisa)
     * @param fit  cuanto se achica por el techo ({@link BackroomsRules#fit})
     * @param side de que lado esta la pared (1 derecha, -1 izquierda; para el Payaso)
     */
    BackroomsFigure(Player owner, BackroomsKind kind, Location at, float fit, int side) {
        this.plugin = Bedrock.plugin();
        this.owner = owner;
        this.kind = kind;
        this.fit = fit;
        this.side = side;
        this.parts = BackroomsRigs.parts(kind);
        this.pos = at.clone();
        World world = at.getWorld();
        boolean bedrock = Bedrock.is(owner);
        for (BackroomsRigs.Part part : parts) {
            ItemDisplay display = spawnPart(world, part);
            if (!bedrock) {
                owner.showEntity(plugin, display);
            }
            displays.add(display);
        }
        stand = bedrock ? spawnBody(world) : null;
        if (stand != null) {
            owner.showEntity(plugin, stand);
        }
        applyTransforms();
    }

    private ItemDisplay spawnPart(World world, BackroomsRigs.Part part) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("caichet", part.model()));
        stack.setItemMeta(meta);
        boolean sonrisa = kind == BackroomsKind.SONRISA;
        return world.spawn(pos, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(sonrisa ? Display.Billboard.CENTER : Display.Billboard.FIXED);
            if (sonrisa) {
                d.setBrightness(new Display.Brightness(15, 15));
            }
            d.setTeleportDuration(2);
            d.setInterpolationDuration(3);
            d.setViewRange(4f);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            Bedrock.markSystem(d);
        });
    }

    private LivingEntity spawnBody(World world) {
        Location at = bodyLocation();
        return kind == BackroomsKind.SONRISA
            ? world.spawn(at, Allay.class, this::configureBody)
            : world.spawn(at, IronGolem.class, this::configureBody);
    }

    private void configureBody(LivingEntity e) {
        e.setAI(false);
        e.setSilent(true);
        e.setGravity(false);
        e.setInvulnerable(true);
        e.setPersistent(false);
        e.setRemoveWhenFarAway(false);
        e.setVisibleByDefault(false);
        Bedrock.markStand(e);
        CaiChetBedrockEntities.present(e, kind.bedrockEntry);
        AttributeInstance scale = e.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(bodyScale());
        }
    }

    private double bodyScale() {
        if (CaiChetBedrockEntities.available()) {
            return CUSTOM_SCALE;
        }
        if (kind == BackroomsKind.SONRISA) {
            return VANILLA_ALLAY_SCALE;
        }
        return Math.max(0.8, BackroomsRigs.height(kind) * fit / GOLEM_HEIGHT);
    }

    private Location bodyLocation() {
        Location at = pos.clone();
        if (kind == BackroomsKind.SONRISA) {
            at.subtract(0, ALLAY_CENTER, 0);
        }
        at.setYaw(yaw);
        at.setPitch(0f);
        return at;
    }

    Location position() {
        return pos.clone();
    }

    /** Luz propia de la figura cuando se apagan las luces: apenas se la ve. */
    private static final Display.Brightness DIM_GLOW = new Display.Brightness(4, 0);
    private boolean dim;

    /** En lo oscuro la figura tiene un poco de luz propia, para que se vea la silueta acercandose. */
    void setDim(boolean value) {
        if (value == dim || kind == BackroomsKind.SONRISA) {
            return; // la Sonrisa ya brilla siempre
        }
        dim = value;
        for (ItemDisplay display : displays) {
            if (display.isValid()) {
                display.setBrightness(value ? DIM_GLOW : null);
            }
        }
    }

    void setMode(BackroomsRigs.Mode mode) {
        this.mode = mode;
    }

    boolean valid() {
        return !displays.isEmpty() && displays.getFirst().isValid();
    }

    /** Mueve la figura (los pies) sin darla vuelta. */
    void moveTo(Location at) {
        pos = at.clone();
    }

    /** La da vuelta para que mire a este punto (solo en horizontal). */
    void faceTowards(Location target) {
        double dx = target.getX() - pos.getX();
        double dz = target.getZ() - pos.getZ();
        if (dx * dx + dz * dz > 1e-6) {
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
    }

    /**
     * La pega a la cara del jugador (a un bloque, mirandolo): para el susto. Los pies quedan mas abajo, para que la
     * cara de la figura sea la que queda frente a los ojos.
     */
    void snapToFace(Player player) {
        snapToFace(player, 1.0);
    }

    /** Igual, a esta distancia de los ojos (para que se tire encima: de lejos a pegada). */
    void snapToFace(Player player, double distance) {
        Location eye = player.getEyeLocation();
        Vector front = eye.toVector().add(eye.getDirection().multiply(distance));
        double drop = kind == BackroomsKind.SONRISA ? 0 : BackroomsRigs.faceHeight(kind) * fit;
        pos = new Location(eye.getWorld(), front.getX(), front.getY() - drop, front.getZ());
        faceTowards(eye);
        push();
    }

    /** Un tick: la figura sigue su posicion y su giro, y cada tanto se actualiza la animacion. */
    void tick() {
        tick++;
        push();
        if (tick % TRANSFORM_EVERY == 0) {
            applyTransforms();
        }
    }

    private void push() {
        Location at = pos.clone();
        if (kind == BackroomsKind.SONRISA) {
            at.setYaw(0f);
        } else {
            at.setYaw(yaw + BackroomsRigs.FRONT_YAW_OFFSET);
        }
        at.setPitch(0f);
        for (ItemDisplay display : displays) {
            if (display.isValid()) {
                display.teleport(at);
            }
        }
        if (stand != null && stand.isValid()) {
            stand.teleport(bodyLocation());
        }
    }

    private void applyTransforms() {
        float bob = kind == BackroomsKind.SONRISA ? 0.1f * (float) Math.sin(tick * 0.06) : 0f;
        for (int i = 0; i < parts.size(); i++) {
            BackroomsRigs.Part part = parts.get(i);
            BackroomsRigs.Pose pose = BackroomsRigs.pose(kind, part, mode, tick, side);
            Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(pose.y()))
                .rotateX((float) Math.toRadians(pose.x()))
                .rotateZ((float) Math.toRadians(pose.z()));
            float scale = part.scale() * fit;
            ItemDisplay display = displays.get(i);
            if (!display.isValid()) {
                continue;
            }
            display.setTransformation(new Transformation(
                new Vector3f(part.x() * fit, part.y() * fit + bob, part.z() * fit), rotation,
                new Vector3f(scale, scale, scale), new Quaternionf()));
            display.setInterpolationDelay(0);
        }
    }

    void remove() {
        displays.forEach(ItemDisplay::remove);
        CaiChetBedrockEntities.discard(stand);
    }
}
