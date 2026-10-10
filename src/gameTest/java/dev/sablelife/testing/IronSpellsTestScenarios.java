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
  if(kind==1){h.assertTrue(shot.isRemoved(),"Body contact did not consume piercing arrow");shot.setPos(center.add(1.5,0,0));shot.setDeltaMovement(-3,0,0);shot.tick();h.assertTrue(c.health().current()==before-10,"Piercing arrow damaged same creature twice");}
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
  h.assertTrue(kind!=0||c.health().current()<before,"Siphoning body hit did not apply native tick damage");
  if(pig!=null)pig.discard();h.succeed();
 });}
 public static void newDirect(GameTestHelper h,int kind){var c=core(h);h.runAfterDelay(4,()->{
  var l=h.getLevel();var center=SableBodyProbe.toWorld(l,c.getBlockPos().east(2).getCenter());var caster=TestActors.creative(l);caster.setPos(center.add(4,0,0));
  AbstractMagicProjectile shot=switch(kind){case 0->new io.redspace.ironsspellbooks.entity.spells.firebolt.FireboltProjectile(l,caster);case 1->new io.redspace.ironsspellbooks.entity.spells.icicle.IcicleProjectile(l,caster);default->new io.redspace.ironsspellbooks.entity.spells.lightning_lance.LightningLanceProjectile(l,caster);};
  shot.setPos(center.add(1.5,0,0));shot.setDeltaMovement(-3,0,0);shot.setNoGravity(true);shot.setDamage(12);float before=c.health().current();shot.tick();
  h.assertTrue(c.health().current()==before-12&&shot.isRemoved(),"New direct projectile missed or did not expire: "+kind);h.succeed();
 });}
 public static void blood(GameTestHelper h,boolean hole){HeartBlockEntity c;
  if(hole){var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,40,5));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(4),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east(4)),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+4,p.getY(),p.getZ()));c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());c.configure(1000,60);c.tryActivate(TestActors.creative(l));}else c=core(h);h.runAfterDelay(4,()->{
  var l=h.getLevel();var block=c.getBlockPos().east(2);var center=SableBodyProbe.toWorld(l,block.getCenter());var caster=TestActors.creative(l);caster.setPos(center.add(4,0,0));
  caster.setHealth(5);
  var shot=new io.redspace.ironsspellbooks.entity.spells.blood_slash.BloodSlashProjectile(l,caster);shot.setDamage(15);shot.setRadius(hole?.15F:1F);shot.setPos(hole?center:center.add(.9,0,0));shot.setDeltaMovement(Vec3.ZERO);
  float before=c.health().current();shot.handleHitDetection();
  h.assertTrue(hole?c.health().current()==before&&!shot.isRemoved():c.health().current()==before-15&&shot.isRemoved(),"Blood slash used empty enclosing bounds or missed box-edge contact: hole="+hole+" hp="+c.health().current()+" removed="+shot.isRemoved());
  h.assertTrue(Math.abs(caster.getHealth()-(hole?5:7.25))<.001,"Blood slash did not preserve native 15% actual-damage lifesteal");
  shot.handleHitDetection();h.assertTrue(c.health().current()==before-(hole?0:15),"Removed blood slash repeated damage");shot.discard();h.succeed();
 });}
 public static void cone(GameTestHelper h,int kind,boolean wall){var c=core(h);h.runAfterDelay(4,()->{
  var l=h.getLevel();var center=SableBodyProbe.toWorld(l,c.getBlockPos().east(2).getCenter());var caster=TestActors.creative(l);caster.setPos(center.add(3,-caster.getEyeHeight()+.5,0));caster.setYRot(90);caster.setXRot(0);
  io.redspace.ironsspellbooks.entity.spells.AbstractConeProjectile shot=switch(kind){case 0->new io.redspace.ironsspellbooks.entity.spells.fire_breath.FireBreathProjectile(l,caster);case 1->new io.redspace.ironsspellbooks.entity.spells.cone_of_cold.ConeOfColdProjectile(l,caster);default->new io.redspace.ironsspellbooks.entity.spells.poison_breath.PoisonBreathProjectile(l,caster);};shot.setDamage(8);
  if(wall)for(int y=-2;y<=2;y++)for(int z=-4;z<=4;z++)l.setBlockAndUpdate(BlockPos.containing(center.add(1.5,y,z)),Blocks.STONE.defaultBlockState());
  boolean old=ServerConfigs.SPELL_GREIFING.get();ServerConfigs.SPELL_GREIFING.set(false);float before=c.health().current();
  try{shot.tick();h.assertTrue(c.health().current()==before-(wall?0:8),"Cone window missed, duplicated across parts, or bypassed wall: "+kind+" hp="+c.health().current());shot.tick();h.assertTrue(c.health().current()==before-(wall?0:8),"Cone applied damage without native activation");}
  finally{ServerConfigs.SPELL_GREIFING.set(old);}
  h.runAfterDelay(2,()->{shot.setDealDamageActive();boolean prev=ServerConfigs.SPELL_GREIFING.get();try{ServerConfigs.SPELL_GREIFING.set(false);shot.tick();h.assertTrue(c.health().current()==before-(wall?0:16),"Reactivated cone failed next native window");}finally{ServerConfigs.SPELL_GREIFING.set(prev);shot.discard();}h.succeed();});
 });}
 public static void siphon(GameTestHelper h){var c=core(h);var armor=c.modules().add("armor");armor.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.EAST)));armor.edit("damageModifier",-50,"");h.runAfterDelay(4,()->{
  var l=h.getLevel();var center=SableBodyProbe.toWorld(l,c.getBlockPos().east(2).getCenter());var caster=TestActors.creative(l);caster.setPos(center.add(3,-caster.getEyeHeight(),0));caster.setYRot(90);caster.setXRot(0);caster.setHealth(5);
  var spell=SpellRegistry.RAY_OF_SIPHONING_SPELL.get();float before=c.health().current();spell.onCast(l,2,caster,CastSource.COMMAND,new MagicData());float loss=before-c.health().current();
  h.assertTrue(loss>0&&Math.abs(caster.getHealth()-5-loss)<.001,"Siphoning did not heal actual body loss");
  spell.onCast(l,2,caster,CastSource.COMMAND,new MagicData());h.assertTrue(c.health().current()==before-loss,"Repeated same-window siphoning duplicated damage");
  h.runAfterDelay(10,()->{var current=SableBodyProbe.toWorld(l,c.getBlockPos().east(2).getCenter());caster.setPos(current.add(3,-caster.getEyeHeight(),0));spell.onCast(l,2,caster,CastSource.COMMAND,new MagicData());h.assertTrue(Math.abs(c.health().current()-(before-loss*2))<.001,"Next native siphoning pulse failed: actual="+c.health().current()+" expected="+(before-loss*2));h.succeed();});
 });}
 public static void immuneSiphon(GameTestHelper h){var c=core(h);var armor=c.modules().add("armor");armor.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.EAST)));armor.edit("damageModifier",-100,"");h.runAfterDelay(4,()->{
  var l=h.getLevel();var center=SableBodyProbe.toWorld(l,c.getBlockPos().east(2).getCenter());var caster=TestActors.creative(l);caster.setPos(center.add(3,-caster.getEyeHeight(),0));caster.setYRot(90);caster.setXRot(0);caster.setHealth(5);float before=c.health().current();
  SpellRegistry.RAY_OF_SIPHONING_SPELL.get().onCast(l,2,caster,CastSource.COMMAND,new MagicData());h.assertTrue(c.health().current()==before&&caster.getHealth()==5,"Immune core still supplied siphoning healing");h.succeed();
 });}
}
