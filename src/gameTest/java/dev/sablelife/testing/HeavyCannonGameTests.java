package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_mounted_cleanup_tests") @PrefixGameTestTemplate(false)
public final class HeavyCannonGameTests {
 @GameTest(template="combat",batch="heavy_cannon_damage")
 public static void HeavyAutocannonNativePenetrationDamagesOncePerCreature(GameTestHelper h)throws Exception{
  var registry=Class.forName("com.dsvv.cbcat.registry.EntityRegister");
  for(var key:List.of("HA_AP_PROJECTILE","HA_APDSFS_PROJECTILE","HA_HE_PROJECTILE")){
   var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,40,4));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
   var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
   var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(1000,60);core.tryActivate(TestActors.creative(level));
   var entry=registry.getField(key).get(null);var type=(EntityType<?>)entry.getClass().getMethod("get").invoke(entry);var projectile=(Projectile)type.create(level);projectile.setDeltaMovement(0,-1,0);
   var base=Class.forName("rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile");var damage=base.getDeclaredField("damage");damage.setAccessible(true);damage.setFloat(projectile,23F);
   var grief=Class.forName("rbasamoyai.createbigcannons.config.CBCCfgMunitions$GriefState");var contextType=Class.forName("rbasamoyai.createbigcannons.munitions.ProjectileContext");var context=contextType.getConstructor(base,grief).newInstance(projectile,grief.getField("NO_DAMAGE").get(null));
   var heavy=Class.forName("com.dsvv.cbcat.cannon.heavy_autocannon.munitions.AbstractHeavyAutocannonProjectile");var method=heavy.getDeclaredMethod("calculateBlockPenetration",contextType,net.minecraft.world.level.block.state.BlockState.class,BlockHitResult.class);method.setAccessible(true);
   var hit=new BlockHitResult(core.getBlockPos().getCenter(),Direction.UP,core.getBlockPos(),false);method.invoke(projectile,context,level.getBlockState(core.getBlockPos()),hit);
   h.assertTrue(core.health().current()==977,key+" did not transfer native 23 direct damage: "+core.health().current());
   method.invoke(projectile,context,level.getBlockState(core.getBlockPos()),hit);h.assertTrue(core.health().current()==977,key+" applied direct damage twice");core.resetForCreative(TestActors.creative(level));dev.sablelife.compat.StructureRemovalApi.remove(level,body);
  }h.succeed();
 }
}
