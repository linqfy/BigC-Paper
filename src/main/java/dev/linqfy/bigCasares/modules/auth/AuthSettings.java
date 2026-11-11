package dev.linqfy.bigCasares.modules.auth;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/** Values of the {@code auth} config section. */
public record AuthSettings(
    String limboWorld,
    int loginTimeoutSeconds,
    int registerTimeoutSeconds,
    int maxAttemptsPerJoin,
    int lockoutFailures,
    long lockoutWindowMillis,
    long lockoutMillis,
    int passwordMinLength,
    int pbkdf2Iterations,
    boolean sessionResume,
    long sessionResumeMillis,
    boolean bedrockAutoLogin,
    String totpIssuer,
    int totpWindow,
    boolean discordEnabled,
    boolean discordAutoPrompt,
    long discordPromptCooldownMillis,
    boolean pgpEnabled,
    boolean patternEnabled,
    int patternMinLength,
    boolean friendsEnabled,
    boolean pluginChannel,
    Premium premium
) {

    public record Premium(
        boolean enabled,
        String bindHost,
        int port,
        String publicHost,
        int publicPort,
        String returnHost,
        int returnPort,
        boolean bindAddress,
        boolean autoDetectNewPlayers,
        String sessionServer
    ) {
    }

    public static AuthSettings load(ConfigurationSection root) {
        ConfigurationSection section = root.getConfigurationSection("auth");
        if (section == null) {
            section = new MemoryConfiguration();
        }
        ConfigurationSection premium = section.getConfigurationSection("premium");
        if (premium == null) {
            premium = new MemoryConfiguration();
        }
        int port = clamp(premium.getInt("port", 25599), 1, 65535);
        return new AuthSettings(
            section.getString("limbo-world", "bigcasares_auth_limbo"),
            clamp(section.getInt("login-timeout-seconds", 120), 15, 3600),
            clamp(section.getInt("register-timeout-seconds", 300), 15, 3600),
            clamp(section.getInt("max-attempts", 5), 1, 50),
            clamp(section.getInt("ip-lockout.failures", 10), 1, 1000),
            clamp(section.getInt("ip-lockout.window-minutes", 10), 1, 1440) * 60_000L,
            clamp(section.getInt("ip-lockout.lock-minutes", 15), 1, 10_080) * 60_000L,
            clamp(section.getInt("password.min-length", 6), 4, 64),
            clamp(section.getInt("password.pbkdf2-iterations", 210_000), 10_000, 5_000_000),
            section.getBoolean("session-resume.enabled", true),
            clamp(section.getInt("session-resume.hours", 6), 1, 720) * 3_600_000L,
            section.getBoolean("bedrock-auto-login", true),
            section.getString("totp.issuer", "BigCasares"),
            clamp(section.getInt("totp.window", 1), 0, 3),
            section.getBoolean("discord.enabled", true),
            section.getBoolean("discord.auto-prompt", true),
            clamp(section.getInt("discord.prompt-cooldown-seconds", 60), 5, 3600) * 1000L,
            section.getBoolean("pgp.enabled", true),
            section.getBoolean("pattern.enabled", true),
            clamp(section.getInt("pattern.min-length", 4), 3, 16),
            section.getBoolean("friends.enabled", true),
            section.getBoolean("plugin-channel", true),
            new Premium(
                premium.getBoolean("enabled", false),
                premium.getString("bind-host", "0.0.0.0"),
                port,
                premium.getString("public-host", ""),
                premium.getInt("public-port", 0) <= 0 ? port : clamp(premium.getInt("public-port"), 1, 65535),
                premium.getString("return-host", ""),
                Math.max(0, Math.min(65535, premium.getInt("return-port", 0))),
                premium.getBoolean("bind-address", true),
                premium.getBoolean("auto-detect-new-players", true),
                premium.getString("session-server", "https://sessionserver.mojang.com")
            )
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
