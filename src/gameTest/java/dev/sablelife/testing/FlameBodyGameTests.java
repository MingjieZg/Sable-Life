package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.combat.BodySphereQuery;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class FlameBodyGameTests {
 private static HeartBlockEntity body(GameTestHelper h,BlockPos pos,int length){
  var level=h.getLevel();var blocks=new ArrayList<BlockPos>();for(int i=0;i<length;i++){var b=pos.west(i);level.setBlockAndUpdate(b,i==0?SableLife.HEART.get().defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());blocks.add(b);}
  var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,blocks,new BoundingBox3i(pos.getX()-length+1,pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ()));
  var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(100,60);return core;
 }
 @GameTest(template="combat",batch="flame_body_surface",timeoutTicks=30)
 public static void SurfaceAwayFromBodyCenterReceivesOneHitPerInterval(GameTestHelper h){
  var pos=h.absolutePos(new BlockPos(4,40,4));var attacker=body(h,pos,1);var victim=body(h,pos.east(6).south(3),7);
  var flame=attacker.modules().add("flame");flame.anchors(List.of(ModuleAnchor.create(attacker,attacker.getBlockPos(),Direction.SOUTH)));flame.edit("trigger",0,"");flame.edit("flameSpeed",4,"");flame.edit("damage",7,"");flame.edit("burn",0,"");flame.edit("interval",20,"");
  var counter=attacker.modules().add("hit_counter");counter.edit("hitThreshold",100,"");var actor=TestActors.creative(h.getLevel());victim.tryActivate(actor);attacker.tryActivate(actor);
  h.runAfterDelay(6,()->h.assertTrue(victim.health().current()==93&&attacker.health().current()==100,"Flame missed off-center real body surface, duplicated hit or hurt itself"));
  h.runAfterDelay(12,()->{h.assertTrue(victim.health().current()==93,"Continuous flame ignored per-creature interval");h.assertTrue(attacker.modules().hitCounter.snapshot(counter.id()).getInt("Count")==1,"Native flame body damage missed/duplicated hit credit");attacker.resetForCreative(actor);victim.resetForCreative(actor);h.succeed();});
 }
 @GameTest(template="combat",batch="flame_body_occlusion",timeoutTicks=30)
 public static void SweptBodyContactsRejectHollowSpaceTerrainAndDisabledLifeAttacks(GameTestHelper h){
  var pos=h.absolutePos(new BlockPos(4,40,4));var victim=body(h,pos,1);var actor=TestActors.creative(h.getLevel());victim.tryActivate(actor);
  h.runAfterDelay(3,()->{var level=h.getLevel();var center=SableBodyProbe.toWorld(level,victim.getBlockPos().getCenter());var from=center.add(0,0,-2);var to=center.add(0,0,-.5);var attacker=UUID.randomUUID();
   h.assertTrue(BodySphereQuery.querySweep(level,attacker,from,to,.25).size()==1,"Sweep missed body surface");
   h.assertTrue(BodySphereQuery.querySweep(level,victim.creatureId(),from,to,.25).isEmpty(),"Sweep accepted self attack");
   h.assertTrue(BodySphereQuery.querySweep(level,attacker,from.add(2,0,0),to.add(2,0,0),.25).isEmpty(),"Sweep accepted empty space");
   var wall=BlockPos.containing(center.add(0,0,-1));level.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());h.assertTrue(BodySphereQuery.querySweep(level,attacker,from,to,.25).isEmpty(),"Flame contacted through terrain");level.setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());
   h.assertTrue(victim.editCore(actor,"lifeAttacks",0,"")&&BodySphereQuery.querySweep(level,attacker,from,to,.25).isEmpty(),"Life attack opt-out bypassed");h.succeed();});
 }
}
