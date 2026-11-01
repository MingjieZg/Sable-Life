package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.attack.ModuleDamage;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.LoadedCoreIndex;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.joml.Vector3d;
import java.util.*;

@GameTestHolder("sablelife_defense_tests") @PrefixGameTestTemplate(false)
public final class KnockbackResistanceGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h,int x){
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(x,15,4));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static net.minecraft.world.entity.LivingEntity target(GameTestHelper h,HeartBlockEntity c){
        c.tryActivate(TestActors.creative(h.getLevel()));LoadedCoreIndex.observe(c);
        return ModuleTargets.query(h.getLevel(),UUID.randomUUID(),LoadedCoreIndex.bounds(c).inflate(1),e->e.getUUID().equals(c.creatureId())).stream().filter(e->e instanceof ModuleTargets.LifeTarget).findFirst().orElseThrow();
    }
    @GameTest(template="combat",batch="knockback_settings")
    public static void ResistanceValidatesPermissionsPersistsAndCopies(GameTestHelper h){
        var c=fixture(h,2);var copy=fixture(h,7);var p=TestActors.creative(h.getLevel());p.setPos(ModuleArena.corePosition(c));var menu=new HeartSettingsMenu(1,p.getInventory(),c,4);
        h.assertTrue(c.knockbackResistance()==0&&menu.snapshot().getDouble("knockbackResistance")==0,"Default changed old combat");
        h.assertTrue(menu.action(p,"knockbackResistance",75,"")&&menu.snapshot().getDouble("knockbackResistance")==75,"Menu failed to apply/sync resistance");
        for(double bad:new double[]{-1,100.01,Double.NaN,Double.POSITIVE_INFINITY})h.assertTrue(!menu.action(p,"knockbackResistance",bad,""),"Invalid resistance accepted");
        var saved=c.saveWithoutMetadata(h.getLevel().registryAccess());var loaded=new HeartBlockEntity(c.getBlockPos(),c.getBlockState());loaded.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(loaded.knockbackResistance()==75&&copy.pasteConfiguration(p,c.copyConfiguration())&&copy.knockbackResistance()==75,"Save/configuration copy lost resistance");
        saved.remove("KnockbackResistance");loaded.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(loaded.knockbackResistance()==0,"Legacy core gained resistance");
        saved.putDouble("KnockbackResistance",Double.NaN);loaded.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(loaded.knockbackResistance()==0,"Invalid saved resistance not sanitized");
        c.tryActivate(p);h.assertTrue(menu.action(p,"knockbackResistance",100,""),"Live editing rejected");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);h.assertTrue(!menu.action(p,"knockbackResistance",0,"")&&c.knockbackResistance()==100,"Survival actor changed resistance");h.succeed();
    }
    @GameTest(template="combat",batch="knockback_native")
    public static void NativeMeleeImpulseScalesButDoesNotEraseExistingMotionOrDamage(GameTestHelper h){
        var c=fixture(h,4);var victim=target(h,c);var author=TestActors.creative(h.getLevel());var body=LinearMovementGeometry.capture(c).getFirst().body();var pipeline=SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();
        Vector3d full=null;
        for(double resistance:new double[]{0,50,100}){
            c.editCore(author,"knockbackResistance",resistance,"");pipeline.resetVelocity(body);body.latestLinearVelocity.zero();victim.setOnGround(true);victim.knockback(10,-1,0);
            var actual=pipeline.getLinearVelocity(body,new Vector3d());if(full==null)full=new Vector3d(actual);
            h.assertTrue(full.x>1&&actual.distance(new Vector3d(full).mul(1-resistance/100))<1e-5,"Attack impulse did not scale including vertical component: "+resistance+" / "+actual+" full="+full);
        }
        pipeline.resetVelocity(body);var initial=new Vector3d(2,-3,4);pipeline.addLinearAndAngularVelocity(body,initial,new Vector3d());body.latestLinearVelocity.set(initial);victim.knockback(100,-1,0);
        h.assertTrue(pipeline.getLinearVelocity(body,new Vector3d()).distance(initial)<1e-5,"100% resistance erased existing movement");
        victim.push(.1,.2,.3);h.assertTrue(pipeline.getLinearVelocity(body,new Vector3d()).distance(new Vector3d(initial).add(2,4,6))<1e-5,"Resistance reduced non-attack movement/attraction");
        h.assertTrue(victim.hurt(ModuleDamage.physical(h.getLevel()),10)&&c.health().current()==90,"Resistance incorrectly blocked damage");h.succeed();
    }
    @GameTest(template="combat",batch="knockback_quake")
    public static void QuakeAttackImpulseScalesForLifeOnly(GameTestHelper h){
        var c=fixture(h,4);var victim=target(h,c);var author=TestActors.creative(h.getLevel());var body=LinearMovementGeometry.capture(c).getFirst().body();var pipeline=SubLevelPhysicsSystem.get(h.getLevel()).getPipeline();var impulse=new Vec3(2,3,4);
        for(double resistance:new double[]{0,50,100}){
            c.editCore(author,"knockbackResistance",resistance,"");pipeline.resetVelocity(body);body.latestLinearVelocity.zero();ModuleTargets.attackPush(victim,impulse);
            h.assertTrue(pipeline.getLinearVelocity(body,new Vector3d()).distance(new Vector3d(40,60,80).mul(1-resistance/100))<1e-5,"Quake launch/push failed resistance "+resistance);
        }
        var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());cow.setDeltaMovement(Vec3.ZERO);ModuleTargets.attackPush(cow,impulse);h.assertTrue(cow.getDeltaMovement().distanceToSqr(impulse)<1e-10,"Life resistance changed normal entity push");h.succeed();
    }
}
