package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * El encuentro de Enlil, señor del viento.
 *
 * <p>Fase 1: flota fuera de alcance, tira rafagas que empujan hacia las paredes (agacharse aguanta) y tornados que
 * persiguen; las flechas rebotan en el viento. Cada tanto baja a tierra unos segundos: solo ahi se le pega (cuerpo a
 * cuerpo). Al 50 %: fase 2.</p>
 *
 * <p>Fase 2: se enfurece y la arena se inunda (agua real, dos capas, restaurada al terminar). Tres Tablillas del
 * Destino lo hacen invulnerable; al romperlas (golpes o flechas) cae al agua y queda expuesto; despues se levanta, las
 * tablillas se vuelven a formar y el ciclo se repite hasta matarlo.</p>
 */
public final class EnlilEncounter {

    private EnlilEncounter() {
    }

    /** Claves extra de BetterModel que usa el encuentro (ademas del modelo del jefe); se instalan si vienen en el jar. */
    public static List<String> extraModelKeys() {
        return List.of(EnlilTablets.MODEL);
    }

    /**
     * Saca el agua que dejo una inundacion de Enlil cuya pelea no termino limpia (un crash): solo los bloques de los
     * diarios {@code plugins/BigCasares/mythicdungeons/enlil-flood-*} de jefes que no estan vivos y que siguen
     * siendo agua. Llamarlo al prender el modulo, en el hilo del servidor y con los mundos cargados.
     */
    public static void recoverOrphanFloods(JavaPlugin plugin) {
        EnlilFlood.recoverOrphans(plugin);
    }

    public static BossEncounterControllerFactory factory(MythicBossSupport support) {
        return context -> new EnlilFight(support, context);
    }
}
