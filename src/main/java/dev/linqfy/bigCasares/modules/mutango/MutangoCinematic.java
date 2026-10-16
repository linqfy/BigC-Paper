package dev.linqfy.bigCasares.modules.mutango;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Lo que sale en la pantalla, en orden y sin pisarse:
 * <ul>
 *   <li><b>versos</b> como en una pelicula: cada uno aparece letra por letra en el medio de la pantalla (italica, las
 *   palabras en mapudungun en dorado), se queda un rato y se desvanece; despues va el siguiente;</li>
 *   <li><b>carteles</b> (FASE 2, FASE 3, ¡Derrotaste a Neguechen!...): titulo grande y subtitulo, despues de los versos
 *   que esten en la fila.</li>
 * </ul>
 * Lo ven los jugadores a menos de 64 bloques de donde pasa. Se mueve con tick() (una vez por tick).
 */
final class MutangoCinematic {

    private static final double RADIUS = 64.0;
    private static final int CARD_TICKS = 70;

    private sealed interface Card permits Verse, Banner, Action {
    }

    /** Algo que pasa cuando la fila llega hasta aca (por ejemplo, despues de los ultimos versos). */
    private record Action(Runnable run) implements Card {
    }

    /** Un verso: start dice si arranca estrofa (suena el tono). */
    private record Verse(Location where, String text, boolean start) implements Card {
    }

    private record Banner(Location where, String title, String subtitle, Sound sound) implements Card {
    }

    /**
     * Como se muestra a los de Bedrock: isBedrock dice quien lo es; con actionBar el verso va por la barra de abajo y si
     * no, como titulo grande (que Bedrock achica solo para que entre).
     */
    record Display(Predicate<UUID> isBedrock, boolean actionBar) {

        /** Todos ven los versos como los de Java (sin Bedrock). */
        static final Display JAVA_ONLY = new Display(id -> false, false);

        /** Segun el valor de bedrock.texto-en-pantalla del config ("barra" = barra de abajo; cualquier otro, titulo). */
        static Display of(Predicate<UUID> isBedrock, String textMode) {
            return new Display(isBedrock, "barra".equalsIgnoreCase(textMode == null ? "" : textMode.trim()));
        }
    }

    private final Display display;
    /** Cada pelea tiene su propia fila de versos y carteles (las peleas no se pisan entre ellas). */
    private final Deque<Card> queue = new ArrayDeque<>();
    private Card current;
    private int ticks;

    MutangoCinematic(Display display) {
        this.display = display;
    }

    /** Una estrofa: sus versos, uno detras del otro. */
    void verses(Location where, String... lines) {
        boolean first = true;
        for (String line : lines) {
            // un verso largo se parte en pedazos que entran en la pantalla (los titulos de Java no se achican)
            for (String piece : MutangoPoem.fit(line, MutangoPoem.MAX_WIDTH)) {
                queue.add(new Verse(where.clone(), piece, first));
                first = false;
            }
        }
    }

    /** Un cartel grande (despues de lo que ya este en la fila). */
    void banner(Location where, String title, String subtitle, Sound sound) {
        queue.add(new Banner(where.clone(), title, subtitle, sound));
    }

    /** Corre run cuando termine todo lo que ya esta en la fila. */
    void then(Runnable run) {
        queue.add(new Action(run));
    }

    /** Ya no queda nada por mostrar ni en la fila. */
    boolean idle() {
        return current == null && queue.isEmpty();
    }

    void clear() {
        queue.clear();
        current = null;
    }

    void tick() {
        if (current == null) {
            current = queue.poll();
            ticks = 0;
            if (current == null) {
                return;
            }
        }
        ticks++;
        if (current instanceof Verse verse) {
            playVerse(verse);
        } else if (current instanceof Banner banner) {
            playBanner(banner);
        } else if (current instanceof Action action) {
            current = null;
            action.run().run();
        }
    }

    private void playVerse(Verse verse) {
        List<String> letters = MutangoPoem.letters(verse.text());
        int count = letters.size();
        if (ticks == 1 && verse.start()) {
            for (Player player : viewers(verse.where())) {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.6f);
            }
        }
        switch (MutangoVerseTiming.stage(count, ticks)) {
            // va apareciendo letra por letra (sin fundido, para que no titile)
            case TYPING -> show(verse.where(),
                String.join("", letters.subList(0, MutangoVerseTiming.shown(count, ticks))), 0, 40, 0);
            case COMPLETE -> show(verse.where(), String.join("", letters), 0, MutangoVerseTiming.HOLD_TICKS,
                MutangoVerseTiming.FADE_OUT_TICKS);
            case HOLDING -> {
                if (display.actionBar() && MutangoVerseTiming.resendOnActionBar(count, ticks)) {
                    // la barra de abajo dura poco: se vuelve a mandar mientras el verso se queda
                    show(verse.where(), String.join("", letters), 0, MutangoVerseTiming.HOLD_TICKS,
                        MutangoVerseTiming.FADE_OUT_TICKS);
                }
            }
            case FINISHED -> current = null;
        }
    }

    private void playBanner(Banner banner) {
        if (ticks == 1) {
            Title title = Title.title(text(banner.title()), text(banner.subtitle()), times(6, 50, 14));
            for (Player player : viewers(banner.where())) {
                player.showTitle(title);
                player.playSound(player.getLocation(), banner.sound(), 1.0f, 0.5f);
            }
        }
        if (ticks >= CARD_TICKS) {
            current = null;
        }
    }

    private void show(Location where, String line, int in, int stay, int out) {
        Component component = text(line);
        Title.Times times = times(in, stay, out);
        // el titulo de Java (subtitulo con el titulo vacio) solo se arma si hay alguien de Java mirando
        Title javaTitle = null;
        for (Player player : viewers(where)) {
            if (!display.isBedrock().test(player.getUniqueId())) {
                if (javaTitle == null) {
                    javaTitle = Title.title(Component.empty(), component, times);
                }
                player.showTitle(javaTitle);
            } else if (display.actionBar()) {
                player.sendActionBar(component);
            } else {
                // Bedrock: solo el titulo grande (sin subtitulo vacio, que Geyser dibuja como un cuadrado gris); Bedrock
                // lo achica solo si no entra en la pantalla
                player.sendTitlePart(TitlePart.TIMES, times);
                player.sendTitlePart(TitlePart.TITLE, component);
            }
        }
    }

    private static List<Player> viewers(Location where) {
        List<Player> out = new ArrayList<>();
        for (Player player : where.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(where) <= RADIUS * RADIUS) {
                out.add(player);
            }
        }
        return out;
    }

    private static Component text(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    private static Title.Times times(int in, int stay, int out) {
        return Title.Times.times(Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L),
            Duration.ofMillis(out * 50L));
    }

    // ------------------------------------------------------------ el poema (los versos estan en MutangoPoem)

    /** Al correr el cubo del santuario. */
    void awakening(Location where) {
        verses(where, MutangoPoem.AWAKENING.toArray(String[]::new));
    }

    /** Al terminar la fase 1. */
    void endOfPhaseOne(Location where) {
        verses(where, MutangoPoem.END_OF_PHASE_ONE.toArray(String[]::new));
    }

    /** Al terminar la fase 2. */
    void endOfPhaseTwo(Location where) {
        verses(where, MutangoPoem.END_OF_PHASE_TWO.toArray(String[]::new));
    }

    /** Al terminar la fase 3. */
    void endOfPhaseThree(Location where) {
        verses(where, MutangoPoem.END_OF_PHASE_THREE.toArray(String[]::new));
    }

    /** Lo ultimo que dice Neguechen, flotando, antes de irse. */
    void farewell(Location where) {
        verses(where, MutangoPoem.FAREWELL.toArray(String[]::new));
    }
}
