package dev.linqfy.bigCasares.modules.toffy;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

interface ToffyTournamentStorage {
    List<ToffyTournament> load() throws IOException;

    void save(Collection<ToffyTournament> tournaments) throws IOException;

    /** One saved point of a place list, as {@code torneos.yml} writes it. */
    static Map<String, Object> pointOf(ToffyStoredLocation at) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", at.world());
        map.put("x", at.x());
        map.put("y", at.y());
        map.put("z", at.z());
        map.put("yaw", (double) at.yaw());
        map.put("pitch", (double) at.pitch());
        return map;
    }
}
