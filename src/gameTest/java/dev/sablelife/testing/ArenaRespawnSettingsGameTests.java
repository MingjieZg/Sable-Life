package dev.sablelife.testing;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sablelife_respawn_tests")
@PrefixGameTestTemplate(false)
public final class ArenaRespawnSettingsGameTests {
    @GameTest(template="combat", batch="record_before_enable", timeoutTicks=360)
    public static void RecordingBeforeEnablingRestoresOffAndStopsNextCountdown(GameTestHelper h) {
        ArenaRespawnScenarios.enableAfterRecord(h);
    }

    @GameTest(template="combat", batch="record_after_enable", timeoutTicks=360)
    public static void RecordingAfterEnablingKeepsTwoRespawnGenerations(GameTestHelper h) {
        ArenaRespawnScenarios.repeats(h);
    }
}
