package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.attack.ModuleArrow;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;

@GameTestHolder("sablelife_control_tests")
@PrefixGameTestTemplate(false)
public final class MoveSetGameTests {
    @GameTest(template="combat",batch="moveset_diagnostics")
    public static void DiagnosticsIdentifyEveryBrokenReferenceAndClearAfterRepair(GameTestHelper h){
        var heart=fixture(h);var disabled=heart.modules().add("wireless_redstone");var unmarked=heart.modules().add("melee");var wrong=heart.modules().add("wireless_redstone");var deleted=heart.modules().add("wireless_redstone");var valid=heart.modules().add("wireless_redstone");var table=heart.modules().add("move_set");
        table.moveSet().edit("add",0,"",heart.modules());
        for(var m:List.of(disabled,unmarked,wrong,deleted,valid,disabled))table.moveSet().edit("action_add",0,"0|"+m.id(),heart.modules());
        disabled.edit("enabled",0,"");wrong.edit("trigger",0,"");heart.modules().remove(deleted.id());
        var runtime=new ModuleMoveSet();var problems=runtime.snapshot(table,heart).getList("Rows",10).getCompound(0).getList("Problems",10);
        h.assertTrue(problems.size()==4,"Diagnostics duplicated references or included valid module");
        var ids=List.of(disabled.id(),unmarked.id(),wrong.id(),deleted.id());var reasons=List.of("disabled","unmarked","trigger","missing");
        for(int i=0;i<4;i++)h.assertTrue(problems.getCompound(i).getUUID("Module").equals(ids.get(i))&&problems.getCompound(i).getString("Reason").equals(reasons.get(i))&&problems.getCompound(i).getInt("Action")==i+1,"Incorrect diagnostic identity/reason/action index "+i);
        disabled.edit("enabled",1,"");wrong.edit("trigger",4,"");table.moveSet().edit("action_delete",0,"0|3",heart.modules());table.moveSet().edit("action_delete",0,"0|1",heart.modules());
        h.assertTrue(runtime.snapshot(table,heart).getList("Rows",10).getCompound(0).getList("Problems",10).isEmpty(),"Repaired move retained stale diagnostics");h.succeed();
    }
    @GameTest(template="combat",batch="moveset_rules_migration")
    public static void LegacyMoveRowConfigMigratesWithoutStandaloneDispatcher(GameTestHelper h){
        var heart=fixture(h);var out=heart.modules().add("wireless_redstone");out.edit("trigger",4,"");var table=heart.modules().add("move_set");var rules=new MoveRules();rules.edit("name",0,"Migrated move");rules.edit("dispatchWeight",37,"");rules.edit("dispatchCooldown",85,"");rules.edit("dispatchBusy",120,"");rules.edit("dispatchRest",15,"");rules.edit("dispatchNear",2.5,"");rules.edit("dispatchFar",19,"");rules.edit("phaseLower",15,"");rules.edit("phaseUpper",70,"");rules.edit("dispatchCone",125,"");rules.edit("dispatchFront",2,"");rules.edit("lineOfSight",1,"");rules.edit("dispatchRepeat",1,"");
        var oldConfig=rules.save();oldConfig.putString("Type","dispatch");oldConfig.getCompound("Settings").putDouble("dispatchChannel",9);oldConfig.getCompound("Settings").putDouble("dispatchPower",7);oldConfig.getCompound("Settings").putDouble("dispatchPulse",12);
        var oldRow=new net.minecraft.nbt.CompoundTag();oldRow.put("Config",oldConfig);oldRow.putBoolean("Priority",true);var a=new net.minecraft.nbt.CompoundTag();a.putUUID("Module",out.id());a.putInt("Start",7);a.putInt("Duration",45);var actions=new net.minecraft.nbt.ListTag();actions.add(a);oldRow.put("Actions",actions);var rows=new net.minecraft.nbt.ListTag();rows.add(oldRow);
        var savedTable=table.save();savedTable.put("Moves",rows);var migrated=VirtualModule.load(savedTable);var move=migrated.moveSet().get(0);
        h.assertTrue(move.config().save().equals(rules.save())&&move.priority()&&move.actions().getFirst().module().equals(out.id())&&move.actions().getFirst().start()==7&&move.actions().getFirst().duration()==45,"Existing Move set lost rules, identity, priority or action timing");
        var pool=heart.modules().save();pool.getList("Modules",10).add(oldConfig);var loaded=new HeartModules();loaded.load(pool);h.assertTrue(loaded.all().size()==2&&loaded.all().stream().noneMatch(m->m.type().equals("dispatch"))&&loaded.add("dispatch")==null,"Removed standalone dispatcher retained runtime/catalog entry");
        h.assertTrue(!ModuleFields.moveRules().stream().anyMatch(f->java.util.Set.of("dispatchChannel","dispatchPower","dispatchPulse").contains(f.key())),"Move rules still contain obsolete output routing");h.succeed();
    }
    @GameTest(template="combat",batch="moveset_rules_filters")
    public static void IntegratedRulesFilterDistanceHpConeAndLineOfSight(GameTestHelper h){
        var heart=fixture(h);var rules=new MoveRules();rules.edit("target",1,"");var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter());var target=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setPos(center.add(0,0,3));h.getLevel().addFreshEntity(target);
        rules.edit("dispatchFar",1,"");h.assertTrue(!rules.targetPresent(heart),"Distance condition ignored");rules.edit("dispatchFar",10,"");rules.edit("phaseUpper",50,"");h.assertTrue(!rules.targetPresent(heart),"HP condition ignored");rules.edit("phaseUpper",100,"");rules.edit("dispatchCone",90,"");h.assertTrue(!rules.targetPresent(heart),"North-facing cone accepted south target");rules.edit("dispatchFront",1,"");h.assertTrue(rules.targetPresent(heart),"Valid facing target rejected");
        var wall=net.minecraft.core.BlockPos.containing(center.add(0,0,1.5));h.getLevel().setBlockAndUpdate(wall,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(wall.above(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());rules.edit("lineOfSight",1,"");h.assertTrue(!rules.targetPresent(heart),"LOS ignored wall");rules.edit("lineOfSight",0,"");h.assertTrue(rules.targetPresent(heart),"LOS Off still rejected obscured target");target.discard();h.succeed();
    }
    @GameTest(template="combat",batch="moveset_external_redstone",timeoutTicks=45)
    public static void UnmarkedRedstoneDoesNotBlockMoveAndTransmitsExternalFrequency(GameTestHelper h) {
        var heart=fixture(h);var level=h.getLevel();var arrow=weapon(heart,"arrow");
        var output=heart.modules().add("wireless_redstone");output.edit("redstonePower",11,"");
        var table=heart.modules().add("move_set");edit(h,heart,table,"add",0,"");
        for(var m:List.of(arrow,output))edit(h,heart,table,"action_add",0,"0|"+m.id());
        edit(h,heart,table,"dispatchCooldown",1200,"0");
        h.assertTrue(ModuleMoveSet.status(table.moveSet().get(0),heart).equals("ready"),"Empty optional redstone destinations blocked entire move");
        var items=level.registryAccess();
        output.redBlue(false,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND),items);
        output.redBlue(true,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD),items);
        var p=h.absolutePos(new BlockPos(4,6,7));level.setBlockAndUpdate(p.below(),Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(p,com.simibubi.create.AllBlocks.REDSTONE_LINK.getDefaultState()
                .setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.RECEIVER,true)
                .setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.FACING,Direction.UP));
        var receiver=(com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity)level.getBlockEntity(p);
        receiver.loadWithComponents(new net.minecraft.nbt.CompoundTag(),items);receiver.tick();
        var link=receiver.getBehaviour(com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);
        link.setFrequency(true,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        link.setFrequency(false,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD));
        output.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos().east(),Direction.UP)));
        output.clearMarks();
        heart.tryActivate(FakePlayerFactory.getMinecraft(level));heart.modules().tick(heart);
        h.runAfterDelay(5,()->{
            h.assertTrue(output.anchors().isEmpty()&&heart.wireless().size()==0&&HeartModules.triggered(output,heart),"External move output required Mark or channel");
            h.assertTrue(receiver.getReceivedSignal()==11&&arrows(h,heart)>0,"Unmarked external output blocked transmission or companion attack");
        });
        h.runAfterDelay(10,()->{
            h.assertTrue(receiver.getReceivedSignal()==11,"Clearing Mark stopped external move output");
            heart.wireless().master(false);
        });
        h.runAfterDelay(13,()->{h.assertTrue(receiver.getReceivedSignal()==0,"Master off retained external move signal");h.succeed();});
    }
    @GameTest(template="combat",batch="core_death_presentation",timeoutTicks=35)
    public static void DeathPresentationPersistsCopiesAndPreservesDeathLifecycle(GameTestHelper h) {
        var heart=fixture(h);
        var player=FakePlayerFactory.getMinecraft(h.getLevel());player.setGameMode(GameType.CREATIVE);
        h.assertTrue(heart.deathPresentation().particleSelection().particles().contains("minecraft:firework")&&heart.deathPresentation().text("deathSound").equals("minecraft:entity.firework_rocket.blast")&&heart.deathPresentation().text("deathTailSound").isEmpty(),"Default death burst missing firework particles or primary sound");
        h.assertTrue(heart.removalPresentation().particleSelection().particles().equals(List.of("minecraft:poof"))&&heart.removalPresentation().value("deathPattern")==5,"Default disappearance smoke missing");
        h.assertTrue(heart.editCore(player,"vanishParticle",0,"minecraft:cloud")&&heart.editCore(player,"vanishCount",8,"")&&heart.editCore(player,"vanishPattern",2,"")&&!heart.editCore(player,"vanishPattern",6,""),"Disappearance settings validation failed");
        var removal=heart.removalPresentation().save();
        h.assertTrue(heart.editCore(player,"deathParticle",0,"minecraft:cloud"),"Death particle edit rejected");
        h.assertTrue(heart.editCore(player,"deathSound",0,"minecraft:entity.firework_rocket.blast"),"Death sound edit rejected");
        h.assertTrue(heart.editCore(player,"deathPitch",.7,"")&&!heart.editCore(player,"deathPitch",.4,""),"Death sound pitch validation failed");
        h.assertTrue(heart.editCore(player,"deathDuration",2,"")&&heart.editCore(player,"deathScale",1.5,""),"Particle properties rejected");
        var particles=heart.deathPresentation().particleSelection();
        h.assertTrue(particles.editParticle("add","minecraft:flame")&&particles.editParticle("duration","minecraft:flame",3)&&particles.editParticle("size","minecraft:flame",2),"Multiple death particles or individual properties rejected");
        h.assertTrue(!heart.editCore(player,"deathScale",Double.NaN,"")&&!heart.editCore(player,"deathSound",0,"invalid:sound"),"Invalid death properties accepted");
        var saved=heart.deathPresentation().save();var loaded=new DeathPresentation();loaded.load(saved);
        h.assertTrue(loaded.save().equals(saved),"Death effects lost properties on save/load");
        h.assertTrue(loaded.particleSelection().particleEntry("minecraft:cloud").ticks()==40&&loaded.particleSelection().particleEntry("minecraft:flame").ticks()==60,"Individual particle lifetimes lost");
        var legacy=new net.minecraft.nbt.CompoundTag();legacy.putString("deathParticle","minecraft:cloud");legacy.putDouble("deathDuration",2);legacy.putDouble("deathScale",1.5);
        var migrated=new DeathPresentation();migrated.load(legacy);
        h.assertTrue(migrated.particleSelection().particleEntry("minecraft:cloud").ticks()==40&&migrated.particleSelection().particleEntry("minecraft:cloud").size()==1.5F,"Previous single-particle settings failed migration");
        h.assertTrue(heart.pasteConfiguration(player,heart.copyConfiguration())&&heart.deathPresentation().save().equals(saved),"Copier lost death effects");
        h.assertTrue(heart.removalPresentation().save().equals(removal),"Copier lost disappearance effects");
        h.assertTrue(heart.configure(20,20),"Death fixture configuration failed");heart.tryActivate(player);
        h.assertTrue(heart.health().state()==HeartState.ALIVE,"Death fixture failed to activate");
        h.assertTrue(heart.editCore(player,"deathPreview",0,"")&&heart.health().current()==20,"Preview changed health");
        for(int pattern=0;pattern<6;pattern++) {
            h.assertTrue(heart.editCore(player,"deathPattern",pattern,"")&&heart.editCore(player,"deathPreview",0,""),"Death pattern preview rejected");
        }
        h.assertTrue(heart.editCore(player,"vanishPreview",0,"")&&heart.health().state()==HeartState.ALIVE&&heart.health().current()==20,"Disappearance preview removed or killed creature");
        h.assertTrue(heart.damage(20)&&!heart.damage(1),"Lethal damage was rejected or repeated death was accepted");
        h.assertTrue(heart.health().state()==HeartState.DYING,"Cosmetic effects bypassed delayed death");
        var lifecycle=dev.sablelife.ownership.LifeLifecycle.get(h.getLevel().getServer());
        var jobs=lifecycle.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());
        var reloaded=dev.sablelife.ownership.LifeLifecycle.load(jobs,h.getLevel().registryAccess()).save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()).getList("Deaths",10);
        boolean preserved=false;for(int i=0;i<reloaded.size();i++){var job=reloaded.getCompound(i);if(job.getUUID("Id").equals(heart.creatureId()))preserved=job.getCompound("Presentation").equals(removal)&&!job.getBoolean("PresentationPlayed");}
        h.assertTrue(preserved,"Delayed disappearance lost settings on world save/load or played early");
        h.runAfterDelay(25,()->{h.assertTrue(heart.isRemoved()||heart.health().state()==HeartState.DEAD,"Cosmetic effects blocked death completion");h.succeed();});
    }
    private static HeartBlockEntity fixture(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,6,4));level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        var heart=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
        var pig=EntityType.PIG.create(level);pig.setNoAi(true);pig.setNoGravity(true);pig.setPos(SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter()).add(0,0,3));level.addFreshEntity(pig);
        return heart;
    }
    private static VirtualModule weapon(HeartBlockEntity heart,String type) {
        var m=heart.modules().add(type);m.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos().east(),Direction.UP)));return m;
    }
    private static void edit(GameTestHelper h,HeartBlockEntity heart,VirtualModule table,String key,double value,String text) {
        h.assertTrue(table.moveSet().edit(key,value,text,heart.modules()),"Move edit rejected: "+key);heart.modules().changed();
    }
    private static long arrows(GameTestHelper h,HeartBlockEntity heart) {
        long n=0;for(var e:h.getLevel().getAllEntities())if(e instanceof ModuleArrow&&e.getPersistentData().hasUUID("SableLifeAttackOwner")&&e.getPersistentData().getUUID("SableLifeAttackOwner").equals(heart.creatureId()))n++;return n;
    }
    @GameTest(template="combat",batch="moveset_timeline",timeoutTicks=55)
    public static void DirectTimelineStartsOverlapsAndStopsWithoutChannels(GameTestHelper h) {
        var heart=fixture(h);var arrow=weapon(heart,"arrow");var flame=weapon(heart,"flame");var gun=weapon(heart,"gun");
        var table=heart.modules().add("move_set");edit(h,heart,table,"add",0,"");
        for(var m:List.of(arrow,flame,gun))edit(h,heart,table,"action_add",0,"0|"+m.id());
        edit(h,heart,table,"action_start",.1,"0|0");edit(h,heart,table,"action_duration",.4,"0|0");
        edit(h,heart,table,"action_start",.2,"0|1");edit(h,heart,table,"action_duration",.4,"0|1");
        edit(h,heart,table,"action_start",.7,"0|2");edit(h,heart,table,"action_duration",.2,"0|2");
        edit(h,heart,table,"dispatchCooldown",1200,"0");
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.modules().tick(heart);
        h.assertTrue(heart.wireless().size()==0&&!HeartModules.triggered(arrow,heart),"Timeline needed a channel or ignored its start delay");
        h.runAfterDelay(6,()->{
            h.assertTrue(HeartModules.triggered(arrow,heart)&&HeartModules.triggered(flame,heart)&&!HeartModules.triggered(gun,heart),"Overlapping timeline actions have incorrect gates");
            h.assertTrue(arrows(h,heart)>0,"Timeline gate did not spawn an actual arrow");
        });
        h.runAfterDelay(15,()->h.assertTrue(!HeartModules.triggered(arrow,heart)&&!HeartModules.triggered(flame,heart)&&HeartModules.triggered(gun,heart),"Sequential gun action timing failed"));
        h.runAfterDelay(23,()->{h.assertTrue(!HeartModules.triggered(gun,heart),"Action outlived its duration");h.succeed();});
    }
    @GameTest(template="combat",batch="moveset_cleanup",timeoutTicks=40)
    public static void MissingDeletedDisabledAndMasterOffCannotLeakTriggers(GameTestHelper h) {
        var heart=fixture(h);var arrow=weapon(heart,"arrow");var table=heart.modules().add("move_set");edit(h,heart,table,"add",0,"");edit(h,heart,table,"action_add",0,"0|"+arrow.id());
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.modules().tick(heart);
        h.assertTrue(HeartModules.triggered(arrow,heart),"Ready move did not start");
        heart.wireless().master(false);heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(arrow,heart),"Master off retained direct trigger");
        heart.wireless().master(true);heart.modules().tick(heart);h.assertTrue(HeartModules.triggered(arrow,heart),"Master on failed to resume available move");
        table.edit("enabled",0,"");heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(arrow,heart),"Disabled table retained direct trigger");
        table.edit("enabled",1,"");heart.modules().tick(heart);heart.modules().remove(arrow.id());heart.modules().tick(heart);
        h.assertTrue(ModuleMoveSet.status(table.moveSet().get(0),heart).equals("missing"),"Missing reference was silently retargeted");
        h.assertTrue(!table.moveSet().edit("action_add",0,"0|"+table.id(),heart.modules()),"Move table allowed a recursive action");h.succeed();
    }
    @GameTest(template="combat",batch="moveset_copy",timeoutTicks=35)
    public static void SaveAndCopierRemapDirectReferencesAndPreserveRules(GameTestHelper h) {
        var heart=fixture(h);var arrow=weapon(heart,"arrow");var table=heart.modules().add("move_set");
        edit(h,heart,table,"add",0,"");edit(h,heart,table,"name",0,"0|Arrow move");edit(h,heart,table,"action_add",0,"0|"+arrow.id());
        edit(h,heart,table,"action_start",.5,"0|0");edit(h,heart,table,"action_duration",2,"0|0");
        var loaded=VirtualModule.load(table.save());var action=loaded.moveSet().get(0).actions().getFirst();
        h.assertTrue(action.module().equals(arrow.id())&&action.start()==10&&action.duration()==40,"Saved direct action lost identity or timing");
        edit(h,heart,table,"duplicate",0,"0");h.assertTrue(!table.moveSet().get(0).config().id().equals(table.moveSet().get(1).config().id()),"Duplicate move reused runtime identity");
        var player=FakePlayerFactory.getMinecraft(h.getLevel());player.setGameMode(GameType.CREATIVE);
        var data=heart.copyConfiguration();h.assertTrue(heart.pasteConfiguration(player,data),"Copier rejected new move set");
        var copiedArrow=heart.modules().all().stream().filter(m->m.type().equals("arrow")).findFirst().orElseThrow();
        var copiedTable=heart.modules().all().stream().filter(m->m.type().equals("move_set")).findFirst().orElseThrow();
        h.assertTrue(!copiedArrow.id().equals(arrow.id())&&copiedTable.moveSet().get(0).actions().getFirst().module().equals(copiedArrow.id()),"Copier retained source module UUID");
        h.assertTrue(copiedArrow.value("trigger")==4&&copiedArrow.anchors().isEmpty(),"Copier lost direct trigger or copied spatial anchors");
        h.assertTrue(copiedTable.moveSet().get(0).config().name().equals("Arrow move"),"Move rules/name lost during copy");h.succeed();
    }
}
