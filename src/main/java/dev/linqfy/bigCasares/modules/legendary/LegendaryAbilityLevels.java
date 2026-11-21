package dev.linqfy.bigCasares.modules.legendary;

/** One consistent action improves with each player kill; damage remains bounded. */
record LegendaryAbilityLevels(int level) {
    static LegendaryAbilityLevels at(int level) {
        if (level < 1 || level > 5)
            throw new IllegalArgumentException("La habilidad todavía no está desbloqueada.");
        return new LegendaryAbilityLevels(level);
    }

    double bloodRange() {
        return 6 + 2 * level;
    }

    boolean bloodTracker() {
        return level >= 2;
    }

    boolean bloodTrail() {
        return level >= 3;
    }

    boolean bloodStrength() {
        return level >= 4;
    }

    boolean bloodHook() {
        return level >= 5;
    }

    double bloodPull() {
        return .45 + .15 * level;
    }

    int daggers() {
        return (level + 1) / 2;
    }

    double shadowRange() {
        return 6 + 2 * level;
    }

    double shadowDamage() {
        return 1.5 + .5 * level;
    }

    int shadowEffectTicks() {
        return 10 + 10 * level;
    }

    double earthRange() {
        return 2 + 2 * level;
    }

    double earthWidth() {
        return 1.25 + .25 * level;
    }

    double earthHealthDamage() {
        return 4;
    }

    int guardDurationTicks() {
        return level * 20;
    }

    double guardReflectCap() {
        return 1 + .6 * level;
    }

    static long cooldown(LegendaryWeapon weapon, boolean secondary) {
        return switch (weapon) {
            case BLOODLUST -> secondary ? 60_000 : 30_000;
            case SHADOW_BLADE -> 30_000;
            case EARTHBREAKER -> 45_000;
            default -> 40_000;
        };
    }
}
