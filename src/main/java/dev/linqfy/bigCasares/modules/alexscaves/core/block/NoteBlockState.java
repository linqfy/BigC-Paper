package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import java.util.List;
import java.util.Optional;

/**
 * One state of {@code minecraft:note_block}. Module blocks are drawn through the
 * states with index {@code >= FIRST_CUSTOM} (see {@code alexscaves/blocks.yml});
 * every {@code instrument=harp} state stays a vanilla note block.
 *
 * @param index {@code instrument * 50 + note * 2 + (powered ? 1 : 0)} over {@link #INSTRUMENTS}
 */
public record NoteBlockState(int index) {
    /** Order used by the allocation; must match tools/alexscaves/generate_content.py. */
    public static final List<String> INSTRUMENTS = List.of(
        "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime", "xylophone",
        "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling");
    public static final int COUNT = 16 * 25 * 2;
    public static final int FIRST_CUSTOM = 50;

    public NoteBlockState {
        if (index < 0 || index >= COUNT) {
            throw new IllegalArgumentException("Note block state out of range: " + index);
        }
    }

    public String instrument() {
        return INSTRUMENTS.get(index / 50);
    }

    public int note() {
        return (index % 50) / 2;
    }

    public boolean powered() {
        return index % 2 == 1;
    }

    public boolean isCustom() {
        return index >= FIRST_CUSTOM;
    }

    /** Block data string, e.g. {@code minecraft:note_block[instrument=bass,note=3,powered=false]}. */
    public String blockDataString() {
        return "minecraft:note_block[instrument=" + instrument() + ",note=" + note() + ",powered=" + powered() + "]";
    }

    public static Optional<NoteBlockState> of(String instrument, int note, boolean powered) {
        int instrumentIndex = INSTRUMENTS.indexOf(instrument);
        if (instrumentIndex < 0 || note < 0 || note > 24) {
            return Optional.empty();
        }
        return Optional.of(new NoteBlockState(instrumentIndex * 50 + note * 2 + (powered ? 1 : 0)));
    }
}
