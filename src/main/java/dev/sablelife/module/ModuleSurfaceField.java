package dev.sablelife.module;

import dev.sablelife.attack.*;
import dev.sablelife.heart.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import java.util.*;

/** World-fixed impact effects owned and cleaned up by one creature. No terrain mutations. */
public final class ModuleSurfaceField {
    public static final int LIMIT=64;
    public static final Set<String> IMPACT_ATTACKS=Set.of("mortar","flame","gun","arrow","slash","laser","ground_strike","homing");
    private static final class Field {
        final UUID source,template; final VirtualModule config; final SurfaceFieldGeometry.Frame frame; final List<Vec3> samples;
        long expires,nextScan;
        Field(UUID source,UUID template,VirtualModule config,SurfaceFieldGeometry.Frame frame,List<Vec3> samples,long expires){this.source=source;this.template=template;this.config=config;this.frame=frame;this.samples=samples;this.expires=expires;}
    }
    private record HitKey(UUID source,UUID template,UUID target){}
    private final List<Field> fields=new ArrayList<>();
    private final Map<HitKey,Long> hits=new HashMap<>();
    public int count(){return fields.size();}
    public void clear(){fields.clear();hits.clear();}
    private static boolean sourceEnabled(HeartBlockEntity heart,VirtualModule source){return ModuleAvailability.enabled(heart,source)&&(source.enabled()||heart.modules().summonedWeapons.using(source.id()));}
    public static boolean impactAttack(VirtualModule m){return m!=null&&IMPACT_ATTACKS.contains(m.type());}
    /** One shared impact entry point; targets without a world surface are projected to ground. */
    public void impact(HeartBlockEntity heart,VirtualModule attack,Vec3 point,Vec3 normal,Vec3 incoming){
        if(!impactAttack(attack)||attack.impactFieldId()==null)return;
        if(!(heart.getLevel() instanceof ServerLevel level)||!heart.operational()||!heart.wireless().master()||!impactAttack(attack)||!attack.enabled()||!ModuleAvailability.enabled(heart,attack))return;
        var source=heart.modules().find(attack.id());if(!sourceEnabled(heart,source))return;
        var config=heart.modules().find(attack.impactFieldId());
        if(config!=null&&ModuleAvailability.enabled(heart,config)&&config.enabled()&&config.type().equals("surface_field"))spawn(level,attack.id(),config.id(),config,point,normal,incoming);
    }
    public void spawn(ServerLevel level,UUID source,UUID template,VirtualModule config,Vec3 point,Vec3 normal,Vec3 incoming){
        if(!Double.isFinite(point.lengthSqr()+normal.lengthSqr()+incoming.lengthSqr())||!level.hasChunkAt(BlockPos.containing(point)))return;
        normal=normal.lengthSqr()<.5?new Vec3(0,1,0):normal.normalize();
        var surface=SurfaceFieldGeometry.surface(level,point,normal);if(surface==null)return;
        var frame=SurfaceFieldGeometry.frame(config,surface,normal,incoming);long expires=level.getGameTime()+(long)config.value("fieldDuration");
        // Refresh a local patch, never enlarge it or add a second damage clock.
        for(var old:fields)if(old.source.equals(source)&&old.template.equals(template)&&old.frame.normal().dot(frame.normal())>.99&&old.frame.center().distanceToSqr(surface)<1){old.expires=expires;return;}
        if(fields.size()>=LIMIT)return;var samples=SurfaceFieldGeometry.samples(level,frame);if(samples.isEmpty())return;
        fields.add(new Field(source,template,VirtualModule.load(config.save()),frame,samples,expires));
    }
    public void tick(HeartBlockEntity heart){
        if(!(heart.getLevel() instanceof ServerLevel level)||!heart.operational()||!heart.wireless().master()||heart.health().state()!=HeartState.ALIVE){clear();return;}
        long now=level.getGameTime();
        fields.removeIf(f->{var source=heart.modules().find(f.source);var template=heart.modules().find(f.template);return now>=f.expires||!sourceEnabled(heart,source)||template==null||!ModuleAvailability.enabled(heart,template)||!template.enabled();});
        hits.entrySet().removeIf(e->e.getValue()<=now);
        int visuals=192;
        for(int index=0;index<fields.size();index++){
            // Rotate visual priority so a dense old patch cannot hide newer impacts forever.
            var f=fields.get(Math.floorMod(index+(int)(now/4),fields.size()));
            if(now%4==0&&visuals>0){var entries=f.config.particleEntries();int count=Math.min(24,Math.min(f.samples.size(),visuals/Math.max(1,entries.size())));visuals-=count*entries.size();
                for(int i=0;i<count;i++){var p=f.samples.get(level.random.nextInt(f.samples.size()));if(!level.hasChunkAt(BlockPos.containing(p)))continue;
                    var surface=SurfaceFieldGeometry.surface(level,p,f.frame.normal());if(surface==null||surface.distanceToSqr(p)>.25)continue;
                    for(var particle:entries)ModuleParticles.send(level,particle,p.x,p.y,p.z,0,f.frame.normal().x*.015,f.frame.normal().y*.015,f.frame.normal().z*.015,1);
                }
            }
            if(now<f.nextScan)continue;f.nextScan=now+(f.config.value("fieldBurn")==1?1:Math.min(5,(long)f.config.value("hitInterval")));
            var frame=f.frame;double radius=frame.circle()?frame.length():Math.hypot(frame.length(),frame.width());
            var center=frame.ahead()&&!frame.circle()?frame.center().add(frame.forward().scale(frame.length()/2)):frame.center();
            for(var victim:ModuleTargets.query(level,heart.creatureId(),new AABB(center,center).inflate(radius+3),e->!(e instanceof ModuleTargets.LifeTarget)&&ModuleTargets.matches(e,f.config))) {
                var box=victim.getBoundingBox();var p=new Vec3(Math.clamp(center.x,box.minX,box.maxX),Math.clamp(center.y,box.minY,box.maxY),Math.clamp(center.z,box.minZ,box.maxZ));
                if(contact(level,f,p,box))apply(heart,level,f,victim,now);
            }
            // Use actual owned collision surfaces, not the creature's enclosing target box.
            if(f.config.value("target")<2||f.config.value("damage")<=0||dev.sablelife.ownership.LoadedCoreIndex.query(level,new AABB(center,center).inflate(radius+3)).stream().noneMatch(core->core!=heart&&core.acceptsLifeAttack(heart.creatureId())))continue;
            for(var hit:dev.sablelife.combat.BodySphereQuery.query(level,center.add(frame.normal().scale(.1)),center.add(frame.normal().scale(.1)),Math.min(128,radius+3))){
                if(!hit.core().acceptsLifeAttack(heart.creatureId())||!frame.contains(hit.point())||f.samples.stream().noneMatch(p->p.distanceToSqr(hit.point())<.75*.75))continue;
                var key=new HitKey(f.source,f.template,hit.core().creatureId());if(hits.containsKey(key)||f.config.value("damage")<=0)continue;
                hits.put(key,now+(long)f.config.value("hitInterval"));float before=hit.core().health().current();if(hit.core().damageAt(hit.block(),(float)f.config.value("damage"))){heart.modules().hitCounter.record(heart,f.source,Math.max(0,before-hit.core().health().current()));dev.sablelife.attack.CombatEventHooks.killed(heart,f.source,hit.core());}
            }
        }
    }
    private boolean contact(ServerLevel level,Field f,Vec3 point,AABB box){
        if(!f.frame.contains(point))return false;var surface=SurfaceFieldGeometry.surface(level,point,f.frame.normal());
        return surface!=null&&box.inflate(.2).contains(surface.add(f.frame.normal().scale(.15)));
    }
    private void apply(HeartBlockEntity heart,ServerLevel level,Field f,LivingEntity victim,long now){
        // Refresh residual burning on every contact scan, independently of the damage clock.
        if(f.config.value("fieldBurn")==1&&f.config.value("burn")>0)victim.igniteForSeconds((int)f.config.value("burn"));
        var key=new HitKey(f.source,f.template,victim.getUUID());if(hits.containsKey(key))return;
        hits.put(key,now+(long)f.config.value("hitInterval"));var attack=heart.modules().find(f.source);
        if(f.config.value("damage")>0)ModuleDamage.hurt(victim,ModuleDamage.physical(level),(float)f.config.value("damage"),heart,attack);
        ModuleEffects.apply(victim,f.config.effects(),null);
    }
}
