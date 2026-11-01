package dev.sablelife.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import net.minecraft.world.damagesource.DamageSource;
@Mixin(value=GuardSkill.class,remap=false)
public interface EpicFightGuardAccess {
 @Invoker("isBlockableSource") boolean sablelife$blockable(DamageSource source,boolean advanced);
 @Invoker("getPenalizer") float sablelife$penalizer(CapabilityItem item);
 @Invoker("getGuardMotion") AnimationAccessor<? extends StaticAnimation> sablelife$motion(SkillContainer container,PlayerPatch<?> patch,CapabilityItem item,GuardSkill.BlockType type);
}
