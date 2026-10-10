package dev.sablelife.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.sablelife.compat.ironspells.IronSpellsBodyBridge;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.sense.SensorGeometry;
import io.redspace.ironsspellbooks.spells.evocation.FangStrikeSpell;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** Preserve a ground attack: a living moving body is a victim, not the terrain beneath its own feet. */
@Mixin(value=FangStrikeSpell.class,remap=false)
public abstract class IronFangGroundMixin {
 @ModifyExpressionValue(method="getGroundLevel",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",remap=true))
 private BlockHitResult sablelife$worldGround(BlockHitResult hit,Level level,Vec3 start,int steps){
  var world=IronSpellsBodyBridge.worldBlockHit(level,hit);var core=hit.getType()==HitResult.Type.BLOCK?SableBodyProbe.findActivatedHeart(level,hit.getBlockPos()):null;
  if(core==null||!(level instanceof net.minecraft.server.level.ServerLevel))return world;var end=start.add(0,-steps*2,0);var point=SensorGeometry.beamEnd((net.minecraft.server.level.ServerLevel)level,core.creatureId(),start,end);
  return new BlockHitResult(point,net.minecraft.core.Direction.UP,net.minecraft.core.BlockPos.containing(point),false);
 }
}
