package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.event.DimensionTravelEvent;
import dev.linqfy.bigCasares.dimension.event.DimensionTravelEvent.Cause;
import dev.linqfy.bigCasares.modules.sift.MainPortalTravel.Exit;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Anchor;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPortalTravelTest {

    private static final Anchor ANCHOR = new Anchor(-120, 77, 301);
    private static final Location OVERWORLD_SPOT = new Location(null, 1500.5, 64.0, -800.5, 135.0F, 12.5F);
    private static final Location SIFT_ARRIVAL = new Location(null, 1500.5, 64.0, -800.5, 135.0F, 12.5F);

    private static DimensionTravelEvent event(DimensionKey dimension, boolean entering, Cause cause, String portal) {
        return new DimensionTravelEvent(null, dimension, entering, cause, OVERWORLD_SPOT, SIFT_ARRIVAL, portal);
    }

    @Test
    void theAncientCityPortalSendsPlayersToTheAnchorFacingTheirWay() {
        Optional<Location> destination = MainPortalTravel.redirect(
            event(SiftDimension.KEY, true, Cause.PORTAL, SiftDimension.ANCIENT_CITY_PORTAL), ANCHOR);

        Location to = destination.orElseThrow();
        assertEquals(-119.5, to.getX());
        assertEquals(77.0, to.getY());
        assertEquals(301.5, to.getZ());
        assertEquals(135.0F, to.getYaw());
        assertEquals(12.5F, to.getPitch());
    }

    @Test
    void theCraftedPortalKeepsItsCoordinatesAndReturnFrame() {
        assertEquals(Optional.empty(), MainPortalTravel.redirect(event(SiftDimension.KEY, true, Cause.PORTAL, "rift_frame"), ANCHOR));
    }

    @Test
    void onlyPortalTransfersIntoTheSiftAreRedirected() {
        String ancientCity = SiftDimension.ANCIENT_CITY_PORTAL;
        assertEquals(Optional.empty(), MainPortalTravel.redirect(event(SiftDimension.KEY, true, Cause.COMMAND, null), ANCHOR));
        assertEquals(Optional.empty(), MainPortalTravel.redirect(event(SiftDimension.KEY, true, Cause.API, null), ANCHOR));
        assertEquals(Optional.empty(), MainPortalTravel.redirect(event(SiftDimension.KEY, true, Cause.RIFT, null), ANCHOR));
        assertEquals(Optional.empty(), MainPortalTravel.redirect(event(SiftDimension.KEY, false, Cause.PORTAL, ancientCity), ANCHOR),
            "leaving is not entering");
        assertEquals(Optional.empty(), MainPortalTravel.redirect(
            event(DimensionKey.of("example", "basalt_sea"), true, Cause.PORTAL, ancientCity), ANCHOR), "another dimension");
        assertEquals(Optional.empty(), MainPortalTravel.redirect(event(SiftDimension.KEY, true, Cause.PORTAL, null), ANCHOR),
            "a portal transfer that names no portal");
    }

    @Test
    void walkingIntoTheMainPortalFromInsideTheSiftLeavesThroughIt() {
        assertTrue(MainPortalTravel.leavesThroughMainPortal(event(SiftDimension.KEY, false, Cause.PORTAL, SiftDimension.MAIN_PORTAL)));

        assertFalse(MainPortalTravel.leavesThroughMainPortal(event(SiftDimension.KEY, false, Cause.PORTAL, "ancient_city")),
            "an exit frame built for a fallback arrival is the framework's business");
        assertFalse(MainPortalTravel.leavesThroughMainPortal(event(SiftDimension.KEY, false, Cause.COMMAND, SiftDimension.MAIN_PORTAL)));
        assertFalse(MainPortalTravel.leavesThroughMainPortal(event(SiftDimension.KEY, true, Cause.PORTAL, SiftDimension.MAIN_PORTAL)));
        assertFalse(MainPortalTravel.leavesThroughMainPortal(
            event(DimensionKey.of("example", "basalt_sea"), false, Cause.PORTAL, SiftDimension.MAIN_PORTAL)));
    }

    @Test
    void anEntranceThatStillStandsLetsTheFrameworkReturnThePlayer() {
        Set<String> lit = new HashSet<>(Set.of("1500,64,-801"));

        assertEquals(Exit.FRAMEWORK, MainPortalTravel.exit(false, OVERWORLD_SPOT, (x, y, z) -> lit.contains(x + "," + y + "," + z)));
    }

    @Test
    void anEntranceThatIsGoneSendsThePlayerThroughAnExitRift() {
        assertEquals(Exit.ENTRANCE_CLOSED, MainPortalTravel.exit(false, OVERWORLD_SPOT, (x, y, z) -> false));
    }

    @Test
    void playersWhoCameInThroughARiftHaveNoEntranceToReturnTo() {
        assertEquals(Exit.FROM_RIFT, MainPortalTravel.exit(true, OVERWORLD_SPOT, (x, y, z) -> true));
    }

    @Test
    void theEntranceSearchReachesTwoBlocksAroundAndOneBelowTheStoredPoint() {
        // The stored point is where the player stood next to the portal when the move triggered it.
        assertTrue(MainPortalTravel.entranceStands(OVERWORLD_SPOT, (x, y, z) -> x == 1502 && y == 66 && z == -802));
        assertTrue(MainPortalTravel.entranceStands(OVERWORLD_SPOT, (x, y, z) -> x == 1498 && y == 63 && z == -799));
        assertFalse(MainPortalTravel.entranceStands(OVERWORLD_SPOT, (x, y, z) -> x == 1503 && y == 64 && z == -801));
        assertFalse(MainPortalTravel.entranceStands(OVERWORLD_SPOT, (x, y, z) -> x == 1500 && y == 62 && z == -801));
        assertFalse(MainPortalTravel.entranceStands(OVERWORLD_SPOT, (x, y, z) -> x == 1500 && y == 67 && z == -801));
    }

    @Test
    void theStoredAnchorSurvivesARoundTrip() {
        assertEquals(Optional.of(ANCHOR), SiftMainPortal.decode(SiftMainPortal.encode(ANCHOR)));
        assertEquals(Optional.of(new Anchor(0, 5, 0)), SiftMainPortal.decode(SiftMainPortal.encode(new Anchor(0, 5, 0))));
        assertEquals("-120,77,301", SiftMainPortal.encode(ANCHOR));
    }

    @Test
    void aDamagedStoredAnchorIsIgnored() {
        assertEquals(Optional.empty(), SiftMainPortal.decode(""));
        assertEquals(Optional.empty(), SiftMainPortal.decode("1,2"));
        assertEquals(Optional.empty(), SiftMainPortal.decode("1,2,three"));
        assertEquals(Optional.empty(), SiftMainPortal.decode("1,2,3,4"));
        assertEquals(Optional.of(new Anchor(1, 2, 3)), SiftMainPortal.decode(" 1, 2 ,3 "));
    }
}
