package dev.linqfy.bigCasares.modules.mutango;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutangoStructureTest {

    private static MutangoStructure ruinas() throws IOException {
        try (InputStream stream = MutangoStructureTest.class.getClassLoader()
            .getResourceAsStream("mutango/structures/ruinas.json.gz")) {
            assertNotNull(stream, "falta mutango/structures/ruinas.json.gz");
            return MutangoStructure.load(stream);
        }
    }

    private static InputStream gzip(String json) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    @Test
    void lasRuinasMidenLoMismoQueElLitematic() throws IOException {
        MutangoStructure ruinas = ruinas();
        assertEquals(42, ruinas.width());
        assertEquals(26, ruinas.height());
        assertEquals(52, ruinas.length());
        assertEquals(197, ruinas.palette().size());
    }

    @Test
    void hayUnSoloBeaconEnLaPlataforma() throws IOException {
        MutangoStructure ruinas = ruinas();
        int beacons = 0;
        int beaconY = -1;
        for (int y = 0; y < ruinas.height(); y++) {
            for (int z = 0; z < ruinas.length(); z++) {
                for (int x = 0; x < ruinas.width(); x++) {
                    if (ruinas.palette().get(ruinas.stateAt(x, y, z)).equals("minecraft:beacon")) {
                        beacons++;
                        beaconY = y;
                    }
                }
            }
        }
        // el santuario va en el beacon: si cambia el litematic y no hay (o hay varios), se rompe
        assertEquals(1, beacons);
        assertEquals(3, beaconY, "el beacon esta en el piso de la plataforma");
    }

    @Test
    void laCapaDeAbajoEsPastoYFlores() throws IOException {
        MutangoStructure ruinas = ruinas();
        int plantas = 0;
        for (int z = 0; z < ruinas.length(); z++) {
            for (int x = 0; x < ruinas.width(); x++) {
                if (ruinas.isPlant(ruinas.stateAt(x, 0, z))) {
                    plantas++;
                }
            }
        }
        assertTrue(plantas > 500, "abajo de las ruinas va el pasto: hay " + plantas);
    }

    @Test
    void reconoceAireYPlantas() throws IOException {
        MutangoStructure tiny = MutangoStructure.load(gzip("""
            {"size":[4,1,1],"palette":["minecraft:air","minecraft:short_grass",
             "minecraft:tall_grass[half=lower]","minecraft:stone"],"runs":[[0,1],[1,1],[2,1],[3,1]]}"""));
        assertTrue(tiny.isAir(tiny.stateAt(0, 0, 0)));
        assertTrue(tiny.isPlant(tiny.stateAt(1, 0, 0)));
        assertTrue(tiny.isPlant(tiny.stateAt(2, 0, 0)), "con propiedades tambien");
        assertFalse(tiny.isPlant(tiny.stateAt(3, 0, 0)));
        assertFalse(tiny.isAir(tiny.stateAt(3, 0, 0)));
    }

    @Test
    void elOrdenEsYDespuesZDespuesX() throws IOException {
        // 2 de ancho, 2 de alto, 2 de largo: cada bloque con su propio estado
        MutangoStructure cube = MutangoStructure.load(gzip("""
            {"size":[2,2,2],"palette":["a","b","c","d","e","f","g","h"],
             "runs":[[0,1],[1,1],[2,1],[3,1],[4,1],[5,1],[6,1],[7,1]]}"""));
        assertEquals(1, cube.stateAt(1, 0, 0));
        assertEquals(2, cube.stateAt(0, 0, 1));
        assertEquals(4, cube.stateAt(0, 1, 0));
        assertEquals(7, cube.stateAt(1, 1, 1));
    }

    @Test
    void unaEstructuraIncompletaFalla() {
        assertThrows(IOException.class, () -> MutangoStructure.load(gzip("""
            {"size":[2,2,2],"palette":["minecraft:air"],"runs":[[0,5]]}""")));
    }

    @Test
    void unaEstructuraConEstadosDeMasFalla() {
        assertThrows(IOException.class, () -> MutangoStructure.load(gzip("""
            {"size":[1,1,1],"palette":["minecraft:air"],"runs":[[3,1]]}""")));
        assertThrows(IOException.class, () -> MutangoStructure.load(gzip("""
            {"size":[1,1,1],"palette":["minecraft:air"],"runs":[[0,2]]}""")));
    }
}
