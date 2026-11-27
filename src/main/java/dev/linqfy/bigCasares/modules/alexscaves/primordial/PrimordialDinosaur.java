package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobBehaviour;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialPose.Kind;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Bee;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Model animation of the plain Primordial mobs (subterranodon, vallumraptor, grottoceratops, trilocaris, tremorsaurus,
 * relicheirus): their movement is the vanilla base's, this only chooses what the mod's animations show. The base loop
 * follows the state (sit, dance, bury eggs, fly, run...), the blows play the mod's melee animation of the goal that
 * would have started them, and the idle fidgets and speaking come with the mod's own chances.
 */
class PrimordialDinosaur implements ModMobBehaviour {
    private static final double RUN_DISTANCE = 5.0;

    private final Kind kind;
    private final PrimordialAnimator animator = new PrimordialAnimator();
    private ModMobContext context;
    private NamespacedKey sittingKey;
    private NamespacedKey babyKey;
    /** Ticks the current action keeps the mob "busy" (the mod runs one Animation at a time). */
    private int busy;
    private int relaxed;
    private int burying;
    private int roarCooldown;
    private boolean speakPending;
    private String requested;
    private boolean wasDancing;

    private final DinosaurChores chores;
    private int pushing;

    PrimordialDinosaur(Kind kind) {
        this(kind, null);
    }

    PrimordialDinosaur(Kind kind, PrimordialBlocks blocks) {
        this.kind = kind;
        this.chores = kind == Kind.VALLUMRAPTOR || kind == Kind.GROTTOCERATOPS || kind == Kind.RELICHEIRUS
            ? new DinosaurChores(kind, blocks, this) : null;
    }

    @Override
    public void tick(LivingEntity entity, ModMobContext context, int ticks) {
        this.context = context;
        if (sittingKey == null) {
            sittingKey = new NamespacedKey(context.plugin(), TamingRules.SITTING_KEY);
            babyKey = new NamespacedKey(context.plugin(), TamingRules.BABY_UNTIL_KEY);
            // First tick after loading: a dinosaur saved mid-dance (e.g. after a crash) gets its AI back.
            if (entity instanceof Mob loaded && !loaded.isAware() && !sitting(entity) && entity.getPassengers().isEmpty()) {
                loaded.setAware(true);
            }
        }
        busy = Math.max(0, busy - ticks);
        relaxed = Math.max(0, relaxed - ticks);
        burying = Math.max(0, burying - ticks);
        roarCooldown = Math.max(0, roarCooldown - ticks);
        pushing = Math.max(0, pushing - ticks);
        animator.track(entity, ticks);
        boolean sitting = sitting(entity);
        boolean dancing = animator.dancing(entity, ticks);
        if (dancing != wasDancing) {
            danceChanged(entity, dancing, sitting);
        }

        Mob mob = entity instanceof Mob living ? living : null;
        LivingEntity target = mob == null ? null : mob.getTarget();
        boolean running = kind == Kind.VALLUMRAPTOR && animator.moving() && runs(entity, target);
        boolean onGround = kind == Kind.TRILOCARIS ? !entity.isInWater() : entity.isOnGround();
        PrimordialPose.State state = new PrimordialPose.State(sitting, dancing, burying > 0, running, relaxed > 0,
            kind == Kind.SUBTERRANODON && !entity.isOnGround(), entity.isInWater(), false, onGround, animator.moving());
        boolean leaping = chores != null && chores.leaping();
        animator.base(context, entity, leaping ? "leap" : PrimordialPose.baseLoop(kind, state));

        if (requested != null && busy == 0) {
            play(entity, requested);
        }
        requested = null;
        if (speakPending && busy == 0) {
            String speak = PrimordialPose.speak(kind, random().nextBoolean(), sitting, relaxed > 0);
            if (speak != null) {
                play(entity, speak);
            }
        }
        speakPending = false;
        fidgets(entity, target, sitting, dancing);
        if (chores != null) {
            chores.tick(entity, context, ticks, sitting, dancing);
        }
    }

    @Override
    public void onAttack(LivingEntity entity, ModMobContext context, LivingEntity target) {
        this.context = context;
        if (busy > 0) {
            return;
        }
        String blow = switch (kind) {
            case SUBTERRANODON, TRILOCARIS -> "attack";
            case VALLUMRAPTOR -> PrimordialPose.raptorBlow(random().nextBoolean(), random().nextBoolean());
            case GROTTOCERATOPS -> PrimordialPose.grottoBlow(random().nextBoolean(), random().nextBoolean());
            case TREMORSAURUS -> PrimordialPose.tremorsaurusBlow(random().nextBoolean(),
                Math.max(target.getHeight(), target.getWidth()), flying(target), isBaby(entity));
            case RELICHEIRUS -> PrimordialPose.relicheirusBlow(
                context.mobs().typeOf(target).filter(type -> type == PrimordialMobs.TRILOCARIS).isPresent(),
                random().nextBoolean());
            default -> null;
        };
        if (blow != null) {
            play(entity, blow);
        }
    }

    @Override
    public String soundSuffix(LivingEntity entity, String suffix) {
        if (suffix.equals("idle")) {
            // Mod getAmbientSound: speaking starts the speak/call animation.
            speakPending = true;
        }
        return suffix;
    }

    @Override
    public void close(LivingEntity entity, ModMobContext context) {
        animator.close();
        if (chores != null) {
            chores.cancel(entity);
        }
        // A dinosaur unloaded or closed mid-dance must not stay without its AI.
        if (wasDancing && entity instanceof Mob mob && !sitting(entity) && entity.getPassengers().isEmpty()) {
            mob.setAware(true);
        }
    }

    // --- requests from the taming and breeding rules ---

    /** Mod {@code VallumraptorEntity.setRelaxedForTime}: a nugget relaxes it (the relaxed loop). */
    void relax(int ticks) {
        relaxed = ticks;
    }

    /** Mod {@code AnimalLayEggGoal}: the mother buries her eggs. */
    void buryEggs(int ticks) {
        burying = ticks;
    }

    /** Mod {@code onFeedMixture}: primordial soup makes a relicheirus push trees over for a while. */
    void pushTrees(int ticks) {
        pushing = ticks;
    }

    // --- what the chores need ---

    boolean free() {
        return busy == 0;
    }

    boolean act(LivingEntity entity, String animation) {
        if (context != null && animator.action(context, entity, animation)) {
            busy = PrimordialPose.length(kind, animation);
            return true;
        }
        return false;
    }

    boolean moving() {
        return animator.moving();
    }

    boolean dancing() {
        return wasDancing;
    }

    int pushing() {
        return pushing;
    }

    boolean baby(LivingEntity entity) {
        return isBaby(entity);
    }

    String moduleItem(org.bukkit.inventory.ItemStack stack) {
        return context == null ? null : context.mobs().itemId(stack).orElse(null);
    }

    /** An action to start on the next tick (the grottoceratops chews when fed). */
    void request(String animation) {
        requested = animation;
    }

    /** The behaviour of a tracked Primordial dinosaur, when it is one. */
    static PrimordialDinosaur of(dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService mobs,
                                 org.bukkit.entity.Entity entity) {
        return mobs.behaviourOf(entity).filter(PrimordialDinosaur.class::isInstance)
            .map(PrimordialDinosaur.class::cast).orElse(null);
    }

    // --- internals ---

    private void fidgets(LivingEntity entity, LivingEntity target, boolean sitting, boolean dancing) {
        if (busy > 0 || dancing || context == null) {
            return;
        }
        ThreadLocalRandom random = random();
        switch (kind) {
            case VALLUMRAPTOR -> {
                // Mod: 1 in 100 a tick while still; behaviours tick every 2 ticks.
                if (animator.still() && relaxed == 0 && random.nextInt(50) == 0) {
                    play(entity, PrimordialPose.raptorFidget(random.nextFloat()));
                }
            }
            case RELICHEIRUS -> {
                if (animator.still() && random.nextInt(100) == 0) {
                    play(entity, PrimordialPose.relicheirusFidget(random.nextFloat()));
                }
            }
            case TREMORSAURUS -> {
                if (target != null) {
                    tryRoar(entity);
                } else if (animator.still() && !sitting) {
                    if (random.nextInt(90) == 0) {
                        play(entity, "sniff");
                    } else if (random.nextInt(300) == 0 && entity.getPassengers().isEmpty()) {
                        tryRoar(entity);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Mod {@code TremorsaurusEntity.tryRoar}: a roar every 10 to 20 seconds at most. */
    private void tryRoar(LivingEntity entity) {
        if (roarCooldown == 0 && busy == 0) {
            roarCooldown = 200 + random().nextInt(200);
            play(entity, "roar");
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:tremorsaurus_roar", 2.0F, 1.0F);
        }
    }

    private void play(LivingEntity entity, String animation) {
        if (animator.action(context, entity, animation)) {
            busy = PrimordialPose.length(kind, animation);
        }
    }

    /** Mod: running while it chases from far away or follows its owner from more than 5 blocks away. */
    private boolean runs(LivingEntity entity, LivingEntity target) {
        if (target != null && target.getWorld().equals(entity.getWorld())) {
            return entity.getLocation().distanceSquared(target.getLocation()) > RUN_DISTANCE * RUN_DISTANCE;
        }
        String owner = entity.getPersistentDataContainer().get(new NamespacedKey(context.plugin(), TamingRules.OWNER_KEY),
            PersistentDataType.STRING);
        if (owner == null || sitting(entity)) {
            return false;
        }
        try {
            Player player = Bukkit.getPlayer(UUID.fromString(owner));
            return player != null && player.getWorld().equals(entity.getWorld())
                && player.getLocation().distanceSquared(entity.getLocation()) > RUN_DISTANCE * RUN_DISTANCE;
        } catch (IllegalArgumentException broken) {
            return false;
        }
    }

    private void danceChanged(LivingEntity entity, boolean dancing, boolean sitting) {
        wasDancing = dancing;
        if (entity instanceof Mob mob) {
            // Mod travel(): a dancing dinosaur stands still.
            if (dancing) {
                mob.setTarget(null);
                mob.getPathfinder().stopPathfinding();
                mob.setAware(false);
            } else if (!sitting && entity.getPassengers().isEmpty()) {
                mob.setAware(true);
            }
        }
    }

    boolean sitting(LivingEntity entity) {
        Byte value = entity.getPersistentDataContainer().get(sittingKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private boolean isBaby(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(babyKey, PersistentDataType.LONG);
    }

    private static boolean flying(LivingEntity target) {
        return target instanceof Bat || target instanceof Bee || target instanceof Parrot || target instanceof Phantom;
    }

    private static ThreadLocalRandom random() {
        return ThreadLocalRandom.current();
    }
}
