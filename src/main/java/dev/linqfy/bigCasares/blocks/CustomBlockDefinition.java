package dev.linqfy.bigCasares.blocks;

import com.google.gson.JsonElement;
import dev.linqfy.bigCasares.dimension.NamespacedId;

import java.util.List;
import java.util.Objects;

/**
 * A server-authoritative custom full-cube block.
 *
 * @param id         stable namespaced id (also the default item id); never reuse for another block
 * @param state      backing vanilla state; stable forever once blocks exist in worlds
 * @param apply      blockstate "apply" JSON (a model object or a weighted list) rendered for {@code state}
 * @param hardness   emulated hardness (vanilla stone is 1.5, deepslate 3.0)
 * @param tool       preferred tool category
 * @param minimumTier minimum tool tier for drops when {@code requiresTool}
 * @param drops      drops without silk touch; empty means "drop itself"
 * @param silkTouchDropsSelf whether silk touch yields the block item
 * @param experience XP dropped (ores), as {@code [min, max]}
 * @param soundGroup vanilla sound group prefix played for break/place, e.g. {@code block.deepslate}
 */
public record CustomBlockDefinition(
    NamespacedId id,
    MushroomState state,
    JsonElement apply,
    float hardness,
    ToolType tool,
    ToolTier minimumTier,
    boolean requiresTool,
    List<Drop> drops,
    boolean silkTouchDropsSelf,
    int minExperience,
    int maxExperience,
    String soundGroup
) {

    public enum ToolType {
        PICKAXE, AXE, SHOVEL, HOE, NONE
    }

    /** Tool tiers in mining order, with vanilla's base mining speeds. */
    public enum ToolTier {
        HAND(1.0F), WOOD(2.0F), STONE(4.0F), COPPER(5.0F), IRON(6.0F), DIAMOND(8.0F), NETHERITE(9.0F), GOLD(12.0F);

        private final float speed;

        ToolTier(float speed) {
            this.speed = speed;
        }

        public float speed() {
            return speed;
        }

        /** Harvest level ordering (gold harvests like wood). */
        public int level() {
            return switch (this) {
                case HAND -> 0;
                case WOOD, GOLD -> 1;
                case STONE, COPPER -> 2;
                case IRON -> 3;
                case DIAMOND -> 4;
                case NETHERITE -> 5;
            };
        }
    }

    /**
     * A drop: {@code item} is a content id ({@code the_sift:cobbled_siftslate}) or a
     * vanilla material id ({@code minecraft:coal}); {@code fortune} applies the ore formula.
     */
    public record Drop(String item, int min, int max, boolean fortune) {
        public Drop {
            Objects.requireNonNull(item, "item");
            if (min < 0 || max < min || max > 64) {
                throw new IllegalArgumentException("Invalid drop count for " + item);
            }
        }
    }

    public CustomBlockDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(apply, "apply");
        apply = apply.deepCopy();
        if (!state.isReservable()) {
            throw new IllegalArgumentException("Block " + id + " uses non-reservable state " + state);
        }
        if (!(hardness >= 0.0F) || hardness > 100.0F) {
            throw new IllegalArgumentException("Hardness out of range for " + id);
        }
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(minimumTier, "minimumTier");
        drops = List.copyOf(drops);
        if (minExperience < 0 || maxExperience < minExperience) {
            throw new IllegalArgumentException("Invalid experience range for " + id);
        }
        Objects.requireNonNull(soundGroup, "soundGroup");
    }

    @Override
    public JsonElement apply() {
        return apply.deepCopy();
    }
}
