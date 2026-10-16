package dev.linqfy.bigCasares.dimension.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.worldgen.SurfaceRule.CaveSurface;
import dev.linqfy.bigCasares.dimension.worldgen.SurfaceRule.SurfaceCondition;

import java.util.Arrays;

/** Readable shorthands for common surface-rule conditions. */
public final class SurfaceRules {

    private SurfaceRules() {
    }

    public static SurfaceCondition biome(NamespacedId... biomes) {
        return new SurfaceRule.BiomeIs(Arrays.asList(biomes));
    }

    /** True on the topmost solid block of a floor (offset 0) and {@code depth} blocks below it. */
    public static SurfaceCondition onFloor(int depth) {
        return new SurfaceRule.StoneDepth(depth, false, 0, CaveSurface.FLOOR);
    }

    /** Floor band that also follows vanilla's per-column surface depth noise. */
    public static SurfaceCondition underFloor(int offset) {
        return new SurfaceRule.StoneDepth(offset, true, 0, CaveSurface.FLOOR);
    }

    public static SurfaceCondition onCeiling() {
        return new SurfaceRule.StoneDepth(0, false, 0, CaveSurface.CEILING);
    }

    public static SurfaceCondition aboveY(int y) {
        return new SurfaceRule.YAbove(VerticalAnchor.absolute(y), 0, false);
    }

    public static SurfaceCondition aboveWaterLevel(int offset) {
        return new SurfaceRule.Water(offset, 0, false);
    }

    public static SurfaceCondition noise(NamespacedId noise, double min, double max) {
        return new SurfaceRule.NoiseThreshold(noise, min, max);
    }

    public static SurfaceCondition not(SurfaceCondition condition) {
        return new SurfaceRule.Not(condition);
    }

    public static SurfaceCondition steep() {
        return new SurfaceRule.Steep();
    }

    public static SurfaceCondition abovePreliminarySurface() {
        return new SurfaceRule.AbovePreliminarySurface();
    }

    /** Random dithering between two absolute heights (bedrock-floor style). */
    public static SurfaceCondition gradient(String randomName, int trueAtAndBelow, int falseAtAndAbove) {
        return new SurfaceRule.VerticalGradient(randomName,
            VerticalAnchor.absolute(trueAtAndBelow), VerticalAnchor.absolute(falseAtAndAbove));
    }

    public static SurfaceCondition bottomGradient(String randomName, int trueAtAndBelow, int falseAtAndAbove) {
        return new SurfaceRule.VerticalGradient(randomName,
            VerticalAnchor.aboveBottom(trueAtAndBelow), VerticalAnchor.aboveBottom(falseAtAndAbove));
    }
}
