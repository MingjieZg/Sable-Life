package dev.sablelife.compat;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import dev.sablelife.ownership.*;
/** Death-only, owned controller references; never searches nearby entities. */
public final class MountedContraptionCleanup {
 public static boolean dyingOwned(BlockEntity controller){
  if(controller==null||!(controller.getLevel() instanceof ServerLevel level))return false;
  var data=BodyOwnership.get(level);var owner=data.owner(controller.getBlockPos());if(owner==null)return false;
  var lifecycle=LifeLifecycle.get(level.getServer());var part=data.part(controller.getBlockPos());
  return lifecycle.isDying(owner)||part!=null&&lifecycle.isDying(part);
 }
 public static boolean permitted(BlockEntity controller){return BodyProtection.CLEANING.get()>0&&dyingOwned(controller);}
 public static void discardForDeath(BlockEntity controller){
  if(!permitted(controller))return;
  if(controller instanceof MechanicalBearingBlockEntity)((BearingCleanupAccess)controller).sablelife$discardMounted();
  else if(CannonDeathCleanup.isMount(controller))CannonDeathCleanup.discardMounted(controller);
 }
 private MountedContraptionCleanup(){}
}
