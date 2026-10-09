package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;
@GameTestHolder("sablelife_defense_tests")
@PrefixGameTestTemplate(false)
public final class AdaptiveDefenseGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h,int x,int z){var l=h.getLevel();var p=h.absolutePos(new BlockPos(x,6,z));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());}
    private static net.neoforged.neoforge.common.util.FakePlayer author(GameTestHelper h){var p=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"DefenseAuthor"));p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);return p;}
    @GameTest(template="combat",batch="defense_armor")
    public static void ArmorRunsBeforeSharedDpsStatisticsAndBlockedDamageIsIgnored(GameTestHelper h){var heart=fixture(h,4,4);var p=author(h);heart.configure(1000,60);heart.editCore(p,"defenseEnabled",1,"");var armor=heart.modules().add("armor");armor.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos().east(),Direction.EAST)));armor.edit("reduction",50,"");heart.tryActivate(p);
        h.assertTrue(heart.damageAt(heart.getBlockPos(),100)&&heart.damageAt(heart.getBlockPos().east(),100)&&heart.damageAt(heart.getBlockPos(),100),"Valid damage rejected");h.assertTrue(Math.abs(heart.health().current()-(1000-50-50-100f/3))<.001,"Armor/adaptive mitigation order incorrect");h.assertTrue(heart.adaptiveDefense().dps()==150,"Different hit parts did not share post-armor DPS");
        armor.edit("reduction",100,"");h.assertTrue(!heart.damageAt(heart.getBlockPos(),100)&&!heart.damage(Float.NaN)&&!heart.damage(0)&&heart.adaptiveDefense().dps()==150,"Blocked/invalid hits polluted DPS");h.succeed();}
    @GameTest(template="combat",batch="defense_copy")
    public static void ConfigurationPersistsAndCopiesWithoutRuntimeHistory(GameTestHelper h){var heart=fixture(h,2,3);var target=fixture(h,6,7);var p=author(h);heart.configure(10000,60);heart.editCore(p,"defenseThreshold",250,"");heart.editCore(p,"defenseMaximum",75,"");heart.editCore(p,"defenseWindow",2,"");heart.editCore(p,"defenseEnabled",1,"");heart.tryActivate(p);heart.damage(2000);h.assertTrue(heart.adaptiveDefense().dps()==1000&&heart.adaptiveDefense().reduction()==.75,"DPS fixture failed");
        var saved=heart.saveWithoutMetadata(h.getLevel().registryAccess());var loaded=new HeartBlockEntity(heart.getBlockPos(),heart.getBlockState());loaded.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(loaded.adaptiveDefense().enabled()&&loaded.adaptiveDefense().threshold()==250&&loaded.adaptiveDefense().maximum()==75&&loaded.adaptiveDefense().window()==40&&loaded.adaptiveDefense().dps()==0&&loaded.adaptiveDefense().reduction()==0,"Configuration or runtime reset failed on load");
        h.assertTrue(target.pasteConfiguration(p,heart.copyConfiguration())&&target.adaptiveDefense().enabled()&&target.adaptiveDefense().threshold()==250&&target.adaptiveDefense().dps()==0,"Photomancy configuration copy lost defense or copied live DPS");
        saved.remove("AdaptiveDefense");var legacy=new HeartBlockEntity(heart.getBlockPos(),heart.getBlockState());legacy.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(!legacy.adaptiveDefense().enabled()&&legacy.adaptiveDefense().threshold()==100,"Old save enabled defense unexpectedly");h.succeed();}
    @GameTest(template="combat",batch="defense_permissions")
    public static void DefensePageValidatesLiveCreativePermissionsAndRanges(GameTestHelper h){var heart=fixture(h,4,4);var p=author(h);p.setPos(ModuleArena.corePosition(heart));var menu=new HeartSettingsMenu(1,p.getInventory(),heart,4);h.assertTrue(menu.snapshot().getInt("InitialPage")==4&&menu.action(p,"defenseEnabled",1,""),"Defense page failed to initialize");heart.tryActivate(p);h.assertTrue(menu.action(p,"defenseThreshold",200,""),"Live creative defense editing rejected");h.assertTrue(!menu.action(p,"defenseMaximum",100,"")&&!menu.action(p,"defenseThreshold",0,"")&&!menu.action(p,"defenseWindow",.09,"")&&!menu.action(p,"defenseWindow",Double.NaN,""),"Invalid defense configuration accepted");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);h.assertTrue(!menu.action(p,"defenseEnabled",0,"")&&heart.adaptiveDefense().enabled(),"Survival actor edited defense");h.succeed();}
    @GameTest(template="combat",batch="defense_lifecycle")
    public static void ArenaRestoreAndPhysicalCoreDestructionResetDefense(GameTestHelper h){var heart=fixture(h,4,4);var p=author(h);heart.configure(1000,60);heart.editCore(p,"defenseEnabled",1,"");heart.tryActivate(p);heart.damage(1000);h.assertTrue(heart.health().current()==800&&heart.adaptiveDefense().reduction()==.8,"Adaptive hit did not mitigate");heart.restoreFullHealth();h.assertTrue(heart.health().current()==1000&&heart.adaptiveDefense().dps()==0,"Arena-style reset retained defense history");heart.damage(1000);heart.resetForCreative(p);h.assertTrue(heart.health().state()==HeartState.DORMANT&&heart.adaptiveDefense().enabled()&&heart.adaptiveDefense().dps()==0,"Creative release lost settings or retained runtime");heart.tryActivate(p);heart.damage(1000);h.assertTrue(heart.forceDeath()&&heart.health().state()==HeartState.DYING&&heart.adaptiveDefense().dps()==0,"Core destruction was incorrectly mitigated");h.succeed();}
    @GameTest(template="combat",batch="defense_hurt")
    public static void ReducedDamageStillTriggersHurtAndHighHpNeverRoundsProtectionToImmunity(GameTestHelper h){var heart=fixture(h,4,4);var p=author(h);heart.configure(1000,60);heart.editCore(p,"defenseEnabled",1,"");var response=heart.modules().add("wireless_redstone");response.edit("trigger",5,"");heart.tryActivate(p);heart.damage(500);heart.modules().tick(heart);h.assertTrue(heart.health().current()==900&&HeartModules.triggered(response,heart),"Reduced hit lost hurt trigger");heart.resetForCreative(p);heart.configure(1000000,60);heart.editCore(p,"defenseThreshold",.1,"");heart.editCore(p,"defenseMaximum",95,"");heart.editCore(p,"defenseWindow",.1,"");heart.tryActivate(p);heart.damage(.1F);h.assertTrue(heart.health().current()<heart.health().maximum(),"Float precision made adaptive defense fully immune");h.succeed();}
    @GameTest(template="combat",batch="defense_modifier")
    public static void SignedPartModifierAmplifiesReducesAndPreservesOverlapPriority(GameTestHelper h){
        var heart=fixture(h,4,4);var p=author(h);heart.configure(1000,60);
        var modifier=heart.modules().add("armor");modifier.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.UP)));
        h.assertTrue(modifier.value("damageModifier")==0,"New damage modifier must default to ordinary damage");
        h.assertTrue(modifier.edit("damageModifier",1001,"")&&modifier.value("damageModifier")==1000&&modifier.edit("damageModifier",-101,"")&&modifier.value("damageModifier")==-100&&!modifier.edit("damageModifier",Double.NaN,""),"Signed range clamping failed");
        modifier.edit("damageModifier",50,"");
        heart.tryActivate(p);heart.damageAt(heart.getBlockPos().east(),20);
        h.assertTrue(heart.health().current()==970,"Positive modifier failed to amplify entire marked part");
        modifier.edit("damageModifier",-50,"");heart.damageAt(heart.getBlockPos(),20);
        h.assertTrue(heart.health().current()==960,"Negative modifier failed to reduce damage");
        modifier.edit("damageModifier",0,"");heart.damageAt(heart.getBlockPos(),20);
        h.assertTrue(heart.health().current()==940,"Zero modifier changed damage");
        modifier.edit("damageModifier",100,"");var shield=heart.modules().add("armor");shield.anchors(modifier.anchors());shield.edit("damageModifier",-100,"");
        h.assertTrue(!heart.damageAt(heart.getBlockPos(),20)&&heart.health().current()==940,"Amplification bypassed overlapping immunity");
        shield.edit("enabled",0,"");heart.damageAt(heart.getBlockPos(),20);
        h.assertTrue(heart.health().current()==900,"Disabled immunity blocked amplification");
        modifier.edit("enabled",0,"");heart.damageAt(heart.getBlockPos(),20);
        h.assertTrue(heart.health().current()==880,"Inactive modifier changed ordinary damage");
        modifier.edit("enabled",1,"");modifier.edit("damageModifier",1000,"");heart.damageAt(heart.getBlockPos(),20);
        h.assertTrue(heart.health().current()==660,"+1000% did not yield eleven times incoming damage");
        h.assertTrue(VirtualModule.load(modifier.save()).value("damageModifier")==1000&&ModulePreset.create(modifier.save()).value("damageModifier")==1000,"Extended amplification failed save/preset round trip");h.succeed();
    }
    @GameTest(template="combat",batch="defense_modifier_save")
    public static void SignedModifierMigratesLegacyWorldsAndPresetsWithoutDoubleNegation(GameTestHelper h){
        var legacy=new VirtualModule("armor").save();var settings=legacy.getCompound("Settings");settings.remove("damageModifier");settings.putDouble("reduction",75);
        var migrated=VirtualModule.load(legacy);h.assertTrue(migrated.value("damageModifier")==-75,"Legacy reduction became amplification");
        h.assertTrue(ModulePreset.create(legacy).value("damageModifier")==-75,"Legacy personal preset failed migration");
        h.assertTrue(VirtualModule.load(migrated.save()).value("damageModifier")==-75,"Reload double-negated migrated immunity");
        migrated.edit("damageModifier",50,"");var saved=migrated.save();saved.getCompound("Settings").putDouble("reduction",100);
        h.assertTrue(VirtualModule.load(saved).value("damageModifier")==50&&ModulePreset.create(saved).value("damageModifier")==50,"Stale legacy key overrode current signed setting");h.succeed();
    }
    @GameTest(template="combat",batch="defense_modifier_dps")
    public static void AmplifiedPartDamageFeedsAdaptiveStatistics(GameTestHelper h){
        var heart=fixture(h,4,4);var p=author(h);heart.configure(1000,60);heart.editCore(p,"defenseEnabled",1,"");
        var modifier=heart.modules().add("armor");modifier.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.UP)));modifier.edit("damageModifier",100,"");heart.tryActivate(p);
        heart.damageAt(heart.getBlockPos(),100);h.assertTrue(heart.adaptiveDefense().dps()==200&&heart.health().current()==900,"Adaptive defense did not measure amplified damage before reduction");h.succeed();
    }
}
