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
            if (module.anchors().getFirst().bodyId().equals(body.getUniqueId())) for(var row:module.armorRules().rows()) {
                if(!row.enabled()||row.channel()==0||row.channel()>heart.wireless().size())continue;
                int power=row.source()==0?heart.wireless().output(row.channel()-1):heart.wireless().input(row.channel()-1);
                if(row.when()==0?power>0:power==0)modifier=Math.min(modifier,row.modifier());
            }
        }
        // Preserve strongest-protection precedence on overlapping marks; modifiers do not stack.
        if (modifier == Double.POSITIVE_INFINITY) return amount;
        return (float) (amount * (1 + Math.clamp(modifier, -100, 1000) / 100.));
    }
    private ModuleArmor() {}
}
