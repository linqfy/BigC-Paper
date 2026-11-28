package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Los sonidos propios del modulo. Cada nombre es un evento de {@code assets/caichet/sounds.json} (Java) y una
 * definicion de {@code sound_definitions.json} (Bedrock): Geyser manda a Bedrock el nombre tal cual cuando no tiene
 * un equivalente vanilla, asi que los dos packs usan el mismo identificador.
 */
final class CaiChetSounds {

    static final String TRAIN_WHISTLE = "caichet:train.whistle";
    static final String TRAIN_ENGINE = "caichet:train.engine";
    static final String TRAIN_STEAM = "caichet:train.steam";
    static final String WEAPON_BEAM = "caichet:weapon.beam";
    static final String DOOR_KEY = "caichet:door.key";
    static final String DOOR_CREAK = "caichet:door.creak";
    static final String FIGURE_APPEAR = "caichet:figure.appear";
    static final String FIGURE_CHASE = "caichet:figure.chase";
    static final String EYE_ROAR = "caichet:eye.roar";
    static final String PIT_TRAVEL = "caichet:pit.travel";
    static final String PIT_RETURN = "caichet:pit.return";
    static final String DREAM_CHIMES = "caichet:dream.chimes";
    static final String HOTEL_FLICKER = "caichet:hotel.flicker";
    static final String ENTITY_JUMPSCARE = "caichet:entity.jumpscare";
    static final String ENTITY_ATTACK = "caichet:entity.attack";
    static final String ENTITY_STEP = "caichet:entity.step";
    static final String SONRISA_LAUGH = "caichet:sonrisa.laugh";
    static final String E666_HEARTBEAT_SLOW = "caichet:e666.heartbeat_slow";
    static final String E666_HEARTBEAT_FAST = "caichet:e666.heartbeat_fast";
    static final String JUANCARLOS_GROWL = "caichet:juancarlos.growl";
    static final String JUANCARLOS_GRAB = "caichet:juancarlos.grab";
    static final String ARANA_SCREECH = "caichet:arana.screech";
    static final String ARANA_CLICK = "caichet:arana.click";
    static final String PAYASO_GIGGLE = "caichet:payaso.giggle";
    static final String PAYASO_LAUGH = "caichet:payaso.laugh";
    static final String BACKROOMS_AMBIENT = "caichet:backrooms.ambient";

    static final List<String> ALL = withMusic(List.of(TRAIN_WHISTLE, TRAIN_ENGINE, TRAIN_STEAM, WEAPON_BEAM, DOOR_KEY, DOOR_CREAK,
            FIGURE_APPEAR, FIGURE_CHASE, EYE_ROAR, PIT_TRAVEL, PIT_RETURN, DREAM_CHIMES, HOTEL_FLICKER,
            ENTITY_JUMPSCARE, ENTITY_ATTACK, ENTITY_STEP, SONRISA_LAUGH, E666_HEARTBEAT_SLOW, E666_HEARTBEAT_FAST, JUANCARLOS_GROWL,
            JUANCARLOS_GRAB, ARANA_SCREECH, ARANA_CLICK, PAYASO_GIGGLE, PAYASO_LAUGH, BACKROOMS_AMBIENT));

    private static final SoundGate GATE = new SoundGate();

    /** Los sonidos sueltos mas los pedazos de las canciones de {@link CaiChetMusic}. */
    private static List<String> withMusic(List<String> effects) {
        List<String> all = new ArrayList<>(effects);
        all.addAll(CaiChetMusic.events());
        return List.copyOf(all);
    }

    private CaiChetSounds() {
    }

    /** Suena para todos los que esten cerca del lugar (el volumen manda hasta donde se oye). */
    static void play(World world, Location at, String event, SoundCategory category, float volume, float pitch) {
        world.playSound(at, event, category, volume, pitch);
    }

    /** Suena solo para este jugador, como si viniera de at. */
    static void playTo(Player player, Location at, String event, SoundCategory category, float volume, float pitch) {
        player.playSound(at, event, category, volume, pitch);
    }

    /** Suena para este jugador pegado a una entidad: se mueve con ella. Bedrock no ve algunas, ahi se usa la posicion. */
    static void follow(Player player, Entity emitter, String event, SoundCategory category, float volume, float pitch) {
        player.playSound(emitter, event, category, volume, pitch);
    }

    /** Tope de frecuencia por jugador y clave; ver {@link SoundGate#allow}. */
    static boolean allow(UUID player, String key, long nowTicks, long cooldownTicks) {
        return GATE.allow(player, key, nowTicks, cooldownTicks);
    }

    /** Cadencia al azar por jugador y clave; ver {@link SoundGate#due}. */
    static boolean due(UUID player, String key, long nowTicks, long minTicks, long maxTicks, RandomGenerator random) {
        return GATE.due(player, key, nowTicks, minTicks, maxTicks, random);
    }

    static void forget(UUID player) {
        GATE.forget(player);
    }

    static void clear() {
        GATE.clear();
    }
}
