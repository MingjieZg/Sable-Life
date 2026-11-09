package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;

/** Resolve per-part rules or an explicitly enabled whole-creature modifier, without stacking. */
public final class ModuleArmor {
    public static final double SURVIVAL_MAX_REDUCTION=90;
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
            double current=activeModifier(heart,module);
            if(heart.isSurvivalCore()&&current<0)
                current=Math.max(current,heart.modules().armorBilling.fundedModifier(heart,module));
            modifier=Math.min(modifier,current);
        }
        // Preserve strongest-protection precedence on overlapping marks; modifiers do not stack.
        if (modifier == Double.POSITIVE_INFINITY) return amount;
        return (float) (amount * (1 + Math.clamp(modifier, -100, 1000) / 100.));
    }
    private static double activeModifier(HeartBlockEntity heart,VirtualModule module){
        double modifier=Double.POSITIVE_INFINITY;
        for(var row:module.armorRules().rows())
            if(row.enabled()&&signal(heart,row.source(),row.when(),row.channel()))modifier=Math.min(modifier,row.modifier());
        return heart.isSurvivalCore()?Math.max(-SURVIVAL_MAX_REDUCTION,modifier):modifier;
    }

    public static double estimatedReduction(VirtualModule module){
        double reduction=0;
        for(var row:module.armorRules().rows())
            if(row.enabled()&&row.channel()>0)reduction=Math.max(reduction,Math.clamp(-row.modifier()/100,0,SURVIVAL_MAX_REDUCTION/100));
        return reduction;
    }

    /** Fractional Token prepayments are used by subsequent active ticks. */
    public static final class Billing {
        private static final long MICRO_TOKEN=1000000;
        private static final class Payment {
            long credit;
            double modifier;
        }
        private final java.util.Map<java.util.UUID,Payment> payments=new java.util.HashMap<>();
        private long lastTick=Long.MIN_VALUE;

        public void clear(){payments.clear();lastTick=Long.MIN_VALUE;}

        public void tick(HeartBlockEntity heart){
            if(!heart.isSurvivalCore()||!(heart.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;
            if(!heart.operational()||!heart.wireless().master()){clear();return;}
            long now=level.getGameTime();
            if(lastTick==now)return;
            lastTick=now;
            payments.keySet().removeIf(id->heart.modules().find(id)==null);
            for(var module:heart.modules().all()){
                if(!module.type().equals("armor"))continue;
                var payment=payments.computeIfAbsent(module.id(),id->new Payment());
                payment.modifier=0;
                if(!HeartModules.triggered(module,heart))continue;
                double modifier=activeModifier(heart,module);
                if(modifier>=0)continue;
                double reduction=Math.clamp(-modifier,0,SURVIVAL_MAX_REDUCTION);
                long cost=Math.max(1,Math.round(reduction*dev.sablelife.SableLifeServerConfig.armorTokenRate()*MICRO_TOKEN/20));
                long shortage=Math.max(0,cost-payment.credit);
                long tokens=(shortage+MICRO_TOKEN-1)/MICRO_TOKEN;
                if(!heart.spendAttackTokens(tokens))continue;
                payment.credit+=tokens*MICRO_TOKEN-cost;
                payment.modifier=modifier;
            }
        }

        double fundedModifier(HeartBlockEntity heart,VirtualModule module){
            tick(heart);
            var payment=payments.get(module.id());
            return payment==null?0:payment.modifier;
        }
    }

    private static boolean signal(HeartBlockEntity heart,int source,int when,int channel){
        if(channel<=0||channel>heart.wireless().size())return false;
        int power=source==0?heart.wireless().output(channel-1):heart.wireless().input(channel-1);
        return when==0?power>0:power==0;
    }
    private ModuleArmor() {}
}
