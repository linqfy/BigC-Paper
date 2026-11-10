package dev.linqfy.bigCasares.modules.staffevents;

import dev.linqfy.bigCasares.dimension.datapack.DatapackCompiler;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventArenaDimensionTest {

    @Test
    void compilesToAVoidDimension() {
        GeneratedDatapack pack = new DatapackCompiler().compile(List.of(EventArenaDimension.create())).datapack();
        assertNotNull(pack.file("data/bigcasares/dimension/eventos.json"));
        assertNotNull(pack.file("data/bigcasares/dimension_type/eventos.json"));
        String noise = pack.text("data/bigcasares/worldgen/noise_settings/eventos.json");
        assertTrue(noise.contains("minecraft:air"), noise);
    }
}
