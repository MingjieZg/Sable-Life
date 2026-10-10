package dev.sablelife.mixin;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.sablelife.compat.MountedContraptionCleanup;
import dev.sablelife.compat.BearingCleanupAccess;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=MechanicalBearingBlockEntity.class,remap=false)
public abstract class BearingDeathCleanupMixin implements BearingCleanupAccess {
 @Shadow protected ControlledContraptionEntity movedContraption;
 @Shadow protected boolean running;
 @Shadow protected boolean assembleNextTick;
 @Override public void sablelife$discardMounted(){
  if(!MountedContraptionCleanup.permitted((MechanicalBearingBlockEntity)(Object)this))return;
  if(movedContraption!=null){movedContraption.ejectPassengers();movedContraption.discard();}
  movedContraption=null;running=false;assembleNextTick=false;
 }
 @Inject(method="disassemble",at=@At("HEAD"),cancellable=true)
 private void sablelife$cleanup(CallbackInfo ci){
  if(MountedContraptionCleanup.permitted((MechanicalBearingBlockEntity)(Object)this)){sablelife$discardMounted();ci.cancel();}
 }
}
