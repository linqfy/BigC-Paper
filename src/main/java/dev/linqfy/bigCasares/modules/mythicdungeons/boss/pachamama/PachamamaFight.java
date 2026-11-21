package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import dev.linqfy.bigCasares.modules.mythicdungeons.DungeonBounds;
import dev.linqfy.bigCasares.modules.mythicdungeons.MandingaGuitar;
import dev.linqfy.bigCasares.modules.mythicdungeons.MandingaGuitarPactRule;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterContext;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterController;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Creaking;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * Bukkit glue of the Pachamama encounter. Decisions live in {@link PachamamaRoundCycle}, {@link PachamamaRoundPlanner},
 * {@link PachamamaChordCycle}, {@link PachamamaShockwave}, {@link PachamamaRules} and {@link PachamamaDealHold}; this
 * class reads the world, paints the floor through the {@link PachamamaFloor} journal, moves the invisible Warden by
 * teleport (gravity off) and shows the effects. Everything runs in one 1-tick task of
 * {@link MythicBossSupport#scheduler()} that {@link #stop()} cancels.
 *
 * <p>Bedrock: the floor colours, the water, lightning, particles and sounds are vanilla, so Bedrock sees the same
 * rounds; a scaled Creaking stands in for Pachamama and a small wither skeleton for Mandinga; the pedestal shows a
 * floating item and also takes a sneak-hold besides the click. Only players inside the measured room are targeted
 * ({@link #fighters()}): the audience radius reaches the corridors behind the walls.</p>
 */
final class PachamamaFight implements BossEncounterController {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Particle.DustOptions WAVE_DUST = new Particle.DustOptions(Color.fromRGB(140, 40, 200), 1.6f);
    private static final Particle.DustOptions VEIN_DUST = new Particle.DustOptions(Color.fromRGB(170, 70, 230), 1.2f);
    private static final double STAND_IN_SCALE = 1.5;
    private static final double WALK_SPEED = 0.07;
    private static final double STOP_DISTANCE = 3.0;
    private static final int HINT_COOLDOWN_TICKS = 40;
    private static final double DEAL_REACH = 1.8;

    private final MythicBossSupport support;
    private final BossEncounterContext context;
    private final UUID bossId;
    private final RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
    private final Map<UUID, Long> hints = new HashMap<>();
    private final PachamamaDealHold dealHold = new PachamamaDealHold();
    private final PachamamaDealConfirm dealConfirm = new PachamamaDealConfirm();
    private final List<PachamamaShockwave> waves = new ArrayList<>();
    /** Floor block y of every paintable column (key: {@link #key}). */
    private final Map<Long, Integer> paintable = new HashMap<>();
    private final List<Block> roundBlocks = new ArrayList<>();
    private final Set<Long> paintedColumns = new HashSet<>();
    /** Blue round: water columns that were already there when it was painted ({@code null} otherwise). */
    private Set<Long> waterBefore;

    private PachamamaArena arena;
    private World world;
    private PachamamaFloor floor;
    private PachamamaMandinga mandinga;
    private PachamamaPedestal pedestal;
    private PachamamaRoundCycle rounds;
    private PachamamaChordCycle chords;
    private PachamamaRoundPlanner.Round round;
    private PachamamaColour previousColour;
    private List<PachamamaTile> usable = List.of();
    private BukkitTask task;
    private int phase = 1;
    private long tick;
    private int riseLeft = PachamamaTuning.RISE_TICKS;
    private int transitionLeft;
    private long offeredAt;
    private int spawnX;
    private int spawnZ;
    private double x;
    private double z;
    private boolean walking;
    private boolean started;
    private boolean stopped;

    PachamamaFight(MythicBossSupport support, BossEncounterContext context) {
        this.support = support;
        this.context = context;
        this.bossId = context.bossInstanceId();
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    @Override
    public void start() {
        if (started || stopped) {
            return;
        }
        started = true;
        Warden boss = context.boss();
        boss.setGravity(false);
        Location at = boss.getLocation();
        world = at.getWorld();
        spawnX = at.getBlockX();
        spawnZ = at.getBlockZ();
        x = at.getX();
        z = at.getZ();
        arena = measure(world, at);
        floor = new PachamamaFloor(support.plugin(), bossId);
        try {
            PachamamaFloor.recoverOrphans(support.plugin());
        } catch (RuntimeException failure) {
            support.logger().log(Level.WARNING, "[Mazmorras] Pachamama: no se pudo limpiar una pelea vieja", failure);
        }
        scanFloor(support.locator().dungeonBounds(at));
        usable = usableTiles(null);
        mandinga = new PachamamaMandinga(support, bossId);
        pedestal = new PachamamaPedestal(support, bossId, floor);
        rounds = new PachamamaRoundCycle(PachamamaTuning.RISE_TICKS + 30);
        try {
            support.spawnBedrockStandIn(bossId, boss, Creaking.class, STAND_IN_SCALE);
        } catch (RuntimeException failure) {
            support.logger().log(Level.WARNING, "[Mazmorras] Pachamama: sin doble de Bedrock", failure);
        }
        context.animate("rise");
        task = support.scheduler().repeating("pachamama-" + bossId, 1L, 1L, this::loop);
        actionBar(fighters(), "§2La tierra se abre... §7Pararte en baldosas sin pintar te salva.");
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        if (task != null) {
            task.cancel();
            task = null;
        }
        waves.clear();
        if (pedestal != null) {
            try {
                pedestal.clear();
            } catch (RuntimeException failure) {
                support.logger().log(Level.WARNING, "[Mazmorras] Pachamama: fallo al sacar el pedestal", failure);
            }
        }
        if (mandinga != null) {
            try {
                mandinga.clear();
            } catch (RuntimeException failure) {
                support.logger().log(Level.WARNING, "[Mazmorras] Pachamama: fallo al sacar a Mandinga", failure);
            }
        }
        if (floor != null) {
            try {
                floor.restoreAll();
                dryStrays();
            } catch (RuntimeException failure) {
                support.logger().log(Level.WARNING, "[Mazmorras] Pachamama: fallo al restaurar el piso", failure);
            }
        }
        support.cleanup(bossId);
    }

    @Override
    public void tick(Instant now) {
        // everything runs in the 1-tick task of start()
    }

    // ------------------------------------------------------------------------------------------------ rules

    @Override
    public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        boolean note = MandingaGuitar.noteStriking();
        double admitted = PachamamaRules.admit(phase == 1, vulnerable(), note, context.health(),
            context.maximumHealth(), damage);
        if (admitted <= 0.0 && attacker != null) {
            hint(attacker, riseLeft > 0
                ? "§7Pachamama todavía sale de la tierra..."
                : "§5Mandinga la cubre mientras sale de su espalda. §7Esperá un momento.");
        } else if (note && attacker != null) {
            Location at = context.boss().getLocation().add(0.0, 2.5, 0.0);
            at.getWorld().spawnParticle(Particle.NOTE, at, 0, 0.9, 0.0, 0.0, 1.0);
        }
        return admitted;
    }

    @Override
    public OptionalInt phaseOverride() {
        return OptionalInt.of(phase);
    }

    @Override
    public boolean suspendsAbilities() {
        return !vulnerable();
    }

    private boolean vulnerable() {
        return riseLeft <= 0 && transitionLeft <= 0;
    }

    // ------------------------------------------------------------------------------------------------ loop

    private void loop() {
        if (stopped) {
            return;
        }
        Warden boss = context.boss();
        if (!boss.isValid() || boss.isDead()) {
            return;
        }
        try {
            tick++;
            List<Player> players = fighters();
            if (riseLeft > 0) {
                rising(players);
            } else if (phase == 1 && PachamamaRules.entersPhaseTwo(context.health(), context.maximumHealth())) {
                beginPhaseTwo(players);
            }
            if (transitionLeft > 0 && --transitionLeft == 0) {
                mandingaOffers(players);
            }
            if (riseLeft <= 0 && transitionLeft <= 0) {
                for (PachamamaRoundCycle.Cue cue : rounds.advance(phase == 2)) {
                    roundCue(cue, players);
                }
                if (rounds.state() == PachamamaRoundCycle.State.PAINTED) {
                    telegraph(players);
                }
                if (phase == 2) {
                    phaseTwo(players);
                }
            }
            move(boss, players);
            if (mandinga.present()) {
                mandinga.follow(boss.getLocation());
            }
            if (tick % 10 == 0) {
                veins(boss);
            }
        } catch (RuntimeException failure) {
            support.logger().log(Level.WARNING, "[Mazmorras] Pachamama: fallo en el tick del encuentro", failure);
        }
    }

    private void rising(List<Player> players) {
        riseLeft--;
        Location at = context.boss().getLocation();
        if (tick % 4 == 0) {
            world.spawnParticle(Particle.BLOCK, at.clone().add(0.0, 0.3, 0.0), 30, 1.6, 0.3, 1.6, 0.0,
                Material.ROOTED_DIRT.createBlockData());
            world.spawnParticle(Particle.DUST_PLUME, at.clone().add(0.0, 0.2, 0.0), 10, 1.4, 0.1, 1.4, 0.02);
        }
        if (tick % 12 == 1) {
            sound(Sound.BLOCK_ROOTED_DIRT_BREAK, 2.5f, 0.5f);
            sound(Sound.ENTITY_WARDEN_DIG, 1.6f, 0.7f);
        }
        if (riseLeft == 0) {
            sound(Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.8f);
            for (Player player : players) {
                player.showTitle(Title.title(LEGACY.deserialize("§2Pachamama"),
                    LEGACY.deserialize("§5corrompida por Mandinga"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ rounds

    private void roundCue(PachamamaRoundCycle.Cue cue, List<Player> players) {
        switch (cue) {
            case PAINT -> paint(players);
            case STRIKE -> strike(players);
            case PULSE -> pulse(players, false);
            case CLEAR -> clearRound();
        }
    }

    private void paint(List<Player> players) {
        if (usable.isEmpty()) {
            return;
        }
        round = PachamamaRoundPlanner.plan(usable, phase == 2, previousColour, random);
        previousColour = round.colour();
        Material paint = paintMaterial(round.colour());
        Map<Block, BlockData> batch = new LinkedHashMap<>();
        Set<Long> columns = new HashSet<>();
        BlockData data = paint.createBlockData();
        for (PachamamaTile tile : round.painted()) {
            for (int[] column : arena.columns(tile)) {
                Integer floorY = paintable.get(key(column[0], column[1]));
                if (floorY == null) {
                    continue;
                }
                Block block = world.getBlockAt(column[0], floorY, column[1]);
                if (!stillPaintable(block)) {
                    continue;
                }
                batch.put(block, data);
                columns.add(key(column[0], column[1]));
            }
        }
        if (floor.change(batch)) {
            roundBlocks.addAll(batch.keySet());
            paintedColumns.addAll(columns);
        }
        if (round.colour() == PachamamaColour.BLUE) {
            waterBefore = waterCells(PachamamaWaterSweep.area(paintedColumns));
        }
        context.animate("cast");
        sound(Sound.BLOCK_ROOTED_DIRT_PLACE, 2.5f, 0.6f);
        sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2.0f, 0.5f);
        PachamamaColour colour = round.colour();
        actionBar(players, colour.code() + "§lEl suelo se pinta de " + colour.spanish() + "! §7Salí de esas baldosas.");
    }

    /** While the cells are painted: particles over them, a ticking sound and, for brown, the first tremors. */
    private void telegraph(List<Player> players) {
        if (round == null) {
            return;
        }
        int left = rounds.left();
        if (tick % 5 == 0) {
            Particle.DustOptions dust = new Particle.DustOptions(dustColour(round.colour()), 1.3f);
            for (PachamamaTile tile : round.painted()) {
                double[] centre = arena.tileCenter(tile);
                world.spawnParticle(Particle.DUST, new Location(world, centre[0], arena.floorY() + 0.2, centre[1]),
                    2, 0.9, 0.05, 0.9, 0.0, dust);
            }
        }
        if (left % 10 == 0 && left > 0) {
            sound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 2.0f, 0.5f + (20 - Math.min(20, left / 3)) * 0.04f);
        }
        if (left == 20) {
            actionBar(players, round.colour().code() + "§l¡Ya viene! §7Pisá las baldosas sin color.");
        }
        if (round.colour() == PachamamaColour.BROWN && tick % 8 == 0) {
            for (PachamamaTile tile : round.painted()) {
                double[] centre = arena.tileCenter(tile);
                world.spawnParticle(Particle.BLOCK, new Location(world, centre[0], arena.floorY() + 0.1, centre[1]),
                    3, 0.8, 0.05, 0.8, 0.0, Material.DIRT.createBlockData());
            }
        }
    }

    private void strike(List<Player> players) {
        if (round == null) {
            return;
        }
        PachamamaColour colour = round.colour();
        actionBar(players, colour.code() + "§l" + colour.strike());
        switch (colour) {
            case BLUE -> blueStrike(players);
            case YELLOW -> pulse(players, true);
            case BROWN -> {
                sound(Sound.ENTITY_RAVAGER_ROAR, 2.0f, 0.5f);
                pulse(players, true);
            }
        }
    }

    private void blueStrike(List<Player> players) {
        Map<Block, BlockData> water = new LinkedHashMap<>();
        BlockData source = Material.WATER.createBlockData();
        for (long column : paintedColumns) {
            Integer floorY = paintable.get(column);
            if (floorY == null) {
                continue;
            }
            Block above = world.getBlockAt((int) (column >> 32), floorY + 1, (int) column);
            if (above.getType().isAir()) {
                water.put(above, source);
            }
        }
        if (floor.change(water)) {
            roundBlocks.addAll(water.keySet());
        }
        sound(Sound.ITEM_BUCKET_EMPTY, 2.5f, 0.6f);
        List<PachamamaTile> tiles = new ArrayList<>(round.painted());
        for (int index = 0; index < Math.min(4, tiles.size()); index++) {
            double[] centre = arena.tileCenter(tiles.get(random.nextInt(tiles.size())));
            world.strikeLightningEffect(new Location(world, centre[0], arena.floorY(), centre[1]));
        }
        for (Player player : players) {
            if (onPainted(player)) {
                world.strikeLightningEffect(player.getLocation());
                player.damage(PachamamaTuning.LIGHTNING_DAMAGE, context.boss());
            }
        }
    }

    /** The effect on whoever stands on the colour; {@code first} is the strike itself. */
    private void pulse(List<Player> players, boolean first) {
        if (round == null) {
            return;
        }
        for (Player player : players) {
            if (!onPainted(player)) {
                continue;
            }
            switch (round.colour()) {
                case BLUE -> {
                    if (player.isInWater()) {
                        player.damage(PachamamaTuning.WATER_DAMAGE, context.boss());
                        world.spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0.0, 0.5, 0.0), 12,
                            0.3, 0.3, 0.3, 0.1);
                    }
                }
                case YELLOW -> {
                    int drain = first ? PachamamaTuning.HUNGER_FOOD_DRAIN : 1;
                    player.setFoodLevel(Math.max(0, player.getFoodLevel() - drain));
                    player.setSaturation(0.0f);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, PachamamaTuning.HUNGER_TICKS,
                        PachamamaTuning.HUNGER_AMPLIFIER, false, true, true));
                    world.spawnParticle(Particle.FALLING_DUST, player.getLocation().add(0.0, 1.8, 0.0), 10, 0.3, 0.2,
                        0.3, 0.0, Material.YELLOW_CONCRETE_POWDER.createBlockData());
                    if (first) {
                        hint(player, "§e¡Tenés un hambre terrible! §7Comé algo y salí del amarillo.");
                    }
                }
                case BROWN -> {
                    if (PachamamaRules.onFloor(player.getLocation().getY(), arena.floorY())) {
                        player.damage(PachamamaTuning.QUAKE_DAMAGE, context.boss());
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, PachamamaTuning.NAUSEA_TICKS,
                            0, false, true, true));
                        Vector velocity = player.getVelocity();
                        player.setVelocity(new Vector(velocity.getX() + (random.nextDouble() - 0.5) * 0.3,
                            PachamamaTuning.QUAKE_LIFT, velocity.getZ() + (random.nextDouble() - 0.5) * 0.3));
                    }
                }
            }
        }
        if (round.colour() == PachamamaColour.BROWN) {
            sound(Sound.ENTITY_RAVAGER_STEP, 2.5f, 0.5f);
            sound(Sound.BLOCK_ROOTED_DIRT_BREAK, 2.5f, 0.6f);
            for (PachamamaTile tile : round.painted()) {
                double[] centre = arena.tileCenter(tile);
                world.spawnParticle(Particle.BLOCK, new Location(world, centre[0], arena.floorY() + 0.2, centre[1]),
                    8, 1.0, 0.2, 1.0, 0.0, Material.COARSE_DIRT.createBlockData());
            }
        } else if (round.colour() == PachamamaColour.BLUE) {
            sound(Sound.BLOCK_WATER_AMBIENT, 2.0f, 1.2f);
        }
    }

    private void clearRound() {
        floor.restore(roundBlocks);
        dryStrays();
        roundBlocks.clear();
        paintedColumns.clear();
        round = null;
    }

    /**
     * After a blue round: water the journal does not know (a source the infinite-water rule made between two of
     * ours) at the water level over the painted columns and one block around, inside the room; water that was
     * there when the round started stays.
     */
    private void dryStrays() {
        if (waterBefore == null) {
            return;
        }
        Set<Long> area = PachamamaWaterSweep.area(paintedColumns);
        int y = arena.floorY();
        List<Long> strays = PachamamaWaterSweep.strays(area, waterBefore,
            column -> world.getBlockAt(PachamamaWaterSweep.x(column), y, PachamamaWaterSweep.z(column)).getType()
                == Material.WATER,
            column -> arena.contains(PachamamaWaterSweep.x(column) + 0.5, PachamamaWaterSweep.z(column) + 0.5));
        for (long column : strays) {
            world.getBlockAt(PachamamaWaterSweep.x(column), y, PachamamaWaterSweep.z(column))
                .setType(Material.AIR, true);
        }
        waterBefore = null;
    }

    /** Columns of {@code area} with water at the water level of the blue rounds. */
    private Set<Long> waterCells(Set<Long> area) {
        Set<Long> water = new HashSet<>();
        int y = arena.floorY();
        for (long column : area) {
            if (world.getBlockAt(PachamamaWaterSweep.x(column), y, PachamamaWaterSweep.z(column)).getType()
                == Material.WATER) {
                water.add(column);
            }
        }
        return water;
    }

    private boolean onPainted(Player player) {
        Location at = player.getLocation();
        return paintedColumns.contains(key(at.getBlockX(), at.getBlockZ()))
            && PachamamaRules.onFloor(at.getY(), arena.floorY());
    }

    // ------------------------------------------------------------------------------------------------ phase 2

    private void beginPhaseTwo(List<Player> players) {
        phase = 2;
        transitionLeft = PachamamaTuning.TRANSITION_TICKS;
        if (rounds.reset(PachamamaTuning.FIRST_CHORD_TICKS / 2)) {
            clearRound();
        }
        stopWalking();
        context.animate("rage");
        sound(Sound.ENTITY_WARDEN_ROAR, 3.0f, 0.6f);
        sound(Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2.5f, 0.5f);
        mandinga.spawn(context.boss().getLocation());
        Location back = context.boss().getLocation().add(0.0, 3.0, 0.0);
        world.spawnParticle(Particle.SCULK_SOUL, back, 40, 0.6, 0.8, 0.6, 0.05);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, back, 30, 0.5, 0.6, 0.5, 0.02);
        actionBar(players, "§4¡Mandinga sale de la espalda de Pachamama!");
    }

    /** End of the transition: the pedestal with the guitar, the offer, his serpents. */
    private void mandingaOffers(List<Player> players) {
        chords = new PachamamaChordCycle();
        raisePedestal();
        offer(players);
        int vipers = PachamamaTuning.vipers(players.size());
        for (int index = 0; index < vipers; index++) {
            double angle = index * 2.0 * Math.PI / vipers + random.nextDouble();
            Location at = new Location(world, arena.clampX(arena.centerX() + Math.cos(angle) * 9.0, 2.0),
                arena.floorY(), arena.clampZ(arena.centerZ() + Math.sin(angle) * 9.0, 2.0));
            support.spawnMinion(bossId, "salamanca_viper", at);
            world.spawnParticle(Particle.SMOKE, at.clone().add(0.0, 0.5, 0.0), 20, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private void offer(List<Player> players) {
        offeredAt = tick;
        mandinga.animate("offer");
        sound(Sound.ENTITY_WITCH_CELEBRATE, 2.0f, 0.5f);
        if (!pedestal.raised()) {
            return;
        }
        for (Player player : players) {
            player.sendMessage(LEGACY.deserialize("§4Mandinga: §7\"¿Querés mi guitarra, che? Te la cambio por un "
                + "corazón... §cpara siempre§7.\""));
            player.sendMessage(LEGACY.deserialize("§8(Click derecho dos veces en el pedestal, o quedate agachado al lado "
                + "3 segundos. Una sola vez por jugador.)"));
        }
    }

    private void raisePedestal() {
        Optional<ItemStack> guitar = MandingaGuitar.create(support.plugin());
        if (guitar.isEmpty()) {
            support.logger().warning("[Mazmorras] Pachamama: el catalogo no tiene la guitarra de Mandinga; "
                + "sin pedestal.");
            return;
        }
        PachamamaTile best = null;
        double bestScore = Double.MAX_VALUE;
        for (PachamamaTile tile : usable) {
            double[] centre = arena.tileCenter(tile);
            if (!paintable.containsKey(key((int) Math.floor(centre[0]), (int) Math.floor(centre[1])))) {
                continue;
            }
            double score = Math.abs(Math.hypot(centre[0] - spawnX - 0.5, centre[1] - spawnZ - 0.5) - 7.0);
            if (score < bestScore) {
                bestScore = score;
                best = tile;
            }
        }
        if (best == null) {
            return;
        }
        double[] centre = arena.tileCenter(best);
        int bx = (int) Math.floor(centre[0]);
        int bz = (int) Math.floor(centre[1]);
        int floorY = paintable.get(key(bx, bz));
        if (pedestal.raise(world, bx, floorY + 1, bz, guitar.get())) {
            usable = usableTiles(best);
        }
    }

    private void phaseTwo(List<Player> players) {
        if (chords != null) {
            chords.advance().ifPresent(cue -> {
                if (cue == PachamamaChordCycle.Cue.STRUM) {
                    strum(players);
                } else {
                    Location at = context.boss().getLocation();
                    waves.add(new PachamamaShockwave(at.getX(), at.getZ()));
                    sound(Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.6f);
                }
            });
        }
        Iterator<PachamamaShockwave> iterator = waves.iterator();
        while (iterator.hasNext()) {
            PachamamaShockwave wave = iterator.next();
            if (!wave.advance()) {
                iterator.remove();
                continue;
            }
            drawWave(wave);
            for (Player player : players) {
                Location at = player.getLocation();
                if (wave.hits(player.getUniqueId(), at.getX(), at.getZ(), player.isOnGround())) {
                    player.damage(PachamamaTuning.WAVE_DAMAGE, context.boss());
                    Vector out = new Vector(at.getX() - wave.centerX(), 0.0, at.getZ() - wave.centerZ());
                    out = out.lengthSquared() < 1.0e-6 ? new Vector(0, 0, 0) : out.normalize().multiply(0.35);
                    player.setVelocity(out.setY(0.35));
                    world.playSound(at, Sound.BLOCK_NOTE_BLOCK_GUITAR, SoundCategory.HOSTILE, 1.2f, 0.5f);
                }
            }
        }
        if (pedestal.raised()) {
            pedestal.tick(tick, players);
            deals(players);
            if (tick - offeredAt >= PachamamaTuning.OFFER_REPEAT_TICKS) {
                offer(players);
            }
        }
    }

    private void strum(List<Player> players) {
        mandinga.animate("strum");
        Location at = mandinga.present() ? mandinga.location() : context.boss().getLocation();
        for (float pitch : new float[] {0.5f, 0.63f, 0.75f}) {
            world.playSound(at, Sound.BLOCK_NOTE_BLOCK_GUITAR, SoundCategory.HOSTILE, 2.5f, pitch);
        }
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.0f, 1.4f);
        world.spawnParticle(Particle.NOTE, at.clone().add(0.0, 0.8, 0.0), 0, 0.3, 0.0, 0.0, 1.0);
        Location ground = context.boss().getLocation();
        for (int index = 0; index < 16; index++) {
            double angle = index * Math.PI / 8.0;
            world.spawnParticle(Particle.DUST, new Location(world, ground.getX() + Math.cos(angle) * 1.5,
                arena.floorY() + 0.15, ground.getZ() + Math.sin(angle) * 1.5), 1, 0.0, 0.0, 0.0, 0.0, WAVE_DUST);
        }
        actionBar(players, "§5¡Mandinga rasguea la guitarra! §f¡Saltá la onda cuando llegue!");
    }

    private void drawWave(PachamamaShockwave wave) {
        double radius = wave.radius();
        int points = (int) Math.max(12, Math.min(96, radius * 6));
        double y = arena.floorY() + 0.15;
        for (int index = 0; index < points; index++) {
            double angle = index * 2.0 * Math.PI / points;
            double px = wave.centerX() + Math.cos(angle) * radius;
            double pz = wave.centerZ() + Math.sin(angle) * radius;
            if (!arena.contains(px, pz)) {
                continue;
            }
            Location at = new Location(world, px, y, pz);
            world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, WAVE_DUST);
            if (index % 8 == 0) {
                world.spawnParticle(Particle.NOTE, at.add(0.0, 0.3, 0.0), 0, radius / PachamamaTuning.WAVE_MAX_RADIUS,
                    0.0, 0.0, 1.0);
            }
        }
    }

    /** Clicks on the pedestal (a second one confirms) and sneak-holds next to it. */
    private void deals(List<Player> players) {
        dealConfirm.expire(tick);
        pedestal.poll().filter(players::contains).ifPresent(this::clicked);
        Location top = pedestal.top();
        Set<UUID> holding = new HashSet<>();
        for (Player player : players) {
            Location at = player.getLocation();
            if (player.isSneaking() && Math.hypot(at.getX() - top.getX(), at.getZ() - top.getZ()) <= DEAL_REACH
                && Math.abs(at.getY() - (top.getY() - 1.0)) <= 2.0) {
                holding.add(player.getUniqueId());
            }
        }
        UUID done = dealHold.tick(holding);
        for (Player player : players) {
            int progress = dealHold.progress(player.getUniqueId());
            if (progress > 0 && progress % 20 == 0) {
                player.sendActionBar(LEGACY.deserialize("§4Pacto con Mandinga... §f"
                    + (3 - progress / 20) + "s §7(levantate para arrepentirte)"));
            }
            if (player.getUniqueId().equals(done)) {
                deal(player);
            }
        }
    }

    /** Any right-click looking at the pedestal lands here, so the first one only asks for a second. */
    private void clicked(Player player) {
        if (MandingaGuitar.check(player) != MandingaGuitarPactRule.Outcome.ACCEPT) {
            deal(player);
            return;
        }
        switch (dealConfirm.click(player.getUniqueId(), tick)) {
            case CONFIRMED -> deal(player);
            case ARMED -> player.showTitle(Title.title(LEGACY.deserialize(""),
                LEGACY.deserialize("§4Hacé click otra vez en 3 s para pagar un corazón"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2600), Duration.ofMillis(300))));
            case HELD -> {
                // a held use key: wait for a real second click
            }
        }
    }

    private void deal(Player player) {
        MandingaGuitarPactRule.Outcome check = MandingaGuitar.check(player);
        if (check == MandingaGuitarPactRule.Outcome.ALREADY_PAID) {
            hint(player, "§4Mandinga: §7\"Vos ya me diste tu corazón. La guitarra ya es tuya.\"");
            return;
        }
        if (check == MandingaGuitarPactRule.Outcome.TOO_WEAK) {
            hint(player, "§4Mandinga: §7\"No te queda corazón para darme, gurí.\"");
            return;
        }
        Optional<ItemStack> guitar = MandingaGuitar.create(support.plugin());
        if (guitar.isEmpty()) {
            hint(player, "§7La guitarra se deshace en humo. §8(No está en el catálogo de items.)");
            return;
        }
        if (MandingaGuitar.payHeart(player) != MandingaGuitarPactRule.Outcome.ACCEPT) {
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(guitar.get());
        for (ItemStack rest : left.values()) {
            player.getWorld().dropItem(player.getLocation(), rest, item -> {
                PachamamaPaidDrop.protect(item, player.getUniqueId());
                item.setCanMobPickup(false);
            });
        }
        Location at = player.getLocation().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.DAMAGE_INDICATOR, at, 10, 0.3, 0.4, 0.3, 0.1);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 30, 0.4, 0.6, 0.4, 0.04);
        world.playSound(at, Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.HOSTILE, 1.5f, 0.6f);
        world.playSound(at, Sound.BLOCK_NOTE_BLOCK_GUITAR, SoundCategory.PLAYERS, 1.5f, 1.0f);
        mandinga.animate("offer");
        player.showTitle(Title.title(LEGACY.deserialize("§4Trato hecho"),
            LEGACY.deserialize("§7Perdiste un corazón para siempre. §6La guitarra es tuya."),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500))));
        for (Player other : fighters()) {
            if (!other.equals(player)) {
                other.sendMessage(LEGACY.deserialize("§4" + player.getName()
                    + " §7le vendió un corazón a Mandinga."));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ world

    /** Walks toward the nearest fighter (slowly), always facing them; never during the rise or the transition. */
    private void move(Warden boss, List<Player> players) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : players) {
            double distance = Math.hypot(player.getLocation().getX() - x, player.getLocation().getZ() - z);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null && vulnerable() && best > STOP_DISTANCE) {
            double dx = nearest.getLocation().getX() - x;
            double dz = nearest.getLocation().getZ() - z;
            x = arena.clampX(x + dx / best * WALK_SPEED, 1.5);
            z = arena.clampZ(z + dz / best * WALK_SPEED, 1.5);
            if (!walking) {
                walking = true;
                context.animate("walk");
            }
        } else {
            stopWalking();
        }
        Location to = new Location(world, x, groundY(x, z), z);
        if (nearest != null) {
            Vector look = nearest.getLocation().toVector().subtract(to.toVector());
            to.setDirection(look.lengthSquared() < 1.0e-6 ? new Vector(0, 0, 1) : look);
            to.setPitch(0.0f);
        } else {
            to.setYaw(boss.getLocation().getYaw());
        }
        boss.teleport(to);
        boss.setVelocity(new Vector());
    }

    private void stopWalking() {
        if (walking) {
            walking = false;
            support.models().stopAnimation(context.model(), "walk");
        }
    }

    /** Top of the first solid block under (x, z) near the floor (water is not solid). */
    private double groundY(double atX, double atZ) {
        int bx = (int) Math.floor(atX);
        int bz = (int) Math.floor(atZ);
        for (int y = arena.floorY() + 2; y >= arena.floorY() - 2; y--) {
            if (!world.getBlockAt(bx, y, bz).isPassable()) {
                return y + 1.0;
            }
        }
        return arena.floorY();
    }

    /** The corruption: violet sparks around her (and around Mandinga once he is out). */
    private void veins(Warden boss) {
        Location at = boss.getLocation().add(0.0, 2.0, 0.0);
        world.spawnParticle(Particle.DUST, at, 3, 0.8, 1.2, 0.8, 0.0, VEIN_DUST);
        if (phase == 2) {
            world.spawnParticle(Particle.SMOKE, at.clone().add(0.0, 1.4, 0.0), 2, 0.3, 0.2, 0.3, 0.0);
        }
    }

    private void scanFloor(Optional<DungeonBounds> bounds) {
        String worldName = world.getName();
        for (int bx = arena.minX(); bx <= arena.maxX(); bx++) {
            for (int bz = arena.minZ(); bz <= arena.maxZ(); bz++) {
                if (bounds.isPresent() && !bounds.get().contains(worldName, bx + 0.5, arena.floorY(), bz + 0.5)) {
                    continue;
                }
                Block block = world.getBlockAt(bx, arena.floorY() - 1, bz);
                if (stillPaintable(block)) {
                    paintable.put(key(bx, bz), block.getY());
                }
            }
        }
    }

    /** A full, plain floor block with two free blocks above (not a slab, stairs, chest or the altar). */
    private boolean stillPaintable(Block block) {
        Material type = block.getType();
        if (!type.isSolid() || !type.isOccluding() || type.hasGravity() || type == Material.BEDROCK
            || type == Material.CHISELED_POLISHED_BLACKSTONE) {
            return false;
        }
        if (floor.changed(block) || block.getState(false) instanceof TileState) {
            return false;
        }
        return block.getRelative(0, 1, 0).getType().isAir() && block.getRelative(0, 2, 0).isPassable();
    }

    /** Tiles with enough paintable columns, away from the altar (under the spawn) and the pedestal. */
    private List<PachamamaTile> usableTiles(PachamamaTile pedestalTile) {
        List<PachamamaTile> tiles = new ArrayList<>();
        PachamamaTile altar = arena.tileAt(spawnX, spawnZ);
        for (PachamamaTile tile : arena.tiles()) {
            if (Math.abs(tile.column() - altar.column()) <= 1 && Math.abs(tile.row() - altar.row()) <= 1) {
                continue;
            }
            if (tile.equals(pedestalTile)) {
                continue;
            }
            int columns = 0;
            for (int[] column : arena.columns(tile)) {
                if (paintable.containsKey(key(column[0], column[1]))) {
                    columns++;
                }
            }
            if (columns >= PachamamaTuning.MIN_TILE_COLUMNS) {
                tiles.add(tile);
            }
        }
        return List.copyOf(tiles);
    }

    private static Material paintMaterial(PachamamaColour colour) {
        return switch (colour) {
            case BLUE -> Material.BLUE_CONCRETE;
            case BROWN -> Material.BROWN_CONCRETE;
            case YELLOW -> Material.YELLOW_CONCRETE;
        };
    }

    private static Color dustColour(PachamamaColour colour) {
        return switch (colour) {
            case BLUE -> Color.fromRGB(60, 90, 255);
            case BROWN -> Color.fromRGB(120, 72, 36);
            case YELLOW -> Color.fromRGB(250, 220, 40);
        };
    }

    private static long key(int bx, int bz) {
        return PachamamaWaterSweep.key(bx, bz);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static PachamamaArena measure(World world, Location at) {
        PachamamaArena.Passable passable = (bx, by, bz) -> world.getBlockAt(bx, by, bz).isPassable();
        int floorY = PachamamaArena.floorUnder(at.getBlockX(), at.getBlockY(), at.getBlockZ(), passable);
        return PachamamaArena.probe(at.getBlockX(), at.getBlockZ(), floorY, passable);
    }

    /** The audience inside the room. */
    private List<Player> fighters() {
        List<Player> inside = new ArrayList<>();
        for (Player player : context.players()) {
            Location at = player.getLocation();
            if (at.getWorld().equals(world) && arena.containsFeet(at.getX(), at.getY(), at.getZ())) {
                inside.add(player);
            }
        }
        return inside;
    }

    private void actionBar(List<Player> players, String text) {
        for (Player player : players) {
            player.sendActionBar(LEGACY.deserialize(text));
        }
    }

    private void hint(Player player, String text) {
        Long last = hints.get(player.getUniqueId());
        if (last == null || tick - last >= HINT_COOLDOWN_TICKS) {
            hints.put(player.getUniqueId(), tick);
            player.sendActionBar(LEGACY.deserialize(text));
        }
    }

    private void sound(Sound sound, float volume, float pitch) {
        Location at = context.boss().getLocation();
        at.getWorld().playSound(at, sound, SoundCategory.HOSTILE, volume, pitch);
    }
}
