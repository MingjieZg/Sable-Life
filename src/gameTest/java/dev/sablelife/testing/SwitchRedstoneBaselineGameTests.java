package dev.sablelife.testing;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("sablelife_switch_baseline_tests") @PrefixGameTestTemplate(false)
public final class SwitchRedstoneBaselineGameTests {
 @GameTest(template="combat",timeoutTicks=245)
 public static void FiveSecondFaceSwitch(GameTestHelper h){MeleeProtectionAndSwitchGameTests.MoveSetRightThenLeftFiveSecondsUpdatesActualLamps(h);}
 @GameTest(template="combat",timeoutTicks=65)
 public static void ConsecutiveOneTickFaceSwitch(GameTestHelper h){MeleeProtectionAndSwitchGameTests.SixteenConsecutiveOneTickActionsPublishBothFacesWithoutStalePower(h);}
}
