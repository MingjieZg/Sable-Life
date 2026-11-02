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
        return (ModConfigSpec.BooleanValue)SableLifeServerConfig.SPEC.getValues().get(List.of("modules",redstone?"redstoneCore":"lifeCore",type));
    }
    private static HeartBlockEntity core(GameTestHelper h,boolean redstone,int x){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(x,6,3));
        level.setBlockAndUpdate(pos,(redstone?SableLife.REDSTONE_CORE.get():SableLife.HEART.get()).defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static boolean offered(HeartBlockEntity core,String type){return core.modules().snapshot(core,true).getList("AvailableTypes",8).stream().anyMatch(t->t.getAsString().equals(type));}
    @GameTest(template="combat",batch="server_module_policy")
    public static void OptionalPresentationDefaultsOffAndSurvivalEditorsRunWhenEnabled(GameTestHelper h){
        var core=core(h,true,3);var author=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"OptionalControlAuthor"));
        author.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);author.setPos(SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter()));
        var menu=new HeartDashboardMenu(1,author.getInventory(),core);var previous=new HashMap<String,Boolean>();
        try{
            h.assertTrue(RedstoneCorePolicy.TYPES.size()==14,"Expected nine control and five optional presentation types");
            for(String type:RedstoneCorePolicy.OPTIONAL_TYPES){
                var option=setting(true,type);previous.put(type,option.get());
                h.assertTrue(!option.get()&&!offered(core,type),"Optional presentation enabled by default: "+type);
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
            h.assertTrue(!ModuleAvailability.enabled(true,"sound")&&!ModuleAvailability.enabled(true,"bgm")&&!ModuleAvailability.enabled(true,"move_set"),"Unrequested capabilities opened");
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
            h.assertTrue(ModuleAvailability.available(false).size()==44&&ModuleAvailability.available(true).size()==9,"Default module tables differ");
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
}
