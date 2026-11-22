package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.world.entity.LivingEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** Move set owns these rules. Legacy setting keys remain stable for existing move saves. */
public final class MoveRules {
    private final UUID id;
    private String name="";
    private boolean enabled=true;
    private final CompoundTag settings=new CompoundTag();
    public MoveRules(){this(UUID.randomUUID());}
    private MoveRules(UUID id){this.id=id;for(var f:ModuleFields.moveRules())settings.putDouble(f.key(),f.initial());settings.putDouble("target",2);}
    public UUID id(){return id;}
    public String name(){return name;}
    public boolean enabled(){return enabled;}
    public double value(String key){return key.equals("enabled")?(enabled?1:0):settings.getDouble(key);}
    public boolean edit(String key,double value,String text){
        if(!Double.isFinite(value))return false;
        if(key.equals("name")){if(text.codePointCount(0,text.length())>40||text.codePoints().anyMatch(Character::isISOControl))return false;name=text.trim();return true;}
        if(key.equals("enabled")){enabled=value!=0;return true;}
        var field=ModuleFields.moveRules().stream().filter(f->f.key().equals(key)).findFirst().orElse(null);if(field==null)return false;
        double next=Math.clamp(value,field.min(),field.max());if(field.scale()==20||field.choice()||key.equals("dispatchWeight"))next=Math.round(next);settings.putDouble(key,next);return true;
    }
    public CompoundTag save(){var tag=new CompoundTag();tag.putInt("Version",1);tag.putString("Type","move_rules");tag.putUUID("Id",id);tag.putString("Name",name);tag.putBoolean("Enabled",enabled);tag.put("Settings",settings.copy());return tag;}
    public static MoveRules load(CompoundTag tag){
        var rules=new MoveRules(tag.hasUUID("Id")?tag.getUUID("Id"):UUID.randomUUID());
        rules.edit("name",0,tag.getString("Name"));rules.enabled=tag.getBoolean("Enabled");
        var saved=tag.getCompound("Settings");
        rules.settings.putDouble("inheritMoveTarget", 0);
        for(var f:ModuleFields.moveRules())if(saved.contains(f.key()))rules.edit(f.key(),saved.getDouble(f.key()),"");
        return rules;
    }
    public boolean targetPresent(HeartBlockEntity heart) { return select(heart, false) != null; }
    int memoryTicks() { return value("lineOfSight") == 1 ? (int) value(SightMemory.FIELD) : 0; }

    private record Search(ServerLevel level, Vec3 center, Vec3 forward, org.joml.Quaterniond orientation,
                          double nearSquared, double farSquared, double coneCosine) {}

    private Search search(HeartBlockEntity heart) {
        if (value("dispatchNear") > value("dispatchFar") || !heart.isControlCore()
                && !HealthPhase.contains(heart.health().current(), heart.health().maximum(), value("phaseLower"), value("phaseUpper"))) return null;
        var level = (ServerLevel) heart.getLevel();
        var center = SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter());
        var body = Sable.HELPER.getContaining(level, heart.getBlockPos());
        var directions = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        var local = Vec3.atLowerCornerOf(directions[(int) value("dispatchFront")].getNormal());
        var orientation = body == null ? new org.joml.Quaterniond() : new org.joml.Quaterniond(body.logicalPose().orientation());
        var forward = dev.sablelife.attack.ContactBox.rotate(orientation, local);
        return new Search(level, center, forward, orientation, Math.pow(value("dispatchNear"), 2),
                Math.pow(value("dispatchFar"), 2), Math.cos(Math.toRadians(value("dispatchCone") / 2)));
    }

    private boolean withinDispatch(Search search, Vec3 point) {
        var delta = point.subtract(search.center());
        double distance = delta.lengthSqr();
        return distance >= search.nearSquared() && distance <= search.farSquared()
                && (value("dispatchCone") == 360 || distance <= 1e-6
                    || search.forward().dot(delta) >= search.coneCosine() * Math.sqrt(distance));
    }

    private boolean sees(HeartBlockEntity heart, Search search, LivingEntity target) {
        return value("lineOfSight") != 1
                || ModuleTargets.sensed(search.level(), heart, search.center(), search.orientation(), target, java.util.Set.of());
    }

    private boolean eligible(LivingEntity target) {
        return target != null && !target.isRemoved() && ModuleTargets.matches(target, (int) value("target"));
    }

    SightMemory.Target activeTarget(HeartBlockEntity heart, UUID id) {
        var level = (ServerLevel) heart.getLevel();
        var entity = ModuleTargets.resolve(level, id, heart.creatureId());
        return eligible(entity)
                ? new SightMemory.Target(id, SableBodyProbe.toWorld(level, entity.getBoundingBox().getCenter()), true)
                : null;
    }

    SightMemory.Target observe(HeartBlockEntity heart, SightMemory.Target last) {
        var search = search(heart);
        if (search == null || last == null) return null;
        var entity = ModuleTargets.resolve(search.level(), last.id(), heart.creatureId());
        if (!eligible(entity)) return null;

        var point = SableBodyProbe.toWorld(search.level(), entity.getBoundingBox().getCenter());
        boolean withinCurrent = withinDispatch(search, point);
        boolean withinLast = withinDispatch(search, last.point());
        if (!withinCurrent && !withinLast) return null;
        if (SightMemory.canRecheck(search.center(), point) && sees(heart, search, entity)) return withinCurrent ? new SightMemory.Target(last.id(), point, true) : null;
        return withinLast ? new SightMemory.Target(last.id(), last.point(), false) : null;
    }

    /** Validate the source's observed position. Hidden targets supply only their saved position. */
    SightMemory.Target accept(HeartBlockEntity heart, SightMemory.Target source, boolean verifySight) {
        if (source == null || !source.visible() && value("lineOfSight") != 1) return null;
        var search = search(heart);
        if (search == null || !withinDispatch(search, source.point())) return null;
        var entity = ModuleTargets.resolve(search.level(), source.id(), heart.creatureId());
        return eligible(entity) && (!verifySight || !source.visible() || sees(heart, search, entity)) ? source : null;
    }

    SightMemory.Target select(HeartBlockEntity heart) {
        return select(heart, true);
    }

    private SightMemory.Target select(HeartBlockEntity heart, boolean chooseBest) {
        var search = search(heart);
        if (search == null) return null;
        var bounds = new AABB(search.center(), search.center()).inflate(value("dispatchFar"));
        SightMemory.Target best = null;
        int bestRank = Integer.MAX_VALUE;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (var entity : ModuleTargets.query(search.level(), heart.creatureId(), bounds)) {
            if (!eligible(entity)) continue;
            var point = SableBodyProbe.toWorld(search.level(), entity.getBoundingBox().getCenter());
            if (!withinDispatch(search, point)) continue;
            var id = entity.getUUID();
            int rank = chooseBest ? ModuleTargets.rank(heart, entity) : 0;
            double distance = point.distanceToSqr(search.center());
            if (best != null && (rank > bestRank || rank == bestRank
                    && (distance > bestDistance || distance == bestDistance && id.compareTo(best.id()) >= 0))) continue;
            if (!sees(heart, search, entity)) continue;
            best = new SightMemory.Target(id, point, true);
            if (!chooseBest) return best;
            bestRank = rank;
            bestDistance = distance;
        }
        return best;
    }
}
