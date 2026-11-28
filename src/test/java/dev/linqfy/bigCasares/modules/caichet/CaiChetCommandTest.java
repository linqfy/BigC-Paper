package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void giveOffersTheCuadroItemAndItsChoices() {
        assertEquals(List.of("cuadro"), CaiChetCommand.complete(new String[] {"give", "cu"}));
        assertEquals(CaiChetCommand.PAINTING_CHOICES, CaiChetCommand.complete(new String[] {"give", "cuadro", ""}));
        assertEquals(List.of("gotico", "grito"), CaiChetCommand.complete(new String[] {"give", "cuadro", "g"}));
        assertTrue(CaiChetCommand.PAINTING_CHOICES.containsAll(DreamPaintings.ids()));
        assertTrue(CaiChetCommand.PAINTING_CHOICES.contains("azar"));
        assertEquals(List.of(), CaiChetCommand.complete(new String[] {"give", "cuadro", "grito", ""}));
    }

    @Test
    void cuadrosIsASubcommandWithoutArguments() {
        assertEquals(List.of("cuadros"), CaiChetCommand.complete(new String[] {"cu"}));
        assertEquals(List.of(), CaiChetCommand.complete(new String[] {"cuadros", ""}));
    }

    @Test
    void paintingChoiceTakesAnIdRandomOrNothing() {
        assertEquals("azar", CaiChetCommand.paintingChoice(new String[] {"give", "cuadro"}));
        assertEquals("azar", CaiChetCommand.paintingChoice(new String[] {"give", "cuadro", "azar"}));
        assertEquals("grito", CaiChetCommand.paintingChoice(new String[] {"give", "cuadro", "GRITO"}));
        assertEquals("azar", CaiChetCommand.paintingChoice(new String[] {"give", "cuadro", "3"}));
        assertNull(CaiChetCommand.paintingChoice(new String[] {"give", "cuadro", "kebab"}));
    }

    @Test
    void theAmountOfAPaintingGoesAfterTheIdOrInItsPlace() {
        assertEquals(3, CaiChetCommand.amountIndex(new String[] {"give", "cuadro"}));
        assertEquals(3, CaiChetCommand.amountIndex(new String[] {"give", "cuadro", "grito", "2"}));
        assertEquals(2, CaiChetCommand.amountIndex(new String[] {"give", "cuadro", "2"}));
    }

    @Test
    void entidadIsASubcommandWithTheFiveEntities() {
        assertEquals(List.of("entidad"), CaiChetCommand.complete(new String[] {"e"}));
        assertEquals(List.of("sonrisa", "666", "juancarlos", "arana", "payaso"), CaiChetCommand.ENTITIES);
        assertEquals(CaiChetCommand.ENTITIES, CaiChetCommand.complete(new String[] {"entidad", ""}));
        assertEquals(List.of("juancarlos"), CaiChetCommand.complete(new String[] {"entidad", "J"}));
        assertEquals(List.of("arana"), CaiChetCommand.complete(new String[] {"entidad", "ar"}));
        assertEquals(List.of(), CaiChetCommand.complete(new String[] {"entidad", "arana", ""}));
    }
}
