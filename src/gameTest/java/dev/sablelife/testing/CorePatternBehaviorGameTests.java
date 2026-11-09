package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Positive/negative spatial outcomes, using live creatures and the real heart ticker. */
@GameTestHolder("sablelife_core_pattern_behavior_tests")
@PrefixGameTestTemplate(false)
public final class CorePatternBehaviorGameTests {
    private record Fixture(HeartBlockEntity core,net.minecraft.server.level.ServerPlayer actor){}
    private static Fixture fixture(GameTestHelper h,int kind){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,40,4));
        level.setBlockAndUpdate(pos,(kind==0?SableLife.HEART.get():SableLife.SURVIVAL_LIFE_CORE.get()).defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var actor=TestActors.creative(level);actor.setPos(ModuleArena.corePosition(core));
        if(kind==2)core.depositSurvivalTokens(actor,new ItemStack(Items.COPPER_INGOT,10));
        core.tryActivate(actor);h.assertTrue(core.health().state()==HeartState.ALIVE,"Fixture did not activate");
        return new Fixture(core,actor);
    }
    private static Pig pig(GameTestHelper h,Vec3 center){
        var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setNoGravity(true);
        pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);pig.setHealth(100);
        pig.setPos(center.add(0,-pig.getBbHeight()/2,0));h.getLevel().addFreshEntity(pig);return pig;
    }
    private static VirtualModule module(Fixture f,String type){
        var m=f.core.modules().add(type);m.edit("trigger",0,"");m.anchors(List.of(ModuleAnchor.create(f.core,f.core.getBlockPos().east(),Direction.EAST)));return m;
    }
    private static void release(Fixture f,int kind,List<Pig> pigs){
        if(kind==2)f.core.resetForSurvival(f.actor);else f.core.resetForCreative(f.actor);
        pigs.forEach(Pig::discard);
    }
    @GameTestGenerator public static Collection<TestFunction> shapes(){
        var tests=new ArrayList<TestFunction>();
        for(int kind:new int[]{0,2})for(int shape=0;shape<2;shape++){
            int k=kind,s=shape;String flame="flame_"+k+"_"+s;
            tests.add(new TestFunction(flame,flame,"sablelife_core_pattern_behavior_tests:combat",60,0,true,h->flame(h,k,s)));
            for(int placement=0;placement<2;placement++){
                int p=placement;String freeze="freeze_"+k+"_"+s+"_"+p;
                tests.add(new TestFunction(freeze,freeze,"sablelife_core_pattern_behavior_tests:combat",60,0,true,h->freeze(h,k,s,p)));
            }
        }
        return tests;
    }
    private static void flame(GameTestHelper h,int kind,int shape){
        var f=fixture(h,kind);var m=module(f,"flame");m.edit("flameShape",shape,"");m.edit("flameFanAngle",120,"");
        m.edit("range",6,"");m.edit("width",.75,"");m.edit("flameSpeed",4,"");m.edit("damage",7,"");m.edit("burn",0,"");m.edit("interval",100,"");
        var origin=ModuleCombat.origin(f.core,m,true);
        var front=pig(h,origin.add(3,0,0));var side=pig(h,origin.add(2.5,0,3));var behind=pig(h,origin.add(-3,0,0));var pigs=List.of(front,side,behind);
        Runnable funded=()->{
            if(kind==2)f.core.depositSurvivalTokens(f.actor,new ItemStack(Items.NETHERITE_BLOCK));
            f.core.modules().clearRuntime();long before=f.core.survivalTokenBalance();f.core.modules().tick(f.core);
            h.runAfterDelay(8,()->{
                h.assertTrue(front.getHealth()<100,"Front target was missed");
                h.assertTrue(shape==1?side.getHealth()<100:side.getHealth()==100,"Cone/fan lateral coverage is wrong");
                h.assertTrue(behind.getHealth()==100,"Flame damaged behind its muzzle");
                if(kind==2)h.assertTrue(f.core.survivalTokenBalance()<before,"Funded flame did not consume energy");
                release(f,kind,pigs);h.succeed();
            });
        };
        if(kind==2){f.core.modules().tick(f.core);h.runAfterDelay(5,()->{h.assertTrue(pigs.stream().allMatch(p->p.getHealth()==100)&&f.core.survivalTokenBalance()==0,"Unfunded flame emitted damage");funded.run();});}
        else funded.run();
    }
    private static void freeze(GameTestHelper h,int kind,int shape,int placement){
        var f=fixture(h,kind);var m=module(f,"freeze");m.edit("freezeTarget",1,"");m.edit("fieldShape",shape,"");m.edit("fieldPlacement",placement,"");
        m.edit("freezeRadius",2,"");m.edit("freezeHeight",4,"");m.edit("fieldLength",8,"");m.edit("fieldWidth",2,"");m.edit("freezeWarning",5,"");m.edit("freezeDuration",20,"");m.edit("interval",400,"");
        var origin=ModuleCombat.origin(f.core,m,true);var inside=pig(h,origin.add(1,0,0));var outside=pig(h,origin.add(1,0,5));var above=pig(h,origin.add(1,4,0));var pigs=List.of(inside,outside,above);
        Runnable funded=()->{
            if(kind==2)f.core.depositSurvivalTokens(f.actor,new ItemStack(Items.NETHERITE_BLOCK));
            f.core.modules().clearRuntime();long before=f.core.survivalTokenBalance();f.core.modules().tick(f.core);
            h.runAfterDelay(8,()->{
                h.assertTrue(MobGrab.frozen(inside)&&!MobGrab.frozen(outside)&&!MobGrab.frozen(above),"Cylinder/box or height boundary is wrong");
                if(kind==2)h.assertTrue(f.core.survivalTokenBalance()<before,"Funded freeze did not consume energy");
                m.edit("enabled",0,"");f.core.modules().tick(f.core);
                h.assertTrue(!MobGrab.frozen(inside),"Disabled freeze retained control");
                release(f,kind,pigs);h.succeed();
            });
        };
        if(kind==2){f.core.modules().tick(f.core);h.runAfterDelay(8,()->{h.assertTrue(pigs.stream().noneMatch(MobGrab::frozen)&&f.core.survivalTokenBalance()==0,"Unfunded freeze captured a target");funded.run();});}
        else funded.run();
    }
}
