package dev.sablelife.module;

import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

public final class ModuleNetworking {
    public record BlueprintPreview(ResourceLocation dimension,net.minecraft.world.phys.Vec3 min,net.minecraft.world.phys.Vec3 max,net.minecraft.world.phys.Vec3 anchor,net.minecraft.world.phys.Vec3 forward,int duration,UUID projection,org.joml.Quaternionf rotation) implements CustomPacketPayload {
        public BlueprintPreview(ResourceLocation dimension,net.minecraft.world.phys.Vec3 min,net.minecraft.world.phys.Vec3 max,
                                net.minecraft.world.phys.Vec3 anchor,net.minecraft.world.phys.Vec3 forward) {
            this(dimension,min,max,anchor,forward,12);
        }
        public BlueprintPreview(ResourceLocation dimension,net.minecraft.world.phys.Vec3 min,net.minecraft.world.phys.Vec3 max,
                                net.minecraft.world.phys.Vec3 anchor,net.minecraft.world.phys.Vec3 forward,int duration){
            this(dimension,min,max,anchor,forward,duration,null,null);
        }
        public static final Type<BlueprintPreview> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"blueprint_preview"));
        private static void point(RegistryFriendlyByteBuf b,net.minecraft.world.phys.Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
        private static net.minecraft.world.phys.Vec3 point(RegistryFriendlyByteBuf b){return new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
        public static final StreamCodec<RegistryFriendlyByteBuf,BlueprintPreview> CODEC=StreamCodec.of((b,p)->{
            b.writeResourceLocation(p.dimension);point(b,p.min);point(b,p.max);point(b,p.anchor);point(b,p.forward);b.writeVarInt(p.duration);
            b.writeBoolean(p.projection!=null);
            if(p.projection!=null){
                b.writeUUID(p.projection);
                var q=p.rotation;
                b.writeFloat(q.x);b.writeFloat(q.y);b.writeFloat(q.z);b.writeFloat(q.w);
            }
        },b->{
            var dimension=b.readResourceLocation();
            var min=point(b);var max=point(b);var anchor=point(b);var forward=point(b);
            int duration=b.readVarInt();
            return b.readBoolean()?new BlueprintPreview(dimension,min,max,anchor,forward,duration,b.readUUID(),
                    new org.joml.Quaternionf(b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat())):
                    new BlueprintPreview(dimension,min,max,anchor,forward,duration);
        });
        @Override public Type<BlueprintPreview> type(){return TYPE;}
    }
    public record Action(int container, int revision, UUID module, String operation, String key, double value, String text) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "module_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.container); buf.writeVarInt(p.revision); buf.writeUUID(p.module);
            buf.writeUtf(p.operation, 16); buf.writeUtf(p.key, 64); buf.writeDouble(p.value); buf.writeUtf(p.text, p.operation.equals("add_preset") ? ModulePreset.MAX_TEXT : p.operation.equals("commands") ? CommandProgram.MAX_TEXT+8 : p.operation.equals("sound_timeline") ? 256 : p.operation.equals("wild_encounter") && (p.key.equals("conditions") || p.key.endsWith("_rule")) ? 16000 : 80);
        }, buf -> { int container=buf.readVarInt(),revision=buf.readVarInt();var module=buf.readUUID();var operation=buf.readUtf(16);var key=buf.readUtf(64);double value=buf.readDouble();return new Action(container,revision,module,operation,key,value,buf.readUtf(operation.equals("add_preset")?ModulePreset.MAX_TEXT:operation.equals("commands")?CommandProgram.MAX_TEXT+8:operation.equals("sound_timeline")?256:operation.equals("wild_encounter")&&(key.equals("conditions")||key.endsWith("_rule"))?16000:80)); });
        @Override public Type<Action> type() { return TYPE; }
    }
    public record MarkState(boolean active) implements CustomPacketPayload {
        public static final Type<MarkState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "mark_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MarkState> CODEC = StreamCodec.of((b, p) -> b.writeBoolean(p.active), b -> new MarkState(b.readBoolean()));
        @Override public Type<MarkState> type() { return TYPE; }
    }
    public record ParticleVisual(String id, net.minecraft.world.phys.Vec3 position, int count, net.minecraft.world.phys.Vec3 spread,
                                 double speed, int ticks, float size,int color) implements CustomPacketPayload {
        public ParticleVisual(String id,net.minecraft.world.phys.Vec3 position,int count,net.minecraft.world.phys.Vec3 spread,double speed,int ticks,float size){this(id,position,count,spread,speed,ticks,size,-1);}
        public static final Type<ParticleVisual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "particle_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ParticleVisual> CODEC = StreamCodec.of((b,p) -> {
            b.writeUtf(p.id,80); b.writeDouble(p.position.x); b.writeDouble(p.position.y); b.writeDouble(p.position.z); b.writeVarInt(p.count);
            b.writeDouble(p.spread.x); b.writeDouble(p.spread.y); b.writeDouble(p.spread.z); b.writeDouble(p.speed); b.writeVarInt(p.ticks); b.writeFloat(p.size);b.writeInt(p.color);
        }, b -> new ParticleVisual(b.readUtf(80), new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()), b.readVarInt(),
                new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()), b.readDouble(),b.readVarInt(),b.readFloat(),b.readInt()));
        @Override public Type<ParticleVisual> type() { return TYPE; }
    }
    /** Describes births at the nozzle; existing particles advance locally, rather than re-drawing a trail. */
    public record FlameJet(net.minecraft.world.phys.Vec3 origin,net.minecraft.world.phys.Vec3 direction,double speed,double range,double width,int appearance,float size,long seed,java.util.List<ModuleParticles.Entry> particles,double fanAngle) implements CustomPacketPayload {
        public FlameJet { particles=java.util.List.copyOf(particles.stream().limit(ModuleParticles.LIMIT).toList()); }
        public FlameJet(net.minecraft.world.phys.Vec3 origin,net.minecraft.world.phys.Vec3 direction,double speed,double range,
                        double width,int appearance,float size,long seed,java.util.List<ModuleParticles.Entry> particles) {
            this(origin,direction,speed,range,width,appearance,size,seed,particles,0);
        }
        public FlameJet(net.minecraft.world.phys.Vec3 origin,net.minecraft.world.phys.Vec3 direction,double speed,double range,double width,int appearance,float size,long seed){this(origin,direction,speed,range,width,appearance,size,seed,java.util.List.of(new ModuleParticles.Entry(appearance==1?"minecraft:soul_fire_flame":"minecraft:flame",0,size)));}
        private static java.util.List<ModuleParticles.Entry> entries(RegistryFriendlyByteBuf b){var tag=b.readNbt();if(tag==null)return java.util.List.of();var ids=ModuleParticles.load(tag.getList("Ids",8));return ModuleParticles.loadEntries(tag.getList("Entries",10),ids);}
        public static final Type<FlameJet> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"flame_jet"));
        public static final StreamCodec<RegistryFriendlyByteBuf,FlameJet> CODEC=StreamCodec.of((b,p)->{
            b.writeDouble(p.origin.x);b.writeDouble(p.origin.y);b.writeDouble(p.origin.z);
            b.writeDouble(p.direction.x);b.writeDouble(p.direction.y);b.writeDouble(p.direction.z);
            b.writeDouble(p.speed);b.writeDouble(p.range);b.writeDouble(p.width);b.writeVarInt(p.appearance);b.writeFloat(p.size);b.writeLong(p.seed);var tag=new net.minecraft.nbt.CompoundTag();tag.put("Ids",ModuleParticles.save(p.particles.stream().map(ModuleParticles.Entry::id).toList()));tag.put("Entries",ModuleParticles.saveEntries(p.particles));b.writeNbt(tag);b.writeDouble(p.fanAngle);
        },b->new FlameJet(new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readFloat(),b.readLong(),entries(b),b.readDouble()));
        @Override public Type<FlameJet> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("48");
        registrar.playToClient(DatapackModuleCatalog.TYPE, DatapackModuleCatalog.CODEC,
                (packet, context) -> DatapackModules.receive(packet));
        registrar.playToClient(MovementPreview.TYPE,MovementPreview.CODEC,(packet,ctx)->dev.sablelife.client.MovementPreviewClient.receive(packet));
        registrar.playToClient(LaserVisual.TYPE,LaserVisual.CODEC,(packet,ctx)->dev.sablelife.client.LaserVisualClient.receive(packet));
        registrar.playToClient(BlueprintProjection.TYPE,BlueprintProjection.CODEC,(p,ctx)->dev.sablelife.client.BlueprintPreviewClient.receive(p));
        registrar.playToClient(MarkOutline.TYPE,MarkOutline.CODEC,(p,ctx)->dev.sablelife.client.BlueprintPreviewClient.receive(p));
        registrar.playToClient(BlueprintPreview.TYPE,BlueprintPreview.CODEC,(p,ctx)->dev.sablelife.client.BlueprintPreviewClient.receive(p));
        registrar.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> {
            if (p.operation.equals("cancel")) { MarkingSessions.cancel(ctx.player()); return; }
            if (!AuthoringRateLimit.allow(ctx.player(), p.operation.equals("add_preset") ? 20 : 1)) return;
            if (p.operation.equals("clear_mark")) { MarkingSessions.clearMarks(ctx.player()); return; }
            if (ctx.player().containerMenu instanceof HeartDashboardMenu menu && menu.containerId == p.container)
                menu.action(ctx.player(), p.revision, p.module, p.operation, p.key, p.value, p.text);
            else if (ctx.player().containerMenu instanceof dev.sablelife.heart.HeartWirelessMenu menu && menu.containerId == p.container && p.operation.equals("wireless_name"))
                menu.rename(ctx.player(), p.value, p.text);
            else if (ctx.player().containerMenu instanceof EffectSelectionMenu menu && menu.containerId == p.container && p.operation.equals("effect"))
                menu.action(ctx.player(), p.module, p.key, p.value, p.text);
            else if(ctx.player().containerMenu instanceof dev.sablelife.heart.HeartLootMenu menu&&menu.containerId==p.container&&p.operation.equals("loot_edit"))
                menu.action(ctx.player(),p.revision,p.module,p.key,p.value,p.text);
            else if(ctx.player().containerMenu instanceof VirtualLootMenu menu&&menu.containerId==p.container&&p.operation.equals("loot_edit"))
                menu.action(ctx.player(),p.revision,p.module,p.key,p.value,p.text);
            else if(ctx.player().containerMenu instanceof SignalRuleMenu menu&&menu.containerId==p.container&&p.operation.equals("signal_rule_edit"))
                menu.action(ctx.player(),p.revision,p.module,p.key,p.value);
            else if(ctx.player().containerMenu instanceof HealthPhaseRuleMenu menu&&menu.containerId==p.container&&p.operation.equals("phase_rule_edit"))
                menu.action(ctx.player(),p.revision,p.module,p.key,p.value);
            else if(ctx.player().containerMenu instanceof ArmorRuleMenu menu&&menu.containerId==p.container&&p.operation.equals("armor_rule_edit"))
                menu.action(ctx.player(),p.revision,p.module,p.key,p.value);
            else if(ctx.player().containerMenu instanceof ArenaBuffMenu menu&&menu.containerId==p.container&&p.operation.equals("arena_buff_edit"))
                menu.action(ctx.player(),p.revision,p.module,p.key,p.value);
            else if (ctx.player().containerMenu instanceof SequenceMenu menu && menu.containerId == p.container && p.operation.equals("seq_edit"))
                menu.action(ctx.player(), p.module, p.key, p.value, p.text);
            else if (ctx.player().containerMenu instanceof dev.sablelife.sound.SoundTimelineMenu menu && menu.containerId == p.container && p.operation.equals("sound_timeline"))
                menu.action(ctx.player(),p.revision,p.key,p.module,p.value,p.text);
            else if (ctx.player().containerMenu instanceof dev.sablelife.ownership.WildEncounterMenu menu && menu.containerId == p.container && p.operation.equals("wild_encounter"))
                menu.action(ctx.player(), p.key, p.text);
            else if (ctx.player().containerMenu instanceof dev.sablelife.heart.HeartSettingsMenu menu && menu.containerId == p.container && p.operation.equals("core"))
                menu.action(ctx.player(), p.key, p.value, p.text);
        });
        registrar.playToClient(FreezeStatePacket.TYPE,FreezeStatePacket.CODEC,(p,ctx)->dev.sablelife.client.FreezeClient.receive(p));
        registrar.playToClient(TerrainDebrisPacket.TYPE,TerrainDebrisPacket.CODEC,(p,ctx)->dev.sablelife.client.TerrainDebrisClient.receive(p));
        registrar.playToClient(DistortionTrailPacket.TYPE,DistortionTrailPacket.CODEC,(p,ctx)->dev.sablelife.client.DistortionTrailClient.receive(p));
        registrar.playToClient(VisualFilterPacket.TYPE,VisualFilterPacket.CODEC,(p,ctx)->dev.sablelife.client.ShockwaveClient.receive(p));
        registrar.playToClient(ShockwaveZoomPacket.TYPE,ShockwaveZoomPacket.CODEC,(p,ctx)->dev.sablelife.client.ShockwaveClient.receive(p));
        registrar.playToClient(PatternVisual.TYPE,PatternVisual.CODEC,(p,ctx)->dev.sablelife.client.PatternVisualClient.receive(p));
        registrar.playToClient(ShockwavePacket.TYPE,ShockwavePacket.CODEC,(p,ctx)->dev.sablelife.client.ShockwaveClient.receive(p));
        registrar.playToClient(ItemDisplayPacket.TYPE,ItemDisplayPacket.CODEC,(p,ctx)->dev.sablelife.client.ModuleItemDisplayClient.receive(p));
        registrar.playToClient(FlameJet.TYPE,FlameJet.CODEC,(p,ctx)->dev.sablelife.client.FlameJetParticle.receive(p));
        registrar.playToClient(ParticleVisual.TYPE, ParticleVisual.CODEC, (p, ctx) -> dev.sablelife.client.ParticleVisualClient.receive(p));
        registrar.playToClient(SlashVisualPacket.TYPE,SlashVisualPacket.CODEC,(p,ctx)->dev.sablelife.client.SlashVisualClient.receive(p));
        registrar.playToClient(ParticleTravelPacket.TYPE,ParticleTravelPacket.CODEC,(p,ctx)->dev.sablelife.client.ParticleVisualClient.receive(p));
        registrar.playToClient(MarkState.TYPE, MarkState.CODEC, (p, ctx) -> dev.sablelife.client.HeartDashboardScreen.marking = p.active);
        registrar.playToClient(BattleMusic.TYPE, BattleMusic.CODEC, (p, ctx) -> dev.sablelife.client.BattleMusicClient.receive(p));
        registrar.playToClient(SoundMotionPacket.TYPE, SoundMotionPacket.CODEC, (p, ctx) -> dev.sablelife.client.ModuleSoundClient.motion(p));
        registrar.playToClient(ModuleSound.TYPE, ModuleSound.CODEC, (p, ctx) -> dev.sablelife.client.ModuleSoundClient.receive(p));
    }
    public record BattleMusic(UUID module, String sound, boolean playing, boolean restart, float volume, int fade, net.minecraft.nbt.CompoundTag timeline, net.minecraft.world.phys.Vec3 position) implements CustomPacketPayload {
        public BattleMusic(UUID module,String sound,boolean playing,boolean restart,float volume,int fade){this(module,sound,playing,restart,volume,fade,new net.minecraft.nbt.CompoundTag(),net.minecraft.world.phys.Vec3.ZERO);}
        public static final Type<BattleMusic> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "battle_music"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BattleMusic> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.module); b.writeUtf(p.sound, 256); b.writeBoolean(p.playing); b.writeBoolean(p.restart); b.writeFloat(p.volume); b.writeVarInt(p.fade); b.writeNbt(p.timeline);b.writeDouble(p.position.x);b.writeDouble(p.position.y);b.writeDouble(p.position.z);
        }, b -> new BattleMusic(b.readUUID(), b.readUtf(256), b.readBoolean(), b.readBoolean(), b.readFloat(), b.readVarInt(), b.readNbt(),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
        @Override public Type<BattleMusic> type() { return TYPE; }
    }
    public record ModuleSound(UUID module, String sound, boolean stop, net.minecraft.world.phys.Vec3 position, float volume, float pitch, float radius, int start, int duration, int fade, boolean persistent) implements CustomPacketPayload {
        public ModuleSound(UUID module, String sound, boolean stop, net.minecraft.world.phys.Vec3 position, float volume, float pitch) { this(module, sound, stop, position, volume, pitch, 0, 0, 0, false); }
        public ModuleSound(UUID module, String sound, boolean stop, net.minecraft.world.phys.Vec3 position, float volume, float pitch, float radius) { this(module,sound,stop,position,volume,pitch,radius,0,0,false); }
        public ModuleSound(UUID module,String sound,boolean stop,net.minecraft.world.phys.Vec3 position,float volume,float pitch,float radius,int start,int duration){this(module,sound,stop,position,volume,pitch,radius,start,duration,false);}
        public ModuleSound(UUID module,String sound,boolean stop,net.minecraft.world.phys.Vec3 position,float volume,float pitch,float radius,int start,int duration,boolean persistent){this(module,sound,stop,position,volume,pitch,radius,start,duration,0,persistent);}
        public static final Type<ModuleSound> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "module_sound"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ModuleSound> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.module); b.writeUtf(p.sound, 256); b.writeBoolean(p.stop);
            b.writeDouble(p.position.x); b.writeDouble(p.position.y); b.writeDouble(p.position.z); b.writeFloat(p.volume); b.writeFloat(p.pitch); b.writeFloat(p.radius); b.writeVarInt(p.start); b.writeVarInt(p.duration);b.writeVarInt(p.fade);b.writeBoolean(p.persistent);
        }, b -> new ModuleSound(b.readUUID(), b.readUtf(256), b.readBoolean(), new net.minecraft.world.phys.Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()));
        @Override public Type<ModuleSound> type() { return TYPE; }
    }
}
