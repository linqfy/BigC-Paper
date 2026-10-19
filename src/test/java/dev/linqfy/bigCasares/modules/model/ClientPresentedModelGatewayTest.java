package dev.linqfy.bigCasares.modules.model;

import dev.linqfy.bigCasares.platform.ClientEntityPresentationRegistry;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Wolf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPresentedModelGatewayTest {

    @Test
    void aDressedBodyIsRecordedUnderItsModelUntilTheModelCloses() {
        Entity wolf = entity(Wolf.class, EntityType.WOLF, false);
        ClientPresentedModelGateway models = new ClientPresentedModelGateway(new NoModels(), new NoStandIns());

        JavaModelHandle handle = models.attach(wolf, "the_sift_blub");

        assertEquals(new ClientEntityPresentationRegistry.Presentation("WOLF", Set.of("the_sift_blub")),
            ClientEntityPresentationRegistry.find(wolf.getUniqueId()).orElseThrow());
        models.close(handle);
        assertTrue(ClientEntityPresentationRegistry.find(wolf.getUniqueId()).isEmpty());
    }

    @Test
    void aBodyIsSentAgainOnlyAfterItIsRecorded() {
        Entity wolf = entity(Wolf.class, EntityType.WOLF, false);
        List<Boolean> recordedWhenRespawned = new ArrayList<>();
        ClientPresentedModelGateway models = new ClientPresentedModelGateway(new NoModels(), new NoStandIns() {
            @Override
            public void respawn(Entity body) {
                recordedWhenRespawned.add(ClientEntityPresentationRegistry.find(body.getUniqueId()).isPresent());
            }
        });

        JavaModelHandle handle = models.attach(wolf, "the_sift_blub");
        models.attach(wolf, "the_sift_blub");

        assertEquals(List.of(true), recordedWhenRespawned, "sent once, after recording; a repeated attach sends nothing");
        models.close(handle);
    }

    @Test
    void swappingModelsKeepsTheNewOneRecorded() {
        Entity wolf = entity(Wolf.class, EntityType.WOLF, false);
        ClientPresentedModelGateway models = new ClientPresentedModelGateway(new NoModels(), new NoStandIns());
        JavaModelHandle wild = models.attach(wolf, "the_sift_blub");

        JavaModelHandle tamed = models.attach(wolf, "the_sift_blub_tamed");
        models.close(wild);

        assertEquals(Set.of("the_sift_blub_tamed"),
            ClientEntityPresentationRegistry.find(wolf.getUniqueId()).orElseThrow().markerKeys());
        models.close(tamed);
    }

    @Test
    void aModelOnAnInvisibleAnchorIsRecordedOnItsStandIn() {
        Entity anchor = entity(ArmorStand.class, EntityType.ARMOR_STAND, true);
        Entity standIn = entity(ArmorStand.class, EntityType.ARMOR_STAND, false);
        List<Entity> closed = new ArrayList<>();
        List<Entity> respawned = new ArrayList<>();
        ClientPresentedModelGateway models = new ClientPresentedModelGateway(new NoModels(),
            new ClientPresentedModelGateway.OtherClients() {
                @Override
                public Optional<Entity> openStandIn(Entity invisible) {
                    return Optional.of(standIn);
                }

                @Override
                public void closeStandIn(Entity entity) {
                    closed.add(entity);
                }

                @Override
                public void respawn(Entity body) {
                    respawned.add(body);
                }
            });

        JavaModelHandle handle = models.attach(anchor, "the_sift_rift");

        assertEquals(List.of(standIn), respawned, "the stand-in is shown once it is recorded");
        assertTrue(ClientEntityPresentationRegistry.find(anchor.getUniqueId()).isEmpty());
        assertEquals(Set.of("the_sift_rift"),
            ClientEntityPresentationRegistry.find(standIn.getUniqueId()).orElseThrow().markerKeys());
        models.close(handle);
        assertEquals(List.of(standIn), closed);
        assertTrue(ClientEntityPresentationRegistry.find(standIn.getUniqueId()).isEmpty());
    }

    private static Entity entity(Class<? extends Entity> type, EntityType entityType, boolean invisible) {
        UUID id = UUID.randomUUID();
        return (Entity) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getType" -> entityType;
                case "isInvisible" -> invisible;
                case "toString" -> entityType + ":" + id;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static class NoStandIns implements ClientPresentedModelGateway.OtherClients {
        @Override
        public Optional<Entity> openStandIn(Entity anchor) {
            return Optional.empty();
        }

        @Override
        public void closeStandIn(Entity standIn) {
        }

        @Override
        public void respawn(Entity body) {
        }
    }

    private static final class NoModels implements JavaModelGateway {
        @Override
        public JavaModelHandle attach(Entity anchor, String modelKey) {
            return new JavaModelHandle(anchor.getUniqueId(), modelKey);
        }

        @Override
        public boolean animate(JavaModelHandle handle, String animationKey) {
            return true;
        }

        @Override
        public boolean animateOnce(JavaModelHandle handle, String animationKey, Runnable onEnd) {
            return true;
        }

        @Override
        public boolean scale(JavaModelHandle handle, float factor) {
            return true;
        }

        @Override
        public void close(JavaModelHandle handle) {
        }
    }
}
