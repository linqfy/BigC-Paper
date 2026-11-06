package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La cola de adjudicaciones: lo que un jugador ya gano pero todavia no esta confirmado como entregado (victorias contra
 * Neguechen y premios: Botas de Neguechen y Cruz Andina). Cada adjudicacion tiene su id y se guarda en un archivo
 * <b>antes</b> de tocar al jugador; se borra de la cola recien cuando el jugador ya tiene todo guardado y con su recibo
 * (ver MutangoAwardClaim). Asi un corte en cualquier paso no pierde ni duplica un premio: si se cae antes del recibo se
 * reintenta, y si se cae despues el recibo evita darlo de nuevo.
 */
final class MutangoPending {

    static final int VERSION = 2;

    /**
     * Una adjudicacion: cuantas victorias y cuantos juegos de premios le tocan a ese jugador. applied: ya se le aplico y se
     * guardo al jugador, pero todavia no hay prueba de que el archivo del jugador la tenga (ver MutangoAwardClaim): se
     * confirma al volver a entrar con el recibo en los datos que leyo del disco.
     */
    record Award(UUID id, UUID player, int wins, int rewards, boolean applied) {
    }

    /**
     * La identidad estable de la resolucion de una pelea para un jugador: la misma pelea y el mismo jugador dan siempre el
     * mismo id, asi resolver dos veces la misma pelea (un boss recuperado con su estado anterior) no genera otra adjudicacion
     * que los recibos viejos no reconozcan.
     */
    static UUID idFor(UUID fight, UUID player) {
        return UUID.nameUUIDFromBytes(("mutango-victoria:" + fight + ":" + player).getBytes(StandardCharsets.UTF_8));
    }

    private final File file;
    private final Map<UUID, Award> awards = new LinkedHashMap<>();

    MutangoPending(File file) {
        this.file = file;
        load();
    }

    /**
     * Anota una adjudicacion (victoria y premios juntos, en UNA sola operacion) y la guarda en el archivo antes de
     * devolverla. Es idempotente por id: si ya estaba anotada se devuelve la que habia. Si no se puede guardar queda en la
     * cola de esta corrida igual (se va a entregar) y se avisa con IllegalStateException.
     */
    Award add(UUID id, UUID player, int wins, int rewards) {
        Award existing = awards.get(id);
        if (existing != null) {
            return existing;
        }
        Award award = new Award(id, player, Math.max(0, wins), Math.max(0, rewards), false);
        awards.put(award.id(), award);
        save();
        return award;
    }

    /** Lo que ese jugador tiene sin confirmar, en el orden en que se anoto. */
    List<Award> of(UUID player) {
        List<Award> list = new ArrayList<>();
        for (Award award : awards.values()) {
            if (award.player().equals(player)) {
                list.add(award);
            }
        }
        return list;
    }

    /** Lo que ese jugador tiene por aplicar (sin contar lo que ya se aplico y espera confirmacion). */
    List<Award> pendingOf(UUID player) {
        return of(player).stream().filter(award -> !award.applied()).toList();
    }

    /** Lo que ya se le aplico y espera la prueba de que su archivo lo tiene. */
    List<Award> appliedOf(UUID player) {
        return of(player).stream().filter(Award::applied).toList();
    }

    /** Tiene algo por aplicar. */
    boolean has(UUID player) {
        return awards.values().stream().anyMatch(award -> award.player().equals(player) && !award.applied());
    }

    int size() {
        return awards.size();
    }

    /** Se le aplico al jugador y se lo guardo: queda en la cola, aplicada, hasta que se confirme con la prueba del disco. */
    void markApplied(UUID awardId) {
        setApplied(awardId, true);
    }

    /** No esta en el archivo del jugador (el guardado fallo): vuelve a estar por aplicar. */
    void markPending(UUID awardId) {
        setApplied(awardId, false);
    }

    private void setApplied(UUID awardId, boolean applied) {
        Award award = awards.get(awardId);
        if (award != null && award.applied() != applied) {
            awards.put(awardId, new Award(award.id(), award.player(), award.wins(), award.rewards(), applied));
            save();
        }
    }

    /** Ya hay prueba de que el jugador lo tiene guardado: sale de la cola (y del archivo). */
    void remove(UUID awardId) {
        if (awards.remove(awardId) != null) {
            save();
        }
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> raw : yaml.getMapList("adjudicaciones")) {
            try {
                UUID id = UUID.fromString(String.valueOf(raw.get("id")));
                UUID player = UUID.fromString(String.valueOf(raw.get("jugador")));
                int wins = Math.max(0, number(raw.get("victorias")));
                int rewards = Math.max(0, number(raw.get("premios")));
                boolean applied = Boolean.parseBoolean(String.valueOf(raw.get("aplicada")));
                if (wins > 0 || rewards > 0) {
                    awards.put(id, new Award(id, player, wins, rewards, applied));
                }
            } catch (IllegalArgumentException invalid) {
                // un registro roto no puede impedir cargar los demas
            }
        }
        loadLegacy(yaml);
    }

    /**
     * El formato anterior (sin version) guardaba solo cuentas por jugador. Cada una pasa a ser una adjudicacion con un id
     * que sale del jugador (siempre el mismo: si se vuelve a leer antes de guardarse, no se duplica).
     */
    private void loadLegacy(YamlConfiguration yaml) {
        ConfigurationSection section = yaml.getConfigurationSection("pendientes");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID player = UUID.fromString(key);
                int wins = Math.max(0, section.getInt(key + ".victorias"));
                int rewards = Math.max(0, section.getInt(key + ".premios"));
                UUID id = UUID.nameUUIDFromBytes(("mutango-pendiente-viejo:" + player).getBytes(StandardCharsets.UTF_8));
                if (wins + rewards > 0 && !awards.containsKey(id)) {
                    awards.put(id, new Award(id, player, wins, rewards, false));
                }
            } catch (IllegalArgumentException invalid) {
                // un registro roto no puede impedir cargar los demas
            }
        }
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    /** Escribe a un archivo temporal y lo mueve (asi un corte a mitad de guardar no deja el archivo a medias). */
    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", VERSION);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Award award : awards.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", award.id().toString());
            entry.put("jugador", award.player().toString());
            entry.put("victorias", award.wins());
            entry.put("premios", award.rewards());
            entry.put("aplicada", award.applied());
            list.add(entry);
        }
        yaml.set("adjudicaciones", list);
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            File temp = new File(file.getPath() + ".tmp");
            yaml.save(temp);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failed) {
            throw new IllegalStateException("No se pudo guardar " + file + ": " + failed.getMessage(), failed);
        }
    }
}
