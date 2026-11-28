package dev.linqfy.bigCasares.modules.toffy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Collections;

/**
 * Spreads the players of a teleport over the places of a tournament: round robin, starting from a shuffled order of
 * the places so the first player does not always land on the first point. Pure.
 */
final class ToffyPlaces {
    private ToffyPlaces() {
    }

    /**
     * For each of {@code players} people (in order), the index of the place they go to.
     *
     * @throws IllegalArgumentException when there are no places
     */
    static List<Integer> assign(int places, int players, Random random) {
        if (places < 1) {
            throw new IllegalArgumentException("Hace falta al menos un lugar.");
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < places; index++) {
            order.add(index);
        }
        Collections.shuffle(order, random);
        List<Integer> result = new ArrayList<>(players);
        for (int player = 0; player < players; player++) {
            result.add(order.get(player % places));
        }
        return List.copyOf(result);
    }
}
