package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_blast_tests") @PrefixGameTestTemplate(false)
public final class MultiOriginGameTests {
 private static HeartBlockEntity core(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());c.tryActivate(TestActors.creative(l));return c;}
 private static Pig pig(GameTestHelper h,Vec3 p){var e=EntityType.PIG.create(h.getLevel());e.setPos(p);e.setNoAi(true);e.setNoGravity(true);h.getLevel().addFreshEntity(e);return e;}
 private static VirtualModule module(HeartBlockEntity c,String type){var m=c.modules().add(type);m.edit("trigger",0,"");m.edit("releasePosition",1,"");m.edit("castTargetRange",16,"");m.edit("target",1,"");m.edit("targetMultiple",1,"");m.edit("interval",1000,"");return m;}
 @GameTest(template="combat",batch="multi_sphere",timeoutTicks=25)
 public static void SphereBlastsAtBothSelectedCenters(GameTestHelper h){
  var c=core(h);var m=module(c,"sphere_blast");m.edit("blastRadius",1,"");m.edit("chargeTime",4,"");m.edit("damage",3,"");var o=TargetCastOrigin.searchOrigin(c,m);var a=pig(h,o.add(5,4,0));var b=pig(h,o.add(-6,4,0));
  h.assertTrue(TargetCastOrigin.points(c,m).size()==2,"Multiple sphere selection lost a target");m.edit("targetMultiple",0,"");h.assertTrue(TargetCastOrigin.points(c,m).size()==1,"Nearest-only selection not bounded");m.edit("targetMultiple",1,"");c.modules().tick(c);
  h.runAfterDelay(7,()->{h.assertTrue(a.getHealth()==7&&b.getHealth()==7,"Sphere did not cast both centers: "+a.getHealth()+"/"+b.getHealth());a.discard();b.discard();h.succeed();});
 }
 @GameTest(template="combat",batch="multi_pulse",timeoutTicks=20)
 public static void PulseUsesTargetCentersWithoutMarkAndSingleCooldown(GameTestHelper h){
  var c=core(h);var m=module(c,"pulse");m.edit("radius",1,"");m.edit("damage",3,"");m.edit("knockback",0,"");var o=TargetCastOrigin.searchOrigin(c,m);var a=pig(h,o.add(5,4,0));var b=pig(h,o.add(-6,4,0));
  h.assertTrue(HeartModules.status(m,c).equals("valid"),"Target pulse still requires Mark");c.modules().tick(c);
  h.runAfterDelay(4,()->{h.assertTrue(a.getHealth()==7&&b.getHealth()==7,"Target-centered pulse missed center target or repeated cooldown: "+a.getHealth()+"/"+b.getHealth());a.discard();b.discard();h.succeed();});
 }
 @GameTest(template="combat",batch="multi_pull",timeoutTicks=25)
 public static void AttractionFreezesMultipleCentersAndPullsTowardEach(GameTestHelper h){
  var c=core(h);var m=module(c,"pull");m.edit("pullWarning",5,"");m.edit("pullRadius",2,"");m.edit("pullStop",0,"");m.edit("pullStrength",.1,"");var o=TargetCastOrigin.searchOrigin(c,m);var a=pig(h,o.add(5,4,0));var b=pig(h,o.add(-6,4,0));c.modules().tick(c);
  h.runAfterDelay(2,()->{a.setPos(a.position().add(.8,0,0));b.setPos(b.position().add(.8,0,0));});
  h.runAfterDelay(7,()->{h.assertTrue(a.getDeltaMovement().x<-.01&&b.getDeltaMovement().x<-.01,"Attraction did not keep independent frozen centers: "+a.getDeltaMovement()+"/"+b.getDeltaMovement());a.discard();b.discard();h.succeed();});
 }
 @GameTest(template="combat",batch="multi_save")
 public static void AddedFieldsPersistAndDefaultToSingleMarkedCast(GameTestHelper h){
  for(var type:List.of("sphere_blast","pull","pulse")){var m=new VirtualModule(type);h.assertTrue(m.value("targetMultiple")==0&&m.value("releasePosition")==0,"Default behavior changed");h.assertTrue(m.edit("targetMultiple",1,"")&&m.edit("releasePosition",1,""),"Authoring fields missing");var copy=VirtualModule.load(m.save());h.assertTrue(copy.value("targetMultiple")==1&&TargetCastOrigin.targeted(copy),"Multi-origin save lost fields");}h.succeed();
 }
}
