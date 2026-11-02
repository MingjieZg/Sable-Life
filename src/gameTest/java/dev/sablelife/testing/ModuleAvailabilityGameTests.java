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
