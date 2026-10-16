package dev.linqfy.bigCasares.modules.mutango;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Una pelea: una invocacion de Lef, Lihuen y Lican (y despues de Neguechen). Cada pelea tiene lo suyo y no depende de
 * ninguna otra: su dificultad (segun las victorias de quien la desperto), sus Mutangos, quienes pelearon, la fase en la
 * que esta, su fase 3 y sus versos en pantalla. Puede haber varias a la vez (una por santuario, o por /mutango).
 * Cada entidad de la pelea guarda el id de su pelea, asi se vuelve a armar sola si se reinicia el servidor.
 */
final class MutangoFight {

    final UUID id;
    final MutangoDifficulty difficulty;
    /** Quienes estan peleando (el que la empezo y los que le pegaron a alguno): a ellos se les suma la victoria. */
    final Set<UUID> fighters = new HashSet<>();
    /** Lef, Lihuen y Lican de esta pelea (en el orden en que aparecieron). */
    final Set<UUID> members = new LinkedHashSet<>();
    final MutangoCinematic cinematic;
    final MutangoBattle battle;
    final MutangoPhaseThree phaseThree;
    /** El santuario esta despertando a los Mutangos de esta pelea: todavia no aparecieron pero la pelea ya empezo. */
    boolean awakening;
    /** Cayeron los tres Mutangos de la fase 2 y Neguechen todavia no aparecio (aparece 2 segundos despues). */
    boolean starting;
    /** Neguechen fue vencida pero los premios todavia no se entregaron (se entregan al cerrarse su grieta o si sacan la pelea). */
    boolean rewardsPending;

    MutangoFight(UUID id, int level, BiConsumer<UUID, String> playOnce, MutangoAnimations animations,
                 Function<MutangoFight, MutangoPhaseThree.Host> hostFactory, MutangoBattle.Store store,
                 MutangoCinematic.Display display) {
        this.id = id;
        this.cinematic = new MutangoCinematic(display);
        this.difficulty = new MutangoDifficulty(level);
        this.battle = new MutangoBattle(new MutangoPhaseOne(playOnce, difficulty),
            new MutangoPhaseTwo(animations, difficulty), animations, cinematic, store);
        this.phaseThree = new MutangoPhaseThree(hostFactory.apply(this), difficulty);
    }

    /** Ya no queda nada de esta pelea: ni Mutangos, ni Neguechen (o su grieta), ni versos, ni despertar en curso. */
    boolean over() {
        return members.isEmpty() && !awakening && !starting && !phaseThree.busy() && cinematic.idle();
    }
}
