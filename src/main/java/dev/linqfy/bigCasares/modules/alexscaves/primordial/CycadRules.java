package dev.linqfy.bigCasares.modules.alexscaves.primordial;

/**
 * Rules of the mod's cycad ({@code CycadBlock}, {@code CycadFeature}) apart from the world: which block is the tip of
 * a stack, what it can stand on, how far bone meal grows it and how tall the generated stacks are.
 */
final class CycadRules {
    /** Mod {@code isValidBonemealTarget}: a stack grows while it has fewer than this many cycads (the tip counted). */
    static final int MAX_STACK_TO_GROW = 4;

    private CycadRules() {
    }

    /** Mod {@code updateShape}: a cycad is the tip of its stack (the leafy model) when no cycad is above it. */
    static boolean tip(boolean cycadAbove) {
        return !cycadAbove;
    }

    /** Mod {@code mayPlaceOn}: dirt blocks (and farmland, as any bush) or another cycad. */
    static boolean mayStandOn(boolean dirtOrFarmland, boolean cycad) {
        return dirtOrFarmland || cycad;
    }

    /** Mod {@code isValidBonemealTarget}: only a tip, and only while its stack (it included) has fewer than four. */
    static boolean canGrow(boolean tip, int stackHeight) {
        return tip && stackHeight < MAX_STACK_TO_GROW;
    }

    /** Mod {@code isBonemealSuccess}: half of the bone meal grows the stack. */
    static boolean boneMealWorks(boolean coin) {
        return coin;
    }

    /**
     * Mod {@code CycadFeature}: {@code ceil(random * 2.5)}, so 0 to 3. The feature walks from the scanned block up to
     * this height inclusive, and the last position is the tip.
     */
    static int generatedHeight(float roll) {
        return (int) Math.ceil(roll * 2.5F);
    }
}
