package dev.linqfy.bigCasares.modules.toffy;

import java.util.List;

public record ToffyConfigLoadResult(ToffySettings settings, List<String> errors, List<String> warnings) {
    public ToffyConfigLoadResult {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean valid() {
        return settings != null && errors.isEmpty();
    }
}
