package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.combat.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_missile.MagicMissileProjectile;
import io.redspace.ironsspellbooks.entity.spells.magic_arrow.MagicArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.fire_arrow.FireArrowProjectile;
import io.redspace.ironsspellbooks.entity.spells.fireball.*;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import io.redspace.ironsspellbooks.spells.lightning.ShockwaveSpell;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;
public final class IronSpellsTestScenarios {
 private static HeartBlockEntity core(GameTestHelper h){return core(h,0);}
 private static HeartBlockEntity core(GameTestHelper h,int offset){var level=h.getLevel();var p=h.absolutePos(new BlockPos(5,40,5+offset));var blocks=List.of(p,p.east(),p.east(2));for(var b:blocks)level.setBlockAndUpdate(b,b.equals(p)?SableLife.HEART.get().defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,p,blocks,new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+2,p.getY(),p.getZ()));var c=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());c.configure(1000,60);c.tryActivate(TestActors.creative(level));return c;}
 private static long entities(ServerLevel l){return java.util.stream.StreamSupport.stream(l.getAllEntities().spliterator(),false).count();}
 private static final class AddonMissile extends MagicMissileProjectile {
  Vec3 impact;AddonMissile(ServerLevel l,LivingEntity caster){super(l,caster);}
  @Override public void impactParticles(double x,double y,double z){impact=new Vec3(x,y,z);super.impactParticles(x,y,z);}
 }
 public static void direct(GameTestHelper h,int kind){var c=core(h);h.runAfterDelay(4,()->{
  var level=h.getLevel();var target=c.getBlockPos().east(2);var center=SableBodyProbe.toWorld(level,target.getCenter());var caster=TestActors.creative(level);caster.setPos(center.add(4,0,0));
  AbstractMagicProjectile shot=switch(kind){case 0->new MagicMissileProjectile(level,caster);case 1->new MagicArrowProjectile(level,caster);case 2->new SmallMagicFireball(level,caster);default->new AddonMissile(level,caster);};
  shot.setPos(center.add(1.5,0,0));shot.setDeltaMovement(-3,0,0);shot.setNoGravity(true);shot.setDamage(10);long beforeEntities=entities(level);float before=c.health().current();shot.tick();
  h.assertTrue(Math.abs(before-c.health().current()-10)<.001,"Native projectile missed or duplicated body damage: kind="+kind+" damage="+(before-c.health().current())+" removed="+shot.isRemoved());
  h.assertTrue(level.getBlockState(target).is(Blocks.IRON_BLOCK),"Direct magic removed protected body block");
  if(kind==1){h.assertTrue(!shot.isRemoved(),"Native piercing arrow was forced to disappear");shot.setPos(center.add(1.5,0,0));shot.setDeltaMovement(-3,0,0);shot.tick();h.assertTrue(c.health().current()==before-10,"Piercing arrow damaged same creature twice");}
  else h.assertTrue(shot.isRemoved(),"Native one-shot projectile did not expire");
  if(shot instanceof AddonMissile addon)h.assertTrue(addon.impact!=null&&addon.impact.distanceTo(center)<2,"Native impact VFX did not stay near the world-space body: "+addon.impact);
  h.assertTrue(entities(level)==beforeEntities,"Bridge spawned proxy entities");shot.discard();h.succeed();
 });}
 public static void blast(GameTestHelper h,boolean terrain,boolean arrow){var c=core(h);var nearby=core(h,3);h.runAfterDelay(4,()->{
  var level=h.getLevel();var target=c.getBlockPos().east(2);var center=SableBodyProbe.toWorld(level,target.getCenter());var caster=TestActors.creative(level);caster.setPos(center.add(6,0,0));
  AbstractMagicProjectile shot=arrow?new FireArrowProjectile(level,caster):new MagicFireball(level,caster);shot.setPos(center.add(1.5,0,0));shot.setDeltaMovement(-3,0,0);shot.setNoGravity(true);shot.setDamage(20);shot.setExplosionRadius(4);
  var ray=level.clip(new net.minecraft.world.level.ClipContext(shot.position(),shot.position().add(shot.getDeltaMovement()),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,shot));
  var impact=SableBodyProbe.toWorld(level,ray.getLocation());var eye=io.redspace.ironsspellbooks.api.util.Utils.raycastForBlock(level,shot.position(),shot.position().add(0,2,0),net.minecraft.world.level.ClipContext.Fluid.NONE).getLocation();
  var contact=BodySphereQuery.query(level,impact,SableBodyProbe.toWorld(level,eye),4).stream().filter(hit->hit.core()==c).findFirst();h.assertTrue(contact.isPresent(),"Explosion fixture failed real sphere query");
  var adjacent=BodySphereQuery.query(level,impact,SableBodyProbe.toWorld(level,eye),4).stream().filter(hit->hit.core()==nearby).findFirst();h.assertTrue(adjacent.isPresent(),"Adjacent-core blast fixture failed");
  double adjacentExpected=(arrow?10:20)*(1-adjacent.get().distance()*adjacent.get().distance()/16);float adjacentBefore=nearby.health().current();
  double expected=arrow?20:20*(1-contact.get().distance()*contact.get().distance()/16);float before=c.health().current();boolean old=ServerConfigs.SPELL_GREIFING.get();
  try{ServerConfigs.SPELL_GREIFING.set(terrain);shot.tick();h.assertTrue(Math.abs(before-c.health().current()-expected)<.01,"Native spell blast changed damage or added TNT: arrow="+arrow+" terrain="+terrain+" expected="+expected+" actual="+(before-c.health().current()));
   h.assertTrue(Math.abs(adjacentBefore-nearby.health().current()-adjacentExpected)<.01,"Adjacent core lost falloff/splash semantics or received TNT damage: expected="+adjacentExpected+" actual="+(adjacentBefore-nearby.health().current()));
   h.assertTrue(level.getBlockState(target).is(Blocks.IRON_BLOCK),"Terrain explosion removed protected core body");
  }finally{ServerConfigs.SPELL_GREIFING.set(old);shot.discard();}h.succeed();
 });}
 private static final class OptInBolt extends AbstractMagicProjectile {
  OptInBolt(ServerLevel level,LivingEntity owner){super(io.redspace.ironsspellbooks.registries.EntityRegistry.MAGIC_MISSILE_PROJECTILE.get(),level);setOwner(owner);setNoGravity(true);}
  @Override public void trailParticles(){}
  @Override public void impactParticles(double x,double y,double z){}
  @Override public float getSpeed(){return 3;}
  @Override public java.util.Optional<net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>> getImpactSound(){return java.util.Optional.empty();}
 }
 public static void immune(GameTestHelper h){var c=core(h);var armor=c.modules().add("armor");armor.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.EAST)));armor.edit("damageModifier",-100,"");h.runAfterDelay(4,()->{
  var l=h.getLevel();var center=SableBodyProbe.toWorld(l,c.getBlockPos().east(2).getCenter());var caster=TestActors.creative(l);caster.setPos(center.add(4,0,0));var arrow=new MagicArrowProjectile(l,caster);arrow.setDamage(10);arrow.setNoGravity(true);arrow.setPos(center.add(1.5,0,0));arrow.setDeltaMovement(-3,0,0);float before=c.health().current();arrow.tick();h.assertTrue(c.health().current()==before,"Arrow bypassed immunity");armor.edit("enabled",0,"");arrow.setPos(center.add(1.5,0,0));arrow.setDeltaMovement(-3,0,0);arrow.tick();h.assertTrue(c.health().current()==before,"Same arrow retried previously immune creature");arrow.discard();h.succeed();
 });}
 public static void optin(GameTestHelper h){var c=core(h);h.runAfterDelay(4,()->{
  var l=h.getLevel();var center=SableBodyProbe.toWorld(l,c.getBlockPos().east(2).getCenter());var caster=TestActors.creative(l);caster.setPos(center.add(4,0,0));
  var unknown=new OptInBolt(l,caster);unknown.setPos(center.add(1.5,0,0));unknown.setDeltaMovement(-3,0,0);unknown.setDamage(10);float before=c.health().current();unknown.tick();h.assertTrue(c.health().current()==before,"Unknown base projectile was assumed to be damaging");unknown.discard();
  h.assertTrue(!dev.sablelife.compat.ironspells.IronSpellsBodyBridge.registerDirectProjectile(AbstractMagicProjectile.class,AbstractMagicProjectile::getDamage),"Blanket abstract profile accepted");
  h.assertTrue(dev.sablelife.compat.ironspells.IronSpellsBodyBridge.registerDirectProjectile(OptInBolt.class,shot->shot.getDamage()*2),"Addon registration rejected");
  var registered=new OptInBolt(l,caster);registered.setPos(center.add(1.5,0,0));registered.setDeltaMovement(-3,0,0);registered.setDamage(10);registered.tick();h.assertTrue(c.health().current()==before-20,"Registered profile did not use its damage calculation");registered.discard();h.succeed();
 });}
 public static void front(GameTestHelper h,boolean wall){var c=core(h);h.runAfterDelay(4,()->{
  var l=h.getLevel();var center=SableBodyProbe.toWorld(l,c.getBlockPos().east(2).getCenter());var caster=TestActors.creative(l);caster.setPos(center.add(6,0,0));
  var shot=new MagicMissileProjectile(l,caster);shot.setPos(center.add(3,0,0));shot.setDeltaMovement(-4,0,0);shot.setDamage(10);shot.setNoGravity(true);
  net.minecraft.world.entity.animal.Pig pig=null;float before=0;
  if(wall){l.setBlockAndUpdate(BlockPos.containing(center.add(1.5,0,0)),Blocks.STONE.defaultBlockState());}
  else{pig=net.minecraft.world.entity.EntityType.PIG.create(l);pig.setPos(center.add(1.5,-.4,0));pig.setNoAi(true);pig.setNoGravity(true);l.addFreshEntity(pig);before=pig.getHealth();}
  float hp=c.health().current();shot.tick();h.assertTrue(c.health().current()==hp&&shot.isRemoved(),"Earlier terrain/living target did not consume missile");
  if(pig!=null){h.assertTrue(pig.getHealth()<before,"Native living target damage was changed");pig.discard();}shot.discard();h.succeed();
 });}
 public static void shockwave(GameTestHelper h){var c=core(h);h.runAfterDelay(4,()->{
  var level=h.getLevel();var center=SableBodyProbe.toWorld(level,c.getBlockPos().getCenter());var caster=TestActors.creative(level);caster.setPos(center.add(0,0,-3));
  var spell=(ShockwaveSpell)SpellRegistry.SHOCKWAVE_SPELL.get();float normal=spell.getDamage(2,caster);caster.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.SPELL_POWER).setBaseValue(2);h.assertTrue(spell.getDamage(2,caster)>normal,"Caster power fixture did not boost native spell");float expected=spell.getDamage(2,caster),before=c.health().current();long beforeEntities=entities(level);
  spell.onCast(level,2,caster,CastSource.COMMAND,new MagicData());
  h.assertTrue(Math.abs(before-c.health().current()-expected)<.01,"Native Shockwave did not use spell-level/caster damage: expected="+expected+" actual="+(before-c.health().current()));
  h.assertTrue(entities(level)==beforeEntities,"Shockwave bridge spawned proxy targets");h.succeed();
 });}
 public static void ray(GameTestHelper h,int kind){var c=core(h);h.runAfterDelay(4,()->{
  var l=h.getLevel();var block=c.getBlockPos().east(2);var center=SableBodyProbe.toWorld(l,block.getCenter());var caster=TestActors.creative(l);
  caster.setPos(center.add(3,-caster.getEyeHeight(),0));caster.setYRot(90);caster.setXRot(0);
  net.minecraft.world.entity.animal.Pig pig=null;
  if(kind==1){pig=net.minecraft.world.entity.EntityType.PIG.create(l);pig.setPos(center.add(1.5,-.4,0));pig.setNoAi(true);pig.setNoGravity(true);l.addFreshEntity(pig);}
  if(kind==2)l.setBlockAndUpdate(BlockPos.containing(center.add(1.5,0,0)),Blocks.STONE.defaultBlockState());
  // Assert coordinate safety before invoking the native query: the unpatched builder would scan toward the remote plot.
  var nativeClip=l.clip(new net.minecraft.world.level.ClipContext(caster.getEyePosition(),center.add(-2,0,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,caster));
  var normalized=dev.sablelife.compat.ironspells.IronSpellsBodyBridge.worldBlockHit(l,nativeClip);
  if(kind==0)h.assertTrue(nativeClip.getLocation().distanceTo(caster.getEyePosition())>10000&&normalized.getBlockPos().equals(block),"Fixture did not exercise remote plot coordinates with retained body identity");
  h.assertTrue(normalized.getLocation().distanceTo(caster.getEyePosition())<8,"Ray fixture world normalization failed");
  var hit=io.redspace.ironsspellbooks.api.util.RaycastBuilder.begin(l,caster).start(caster.getEyePosition()).end(center.add(-2,0,0)).checkForBlocks(true).bbInflation(.15F).build();
  h.assertTrue(hit.getLocation().distanceTo(caster.getEyePosition())<8,"Native ray leaked storage coordinates");
  if(kind==1)h.assertTrue(hit instanceof EntityHitResult e&&e.getEntity()==pig,"Closer living target no longer wins");
  else h.assertTrue(hit instanceof BlockHitResult b&&b.getBlockPos().equals(kind==0?block:BlockPos.containing(center.add(1.5,0,0))),"Ray changed body/terrain identity");
  float before=c.health().current();
  SpellRegistry.RAY_OF_SIPHONING_SPELL.get().onCast(l,1,caster,CastSource.COMMAND,new MagicData());
  h.assertTrue(c.health().current()==before,"Coordinate fix silently added unsupported siphoning body damage");
  if(pig!=null)pig.discard();h.succeed();
 });}
}
