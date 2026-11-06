package dev.linqfy.bigCasares.modules.tideframe.engine.compat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Own fixtures test reflection and fallback only; they do not emulate Bedrock networking. */
public class BedrockBridgeTest {
    private static final String FLOODGATE = "org.geysermc.floodgate.api.FloodgateApi";
    private static final String GEYSER = "org.geysermc.geyser.api.GeyserApi";
    private static final UUID BEDROCK = UUID.fromString("f1cb8b92-ae6e-4784-abac-1aff99d79b3e");
    private static final UUID JAVA = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void recognizesFloodgatePlayerUsingOfficialMethods() {
        var bridge = new BedrockBridge(name -> name.equals(FLOODGATE) ? FloodgateFixture.class : null);
        assertTrue(bridge.isBedrock(BEDROCK));
        assertFalse(bridge.isBedrock(JAVA));
    }

    @Test void recognizesGeyserPlayerWhenFloodgateIsAbsent() {
        var bridge = new BedrockBridge(name -> name.equals(GEYSER) ? GeyserFixture.class : null);
        assertTrue(bridge.isBedrock(BEDROCK));
        assertFalse(bridge.isBedrock(JAVA));
    }

    @Test void triesGeyserAfterFloodgateReturnsFalse() {
        var bridge = new BedrockBridge(name -> name.equals(FLOODGATE) ? FalseFloodgateFixture.class : GeyserFixture.class);
        assertTrue(bridge.isBedrock(BEDROCK));
    }

    @Test void triesGeyserAfterFloodgateThrows() {
        var bridge = new BedrockBridge(name -> name.equals(FLOODGATE) ? ThrowingFloodgateFixture.class : GeyserFixture.class);
        assertTrue(bridge.isBedrock(BEDROCK));
    }

    @Test void successDoesNotRequireTheOtherProviderToBeAvailable() {
        var calls = new AtomicInteger();
        var bridge = new BedrockBridge(name -> {
            calls.incrementAndGet();
            if (name.equals(FLOODGATE)) return FloodgateFixture.class;
            throw new NoClassDefFoundError("no Geyser");
        });
        assertTrue(bridge.isBedrock(BEDROCK));
        assertEquals(1, calls.get());
    }

    @Test void absentProvidersNeverInferIdentityFromUuidPrefix() {
        var bridge = new BedrockBridge(name -> null);
        assertFalse(bridge.isBedrock(JAVA));
        assertFalse(bridge.isBedrock(BEDROCK));
        assertFalse(new BedrockBridge().isBedrock(BEDROCK));
    }

    @Test void nullUuidIsRejectedWithoutResolvingAnyProvider() {
        var calls = new AtomicInteger();
        assertFalse(new BedrockBridge(name -> { calls.incrementAndGet(); return FloodgateFixture.class; }).isBedrock(null));
        assertEquals(0, calls.get());
    }

    @Test void linkageAndMissingMethodsAndNullSingletonAreSafe() {
        assertFalse(new BedrockBridge(name -> { throw new NoClassDefFoundError("optional dependency"); }).isBedrock(BEDROCK));
        assertFalse(new BedrockBridge(name -> { throw new IllegalStateException("unavailable loader"); }).isBedrock(BEDROCK));
        assertFalse(new BedrockBridge(name -> Object.class).isBedrock(BEDROCK));
        assertFalse(new BedrockBridge(name -> NullSingletonFixture.class).isBedrock(BEDROCK));
    }

    @Test void nonBooleanProviderResultDoesNotCountAsDetection() {
        assertFalse(new BedrockBridge(name -> WrongTypeFixture.class).isBedrock(BEDROCK));
    }

    public static class FloodgateFixture {
        public static FloodgateFixture getInstance() { return new FloodgateFixture(); }
        public boolean isFloodgatePlayer(UUID id) { return BEDROCK.equals(id); }
    }
    public static class GeyserFixture {
        public static GeyserFixture api() { return new GeyserFixture(); }
        public boolean isBedrockPlayer(UUID id) { return BEDROCK.equals(id); }
    }
    public static class FalseFloodgateFixture {
        public static FalseFloodgateFixture getInstance() { return new FalseFloodgateFixture(); }
        public boolean isFloodgatePlayer(UUID id) { return false; }
    }
    public static class ThrowingFloodgateFixture {
        public static ThrowingFloodgateFixture getInstance() { throw new IllegalStateException("not ready"); }
        public boolean isFloodgatePlayer(UUID id) { return false; }
    }
    public static class NullSingletonFixture {
        public static NullSingletonFixture getInstance() { return null; }
        public static NullSingletonFixture api() { return null; }
        public boolean isFloodgatePlayer(UUID id) { return true; }
        public boolean isBedrockPlayer(UUID id) { return true; }
    }
    public static class WrongTypeFixture {
        public static WrongTypeFixture getInstance() { return new WrongTypeFixture(); }
        public static WrongTypeFixture api() { return new WrongTypeFixture(); }
        public String isFloodgatePlayer(UUID id) { return "true"; }
        public String isBedrockPlayer(UUID id) { return "true"; }
    }
}
