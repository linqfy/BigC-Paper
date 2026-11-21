package dev.linqfy.bigCasares.modules.geyser;

/**
 * A custom item the Bedrock client may consume: Geyser tells Bedrock how long and with which animation, and the Java
 * server then runs its own consumable component.
 *
 * @param seconds   how long the item takes to be consumed, more than zero
 * @param animation how it is held while it is consumed
 */
public record GeyserItemConsumption(float seconds, Animation animation) {

    public enum Animation {
        EAT,
        DRINK
    }

    public GeyserItemConsumption {
        if (!(seconds > 0f)) {
            throw new IllegalArgumentException("seconds must be more than zero: " + seconds);
        }
        if (animation == null) {
            throw new IllegalArgumentException("animation must not be null");
        }
    }
}
