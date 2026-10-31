package dev.sablelife.module;

import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One candidate pool per formation. Target positions stay live between spatial searches. */
public final class SummonTargetCache {
    public static final int REFRESH_TICKS=5;
    private List<AABB> coverage=List.of();
    private List<LivingEntity> candidates=List.of();
    private Map<UUID,LivingEntity> entities=Map.of();
    private final Map<UUID,Vec3> centers=new LinkedHashMap<>();
    private long nextSearch=Long.MIN_VALUE,queries,revision;
    private int category=-1;
    public long queries(){return queries;}
    public long revision(){return revision;}
    public Vec3 point(UUID id){if(id==null)return null;var e=entities.get(id);return e!=null&&!e.isRemoved()&&e.isAlive()?centers.get(id):null;}
    public void suspend(){coverage=List.of();candidates=List.of();entities=Map.of();centers.clear();nextSearch=Long.MIN_VALUE;category=-1;}
    private static boolean contains(AABB outer,Vec3 point,double range){return outer.minX<=point.x-range&&outer.minY<=point.y-range&&outer.minZ<=point.z-range&&outer.maxX>=point.x+range&&outer.maxY>=point.y+range&&outer.maxZ>=point.z+range;}
    /** Separate distant search islands instead of scanning a large empty bounding box. */
    private static List<AABB> clusters(List<Vec3> points,double range){
        double padding=Math.min(2,Math.max(.1,range*.1));var result=new ArrayList<AABB>();
        for(var point:points){var box=new AABB(point,point).inflate(range+padding);
            for(int i=0;i<result.size();)if(box.intersects(result.get(i))){box=box.minmax(result.remove(i));i=0;}else i++;
            result.add(box);
        }
        return List.copyOf(result);
    }
    public void prepare(ServerLevel level,UUID owner,int mode,List<Vec3> positions,double range,long now){
        if(positions.isEmpty()){suspend();return;}
        boolean covered=mode==category;
        if(covered)for(var p:positions){boolean found=false;for(var c:coverage)if(contains(c,p,range)){found=true;break;}if(!found){covered=false;break;}}
        if(!covered||now>=nextSearch){
            coverage=clusters(positions,range);category=mode;var unique=new LinkedHashMap<UUID,LivingEntity>();
            for(var box:coverage){queries++;for(var target:ModuleTargets.query(level,owner,box,e->ModuleTargets.matches(e,mode)))unique.putIfAbsent(target.getUUID(),target);}
            candidates=List.copyOf(unique.values());entities=unique;centers.clear();nextSearch=now+REFRESH_TICKS;revision++;
        }
        // Between searches, validate/refresh candidates once per group rather than per weapon.
        for(var entity:candidates){
            var id=entity.getUUID();boolean loaded=entity instanceof ModuleTargets.LifeTarget?LoadedCoreIndex.find(level,id)!=null:level.getEntity(id)==entity;
            if(entity.level()==level&&!entity.isRemoved()&&loaded&&ModuleTargets.matches(entity,mode))centers.put(id,entity.getBoundingBox().getCenter());else centers.remove(id);
        }
    }
    public UUID select(UUID current,Vec3 origin,double range,boolean reselect){
        double best=range*range;var old=point(current);
        if(!reselect&&old!=null&&old.distanceToSqr(origin)<=best)return current;
        UUID chosen=null;
        for(var entry:centers.entrySet()){if(point(entry.getKey())==null)continue;double distance=entry.getValue().distanceToSqr(origin);if(distance<=best&&(chosen==null||distance<best)){best=distance;chosen=entry.getKey();}}
        return chosen;
    }
}
