package dev.sablelife.module;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.SimpleContainer;
import java.util.UUID;
/** Container ownership, module identity, revision and permission are checked before every mutation. */
public final class HealthPhaseRuleMenu extends AbstractContainerMenu {
 private final HeartBlockEntity heart;private final VirtualModule module;private final Player viewer;private final SimpleContainer data=new SimpleContainer(1);private final UUID selected;
 public HealthPhaseRuleMenu(int id,Inventory inv){this(id,inv,null,null,null);}
 public HealthPhaseRuleMenu(int id,Inventory inv,HeartBlockEntity heart,VirtualModule module,UUID selected){super(SableLife.HEALTH_PHASE_RULE_MENU.get(),id);this.heart=heart;this.module=module;viewer=inv.player;this.selected=selected;
  addSlot(new Slot(data,0,-10000,-10000){@Override public boolean isActive(){return false;}@Override public boolean mayPlace(ItemStack s){return false;}@Override public boolean mayPickup(Player p){return false;}});refresh();}
 public static void open(Player p,HeartBlockEntity heart,VirtualModule module,UUID selected){p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,ignored)->new HealthPhaseRuleMenu(id,inv,heart,module,selected),net.minecraft.network.chat.Component.translatable("phase_rules.sablelife.title")));}
 public CompoundTag snapshot(){return data.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
 private void refresh(){if(heart==null)return;var tag=new CompoundTag();tag.putBoolean("Ready",true);tag.putBoolean("Editable",heart.canEdit(viewer));tag.putUUID("ModuleId",module.id());tag.putInt("Revision",heart.modules().revision());tag.put("Groups",module.healthPhaseRules().save());tag.putInt("Channels",heart.wireless().size());tag.put("Runtime",heart.modules().phaseRuntime(module.id()));if(selected!=null)tag.putUUID("Selected",selected);var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));data.setItem(0,paper);}
 @Override public void broadcastChanges(){if(heart!=null&&heart.getLevel().getGameTime()%5==0)refresh();super.broadcastChanges();}
 @Override public boolean stillValid(Player p){return heart==null||heart.canAccessEditor(p)&&!heart.isRemoved()&&p.level()==heart.getLevel()&&heart.getLevel().getBlockEntity(heart.getBlockPos())==heart&&heart.modules().find(module.id())==module&&module.type().equals("health_phase")&&SableBodyProbe.toWorld(p.level(),p.getEyePosition()).distanceToSqr(SableBodyProbe.toWorld(p.level(),heart.getBlockPos().getCenter()))<=64;}
 public boolean action(Player p,int revision,UUID id,String key,double value){
  if(heart==null||!stillValid(p))return false;var group=module.healthPhaseRules().find(id);
  if(!heart.canEdit(p)||revision!=heart.modules().revision()||!Double.isFinite(value)){refresh();sendAllDataToRemote();return false;}
  boolean accepted=switch(key){case "add"->module.healthPhaseRules().add()!=null;case "delete"->module.healthPhaseRules().remove(id);case "up","down"->module.healthPhaseRules().move(id,key.equals("up")?-1:1);default->group!=null&&group.edit(key,value);};
  if(accepted){heart.modules().changed();heart.setChanged();}refresh();sendAllDataToRemote();return accepted;
 }
 @Override public boolean clickMenuButton(Player p,int n){if(n!=0||heart==null||!stillValid(p))return false;DashboardSelection.set(heart,p.getUUID(),module.id());heart.openDashboard(p);return true;}
 @Override public void clicked(int slot,int button,ClickType type,Player p){}
 @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
}
