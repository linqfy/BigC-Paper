package dev.linqfy.bigCasares.modules.alexscaves.core.item;

/** Exposes the package-private recipe-key rule to tests in other packages. */
public final class AlexsCavesCraftGuardTestAccess {
    private AlexsCavesCraftGuardTestAccess() {
    }

    public static boolean isModuleRecipeKey(String namespace, String key) {
        return AlexsCavesCraftGuard.isModuleRecipeKey(namespace, key);
    }
}
