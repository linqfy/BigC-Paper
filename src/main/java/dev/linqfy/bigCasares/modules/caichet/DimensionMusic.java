package dev.linqfy.bigCasares.modules.caichet;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * La musica de una dimension, la misma para todos los que estan ahi. Cada cancion va cortada en pedazos de
 * {@link #SEGMENT_TICKS} (un sonido por pedazo): el servidor los larga a todos a la vez y quien llega a mitad de una
 * cancion la empieza a escuchar en el proximo pedazo, sincronizado con los demas (Minecraft no deja arrancar un
 * sonido por la mitad). Entre cancion y cancion hay un silencio al azar. Sin Bukkit: el reloj lo pone quien llama.
 */
final class DimensionMusic {

    /** Lo que dura cada pedazo (15 s); el ultimo de cada cancion dura lo que le sobre. */
    static final int SEGMENT_TICKS = 20 * 15;
    static final int GAP_MIN_TICKS = 20 * 10;
    static final int GAP_MAX_TICKS = 20 * 40;
    /** Cuando entra el primero a una dimension vacia, la musica arranca enseguida (entre estos dos). */
    static final int FIRST_MIN_TICKS = 20 * 3;
    static final int FIRST_MAX_TICKS = 20 * 8;

    /**
     * Una cancion.
     *
     * @param name      el nombre de sus sonidos: {@code caichet:music.<name>.<n>}, n desde 1
     * @param segments  cuantos pedazos tiene
     * @param lastTicks lo que dura el ultimo pedazo
     */
    record Track(String name, int segments, int lastTicks) {

        String event(int segment) {
            return "caichet:music." + name + "." + segment;
        }

        int ticks(int segment) {
            return segment == segments ? lastTicks : SEGMENT_TICKS;
        }
    }

    private final List<Track> tracks;
    private int current = -1;
    private int last = -1;
    private int segment;
    private long nextAt = -1;

    DimensionMusic(List<Track> tracks) {
        this.tracks = List.copyOf(tracks);
    }

    List<Track> tracks() {
        return tracks;
    }

    /** Todos los sonidos de estas canciones. */
    List<String> events() {
        List<String> out = new ArrayList<>();
        for (Track track : tracks) {
            for (int segment = 1; segment <= track.segments(); segment++) {
                out.add(track.event(segment));
            }
        }
        return out;
    }

    /**
     * Un tick con gente en la dimension.
     *
     * @return el pedazo que hay que hacer sonar ahora para todos, o null
     */
    String tick(long now, RandomGenerator random) {
        if (nextAt < 0) {
            nextAt = now + between(FIRST_MIN_TICKS, FIRST_MAX_TICKS, random);
            return null;
        }
        if (now < nextAt) {
            return null;
        }
        if (current < 0) {
            current = pick(random);
            segment = 1;
        } else if (segment < tracks.get(current).segments()) {
            segment++;
        } else {
            last = current;
            current = -1;
            nextAt = now + between(GAP_MIN_TICKS, GAP_MAX_TICKS, random);
            return null;
        }
        Track track = tracks.get(current);
        nextAt = now + track.ticks(segment);
        return track.event(segment);
    }

    /** Se fueron todos: la musica se corta y vuelve a arrancar cuando llegue alguien. */
    void reset() {
        if (current >= 0) {
            last = current;
        }
        current = -1;
        nextAt = -1;
    }

    /** La cancion que suena (null en silencio). */
    Track playing() {
        return current < 0 ? null : tracks.get(current);
    }

    /** Una al azar que no sea la que acaba de sonar (si hay otra). */
    private int pick(RandomGenerator random) {
        if (tracks.size() == 1) {
            return 0;
        }
        int choice = random.nextInt(tracks.size() - (last >= 0 ? 1 : 0));
        return last >= 0 && choice >= last ? choice + 1 : choice;
    }

    private static long between(int min, int max, RandomGenerator random) {
        return min + random.nextInt(max - min + 1);
    }
}
