package dev.sablelife.module;
import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
/** Explicit repeat-signal zoom state, independent of individual wave births. */
public record ShockwaveZoomPacket(ResourceLocation dimension,UUID module,Vec3 point,float zoom,int recovery,float range,boolean active) implements CustomPacketPayload {
 public static final Type<ShockwaveZoomPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"shockwave_zoom"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ShockwaveZoomPacket> CODEC=StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension);b.writeUUID(p.module);b.writeDouble(p.point.x);b.writeDouble(p.point.y);b.writeDouble(p.point.z);b.writeFloat(p.zoom);b.writeVarInt(p.recovery);b.writeFloat(p.range);b.writeBoolean(p.active);},b->new ShockwaveZoomPacket(b.readResourceLocation(),b.readUUID(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readVarInt(),b.readFloat(),b.readBoolean()));
 public boolean valid(){return dimension!=null&&module!=null&&point!=null&&Double.isFinite(point.lengthSqr())&&Math.abs(point.x)<=30000000&&Math.abs(point.z)<=30000000&&Float.isFinite(zoom)&&zoom>=-100&&zoom<=30&&recovery>=1&&recovery<=200&&Float.isFinite(range)&&range>=1&&range<=256;}
 public ShockwaveZoomPacket released(){return new ShockwaveZoomPacket(dimension,module,point,zoom,recovery,range,false);}
 @Override public Type<ShockwaveZoomPacket> type(){return TYPE;}
}
