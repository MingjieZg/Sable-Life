package dev.sablelife.ownership;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.compat.*;
import dev.sablelife.heart.DeathPresentation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Remove only a fully loaded plot whose every non-air block belongs to this cleanup group. */
final class SableBodyDeletion {
    record Result(int removed,int visits) {}
    static Result attempt(ServerLevel level,BodyOwnership ownership,ServerSubLevel body,UUID group,boolean part,int budget,DeathPresentation effect) {
        if(budget<=0||body.isRemoved()||body.getPlot().getBoundingBox()==null)return new Result(0,0);
        var bounds=body.getPlot().getBoundingBox();
        long columns=(long)((bounds.maxX()>>4)-(bounds.minX()>>4)+1)*((bounds.maxZ()>>4)-(bounds.minZ()>>4)+1);
        if(columns>1024)return new Result(0,0);
        for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++)
            if(!level.hasChunkAt(new BlockPos(x<<4,bounds.minY(),z<<4)))return new Result(0,0);
        long expectedRecords=0;
        for(var holder:body.getPlot().getLoadedChunks())expectedRecords+=ownership.chunkRecords(holder.getChunk().getPos().toLong()).size();
        // Do not spend the complete visit budget on an unfinished validation every tick.
        if(expectedRecords>budget)return new Result(0,0);
        var records=new ArrayList<BlockPos>();var points=new ArrayList<Vec3>();
        int visits=0,ownedSolid=0;long[] allSolid={0};
        var mounts=new ArrayList<net.minecraft.world.level.block.entity.BlockEntity>();
        for(var holder:body.getPlot().getLoadedChunks()) {
            var chunk=holder.getChunk();
            for(var section:chunk.getSections())if(!section.hasOnlyAir()) {
                // Palette counting avoids visiting every air voxel or invoking any block lifecycle callback.
                section.getStates().count((state,count)->{if(!state.isAir())allSolid[0]+=count;});
            }
            for(var position:ownership.chunkRecords(chunk.getPos().toLong())) {
                if(visits>=budget)return new Result(0,visits);
                visits++;
                var pos=BlockPos.of(position);
                if(Sable.HELPER.getContaining(level,pos)!=body||!ownership.matchesCleanup(pos,group,part))continue;
                records.add(pos);
                if(!level.getBlockState(pos).isAir()) {
                    ownedSolid++;
                    if(points.size()<16)points.add(body.logicalPose().transformPosition(pos.getCenter()));
                }
            }
            for(var entity:chunk.getBlockEntities().values()) {
                var name=entity.getClass().getName();
                if(name.equals("rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity")||name.equals("rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity"))mounts.add(entity);
            }
        }
        if(ownedSolid==0||ownedSolid!=allSolid[0])return new Result(0,visits);
        BodyProtection.CLEANING.set(BodyProtection.CLEANING.get()+1);
        try {
            for(var mount:mounts)CannonDeathCleanup.discardMounted(mount);
            StructureRemovalApi.remove(level,body);
        }finally {BodyProtection.CLEANING.set(BodyProtection.CLEANING.get()-1);}
        for(var pos:records)if(ownership.matchesCleanup(pos,group,part))ownership.forget(pos);
        dev.sablelife.compat.sable.SableBodyProbe.invalidate(level);
        LoadedCoreIndex.geometryChanged(level);
        for(var point:points) {
            if(effect!=null)effect.emit(level,point);
            else dev.sablelife.module.ModuleParticles.send(level,net.minecraft.core.particles.ParticleTypes.POOF,point.x,point.y,point.z,6,.35,.35,.35,.04);
        }
        return new Result(records.size(),visits);
    }
    private SableBodyDeletion() {}
}
