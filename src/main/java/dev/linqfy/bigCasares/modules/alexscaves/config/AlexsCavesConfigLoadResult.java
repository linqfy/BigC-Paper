package dev.linqfy.bigCasares.modules.alexscaves.config;

import java.util.List;

/** Settings plus the problems found while loading them. Errors only disable the affected section. */
public record AlexsCavesConfigLoadResult(AlexsCavesSettings settings, List<String> errors, List<String> warnings) {
    public AlexsCavesConfigLoadResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
