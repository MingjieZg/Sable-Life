package dev.sablelife.testing;
import dev.sablelife.ownership.*;
import dev.sablelife.compat.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import java.util.*;
@GameTestHolder("sablelife_mounted_cleanup_tests") @PrefixGameTestTemplate(false)
public final class MountedCleanupGameTests {
 private static void field(Object target,String name,Object value)throws Exception{for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private static Block block(String registry,String name)throws Exception{var entry=Class.forName(registry).getField(name).get(null);return (Block)entry.getClass().getMethod("get").invoke(entry);}
 private static BlockEntity controller(GameTestHelper h,Block block,boolean plot){var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,15,5));l.setBlockAndUpdate(p,block.defaultBlockState());if(plot){var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));p=body.getPlot().getCenterBlock();}return l.getBlockEntity(p);}
 private static Contraption payload(String name,BlockPos p)throws Exception{var c=(Contraption)Class.forName(name).getConstructor().newInstance();c.anchor=p.above(2);c.bounds=new AABB(0,0,0,1,1,1);c.getBlocks().put(BlockPos.ZERO,new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(BlockPos.ZERO,Blocks.GLASS.defaultBlockState(),null));return c;}
 private static AbstractContraptionEntity cannon(GameTestHelper h,BlockEntity mount,String type)throws Exception{var c=payload(type,mount.getBlockPos());field(c,"initialOrientation",Direction.NORTH);field(c,"startPos",BlockPos.ZERO);var entityClass=Class.forName("rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity");var controllerClass=Class.forName("rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption$Block");var entity=(AbstractContraptionEntity)entityClass.getMethod("create",Level.class,Contraption.class,Direction.class,controllerClass).invoke(null,h.getLevel(),c,Direction.NORTH,mount);entity.setPos(mount.getBlockPos().above(2).getCenter());mount.getClass().getMethod("attach",entityClass).invoke(mount,entity);return entity;}
 private static ControlledContraptionEntity radar(GameTestHelper h,MechanicalBearingBlockEntity bearing)throws Exception{var c=payload("com.happysg.radar.block.radar.bearing.RadarContraption",bearing.getBlockPos());field(c,"facing",Direction.UP);var entity=ControlledContraptionEntity.create(h.getLevel(),bearing,c);entity.setPos(bearing.getBlockPos().above(2).getCenter());bearing.attach(entity);return entity;}
 private static void death(GameTestHelper h,BlockEntity be,boolean whole){var l=h.getLevel();var owner=UUID.randomUUID();var part=UUID.randomUUID();var data=BodyOwnership.get(l);data.bind(be.getBlockPos(),owner);data.bindPart(be.getBlockPos(),part);h.assertTrue(LifeLifecycle.get(l.getServer()).schedulePart(l,part,owner,"test",be.getBlockPos().getCenter(),0,net.minecraft.resources.ResourceLocation.withDefaultNamespace("empty"),0),"No registered death job");var result=data.cleanBudgeted(l,part,256,16384,true,null,whole);h.assertTrue(result.removed()>0,"Owned controller not cleaned");}
 private static void cbcDeath(GameTestHelper h,String cls)throws Exception{
  var be=controller(h,block("rbasamoyai.createbigcannons.index.CBCBlocks","CANNON_MOUNT"),true);var entity=cannon(h,be,cls);
  h.assertTrue(be.getClass().getMethod("getContraption").invoke(be)==entity,"Native cannon attach failed");
  death(h,be,true);h.assertTrue(entity.isRemoved()&&!entity.getContraption().disassembled,"Mounted cannon removed="+entity.isRemoved()+" restored="+entity.getContraption().disassembled+": "+cls);h.succeed();
 }
 @GameTest(template="combat",batch="mount_cbc") public static void CbcCannonIsDiscardedOnWholeBodyDeath(GameTestHelper h)throws Exception{cbcDeath(h,"rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption");}
 @GameTest(template="combat",batch="mount_heavy") public static void HeavyAddonIsDiscardedOnWholeBodyDeath(GameTestHelper h)throws Exception{cbcDeath(h,"com.dsvv.cbcat.cannon.heavy_autocannon.contraption.MountedHeavyAutocannonContraption");}
 @GameTest(template="combat",batch="mount_twin") public static void TwinAddonIsDiscardedOnWholeBodyDeath(GameTestHelper h)throws Exception{cbcDeath(h,"com.dsvv.cbcat.cannon.twin_autocannon.contraption.MountedTwinAutocannonContraption");}
 @GameTest(template="combat",batch="mount_subclass") public static void RealRadarSmartMountSubclassIsRecognizedAndCleaned(GameTestHelper h)throws Exception{
  var be=controller(h,block("rbasamoyai.createbigcannons.index.CBCBlocks","CANNON_MOUNT"),true);var cls=Class.forName("com.happysg.radar.block.mount.SmartMountBlockEntity");var smart=(BlockEntity)cls.getConstructor(BlockEntityType.class,BlockPos.class,net.minecraft.world.level.block.state.BlockState.class).newInstance(be.getType(),be.getBlockPos(),be.getBlockState());h.getLevel().setBlockEntity(smart);h.assertTrue(CannonDeathCleanup.isMount(smart),"CBC subclass missed");var entity=cannon(h,smart,"com.dsvv.cbcat.cannon.heavy_autocannon.contraption.MountedHeavyAutocannonContraption");death(h,smart,true);h.assertTrue(entity.isRemoved()&&!entity.getContraption().disassembled,"Subclass mounted cannon survived or restored");h.succeed();
 }
 private static void radarDeath(GameTestHelper h,boolean whole)throws Exception{var be=(MechanicalBearingBlockEntity)controller(h,block("com.happysg.radar.registry.ModBlocks","RADAR_BEARING_BLOCK"),whole);var entity=radar(h,be);death(h,be,whole);h.assertTrue(entity.isRemoved()&&!entity.getContraption().disassembled&&be.getMovedContraption()==null,"Radar survived or restored blocks");h.succeed();}
 @GameTest(template="combat",batch="radar_whole") public static void RadarAntennaIsDiscardedBeforeNativePlotRemoval(GameTestHelper h)throws Exception{radarDeath(h,true);}
 @GameTest(template="combat",batch="radar_fallback") public static void RadarAntennaIsDiscardedInBlockCleanupFallback(GameTestHelper h)throws Exception{radarDeath(h,false);}
 @GameTest(template="combat",batch="mount_permissions") public static void UnownedAndLivingControllersStayAndOrdinaryRadarDisassemblyWorks(GameTestHelper h)throws Exception{
  var be=(MechanicalBearingBlockEntity)controller(h,block("com.happysg.radar.registry.ModBlocks","RADAR_BEARING_BLOCK"),false);var entity=radar(h,be);
  BodyProtection.CLEANING.set(BodyProtection.CLEANING.get()+1);try{MountedContraptionCleanup.discardForDeath(be);h.assertTrue(!entity.isRemoved(),"Unowned radar deleted");BodyOwnership.get(h.getLevel()).bind(be.getBlockPos(),UUID.randomUUID());MountedContraptionCleanup.discardForDeath(be);h.assertTrue(!entity.isRemoved(),"Living owner's radar deleted without death job");}finally{BodyProtection.CLEANING.set(BodyProtection.CLEANING.get()-1);}
  be.disassemble();h.assertTrue(entity.isRemoved()&&entity.getContraption().disassembled,"Ordinary radar disassembly intercepted");BodyOwnership.get(h.getLevel()).forget(be.getBlockPos());h.getLevel().removeBlock(be.getBlockPos(),false);h.succeed();
 }
 @GameTest(template="combat",batch="cannon_permissions") public static void LivingAndUnownedCannonsStayAndOrdinaryDisassemblyWorks(GameTestHelper h)throws Exception{
  var be=controller(h,block("rbasamoyai.createbigcannons.index.CBCBlocks","CANNON_MOUNT"),false);var entity=cannon(h,be,"rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption");
  BodyProtection.CLEANING.set(BodyProtection.CLEANING.get()+1);try{MountedContraptionCleanup.discardForDeath(be);h.assertTrue(!entity.isRemoved(),"Unowned cannon deleted");BodyOwnership.get(h.getLevel()).bind(be.getBlockPos(),UUID.randomUUID());MountedContraptionCleanup.discardForDeath(be);h.assertTrue(!entity.isRemoved(),"Living cannon deleted without death job");}finally{BodyProtection.CLEANING.set(BodyProtection.CLEANING.get()-1);}
  be.getClass().getMethod("disassemble").invoke(be);h.assertTrue(entity.isRemoved()&&entity.getContraption().disassembled,"Ordinary cannon disassembly intercepted");BodyOwnership.get(h.getLevel()).forget(be.getBlockPos());h.getLevel().removeBlock(be.getBlockPos(),false);h.succeed();
 }

 @SuppressWarnings("unchecked") private static void fullCompact(GameTestHelper h,String type,String namespace,String family,boolean whole)throws Exception{
  var mount=controller(h,block("riftyboi.cbcmodernwarfare.index.CBCModernWarfareBlocks","COMPACT_MOUNT"),whole);var l=h.getLevel();var anchor=mount.getBlockPos().relative(mount.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getClockWise());var positions=new ArrayList<BlockPos>();
  var suffix=family.equals("rotarycannon")?List.of("breech","bearing","barrel","barrel","barrel"):List.of("breech","recoil_spring","barrel","barrel","muzzle_brake");
  for(int n=0;n<suffix.size();n++){var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace,"steel_"+family+"_"+suffix.get(n));var gun=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);h.assertTrue(gun!=Blocks.AIR,"Missing actual gun block "+id);var state=gun.defaultBlockState();for(var property:state.getProperties())if(property.getName().equals("facing"))state=state.setValue((net.minecraft.world.level.block.state.properties.Property<Direction>)property,Direction.NORTH);var pos=anchor.relative(Direction.NORTH,n-1);l.setBlockAndUpdate(pos,state);positions.add(pos);}
  h.runAfterDelay(3,()->{try{
   for(int n=0;n<positions.size();n++){var be=l.getBlockEntity(positions.get(n));h.assertTrue(be!=null,"Real cannon segment has no block entity");var behavior=be.getClass().getMethod("cannonBehavior").invoke(be);behavior.getClass().getMethod("setConnectedFace",Direction.class,boolean.class).invoke(behavior,Direction.NORTH,n+1<positions.size());behavior.getClass().getMethod("setConnectedFace",Direction.class,boolean.class).invoke(behavior,Direction.SOUTH,n>0);be.setChanged();}
   var entity=(AbstractContraptionEntity)mount.getClass().getMethod("getContraption").invoke(mount);
   if(entity==null){for(Class<?> cls=mount.getClass();cls!=null;cls=cls.getSuperclass())try{var assemble=cls.getDeclaredMethod("assemble");assemble.setAccessible(true);assemble.invoke(mount);break;}catch(NoSuchMethodException ignored){}entity=(AbstractContraptionEntity)mount.getClass().getMethod("getContraption").invoke(mount);}
   h.assertTrue(entity!=null,"Native compact mount did not assemble actual gun");
   var contraption=entity.getContraption();h.assertTrue(contraption.getClass().getName().equals(type)&&contraption.getBlocks().size()==positions.size(),"Native compact assembly did not capture complete real gun: "+contraption.getClass().getName()+" blocks="+contraption.getBlocks().size());
   final var realEntity=entity;
   death(h,mount,whole);h.assertTrue(entity.isRemoved()&&!contraption.disassembled,"Compact real gun survived or restored");h.assertTrue(mount.getClass().getMethod("getContraption").invoke(mount)==null,"Dead compact mount kept a stale cannon reference");
   mount.getClass().getMethod("disassemble").invoke(mount);
   h.runAfterDelay(5,()->{for(var pos:positions)h.assertTrue(l.getBlockState(pos).isAir(),"Gun block restored after death: "+pos);h.assertTrue(realEntity.isRemoved()&&!contraption.disassembled,"Later callback restored cannon");h.succeed();});
  }catch(Exception failure){Throwable cause=failure;while(cause instanceof java.lang.reflect.InvocationTargetException&&cause.getCause()!=null)cause=cause.getCause();String detail=cause.toString();if(cause instanceof com.simibubi.create.content.contraptions.AssemblyException assembly)detail=assembly.component.getString();com.mojang.logging.LogUtils.getLogger().error("Full cannon fixture assembly: {}",detail);throw new RuntimeException(detail);}});
 }
 @GameTest(template="combat",batch="compact_neo_full",timeoutTicks=100) public static void CompleteSteelRotaryGunClearsFromNeoCompactMount(GameTestHelper h)throws Exception{fullCompact(h,"riftyboi.cbcmodernwarfare.cannon_control.contraption.MountedRotarycannonContraption","cbcmodernwarfare","rotarycannon",true);}
 @GameTest(template="combat",batch="compact_twin_full",timeoutTicks=100) public static void CompleteSteelTwinGunClearsFromNeoCompactMount(GameTestHelper h)throws Exception{fullCompact(h,"com.dsvv.cbcat.cannon.twin_autocannon.contraption.MountedTwinAutocannonContraption","cbc_at","twin_autocannon",true);}
 @GameTest(template="combat",batch="compact_heavy_full",timeoutTicks=100) public static void CompleteSteelHeavyGunClearsFromNeoCompactMount(GameTestHelper h)throws Exception{fullCompact(h,"com.dsvv.cbcat.cannon.heavy_autocannon.contraption.MountedHeavyAutocannonContraption","cbc_at","heavy_autocannon",false);}

}
