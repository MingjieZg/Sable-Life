package dev.sablelife.module;

import dev.sablelife.heart.*;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Per-module damage activation. Runtime is deliberately absent from saved blueprints. */
public final class ModuleHurt {
    public static boolean once(VirtualModule m) { return m.value("trigger") == 5 && m.value("hurtMode") == 0; }
    private static final class State {
        long due, end;
        boolean pending, active, started;
    }
    private final Map<UUID, State> states = new HashMap<>();
    public void clear() { states.clear(); }
    public void reset(UUID id) { states.remove(id); }
    public boolean powered(UUID id) { var s = states.get(id); return s != null && s.active; }
    public void finish(UUID id) { var s=states.get(id); if(s!=null) { s.active=false; s.pending=false; } }
    public void completed(VirtualModule m) { var s=states.get(m.id());if(once(m)&&s!=null&&s.started) finish(m.id()); }
    public boolean start(VirtualModule m) {
        if (!once(m)) return true;
        var s=states.get(m.id()); if(s==null || !s.active || s.started) return false;
        s.started=true; return true;
    }
    public void hurt(HeartBlockEntity heart) {
        if (heart.health().state()!=HeartState.ALIVE || !heart.wireless().master()) return;
        long now=heart.getLevel().getGameTime();
        for(var m:heart.modules().all()) {
            if(m.value("trigger")!=5 || !HeartModules.status(m,heart).equals("valid")) continue;
            var s=states.computeIfAbsent(m.id(),ignored->new State());
            if(now<s.due || s.pending || s.active && (once(m) || m.value("hurtMode")==2)) continue;
            s.due=now+(long)m.value("hurtCooldown"); s.pending=true;
        }
    }
    public void tick(HeartBlockEntity heart) {
        if(heart.health().state()!=HeartState.ALIVE || !heart.wireless().master()) { clear(); return; }
        long now=heart.getLevel().getGameTime();
        states.entrySet().removeIf(e->{var m=heart.modules().find(e.getKey());return m==null || m.value("trigger")!=5 || !HeartModules.status(m,heart).equals("valid");});
        for(var e:states.entrySet()) {
            var s=e.getValue(); var m=heart.modules().find(e.getKey());
            if(s.pending) { s.pending=false; s.active=true; s.started=false; s.end=m.value("hurtMode")==2 || once(m)&&m.type().equals("move_set") ? Long.MAX_VALUE : now+(m.value("hurtMode")==1 ? (long)m.value("hurtDuration") : onceTicks(m)); }
            else if(s.active && now>=s.end) s.active=false;
        }
    }
    private static long onceTicks(VirtualModule m) {
        return Math.max(1,(long)Math.ceil(switch(m.type()) {
            case "slash" -> m.value("range")/m.value("slashSpeed")*20+3;
            case "sphere_blast" -> m.value("chargeTime")+2;
            case "laser" -> m.value("chargeTime")+m.value("beamHits")*m.value("hitInterval")+2;
            case "homing" -> m.value("homingDeploy")+m.value("chargeTime")+m.value("homingLife")+2;
            case "flame" -> m.value("range")/m.value("flameSpeed")+3;
            case "pull" -> m.value("pullWarning")+m.value("pullDuration")+6;
            case "quake" -> m.value("warningTime")+m.value("quakeRange")/m.value("quakeSpeed")+(m.value("quakeWaves")-1)*m.value("quakeGap")+16;
            case "ground_strike" -> m.value("warningTime")+Math.max(m.value("strikeRandomCount"),m.value("strikeRange")/m.value("strikeStep")+2)*m.value("waveDelay")+m.value("strikeHeight")/m.value("strikeSpeed")+3;
            case "linear_movement" -> m.value("chargeTime")+(m.value("linearMode")==1?(m.value("moveAim")==1?m.value("moveTargetRange"):m.value("moveDistance"))/m.value("travelSpeed")*20:0)+4;
            case "sequence" -> m.sequence().stream().mapToLong(PulseSequence.Step::ticks).sum()+2;
            case "arena" -> m.value("countdown")+2;
            default -> 1;
        }));
    }
    public CompoundTag snapshot(UUID id,long now) {
        var tag=new CompoundTag();var s=states.get(id);
        if(s!=null) {tag.putBoolean("Active",s.active||s.pending);tag.putInt("Cooldown",(int)Math.max(0,s.due-now));}
        return tag;
    }
}
