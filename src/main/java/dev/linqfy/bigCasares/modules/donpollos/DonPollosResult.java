package dev.linqfy.bigCasares.modules.donpollos;

public record DonPollosResult(boolean success, String message) {

    public static DonPollosResult success(String message) {
        return new DonPollosResult(true, message);
    }

    public static DonPollosResult failure(String message) {
        return new DonPollosResult(false, message);
    }
}
