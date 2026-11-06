package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.concurrent.*;
import java.net.InetSocketAddress;
import java.util.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImagePolicy;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.CloseControl;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.PassiveHudState;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.VisualMenuCatalog;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.*;
import java.math.BigDecimal;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.compat.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.Action;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import org.bukkit.plugin.java.JavaPlugin;

public final class TideframeEngine implements Listener,TideframeApi {
    private final JavaPlugin host;
    private final dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope scope =
        new dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope();
    private final dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations registrations;

    /** The engine's settings: config.yml of BigCasares, section tideframe-ui.motor. */
    private final EngineConfig engineConfig;

    public TideframeEngine(JavaPlugin host) {
        this.host = Objects.requireNonNull(host);
        this.registrations = new dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations(host, scope);
        this.engineConfig = new EngineConfig(host.getDataFolder().toPath().resolve("config.yml"), () -> host.getResource("config.yml"));
    }

    public java.io.File getDataFolder() { return new java.io.File(host.getDataFolder(), "tideframe/engine"); }
    private org.bukkit.Server getServer() { return host.getServer(); }
    private java.util.logging.Logger getLogger() { return host.getLogger(); }
    private java.io.InputStream getResource(String path) { return TideframeResources.open(path); }
    private void registerListener(Listener listener) {
        registrations.registerListener("engine-listener-" + listener.getClass().getName(), listener);
    }

    public boolean sendsOwnPack() { return !stopping && pack != null && sendPack; }
    public boolean isOwnPackLoaded(Player player) { return ownAccepted.contains(player.getUniqueId()); }

    public void close() {
        try { onDisable(); }
        finally { scope.close(); io.shutdownNow(); packHttp.shutdownNow(); }
    }
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64),r -> { var t = new Thread(r,"Tideframe-worker"); t.setDaemon(true); return t; });
    // Pack downloads get their own threads: slow clients must never stall screen I/O or pack building.
    private final ThreadPoolExecutor packHttp = new ThreadPoolExecutor(4,4,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32),r -> { var t = new Thread(r,"Tideframe-pack-http"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardPolicy());
    private volatile PluginSettings settings;
    private volatile SoundSettings sounds=SoundSettings.defaults();
    private final Map<UUID,Double> sensitivities=new ConcurrentHashMap<>();
    private final Set<UUID> debugging=ConcurrentHashMap.newKeySet();
    private final LivePlaceholders placeholders=new LivePlaceholders();
    private static final NamespacedKey SENSITIVITY_KEY=new NamespacedKey("tideframe","sensitivity");
    /** Marks an item that opens a screen (config.yml open.items); the value is the screen id. */
    private static final NamespacedKey OPEN_ITEM_KEY=new NamespacedKey("tideframe","open");
    private volatile OpenTriggers openTriggers=OpenTriggers.NONE;
    private final OpenTriggers.Visits zoneVisits=new OpenTriggers.Visits();
    private final ReopenAfterReload reopening=new ReopenAfterReload();
    private volatile boolean reopenAfterReload=true;
    private SchedulerBridge scheduler;
    private volatile PackGenerator.Artifact pack;
    private volatile ImageGlyphs images=ImageGlyphs.empty();
    private volatile HudAssets hudAssets=HudAssets.empty();
    private volatile PackService http;
    private volatile boolean stopping;
    /** True while plugins/BigCasares/tideframe/engine/screens/menu.json is still the untouched built-in placeholder. */
    private volatile boolean menuIsBuiltIn;
    private final Object lifecycle=new Object();
    private final Map<String,Screen> screens=new ConcurrentHashMap<>(Screen.demos());
    private final Map<UUID,String> activeScreens=new ConcurrentHashMap<>();
    private ScreenStore store;
    private SerialScreenStore repository;
    private final dev.linqfy.bigCasares.modules.tideframe.engine.core.OperationGate operations=new dev.linqfy.bigCasares.modules.tideframe.engine.core.OperationGate();
    private final BedrockBridge bedrock=new BedrockBridge();
    private final ItemsAdderBridge itemsAdder=new ItemsAdderBridge();
    private FallbackMenu fallback;
    private final Map<UUID,UiSession> sessions=new ConcurrentHashMap<>();
    private final Map<UUID,ViewportFit> viewports=new ConcurrentHashMap<>();
    private final Map<UUID,PassiveHudSession> overlays=new ConcurrentHashMap<>();
    private final Map<UUID,DemoProgram> programs=new ConcurrentHashMap<>();
    private static final class DemoProgram {
        final SequenceGame sequence;final RouletteSpin roulette;int ticks;
        DemoProgram(SequenceGame value){sequence=value;roulette=null;}
        DemoProgram(RouletteSpin value){sequence=null;roulette=value;}
        String frame(){return sequence!=null?sequence.frameId():roulette.frameId();}
        String tick(){return sequence!=null?sequence.tick():roulette.tick();}
    }
    private KeyInput keyInput;
    /** Screen logic (schema 9): runtimes, variables, history, lists and the game side of actions. */
    private LogicController logic;
    private ScreenCommands screenCommands;
    /** Players whose open-on-join screen already opened this login. */
    private final Set<UUID> joinedOpen=ConcurrentHashMap.newKeySet();
    /** Boss-fight key prompts: transparent overlay over the world, optionally bound to a real boss entity. */
    private final BossChallenges challenges=new BossChallenges(new BossChallenges.Hooks(
        id->screens.get(id),this::ensurePassive,this::closePassive,player->closeMenu(player,TideframeCloseEvent.Reason.REPLACED),
        (player,sound)->gameSound(player,sound[0],Float.parseFloat(sound[1])),
        player->scheduler.repeat(player,()->challenges().tick(player)),
        event->getServer().getPluginManager().callEvent(event),()->this.bossFight));
    /** config.yml → display.cursor-size (0.25-1.5 of the original cursor; default 0.4). */
    private double cursorScale(){
        var config=engineConfig.read();
        double value=config.getDouble("display.cursor-size",HudAssets.DEFAULT_CURSOR_SCALE);
        if(!(value>=.25&&value<=1.5)){getLogger().warning("display.cursor-size must be 0.25 to 1.5; using "+HudAssets.DEFAULT_CURSOR_SCALE);return HudAssets.DEFAULT_CURSOR_SCALE;}
        return value;
    }
    /** config.yml → boss-fight (validated); the built-in look when absent or invalid. */
    private volatile dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightSettings bossFight=dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightSettings.defaults();
    private void loadBossFight(){
        try{bossFight=BossFightConfig.load(engineConfig.read());}
        catch(IllegalArgumentException invalid){bossFight=dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightSettings.defaults();getLogger().warning(invalid.getMessage()+"; using the default boss fight look.");}
    }
    /** The engine once kept its own config.yml in its folder; it is no longer read, and is left as it is. */
    private void reportOldConfig(){
        if(java.nio.file.Files.isRegularFile(getDataFolder().toPath().resolve("config.yml")))
            getLogger().warning("plugins/BigCasares/tideframe/engine/config.yml is no longer read: the engine settings are in config.yml, section "
                +EngineConfig.PATH+". Copy any value you changed there; the old file can be deleted.");
    }
    private BossChallenges challenges(){return challenges;}
    private void gameSound(Player player,String sound,float pitch){
        var current=sounds;
        if(current.enabled())player.playSound(player.getLocation(),sound,SoundCategory.MASTER,Math.max(.4f,current.volume()),pitch);
    }
    /** /tideframe challenge: the living mob you look at (within 24 blocks) becomes the boss; otherwise a practice boss. */
    private void startChallenge(Player player){
        if(!accepted.contains(player.getUniqueId())){player.sendMessage("Accept the Tideframe resource pack first.");return;}
        org.bukkit.entity.LivingEntity boss=null;
        var target=player.getTargetEntity(24);
        if(target instanceof org.bukkit.entity.LivingEntity living&&!(target instanceof Player)&&!living.isDead())boss=living;
        challenges.start(player,boss,null);
        player.sendMessage(boss==null?"Practice boss: press the key shown before the white bar runs out.":"Boss fight against "+BossChallenges.displayName(boss)+": hits damage it, misses hurt you.");
    }
    private Map<String,String> liveValues(Player player,Screen shown){
        var values=logic==null?LivePlaceholders.Values.NONE:logic.liveValues(player,shown);
        if(logic!=null)logic.refresh(player,shown);
        return placeholders.resolve(player,shown,this::apiBalance,challenges.values(player),values);
    }

    private final Set<UUID> accepted=ConcurrentHashMap.newKeySet();
    private final Set<UUID> ownAccepted=ConcurrentHashMap.newKeySet();
    private static final UUID PACK_ID=UUID.fromString("2c53f6de-8b88-4ab0-a44c-17d6535c11db");
    public void onEnable() {
        try {
            CameraPackets.initialize(Bukkit.getMinecraftVersion());
            getLogger().info("TIDEFRAME_CAMERA_BINDINGS_READY protocol=776 playerProbe=false");
        } catch(RuntimeException unavailable) {getLogger().warning(unavailable.getMessage());}
        scheduler = new SchedulerBridge(host, scope);
        fallback=new FallbackMenu(scheduler);
        logic=new LogicController(new LogicController.Plugin(){
            public void open(Player player,String screenId,boolean back){TideframeEngine.this.open(player,screenId,back);}
            public void close(Player player,TideframeCloseEvent.Reason reason){requestClose(player,reason);}
            public UiSession session(Player player){return sessions.get(player.getUniqueId());}
            public PassiveHudSession overlay(Player player){var view=overlays.get(player.getUniqueId());return view==null||view.closed()?null:view;}
            public String placeholders(Player player,String text){return placeholders.apply(player,text,TideframeEngine.this::apiBalance,challenges.values(player));}
            public void legacy(Player player,String action){action(player,action);}
            public SchedulerBridge scheduler(){return scheduler;}
            public java.util.logging.Logger logger(){return getLogger();}
            public void callEvent(Event event){getServer().getPluginManager().callEvent(event);}
            public void connect(Player player,String server){
                // Close first (camera and title restored), then hand the player to the proxy on their next tick.
                requestClose(player,TideframeCloseEvent.Reason.ACTION);
                scheduler.later(player,1,()->{if(player.isOnline())player.sendPluginMessage(host,ProxyConnect.CHANNEL,ProxyConnect.connect(server));});
            }
            public HudAssets assets(){return hudAssets;}
            public void hud(Player player,String screenId,boolean add){hudPage(player,screenId,add,true);}
            public void toast(Player player,String screenId,double seconds,Map<String,String> values){queueToast(player,screenId,seconds,values);}
        });
        getServer().getMessenger().registerOutgoingPluginChannel(host,ProxyConnect.CHANNEL);
        scope.register("proxy-channel", () -> getServer().getMessenger().unregisterOutgoingPluginChannel(host, ProxyConnect.CHANNEL));
        screenCommands=new ScreenCommands(host,scheduler,(player,id)->scheduler.entity(player,()->open(player,id)));
        registerListener(this);
        registerListener(fallback);
        registerListener(new MenuMovementGuard(activeScreens,player->closeMenu(player,TideframeCloseEvent.Reason.INVALID)));
        registrations.bindCommand("tideframe-command", Objects.requireNonNull(host.getCommand("tideframe")), this::command, this::complete);
        // No Bukkit service: the engine belongs to BigCasares' tideframe-ui module, which holds it directly.
        keyInput=new KeyInput((player,key)->{
            // Movement keys go to an open menu's keybinds; otherwise to a running boss challenge.
            if(sessions.containsKey(player.getUniqueId())){if(key!=dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.KeyChallenge.Key.CLICK)logic.key(player,key.label);}
            else challenges.key(player,key);
        },player->challenges.active(player)||sessions.containsKey(player.getUniqueId()));
        boolean preciseKeys=keyInput.register(host);
        getLogger().info("TIDEFRAME_KEY_INPUT "+(preciseKeys?"native":"movement-fallback"));
        io.execute(() -> {
            try {
                reportOldConfig();
                settings = PluginSettings.load(engineConfig.read());
                try {sounds=SoundSettings.load(engineConfig.read());}
                catch(IllegalArgumentException invalid){getLogger().warning(invalid.getMessage()+"; using default sounds.");}
                loadBossFight();
                store=new ScreenStore(getDataFolder().toPath().resolve("screens"));
                repository=new SerialScreenStore(store);
                if (stopping) { repository.close(); return; }
                var built=buildRuntime();
                long packBytes=built.packBytes();
                synchronized(lifecycle) {
                    if (stopping) { repository.close();return; }
                    publishRuntime(built);
                    var service=new PackService(new InetSocketAddress(settings.bind(),settings.port()),packHttp);
                    service.start(pack);
                    http=service;
                }
                scheduler.global(() -> {
                    getLogger().info("TIDEFRAME_CORE_READY version=" + getServer().getBukkitVersion()
                        + " folia=" + PlatformProbe.folia() + " java=" + java.lang.Runtime.version().feature());
                    getLogger().info("TIDEFRAME_RUNTIME_READY screens="+screens.size()+" schema="+ScreenCodec.LATEST+" api=true passive=true selfInput="+CameraPackets.available()
                        +" cursorFastPath="+CameraPackets.rotationFastPath()+" keys="+CameraPackets.keysAvailable()+" dialogs="+logic.dialogsAvailable()+" packBytes="+packBytes+" demoGames="+demoGames);
                    if(demoGames&&packBytes>50L*1024*1024)getLogger().info("The demo games are most of the "+packBytes/1048576+" MiB resource pack; set tideframe-ui.motor.demos.games: false in config.yml for a pack of a few MiB.");
                    registerScreenCommands();
                    reportModel();
                    for (var player:Bukkit.getOnlinePlayers()) scheduler.entity(player,() -> {restoreDisplay(player);offer(player);});
                });
                startWatcher();
                startOpenWatcher();
            } catch (Exception | LinkageError e) {
                getLogger().log(java.util.logging.Level.SEVERE,"Tideframe failed to start; commands stay disabled until the cause below is fixed and the server restarts.",e);
                editorFailed(String.valueOf(e.getMessage()));
            }
        });
    }
    /** Everything derived from the screen JSON files; built on a worker, then published at once. */
    private record Runtime(Map<String,Screen> screens,ImageGlyphs images,HudAssets hud,PackGenerator.Artifact pack,long packBytes,boolean menuBuiltIn,String fingerprint,String configFingerprint,
                           Map<String,List<String>> problems,Map<String,Long> stamps) { }
    private volatile String builtConfigFingerprint="";
    private volatile dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenWatcher configWatcher;
    /** Fingerprint of the screens folder the published runtime was built from (see ScreenWatcher). */
    private volatile String builtFingerprint="";
    private volatile dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenWatcher watcher;
    private final java.util.concurrent.atomic.AtomicBoolean pendingAutoReload=new java.util.concurrent.atomic.AtomicBoolean();
    private java.nio.file.Path screensFolder(){return getDataFolder().toPath().resolve("screens");}
    /** auto-reload: saving a JSON in plugins/BigCasares/tideframe/engine/screens (or Publish in the editor) rebuilds the pack by itself. */
    private void startWatcher(){
        synchronized (lifecycle) {
        if (stopping) return;
        var config=engineConfig.read();
        if(!config.getBoolean("editor.auto-reload",true))return;
        try {
            watcher=registrations.ownCloseable("engine-screens-watcher", new dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenWatcher(screensFolder(),config.getLong("editor.auto-reload-delay-ms",1500),()->builtFingerprint,
                changed->scheduler.global(this::autoReload)));
            // Saving BigCasares' config.yml applies the engine section (boss fight look, sizes, sounds) too; edits to
            // other sections leave its fingerprint as it was and reload nothing.
            configWatcher=registrations.ownCloseable("engine-config-watcher", new dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenWatcher(host.getDataFolder().toPath(),"config.yml",config.getLong("editor.auto-reload-delay-ms",1500),
                engineConfig::fingerprint,()->builtConfigFingerprint,changed->scheduler.global(this::autoReload)));
            getLogger().info("TIDEFRAME_AUTO_RELOAD watching="+screensFolder()+" and config.yml ("+EngineConfig.PATH+")");
        } catch(java.io.IOException unavailable){getLogger().warning("Auto-reload is unavailable: "+unavailable.getMessage());}
        }
    }
    // ----- the web editor (EditorBridge) -----
    private volatile dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenWatcher openWatcher;
    private volatile String handledOpenFingerprint="";
    private volatile String lastOpenAnswer=null;
    private record LoadReport(boolean ok,String error,java.util.SortedSet<String> screens,Map<String,List<String>> problems,Map<String,Long> stamps){}
    private volatile LoadReport lastLoad=new LoadReport(true,null,new TreeSet<>(),Map.of(),Map.of());
    private final Object editorStatusLock=new Object();
    private String editorStatusFailure;
    /** editor-status.json once a load is live: its screens, their warnings and file times, for the editor's "Publicar". */
    private void editorLoaded(Collection<String> loaded,Map<String,List<String>> problems,Map<String,Long> stamps){
        lastLoad=new LoadReport(true,null,new TreeSet<>(loaded),problems,stamps);
        writeEditorStatus();
    }
    /** A failed load keeps the previous screens active, so their report stays, now with the error. */
    private void editorFailed(String error){
        var previous=lastLoad;
        lastLoad=new LoadReport(false,error,previous.screens(),previous.problems(),previous.stamps());
        writeEditorStatus();
    }
    private void writeEditorStatus(){
        synchronized(editorStatusLock){
            var load=lastLoad;
            try{
                EditorBridge.writeStatus(getDataFolder().toPath(),EditorBridge.status(System.currentTimeMillis(),load.ok(),load.error(),load.screens(),load.problems(),load.stamps(),lastOpenAnswer));
                editorStatusFailure=null;
            }catch(java.io.IOException|RuntimeException failure){
                // Said once per cause: without this file the editor cannot tell when a published screen is live.
                if(!Objects.equals(editorStatusFailure,failure.getMessage()))getLogger().warning("editor-status.json could not be written: "+failure.getMessage());
                editorStatusFailure=failure.getMessage();
            }
        }
    }
    /** The editor's "Abrir en el juego": editor-open.json is read once, deleted, and its screen opened for the player. */
    private void startOpenWatcher(){
        synchronized (lifecycle) {
        if (stopping) return;
        var folder=getDataFolder().toPath();
        try{
            java.nio.file.Files.deleteIfExists(folder.resolve(EditorBridge.OPEN));
            handledOpenFingerprint=dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenWatcher.fingerprint(folder,EditorBridge.OPEN);
            openWatcher=registrations.ownCloseable("engine-open-watcher", new dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenWatcher(folder,EditorBridge.OPEN,200,()->handledOpenFingerprint,changed->scheduler.global(this::openFromEditor)));
        }catch(java.io.IOException unavailable){getLogger().warning("The editor's open-in-game requests are unavailable: "+unavailable.getMessage());}
        }
    }
    private void openFromEditor(){
        var folder=getDataFolder().toPath();var file=folder.resolve(EditorBridge.OPEN);
        String raw;
        try{raw=java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.UTF_8);java.nio.file.Files.deleteIfExists(file);}
        catch(java.io.IOException gone){return;}
        finally{handledOpenFingerprint=dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenWatcher.fingerprint(folder,EditorBridge.OPEN);}
        var request=EditorBridge.openRequest(raw);
        Player target=null;String answer;
        if(request==null)answer="invalid request";
        else if(!screens.containsKey(request.screen()))answer="unknown screen "+request.screen()+" (publish it first)";
        else{
            var online=Bukkit.getOnlinePlayers().stream().filter(candidate->candidate.hasPermission("tideframe.use")).toList();
            if(request.player()!=null)target=Bukkit.getPlayerExact(request.player());
            else if(online.size()==1)target=online.get(0);
            answer=target!=null?"opened "+request.screen()+" for "+target.getName()
                :request.player()!=null?"player "+request.player()+" is not online":online.isEmpty()?"no player is online":"several players are online: name one";
        }
        // After the request's id, so the editor tells its own answer from an older one.
        lastOpenAnswer=(request!=null&&request.request()!=null?request.request():"-")+" "+answer;
        getLogger().info("Editor open-in-game: "+answer);
        // The last load's report, now with this answer.
        writeEditorStatus();
        if(target!=null){var player=target;String screen=request.screen();scheduler.entity(player,()->open(player,screen));}
    }
    private void autoReload(){
        if(reloading.get()){pendingAutoReload.set(true);return;}
        // A change noticed while a reload was running may already be in that build: skip a second, identical reload
        // (players would download the same pack twice), unless an API call is waiting for one.
        boolean waiting;synchronized(reloadWaiters){waiting=!reloadWaiters.isEmpty();}
        if(!waiting&&dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenWatcher.fingerprint(screensFolder()).equals(builtFingerprint)
                &&engineConfig.fingerprint().equals(builtConfigFingerprint))return;
        getLogger().info("Screens or the tideframe-ui.motor settings changed; reloading automatically.");
        reload(Bukkit.getConsoleSender());
    }
    /** Worker thread only: parse/validate screens, bake HUD assets and generate the pack. Touches no live state. */
    private Runtime buildRuntime() throws Exception {
        var fresh=new TreeMap<String,Screen>(Screen.demos());
        for(String id:Screen.demos().keySet()) {
            try { fresh.put(id,store.load(id)); }
            catch(java.nio.file.NoSuchFileException e) { store.save(fresh.get(id)); }
        }
        // Taken before reading: a file saved during the build makes the fingerprints differ and reloads again.
        String fingerprint=dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenWatcher.fingerprint(screensFolder());
        String configFingerprint=engineConfig.fingerprint();
        // Each file's time, taken before reading it: the editor's Publish waits for the time of its own file (EditorBridge).
        var stamps=new TreeMap<String,Long>();
        for(String id:store.ids()){stamps.put(id,store.stamp(id));fresh.put(id,store.load(id));}
        // Operator JSON overrides are preserved, including manual GUI calibration.
        // Boss prompts follow config.yml (colours, layout, texts) unless a screen JSON with the same id replaces them.
        dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightScreens.build(bossFight).forEach(fresh::putIfAbsent);
        // The demo games are most of the pack (about 95 MB at the default sizes); servers can leave them out.
        boolean games=engineConfig.read().getBoolean("demos.games",true);
        demoGames=games;
        if(games)RuntimeDemoCatalog.screens().forEach((id,demo)->fresh.putIfAbsent(id,demoPresentation(demo)));
        fresh.putIfAbsent("visual_menu09",VisualMenuCatalog.screen());
        fresh.putIfAbsent(dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.LogicDemo.ID,dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.LogicDemo.screen());
        fresh.putIfAbsent(dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.ShowcaseDemo.ID,dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.ShowcaseDemo.screen());
        // The blur demo's full-screen photograph weighs about 14 MB of pack: it belongs with the demo games.
        if(games){fresh.putIfAbsent(dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.MediaDemo.ID,dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.MediaDemo.screen());copyDemoGif();}
        // experimental.mannequin-test: the 3D model proof of concept; its menu exists only with the option on.
        boolean models=engineConfig.read().getBoolean("experimental.mannequin-test",false);
        modelTest=models;
        if(models)fresh.putIfAbsent(dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.ModelDemo.ID,dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.ModelDemo.screen());
        boolean builtIn=Screen.demos().get("menu").equals(fresh.get("menu"));
        writeCatalog(fresh,store.ids());
        fresh.replaceAll((id,screen)->CloseControl.prepare(screen));
        var freshImages=ImageGlyphs.create(fresh.values().stream().flatMap(screen -> screen.elements().stream())
            .map(Screen.Element::image).filter(Objects::nonNull).toList());
        var problems=new TreeMap<String,List<String>>();
        for(var screen:fresh.values())for(String problem:screen.logicProblems()){getLogger().warning("Screen "+screen.id()+": "+problem);problems.computeIfAbsent(screen.id(),k->new ArrayList<>()).add(problem);}
        var frameSet=frameImages(fresh.values());
        // display.auto-fit: the pack carries a text shader that fits menus to the window (opt-in; needs a server that can switch the glyph shadow off).
        boolean wantsAutoFit=engineConfig.read().getBoolean("display.auto-fit",false);
        boolean autoFit=wantsAutoFit&&Shadow.available();
        if(wantsAutoFit&&!autoFit)getLogger().warning("display.auto-fit needs a server with text shadow components (Minecraft 26.x); it stays off.");
        if(autoFit)getLogger().info("TIDEFRAME_AUTO_FIT on reference="+HudAssets.REFERENCE_QUARTERS+"q: the pack carries the text shader; menus and HUD pages fit the window by themselves.");
        var freshHud=HudAssets.create(fresh.values(),settings.displaySizes(),cursorScale(),frameSet.images(),frameSet.intervals(),autoFit);
        var portraitFile=getDataFolder().toPath().resolve("boss-face.png");
        if(!java.nio.file.Files.exists(portraitFile,java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            try(var portraitResource=getResource("boss-face.png")) {
                if(portraitResource==null)throw new java.io.IOException("Bundled boss portrait is missing.");
                java.nio.file.Files.copy(portraitResource,portraitFile);
            }
        }
        dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset bossPortrait=null;
        try {bossPortrait=BossPortrait.load(getDataFolder().toPath());}
        catch(java.io.IOException invalid){getLogger().warning("Boss portrait rejected; using built-in geometric face: "+invalid.getMessage());}
        if(bossPortrait!=null)getLogger().info("TIDEFRAME_BOSS_PORTRAIT_READY width="+bossPortrait.width()
            +" height="+bossPortrait.height()+" sha256="+bossPortrait.sha256());
        // plugins/BigCasares/tideframe/engine/sounds: the operator's own Ogg Vorbis sounds, as tideframe:NAME.
        var sounds=dev.linqfy.bigCasares.modules.tideframe.engine.pack.CustomSounds.load(getDataFolder().toPath().resolve("sounds"));
        for(String problem:sounds.problems())getLogger().warning(problem);
        if(!sounds.sounds().isEmpty())getLogger().info("TIDEFRAME_SOUNDS "+sounds.sounds().stream().map(dev.linqfy.bigCasares.modules.tideframe.engine.pack.CustomSounds.Sound::id).toList());
        // config.yml boss-bar.style: the boss bar frame design baked into the pack.
        String bossStyle=engineConfig.read().getString("boss-bar.style","rose");
        if(!dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets.STYLES.contains(bossStyle)){getLogger().warning("config.yml tideframe-ui.motor.boss-bar.style must be one of "+dev.linqfy.bigCasares.modules.tideframe.engine.pack.BossBarAssets.STYLES+"; using rose.");bossStyle="rose";}
        var freshPack=PackGenerator.generate(getDataFolder().toPath().resolve("packs"),freshImages,freshHud,bossPortrait,sounds.sounds(),bossStyle);
        exportPack(freshPack.path());
        return new Runtime(fresh,freshImages,freshHud,freshPack,java.nio.file.Files.size(freshPack.path()),builtIn,fingerprint,configFingerprint,Map.copyOf(problems),Map.copyOf(stamps));
    }
    /**
     * Worker thread: plugins/BigCasares/tideframe/engine/MENUS.md, a read-only description of every menu for people and agents. Rewritten
     * only when it changes; a failure is logged and never stops the menus from loading.
     */
    private void writeCatalog(Map<String,Screen> all,Collection<String> fileIds){
        try{
            String text=dev.linqfy.bigCasares.modules.tideframe.engine.core.MenuCatalog.render(all,new TreeSet<>(fileIds),"0.23.0");
            var target=getDataFolder().toPath().resolve("MENUS.md");
            if(java.nio.file.Files.isRegularFile(target,java.nio.file.LinkOption.NOFOLLOW_LINKS)
                &&java.nio.file.Files.readString(target,java.nio.charset.StandardCharsets.UTF_8).equals(text))return;
            java.nio.file.Files.createDirectories(target.getParent());
            var temporary=java.nio.file.Files.createTempFile(target.getParent(),"MENUS",".tmp");
            try{
                java.nio.file.Files.writeString(temporary,text,java.nio.charset.StandardCharsets.UTF_8);
                java.nio.file.Files.move(temporary,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }finally{java.nio.file.Files.deleteIfExists(temporary);}
            getLogger().info("TIDEFRAME_MENUS_FILE "+target.getFileName()+" menus="+all.size());
        }catch(java.io.IOException|RuntimeException failure){getLogger().warning("MENUS.md could not be written: "+failure.getMessage());}
    }
    /** Menu size (quarters) for this player and screen: the fixed reference size when auto-fit scales it in the client. */
    private int quartersFor(Player player,Screen screen){
        return hudAssets.fitted(screen)?HudAssets.REFERENCE_QUARTERS:viewport(player).multiplier(screen,hudAssets.sizes());
    }
    private void publishRuntime(Runtime built){
        screens.keySet().retainAll(built.screens().keySet());screens.putAll(built.screens());
        loadActionSettings();
        images=built.images();hudAssets=built.hud();pack=built.pack();menuIsBuiltIn=built.menuBuiltIn();builtFingerprint=built.fingerprint();builtConfigFingerprint=built.configFingerprint();
        // Only now are the new screens live: the editor's "Abrir en el juego" can open them from here on.
        editorLoaded(built.screens().keySet(),built.problems(),built.stamps());
    }
    /** config.yml → actions: which root commands console actions may run. */
    /** config.yml editing.in-game: false (the default) keeps menus read-only in the game; they change by editing their files. */
    private volatile boolean inGameEditing;
    private void loadActionSettings(){
        var config=engineConfig.read();
        inGameEditing=config.getBoolean("editing.in-game",false);
        var savedNames=config.getStringList("variables.saved");
        for(String bad:SavedVariables.invalid(savedNames))getLogger().warning("config.yml tideframe-ui.motor.variables.saved: '"+bad+"' is not a variable name (a-z, 0-9 and _, up to 24 characters); ignored.");
        logic.saveVariables(new SavedVariables(savedNames));
        openTriggers=OpenTriggers.parse(config.getConfigurationSection("open"),problem->getLogger().warning("config.yml tideframe-ui.motor: "+problem));
        var huds=new java.util.LinkedHashMap<String,List<String>>();
        var worldsSection=config.getConfigurationSection("hud.worlds");
        if(worldsSection!=null)for(String world:worldsSection.getKeys(false)){
            var ids=new ArrayList<String>();
            for(String id:worldsSection.getStringList(world)){
                if(id.matches("[a-z][a-z0-9_-]{0,31}"))ids.add(id);
                else getLogger().warning("config.yml tideframe-ui.motor.hud.worlds."+world+": '"+id+"' is not a screen id; ignored.");
            }
            if(!ids.isEmpty())huds.put(world,List.copyOf(ids));
        }
        worldHuds=Map.copyOf(huds);
        bossFlash=config.getBoolean("boss-bar.flash",false);bossShake=config.getBoolean("boss-bar.shake",false);
        for(var view:overlays.values())view.setBossEffects(bossFlash,bossShake);
        reopenAfterReload=config.getBoolean("editor.reopen-after-reload",true);
        var allowed=config.isList("actions.console-commands")?config.getStringList("actions.console-commands"):List.of("*");
        logic.configure(allowed);
    }
    /** Global thread: /command for every screen whose logic names one. */
    private void registerScreenCommands(){
        var commands=new TreeMap<String,String>();
        for(var screen:screens.values()){
            String name=screen.logic().command();
            if(name==null)continue;
            var previous=commands.putIfAbsent(name,screen.id());
            if(previous!=null)getLogger().warning("Screens "+previous+" and "+screen.id()+" both use /"+name+"; "+previous+" keeps it.");
        }
        try{
            var refused=screenCommands.replace(commands);
            for(String name:refused)getLogger().warning("/"+name+" is taken by another plugin; use /tideframe:"+name+" or pick another command.");
            if(!commands.isEmpty())getLogger().info("TIDEFRAME_SCREEN_COMMANDS "+commands);
        }catch(RuntimeException failure){getLogger().warning("Screen commands could not be registered: "+failure.getMessage());}
    }
    /** Frames by folder, and the pace each GIF folder was sampled at. */
    private record FrameSet(Map<String,List<dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset>> images,Map<String,Integer> intervals){}
    /**
     * Worker thread: frames of animated elements, from plugins/BigCasares/tideframe/engine/images/FOLDER: its PNG files (sorted by name,
     * numbers in order), else the first GIF in it, else images/FOLDER.gif.
     */
    private FrameSet frameImages(Collection<Screen> all){
        // A GIF is sampled at the interval its first element asks for (0 = the GIF's own pace).
        var requested=new TreeMap<String,Integer>();
        for(var screen:all)for(var e:screen.elements())if(e.logic().frames()!=null)
            requested.putIfAbsent(e.logic().frames().folder(),e.logic().frames().intervalMs());
        var out=new HashMap<String,List<dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset>>();
        var intervals=new HashMap<String,Integer>();
        var root=getDataFolder().toPath().resolve("images").toAbsolutePath().normalize();
        for(String folder:requested.keySet()){
            var dir=root.resolve(folder).normalize();
            var gifFile=root.resolve(folder+".gif").normalize();
            boolean isDir=dir.startsWith(root)&&java.nio.file.Files.isDirectory(dir,java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if(!isDir){
                if(gifFile.startsWith(root)&&java.nio.file.Files.isRegularFile(gifFile,java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    loadGif(gifFile,root,folder,requested.get(folder),out,intervals);
                else getLogger().warning("Animation folder images/"+folder+" (or images/"+folder+".gif) is missing; the element shows its own image instead.");
                continue;
            }
            try(var files=java.nio.file.Files.list(dir)){
                var listed=files.filter(f->java.nio.file.Files.isRegularFile(f,java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(f->naturalKey(f.getFileName().toString()))).toList();
                var pngs=listed.stream().filter(f->f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).toList();
                if(pngs.isEmpty()){
                    var gif=listed.stream().filter(f->f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif")).findFirst();
                    if(gif.isPresent())loadGif(gif.get(),root,folder,requested.get(folder),out,intervals);
                    else getLogger().warning("Animation folder images/"+folder+" has no PNG or GIF; the element shows its own image instead.");
                    continue;
                }
                if(pngs.size()>HudAssets.MAX_FRAMES){getLogger().warning("images/"+folder+" has "+pngs.size()+" frames; only the first "+HudAssets.MAX_FRAMES+" are used.");pngs=pngs.subList(0,HudAssets.MAX_FRAMES);}
                var frames=new ArrayList<dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset>();
                for(var png:pngs){
                    try{frames.add(dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset.fromPng(java.nio.file.Files.readAllBytes(png)));}
                    catch(IllegalArgumentException invalid){getLogger().warning("Frame "+png.getFileName()+" skipped: "+invalid.getMessage());}
                }
                if(!frames.isEmpty())out.put(folder,List.copyOf(frames));
            }catch(java.io.IOException unreadable){getLogger().warning("Animation folder images/"+folder+" unreadable: "+unreadable.getMessage());}
        }
        return new FrameSet(out,intervals);
    }
    /** Worker thread: images/demo_estrella.gif for /tideframe difuminados, from the plugin's own art; an existing file is kept. */
    private void copyDemoGif(){
        var target=getDataFolder().toPath().resolve("images").resolve(dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.MediaDemo.GIF_FOLDER+".gif");
        if(java.nio.file.Files.exists(target,java.nio.file.LinkOption.NOFOLLOW_LINKS))return;
        try(var gif=getResource(dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.MediaDemo.GIF_RESOURCE)){
            if(gif==null){getLogger().warning("Bundled demo GIF is missing.");return;}
            java.nio.file.Files.createDirectories(target.getParent());
            java.nio.file.Files.copy(gif,target);
        }catch(java.io.IOException failure){getLogger().warning("Demo GIF could not be copied: "+failure.getMessage());}
    }
    /** Decodes one GIF into the frames of {@code folder}; a bad GIF is reported and the element keeps its own image. */
    private void loadGif(java.nio.file.Path file,java.nio.file.Path root,String folder,int requestedInterval,
                         Map<String,List<dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset>> out,Map<String,Integer> intervals){
        String name=root.relativize(file).toString().replace('\\','/');
        try{
            if(java.nio.file.Files.size(file)>dev.linqfy.bigCasares.modules.tideframe.engine.core.GifFrames.MAX_GIF_BYTES){
                getLogger().warning("images/"+name+" is larger than "+dev.linqfy.bigCasares.modules.tideframe.engine.core.GifFrames.MAX_GIF_BYTES/1_048_576+" MiB; the element shows its own image instead.");return;
            }
            var gif=dev.linqfy.bigCasares.modules.tideframe.engine.core.GifFrames.decode(java.nio.file.Files.readAllBytes(file),requestedInterval,HudAssets.MAX_FRAMES);
            out.put(folder,gif.frames());intervals.put(folder,gif.intervalMs());
            getLogger().info("TIDEFRAME_GIF images/"+name+" frames="+gif.frames().size()+" distinct="+gif.distinct()+" interval-ms="+gif.intervalMs()
                +" gif-frames="+gif.sourceFrames()+" duration-ms="+gif.durationMs());
            if(gif.truncated())getLogger().warning("images/"+name+" has more than "+dev.linqfy.bigCasares.modules.tideframe.engine.core.GifFrames.MAX_SOURCE_FRAMES+" frames; the rest are ignored.");
            if(requestedInterval!=0&&gif.intervalMs()!=requestedInterval)
                getLogger().warning("images/"+name+" is too long for "+HudAssets.MAX_FRAMES+" frames every "+requestedInterval+" ms; it plays one frame every "+gif.intervalMs()+" ms instead.");
        }catch(java.io.IOException|IllegalArgumentException invalid){
            getLogger().warning("GIF images/"+name+" skipped: "+invalid.getMessage());
        }
    }
    // ----- Ultimate UI import -----
    private java.nio.file.Path ultimateContents(){return host.getDataFolder().toPath().getParent().resolve("UltimateUI").resolve("contents");}
    /** Pages to import: plugins/UltimateUI/contents/pages, else plugins/BigCasares/tideframe/engine/import. */
    private java.nio.file.Path importFolder(){
        var pages=ultimateContents().resolve("pages");
        return java.nio.file.Files.isDirectory(pages)?pages:getDataFolder().toPath().resolve("import");
    }
    private List<String> importablePages(){
        var options=new ArrayList<String>(List.of("all"));
        try(var files=java.nio.file.Files.list(importFolder())){
            files.map(f->f.getFileName().toString()).filter(n->n.endsWith(".yml")||n.endsWith(".yaml")).map(n->n.replaceFirst("\\.ya?ml$","")).sorted().forEach(options::add);
        }catch(java.io.IOException|RuntimeException missing){ }
        return options;
    }
    /** /tideframe import PAGE|all: converts Ultimate UI pages into screens/ID.json (and copies animation frames). */
    private void importPages(CommandSender sender,String page){
        if(!sender.hasPermission("tideframe.edit")){sender.sendMessage("You do not have editor permission.");return;}
        if(page==null){sender.sendMessage("Use /tideframe import PAGE or /tideframe import all. Pages are read from "+importFolder()+".");return;}
        var folder=importFolder();
        try{io.execute(()->{
            var results=new ArrayList<String>();
            try{
                List<java.nio.file.Path> files;
                try(var listing=java.nio.file.Files.list(folder)){
                    files=listing.filter(f->{String n=f.getFileName().toString();return (n.endsWith(".yml")||n.endsWith(".yaml"))&&(page.equals("all")||n.replaceFirst("\\.ya?ml$","").equalsIgnoreCase(page));}).sorted().toList();
                }
                if(files.isEmpty()){sender.sendMessage("No page "+page+" in "+folder+".");return;}
                var images=ultimateContents().resolve("images").toAbsolutePath().normalize();
                var components=ultimateContents().resolve("components").toAbsolutePath().normalize();
                var effects=ultimateContents().resolve("effects").toAbsolutePath().normalize();
                var sources=new UltimateUiImporter.Sources(){
                    public byte[] image(String name){return read(images,name.endsWith(".png")?name:name+".png");}
                    public String component(String name){
                        byte[] bytes=read(components,name.endsWith(".yml")?name:name+".yml");
                        return bytes==null?null:new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
                    }
                    public String effect(String name){
                        byte[] bytes=read(effects,name.endsWith(".yml")?name:name+".yml");
                        return bytes==null?null:new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
                    }
                    private byte[] read(java.nio.file.Path root,String relative){
                        try{
                            var file=root.resolve(relative).normalize();
                            if(!file.startsWith(root)||!java.nio.file.Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)||java.nio.file.Files.size(file)>dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset.MAX_PNG_BYTES)return null;
                            return java.nio.file.Files.readAllBytes(file);
                        }catch(java.io.IOException|RuntimeException unreadable){return null;}
                    }
                };
                for(var file:files){
                    try{
                        String yaml=java.nio.file.Files.readString(file,java.nio.charset.StandardCharsets.UTF_8);
                        var result=UltimateUiImporter.convert(yaml,file.getFileName().toString(),sources);
                        store.save(result.screen());
                        int copied=0;
                        for(var frames:result.frameFolders().entrySet())copied+=copyFrames(images.resolve(frames.getKey()).normalize(),images,frames.getValue());
                        results.add(result.screen().id()+" ("+result.screen().elements().size()+" elements"+(copied>0?", "+copied+" frames":"")+", "+result.warnings().size()+" notes)");
                        for(String warning:result.warnings())getLogger().info("Import "+file.getFileName()+": "+warning);
                        var first=result.warnings().stream().limit(5).toList();
                        for(String warning:first)sender.sendMessage(" - "+warning);
                        if(result.warnings().size()>first.size())sender.sendMessage(" - ... "+(result.warnings().size()-first.size())+" more in the server log");
                    }catch(java.io.IOException|RuntimeException failure){results.add(file.getFileName()+" FAILED: "+failure.getMessage());}
                }
                sender.sendMessage("Imported: "+String.join(", ",results)+". "+(watcher!=null?"The screens reload by themselves.":"Run /tideframe reload to use them."));
            }catch(java.io.IOException unreadable){sender.sendMessage("Cannot read "+folder+": "+unreadable.getMessage());}
        });}catch(RejectedExecutionException busy){sender.sendMessage("Tideframe is busy; try again.");}
    }
    /** Copies PNG frames into plugins/BigCasares/tideframe/engine/images/FOLDER (never outside it). Returns how many. */
    private int copyFrames(java.nio.file.Path source,java.nio.file.Path sourceRoot,String folder) throws java.io.IOException {
        if(!source.startsWith(sourceRoot)||!java.nio.file.Files.isDirectory(source,java.nio.file.LinkOption.NOFOLLOW_LINKS))return 0;
        var root=getDataFolder().toPath().resolve("images").toAbsolutePath().normalize();
        var target=root.resolve(folder).normalize();
        if(!target.startsWith(root))return 0;
        java.nio.file.Files.createDirectories(target);
        int copied=0;
        try(var files=java.nio.file.Files.list(source)){
            for(var png:files.filter(f->f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")&&java.nio.file.Files.isRegularFile(f,java.nio.file.LinkOption.NOFOLLOW_LINKS)).limit(HudAssets.MAX_FRAMES).toList()){
                java.nio.file.Files.copy(png,target.resolve(png.getFileName().toString()),java.nio.file.StandardCopyOption.REPLACE_EXISTING);copied++;
            }
        }
        return copied;
    }
    /** "frame10" sorts after "frame9". */
    static String naturalKey(String name){
        var m=java.util.regex.Pattern.compile("\\d+").matcher(name.toLowerCase(Locale.ROOT));
        var out=new StringBuilder();
        while(m.find())m.appendReplacement(out,String.format(Locale.ROOT,"%012d",Long.parseLong(m.group().length()>12?m.group().substring(0,12):m.group())));
        return m.appendTail(out).toString();
    }
    /** config.yml → demos.games (sequence, roulette, overlay and image demos in the pack). */
    private volatile boolean demoGames=true;
    /** config.yml experimental.mannequin-test: /tideframe modeltest and its menu. */
    private volatile boolean modelTest;
    /** Global thread, with the option on: builds a real Mannequin and its packets once and logs whether models work. */
    private void reportModel(){
        if(!modelTest)return;
        var worlds=getServer().getWorlds();
        String failure=worlds.isEmpty()?"no world is loaded":CameraPackets.probeModel(worlds.get(0));
        if(failure==null)getLogger().info("TIDEFRAME_MODEL ready: /tideframe modeltest [spin] [profile:NAME]");
        else getLogger().warning("TIDEFRAME_MODEL unavailable: "+failure);
    }
    private final java.util.concurrent.atomic.AtomicBoolean reloading=new java.util.concurrent.atomic.AtomicBoolean();
    /** registerScreen / unregisterScreen futures waiting for the next reload that starts after their file change. */
    private final List<java.util.concurrent.CompletableFuture<Void>> reloadWaiters=new java.util.ArrayList<>();
    /** openWhenReady requests waiting for a player to load the current pack. */
    private final Map<java.util.UUID,List<Map.Entry<String,java.util.concurrent.CompletableFuture<Boolean>>>> readyOpens=new java.util.concurrent.ConcurrentHashMap<>();
    /** /tideframe reload: rebuild from disk, swap the pack server, close menus and resend the pack. */
    private void reload(CommandSender sender){
        if(!sender.hasPermission("tideframe.edit")){sender.sendMessage("You do not have editor permission.");return;}
        if(!reloading.compareAndSet(false,true)){sender.sendMessage("A reload is already running.");return;}
        sender.sendMessage("Reloading Tideframe screens and resource pack...");
        try { io.execute(()->{
            List<java.util.concurrent.CompletableFuture<Void>> waiters;
            synchronized(reloadWaiters){waiters=List.copyOf(reloadWaiters);reloadWaiters.clear();}
            try {
                var previous=settings;
                var loaded=PluginSettings.load(engineConfig.read());
                // The pack server keeps its address until restart; everything else applies now.
                settings=new PluginSettings(previous.bind(),previous.port(),previous.publicUrl(),loaded.required(),loaded.distance(),
                    loaded.predictionMs(),loaded.snapRadius(),loaded.elements(),loaded.displaySizes(),loaded.defaultSize());
                try {sounds=SoundSettings.load(engineConfig.read());}catch(IllegalArgumentException invalid){getLogger().warning(invalid.getMessage());}
                loadBossFight();
                var built=buildRuntime();
                var service=new PackService(new InetSocketAddress(settings.bind(),settings.port()),packHttp);
                PackService old;
                synchronized(lifecycle){ if(stopping){waiters.forEach(w->w.completeExceptionally(new IllegalStateException("Tideframe is stopping")));return;} old=http; http=null; }
                if(old!=null)old.close();
                service.start(built.pack());
                synchronized(lifecycle){ if(stopping){service.close();waiters.forEach(w->w.completeExceptionally(new IllegalStateException("Tideframe is stopping")));return;} http=service; }
                placeholders.reset();
                logic.resetIntegrations();
                scheduler.global(()->{
                    var online=List.copyOf(Bukkit.getOnlinePlayers());
                    accepted.clear();ownAccepted.clear();
                    // editor.reopen-after-reload: each player gets their menu back once the new pack has loaded.
                    for(var player:online)reopening.remember(player.getUniqueId(),reopenAfterReload&&sessions.containsKey(player.getUniqueId())?activeScreens.get(player.getUniqueId()):null);
                    for(var player:online)scheduler.entity(player,()->closeAll(player,TideframeCloseEvent.Reason.COMMAND));
                    publishRuntime(built);
                    registerScreenCommands();
                    reportModel();
                    for(var player:online)scheduler.entity(player,()->offer(player));
                    sender.sendMessage("Tideframe reloaded: "+built.screens().size()+" screens, pack "+built.packBytes()/1024+" KiB. Players must accept the new pack.");
                    getLogger().info("TIDEFRAME_RELOADED screens="+built.screens().size()+" packBytes="+built.packBytes());
                    waiters.forEach(w->w.complete(null));
                });
            } catch(Exception | LinkageError failure) {
                getLogger().log(java.util.logging.Level.SEVERE,"Tideframe reload failed; the previous screens stay active.",failure);
                editorFailed(String.valueOf(failure.getMessage()));
                sender.sendMessage("Reload failed: "+failure.getMessage()+" (previous screens stay active; see the console).");
                waiters.forEach(w->w.completeExceptionally(failure));
            } finally {
                reloading.set(false);
                if(pendingAutoReload.getAndSet(false))scheduler.global(this::autoReload);
            }
        }); } catch(RejectedExecutionException busy){reloading.set(false);sender.sendMessage("Tideframe is busy; try again.");}
    }
    private boolean command(CommandSender sender,Command command,String label,String[] args) {
        if (settings==null || http==null) { sender.sendMessage("Tideframe is starting or unavailable; check the server log.");return true; }
        if (args.length==1 && args[0].equalsIgnoreCase("reload")) { reload(sender);return true; }
        if (args.length>=1 && args[0].equalsIgnoreCase("import")) {
            if(sender instanceof Player&&!inGameEditing){sender.sendMessage(READ_ONLY);return true;}
            importPages(sender,args.length==2?args[1]:null);return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("TIDEFRAME_STATUS screens="+screens.size()+" menuElements="+screens.get("menu").elements().size()+" pack="+pack.path().getFileName()+" renderer=title-hud");return true;
        }
        scheduler.entity(player,() -> {
            String arg=args.length==0?"visual_menu09":args[0].toLowerCase(Locale.ROOT);
            if(arg.equals("menu")&&args.length==1&&menuIsBuiltIn)arg="visual_menu09";
            if(arg.equals("close")){closeAll(player,TideframeCloseEvent.Reason.COMMAND);return;}
            if (!player.hasPermission("tideframe.use")) { player.sendMessage("You do not have permission.");return; }
            if((arg.equals("fit")||arg.equals("size"))&&hudAssets.autoFit()){player.sendMessage("Auto-fit is on: menus and HUD pages fit your window by themselves, there is no size to choose.");return;}
            if(arg.equals("fit")){
                try {
                    if(args.length!=4)throw new IllegalArgumentException();
                    var viewport=new ViewportFit(Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]));
                    applyDisplay(player,viewport);
                    int fitted=viewport.multiplier(screens.get("visual_menu09"),hudAssets.sizes());
                    player.sendMessage("Window "+args[1]+"x"+args[2]+", GUI scale "+viewport.effectiveGuiScale()+": menu size "+ViewportFit.label(fitted)+". Saved for your next sessions.");
                }catch(IllegalArgumentException invalid){player.sendMessage("Use /tideframe fit WINDOW_WIDTH WINDOW_HEIGHT GUI_SCALE (0 = Auto). Example: /tideframe fit 1920 1080 0");}
            }
            else if(arg.equals("sensitivity")){
                try {
                    if(args.length!=2)throw new IllegalArgumentException();
                    double value=Double.parseDouble(args[1].replace(',','.'));
                    if(!(value>=dev.linqfy.bigCasares.modules.tideframe.engine.core.ScreenCursorInteraction.MIN_SENSITIVITY&&value<=dev.linqfy.bigCasares.modules.tideframe.engine.core.ScreenCursorInteraction.MAX_SENSITIVITY))throw new IllegalArgumentException();
                    sensitivities.put(player.getUniqueId(),value);
                    player.getPersistentDataContainer().set(SENSITIVITY_KEY,org.bukkit.persistence.PersistentDataType.DOUBLE,value);
                    var session=sessions.get(player.getUniqueId());if(session!=null)session.setSensitivity(value);
                    player.sendMessage("Cursor sensitivity "+value+" saved.");
                }catch(IllegalArgumentException invalid){player.sendMessage("Use /tideframe sensitivity 0.25-4 (1 = default, 2 = twice as fast). Current: "+sensitivities.getOrDefault(player.getUniqueId(),1.0));}
            }
            else if(arg.equals("debug")){
                if(debugging.remove(player.getUniqueId())){player.sendActionBar(Component.empty());player.sendMessage("Tideframe debug off.");}
                else {debugging.add(player.getUniqueId());player.sendMessage("Tideframe debug on: open a menu to see ping, mouse packets per second, fast path and size in the action bar.");}
            }
            else if(arg.equals("size")){
                if(args.length==1){sizeChooser(player);return;}
                try {
                    if(args.length!=2)throw new IllegalArgumentException();
                    int requested=ViewportFit.parseSize(args[1]);
                    int chosen=4;for(int q:hudAssets.sizes())if(q<=requested)chosen=q;
                    applyDisplay(player,ViewportFit.fixed(chosen));
                    player.sendMessage("Menu size "+ViewportFit.label(chosen)+" saved. If an edge is cut off, pick a smaller one with /tideframe size.");
                }catch(IllegalArgumentException invalid){player.sendMessage("Use /tideframe size to list the sizes, e.g. /tideframe size 1.75");}
            }
            else if (arg.equals("hud")) toggleHud(player);
            else if(arg.equals("modeltest")){
                if(!modelTest){player.sendMessage("The 3D model test is off (config.yml → tideframe-ui.motor.experimental.mannequin-test).");return;}
                if(bedrock.isBedrock(player.getUniqueId())){player.sendMessage("The 3D model test needs Minecraft Java 26.2.");return;}
                boolean spin=false;String profile=null;
                for(int i=1;i<args.length;i++){
                    String option=args[i];
                    if(option.equalsIgnoreCase("spin"))spin=true;
                    else if(option.regionMatches(true,0,"profile:",0,8)&&option.substring(8).matches("[A-Za-z0-9_]{1,16}"))profile=option.substring(8);
                    else {player.sendMessage("Use /tideframe modeltest [spin] [profile:NAME]");return;}
                }
                open(player,dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.ModelDemo.ID,false,new CameraPackets.Model(profile,spin));
            }
            else if(arg.equals("sequence"))open(player,"sequence_demo");
            else if(arg.equals("challenge")){if(args.length==2&&args[1].equalsIgnoreCase("stop"))challenges.stop(player);else startChallenge(player);}
            else if(arg.equals("roulette")){
                try{if(args.length!=2)throw new IllegalArgumentException();startRoulette(player,RouletteSpin.Color.valueOf(args[1].toUpperCase(Locale.ROOT)));}
                catch(IllegalArgumentException bad){player.sendMessage("Use /tideframe roulette RED|GREEN|BLUE|GOLD.");}
            }
            else if(arg.equals("overlay")){
                if(args.length==1&&overlays.containsKey(player.getUniqueId()))closePassive(player);
                else open(player,args.length==2?args[1]:"overlay_demo");
            }
            else if(arg.equals("balance")){
                if(!accepted.contains(player.getUniqueId())){player.sendMessage("Accept the Tideframe resource pack first.");return;}
                try{
                    if(args.length>2||args.length==2&&!player.hasPermission("tideframe.edit"))throw new IllegalArgumentException();
                    var value=args.length==2?new BigDecimal(args[1]):new BigDecimal("1250");
                    new PassiveHudState().setBalance(value,true);
                    var view=ensurePassive(player);view.state().setBalance(value,true);balances.put(player.getUniqueId(),value);view.refresh();
                }catch(IllegalArgumentException bad){player.sendMessage("Use /tideframe balance [0..1000000000000]; custom demo values require editor permission.");}
            }
            else if(arg.equals("item")){
                var items=openTriggers.items;
                String id=args.length==2?args[1].toLowerCase(Locale.ROOT):items.size()==1?items.keySet().iterator().next():null;
                var item=id==null?null:items.get(id);
                if(item==null){player.sendMessage(items.isEmpty()?"No menu items are set up (config.yml → tideframe-ui.motor.open.items).":"Use /tideframe item "+String.join("|",items.keySet()));return;}
                if(giveOpenItem(player,item))player.sendMessage("Right click with this item to open "+id+".");
            }
            else if(arg.equals("boss")){
                if(!player.hasPermission("tideframe.edit")){player.sendMessage("The demo boss bar needs editor permission (tideframe.edit).");return;}
                if(!accepted.contains(player.getUniqueId())){player.sendMessage("Accept the Tideframe resource pack first.");return;}
                var view=ensurePassive(player);
                // /tideframe boss hit: the demo boss loses 10 health, to see config.yml boss-bar flash and shake.
                if(args.length==2&&args[1].equalsIgnoreCase("hit")){
                    var shown=view.state().snapshot();
                    double hp=shown.bossVisible()?Math.max(0,Math.round(shown.bossProgress()*100)-10):65;
                    view.state().setBoss("DEMO BOSS",hp,100);view.refresh();
                    player.sendMessage("Demo boss hit: "+(int)hp+"/100.");return;
                }
                if(view.state().snapshot().bossVisible())view.state().hideBoss();else view.state().setBoss("DEMO BOSS",75,100);
                view.refresh();
            }
            else if (arg.equals("edit")) edit(player,Arrays.copyOfRange(args,1,args.length));
            else if (arg.equals("item")) item(player,args);
            else if (arg.equals("choose")) {
                try{if(args.length!=2)throw new NumberFormatException();fallback.choose(player,Integer.parseInt(args[1]));}
                catch(NumberFormatException e){player.sendMessage("Use /tideframe choose NUMBER.");}
            }
            else if (arg.equals("fallback")) {
                String target=args.length==2?args[1]:"menu";Screen screen=screens.get(target);
                if(screen==null)player.sendMessage("Unknown screen.");else nativeMenu(player,screen);
            }
            else open(player,arg);
        });
        return true;
    }
    /** config.yml → pack.send: false when another plugin (ItemsAdder, Nexo, Oraxen...) distributes the exported assets. */
    private volatile boolean sendPack=true;
    /** Worker thread: copies the pack's assets into every pack.export-to folder (for ItemsAdder, Nexo, Oraxen or a CDN). */
    private void exportPack(java.nio.file.Path zip){
        var config=engineConfig.read();
        sendPack=config.getBoolean("pack.send",true);
        var server=host.getDataFolder().toPath().toAbsolutePath().normalize().getParent().getParent();
        for(String raw:config.getStringList("pack.export-to")){
            try{
                var target=server.resolve(raw).toAbsolutePath().normalize();
                if(raw.isBlank()||java.nio.file.Paths.get(raw).isAbsolute()||!target.startsWith(server)){getLogger().warning("pack.export-to "+raw+" must be a folder inside the server directory; skipped.");continue;}
                var ours=target.resolve("assets").resolve("tideframe");
                if(java.nio.file.Files.isDirectory(ours,java.nio.file.LinkOption.NOFOLLOW_LINKS))try(var old=java.nio.file.Files.walk(ours)){
                    for(var path:old.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(path);
                }
                int files=0;
                try(var input=new java.util.zip.ZipInputStream(java.nio.file.Files.newInputStream(zip))){
                    for(var entry=input.getNextEntry();entry!=null;entry=input.getNextEntry()){
                        if(entry.isDirectory()||!entry.getName().startsWith("assets/"))continue;
                        var file=target.resolve(entry.getName()).normalize();
                        if(!file.startsWith(target))continue;
                        java.nio.file.Files.createDirectories(file.getParent());
                        java.nio.file.Files.copy(input,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);files++;
                    }
                }
                getLogger().info("TIDEFRAME_PACK_EXPORTED folder="+target+" files="+files);
            }catch(java.io.IOException|RuntimeException failure){getLogger().warning("pack.export-to "+raw+" failed: "+failure.getMessage());}
        }
    }
    private void offer(Player player) {
        if (pack==null || stopping || bedrock.isBedrock(player.getUniqueId())) return;
        if (!sendPack) return; // the exported assets reach the player through another plugin's pack
        player.addResourcePack(PACK_ID,settings.publicUrl()+"tideframe.zip",pack.sha1(),"Tideframe original UI font and cursor",settings.required());
    }
    private void open(Player player,String id) {open(player,id,false);}
    /** {@code back}: a back navigation, which must not push the screen being left onto the history again. */
    private void open(Player player,String id,boolean back) {open(player,id,back,null);}
    /** {@code model}: the client-only Mannequin of /tideframe modeltest, or null. */
    private void open(Player player,String id,boolean back,CameraPackets.Model model) {
        if(stopping || !player.hasPermission("tideframe.use"))return;
        // The boss prompts are driven by the challenge, not opened as plain screens.
        if(id.startsWith("challenge_")){startChallenge(player);return;}
        Screen screen=screens.get(id);
        if (screen==null && !demoGames && (id.equals("sequence_demo")||id.equals("overlay_demo")||id.equals("image_demo08")||id.startsWith("spin_")||id.startsWith("seq_")||id.startsWith("win_")||id.equals("roulette_demo"))) {
            player.sendMessage("The demo games are turned off on this server (config.yml → tideframe-ui.motor.demos.games).");return;
        }
        if (screen==null || screen.elements().size()>settings.elements()) { player.sendMessage("Unknown screen or element limit exceeded.");return; }
        String required=screen.logic().permission();
        if(required!=null && !player.hasPermission(required)){player.sendMessage("You do not have permission to open this screen.");return;}
        boolean overlay=screen.presentation().mode().equals("overlay");
        String previous=activeScreens.get(player.getUniqueId());
        var opening=new TideframeOpenEvent(player,id,overlay?null:previous,overlay);
        getServer().getPluginManager().callEvent(opening);
        if(opening.isCancelled())return;
        if(overlay){
            if(bedrock.isBedrock(player.getUniqueId())){player.sendMessage("Custom passive overlays currently require Minecraft Java. Use the native fallback menu.");return;}
            if(!accepted.contains(player.getUniqueId())){player.sendMessage("Accept the Tideframe resource pack before opening the HUD.");return;}
            closeMenu(player,TideframeCloseEvent.Reason.REPLACED);
            var view=ensurePassive(player);
            // presentation.stack: added over the HUD pages already shown; otherwise it replaces them all, as always.
            if(screen.presentation().stack()){
                try{view.addScreen(screen,0);}catch(IllegalStateException full){player.sendMessage(full.getMessage()+".");return;}
                logic.attachOverlay(player,screen);
            }else{
                logic.detach(player,LogicController.Mode.OVERLAY);
                toastQueues.remove(player.getUniqueId());activeToasts.remove(player.getUniqueId());
                view.showScreen(screen);view.refresh();
                logic.attach(player,screen,LogicController.Mode.OVERLAY);
            }
            logic.forgetSession(player);
            return;
        }
        if(player.isInsideVehicle()){player.sendMessage("Leave your vehicle before opening a menu.");return;}
        if(bedrock.isBedrock(player.getUniqueId())){nativeMenu(player,screen);return;}
        if (!accepted.contains(player.getUniqueId())) { player.sendMessage("Accept the Tideframe resource pack before opening the UI.");return; }
        if(previous!=null && !back && !previous.equals(id) && sessions.containsKey(player.getUniqueId()))logic.pushHistory(player,previous);
        // Menu -> menu keeps the fixed camera and input hook: no flicker, the entrance animation replays.
        var current=sessions.get(player.getUniqueId());
        // A model travels in the camera's packets: opening one, or leaving one, always reopens the camera.
        if(current!=null && model==null && !current.hasModel() && current.displayQuarters()==quartersFor(player,screen)) {
            programs.remove(player.getUniqueId());
            logic.detach(player,LogicController.Mode.MENU);
            if(previous!=null)getServer().getPluginManager().callEvent(new TideframeCloseEvent(player,previous,TideframeCloseEvent.Reason.REPLACED));
            current.navigate(screen);activeScreens.put(player.getUniqueId(),id);
            if(id.equals("sequence_demo"))programs.put(player.getUniqueId(),new DemoProgram(new SequenceGame()));
            logic.attach(player,screen,LogicController.Mode.MENU);
            return;
        }
        closeMenu(player,TideframeCloseEvent.Reason.REPLACED);
        try {
            int displayMultiplier=quartersFor(player,screen);
            var created=new UiSession(host,scheduler,player,screen,settings,a -> action(player,a),images,hudAssets,
                ()->advanceProgram(player),()->"",displayMultiplier,glyphs->{
                    var view=overlays.get(player.getUniqueId());
                    return (view==null?Component.empty():view.layer()).append(HudText.layer(hudAssets,glyphs,displayMultiplier,screen));
                },model);
            String modelProblem=created.modelProblem();
            if("blocked".equals(modelProblem))player.sendMessage("Something is in the way of the 3D model: step back from walls and ceilings and try again.");
            else if(modelProblem!=null){player.sendMessage("The 3D model could not be shown; the menu opened without it.");getLogger().warning("TIDEFRAME_MODEL "+modelProblem);}
            boolean published;
            synchronized(lifecycle) {
                published=!stopping;
                if(published) {sessions.put(player.getUniqueId(),created);activeScreens.put(player.getUniqueId(),id);}
            }
            if(published) {
                created.setSensitivity(sensitivities.getOrDefault(player.getUniqueId(),1.0));
                created.setFeedback(feedback(player,created));
                var trails=new dev.linqfy.bigCasares.modules.tideframe.engine.core.BarTrails();
                created.setLiveResolver(shown->trails.apply(shown,liveValues(player,shown)));
                created.setClickObserver((screenId,elementId,action)->{
                    var event=new TideframeClickEvent(player,screenId,elementId,action);
                    getServer().getPluginManager().callEvent(event);
                    return !event.isCancelled();
                });
                created.setInvalidListener(()->closeMenu(player,player.isDead()?TideframeCloseEvent.Reason.DEATH:TideframeCloseEvent.Reason.INVALID));
            }
            if(!published)created.close();
            else {
                if(id.equals("sequence_demo"))programs.put(player.getUniqueId(),new DemoProgram(new SequenceGame()));
                logic.attach(player,screen,LogicController.Mode.MENU);
            }
        } catch(RuntimeException failure) {
            closeMenu(player,TideframeCloseEvent.Reason.INVALID);player.sendMessage("The fixed-camera menu could not open. Stand upright outside a vehicle; this adapter requires Minecraft 26.2 or later.");
            getLogger().warning("Fixed-camera menu unavailable: "+failure.getMessage());
        }
    }
    private void action(Player player,String action) {
        if(action.equals("close")){requestClose(player,TideframeCloseEvent.Reason.ACTION);return;}
        if(!player.hasPermission("tideframe.use"))return;
        if(action.startsWith("message:sequence:")){
            var program=programs.get(player.getUniqueId());
            if(program!=null&&program.sequence!=null){
                String value=action.substring("message:sequence:".length());
                if(value.equals("RESET"))program.sequence.reset();else program.sequence.click(value);
                publishFrame(player,program.frame());return;
            }
        }
        if(action.equals("message:challenge:START")){startChallenge(player);return;}
        if(action.startsWith("message:roulette:")&&"roulette_demo".equals(activeScreens.get(player.getUniqueId()))){
            startRoulette(player,RouletteSpin.Color.valueOf(action.substring("message:roulette:".length())));return;
        }
        dev.linqfy.bigCasares.modules.tideframe.engine.core.ActionRouter.dispatch(action,player.hasPermission("tideframe.use"),
            () -> requestClose(player,TideframeCloseEvent.Reason.ACTION),id -> open(player,id),player::sendMessage,
            line -> {closeMenu(player,TideframeCloseEvent.Reason.ACTION);player.performCommand(line.replace("{player}",player.getName()));});
    }
    private void nativeMenu(Player player,Screen screen) {
        if(stopping)return;
        if(player.isInsideVehicle()){player.sendMessage("Leave your vehicle before opening a menu.");return;}
        closeMenu(player,TideframeCloseEvent.Reason.REPLACED);activeScreens.put(player.getUniqueId(),screen.id());
        fallback.open(player,screen,a -> {
            if(!player.hasPermission("tideframe.use"))return;
            if(a.startsWith("open:")) {
                var next=screens.get(a.substring(5));if(next!=null && next.elements().size()<=settings.elements())nativeMenu(player,next);
            }else action(player,a);
        });
    }
    private void item(Player player,String[] args) {
        if(args.length!=2){player.sendMessage("Use /tideframe item namespace:id while a cursor UI is open.");return;}
        var session=sessions.get(player.getUniqueId());
        if(session==null){player.sendMessage("Open a cursor UI first.");return;}
        player.sendMessage("Custom item rendering is unavailable in screen HUD. Use an authorized PNG element.");
    }
    /**
     * A player-initiated close (close action, X, Shift, F): a page with behavior.reopen goes back to the previous
     * screen instead of closing.
     */
    /** Players whose menu is closing now: on-close actions that close again (teleport, server) join that close. */
    private final Set<UUID> closingMenus=ConcurrentHashMap.newKeySet();
    private void requestClose(Player player,TideframeCloseEvent.Reason reason) {
        if(closingMenus.contains(player.getUniqueId()))return;
        if(logic!=null && logic.reopens(player)){var previous=logic.popHistory(player);if(previous.isPresent()){open(player,previous.get(),true);return;}}
        closeMenu(player,reason);
    }
    private void closeMenu(Player player,TideframeCloseEvent.Reason reason) {
        if(!closingMenus.add(player.getUniqueId()))return;
        try{closeMenuOnce(player,reason);}finally{closingMenus.remove(player.getUniqueId());}
    }
    private void closeMenuOnce(Player player,TideframeCloseEvent.Reason reason) {
        String shown=activeScreens.get(player.getUniqueId());
        boolean open=shown!=null || sessions.containsKey(player.getUniqueId());
        // On-close actions run while the screen is still on screen.
        if(logic!=null)SessionCleanup.run(()->logic.detach(player,LogicController.Mode.MENU));
        operations.invalidate(player.getUniqueId());activeScreens.remove(player.getUniqueId());programs.remove(player.getUniqueId());
        var session=sessions.remove(player.getUniqueId());
        SessionCleanup.run(() -> {if(session!=null)session.close();},() -> {if(fallback!=null)fallback.close(player);},
            ()->{var view=overlays.get(player.getUniqueId());if(view!=null)view.refresh();},
            ()->{if(open && shown!=null)getServer().getPluginManager().callEvent(new TideframeCloseEvent(player,shown,reason));},
            ()->{if(logic!=null && reason!=TideframeCloseEvent.Reason.REPLACED)logic.forgetSession(player);});
    }
    private static final String READ_ONLY="Menus are read-only in the game. Edit plugins/BigCasares/tideframe/engine/screens/ID.json (see MENUS.md); they reload by themselves.";
    private void edit(Player player,String[] args) {
        if (!inGameEditing) { player.sendMessage(READ_ONLY);return; }
        if (!player.hasPermission("tideframe.edit")) { player.sendMessage("You do not have editor permission.");return; }
        String id=activeScreens.getOrDefault(player.getUniqueId(),"menu");Screen screen=screens.get(id);
        if(args.length==0){player.sendMessage("Edit: create ID TEXT | move ID [X Y] | delete ID | save | load SCREEN_ID");return;}
        try {
            if(args[0].equals("save") || args[0].equals("load")) {
                boolean load=args[0].equals("load");String target=load&&args.length==2?args[1]:id;
                if((load && args.length!=2) || (!load && args.length!=1))throw new IllegalArgumentException("Use save or load SCREEN_ID.");
                var ticket=operations.begin(player.getUniqueId());
                if(load) repository.load(target).whenComplete((loaded,error) -> scheduler.entity(player,() -> {
                    if(!operations.current(ticket) || !player.hasPermission("tideframe.edit") || !player.hasPermission("tideframe.use"))return;
                    if(error!=null){player.sendMessage("Screen load failed: "+error.getMessage());return;}
                    try {
                        var prepared=CloseControl.prepare(loaded);
                        hudAssets.requireKnown(prepared);
                        ImagePolicy.publish(prepared,images,approved -> {
                            screens.put(approved.id(),approved);player.sendMessage("Screen loaded.");open(player,target);
                        });
                    } catch(IllegalArgumentException e) {player.sendMessage(e.getMessage());}
                }));
                else repository.save(screen).whenComplete((ignored,error) -> scheduler.entity(player,() -> {
                    if(operations.current(ticket) && player.hasPermission("tideframe.edit") && player.hasPermission("tideframe.use"))
                        player.sendMessage(error==null?"Screen saved.":"Screen save failed: "+error.getMessage());
                }));
                return;
            }
            var session=sessions.get(player.getUniqueId());
            var point=session==null?new dev.linqfy.bigCasares.modules.tideframe.engine.core.PlaneProjection.Point(0,0):session.cursor();
            Screen edited=InGameEditor.apply(screen,args,point);
            hudAssets.requireKnown(edited);
            screens.put(id,edited);
            player.sendMessage("Screen updated.");open(player,id);
        } catch(IllegalArgumentException | RejectedExecutionException e){player.sendMessage("Edit failed: "+e.getMessage());}
    }
    /** /tideframe hud: shows or hides the money HUD (Vault balance, or the one set through the API). */
    private void toggleHud(Player player) {
        var current=overlays.get(player.getUniqueId());
        if(current!=null && !current.closed() && current.state().snapshot().balanceVisible()){closePassive(player);return;}
        if(!accepted.contains(player.getUniqueId())) {player.sendMessage("Accept the Tideframe resource pack first.");return;}
        BigDecimal value;
        try{value=new BigDecimal(placeholders.apply(player,"{balance}",this::apiBalance));}catch(NumberFormatException unknown){value=BigDecimal.ZERO;}
        var view=ensurePassive(player);view.state().setBalance(value,true);view.refresh();
    }
    @EventHandler public void join(PlayerJoinEvent event) { scheduler.entity(event.getPlayer(),() -> {restoreDisplay(event.getPlayer());offer(event.getPlayer());giveOpenItems(event.getPlayer());}); }
    @EventHandler public void pack(PlayerResourcePackStatusEvent event) {
        if (PACK_ID.equals(event.getID())) {
            if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) ownAccepted.add(event.getPlayer().getUniqueId());
            else if (event.getStatus() != PlayerResourcePackStatusEvent.Status.ACCEPTED && event.getStatus() != PlayerResourcePackStatusEvent.Status.DOWNLOADED)
                ownAccepted.remove(event.getPlayer().getUniqueId());
        }
        // pack.send: false — Tideframe's assets ride in another plugin's pack; any loaded server pack counts.
        if (!PACK_ID.equals(event.getID()) && (sendPack || event.getStatus()!=PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)) return;
        if (event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            accepted.add(event.getPlayer().getUniqueId());
            if(joinedOpen.add(event.getPlayer().getUniqueId()))scheduler.entity(event.getPlayer(),()->openOnJoin(event.getPlayer()));
            // The world's HUD pages, on joining and again after every new pack (a reload closes them).
            if(!worldHuds.isEmpty()){var player=event.getPlayer();scheduler.entity(player,()->applyWorldHuds(player));}
            var waiting=readyOpens.remove(event.getPlayer().getUniqueId());
            if(waiting!=null)for(var request:waiting)openReady(event.getPlayer(),request.getKey(),request.getValue());
            String again=reopening.take(event.getPlayer().getUniqueId());
            if(again!=null){var player=event.getPlayer();scheduler.entity(player,()->{if(!sessions.containsKey(player.getUniqueId())&&screens.containsKey(again))open(player,again);});}
        }
        else if(event.getStatus()!=PlayerResourcePackStatusEvent.Status.ACCEPTED && event.getStatus()!=PlayerResourcePackStatusEvent.Status.DOWNLOADED) {
            accepted.remove(event.getPlayer().getUniqueId());reopening.forget(event.getPlayer().getUniqueId());scheduler.entity(event.getPlayer(),() -> closeAll(event.getPlayer(),TideframeCloseEvent.Reason.INVALID));
        }
    }
    /** The first screen (by id) whose logic says open-on-join and that the player may open. */
    private void openOnJoin(Player player) {
        if(stopping || !player.isOnline())return;
        screens.values().stream().filter(screen->screen.logic().openOnJoin())
            .filter(screen->screen.logic().permission()==null || player.hasPermission(screen.logic().permission()))
            .map(Screen::id).sorted().findFirst().ifPresent(id->open(player,id));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent event) {
        var session=sessions.get(event.getPlayer().getUniqueId());if(session==null){openFromInteract(event);return;}
        event.setCancelled(true);
        // Left click on air comes from the arm swing packet, which also follows a right click: see UiSession#swingIsClick.
        if (event.getAction()==Action.LEFT_CLICK_AIR) {if(session.swingIsClick())scheduler.entity(event.getPlayer(),session::click);}
        else if (event.getAction()==Action.LEFT_CLICK_BLOCK) scheduler.entity(event.getPlayer(),session::click);
        else if (event.getAction()==Action.RIGHT_CLICK_AIR || event.getAction()==Action.RIGHT_CLICK_BLOCK) {session.noteRightClick();scheduler.entity(event.getPlayer(),session::rightClick);}
    }
    /**
     * With a menu open the player's clicks belong to it: cancelled first, so listeners that skip cancelled interactions
     * (a villager that steals on right click, for one) never act on the entity behind the menu. The HIGHEST handler
     * below cancels again and turns the click into the menu's right click.
     */
    @EventHandler(priority=EventPriority.LOWEST) public void claimEntityInteraction(PlayerInteractEntityEvent event) {
        if(sessions.containsKey(event.getPlayer().getUniqueId()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void interactEntity(PlayerInteractEntityEvent event) {
        var session=sessions.get(event.getPlayer().getUniqueId());if(session==null)return;
        event.setCancelled(true);
        // Both hands' events mark the right click (the swing that follows may come from either); one press only.
        session.noteRightClick();
        if(event.getHand()==org.bukkit.inventory.EquipmentSlot.HAND)scheduler.entity(event.getPlayer(),session::rightClick);
    }
    /** Number keys and the mouse wheel: the slot stays put while a menu is open, the change becomes a key or a wheel step. */
    @EventHandler(priority=EventPriority.HIGHEST) public void hotbar(PlayerItemHeldEvent event) {
        if(!sessions.containsKey(event.getPlayer().getUniqueId()))return;
        event.setCancelled(true);
        logic.hotbar(event.getPlayer(),event.getPreviousSlot(),event.getNewSlot());
    }
    /** Q and F reach the page from the network (see CameraInputPackets); here they only must not drop or swap items. */
    @EventHandler(priority=EventPriority.HIGHEST) public void drop(PlayerDropItemEvent event) {
        if(sessions.containsKey(event.getPlayer().getUniqueId()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void swap(PlayerSwapHandItemsEvent event) {
        var player=event.getPlayer();
        if(sessions.containsKey(player.getUniqueId())){event.setCancelled(true);return;}
        // config.yml open.keys: F (or Shift+F) outside a menu opens a screen instead of swapping hands.
        String screen=openTriggers.keyScreen(player.isSneaking());
        if(screen==null||challenges.active(player)||!player.hasPermission("tideframe.use"))return;
        event.setCancelled(true);
        scheduler.entity(player,()->open(player,screen));
    }
    /** config.yml open.zones: walking into a zone opens its screen once per visit. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void walk(PlayerMoveEvent event) {
        var triggers=openTriggers;
        if(triggers.zones.isEmpty())return;
        var to=event.getTo();var from=event.getFrom();
        if(to.getWorld()==from.getWorld()&&to.getBlockX()==from.getBlockX()&&to.getBlockY()==from.getBlockY()&&to.getBlockZ()==from.getBlockZ())return;
        var player=event.getPlayer();
        if(sessions.containsKey(player.getUniqueId())||challenges.active(player))return;
        var zone=triggers.zoneAt(to.getWorld().getName(),to.getBlockX(),to.getBlockY(),to.getBlockZ());
        if(zoneVisits.entered(player.getUniqueId(),zone,System.currentTimeMillis())&&player.hasPermission("tideframe.use"))
            scheduler.entity(player,()->open(player,zone.screen()));
    }
    /** Outside a menu: an item with Tideframe's mark, or a [Tideframe] sign, opens its screen (config.yml open). */
    private void openFromInteract(PlayerInteractEvent event) {
        var triggers=openTriggers;
        if(!triggers.any()||event.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND)return;
        if(event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        String screen=null;
        var block=event.getClickedBlock();
        if(triggers.signs&&event.getAction()==Action.RIGHT_CLICK_BLOCK&&block!=null&&block.getState() instanceof org.bukkit.block.Sign sign){
            var front=sign.getSide(org.bukkit.block.sign.Side.FRONT);
            var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            screen=OpenTriggers.signScreen(plain.serialize(front.line(0)),plain.serialize(front.line(1)));
        }
        if(screen==null&&!triggers.items.isEmpty())screen=itemScreen(event.getItem());
        if(screen==null||!event.getPlayer().hasPermission("tideframe.use"))return;
        event.setCancelled(true);
        var player=event.getPlayer();String target=screen;
        scheduler.entity(player,()->open(player,target));
    }
    private static String itemScreen(org.bukkit.inventory.ItemStack item) {
        if(item==null||!item.hasItemMeta())return null;
        return item.getItemMeta().getPersistentDataContainer().get(OPEN_ITEM_KEY,org.bukkit.persistence.PersistentDataType.STRING);
    }
    /** open.items with give-on-join: puts each missing item in its slot (or anywhere free). */
    private void giveOpenItems(Player player) {
        for(var item:openTriggers.items.values()){
            if(!item.giveOnJoin())continue;
            boolean has=false;
            for(var stack:player.getInventory().getContents())if(item.screen().equals(itemScreen(stack))){has=true;break;}
            if(!has)giveOpenItem(player,item);
        }
    }
    private boolean giveOpenItem(Player player,OpenTriggers.Item item) {
        Material material=Material.matchMaterial(item.material());
        if(material==null||!material.isItem()||material.isAir()){getLogger().warning("config.yml tideframe-ui.motor.open.items."+item.screen()+": unknown material "+item.material());return false;}
        var stack=new org.bukkit.inventory.ItemStack(material);
        var meta=stack.getItemMeta();
        if(!item.name().isBlank())meta.displayName(TextFormat.parse(item.name()).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));
        meta.getPersistentDataContainer().set(OPEN_ITEM_KEY,org.bukkit.persistence.PersistentDataType.STRING,item.screen());
        stack.setItemMeta(meta);
        var inventory=player.getInventory();
        if(item.slot()>=0&&inventory.getItem(item.slot())==null)inventory.setItem(item.slot(),stack);
        else inventory.addItem(stack).values().forEach(rest->player.getWorld().dropItemNaturally(player.getLocation(),rest));
        return true;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(org.bukkit.event.entity.EntityDamageEvent event) {
        if(!(event.getEntity() instanceof Player player)||logic==null)return;
        if(!sessions.containsKey(player.getUniqueId())&&!overlays.containsKey(player.getUniqueId()))return;
        double amount=event.getFinalDamage();String cause=event.getCause().name().toLowerCase(java.util.Locale.ROOT);
        scheduler.entity(player,()->logic.damaged(player,amount,cause));
    }
    @EventHandler public void changedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        var player=event.getPlayer();
        if(logic==null)return;
        if(!worldHuds.isEmpty()||worldHudShown.containsKey(player.getUniqueId()))scheduler.entity(player,()->applyWorldHuds(player));
        if(!sessions.containsKey(player.getUniqueId())&&!overlays.containsKey(player.getUniqueId()))return;
        String world=player.getWorld().getName(),from=event.getFrom().getName();
        scheduler.entity(player,()->logic.worldChanged(player,world,from));
    }
    @EventHandler public void death(org.bukkit.event.entity.PlayerDeathEvent event) {
        var player=event.getPlayer();
        if(sessions.containsKey(player.getUniqueId()))scheduler.entity(player,()->closeMenu(player,TideframeCloseEvent.Reason.DEATH));
    }
    @EventHandler public void swing(PlayerAnimationEvent event) {
        var session=sessions.get(event.getPlayer().getUniqueId());if(session!=null){if(session.swingIsClick())scheduler.entity(event.getPlayer(),session::click);}
        // During a boss fight a swing (left click on air, block or the boss) answers a CLICK prompt.
        else if(challenges.active(event.getPlayer()))challenges.key(event.getPlayer(),dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.KeyChallenge.Key.CLICK);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void attack(EntityDamageByEntityEvent event) {
        if(event.getDamager() instanceof Player p) {
            var session=sessions.get(p.getUniqueId());if(session!=null){event.setCancelled(true);scheduler.entity(p,session::click);}
        }
    }
    @EventHandler public void sneak(PlayerToggleSneakEvent event) {
        if(!event.isSneaking())return;
        var player=event.getPlayer();
        if(sessions.containsKey(player.getUniqueId()) && !logic.closesOnSneak(player))return;
        requestClose(player,TideframeCloseEvent.Reason.SNEAK);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        ownAccepted.remove(event.getPlayer().getUniqueId());
        Player p=event.getPlayer();
        SessionCleanup.run(() -> closeAll(p,TideframeCloseEvent.Reason.QUIT),() -> UiSession.cancelOutro(p.getUniqueId()),() -> accepted.remove(p.getUniqueId()),() -> joinedOpen.remove(p.getUniqueId()),
            () -> zoneVisits.forget(p.getUniqueId()),() -> reopening.forget(p.getUniqueId()),
            () -> logic.quit(p),() -> operations.forget(p.getUniqueId()),()->viewports.remove(p.getUniqueId()),
            ()->{var waiting=readyOpens.remove(p.getUniqueId());if(waiting!=null)waiting.forEach(request->request.getValue().complete(false));},()->sensitivities.remove(p.getUniqueId()),()->balances.remove(p.getUniqueId()),()->debugging.remove(p.getUniqueId()));
    }
    public void onDisable() {
        ownAccepted.clear();
        PackService service;
        synchronized(lifecycle) { stopping=true;service=http;http=null; }
        var watching=watcher;watcher=null;if(watching!=null)watching.close();
        var watchingConfig=configWatcher;configWatcher=null;if(watchingConfig!=null)watchingConfig.close();
        var watchingOpen=openWatcher;openWatcher=null;if(watchingOpen!=null)watchingOpen.close();
        if(keyInput!=null)keyInput.unregister();
        challenges.stopAll();
        // Saved variables changed in menus still open (Folia: the players' own threads are gone, they were saved on close).
        if(logic!=null&&!PlatformProbe.folia()){try{logic.persistAll(getServer().getOnlinePlayers());}catch(RuntimeException failure){getLogger().warning("Saving variables failed: "+failure.getMessage());}}
        sessions.forEach((id,session) -> {
            try {if(PlatformProbe.folia())session.abandonOnShutdown();else session.close();}
            catch(RuntimeException failure){getLogger().warning("Session shutdown cleanup failed: "+failure.getMessage());}
        });
        sessions.clear();activeScreens.clear();if(logic!=null)logic.clear();
        try{UiSession.cancelOutros();}catch(RuntimeException failure){getLogger().warning("Closing slide cleanup failed: "+failure.getMessage());}
        if(screenCommands!=null){try{screenCommands.clear();}catch(RuntimeException failure){getLogger().warning("Screen command cleanup failed: "+failure.getMessage());}}
        programs.clear();overlays.values().forEach(view->{try{if(PlatformProbe.folia())view.abandon();else view.close();}catch(RuntimeException failure){getLogger().warning("Passive HUD cleanup failed: "+failure.getMessage());}});overlays.clear();
        if(fallback!=null)fallback.clear();
        operations.clear();if(repository!=null)repository.close();
        if(service!=null)service.close();io.shutdownNow();packHttp.shutdownNow();
        scope.close();
    }
    private static final NamespacedKey DISPLAY_KEY=new NamespacedKey("tideframe","display");
    /** Shows the pack's sizes as clickable choices, with the screens each one fills. */
    private void sizeChooser(Player player){
        var hints=Map.of(4,"small window",7,"full screen: 1280x720, 2560x1440, 4K at GUI Auto",8,"full screen: 1920x1080 at GUI Auto",
            11,"full screen: 1920x1080 GUI 3, 2560x1440 GUI 4, 4K GUI 6");
        player.sendMessage(Component.text("Menu size (click one; the menu should fill your screen):",net.kyori.adventure.text.format.NamedTextColor.AQUA));
        int current=viewport(player).fixedQuarters();
        for(int q:hudAssets.sizes()) {
            String label=ViewportFit.label(q);
            player.sendMessage(Component.text((q==current?" > ":"   ")+"["+label+"x] ",q==current?net.kyori.adventure.text.format.NamedTextColor.GOLD:net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .append(Component.text(hints.getOrDefault(q,""),net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/tideframe size "+label))
                .hoverEvent(Component.text("Open the menu at size "+label)));
        }
        player.sendMessage(Component.text("Exact full screen for your window: /tideframe fit WIDTH HEIGHT GUI_SCALE (0 = Auto)",net.kyori.adventure.text.format.NamedTextColor.GRAY));
    }
    /** Owner-thread sounds plus the optional debug line for one session. */
    private UiSession.Feedback feedback(Player player,UiSession session){
        return new UiSession.Feedback(){
            public void hover(){play(player,sounds.hover(),1.6f);}
            public void click(){play(player,sounds.click(),1f);}
        };
    }
    private void play(Player player,String sound,float pitch){
        var current=sounds;
        if(current.enabled()&&current.volume()>0)player.playSound(player.getLocation(),sound,SoundCategory.MASTER,current.volume(),pitch);
    }
    private static final List<String> SUBCOMMANDS=List.of("close","size","fit","sensitivity","debug","sequence","challenge","roulette","overlay","balance","boss","fallback","choose","edit","reload","hud","import","modeltest","item");
    private List<String> complete(CommandSender sender,Command command,String label,String[] args){
        if(args.length==1){
            var options=new ArrayList<String>(SUBCOMMANDS);
            screens.keySet().stream().filter(id->!id.startsWith("spin_")&&!id.startsWith("seq_")&&!id.startsWith("win_")).forEach(options::add);
            return options.stream().filter(o->o.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
        }
        if(args.length==2) {
            List<String> options=switch(args[0].toLowerCase(Locale.ROOT)){
                case "size"->hudAssets.sizes().stream().map(ViewportFit::label).toList();
                case "sensitivity"->List.of("0.5","0.75","1","1.5","2");
                case "roulette"->List.of("red","green","blue","gold");
                case "fit"->List.of("1920","2560","1366","1280");
                case "fallback","overlay"->List.copyOf(screens.keySet());
                case "item"->List.copyOf(openTriggers.items.keySet());
                case "import"->importablePages();
                default->List.of();
            };
            return options.stream().filter(o->o.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if(args.length==3&&args[0].equalsIgnoreCase("fit"))return List.of("1080","1440","768","720");
        if(args.length==4&&args[0].equalsIgnoreCase("fit"))return List.of("0","2","3","4");
        return List.of();
    }
    private final Map<UUID,BigDecimal> balances=new ConcurrentHashMap<>();
    /** Balance supplied through the API (used for {balance} when Vault is not installed). */
    private BigDecimal apiBalance(Player player){return balances.get(player.getUniqueId());}
    private ViewportFit viewport(Player player){
        var chosen=viewports.get(player.getUniqueId());
        return chosen!=null?chosen:ViewportFit.fixed(settings.defaultSize());
    }
    /** Stores the choice on the player (survives reconnects) and reopens the current menu at the new size. */
    private void applyDisplay(Player player,ViewportFit viewport){
        viewports.put(player.getUniqueId(),viewport);
        player.getPersistentDataContainer().set(DISPLAY_KEY,org.bukkit.persistence.PersistentDataType.STRING,viewport.encode());
        String current=activeScreens.get(player.getUniqueId());
        boolean overlay=overlays.containsKey(player.getUniqueId());
        closeAll(player,TideframeCloseEvent.Reason.REPLACED);
        if(overlay&&current==null)open(player,"overlay_demo");
        else open(player,current==null||programs.containsKey(player.getUniqueId())?"visual_menu09":current);
    }
    private void restoreDisplay(Player player){
        Double sensitivity=player.getPersistentDataContainer().get(SENSITIVITY_KEY,org.bukkit.persistence.PersistentDataType.DOUBLE);
        if(sensitivity!=null&&sensitivity>=.25&&sensitivity<=4)sensitivities.put(player.getUniqueId(),sensitivity);
        String stored=player.getPersistentDataContainer().get(DISPLAY_KEY,org.bukkit.persistence.PersistentDataType.STRING);
        if(stored==null)return;
        try{viewports.put(player.getUniqueId(),ViewportFit.decode(stored));}
        catch(IllegalArgumentException invalid){player.getPersistentDataContainer().remove(DISPLAY_KEY);}
    }
    private static Screen demoPresentation(Screen screen){
        boolean passive=screen.id().equals("overlay_demo")||screen.presentation().mode().equals("overlay");
        return screen.withPresentation(new Screen.Presentation(passive?"overlay":"menu",1,passive?"text":"arrow"));
    }
    private PassiveHudSession ensurePassive(Player player){
        if(stopping||!accepted.contains(player.getUniqueId()))throw new IllegalArgumentException("Accept the Tideframe pack first.");
        var current=overlays.get(player.getUniqueId());if(current!=null&&!current.closed())return current;
        var created=new PassiveHudSession(scheduler,player,hudAssets,()->sessions.containsKey(player.getUniqueId())||UiSession.outroPlaying(player.getUniqueId()),
            ()->closePassive(player),viewport(player));
        var trails=new ConcurrentHashMap<String,dev.linqfy.bigCasares.modules.tideframe.engine.core.BarTrails>();
        created.setLiveResolver(shown->trails.computeIfAbsent(shown.id(),id->new dev.linqfy.bigCasares.modules.tideframe.engine.core.BarTrails()).apply(shown,liveValues(player,shown)));
        created.setExpiryListener(id->toastLeft(player,id));
        created.setBossEffects(bossFlash,bossShake);
        overlays.put(player.getUniqueId(),created);return created;
    }
    /** Players whose HUD is closing now: pages its on-close actions add (hud, toast) would outlive it, so they are not added. */
    private final Set<UUID> closingHuds=ConcurrentHashMap.newKeySet();
    private void closePassive(Player player){
        if(!closingHuds.add(player.getUniqueId()))return;
        try{
            if(logic!=null)SessionCleanup.run(()->logic.detach(player,LogicController.Mode.OVERLAY));
            var old=overlays.remove(player.getUniqueId());if(old!=null)old.close();
            toastQueues.remove(player.getUniqueId());activeToasts.remove(player.getUniqueId());worldHudShown.remove(player.getUniqueId());
            if(logic!=null)logic.forgetSession(player);
        }finally{closingHuds.remove(player.getUniqueId());}
    }
    /** True while the HUD session still shows something: a page, the supplied balance or the boss bar. */
    private static boolean showsAnything(PassiveHudSession view){
        var value=view.state().snapshot();
        return view.hasPages()||value.balanceVisible()||value.bossVisible();
    }
    /**
     * Adds HUD page {@code id} over the shown ones ({@code add}) or takes it away; a menu stays open. {@code warn}: tell the
     * log why a page could not be added (actions); the world list stays quiet.
     */
    private void hudPage(Player player,String id,boolean add,boolean warn){
        UUID uuid=player.getUniqueId();
        if(!add){
            var view=overlays.get(uuid);if(view==null||view.closed())return;
            logic.detachOverlay(player,id);
            view.removeScreen(id);
            // Taking away the toast on screen already moved the queue on (toastLeft), and may have closed the HUD.
            if(!view.closed()&&!showsAnything(view))closePassive(player);
            return;
        }
        if(closingHuds.contains(uuid))return;
        Screen screen=screens.get(id);
        String problem=screen==null?"there is no screen "+id:!screen.presentation().mode().equals("overlay")?id+" is not a HUD page (presentation mode overlay)"
            :screen.logic().permission()!=null&&!player.hasPermission(screen.logic().permission())?player.getName()+" lacks the permission of "+id
            :bedrock.isBedrock(uuid)?"HUD pages need Minecraft Java":!accepted.contains(uuid)?player.getName()+" has not loaded the pack yet":null;
        if(problem!=null){if(warn)getLogger().warning("hud: "+problem+"; not shown.");return;}
        var opening=new TideframeOpenEvent(player,id,null,true);
        getServer().getPluginManager().callEvent(opening);
        if(opening.isCancelled())return;
        var view=ensurePassive(player);
        // Already there to stay: nothing to do. Shown only as a toast: it stays from now on (the toast is reported gone).
        if(view.staying(id))return;
        try{view.addScreen(screen,0);}catch(IllegalStateException full){if(warn)getLogger().warning("hud: "+full.getMessage()+"; "+id+" not shown.");return;}
        logic.attachOverlay(player,screen);
    }
    /** The page's own permission (logic.permission), as every other way of opening a page checks it. */
    private static boolean mayOpen(Player player,Screen screen){return screen.logic().permission()==null||player.hasPermission(screen.logic().permission());}
    // ----- toasts (idea 22): HUD pages shown for a few seconds, one after another -----
    private record Toast(String screen,double seconds,Map<String,String> values){}
    private final Map<UUID,java.util.ArrayDeque<Toast>> toastQueues=new ConcurrentHashMap<>();
    private final Map<UUID,String> activeToasts=new ConcurrentHashMap<>();
    /** Owner thread: shows the toast now, or after the ones already waiting (at most 16 wait). */
    private void queueToast(Player player,String id,double seconds,Map<String,String> values){
        if(closingHuds.contains(player.getUniqueId()))return;
        Screen screen=screens.get(id);
        if(screen==null||!screen.presentation().mode().equals("overlay")){getLogger().warning("toast: "+id+" is not an installed HUD page (presentation mode overlay); skipped.");return;}
        if(!mayOpen(player,screen)){getLogger().fine("toast: "+player.getName()+" lacks the permission of "+id+"; skipped.");return;}
        if(bedrock.isBedrock(player.getUniqueId())||!accepted.contains(player.getUniqueId()))return;
        var queue=toastQueues.computeIfAbsent(player.getUniqueId(),uuid->new java.util.ArrayDeque<>());
        if(queue.size()>=16){getLogger().warning("toast: "+player.getName()+" already has 16 toasts waiting; "+id+" skipped.");return;}
        queue.add(new Toast(id,seconds,Map.copyOf(values)));
        if(!activeToasts.containsKey(player.getUniqueId()))nextToast(player);
    }
    private void nextToast(Player player){
        var queue=toastQueues.get(player.getUniqueId());
        var toast=queue==null?null:queue.poll();
        if(toast==null){activeToasts.remove(player.getUniqueId());return;}
        Screen screen=screens.get(toast.screen());
        // Checked again when it comes in: pages and permissions can change while it waits.
        if(screen==null||!screen.presentation().mode().equals("overlay")||!mayOpen(player,screen)){nextToast(player);return;}
        var view=ensurePassive(player);
        try{view.addScreen(screen,toast.seconds());}catch(IllegalStateException full){getLogger().warning("toast: "+full.getMessage()+"; "+toast.screen()+" skipped.");nextToast(player);return;}
        activeToasts.put(player.getUniqueId(),toast.screen());
        logic.attachOverlay(player,screen,toast.values());
    }
    /** A toast finished leaving: its page closes and the next one, if any, comes in. */
    private void toastLeft(Player player,String id){
        logic.detachOverlay(player,id);
        if(id.equals(activeToasts.get(player.getUniqueId()))){activeToasts.remove(player.getUniqueId());nextToast(player);}
        var view=overlays.get(player.getUniqueId());
        if(view!=null&&!view.closed()&&!showsAnything(view))closePassive(player);
    }
    // ----- HUD pages per world (config.yml hud.worlds) -----
    private volatile Map<String,List<String>> worldHuds=Map.of();
    /** config.yml boss-bar.flash / boss-bar.shake: what the boss bar does when the boss loses health. */
    private volatile boolean bossFlash,bossShake;
    private final Map<UUID,Set<String>> worldHudShown=new ConcurrentHashMap<>();
    /** Owner thread: the HUD pages the player's world lists come in, the ones of the world they left go. */
    private void applyWorldHuds(Player player){
        if(stopping||!player.isOnline()||!accepted.contains(player.getUniqueId()))return;
        var wanted=new java.util.LinkedHashSet<>(worldHuds.getOrDefault(player.getWorld().getName(),List.of()));
        var shown=worldHudShown.computeIfAbsent(player.getUniqueId(),uuid->ConcurrentHashMap.newKeySet());
        for(String id:List.copyOf(shown))if(!wanted.contains(id)){shown.remove(id);hudPage(player,id,false,false);}
        // Taking pages away may have closed an empty HUD, which drops this set: keep counting in the current one.
        shown=worldHudShown.computeIfAbsent(player.getUniqueId(),uuid->ConcurrentHashMap.newKeySet());
        for(String id:wanted)if(!shown.contains(id)){
            hudPage(player,id,true,false);
            var view=overlays.get(player.getUniqueId());
            if(view!=null&&view.screenIds().contains(id))shown.add(id);
        }
    }
    private void closeAll(Player player,TideframeCloseEvent.Reason reason){SessionCleanup.run(()->challenges.stop(player),()->closePassive(player),()->closeMenu(player,reason));}
    private void startRoulette(Player player,RouletteSpin.Color color){
        var program=new DemoProgram(new RouletteSpin(color));open(player,program.frame());
        // Registered only if the first frame really opened (new session or in-session navigation).
        if(sessions.containsKey(player.getUniqueId())&&program.frame().equals(activeScreens.get(player.getUniqueId())))
            programs.put(player.getUniqueId(),program);
    }
    private void advanceProgram(Player player){
        var program=programs.get(player.getUniqueId());
        if(program!=null&&++program.ticks%2==0)publishFrame(player,program.tick());
        if(debugging.contains(player.getUniqueId())&&player.getTicksLived()%10==0){
            var session=sessions.get(player.getUniqueId());
            if(session!=null)player.sendActionBar(Component.text(session.debugLine(),net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }
    }
    private void publishFrame(Player player,String id){
        var view=sessions.get(player.getUniqueId());if(view==null||id.equals(activeScreens.get(player.getUniqueId())))return;
        var next=screens.get(id);if(next==null||next.elements().size()>settings.elements())throw new IllegalStateException("Missing prepared demo frame: "+id);
        view.replaceScreen(next);activeScreens.put(player.getUniqueId(),id);
    }
    private void apiUpdate(Player player,Runnable update){
        Objects.requireNonNull(player);scheduler.entity(player,()->{
            if(stopping||settings==null||http==null||!player.isOnline()||!player.hasPermission("tideframe.use"))return;
            try{update.run();}catch(IllegalArgumentException invalid){player.sendMessage(invalid.getMessage());}
        });
    }
    @Override public void showSequence(Player player){apiUpdate(player,()->open(player,"sequence_demo"));}
    @Override public void showRoulette(Player player,RouletteSpin.Color color){Objects.requireNonNull(color);apiUpdate(player,()->startRoulette(player,color));}
    @Override public void showOverlay(Player player,String id){
        Objects.requireNonNull(id);apiUpdate(player,()->{var screen=screens.get(id);if(screen==null||!screen.presentation().mode().equals("overlay"))throw new IllegalArgumentException("Choose an installed passive overlay.");open(player,id);});
    }
    @Override public void addOverlay(Player player,String screenId){
        Objects.requireNonNull(screenId);apiUpdate(player,()->{var screen=screens.get(screenId);if(screen==null||!screen.presentation().mode().equals("overlay"))throw new IllegalArgumentException("Choose an installed passive overlay.");hudPage(player,screenId,true,true);});
    }
    @Override public void removeOverlay(Player player,String screenId){Objects.requireNonNull(screenId);apiUpdate(player,()->hudPage(player,screenId,false,true));}
    @Override public void showToast(Player player,String screenId,double seconds,Map<String,String> values){
        Objects.requireNonNull(screenId);
        if(!Double.isFinite(seconds)||seconds<1||seconds>60)throw new IllegalArgumentException("A toast shows for 1 to 60 seconds");
        var copy=Map.copyOf(values);
        for(var entry:copy.entrySet()){dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Variables.validName(entry.getKey());if(entry.getValue().length()>256)throw new IllegalArgumentException("Toast values are at most 256 characters");}
        apiUpdate(player,()->queueToast(player,screenId,seconds,copy));
    }
    @Override public java.util.List<String> overlays(Player player){
        var view=overlays.get(player.getUniqueId());return view==null||view.closed()?java.util.List.of():view.screenIds();
    }
    @Override public void setBalance(Player player,BigDecimal amount){
        new PassiveHudState().setBalance(amount,false);balances.put(player.getUniqueId(),amount);
        apiUpdate(player,()->{var view=ensurePassive(player);view.state().setBalance(amount,false);view.refresh();});
    }
    @Override public void setBoss(Player player,String title,double hp,double maximum){
        new PassiveHudState().setBoss(title,hp,maximum);apiUpdate(player,()->{var view=ensurePassive(player);view.state().setBoss(title,hp,maximum);view.refresh();});
    }
    @Override public void startBossChallenge(Player player,org.bukkit.entity.LivingEntity boss,String title){
        Objects.requireNonNull(player);apiUpdate(player,()->{
            if(!accepted.contains(player.getUniqueId()))throw new IllegalArgumentException("Accept the Tideframe resource pack first.");
            challenges.start(player,boss,title);
        });
    }
    @Override public void stopBossChallenge(Player player){Objects.requireNonNull(player);apiUpdate(player,()->challenges.stop(player));}
    @Override public void closeViews(Player player){Objects.requireNonNull(player);scheduler.entity(player,()->closeAll(player,TideframeCloseEvent.Reason.ACTION));}
    @Override public boolean openScreen(Player player,String screenId){
        Objects.requireNonNull(player);Objects.requireNonNull(screenId);
        if(!screens.containsKey(screenId))return false;
        apiUpdate(player,()->open(player,screenId));return true;
    }
    @Override public void closeScreen(Player player){Objects.requireNonNull(player);scheduler.entity(player,()->closeMenu(player,TideframeCloseEvent.Reason.ACTION));}
    @Override public java.util.Optional<String> currentScreen(Player player){return java.util.Optional.ofNullable(activeScreens.get(player.getUniqueId()));}
    @Override public java.util.Set<String> screenIds(){return java.util.Set.copyOf(screens.keySet());}
    @Override public void runActions(Player player,List<Map<String,Object>> actions){
        Objects.requireNonNull(actions);
        var parsed=dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser.actions(actions); // validates now, throws on mistakes
        scheduler.entity(player,()->{if(!parsed.isEmpty())logic.run(player,actions);});
    }
    @Override public void runAction(Player player,String type,Object value){
        runActions(player,List.of(Map.of("type",Objects.requireNonNull(type),"value",value==null?"":value)));
    }
    @Override public void clickElement(Player player,String elementId){scheduler.entity(player,()->logic.clickElement(player,elementId));}
    @Override public void pressKey(Player player,String key){
        String base=key.toUpperCase(Locale.ROOT).replace("SHIFT_","");
        if(!dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.validKey(base))throw new IllegalArgumentException("Unknown key "+key);
        scheduler.entity(player,()->logic.key(player,base));
    }
    @Override public java.util.Optional<String> getVariable(Player player,String name){return logic.variable(player,name);}
    @Override public void setVariable(Player player,String name,String value){
        dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Variables.validName(name);Objects.requireNonNull(value);
        scheduler.entity(player,()->logic.setVariable(player,name,value));
    }
    @Override public void setList(Player player,String source,List<Map<String,String>> rows){
        if(!source.matches("[a-z_][a-z0-9_]{0,23}"))throw new IllegalArgumentException("Invalid list source "+source);
        var copy=rows.stream().map(Map::copyOf).toList();
        scheduler.entity(player,()->logic.setList(player,source,copy));
    }
    @Override public void clearList(Player player,String source){scheduler.entity(player,()->logic.clearList(player,source));}
    @Override public void setElementVisible(Player player,String elementId,boolean visible){
        scheduler.entity(player,()->logic.view(player,view->visible?view.visibility(List.of(elementId),List.of(),List.of()):view.visibility(List.of(),List.of(elementId),List.of())));
    }
    @Override public void setElementOffset(Player player,String elementId,int dx){scheduler.entity(player,()->logic.view(player,view->view.offset(elementId,dx)));}
    @Override public void setElementTint(Player player,String elementId,String color){scheduler.entity(player,()->logic.view(player,view->view.tint(elementId,color)));}
    @Override public void playElementAnimation(Player player,String elementId){
        scheduler.entity(player,()->logic.view(player,view->view.restart(elementId,System.nanoTime())));
    }
    @Override public java.util.concurrent.CompletableFuture<Void> registerScreen(Screen screen){
        Objects.requireNonNull(screen);
        var done=new java.util.concurrent.CompletableFuture<Void>();
        try{dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec.encode(screen);}catch(IllegalArgumentException invalid){done.completeExceptionally(invalid);return done;}
        if(store==null||stopping){done.completeExceptionally(new IllegalStateException("Tideframe is starting or stopping"));return done;}
        try{io.execute(()->{
            try{store.save(screen);}
            catch(java.io.IOException|RuntimeException failure){done.completeExceptionally(failure);return;}
            synchronized(reloadWaiters){reloadWaiters.add(done);}
            getLogger().info("Screen "+screen.id()+" registered through the API; rebuilding the pack.");
            scheduler.global(this::autoReload);
        });}catch(RejectedExecutionException busy){done.completeExceptionally(busy);}
        return done;
    }
    @Override public java.util.concurrent.CompletableFuture<Boolean> unregisterScreen(String screenId){
        var done=new java.util.concurrent.CompletableFuture<Boolean>();
        if(screenId==null||!screenId.matches("[a-z][a-z0-9_-]{0,31}")){done.completeExceptionally(new IllegalArgumentException("Invalid screen id"));return done;}
        if(store==null||stopping){done.completeExceptionally(new IllegalStateException("Tideframe is starting or stopping"));return done;}
        try{io.execute(()->{
            boolean deleted;
            try{deleted=store.delete(screenId);}
            catch(java.io.IOException|RuntimeException failure){done.completeExceptionally(failure);return;}
            if(!deleted){done.complete(false);return;}
            var rebuilt=new java.util.concurrent.CompletableFuture<Void>();
            rebuilt.whenComplete((ignored,failure)->{if(failure!=null)done.completeExceptionally(failure);else done.complete(true);});
            synchronized(reloadWaiters){reloadWaiters.add(rebuilt);}
            scheduler.global(this::autoReload);
        });}catch(RejectedExecutionException busy){done.completeExceptionally(busy);}
        return done;
    }
    @Override public boolean isPackLoaded(Player player){return accepted.contains(player.getUniqueId());}
    @Override public java.util.concurrent.CompletableFuture<Boolean> openWhenReady(Player player,String screenId){
        Objects.requireNonNull(player);Objects.requireNonNull(screenId);
        var done=new java.util.concurrent.CompletableFuture<Boolean>();
        if(accepted.contains(player.getUniqueId())){openReady(player,screenId,done);return done;}
        readyOpens.computeIfAbsent(player.getUniqueId(),id->new java.util.concurrent.CopyOnWriteArrayList<>()).add(Map.entry(screenId,done));
        done.completeOnTimeout(false,2,java.util.concurrent.TimeUnit.MINUTES);
        return done;
    }
    private void openReady(Player player,String screenId,java.util.concurrent.CompletableFuture<Boolean> done){
        if(done.isDone())return;
        scheduler.entity(player,()->{
            if(!screens.containsKey(screenId)||!player.isOnline()){done.complete(false);return;}
            open(player,screenId);
            done.complete(screenId.equals(activeScreens.get(player.getUniqueId()))||overlays.containsKey(player.getUniqueId()));
        });
    }
}
