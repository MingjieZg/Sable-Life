package dev.sablelife.module;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Immutable cast-time visual settings; never used by damage or terrain calculations. */
public record ImpactVisual(boolean custom,double radius,int count,List<ModuleParticles.Entry> particles) {
    public ImpactVisual {radius=Double.isFinite(radius)?Math.clamp(radius,0,32):0;count=Math.clamp(count,1,128);particles=List.copyOf(particles);}
    public static ImpactVisual of(VirtualModule m,double damageRadius){
        var selection=m.movementParticles("Impact");
        return new ImpactVisual(m.value("impactVisual")==1,m.value("impactSpreadMode")==1?m.value("impactSpreadRadius"):damageRadius,(int)m.value("impactCount"),selection==null?List.of():selection.particleEntries());
    }
    public CompoundTag save(){var tag=new CompoundTag();tag.putBoolean("Custom",custom);tag.putDouble("Radius",radius);tag.putInt("Count",count);tag.put("Particles",ModuleParticles.saveEntries(particles));return tag;}
    public static ImpactVisual load(CompoundTag tag){var rows=tag.getList("Particles",10);var ids=new java.util.ArrayList<String>();for(int i=0;i<Math.min(rows.size(),ModuleParticles.LIMIT);i++)ids.add(rows.getCompound(i).getString("Id"));return new ImpactVisual(tag.getBoolean("Custom"),tag.getDouble("Radius"),tag.getInt("Count"),ModuleParticles.loadEntries(rows,ids));}
    /** Uniform disk, one central particle, bounded count independent of radius and selection size. */
    public Vec3 position(Vec3 point,int index){if(index==0||count==1)return point.add(0,.2,0);double r=radius*Math.sqrt(index/(double)(count-1)),a=index*2.399963229728653;return point.add(Math.cos(a)*r,.2,Math.sin(a)*r);}
    public void emit(ServerLevel level,Vec3 point){if(!custom||particles.isEmpty())return;for(int i=0;i<count;i++){var p=position(point,i);ModuleParticles.send(level,particles.get(i%particles.size()),p.x,p.y,p.z,1,0,0,0,0);}}
}
