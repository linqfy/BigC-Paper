package dev.linqfy.bigCasares.modules.forex;

/** A refused market operation. The message is shown to the player as is. */
public final class ForexException extends RuntimeException {

    public ForexException(String message) {
        super(message);
    }
}
