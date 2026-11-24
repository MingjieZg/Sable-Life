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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkChannel;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;

@GameTestHolder("sablelife_pattern_visual_tests") @PrefixGameTestTemplate(false)
public final class PatternVisualGameTests {
    private static final String SHIELD_SIGNAL = "deployable_shield_test";

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
        m.edit("shockPattern",10,"");m.edit("visualSymbol",6,"");m.edit("visualTime",1201,"");
        h.assertTrue(m.value("shockPattern")==9&&m.value("visualSymbol")==5&&m.value("visualTime")==1200
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
        h.assertTrue(!new PatternVisual(p.dimension(),p.id(),p.anchor(),p.point(),p.normal(),p.up(),p.startDistance(),p.size(),p.speed(),p.duration(),p.rgb(),8,p.symbol(),p.follow(),p.range()).valid(),"Unknown pattern accepted");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "deployable_shield_data")
    public static void ShieldChoiceAppendsWithoutChangingOldSettings(GameTestHelper h) {
        var choices = ModuleFields.forType("visual_shockwave").stream()
                .filter(field -> field.key().equals("shockPattern")).findFirst().orElseThrow().choices();
        h.assertTrue(choices.equals(List.of("shock_ring", "shock_radial", "visual_gather", "visual_mechanical",
                "visual_shield", "visual_jet", "visual_fissures", "visual_echo", "visual_symbols", "visual_deployable_shield"))
                && ShieldVisualPacket.MODULE_PATTERN == 9 && ShieldVisualPacket.GEOMETRY_PATTERN == 7
                && PatternVisual.PATTERNS == 8, "A legacy saved choice or wire index moved");

        var module = new VirtualModule("visual_shockwave");
        module.edit("shockPattern", 4, "");
        module.edit("shockContact", 1, "");
        module.edit("shockMode", 1, "");
        var migrated = VirtualModule.load(module.save());
        h.assertTrue(migrated.value("shockPattern") == 4 && migrated.value("shockContact") == 1
                && migrated.value("shockMode") == 1, "Legacy Shield Fracture settings changed");

        h.assertTrue(migrated.edit("shockPattern", 9, ""), "Deployable Shield choice was rejected");
        for (var field : List.of("shockContact", "contactDirection", "contactDistance", "contactCooldown", "shockMode", "shockInterval")) {
            h.assertTrue(!SurfaceFieldSettings.visible(migrated, field), "Deployable Shield exposed " + field);
        }
        h.assertTrue(SurfaceFieldSettings.visible(migrated, "visualPlacement") && SurfaceFieldSettings.visible(migrated, "visualStart"),
                "A retained Contact setting hid the shield Placement fields");
        for (var restored : List.of(VirtualModule.load(migrated.save()), ModulePreset.create(migrated.save()))) {
            h.assertTrue(restored.value("shockPattern") == 9
                    && restored.value("shockContact") == 1 && restored.value("shockMode") == 1,
                    "Saving a Deployable Shield lost its mode or legacy hidden settings");
        }
        migrated.edit("shockPattern", 4, "");
        h.assertTrue(SurfaceFieldSettings.visible(migrated, "shockContact"),
                "Switching back to Shield Fracture left its controls hidden");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "deployable_shield_wire")
    public static void ShieldPacketRoundTripsAndRejectsMalformedPayloads(GameTestHelper h) {
        var core = core(h);
        var module = effect(core);
        module.edit("shockPattern", 9, "");
        var visual = PatternVisual.create(core, module, null);
        h.assertTrue(visual.pattern() == 7 && visual.valid() && ShieldVisualPacket.LEASE_TICKS == 35, "Wrong new effect index or lease");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            for (var phase : List.of(ShieldVisualPacket.Phase.HOLD, ShieldVisualPacket.Phase.CLOSE,
                    ShieldVisualPacket.Phase.PREVIEW, ShieldVisualPacket.Phase.SYNC)) {
                buffer.clear();
                var packet = new ShieldVisualPacket(visual, phase);
                ShieldVisualPacket.CODEC.encode(buffer, packet);
                var restored = ShieldVisualPacket.CODEC.decode(buffer);
                h.assertTrue(restored.valid() && packet.equals(restored) && !buffer.isReadable(), "Broken shield codec for " + phase);
            }
            var malformed = new PatternVisual(visual.dimension(), visual.id(), visual.anchor(), visual.point(), visual.normal(),
                    visual.normal(), visual.startDistance(), Float.NaN, visual.speed(), 1201, visual.rgb(), 7, visual.symbol(),
                    visual.follow(), visual.range());
            buffer.clear();
            ShieldVisualPacket.CODEC.encode(buffer, new ShieldVisualPacket(malformed, ShieldVisualPacket.Phase.HOLD));
            h.assertTrue(!ShieldVisualPacket.CODEC.decode(buffer).valid(), "Malformed shield passed validation after wire decode");

            module.edit("shockPattern", 4, "");
            var legacy = PatternVisual.create(core, module, null);
            h.assertTrue(legacy.valid() && !new ShieldVisualPacket(legacy, ShieldVisualPacket.Phase.HOLD).valid(),
                    "A legacy pattern was accepted as a held shield");
            buffer.clear();
            PatternVisual.CODEC.encode(buffer, visual);
            buffer.writeVarInt(ShieldVisualPacket.Phase.values().length);
            boolean rejected = false;
            try { ShieldVisualPacket.CODEC.decode(buffer); }
            catch (IndexOutOfBoundsException | io.netty.handler.codec.DecoderException expected) { rejected = true; }
            h.assertTrue(rejected, "Unknown shield phase decoded successfully");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    @GameTest(template = "combat", batch = "deployable_shield_lifetime", timeoutTicks = 65)
    public static void ShieldUsesItsTriggerAndNegotiatedPersistentVisuals(GameTestHelper h) {
        var core = core(h);
        var module = effect(core);
        module.edit("shockPattern", 9, "");
        module.edit("visualTime", 2, "");
        module.edit("shockContact", 1, "");
        module.edit("shockMode", 1, "");
        int output = core.wireless().add();
        core.wireless().channel(output).set(0, 1);
        core.wireless().channel(output).set(6, 3);
        module.edit("trigger", 2, "");
        module.edit("channel", output + 1, "");
        var viewer = ShieldObserver.create(h, core, true);
        var late = ShieldObserver.create(h, core, true);
        var legacy = ShieldObserver.create(h, core, false);
        late.player.setPos(ModuleArena.corePosition(core).add(200, 0, 0));
        h.assertTrue(ModuleShockwave.preview(viewer.player, core, module) && viewer.count(ShieldVisualPacket.Phase.PREVIEW) == 1
                && viewer.count(ShieldVisualPacket.Phase.HOLD) == 0, "Editor preview opened a persistent runtime shield");
        ModuleShockwave.preview(legacy.player, core, module);
        core.tryActivate(viewer.player);
        h.assertTrue(core.operational(), "Shield fixture did not activate");
        h.runAfterDelay(4, () -> {
            h.assertTrue(viewer.count(ShieldVisualPacket.Phase.HOLD) == 0, "An unpowered shield opened");
            core.wireless().programOutputs(SHIELD_SIGNAL, Map.of(output, 15));
        });
        h.runAfterDelay(8, () -> {
            h.assertTrue(viewer.count(ShieldVisualPacket.Phase.HOLD) == 1 && late.packets.isEmpty(), "Shield failed to open or ignored range");
            h.assertTrue(ModuleArmor.damage(core, core.getBlockPos(), 20) == 20, "The shield effect changed damage");
        });
        h.runAfterDelay(15, () -> late.player.setPos(ModuleArena.corePosition(core)));
        h.runAfterDelay(25, () -> {
            h.assertTrue(viewer.count(ShieldVisualPacket.Phase.HOLD) >= 2 && late.count(ShieldVisualPacket.Phase.SYNC) == 1,
                    "A short Preview duration expired the held shield or a late player missed Sync");
            var id = viewer.first(ShieldVisualPacket.Phase.HOLD).visual().id();
            h.assertTrue(late.first(ShieldVisualPacket.Phase.SYNC).visual().id().equals(id), "Late Sync changed the effect identity");
            core.wireless().programOutputs(SHIELD_SIGNAL, Map.of());
        });
        h.runAfterDelay(28, () -> {
            h.assertTrue(viewer.count(ShieldVisualPacket.Phase.CLOSE) == 1 && late.count(ShieldVisualPacket.Phase.CLOSE) == 1,
                    "Trigger OFF failed to close the held shield");
            h.assertTrue(viewer.first(ShieldVisualPacket.Phase.CLOSE).visual().id().equals(viewer.first(ShieldVisualPacket.Phase.HOLD).visual().id()),
                    "Closing updated a different shield");
            core.wireless().programOutputs(SHIELD_SIGNAL, Map.of(output, 15));
        });
        h.runAfterDelay(32, () -> {
            var heldIds = viewer.packets.stream().filter(packet -> packet.phase() == ShieldVisualPacket.Phase.HOLD)
                    .map(packet -> packet.visual().id()).distinct().toList();
            h.assertTrue(viewer.packets.getLast().phase() == ShieldVisualPacket.Phase.HOLD && heldIds.size() == 1,
                    "A re-open replaced a live collapse with an unrelated effect");
            h.assertTrue(legacy.packets.isEmpty() && legacy.leaks == 0 && viewer.leaks == 0,
                    "Deployable Shield used an unnegotiated or legacy world payload");
            h.succeed();
        });
    }

    private static final class ShieldObserver implements GameTestListener, AutoCloseable {
        final List<ShieldVisualPacket> packets = new ArrayList<>();
        final ServerPlayer player;
        final EmbeddedChannel channel;
        int leaks;

        static ShieldObserver create(GameTestHelper test, HeartBlockEntity core, boolean modern) {
            var viewer = new ShieldObserver(test, core, modern);
            test.testInfo.addListener(viewer);
            test.assertTrue(viewer.player.connection.getConnection().isConnected()
                    && NetworkRegistry.hasChannel(viewer.player.connection, ShieldVisualPacket.TYPE.id()) == modern,
                    "The fixture did not negotiate the expected shield capability");
            return viewer;
        }

        private ShieldObserver(GameTestHelper test, HeartBlockEntity core, boolean modern) {
            var level = test.getLevel();
            player = TestActors.creative(level);
            player.setPos(ModuleArena.corePosition(core));
            var transport = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(transport);
            var legacy = PatternVisual.TYPE.id();
            var optional = ShieldVisualPacket.TYPE.id();
            var channels = new HashMap<>(Map.of(legacy, new NetworkChannel(legacy, "50")));
            if (modern) channels.put(optional, new NetworkChannel(optional, "50"));
            ChannelAttributes.setPayloadSetup(transport, new NetworkPayloadSetup(Map.of(ConnectionProtocol.PLAY, channels)));
            player.connection = new ServerGamePacketListenerImpl(level.getServer(), transport, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    if (!(packet instanceof ClientboundCustomPayloadPacket payload)) return;
                    if (payload.payload() instanceof ShieldVisualPacket shield) packets.add(shield);
                    if (payload.payload() instanceof PatternVisual visual && visual.pattern() == 7) leaks++;
                }
            };
            level.players().add(player);
        }

        long count(ShieldVisualPacket.Phase phase) {
            return packets.stream().filter(packet -> packet.phase() == phase).count();
        }

        ShieldVisualPacket first(ShieldVisualPacket.Phase phase) {
            return packets.stream().filter(packet -> packet.phase() == phase).findFirst().orElseThrow();
        }

        @Override public void testStructureLoaded(GameTestInfo info) {}
        @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo retry, GameTestRunner runner) { close(); }
        @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
        @Override public void close() {
            player.serverLevel().players().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
