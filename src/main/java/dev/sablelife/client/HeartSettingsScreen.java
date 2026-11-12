package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartSettingsMenu;
import dev.sablelife.heart.HitPresentation;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@EventBusSubscriber(modid=SableLife.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class HeartSettingsScreen extends AbstractContainerScreen<HeartSettingsMenu> {
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.register(SableLife.SETTINGS_MENU.get(),HeartSettingsScreen::new); }
    private int page;
    private boolean pageInitialized;
    private String[] keys={};
    private final List<EditBox> inputs=new ArrayList<>();
    private final List<Button> applies=new ArrayList<>();
    private final Map<String,Button> toggles=new LinkedHashMap<>(), sounds=new LinkedHashMap<>(), soundNones=new LinkedHashMap<>();
    private final java.text.DecimalFormat lifeFormat=new java.text.DecimalFormat("0.##");
    private Button particle,pattern,preview,restore,experienceMode,release,bossColor,targetPriority,hitParticles;
    public HeartSettingsScreen(HeartSettingsMenu menu,Inventory inventory,Component title) { super(menu,inventory,title);imageWidth=330;imageHeight=246; }
    private Component tr(String key) { return Component.translatable("core.sablelife."+key); }
    private String prefix() { return page==3?"vanish":"death"; }
    private Button button(Component text,int x,int y,int width,Runnable action) { return addRenderableWidget(LifeUiStyle.button(text,leftPos+x,topPos+y,width,16,action)); }
    private void navigation(int id) { if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id); }
    private void edit(String key,double value,String text) { PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,0,new UUID(0,0),"core",key,value,text)); }
    private boolean survival(){return menu.snapshot().getBoolean("SurvivalCore");}
    private boolean hitEffectPage(){return page==HeartSettingsMenu.HIT_EFFECT_PAGE;}
    private int lastPage(){return survival()?2:6;}
    private int settingsPage(int next){return next==HeartSettingsMenu.HIT_EFFECT_PAGE?next:Math.clamp(next,0,lastPage());}
    private void showPage(int next){page=settingsPage(next);rebuildWidgets();edit("viewPage",page,"");}
    @Override protected void init() {
        imageHeight=page>=4||page==1?Math.min(246,height-12):246;
        super.init();inputs.clear();applies.clear();toggles.clear();sounds.clear();soundNones.clear();particle=pattern=preview=restore=experienceMode=bossColor=targetPriority=hitParticles=null;
        keys=hitEffectPage()?new String[]{"hitParticleCount","hitParticleSpread"}:page==6?new String[]{"limbCleanupDelay"}:page==5?new String[]{"crushDelay"}:page==4?new String[]{"defenseThreshold","defenseMaximum","defenseWindow","knockbackResistance"}:page==0?new String[]{"name","health","delay","xp","regen","regenPercent","regenDelay"}:page==1?new String[]{"voidDeathHeight","ownerName"}:page==2?new String[]{"deathCount","deathSpread","deathEffectRadius"}:page==3?new String[]{"vanishCount","vanishSpread"}:new String[]{};
        if(survival()&&page==0)keys=new String[]{"name","health","regen","regenPercent","regenDelay","ownerName"};
        if(survival()&&page==1)keys=new String[]{};
        button(Component.translatable("manual.sablelife.open"),10,9,75,()->minecraft.setScreen(new ManualScreen(this,survival()?"survival_heart_core":"core")));button(Component.translatable("relations.sablelife.title"),90,9,80,()->minecraft.setScreen(new CoreRelationsScreen(this,menu)));
        release=button(Component.translatable("dashboard.sablelife.release").withStyle(net.minecraft.ChatFormatting.RED),175,9,85,()->edit("release",0,""));release.setTooltip(Tooltip.create(Component.translatable("dashboard.sablelife.release_help")));
        button(Component.translatable("dashboard.sablelife.back"),265,9,55,()->{if(hitEffectPage())showPage(0);else navigation(0);});
        for(int i=0;i<keys.length;i++) {
            final int n=i;int y=fieldY(i);var input=new EditBox(font,leftPos+150,topPos+y,textKey(keys[i])?135:85,16,tr(keys[i]));
            if(keys[i].equals("name"))input.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("name_help")));
            input.setMaxLength(keys[i].equals("ownerName")?16:keys[i].equals("name")?64:10);if(!textKey(keys[i]))input.setFilter(s->s.matches(keys[n].equals("voidDeathHeight")?"-?[0-9]{0,4}":keys[n].equals("hitParticleCount")?"[0-9]{0,2}":"[0-9]{0,7}(\\.[0-9]{0,2})?"));
            if(keys[i].equals("ownerName"))input.setTooltip(Tooltip.create(tr("owner_help")));else if(page==6)input.setTooltip(Tooltip.create(tr("limbCleanup_help")));else if(page==5)input.setTooltip(Tooltip.create(tr("crush_help")));else if(page==1)input.setTooltip(Tooltip.create(tr("void_death_help")));else if(page==4||hitEffectPage())input.setTooltip(Tooltip.create(tr(keys[i]+"_help")));else if(page>=2)input.setTooltip(Tooltip.create(tr(keys[i].equals("deathEffectRadius")?"deathEffectRadius_help":page==3?"vanish_help":"death_help")));else if(i>=4)input.setTooltip(Tooltip.create(tr("regen_help")));
            inputs.add(addRenderableWidget(input));applies.add(button(Component.literal("✓"),292,y,28,()->apply(n)));
            if(page==1&&keys[i].equals("voidDeathHeight")){LifeUiStyle.reverse(applies.getLast(),()->{input.setFocused(false);edit("voidDeathDefault",0,"");});applies.getLast().setTooltip(Tooltip.create(tr("void_death_help")));}
        }
        if(page==0&&!survival()){var heal=button(Component.empty(),150,177,170,()->edit("killHeal",menu.snapshot().getBoolean("killHeal")?0:1,""));LifeUiStyle.reverse(heal,heal::onPress);heal.setTooltip(Tooltip.create(tr("killHeal_help")));toggles.put("killHeal",heal);experienceMode=button(Component.empty(),100,105,44,this::switchExperience);experienceMode.setTooltip(Tooltip.create(tr("xp_units_help")));}
        if(page==1) {
            targetPriority=button(Component.empty(),150,31,170,()->edit("targetPriority",Math.floorMod(menu.snapshot().getInt("targetPriority")+1,4),""));LifeUiStyle.reverse(targetPriority,()->edit("targetPriority",Math.floorMod(menu.snapshot().getInt("targetPriority")-1,4),""));targetPriority.setTooltip(Tooltip.create(tr("target_priority_help")));
            String[] options=survival()?new String[]{"explosion","bossbar"}:new String[]{"breaking","miningDrops","explosion","survivalInteraction","bossbar","lifeAttacks","forceChunkLoading"};
            for(int i=0;i<options.length;i++) { String key=options[i];var b=button(Component.empty(),150,49+i*16+(survival()?i*2:0),170,()->toggleSetting(key,1));b.setHeight(15);LifeUiStyle.reverse(b,()->toggleSetting(key,-1));toggles.put(key,b);
                if(key.equals("bossbar")){
                    b.setWidth(82);bossColor=button(Component.empty(),238,b.getY()-topPos,82,()->cycleBossColor(1));
                    bossColor.setHeight(b.getHeight());LifeUiStyle.reverse(bossColor,()->cycleBossColor(-1));bossColor.setTooltip(Tooltip.create(tr("bossbar_color_help")));
                }
                if(!key.equals("bossbar"))b.setTooltip(Tooltip.create(tr(key.equals("survivalInteraction")?"survivalInteraction_help":key.equals("forceChunkLoading")?"force_chunk_help":key.equals("breaking")?"protection_help":key.equals("explosion")?"explosion_help":key.equals("miningDrops")?"miningDrops_help":"lifeAttacks_help")));
            }
        }
        if(page==1&&!survival())button(tr("limbCleanup_title"),10,197,134,()->showPage(6));
        if(page==6){
            var toggle=button(Component.empty(),150,51,170,()->edit("limbCleanup",menu.snapshot().getBoolean("limbCleanup")?0:1,""));
            toggle.setTooltip(Tooltip.create(tr("limbCleanup_help")));toggles.put("limbCleanup",toggle);
        }
        if(page==5){
            String[] options={"crushEnabled","crushFloor","crushDebris","crushDrops"};int[] ys={51,69,123,141};
            for(int i=0;i<options.length;i++){String key=options[i];var b=button(Component.empty(),150,ys[i],170,()->edit(key,menu.snapshot().getBoolean(key)?0:1,""));LifeUiStyle.reverse(b,b::onPress);b.setTooltip(Tooltip.create(tr("crush_help")));toggles.put(key,b);}
            var protectedBlocks=button(tr("crushProtected_open"),150,105,170,()->minecraft.setScreen(new CollisionProtectionScreen(this,menu)));protectedBlocks.setTooltip(Tooltip.create(tr("crushProtected_help")));
        }
        if(page==4){var enabled=button(Component.empty(),150,51,170,()->edit("defenseEnabled",menu.snapshot().getBoolean("defenseEnabled")?0:1,""));toggles.put("defenseEnabled",enabled);enabled.setTooltip(Tooltip.create(tr("defense_help")));}
        if(page<=1&&!survival())button(tr("defense_open"),150,page==1?197:195,170,()->showPage(4));
        if(page==0)button(tr("hit_particles_title"),10,survival()?169:195,134,()->showPage(HeartSettingsMenu.HIT_EFFECT_PAGE));
        if(hitEffectPage()){
            hitParticles=button(Component.empty(),150,51,170,()->navigation(6));
            hitParticles.setTooltip(Tooltip.create(tr("hit_particles_help")));
        }
        if(page==2||page==3) {
            pattern=button(Component.empty(),150,141,170,()->cyclePattern(1));LifeUiStyle.reverse(pattern,()->cyclePattern(-1));pattern.setTooltip(Tooltip.create(tr("pattern_help")));
            particle=button(Component.empty(),150,159,170,()->navigation(page==3?5:4));particle.setTooltip(Tooltip.create(tr(page==3?"vanish_help":"death_help")));
            soundRow(prefix()+"Sound",177);
            restore=button(tr("restore"),174,31,70,()->{inputs.forEach(b->{b.setFocused(false);b.setTextColor(0xE0E0E0);});edit(prefix()+"Restore",0,"");});restore.setTooltip(Tooltip.create(tr("restore_help")));
            preview=button(tr("preview"),250,31,70,this::previewDeath);preview.setTooltip(Tooltip.create(tr(page==3?"vanish_help":"death_help")));
        }
        int footer=page>=4||page==1?imageHeight-22:224;
        if(hitEffectPage()){
            sync();
            return;
        }
        var previous=button(Component.literal("<"),10,footer,28,()->showPage(page-1));previous.active=page>0;
        var next=button(Component.literal(">"),292,footer,28,()->showPage(page+1));next.active=page<lastPage();sync();
    }
    private void toggleSetting(String key,int step){
        var data=menu.snapshot();
        edit(key,key.equals("breaking")?Math.floorMod(data.getInt("MiningMode")+step,3):data.getBoolean(key)?0:1,"");
    }
    private void cycleBossColor(int delta){edit("bossbarColor",Math.floorMod(menu.snapshot().getInt("bossbarColor")+delta,net.minecraft.world.BossEvent.BossBarColor.values().length),"");}
    private void soundRow(String key,int y) {
        var choice=button(Component.empty(),150,y,170,()->edit("soundEditor:"+key,0,""));sounds.put(key,choice);
        choice.setTooltip(Tooltip.create(Component.translatable("sound_timeline.sablelife.open_help")));
    }
    private int fieldY(int index){return (hitEffectPage()?69:page>=5?87:page==4?69:page==1?161:51)+index*18;}
    private double maximum(int i) { return switch(keys[i].replace("vanish","death")) { case "hitParticleCount"->HitPresentation.MAX_COUNT;case "hitParticleSpread"->HitPresentation.MAX_SPREAD;case "health","regen","defenseThreshold"->1000000;case "regenPercent"->100;case "defenseMaximum","knockbackResistance"->100;case "crushDelay","defenseWindow"->10;case "xp"->menu.snapshot().getBoolean("xpLevels")?dev.sablelife.heart.ExperienceUnits.MAX_LEVELS:dev.sablelife.heart.ExperienceUnits.MAX_POINTS;case "voidDeathHeight"->2048;case "limbCleanupDelay"->dev.sablelife.ownership.DetachedLimbCleanup.MAX_SECONDS;case "delay"->60;case "regenDelay"->600;case "deathCount","deathRadius","deathEffectRadius"->256;case "deathSpread"->32;case "deathPitch"->2;case "deathVolume"->4;default->10000; }; }
    private double minimum(int i) { return keys[i].equals("deathEffectRadius")?0:keys[i].equals("voidDeathHeight")?-2048:keys[i].equals("defenseThreshold")||keys[i].equals("defenseWindow")?.1:keys[i].equals("health")||keys[i].equals("limbCleanupDelay")||keys[i].endsWith("Radius")?1:keys[i].endsWith("Pitch")?.5:0; }
    private static boolean textKey(String key){return key.equals("name")||key.equals("ownerName");}
    private static boolean integerDisplay(String key){return key.equals("voidDeathHeight")||key.equals("hitParticleCount");}
    private boolean valid(int i) {
        if(keys[i].equals("ownerName"))return inputs.get(i).getValue().strip().matches("[A-Za-z0-9_]{0,16}");
        if(keys[i].equals("name"))return true;
        try { double value=Double.parseDouble(inputs.get(i).getValue());return Double.isFinite(value)&&value>=minimum(i)&&value<=maximum(i)&&(!(keys[i].equals("voidDeathHeight")||keys[i].equals("limbCleanupDelay"))||value==Math.rint(value))&&(!keys[i].equals("xp")||!menu.snapshot().getBoolean("xpLevels")||value==Math.rint(value)); }catch(NumberFormatException ignored){return false;}
    }
    private void apply(int i) { var input=inputs.get(i);if(!valid(i)){input.setTextColor(0xFF7777);return;}edit(keys[i],textKey(keys[i])?0:Double.parseDouble(input.getValue()),textKey(keys[i])?input.getValue():"");input.setTextColor(0xE0E0E0);input.setFocused(false); }
    private void switchExperience(){for(int i=0;i<keys.length;i++)if(keys[i].equals("xp")&&inputs.get(i).isFocused()){if(!valid(i)){inputs.get(i).setTextColor(0xFF7777);return;}apply(i);}edit("xpLevels",menu.snapshot().getBoolean("xpLevels")?0:1,"");}
    private void previewDeath() { for(int i=0;i<inputs.size();i++)if(!valid(i)){inputs.get(i).setTextColor(0xFF7777);return;}for(int i=0;i<inputs.size();i++)apply(i);edit(prefix()+"Preview",0,""); }
    private void cyclePattern(int direction) { edit(prefix()+"Pattern",Math.floorMod((int)menu.snapshot().getDouble(prefix()+"Pattern")+direction,6),""); }
    private void sync() {
        var data=menu.snapshot();boolean ready=data.getBoolean("Ready"),live=ready&&data.getBoolean("LiveControls");
        if(targetPriority!=null){targetPriority.active=live;targetPriority.setMessage(tr("target_priority_"+Math.clamp(data.getInt("targetPriority"),0,3)));}
        if(bossColor!=null){bossColor.active=live;var color=net.minecraft.world.BossEvent.BossBarColor.values()[Math.clamp(data.getInt("bossbarColor"),0,6)];bossColor.setMessage(tr("bossbar_color_"+color.name().toLowerCase(Locale.ROOT)));}
        if(release!=null)release.active=ready&&data.getBoolean("ReleaseAllowed");
        if(preview!=null)preview.active=live;
        if(restore!=null)restore.active=live;
        if(experienceMode!=null){experienceMode.active=ready&&data.getBoolean("Editable");experienceMode.setMessage(tr(data.getBoolean("xpLevels")?"xp_levels":"xp_points"));experienceMode.setTooltip(Tooltip.create(tr("xp_units_help").copy().append("\n").append(Component.translatable(data.getBoolean("xpLevels")?"core.sablelife.xp_actual_levels":"core.sablelife.xp_actual",data.getBoolean("xpLevels")?data.getInt("xp"):data.getInt("xpPoints")))));}
        for(int i=0;i<inputs.size();i++){var input=inputs.get(i);boolean active=ready&&data.getBoolean(textKey(keys[i])||keys[i].equals("voidDeathHeight")||keys[i].startsWith("regen")||page>=2?"LiveControls":"Editable");input.active=active;input.setEditable(active);applies.get(i).active=active;if(!input.isFocused()&&ready)input.setValue(textKey(keys[i])?data.getString(keys[i]):keys[i].equals("xp")||integerDisplay(keys[i])?Integer.toString(data.getInt(keys[i])):String.format(Locale.ROOT,"%.2f",data.getDouble(keys[i])));if(!active)input.setFocused(false);}
        toggles.forEach((key,b)->{b.active=key.equals("forceChunkLoading")?ready&&data.getBoolean("ChunkLoadingEditable"):live;b.setMessage(tr(key.equals("breaking")?"mining_mode_"+data.getInt("MiningMode"):data.getBoolean(key)?"on":"off"));});
        if(particle!=null){particle.active=pattern.active=live;pattern.setMessage(tr("pattern_"+(int)data.getDouble(prefix()+"Pattern")));particle.setMessage(Component.translatable("particles.sablelife.count",data.getInt(page==3?"VanishParticleCount":"DeathParticleCount")));}
        if(hitParticles!=null){hitParticles.active=live;hitParticles.setMessage(Component.translatable("particles.sablelife.count",data.getInt("HitParticleTypes")));}
        sounds.forEach((key,b)->{b.active=ready;b.setMessage(Component.translatable("sound_timeline.sablelife.count",data.getInt(key+"Count")));});
    }
    @Override protected void containerTick(){super.containerTick();if(!pageInitialized&&menu.snapshot().getBoolean("Ready")){pageInitialized=true;page=settingsPage(menu.snapshot().getInt("InitialPage"));rebuildWidgets();}sync();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==257||key==335)for(int i=0;i<inputs.size();i++)if(inputs.get(i).isFocused()){apply(i);return true;}if(key!=256)for(var input:inputs)if(input.active&&input.isFocused()){input.keyPressed(key,scan,modifiers);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(vertical!=0)for(int i=0;i<inputs.size();i++){var input=inputs.get(i);if(textKey(keys[i])||!input.active||!input.isMouseOver(x,y))continue;double value;try{value=Double.parseDouble(input.getValue());}catch(NumberFormatException ignored){value=menu.snapshot().getDouble(keys[i]);}value=Math.clamp(Math.round((value+Math.signum(vertical)*NumericWheelSteps.core(keys[i]))*100)/100.,minimum(i),maximum(i));input.setValue(integerDisplay(keys[i])?Integer.toString((int)value):String.format(Locale.ROOT,"%.2f",value));apply(i);input.setFocused(true);return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xFF19151D);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF9B614E);g.fill(leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xFF242632);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        label(g,hitEffectPage()?"hit_particles_title":page==6?"limbCleanup_title":page==5?"crush_title":page==4?"defense_title":page==2?(survival()?"depleted_stage":"death_stage"):page==3?"vanish_stage":page==1?"target_priority_label":"title",34,page==2||page==3?158:page==1?134:230,0xD9C4ED);
        for(int i=0;i<keys.length;i++){
            int yLabel=fieldY(i)+4;
            label(g,keys[i],yLabel,keys[i].equals("xp")?84:134);
            if(!textKey(keys[i]))renderFieldLimit(g,i,yLabel);
        }
        if(hitEffectPage()){
            label(g,"hit_particles_types",55,134);
            return;
        }
        for(var key:toggles.keySet())label(g,key,toggles.get(key).getY()-topPos+4,134);if(page==2||page==3){label(g,prefix()+"Pattern",145,134);label(g,prefix()+"Particle",163,134);label(g,prefix()+"Sound",181,134);}if(page==1&&!survival()){var data=menu.snapshot();g.drawCenteredString(font,Component.translatable("core.sablelife.chunk_status",tr("chunk_"+(data.getString("ChunkLoadingStatus").isBlank()?"off":data.getString("ChunkLoadingStatus"))),data.getInt("ChunkLoadingCount")),imageWidth/2,imageHeight-32,0xAEB8C7);}
        if(page==4){var data=menu.snapshot();int footer=imageHeight-22;int dpsY=Math.min(149,footer-48),reductionY=Math.min(165,footer-34);
            g.drawString(font,Component.translatable("core.sablelife.defense_dps",String.format(Locale.ROOT,data.getDouble("DefenseDps")>10000000?"%.2e":"%.1f",data.getDouble("DefenseDps"))),10,dpsY,0xD9C4ED,false);
            g.drawString(font,Component.translatable("core.sablelife.defense_reduction",String.format(Locale.ROOT,"%.1f",data.getDouble("DefenseReduction"))),10,reductionY,0xFFFF55,false);
            int noteY=reductionY+14,available=footer-noteY-4;
            if(available>=9){var note=tr("defense_note");float scale=Math.max(.5F,Math.min(1F,available/(float)Math.max(1,font.split(note,305).size()*font.lineHeight)));g.pose().pushPose();g.pose().translate(10,noteY,0);g.pose().scale(scale,scale,1);g.drawWordWrap(font,note,0,0,(int)(305/scale),0xAEB8C7);g.pose().popPose();}
            g.drawString(font,Component.literal(data.getString("State")),274,34,0xAEB8C7,false);
        }
        g.drawString(font,Component.literal((page+1)+" / "+(lastPage()+1)),153,page>=4||page==1?imageHeight-18:228,0xD9C4ED,false);
        if(page==5){label(g,"crushProtected",109,134);if(imageHeight>=220)g.drawWordWrap(font,tr("crush_note"),10,166,305,0xAEB8C7);}
        if(page==6)g.drawWordWrap(font,tr("limbCleanup_help"),10,117,imageWidth-20,0xAEB8C7);
        if(page==0&&survival()){
            var data=menu.snapshot();
            float current=data.getFloat("CurrentLife"),maximum=data.getFloat("health");
            int healthColor=current<=0?0xE89A9A:current<maximum*.25F?0xF1B27D:0x9EDCC6;
            var life=Component.literal(lifeFormat.format(current)).withColor(healthColor);
            var limit=Component.literal(lifeFormat.format(maximum)).withColor(0xD7DBE6);
            g.drawString(font,Component.translatable("core.sablelife.current_life",life,limit),10,201,0xAEB8C7,false);
        }
        if(page!=1&&page<4)g.drawString(font,Component.literal(menu.snapshot().getString("State")),10,page==1?imageHeight-33:213,0xAEB8C7,false);
    }

    private void renderFieldLimit(GuiGraphics g,int index,int y){
        var limit=Component.literal("≤"+(int)maximum(index));
        int rate=menu.snapshot().getInt("ActivationTokenRate");
        if(survival()&&page==0&&index==1&&rate>0){
            var price=Component.translatable("survival_tokens.sablelife.hp_rate",rate);
            float scale=Math.min(.8F,48F/Math.max(1,font.width(price)));
            g.drawString(font,limit,240,y-4,0xAEB8C7,false);
            g.pose().pushPose();
            g.pose().translate(240,y+5,0);
            g.pose().scale(scale,scale,1);
            g.drawString(font,price,0,0,0xFFCA54,false);
            g.pose().popPose();
        }else g.drawString(font,limit,240,y,0xAEB8C7,false);
    }

    private void label(GuiGraphics g,String key,int y,int width){label(g,key,y,width,0xE6D8EA);}
    private void label(GuiGraphics g,String key,int y,int width,int color){var text=tr(key);float scale=Math.min(1,width/(float)Math.max(1,font.width(text)));g.pose().pushPose();g.pose().translate(10,y,0);g.pose().scale(scale,scale,1);g.drawString(font,text,0,0,color,false);g.pose().popPose();}
    @Override public void render(GuiGraphics g,int x,int y,float partial){sync();renderBackground(g,x,y,partial);super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
