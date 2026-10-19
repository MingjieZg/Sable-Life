package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.attack.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_blast_tests") @PrefixGameTestTemplate(false)
public final class ProjectileAimGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,6,4));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
    private static VirtualModule weapon(HeartBlockEntity core,String type){var m=core.modules().add(type);m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.UP)));m.edit("target",1,"");m.edit("targetRange",16,"");m.edit("shotAim",1,"");m.edit("interval",200,"");return m;}
    private static LivingEntity cow(GameTestHelper h,Vec3 center){var e=EntityType.COW.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setPos(center.add(0,-e.getBbHeight()/2,0));h.getLevel().addFreshEntity(e);return e;}
    @GameTest(template="combat",batch="aim_defaults")
    public static void DefaultStraightAndTargetSettingsPersist(GameTestHelper h){var core=fixture(h);for(String type:List.of("gun","arrow","slash")){var m=weapon(core,type);m.edit("shotAim",0,"");var origin=ModuleCombat.nozzle(core,m);h.assertTrue(ProjectileAim.directions(core,m,origin).equals(List.of(ModuleCombat.direction(core,m))),"Default straight direction changed");m.edit("shotAim",1,"");m.edit("targetMultiple",1,"");m.edit("targetRange",96,"");var saved=m.save();var loaded=VirtualModule.load(saved);h.assertTrue(loaded.value("shotAim")==1&&loaded.value("targetMultiple")==1&&loaded.value("targetRange")==96,"Target settings lost on reload");saved.getCompound("Settings").remove("shotAim");h.assertTrue(VirtualModule.load(saved).value("shotAim")==0,"Old blueprint changed to target mode");}h.succeed();}
    @GameTest(template="combat",batch="aim_filters")
    public static void NearestMultipleCategoryRangeAndVisibility(GameTestHelper h){var core=fixture(h);var m=weapon(core,"gun");var origin=ModuleCombat.nozzle(core,m);m.edit("targetRange",6,"");var near=cow(h,origin.add(2,0,0));var far=cow(h,origin.add(0,0,4));var outside=cow(h,origin.add(0,0,-20));
        h.assertTrue(ProjectileAim.directions(core,m,origin).size()==1&&ProjectileAim.directions(core,m,origin).getFirst().dot(new Vec3(1,0,0))>.999,"Nearest target was not selected");m.edit("targetMultiple",1,"");h.assertTrue(ProjectileAim.directions(core,m,origin).size()==2,"Multiple ignored range or failed to acquire both: "+ProjectileAim.directions(core,m,origin));m.edit("target",0,"");h.assertTrue(ProjectileAim.directions(core,m,origin).isEmpty(),"Mob matched players-only filter");m.edit("target",1,"");var wall=BlockPos.containing(origin.add(1,0,0));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());h.assertTrue(ProjectileAim.directions(core,m,origin).size()==1,"Target behind terrain was selected");h.getLevel().removeBlock(wall,false);near.discard();far.discard();outside.discard();h.succeed();}
    private static void burst(GameTestHelper h,String type,Class<? extends Entity> entityClass){var core=fixture(h);var m=weapon(core,type);m.edit("targetMultiple",1,"");m.edit("arrows",2,"");m.edit("endRadius",0,"");core.tryActivate(TestActors.creative(h.getLevel()));var combat=new ModuleCombat();combat.tick(core);var origin=ModuleCombat.nozzle(core,m);var a=cow(h,origin.add(5,0,0));var b=cow(h,origin.add(0,0,7));combat.tick(core);
        var shots=h.getLevel().getEntitiesOfClass(entityClass,new AABB(origin,origin).inflate(20),e->e.getPersistentData().hasUUID(AttackHitCredit.MODULE)&&e.getPersistentData().getUUID(AttackHitCredit.MODULE).equals(m.id()));
        h.assertTrue(shots.size()==4,"Expected full burst for each target after no-target wait: "+shots.size());int x=0,z=0;for(var shot:shots){var direction=shot.getDeltaMovement().normalize();if(direction.dot(new Vec3(1,0,0))>.999)x++;if(direction.dot(new Vec3(0,0,1))>.999)z++;h.assertTrue(shot.getPersistentData().getUUID("SableLifeAttackOwner").equals(core.creatureId()),"Lost attack ownership");shot.discard();}h.assertTrue(x==2&&z==2,"Burst directions did not aim at both sampled points");m.edit("enabled",0,"");a.discard();b.discard();h.succeed();}
    @GameTest(template="combat",batch="aim_gun") public static void GunEmitsFullBurstPerTarget(GameTestHelper h){burst(h,"gun",ModuleShot.class);}
    @GameTest(template="combat",batch="aim_arrow") public static void ArrowEmitsFullBurstPerTarget(GameTestHelper h){burst(h,"arrow",ModuleArrow.class);}
    @GameTest(template="combat",batch="aim_slash",timeoutTicks=25)
    public static void SlashAimedAtTwoPointsActuallyHitsBoth(GameTestHelper h){var core=fixture(h);var m=weapon(core,"slash");m.edit("targetMultiple",1,"");m.edit("slashSpeed",80,"");m.edit("damage",3,"");var origin=m.anchors().getFirst().world(core,false);var a=cow(h,origin.add(6,0,0));var b=cow(h,origin.add(0,0,8));core.tryActivate(TestActors.creative(h.getLevel()));core.modules().tick(core);h.runAfterDelay(3,()->{h.assertTrue(a.getHealth()<a.getMaxHealth()&&b.getHealth()<b.getMaxHealth(),"Target-point slashes failed to damage both targets: "+a.getHealth()+" / "+b.getHealth());m.edit("enabled",0,"");a.discard();b.discard();h.succeed();});}
}
