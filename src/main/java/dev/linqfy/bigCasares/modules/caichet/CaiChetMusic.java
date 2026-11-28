package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * La musica del hotel (backrooms) y del Sueño (dreamcore): canciones con efecto de VHS (cortes, trabas, cinta
 * gastada) que escuchan todos los que estan en esa dimension a la vez. El desierto no tiene musica. Va en la categoria
 * de discos para no pisarse con la musica del juego, que se corta en cada pedazo. Corre una vez por tick.
 */
final class CaiChetMusic implements Runnable, Listener {

    /** Las canciones del hotel y del Sueño: pedazos de cada una y lo que dura el ultimo. */
    static final List<DimensionMusic.Track> HOTEL_TRACKS = List.of(
        new DimensionMusic.Track("hotel_1", 4, 276),
        new DimensionMusic.Track("hotel_2", 15, 109),
        new DimensionMusic.Track("hotel_3", 14, 299));
    static final List<DimensionMusic.Track> DREAM_TRACKS = List.of(
        new DimensionMusic.Track("sueno_1", 13, 244),
        new DimensionMusic.Track("sueno_2", 5, 173),
        new DimensionMusic.Track("sueno_3", 5, 28));

    private final DimensionMusic hotel = new DimensionMusic(HOTEL_TRACKS);
    private final DimensionMusic dream = new DimensionMusic(DREAM_TRACKS);
    private long tick;

    /** Todos los sonidos de musica (para la lista de sonidos del modulo). */
    static List<String> events() {
        List<String> out = new ArrayList<>(new DimensionMusic(HOTEL_TRACKS).events());
        out.addAll(new DimensionMusic(DREAM_TRACKS).events());
        return out;
    }

    @Override
    public void run() {
        tick++;
        play(HotelWorld.get(), hotel);
        play(DreamWorld.get(), dream);
    }

    private void play(World world, DimensionMusic music) {
        List<Player> players = world == null ? List.of() : world.getPlayers();
        if (players.isEmpty()) {
            music.reset();
            return;
        }
        String event = music.tick(tick, ThreadLocalRandom.current());
        if (event == null) {
            return;
        }
        for (Player player : players) {
            player.stopSound(SoundCategory.MUSIC); // la musica del juego no se mezcla
            CaiChetSounds.follow(player, player, event, SoundCategory.RECORDS, 1f, 1f);
        }
    }

    /** Al irse del hotel o del Sueño, la cancion se corta para ese jugador. */
    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        World from = event.getFrom();
        if (HotelWorld.isHotel(from) || DreamWorld.isDream(from)) {
            event.getPlayer().stopSound(SoundCategory.RECORDS);
        }
    }

    /** Al apagar el modulo: se corta para todos los que estaban escuchando. */
    void stopAll() {
        for (World world : new World[] {HotelWorld.get(), DreamWorld.get()}) {
            if (world != null) {
                world.getPlayers().forEach(player -> player.stopSound(SoundCategory.RECORDS));
            }
        }
        hotel.reset();
        dream.reset();
    }
}
