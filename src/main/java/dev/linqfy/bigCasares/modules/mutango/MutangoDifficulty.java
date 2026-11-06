package dev.linqfy.bigCasares.modules.mutango;

/**
 * Que tan fuerte sale la pelea (sin Bukkit), como DonPollosBossDifficulty en BigC. level = cuantas veces ya le gano a
 * Neguechen el jugador que la empezo (el que corrio el cubo del santuario o uso /mutango). Cada victoria suma un 50%:
 * tienen mas vida, pegan mas, atacan mas seguido, Neguechen invoca mas animales y los animales son mas rapidos, tienen
 * mas vida y pegan mas. (Don Pollos no le sube la vida al jefe; aca si.)
 * <p>
 * Cada pelea (cada invocacion) tiene la suya, que no depende de ninguna otra; el nivel tambien queda guardado en cada
 * entidad, asi no cambia si se reinicia el servidor.
 */
record MutangoDifficulty(int level) {

    /**
     * Cuanto mas fuerte sale despues de la primera victoria (+50%). Cada vez que se duplican las victorias mas uno
     * (1, 3, 7, 15, 31...) suma otro tanto: el aumento es logaritmico, sube rapido las primeras veces y despues muy
     * despacio (de la pelea 8 en adelante ya casi no cambia).
     */
    static final double STEP = 0.5;
    /** La velocidad no pasa del doble: mas rapido los animales ya no pueden caminar bien. */
    static final double MAX_SPEED = 2.0;
    /** La vida mas alta que deja tener Minecraft. */
    static final double MAX_HEALTH = 1024.0;
    /** Un ataque nunca se repite mas seguido que una vez por segundo. */
    static final int MIN_COOLDOWN_TICKS = 20;

    MutangoDifficulty {
        level = Math.max(0, level);
    }

    /** 1 + STEP * log2(1 + victorias): 1, 1.5, 1.79, 2.0, 2.16, 2.29, 2.40, 2.5 (7 victorias), 2.58, 3.0 (15)... */
    double factor() {
        return 1 + STEP * Math.log(1 + level) / Math.log(2);
    }

    double damage(double base) {
        return base * factor();
    }

    /** Espera entre ataques (en ticks): se divide por el factor, sin bajar de un segundo (ni subir si ya era menos). */
    int cooldownTicks(int baseTicks) {
        int scaled = (int) Math.round(baseTicks / factor());
        return Math.max(Math.min(MIN_COOLDOWN_TICKS, baseTicks), scaled);
    }

    /** Vida maxima: sube igual que todo lo demas (hasta lo que deja Minecraft). */
    double health(double base) {
        return Math.min(MAX_HEALTH, base * factor());
    }

    int count(int base) {
        return (int) Math.ceil(base * factor());
    }

    double speed(double base) {
        return base * Math.min(MAX_SPEED, factor());
    }

    /** Lo que se le dice a quien la despierta. */
    static String summonMessage(int wins) {
        if (wins <= 0) {
            return "§7Todavia no le ganaste nunca a Neguechen.";
        }
        return "§4Le ganaste §f" + wins + "§4 " + (wins == 1 ? "vez" : "veces") + " a Neguechen: §cesta vez van a ser mas "
            + "fuertes" + armorNote(wins) + ".";
    }

    private static String armorNote(int wins) {
        MutangoArmor.Tier tier = MutangoArmor.tier(wins);
        return tier == MutangoArmor.Tier.NONE ? "" : " §7(armadura de " + tier.suffix + ")";
    }
}
