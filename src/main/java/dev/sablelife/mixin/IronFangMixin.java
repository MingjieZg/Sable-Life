package dev.sablelife.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.sablelife.compat.ironspells.IronAreaBodyBridge;
import io.redspace.ironsspellbooks.entity.spells.ExtendedEvokerFang;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.*;
@Mixin(value=ExtendedEvokerFang.class,remap=false)
public abstract class IronFangMixin {
 @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",remap=true))
 private List<LivingEntity> sablelife$bodyBite(Level level,Class<LivingEntity> type,AABB box,Operation<List<LivingEntity>> original){var self=(ExtendedEvokerFang)(Object)this;IronAreaBodyBridge.box(self,box,self.position(),self.getDamage(),new HashSet<>());return original.call(level,type,box);}
}
