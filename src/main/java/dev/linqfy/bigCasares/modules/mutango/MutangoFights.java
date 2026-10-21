package dev.linqfy.bigCasares.modules.mutango;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Las peleas en curso y las entidades que las forman: de cada Mutango, Neguechen, el Sol, la Luna y las manos se
 * encarga de crearlos (una vaca sin IA con el modelo encima), ponerles el modelo de BetterModel, volver a armarlos si el
 * servidor se reinicia o el chunk se descarga, y darle un paso a cada pelea cada tick. Cada pelea es independiente.
 */
final class MutangoFights implements Listener {

    /** Vida de cada Mutango (cada uno tiene la suya y su barra). */
    private static final double MAX_HEALTH = 50.0;
    private static final double MOVE_SPEED = 0.5;
    private static final double FOLLOW_RANGE = 64.0;
    /** Pelea de los Mutangos que ya estaban en el mundo antes de que cada entidad guardara la suya. */
    private static final UUID LEGACY_FIGHT = UUID.nameUUIDFromBytes("mutango-pelea-vieja".getBytes(
        StandardCharsets.UTF_8));

    private final JavaPlugin plugin;
    private final MutangoKeys keys;
    private final MutangoBlockJournal journal;
    private final MutangoCinematic.Display display;
    private final MutangoBedrock bedrock;
    private final boolean bedrockModels;
    private final Map<UUID, EntityTracker> trackers = new HashMap<>();
    private final MutangoBossBars bossBars = new MutangoBossBars();
    /** Las peleas en curso: una por invocacion, cada una con su dificultad, sus Mutangos, su fase y sus versos. */
    private final Map<UUID, MutangoFight> fights = new LinkedHashMap<>();
    private MutangoShrine shrine;
    private MutangoRewards rewards;
    /** BetterModel tiene los modelos cargados (si no, los Mutangos se ven como vacas). */
    private boolean modelsReady;
    private final MutangoAnimations animations = new MutangoAnimations() {
        @Override
        public void once(UUID id, String animation) {
            playOnce(id, animation);
        }

        @Override
        public void loop(UUID id, String animation) {
            EntityTracker tracker = trackers.get(id);
            if (tracker != null) {
                tracker.animate(animation);
            }
        }

        @Override
        public void stop(UUID id, String animation) {
            EntityTracker tracker = trackers.get(id);
            if (tracker != null) {
                tracker.stopAnimation(animation);
            }
        }
    };
    /** Guarda la fase y si esta caido en la ficha de cada Mutango (vuelven a su estado si se reinicia el servidor). */
    private final MutangoBattle.Store battleStore = new MutangoBattle.Store() {
        @Override
        public void phase(Mob mutango, int phase) {
            mutango.getPersistentDataContainer().set(keys.phase, PersistentDataType.INTEGER, phase);
        }

        @Override
        public void downed(Mob mutango, boolean downed) {
            if (downed) {
                mutango.getPersistentDataContainer().set(keys.downed, PersistentDataType.BYTE, (byte) 1);
            } else {
                mutango.getPersistentDataContainer().remove(keys.downed);
            }
        }
    };

    MutangoFights(JavaPlugin plugin, MutangoKeys keys, MutangoBlockJournal journal, MutangoCinematic.Display display,
                  MutangoBedrock bedrock, boolean bedrockModels) {
        this.plugin = plugin;
        this.keys = keys;
        this.journal = journal;
        this.display = display;
        this.bedrock = bedrock;
        this.bedrockModels = bedrockModels;
    }

    /** Lo que se arma despues (se necesitan entre si): el santuario, los premios y si los modelos estan cargados. */
    void wire(MutangoShrine shrine, MutangoRewards rewards, boolean modelsReady) {
        this.shrine = shrine;
        this.rewards = rewards;
        this.modelsReady = modelsReady;
    }

    MutangoBossBars bossBars() {
        return bossBars;
    }

    MutangoFight get(UUID fightId) {
        return fights.get(fightId);
    }

    Collection<MutangoFight> all() {
        return fights.values();
    }

    /** Le pone a todos los Mutangos que ya estaban en el mundo (al arrancar o recargar) su modelo y su estado. */
    void attachAll() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Mob mutango : world.getEntitiesByClass(Mob.class)) {
                attachIfMutango(mutango);
            }
        }
    }

    /** Todos los que tienen modelo hacen esa animacion una vez. Devuelve cuantos son. */
    int playAll(String animation) {
        int count = 0;
        for (UUID id : trackers.keySet()) {
            playOnce(id, animation);
            count++;
        }
        return count;
    }

    /** Saca el modelo de esa entidad (murio). */
    void releaseTracker(UUID id) {
        EntityTracker tracker = trackers.remove(id);
        if (tracker != null) {
            tracker.close();
        }
    }

    /** Al apagar el plugin: frena todas las peleas y suelta los modelos y las barras. */
    void shutdown() {
        for (MutangoFight fight : fights.values()) {
            fight.phaseThree.stop();
            fight.cinematic.clear();
        }
        fights.clear();
        trackers.values().forEach(EntityTracker::close);
        trackers.clear();
        bossBars.removeAll();
    }

    /** Lef, Lihuen y Lican de esta pelea en esos lugares (en ese orden). */
    void spawnTrio(MutangoFight fight, List<Location> spots) {
        int level = fight.difficulty.level();
        for (int i = 0; i < MutangoModels.MODELS.size(); i++) {
            Location spot = spots.get(i);
            String model = MutangoModels.MODELS.get(i);
            Cow cow = spot.getWorld().spawn(spot, Cow.class, entity -> {
                keys.markSystem(entity);
                entity.getPersistentDataContainer().set(keys.model, PersistentDataType.STRING, model);
                entity.getPersistentDataContainer().set(keys.fight, PersistentDataType.STRING, fight.id.toString());
                entity.getPersistentDataContainer().set(keys.level, PersistentDataType.INTEGER, level);
                entity.getPersistentDataContainer().set(keys.fighters, PersistentDataType.STRING, fightersOf(fight));
                entity.getPersistentDataContainer().set(keys.phase, PersistentDataType.INTEGER, 1);
                MutangoCombat.armor(entity, level);
                entity.setSilent(true);
                entity.setInvulnerable(false);
                entity.setCustomName(MutangoBossBars.of(model).name());
                entity.setCustomNameVisible(false);
                // segun las victorias de quien los desperto, mas vida (logaritmico)
                double health = fight.difficulty.health(MAX_HEALTH);
                var maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) {
                    maxHealth.setBaseValue(health);
                }
                entity.setHealth(health);
                // las vacas caminan a 0.2; con 0.5 (lo de un aldeano) andan a la velocidad para la que esta pensado
                var speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.setBaseValue(MOVE_SPEED);
                }
                entity.setPersistent(true);
                entity.setRemoveWhenFarAway(false);
            });
            attachIfMutango(cow);
        }
        saveRoster(fight);
    }

    /** Una vaca sin IA, silenciosa y persistente con ese modelo (Neguechen, el Sol o la Luna) de esa pelea. */
    private Mob spawnModel(MutangoFight fight, String model, Location location, double maxHealth) {
        Cow cow = location.getWorld().spawn(location, Cow.class, entity -> {
            keys.markSystem(entity);
            entity.getPersistentDataContainer().set(keys.model, PersistentDataType.STRING, model);
            entity.getPersistentDataContainer().set(keys.fight, PersistentDataType.STRING, fight.id.toString());
            entity.getPersistentDataContainer().set(keys.level, PersistentDataType.INTEGER,
                fight.difficulty.level());
            entity.getPersistentDataContainer().set(keys.fighters, PersistentDataType.STRING, fightersOf(fight));
            if (model.equals(MutangoPhaseThree.NEGUECHEN)) {
                MutangoCombat.armor(entity, fight.difficulty.level());
            }
            entity.setSilent(true);
            entity.setGravity(false);
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            MutangoBossBars.Identity identity = MutangoBossBars.of(model);
            if (identity != null) {
                entity.setCustomName(identity.name());
                entity.setCustomNameVisible(false);
            }
            if (maxHealth > 0) {
                var health = entity.getAttribute(Attribute.MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(maxHealth);
                }
                entity.setHealth(maxHealth);
            } else {
                entity.setCollidable(false); // el Sol y la Luna: que no te empujen ni se traben con nada
            }
        });
        attach(cow, true);
        return cow;
    }

    /** Una pieza del santuario: quieta, sin IA, sin gravedad e invulnerable (el modelo lo pone attach). */
    Mob spawnShrinePart(String model, Location location) {
        Cow cow = location.getWorld().spawn(location, Cow.class, entity -> {
            keys.markSystem(entity);
            entity.getPersistentDataContainer().set(keys.model, PersistentDataType.STRING, model);
            entity.setSilent(true);
            entity.setGravity(false);
            entity.setAware(false);
            entity.setInvulnerable(true);
            entity.setCollidable(false);
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
        });
        cow.setRotation(location.getYaw(), 0);
        cow.setBodyYaw(location.getYaw());
        attach(cow, true);
        return cow;
    }

    /** Aparece Neguechen (fase 3) de esa pelea flotando en location. */
    void startPhaseThree(MutangoFight fight, Location location) {
        fight.phaseThree.start(location);
        fight.battle.announce(location, "§f§lFASE 3", "§eNeguechen §7y el Sol y la Luna",
            Sound.BLOCK_BEACON_ACTIVATE);
    }

    /** Saca todas las peleas (los Mutangos, Neguechen, el Sol y la Luna). Devuelve cuantos saco. */
    int removeAll() {
        for (MutangoFight fight : fights.values()) {
            // si ya habian vencido a Neguechen, los premios se dan ya mismo (no se pierden por sacar la pelea)
            rewards.deliverRewards(fight);
            fight.phaseThree.stop();
            fight.cinematic.clear();
        }
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Mob mutango : world.getEntitiesByClass(Mob.class)) {
                if (mutango.getPersistentDataContainer().has(keys.model, PersistentDataType.STRING)
                    && !shrine.isPart(mutango)) {
                    EntityTracker tracker = trackers.remove(mutango.getUniqueId());
                    if (tracker != null) {
                        tracker.close();
                    }
                    confirmDeath(mutango.getUniqueId());
                    bossBars.remove(mutango.getUniqueId());
                    mutango.remove();
                    removed++;
                }
            }
        }
        fights.clear();
        return removed;
    }

    /**
     * Murio de verdad (o la entidad ya no existe): sale de la cuenta de todas las peleas. Que su chunk se descargue NO es
     * una muerte: ahi solo se suelta el modelo y se conserva el lugar en la pelea.
     */
    void confirmDeath(UUID id) {
        for (MutangoFight fight : fights.values()) {
            fight.battle.forget(id);
            if (fight.members.contains(id)) {
                fight.roster.confirmDeath(id);
                saveRoster(fight);
            }
        }
    }

    /** Guarda en cada Mutango cargado quienes forman su pelea (para saber quienes faltan cargar despues de un reinicio). */
    private void saveRoster(MutangoFight fight) {
        String saved = fight.roster.serialize();
        for (Mob mutango : aliveOf(fight)) {
            mutango.getPersistentDataContainer().set(keys.roster, PersistentDataType.STRING, saved);
        }
    }

    /** Una pelea nueva, de quien la empieza: con su dificultad y avisandole (cada invocacion es independiente). */
    MutangoFight beginFight(Player summoner) {
        int level = rewards.wins(summoner);
        MutangoFight fight = newFight(UUID.randomUUID(), level);
        fight.fighters.add(summoner.getUniqueId());
        summoner.sendMessage(MutangoDifficulty.summonMessage(level));
        return fight;
    }

    private MutangoFight newFight(UUID id, int level) {
        MutangoFight fight = new MutangoFight(id, level, this::playOnce, animations, this::fightHost, battleStore,
            display);
        fights.put(id, fight);
        return fight;
    }

    /** La pelea de esta entidad (si despues de un reinicio todavia no esta armada, la arma con el nivel de la entidad). */
    MutangoFight fightOf(Entity entity) {
        var data = entity.getPersistentDataContainer();
        UUID id = LEGACY_FIGHT;
        String raw = data.get(keys.fight, PersistentDataType.STRING);
        if (raw != null) {
            try {
                id = UUID.fromString(raw);
            } catch (IllegalArgumentException invalid) {
                id = LEGACY_FIGHT;
            }
        }
        MutangoFight fight = fights.get(id);
        if (fight == null) {
            Integer level = data.get(keys.level, PersistentDataType.INTEGER);
            fight = newFight(id, level == null ? 0 : level);
        }
        return fight;
    }

    private static String fightersOf(MutangoFight fight) {
        return fight.fighters.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    /** Guarda quienes pelearon en cada Mutango y en Neguechen de esa pelea (para sumarles la victoria tras un reinicio). */
    void saveFighters(MutangoFight fight) {
        String saved = fightersOf(fight);
        List<UUID> ids = new ArrayList<>(fight.members);
        if (fight.phaseThree.neguechen() != null) {
            ids.add(fight.phaseThree.neguechen());
        }
        for (UUID id : ids) {
            if (plugin.getServer().getEntity(id) instanceof Mob mob) {
                mob.getPersistentDataContainer().set(keys.fighters, PersistentDataType.STRING, saved);
            }
        }
    }

    /** La pelea con Mutangos mas cercana al jugador (null si no hay ninguna en su mundo). */
    MutangoFight nearestFight(Player player) {
        MutangoFight best = null;
        double bestDistance = Double.MAX_VALUE;
        for (MutangoFight fight : fights.values()) {
            for (Mob mutango : aliveOf(fight)) {
                if (mutango.getWorld() != player.getWorld()) {
                    continue;
                }
                double distance = mutango.getLocation().distanceSquared(player.getLocation());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = fight;
                }
            }
        }
        return best;
    }

    /** Lo que la fase 3 de una pelea necesita del plugin: spawnear (con el id de su pelea), modelos y marcas. */
    private MutangoPhaseThree.Host fightHost(MutangoFight fight) {
        return new MutangoPhaseThree.Host() {
            @Override
            public Mob spawn(String model, Location location, double maxHealth) {
                return spawnModel(fight, model, location, maxHealth);
            }

            @Override
            public EntityTracker tracker(UUID id) {
                return trackers.get(id);
            }

            @Override
            public MutangoAnimations animations() {
                return animations;
            }

            @Override
            public NamespacedKey debrisKey() {
                return keys.debris;
            }

            @Override
            public NamespacedKey summonKey() {
                return keys.summon;
            }

            @Override
            public MutangoBlockJournal journal() {
                return journal;
            }

            @Override
            public void fighting(UUID neguechen) {
                if (plugin.getServer().getEntity(neguechen) instanceof Mob boss) {
                    boss.getPersistentDataContainer().set(keys.phase, PersistentDataType.INTEGER, 3);
                }
            }
        };
    }

    /** Los Mutangos vivos de esa pelea, siempre en el mismo orden (Lef, Lihuen, Lican). */
    List<Mob> aliveOf(MutangoFight fight) {
        List<Mob> alive = new ArrayList<>();
        for (UUID id : fight.members) {
            if (plugin.getServer().getEntity(id) instanceof Mob mutango && mutango.isValid()) {
                alive.add(mutango);
            }
        }
        if (alive.size() < 2) {
            return alive;
        }
        // en el orden Lef, Lihuen, Lican: cada uno se lee una sola vez (antes el PDC se leia en cada comparacion)
        Mob[] ordered = new Mob[MutangoModels.MODELS.size()];
        List<Mob> others = new ArrayList<>();
        for (Mob mutango : alive) {
            int index = MutangoModels.MODELS.indexOf(
                mutango.getPersistentDataContainer().get(keys.model, PersistentDataType.STRING));
            if (index >= 0 && ordered[index] == null) {
                ordered[index] = mutango;
            } else {
                others.add(mutango);
            }
        }
        List<Mob> sorted = new ArrayList<>(alive.size());
        for (Mob mutango : ordered) {
            if (mutango != null) {
                sorted.add(mutango);
            }
        }
        sorted.addAll(others);
        return sorted;
    }

    void playOnce(UUID id, String animation) {
        EntityTracker tracker = trackers.get(id);
        if (tracker != null) {
            tracker.animate(animation, AnimationModifier.DEFAULT_WITH_PLAY_ONCE, () -> { });
        }
    }

    void attachIfMutango(Entity entity) {
        attach(entity, false);
    }

    /** spawned: lo acaba de crear el plugin (si no, es uno que ya estaba en el mundo al cargar). */
    private void attach(Entity entity, boolean spawned) {
        if (!(entity instanceof Mob mutango) || trackers.containsKey(mutango.getUniqueId())) {
            return;
        }
        String model = mutango.getPersistentDataContainer().get(keys.model, PersistentDataType.STRING);
        if (model == null || !modelsReady) {
            return;
        }
        if (shrine.isPart(mutango) || MutangoShrine.STATUES.contains(model) || model.equals(MutangoShrine.CUBE)) {
            plugin.getServer().getMobGoals().removeAllGoals(mutango);
            BetterModel.model(model).ifPresent(renderer -> track(mutango, renderer));
            if (shrine.isCube(mutango)) {
                // si se corto un despertar (chunk descargado, servidor caido), el cubo vuelve a su lugar cerrado
                shrine.closeIfInterrupted(mutango);
            }
            return;
        }
        MutangoFight fight = fightOf(mutango);
        Integer level = mutango.getPersistentDataContainer().get(keys.level, PersistentDataType.INTEGER);
        // la fase 3 no sigue despues de reiniciar: Neguechen, el Sol o la Luna que quedaron de antes se borran
        // (salvo Neguechen peleando, que se retoma donde estaba con el Sol y la Luna nuevos)
        boolean resume = false;
        if (MutangoPhaseThree.MODELS.contains(model) && !spawned && !fight.phaseThree.owns(mutango.getUniqueId())) {
            Integer saved = mutango.getPersistentDataContainer().get(keys.phase, PersistentDataType.INTEGER);
            resume = model.equals(MutangoPhaseThree.NEGUECHEN) && saved != null && saved == 3
                && !fight.phaseThree.active();
            if (!resume) {
                plugin.getLogger().info("Borrado un " + model + " que quedo de antes (la fase 3 se vuelve a empezar).");
                mutango.remove();
                return;
            }
        }
        // en cada pelea hay un solo Lef, un solo Lihuen y un solo Lican: se borran los repetidos y los de antes
        // (eran aldeanos)
        boolean repeated = aliveOf(fight).stream().anyMatch(other ->
            !other.getUniqueId().equals(mutango.getUniqueId())
                && model.equals(other.getPersistentDataContainer().get(keys.model, PersistentDataType.STRING)));
        if (!(mutango instanceof Cow) || repeated) {
            plugin.getLogger().info("Borrado un Mutango " + (repeated ? "repetido" : "viejo (aldeano)") + ": " + model);
            mutango.remove();
            return;
        }
        // si el servidor se apago a mitad de una embestida o de la pila queda sin IA ni gravedad: arranca de cero
        mutango.setAware(true);
        mutango.setGravity(true);
        mutango.setCollidable(true);
        // sin ninguna IA propia (por ejemplo el panico al recibir un golpe): solo los mueve el plugin
        plugin.getServer().getMobGoals().removeAllGoals(mutango);
        // una vaca solo arma caminos de hasta 10 bloques: sin esto no llegan a lugares lejos y se traban
        var followRange = mutango.getAttribute(Attribute.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.setBaseValue(FOLLOW_RANGE);
        }
        // los de antes de tener vida eran invulnerables
        mutango.setInvulnerable(false);
        MutangoBossBars.Identity identity = MutangoBossBars.of(model);
        if (identity != null) {
            bossBars.show(mutango, identity);
        }
        // con la armadura de su nivel (cobre, hierro, diamante o netherite), dibujada en el modelo
        String shown = MutangoArmor.model(model, level == null ? 0 : level);
        BetterModel.model(shown).or(() -> BetterModel.model(model)).ifPresentOrElse(
            renderer -> track(mutango, renderer),
            () -> plugin.getLogger().warning("BetterModel no tiene cargado el modelo " + shown));
        if (MutangoModels.MODELS.contains(model)) {
            fight.roster.add(mutango.getUniqueId());
            // los que todavia no cargaron (otro chunk, despues de un reinicio) tambien forman la pelea
            fight.roster.adopt(MutangoRoster.parse(
                mutango.getPersistentDataContainer().get(keys.roster, PersistentDataType.STRING)));
        }
        if (!spawned) {
            restoreState(fight, mutango, model, resume);
        }
    }

    /**
     * Despues de un reinicio (o de que el chunk se descargue y vuelva) cada uno retoma lo que tenia guardado: la fase
     * de la pelea, si estaba caido, quienes pelearon y, Neguechen, su pelea. La vida ya la guarda Minecraft.
     */
    private void restoreState(MutangoFight fight, Mob mutango, String model, boolean resume) {
        var data = mutango.getPersistentDataContainer();
        String fighters = data.get(keys.fighters, PersistentDataType.STRING);
        if (fighters != null && !fighters.isBlank()) {
            for (String raw : fighters.split(",")) {
                try {
                    fight.fighters.add(UUID.fromString(raw.trim()));
                } catch (IllegalArgumentException invalid) {
                    // un id roto no impide cargar los demas
                }
            }
        }
        if (MutangoModels.MODELS.contains(model)) {
            Integer phase = data.get(keys.phase, PersistentDataType.INTEGER);
            if (phase != null) {
                fight.battle.restorePhase(phase);
            }
            if (data.has(keys.downed, PersistentDataType.BYTE)) {
                fight.battle.restoreDowned(mutango);
            }
        }
        if (resume) {
            fight.phaseThree.resume(mutango);
            plugin.getLogger().info("Neguechen retomada donde estaba (con el Sol y la Luna nuevos).");
        }
    }

    /**
     * Le pone el modelo. A los de Bedrock sin GeyserModelEngine no se les manda: Bedrock no lo puede dibujar, y sin
     * modelo BetterModel tampoco les esconde la vaca, asi ven vacas en vez de nada (como Don Pollos en BigC).
     */
    private void track(Mob mutango, ModelRenderer renderer) {
        EntityTracker tracker = renderer.getOrCreate(BukkitAdapter.adapt(mutango));
        restrictViewers(tracker);
        trackers.put(mutango.getUniqueId(), tracker);
    }

    /**
     * Solo a quien {@code showModel} acepta se le manda el modelo. {@code EntityTracker.spawnCondition} llego en
     * BetterModel 3.5.0 y BigCasares compila contra la 3.2.0, asi que se llama por reflexion si esta en el servidor
     * (si no, todos ven el modelo).
     */
    private void restrictViewers(EntityTracker tracker) {
        Predicate<PlatformPlayer> condition = player ->
            MutangoBedrock.showModel(bedrock.isBedrock(player.uuid()), bedrockModels);
        try {
            tracker.getClass().getMethod("spawnCondition", Predicate.class).invoke(tracker, condition);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // BetterModel sin spawnCondition
        }
    }

    /**
     * Cada tick: suelta el modelo y la barra de los que ya no estan cargados y le da un paso a cada pelea (de forma
     * independiente). Que una entidad no este cargada NO es que murio: conserva su lugar en la pelea y vuelve a enlazarse
     * (EntitiesLoadEvent) cuando carga otra vez. Solo una entidad que existe pero ya no es valida (murio o la sacaron)
     * se da por muerta.
     */
    void tick() {
        trackers.keySet().removeIf(id -> {
            Entity entity = plugin.getServer().getEntity(id);
            if (entity == null || !entity.isValid()) {
                EntityTracker tracker = trackers.get(id);
                if (tracker != null) {
                    tracker.close();
                }
                if (entity != null) {
                    confirmDeath(id);
                }
                bossBars.remove(id);
                return true;
            }
            return false;
        });
        for (MutangoFight fight : new ArrayList<>(fights.values())) {
            List<Mob> alive = aliveOf(fight);
            fight.battle.tick(alive);
            alive.forEach(mutango -> bossBars.update(mutango, fight.battle.isDowned(mutango.getUniqueId())));
            fight.cinematic.tick();
            try {
                fight.phaseThree.tick();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Error en la fase 3", exception);
            }
            if (fight.phaseThree.active() && !fight.phaseThree.dying()
                && plugin.getServer().getEntity(fight.phaseThree.neguechen()) instanceof Mob neguechen) {
                bossBars.update(neguechen, false);
            }
            if (fight.over()) {
                fights.remove(fight.id);
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::attachIfMutango);
    }
}
