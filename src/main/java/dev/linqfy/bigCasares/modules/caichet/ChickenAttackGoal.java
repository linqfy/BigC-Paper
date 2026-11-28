package dev.linqfy.bigCasares.modules.caichet;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;

/**
 * Lo que hace una gallina enojada: persigue a quien le pego y le pega como un zombie (mismo dano, cada un segundo).
 * Tiene prioridad 0, asi le gana al "panico" que tienen las gallinas cuando las lastiman.
 */
final class ChickenAttackGoal implements Goal<Chicken> {

    static final GoalKey<Chicken> KEY = GoalKey.of(Chicken.class, new NamespacedKey("caichet", "chicken_attack"));

    /** El dano base del zombie (en Normal; en Facil y Dificil se ajusta solo, como con el zombie; en Pacifico ver tick). */
    static final double ATTACK_DAMAGE = 3.0;
    /** Velocidad del zombie bebe: 0.23 del zombie + 50%. */
    static final double BABY_ZOMBIE_SPEED = 0.345;
    /** Mismo ritmo de golpes que el zombie. */
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    /** Hasta donde llega el picotazo (bloques, centro a centro en horizontal). */
    private static final double REACH = 1.4;
    /** Si el jugador se aleja mas que esto, la gallina se calma. */
    private static final double GIVE_UP_DISTANCE = 40.0;

    private final Chicken chicken;
    private Player target;
    private int cooldown;
    private int repath;

    ChickenAttackGoal(Chicken chicken, Player target) {
        this.chicken = chicken;
        this.target = target;
    }

    void setTarget(Player target) {
        this.target = target;
    }

    private boolean targetValid() {
        return target != null && target.isOnline() && !target.isDead()
                && target.getWorld().equals(chicken.getWorld())
                && target.getGameMode() != GameMode.CREATIVE && target.getGameMode() != GameMode.SPECTATOR
                && target.getLocation().distanceSquared(chicken.getLocation()) < GIVE_UP_DISTANCE * GIVE_UP_DISTANCE;
    }

    @Override
    public boolean shouldActivate() {
        return targetValid();
    }

    @Override
    public boolean shouldStayActive() {
        return targetValid();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        chicken.getPathfinder().stopPathfinding();
        target = null;
    }

    @Override
    public void tick() {
        chicken.lookAt(target);
        if (--repath <= 0) {
            chicken.getPathfinder().moveTo(target, 1.0);
            repath = 10;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        double dx = target.getX() - chicken.getX();
        double dz = target.getZ() - chicken.getZ();
        double dy = target.getY() - chicken.getY();
        if (cooldown == 0 && dx * dx + dz * dz <= REACH * REACH && Math.abs(dy) < 1.6) {
            cooldown = ATTACK_COOLDOWN_TICKS;
            // un doble pega con el daño de su arma y desde su cuerpo; una gallina comun, como un zombie
            Doppelganger doppel = Doppelganger.of(chicken);
            double damage = doppel != null ? doppel.weaponDamage : ATTACK_DAMAGE;
            if (doppel != null) {
                doppel.body.swingMainHand();
            }
            if (chicken.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
                // en Pacifico el juego anula el dano de los mobs (tambien el del zombie): se aplica como dano
                // directo, con un empujoncito, para que igual pegue
                target.damage(damage);
                target.knockback(0.4, -dx, -dz);
            } else {
                // golpe de mob comun: el juego lo ajusta por dificultad igual que al zombie
                target.damage(damage, doppel != null ? doppel.body : chicken);
            }
            if (doppel != null) {
                return;
            }
            chicken.getWorld().playSound(chicken.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1f, 0.6f);
        }
    }

    @Override
    public @NonNull GoalKey<Chicken> getKey() {
        return KEY;
    }

    @Override
    public @NonNull EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
    }
}
