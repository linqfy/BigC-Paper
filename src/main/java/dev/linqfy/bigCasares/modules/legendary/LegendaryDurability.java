package dev.linqfy.bigCasares.modules.legendary;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

final class LegendaryDurability {
    @FunctionalInterface interface DirectorySync { void force(Path directory) throws IOException; }
    private LegendaryDurability() { }

    static void forceDirectory(Path directory) throws IOException {
        forceDirectory(directory, System.getProperty("os.name", ""), path -> {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) { channel.force(true); }
        });
    }

    static void forceDirectory(Path directory, String platform, DirectorySync sync) throws IOException {
        // Java's Windows provider cannot open directory FileChannels. Regular
        // files are still forced and journals still require an atomic rename.
        if (platform.toLowerCase(Locale.ROOT).startsWith("windows")) return;
        sync.force(directory);
    }
}
