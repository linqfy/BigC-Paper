package dev.linqfy.bigCasares.modules.donpollos;

import java.util.Objects;

public record DonPollosTrade(DonPollosItem cost, DonPollosItem result) {

    public DonPollosTrade {
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(result, "result");
    }
}
