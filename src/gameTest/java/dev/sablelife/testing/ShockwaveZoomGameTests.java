package dev.sablelife.testing;
import dev.sablelife.client.ShockwaveZoomState;
import dev.sablelife.module.ShockwaveZoomPacket;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.*;
import io.netty.buffer.Unpooled;
import java.util.UUID;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class ShockwaveZoomGameTests {
 private static ShockwaveZoomPacket packet(UUID id,float zoom,int recovery,boolean active){return new ShockwaveZoomPacket(ResourceLocation.withDefaultNamespace("overworld"),id,Vec3.ZERO,zoom,recovery,64,active);}
 @GameTest(template="combat",batch="shock_zoom_hold")
 public static void RepeatHoldNeverRecoversUntilExplicitSignalEnd(GameTestHelper h){
  for(float value:new float[]{20,-80}){var model=new ShockwaveZoomState();var id=UUID.randomUUID();var on=packet(id,value,20,true);model.accept(on,0);
   for(int tick=0;tick<=200;tick++){if(tick%10==0)model.accept(on,tick);double zoom=model.zoom(tick,Vec3.ZERO);if(tick>=3)h.assertTrue(Math.abs(zoom-value)<1e-8,"Repeat refresh restarted recovery or stacked zoom at "+tick+": "+zoom);}
   model.accept(on.released(),200);h.assertTrue(model.zoom(200,Vec3.ZERO)==value,"Signal end snapped zoom");
   h.assertTrue(Math.abs(model.zoom(210,Vec3.ZERO)-value*.5)<1e-8&&model.zoom(220,Vec3.ZERO)==0,"Recovery did not begin at signal end or obey its duration");
  }h.succeed();
 }
 @GameTest(template="combat",batch="shock_zoom_cleanup")
 public static void RecoveryCanRetriggerAndMissingSenderExpiresWithoutStuckFov(GameTestHelper h){
  var model=new ShockwaveZoomState();var id=UUID.randomUUID();var on=packet(id,20,20,true);model.accept(on,0);model.accept(on.released(),10);h.assertTrue(model.zoom(20,Vec3.ZERO)==10,"Recovery fixture failed");model.accept(on,20);h.assertTrue(model.zoom(20,Vec3.ZERO)==10&&model.zoom(23,Vec3.ZERO)==20,"New signal snapped instead of blending from current recovery");
  h.assertTrue(model.zoom(79,Vec3.ZERO)==20&&model.zoom(90,Vec3.ZERO)==10&&model.zoom(100,Vec3.ZERO)==0,"Unloaded/missing source left zoom stuck");model.accept(on,110);h.assertTrue(model.zoom(115,new Vec3(64,0,0))==0,"Range attenuation was lost");model.clear();h.assertTrue(model.zoom(115,Vec3.ZERO)==0,"World cleanup left held zoom");
  var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{ShockwaveZoomPacket.CODEC.encode(buf,on);h.assertTrue(ShockwaveZoomPacket.CODEC.decode(buf).equals(on),"Zoom state lost on wire");}finally{buf.release();}h.succeed();
 }
}
