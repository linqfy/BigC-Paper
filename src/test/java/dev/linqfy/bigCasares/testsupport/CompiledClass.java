package dev.linqfy.bigCasares.testsupport;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads what a compiled class references (classes, methods it calls, annotations it carries) straight from its
 * bytecode, without loading it. Useful when the class needs runtime-only dependencies (Geyser, Paper) that unit tests
 * do not have, and more reliable than searching the source: comments and renamed locals do not count.
 */
public final class CompiledClass {

    /** A method called by the class: owner in internal form (a/b/C), name and descriptor. */
    public record Call(String owner, String name, String descriptor) {
    }

    private final Set<String> classes = new HashSet<>();
    private final Set<String> strings = new HashSet<>();
    private final List<Call> calls = new ArrayList<>();

    private CompiledClass() {
    }

    public static CompiledClass of(Class<?> anchor, String binaryName) throws IOException {
        String resource = binaryName.replace('.', '/') + ".class";
        try (InputStream stream = anchor.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("compiled class not found: " + resource);
            }
            return read(new DataInputStream(stream));
        }
    }

    public Set<String> referencedClasses() {
        return classes;
    }

    public List<Call> calls() {
        return calls;
    }

    public boolean calls(String owner, String name) {
        return calls.stream().anyMatch(call -> call.owner().equals(owner) && call.name().equals(name));
    }

    public boolean callsAnyOwner(String name, Set<String> owners) {
        return calls.stream().anyMatch(call -> owners.contains(call.owner()) && call.name().equals(name));
    }

    /** True when the class holds this text in its constant pool, for example a string literal it uses. */
    public boolean hasConstant(String text) {
        return strings.contains(text);
    }

    /** True when the annotation type (for example org.geysermc.event.subscribe.Subscribe) appears in the class. */
    public boolean mentionsType(String binaryName) {
        return strings.contains("L" + binaryName.replace('.', '/') + ";");
    }

    private static CompiledClass read(DataInputStream in) throws IOException {
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("not a class file");
        }
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        int[] classNameIndex = new int[count];
        int[][] refs = new int[count][];
        int[][] nameAndType = new int[count][];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF();
                case 3, 4 -> in.readInt();
                case 5, 6 -> {
                    in.readLong();
                    i++;
                }
                case 7 -> classNameIndex[i] = in.readUnsignedShort();
                case 8, 16, 19, 20 -> in.readUnsignedShort();
                case 9, 10, 11 -> refs[i] = new int[]{tag, in.readUnsignedShort(), in.readUnsignedShort()};
                case 12 -> nameAndType[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                case 15 -> {
                    in.readUnsignedByte();
                    in.readUnsignedShort();
                }
                case 17, 18 -> in.readInt();
                default -> throw new IOException("unknown constant pool tag " + tag);
            }
        }
        CompiledClass compiled = new CompiledClass();
        for (int i = 1; i < count; i++) {
            if (utf8[i] != null) {
                compiled.strings.add(utf8[i]);
            }
            if (classNameIndex[i] != 0) {
                compiled.classes.add(utf8[classNameIndex[i]]);
            }
        }
        for (int i = 1; i < count; i++) {
            if (refs[i] != null && refs[i][0] != 9) {
                String owner = utf8[classNameIndex[refs[i][1]]];
                int[] nat = nameAndType[refs[i][2]];
                compiled.calls.add(new Call(owner, utf8[nat[0]], utf8[nat[1]]));
            }
        }
        return compiled;
    }
}
