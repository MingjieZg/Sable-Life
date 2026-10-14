package dev.sablelife.mixin;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Vanilla's four-entry lookup cache must not outlive a Sable plot chunk replacement. */
@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccess {
    @Invoker("clearCache") void sablelife$clearCache();
}
