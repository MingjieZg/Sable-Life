package dev.sablelife.testing;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Discovery must not load optional API types before the namespace/dependency check. */
@GameTestHolder("sablelife_epicfight_tests") @PrefixGameTestTemplate(false)
public final class EpicFightGameTests {
    @GameTest(template="combat",batch="epic_charge_rules")
    public static void NativeChargingRulesMatchRealPostHitAndRespectGuards(GameTestHelper h){EpicFightTestScenarios.NativeChargingRulesMatchRealPostHitAndRespectGuards(h);}
    @GameTest(template="combat",batch="epic_native_sword")
    public static void NativeSwordAnimationHitsBodyOnlyOnceAndNextPhaseCanHitAgain(GameTestHelper h)throws Exception {EpicFightTestScenarios.NativeSwordAnimationHitsBodyOnlyOnceAndNextPhaseCanHitAgain(h);}
    @GameTest(template="combat",batch="epic_native_axe")
    public static void NativeAxeAnimationHitsBodyWithPhaseLimits(GameTestHelper h)throws Exception {EpicFightTestScenarios.NativeAxeAnimationHitsBodyWithPhaseLimits(h);}
    @GameTest(template="combat",batch="epic_native_trident")
    public static void NativeTridentAnimationHitsBodyWithPhaseLimits(GameTestHelper h)throws Exception {EpicFightTestScenarios.NativeTridentAnimationHitsBodyWithPhaseLimits(h);}
    @GameTest(template="combat",batch="epic_shared_strike_limit")
    public static void LivingTargetsAndLifeBodiesShareTheNativeStrikeLimit(GameTestHelper h)throws Exception {EpicFightTestScenarios.LivingTargetsAndLifeBodiesShareTheNativeStrikeLimit(h);}
}
