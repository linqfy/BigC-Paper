package dev.linqfy.bigCasares.modules.model;

import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterModelAssetInstallerContractTest {

    // BetterModel classes cannot be loaded in unit tests, so the wiring is checked from the compiled bytecode.

    @Test
    void installerAsksBetterModelToReloadTheCopiedModels() throws Exception {
        CompiledClass installer = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.modules.model.BetterModelAssetInstaller");

        assertTrue(installer.calls("kr/toxicity/model/api/BetterModelPlatform", "reload"));
    }

    @Test
    void pveModuleRepeatsInstallAfterBetterModelDelayedInitialization() throws Exception {
        CompiledClass module = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.modules.pveboss.PveBossModule");

        assertTrue(module.calls("dev/linqfy/bigCasares/modules/model/BetterModelAssetInstaller", "installSahurModels"));
        assertTrue(module.calls("org/bukkit/scheduler/BukkitScheduler", "runTaskLater"));
    }
}
