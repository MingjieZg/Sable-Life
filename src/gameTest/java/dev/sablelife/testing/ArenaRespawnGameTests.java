package dev.sablelife.testing;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("sablelife_respawn_tests") @PrefixGameTestTemplate(false)
public final class ArenaRespawnGameTests {
 @GameTest(template="combat",batch="respawn_cycle",timeoutTicks=240) public static void RecordedBodyRespawnsActiveWithMarksAndInventory(GameTestHelper h){ArenaRespawnScenarios.cycle(h,false);}
 @GameTest(template="combat",batch="respawn_resume",timeoutTicks=240) public static void MidPlacementSaveReloadDoesNotDuplicateBody(GameTestHelper h){ArenaRespawnScenarios.cycle(h,true);}
 @GameTest(template="combat",batch="respawn_permissions") public static void RecordRequiresCreativeOperatorAndRejectsWrongModule(GameTestHelper h){ArenaRespawnScenarios.permissions(h);}
 @GameTest(template="combat",batch="respawn_unloaded",timeoutTicks=100) public static void CountdownPausesWithoutLoadingDistantChunks(GameTestHelper h){ArenaRespawnScenarios.unloaded(h);}
 @GameTest(template="combat",batch="respawn_repeat",timeoutTicks=240) public static void TwoGenerationsKeepAutoRespawnWithoutDuplicatingLife(GameTestHelper h){ArenaRespawnScenarios.repeats(h);}
 @GameTest(template="combat",batch="respawn_cancel",timeoutTicks=240) public static void OperatorCanCancelPartialPlacementSafely(GameTestHelper h){ArenaRespawnScenarios.cancelPartial(h);}
 @GameTest(template="combat",batch="respawn_sparse",timeoutTicks=200) public static void SparseStructureAndLargeBlockDataRecordAndReload(GameTestHelper h){ArenaRespawnScenarios.sparseCapture(h);}
 @GameTest(template="combat",batch="respawn_saved_budget") public static void SavedTemplatesKeepPerRecordAndWorldByteLimits(GameTestHelper h){ArenaRespawnScenarios.savedBudget(h);}

}
