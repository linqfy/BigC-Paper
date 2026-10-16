package dev.linqfy.bigCasares.dimension.travel;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Detects a rectangular portal frame (corners optional, like vanilla nether
 * portals) around a start position. Works on any {@link BlockGrid}; the frame and
 * interior predicates come from the {@link PortalDefinition}.
 */
public final class PortalShapeDetector {

    private final Predicate<String> isFrame;
    private final Predicate<String> isInterior;
    private final int minWidth;
    private final int maxWidth;
    private final int minHeight;
    private final int maxHeight;

    public PortalShapeDetector(
        Predicate<String> isFrame, Predicate<String> isInterior,
        int minWidth, int maxWidth, int minHeight, int maxHeight
    ) {
        this.isFrame = java.util.Objects.requireNonNull(isFrame, "isFrame");
        this.isInterior = java.util.Objects.requireNonNull(isInterior, "isInterior");
        if (minWidth < 1 || minHeight < 1 || maxWidth < minWidth || maxHeight < minHeight || maxWidth > 64 || maxHeight > 64) {
            throw new IllegalArgumentException("Invalid portal size limits");
        }
        this.minWidth = minWidth;
        this.maxWidth = maxWidth;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    /**
     * @param x,y,z a frame block (e.g. the one the player clicked) or an interior block
     */
    public Optional<PortalShape> detect(BlockGrid grid, int x, int y, int z) {
        if (interior(grid, x, y, z)) {
            return detectFromInterior(grid, x, y, z);
        }
        if (!frame(grid, x, y, z)) {
            return Optional.empty();
        }
        int[][] neighbours = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
        for (int[] offset : neighbours) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            int nz = z + offset[2];
            if (interior(grid, nx, ny, nz)) {
                Optional<PortalShape> shape = detectFromInterior(grid, nx, ny, nz);
                if (shape.isPresent()) {
                    return shape;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<PortalShape> detectFromInterior(BlockGrid grid, int x, int y, int z) {
        for (PortalShape.Axis axis : PortalShape.Axis.values()) {
            Optional<PortalShape> shape = detectAlong(grid, axis, x, y, z);
            if (shape.isPresent()) {
                return shape;
            }
        }
        return Optional.empty();
    }

    private Optional<PortalShape> detectAlong(BlockGrid grid, PortalShape.Axis axis, int x, int y, int z) {
        int dx = axis == PortalShape.Axis.X ? 1 : 0;
        int dz = axis == PortalShape.Axis.Z ? 1 : 0;
        // Drop to the bottom row.
        int bottom = y;
        int steps = 0;
        while (interior(grid, x, bottom - 1, z)) {
            bottom--;
            if (++steps > maxHeight) {
                return Optional.empty();
            }
        }
        if (!frame(grid, x, bottom - 1, z)) {
            return Optional.empty();
        }
        // Walk to the "left" edge along the bottom row.
        int left = 0;
        while (interior(grid, x - dx * (left + 1), bottom, z - dz * (left + 1))
            && frame(grid, x - dx * (left + 1), bottom - 1, z - dz * (left + 1))) {
            left++;
            if (left > maxWidth) {
                return Optional.empty();
            }
        }
        int startX = x - dx * left;
        int startZ = z - dz * left;
        if (!frame(grid, startX - dx, bottom, startZ - dz)) {
            return Optional.empty();
        }
        // Measure width on the bottom row.
        int width = 0;
        while (width <= maxWidth && interior(grid, startX + dx * width, bottom, startZ + dz * width)
            && frame(grid, startX + dx * width, bottom - 1, startZ + dz * width)) {
            width++;
        }
        if (width < minWidth || width > maxWidth || !frame(grid, startX + dx * width, bottom, startZ + dz * width)) {
            return Optional.empty();
        }
        // Grow rows upward until a full frame row closes the portal.
        int height = 0;
        while (height <= maxHeight) {
            int rowY = bottom + height;
            if (rowIsFrame(grid, startX, rowY, startZ, dx, dz, width)) {
                break;
            }
            if (!frame(grid, startX - dx, rowY, startZ - dz)
                || !frame(grid, startX + dx * width, rowY, startZ + dz * width)) {
                return Optional.empty();
            }
            for (int index = 0; index < width; index++) {
                if (!interior(grid, startX + dx * index, rowY, startZ + dz * index)) {
                    return Optional.empty();
                }
            }
            height++;
        }
        if (height < minHeight || height > maxHeight) {
            return Optional.empty();
        }
        int endX = startX + dx * (width - 1);
        int endZ = startZ + dz * (width - 1);
        return Optional.of(new PortalShape(axis, Math.min(startX, endX), bottom, Math.min(startZ, endZ),
            Math.max(startX, endX), bottom + height - 1, Math.max(startZ, endZ)));
    }

    private boolean rowIsFrame(BlockGrid grid, int startX, int y, int startZ, int dx, int dz, int width) {
        for (int index = 0; index < width; index++) {
            if (!frame(grid, startX + dx * index, y, startZ + dz * index)) {
                return false;
            }
        }
        return true;
    }

    private boolean frame(BlockGrid grid, int x, int y, int z) {
        return y >= grid.minY() && y <= grid.maxY() && isFrame.test(grid.blockId(x, y, z));
    }

    private boolean interior(BlockGrid grid, int x, int y, int z) {
        return y >= grid.minY() && y <= grid.maxY() && isInterior.test(grid.blockId(x, y, z));
    }
}
