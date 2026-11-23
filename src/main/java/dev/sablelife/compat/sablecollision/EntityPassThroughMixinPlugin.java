package dev.sablelife.compat.sablecollision;

import com.mojang.logging.LogUtils;
import dev.sablelife.compat.ModVersion;
import dev.sablelife.compat.OptionalMod;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class EntityPassThroughMixinPlugin implements IMixinConfigPlugin {
    private static final String SABLE = "sable";
    private static final String CLASS_ROOT = "dev/ryanhcode/sable/";
    private static final String MINIMUM_SABLE = "2.0.5";

    // Sable 2.0.5 and 2.0.6 have identical hooks and callers. Future releases with the same
    // bytecode can use the same adapter, including the injected tracking methods and packet semantics.
    private static final Map<String, String> HOOKS = Map.of(
            "sublevel/entity_collision/SubLevelEntityCollision", "910475ca6e8321f53ba8ba6102838915726f8d2ae0b105ada701f9d6de862fda",
            "mixin/entity/entity_sublevel_collision/EntityMixin", "69c358c17bca9f427ac1b3ce020f93126d666090dd6737e077d46edb1d448fc8",
            "mixin/entity/entity_sublevel_collision/LivingEntityMixin", "c75b3735adf5ea22b2104b1d89490283ea0d7a5f73c0c590f03bcb54ee947ea6",
            "mixin/entity/entities_stick_sublevels/player/ServerboundMovePlayerPacketMixin", "6394ba9421ce92fca031788e2c6b2c52adb0da00383cdb2a7553dd3f114ac147",
            "mixin/entity/entities_stick_sublevels/player/ServerPlayerMixin", "4c60094c858e95d736295699497efc4a421b954f652b949adcd7a2c84d229747",
            "mixin/entity/entities_stick_sublevels/player/LocalPlayerMixin", "1f11619e0b9fc9b75534420c7196ad05fb41c27c0e8709dd17b67337de3b0239",
            "mixinhelpers/CanFallAtleastHelper", "24d986df0cae925be7c699fcf42364b98e868775f630f4510b2b9915fc6e443f",
            "mixin/entity/entity_sublevel_collision/PlayerMixin", "da0375f8b27c15295e82292e0e4464044a74bd25e943e9882d15c6deb7e24eb2",
            "mixin/player_standup/PlayerMixin", "2e1f2a8146731428b10b5b5f44dd9c2ec3bf74401244aa01ba0caa966d2893ac");

    private static volatile Boolean compatible;

    public static boolean supported() {
        var cached = compatible;
        return cached == null ? inspect() : cached;
    }

    private static synchronized boolean inspect() {
        if (compatible != null) return compatible;
        if (FMLLoader.getLoadingModList() == null) return false;
        var version = OptionalMod.version(SABLE);
        compatible = ModVersion.atLeast(version, MINIMUM_SABLE) && matchesBytecode();
        if (compatible) LogUtils.getLogger().info("Sable Life enabled entity pass-through for Sable {}", version);
        else if (!version.isEmpty()) LogUtils.getLogger().warn("Sable Life skipped entity pass-through for unverified Sable {}", version);
        return compatible;
    }

    private static boolean matchesBytecode() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var hook : HOOKS.entrySet()) {
                byte[] code = OptionalMod.classBytes(SABLE, CLASS_ROOT + hook.getKey());
                if (code == null || !HexFormat.of().formatHex(digest.digest(code)).equals(hook.getValue())) return false;
            }
            return true;
        } catch (NoSuchAlgorithmException ignored) {
            return false;
        }
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) { return supported(); }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
