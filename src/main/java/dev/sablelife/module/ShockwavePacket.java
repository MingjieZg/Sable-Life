package dev.sablelife.module;
import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
/** One cosmetic birth, no continuously streamed transforms or entities. */
public record ShockwavePacket(ResourceLocation dimension,Vec3 point,float radius,int expansion,float width,float distortion,float blur,float shake,int shakeTicks,float zoom,int zoomTicks,float range,boolean reverse,float shakeFrequency,int pattern,float clearRadius) implements CustomPacketPayload {
 public ShockwavePacket(ResourceLocation dimension,Vec3 point,float radius,int expansion,float width,float distortion,float blur,float shake,int shakeTicks,float zoom,int zoomTicks,float range,boolean reverse,float shakeFrequency){this(dimension,point,radius,expansion,width,distortion,blur,shake,shakeTicks,zoom,zoomTicks,range,reverse,shakeFrequency,0,.08F);}
 public ShockwavePacket(ResourceLocation dimension,Vec3 point,float radius,int expansion,float width,float distortion,float blur,float shake,int shakeTicks,float zoom,int zoomTicks,float range){this(dimension,point,radius,expansion,width,distortion,blur,shake,shakeTicks,zoom,zoomTicks,range,false);}
 public ShockwavePacket(ResourceLocation dimension,Vec3 point,float radius,int expansion,float width,float distortion,float blur,float shake,int shakeTicks,float zoom,int zoomTicks,float range,boolean reverse){this(dimension,point,radius,expansion,width,distortion,blur,shake,shakeTicks,zoom,zoomTicks,range,reverse,1);}
 public static final Type<ShockwavePacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"visual_shockwave"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ShockwavePacket> CODEC=StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension);b.writeDouble(p.point.x);b.writeDouble(p.point.y);b.writeDouble(p.point.z);b.writeFloat(p.radius);b.writeVarInt(p.expansion);b.writeFloat(p.width);b.writeFloat(p.distortion);b.writeFloat(p.blur);b.writeFloat(p.shake);b.writeVarInt(p.shakeTicks);b.writeFloat(p.zoom);b.writeVarInt(p.zoomTicks);b.writeFloat(p.range);b.writeBoolean(p.reverse);b.writeFloat(p.shakeFrequency);b.writeVarInt(p.pattern);b.writeFloat(p.clearRadius);},b->new ShockwavePacket(b.readResourceLocation(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readVarInt(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readVarInt(),b.readFloat(),b.readVarInt(),b.readFloat(),b.readBoolean(),b.readFloat(),b.readVarInt(),b.readFloat()));
 public boolean valid(){return pattern>=0&&pattern<=1&&Float.isFinite(clearRadius)&&clearRadius>=0&&clearRadius<=.5F&&point!=null&&Double.isFinite(point.lengthSqr())&&Math.abs(point.x)<=30000000&&Math.abs(point.z)<=30000000&&Float.isFinite(radius)&&radius>=.5&&radius<=128&&expansion>=1&&expansion<=200&&Float.isFinite(width)&&width>=.1&&width<=16&&Float.isFinite(distortion)&&distortion>=0&&distortion<=1&&Float.isFinite(blur)&&blur>=0&&blur<=1&&Float.isFinite(shake)&&shake>=0&&shake<=1&&shakeTicks>=1&&shakeTicks<=200&&Float.isFinite(shakeFrequency)&&shakeFrequency>=.1F&&shakeFrequency<=2&&Float.isFinite(zoom)&&zoom>=-100&&zoom<=30&&zoomTicks>=1&&zoomTicks<=200&&Float.isFinite(range)&&range>=1&&range<=256;}
 public int lifetime(){return Math.max(expansion,Math.max(shakeTicks,zoomTicks));}
 @Override public Type<ShockwavePacket> type(){return TYPE;}
}
