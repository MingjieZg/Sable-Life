package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.module.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import java.util.*;

@GameTestHolder("sablelife_facing_offset_tests") @PrefixGameTestTemplate(false)
public final class AutoFacingOffsetGameTests {
    @GameTest(template="combat") public static void FractionalAimOffsetsAlignTheDisplacedRayForEveryMarkedFace(GameTestHelper h){
        for(var face:Direction.values())for(var orientation:List.of(new Quaterniond(),new Quaterniond().rotateXYZ(.2,.5,-.1)))for(double left:new double[]{-.5,0,.25,1.2}){
            var local=Vec3.atLowerCornerOf(face.getNormal());var front=ContactBox.rotate(orientation,local);var offset=AutoFacingGeometry.aimOffset(orientation,local,left,-.35);var target=new Vec3(12,4,-7);
            var reference=AutoFacingGeometry.aimReference(front,target,offset,true);h.assertTrue(reference!=null,"Reachable target rejected");var turn=AutoFacingGeometry.turn(reference,target,true,360);
            var ray=ContactBox.rotate(turn,front);var relative=target.subtract(ContactBox.rotate(turn,offset));
            h.assertTrue(relative.dot(ray)>0&&relative.cross(ray).length()<1e-7,"Offset ray failed "+face+" left="+left);
            var limited=AutoFacingGeometry.turn(reference,target,true,9);h.assertTrue(Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(limited.w))))<=9.000001,"Offset bypassed speed cap");
        }
        h.assertTrue(AutoFacingGeometry.aimReference(new Vec3(0,0,-1),new Vec3(0,0,-.5),new Vec3(-1,0,0),true)==null,"Impossible close offset caused a rotating reference");h.succeed();
    }
    @GameTest(template="combat") public static void DefaultsLegacyValuesDecimalBoundsAndWireCodecStayCompatible(GameTestHelper h){
        var fresh=new VirtualModule("auto_facing");h.assertTrue(fresh.value("facingCollision")==0,"New default not Off");
        for(double value:new double[]{0,1}){fresh.edit("facingCollision",value,"");h.assertTrue(VirtualModule.load(fresh.save()).value("facingCollision")==value,"Saved collision changed");}
        var legacy=fresh.save();legacy.getCompound("Settings").remove("facingCollision");h.assertTrue(VirtualModule.load(legacy).value("facingCollision")==1,"Missing legacy collision changed meaning");
        for(String key:List.of("facingOffsetLeft","facingOffsetUp")){
            fresh.edit(key,-.375,"");h.assertTrue(VirtualModule.load(fresh.save()).value(key)==-.375,"Decimal offset lost");h.assertTrue(!fresh.edit(key,Double.NaN,""),"NaN offset accepted");
            fresh.edit(key,999,"");h.assertTrue(fresh.value(key)==64,"Offset bound failed");
            for(double value:new double[]{-64,-.375,0,.25,64}){var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{var action=new ModuleNetworking.Action(1,0,UUID.randomUUID(),"edit",key,value,"");ModuleNetworking.Action.CODEC.encode(buffer,action);h.assertTrue(action.equals(ModuleNetworking.Action.CODEC.decode(buffer)),"Offset wire codec mismatch");}finally{buffer.release();}}
        }
        h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=60) public static void NativeControllerAimsTheLeftAndUpPointAtTheSameTarget(GameTestHelper h){
        var core=ContactSensorGameTests.core(h,SableLife.HEART.get(),3);var m=core.modules().add("auto_facing");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.NORTH)));m.edit("target",1,"");m.edit("facingVertical",1,"");m.edit("facingOffsetLeft",1.25,"");m.edit("facingOffsetUp",.5,"");m.edit("facingSpeed",720,"");
        var pivot=SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(pivot.add(8,3,-8));h.getLevel().addFreshEntity(cow);ContactSensorGameTests.activate(h,core);
        h.runAfterDelay(18,()->{
            var orientation=new Quaterniond(m.anchors().getFirst().body(core).logicalPose().orientation());var origin=SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter()).add(AutoFacingGeometry.aimOffset(orientation,new Vec3(0,0,-1),1.25,.5));
            var front=ContactBox.rotate(orientation,new Vec3(0,0,-1));var desired=cow.getBoundingBox().getCenter().subtract(origin).normalize();h.assertTrue(front.dot(desired)>.995,"Runtime ignored displaced local reference");cow.discard();h.succeed();
        });
    }
}
