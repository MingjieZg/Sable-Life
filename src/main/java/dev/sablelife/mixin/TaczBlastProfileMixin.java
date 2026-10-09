package dev.sablelife.mixin;
import dev.sablelife.combat.BodyDamageBridge;
import org.spongepowered.asm.mixin.*;
/** Use TaCZ's configured entity radius and linear power falloff, not vanilla TNT strength. */
@Pseudo @Mixin(targets="com.tacz.guns.util.block.ProjectileExplosion",remap=false)
public abstract class TaczBlastProfileMixin implements BodyDamageBridge.BlastProfile {
 @Shadow private float radius;@Shadow private float power;
 public double sablelife$blastRange(){return radius;}
 public float sablelife$blastDamage(double distance){return radius>0?(float)(Math.max(0,1-distance/radius)*power):0;}
}
