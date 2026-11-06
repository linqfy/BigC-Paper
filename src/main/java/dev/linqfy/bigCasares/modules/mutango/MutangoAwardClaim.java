package dev.linqfy.bigCasares.modules.mutango;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Entrega una adjudicacion a un jugador, en el orden que no pierde ni duplica nada (sin Bukkit):
 * <ol>
 *   <li>si el jugador ya tiene el recibo de esa adjudicacion, ya se aplico (un corte dejo la cola sin marcar): solo se
 *   marca como aplicada;</li>
 *   <li>si no entran los items, sigue pendiente y no se toca nada (no se deja un drop en el mundo);</li>
 *   <li>se aplican las victorias y los items, se anota el recibo y se guarda el jugador;</li>
 *   <li>se marca como <b>aplicada</b>, pero NO se borra de la cola: Player#saveData no avisa si fallo (Paper captura la
 *   excepcion de escritura y vuelve normal), asi que "guarde al jugador" no prueba nada. La prueba es el disco: al volver a
 *   entrar el jugador, si los datos que se leyeron traen el recibo se confirma (se borra de la cola) y si no, la
 *   adjudicacion vuelve a estar por aplicar (verifyAtJoin).</li>
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

    /** Que hacer con una adjudicacion aplicada cuando el jugador vuelve a entrar. */
    enum Verdict { CONFIRM, REAPPLY }

    /**
     * El jugador entro y sus datos vienen del disco: con el recibo, la adjudicacion quedo guardada de verdad (se confirma);
     * sin el, el guardado fallo en silencio o se perdio (vuelve a estar por aplicar).
     */
    static Verdict verifyAtJoin(MutangoPending.Award award, Receipts receipts) {
        return receipts.has(award.id()) ? Verdict.CONFIRM : Verdict.REAPPLY;
    }

    /**
     * Las victorias que cuentan para la dificultad de la proxima pelea: las ya aplicadas (en el jugador) mas las ganadas que
     * todavia no se aplicaron (por ejemplo sin lugar en el inventario). Las aplicadas esperando confirmacion ya estan en
     * {@code appliedWins}: no se suman otra vez.
     */
    static int difficultyWins(int appliedWins, java.util.Collection<MutangoPending.Award> notApplied) {
        int total = appliedWins;
        for (MutangoPending.Award award : notApplied) {
            total += award.wins();
        }
        return total;
    }

    /**
     * Recorta los recibos mas viejos hasta quedar en max, pero NUNCA uno que una adjudicacion sin confirmar todavia necesita
     * (verifyAtJoin lo busca: sin el recibo creeria que el guardado fallo y entregaria otra vez). Si todos hacen falta, no
     * se recorta de mas. {@code receipts} va del mas viejo al mas nuevo.
     */
    static java.util.Set<String> trimReceipts(java.util.Collection<String> receipts, java.util.Set<String> needed, int max) {
        java.util.Set<String> kept = new java.util.LinkedHashSet<>(receipts);
        int excess = kept.size() - max;
        for (java.util.Iterator<String> it = kept.iterator(); it.hasNext() && excess > 0; ) {
            if (!needed.contains(it.next())) {
                it.remove();
                excess--;
            }
        }
        return kept;
    }

    static Result claim(MutangoPending.Award award, Receipts receipts, Target target, Consumer<UUID> markApplied) {
        if (receipts.has(award.id())) {
            markApplied.accept(award.id());
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
        markApplied.accept(award.id());
        return Result.CLAIMED;
    }
}
