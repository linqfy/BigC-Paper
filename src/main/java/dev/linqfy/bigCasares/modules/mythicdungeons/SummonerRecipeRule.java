package dev.linqfy.bigCasares.modules.mythicdungeons;

import java.util.List;
import java.util.Optional;

/**
 * La regla del crafteo del Invocador (sin servidor): exactamente cuatro Fragmentos de Odio del mismo jefe y nada mas,
 * en cualquier lugar de la mesa o del 2x2 del inventario (la cantidad de cada casilla no importa: se gasta uno de
 * cada una). Los fragmentos se reconocen por su id de catalogo, no por igualdad exacta del item: la receta del
 * catalogo ({@code boss_summoner_<cultura>.yml}) usa ingredientes exactos que dejan de coincidir con fragmentos
 * creados con otra revision del catalogo, asi que el modulo suma una receta propia por material y esta regla decide
 * el resultado en la mesa ({@link #decide}).
 */
public final class SummonerRecipeRule {

    public static final int FRAGMENTS_PER_SUMMONER = 4;

    /** Que hace la mesa con el resultado que propone el servidor. */
    public enum Action {
        /** Lo deja como esta. */
        KEEP,
        /** No sale nada. */
        CLEAR,
        /** Sale el Invocador de {@link Decision#culture()}. */
        SET
    }

    /** La decision para una mesa. */
    public record Decision(Action action, Optional<MythicCulture> culture) {

        static final Decision KEEP = new Decision(Action.KEEP, Optional.empty());
        static final Decision CLEAR = new Decision(Action.CLEAR, Optional.empty());

        static Decision set(MythicCulture culture) {
            return new Decision(Action.SET, Optional.of(culture));
        }
    }

    private SummonerRecipeRule() {
    }

    /**
     * @param gridItemIds el id de catalogo de cada casilla ocupada de la mesa; {@code null} (o vacio) para un item
     *                    que no es del catalogo
     * @return la cultura cuyo Invocador sale, si la mesa cumple la regla
     */
    public static Optional<MythicCulture> result(List<String> gridItemIds) {
        if (gridItemIds == null || gridItemIds.size() != FRAGMENTS_PER_SUMMONER) {
            return Optional.empty();
        }
        MythicCulture culture = null;
        for (String itemId : gridItemIds) {
            Optional<MythicCulture> fragment = MythicCulture.byFragmentItemId(itemId);
            if (fragment.isEmpty() || (culture != null && culture != fragment.get())) {
                return Optional.empty();
            }
            culture = fragment.get();
        }
        return Optional.ofNullable(culture);
    }

    /**
     * Que sale de la mesa.
     *
     * @param gridItemIds   el id de catalogo de cada casilla ocupada ({@code null} si no es del catalogo)
     * @param resultSummoner la cultura del Invocador que propone la receta que encontro el servidor, si propone uno
     * @param moduleRecipe  si la receta que encontro el servidor es la propia del modulo (por material)
     */
    public static Decision decide(List<String> gridItemIds, Optional<MythicCulture> resultSummoner,
                                  boolean moduleRecipe) {
        Optional<MythicCulture> wanted = result(gridItemIds);
        if (resultSummoner.isPresent() || moduleRecipe) {
            if (wanted.isEmpty()) {
                return Decision.CLEAR;
            }
            // la receta del catalogo ya da el Invocador correcto; la propia siempre se reemplaza por uno nuevo
            return !moduleRecipe && resultSummoner.equals(wanted) ? Decision.KEEP : Decision.set(wanted.get());
        }
        boolean mythicIngredient = gridItemIds != null
            && gridItemIds.stream().anyMatch(id -> id != null && isMythicItem(id));
        // un Fragmento o un Invocador nunca cuenta como el item vanilla de base en otra receta
        return mythicIngredient ? Decision.CLEAR : Decision.KEEP;
    }

    /** Si el id es uno de los items del modulo (fragmento o invocador). */
    public static boolean isMythicItem(String itemId) {
        return MythicCulture.byFragmentItemId(itemId).isPresent()
            || MythicCulture.bySummonerItemId(itemId).isPresent();
    }
}
