package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The few server internals the remap needs, by reflection (the plugin compiles against the Paper API only):
 * the wire id of a block state ({@code Block.getId(CraftBlockData.getState())}, the id the server writes in chunk
 * and block change packets), a one-pass palette check of a chunk against a set of states, and, for
 * {@code /alexscaves remap bench}, the encoded body of a real chunk packet.
 */
final class ServerInternals {
    private final ClassLoader loader;
    private final Method getState;
    private final Method getId;

    private ServerInternals(ClassLoader loader, Method getState, Method getId) {
        this.loader = loader;
        this.getState = getState;
        this.getId = getId;
    }

    static ServerInternals lookup(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> craftBlockData = Class.forName("org.bukkit.craftbukkit.block.data.CraftBlockData", true, loader);
        Class<?> blockState = Class.forName("net.minecraft.world.level.block.state.BlockState", true, loader);
        Class<?> block = Class.forName("net.minecraft.world.level.block.Block", true, loader);
        return new ServerInternals(loader, craftBlockData.getMethod("getState"), block.getMethod("getId", blockState));
    }

    int stateId(BlockData data) {
        try {
            return (int) getId.invoke(null, getState.invoke(data));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("No se pudo leer el id de " + data.getAsString(), failure);
        }
    }

    /** Whether a loaded chunk may hold one of some states. */
    interface ChunkProbe {
        boolean mayHold(Chunk chunk);
    }

    /**
     * One pass over each section palette ({@code PalettedContainer.maybeHas}, what {@link Chunk#contains} does for one
     * state) with an identity set of the states. Falls back to one {@link Chunk#contains} per state.
     */
    ChunkProbe probe(Collection<BlockData> states) {
        Set<Object> handles = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            for (BlockData data : states) {
                handles.add(getState.invoke(data));
            }
            Class<?> chunkStatus = Class.forName("net.minecraft.world.level.chunk.status.ChunkStatus", true, loader);
            Object full = chunkStatus.getField("FULL").get(null);
            Class<?> chunkAccess = Class.forName("net.minecraft.world.level.chunk.ChunkAccess", true, loader);
            Method sections = chunkAccess.getMethod("getSections");
            Method statesOf = Class.forName("net.minecraft.world.level.chunk.LevelChunkSection", true, loader)
                .getMethod("getStates");
            Predicate<Object> wanted = handles::contains;
            Map<Class<?>, Method> methods = new ConcurrentHashMap<>();
            return chunk -> {
                try {
                    Object level = methods.computeIfAbsent(chunk.getClass(), type -> method(type, "getHandle", chunkStatus))
                        .invoke(chunk, full);
                    for (Object section : (Object[]) sections.invoke(level)) {
                        Object container = statesOf.invoke(section);
                        if ((boolean) methods.computeIfAbsent(container.getClass(), type -> method(type, "maybeHas", Predicate.class))
                            .invoke(container, wanted)) {
                            return true;
                        }
                    }
                    return false;
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    return containsAny(chunk, states);
                }
            };
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return chunk -> containsAny(chunk, states);
        }
    }

    private static Method method(Class<?> type, String name, Class<?> parameter) {
        try {
            return type.getMethod(name, parameter);
        } catch (NoSuchMethodException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private static boolean containsAny(Chunk chunk, Collection<BlockData> states) {
        for (BlockData state : states) {
            if (chunk.contains(state)) {
                return true;
            }
        }
        return false;
    }

    /** Body (after the packet id) of the {@code level_chunk_with_light} packet the server would send for a chunk. */
    byte[] chunkPacketBody(World world, Chunk chunk) throws ReflectiveOperationException {
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Class<?> chunkStatus = Class.forName("net.minecraft.world.level.chunk.status.ChunkStatus", true, loader);
        Object full = chunkStatus.getField("FULL").get(null);
        Object levelChunk = chunk.getClass().getMethod("getHandle", chunkStatus).invoke(chunk, full);
        Object light = level.getClass().getMethod("getLightEngine").invoke(level);
        Class<?> packetType = Class.forName("net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket",
            true, loader);
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : packetType.getConstructors()) {
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length == 4 && parameters[2] == BitSet.class && parameters[3] == BitSet.class) {
                constructor = candidate;
            }
        }
        if (constructor == null) {
            throw new NoSuchMethodException("ClientboundLevelChunkWithLightPacket(LevelChunk, LevelLightEngine, BitSet, BitSet)");
        }
        Object packet = constructor.newInstance(levelChunk, light, null, null);
        Class<?> byteBuf = Class.forName("io.netty.buffer.ByteBuf", true, loader);
        Object raw = Class.forName("io.netty.buffer.Unpooled", true, loader).getMethod("buffer").invoke(null);
        Object registries = level.getClass().getMethod("registryAccess").invoke(level);
        Class<?> registryAccess = Class.forName("net.minecraft.core.RegistryAccess", true, loader);
        Object buffer = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf", true, loader)
            .getConstructor(byteBuf, registryAccess).newInstance(raw, registries);
        Object codec = packetType.getField("STREAM_CODEC").get(null);
        Class.forName("net.minecraft.network.codec.StreamEncoder", true, loader)
            .getMethod("encode", Object.class, Object.class).invoke(codec, buffer, packet);
        int readable = (int) byteBuf.getMethod("readableBytes").invoke(raw);
        byte[] bytes = new byte[readable];
        byteBuf.getMethod("readBytes", byte[].class).invoke(raw, (Object) bytes);
        byteBuf.getMethod("release").invoke(raw);
        return bytes;
    }
}
