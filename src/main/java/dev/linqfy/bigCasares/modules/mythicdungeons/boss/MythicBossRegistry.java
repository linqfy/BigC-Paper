package dev.linqfy.bigCasares.modules.mythicdungeons.boss;

import dev.linqfy.bigCasares.modules.mythicdungeons.MythicCulture;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.came.CameEncounter;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo.DiabloEncounter;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil.EnlilEncounter;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma.EnmaEncounter;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus.HorusEncounter;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis.IblisEncounter;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin.OdinEncounter;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama.PachamamaEncounter;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus.ZeusEncounter;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Los jefes miticos de las culturas desarrolladas, cada uno con su encuentro en {@code boss/<jefe>/<Jefe>Encounter}. Cada clase de
 * encuentro expone {@code public static BossEncounterControllerFactory factory(MythicBossSupport)} y
 * {@code public static List<String> extraModelKeys()}; su definicion de habilidades y fases es
 * {@code bosses/<culture.requireBoss().id()>.yml} (el {@code id} del YAML tiene que ser ese mismo id).
 */
public final class MythicBossRegistry {

    /** Un jefe: su cultura, como crear el controlador de cada instancia y los modelos extra que usa. */
    public record Entry(MythicCulture culture, Function<MythicBossSupport, BossEncounterControllerFactory> factory,
                        Supplier<List<String>> extraModelKeys) {

        public Entry {
            Objects.requireNonNull(culture, "culture");
            Objects.requireNonNull(factory, "factory");
            Objects.requireNonNull(extraModelKeys, "extraModelKeys");
        }

        public String bossId() {
            return culture.requireBoss().id();
        }
    }

    private static final List<Entry> ENTRIES = List.of(
        new Entry(MythicCulture.GREEK, ZeusEncounter::factory, ZeusEncounter::extraModelKeys),
        new Entry(MythicCulture.NORSE, OdinEncounter::factory, OdinEncounter::extraModelKeys),
        new Entry(MythicCulture.MEDIEVAL, DiabloEncounter::factory, DiabloEncounter::extraModelKeys),
        new Entry(MythicCulture.EGYPTIAN, HorusEncounter::factory, HorusEncounter::extraModelKeys),
        new Entry(MythicCulture.MESOPOTAMIAN, EnlilEncounter::factory, EnlilEncounter::extraModelKeys),
        new Entry(MythicCulture.JAPANESE, EnmaEncounter::factory, EnmaEncounter::extraModelKeys),
        new Entry(MythicCulture.MESOAMERICAN, CameEncounter::factory, CameEncounter::extraModelKeys),
        new Entry(MythicCulture.ARABIAN, IblisEncounter::factory, IblisEncounter::extraModelKeys),
        new Entry(MythicCulture.ANDEAN, PachamamaEncounter::factory, PachamamaEncounter::extraModelKeys)
    );

    private MythicBossRegistry() {
    }

    /** Uno por cultura desarrollada, en el orden de {@link MythicCulture}. */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry of(MythicCulture culture) {
        return ENTRIES.stream().filter(entry -> entry.culture() == culture).findFirst()
            .orElseThrow(() -> new IllegalStateException("no boss registered for " + culture.id()));
    }

    /** Los modelos extra de todos los encuentros (sin repetir). */
    public static List<String> extraModelKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Entry entry : ENTRIES) {
            keys.addAll(entry.extraModelKeys().get());
        }
        return List.copyOf(keys);
    }
}
