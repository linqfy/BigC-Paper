package dev.linqfy.bigCasares.modules.donpollos;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DonPollosSettings(
    Map<DonPolloVariant, DonPollosVariantSettings> variants,
    DonPollosRoulette roulette,
    double salseroFollowRadius,
    double salseroStopDistance,
    long salseroUpdateTicks,
    List<String> salseroDances,
    DonPollosBattleSettings battle,
    DonPollosCitySettings cities,
    DonPollosBossSettings boss
) {

    public DonPollosSettings {
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(roulette, "roulette");
        Objects.requireNonNull(battle, "battle");
        Objects.requireNonNull(cities, "cities");
        Objects.requireNonNull(boss, "boss");
        for (DonPolloVariant variant : DonPolloVariant.values()) {
            if (!variants.containsKey(variant)) {
                throw new IllegalArgumentException("Missing Don Pollo variant: " + variant.id());
            }
        }
        variants = Map.copyOf(new EnumMap<>(variants));
        if (salseroUpdateTicks < 1) {
            throw new IllegalArgumentException("salsero.update-ticks must be positive");
        }
        new DonPollosSalseroPolicy(salseroFollowRadius, salseroStopDistance);
        salseroDances = List.copyOf(salseroDances == null ? List.of() : salseroDances);
    }

    public DonPollosVariantSettings variant(DonPolloVariant variant) {
        return variants.get(variant);
    }
}
