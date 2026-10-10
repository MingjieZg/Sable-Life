package dev.sablelife.ownership;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
/** New blueprint bodies must not run block entities before their deferred NBT commit. */
public final class ArenaBuildTicks {
 private static final Map<ServerLevel,State> LEVELS=new WeakHashMap<>();
 private static final class State {final Map<UUID,Set<UUID>> groups=new HashMap<>();final Set<UUID> blocked=new HashSet<>();void refresh(){blocked.clear();groups.values().forEach(blocked::addAll);}}
 public static void hold(ServerLevel level,UUID job,Set<UUID> bodies){
  if(bodies.isEmpty())return;var state=LEVELS.computeIfAbsent(level,k->new State());if(bodies.equals(state.groups.get(job)))return;state.groups.put(job,Set.copyOf(bodies));state.refresh();
 }
 public static void release(ServerLevel level,Set<UUID> bodies){
  var state=LEVELS.get(level);if(state==null)return;
  state.groups.replaceAll((id,set)->{var next=new HashSet<>(set);next.removeAll(bodies);return next;});state.groups.values().removeIf(Set::isEmpty);state.refresh();if(state.blocked.isEmpty())LEVELS.remove(level);
 }
 public static boolean blocked(BlockEntity entity){
  if(!(entity.getLevel() instanceof ServerLevel level))return false;var state=LEVELS.get(level);if(state==null||state.blocked.isEmpty())return false;
  var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(level,entity.getBlockPos());return body!=null&&state.blocked.contains(body.getUniqueId());
 }
 private ArenaBuildTicks(){}
}
