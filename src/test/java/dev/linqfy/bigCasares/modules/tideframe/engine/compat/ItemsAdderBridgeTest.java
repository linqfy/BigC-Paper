package dev.linqfy.bigCasares.modules.tideframe.engine.compat;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Own fixtures test a reflection contract, not an installed ItemsAdder plugin. */
public class ItemsAdderBridgeTest {
    private static final String API = "dev.lone.itemsadder.api.CustomStack";

    @Test void resolvesTheOfficialMethodNamesAndReturnsTheOriginalItemObject() {
        var bridge = new ItemsAdderBridge(name -> name.equals(API) ? CustomStackFixture.class : null);
        assertSame(CustomStackFixture.ITEM, bridge.item("tideframe:ui/button.v1").orElseThrow());
        assertEquals("tideframe:ui/button.v1", CustomStackFixture.received);
    }

    @Test void providerLookupReturningNullMeansUnknownItem() {
        assertTrue(new ItemsAdderBridge(name -> CustomStackFixture.class).item("tideframe:missing").isEmpty());
    }

    @Test void providerReturningNullItemMeansUnavailableItem() {
        assertTrue(new ItemsAdderBridge(name -> NullItemFixture.class).item("tideframe:button").isEmpty());
    }

    @Test void rejectsMalformedIdsBeforeLookingForAProvider() {
        var resolutions = new AtomicInteger();
        var bridge = new ItemsAdderBridge(name -> { resolutions.incrementAndGet(); return CustomStackFixture.class; });
        String[] invalid = {null, "", "button", "Tideframe:button", "tideframe:", ":button", "tideframe:space here",
                "tideframe:../button", "tideframe:ui/../button", "tideframe:ui/..", "tideframe:a:b", "tideframe:\\button",
                "tideframe:button\n", "tideframe:" + "a".repeat(119)};
        for (String id : invalid) assertEquals(Optional.empty(), bridge.item(id), "Invalid ID: " + id);
        assertEquals(0, resolutions.get());
    }

    @Test void acceptsTheMaximumLengthNamespacedId() {
        assertTrue(new ItemsAdderBridge(name -> CustomStackFixture.class)
                .item("tideframe:" + "a".repeat(118)).isPresent());
    }

    @Test void missingClassAndLinkageFailureAreSafe() {
        assertTrue(new ItemsAdderBridge(name -> null).item("tideframe:button").isEmpty());
        assertTrue(new ItemsAdderBridge(name -> { throw new NoClassDefFoundError("optional dependency"); })
                .item("tideframe:button").isEmpty());
        assertTrue(new ItemsAdderBridge(name -> { throw new IllegalStateException("unavailable loader"); })
                .item("tideframe:button").isEmpty());
    }

    @Test void missingMethodAndProviderExceptionsAreSafe() {
        assertTrue(new ItemsAdderBridge(name -> Object.class).item("tideframe:button").isEmpty());
        assertTrue(new ItemsAdderBridge(name -> ThrowingLookupFixture.class).item("tideframe:button").isEmpty());
        assertTrue(new ItemsAdderBridge(name -> ThrowingItemFixture.class).item("tideframe:button").isEmpty());
    }

    @Test void resolvesAgainAfterProviderRegistryChanges() {
        AtomicInteger lookups = new AtomicInteger();
        var bridge = new ItemsAdderBridge(name -> lookups.incrementAndGet() == 1 ? null : CustomStackFixture.class);
        assertTrue(bridge.item("tideframe:button").isEmpty());
        assertTrue(bridge.item("tideframe:button").isPresent());
        assertEquals(2, lookups.get());
    }

    public static class CustomStackFixture {
        static final Object ITEM = new Object();
        static String received;
        public static CustomStackFixture getInstance(String id) {
            received = id;
            return id.endsWith(":missing") ? null : new CustomStackFixture();
        }
        public Object getItemStack() { return ITEM; }
    }
    public static class NullItemFixture {
        public static NullItemFixture getInstance(String id) { return new NullItemFixture(); }
        public Object getItemStack() { return null; }
    }
    public static class ThrowingLookupFixture {
        public static ThrowingLookupFixture getInstance(String id) { throw new IllegalStateException("not loaded"); }
        public Object getItemStack() { return new Object(); }
    }
    public static class ThrowingItemFixture {
        public static ThrowingItemFixture getInstance(String id) { return new ThrowingItemFixture(); }
        public Object getItemStack() { throw new IllegalStateException("registry reload"); }
    }
}
