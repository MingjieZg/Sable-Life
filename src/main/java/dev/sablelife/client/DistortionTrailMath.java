package dev.sablelife.client;

import dev.sablelife.module.DistortionTrailPacket;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector3d;

/** Projects an oriented ellipsoid covariance; all three adjustable dimensions affect its silhouette. */
public final class DistortionTrailMath {
 public static float[] project(DistortionTrailPacket.Sample sample,Vec3 half,Vec3 camera,Matrix4f view,Matrix4f projection,float aspect){
  var delta=sample.center().subtract(camera);var clip=new Vector4f((float)delta.x,(float)delta.y,(float)delta.z,1);var matrix=new Matrix4f(projection).mul(view);matrix.transform(clip);
  if(!Float.isFinite(clip.w)||clip.w<=.05)return null;
  double xx=0,xy=0,yy=0;var q=sample.rotation();
  for(var axis:new Vector3d[]{new Vector3d(half.x,0,0),new Vector3d(0,half.y,0),new Vector3d(0,0,half.z)}){
   q.transform(axis);var v=new Vector4f((float)axis.x,(float)axis.y,(float)axis.z,0);matrix.transform(v);
   double x=(v.x*clip.w-clip.x*v.w)/((double)clip.w*clip.w)*.5*aspect,y=(v.y*clip.w-clip.y*v.w)/((double)clip.w*clip.w)*.5;
   xx+=x*x;xy+=x*y;yy+=y*y;
  }
  double determinant=xx*yy-xy*xy;if(!Double.isFinite(determinant)||determinant<1e-18)return null;
  return new float[]{clip.x/clip.w*.5f+.5f,clip.y/clip.w*.5f+.5f,(float)(yy/determinant),(float)(xx/determinant),(float)(-xy/determinant)};
 }
 /** Swept blade segment, projected as a ribbon rather than independent round volumes.
  * Corners are old root/tip, new root/tip. Clip rejection avoids near-plane explosions. */
 public static float[] ribbon(DistortionTrailPacket.Sample a,DistortionTrailPacket.Sample b,Vec3 half,Vec3 camera,Matrix4f view,Matrix4f projection,float aspect,double taper){
  var matrix=new Matrix4f(projection).mul(view);float[] out=new float[8];int n=0;
  for(var sample:new DistortionTrailPacket.Sample[]{a,b})for(int end:new int[]{-1,1}){
   var axis=new Vector3d(0,0,end*half.z*taper);sample.rotation().transform(axis);
   var point=sample.center().add(axis.x,axis.y,axis.z).subtract(camera);
   var clip=new Vector4f((float)point.x,(float)point.y,(float)point.z,1);matrix.transform(clip);
   if(!Float.isFinite(clip.w)||clip.w<=.05)return null;
   out[n++]=(clip.x/clip.w*.5f+.5f)*aspect;out[n++]=clip.y/clip.w*.5f+.5f;
  }
  // Preserve a thin volume when viewed edge-on, with width/height setting thickness.
  var shape=project(b,half,camera,view,projection,aspect);if(shape==null)return null;
  double dx=(out[4]+out[6]-out[0]-out[2])*.5,dy=(out[5]+out[7]-out[1]-out[3])*.5;
  double len=Math.hypot(dx,dy);if(len<1e-7){dx=out[6]-out[2];dy=out[7]-out[3];len=Math.hypot(dx,dy);}if(len<1e-7)return null;
  double depth=Math.max(.1,b.center().distanceTo(camera));
  float thickness=(float)(Math.hypot(half.x,half.y)*projection.m11()*.5/depth*taper);
  double bladeX=(out[2]-out[0]+out[6]-out[4])*.5,bladeY=(out[3]-out[1]+out[7]-out[5])*.5,bladeLen=Math.hypot(bladeX,bladeY);
  double nx=bladeLen>1e-7?-bladeY/bladeLen:dx/len,ny=bladeLen>1e-7?bladeX/bladeLen:dy/len;if(nx*dx+ny*dy<0){nx=-nx;ny=-ny;}
  float tx=(float)nx*thickness,ty=(float)ny*thickness;
  out[0]-=tx;out[1]-=ty;out[2]-=tx;out[3]-=ty;out[4]+=tx;out[5]+=ty;out[6]+=tx;out[7]+=ty;
  for(float value:out)if(!Float.isFinite(value))return null;
  return out;
 }
 private DistortionTrailMath(){}
}
