package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.NamespacedKey;
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
    /** En cada entidad de una pelea: quienes pelearon (ids separados por coma), para sumarles la victoria. */
    final NamespacedKey fighters;

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
    }
}
