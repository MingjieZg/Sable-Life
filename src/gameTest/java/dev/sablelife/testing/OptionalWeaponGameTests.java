package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.combat.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.lang.reflect.*;
import java.util.*;
@GameTestHolder("sablelife_weapon_tests") @PrefixGameTestTemplate(false)
public final class OptionalWeaponGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,6,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var core=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());core.configure(1000,60);core.tryActivate(TestActors.creative(l));return core;}
 private static void field(Object o,String name,Object value)throws Exception{for(var c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 @GameTest(template="combat",batch="real_tacz") public static void RealTaczBlockCallbackTransfersNativeBulletDamage(GameTestHelper h)throws Exception{
  var core=fixture(h);var type=Class.forName("com.tacz.guns.entity.EntityKineticBullet");var bullet=(Projectile)type.getConstructor(EntityType.class,Level.class).newInstance(type.getField("TYPE").get(null),h.getLevel());var world=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());
  var pair=Class.forName("com.tacz.guns.resource.pojo.data.gun.ExtraDamage$DistanceDamagePair").getConstructor(float.class,float.class).newInstance(100F,17F);field(bullet,"damageAmount",new LinkedList<>(List.of(pair)));field(bullet,"startPos",world.add(0,1,0));field(bullet,"distanceAmount",100F);field(bullet,"damageModifier",1F);field(bullet,"shotDamageMultiplier",1F);for(String key:List.of("ammoId","gunId","gunDisplayId"))field(bullet,key,net.minecraft.resources.ResourceLocation.parse(key.equals("ammoId")?"tacz:556x45":"tacz:m4a1"));
  var hit=new BlockHitResult(core.getBlockPos().getCenter(),Direction.UP,core.getBlockPos(),false);var method=type.getDeclaredMethod("onHitBlock",BlockHitResult.class,Vec3.class,Vec3.class);method.setAccessible(true);method.invoke(bullet,hit,world.add(0,1,0),world);h.assertTrue(core.health().current()==983,"Real TaCZ callback did not use configured 17 damage: "+core.health().current());h.succeed();
 }
 @GameTest(template="combat",batch="real_cbc") public static void RealCannonPenetrationCallbackTransfersNativeDamage(GameTestHelper h)throws Exception{
  var core=fixture(h);var registry=Class.forName("rbasamoyai.createbigcannons.index.CBCEntityTypes");var entry=registry.getField("SHOT").get(null);var entityType=(EntityType<?>)entry.getClass().getMethod("get").invoke(entry);var projectile=(Projectile)entityType.create(h.getLevel());field(projectile,"damage",23F);projectile.setDeltaMovement(0,-1,0);
  var base=Class.forName("rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile");var grief=Class.forName("rbasamoyai.createbigcannons.config.CBCCfgMunitions$GriefState");var contextType=Class.forName("rbasamoyai.createbigcannons.munitions.ProjectileContext");var context=contextType.getConstructor(base,grief).newInstance(projectile,grief.getField("NO_DAMAGE").get(null));var cls=Class.forName("rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile");var method=cls.getDeclaredMethod("calculateBlockPenetration",contextType,net.minecraft.world.level.block.state.BlockState.class,BlockHitResult.class);method.setAccessible(true);var hit=new BlockHitResult(core.getBlockPos().getCenter(),Direction.UP,core.getBlockPos(),false);method.invoke(projectile,context,h.getLevel().getBlockState(core.getBlockPos()),hit);h.assertTrue(core.health().current()<=977&&core.health().current()>940,"Real cannon callback missing direct/impact explosion damage: "+core.health().current());float after=core.health().current();BodyDamageBridge.cannon(projectile,hit);h.assertTrue(core.health().current()==after,"Cannon direct hit applied twice");h.succeed();
 }
 @GameTest(template="combat",batch="real_tacz_blast") public static void TaczExplosionUsesActualPowerAndRadius(GameTestHelper h)throws Exception{var core=fixture(h);var p=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter()).add(0,1,0);var type=Class.forName("com.tacz.guns.util.block.ProjectileExplosion");var e=(Explosion)type.getConstructor(Level.class,Entity.class,Entity.class,net.minecraft.world.damagesource.DamageSource.class,ExplosionDamageCalculator.class,double.class,double.class,double.class,float.class,float.class,boolean.class,Explosion.BlockInteraction.class).newInstance(h.getLevel(),null,null,null,null,p.x,p.y,p.z,20F,4F,false,Explosion.BlockInteraction.KEEP);h.assertTrue(e instanceof BodyDamageBridge.BlastProfile&&BodyDamageBridge.explosionRange(e)==4&&BodyDamageBridge.explosionDamage(e,2,4)==10,"TaCZ blast profile mismatch");e.explode();h.assertTrue(core.health().current()<1000&&core.health().current()>=980,"Real TaCZ explosion missing or used TNT formula: "+core.health().current());h.succeed();}
 @GameTest(template="combat",batch="real_tacz_ray",timeoutTicks=30) public static void RealTaczRayTraceHitsMovingVoxelsAndStopsBeforeTerrain(GameTestHelper h)throws Exception{
  var core=fixture(h);
  h.runAfterDelay(4,()->{try{
   var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());var start=center.add(0,0,-3);var end=center.add(0,0,3);var context=new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(Entity)null);
   var method=Class.forName("com.tacz.guns.util.block.BlockRayTrace").getMethod("rayTraceBlocks",Level.class,ClipContext.class);var hit=(BlockHitResult)method.invoke(null,h.getLevel(),context);
   h.assertTrue(hit.getType()==HitResult.Type.BLOCK&&dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),hit.getBlockPos())!=null,"Native TaCZ trace ignored moving body voxels");h.assertTrue(hit.getLocation().distanceTo(center)<1.1,"TaCZ hit point was left in plot/storage coordinates");
   var wall=BlockPos.containing(center.add(0,0,-2));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());var blocked=(BlockHitResult)method.invoke(null,h.getLevel(),context);h.assertTrue(blocked.getBlockPos().equals(wall),"Body clip bypassed earlier terrain wall");h.succeed();
  }catch(Exception e){throw new RuntimeException(e);}});
 }
 @GameTest(template="combat",batch="real_tacz_flight",timeoutTicks=30) public static void RealTaczFlightDeliversNativeDamageWithoutPassingThroughLife(GameTestHelper h)throws Exception{
  var core=fixture(h);
  h.runAfterDelay(4,()->{try{
   var world=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());var start=world.add(0,0,-3);var type=Class.forName("com.tacz.guns.entity.EntityKineticBullet");var bullet=(Projectile)type.getConstructor(EntityType.class,Level.class).newInstance(type.getField("TYPE").get(null),h.getLevel());
   var pair=Class.forName("com.tacz.guns.resource.pojo.data.gun.ExtraDamage$DistanceDamagePair").getConstructor(float.class,float.class).newInstance(100F,17F);field(bullet,"damageAmount",new LinkedList<>(List.of(pair)));field(bullet,"startPos",start);field(bullet,"distanceAmount",100F);field(bullet,"damageModifier",1F);field(bullet,"shotDamageMultiplier",1F);for(String key:List.of("ammoId","gunId","gunDisplayId"))field(bullet,key,net.minecraft.resources.ResourceLocation.parse(key.equals("ammoId")?"tacz:556x45":"tacz:m4a1"));bullet.setPos(start);bullet.setDeltaMovement(0,0,6);
   var tick=type.getDeclaredMethod("onBulletTick");tick.setAccessible(true);tick.invoke(bullet);
   h.assertTrue(core.health().current()==983,"Flying TaCZ bullet passed through or lost native 17 damage: "+core.health().current());h.assertTrue(bullet.isRemoved(),"Native block impact did not stop non-piercing bullet");h.succeed();
  }catch(Exception e){throw new RuntimeException(e);}});
 }
}
