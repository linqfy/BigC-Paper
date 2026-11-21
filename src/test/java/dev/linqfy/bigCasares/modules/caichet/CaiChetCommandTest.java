package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Autocompletado y cantidad de /caichet. */
class CaiChetCommandTest {

    @Test
    void noArgumentsOffersEverySubcommand() {
        assertEquals(CaiChetCommand.SUBCOMMANDS, CaiChetCommand.complete(new String[0]));
        assertEquals(CaiChetCommand.SUBCOMMANDS, CaiChetCommand.complete(new String[] {""}));
    }

    @Test
    void subcommandsFilterByPrefix() {
        assertEquals(List.of("give"), CaiChetCommand.complete(new String[] {"g"}));
        assertEquals(List.of("give"), CaiChetCommand.complete(new String[] {"G"}));
    }

    @Test
    void giveOffersItemsByPrefix() {
        assertEquals(List.of("reloj"), CaiChetCommand.complete(new String[] {"give", "re"}));
        assertEquals(CaiChetCommand.ITEMS, CaiChetCommand.complete(new String[] {"give", ""}));
    }

    @Test
    void trenOffersAqui() {
        assertEquals(List.of("aqui"), CaiChetCommand.complete(new String[] {"tren", ""}));
        assertEquals(List.of(), CaiChetCommand.complete(new String[] {"tren", "x"}));
    }

    @Test
    void pozoIsASubcommandWithoutArguments() {
        assertEquals(List.of("pozo"), CaiChetCommand.complete(new String[] {"p"}));
        assertEquals(List.of(), CaiChetCommand.complete(new String[] {"pozo", ""}));
    }

    @Test
    void otherPositionsOfferNothing() {
        assertEquals(List.of(), CaiChetCommand.complete(new String[] {"tp", "x"}));
        assertEquals(List.of(), CaiChetCommand.complete(new String[] {"give", "reloj", ""}));
    }

    @Test
    void amountIsClampedToOneAndTheStackSize() {
        assertEquals(1, CaiChetCommand.amount("0", 16));
        assertEquals(1, CaiChetCommand.amount("-7", 16));
        assertEquals(16, CaiChetCommand.amount("999", 16));
        assertEquals(5, CaiChetCommand.amount("5", 16));
        assertEquals(16, CaiChetCommand.amount("16", 16));
    }

    @Test
    void amountRejectsNonNumbers() {
        assertThrows(NumberFormatException.class, () -> CaiChetCommand.amount("abc", 16));
    }
}
