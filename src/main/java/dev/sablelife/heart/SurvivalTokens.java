package dev.sablelife.heart;

import dev.sablelife.SableLifeServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SurvivalTokens {
    public static final java.util.List<String> CAST_TYPES=java.util.List.of("flame","gun","mortar","homing","laser","sphere_blast",
            "slash","ground_strike","quake","pull","freeze","grab","pulse","surface_field","summoned_weapon");

    public static boolean chargeCast(HeartBlockEntity heart,dev.sablelife.module.VirtualModule module,int units){
        return !heart.isSurvivalCore()||heart.spendAttackTokens(castCost(module,units,SableLifeServerConfig.castTokenRate(module.type())));
    }

    public static boolean chargeFieldExtension(HeartBlockEntity heart,dev.sablelife.module.VirtualModule module,long ticks){
        if(!heart.isSurvivalCore()||ticks<=0)return true;
        int rate=SableLifeServerConfig.castTokenRate("surface_field");
        return heart.spendAttackTokens(roundedCost(fieldCost(module,ticks),1,rate));
    }

    private static double fieldCost(dev.sablelife.module.VirtualModule m,double ticks){
        return fieldArea(m,"fieldRadius")*seconds(ticks)*(m.value("damage")+enabledBurn(m,"fieldBurn"));
    }

    private static double area(double radius){return Math.PI*radius*radius;}
    private static double seconds(double ticks){return ticks/20;}

    private static long castCost(dev.sablelife.module.VirtualModule m,int units,int rate){
        double damage=m.value("damage");
        double blast=impactBlastCost(m);
        double cost=switch(m.type()){
            case "gun" -> m.value("arrows")*(m.value("speed")*damage*(m.value("range")/64)+blast);
            case "mortar" -> m.value("speed")*damage+blast;
            case "homing" -> m.value("homingCount")*((1+m.value("speed"))*damage*(m.value("homingLife")/200)+blast);
            case "flame" -> flameArea(m)*seconds(m.value("range")/.8)*(m.value("flameSpeed")/.8)*(damage+m.value("burn"));
            case "laser" -> m.value("beamLength")*m.value("beamWidth")*seconds(m.value("beamHits")*10)*(10/m.value("hitInterval"))*damage;
            case "sphere_blast" -> m.value("blastRadius")*damage*(m.value("sphereChargeSpeed")/2);
            case "pulse" -> m.value("radius")*(damage+m.value("knockback"));
            case "slash" -> m.value("slashCount")*m.value("slashLength")*m.value("slashWidth")*m.value("range")/16*(m.value("slashSpeed")/16)*(damage+enabledBurn(m,"slashIgnite"));
            case "ground_strike" -> m.value("strikeRadius")*damage*m.value("strikeSpeed");
            case "quake" -> (m.value("quakeMode")==0?m.value("quakeRange")*m.value("quakeWidth"):area(m.value("quakeRange")))
                    *(m.value("quakeMode")==2?1:seconds(m.value("quakeRange")/.6)*(m.value("quakeSpeed")/.6))
                    *m.value("quakeWaves")*Math.max(damage,m.value("quakeLaunch")+m.value("quakePush"));
            case "pull" -> (m.value("pullShape")==0?area(m.value("pullRadius")):
                    m.value("pullLength")*(m.value("pullStartRadius")+m.value("pullEndRadius")))
                    *seconds(m.value("pullDuration"))*m.value("pullStrength");
            case "freeze" -> fieldReach(m,"freezeRadius")*m.value("freezeHeight")*seconds(m.value("freezeDuration"));
            case "grab" -> area(m.value("grabRadius"))*seconds(m.value("grabDuration"));
            case "surface_field" -> fieldCost(m,m.value("fieldDuration"))
                    +(m.value("fieldBlast")==1?fieldReach(m,"fieldRadius")*m.value("blastDamage"):0);
            case "summoned_weapon" -> m.value("summonCount")*seconds(m.value("summonDuration"));
            default -> throw new IllegalArgumentException("Missing Token formula for "+m.type());
        };
        return roundedCost(cost,units,rate);
    }

    private static long roundedCost(double cost,int units,int rate){
        return units<=0?0:(long)Math.ceil(Math.max(1,cost)*units*rate);
    }

    private static double flameArea(dev.sablelife.module.VirtualModule m){
        double range=m.value("range"),width=m.value("width");
        return m.value("flameShape")==1?area(range)*m.value("flameFanAngle")/360*width:range*width*2;
    }

    private static double enabledBurn(dev.sablelife.module.VirtualModule m,String key){
        return m.value(key)==1?m.value("burn"):0;
    }

    private static double fieldReach(dev.sablelife.module.VirtualModule m,String radius){
        return m.value("fieldShape")==0?m.value(radius):(m.value("fieldLength")+m.value("fieldWidth"))/4;
    }

    private static double fieldArea(dev.sablelife.module.VirtualModule m,String radius){
        return m.value("fieldShape")==0?area(m.value(radius)):m.value("fieldLength")*m.value("fieldWidth");
    }
    public static void writeEstimate(HeartBlockEntity heart,dev.sablelife.module.VirtualModule module,net.minecraft.nbt.CompoundTag row){
        if(module.type().equals("armor")){
            int rate=SableLifeServerConfig.armorTokenRate();
            row.putString("TokenFormula","armor");
            row.putInt("TokenRate",rate);
            double perSecond=dev.sablelife.module.ModuleArmor.estimatedReduction(module)*100*rate;
            row.putDouble("TokenEstimatePerSecond",perSecond);
            row.putLong("TokenEstimate",(long)Math.ceil(perSecond));
            return;
        }
        if(module.type().equals("arrow")){
            int rate=SableLifeServerConfig.arrowTokenRate();
            row.putString("TokenFormula","arrow");
            row.putInt("TokenRate",rate);
            row.putLong("TokenEstimate",arrowCost(module,(int)module.value("arrows"),rate));
            return;
        }
        if(module.type().equals("melee")){
            int rate=SableLifeServerConfig.meleeTokenRate();
            row.putString("TokenFormula","melee");
            row.putInt("TokenRate",rate);
            row.putLong("TokenEstimate",meleeCost(module,rate));
            return;
        }
        if(CAST_TYPES.contains(module.type())){
            int rate=SableLifeServerConfig.castTokenRate(module.type());
            row.putString("TokenFormula",module.type());
            row.putInt("TokenRate",rate);
            int units=module.type().equals("ground_strike")?dev.sablelife.module.ModuleGroundStrike.plannedPointCount(module):1;
            row.putLong("TokenEstimate",castCost(module,units,rate));
            return;
        }
        if(module.type().equals("health_adjustment")&&module.value("healthAmount")>0){
            var health=heart.health();
            int rate=SableLifeServerConfig.healingTokenRate();
            double amount=module.value("healthAmount")*(module.value("healthUnits")==1?health.maximum()/100.:1);
            float target=Math.min(health.maximum(),health.current()+(float)amount);
            row.putLong("TokenEstimate",healthCost(health.current(),target,rate));
            row.putInt("TokenRate",rate);
            row.putString("TokenFormula","healing");
        }else row.putString("TokenFormula","free");
    }
    static long depositValue(ItemStack stack){
        return stack.isEmpty()?0:(long)stack.getCount()*catalog().getOrDefault(stack.getItem(),0);
    }
    static long inputValue(net.minecraft.world.Container inputs){
        var values=catalog();
        long total=0;
        for(int i=0;i<inputs.getContainerSize();i++){
            var stack=inputs.getItem(i);
            if(stack.isEmpty())continue;
            int price=values.getOrDefault(stack.getItem(),0);
            if(price==0)return 0;
            total+=(long)price*stack.getCount();
        }
        return total;
    }

    static java.util.Map<net.minecraft.world.item.Item,Integer> catalog(){
        var values=new java.util.LinkedHashMap<net.minecraft.world.item.Item,Integer>();
        for(var entry:SableLifeServerConfig.tokenValues()){
            int split=entry.indexOf('=');
            var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.substring(0,split)));
            if(item!=Items.AIR)values.put(item,Integer.parseInt(entry.substring(split+1)));
        }
        return values;
    }

    public static long arrowCost(dev.sablelife.module.VirtualModule module,int count,int rate){
        return (long)Math.ceil(count*(module.value("speed")*module.value("damage")+impactBlastCost(module))*rate);
    }

    private static double impactBlastCost(dev.sablelife.module.VirtualModule module){
        return module.value("explode")==1?module.value("blastRadius")*module.value("blastDamage"):0;
    }

    public static long meleeCost(dev.sablelife.module.VirtualModule module,int rate){
        return (long)Math.ceil(module.value("damage")*rate);
    }

    static long healthCost(float previous,float next,int rate){
        return (long)Math.ceil(((double)next-previous)*rate);
    }

    static float affordableHealth(float current,float target,long balance,int rate){
        if(healthCost(current,target,rate)<=balance)return target;
        float affordable=(float)(current+(double)balance/rate);
        return healthCost(current,affordable,rate)>balance?Math.nextDown(affordable):affordable;
    }

    private SurvivalTokens(){}
}
