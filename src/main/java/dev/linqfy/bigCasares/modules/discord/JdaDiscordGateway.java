package dev.linqfy.bigCasares.modules.discord;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.communication.EmojiAliasService;
import dev.linqfy.bigCasares.modules.moderation.AuditEvent;
import dev.linqfy.bigCasares.modules.moderation.AuditRedactor;
import dev.linqfy.bigCasares.modules.moderation.AuditSeverity;
import dev.linqfy.bigCasares.modules.moderation.AuditSink;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.requests.CloseCode;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class JdaDiscordGateway extends ListenerAdapter implements DiscordGateway {
    private static final int MAX_OUTAGE_EVENTS = 500;
    private static final String LOGIN_APPROVE_PREFIX = "bigcasares:auth:approve:";
    private static final String LOGIN_DENY_PREFIX = "bigcasares:auth:deny:";

    private final BigCasares plugin;
    private final DiscordSettings settings;
    private final DiscordSecrets secrets;
    private final EmojiAliasService emojiAliases;
    private final AuditSink audit;
    private final BiConsumer<String, String> inboundBridge;
    private final BooleanSupplier generationActive;
    private final DiscordLinkService linkService;
    private final YamlDiscordStateStorage stateStorage;
    private final RemoteCommandPolicy commandPolicy = new RemoteCommandPolicy(Duration.ofSeconds(30));
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService coalescer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BigCasares-Discord-Logs");
        thread.setDaemon(true);
        return thread;
    });
    private final Deque<AuditEvent> outageQueue = new ArrayDeque<>();
    private final Map<String, PendingAudit> pendingWarnings = new HashMap<>();
    private volatile JDA jda;
    private volatile TextChannel publicChannel;
    private volatile TextChannel logChannel;
    private volatile boolean healthy;
    private volatile boolean inboundBridgeEnabled = true;
    private volatile boolean stopping;
    private volatile boolean serverOnline = true;
    private volatile List<String> visiblePlayers = List.of();
    private volatile LoginApprovalHandler loginApprovalHandler;
    private DiscordMessageState messageState;

    public JdaDiscordGateway(
        BigCasares plugin,
        DiscordSettings settings,
        DiscordSecrets secrets,
        EmojiAliasService emojiAliases,
        AuditSink audit,
        BiConsumer<String, String> inboundBridge
    ) {
        this(plugin, settings, secrets, emojiAliases, audit, inboundBridge, () -> true, null);
    }

    public JdaDiscordGateway(
        BigCasares plugin,
        DiscordSettings settings,
        DiscordSecrets secrets,
        EmojiAliasService emojiAliases,
        AuditSink audit,
        BiConsumer<String, String> inboundBridge,
        BooleanSupplier generationActive,
        DiscordLinkService linkService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.secrets = secrets;
        this.emojiAliases = emojiAliases;
        this.audit = audit == null ? ignored -> { } : audit;
        this.inboundBridge = inboundBridge == null ? (author, message) -> { } : inboundBridge;
        this.generationActive = generationActive == null ? () -> true : generationActive;
        this.linkService = linkService;
        this.stateStorage = new YamlDiscordStateStorage(
            plugin.getDataFolder().toPath().resolve("data").resolve("discord-integration").resolve("state.yml")
        );
        this.messageState = stateStorage.load();
    }

    @Override
    public void start() {
        stopping = false;
        connect(true);
    }

    private void connect(boolean includeMessageContent) {
        if (!callbacksActive()) {
            return;
        }
        try {
            JDABuilder builder = includeMessageContent
                ? JDABuilder.createDefault(secrets.token(), GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.DIRECT_MESSAGES)
                : JDABuilder.createDefault(secrets.token(), GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES);
            jda = builder.addEventListeners(this).build();
        } catch (RuntimeException ex) {
            healthy = false;
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Discord no pudo iniciar", ex);
        }
    }

    @Override
    public void stop() {
        if (stopping) {
            return;
        }
        stopping = true;
        updateServerStatus(false);
        healthy = false;
        coalescer.shutdown();
        JDA active = jda;
        if (active != null) {
            active.shutdown();
            try {
                if (!active.awaitShutdown(Duration.ofSeconds(5))) {
                    active.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                active.shutdownNow();
            }
        }
        jda = null;
        publicChannel = null;
        logChannel = null;
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public void updateServerStatus(boolean online) {
        serverOnline = online;
        if (healthy) {
            ensureServerMessage();
        }
    }

    @Override
    public void updateVisiblePlayers(List<String> playerNames) {
        visiblePlayers = List.copyOf(playerNames);
        if (healthy) {
            ensurePlayerMessage();
        }
    }

    @Override
    public void sendMinecraftChat(String playerName, String message, String avatarUrl) {
        if (!callbacksActive() || secrets.bridgeWebhookUrl().isBlank()) {
            return;
        }
        String safeName = sanitizeUsername(playerName);
        String safeContent = sanitizeMentions(emojiAliases.replace(message));
        String json = "{\"username\":\"" + json(safeName) + "\","
            + (avatarUrl == null || avatarUrl.isBlank() ? "" : "\"avatar_url\":\"" + json(avatarUrl) + "\",")
            + "\"content\":\"" + json(safeContent) + "\","
            + "\"allowed_mentions\":{\"parse\":[]}}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(secrets.bridgeWebhookUrl()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenAccept(response -> {
                if (!callbacksActive()) {
                    return;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    audit.publish(AuditEvent.system(
                        AuditSeverity.WARNING, "discord", "bridge-webhook-failed",
                        "El webhook respondió HTTP " + response.statusCode()
                    ));
                }
            }).exceptionally(error -> {
                if (callbacksActive()) {
                    audit.publish(AuditEvent.system(
                        AuditSeverity.WARNING, "discord", "bridge-webhook-failed", error.getMessage()
                    ));
                }
                return null;
            });
        } catch (IllegalArgumentException ex) {
            audit.publish(AuditEvent.system(AuditSeverity.WARNING, "discord", "bridge-webhook-invalid", ex.getMessage()));
        }
    }

    @Override
    public void sendPrivateMessage(long userId, String message) {
        if (!callbacksActive() || jda == null) {
            return;
        }
        jda.retrieveUserById(userId).queue(user -> {
            user.openPrivateChannel().queue(channel -> {
                channel.sendMessage(message).queue();
            }, error -> {
                // Ignore if we can't open DM
            });
        }, error -> {
            // Ignore if user not found
        });
    }

    @Override
    public boolean sendLoginApproval(long userId, String requestId, String message) {
        JDA current = jda;
        if (!callbacksActive() || current == null) {
            return false;
        }
        current.retrieveUserById(userId).queue(user -> user.openPrivateChannel().queue(channel ->
            channel.sendMessage(message)
                .addComponents(ActionRow.of(
                    Button.success(LOGIN_APPROVE_PREFIX + requestId, "Aprobar ingreso"),
                    Button.danger(LOGIN_DENY_PREFIX + requestId, "No fui yo")
                ))
                .queue(), error -> {
                    // DMs closed: the player can still use another login method
                }), error -> {
                    // unknown user
                });
        return true;
    }

    @Override
    public void setLoginApprovalHandler(LoginApprovalHandler handler) {
        this.loginApprovalHandler = handler;
    }

    @Override
    public void publish(AuditEvent event) {
        if (!callbacksActive()) {
            return;
        }
        if (event.severity() == AuditSeverity.WARNING || event.severity() == AuditSeverity.SEVERE) {
            coalesce(event);
            return;
        }
        deliverOrQueue(event, 1);
    }

    @Override
    public void onReady(ReadyEvent event) {
        if (!callbacksActive()) {
            return;
        }
        Guild guild = event.getJDA().getGuildById(settings.guildId());
        if (guild == null) {
            plugin.getLogger().warning("Discord desactivado: completá los IDs en config.yml. El bot no pertenece al guild configurado (ID: " + settings.guildId() + ").");
            JDA currentJda = event.getJDA();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, currentJda::shutdown);
            return;
        }
        publicChannel = guild.getTextChannelById(settings.publicChannelId());
        logChannel = guild.getTextChannelById(settings.logChannelId());
        if (publicChannel == null || logChannel == null
            || guild.getTextChannelById(settings.adminChannelId()) == null
            || guild.getTextChannelById(settings.bridgeChannelId()) == null) {
            plugin.getLogger().warning(String.format("Discord desactivado: completá token y bridge-webhook-url (y channel IDs). Faltan canales: public=%s, log=%s, admin=%s, bridge=%s", 
                settings.publicChannelId(), settings.logChannelId(), settings.adminChannelId(), settings.bridgeChannelId()));
            JDA currentJda = event.getJDA();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, currentJda::shutdown);
            return;
        }
        registerCommands(guild);
        healthy = true;
        ensureServerMessage();
        ensurePlayerMessage();
        flushOutageQueue();
    }

    @Override
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        if (!callbacksActive()) {
            return;
        }
        healthy = false;
        if (event.getCloseCode() == CloseCode.DISALLOWED_INTENTS && inboundBridgeEnabled && !stopping) {
            inboundBridgeEnabled = false;
            plugin.getLogger().warning(
                "El intent privilegiado MESSAGE_CONTENT no está habilitado en el portal de Discord; "
                    + "el puente Discord → Minecraft queda desactivado."
            );
            JDA currentJda = event.getJDA();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                currentJda.shutdownNow();
                coalescer.schedule(() -> {
                    if (callbacksActive()) {
                        connect(false);
                    }
                }, 1, TimeUnit.SECONDS);
            });
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!callbacksActive() || event.getAuthor().isBot() || event.getMessage().isWebhookMessage()) {
            return;
        }

        if (!event.isFromGuild()) {
            if (linkService != null) {
                String code = event.getMessage().getContentRaw().trim();
                if (linkService.verifyCode(code, event.getAuthor().getIdLong())) {
                    event.getChannel().sendMessage("¡Cuenta vinculada exitosamente con Minecraft!").queue();
                } else {
                    event.getChannel().sendMessage("Código inválido o expirado. Genera uno nuevo en Minecraft con `/linkeardiscord`.").queue();
                }
            }
            return;
        }

        if (!inboundBridgeEnabled || event.getGuild().getIdLong() != settings.guildId()
            || event.getChannel().getIdLong() != settings.bridgeChannelId()) {
            return;
        }
        StringBuilder content = new StringBuilder(event.getMessage().getContentDisplay());
        for (var attachment : event.getMessage().getAttachments()) {
            if (!content.isEmpty()) {
                content.append(' ');
            }
            content.append(attachment.getUrl());
        }
        if (content.isEmpty()) {
            return;
        }
        String safe = emojiAliases.replace(sanitizeMentions(content.toString()));
        runMainThread(() -> inboundBridge.accept(event.getAuthor().getName(), safe));
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!callbacksActive() || !"discordadmin".equals(event.getName())
            || !"field".equals(event.getFocusedOption().getName())) {
            return;
        }
        String query = event.getFocusedOption().getValue().toLowerCase(java.util.Locale.ROOT);
        List<Command.Choice> choices = DiscordFieldValidator.FIELDS.stream()
            .filter(field -> field.contains(query))
            .sorted()
            .limit(25)
            .map(field -> new Command.Choice(field, field))
            .toList();
        event.replyChoices(choices).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!callbacksActive()) {
            return;
        }
        if ("discordadmin".equals(event.getName())) {
            handleSetFields(event);
        } else if ("discordcommand".equals(event.getName())) {
            handleRemoteCommand(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!callbacksActive()) {
            return;
        }
        String componentId = event.getComponentId();
        if (componentId.startsWith(LOGIN_APPROVE_PREFIX) || componentId.startsWith(LOGIN_DENY_PREFIX)) {
            boolean approved = componentId.startsWith(LOGIN_APPROVE_PREFIX);
            String requestId = componentId.substring(componentId.lastIndexOf(':') + 1);
            LoginApprovalHandler handler = loginApprovalHandler;
            String reply = handler == null
                ? "El sistema de login no está disponible ahora."
                : handler.onDecision(requestId, event.getUser().getIdLong(), approved);
            event.editMessage(reply).setComponents().queue();
            return;
        }
        if (!(componentId.startsWith("bigcasares:confirm:") || componentId.startsWith("bigcasares:reject:"))) {
            return;
        }
        String id = componentId.substring(componentId.lastIndexOf(':') + 1);
        if (componentId.contains(":reject:")) {
            boolean rejected = commandPolicy.reject(id, event.getUser().getId());
            event.editMessage(rejected ? "Comando rechazado." : "Esta confirmación ya no es válida.")
                .setComponents().queue();
            audit.publish(AuditEvent.system(AuditSeverity.WARNING, "discord", "remote-command-rejected", "Confirmación rechazada"));
            return;
        }
        commandPolicy.confirm(id, event.getUser().getId(), Instant.now()).ifPresentOrElse(command -> {
            event.editMessage("Confirmado. Ejecutando…").setComponents().queue();
            auditRemote("remote-command-confirmed", command, AuditSeverity.WARNING);
            executeRemote(command, event.getHook());
        }, () -> event.editMessage("Esta confirmación venció o pertenece a otro usuario.").setComponents().queue());
    }

    private void registerCommands(Guild guild) {
        guild.updateCommands().addCommands(
            Commands.slash("discordadmin", "Administra la presentación pública")
                .addSubcommands(new SubcommandData("setfields", "Edita un campo administrado")
                    .addOptions(
                        new OptionData(OptionType.STRING, "field", "Campo predefinido", true, true),
                        new OptionData(OptionType.STRING, "value", "Nuevo valor", true)
                    )),
            Commands.slash("discordcommand", "Ejecuta un comando autorizado en la consola")
                .addOption(OptionType.STRING, "command", "Comando sin la barra inicial", true)
        ).queue();
    }

    private void handleSetFields(SlashCommandInteractionEvent event) {
        if (!authorized(event) || !"setfields".equals(event.getSubcommandName())) {
            reject(event, "No estás autorizado para editar los embeds.");
            return;
        }
        String field = event.getOption("field").getAsString();
        String rawValue = event.getOption("value").getAsString();
        event.deferReply(true).queue(hook -> runMainThread(() -> {
            try {
                String value = DiscordFieldValidator.validate(field, rawValue);
                plugin.getConfig().set("discord-integration." + field, value);
                plugin.saveConfig();
                if (field.startsWith("servidor.")) {
                    ensureServerMessage();
                } else {
                    ensurePlayerMessage();
                }
                hook.editOriginal("Campo `" + field + "` actualizado.").queue();
                audit.publish(AuditEvent.system(AuditSeverity.INFO, "discord", "embed-field-updated", field));
            } catch (IllegalArgumentException ex) {
                hook.editOriginal("Valor rechazado: " + ex.getMessage()).queue();
                audit.publish(AuditEvent.system(AuditSeverity.WARNING, "discord", "embed-field-rejected", field));
            }
        }));
    }

    private void handleRemoteCommand(SlashCommandInteractionEvent event) {
        if (!authorized(event)) {
            reject(event, "No estás autorizado para ejecutar comandos.");
            return;
        }
        String command = event.getOption("command").getAsString().trim();
        if (commandPolicy.requiresConfirmation(command)) {
            String id = commandPolicy.createConfirmation(event.getUser().getId(), command, Instant.now());
            event.reply("Este comando puede cambiar autoridad, acceso o datos. Confirmalo dentro de 30 segundos.")
                .setEphemeral(true)
                .addComponents(ActionRow.of(
                    Button.danger("bigcasares:confirm:" + id, "Confirmar"),
                    Button.secondary("bigcasares:reject:" + id, "Cancelar")
                )).queue();
            auditRemote("remote-command-awaiting-confirmation", command, AuditSeverity.WARNING);
            return;
        }
        event.deferReply(true).queue(hook -> executeRemote(command, hook));
    }

    private void executeRemote(String command, InteractionHook hook) {
        runMainThread(() -> {
            CapturingLogHandler capture = new CapturingLogHandler();
            Logger root = Logger.getLogger("");
            root.addHandler(capture);
            boolean success;
            try {
                success = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            } catch (RuntimeException ex) {
                success = false;
                capture.lines.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } finally {
                root.removeHandler(capture);
            }
            List<String> chunks = outputChunks(capture.lines, success);
            hook.editOriginal(chunks.getFirst()).queue();
            for (int index = 1; index < chunks.size(); index++) {
                hook.sendMessage(chunks.get(index)).setEphemeral(true).queue();
            }
            auditRemote(success ? "remote-command-success" : "remote-command-failed", command,
                success ? AuditSeverity.INFO : AuditSeverity.SEVERE);
        });
    }

    private boolean authorized(SlashCommandInteractionEvent event) {
        return event.isFromGuild() && event.getGuild().getIdLong() == settings.guildId()
            && event.getChannel().getIdLong() == settings.adminChannelId()
            && event.getUser().getIdLong() == settings.authorizedUserId();
    }

    private void reject(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
        audit.publish(AuditEvent.system(AuditSeverity.WARNING, "discord", "administration-rejected", message));
    }

    private void auditRemote(String action, String command, AuditSeverity severity) {
        audit.publish(AuditEvent.system(severity, "discord", action, AuditRedactor.redact(command)));
    }

    private void ensureServerMessage() {
        TextChannel channel = publicChannel;
        if (channel == null) {
            return;
        }
        MessageEmbed embed = new DiscordEmbedFactory(plugin.getConfig()).server(serverOnline);
        ensureManagedMessage(channel, messageState.serverMessageId(), embed, id -> updateState(id, messageState.playerMessageId()));
    }

    private void ensurePlayerMessage() {
        TextChannel channel = publicChannel;
        if (channel == null) {
            return;
        }
        MessageEmbed embed = new DiscordEmbedFactory(plugin.getConfig()).players(visiblePlayers);
        ensureManagedMessage(channel, messageState.playerMessageId(), embed, id -> updateState(messageState.serverMessageId(), id));
    }

    private void ensureManagedMessage(TextChannel channel, long messageId, MessageEmbed embed, java.util.function.LongConsumer created) {
        if (!callbacksActive()) {
            return;
        }
        java.util.function.LongConsumer guardedCreated = id -> {
            if (callbacksActive()) {
                created.accept(id);
            }
        };
        if (messageId <= 0) {
            channel.sendMessageEmbeds(embed).queue(
                message -> guardedCreated.accept(message.getIdLong()), this::managedMessageFailure);
            return;
        }
        channel.retrieveMessageById(messageId).queue(
            message -> message.editMessageEmbeds(embed).queue(null, this::managedMessageFailure),
            failure -> {
                if (callbacksActive()) {
                    channel.sendMessageEmbeds(embed).queue(
                        message -> guardedCreated.accept(message.getIdLong()), this::managedMessageFailure);
                }
            }
        );
    }

    private synchronized void updateState(long serverId, long playerId) {
        messageState = new DiscordMessageState(serverId, playerId);
        stateStorage.save(messageState);
    }

    private void managedMessageFailure(Throwable failure) {
        if (callbacksActive()) {
            audit.publish(AuditEvent.system(
                AuditSeverity.WARNING, "discord", "managed-message-failed", failure.getMessage()));
        }
    }

    private synchronized void coalesce(AuditEvent event) {
        String fingerprint = event.severity() + "|" + event.category() + "|" + event.action() + "|" + event.message();
        PendingAudit existing = pendingWarnings.get(fingerprint);
        if (existing != null) {
            pendingWarnings.put(fingerprint, new PendingAudit(existing.event(), existing.count() + 1));
            return;
        }
        pendingWarnings.put(fingerprint, new PendingAudit(event, 1));
        coalescer.schedule(() -> flushPending(fingerprint), 5, TimeUnit.SECONDS);
    }

    private void flushPending(String fingerprint) {
        if (!callbacksActive()) {
            return;
        }
        PendingAudit pending;
        synchronized (this) {
            pending = pendingWarnings.remove(fingerprint);
        }
        if (pending != null) {
            deliverOrQueue(pending.event(), pending.count());
        }
    }

    private synchronized void deliverOrQueue(AuditEvent event, int duplicates) {
        TextChannel channel = logChannel;
        if (!healthy || channel == null) {
            if (outageQueue.size() >= MAX_OUTAGE_EVENTS) {
                outageQueue.removeFirst();
                plugin.getLogger().warning("[DiscordQueue] Cola llena; se descartó el evento privado más antiguo.");
            }
            outageQueue.addLast(event);
            return;
        }
        MessageEmbed embed = new DiscordEmbedFactory(plugin.getConfig()).audit(event, duplicates);
        channel.sendMessageEmbeds(embed).queue(null, failure -> {
            if (!callbacksActive()) {
                return;
            }
            synchronized (JdaDiscordGateway.this) {
                healthy = false;
                if (outageQueue.size() >= MAX_OUTAGE_EVENTS) {
                    outageQueue.removeFirst();
                }
                outageQueue.addLast(event);
            }
        });
    }

    private synchronized void flushOutageQueue() {
        while (!outageQueue.isEmpty() && healthy) {
            AuditEvent event = outageQueue.removeFirst();
            deliverOrQueue(event, 1);
        }
    }

    private boolean callbacksActive() {
        return !stopping && generationActive.getAsBoolean();
    }

    private void runMainThread(Runnable callback) {
        if (!callbacksActive()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (callbacksActive()) {
                callback.run();
            }
        });
    }

    private List<String> outputChunks(List<String> lines, boolean success) {
        String output = lines.isEmpty()
            ? (success ? "Comando ejecutado correctamente (sin salida capturada)." : "El comando falló sin salida capturada.")
            : AuditRedactor.redact(String.join("\n", lines));
        List<String> chunks = new ArrayList<>();
        int offset = 0;
        while (offset < output.length() && chunks.size() < 5) {
            int end = Math.min(offset + 1900, output.length());
            chunks.add("```\n" + output.substring(offset, end) + "\n```");
            offset = end;
        }
        if (offset < output.length()) {
            int last = chunks.size() - 1;
            chunks.set(last, chunks.get(last).substring(0, Math.min(chunks.get(last).length(), 1850)) + "\n… salida truncada\n```");
        }
        return chunks;
    }

    private String sanitizeUsername(String value) {
        String safe = value == null ? "Jugador" : value.replaceAll("[^\\p{L}\\p{N}_. -]", "").trim();
        if (safe.isBlank() || "discord".equalsIgnoreCase(safe) || "clyde".equalsIgnoreCase(safe)) {
            safe = "Jugador de Minecraft";
        }
        return safe.length() <= 80 ? safe : safe.substring(0, 80);
    }

    private String sanitizeMentions(String value) {
        return value.replace("@everyone", "@\u200Beveryone").replace("@here", "@\u200Bhere");
    }

    private String json(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(character < 32 ? " " : character);
            }
        }
        return escaped.toString();
    }

    private record PendingAudit(AuditEvent event, int count) {
    }

    private static final class CapturingLogHandler extends Handler {
        private final List<String> lines = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getMessage() != null) {
                lines.add(record.getMessage());
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
