package dev.linqfy.bigCasares.modules.mutango;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los modelos que usa el plugin estan adentro del jar y tienen las animaciones y huesos que el codigo pide por
 * nombre (si se regenera un modelo y se pierde alguno, el juego no avisa: la animacion simplemente no pasa).
 */
class MutangoModelsTest {

    /** Lef, Lihuen y Lican (MutangoModels.MODELS). */
    private static final List<String> MUTANGOS = List.of(
        "bigcasares_boss_mutango", "bigcasares_boss_mutango_2ojos", "bigcasares_boss_mutango_3ojos");
    /** Las animaciones de los Mutangos que piden las fases 1 y 2 y la pelea. */
    private static final List<String> MUTANGO_ANIMATIONS = List.of("idle", "walk", "attack",
        MutangoPhaseOne.RAM_ANIMATION, MutangoPhaseOne.HEADBUTT_ANIMATION, MutangoBattle.DOWNED_ANIMATION,
        "golpe", "saltar", "sostener", "lanzar", "tomar_impulso", "lanzado");
    private static final List<String> NEGUECHEN_ANIMATIONS = List.of("idle", "lanzar", "levantar_manos", "sin_manos",
        "salto");

    private static JsonObject model(String name) throws IOException {
        try (InputStream stream = MutangoModelsTest.class.getClassLoader()
            .getResourceAsStream("bettermodel/models/" + name + ".bbmodel")) {
            assertNotNull(stream, "falta bettermodel/models/" + name + ".bbmodel en el jar (src/main/resources/bettermodel/models)");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Set<String> animations(JsonObject model) {
        Set<String> names = new HashSet<>();
        for (JsonElement animation : model.getAsJsonArray("animations")) {
            names.add(animation.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    private static Set<String> bones(JsonObject model) {
        Set<String> names = new HashSet<>();
        for (JsonElement group : model.getAsJsonArray("groups")) {
            names.add(group.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    private static void assertAnimations(String name, List<String> expected) throws IOException {
        Set<String> have = animations(model(name));
        for (String animation : expected) {
            assertTrue(have.contains(animation), name + " no tiene la animacion " + animation);
        }
    }

    @Test
    void losTresMutangosTienenTodasSusAnimaciones() throws IOException {
        for (String name : MUTANGOS) {
            assertAnimations(name, MUTANGO_ANIMATIONS);
        }
    }

    @Test
    void losMutangosTienenSuHitbox() throws IOException {
        for (String name : MUTANGOS) {
            assertTrue(bones(model(name)).contains("b_cuerpo"), name + " sin la hitbox b_cuerpo");
        }
    }

    @Test
    void lasEstatuasSonLosMismosTresDormidos() throws IOException {
        assertEquals(3, MUTANGOS.size());
        for (String name : MUTANGOS) {
            JsonObject awake = model(name);
            JsonObject asleep = model(name + "_dormido");
            assertEquals(bones(awake), bones(asleep), "la estatua de " + name + " tiene otros huesos");
            assertEquals(awake.getAsJsonArray("elements").size(), asleep.getAsJsonArray("elements").size());
        }
    }

    @Test
    void neguechenTieneSusAnimacionesYSuHitbox() throws IOException {
        assertAnimations(MutangoPhaseThree.NEGUECHEN, NEGUECHEN_ANIMATIONS);
        Set<String> bones = bones(model(MutangoPhaseThree.NEGUECHEN));
        assertTrue(bones.contains("b_cuerpo"));
        // las manos que se esconden con "sin_manos" y las que brillan
        assertTrue(bones.contains("glow_mano_izq") && bones.contains("glow_mano_der"));
    }

    @Test
    void lasManosSueltasYElSolYLaLunaEstan() throws IOException {
        for (String name : List.of(MutangoHandsAttack.HAND_LEFT, MutangoHandsAttack.HAND_RIGHT,
            MutangoPhaseThree.SUN, MutangoPhaseThree.MOON)) {
            assertAnimations(name, List.of("idle"));
        }
        // el Sol brilla solo (etiqueta glow_ de BetterModel)
        assertTrue(bones(model(MutangoPhaseThree.SUN)).stream().anyMatch(bone -> bone.startsWith("glow_")));
    }

    @Test
    void elCuboDelSantuarioEsUnBloque() throws IOException {
        JsonObject cube = model(MutangoShrine.CUBE);
        assertEquals(1, cube.getAsJsonArray("elements").size());
        JsonObject element = cube.getAsJsonArray("elements").get(0).getAsJsonObject();
        for (int i = 0; i < 3; i++) {
            double size = element.getAsJsonArray("to").get(i).getAsDouble()
                - element.getAsJsonArray("from").get(i).getAsDouble();
            assertEquals(16.0, size, 1e-9, "el cubo mide un bloque");
        }
    }

    @Test
    void cadaArmaduraEsElMismoModeloConLasPiezasEncima() throws IOException {
        assertEquals(16, MutangoArmor.armoredModels().size());
        for (String base : MutangoArmor.ARMORED_BASE_MODELS) {
            JsonObject plain = model(base);
            for (MutangoArmor.Tier tier : MutangoArmor.Tier.values()) {
                if (tier == MutangoArmor.Tier.NONE) {
                    continue;
                }
                String name = MutangoArmor.model(base, tier.ordinal());
                JsonObject armored = model(name);
                // mismos huesos y animaciones: la armadura se mueve con lo que cubre
                assertEquals(bones(plain), bones(armored), name);
                assertEquals(animations(plain), animations(armored), name);
                long pieces = armored.getAsJsonArray("elements").asList().stream()
                    .filter(e -> e.getAsJsonObject().get("name").getAsString().startsWith("armadura_")).count();
                assertTrue(pieces >= 3, name + " sin piezas de armadura");
                assertEquals(plain.getAsJsonArray("elements").size() + pieces,
                    armored.getAsJsonArray("elements").size(), name + ": se perdio algo del modelo");
            }
        }
    }

    @Test
    void cadaModeloTraeSuTexturaAdentro() throws IOException {
        for (String name : List.of(MUTANGOS.get(0), MutangoPhaseThree.NEGUECHEN, MutangoPhaseThree.SUN,
            MutangoShrine.CUBE)) {
            String source = model(name).getAsJsonArray("textures").get(0).getAsJsonObject().get("source").getAsString();
            assertTrue(source.startsWith("data:image/png;base64,"), name + " sin textura empaquetada");
        }
    }
}
