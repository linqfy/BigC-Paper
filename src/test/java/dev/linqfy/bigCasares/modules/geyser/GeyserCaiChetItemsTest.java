package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.modules.caichet.CaiChetBedrockItems;
import dev.linqfy.bigCasares.modules.caichet.HotelLoot;
import dev.linqfy.bigCasares.modules.resourcepack.ResourcePackAsset;
import dev.linqfy.bigCasares.modules.resourcepack.ResourcePackRegistry;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeyserCaiChetItemsTest {

    @Test
    void everyCaiChetItemBecomesABedrockItemOnTopOfItsVanillaItem() {
        List<GeyserCustomItemDefinition> definitions = GeyserCaiChetItems.definitions(CaiChetBedrockItems.all());
        assertEquals(6, definitions.size());
        for (GeyserCustomItemDefinition definition : definitions) {
            assertTrue(definition.javaBaseIdentifier().startsWith("minecraft:"), definition.javaBaseIdentifier());
            assertTrue(definition.javaModelIdentifier().startsWith("caichet:"), definition.javaModelIdentifier());
            assertEquals(definition.javaModelIdentifier(), definition.bedrockIdentifier());
            assertFalse(definition.bedrockIdentifier().startsWith("minecraft:"), "Geyser rejects minecraft: ids");
        }
        assertEquals(List.of("minecraft:stick", "minecraft:paper", "minecraft:paper", "minecraft:paper",
                "minecraft:clock", "minecraft:totem_of_undying"),
            definitions.stream().map(GeyserCustomItemDefinition::javaBaseIdentifier).toList());
    }

    @Test
    void bottleAndJarCarryTheirDrinkTimeAndNothingElseIsConsumed() {
        Map<String, GeyserCustomItemDefinition> byModel = GeyserCaiChetItems.definitions(CaiChetBedrockItems.all())
            .stream().collect(Collectors.toMap(GeyserCustomItemDefinition::javaModelIdentifier, Function.identity()));

        assertEquals(new GeyserItemConsumption(1.6f, GeyserItemConsumption.Animation.DRINK),
            byModel.get("caichet:botella").consumption());
        assertEquals(new GeyserItemConsumption(2.4f, GeyserItemConsumption.Animation.DRINK),
            byModel.get("caichet:jarra").consumption());
        byModel.forEach((model, definition) -> {
            if (!model.equals("caichet:botella") && !model.equals("caichet:jarra")) {
                assertNull(definition.consumption(), model);
            }
        });
    }

    @Test
    void drinkTimesAreTheOnesHotelLootUsesForTheJavaConsumable() {
        Map<String, Long> millis = CaiChetBedrockItems.all().stream()
            .collect(Collectors.toMap(CaiChetBedrockItems.Item::itemModel, CaiChetBedrockItems.Item::drinkMillis));

        assertEquals(HotelLoot.BOTTLE_DRINK_MS, millis.get("caichet:botella"));
        assertEquals(HotelLoot.JAR_DRINK_MS, millis.get("caichet:jarra"));
    }

    @Test
    void javaDrinksCarryTheConsumableAndNoFoodComponentThatGeyserWouldTranslateDifferently() throws IOException {
        CompiledClass loot = CompiledClass.of(getClass(), HotelLoot.class.getName());

        assertTrue(loot.referencedClasses().contains("io/papermc/paper/datacomponent/item/Consumable"));
        assertTrue(loot.calls("io/papermc/paper/datacomponent/item/Consumable$Builder", "consumeSeconds"));
        assertTrue(loot.mentionsType("io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation"));
        // Geyser sends Bedrock a food component of its own (nutrition 0, can always eat) when the item has none
        assertFalse(loot.mentionsType("io.papermc.paper.datacomponent.item.FoodProperties"));
    }

        @Test
    void eachIconKeyComesFromARegistryEntryWithItsTexture() throws IOException {
        Map<String, ResourcePackAsset> registry = ResourcePackRegistry.load(Path.of("resourcepack/shared/registry.yml"))
            .assets().stream().collect(Collectors.toMap(ResourcePackAsset::id, Function.identity()));
        for (CaiChetBedrockItems.Item item : CaiChetBedrockItems.all()) {
            String id = item.itemModel().substring("caichet:".length());
            ResourcePackAsset asset = registry.get("caichet-" + id.replace('_', '-'));
            assertNotNull(asset, "falta la entrada del registro de " + id);
            assertEquals("item-model", asset.type());
            assertEquals("caichet:item/" + id, asset.javaModel());
            // la clave que el pack Bedrock genera para esa entrada es la que usa el item como icono
            assertEquals("bigcasares." + asset.id().replace('-', '_'), item.icon());
            assertTrue(Files.isRegularFile(Path.of("resourcepack/shared/textures", asset.texturePath() + ".png")),
                asset.texturePath() + " existe");
            // la definicion Java que genera el pack coincide con la que ya trae el modulo
            assertTrue(Files.isRegularFile(Path.of("resourcepack/java/assets/caichet/items", id + ".json")), id);
        }
    }

    @Test
    void geyserModuleRegistersTheCaiChetItemsThroughTheFactory() throws IOException {
        CompiledClass module = CompiledClass.of(getClass(), GeyserIntegrationModule.class.getName());
        assertTrue(module.calls("dev/linqfy/bigCasares/modules/geyser/GeyserCaiChetItems", "definitions"));
        assertTrue(module.calls("dev/linqfy/bigCasares/modules/caichet/CaiChetBedrockItems", "all"));
    }
}
