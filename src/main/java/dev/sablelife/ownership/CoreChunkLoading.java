package dev.sablelife.ownership;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.heart.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

/** Sable persists body tickets; world tickets are refreshed and owned separately per creature. */
public final class CoreChunkLoading {
    public static final int MAX_CREATURE_CHUNKS=256,MAX_SERVER_CHUNKS=1024;
    public static final SubLevelLoadingTicketType<UUID> BODY_TICKET=SubLevelLoadingTicketType.create(
            ResourceLocation.fromNamespaceAndPath("sablelife","life_core"),net.minecraft.core.UUIDUtil.CODEC);
    private static final TicketType<UUID> WORLD_TICKET=TicketType.create("sablelife_life_core",UUID::compareTo,40);
    private record Owner(ServerLevel level,UUID id){}
    private static final class Group {
        final Owner owner;final Set<ServerSubLevel> sources=new HashSet<>();
        Group(Owner owner){this.owner=owner;}
    }
    private static final class State {
        final Map<UUID,HeartBlockEntity> cores=new HashMap<>();
        final Map<Owner,Set<ChunkPos>> world=new HashMap<>();
        final Map<UUID,String> status=new HashMap<>();
        final Map<UUID,Integer> counts=new HashMap<>();
        final Map<Owner,Integer> missing=new HashMap<>();
    }
    private static final Map<MinecraftServer,State> states=new WeakHashMap<>();
    public static void register(){} // Initialize the ticket codec before Sable loads persistent tickets.
    private static State state(MinecraftServer server){return states.computeIfAbsent(server,s->new State());}
    public static void stopped(ServerStoppedEvent event){states.remove(event.getServer());}
    public static void observe(HeartBlockEntity core){
        if(!core.isControlCore()&&core.forceChunkLoading()&&core.getLevel() instanceof ServerLevel level)
            state(level.getServer()).cores.put(core.creatureId(),core);
    }
    public static String status(HeartBlockEntity core){
        if(!core.forceChunkLoading())return "off";
        if(core.health().state()==HeartState.DORMANT)return "waiting";
        if(!(core.getLevel() instanceof ServerLevel level))return "waiting";
        return state(level.getServer()).status.getOrDefault(core.creatureId(),"waiting");
    }
    public static int count(HeartBlockEntity core){return core.getLevel() instanceof ServerLevel level?state(level.getServer()).counts.getOrDefault(core.creatureId(),0):0;}
    public static Set<ChunkPos> chunks(MinecraftServer server,ServerLevel level,UUID owner){return Set.copyOf(state(server).world.getOrDefault(new Owner(level,owner),Set.of()));}
    /** World-space chunk columns; a touched boundary is not an extra occupied chunk. */
    public static Optional<Set<ChunkPos>> footprint(List<AABB> boxes){
        var result=new HashSet<ChunkPos>();
        for(var box:boxes){
            if(!Double.isFinite(box.minX+box.maxX+box.minZ+box.maxZ)||Math.max(Math.max(Math.abs(box.minX),Math.abs(box.maxX)),Math.max(Math.abs(box.minZ),Math.abs(box.maxZ)))>30000000)return Optional.empty();
            if(box.maxX<=box.minX||box.maxZ<=box.minZ)continue;
            int minX=(int)Math.floor(box.minX/16),maxX=(int)Math.ceil(box.maxX/16)-1;
            int minZ=(int)Math.floor(box.minZ/16),maxZ=(int)Math.ceil(box.maxZ/16)-1;
            if((long)(maxX-minX+1)*(maxZ-minZ+1)>MAX_CREATURE_CHUNKS)return Optional.empty();
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){result.add(new ChunkPos(x,z));if(result.size()>MAX_CREATURE_CHUNKS)return Optional.empty();}
        }return Optional.of(Set.copyOf(result));
    }
    private static void sources(Group group,Set<ServerSubLevel> desired){
        var container=SubLevelContainer.getContainer(group.owner.level);if(container==null)return;
        for(var body:group.sources)if(!desired.contains(body))container.removeForceLoadTicket(body,BODY_TICKET,group.owner.id);
        for(var body:desired)if(!group.sources.contains(body))container.addForceLoadTicket(body,BODY_TICKET,group.owner.id);
    }
    public static void release(HeartBlockEntity core){if(core.getLevel() instanceof ServerLevel level)release(level.getServer(),core.creatureId());}
    public static void release(MinecraftServer server,UUID id){
        LifeLifecycle.get(server).deathLoading(id,false);
        var s=state(server);s.cores.remove(id);s.counts.remove(id);s.status.remove(id);s.missing.keySet().removeIf(o->o.id.equals(id));
        for(var level:server.getAllLevels()){
            var container=SubLevelContainer.getContainer(level);if(container==null)continue;
            for(var entry:List.copyOf(container.collectForceLoadTickets().entrySet()))
                if(entry.getValue().stream().anyMatch(t->t.type().equals(BODY_TICKET)&&id.equals(t.key())))container.removeForceLoadTicket(entry.getKey(),BODY_TICKET,id);
        }
        for(var owner:List.copyOf(s.world.keySet()))if(owner.id.equals(id)){
            for(var chunk:s.world.remove(owner))owner.level.getChunkSource().removeRegionTicket(WORLD_TICKET,chunk,2,id,true);
        }
    }
    public static void tick(ServerTickEvent.Post event){refresh(event.getServer());}
    public static void refresh(MinecraftServer server){
        var s=state(server);var groups=new HashMap<Owner,Group>();
        for(var level:server.getAllLevels()){
            var container=SubLevelContainer.getContainer(level);if(container==null)continue;
            for(var entry:container.collectForceLoadTickets().entrySet())for(var ticket:entry.getValue())
                if(ticket.type().equals(BODY_TICKET)&&ticket.key() instanceof UUID id)groups.computeIfAbsent(new Owner(level,id),Group::new).sources.add(entry.getKey());
        }
        for(var core:List.copyOf(s.cores.values())){
            if(core.isRemoved()||!core.forceChunkLoading()||!(core.getLevel() instanceof ServerLevel level)){s.cores.remove(core.creatureId());continue;}
            groups.computeIfAbsent(new Owner(level,core.creatureId()),Group::new);
        }
        var desiredWorld=new HashMap<Owner,Set<ChunkPos>>();var budget=new HashMap<ServerLevel,Set<ChunkPos>>();int total=0;
        var lifecycle=LifeLifecycle.get(server);
        for(var group:groups.values().stream().sorted(Comparator.comparingInt((Group g)->s.world.containsKey(g.owner)||!g.sources.isEmpty()?0:1).thenComparing(g->g.owner.id.toString()).thenComparing(g->g.owner.level.dimension().location().toString())).toList()){
            var owner=group.owner;var core=s.cores.get(owner.id);
            if(core==null||core.isRemoved()||core.getLevel()!=owner.level)core=BodyOwnership.get(owner.level).loadedHeart(owner.level,owner.id);
            if(core!=null&&core.isRemoved())core=null;
            var bodies=new HashSet<ServerSubLevel>();String status="waiting";
            if(core!=null&&!core.isControlCore()&&core.forceChunkLoading()&&core.health().state()!=HeartState.DORMANT
                    &&(core.health().state()!=HeartState.DEAD||lifecycle.deathDue(owner.id))){
                var root=Sable.HELPER.getContaining(owner.level,core.getBlockPos());var graph=SableBodyProbe.inspect(owner.level,core.getBlockPos());
                if(root==null)status="unassembled";
                else if(graph.otherHearts()>0)status="duplicate";
                else {for(var body:SubLevelHelper.getConnectedChain(root))if(body instanceof ServerSubLevel serverBody&&!body.isRemoved())bodies.add(serverBody);status="active";s.cores.put(owner.id,core);}
            }else if(core==null&&lifecycle.deathDue(owner.id)&&BodyOwnership.get(owner.level).hasOwnedWithin(owner.level,owner.id,group.sources.stream().map(ServerSubLevel::getUniqueId).collect(java.util.stream.Collectors.toSet()))){
                group.sources.stream().filter(b->!b.isRemoved()).forEach(bodies::add);status="clearing";
            }
            if(core!=null)s.missing.remove(owner);
            if(core==null&&bodies.isEmpty()&&!group.sources.isEmpty()&&!lifecycle.isDying(owner.id)
                    &&BodyOwnership.get(owner.level).hasOwnedWithin(owner.level,owner.id,group.sources.stream().map(ServerSubLevel::getUniqueId).collect(java.util.stream.Collectors.toSet()))
                    &&s.missing.merge(owner,1,Integer::sum)<=100)group.sources.stream().filter(b->!b.isRemoved()).forEach(bodies::add);
            if(bodies.isEmpty()){sources(group,Set.of());var current=s.cores.get(owner.id);if(current==null||current.getLevel()==owner.level){s.status.put(owner.id,status);s.counts.put(owner.id,0);}continue;}
            var boxes=new ArrayList<AABB>();for(var body:bodies){body.updateBoundingBox();var b=body.boundingBox();boxes.add(new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()));}
            var footprint=footprint(boxes);var chunks=new HashSet<ChunkPos>(footprint.orElse(Set.of()));
            var point=lifecycle.rewardPoint(owner.id,owner.level.dimension().location());if(point!=null)chunks.add(new ChunkPos(net.minecraft.core.BlockPos.containing(point)));
            var accepted=budget.computeIfAbsent(owner.level,k->new HashSet<>());var union=new HashSet<>(accepted);union.addAll(chunks);
            if(footprint.isEmpty()||chunks.size()>MAX_CREATURE_CHUNKS||total+union.size()-accepted.size()>MAX_SERVER_CHUNKS){sources(group,Set.of());s.status.put(owner.id,"limit");s.counts.put(owner.id,0);continue;}
            total+=union.size()-accepted.size();accepted.addAll(chunks);sources(group,bodies);desiredWorld.put(owner,Set.copyOf(chunks));s.status.put(owner.id,status);s.counts.put(owner.id,chunks.size());
        }
        // A zero-delay death can remove its core before the reward chunk finishes loading.
        for(var level:server.getAllLevels())for(var reward:lifecycle.loadingRewards(level.dimension().location()).entrySet()){
            var owner=new Owner(level,reward.getKey());if(desiredWorld.containsKey(owner))continue;
            var chunk=new ChunkPos(net.minecraft.core.BlockPos.containing(reward.getValue()));var accepted=budget.computeIfAbsent(level,k->new HashSet<>());
            if(!accepted.contains(chunk)&&total>=MAX_SERVER_CHUNKS)continue;
            if(accepted.add(chunk))total++;desiredWorld.put(owner,Set.of(chunk));s.status.compute(owner.id,(id,old)->"limit".equals(old)?"limit":s.cores.get(id)!=null&&s.cores.get(id).getLevel()!=level&&"active".equals(old)?old:"clearing");s.counts.put(owner.id,1);
        }
        for(var entry:s.world.entrySet())for(var chunk:entry.getValue())if(!desiredWorld.getOrDefault(entry.getKey(),Set.of()).contains(chunk))
            entry.getKey().level.getChunkSource().removeRegionTicket(WORLD_TICKET,chunk,2,entry.getKey().id,true);
        for(var entry:desiredWorld.entrySet())for(var chunk:entry.getValue())if(server.getTickCount()%10==0||!s.world.getOrDefault(entry.getKey(),Set.of()).contains(chunk))entry.getKey().level.getChunkSource().addRegionTicket(WORLD_TICKET,chunk,2,entry.getKey().id,true);
        s.world.clear();s.world.putAll(desiredWorld);
        s.counts.clear();desiredWorld.forEach((owner,chunks)->s.counts.merge(owner.id,chunks.size(),Integer::sum));
        var known=new HashSet<>(s.cores.keySet());groups.keySet().forEach(o->known.add(o.id));desiredWorld.keySet().forEach(o->known.add(o.id));s.status.keySet().retainAll(known);s.counts.keySet().retainAll(known);s.missing.keySet().retainAll(groups.keySet());
    }
}
