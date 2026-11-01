package dev.sablelife.module;
import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
/** Cosmetic position of an independent flying sound source, never a module-wide stop. */
public record SoundMotionPacket(ResourceLocation dimension,UUID source,Vec3 position,boolean stop) implements CustomPacketPayload {
 public static final Type<SoundMotionPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"sound_motion"));
 public static final StreamCodec<RegistryFriendlyByteBuf,SoundMotionPacket> CODEC=StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension);b.writeUUID(p.source);b.writeDouble(p.position.x);b.writeDouble(p.position.y);b.writeDouble(p.position.z);b.writeBoolean(p.stop);},b->new SoundMotionPacket(b.readResourceLocation(),b.readUUID(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBoolean()));
 public boolean valid(){return dimension!=null&&source!=null&&position!=null&&Double.isFinite(position.lengthSqr())&&Math.abs(position.x)<=30000000&&Math.abs(position.y)<=30000000&&Math.abs(position.z)<=30000000;}
 @Override public Type<SoundMotionPacket> type(){return TYPE;}
}
