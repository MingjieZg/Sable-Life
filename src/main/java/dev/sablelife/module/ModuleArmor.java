package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;

/** Resolve protection from the actual hit part, never from the connected creature as a whole. */
public final class ModuleArmor {
    public static float damage(HeartBlockEntity heart, BlockPos hit, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return 0;
        var body = Sable.HELPER.getContaining(heart.getLevel(), hit);
        if (body == null || body.isRemoved()) return amount;
        double modifier = Double.POSITIVE_INFINITY;
        for (var module : heart.modules().all()) {
            if (!module.type().equals("armor") || !HeartModules.triggered(module, heart)) continue;
            if (module.anchors().getFirst().bodyId().equals(body.getUniqueId())) modifier = Math.min(modifier, module.value("damageModifier"));
        }
        // Preserve strongest-protection precedence on overlapping marks; modifiers do not stack.
        if (modifier == Double.POSITIVE_INFINITY) return amount;
        return (float) (amount * (1 + Math.clamp(modifier, -100, 1000) / 100.));
    }
    private ModuleArmor() {}
}
