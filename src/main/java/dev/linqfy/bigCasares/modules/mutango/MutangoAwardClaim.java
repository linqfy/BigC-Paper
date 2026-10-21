package dev.linqfy.bigCasares.modules.mutango;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Entrega una adjudicacion a un jugador, en el orden que no pierde ni duplica nada (sin Bukkit):
 * <ol>
 *   <li>si el jugador ya tiene el recibo de esa adjudicacion, ya se aplico (la cola quedo sin borrar por un corte): solo
 *   se confirma;</li>
 *   <li>si no entran los items, sigue pendiente y no se toca nada (no se deja un drop en el mundo);</li>
 *   <li>se aplican las victorias y los items, se anota el recibo y se guarda el jugador;</li>
 *   <li>recien ahi se confirma (se borra de la cola).</li>
 * </ol>
 * Si cualquier paso falla a mitad, se <b>deshace todo lo aplicado en memoria</b> (victorias, items y recibo) antes de
 * relanzar el error: un reintento en la misma sesion (sin reinicio) no puede sumar dos veces lo que la primera vuelta ya
 * habia puesto en el jugador sin guardar.
 * Las victorias, los items y el recibo viven en los datos del mismo jugador y se guardan juntos, asi que un corte antes de
 * guardar deja la adjudicacion sin aplicar (se reintenta) y un corte despues la deja con recibo (no se repite).
 */
final class MutangoAwardClaim {

    enum Result { CLAIMED, ALREADY_APPLIED, NO_ROOM }

    /** Los recibos que lleva el propio jugador. */
    interface Receipts {
        boolean has(UUID award);

        void add(UUID award);

        /** Deshace add (la entrega fallo antes de guardar al jugador). */
        void remove(UUID award);
    }

    /** El jugador que recibe. */
    interface Target {
        /** Le entran los premios en el inventario. */
        boolean hasRoom(int rewards);

        void addWins(int wins);

        void giveRewards(int rewards);

        /** Guarda al jugador (victorias, items y recibo juntos). */
        void persist();

        /** Empieza una entrega: recuerda como estaba el jugador (victorias e inventario) para poder deshacerla. */
        void begin();

        /** La entrega fallo: el jugador vuelve a como estaba en begin (lo que no se llego a guardar no cuenta). */
        void rollback();
    }

    private MutangoAwardClaim() {
    }

    static Result claim(MutangoPending.Award award, Receipts receipts, Target target, Consumer<UUID> confirm) {
        if (receipts.has(award.id())) {
            confirm.accept(award.id());
            return Result.ALREADY_APPLIED;
        }
        if (award.rewards() > 0 && !target.hasRoom(award.rewards())) {
            return Result.NO_ROOM;
        }
        target.begin();
        try {
            if (award.wins() > 0) {
                target.addWins(award.wins());
            }
            if (award.rewards() > 0) {
                target.giveRewards(award.rewards());
            }
            receipts.add(award.id());
            target.persist();
        } catch (RuntimeException failure) {
            receipts.remove(award.id());
            target.rollback();
            throw failure;
        }
        confirm.accept(award.id());
        return Result.CLAIMED;
    }
}
