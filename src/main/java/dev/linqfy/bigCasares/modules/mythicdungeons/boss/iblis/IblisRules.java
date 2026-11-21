package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

/**
 * The phases of Iblis and what a hit does in each, without server types.
 *
 * <p>{@link Stage#SMOKE} (phase 1): he is invisible smoke; melee hits on the smoke land, projectiles go through the
 * smoke and fire does nothing (he is made of it). The damage of the hit that crosses {@link #PHASE_TWO_AT} is cut to
 * the threshold, so the phase change is never skipped. {@link Stage#SOLOMON}: King Solomon appears and hands out his
 * ring; nobody can hurt Iblis. {@link Stage#VESSELS} (phase 2): he only takes damage while trapped in a vessel.</p>
 */
public final class IblisRules {

    /** Health fraction where Solomon appears. */
    public static final double PHASE_TWO_AT = 0.5;
    /** Ticks of the Solomon scene (rings handed out, vessels rise) before phase 2 starts. */
    public static final int SOLOMON_TICKS = 100;
    /** Damage multiplier on a trapped Iblis. */
    public static final double TRAPPED_MULTIPLIER = 1.25;

    public enum Stage { SMOKE, SOLOMON, VESSELS }

    /** {@code OTHER} is any other hit by a player; {@code ENVIRONMENT} has no player behind it. */
    public enum HitKind { MELEE, PROJECTILE, FIRE, OTHER, ENVIRONMENT }

    public enum Verdict {
        ADMITTED,
        /** Phase 1 projectile: it goes through the smoke. */
        THROUGH_SMOKE,
        /** Fire on a body of smokeless fire. */
        FIRE_IMMUNE,
        /** Damage with no player behind it (suffocation, a fall, an explosion). */
        ENVIRONMENT,
        /** The Solomon scene. */
        SCENE,
        /** Phase 2 and not trapped. */
        UNTOUCHABLE
    }

    public record Decision(Verdict verdict, double damage) {
        static Decision of(Verdict verdict) {
            return new Decision(verdict, 0.0);
        }
    }

    private Stage stage = Stage.SMOKE;
    private int stageSince;

    public Stage stage() {
        return stage;
    }

    /** 1 while he is smoke, 2 from Solomon on. */
    public int phase() {
        return stage == Stage.SMOKE ? 1 : 2;
    }

    /** Advances the stage; returns the new stage when it changed this tick, else {@code null}. */
    public Stage update(int tick, double healthFraction) {
        // the capped hit lands exactly on the threshold; the epsilon absorbs the floating point rest
        if (stage == Stage.SMOKE && healthFraction <= PHASE_TWO_AT + 1.0e-6) {
            return enter(Stage.SOLOMON, tick);
        }
        if (stage == Stage.SOLOMON && tick - stageSince >= SOLOMON_TICKS) {
            return enter(Stage.VESSELS, tick);
        }
        return null;
    }

    /** Ticks since the current stage started. */
    public int inStage(int tick) {
        return tick - stageSince;
    }

    private Stage enter(Stage next, int tick) {
        stage = next;
        stageSince = tick;
        return next;
    }

    public Decision admit(HitKind kind, double damage, double health, double maximumHealth, boolean trapped) {
        if (!(damage > 0.0) || !Double.isFinite(damage)) {
            return new Decision(Verdict.ADMITTED, 0.0);
        }
        if (kind == HitKind.FIRE) {
            return Decision.of(Verdict.FIRE_IMMUNE);
        }
        if (kind == HitKind.ENVIRONMENT) {
            return Decision.of(Verdict.ENVIRONMENT);
        }
        return switch (stage) {
            case SMOKE -> {
                if (kind == HitKind.PROJECTILE) {
                    yield Decision.of(Verdict.THROUGH_SMOKE);
                }
                double floor = maximumHealth * PHASE_TWO_AT;
                double room = Math.max(0.0, health - floor);
                yield new Decision(Verdict.ADMITTED, Math.min(damage, room));
            }
            case SOLOMON -> Decision.of(Verdict.SCENE);
            case VESSELS -> trapped
                ? new Decision(Verdict.ADMITTED, damage * TRAPPED_MULTIPLIER)
                : Decision.of(Verdict.UNTOUCHABLE);
        };
    }
}
