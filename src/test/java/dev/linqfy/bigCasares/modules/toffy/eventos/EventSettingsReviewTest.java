package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSettingsReviewTest {

    @Test
    void anEmptyAllowedListFallsBackToTheDefaultWithAWarning() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("allowed-commands: []\n");

        ToffyEventSettings settings = ToffyEventSettings.load(yaml);

        assertEquals(List.of("anotar", "toffy", "tcomp", "evento", "eventos"), settings.allowedCommands());
        assertFalse(settings.warnings().isEmpty());
    }

    @Test
    void configuredCommandsLoseTheSlashAndNamespaceAndAreLowerCase() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("allowed-commands: ['/Bigcasares:Anotar', toffy]\n");

        assertEquals(List.of("anotar", "toffy"), ToffyEventSettings.load(yaml).allowedCommands());
    }

    @Test
    void anInvalidSoundKeyWarnsAndIsSilent() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("sounds:\n  begin:\n    sonido: \"Mal Key!\"\n");

        ToffyEventSettings settings = ToffyEventSettings.load(yaml);

        assertTrue(settings.sound(ToffyEventSettings.SOUND_BEGIN).silent());
        assertFalse(settings.warnings().isEmpty());
    }

    @Test
    void volumeAndPitchAreClampedToTheDocumentedRanges() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("sounds:\n  begin:\n    sonido: minecraft:entity.ender_dragon.growl\n"
            + "    volumen: 99\n    tono: 9\n");

        ToffyEventSettings.SoundSpec sound = ToffyEventSettings.load(yaml).sound(ToffyEventSettings.SOUND_BEGIN);

        assertEquals(10f, sound.volume());
        assertEquals(2f, sound.pitch());
        assertEquals(0.5f, new ToffyEventSettings.SoundSpec("x", 1f, 0.1f).pitch());
    }

    @Test
    void theSoundKeyHelperAcceptsBlankAsSilentAndRejectsGarbage() {
        assertEquals("", ToffyEvents.Text.soundKey("  "));
        assertEquals("minecraft:block.note_block.hat", ToffyEvents.Text.soundKey("minecraft:block.note_block.hat"));
        assertNull(ToffyEvents.Text.soundKey("Mal Key!"));
    }
}
