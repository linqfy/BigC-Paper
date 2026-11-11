package dev.linqfy.bigCasares.modules.auth.premium;

import java.util.List;
import java.util.UUID;

/** A Mojang-authenticated Java Edition profile. */
public record PremiumProfile(UUID id, String name, List<Property> properties) {

    public record Property(String name, String value, String signature) {
    }
}
