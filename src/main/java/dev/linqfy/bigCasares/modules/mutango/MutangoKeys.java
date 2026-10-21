package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Las marcas que el plugin guarda en las entidades y en los jugadores (todas con el espacio de nombres del plugin). */
final class MutangoKeys {

    /** En cada entidad del plugin: que modelo lleva (Lef, Neguechen, una estatua...). */
    final NamespacedKey model;
    /** En los bloques que revolean las ondas de las manos (el plugin no los deja apoyarse). */
    final NamespacedKey debris;
    /** En los animales que invoca Neguechen. */
    final NamespacedKey summon;
    /** En el jugador: cuantas veces le gano a Neguechen. */
    final NamespacedKey wins;
    /** En cada entidad de la pelea: con que nivel salio. */
    final NamespacedKey level;
    /** En cada entidad de una pelea: el id de su pelea. */
    final NamespacedKey fight;
    /** En cada Mutango: la fase de la pelea (1 o 2). En Neguechen: 3 mientras esta peleando. */
    final NamespacedKey phase;
    /** En cada Mutango: esta caido (fase 1). */
    final NamespacedKey downed;
    /** En cada entidad que crea el modulo: la marca de "entidad de un sistema" que reconoce BigC (mob-scaling, Jeremy). */
    final NamespacedKey systemMob;
    /** En el jugador: los ids de las adjudicaciones que ya se le aplicaron (los recibos). */
    final NamespacedKey receipts;
    /** En cada Mutango: los ids de los tres de su pelea (para saber quienes faltan cargar tras un reinicio). */
    final NamespacedKey roster;
    /** En cada entidad de una pelea: quienes pelearon (ids separados por coma), para sumarles la victoria. */
    final NamespacedKey fighters;

    /** Marca la entidad como propia del modulo (hay que llamarlo dentro del consumer de spawn). */
    void markSystem(PersistentDataHolder entity) {
        entity.getPersistentDataContainer().set(systemMob, PersistentDataType.STRING,
            MutangoModule.SYSTEM_MOB_VALUE);
    }

    MutangoKeys(Plugin plugin) {
        model = new NamespacedKey(plugin, "modelo");
        debris = new NamespacedKey(plugin, "escombro");
        summon = new NamespacedKey(plugin, "invocado");
        wins = new NamespacedKey(plugin, "neguechen_ganadas");
        level = new NamespacedKey(plugin, "nivel");
        fight = new NamespacedKey(plugin, "pelea");
        phase = new NamespacedKey(plugin, "fase");
        downed = new NamespacedKey(plugin, "caido");
        fighters = new NamespacedKey(plugin, "peleadores");
        systemMob = new NamespacedKey(plugin, MutangoModule.SYSTEM_MOB_KEY);
        roster = new NamespacedKey(plugin, "equipo");
        receipts = new NamespacedKey(plugin, "premios_recibidos");
    }
}
