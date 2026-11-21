package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

/** Debug counters of the packet remap (written from Netty threads). */
public final class RemapStats {
    public final LongAdder chunksRewritten = new LongAdder();
    public final LongAdder chunksRewrittenNanos = new LongAdder();
    public final LongAdder chunksDecodedUnchanged = new LongAdder();
    public final LongAdder chunksSkipped = new LongAdder();
    public final LongAdder chunksSkippedNanos = new LongAdder();
    public final LongAdder sectionsRewritten = new LongAdder();
    public final LongAdder blockChanges = new LongAdder();
    public final LongAdder multiBlockEntries = new LongAdder();
    public final LongAdder breakEffects = new LongAdder();
    public final LongAdder bedrockPackets = new LongAdder();
    public final LongAdder otherVersionPackets = new LongAdder();
    public final LongAdder errors = new LongAdder();
    public final LongAdder chunkScans = new LongAdder();
    public final LongAdder chunkScanNanos = new LongAdder();
    public final LongAdder chunksMarkedByScan = new LongAdder();
    public final LongAdder chunksMarkedByPacket = new LongAdder();
    public final LongAdder wetBlocks = new LongAdder();

    public void reset() {
        for (LongAdder adder : List.of(chunksRewritten, chunksRewrittenNanos, chunksDecodedUnchanged, chunksSkipped,
            chunksSkippedNanos, sectionsRewritten, blockChanges, multiBlockEntries, breakEffects, bedrockPackets,
            otherVersionPackets, errors, chunkScans, chunkScanNanos, chunksMarkedByScan, chunksMarkedByPacket, wetBlocks)) {
            adder.reset();
        }
    }

    /** Mean microseconds per item, {@code -} when there were none. */
    static String micros(long nanos, long count) {
        return count == 0 ? "-" : String.format(Locale.ROOT, "%.1f µs", nanos / 1000.0 / count);
    }

    public List<String> lines() {
        long rewritten = chunksRewritten.sum() + chunksDecodedUnchanged.sum();
        return List.of(
            "chunks reescritos: " + chunksRewritten.sum() + " (+" + chunksDecodedUnchanged.sum()
                + " decodificados sin cambios), " + micros(chunksRewrittenNanos.sum(), rewritten) + " c/u, secciones: "
                + sectionsRewritten.sum(),
            "chunks salteados sin decodificar: " + chunksSkipped.sum() + ", " + micros(chunksSkippedNanos.sum(),
                chunksSkipped.sum()) + " c/u",
            "cambios de bloque: " + blockChanges.sum() + ", entradas multi: " + multiBlockEntries.sum()
                + ", efectos de rotura: " + breakEffects.sum() + ", bloques enviados en agua: " + wetBlocks.sum(),
            "paquetes de Bedrock sin tocar: " + bedrockPackets.sum() + ", de otra versión: " + otherVersionPackets.sum()
                + ", errores: " + errors.sum(),
            "escaneos al cargar chunk: " + chunkScans.sum() + ", " + micros(chunkScanNanos.sum(), chunkScans.sum())
                + " c/u, marcados por escaneo: " + chunksMarkedByScan.sum() + ", por paquete: "
                + chunksMarkedByPacket.sum());
    }
}
