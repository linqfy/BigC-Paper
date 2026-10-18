package dev.linqfy.bigCasares.modules.finance;

public final class ExchangeException extends RuntimeException {

    public enum Code {
        INVALID_AMOUNT, INVALID_TRADE, SLIPPAGE, OVERFLOW, INSUFFICIENT_BALANCE,
        CONFLICT, NOT_FOUND, CLOSED, LOCKED, LIMIT, INVALID_REQUEST, STORAGE, UNBALANCED
    }

    private final Code code;

    public ExchangeException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ExchangeException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
