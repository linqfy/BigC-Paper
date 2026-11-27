package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.remap.AttributeModifierMerge.Modifier;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.remap.AttributeModifierMerge.Operation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributeModifierMergeTest {
    private static final Modifier OTHER = new Modifier("other:speed", 0.5, Operation.ADDITION);

    @Test
    void keepsTheSendersModifiersAndAppendsOurs() {
        List<Modifier> merged = AttributeModifierMerge.merge(List.of(OTHER), 0.25);
        assertEquals(List.of(OTHER, new Modifier(AttributeModifierMerge.ID, -0.75, Operation.MULTIPLY_TOTAL)), merged);
    }

    @Test
    void replacesAStaleCopyOfOurs() {
        Modifier stale = new Modifier(AttributeModifierMerge.ID, 3.0, Operation.MULTIPLY_TOTAL);
        List<Modifier> merged = AttributeModifierMerge.merge(List.of(stale, OTHER), 0.5);
        assertEquals(List.of(OTHER, AttributeModifierMerge.modifierFor(0.5)), merged);
    }

    @Test
    void withoutAFactorOnlyOursIsRemoved() {
        Modifier stale = AttributeModifierMerge.modifierFor(2.0);
        assertEquals(List.of(OTHER), AttributeModifierMerge.merge(List.of(OTHER, stale), null));
        assertEquals(List.of(OTHER), AttributeModifierMerge.merge(List.of(OTHER, stale), Double.NaN));
    }

    @Test
    void theFinalValueIsTheRealOneTimesTheFactor() {
        List<Modifier> theirs = List.of(OTHER, new Modifier("a:b", 0.2, Operation.MULTIPLY_BASE),
            new Modifier("c:d", 1.0, Operation.MULTIPLY_TOTAL));
        double real = AttributeModifierMerge.value(1.0, theirs);
        // (1 + 0.5) * 1.2 * 2 = 3.6
        assertEquals(3.6, real, 1e-9);
        assertEquals(real * 0.25, AttributeModifierMerge.value(1.0, AttributeModifierMerge.merge(theirs, 0.25)), 1e-9);
    }

    @Test
    void doesNotMutateTheInput() {
        List<Modifier> theirs = List.of(OTHER);
        AttributeModifierMerge.merge(theirs, 2.0);
        assertEquals(1, theirs.size());
    }
}
