package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import java.util.*;

/** Exercise actual dashboard controls at every existing density/GUI-scale combination. */
public final class SurfaceFieldClientCheck {
    private static Object get(Object o,String key)throws Exception{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    public static void run(HeartDashboardScreen panel,int index,Minecraft mc)throws Exception{
        var params=(List<ModuleFields.Field>)get(panel,"parameters");var modules=(List<VirtualModule>)get(panel,"modules");var m=modules.get(8);
        for(var f:params)if(net.minecraft.client.resources.language.I18n.get("dashboard.sablelife."+f.key()).startsWith("dashboard."))throw new AssertionError("Missing field translation "+f.key());
        if(m.type().equals("surface_field")){
            for(String key:List.of("mark","preview","trigger","channel","channelApply","hurtSettings"))if(((net.minecraft.client.gui.components.AbstractWidget)get(panel,key)).visible)throw new AssertionError("Impact template retained standalone control "+key);
            if(params.stream().anyMatch(f->List.of("castTargetRange","targetMultiple","interval").contains(f.key())))throw new AssertionError("Removed standalone release fields remain");
            for(String key:List.of("fieldRadius","fieldLength","fieldWidth","fieldDuration")){var f=ModuleFields.find(m.type(),key);if(NumericWheelSteps.module(m.type(),key,f.scale(),f.min())!=1)throw new AssertionError("Wheel is not one display unit: "+key);}
            if(params.stream().anyMatch(f->f.key().equals("burn"))!=(m.value("fieldBurn")==1))throw new AssertionError("Burn duration visibility wrong");
            if(params.stream().anyMatch(f->f.key().equals(m.value("fieldShape")==0?"fieldLength":"fieldRadius")))throw new AssertionError("Wrong geometry fields visible");
        }else if(params.stream().noneMatch(f->f.key().equals("impactArea"))||params.stream().anyMatch(f->f.key().startsWith("impactFire")))throw new AssertionError("Impact controls missing");
        if(m.type().equals("slash")&&params.stream().anyMatch(f->f.key().equals("burn"))!=(m.value("slashIgnite")==1))throw new AssertionError("Slash burn duration visibility wrong");
        var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);var page=HeartDashboardScreen.class.getDeclaredField("parameterPage");page.setAccessible(true);
        int slots=(int)get(panel,"parameterSlots");int checked=0;
        for(int p=0;p*slots<params.size();p++){
            page.setInt(panel,p);sync.invoke(panel);
            var boxes=(List<EditBox>)get(panel,"fields");var buttons=(List<Button>)get(panel,"choices");
            for(int i=0;i<boxes.size()&&p*slots+i<params.size();i++){
                var f=params.get(p*slots+i);var box=boxes.get(i);var button=buttons.get(i);
                if(f.key().equals("slashBlockable")&&(!button.visible||!button.active||button.getWidth()!=18||!button.getMessage().getString().equals(m.value("slashBlockable")==1?"✓":"")))throw new AssertionError("Slash block option is not a usable checkbox");
                if(f.choice()){if(!button.visible)throw new AssertionError("Choice hidden "+f.key());if(m.type().equals("surface_field")&&f.key().equals("fieldRelease")&&(button.active||f.choices().size()!=1))throw new AssertionError("Impact-only condition can still be switched");if(f.key().equals("impactArea")&&!button.active)throw new AssertionError("Impact selector disabled on "+m.type());if(f.key().equals("impactArea")&&!button.getMessage().getString().contains("#1"))throw new AssertionError("Stable field reference not shown");}
                else{if(!box.visible)throw new AssertionError("Editor missing "+f.key());if(!box.active)continue;box.setFocused(true);box.setValue("1.25");sync.invoke(panel);if(!box.isFocused()||!box.getValue().equals("1.25"))throw new AssertionError("Draft lost");box.setFocused(false);checked++;}
            }
        }
        page.setInt(panel,0);sync.invoke(panel);
        var prior=mc.screen;final int[] picked={-1};var picker=new SurfaceFieldPickerScreen(panel,modules,modules.getFirst().id(),v->picked[0]=v);picker.init(mc,panel.width,panel.height);
        var children=picker.children().stream().filter(w->w instanceof Button).map(w->(Button)w).toList();var option=children.stream().filter(b->b.getMessage().getString().contains("#1")).findFirst().orElseThrow();option.onPress();if(picked[0]!=1)throw new AssertionError("Picker selected wrong module");mc.setScreen(prior);
        System.out.println("Surface field UI layout "+index+" passed; "+checked+" editable fields");
    }
}
