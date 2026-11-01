package dev.sablelife.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.sablelife.compat.ironspells.*;
import io.redspace.ironsspellbooks.spells.holy.DivineSmiteSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import java.util.*;
@Mixin(value=DivineSmiteSpell.class,remap=false)
public abstract class IronDivineSmiteMixin {
 @Shadow private float getDamage(int level,LivingEntity caster){throw new AssertionError();}
 @ModifyExpressionValue(method="onCast",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",remap=true))
 private BlockHitResult sablelife$worldVisual(BlockHitResult hit,Level level,int spellLevel,LivingEntity caster,CastSource source,MagicData data){return IronSpellsBodyBridge.worldBlockHit(level,hit);}
 @WrapOperation(method="onCast",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",remap=true))
 private List<Entity> sablelife$bodyArea(Level level,Entity excluded,AABB box,Operation<List<Entity>> original,Level castLevel,int spellLevel,LivingEntity caster,CastSource source,MagicData data){IronAreaBodyBridge.box(caster,box,box.getCenter().add(0,1,0),getDamage(spellLevel,caster),new HashSet<>());return original.call(level,excluded,box);}
}
