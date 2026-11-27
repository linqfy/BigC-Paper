package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The mod's cave paintings ({@code CavePaintingBlock}: smooth limestone with a drawing on its {@code facing} side).
 * Here the block is the module's smooth limestone and the drawing an {@code ItemDisplay} standing in the block in
 * front of that side (it takes that block's light), with the painted face model ({@code display_models.py}). The
 * display remembers its painting and the limestone it belongs to in its persistent data.
 */
public final class CavePaintings {
    /** Painting id ({@code dark}, {@code pewen}...) of a painting display. */
    public static final NamespacedKey PAINTING = new NamespacedKey("bigcasares", "ac_cave_painting");
    /** {x, y, z} of the limestone a painting display belongs to. */
    public static final NamespacedKey HOST = new NamespacedKey("bigcasares", "ac_cave_painting_host");
    public static final String ITEM_PREFIX = "ac_cave_painting_";

    private CavePaintings() {
    }

    /** Where the display of a painting on side {@code facing} of block (x, y, z) stands: the block in front, centred. */
    public static double[] anchor(int x, int y, int z, BlockFace facing) {
        return new double[] {x + 0.5 + facing.getModX(), y + 0.5 + facing.getModY(), z + 0.5 + facing.getModZ()};
    }

    public static Location anchorLocation(World world, int x, int y, int z, BlockFace facing) {
        double[] at = anchor(x, y, z, facing);
        return new Location(world, at[0], at[1], at[2]);
    }

    /** Item model of the painted face of {@code painting}. */
    public static String displayModel(String painting) {
        return "display/cave_painting_" + painting;
    }

    /** Painting id of a module item id ({@code ac_cave_painting_dark} -> {@code dark}), or null. */
    public static String paintingOf(String itemId) {
        return itemId != null && itemId.startsWith(ITEM_PREFIX) && itemId.length() > ITEM_PREFIX.length()
            ? itemId.substring(ITEM_PREFIX.length()) : null;
    }

    /** Sets up a new painting display (spawn consumer; also used by worldgen). */
    public static void configure(ItemDisplay display, String painting, BlockFace facing, int x, int y, int z) {
        ItemStack shown = new ItemStack(Material.PAPER);
        shown.setData(DataComponentTypes.ITEM_MODEL, Key.key("alexscaves", displayModel(painting)));
        display.setItemStack(shown);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setBillboard(Display.Billboard.FIXED);
        display.setTransformation(new Transformation(new Vector3f(), FacingDisplays.leftRotation(facing),
            new Vector3f(1, 1, 1), new Quaternionf()));
        display.setPersistent(true);
        PersistentDataContainer data = display.getPersistentDataContainer();
        data.set(PAINTING, PersistentDataType.STRING, painting);
        data.set(HOST, PersistentDataType.INTEGER_ARRAY, new int[] {x, y, z});
    }

    /** The limestone {x, y, z} of a painting display, or null when the entity is not one. */
    public static int[] host(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof ItemDisplay)) {
            return null;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (!data.has(PAINTING, PersistentDataType.STRING)) {
            return null;
        }
        int[] host = data.get(HOST, PersistentDataType.INTEGER_ARRAY);
        return host != null && host.length == 3 ? host : null;
    }

    /** A painting mark of a structure template: template position, painting id and side. */
    public record Mark(int x, int y, int z, String painting, BlockFace facing) {
    }
}
