package dev.sablelife.testing;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
/** No optional API types in annotated discovery signatures or fields. */
@GameTestHolder("sablelife_irons_tests") @PrefixGameTestTemplate(false)
public final class IronSpellsGameTests {
 @GameTest(template="combat",batch="iron_missile") public static void NativeMissileHitsOwnedBodyAndExpires(GameTestHelper h){IronSpellsTestScenarios.direct(h,0);}
 @GameTest(template="combat",batch="iron_arrow") public static void NativeArrowBodyContactConsumesProjectileOnce(GameTestHelper h){IronSpellsTestScenarios.direct(h,1);}
 @GameTest(template="combat",batch="iron_small_fireball") public static void NativeSmallFireballHitsBody(GameTestHelper h){IronSpellsTestScenarios.direct(h,2);}
 @GameTest(template="combat",batch="iron_addon_inherit") public static void UnchangedMissileSubclassInheritsTheBodyBridge(GameTestHelper h){IronSpellsTestScenarios.direct(h,3);}
 @GameTest(template="combat",batch="iron_blast_off") public static void NativeFireballKeepsSpellFalloffWithTerrainDestructionOff(GameTestHelper h){IronSpellsTestScenarios.blast(h,false,false);}
 @GameTest(template="combat",batch="iron_blast_on") public static void NativeFireballTerrainExplosionDoesNotAddTntDamage(GameTestHelper h){IronSpellsTestScenarios.blast(h,true,false);}
 @GameTest(template="combat",batch="iron_fire_arrow") public static void NativeFireArrowDirectHitDoesNotAddSplashDamage(GameTestHelper h){IronSpellsTestScenarios.blast(h,true,true);}
 @GameTest(template="combat",batch="iron_arrow_immune") public static void ImmuneArrowBodyContactConsumesProjectile(GameTestHelper h){IronSpellsTestScenarios.immune(h);}
 @GameTest(template="combat",batch="iron_optin") public static void NewDirectAddonOptsInWithoutTreatingUnknownProjectilesAsDamage(GameTestHelper h){IronSpellsTestScenarios.optin(h);}
 @GameTest(template="combat",batch="iron_front_entity") public static void EarlierNativeLivingTargetConsumesTheMissileBeforeBody(GameTestHelper h){IronSpellsTestScenarios.front(h,false);}
 @GameTest(template="combat",batch="iron_front_wall") public static void EarlierTerrainStopsMissileBeforeBody(GameTestHelper h){IronSpellsTestScenarios.front(h,true);}
 @GameTest(template="combat",batch="iron_shockwave") public static void NativeShockwaveOnCastHitsRealBodyWithoutProxyEntities(GameTestHelper h){IronSpellsTestScenarios.shockwave(h);}
 @GameTest(template="combat",batch="iron_ray_body") public static void NativeSiphoningRayKeepsBodyHitInWorldSpace(GameTestHelper h){IronSpellsTestScenarios.ray(h,0);}
 @GameTest(template="combat",batch="iron_ray_entity") public static void NativeRayStillSelectsCloserLivingTarget(GameTestHelper h){IronSpellsTestScenarios.ray(h,1);}
 @GameTest(template="combat",batch="iron_ray_wall") public static void NativeRayStillStopsAtEarlierTerrain(GameTestHelper h){IronSpellsTestScenarios.ray(h,2);}
 @GameTest(template="combat",batch="iron_firebolt") public static void FireboltContactsAndExpires(GameTestHelper h){IronSpellsTestScenarios.newDirect(h,0);}
 @GameTest(template="combat",batch="iron_icicle") public static void PiercingIcicleContactsAndExpires(GameTestHelper h){IronSpellsTestScenarios.newDirect(h,1);}
 @GameTest(template="combat",batch="iron_lance") public static void LightningLanceContactsAndExpires(GameTestHelper h){IronSpellsTestScenarios.newDirect(h,2);}
 @GameTest(template="combat",batch="iron_blood") public static void BloodSlashBoxEdgeHitsOnceThenExpires(GameTestHelper h){IronSpellsTestScenarios.blood(h,false);}
 @GameTest(template="combat",batch="iron_blood_hole") public static void BloodSlashDoesNotHitEmptyBodyBounds(GameTestHelper h){IronSpellsTestScenarios.blood(h,true);}
 @GameTest(template="combat",batch="iron_breath") public static void FireBreathUsesNativeDamageWindows(GameTestHelper h){IronSpellsTestScenarios.cone(h,0,false);}
 @GameTest(template="combat",batch="iron_cold") public static void ColdConeUsesNativeDamageWindows(GameTestHelper h){IronSpellsTestScenarios.cone(h,1,false);}
 @GameTest(template="combat",batch="iron_poison") public static void PoisonBreathUsesNativeDamageWindows(GameTestHelper h){IronSpellsTestScenarios.cone(h,2,false);}
 @GameTest(template="combat",batch="iron_breath_wall") public static void BreathDoesNotDamageThroughTerrain(GameTestHelper h){IronSpellsTestScenarios.cone(h,0,true);}
 @GameTest(template="combat",batch="iron_siphon_damage") public static void SiphoningHealsActualDamageAcrossNativeWindows(GameTestHelper h){IronSpellsTestScenarios.siphon(h);}
 @GameTest(template="combat",batch="iron_siphon_immune") public static void ImmuneBodyDoesNotSupplySiphoningHealing(GameTestHelper h){IronSpellsTestScenarios.immuneSiphon(h);}
 @GameTest(template="combat",batch="iron_shadow") public static void ShadowSlashKeepsQueriesAndImpulseInWorldSpace(GameTestHelper h){IronSpellsTestScenarios.shadow(h,false);}
 @GameTest(template="combat",batch="iron_shadow_wall") public static void ShadowSlashDoesNotDamageBodyThroughWall(GameTestHelper h){IronSpellsTestScenarios.shadow(h,true);}
 @GameTest(template="combat",batch="iron_firefield") public static void FireFieldKeepsNativePulseAndPostHitRules(GameTestHelper h){IronSpellsTestScenarios.area(h,0);}
 @GameTest(template="combat",batch="iron_poisoncloud") public static void PoisonCloudKeepsNativePulseAndPostHitRules(GameTestHelper h){IronSpellsTestScenarios.area(h,1);}
 @GameTest(template="combat",batch="iron_earthquake") public static void EarthquakeKeepsNativePulseAndPostHitRules(GameTestHelper h){IronSpellsTestScenarios.area(h,2);}
 @GameTest(template="combat",batch="iron_healing") public static void HealingAreaNeverBecomesBodyDamage(GameTestHelper h){IronSpellsTestScenarios.area(h,3);}
 @GameTest(template="combat",batch="iron_frostfield") public static void FreezeOnlyFieldNeverBecomesBodyDamage(GameTestHelper h){IronSpellsTestScenarios.area(h,4);}
 @GameTest(template="combat",batch="iron_wither") public static void WitherSkullContactsBodyWithOneFullDirectHit(GameTestHelper h){IronSpellsTestScenarios.namedProjectile(h,0);}
 @GameTest(template="combat",batch="iron_comet") public static void StarfallCometHitsRealBody(GameTestHelper h){IronSpellsTestScenarios.namedProjectile(h,1);}
 @GameTest(template="combat",batch="iron_volley_arrow") public static void ArrowVolleySmallArrowHitsAndExpires(GameTestHelper h){IronSpellsTestScenarios.namedProjectile(h,2);}
 @GameTest(template="combat",batch="iron_sunbeam") public static void SunbeamKeepsNativeWarmupAndOneBurst(GameTestHelper h){IronSpellsTestScenarios.sunbeam(h);}
 @GameTest(template="combat",batch="iron_world_helpers") public static void LegacyRayAndGroundHelpersKeepWorldPositions(GameTestHelper h){IronSpellsTestScenarios.worldHelpers(h);}
 @GameTest(template="combat",batch="iron_starfall_cast") public static void StarfallNativeCastCreatesCometsNearTheWorldTarget(GameTestHelper h){IronSpellsTestScenarios.namedCast(h,0);}
 @GameTest(template="combat",batch="iron_volley_cast") public static void VolleyNativeCastCreatesWorldSpaceControllerAndArrows(GameTestHelper h){IronSpellsTestScenarios.namedCast(h,1);}
 @GameTest(template="combat",batch="iron_sunbeam_cast") public static void SunbeamNativeCastDamagesTheSelectedBody(GameTestHelper h){IronSpellsTestScenarios.namedCast(h,2);}
 @GameTest(template="combat",batch="iron_aoe_immune") public static void ImmuneAreaContactStillRunsNativePostHitOnce(GameTestHelper h){IronSpellsTestScenarios.areaRules(h,0);}
 @GameTest(template="combat",batch="iron_aoe_corner") public static void CircularAreaRejectsItsEmptyBoxCorner(GameTestHelper h){IronSpellsTestScenarios.areaRules(h,1);}
 @GameTest(template="combat",batch="iron_aoe_mixed") public static void MixedBodyAndLivingAreaHitRunsNativePostHitOnce(GameTestHelper h){IronSpellsTestScenarios.areaRules(h,2);}
 @GameTest(template="combat",batch="iron_mortar") public static void CreeperMortarBodyHitKeepsQuadraticBlast(GameTestHelper h){IronSpellsTestScenarios.remaining(h,0);}
 @GameTest(template="combat",batch="iron_smite") public static void DivineSmitePhysicalAreaHitsBody(GameTestHelper h){IronSpellsTestScenarios.remaining(h,1);}
 @GameTest(template="combat",batch="iron_fang") public static void FangPhysicalBiteKeepsNativeWarmup(GameTestHelper h){IronSpellsTestScenarios.remaining(h,2);}
 @GameTest(template="combat",batch="iron_ice_fall") public static void IceBlockFloatingIsSafeAndFallingContactConsumes(GameTestHelper h){IronSpellsTestScenarios.remaining(h,3);}
 @GameTest(template="combat",batch="iron_ice_burst") public static void IceBlockGroundImpactKeepsCubicFalloff(GameTestHelper h){IronSpellsTestScenarios.remaining(h,4);}
 @GameTest(template="combat",batch="iron_fang_cast") public static void NativeFangCastUsesWorldGroundAndPhysicalBodyContact(GameTestHelper h){IronSpellsTestScenarios.fangCast(h);}
 @GameTest(template="combat",batch="iron_ice_cast") public static void IceBlockNativeFallbackDoesNotRequireBodyEntityLockOn(GameTestHelper h){IronSpellsTestScenarios.iceCast(h);}
}
