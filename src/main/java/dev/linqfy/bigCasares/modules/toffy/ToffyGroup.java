package dev.linqfy.bigCasares.modules.toffy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A group (sub-category) of one tournament: its own members, places, level range and capacity. Mutable and
 * main-thread only; {@link ToffyTournamentService} is the only code that should change it after loading.
 */
final class ToffyGroup {
    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9_-]+");
    /**
     * Names a group cannot take: the anotar subcommands, and the words the /toffy group and tp/evento arguments use
     * ({@code grupo}, {@code forzar}, ...), so they never read as a group.
     */
    private static final Set<String> RESERVED = Set.of("salir", "estado", "forzar", "grupo", "lugar", "todos", "sin",
        "agregar", "borrar", "cancelar", "abierto");

    private final String key;
    private final String displayName;
    private boolean open;
    private Integer capacity;
    private Double minLevel;
    private Double maxLevel;
    private final List<ToffyStoredLocation> places = new ArrayList<>();
    private final Set<UUID> members = new LinkedHashSet<>();

    ToffyGroup(String key, String displayName, boolean open) {
        this.key = key;
        this.displayName = displayName;
        this.open = open;
    }

    /**
     * Lowercase key of a typed group name. Same rules as tournament names.
     *
     * @throws IllegalArgumentException when the name is empty, too long, reserved or has characters other than a-z 0-9
     *                                  _ -
     */
    static String keyOf(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || key.length() > ToffyTournament.MAX_NAME_LENGTH || !VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("El nombre del grupo solo puede tener letras sin acentos, numeros, "
                + "_ y - (maximo " + ToffyTournament.MAX_NAME_LENGTH + " caracteres).");
        }
        if (RESERVED.contains(key)) {
            throw new IllegalArgumentException("El nombre " + key + " esta reservado para /anotar; elegi otro.");
        }
        return key;
    }

    String key() {
        return key;
    }

    String displayName() {
        return displayName;
    }

    /** Whether players may choose this group themselves with {@code /anotar <torneo> <grupo>}. */
    boolean open() {
        return open;
    }

    void setOpen(boolean open) {
        this.open = open;
    }

    Integer capacity() {
        return capacity;
    }

    void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    Double minLevel() {
        return minLevel;
    }

    Double maxLevel() {
        return maxLevel;
    }

    void setLevelRange(Double min, Double max) {
        this.minLevel = min;
        this.maxLevel = max;
    }

    /** Whether the group has a level bound at all. Without one, every level fits. */
    boolean hasLevelRange() {
        return minLevel != null || maxLevel != null;
    }

    /** Whether a player with this Toffy level fits the group (no bound means no limit). */
    boolean allowsLevel(double level) {
        return (minLevel == null || level >= minLevel) && (maxLevel == null || level <= maxLevel);
    }

    /** Whether the capacity is reached. Always false without a capacity. */
    boolean isFull() {
        return capacity != null && members.size() >= capacity;
    }

    List<ToffyStoredLocation> places() {
        return Collections.unmodifiableList(places);
    }

    void setPlaces(List<ToffyStoredLocation> newPlaces) {
        places.clear();
        places.addAll(newPlaces);
    }

    void addPlace(ToffyStoredLocation place) {
        places.add(place);
    }

    Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    boolean addMember(UUID id) {
        return members.add(id);
    }

    boolean removeMember(UUID id) {
        return members.remove(id);
    }

    void clearMembers() {
        members.clear();
    }
}
