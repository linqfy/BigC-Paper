package dev.linqfy.bigCasares.dimension.travel;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A buildable portal type leading into a dimension.
 *
 * @param id                 stable id stored with every built portal
 * @param frameBlocks        block ids (or custom block ids resolved by the runtime) that form the frame
 * @param activationItem     item used on the frame to light it: a vanilla material id
 *                           ({@code minecraft:echo_shard}) or a catalog id ({@code bigcasares:rift_key});
 *                           blank for portals lit only by code ({@code DimensionPlatform#lightPortal})
 * @param consumeActivationItem whether lighting consumes one item
 * @param minWidth           interior limits
 * @param createExitPortal   whether arriving through it builds a linked return frame in the dimension
 * @param interiorLight      light level (0 = none) of the invisible light blocks filling the interior
 * @param surfaceItemModel   optional item model ({@code namespace:path}) shown as a scaled display
 *                           entity across the interior; requires the resource pack to look right
 * @param ambientParticle    optional vanilla particle id emitted inside lit portals near players
 */
public record PortalDefinition(
    String id,
    Set<String> frameBlocks,
    String activationItem,
    boolean consumeActivationItem,
    int minWidth,
    int maxWidth,
    int minHeight,
    int maxHeight,
    boolean createExitPortal,
    int interiorLight,
    String surfaceItemModel,
    String ambientParticle
) {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    public PortalDefinition {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid portal id: " + id);
        }
        frameBlocks = Set.copyOf(frameBlocks);
        if (frameBlocks.isEmpty()) {
            throw new IllegalArgumentException("Portal " + id + " needs at least one frame block");
        }
        Objects.requireNonNull(activationItem, "activationItem");
        if (interiorLight < 0 || interiorLight > 15) {
            throw new IllegalArgumentException("interiorLight must be within [0, 15]");
        }
    }

    public PortalShapeDetector detector() {
        return new PortalShapeDetector(frameBlocks::contains, PortalDefinition::isOpenInterior,
            minWidth, maxWidth, minHeight, maxHeight);
    }

    /** Whether players can light the frame with an item (otherwise only code can). */
    public boolean itemActivated() {
        return !activationItem.isBlank();
    }

    /**
     * Interior blocks a frame may enclose before lighting: air, light and thin
     * coverings that grow over old frames (sculk veins, glow lichen), which lighting
     * replaces.
     */
    public static boolean isOpenInterior(String blockId) {
        return blockId.equals("minecraft:air") || blockId.equals("minecraft:cave_air")
            || blockId.equals("minecraft:void_air") || blockId.equals("minecraft:light")
            || blockId.equals("minecraft:sculk_vein") || blockId.equals("minecraft:glow_lichen");
    }
}
