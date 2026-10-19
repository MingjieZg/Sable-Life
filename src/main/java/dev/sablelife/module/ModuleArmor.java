package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;

/** Resolve per-part rules or an explicitly enabled whole-creature modifier, without stacking. */
public final class ModuleArmor {
    public static float damage(HeartBlockEntity heart, BlockPos hit, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return 0;
        var body = Sable.HELPER.getContaining(heart.getLevel(), hit);
        if (body == null || body.isRemoved()) return amount;
        double modifier = Double.POSITIVE_INFINITY;Boolean ownWholeHit=null;
        for (var module : heart.modules().all()) {
            if (!module.type().equals("armor") || !HeartModules.triggered(module, heart)) continue;
            if(module.value("armorWholeBody")==1){
                if(ownWholeHit==null){var core=Sable.HELPER.getContaining(heart.getLevel(),heart.getBlockPos());ownWholeHit=core!=null&&dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(core).contains(body);}
                if(!ownWholeHit)continue;

            }else if(!module.anchors().getFirst().bodyId().equals(body.getUniqueId()))continue;
            for(var row:module.armorRules().rows()) {
                if(row.enabled()&&signal(heart,row.source(),row.when(),row.channel()))modifier=Math.min(modifier,row.modifier());
            }
        }
        // Preserve strongest-protection precedence on overlapping marks; modifiers do not stack.
        if (modifier == Double.POSITIVE_INFINITY) return amount;
        return (float) (amount * (1 + Math.clamp(modifier, -100, 1000) / 100.));
    }
    private static boolean signal(HeartBlockEntity heart,int source,int when,int channel){
        if(channel<=0||channel>heart.wireless().size())return false;
        int power=source==0?heart.wireless().output(channel-1):heart.wireless().input(channel-1);
        return when==0?power>0:power==0;
    }
    private ModuleArmor() {}
}
