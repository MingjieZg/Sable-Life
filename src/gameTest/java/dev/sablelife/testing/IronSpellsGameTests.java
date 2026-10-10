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
}
