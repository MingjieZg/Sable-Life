package dev.sablelife.testing;
import dev.sablelife.client.VisualFilterState;
import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.*;
import io.netty.buffer.Unpooled;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class VisualFilterGameTests {
 private static VisualFilterPacket on(UUID id,int color,float strength,int in,int out){return new VisualFilterPacket(ResourceLocation.withDefaultNamespace("overworld"),id,color,strength,in,out,true);}
 private static boolean close(float a,float b){return Math.abs(a-b)<.0001;}
 @GameTest(template="combat",batch="filter_state") public static void HeartbeatsHoldUntilReleaseAndReentryContinuesFromCurrentTint(GameTestHelper h){
  var state=new VisualFilterState();var p=on(UUID.randomUUID(),0xAA33FF,.4F,20,20);state.accept(p,0);
  h.assertTrue(close(state.tint(10).alpha(),.2F),"Fade-in midpoint incorrect");
  for(int tick=10;tick<=200;tick+=10){state.accept(p,tick);h.assertTrue(close(state.tint(tick).alpha(),tick==10?.2F:.4F),"Heartbeat restarted fade at "+tick);}
  state.accept(p.released(),200);h.assertTrue(close(state.tint(200).alpha(),.4F)&&close(state.tint(210).alpha(),.2F),"Release snapped or ignored fade duration");
  state.accept(p,210);h.assertTrue(close(state.tint(210).alpha(),.2F)&&close(state.tint(220).alpha(),.3F),"Reentry did not blend from current alpha");
  h.assertTrue(close(state.tint(280).alpha(),.2F)&&state.tint(290).alpha()==0,"Missing sender did not expire and clean up");state.accept(p,300);state.clear();h.assertTrue(state.tint(301).alpha()==0,"World cleanup retained filter");h.succeed();
 }
 @GameTest(template="combat",batch="filter_blend") public static void MultipleSourcesBlendIndependentlyAndZeroDurationIsInstant(GameTestHelper h){
  var a=on(UUID.randomUUID(),0xFF0000,.5F,0,0);var b=on(UUID.randomUUID(),0x0000FF,.5F,0,0);var s=new VisualFilterState();var reversed=new VisualFilterState();s.accept(a,0);s.accept(b,0);reversed.accept(b,0);reversed.accept(a,0);
  var tint=s.tint(0);h.assertTrue(tint.equals(reversed.tint(0))&&close(tint.alpha(),.75F)&&close(tint.red(),.5F)&&close(tint.blue(),.5F),"Blend depends on receipt order");s.accept(a.released(),1);h.assertTrue(close(s.tint(1).alpha(),.5F)&&s.tint(1).blue()==1,"One source release erased another");s.accept(b.released(),2);h.assertTrue(s.tint(2).alpha()==0,"Instant release failed");h.succeed();
 }
 @GameTest(template="combat",batch="filter_bounds") public static void PacketBoundsCodecAndOwnershipCapRejectInvalidInput(GameTestHelper h){
  var p=on(UUID.randomUUID(),0x123456,.25F,4,8);var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{VisualFilterPacket.CODEC.encode(buf,p);h.assertTrue(VisualFilterPacket.CODEC.decode(buf).equals(p),"Filter packet lost fields");}finally{buf.release();}
  var state=new VisualFilterState();for(var bad:List.of(on(UUID.randomUUID(),-1,.2F,1,1),on(UUID.randomUUID(),0,Float.NaN,1,1),on(UUID.randomUUID(),0,2,1,1),on(UUID.randomUUID(),0,.2F,201,1))){h.assertTrue(!bad.valid(),"Invalid filter accepted");state.accept(bad,0);}h.assertTrue(state.tint(0).alpha()==0,"Rejected filter changed scene");
  var oldest=on(UUID.randomUUID(),0xFF0000,1,0,0);state.accept(oldest,0);for(int i=0;i<VisualFilterState.LIMIT;i++)state.accept(on(UUID.randomUUID(),0x0000FF,.01F,0,0),0);h.assertTrue(state.tint(0).red()==0&&state.tint(0).blue()==1,"Ownership cap failed to evict oldest source");h.succeed();
 }
 @GameTest(template="combat",batch="filter_config") public static void OldDefaultsCustomColorSavePresetAndIndependentControls(GameTestHelper h){
  var m=new VirtualModule("visual_shockwave");h.assertTrue(m.value("filterEnabled")==0&&m.value("shockWaveEnabled")==1&&m.value("shockShakeEnabled")==1&&m.value("shockZoomEnabled")==1,"Old settings changed behavior");
  m.edit("filterEnabled",1,"");m.edit("filterColorRgb",1,"");m.edit("filterColorRed",18,"");m.edit("filterColorGreen",52,"");m.edit("filterColorBlue",86,"");m.edit("filterFadeIn",12,"");
  for(var copy:List.of(VirtualModule.load(m.save()),ModulePreset.create(m.save()))){var c=ModuleColors.vector(copy,"filterColor");h.assertTrue(copy.value("filterEnabled")==1&&copy.value("filterFadeIn")==12&&close(c.x,18/255F)&&close(c.z,86/255F),"Custom color or timings lost on save/preset");}h.succeed();
 }
}
