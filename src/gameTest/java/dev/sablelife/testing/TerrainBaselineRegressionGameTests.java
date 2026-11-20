package dev.sablelife.testing;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("sablelife_terrain_baseline_tests") @PrefixGameTestTemplate(false)
public final class TerrainBaselineRegressionGameTests {
 @GameTest(template="combat",batch="baseline_command") public static void ExistingCommandContract(GameTestHelper h){CoreCommandGameTests.RemovedAuthoringCommandsLeaveAdministratorRecoveryAvailable(h);}
 @GameTest(template="combat",batch="baseline_signal",timeoutTicks=25) public static void ExistingSignalSnapshotTiming(GameTestHelper h){SignalModuleGameTests.LogicChainUsesOneSnapshotAndDoesNotDependOnOrder(h);}
}
