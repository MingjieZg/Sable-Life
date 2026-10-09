package dev.sablelife.module;
import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
public record SlashVisualPacket(Vec3 from,Vec3 to,Vec3 forward,Vec3 right,float length,float width,int color,boolean custom,CompoundTag styles) implements CustomPacketPayload {
 public static final Type<SlashVisualPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"slash_visual"));
 private static void vector(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
 private static Vec3 vector(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
 public static final StreamCodec<RegistryFriendlyByteBuf,SlashVisualPacket> CODEC=StreamCodec.of((b,p)->{vector(b,p.from);vector(b,p.to);vector(b,p.forward);vector(b,p.right);b.writeFloat(p.length);b.writeFloat(p.width);b.writeInt(p.color);b.writeBoolean(p.custom);b.writeNbt(p.styles);},b->new SlashVisualPacket(vector(b),vector(b),vector(b),vector(b),b.readFloat(),b.readFloat(),b.readInt(),b.readBoolean(),b.readNbt()));
 @Override public Type<SlashVisualPacket> type(){return TYPE;}
}
