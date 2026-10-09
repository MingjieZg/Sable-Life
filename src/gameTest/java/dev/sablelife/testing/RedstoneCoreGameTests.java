package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_redstone_core_tests")
@PrefixGameTestTemplate(false)
public final class RedstoneCoreGameTests {
    @GameTest(template="combat",batch="module_order")
    public static void ModuleReorderingKeepsIdentityAndPersistsOrder(GameTestHelper h){
        var core=fixture(h,false);var author=player(h,core);var menu=new HeartDashboardMenu(1,author.getInventory(),core);
        var first=core.modules().add("sound");var second=core.modules().add("auto_facing");var controller=core.modules().add("move_set");
        h.assertTrue(menu.action(author,core.modules().revision(),second.id(),"reorder","",-1,""),"Reorder refused");
        h.assertTrue(core.modules().all().getFirst()==second&&core.modules().find(first.id())==first,"Reorder replaced module objects or identities");
        h.assertTrue(!menu.action(author,core.modules().revision(),second.id(),"reorder","",-1,"")&&!menu.action(author,core.modules().revision(),second.id(),"reorder","",.5,""),"Invalid or out-of-range reorder accepted");
        var restored=new HeartModules();restored.load(core.modules().save());h.assertTrue(restored.all().getFirst().id().equals(second.id())&&restored.all().get(1).id().equals(first.id())&&restored.all().get(2).id().equals(controller.id()),"Saved ordering lost");
        h.succeed();
    }
    @GameTest(template="combat",batch="module_presets")
    public static void PresetsPreserveConfigurationButNotMarksAndRespectCorePolicy(GameTestHelper h){
        var core=fixture(h,false);var author=player(h,core);var menu=new HeartDashboardMenu(1,author.getInventory(),core);
        var source=new VirtualModule("auto_facing");source.edit("facingSpeed",250,"");source.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.NORTH)));
        var preset=ModulePreset.portable(source.save());
        h.assertTrue(menu.action(author,core.modules().revision(),new UUID(0,0),"add_preset","auto_facing",0,preset.toString()),"Valid control preset refused");
        var added=core.modules().all().getFirst();h.assertTrue(!added.id().equals(source.id())&&added.anchors().isEmpty()&&added.value("facingSpeed")==250,"Preset lost settings or retained source marks/identity");
        h.assertTrue(!menu.action(author,core.modules().revision()-1,new UUID(0,0),"add_preset","auto_facing",0,preset.toString()),"Stale preset revision accepted");
        h.assertTrue(!menu.action(author,core.modules().revision(),new UUID(0,0),"add_preset","flame",0,new VirtualModule("flame").save().toString()),"Attack bypassed redstone whitelist");
        h.assertTrue(!menu.action(author,core.modules().revision(),new UUID(0,0),"add_preset","auto_facing",0,new VirtualModule("flame").save().toString()),"Forged type bypassed whitelist");
        h.succeed();
    }
    @GameTest(template="combat",batch="redstone_audio_editor")
    public static void SurvivalSoundMixEditorRetainsDisabledCombatSettings(GameTestHelper h){
        var core=fixture(h,false);var author=player(h,core);var dashboard=new HeartDashboardMenu(1,author.getInventory(),core);
        for(String type:List.of("sound","trigger_sound","bgm"))h.assertTrue(dashboard.action(author,core.modules().revision(),new UUID(0,0),"add",type,0,""),"Sound module add rejected: "+type);
        for(var m:core.modules().all())for(String key:m.type().equals("sound")?List.of("approach","ambient","contactSound"):List.of(m.type().equals("bgm")?"bgm":"triggerSound")){
            h.assertTrue(dev.sablelife.sound.SoundTimelineMenu.open(author,core,m.id(),key,"module"),"Survival sound editor rejected: "+key);
            var editor=new dev.sablelife.sound.SoundTimelineMenu(1,author.getInventory(),core,m.id(),key,"module");h.assertTrue(editor.stillValid(author)&&editor.action(author,editor.snapshot().getInt("Revision"),"add",null,0,"minecraft:block.note_block.bass"),"Survival sound mix edit failed");
        }
        var sound=core.modules().all().get(0);var bgm=core.modules().all().get(2);
        h.assertTrue(!dev.sablelife.sound.SoundTimelineMenu.open(author,core,sound.id(),"hurt","module"),"Hurt editor opened on control core");
        var forged=new dev.sablelife.sound.SoundTimelineMenu(1,author.getInventory(),core,sound.id(),"hurt","module");h.assertTrue(!forged.action(author,forged.snapshot().getInt("Revision"),"add",null,0,"minecraft:block.note_block.bass"),"Forged hurt editor changed configuration");
        h.assertTrue(!dashboard.action(author,core.modules().revision(),bgm.id(),"edit","bgmStart",1,""),"Combat BGM start edited on control core");
        h.assertTrue(bgm.value("bgmStart")==0&&ModuleEvents.musicReady(core,bgm)&&sound.hasSound("hurt"),"Disabled shared settings erased, or BGM waits for impossible damage");
        var data=core.copyConfiguration();h.assertTrue(core.pasteConfiguration(author,data)&&core.modules().all().size()==3&&core.modules().all().get(2).value("bgmStart")==0,"Copy lost sounds or disabled combat configuration");h.succeed();
    }
    @GameTest(template="combat",batch="redstone_audio_runtime",timeoutTicks=20)
    public static void TriggerSoundRunsWithoutLifeAndMasterClearsRepeatState(GameTestHelper h){
        var core=fixture(h,false);var trigger=core.modules().add("trigger_sound");trigger.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.NORTH)));trigger.edit("trigger",0,"");trigger.edit("cooldown",0,"");
        var ambient=core.modules().add("sound");ambient.anchors(trigger.anchors());
        core.modules().tick(core);long count=core.modules().events().emittedSounds();h.assertTrue(count==1,"Trigger sound required life activation");core.modules().events().hurt(core);h.assertTrue(core.modules().events().emittedSounds()==count,"Disabled Hurt sound played");
        core.modules().tick(core);h.assertTrue(core.modules().events().emittedSounds()==count,"Once trigger repeated while signal stayed high");
        core.wireless().master(false);core.modules().tick(core);core.wireless().master(true);core.modules().tick(core);h.assertTrue(core.modules().events().emittedSounds()==count+1,"Master restart did not reset audio trigger");
        trigger.edit("soundMode",1,"");trigger.edit("interval",2,"");
        h.runAfterDelay(4,()->{h.assertTrue(core.modules().events().emittedSounds()>count+1,"Control repeated sound did not run");core.wireless().master(false);core.modules().tick(core);long stopped=core.modules().events().emittedSounds();h.runAfterDelay(4,()->{h.assertTrue(core.modules().events().emittedSounds()==stopped&&core.health().state()==HeartState.DORMANT,"Master off kept audio or activated life");h.succeed();});});
    }
    @GameTest(template="combat",timeoutTicks=20,batch="redstone_survivalmovesetschedulesonlyallowedactionswithouthealth")
    public static void SurvivalMoveSetSchedulesOnlyAllowedActionsWithoutHealth(GameTestHelper h){
        var core=fixture(h,false);var author=player(h,core);var menu=new HeartDashboardMenu(1,author.getInventory(),core);
        for(var type:List.of("move_set","sensor","navigation","auto_facing"))h.assertTrue(menu.action(author,core.modules().revision(),new UUID(0,0),"add",type,0,""),"New control module rejected: "+type);
        var table=core.modules().all().getFirst();var out=core.modules().add("wireless_redstone");out.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));out.edit("redstonePower",8,"");
        h.assertTrue(menu.action(author,core.modules().revision(),table.id(),"move_set","add",0,""),"Control move editor rejected");
        h.assertTrue(menu.action(author,core.modules().revision(),table.id(),"move_set","action_add",0,"0|"+out.id()),"Allowed timeline action rejected");
        menu.action(author,core.modules().revision(),table.id(),"move_set","action_start",.2,"0|0");
        for(var type:List.of("trigger_sound","sound","bgm")){
            var audio=core.modules().add(type);if(!type.equals("bgm"))audio.anchors(out.anchors());
            h.assertTrue(menu.action(author,core.modules().revision(),table.id(),"move_set","action_add",0,"0|"+audio.id()),"Sound action rejected by control Move set: "+type);
            h.assertTrue(ModuleMoveSet.status(table.moveSet().get(0),core).equals("ready"),"Sound action made control move unavailable");
        }
        var forbidden=core.modules().add("flame");h.assertTrue(!menu.action(author,core.modules().revision(),table.id(),"move_set","action_add",0,"0|"+forbidden.id()),"Timeline bypassed whitelist");
        h.assertTrue(!menu.action(author,core.modules().revision(),table.id(),"move_set","phaseUpper",50,"0"),"HP edit allowed on non-living core");
        core.modules().remove(forbidden.id());
        // Imported legacy HP bands are ignored by the non-living controller.
        table.moveSet().get(0).config().edit("phaseUpper",50,"");
        var pig=net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setNoGravity(true);var center=SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());pig.setPos(center.add(0,0,4));h.getLevel().addFreshEntity(pig);
        core.modules().tick(core);h.assertTrue(!HeartModules.triggered(out,core),"Delayed control action fired early");
        h.runAfterDelay(6,()->{h.assertTrue(HeartModules.triggered(out,core),"Control move did not dispatch without life: "+core.modules().snapshot(core,true));var a=out.anchors().getFirst();h.assertTrue(ModuleSignals.signal(h.getLevel(),a.position(a.body(core)),Direction.DOWN)==8,"Move-set redstone output missing");core.wireless().master(false);core.modules().tick(core);h.assertTrue(!HeartModules.triggered(out,core),"Master off retained move dispatch");pig.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="redstone_sensorandalignmentpublishsignalswithoutlife")
    public static void SensorAndAlignmentPublishSignalsWithoutLife(GameTestHelper h){
        var core=fixture(h,false);var sensor=core.modules().add("sensor");var nav=core.modules().add("navigation");
        sensor.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.NORTH)));nav.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.NORTH)));sensor.edit("target",1,"");nav.edit("target",1,"");sensor.edit("lineOfSight",1,"");nav.edit("lineOfSight",1,"");
        var center=SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(center.add(-4,0,-6));h.getLevel().addFreshEntity(cow);
        core.modules().tick(core);var a=sensor.anchors().getFirst();h.assertTrue(ModuleSignals.signal(h.getLevel(),a.position(a.body(core)),Direction.DOWN)==15,"Dormant sensor output missing");
        h.assertTrue(core.modules().events().navigation().left(nav.id())>0,"Dormant target alignment output missing");
        core.wireless().master(false);core.modules().tick(core);h.assertTrue(ModuleSignals.signal(h.getLevel(),a.position(a.body(core)),Direction.DOWN)==0,"Master off retained sensing output");cow.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30,batch="redstone_redstonecorefacestargetwithoutlifeactivation")
    public static void RedstoneCoreFacesTargetWithoutLifeActivation(GameTestHelper h){
        var core=fixture(h,false);var facing=core.modules().add("auto_facing");facing.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.NORTH)));facing.edit("target",1,"");facing.edit("facingSpeed",720,"");
        var center=SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(center.add(6,0,0));h.getLevel().addFreshEntity(cow);
        h.runAfterDelay(15,()->{var a=facing.anchors().getFirst();var front=dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(a.body(core).logicalPose().orientation()),new net.minecraft.world.phys.Vec3(0,0,-1));var pivot=SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());var delta=cow.getBoundingBox().getCenter().subtract(pivot);delta=new net.minecraft.world.phys.Vec3(delta.x,0,delta.z).normalize();h.assertTrue(front.dot(delta)>.98&&core.health().state()==HeartState.DORMANT,"Control facing failed or activated life");cow.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="redstone_specifiedrecipeproducesoneredstonecore")
    public static void SpecifiedRecipeProducesOneRedstoneCore(GameTestHelper h){
        var recipe=(net.minecraft.world.item.crafting.CraftingRecipe)h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sablelife","redstone_core")).orElseThrow().value();
        var items=List.of(net.minecraft.world.item.Items.REDSTONE_BLOCK,net.minecraft.world.item.Items.REDSTONE_TORCH,net.minecraft.world.item.Items.REDSTONE_BLOCK,net.minecraft.world.item.Items.REPEATER,net.minecraft.world.item.Items.AMETHYST_SHARD,net.minecraft.world.item.Items.COMPARATOR,net.minecraft.world.item.Items.REDSTONE_BLOCK,net.minecraft.world.item.Items.LEVER,net.minecraft.world.item.Items.REDSTONE_BLOCK);
        var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,items.stream().map(net.minecraft.world.item.ItemStack::new).toList());
        var result=recipe.assemble(input,h.getLevel().registryAccess());h.assertTrue(recipe.matches(input,h.getLevel())&&result.is(SableLife.REDSTONE_CORE_ITEM.get())&&result.getCount()==1,"Specified recipe mismatch");h.succeed();
    }
    private static HeartBlockEntity fixture(GameTestHelper h,boolean duplicate){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,6,3));level.setBlockAndUpdate(pos,SableLife.REDSTONE_CORE.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),duplicate?SableLife.HEART.get().defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static net.neoforged.neoforge.common.util.FakePlayer player(GameTestHelper h,HeartBlockEntity core){var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"RedstoneAuthor"));p.setGameMode(GameType.SURVIVAL);var w=SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());p.setPos(w.x,w.y,w.z);return p;}
    @GameTest(template="combat",batch="redstone_survivaleditsandsignalsrunwithoutactivatinglife")
    public static void SurvivalEditsAndSignalsRunWithoutActivatingLife(GameTestHelper h){
        var core=fixture(h,false);var author=player(h,core);h.assertTrue(core.isControlCore()&&core.canEdit(author)&&core.operational()&&core.health().state()==HeartState.DORMANT,"Controller required creative or life activation: edit="+core.canEdit(author)+", ready="+core.operational()+", graph="+SableBodyProbe.inspect(h.getLevel(),core.getBlockPos()));
        var menu=new HeartDashboardMenu(1,author.getInventory(),core);h.assertTrue(menu.stillValid(author),"Survival dashboard inaccessible");
        h.assertTrue(menu.action(author,core.modules().revision(),new UUID(0,0),"add","wireless_redstone",0,""),"Survival module add failed");var module=core.modules().all().getFirst();module.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));module.edit("redstonePower",11,"");module.edit("trigger",0,"");
        h.assertTrue(HeartModules.triggered(module,core),"Controller trigger not powered: "+HeartModules.status(module,core));core.modules().tick(core);var anchor=module.anchors().getFirst();var pos=anchor.position(anchor.body(core));h.assertTrue(ModuleSignals.signal(h.getLevel(),pos,Direction.DOWN)==11,"Marked output did not work without life, actual="+ModuleSignals.signal(h.getLevel(),pos,Direction.DOWN)+", ready="+core.operational());
        core.wireless().master(false);core.modules().tick(core);h.assertTrue(ModuleSignals.signal(h.getLevel(),pos,Direction.DOWN)==0,"Master off kept output");h.assertTrue(core.health().state()==HeartState.DORMANT,"Signal output activated life");h.succeed();
    }
    @GameTest(template="combat",batch="redstone_whitelistrejectsforgedrequestsandpoisonedimports")
    public static void WhitelistRejectsForgedRequestsAndPoisonedImports(GameTestHelper h){
        var core=fixture(h,false);var author=player(h,core);var menu=new HeartDashboardMenu(1,author.getInventory(),core);
        for(String type:List.of("command","flame","linear_movement","health_phase","particles"))h.assertTrue(!menu.action(author,core.modules().revision(),new UUID(0,0),"add",type,0,""),"Unsafe type accepted: "+type);
        var m=core.modules().add("wireless_redstone");h.assertTrue(!menu.action(author,core.modules().revision(),m.id(),"edit","trigger",5,""),"Hurt trigger accepted");
        h.assertTrue(!core.editCore(author,"health",500,"")&&!core.editCore(author,"deathPreview",0,""),"Life settings exposed");
        core.modules().add("flame");m.edit("trigger",5,"");var tag=core.saveWithoutMetadata(h.getLevel().registryAccess());tag.putString("LifeState","ALIVE");tag.putFloat("Health",100);
        var restored=new HeartBlockEntity(core.getBlockPos(),SableLife.REDSTONE_CORE.get().defaultBlockState());restored.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(restored.health().state()==HeartState.DORMANT&&restored.modules().all().stream().allMatch(RedstoneCorePolicy::allowed),"NBT reactivated life or bypassed whitelist");h.succeed();
    }
    @GameTest(template="combat",batch="redstone_mixedduplicatecoressuspendbothcontrollers")
    public static void MixedDuplicateCoresSuspendBothControllers(GameTestHelper h){
        var core=fixture(h,true);h.assertTrue(SableBodyProbe.findConnectedHearts(h.getLevel(),core.getBlockPos()).size()==2&&!core.operational(),"Duplicate body accepted controller");
        var full=SableBodyProbe.findConnectedHearts(h.getLevel(),core.getBlockPos()).stream().filter(c->!c.isControlCore()).findFirst().orElseThrow();full.health().activate();h.assertTrue(!full.operational(),"Full life core bypassed mixed-core limit");h.succeed();
    }
    @GameTest(template="combat",batch="redstone_sequenceproducesinternalsignalwithoutlife")
    public static void SequenceProducesInternalSignalWithoutLife(GameTestHelper h){
        var core=fixture(h,false);int channel=core.wireless().add();core.wireless().channel(channel).set(0,1);core.wireless().channel(channel).set(6,3);
        var sequence=core.modules().add("sequence");sequence.editSequence("add",0,0);sequence.editSequence("channel",0,channel+1);sequence.editSequence("power",0,9);
        core.modules().tick(core);h.assertTrue(core.wireless().output(channel)==9,"Sequence did not drive internal program channel");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30,batch="redstone_externalfrequencysurvivaleditorandconfigurationcopy")
    public static void ExternalFrequencySurvivalEditorAndConfigurationCopy(GameTestHelper h){
        var core=fixture(h,false);var level=h.getLevel();var author=player(h,core);var m=core.modules().add("wireless_redstone");m.edit("trigger",0,"");m.edit("redstonePower",12,"");
        var menu=new ModuleFrequencyMenu(1,author.getInventory(),core,m);
        menu.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,4));menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,author);
        menu.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD,3));menu.clicked(1,0,net.minecraft.world.inventory.ClickType.PICKUP,author);
        h.assertTrue(menu.getCarried().getCount()==3&&m.hasRedBlue(),"Survival frequency editor consumed or refused samples");
        var p=h.absolutePos(new BlockPos(4,6,7));level.setBlockAndUpdate(p.below(),Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(p,com.simibubi.create.AllBlocks.REDSTONE_LINK.getDefaultState().setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.RECEIVER,true).setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.FACING,Direction.UP));
        var receiver=(com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity)level.getBlockEntity(p);receiver.loadWithComponents(new net.minecraft.nbt.CompoundTag(),level.registryAccess());receiver.tick();
        var link=receiver.getBehaviour(com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);link.setFrequency(true,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));link.setFrequency(false,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD));
        core.modules().tick(core);
        h.runAfterDelay(5,()->{
            h.assertTrue(receiver.getReceivedSignal()==12,"Dormant redstone core failed external output");
            var config=core.copyConfiguration();h.assertTrue(core.pasteConfiguration(author,config)&&core.modules().all().getFirst().hasRedBlue()&&core.health().state()==HeartState.DORMANT,"Copy lost frequency or activated life");
            core.modules().tick(core);core.wireless().master(false);h.assertTrue(receiver.getReceivedSignal()==0,"Master off kept external output");h.succeed();
        });
    }
    @GameTest(template="combat",timeoutTicks=10,batch="redstone_placementrejectsmixedsecondcoreandremovalresumesoutput")
    public static void PlacementRejectsMixedSecondCoreAndRemovalResumesOutput(GameTestHelper h){
        var core=fixture(h,false);var level=h.getLevel();var p=core.getBlockPos().east();var previous=net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(),level,p);
        level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());
        var event=new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(previous,core.getBlockState(),player(h,core));CorePlacementRules.placed(event);
        h.assertTrue(event.isCanceled(),"Mixed duplicate placement accepted");
        h.runAfterDelay(1,()->{h.assertTrue(!core.operational(),"Duplicate did not suspend output");level.setBlockAndUpdate(p,Blocks.IRON_BLOCK.defaultBlockState());});
        h.runAfterDelay(2,()->{h.assertTrue(core.operational(),"Removing duplicate did not resume output");h.succeed();});
    }
}

