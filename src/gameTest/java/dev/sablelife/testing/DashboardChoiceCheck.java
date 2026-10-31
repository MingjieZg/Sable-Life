package dev.sablelife.testing;
import dev.sablelife.module.ModuleFields;
import java.util.*;
/** Check the exact choice_ keys consumed by the dashboard, including choices not currently selected. */
public final class DashboardChoiceCheck {
    private static boolean checked;
    public static void run(){if(checked)return;checked=true;
        var editors=Set.of("particles","effects","library","signal_rule_editor");
        var keys=new HashSet<String>();
        for(var type:List.of("freeze","grab","signal_logic","pulse"))for(var field:ModuleFields.forType(type))for(var value:field.choices())if(!editors.contains(value)){
            var key="dashboard.sablelife.choice_"+value;
            keys.add(key);
            if(net.minecraft.client.resources.language.I18n.get(key).equals(key))throw new AssertionError("Missing dashboard choice label: "+type+"/"+field.key()+"/"+key);
        }
        for(var lang:List.of("en_us","zh_cn"))try(var reader=net.minecraft.client.Minecraft.getInstance().getResourceManager().getResourceOrThrow(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sablelife","lang/"+lang+".json")).openAsReader()){
            var data=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for(var key:keys)if(!data.has(key)||data.get(key).getAsString().isBlank())throw new AssertionError("Missing "+lang+" choice: "+key);
        }catch(java.io.IOException e){throw new AssertionError(e);}
        System.out.println("All freeze/grab/signal logic/shockwave dashboard option labels resolved");
    }
    private DashboardChoiceCheck(){}
}
