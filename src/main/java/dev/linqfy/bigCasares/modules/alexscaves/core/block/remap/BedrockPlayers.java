package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells Bedrock connections apart, from Netty threads. Bedrock players must keep receiving the note block (Geyser
 * maps those states to the module's custom blocks), so any doubt counts as Bedrock:
 * <ol>
 *   <li>Geyser-Spigot's direct connection: Geyser talks to the server through a Netty {@code LocalChannel}
 *       (packetevents sees it like any other channel); no Java client ever connects that way.</li>
 *   <li>Floodgate's API ({@code isFloodgatePlayer}), also for Geyser on a proxy.</li>
 *   <li>Geyser's API ({@code isBedrockPlayer}).</li>
 *   <li>Floodgate's UUID shape ({@code 00000000-0000-0000-...}), when neither API is on this server.</li>
 * </ol>
 * The answer per player is cached until they disconnect.
 */
public final class BedrockPlayers {
    private final Object floodgate;
    private final Method floodgateCheck;
    private final Object geyser;
    private final Method geyserCheck;
    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    BedrockPlayers(Object floodgate, Method floodgateCheck, Object geyser, Method geyserCheck) {
        this.floodgate = floodgate;
        this.floodgateCheck = floodgateCheck;
        this.geyser = geyser;
        this.geyserCheck = geyserCheck;
    }

    /** Looks the Floodgate and Geyser APIs up (either may be missing). */
    public static BedrockPlayers lookup(ClassLoader loader) {
        Object floodgate = null;
        Method floodgateCheck = null;
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, loader);
            floodgate = api.getMethod("getInstance").invoke(null);
            floodgateCheck = api.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException notInstalled) {
            // no Floodgate here
        }
        Object geyser = null;
        Method geyserCheck = null;
        try {
            Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi", true, loader);
            geyser = api.getMethod("api").invoke(null);
            geyserCheck = api.getMethod("isBedrockPlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException notInstalled) {
            // no Geyser here (it may run on a proxy)
        }
        return new BedrockPlayers(floodgate, floodgateCheck, geyser, geyserCheck);
    }

    /** Which checks are available, for the startup log. */
    public List<String> methods() {
        List<String> methods = new ArrayList<>();
        methods.add("canal local de Geyser");
        if (floodgate != null) {
            methods.add("API de Floodgate");
        }
        if (geyser != null) {
            methods.add("API de Geyser");
        }
        if (floodgate == null && geyser == null) {
            methods.add("UUID de Floodgate");
        }
        return methods;
    }

    /**
     * @param channel the connection's Netty channel (may be null)
     * @param player  the player's UUID, null while it is not known yet (then nothing is rewritten)
     */
    public boolean isBedrock(Object channel, UUID player) {
        if (channel != null && isLocalChannel(channel.getClass())) {
            return true;
        }
        if (player == null) {
            return true;
        }
        Boolean known = cache.get(player);
        if (known == null) {
            known = ask(floodgate, floodgateCheck, player) || ask(geyser, geyserCheck, player)
                || floodgate == null && geyser == null && looksLikeFloodgate(player);
            cache.put(player, known);
        }
        return known;
    }

    public void forget(UUID player) {
        if (player != null) {
            cache.remove(player);
        }
    }

    public void clear() {
        cache.clear();
    }

    /** Netty's {@code io.netty.channel.local.LocalChannel} or a subclass (Geyser's wrapper), relocated or not. */
    static boolean isLocalChannel(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (isLocalChannelName(current.getName())) {
                return true;
            }
        }
        return false;
    }

    static boolean isLocalChannelName(String className) {
        return className.equals("io.netty.channel.local.LocalChannel") || className.endsWith(".channel.local.LocalChannel");
    }

    /** UUIDs Floodgate makes up for Bedrock players have their first half at zero. */
    static boolean looksLikeFloodgate(UUID id) {
        return id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() != 0;
    }

    private static boolean ask(Object api, Method check, UUID id) {
        if (api == null || check == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(check.invoke(api, id));
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return false;
        }
    }
}
