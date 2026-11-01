package dev.sablelife.module;

import dev.sablelife.heart.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.commands.CommandSource;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec2;
import java.util.*;

/** Virtual command blocks execute as a level-2 block source at the moving core. */
public final class ModuleCommands {
    private static final class State { boolean powered; final Map<UUID,Long> due=new HashMap<>(); final Map<UUID,Integer> results=new HashMap<>(); }
    private final Map<UUID,State> states=new HashMap<>();
    public void clear(){states.clear();}
    public void tick(HeartBlockEntity heart){
        if(!(heart.getLevel() instanceof ServerLevel level))return;
        if(heart.health().state()!=HeartState.ALIVE||!heart.wireless().master()||!level.getServer().isCommandBlockEnabled()){clear();return;}
        long now=level.getGameTime();int budget=2048;var keep=new HashSet<UUID>();
        for(var module:heart.modules().all()){
            if(!module.type().equals("command"))continue;keep.add(module.id());
            var state=states.computeIfAbsent(module.id(),ignored->new State());
            boolean powered=HeartModules.triggered(module,heart), rising=powered&&!state.powered;
            boolean hurtOnce=powered&&ModuleHurt.once(module)&&heart.modules().hurtTriggers.start(module);
            boolean previousRan=false;int previousResult=0;
            for(var row:module.commands().rows()){
                if(heart.isRemoved()||heart.health().state()!=HeartState.ALIVE||!heart.wireless().master()||heart.modules().find(module.id())!=module)break;
                boolean run=row.enabled()&&powered&&switch(row.mode()){
                    case 0 -> rising||hurtOnce;
                    case 1 -> (hurtOnce||!ModuleHurt.once(module))&&now>=state.due.getOrDefault(row.id(),0L);
                    default -> previousRan;
                };
                boolean allowed=!row.conditional()||(previousRan&&previousResult>0);
                previousRan=run;previousResult=0;
                if(!run)continue;
                if(!allowed){state.results.put(row.id(),0);continue;}
                if(budget--<=0)break;
                if(row.mode()==1)state.due.put(row.id(),now+row.interval());
                int[] result={0};
                if(!row.command().isBlank()){
                    var source=level.getServer().createCommandSourceStack().withSource(CommandSource.NULL).withPermission(2).withLevel(level)
                            .withPosition(SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter())).withRotation(Vec2.ZERO).withSuppressedOutput()
                            .withCallback((success,value)->{if(success)result[0]=Math.max(result[0],Math.max(1,value));});
                    level.getServer().getCommands().performPrefixedCommand(source,row.command());
                }
                previousRan=true;previousResult=result[0];state.results.put(row.id(),previousResult);
            }
            state.powered=powered;
            if(hurtOnce)heart.modules().hurtTriggers.finish(module.id());
            if(!powered)state.due.clear();
        }
        states.keySet().retainAll(keep);
    }
    public CompoundTag snapshot(UUID module){var tag=new CompoundTag();var s=states.get(module);var rows=new ListTag();if(s!=null){tag.putBoolean("Powered",s.powered);s.results.forEach((id,result)->{var r=new CompoundTag();r.putUUID("Id",id);r.putInt("Success",result);rows.add(r);});}tag.put("Rows",rows);return tag;}
}
