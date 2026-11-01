package dev.sablelife.mixin;
import dev.sablelife.attack.BodyMeleeSource;
import dev.sablelife.compat.epicfight.EpicFightBodyGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.api.event.types.entity.TakeDamageEvent;
@Mixin(value=GuardSkill.class,remap=false)
public abstract class EpicFightBodyGuardMixin {
 @Inject(method="guard",at=@At("HEAD"),cancellable=true)
 private void sablelife$positionGuard(SkillContainer c,CapabilityItem i,ServerPlayerPatch p,TakeDamageEvent.Income e,float knockback,float impact,boolean advanced,CallbackInfo ci) {
  if(e.getDamageSource() instanceof BodyMeleeSource){EpicFightBodyGuard.guard((GuardSkill)(Object)this,c,i,p,e,knockback,impact,advanced,0);ci.cancel();}
 }
}
