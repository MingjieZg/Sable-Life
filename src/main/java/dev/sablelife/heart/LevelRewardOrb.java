package dev.sablelife.heart;
import dev.sablelife.SableLife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
/** An orb-shaped, non-merging direct-level pickup; raw-point orbs retain vanilla behavior. */
public final class LevelRewardOrb extends ExperienceOrb {
 private static final EntityDataAccessor<Integer> LEVELS=SynchedEntityData.defineId(LevelRewardOrb.class,EntityDataSerializers.INT);
 public LevelRewardOrb(EntityType<? extends LevelRewardOrb> type,Level level){super(type,level);}
 public static void award(ServerLevel level,Vec3 point,int levels){if(levels<=0)return;var orb=new LevelRewardOrb(SableLife.LEVEL_REWARD_ORB.get(),level);orb.entityData.set(LEVELS,Math.clamp(levels,1,ExperienceUnits.MAX_LEVELS));orb.setPos(point);orb.setDeltaMovement((level.random.nextDouble()-.5)*.15,.2,(level.random.nextDouble()-.5)*.15);level.addFreshEntity(orb);}
 public int levels(){return entityData.get(LEVELS);}
 @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(LEVELS,1);}
 @Override public int getIcon(){return 7;}
 @Override public void playerTouch(Player player){if(!(player instanceof ServerPlayer)||isRemoved()||player.isSpectator()||player.takeXpDelay!=0)return;if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerXpEvent.PickupXp(player,this)).isCanceled())return;player.takeXpDelay=2;player.take(this,1);player.giveExperienceLevels(levels());discard();}
 @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("LevelReward",levels());}
 @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(LEVELS,Math.clamp(tag.getInt("LevelReward"),1,ExperienceUnits.MAX_LEVELS));}
 @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(ServerEntity entity){return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this,entity);}
}
