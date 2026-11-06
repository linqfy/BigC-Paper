package dev.linqfy.bigCasares.modules.tideframe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TideframeStatusTest {

    @Test
    void anEngineThatIsOffSaysSoAndNothingElse() {
        List<String> lines = TideframeStatus.off().lines();
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).contains("Motor apagado"));
    }

    @Test
    void aHealthyEngineReportsPackScreensAndPlayers() {
        List<String> lines = new TideframeStatus(true, false, true, true, false, List.of(), 3, 2, 1, java.util.Map.of()).lines();
        assertTrue(lines.contains("§aPack propio: se envía (pack.send: true)."));
        assertTrue(lines.contains("§7Misiones: §aTideframe §7· Tienda: §eclásico"));
        assertTrue(lines.contains("§aPantallas bigc_* cargadas."));
        assertTrue(lines.contains("§7Jugadores Java: §f3 §7(con el pack: §f2§7) · Bedrock: §f1 §7(formularios)"));
        assertTrue(lines.contains("§7Menú clásico en vez de Tideframe: ninguna vez desde el arranque."));
    }

    @Test
    void fallbacksAreListedInAFixedOrderWithTheirCounts() {
        var fallbacks = java.util.Map.of(TideframeStatus.Fallback.OPEN_FAILED, 1L, TideframeStatus.Fallback.PACK_NOT_LOADED, 3L,
            TideframeStatus.Fallback.MOVING, 0L);
        List<String> lines = new TideframeStatus(true, false, true, true, true, List.of(), 1, 1, 0, fallbacks).lines();
        assertEquals("§7Menú clásico en vez de Tideframe desde el arranque: sin el pack cargado §f3§7 · apertura fallida §f1§7",
            lines.getLast());
    }

    @Test
    void problemsComeFirstAndNameWhatIsWrong() {
        List<String> lines = new TideframeStatus(true, true, false, true, true, List.of("bigc_tienda"), 0, 0, 0, java.util.Map.of()).lines();
        assertTrue(lines.get(1).contains("API interna no coincide"));
        assertTrue(lines.get(2).contains("pack.send: false"));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Pantallas sin cargar: bigc_tienda")));
    }
}
