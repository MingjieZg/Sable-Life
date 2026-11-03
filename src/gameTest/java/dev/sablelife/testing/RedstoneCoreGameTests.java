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
        var first=core.modules().add("sequence");var second=core.modules().add("auto_facing");var controller=core.modules().add("sensor");
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
    @GameTest(template="combat",batch="redstone_restricted_catalog")
    public static void RestrictedCatalogRejectsPresentationAndLifeModules(GameTestHelper h){
        var core=fixture(h,false);var author=player(h,core);var menu=new HeartDashboardMenu(1,author.getInventory(),core);
        for(String type:VirtualModule.AVAILABLE_TYPES){
            boolean accepted=menu.action(author,core.modules().revision(),new UUID(0,0),"add",type,0,"");
            h.assertTrue(accepted==ModuleAvailability.enabled(true,type),"Unexpected survival capability: "+type);
        }
        var module=core.modules().all().getFirst();
        for(int trigger:List.of(4,5))h.assertTrue(!menu.action(author,core.modules().revision(),module.id(),"edit","trigger",trigger,""),"Life-only trigger accepted");
        h.succeed();
    }
    @GameTest(template="combat",batch="redstone_legacy_retention")
    public static void LegacyModulesRemainStoredWithoutRunning(GameTestHelper h){
        var core=fixture(h,false);var author=player(h,core);var menu=new HeartDashboardMenu(1,author.getInventory(),core);
        var legacy=core.modules().add("sound");legacy.edit("ambient",0,"minecraft:entity.ravager.roar");legacy.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.NORTH)));
        var before=legacy.text("ambient");RedstoneCorePolicy.sanitize(core);
        h.assertTrue(core.modules().find(legacy.id())==legacy&&!legacy.enabled()&&legacy.text("ambient").equals(before),"Legacy configuration lost");
        legacy.edit("enabled",1,"");core.modules().tick(core);
        h.assertTrue(!HeartModules.triggered(legacy,core)&&core.modules().events().emittedSounds()==0,"Legacy module bypassed runtime restriction");
        h.assertTrue(!menu.action(author,core.modules().revision(),legacy.id(),"copy","",0,""),"Forbidden legacy module copied");
        h.assertTrue(menu.action(author,core.modules().revision(),legacy.id(),"remove","",0,""),"Legacy module could not be removed");h.succeed();
    }
    @GameTest(template="combat",batch="redstone_signal_logic")
    public static void SurvivalSignalLogicRunsAndClearsWithMaster(GameTestHelper h){
        var core=fixture(h,false);var author=player(h,core);var menu=new HeartDashboardMenu(1,author.getInventory(),core);
        int input=core.wireless().add();int output=core.wireless().add();
        for(int channel:List.of(input,output)){core.wireless().channel(channel).set(0,1);core.wireless().channel(channel).set(6,3);}
        h.assertTrue(menu.action(author,core.modules().revision(),new UUID(0,0),"add","signal_logic",0,""),"Signal logic add rejected");
        var logic=core.modules().all().getFirst();logic.edit("logicChannel",output+1,"");logic.edit("logicPower",9,"");
        h.assertTrue(menu.action(author,core.modules().revision(),logic.id(),"signal_rules","",0,""),"Signal rule editor rejected");
        var editor=new SignalRuleMenu(1,author.getInventory(),core,logic,null);
        h.assertTrue(editor.action(author,core.modules().revision(),new UUID(0,0),"add",0),"Survival signal rule add rejected");
        for(var row:logic.signalRules().rows())row.edit("signalInput",input+1);
        core.wireless().programOutputs("test",Map.of(input,15));core.modules().tick(core);
        h.assertTrue(core.wireless().output(output)==9&&core.health().state()==HeartState.DORMANT,"Signal logic required life activation");
        h.runAfterDelay(2,()->core.wireless().programOutputs("test",Map.of()));
        h.runAfterDelay(3,()->{core.modules().tick(core);h.assertTrue(core.wireless().output(output)==0,"Logic retained output after input ended");core.wireless().programOutputs("test",Map.of(input,15));});
        h.runAfterDelay(4,()->{core.modules().tick(core);h.assertTrue(core.wireless().output(output)==9,"Logic did not rearm");core.wireless().master(false);});
        h.runAfterDelay(6,()->{core.modules().tick(core);h.assertTrue(core.wireless().output(output)==0,"Master off retained logic output");h.succeed();});
    }
    @GameTest(template="combat",batch="redstone_sensorandalignmentpublishsignalswithoutlife")
    public static void SensorAndAlignmentPublishSignalsWithoutLife(GameTestHelper h){
        var core=fixture(h,false);var sensor=core.modules().add("sensor");var nav=core.modules().add("navigation");
        sensor.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.NORTH)));nav.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.NORTH)));sensor.edit("target",1,"");nav.edit("target",1,"");sensor.edit("lineOfSight",1,"");nav.edit("lineOfSight",1,"");
        var center=SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(center.add(-4,0,-6));h.getLevel().addFreshEntity(cow);
        core.modules().tick(core);var a=sensor.anchors().getFirst();h.assertTrue(h.getLevel().getBestNeighborSignal(a.position(a.body(core)))==15,"Dormant sensor output missing");
        h.assertTrue(core.modules().events().navigation().left(nav.id())>0,"Dormant target alignment output missing");
        core.wireless().master(false);core.modules().tick(core);h.assertTrue(h.getLevel().getBestNeighborSignal(a.position(a.body(core)))==0,"Master off retained sensing output");cow.discard();h.succeed();
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
        h.assertTrue(HeartModules.triggered(module,core),"Controller trigger not powered: "+HeartModules.status(module,core));core.modules().tick(core);var anchor=module.anchors().getFirst();var pos=anchor.position(anchor.body(core));h.assertTrue(ModuleSignals.signal(h.getLevel(),pos.above(),Direction.UP)==11,"Marked output did not work without life, actual="+ModuleSignals.signal(h.getLevel(),pos.above(),Direction.UP)+", ready="+core.operational());
        h.assertTrue(module.value("redstoneFaces")==1,"Survival relay did not default to selected surface");
        h.assertTrue(menu.action(author,core.modules().revision(),module.id(),"edit","redstoneRampRange",0,"12|2"),"Survival endpoint pair editing failed");
        module.edit("redstoneStrengthMode",2,"");core.modules().tick(core);h.assertTrue(ModuleSignals.signal(h.getLevel(),pos.above(),Direction.UP)==12,"Survival ramp required life activation");
        core.wireless().master(false);core.modules().tick(core);h.assertTrue(ModuleSignals.signal(h.getLevel(),pos.above(),Direction.UP)==0,"Master off kept output");h.assertTrue(core.health().state()==HeartState.DORMANT,"Signal output activated life");h.succeed();
    }
    @GameTest(template="combat",batch="redstone_whitelistrejectsforgedrequestsandpoisonedimports")
    public static void WhitelistRejectsForgedRequestsAndPoisonedImports(GameTestHelper h){
        var core=fixture(h,false);var author=player(h,core);var menu=new HeartDashboardMenu(1,author.getInventory(),core);
        for(String type:List.of("command","flame","linear_movement","health_phase","particles"))h.assertTrue(!menu.action(author,core.modules().revision(),new UUID(0,0),"add",type,0,""),"Unsafe type accepted: "+type);
        var m=core.modules().add("wireless_redstone");h.assertTrue(!menu.action(author,core.modules().revision(),m.id(),"edit","trigger",5,""),"Hurt trigger accepted");
        h.assertTrue(!core.editCore(author,"health",500,"")&&!core.editCore(author,"deathPreview",0,""),"Life settings exposed");
        core.modules().add("flame");m.edit("trigger",5,"");var tag=core.saveWithoutMetadata(h.getLevel().registryAccess());tag.putString("LifeState","ALIVE");tag.putFloat("Health",100);
        var restored=new HeartBlockEntity(core.getBlockPos(),SableLife.REDSTONE_CORE.get().defaultBlockState());restored.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(restored.health().state()==HeartState.DORMANT&&restored.modules().all().size()==2&&restored.modules().all().stream().noneMatch(VirtualModule::enabled),"NBT reactivated life or bypassed whitelist");h.succeed();
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

