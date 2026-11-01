package dev.sablelife.module;
import dev.sablelife.SableLife;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Position-only ordinary-mob attachments. AI, movement logic and persistent flags remain native. */
@EventBusSubscriber(modid=SableLife.MOD_ID)
public final class MobGrab {
    @FunctionalInterface public interface Follow {Vec3 point(Mob mob);}
    private static final class Hold {
        final ServerLevel level;final Map<PlayerFreeze.Source,Long> freezes=new HashMap<>();
        PlayerFreeze.Source source;long until;Follow follow;Vec3 point;long tick=Long.MIN_VALUE;
        Hold(ServerLevel level,Vec3 point){this.level=level;this.point=point;}
    }
    private static final Map<MinecraftServer,Map<UUID,Hold>> SERVERS=new WeakHashMap<>();
    public static boolean eligible(LivingEntity e){return e instanceof Mob&&!(e instanceof ModuleTargets.LifeTarget)&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator();}
    public static boolean grab(Mob mob,PlayerFreeze.Source source,int ticks,Follow follow){
        if(!(mob.level() instanceof ServerLevel level)||!eligible(mob)||held(mob)||ticks<1)return false;
        var all=SERVERS.computeIfAbsent(level.getServer(),s->new HashMap<>());if(all.size()>=256&&!all.containsKey(mob.getUUID()))return false;
        var point=follow.point(mob);if(point==null||!RestraintPath.safe(mob,mob.position(),point))return false;
        var hold=current(mob);if(hold==null){hold=new Hold(level,mob.position());all.put(mob.getUUID(),hold);}
        mob.stopRiding();hold.source=source;hold.until=level.getGameTime()+Math.clamp(ticks,1,1200);hold.follow=follow;hold.tick=Long.MIN_VALUE;pin(mob);return held(mob);
    }
    public static boolean freeze(Mob mob,PlayerFreeze.Source source,int ticks){
        if(!(mob.level() instanceof ServerLevel level)||!eligible(mob)||ticks<1)return false;
        var all=SERVERS.computeIfAbsent(level.getServer(),s->new HashMap<>());var hold=current(mob);
        if(hold==null){if(all.size()>=256)return false;hold=new Hold(level,mob.position());all.put(mob.getUUID(),hold);}
        if(hold.freezes.size()>=64&&!hold.freezes.containsKey(source))return false;
        hold.freezes.merge(source,level.getGameTime()+Math.clamp(ticks,1,1200),Math::max);mob.stopRiding();pin(mob);return true;
    }

    private static Hold current(Mob mob){
        if(!(mob.level() instanceof ServerLevel level))return null;var all=SERVERS.get(level.getServer());if(all==null)return null;var hold=all.get(mob.getUUID());if(hold==null)return null;
        long now=level.getGameTime();if(!eligible(mob)||hold.level!=level){all.remove(mob.getUUID());return null;}
        hold.freezes.values().removeIf(until->until<=now);
        if(hold.follow!=null&&now>=hold.until){hold.follow=null;hold.source=null;}
        if(hold.follow!=null&&hold.tick!=now){hold.tick=now;var point=hold.follow.point(mob);if(point==null||!RestraintPath.safe(mob,hold.point,point)){hold.follow=null;hold.source=null;}else hold.point=point;}
        if(hold.follow==null&&hold.freezes.isEmpty()){all.remove(mob.getUUID());return null;}
        return hold;
    }
    public static boolean held(Mob mob){var hold=current(mob);return hold!=null&&hold.follow!=null;}
    public static boolean frozen(Mob mob){return current(mob)!=null;}
    public static void release(MinecraftServer server,PlayerFreeze.Source source){var all=SERVERS.get(server);if(all!=null)all.values().removeIf(hold->{hold.freezes.remove(source);if(source.equals(hold.source)){hold.follow=null;hold.source=null;}return hold.follow==null&&hold.freezes.isEmpty();});}
    private static void pin(Mob mob){var hold=current(mob);if(hold==null)return;mob.stopRiding();mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
        // Keep vanilla entity tracking in charge of relative movement/interpolation packets.
        if(mob.position().distanceToSqr(hold.point)>1e-10)mob.setPos(hold.point);
    }
    @SubscribeEvent public static void pre(EntityTickEvent.Pre e){if(e.getEntity() instanceof Mob mob)pin(mob);}
    @SubscribeEvent public static void post(EntityTickEvent.Post e){if(e.getEntity() instanceof Mob mob)pin(mob);}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent e){if(e.getLevel() instanceof ServerLevel level){var all=SERVERS.get(level.getServer());if(all!=null)all.remove(e.getEntity().getUUID());}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){SERVERS.remove(e.getServer());}
    private MobGrab(){}
}
