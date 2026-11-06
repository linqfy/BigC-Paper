package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Boss-fight quick-time mechanic (shown as a transparent overlay over the world): a key appears (W, A, S, D, SPACE or CLICK) and the player must press it before the timer runs out.
 * A hit damages the boss (more with a combo); a wrong key or a timeout costs one of the player's hearts.
 * Player-owned and deterministic for a seed: no scheduler, I/O or global state. Call {@link #tick()} every game tick.
 */
public final class KeyChallenge {
    public enum Key {
        W("W"), A("A"), S("S"), D("D"), SPACE("SPACE"), CLICK("CLICK");
        public final String label;
        Key(String label) { this.label = label; }
    }
    public enum Phase { COUNTDOWN, PROMPT, GAP, WON, LOST }
    public enum Outcome { HIT, MISS, IGNORED }

    /** SCREEN: no key on screen; KEY_SCREEN: square keycap (letters, countdown); WIDE_SCREEN: wide keycap (SPACE, CLICK). */
    public static final String SCREEN = "challenge_demo", KEY_SCREEN = "challenge_key", WIDE_SCREEN = "challenge_wide",
            WIN_SCREEN = "challenge_win", LOSE_SCREEN = "challenge_lose";
    public static final String BOSS_NAME = "THE TIDE KING";
    public static final int BOSS_MAX = 100, PLAYER_MAX = 5;
    static final int COUNTDOWN_TICKS = 70;
    static final int HIT_GAP = 6, MISS_GAP = 14;

    private final Random random;
    private final BossFightSettings settings;
    private Phase phase = Phase.COUNTDOWN;
    private int phaseTicks, bossHp = BOSS_MAX, playerHp, combo, bestCombo, hits, misses, score;
    private int window, remaining;
    private Key current;
    private String feedback;
    private boolean enraged;

    public KeyChallenge(long seed) { this(seed, BossFightSettings.defaults()); }
    public KeyChallenge(long seed, BossFightSettings settings) {
        random = new Random(seed); this.settings = settings;
        playerHp = settings.lives(); window = settings.startTicks(); feedback = settings.text("get-ready");
    }
    public BossFightSettings settings() { return settings; }
    public int lives() { return settings.lives(); }
    private int countdownTicks() { return settings.show("countdown") ? COUNTDOWN_TICKS : 12; }

    public Phase phase() { return phase; }
    public Key current() { return phase == Phase.PROMPT ? current : null; }
    public int bossHp() { return bossHp; }
    public int playerHp() { return playerHp; }
    public int combo() { return combo; }
    public int score() { return score; }
    public int hits() { return hits; }
    public int misses() { return misses; }
    /** Ticks into the countdown (1-based), or -1 outside it: 1/21/41 show 3/2/1, 61 shows GO. */
    public int countdownTick() { return phase == Phase.COUNTDOWN && settings.show("countdown") ? phaseTicks : -1; }
    public boolean finished() { return phase == Phase.WON || phase == Phase.LOST; }

    /** Screen to show for the current phase: the keycap shape follows the prompt. */
    public String frameId() {
        if (phase == Phase.WON) return WIN_SCREEN;
        if (phase == Phase.LOST) return LOSE_SCREEN;
        String prompt = prompt();
        return prompt.isEmpty() ? SCREEN : prompt.length() == 1 ? KEY_SCREEN : WIDE_SCREEN;
    }
    private String prompt() {
        return switch (phase) {
            case COUNTDOWN -> !settings.show("countdown") ? "" : phaseTicks < 20 ? "3" : phaseTicks < 40 ? "2" : phaseTicks < 60 ? "1" : "";
            case PROMPT -> current.label;
            default -> "";
        };
    }
    private int lastDamage;
    /** Boss damage (out of {@link #BOSS_MAX}) dealt by the last hit. */
    public int lastDamage() { return lastDamage; }
    /**
     * Follow a real boss: its health fraction (0..1) replaces the internal boss health, so damage from swords,
     * arrows or other players counts too. A dead boss ends the fight as a win.
     */
    public void syncBoss(double fraction) {
        if (finished()) return;
        bossHp = (int) Math.round(Math.max(0, Math.min(1, fraction)) * BOSS_MAX);
        if (fraction > 0 && bossHp == 0) bossHp = 1;
        if (bossHp == 0 && phase != Phase.GAP) { feedback = settings.text("final-blow"); lastWasHit = true; phase = Phase.GAP; phaseTicks = 0; }
    }

    /** One game tick (50 ms). */
    public void tick() {
        phaseTicks++;
        switch (phase) {
            case COUNTDOWN -> { if (phaseTicks >= countdownTicks()) next(); }
            case PROMPT -> { if (--remaining <= 0) miss(settings.text("too-slow")); }
            case GAP -> {
                if (phaseTicks >= (lastWasHit ? HIT_GAP : MISS_GAP)) {
                    if (bossHp <= 0) { phase = Phase.WON; phaseTicks = 0; feedback = settings.text("victory"); }
                    else if (playerHp <= 0) { phase = Phase.LOST; phaseTicks = 0; feedback = settings.text("defeated"); }
                    else next();
                }
            }
            default -> { }
        }
    }
    private boolean lastWasHit;
    private int wrongPresses;

    public Outcome press(Key key) {
        if (phase != Phase.PROMPT || key == null) return Outcome.IGNORED;
        // Players keep moving in a real fight: one stray key per prompt is forgiven, a second one is a miss (no spamming).
        if (key != current) {
            if (++wrongPresses < 2) return Outcome.IGNORED;
            miss(settings.text("wrong-key")); return Outcome.MISS;
        }
        combo++; hits++; bestCombo = Math.max(bestCombo, combo);
        int damage = settings.hitDamage() + 2 * Math.min(combo - 1, 5);
        lastDamage = Math.min(bossHp, damage);
        bossHp = Math.max(0, bossHp - damage);
        // Faster reactions score more: up to double for an instant press.
        score += (100 + 100 * remaining / Math.max(1, window)) * combo;
        // Reaction-speed grades; the running combo has its own counter on screen.
        feedback = settings.text(bossHp == 0 ? "final-blow" : remaining * 10 >= window * 6 ? "perfect" : remaining * 10 >= window * 3 ? "great" : "good");
        window = Math.max(settings.minTicks(), window - 2);
        if (!enraged && bossHp > 0 && bossHp <= settings.enragePercent()) {
            enraged = true; window = Math.max(settings.minTicks(), window - 6); feedback = settings.text("enraged");
        }
        lastWasHit = true; phase = Phase.GAP; phaseTicks = 0;
        return Outcome.HIT;
    }

    private void miss(String why) {
        playerHp = Math.max(0, playerHp - 1); combo = 0; misses++;
        feedback = playerHp == 0 ? settings.text("you-fall") : why;
        lastWasHit = false; phase = Phase.GAP; phaseTicks = 0;
    }

    private void next() {
        var keys = settings.keys();
        Key chosen;
        if (keys.size() == 1) chosen = keys.get(0);
        else do chosen = keys.get(random.nextInt(keys.size())); while (chosen == current);
        current = chosen; remaining = window; phase = Phase.PROMPT; phaseTicks = 0; wrongPresses = 0;
        if (feedback.equals(settings.text("get-ready")) || feedback.equals(settings.text("go"))) feedback = "";
    }

    /** Live values for the challenge screens: tokens ({key}, {feedback}, ...) and bar fractions ("bar:boss"). */
    public Map<String, String> values() {
        var out = new LinkedHashMap<String, String>();
        String prompt = prompt();
        // Single letters use the 4x label, words the 3x one (both sit in the same box; one is always empty).
        out.put("key", prompt.length() == 1 ? prompt : "");
        out.put("key_word", prompt.length() > 1 ? prompt : "");
        out.put("feedback", phase == Phase.COUNTDOWN ? settings.text(phaseTicks < countdownTicks() - 10 ? "get-ready" : "go") : feedback);
        out.put("combo", combo >= 2 ? settings.text("combo") + combo : "");
        out.put("score", String.valueOf(score));
        out.put("best_combo", String.valueOf(bestCombo));
        out.put("hits", String.valueOf(hits));
        out.put("misses", String.valueOf(misses));
        out.put("lives", playerHp + "/" + settings.lives());
        out.put("boss_name", settings.text("boss-name"));
        out.put("boss_hp", String.valueOf(bossHp));
        out.put("boss_max", String.valueOf(BOSS_MAX));
        out.put("bar:boss", fraction(bossHp / (double) BOSS_MAX));
        out.put("bar:hp", fraction(playerHp / (double) settings.lives()));
        out.put("bar:timer", fraction(phase == Phase.PROMPT ? remaining / (double) window : phase == Phase.COUNTDOWN ? 1 : 0));
        return out;
    }
    private static String fraction(double value) { return String.format(Locale.ROOT, "%.4f", Math.max(0, Math.min(1, value))); }
}
