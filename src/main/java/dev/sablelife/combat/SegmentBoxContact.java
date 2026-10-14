package dev.sablelife.combat;
import net.minecraft.world.phys.*;
import java.util.Arrays;
/** Closest point on a voxel box to a segment, including scaled body axes. */
public final class SegmentBoxContact {
 public record Contact(Vec3 point,double fraction){}
 public static Contact closest(Vec3 start,Vec3 end,AABB box,Vec3 weights){
  double[] a={start.x,start.y,start.z},d={end.x-start.x,end.y-start.y,end.z-start.z};
  double[] min={box.minX,box.minY,box.minZ},max={box.maxX,box.maxY,box.maxZ},w={weights.x,weights.y,weights.z};
  double[] cuts=new double[8];int count=2;cuts[0]=0;cuts[1]=1;
  for(int i=0;i<3;i++)if(Math.abs(d[i])>1e-12){for(double bound:new double[]{min[i],max[i]}){double t=(bound-a[i])/d[i];if(t>0&&t<1)cuts[count++]=t;}}
  Arrays.sort(cuts,0,count);double best=Double.POSITIVE_INFINITY,bestT=0;
  for(int j=0;j<count-1;j++){
   double lo=cuts[j],hi=cuts[j+1],mid=(lo+hi)/2,numerator=0,denominator=0;
   for(int i=0;i<3;i++){double p=a[i]+d[i]*mid;double bound=p<min[i]?min[i]:p>max[i]?max[i]:Double.NaN;if(Double.isNaN(bound))continue;numerator+=w[i]*d[i]*(a[i]-bound);denominator+=w[i]*d[i]*d[i];}
   double t=denominator>0?Math.clamp(-numerator/denominator,lo,hi):lo;double distance=0;
   for(int i=0;i<3;i++){double p=a[i]+d[i]*t,delta=p-Math.clamp(p,min[i],max[i]);distance+=w[i]*delta*delta;}
   if(distance<best){best=distance;bestT=t;}
  }
  var point=start.lerp(end,bestT);return new Contact(new Vec3(Math.clamp(point.x,box.minX,box.maxX),Math.clamp(point.y,box.minY,box.maxY),Math.clamp(point.z,box.minZ,box.maxZ)),bestT);
 }
 private SegmentBoxContact(){}
}
