package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

/**
 * La cuenta de los Ojos de Horus de la fase 2, sin Bukkit: cuantos salieron, cuantos mataron y cuanto de la vida
 * que le queda a Horus se le acredita a quien mata cada uno (asi la barra baja con los Ojos y el ranking los cuenta).
 * Siempre queda {@link #RESERVED_HEALTH} de vida: la ultima la saca el polvo final con {@code defeat()}.
 */
public final class HorusEyeSwarm {

    /** Cuantos Ojos salen (el diseno pide "unos 20"). */
    public static final int DEFAULT_TOTAL = 20;
    public static final int WAVE_SIZE = 4;
    public static final double RESERVED_HEALTH = 1.0;

    private final int total;
    private int alive;
    private int killed;

    public HorusEyeSwarm(int total) {
        if (total < 1) {
            throw new IllegalArgumentException("total must be positive");
        }
        this.total = total;
    }

    public int total() {
        return total;
    }

    public int alive() {
        return alive;
    }

    public int killed() {
        return killed;
    }

    /** Los que faltan matar (vivos o por salir). */
    public int remaining() {
        return total - killed;
    }

    /** Cuantos deberian salir en la proxima oleada (sin pasarse del total). */
    public int nextWave() {
        return Math.min(WAVE_SIZE, total - killed - alive);
    }

    public boolean allSpawned() {
        return alive + killed >= total;
    }

    public boolean cleared() {
        return killed >= total;
    }

    /** Salio un Ojo. */
    public void spawned() {
        if (allSpawned()) {
            throw new IllegalStateException("every eye is already out");
        }
        alive++;
    }

    /** Un Ojo desaparecio sin morir (descarga, limpieza): vuelve a la cola para salir de nuevo. */
    public void lost() {
        if (alive > 0) {
            alive--;
        }
    }

    /**
     * Mataron un Ojo.
     *
     * @param bossHealth vida actual de Horus
     * @return el dano a acreditarle a quien lo mato (la parte de la vida que le toca a este Ojo)
     */
    public double killedOne(double bossHealth) {
        if (alive < 1) {
            throw new IllegalStateException("no eye is alive");
        }
        int before = remaining();
        alive--;
        killed++;
        double spendable = Math.max(0.0, bossHealth - RESERVED_HEALTH);
        return spendable / before;
    }
}
