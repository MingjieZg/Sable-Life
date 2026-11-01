package dev.sablelife.module;
import dev.sablelife.SableLife;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Runtime-only leases: overlapping casters share one position, but release independently. */
@EventBusSubscriber(modid=SableLife.MOD_ID)
public final class PlayerFreeze {
    public record Source(UUID core,UUID module){}
    @FunctionalInterface public interface Follow {Vec3 point(ServerPlayer player);}
    private static final Map<MinecraftServer,Map<UUID,Lock>> SERVERS=new WeakHashMap<>();
    private static final class Lock {
        Vec3 point;final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final boolean grounded;final Map<Source,Long> leases=new HashMap<>();
        final Map<Source,Follow> follows=new HashMap<>();long followTick=Long.MIN_VALUE;
        Lock(ServerPlayer p){point=dev.sablelife.compat.sable.SableBodyProbe.toWorld(p.level(),p.position());dimension=p.level().dimension();grounded=p.onGround();}
    }
    public static boolean eligible(ServerPlayer p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator();}
    private static Map<UUID,Lock> locks(MinecraftServer server){return SERVERS.computeIfAbsent(server,s->new HashMap<>());}
    private static void sync(ServerPlayer p,Lock lock){long now=p.serverLevel().getGameTime();int ticks=lock==null?0:(int)Math.clamp(lock.leases.values().stream().mapToLong(v->v).max().orElse(now)-now,0,1200);net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new FreezeStatePacket(ticks,lock==null?p.position():lock.point));}
    public static void apply(ServerPlayer p,Source source,int ticks){
        if(!eligible(p)||ticks<1)return;var all=locks(p.server);if(!all.containsKey(p.getUUID())&&all.size()>=256)return;
        var lock=current(p);if(lock!=null&&!lock.dimension.equals(p.level().dimension())){all.remove(p.getUUID());lock=null;}
        if(lock==null){p.stopRiding();p.stopFallFlying();lock=new Lock(p);all.put(p.getUUID(),lock);}
        if(lock.leases.size()>=64&&!lock.leases.containsKey(source))return;
        lock.leases.merge(source,p.serverLevel().getGameTime()+Math.clamp(ticks,1,1200),Math::max);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;sync(p,lock);
    }
    public static boolean attached(ServerPlayer p){var lock=current(p);return lock!=null&&!lock.follows.isEmpty();}
    public static boolean grab(ServerPlayer p,Source source,int ticks,Follow follow){
        if(!eligible(p)||attached(p))return false;var target=follow.point(p);if(target==null||!safePath(p,p.position(),target))return false;
        apply(p,source,ticks);var lock=current(p);if(lock==null||!lock.leases.containsKey(source))return false;
        lock.follows.put(source,follow);lock.followTick=Long.MIN_VALUE;pin(p);return attached(p);
    }
    /** Check actual player volume along each short move; never load new terrain or tunnel through it. */
    private static boolean safePath(ServerPlayer p,Vec3 from,Vec3 to){
        return RestraintPath.safe(p,from,to);
    }
    private static Lock current(ServerPlayer p){
        var all=SERVERS.get(p.server);if(all==null)return null;var lock=all.get(p.getUUID());if(lock==null)return null;
        if(!eligible(p)||!lock.dimension.equals(p.level().dimension())){all.remove(p.getUUID());sync(p,null);return null;}
        long now=p.serverLevel().getGameTime();lock.leases.values().removeIf(until->until<=now);lock.follows.keySet().retainAll(lock.leases.keySet());
        if(!lock.follows.isEmpty()&&lock.followTick!=now){
            lock.followTick=now;boolean moved=false;
            for(var source:List.copyOf(lock.follows.keySet())){var point=lock.follows.get(source).point(p);if(point==null||!safePath(p,lock.point,point)){lock.follows.remove(source);lock.leases.remove(source);continue;}moved=point.distanceToSqr(lock.point)>1e-10;lock.point=point;break;}
            if(moved||lock.follows.isEmpty())sync(p,lock);
        }
        if(lock.leases.isEmpty()){all.remove(p.getUUID());sync(p,null);return null;}return lock;
    }
    public static boolean frozen(ServerPlayer p){return current(p)!=null;}
    public static void release(MinecraftServer server,Source source){var all=SERVERS.get(server);if(all==null)return;for(var id:List.copyOf(all.keySet())){var lock=all.get(id);if(lock.leases.remove(source)==null)continue;lock.follows.remove(source);var p=server.getPlayerList().getPlayer(id);if(lock.leases.isEmpty()){all.remove(id);if(p!=null)sync(p,null);}else if(p!=null)sync(p,lock);}}
    public static ServerboundMovePlayerPacket constrain(ServerPlayer p,ServerboundMovePlayerPacket packet){var lock=current(p);return lock==null?packet:new ServerboundMovePlayerPacket.PosRot(lock.point.x,lock.point.y,lock.point.z,packet.getYRot(p.getYRot()),packet.getXRot(p.getXRot()),lock.grounded);}
    private static void pin(ServerPlayer p){var lock=current(p);if(lock==null)return;p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;p.stopFallFlying();if(p.isPassenger())p.stopRiding();if(p.position().distanceToSqr(lock.point)>1e-8)p.connection.teleport(lock.point.x,lock.point.y,lock.point.z,p.getYRot(),p.getXRot());if(p.tickCount%20==0)sync(p,lock);}
    @SubscribeEvent public static void pre(PlayerTickEvent.Pre e){if(e.getEntity() instanceof ServerPlayer p)pin(p);}
    @SubscribeEvent public static void post(PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p)pin(p);}
    private static void forget(ServerPlayer p){var all=SERVERS.get(p.server);if(all!=null&&all.remove(p.getUUID())!=null)sync(p,null);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)forget(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)forget(p);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)forget(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){SERVERS.remove(e.getServer());}
    private PlayerFreeze(){}
}
