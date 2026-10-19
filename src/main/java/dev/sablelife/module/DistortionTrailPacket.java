package dev.sablelife.module;

import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import java.util.*;

/** Bounded cosmetic births at frozen world poses; never a live body/entity reference. */
public record DistortionTrailPacket(ResourceLocation dimension,UUID module,List<Sample> samples,float width,float height,float length,int life,float strength,float range) implements CustomPacketPayload {
 public static final int MAX_SAMPLES=8;
 public record Sample(Vec3 center,float x,float y,float z,float w){
  public Quaterniond rotation(){return new Quaterniond(x,y,z,w).normalize();}
  public boolean valid(){double n=x*x+y*y+z*z+w*w;return center!=null&&Double.isFinite(center.lengthSqr())&&Math.abs(center.x)<=30000000&&Math.abs(center.y)<=30000000&&Math.abs(center.z)<=30000000&&Double.isFinite(n)&&Math.abs(n-1)<.01;}
 }
 public DistortionTrailPacket{samples=List.copyOf(samples);}
 public static final Type<DistortionTrailPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"distortion_trail"));
 public static final StreamCodec<RegistryFriendlyByteBuf,DistortionTrailPacket> CODEC=StreamCodec.of((b,p)->{
  b.writeResourceLocation(p.dimension);b.writeUUID(p.module);b.writeVarInt(p.samples.size());
  for(var s:p.samples){b.writeDouble(s.center.x);b.writeDouble(s.center.y);b.writeDouble(s.center.z);b.writeFloat(s.x);b.writeFloat(s.y);b.writeFloat(s.z);b.writeFloat(s.w);}
  b.writeFloat(p.width);b.writeFloat(p.height);b.writeFloat(p.length);b.writeVarInt(p.life);b.writeFloat(p.strength);b.writeFloat(p.range);
 },b->{var dimension=b.readResourceLocation();var id=b.readUUID();int count=b.readVarInt();if(count<1||count>MAX_SAMPLES)throw new IllegalArgumentException("Invalid trail sample count");var list=new ArrayList<Sample>();for(int i=0;i<count;i++)list.add(new Sample(new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat()));return new DistortionTrailPacket(dimension,id,list,b.readFloat(),b.readFloat(),b.readFloat(),b.readVarInt(),b.readFloat(),b.readFloat());});
 public boolean valid(){return dimension!=null&&module!=null&&!samples.isEmpty()&&samples.size()<=MAX_SAMPLES&&samples.stream().allMatch(Sample::valid)&&dimension(width)&&dimension(height)&&dimension(length)&&life>=1&&life<=60&&Float.isFinite(strength)&&strength>=0&&strength<=1&&Float.isFinite(range)&&range>=1&&range<=256;}
 private static boolean dimension(float v){return Float.isFinite(v)&&v>=.1&&v<=64;}
 @Override public Type<DistortionTrailPacket> type(){return TYPE;}
}
