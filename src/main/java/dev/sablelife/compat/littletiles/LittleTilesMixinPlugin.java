package dev.sablelife.compat.littletiles;

import dev.sablelife.compat.OptionalMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;

public final class LittleTilesMixinPlugin implements IMixinConfigPlugin {
    private Boolean compatible;

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (compatible == null) compatible = inspect();
        return compatible;
    }

    private static boolean inspect() {
        if (FMLLoader.getDist() != Dist.CLIENT) return false;
        var tiles = OptionalMod.version("littletiles");
        if (tiles.isEmpty()) return false;
        if (!OptionalMod.version("sablelife_lt_recovery").isEmpty()) {
            com.mojang.logging.LogUtils.getLogger().info("Sable Life skipped built-in LittleTiles mesh recovery because the standalone recovery addon is installed");
            return false;
        }
        var creativeCore = OptionalMod.version("creativecore");
        var sable = OptionalMod.version("sable");
        boolean result = LittleTilesMixinContract.supported(tiles, creativeCore, sable)
                && LittleTilesMixinContract.inspect(name -> OptionalMod.classBytes("littletiles", name))
                && LittleTilesMixinContract.inspectCreativeCore(OptionalMod.classBytes("creativecore", LittleTilesMixinContract.CHUNK_LAYER_MAP));
        var logger = com.mojang.logging.LogUtils.getLogger();
        if (result) logger.info("Sable Life enabled LittleTiles mesh recovery for LittleTiles {}, CreativeCore {}, Sable {}", tiles, creativeCore, sable);
        else logger.warn("Sable Life skipped optional LittleTiles mesh recovery: unsupported dependency or interface (LittleTiles {}, CreativeCore {}, Sable {})",
                tiles, creativeCore, sable);
        return result;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
