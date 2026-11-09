package dev.sablelife.client;
import dev.sablelife.module.ShockwavePacket;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
/** All coordinates are camera-relative. Offscreen centres are deliberately not clamped to the screen. */
public final class ShockwaveMath {
 public record Projection(float x,float y,float unitRadius){}
 public static Projection project(Vec3 world,Vec3 camera,Matrix4f view,Matrix4f projection){var p=world.subtract(camera);var clip=new Vector4f((float)p.x,(float)p.y,(float)p.z,1);view.transform(clip);projection.transform(clip);if(!Float.isFinite(clip.w)||clip.w<=.01f)return null;return new Projection(clip.x/clip.w*.5f+.5f,clip.y/clip.w*.5f+.5f,Math.abs(projection.m11())/(2*clip.w));}
 public static double attenuation(double distance,double range){return Math.clamp(1-distance/Math.max(1,range),0,1);}
 public static double envelope(double age,double duration){if(age<0||age>=duration)return 0;double t=age/duration;return t<.15?t/.15:Math.pow((1-t)/.85,2);}
 /** Camera shake uses distance, independent of the camera's view or the ring's projection. */
 public static double shakeAmplitude(ShockwavePacket packet,Vec3 camera,double age,double scale){
  return 3*packet.shake()*envelope(age,packet.shakeTicks())*attenuation(camera.distanceTo(packet.point()),packet.range())*scale;
 }
 /** Linear travel in world blocks: duration only slows the wave; it never sets its starting radius. */
 public static double radius(double age,double duration,double maximum){return radius(age,duration,maximum,false);}
 public static double radius(double age,double duration,double maximum,boolean reverse){double t=Math.clamp(age/Math.max(1,duration),0,1);return maximum*(reverse?1-t:t);}
 /** Keep the first small ring visible, fading only during the final fifth of its travel. */
 public static double waveStrength(double age,double duration){if(age<0||age>=duration)return 0;double t=age/Math.max(1,duration);return 1-Math.clamp((t-.8)/.2,0,1);}
 private ShockwaveMath(){}
}
