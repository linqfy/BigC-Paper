package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPlayersTest {
    private static final UUID JAVA = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID FLOODGATE = new UUID(0L, 0x0009_01f2_3a4b_5c6dL);

    /** Stands in for FloodgateApi: knows a fixed set of players. */
    public static final class FakeFloodgate {
        final Set<UUID> bedrock = new HashSet<>();
        int calls;

        public boolean isFloodgatePlayer(UUID id) {
            calls++;
            return bedrock.contains(id);
        }
    }

    @Test
    void geyserLocalChannelIsAlwaysBedrock() {
        assertTrue(BedrockPlayers.isLocalChannelName("io.netty.channel.local.LocalChannel"));
        assertTrue(BedrockPlayers.isLocalChannelName("org.geysermc.relocate.io.netty.channel.local.LocalChannel"));
        assertFalse(BedrockPlayers.isLocalChannelName("io.netty.channel.socket.nio.NioSocketChannel"));
        assertFalse(BedrockPlayers.isLocalChannelName("io.netty.channel.epoll.EpollSocketChannel"));
        assertFalse(BedrockPlayers.isLocalChannelName("io.netty.channel.local.LocalServerChannel"));
        BedrockPlayers none = new BedrockPlayers(null, null, null, null);
        assertFalse(none.isBedrock(new Object(), JAVA), "a plain channel and a Java UUID");
    }

    @Test
    void unknownPlayerIsTreatedAsBedrock() {
        assertTrue(new BedrockPlayers(null, null, null, null).isBedrock(null, null));
    }

    @Test
    void withoutApisTheFloodgateUuidShapeDecides() {
        BedrockPlayers players = new BedrockPlayers(null, null, null, null);
        assertTrue(players.isBedrock(null, FLOODGATE));
        assertFalse(players.isBedrock(null, JAVA));
        assertEquals(List.of("canal local de Geyser", "UUID de Floodgate"), players.methods());
    }

    @Test
    void floodgateApiIsAskedOncePerPlayer() throws NoSuchMethodException {
        FakeFloodgate floodgate = new FakeFloodgate();
        UUID linked = UUID.randomUUID();
        floodgate.bedrock.add(linked);
        Method check = FakeFloodgate.class.getMethod("isFloodgatePlayer", UUID.class);
        BedrockPlayers players = new BedrockPlayers(floodgate, check, null, null);
        assertTrue(players.isBedrock(null, linked), "a linked Bedrock account has a normal-looking UUID");
        assertFalse(players.isBedrock(null, JAVA));
        assertFalse(players.isBedrock(null, FLOODGATE), "with the API the UUID shape is not used");
        players.isBedrock(null, linked);
        assertEquals(3, floodgate.calls, "cached per player");
        players.forget(linked);
        players.isBedrock(null, linked);
        assertEquals(4, floodgate.calls);
        assertEquals(List.of("canal local de Geyser", "API de Floodgate"), players.methods());
    }
}
