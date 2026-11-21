package dev.linqfy.bigCasares.modules.auth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.WritableBookMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code /login}, {@code /register}, {@code /auth} and {@code /premium}. */
final class AuthCommands implements CommandExecutor, TabCompleter {

    private static final String P = AuthModule.PREFIX;
    private static final String ADMIN_PERMISSION = "bigcasares.auth.admin";
    private final AuthModule auth;

    AuthCommands(AuthModule auth) {
        this.auth = auth;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("auth") && args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            admin(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        switch (name) {
            case "login" -> login(player, args);
            case "register" -> register(player, args);
            case "premium" -> premium(player, args);
            default -> manage(player, args);
        }
        return true;
    }

    private void login(Player player, String[] args) {
        if (args.length == 0) {
            PlayerAuthState state = auth.state(player);
            if (state != null && !state.authenticated && state.registered()) {
                auth.listMethods(player, state);
            } else {
                player.sendMessage(P + "§7Uso: §f/login <contraseña o código>");
            }
            return;
        }
        String method = args[0].toLowerCase(Locale.ROOT);
        switch (method) {
            case "discord" -> auth.startDiscordLogin(player, true);
            case "pgp" -> {
                if (args.length == 1) {
                    auth.startPgp(player);
                } else {
                    auth.submitPgpChunk(player, String.join("", Arrays.copyOfRange(args, 1, args.length)));
                }
            }
            case "patron", "pattern" -> auth.openPatternLogin(player);
            case "amigo", "friend" -> {
                if (args.length < 2) {
                    player.sendMessage(P + "§7Uso: §f/login amigo <jugador>");
                } else {
                    auth.requestFriend(player, args[1]);
                }
            }
            case "codigo", "code" -> {
                if (args.length < 2) {
                    player.sendMessage(P + "§7Uso: §f/login codigo <código>");
                } else {
                    auth.loginWithSecret(player, args[1]);
                }
            }
            default -> auth.loginWithSecret(player, String.join(" ", args));
        }
    }

    private void register(Player player, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("pgp")) {
            if (!auth.settings().pgpEnabled()) {
                player.sendMessage(P + "§cEl registro con PGP está desactivado.");
                return;
            }
            auth.registerPgpChunk(player, String.join("", Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        if (args.length != 2) {
            player.sendMessage(P + "§7Uso: §f/register <contraseña> <contraseña>§7 o §f/register pgp <clave>");
            return;
        }
        auth.register(player, args[0], args[1]);
    }

    private void premium(Player player, String[] args) {
        PlayerAuthState state = loggedIn(player);
        if (state == null) {
            return;
        }
        if (!auth.premiumActive()) {
            player.sendMessage(P + "§cEl inicio premium no está habilitado en este servidor.");
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("off")) {
            if (state.account != null && state.account.premium()) {
                state.account.premium(false, null);
                auth.accounts().save(state.account);
            }
            player.sendMessage(P + "§eInicio premium desactivado.");
            return;
        }
        if (state.account == null) {
            player.sendMessage(P + "§cPrimero creá tu cuenta con §f/auth password <nueva>§c.");
            return;
        }
        if (state.account.premium()) {
            player.sendMessage(P + "§aTu cuenta ya entra automáticamente como premium. §7(/premium off para quitarlo)");
            return;
        }
        player.sendMessage(P + "§dVerificando tu cuenta con Mojang... vas a volver en un segundo.");
        player.sendMessage(P + "§7Si tu Minecraft no es original, la conexión va a fallar: volvé a entrar normal.");
        if (!auth.beginPremiumOptIn(player)) {
            player.sendMessage(P + "§cNo se pudo iniciar la verificación (falta premium.public-host).");
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // /auth

    private void manage(Player player, String[] args) {
        String sub = args.length == 0 ? "estado" : args[0].toLowerCase(Locale.ROOT);
        String[] rest = args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
        if (sub.equals("aprobar") || sub.equals("rechazar")) {
            if (loggedIn(player) != null && rest.length == 1) {
                auth.answerFriend(player, rest[0], sub.equals("aprobar"));
            }
            return;
        }
        PlayerAuthState state = loggedIn(player);
        if (state == null) {
            return;
        }
        switch (sub) {
            case "estado", "status" -> status(player, state);
            case "password", "contraseña", "contrasena" -> password(player, state, rest);
            case "totp", "app", "2fa-app" -> totp(player, state, rest);
            case "codigos", "codes", "recuperacion" -> recoveryCodes(player, state);
            case "pgp" -> pgp(player, state, rest);
            case "patron", "pattern" -> pattern(player, state, rest);
            case "discord" -> discord(player, state, rest);
            case "confiar", "trust" -> trust(player, state, rest, true);
            case "desconfiar", "untrust" -> trust(player, state, rest, false);
            case "2fa", "doble" -> twoFactor(player, state, rest);
            case "sesion", "session" -> sessionResume(player, state, rest);
            case "olvidar", "logout" -> forgetSession(player, state);
            default -> player.sendMessage(P + "§7Subcomandos: §festado, password, totp, codigos, pgp, patron, "
                + "discord, confiar, desconfiar, 2fa, sesion, olvidar");
        }
    }

    private void status(Player player, PlayerAuthState state) {
        AuthAccount account = state.account;
        if (account == null) {
            player.sendMessage(P + "§eTu nombre no está protegido. Creá la cuenta con §f/auth password <nueva>§e.");
            return;
        }
        Set<AuthMethod> methods = account.configuredMethods();
        boolean discordLinked = auth.linkedDiscord(state).isPresent();
        player.sendMessage(Component.text("━━ Seguridad de " + account.name() + " ━━", NamedTextColor.LIGHT_PURPLE));
        row(player, "Contraseña", methods.contains(AuthMethod.PASSWORD), "/auth password ",
            "Cambiar o crear la contraseña");
        row(player, "App autenticadora", methods.contains(AuthMethod.TOTP), "/auth totp",
            "Google Authenticator, Aegis, Authy... (QR en un mapa)");
        row(player, "Códigos de recuperación (" + account.recoveryDigests().size() + ")",
            methods.contains(AuthMethod.RECOVERY_CODE), "/auth codigos", "Generar 10 códigos de un solo uso");
        row(player, "Discord", discordLinked, discordLinked ? "/auth discord " : "/linkeardiscord",
            discordLinked ? "Activar/desactivar el ingreso por Discord" : "Vincular tu Discord primero");
        row(player, "Clave PGP" + (account.pgpFingerprint() == null ? ""
                : " (" + account.pgpFingerprint().substring(24) + ")"),
            methods.contains(AuthMethod.PGP), "/auth pgp ", "Pegá tu clave pública (en partes con +)");
        row(player, "Patrón", methods.contains(AuthMethod.PATTERN), "/auth patron", "Dibujar un patrón en un cofre");
        row(player, "Amigos de confianza " + account.trustedFriends(), methods.contains(AuthMethod.FRIEND),
            "/auth confiar ", "Un amigo conectado puede aprobar tu ingreso");
        row(player, "Premium automático", account.premium(), "/premium", "Entrar sin login con Minecraft original");
        row(player, "Exigir dos métodos", account.requireTwoFactors(), "/auth 2fa ", "on / off");
        row(player, "Recordar sesión (misma IP)", account.sessionResume(), "/auth sesion ", "on / off");
    }

    private void row(Player player, String label, boolean enabled, String command, String hover) {
        player.sendMessage(Component.text(enabled ? " ✔ " : " ✘ ", enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
            .append(Component.text(label, NamedTextColor.WHITE)
                .clickEvent(command.endsWith(" ") ? ClickEvent.suggestCommand(command) : ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover)))));
    }

    private void password(Player player, PlayerAuthState state, String[] args) {
        AuthAccount account = state.account;
        boolean hasPassword = account != null && account.password() != null;
        if (hasPassword ? args.length != 2 : args.length != 1) {
            player.sendMessage(P + "§7Uso: §f" + (hasPassword ? "/auth password <actual> <nueva>" : "/auth password <nueva>"));
            return;
        }
        String fresh = hasPassword ? args[1] : args[0];
        String rejection = auth.passwordRejection(player.getName(), fresh);
        if (rejection != null) {
            player.sendMessage(P + "§c" + rejection);
            return;
        }
        if (!hasPassword) {
            auth.hashAsync(player, fresh, (current, hash) -> {
                if (current.account == null) {
                    current.account = new AuthAccount(player.getName(), System.currentTimeMillis());
                }
                current.account.password(hash);
                auth.accounts().put(current.account);
                player.sendMessage(P + "§aContraseña guardada.");
            });
            return;
        }
        auth.verifyAsync(player, args[0], account.password(), (current, valid) -> {
            if (!valid) {
                player.sendMessage(P + "§cLa contraseña actual no es correcta.");
                return;
            }
            auth.hashAsync(player, fresh, (latest, hash) -> {
                latest.account.password(hash);
                auth.accounts().save(latest.account);
                player.sendMessage(P + "§aContraseña cambiada.");
            });
        });
    }

    private void totp(Player player, PlayerAuthState state, String[] args) {
        AuthAccount account = requireAccount(player, state);
        if (account == null) {
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("off")) {
            if (account.totpSecret() == null) {
                player.sendMessage(P + "§7No tenés app autenticadora configurada.");
                return;
            }
            var step = args.length < 2 ? java.util.OptionalLong.empty() : Totp.verify(account.totpSecret(), args[1],
                System.currentTimeMillis() / 1000L, auth.settings().totpWindow(), account.totpLastStep());
            if (step.isEmpty()) {
                player.sendMessage(P + "§7Para quitarla confirmá con un código: §f/auth totp off <código>");
                return;
            }
            account.totpSecret(null);
            relaxTwoFactor(player, state);
            auth.accounts().save(account);
            player.sendMessage(P + "§eApp autenticadora eliminada.");
            return;
        }
        if (args.length >= 1 && state.pendingTotpSecret != null) {
            var step = Totp.verify(state.pendingTotpSecret, args[0], System.currentTimeMillis() / 1000L,
                auth.settings().totpWindow(), Long.MIN_VALUE);
            if (step.isEmpty()) {
                player.sendMessage(P + "§cEse código no coincide. Probá con el siguiente que muestre la app.");
                return;
            }
            account.totpSecret(state.pendingTotpSecret);
            account.totpLastStep(step.getAsLong());
            state.pendingTotpSecret = null;
            auth.removeQrMaps(player);
            auth.accounts().save(account);
            player.sendMessage(P + "§a¡App autenticadora activada!");
            if (account.recoveryDigests().isEmpty()) {
                recoveryCodes(player, state);
            }
            return;
        }
        String secret = Totp.newSecret(auth.random());
        state.pendingTotpSecret = secret;
        String uri = Totp.otpauthUri(auth.settings().totpIssuer(), account.name(), secret);
        boolean map = auth.giveQrMap(player, uri);
        player.sendMessage(Component.text("━━ App autenticadora ━━", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text(map
            ? "1. Sostené el mapa y escanealo con Google Authenticator, Aegis o Authy."
            : "1. Liberá un espacio del inventario para recibir el QR, o cargá la clave a mano:", NamedTextColor.GRAY));
        player.sendMessage(Component.text("   Clave: ", NamedTextColor.GRAY)
            .append(Component.text(Totp.grouped(secret), NamedTextColor.WHITE)
                .clickEvent(ClickEvent.copyToClipboard(secret))
                .hoverEvent(HoverEvent.showText(Component.text("Copiar")))));
        player.sendMessage(Component.text("2. Confirmá con ", NamedTextColor.GRAY)
            .append(AuthModule.suggest("/auth totp <código>", "/auth totp ", "El código de 6 dígitos de la app")));
    }

    private void recoveryCodes(Player player, PlayerAuthState state) {
        AuthAccount account = requireAccount(player, state);
        if (account == null) {
            return;
        }
        List<String> codes = RecoveryCodes.generate(auth.random(), 10);
        account.recoveryDigests(codes.stream().map(RecoveryCodes::digest).toList());
        auth.accounts().save(account);
        String joined = String.join(" ", codes);
        player.sendMessage(Component.text("Códigos de recuperación (cada uno sirve una vez; se muestran solo ahora):",
            NamedTextColor.GOLD));
        player.sendMessage(Component.text(joined, NamedTextColor.WHITE)
            .clickEvent(ClickEvent.copyToClipboard(joined))
            .hoverEvent(HoverEvent.showText(Component.text("Copiar todos"))));
    }

    private void pgp(Player player, PlayerAuthState state, String[] args) {
        AuthAccount account = requireAccount(player, state);
        if (account == null) {
            return;
        }
        if (!auth.settings().pgpEnabled()) {
            player.sendMessage(P + "§cPGP está desactivado en este servidor.");
            return;
        }
        if (args.length == 0) {
            player.sendMessage(P + "§7Exportá tu clave pública y pegala:");
            String command = "gpg --export --export-options export-minimal <TU_ID> | base64 -w0";
            player.sendMessage(Component.text("  " + command, NamedTextColor.WHITE)
                .clickEvent(ClickEvent.copyToClipboard(command)));
            player.sendMessage(P + "§7Luego §f/auth pgp <clave>§7 (en partes terminadas en §f+§7 si es larga), o "
                + "escribila en un libro y usá §f/auth pgp libro§7 sosteniéndolo.");
            return;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("quitar") || first.equals("remove")) {
            account.pgpKey(null, null);
            relaxTwoFactor(player, state);
            auth.accounts().save(account);
            player.sendMessage(P + "§eClave PGP eliminada.");
            return;
        }
        Optional<OpenPgp.KeyRing> ring;
        if (first.equals("libro") || first.equals("book")) {
            ring = keyFromBook(player);
        } else {
            ring = auth.collectKey(player, state, String.join("", args));
        }
        ring.ifPresent(keyRing -> {
            account.pgpKey(keyRing.encoded(), keyRing.primaryFingerprint());
            auth.accounts().save(account);
            player.sendMessage(P + "§aClave PGP guardada: §f" + OpenPgp.describe(keyRing) + "§a. Probala la próxima vez "
                + "con §f/login pgp§a.");
        });
    }

    private Optional<OpenPgp.KeyRing> keyFromBook(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        List<String> pages = new ArrayList<>();
        if (held.getType() == Material.WRITABLE_BOOK && held.getItemMeta() instanceof WritableBookMeta book) {
            pages.addAll(book.getPages());
        } else if (held.getType() == Material.WRITTEN_BOOK && held.getItemMeta() instanceof BookMeta book) {
            book.pages().forEach(page -> pages.add(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(page)));
        } else {
            player.sendMessage(P + "§cSostené un libro con tu clave pública.");
            return Optional.empty();
        }
        try {
            return Optional.of(OpenPgp.parseKeyRing(String.join("\n", pages)));
        } catch (OpenPgp.PgpException ex) {
            player.sendMessage(P + "§c" + ex.getMessage());
            return Optional.empty();
        }
    }

    private void pattern(Player player, PlayerAuthState state, String[] args) {
        AuthAccount account = requireAccount(player, state);
        if (account == null) {
            return;
        }
        if (!auth.settings().patternEnabled()) {
            player.sendMessage(P + "§cEl patrón está desactivado en este servidor.");
            return;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("quitar") || args[0].equalsIgnoreCase("remove"))) {
            account.pattern(null);
            relaxTwoFactor(player, state);
            auth.accounts().save(account);
            player.sendMessage(P + "§ePatrón eliminado.");
            return;
        }
        state.pendingPattern = null;
        openPatternSetup(player, "Dibujá tu nuevo patrón");
    }

    private void openPatternSetup(Player player, String title) {
        player.openInventory(new PatternMenu(title, sequence -> {
            PlayerAuthState current = auth.state(player);
            if (current == null || !current.authenticated) {
                player.closeInventory();
                return;
            }
            if (sequence.length() < auth.settings().patternMinLength()) {
                player.sendMessage(P + "§cUsá al menos " + auth.settings().patternMinLength() + " puntos.");
                return;
            }
            if (current.pendingPattern == null) {
                current.pendingPattern = sequence;
                openPatternSetup(player, "Repetí el patrón");
                return;
            }
            boolean matches = current.pendingPattern.equals(sequence);
            current.pendingPattern = null;
            player.closeInventory();
            if (!matches) {
                player.sendMessage(P + "§cLos patrones no coinciden. Probá de nuevo con §f/auth patron§c.");
                return;
            }
            auth.hashAsync(player, PatternMenu.secretOf(sequence), (latest, hash) -> {
                latest.account.pattern(hash);
                auth.accounts().save(latest.account);
                player.sendMessage(P + "§a¡Patrón guardado! Entrá con §f/login patron§a.");
            });
        }).getInventory());
    }

    private void discord(Player player, PlayerAuthState state, String[] args) {
        AuthAccount account = requireAccount(player, state);
        if (account == null) {
            return;
        }
        if (args.length == 0) {
            player.sendMessage(P + "§7Uso: §f/auth discord <on|off>§7 (vinculá antes con §f/linkeardiscord§7)");
            return;
        }
        account.discordLogin(args[0].equalsIgnoreCase("on"));
        if (!account.discordLogin()) {
            relaxTwoFactor(player, state);
        }
        auth.accounts().save(account);
        player.sendMessage(P + (account.discordLogin() ? "§aIngreso por Discord activado." : "§eIngreso por Discord desactivado."));
    }

    private void trust(Player player, PlayerAuthState state, String[] args, boolean add) {
        AuthAccount account = requireAccount(player, state);
        if (account == null || args.length != 1) {
            player.sendMessage(P + "§7Uso: §f/auth " + (add ? "confiar" : "desconfiar") + " <jugador>");
            return;
        }
        if (args[0].equalsIgnoreCase(player.getName())) {
            player.sendMessage(P + "§cNo podés ser tu propio amigo de confianza.");
            return;
        }
        if (add) {
            if (auth.accounts().find(args[0]).isEmpty()) {
                player.sendMessage(P + "§c" + args[0] + " no tiene una cuenta registrada.");
                return;
            }
            account.trust(args[0]);
            player.sendMessage(P + "§a" + args[0] + " puede aprobar tus ingresos.");
        } else {
            account.untrust(args[0]);
            relaxTwoFactor(player, state);
            player.sendMessage(P + "§e" + args[0] + " ya no puede aprobar tus ingresos.");
        }
        auth.accounts().save(account);
    }

    private void twoFactor(Player player, PlayerAuthState state, String[] args) {
        AuthAccount account = requireAccount(player, state);
        if (account == null) {
            return;
        }
        boolean enable = args.length >= 1 && args[0].equalsIgnoreCase("on");
        if (enable && availableMethods(state) < 2) {
            player.sendMessage(P + "§cNecesitás al menos dos métodos configurados (por ejemplo contraseña + app).");
            return;
        }
        account.requireTwoFactors(enable);
        auth.accounts().save(account);
        player.sendMessage(P + (enable ? "§aDesde ahora tu cuenta pide dos métodos distintos." : "§eYa alcanza con un método."));
    }

    private void sessionResume(Player player, PlayerAuthState state, String[] args) {
        AuthAccount account = requireAccount(player, state);
        if (account == null) {
            return;
        }
        account.sessionResume(args.length >= 1 && args[0].equalsIgnoreCase("on"));
        auth.accounts().save(account);
        player.sendMessage(P + (account.sessionResume()
            ? "§aTu sesión se recuerda unas horas si volvés desde la misma conexión."
            : "§eTu sesión ya no se recuerda: siempre vas a iniciar sesión."));
    }

    private void forgetSession(Player player, PlayerAuthState state) {
        AuthAccount account = requireAccount(player, state);
        if (account != null) {
            account.recordLogin(null, 0L);
            auth.accounts().save(account);
            player.sendMessage(P + "§eSesión olvidada: la próxima vez vas a tener que iniciar sesión.");
        }
    }

    private void relaxTwoFactor(Player player, PlayerAuthState state) {
        if (state.account.requireTwoFactors() && availableMethods(state) < 2) {
            state.account.requireTwoFactors(false);
            player.sendMessage(P + "§eTe quedó un solo método: se desactivó el requisito de dos métodos.");
        }
    }

    private int availableMethods(PlayerAuthState state) {
        Set<AuthMethod> methods = new java.util.HashSet<>(state.account.configuredMethods());
        methods.remove(AuthMethod.RECOVERY_CODE);
        if (auth.linkedDiscord(state).isPresent()) {
            methods.add(AuthMethod.DISCORD);
        }
        return methods.size();
    }

    // -------------------------------------------------------------------------------------------------------------
    // /auth admin

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(P + "§cNo tenés permiso.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(P + "§7Uso: §f/auth admin <info|borrar|forzar|premium|resetpatron> <jugador> [on|off]");
            return;
        }
        String target = args[1];
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> auth.accounts().find(target).ifPresentOrElse(account -> sender.sendMessage(P + "§f"
                    + account.name() + "§7: " + account.configuredMethods().stream().map(AuthMethod::displayName)
                    .collect(Collectors.joining(", ")) + (account.premium() ? " §d[premium]" : "")
                    + (account.requireTwoFactors() ? " §6[2 métodos]" : "")),
                () -> sender.sendMessage(P + "§7" + target + " no está registrado."));
            case "borrar", "unregister" -> {
                auth.forgetAccount(target);
                sender.sendMessage(P + "§eCuenta de " + target + " eliminada; puede registrarse de nuevo.");
            }
            case "forzar", "forcelogin" -> {
                Player player = Bukkit.getPlayerExact(target);
                if (player == null || !auth.isPending(player)) {
                    sender.sendMessage(P + "§c" + target + " no está esperando login.");
                } else {
                    auth.adminForceLogin(player);
                    sender.sendMessage(P + "§aIngreso forzado para " + target + ".");
                }
            }
            case "premium" -> auth.accounts().find(target).ifPresentOrElse(account -> {
                boolean enable = args.length >= 3 && args[2].equalsIgnoreCase("on");
                account.premium(enable, enable ? account.premiumId() : null);
                auth.accounts().save(account);
                sender.sendMessage(P + "§7Premium de " + account.name() + ": " + (enable ? "§aon" : "§eoff"));
            }, () -> sender.sendMessage(P + "§7" + target + " no está registrado."));
            default -> sender.sendMessage(P + "§7Uso: §f/auth admin <info|borrar|forzar|premium> <jugador>");
        }
    }

    private PlayerAuthState loggedIn(Player player) {
        PlayerAuthState state = auth.state(player);
        if (state == null || !state.authenticated) {
            player.sendMessage(P + "§cPrimero iniciá sesión.");
            return null;
        }
        return state;
    }

    private AuthAccount requireAccount(Player player, PlayerAuthState state) {
        if (state.account == null) {
            player.sendMessage(P + "§cTu nombre no está protegido. Creá la cuenta con §f/auth password <nueva>§c.");
        }
        return state.account;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "login" -> List.of("discord", "pgp", "patron", "amigo");
            case "register" -> List.of("pgp");
            case "premium" -> List.of("off");
            default -> List.of("estado", "password", "totp", "codigos", "pgp", "patron", "discord", "confiar",
                "desconfiar", "aprobar", "rechazar", "2fa", "sesion", "olvidar");
        };
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
