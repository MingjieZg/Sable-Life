package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
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
    public static MoveRules load(CompoundTag tag){var rules=new MoveRules(tag.hasUUID("Id")?tag.getUUID("Id"):UUID.randomUUID());rules.edit("name",0,tag.getString("Name"));rules.enabled=tag.getBoolean("Enabled");var saved=tag.getCompound("Settings");for(var f:ModuleFields.moveRules())if(saved.contains(f.key()))rules.edit(f.key(),saved.getDouble(f.key()),"");return rules;}
    public boolean targetPresent(HeartBlockEntity heart) {
        var level = (ServerLevel) heart.getLevel();
        if (value("dispatchNear") > value("dispatchFar")
                || !heart.isControlCore() && !HealthPhase.contains(heart.health().current(), heart.health().maximum(), value("phaseLower"), value("phaseUpper"))) return false;
        var center = SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter());
        var body = Sable.HELPER.getContaining(level, heart.getBlockPos());
        var directions = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        var local = Vec3.atLowerCornerOf(directions[(int) value("dispatchFront")].getNormal());
        var forward = body == null ? local : body.logicalPose().transformPosition(heart.getBlockPos().getCenter().add(local)).subtract(center).normalize();
        double far = value("dispatchFar"), near = value("dispatchNear");
        for (var victim : ModuleTargets.query(level, heart.creatureId(), new AABB(center, center).inflate(far))) {
            if (!ModuleTargets.matches(victim,(int)value("target"))) continue;
            var point = SableBodyProbe.toWorld(level, victim.getBoundingBox().getCenter()); var delta = point.subtract(center);
            double distance = delta.length();
            if (distance < near || distance > far) continue;
            if (value("dispatchCone") < 360 && distance > .001
                    && forward.dot(delta.normalize()) < Math.cos(Math.toRadians(value("dispatchCone") / 2))) continue;
            if (value("lineOfSight") == 1) {
                var eye = SableBodyProbe.toWorld(level, victim.getEyePosition());
                var start = center.add(eye.subtract(center).normalize().scale(.8));
                if (!ModuleTargets.visible(level,heart.creatureId(),start,victim)) continue;
            }
            return true;
        }
        return false;
    }
}
