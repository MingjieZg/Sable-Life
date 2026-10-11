package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@EventBusSubscriber(modid = SableLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HeartDashboardScreen extends AbstractContainerScreen<HeartDashboardMenu> {
    public static boolean marking;
    private static final UUID NONE = new UUID(0, 0);
    private UUID selected = NONE, last = NONE;
    private int page, parameterPage, deleteUntil;
    private Set<UUID> beforeAdd;
    private String addingType="";
    private int addExpires;
    private Button savePreset,moveModuleUp,moveModuleDown;
    private int reorderDestination=-1,reorderExpires;
    private int parameterSlots, rowPitch, detailX, inputX, applyX, detailWidth;
    private int sidebarSlots = 8;
    private int sidebarPitch;
    private float textScale() { return LifeUiStyle.scale(); }
    private int pagerY() { return imageHeight - 99; }
    private int triggerY() { return imageHeight - 73; }
    private int markY() { return imageHeight - 51; }
    private int bottomY() { return imageHeight - 20; }
    private void changeDensity() {
        changeDensity(1);
    }
    private void changeDensity(int direction) {
        LifeUiStyle.cycle(direction);
        // Changing layout must not silently discard typed edits.
        for (int i = 0; i < fields.size(); i++) if (fields.get(i).isFocused() && fields.get(i).active) apply(i);
        if (name.isFocused()) { action("edit", "name", 0, name.getValue()); name.setFocused(false); }
        if (channel.isFocused()) applyChannel();
        parameterPage = 0; rebuildWidgets();
    }
    private boolean selectionRestored;
    private Button samples, hurtSettings;
    private final List<Button> rgbButtons=new ArrayList<>();
    private String pendingRgb="";private double pendingRgbMode;
    private Button releaseLife;
    private Button settingsNav,lootNav;
    private final List<Button> rows = new ArrayList<>(), edits = new ArrayList<>();
    private final List<EditBox> fields = new ArrayList<>();
    private final List<Button> applyButtons = new ArrayList<>(), choices = new ArrayList<>(), soundFolders = new ArrayList<>(), vanillaFolders = new ArrayList<>(), noneButtons = new ArrayList<>();
    private Button add, toggle, trigger, remove, mark, preview, channelApply, previousParameters, nextParameters;
    private EditBox name, channel;
    private CompoundTag snapshot = new CompoundTag();
    private net.minecraft.world.item.component.CustomData decodedData;
    private final List<VirtualModule> modules = new ArrayList<>();
    private final List<String> statuses = new ArrayList<>();
    private List<ModuleFields.Field> parameters = List.of();
    public HeartDashboardScreen(HeartDashboardMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=360;imageHeight=234;}
    private Component tr(String key) { return Component.translatable("dashboard.sablelife." + key); }
    private Button button(Component text, int x, int y, int width, Runnable action) {
        return addRenderableWidget(LifeUiStyle.button(text, leftPos + x, topPos + y, width, 18, action));
    }
    private void action(String operation, String key, double value, String text) {
        PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId, snapshot.getInt("Revision"), selected, operation, key, value, text));
    }
    boolean canAddModule(){return menu.snapshot().getBoolean("Editable")&&menu.snapshot().getList("Modules",10).size()<HeartModules.LIMIT;}
    void addType(String type){sync();trackAdd(type);action("add",type,0,"");}
    void addPreset(CompoundTag tag){sync();trackAdd(tag.getString("Type"));action("add_preset",tag.getString("Type"),0,ModulePreset.portable(tag).toString());}
    private void reorderModule(int direction){sync();if(selected()!=null){reorderDestination=modules.indexOf(selected())+direction;reorderExpires=tickCount+100;action("reorder","",direction,"");}}
    private void trackAdd(String type){beforeAdd=new HashSet<>(modules.stream().map(VirtualModule::id).toList());addingType=type;addExpires=tickCount+100;}
    boolean savePreset(String name){sync();var module=selected();if(module==null)return false;try{ModulePresetLibrary.save(name,module.save());return true;}catch(java.io.IOException e){return false;}}
    private List<String> availableTypes(){return menu.snapshot().getBoolean("ControlCore")?dev.sablelife.heart.RedstoneCorePolicy.TYPES:VirtualModule.AVAILABLE_TYPES;}
    private List<VirtualModule> sidebarModules() { return modules; }

    private VirtualModule selected() { return modules.stream().filter(m -> m.id().equals(selected)).findFirst().orElse(null); }
    private int frequencySlotsX() { return inputX-2*(Math.min(16,rowPitch-2)+5)-8; }
    private int frequencyButtonX() { return inputX; }
    private int frequencyButtonWidth() { return Math.max(45,Math.min(65,Math.round(detailWidth*.2f))); }
    @Override protected void init() {
        int oldFirst=parameterPage*parameterSlots;
        String topField=oldFirst<parameters.size()?parameters.get(oldFirst).key():null;
        imageWidth = Math.max(300, Math.min(480, width - 14));
        imageHeight = Math.max(200, height - 14);
        int propertySpace = imageHeight - 99 - 63;
        rowPitch = Math.max(12,(int)Math.ceil((font.lineHeight+8)*textScale()));
        parameterSlots = Math.max(1, propertySpace / rowPitch);
        sidebarPitch = Math.max(14,(int)Math.ceil(font.lineHeight*textScale())+8);
        sidebarSlots = Math.max(1,(imageHeight - 44 - 47)/sidebarPitch);
        if(topField!=null)for(int i=0;i<parameters.size();i++)if(parameters.get(i).key().equals(topField)){parameterPage=i/parameterSlots;break;}
        for(int i=0;i<modules.size();i++)if(modules.get(i).id().equals(selected)){page=i/sidebarSlots;break;}
        detailX = Math.min(143,Math.max(103,Math.round(imageWidth*.30f))); detailWidth = imageWidth - detailX - 11;
        inputX = detailX + Math.round(detailWidth * .46f); applyX = imageWidth - 49;
        super.init(); rows.clear(); edits.clear(); fields.clear(); applyButtons.clear(); choices.clear(); soundFolders.clear(); vanillaFolders.clear(); noneButtons.clear();rgbButtons.clear();
        int nav = (imageWidth - 180) / 3;
        settingsNav=button(tr("settings"), 7, 8, nav, () -> { if(menu.snapshot().getBoolean("ControlCore"))minecraft.setScreen(new ManualScreen(this,"survival_core"));else action("settings", "", 0, ""); });
        button(tr("wireless"), 11 + nav, 8, nav, () -> action("wireless", "", 0, ""));
        lootNav=button(tr("loot"), 15 + nav * 2, 8, nav, () -> action("loot", "", 0, ""));lootNav.visible=!menu.snapshot().getBoolean("ControlCore");
        button(Component.translatable("language_picker.sablelife.button"),imageWidth-159,8,45,()->minecraft.setScreen(new LanguagePickerScreen(this))).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("language_picker.sablelife.help")));
        var densityButton = button(tr("density_" + LifeUiStyle.density()), imageWidth - 110, 8, 45, this::changeDensity);
        LifeUiStyle.reverse(densityButton, () -> changeDensity(-1));
        densityButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("density_help")));
        releaseLife = button(tr("release").copy().withStyle(net.minecraft.ChatFormatting.RED), imageWidth - 61, 8, 34, () -> action("release", "", 0, ""));
        releaseLife.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("release_help")));
        button(Component.literal("\u00d7"), imageWidth - 23, 8, 16, () -> onClose()).setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("close")));
        for (int i = 0; i < sidebarSlots; i++) {
            int index = i;
            rows.add(button(Component.empty(), 7, 47 + i * sidebarPitch, detailX-23, () -> {
                if (page * sidebarSlots + index < sidebarModules().size()) { selected = sidebarModules().get(page * sidebarSlots + index).id(); action("select", "", 0, ""); }
            }));
            rows.getLast().setHeight(sidebarPitch - 2);
        }
        button(Component.literal("‹"), 7, imageHeight - 44, 27, () -> page = Math.max(0, page - 1));
        button(Component.literal("›"), detailX-43, imageHeight - 44, 27, () -> page = Math.min(Math.max(0, (sidebarModules().size() - 1) / sidebarSlots), page + 1));
        add=button(tr("add"),7,bottomY(),detailX-67,()->minecraft.setScreen(new ModulePickerScreen(this,availableTypes())));
        moveModuleUp=button(Component.literal("↑"),detailX-57,bottomY(),19,()->reorderModule(-1));
        moveModuleDown=button(Component.literal("↓"),detailX-35,bottomY(),19,()->reorderModule(1));
        moveModuleUp.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("module_picker.sablelife.move_up")));
        moveModuleDown.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("module_picker.sablelife.move_down")));
        name = new EditBox(font, leftPos + detailX, topPos + 34, detailWidth - 65, 16, tr("name")); name.setMaxLength(40); addRenderableWidget(name);
        button(Component.literal("?"), applyX - 22, 33, 19, () -> {
            var module = selected();
            minecraft.setScreen(new ManualScreen(this, module == null ? "getting_started" : module.type()));
        }).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("manual.sablelife.help")));
        edits.add(button(Component.literal("✓"), applyX, 33, 38, () -> { action("edit", "name", 0, name.getValue()); name.setFocused(false); }));
        for (int i = 0; i < parameterSlots; i++) {
            int index = i, y = 63 + i * rowPitch;
            var box = new EditBox(font, leftPos + inputX, topPos + y, applyX - inputX - 32, rowPitch - 2, Component.empty());
            box.setMaxLength(8); box.setFilter(s -> s.matches("-?[0-9]{0,5}(\\.[0-9]{0,2})?")); fields.add(box); addRenderableWidget(box);
            var apply = button(Component.literal("✓"), applyX, y - 1, 38, () -> apply(index)); apply.setHeight(rowPitch - 1); applyButtons.add(apply); edits.add(apply);
            var choice = button(Component.empty(), inputX, y - 1, imageWidth - inputX - 11, () -> cycle(index, 1)); choice.setHeight(rowPitch - 1); choices.add(choice); edits.add(choice);
            LifeUiStyle.reverse(choice, () -> cycle(index, -1));
            var rgb=button(Component.literal("RGB"),inputX,y-1,36,()->toggleRgb(index));rgb.setHeight(rowPitch-1);rgb.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("rgb_help")));rgbButtons.add(rgb);
            var none = button(Component.empty(), inputX, y - 1, 18, () -> toggleNone(index));
            none.setHeight(rowPitch - 1); none.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("none_toggle"))); noneButtons.add(none);
            var vanilla = button(tr("vanilla_folder"), applyX - 2, y - 1, 19, () -> openVanillaSound(index));
            vanilla.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("vanilla_folder_help"))); vanilla.setHeight(rowPitch - 1); vanillaFolders.add(vanilla);
            var folder = button(tr("custom_folder"), applyX + 19, y - 1, 19, () -> net.minecraft.Util.getPlatform().openPath(dev.sablelife.sound.SoundLibrary.folder()));
            folder.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("sound_folder_help")));
            folder.setHeight(rowPitch - 1); soundFolders.add(folder);
        }
        previousParameters = button(Component.literal("‹"), detailX, pagerY(), 27, () -> changeParameterPage(-1));
        nextParameters = button(Component.literal("›"), imageWidth - 38, pagerY(), 27, () -> changeParameterPage(1));
        trigger = button(Component.empty(), detailX, triggerY(), detailWidth - 89, () -> {
            var m = selected(); if (m != null) action("edit", "trigger", menu.snapshot().getBoolean("ControlCore")?nextControlTrigger(m,1):nextTrigger(m, 1), "");
        }); LifeUiStyle.reverse(trigger, () -> { var m = selected(); if (m != null) action("edit", "trigger", menu.snapshot().getBoolean("ControlCore")?nextControlTrigger(m,-1):nextTrigger(m, -1), ""); }); edits.add(trigger);
        hurtSettings = button(tr("hurt_settings"), imageWidth - 94, triggerY(), 83, () -> { var m=selected(); if(m!=null) minecraft.setScreen(new HurtSettingsScreen(menu,minecraft.player.getInventory(),title,m.id())); });
        samples = button(tr("samples"), detailX, triggerY(), detailWidth, () -> action("samples", "", 0, ""));
        channel = new EditBox(font, leftPos + imageWidth - 94, topPos + triggerY() + 1, 47, 16, tr("channel"));
        channel.setMaxLength(5); channel.setFilter(s -> s.matches("[0-9]{0,5}")); addRenderableWidget(channel);
        channelApply = button(Component.literal("✓"), imageWidth - 42, triggerY(), 31, this::applyChannel); edits.add(channelApply);
        mark = button(tr("mark"), detailX, markY(), (detailWidth - 6) / 2, () -> action("mark", "", 0, "")); edits.add(mark);
        preview = button(tr("preview"), detailX + (detailWidth + 6) / 2, markY(), (detailWidth - 6) / 2, () -> action("preview", "", 0, ""));
        toggle = button(Component.empty(), detailX, bottomY(), (detailWidth - 12) / 3, () -> {
            var m = selected(); if (m != null) action("edit", "enabled", m.enabled() ? 0 : 1, "");
        }); LifeUiStyle.reverse(toggle, toggle::onPress); edits.add(toggle);
        int bottomWidth=(detailWidth-12)/3;
        savePreset=button(Component.translatable("module_picker.sablelife.save"),detailX+bottomWidth+6,bottomY(),bottomWidth,()->minecraft.setScreen(new SaveModulePresetScreen(this)));
        savePreset.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("module_picker.sablelife.portable")));
        remove = button(tr("remove"), detailX+2*(bottomWidth+6), bottomY(), detailWidth-2*(bottomWidth+6), () -> {
            if (deleteUntil > tickCount) { action("remove", "", 0, ""); deleteUntil = 0; }
            else deleteUntil = tickCount + 60;
        }); edits.add(remove); sync();
    }
    private void apply(int index) {
        if (parameterPage * parameterSlots + index >= parameters.size()) return;
        var field = parameters.get(parameterPage * parameterSlots + index);
        var box = fields.get(index);
        try {
            double n = Double.parseDouble(box.getValue());
            double min = field.min() / field.scale(), max = field.max() / field.scale();
            if (!Double.isFinite(n) || n < min || n > max) { box.setTextColor(0xFF7777); return; }
            action("edit", field.key(), n * field.scale(), ""); box.setFocused(false); box.setTextColor(0xE0E0E0);
        } catch (NumberFormatException ignored) { box.setTextColor(0xFF7777); }
    }
    private void changeParameterPage(int delta) {
        fields.forEach(b -> b.setFocused(false));
        parameterPage = Math.clamp(parameterPage + delta, 0, Math.max(0, (parameters.size() - 1) / parameterSlots));
    }
    private static int nextControlTrigger(VirtualModule m,int direction){int next=Math.floorMod((int)m.value("trigger")+direction,5);return next==4&&!MoveSet.TARGETS.contains(m.type())?Math.floorMod(next+direction,5):next;}
    private static int nextTrigger(VirtualModule m,int direction) { int next=Math.floorMod((int)m.value("trigger")+direction,6); return next==4&&!MoveSet.TARGETS.contains(m.type()) ? Math.floorMod(next+direction,6) : next; }
    private void applyChannel() {
        if (!channel.active) return;
        try { int n = Integer.parseInt(channel.getValue()); if (n >= 1 && n <= dev.sablelife.heart.HeartWireless.CHANNELS) { action("edit", "channel", n, ""); channel.setFocused(false); } } catch (NumberFormatException ignored) {}
    }
    private void cycle(int index, int direction) {
        if (parameterPage * parameterSlots + index >= parameters.size() || selected() == null) return;
        var field = parameters.get(parameterPage * parameterSlots + index); var m = selected();
        if(field.key().equals("arenaRespawnRecord")){action("arena_record","",0,"");return;}
        if(field.key().equals("arenaRespawnClear")){action("arena_clear","",0,"");return;}
        if(field.key().equals("arenaBuffGroups")){action("arena_buffs","",0,"");return;}
        if (field.key().equals("commandEditor")) { minecraft.setScreen(new CommandProgramScreen(menu, minecraft.player.getInventory(), title, m.id())); return; }
        if (field.key().equals("moveSetEditor")) { minecraft.setScreen(new MoveSetScreen(menu, minecraft.player.getInventory(), title, m.id())); return; }
        if (field.key().equals("sequenceSteps")) { action("sequence", "", 0, ""); return; }
        if(field.key().equals("accumReset")){action("accum_reset","",0,"");return;}
        if (field.key().equals("redstoneFrequency")) { action("frequency", "", 0, ""); return; }
        if (field.key().equals("effects") || field.key().equals("particles") || VirtualModule.particleStage(field.key())!=null) { action("effects", field.key(), 0, ""); return; }
        if (field.choices().equals(List.of("item"))) { action("projectile", "", 0, ""); return; }
        if (field.choices().equals(List.of("library"))) {
            action("sound_editor",field.key(),0,"");
        } else action("edit", field.key(), Math.floorMod((int) m.value(field.key()) + direction, field.choices().size()), "");
    }
    private void toggleNone(int index) {
        var m = selected(); int row = parameterPage * parameterSlots + index;
        if (m == null || row >= parameters.size()) return;
        var f = parameters.get(row);
        if (f.choices().equals(List.of("library"))) {
            String next = "";
            if (m.text(f.key()).isEmpty()) {
                next = m.text(f.key() + "Last");
                if (next.isEmpty()) next = dev.sablelife.sound.SoundLibrary.entries().stream().filter(e -> !e.id().isEmpty()).map(dev.sablelife.sound.SoundLibrary.Entry::id).findFirst().orElse("");
            }
            action("edit", f.key(), 0, next);
        } else {
            int none = f.choices().indexOf("none");
            if (none >= 0) action("edit", f.key(), m.value(f.key()) == none ? (none + 1) % f.choices().size() : none, "");
        }
    }
    private void toggleRgb(int index){
        int row=parameterPage*parameterSlots+index;var m=selected();if(m==null||row>=parameters.size())return;String key=parameters.get(row).key();if(!ModuleColors.color(m.type(),key))return;
        var current=ModuleColors.vector(m,key);int original=(Math.round(current.x*255)<<16)|(Math.round(current.y*255)<<8)|Math.round(current.z*255);
        int initial=m.value(key+"RgbSet")==1?((int)m.value(key+"Red")<<16)|((int)m.value(key+"Green")<<8)|(int)m.value(key+"Blue"):original;
        minecraft.setScreen(new ColorPickerScreen(this,tr(key),initial,original,(custom,color)->{
            pendingRgb=key;pendingRgbMode=custom?1:0;action("edit",key+"Rgb",pendingRgbMode,((color>>16)&255)+","+((color>>8)&255)+","+(color&255));
        }));
    }

    private void openVanillaSound(int index) {
        int fieldIndex = parameterPage * parameterSlots + index;
        if (selected() == null || fieldIndex >= parameters.size()) return;
        String key = parameters.get(fieldIndex).key(); UUID module = selected;
        boolean editable = snapshot.getBoolean("Editable");
        minecraft.setScreen(new VanillaSoundBrowser(this, sound -> {
            if (editable && selected.equals(module)) action("edit", key, 0, sound);
        }));
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) for (int i = 0; i < choices.size(); i++) {
            var choice = choices.get(i);
            int field = parameterPage * parameterSlots + i;
            if (choice.visible && choice.active && choice.isMouseOver(mouseX, mouseY) && field < parameters.size()
                    && !List.of("effects", "particles", "projectile").contains(parameters.get(field).key())) {
                choice.playDownSound(minecraft.getSoundManager()); cycle(i, -1); return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    private int tickCount;
    @Override protected void containerTick() { super.containerTick(); tickCount++; sync(); }
    private void sync() {
        var data = menu.getSlot(0).getItem().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
        if (data != decodedData) {
            decodedData = data; snapshot = data.copyTag(); modules.clear(); statuses.clear(); var list = snapshot.getList("Modules", 10);
            for (int i = 0; i < list.size(); i++) { modules.add(VirtualModule.load(list.getCompound(i))); statuses.add(list.getCompound(i).getString("Status")); }
        }
        if (!selectionRestored && snapshot.getBoolean("Ready")) {
            if (snapshot.hasUUID("Selected")) selected = snapshot.getUUID("Selected");
            selectionRestored = true;
            for (int i = 0; i < sidebarModules().size(); i++) if (sidebarModules().get(i).id().equals(selected)) page = i / sidebarSlots;
        }

        if (selected() == null)
            selected = sidebarModules().isEmpty() ? NONE : sidebarModules().getFirst().id();
        page = Math.min(page, Math.max(0, (sidebarModules().size() - 1) / sidebarSlots));
        if(beforeAdd!=null){var added=modules.stream().filter(m->m.type().equals(addingType)&&!beforeAdd.contains(m.id())).findFirst();if(added.isPresent()){selected=added.get().id();page=modules.indexOf(added.get())/sidebarSlots;beforeAdd=null;}else if(tickCount>addExpires)beforeAdd=null;}
        var m=selected();boolean editable=snapshot.getBoolean("Editable")&&!(m!=null&&m.type().equals("command")&&!snapshot.getBoolean("CommandEditable"));boolean usable=m!=null&&m.known();
        releaseLife.visible=!snapshot.getBoolean("ControlCore");releaseLife.active = snapshot.getBoolean("Ready");
        lootNav.visible=!snapshot.getBoolean("ControlCore");if(snapshot.getBoolean("ControlCore"))settingsNav.setMessage(Component.translatable("manual.sablelife.open"));
        boolean selectionChanged = !last.equals(selected);
        if (selectionChanged) { parameterPage = 0; fields.forEach(b -> b.setFocused(false)); name.setFocused(false); channel.setFocused(false); }
        var nextFields = m == null ? List.<ModuleFields.Field>of() : ModuleFields.forType(m.type());
        if(m!=null){var soundKeys=ModuleFields.forType(m.type()).stream().filter(f->f.choices().equals(List.of("library"))).map(ModuleFields.Field::key).toList();
            nextFields=nextFields.stream().filter(f->!soundKeys.stream().anyMatch(key->f.key().equals(key+"Volume")||f.key().equals(key+"Radius"))
                && !(List.of("sound","trigger_sound").contains(m.type())&&List.of("volume","pitch").contains(f.key()))&&!f.key().equals("bgmVolume")).toList();}
        if (m != null && m.type().equals("particles")) nextFields = nextFields.stream().filter(f -> dev.sablelife.module.ParticlePattern.visibleField(m, f.key())).toList();
        if(m!=null)nextFields=ModuleColors.display(m,nextFields,parameterSlots);
        if (!nextFields.equals(parameters)) fields.forEach(box -> box.setFocused(false));
        parameters = nextFields;
        if(m!=null&&m.type().equals("linear_movement"))parameters=parameters.stream().filter(f->!(f.key().equals("moveObstruction")&&m.value("linearMode")==1)&&!(f.key().equals("travelSpeed")&&m.value("linearMode")==0)&&!(m.value("moveAim")==0&&List.of("target","moveTargetRange","moveStopDistance","moveTargetVertical","facingCreative").contains(f.key()))&&!(m.value("moveAim")==1&&List.of("moveDistance","moveHorizontal","moveVertical","direction").contains(f.key()))).toList();
        if(selectionChanged&&m!=null){var position=DashboardPageMemory.get(m.id());if(position!=null)parameterPage=position.restore(parameters.stream().map(ModuleFields.Field::key).toList(),parameterSlots);}
        if(!pendingRgb.isEmpty()&&m!=null&&m.value(pendingRgb+"Rgb")==pendingRgbMode){for(int i=0;i<parameters.size();i++)if(parameters.get(i).key().equals(pendingRgb))parameterPage=i/parameterSlots;pendingRgb="";}
        parameterPage = Math.min(parameterPage, Math.max(0, (parameters.size() - 1) / parameterSlots));
        for (int i = 0; i < sidebarSlots; i++) {
            int row=page*sidebarSlots+i;var button=rows.get(i);button.visible=row<sidebarModules().size();button.active=true;
            if(row<sidebarModules().size()){
                var entry=sidebarModules().get(row);Component label=entry.name().isEmpty()?tr("type_"+entry.type()):Component.literal(entry.name());
                button.setMessage(Component.literal(entry.id().equals(selected)?"▶ ":"").append(label).append(" #"+(row+1)));
                LifeUiStyle.bookmark(button,ModuleCategory.of(entry.type()).color);
                button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.empty().append(tr("type_"+entry.type())).append("\n").append(tr("description_"+entry.type()))));
            }
        }
        add.active=editable&&modules.size()<HeartModules.LIMIT;add.setMessage(tr("add"));
        int selectedIndex=modules.indexOf(m);moveModuleUp.active=editable&&selectedIndex>0;moveModuleDown.active=editable&&selectedIndex>=0&&selectedIndex<modules.size()-1;
        if(reorderDestination>=0){if(selectedIndex==reorderDestination){page=selectedIndex/sidebarSlots;reorderDestination=-1;}else if(tickCount>reorderExpires)reorderDestination=-1;}
        int categoryColor = m == null ? 0 : ModuleCategory.of(m.type()).color;
        for (Button b : edits) { b.active = editable && usable; LifeUiStyle.bookmark(b, b.getWidth() >= 60 ? categoryColor : 0); }
        savePreset.active=usable;LifeUiStyle.bookmark(savePreset,categoryColor);
        LifeUiStyle.bookmark(preview, categoryColor);
        boolean loot = usable && m.type().equals("loot"), wireless = usable && !loot && ((int) m.value("trigger") == 2 || (int) m.value("trigger") == 3);
        name.visible = m != null; name.setEditable(editable && usable); channel.setEditable(editable && wireless);
        channel.active = editable && wireless; channel.setTextColor(editable && wireless ? 0xE0E0E0 : 0x666666);
        channelApply.active = editable && wireless;
        channel.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("channel_unbound_help")));
        if (!wireless) channel.setFocused(false);
        if (m != null) {
            if (!name.isFocused() || !last.equals(selected)) name.setValue(m.name());
            if (!channel.isFocused() || !last.equals(selected)) channel.setValue(Integer.toString((int) m.value("channel") + 1));
            for (int i = 0; i < fields.size(); i++) {
                var box = fields.get(i); box.setEditable(editable && usable);
                int index = parameterPage * parameterSlots + i; boolean exists = index < parameters.size()&&!parameters.get(index).key().startsWith("__rgb_pad_");
                var field = exists ? parameters.get(index) : null;
                box.visible = exists && !field.choice(); applyButtons.get(i).visible = box.visible;
                choices.get(i).visible = exists && field.choice();
                boolean soundField = exists && field.choices().equals(List.of("library"));
                boolean hasNone = exists && field.choices().contains("none");
                boolean isNone = hasNone && (soundField ? m.text(field.key()).isEmpty() : field.choices().indexOf("none") == (int) m.value(field.key()));
                noneButtons.get(i).visible = hasNone; noneButtons.get(i).active = editable && usable;
                noneButtons.get(i).setMessage(Component.literal(isNone ? "✓" : ""));
                choices.get(i).setX(leftPos + inputX + (hasNone ? 20 : 0));
                if(exists&&field.key().equals("redstoneFrequency"))choices.get(i).setX(leftPos+frequencyButtonX());
                soundFolders.get(i).visible = false; soundFolders.get(i).active = true;
                vanillaFolders.get(i).visible = false; vanillaFolders.get(i).active = true;
                box.setWidth(exists && field.key().equals("phaseChannel") ? 40 : applyX - inputX - 32);
                choices.get(i).setWidth(exists && field.key().equals("redstoneFrequency") ? frequencyButtonWidth() : exists && field.key().equals("targetMultiple") ? 18 : exists && List.of("lineOfSight", "arenaDeath", "arenaForceReturn", "trail").contains(field.key()) ? 36 : (imageWidth - inputX - 11) - (hasNone ? 20 : 0));
                var rgbButton=rgbButtons.get(i);boolean colorField=exists&&ModuleColors.color(m.type(),field.key());rgbButton.visible=colorField;rgbButton.active=colorField&&editable&&usable;
                if(colorField){rgbButton.setX(leftPos+inputX);LifeUiStyle.bookmark(rgbButton,m.value(field.key()+"Rgb")==1?0xB079E0:0);choices.get(i).setX(leftPos+inputX+40);choices.get(i).setWidth(imageWidth-inputX-51);}
                if (exists) {
                    boolean automaticAim = field.key().equals("targetMultiple") && (m.type().equals("mortar") ? m.value("aimMode") != 1 : m.type().equals("laser") ? m.value("laserAim") != 1 : m.type().equals("quake") ? m.value("quakeOrigin") != 1 : m.type().equals("homing") ? false : m.value("strikeMode") != 2)
                            || m.type().equals("ground_strike") && (field.key().equals("strikeRandomCount") && m.value("strikeMode") != 3 || field.key().equals("strikeRays") && m.value("strikeMode") != 1)
                            || m.type().equals("quake") && (field.key().equals("quakeDetectRange") && m.value("quakeOrigin") != 1 || List.of("quakeWidth", "quakeSpeed").contains(field.key()) && m.value("quakeMode") == 2)
                            || m.type().equals("mortar") && m.value("aimMode") == 1 && List.of("launchAngle", "direction").contains(field.key())
                            || m.type().equals("laser") && m.value("laserAim") != 1 && List.of("laserTracking", "laserFollow").contains(field.key())
                            || m.type().equals("laser") && m.value("laserAim") == 1 && List.of("direction", "sweepAngle").contains(field.key())
                            || m.type().equals("linear_movement")&&field.key().equals("moveObstruction")&&(m.value("moveForce")==1||m.value("linearMode")==1)
                            || m.type().equals("wireless_redstone") && field.key().equals("redstonePulse") && m.value("redstoneMode") == 0
                            || m.type().equals("health_phase") && field.key().equals("phasePulse") && m.value("phaseMode") == 0
                            || ModuleColors.color(m.type(),field.key())&&m.value(field.key()+"Rgb")==1
                            || m.type().equals("laser")&&(field.key().equals("laserBreakMode")&&m.value("laserBreakBlocks")==0||field.key().equals("laserBreakSpeed")&&(m.value("laserBreakBlocks")==0||m.value("laserBreakMode")==0))
                            || field.key().equals("castTargetRange")&&!TargetCastOrigin.targeted(m)
                            || TargetCastOrigin.targeted(m)&&List.of("startDistance","sphereDistance","direction","pullOffset").contains(field.key());
                    box.setEditable(editable && usable && !automaticAim); box.active = editable && usable && !automaticAim && !isNone;
                    applyButtons.get(i).active = box.active; choices.get(i).active = (editable || soundField || field.key().equals("effects") || field.key().equals("particles") || field.key().endsWith("Particles") || field.key().equals("sequenceSteps") || field.key().equals("moveSetEditor") || field.key().equals("commandEditor") || field.key().equals("arenaBuffGroups")) && usable && !automaticAim && !isNone;
                    box.setTextColor(automaticAim ? 0x777777 : 0xE0E0E0);
                    box.setTooltip(m.type().equals("signal_turn")?net.minecraft.client.gui.components.Tooltip.create(tr("signal_turn_help")):m.type().equals("damage_accumulation")?net.minecraft.client.gui.components.Tooltip.create(tr("accum_help")):m.type().equals("armor")?net.minecraft.client.gui.components.Tooltip.create(tr("armor_help")):m.type().equals("item_display")?net.minecraft.client.gui.components.Tooltip.create(tr("item_display_help")):m.type().equals("melee") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("meleeHitLimit") ? "melee_hit_limit_help" : List.of("startDistance","meleeHorizontal","meleeVertical").contains(field.key()) ? "melee_offset_help" : "melee_help")) : m.type().equals("particles") ? net.minecraft.client.gui.components.Tooltip.create(tr("particle_pattern_help")) : m.type().equals("arena") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("countdown") ? "arena_countdown_help" : "arena_help")) : m.type().equals("flame") && field.key().equals("flameParticleSize") ? net.minecraft.client.gui.components.Tooltip.create(tr("flame_visual_help")) : m.type().equals("slash") ? net.minecraft.client.gui.components.Tooltip.create(tr("slash_help")) : m.type().equals("sphere_blast") ? net.minecraft.client.gui.components.Tooltip.create(tr("sphere_blast_help")) : m.type().equals("linear_movement") ? net.minecraft.client.gui.components.Tooltip.create(tr("linear_help")) : field.key().equals("startDistance") ? net.minecraft.client.gui.components.Tooltip.create(tr("start_distance_help")) : m.type().equals("laser") && List.of("laserTracking", "laserFollow").contains(field.key()) ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("laserFollow") ? "laser_follow_help" : "laser_tracking_help")) : m.type().equals("quake") ? net.minecraft.client.gui.components.Tooltip.create(tr("quake_help")) : m.type().equals("wireless_redstone") ? net.minecraft.client.gui.components.Tooltip.create(tr("redstone_help")) : m.type().equals("pull") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("pullStop")?"pull_stop_help":"pull_help")) : m.type().equals("health_phase") ? net.minecraft.client.gui.components.Tooltip.create(tr("phase_help")) : m.type().equals("homing") ? net.minecraft.client.gui.components.Tooltip.create(tr("homing_help")) : m.type().equals("laser") ? net.minecraft.client.gui.components.Tooltip.create(tr("laser_help")) : m.type().equals("mortar") && field.key().equals("speed")
                            ? net.minecraft.client.gui.components.Tooltip.create(tr("mortar_speed_help")) : m.type().equals("auto_facing") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("facingCollision")?"facing_collision_help":field.key().equals("lineOfSight")?"line_of_sight_help":"auto_facing_help")) : null);
                    if (!box.isFocused() || !last.equals(selected)) box.setValue(String.format(Locale.ROOT, "%.2f", m.value(field.key()) / field.scale()));
                    if (m.type().equals("health_phase") && field.key().equals("phaseChannel")) {
                        String state = snapshot.getList("Modules", 10).getCompound(modules.indexOf(m)).getString("PhaseChannelStatus");
                        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.empty().append(tr("phase_channel_" + state)).append("\n").append(tr("phase_channel_note"))));
                        if (!state.equals("ready")) box.setTextColor(0xFF7777);
                    }
                    if(field.key().startsWith("arenaRespawn")){
                        var state=snapshot.getList("Modules",10).getCompound(modules.indexOf(m)).getCompound("RespawnRuntime");
                        boolean recordButton=field.key().equals("arenaRespawnRecord")||field.key().equals("arenaRespawnClear");
                        choices.get(i).active=usable&&state.getBoolean("Available")&&snapshot.getBoolean("RespawnAllowed")&&(editable||recordButton);
                        if(field.key().equals("arenaRespawnClear"))choices.get(i).active &= state.getBoolean("Recorded");
                        if(!state.getBoolean("Available")||!snapshot.getBoolean("RespawnAllowed")){box.active=false;applyButtons.get(i).active=false;choices.get(i).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("arena_respawn.sablelife.requires")));}
                    }
                    if (field.choice()) {
                        Component label = field.key().equals("arenaRespawnRecord")?Component.translatable("arena_respawn.sablelife.record"+(snapshot.getList("Modules",10).getCompound(modules.indexOf(m)).getCompound("RespawnRuntime").getBoolean("Recorded")?"ed":"")):field.key().equals("arenaBuffGroups")?Component.translatable("arena_buffs.sablelife.count",m.arenaBuffs().groups().size()):field.key().equals("targetMultiple") ? Component.literal(m.value("targetMultiple") == 1 ? "✓" : "") : VirtualModule.particleStage(field.key())!=null ? Component.translatable("particles.sablelife.count",m.movementParticles(VirtualModule.particleStage(field.key())).particles().size()) : field.key().equals("particles") ? Component.translatable("particles.sablelife.count", m.particles().size()) : field.key().equals("effects") ? Component.translatable("effects.sablelife.count", m.effects().size())
                                : field.choices().equals(List.of("item")) ? (minecraft.level==null||m.projectileItem(minecraft.level.registryAccess()).isEmpty() ? tr(m.type().equals("item_display")?"display_empty":m.type().equals("arrow") ? "projectile_default_arrow" : m.type().equals("homing") ? "projectile_default_homing" : "projectile_default") : m.projectileItem(minecraft.level.registryAccess()).getHoverName())
                                : field.choices().equals(List.of("library")) ? Component.translatable("sound_timeline.sablelife.count",m.soundTimeline(field.key()).rows().size())
                                : tr("choice_" + field.choices().get(Math.clamp((int) m.value(field.key()), 0, field.choices().size() - 1)));
                        if(colorField&&m.value(field.key()+"Rgb")==1){var rgb=ModuleColors.vector(m,field.key());label=Component.literal(String.format(Locale.ROOT,"#%02X%02X%02X",Math.round(rgb.x*255),Math.round(rgb.y*255),Math.round(rgb.z*255)));}
                        choices.get(i).setMessage(field.key().equals("redstoneFrequency") ? tr("frequency_item") : label);
                        if(field.key().startsWith("arenaRespawn")&&(!snapshot.getBoolean("RespawnAllowed")||!snapshot.getList("Modules",10).getCompound(modules.indexOf(m)).getCompound("RespawnRuntime").getBoolean("Available")))choices.get(i).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("arena_respawn.sablelife.requires")));
                        choices.get(i).setTooltip(m.type().equals("signal_turn")?net.minecraft.client.gui.components.Tooltip.create(tr("signal_turn_help")):m.type().equals("damage_accumulation")?net.minecraft.client.gui.components.Tooltip.create(tr("accum_help")):m.type().equals("auto_facing") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("facingCollision")?"facing_collision_help":field.key().equals("lineOfSight")?"line_of_sight_help":"auto_facing_help")) : m.type().equals("particles") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("trail") ? "particle_trail_help" : field.key().equals("motionOnly") ? "particle_motion_only_help" : field.key().equals("particlePropagation") ? "particle_propagation_help" : field.key().equals("particleLineMode") ? "particle_spray_help" : "particle_pattern_help")) : m.type().equals("arena") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("arenaDeath") ? "arena_death_note" : field.key().equals("arenaForceReturn") ? "arena_force_return_note" : "arena_help")) : m.type().equals("flame") && List.of("particles","effects").contains(field.key()) ? net.minecraft.client.gui.components.Tooltip.create(tr("flame_visual_help")) : field.key().equals("target") ? net.minecraft.client.gui.components.Tooltip.create(tr(m.type().equals("linear_movement")?"linear_help":"target_help")) : field.key().equals("targetMultiple") ? net.minecraft.client.gui.components.Tooltip.create(tr("target_multiple_help")) : field.key().equals("lineOfSight") ? net.minecraft.client.gui.components.Tooltip.create(tr("line_of_sight_help"))
                                : soundField ? net.minecraft.client.gui.components.Tooltip.create(Component.empty().append(label).append("\n").append(Component.translatable("sound_timeline.sablelife.open_help")))
                                : field.choices().equals(List.of("item")) ? net.minecraft.client.gui.components.Tooltip.create(tr(m.type().equals("item_display")?"item_display_help":"projectile_help"))
                                : m.type().equals("slash") ? net.minecraft.client.gui.components.Tooltip.create(tr("slash_help")) : m.type().equals("sphere_blast") ? net.minecraft.client.gui.components.Tooltip.create(tr("sphere_blast_help")) : m.type().equals("linear_movement") ? net.minecraft.client.gui.components.Tooltip.create(tr("linear_help"))
                                : m.type().equals("arena") ? net.minecraft.client.gui.components.Tooltip.create(tr("arena_help"))
                                : m.type().equals("health_phase") ? net.minecraft.client.gui.components.Tooltip.create(tr("phase_help"))
                                : m.type().equals("quake") ? net.minecraft.client.gui.components.Tooltip.create(tr("quake_help")) : m.type().equals("wireless_redstone") ? net.minecraft.client.gui.components.Tooltip.create(tr("redstone_help"))
                                : m.type().equals("sequence") ? net.minecraft.client.gui.components.Tooltip.create(tr("sequence_help"))
                                : m.type().equals("pull") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("pullStop")?"pull_stop_help":"pull_help"))
                                : m.type().equals("homing") ? net.minecraft.client.gui.components.Tooltip.create(tr("homing_help")) : m.type().equals("laser") ? net.minecraft.client.gui.components.Tooltip.create(tr("laser_help"))
                                : m.type().equals("ground_strike") ? net.minecraft.client.gui.components.Tooltip.create(tr("ground_strike_help"))
                                : m.type().equals("trigger_sound") ? net.minecraft.client.gui.components.Tooltip.create(tr("trigger_sound_help"))
                                : m.type().equals("pulse") && field.key().equals("pulseStart") ? net.minecraft.client.gui.components.Tooltip.create(tr("pulse_start_help"))
                                : m.type().equals("armor") ? net.minecraft.client.gui.components.Tooltip.create(tr("armor_help"))
                                : m.type().equals("auto_facing") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("facingCollision")?"facing_collision_help":field.key().equals("lineOfSight")?"line_of_sight_help":"auto_facing_help")) : m.type().equals("navigation") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("navigationInvert")?"navigation_invert_help":"navigation_help")) : m.type().equals("particles") ? net.minecraft.client.gui.components.Tooltip.create(tr(field.key().equals("trail")?"particle_trail_help":field.key().equals("motionOnly")?"particle_motion_only_help":"particle_propagation_help")) : null);
                    }
                    if(field.key().equals("accumReset"))choices.get(i).active=usable&&minecraft.player!=null&&minecraft.player.isCreative();
                    if(snapshot.getBoolean("ControlCore")&&dev.sablelife.heart.RedstoneCorePolicy.disabledField(m,field.key())){
                        box.active=false;box.setEditable(false);applyButtons.get(i).active=false;choices.get(i).active=false;noneButtons.get(i).active=false;
                        choices.get(i).setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("control_audio_help")));
                        if(field.key().equals("bgmStart"))choices.get(i).setMessage(tr("choice_on_approach"));
                    }
                }
            }
            trigger.setMessage(tr("trigger_" + (int) m.value("trigger")));
            trigger.setTooltip(m.value("trigger") == 2 ? net.minecraft.client.gui.components.Tooltip.create(tr("trigger_internal_help")) : null); toggle.setMessage(tr(m.enabled() ? "disable" : "enable"));
        } else { rgbButtons.forEach(b->b.visible=false);name.setValue(""); channel.setValue(""); fields.forEach(b -> { b.setValue(""); b.visible = false; }); applyButtons.forEach(b -> b.visible = false); choices.forEach(b -> b.visible = false); soundFolders.forEach(b -> b.visible = false); vanillaFolders.forEach(b -> b.visible = false); noneButtons.forEach(b -> b.visible = false); }
        edits.forEach(b -> { if (m == null) b.visible = false; });
        if (m != null) { toggle.visible = remove.visible = mark.visible = trigger.visible = channelApply.visible = true; edits.getFirst().visible = true; }
        samples.visible = loot; samples.active = loot;
        if (loot) trigger.visible = channelApply.visible = false;
        channel.visible = m != null && !loot; preview.visible = m != null;
        previousParameters.visible = nextParameters.visible = parameters.size() > parameterSlots;
        previousParameters.active = parameterPage > 0; nextParameters.active = (parameterPage + 1) * parameterSlots < parameters.size();
        boolean bgm = usable && m.type().equals("bgm");
        mark.visible = m != null && !m.coreModule();
        preview.visible = m != null && !m.type().equals("sequence") && !m.type().equals("health_phase") && !m.type().equals("damage_accumulation") ;
        if (m != null && (m.type().equals("move_set") || m.type().equals("command"))) preview.visible = false;
        if (m != null && m.value("trigger") >= 4) channel.visible = channelApply.visible = false;
        hurtSettings.visible = !snapshot.getBoolean("ControlCore") && m != null && !loot && m.value("trigger") == 5; hurtSettings.active = usable;
        if(hurtSettings.visible) hurtSettings.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("hurt.sablelife.summary", Component.translatable("hurt.sablelife.mode_"+(int)m.value("hurtMode")), String.format(Locale.ROOT,"%.2f",m.value("hurtCooldown")/20))));
        preview.setMessage(tr("preview")); preview.active = usable && (bgm || TargetCastOrigin.targeted(m) || m.type().equals("arena") && m.arenaCenter() != null || !m.anchors().isEmpty());
        mark.setMessage(tr(m != null && m.area() ? "mark_particle_area" : m != null && m.type().equals("particles") ? "mark_particle_point" : m != null && m.type().equals("arena") ? "mark_arena_center" : loot ? "mark_limb" : m != null && List.of("navigation","auto_facing","signal_turn").contains(m.type()) ? "mark_front"
                : m != null && m.type().equals("sound") && m.value("contactMode") == 1 ? "mark_contact" : "mark"));
        mark.setTooltip(m!=null&&m.type().equals("signal_turn")?net.minecraft.client.gui.components.Tooltip.create(tr("signal_turn_help")):m != null && m.type().equals("auto_facing") ? net.minecraft.client.gui.components.Tooltip.create(tr("auto_facing_help")) : null);
        if (m != null && m.type().equals("particles")) {
            String status = statuses.get(modules.indexOf(m));
            preview.active = usable && (status.equals("valid") || status.equals("disabled"));
            mark.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("particle_mark_help")));
        }
        if (!last.equals(selected)) deleteUntil = 0;
        remove.setMessage(tr(deleteUntil > tickCount ? "confirm_remove" : "remove")); last = selected;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) {
            if (name.isFocused()) { action("edit", "name", 0, name.getValue()); name.setFocused(false); return true; }
            if (channel.isFocused()) { applyChannel(); return true; }
            for (int i = 0; i < fields.size(); i++) if (fields.get(i).isFocused()) { apply(i); return true; }
        }
        // AbstractContainerScreen handles the inventory binding before dispatching to EditBox.
        // Keep all non-Escape keys in the focused editor; charTyped then inserts letters normally.
        if (key != 256) {
            for (EditBox input : allInputs()) if (input.visible && input.active && input.isFocused()) {
                input.keyPressed(key, scan, modifiers); return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private List<EditBox> allInputs() { var result = new ArrayList<>(fields); result.add(name); result.add(channel); return result; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0 || !snapshot.getBoolean("Editable") || selected() == null) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        if(channel.visible&&channel.active&&channel.isMouseOver(mouseX,mouseY)){
            int value;try{value=Integer.parseInt(channel.getValue());}catch(NumberFormatException ignored){value=(int)selected().value("channel")+1;}
            channel.setValue(Integer.toString(Math.clamp(value+(int)Math.signum(vertical),1,dev.sablelife.heart.HeartWireless.CHANNELS)));applyChannel();channel.setFocused(true);return true;
        }
        for (int i = 0; i < fields.size(); i++) {
            var box = fields.get(i); int index = parameterPage * parameterSlots + i;
            if (!box.visible || !box.active || !box.isMouseOver(mouseX, mouseY) || index >= parameters.size()) continue;
            var field = parameters.get(index); if (field.choice()) continue;
            double value;
            try { value = Double.parseDouble(box.getValue()); } catch (NumberFormatException ignored) { value = selected().value(field.key()) / field.scale(); }
            double step = NumericWheelSteps.module(selected().type(),field.key(),field.scale(),field.min());
            double next = Math.clamp(Math.round((value + Math.signum(vertical) * step) * 100) / 100., field.min() / field.scale(), field.max() / field.scale());
            box.setValue(String.format(Locale.ROOT, "%.2f", next)); apply(i); box.setFocused(true); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos - 2, topPos - 2, leftPos + imageWidth + 2, topPos + imageHeight + 2, 0xFF19151D);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF9B614E);
        g.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFF242632);
        g.fill(leftPos + 5, topPos + 29, leftPos + detailX - 13, topPos + imageHeight - 22, 0xFF363140);
        g.fill(leftPos + detailX-9, topPos + 30, leftPos + detailX-7, topPos + imageHeight - 3, 0xFF9770B5);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, tr("modules"), 9, 34, 0xD9C4ED, false);
        g.drawString(font, (page + 1) + " / " + Math.max(1, (sidebarModules().size() + sidebarSlots - 1) / sidebarSlots), 7+(detailX-23)/2-20, imageHeight - 39, 0xD9C4ED, false);
        var m = selected(); if (m == null) { g.drawWordWrap(font, tr("empty"), detailX, 74, detailWidth, 0xD9C4ED); return; }
        Component current = Component.translatable("dashboard.sablelife.current", tr(m.enabled() ? "enabled" : "disabled"))
                .append(" | ").append(tr((m.coreModule()) && statuses.get(modules.indexOf(m)).equals("valid") ? "status_core" : "status_" + statuses.get(modules.indexOf(m))));
        g.pose().pushPose(); g.pose().translate(detailX, 53, 0); g.pose().scale(textScale(), textScale(), 1);
        g.drawString(font, font.plainSubstrByWidth(current.getString(), (int) (detailWidth / textScale())), 0, 0, 0xD9C4ED, false); g.pose().popPose();
        for (int i = 0; i < parameterSlots; i++) if (parameterPage * parameterSlots + i < parameters.size()) {
            String key = parameters.get(parameterPage * parameterSlots + i).key();if(key.startsWith("__rgb_pad_"))continue;
            var label = tr(m.type().equals("slash")&&key.equals("flightSound")?"slashReleaseSound":m.type().equals("slash")&&key.equals("range")?"slash_range":m.type().equals("quake") && key.equals("quakeRange") ? m.value("quakeMode") == 0 ? "quake_length" : "quake_radius" : m.type().equals("ground_strike") && m.value("strikeMode") == 3 && List.of("strikeRange", "waveDelay").contains(key) ? "random_" + key : key.equals("speed") && (m.type().equals("gun") || m.type().equals("mortar")) ? m.type() + "_speed"
                    : key.equals("speed") && m.type().equals("homing") ? "homing_speed" : key.equals("particles") && List.of("arrow", "gun", "mortar", "homing").contains(m.type()) ? "flight_particles"
                    : key.equals("arrows") && m.type().equals("gun") ? "bullets" : key.equals("interval") && m.type().equals("laser") ? "burstInterval" : key);
            int labelWidth = (int) ((inputX - detailX - 6) / textScale());
            if(key.equals("redstoneFrequency"))labelWidth=Math.max(1,font.width(label));
            if(m.type().equals("pull")&&key.equals("pullStop"))label=label.copy().append("\n").append(tr("pull_stop_note"));
            if(m.type().equals("arena")&&key.equals("arenaRespawnRecord"))label=label.copy().append("\n").append(tr("arena_record_config_note").copy().withStyle(net.minecraft.ChatFormatting.GRAY));
            int lines = font.split(label, labelWidth).size();
            float scale = Math.min(textScale(), (rowPitch - 2f) / Math.max(font.lineHeight, lines * font.lineHeight));
            if(key.equals("redstoneFrequency"))scale=Math.min(scale,(frequencySlotsX()-detailX-4f)/Math.max(1,font.width(label)));
            g.pose().pushPose();
            g.pose().translate(detailX, 63 + i * rowPitch + (rowPitch - 2 - lines * font.lineHeight * scale) / 2, 0);
            g.pose().scale(scale, scale, 1);
            g.drawWordWrap(font, label, 0, 0, labelWidth, 0xE6D8EA);
            g.pose().popPose();
            var field = parameters.get(parameterPage * parameterSlots + i);
            if (List.of("lineOfSight", "arenaDeath", "arenaForceReturn", "trail").contains(key)) {
                var hint = tr(key.equals("trail") ? "particle_trail_note" : key.equals("arenaDeath") ? "arena_death_note" : key.equals("arenaForceReturn") ? "arena_force_return_note" : "line_of_sight_note");
                int noteX = inputX + 42;
                int available = imageWidth - noteX - 11;
                float hintScale = Math.min(textScale(), available / (float) Math.max(1, font.width(hint)));
                g.pose().pushPose();
                g.pose().translate(noteX, 63 + i * rowPitch + (rowPitch - 2 - font.lineHeight * hintScale) / 2, 0);
                g.pose().scale(hintScale, hintScale, 1);
                g.drawString(font, hint, 0, 0, 0xBDC5D4, false); g.pose().popPose();
            }
            if (key.equals("redstoneFrequency")) {
                var frequencyModule=selected();
                int slotSize=Math.min(16,rowPitch-2), rowY=63+i*rowPitch;
                if(frequencyModule!=null) for(int frequency=0;frequency<2;frequency++) {
                    int slotX=frequencySlotsX()+frequency*(slotSize+5);
                    g.fill(slotX,rowY,slotX+slotSize,rowY+slotSize,frequency==0?0xFFCC666E:0xFF6D9ACC);
                    g.fill(slotX+1,rowY+1,slotX+slotSize-1,rowY+slotSize-1,0xFF403B49);
                    g.pose().pushPose();g.pose().translate(slotX+1,rowY+1,0);g.pose().scale((slotSize-2)/16f,(slotSize-2)/16f,1);
                    g.renderItem(minecraft.level==null?net.minecraft.world.item.ItemStack.EMPTY:frequencyModule.redBlue(frequency==1,minecraft.level.registryAccess()),0,0);g.pose().popPose();
                }
                var hint = tr(m.type().equals("damage_accumulation")?"accum_link_note":"redstone_link_note");
                int noteX = frequencyButtonX()+frequencyButtonWidth()+4, available = Math.max(1,imageWidth-noteX-11);
                float hintScale = Math.min(textScale(), available / (float) Math.max(1, font.width(hint)));
                g.pose().pushPose();
                g.pose().translate(noteX, 63 + i * rowPitch + (rowPitch - 2 - font.lineHeight * hintScale) / 2, 0);
                g.pose().scale(hintScale, hintScale, 1); g.drawString(font, hint, 0, 0, 0xBDC5D4, false); g.pose().popPose();
            }
            if (key.equals("phaseChannel")) {
                var internal = tr("phase_channel_internal_note"); var external = tr("phase_channel_external_note");
                int noteX = inputX + 44, available = applyX - noteX - 4;
                float hintScale = Math.min(textScale(), Math.min(available / (float) Math.max(1, Math.max(font.width(internal), font.width(external))), (rowPitch - 4f) / (2 * font.lineHeight)));
                g.pose().pushPose();
                g.pose().translate(noteX, 63 + i * rowPitch + (rowPitch - 2 - 2 * font.lineHeight * hintScale) / 2, 0);
                g.pose().scale(hintScale, hintScale, 1);
                g.drawString(font, internal, 0, 0, 0xBDC5D4, false);
                g.drawString(font, external, 0, font.lineHeight, 0xBDC5D4, false); g.pose().popPose();
            }
            if (!field.choice() && !key.equals("phaseChannel")) {
                double maximum = field.max() / field.scale();
                String hint = field.min() < 0 ? ((int) (field.min() / field.scale())) + "…" + (int) maximum : "≤" + (maximum == Math.rint(maximum) ? Integer.toString((int) maximum) : Double.toString(maximum));
                float hintScale = Math.min(textScale(), 26f / Math.max(1, font.width(hint)));
                g.pose().pushPose(); g.pose().translate(applyX - 28, 66 + i * rowPitch, 0); g.pose().scale(hintScale, hintScale, 1);
                g.drawString(font, hint, 0, 0, 0xBDC5D4, false); g.pose().popPose();
            }
        }
        if (parameters.size() > parameterSlots) g.drawString(font, (parameterPage + 1) + " / " + ((parameters.size() + parameterSlots - 1) / parameterSlots), detailX + detailWidth / 2 - 10, pagerY() + 5, 0xD9C4ED, false);
        boolean running = snapshot.getList("Modules", 10).getCompound(modules.indexOf(m)).getBoolean("TriggerPowered");
        String runtime = m.type().equals("loot") ? "loot_detach" : running ? "runtime_running" : "runtime_waiting";
        if (running && m.type().equals("quake")) runtime = "runtime_quake_" + snapshot.getList("Modules", 10).getCompound(modules.indexOf(m)).getString("QuakePhase");
        if (running && m.type().equals("pull")) runtime = "runtime_pull_" + snapshot.getList("Modules", 10).getCompound(modules.indexOf(m)).getString("PullPhase");
        if (running && m.type().equals("laser")) runtime = "runtime_laser_" + snapshot.getList("Modules", 10).getCompound(modules.indexOf(m)).getString("LaserPhase");
        if(m.type().equals("linear_movement"))runtime="move_runtime_"+snapshot.getList("Modules",10).getCompound(modules.indexOf(m)).getString("MovementPhase");
        if(m.type().equals("item_display"))runtime=snapshot.getList("Modules",10).getCompound(modules.indexOf(m)).getBoolean("ItemDisplayVisible")?"display_visible":"display_hidden";
        if (m.type().equals("sequence")) runtime = snapshot.getList("Modules", 10).getCompound(modules.indexOf(m)).getCompound("SequenceRuntime").getBoolean("Running") ? "runtime_sequence_running" : "runtime_sequence_waiting";
        if (m.type().equals("health_phase")) runtime = snapshot.getList("Modules", 10).getCompound(modules.indexOf(m)).getBoolean("PhaseActive") ? "runtime_phase_active" : "runtime_phase_waiting";
        if (m.type().equals("arena")) {
            var arena = snapshot.getList("Modules", 10).getCompound(modules.indexOf(m));
            runtime = !snapshot.getString("LifeState").equals("ALIVE") ? "runtime_arena_dormant" : !running ? "runtime_arena_signal" : arena.getBoolean("ArenaWarning") ? "runtime_arena_warning" : arena.getDouble("ArenaDistance") > m.value("arenaRadius") ? "runtime_arena_no_players" : "runtime_arena_standby";
        }
        if (m.type().equals("armor")) runtime = running ? "runtime_armored" : "runtime_armor_waiting";
        if (running && m.type().equals("pulse") && m.value("pulseStart") == 1) runtime = snapshot.getList("Modules", 10).getCompound(modules.indexOf(m)).getBoolean("PulseTargetPresent")
                ? "runtime_pulse_target" : "runtime_pulse_armed";
        if (running && m.type().equals("sound")) runtime = "runtime_listening";
        if (m.type().equals("trigger_sound")) runtime = running ? m.value("soundMode") == 0 ? "runtime_trigger_sound_once" : "runtime_trigger_sound_repeat" : "runtime_trigger_sound_waiting";
        if (m.type().equals("bgm")) runtime = running && (snapshot.getBoolean("ControlCore")||m.value("bgmStart") == 1) ? "runtime_bgm_range"
                : running && snapshot.getBoolean("BgmStarted") ? "runtime_bgm" : "runtime_bgm_waiting";
        if (running && m.type().equals("sensor")) runtime = snapshot.getList("Modules", 10).getCompound(modules.indexOf(m)).getBoolean("SensorPowered") ? "runtime_detected" : "runtime_target_waiting";
        Component runtimeLabel = tr(runtime);
        if(m.type().equals("damage_accumulation")){var data=snapshot.getList("Modules",10).getCompound(modules.indexOf(m)).getCompound("AccumulationRuntime");runtimeLabel=Component.translatable("dashboard.sablelife.accum_progress",String.format(Locale.ROOT,"%.1f",data.getDouble("Accumulated")),String.format(Locale.ROOT,"%.1f",m.value("accumThreshold")),data.getLong("Pending"))
            .append(" | ").append(tr(data.getBoolean("High")?(m.value("accumInvert")==1?"accum_interrupted":"accum_emitting"):!running?"accum_paused":!data.getString("ChannelStatus").equals("ready")&&!m.hasRedBlue()?"accum_no_output":"accum_counting"));}
        if (m.type().equals("health_phase")) {
            var data = snapshot.getList("Modules", 10).getCompound(modules.indexOf(m));
            String state = data.getString("PhaseChannelStatus");
            runtimeLabel = !data.getBoolean("PhaseRangeValid") ? tr("phase_invalid_range")
                    : !state.equals("ready") ? tr("phase_channel_" + state)
                    : !running ? tr("runtime_phase_waiting")
                    : data.getBoolean("PhaseActive") ? tr("runtime_phase_active")
                    : m.value("phaseMode") == 1 && dev.sablelife.module.HealthPhase.contains(snapshot.getDouble("HealthPercent"), 100, m.value("phaseLower"), m.value("phaseUpper"))
                    ? tr("phase_pulse_done") : tr("runtime_phase_waiting");
            runtimeLabel = Component.translatable("dashboard.sablelife.phase_health", String.format(Locale.ROOT, "%.1f", snapshot.getDouble("HealthPercent")))
                    .append(" | ").append(runtimeLabel);
        }

        if(m.type().equals("signal_turn")){String turnState=snapshot.getList("Modules",10).getCompound(modules.indexOf(m)).getString("TurnState");runtimeLabel=tr("turn_state_"+(turnState.isBlank()?"idle":turnState));}
        if(m.type().equals("auto_facing")){runtimeLabel=tr("facing_state_"+snapshot.getList("Modules",10).getCompound(modules.indexOf(m)).getString("FacingState"));}
        if (m.type().equals("arena") && m.arenaCenter() != null) {
            var data = snapshot.getList("Modules", 10).getCompound(modules.indexOf(m));
            runtimeLabel = Component.translatable("dashboard.sablelife.arena_distance", String.format(Locale.ROOT, "%.1f", data.getDouble("ArenaDistance")),
                    String.format(Locale.ROOT, "%.1f", m.value("arenaRadius"))).append(" | ").append(runtimeLabel);
        }
        if (running && m.type().equals("navigation")) {
            var data = snapshot.getList("Modules", 10).getCompound(modules.indexOf(m));
            runtimeLabel = data.getString("NavigationTarget").isEmpty() ? tr("runtime_target_waiting")
                    : Component.translatable("dashboard.sablelife.navigation_tracking", data.getString("NavigationTarget"), data.getInt("NavigationLeft"), data.getInt("NavigationRight"), data.getInt("NavigationUp"), data.getInt("NavigationDown"));
        }
        if(snapshot.getBoolean("ControlCore"))runtimeLabel=Component.translatable("control.sablelife."+snapshot.getString("ControlStatus"));
        float runtimeScale = Math.min(textScale(), detailWidth / (float) Math.max(1, font.width(runtimeLabel)));
        g.pose().pushPose(); g.pose().translate(detailX, imageHeight - 31, 0); g.pose().scale(runtimeScale, runtimeScale, 1);
        g.drawString(font, runtimeLabel, 0, 0, running ? 0x7BE3AA : 0xBDA5D0, false); g.pose().popPose();
    }
    @Override public void removed() {
        if(selected()!=null)DashboardPageMemory.remember(selected,parameters.stream().map(ModuleFields.Field::key).toList(),parameterPage,parameterSlots);
        super.removed();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sync(); renderBackground(graphics, mouseX, mouseY, partialTick); super.render(graphics, mouseX, mouseY, partialTick);
    }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.<HeartDashboardMenu, HeartDashboardScreen>register(SableLife.DASHBOARD_MENU.get(), HeartDashboardScreen::new); }
}
