package dev.linqfy.bigCasares.modules.mutango;

import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelScaler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Fase 3: Neguechen. Flota mirandote siempre y cada tanto pega un saltito a otro lugar cerca, con el Sol y la Luna
 * dando vueltas arriba de su cabeza.
 * <p>
 * Primer ataque: manda al Sol y despues a la Luna (asincronicos) a estrellarse contra vos. Cada uno calcula su
 * trayectoria hacia vos en el momento en que sale y la sigue sin corregir, bastante rapido. Si te toca te pega; siga o
 * no, termina clavado en el piso. Mientras tiene al Sol y a la Luna (y sus particulas alrededor) no le podes hacer
 * dano; cuando los manda se le van las particulas y ahi si le podes pegar, hasta 6 segundos despues de que salio la
 * Luna: vuelven las particulas y el Sol y la Luna se desclavan y vuelven a girar arriba de su cabeza.
 * <p>
 * Segundo ataque (MutangoHandsAttack): levanta las manos y te las tira en parabola a los costados; al caer largan
 * ondas expansivas que hay que saltar.
 * <p>
 * Tercer ataque (MutangoAnimalsAttack, el menos probable): invoca 3 vacas, 2 gallinas, 1 caballo, 1 burro y 5 chanchos
 * que te persiguen y te pegan. Cada vez que ataca elige al azar: 30% el primero, 40% el segundo y 30% el tercero.
 */
final class MutangoPhaseThree {

    static final String NEGUECHEN = "bigcasares_neguechen";
    static final String SUN = "bigcasares_sol";
    static final String MOON = "bigcasares_luna";
    static final List<String> MODELS = List.of(NEGUECHEN, SUN, MOON, MutangoHandsAttack.HAND_LEFT,
        MutangoHandsAttack.HAND_RIGHT);
    static final double MAX_HEALTH = 150.0;

    /** Neguechen se ve 1.6 veces mas grande que el modelo (unos 6.8 bloques de alto). */
    static final float NEGUECHEN_SCALE = 1.6f;
    /** Neguechen flota a esta altura del piso. */
    private static final double FLOAT_HEIGHT = 1.5;
    /** Radio y alto de la espiral de particulas alrededor del cuerpo. */
    private static final double AURA_RADIUS = 3.6;
    private static final double AURA_HEIGHT = 7.2;
    private static final double TARGET_RADIUS = 48.0;
    /** Hasta donde busca (bloques en cada eje) un lugar donde la grieta no toque bloques protegidos. */
    private static final int RIFT_SEARCH = 12;

    // saltitos: cada tanto se mueve un poco, sin irse lejos de donde aparecio
    private static final int HOP_EVERY_TICKS = 50;
    private static final int HOP_TICKS = 14;
    private static final double HOP_HEIGHT = 2.0;
    private static final double HOP_RADIUS = 6.0;
    private static final double HOP_MIN_DISTANCE = 2.5;

    // primer ataque
    private static final int FIRST_ATTACK_TICKS = 80;
    /** Que tan seguido elige cada ataque: el Sol y la Luna, las manos y (lo que queda) los animales. */
    private static final double SUN_AND_MOON_CHANCE = 0.30;
    private static final double HANDS_CHANCE = 0.40;
    /** Despues de que el Sol y la Luna vuelven a girar, cuanto espera para volver a mandarlos. */
    private static final int ATTACK_EVERY_TICKS = 100;
    /** La Luna sale un rato despues del Sol (asincronicos). */
    private static final int MOON_DELAY_TICKS = 16;
    /** Sin particulas (sin poder pegarle) hasta 6 s despues de que sale la Luna. */
    private static final int SHIELDLESS_AFTER_MOON_TICKS = 120;

    // cuando la vencen no muere: frena, sube un poco y se queda flotando mientras dice sus ultimos versos
    /** Cuanto sube (bloques) y en cuanto tiempo. */
    private static final double DEATH_RISE = 2.5;
    private static final int DEATH_RISE_TICKS = 50;

    // aparece y se va por una grieta en el piso (MutangoRift)
    /** Cuanto abajo del piso arranca (y termina): un poco mas que su altura, para que no se le vea el gorro. */
    private static final double UNDERGROUND = 8.5;
    private static final int EMERGE_TICKS = 70;
    private static final int SINK_TICKS = 55;
    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.fromRGB(110, 255, 140), 1.4f);

    // particulas de Neguechen
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 70), 1.3f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(255, 250, 230), 1.0f);
    private static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(170, 90, 255), 1.4f);
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(255, 60, 60), 1.2f);
    private static final Particle.DustOptions GRAY = new Particle.DustOptions(Color.fromRGB(120, 120, 130), 1.1f);

    interface Host {
        /** Spawnea una entidad con ese modelo (sin IA, persistente) y le engancha el modelo. */
        Mob spawn(String model, Location location, double maxHealth);

        EntityTracker tracker(UUID id);

        MutangoAnimations animations();

        /** Marca de los bloques revoleados por las ondas (el plugin no los deja apoyarse). */
        NamespacedKey debrisKey();

        /** Marca de los animales invocados (no sueltan nada y no se los puede montar ni alimentar). */
        NamespacedKey summonKey();

        /** Neguechen ya esta peleando (salio de la grieta): se guarda para retomarla si se reinicia el servidor. */
        void fighting(UUID neguechen);

        /** El diario de los bloques que se sacan (la grieta y las ondas) y hay que devolver aunque el servidor se caiga. */
        MutangoBlockJournal journal();
    }

    private final Host host;
    /** La dificultad de esta pelea (cada pelea la suya). */
    private final MutangoDifficulty difficulty;
    private UUID neguechen;
    /** Donde esta ahora (flotando); el Sol, la Luna y las particulas giran alrededor de este punto. */
    private Location home;
    /** Donde aparecio: los saltitos no se alejan mas de HOP_RADIUS de aca. */
    private Location anchor;
    private Location hopFrom;
    private Location hopTo;
    private int hopStart = -1;
    private int nextHop;
    private final MutangoSunMoon sunAndMoon;
    private final MutangoProtectedZone protectedZone;
    private int ticks;
    private boolean shield = true;
    private int nextAttack;
    private int attackStart = -1;
    private int shieldBack = -1;
    /** Desde que tick esta vencida (-1: peleando). */
    private int dyingStart = -1;
    private Location dyingFrom;
    /** La grieta abierta (o cerrandose), si hay. */
    private MutangoRift rift;
    /** Saliendo de la grieta: desde que tick (-1: ya salio). */
    private int emergeStart = -1;
    /** Yendose por la grieta: desde que tick (-1: no se esta yendo). */
    private int leaveStart = -1;
    private Location leaveFrom;
    /** Cuando se termina de cerrar la grieta por la que se fue: a quien avisar y donde (el piso). */
    private Consumer<Location> onGone;
    private Location goneAt;

    private final MutangoHandsAttack hands;
    private final MutangoAnimalsAttack animals;

    MutangoPhaseThree(Host host, MutangoDifficulty difficulty) {
        this.host = host;
        this.difficulty = difficulty;
        this.hands = new MutangoHandsAttack(host, difficulty, this::allowed);
        this.animals = new MutangoAnimalsAttack(host, difficulty);
        this.sunAndMoon = new MutangoSunMoon(host, difficulty);
        this.protectedZone = new MutangoProtectedZone(this::allowed, difficulty);
    }

    boolean active() {
        return neguechen != null;
    }

    /**
     * Todavia no termino del todo: peleando, vencida hablando o hundiendose, o con la grieta por la que se fue sin
     * cerrar (los premios se dejan recien cuando se cierra). Mientras tanto no puede empezar otra pelea.
     */
    boolean busy() {
        return neguechen != null || rift != null || onGone != null;
    }

    boolean owns(UUID id) {
        return id.equals(neguechen) || sunAndMoon.owns(id) || hands.owns(id);
    }

    UUID neguechen() {
        return neguechen;
    }

    /** Con el Sol y la Luna (y sus particulas) no le podes pegar; cuando los manda, si. Al Sol y a la Luna nunca. */
    boolean isProtected(UUID id) {
        return sunAndMoon.owns(id) || hands.owns(id)
            || id.equals(neguechen) && (shield || dyingStart >= 0 || emergeStart >= 0);
    }

    /** La vencieron y esta flotando, diciendo sus ultimos versos. */
    boolean dying() {
        return neguechen != null && dyingStart >= 0;
    }

    /**
     * La vencieron: en vez de morir frena (se cortan los ataques, se van el Sol y la Luna) y empieza a subir despacio.
     * Se queda flotando hasta vanish().
     */
    void beginDeath() {
        if (neguechen == null || dyingStart >= 0) {
            return;
        }
        protectedZone.clear();
        emergeStart = -1;
        hands.stop();
        hands.restoreAll();
        animals.stop();
        sunAndMoon.vanish();
        hopStart = -1;
        attackStart = -1;
        shieldBack = -1;
        shield = true;
        dyingStart = ticks;
        dyingFrom = home.clone();
        World world = home.getWorld();
        world.playSound(home, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.5f);
        world.playSound(home, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2f, 0.5f);
        world.spawnParticle(Particle.END_ROD, home.clone().add(0, AURA_HEIGHT / 2, 0), 120, AURA_RADIUS,
            AURA_HEIGHT / 2, AURA_RADIUS, 0.05);
        world.spawnParticle(Particle.DUST, home.clone().add(0, AURA_HEIGHT / 2, 0), 80, AURA_RADIUS,
            AURA_HEIGHT / 2, AURA_RADIUS, 0, GOLD);
    }

    /**
     * Termino de hablar: se abre una grieta debajo suyo y se hunde por ahi; la grieta se cierra y entonces avisa a
     * onGone con el piso donde estaba (para dejar los premios). Si no estaba vencida no hace nada y devuelve false.
     */
    boolean leave(Consumer<Location> onGone) {
        if (!dying() || leaveStart >= 0) {
            return false;
        }
        this.onGone = onGone;
        Location ground = home.clone();
        ground.setY(MutangoGround.y(ground) + 1);
        goneAt = ground;
        if (!(Bukkit.getEntity(neguechen) instanceof Mob)) {
            // su chunk no esta cargado (te fuiste lejos mientras hablaba): se va sin animacion, pero los premios quedan
            finishLeaving();
            return true;
        }
        // si ahi la grieta tocaria bloques protegidos, se corre al lugar permitido mas cercano antes de hundirse
        Location spot = allowedRiftSpot();
        if (spot != null) {
            home = spot.clone().add(0, DEATH_RISE, 0);
            ground = spot.clone();
            ground.setY(MutangoGround.y(ground) + 1);
            goneAt = ground;
        }
        leaveStart = ticks;
        leaveFrom = home.clone();
        if (rift != null) {
            rift.restoreAll();
        }
        rift = new MutangoRift(MutangoGround.block(home), riftAlongX(), ticks * 31L + 7,
            host.journal(), this::allowed);
        rift.open();
        return true;
    }

    /** Ya se hundio entera: desaparece y se cierra la grieta (onGone corre cuando termina de cerrarse). */
    private void finishLeaving() {
        if (neguechen != null && Bukkit.getEntity(neguechen) instanceof Mob boss) {
            boss.remove();
        }
        neguechen = null;
        dyingStart = -1;
        leaveStart = -1;
        if (rift != null) {
            rift.close();
        } else {
            goneNow();
        }
    }

    private void goneNow() {
        Consumer<Location> callback = onGone;
        onGone = null;
        if (callback != null && goneAt != null) {
            callback.accept(goneAt);
        }
    }

    /** La grieta va de costado para quien mira (asi se ve entera): a lo largo del eje que no apunta al jugador. */
    private boolean riftAlongX() {
        Player target = nearestTarget();
        if (target == null) {
            return true;
        }
        Vector look = target.getLocation().toVector().subtract(home.toVector());
        return Math.abs(look.getZ()) >= Math.abs(look.getX());
    }


    /**
     * Se puede tocar ese bloque (no esta en una zona protegida): se pregunta con el evento de cambio de bloque de una
     * entidad (el mismo que escuchan los plugins de proteccion contra los mobs que rompen bloques). Sin Neguechen
     * todavia, o si nadie lo cancela, se puede.
     */
    private boolean allowed(Block block) {
        Entity actor = neguechen == null ? null : Bukkit.getEntity(neguechen);
        if (actor == null) {
            return true;
        }
        EntityChangeBlockEvent event = new EntityChangeBlockEvent(actor,
            block, Material.AIR.createBlockData());
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    /**
     * El lugar mas cercano a home (hasta RIFT_SEARCH bloques) donde la grieta entera se puede abrir sin tocar bloques
     * protegidos, o null si donde esta ya se puede (o si no hay ninguno: ahi la grieta deja sin tocar lo protegido).
     */
    private Location allowedRiftSpot() {
        boolean alongX = riftAlongX();
        World world = home.getWorld();
        for (int[] offset : MutangoProtection.offsetsByDistance(RIFT_SEARCH)) {
            Location candidate = home.clone().add(offset[0], 0, offset[1]);
            candidate.setY(MutangoGround.y(candidate) + 1 + FLOAT_HEIGHT);
            MutangoRift probe = new MutangoRift(MutangoGround.block(candidate), alongX,
                candidate.getBlockX() * 31L + candidate.getBlockZ(), host.journal(), this::allowed);
            if (probe.allAllowed()) {
                return offset[0] == 0 && offset[1] == 0 ? null : candidate;
            }
        }
        return null;
    }

    /** Aparece Neguechen flotando sobre location, con el Sol y la Luna. */
    void start(Location location) {
        stop();
        home = location.clone();
        home.setY(MutangoGround.y(home) + 1 + FLOAT_HEIGHT);
        // sale de una grieta: arranca escondida abajo del piso
        Mob boss = host.spawn(NEGUECHEN, home.clone().subtract(0, FLOAT_HEIGHT + UNDERGROUND, 0),
            difficulty.health(MAX_HEALTH));
        neguechen = boss.getUniqueId();
        // si la grieta tocaria bloques protegidos, aparece en el lugar permitido mas cercano
        Location spot = allowedRiftSpot();
        if (spot != null) {
            home = spot;
            boss.teleport(home.clone().subtract(0, FLOAT_HEIGHT + UNDERGROUND, 0));
        }
        anchor = home.clone();
        hopStart = -1;
        nextHop = HOP_EVERY_TICKS;
        rift = new MutangoRift(MutangoGround.block(home), riftAlongX(), home.getBlockX() * 31L
            + home.getBlockZ(), host.journal(), this::allowed);
        rift.open();
        EntityTracker bossTracker = host.tracker(neguechen);
        if (bossTracker != null) {
            bossTracker.scaler(ModelScaler.value(NEGUECHEN_SCALE));
        }
        ticks = 0;
        shield = true;
        emergeStart = 0;
        nextAttack = Integer.MAX_VALUE;
        attackStart = -1;
        shieldBack = -1;
    }

    /**
     * Retoma a Neguechen que estaba peleando cuando se reinicio el servidor (o se descargo y volvio su chunk): sigue
     * donde estaba, con la vida que le quedaba, y vuelven el Sol y la Luna girando. Los ataques en curso (manos,
     * animales, el Sol y la Luna en vuelo) se pierden.
     */
    void resume(Mob boss) {
        stop();
        neguechen = boss.getUniqueId();
        home = boss.getLocation().clone();
        anchor = home.clone();
        hopStart = -1;
        nextHop = HOP_EVERY_TICKS;
        ticks = 0;
        shield = true;
        attackStart = -1;
        shieldBack = -1;
        EntityTracker bossTracker = host.tracker(neguechen);
        if (bossTracker != null) {
            bossTracker.scaler(ModelScaler.value(NEGUECHEN_SCALE));
        }
        emerged();
    }

    /** Ya salio de la grieta: aparecen el Sol y la Luna y empieza a pelear. */
    private void emerged() {
        emergeStart = -1;
        host.fighting(neguechen);
        if (rift != null) {
            rift.close();
        }
        sunAndMoon.spawn(home, ticks, neguechen);
        nextAttack = ticks + difficulty.cooldownTicks(FIRST_ATTACK_TICKS);
        nextHop = ticks + HOP_EVERY_TICKS;
        World world = home.getWorld();
        world.playSound(home, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.6f);
        world.spawnParticle(Particle.END_ROD, home.clone().add(0, 3.5, 0), 150, 2.5, 3.5, 2.5, 0.08);
        world.spawnParticle(Particle.DUST, home.clone().add(0, 3.5, 0), 120, 3, 3.5, 3, 0, GOLD);
    }

    /** Saca a Neguechen, al Sol y a la Luna. */
    void stop() {
        protectedZone.clear();
        dyingStart = -1;
        emergeStart = -1;
        leaveStart = -1;
        onGone = null;
        if (rift != null) {
            rift.restoreAll();
            rift = null;
        }
        hands.stop();
        hands.restoreAll();
        animals.stop();
        sunAndMoon.discard();
        if (neguechen != null && Bukkit.getEntity(neguechen) instanceof Mob mob) {
            mob.remove();
        }
        neguechen = null;
    }

    /** Neguechen murio: se van el Sol y la Luna. */
    void defeated() {
        protectedZone.clear();
        hands.stop();
        hands.restoreAll();
        animals.stop();
        neguechen = null;
        dyingStart = -1;
        emergeStart = -1;
        leaveStart = -1;
        onGone = null;
        if (rift != null) {
            rift.restoreAll();
            rift = null;
        }
        sunAndMoon.vanish();
    }

    void tick() {
        hands.tickRestore();
        animals.tick();
        if (rift != null) {
            rift.tick();
            if (rift.closed()) {
                rift = null;
                goneNow();
            }
        }
        if (neguechen == null) {
            return;
        }
        if (!(Bukkit.getEntity(neguechen) instanceof Mob boss) || !boss.isValid()) {
            // vencida y hablando: si su chunk se descargo la espera (los premios se dejan igual, ver leave); si ya se
            // estaba hundiendo, termina de irse asi la grieta se cierra igual
            if (leaveStart >= 0) {
                finishLeaving();
            } else if (dyingStart < 0) {
                stop();
            }
            return;
        }
        ticks++;
        Player target = nearestTarget();
        if (emergeStart >= 0) {
            emerge(boss, target);
            return;
        }
        if (dyingStart >= 0) {
            if (leaveStart >= 0) {
                sink(boss, target);
            } else {
                rise(boss, target);
            }
            return;
        }
        hover(boss, target);
        particles();
        protectedZone.tick(ticks, Bukkit.getEntity(neguechen));

        // cada vez que ataca elige al azar: el Sol y la Luna (30%), las manos (40%) o los animales (30%; solo si no
        // quedan animales de la vez anterior, si no las manos)
        if (attackStart < 0 && !hands.active() && ticks >= nextAttack && target != null
            && sunAndMoon.orbiting()) {
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll < SUN_AND_MOON_CHANCE) {
                attackSunAndMoon(target);
            } else if (roll < SUN_AND_MOON_CHANCE + HANDS_CHANCE || animals.active()) {
                if (protectedZone.contains(target)) {
                    // no puede tirarle las manos: esta metido en una zona protegida. Tiene 5 segundos para salir
                    protectedZone.warn(target, ticks);
                    nextAttack = ticks + difficulty.cooldownTicks(ATTACK_EVERY_TICKS);
                } else {
                    hands.start(neguechen, home);
                }
            } else {
                animals.start(neguechen, home);
                nextAttack = ticks + difficulty.cooldownTicks(ATTACK_EVERY_TICKS);
            }
        }
        if (hands.active() && hands.tick(home, boss.getLocation().getYaw(), target)) {
            nextAttack = ticks + difficulty.cooldownTicks(ATTACK_EVERY_TICKS);
        }
        if (attackStart >= 0 && ticks == attackStart + MOON_DELAY_TICKS) {
            sunAndMoon.launchMoon(nearestTarget());
        }
        // vuelven las particulas y el Sol y la Luna se desclavan y vuelven a girar
        if (shieldBack >= 0 && ticks >= shieldBack) {
            shield = true;
            shieldBack = -1;
            attackStart = -1;
            sunAndMoon.recall();
            home.getWorld().playSound(home, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.2f);
            home.getWorld().spawnParticle(Particle.END_ROD, home.clone().add(0, AURA_HEIGHT / 2, 0), 60,
                AURA_RADIUS, AURA_HEIGHT / 2, AURA_RADIUS, 0.02);
        }

        // ya estan de vuelta girando: el proximo ataque, un rato despues
        sunAndMoon.tick(home, ticks, neguechen, target,
            () -> nextAttack = ticks + difficulty.cooldownTicks(ATTACK_EVERY_TICKS));
    }

    /** Primer ataque: se le van las particulas y manda al Sol (la Luna sale MOON_DELAY_TICKS despues). */
    private void attackSunAndMoon(Player target) {
        attackStart = ticks;
        shield = false;
        shieldBack = ticks + MOON_DELAY_TICKS + SHIELDLESS_AFTER_MOON_TICKS;
        host.animations().once(neguechen, "lanzar");
        // se le apaga la espiral: estalla para afuera
        home.getWorld().spawnParticle(Particle.DUST, home.clone().add(0, AURA_HEIGHT / 2, 0), 60, AURA_RADIUS,
            AURA_HEIGHT / 2, AURA_RADIUS, 0, GOLD);
        home.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, home.clone().add(0, AURA_HEIGHT / 2, 0), 40, 1.5,
            2.5, 1.5, 0.3);
        home.getWorld().playSound(home, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.7f);
        sunAndMoon.launchSun(target);
    }

    // ---------------------------------------------------------------- Neguechen

    /** Sale de la grieta: espera a que se abra y sube despacio desde abajo del piso hasta donde flota. */
    private void emerge(Mob boss, Player target) {
        int opening = MutangoRiftShape.duration(MutangoRift.HALF_LENGTH) + 5;
        int t = ticks - emergeStart;
        Location below = home.clone().subtract(0, FLOAT_HEIGHT + UNDERGROUND, 0);
        double s = t < opening ? 0 : Math.min(1.0, (t - opening) / (double) EMERGE_TICKS);
        double eased = 1 - Math.pow(1 - s, 3);
        place(boss, below.clone().add(0, (FLOAT_HEIGHT + UNDERGROUND) * eased, 0), target);
        World world = home.getWorld();
        if (t == opening) {
            world.playSound(home, Sound.ENTITY_WARDEN_EMERGE, 2f, 0.7f);
            world.playSound(home, Sound.BLOCK_BEACON_AMBIENT, 2f, 0.5f);
        }
        if (s > 0) {
            // luz verde que sale con ella de la grieta
            Location ground = home.clone().subtract(0, FLOAT_HEIGHT - 0.3, 0);
            world.spawnParticle(Particle.DUST, ground, 6, 1.2, 0.3, 1.2, 0, GREEN);
            world.spawnParticle(Particle.GLOW, ground, 3, 1.0, 0.5, 1.0, 0.05);
        }
        if (s >= 1.0) {
            emerged();
        }
    }

    /** Se va: espera a que se abra la grieta y se hunde (cada vez mas rapido) hasta abajo del piso. */
    private void sink(Mob boss, Player target) {
        int opening = MutangoRiftShape.duration(MutangoRift.HALF_LENGTH) + 5;
        int t = ticks - leaveStart;
        double s = t < opening ? 0 : Math.min(1.0, (t - opening) / (double) SINK_TICKS);
        double depth = (leaveFrom.getY() - goneAt.getY() + UNDERGROUND) * s * s;
        home = leaveFrom.clone().subtract(0, depth, 0);
        place(boss, home, target);
        World world = home.getWorld();
        if (t == opening) {
            world.playSound(goneAt, Sound.ENTITY_WARDEN_DIG, 2f, 0.6f);
            world.playSound(goneAt, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.6f);
        }
        Location ground = goneAt.clone().add(0, 0.3, 0);
        world.spawnParticle(Particle.DUST, ground, 5, 1.2, 0.3, 1.2, 0, GREEN);
        world.spawnParticle(Particle.GLOW, ground, 2, 1.0, 0.5, 1.0, 0.05);
        if (s >= 1.0) {
            finishLeaving();
        }
    }

    /** La pone en at mirando al jugador (sin IA ni gravedad: la mueve el plugin). */
    private void place(Mob boss, Location at, Player target) {
        boss.setAware(false);
        boss.setGravity(false);
        Location to = at.clone();
        to.setYaw(boss.getLocation().getYaw());
        if (target != null) {
            Vector look = target.getLocation().toVector().subtract(at.toVector()).setY(0);
            if (look.lengthSquared() > 1e-4) {
                to.setYaw(MutangoTargets.yaw(look));
            }
        }
        boss.teleport(to);
        boss.setBodyYaw(to.getYaw());
    }

    /** Vencida: sube despacio (frenando al final) y se queda flotando, mirandote, con luz que le cae como ceniza. */
    private void rise(Mob boss, Player target) {
        boss.setAware(false);
        boss.setGravity(false);
        double s = Math.min(1.0, (ticks - dyingStart) / (double) DEATH_RISE_TICKS);
        double eased = 1 - (1 - s) * (1 - s);
        // despues de subir se mece un poquito
        double sway = s >= 1.0 ? Math.sin((ticks - dyingStart - DEATH_RISE_TICKS) * 0.08) * 0.15 : 0;
        home = dyingFrom.clone().add(0, DEATH_RISE * eased + sway, 0);
        Location at = home.clone();
        at.setYaw(boss.getLocation().getYaw());
        if (target != null) {
            Vector look = target.getLocation().toVector().subtract(home.toVector()).setY(0);
            if (look.lengthSquared() > 1e-4) {
                at.setYaw(MutangoTargets.yaw(look));
            }
        }
        boss.teleport(at);
        boss.setBodyYaw(at.getYaw());
        World world = home.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int k = 0; k < 2; k++) {
            Location point = home.clone().add(rng.nextDouble(-1.6, 1.6), rng.nextDouble(AURA_HEIGHT),
                rng.nextDouble(-1.6, 1.6));
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, k == 0 ? GOLD : WHITE);
        }
        world.spawnParticle(Particle.END_ROD, home.clone().add(rng.nextDouble(-1.2, 1.2), AURA_HEIGHT * 0.8,
            rng.nextDouble(-1.2, 1.2)), 0, 0, -0.4, 0, 0.05);
        if (ticks % 4 == 0) {
            double angle = ticks * 0.15;
            Location ring = home.clone().add(Math.cos(angle) * 2.2, 0.2, Math.sin(angle) * 2.2);
            world.spawnParticle(Particle.DUST, ring, 1, 0, 0, 0, 0, GOLD);
        }
    }

    private void hover(Mob boss, Player target) {
        boss.setAware(false);
        boss.setGravity(false);
        hop();
        Location at = home.clone();
        if (target != null) {
            Vector look = target.getLocation().toVector().subtract(home.toVector()).setY(0);
            if (look.lengthSquared() > 1e-4) {
                at.setYaw(MutangoTargets.yaw(look));
            }
        } else {
            at.setYaw(boss.getLocation().getYaw());
        }
        boss.teleport(at);
        boss.setBodyYaw(at.getYaw());
    }

    /** Cada tanto salta en arco a otro lugar cerca (sin alejarse de donde aparecio); mientras, sigue mirandote. */
    private void hop() {
        if (hopStart < 0) {
            if (ticks < nextHop) {
                return;
            }
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int tries = 0; tries < 10; tries++) {
                double angle = rng.nextDouble(Math.PI * 2);
                double radius = rng.nextDouble(HOP_RADIUS);
                Location to = anchor.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                to.setY(MutangoGround.y(to) + 1 + FLOAT_HEIGHT);
                if (to.distance(home) >= HOP_MIN_DISTANCE && Math.abs(to.getY() - home.getY()) < 4) {
                    hopFrom = home.clone();
                    hopTo = to;
                    hopStart = ticks;
                    home.getWorld().playSound(home, Sound.ENTITY_BREEZE_JUMP, 1.2f, 0.6f);
                    host.animations().once(neguechen, "salto");
                    Location ground = home.clone().subtract(0, FLOAT_HEIGHT - 0.2, 0);
                    home.getWorld().spawnParticle(Particle.CLOUD, ground, 18, 1.2, 0.1, 1.2, 0.05);
                    home.getWorld().spawnParticle(Particle.DUST, ground, 20, 1.5, 0.1, 1.5, 0, GOLD);
                    break;
                }
            }
            nextHop = ticks + HOP_EVERY_TICKS;
            return;
        }
        double s = Math.min(1.0, (ticks - hopStart) / (double) HOP_TICKS);
        home = hopFrom.clone().add(hopTo.clone().subtract(hopFrom).toVector().multiply(s))
            .add(0, 4 * HOP_HEIGHT * s * (1 - s), 0);
        home.getWorld().spawnParticle(Particle.END_ROD, home, 2, 0.6, 0.2, 0.6, 0.01);
        if (s >= 1.0) {
            hopStart = -1;
            nextHop = ticks + HOP_EVERY_TICKS;
            World world = home.getWorld();
            world.playSound(home, Sound.ENTITY_BREEZE_LAND, 1.2f, 0.6f);
            Location ground = home.clone().subtract(0, FLOAT_HEIGHT - 0.2, 0);
            for (int k = 0; k < 24; k++) {
                double angle = 2 * Math.PI * k / 24;
                Vector out = new Vector(Math.cos(angle), 0, Math.sin(angle));
                world.spawnParticle(Particle.CLOUD, ground.clone().add(out.clone().multiply(1.2)), 0,
                    out.getX(), 0.05, out.getZ(), 0.25);
                world.spawnParticle(Particle.DUST, ground.clone().add(out.multiply(2.6)), 1, 0, 0, 0, 0, GOLD);
            }
        }
    }

    /**
     * Con el Sol y la Luna (protegido): doble espiral dorada y blanca que sube girando, chispas que suben y un anillo
     * violeta y dorado que gira en el piso debajo suyo. Sin ellos (le podes pegar): se le apaga la espiral, el anillo
     * queda gris y le saltan chispas electricas y puntos rojos por el cuerpo.
     */
    private void particles() {
        World world = home.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location base = home.clone().subtract(0, FLOAT_HEIGHT - 0.15, 0);
        if (shield) {
            for (int strand = 0; strand < 2; strand++) {
                for (int k = 0; k < 3; k++) {
                    double angle = ticks * 0.2 + strand * Math.PI + k * 0.35;
                    double y = (ticks * 0.12 + k * 0.5 + strand * AURA_HEIGHT / 2) % AURA_HEIGHT;
                    Location point = home.clone().add(Math.cos(angle) * AURA_RADIUS, y,
                        Math.sin(angle) * AURA_RADIUS);
                    world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, strand == 0 ? GOLD : WHITE);
                }
            }
            for (int k = 0; k < 2; k++) {
                Location spark = home.clone().add(rng.nextDouble(-AURA_RADIUS, AURA_RADIUS) * 0.8,
                    rng.nextDouble(AURA_HEIGHT), rng.nextDouble(-AURA_RADIUS, AURA_RADIUS) * 0.8);
                world.spawnParticle(Particle.END_ROD, spark, 0, 0, 0.6, 0, 0.05);
            }
        } else {
            for (int k = 0; k < 3; k++) {
                Location spark = home.clone().add(rng.nextDouble(-1.8, 1.8), rng.nextDouble(AURA_HEIGHT * 0.9),
                    rng.nextDouble(-1.8, 1.8));
                world.spawnParticle(Particle.ELECTRIC_SPARK, spark, 2, 0.2, 0.2, 0.2, 0.05);
            }
            if (ticks % 2 == 0) {
                world.spawnParticle(Particle.DUST, home.clone().add(0, AURA_HEIGHT / 2, 0), 2, 1.6, 2.5, 1.6, 0,
                    RED);
            }
        }
        if (ticks % 2 == 0) {
            for (int k = 0; k < 14; k++) {
                double angle = 2 * Math.PI * k / 14 - ticks * 0.08;
                Location point = base.clone().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0);
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                    !shield ? GRAY : k % 2 == 0 ? PURPLE : GOLD);
            }
        }
    }

    private Player nearestTarget() {
        Player nearest = null;
        double best = TARGET_RADIUS * TARGET_RADIUS;
        for (Player player : home.getWorld().getPlayers()) {
            if (!MutangoTargets.targetable(player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(home);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }
}
