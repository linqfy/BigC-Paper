package dev.linqfy.bigCasares.dimension.nbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal Named Binary Tag model used for structure templates ({@code data/<ns>/structure/*.nbt}).
 *
 * <p>Supports every tag type the template format uses. Compound key order is
 * preserved so writing the same template twice produces identical bytes (the
 * GZIP header timestamp is zeroed by {@link #writeGzip}).</p>
 */
public final class Nbt {

    private static final int MAX_DEPTH = 512;

    private Nbt() {
    }

    public sealed interface Tag permits ByteTag, ShortTag, IntTag, LongTag, FloatTag, DoubleTag,
        ByteArrayTag, StringTag, ListTag, CompoundTag, IntArrayTag, LongArrayTag {
        byte id();
    }

    public record ByteTag(byte value) implements Tag { public byte id() { return 1; } }
    public record ShortTag(short value) implements Tag { public byte id() { return 2; } }
    public record IntTag(int value) implements Tag { public byte id() { return 3; } }
    public record LongTag(long value) implements Tag { public byte id() { return 4; } }
    public record FloatTag(float value) implements Tag { public byte id() { return 5; } }
    public record DoubleTag(double value) implements Tag { public byte id() { return 6; } }

    public record ByteArrayTag(byte[] value) implements Tag {
        public ByteArrayTag { value = value.clone(); }
        public byte id() { return 7; }
        @Override public byte[] value() { return value.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof ByteArrayTag tag && java.util.Arrays.equals(value, tag.value);
        }
        @Override public int hashCode() { return java.util.Arrays.hashCode(value); }
    }

    public record StringTag(String value) implements Tag {
        public StringTag { Objects.requireNonNull(value, "value"); }
        public byte id() { return 8; }
    }

    /** A homogeneous list; {@code elementType} is kept for empty lists. */
    public record ListTag(byte elementType, List<Tag> values) implements Tag {
        public ListTag {
            values = List.copyOf(values);
            for (Tag value : values) {
                if (value.id() != elementType) {
                    throw new IllegalArgumentException("Mixed NBT list element types");
                }
            }
        }
        public byte id() { return 9; }

        public static ListTag of(List<? extends Tag> values) {
            byte type = values.isEmpty() ? 0 : values.getFirst().id();
            return new ListTag(type, new ArrayList<>(values));
        }
    }

    public record CompoundTag(Map<String, Tag> values) implements Tag {
        public CompoundTag {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
        public byte id() { return 10; }

        public Tag get(String key) {
            return values.get(key);
        }

        public String getString(String key) {
            return values.get(key) instanceof StringTag tag ? tag.value() : null;
        }

        public CompoundTag getCompound(String key) {
            return values.get(key) instanceof CompoundTag tag ? tag : null;
        }

        public ListTag getList(String key) {
            return values.get(key) instanceof ListTag tag ? tag : null;
        }

        public CompoundTag with(String key, Tag value) {
            LinkedHashMap<String, Tag> copy = new LinkedHashMap<>(values);
            copy.put(key, value);
            return new CompoundTag(copy);
        }
    }

    public record IntArrayTag(int[] value) implements Tag {
        public IntArrayTag { value = value.clone(); }
        public byte id() { return 11; }
        @Override public int[] value() { return value.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof IntArrayTag tag && java.util.Arrays.equals(value, tag.value);
        }
        @Override public int hashCode() { return java.util.Arrays.hashCode(value); }
    }

    public record LongArrayTag(long[] value) implements Tag {
        public LongArrayTag { value = value.clone(); }
        public byte id() { return 12; }
        @Override public long[] value() { return value.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof LongArrayTag tag && java.util.Arrays.equals(value, tag.value);
        }
        @Override public int hashCode() { return java.util.Arrays.hashCode(value); }
    }

    /** Fluent compound builder. */
    public static CompoundBuilder compound() {
        return new CompoundBuilder();
    }

    public static final class CompoundBuilder {
        private final LinkedHashMap<String, Tag> values = new LinkedHashMap<>();

        private CompoundBuilder() {
        }

        public CompoundBuilder put(String key, Tag value) {
            values.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public CompoundBuilder putString(String key, String value) { return put(key, new StringTag(value)); }
        public CompoundBuilder putInt(String key, int value) { return put(key, new IntTag(value)); }
        public CompoundBuilder putByte(String key, byte value) { return put(key, new ByteTag(value)); }

        public CompoundTag build() {
            return new CompoundTag(values);
        }
    }

    public static ListTag intList(int... values) {
        List<Tag> tags = new ArrayList<>(values.length);
        for (int value : values) {
            tags.add(new IntTag(value));
        }
        return new ListTag((byte) 3, tags);
    }

    /** Writes a root compound (empty root name), GZIP-compressed with a zero mtime. */
    public static byte[] writeGzip(CompoundTag root) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(raw)) {
                out.writeByte(root.id());
                out.writeUTF("");
                writePayload(out, root);
            }
            ByteArrayOutputStream gzip = new ByteArrayOutputStream();
            try (GZIPOutputStream out = new GZIPOutputStream(gzip)) {
                out.write(raw.toByteArray());
            }
            byte[] bytes = gzip.toByteArray();
            // Bytes 4..7 hold the GZIP mtime; zero them for reproducible output.
            bytes[4] = 0;
            bytes[5] = 0;
            bytes[6] = 0;
            bytes[7] = 0;
            return bytes;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static CompoundTag readGzip(byte[] bytes) throws IOException {
        try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes));
             DataInputStream in = new DataInputStream(gzip)) {
            byte type = in.readByte();
            if (type != 10) {
                throw new IOException("NBT root must be a compound, found tag " + type);
            }
            in.readUTF();
            return (CompoundTag) readPayload(in, type, 0);
        }
    }

    private static void writePayload(DataOutput out, Tag tag) throws IOException {
        switch (tag) {
            case ByteTag value -> out.writeByte(value.value());
            case ShortTag value -> out.writeShort(value.value());
            case IntTag value -> out.writeInt(value.value());
            case LongTag value -> out.writeLong(value.value());
            case FloatTag value -> out.writeFloat(value.value());
            case DoubleTag value -> out.writeDouble(value.value());
            case ByteArrayTag value -> {
                byte[] data = value.value();
                out.writeInt(data.length);
                out.write(data);
            }
            case StringTag value -> out.writeUTF(value.value());
            case ListTag value -> {
                out.writeByte(value.values().isEmpty() ? 0 : value.elementType());
                out.writeInt(value.values().size());
                for (Tag element : value.values()) {
                    writePayload(out, element);
                }
            }
            case CompoundTag value -> {
                for (Map.Entry<String, Tag> entry : value.values().entrySet()) {
                    out.writeByte(entry.getValue().id());
                    out.writeUTF(entry.getKey());
                    writePayload(out, entry.getValue());
                }
                out.writeByte(0);
            }
            case IntArrayTag value -> {
                int[] data = value.value();
                out.writeInt(data.length);
                for (int element : data) {
                    out.writeInt(element);
                }
            }
            case LongArrayTag value -> {
                long[] data = value.value();
                out.writeInt(data.length);
                for (long element : data) {
                    out.writeLong(element);
                }
            }
        }
    }

    private static Tag readPayload(DataInput in, byte type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting too deep");
        }
        return switch (type) {
            case 1 -> new ByteTag(in.readByte());
            case 2 -> new ShortTag(in.readShort());
            case 3 -> new IntTag(in.readInt());
            case 4 -> new LongTag(in.readLong());
            case 5 -> new FloatTag(in.readFloat());
            case 6 -> new DoubleTag(in.readDouble());
            case 7 -> {
                byte[] data = new byte[checkedLength(in.readInt())];
                in.readFully(data);
                yield new ByteArrayTag(data);
            }
            case 8 -> new StringTag(in.readUTF());
            case 9 -> {
                byte elementType = in.readByte();
                int length = checkedLength(in.readInt());
                List<Tag> values = new ArrayList<>(Math.min(length, 4096));
                for (int index = 0; index < length; index++) {
                    values.add(readPayload(in, elementType, depth + 1));
                }
                yield new ListTag(length == 0 ? 0 : elementType, values);
            }
            case 10 -> {
                LinkedHashMap<String, Tag> values = new LinkedHashMap<>();
                while (true) {
                    byte childType = in.readByte();
                    if (childType == 0) {
                        break;
                    }
                    String key = in.readUTF();
                    values.put(key, readPayload(in, childType, depth + 1));
                }
                yield new CompoundTag(values);
            }
            case 11 -> {
                int[] data = new int[checkedLength(in.readInt())];
                for (int index = 0; index < data.length; index++) {
                    data[index] = in.readInt();
                }
                yield new IntArrayTag(data);
            }
            case 12 -> {
                long[] data = new long[checkedLength(in.readInt())];
                for (int index = 0; index < data.length; index++) {
                    data[index] = in.readLong();
                }
                yield new LongArrayTag(data);
            }
            default -> throw new IOException("Unknown NBT tag type " + type);
        };
    }

    private static int checkedLength(int length) throws IOException {
        if (length < 0 || length > 16 * 1024 * 1024) {
            throw new IOException("Invalid NBT length " + length);
        }
        return length;
    }
}
