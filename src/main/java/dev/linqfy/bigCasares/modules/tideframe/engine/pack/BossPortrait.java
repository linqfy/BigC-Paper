package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;

/** Fixed local portrait input. File I/O and PNG decoding belong on the pack worker. */
public final class BossPortrait {
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private BossPortrait() { }

    /** Missing files preserve the built-in portrait; invalid supplied files fail visibly. */
    public static ImageAsset load(Path directory) throws IOException {
        Objects.requireNonNull(directory, "Portrait directory must be specified");
        Path file = directory.resolve("boss-face.png");
        if (Files.isSymbolicLink(directory))
            throw new IOException("Portrait directory must be a real directory, not a symbolic link: " + directory);
        BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException absent) { return null; }
        if (!attributes.isRegularFile() || attributes.isSymbolicLink())
            throw new IOException("Boss portrait must be a regular file, not a directory or symbolic link: " + file);
        if (attributes.size() > MAX_BYTES) throw tooLarge(file);
        byte[] bytes;
        // NOFOLLOW_LINKS also protects the final path if replaced after the attribute check.
        try (var channel = Files.newByteChannel(file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
             var input = Channels.newInputStream(channel)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length > MAX_BYTES) throw tooLarge(file);
        try { return ImageAsset.fromPng(bytes); }
        catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid boss portrait at " + file
                    + "; replace it with a valid static PNG (at most 8 MiB, 3840 pixels per side and 8294400 pixels total).", invalid);
        }
    }

    private static IOException tooLarge(Path file) {
        return new IOException("Boss portrait exceeds 8 MiB: " + file + "; resize or recompress the PNG.");
    }
}
