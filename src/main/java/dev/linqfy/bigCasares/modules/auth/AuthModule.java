package dev.linqfy.bigCasares.modules.auth;

import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.auth.premium.MojangProfiles;
import dev.linqfy.bigCasares.modules.auth.premium.MojangSessionClient;
import dev.linqfy.bigCasares.modules.auth.premium.PremiumTicket;
import dev.linqfy.bigCasares.modules.auth.premium.PremiumVerifierServer;
import dev.linqfy.bigCasares.modules.discord.DiscordGateway;
import dev.linqfy.bigCasares.modules.discord.DiscordIntegrationModule;
import dev.linqfy.bigCasares.modules.servercontrol.VanishSessionRegistry;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Login system for an offline-mode server. Until a player proves ownership of their name they wait in an empty
 * void world with their inventory stashed on disk, so they see no items, coordinates, terrain or players.
 *
 * <p>Automatic logins: premium Java accounts (verified against Mojang by the built-in verifier), Bedrock players
 * authenticated by Xbox through Geyser, and a remembered session from the same address. Manual methods: password,
 * authenticator app (QR on a map), one-time recovery codes, Discord DM approval or code, OpenPGP challenge
 * signatures (chat or a client mod over {@code bigcasares:auth}), a chest pattern lock and approval by a trusted
 * friend. Any account may require two different manual methods.
 */
public final class AuthModule implements PluginModule {

    static final String PREFIX = "§8[§dLogin§8] §7";
    static final String CHANNEL = "bigcasares:auth";
    private static final long PREAUTH_MILLIS = 60_000L;
    private static final long CHALLENGE_MILLIS = 5 * 60_000L;
    private static final long REQUEST_MILLIS = 2 * 60_000L;
    private static final Set<String> RESERVED_PASSWORDS = Set.of(
        "discord", "pgp", "patron", "pattern", "amigo", "friend", "codigo", "code");

    private final JavaPlugin plugin;
    private final Function<UUID, ClientPlatform> platforms;
    private final Supplier<DiscordIntegrationModule> discord;
    private final VanishSessionRegistry vanish;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, PlayerAuthState> states = new ConcurrentHashMap<>();
    private final Map<UUID, PreAuth> preAuthorized = new ConcurrentHashMap<>();
    private final Map<UUID, Location> originalSpawns = new ConcurrentHashMap<>();
    private final Set<String> authenticatedNames = ConcurrentHashMap.newKeySet();
    private final Map<String, UUID> discordRequests = new ConcurrentHashMap<>();
    private final Map<String, Long> lastDiscordPromptByName = new ConcurrentHashMap<>();

    private AuthSettings settings;
    private AuthAccountStore accounts;
    private InventoryStash stash;
    private AuthSecret secret;
    private PasswordHasher hasher;
    private LoginAttemptLimiter limiter;
    private PremiumGate premiumGate;
    private MojangProfiles mojangProfiles;
    private PremiumVerifierServer verifier;
    private World limbo;
    private Location limboSpawn;
    private NamespacedKey qrKey;
    private NamespacedKey premiumCookieKey;
    private RuntimeRegistrationScope scope;
    private RuntimeRegistrationScope compatibilityScope;
    private boolean onlineModeServer;

    public AuthModule(JavaPlugin plugin, Function<UUID, ClientPlatform> platforms,
                      Supplier<DiscordIntegrationModule> discord, VanishSessionRegistry vanish) {
        this.plugin = plugin;
        this.platforms = platforms;
        this.discord = discord;
        this.vanish = vanish;
    }

    @Override
    public String getId() {
        return "auth-system";
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope owned = new RuntimeRegistrationScope();
        this.compatibilityScope = owned;
        onEnable(owned);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        this.scope = scope;
        this.settings = AuthSettings.load(plugin.getConfig());
        Path data = plugin.getDataFolder().toPath().resolve("data").resolve("auth");
        try {
            this.secret = AuthSecret.loadOrCreate(data.resolve("secret.key"), random);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not prepare data/auth/secret.key", ex);
        }
        this.accounts = new AuthAccountStore(data.resolve("accounts"));
        this.stash = new InventoryStash(data.resolve("stash"));
        this.hasher = new PasswordHasher(settings.pbkdf2Iterations(), random);
        this.limiter = new LoginAttemptLimiter(settings.lockoutFailures(), settings.lockoutWindowMillis(),
            settings.lockoutMillis());
        this.premiumGate = new PremiumGate(secret, accounts::find, System::currentTimeMillis,
            settings.premium().bindAddress());
        this.mojangProfiles = new MojangProfiles("https://api.mojang.com");
        this.qrKey = new NamespacedKey(plugin, "auth_qr_map");
        this.premiumCookieKey = NamespacedKey.fromString(PremiumVerifierServer.COOKIE_KEY);
        this.onlineModeServer = Bukkit.getOnlineMode();
        this.limbo = LimboWorld.createOrLoad(settings.limboWorld());
        this.limboSpawn = LimboWorld.spawn(limbo);
        scope.register("auth-state", this::clearState);

        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.registerListener("auth-listener", new AuthListener(this));
        AuthCommands commands = new AuthCommands(this);
        for (String label : List.of("login", "register", "auth", "premium")) {
            var command = plugin.getCommand(label);
            if (command != null) {
                registrations.bindCommand("auth-command-" + label, command, commands, commands);
            }
        }
        if (settings.pluginChannel()) {
            AuthPluginChannel channel = new AuthPluginChannel(this);
            var messenger = plugin.getServer().getMessenger();
            messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
            messenger.registerIncomingPluginChannel(plugin, CHANNEL, channel);
            registrations.ownCleanup("auth-plugin-channel", () -> {
                messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, channel);
                messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
            });
        }
        registrations.scheduleRepeating("auth-ticker", this::tick, 20L, 20L);
        startPremiumVerifier(registrations);
        DiscordGateway gateway = discordGateway();
        if (gateway != null) {
            gateway.setLoginApprovalHandler(this::onDiscordDecision);
            registrations.ownCleanup("auth-discord-handler", () -> gateway.setLoginApprovalHandler(null));
        }
        // A reload keeps everyone who is already playing logged in; pending players were released on disable.
        for (Player online : Bukkit.getOnlinePlayers()) {
            PlayerAuthState state = newState(online.getUniqueId(), online.getName(), addressOf(online),
                accounts.find(online.getName()).orElse(null));
            state.authenticated = true;
            states.put(online.getUniqueId(), state);
            authenticatedNames.add(key(online.getName()));
        }
        if (onlineModeServer) {
            plugin.getLogger().info("auth-system: el servidor está en online-mode; todos entran automáticamente.");
        }
    }

    @Override
    public void onDisable() {
        for (PlayerAuthState state : List.copyOf(states.values())) {
            Player player = Bukkit.getPlayer(state.playerId);
            if (player != null && !state.authenticated) {
                try {
                    releaseFromLimbo(player, state);
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Could not restore " + player.getName(), ex);
                }
                player.kick(Component.text("El sistema de login se reinició. Volvé a entrar.", NamedTextColor.YELLOW));
            }
        }
        // The module manager closes the scope it handed to onEnable(scope); only a self-created one is closed here.
        RuntimeRegistrationScope owned = compatibilityScope;
        compatibilityScope = null;
        if (owned != null) {
            owned.close();
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Connection hooks (called by AuthListener)

    /** Async: rejects locked addresses, name-case impostors and duplicate logins before anything loads. */
    String preLoginRejection(String name, InetAddress address) {
        long lock = limiter.lockRemainingMillis(secret.addressKey(address), System.currentTimeMillis());
        if (lock > 0) {
            return "Demasiados intentos fallidos. Esperá " + Math.max(1, TimeUnit.MILLISECONDS.toMinutes(lock) + 1)
                + " minutos.";
        }
        Optional<AuthAccount> account = accounts.find(name);
        if (account.isPresent() && !account.get().name().equals(name)) {
            return "Esta cuenta está registrada como \"" + account.get().name()
                + "\". Entrá con ese nombre exacto (mayúsculas incluidas).";
        }
        if (authenticatedNames.contains(key(name))) {
            return "Esa cuenta ya está conectada.";
        }
        return null;
    }

    /** Async, during configuration: decides automatic logins and bounces premium accounts to the verifier. */
    void configure(PlayerConfigurationConnection connection) {
        var profile = connection.getProfile();
        UUID playerId = profile.getId();
        String name = profile.getName();
        if (playerId == null || name == null || states.containsKey(playerId)) {
            return;
        }
        InetAddress address = connection.getClientAddress().getAddress();
        if (onlineModeServer) {
            preAuthorize(playerId, AuthMethod.PREMIUM, null, false);
            return;
        }
        if (settings.bedrockAutoLogin() && platforms.apply(playerId) == ClientPlatform.BEDROCK) {
            preAuthorize(playerId, AuthMethod.BEDROCK, null, false);
            return;
        }
        AuthAccount account = accounts.find(name).orElse(null);
        if (premiumActive()) {
            boolean newPremium = account == null && premiumGate.newPlayerPending(name);
            if (premiumGate.wantsVerification(account) || newPremium) {
                byte[] cookie = connection.isTransferred() ? readCookie(connection) : null;
                Optional<PremiumTicket> ticket = premiumGate.redeem(name, address, cookie);
                if (ticket.isPresent()) {
                    preAuthorize(playerId, AuthMethod.PREMIUM, ticket.get().premiumId(),
                        account == null || !account.premium());
                    return;
                }
                if (premiumGate.markBounce(name) && bounce(connection)) {
                    return;
                }
            } else if (account == null && settings.premium().autoDetectNewPlayers()
                && mojangProfiles.exists(name) && premiumGate.markBounce(name)) {
                premiumGate.markNewPlayer(name);
                if (bounce(connection)) {
                    return;
                }
            }
        }
        if (account != null && settings.sessionResume() && account.sessionResume()
            && !account.requireTwoFactors()
            && secret.addressKey(address).equals(account.lastAddressHash())
            && System.currentTimeMillis() - account.lastLoginAtMillis() <= settings.sessionResumeMillis()) {
            preAuthorize(playerId, AuthMethod.SESSION, null, false);
        }
    }

    /** Async: unauthenticated players spawn in limbo instead of their real location. */
    Location spawnLocation(UUID playerId, Location original) {
        PreAuth pre = preAuthorized.get(playerId);
        if (pre != null && pre.expiresAt() >= System.currentTimeMillis()) {
            return original;
        }
        if (original != null && original.getWorld() != null && !original.getWorld().equals(limbo)) {
            originalSpawns.put(playerId, original);
        }
        return limboSpawn.clone();
    }

    /** Main thread, lowest priority join. */
    void join(Player player, Component joinMessage, java.util.function.Consumer<Component> setJoinMessage) {
        UUID playerId = player.getUniqueId();
        AuthAccount account = accounts.find(player.getName()).orElse(null);
        PlayerAuthState state = newState(playerId, player.getName(), addressOf(player), account);
        states.put(playerId, state);
        for (PlayerAuthState other : states.values()) {
            Player otherPlayer = Bukkit.getPlayer(other.playerId);
            if (!other.authenticated && otherPlayer != null && otherPlayer != player) {
                player.unlistPlayer(otherPlayer);
                otherPlayer.unlistPlayer(player);
            }
        }
        PreAuth pre = preAuthorized.remove(playerId);
        Location original = originalSpawns.remove(playerId);
        state.returnLocation = original;
        // A crash while waiting in limbo leaves a stash and a limbo position behind; automatic logins release it.
        boolean leftover = stash.exists(playerId) || player.getWorld().equals(limbo);
        if (pre != null && pre.expiresAt() >= System.currentTimeMillis()) {
            if (pre.method() == AuthMethod.PREMIUM && !onlineModeServer) {
                if (state.account == null) {
                    state.account = new AuthAccount(player.getName(), System.currentTimeMillis());
                    accounts.put(state.account);
                }
                if (pre.enablePremium() || state.account.premiumId() == null) {
                    state.account.premium(true, pre.premiumId());
                    premiumGate.endOptIn(player.getName());
                    accounts.save(state.account);
                }
                if (pre.enablePremium()) {
                    player.sendMessage(PREFIX + "§a¡Modo premium activado! §7Desde ahora entrás sin escribir nada.");
                }
            }
            completeLogin(player, state, pre.method(), leftover);
            return;
        }
        if (settings.bedrockAutoLogin() && platforms.apply(playerId) == ClientPlatform.BEDROCK) {
            completeLogin(player, state, AuthMethod.BEDROCK, leftover);
            return;
        }
        state.deferredJoinMessage = joinMessage;
        setJoinMessage.accept(null);
        enterLimbo(player, state, original);
        greet(player, state);
    }

    void quit(Player player) {
        PlayerAuthState state = states.remove(player.getUniqueId());
        if (state == null) {
            return;
        }
        removeQrMaps(player);
        if (state.discordRequestId != null) {
            discordRequests.remove(state.discordRequestId);
        }
        if (state.authenticated) {
            authenticatedNames.remove(key(state.name));
            return;
        }
        releaseFromLimbo(player, state);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Login methods (called by commands, menus, channels)

    void register(Player player, String password, String confirmation) {
        PlayerAuthState state = pending(player);
        if (state == null || state.busy) {
            return;
        }
        if (state.registered()) {
            player.sendMessage(PREFIX + "§cYa estás registrado. Usá §f/login <contraseña>§c.");
            return;
        }
        if (!password.equals(confirmation)) {
            player.sendMessage(PREFIX + "§cLas contraseñas no coinciden.");
            return;
        }
        String rejection = passwordRejection(player.getName(), password);
        if (rejection != null) {
            player.sendMessage(PREFIX + "§c" + rejection);
            return;
        }
        state.busy = true;
        async(() -> {
            PasswordHasher.HashedSecret hash = hasher.hash(password);
            sync(player, current -> {
                current.busy = false;
                if (current.registered()) {
                    return;
                }
                AuthAccount account = new AuthAccount(player.getName(), System.currentTimeMillis());
                account.password(hash);
                accounts.put(account);
                current.account = account;
                current.progress = new LoginProgress(false);
                player.sendMessage(PREFIX + "§a¡Cuenta registrada!");
                completeLogin(player, current, AuthMethod.PASSWORD, true);
                player.sendMessage(Component.text("» Protegé tu cuenta con ", NamedTextColor.GRAY)
                    .append(command("/auth", "/auth", "Ver métodos: app autenticadora, Discord, PGP, patrón...")));
            });
        });
    }

    /** {@code /login <secret>}: Discord code, authenticator code, recovery code or password, in that order. */
    void loginWithSecret(Player player, String input) {
        PlayerAuthState state = pending(player);
        if (state == null || state.busy) {
            return;
        }
        if (!state.registered()) {
            player.sendMessage(PREFIX + "§cNo estás registrado. Usá §f/register <contraseña> <contraseña>§c.");
            return;
        }
        AuthAccount account = state.account;
        long now = System.currentTimeMillis();
        if (state.discordCode != null && now <= state.discordExpiresAt
            && java.security.MessageDigest.isEqual(state.discordCode.getBytes(StandardCharsets.US_ASCII),
                input.getBytes(StandardCharsets.US_ASCII))) {
            succeed(player, state, AuthMethod.DISCORD);
            return;
        }
        if (account.totpSecret() != null && !state.progress.satisfied(AuthMethod.TOTP)) {
            var step = Totp.verify(account.totpSecret(), input, now / 1000L, settings.totpWindow(),
                account.totpLastStep());
            if (step.isPresent()) {
                account.totpLastStep(step.getAsLong());
                accounts.save(account);
                succeed(player, state, AuthMethod.TOTP);
                return;
            }
        }
        if (RecoveryCodes.looksLikeCode(input) && account.consumeRecoveryDigest(RecoveryCodes.digest(input))) {
            accounts.save(account);
            player.sendMessage(PREFIX + "§eUsaste un código de recuperación. Te quedan §f"
                + account.recoveryDigests().size() + "§e.");
            succeed(player, state, AuthMethod.RECOVERY_CODE);
            return;
        }
        if (account.password() == null || state.progress.satisfied(AuthMethod.PASSWORD)) {
            fail(player, state, "Código incorrecto o vencido.");
            return;
        }
        state.busy = true;
        PasswordHasher.HashedSecret stored = account.password();
        async(() -> {
            boolean valid = PasswordHasher.verify(input, stored);
            sync(player, current -> {
                current.busy = false;
                if (valid) {
                    succeed(player, current, AuthMethod.PASSWORD);
                } else {
                    fail(player, current, "Contraseña o código incorrecto.");
                }
            });
        });
    }

    void openPatternLogin(Player player) {
        PlayerAuthState state = pending(player);
        if (state == null || !requireRegistered(player, state)) {
            return;
        }
        if (!settings.patternEnabled() || state.account.pattern() == null) {
            player.sendMessage(PREFIX + "§cTu cuenta no tiene patrón. Configuralo después con §f/auth patron§c.");
            return;
        }
        player.openInventory(new PatternMenu("Desbloqueá con tu patrón", sequence -> {
            player.closeInventory();
            PlayerAuthState current = pending(player);
            if (current == null || current.busy) {
                return;
            }
            current.busy = true;
            PasswordHasher.HashedSecret stored = current.account.pattern();
            async(() -> {
                boolean valid = PasswordHasher.verify(PatternMenu.secretOf(sequence), stored);
                sync(player, latest -> {
                    latest.busy = false;
                    if (valid) {
                        succeed(player, latest, AuthMethod.PATTERN);
                    } else {
                        fail(player, latest, "Patrón incorrecto.");
                    }
                });
            });
        }).getInventory());
    }

    void startDiscordLogin(Player player, boolean manual) {
        PlayerAuthState state = pending(player);
        if (state == null || !requireRegistered(player, state)) {
            return;
        }
        Optional<Long> discordId = linkedDiscord(state);
        DiscordGateway gateway = discordGateway();
        if (discordId.isEmpty() || gateway == null) {
            if (manual) {
                player.sendMessage(PREFIX + "§cTu cuenta no tiene Discord vinculado o el bot no está disponible.");
            }
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = manual ? 15_000L : settings.discordPromptCooldownMillis();
        Long last = lastDiscordPromptByName.get(key(state.name));
        if (last != null && now - last < cooldown) {
            if (manual) {
                player.sendMessage(PREFIX + "§eYa te mandamos un mensaje por Discord. Esperá unos segundos.");
            }
            return;
        }
        lastDiscordPromptByName.put(key(state.name), now);
        if (state.discordRequestId != null) {
            discordRequests.remove(state.discordRequestId);
        }
        state.discordRequestId = HexFormat.of().formatHex(randomBytes(8));
        state.discordCode = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        state.discordExpiresAt = now + REQUEST_MILLIS;
        discordRequests.put(state.discordRequestId, state.playerId);
        String message = "🔐 **Intento de ingreso a BigCasares**\n"
            + "Cuenta: `" + state.name + "`\n"
            + "Desde: `" + maskAddress(state.address) + "`\n"
            + "Código: **" + state.discordCode + "** (escribilo con `/login " + state.discordCode + "`)\n"
            + "O tocá un botón. Vence <t:" + (state.discordExpiresAt / 1000L) + ":R>.";
        if (gateway.sendLoginApproval(discordId.get(), state.discordRequestId, message)) {
            player.sendMessage(PREFIX + "§9Te mandamos un mensaje privado en Discord: §faprobá el ingreso §9o escribí "
                + "§f/login <código>§9.");
        } else if (manual) {
            player.sendMessage(PREFIX + "§cEl bot de Discord no está conectado ahora.");
        }
    }

    void startPgp(Player player) {
        PlayerAuthState state = pending(player);
        if (state == null) {
            return;
        }
        if (!settings.pgpEnabled()) {
            player.sendMessage(PREFIX + "§cEl inicio con PGP está desactivado.");
            return;
        }
        if (state.pendingRegistrationKey == null && (!state.registered() || state.account.pgpKeyRing() == null)) {
            player.sendMessage(PREFIX + "§cTu cuenta no tiene una clave PGP. Registrala con §f/register pgp <clave>§c "
                + "o, ya dentro, con §f/auth pgp§c.");
            return;
        }
        String challenge = challengeFor(state);
        String command = "printf '%s' '" + challenge + "' | gpg --detach-sign | base64 -w0";
        player.sendMessage(Component.text("Firmá este desafío con tu clave PGP:", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("  " + challenge, NamedTextColor.WHITE)
            .clickEvent(ClickEvent.copyToClipboard(challenge))
            .hoverEvent(HoverEvent.showText(Component.text("Copiar desafío"))));
        player.sendMessage(Component.text("  [Copiar comando de gpg]", NamedTextColor.AQUA, TextDecoration.BOLD)
            .clickEvent(ClickEvent.copyToClipboard(command))
            .hoverEvent(HoverEvent.showText(Component.text(command))));
        player.sendMessage(Component.text("Después pegá la salida con ", NamedTextColor.GRAY)
            .append(Component.text("/login pgp <firma>", NamedTextColor.WHITE)
                .clickEvent(ClickEvent.suggestCommand("/login pgp ")))
            .append(Component.text(". Si no entra en el chat, mandala en partes terminadas en ", NamedTextColor.GRAY))
            .append(Component.text("+", NamedTextColor.WHITE))
            .append(Component.text(".", NamedTextColor.GRAY)));
    }

    /** Chat submission of a (possibly chunked) detached signature. */
    void submitPgpChunk(Player player, String chunk) {
        PlayerAuthState state = pending(player);
        if (state == null || state.busy) {
            return;
        }
        if (chunk.endsWith("+")) {
            if (state.pgpBuffer.length() + chunk.length() > OpenPgp.MAX_SIGNATURE_BYTES * 2) {
                state.pgpBuffer.setLength(0);
                player.sendMessage(PREFIX + "§cLa firma es demasiado grande.");
                return;
            }
            state.pgpBuffer.append(chunk, 0, chunk.length() - 1);
            player.sendMessage(PREFIX + "§7Parte recibida (§f" + state.pgpBuffer.length() + "§7 caracteres). "
                + "Mandá la siguiente; la última sin §f+§7.");
            return;
        }
        state.pgpBuffer.append(chunk);
        String text = state.pgpBuffer.toString();
        state.pgpBuffer.setLength(0);
        try {
            verifyPgp(player, state, OpenPgp.decodeSignature(text));
        } catch (OpenPgp.PgpException ex) {
            fail(player, state, ex.getMessage());
        }
    }

    /** Verifies a binary signature from chat or the client-mod channel. */
    void verifyPgp(Player player, PlayerAuthState state, byte[] signature) {
        if (state.pgpChallenge == null || System.currentTimeMillis() > state.pgpChallengeExpiresAt) {
            player.sendMessage(PREFIX + "§cEl desafío venció. Pedí uno nuevo con §f/login pgp§c.");
            return;
        }
        if (state.pendingRegistrationKey == null && (!state.registered() || state.account.pgpKeyRing() == null)) {
            player.sendMessage(PREFIX + "§cTu cuenta no tiene una clave PGP.");
            return;
        }
        OpenPgp.KeyRing ring;
        try {
            ring = state.pendingRegistrationKey != null
                ? state.pendingRegistrationKey
                : OpenPgp.parseKeyRing(state.account.pgpKeyRing());
            String challenge = state.pgpChallenge;
            List<byte[]> candidates = List.of(
                challenge.getBytes(StandardCharsets.UTF_8),
                (challenge + "\n").getBytes(StandardCharsets.UTF_8),
                (challenge + "\r\n").getBytes(StandardCharsets.UTF_8));
            Optional<String> signer = OpenPgp.verifyDetached(ring, signature, candidates);
            if (signer.isEmpty()) {
                fail(player, state, "La firma no corresponde al desafío.");
                return;
            }
        } catch (OpenPgp.PgpException ex) {
            fail(player, state, ex.getMessage());
            return;
        }
        state.pgpChallenge = null;
        if (state.pendingRegistrationKey != null && !state.registered()) {
            AuthAccount account = new AuthAccount(player.getName(), System.currentTimeMillis());
            account.pgpKey(ring.encoded(), ring.primaryFingerprint());
            accounts.put(account);
            state.account = account;
            state.progress = new LoginProgress(false);
            state.pendingRegistrationKey = null;
            player.sendMessage(PREFIX + "§a¡Cuenta registrada con tu clave PGP §f" + ring.shortId() + "§a!");
        }
        succeed(player, state, AuthMethod.PGP);
    }

    /** {@code /register pgp <key chunk>[+]}: collects a public key for a new account, then asks for a signature. */
    void registerPgpChunk(Player player, String chunk) {
        PlayerAuthState state = pending(player);
        if (state == null) {
            return;
        }
        if (state.registered()) {
            player.sendMessage(PREFIX + "§cYa estás registrado.");
            return;
        }
        Optional<OpenPgp.KeyRing> ring = collectKey(player, state, chunk);
        if (ring.isEmpty()) {
            return;
        }
        state.pendingRegistrationKey = ring.get();
        state.pgpChallenge = null;
        player.sendMessage(PREFIX + "§aClave recibida: §f" + OpenPgp.describe(ring.get()) + "§a. Ahora probá que es "
            + "tuya:");
        startPgp(player);
    }

    /** Shared chunked key reader for registration and {@code /auth pgp}. */
    Optional<OpenPgp.KeyRing> collectKey(Player player, PlayerAuthState state, String chunk) {
        if (chunk.endsWith("+")) {
            if (state.keyBuffer.length() + chunk.length() > OpenPgp.MAX_KEY_RING_BYTES * 2) {
                state.keyBuffer.setLength(0);
                player.sendMessage(PREFIX + "§cLa clave es demasiado grande.");
                return Optional.empty();
            }
            state.keyBuffer.append(chunk, 0, chunk.length() - 1);
            player.sendMessage(PREFIX + "§7Parte de la clave recibida (§f" + state.keyBuffer.length()
                + "§7 caracteres). Mandá la siguiente; la última sin §f+§7.");
            return Optional.empty();
        }
        state.keyBuffer.append(chunk);
        String text = state.keyBuffer.toString();
        state.keyBuffer.setLength(0);
        try {
            return Optional.of(OpenPgp.parseKeyRing(text));
        } catch (OpenPgp.PgpException ex) {
            player.sendMessage(PREFIX + "§c" + ex.getMessage());
            return Optional.empty();
        }
    }

    void requestFriend(Player player, String friendName) {
        PlayerAuthState state = pending(player);
        if (state == null || !requireRegistered(player, state)) {
            return;
        }
        if (!settings.friendsEnabled()) {
            player.sendMessage(PREFIX + "§cLa aprobación por amigos está desactivada.");
            return;
        }
        Player friend = Bukkit.getPlayerExact(friendName);
        PlayerAuthState friendState = friend == null ? null : states.get(friend.getUniqueId());
        // Same answer either way, so the command does not reveal who is trusted.
        player.sendMessage(PREFIX + "§7Si §f" + friendName + "§7 es de confianza y está conectado, recibió tu pedido.");
        if (friendState == null || !friendState.authenticated || !state.account.trusts(friend.getName())) {
            return;
        }
        state.friendApprover = key(friend.getName());
        state.friendExpiresAt = System.currentTimeMillis() + REQUEST_MILLIS;
        friend.sendMessage(Component.text(state.name + " quiere entrar y te eligió como amigo de confianza. ",
                NamedTextColor.LIGHT_PURPLE)
            .append(command("[Aprobar]", "/auth aprobar " + state.name, "Solo si de verdad es " + state.name)
                .color(NamedTextColor.GREEN))
            .append(Component.space())
            .append(command("[Rechazar]", "/auth rechazar " + state.name, "No es esa persona")
                .color(NamedTextColor.RED)));
        friend.playSound(friend.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
    }

    void answerFriend(Player approver, String targetName, boolean approved) {
        Player target = Bukkit.getPlayerExact(targetName);
        PlayerAuthState state = target == null ? null : states.get(target.getUniqueId());
        if (state == null || state.authenticated || !key(approver.getName()).equals(state.friendApprover)
            || System.currentTimeMillis() > state.friendExpiresAt) {
            approver.sendMessage(PREFIX + "§cNo hay ningún pedido pendiente de §f" + targetName + "§c.");
            return;
        }
        state.friendApprover = null;
        if (approved) {
            approver.sendMessage(PREFIX + "§aAprobaste el ingreso de §f" + state.name + "§a.");
            target.sendMessage(PREFIX + "§f" + approver.getName() + "§a aprobó tu ingreso.");
            succeed(target, state, AuthMethod.FRIEND);
        } else {
            approver.sendMessage(PREFIX + "§eRechazaste el ingreso de §f" + state.name + "§e.");
            fail(target, state, approver.getName() + " rechazó tu ingreso.");
        }
    }

    void adminForceLogin(Player target) {
        PlayerAuthState state = pending(target);
        if (state != null) {
            succeed(target, state, AuthMethod.ADMIN);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Account settings helpers (called by AuthCommands for authenticated players)

    void hashAsync(Player player, String secretValue, java.util.function.BiConsumer<PlayerAuthState,
        PasswordHasher.HashedSecret> onHashed) {
        PlayerAuthState state = states.get(player.getUniqueId());
        if (state == null || state.busy) {
            return;
        }
        state.busy = true;
        async(() -> {
            PasswordHasher.HashedSecret hash = hasher.hash(secretValue);
            sync(player, current -> {
                current.busy = false;
                onHashed.accept(current, hash);
            });
        });
    }

    void verifyAsync(Player player, String secretValue, PasswordHasher.HashedSecret stored,
                     java.util.function.BiConsumer<PlayerAuthState, Boolean> onVerified) {
        PlayerAuthState state = states.get(player.getUniqueId());
        if (state == null || state.busy) {
            return;
        }
        state.busy = true;
        async(() -> {
            boolean valid = PasswordHasher.verify(secretValue, stored);
            sync(player, current -> {
                current.busy = false;
                onVerified.accept(current, valid);
            });
        });
    }

    String passwordRejection(String playerName, String password) {
        if (RESERVED_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            return "Esa palabra está reservada para otros métodos de login.";
        }
        return PasswordHasher.rejectionReason(password, playerName, settings.passwordMinLength());
    }

    /** Gives a map with the authenticator QR code; returns {@code false} when the inventory is full. */
    boolean giveQrMap(Player player, String text) {
        QrCode code = QrCode.encodeText(text);
        if (QrMapRenderer.scale(code) < 1) {
            return false;
        }
        int slot = player.getInventory().firstEmpty();
        if (slot < 0) {
            return false;
        }
        MapView view = Bukkit.createMap(player.getWorld());
        view.getRenderers().forEach(view::removeRenderer);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        view.addRenderer(new QrMapRenderer(code));
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        meta.displayName(Component.text("Escaneá con tu app autenticadora", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(qrKey, PersistentDataType.BYTE, (byte) 1);
        map.setItemMeta(meta);
        player.getInventory().setItem(slot, map);
        player.getInventory().setHeldItemSlot(slot < 9 ? slot : player.getInventory().getHeldItemSlot());
        return true;
    }

    boolean isQrMap(ItemStack item) {
        return item != null && item.getType() == Material.FILLED_MAP && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(qrKey, PersistentDataType.BYTE);
    }

    void removeQrMaps(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isQrMap(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
    }

    /** Sends a premium-verification bounce for an authenticated player running {@code /premium}. */
    boolean beginPremiumOptIn(Player player) {
        if (!premiumActive()) {
            return false;
        }
        premiumGate.beginOptIn(player.getName());
        premiumGate.markBounce(player.getName());
        InetSocketAddress virtualHost = player.getVirtualHost();
        String host = settings.premium().publicHost().isBlank()
            ? (virtualHost == null ? "" : virtualHost.getHostString())
            : settings.premium().publicHost();
        if (host.isBlank()) {
            return false;
        }
        player.transfer(host, settings.premium().publicPort());
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Accessors for listener, commands and channel

    PlayerAuthState state(Player player) {
        return states.get(player.getUniqueId());
    }

    boolean isPending(Player player) {
        PlayerAuthState state = states.get(player.getUniqueId());
        return state != null && !state.authenticated;
    }

    boolean allowsTeleport(Player player) {
        PlayerAuthState state = states.get(player.getUniqueId());
        return state == null || state.authenticated || state.allowTeleport;
    }

    AuthSettings settings() {
        return settings;
    }

    AuthAccountStore accounts() {
        return accounts;
    }

    SecureRandom random() {
        return random;
    }

    World limbo() {
        return limbo;
    }

    Location limboSpawn() {
        return limboSpawn.clone();
    }

    JavaPlugin plugin() {
        return plugin;
    }

    boolean premiumActive() {
        return settings.premium().enabled() && verifier != null && !onlineModeServer;
    }

    Optional<Long> linkedDiscord(PlayerAuthState state) {
        if (!settings.discordEnabled() || state.account == null || !state.account.discordLogin()) {
            return Optional.empty();
        }
        DiscordIntegrationModule module = discord.get();
        if (module == null || module.linkService() == null) {
            return Optional.empty();
        }
        return module.linkService().getDiscordId(state.playerId);
    }

    String challengeFor(PlayerAuthState state) {
        if (state.pgpChallenge == null || System.currentTimeMillis() > state.pgpChallengeExpiresAt) {
            state.pgpChallenge = "bigcasares:" + state.name + ":" + HexFormat.of().formatHex(randomBytes(8));
            state.pgpChallengeExpiresAt = System.currentTimeMillis() + CHALLENGE_MILLIS;
        }
        return state.pgpChallenge;
    }

    void forgetAccount(String name) {
        accounts.delete(name);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Internals

    private void succeed(Player player, PlayerAuthState state, AuthMethod method) {
        if (state.authenticated) {
            return;
        }
        if (!state.progress.satisfy(method)) {
            player.sendMessage(PREFIX + "§a✔ " + method.displayName() + "§7. Tu cuenta pide §fdos métodos§7: "
                + "completá otro distinto.");
            listMethods(player, state);
            return;
        }
        completeLogin(player, state, method, true);
    }

    private void fail(Player player, PlayerAuthState state, String reason) {
        state.failures++;
        boolean locked = limiter.recordFailure(state.addressKey, System.currentTimeMillis());
        if (locked || state.failures >= settings.maxAttemptsPerJoin()) {
            player.kick(Component.text(reason + "\nDemasiados intentos fallidos.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(PREFIX + "§c" + reason + " §8(" + state.failures + "/" + settings.maxAttemptsPerJoin()
            + ")");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    private void completeLogin(Player player, PlayerAuthState state, AuthMethod method, boolean fromLimbo) {
        state.authenticated = true;
        state.busy = false;
        state.pgpChallenge = null;
        if (state.discordRequestId != null) {
            discordRequests.remove(state.discordRequestId);
            state.clearDiscord();
        }
        authenticatedNames.add(key(state.name));
        limiter.recordSuccess(state.addressKey);
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof PatternMenu) {
            player.closeInventory();
        }
        if (fromLimbo) {
            releaseFromLimbo(player, state);
        }
        if (state.account != null) {
            state.account.recordLogin(state.addressKey, System.currentTimeMillis());
            accounts.save(state.account);
        }
        if (state.deferredJoinMessage != null) {
            Bukkit.getServer().sendMessage(state.deferredJoinMessage);
            state.deferredJoinMessage = null;
        }
        player.updateCommands();
        AuthPluginChannel.sendResult(this, player, true, "ok");
        player.showTitle(Title.title(
            Component.text("¡Bienvenido!", NamedTextColor.GREEN),
            Component.text(method.automatic() ? "Ingreso automático: " + method.displayName()
                : "Sesión iniciada", NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(400))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        if (method.automatic()) {
            player.sendMessage(PREFIX + "§aIngreso automático §7(" + method.displayName() + ").");
        }
        if (state.account == null) {
            player.sendMessage(PREFIX + "§eTu nombre no está protegido. Registrate con §f/register <contraseña> "
                + "<contraseña>§e la próxima vez que entres, o usá §f/auth password <nueva>§e ahora.");
        }
    }

    private void enterLimbo(Player player, PlayerAuthState state, Location original) {
        UUID playerId = player.getUniqueId();
        Location returnLocation = original;
        if (returnLocation == null && !player.getWorld().equals(limbo)) {
            returnLocation = player.getLocation();
        }
        if (!stash.exists(playerId)) {
            ItemStack[] contents = player.getInventory().getContents().clone();
            for (int slot = 0; slot < contents.length; slot++) {
                if (contents[slot] == null) {
                    contents[slot] = ItemStack.empty();
                }
            }
            stash.write(playerId, contents, returnLocation);
        }
        player.getInventory().clear();
        player.closeInventory();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != player) {
                other.unlistPlayer(player);
                player.unlistPlayer(other);
            }
        }
        if (!player.getWorld().equals(limbo)) {
            teleportOwned(player, state, limboSpawn);
        }
        player.setFallDistance(0f);
    }

    /** Restores location, inventory and player-list visibility of a limbo player. */
    private void releaseFromLimbo(Player player, PlayerAuthState state) {
        UUID playerId = player.getUniqueId();
        Optional<InventoryStash.Stashed> stashed = stash.read(playerId);
        Location target = stashed.map(InventoryStash.Stashed::location).orElse(state.returnLocation);
        if (target == null || target.getWorld() == null || target.getWorld().equals(limbo)) {
            World main = Bukkit.getWorlds().getFirst();
            target = main.getSpawnLocation();
        }
        if (player.getWorld().equals(limbo)) {
            teleportOwned(player, state, target);
        }
        if (stashed.isPresent()) {
            removeQrMaps(player);
            List<ItemStack> extras = new ArrayList<>();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    extras.add(item);
                }
            }
            player.getInventory().setContents(stashed.get().items());
            for (ItemStack overflow : player.getInventory().addItem(extras.toArray(ItemStack[]::new)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
            stash.delete(playerId);
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == player) {
                continue;
            }
            PlayerAuthState otherState = states.get(other.getUniqueId());
            boolean otherPending = otherState != null && !otherState.authenticated;
            if (state.authenticated && !otherPending && !vanished(other)) {
                player.listPlayer(other);
            }
            if (state.authenticated && !otherPending && !vanished(player)) {
                other.listPlayer(player);
            }
        }
    }

    private void teleportOwned(Player player, PlayerAuthState state, Location target) {
        state.allowTeleport = true;
        try {
            player.teleport(target);
        } finally {
            state.allowTeleport = false;
        }
    }

    private boolean vanished(Player player) {
        return vanish != null && vanish.contains(player.getUniqueId());
    }

    private void greet(Player player, PlayerAuthState state) {
        player.showTitle(Title.title(
            Component.text(state.registered() ? "Iniciá sesión" : "Registrate", NamedTextColor.LIGHT_PURPLE),
            Component.text("Tu inventario y tu posición están a salvo", NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(4), Duration.ofMillis(500))));
        if (!state.registered()) {
            player.sendMessage(PREFIX + "§fBienvenido, §d" + state.name + "§f. Este nombre todavía no tiene dueño.");
            player.sendMessage(Component.text("» ", NamedTextColor.DARK_GRAY)
                .append(suggest("/register <contraseña> <contraseña>", "/register ", "Crear la cuenta con contraseña")));
            if (settings.pgpEnabled()) {
                player.sendMessage(Component.text("» o con tu clave PGP: ", NamedTextColor.DARK_GRAY)
                    .append(suggest("/register pgp <clave pública>", "/register pgp ",
                        "Pegá: gpg --export --export-options export-minimal <id> | base64 -w0")));
            }
            return;
        }
        player.sendMessage(PREFIX + "§fHola de nuevo, §d" + state.name + "§f. Elegí cómo entrar:");
        listMethods(player, state);
        if (settings.discordAutoPrompt()) {
            startDiscordLogin(player, false);
        }
        if (settings.pluginChannel() && player.getListeningPluginChannels().contains(CHANNEL)) {
            AuthPluginChannel.sendChallenge(this, player, state);
        }
    }

    void listMethods(Player player, PlayerAuthState state) {
        AuthAccount account = state.account;
        Set<AuthMethod> configured = account.configuredMethods();
        if (configured.contains(AuthMethod.PASSWORD) && !state.progress.satisfied(AuthMethod.PASSWORD)) {
            player.sendMessage(line(suggest("/login <contraseña>", "/login ", "Entrar con tu contraseña")));
        }
        if (configured.contains(AuthMethod.TOTP) && !state.progress.satisfied(AuthMethod.TOTP)) {
            player.sendMessage(line(suggest("/login <código de 6 dígitos>", "/login ", "Código de tu app autenticadora")));
        }
        if (linkedDiscord(state).isPresent() && !state.progress.satisfied(AuthMethod.DISCORD)) {
            player.sendMessage(line(command("/login discord", "/login discord", "Aprobar desde un DM de Discord")));
        }
        if (settings.pgpEnabled() && configured.contains(AuthMethod.PGP) && !state.progress.satisfied(AuthMethod.PGP)) {
            player.sendMessage(line(command("/login pgp", "/login pgp", "Firmar un desafío con tu clave")));
        }
        if (settings.patternEnabled() && configured.contains(AuthMethod.PATTERN)
            && !state.progress.satisfied(AuthMethod.PATTERN)) {
            player.sendMessage(line(command("/login patron", "/login patron", "Dibujar tu patrón")));
        }
        if (settings.friendsEnabled() && configured.contains(AuthMethod.FRIEND)
            && !state.progress.satisfied(AuthMethod.FRIEND)) {
            player.sendMessage(line(suggest("/login amigo <jugador>", "/login amigo ", "Que te apruebe un amigo")));
        }
        if (configured.contains(AuthMethod.RECOVERY_CODE)) {
            player.sendMessage(Component.text("   ¿Perdiste el acceso? /login <código de recuperación>",
                NamedTextColor.DARK_GRAY));
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        preAuthorized.values().removeIf(pre -> pre.expiresAt() < now);
        for (PlayerAuthState state : List.copyOf(states.values())) {
            if (state.authenticated) {
                continue;
            }
            Player player = Bukkit.getPlayer(state.playerId);
            if (player == null) {
                continue;
            }
            long limit = (state.registered() ? settings.loginTimeoutSeconds() : settings.registerTimeoutSeconds())
                * 1000L;
            long remaining = limit - (now - state.joinedAtMillis);
            if (remaining <= 0) {
                player.kick(Component.text("Se acabó el tiempo para iniciar sesión.", NamedTextColor.RED));
                continue;
            }
            if (!player.getWorld().equals(limbo)) {
                teleportOwned(player, state, limboSpawn);
            }
            player.sendActionBar(Component.text(state.registered() ? "Iniciá sesión" : "Registrate",
                    NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(" · " + (remaining / 1000L) + "s", NamedTextColor.GRAY)));
        }
    }

    private String onDiscordDecision(String requestId, long discordUserId, boolean approved) {
        UUID playerId = discordRequests.get(requestId);
        PlayerAuthState state = playerId == null ? null : states.get(playerId);
        if (state == null || state.authenticated || !requestId.equals(state.discordRequestId)
            || System.currentTimeMillis() > state.discordExpiresAt
            || linkedDiscord(state).filter(id -> id == discordUserId).isEmpty()) {
            return "Esta solicitud ya no es válida.";
        }
        RuntimeRegistrationScope owner = scope;
        if (owner == null) {
            return "El sistema de login no está disponible ahora.";
        }
        discordRequests.remove(requestId);
        Bukkit.getScheduler().runTask(plugin, owner.guard(() -> {
            Player player = Bukkit.getPlayer(playerId);
            PlayerAuthState current = states.get(playerId);
            if (player == null || current != state || current.authenticated) {
                return;
            }
            current.clearDiscord();
            if (approved) {
                succeed(player, current, AuthMethod.DISCORD);
            } else {
                limiter.lock(current.addressKey, System.currentTimeMillis(), 30 * 60_000L);
                player.kick(Component.text("El dueño de la cuenta rechazó este ingreso desde Discord.",
                    NamedTextColor.RED));
            }
        }));
        return approved ? "✅ Ingreso aprobado." : "⛔ Ingreso bloqueado. Esa dirección no podrá entrar por 30 minutos.";
    }

    private boolean bounce(PlayerConfigurationConnection connection) {
        InetSocketAddress virtualHost = connection.getVirtualHost();
        String host = settings.premium().publicHost().isBlank()
            ? (virtualHost == null ? "" : virtualHost.getHostString())
            : settings.premium().publicHost();
        if (host.isBlank()) {
            return false;
        }
        connection.transfer(host, settings.premium().publicPort());
        return true;
    }

    private byte[] readCookie(PlayerConfigurationConnection connection) {
        try {
            return connection.retrieveCookie(premiumCookieKey).get(3, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            return null;
        }
    }

    private void startPremiumVerifier(BukkitRuntimeRegistrations registrations) {
        AuthSettings.Premium premium = settings.premium();
        if (!premium.enabled() || onlineModeServer) {
            return;
        }
        if (!Bukkit.getServer().isAcceptingTransfers()) {
            plugin.getLogger().warning("auth-system: el inicio premium necesita accepts-transfers=true en "
                + "server.properties; queda desactivado.");
            return;
        }
        int returnPort = premium.returnPort() == 0 ? Bukkit.getPort() : premium.returnPort();
        PremiumVerifierServer server = new PremiumVerifierServer(
            new InetSocketAddress(premium.bindHost(), premium.port()),
            MojangSessionClient.http(premium.sessionServer()),
            (profile, address, handshakeHost) ->
                premiumGate.decide(profile, address, handshakeHost, premium.returnHost(), returnPort),
            plugin.getLogger());
        try {
            server.start();
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "auth-system: no se pudo abrir el verificador premium en el puerto "
                + premium.port() + "; el inicio premium queda desactivado.", ex);
            return;
        }
        this.verifier = registrations.ownCloseable("auth-premium-verifier", server);
        plugin.getLogger().info("auth-system: verificador premium escuchando en " + premium.bindHost() + ":"
            + server.localPort());
    }

    private void preAuthorize(UUID playerId, AuthMethod method, UUID premiumId, boolean enablePremium) {
        preAuthorized.put(playerId, new PreAuth(method, premiumId, enablePremium,
            System.currentTimeMillis() + PREAUTH_MILLIS));
    }

    private PlayerAuthState newState(UUID playerId, String name, InetAddress address, AuthAccount account) {
        return new PlayerAuthState(playerId, name, address, secret.addressKey(address), System.currentTimeMillis(),
            account);
    }

    private PlayerAuthState pending(Player player) {
        PlayerAuthState state = states.get(player.getUniqueId());
        if (state == null || state.authenticated) {
            player.sendMessage(PREFIX + "§7Ya iniciaste sesión.");
            return null;
        }
        return state;
    }

    private boolean requireRegistered(Player player, PlayerAuthState state) {
        if (!state.registered()) {
            player.sendMessage(PREFIX + "§cNo estás registrado. Usá §f/register <contraseña> <contraseña>§c.");
            return false;
        }
        return true;
    }

    private DiscordGateway discordGateway() {
        DiscordIntegrationModule module = discord.get();
        return module == null ? null : module.gateway();
    }

    private void async(Runnable task) {
        RuntimeRegistrationScope current = scope;
        if (current != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, current.guard(task));
        }
    }

    private void sync(Player player, java.util.function.Consumer<PlayerAuthState> callback) {
        RuntimeRegistrationScope current = scope;
        if (current == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, current.guard(() -> {
            PlayerAuthState state = player.isOnline() ? states.get(player.getUniqueId()) : null;
            if (state != null) {
                callback.accept(state);
            }
        }));
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    private void clearState() {
        states.clear();
        preAuthorized.clear();
        originalSpawns.clear();
        authenticatedNames.clear();
        discordRequests.clear();
        verifier = null;
        scope = null;
    }

    private static InetAddress addressOf(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return InetAddress.getLoopbackAddress();
        }
        return address.getAddress();
    }

    static String maskAddress(InetAddress address) {
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            return (raw[0] & 0xFF) + "." + (raw[1] & 0xFF) + ".x.x";
        }
        return "IPv6 " + HexFormat.of().formatHex(raw, 0, 4) + "::";
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    static Component line(Component content) {
        return Component.text(" » ", NamedTextColor.DARK_GRAY).append(content);
    }

    static Component suggest(String label, String command, String hover) {
        return Component.text(label, NamedTextColor.WHITE)
            .clickEvent(ClickEvent.suggestCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    static Component command(String label, String command, String hover) {
        return Component.text(label, NamedTextColor.AQUA)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private record PreAuth(AuthMethod method, UUID premiumId, boolean enablePremium, long expiresAt) {
    }
}
