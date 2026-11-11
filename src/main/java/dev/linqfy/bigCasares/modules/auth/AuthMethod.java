package dev.linqfy.bigCasares.modules.auth;

/** Ways to prove ownership of an offline-mode name. */
public enum AuthMethod {
    PASSWORD("contraseña", false),
    PATTERN("patrón", false),
    TOTP("app autenticadora", false),
    RECOVERY_CODE("código de recuperación", false),
    DISCORD("Discord", false),
    PGP("firma PGP", false),
    FRIEND("amigo de confianza", false),
    PREMIUM("cuenta premium", true),
    BEDROCK("cuenta Xbox (Bedrock)", true),
    SESSION("sesión recordada", true),
    ADMIN("admin", true);

    private final String displayName;
    private final boolean automatic;

    AuthMethod(String displayName, boolean automatic) {
        this.displayName = displayName;
        this.automatic = automatic;
    }

    public String displayName() {
        return displayName;
    }

    /** Automatic methods (verified by Mojang, Xbox, a remembered session or an admin) always suffice alone. */
    public boolean automatic() {
        return automatic;
    }
}
