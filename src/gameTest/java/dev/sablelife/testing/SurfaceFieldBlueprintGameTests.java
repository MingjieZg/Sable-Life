package dev.sablelife.testing;

import dev.sablelife.compat.photomancy.PhotomancyRespawnBridge;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.rew1nd.sableschematicapi.blueprint.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_surface_tests") @PrefixGameTestTemplate(false)
public final class SurfaceFieldBlueprintGameTests {
 @GameTest(template="combat",batch="surface_blueprint_references",timeoutTicks=100)
 public static void NativeExportAndRepeatedPlacementKeepLocalImpactTemplates(GameTestHelper h){
  var source=SlashGuardGameTests.core(h);var first=source.modules().add("surface_field");first.edit("name",0,"before");
  var attacks=new ArrayList<VirtualModule>();
  for(String type:List.of("mortar","flame","gun","arrow","slash","laser","ground_strike","homing")){
   var m=source.modules().add(type);m.anchors(List.of(ModuleAnchor.create(source,source.getBlockPos(),Direction.SOUTH)));attacks.add(m);
  }
  var last=source.modules().add("surface_field");last.edit("name",0,"after");last.edit("fieldShape",1,"");last.edit("fieldLength",5,"");last.edit("fieldWidth",.5,"");last.edit("fieldAngle",90,"");
  for(int i=0;i<attacks.size();i++)attacks.get(i).impactField(i%2==0?first.id():last.id());
  source.modules().add("gun"); // No reference must remain unconfigured.
  var center=ModuleArena.corePosition(source);var template=PhotomancyRespawnBridge.capture(source,center);
  var a=place(h,template.copy(),center.add(20,0,0));var b=place(h,template.copy(),center.add(40,0,0));
  verify(h,source,a);verify(h,source,b);verify(h,a,b);
  var recaptured=PhotomancyRespawnBridge.capture(a,ModuleArena.corePosition(a));var c=place(h,recaptured,center.add(60,0,0));verify(h,a,c);
  for(int i=0;i<attacks.size();i++)h.assertTrue(attacks.get(i).impactFieldId().equals(i%2==0?first.id():last.id()),"Export or placement mutated source configuration");
  // Resolve the restored reference through the actual impact entry point, not just NBT comparison.
  var ground=h.absolutePos(new BlockPos(8,4,8));for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)h.getLevel().setBlockAndUpdate(ground.offset(x,0,z),Blocks.STONE.defaultBlockState());
  c.tryActivate(TestActors.creative(h.getLevel()));h.assertTrue(c.operational(),"Placed body cannot activate");
  var attack=c.modules().all().stream().filter(m->m.type().equals("slash")).findFirst().orElseThrow();
  SurfaceFieldImpact.block(c,attack,new BlockHitResult(ground.getCenter().add(0,.5,0),Direction.UP,ground,false),new Vec3(0,-1,0));
  h.assertTrue(c.modules().surfaceFields.count()==1,"Restored impact reference did not spawn an area");
  for(var core:List.of(source,a,b,c))core.modules().all().forEach(m->m.edit("enabled",0,""));h.succeed();
 }
 private static HeartBlockEntity place(GameTestHelper h,net.minecraft.nbt.CompoundTag template,Vec3 center){
  var decoded=SableBlueprint.loadWithDiagnostics(template);h.assertTrue(decoded.diagnostics().isEmpty(),"Saved blueprint did not decode cleanly");
  var result=SableBlueprintPlacer.place(h.getLevel(),decoded.blueprint(),center);h.assertTrue(result.diagnostics().isEmpty()&&result.placedSubLevels()==1,"Native schematic placement failed");
  var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel());var body=container.getSubLevel(result.subLevelUuidMap().values().iterator().next());
  return (HeartBlockEntity)h.getLevel().getBlockEntity(body.getPlot().getCenterBlock());
 }
 private static void verify(GameTestHelper h,HeartBlockEntity from,HeartBlockEntity placed){
  var old=from.modules().all();var fresh=placed.modules().all();h.assertTrue(old.size()==fresh.size(),"Placement lost modules");
  var priorIds=old.stream().map(VirtualModule::id).collect(java.util.stream.Collectors.toSet());
  for(int i=0;i<old.size();i++){
   var original=old.get(i);var mapped=fresh.get(i);h.assertTrue(!priorIds.contains(mapped.id()),"Placement reused another creature's module ID");
   if(original.impactFieldId()==null){h.assertTrue(mapped.impactFieldId()==null,"Placement invented an impact reference");continue;}
   int target=-1;for(int j=0;j<old.size();j++)if(old.get(j).id().equals(original.impactFieldId()))target=j;
   h.assertTrue(target>=0&&mapped.impactFieldId()!=null&&mapped.impactFieldId().equals(fresh.get(target).id()),"Impact template reference is stale: "+mapped.type());
   var field=placed.modules().find(mapped.impactFieldId());h.assertTrue(field!=null&&field.type().equals("surface_field"),"Restored template shows deleted");
   h.assertTrue(field.value("fieldLength")==old.get(target).value("fieldLength")&&field.value("fieldWidth")==old.get(target).value("fieldWidth")&&field.value("fieldAngle")==old.get(target).value("fieldAngle"),"Placement changed area shape settings");
  }
 }
}
