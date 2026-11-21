package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * El jefe del Sueño: un ojo con alas (modelo 3D: el ojo y cada ala son piezas separadas, las alas aletean). Una sola
 * fase: vuela alrededor tuyo mirandote, carga y tira un rayo rojo como el del warden (menos daño), y cada tanto baja
 * en parabola hasta tu altura y vuelve a subir: ahi se le pega con la mano. Con 3 golpes se despide y te
 * despierta en el overworld. Solo lo ve quien lo invoco.
 */
final class EyeBoss {

    private static final float SCALE = 3.4f;
    private static final double BEAM_DAMAGE = 4.0; // el warden pega 10
    private static final double BEAM_RANGE = 40;
    private static final int HITS_TO_KILL = 3;
    private static final int CHARGE_TICKS = 24;
    private static final int SWOOP_TICKS = 70;
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(230, 20, 40), 1.8f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(255, 240, 250), 1.6f);

    private static final List<String> FAREWELL = List.of(
            "Nos volveremos a ver,", "ya que mente tienen todos,", "pero sueños no tiene cualquiera,",
            "nos veremos allí afuera.");
    /** Le gano al ojo y todavia no se desperto (sobrevive a desconexiones y reinicios). */
    static final NamespacedKey WAKE_PENDING = new NamespacedKey("caichet", "dream_wake_pending");

    private static final List<String> WAKE = List.of("Despertarse", "Ahora sí", "Creo...", "No", "Ahora sí", "Ya está");

    private enum State { APPEAR, HOVER, CHARGE, SWOOP, DYING }

    private final Plugin plugin;
    final Player owner;
    private final ItemDisplay body;
    private final ItemDisplay wingL;
    private final ItemDisplay wingR;
    final Interaction hitbox;
    /** Cuerpo para quien juega desde Bedrock (no ve displays ni Interaction): un allay grande, con alas (los monstruos no se pueden crear en Pacifico). */
    final Allay guardian;
    private final BossBar bar;

    private Location pos;
    private State state = State.APPEAR;
    private int t;
    private int stateT;
    private int hits;
    private int hitCooldown;
    private int beamsSinceSwoop;
    private double orbit = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
    private Vector beamFrom;
    private Vector beamTo;
    private Location swoopStart;
    private Location swoopEnd;
    private double swoopLow;
    private boolean finished;

    EyeBoss(Plugin plugin, Player owner, Location start) {
        this.plugin = plugin;
        this.owner = owner;
        this.pos = start.clone();
        World world = start.getWorld();
        body = part(world, "boss_eye");
        wingL = part(world, "boss_wing_l");
        wingR = part(world, "boss_wing_r");
        hitbox = world.spawn(start, Interaction.class, i -> {
            i.setInteractionWidth(SCALE * 0.9f);
            i.setInteractionHeight(SCALE * 0.9f);
            i.setResponsive(true);
            i.setPersistent(false);
            i.setVisibleByDefault(false);
        });
        if (Bedrock.is(owner)) {
            guardian = world.spawn(start, Allay.class, g -> {
                g.setAI(false);
                g.setSilent(true);
                g.setGravity(false);
                g.setInvulnerable(true);
                g.setPersistent(false);
                g.setRemoveWhenFarAway(false);
                g.setVisibleByDefault(false);
                Bedrock.markStand(g);
                CaiChetBedrockEntities.present(g, CaiChetBedrockEntities.BOSS_EYE);
                AttributeInstance scale = g.getAttribute(Attribute.SCALE);
                if (scale != null) {
                    scale.setBaseValue(4.0);
                }
            });
            owner.showEntity(plugin, guardian);
        } else {
            guardian = null;
            owner.showEntity(plugin, hitbox);
        }
        bar = BossBar.bossBar(Component.text("El Ojo", NamedTextColor.LIGHT_PURPLE), 1f,
                BossBar.Color.PINK, BossBar.Overlay.NOTCHED_6);
        owner.showBossBar(bar);
        CaiChetSounds.play(world, start, CaiChetSounds.EYE_ROAR, SoundCategory.HOSTILE, 3f, 1f);
        world.playSound(start, Sound.ENTITY_WARDEN_EMERGE, 0.5f, 1.6f);
        owner.showTitle(Title.title(Component.empty(), Component.text("algo te está mirando", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, true), Title.Times.times(Duration.ofMillis(600),
                Duration.ofMillis(1800), Duration.ofMillis(800))));
        place();
    }

    private ItemDisplay part(World world, String model) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey("caichet", model));
        stack.setItemMeta(meta);
        ItemDisplay display = world.spawn(pos, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(transform(0f));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(3);
            d.setInterpolationDuration(3);
            d.setViewRange(4f);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
        });
        if (!Bedrock.is(owner)) {
            owner.showEntity(plugin, display);
        }
        return display;
    }

    private static Transformation transform(float flapRadians) {
        return new Transformation(new Vector3f(), new AxisAngle4f(flapRadians, 0f, 0f, 1f),
                new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f());
    }

    // ------------------------------------------------------------------ cada tick

    /** @return false cuando termino (se fue o se despidio) */
    boolean tick() {
        if (finished) {
            return false;
        }
        t++;
        stateT++;
        if (hitCooldown > 0) {
            hitCooldown--;
        }
        switch (state) {
            case APPEAR -> {
                pos.add(0, 0.06, 0);
                if (stateT >= 40) {
                    setState(State.HOVER);
                }
            }
            case HOVER -> hover();
            case CHARGE -> charge();
            case SWOOP -> swoop();
            case DYING -> {
                dying();
                if (finished) {
                    return false;
                }
            }
        }
        place();
        return true;
    }

    private void setState(State next) {
        state = next;
        stateT = 0;
    }

    /** Da vueltas alrededor tuyo, arriba, y cada tanto carga el rayo o baja en picada. */
    private void hover() {
        orbit += 0.018;
        Location target = owner.getLocation().add(Math.cos(orbit) * 9, 7 + Math.sin(t * 0.05) * 1.2, Math.sin(orbit) * 9);
        pos.add(target.toVector().subtract(pos.toVector()).multiply(0.06));
        if (stateT >= 70) {
            if (beamsSinceSwoop >= 2) {
                startSwoop();
            } else {
                beamFrom = pos.toVector();
                beamTo = owner.getEyeLocation().toVector().subtract(new Vector(0, 0.4, 0));
                owner.playSound(pos, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.4f, 1.3f);
                setState(State.CHARGE);
            }
        }
    }

    /** Se queda quieto juntando luz roja; al final dispara hacia donde estabas cuando empezo. */
    private void charge() {
        World world = pos.getWorld();
        double r = 2.2 * (1 - stateT / (double) CHARGE_TICKS);
        for (int k = 0; k < 4; k++) {
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double b = ThreadLocalRandom.current().nextDouble(-1, 1);
            owner.spawnParticle(Particle.DUST, pos.clone().add(Math.cos(a) * r, b * r, Math.sin(a) * r), 1, 0, 0, 0, 0, RED);
        }
        if (stateT < CHARGE_TICKS) {
            return;
        }
        Vector dir = beamTo.clone().subtract(beamFrom).normalize();
        Vector end = beamFrom.clone().add(dir.clone().multiply(BEAM_RANGE));
        for (double d = 0; d <= BEAM_RANGE; d += 0.5) {
            Location p = beamFrom.clone().add(dir.clone().multiply(d)).toLocation(world);
            owner.spawnParticle(Particle.DUST, p, 2, 0.05, 0.05, 0.05, 0, RED, true);
            if (((int) (d * 2)) % 6 == 0) {
                // Bedrock no mapea con seguridad el sonic boom: ahi se usa una explosion chica
                owner.spawnParticle(Bedrock.is(owner) ? Particle.EXPLOSION : Particle.SONIC_BOOM, p, 1, 0, 0, 0, 0, null, true);
            }
        }
        owner.playSound(pos, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.4f);
        Vector chest = owner.getLocation().toVector().add(new Vector(0, 1, 0));
        if (Watchers.distanceToSegment(chest, beamFrom, end) <= 1.3) {
            owner.damage(BEAM_DAMAGE);
            owner.setVelocity(owner.getVelocity().add(dir.clone().multiply(0.8).setY(0.35)));
        }
        beamsSinceSwoop++;
        setState(State.HOVER);
    }

    private void startSwoop() {
        beamsSinceSwoop = 0;
        swoopStart = pos.clone();
        Location you = owner.getLocation();
        Vector across = you.toVector().subtract(swoopStart.toVector()).setY(0);
        swoopEnd = you.clone().add(across).add(0, swoopStart.getY() - you.getY(), 0);
        swoopLow = you.getY() + 1.4; // a la altura del pecho: se le puede pegar
        owner.playSound(pos, Sound.ENTITY_PHANTOM_SWOOP, 1.5f, 0.7f);
        setState(State.SWOOP);
    }

    /** Baja en parabola pasando a tu altura y vuelve a subir del otro lado. */
    private void swoop() {
        double f = Math.min(1, stateT / (double) SWOOP_TICKS);
        double x = swoopStart.getX() + (swoopEnd.getX() - swoopStart.getX()) * f;
        double z = swoopStart.getZ() + (swoopEnd.getZ() - swoopStart.getZ()) * f;
        double k = (2 * f - 1);
        double y = swoopLow + (swoopStart.getY() - swoopLow) * k * k;
        pos = new Location(pos.getWorld(), x, y, z);
        if (f >= 1) {
            setState(State.HOVER);
        }
    }

    /** Un golpe (con lo que sea). Al tercero se despide. */
    void hit() {
        if (state == State.DYING || state == State.APPEAR || hitCooldown > 0) {
            return;
        }
        hitCooldown = 12;
        hits++;
        bar.progress(Math.max(0f, 1f - hits / (float) HITS_TO_KILL));
        World world = pos.getWorld();
        owner.playSound(pos, Sound.ENTITY_PHANTOM_HURT, 1.5f, 0.6f);
        owner.spawnParticle(Particle.DUST, pos, 40, 0.8, 0.8, 0.8, 0, WHITE, true);
        if (hits >= HITS_TO_KILL) {
            owner.playSound(pos, Sound.ENTITY_PHANTOM_DEATH, 1.5f, 0.5f);
            // la victoria queda anotada ya: la despedida es solo para mirar
            owner.getPersistentDataContainer().set(WAKE_PENDING, PersistentDataType.BYTE, (byte) 1);
            setState(State.DYING);
        } else if (state == State.SWOOP) {
            setState(State.HOVER); // sube enseguida
        }
    }

    // ------------------------------------------------------------------ despedida

    private void dying() {
        // tiembla en el lugar y habla
        pos.add(ThreadLocalRandom.current().nextDouble(-0.06, 0.06), 0.01, ThreadLocalRandom.current().nextDouble(-0.06, 0.06));
        int line = stateT / 60;
        if (stateT % 60 == 1 && line < FAREWELL.size()) {
            owner.showTitle(Title.title(Component.empty(),
                    Component.text(FAREWELL.get(line), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, true),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2400), Duration.ofMillis(300))));
            owner.playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.5f + line * 0.1f);
        }
        if (stateT >= FAREWELL.size() * 60 + 10) {
            owner.spawnParticle(Particle.CLOUD, pos, 80, 1.5, 1.5, 1.5, 0.05, null, true);
            owner.spawnParticle(Particle.END_ROD, pos, 60, 1.5, 1.5, 1.5, 0.1, null, true);
            remove();
            wakeUp(owner);
        }
    }

    /** Te despierta en el overworld con unas frases rapidas. */
    static void wakeUp(Player player) {
        player.getPersistentDataContainer().remove(WAKE_PENDING);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
        World overworld = Bukkit.getWorlds().getFirst();
        Location bed = player.getRespawnLocation();
        player.teleport(bed != null && bed.getWorld().equals(overworld) ? bed : overworld.getSpawnLocation());
        if (!CaiChetRuntime.active()) {
            return; // apagando el servidor: se despierta sin las frases
        }
        Title.Times fast = Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ofMillis(100));
        for (int i = 0; i < WAKE.size(); i++) {
            String text = WAKE.get(i);
            CaiChetRuntime.tasks().later(() -> {
                if (player.isOnline()) {
                    player.showTitle(Title.title(Component.text(text, NamedTextColor.WHITE), Component.empty(), fast));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.2f);
                }
            }, 25L + i * 14L);
        }
    }

    // ------------------------------------------------------------------ dibujo

    /** Pone las piezas donde esta, mirando al jugador, con las alas aleteando. */
    private void place() {
        Vector look = owner.getEyeLocation().toVector().subtract(pos.toVector());
        // el frente del modelo (el iris) queda del lado contrario al "adelante" del display: se da vuelta
        float yaw = (float) Math.toDegrees(Math.atan2(-look.getX(), look.getZ())) + 180f;
        float pitch = (float) Math.toDegrees(Math.atan2(look.getY(), Math.hypot(look.getX(), look.getZ())));
        Location at = pos.clone();
        at.setYaw(yaw);
        at.setPitch(pitch);
        body.teleport(at);
        Location wingAt = at.clone();
        wingAt.setPitch(0);
        wingL.teleport(wingAt);
        wingR.teleport(wingAt);
        if (t % 3 == 0) {
            double speed = state == State.SWOOP ? 0.9 : 0.45;
            float flap = (float) (Math.sin(t * speed) * Math.toRadians(32));
            wingR.setTransformation(transform(flap));
            wingL.setTransformation(transform(-flap));
            wingR.setInterpolationDelay(0);
            wingL.setInterpolationDelay(0);
        }
        hitbox.teleport(pos.clone().subtract(0, SCALE * 0.45, 0));
        if (guardian != null) {
            guardian.teleport(pos.clone().subtract(0, 1.2, 0).setDirection(look.clone().normalize()));
        }
    }

    /** Es el cuerpo del ojo (la caja de choque en Java, el allay en Bedrock)? */
    boolean isBody(Entity entity) {
        return entity.equals(hitbox) || (guardian != null && entity.equals(guardian));
    }

    boolean near(Vector point, double radius) {
        return pos.toVector().distance(point) <= radius;
    }

    /** Ya recibio los 3 golpes (esta despidiendose). */
    boolean defeated() {
        return state == State.DYING;
    }

    Location position() {
        return pos.clone();
    }

    /** Se va sin despedirse (te moriste, te fuiste o te alejaste). */
    void remove() {
        finished = true;
        for (Entity e : new Entity[]{body, wingL, wingR, hitbox}) {
            e.remove();
        }
        CaiChetBedrockEntities.discard(guardian);
        owner.hideBossBar(bar);
    }
}
