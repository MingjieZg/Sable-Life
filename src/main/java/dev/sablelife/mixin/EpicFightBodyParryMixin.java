package dev.sablelife.mixin;
import dev.sablelife.attack.BodyMeleeSource;
import dev.sablelife.compat.epicfight.EpicFightBodyGuard;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.skill.guard.*;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.api.event.types.entity.TakeDamageEvent;
@Mixin(value=ParryingSkill.class,remap=false)
public abstract class EpicFightBodyParryMixin {
 @Shadow private int parryWindow;
 @Inject(method="guard",at=@At("HEAD"),cancellable=true)
 private void sablelife$positionParry(SkillContainer c,CapabilityItem i,ServerPlayerPatch p,TakeDamageEvent.Income e,float knockback,float impact,boolean advanced,CallbackInfo ci) {
  if(e.getDamageSource() instanceof BodyMeleeSource){EpicFightBodyGuard.guard((GuardSkill)(Object)this,c,i,p,e,knockback,impact,advanced,parryWindow);ci.cancel();}
 }
}
