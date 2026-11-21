package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.modules.caichet.CaiChetBedrockEntities;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeyserCaiChetEntitiesTest {

    @Test
    void everyCaiChetEntityBecomesAGeyserDefinitionOnItsJavaBody() {
        List<GeyserCustomEntityDefinition> definitions = GeyserCaiChetEntities.definitions(CaiChetBedrockEntities.all());
        assertEquals(CaiChetBedrockEntities.all().size(), definitions.size());
        for (int i = 0; i < definitions.size(); i++) {
            CaiChetBedrockEntities.Entry entry = CaiChetBedrockEntities.all().get(i);
            GeyserCustomEntityDefinition definition = definitions.get(i);
            assertEquals(entry.javaEntityType(), definition.javaEntityType());
            assertEquals(entry.bedrockIdentifier(), definition.bedrockIdentifier());
            assertEquals(entry.markerKey(), definition.markerKey());
            assertTrue(definition.matches(entry.javaEntityType(), Set.of(entry.markerKey())));
        }
    }

    @Test
    void aBodyMatchesExactlyOneDefinitionAcrossEveryRegisteredEntity() {
        List<GeyserCustomEntityDefinition> all = new ArrayList<>(GeyserSiftContent.entities());
        all.add(new GeyserCustomEntityDefinition("GHAST", "bigcasares:nexus", "nexus_id"));
        all.addAll(GeyserCaiChetEntities.definitions(CaiChetBedrockEntities.all()));
        for (GeyserCustomEntityDefinition definition : GeyserCaiChetEntities.definitions(CaiChetBedrockEntities.all())) {
            long matches = all.stream()
                .filter(other -> other.matches(definition.javaEntityType(), Set.of(definition.markerKey()))).count();
            assertEquals(1, matches, definition.bedrockIdentifier());
        }
        Set<String> identifiers = new HashSet<>();
        for (GeyserCustomEntityDefinition definition : GeyserCaiChetEntities.definitions(CaiChetBedrockEntities.all())) {
            assertTrue(identifiers.add(definition.bedrockIdentifier()), definition.bedrockIdentifier());
        }
        assertTrue(GeyserSiftContent.entities().stream().noneMatch(sift -> identifiers.contains(sift.bedrockIdentifier())));
    }

    @Test
    void geyserModuleRegistersTheCaiChetEntitiesAndTurnsThemOnOnlyWithThem() throws IOException {
        CompiledClass module = CompiledClass.of(getClass(), GeyserIntegrationModule.class.getName());
        assertTrue(module.calls("dev/linqfy/bigCasares/modules/geyser/GeyserCaiChetEntities", "definitions"));
        assertTrue(module.calls("dev/linqfy/bigCasares/modules/caichet/CaiChetBedrockEntities", "all"));
        assertTrue(module.calls("dev/linqfy/bigCasares/modules/caichet/CaiChetBedrockEntities", "setAvailable"));
    }
}
