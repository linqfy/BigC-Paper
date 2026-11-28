package dev.linqfy.bigCasares.modules.toffy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Tournaments in memory (main thread). Every change is saved through the storage; when saving fails the change stays
 * in memory and an {@link UncheckedIOException} is thrown.
 */
final class ToffyTournamentService {
    enum RegisterResult {
        REGISTERED, REGISTERED_CLOSED, ALREADY_REGISTERED, CLOSED, UNKNOWN_TOURNAMENT, LEVEL_OUT_OF_RANGE
    }

    enum Change {
        CHANGED, UNCHANGED, UNKNOWN
    }

    record PendingReturn(String tournament, ToffyStoredLocation location) {
    }

    /** A reminder due for the scheduled teleport or event of {@code tournament}, {@code minutes} before it. */
    record Warning(String tournament, int minutes) {
    }

    /** Names that /anotar uses for its own subcommands, so a tournament cannot take them. */
    static final Set<String> RESERVED_NAMES = Set.of("salir", "estado");
    /** Default minutes before a scheduled teleport or event when the sign-ups are warned (torneos.avisos-*). */
    static final int[] DEFAULT_WARNING_MINUTES = {5, 1};
    /** Default: a schedule more than this late when the file is loaded is dropped (torneos.perdido-tras-minutos). */
    static final long DEFAULT_MISSED_AFTER_MILLIS = 5 * 60_000L;
    private static final long MINUTE_MILLIS = 60_000L;

    private final ToffyTournamentStorage storage;
    private final LongSupplier clock;
    private final Consumer<String> log;
    private final Map<String, ToffyTournament> tournaments = new LinkedHashMap<>();
    // Set from toffy_eventos.yml (torneos.*) through useTimings; the defaults are the values the file ships with.
    private int[] teleportWarnings = DEFAULT_WARNING_MINUTES;
    private int[] eventWarnings = DEFAULT_WARNING_MINUTES;
    private long missedAfterMillis = DEFAULT_MISSED_AFTER_MILLIS;

    /**
     * Replaces the timings: the minutes before a scheduled teleport and before a scheduled event that warn the sign-ups,
     * and how late a schedule loaded from the file may be before it is dropped.
     */
    void useTimings(List<Integer> teleportMinutes, List<Integer> eventMinutes, long missedMillis) {
        teleportWarnings = teleportMinutes.stream().mapToInt(Integer::intValue).toArray();
        eventWarnings = eventMinutes.stream().mapToInt(Integer::intValue).toArray();
        missedAfterMillis = missedMillis;
    }

    ToffyTournamentService(ToffyTournamentStorage storage, Collection<ToffyTournament> initial) {
        this(storage, initial, System::currentTimeMillis, message -> { });
    }

    /**
     * @param clock current epoch millis
     * @param log   receives the warnings about what was dropped or could not be saved while loading
     */
    ToffyTournamentService(ToffyTournamentStorage storage, Collection<ToffyTournament> initial, LongSupplier clock,
                           Consumer<String> log) {
        this.storage = storage;
        this.clock = clock;
        this.log = log;
        initial.forEach(tournament -> tournaments.put(tournament.key(), tournament));
        // Prize items that do not parse are dropped with a warning, so the events never try to give them.
        for (ToffyTournament tournament : tournaments.values()) {
            for (String invalid : tournament.dropInvalidRewardItems()) {
                log.accept("Premio invalido del torneo " + tournament.displayName() + ": '" + invalid
                    + "' (se descarta; formato id o id:cantidad).");
            }
        }
        restoreSchedules();
    }

    static ToffyTournamentService load(ToffyTournamentStorage storage) throws IOException {
        return load(storage, message -> { });
    }

    static ToffyTournamentService load(ToffyTournamentStorage storage, Consumer<String> log) throws IOException {
        return new ToffyTournamentService(storage, storage.load(), System::currentTimeMillis, log);
    }

    /** Current epoch millis of the injected clock. */
    long now() {
        return clock.getAsLong();
    }

    /**
     * @throws IllegalArgumentException invalid name or a reserved one ("salir", "estado")
     * @throws IllegalStateException    the tournament already exists
     */
    ToffyTournament create(String name) {
        String key = ToffyTournament.keyOf(name);
        if (RESERVED_NAMES.contains(key)) {
            throw new IllegalArgumentException("El nombre " + key + " esta reservado para /anotar; elegi otro.");
        }
        if (tournaments.containsKey(key)) {
            throw new IllegalStateException("Ya existe el torneo " + tournaments.get(key).displayName() + ".");
        }
        ToffyTournament tournament = new ToffyTournament(key, name.trim());
        tournaments.put(key, tournament);
        save();
        return tournament;
    }

    Optional<ToffyTournament> find(String name) {
        try {
            return Optional.ofNullable(tournaments.get(ToffyTournament.keyOf(name)));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    List<ToffyTournament> list() {
        return List.copyOf(tournaments.values());
    }

    List<String> keys() {
        return List.copyOf(tournaments.keySet());
    }

    /** Deleting a tournament also deletes its place and its schedule. */
    boolean delete(String name) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty()) {
            return false;
        }
        tournaments.remove(found.get().key());
        save();
        return true;
    }

    Change close(String name) {
        return setOpen(name, false);
    }

    Change open(String name) {
        return setOpen(name, true);
    }

    private Change setOpen(String name, boolean open) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty()) {
            return Change.UNKNOWN;
        }
        if (found.get().open() == open) {
            return Change.UNCHANGED;
        }
        found.get().setOpen(open);
        save();
        return Change.CHANGED;
    }

    RegisterResult register(String name, UUID playerId, String playerName) {
        return register(name, playerId, playerName, 0.0);
    }

    /**
     * Signs the player up. Refused when the sign-ups are closed or the level is outside the range of the tournament.
     * Reaching the capacity closes the sign-ups ({@link RegisterResult#REGISTERED_CLOSED}).
     */
    RegisterResult register(String name, UUID playerId, String playerName, double level) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty()) {
            return RegisterResult.UNKNOWN_TOURNAMENT;
        }
        ToffyTournament tournament = found.get();
        if (tournament.participants().containsKey(playerId)) {
            return RegisterResult.ALREADY_REGISTERED;
        }
        if (!tournament.open() || (tournament.capacity() != null
            && tournament.participants().size() >= tournament.capacity())) {
            return RegisterResult.CLOSED;
        }
        if (!tournament.allowsLevel(level)) {
            return RegisterResult.LEVEL_OUT_OF_RANGE;
        }
        tournament.putParticipant(playerId, playerName);
        boolean full = tournament.capacity() != null && tournament.participants().size() >= tournament.capacity();
        if (full) {
            tournament.setOpen(false);
        }
        save();
        return full ? RegisterResult.REGISTERED_CLOSED : RegisterResult.REGISTERED;
    }

    /** @throws IllegalArgumentException when min is above max or one of them is negative */
    boolean setLevelRange(String name, Double min, Double max) {
        if ((min != null && min < 0) || (max != null && max < 0)
            || (min != null && max != null && min > max)) {
            throw new IllegalArgumentException("El rango de nivel tiene que ser 0 <= minimo <= maximo.");
        }
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setLevelRange(min, max);
        save();
        return true;
    }

    /**
     * Sets the capacity. Reaching it closes the sign-ups right away.
     *
     * @return true when the sign-ups were closed by this change
     */
    boolean setCapacity(String name, Integer capacity) {
        ToffyTournament tournament = require(name);
        if (capacity != null && capacity < 1) {
            throw new IllegalArgumentException("El cupo tiene que ser al menos 1.");
        }
        tournament.setCapacity(capacity);
        boolean closed = capacity != null && tournament.open() && tournament.participants().size() >= capacity;
        if (closed) {
            tournament.setOpen(false);
        }
        save();
        return closed;
    }

    boolean setMinPlayers(String name, Integer minimum) {
        if (minimum != null && minimum < 1) {
            throw new IllegalArgumentException("El minimo tiene que ser al menos 1.");
        }
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setMinPlayers(minimum);
        save();
        return true;
    }

    /** Schedules the sign-up close at {@code at}; replaces an earlier one. */
    void scheduleClose(String name, long at) {
        require(name).setCloseAt(at);
        save();
    }

    boolean cancelClose(String name) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || found.get().closeAt() == null) {
            return false;
        }
        found.get().setCloseAt(null);
        save();
        return true;
    }

    List<String> dueCloses(long now) {
        List<String> due = new ArrayList<>();
        for (ToffyTournament tournament : tournaments.values()) {
            if (tournament.closeAt() != null && tournament.closeAt() <= now) {
                due.add(tournament.key());
            }
        }
        return due;
    }

    /**
     * The scheduled close ran: closes the sign-ups and clears the schedule.
     *
     * @return true only when this call closed an open tournament (announce the close only then)
     */
    boolean markClosed(String name) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || found.get().closeAt() == null) {
            return false;
        }
        boolean wasOpen = found.get().open();
        found.get().setOpen(false);
        save();
        return wasOpen;
    }

    /**
     * Schedules a staff event on the tournament. Replaces an earlier scheduled event of the tournament.
     *
     * @return the event time that was replaced, if any
     */
    Optional<Long> scheduleEvent(String name, String type, Integer lobbySeconds, long at) {
        return scheduleEvent(name, type, lobbySeconds, at, null);
    }

    /** Same as {@link #scheduleEvent(String, String, Integer, long)}, for the members of {@code group} when not null. */
    Optional<Long> scheduleEvent(String name, String type, Integer lobbySeconds, long at, String group) {
        ToffyTournament tournament = require(name);
        Long previous = tournament.eventAt();
        tournament.setEvent(type, lobbySeconds, at, group == null ? null : keyOfOrNull(group));
        markPassedEventWarnings(tournament, at, now());
        save();
        return Optional.ofNullable(previous);
    }

    boolean cancelEvent(String name) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || found.get().eventAt() == null) {
            return false;
        }
        found.get().setEvent(null, null, null);
        save();
        return true;
    }

    List<String> dueEvents(long now) {
        List<String> due = new ArrayList<>();
        for (ToffyTournament tournament : tournaments.values()) {
            if (tournament.eventAt() != null && tournament.eventAt() <= now) {
                due.add(tournament.key());
            }
        }
        return due;
    }

    /** The scheduled event was started (or could not start): clears the schedule. */
    boolean markEventFired(String name) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || found.get().eventAt() == null) {
            return false;
        }
        found.get().setEvent(null, null, null);
        save();
        return true;
    }

    List<Warning> dueEventWarnings(long now) {
        List<Warning> due = new ArrayList<>();
        for (ToffyTournament tournament : tournaments.values()) {
            Long at = tournament.eventAt();
            if (at == null || now >= at) {
                continue;
            }
            for (int minutes : eventWarnings) {
                if (!tournament.isEventAnnounced(minutes) && now >= at - minutes * MINUTE_MILLIS) {
                    due.add(new Warning(tournament.key(), minutes));
                }
            }
        }
        return due;
    }

    void markEventWarned(String name, int minutes) {
        find(name).ifPresent(tournament -> tournament.markEventAnnounced(minutes));
    }

    boolean removeParticipant(String name, UUID playerId) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || !found.get().removeParticipant(playerId)) {
            return false;
        }
        save();
        return true;
    }

    /**
     * Saves where the player was. Keeps the first saved location if there is one so teleporting twice still returns
     * the player to the original place.
     *
     * @return true when the location was saved, false when an earlier one was kept or the tournament is unknown
     */
    boolean recordDeparture(String name, UUID playerId, ToffyStoredLocation location) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || found.get().returns().containsKey(playerId)) {
            return false;
        }
        found.get().putReturn(playerId, location);
        save();
        return true;
    }

    int savedPlaces(String name) {
        return find(name).map(tournament -> tournament.returns().size()).orElse(0);
    }

    Optional<ToffyStoredLocation> savedLocation(String name, UUID playerId) {
        return find(name).map(tournament -> tournament.returns().get(playerId));
    }

    /**
     * Forgets the saved location and any pending return of the player.
     */
    boolean clearReturn(String name, UUID playerId) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty()) {
            return false;
        }
        boolean changed = found.get().removeReturn(playerId);
        changed |= found.get().removePending(playerId);
        if (changed) {
            save();
        }
        return changed;
    }

    /**
     * Saved location of the player, removed from the tournament.
     */
    Optional<ToffyStoredLocation> takeReturn(String name, UUID playerId) {
        Optional<ToffyStoredLocation> location = savedLocation(name, playerId);
        if (location.isPresent()) {
            clearReturn(name, playerId);
        }
        return location;
    }

    boolean markPending(String name, UUID playerId) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || !found.get().returns().containsKey(playerId) || !found.get().addPending(playerId)) {
            return false;
        }
        save();
        return true;
    }

    boolean isPending(String name, UUID playerId) {
        return find(name).map(tournament -> tournament.pending().contains(playerId)).orElse(false);
    }

    /**
     * Returns still owed to the player, in tournament order. A pending mark without a saved location is dropped.
     * Nothing is cleared here: call {@link #clearReturn} once the teleport worked.
     */
    List<PendingReturn> pendingReturns(UUID playerId) {
        List<PendingReturn> result = new ArrayList<>();
        boolean dropped = false;
        for (ToffyTournament tournament : tournaments.values()) {
            if (!tournament.pending().contains(playerId)) {
                continue;
            }
            ToffyStoredLocation location = tournament.returns().get(playerId);
            if (location == null) {
                tournament.removePending(playerId);
                dropped = true;
            } else {
                result.add(new PendingReturn(tournament.key(), location));
            }
        }
        if (dropped) {
            save();
        }
        return result;
    }

    /**
     * Changes the event rewards of the tournament and saves them when the change says so.
     *
     * @return the result of the change; empty when the tournament does not exist
     */
    Optional<Boolean> editRewards(String name, Predicate<ToffyTournament> change) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        boolean changed = change.test(found.get());
        if (changed) {
            save();
        }
        return Optional.of(changed);
    }

    boolean setMessage(String name, String caseKey, String text) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty()) {
            return false;
        }
        found.get().putMessage(caseKey, text);
        save();
        return true;
    }

    /**
     * @return true when an override existed and was removed
     */
    boolean resetMessage(String name, String caseKey) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || !found.get().removeMessage(caseKey)) {
            return false;
        }
        save();
        return true;
    }

    /**
     * Replaces every destination of the tournament with {@code place} (one point).
     *
     * @return false when the tournament does not exist
     */
    boolean setPlace(String name, ToffyStoredLocation place) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setPlaces(List.of(place));
        save();
        return true;
    }

    /** Adds one more destination. */
    boolean addPlace(String name, ToffyStoredLocation place) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty()) {
            return false;
        }
        found.get().addPlace(place);
        save();
        return true;
    }

    /**
     * Removes every destination. A schedule of the teleport needs a place, so it is cancelled too.
     *
     * @return false when the tournament does not exist or had no place
     */
    boolean clearPlace(String name) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || found.get().places().isEmpty()) {
            return false;
        }
        found.get().setPlaces(List.of());
        found.get().setScheduledAt(null);
        save();
        return true;
    }

    /**
     * Schedules the teleport to the place at {@code at} (epoch millis). A schedule that is already there is replaced.
     * Warnings that would have been sent before {@code at} are marked as passed, so they are not sent late.
     *
     * @return the schedule that was replaced, if any
     * @throws IllegalArgumentException the tournament does not exist
     * @throws IllegalStateException    the tournament has no place
     */
    Optional<Long> schedule(String name, long at) {
        ToffyTournament tournament = require(name);
        if (tournament.places().isEmpty()) {
            throw new IllegalStateException("El torneo no tiene lugar. Usa /toffy torneo lugar " + tournament.key());
        }
        Long previous = tournament.scheduledAt();
        tournament.setScheduledAt(at);
        markPassedWarnings(tournament, at, now());
        save();
        return Optional.ofNullable(previous);
    }

    /** @return true when a schedule existed and was removed */
    boolean cancelSchedule(String name) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || found.get().scheduledAt() == null) {
            return false;
        }
        found.get().setScheduledAt(null);
        save();
        return true;
    }

    /**
     * Tournaments whose schedule is due at {@code now} (epoch millis at or before it), in tournament order.
     */
    List<String> dueSchedules(long now) {
        List<String> due = new ArrayList<>();
        for (ToffyTournament tournament : tournaments.values()) {
            Long at = tournament.scheduledAt();
            if (at != null && at <= now) {
                due.add(tournament.key());
            }
        }
        return due;
    }

    /**
     * Warnings to send at {@code now}: each minute mark of {@link #teleportWarnings} that has started, has not been
     * sent yet and whose schedule has not passed.
     */
    List<Warning> dueWarnings(long now) {
        List<Warning> due = new ArrayList<>();
        for (ToffyTournament tournament : tournaments.values()) {
            Long at = tournament.scheduledAt();
            if (at == null || now >= at) {
                continue;
            }
            for (int minutes : teleportWarnings) {
                if (!tournament.isAnnounced(minutes) && now >= at - minutes * MINUTE_MILLIS) {
                    due.add(new Warning(tournament.key(), minutes));
                }
            }
        }
        return due;
    }

    /** Records that the warning was sent, so it is not sent again. */
    void markWarned(String name, int minutes) {
        find(name).ifPresent(tournament -> tournament.markAnnounced(minutes));
    }

    /**
     * The teleport of the tournament ran (or could not run): clears its schedule.
     *
     * @return true when there was a schedule to clear
     */
    boolean markFired(String name) {
        Optional<ToffyTournament> found = find(name);
        if (found.isEmpty() || found.get().scheduledAt() == null) {
            return false;
        }
        found.get().setScheduledAt(null);
        save();
        return true;
    }

    /** Who asks for a group placement: the player choosing with {@code /anotar}, staff, or a forced staff change. */
    enum GroupMode {
        PLAYER, STAFF, FORCED
    }

    enum GroupResult {
        ASSIGNED, MOVED, ALREADY_IN_GROUP, UNKNOWN_TOURNAMENT, UNKNOWN_GROUP, GROUP_CLOSED, GROUP_FULL,
        GROUP_LEVEL_OUT_OF_RANGE, LEVEL_UNKNOWN, TOURNAMENT_CLOSED, TOURNAMENT_LEVEL_OUT
    }

    /**
     * Outcome of {@link #placeInGroup}. {@code registered} is true when the player was signed up to the tournament by
     * this call; {@code closedByCapacity} when that sign-up reached the capacity and closed the sign-ups.
     */
    record Placement(GroupResult result, boolean registered, boolean closedByCapacity) {
        static Placement refused(GroupResult result) {
            return new Placement(result, false, false);
        }
    }

    /**
     * Result of {@link #autoAssign}: the players placed now (player to group key), how many participants stay without a
     * group, and how many candidates were left out because their level is outside the range of the tournament.
     */
    record AutoResult(Map<UUID, String> assigned, int ungrouped, int outOfTournamentRange) {
        AutoResult {
            assigned = Map.copyOf(assigned);
        }
    }

    /**
     * What {@link #deleteGroup} took away: whether the group existed, the members it had (they are now without a group),
     * and whether a scheduled event limited to that group was cancelled too.
     */
    record GroupDeletion(boolean deleted, boolean eventCancelled, Set<UUID> members) {
        GroupDeletion {
            members = Set.copyOf(members);
        }

        static GroupDeletion none() {
            return new GroupDeletion(false, false, Set.of());
        }
    }

    /**
     * Creates a group of the tournament.
     *
     * @throws IllegalArgumentException the tournament does not exist, or the name is invalid or reserved
     * @throws IllegalStateException    the tournament already has a group with that name
     */
    ToffyGroup createGroup(String tournament, String name, boolean open) {
        ToffyTournament found = require(tournament);
        String key = ToffyGroup.keyOf(name);
        if (found.group(key).isPresent()) {
            throw new IllegalStateException("Ya existe el grupo " + found.group(key).get().displayName() + " en "
                + found.displayName() + ".");
        }
        ToffyGroup group = new ToffyGroup(key, name.trim(), open);
        found.putGroup(group);
        save();
        return group;
    }

    /**
     * Deletes a group. Its members stay signed up to the tournament, just without a group. A scheduled event limited to
     * that group is cancelled, since it would have no roster left to run for.
     */
    GroupDeletion deleteGroup(String tournament, String group) {
        Optional<ToffyTournament> found = find(tournament);
        if (found.isEmpty()) {
            return GroupDeletion.none();
        }
        ToffyTournament current = found.get();
        String key = keyOfOrNull(group);
        Optional<ToffyGroup> target = current.group(key);
        if (target.isEmpty()) {
            return GroupDeletion.none();
        }
        Set<UUID> members = target.get().members();
        boolean eventCancelled = key.equals(current.eventGroup()) && current.eventAt() != null;
        if (eventCancelled) {
            current.setEvent(null, null, null);
        }
        current.removeGroup(key);
        save();
        return new GroupDeletion(true, eventCancelled, members);
    }

    Optional<ToffyGroup> group(String tournament, String group) {
        return find(tournament).flatMap(found -> found.group(keyOfOrNull(group)));
    }

    /** Group keys of the tournament, in creation order. Empty when the tournament does not exist. */
    List<String> groupKeys(String tournament) {
        return find(tournament).map(found -> found.groups().stream().map(ToffyGroup::key).toList()).orElse(List.of());
    }

    /** Lets players choose the group with {@code /anotar} (open) or not (closed). */
    Change setGroupOpen(String tournament, String group, boolean open) {
        Optional<ToffyGroup> found = group(tournament, group);
        if (found.isEmpty()) {
            return Change.UNKNOWN;
        }
        if (found.get().open() == open) {
            return Change.UNCHANGED;
        }
        found.get().setOpen(open);
        save();
        return Change.CHANGED;
    }

    /** @return false when the tournament or the group does not exist; @throws IllegalArgumentException below 1 */
    boolean setGroupCapacity(String tournament, String group, Integer capacity) {
        if (capacity != null && capacity < 1) {
            throw new IllegalArgumentException("El cupo del grupo tiene que ser al menos 1.");
        }
        Optional<ToffyGroup> found = group(tournament, group);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setCapacity(capacity);
        save();
        return true;
    }

    /** @throws IllegalArgumentException when min is above max or one of them is negative */
    boolean setGroupLevelRange(String tournament, String group, Double min, Double max) {
        if ((min != null && min < 0) || (max != null && max < 0) || (min != null && max != null && min > max)) {
            throw new IllegalArgumentException("El rango de nivel tiene que ser 0 <= minimo <= maximo.");
        }
        Optional<ToffyGroup> found = group(tournament, group);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setLevelRange(min, max);
        save();
        return true;
    }

    /** Replaces every place of the group with {@code place}. */
    boolean setGroupPlace(String tournament, String group, ToffyStoredLocation place) {
        Optional<ToffyGroup> found = group(tournament, group);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setPlaces(List.of(place));
        save();
        return true;
    }

    boolean addGroupPlace(String tournament, String group, ToffyStoredLocation place) {
        Optional<ToffyGroup> found = group(tournament, group);
        if (found.isEmpty()) {
            return false;
        }
        found.get().addPlace(place);
        save();
        return true;
    }

    /** @return false when the group does not exist or had no place */
    boolean clearGroupPlaces(String tournament, String group) {
        Optional<ToffyGroup> found = group(tournament, group);
        if (found.isEmpty() || found.get().places().isEmpty()) {
            return false;
        }
        found.get().setPlaces(List.of());
        save();
        return true;
    }

    /** Whether {@code /anotar <torneo>} lists the open groups instead of signing up (see {@code grupo-obligatorio}). */
    boolean setGroupMandatory(String tournament, boolean mandatory) {
        Optional<ToffyTournament> found = find(tournament);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setGroupMandatory(mandatory);
        save();
        return true;
    }

    /**
     * Places a participant (or a new one, see {@link GroupMode}) in a group. A player is in at most one group: leaving
     * the previous one is part of the change. Every check of the group and of the tournament applies to
     * {@link GroupMode#PLAYER} and {@link GroupMode#STAFF}; {@link GroupMode#FORCED} skips them all.
     *
     * @param level real level of the player, or null when it cannot be read (offline)
     */
    Placement placeInGroup(String tournament, String group, UUID playerId, String playerName, Double level,
                           GroupMode mode) {
        Optional<ToffyTournament> found = find(tournament);
        if (found.isEmpty()) {
            return Placement.refused(GroupResult.UNKNOWN_TOURNAMENT);
        }
        ToffyTournament current = found.get();
        Optional<ToffyGroup> target = current.group(keyOfOrNull(group));
        if (target.isEmpty()) {
            return Placement.refused(GroupResult.UNKNOWN_GROUP);
        }
        ToffyGroup destination = target.get();
        if (destination.members().contains(playerId)) {
            return Placement.refused(GroupResult.ALREADY_IN_GROUP);
        }
        boolean registered = false;
        boolean closed = false;
        if (mode != GroupMode.FORCED) {
            Optional<ToffyGroupRules.Refusal> refusal = ToffyGroupRules.refusal(destination, playerId, level,
                mode == GroupMode.PLAYER);
            if (refusal.isPresent()) {
                return Placement.refused(switch (refusal.get()) {
                    case CLOSED -> GroupResult.GROUP_CLOSED;
                    case FULL -> GroupResult.GROUP_FULL;
                    case LEVEL_OUT_OF_RANGE -> GroupResult.GROUP_LEVEL_OUT_OF_RANGE;
                    case LEVEL_UNKNOWN -> GroupResult.LEVEL_UNKNOWN;
                });
            }
            if (!current.participants().containsKey(playerId)) {
                if (level == null && current.hasLevelRange()) {
                    return Placement.refused(GroupResult.LEVEL_UNKNOWN);
                }
                RegisterResult signUp = register(tournament, playerId, playerName, level == null ? 0.0 : level);
                switch (signUp) {
                    case REGISTERED -> registered = true;
                    case REGISTERED_CLOSED -> {
                        registered = true;
                        closed = true;
                    }
                    case CLOSED -> {
                        return Placement.refused(GroupResult.TOURNAMENT_CLOSED);
                    }
                    case LEVEL_OUT_OF_RANGE -> {
                        return Placement.refused(GroupResult.TOURNAMENT_LEVEL_OUT);
                    }
                    default -> {
                        return Placement.refused(GroupResult.UNKNOWN_TOURNAMENT);
                    }
                }
            }
        } else if (!current.participants().containsKey(playerId)) {
            current.putParticipant(playerId, playerName);
            registered = true;
        }
        boolean moved = current.groupOf(playerId).isPresent();
        for (ToffyGroup other : current.groups()) {
            other.removeMember(playerId);
        }
        destination.addMember(playerId);
        save();
        return new Placement(moved ? GroupResult.MOVED : GroupResult.ASSIGNED, registered, closed);
    }

    /** Takes the player out of its group. The sign-up to the tournament stays. False when it had no group. */
    boolean leaveGroup(String tournament, UUID playerId) {
        Optional<ToffyTournament> found = find(tournament);
        if (found.isEmpty()) {
            return false;
        }
        boolean removed = false;
        for (ToffyGroup group : found.get().groups()) {
            removed |= group.removeMember(playerId);
        }
        if (removed) {
            save();
        }
        return removed;
    }

    Optional<String> groupOf(String tournament, UUID playerId) {
        return find(tournament).flatMap(found -> found.groupOf(playerId));
    }

    /**
     * Places players into the groups of the tournament with {@link ToffyGroupAssigner}. Only participants with a level
     * in {@code levels} are considered (offline players have none and keep what they have). Without {@code reshuffle}
     * only the ones without a group are placed; with it, the placed players are first taken out of their groups.
     *
     * @return the placed players, and the participants that are still without a group
     */
    AutoResult autoAssign(String tournament, boolean parejo, boolean reshuffle, Map<UUID, Double> levels,
                          Random random) {
        ToffyTournament current = require(tournament);
        Map<UUID, Double> candidates = new LinkedHashMap<>();
        int outOfRange = 0;
        for (UUID id : current.participants().keySet()) {
            Double level = levels.get(id);
            if (level == null || (!reshuffle && current.groupOf(id).isPresent())) {
                continue;
            }
            if (!current.allowsLevel(level)) {
                outOfRange++;
                continue;
            }
            candidates.put(id, level);
        }
        for (UUID id : candidates.keySet()) {
            for (ToffyGroup group : current.groups()) {
                group.removeMember(id);
            }
        }
        List<ToffyGroupAssigner.Slot> slots = current.groups().stream()
            .map(group -> new ToffyGroupAssigner.Slot(group.key(), group.capacity(), group.minLevel(),
                group.maxLevel(), group.members().size()))
            .toList();
        ToffyGroupAssigner.Result result = ToffyGroupAssigner.assign(candidates, slots, parejo, random);
        result.assigned().forEach((id, key) -> current.group(key).ifPresent(group -> group.addMember(id)));
        save();
        int ungrouped = (int) current.participants().keySet().stream()
            .filter(id -> current.groupOf(id).isEmpty())
            .count();
        return new AutoResult(result.assigned(), ungrouped, outOfRange);
    }

    /** The group key of a typed name, or the name itself when it is not a valid key (then nothing matches). */
    private static String keyOfOrNull(String name) {
        try {
            return ToffyGroup.keyOf(name);
        } catch (IllegalArgumentException invalid) {
            return "";
        }
    }

    private ToffyTournament require(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("No existe el torneo " + name + "."));
    }

    /**
     * Schedules loaded from the file: the ones more than five minutes late are dropped (with a warning), the rest fire
     * normally.
     */
    private void restoreSchedules() {
        long now = now();
        ZoneId zone = ZoneId.systemDefault();
        boolean dropped = false;
        for (ToffyTournament tournament : tournaments.values()) {
            Long at = tournament.scheduledAt();
            if (at != null) {
                if (at < now - missedAfterMillis) {
                    log.accept(missed("teletransporte", tournament, at, zone));
                    tournament.setScheduledAt(null);
                    dropped = true;
                } else {
                    markPassedWarnings(tournament, at, now);
                }
            }
            Long closeAt = tournament.closeAt();
            if (closeAt != null && closeAt < now - missedAfterMillis) {
                // Safe direction: a close missed while the server was off closes the sign-ups now (no broadcast).
                log.accept("Cierre programado del torneo " + tournament.displayName() + " para "
                    + ToffyTournamentTime.dateTime(closeAt, zone)
                    + " perdido (el servidor estaba apagado): inscripciones cerradas al cargar.");
                tournament.setOpen(false);
                dropped = true;
            }
            Long eventAt = tournament.eventAt();
            if (eventAt != null) {
                if (eventAt < now - missedAfterMillis) {
                    log.accept(missed("evento " + tournament.eventType(), tournament, eventAt, zone));
                    tournament.setEvent(null, null, null);
                    dropped = true;
                } else {
                    markPassedEventWarnings(tournament, eventAt, now);
                }
            }
        }
        if (dropped) {
            try {
                save();
            } catch (UncheckedIOException failure) {
                log.accept("No se pudo guardar torneos.yml sin los programados descartados: "
                    + failure.getCause().getMessage());
            }
        }
    }

    private String missed(String what, ToffyTournament tournament, long at, ZoneId zone) {
        return "Programado (" + what + ") del torneo " + tournament.displayName() + " para "
            + ToffyTournamentTime.dateTime(at, zone) + " descartado: el servidor estaba apagado y paso el horario.";
    }

    private void markPassedEventWarnings(ToffyTournament tournament, long at, long now) {
        for (int minutes : eventWarnings) {
            if (at - minutes * MINUTE_MILLIS <= now) {
                tournament.markEventAnnounced(minutes);
            }
        }
    }

    private void markPassedWarnings(ToffyTournament tournament, long at, long now) {
        for (int minutes : teleportWarnings) {
            if (at - minutes * MINUTE_MILLIS <= now) {
                tournament.markAnnounced(minutes);
            }
        }
    }

    private void save() {
        try {
            storage.save(tournaments.values());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
