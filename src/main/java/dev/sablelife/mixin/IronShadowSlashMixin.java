package dev.sablelife.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.sablelife.compat.ironspells.*;
import io.redspace.ironsspellbooks.spells.ender.ShadowSlashSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Mixin(value=ShadowSlashSpell.class,remap=false)
public abstract class IronShadowSlashMixin {
 @Shadow private float getDamage(int spellLevel,LivingEntity caster){throw new AssertionError();}
 @Inject(method="onCast",at=@At("HEAD"))
 private void sablelife$window(Level level,int spellLevel,LivingEntity caster,CastSource source,MagicData data,CallbackInfo ci,@Share("sablelife$shadowHits") LocalRef<Set<UUID>> hits){hits.set(new HashSet<>());}
 @ModifyExpressionValue(method="onCast",at=@At(value="INVOKE",target="Lio/redspace/ironsspellbooks/api/util/Utils;raycastForBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;"))
 private BlockHitResult sablelife$worldEnd(BlockHitResult hit,Level level,int spellLevel,LivingEntity caster,CastSource source,MagicData data,@Share("sablelife$shadowHits") LocalRef<Set<UUID>> hits){
  var world=IronSpellsBodyBridge.worldBlockHit(level,hit);
  IronAreaBodyBridge.shadowContact(caster,world,getDamage(spellLevel,caster),hits.get());
  return world;
 }
 @WrapOperation(method="onCast",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",remap=true))
 private List<Entity> sablelife$nativeDamageBox(Level level,Entity excluded,AABB box,Operation<List<Entity>> original,Level castLevel,int spellLevel,LivingEntity caster,CastSource source,MagicData data,@Share("sablelife$shadowHits") LocalRef<Set<UUID>> hits){
  IronAreaBodyBridge.box(caster,box,caster.getEyePosition(),getDamage(spellLevel,caster),hits.get());
  return original.call(level,excluded,box);
 }
}
