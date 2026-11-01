package dev.sablelife.module;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import java.util.*;

/** Rotation contributes swept distance, including swings whose mark stays still. */
public final class TrailMotion {
 public record Pose(Vec3 center,Quaterniond rotation){
  public Pose{rotation=new Quaterniond(rotation).normalize();}
  public DistortionTrailPacket.Sample sample(){return new DistortionTrailPacket.Sample(center,(float)rotation.x,(float)rotation.y,(float)rotation.z,(float)rotation.w);}
 }
 private Pose previous;
 public void clear(){previous=null;}
 public static double distance(Pose a,Pose b,Vec3 half){return a.center.distanceTo(b.center)+2*Math.acos(Math.clamp(Math.abs(a.rotation.dot(b.rotation)),0,1))*half.length();}
 public List<Pose> advance(Pose next,Vec3 half,double threshold,double spacing){
  var old=previous;previous=next;if(old==null)return List.of();double motion=distance(old,next,half);
  // A teleport is a new baseline, never a screen-wide connecting streak.
  if(old.center.distanceTo(next.center)>64||!Double.isFinite(motion)){return List.of();}
  if(motion<1e-5||motion*20<threshold){return List.of();}
  // Every moving tick includes both ends. Spacing controls curvature subdivision,
  // never whether adjacent ticks connect; packet-local strips cannot bridge pauses.
  int segments=Math.min(DistortionTrailPacket.MAX_SAMPLES-1,Math.max(1,(int)Math.ceil(motion/spacing)));
  var result=new ArrayList<Pose>();result.add(old);
  for(int i=1;i<=segments;i++){double t=(double)i/segments;result.add(new Pose(old.center.lerp(next.center,t),new Quaterniond(old.rotation).slerp(next.rotation,t)));}
  return List.copyOf(result);
 }
}
