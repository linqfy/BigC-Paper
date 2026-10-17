package dev.linqfy.bigCasares.dimension.event;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DimensionTravelEventTest {

    private static final DimensionKey KEY = DimensionKey.of("example", "basalt_sea");

    @Test
    void portalTransfersNameTheirPortal() {
        DimensionTravelEvent portal = new DimensionTravelEvent(null, KEY, true, DimensionTravelEvent.Cause.PORTAL,
            new Location(null, 0, 64, 0), new Location(null, 10, 70, 10), "rift_frame");
        DimensionTravelEvent command = new DimensionTravelEvent(null, KEY, true, DimensionTravelEvent.Cause.COMMAND,
            new Location(null, 0, 64, 0), new Location(null, 10, 70, 10));

        assertEquals(Optional.of("rift_frame"), portal.portal());
        assertEquals(Optional.empty(), command.portal());
    }

    @Test
    void theDestinationCanBeReplaced() {
        DimensionTravelEvent event = new DimensionTravelEvent(null, KEY, true, DimensionTravelEvent.Cause.PORTAL,
            new Location(null, 0, 64, 0), new Location(null, 10, 70, 10), "rift_frame");
        event.setTo(new Location(null, 3, 80, 19));

        assertEquals(new Location(null, 3, 80, 19), event.to());
    }
}
