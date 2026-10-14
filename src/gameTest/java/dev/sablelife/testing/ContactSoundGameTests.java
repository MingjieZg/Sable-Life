package dev.sablelife.testing;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
/** Keep current contact regressions in the active sound suite, separate from withdrawn legacy attack fixtures. */
@GameTestHolder("sablelife_sound_tests") @PrefixGameTestTemplate(false)
public final class ContactSoundGameTests {
 @GameTest(template="combat",batch="trigger_contact_faces") public static void TriggerSoundContactDetectsSixFaces(GameTestHelper h){VirtualModuleGameTests.contactDetectsAllSixConfiguredFacesAndReloads(h);}
 @GameTest(template="combat",batch="trigger_contact_reentry",timeoutTicks=100) public static void TriggerSoundContactReentryKeepsCooldown(GameTestHelper h){VirtualModuleGameTests.contactReentryHasIndependentCooldown(h);}
 @GameTest(template="combat",batch="trigger_contact_once") public static void TriggerSoundContactDoesNotLoopWhileTouching(GameTestHelper h){VirtualModuleGameTests.contactSoundOnlyPlaysOnNewGroundContact(h);}
 @GameTest(template="combat",batch="trigger_contact_schema") public static void ContactSettingsBelongOnlyToTriggerSoundAndReload(GameTestHelper h){VirtualModuleGameTests.extendedSoundSettingsReloadAndOldModulesStayQuiet(h);}
 @GameTest(template="combat",batch="trigger_contact_signal",timeoutTicks=100) public static void ContactHonorsTriggerAndNormalSoundRemainsIndependent(GameTestHelper h){
  var level=h.getLevel();var p=h.absolutePos(new net.minecraft.core.BlockPos(4,6,4));level.setBlockAndUpdate(p,dev.sablelife.SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(p.east(),net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());
  for(int x=-2;x<=3;x++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(p.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
  var body=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,p,java.util.List.of(p,p.east()),new dev.ryanhcode.sable.companion.math.BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var core=(dev.sablelife.heart.HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
  var module=core.modules().add("trigger_sound");module.anchors(java.util.List.of(dev.sablelife.module.ModuleAnchor.create(core,core.getBlockPos().east(),net.minecraft.core.Direction.DOWN)));module.edit("triggerSound",0,"");module.edit("contactMode",1,"");module.edit("contactSound",0,"minecraft:block.note_block.bass");module.edit("contactDistance",.5,"");
  core.tryActivate(TestActors.creative(level));var ground=net.minecraft.core.BlockPos.containing(dev.sablelife.module.ModuleEvents.contactStart(core,module).add(0,-.25,0));level.setBlockAndUpdate(ground,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());core.modules().tick(core);h.assertTrue(core.modules().events().emittedSounds()==0,"Contact ignored inactive redstone trigger");
  module.edit("trigger",0,"");core.modules().tick(core);h.assertTrue(core.modules().events().emittedSounds()==1,"Active trigger did not enable contact audio");
  module.edit("contactMode",0,"");module.edit("trigger",1,"");h.assertTrue(module.edit("triggerSound",0,"minecraft:entity.ravager.roar"),"Normal sound selection rejected");core.modules().tick(core);
  h.runAfterDelay(25,()->{module.edit("trigger",0,"");core.modules().tick(core);h.assertTrue(core.modules().events().emittedSounds()==2,"Normal trigger after cooldown did not play independently: "+core.modules().events().emittedSounds()+" life="+core.health().state()+" valid="+dev.sablelife.module.HeartModules.status(module,core)+" power="+dev.sablelife.module.HeartModules.triggered(module,core)+" audio="+module.hasSound("triggerSound"));h.succeed();});
 }
}
