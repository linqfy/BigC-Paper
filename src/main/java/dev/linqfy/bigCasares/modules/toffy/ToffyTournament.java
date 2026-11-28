package dev.linqfy.bigCasares.modules.toffy;

import dev.linqfy.bigCasares.modules.toffy.eventos.RewardItem;
import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEventSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One tournament. Mutable and main-thread only; {@link ToffyTournamentService} is the only code that should change
 * it after loading.
 */
final class ToffyTournament {
    static final int MAX_NAME_LENGTH = 32;
    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9_-]+");

    private final String key;
    private final String displayName;
    private boolean open = true;
    private final Map<UUID, String> participants = new LinkedHashMap<>();
    private final Map<String, String> messages = new LinkedHashMap<>();
    // Rewards of this tournament's events: when any is set they replace the ones of toffy_eventos.yml.
    private final List<String> rewardItems = new ArrayList<>();
    private final List<String> rewardCommands = new ArrayList<>();
    private final Map<UUID, ToffyStoredLocation> returns = new LinkedHashMap<>();
    private final Set<UUID> pending = new LinkedHashSet<>();
    private final List<ToffyStoredLocation> places = new ArrayList<>();
    private Long scheduledAt;
    /** Minutes-before warnings already sent (or passed) for the current schedule. Not saved. */
    private final Set<Integer> announced = new HashSet<>();
    private Double minLevel;
    private Double maxLevel;
    private Integer capacity;
    private Integer minPlayers;
    private Long closeAt;
    private String eventType;
    private Integer eventLobby;
    private Long eventAt;
    /** Key of the group the scheduled event runs for, or null for the whole sign-up list. */
    private String eventGroup;
    /** Minutes-before event warnings already sent (or passed). Not saved. */
    private final Set<Integer> eventAnnounced = new HashSet<>();
    /** Sub-categories of the tournament, by key, in the order they were created. */
    private final Map<String, ToffyGroup> groups = new LinkedHashMap<>();
    /** When true and there are open groups, {@code /anotar <torneo>} lists them instead of signing up. */
    private boolean groupMandatory;

    ToffyTournament(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    /**
     * Lowercase key of a typed name.
     *
     * @throws IllegalArgumentException when the name is empty, too long or has characters other than a-z 0-9 _ -
     */
    static String keyOf(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || key.length() > MAX_NAME_LENGTH || !VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("El nombre del torneo solo puede tener letras sin acentos, numeros, "
                + "_ y - (maximo " + MAX_NAME_LENGTH + " caracteres).");
        }
        return key;
    }

    String key() {
        return key;
    }

    String displayName() {
        return displayName;
    }

    boolean open() {
        return open;
    }

    Map<UUID, String> participants() {
        return Collections.unmodifiableMap(participants);
    }

    Map<String, String> messages() {
        return Collections.unmodifiableMap(messages);
    }

    String message(String caseKey) {
        return messages.get(caseKey);
    }

    Map<UUID, ToffyStoredLocation> returns() {
        return Collections.unmodifiableMap(returns);
    }

    Set<UUID> pending() {
        return Collections.unmodifiableSet(pending);
    }

    /** Fixed destinations of the teleports, in the order they were added. Empty when there are none. */
    List<ToffyStoredLocation> places() {
        return Collections.unmodifiableList(places);
    }

    Double minLevel() {
        return minLevel;
    }

    Double maxLevel() {
        return maxLevel;
    }

    /** Whether a player with this Toffy level may sign up or be teleported (no bound means no limit). */
    boolean allowsLevel(double level) {
        return (minLevel == null || level >= minLevel) && (maxLevel == null || level <= maxLevel);
    }

    Integer capacity() {
        return capacity;
    }

    Integer minPlayers() {
        return minPlayers;
    }

    /** Epoch millis of the scheduled sign-up close, or null. */
    Long closeAt() {
        return closeAt;
    }

    /** Type id of the scheduled event, or null. */
    String eventType() {
        return eventType;
    }

    Integer eventLobby() {
        return eventLobby;
    }

    /** Epoch millis of the scheduled event, or null. */
    Long eventAt() {
        return eventAt;
    }

    /** Key of the group of the scheduled event, or null when it runs for every sign-up. */
    String eventGroup() {
        return eventGroup;
    }

    /** The groups of the tournament, in the order they were created. */
    List<ToffyGroup> groups() {
        return List.copyOf(groups.values());
    }

    Optional<ToffyGroup> group(String key) {
        return Optional.ofNullable(groups.get(key));
    }

    /** Key of the group the player is in, if any. A participant is in at most one group. */
    Optional<String> groupOf(UUID playerId) {
        for (ToffyGroup group : groups.values()) {
            if (group.members().contains(playerId)) {
                return Optional.of(group.key());
            }
        }
        return Optional.empty();
    }

    boolean groupMandatory() {
        return groupMandatory;
    }

    void setGroupMandatory(boolean groupMandatory) {
        this.groupMandatory = groupMandatory;
    }

    void putGroup(ToffyGroup group) {
        groups.put(group.key(), group);
    }

    /** Removes the group. Its members stay signed up to the tournament, just without a group. */
    boolean removeGroup(String key) {
        return groups.remove(key) != null;
    }

    /** Whether the tournament has a level bound at all. */
    boolean hasLevelRange() {
        return minLevel != null || maxLevel != null;
    }

    boolean isEventAnnounced(int minutes) {
        return eventAnnounced.contains(minutes);
    }

    void markEventAnnounced(int minutes) {
        eventAnnounced.add(minutes);
    }

    void setLevelRange(Double min, Double max) {
        this.minLevel = min;
        this.maxLevel = max;
    }

    void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    void setMinPlayers(Integer minPlayers) {
        this.minPlayers = minPlayers;
    }

    void setCloseAt(Long at) {
        this.closeAt = at;
    }

    /** Sets or clears (all null) the scheduled event, for every sign-up. */
    void setEvent(String type, Integer lobby, Long at) {
        setEvent(type, lobby, at, null);
    }

    /** Sets or clears (all null) the scheduled event, for the members of {@code group} when it is not null. */
    void setEvent(String type, Integer lobby, Long at, String group) {
        this.eventType = type;
        this.eventLobby = lobby;
        this.eventAt = at;
        this.eventGroup = at == null ? null : group;
        eventAnnounced.clear();
    }

    /** Epoch millis of the scheduled teleport, or null when nothing is scheduled. */
    Long scheduledAt() {
        return scheduledAt;
    }

    boolean isAnnounced(int minutes) {
        return announced.contains(minutes);
    }

    void markAnnounced(int minutes) {
        announced.add(minutes);
    }

    /** Closing the sign-ups also drops a scheduled close: it would have nothing left to do. */
    void setOpen(boolean open) {
        this.open = open;
        if (!open) {
            closeAt = null;
        }
    }

    void setPlaces(List<ToffyStoredLocation> newPlaces) {
        places.clear();
        places.addAll(newPlaces);
    }

    void addPlace(ToffyStoredLocation place) {
        places.add(place);
    }

    /** Sets or clears the schedule. Resets the warnings, so the caller marks the ones that already passed. */
    void setScheduledAt(Long at) {
        this.scheduledAt = at;
        announced.clear();
    }

    void putParticipant(UUID id, String name) {
        participants.put(id, name);
    }

    /** Removes the sign-up and the group membership of the player. */
    boolean removeParticipant(UUID id) {
        groups.values().forEach(group -> group.removeMember(id));
        return participants.remove(id) != null;
    }

    void putMessage(String caseKey, String text) {
        messages.put(caseKey, text);
    }

    /** Event rewards of this tournament: item entries ({@code id[:amount]}) and console commands ({player}). */
    ToffyEventSettings.Rewards rewards() {
        return new ToffyEventSettings.Rewards(rewardItems, rewardCommands);
    }

    /** The rewards as the admin lists them: items first, then commands, numbered from 1. */
    List<String> rewardLines() {
        List<String> lines = new ArrayList<>();
        for (String item : rewardItems) {
            lines.add("item " + item);
        }
        for (String command : rewardCommands) {
            lines.add("comando " + command);
        }
        return lines;
    }

    void addRewardItem(String entry) {
        rewardItems.add(entry);
    }

    void addRewardCommand(String command) {
        rewardCommands.add(command);
    }

    /** Removes the reward at the 1-based position of {@link #rewardLines()}; false when there is none there. */
    boolean removeReward(int number) {
        int index = number - 1;
        if (index < 0) {
            return false;
        }
        if (index < rewardItems.size()) {
            rewardItems.remove(index);
            return true;
        }
        index -= rewardItems.size();
        if (index < rewardCommands.size()) {
            rewardCommands.remove(index);
            return true;
        }
        return false;
    }

    /** Removes the reward items that do not parse ({@link RewardItem#parse}) and returns them, for the warning. */
    List<String> dropInvalidRewardItems() {
        List<String> invalid = new ArrayList<>();
        for (String item : List.copyOf(rewardItems)) {
            if (RewardItem.parse(item).isEmpty()) {
                invalid.add(item);
                rewardItems.remove(item);
            }
        }
        return invalid;
    }

    void clearRewards() {
        rewardItems.clear();
        rewardCommands.clear();
    }

    boolean removeMessage(String caseKey) {
        return messages.remove(caseKey) != null;
    }

    void putReturn(UUID id, ToffyStoredLocation location) {
        returns.put(id, location);
    }

    boolean removeReturn(UUID id) {
        return returns.remove(id) != null;
    }

    boolean addPending(UUID id) {
        return pending.add(id);
    }

    boolean removePending(UUID id) {
        return pending.remove(id);
    }
}
