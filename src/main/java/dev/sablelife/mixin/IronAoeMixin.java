package dev.sablelife.mixin;
import dev.sablelife.compat.ironspells.IronAreaBodyBridge;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Add body contacts to the native checkHits result, preserving cadence and post-hit radius/duration rules. */
@Mixin(value=AoeEntity.class,remap=false)
public abstract class IronAoeMixin {
 @Unique private boolean sablelife$bodyHit;
 @Shadow protected abstract Vec3 getInflation();
 @Inject(method="checkHits",at=@At("HEAD"))
 private void sablelife$bodyWindow(CallbackInfo ci){
  var self=(AoeEntity)(Object)this;var inflation=getInflation();
  sablelife$bodyHit=IronAreaBodyBridge.area(self,self.getBoundingBox().inflate(inflation.x,inflation.y,inflation.z));
 }
 @ModifyVariable(method="checkHits",at=@At("LOAD"),ordinal=0)
 private boolean sablelife$mergeNativeHit(boolean hit){return hit||sablelife$bodyHit;}
}
