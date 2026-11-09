package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import io.netty.buffer.Unpooled;
import java.util.*;

@GameTestHolder("sablelife_pattern_visual_tests") @PrefixGameTestTemplate(false)
public final class PatternVisualGameTests {
    private static HeartBlockEntity core(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,5,3));
        level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static VirtualModule effect(HeartBlockEntity core){
        var module=core.modules().add("visual_shockwave");module.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));
        module.edit("shockShakeEnabled",0,"");module.edit("shockZoomEnabled",0,"");module.edit("shockDistortion",0,"");module.edit("shockBlur",0,"");
        return module;
    }
    @GameTest(template="combat")
    public static void PatternDefaultsVisibilityAndSaveAreBackwardCompatible(GameTestHelper h){
        var m=new VirtualModule("visual_shockwave");
        h.assertTrue(m.value("shockPattern")==0&&!SurfaceFieldSettings.visible(m,"visualSize"),"Old default became a world pattern");
        for(int pattern=2;pattern<=8;pattern++){
            h.assertTrue(m.edit("shockPattern",pattern,""),"New choice rejected: "+pattern);
            h.assertTrue(SurfaceFieldSettings.visible(m,"visualColor")&&SurfaceFieldSettings.visible(m,"visualTime")
                    &&!SurfaceFieldSettings.visible(m,"shockRadius")&&!SurfaceFieldSettings.visible(m,"shockRadialTime"),"Wrong pattern controls visible");
            h.assertTrue(SurfaceFieldSettings.visible(m,"visualSymbol")== (pattern==8),"Symbol selector shown for another pattern");
            m.edit("visualSize",3.5,"");m.edit("visualTime",73,"");m.edit("visualSpeed",2.5,"");m.edit("visualPlacement",1,"");
            m.edit("visualColorRgb",1,"");m.edit("visualColorRed",210,"");
            for(var loaded:List.of(VirtualModule.load(m.save()),ModulePreset.create(m.save())))
                h.assertTrue(loaded.value("shockPattern")==pattern&&loaded.value("visualTime")==73&&loaded.value("visualSize")==3.5
                        &&loaded.value("visualColorRed")==210&&loaded.value("visualPlacement")==1,"Saved pattern settings changed");
        }
        m.edit("shockContact",1,"");h.assertTrue(!SurfaceFieldSettings.visible(m,"visualPlacement")&&!SurfaceFieldSettings.visible(m,"visualStart"),"Contact exposed ignored settings");
        m.edit("shockWaveEnabled",0,"");h.assertTrue(!SurfaceFieldSettings.visible(m,"visualColor")&&!SurfaceFieldSettings.visible(m,"visualSymbol"),"Disabled visuals exposed settings");
        m.edit("shockPattern",9,"");m.edit("visualSymbol",6,"");m.edit("visualTime",1201,"");
        h.assertTrue(m.value("shockPattern")==8&&m.value("visualSymbol")==5&&m.value("visualTime")==1200
                &&!m.edit("visualSize",Double.NaN,""),"Field limits were not enforced");
        h.succeed();
    }
    @GameTest(template="combat")
    public static void EveryPatternHasAValidatedWireRoundTripAndStableContactPlacement(GameTestHelper h){
        var core=core(h);var m=effect(core);
        for(int pattern=2;pattern<=8;pattern++){
            m.edit("shockPattern",pattern,"");m.edit("visualSymbol",pattern%6,"");
            var p=PatternVisual.create(core,m,null);
            h.assertTrue(p.valid()&&p.pattern()==pattern-2&&p.follow()&&Math.abs(p.normal().dot(p.up()))<1E-6,"Invalid emission frame");
            var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try{PatternVisual.CODEC.encode(buf,p);h.assertTrue(p.equals(PatternVisual.CODEC.decode(buf))&&buf.readableBytes()==0,"Pattern codec mismatch");}
            finally{buf.release();}
            var contact=new Vec3(8,10,12);var fixed=PatternVisual.create(core,m,contact);
            h.assertTrue(!fixed.follow()&&fixed.point().distanceTo(contact)<.03,"Contact followed a moving part");
            var screen=ModuleShockwave.packet(core,m);
            h.assertTrue(screen.valid()&&screen.pattern()==0&&screen.distortion()==0&&screen.blur()==0,"World pattern became unwanted screen distortion");
        }
        h.succeed();
    }
    @GameTest(template="combat",batch="pattern_broadcast")
    public static void WorldOnlyPatternsEmitThroughRealTriggersAndPreview(GameTestHelper h){
        var core=core(h);var m=effect(core);m.edit("shockPattern",8,"");m.edit("visualSymbol",1,"");
        var player=TestActors.creative(h.getLevel());player.setPos(ModuleArena.corePosition(core));
        var sent=new ArrayList<PatternVisual>();
        player.connection=new ServerGamePacketListenerImpl(h.getLevel().getServer(),new Connection(PacketFlow.SERVERBOUND),player,CommonListenerCookie.createInitial(player.getGameProfile(),false)){
            @Override public void send(Packet<?> packet){if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload() instanceof PatternVisual visual)sent.add(visual);}
        };
        h.getLevel().players().add(player);
        try{
            h.assertTrue(ModuleShockwave.preview(player,core,m)&&sent.size()==1&&sent.getFirst().symbol()==1,"Pattern preview did not emit");
            core.tryActivate(player);h.assertTrue(core.health().state()==HeartState.ALIVE,"Fixture did not activate");
            core.modules().tick(core);h.assertTrue(sent.size()==2,"World-only trigger emitted no visual");
            core.modules().tick(core);h.assertTrue(sent.size()==2,"Same-tick trigger duplicated the visual");
            m.edit("enabled",0,"");core.modules().tick(core);h.assertTrue(sent.size()==2,"Disabled module emitted");
        }finally{h.getLevel().players().remove(player);}
        h.succeed();
    }
    @GameTest(template="combat")
    public static void GeometryIsFiniteBoundedAnimatedAndExpires(GameTestHelper h){
        for(int pattern=0;pattern<6;pattern++)for(double size:new double[]{.1,2,32})for(double speed:new double[]{.1,1,8}){
            var lines=PatternGeometry.strokes(pattern,35,60,speed,size);
            h.assertTrue(!lines.isEmpty()&&lines.size()<=512,"Empty/unbounded geometry: "+pattern+" "+speed);
            for(var line:lines)h.assertTrue(Double.isFinite(line.a().lengthSqr())&&Double.isFinite(line.b().lengthSqr())
                    &&line.width()>0&&line.alpha()>0&&line.alpha()<=1,"Invalid geometry vertex");
            h.assertTrue(PatternGeometry.strokes(pattern,60,60,speed,size).isEmpty(),"Expired geometry remained");
            if(pattern!=4)h.assertTrue(!lines.equals(PatternGeometry.strokes(pattern,30.23,60,speed,size)),"Animation did not advance: "+pattern);
        }
        h.assertTrue(PatternGeometry.strokes(99,1,60,1,2).isEmpty()&&PatternGeometry.strokes(0,Double.NaN,60,1,2).isEmpty(),"Invalid geometry input accepted");
        h.succeed();
    }
    @GameTest(template="combat")
    public static void EmissionFrameFollowsEveryFaceIncludingPartRoll(GameTestHelper h){
        var c=core(h);var m=effect(c);m.edit("shockPattern",3,"");m.edit("visualStart",3,"");
        var body=m.anchors().getFirst().body(c);
        body.logicalPose().position().add(4,2,3);
        body.logicalPose().orientation().rotateXYZ(.4,.7,1.1);
        for(var face:Direction.values()){
            var a=ModuleAnchor.create(c,c.getBlockPos().east(),face);m.anchors(List.of(a));
            var p=PatternVisual.create(c,m,null);
            var local=ModuleHealthBar.localPoint(a,body,3);
            var n=Vec3.atLowerCornerOf(face.getNormal());
            var r=n.cross(Math.abs(n.y)>.9?new Vec3(0,0,1):new Vec3(0,1,0)).normalize();
            var expected=body.logicalPose().transformPosition(local);
            var up=body.logicalPose().transformPosition(local.add(n.cross(r))).subtract(expected).normalize();
            h.assertTrue(p.valid()&&p.point().distanceTo(expected)<1E-7&&p.up().distanceTo(up)<1E-7,"Rolled face lost emission frame: "+face);
        }
        h.succeed();
    }
    @GameTest(template="combat")
    public static void MalformedVisualPacketsAreRejected(GameTestHelper h){
        var c=core(h);var m=effect(c);m.edit("shockPattern",2,"");var p=PatternVisual.create(c,m,null);
        h.assertTrue(!new PatternVisual(p.dimension(),p.id(),p.anchor(),p.point(),p.normal(),p.normal(),p.startDistance(),p.size(),p.speed(),p.duration(),p.rgb(),p.pattern(),p.symbol(),p.follow(),p.range()).valid(),"Parallel orientation axes accepted");
        h.assertTrue(!new PatternVisual(p.dimension(),p.id(),p.anchor(),p.point(),p.normal(),p.up(),p.startDistance(),Float.NaN,p.speed(),p.duration(),p.rgb(),p.pattern(),p.symbol(),p.follow(),p.range()).valid(),"NaN size accepted");
        h.assertTrue(!new PatternVisual(p.dimension(),p.id(),p.anchor(),p.point(),p.normal(),p.up(),p.startDistance(),p.size(),p.speed(),1201,p.rgb(),p.pattern(),p.symbol(),p.follow(),p.range()).valid(),"Unbounded lifetime accepted");
        h.assertTrue(!new PatternVisual(p.dimension(),p.id(),p.anchor(),p.point(),p.normal(),p.up(),p.startDistance(),p.size(),p.speed(),p.duration(),p.rgb(),7,p.symbol(),p.follow(),p.range()).valid(),"Unknown pattern accepted");
        h.succeed();
    }
}
