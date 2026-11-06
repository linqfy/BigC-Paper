package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import java.util.List;
import java.util.Locale;

/** Player-owned, deterministic demo state. No scheduler, I/O or global player state. */
public final class SequenceGame {
    public static final List<String> ORDER = List.of("A", "C", "B", "D");
    private int completed;
    private int fadePhase;

    public boolean click(String letterOrElementId) {
        if (letterOrElementId == null || fadePhase != 0 || finished()) return false;
        String letter = letterOrElementId.startsWith("letter_") ? letterOrElementId.substring(7) : letterOrElementId;
        if (!ORDER.get(completed).equals(letter.toUpperCase(Locale.ROOT))) return false;
        fadePhase = 1;
        return true;
    }

    /** Each invocation advances one of three fade frames, then removes the letter. */
    public String tick() {
        if (fadePhase > 0 && ++fadePhase > 3) { completed++; fadePhase = 0; }
        return frameId();
    }

    public String frameId() {
        return fadePhase > 0 ? "seq_f" + completed + "_" + fadePhase : stableFrameId(completed);
    }

    public boolean finished() { return completed == ORDER.size(); }
    public void reset() { completed = 0; fadePhase = 0; }

    static String stableFrameId(int completed) { return completed == 0 ? "sequence_demo" : "seq_s" + completed; }
}
