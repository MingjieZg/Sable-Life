package dev.sablelife.heart;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.module.ModuleParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

/** A cosmetic burst on the lethal hit, before the delayed physical breakup. */
public final class DeathPresentation {
    private final boolean removal;
    public DeathPresentation() { this(false); }
    public DeathPresentation(boolean removal) { this.removal=removal; defaults(); }
    private void defaults() {
        particles=new dev.sablelife.module.VirtualModule("particles");
        particles.editParticle("add",removal ? "minecraft:poof" : "minecraft:firework");
        if(!removal) particles.editParticle("add","minecraft:flash");
        settings=new CompoundTag();
        settings.putString("deathSound",removal ? "" : "minecraft:entity.firework_rocket.blast");
        soundTimelines=new CompoundTag();
        settings.putString("deathSoundLast",text("deathSound"));
    }
    private CompoundTag settings = new CompoundTag();
    private dev.sablelife.module.VirtualModule particles = new dev.sablelife.module.VirtualModule("particles");
    public dev.sablelife.module.VirtualModule particleSelection() { return particles; }
    private CompoundTag soundTimelines = new CompoundTag();
    public void restoreDefaults(){defaults();}
    public dev.sablelife.sound.SoundTimeline soundTimeline(String key) {
        if(!key.equals("deathSound"))return new dev.sablelife.sound.SoundTimeline();
        if(soundTimelines.contains(key))return dev.sablelife.sound.SoundTimeline.load(soundTimelines.getCompound(key));
        return dev.sablelife.sound.SoundTimeline.legacy(key,text(key),value("deathVolume"),Math.clamp(value("deathPitch"),.5,2),value("deathRadius"));
    }
    public void soundTimeline(String key,dev.sablelife.sound.SoundTimeline timeline){if(key.equals("deathSound"))soundTimelines.put(key,timeline.save());}
    public String text(String key) { return settings.getString(key); }
    public double value(String key) { return settings.contains(key) ? settings.getDouble(key) : switch (key) {
        case "deathCount" -> removal ? 6 : 80; case "deathSpread" -> removal ? .35 : .8; case "deathPattern" -> removal ? 5 : 0;
        case "deathVolume" -> removal ? 1 : .8; case "deathPitch" -> removal ? 1 : 1.1;
        case "deathScale" -> 1; case "deathRadius" -> 32; default -> 0;
    }; }
    public boolean edit(String key, double value, String text) {
        if (key.equals("deathParticle") || key.equals("deathSound") || key.equals("deathSoundLast")) {
            var id = ResourceLocation.tryParse(text);
            if (!text.isEmpty() && (id == null || (key.equals("deathParticle") ? !ModuleParticles.valid(text)
                    : !BuiltInRegistries.SOUND_EVENT.containsKey(id) && !dev.sablelife.sound.SoundLibrary.contains(text)))) return false;
            soundTimelines.remove(key); settings.putString(key, text);
            if(key.equals("deathSound")&&!text.isEmpty())settings.putString("deathSoundLast",text);

            if (key.equals("deathParticle")) {
                for (var old : particles.particles()) particles.editParticle("remove", old);
                if (!text.isEmpty()) {
                    particles.editParticle("add", text);
                    particles.editParticle("duration", text, value("deathDuration"));
                    particles.editParticle("size", text, value("deathScale"));
                }
            }
            return true;
        }
        if (!Double.isFinite(value)) return false;
        double min = key.equals("deathScale") ? .1 : key.equals("deathPitch") ? .5 : key.equals("deathRadius") ? 1 : 0;
        double max = switch (key) { case "deathCount", "deathRadius" -> 256; case "deathSpread" -> 32;
            case "deathPattern" -> 5; case "deathDuration" -> 60; case "deathPitch" -> 2; case "deathScale", "deathVolume" -> 4; default -> -1; };
        if (value < min || value > max) return false;
        settings.putDouble(key, key.equals("deathCount") || key.equals("deathPattern") ? Math.round(value) : value);
        if(key.equals("deathDuration") || key.equals("deathScale")) for(var id:particles.particles())
            particles.editParticle(key.equals("deathDuration") ? "duration" : "size",id,value);
        return true;
    }
    public CompoundTag save() { var result=settings.copy(); result.put("SoundTimelines",soundTimelines.copy()); result.putInt("PresentationVersion",3); result.put("ParticleSelection",particles.save()); return result; }
    public void load(CompoundTag data) {
        defaults();
        for (var key : java.util.List.of("deathParticle", "deathSound", "deathSoundLast")) if(data.contains(key))edit(key, 0, data.getString(key));
        for (var key : java.util.List.of("deathCount", "deathSpread", "deathDuration", "deathScale", "deathVolume", "deathPitch", "deathRadius", "deathPattern"))
            if (data.contains(key)) edit(key, data.getDouble(key), "");
        if(data.contains("ParticleSelection",10)) {
            var particleData=data.getCompound("ParticleSelection").copy(); particleData.putString("Type","particles");
            particles=dev.sablelife.module.VirtualModule.load(particleData);
        } else if(!text("deathParticle").isEmpty()) {
            particles.editParticle("duration",text("deathParticle"),value("deathDuration"));
            particles.editParticle("size",text("deathParticle"),value("deathScale"));
        }
        // Before the two-stage editor, empty settings still played a hard-coded firework burst.
        if(!removal&&data.getInt("PresentationVersion")<2&&particles.particles().isEmpty()&&text("deathSound").isEmpty()) defaults();
        soundTimelines=data.getCompound("SoundTimelines").copy();soundTimelines.remove("deathTailSound");
    }
    public void play(HeartBlockEntity heart) {
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        var point = SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter());
        emit(level,point); playSound(level,point,heart.creatureId());
    }
    public void emit(ServerLevel level, net.minecraft.world.phys.Vec3 point) {
        int count=(int)value("deathCount"), pattern=(int)value("deathPattern"); double spread=value("deathSpread");
        if(count<=0||particles.particles().isEmpty())return;
        for(var entry:particles.particleEntries()) {
            if(pattern==0 || pattern==5) {
                boolean flash=entry.id().equals("minecraft:flash");
                double width=flash?0:pattern==0?spread*.1875:spread;
                ModuleParticles.send(level,entry,point.x,point.y,point.z,flash?1:count,width,width,width,pattern==0&&!flash?.35:.04);
            } else for(int i=0;i<count;i++) {
                double angle=2*Math.PI*i/count; net.minecraft.world.phys.Vec3 offset;
                if(pattern==1) { double y=1-2*(i+.5)/count, radius=Math.sqrt(1-y*y), az=i*Math.PI*(3-Math.sqrt(5));offset=new net.minecraft.world.phys.Vec3(radius*Math.cos(az),y,radius*Math.sin(az)); }
                else if(pattern==2) offset=new net.minecraft.world.phys.Vec3(Math.cos(angle),0,Math.sin(angle));
                else if(pattern==3) {
                    double edge=10.*i/count;int corner=(int)edge;double t=edge-corner;
                    double a=corner*Math.PI/5-Math.PI/2,b=a+Math.PI/5,r1=corner%2==0?1:.4,r2=corner%2==0?.4:1;
                    offset=new net.minecraft.world.phys.Vec3((1-t)*r1*Math.cos(a)+t*r2*Math.cos(b),(1-t)*r1*Math.sin(a)+t*r2*Math.sin(b),0);
                } else offset=new net.minecraft.world.phys.Vec3(Math.cos(angle*3),2.*i/count-1,Math.sin(angle*3));
                var p=point.add(offset.scale(spread));ModuleParticles.send(level,entry,p.x,p.y,p.z,1,0,0,0,0);
            }
        }
        if(pattern==0&&particles.particles().contains("minecraft:firework")) ModuleParticles.send(level,new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.65F,.25F,1F),1.4F),point.x,point.y,point.z,28,spread,spread,spread,0);
    }
    public void playSound(ServerLevel level, net.minecraft.world.phys.Vec3 point, java.util.UUID creature) {
        String key="deathSound";
        var soundId=java.util.UUID.nameUUIDFromBytes((creature+":"+(removal?"removal":"death")+":"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        dev.sablelife.module.ModuleAudio.stop(level,soundId);
        dev.sablelife.module.ModuleAudio.play(level,soundId,soundTimeline(key),point);
    }
}
