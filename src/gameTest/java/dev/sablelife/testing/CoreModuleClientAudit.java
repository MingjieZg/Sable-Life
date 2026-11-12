package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Actual client render/parameter-page smoke test; no player world or external server is opened. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class CoreModuleClientAudit {
    private record Case(int kind,String type,String key,int value){Case(int kind,String type){this(kind,type,"default",0);}}
    private static final List<Case> cases=new ArrayList<>();
    private static final List<String> failures=new ArrayList<>();
    private static final boolean PART_DAMAGE_ONLY = Boolean.getBoolean("sablelife.partDamageUiOnly");
    private static final boolean TRACER_ONLY = Boolean.getBoolean("sablelife.projectileTracerUiOnly");
    private static int phase,index,ticks,checked,pages;
    private static java.util.concurrent.CompletableFuture<?> reload;
    private static Preview screen;
    private static Object get(String name)throws Exception{var f=HeartDashboardScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen.panel);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.coreModuleAudit"))return;var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null||reload!=null&&!reload.isDone())return;
        if(cases.isEmpty()){for(int kind=0;kind<3;kind++)for(var type:kind==0?VirtualModule.AVAILABLE_TYPES:kind==1?RedstoneCorePolicy.TYPES:SurvivalLifeCorePolicy.TYPES)cases.add(new Case(kind,type));if(Boolean.getBoolean("sablelife.propertyHelpOnly"))cases.removeIf(value->!List.of("linear_movement","sensor","health_bar","laser").contains(value.type));if(Boolean.getBoolean("sablelife.survivalAimOnly"))cases.removeIf(value->value.kind!=2||!List.of("arrow","gun","mortar","slash","sphere_blast","pull","freeze","pulse","quake","ground_strike").contains(value.type));}
        if(phase==0){
            if(mc.level!=null||mc.screen==null)return;
            if(PART_DAMAGE_ONLY){
                cases.removeIf(value->value.kind==1||!List.of("part_break","damage_accumulation").contains(value.type));
                for(int kind : List.of(0,2)){
                    cases.add(new Case(kind,"part_break","partBreakMode",1));
                    cases.add(new Case(kind,"part_break","partBreakShape",1));
                    cases.add(new Case(kind,"damage_accumulation","accumScope",1));
                }
            }
            if (TRACER_ONLY) {
                cases.removeIf(value -> !ProjectileTracer.supports(value.type));
                for (var entry : List.copyOf(cases)) cases.add(new Case(entry.kind, entry.type, "projectileTrailStyle", 1));
            }
            if(Boolean.getBoolean("sablelife.corePatternUi")){
                var baseline=List.copyOf(cases);
                for(var entry:baseline)for(var field:ModuleFields.forType(entry.type))if(field.choice()&&field.choices().size()>1)
                    for(int value=0;value<field.choices().size();value++)cases.add(new Case(entry.kind,entry.type,field.key(),value));
            }
            language(mc,"en_us");phase=1;return;
        }
        if(screen==null){mc.options.guiScale().set(index%2==0?2:3);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);ticks=0;return;}
        if(++ticks<3)return;
        var value=cases.get(index);String label=mc.options.languageCode+"/"+value.kind+"/"+value.type+"/"+value.key+"="+value.value;
        try{check();checked++;System.out.println("CORE_UI_AUDIT PASS "+label);
            if(PART_DAMAGE_ONLY||TRACER_ONLY||(value.key.equals("default")||List.of("flameShape","fieldShape","particlePattern","pullShape","laserVisual").contains(value.key))&&(value.type.equals("flame")||value.type.equals("block_interaction")||Boolean.getBoolean("sablelife.survivalAimOnly")&&List.of("arrow","gun","mortar","slash").contains(value.type)||Boolean.getBoolean("sablelife.propertyHelpPreview")&&List.of("linear_movement","sensor","health_bar","laser").contains(value.type))){
                var target=mc.gameDirectory.toPath().resolve("screenshots/"+mc.options.languageCode+"-"+value.kind+"-"+value.type+"-"+value.key+"-"+value.value+".png");java.nio.file.Files.createDirectories(target.getParent());
                try(var picture=Screenshot.takeScreenshot(mc.getMainRenderTarget())){picture.writeToFile(target);}
            }
        }catch(Throwable failure){failures.add(label+": "+failure);System.out.println("CORE_UI_AUDIT FAIL "+label+": "+failure);}
        screen=null;
        if(++index<cases.size())return;
        index=0;
        if(phase==1){language(mc,"zh_cn");phase=2;return;}
        var data=new com.google.gson.JsonObject();data.addProperty("cases",cases.size()*2);data.addProperty("passed",checked);data.addProperty("parameterPages",pages);
        var errors=new com.google.gson.JsonArray();failures.forEach(errors::add);data.add("failures",errors);
        java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("core-module-ui-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data));
        System.out.println("CORE_UI_AUDIT COMPLETE passed="+checked+" cases="+(cases.size()*2)+" pages="+pages+" failures="+failures.size());mc.stop();
    }
    private static void language(Minecraft mc,String code){mc.getLanguageManager().setSelected(code);mc.options.languageCode=code;reload=mc.reloadResourcePacks();}
    private static void check()throws Exception{
        var page=HeartDashboardScreen.class.getDeclaredField("parameterPage");page.setAccessible(true);
        var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);
        var fields=(List<ModuleFields.Field>)get("parameters");int slots=(int)get("parameterSlots");
        if(slots<=0)throw new AssertionError("No parameter slots");
        if(PART_DAMAGE_ONLY){
            var value=cases.get(index);
            boolean watches=List.of("partBreakMode","accumScope").contains(value.key);
            boolean partBreak=value.type.equals("part_break");
            boolean triggerVisible=((Button)get("trigger")).visible;
            if(triggerVisible==(partBreak&&watches))throw new AssertionError("Unexpected trigger control visibility");
            var keys=fields.stream().map(ModuleFields.Field::key).toList();
            if(keys.contains("watchedPart")!=watches||keys.contains("partBreakThreshold")!=(partBreak&&watches))
                throw new AssertionError("Unexpected watched-part or threshold fields");
            if (value.type.equals("damage_accumulation")) {
                var threshold = fields.stream().filter(field -> field.key().equals("accumThreshold")).findFirst().orElseThrow();
                if (NumericWheelSteps.module(value.type, threshold.key(), threshold.scale(), threshold.min()) != 5)
                    throw new AssertionError("Damage threshold must scroll by 5 health points");
            }
            for(var field:fields)if(List.of("partBreakMode","partBreakShape","watchedPart","partBreakThreshold","accumScope").contains(field.key())){
                for(String prefix : List.of("dashboard.sablelife.","dashboard.sablelife.property_help.")){
                    String key=prefix+field.key();
                    if(Component.translatable(key).getString().equals(key))throw new AssertionError("Untranslated field text: "+key);
                }
            }
        }
        int aimPage=0;
        if (TRACER_ONLY) {
            boolean tracer = cases.get(index).value == 1;
            var keys = fields.stream().map(ModuleFields.Field::key).toList();
            if (!keys.contains("projectileTrailStyle") || keys.contains("particles") == tracer)
                throw new AssertionError("Wrong trail style or particle picker visibility");
            for (String key : List.of("projectileTrailColor", "projectileTrailLength", "projectileTrailWidth")) {
                if (keys.contains(key) != tracer) throw new AssertionError("Wrong visibility for " + key);
                for (String prefix : List.of("dashboard.sablelife.", "dashboard.sablelife.property_help.")) {
                    String translated = prefix + key;
                    if (Component.translatable(translated).getString().equals(translated)) throw new AssertionError("Missing " + translated);
                }
            }
            aimPage = keys.indexOf("projectileTrailStyle") / slots;
        }
        if(Boolean.getBoolean("sablelife.survivalAimOnly")){
            String type=cases.get(index).type;boolean aimed=List.of("arrow","gun","mortar","slash").contains(type);
            String key=type.equals("mortar")?"aimMode":"shotAim";
            var keys=fields.stream().map(ModuleFields.Field::key).toList();
            if(aimed){
                if(!keys.contains(key)||!keys.contains("targetMultiple")||!keys.contains("targetRange"))throw new AssertionError("Aiming controls missing: "+type);
                aimPage=keys.indexOf(key)/slots;
            }else if(keys.contains("releasePosition")||keys.contains("quakeOrigin")||keys.contains("targetMultiple"))throw new AssertionError("Direct target placement controls exposed: "+type);
        }
        for(int n=0;n<Math.max(1,(fields.size()+slots-1)/slots);n++){
            page.setInt(screen.panel,n);sync.invoke(screen.panel);pages++;
            for(var child:screen.panel.children())if(child instanceof AbstractWidget widget&&widget.visible){
                if(widget.getX()<0||widget.getY()<0||widget.getX()+widget.getWidth()>screen.width||widget.getY()+widget.getHeight()>screen.height)throw new AssertionError("Widget outside viewport: "+widget.getMessage().getString());
                if(widget.getMessage().getString().contains("dashboard.sablelife.")||widget.getMessage().getString().contains("manual.sablelife."))throw new AssertionError("Unresolved button label: "+widget.getMessage().getString());
            }
        }
        page.setInt(screen.panel,aimPage);sync.invoke(screen.panel);
    }
    private static final class Preview extends Screen{
        HeartDashboardScreen panel;String type;
        private Object own(String name)throws Exception{var f=HeartDashboardScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(panel);}
        Preview(){super(Component.empty());}
        @Override protected void init(){
            var value=cases.get(index);type=value.type;var modules=new HeartModules();var module=modules.add(value.type);if(Boolean.getBoolean("sablelife.propertyHelpOnly")&&type.equals("linear_movement"))module.edit("linearMode",1,"");
            if(!value.key.equals("default"))module.edit(value.key,value.value,"");
            if(value.kind==1){RedstoneCorePolicy.constrainMovement(module);if(type.equals("move_set"))module.edit("ignoreConditions",1,"");}
            if(value.kind==2)SurvivalLifeCorePolicy.constrain(module);
            if(Boolean.getBoolean("sablelife.survivalAimOnly")&&List.of("arrow","gun","mortar","slash").contains(type)){module.edit(type.equals("mortar")?"aimMode":"shotAim",1,"");module.edit("targetMultiple",1,"");}
            var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",module.id());
            tag.putBoolean("ControlCore",value.kind==1);tag.putBoolean("SurvivalCore",value.kind==2);tag.putBoolean("ControlReady",true);tag.putString("ControlStatus","ready");
            tag.putLong("SurvivalTokens",1234);var row=tag.getList("Modules",10).getCompound(0);row.putString("Status","unmarked");row.putString("MovementPhase","idle");row.putString("TokenFormula","free");
            var inventory=new Inventory(null);var menu=new HeartDashboardMenu(1,inventory);var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(paper);
            panel=new HeartDashboardScreen(menu,inventory,title);panel.init(minecraft,width,height);
        }
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){
            if(Boolean.getBoolean("sablelife.propertyHelpPreview"))try{
                String key=switch(type){case "linear_movement"->"travelSpeed";case "sensor"->"radius";case "health_bar"->"healthBarNameSize";case "laser"->"beamLength";default->"";};
                var fields=(List<ModuleFields.Field>)own("parameters");int at=-1;for(int i=0;i<fields.size();i++)if(fields.get(i).key().equals(key))at=i;
                if(at>=0){int slots=(int)own("parameterSlots");var page=HeartDashboardScreen.class.getDeclaredField("parameterPage");page.setAccessible(true);page.setInt(panel,at/slots);var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);sync.invoke(panel);var box=((List<EditBox>)own("fields")).get(at%slots);panel.render(graphics,-100,-100,partial);var help=Class.forName("dev.sablelife.client.ModulePropertyHelp").getDeclaredMethod("text",VirtualModule.class,ModuleFields.Field.class);help.setAccessible(true);graphics.renderTooltip(minecraft.font,Tooltip.splitTooltip(minecraft,(Component)help.invoke(null,new VirtualModule(type),fields.get(at))),box.getX()+3,box.getY()+3);return;}
            }catch(Exception failure){throw new IllegalStateException(failure);}
            panel.render(graphics,-100,-100,partial);
        }
    }
}
