package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.platform.ClientEntityPresentationRegistry;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.event.PostOrder;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.block.custom.component.TransformationComponent;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class GeyserRuntimeBridge implements GeyserApiFacade, EventRegistrar {
    private final GeyserApi api;
    private final GeyserSettings settings;
    private final GeyserFormResponseDispatcher formResponses;
    private final GeyserSubscriptionLifecycle subscriptions;
    private final List<GeyserCustomEntityDefinition> customEntities = new CopyOnWriteArrayList<>();
    private final List<GeyserCustomItemDefinition> customItems = new CopyOnWriteArrayList<>();
    private final List<GeyserCustomBlockDefinition> customBlocks = new CopyOnWriteArrayList<>();
    private final Map<String, CustomEntityDefinition> registeredEntities = new ConcurrentHashMap<>();
    private volatile Path resourcePack;

    public GeyserRuntimeBridge(GeyserSettings settings, Consumer<Runnable> mainThreadExecutor) {
        this.api = GeyserApi.api();
        this.settings = settings;
        this.formResponses = new GeyserFormResponseDispatcher(mainThreadExecutor);
        // Subscribed one by one instead of registering annotated handlers: Floodgate ships its own unrelocated
        // copy of org.geysermc.event, and when this plugin resolves @Subscribe from it Geyser ignores every annotated
        // method, so nothing (pack, items, entities) ever reached Geyser.
        this.api.eventBus().subscribe(this, GeyserDefineResourcePacksEvent.class, this::onDefineResourcePacks);
        this.api.eventBus().subscribe(this, GeyserDefineEntitiesEvent.class, this::onDefineCustomEntities);
        this.api.eventBus().subscribe(this, GeyserDefineCustomItemsEvent.class, this::onDefineCustomItems);
        subscribeCustomBlocksLast();
        this.api.eventBus().subscribe(this, ServerSpawnEntityEvent.class, this::onServerSpawnEntity);
        this.subscriptions = new GeyserSubscriptionLifecycle(() -> this.api.eventBus().unregisterAll(this));
    }

    /**
     * Geyser defines its own Bedrock blocks for every mushroom-block state (Bedrock's mushroom blocks cannot show
     * them all), from a handler it subscribes when it starts, after this plugin. For the same Java state the
     * override registered last wins, so the content blocks subscribe last. A Floodgate copy of the event classes
     * can make the ordered call unlinkable; then the content blocks still register, in subscription order.
     */
    private void subscribeCustomBlocksLast() {
        try {
            this.api.eventBus().subscribe(this, GeyserDefineCustomBlocksEvent.class, this::onDefineCustomBlocks,
                PostOrder.LAST);
        } catch (LinkageError unordered) {
            this.api.eventBus().subscribe(this, GeyserDefineCustomBlocksEvent.class, this::onDefineCustomBlocks);
        }
    }

    @Override
    public boolean isBedrockPlayer(UUID playerId) {
        return api.isBedrockPlayer(playerId);
    }

    @Override
    public GeyserCapabilities capabilities() {
        return new GeyserCapabilities(
            true,
            settings.bedrockResourcePack(),
            settings.customItems(),
            settings.bedrockForms(),
            settings.customEntities()
        );
    }

    @Override
    public boolean sendForm(UUID playerId, BedrockShopForm form) {
        if (subscriptions.isClosed() || !settings.bedrockForms() || !isBedrockPlayer(playerId)) {
            return false;
        }
        SimpleForm.Builder builder = SimpleForm.builder()
            .title(form.title())
            .content(form.content());
        for (BedrockShopForm.Button button : form.buttons()) {
            if (button.iconUrl() == null || button.iconUrl().isBlank()) {
                builder.button(button.text());
            } else {
                builder.button(button.text(), FormImage.Type.URL, button.iconUrl());
            }
        }
        builder.validResultHandler(response ->
            formResponses.dispatch(form, playerId, response.clickedButtonId()));
        return api.sendForm(playerId, builder.build());
    }

    @Override
    public void close() {
        if (!subscriptions.close()) {
            return;
        }
        customEntities.clear();
        customItems.clear();
        customBlocks.clear();
        registeredEntities.clear();
        resourcePack = null;
    }

    @Override
    public boolean registerResourcePack(Path pack) {
        if (!settings.bedrockResourcePack() || !Files.isRegularFile(pack)) {
            return false;
        }
        this.resourcePack = pack.toAbsolutePath().normalize();
        return true;
    }

    @Override
    public boolean registerCustomItem(GeyserCustomItemDefinition definition) {
        if (!settings.customItems()) {
            return false;
        }
        customItems.add(definition);
        return true;
    }

    @Override
    public boolean registerCustomEntity(GeyserCustomEntityDefinition definition) {
        if (!settings.customEntities()) {
            return false;
        }
        customEntities.add(definition);
        return true;
    }

    @Override
    public boolean registerCustomBlock(GeyserCustomBlockDefinition definition) {
        if (!settings.customBlocks()) {
            return false;
        }
        customBlocks.add(definition);
        return true;
    }

    public void onDefineResourcePacks(GeyserDefineResourcePacksEvent event) {
        Path pack = resourcePack;
        if (pack != null && Files.isRegularFile(pack)) {
            event.register(ResourcePack.create(PackCodec.path(pack)));
        }
    }

    public void onDefineCustomEntities(GeyserDefineEntitiesEvent event) {
        registeredEntities.clear();
        for (GeyserCustomEntityDefinition definition : customEntities) {
            // Several Java bodies may share one Bedrock entity (the Blub's wild and tamed models); it is defined once.
            if (registeredEntities.containsKey(definition.bedrockIdentifier())) {
                continue;
            }
            CustomEntityDefinition geyserDefinition = CustomEntityDefinition.of(
                Identifier.of(definition.bedrockIdentifier()));
            event.register(geyserDefinition);
            registeredEntities.put(definition.bedrockIdentifier(), geyserDefinition);
        }
    }

    public void onDefineCustomItems(GeyserDefineCustomItemsEvent event) {
        for (GeyserCustomItemDefinition definition : customItems) {
            CustomItemDefinition geyserDefinition = CustomItemDefinition.builder(
                    Identifier.of(definition.bedrockIdentifier()),
                    Identifier.of(definition.javaModelIdentifier()))
                .displayName(definition.displayName())
                .bedrockOptions(CustomItemBedrockOptions.builder()
                    .icon(definition.icon())
                    .allowOffhand(true))
                .build();
            event.register(Identifier.of(definition.javaBaseIdentifier()), geyserDefinition);
        }
    }

    public void onDefineCustomBlocks(GeyserDefineCustomBlocksEvent event) {
        for (GeyserCustomBlockDefinition definition : customBlocks) {
            CustomBlockComponents.Builder components = CustomBlockComponents.builder()
                .displayName(definition.displayName());
            definition.faces().forEach((face, texture) -> components.materialInstance(face, MaterialInstance.builder()
                .texture(texture)
                .renderMethod(definition.renderMethod())
                .build()));
            List<Integer> rotation = definition.rotation();
            if (rotation.stream().anyMatch(angle -> angle != 0)) {
                components.transformation(new TransformationComponent(rotation.get(0), rotation.get(1), rotation.get(2)));
            }
            CustomBlockData block = CustomBlockData.builder()
                .name(definition.name())
                .includedInCreativeInventory(false)
                .components(components.build())
                .build();
            event.register(block);
            event.registerOverride(definition.javaState(), block.defaultBlockState());
        }
    }

    public void onServerSpawnEntity(ServerSpawnEntityEvent event) {
        ClientEntityPresentationRegistry.find(event.uuid()).ifPresent(presentation ->
            customEntities.stream()
                .filter(definition -> definition.matches(
                    presentation.javaEntityType(), presentation.markerKeys()))
                .map(definition -> registeredEntities.get(definition.bedrockIdentifier()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .ifPresent(event::definition)
        );
    }
}
