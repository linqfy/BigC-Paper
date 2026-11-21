package dev.linqfy.bigCasares.modules.forex;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.teams.InMemoryTeamStorage;
import dev.linqfy.bigCasares.modules.teams.TeamColor;
import dev.linqfy.bigCasares.modules.teams.TeamService;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForexModuleWiringTest {

    @Test
    void closedModuleAnswersNeutralDefaults() {
        ForexModule module = new ForexModule(null, ForexTeams.NONE);
        assertEquals("forex", module.getId());
        assertDoesNotThrow(() -> module.onEnable(new RuntimeRegistrationScope()));
        assertFalse(module.isOpen());
        assertFalse(module.hasClanPass(UUID.randomUUID(), "team"));
        assertEquals(1.0, module.creditMultiplier(UUID.randomUUID()));
        assertDoesNotThrow(module::onDisable);
    }

    @Test
    void moduleIsRegisteredWiredDeclaredAndEnabledByDefault() throws Exception {
        CompiledClass main = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.BigCasares");
        assertTrue(main.calls("dev/linqfy/bigCasares/modules/forex/ForexModule", "<init>"));
        assertTrue(main.calls("dev/linqfy/bigCasares/modules/casino/CasinoModule", "setCreditRating"),
            "Don Casares reads the forex credit history");
        assertTrue(main.calls("dev/linqfy/bigCasares/modules/nexus/NexusModule", "setContainerPass"),
            "clan coins open the clan's protected chests");
        assertEquals(ForexModule.class, BigCasares.class.getMethod("getForexModule").getReturnType());

        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        plugin.load(new File("src/main/resources/plugin.yml"));
        assertTrue(plugin.isConfigurationSection("commands/forex"));
        assertEquals("true", plugin.getString("permissions/bigcasares.forex/default"));
        assertEquals("op", plugin.getString("permissions/" + ForexCommand.ADMIN_PERMISSION + "/default"));
        assertTrue(plugin.getBoolean("permissions/bigcasares.*/children/" + ForexCommand.ADMIN_PERMISSION));
        assertTrue(plugin.getStringList("softdepend").contains("Vault"));

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertTrue(config.getBoolean("modules.forex.enabled"));
    }

    @Test
    void onlyTheOwnerOfAClanCanMakeItsCoinOfficial() {
        TeamService teams = new TeamService(new InMemoryTeamStorage());
        UUID owner = UUID.randomUUID();
        var team = teams.createTeam("Los Casares", "CAS", TeamColor.values()[0], owner, Instant.EPOCH);
        TeamServiceForexTeams adapter = new TeamServiceForexTeams(() -> Optional.of(teams));

        assertEquals(Optional.of(team.id().toString()), adapter.ownedTeam(owner));
        assertTrue(adapter.ownedTeam(UUID.randomUUID()).isEmpty());
        assertTrue(adapter.label(team.id().toString()).orElseThrow().contains("[CAS]"));
        assertTrue(adapter.label("not-a-uuid").isEmpty());
        assertTrue(new TeamServiceForexTeams(Optional::empty).ownedTeam(owner).isEmpty(), "team system disabled");
    }
}
