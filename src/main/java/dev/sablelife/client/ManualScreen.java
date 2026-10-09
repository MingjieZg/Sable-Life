package dev.sablelife.client;

import com.google.gson.JsonParser;
import dev.sablelife.module.ModuleFields;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;

/** Resource-backed, read-only reference. Never sends packets or edits a module. */
public final class ManualScreen extends Screen {
    private record Bar(String module, double start, double duration) {}
    private record Chapter(String id, String title, String module, Map<String,List<String>> sections, List<Bar> timeline) {}
    private static final List<String> TABS = List.of("usage", "settings", "example", "troubleshooting");
    private final Screen parent;
    private final String initial;
    private final Map<Integer,String> drafts = new HashMap<>();
    private List<Chapter> chapters = List.of();
    private final Map<String,String> english = new HashMap<>();
    private Chapter selected;
    private int revision, tab, listPage, scroll, contentHeight;
    private int listRowHeight;
    private float textScale(){return LifeUiStyle.scale();}
    private void changeDensity(int direction){LifeUiStyle.cycle(direction);scroll=0;rebuildWidgets();}
    private void drawText(GuiGraphics g,Object text,int tx,int ty,int color){g.pose().pushPose();g.pose().translate(tx,ty,0);g.pose().scale(textScale(),textScale(),1);if(text instanceof Component c)g.drawString(font,c,0,0,color,false);else if(text instanceof FormattedCharSequence line)g.drawString(font,line,0,0,color,false);else g.drawString(font,text.toString(),0,0,color,false);g.pose().popPose();}
    private String category = "all", query = "";
    private EditBox search;
    private int x, y, panelWidth, panelHeight, sidebar, bodyX, bodyY, bodyWidth, bodyHeight, listSlots;
    private boolean loaded;

    public ManualScreen(Screen parent, String chapter) {
        super(Component.translatable("manual.sablelife.title"));
        this.parent = parent; this.initial = chapter;
        // Preserve an unsaved focused draft when returning to a rebuilt settings screen.
        var children = parent.children();
        for (int i=0;i<children.size();i++) if (children.get(i) instanceof EditBox box && box.isFocused()) drafts.put(i,box.getValue());
    }
    private Component tr(String key, Object... args) { return Component.translatable("manual.sablelife."+key,args); }
    private Component localized(String key) { return I18n.exists(key) ? Component.translatable(key) : Component.literal(english.getOrDefault(key,key)); }
    private void load() {
        // Re-read on each opening so a resource-pack reload updates the manual too.
        try (var reader=minecraft.getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("sablelife","lang/en_us.json"))) {
            JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e->english.put(e.getKey(),e.getValue().getAsString()));
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read manual English baseline",e); }
        try (var reader=minecraft.getResourceManager().openAsReader(ResourceLocation.fromNamespaceAndPath("sablelife","manual/catalog.json"))) {
            var data=JsonParser.parseReader(reader).getAsJsonObject(); revision=data.get("revision").getAsInt();
            var list=new ArrayList<Chapter>();
            for(var value:data.getAsJsonArray("entries")) {
                var entry=value.getAsJsonObject(); var sections=new LinkedHashMap<String,List<String>>();
                for(var section:entry.getAsJsonObject("sections").entrySet()) {
                    var keys=new ArrayList<String>(); section.getValue().getAsJsonArray().forEach(k->keys.add(k.getAsString())); sections.put(section.getKey(),List.copyOf(keys));
                }
                var bars=new ArrayList<Bar>();
                if(entry.has("timeline"))for(var item:entry.getAsJsonArray("timeline")){var bar=item.getAsJsonObject();bars.add(new Bar(bar.get("module").getAsString(),bar.get("start").getAsDouble(),bar.get("duration").getAsDouble()));}
                list.add(new Chapter(entry.get("id").getAsString(),entry.get("title").getAsString(),entry.has("module")?entry.get("module").getAsString():"",sections,List.copyOf(bars)));
            }
            chapters=List.copyOf(list); selected=chapters.stream().filter(c->c.id.equals(initial)).findFirst().orElse(chapters.getFirst()); loaded=true;
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot read manual catalog",e); }
    }
    private boolean validParent() {
        return !(parent instanceof AbstractContainerScreen<?> container) || minecraft.player!=null && minecraft.player.containerMenu==container.getMenu();
    }
    @Override public void tick() { if(!validParent())minecraft.setScreen(null); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        if(!validParent()){minecraft.setScreen(null);return;}
        minecraft.setScreen(parent);
        drafts.forEach((i,value)->{if(i<parent.children().size() && parent.children().get(i) instanceof EditBox box){box.setValue(value);parent.setFocused(box);box.setFocused(true);}});
    }
    private net.minecraft.client.gui.components.Button button(Component label,int bx,int by,int w,Runnable action) {
        return addRenderableWidget(LifeUiStyle.button(label,bx,by,w,18,action));
    }
    private List<Chapter> matches() {
        String needle=query.strip().toLowerCase(Locale.ROOT);
        return chapters.stream().filter(c->category.equals("all") || category.equals("guides") && c.module.isEmpty() || !c.module.isEmpty() && ModuleCategory.of(c.module).key.equals(category))
            .filter(c->(c.id+" "+localized(c.title).getString()+" "+english.getOrDefault(c.title,"")).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    @Override protected void init() {
        if(!loaded)load();
        chapterButtons.clear();
        panelWidth=Math.min(620,width-12);panelHeight=Math.min(370,height-12);x=(width-panelWidth)/2;y=(height-panelHeight)/2;
        sidebar=Math.min(165,Math.max(92,panelWidth/3));bodyX=x+sidebar+12;bodyY=y+88;bodyWidth=panelWidth-sidebar-24;bodyHeight=panelHeight-111;
        listRowHeight=Math.max(14,Math.round(21*textScale()));listSlots=Math.max(1,(panelHeight-87)/listRowHeight);listPage=Math.clamp(listPage,0,Math.max(0,(matches().size()-1)/listSlots));
        button(Component.translatable("language_picker.sablelife.button"),x+panelWidth-253,y+8,70,()->minecraft.setScreen(new LanguagePickerScreen(this))).setTooltip(Tooltip.create(Component.translatable("language_picker.sablelife.help")));
        var density=button(tr("density",Component.translatable("dashboard.sablelife.density_"+LifeUiStyle.density())),x+panelWidth-178,y+8,107,()->changeDensity(1));LifeUiStyle.reverse(density,()->changeDensity(-1));density.setTooltip(Tooltip.create(Component.translatable("dashboard.sablelife.density_help")));
        button(tr("back"),x+panelWidth-66,y+8,58,this::onClose);
        search=addRenderableWidget(new EditBox(font,x+8,y+34,sidebar-8,18,tr("search")));search.setMaxLength(80);search.setHint(tr("search"));search.setValue(query);
        search.setResponder(value->{query=value;listPage=0;refreshList();});
        String[] categories={"all","guides","signal","weapon","function"};int cw=bodyWidth/categories.length;
        for(int i=0;i<categories.length;i++) {
            String key=categories[i];var b=addRenderableWidget(LifeUiStyle.button(tr(key),bodyX+i*cw,y+34,cw-3,18,()->{category=key;listPage=0;rebuildWidgets();}));
            if(List.of("weapon","function","signal").contains(key))LifeUiStyle.bookmark(b,ModuleCategory.valueOf(key.toUpperCase(Locale.ROOT)).color);
            b.setTooltip(Tooltip.create(tr(key)));b.active=!category.equals(key);
        }
        int tw=bodyWidth/4;
        for(int i=0;i<TABS.size();i++){final int index=i;var b=addRenderableWidget(LifeUiStyle.button(tr(TABS.get(i)),bodyX+i*tw,y+58,tw-3,18,()->{tab=index;scroll=0;rebuildWidgets();}));b.active=tab!=i;b.setTooltip(Tooltip.create(tr(TABS.get(i))));}
        refreshList();
        button(Component.literal("‹"),x+8,y+panelHeight-24,24,()->{listPage=Math.max(0,listPage-1);rebuildWidgets();});
        button(Component.literal("›"),x+sidebar-24,y+panelHeight-24,24,()->{listPage=Math.min(Math.max(0,(matches().size()-1)/listSlots),listPage+1);rebuildWidgets();});
    }
    private final List<net.minecraft.client.gui.components.Button> chapterButtons=new ArrayList<>();
    private void refreshList() {
        chapterButtons.forEach(this::removeWidget);chapterButtons.clear();
        var visible=matches();
        for(int i=0;i<listSlots && listPage*listSlots+i<visible.size();i++) {
            var chapter=visible.get(listPage*listSlots+i);
            var b=addRenderableWidget(LifeUiStyle.button(localized(chapter.title),x+8,y+58+i*listRowHeight,sidebar-8,listRowHeight-2,()->{selected=chapter;tab=0;scroll=0;rebuildWidgets();}));
            LifeUiStyle.bookmark(b,chapter.module.isEmpty()?0xFFAEB8C7:ModuleCategory.of(chapter.module).color);b.active=chapter!=selected;
            b.setTooltip(Tooltip.create(localized(chapter.title).copy().append(" ["+chapter.id+"]")));chapterButtons.add(b);
        }
    }
    private String number(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    private List<Component> paragraphs() {
        var result=new ArrayList<Component>();if(selected==null)return result;
        selected.sections.getOrDefault(TABS.get(tab),List.of()).forEach(k->result.add(localized(k)));
        if(tab==1 && !selected.module.isEmpty()) {
            result.add(tr("defaults"));result.add(tr("schema_note"));
            var fields=ModuleFields.forType(selected.module);
            // Audio volume/range/pitch belong to the shared editor, not obsolete outer fields.
            var audio=fields.stream().filter(f->f.choices().contains("library")).map(ModuleFields.Field::key).toList();
            for(var f:fields) {
                if(dev.sablelife.module.ModuleColors.parent(selected.module,f.key())!=null)continue;
                if(audio.stream().anyMatch(k->f.key().equals(k+"Volume")||f.key().equals(k+"Radius")) || List.of("sound","trigger_sound").contains(selected.module)&&List.of("volume","pitch").contains(f.key()) || f.key().equals("bgmVolume"))continue;
                String key="dashboard.sablelife."+f.key();
                if(selected.module.equals("gun")&&f.key().equals("speed"))key="dashboard.sablelife.gun_speed";
                Component label=localized(key);
                if(!f.choice())result.add(tr("numeric",label,number(f.initial()/f.scale()),number(f.min()/f.scale()),number(f.max()/f.scale())));
                else if(f.choices().size()==1 || f.choices().contains("library"))result.add(tr("editor",label));
                else {var options=Component.empty();for(String option:f.choices()){if(!options.getString().isEmpty())options.append(" / ");options.append(localized("dashboard.sablelife.choice_"+option));}
                    result.add(tr("choice",label,localized("dashboard.sablelife.choice_"+f.choices().get((int)f.initial())),options));}
            }
        }
        return result;
    }
    private List<FormattedCharSequence> lines(){
        var result=new ArrayList<FormattedCharSequence>();
        for(var p:paragraphs()){
            for(String line:p.getString().split("\n",-1)){
                // Font wrapping may omit an empty line; explicit paragraph gaps must survive.
                if(line.isEmpty())result.add(FormattedCharSequence.EMPTY);
                else result.addAll(font.split(Component.literal(line),Math.max(24,(int)((bodyWidth-14)/textScale()))));
            }
            result.add(FormattedCharSequence.EMPTY);
        }
        return result;
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if(mx>=bodyX&&mx<=bodyX+bodyWidth&&my>=bodyY&&my<=bodyY+bodyHeight){scroll=Math.clamp(scroll-(int)(vertical*27),0,Math.max(0,contentHeight-bodyHeight));return true;}
        if(mx>=x&&mx<bodyX){listPage=Math.clamp(listPage-(int)Math.signum(vertical),0,Math.max(0,(matches().size()-1)/listSlots));rebuildWidgets();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(key==266||key==267||key==268||key==269){scroll=key==268?0:key==269?Math.max(0,contentHeight-bodyHeight):Math.clamp(scroll+(key==267?1:-1)*Math.max(20,bodyHeight-18),0,Math.max(0,contentHeight-bodyHeight));return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial) {
        // Screen.render calls this before widgets; blur only the scene behind the panel.
        super.renderBackground(g,mx,my,partial);g.fill(x-2,y-2,x+panelWidth+2,y+panelHeight+2,0xFF9B614E);g.fill(x,y,x+panelWidth,y+panelHeight,0xFF242632);
        drawText(g,font.plainSubstrByWidth(title.getString(),(int)((panelWidth-269)/textScale())),x+8,y+12,0xFFE6D8EA);
        if(selected!=null){var name=font.plainSubstrByWidth(localized(selected.title).getString(),bodyWidth);drawText(g,name,bodyX,y+79,0xFFFFE4AA);}
        var lines=lines();int pitch=Math.max(7,Math.round((font.lineHeight+3)*textScale()));boolean timeline=tab==2&&selected!=null&&!selected.timeline.isEmpty();int chartHeight=timeline?selected.timeline.size()*26+30:0;
        contentHeight=lines.size()*pitch+chartHeight;scroll=Math.clamp(scroll,0,Math.max(0,contentHeight-bodyHeight));
        g.enableScissor(bodyX,bodyY,bodyX+bodyWidth,bodyY+bodyHeight);
        for(int i=0;i<lines.size();i++)drawText(g,lines.get(i),bodyX,bodyY+i*pitch-scroll,0xFFE1D5E8);
        if(timeline) {
            int cy=bodyY+lines.size()*pitch-scroll;g.drawString(font,tr("timeline"),bodyX,cy,0xFFFFE4AA,false);
            double end=selected.timeline.stream().mapToDouble(b->b.start+b.duration).max().orElse(1);int laneWidth=bodyWidth-18;
            for(int i=0;i<selected.timeline.size();i++){var bar=selected.timeline.get(i);int by=cy+15+i*26;g.drawString(font,font.plainSubstrByWidth(Component.translatable("dashboard.sablelife.type_"+bar.module).getString()+"  "+number(bar.start)+"–"+number(bar.start+bar.duration)+"s",bodyWidth-12),bodyX,by,0xFFE1D5E8,false);g.fill(bodyX,by+11,bodyX+laneWidth,by+17,0xFF171821);g.fill(bodyX+(int)(bar.start/end*laneWidth),by+11,bodyX+(int)((bar.start+bar.duration)/end*laneWidth),by+17,ModuleCategory.of(bar.module).color);}
        }
        g.disableScissor();
        if(contentHeight>bodyHeight){int thumb=Math.max(12,bodyHeight*bodyHeight/contentHeight);int ty=bodyY+(bodyHeight-thumb)*scroll/Math.max(1,contentHeight-bodyHeight);g.fill(bodyX+bodyWidth-4,bodyY,bodyX+bodyWidth-2,bodyY+bodyHeight,0xFF44404D);g.fill(bodyX+bodyWidth-4,ty,bodyX+bodyWidth-2,ty+thumb,0xFFAD75DF);}
        if(matches().isEmpty())g.drawWordWrap(font,tr("empty"),x+8,y+60,sidebar-8,0xFFAEB8C7);
        g.drawString(font,Component.literal((listPage+1)+" / "+Math.max(1,(matches().size()+listSlots-1)/listSlots)),x+39,y+panelHeight-19,0xFFAEB8C7,false);
        g.drawString(font,tr("revision",revision),bodyX,y+panelHeight-18,0xFFAEB8C7,false);
    }
}
