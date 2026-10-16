package dev.linqfy.bigCasares.dimension.environment;

import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A repeating sequence of environmental phases bound to the dimension's world clock.
 *
 * <p>The cycle is <em>stateless</em>: the active phase is a pure function of the
 * clock time ({@code World#getFullTime()} for the dimension), which Minecraft
 * already persists, syncs to clients and lets operators change with {@code /time}.
 * Visual tracks compile into a vanilla {@code timeline}, so every client renders
 * the same interpolated sky/fog without plugin packets. Server-side effects
 * observe phase changes through {@link EnvironmentController}.</p>
 */
public final class EnvironmentCycle {

    private static final Pattern PHASE_ID = Pattern.compile("[a-z0-9_]+");

    /** A named phase occupying {@code durationTicks} of the cycle. */
    public record Phase(String id, int durationTicks, Map<String, String> properties) {
        public Phase {
            Objects.requireNonNull(id, "id");
            if (!PHASE_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("Invalid phase id: " + id);
            }
            if (durationTicks < 20) {
                throw new IllegalArgumentException("Phase " + id + " must last at least 20 ticks");
            }
            properties = Map.copyOf(properties);
        }
    }

    /** Position inside the cycle at a clock time. */
    public record Snapshot(Phase phase, int phaseIndex, long cycleNumber, long ticksIntoPhase, long ticksUntilNextPhase) {
    }

    private final String id;
    private final List<Phase> phases;
    private final long period;
    private final List<TimelineTrack> visualTracks;
    private final Map<String, Integer> timeMarkers;

    private EnvironmentCycle(
        String id, List<Phase> phases, List<TimelineTrack> visualTracks, Map<String, Integer> timeMarkers
    ) {
        this.id = Objects.requireNonNull(id, "id");
        if (!PHASE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid cycle id: " + id);
        }
        this.phases = List.copyOf(phases);
        if (this.phases.isEmpty()) {
            throw new IllegalArgumentException("An environment cycle needs at least one phase");
        }
        long total = 0;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Phase phase : this.phases) {
            if (!ids.add(phase.id())) {
                throw new IllegalArgumentException("Duplicate phase id: " + phase.id());
            }
            total += phase.durationTicks();
        }
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Cycle period too long");
        }
        this.period = total;
        this.visualTracks = List.copyOf(visualTracks);
        java.util.Set<String> attributes = new java.util.HashSet<>();
        for (TimelineTrack track : this.visualTracks) {
            if (!attributes.add(track.attribute())) {
                throw new IllegalArgumentException("Duplicate timeline track: " + track.attribute());
            }
            for (TimelineTrack.Keyframe keyframe : track.keyframes()) {
                if (keyframe.ticks() > period) {
                    throw new IllegalArgumentException("Keyframe beyond cycle period in " + track.attribute());
                }
            }
        }
        this.timeMarkers = Map.copyOf(timeMarkers);
        for (int ticks : this.timeMarkers.values()) {
            if (ticks < 0 || ticks >= period) {
                throw new IllegalArgumentException("Time marker outside the cycle period");
            }
        }
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    public List<Phase> phases() {
        return phases;
    }

    public long period() {
        return period;
    }

    public List<TimelineTrack> visualTracks() {
        return visualTracks;
    }

    public Optional<Phase> phase(String id) {
        return phases.stream().filter(phase -> phase.id().equals(id)).findFirst();
    }

    /** Start offset (ticks into the cycle) of the given phase. */
    public long phaseStart(String id) {
        long offset = 0;
        for (Phase phase : phases) {
            if (phase.id().equals(id)) {
                return offset;
            }
            offset += phase.durationTicks();
        }
        throw new IllegalArgumentException("Unknown phase: " + id);
    }

    /** Resolves the phase active at {@code clockTicks}; negative times are treated as 0. */
    public Snapshot snapshotAt(long clockTicks) {
        long time = Math.max(0L, clockTicks);
        long cycleNumber = time / period;
        long offset = time % period;
        long start = 0;
        for (int index = 0; index < phases.size(); index++) {
            Phase phase = phases.get(index);
            long end = start + phase.durationTicks();
            if (offset < end) {
                return new Snapshot(phase, index, cycleNumber, offset - start, end - offset);
            }
            start = end;
        }
        throw new IllegalStateException("Unreachable: offset " + offset + " outside period " + period);
    }

    /** Clock time at which the given phase next begins, strictly after {@code clockTicks}. */
    public long nextStartOf(String phaseId, long clockTicks) {
        long start = phaseStart(phaseId);
        long time = Math.max(0L, clockTicks);
        long candidate = (time / period) * period + start;
        return candidate > time ? candidate : candidate + period;
    }

    /** The {@code timeline} JSON for the given clock. */
    public JsonObject timelineJson(NamespacedId clock) {
        JsonObject tracks = new JsonObject();
        for (TimelineTrack track : visualTracks) {
            tracks.add(EnvironmentAttributeIds.qualified(track.attribute()), track.toJson());
        }
        Json.ObjectBuilder builder = Json.object()
            .put("clock", clock)
            .put("period_ticks", (int) period);
        JsonObject markers = new JsonObject();
        long offset = 0;
        for (Phase phase : phases) {
            markers.add(clock.namespace() + ":" + id + "_" + phase.id(), Json.object()
                .put("show_in_commands", true)
                .put("ticks", (int) offset)
                .build());
            offset += phase.durationTicks();
        }
        timeMarkers.forEach((name, ticks) -> markers.add(name, Json.of(ticks)));
        builder.put("time_markers", markers);
        return builder.put("tracks", tracks).build();
    }

    public static final class Builder {
        private final String id;
        private final List<Phase> phases = new ArrayList<>();
        private final List<TimelineTrack> tracks = new ArrayList<>();
        private final Map<String, Integer> markers = new LinkedHashMap<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder phase(String id, int durationTicks) {
            return phase(id, durationTicks, Map.of());
        }

        public Builder phase(String id, int durationTicks, Map<String, String> properties) {
            phases.add(new Phase(id, durationTicks, properties));
            return this;
        }

        public Builder track(TimelineTrack track) {
            tracks.add(Objects.requireNonNull(track, "track"));
            return this;
        }

        /**
         * Adds a raw time marker, e.g. {@code minecraft:wake_up_from_sleep} so that
         * sleeping (when allowed) fast-forwards the clock to that point.
         */
        public Builder marker(String namespacedName, int ticks) {
            markers.put(Objects.requireNonNull(namespacedName, "namespacedName"), ticks);
            return this;
        }

        public EnvironmentCycle build() {
            return new EnvironmentCycle(id, phases, tracks, markers);
        }
    }

    /** Small helper so callers can use unqualified attribute paths. */
    static final class EnvironmentAttributeIds {
        private EnvironmentAttributeIds() {
        }

        static String qualified(String path) {
            return "minecraft:" + path;
        }
    }
}
