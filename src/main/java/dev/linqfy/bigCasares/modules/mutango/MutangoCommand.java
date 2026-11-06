package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** El comando /mutango (solo OP): spawnear peleas, pasar de fase, sacar todo, el santuario, las ruinas y los premios. */
final class MutangoCommand implements CommandExecutor, TabCompleter {

    private final MutangoFights fights;
    private final MutangoShrine shrine;
    private final MutangoRelics relics;
    /** Las ruinas pueden estar desactivadas (null). */
    private final Supplier<MutangoRuins> ruins;

    MutangoCommand(MutangoFights fights, MutangoShrine shrine, MutangoRelics relics, Supplier<MutangoRuins> ruins) {
        this.fights = fights;
        this.shrine = shrine;
        this.relics = relics;
        this.ruins = ruins;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "spawn" : args[0].toLowerCase();
        switch (action) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cSolo un jugador puede spawnear Mutangos.");
                    return true;
                }
                // una pelea nueva e independiente (las que ya hay siguen como estaban)
                MutangoFight fight = fights.beginFight(player);
                spawnAll(fight, player);
                fight.battle.setPhase(1, fights.aliveOf(fight));
                sender.sendMessage("§aAparecieron §cLef§a, §eLihuen §ay §dLican§a. §cFase 1: ¡cuidado que embisten!");
            }
            case "quitar" -> {
                int removed = fights.removeAll();
                sender.sendMessage("§eMutangos quitados: " + removed);
            }
            case "fase" -> {
                if (args.length >= 2 && args[1].equals("3")) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§cSolo un jugador puede empezar la fase 3.");
                        return true;
                    }
                    // sin Mutangos: aparece Neguechen a 12 bloques delante tuyo, en una pelea nueva
                    MutangoFight fight = fights.beginFight(player);
                    fight.battle.setPhase(2, List.of());
                    Vector forward = player.getLocation().getDirection().setY(0);
                    if (forward.lengthSquared() < 1e-4) {
                        forward = new Vector(0, 0, 1);
                    }
                    fights.startPhaseThree(fight, player.getLocation().add(forward.normalize().multiply(12)));
                    return true;
                }
                if (args.length < 2 || !(args[1].equals("1") || args[1].equals("2"))) {
                    sender.sendMessage("§7Uso: /mutango fase <1|2|3>  (cambia la pelea mas cercana a vos)");
                    return true;
                }
                List<MutangoFight> targets = sender instanceof Player player
                    ? Optional.ofNullable(fights.nearestFight(player)).stream().toList()
                    : List.copyOf(fights.all());
                if (targets.isEmpty()) {
                    sender.sendMessage("§cNo hay ninguna pelea de Mutangos cerca.");
                    return true;
                }
                for (MutangoFight fight : targets) {
                    fight.phaseThree.stop();
                    fight.battle.setPhase(Integer.parseInt(args[1]), fights.aliveOf(fight));
                }
                sender.sendMessage("§eMutangos en la fase " + args[1] + ".");
            }
            case "atacar" -> {
                sender.sendMessage("§eMutangos atacando: " + fights.playAll("attack"));
            }
            case "santuario" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cSolo un jugador puede usar /mutango santuario.");
                    return true;
                }
                placeShrineAhead(player);
                sender.sendMessage("§eSantuario armado delante tuyo: hace click en el cubo.");
            }
            case "ruinas" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cSolo un jugador puede usar /mutango ruinas.");
                    return true;
                }
                MutangoRuins ruinsNow = ruins.get();
                if (ruinsNow == null) {
                    sender.sendMessage("§cLas ruinas estan desactivadas (mira la consola).");
                    return true;
                }
                String sub = args.length >= 2 ? args[1].toLowerCase() : "cerca";
                if (sub.equals("aqui")) {
                    ruinsNow.buildHere(player);
                } else {
                    player.sendMessage(ruinsNow.nearest(player));
                }
            }
            case "premios" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cSolo un jugador puede recibir los premios.");
                    return true;
                }
                relics.give(player);
                sender.sendMessage("§6Te di las Botas de Neguechen y la Cruz Andina.");
            }
            default -> sender.sendMessage(
                "§7Uso: /mutango [quitar|atacar|fase <1|2|3>|ruinas <aqui|cerca>|santuario|premios]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("fase")) {
            return List.of("1", "2", "3");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ruinas")) {
            return List.of("aqui", "cerca");
        }
        if (args.length != 1) {
            return List.of();
        }
        return List.of("quitar", "atacar", "fase", "ruinas", "santuario", "premios").stream()
            .filter(option -> option.startsWith(args[0].toLowerCase())).toList();
    }

    /** Los tres en fila, a 4 bloques delante del jugador y mirandolo. */
    private void spawnAll(MutangoFight fight, Player player) {
        Location eye = player.getLocation();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1e-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location center = eye.clone().add(forward.clone().multiply(4));
        List<Location> spots = new ArrayList<>();
        for (int i = 0; i < MutangoModels.MODELS.size(); i++) {
            Location spot = center.clone().add(right.clone().multiply((i - 1) * 2.5));
            spot.setY(MutangoGround.y(spot) + 1);
            spot.setDirection(forward.clone().multiply(-1));
            spots.add(spot);
        }
        fights.spawnTrio(fight, spots);
    }

    /** /mutango santuario: el agujero a 3 bloques delante tuyo y las estatuas detras, mirandote. */
    private void placeShrineAhead(Player player) {
        float yaw = player.getLocation().getYaw();
        int quarter = Math.floorMod(Math.round(yaw / 90f), 4);
        // hacia donde mira el jugador, redondeado a N/S/E/O (yaw 0 = +Z)
        Vector forward = switch (quarter) {
            case 1 -> new Vector(-1, 0, 0);
            case 2 -> new Vector(0, 0, -1);
            case 3 -> new Vector(1, 0, 0);
            default -> new Vector(0, 0, 1);
        };
        Location at = player.getLocation().add(forward.clone().multiply(3));
        Block hole = MutangoGround.block(at);
        // la izquierda de quien mira hacia forward (mirando a +Z, la izquierda es +X)
        Vector left = new Vector(forward.getZ(), 0, -forward.getX());
        shrine.place(hole, left, forward.clone().multiply(-1));
    }
}
