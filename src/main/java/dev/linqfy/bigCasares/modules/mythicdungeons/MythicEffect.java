package dev.linqfy.bigCasares.modules.mythicdungeons;

import java.util.Objects;

/**
 * Un efecto de pocion que aplica un mob al pegar (o en su aura), por clave de minecraft ({@code "slowness"}) para que
 * el catalogo no dependa del registro del servidor.
 */
public record MythicEffect(String effectKey, int durationTicks, int amplifier) {

    public MythicEffect {
        Objects.requireNonNull(effectKey, "effectKey");
        if (effectKey.isBlank() || durationTicks <= 0 || amplifier < 0) {
            throw new IllegalArgumentException("invalid mythic effect: " + effectKey);
        }
    }

    public static MythicEffect of(String effectKey, int seconds, int amplifier) {
        return new MythicEffect(effectKey, seconds * 20, amplifier);
    }
}
