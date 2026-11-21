package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.netty.buffer.UnpooledByteBufAllocationHelper;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.MapPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.Palette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BaseStorage;
import com.github.retrooper.packetevents.protocol.world.dimension.DimensionTypes;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The packetevents side of the remap. Only loaded when packetevents is on the server (every packetevents type lives
 * here). Rewrites, for Java players on the server's own protocol only: chunk data (per section palette; sections
 * with the global palette entry by entry), block changes, multi block changes and the break effect (level event
 * 2001, whose data is a state id). Block place acknowledgements carry no state since 1.19 (the state goes in a block
 * change). Every other packet is untouched.
 */
final class PacketEventsRemap implements PacketListener {
    /** Level event: block destroyed (particles and sound of the state in its data). */
    static final int BREAK_EFFECT = 2001;
    /** After this many chunk rewrite failures chunks are no longer touched (they keep the note block look). */
    static final int MAX_CHUNK_ERRORS = 20;

    /**
     * {@code wet}: client id after {@code remap} -> its waterlogged client id, for the positions in {@code wetIndex};
     * {@code worldOf}: the world and its min height of the player a packet goes to (null while it leaves).
     */
    record Context(StateIdRemap remap, StateIdRemap wet, RemapChunkIndex index, WaterloggedIndex wetIndex,
                   RemapStats stats, BedrockPlayers bedrock, Logger logger, Function<Object, PlayerWorld> worldOf) {
    }

    /** The world a player is in when a packet is sent to them. */
    record PlayerWorld(UUID world, int minY) {
    }

    private final Context context;
    private final ClientVersion serverVersion;
    private final AtomicBoolean chunksBroken = new AtomicBoolean();
    private final AtomicBoolean versionWarned = new AtomicBoolean();
    private final AtomicBoolean errorLogged = new AtomicBoolean();

    private PacketEventsRemap(Context context) {
        this.context = context;
        this.serverVersion = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    }

    static AutoCloseable register(Context context) {
        PacketEventsRemap listener = new PacketEventsRemap(context);
        PacketListenerCommon registered = PacketEvents.getAPI().getEventManager()
            .registerListener(listener, PacketListenerPriority.HIGHEST);
        return () -> PacketEvents.getAPI().getEventManager().unregisterListener(registered);
    }

    static String serverVersionName() {
        return PacketEvents.getAPI().getServerManager().getVersion().name();
    }

    /** packetevents' own id of a state, to cross-check the server's ids. */
    static int packetEventsId(BlockData data) {
        return SpigotConversionUtil.fromBukkitBlockData(data).getGlobalId();
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        context.bedrock().forget(event.getUser().getUUID());
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        PacketTypeCommon type = event.getPacketType();
        if (type != PacketType.Play.Server.CHUNK_DATA && type != PacketType.Play.Server.BLOCK_CHANGE
            && type != PacketType.Play.Server.MULTI_BLOCK_CHANGE && type != PacketType.Play.Server.EFFECT
            || event.isCancelled()) {
            return;
        }
        RemapStats stats = context.stats();
        User user = event.getUser();
        if (context.bedrock().isBedrock(user.getChannel(), user.getUUID())) {
            stats.bedrockPackets.increment();
            return;
        }
        if (user.getClientVersion() != serverVersion) {
            // ViaVersion clients: their ids are translated elsewhere, leave them the note block.
            stats.otherVersionPackets.increment();
            if (versionWarned.compareAndSet(false, true)) {
                context.logger().info("[AlexsCaves] Reescritura por paquetes: " + user.getClientVersion()
                    + " no es la versión del servidor; sus paquetes no se tocan.");
            }
            return;
        }
        try {
            if (type == PacketType.Play.Server.CHUNK_DATA) {
                chunk(event);
            } else if (type == PacketType.Play.Server.BLOCK_CHANGE) {
                blockChange(event);
            } else if (type == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                multiBlockChange(event);
            } else {
                effect(event);
            }
        } catch (RuntimeException failure) {
            stats.errors.increment();
            event.markForReEncode(false);
            if (type == PacketType.Play.Server.CHUNK_DATA && stats.errors.sum() >= MAX_CHUNK_ERRORS
                && chunksBroken.compareAndSet(false, true)) {
                context.logger().severe("[AlexsCaves] Reescritura por paquetes: demasiados errores, los chunks dejan de "
                    + "reescribirse (se ven como note blocks).");
            }
            if (errorLogged.compareAndSet(false, true)) {
                context.logger().log(Level.WARNING, "[AlexsCaves] Reescritura por paquetes: falló "
                    + event.getPacketName() + " (el paquete sale sin cambios)", failure);
            }
        }
    }

    private void chunk(PacketSendEvent event) {
        long start = System.nanoTime();
        RemapStats stats = context.stats();
        Object buffer = event.getByteBuf();
        int reader = ByteBufHelper.readerIndex(buffer);
        int chunkX = ByteBufHelper.readInt(buffer);
        int chunkZ = ByteBufHelper.readInt(buffer);
        ByteBufHelper.readerIndex(buffer, reader);
        if (chunksBroken.get() || !context.index().mayContain(chunkX, chunkZ)
            && !context.wetIndex().mayContain(chunkX, chunkZ)) {
            stats.chunksSkipped.increment();
            stats.chunksSkippedNanos.add(System.nanoTime() - start);
            return;
        }
        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        int sections = rewriteColumn(wrapper.getColumn(), context.remap());
        PlayerWorld world = context.worldOf().apply(event.getPlayer());
        if (world != null) {
            int[] wet = context.wetIndex().positions(world.world(), chunkX, chunkZ);
            if (wet.length > 0) {
                int blocks = rewriteWet(wrapper.getColumn(), world.minY(), wet, context.wet());
                stats.wetBlocks.add(blocks);
                sections += blocks;
            }
        }
        if (sections > 0) {
            event.markForReEncode(true);
            stats.chunksRewritten.increment();
            stats.sectionsRewritten.add(sections);
        } else {
            stats.chunksDecodedUnchanged.increment();
        }
        stats.chunksRewrittenNanos.add(System.nanoTime() - start);
    }

    private void blockChange(PacketSendEvent event) {
        WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
        int id = wrapper.getBlockId();
        int sent = context.remap().apply(id);
        Vector3i position = wrapper.getBlockPosition();
        if (context.wet().remaps(sent) && context.wetIndex().mayContain(position.getX() >> 4, position.getZ() >> 4)) {
            PlayerWorld world = context.worldOf().apply(event.getPlayer());
            if (world != null && context.wetIndex().contains(world.world(), position.getX(), position.getY(), position.getZ())) {
                sent = context.wet().apply(sent);
                context.stats().wetBlocks.increment();
            }
        }
        if (sent == id) {
            return;
        }
        wrapper.setBlockID(sent);
        event.markForReEncode(true);
        context.stats().blockChanges.increment();
        if (context.remap().remaps(id)) {
            markFromPacket(event, position.getX() >> 4, position.getZ() >> 4);
        }
    }

    private void multiBlockChange(PacketSendEvent event) {
        WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
        Vector3i section = wrapper.getChunkPosition();
        PlayerWorld world = context.wetIndex().mayContain(section.getX(), section.getZ())
            ? context.worldOf().apply(event.getPlayer()) : null;
        int changed = 0;
        boolean remapped = false;
        for (WrapperPlayServerMultiBlockChange.EncodedBlock block : wrapper.getBlocks()) {
            int id = block.getBlockId();
            int sent = context.remap().apply(id);
            remapped |= sent != id;
            if (world != null && context.wet().remaps(sent)
                && context.wetIndex().contains(world.world(), block.getX(), block.getY(), block.getZ())) {
                sent = context.wet().apply(sent);
                context.stats().wetBlocks.increment();
            }
            if (sent != id) {
                block.setBlockId(sent);
                changed++;
            }
        }
        if (changed > 0) {
            event.markForReEncode(true);
            context.stats().multiBlockEntries.add(changed);
            if (remapped) {
                markFromPacket(event, section.getX(), section.getZ());
            }
        }
    }

    private void effect(PacketSendEvent event) {
        WrapperPlayServerEffect wrapper = new WrapperPlayServerEffect(event);
        if (wrapper.getType() != BREAK_EFFECT) {
            return;
        }
        int id = wrapper.getData();
        int sent = context.remap().apply(id);
        Vector3i position = wrapper.getPosition();
        // A waterlogged block keeps its mark until the tick after its break: its particles are the wet state's.
        if (context.wet().remaps(sent) && context.wetIndex().mayContain(position.getX() >> 4, position.getZ() >> 4)) {
            PlayerWorld world = context.worldOf().apply(event.getPlayer());
            if (world != null && context.wetIndex().contains(world.world(), position.getX(), position.getY(), position.getZ())) {
                sent = context.wet().apply(sent);
            }
        }
        if (sent == id) {
            return;
        }
        wrapper.setData(sent);
        event.markForReEncode(true);
        context.stats().breakEffects.increment();
    }

    /** A remapped state reached a player: its chunk holds one, so the next full send of that chunk is rewritten. */
    private void markFromPacket(PacketSendEvent event, int chunkX, int chunkZ) {
        PlayerWorld world = context.worldOf().apply(event.getPlayer());
        if (world != null && context.index().mark(world.world(), chunkX, chunkZ)) {
            context.stats().chunksMarkedByPacket.increment();
        }
    }

    /** @return the number of sections whose block states changed */
    static int rewriteColumn(Column column, StateIdRemap remap) {
        int changed = 0;
        for (BaseChunk section : column.getChunks()) {
            if (section instanceof Chunk_v1_18 modern && rewriteStates(modern.getChunkData(), remap)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Sends the marked positions of a chunk with their waterlogged client state ({@code wet}, keyed by the id left by
     * the dry rewrite). A position whose block has no wet state (a stale mark) is left alone. Each changed block gains
     * a fluid, so the section's fluid count goes up with it (the client skips fluids of sections without any).
     *
     * @return the number of blocks changed
     */
    static int rewriteWet(Column column, int minY, int[] positions, StateIdRemap wet) {
        BaseChunk[] sections = column.getChunks();
        int changed = 0;
        for (int packed : positions) {
            int y = WaterloggedIndex.y(packed);
            int index = (y - minY) >> 4;
            if (index < 0 || index >= sections.length || !(sections[index] instanceof Chunk_v1_18 section)) {
                continue;
            }
            int x = WaterloggedIndex.localX(packed);
            int z = WaterloggedIndex.localZ(packed);
            DataPalette states = section.getChunkData();
            int id = states.get(x, y & 15, z);
            if (wet.remaps(id)) {
                states.set(x, y & 15, z, wet.apply(id));
                section.setFluidCount(section.getFluidCount() + 1);
                changed++;
            }
        }
        return changed;
    }

    /**
     * Palette-level rewrite: a single-state or indirect palette only has its entries replaced (the packed indices
     * stay valid); a section with the global palette has its 4096 entries rewritten in place (same bit width).
     */
    static boolean rewriteStates(DataPalette states, StateIdRemap remap) {
        Palette palette = states.palette;
        if (palette instanceof SingletonPalette) {
            int id = palette.idToState(0);
            if (!remap.remaps(id)) {
                return false;
            }
            states.palette = new SingletonPalette(remap.apply(id));
            return true;
        }
        if (palette instanceof ListPalette || palette instanceof MapPalette) {
            int[] ids = new int[palette.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = palette.idToState(i);
            }
            if (!remap.rewrite(ids)) {
                return false;
            }
            states.palette = new ListPalette(palette.getBits(), ids);
            return true;
        }
        BaseStorage storage = states.storage;
        if (storage == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < 4096; i++) {
            int id = storage.get(i);
            if (remap.remaps(id)) {
                storage.set(i, remap.apply(id));
                changed = true;
            }
        }
        return changed;
    }

    // --- /alexscaves remap bench --------------------------------------------------------------------------------

    /** One chunk packet body built by the server for the bench. */
    record BenchChunk(int x, int z, byte[] body) {
    }

    /**
     * Runs real chunk packet bodies through the listener's code paths, without a client: the skip check on every
     * packet, and decode + rewrite + encode on the marked ones. Also checks that packetevents reads and writes the
     * server's 26.2 chunk packets byte for byte (unmarked chunks, decoded and encoded unchanged) and that no
     * remapped id is left after a rewrite.
     */
    static List<String> bench(List<BenchChunk> chunks, int minY, int height, StateIdRemap remap, RemapChunkIndex index,
                              int rounds) throws ReflectiveOperationException {
        User user = new User(null, ConnectionState.PLAY, PacketEvents.getAPI().getServerManager().getVersion().toClientVersion(),
            new UserProfile(UUID.randomUUID(), "bench"));
        // The heights come from the dimension type (packetevents refuses setting them directly on 1.17+).
        user.setDimensionType(DimensionTypes.OVERWORLD);
        if (user.getMinWorldHeight() != minY || user.getTotalWorldHeight() != height) {
            return List.of("El bench solo sabe medir mundos con la altura del overworld vanilla (" + minY + ", " + height + ").");
        }
        long skipNanos = 0;
        long skipped = 0;
        long rewriteNanos = 0;
        long rewritten = 0;
        int identical = 0;
        int different = 0;
        int leftovers = 0;
        int borrowed = 0;
        int sectionsChanged = 0;
        int marked = 0;
        for (int round = 0; round < rounds; round++) {
            for (BenchChunk chunk : chunks) {
                Object buffer = UnpooledByteBufAllocationHelper.wrappedBuffer(chunk.body());
                long start = System.nanoTime();
                int reader = ByteBufHelper.readerIndex(buffer);
                int x = ByteBufHelper.readInt(buffer);
                int z = ByteBufHelper.readInt(buffer);
                ByteBufHelper.readerIndex(buffer, reader);
                boolean mayContain = index.mayContain(x, z);
                long peek = System.nanoTime() - start;
                if (!mayContain) {
                    skipNanos += peek;
                    skipped++;
                    if (round == 0) {
                        byte[] again = encode(decode(buffer, user), user);
                        if (Arrays.equals(again, chunk.body())) {
                            identical++;
                        } else {
                            different++;
                        }
                    }
                    continue;
                }
                start = System.nanoTime();
                WrapperPlayServerChunkData wrapper = decode(buffer, user);
                int sections = rewriteColumn(wrapper.getColumn(), remap);
                byte[] written = encode(wrapper, user);
                rewriteNanos += System.nanoTime() - start;
                rewritten++;
                if (round == 0) {
                    marked++;
                    sectionsChanged += sections;
                    Column check = decode(UnpooledByteBufAllocationHelper.wrappedBuffer(written), user).getColumn();
                    for (BaseChunk section : check.getChunks()) {
                        for (int i = 0; i < 4096; i++) {
                            int id = section.getBlockId(i & 15, i >> 8, i >> 4 & 15);
                            // A module note block left over (its target is itself remapped: note -> leaves -> d7);
                            // a borrowed leaves state is what the rewrite is meant to leave.
                            if (remap.remaps(id) && remap.remaps(remap.apply(id))) {
                                leftovers++;
                            } else if (remap.remaps(id)) {
                                borrowed++;
                            }
                        }
                    }
                }
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("chunks: " + chunks.size() + " (marcados " + marked + "), rondas: " + rounds);
        lines.add("salteo sin decodificar: " + RemapStats.micros(skipNanos, skipped) + " por paquete (" + skipped + ")");
        lines.add("decodificar + reescribir + codificar: " + RemapStats.micros(rewriteNanos, rewritten) + " por chunk ("
            + rewritten + "), secciones cambiadas: " + sectionsChanged);
        lines.add(String.format(Locale.ROOT, "ida y vuelta de packetevents igual al servidor: %d de %d", identical,
            identical + different));
        lines.add("note blocks del módulo que quedan tras reescribir: " + leftovers + ", bloques que salen como hojas: "
            + borrowed);
        return lines;
    }

    private static WrapperPlayServerChunkData decode(Object buffer, User user) throws ReflectiveOperationException {
        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData((Column) null);
        attach(wrapper, user);
        wrapper.buffer = buffer;
        wrapper.read();
        return wrapper;
    }

    private static byte[] encode(WrapperPlayServerChunkData wrapper, User user) throws ReflectiveOperationException {
        attach(wrapper, user);
        Object out = UnpooledByteBufAllocationHelper.buffer();
        wrapper.buffer = out;
        wrapper.write();
        byte[] bytes = new byte[ByteBufHelper.readableBytes(out)];
        ByteBufHelper.getBytes(out, ByteBufHelper.readerIndex(out), bytes);
        return bytes;
    }

    /** The bench has no connection: the wrapper gets a detached user (world height) and the server's version. */
    private static void attach(PacketWrapper<?> wrapper, User user) throws ReflectiveOperationException {
        Field userField = PacketWrapper.class.getDeclaredField("user");
        userField.setAccessible(true);
        userField.set(wrapper, user);
        Field version = PacketWrapper.class.getDeclaredField("clientVersion");
        version.setAccessible(true);
        version.set(wrapper, user.getClientVersion());
    }
}
