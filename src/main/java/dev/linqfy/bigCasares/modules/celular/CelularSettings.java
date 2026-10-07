package dev.linqfy.bigCasares.modules.celular;

public record CelularSettings(boolean recipeEnabled, boolean villageLootEnabled) {

    public static CelularSettings defaults() {
        return new CelularSettings(true, true);
    }
}
