package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * La grieta por donde sale y se va Neguechen: se abre en el piso de verdad (del centro hacia las puntas), con luces
 * verdes en el fondo (froglights) y particulas verdes que suben, y despues se cierra y deja todo como estaba.
 * La forma la da MutangoRiftShape. Se mueve con tick().
 */
final class MutangoRift {

    /** Media grieta: 13 bloques de largo. */
    static final int HALF_LENGTH = 6;
    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.fromRGB(110, 255, 140), 1.4f);
    private static final Particle.DustOptions DARK_GREEN = new Particle.DustOptions(Color.fromRGB(40, 150, 70), 1.1f);

    private enum State { OPENING, OPEN, CLOSING, CLOSED }

    /** Un bloque que se saco o se cambio, para volver a ponerlo. */
    private record Changed(Block block, BlockData original) {
    }

    /** Una columna de la grieta ya en el mundo: el bloque de arriba (la superficie) y su celda. */
    private record Column(Block surface, MutangoRiftShape.Cell cell) {
    }

    private final World world;
    /** Dice si se puede tocar un bloque (zonas protegidas): lo que no, la grieta lo deja como esta. */
    private final Predicate<Block> allowed;
    /** Donde se anota cada bloque antes de tocarlo (si el servidor se cae, al volver a arrancar se devuelven). */
    private final MutangoBlockJournal journal;
    private final Location center;
    private final List<Column> columns = new ArrayList<>();
    /** Por columna, en el mismo orden, lo que se cambio (para cerrarla). */
    private final List<List<Changed>> changed = new ArrayList<>();
    private final int duration;
    private State state = State.CLOSED;
    private int ticks;

    /**
     * Una grieta con el centro en el bloque surface (el del piso) y a lo largo de alongX (true: eje X, false: eje Z).
     * Todavia no se abre: open().
     */
    MutangoRift(Block surface, boolean alongX, long seed, MutangoBlockJournal journal,
                Predicate<Block> allowed) {
        this.allowed = allowed;
        this.journal = journal;
        this.world = surface.getWorld();
        this.center = surface.getLocation().add(0.5, 1, 0.5);
        this.duration = MutangoRiftShape.duration(HALF_LENGTH);
        for (MutangoRiftShape.Cell cell : MutangoRiftShape.shape(seed, HALF_LENGTH)) {
            int dx = alongX ? cell.along() : cell.across();
            int dz = alongX ? cell.across() : cell.along();
            Block column = surface.getRelative(dx, 0, dz);
            // sigue el piso: si ahi el piso esta un poco mas arriba o mas abajo, la grieta tambien
            int top = MutangoGround.y(column.getLocation());
            if (Math.abs(top - surface.getY()) <= 2) {
                column = world.getBlockAt(column.getX(), top, column.getZ());
            }
            columns.add(new Column(column, cell));
            changed.add(new ArrayList<>());
        }
    }

    /** Todos los bloques que esta grieta tocaria se pueden tocar (ninguno esta en una zona protegida). */
    boolean allAllowed() {
        for (Column column : columns) {
            int depth = column.cell().depth();
            for (int down = -1; down <= depth; down++) {
                if (!allowed.test(column.surface().getRelative(0, -down, 0))) {
                    return false;
                }
            }
        }
        return true;
    }

    Location center() {
        return center.clone();
    }

    void open() {
        state = State.OPENING;
        ticks = 0;
        world.playSound(center, Sound.ENTITY_WARDEN_DIG, 2f, 0.6f);
        world.playSound(center, Sound.BLOCK_DEEPSLATE_BREAK, 2f, 0.5f);
    }

    void close() {
        if (state == State.OPENING || state == State.OPEN) {
            state = State.CLOSING;
            ticks = 0;
            world.playSound(center, Sound.BLOCK_DEEPSLATE_PLACE, 2f, 0.5f);
        }
    }

    boolean closed() {
        return state == State.CLOSED;
    }

    void tick() {
        switch (state) {
            case OPENING -> {
                for (int i = 0; i < columns.size(); i++) {
                    if (columns.get(i).cell().openAt() == ticks) {
                        carve(i);
                    }
                }
                if (ticks % 4 == 0) {
                    world.playSound(center, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.5f);
                }
                glow();
                if (++ticks >= duration) {
                    state = State.OPEN;
                    ticks = 0;
                }
            }
            case OPEN -> {
                glow();
                if (ticks++ % 30 == 0) {
                    world.playSound(center, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.2f, 0.6f);
                }
            }
            case CLOSING -> {
                int row = duration - 1 - ticks;
                for (int i = 0; i < columns.size(); i++) {
                    if (columns.get(i).cell().openAt() == row) {
                        fill(i);
                    }
                }
                if (ticks % 4 == 0) {
                    world.playSound(center, Sound.BLOCK_ROOTED_DIRT_PLACE, 1.5f, 0.5f);
                }
                if (++ticks >= duration) {
                    restoreAll();
                }
            }
            case CLOSED -> {
            }
        }
    }

    /** La cierra ya mismo y deja todo como estaba (al apagar el servidor o si se corta la pelea). */
    void restoreAll() {
        for (int i = 0; i < columns.size(); i++) {
            fill(i);
        }
        state = State.CLOSED;
    }

    /** Abre una columna: saca los bloques y pone la luz verde en el fondo. */
    private void carve(int index) {
        Column column = columns.get(index);
        List<Changed> list = changed.get(index);
        if (!list.isEmpty()) {
            return;
        }
        // lo que crece arriba del piso (pasto, flores) tambien se va
        Block above = column.surface().getRelative(0, 1, 0);
        if (!above.getType().isAir() && !above.getType().isSolid() && carvable(above)) {
            change(list, above, Material.AIR.createBlockData());
        }
        int depth = column.cell().depth();
        for (int down = 0; down < depth; down++) {
            Block block = column.surface().getRelative(0, -down, 0);
            if (!carvable(block)) {
                break;
            }
            world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.9, 0.5), 8, 0.3, 0.2, 0.3, 0,
                block.getBlockData());
            change(list, block, Material.AIR.createBlockData());
        }
        Block bottom = column.surface().getRelative(0, -depth, 0);
        if (column.cell().light() && carvable(bottom)) {
            change(list, bottom, Material.VERDANT_FROGLIGHT.createBlockData());
        }
        world.spawnParticle(Particle.DUST, column.surface().getLocation().add(0.5, 1, 0.5), 4, 0.3, 0.2, 0.3, 0,
            DARK_GREEN);
    }

    /** Cambia un bloque: primero lo anota en el diario (con como era), despues lo toca. */
    private void change(List<Changed> list, Block block, BlockData replacement) {
        journal.record(world.getUID(), block.getX(), block.getY(), block.getZ(), block.getBlockData().getAsString());
        list.add(new Changed(block, block.getBlockData()));
        block.setBlockData(replacement, false);
    }

    /** Cierra una columna: vuelve a poner los bloques (y saca a quien este adentro). */
    private void fill(int index) {
        List<Changed> list = changed.get(index);
        if (list.isEmpty()) {
            return;
        }
        Block surface = columns.get(index).surface();
        BoundingBox box = new BoundingBox(surface.getX(), surface.getY() - MutangoRiftShape.MAX_DEPTH - 1,
            surface.getZ(), surface.getX() + 1, surface.getY() + 1, surface.getZ() + 1);
        for (Player player : world.getPlayers()) {
            if (player.getBoundingBox().overlaps(box)) {
                Location out = player.getLocation();
                out.setY(surface.getY() + 1.1);
                player.teleport(out);
            }
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            Changed entry = list.get(i);
            // solo donde sigue lo que puso la grieta (aire o la luz): si alguien puso un bloque ahi, se respeta
            Material now = entry.block().getType();
            if (now.isAir() || now == Material.VERDANT_FROGLIGHT) {
                entry.block().setBlockData(entry.original(), false);
            }
            journal.release(world.getUID(), entry.block().getX(), entry.block().getY(), entry.block().getZ());
        }
        list.clear();
        world.spawnParticle(Particle.BLOCK, surface.getLocation().add(0.5, 1.1, 0.5), 6, 0.3, 0.1, 0.3, 0,
            surface.getBlockData());
    }

    /** Luz verde que sube desde el fondo de lo que ya esta abierto. */
    private void glow() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int k = 0; k < 4; k++) {
            int index = rng.nextInt(columns.size());
            if (changed.get(index).isEmpty()) {
                continue;
            }
            Column column = columns.get(index);
            Location bottom = column.surface().getLocation().add(rng.nextDouble(), -column.cell().depth() + 1.2,
                rng.nextDouble());
            world.spawnParticle(Particle.DUST, bottom, 1, 0.1, 0.1, 0.1, 0, rng.nextBoolean() ? GREEN : DARK_GREEN);
            world.spawnParticle(Particle.GLOW, bottom, 0, 0, 0.15, 0, 1);
            if (rng.nextInt(3) == 0) {
                world.spawnParticle(Particle.HAPPY_VILLAGER, bottom.clone().add(0, 1.5, 0), 1, 0.2, 0.4, 0.2, 0);
            }
        }
    }

    /** Lo que se puede sacar sin romper nada: ni cofres ni hornos, ni bedrock, ni agua o lava, ni aire. */
    private boolean carvable(Block block) {
        Material type = block.getType();
        return !type.isAir() && type != Material.BEDROCK && !block.isLiquid()
            && !(block.getState(false) instanceof TileState) && type.getHardness() >= 0 && allowed.test(block);
    }
}
