package dev.linqfy.bigCasares.modules.airdrop;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

public final class YamlAirdropStorage implements AirdropStorage {

    private final Path dataFolder;
    private final Logger logger;

    public YamlAirdropStorage(Path dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    private Path file() {
        return dataFolder.resolve("airdrop.yml");
    }

    @Override
    public void save(AirdropData data) {
        Path file = file();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("position.x", data.position().x());
        yaml.set("position.y", data.position().y());
        yaml.set("position.z", data.position().z());
        yaml.set("type", data.type().name());
        yaml.set("phase", data.phase().name());
        try {
            Files.createDirectories(dataFolder);
            yaml.save(file.toFile());
        } catch (IOException e) {
            logger.severe("[AirdropStorage] Failed to save airdrop data: " + e.getMessage());
        }
    }

    @Override
    public Optional<AirdropData> load() {
        Path file = file();
        if (Files.notExists(file)) return Optional.empty();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        int x = yaml.getInt("position.x");
        int y = yaml.getInt("position.y");
        int z = yaml.getInt("position.z");
        String typeName = yaml.getString("type");
        if (typeName == null) return Optional.empty();
        try {
            AirdropType type = AirdropType.valueOf(typeName);
            AirdropPhase phase = readPhase(yaml);
            return Optional.of(new AirdropData(new AirdropPosition(x, y, z), type, phase));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public void clear() {
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            logger.severe("[AirdropStorage] Failed to clear airdrop data: " + e.getMessage());
        }
    }

    private AirdropPhase readPhase(YamlConfiguration yaml) {
        String phaseName = yaml.getString("phase");
        if (phaseName != null) {
            return AirdropPhase.valueOf(phaseName);
        }
        return yaml.getBoolean("active") ? AirdropPhase.LANDED : AirdropPhase.CLAIMED;
    }
}
