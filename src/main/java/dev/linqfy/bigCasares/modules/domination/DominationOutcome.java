package dev.linqfy.bigCasares.modules.domination;

/**
 * Resultado de una muerte entre jugadores.
 *
 * @param streak     muertes consecutivas del asesino sobre esta víctima, incluida esta
 * @param domination esta muerte inicia una dominación (llegó justo al umbral)
 * @param revenge    la víctima dominaba al asesino y este se vengó
 */
public record DominationOutcome(int streak, boolean domination, boolean revenge) {
}
