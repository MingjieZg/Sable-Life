package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.attack.ModuleShot;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_surface_tests") @PrefixGameTestTemplate(false)
public final class SurfaceFieldGameTests {
    @GameTest(template="combat",timeoutTicks=30,batch="surface_flame_density_packets") public static void defaultDensityActuallySendsFlameParticlesToViewer(GameTestHelper h){
        var c=core(h);var p=floor(h);var area=c.modules().add("surface_field");area.editParticle("remove","minecraft:effect");area.editParticle("add","minecraft:flame");var attack=c.modules().add("mortar");attack.impactField(area.id());
        var viewer=TestActors.creative(h.getLevel());viewer.setPos(p.add(0,2,0));var packets=new ArrayList<net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket>();
        viewer.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),viewer,net.minecraft.server.network.CommonListenerCookie.createInitial(viewer.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket particles)packets.add(particles);}
        };h.getLevel().players().add(viewer);
        long delay=4-Math.floorMod(h.getLevel().getGameTime(),4);
        h.runAfterDelay(delay,()->{try{
            var runtime=new ModuleSurfaceField();runtime.impact(c,attack,p,new Vec3(0,1,0),new Vec3(0,0,1));runtime.tick(c);
            h.assertTrue(runtime.count()==1&&packets.size()==24,"Default24 flame did not send24 real particle packets: "+packets.size()+"; fields="+runtime.count()+"; tick="+h.getLevel().getGameTime());
            h.assertTrue(packets.stream().allMatch(packet->packet.getParticle().getType()==net.minecraft.core.particles.ParticleTypes.FLAME),"Wrong particle type sent");h.succeed();
        }finally{h.getLevel().players().remove(viewer);}});
    }
    @GameTest(template="combat") public static void particleDensityPersistsAndRespectsVisualBudget(GameTestHelper h){
        var m=new VirtualModule("surface_field");h.assertTrue(m.value("density")==24&&ModuleSurfaceField.visualSampleCount(m,100,192)==24,"Default particle density changed old appearance");
        m.edit("density",80,"");h.assertTrue(ModuleSurfaceField.visualSampleCount(m,100,192)==80&&ModuleSurfaceField.visualSampleCount(m,10,192)==10,"Density did not control samples or exceeded geometry");
        m.editParticle("add","minecraft:flame");h.assertTrue(ModuleSurfaceField.visualSampleCount(m,100,30)==15,"Multiple particle types exceeded shared visual budget");
        h.assertTrue(VirtualModule.load(m.save()).value("density")==80&&ModulePreset.create(m.save()).value("density")==80,"Density lost on save or preset");
        var legacy=m.save();legacy.getCompound("Settings").remove("density");h.assertTrue(VirtualModule.load(legacy).value("density")==24,"Legacy config did not retain default24");
        m.edit("density",0,"");h.assertTrue(ModuleSurfaceField.visualSampleCount(m,100,192)==0,"Zero density emitted particles");
        m.edit("density",128,"");h.assertTrue(m.value("density")==128&&dev.sablelife.client.NumericWheelSteps.module("surface_field","density",1,0)==1,"Density upper bound/wheel wrong");h.succeed();
    }
    @GameTest(template="combat") public static void zeroParticleDensityStillDamagesTargets(GameTestHelper h){
        var c=core(h);var p=floor(h);var area=c.modules().add("surface_field");area.edit("density",0,"");area.edit("damage",2,"");var attack=c.modules().add("mortar");attack.impactField(area.id());var victim=pig(h,p);float before=victim.getHealth();
        c.modules().surfaceFields.impact(c,attack,p,new Vec3(0,1,0),new Vec3(0,0,1));c.modules().surfaceFields.tick(c);h.assertTrue(victim.getHealth()==before-2,"Disabling particles disabled damage");victim.discard();h.succeed();
    }
    private static HeartBlockEntity core(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,9,1));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        var c=(HeartBlockEntity)level.getBlockEntity(pos);c.health().activate();
        dev.sablelife.ownership.BodyOwnership.get(level).bind(pos,c.creatureId());dev.sablelife.ownership.LoadedCoreIndex.observe(c);return c;
    }
    private static Vec3 floor(GameTestHelper h){
        var center=h.absolutePos(new BlockPos(7,5,7));for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)h.getLevel().setBlockAndUpdate(center.offset(x,0,z),Blocks.STONE.defaultBlockState());
        return center.getBottomCenter().add(0,1,0);
    }
    private static LivingEntity pig(GameTestHelper h,Vec3 p){var e=EntityType.PIG.create(h.getLevel());e.setNoAi(true);e.setNoGravity(true);e.setPos(p);h.getLevel().addFreshEntity(e);return e;}
    @GameTest(template="combat") public static void rectangleRotatesRelativeToIncomingAndForwardStartsAtImpact(GameTestHelper h){
        var m=new VirtualModule("surface_field");m.edit("fieldShape",1,"");m.edit("fieldLength",8,"");m.edit("fieldWidth",2,"");m.edit("fieldPlacement",1,"");
        var f=SurfaceFieldGeometry.frame(m,Vec3.ZERO,new Vec3(0,1,0),new Vec3(0,0,1));
        h.assertTrue(f.contains(new Vec3(0,0,7.9))&&!f.contains(new Vec3(0,0,-.1))&&!f.contains(new Vec3(1.1,0,3)),"Forward rectangle limits wrong");
        m.edit("fieldAngle",90,"");f=SurfaceFieldGeometry.frame(m,Vec3.ZERO,new Vec3(0,1,0),new Vec3(0,0,1));
        h.assertTrue(f.contains(new Vec3(7.9,0,0))&&!f.contains(new Vec3(0,0,3)),"90 degree transverse strip did not rotate");h.succeed();
    }
    @GameTest(template="combat") public static void verticalImpactHasStableBasisAndCircleIgnoresRectangleDimensions(GameTestHelper h){
        var m=new VirtualModule("surface_field");m.edit("fieldRadius",2,"");m.edit("fieldLength",32,"");
        var f=SurfaceFieldGeometry.frame(m,Vec3.ZERO,new Vec3(0,1,0),new Vec3(0,-1,0));
        h.assertTrue(f.contains(new Vec3(1,0,1))&&!f.contains(new Vec3(2,0,2))&&Math.abs(f.forward().length()-1)<1e-6,"Degenerate frame or radius polluted by length");h.succeed();
    }
    @GameTest(template="combat") public static void surfaceSamplesConformToStepAndWallWithoutFillingAir(GameTestHelper h){
        var p=floor(h);var l=h.getLevel();l.setBlockAndUpdate(BlockPos.containing(p.add(1,0,0)),Blocks.STONE.defaultBlockState());
        var m=new VirtualModule("surface_field");m.edit("fieldRadius",2,"");var f=SurfaceFieldGeometry.frame(m,p,new Vec3(0,1,0),new Vec3(0,0,1));var points=SurfaceFieldGeometry.samples(l,f);
        h.assertTrue(points.stream().anyMatch(v->v.y>p.y+.9)&&points.stream().anyMatch(v->Math.abs(v.y-p.y)<.1),"Area did not follow a one-block step");
        var wall=BlockPos.containing(p.add(0,3,0));for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)l.setBlockAndUpdate(wall.offset(x,y,0),Blocks.STONE.defaultBlockState());
        f=SurfaceFieldGeometry.frame(m,wall.getCenter().add(0,0,.5),new Vec3(0,0,1),new Vec3(0,0,-1));points=SurfaceFieldGeometry.samples(l,f);
        h.assertTrue(!points.isEmpty()&&points.stream().allMatch(v->Math.abs(v.z-(wall.getZ()+1.035))<.01),"Wall field is not on surface");h.succeed();
    }
    @GameTest(template="combat") public static void referenceSurvivesSaveCopyReorderButPortablePresetUnbinds(GameTestHelper h){
        var modules=new HeartModules();var mortar=modules.add("mortar");var config=modules.add("surface_field");mortar.impactField(config.id());
        var loaded=VirtualModule.load(mortar.save());h.assertTrue(config.id().equals(loaded.impactFieldId()),"Saved UUID lost");
        var copy=modules.copy(mortar.id());modules.reorder(config.id(),-1);h.assertTrue(config.id().equals(copy.impactFieldId()),"Copy/reorder changed target");
        h.assertTrue(ModulePreset.create(mortar.save()).impactFieldId()==null,"Cross-world preset retained foreign reference");
        h.assertTrue(VirtualModule.load(config.save()).particles().size()==1,"Default particles duplicated on load");config.editParticle("remove","minecraft:effect");h.assertTrue(VirtualModule.load(config.save()).particles().isEmpty(),"Empty particle selection reappeared on load");
        h.assertTrue(!MoveSet.TARGETS.contains("surface_field")&&ModuleFields.forType("mortar").stream().anyMatch(f->f.key().equals("impactArea")),"Missing authoring integration");h.succeed();
    }
    @GameTest(template="combat") public static void modeVisibilityRetainsIndependentCircleAndRectangleSettings(GameTestHelper h){
        var m=new VirtualModule("surface_field");h.assertTrue(SurfaceFieldSettings.visible(m,"fieldRadius")&&!SurfaceFieldSettings.visible(m,"fieldLength"),"Circle mode visibility wrong");m.edit("fieldShape",1,"");h.assertTrue(!SurfaceFieldSettings.visible(m,"fieldRadius")&&SurfaceFieldSettings.visible(m,"fieldAngle"),"Rectangle mode visibility wrong");
        h.assertTrue(!SurfaceFieldSettings.visible(m,"burn"),"Disabled burning exposes duration");m.edit("fieldBurn",1,"");h.assertTrue(SurfaceFieldSettings.visible(m,"burn"),"Enabled burning hides duration");
        for(var type:List.of("mortar","flame"))h.assertTrue(ModuleFields.forType(type).stream().noneMatch(f->f.key().startsWith("impactFire")),"Duplicate fire settings remain");h.succeed();
    }
    @GameTest(template="combat") public static void legacyTargetAndMarkModesCannotReleaseStandaloneDamage(GameTestHelper h){
        var c=core(h);var p=floor(h);pig(h,p);var old=new VirtualModule("surface_field");var saved=old.save();saved.getCompound("Settings").putDouble("fieldRelease",2);saved.getCompound("Settings").putDouble("castTargetRange",128);saved.putBoolean("Invalidated",true);saved.putBoolean("Detached",true);
        var loaded=VirtualModule.load(saved);h.assertTrue(loaded.value("fieldRelease")==0&&loaded.coreModule()&&HeartModules.status(loaded,c).equals("valid")&&!TargetCastOrigin.targeted(loaded),"Legacy mode/Mark still controls template validity");
        var template=c.modules().addPreset(saved);c.modules().surfaceFields.tick(c);h.assertTrue(c.modules().surfaceFields.count()==0,"Old on-target setting still spawned early area");
        h.assertTrue(!MarkingSessions.start(TestActors.creative(h.getLevel()),c,template)&&!MoveSet.TARGETS.contains("surface_field")&&!ModuleHitCounter.ATTACKS.contains("surface_field"),"Template still advertises independent activation");
        var move=c.modules().add("move_set");move.moveSet().edit("add",0,"",c.modules());h.assertTrue(!move.moveSet().edit("action_add",0,"0|"+template.id(),c.modules()),"Move Set can still directly release template");
        for(String key:List.of("castTargetRange","targetMultiple","interval"))h.assertTrue(ModuleFields.find("surface_field",key)==null,"Removed release field remains: "+key);
        h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=40) public static void repeatedContactsRefreshWithoutStackingDamageAndAreaSurvivesTriggerEnd(GameTestHelper h){
        var c=core(h);var p=floor(h);var config=c.modules().add("surface_field");config.edit("fieldRadius",2,"");config.edit("hitInterval",10,"");config.edit("damage",2,"");config.edit("fieldDuration",20,"");var attack=c.modules().add("mortar");attack.impactField(config.id());attack.edit("trigger",1,"");var target=pig(h,p.add(.5,0,0));
        for(int i=0;i<10;i++)c.modules().surfaceFields.impact(c,attack,p,new Vec3(0,1,0),new Vec3(0,-1,0));c.modules().surfaceFields.tick(c);
        h.assertTrue(c.modules().surfaceFields.count()==1&&target.getHealth()==8,"Repeated contact stacked damage or failed initial hit: "+target.getHealth());
        h.runAfterDelay(5,()->{c.modules().surfaceFields.tick(c);h.assertTrue(target.getHealth()==8,"Damage cadence ignored");});
        h.runAfterDelay(11,()->{c.modules().surfaceFields.tick(c);h.assertTrue(target.getHealth()==6,"Area stopped when attack trigger was off");});
        h.runAfterDelay(22,()->{c.modules().surfaceFields.tick(c);h.assertTrue(c.modules().surfaceFields.count()==0,"Area failed to expire");h.succeed();});
    }
    @GameTest(template="combat") public static void burningIsOptionalWithinOneAreaAndNeverPlacesRealFire(GameTestHelper h){
        var c=core(h);var p=floor(h);var m=c.modules().add("flame");var area=c.modules().add("surface_field");area.edit("fieldBurn",1,"");area.edit("damage",0,"");m.impactField(area.id());var target=pig(h,p);c.modules().surfaceFields.impact(c,m,p,new Vec3(0,1,0),new Vec3(1,0,0));c.modules().surfaceFields.tick(c);
        h.assertTrue(c.modules().surfaceFields.count()==1&&target.isOnFire(),"Area burning checkbox did not ignite");
        h.assertTrue(h.getLevel().getBlockState(BlockPos.containing(p)).isAir(),"Real fire was placed");
        m.edit("enabled",0,"");c.modules().surfaceFields.tick(c);h.assertTrue(c.modules().surfaceFields.count()==0,"Disabled source left live areas");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=90) public static void burningRefreshesBetweenDamageHitsAndExpiresAfterLeaving(GameTestHelper h){
        var c=core(h);var runtime=new ModuleSurfaceField();var p=floor(h);var area=c.modules().add("surface_field");area.edit("fieldBurn",1,"");area.edit("burn",3,"");area.edit("damage",0,"");area.edit("hitInterval",100,"");var m=c.modules().add("flame");m.impactField(area.id());var target=pig(h,p);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);
        runtime.impact(c,m,p,new Vec3(0,1,0),new Vec3(1,0,0));runtime.tick(c);
        h.runAfterDelay(5,()->{target.setRemainingFireTicks(1);runtime.tick(c);h.assertTrue(target.getRemainingFireTicks()>=59,"Burn refresh incorrectly waits for next damage interval");target.setPos(p.add(12,0,0));});
        h.runAfterDelay(8,()->{runtime.tick(c);h.assertTrue(target.isOnFire()&&target.getRemainingFireTicks()<60,"Residual burning stopped or kept refreshing outside area");});
        h.runAfterDelay(70,()->{runtime.tick(c);h.assertTrue(!target.isOnFire(),"Burning did not expire after leaving");h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=30) public static void regionDamageIgnoresExistingInvulnerabilityAndRecentFireDamage(GameTestHelper h){
        var c=core(h);var runtime=new ModuleSurfaceField();var p=floor(h);var area=c.modules().add("surface_field");area.edit("fieldBurn",1,"");area.edit("damage",2,"");area.edit("hitInterval",5,"");var m=c.modules().add("mortar");m.impactField(area.id());var target=pig(h,p);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);target.invulnerableTime=100;
        runtime.impact(c,m,p,new Vec3(0,1,0),new Vec3(1,0,0));runtime.tick(c);h.assertTrue(target.getHealth()==98,"Area obeyed ordinary invulnerability frames");
        h.runAfterDelay(5,()->{target.invulnerableTime=0;target.hurt(h.getLevel().damageSources().onFire(),1);float before=target.getHealth();runtime.tick(c);h.assertTrue(Math.abs(target.getHealth()-(before-2))<.01,"Recent burning damage blocked scheduled area damage");h.succeed();});
    }
    @GameTest(template="combat") public static void actualMortarCollisionEmitsOneAreaAndReloadPreservesImpactSettings(GameTestHelper h){
        var c=core(h);var p=floor(h);var config=c.modules().add("surface_field");var mortar=c.modules().add("mortar");mortar.impactField(config.id());mortar.edit("damage",0,"");mortar.edit("explode",0,"");
        var shot=new ModuleShot(h.getLevel(),p.add(0,1,0),new Vec3(0,-2,0),mortar,c);var tag=shot.saveWithoutId(new net.minecraft.nbt.CompoundTag());var loaded=new ModuleShot(SableLife.MODULE_SHOT.get(),h.getLevel());loaded.load(tag);h.getLevel().addFreshEntity(loaded);loaded.tick();
        h.assertTrue(loaded.isRemoved()&&c.modules().surfaceFields.count()==1,"Actual shell impact or projectile save lost area reference");h.succeed();
    }
    @GameTest(template="combat") public static void airborneAndOutsideTargetsDoNotTakeGroundDamage(GameTestHelper h){
        var c=core(h);var p=floor(h);var config=c.modules().add("surface_field");config.edit("fieldRadius",2,"");var attack=c.modules().add("mortar");attack.impactField(config.id());var above=pig(h,p.add(0,3,0));var outside=pig(h,p.add(4,0,0));
        c.modules().surfaceFields.impact(c,attack,p,new Vec3(0,1,0),new Vec3(0,-1,0));c.modules().surfaceFields.tick(c);h.assertTrue(above.getHealth()==10&&outside.getHealth()==10,"Ground area hit airborne/outside entity");h.succeed();
    }
    @GameTest(template="combat") public static void zeroDamageAreaAppliesDebuffAndGlobalStopClearsIt(GameTestHelper h){
        var c=core(h);var p=floor(h);var config=c.modules().add("surface_field");config.edit("damage",0,"");config.editEffect("add","minecraft:slowness",0);var attack=c.modules().add("mortar");attack.impactField(config.id());var target=pig(h,p);
        c.modules().surfaceFields.impact(c,attack,p,new Vec3(0,1,0),new Vec3(1,0,0));c.modules().surfaceFields.tick(c);
        var slow=net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(net.minecraft.resources.ResourceLocation.parse("minecraft:slowness")).orElseThrow();h.assertTrue(target.getHealth()==10&&target.hasEffect(slow),"Debuff-only field failed");
        c.wireless().master(false);c.modules().surfaceFields.tick(c);h.assertTrue(c.modules().surfaceFields.count()==0,"Global stop retained an active region");h.succeed();
    }
    @GameTest(template="combat") public static void activeAreaCountIsBoundedUnderImpactFlood(GameTestHelper h){
        var c=core(h);var p=floor(h);var config=c.modules().add("surface_field");config.edit("fieldRadius",.25,"");var attack=c.modules().add("mortar");attack.impactField(config.id());
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)c.modules().surfaceFields.impact(c,attack,p.add(x*1.1,0,z*1.1),new Vec3(0,1,0),new Vec3(0,-1,0));
        h.assertTrue(c.modules().surfaceFields.count()==ModuleSurfaceField.LIMIT,"Impact flood exceeded or never reached bounded area count: "+c.modules().surfaceFields.count());c.modules().clearRuntime();h.assertTrue(c.modules().surfaceFields.count()==0,"Unload/reset retained fields");h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30) public static void nearbyImpactRefreshesExpirationWithoutReplacingOriginalPatch(GameTestHelper h){
        var c=core(h);var p=floor(h);var config=c.modules().add("surface_field");config.edit("fieldDuration",10,"");var attack=c.modules().add("mortar");attack.impactField(config.id());
        c.modules().surfaceFields.impact(c,attack,p,new Vec3(0,1,0),new Vec3(0,-1,0));
        h.runAfterDelay(6,()->c.modules().surfaceFields.impact(c,attack,p.add(.2,0,0),new Vec3(0,1,0),new Vec3(0,-1,0)));
        h.runAfterDelay(11,()->{c.modules().surfaceFields.tick(c);h.assertTrue(c.modules().surfaceFields.count()==1,"Refresh did not extend original duration");});
        h.runAfterDelay(18,()->{c.modules().surfaceFields.tick(c);h.assertTrue(c.modules().surfaceFields.count()==0,"Refreshed area never expired");h.succeed();});
    }
    @GameTest(template="combat",batch="surface_flame_native",timeoutTicks=30) public static void actualFlameFrontLeavesWallPatchAndRefreshesIt(GameTestHelper h){
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(4,40,4));l.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        var body=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(l,pos,List.of(pos),new dev.ryanhcode.sable.companion.math.BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ()));
        var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());c.configure(100,60);var config=c.modules().add("surface_field");config.edit("fieldRadius",1,"");var flame=c.modules().add("flame");flame.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));flame.edit("flameSpeed",4,"");flame.impactField(config.id());c.tryActivate(TestActors.creative(l));
        h.runAfterDelay(3,()->{var start=ModuleCombat.nozzle(c,flame);var wall=BlockPos.containing(start.add(0,0,2));for(int x=-2;x<=2;x++)for(int y=-2;y<=2;y++)l.setBlockAndUpdate(wall.offset(x,y,0),Blocks.STONE.defaultBlockState());
            var combat=new ModuleCombat();for(int delay=1;delay<=3;delay++)h.runAfterDelay(delay,()->{
                combat.tick(c);
            });
            h.runAfterDelay(4,()->{h.assertTrue(c.modules().surfaceFields.count()==1,"Actual flame did not create/refresh one wall patch: "+c.modules().surfaceFields.count()+" status="+HeartModules.status(flame,c));c.modules().clearRuntime();h.succeed();});});
    }
}

