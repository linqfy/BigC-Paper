package dev.linqfy.bigCasares.modules.pveboss;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Pure decisions the boss runtime takes on behalf of a {@link BossEncounterController}. */
public final class BossEncounterRules {

    public static final String SAHUR_BOSS_ID = "tung-tung-sahur";
    public static final String SAHUR_MARKER_KEY = "sahur_boss_id";
    public static final String SHARED_MARKER_KEY = "pve_boss_id";
    static final int MINIMUM_PHASE = 1;
    static final int MAXIMUM_PHASE = 3;

    private BossEncounterRules() {
    }

    /**
     * Name of the persistent marker key of a boss. The two original bosses keep their stable keys (the Geyser
     * bridge maps {@code pve_boss_id} to the Abyss Guardian Bedrock entity). A scripted boss gets its own key
     * derived from its id, so it is never presented as another boss.
     */
    public static String markerKeyName(String bossId, boolean scripted) {
        String id = Objects.requireNonNull(bossId, "bossId").strip().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("boss id must not be blank");
        }
        if (id.equals(SAHUR_BOSS_ID)) {
            return SAHUR_MARKER_KEY;
        }
        if (!scripted) {
            return SHARED_MARKER_KEY;
        }
        StringBuilder key = new StringBuilder(id.length() + 8);
        for (int index = 0; index < id.length(); index++) {
            char character = id.charAt(index);
            boolean allowed = (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '_';
            key.append(allowed ? character : '_');
        }
        String name = key.append("_boss_id").toString();
        if (name.equals(SAHUR_MARKER_KEY) || name.equals(SHARED_MARKER_KEY)) {
            throw new IllegalArgumentException("boss id " + id + " collides with a reserved marker key");
        }
        return name;
    }

    /** The phase in effect: the controller override clamped to the supported range, else the health phase. */
    public static int resolvePhase(OptionalInt override, int healthPhase) {
        Objects.requireNonNull(override, "override");
        if (override.isEmpty()) {
            return healthPhase;
        }
        return Math.max(MINIMUM_PHASE, Math.min(MAXIMUM_PHASE, override.getAsInt()));
    }

    /** Damage that reaches the health pool; zero means the hit is negated (non-finite values negate too). */
    public static double admittedDamage(double filtered) {
        return Double.isFinite(filtered) && filtered > 0.0 ? filtered : 0.0;
    }

    /**
     * Track for a phase, falling back to the nearest lower phase and then to any declared track, so an
     * overridden phase without music never throws. Empty only when no track is declared at all.
     */
    public static Optional<BossMusicTrack> musicFor(Map<Integer, BossMusicTrack> tracksByPhase, int phase) {
        Objects.requireNonNull(tracksByPhase, "tracksByPhase");
        for (int candidate = phase; candidate >= MINIMUM_PHASE; candidate--) {
            BossMusicTrack track = tracksByPhase.get(candidate);
            if (track != null) {
                return Optional.of(track);
            }
        }
        return tracksByPhase.keySet().stream().sorted().findFirst().map(tracksByPhase::get);
    }
}
