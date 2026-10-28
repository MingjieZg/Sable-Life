package dev.sablelife.testing;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

/** Discovery must not load optional API types before the namespace/dependency check. */
@GameTestHolder("sablelife_epicfight_tests") @PrefixGameTestTemplate(false)
public final class EpicFightGameTests {
    @GameTest(template="combat",batch="epic_slash_guard_option")
    public static void SlashOptionSupportsNativeGuardsAndRearHits(GameTestHelper h){EpicFightTestScenarios.SlashOptionSupportsNativeGuardsAndRearHits(h);}
    @GameTest(template="combat",batch="epic_module_guard_interval")
    public static void ActualMeleeModuleBlocksWithoutEveryTickStaminaDrain(GameTestHelper h){EpicFightTestScenarios.ActualMeleeModuleBlocksWithoutEveryTickStaminaDrain(h);}
    @GameTest(template="combat",batch="epic_arrow_guard_rules")
    public static void NativeAndModuleArrowsFollowTheSameGuardAndParryingRules(GameTestHelper h){EpicFightTestScenarios.NativeAndModuleArrowsFollowTheSameGuardAndParryingRules(h);}
    @GameTest(template="combat",batch="epic_body_guard")
    public static void PositionBasedMeleeUsesNativeGuardParryAndRearRules(GameTestHelper h){EpicFightTestScenarios.PositionBasedMeleeUsesNativeGuardParryAndRearRules(h);}
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
