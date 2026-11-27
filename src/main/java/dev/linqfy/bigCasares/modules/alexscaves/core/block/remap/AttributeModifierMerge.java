package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure merge of the mining-sync modifier into the modifier list of a {@code block_break_speed} property that is
 * about to go out: every modifier of the sender is kept, ours is (re)applied last, once.
 */
final class AttributeModifierMerge {
    /** Id of the modifier, {@code namespace:path}. */
    static final String ID = "bigcasares:alexscaves_mining_sync";

    /** Operations in the order of the protocol. */
    enum Operation { ADDITION, MULTIPLY_BASE, MULTIPLY_TOTAL }

    record Modifier(String id, double amount, Operation operation) {
    }

    private AttributeModifierMerge() {
    }

    /** The modifier that multiplies the final value by {@code factor}. */
    static Modifier modifierFor(double factor) {
        return new Modifier(ID, factor - 1.0, Operation.MULTIPLY_TOTAL);
    }

    /**
     * @param factor null or NaN for "no sync": then only a stale copy of our modifier is removed
     */
    static List<Modifier> merge(List<Modifier> theirs, Double factor) {
        List<Modifier> merged = new ArrayList<>(theirs.size() + 1);
        for (Modifier modifier : theirs) {
            if (!ID.equals(modifier.id())) {
                merged.add(modifier);
            }
        }
        if (factor != null && !factor.isNaN()) {
            merged.add(modifierFor(factor));
        }
        return merged;
    }

    /** Final value the client computes, vanilla order (add, multiply base, multiply total). */
    static double value(double base, List<Modifier> modifiers) {
        double added = base;
        for (Modifier modifier : modifiers) {
            if (modifier.operation() == Operation.ADDITION) {
                added += modifier.amount();
            }
        }
        double result = added;
        for (Modifier modifier : modifiers) {
            if (modifier.operation() == Operation.MULTIPLY_BASE) {
                result += added * modifier.amount();
            }
        }
        for (Modifier modifier : modifiers) {
            if (modifier.operation() == Operation.MULTIPLY_TOTAL) {
                result *= 1.0 + modifier.amount();
            }
        }
        return result;
    }
}
