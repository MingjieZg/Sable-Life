package dev.sablelife.module;
import net.minecraft.server.level.ServerLevel;import net.minecraft.world.phys.Vec3;import java.util.*;
/** Cosmetic outward shell: damage is still resolved immediately by ModuleCombat. */
public final class PulseVisual {
 public static final int POINTS=64;
 public static List<ParticleTravelPacket> particles(VirtualModule module,Vec3 center){
  var entries=module.movementParticles("Wave").particleEntries();if(module.value("pulseVisual")!=1||entries.isEmpty())return List.of();double radius=module.value("radius");int ticks=Math.clamp((int)module.value("pulseExpandTime"),1,200);var result=new ArrayList<ParticleTravelPacket>();
  for(int i=0;i<POINTS;i++){double y=1-2*(i+.5)/POINTS,angle=i*Math.PI*(3-Math.sqrt(5)),r=Math.sqrt(1-y*y);var to=center.add(Math.cos(angle)*r*radius,y*radius,Math.sin(angle)*r*radius);var entry=entries.get(i%entries.size());result.add(new ParticleTravelPacket(entry.id(),center,to,ticks,Math.max(ticks+2,entry.ticks()),entry.size(),entry.color()));}
  return List.copyOf(result);
 }
 public static void send(ServerLevel level,VirtualModule module,Vec3 center){
  for(var entry:module.movementParticles("Center").particleEntries())ModuleParticles.send(level,entry,center.x,center.y,center.z,1,0,0,0,0);
  if(module.value("pulseVisual")==0){var entries=module.movementParticles("Wave").particleEntries();if(entries.isEmpty())return;for(int i=0;i<32;i++){double angle=Math.PI*2*i/32;ModuleParticles.send(level,entries.get(i%entries.size()),center.x+Math.cos(angle)*module.value("radius"),center.y,center.z+Math.sin(angle)*module.value("radius"),1,0,0,0,0);}return;}
  var packets=particles(module,center);for(var player:level.players())if(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,player.position()).distanceToSqr(center)<=512*512)for(var packet:packets)if(VisualBudget.allow(player,1))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);}
 private PulseVisual(){}
}
