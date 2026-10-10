package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.api.BodyCombatApi;
import dev.sablelife.combat.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_audit_tests") @PrefixGameTestTemplate(false)
public final class BodySpellApiGameTests {
 private static HeartBlockEntity sparse(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,40,4));var end=p.east(4);l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(end,Blocks.STONE_SLAB.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,end),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+4,p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());c.configure(1000,60);c.tryActivate(TestActors.creative(l));return c;}
 @GameTest(template="combat",batch="spell_sphere_geometry")
 public static void SpheresRejectHollowBoundsEmptyCornersAndSlabAir(GameTestHelper h){var c=sparse(h);h.runAfterDelay(4,()->{var l=h.getLevel();var center=SableBodyProbe.toWorld(l,c.getBlockPos().getCenter());var eye=center.add(0,1,-3);
  h.assertTrue(BodySphereQuery.query(l,center.add(2,0,0),eye,.4).isEmpty(),"Sphere hit empty space inside body bounds");
  h.assertTrue(BodySphereQuery.query(l,center.add(.75,0,.75),eye,.3).isEmpty(),"Sphere hit an enclosing-box corner");
  var slab=SableBodyProbe.toWorld(l,c.getBlockPos().east(4).getCenter());h.assertTrue(BodySphereQuery.query(l,slab.add(0,.35,0),eye,.2).isEmpty(),"Sphere hit air above a slab");
  h.assertTrue(BodySphereQuery.query(l,center,eye,.3).size()==1,"Sphere missed real core collision");h.succeed();});}
 @GameTest(template="combat",batch="spell_sphere_wall_armor")
 public static void SphereDamageRespectsWallsArmorAndInvalidInputs(GameTestHelper h){var c=sparse(h);var armor=c.modules().add("armor");armor.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));armor.edit("damageModifier",-50,"");h.runAfterDelay(4,()->{
  var l=h.getLevel();var center=SableBodyProbe.toWorld(l,c.getBlockPos().getCenter());var origin=center.add(0,0,-2);var caster=TestActors.creative(l);caster.setPos(origin);float before=c.health().current();
  h.assertTrue(BodyCombatApi.sphere(caster,origin,origin,3,d->10)==1&&c.health().current()==before-5,"Sphere bypassed part armor or hit multiple times");
  var wall=BlockPos.containing(center.add(0,0,-1));l.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());before=c.health().current();
  h.assertTrue(BodyCombatApi.sphere(caster,origin,origin,3,d->10)==0&&c.health().current()==before,"Sphere hit through earlier terrain");l.setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());
  h.assertTrue(BodyCombatApi.sphere(caster,origin,origin,Double.NaN,d->10)==0&&BodyCombatApi.sphere(caster,origin,origin,257,d->10)==0&&BodyCombatApi.sphere(caster,null,origin,3,d->10)==0&&BodyCombatApi.sphere(caster,origin,origin.add(0,0,270),3,d->10)==0&&BodyCombatApi.sphere(caster,origin,origin,3,d->Double.NaN)==0,"Invalid sphere inputs caused damage");h.succeed();});}
 @GameTest(template="combat",batch="spell_terrain_identity")
 public static void TerrainSuppressionAppliesOnlyToTheMarkedExplosion(GameTestHelper h){var c=sparse(h);h.runAfterDelay(4,()->{var l=h.getLevel();var point=SableBodyProbe.toWorld(l,c.getBlockPos().getCenter()).add(0,0,-2);float before=c.health().current();
  var first=new Explosion(l,null,point.x,point.y,point.z,2,false,Explosion.BlockInteraction.KEEP);BodyExplosionDamage.terrainOnly(first);first.explode();h.assertTrue(c.health().current()==before,"Terrain-only explosion still damaged core");
  var second=new Explosion(l,null,point.x,point.y,point.z,2,false,Explosion.BlockInteraction.KEEP);second.explode();h.assertTrue(c.health().current()<before,"Unrelated vanilla explosion was suppressed");h.succeed();});}
}
