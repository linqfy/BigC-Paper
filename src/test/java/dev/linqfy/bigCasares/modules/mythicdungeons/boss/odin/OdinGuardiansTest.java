package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OdinGuardiansTest {

    @Test
    void theNamedRavensAndWolvesAlwaysCome() {
        List<OdinGuardians.Guardian> plan = OdinGuardians.plan(1);
        assertEquals(List.of("Hugin", "Munin"), names(plan, OdinGuardians.Kind.RAVEN));
        assertEquals(List.of("Geri", "Freki"), names(plan, OdinGuardians.Kind.WOLF));
        plan.stream().filter(guardian -> guardian.kind() == OdinGuardians.Kind.RAVEN)
            .forEach(raven -> assertTrue(raven.height() > 1.0, "ravens fly"));
    }

    @Test
    void einherjarGrowWithThePartyUpToTheCap() {
        assertEquals(3, OdinGuardians.einherjarFor(0));
        assertEquals(3, OdinGuardians.einherjarFor(1));
        assertEquals(4, OdinGuardians.einherjarFor(2));
        assertEquals(6, OdinGuardians.einherjarFor(4));
        assertEquals(7, OdinGuardians.einherjarFor(10));
        assertEquals(4 + 4, OdinGuardians.plan(2).size());
        assertEquals(4, names(OdinGuardians.plan(2), OdinGuardians.Kind.EINHERJAR).size());
    }

    @Test
    void guardiansAppearInFrontOfTheThrone() {
        for (OdinGuardians.Guardian guardian : OdinGuardians.plan(4)) {
            assertTrue(Math.abs(guardian.angleDegrees()) <= 60.0, guardian.toString());
            assertTrue(guardian.distance() >= 2.0 && guardian.distance() <= 7.0, guardian.toString());
        }
    }

    @Test
    void extraModelsAreTheThreeSummons() {
        assertEquals(Set.of("bigcasares_myth_odin_raven", "bigcasares_myth_odin_wolf",
            "bigcasares_myth_odin_einherjar"), Set.copyOf(OdinEncounter.extraModelKeys()));
        assertTrue(MythicBossRegistry.extraModelKeys().containsAll(OdinEncounter.extraModelKeys()));
    }

    private static List<String> names(List<OdinGuardians.Guardian> plan, OdinGuardians.Kind kind) {
        return plan.stream().filter(guardian -> guardian.kind() == kind).map(OdinGuardians.Guardian::name).toList();
    }
}
