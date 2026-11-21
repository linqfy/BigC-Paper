package dev.linqfy.bigCasares.modules.mythicdungeons.boss;

import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.mythicdungeons.DungeonLocator;
import dev.linqfy.bigCasares.modules.mythicdungeons.MythicKeys;
import dev.linqfy.bigCasares.modules.mythicdungeons.MythicMobCatalog;
import dev.linqfy.bigCasares.modules.mythicdungeons.MythicMobs;
import dev.linqfy.bigCasares.modules.mythicdungeons.MythicScheduler;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Los servicios compartidos que reciben los encuentros de los jefes miticos ({@code boss/<jefe>/*Encounter}).
 * Es la API estable entre el modulo y los encuentros: un encuentro no registra listeners ni tareas por su cuenta, usa
 * {@link #scheduler()} (cuyas tareas libera el modulo al apagarse) y crea sus entidades con {@link #spawnMinion} o
 * {@link #spawnOwned}, que quedan marcadas como del modulo (mob-scaling y Jeremy no las tocan), atadas a la instancia del
 * jefe y se borran con {@link #cleanup(UUID)} (el {@code stop()} de un controlador debe llamarlo) o al apagar el modulo.
 * Tras un reinicio los jefes no se recuperan y sus entidades huerfanas se borran al cargarse.
 * Todo se usa desde el hilo del servidor.
 */
public final class MythicBossSupport {

    private final JavaPlugin plugin;
    private final MythicMobs mobs;
    private final JavaModelGateway models;
    private final DungeonLocator locator;
    private final MythicScheduler scheduler;
    private final ClientPlatformGateway platforms;
    private final Map<UUID, List<Entity>> owned = new HashMap<>();
    private BedrockStandIns standIns;

    /** Lo conecta el modulo una vez; sin esto {@link #spawnBedrockStandIn} falla. */
    public void attachBedrockStandIns(BedrockStandIns standIns) {
        this.standIns = Objects.requireNonNull(standIns, "standIns");
    }

    public MythicBossSupport(JavaPlugin plugin, MythicMobs mobs, JavaModelGateway models, DungeonLocator locator,
                             MythicScheduler scheduler, ClientPlatformGateway platforms) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mobs = Objects.requireNonNull(mobs, "mobs");
        this.models = Objects.requireNonNull(models, "models");
        this.locator = Objects.requireNonNull(locator, "locator");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.platforms = Objects.requireNonNull(platforms, "platforms");
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public Logger logger() {
        return plugin.getLogger();
    }

    /** La API de spawn de los mobs miticos (con sus modelos y conductas). */
    public MythicMobs mobs() {
        return mobs;
    }

    /** Los mobs con los numeros de {@code mythic-dungeons.yml}. */
    public MythicMobCatalog mobCatalog() {
        return mobs.catalog();
    }

    /** El gateway de modelos de BetterModel (para modelos extra de un encuentro; cerrar siempre lo que se abre). */
    public JavaModelGateway models() {
        return models;
    }

    /** Donde estan las mazmorras (por ejemplo para no sacar minions de la sala). */
    public DungeonLocator locator() {
        return locator;
    }

    /** Tareas del servidor que el modulo cancela al apagarse; cancelar las propias en {@code stop()}. */
    public MythicScheduler scheduler() {
        return scheduler;
    }

    public boolean isBedrock(UUID playerId) {
        return platforms.resolvePlatform(playerId) == ClientPlatform.BEDROCK;
    }

    /** Los dobles vanilla para Bedrock (ver {@link BedrockStandIns}); el modulo los mueve y los limpia. */
    public BedrockStandIns bedrockStandIns() {
        if (standIns == null) {
            throw new IllegalStateException("Bedrock stand-ins are not available");
        }
        return standIns;
    }

    /**
     * Crea un doble vanilla que solo ven los jugadores de Bedrock, atado al jefe y que le pasa los golpes a
     * {@code anchor}. Usar para el jefe (con el Warden como ancla) y para piezas con modelo que se pueden golpear.
     */
    public <T extends LivingEntity> T spawnBedrockStandIn(UUID bossInstanceId, Entity anchor, Class<T> type,
                                                         double scale) {
        T standIn = spawnOwned(bossInstanceId, anchor.getLocation(), type, created -> {
            created.setAI(false);
            created.setInvulnerable(false);
        });
        BedrockStandIns.scale(standIn, scale);
        bedrockStandIns().bind(standIn, anchor);
        return standIn;
    }

    /**
     * Crea un mob mitico como minion del jefe: no suelta fragmentos, ni botin, ni experiencia y se borra con el jefe.
     *
     * @param mobId uno de los ids de {@link MythicMobCatalog} (por ejemplo {@code "draugr"})
     */
    public Optional<LivingEntity> spawnMinion(UUID bossInstanceId, String mobId, Location at) {
        Objects.requireNonNull(bossInstanceId, "bossInstanceId");
        Optional<LivingEntity> minion = mobCatalog().find(mobId)
            .flatMap(type -> mobs.spawn(type, at, false, entity -> markOwner(entity, bossInstanceId)));
        minion.ifPresent(entity -> track(bossInstanceId, entity));
        return minion;
    }

    /**
     * Crea cualquier entidad del encuentro (un decorado, un proyectil quieto, un mob vanilla de apoyo...). El paso
     * {@code setup} corre adentro del consumer de spawn, despues de marcarla.
     */
    public <T extends Entity> T spawnOwned(UUID bossInstanceId, Location at, Class<T> type, Consumer<? super T> setup) {
        Objects.requireNonNull(bossInstanceId, "bossInstanceId");
        World world = Objects.requireNonNull(at.getWorld(), "world");
        T entity = world.spawn(at, type, created -> {
            markOwner(created, bossInstanceId);
            setup.accept(created);
        });
        track(bossInstanceId, entity);
        return entity;
    }

    /** Ata una entidad ya existente al jefe para que se borre con el. */
    public void track(UUID bossInstanceId, Entity entity) {
        owned.computeIfAbsent(bossInstanceId, ignored -> new ArrayList<>()).add(entity);
    }

    /** Las entidades del jefe que siguen en el mundo. */
    public List<Entity> owned(UUID bossInstanceId) {
        List<Entity> list = owned.get(bossInstanceId);
        if (list == null) {
            return List.of();
        }
        list.removeIf(entity -> !entity.isValid());
        return List.copyOf(list);
    }

    /** Borra todas las entidades del jefe; idempotente. Devuelve cuantas habia. */
    public int cleanup(UUID bossInstanceId) {
        List<Entity> list = owned.remove(bossInstanceId);
        if (list == null) {
            return 0;
        }
        int removed = 0;
        for (Entity entity : list) {
            if (entity.isValid()) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Al apagar el modulo: borra lo de todos los jefes. */
    public void cleanupAll() {
        for (UUID boss : List.copyOf(owned.keySet())) {
            cleanup(boss);
        }
    }

    private void markOwner(Entity entity, UUID bossInstanceId) {
        MythicKeys keys = mobs.keys();
        keys.markSystem(entity);
        keys.markBossOwner(entity, bossInstanceId);
    }
}
