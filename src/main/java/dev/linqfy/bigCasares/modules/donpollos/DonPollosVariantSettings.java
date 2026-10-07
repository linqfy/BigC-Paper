package dev.linqfy.bigCasares.modules.donpollos;

import java.util.List;
import java.util.Objects;

public record DonPollosVariantSettings(
    DonPolloVariant variant,
    String displayName,
    String modelKey,
    String animation,
    boolean wander,
    double speed,
    String menuTitle,
    List<DonPollosTrade> trades,
    List<DonPollosOffer> offers
) {

    public DonPollosVariantSettings {
        Objects.requireNonNull(variant, "variant");
        displayName = requireText(displayName, "display-name");
        modelKey = requireText(modelKey, "model-key");
        animation = animation == null || animation.isBlank() ? null : animation.strip();
        menuTitle = menuTitle == null || menuTitle.isBlank() ? displayName : menuTitle;
        trades = List.copyOf(trades == null ? List.of() : trades);
        offers = List.copyOf(offers == null ? List.of() : offers);
        if (offers.size() > 5) {
            throw new IllegalArgumentException(variant.id() + " supports at most 5 offers");
        }
        if (trades.size() > 45) {
            throw new IllegalArgumentException(variant.id() + " supports at most 45 trades");
        }
        if (variant == DonPolloVariant.GORDITO && trades.isEmpty()) {
            throw new IllegalArgumentException(variant.id() + " needs at least one trade");
        }
        if (variant == DonPolloVariant.AURA_67 && offers.isEmpty()) {
            throw new IllegalArgumentException(variant.id() + " needs at least one offer");
        }
        if (speed <= 0 || speed > 3) {
            throw new IllegalArgumentException(variant.id() + ".speed tiene que estar entre 0 y 3");
        }
    }

    private static String requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " cannot be blank");
        }
        return value.strip();
    }
}
