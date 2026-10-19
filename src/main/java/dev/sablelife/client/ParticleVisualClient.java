package dev.sablelife.client;

import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.module.ModuleParticles;
import net.minecraft.client.Minecraft;

/** Configure the actual client particle, independently of attack/trail emission duration. */
public final class ParticleVisualClient {
    public static void color(net.minecraft.client.particle.Particle particle,ModuleParticles.Entry entry){
        if(entry.color()>=0&&ModuleParticles.colorable(entry.id()))particle.setColor(((entry.color()>>16)&255)/255F,((entry.color()>>8)&255)/255F,(entry.color()&255)/255F);
    }
    public static void receive(dev.sablelife.module.ParticleTravelPacket packet){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var entry=new ModuleParticles.Entry(packet.id(),packet.life(),packet.size(),packet.color());var type=ModuleParticles.options(entry);var from=packet.from();var to=packet.to();
        if(type==null||!Double.isFinite(from.x+from.y+from.z+to.x+to.y+to.z)||from.distanceToSqr(to)>128*128+.001||mc.player.position().distanceToSqr(from)>512*512)return;
        var particle=mc.particleEngine.createParticle(type,from.x,from.y,from.z,0,0,0);
        if(particle!=null){color(particle,entry);particle.scale(Float.isFinite(packet.size())?Math.clamp(packet.size(),.1F,4F):1);((ParticleTravel)particle).sablelife$travel(from,to,packet.ticks(),packet.life());}
    }
    public static void receive(ModuleNetworking.ParticleVisual packet) {
        var mc = Minecraft.getInstance(); if (mc.level == null || mc.player == null) return;
        var style = new ModuleParticles.Entry(packet.id(), packet.ticks(), packet.size(),packet.color());var type = ModuleParticles.options(style); var p = packet.position(); var d = packet.spread();
        if (type == null || !Double.isFinite(p.x+p.y+p.z+d.x+d.y+d.z+packet.speed())
                || mc.player.position().distanceToSqr(p) > 512*512) return;
        var random = mc.level.random; int count = Math.clamp(packet.count(),0,256);
        for (int i=0; i<Math.max(1,count); i++) {
            double x=p.x, y=p.y, z=p.z, vx=d.x*packet.speed(), vy=d.y*packet.speed(), vz=d.z*packet.speed();
            if (count > 0) {
                x+=random.nextGaussian()*d.x; y+=random.nextGaussian()*d.y; z+=random.nextGaussian()*d.z;
                vx=random.nextGaussian()*packet.speed(); vy=random.nextGaussian()*packet.speed(); vz=random.nextGaussian()*packet.speed();
            }
            var particle = mc.particleEngine.createParticle(type,x,y,z,vx,vy,vz);
            if (particle != null) { color(particle,style);if (style.ticks() > 0) particle.setLifetime(style.ticks()); particle.scale(style.size()); }
        }
    }
}
