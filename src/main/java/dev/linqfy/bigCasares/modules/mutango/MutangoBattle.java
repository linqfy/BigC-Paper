package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * La pelea contra Lef, Lihuen y Lican: en que fase estan y cuando pasan a la siguiente.
 * <p>
 * En la fase 1 (cabras) no se mueren: cuando a uno se le acaba la vida queda <b>caido</b> (animacion "derrotado",
 * barra en cero, no recibe dano). Cuando caen los tres se levantan con la vida llena y arranca la fase 2 (pinas y
 * apilarse). En la fase 2 si se mueren de verdad.
 */
final class MutangoBattle {

    static final String DOWNED_ANIMATION = "derrotado";
    /** Los avisos de la pelea en el chat los ven solo los que estan a menos de esta distancia. */
    static final double MESSAGE_RADIUS = 64.0;

    private final MutangoPhaseOne phaseOne;
    private final MutangoPhaseTwo phaseTwo;
    private final MutangoAnimations animations;
    /** Donde se guarda (en cada Mutango) la fase y si esta caido, para retomar la pelea si se reinicia el servidor. */
    private final Store store;
    /** Los versos y carteles de esta pelea. */
    private final MutangoCinematic cinematic;
    private final Set<UUID> downed = new HashSet<>();
    private int phase = 1;
    /** Los tres de la pelea, tambien los que no estan cargados: la fase 1 no se cierra con uno que falta. */
    private final MutangoRoster roster;

    /** Guarda el estado de la pelea en cada Mutango (lo implementa el plugin con la ficha de la entidad). */
    interface Store {
        void phase(Mob mutango, int phase);

        void downed(Mob mutango, boolean downed);
    }

    MutangoBattle(MutangoPhaseOne phaseOne, MutangoPhaseTwo phaseTwo, MutangoAnimations animations,
                  MutangoCinematic cinematic, Store store, MutangoRoster roster) {
        this.roster = roster;
        this.store = store;
        this.cinematic = cinematic;
        this.phaseOne = phaseOne;
        this.phaseTwo = phaseTwo;
        this.animations = animations;
    }

    int phase() {
        return phase;
    }

    void tick(List<Mob> alive) {
        if (alive.isEmpty()) {
            return;
        }
        if (phase == 1) {
            for (Mob mutango : alive) {
                if (!downed.contains(mutango.getUniqueId())) {
                    phaseOne.tick(mutango);
                }
            }
            if (roster.allDowned(downed)) {
                cinematic.endOfPhaseOne(alive.get(0).getLocation());
                setPhase(2, alive);
            }
        } else {
            phaseTwo.tick(alive);
        }
    }

    /**
     * Le llega un golpe que lo mataria. En la fase 1 no se muere: queda caido hasta que caen los tres.
     * Devuelve true si el golpe se cancela.
     */
    boolean onLethal(Mob mutango) {
        if (phase != 1) {
            return false;
        }
        UUID id = mutango.getUniqueId();
        if (downed.add(id)) {
            store.downed(mutango, true);
            phaseOne.forget(id);
            mutango.getPathfinder().stopPathfinding();
            mutango.setAware(false);
            mutango.setHealth(1.0);
            animations.loop(id, DOWNED_ANIMATION);
            mutango.getWorld().playSound(mutango.getLocation(), Sound.BLOCK_STONE_BREAK, 2f, 0.5f);
            mutango.getWorld().playSound(mutango.getLocation(), Sound.ENTITY_GOAT_SCREAMING_HURT, 1.2f, 0.5f);
            String name = mutango.getCustomName() == null ? "Mutango" : mutango.getCustomName();
            // solo a los que estan cerca de la pelea (en un servidor publico no se avisa a todo el mundo)
            for (Player player : MutangoTargets.near(mutango.getLocation(), MESSAGE_RADIUS)) {
                player.sendMessage("§6" + name + " §7se cayo... §8(" + downed.size() + " de "
                    + MutangoModels.MODELS.size() + ")");
            }
        }
        return true;
    }

    /**
     * Retoma la fase guardada (despues de un reinicio o de que se descargue y vuelva el chunk): solo la cambia, sin
     * curarlos ni avisar en pantalla. Nunca vuelve para atras.
     */
    void restorePhase(int saved) {
        if (saved > phase) {
            phaseTwo.reset(List.of());
            phase = saved;
        }
    }

    /** Retoma a un Mutango que estaba caido en la fase 1: sigue caido, con la vida que le quedaba. */
    void restoreDowned(Mob mutango) {
        if (phase != 1) {
            return;
        }
        UUID id = mutango.getUniqueId();
        if (downed.add(id)) {
            phaseOne.forget(id);
            mutango.getPathfinder().stopPathfinding();
            mutango.setAware(false);
            animations.loop(id, DOWNED_ANIMATION);
        }
    }

    boolean isDowned(UUID id) {
        return phase == 1 && downed.contains(id);
    }

    /** Cambia de fase dejando a todos limpios y levantados con la vida llena, y lo anuncia. */
    void setPhase(int next, List<Mob> alive) {
        for (Mob mutango : alive) {
            UUID id = mutango.getUniqueId();
            store.phase(mutango, next);
            store.downed(mutango, false);
            phaseOne.forget(id);
            animations.stop(id, DOWNED_ANIMATION);
            var maxHealth = mutango.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                mutango.setHealth(maxHealth.getValue());
            }
        }
        downed.clear();
        phaseTwo.reset(alive);
        phase = next;
        if (alive.isEmpty()) {
            return;
        }
        String subtitle = next == 1 ? "§7Embisten como cabras" : "§7Se levantan... a las pinas y apilados";
        announce(alive.get(0).getLocation(), "§c§lFASE " + next, subtitle, Sound.ENTITY_GOAT_SCREAMING_AMBIENT);
    }

    /** Titulo en pantalla para los jugadores cerca de where. */
    void announce(Location where, String text, String subtitle, Sound sound) {
        // va por la fila de la pantalla: si hay versos del poema pasando, sale despues
        cinematic.banner(where, text, subtitle, sound);
    }

    boolean isProtected(UUID id) {
        return isDowned(id) || phase == 2 && phaseTwo.isProtected(id);
    }

    void onDamaged(UUID id, double damage) {
        if (phase == 2) {
            phaseTwo.onDamaged(id, damage);
        }
    }

    void forget(UUID id) {
        phaseOne.forget(id);
        downed.remove(id);
    }
}
