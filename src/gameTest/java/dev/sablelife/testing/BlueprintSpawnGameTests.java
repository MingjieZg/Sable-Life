package dev.sablelife.testing;

import java.util.*;
import dev.sablelife.SableLife;
import dev.sablelife.attack.AttackHitCredit;
import dev.sablelife.attack.ModuleShot;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.*;
import dev.sablelife.compat.photomancy.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sablelife_blueprint_spawn_tests") @PrefixGameTestTemplate(false)
public final class BlueprintSpawnGameTests {
    @GameTest(template="combat", batch="wild_contact_sound", timeoutTicks=100)
    public static void WildAndManuallyPlacedBlueprintsRetainContactSound(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var server = level.getServer();
        var player = admin(h);
        var source = template(h, 1, 8, core -> {
            var sound = core.modules().add("trigger_sound");
            sound.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos().east(), Direction.DOWN)));
            sound.edit("trigger", 0, "");
            sound.edit("contactMode", 1, "");
            sound.edit("contactDistance", .5, "");
            sound.edit("contactSound", 0, "minecraft:block.note_block.bass");
        });
        var name = "wild_contact_" + UUID.randomUUID();
        var wildPath = dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server, name);
        var manualPath = dev.rew1nd.sableschematicapi.blueprint.SableBlueprintFiles.path(server, name);
        var target = h.absolutePos(new BlockPos(4, 24, 18));
        level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
        player.setPos(target.getX() + .5, target.getY() + 3, target.getZ() - 23.5);
        player.setYRot(0);
        player.setXRot(0);

        HeartBlockEntity manual;
        HeartBlockEntity wild;
        var encounters = new WildEncounters();
        try {
            for (var path : new LinkedHashSet<>(List.of(wildPath, manualPath))) {
                java.nio.file.Files.createDirectories(path.getParent());
                NbtIo.writeCompressed(source.tag, path);
            }
            encounters.loadBlueprint(level, "", name, true);
            encounters.configure(level, name, 4, 0, 30, false, false, FactionRegistry.WILDS,
                    new WildEncounters.Conditions(true, false, false, Set.of(), 0), 1);
            var attempt = encounters.attemptPreviewOrSpawn(player, name, false);
            h.assertTrue(attempt.success(), "Wild Test Spawn failed: " + attempt.reason());
            wild = LoadedCoreIndex.all(level).stream().filter(core -> core != source.core
                    && core.creatureName().equals(source.core.creatureName())).findFirst().orElseThrow();

            var bodies = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            var existing = new HashSet<UUID>();
            bodies.getAllSubLevels().forEach(body -> existing.add(body.getUniqueId()));
            var destination = Vec3.atCenterOf(target.offset(12, 5, 0));
            level.getChunkAt(BlockPos.containing(destination));
            var placed = dev.rew1nd.sableschematicapi.blueprint.tool.BlueprintToolService.load(server, level, destination, name);
            h.assertTrue(placed.success() && placed.affectedSubLevels() == 1, "Manual Photomancy placement failed: " + placed.message());
            var body = bodies.getAllSubLevels().stream().filter(candidate -> !existing.contains(candidate.getUniqueId()))
                    .findFirst().orElseThrow();
            manual = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
            h.assertTrue(manual != null && manual.health().state() == HeartState.DORMANT, "Manual copy did not start dormant");
            manual.tryActivate(player);
        } finally {
            java.nio.file.Files.deleteIfExists(wildPath);
            java.nio.file.Files.deleteIfExists(manualPath);
        }

        h.assertTrue(manual.health().state() == HeartState.ALIVE, "Manually placed core did not activate");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(wild.health().state() == HeartState.ALIVE, "Wild core did not activate"))
                .thenExecute(() -> {
                    assertRestoredContactSound(h, manual, "Manual copy");
                    assertRestoredContactSound(h, wild, "Wild copy");
                }).thenSucceed();
    }

    private static void assertRestoredContactSound(GameTestHelper h, HeartBlockEntity core, String label) {
        var level = h.getLevel();
        var sound = core.modules().all().stream().filter(module -> module.type().equals("trigger_sound")).findFirst().orElseThrow();
        h.assertTrue(sound.anchors().size() == 1 && HeartModules.status(sound, core).equals("valid"), label + " lost its contact marker");
        var anchor = sound.anchors().getFirst();
        h.assertTrue(anchor.face() == Direction.DOWN && level.getBlockState(anchor.position(anchor.body(core))).is(Blocks.IRON_BLOCK),
                label + " moved the contact marker to the wrong face or block");
        h.assertTrue(sound.value("contactDistance") == .5 && sound.value("contactMode") == 1 && sound.hasSound("contactSound"),
                label + " lost its contact settings or sound");
        h.assertTrue(HeartModules.triggered(sound, core), label + " did not enable the trigger");
        var events = core.modules().events();
        long before = events.emittedSounds();
        events.tick(core);
        h.assertTrue(!ModuleEvents.contact(level, core, sound) && events.emittedSounds() == before, label + " triggered in the air");
        var ground = BlockPos.containing(ModuleEvents.contactStart(core, sound)
                .add(ModuleEvents.contactDirection(core, sound).scale(.25)));
        var previous = level.getBlockState(ground);
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        try {
            h.assertTrue(ModuleEvents.contact(level, core, sound), label + " did not detect ground");
            events.tick(core);
            events.tick(core);
            h.assertTrue(events.emittedSounds() == before + 1, label + " did not emit exactly one contact sound");
        } finally {
            level.setBlockAndUpdate(ground, previous);
        }
    }

    @GameTest(template="combat",batch="wild_disk_only_tracking")
    public static void MissingMemoryEntryPreservesSavedStructureAndQueuedCleanupUntilConfirmedRemoval(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();var body=UUID.randomUUID();
        var encounter=new CompoundTag();encounter.putUUID("Id",UUID.randomUUID());
        var bodies=new ListTag();bodies.add(NbtUtils.createUUID(body));encounter.put("Bodies",bodies);
        encounter.putString("LastDimension",level.dimension().location().toString());
        encounter.putLong("LastPosition",h.absolutePos(BlockPos.ZERO).asLong());encounter.putInt("AbsenceSeconds",0);
        var rule=new CompoundTag();rule.putString("File","disk_only");rule.putString("Dimension",level.dimension().location().toString());rule.putBoolean("ActivateCore",false);
        var entries=new ListTag();entries.add(encounter);rule.put("Encounters",entries);
        var rules=new ListTag();rules.add(rule);var saved=new CompoundTag();saved.put("Rules",rules);
        var data=WildEncounters.load(saved,level.registryAccess());
        var advance=WildEncounters.class.getDeclaredMethod("advance",net.minecraft.server.MinecraftServer.class);advance.setAccessible(true);
        advance.invoke(data,server);
        h.assertTrue(data.snapshot(level,"disk_only").getInt("ActiveCount")==1,"Absence from memory was mistaken for permanent deletion");
        h.assertTrue(data.clearCurrent(level,"disk_only"),"Cannot queue cleanup while structure is unavailable");
        data=WildEncounters.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());advance.invoke(data,server);
        h.assertTrue(data.snapshot(level,"disk_only").getInt("ActiveCount")==1&&data.snapshot(level,"disk_only").getBoolean("Clearing"),"Restart or unavailable structure discarded pending cleanup");
        data.permanentlyRemoved(level,body);advance.invoke(data,server);
        h.assertTrue(data.snapshot(level,"disk_only").getInt("ActiveCount")==0&&!data.snapshot(level,"disk_only").getBoolean("Clearing"),"Confirmed deletion failed to finish cleanup");
        h.succeed();
    }
    @GameTest(template="combat",batch="wild_external_structure_removal",timeoutTicks=80)
    public static void NativePermanentRemovalRetiresOnlyTheDeletedCorelessEncounter(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();var player=admin(h);
        var name="external_structure_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);
        java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(template(h,0,8).tag,path);
        var encounters=WildEncounters.get(server);
        // Other scenarios intentionally retain encounters nearby; this test exercises deletion, not density.
        var nearbyLimit=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("maximumNearbyWildEncounters");
        int previousNearbyLimit=nearbyLimit.get();nearbyLimit.set(64);
        try{
            encounters.loadBlueprint(level,"",name,false);
            encounters.configure(level,name,4,0,10,false,false,FactionRegistry.WILDS,
                    new WildEncounters.Conditions(true,false,false,Set.of(),0),2);
            player.setYRot(0);player.setXRot(0);
            for(int i=0;i<2;i++){
                var spot=h.absolutePos(new BlockPos(4+i*8,24,18));
                level.setBlockAndUpdate(spot,Blocks.STONE.defaultBlockState());
                player.setPos(spot.getX()+.5,spot.getY()+3,spot.getZ()-23.5);
                var attempt=encounters.attemptPreviewOrSpawn(player,name,false);
                h.assertTrue(attempt.success(),"Coreless fixture failed to spawn: "+attempt.reason());
            }
            var row=encounters.save(new CompoundTag(),level.registryAccess()).getList("Rules",10).stream()
                    .map(CompoundTag.class::cast).filter(r->r.getString("File").equals(name)).findFirst().orElseThrow();
            var records=row.getList("Encounters",10);
            var deleted=NbtUtils.loadUUID(records.getCompound(0).getList("Bodies",11).get(0));
            var retained=NbtUtils.loadUUID(records.getCompound(1).getList("Bodies",11).get(0));
            var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            dev.sablelife.compat.StructureRemovalApi.remove(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)container.getSubLevel(deleted));
            h.runAfterDelay(25,()->{
                h.assertTrue(encounters.snapshot(level,name).getInt("ActiveCount")==1,"Permanent removal retained its count or retired another encounter");
                h.assertTrue(container.getSubLevel(retained)!=null,"Permanent removal deleted the other structure");
                encounters.clearCurrent(level,name);
            });
            h.runAfterDelay(65,()->{h.assertTrue(encounters.snapshot(level,name).getInt("ActiveCount")==0,"Remaining structure did not clean up");h.succeed();});
        }finally{nearbyLimit.set(previousNearbyLimit);java.nio.file.Files.deleteIfExists(path);}
    }
    @GameTest(template="combat",batch="wild_overworld_no_oceans",timeoutTicks=100)
    public static void OverworldGroupExcludesOceanTagsButExplicitOceanChoicesStillWork(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();String name="ocean_group_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);
        java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(template(h,0,8).tag,path);
        var point=h.absolutePos(new BlockPos(4,24,18));var target=new BlockPos((point.getX()&~15)+8,point.getY(),(point.getZ()&~15)+8);
        var chunk=level.getChunkAt(target);var sampler=level.getChunkSource().randomState().sampler();
        var original=new HashMap<BlockPos,Holder<net.minecraft.world.level.biome.Biome>>();
        int minX=chunk.getPos().getMinBlockX()>>2,minZ=chunk.getPos().getMinBlockZ()>>2;
        for(int x=minX;x<minX+4;x++)for(int z=minZ;z<minZ+4;z++)for(int y=level.getMinBuildHeight()>>2;y<level.getMaxBuildHeight()>>2;y++)
            original.put(new BlockPos(x,y,z),chunk.getNoiseBiome(x,y,z));
        try{
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlockAndUpdate(target.offset(x,0,z),Blocks.STONE.defaultBlockState());
            var author=admin(h);author.setYRot(0);author.setXRot(0);author.setPos(target.getX()+.5,target.getY()+3,target.getZ()-23.5);
            var data=new WildEncounters();data.loadBlueprint(level,"",name,false);
            var registry=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            var oceans=registry.holders().filter(b->b.is(net.minecraft.tags.BiomeTags.IS_OCEAN)).toList();
            h.assertTrue(oceans.size()>=8,"Ocean tag fixture incomplete");
            for(var ocean:oceans){
                chunk.fillBiomesFromNoise((x,y,z,climate)->ocean,sampler);
                data.configure(level,name,4,0,30,false,false,FactionRegistry.WILDS,new WildEncounters.Conditions(false,false,false,Set.of(WildEncounters.OVERWORLD_BIOMES),0));
                var blocked=data.attemptPreviewOrSpawn(author,name,false);
                h.assertTrue(!blocked.success()&&blocked.reason().equals("placement_biome"),"Overworld group allowed ocean "+ocean.key().location()+": "+blocked.reason());
                data.configure(level,name,4,0,30,false,false,FactionRegistry.WILDS,new WildEncounters.Conditions(false,false,false,Set.of(WildEncounters.OVERWORLD_BIOMES,ocean.key().location()),0));
                var explicit=data.attemptPreviewOrSpawn(author,name,true);
                h.assertTrue(explicit.success()&&explicit.reason().isEmpty(),"Explicit ocean selection was ignored: "+explicit.reason());
            }
            var plains=registry.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
            chunk.fillBiomesFromNoise((x,y,z,climate)->plains,sampler);
            data.configure(level,name,4,0,30,false,false,FactionRegistry.WILDS,new WildEncounters.Conditions(false,false,false,Set.of(WildEncounters.OVERWORLD_BIOMES),0));
            h.assertTrue(data.attemptPreviewOrSpawn(author,name,true).reason().isEmpty(),"Overworld group rejected plains");
            chunk.fillBiomesFromNoise((x,y,z,climate)->oceans.getFirst(),sampler);
            data.configure(level,name,4,0,30,false,false,FactionRegistry.WILDS,new WildEncounters.Conditions(false,false,false,Set.of(),0));
            h.assertTrue(data.attemptPreviewOrSpawn(author,name,true).reason().isEmpty(),"Empty biome list no longer allows oceans");
            h.succeed();
        }finally{chunk.fillBiomesFromNoise((x,y,z,climate)->original.get(new BlockPos(x,y,z)),sampler);java.nio.file.Files.deleteIfExists(path);}
    }

    private static final class AnnouncementListener extends net.minecraft.server.level.ServerPlayer {
        final List<String> messages=new ArrayList<>();
        final List<net.minecraft.network.chat.Component> styledMessages=new ArrayList<>();
        final List<net.minecraft.network.chat.Component> titles=new ArrayList<>();
        final io.netty.channel.embedded.EmbeddedChannel channel;
        AnnouncementListener(net.minecraft.server.level.ServerLevel level){
            super(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SpawnListener"),net.minecraft.server.level.ClientInformation.createDefault());
            var transport=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            channel=new io.netty.channel.embedded.EmbeddedChannel(transport);
            connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),transport,this,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(getGameProfile(),false)){
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener callback){
                    if(packet instanceof net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket title)titles.add(title.text());
                }
            };
        }
        @Override public void sendSystemMessage(net.minecraft.network.chat.Component message,boolean overlay){messages.add(message.getString());styledMessages.add(message.copy());}
    }

    @GameTest(template="combat",batch="wild_spawn_announcement",timeoutTicks=100)
    public static void SpawnAnnouncementPersistsAndBroadcastsOnlySuccessfulSpawnsAcrossDimensions(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();String name="announcement_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);
        java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(template(h,0,8).tag,path);
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        var before=container.getAllSubLevels().stream().map(b->b.getUniqueId()).collect(java.util.stream.Collectors.toSet());
        var first=new AnnouncementListener(level);var remoteLevel=server.getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(remoteLevel!=null,"Missing second dimension");var remote=new AnnouncementListener(remoteLevel);
        var playersField=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");playersField.setAccessible(true);
        @SuppressWarnings("unchecked") var players=(List<net.minecraft.server.level.ServerPlayer>)playersField.get(server.getPlayerList());
        players.add(first);players.add(remote);
        try{
            var data=WildEncounters.get(server);data.loadBlueprint(level,"",name,false);
            h.assertTrue(data.snapshot(level,name).getString("SpawnAnnouncement").isEmpty(),"New rule announced by default");
            h.assertTrue(!data.snapshot(level,name).getBoolean("SpawnAnnouncementCoordinates"),"Coordinates enabled by default");
            data.setAnnouncementCoordinates(level,name,true);
            h.assertTrue(!data.snapshot(level,name).getBoolean("SpawnAnnouncementCoordinates"),"Blank announcement accepted coordinates");
            var author=admin(h);author.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SableLife.WILD_ENCOUNTER_TOOL.get()));
            var menu=new WildEncounterMenu(1,author.getInventory(),author);
            String rawMessage="&e&lMachine &ohas arrived&r in the world!";
            var request=data.snapshot(level,name);request.putString("File",name);request.putString("SpawnAnnouncement",rawMessage);
            request.putBoolean("SpawnAnnouncementCoordinates",true);
            request.putInt("Gap",4);request.putInt("Lifetime",0);request.putInt("Cooldown",30);
            menu.action(author,"save_rule",request.toString());
            var saved=WildEncounters.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            String message="Machine has arrived in the world!";
            h.assertTrue(saved.snapshot(level,name).getString("SpawnAnnouncement").equals(rawMessage),"Menu/save/load lost announcement formatting");
            h.assertTrue(saved.snapshot(level,name).getBoolean("SpawnAnnouncementCoordinates"),"Menu/save/load lost coordinate choice");
            saved.configure(level,name,4,0,30,false,false);
            h.assertTrue(saved.snapshot(level,name).getString("SpawnAnnouncement").equals(rawMessage),"Legacy edit reset announcement");
            h.assertTrue(saved.snapshot(level,name).getBoolean("SpawnAnnouncementCoordinates"),"Legacy edit reset coordinate choice");
            saved.setAnnouncementCoordinates(level,name,false);
            var ground=h.absolutePos(new BlockPos(4,24,18));
            for(int offset:new int[]{0,12,24})for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlockAndUpdate(ground.offset(offset+x,0,z),Blocks.STONE.defaultBlockState());
            author.setYRot(0);author.setXRot(0);author.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()-23.5);
            h.assertTrue(saved.attemptPreviewOrSpawn(author,name,true).success(),"Preview failed");
            h.assertTrue(first.messages.isEmpty()&&remote.messages.isEmpty(),"Preview sent announcement");
            var spawn=saved.attemptPreviewOrSpawn(author,name,false);h.assertTrue(spawn.success(),"Spawn failed: "+spawn.reason());
            h.assertTrue(first.messages.equals(List.of(message))&&remote.messages.equals(List.of(message)),"Successful spawn did not announce once to both dimensions");
            var styles=new ArrayList<net.minecraft.network.chat.Style>();first.styledMessages.getFirst().getVisualOrderText().accept((i,style,code)->{styles.add(style);return true;});
            h.assertTrue(styles.get(0).isBold()&&styles.get(0).getColor().getValue()==0xFFFF55&&styles.get(message.indexOf("has")).isItalic(),"Broadcast lost requested color, bold or italic");
            h.assertTrue(!styles.get(message.indexOf("in the")).isBold()&&!styles.get(message.indexOf("in the")).isItalic(),"Announcement reset code did not reset formatting");
            var blocked=saved.attemptPreviewOrSpawn(author,name,false);h.assertTrue(!blocked.success(),"Population cap bypassed");
            h.assertTrue(first.messages.size()==1&&remote.messages.size()==1,"Failed spawn announced");
            saved.configure(level,name,4,0,30,false,false,FactionRegistry.WILDS,new WildEncounters.Conditions(true,false,false,Set.of(),0),1,true);
            saved.setAnnouncementCoordinates(level,name,true);
            author.setPos(author.getX()+12,author.getY(),author.getZ());
            h.assertTrue(saved.attemptPreviewOrSpawn(author,name,false).success(),"Released spawn failed");
            h.assertTrue(first.messages.size()==2&&remote.messages.size()==2,"Released spawn did not announce");
            String coordinates=message+" [X: "+(ground.getX()+12)+", Y: "+(ground.getY()+5)+", Z: "+ground.getZ()+"]";
            h.assertTrue(first.messages.get(1).equals(coordinates)&&remote.messages.get(1).equals(coordinates),"Announcement did not append actual spawn coordinates: "+first.messages.get(1));
            saved.setAnnouncement(level,name,"   ");author.setPos(author.getX()+12,author.getY(),author.getZ());
            h.assertTrue(!saved.snapshot(level,name).getBoolean("SpawnAnnouncementCoordinates"),"Clearing announcement retained coordinate choice");
            saved.setAnnouncementCoordinates(level,name,true);
            h.assertTrue(!saved.snapshot(level,name).getBoolean("SpawnAnnouncementCoordinates"),"Blank text re-enabled coordinates");
            h.assertTrue(saved.attemptPreviewOrSpawn(author,name,false).success(),"Silent spawn failed");
            h.assertTrue(first.messages.size()==2&&remote.messages.size()==2,"Blank message announced");
            saved.setAnnouncement(level,name,"x".repeat(400)+"\n");
            h.assertTrue(saved.snapshot(level,name).getString("SpawnAnnouncement").length()==256,"Message length not bounded");
            saved.setAnnouncement(level,name,"Hello\nworld\u00a7!");
            h.assertTrue(saved.snapshot(level,name).getString("SpawnAnnouncement").equals("Helloworld!"),"Control/format characters accepted");
            var legacy=saved.save(new CompoundTag(),level.registryAccess());
            for(var value:legacy.getList("Rules",10)){var row=(CompoundTag)value;if(row.getString("File").equals(name))row.remove("SpawnAnnouncementCoordinates");}
            h.assertTrue(!WildEncounters.load(legacy,level.registryAccess()).snapshot(level,name).getBoolean("SpawnAnnouncementCoordinates"),"Old saves unexpectedly enabled coordinates");
            h.succeed();
        }finally{
            players.remove(first);players.remove(remote);
            for(var body:container.getAllSubLevels().stream().filter(b->!before.contains(b.getUniqueId())).toList())dev.sablelife.compat.StructureRemovalApi.remove(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)body);
            java.nio.file.Files.deleteIfExists(path);
        }
    }

    @GameTest(template="combat",batch="blueprint_projection_packet")
    public static void RaisedProjectionLimitRoundTripsWithinPayloadBudget(GameTestHelper h){
        var blocks=new ArrayList<BlueprintProjection.Block>();
        int state=net.minecraft.world.level.block.Block.getId(Blocks.STONE.defaultBlockState());
        for(int i=0;i<BlueprintProjection.MAX_BLOCKS;i++)blocks.add(new BlueprintProjection.Block(state,i,0,0));
        var parts=new ArrayList<BlueprintProjection.Part>();
        int perPart=BlueprintProjection.MAX_BLOCKS/BlueprintProjection.MAX_PARTS;
        for(int i=0;i<BlueprintProjection.MAX_PARTS;i++)
            parts.add(new BlueprintProjection.Part(Vec3.ZERO,new org.joml.Quaternionf(),new Vec3(1,1,1),
                    List.copyOf(blocks.subList(i*perPart,(i+1)*perPart))));
        var packet=new BlueprintProjection(UUID.randomUUID(),parts);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            BlueprintProjection.CODEC.encode(buffer,packet);
            h.assertTrue(buffer.readableBytes()<1048576,"Projection exceeded client payload budget");
            var decoded=BlueprintProjection.CODEC.decode(buffer);
            h.assertTrue(decoded.parts().stream().mapToInt(part->part.blocks().size()).sum()==32768,
                    "Raised projection limit lost blocks in transit");
            h.assertTrue(dev.sablelife.SableLifeServerConfig.maximumBlueprintProjectionBlocks()==8192,
                    "Default configured projection limit was not raised");
        }finally{buffer.release();}
        h.succeed();
    }

    @GameTest(template="combat",batch="spawn_death_event_channel",timeoutTicks=65)
    public static void DeathEventChannelQueuesBlueprintWithoutSeparateSpawnEvent(GameTestHelper h){
        var core=core(h);
        core.configure(100,0);
        var spawn=module(h,core,template(h,0,8).tag);
        int index=core.wireless().add();
        var channel=core.wireless().channel(index);
        channel.set(0,1);
        channel.set(6,4);
        channel.frequency(false,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        channel.frequency(true,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE));
        spawn.edit("trigger",2,"");
        spawn.edit("channel",index,"");
        var event=core.modules().add("combat_event");
        var row=event.signalRules().rows().getFirst();
        row.edit("eventKind",3);
        row.edit("eventChannel",index+1);
        core.tryActivate(admin(h));
        BlueprintSpawns.tickCore(core);
        h.assertTrue(BlueprintSpawns.snapshot(core,spawn).getString("Phase").equals("ready"),"Death channel triggered before death");
        h.assertTrue(core.forceDeath()&&BlueprintSpawns.snapshot(core,spawn).getString("Phase").equals("queued"),"Own death channel did not queue blueprint");
        var owner=core.creatureId();
        var level=h.getLevel();
        h.succeedWhen(()->{
            h.assertTrue(LifeLifecycle.get(level.getServer()).finished(owner),"Original life still cleaning up");
            h.assertTrue(BlueprintSpawns.snapshot(core,spawn).getString("Name").isEmpty(),"Death blueprint did not complete");
        });
    }
    @GameTest(template="combat",batch="wild_structure_only",timeoutTicks=90)
    public static void WildRuleLoadsCorelessBlueprintOnlyWhenActivationIsOff(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        var server=level.getServer();
        var player=admin(h);
        var name="wild_structure_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);
        java.nio.file.Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(template(h,0,8).tag,path);
        var encounters=WildEncounters.get(server);
        try{
            boolean rejected=false;
            try{encounters.loadBlueprint(level,"",name,true);}catch(IllegalArgumentException expected){rejected=true;}
            h.assertTrue(rejected&&!encounters.snapshot(level,name).getBoolean("Configured"),"Activation accepted a blueprint without a core");
            encounters.loadBlueprint(level,"",name,false);
            encounters.configure(level,name,4,1,10,false,false);
            var spot=h.absolutePos(new BlockPos(4,24,18));
            level.setBlockAndUpdate(spot,Blocks.STONE.defaultBlockState());
            player.setYRot(0);player.setXRot(0);
            player.setPos(spot.getX()+.5,spot.getY()+3,spot.getZ()-23.5);
            h.assertTrue(encounters.previewOrTry(player,name,false),"No-core structure did not spawn");
            var restored=WildEncounters.load(encounters.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            var row=restored.snapshot(level,name);
            h.assertTrue(!row.getBoolean("ActivateCore")&&row.getInt("ActiveCount")==1,"Pure structure rule or encounter lost on reload");
        }finally{java.nio.file.Files.deleteIfExists(path);}
        h.runAfterDelay(65,()->{
            h.assertTrue(encounters.snapshot(level,name).getInt("ActiveCount")==0,"Pure structure did not expire");
            h.succeed();
        });
    }
    @GameTest(template="combat",batch="wild_multi_encounter",timeoutTicks=85)
    public static void WildRuleTracksMultipleEncountersWithIndependentLifetimes(GameTestHelper h) throws Exception {
        var level=h.getLevel();var server=level.getServer();var source=template(h,1,8);var player=admin(h);
        String name="wild_multi_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);
        java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(source.tag,path);
        var encounters=WildEncounters.get(server);
        var conditions=new WildEncounters.Conditions(true,false,false,Set.of(),0);
        var ground=h.absolutePos(new BlockPos(4,24,18));
        var next=ground.offset(12,0,0);
        for(var spot:List.of(ground,next))for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)
            level.setBlockAndUpdate(spot.offset(x,0,z),Blocks.STONE.defaultBlockState());
        player.setYRot(0);player.setXRot(0);
        try{
            player.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()-23.5);
            encounters.configure(level,name,4,1,10,false,false,FactionRegistry.WILDS,conditions,2);
            h.assertTrue(encounters.previewOrTry(player,name,false),"First encounter did not spawn");
            player.setPos(next.getX()+.5,next.getY()+3,next.getZ()-23.5);
            encounters.configure(level,name,4,0,10,false,false,FactionRegistry.WILDS,conditions,2);
            var option=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("maximumNearbyWildEncounters");
            int previous=option.get();
            try{
                option.set(1);
                h.assertTrue(!encounters.previewOrTry(player,name,false),"Nearby cap allowed a second encounter");
            }finally{option.set(previous);}
            h.assertTrue(encounters.previewOrTry(player,name,false),"Second encounter did not spawn");
            h.assertTrue(encounters.snapshot(level,name).getInt("ActiveCount")==2&&!encounters.previewOrTry(player,name,false),"Per-rule cap was bypassed");
            var restored=WildEncounters.load(encounters.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
            h.assertTrue(restored.snapshot(level,name).getInt("ActiveCount")==2,"Tracked encounters were lost on reload");
        }finally{java.nio.file.Files.deleteIfExists(path);}
        h.runAfterDelay(65,()->{
            h.assertTrue(encounters.snapshot(level,name).getInt("ActiveCount")==1,"One encounter's expiry changed the other's lifetime");
            h.succeed();
        });
    }
    @GameTest(template="combat",batch="wild_clear_lives",timeoutTicks=120)
    public static void ClearTemplateRemovesItsLivingAndDyingCreaturesOnly(GameTestHelper h)throws Exception{clearPopulation(h,false);}
    @GameTest(template="combat",batch="wild_clear_structures",timeoutTicks=100)
    public static void ClearTemplateAlsoRemovesCorelessStructuresAndPreservesRule(GameTestHelper h)throws Exception{clearPopulation(h,true);}
    private static void clearPopulation(GameTestHelper h,boolean structures)throws Exception{
        var level=h.getLevel();var server=level.getServer();var player=admin(h);var source=template(h,structures?0:1,8);
        String name="clear_"+UUID.randomUUID().toString().substring(0,8),other=name+"_other";
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);var otherPath=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,other);
        java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(source.tag,path);NbtIo.writeCompressed(source.tag,otherPath);
        var encounters=WildEncounters.get(server);var conditions=new WildEncounters.Conditions(true,false,false,Set.of(),0);
        var nearby=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("maximumNearbyWildEncounters");int oldNearby=nearby.get();nearby.set(64);
        try{
            if(structures){encounters.loadBlueprint(level,"",name,false);encounters.loadBlueprint(level,"",other,false);}
            encounters.configure(level,name,4,0,30,false,false,FactionRegistry.WILDS,conditions,2);encounters.configure(level,other,4,0,30,false,false,FactionRegistry.WILDS,conditions,2);
            player.setYRot(0);player.setXRot(0);
            for(int i=0;i<3;i++){
                var spot=h.absolutePos(new BlockPos(4+(i==1?4:0),24,18+(i==2?4:0)));for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(spot.offset(x,0,z),Blocks.STONE.defaultBlockState());
                player.setPos(spot.getX()+.5,spot.getY()+3,spot.getZ()-23.5);h.assertTrue(encounters.previewOrTry(player,i<2?name:other,false),"Population fixture failed to spawn "+i);
            }
            var selected=encounters.save(new CompoundTag(),level.registryAccess()).getList("Rules",10).stream().map(CompoundTag.class::cast).filter(r->r.getString("File").equals(name)).findFirst().orElseThrow();
            var ids=selected.getList("Encounters",10).stream().map(CompoundTag.class::cast).map(r->r.getUUID("Id")).toList();
            if(!structures){var dying=LoadedCoreIndex.find(level,ids.getFirst());h.assertTrue(dying!=null&&dying.forceDeath(),"Dying fixture failed");}
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SableLife.WILD_ENCOUNTER_TOOL.get()));
            var menu=new WildEncounterMenu(1,player.getInventory(),player);var request=new CompoundTag();request.putString("File",name);
            var row=menu.snapshot().getList("Rules",10).stream().map(CompoundTag.class::cast).filter(r->r.getString("File").equals(name)).findFirst().orElseThrow();
            h.assertTrue(row.getInt("ActiveCount")==2,"Menu omitted tracked count");
            player.setGameMode(GameType.SURVIVAL);menu.action(player,"clear_rule",request.toString());h.assertTrue(!encounters.snapshot(level,name).getBoolean("Clearing"),"Non-admin cleared encounters");
            player.setGameMode(GameType.CREATIVE);menu.action(player,"clear_rule",request.toString());
            h.assertTrue(encounters.snapshot(level,name).getBoolean("Clearing")&&encounters.snapshot(level,name).getInt("ActiveCount")==2&&!encounters.clearCurrent(level,name),"Clear request lost current objects or accepted a duplicate");
            h.assertTrue(!encounters.previewOrTry(player,name,false),"Clearing population was replenished");
            var restored=WildEncounters.load(encounters.save(new CompoundTag(),level.registryAccess()),level.registryAccess());h.assertTrue(restored.snapshot(level,name).getBoolean("Clearing")&&restored.snapshot(level,name).getInt("ActiveCount")==2,"Cleanup request did not survive reload");
            final boolean[] verified={false};final long[] verifiedAt={-1};
            h.succeedWhen(()->{
                var current=encounters.snapshot(level,name);h.assertTrue(current.getInt("ActiveCount")==0&&!current.getBoolean("Clearing"),"Clear population still pending");
                h.assertTrue(current.getBoolean("Configured")&&current.getInt("Cooldown")==30,"Clear removed the template or its conditions");
                if(!verified[0]){h.assertTrue(encounters.snapshot(level,other).getInt("ActiveCount")==1,"Clear removed another template's objects");verified[0]=true;verifiedAt[0]=h.getTick();h.assertTrue(encounters.clearCurrent(level,other),"Other fixture clear request rejected");}
                h.assertTrue(encounters.snapshot(level,other).getInt("ActiveCount")==0,"Other fixture cleanup pending at "+h.getTick()+" (primary done at "+verifiedAt[0]+") : "+encounters.snapshot(level,other));
                for(var id:ids)h.assertTrue(!BodyOwnership.get(level).hasOwned(id)&&LifeLifecycle.get(server).rewardPoint(id,level.dimension().location())==null,"Clear left ownership or rewards");
            });
        }finally{nearby.set(oldNearby);java.nio.file.Files.deleteIfExists(path);java.nio.file.Files.deleteIfExists(otherPath);}
    }

    @GameTest(template="combat",batch="wild_release_lives",timeoutTicks=95)
    public static void ReleasedLivesIgnoreRuleLifetimeAndClearWithoutReleasingExistingObjects(GameTestHelper h)throws Exception{releasePopulation(h,false);}
    @GameTest(template="combat",batch="wild_release_structures",timeoutTicks=95)
    public static void ReleasedCorelessStructuresRemainIndependentOfRules(GameTestHelper h)throws Exception{releasePopulation(h,true);}
    private static void releasePopulation(GameTestHelper h,boolean structures)throws Exception{
        var level=h.getLevel();var server=level.getServer();var player=admin(h);var source=template(h,structures?0:1,8);
        String name="release_"+UUID.randomUUID().toString().substring(0,8);var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);
        java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(source.tag,path);
        var encounters=WildEncounters.get(server);var conditions=new WildEncounters.Conditions(true,false,false,Set.of(),1);
        var nearby=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("maximumNearbyWildEncounters");int oldNearby=nearby.get();nearby.set(64);
        try{
            if(structures)encounters.loadBlueprint(level,"",name,false);
            encounters.configure(level,name,4,1,30,false,false,FactionRegistry.WILDS,conditions,1);
            player.setYRot(0);player.setXRot(0);
            for(int i=0;i<3;i++){var spot=h.absolutePos(new BlockPos(4+(i==1?4:0),24,18+(i==2?4:0)));for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(spot.offset(x,0,z),Blocks.STONE.defaultBlockState());}
            var ground=h.absolutePos(new BlockPos(4,24,18));player.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()-23.5);
            h.assertTrue(encounters.previewOrTry(player,name,false)&&encounters.snapshot(level,name).getInt("ActiveCount")==1,"Managed fixture did not occupy a slot");
            var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);var before=container.getAllSubLevels().stream().map(body->body.getUniqueId()).collect(java.util.stream.Collectors.toSet());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SableLife.WILD_ENCOUNTER_TOOL.get()));
            var menu=new WildEncounterMenu(1,player.getInventory(),player);var request=encounters.snapshot(level,name);request.putString("File",name);request.putBoolean("ReleaseAfterSpawn",true);
            var c=request.getCompound("Conditions");for(String key:c.getAllKeys())request.put(key,c.get(key).copy());menu.action(player,"save_rule",request.toString());
            h.assertTrue(encounters.snapshot(level,name).getBoolean("ReleaseAfterSpawn")&&encounters.snapshot(level,name).getInt("ActiveCount")==1,"Release switch did not persist or detached an existing managed object");
            // Numeric edits and legacy callers must preserve the independent release switch.
            encounters.configure(level,name,4,1,30,false,true,FactionRegistry.WILDS,conditions,1);
            h.assertTrue(encounters.snapshot(level,name).getBoolean("ReleaseAfterSpawn"),"Existing configure API reset release mode");
            for(int i=1;i<3;i++){var spot=h.absolutePos(new BlockPos(4+(i==1?4:0),24,18+(i==2?4:0)));player.setPos(spot.getX()+.5,spot.getY()+3,spot.getZ()-23.5);h.assertTrue(encounters.previewOrTry(player,name,false),"Release was blocked by managed per-rule limit");}
            var released=container.getAllSubLevels().stream().filter(body->!before.contains(body.getUniqueId())).map(body->body.getUniqueId()).toList();
            h.assertTrue(released.size()==2&&encounters.snapshot(level,name).getInt("ActiveCount")==1,"Released objects entered the population count");
            encounters.configure(level,name,4,1,30,false,true,FactionRegistry.WILDS,conditions,1,false);
            h.assertTrue(!encounters.previewOrTry(player,name,false),"Disabling release did not restore the tracked population limit");
            encounters.configure(level,name,4,1,30,false,true,FactionRegistry.WILDS,conditions,1,true);
            var stored=WildEncounters.load(encounters.save(new CompoundTag(),level.registryAccess()),level.registryAccess());h.assertTrue(stored.snapshot(level,name).getBoolean("ReleaseAfterSpawn")&&stored.snapshot(level,name).getInt("ActiveCount")==1,"Release flag or managed population was lost on reload");
            h.assertTrue(encounters.clearCurrent(level,name),"Could not clear the old managed object");
            h.runAfterDelay(65,()->{
                h.assertTrue(encounters.snapshot(level,name).getInt("ActiveCount")==0&&!encounters.snapshot(level,name).getBoolean("Clearing"),"Managed object did not finish cleanup");
                for(var id:released){var body=container.getSubLevel(id);h.assertTrue(body!=null&&!body.isRemoved(),"Released structure was cleared or expired");
                    if(!structures){var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());h.assertTrue(core!=null&&core.health().state()==HeartState.ALIVE,"Released life expired with its former rule");core.expireWildEncounter();}
                    else dev.sablelife.compat.StructureRemovalApi.remove(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)body);
                }
                h.assertTrue(!encounters.clearCurrent(level,name),"Rule still controls released objects");h.succeed();
            });
        }finally{nearby.set(oldNearby);java.nio.file.Files.deleteIfExists(path);}
    }

    @GameTest(template="combat",batch="wild_absence_cleanup",timeoutTicks=160)
    public static void NearbyPlayerResetsAbsenceEvenWithForcedChunkLoading(GameTestHelper h) throws Exception {
        var level=h.getLevel();var server=level.getServer();var source=template(h,1,8);var player=admin(h);
        String name="wild_absence_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);
        java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(source.tag,path);
        var ground=h.absolutePos(new BlockPos(4,24,18));
        player.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()-23.5);player.setYRot(0);player.setXRot(0);
        level.setBlockAndUpdate(ground,Blocks.STONE.defaultBlockState());
        var encounters=WildEncounters.get(server);
        try {
            h.assertTrue(WildEncounters.Conditions.load(new CompoundTag()).absenceSeconds()==180,"Default absence setting changed");
            encounters.configure(level,name,4,0,10,false,true,FactionRegistry.WILDS,
                    new WildEncounters.Conditions(true,false,false,Set.of(),2));
            h.assertTrue(encounters.previewOrTry(player,name,false),"Test encounter did not spawn");
        } finally {java.nio.file.Files.deleteIfExists(path);}
        var spawned=LoadedCoreIndex.all(level).stream().filter(core->core!=source.core&&core.creatureName().equals(source.core.creatureName())&&core.health().state()==HeartState.ALIVE).findFirst().orElseThrow();
        h.assertTrue(spawned.editCore(player,"forceChunkLoading",1,""),"Forced chunk loading did not enable");
        level.players().add(player);
        var nearby=player.position();
        player.setPos(nearby.add(0,500,0));
        h.runAfterDelay(25,()->player.setPos(nearby));
        h.runAfterDelay(55,()->{
            h.assertTrue(!spawned.isRemoved()&&spawned.health().state()==HeartState.ALIVE,"Returning player did not reset absence");
            player.setPos(nearby.add(0,500,0));
        });
        h.runAfterDelay(130,()->{
            level.players().remove(player);
            h.assertTrue(spawned.isRemoved(),"Unattended forced-loaded encounter did not expire");
            h.assertTrue(LifeLifecycle.get(server).rewardPoint(spawned.creatureId(),level.dimension().location())==null,"Absence cleanup queued rewards");
            h.succeed();
        });
    }
    private record Template(CompoundTag tag,HeartBlockEntity core){}
    private static net.neoforged.neoforge.common.util.FakePlayer admin(GameTestHelper h){var p=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BlueprintAdmin")){@Override public boolean canUseGameMasterBlocks(){return isCreative();}};p.setGameMode(GameType.CREATIVE);return p;}
    private static Template template(GameTestHelper h,int kind,int offset){return template(h,kind,offset,core->{});}
    private static Template template(GameTestHelper h,int kind,int offset,java.util.function.Consumer<HeartBlockEntity> configureCore){var l=h.getLevel();var p=h.absolutePos(new BlockPos(4+offset,40,4));l.setBlockAndUpdate(p,kind==0?Blocks.IRON_BLOCK.defaultBlockState():kind==3?SableLife.REDSTONE_CORE.get().defaultBlockState():kind==4?SableLife.SURVIVAL_LIFE_CORE.get().defaultBlockState():SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),kind==2?SableLife.HEART.get().defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var c=l.getBlockEntity(body.getPlot().getCenterBlock()) instanceof HeartBlockEntity core?core:null;if(c!=null&&!c.isControlCore())c.editCore(admin(h),"name",0,"Spawn"+UUID.randomUUID().toString().substring(0,8));if(c!=null)configureCore.accept(c);var tag=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(l,new Vec3(body.logicalPose().position().x(),body.logicalPose().position().y(),body.logicalPose().position().z()),List.of(body.getUniqueId())).save();return new Template(tag,c);}
    @GameTest(template="combat", batch="blueprint_module_capacity", timeoutTicks=120)
    public static void NativeBlueprintPreservesFullModuleList(GameTestHelper h) throws java.io.IOException {
        var level = h.getLevel();
        var source = template(h, 1, 0, core -> {
            var list = new ListTag();
            for (int i = 0; i < HeartModules.LIMIT; i++) {
                var module = new VirtualModule("item_display");
                module.edit("name", 0, "Display " + i);
                module.edit("displaySize", 0.75, "");
                module.anchors(List.of(ModuleAnchor.create(core, core.getBlockPos().east(), Direction.UP)));
                list.add(module.save());
            }
            var data = new CompoundTag();
            data.put("Modules", list);
            core.modules().load(data);
        });
        var bytes = new java.io.ByteArrayOutputStream();
        NbtIo.writeCompressed(source.tag, bytes);
        var restored = NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.create(16 * 1024 * 1024));
        var build = PhotomancyRespawnBridge.createSpawn(SpawnTemplateBridge.inspect(restored).template(),
                new CompoundTag(), h.absoluteVec(new Vec3(6, 40, 16)), new org.joml.Quaterniond());
        long start = System.nanoTime();
        h.assertTrue(build.step(level).equals("DONE"), "Native full-capacity blueprint placement failed");
        System.out.println("MODULE_CAPACITY_BLUEPRINT_PLACE_MS=" + (System.nanoTime() - start) / 1e6);
        h.succeedWhen(() -> {
            var copy = build.heart(level);
            h.assertTrue(copy != null, "Placed core missing");
            var modules = copy.modules().all();
            h.assertTrue(modules.size() == HeartModules.LIMIT, "Blueprint dropped saved modules");
            var last = modules.getLast();
            h.assertTrue(last.name().equals("Display " + (HeartModules.LIMIT - 1)) && last.value("displaySize") == 0.75
                    && last.anchors().size() == 1 && last.anchors().getFirst().status(copy).equals("valid"),
                    "Native placement lost the last module settings or mark");
        });
    }

    private static HeartBlockEntity core(GameTestHelper h){return template(h,1,0).core;}
    private static VirtualModule module(GameTestHelper h,HeartBlockEntity c,CompoundTag template){var m=c.modules().add("blueprint_spawn");m.edit("spawnAnchor",1,"");m.arenaCenter(h.absoluteVec(new Vec3(4,25,12)),h.getLevel().dimension().location().toString());m.edit("spawnDelay",0,"");m.edit("spawnCooldown",1,"");BlueprintSpawns.install(c,m,"test",SpawnTemplateBridge.inspect(template));return m;}
    @GameTest(template="combat",batch="arena_blueprint_stage",timeoutTicks=380)
    public static void ArenaWaitsForNextStageAndAnnouncesFinalDefeat(GameTestHelper h)throws Exception {
        testArenaStages(h,false,0);
    }

    @GameTest(template="combat", batch="arena_two_boundaries", timeoutTicks=320)
    public static void OneCoreAllowsTwoBoundariesButOnlyOneRespawnRecord(GameTestHelper h) {
        var level = h.getLevel();
        var boss = core(h);
        boss.configure(100, 0);
        var player = admin(h);
        var center = ModuleArena.corePosition(boss);
        var dimension = level.dimension().location().toString();
        var first = boss.modules().add("arena");
        first.arenaCenter(center, dimension);
        first.edit("arenaRadius", 48, "");
        first.edit("arenaRespawnEnabled", 1, "");
        first.edit("arenaRespawnDelay", 100, "");
        first.edit("arenaRespawnBattleTimer", 1, "");
        var second = boss.modules().add("arena");
        var secondCenter = center.add(12, 0, 0);
        second.arenaCenter(secondCenter, dimension);
        second.edit("arenaRadius", 4, "");
        second.edit("arenaForceReturn", 1, "");
        second.edit("arenaRespawnEnabled", 1, "");
        player.setPos(center);
        h.assertTrue(ArenaRespawns.record(player, boss, first), "Could not record the first arena");
        h.assertTrue(!ArenaRespawns.record(player, boss, second), "Second arena acquired another respawn record");
        h.assertTrue(ArenaRespawns.snapshot(boss, first.id()).getBoolean("Recorded")
                && !ArenaRespawns.snapshot(boss, second.id()).getBoolean("Recorded"), "Wrong arena owns the respawn");
        var original = boss.creatureId();
        String name = boss.creatureName();
        boss.tryActivate(player);
        h.assertTrue(boss.modules().damageBoxes.bodyDamage(boss, player, boss.getBlockPos(), 5, false, false),
                "Could not start the battle timer");
        var record = arenaRecord(h, original);
        h.assertTrue(record.getLong("BattleDeadline") > level.getGameTime() && record.getUUID("Module").equals(first.id()),
                "Expected the single timer to belong to the recorded arena");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(
                ModuleArena.horizontalDistance(ModuleArena.corePosition(boss), secondCenter) < 2,
                "The second boundary did not return the boss")).thenExecute(() -> {
            h.assertTrue(boss.forceDeath(), "Could not defeat the boss in the second boundary");
        }).thenWaitUntil(() -> {
            var rebuilt = aliveNamed(level, name);
            h.assertTrue(rebuilt.size() == 1 && !rebuilt.getFirst().creatureId().equals(original), "Expected one respawn");
            var restored = rebuilt.getFirst();
            var arenas = restored.modules().all().stream().filter(m -> m.type().equals("arena")).toList();
            h.assertTrue(arenas.size() == 2, "Respawn lost a boundary module");
            h.assertTrue(arenas.stream().filter(m -> ArenaRespawns.snapshot(restored, m.id()).getBoolean("Recorded")).count() == 1,
                    "Respawn did not preserve the single arena record");
        }).thenSucceed();
    }

    @GameTest(template="combat", batch="arena_multiple_blueprint_roles", timeoutTicks=390)
    public static void BossDeathAllowsOneNextStageAndTwoIndependentLives(GameTestHelper h) {
        var level = h.getLevel();
        var boss = core(h);
        boss.configure(100, 0);
        var player = admin(h);
        var center = ModuleArena.corePosition(boss);
        var dimension = level.dimension().location().toString();
        var arena = boss.modules().add("arena");
        arena.arenaCenter(center, dimension);
        arena.edit("arenaRespawnEnabled", 1, "");
        arena.edit("arenaRespawnDelay", 100, "");
        arena.edit("arenaRespawnBattleTimer", 1, "");
        int channel = boss.wireless().add();
        boss.wireless().channel(channel).set(0, 1);
        boss.wireless().channel(channel).set(6, 4);
        boss.wireless().channel(channel).frequency(false, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        boss.wireless().channel(channel).frequency(true, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE));
        var death = boss.modules().add("combat_event").signalRules().rows().getFirst();
        death.edit("eventKind", 3);
        death.edit("eventChannel", channel + 1);
        var names = new java.util.ArrayList<String>();
        for (int i = 0; i < 4; i++) {
            var source = template(h, 1, 8 + i * 4, core -> core.configure(100, 0));
            names.add(source.core.creatureName());
            var spawn = module(h, boss, source.tag);
            spawn.arenaCenter(center.add(5 - i * 4, 5, 6), dimension);
            spawn.edit("spawnArenaRole", i < 2 ? 1 : 0, "");
            spawn.edit("spawnActivate", 1, "");
            spawn.edit("trigger", 2, "");
            spawn.edit("channel", channel, "");
        }
        player.setPos(center);
        h.assertTrue(ArenaRespawns.record(player, boss, arena), "Arena capture failed");
        String bossName = boss.creatureName();
        var original = boss.creatureId();
        boss.tryActivate(player);
        h.assertTrue(boss.modules().damageBoxes.bodyDamage(boss, player, boss.getBlockPos(), 5, false, false),
                "Could not start the battle timer");
        long deadline = arenaRecord(h, original).getLong("BattleDeadline");
        h.assertTrue(boss.forceDeath(), "Boss death did not trigger generation");
        h.startSequence().thenIdle(130).thenExecute(() -> {
            var firstStage = aliveNamed(level, names.get(0));
            var secondStage = aliveNamed(level, names.get(1));
            h.assertTrue(firstStage.size() + secondStage.size() == 1, "Expected exactly one next stage");
            var stage = firstStage.isEmpty() ? secondStage.getFirst() : firstStage.getFirst();
            h.assertTrue(ArenaRespawns.inheritedBoundary(stage) != null, "The next stage has no inherited arena");
            h.assertTrue(arenaRecord(h, original).getLong("BattleDeadline") == deadline
                    && aliveNamed(level, bossName).isEmpty(), "First stage respawned while the next stage was alive");
            for (String name : names.subList(2, 4)) {
                var independent = aliveNamed(level, name);
                h.assertTrue(independent.size() == 1 && ArenaRespawns.inheritedBoundary(independent.getFirst()) == null,
                        "An independent life failed to spawn or inherited the arena");
            }
            h.assertTrue(stage.forceDeath(), "Could not defeat the next stage");
        }).thenWaitUntil(() -> {
            var reborn = aliveNamed(level, bossName);
            h.assertTrue(reborn.size() == 1 && !reborn.getFirst().creatureId().equals(original),
                    "Expected one first-stage respawn after the next stage died");
            for (String name : names.subList(2, 4)) {
                h.assertTrue(aliveNamed(level, name).size() == 1, "Arena reset removed an independent life");
            }
        }).thenSucceed();
    }

    @GameTest(template="combat",batch="arena_blueprint_timeout",timeoutTicks=460)
    public static void ArenaTimeoutClearsActiveSecondStageAndSummons(GameTestHelper h)throws Exception {
        testArenaStages(h,true,0);
    }

    @GameTest(template="combat",batch="arena_pending_stage_timeout",timeoutTicks=460)
    public static void ArenaTimeoutCancelsQueuedSecondStage(GameTestHelper h)throws Exception {
        testArenaStages(h,true,260);
    }

    private static void testArenaStages(GameTestHelper h, boolean timeout, int stageDelay)throws Exception {
        testArenaStages(h, timeout, stageDelay, false);
    }

    @GameTest(template="combat",batch="arena_recursive_blueprints",timeoutTicks=380)
    public static void ArenaAllowsStageSummonButDoesNotChainStageOrSelfReferencingMinion(GameTestHelper h)throws Exception {
        testArenaStages(h, false, 0, true);
    }

    private static void testArenaStages(GameTestHelper h, boolean timeout, int stageDelay, boolean nested)throws Exception {
        testArenaStages(h,timeout,stageDelay,nested,null);
    }

    static void prepareArenaRestart(GameTestHelper h, java.nio.file.Path manifest)throws Exception {
        testArenaStages(h,false,200,true,manifest);
    }

    private static void testArenaStages(GameTestHelper h, boolean timeout, int stageDelay, boolean nested,
                                        java.nio.file.Path restartManifest)throws Exception {
        var level=h.getLevel();
        var server=level.getServer();
        var first=core(h);
        first.configure(100,0);
        for(int x=2;x<=8;x++) for(int z=17;z<=23;z++)
            level.setBlockAndUpdate(h.absolutePos(new BlockPos(x,4,z)),Blocks.STONE.defaultBlockState());
        var player=admin(h);
        String minionFileName="arena_minion_"+UUID.randomUUID();
        String fileName="arena_stage_"+UUID.randomUUID();
        var minionFile=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,minionFileName);
        var file=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,fileName);
        java.nio.file.Files.createDirectories(minionFile.getParent());
        var placeholder = nested ? template(h,1,24) : null;
        if (nested) {
            NbtIo.writeCompressed(placeholder.tag,minionFile);
            NbtIo.writeCompressed(placeholder.tag,file);
        }
        var minion=template(h,1,16,core->{
            core.configure(100,0);
            if (nested) {
                var recursive = follow(h,core,placeholder.tag);
                recursive.edit("startDistance",8,"");
                recursive.edit("spawnActivate",1,"");
                recursive.edit("spawnLimit",64,"");
                player.setPos(ModuleArena.corePosition(core));
                h.assertTrue(BlueprintSpawns.select(player,core,recursive,minionFileName),"Could not author self-referencing minion");
            }
        });
        String minionName=minion.core.creatureName();
        NbtIo.writeCompressed(minion.tag,minionFile);
        if (nested) {
            var recursive = minion.core.modules().all().getFirst();
            h.assertTrue(BlueprintSpawns.clear(player,minion.core,recursive),"Could not release source-only minion selection");
        }
        var second=template(h,1,8,core->{
            core.configure(100,0);
            var summon=follow(h,core,minion.tag);
            summon.edit("startDistance",8,"");
            summon.edit("spawnActivate",1,"");
            summon.edit("spawnArenaRole",2,"");
            summon.edit("trigger",0,"");
            player.setPos(ModuleArena.corePosition(core));
            h.assertTrue(BlueprintSpawns.select(player,core,summon,minionFileName),"Could not select minion blueprint");
            if (nested) {
                var recursive = module(h,core,placeholder.tag);
                recursive.edit("spawnArenaRole",1,"");
                recursive.edit("spawnActivate",1,"");
                int deathChannel = core.wireless().add();
                core.wireless().channel(deathChannel).set(0,1);
                core.wireless().channel(deathChannel).set(6,4);
                core.wireless().channel(deathChannel).frequency(false,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
                core.wireless().channel(deathChannel).frequency(true,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE));
                recursive.edit("trigger",2,"");
                recursive.edit("channel",deathChannel,"");
                var ownDeath = core.modules().add("combat_event").signalRules().rows().getFirst();
                ownDeath.edit("eventKind",3);
                ownDeath.edit("eventChannel",deathChannel+1);
                h.assertTrue(BlueprintSpawns.select(player,core,recursive,fileName),"Could not author self-referencing second stage");
            }
        });
        String firstName=first.creatureName(),secondName=second.core.creatureName();
        if (nested) {
            var recursive = second.core.modules().all().stream()
                    .filter(m->m.type().equals("blueprint_spawn")&&m.value("spawnArenaRole")==1).findFirst().orElseThrow();
            h.assertTrue(BlueprintSpawns.clear(player,second.core,recursive),"Could not release source-only boss selection");
        }
        var arena=first.modules().add("arena");
        var center=ModuleArena.corePosition(first);
        arena.arenaCenter(center,level.dimension().location().toString());
        arena.edit("arenaRespawnEnabled",1,"");
        arena.edit("arenaRespawnDelay",100,"");
        arena.edit("arenaRespawnBattleTimer",1,"");
        if(timeout)arena.edit("arenaRespawnBattleLimit",220,"");
        var spawn=module(h,first,second.tag);
        spawn.edit("spawnArenaRole",1,"");
        spawn.edit("spawnActivate",1,"");
        spawn.edit("spawnDelay",stageDelay,"");
        int channel=first.wireless().add();
        first.wireless().channel(channel).set(0,1);
        first.wireless().channel(channel).set(6,4);
        first.wireless().channel(channel).frequency(false,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        first.wireless().channel(channel).frequency(true,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE));
        spawn.edit("trigger",2,"");
        spawn.edit("channel",channel,"");
        var signal=first.modules().add("combat_event").signalRules().rows().getFirst();
        signal.edit("eventKind",3);
        signal.edit("eventChannel",channel+1);
        player.setPos(center);
        NbtIo.writeCompressed(second.tag,file);
        h.assertTrue(BlueprintSpawns.select(player,first,spawn,fileName),"Could not select stage blueprint");
        h.assertTrue(ArenaRespawns.record(player,first,arena),"Arena capture failed");
        arena.edit("arenaRespawnAnnouncement",0,"&aReturned "+firstName);
        arena.edit("arenaDefeatAnnouncement",0,"&cDefeated "+firstName);
        first.tryActivate(player);
        h.assertTrue(first.health().state()==HeartState.ALIVE,"Could not activate first stage");
        if (restartManifest!=null) {
            prepareArenaRestart(h,restartManifest,first,second,minion,spawn,fileName,minionFileName,player);
            return;
        }
        var listener=new AnnouncementListener(level);
        var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var players=(List<net.minecraft.server.level.ServerPlayer>)field.get(server.getPlayerList());
        players.add(listener);
        var original=first.creatureId();
        h.assertTrue(first.modules().damageBoxes.bodyDamage(first,player,first.getBlockPos(),5,false,false),"Player body hit failed");
        long deadline=arenaRecord(h,original).getLong("BattleDeadline");
        h.assertTrue(deadline==level.getGameTime()+(timeout?220:6000),"First player hit did not start the configured timer");
        h.assertTrue(first.forceDeath(),"First stage did not die");
        var storage=server.overworld().getDataStorage();
        storage.set("sablelife_arena_respawns",ArenaRespawns.load(ArenaRespawns.get(server).save(new CompoundTag(),level.registryAccess()),level.registryAccess()));
        storage.set("sablelife_blueprint_spawns",BlueprintSpawns.load(BlueprintSpawns.get(server).save(new CompoundTag(),level.registryAccess()),level.registryAccess()));
        h.runAfterDelay(140,()->{
            h.assertTrue(listener.messages.stream().noneMatch(s->s.contains(firstName)),"Arena broadcast before the second stage ended");
            h.assertTrue(aliveNamed(level,firstName).isEmpty(),"First stage respawned over a live second stage");
            var alive=aliveNamed(level,secondName);
            var record=arenaRecord(h,original);
            h.assertTrue(record.getLong("BattleDeadline")==deadline,"Second stage did not inherit the saved deadline");
            if(stageDelay>0){
                h.assertTrue(alive.isEmpty()&&record.hasUUID("StageJob"),"Expected the next stage to remain queued");
                return;
            }
            h.assertTrue(alive.size()==1 && ArenaRespawns.inheritedBoundary(alive.getFirst())!=null,"Arena did not register the second stage boundary");
            var secondCore=alive.getFirst();
            var summon=secondCore.modules().all().stream().filter(m->m.type().equals("blueprint_spawn")&&m.value("spawnArenaRole")==2).findFirst().orElseThrow();
            var minions=aliveNamed(level,minionName);
            h.assertTrue(minions.size()==1,"Expected one live summon, got "+minions.size()+": "
                    +BlueprintSpawns.snapshot(secondCore,summon));
            if (nested) {
                var nextStage = secondCore.modules().all().stream()
                        .filter(m->m.type().equals("blueprint_spawn")&&m.value("spawnArenaRole")==1).findFirst().orElseThrow();
                var minionCore = minions.getFirst();
                var recursive = minionCore.modules().all().stream().filter(m->m.type().equals("blueprint_spawn")).findFirst().orElseThrow();
                h.assertTrue(recursive.value("spawnLimit")==64,"Nested module settings were lost at placement");
                h.assertTrue(BlueprintSpawns.snapshot(secondCore,nextStage).getString("Name").isEmpty(),"Second boss phase inherited a third-stage job");
                h.assertTrue(BlueprintSpawns.snapshot(minionCore,recursive).getString("Name").isEmpty(),"Minion inherited an automatically executable self-reference");
                h.assertTrue(record.getInt("SummonsUsed")==1&&record.getList("Summons",11).size()==1,"Arena tracked extra nested spawns");
            }
            if(!timeout)h.assertTrue(alive.getFirst().forceDeath(),"Second stage did not die");
        });
        h.runAfterDelay(timeout?295:280,()->{
            try{
                var rebuilt=aliveNamed(level,firstName);
                h.assertTrue(rebuilt.size()==1&&!rebuilt.getFirst().creatureId().equals(original),"First stage failed to respawn once");
                h.assertTrue(aliveNamed(level,secondName).isEmpty(),"Second stage survived the arena reset");
                h.assertTrue(aliveNamed(level,minionName).isEmpty(),"Second-stage summon survived the arena reset");
                if (nested) {
                    var reset = arenaRecord(h,rebuilt.getFirst().creatureId());
                    h.assertTrue(!reset.hasUUID("Stage")&&!reset.hasUUID("StageJob")&&reset.getInt("SummonsUsed")==0
                            &&reset.getList("Summons",11).isEmpty(),"Arena reset left stale stage or summon records");
                }
                var messages=listener.messages.stream().filter(s->s.contains(firstName)).toList();
                var expected=timeout?List.of("Returned "+firstName):List.of("Defeated "+firstName,"Returned "+firstName);
                h.assertTrue(messages.equals(expected),"Wrong arena announcements: "+messages);
                var restored=rebuilt.getFirst().modules().all().stream().filter(m->m.type().equals("blueprint_spawn")).findFirst().orElseThrow();
                h.assertTrue(BlueprintSpawns.snapshot(rebuilt.getFirst(),restored).getString("Name").equals(fileName),"Rebuilt first stage lost its approved blueprint");
                ArenaRespawns.clear(player,rebuilt.getFirst(),rebuilt.getFirst().modules().all().stream().filter(m->m.type().equals("arena")).findFirst().orElseThrow());
                h.succeed();
            }finally{
                players.remove(listener);
                listener.channel.finishAndReleaseAll();
                try{java.nio.file.Files.deleteIfExists(file);java.nio.file.Files.deleteIfExists(minionFile);}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
            }
        });
    }
    private static List<HeartBlockEntity> aliveNamed(net.minecraft.server.level.ServerLevel level,String name){
        return LoadedCoreIndex.all(level).stream().filter(core->core.creatureName().equals(name)&&core.health().state()==HeartState.ALIVE).toList();
    }

    private static void prepareArenaRestart(GameTestHelper h, java.nio.file.Path manifest, HeartBlockEntity first,
                                            Template second, Template minion, VirtualModule spawn, String stageFile,
                                            String minionFile, net.minecraft.server.level.ServerPlayer player) {
        var level = h.getLevel();
        var area = h.absolutePos(new BlockPos(4,25,12));
        for (int x=-1; x<=1; x++) for (int z=-1; z<=1; z++)
            level.setChunkForced((area.getX()>>4)+x,(area.getZ()>>4)+z,true);
        h.assertTrue(first.modules().damageBoxes.bodyDamage(first,player,first.getBlockPos(),5,false,false),"Could not start arena battle");
        h.assertTrue(first.forceDeath(),"Could not queue the second stage by boss death");
        h.runAfterDelay(12,()->{
            var entry = arenaRecord(h,first.creatureId());
            h.assertTrue(entry.hasUUID("StageJob")&&entry.getUUID("StageJob").equals(spawn.id())
                    &&BlueprintSpawns.snapshot(first,spawn).getString("Phase").equals("queued"),"Second stage was not queued before shutdown");
            var state = new CompoundTag();
            state.putUUID("First",first.creatureId());
            state.putUUID("StageJob",spawn.id());
            state.putLong("BattleDeadline",entry.getLong("BattleDeadline"));
            state.putString("FirstName",first.creatureName());
            state.putString("SecondName",second.core.creatureName());
            state.putString("MinionName",minion.core.creatureName());
            state.putString("StageFile",stageFile);
            state.putString("MinionFile",minionFile);
            state.putLong("Area",area.asLong());
            try { NbtIo.writeCompressed(state,manifest); }
            catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            h.succeed();
        });
    }

    static void verifyArenaRestart(GameTestHelper h, java.nio.file.Path manifest)throws Exception {
        var state = NbtIo.readCompressed(manifest,NbtAccounter.unlimitedHeap());
        var level = h.getLevel();
        var server = level.getServer();
        var first = state.getUUID("First");
        var entry = arenaRecord(h,first);
        var stageJobs = BlueprintSpawns.get(server).save(new CompoundTag(),level.registryAccess()).getList("Entries",10);
        h.assertTrue(entry.hasUUID("StageJob")&&entry.getUUID("StageJob").equals(state.getUUID("StageJob"))
                &&stageJobs.stream().map(tag->(CompoundTag)tag).anyMatch(tag->tag.getUUID("Module").equals(state.getUUID("StageJob"))
                        &&tag.getString("Phase").equals("queued")),"Queued stage was not restored after restart");
        h.assertTrue(entry.getLong("BattleDeadline")==state.getLong("BattleDeadline"),"Arena deadline changed on restart");
        h.runAfterDelay(255,()->{
            var stages = aliveNamed(level,state.getString("SecondName"));
            var minions = aliveNamed(level,state.getString("MinionName"));
            h.assertTrue(aliveNamed(level,state.getString("FirstName")).isEmpty(),"First-stage boss respawned during the second stage");
            h.assertTrue(stages.size()==1&&minions.size()==1,"Restart did not produce exactly one second stage and one minion");
            h.assertTrue(ArenaRespawns.inheritedBoundary(stages.getFirst())!=null,"Restart lost arena boundary");
            h.assertTrue(stages.getFirst().forceDeath(),"Could not defeat the restored second stage");
        });
        h.runAfterDelay(440,()->{
            var rebuilt = aliveNamed(level,state.getString("FirstName"));
            h.assertTrue(rebuilt.size()==1&&!rebuilt.getFirst().creatureId().equals(first)
                    &&aliveNamed(level,state.getString("SecondName")).isEmpty()&&aliveNamed(level,state.getString("MinionName")).isEmpty(),
                    "Post-restart arena did not clean the full wave and respawn exactly one boss");
            var boss = rebuilt.getFirst();
            var arena = boss.modules().all().stream().filter(m->m.type().equals("arena")).findFirst().orElseThrow();
            var spawn = boss.modules().all().stream().filter(m->m.type().equals("blueprint_spawn")).findFirst().orElseThrow();
            h.assertTrue(BlueprintSpawns.snapshot(boss,spawn).getString("Name").equals(state.getString("StageFile")),
                    "Post-restart boss lost stage blueprint selection");
            var player = admin(h);
            player.setPos(ModuleArena.corePosition(boss));
            ArenaRespawns.clear(player,boss,arena);
            var area = BlockPos.of(state.getLong("Area"));
            for (int x=-1; x<=1; x++) for (int z=-1; z<=1; z++)
                level.setChunkForced((area.getX()>>4)+x,(area.getZ()>>4)+z,false);
            h.succeed();
        });
    }

    private static CompoundTag arenaRecord(GameTestHelper h, UUID owner) {
        return ArenaRespawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess())
                .getList("Entries",10).stream().map(tag->(CompoundTag)tag)
                .filter(tag->tag.getUUID("Creature").equals(owner)).findFirst().orElseThrow();
    }

    static void arenaRejectsOutsideAndSeventeenthSummonAndResetsQuota(GameTestHelper h) {
        var level = h.getLevel();
        var boss = core(h);
        var minion = template(h,1,8);
        var player = admin(h);
        var center = ModuleArena.corePosition(boss);
        var outsidePoint = center.add(50,0,0);
        var outsideChunk = new ChunkPos(BlockPos.containing(outsidePoint));
        level.setChunkForced(outsideChunk.x,outsideChunk.z,true);
        player.setPos(center);
        var arena = boss.modules().add("arena");
        arena.arenaCenter(center,level.dimension().location().toString());
        arena.edit("arenaRespawnEnabled",1,"");
        arena.edit("arenaRespawnDelay",100,"");
        h.assertTrue(ArenaRespawns.record(player,boss,arena),"Could not capture boss blueprint");

        var outside = module(h,boss,minion.tag);
        outside.arenaCenter(center.add(49,0,0),level.dimension().location().toString());
        outside.edit("spawnArenaRole",2,"");
        var spawnModules = new ArrayList<VirtualModule>();
        for (int i=0; i<17; i++) {
            var position = center.add(5+(i%5)*4,-12,5+(i/5)*4);
            var floor = BlockPos.containing(position).below();
            for (int x=-1; x<=2; x++) for (int z=-1; z<=1; z++)
                level.setBlockAndUpdate(floor.offset(x,0,z),Blocks.STONE.defaultBlockState());
            var spawn = module(h,boss,minion.tag);
            spawn.arenaCenter(position,level.dimension().location().toString());
            spawn.edit("spawnArenaRole",2,"");
            spawnModules.add(spawn);
        }
        boss.tryActivate(player);
        h.assertTrue(boss.health().state()==HeartState.ALIVE,"Boss did not activate");
        var owner = boss.creatureId();
        var minionName = minion.core.creatureName();
        var spawnedIds = new ArrayList<UUID>();
        h.runAfterDelay(55,()->{
            var record = arenaRecord(h,owner);
            var minions = aliveNamed(level,minionName);
            h.assertTrue(minions.size()==16&&record.getInt("SummonsUsed")==16&&record.getList("Summons",11).size()==16,
                    "Expected sixteen active and tracked minions, got "+minions.size()+" / "+record.getInt("SummonsUsed"));
            h.assertTrue(BlueprintSpawns.snapshot(boss,outside).getString("Error").equals("arena"),"Out-of-bounds spawn was not rejected");
            h.assertTrue(spawnModules.stream().filter(m->BlueprintSpawns.snapshot(boss,m).getString("Error").equals("arena")).count()==1,
                    "Expected exactly one in-bounds request to be rejected");
            minions.forEach(c->spawnedIds.add(c.creatureId()));
            h.assertTrue(ModuleArena.returnToCenter(minions.getFirst(),outsidePoint),"Could not move summon outside the arena");
        });
        h.runAfterDelay(95,()->{
            var moved = LoadedCoreIndex.find(level,spawnedIds.getFirst());
            var distance = moved==null ? -1 : ModuleArena.horizontalDistance(ModuleArena.corePosition(moved),center);
            h.assertTrue(moved!=null&&distance<2,"Out-of-bounds summon did not return to the center (distance "+distance+")");
            h.assertTrue(boss.forceDeath(),"Could not kill the first-stage boss");
        });
        h.runAfterDelay(290,()->{
            var rebuilt = aliveNamed(level,boss.creatureName());
            h.assertTrue(rebuilt.size()==1&&!rebuilt.getFirst().creatureId().equals(owner),"Arena did not respawn exactly one boss");
            h.assertTrue(aliveNamed(level,minionName).isEmpty(),"Arena reset left active summons");
            var record = arenaRecord(h,rebuilt.getFirst().creatureId());
            h.assertTrue(record.getInt("SummonsUsed")==0&&record.getList("Summons",11).isEmpty(),"Arena reset did not clear the summon quota and records");
            for (var id:spawnedIds)
                h.assertTrue(!BodyOwnership.get(level).hasOwned(id),"Arena reset left summon body ownership");
            var restoredArena = rebuilt.getFirst().modules().all().stream().filter(m->m.type().equals("arena")).findFirst().orElseThrow();
            ArenaRespawns.clear(player,rebuilt.getFirst(),restoredArena);
            level.setChunkForced(outsideChunk.x,outsideChunk.z,false);
            h.succeed();
        });
    }

    @GameTest(template="combat",batch="arena_battle_timeout",timeoutTicks=340)
    public static void ArenaBattleTimeoutResetsWithoutWaitingForRespawnDelay(GameTestHelper h) {
        checkArenaBattleTimeout(h, false);
    }

    @GameTest(template="combat",batch="arena_battle_chat",timeoutTicks=340)
    public static void ArenaBattleChatRespectsRangeAndSurvivesRespawn(GameTestHelper h) {
        checkArenaBattleTimeout(h, true);
    }

    private static void checkArenaBattleTimeout(GameTestHelper h, boolean chat) {
        var level=h.getLevel();
        var first=core(h);
        first.configure(100,0);
        var player=admin(h);
        var center=ModuleArena.corePosition(first);
        player.setPos(center);
        var arena=first.modules().add("arena");
        arena.arenaCenter(center,level.dimension().location().toString());
        arena.edit("arenaRespawnEnabled",1,"");
        arena.edit("arenaRespawnDelay",1200,"");
        h.assertTrue(arena.value("arenaRespawnBattleTimer")==0,"Existing arena settings should opt in to the timer");
        h.assertTrue(ArenaRespawns.record(player,first,arena),"Arena capture failed");
        arena.edit("arenaRespawnBattleTimer",1,"");
        arena.edit("arenaRespawnBattleLimit",220,"");
        h.assertTrue(arena.value(ArenaBattleTitle.ENABLED_KEY)==0,"Old arena settings unexpectedly enabled titles");
        arena.edit(ArenaBattleTitle.ENABLED_KEY,1,"");
        var oldSettings=arena.save();
        oldSettings.getCompound("Settings").remove(ArenaBattleTitle.DISPLAY_KEY);
        h.assertTrue(VirtualModule.load(oldSettings).value(ArenaBattleTitle.DISPLAY_KEY)==0,
                "Older arena records must default to Title");
        if(chat){
            var menu=new HeartDashboardMenu(1,player.getInventory(),first);
            var packet=new ModuleNetworking.Action(1,first.modules().revision(),arena.id(),"edit",
                    ArenaBattleTitle.DISPLAY_KEY,ArenaBattleTitle.CHAT_DISPLAY,"");
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());
            try{
                ModuleNetworking.Action.CODEC.encode(buffer,packet);
                var read=ModuleNetworking.Action.CODEC.decode(buffer);
                var noOp=TestActors.creative(level);
                noOp.setPos(player.position());
                var restricted=new HeartDashboardMenu(2,noOp.getInventory(),first);
                h.assertTrue(!restricted.action(noOp,read.revision(),read.module(),read.operation(),read.key(),read.value(),read.text()),
                        "Non-operator changed the reminder display");
                h.assertTrue(menu.action(player,read.revision(),read.module(),read.operation(),read.key(),read.value(),read.text()),
                        "Dashboard rejected Chat");
            }finally{buffer.release();}
            h.assertTrue(VirtualModule.load(arena.save()).value(ArenaBattleTitle.DISPLAY_KEY)==ArenaBattleTitle.CHAT_DISPLAY,
                    "Chat was lost during module serialization");
        }
        arena.edit("warningRange",4,"");
        String intro="&c&lFight! {time}";
        arena.edit(ArenaBattleTitle.START.key(),0,intro);
        arena.edit(ArenaBattleTitle.TEN_SECONDS.key(),0,"&e{time} left");
        arena.edit(ArenaBattleTitle.ONE_MINUTE.key(),0,"");
        h.assertTrue(ArenaBattleTitle.due(12000,12001)==ArenaBattleTitle.TEN_MINUTES
                &&ArenaBattleTitle.due(6000,6001)==ArenaBattleTitle.FIVE_MINUTES
                &&ArenaBattleTitle.due(1200,1201)==ArenaBattleTitle.ONE_MINUTE
                &&ArenaBattleTitle.due(200,201)==ArenaBattleTitle.TEN_SECONDS
                &&ArenaBattleTitle.due(6000,6000)==null,"Countdown title milestones are incorrect");
        first.tryActivate(player);
        var owner=first.creatureId();
        h.assertTrue(first.damage(5),"Unattributed damage did not apply");
        h.assertTrue(!arenaRecord(h,owner).contains("BattleDeadline"),"Damage with no player attribution started the battle");
        var nearby=new AnnouncementListener(level);
        var outside=new AnnouncementListener(level);
        nearby.setPos(center.x,center.y,center.z);
        outside.setPos(center.x+8,center.y,center.z);
        level.addNewPlayer(nearby);
        level.addNewPlayer(outside);
        var reminders=chat?nearby.styledMessages:nearby.titles;
        var otherFormat=chat?nearby.titles:nearby.styledMessages;
        try{
            h.assertTrue(first.modules().damageBoxes.bodyDamage(first,player,first.getBlockPos(),5,false,false),"Player damage did not apply");
            h.assertTrue(reminders.size()==1&&otherFormat.isEmpty()&&outside.titles.isEmpty()&&outside.messages.isEmpty(),
                    "Start reminder used the wrong display or reached a player outside range");
            var title=reminders.getFirst();
            h.assertTrue(title.getString().equals("Fight! 11 s"),"Start title did not replace the time placeholder: "+title.getString());
            var firstSpan=title.getSiblings().getFirst().getStyle();
            h.assertTrue(firstSpan.isBold()&&firstSpan.getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.RED)),
                    "Start title lost its formatting");
        }catch(RuntimeException|Error failure){
            nearby.discard();
            nearby.channel.finishAndReleaseAll();
            throw failure;
        }finally{
            outside.discard();
            outside.channel.finishAndReleaseAll();
        }
        long deadline=arenaRecord(h,owner).getLong("BattleDeadline");
        h.assertTrue(deadline==level.getGameTime()+220,"Player damage did not start the configured timer");
        h.runAfterDelay(25,()->{
            try{
                h.assertTrue(reminders.size()==2&&otherFormat.isEmpty()&&reminders.getLast().getString().equals("10 s left"),
                        "The ten-second threshold did not send exactly one customized reminder");
            }finally{
                nearby.discard();
                nearby.channel.finishAndReleaseAll();
            }
        });
        h.runAfterDelay(30,()->{
            h.assertTrue(first.modules().damageBoxes.bodyDamage(first,player,first.getBlockPos(),5,false,false),"Second player hit did not apply");
            h.assertTrue(arenaRecord(h,owner).getLong("BattleDeadline")==deadline,"Second hit extended the timer");
        });
        String name=first.creatureName();
        h.runAfterDelay(285,()->{
            var alive=aliveNamed(level,name);
            h.assertTrue(alive.size()==1&&!alive.getFirst().creatureId().equals(owner),"Timeout did not restore the first form before the normal respawn delay");
            var restored=alive.getFirst();
            var restoredArena=restored.modules().all().stream().filter(m->m.type().equals("arena")).findFirst().orElseThrow();
            h.assertTrue(restoredArena.value("arenaRespawnBattleTimer")==1&&restoredArena.value("arenaRespawnBattleLimit")==220,
                    "Respawn lost the current timer configuration");
            h.assertTrue(restoredArena.value(ArenaBattleTitle.ENABLED_KEY)==1
                    &&restoredArena.value(ArenaBattleTitle.DISPLAY_KEY)==(chat?ArenaBattleTitle.CHAT_DISPLAY:0)
                    &&restoredArena.text(ArenaBattleTitle.START.key()).equals(intro)
                    &&restoredArena.text(ArenaBattleTitle.TEN_SECONDS.key()).equals("&e{time} left")
                    &&restoredArena.hasText(ArenaBattleTitle.ONE_MINUTE.key())
                    &&restoredArena.text(ArenaBattleTitle.ONE_MINUTE.key()).isEmpty(),
                    "Respawn lost a custom or muted title saved after template capture");
            h.assertTrue(!arenaRecord(h,restored.creatureId()).contains("BattleDeadline"),"A new battle started without a hit");
            ArenaRespawns.clear(player,restored,restoredArena);
            h.succeed();
        });
    }

    @GameTest(template="combat",batch="arena_announcement_edit")
    public static void ArenaAnnouncementUsesDashboardPermissionsAndPacketLimit(GameTestHelper h){
        var core=core(h);
        var arena=core.modules().add("arena");
        var player=admin(h);
        player.setPos(ModuleArena.corePosition(core));
        var menu=new HeartDashboardMenu(1,player.getInventory(),core);
        h.assertTrue(!VirtualModule.load(arena.save()).hasText(ArenaBattleTitle.START.key()),
                "A new arena's missing title became an explicit blank after reload");
        String message="界".repeat(ModuleFields.ARENA_ANNOUNCEMENT_LENGTH);
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
          for(String key:List.of("arenaDefeatAnnouncement",ArenaBattleTitle.START.key())){
            buffer.clear();
            var valid=new ModuleNetworking.Action(1,core.modules().revision(),arena.id(),"edit",key,0,message);
            ModuleNetworking.Action.CODEC.encode(buffer,valid);
            var read=ModuleNetworking.Action.CODEC.decode(buffer);
            h.assertTrue(read.equals(valid),"Announcement text changed during packet roundtrip");
            h.assertTrue(menu.action(player,read.revision(),read.module(),read.operation(),read.key(),read.value(),read.text()),"Dashboard rejected valid message");
            h.assertTrue(VirtualModule.load(arena.save()).text(key).equals(message),"Announcement did not survive save/reload");
            try{
                ModuleNetworking.Action.CODEC.encode(buffer,new ModuleNetworking.Action(1,read.revision(),arena.id(),"edit",key,0,message+"a"));
                h.fail("Packet encoder accepted an oversized announcement");
            }catch(io.netty.handler.codec.EncoderException expected){}
            var noOp=TestActors.creative(h.getLevel());
            noOp.setPos(player.position());
            var restricted=new HeartDashboardMenu(2,noOp.getInventory(),core);
            h.assertTrue(!restricted.action(noOp,core.modules().revision(),arena.id(),"edit",key,0,"unapproved"),"Non-operator edited a broadcast");
            h.assertTrue(menu.action(player,core.modules().revision(),arena.id(),"edit",key,0,""),"Operator could not clear announcement");
            var restored=VirtualModule.load(arena.save());
            h.assertTrue(restored.hasText(key)&&restored.text(key).isEmpty(),"Explicit blank did not survive save/reload");
          }
            h.succeed();
        }finally{buffer.release();}
    }
    @GameTest(template = "combat", batch = "survival_refuel_blueprint")
    public static void BlueprintCopiesAutoRefuelSettingButNotStoredFuel(GameTestHelper h) {
        var level = h.getLevel();
        var player = admin(h);
        var source = template(h, 4, 0, core -> {
            player.setPos(ModuleArena.corePosition(core));
            var menu = new SurvivalTopUpMenu(1, player.getInventory(), core);
            menu.getSlot(2).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT, 3));
            var tank = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                    core.getBlockPos(), Direction.UP);
            h.assertTrue(tank != null && tank.fill(new net.neoforged.neoforge.fluids.FluidStack(
                    net.minecraft.world.level.material.Fluids.LAVA, 600),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 600,
                    "Could not store fixture liquid in the source core");
            h.assertTrue(menu.clickMenuButton(player, SurvivalTopUpMenu.ENABLE_AUTO), "Could not enable automatic refuel");
        });
        var incoming = source.tag.copy();
        var coreTag = incoming.getList("sub_levels", 10).getCompound(0).getList("block_entities", 10).getCompound(0);
        var exportedFuel = coreTag.getCompound("SurvivalRefuel");
        h.assertTrue(exportedFuel.getBoolean("Automatic") && !exportedFuel.contains("Items") && !exportedFuel.contains("Liquid"),
                "Export did not remove stored fuels");
        var originalFuel = source.core.saveWithoutMetadata(level.registryAccess()).getCompound("SurvivalRefuel");
        h.assertTrue(originalFuel.contains("Liquid"), "Missing unfiltered input tank fixture");
        coreTag.put("SurvivalRefuel", originalFuel);
        var build = PhotomancyRespawnBridge.createSpawn(incoming, new CompoundTag(),
                h.absoluteVec(new Vec3(6, 40, 16)), new org.joml.Quaterniond());
        h.assertTrue(build.step(level).equals("DONE"), "Native placement failed");
        var copy = LoadedCoreIndex.all(level).stream()
                .filter(core -> core != source.core && core.creatureName().equals(source.core.creatureName()))
                .findFirst().orElseThrow();
        var menu = new SurvivalTopUpMenu(1, player.getInventory(), copy);
        h.assertTrue(copy.isSurvivalCore() && menu.snapshot().getBoolean("Automatic")
                && menu.getSlot(2).getItem().isEmpty() && copy.survivalTokenBalance() == 0,
                "Blueprint did not preserve Auto Refuel or duplicated fuel");
        h.assertTrue(new SurvivalTopUpMenu(1, player.getInventory(), source.core).getSlot(2).getItem().getCount() == 3
                && source.core.survivalLiquidFuel().getFluid().getAmount() == 600,
                "Export removed fuel from the original core");
        h.assertTrue(copy.survivalLiquidFuel().getFluid().isEmpty(), "Import duplicated liquid fuel from an unfiltered template");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "spawn_world_destination")
    public static void BlueprintPlacementMovesTheSavedWorldDestinationWithTheCore(GameTestHelper h) {
        var level = h.getLevel();
        var offset = new Vec3(0, 2, 5);
        var source = template(h, 1, 0, core -> {
            var movement = core.modules().add("linear_movement");
            movement.edit("moveAim", 2, "");
            movement.arenaCenter(SableBodyProbe.toWorld(level, core.getBlockPos().getCenter()).add(offset),
                    level.dimension().location().toString());
        });
        var rotation = new org.joml.Quaterniond().rotationY(Math.PI / 2);
        var build = PhotomancyRespawnBridge.createSpawn(source.tag, new CompoundTag(),
                h.absoluteVec(new Vec3(6, 40, 16)), rotation);
        h.assertTrue(build.step(level).equals("DONE"), "Native placement failed");
        var copy = LoadedCoreIndex.all(level).stream()
                .filter(core -> core != source.core && core.creatureName().equals(source.core.creatureName()))
                .findFirst().orElseThrow();
        var expected = dev.sablelife.attack.ContactBox.rotate(rotation, offset);
        var destination = SableBodyProbe.toWorld(level, copy.getBlockPos().getCenter()).add(expected);
        h.succeedWhen(() -> {
            var movement = copy.modules().all().getFirst();
            h.assertTrue(movement.arenaCenter() != null && movement.arenaCenter().distanceTo(destination) < 0.12,
                    "Placed blueprint lost or misaligned the destination: " + movement.arenaCenter() + " / " + destination);
        });
    }

    @GameTest(template = "combat", batch = "spawn_arena_requires_new_mark", timeoutTicks = 60)
    public static void SpawnedBlueprintKeepsArenaSettingsButRequiresANewWorldMark(GameTestHelper h) {
        var level = h.getLevel();
        var actor = admin(h);
        var generator = core(h);
        var offset = new Vec3(0, 2, 5);
        var source = template(h, 1, 8, heart -> {
            var arena = heart.modules().add("arena");
            arena.arenaCenter(ModuleArena.corePosition(heart).add(0, 0, 4), level.dimension().location().toString());
            arena.edit("arenaRadius", 72, "");
            arena.edit("arenaRespawnEnabled", 1, "");
            h.assertTrue(ArenaRespawns.record(actor, heart, arena), "Could not record the original arena");

            var movement = heart.modules().add("linear_movement");
            movement.edit("moveAim", 2, "");
            movement.edit("trigger", 2, "");
            movement.arenaCenter(ModuleArena.corePosition(heart).add(offset), level.dimension().location().toString());
        });
        var originalArena = source.core.modules().all().getFirst();
        try {
            var arenaTag = findModule(source.tag, "arena");
            h.assertTrue(arenaTag != null && !arenaTag.contains("sablelife:blueprint_arena_offset")
                    && !arenaTag.contains("ArenaCenter"), "New blueprint exported the original arena mark");
            var oldOffset = new CompoundTag();
            oldOffset.putDouble("Z", 4);
            arenaTag.put("sablelife:blueprint_arena_offset", oldOffset);

            var spawn = module(h, generator, source.tag);
            spawn.edit("spawnActivate", 1, "");
            generator.tryActivate(actor);
            BlueprintSpawns.tickCore(generator);
            BlueprintSpawns.get(level.getServer()).advance(level.getServer());

            var copy = LoadedCoreIndex.all(level).stream()
                    .filter(heart -> heart != source.core && heart.creatureName().equals(source.core.creatureName()))
                    .findFirst().orElseThrow(() -> new GameTestAssertException("Could not find the generated core"));
            var expectedWaypoint = ModuleArena.corePosition(copy).add(offset);
            h.assertTrue(copy.health().state() == HeartState.DORMANT, "Core activated before its fixed waypoint was restored");
            var savedWhileWaiting = copy.saveWithoutMetadata(level.registryAccess());
            h.assertTrue(savedWhileWaiting.contains("PendingBlueprintActivation"), "Pending activation was not saved");
            copy.loadWithComponents(savedWhileWaiting, level.registryAccess());
            h.assertTrue(copy.health().state() == HeartState.DORMANT, "Reload bypassed pending activation");
            var copiedArena = copy.modules().all().getFirst();
            var movement = copy.modules().all().get(1);
            h.assertTrue(copiedArena.arenaCenter() == null && HeartModules.status(copiedArena, copy).equals("unmarked"),
                    "A legacy blueprint restored the arena's world mark");
            h.assertTrue(copiedArena.value("arenaRadius") == 72 && copiedArena.value("arenaRespawnEnabled") == 1,
                    "Arena settings were lost while clearing the world mark");
            h.assertTrue(!ArenaRespawns.snapshot(copy, copiedArena.id()).getBoolean("Recorded"),
                    "Generated blueprint inherited the original arena record");

            h.succeedWhen(() -> {
                h.assertTrue(copy.health().state() == HeartState.ALIVE, "Generated core did not activate");
                h.assertTrue(!copy.saveWithoutMetadata(level.registryAccess()).contains("PendingBlueprintActivation"),
                        "Completed activation retained a pending request");
                h.assertTrue(movement.arenaCenter() != null && movement.arenaCenter().distanceTo(expectedWaypoint) < .25,
                        "Independent linear waypoint was lost or misaligned");
                h.assertTrue(copiedArena.arenaCenter() == null && HeartModules.status(copiedArena, copy).equals("unmarked"),
                        "Activating the generated core restored the old arena mark");
            });
        } finally {
            ArenaRespawns.clear(actor, source.core, originalArena);
        }
    }

    private static CompoundTag findModule(Tag tag, String type) {
        if (tag instanceof CompoundTag compound) {
            if (compound.getString("Type").equals(type) && compound.hasUUID("Id")) return compound;
            for (String key : compound.getAllKeys()) {
                var found = findModule(compound.get(key), type);
                if (found != null) return found;
            }
        } else if (tag instanceof ListTag list) {
            for (var entry : list) {
                var found = findModule(entry, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @GameTest(template="combat",batch="spawn_metadata")
    public static void CountsOrdinarySingleMultipleAndRedstoneCoresBeforeGeneration(GameTestHelper h){for(int kind=0;kind<4;kind++){var source=template(h,kind,kind*5);var info=SpawnTemplateBridge.inspect(source.tag);h.assertTrue(info.life()==(kind==0||kind==3?0:kind==2?2:1)&&info.redstone()==(kind==3?1:0),"Wrong blueprint core counts for kind "+kind);h.assertTrue(info.canActivate()==(kind==1),"Invalid auto activation policy for kind "+kind);}h.succeed();}
    @GameTest(template="combat",batch="spawn_ordinary")
    public static void OrdinaryBlueprintGeneratesOnceOnHeldTriggerAndHonorsLimit(GameTestHelper h){var c=core(h);var m=module(h,c,template(h,0,8).tag);c.tryActivate(admin(h));var data=BlueprintSpawns.get(h.getLevel().getServer());BlueprintSpawns.tickCore(c);data.advance(h.getLevel().getServer());h.assertTrue(BlueprintSpawns.snapshot(c,m).getInt("Count")==1&&BlueprintSpawns.snapshot(c,m).getString("Phase").equals("done"),"Ordinary no-core structure failed native placement: "+BlueprintSpawns.snapshot(c,m));for(int i=0;i<5;i++){BlueprintSpawns.tickCore(c);data.advance(h.getLevel().getServer());}h.assertTrue(BlueprintSpawns.snapshot(c,m).getInt("Count")==1,"Held signal repeatedly generated structures");m.edit("trigger",2,"");BlueprintSpawns.tickCore(c);m.edit("trigger",0,"");BlueprintSpawns.tickCore(c);data.advance(h.getLevel().getServer());h.assertTrue(BlueprintSpawns.snapshot(c,m).getInt("Count")==1,"Generation limit was bypassed");c.forceDeath();h.assertTrue(BlueprintSpawns.snapshot(c,m).getString("Name").isEmpty(),"Dead source retained an idle template registration");h.succeed();}
    @GameTest(template="combat",batch="spawn_activate",timeoutTicks=50)
    public static void SingleCoreGenerationAutoActivatesWithNewIdentity(GameTestHelper h) {
        var c = core(h);
        var source = template(h, 1, 8, heart -> {
            heart.modules().add("sound").group("警报");
            heart.modules().add("melee").group("警报");
            heart.modules().add("particles");
        });
        String name = source.core.creatureName();
        UUID old = source.core.creatureId();
        var m = module(h, c, source.tag);
        h.assertTrue(m.value("spawnActivate") == 1, "New blueprint modules must default to activation On");
        c.tryActivate(admin(h));
        h.succeedWhen(() -> {
            var alive = LoadedCoreIndex.all(h.getLevel()).stream()
                    .filter(v -> v.creatureName().equals(name) && v.health().state() == HeartState.ALIVE).toList();
            h.assertTrue(alive.size() == 1 && !alive.getFirst().creatureId().equals(old), "Generated life core not activated with a new identity");
            h.assertTrue(BlueprintSpawns.snapshot(c, m).getInt("Count") == 1, "Generation completion missing");
            var restored = alive.getFirst().modules().all();
            h.assertTrue(restored.stream().map(VirtualModule::type).toList().equals(List.of("sound", "melee", "particles"))
                    && restored.stream().map(VirtualModule::group).toList().equals(List.of("警报", "警报", "")),
                    "Blueprint placement lost group membership or module order");
        });
    }

    @GameTest(template = "combat", batch = "spawn_move_target_legacy", timeoutTicks = 50)
    public static void LegacyBlueprintMoveKeepsIndependentGunAimAfterPlacement(GameTestHelper h) {
        spawnMoveGun(h, false);
    }

    @GameTest(template = "combat", batch = "spawn_move_target_on", timeoutTicks = 50)
    public static void BlueprintMoveWithInheritanceKeepsItsGunAimAfterPlacement(GameTestHelper h) {
        spawnMoveGun(h, true);
    }

    private static void spawnMoveGun(GameTestHelper h, boolean inherit) {
        var level = h.getLevel();
        var generator = core(h);
        var source = template(h, 1, 8, heart -> {
            var gun = heart.modules().add("gun");
            gun.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.UP)));
            gun.edit("shotAim", 1, "");
            gun.edit("target", 1, "");
            gun.edit("targetRange", 12, "");
            gun.edit("interval", 200, "");
            var table = heart.modules().add("move_set");
            h.assertTrue(table.moveSet().edit("add", 0, "", heart.modules()), "Could not add source move");
            h.assertTrue(table.moveSet().edit("action_add", 0, "0|" + gun.id(), heart.modules()), "Could not bind source gun");
            var rules = table.moveSet().get(0).config();
            h.assertTrue(rules.edit("dispatchFront", 3, "") && rules.edit("dispatchCone", 60, "")
                    && rules.edit("target", 1, "") && rules.edit("inheritMoveTarget", 1, ""), "Could not set move target rules");
        });
        if (!inherit) h.assertTrue(removeMoveTargetField(source.tag) == 1, "Legacy blueprint fixture did not remove one move setting");

        var spawn = module(h, generator, source.tag);
        spawn.edit("spawnActivate", 1, "");
        generator.tryActivate(admin(h));
        BlueprintSpawns.tickCore(generator);
        BlueprintSpawns.get(level.getServer()).advance(level.getServer());
        h.assertTrue(BlueprintSpawns.snapshot(generator, spawn).getInt("Count") == 1, "Native placement did not complete");

        var copy = LoadedCoreIndex.all(level).stream().filter(heart -> !heart.creatureId().equals(source.core.creatureId())
                && heart.creatureName().equals(source.core.creatureName()) && heart.health().state() == HeartState.ALIVE)
                .findFirst().orElseThrow(() -> new GameTestAssertException("Could not find the spawned life"));
        var gun = copy.modules().all().getFirst();
        var move = copy.modules().all().get(1).moveSet().get(0);
        var oldGun = source.core.modules().all().getFirst();
        var oldMove = source.core.modules().all().get(1).moveSet().get(0);
        h.assertTrue(gun.type().equals("gun") && !gun.id().equals(oldGun.id()) && !move.config().id().equals(oldMove.config().id())
                && move.actions().size() == 1 && move.actions().getFirst().module().equals(gun.id()), "Move or action UUID was not remapped");
        h.assertTrue(HeartModules.status(gun, copy).equals("valid") && !gun.anchors().getFirst().bodyId().equals(oldGun.anchors().getFirst().bodyId())
                && gun.value("trigger") == 4 && move.config().value("inheritMoveTarget") == (inherit ? 1 : 0),
                "The placed gun lost its mark, trigger or move rule");

        var origin = ModuleCombat.nozzle(copy, gun);
        var east = spawnMoveCow(h, origin.add(7, 0, 0));
        var west = spawnMoveCow(h, origin.add(-3, 0, 0));
        copy.modules().tick(copy);
        var shots = level.getEntitiesOfClass(ModuleShot.class, new AABB(origin, origin).inflate(12),
                shot -> shot.getPersistentData().hasUUID(AttackHitCredit.MODULE)
                        && gun.id().equals(shot.getPersistentData().getUUID(AttackHitCredit.MODULE)));
        h.assertTrue(shots.size() == 1 && shots.getFirst().getDeltaMovement().x * (inherit ? 1 : -1) > 0,
                "The placed gun did not fire at the expected target");
        shots.forEach(Entity::discard);
        east.discard();
        west.discard();
        gun.edit("enabled", 0, "");
        h.succeed();
    }

    private static LivingEntity spawnMoveCow(GameTestHelper h, Vec3 center) {
        var cow = EntityType.COW.create(h.getLevel());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPos(center.add(0, -cow.getBbHeight() / 2, 0));
        h.assertTrue(h.getLevel().addFreshEntity(cow), "Could not spawn target cow");
        return cow;
    }

    private static int removeMoveTargetField(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            if (compound.getString("Type").equals("move_rules")) {
                var settings = compound.getCompound("Settings");
                if (!settings.contains("inheritMoveTarget")) return 0;
                settings.remove("inheritMoveTarget");
                return 1;
            }
            return compound.getAllKeys().stream().mapToInt(key -> removeMoveTargetField(compound.get(key))).sum();
        }
        if (tag instanceof ListTag list) return list.stream().mapToInt(BlueprintSpawnGameTests::removeMoveTargetField).sum();
        return 0;
    }

    @GameTest(template="combat",batch="spawn_no_activate",timeoutTicks=40)
    public static void LifeBlueprintWithActivationOffSpawnsDormant(GameTestHelper h) {
        var c = core(h);
        var source = template(h, 1, 8);
        String name = source.core.creatureName();
        var m = module(h, c, source.tag);
        var actor = admin(h);
        actor.setPos(ModuleArena.corePosition(c));
        var menu = new HeartDashboardMenu(1, actor.getInventory(), c);
        h.assertTrue(editSpawnActivation(h, menu, actor, m, 0), "Could not disable activation from the dashboard");
        h.assertTrue(VirtualModule.load(m.save()).value("spawnActivate") == 0, "Saved activation Off was lost");
        c.tryActivate(actor);
        h.succeedWhen(() -> {
            var copies = LoadedCoreIndex.all(h.getLevel()).stream()
                    .filter(v -> v != source.core && v.creatureName().equals(name)).toList();
            h.assertTrue(copies.size() == 1 && copies.getFirst().health().state() == HeartState.DORMANT,
                    "Auto activation Off spawned active life");
            h.assertTrue(BlueprintSpawns.snapshot(c, m).getInt("Count") == 1, "Dormant placement did not complete");
        });
    }

    @GameTest(template="combat",batch="spawn_activate_authoring")
    public static void ActivationControlPreservesLegacyDataAndValidatesPermissionAndBlueprint(GameTestHelper h) {
        var c = core(h);
        var module = c.modules().add("blueprint_spawn");
        h.assertTrue(VirtualModule.load(module.save()).value("spawnActivate") == 1, "Saved activation On was lost");
        var legacy = module.save();
        legacy.getCompound("Settings").remove("spawnActivate");
        h.assertTrue(VirtualModule.load(legacy).value("spawnActivate") == 0, "Legacy module activation changed");

        var actor = admin(h);
        actor.setPos(ModuleArena.corePosition(c));
        var menu = new HeartDashboardMenu(1, actor.getInventory(), c);
        h.assertTrue(editSpawnActivation(h, menu, actor, module, 0)
                        && editSpawnActivation(h, menu, actor, module, 1), "Empty module could not toggle activation");
        h.assertTrue(!editSpawnActivation(h, menu, actor, module, 2), "Invalid activation value accepted");
        actor.setGameMode(GameType.SURVIVAL);
        h.assertTrue(!editSpawnActivation(h, menu, actor, module, 0), "Non-operator could disable activation");
        actor.setGameMode(GameType.CREATIVE);

        var coreless = SpawnTemplateBridge.inspect(template(h, 0, 8).tag);
        h.assertTrue(BlueprintSpawns.install(c, module, "test", coreless) && module.value("spawnActivate") == 0,
                "Coreless blueprint did not load with activation Off");
        h.assertTrue(!editSpawnActivation(h, menu, actor, module, 1), "Coreless blueprint accepted activation On");
        h.assertTrue(BlueprintSpawns.clear(actor, c, module) && editSpawnActivation(h, menu, actor, module, 1),
                "Clearing the blueprint did not restore activation editing");
        h.succeed();
    }

    private static boolean editSpawnActivation(GameTestHelper h, HeartDashboardMenu menu,
            net.minecraft.server.level.ServerPlayer player, VirtualModule module, double value) {
        var action = new ModuleNetworking.Action(menu.containerId, menu.snapshot().getInt("Revision"),
                module.id(), "edit", "spawnActivate", value, "");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                h.getLevel().registryAccess());
        try {
            ModuleNetworking.Action.CODEC.encode(buffer, action);
            var decoded = ModuleNetworking.Action.CODEC.decode(buffer);
            return menu.action(player, decoded.revision(), decoded.module(), decoded.operation(), decoded.key(),
                    decoded.value(), decoded.text());
        } finally {
            buffer.release();
        }
    }
    @GameTest(template="combat",batch="spawn_conflict")
    public static void InvalidAutoActivationDoesNotGenerateAndPlainRedstoneStillPlaces(GameTestHelper h){var c=core(h);var m=module(h,c,template(h,3,8).tag);m.edit("spawnActivate",1,"");c.tryActivate(admin(h));BlueprintSpawns.tickCore(c);BlueprintSpawns.get(h.getLevel().getServer()).advance(h.getLevel().getServer());h.assertTrue(BlueprintSpawns.snapshot(c,m).getInt("Count")==0&&BlueprintSpawns.snapshot(c,m).getString("Error").equals("activation"),"Redstone-only template attempted Life activation");m.edit("spawnActivate",0,"");m.edit("trigger",2,"");BlueprintSpawns.tickCore(c);m.edit("trigger",0,"");BlueprintSpawns.tickCore(c);BlueprintSpawns.get(h.getLevel().getServer()).advance(h.getLevel().getServer());h.assertTrue(BlueprintSpawns.snapshot(c,m).getInt("Count")==1,"Redstone-only template failed ordinary placement");h.succeed();}
    @GameTest(template="combat",batch="spawn_death",timeoutTicks=65)
    public static void DeathJobOutlivesOriginalCoreAndGeneratesSecondStage(GameTestHelper h){var c=core(h);c.configure(100,0);var source=template(h,1,8);String name=source.core.creatureName();var m=module(h,c,source.tag);m.edit("spawnEvent",1,"");m.edit("spawnActivate",1,"");c.tryActivate(admin(h));UUID original=c.creatureId();h.assertTrue(c.forceDeath(),"Fixture death failed");h.succeedWhen(()->{h.assertTrue(LifeLifecycle.get(h.getLevel().getServer()).finished(original),"Generation did not wait for original cleanup");var alive=LoadedCoreIndex.all(h.getLevel()).stream().filter(v->v.creatureName().equals(name)&&v.health().state()==HeartState.ALIVE).toList();h.assertTrue(alive.size()==1,"Death generation missing or duplicated");h.assertTrue(c.isRemoved()||c.health().state()!=HeartState.ALIVE,"Original core remained alive");});}
    @GameTest(template="combat",batch="spawn_resume")
    public static void QueuedJobPersistsWithoutCopyingTemplateToClient(GameTestHelper h){var c=core(h);var m=module(h,c,template(h,0,8).tag);m.edit("spawnDelay",10,"");c.tryActivate(admin(h));BlueprintSpawns.tickCore(c);var data=BlueprintSpawns.get(h.getLevel().getServer());var saved=data.save(new CompoundTag(),h.getLevel().registryAccess());var restored=BlueprintSpawns.load(saved,h.getLevel().registryAccess());h.getLevel().getServer().overworld().getDataStorage().set("sablelife_blueprint_spawns",restored);h.assertTrue(BlueprintSpawns.snapshot(c,m).getString("Phase").equals("queued"),"Queued job lost on reload");h.assertTrue(!c.modules().snapshot(c,true).toString().contains("block_palette"),"Large template sent in client metadata");h.runAfterDelay(15,()->{h.assertTrue(BlueprintSpawns.snapshot(c,m).getInt("Count")==1,"Queued placement did not resume");h.succeed();});}
    @GameTest(template="combat",batch="spawn_files")
    public static void OperatorFileSelectionRejectsTraversalAndNonOperator(GameTestHelper h)throws Exception{var c=core(h);var m=c.modules().add("blueprint_spawn");var p=admin(h);var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(h.getLevel().getServer(),"sablelife_spawn_"+UUID.randomUUID());java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(template(h,0,8).tag,path);String name=path.getFileName().toString();try{h.assertTrue(BlueprintSpawns.select(p,c,m,name),"Server blueprint failed selection");h.assertTrue(!BlueprintSpawns.select(TestActors.creative(h.getLevel()),c,m,name),"Non-operator imported template");h.assertTrue(!BlueprintSpawns.select(p,c,m,"../outside"),"Path traversal accepted");h.assertTrue(BlueprintSpawns.snapshot(c,m).getInt("Life")==0,"Selected ordinary template metadata incorrect");}finally{java.nio.file.Files.deleteIfExists(path);}h.succeed();}
    @GameTest(template="combat",batch="spawn_names")
    public static void NamesAreCoreLocalCaseInsensitiveAndNeedNoOnlineLookup(GameTestHelper h){var c=core(h);var p=admin(h);p.setPos(SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));var menu=new HeartSettingsMenu(1,p.getInventory(),c);h.assertTrue(menu.action(p,"relation:excludeName",0,"Offline_User"),"Offline name entry rejected");h.assertTrue(c.relations().excludes("OFFLINE_user"),"Name match was case sensitive");var clone=new CoreRelations();clone.load(c.relations().save());h.assertTrue(clone.excludes("offline_user"),"Name lost on save/load");h.assertTrue(!c.relations().exclude("bad/name")&&!c.relations().exclude("name with spaces"),"Invalid name accepted");h.assertTrue(menu.action(p,"relation:removeName",0,"offline_USER")&&!c.relations().excludes("Offline_User"),"Name removal failed");h.succeed();}
    private static VirtualModule follow(GameTestHelper h,HeartBlockEntity c,CompoundTag template){var m=module(h,c,template);m.edit("spawnPosition",1,"");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().east(),Direction.SOUTH)));return m;}
    private static AABB actualBounds(net.minecraft.server.level.ServerLevel level,Set<UUID> ids){AABB result=null;var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);for(var id:ids){var body=container.getSubLevel(id);var b=body.getPlot().getBoundingBox();for(double x:new double[]{b.minX(),b.maxX()+1})for(double y:new double[]{b.minY(),b.maxY()+1})for(double z:new double[]{b.minZ(),b.maxZ()+1}){var p=body.logicalPose().transformPosition(new Vec3(x,y,z));var box=new AABB(p,p);result=result==null?box:result.minmax(box);}}return result;}
    private static void sameBox(GameTestHelper h,AABB a,AABB b){h.assertTrue(a.getCenter().distanceTo(b.getCenter())<.001&&Math.abs(a.getXsize()-b.getXsize())<.001&&Math.abs(a.getYsize()-b.getYsize())<.001&&Math.abs(a.getZsize()-b.getZsize())<.001,"Preview/placement bounds differ: "+a+" / "+b);}
    @GameTest(template="combat",batch="spawn_follow_geometry")
    public static void FollowMarkUsesPartRotationAndStartDistance(GameTestHelper h){var c=core(h);var m=follow(h,c,template(h,0,8).tag);m.edit("startDistance",3,"");var anchor=m.anchors().getFirst();var body=anchor.body(c);var before=BlueprintSpawnPlacement.resolve(c,m);body.logicalPose().orientation().rotateY(Math.PI/2);body.logicalPose().position().add(4,2,3);var after=BlueprintSpawnPlacement.resolve(c,m);var expected=anchor.world(c,false).add(ModuleCombat.baseDirection(c,m).scale(3));h.assertTrue(after!=null&&after.position().distanceTo(expected)<1e-7&&after.position().distanceTo(before.position())>1,"Follow point lost body transform or offset");var forward=dev.sablelife.attack.ContactBox.rotate(after.rotation(),new Vec3(0,0,1));h.assertTrue(forward.distanceTo(ModuleCombat.baseDirection(c,m))<1e-7,"Blueprint orientation did not follow selected direction");m.edit("direction",1,"");var down=BlueprintSpawnPlacement.resolve(c,m);h.assertTrue(down.position().distanceTo(anchor.world(c,false).add(ModuleCombat.baseDirection(c,m).scale(3)))<1e-7,"Direction override did not move start offset");h.succeed();}
    @GameTest(template="combat",batch="spawn_fixed_legacy")
    public static void FixedModeKeepsLegacyPointAndIgnoresFollowFields(GameTestHelper h){var c=core(h);var m=module(h,c,template(h,0,8).tag);m.edit("startDistance",100,"");m.edit("direction",1,"");var point=BlueprintSpawnPlacement.resolve(c,m);h.assertTrue(point.position().equals(m.arenaCenter())&&point.rotation()==null,"Fixed placement changed legacy pose/position");h.assertTrue(VirtualModule.load(m.save()).value("spawnPosition")==0,"Old save no longer defaults fixed");h.succeed();}
    @GameTest(template="combat",batch="spawn_rotated_bounds")
    public static void RotatedNativePlacementMatchesCachedPreviewGeometry(GameTestHelper h){var c=core(h);var source=template(h,0,8);var m=follow(h,c,source.tag);m.edit("startDistance",5,"");m.anchors().getFirst().body(c).logicalPose().orientation().rotateY(.7).rotateZ(.3);var destination=BlueprintSpawnPlacement.resolve(c,m);var preview=BlueprintSpawns.previewBounds(c,m);var build=PhotomancyRespawnBridge.createSpawn(source.tag,new CompoundTag(),destination.position(),destination.rotation());sameBox(h,preview,build.bounds());h.assertTrue(build.step(h.getLevel()).equals("DONE"),"Rotated native build failed");sameBox(h,preview,actualBounds(h.getLevel(),build.bodies()));h.succeed();}
    @GameTest(template="combat",batch="spawn_preview_pure")
    public static void PreviewDoesNotPlaceBodiesOrExposeTemplatePayload(GameTestHelper h){var c=core(h);var m=follow(h,c,template(h,0,8).tag);var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel());var before=c.health().current();AABB first=BlueprintSpawns.previewBounds(c,m);for(int i=0;i<10;i++)sameBox(h,first,BlueprintSpawns.previewBounds(c,m));h.assertTrue(c.health().current()==before&&BlueprintSpawns.snapshot(c,m).getInt("Count")==0,"Preview changed runtime");var data=BlueprintSpawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess());h.assertTrue(data.getList("Entries",10).stream().map(t->(CompoundTag)t).filter(t->t.hasUUID("Module")&&t.getUUID("Module").equals(m.id())).allMatch(t->t.getString("Phase").equals("ready")),"Preview queued placement");h.assertTrue(!c.modules().snapshot(c,true).toString().contains("block_palette"),"Preview metadata uploaded a template");h.succeed();}
    @GameTest(template="combat",batch="spawn_follow_snapshot",timeoutTicks=40)
    public static void QueuedFollowJobFreezesPoseAndSurvivesMarkLossAndReload(GameTestHelper h){var c=core(h);var m=follow(h,c,template(h,0,8).tag);m.edit("spawnDelay",10,"");m.edit("startDistance",6,"");c.tryActivate(admin(h));BlueprintSpawns.tickCore(c);var original=BlueprintSpawns.previewBounds(c,m);m.anchors().getFirst().body(c).logicalPose().orientation().rotateY(1);m.clearMarks();sameBox(h,original,BlueprintSpawns.previewBounds(c,m));var registry=BlueprintSpawns.get(h.getLevel().getServer());var saved=registry.save(new CompoundTag(),h.getLevel().registryAccess());var restored=BlueprintSpawns.load(saved,h.getLevel().registryAccess());h.getLevel().getServer().overworld().getDataStorage().set("sablelife_blueprint_spawns",restored);sameBox(h,original,BlueprintSpawns.previewBounds(c,m));h.succeedWhen(()->{h.assertTrue(BlueprintSpawns.snapshot(c,m).getInt("Count")==1,"Frozen follow job failed after mark loss/reload");});}
    @GameTest(template="combat",batch="spawn_follow_death",timeoutTicks=65)
    public static void FollowDeathSnapshotSurvivesCoreCleanup(GameTestHelper h){var c=core(h);c.configure(100,0);var source=template(h,1,8);String name=source.core.creatureName();var m=follow(h,c,source.tag);m.edit("spawnEvent",1,"");m.edit("spawnActivate",1,"");m.edit("startDistance",8,"");c.tryActivate(admin(h));var destination=BlueprintSpawnPlacement.resolve(c,m);UUID original=c.creatureId();c.forceDeath();h.succeedWhen(()->{h.assertTrue(LifeLifecycle.get(h.getLevel().getServer()).finished(original),"Original cleanup not finished");var copy=LoadedCoreIndex.all(h.getLevel()).stream().filter(v->v.health().state()==HeartState.ALIVE&&v.creatureName().equals(name)).findFirst().orElseThrow(()->new net.minecraft.gametest.framework.GameTestAssertException("No generated follow life"));var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),copy.getBlockPos());h.assertTrue(new org.joml.Quaterniond(body.logicalPose().orientation()).dot(destination.rotation())>.999,"Death placement lost orientation");});}
    @GameTest(template="combat",batch="spawn_follow_invalid")
    public static void FollowRejectsMissingOrDisconnectedMarkAndKeepsOtherModePoint(GameTestHelper h){var c=core(h);var source=template(h,0,8);var m=module(h,c,source.tag);var fixed=m.arenaCenter();m.edit("spawnPosition",1,"");h.assertTrue(BlueprintSpawnPlacement.resolve(c,m)==null&&HeartModules.status(m,c).equals("unmarked"),"Follow mode accepted missing mark");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));h.assertTrue(BlueprintSpawnPlacement.resolve(c,m)!=null,"Valid follow mark rejected");m.edit("spawnPosition",0,"");h.assertTrue(BlueprintSpawnPlacement.resolve(c,m).position().equals(fixed),"Switching mode erased fixed mark");h.succeed();}

    @GameTest(template="combat",batch="spawn_top_upright")
    public static void TopFaceStaysUprightAndOffsetsAreIndependent(GameTestHelper h){
        var c=core(h);var m=follow(h,c,template(h,0,8).tag);m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));m.edit("startDistance",3,"");m.edit("spawnOffsetX",2,"");m.edit("spawnOffsetY",4,"");m.edit("spawnOffsetZ",-1,"");
        var body=m.anchors().getFirst().body(c);var result=BlueprintSpawnPlacement.resolve(c,m);var expected=m.anchors().getFirst().world(c,false).add(ModuleCombat.baseDirection(c,m).scale(3)).add(dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(body.logicalPose().orientation()),new Vec3(2,4,-1)));
        h.assertTrue(result.position().distanceTo(expected)<1e-7,"Offsets ignored local frame");h.assertTrue(Math.abs(result.rotation().dot(new org.joml.Quaterniond(body.logicalPose().orientation())))>.99999,"Top face tipped blueprint");
        m.edit("spawnYaw",90,"");var rotated=BlueprintSpawnPlacement.resolve(c,m);h.assertTrue(rotated.position().distanceTo(expected)<1e-7,"Rotation moved the spawn point");h.assertTrue(dev.sablelife.attack.ContactBox.rotate(rotated.rotation(),new Vec3(0,0,1)).distanceTo(dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(body.logicalPose().orientation()),new Vec3(1,0,0)))<1e-7,"Yaw did not turn forward");h.succeed();
    }
    @GameTest(template="combat",batch="spawn_base_anchor")
    public static void BaseAnchorPlacesWholeBoundsAboveMarkedSurface(GameTestHelper h){
        var c=core(h);var m=module(h,c,template(h,0,8).tag);m.edit("spawnAnchor",0,"");m.edit("spawnOffsetY",2,"");m.edit("spawnYaw",35,"");m.edit("spawnPitch",25,"");var bounds=BlueprintSpawns.previewBounds(c,m);var point=BlueprintSpawnPlacement.resolve(c,m).position();
        h.assertTrue(Math.abs(bounds.minY-point.y)<1e-6&&Math.abs(bounds.getCenter().x-point.x)<1e-6&&Math.abs(bounds.getCenter().z-point.z)<1e-6,"Base anchor not at bounds bottom center");
        c.tryActivate(admin(h));BlueprintSpawns.tickCore(c);var rows=BlueprintSpawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()).getList("Entries",10);var job=rows.stream().map(t->(CompoundTag)t).filter(t->t.getUUID("Module").equals(m.id())).findFirst().orElseThrow();var q=job.getList("Rotation",6);var rotation=new org.joml.Quaterniond(q.getDouble(0),q.getDouble(1),q.getDouble(2),q.getDouble(3));var build=PhotomancyRespawnBridge.createSpawn(job.getCompound("Template"),new CompoundTag(),new Vec3(job.getDouble("X"),job.getDouble("Y"),job.getDouble("Z")),rotation);sameBox(h,bounds,build.bounds());h.succeed();
    }
    @GameTest(template="combat",batch="spawn_release_retains",timeoutTicks=45)
    public static void ReleaseKeepsBothTemplatesAndCancelsQueuedGeneration(GameTestHelper h){
        var c=core(h);var player=admin(h);var m=module(h,c,template(h,0,8).tag);m.edit("spawnDelay",10,"");var arena=c.modules().add("arena");arena.arenaCenter(h.absoluteVec(new Vec3(4,25,12)),h.getLevel().dimension().location().toString());h.assertTrue(ArenaRespawns.record(player,c,arena),"Arena record failed");
        c.tryActivate(player);BlueprintSpawns.tickCore(c);var old=c.creatureId();h.assertTrue(BlueprintSpawns.snapshot(c,m).getString("Phase").equals("queued"),"Fixture not queued");h.assertTrue(c.resetForCreative(player)&&!old.equals(c.creatureId()),"Release failed");
        h.assertTrue(BlueprintSpawns.snapshot(c,m).getString("Name").equals("test")&&BlueprintSpawns.snapshot(c,m).getString("Phase").equals("ready"),"Release erased blueprint selection");h.assertTrue(ArenaRespawns.snapshot(c,arena.id()).getBoolean("Recorded"),"Release erased Arena capture");
        h.runAfterDelay(15,()->{h.assertTrue(BlueprintSpawns.snapshot(c,m).getInt("Count")==0,"Cancelled job still generated");var saved=BlueprintSpawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess());var restored=BlueprintSpawns.load(saved,h.getLevel().registryAccess());h.getLevel().getServer().overworld().getDataStorage().set("sablelife_blueprint_spawns",restored);h.assertTrue(BlueprintSpawns.snapshot(c,m).getString("Name").equals("test"),"Preserved template lost after reload");c.tryActivate(player);h.assertTrue(c.health().state()==HeartState.ALIVE,"Reactivation failed after Release");BlueprintSpawns.tickCore(c);h.succeedWhen(()->h.assertTrue(BlueprintSpawns.snapshot(c,m).getInt("Count")==1,"Retained template did not run after reactivation"));});
    }
    @GameTest(template="combat",batch="spawn_external_delete_cancels")
    public static void PermanentCoreDeletionCancelsQueuedBlueprint(GameTestHelper h) {
        var c=core(h);
        var m=module(h,c,template(h,0,8).tag);
        m.edit("spawnDelay",20,"");
        c.tryActivate(admin(h));
        BlueprintSpawns.tickCore(c);
        h.assertTrue(BlueprintSpawns.snapshot(c,m).getString("Phase").equals("queued"),"Fixture did not queue blueprint");
        var level=h.getLevel();
        var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)dev.ryanhcode.sable.Sable.HELPER.getContaining(level,c.getBlockPos());
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).removeSubLevel(body,dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        h.assertTrue(BlueprintSpawns.snapshot(c,m).getString("Name").isEmpty(),"Deleted core kept its queued blueprint");
        h.succeed();
    }
    @GameTest(template="combat",batch="wild_ground_gap_and_expiry",timeoutTicks=100)
    public static void WildEncounterSpawnsAboveHighestGroundAndExpiresWithoutReward(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var source = template(h, 1, 8);
        String name = "wild_fixture_" + UUID.randomUUID().toString().substring(0, 8);
        var path = dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(level.getServer(), name);
        java.nio.file.Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(source.tag, path);
        try {
            var player = admin(h);
            var target = h.absolutePos(new BlockPos(4, 24, 18));
            player.setPos(target.getX() + .5, target.getY() + 3, target.getZ() - 24 + .5);
            player.setYRot(0);
            player.setXRot(0);
            level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            var encounters = WildEncounters.get(level.getServer());
            encounters.configure(level, name, 4, 1, 10, false, true);
            var enabled = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("enableWildEncounters");
            boolean previous = enabled.get();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(SableLife.WILD_ENCOUNTER_TOOL.get()));
            var menu = new WildEncounterMenu(1, player.getInventory(), player);
            try {
                enabled.set(false);
                h.assertTrue(!menu.stillValid(player), "Disabled feature kept an open tool menu valid");
                h.assertTrue(!encounters.previewOrTry(player, name, false), "Disabled feature still allowed test spawn");
                h.assertTrue(!encounters.snapshot(level, name).getBoolean("Active"), "Disabled trial created an encounter");
            } finally { enabled.set(previous); }
            h.assertTrue(encounters.previewOrTry(player, name, false), "Trial placement above ground failed");
            var spawned = LoadedCoreIndex.all(level).stream().filter(core -> core != source.core
                    && core.creatureName().equals(source.core.creatureName()) && core.health().state() == HeartState.ALIVE).findFirst().orElseThrow();
            h.assertTrue(FactionRegistry.WILDS.equals(spawned.relations().faction()), "Default trial did not join Wilds");
            source.core.relations().faction(FactionRegistry.WILDS);
            LoadedCoreIndex.observe(source.core);
            h.assertTrue(CoreRelations.allies(source.core, spawned) && !CoreRelations.damageAllowed(spawned, source.core.creatureId()), "Wilds did not block friendly damage");
            h.assertTrue(FactionRegistry.get(level.getServer()).snapshot(player).stream().map(CompoundTag.class::cast)
                    .noneMatch(row -> FactionRegistry.WILDS.equals(row.getUUID("Id"))), "Wilds appeared in the public faction list");
            var body = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, spawned.getBlockPos());
            h.assertTrue(Math.abs(actualBounds(level, Set.of(body.getUniqueId())).minY - (target.getY() + 5)) < .001,
                    "Bottom of the blueprint was not four blocks above the highest ground");
            var id = spawned.creatureId();
            h.assertTrue(!encounters.previewOrTry(player, name, false), "Rule spawned a second simultaneous creature");
            var stored = WildEncounters.load(encounters.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            h.assertTrue(stored.snapshot(level, name).getBoolean("Active") && stored.snapshot(level, name).getInt("Lifetime") == 1,
                    "Lifetime or tracked creature lost on reload");
            h.succeedWhen(() -> {
                var lifecycle = LifeLifecycle.get(level.getServer());
                h.assertTrue(spawned.isRemoved() && !BodyOwnership.get(level).hasOwned(id), "Expired encounter was not removed");
                h.assertTrue(lifecycle.rewardPoint(id, level.dimension().location()) == null, "Expiry queued a reward");
            });
        } finally { java.nio.file.Files.deleteIfExists(path); }
    }
    @GameTest(template="combat",batch="wild_player_proximity",timeoutTicks=110)
    public static void WildEncounterUsesLoadedGroundNearSurvivalPlayer(GameTestHelper h) throws Exception {automaticPlayerSpawn(h,GameType.SURVIVAL);}
    @GameTest(template="combat",batch="wild_creative_player_proximity",timeoutTicks=140)
    public static void CreativePlayersAlsoTriggerNaturalEncountersButSpectatorsDoNot(GameTestHelper h) throws Exception {automaticPlayerSpawn(h,GameType.CREATIVE);}
    private static void automaticPlayerSpawn(GameTestHelper h,GameType mode) throws Exception {
        automaticPlayerSpawn(h,mode,WildEncounters.Density.DEFAULT);
    }

    @GameTest(template="combat",batch="wild_density_distance",timeoutTicks=120)
    public static void AutomaticSpawningUsesTheConfiguredDistanceBand(GameTestHelper h)throws Exception{
        automaticPlayerSpawn(h,GameType.SURVIVAL,new WildEncounters.Density(1,4,24,32));
    }

    private static void automaticPlayerSpawn(GameTestHelper h,GameType mode,WildEncounters.Density density) throws Exception {
        var level = h.getLevel();
        var source = template(h, 1, 8);
        String name = "wild_natural_" + UUID.randomUUID().toString().substring(0, 8);
        var path = dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(level.getServer(), name);
        java.nio.file.Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(source.tag, path);
        var encounters = WildEncounters.get(level.getServer());
        var faction = FactionRegistry.get(level.getServer()).create(admin(h), "Encounter " + name);
        h.assertTrue(faction != null, "Could not create encounter faction");
        encounters.configure(level,name,2,0,10,true,true,faction,new WildEncounters.Conditions(true,false,false,Set.of(),180),1,false,density);
        var restored = WildEncounters.load(encounters.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        h.assertTrue(faction.equals(restored.snapshot(level, name).getUUID("Faction")), "Encounter faction did not survive reload");
        java.nio.file.Files.deleteIfExists(path);
        var point = h.absoluteVec(new Vec3(4.5, 7, 4.5));
        int chunkX = BlockPos.containing(point).getX() >> 4;
        int chunkZ = BlockPos.containing(point).getZ() >> 4;
        var forced = new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            var chunk = new net.minecraft.world.level.ChunkPos(chunkX + x, chunkZ + z);
            if (level.setChunkForced(chunk.x, chunk.z, true)) forced.add(chunk);
            level.getChunk(chunk.x, chunk.z);
        }
        var player = TestActors.creative(level);
        player.setGameMode(mode==GameType.CREATIVE?GameType.SPECTATOR:mode);
        player.setNoGravity(true);
        player.setPos(point);
        level.addNewPlayer(player);
        var enabled = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue) dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("enableWildEncounters");
        boolean previous = enabled.get();
        enabled.set(false);
        h.runAfterDelay(25, () -> {
            try {
                h.assertTrue(!encounters.snapshot(level, name).getBoolean("Active"), "Disabled server switch allowed automatic spawning");
            } finally { enabled.set(previous); }
            if(mode==GameType.CREATIVE){
                h.runAfterDelay(20,()->{
                    h.assertTrue(!encounters.snapshot(level,name).getBoolean("Active"),"Spectator triggered natural generation");
                    player.setGameMode(mode);
                });
            }
            h.succeedWhen(() -> {
                h.assertTrue(encounters.snapshot(level, name).getBoolean("Active"), "Re-enabled rule did not spawn around "+mode);
                var core = LoadedCoreIndex.all(level).stream().filter(candidate -> candidate != source.core && candidate.creatureName().equals(source.core.creatureName()) && candidate.health().state() == HeartState.ALIVE).findFirst().orElseThrow();
                h.assertTrue(faction.equals(core.relations().faction()), "Automatic spawn did not join its configured faction");
                var position = SableBodyProbe.toWorld(level, core.getBlockPos().getCenter());
                double distance = Math.hypot(position.x - point.x, position.z - point.z);
                h.assertTrue(distance >= density.minDistance()-2 && distance <= density.maxDistance()+2, "Encounter was outside the expected distance ring");
                try{encounters.configure(level,name,2,0,10,false,false,faction);}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
                core.expireWildEncounter();
                player.discard();
                for (var chunk : forced) level.setChunkForced(chunk.x, chunk.z, false);
            });
        });
    }

    @GameTest(template="combat",batch="wild_legacy_once_removed",timeoutTicks=100)
    public static void LegacyCompletedOnceRulesCanTestAndAutomaticallyReplenishAgain(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();var source=template(h,0,8);String name="legacy_once_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(source.tag,path);
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);var before=container.getAllSubLevels().stream().map(b->b.getUniqueId()).collect(java.util.stream.Collectors.toSet());
        var actor=TestActors.creative(level);actor.setNoGravity(true);var point=h.absoluteVec(new Vec3(4.5,7,4.5));actor.setPos(point);level.addNewPlayer(actor);
        var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();int cx=BlockPos.containing(point).getX()>>4,cz=BlockPos.containing(point).getZ()>>4;
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){var chunk=new net.minecraft.world.level.ChunkPos(cx+x,cz+z);if(level.setChunkForced(chunk.x,chunk.z,true))forced.add(chunk);level.getChunk(chunk.x,chunk.z);}
        try{
            var data=new WildEncounters();data.loadBlueprint(level,"",name,false);data.configure(level,name,4,0,10,true,true,FactionRegistry.WILDS,new WildEncounters.Conditions(false,true,false,Set.of(),0),1,false,new WildEncounters.Density(1,8,16,16));
            var saved=data.save(new CompoundTag(),level.registryAccess());var old=saved.getList("Rules",10).getCompound(0);old.putBoolean("Once",true);old.putBoolean("Spawned",true);
            data=WildEncounters.load(saved,level.registryAccess());h.assertTrue(!data.snapshot(level,name).contains("Once")&&!data.snapshot(level,name).contains("Spawned"),"Removed one-shot state is still exposed");
            var ground=h.absolutePos(new BlockPos(4,24,18));for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlockAndUpdate(ground.offset(x,0,z),Blocks.STONE.defaultBlockState());
            var tester=admin(h);tester.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()-23.5);tester.setYRot(0);tester.setXRot(0);
            var test=data.attemptPreviewOrSpawn(tester,name,false);h.assertTrue(test.success()&&data.snapshot(level,name).getInt("ActiveCount")==1,"Legacy completed history still blocked manual testing: "+test.reason());
            tester.setPos(tester.getX()+5,tester.getY(),tester.getZ());var capped=data.attemptPreviewOrSpawn(tester,name,false);h.assertTrue(!capped.success()&&capped.reason().equals("placement_rule_cap"),"Removing one-shot mode bypassed population limits");
            var advance=WildEncounters.class.getDeclaredMethod("advance",net.minecraft.server.MinecraftServer.class);advance.setAccessible(true);
            for(int i=0;i<2;i++){
                for(var body:container.getAllSubLevels().stream().filter(b->!before.contains(b.getUniqueId())).toList())dev.sablelife.compat.StructureRemovalApi.remove(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)body);
                saved=data.save(new CompoundTag(),level.registryAccess());var row=saved.getList("Rules",10).getCompound(0);h.assertTrue(!row.contains("Once")&&!row.contains("Spawned"),"Removed flags were written back to disk");
                row.put("Encounters",new ListTag());row.putLong("Next",0);
                if(i==0){row.putBoolean("Once",true);row.putBoolean("Spawned",true);}
                data=WildEncounters.load(saved,level.registryAccess());advance.invoke(data,server);
                h.assertTrue(data.snapshot(level,name).getInt("ActiveCount")==1,"Population did not replenish after the old one-shot rule was migrated");
            }
            h.succeed();
        }finally{
            actor.discard();for(var body:container.getAllSubLevels().stream().filter(b->!before.contains(b.getUniqueId())).toList())dev.sablelife.compat.StructureRemovalApi.remove(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)body);
            for(var chunk:forced)level.setChunkForced(chunk.x,chunk.z,false);java.nio.file.Files.deleteIfExists(path);
        }
    }

    @GameTest(template="combat",batch="wild_density_settings")
    public static void DensityPersistsAndMenuRejectsInvalidDistancesWithoutChangingTheRule(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();var source=template(h,0,8);String name="density_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(source.tag,path);
        try{
            var data=WildEncounters.get(server);data.loadBlueprint(level,"",name,false);
            var defaults=data.snapshot(level,name);h.assertTrue(defaults.getInt("RetrySeconds")==5&&defaults.getInt("SearchAttempts")==1&&defaults.getInt("MinDistance")==48&&defaults.getInt("MaxDistance")==96,"Legacy density defaults changed");
            var conditions=new WildEncounters.Conditions(false,false,false,Set.of(),180);
            data.configure(level,name,4,60,30,false,false,FactionRegistry.WILDS,conditions,3,false,new WildEncounters.Density(9,4,64,120));
            var restored=WildEncounters.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess()).snapshot(level,name);
            h.assertTrue(restored.getInt("RetrySeconds")==9&&restored.getInt("SearchAttempts")==4&&restored.getInt("MinDistance")==64&&restored.getInt("MaxDistance")==120,"Density settings did not survive reload");
            data.configure(level,name,4,60,30,false,false,FactionRegistry.WILDS,conditions);
            h.assertTrue(data.snapshot(level,name).getInt("SearchAttempts")==4,"Legacy configure call reset density");
            var player=admin(h);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SableLife.WILD_ENCOUNTER_TOOL.get()));
            var menu=new WildEncounterMenu(1,player.getInventory(),player);var request=data.snapshot(level,name);request.putString("File",name);
            var nested=request.getCompound("Conditions");for(String key:nested.getAllKeys())request.put(key,nested.get(key).copy());
            request.putInt("Cooldown",60);request.putInt("RetrySeconds",1);request.putInt("SearchAttempts",8);request.putInt("MinDistance",16);request.putInt("MaxDistance",24);
            menu.action(player,"save_rule",request.toString());
            var saved=data.snapshot(level,name);h.assertTrue(saved.getInt("Cooldown")==60&&saved.getInt("SearchAttempts")==8&&saved.getInt("RetrySeconds")==1&&saved.getInt("MaxDistance")==24,"Menu did not save density");
            request.putInt("MinDistance",200);menu.action(player,"save_rule",request.toString());
            h.assertTrue(data.snapshot(level,name).getInt("MinDistance")==16&&data.snapshot(level,name).getInt("MaxDistance")==24,"Invalid distance range changed the rule");
            request.putInt("MinDistance",16);request.putInt("SearchAttempts",9);menu.action(player,"save_rule",request.toString());
            h.assertTrue(data.snapshot(level,name).getInt("SearchAttempts")==8,"Unbounded search attempts were accepted");h.succeed();
        }finally{java.nio.file.Files.deleteIfExists(path);}
    }

    @GameTest(template="combat",batch="wild_density_runtime",timeoutTicks=100)
    public static void DensityRetriesFailedPositionsAndDoesNotAddFailureBackoffAfterSuccess(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();var source=template(h,0,8);String name="search_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(source.tag,path);
        var player=TestActors.creative(level);player.setNoGravity(true);var point=h.absoluteVec(new Vec3(4.5,7,4.5));player.setPos(point);level.addNewPlayer(player);
        var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();int cx=BlockPos.containing(point).getX()>>4,cz=BlockPos.containing(point).getZ()>>4;
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){var chunk=new net.minecraft.world.level.ChunkPos(cx+x,cz+z);if(level.setChunkForced(chunk.x,chunk.z,true))forced.add(chunk);level.getChunk(chunk.x,chunk.z);}
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);var before=container.getAllSubLevels().stream().map(b->b.getUniqueId()).collect(java.util.stream.Collectors.toSet());
        BlockPos obstruction=null;
        try{
            var conditions=new WildEncounters.Conditions(false,true,false,Set.of(),0);
            var first=new WildEncounters();first.loadBlueprint(level,"",name,false);first.configure(level,name,1,0,10,true,false,FactionRegistry.WILDS,conditions,3,false,new WildEncounters.Density(60,1,32,32));
            var multiple=new WildEncounters();multiple.loadBlueprint(level,"",name,false);multiple.configure(level,name,1,0,10,true,false,FactionRegistry.WILDS,conditions,3,false,new WildEncounters.Density(60,2,32,32));
            level.random.setSeed(1291);level.random.nextInt(1);double angle=level.random.nextDouble()*Math.PI*2;level.random.nextInt(1);
            obstruction=new BlockPos((int)Math.floor(point.x+Math.cos(angle)*32),level.getMaxBuildHeight()-1,(int)Math.floor(point.z+Math.sin(angle)*32));level.setBlockAndUpdate(obstruction,Blocks.BEDROCK.defaultBlockState());
            var advance=WildEncounters.class.getDeclaredMethod("advance",net.minecraft.server.MinecraftServer.class);advance.setAccessible(true);
            var rulesField=WildEncounters.class.getDeclaredField("rules");rulesField.setAccessible(true);
            level.random.setSeed(1291);advance.invoke(first,server);h.assertTrue(first.snapshot(level,name).getInt("ActiveCount")==0,"Single search ignored blocked first candidate");
            Object firstRule=((Map<?,?>)rulesField.get(first)).values().iterator().next();var retry=firstRule.getClass().getDeclaredField("retryAfter");retry.setAccessible(true);
            h.assertTrue(retry.getLong(firstRule)==server.overworld().getGameTime()+1200,"Failed search did not use configured retry interval");
            level.setBlockAndUpdate(obstruction,Blocks.AIR.defaultBlockState());level.random.setSeed(1291);advance.invoke(first,server);h.assertTrue(first.snapshot(level,name).getInt("ActiveCount")==0,"Failed-search backoff was ignored");
            level.setBlockAndUpdate(obstruction,Blocks.BEDROCK.defaultBlockState());level.random.setSeed(1291);advance.invoke(multiple,server);
            h.assertTrue(multiple.snapshot(level,name).getInt("ActiveCount")==1,"Second candidate was not searched after first failed");
            Object rule=((Map<?,?>)rulesField.get(multiple)).values().iterator().next();h.assertTrue(retry.getLong(rule)==0,"Failure backoff delayed a successful spawn");
            var saved=multiple.save(new CompoundTag(),level.registryAccess()).getList("Rules",10).getCompound(0);h.assertTrue(saved.getLong("Next")==server.overworld().getGameTime()+200,"Successful spawn did not use its separate cooldown");h.succeed();
        }finally{
            if(obstruction!=null)level.setBlockAndUpdate(obstruction,Blocks.AIR.defaultBlockState());player.discard();
            for(var body:container.getAllSubLevels().stream().filter(b->!before.contains(b.getUniqueId())).toList())dev.sablelife.compat.StructureRemovalApi.remove(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)body);
            for(var chunk:forced)level.setChunkForced(chunk.x,chunk.z,false);java.nio.file.Files.deleteIfExists(path);
        }
    }

    @GameTest(template="combat",batch="wild_forest_rules",timeoutTicks=80)
    public static void ForestPlacementCopiesRulesAndDeletesOnlyConfirmedBlueprint(GameTestHelper h) throws Exception {
        var level=h.getLevel();var server=level.getServer();var source=template(h,1,8);
        String first="forest_a_"+UUID.randomUUID(),second="forest_b_"+UUID.randomUUID();
        var firstPath=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,first);
        var secondPath=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,second);
        java.nio.file.Files.createDirectories(firstPath.getParent());
        NbtIo.writeCompressed(source.tag,firstPath);NbtIo.writeCompressed(source.tag,secondPath);
        try{
            var player=admin(h);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(SableLife.WILD_ENCOUNTER_TOOL.get()));
            var ground=h.absolutePos(new BlockPos(4,24,18));
            player.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()-23.5);player.setYRot(0);player.setXRot(0);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(ground.offset(x,0,z),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(ground.above(),Blocks.OAK_LOG.defaultBlockState());
            level.setBlockAndUpdate(ground.above(2),Blocks.OAK_LOG.defaultBlockState());
            var biome=level.getBiome(ground.above()).unwrapKey().orElseThrow().location();
            var encounters=WildEncounters.get(server);
            encounters.configure(level,first,0,0,30,false,false,FactionRegistry.WILDS,new WildEncounters.Conditions(true,false,false,Set.of(biome)));
            h.assertTrue(!encounters.previewOrTry(player,first,false),"Default bounds allowed placement through a tree");
            var menu=new WildEncounterMenu(1,player.getInventory(),player);
            menu.action(player,"select",first);
            var conditions=new CompoundTag();conditions.putString("File",first);conditions.putBoolean("Sky",true);
            conditions.putBoolean("IgnoreBounds",true);conditions.putBoolean("ClearTrees",true);
            var biomes=new ListTag();biomes.add(StringTag.valueOf(biome.toString()));conditions.put("Biomes",biomes);
            menu.action(player,"conditions",conditions.toString());
            menu.action(player,"copy","");menu.action(player,"select",second);menu.action(player,"paste","");
            var rule=encounters.snapshot(level,second);
            h.assertTrue(rule.getInt("Cooldown")==30&&!rule.getBoolean("Active")&&rule.getCompound("Conditions").getBoolean("ClearTrees"),"Pasted rule lost settings or copied runtime");
            h.assertTrue(encounters.previewOrTry(player,second,false),"Clearing could not place at forest floor");
            h.assertTrue(level.getBlockState(ground).is(Blocks.STONE)&&level.getBlockState(ground.above()).isAir()
                    &&level.getBlockState(ground.above(2)).is(Blocks.OAK_LOG),"Unexpected clearing: "+level.getBlockState(ground)+", "+level.getBlockState(ground.above())+", "+level.getBlockState(ground.above(2)));
            menu.action(player,"delete_blueprint",second);menu.action(player,"delete_blueprint",second);
            h.assertTrue(java.nio.file.Files.exists(secondPath),"Deleted a blueprint with an active encounter");
            menu.action(player,"delete_blueprint",first);
            h.assertTrue(java.nio.file.Files.exists(firstPath),"First click deleted a blueprint");
            menu.action(player,"delete_blueprint",first);
            h.assertTrue(!java.nio.file.Files.exists(firstPath)&&!encounters.snapshot(level,first).getBoolean("Configured")
                    &&java.nio.file.Files.exists(secondPath),"Confirmed delete removed the wrong file or retained its rule");
            h.succeed();
        }finally{
            java.nio.file.Files.deleteIfExists(firstPath);java.nio.file.Files.deleteIfExists(secondPath);
        }
    }

    @GameTest(template="combat",batch="wild_clear_obstacles",timeoutTicks=80)
    public static void FullObstacleClearingCutsOnlyBlueprintSpaceAndPreservesProtectedBlocks(GameTestHelper h)throws Exception{
        var level=h.getLevel();var player=admin(h);var source=template(h,0,8);var name="obstacles_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(level.getServer(),name);java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(source.tag,path);
        try{
            var ground=h.absolutePos(new BlockPos(4,24,18));for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlockAndUpdate(ground.offset(x,0,z),Blocks.STONE.defaultBlockState());
            player.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()-23.5);player.setYRot(0);player.setXRot(0);
            var conditions=new CompoundTag();conditions.putBoolean("Sky",false);conditions.putBoolean("ClearObstacles",true);conditions.putInt("AbsenceSeconds",0);
            var data=WildEncounters.get(level.getServer());data.loadBlueprint(level,"",name,false);data.configure(level,name,0,1,30,false,false,FactionRegistry.WILDS,WildEncounters.Conditions.load(conditions));
            var basis=SpawnTemplateBridge.geometry(source.tag).bounds(new BlueprintSpawnPlacement.Destination(level.dimension().location(),Vec3.ZERO,null));
            double dx=ground.getX()+.5-basis.getCenter().x,dz=ground.getZ()+.5-basis.getCenter().z;BlockPos edge=null;
            for(int x=(int)Math.floor(basis.minX+dx);x<(int)Math.ceil(basis.maxX+dx);x++)for(int z=(int)Math.floor(basis.minZ+dz);z<(int)Math.ceil(basis.maxZ+dz);z++)if(x!=ground.getX()||z!=ground.getZ())edge=new BlockPos(x,ground.getY()+1,z);
            h.assertTrue(edge!=null,"Fixture has no non-center terrain column");var cut=edge;
            for(var protectedBlock:List.of(Blocks.BEDROCK,Blocks.CHEST)){
                level.setBlockAndUpdate(cut,protectedBlock.defaultBlockState());
                if(level.getBlockEntity(cut) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
                h.assertTrue(!data.previewOrTry(player,name,false)&&level.getBlockState(cut).is(protectedBlock),"Protected block was removed");
                var attempt=data.attemptPreviewOrSpawn(player,name,true);
                h.assertTrue(attempt.success()&&attempt.reason().equals("placement_protected")&&level.getBlockState(cut).is(protectedBlock),"Protected terrain hid the read-only preview or changed during preview");
                if(level.getBlockEntity(cut) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)h.assertTrue(chest.getItem(0).getCount()==3,"Clearing changed protected inventory");
            }
            level.setBlockAndUpdate(cut,Blocks.DIRT.defaultBlockState());var owned=BodyOwnership.get(level);owned.bind(cut,UUID.randomUUID());
            h.assertTrue(!data.previewOrTry(player,name,false)&&level.getBlockState(cut).is(Blocks.DIRT),"Life-owned terrain was cleared");owned.forget(cut);
            var limit=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("maximumWildClearedBlocks");int previous=limit.get();
            try{limit.set(0);h.assertTrue(!data.previewOrTry(player,name,false)&&level.getBlockState(cut).is(Blocks.DIRT),"Clear budget rejection changed terrain");}finally{limit.set(previous);}
            h.assertTrue(data.previewOrTry(player,name,true)&&level.getBlockState(cut).is(Blocks.DIRT),"Preview changed terrain");
            var outside=ground.offset(3,1,3);level.setBlockAndUpdate(outside,Blocks.OAK_PLANKS.defaultBlockState());
            h.assertTrue(data.previewOrTry(player,name,false),"Full clearing could not cut raised terrain");
            h.assertTrue(level.getBlockState(cut).isAir()&&level.getBlockState(cut.below()).is(Blocks.STONE)&&level.getBlockState(outside).is(Blocks.OAK_PLANKS),"Clearing exceeded bounds or left raised terrain");
            h.assertTrue(data.snapshot(level,name).getCompound("Conditions").getBoolean("ClearObstacles"),"New clearing flag did not save");h.succeed();
        }finally{java.nio.file.Files.deleteIfExists(path);}
    }
    @GameTest(template="combat",batch="wild_over_water",timeoutTicks=100)
    public static void LiquidBelowSpawnDoesNotBlockAirbornePlacementButOverlapStillRejects(GameTestHelper h)throws Exception{aboveLiquid(h,Blocks.WATER);}

    @GameTest(template="combat",batch="wild_over_lava",timeoutTicks=100)
    public static void LavaBelowSpawnDoesNotBlockAirbornePlacementButOverlapStillRejects(GameTestHelper h)throws Exception{aboveLiquid(h,Blocks.LAVA);}

    private static void aboveLiquid(GameTestHelper h,net.minecraft.world.level.block.Block liquid)throws Exception{
        var level=h.getLevel();var player=admin(h);var source=template(h,0,8);var name="over_liquid_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(level.getServer(),name);
        java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(source.tag,path);
        try{
            var ground=h.absolutePos(new BlockPos(4,24,18));for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlockAndUpdate(ground.offset(x,0,z),Blocks.STONE.defaultBlockState());
            player.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()-23.5);player.setYRot(0);player.setXRot(0);
            var data=WildEncounters.get(level.getServer());data.loadBlueprint(level,"",name,false);
            var basis=SpawnTemplateBridge.geometry(source.tag).bounds(new BlueprintSpawnPlacement.Destination(level.dimension().location(),Vec3.ZERO,null));
            double dx=ground.getX()+.5-basis.getCenter().x,dz=ground.getZ()+.5-basis.getCenter().z;BlockPos wet=null;
            for(int x=(int)Math.floor(basis.minX+dx);x<(int)Math.ceil(basis.maxX+dx);x++)for(int z=(int)Math.floor(basis.minZ+dz);z<(int)Math.ceil(basis.maxZ+dz);z++)if(x!=ground.getX()||z!=ground.getZ())wet=new BlockPos(x,ground.getY()+1,z);
            h.assertTrue(wet!=null,"Fixture has no off-center liquid column");level.setBlockAndUpdate(wet,liquid.defaultBlockState());
            for(boolean ignore:new boolean[]{false,true})for(boolean clearing:new boolean[]{false,true}){
                var conditions=new WildEncounters.Conditions(true,ignore,false,Set.of(),0,clearing);
                data.configure(level,name,8,0,30,false,false,FactionRegistry.WILDS,conditions);
                var preview=data.attemptPreviewOrSpawn(player,name,true);
                h.assertTrue(preview.success()&&preview.reason().isEmpty(),"Liquid below the placement blocked preview validation");
                h.assertTrue(level.getBlockState(wet).is(liquid)&&data.snapshot(level,name).getInt("ActiveCount")==0,"Preview changed liquid or spawned a structure");
            }
            data.configure(level,name,0,0,30,false,false,FactionRegistry.WILDS,new WildEncounters.Conditions(true,true,false,Set.of(),0,true));
            var overlap=data.attemptPreviewOrSpawn(player,name,false);
            h.assertTrue(!overlap.success()&&overlap.reason().equals("placement_fluid")&&level.getBlockState(wet).is(liquid),"Overlapping liquid was cleared or permitted");
            data.configure(level,name,8,1,30,false,false,FactionRegistry.WILDS,new WildEncounters.Conditions(true,true,false,Set.of(),0));
            h.assertTrue(data.attemptPreviewOrSpawn(player,name,false).success()&&data.snapshot(level,name).getInt("ActiveCount")==1,"Airborne structure did not actually spawn over liquid");
            h.assertTrue(level.getBlockState(wet).is(liquid),"Spawning changed liquid below the structure");h.succeed();
        }finally{java.nio.file.Files.deleteIfExists(path);}
    }

    @GameTest(template="combat",batch="wild_large_blueprint",timeoutTicks=160)
    public static void WildBlueprintLimitsAreIndependentConfigurableAndAcceptMoreThanOneHundredThousandBlocks(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();int count=131073;
        var blocks=new ArrayList<dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.BlockData>();
        for(int i=0;i<count;i++)blocks.add(new dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.BlockData(new BlockPos(i%128,i/(128*64),(i/128)%64),0,-1));
        var bounds=new dev.ryanhcode.sable.companion.math.BoundingBox3d(0,0,0,128,17,64);
        var part=new dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.SubLevelData(0,UUID.randomUUID(),new dev.ryanhcode.sable.companion.math.Pose3d(),new BoundingBox3i(0,0,0,127,16,63),bounds,BlockPos.ZERO,List.of(Blocks.STONE.defaultBlockState()),blocks,List.of(),List.of(),new CompoundTag(),"large");
        var blueprint=new dev.rew1nd.sableschematicapi.blueprint.SableBlueprint(new org.joml.Vector3d(),bounds,List.of(part),new CompoundTag());
        var name="large_wild_"+UUID.randomUUID();var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(blueprint.save(),path);
        var config=dev.sablelife.SableLifeServerConfig.SPEC;
        var arena=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)config.getValues().get("arenaCaptureBlocks");int oldArena=arena.get();
        var capacity=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)config.getValues().get("maximumWildBlueprintBlocks");int oldCapacity=capacity.get();
        var size=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)config.getValues().get("maximumWildBlueprintHorizontalSize");int oldSize=size.get();
        var bytes=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)config.getValues().get("maximumWildBlueprintBytes");int oldBytes=bytes.get();
        try{
            h.assertTrue(oldCapacity==262144&&oldSize==256,"New wild defaults are incorrect");arena.set(32768);
            h.assertTrue(SpawnTemplateBridge.readWild(server,name).blocks()==count,"Wild template was blocked by the old Arena block cap");
            var data=WildEncounters.get(server);data.loadBlueprint(level,"",name,false);
            h.assertTrue(data.snapshot(level,name).getBoolean("Configured"),"Large and wider-than-64 blueprint could not load");
            capacity.set(count-1);boolean rejected=false;try{data.configure(level,name,0,0,30,true,false);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Cached template ignored lowered block cap");capacity.set(oldCapacity);
            size.set(64);rejected=false;try{data.configure(level,name,0,0,30,true,false);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Cached template ignored lowered footprint cap");size.set(oldSize);
            bytes.set(1048576);rejected=false;try{SpawnTemplateBridge.readWild(server,name);}catch(RuntimeException|java.io.IOException expected){rejected=true;}h.assertTrue(rejected,"Wild data cap was ignored");
            h.assertTrue(dev.sablelife.SableLifeServerConfig.maximumBlueprintProjectionBlocks()==8192,"Placement capacity changed projection limit");h.succeed();
        }finally{arena.set(oldArena);capacity.set(oldCapacity);size.set(oldSize);bytes.set(oldBytes);java.nio.file.Files.deleteIfExists(path);}
    }

    @GameTest(template="combat",batch="wild_many_parts",timeoutTicks=100)
    public static void WildPartCapacitySupportsNativePlacementAndBoundaryPreviewBeyondThirtyTwoParts(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();
        var parts=new ArrayList<dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.SubLevelData>();
        var localBounds=new BoundingBox3i(0,0,0,0,0,0);
        var partBounds=new dev.ryanhcode.sable.companion.math.BoundingBox3d(0,0,0,1,1,1);
        for(int i=0;i<33;i++){
            var pose=new dev.ryanhcode.sable.companion.math.Pose3d();pose.position().set(i%6,0,i/6);
            parts.add(new dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.SubLevelData(i,UUID.randomUUID(),pose,localBounds,partBounds,BlockPos.ZERO,List.of(Blocks.STONE.defaultBlockState()),List.of(new dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.BlockData(BlockPos.ZERO,0,-1)),List.of(),List.of(),new CompoundTag(),"part_"+i));
        }
        var blueprint=new dev.rew1nd.sableschematicapi.blueprint.SableBlueprint(new org.joml.Vector3d(),new dev.ryanhcode.sable.companion.math.BoundingBox3d(0,0,0,6,1,6),parts,new CompoundTag());
        var tag=blueprint.save();var name="multipart_wild_"+UUID.randomUUID();var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);
        java.nio.file.Files.createDirectories(path.getParent());NbtIo.writeCompressed(tag,path);
        var capacity=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("maximumWildBlueprintParts");int previous=capacity.get();
        dev.sablelife.ownership.ArenaRespawns.Build build=null;
        try{
            h.assertTrue(previous==128,"Wild part default is incorrect");var info=SpawnTemplateBridge.readWild(server,name);
            h.assertTrue(info.parts()==33&&info.blocks()==33&&!info.canProject(),"Multipart metadata or boundary-preview eligibility is incorrect");
            boolean rejected=false;try{SpawnTemplateBridge.inspect(tag);}catch(ArenaRespawnLimits.Exceeded expected){rejected=expected.kind.equals("parts");}h.assertTrue(rejected,"Wild capacity changed ordinary Arena admission");
            var data=WildEncounters.get(server);data.loadBlueprint(level,"",name,false);
            capacity.set(32);rejected=false;try{SpawnTemplateBridge.readWild(server,name);}catch(IllegalArgumentException expected){rejected=expected.getMessage().contains("33 > 32");}h.assertTrue(rejected,"File admission ignored the configured part cap");
            rejected=false;try{data.configure(level,name,0,0,30,true,false);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Cached template ignored lowered part cap");capacity.set(previous);
            var ground=h.absolutePos(new BlockPos(4,24,18));for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)level.setBlockAndUpdate(ground.offset(x,0,z),Blocks.STONE.defaultBlockState());
            var player=admin(h);player.setYRot(0);player.setXRot(0);player.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()-23.5);
            data.configure(level,name,0,0,30,false,false,FactionRegistry.WILDS,new WildEncounters.Conditions(false,false,false,Set.of(),0));
            h.assertTrue(data.previewOrTry(player,name,true),"More than 32 parts could not use boundary preview");
            build=PhotomancyRespawnBridge.createSpawn(info.template(),new CompoundTag(),h.absoluteVec(new Vec3(4,40,4)),null);
            h.assertTrue(build.step(level).equals("DONE")&&build.bodies().size()==33,"Native placer did not restore all 33 parts");h.succeed();
        }finally{
            capacity.set(previous);java.nio.file.Files.deleteIfExists(path);
            if(build!=null){var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);for(var id:build.bodies()){var body=container.getSubLevel(id);if(body!=null)dev.sablelife.compat.StructureRemovalApi.remove(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)body);}}
        }
    }

    @GameTest(template="combat",batch="wild_forest_self_clear")
    public static void ForestPlacementCanLeaveTreesForTheCoreToBreak(GameTestHelper h) throws Exception {
        var level=h.getLevel();var server=level.getServer();var source=template(h,1,8);var player=admin(h);
        String name="forest_self_"+UUID.randomUUID();
        var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);
        java.nio.file.Files.createDirectories(path.getParent());
        try{
            var ground=h.absolutePos(new BlockPos(4,24,18));
            player.setPos(ground.getX()+.5,ground.getY()+3,ground.getZ()-23.5);player.setYRot(0);player.setXRot(0);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setBlockAndUpdate(ground.offset(x,0,z),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(ground.above(),Blocks.OAK_LOG.defaultBlockState());
            var encounters=WildEncounters.get(server);
            var conditions=new WildEncounters.Conditions(true,true,false,Set.of());
            NbtIo.writeCompressed(source.tag,path);
            encounters.configure(level,name,0,0,30,false,false,FactionRegistry.WILDS,conditions);
            h.assertTrue(!encounters.previewOrTry(player,name,false),"A core without terrain breaking spawned inside a log");
            encounters.removeRule(level,name);
            h.assertTrue(source.core.editCore(player,"crushEnabled",1,""),"Could not enable the blueprint's terrain breaking");
            var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(level,source.core.getBlockPos());
            var position=body.logicalPose().position();
            var updated=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level,
                    new Vec3(position.x(),position.y(),position.z()),List.of(body.getUniqueId())).save();
            NbtIo.writeCompressed(updated,path);
            encounters.configure(level,name,0,0,30,false,false,FactionRegistry.WILDS,conditions);
            h.assertTrue(encounters.previewOrTry(player,name,false),"Terrain-breaking blueprint could not spawn in forest");
            h.assertTrue(level.getBlockState(ground.above()).is(Blocks.OAK_LOG),"Spawner pre-cleared a tree with pre-clearing off");
            h.succeed();
        }finally{java.nio.file.Files.deleteIfExists(path);}
    }
}
