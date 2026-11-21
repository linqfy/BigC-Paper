package dev.linqfy.bigCasares.modules.quadrants.quests;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Estado de una persona; los reclamos pendientes nunca se reinician automáticamente. */
public final class PvpQuestState {
    public enum Claim { NONE, PENDING, PAID }

    String rivalWeek = "";
    String dominationWeek = "";
    boolean rivalStarted;
    boolean dominationStarted;
    Set<UUID> rivals = new HashSet<>();
    Set<UUID> dominated = new HashSet<>();
    Map<UUID, Integer> streaks = new HashMap<>();
    Claim rivalClaim = Claim.NONE;
    Claim dominationClaim = Claim.NONE;

    public static PvpQuestState empty() { return new PvpQuestState(); }
    public Set<UUID> rivals() { return Set.copyOf(rivals); }
    public Set<UUID> dominated() { return Set.copyOf(dominated); }
    public boolean rivalStarted() { return rivalStarted; }
    public boolean dominationStarted() { return dominationStarted; }
    public Claim rivalClaim() { return rivalClaim; }
    public Claim dominationClaim() { return dominationClaim; }

    PvpQuestState copy() {
        PvpQuestState c = empty();
        c.rivalWeek = rivalWeek; c.dominationWeek = dominationWeek;
        c.rivalStarted = rivalStarted; c.dominationStarted = dominationStarted;
        c.rivals.addAll(rivals); c.dominated.addAll(dominated); c.streaks.putAll(streaks);
        c.rivalClaim = rivalClaim; c.dominationClaim = dominationClaim;
        return c;
    }

    void roll(String week) {
        if (!rivalWeek.equals(week) && rivalClaim != Claim.PENDING) {
            rivalWeek = week; rivalStarted = false; rivals.clear(); rivalClaim = Claim.NONE;
        }
        if (!dominationWeek.equals(week) && dominationClaim != Claim.PENDING) {
            dominationWeek = week; dominationStarted = false; dominated.clear(); streaks.clear(); dominationClaim = Claim.NONE;
        }
    }
}
