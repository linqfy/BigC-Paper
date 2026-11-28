package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La tabla de partes de las figuras: nombres de modelo, pivotes y movimientos de la especificacion. */
class BackroomsRigsTest {

    private static Set<String> models(BackroomsKind kind) {
        Set<String> out = new HashSet<>();
        BackroomsRigs.parts(kind).forEach(part -> out.add(part.model()));
        return out;
    }

    private static BackroomsRigs.Part part(BackroomsKind kind, BackroomsRigs.Role role) {
        return BackroomsRigs.parts(kind).stream().filter(p -> p.role() == role).findFirst().orElseThrow();
    }

    @Test
    void everyEntityHasItsModelsWithTheAgreedNames() {
        assertEquals(Set.of("sonrisa"), models(BackroomsKind.SONRISA));
        assertEquals(Set.of("e666_cuerpo", "e666_cabeza", "e666_brazo", "e666_pierna"), models(BackroomsKind.E666));
        assertEquals(Set.of("jc_tunica", "jc_cabeza", "jc_brazo"), models(BackroomsKind.JUANCARLOS));
        assertEquals(Set.of("arana_cuerpo", "arana_pata"), models(BackroomsKind.ARANA));
        assertEquals(Set.of("payaso_cuerpo", "payaso_cabeza"), models(BackroomsKind.PAYASO));
    }

    @Test
    void partCountsMatchTheSpec() {
        assertEquals(1, BackroomsRigs.parts(BackroomsKind.SONRISA).size());
        assertEquals(6, BackroomsRigs.parts(BackroomsKind.E666).size());
        assertEquals(4, BackroomsRigs.parts(BackroomsKind.JUANCARLOS).size());
        assertEquals(5, BackroomsRigs.parts(BackroomsKind.ARANA).size());
        assertEquals(2, BackroomsRigs.parts(BackroomsKind.PAYASO).size());
    }

    @Test
    void theHeightsAreTheSpecOnes() {
        assertEquals(2.9f, BackroomsRigs.height(BackroomsKind.E666));
        assertEquals(3.35f, BackroomsRigs.height(BackroomsKind.JUANCARLOS));
        assertEquals(3.4f, BackroomsRigs.height(BackroomsKind.ARANA));
        assertEquals(2.35f, BackroomsRigs.height(BackroomsKind.PAYASO));
    }

    @Test
    void thePivotsAreTheSpecOnes() {
        BackroomsRigs.Part head = part(BackroomsKind.E666, BackroomsRigs.Role.HEAD);
        assertEquals(2.10f, head.y(), 1e-6);
        assertEquals(1.30f, part(BackroomsKind.E666, BackroomsRigs.Role.BODY).y(), 1e-6);
        assertEquals(2.00f, part(BackroomsKind.E666, BackroomsRigs.Role.ARM_L).y(), 1e-6);
        assertEquals(-0.36f, part(BackroomsKind.E666, BackroomsRigs.Role.ARM_L).x(), 1e-6);
        assertEquals(0.14f, part(BackroomsKind.E666, BackroomsRigs.Role.LEG_R).x(), 1e-6);
        assertEquals(1.40f, part(BackroomsKind.JUANCARLOS, BackroomsRigs.Role.BODY).y(), 1e-6);
        assertEquals(2.60f, part(BackroomsKind.JUANCARLOS, BackroomsRigs.Role.HEAD).y(), 1e-6);
        assertEquals(0.42f, part(BackroomsKind.JUANCARLOS, BackroomsRigs.Role.ARM_R).x(), 1e-6);
        assertEquals(3.20f, part(BackroomsKind.ARANA, BackroomsRigs.Role.BODY).y(), 1e-6);
        assertEquals(1.00f, part(BackroomsKind.PAYASO, BackroomsRigs.Role.BODY).y(), 1e-6);
        assertEquals(1.70f, part(BackroomsKind.PAYASO, BackroomsRigs.Role.HEAD).y(), 1e-6);
    }

    @Test
    void leftAndRightLimbsMirrorEachOther() {
        for (BackroomsKind kind : List.of(BackroomsKind.E666, BackroomsKind.JUANCARLOS)) {
            BackroomsRigs.Part armL = part(kind, BackroomsRigs.Role.ARM_L);
            BackroomsRigs.Part armR = part(kind, BackroomsRigs.Role.ARM_R);
            assertEquals(-armL.x(), armR.x(), 1e-6);
            assertEquals(armL.y(), armR.y(), 1e-6);
            assertEquals(armL.model(), armR.model());
        }
        BackroomsRigs.Part legL = part(BackroomsKind.E666, BackroomsRigs.Role.LEG_L);
        BackroomsRigs.Part legR = part(BackroomsKind.E666, BackroomsRigs.Role.LEG_R);
        assertEquals(-legL.x(), legR.x(), 1e-6);
        assertEquals(legL.y(), legR.y(), 1e-6);
    }

    @Test
    void theSpiderLegsPointToTheFourDiagonalsOpenedTwentyDegrees() {
        Set<Float> yaws = new HashSet<>();
        for (BackroomsRigs.Part part : BackroomsRigs.parts(BackroomsKind.ARANA)) {
            if (part.model().equals("arana_pata")) {
                yaws.add(part.yaw());
                assertEquals(2.2f, part.scale());
                assertEquals(20f, part.open());
                assertEquals(0.30f, Math.abs(part.x()), 1e-6);
                assertEquals(0.30f, Math.abs(part.z()), 1e-6);
                assertEquals(3.10f, part.y(), 1e-6);
            }
        }
        assertEquals(Set.of(45f, 135f, 225f, 315f), yaws);
    }

    @Test
    void theSpiderLegsWalkInAlternatingDiagonalPairs() {
        BackroomsRigs.Part a = part(BackroomsKind.ARANA, BackroomsRigs.Role.LEG_PAIR_A);
        BackroomsRigs.Part b = part(BackroomsKind.ARANA, BackroomsRigs.Role.LEG_PAIR_B);
        assertEquals(20f, BackroomsRigs.pose(BackroomsKind.ARANA, a, BackroomsRigs.Mode.IDLE, 7, 1).x(), 1e-6,
            "quieta, la pata solo esta abierta");
        assertEquals(a.yaw(), BackroomsRigs.pose(BackroomsKind.ARANA, a, BackroomsRigs.Mode.WALK, 7, 1).y(), 1e-6);
        for (long t = 0; t < 40; t++) {
            float pa = BackroomsRigs.pose(BackroomsKind.ARANA, a, BackroomsRigs.Mode.WALK, t, 1).x();
            float pb = BackroomsRigs.pose(BackroomsKind.ARANA, b, BackroomsRigs.Mode.WALK, t, 1).x();
            assertEquals(40f, pa + pb, 1e-4, "un par sube lo que baja el otro");
            assertTrue(Math.abs(pa - 20f) <= 20.0001f);
        }
    }

    @Test
    void theE666WalksWithOppositeLimbsAndTwitches() {
        BackroomsRigs.Part legL = part(BackroomsKind.E666, BackroomsRigs.Role.LEG_L);
        BackroomsRigs.Part legR = part(BackroomsKind.E666, BackroomsRigs.Role.LEG_R);
        BackroomsRigs.Part armL = part(BackroomsKind.E666, BackroomsRigs.Role.ARM_L);
        float maxLeg = 0;
        float maxArm = 0;
        for (long t = 0; t < 100; t++) {
            float l = BackroomsRigs.pose(BackroomsKind.E666, legL, BackroomsRigs.Mode.WALK, t, 1).x();
            float r = BackroomsRigs.pose(BackroomsKind.E666, legR, BackroomsRigs.Mode.WALK, t, 1).x();
            float arm = BackroomsRigs.pose(BackroomsKind.E666, armL, BackroomsRigs.Mode.WALK, t, 1).x();
            assertEquals(-l, r, 1e-4);
            assertEquals(-l * 12f / 25f, arm, 1e-4, "el brazo va al reves de su pierna");
            maxLeg = Math.max(maxLeg, Math.abs(l));
            maxArm = Math.max(maxArm, Math.abs(arm));
        }
        assertTrue(maxLeg <= 25f && maxLeg > 24f);
        assertTrue(maxArm <= 12f && maxArm > 11f);
        BackroomsRigs.Part head = part(BackroomsKind.E666, BackroomsRigs.Role.HEAD);
        Set<Float> tics = new HashSet<>();
        for (long t = 0; t < 400; t++) {
            tics.add(BackroomsRigs.pose(BackroomsKind.E666, head, BackroomsRigs.Mode.WALK, t, 1).z());
        }
        assertEquals(Set.of(0f, 20f, -20f), tics, "el tic es de golpe, a un lado o al otro");
        assertEquals(0f, BackroomsRigs.tic(3));
        assertEquals(20f, BackroomsRigs.tic(1));
    }

    @Test
    void juanCarlosSwaysWhileWalkingAndReachesForwardWhenGrabbing() {
        BackroomsRigs.Part tunic = part(BackroomsKind.JUANCARLOS, BackroomsRigs.Role.BODY);
        BackroomsRigs.Part arm = part(BackroomsKind.JUANCARLOS, BackroomsRigs.Role.ARM_L);
        float max = 0;
        for (long t = 0; t < 100; t++) {
            max = Math.max(max, Math.abs(BackroomsRigs.pose(BackroomsKind.JUANCARLOS, tunic,
                BackroomsRigs.Mode.WALK, t, 1).z()));
        }
        assertTrue(max <= 3f && max > 2.9f);
        assertEquals(0f, BackroomsRigs.pose(BackroomsKind.JUANCARLOS, arm, BackroomsRigs.Mode.WALK, 3, 1).x());
        assertEquals(-80f, BackroomsRigs.pose(BackroomsKind.JUANCARLOS, arm, BackroomsRigs.Mode.GRAB, 3, 1).x());
    }

    @Test
    void theClownLeansHisHeadAndBodyTowardsTheWall() {
        BackroomsRigs.Part head = part(BackroomsKind.PAYASO, BackroomsRigs.Role.HEAD);
        BackroomsRigs.Part body = part(BackroomsKind.PAYASO, BackroomsRigs.Role.BODY);
        float rightHead = BackroomsRigs.pose(BackroomsKind.PAYASO, head, BackroomsRigs.Mode.SPY, 0, 1).z();
        float leftHead = BackroomsRigs.pose(BackroomsKind.PAYASO, head, BackroomsRigs.Mode.SPY, 0, -1).z();
        assertEquals(25f, Math.abs(rightHead));
        assertEquals(-rightHead, leftHead);
        assertEquals(8f, Math.abs(BackroomsRigs.pose(BackroomsKind.PAYASO, body, BackroomsRigs.Mode.SPY, 0, 1).z()));
        assertEquals(0f, BackroomsRigs.pose(BackroomsKind.PAYASO, head, BackroomsRigs.Mode.IDLE, 0, 1).z());
    }

    @Test
    void theSonrisaIsOneImageAtScaleTwoPointTwo() {
        BackroomsRigs.Part only = BackroomsRigs.parts(BackroomsKind.SONRISA).getFirst();
        assertEquals(2.2f, only.scale());
        assertEquals(0f, BackroomsRigs.pose(BackroomsKind.SONRISA, only, BackroomsRigs.Mode.WALK, 5, 1).x());
    }

    @Test
    void aLowCeilingShrinksEveryFigureAndAHighOneDoesNot() {
        for (BackroomsKind kind : BackroomsKind.values()) {
            assertEquals(1f, BackroomsRigs.fit(kind, 8f), 1e-6, kind.name());
            float low = BackroomsRigs.fit(kind, 2.0f);
            assertTrue(low < 1f && low > 0f, kind.name());
            assertEquals((2.0f - 0.2f) / BackroomsRigs.height(kind), low, 1e-6);
        }
        assertEquals(180f, BackroomsRigs.FRONT_YAW_OFFSET);
    }

    @Test
    void theFaceIsInsideTheFigure() {
        for (BackroomsKind kind : BackroomsKind.values()) {
            assertTrue(BackroomsRigs.faceHeight(kind) <= BackroomsRigs.height(kind), kind.name());
        }
    }
}
