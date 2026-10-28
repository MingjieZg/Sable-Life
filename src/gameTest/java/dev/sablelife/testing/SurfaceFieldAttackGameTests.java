package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.attack.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_surface_tests") @PrefixGameTestTemplate(false)
public final class SurfaceFieldAttackGameTests {
    private static HeartBlockEntity core(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,10,1));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());var c=(HeartBlockEntity)l.getBlockEntity(p);c.health().activate();dev.sablelife.ownership.BodyOwnership.get(l).bind(p,c.creatureId());dev.sablelife.ownership.LoadedCoreIndex.observe(c);return c;}
    private static Vec3 floor(GameTestHelper h){var p=h.absolutePos(new BlockPos(7,5,7));for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)h.getLevel().setBlockAndUpdate(p.offset(x,0,z),Blocks.STONE.defaultBlockState());return p.getBottomCenter().add(0,1,0);}
    private static VirtualModule weapon(HeartBlockEntity c,String type){var area=c.modules().add("surface_field");area.edit("fieldRadius",.5,"");var m=c.modules().add(type);m.impactField(area.id());return m;}
    private static ModuleArrow arrow(GameTestHelper h,HeartBlockEntity c,VirtualModule m,Vec3 p,Vec3 velocity){var a=new ModuleArrow(h.getLevel(),p.x,p.y,p.z,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));a.configure(m);AttackHitCredit.attach(a,c,m);a.setBaseDamage(1);a.setDeltaMovement(velocity);return a;}
    private static LivingEntity pig(GameTestHelper h,Vec3 p){var e=EntityType.PIG.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setPos(p);h.getLevel().addFreshEntity(e);return e;}
    @GameTest(template="combat") public static void gunCollisionCreatesAreaAfterProjectileReload(GameTestHelper h){
        var c=core(h);var p=floor(h);var m=weapon(c,"gun");var s=new ModuleShot(h.getLevel(),p.add(0,1,0),new Vec3(0,-2,0),m,c);var nbt=s.saveWithoutId(new net.minecraft.nbt.CompoundTag());var loaded=new ModuleShot(SableLife.MODULE_SHOT.get(),h.getLevel());loaded.load(nbt);h.getLevel().addFreshEntity(loaded);loaded.tick();
        h.assertTrue(loaded.isRemoved()&&c.modules().surfaceFields.count()==1,"Gun impact/config reload did not generate one field");h.succeed();
    }
    @GameTest(template="combat") public static void gunAtMaximumRangeWithoutCollisionDoesNotGenerateArea(GameTestHelper h){
        var c=core(h);var p=floor(h);var m=weapon(c,"gun");m.edit("range",1,"");var s=new ModuleShot(h.getLevel(),p.add(0,5,0),new Vec3(1,0,0),m,c);h.getLevel().addFreshEntity(s);s.tick();s.tick();h.assertTrue(s.isRemoved()&&c.modules().surfaceFields.count()==0,"Range expiry manufactured an impact");h.succeed();
    }
    @GameTest(template="combat") public static void arrowEmbeddingAndSavedEmbeddingDoNotRepeatArea(GameTestHelper h){
        var c=core(h);var p=floor(h);var m=weapon(c,"arrow");var a=arrow(h,c,m,p.add(0,1,0),new Vec3(0,-2,0));var saved=a.saveWithoutId(new net.minecraft.nbt.CompoundTag());var loaded=new ModuleArrow(SableLife.MODULE_ARROW.get(),h.getLevel());loaded.load(saved);h.getLevel().addFreshEntity(loaded);loaded.tick();
        h.assertTrue(c.modules().surfaceFields.count()==1,"Arrow impact/load lost the field");var stuck=loaded.saveWithoutId(new net.minecraft.nbt.CompoundTag());loaded.discard();c.modules().surfaceFields.clear();
        var again=new ModuleArrow(SableLife.MODULE_ARROW.get(),h.getLevel());again.load(stuck);h.getLevel().addFreshEntity(again);again.emitSurfaceField(new BlockHitResult(p,Direction.UP,BlockPos.containing(p.add(0,-.1,0)),false));again.tick();h.assertTrue(c.modules().surfaceFields.count()==0,"Embedded/reloaded arrow emitted another area");again.discard();h.succeed();
    }
    @GameTest(template="combat") public static void explosiveArrowCreatesAreaBeforeCanceledEmbedding(GameTestHelper h){
        var c=core(h);var p=floor(h);var m=weapon(c,"arrow");m.edit("explode",1,"");m.edit("blastDamage",0,"");var a=arrow(h,c,m,p.add(0,1,0),new Vec3(0,-2,0));ModuleBlast.configure(a,m);h.getLevel().addFreshEntity(a);a.tick();
        h.assertTrue(a.isRemoved()&&c.modules().surfaceFields.count()==1,"Explosive arrow bypassed/duplicated accepted impact field");h.succeed();
    }
    @GameTest(template="combat") public static void entityImpactsProjectUnderTargetAndNotOntoLowRoof(GameTestHelper h){
        var c=core(h);var p=floor(h);var m=weapon(c,"gun");var victim=pig(h,p);var roof=BlockPos.containing(p.add(0,3,0));h.getLevel().setBlockAndUpdate(roof,Blocks.STONE.defaultBlockState());
        var s=new ModuleShot(h.getLevel(),p.add(-2,.4,0),new Vec3(4,0,0),m,c);h.getLevel().addFreshEntity(s);s.tick();h.assertTrue(s.isRemoved()&&c.modules().surfaceFields.count()==1,"Entity hit did not create ground field");
        c.modules().surfaceFields.tick(c);h.assertTrue(victim.getHealth()<9,"Field was projected onto roof instead of target feet");h.succeed();
    }
    @GameTest(template="combat") public static void arrowEntityHitCreatesGroundField(GameTestHelper h){
        var c=core(h);var p=floor(h);var m=weapon(c,"arrow");var victim=pig(h,p);var a=arrow(h,c,m,p.add(-2,.4,0),new Vec3(4,0,0));h.getLevel().addFreshEntity(a);a.tick();
        h.assertTrue(c.modules().surfaceFields.count()==1&&victim.getHealth()<10,"Arrow entity collision did not produce field");a.discard();h.succeed();
    }
    @GameTest(template="combat") public static void impactSelectorServerRejectsNonAreaAndStaleRevision(GameTestHelper h){
        var c=body(h);var actor=TestActors.creative(h.getLevel());actor.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));var area=c.modules().add("surface_field");var other=c.modules().add("sound");
        var menu=new HeartDashboardMenu(1,actor.getInventory(),c);
        for(String type:ModuleSurfaceField.IMPACT_ATTACKS){var m=c.modules().add(type);int rev=c.modules().revision();
            h.assertTrue(!menu.action(actor,rev-1,m.id(),"edit","impactArea",1,""),"Stale selector edit accepted on "+type);
            h.assertTrue(!menu.action(actor,rev,m.id(),"edit","impactArea",2,""),"Non-area selector accepted on "+type);
            h.assertTrue(menu.action(actor,rev,m.id(),"edit","impactArea",1,"")&&area.id().equals(m.impactFieldId()),"Valid area selector rejected on "+type);
        }h.succeed();
    }
    @GameTest(template="combat") public static void disabledOrDeletedSourceCannotEmitFromInFlightProjectile(GameTestHelper h){
        var c=core(h);var p=floor(h);var m=weapon(c,"gun");var s=new ModuleShot(h.getLevel(),p.add(0,1,0),new Vec3(0,-2,0),m,c);m.edit("enabled",0,"");s.tick();h.assertTrue(c.modules().surfaceFields.count()==0,"Disabled source created an area");m.edit("enabled",1,"");var a=arrow(h,c,m,p.add(0,1,0),new Vec3(0,-2,0));c.modules().remove(m.id());a.emitSurfaceField(new BlockHitResult(p,Direction.UP,BlockPos.containing(p.add(0,-.1,0)),false));h.assertTrue(c.modules().surfaceFields.count()==0,"Deleted source created an area");h.succeed();
    }
    @GameTest(template="combat") public static void allSupportedAttackTypesKeepStableImpactReference(GameTestHelper h){
        for(String type:ModuleSurfaceField.IMPACT_ATTACKS){var m=new VirtualModule(type);var id=UUID.randomUUID();m.impactField(id);h.assertTrue(ModuleFields.find(type,"impactArea")!=null&&id.equals(VirtualModule.load(m.save()).impactFieldId()),"Missing persistent impact selector on "+type);h.assertTrue(ModulePreset.create(m.save()).impactFieldId()==null,"Portable preset retained external target on "+type);}
        for(String type:List.of("melee","sphere_blast","pulse","quake"))h.assertTrue(ModuleFields.find(type,"impactArea")==null,"Unsupported attack advertised impact fields");h.succeed();
    }
    private static HeartBlockEntity body(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,40,4));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());var b=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p),new dev.ryanhcode.sable.companion.math.BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());c.configure(100,60);return c;}
    private static void wall(GameTestHelper h,Vec3 at){var p=BlockPos.containing(at);for(int x=-3;x<=3;x++)for(int y=-3;y<=3;y++)h.getLevel().setBlockAndUpdate(p.offset(x,y,0),Blocks.STONE.defaultBlockState());}
    private static ModuleShot homingHit(GameTestHelper h,HeartBlockEntity c,VirtualModule m,LivingEntity victim,boolean reload){
        var center=victim.getBoundingBox().getCenter();var shot=ModuleShot.homing(h.getLevel(),center.add(0,0,-1.2),center,m,c,0);shot.setDeltaMovement(0,0,2);
        if(reload){var tag=shot.saveWithoutId(new net.minecraft.nbt.CompoundTag());shot=new ModuleShot(SableLife.MODULE_SHOT.get(),h.getLevel());shot.load(tag);}
        h.getLevel().addFreshEntity(shot);for(int i=0;i<8&&!shot.isRemoved();i++)shot.tick();h.assertTrue(shot.isRemoved(),"Homing projectile did not make real contact: target="+shot.homingTarget()+" hovering="+shot.hovering()+" position="+shot.position());return shot;
    }
    @GameTest(template="combat",batch="surface_homing_impacts",timeoutTicks=35) public static void homingVolleyRefreshesSamePatchWithoutStackingDamage(GameTestHelper h){
        var c=body(h);var m=weapon(c,"homing");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("target",1,"");m.edit("homingDeploy",0,"");m.edit("chargeTime",0,"");m.edit("damage",0,"");m.edit("explode",0,"");m.edit("speed",2,"");m.edit("homingTracking",1,"");m.edit("trigger",1,"");
        var config=c.modules().find(m.impactFieldId());config.edit("damage",2,"");config.edit("hitInterval",100,"");config.edit("fieldDuration",10,"");config.edit("trigger",2,"");
        var p=targetFloor(h,c,m);var victim=pig(h,p);c.tryActivate(TestActors.creative(h.getLevel()));
        homingHit(h,c,m,victim,true);c.modules().surfaceFields.tick(c);h.assertTrue(c.modules().surfaceFields.count()==1&&victim.getHealth()==8,"Initial homing impact did not create one damaging patch");
        for(int i=0;i<3;i++){homingHit(h,c,m,victim,false);c.modules().surfaceFields.tick(c);}
        h.assertTrue(c.modules().surfaceFields.count()==1&&victim.getHealth()==8,"Multiple homing hits stacked fields or reset damage clock");
        h.runAfterDelay(6,()->{victim.setPos(p);homingHit(h,c,m,victim,false);c.modules().surfaceFields.tick(c);h.assertTrue(victim.getHealth()==8,"Refresh added another hit inside interval");});
        h.runAfterDelay(11,()->{c.modules().surfaceFields.tick(c);h.assertTrue(c.modules().surfaceFields.count()==1,"Repeated homing hit did not refresh duration");});
        h.runAfterDelay(18,()->{c.modules().surfaceFields.tick(c);h.assertTrue(c.modules().surfaceFields.count()==0,"Refreshed homing area did not expire");m.edit("enabled",0,"");h.succeed();});
    }
    @GameTest(template="combat",batch="surface_slash_terrain",timeoutTicks=25) public static void slashLeavesFieldAtTerrainContact(GameTestHelper h){
        var c=body(h);var m=weapon(c,"slash");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("slashSpeed",80,"");m.edit("interval",100,"");var start=ModuleCombat.nozzle(c,m);wall(h,start.add(0,0,2));c.tryActivate(TestActors.creative(h.getLevel()));var slash=new ModuleSlash();slash.tick(c);
        h.assertTrue(c.modules().surfaceFields.count()==1,"Actual slash terrain collision did not leave area");m.edit("enabled",0,"");slash.clear();h.succeed();
    }
    @GameTest(template="combat",batch="surface_laser_terrain",timeoutTicks=25) public static void laserDoesNotEmitDuringChargeAndRefreshesActualWallContact(GameTestHelper h){
        var c=body(h);var m=weapon(c,"laser");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("chargeTime",3,"");m.edit("beamHits",10,"");m.edit("interval",100,"");wall(h,ModuleCombat.nozzle(c,m).add(0,0,3));c.tryActivate(TestActors.creative(h.getLevel()));var laser=new ModuleLaser();laser.tick(c);h.assertTrue(c.modules().surfaceFields.count()==0,"Charging laser created damage area");
        h.runAfterDelay(4,()->{laser.tick(c);h.assertTrue(c.modules().surfaceFields.count()==1,"Firing laser failed wall contact");});
        h.runAfterDelay(5,()->{laser.tick(c);h.assertTrue(c.modules().surfaceFields.count()==1,"Continuous laser stacked identical wall area");m.edit("enabled",0,"");laser.clear();h.succeed();});
    }
    private static Vec3 targetFloor(GameTestHelper h,HeartBlockEntity c,VirtualModule m){var mark=m.anchors().getFirst().world(c,false);var block=BlockPos.containing(mark.add(0,-1,3));for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)h.getLevel().setBlockAndUpdate(block.offset(x,0,z),Blocks.STONE.defaultBlockState());return block.getBottomCenter().add(0,1,0);}
    @GameTest(template="combat",batch="surface_slash_entity") public static void slashEntityContactProjectsActualContactToFloor(GameTestHelper h){
        var c=body(h);var m=weapon(c,"slash");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("slashSpeed",80,"");var p=targetFloor(h,c,m);var victim=pig(h,p);c.tryActivate(TestActors.creative(h.getLevel()));var slash=new ModuleSlash();slash.tick(c);
        h.assertTrue(victim.getHealth()<10&&c.modules().surfaceFields.count()==1,"Slash entity contact failed to generate ground area");m.edit("enabled",0,"");slash.clear();h.succeed();
    }
    @GameTest(template="combat",batch="slash_direct_burn") public static void slashIgnitesOnAcceptedHitWithoutAreaEvenAtZeroDamage(GameTestHelper h){
        var c=body(h);var m=weapon(c,"slash");m.impactField(null);m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("slashSpeed",80,"");m.edit("damage",0,"");m.edit("slashIgnite",1,"");m.edit("burn",4,"");var p=targetFloor(h,c,m);var victim=pig(h,p);
        var loaded=VirtualModule.load(m.save());h.assertTrue(loaded.value("slashIgnite")==1&&loaded.value("burn")==4,"Slash ignition settings lost on save");c.tryActivate(TestActors.creative(h.getLevel()));var slash=new ModuleSlash();slash.tick(c);
        h.assertTrue(victim.getHealth()==10&&victim.isOnFire()&&victim.getRemainingFireTicks()==80&&c.modules().surfaceFields.count()==0,"Direct slash ignition incorrectly requires area or direct damage");m.edit("enabled",0,"");slash.clear();h.succeed();
    }
    @GameTest(template="combat",batch="slash_burn_default") public static void slashIgnitionDefaultsOffAndDurationIsConditional(GameTestHelper h){
        var c=body(h);var m=weapon(c,"slash");m.impactField(null);m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("slashSpeed",80,"");m.edit("damage",0,"");m.edit("burn",5,"");var p=targetFloor(h,c,m);var victim=pig(h,p);c.tryActivate(TestActors.creative(h.getLevel()));var slash=new ModuleSlash();slash.tick(c);
        h.assertTrue(!victim.isOnFire()&&!SurfaceFieldSettings.visible(m,"burn"),"Disabled ignition still burns or exposes duration");m.edit("slashIgnite",1,"");h.assertTrue(SurfaceFieldSettings.visible(m,"burn"),"Enabled ignition hides burn duration");m.edit("enabled",0,"");slash.clear();h.succeed();
    }
    @GameTest(template="combat",batch="surface_laser_entity") public static void laserEntityContactCreatesAreaWithoutTerrainEndpoint(GameTestHelper h){
        var c=body(h);var m=weapon(c,"laser");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("target",1,"");m.edit("chargeTime",0,"");var p=targetFloor(h,c,m);var victim=pig(h,p);c.tryActivate(TestActors.creative(h.getLevel()));var laser=new ModuleLaser();laser.tick(c);
        h.assertTrue(victim.getHealth()<10&&c.modules().surfaceFields.count()==1,"Laser entity contact failed to create area");m.edit("enabled",0,"");laser.clear();h.succeed();
    }
    @GameTest(template="combat",batch="surface_laser_range") public static void laserRangeEndingJustBeforeWallDoesNotManufactureContact(GameTestHelper h){
        var c=body(h);var m=weapon(c,"laser");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("chargeTime",0,"");var start=ModuleCombat.nozzle(c,m);var wall=BlockPos.containing(start.add(0,0,4));wall(h,wall.getCenter());m.edit("beamLength",wall.getZ()-start.z-.1,"");c.tryActivate(TestActors.creative(h.getLevel()));var laser=new ModuleLaser();laser.tick(c);
        h.assertTrue(c.modules().surfaceFields.count()==0,"Laser spawned a field beyond its actual range");m.edit("enabled",0,"");laser.clear();h.succeed();
    }
    @GameTest(template="combat",batch="surface_ground_impact",timeoutTicks=30) public static void groundStrikeCreatesAreaOnlyWhenFallingStrikeLands(GameTestHelper h){
        var c=body(h);var m=weapon(c,"ground_strike");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("strikeMode",0,"");m.edit("strikeStart",2,"");m.edit("strikeRange",2,"");m.edit("warningTime",3,"");m.edit("strikeHeight",1,"");m.edit("strikeSpeed",1,"");m.edit("interval",100,"");
        var pos=BlockPos.containing(ModuleCombat.nozzle(c,m).add(0,-2,2));for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)h.getLevel().setBlockAndUpdate(pos.offset(x,0,z),Blocks.STONE.defaultBlockState());
        c.tryActivate(TestActors.creative(h.getLevel()));var strike=new ModuleGroundStrike();strike.tick(c);h.assertTrue(c.modules().surfaceFields.count()==0,"Ground warning created area before impact");
        h.runAfterDelay(7,()->{strike.tick(c);h.assertTrue(c.modules().surfaceFields.count()==1,"Ground strike landing did not generate one area: "+c.modules().surfaceFields.count());m.edit("enabled",0,"");strike.clear();h.succeed();});
    }
}
