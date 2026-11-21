package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * El encuentro de Pachamama corrompida por Mandinga (mazmorra sudamericana).
 *
 * <p>Fase 1: Pachamama sale de la tierra. Por rondas el piso se pinta de a baldosas (un ajedrez o al azar) de un solo
 * color elegido al azar: azul (agua y rayo), amarillo (hambre extrema) o marrón (temblor: daño, náusea y un sacudón).
 * Las baldosas pintadas avisan antes de que pegue el efecto; se le pega a ella en cualquier momento, parado en las
 * baldosas sanas. Al 50 %: fase 2.</p>
 *
 * <p>Fase 2: Mandinga sale de su espalda, enroscado como una víbora, con su guitarra. Ofrece la guitarra a cambio de
 * un corazón (para siempre, uno por jugador) desde un pedestal; el trato es opcional. Cada acorde manda una onda por
 * el piso que hay que saltar, y siguen rondas de piso pintado más livianas. Las notas de la guitarra le pegan fuerte.</p>
 */
public final class PachamamaEncounter {

    /** Id del jefe (el del YAML); fijo aca porque la cultura todavia puede no tener el jefe registrado. */
    public static final String BOSS_ID = "myth-pachamama";
    public static final String SHORT_ID = "pachamama";
    public static final String MODEL_KEY = "bigcasares_myth_pachamama";

    private PachamamaEncounter() {
    }

    /** Claves extra de BetterModel que usa el encuentro (ademas del modelo del jefe). */
    public static List<String> extraModelKeys() {
        return List.of(PachamamaMandinga.MODEL);
    }

    /**
     * Restaura los bloques (piso pintado, agua, pedestal) de una pelea de Pachamama que no termino limpia (un
     * crash): solo los de los diarios {@code plugins/BigCasares/mythicdungeons/pachamama-blocks-*.txt} de jefes que
     * no estan vivos y que siguen siendo lo que puso la pelea. Llamarlo al prender el modulo, en el hilo del servidor
     * y con los mundos cargados. Cada pelea nueva de Pachamama tambien lo hace al empezar.
     */
    public static void recoverOrphans(JavaPlugin plugin) {
        PachamamaFloor.recoverOrphans(plugin);
    }

    public static BossEncounterControllerFactory factory(MythicBossSupport support) {
        return context -> new PachamamaFight(support, context);
    }
}
