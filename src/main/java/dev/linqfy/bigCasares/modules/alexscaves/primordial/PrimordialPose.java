package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import java.util.Map;
import java.util.Set;

/**
 * Pure choice of the Primordial mobs' model animations, after the mod's entity classes: which base loop plays for the
 * current state and which one-shot action the mod's goals start for each blow or idle fidget. The names are the
 * animations of the generated {@code .bbmodel} files.
 */
final class PrimordialPose {
    /** The mod entities that share an animation set. */
    enum Kind {
        SUBTERRANODON, VALLUMRAPTOR, GROTTOCERATOPS, TRILOCARIS, TREMORSAURUS, RELICHEIRUS, SAUROPOD, TREMORZILLA
    }

    /** Everything the base loop depends on. */
    record State(boolean sitting, boolean dancing, boolean burying, boolean running, boolean relaxed, boolean flying,
                 boolean swimming, boolean firing, boolean onGround, boolean moving) {
    }

    /** Horizontal blocks per tick above which a mob counts as walking (the mapping's threshold). */
    static final double WALK_SPEED = 0.02;
    /** Mod {@code isStillEnough}: horizontal speed under this lets the idle fidgets start. */
    static final double STILL_SPEED = 0.05;

    private static final Set<Kind> SITS = Set.of(Kind.SUBTERRANODON, Kind.VALLUMRAPTOR, Kind.TREMORSAURUS, Kind.TREMORZILLA);
    private static final Set<Kind> BURIES = Set.of(Kind.SUBTERRANODON, Kind.VALLUMRAPTOR, Kind.GROTTOCERATOPS,
        Kind.TREMORSAURUS, Kind.RELICHEIRUS);

    private PrimordialPose() {
    }

    /** The loop that takes the whole model while nothing else plays. Order: sit, dance, bury, then the mob's own states. */
    static String baseLoop(Kind kind, State state) {
        if (state.sitting() && SITS.contains(kind)) {
            return "sit";
        }
        if (state.dancing()) {
            return "dance";
        }
        if (state.burying() && BURIES.contains(kind)) {
            return "bury_eggs";
        }
        switch (kind) {
            case VALLUMRAPTOR -> {
                if (state.relaxed()) {
                    return "relaxed";
                }
                if (state.running()) {
                    return "run";
                }
            }
            case SUBTERRANODON -> {
                if (state.flying()) {
                    return "fly";
                }
            }
            case TRILOCARIS -> {
                if (state.onGround() && !state.swimming()) {
                    return "ground";
                }
            }
            case TREMORZILLA -> {
                if (state.firing()) {
                    return "beam";
                }
                if (state.swimming()) {
                    return "swim";
                }
            }
            default -> {
            }
        }
        return state.moving() ? "walk" : "idle";
    }

    /**
     * Mod {@code SauropodBaseEntity.legBackAmount}: the hind legs swing back while it strides, so the leg_back loop
     * (the walk's own movement plus the legs pulled back) takes the place of the walk for that part of the stride.
     */
    static String sauropodBase(String base, boolean legsBack) {
        return legsBack && base.equals("walk") ? "leg_back" : base;
    }

    /** Mod {@code VallumraptorMeleeGoal}: bite 1 in 2, otherwise slash 2 or slash 1 evenly. */
    static String raptorBlow(boolean first, boolean second) {
        return first ? "melee_bite" : second ? "melee_slash_2" : "melee_slash_1";
    }

    /** Mod {@code GrottoceratopsMeleeGoal}: ram 1 in 2, otherwise a tail swing to the left or right. */
    static String grottoBlow(boolean ram, boolean left) {
        return ram ? "melee_ram" : left ? "melee_tail_1" : "melee_tail_2";
    }

    /**
     * Mod {@code TremorsaurusMeleeGoal}: it shakes small prey (under 2 blocks) half the time and anything flying always,
     * unless it is a baby; otherwise it bites.
     */
    static String tremorsaurusBlow(boolean coin, double preyBigSide, boolean flyingPrey, boolean baby) {
        boolean shake = (!coin && !(preyBigSide >= 2.0F) || flyingPrey) && !baby;
        return shake ? "shake_prey" : "bite";
    }

    /** Mod {@code RelicheirusMeleeGoal}: trilocaris are eaten, anything else is slashed. */
    static String relicheirusBlow(boolean trilocaris, boolean coin) {
        return trilocaris ? "eat_trilocaris" : coin ? "melee_slash_1" : "melee_slash_2";
    }

    /** The fidget a still raptor starts (1 in 100 a tick): scratch 45 %, scratch 45 %, shake 10 %. */
    static String raptorFidget(float roll) {
        return roll < 0.45F ? "scratch_1" : roll < 0.9F ? "scratch_2" : "shake";
    }

    /** The fidget a still relicheirus starts (1 in 200 a tick): scratch 15 %, scratch 15 %, shake 70 %. */
    static String relicheirusFidget(float roll) {
        return roll < 0.15F ? "scratch_1" : roll < 0.3F ? "scratch_2" : "shake";
    }

    /** What an ambient sound does to the mob (the mod's {@code getAmbientSound} starts the speak animation). */
    static String speak(Kind kind, boolean coin, boolean sitting, boolean relaxed) {
        return switch (kind) {
            case VALLUMRAPTOR -> relaxed ? null : coin && !sitting ? "call_2" : "call_1";
            case GROTTOCERATOPS, RELICHEIRUS -> coin ? "speak_2" : "speak_1";
            case TREMORSAURUS, SAUROPOD, TREMORZILLA -> "speak";
            default -> null;
        };
    }

    /** Model animation of a sauropod's action; the tail whips have none (the mod swings the body itself). */
    static String sauropod(SauropodRules.Anim anim) {
        return switch (anim) {
            case SPEAK -> "speak";
            case ROAR -> "roar";
            case EPIC_DEATH -> "epic_death";
            case SUMMON -> "summon";
            case STOMP -> "stomp";
            case SPEW_FLAMES -> "spew_flames";
            case JUMP -> "jump";
            case LEFT_KICK -> "left_kick";
            case RIGHT_KICK -> "right_kick";
            case EAT_LEAVES -> "eat_leaves";
            default -> null;
        };
    }

    /** Model animation of a tremorzilla's action; the tail whips have none. */
    static String tremorzilla(TremorzillaRules.Anim anim) {
        return switch (anim) {
            case SPEAK -> "speak";
            case ROAR_1 -> "roar_1";
            case ROAR_2 -> "roar_2";
            case RIGHT_SCRATCH -> "right_scratch";
            case LEFT_SCRATCH -> "left_scratch";
            case RIGHT_STOMP -> "right_stomp";
            case LEFT_STOMP -> "left_stomp";
            case BITE -> "bite";
            case PREPARE_BREATH -> "prepare_breath";
            case CHEW -> "chew";
            default -> null;
        };
    }

    /** Length in ticks of the action animations (the mod's {@code Animation.create}), 0 when unknown. */
    static int length(Kind kind, String animation) {
        Integer ticks = LENGTHS.get(kind.name() + ":" + animation);
        return ticks == null ? 0 : ticks;
    }

    private static final Map<String, Integer> LENGTHS = Map.ofEntries(
        Map.entry("SUBTERRANODON:attack", 10),
        Map.entry("TRILOCARIS:attack", 10),
        Map.entry("VALLUMRAPTOR:call_1", 15), Map.entry("VALLUMRAPTOR:call_2", 25),
        Map.entry("VALLUMRAPTOR:melee_bite", 15), Map.entry("VALLUMRAPTOR:melee_slash_1", 15),
        Map.entry("VALLUMRAPTOR:melee_slash_2", 15), Map.entry("VALLUMRAPTOR:scratch_1", 20),
        Map.entry("VALLUMRAPTOR:scratch_2", 20), Map.entry("VALLUMRAPTOR:shake", 40),
        Map.entry("VALLUMRAPTOR:startleap", 20), Map.entry("VALLUMRAPTOR:grab", 40),
        Map.entry("RELICHEIRUS:eat_tree", 40), Map.entry("RELICHEIRUS:push_tree", 60),
        Map.entry("GROTTOCERATOPS:chew_from_ground", 60),
        Map.entry("GROTTOCERATOPS:speak_1", 15), Map.entry("GROTTOCERATOPS:speak_2", 20),
        Map.entry("GROTTOCERATOPS:chew", 40), Map.entry("GROTTOCERATOPS:melee_ram", 20),
        Map.entry("GROTTOCERATOPS:melee_tail_1", 20), Map.entry("GROTTOCERATOPS:melee_tail_2", 20),
        Map.entry("TREMORSAURUS:bite", 15), Map.entry("TREMORSAURUS:roar", 55), Map.entry("TREMORSAURUS:shake_prey", 40),
        Map.entry("TREMORSAURUS:sniff", 30), Map.entry("TREMORSAURUS:speak", 15),
        Map.entry("RELICHEIRUS:eat_trilocaris", 50), Map.entry("RELICHEIRUS:melee_slash_1", 20),
        Map.entry("RELICHEIRUS:melee_slash_2", 20), Map.entry("RELICHEIRUS:scratch_1", 60),
        Map.entry("RELICHEIRUS:scratch_2", 40), Map.entry("RELICHEIRUS:shake", 30),
        Map.entry("RELICHEIRUS:speak_1", 13), Map.entry("RELICHEIRUS:speak_2", 20),
        Map.entry("SAUROPOD:speak", 15));
}
