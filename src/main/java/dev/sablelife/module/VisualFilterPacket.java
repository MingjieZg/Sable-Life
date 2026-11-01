package dev.sablelife.module;
import dev.sablelife.SableLife;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** A leased per-viewer color hold, independent of shockwave births. */
public record VisualFilterPacket(ResourceLocation dimension,UUID module,int color,float strength,int fadeIn,int fadeOut,boolean active) implements CustomPacketPayload {
 public static final Type<VisualFilterPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"visual_filter"));
 public static final StreamCodec<RegistryFriendlyByteBuf,VisualFilterPacket> CODEC=StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension);b.writeUUID(p.module);b.writeInt(p.color);b.writeFloat(p.strength);b.writeVarInt(p.fadeIn);b.writeVarInt(p.fadeOut);b.writeBoolean(p.active);},b->new VisualFilterPacket(b.readResourceLocation(),b.readUUID(),b.readInt(),b.readFloat(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
 public boolean valid(){return dimension!=null&&module!=null&&color>=0&&color<=0xFFFFFF&&Float.isFinite(strength)&&strength>=0&&strength<=1&&fadeIn>=0&&fadeIn<=200&&fadeOut>=0&&fadeOut<=200;}
 public VisualFilterPacket released(){return new VisualFilterPacket(dimension,module,color,strength,fadeIn,fadeOut,false);}
 @Override public Type<VisualFilterPacket> type(){return TYPE;}
}
