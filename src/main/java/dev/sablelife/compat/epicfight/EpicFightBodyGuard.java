package dev.sablelife.compat.epicfight;
import dev.sablelife.attack.BodyMeleeSource;
import dev.sablelife.mixin.EpicFightGuardAccess;
import yesman.epicfight.skill.*;
import yesman.epicfight.skill.guard.*;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.api.event.types.entity.TakeDamageEvent;
import yesman.epicfight.registry.entries.EpicFightSkillDataKeys;
import yesman.epicfight.registry.entries.EpicFightSounds;
/** Only replaces the offender-dependent section of pinned native guard methods.
 * Native listeners still decide holding, weapon availability and front/rear eligibility.
 */
public final class EpicFightBodyGuard {
 /** Effect-only slashes still pass native guard listeners, without manufacturing HP damage. */
 public static boolean blockEffectOnly(net.minecraft.world.entity.LivingEntity victim,BodyMeleeSource source){
    if(!(victim instanceof net.minecraft.server.level.ServerPlayer player))return false;
    var patch=yesman.epicfight.world.capabilities.EpicFightCapabilities.getServerPlayerPatch(player);
    if(patch==null||!patch.isEpicFightMode()||!patch.isHoldingAny())return false;
    var event=new TakeDamageEvent.Income(patch,source,0);
    yesman.epicfight.api.event.EpicFightEventHooks.Entity.TAKE_DAMAGE_INCOME.postWithListener(event,patch.getEventListener());
    return event.isCanceled()||source.wasBlocked();
 }
 public static void guard(GuardSkill skill,SkillContainer container,CapabilityItem item,ServerPlayerPatch patch,
        TakeDamageEvent.Income event,float knockback,float impact,boolean advanced,int parryWindow) {
    var source=(BodyMeleeSource)event.getDamageSource();var access=(EpicFightGuardAccess)skill;
    boolean parryAvailable=parryWindow>0&&skill.isHoldingWeaponAvailable(patch,item,GuardSkill.BlockType.ADVANCED_GUARD);
    if(!access.sablelife$blockable(source,parryAvailable||advanced))return;
    var player=patch.getOriginal();var data=container.getDataManager();
    boolean parried=parryAvailable&&player.tickCount-data.getDataValue(EpicFightSkillDataKeys.LAST_ACTIVE)<parryWindow;
    float penalty;
    if(parried){event.setParried(true);penalty=.1F;knockback*=.4F;data.setData(EpicFightSkillDataKeys.LAST_ACTIVE,0);}
    else {penalty=data.getDataValue(EpicFightSkillDataKeys.PENALTY)+access.sablelife$penalizer(item);data.setDataSync(EpicFightSkillDataKeys.PENALTY,penalty);}
    patch.playSound(EpicFightSounds.CLASH.get(),-.05F,.1F);
    boolean affordable=patch.consumeForSkill(skill,Skill.Resource.STAMINA,penalty*impact);
    patch.knockBackEntity(source.getSourcePosition(),knockback);
    if(!parryAvailable)data.setDataSync(EpicFightSkillDataKeys.PENALTY_RESTORE_COUNTER,player.tickCount);
    var type=parried?GuardSkill.BlockType.ADVANCED_GUARD:affordable?GuardSkill.BlockType.GUARD:GuardSkill.BlockType.GUARD_BREAK;
    var animation=access.sablelife$motion(container,patch,item,type);
    if(animation!=null)patch.playAnimationSynchronized(animation,0);
    if(type==GuardSkill.BlockType.GUARD_BREAK)patch.playSound(EpicFightSounds.NEUTRALIZE_MOBS.get(),3,0,.1F);
    // Impact guard's advanced burst expects an attacker Entity; only that visual is omitted.
    // For this ordinary physical source, advanced does not change its damage/block result.
    skill.dealEvent(patch,event,skill instanceof ImpactGuardSkill?false:advanced);
    if(event.isCanceled()){source.blocked(type==GuardSkill.BlockType.GUARD_BREAK?0:2);
        var point=player.getEyePosition().add(player.getLookAngle().scale(.5));
        dev.sablelife.module.ModuleParticles.send(player.serverLevel(),net.minecraft.core.particles.ParticleTypes.CRIT,point.x,point.y,point.z,5,.1,.1,.1,.02);
    }
 }
 private EpicFightBodyGuard(){}
}
