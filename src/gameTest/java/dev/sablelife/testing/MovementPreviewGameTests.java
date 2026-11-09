package dev.sablelife.testing;

import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class MovementPreviewGameTests {
    @GameTest(template="combat")
    public static void PreviewPacketsRemainBoundedAndRoundTripAllDirectionColors(GameTestHelper h){
        var dimension=h.getLevel().dimension().location();
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            var ids=new ArrayList<UUID>();for(int i=0;i<64;i++)ids.add(UUID.randomUUID());
            var packet=new MovementPreview(dimension,ids,new Vec3(0,2,-128),false);ids.clear();
            MovementPreview.CODEC.encode(buffer,packet);h.assertTrue(buffer.readableBytes()<1200,"Preview contains excessive geometry data");
            h.assertTrue(MovementPreview.CODEC.decode(buffer).equals(packet)&&packet.bodies().size()==64,"Body references/offset lost in transport");
            for(var kind:MarkOutline.Kind.values()){
                var outline=new MarkOutline(dimension,kind,0xFFCC33,List.of(Vec3.ZERO,new Vec3(0,0,-3)));
                MarkOutline.CODEC.encode(buffer,outline);h.assertTrue(MarkOutline.CODEC.decode(buffer).equals(outline),"Colored direction did not round trip: "+kind);
            }
            buffer.clear();buffer.writeResourceLocation(dimension);buffer.writeVarInt(65);
            boolean rejected=false;try{MovementPreview.CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected,"Oversized movement preview accepted");
            rejected=false;try{new MovementPreview(dimension,List.of(UUID.randomUUID()),new Vec3(Double.NaN,0,0),true);}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected,"Invalid movement preview coordinates accepted");
        }finally{buffer.release();}h.succeed();
    }
    @GameTest(template="combat")
    public static void NewMovementDefaultsDoNotOverwriteExplicitSavedSettings(GameTestHelper h){
        var module=new VirtualModule("linear_movement");
        h.assertTrue(module.value("trigger")==2&&module.value("moveStepCooldown")==6,"New movement defaults are wrong");
        module.edit("trigger",0,"");module.edit("moveStepCooldown",12,"");
        var saved=VirtualModule.load(module.save());
        h.assertTrue(saved.value("trigger")==0&&saved.value("moveStepCooldown")==12,"New defaults replaced existing saved values");
        h.succeed();
    }
}
