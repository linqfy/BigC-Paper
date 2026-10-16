package dev.linqfy.bigCasares.blocks;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition.ToolTier;
import dev.linqfy.bigCasares.blocks.CustomBlockDefinition.ToolType;

/**
 * Computes the {@code block_break_speed} attribute multiplier that makes a
 * mushroom-backed custom block mine like its emulated hardness and tool.
 *
 * <p>Vanilla progress per tick is {@code speed / hardness / (harvestable ? 30 : 100)},
 * where {@code speed} is the tool speed (plus efficiency) only when the tool is
 * correct for the block. Mushroom blocks have hardness 0.2, need no tool and are
 * axe-mineable. Haste, fatigue, water and air penalties multiply both sides and
 * cancel out of the ratio. The attribute multiplies the final speed, so the
 * client predicts the emulated breaking time correctly.</p>
 */
public final class BreakSpeedModel {

    /** Hardness of vanilla mushroom blocks and stems. */
    public static final float MUSHROOM_HARDNESS = 0.2F;

    private BreakSpeedModel() {
    }

    /** The held tool, as relevant to breaking. */
    public record HeldTool(ToolType type, ToolTier tier, int efficiency) {
        public static final HeldTool HAND = new HeldTool(ToolType.NONE, ToolTier.HAND, 0);

        float speed() {
            if (type == ToolType.NONE) {
                return 1.0F;
            }
            float base = tier.speed();
            return efficiency > 0 ? base + efficiency * efficiency + 1 : base;
        }
    }

    /** Progress per tick for a vanilla mushroom block. */
    public static double vanillaMushroomProgress(HeldTool tool) {
        double speed = tool.type() == ToolType.AXE ? tool.speed() : 1.0;
        return speed / MUSHROOM_HARDNESS / 30.0;
    }

    /** Progress per tick the custom block should have. */
    public static double customProgress(CustomBlockDefinition block, HeldTool tool) {
        if (block.hardness() == 0.0F) {
            return Double.POSITIVE_INFINITY;
        }
        boolean correct = block.tool() != ToolType.NONE && tool.type() == block.tool();
        double speed = correct ? tool.speed() : 1.0;
        boolean harvestable = canHarvest(block, tool);
        return speed / block.hardness() / (harvestable ? 30.0 : 100.0);
    }

    public static boolean canHarvest(CustomBlockDefinition block, HeldTool tool) {
        if (!block.requiresTool()) {
            return true;
        }
        return tool.type() == block.tool() && tool.tier().level() >= block.minimumTier().level();
    }

    /** Multiplier for the {@code block_break_speed} attribute (base 1.0). */
    public static double multiplier(CustomBlockDefinition block, HeldTool tool) {
        double vanilla = vanillaMushroomProgress(tool);
        double custom = customProgress(block, tool);
        if (Double.isInfinite(custom)) {
            return 1024.0;
        }
        // The attribute is clamped to [0, 1024] by vanilla.
        return Math.max(0.0, Math.min(1024.0, custom / vanilla));
    }
}
