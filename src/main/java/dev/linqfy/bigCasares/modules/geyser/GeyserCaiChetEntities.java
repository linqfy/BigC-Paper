package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.modules.caichet.CaiChetBedrockEntities;

import java.util.List;

/** Geyser custom entities of cai chet: the 3D figures that stand in for its displays on Bedrock. */
final class GeyserCaiChetEntities {

    private GeyserCaiChetEntities() {
    }

    static List<GeyserCustomEntityDefinition> definitions(List<CaiChetBedrockEntities.Entry> entries) {
        return entries.stream().map(GeyserCaiChetEntities::definition).toList();
    }

    private static GeyserCustomEntityDefinition definition(CaiChetBedrockEntities.Entry entry) {
        return new GeyserCustomEntityDefinition(entry.javaEntityType(), entry.bedrockIdentifier(), entry.markerKey());
    }
}
