package dev.linqfy.bigCasares.modules.duels;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
class DuelArenaWorkTest {
    @Test void clearIsBoundedAndCoversTheWholeFootprint() {
        DuelArenaWork work = new DuelArenaWork();
        var visited = new ArrayList<String>();
        while (!work.done()) {
            int before = visited.size();
            work.step(500, (x, y, z) -> visited.add(x + "," + y + "," + z));
            assertTrue(visited.size() - before <= 500);
        }
        assertEquals(69 * 69 * 17, visited.size());
        assertEquals(visited.size(), visited.stream().distinct().count());
        assertTrue(visited.contains("-34,62,-34"));
        assertTrue(visited.contains("34,78,34"));
    }
}
