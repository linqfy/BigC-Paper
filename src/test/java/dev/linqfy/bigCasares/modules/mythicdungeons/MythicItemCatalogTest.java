package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.items.catalog.CustomItemCatalog;
import dev.linqfy.bigCasares.items.catalog.CustomItemDefinition;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogLoader;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogValidator;
import dev.linqfy.bigCasares.items.catalog.ItemRecipeDefinition;
import dev.linqfy.bigCasares.items.catalog.RecipeType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los Fragmentos e Invocadores pasan por el cargador y el validador reales del catalogo de items. */
class MythicItemCatalogTest {

    private static final Path ITEMS = Path.of("src/main/resources/content/items");

    private static CustomItemCatalog catalog() {
        CustomItemCatalog catalog = new ItemCatalogLoader().load(ITEMS);
        new ItemCatalogValidator().validate(catalog, Path.of("resourcepack"));
        return catalog;
    }

    @Test
    void theDevelopedCulturesShipAFragmentAndASummoner() {
        CustomItemCatalog catalog = catalog();
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            String boss = culture.requireBoss().displayName();
            CustomItemDefinition fragment = catalog.require(culture.fragmentItemId().orElseThrow());
            assertEquals("mythic-fragment", fragment.mechanic());
            assertEquals("Fragmento de Odio de " + boss, fragment.display().fallbackName());
            assertTrue(fragment.recipeDefinition().isEmpty());
            assertFalse(fragment.display().lore().isEmpty());

            CustomItemDefinition summoner = catalog.require(culture.summonerItemId().orElseThrow());
            assertEquals("mythic-summoner", summoner.mechanic());
            assertEquals("Invocador de " + boss, summoner.display().fallbackName());
            assertTrue(String.join(" ", summoner.display().lore()).contains("altar"));
        }
    }

    @Test
    void fourExactFragmentsOfTheSameBossCraftTheSummoner() {
        CustomItemCatalog catalog = catalog();
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            ItemRecipeDefinition recipe = catalog.require(culture.summonerItemId().orElseThrow())
                .recipeDefinition().orElseThrow();
            assertEquals(RecipeType.SHAPELESS, recipe.type());
            assertEquals(1, recipe.resultAmount());
            assertEquals(null, recipe.resultMaterial());
            // a catalog reference ("bigcasares:<id>") becomes an exact choice: plain vanilla shards never match
            assertEquals(Collections.nCopies(4, "bigcasares:" + culture.fragmentItemId().orElseThrow()),
                recipe.shapelessIngredients());
        }
    }

    @Test
    void theModuleRecipeCoversEveryFragmentMaterialAndNeverASummonerMaterial() {
        CustomItemCatalog catalog = catalog();
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            CustomItemDefinition fragment = catalog.require(culture.fragmentItemId().orElseThrow());
            CustomItemDefinition summoner = catalog.require(culture.summonerItemId().orElseThrow());
            // el Invocador sale de cuatro casillas con fragmentos apilados de a muchos: se gasta uno de cada una
            assertTrue(fragment.maxStackSize() >= SummonerRecipeRule.FRAGMENTS_PER_SUMMONER, culture.id());
            assertFalse(fragment.material().equalsIgnoreCase(summoner.material()), culture.id());
        }
    }

    @Test
    void namesAreTranslatedInTheJavaPack() throws Exception {
        String spanish = Files.readString(Path.of("resourcepack/java/assets/bigcasares/lang/es_es.json"));
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            assertTrue(spanish.contains("\"item.bigcasares." + culture.fragmentItemId().orElseThrow() + "\""));
            assertTrue(spanish.contains("\"item.bigcasares." + culture.summonerItemId().orElseThrow() + "\""));
        }
    }
}
