package dev.linqfy.bigCasares.modules.mutango;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Puente con la API de Geyser: le da a Bedrock el pack con los iconos de las Botas de Neguechen y la Cruz Andina y
 * registra los dos items personalizados (se reconocen por el componente item_model que les pone MutangoRelics).
 * Se suscribe evento por evento, sin @Subscribe: con Floodgate instalado Geyser ignora los metodos anotados.
 * Solo se carga si Geyser esta en este servidor (el resto del plugin no lo nombra).
 */
final class MutangoGeyserBridge implements EventRegistrar {

    private final GeyserApi api;
    private final Path pack;

    MutangoGeyserBridge(Path pack) {
        this.api = GeyserApi.api();
        this.pack = pack;
        api.eventBus().subscribe(this, GeyserDefineResourcePacksEvent.class, this::onDefinePacks);
        api.eventBus().subscribe(this, GeyserDefineCustomItemsEvent.class, this::onDefineItems);
    }

    void close() {
        api.eventBus().unregisterAll(this);
    }

    private void onDefinePacks(GeyserDefineResourcePacksEvent event) {
        if (Files.isRegularFile(pack)) {
            event.register(ResourcePack.create(PackCodec.path(pack)));
        }
    }

    private void onDefineItems(GeyserDefineCustomItemsEvent event) {
        register(event, "minecraft:leather_boots", MutangoRelics.BOOTS, "Botas de Neguechen");
        register(event, "minecraft:totem_of_undying", MutangoRelics.CROSS, "Cruz Andina");
    }

    private static void register(GeyserDefineCustomItemsEvent event, String base, String key, String name) {
        CustomItemDefinition definition = CustomItemDefinition.builder(
                Identifier.of("mutango:" + key), Identifier.of("mutango:" + key))
            .displayName(name)
            .bedrockOptions(CustomItemBedrockOptions.builder().icon("mutango." + key).allowOffhand(true))
            .build();
        event.register(Identifier.of(base), definition);
    }
}
