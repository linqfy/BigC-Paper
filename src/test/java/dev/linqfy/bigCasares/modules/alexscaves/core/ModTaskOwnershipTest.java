package dev.linqfy.bigCasares.modules.alexscaves.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Turning the module off does not disable the plugin: a task scheduled around {@link ModTasks} would outlive it. */
class ModTaskOwnershipTest {
    private static final Pattern DIRECT = Pattern.compile("\\.(runTask|runTaskLater|runTaskTimer|scheduleSyncDelayedTask)\\(");
    /**
     * Files that schedule on their own: the owner itself; classes with their own registry closed at shutdown (nuclear
     * explosions, thrown drums, the Candy sack tick); the locate command; and next-tick block state corrections that
     * move no entity and hurt nobody.
     */
    private static final Set<String> ALLOWED = Set.of("ModTasks.java", "NuclearExplosion.java", "ThrownWasteDrum.java",
        "CandyItems.java", "AlexsCavesCommand.java", "NoteBlockService.java", "PottedPlantListener.java",
        "WaterloggedBlocks.java", "MiningPredictionSync.java", "MagnetismService.java");

    @Test
    void delayedAndRepeatingTasksBelongToTheModule() throws IOException {
        List<String> unowned = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/dev/linqfy/bigCasares/modules/alexscaves"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (!ALLOWED.contains(file.getFileName().toString()) && DIRECT.matcher(Files.readString(file)).find()) {
                    unowned.add(file.getFileName().toString());
                }
            }
        }
        assertEquals(List.of(), unowned);
    }
}
