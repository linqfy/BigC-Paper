package dev.linqfy.bigCasares.modules.caichet;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Los pasajeros del tren: arriba de la locomotora van los integrantes del team de cada jugador, una figura por
 * persona (parados, sentados, agachados). Cada jugador ve SOLO a los de su team: quien no tiene team no ve a nadie, y
 * dos jugadores de teams distintos ven figuras distintas sobre el mismo tren.
 * <ul>
 *   <li>Java: un maniqui con la skin de cada integrante.</li>
 *   <li>Bedrock con las entidades custom de Geyser: el mismo maniqui (Geyser lo traduce como un jugador con skin) sobre
 *   el techo de la locomotora, que ahi si se ve.</li>
 *   <li>Bedrock sin ellas: un aldeano con el nombre del integrante, sobre los vagones comunes.</li>
 * </ul>
 * Las figuras son entidades que se muestran por jugador (visibles solo para quien corresponde) y se borran con el tren.
 */
final class TrainPassengers {

    /** Tope de figuras por team (cabe el techo de la locomotora en tres filas de cuatro). */
    static final int MAX_FIGURES = 12;
    /**
     * Alturas del techo sobre el suelo, medidas sobre el modelo (ya agrandado): la cabina va adelante y es mas alta que
     * la caldera de atras.
     */
    static final double CAB_Y = 4.65;
    static final double BOILER_Y = 3.45;
    /** Desde que punto (hacia adelante, medido desde el centro de la locomotora) empieza el techo de la cabina. */
    private static final double CAB_FROM = 1.45;
    /** Cuanto sube el asiento invisible respecto del techo, para que quien va sentado quede apoyado. */
    static final double SEAT_Y = 0.2;
    /** Lugares a lo largo del techo: dos sobre la cabina y dos sobre la caldera. */
    private static final double[] ALONG = {3.2, 2.0, -1.6, -3.2};
    /** Filas a lo largo del ancho: primero el centro, despues un lado y el otro. */
    private static final double[] LATERAL = {0.0, 0.65, -0.65};
    /**
     * Bedrock no ve el modelo del tren, solo los cuatro vagones: ahi la gente va sobre ellos (la cubierta queda a esta
     * altura sobre el suelo, en los mismos lugares donde estan los vagones).
     */
    private static final double BEDROCK_DECK_Y = 0.85;
    private static final double[] ALONG_BEDROCK = {3.3, 1.1, -1.1, -3.3};
    private static final double[] LATERAL_BEDROCK = {0.0, 0.3, -0.3};

    static double roofAt(double along) {
        return along >= CAB_FROM ? CAB_Y : BOILER_Y;
    }
    private static final int REFRESH_TICKS = 10;

    private enum Style { STAND, SIT, CROUCH }

    private final World world;
    private final Plugin plugin = Bedrock.plugin();
    /** Por clave de team. */
    private final Map<String, Group> groups = new HashMap<>();

    TrainPassengers(World world) {
        this.world = world;
    }

    // ------------------------------------------------------------------ figuras

    private static final class Figure {
        final Entity body;
        /** Asiento invisible (solo si va sentado). */
        final ArmorStand seat;
        final double along;
        final double lateral;
        final float turn;
        final boolean bedrock;

        Figure(Entity body, ArmorStand seat, double along, double lateral, float turn, boolean bedrock) {
            this.body = body;
            this.seat = seat;
            this.along = along;
            this.lateral = lateral;
            this.turn = turn;
            this.bedrock = bedrock;
        }

        /** La entidad que se mueve (si va sentado, el asiento lleva al pasajero). */
        Entity mover() {
            return seat != null ? seat : body;
        }

        void remove() {
            body.remove();
            if (seat != null) {
                seat.remove();
            }
        }
    }

    private static final class Group {
        final TeamLookup.Roster team;
        final List<Figure> java = new ArrayList<>();
        final List<Figure> bedrock = new ArrayList<>();
        /** Si ya se intento armar cada juego de figuras: un fallo no se reintenta (ni repite el aviso) en cada refresco. */
        boolean builtJava;
        boolean builtBedrock;

        Group(TeamLookup.Roster team) {
            this.team = team;
        }

        List<Figure> figures(boolean forBedrock) {
            return forBedrock ? bedrock : java;
        }

        void remove() {
            java.forEach(Figure::remove);
            bedrock.forEach(Figure::remove);
            java.clear();
            bedrock.clear();
        }
    }

    // ------------------------------------------------------------------ cada tick

    /** Mueve las figuras con el tren. @param yaw hacia donde mira el tren (grados, como Bukkit) */
    void update(double hx, double hy, double hz, double dx, double dz, float yaw) {
        if (groups.isEmpty()) {
            return;
        }
        double rx = -dz; // a la derecha del tren
        double rz = dx;
        for (Group group : groups.values()) {
            for (List<Figure> list : List.of(group.java, group.bedrock)) {
                for (Figure f : list) {
                    if (!f.mover().isValid()) {
                        continue;
                    }
                    double base = f.bedrock ? BEDROCK_DECK_Y : roofAt(f.along);
                    double y = hy + base + (f.seat != null ? SEAT_Y : 0);
                    Location at = new Location(world, hx + dx * f.along + rx * f.lateral, y,
                            hz + dz * f.along + rz * f.lateral, yaw + f.turn, 0f);
                    if (f.seat != null) {
                        f.seat.teleport(at);
                    } else {
                        f.body.teleport(at);
                    }
                }
            }
        }
    }

    /**
     * Cada {@value #REFRESH_TICKS} ticks: arma las figuras de los teams que hay en el mundo, borra las de teams sin
     * nadie, y decide quien ve cual. @param head donde esta la locomotora (para crear las figuras ahi)
     */
    void refresh(Location head) {
        Map<String, TeamLookup.Roster> teamOf = new HashMap<>();
        Map<UUID, String> keyOfPlayer = new HashMap<>();
        Set<String> needJava = new HashSet<>();
        Set<String> needBedrock = new HashSet<>();
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            TeamLookup.Roster team = TeamLookup.of(player);
            if (team == null || team.members().isEmpty()) {
                continue; // sin team: no ve a nadie
            }
            teamOf.put(team.key(), team);
            keyOfPlayer.put(player.getUniqueId(), team.key());
            (separateBedrock(player) ? needBedrock : needJava).add(team.key());
        }
        // teams que ya no tienen a nadie mirando
        for (Iterator<Map.Entry<String, Group>> it = groups.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Group> entry = it.next();
            if (!teamOf.containsKey(entry.getKey())) {
                entry.getValue().remove();
                it.remove();
            }
        }
        for (Map.Entry<String, TeamLookup.Roster> entry : teamOf.entrySet()) {
            Group group = groups.computeIfAbsent(entry.getKey(), k -> new Group(entry.getValue()));
            if (needJava.contains(entry.getKey()) && !group.builtJava) {
                group.builtJava = true;
                build(group, head, false);
            }
            if (needBedrock.contains(entry.getKey()) && !group.builtBedrock) {
                group.builtBedrock = true;
                build(group, head, true);
            }
        }
        // visibilidad: cada jugador ve solo las figuras de su team y de su plataforma
        for (Player player : world.getPlayers()) {
            String key = keyOfPlayer.get(player.getUniqueId());
            boolean bedrock = separateBedrock(player);
            for (Map.Entry<String, Group> entry : groups.entrySet()) {
                for (boolean forBedrock : new boolean[]{false, true}) {
                    boolean visible = entry.getKey().equals(key) && forBedrock == bedrock;
                    for (Figure f : entry.getValue().figures(forBedrock)) {
                        setVisible(player, f, visible);
                    }
                }
            }
        }
    }

    /** Quien ve aldeanos sobre vagones comunes: solo Bedrock sin el modelo de la locomotora (ahi ve los maniquies). */
    private static boolean separateBedrock(Player player) {
        return Bedrock.is(player) && !CaiChetBedrockEntities.available();
    }

    private void setVisible(Player player, Figure f, boolean visible) {
        for (Entity e : f.seat != null ? new Entity[]{f.seat, f.body} : new Entity[]{f.body}) {
            if (visible && !player.canSee(e)) {
                player.showEntity(plugin, e);
            } else if (!visible && player.canSee(e)) {
                player.hideEntity(plugin, e);
            }
        }
    }

    // ------------------------------------------------------------------ armado

    private void build(Group group, Location head, boolean forBedrock) {
        List<TeamLookup.Member> members = group.team.members();
        for (int i = 0; i < members.size() && i < MAX_FIGURES; i++) {
            TeamLookup.Member member = members.get(i);
            // el estilo y el lugar salen del integrante, asi cada uno va siempre igual y no cambia entre refrescos
            int h = Math.floorMod(member.id().hashCode() * 31 + i, 997);
            Style style = Style.values()[h % Style.values().length];
            double[] alongs = forBedrock ? ALONG_BEDROCK : ALONG;
            double[] laterals = forBedrock ? LATERAL_BEDROCK : LATERAL;
            double along = alongs[i % alongs.length];
            double lateral = laterals[(i / alongs.length) % laterals.length];
            float turn = (h % 5 - 2) * 25f;
            try {
                group.figures(forBedrock).add(spawn(member, style, head, along, lateral, turn, forBedrock));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("No pude crear un pasajero del tren (" + member.name() + "): " + e.getMessage());
            }
        }
    }

    private Figure spawn(TeamLookup.Member member, Style style, Location at, double along, double lateral, float turn,
                         boolean forBedrock) {
        ArmorStand seat = null;
        if (style == Style.SIT) {
            seat = world.spawn(at, ArmorStand.class, a -> {
                a.setVisible(false);
                a.setMarker(true);
                a.setSmall(true);
                a.setGravity(false);
                a.setInvulnerable(true);
                a.setPersistent(false);
                a.setVisibleByDefault(false);
                Bedrock.markStand(a);
            });
        }
        Entity body;
        try {
            body = forBedrock ? villager(member, at) : mannequin(member, at, style);
        } catch (RuntimeException e) {
            if (seat != null) {
                seat.remove();
            }
            throw e;
        }
        if (seat != null) {
            seat.addPassenger(body);
        }
        return new Figure(body, seat, along, lateral, turn, forBedrock);
    }

    private Entity mannequin(TeamLookup.Member member, Location at, Style style) {
        Player online = Bukkit.getPlayer(member.id());
        PlayerProfile profile = online != null ? online.getPlayerProfile() : Bukkit.createProfile(member.id(), member.name());
        return world.spawn(at, Mannequin.class, m -> {
            m.setProfile(ResolvableProfile.resolvableProfile(profile));
            m.setDescription(null);
            m.customName(null);
            m.setImmovable(true);
            m.setGravity(false);
            m.setSilent(true);
            m.setInvulnerable(true);
            m.setPersistent(false);
            m.setVisibleByDefault(false);
            if (style == Style.CROUCH) {
                m.setPose(Pose.SNEAKING, true);
            }
            Bedrock.markStand(m);
        });
    }

    private Entity villager(TeamLookup.Member member, Location at) {
        return world.spawn(at, Villager.class, v -> {
            v.setAI(false);
            v.setSilent(true);
            v.setGravity(false);
            v.setInvulnerable(true);
            v.setPersistent(false);
            v.setVisibleByDefault(false);
            v.customName(Component.text(member.name()));
            v.setCustomNameVisible(true);
            Bedrock.markStand(v);
        });
    }

    void remove() {
        groups.values().forEach(Group::remove);
        groups.clear();
    }
}
