package dev.linqfy.bigCasares.modules.dupes;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperOptionsTest {
    @Test void readsEachConfiguredOptionIndependently() {
        Map<String, Object> values = Map.of(
                PaperOptions.PISTONS, true, PaperOptions.HOOKS, false);
        PaperOptions options = PaperOptions.read(values::get);
        assertTrue(options.pistons());
        assertFalse(options.hooks());
    }

    @Test void missingSettingsUsePaperDefaults() {
        PaperOptions options = PaperOptions.read(key -> null);
        assertFalse(options.pistons());
        assertFalse(options.hooks());
    }

    @Test void invalidValuesAreNotReportedAsActive() {
        for (Object value : new Object[]{"true", 1, Map.of()}) {
            assertThrows(IllegalArgumentException.class, () -> PaperOptions.read(key -> value));
        }
    }

    @Test void readFailureIsReportedInsteadOfAssumingDefaults() {
        IllegalStateException error = new IllegalStateException("cannot read Paper config");
        assertEquals(error, assertThrows(IllegalStateException.class,
                () -> PaperOptions.read(key -> { throw error; })));
    }

    @Test void snapshotsDoNotMutateConfigurationOrChangeAfterLaterEdits() {
        Map<String, Object> values = new HashMap<>();
        values.put(PaperOptions.HOOKS, true);
        PaperOptions first = PaperOptions.read(values::get);
        assertEquals(Map.of(PaperOptions.HOOKS, true), values);
        values.put(PaperOptions.PISTONS, true);
        PaperOptions second = PaperOptions.read(values::get);
        assertFalse(first.pistons());
        assertTrue(second.pistons());
    }
}
