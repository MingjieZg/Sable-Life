package dev.sablelife.module;

import dev.sablelife.attack.*;
import dev.sablelife.heart.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import java.util.*;

/** World-fixed impact effects owned and cleaned up by one creature. */
public final class ModuleSurfaceField {
    public static final int LIMIT=64;
    public static final Set<String> IMPACT_ATTACKS=Set.of("mortar","flame","gun","arrow","slash","laser","ground_strike","homing");
    private static final class Field {
        final UUID source,template; final VirtualModule config; final SurfaceFieldGeometry.Frame frame; final List<Vec3> samples;
        long expires,nextScan,blastAt;
        Field(UUID source,UUID template,VirtualModule config,SurfaceFieldGeometry.Frame frame,List<Vec3> samples,long now){
            this.source=source;this.template=template;this.config=config;this.frame=frame;this.samples=samples;
            expires=now+(long)config.value("fieldDuration");
            blastAt=config.value("fieldBlast")==1?now+(long)config.value("fieldBlastDelay"):Long.MAX_VALUE;
        }
    }
    private record HitKey(UUID source,UUID template,UUID target){}
    private final List<Field> fields=new ArrayList<>();
    private final Map<HitKey,Long> hits=new HashMap<>();
    public int count(){return fields.size();}
    /** Visual-only sampling stays within geometry and the shared per-creature budget. */
    public static int visualSampleCount(VirtualModule config,int samples,int remaining){
        int entries=config.particleEntries().size();if(entries==0)return 0;
        return Math.max(0,Math.min((int)config.value("density"),Math.min(samples,remaining/entries)));
    }
    public void clear(){fields.clear();hits.clear();}
    private static boolean sourceEnabled(HeartBlockEntity heart,VirtualModule source){return ModuleAvailability.enabled(heart,source)&&(source.enabled()||heart.modules().summonedWeapons.using(source.id()));}
    public static boolean impactAttack(VirtualModule m){return m!=null&&IMPACT_ATTACKS.contains(m.type());}
    /** One shared impact entry point; targets without a world surface are projected to ground. */
    public void impact(HeartBlockEntity heart,VirtualModule attack,Vec3 point,Vec3 normal,Vec3 incoming){
        if(!impactAttack(attack)||attack.impactFieldId()==null)return;
        if(!(heart.getLevel() instanceof ServerLevel level)||!heart.operational()||!heart.wireless().master()||!impactAttack(attack)||!attack.enabled()||!ModuleAvailability.enabled(heart,attack))return;
        var source=heart.modules().find(attack.id());if(!sourceEnabled(heart,source))return;
        var config=heart.modules().find(attack.impactFieldId());
        if(config!=null&&ModuleAvailability.enabled(heart,config)&&config.enabled()&&config.type().equals("surface_field"))spawn(level,attack.id(),config.id(),config,point,normal,incoming,heart);
    }
    public void spawn(ServerLevel level,UUID source,UUID template,VirtualModule config,Vec3 point,Vec3 normal,Vec3 incoming){
        spawn(level,source,template,config,point,normal,incoming,null);
    }
    private void spawn(ServerLevel level,UUID source,UUID template,VirtualModule config,Vec3 point,Vec3 normal,Vec3 incoming,HeartBlockEntity heart){
        if(!Double.isFinite(point.lengthSqr()+normal.lengthSqr()+incoming.lengthSqr())||!level.hasChunkAt(BlockPos.containing(point)))return;
        normal=normal.lengthSqr()<.5?new Vec3(0,1,0):normal.normalize();
        var surface=SurfaceFieldGeometry.surface(level,point,normal);if(surface==null)return;
        var frame=SurfaceFieldGeometry.frame(config,surface,normal,incoming);
        long now=level.getGameTime(),expires=now+(long)config.value("fieldDuration");
        // Refresh a local patch, never enlarge it or add a second damage clock.
        for(var old:fields)if(old.expires>=now&&old.source.equals(source)&&old.template.equals(template)&&old.frame.normal().dot(frame.normal())>.99&&old.frame.center().distanceToSqr(surface)<1){
            if(heart==null)old.expires=expires;
            else if(expires>old.expires&&SurvivalTokens.chargeFieldExtension(heart,old.config,expires-old.expires))
                old.expires=expires;
            return;
        }
        if(fields.size()>=LIMIT)return;var samples=SurfaceFieldGeometry.samples(level,frame);if(samples.isEmpty())return;
        if(heart!=null&&!dev.sablelife.heart.SurvivalTokens.chargeCast(heart,config,1))return;
        fields.add(new Field(source,template,VirtualModule.load(config.save()),frame,samples,now));
    }
    public void tick(HeartBlockEntity heart){
        if(!(heart.getLevel() instanceof ServerLevel level)||!heart.operational()||!heart.wireless().master()||heart.health().state()!=HeartState.ALIVE){clear();return;}
        long now=level.getGameTime();
        fields.removeIf(f->{var source=heart.modules().find(f.source);var template=heart.modules().find(f.template);
            boolean expired=now>f.expires||now==f.expires&&f.blastAt!=now;
            return expired||!sourceEnabled(heart,source)||template==null||!ModuleAvailability.enabled(heart,template)||!template.enabled();});
        hits.entrySet().removeIf(e->e.getValue()<=now);
        int visuals=192;
        boolean endedWithBlast=false;
        for(int index=0;index<fields.size();index++){
            // Rotate visual priority so a dense old patch cannot hide newer impacts forever.
            var f=fields.get(Math.floorMod(index+(int)(now/4),fields.size()));
            if(now>=f.blastAt){
                f.blastAt=Long.MAX_VALUE;
                if(level.hasChunkAt(BlockPos.containing(f.frame.midpoint()))){
                    if(f.config.value("blastDamage")>0)scan(heart,level,f,now,true);
                    SurfaceFieldBlast.present(level,heart,f.config,f.frame);
                }
            }
            if(now==f.expires){endedWithBlast=true;continue;}
            if(now%4==0&&visuals>0){var entries=f.config.particleEntries();int count=visualSampleCount(f.config,f.samples.size(),visuals);visuals-=count*entries.size();
                for(int i=0;i<count;i++){var p=f.samples.get(level.random.nextInt(f.samples.size()));if(!level.hasChunkAt(BlockPos.containing(p)))continue;
                    var surface=SurfaceFieldGeometry.surface(level,p,f.frame.normal());if(surface==null||surface.distanceToSqr(p)>.25)continue;
                    for(var particle:entries)ModuleParticles.send(level,particle,p.x,p.y,p.z,0,f.frame.normal().x*.015,f.frame.normal().y*.015,f.frame.normal().z*.015,1);
                }
            }
            if(now<f.nextScan)continue;f.nextScan=now+(f.config.value("fieldBurn")==1?1:Math.min(5,(long)f.config.value("hitInterval")));
            scan(heart,level,f,now,false);
        }
        if(endedWithBlast)fields.removeIf(f -> now>=f.expires);
    }
    private void scan(HeartBlockEntity heart,ServerLevel level,Field f,long now,boolean blast){
        var frame=f.frame;
        double radius=frame.circle()?frame.length():Math.hypot(frame.length(),frame.width());
        var center=frame.midpoint();
        var bounds=new AABB(center,center).inflate(radius+3);
        float damage=(float)f.config.value(blast?"blastDamage":"damage");
        var attack=heart.modules().find(f.source);
        for(var victim:ModuleTargets.damageQuery(level,heart.creatureId(),bounds,e->!(e instanceof ModuleTargets.LifeTarget)&&ModuleTargets.matches(e,f.config))) {
            var box=victim.getBoundingBox();
            var p=new Vec3(Math.clamp(center.x,box.minX,box.maxX),Math.clamp(center.y,box.minY,box.maxY),Math.clamp(center.z,box.minZ,box.maxZ));
            if(!contact(level,f,p,box))continue;
            if(blast)ModuleDamage.hurt(victim,ModuleDamage.blast(level,null),damage,heart,attack);
            else apply(heart,level,f,victim,now);
        }
        // Use actual owned collision surfaces, not the creature's enclosing target box.
        if(f.config.value("target")<2||damage<=0||dev.sablelife.ownership.LoadedCoreIndex.query(level,bounds).stream().noneMatch(core->core!=heart&&core.acceptsLifeAttack(heart.creatureId())))return;
        var origin=center.add(frame.normal().scale(.1));
        for(var hit:dev.sablelife.combat.BodySphereQuery.query(level,origin,origin,Math.min(128,radius+3))){
            if(!hit.core().acceptsLifeAttack(heart.creatureId())||!frame.contains(hit.point()))continue;
            if(blast){
                var surface=SurfaceFieldGeometry.surface(level,hit.point(),frame.normal());
                if(surface==null||surface.distanceToSqr(hit.point())>=.75*.75)continue;
            }else{
                var key=new HitKey(f.source,f.template,hit.core().creatureId());
                if(hits.containsKey(key)||f.samples.stream().noneMatch(p->p.distanceToSqr(hit.point())<.75*.75))continue;
                hits.put(key,now+(long)f.config.value("hitInterval"));
            }
            float before=hit.core().health().current();
            if(hit.core().damageAt(hit.block(),damage)){
                heart.modules().hitCounter.record(heart,f.source,Math.max(0,before-hit.core().health().current()));
                CombatEventHooks.killed(heart,f.source,hit.core());
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
        ModuleEffects.apply(victim,ModuleEffects.forCore(heart,f.config),null);
    }
}
