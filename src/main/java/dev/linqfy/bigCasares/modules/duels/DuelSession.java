package dev.linqfy.bigCasares.modules.duels;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One accepted duel, from the kit menu until both players are back home. */
final class DuelSession {

    enum Phase {
        /** Players are still where they were, choosing the kit. */
        DRAFT,
        /** In the arena, frozen. */
        COUNTDOWN,
        FIGHT,
        /** Result shown; players are sent home after a short pause. */
        ENDING
    }

    private final UUID first;
    private final UUID second;
    private final DuelArenaSize arena;
    private final long wager;
    private final DuelKitDraft draft;
    private final Set<Long> placedBlocks = new HashSet<>();
    private String kitId;
    private Phase phase;
    private long phaseEndsAt;
    private int slot = -1;

    DuelSession(UUID first, UUID second, DuelArenaSize arena, long wager, String kitId, DuelKitDraft draft) {
        this.first = first;
        this.second = second;
        this.arena = arena;
        this.wager = wager;
        this.kitId = kitId;
        this.draft = draft;
        this.phase = Phase.DRAFT;
    }

    UUID first() {
        return first;
    }

    UUID second() {
        return second;
    }

    List<UUID> players() {
        return List.of(first, second);
    }

    UUID opponent(UUID player) {
        return first.equals(player) ? second : first;
    }

    DuelArenaSize arena() {
        return arena;
    }

    long wager() {
        return wager;
    }

    DuelKitDraft draft() {
        return draft;
    }

    String kitId() {
        return kitId;
    }

    void kitId(String kitId) {
        this.kitId = kitId;
    }

    Phase phase() {
        return phase;
    }

    void phase(Phase phase, long endsAt) {
        this.phase = phase;
        this.phaseEndsAt = endsAt;
    }

    long phaseEndsAt() {
        return phaseEndsAt;
    }

    void phaseEndsAt(long endsAt) {
        this.phaseEndsAt = endsAt;
    }

    int slot() {
        return slot;
    }

    void slot(int slot) {
        this.slot = slot;
    }

    boolean inArena() {
        return phase != Phase.DRAFT;
    }

    void placed(int x, int y, int z) {
        placedBlocks.add(blockKey(x, y, z));
    }

    boolean wasPlaced(int x, int y, int z) {
        return placedBlocks.contains(blockKey(x, y, z));
    }

    void broken(int x, int y, int z) {
        placedBlocks.remove(blockKey(x, y, z));
    }

    static long blockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
