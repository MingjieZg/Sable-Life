package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_module_policy_tests")
@PrefixGameTestTemplate(false)
public final class ModuleAvailabilityGameTests {
    private static ModConfigSpec.BooleanValue setting(boolean redstone,String type){
        return (ModConfigSpec.BooleanValue)SableLifeServerConfig.SPEC.getValues().get(List.of("modules",redstone?"redstoneCore":"heartCore",type));
    }
    private static HeartBlockEntity core(GameTestHelper h,boolean redstone,int x){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(x,6,3));
        level.setBlockAndUpdate(pos,(redstone?SableLife.REDSTONE_CORE.get():SableLife.HEART.get()).defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static boolean offered(HeartBlockEntity core,String type){return core.modules().snapshot(core,true).getList("AvailableTypes",8).stream().anyMatch(t->t.getAsString().equals(type));}
    @GameTest(template="combat",batch="block_interaction",timeoutTicks=30)
    public static void BlockInteractionUsesOneNearestBlockAndRepeatsOnlyOnSchedule(GameTestHelper h){
        var core=core(h,true,3);
        var mark=core.getBlockPos().east();
        var anchor=ModuleAnchor.create(core,mark,Direction.NORTH);
        var worldMark=BlockPos.containing(anchor.world(core,false));
        var point=worldMark.north(3);
        var near=point.east();
        var far=near.above();
        var level=h.getLevel();
        level.setBlockAndUpdate(worldMark.below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(worldMark.west().below(),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(point,Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(near,Blocks.OAK_TRAPDOOR.defaultBlockState());
        level.setBlockAndUpdate(far,Blocks.OAK_TRAPDOOR.defaultBlockState());
        var module=core.modules().add("block_interaction");
        module.anchors(List.of(anchor));
        module.edit("interactionSize",3,"");
        module.edit("startDistance",3,"");
        var open=net.minecraft.world.level.block.TrapDoorBlock.OPEN;
        core.modules().tick(core);
        h.assertTrue(level.getBlockState(near).getValue(open)&&!level.getBlockState(far).getValue(open),"Closest trapdoor was not the only target");
        h.runAfterDelay(3,()->{
            h.assertTrue(level.getBlockState(near).getValue(open),"One-shot interaction repeated while held");
            module.edit("interactionMode",1,"");
            module.edit("interactionInterval",2,"");
            core.modules().clearRuntime();
            core.modules().tick(core);
            h.assertTrue(!level.getBlockState(near).getValue(open),"Repeat did not interact on activation");
        });
        h.runAfterDelay(5,()->{
            h.assertTrue(level.getBlockState(near).getValue(open)&&!level.getBlockState(far).getValue(open),"Repeat timing or nearest target changed");
            h.succeed();
        });
    }
    @GameTest(template="combat",batch="block_interaction_offset")
    public static void InteractionOffsetsMoveTheSearchAndSelectOnlyTheNearestBlock(GameTestHelper h){
        var core=core(h,true,3);
        var level=h.getLevel();
        var mark=core.getBlockPos().east();
        var anchor=ModuleAnchor.create(core,mark,Direction.EAST);
        var worldMark=BlockPos.containing(anchor.world(core,false));
        var target=worldMark.east(3).above();
        var original=worldMark.above();
        level.setBlockAndUpdate(target,Blocks.OAK_TRAPDOOR.defaultBlockState());
        level.setBlockAndUpdate(original,Blocks.OAK_TRAPDOOR.defaultBlockState());
        var module=core.modules().add("block_interaction");
        module.anchors(List.of(anchor));
        h.assertTrue(module.value("interactionSize")==1,"Interaction box should default to one block");
        module.edit("startDistance",3,"");
        module.edit("interactionOffsetY",1,"");
        core.modules().tick(core);
        var open=net.minecraft.world.level.block.TrapDoorBlock.OPEN;
        h.assertTrue(level.getBlockState(target).getValue(open)&&!level.getBlockState(original).getValue(open),
                "Interaction stayed at the mark instead of moving the search box");
        h.succeed();
    }

    @GameTest(template="combat",batch="block_interaction_structure")
    public static void StructureInteractionStaysOnTheMarkedPart(GameTestHelper h){
        var core=core(h,true,3);
        var level=h.getLevel();
        var part=core.getBlockPos().east();
        level.setBlockAndUpdate(part,Blocks.OAK_TRAPDOOR.defaultBlockState());
        var anchor=ModuleAnchor.create(core,part,Direction.UP);
        var world=BlockPos.containing(anchor.world(core,false));
        level.setBlockAndUpdate(world,Blocks.OAK_TRAPDOOR.defaultBlockState());
        var module=core.modules().add("block_interaction");
        module.anchors(List.of(anchor));
        module.edit("interactionSpace",1,"");
        core.modules().tick(core);
        var open=net.minecraft.world.level.block.TrapDoorBlock.OPEN;
        h.assertTrue(level.getBlockState(part).getValue(open)&&!level.getBlockState(world).getValue(open),
                "Structure mode operated the overlapping world block");
        module.edit("interactionSpace",0,"");
        core.modules().clearRuntime();
        core.modules().tick(core);
        h.assertTrue(level.getBlockState(part).getValue(open)&&level.getBlockState(world).getValue(open),
                "World mode did not operate the world block independently");
        h.succeed();
    }

    @GameTest(template="combat",batch="block_interaction_protected_structure")
    public static void OwnInteractionModuleWorksInsideAProtectedLivingStructure(GameTestHelper h){
        var core=core(h,false,3);
        var level=h.getLevel();
        var player=TestActors.creative(level);
        var part=core.getBlockPos().east();
        level.setBlockAndUpdate(part,Blocks.OAK_TRAPDOOR.defaultBlockState());
        var module=core.modules().add("block_interaction");
        module.anchors(List.of(ModuleAnchor.create(core,part,Direction.UP)));
        module.edit("interactionSpace",1,"");
        core.tryActivate(player);
        h.assertTrue(!core.allowSurvivalInteraction(),"Fixture should deny external survival clicks");
        core.modules().tick(core);
        h.assertTrue(level.getBlockState(part).getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN),
                "Core's own module was blocked by its external interaction policy");
        var actor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"OtherActor"));
        h.assertTrue(!dev.sablelife.ownership.BodyInteraction.allowed(actor,part),"Module bypass leaked after interaction");
        h.succeed();
    }

    @GameTest(template="combat",batch="laser_vertical_sweep")
    public static void VerticalLaserSweepFollowsTheMarkedPartAxes(GameTestHelper h){
        var body=new org.joml.Quaterniond();
        var forward=new net.minecraft.world.phys.Vec3(0,0,-1);
        var start=ModuleLaser.sweep(forward,body,0,90,0);
        var end=ModuleLaser.sweep(forward,body,0,90,1);
        h.assertTrue(start.y<-.7&&end.y>.7,"Vertical sweep did not span both sides of center");
        var combined=ModuleLaser.sweep(forward,body,90,90,1);
        h.assertTrue(Math.abs(combined.x)>.49&&combined.y>.7,"Combined sweep lost either yaw or pitch");
        h.assertTrue(Double.isFinite(ModuleLaser.sweep(new net.minecraft.world.phys.Vec3(0,1,0),body,90,90,1).lengthSqr()),
                "A vertical mark produced a degenerate sweep axis");
        h.succeed();
    }

    @GameTest(template="combat",batch="redstone_safe_travel")
    public static void RedstoneMoveSetRunsOnlySafeTravel(GameTestHelper h){
        var core=core(h,true,3);var author=TestActors.creative(h.getLevel());
        author.setPos(ModuleArena.corePosition(core));
        var menu=new HeartDashboardMenu(1,author.getInventory(),core);
        var option=setting(true,"linear_movement");boolean previous=option.get();
        try{
            h.assertTrue(previous&&offered(core,"linear_movement"),"Travel was unavailable by default");
            var preset=new VirtualModule("linear_movement");
            preset.edit("moveForce",1,"");preset.edit("moveObstruction",1,"");
            h.assertTrue(menu.action(author,core.modules().revision(),preset.id(),"add_preset","linear_movement",0,ModulePreset.portable(preset.save()).toString()),"Travel preset was rejected");
            var travel=core.modules().all().getFirst();
            h.assertTrue(travel.value("linearMode")==1&&travel.value("moveForce")==0&&travel.value("moveObstruction")==0,"Preset bypassed safe Travel");
            h.assertTrue(!menu.action(author,core.modules().revision(),travel.id(),"edit","moveForce",1,""),"Editor unlocked forced travel");
            travel.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.NORTH)));
            travel.edit("chargeTime",0,"");travel.edit("travelSpeed",10,"");
            var table=core.modules().add("move_set");
            table.moveSet().edit("add",0,"",core.modules());
            table.moveSet().edit("action_add",0,"0|"+travel.id(),core.modules());
            var origin=ModuleArena.corePosition(core);
            core.modules().tick(core);
            double moved=origin.distanceTo(ModuleArena.corePosition(core));
            h.assertTrue(moved>.1&&moved<.6&&core.health().state()==HeartState.DORMANT,"Move Set failed to drive a continuous step: "+moved);
            option.set(false);
            h.assertTrue(!HeartModules.triggered(travel,core),"Travel stayed active after its server switch was disabled");
            h.succeed();
        }finally{option.set(previous);}
    }
    @GameTest(template="combat",batch="server_module_policy")
    public static void OptionalPresentationDefaultsOffAndSurvivalEditorsRunWhenEnabled(GameTestHelper h){
        var core=core(h,true,3);var author=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"OptionalControlAuthor"));
        author.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);author.setPos(SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter()));
        var menu=new HeartDashboardMenu(1,author.getInventory(),core);var previous=new HashMap<String,Boolean>();
        try{
            h.assertTrue(RedstoneCorePolicy.TYPES.size()==17,"Expected seventeen supported modules");
            for(String type:RedstoneCorePolicy.OPTIONAL_TYPES){
                var option=setting(true,type);previous.put(type,option.get());
                h.assertTrue(option.get()&&offered(core,type),"Presentation module disabled by default: "+type);
                option.set(false);
                h.assertTrue(!menu.action(author,core.modules().revision(),new UUID(0,0),"add",type,0,""),"False option accepted add");
                option.set(true);
                h.assertTrue(offered(core,type)&&menu.action(author,core.modules().revision(),new UUID(0,0),"add",type,0,""),"True option unavailable: "+type);
                var m=core.modules().all().getLast();m.edit("trigger",0,"");var a=ModuleAnchor.create(core,core.getBlockPos(),Direction.NORTH);m.anchors(m.area()?List.of(a,a):List.of(a));
                h.assertTrue(HeartModules.triggered(m,core),"Optional type required Life activation: "+type);
                if(type.equals("particles")){
                    h.assertTrue(menu.action(author,core.modules().revision(),m.id(),"effects","particles",0,""),"Particle editor failed for survival");
                    var editor=new EffectSelectionMenu(1,author.getInventory(),core,m,true);
                    h.assertTrue(editor.stillValid(author)&&editor.action(author,m.id(),"add",0,"minecraft:flame"),"Survival particle selection failed");
                }
                if(type.equals("item_display")){
                    h.assertTrue(menu.action(author,core.modules().revision(),m.id(),"projectile","",0,""),"Item editor unavailable");
                    m.projectileItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DISPENSER),h.getLevel().registryAccess());
                }
                if(type.equals("trigger_sound")){
                    h.assertTrue(menu.action(author,core.modules().revision(),m.id(),"sound_editor","triggerSound",0,""),"Sound editor unavailable");
                    h.assertTrue(m.edit("triggerSound",0,"minecraft:entity.ravager.roar"),"Could not configure optional trigger sound");
                }
            }
            core.modules().tick(core);
            h.assertTrue(core.modules().events().emittedSounds()==1,"Optional trigger sound did not run");
            var display=core.modules().all().stream().filter(m->m.type().equals("item_display")).findFirst().orElseThrow();
            h.assertTrue(ModuleItemDisplay.showing(core,display),"Optional item display hidden while enabled");
            var saved=core.modules().save().getList("Modules",10).copy();
            for(String type:previous.keySet())setting(true,type).set(false);
            core.modules().tick(core);
            h.assertTrue(!ModuleItemDisplay.showing(core,display)&&core.modules().all().stream().noneMatch(m->HeartModules.triggered(m,core)),"False left presentation active");
            h.assertTrue(saved.equals(core.modules().save().getList("Modules",10)),"Disabling erased presentation configuration");
            h.assertTrue(!ModuleAvailability.enabled(true,"sound")&&!ModuleAvailability.enabled(true,"bgm")&&ModuleAvailability.enabled(true,"move_set"),"Unrequested capabilities opened");
            h.succeed();
        }finally{previous.forEach((type,value)->setting(true,type).set(value));}
    }
    @GameTest(template="combat",batch="server_module_policy",timeoutTicks=40)
    public static void SwitchesHideRejectSuspendRetainAndRestoreWithoutCrossCoreGrants(GameTestHelper h){
        var life=core(h,false,3);var control=core(h,true,11);var author=TestActors.creative(h.getLevel());
        var lifeSwitch=setting(false,"signal_logic");var controlSwitch=setting(true,"signal_logic");boolean previousLife=lifeSwitch.get(),previousControl=controlSwitch.get();
        Runnable restore=()->{lifeSwitch.set(previousLife);controlSwitch.set(previousControl);};
        try{
            lifeSwitch.set(true);controlSwitch.set(true);
            h.assertTrue(ModuleAvailability.available(false).stream().filter(type -> type.indexOf(':') < 0).toList().equals(VirtualModule.AVAILABLE_TYPES)
                    && ModuleAvailability.available(true).stream().filter(type -> type.indexOf(':') < 0).toList().equals(RedstoneCorePolicy.TYPES), "Default built-in module tables differ");
            h.assertTrue(!ModuleAvailability.enabled(true,"health_phase")&&setting(true,"health_phase")==null,"Redstone config granted life capability");
            for(var c:List.of(life,control)){
                author.setPos(SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));
                var menu=new HeartDashboardMenu(1,author.getInventory(),c);
                h.assertTrue(menu.action(author,c.modules().revision(),new UUID(0,0),"add","signal_logic",0,""),"True rejected supported module");
            }
            var lifeModule=life.modules().all().getFirst();var module=control.modules().all().getFirst();
            lifeSwitch.set(false);
            h.assertTrue(!offered(life,"signal_logic")&&offered(control,"signal_logic"),"Per-core switch leaked or client catalog ignored server");
            h.assertTrue(HeartModules.status(lifeModule,life).equals("server_disabled")&&lifeModule.enabled(),"Disabled module lost authored enable state");
            lifeSwitch.set(true);
            h.assertTrue(offered(life,"signal_logic"),"True failed to restore catalog");
            int lifeInput=life.wireless().add(),lifeOutput=life.wireless().add();
            for(int i:List.of(lifeInput,lifeOutput)){life.wireless().channel(i).set(0,1);life.wireless().channel(i).set(6,3);}
            lifeModule.signalRules().rows().getFirst().edit("signalInput",lifeInput+1);lifeModule.edit("logicChannel",lifeOutput+1,"");
            life.tryActivate(author);life.wireless().programOutputs("test",Map.of(lifeInput,15));life.modules().tick(life);
            h.assertTrue(life.wireless().output(lifeOutput)==15,"Life Core true failed to run");
            var savedLife=lifeModule.save();lifeSwitch.set(false);life.modules().tick(life);
            h.assertTrue(life.wireless().output(lifeOutput)==0&&!HeartModules.triggered(lifeModule,life)&&savedLife.equals(lifeModule.save()),"Life Core false retained output or lost settings");
            lifeSwitch.set(true);
            int input=control.wireless().add(),output=control.wireless().add();
            for(int i:List.of(input,output)){control.wireless().channel(i).set(0,1);control.wireless().channel(i).set(6,3);}
            module.signalRules().rows().getFirst().edit("signalInput",input+1);module.edit("logicChannel",output+1,"");module.edit("logicPower",9,"");
            var saved=module.save();control.wireless().programOutputs("test",Map.of(input,15));control.modules().tick(control);
            h.assertTrue(control.wireless().output(output)==9,"True failed to run existing module");
            controlSwitch.set(false);control.modules().tick(control);
            h.assertTrue(control.wireless().output(output)==0&&!HeartModules.triggered(module,control),"False retained output/trigger");
            h.assertTrue(saved.equals(module.save())&&control.modules().find(module.id())==module,"False deleted or rewrote module settings");
            h.assertTrue(!offered(control,"signal_logic")&&HeartModules.status(module,control).equals("server_disabled"),"Disabled status/catalog not synchronized");
            author.setPos(SableBodyProbe.toWorld(h.getLevel(),control.getBlockPos().getCenter()));var menu=new HeartDashboardMenu(1,author.getInventory(),control);
            h.assertTrue(!menu.action(author,control.modules().revision(),new UUID(0,0),"add","signal_logic",0,""),"Forged add bypassed server");
            h.assertTrue(!menu.action(author,control.modules().revision(),module.id(),"copy","",0,""),"Copy bypassed server");
            h.assertTrue(!menu.action(author,control.modules().revision(),new UUID(0,0),"add_preset","signal_logic",0,ModulePreset.portable(saved).toString()),"Preset bypassed server");
            var editor=new SignalRuleMenu(1,author.getInventory(),control,module,null);
            h.assertTrue(!editor.action(author,control.modules().revision(),new UUID(0,0),"add",0),"Open editor bypassed disabled policy");
            var stored=new HeartModules();stored.load(control.modules().save());h.assertTrue(stored.find(module.id()).save().equals(saved),"Save/load lost disabled configuration");
            controlSwitch.set(true);
            h.runAfterDelay(2,()->{
                try{life.modules().tick(life);h.assertTrue(life.wireless().output(lifeOutput)==15,"Life Core re-enable failed");control.modules().tick(control);h.assertTrue(offered(control,"signal_logic")&&control.wireless().output(output)==9&&HeartModules.triggered(module,control),"Re-enable did not resume saved module");h.succeed();}
                finally{restore.run();}
            });
        }catch(Throwable failure){restore.run();throw failure;}
    }
    @GameTest(template="combat",batch="survival_heart_core_editing")
    public static void ServerSwitchGrantsAndRevokesSurvivalLifeCoreEditing(GameTestHelper h) {
        var setting = (ModConfigSpec.BooleanValue) SableLifeServerConfig.SPEC.getValues().get("allowEditHeartCoreUnderSurvivalMode");
        boolean previous = setting.get();
        try {
            var core = core(h, false, 3);
            var player = TestActors.creative(h.getLevel());
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setPos(SableBodyProbe.toWorld(h.getLevel(), core.getBlockPos().getCenter()));
            var dashboard = new HeartDashboardMenu(1, player.getInventory(), core);
            var settings = new HeartSettingsMenu(2, player.getInventory(), core);
            var loot = new HeartLootMenu(3, player.getInventory(), core);
            var lootModule = core.modules().add("loot");
            var partLoot = new VirtualLootMenu(4, player.getInventory(), core, lootModule);
            var wireless = new HeartWirelessMenu(5, player.getInventory(), core, 0);
            var sample = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD);
            setting.set(false);
            h.assertTrue(!dashboard.stillValid(player) && !settings.action(player,"xp",100,"") && !core.setDeathTemplate(player,0,sample), "Disabled switch allowed survival edits");
            setting.set(true);
            h.assertTrue(dashboard.stillValid(player) && settings.stillValid(player) && loot.stillValid(player) && partLoot.stillValid(player) && wireless.stillValid(player), "Enabled switch did not open all editor paths");
            h.assertTrue(settings.action(player,"xp",100,"") && core.deathExperience()==100, "Survival XP edit failed");
            h.assertTrue(core.setDeathTemplate(player,0,sample) && core.lootTemplates().getFirst().is(net.minecraft.world.item.Items.EMERALD), "Survival loot edit failed");
            h.assertTrue(settings.action(player,"crushEnabled",1,"") && core.terrainSettings().enabled(), "Survival terrain setting failed");
            h.assertTrue(dashboard.action(player,core.modules().revision(),new UUID(0,0),"add","laser",0,""), "Survival attack module edit failed");
            h.assertTrue(!dashboard.action(player,core.modules().revision(),new UUID(0,0),"add","command",0,""), "Switch granted command authoring permission");
            core.tryActivate(player);
            h.assertTrue(core.health().state()==HeartState.ALIVE && dashboard.stillValid(player) && !core.canEdit(player), "Active core editor bypassed the state restriction");
            setting.set(false);
            h.assertTrue(!dashboard.stillValid(player) && !settings.stillValid(player) && !loot.stillValid(player) && !partLoot.stillValid(player) && !wireless.stillValid(player), "Open menus retained access after revocation");
            h.assertTrue(core.deathExperience()==100 && core.terrainSettings().enabled(), "Revocation erased authored settings");
            h.succeed();
        } finally { setting.set(previous); }
    }
}
