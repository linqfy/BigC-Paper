package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import java.util.Locale;
import java.util.Set;

/**
 * Picks the variant of a module block when a player places it, following the
 * mod's placement: pillars align with the clicked face's axis, directional
 * blocks (nodes, magnets, activators) face away from the clicked face.
 */
public final class BlockVariants {
    public static final String DEFAULT = "default";

    private BlockVariants() {
    }

    /**
     * @param variants    variants of the block
     * @param clickedFace name of the face that was clicked ({@code UP}, {@code NORTH}...)
     */
    public static String choose(Set<String> variants, String clickedFace) {
        if (variants.contains(DEFAULT) || variants.isEmpty()) {
            return DEFAULT;
        }
        String face = clickedFace == null ? "up" : clickedFace.toLowerCase(Locale.ROOT);
        if (variants.contains("x") && variants.contains("y") && variants.contains("z")) {
            return switch (face) {
                case "east", "west" -> "x";
                case "north", "south" -> "z";
                default -> "y";
            };
        }
        if (variants.contains(face)) {
            return face;
        }
        if (variants.contains("up") && variants.contains("down")) {
            return face.equals("down") ? "down" : "up";
        }
        return variants.iterator().next();
    }
}
