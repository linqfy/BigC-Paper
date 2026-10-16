package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Igual que DonPollosMinionArmor: cobre, hierro, diamante, netherite y despues encantamientos. */
class MutangoArmorTest {

    @Test
    void cadaVictoriaUnMaterialMejor() {
        assertEquals(MutangoArmor.Tier.NONE, MutangoArmor.tier(0));
        assertEquals(MutangoArmor.Tier.COBRE, MutangoArmor.tier(1));
        assertEquals(MutangoArmor.Tier.HIERRO, MutangoArmor.tier(2));
        assertEquals(MutangoArmor.Tier.DIAMANTE, MutangoArmor.tier(3));
        assertEquals(MutangoArmor.Tier.NETHERITE, MutangoArmor.tier(4));
        assertEquals(MutangoArmor.Tier.NETHERITE, MutangoArmor.tier(40));
    }

    @Test
    void cadaMaterialProtegeMasQueElAnterior() {
        MutangoArmor.Tier[] tiers = MutangoArmor.Tier.values();
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(tiers[i].armor + tiers[i].toughness > tiers[i - 1].armor + tiers[i - 1].toughness,
                tiers[i].name());
        }
        assertEquals(20, MutangoArmor.Tier.NETHERITE.armor, "como un juego completo de netherite");
    }

    @Test
    void conNetheriteArrancaLaProteccionYDespuesLasEspinas() {
        assertEquals(0, MutangoArmor.protection(4));
        assertEquals(1, MutangoArmor.protection(5));
        assertEquals(5, MutangoArmor.protection(9));
        assertEquals(5, MutangoArmor.protection(30));
        assertEquals(0, MutangoArmor.thorns(9));
        assertEquals(1, MutangoArmor.thorns(10));
        assertEquals(3, MutangoArmor.thorns(30));
    }

    @Test
    void laProteccionFrenaComoEnMinecraft() {
        assertEquals(1.0, MutangoArmor.protectionMultiplier(0), 1e-9);
        assertEquals(1.0, MutangoArmor.protectionMultiplier(4), 1e-9);
        assertEquals(0.84, MutangoArmor.protectionMultiplier(5), 1e-9, "Proteccion I en las 4 piezas: 16%");
        assertEquals(0.2, MutangoArmor.protectionMultiplier(9), 1e-9, "tope de 80%");
        assertEquals(0.2, MutangoArmor.protectionMultiplier(99), 1e-9);
    }

    @Test
    void lasEspinasSoloConEspinas() {
        assertEquals(0.0, MutangoArmor.thornsChance(9), 1e-9);
        double one = MutangoArmor.thornsChance(10);
        double three = MutangoArmor.thornsChance(12);
        assertTrue(one > 0 && one < three && three < 1, one + " " + three);
    }

    @Test
    void elModeloConArmaduraSoloSiTieneVersion() {
        assertEquals("bigcasares_boss_mutango", MutangoArmor.model("bigcasares_boss_mutango", 0));
        assertEquals("bigcasares_boss_mutango_cobre", MutangoArmor.model("bigcasares_boss_mutango", 1));
        assertEquals("bigcasares_boss_mutango_3ojos_netherite", MutangoArmor.model("bigcasares_boss_mutango_3ojos", 9));
        assertEquals("bigcasares_neguechen_diamante", MutangoArmor.model("bigcasares_neguechen", 3));
        // el Sol, la Luna, las estatuas y el cubo no tienen armadura
        assertEquals("bigcasares_sol", MutangoArmor.model("bigcasares_sol", 4));
        assertEquals("bigcasares_boss_mutango_dormido", MutangoArmor.model("bigcasares_boss_mutango_dormido", 4));
    }

    @Test
    void losCaballosLlevanSuArmadura() {
        assertNull(MutangoArmor.horseArmor(0));
        assertEquals("IRON_HORSE_ARMOR", MutangoArmor.horseArmor(2));
        assertEquals("NETHERITE_HORSE_ARMOR", MutangoArmor.horseArmor(7));
    }
}
