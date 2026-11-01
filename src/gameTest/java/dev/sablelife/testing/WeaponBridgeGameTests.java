package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.combat.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_blast_tests") @PrefixGameTestTemplate(false)
public final class WeaponBridgeGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h,boolean hole){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,6,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var positions=new ArrayList<BlockPos>(List.of(p,p.east()));if(hole){l.setBlockAndUpdate(p.east(12),Blocks.IRON_BLOCK.defaultBlockState());positions.add(p.east(12));}var b=SubLevelAssemblyHelper.assembleBlocks(l,p,positions,new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+(hole?12:1),p.getY(),p.getZ()));var core=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());core.configure(1000,60);core.tryActivate(TestActors.creative(l));return core;}
 public static final class DamageProjectile extends Snowball {public DamageProjectile(Level l){super(EntityType.SNOWBALL,l);}public float getDamage(Vec3 hit){return 17;}}
 @GameTest(template="combat",batch="bridge_projectile") public static void NonArrowDamageProjectileUsesCoreArmorAndDeduplicates(GameTestHelper h){var c=fixture(h,false);var armor=c.modules().add("armor");armor.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));ArmorTestSupport.set(c,armor,-50);var bullet=new DamageProjectile(h.getLevel());var hit=new BlockHitResult(c.getBlockPos().getCenter(),Direction.UP,c.getBlockPos(),false);EventHooks.onProjectileImpact(bullet,hit);float after=c.health().current();h.assertTrue(after==991.5F,"Generic projectile did not apply native damage through armor: "+after);EventHooks.onProjectileImpact(bullet,hit);h.assertTrue(c.health().current()==after,"Repeated impact doubled damage");h.succeed();}
 @GameTest(template="combat",batch="bridge_explosion") public static void ProtectedBodyStillTakesOneExplosionHit(GameTestHelper h){var c=fixture(h,false);var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()).add(0,1,0);var explosion=new Explosion(h.getLevel(),null,center.x,center.y,center.z,2,false,Explosion.BlockInteraction.KEEP);var event=new ExplosionEvent.Detonate(h.getLevel(),explosion,new ArrayList<>());BodyExplosionDamage.detonate(event);float after=c.health().current();h.assertTrue(after<1000&&!h.getLevel().getBlockState(c.getBlockPos()).isAir(),"Explosion protection erased HP damage");BodyExplosionDamage.detonate(event);h.assertTrue(c.health().current()==after,"Same explosion applied multiple times");h.succeed();}
 @GameTest(template="combat",batch="bridge_air") public static void ExplosionInEmptyBodyBoxGapDoesNotDamageCore(GameTestHelper h){var c=fixture(h,true);var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()).add(6,0,0);var e=new Explosion(h.getLevel(),null,center.x,center.y,center.z,.5F,false,Explosion.BlockInteraction.KEEP);BodyExplosionDamage.detonate(new ExplosionEvent.Detonate(h.getLevel(),e,new ArrayList<>()));h.assertTrue(c.health().current()==1000,"Empty space inside enclosing body box took damage");h.succeed();}
 @GameTest(template="combat",batch="target_cast") public static void TargetCastNeedsNoMarkAndFreezesSpherePoint(GameTestHelper h){var c=fixture(h,false);var m=c.modules().add("sphere_blast");m.edit("releasePosition",1,"");m.edit("target",1,"");m.edit("chargeTime",4,"");m.edit("damage",3,"");var point=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()).add(0,5,0);var pig=EntityType.PIG.create(h.getLevel());pig.setPos(point);pig.setNoAi(true);pig.setNoGravity(true);h.getLevel().addFreshEntity(pig);h.assertTrue(HeartModules.status(m,c).equals("valid")&&TargetCastOrigin.point(c,m)!=null,"Target mode still requires mark");c.modules().tick(c);var p=ModuleSphereBlast.center(c,m);pig.setPos(point.add(15,0,0));h.runAfterDelay(8,()->{h.assertTrue(pig.getHealth()==10,"Sphere followed target after charge began");pig.discard();h.succeed();});}
 @GameTest(template="combat",batch="target_pull") public static void AttractionTargetPointDefaultsAndAudioPersist(GameTestHelper h){var c=fixture(h,false);var m=c.modules().add("pull");m.edit("releasePosition",1,"");m.edit("target",1,"");var point=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()).add(0,5,0);var pig=EntityType.PIG.create(h.getLevel());pig.setPos(point);pig.setNoAi(true);pig.setNoGravity(true);h.getLevel().addFreshEntity(pig);h.assertTrue(ModulePull.destination(c,m).distanceTo(pig.getBoundingBox().getCenter())<.001&&HeartModules.status(m,c).equals("valid"),"Attraction not on target");var copy=VirtualModule.load(m.save());h.assertTrue(copy.value("releasePosition")==1&&copy.hasSound("warningSound")&&copy.hasSound("pullSound")&&new VirtualModule("sphere_blast").value("sphereChargeSpeed")==2,"Target/audio/default settings lost");pig.discard();h.succeed();}
}
