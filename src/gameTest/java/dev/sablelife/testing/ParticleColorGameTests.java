package dev.sablelife.testing;
import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;

@GameTestHolder("sablelife_particle_tests") @PrefixGameTestTemplate(false)
public final class ParticleColorGameTests {
    @GameTest(template="combat")
    public static void ColorSettingsPersistIndependentlyAndValidate(GameTestHelper h){
        var m=new VirtualModule("particles");
        for(String id:List.of("minecraft:effect","minecraft:instant_effect","minecraft:entity_effect","minecraft:dust","sablelife:arc","sablelife:smoke","sablelife:energy_mote"))h.assertTrue(ModuleParticles.valid(id)&&ModuleParticles.colorable(id)&&m.editParticle("add",id),"Colorable particle unavailable: "+id);
        h.assertTrue(m.editParticle("color","minecraft:effect",0x32CD32)&&m.editParticle("duration","minecraft:effect",2)&&m.editParticle("size","minecraft:effect",2),"Color/life/size edit rejected");
        var loaded=VirtualModule.load(m.save());var preset=ModulePreset.create(m.save());
        h.assertTrue(loaded.particleEntry("minecraft:effect").color()==0x32CD32&&loaded.particleEntry("minecraft:effect").ticks()==40&&loaded.particleEntry("minecraft:effect").size()==2&&preset.particleEntry("minecraft:effect").color()==0x32CD32,"Color lost through save/preset or life/size edit");
        for(String id:ModuleParticles.BUNDLED) {
            h.assertTrue(ModuleParticles.valid(id)&&(m.particles().contains(id)||m.editParticle("add",id))&&m.editParticle("color",id,0x123456)&&m.editParticle("duration",id,3)&&m.editParticle("size",id,2),"Bundled particle edits rejected: "+id);
            var restored=VirtualModule.load(m.save()).particleEntry(id);
            h.assertTrue(restored.color()==0x123456&&restored.ticks()==60&&restored.size()==2&&ModulePreset.create(m.save()).particleEntry(id).equals(restored),"Bundled style lost in NBT/preset");
        }
        h.assertTrue(loaded.particleEntry("minecraft:instant_effect").color()==-1,"Color leaked into another selected particle");
        m.editParticle("add","minecraft:flame");h.assertTrue(!m.editParticle("color","minecraft:flame",0xFF0000)&&!m.editParticle("color","minecraft:effect",0x1000000)&&!m.editParticle("color","minecraft:effect",1.5)&&!m.editParticle("color","minecraft:effect",Double.NaN),"Invalid/unsupported color accepted");
        h.assertTrue(m.editParticle("color","minecraft:effect",-1)&&m.particleEntry("minecraft:effect").color()==-1,"Restore original failed");
        h.assertTrue(!ModuleParticles.valid("minecraft:block")&&!ModuleParticles.valid("minecraft:item")&&!ModuleParticles.colorable("minecraft:flame"),"Unsupported particle payload admitted");h.succeed();
    }
    @GameTest(template="combat")
    public static void NativeColorPayloadAndNetworkRoundTripsPreserveRgb(GameTestHelper h){
        int rgb=0x32CD32;var entry=new ModuleParticles.Entry("minecraft:entity_effect",30,2,rgb);
        var options=(net.minecraft.core.particles.ColorParticleOption)ModuleParticles.options(entry);
        h.assertTrue(Math.abs(options.getGreen()-205/255F)<1e-6&&Math.abs(options.getRed()-50/255F)<1e-6,"Native entity effect color is wrong");
        var dust=(net.minecraft.core.particles.DustParticleOptions)ModuleParticles.options(new ModuleParticles.Entry("minecraft:dust",0,1,rgb));h.assertTrue(dust.getColor().y>dust.getColor().x,"Native dust color is wrong");
        var burst=new ModuleNetworking.ParticleVisual(entry.id(),Vec3.ZERO,8,Vec3.ZERO,.1,30,2,rgb);var travel=new ParticleTravelPacket(entry.id(),Vec3.ZERO,new Vec3(1,0,0),20,30,2,rgb);
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{
            ModuleNetworking.ParticleVisual.CODEC.encode(buf,burst);h.assertTrue(ModuleNetworking.ParticleVisual.CODEC.decode(buf).equals(burst),"Burst packet lost color");
            ParticleTravelPacket.CODEC.encode(buf,travel);h.assertTrue(ParticleTravelPacket.CODEC.decode(buf).equals(travel),"Travel packet lost color");
            var jet=new ModuleNetworking.FlameJet(Vec3.ZERO,new Vec3(1,0,0),1,8,1,0,1,1,List.of(entry));ModuleNetworking.FlameJet.CODEC.encode(buf,jet);h.assertTrue(ModuleNetworking.FlameJet.CODEC.decode(buf).particles().getFirst().equals(entry),"Jet style lost color");
        }finally{buf.release();}
        var m=new VirtualModule("particles");m.edit("particlePattern",3,"");m.edit("particlePropagation",1,"");
        h.assertTrue(ParticlePattern.travel(m,entry,Vec3.ZERO,new org.joml.Quaterniond(),new Vec3(0,1,0),0,8,net.minecraft.util.RandomSource.create()).color()==rgb,"Outward pattern lost color");
        h.assertTrue(ParticlePattern.emissionStyle(m,entry).color()==rgb,"Emission lifetime override lost color");
        var pulse=new VirtualModule("pulse");pulse.edit("pulseVisual",1,"");var wave=pulse.movementParticles("Wave");for(var id:wave.particles())wave.editParticle("remove",id);wave.editParticle("add",entry.id());wave.editParticle("color",entry.id(),rgb);h.assertTrue(PulseVisual.particles(pulse,Vec3.ZERO).stream().allMatch(p->p.color()==rgb),"Pulse wave lost color");
        h.succeed();
    }
}
