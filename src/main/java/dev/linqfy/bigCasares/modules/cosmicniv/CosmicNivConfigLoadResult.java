package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.List;

public record CosmicNivConfigLoadResult(CosmicNivSettings settings, List<String> errors, List<String> warnings) {
    public CosmicNivConfigLoadResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean valid() {
        return settings != null && errors.isEmpty();
    }
}
