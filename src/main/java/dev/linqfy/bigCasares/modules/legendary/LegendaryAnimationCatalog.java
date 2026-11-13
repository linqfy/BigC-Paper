package dev.linqfy.bigCasares.modules.legendary;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Frame sequences baked from the supplied projects; contains no combat rules. */
final class LegendaryAnimationCatalog {
    record Clip(int[] sequence, boolean loop) {
        Clip {
            sequence = sequence.clone();
            if (sequence.length == 0) throw new IllegalArgumentException("Empty animation");
        }

        @Override
        public int[] sequence() {
            return sequence.clone();
        }

        int duration() {
            return sequence.length;
        }

        int frame(long tick) {
            if (tick < 0) return sequence[0];
            if (loop) return sequence[(int) (tick % sequence.length)];
            return sequence[(int) Math.min(tick, sequence.length - 1L)];
        }
    }

    private final Map<String, Clip> clips;

    private LegendaryAnimationCatalog(Map<String, Clip> clips) {
        this.clips = Map.copyOf(clips);
    }

    static LegendaryAnimationCatalog load(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing legendary animation catalog");
        var properties = new Properties();
        properties.load(input);
        if (!"20".equals(properties.getProperty("fps")))
            throw new IllegalArgumentException("Animations require 20 Hz");
        var clips = new HashMap<String, Clip>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.endsWith(".frames")) continue;
            String name = key.substring(0, key.length() - 7);
            String loop = properties.getProperty(name + ".loop");
            if (!"true".equals(loop) && !"false".equals(loop))
                throw new IllegalArgumentException("Missing animation loop flag");
            var words = properties.getProperty(key).split(",");
            var frames = new int[words.length];
            for (int index = 0; index < words.length; index++) {
                frames[index] = Integer.parseInt(words[index]);
                if (frames[index] < 0)
                    throw new IllegalArgumentException("Invalid animation frame");
            }
            clips.put(name, new Clip(frames, Boolean.parseBoolean(loop)));
        }
        return new LegendaryAnimationCatalog(clips);
    }

    Clip clip(LegendaryWeapon weapon, String name) {
        return clips.get(weapon.id + "." + name);
    }

    String idle(LegendaryWeapon weapon) {
        return switch (weapon) {
            case BLOODLUST, WINDWEAVER -> "idle_aura";
            case SHADOW_BLADE -> "idle_flotar";
            case THUNDER_SPEAR -> "rayo";
            default -> "";
        };
    }
}
