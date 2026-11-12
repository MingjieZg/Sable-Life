package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.attack.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@GameTestHolder("sablelife_blast_tests") @PrefixGameTestTemplate(false)
public final class ProjectileItemRotationGameTests {
    @GameTest(template="combat",batch="projectile_tracer")
    public static void TracerPreservesLegacyParticlesAndRoundTripsOnEveryProjectile(GameTestHelper h) {
        var world = h.getLevel();
        var pos = h.absolutePos(new BlockPos(1, 6, 1));
        world.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        var core = (HeartBlockEntity) world.getBlockEntity(pos);
        var expected = new ProjectileTracer(0x2B75E0, 6F, .12F);
        for (String type : java.util.List.of("arrow", "gun", "mortar", "homing")) {
            var module = new VirtualModule(type);
            module.editParticle("add", "minecraft:flame");
            module = VirtualModule.load(module.save());
            h.assertTrue(ProjectileTracer.from(module) == null && ProjectileTracer.visible(module, "particles"),
                    "Legacy projectile no longer defaults to particles: " + type);
            module.edit("projectileTrailStyle", 1, "");
            module.edit("projectileTrailColorRgb", 1, "");
            module.edit("projectileTrailColorRed", 0x2B, "");
            module.edit("projectileTrailColorGreen", 0x75, "");
            module.edit("projectileTrailColorBlue", 0xE0, "");
            module.edit("projectileTrailLength", 6, "");
            module.edit("projectileTrailWidth", .12, "");
            module = VirtualModule.load(module.save());
            h.assertTrue(expected.equals(ProjectileTracer.from(module)), "Tracer settings did not reload: " + type);
            h.assertTrue(!ProjectileTracer.visible(module, "particles"), "Particle picker visible in tracer mode: " + type);
            net.minecraft.world.entity.Entity projectile;
            if (type.equals("arrow")) {
                var arrow = new ModuleArrow(SableLife.MODULE_ARROW.get(), world);
                arrow.configure(module);
                projectile = arrow;
            } else if (type.equals("homing")) {
                projectile = ModuleShot.homing(world, pos.getCenter(), pos.getCenter(), module, core, 0);
            } else {
                projectile = new ModuleShot(world, pos.getCenter(), new Vec3(1, 0, 0), module, core);
            }
            var saved = projectile.saveWithoutId(new CompoundTag());
            h.assertTrue(expected.equals(ProjectileTracer.read(saved)), "Projectile did not capture tracer: " + type);
            var restored = projectile.getType().create(world);
            restored.load(saved);
            h.assertTrue(expected.equals(ProjectileTracer.read(restored.saveWithoutId(new CompoundTag()))),
                    "Projectile lost tracer after reload: " + type);
            saved.remove("ProjectileTracer");
            restored.load(saved);
            h.assertTrue(ProjectileTracer.read(restored.saveWithoutId(new CompoundTag())) == null, "Legacy projectile enabled tracer: " + type);
            module.edit("projectileTrailStyle", 0, "");
            h.assertTrue(module.particles().contains("minecraft:flame"), "Switching modes lost particles: " + type);
        }
        h.succeed();
    }
    @GameTest(template="combat",batch="projectile_item_rotation")
    public static void RotationSurvivesLaunchMetadataReloadAndModuleCopy(GameTestHelper h){
        var world=h.getLevel();var pos=h.absolutePos(new BlockPos(1,6,1));world.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        var core=(HeartBlockEntity)world.getBlockEntity(pos);var velocity=new Vec3(.3,.7,-1.4);
        for(String type:java.util.List.of("arrow","gun","mortar")){
            var module=new VirtualModule(type);
            h.assertTrue(ProjectileItemPose.from(module).lengthSquared()==0,"Legacy module acquired rotation: "+type);
            h.assertTrue(!module.edit("projectileRotationX",Double.NaN,""),"NaN rotation accepted");
            module.edit("projectileRotationX",25,"");module.edit("projectileRotationY",90,"");module.edit("projectileRotationZ",-135,"");
            module.projectileItem(new ItemStack(Items.DIAMOND_SWORD),world.registryAccess());
            module=VirtualModule.load(module.save());
            var expected=new Vector3f(25,90,-135);
            h.assertTrue(ProjectileItemPose.from(module).equals(expected),"Module copy lost rotation: "+type);
            if(type.equals("arrow")){
                var arrow=new ModuleArrow(SableLife.MODULE_ARROW.get(),world);arrow.setDeltaMovement(velocity);arrow.configure(module);
                var remote=new ModuleArrow(SableLife.MODULE_ARROW.get(),world);remote.getEntityData().assignValues(arrow.getEntityData().getNonDefaultValues());
                h.assertTrue(remote.itemRotation().equals(expected),"Arrow metadata lost rotation");
                var saved=arrow.saveWithoutId(new CompoundTag());var restored=new ModuleArrow(SableLife.MODULE_ARROW.get(),world);restored.load(saved);
                h.assertTrue(restored.itemRotation().equals(expected)&&restored.getDeltaMovement().equals(velocity),"Arrow reload lost pose or changed trajectory");
                module.edit("projectileRotationY",0,"");h.assertTrue(arrow.itemRotation().equals(expected),"Editing module changed an existing arrow");
                saved.remove("ProjectileItemRotation");restored.load(saved);h.assertTrue(restored.itemRotation().lengthSquared()==0,"Legacy arrow rotation is not zero");
            }else{
                var shot=new ModuleShot(world,pos.getCenter(),velocity,module,core);var remote=new ModuleShot(SableLife.MODULE_SHOT.get(),world);
                remote.getEntityData().assignValues(shot.getEntityData().getNonDefaultValues());
                h.assertTrue(remote.itemRotation().equals(expected)&&remote.customFlightItem(),"Shot metadata lost pose: "+type);
                var saved=shot.saveWithoutId(new CompoundTag());var restored=new ModuleShot(SableLife.MODULE_SHOT.get(),world);restored.load(saved);
                h.assertTrue(restored.itemRotation().equals(expected)&&restored.getDeltaMovement().equals(velocity),"Shot reload lost pose or changed trajectory: "+type);
                module.edit("projectileRotationY",0,"");h.assertTrue(shot.itemRotation().equals(expected),"Editing module changed an existing shot");
                saved.remove("ProjectileItemRotation");restored.load(saved);h.assertTrue(restored.itemRotation().lengthSquared()==0,"Legacy shot rotation is not zero");
            }
        }
        var invalid=new CompoundTag();invalid.putFloat("X",Float.NaN);invalid.putFloat("Y",300);invalid.putFloat("Z",-300);
        h.assertTrue(ProjectileItemPose.read(invalid).equals(new Vector3f(0,180,-180)),"Invalid saved angles were not sanitized");
        h.succeed();
    }
    @GameTest(template="combat",batch="projectile_item_rotation")
    public static void LocalRotationTracksFlightFrameAndDoesNotChangeLegacyZeroPose(GameTestHelper h){
        for(float yaw:new float[]{0,90,-130})for(float pitch:new float[]{0,65,-40}){
            var base=new Quaternionf().rotationY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(-pitch)).rotateY(-(float)Math.PI/2).rotateZ(-(float)Math.PI/4);
            var sample=new Vector3f(.2f,.8f,-.4f);
            h.assertTrue(ProjectileItemPose.flight(yaw,pitch,new Vector3f()).transform(new Vector3f(sample)).distance(base.transform(new Vector3f(sample)))<1e-5,"Zero offsets changed old arrow pose");
            var angles=new Vector3f(90,0,0);var expected=base.transform(new Vector3f(sample).rotateX((float)Math.PI/2));
            h.assertTrue(ProjectileItemPose.flight(yaw,pitch,angles).transform(new Vector3f(sample)).distance(expected)<1e-5,"Offset used world axes instead of moving flight frame");
        }
        h.succeed();
    }
}
