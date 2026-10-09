package dev.sablelife.client;
import dev.sablelife.module.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
public final class SlashVisualClient {
 public static void receive(SlashVisualPacket p){
  var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||p.styles()==null||!Double.isFinite(p.from().lengthSqr()+p.to().lengthSqr()+p.forward().lengthSqr()+p.right().lengthSqr()+p.length()+p.width())||p.from().distanceToSqr(p.to())>16||mc.player.position().distanceToSqr(p.to())>256*256)return;
  double length=Math.clamp(p.length(),.5F,32F),width=Math.clamp(p.width(),.1F,8F);var forward=p.forward().normalize();var right=p.right().normalize();
  var entries=ModuleParticles.loadEntries(p.styles().getList("Properties",10),ModuleParticles.load(p.styles().getList("Ids",8)));
  if(p.custom()&&entries.isEmpty())return;
  var dust=new DustParticleOptions(new org.joml.Vector3f(((p.color()>>16)&255)/255F,((p.color()>>8)&255)/255F,(p.color()&255)/255F),.7F);
  int samples=Math.clamp((int)Math.ceil(length/.12),12,64);
  for(int i=0;i<=samples;i++)for(int band=0;band<3;band++){
   double t=2.*i/samples-1;var offset=right.scale(t*length/2).add(forward.scale(-width*t*t-width*.28*(1-t*t)*band));var a=p.from().add(offset);var b=p.to().add(offset);
   var entry=entries.isEmpty()?null:entries.get((i*3+band)%entries.size());var type=p.custom()?ModuleParticles.type(entry.id()):dust;
   var particle=mc.particleEngine.createParticle(type,a.x,a.y,a.z,0,0,0);if(particle!=null){particle.scale(p.custom()?entry.size():1);((ParticleTravel)particle).sablelife$travel(a,b,1,p.custom()&&entry.ticks()>0?entry.ticks():2);}
  }
 }
 private SlashVisualClient(){}
}
