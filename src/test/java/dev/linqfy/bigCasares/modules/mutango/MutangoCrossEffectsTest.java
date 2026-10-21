package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutangoCrossEffectsTest {

    @Test
    void daTodoLoDelTotemPeroMasFuerteYMasLargo() {
        for (MutangoCrossEffects.Effect totem : MutangoCrossEffects.TOTEM) {
            MutangoCrossEffects.Effect cross = MutangoCrossEffects.CROSS.stream()
                .filter(effect -> effect.key().equals(totem.key())).findFirst()
                .orElseThrow(() -> new AssertionError("la cruz no da " + totem.key()));
            assertTrue(cross.ticks() > totem.ticks(), totem.key() + " tiene que durar mas");
            assertTrue(cross.amplifier() >= totem.amplifier(), totem.key() + " no puede ser mas debil");
        }
    }

    @Test
    void daMasEfectosQueElTotem() {
        assertTrue(MutangoCrossEffects.CROSS.size() > MutangoCrossEffects.TOTEM.size());
    }

    @Test
    void cadaEfectoUnaSolaVezYConNombreDeMinecraft() {
        Set<String> keys = new HashSet<>();
        for (MutangoCrossEffects.Effect effect : MutangoCrossEffects.CROSS) {
            assertTrue(keys.add(effect.key()), "repetido: " + effect.key());
            assertTrue(effect.key().matches("[a-z_]+"), effect.key());
            assertTrue(effect.ticks() > 0 && effect.amplifier() >= 0, effect.key());
        }
    }

    @Test
    void soloEfectosBuenos() {
        Set<String> good = Set.of("regeneration", "absorption", "fire_resistance", "resistance", "speed", "strength",
            "water_breathing", "health_boost", "night_vision", "jump_boost", "haste", "saturation",
            "instant_health", "slow_falling", "conduit_power", "dolphins_grace", "hero_of_the_village", "luck");
        MutangoCrossEffects.CROSS.forEach(effect -> assertTrue(good.contains(effect.key()), effect.key()));
    }

    @Test
    void teDejaConMasVidaQueElTotem() {
        assertEquals(10.0, MutangoCrossEffects.healthAfter(20.0), 1e-9, "con 10 corazones te deja en 5");
        assertTrue(MutangoCrossEffects.healthAfter(20.0) > 1.0, "el totem te deja en medio corazon");
        assertEquals(1.0, MutangoCrossEffects.healthAfter(1.0), 1e-9, "nunca menos que el totem");
    }
}
