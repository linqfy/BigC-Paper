package dev.linqfy.bigCasares.modules.model;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterModelJavaModelGatewayTest {

    private final Entity anchor = entity(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

    @Test
    void attachRecordsTheAnchorAndStableModelKey() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);

        JavaModelHandle handle = gateway.attach(anchor, JavaModelKeys.NEXUS);

        assertEquals(anchor.getUniqueId(), handle.anchorId());
        assertEquals(JavaModelKeys.NEXUS, handle.modelKey());
        assertEquals(List.of("attach:bigcasares_nexus"), port.events());
    }

    @Test
    void closeStopsTheTrackerExactlyOnce() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle handle = gateway.attach(anchor, JavaModelKeys.NEXUS);

        gateway.close(handle);
        gateway.close(handle);

        assertEquals(List.of("attach:bigcasares_nexus", "close:bigcasares_nexus"), port.events());
    }

    @Test
    void animateForwardsToTheAttachedTracker() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle handle = gateway.attach(anchor, JavaModelKeys.NEXUS);

        assertTrue(gateway.animate(handle, "idle"));

        assertEquals(List.of("attach:bigcasares_nexus", "animate:idle"), port.events());
    }

    @Test
    void animateOnceForwardsOneShotWithEndCallback() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle handle = gateway.attach(anchor, JavaModelKeys.NEXUS);

        assertTrue(gateway.animateOnce(handle, "shoot", () -> { }));

        assertEquals(List.of("attach:bigcasares_nexus", "animateOnce:shoot"), port.events());
    }

    @Test
    void animateOnceRunsEndCallbackWhenTheOneShotFinishes() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle handle = gateway.attach(anchor, JavaModelKeys.NEXUS);
        var fired = new boolean[] { false };

        gateway.animateOnce(handle, "reload", () -> fired[0] = true);
        port.finishAnimation("reload");

        assertTrue(fired[0], "end callback must fire when the one-shot ends");
    }

    @Test
    void animateOnceOnUnknownOrClosedHandleReturnsFalse() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle unknown = new JavaModelHandle(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                JavaModelKeys.NEXUS);

        assertFalse(gateway.animateOnce(unknown, "shoot", () -> { }));
    }

    @Test
    void scaleForwardsToTheAttachedTracker() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle handle = gateway.attach(anchor, JavaModelKeys.NEXUS);

        assertTrue(gateway.scale(handle, 0.2f));

        assertEquals(List.of("attach:bigcasares_nexus", "scale:0.2"), port.events());
    }

    @Test
    void scaleOnUnknownOrClosedHandleReturnsFalse() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle unknown = new JavaModelHandle(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                JavaModelKeys.NEXUS);

        assertFalse(gateway.scale(unknown, 0.2f));
    }

    @Test
    void brightenForwardsTheLevelsAndBonesToTheAttachedTracker() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle handle = gateway.attach(anchor, JavaModelKeys.NEXUS);

        assertTrue(gateway.brighten(handle, 15, 14, List.of("head")));

        assertEquals(List.of("attach:bigcasares_nexus", "brighten:15/14:[head]"), port.events());
    }

    @Test
    void brightenOnUnknownClosedOrUnsupportedHandleReturnsFalse() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle unknown = new JavaModelHandle(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                JavaModelKeys.NEXUS);
        assertFalse(gateway.brighten(unknown, 15, 15, List.of("head")));

        JavaModelHandle attached = gateway.attach(anchor, JavaModelKeys.NEXUS);
        gateway.close(attached);
        assertFalse(gateway.brighten(attached, 15, 15, List.of("head")));

        // A tracker session that does not override brighten (the interface default) cannot brighten.
        TrackerSession plain = new TrackerSession() {
            @Override
            public boolean animate(String animationKey) {
                return true;
            }

            @Override
            public boolean animateOnce(String animationKey, Runnable onEnd) {
                return true;
            }

            @Override
            public boolean scale(float factor) {
                return true;
            }

            @Override
            public void close() {
            }
        };
        assertFalse(plain.brighten(15, 15, List.of("head")));
    }

    @Test
    void duplicateAttachReusesOneTrackerSession() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);

        gateway.attach(anchor, JavaModelKeys.NEXUS);
        gateway.attach(anchor, JavaModelKeys.NEXUS);

        assertEquals(List.of("attach:bigcasares_nexus"), port.events());
    }

    @Test
    void reattachAfterCloseCreatesAFreshTrackerSession() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle first = gateway.attach(anchor, JavaModelKeys.NEXUS);

        gateway.close(first);
        gateway.attach(anchor, JavaModelKeys.NEXUS);

        assertEquals(List.of(
                "attach:bigcasares_nexus",
                "close:bigcasares_nexus",
                "attach:bigcasares_nexus"
        ), port.events());
    }

    @Test
    void animateOnUnknownOrClosedHandleReturnsFalse() {
        var port = new RecordingTrackerPort();
        JavaModelGateway gateway = new BetterModelJavaModelGateway(port);
        JavaModelHandle unknown = new JavaModelHandle(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                JavaModelKeys.NEXUS);

        assertFalse(gateway.animate(unknown, "idle"));

        JavaModelHandle attached = gateway.attach(anchor, JavaModelKeys.NEXUS);
        gateway.close(attached);

        assertFalse(gateway.animate(attached, "idle"));
    }

    @Test
    void productionTrackerPortDelegatesAttachmentToOwnedTrackerCreator() {
        var events = new ArrayList<String>();
        TrackerPort port = new BetterModelTrackerPort((requestedAnchor, modelKey) -> {
            events.add("create:" + modelKey);
            return new TrackerSession() {
                @Override
                public boolean animate(String animationKey) {
                    return true;
                }

                @Override
                public boolean animateOnce(String animationKey, Runnable onEnd) {
                    return true;
                }

                @Override
                public boolean scale(float factor) {
                    return true;
                }

                @Override
                public void close() {
                }
            };
        });

        port.attach(anchor, JavaModelKeys.NEXUS);

        assertEquals(List.of("create:bigcasares_nexus"), events);
    }

    @Test
    void factoryRejectsDisabledBetterModel() {
        Plugin disabledBetterModel = (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isEnabled" -> false;
                    case "getName" -> "BetterModel";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        var error = assertThrows(IllegalStateException.class,
                () -> JavaModelGatewayFactory.create(disabledBetterModel));

        assertEquals("BetterModel is required. Install BetterModel 3.2.0 before starting BigCasares.",
                error.getMessage());
    }

    private static Entity entity(UUID uniqueId) {
        return (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> uniqueId;
                    case "toString" -> "test-entity:" + uniqueId;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class RecordingTrackerPort implements TrackerPort {
        private final List<String> events = new ArrayList<>();
        private final java.util.Map<String, List<Runnable>> endCallbacks = new java.util.HashMap<>();

        @Override
        public TrackerSession attach(Entity anchor, String modelKey) {
            events.add("attach:" + modelKey);
            return new TrackerSession() {
                @Override
                public boolean animate(String animationKey) {
                    events.add("animate:" + animationKey);
                    return true;
                }

                @Override
                public boolean animateOnce(String animationKey, Runnable onEnd) {
                    events.add("animateOnce:" + animationKey);
                    endCallbacks.computeIfAbsent(animationKey, ignored -> new ArrayList<>()).add(onEnd);
                    return true;
                }

                @Override
                public boolean scale(float factor) {
                    events.add("scale:" + factor);
                    return true;
                }

                @Override
                public boolean brighten(int block, int sky, java.util.Collection<String> bones) {
                    events.add("brighten:" + block + "/" + sky + ":" + new java.util.TreeSet<>(bones));
                    return true;
                }

                @Override
                public void close() {
                    events.add("close:" + modelKey);
                }
            };
        }

        void finishAnimation(String animationKey) {
            List<Runnable> callbacks = endCallbacks.remove(animationKey);
            if (callbacks != null) {
                for (Runnable callback : callbacks) {
                    callback.run();
                }
            }
        }

        List<String> events() {
            return List.copyOf(events);
        }
    }
}
