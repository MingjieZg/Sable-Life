package dev.sablelife.ownership;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.compat.StructureRemovalApi;
import dev.sablelife.compat.photomancy.PhotomancyRespawnBridge;
import dev.sablelife.compat.photomancy.SpawnTemplateBridge;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-local encounters, tracked per rule even while their chunks are unloaded. */
public final class WildEncounters extends SavedData {
    private static final int MAX_RULES = 64;
    public static final int MAX_PER_RULE = 64;
    public static final int MAX_ANNOUNCEMENT_LENGTH = 256;
    public static final int DEFAULT_ABSENCE_SECONDS = 180;
    public static final ResourceLocation OVERWORLD_BIOMES=ResourceLocation.fromNamespaceAndPath("sablelife","overworld_biomes");
    private static final int PRESENCE_RADIUS = 128;
    private record Key(ResourceLocation dimension, String file) {}
    private record Placement(Vec3 origin, AABB bounds, Vec3 forward, Map<BlockPos,BlockState> clearing) {}
    public record Density(int retrySeconds,int attempts,int minDistance,int maxDistance) {
        public static final Density DEFAULT=new Density(5,1,48,96);
        public Density {
            if(retrySeconds<1||retrySeconds>3600||attempts<1||attempts>8||minDistance<16||minDistance>512||maxDistance<minDistance||maxDistance>1024)
                throw new IllegalArgumentException("Invalid natural-spawn density settings");
        }
        private CompoundTag save(){var tag=new CompoundTag();tag.putInt("RetrySeconds",retrySeconds);tag.putInt("SearchAttempts",attempts);tag.putInt("MinDistance",minDistance);tag.putInt("MaxDistance",maxDistance);return tag;}
        private static Density load(CompoundTag tag){
            int min=tag.contains("MinDistance")?Math.clamp(tag.getInt("MinDistance"),16,512):48;
            return new Density(tag.contains("RetrySeconds")?Math.clamp(tag.getInt("RetrySeconds"),1,3600):5,
                    tag.contains("SearchAttempts")?Math.clamp(tag.getInt("SearchAttempts"),1,8):1,min,
                    tag.contains("MaxDistance")?Math.clamp(tag.getInt("MaxDistance"),min,1024):Math.max(min,96));
        }
    }
    public record Conditions(boolean sky, boolean ignoreBounds, boolean clearTrees, Set<ResourceLocation> biomes, int absenceSeconds,boolean clearObstacles) {
        public Conditions(boolean sky, boolean ignoreBounds, boolean clearTrees, Set<ResourceLocation> biomes,int absenceSeconds) {
            this(sky,ignoreBounds,clearTrees,biomes,absenceSeconds,false);
        }
        public Conditions(boolean sky, boolean ignoreBounds, boolean clearTrees, Set<ResourceLocation> biomes) {
            this(sky,ignoreBounds,clearTrees,biomes,DEFAULT_ABSENCE_SECONDS);
        }
        public Conditions { biomes=Set.copyOf(biomes); absenceSeconds=Math.clamp(absenceSeconds,0,86400); }
        private CompoundTag save() {
            var tag=new CompoundTag();
            tag.putBoolean("Sky",sky);tag.putBoolean("IgnoreBounds",ignoreBounds);tag.putBoolean("ClearTrees",clearTrees);
            tag.putBoolean("ClearObstacles",clearObstacles);
            var names=new ListTag();biomes.stream().sorted().forEach(b->names.add(net.minecraft.nbt.StringTag.valueOf(b.toString())));
            tag.put("Biomes",names);
            tag.putInt("AbsenceSeconds",absenceSeconds);
            return tag;
        }
        public static Conditions load(CompoundTag tag) {
            var names=tag.getList("Biomes",8).stream().limit(256).map(t->ResourceLocation.tryParse(t.getAsString()))
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            return new Conditions(!tag.contains("Sky")||tag.getBoolean("Sky"),tag.getBoolean("IgnoreBounds"),tag.getBoolean("ClearTrees"),names,
                    tag.contains("AbsenceSeconds")?tag.getInt("AbsenceSeconds"):DEFAULT_ABSENCE_SECONDS,tag.getBoolean("ClearObstacles"));
        }
    }
    private static final class Rule {
        int gap, lifetime, cooldown = 120, maxActive = 1;
        boolean enabled, activateCore=true, clearing, releaseAfterSpawn;
        String announcement="";
        boolean announcementCoordinates;
        Density density=Density.DEFAULT;
        final Map<UUID,Encounter> active = new LinkedHashMap<>();
        UUID faction = dev.sablelife.heart.FactionRegistry.WILDS;
        Conditions conditions = Conditions.load(new CompoundTag());
        long next;
        transient long retryAfter;
        transient SpawnTemplateBridge.Info template;
        transient BlueprintSpawnPlacement.Geometry geometry;
        transient List<AABB> occupied;
        transient dev.sablelife.heart.TerrainBreakSettings terrain;
    }
    private static final class Encounter {
        int absenceSeconds;
        long expires, absentSince=-1;
        boolean expiring, absenceDue;
        BlockPos lastPosition;
        ResourceLocation lastDimension;
        final Set<UUID> bodies=new java.util.LinkedHashSet<>();
    }

    private final Map<Key, Rule> rules = new LinkedHashMap<>();
    private int turn;

    public static WildEncounters get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(WildEncounters::new, WildEncounters::load), "sablelife_wild_encounters");
    }

    public java.util.List<String> configuredFiles(ServerLevel level) {
        return rules.keySet().stream().filter(key -> key.dimension().equals(level.dimension().location())).map(Key::file).toList();
    }

    public boolean removeRule(ServerLevel level, String file) {
        var key = new Key(level.dimension().location(), file);
        var rule = rules.get(key);
        if (rule == null || !rule.active.isEmpty()) return false;
        rules.remove(key);
        setDirty();
        return true;
    }

    /** Persist the request; leave unloaded objects tracked until normal loading permits cleanup. */
    public boolean clearCurrent(ServerLevel level,String file){
        var rule=rules.get(new Key(level.dimension().location(),file));
        if(rule==null||rule.active.isEmpty()||rule.clearing)return false;
        rule.clearing=true;
        setDirty();
        return true;
    }

    public boolean deleteBlueprint(MinecraftServer server, String file) throws IOException {
        if(rules.entrySet().stream().anyMatch(e->e.getKey().file().equals(file)&&!e.getValue().active.isEmpty()))return false;
        SpawnTemplateBridge.delete(server,file);
        if(rules.keySet().removeIf(key->key.file().equals(file)))setDirty();
        return true;
    }

    public static Set<ResourceLocation> biomes(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.BIOME).keySet();
    }

    public CompoundTag snapshot(ServerLevel level, String file) {
        var rule = rules.get(new Key(level.dimension().location(), file));
        var tag = new CompoundTag();
        tag.putInt("Cooldown", 120);
        tag.putInt("MaxActive",1);
        tag.putString("SpawnAnnouncement",rule==null?"":rule.announcement);
        tag.putBoolean("SpawnAnnouncementCoordinates",rule!=null&&rule.announcementCoordinates&&!rule.announcement.isEmpty());
        tag.merge((rule==null?Density.DEFAULT:rule.density).save());
        tag.putInt("ServerPopulationLimit",SableLifeServerConfig.maximumWildEncounters());
        tag.putInt("NearbyPopulationLimit",SableLifeServerConfig.maximumNearbyWildEncounters());
        tag.putUUID("Faction", faction(level, rule));
        tag.put("Conditions",rule==null?new CompoundTag():rule.conditions.save());
        if (rule != null) {
            tag.putBoolean("ActivateCore",rule.activateCore);
            tag.putBoolean("Configured", true);
            tag.putInt("Gap", rule.gap);
            tag.putInt("Lifetime", rule.lifetime);
            tag.putInt("Cooldown", rule.cooldown);
            tag.putBoolean("Enabled", rule.enabled);
            tag.putBoolean("Active", !rule.active.isEmpty());
            tag.putInt("ActiveCount",rule.active.size());
            tag.putBoolean("Clearing",rule.clearing);
            tag.putBoolean("ReleaseAfterSpawn",rule.releaseAfterSpawn);
            tag.putInt("MaxActive",rule.maxActive);
        }
        return tag;
    }

    /** Plain chat text only; an empty message disables the announcement. */
    public void setAnnouncement(ServerLevel level,String file,String message){
        var rule=rules.get(new Key(level.dimension().location(),file));
        if(rule==null)return;
        String clean=cleanAnnouncement(message);
        if(!rule.announcement.equals(clean)){rule.announcement=clean;setDirty();}
        if(clean.isEmpty()&&rule.announcementCoordinates){rule.announcementCoordinates=false;setDirty();}
    }

    public void setAnnouncementCoordinates(ServerLevel level,String file,boolean enabled){
        var rule=rules.get(new Key(level.dimension().location(),file));
        if(rule==null)return;
        boolean value=enabled&&!rule.announcement.isEmpty();
        if(rule.announcementCoordinates!=value){rule.announcementCoordinates=value;setDirty();}
    }

    private static String cleanAnnouncement(String message){
        return message.codePoints().filter(c->!Character.isISOControl(c)&&c!=0xA7)
                .limit(MAX_ANNOUNCEMENT_LENGTH).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString().strip();
    }

    public void loadBlueprint(ServerLevel level,String previous,String file,boolean activateCore) throws IOException{
        var dimension=level.dimension().location();
        var oldKey=new Key(dimension,previous);
        var key=new Key(dimension,file);
        var old=rules.get(oldKey);
        if(!previous.isEmpty()&&old==null)throw new IllegalArgumentException("Rule no longer exists");
        if(rules.containsKey(key)&&!key.equals(oldKey))throw new IllegalArgumentException("Blueprint already has a rule in this dimension");
        if(old==null&&rules.size()>=MAX_RULES)throw new IllegalArgumentException("Wild encounter rule limit reached");
        if(old!=null&&!old.active.isEmpty())throw new IllegalArgumentException("This rule still has active encounters");
        var validated=new Rule();
        validated.activateCore=activateCore;
        template(level,key,validated);
        var rule=old==null?validated:old;
        rule.activateCore=activateCore;
        rule.enabled=false;
        rule.next=0;
        rule.template=validated.template;
        rule.geometry=validated.geometry;
        rule.occupied=null;
        rule.terrain=null;
        if(!oldKey.equals(key))rules.remove(oldKey);
        rules.put(key,rule);
        setDirty();
    }

    /** The legacy ignoredOnce argument is retained for saved-menu/API compatibility and has no effect. */
    public void configure(ServerLevel level, String file, int gap, int lifetime, int cooldown, boolean enabled, boolean ignoredOnce) throws IOException {
        configure(level, file, gap, lifetime, cooldown, enabled, ignoredOnce, faction(level, rules.get(new Key(level.dimension().location(), file))));
    }

    private static UUID faction(ServerLevel level, Rule rule) {
        var registry = dev.sablelife.heart.FactionRegistry.get(level.getServer());
        return rule != null && registry.known(rule.faction) ? rule.faction : dev.sablelife.heart.FactionRegistry.WILDS;
    }

    public void configure(ServerLevel level, String file, int gap, int lifetime, int cooldown, boolean enabled, boolean ignoredOnce, UUID faction) throws IOException {
        var rule=rules.get(new Key(level.dimension().location(),file));
        configure(level,file,gap,lifetime,cooldown,enabled,ignoredOnce,faction,rule==null?Conditions.load(new CompoundTag()):rule.conditions);
    }

    public void configure(ServerLevel level, String file, int gap, int lifetime, int cooldown, boolean enabled, boolean ignoredOnce, UUID faction, Conditions conditions) throws IOException {
        var rule=rules.get(new Key(level.dimension().location(),file));
        configure(level,file,gap,lifetime,cooldown,enabled,ignoredOnce,faction,conditions,rule==null?1:rule.maxActive);
    }

    public void configure(ServerLevel level, String file, int gap, int lifetime, int cooldown, boolean enabled, boolean ignoredOnce, UUID faction, Conditions conditions,int maxActive) throws IOException {
        var existing=rules.get(new Key(level.dimension().location(),file));
        configure(level,file,gap,lifetime,cooldown,enabled,ignoredOnce,faction,conditions,maxActive,existing!=null&&existing.releaseAfterSpawn);
    }

    public void configure(ServerLevel level, String file, int gap, int lifetime, int cooldown, boolean enabled, boolean ignoredOnce, UUID faction, Conditions conditions,int maxActive,boolean releaseAfterSpawn) throws IOException {
        var existing=rules.get(new Key(level.dimension().location(),file));
        configure(level,file,gap,lifetime,cooldown,enabled,ignoredOnce,faction,conditions,maxActive,releaseAfterSpawn,existing==null?Density.DEFAULT:existing.density);
    }

    public void configure(ServerLevel level, String file, int gap, int lifetime, int cooldown, boolean enabled, boolean ignoredOnce, UUID faction, Conditions conditions,int maxActive,boolean releaseAfterSpawn,Density density) throws IOException {
        java.util.Objects.requireNonNull(density);
        if (gap < 0 || gap > 128 || lifetime < 0 || lifetime > 86400 || cooldown < 10 || cooldown > 86400)
            throw new IllegalArgumentException("Value outside allowed range");
        if(maxActive<1||maxActive>MAX_PER_RULE)throw new IllegalArgumentException("Invalid encounter count");
        if (!dev.sablelife.heart.FactionRegistry.get(level.getServer()).known(faction))
            throw new IllegalArgumentException("Faction no longer exists");
        var registered=biomes(level);
        if(conditions.biomes().size()>256||conditions.biomes().stream().anyMatch(id->!id.equals(OVERWORLD_BIOMES)&&!registered.contains(id)))
            throw new IllegalArgumentException("Unknown biome");
        var key = new Key(level.dimension().location(), file);
        var rule = rules.get(key);
        if (rule == null && rules.size() >= MAX_RULES) throw new IllegalArgumentException("Wild encounter rule limit reached");
        if (rule == null) rule = new Rule();
        if (enabled || !rules.containsKey(key)) template(level, key, rule);
        rule.gap = gap;
        rule.lifetime = lifetime;
        rule.cooldown = cooldown;
        rule.enabled = enabled;
        rule.faction = faction;
        rule.conditions = conditions;
        rule.maxActive=maxActive;
        rule.releaseAfterSpawn=releaseAfterSpawn;
        rule.density=density;
        rules.put(key, rule);
        setDirty();
    }

    private static void template(ServerLevel level, Key key, Rule rule) throws IOException {
        var info = rule.template!=null?rule.template:SpawnTemplateBridge.readWild(level.getServer(), key.file());
        if(rule.activateCore&&!info.canActivate())throw new IllegalArgumentException("Activation requires exactly one Life Core and no Redstone Core");
        if(info.blocks()>SableLifeServerConfig.maximumWildBlueprintBlocks())throw new IllegalArgumentException("Wild blueprint has "+info.blocks()+" blocks; server limit is "+SableLifeServerConfig.maximumWildBlueprintBlocks());
        if(info.parts()>SableLifeServerConfig.maximumWildBlueprintParts())throw new IllegalArgumentException("Wild blueprint has "+info.parts()+" parts; server limit is "+SableLifeServerConfig.maximumWildBlueprintParts());
        if (info.template().sizeInBytes() > SableLifeServerConfig.maximumWildBlueprintBytes()) throw new IllegalArgumentException("Wild blueprint exceeds server data limit of "+SableLifeServerConfig.maximumWildBlueprintBytes()+" bytes");
        var geometry = rule.geometry!=null?rule.geometry:SpawnTemplateBridge.geometry(info.template());
        var bounds = geometry.bounds(new BlueprintSpawnPlacement.Destination(key.dimension(), Vec3.ZERO, null));
        int size=SableLifeServerConfig.maximumWildBlueprintHorizontalSize();
        if (bounds.getXsize() > size || bounds.getZsize() > size) throw new IllegalArgumentException("Wild blueprint footprint exceeds server limit of "+size+" by "+size+" blocks");
        rule.geometry = geometry;
        rule.template = info;
    }

    private static boolean tree(BlockState state) { return state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES); }

    private static Placement aboveGround(ServerLevel level, Rule rule, int x, int z) {
        return aboveGround(level,rule,x,z,null);
    }

    private static Placement rejected(java.util.function.Consumer<String> failure,String reason) {
        if(failure!=null)failure.accept("placement_"+reason);
        return null;
    }

    private static Placement aboveGround(ServerLevel level, Rule rule, int x, int z,java.util.function.Consumer<String> failure) {
        var geometry=rule.geometry;
        var conditions=rule.conditions;
        boolean clearTrees=conditions.ignoreBounds()&&conditions.clearTrees();
        boolean clearObstacles=conditions.clearObstacles();
        if(conditions.ignoreBounds()&&!clearTrees&&!clearObstacles&&rule.terrain==null)rule.terrain=SpawnTemplateBridge.terrainSettings(rule.template.template());
        boolean letCoreBreakTrees=rule.activateCore&&conditions.ignoreBounds()&&!clearTrees&&!clearObstacles&&rule.terrain.enabled();
        if(conditions.sky()&&!level.dimensionType().hasSkyLight())return rejected(failure,"sky");
        var dimension = level.dimension().location();
        var basis = geometry.bounds(new BlueprintSpawnPlacement.Destination(dimension, Vec3.ZERO, null));
        int size=SableLifeServerConfig.maximumWildBlueprintHorizontalSize();
        if (basis.getXsize() > size || basis.getZsize() > size) return rejected(failure,"size");
        double dx = x + .5 - basis.getCenter().x, dz = z + .5 - basis.getCenter().z;
        int minX = (int) Math.floor(basis.minX + dx), maxX = (int) Math.ceil(basis.maxX + dx) - 1;
        int minZ = (int) Math.floor(basis.minZ + dz), maxZ = (int) Math.ceil(basis.maxZ + dz) - 1;
        String baseProtection=WildWorkbenchProtection.check(level,new AABB(basis.minX+dx,0,basis.minZ+dz,basis.maxX+dx,1,basis.maxZ+dz));
        if(!baseProtection.isEmpty())return rejected(failure,baseProtection);
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) if (!level.hasChunk(cx, cz)) return rejected(failure,"chunks");
        int ground = level.getMinBuildHeight();
        int centerGround=ground;
        int budget=SableLifeServerConfig.wildPlacementScanVoxels();
        var cursor=new BlockPos.MutableBlockPos();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                while(top>level.getMinBuildHeight()){
                    if(--budget<0)return rejected(failure,"budget");
                    cursor.set(bx,top-1,bz);
                    var state=level.getBlockState(cursor);
                    // A liquid surface may be below an airborne spawn. Reject liquids only
                    // when they overlap the final placement volumes, not while finding height.
                    if(!state.getFluidState().isEmpty())break;
                    if(!tree(state)&&state.isFaceSturdy(level,cursor,net.minecraft.core.Direction.UP))break;
                    top--;
                }
                if(top<=level.getMinBuildHeight())return rejected(failure,"ground");
                ground = Math.max(ground, top);
                if(bx==x&&bz==z)centerGround=top;
            }
        }
        var biome=level.getBiome(new BlockPos(x,centerGround,z));
        boolean allOverworld=level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)&&conditions.biomes().contains(OVERWORLD_BIOMES)
                &&!biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN);
        if(!allOverworld&&!conditions.biomes().isEmpty()&&!biome.unwrapKey()
                .map(ResourceKey::location).filter(conditions.biomes()::contains).isPresent())return rejected(failure,"biome");
        var origin = new Vec3(dx, (clearObstacles?centerGround:ground) + rule.gap - basis.minY, dz);
        var bounds = basis.move(origin);
        if (bounds.maxY > level.getMaxBuildHeight()) return rejected(failure,"height");
        if (!level.getWorldBorder().isWithinBounds(bounds)) return rejected(failure,"border");
        if(conditions.sky()){
            int ceiling=(int)Math.ceil(bounds.maxY);
            for(int bx=minX;bx<=maxX;bx++)for(int bz=minZ;bz<=maxZ;bz++){
                int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING,bx,bz);
                for(int by=top-1;by>=ceiling;by--){
                    if(--budget<0)return rejected(failure,"budget");
                    var state=level.getBlockState(cursor.set(bx,by,bz));
                    if(!state.isAir()&&!((clearObstacles||clearTrees||letCoreBreakTrees)&&tree(state))&&!state.getCollisionShape(level,cursor).isEmpty())return rejected(failure,"sky");
                }
            }
        }
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return rejected(failure,"unavailable");
        for (var body : container.getAllSubLevels()) {
            if (body.isRemoved()) continue;
            var b = body.boundingBox();
            if (b != null && bounds.intersects(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) return rejected(failure,"structure");
        }
        var clearing=new LinkedHashMap<BlockPos,BlockState>();
        if(!clearObstacles&&conditions.ignoreBounds()&&rule.occupied==null)rule.occupied=SpawnTemplateBridge.occupiedBounds(rule.template.template());
        var volumes=!clearObstacles&&conditions.ignoreBounds()?rule.occupied:List.of(basis);
        var ownership=BodyOwnership.get(level);
        for(var volume:volumes){
            var occupied=volume.move(origin);
            int x0=(int)Math.floor(occupied.minX+1e-7),y0=(int)Math.floor(occupied.minY+1e-7),z0=(int)Math.floor(occupied.minZ+1e-7);
            int x1=(int)Math.ceil(occupied.maxX-1e-7),y1=(int)Math.ceil(occupied.maxY-1e-7),z1=(int)Math.ceil(occupied.maxZ-1e-7);
            for(int bx=x0;bx<x1;bx++)for(int bz=z0;bz<z1;bz++)for(int by=y0;by<y1;by++){
                if(--budget<0)return rejected(failure,"budget");
                cursor.set(bx,by,bz);
                var state=level.getBlockState(cursor);
                if(state.isAir())continue;
                if(!state.getFluidState().isEmpty())return rejected(failure,"fluid");
                if(clearObstacles){
                    if(ownership.owner(cursor)!=null||state.hasBlockEntity()||state.getDestroySpeed(level,cursor)<0)return rejected(failure,"protected");
                    clearing.putIfAbsent(cursor.immutable(),state);
                    if(clearing.size()>SableLifeServerConfig.maximumWildClearedBlocks())return rejected(failure,"clear_budget");
                    continue;
                }
                if(tree(state)&&ownership.owner(cursor)==null&&!state.hasBlockEntity()){
                    if(clearTrees){clearing.putIfAbsent(cursor.immutable(),state);if(clearing.size()>SableLifeServerConfig.maximumWildClearedBlocks())return rejected(failure,"clear_budget");continue;}
                    if(letCoreBreakTrees&&!rule.terrain.protects(state)&&state.getDestroySpeed(level,cursor)>=0)continue;
                }
                if(!state.getCollisionShape(level,cursor).isEmpty())return rejected(failure,"obstacle");
            }
        }
        return new Placement(origin, bounds, geometry.fixedForward(), clearing);
    }

    public record Attempt(boolean success,String reason) {}

    public boolean previewOrTry(ServerPlayer player, String file, boolean preview) throws IOException {
        return attemptPreviewOrSpawn(player,file,preview).success();
    }

    public Attempt attemptPreviewOrSpawn(ServerPlayer player, String file, boolean preview) throws IOException {
        if (!SableLifeServerConfig.wildEncountersEnabled() || !BlueprintSpawns.available()) return new Attempt(false,"placement_unavailable");
        var level = player.serverLevel();
        var key = new Key(level.dimension().location(), file);
        var rule = rules.get(key);
        if (rule == null) throw new IllegalArgumentException("Save the rule before previewing or spawning");
        template(level, key, rule);
        var bounds = rule.geometry.bounds(new BlueprintSpawnPlacement.Destination(key.dimension(), Vec3.ZERO, null));
        double distance = Math.max(24, Math.ceil(Math.hypot(bounds.getXsize(), bounds.getZsize()) / 2 + 8));
        var point = SableBodyProbe.toWorld(level, player.position()).add(Vec3.directionFromRotation(0, player.getYRot()).scale(distance));
        int x=BlockPos.containing(point).getX(),z=BlockPos.containing(point).getZ();
        String[] failure={""};
        var placement = aboveGround(level, rule, x, z,reason->failure[0]=reason);
        if (preview) {
            if(placement==null){
                // Read-only preview must not require loaded or safe placement terrain.
                // When validation fails, use loaded center-column height or the viewer's height.
                double ground=level.hasChunk(x>>4,z>>4)?level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z):SableBodyProbe.toWorld(level,player.position()).y;
                var origin=new Vec3(x+.5-bounds.getCenter().x,ground+rule.gap-bounds.minY,z+.5-bounds.getCenter().z);
                placement=new Placement(origin,bounds.move(origin),rule.geometry.fixedForward(),Map.of());
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("wild.sablelife.preview_blocked",
                        net.minecraft.network.chat.Component.translatable("wild.sablelife."+failure[0])).withStyle(net.minecraft.ChatFormatting.YELLOW),false);
            }
            var b = placement.bounds();
            var anchor=new Vec3(b.getCenter().x,b.minY+.05,b.getCenter().z);
            UUID projection=rule.template.canProject()?UUID.randomUUID():null;
            if(projection!=null)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    SpawnTemplateBridge.projection(rule.template.template(),projection,false,placement.origin().subtract(anchor)));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new dev.sablelife.module.ModuleNetworking.BlueprintPreview(
                    key.dimension(), new Vec3(b.minX,b.minY,b.minZ), new Vec3(b.maxX,b.maxY,b.maxZ),
                    anchor,placement.forward(),100,projection,new org.joml.Quaternionf()));
            placement.clearing().keySet().stream().limit(128).forEach(pos->level.sendParticles(player,
                    net.minecraft.core.particles.ParticleTypes.FLAME,true,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,1,0,0,0,0));
            return new Attempt(true,failure[0]);
        }
        if(placement==null)return new Attempt(false,failure[0]);
        String blocked=spawnBlocker(level,rule,placement);
        if(!blocked.isEmpty())return new Attempt(false,blocked);
        return spawn(level,rule,placement)?new Attempt(true,""):new Attempt(false,"placement_failed");
    }

    private String spawnBlocker(ServerLevel level,Rule rule,Placement placement) {
        if(rule.clearing)return "placement_clearing";
        String baseProtection=WildWorkbenchProtection.check(level,placement.bounds());
        if(!baseProtection.isEmpty())return "placement_"+baseProtection;
        if(!rule.releaseAfterSpawn&&rule.active.size()>=rule.maxActive)return "placement_rule_cap";
        if(!globalSlot())return "placement_global_cap";
        if(!nearbySlot(level,placement.bounds().getCenter()))return "placement_nearby_cap";
        for(var player:level.players())if(placement.bounds().inflate(4).contains(SableBodyProbe.toWorld(level,player.position())))return "placement_player";
        return "";
    }

    private boolean spawn(ServerLevel level,Rule rule,Placement placement) {
        if (!spawnBlocker(level,rule,placement).isEmpty()) return false;
        var build = PhotomancyRespawnBridge.createSpawn(rule.template.template(), new CompoundTag(), placement.origin(), null);
        var cleared=new LinkedHashMap<BlockPos,BlockState>();
        int flags=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE;
        try {
            for(var entry:placement.clearing().entrySet()){
                if(level.getBlockState(entry.getKey())!=entry.getValue()
                        ||!level.setBlock(entry.getKey(),Blocks.AIR.defaultBlockState(),flags))throw new IllegalStateException("Obstruction changed before spawning");
                cleared.put(entry.getKey(),entry.getValue());
            }
            if (!build.step(level).equals("DONE")) throw new IllegalStateException("Incomplete placement");
            var core=rule.activateCore?build.heart(level):null;
            if(rule.activateCore){
                if(core==null)throw new IllegalStateException("Cannot find blueprint core");
                core.relations().faction(faction(level,rule));
                if(!core.activateForRespawn())throw new IllegalStateException("Cannot activate blueprint core");
                build.captureOwnership(core);
            }
            long now = level.getServer().overworld().getGameTime();
            var encounter=new Encounter();
            encounter.expires=rule.lifetime==0?0:now+rule.lifetime*20L;
            encounter.absenceSeconds=rule.conditions.absenceSeconds();
            if(core==null){
                encounter.bodies.addAll(build.bodies());
                if(encounter.bodies.isEmpty())throw new IllegalStateException("Cannot find placed structures");
                updatePosition(encounter,level,placement.bounds().getCenter());
            }else updatePosition(encounter,level,SableBodyProbe.toWorld(level,core.getBlockPos().getCenter()));
            if(!rule.releaseAfterSpawn)rule.active.put(core==null?UUID.randomUUID():core.creatureId(),encounter);
            rule.next=now+rule.cooldown*20L;
            rule.retryAfter=0;
            setDirty();
        } catch (RuntimeException failure) {
            var container = SubLevelContainer.getContainer(level);
            for (var id : build.bodies()) {
                try {
                    if (container != null && container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) StructureRemovalApi.remove(level, body);
                } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            cleared.forEach((pos,state)->{if(level.getBlockState(pos).isAir())level.setBlock(pos,state,flags);});
            LogUtils.getLogger().warn("Wild encounter placement failed", failure);
            return false;
        }
        if(!rule.announcement.isEmpty()){
            var message=dev.sablelife.heart.CreatureNames.formatted(rule.announcement).copy();
            if(rule.announcementCoordinates){
                var center=placement.bounds().getCenter();
                var pos=BlockPos.containing(center.x,placement.bounds().minY,center.z);
                message.append(net.minecraft.network.chat.Component.literal(" [X: "+pos.getX()+", Y: "+pos.getY()+", Z: "+pos.getZ()+"]")
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
            }
            level.getServer().getPlayerList().broadcastSystemMessage(message,false);
        }
        return true;
    }

    public static void tick(ServerTickEvent.Post event) {
        if (!SableLifeServerConfig.wildEncountersEnabled()) return;
        var server = event.getServer();
        if (server.getTickCount() % 20 == 0) get(server).advance(server);
    }

    private void advance(MinecraftServer server) {
        if (rules.isEmpty()) return;
        long now = server.overworld().getGameTime();
        var lifecycle = LifeLifecycle.get(server);
        for (var rule : rules.values()) {
            var entries=rule.active.entrySet().iterator();
            while(entries.hasNext()){
                var entry=entries.next();
                var id=entry.getKey();
                var encounter=entry.getValue();
                if(!encounter.bodies.isEmpty()){
                    if(updateStructureEncounter(server,encounter,now,rule.clearing)){
                        entries.remove();
                        setDirty();
                    }
                    continue;
                }
                boolean owned=false;
                dev.sablelife.heart.HeartBlockEntity loadedCore=null;
                for(var level:server.getAllLevels()){
                    owned|=BodyOwnership.get(level).hasOwned(id);
                    var core=LoadedCoreIndex.find(level,id);
                    if(core!=null)loadedCore=core;
                }
                if(lifecycle.finished(id)||!owned&&!lifecycle.isDying(id)){
                    if(encounter.expiring&&lifecycle.finished(id))lifecycle.cancel(id);
                    entries.remove();
                    setDirty();
                    continue;
                }
                if(rule.clearing&&lifecycle.isDying(id)){
                    lifecycle.expediteNaturalRemoval(id);
                    if(!encounter.expiring){encounter.expiring=true;setDirty();}
                    if(loadedCore!=null){BlueprintSpawns.release(loadedCore);ArenaRespawns.release(loadedCore);}
                }
                if(encounter.expiring)continue;
                boolean absentExpired=absenceExpired(server,encounter,loadedCore,now);
                if(loadedCore!=null&&(rule.clearing||absentExpired||encounter.expires!=0&&now>=encounter.expires)&&loadedCore.expireWildEncounter()){
                    encounter.expiring=true;
                    setDirty();
                }
            }
            if(rule.clearing&&rule.active.isEmpty()){
                rule.clearing=false;
                rule.next=Math.max(rule.next,now+rule.cooldown*20L);
                setDirty();
            }
        }
        if (!BlueprintSpawns.available() || !globalSlot()) return;
        var entries = new ArrayList<>(rules.entrySet());
        var candidates=new java.util.HashMap<ServerLevel,List<ServerPlayer>>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(Math.floorMod(turn++, entries.size()));
            var key = entry.getKey();
            var rule = entry.getValue();
            if (!rule.enabled || !hasSlot(rule) || now < Math.max(rule.next,rule.retryAfter)) continue;
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, key.dimension()));
            if (level == null) continue;
            var players = candidates.computeIfAbsent(level,current->current.players().stream()
                    .filter(p->p.isAlive()&&!p.isSpectator()
                            &&nearbySlot(current,SableBodyProbe.toWorld(current,p.position()))).toList());
            if (players.isEmpty()) continue;
            rule.retryAfter=now+rule.density.retrySeconds()*20L;
            try {
                template(level, key, rule);
                var player = players.get(level.random.nextInt(players.size()));
                var point = SableBodyProbe.toWorld(level, player.position());
                var shape=rule.geometry.bounds(new BlueprintSpawnPlacement.Destination(key.dimension(),Vec3.ZERO,null));
                int minimumDistance=WildSpawnDistances.minimum(shape.getXsize(),shape.getZsize(),rule.density.minDistance());
                int distances=WildSpawnDistances.choices(minimumDistance,rule.density.maxDistance());
                if(distances==0)return;
                for(int attempt=0;attempt<rule.density.attempts();attempt++){
                    double angle=level.random.nextDouble()*Math.PI*2;
                    int distance=minimumDistance+level.random.nextInt(distances);
                    int x=(int)Math.floor(point.x+Math.cos(angle)*distance),z=(int)Math.floor(point.z+Math.sin(angle)*distance);
                    var placement=aboveGround(level,rule,x,z);
                    if(placement!=null&&spawn(level,rule,placement))break;
                }
            } catch (IOException | RuntimeException failure) {
                rule.enabled = false;
                setDirty();
                LogUtils.getLogger().warn("Disabled invalid wild encounter rule {}", key, failure);
            }
            return;
        }
    }

    private static boolean hasSlot(Rule rule){
        return !rule.clearing&&(rule.releaseAfterSpawn||rule.active.size()<rule.maxActive);
    }

    private boolean globalSlot(){
        return rules.values().stream().mapToInt(rule->rule.active.size()).sum()<SableLifeServerConfig.maximumWildEncounters();
    }

    private boolean nearbySlot(ServerLevel level,Vec3 position){
        var dimension=level.dimension().location();
        int remaining=SableLifeServerConfig.maximumNearbyWildEncounters();
        for(var rule:rules.values())for(var encounter:rule.active.values()){
            if(dimension.equals(encounter.lastDimension)&&encounter.lastPosition!=null
                    &&encounter.lastPosition.getCenter().distanceToSqr(position)<=PRESENCE_RADIUS*PRESENCE_RADIUS
                    &&--remaining==0)return false;
        }
        return true;
    }

    private void updatePosition(Encounter encounter,ServerLevel level,Vec3 position){
        var block=BlockPos.containing(position);
        var dimension=level.dimension().location();
        if(!block.equals(encounter.lastPosition)||!dimension.equals(encounter.lastDimension)){
            encounter.lastPosition=block;encounter.lastDimension=dimension;setDirty();
        }
    }

    private boolean absenceExpired(MinecraftServer server,Encounter encounter,dev.sablelife.heart.HeartBlockEntity core,long now){
        ServerLevel level;
        Vec3 position;
        if(core!=null){
            level=(ServerLevel)core.getLevel();
            position=SableBodyProbe.toWorld(level,core.getBlockPos().getCenter());
            updatePosition(encounter,level,position);
        }else{
            if(encounter.lastPosition==null||encounter.lastDimension==null)return false;
            level=server.getLevel(ResourceKey.create(Registries.DIMENSION,encounter.lastDimension));
            if(level==null)return false;
            position=encounter.lastPosition.getCenter();
        }
        if(encounter.absenceSeconds==0)return false;
        if(encounter.absenceDue)return true;
        boolean nearby=level.players().stream().anyMatch(player->player.isAlive()&&!player.isSpectator()
                &&SableBodyProbe.toWorld(level,player.position()).distanceToSqr(position)<=PRESENCE_RADIUS*PRESENCE_RADIUS);
        if(nearby){
            if(encounter.absentSince!=-1){encounter.absentSince=-1;setDirty();}
            return false;
        }
        if(encounter.absentSince==-1){encounter.absentSince=now;setDirty();}
        if(now-encounter.absentSince<encounter.absenceSeconds*20L)return false;
        encounter.absenceDue=true;setDirty();
        return true;
    }

    private boolean updateStructureEncounter(MinecraftServer server,Encounter encounter,long now,boolean clearing){
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,encounter.lastDimension));
        if(level==null||!(SubLevelContainer.getContainer(level) instanceof dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container))return false;
        for(var id:encounter.bodies){
            if(container.getSubLevel(id) instanceof ServerSubLevel body&&!body.isRemoved()){
                var position=body.logicalPose().position();
                updatePosition(encounter,level,new Vec3(position.x(),position.y(),position.z()));
                break;
            }
        }
        boolean expired=clearing||absenceExpired(server,encounter,null,now)||encounter.expires!=0&&now>=encounter.expires;
        // A missing in-memory holding entry can already be saved and fully unloaded.
        // Only native permanent removal confirms deletion; never infer it from absence.
        // Removal hooks can update the set while StructureRemovalApi is running.
        for(var id:List.copyOf(encounter.bodies)){
            if(container.getSubLevel(id) instanceof ServerSubLevel body&&!body.isRemoved()){
                if(!expired)continue;
                try{StructureRemovalApi.remove(level,body);}catch(RuntimeException failure){
                    LogUtils.getLogger().warn("Could not remove expired wild structure {}",id,failure);
                    continue;
                }
            }else continue;
            encounter.bodies.remove(id);
            setDirty();
        }
        return encounter.bodies.isEmpty();
    }

    /** Called after native REMOVED, never for ordinary chunk unload or assembly transfers. */
    public void permanentlyRemoved(ServerLevel level,UUID body){
        for(var entry:rules.entrySet()){
            if(!entry.getKey().dimension().equals(level.dimension().location()))continue;
            for(var encounter:entry.getValue().active.values())if(encounter.bodies.remove(body))setDirty();
        }
    }

    private static CompoundTag saveEncounter(UUID id,Encounter encounter){
        var row=new CompoundTag();
        row.putUUID("Id",id);
        row.putInt("AbsenceSeconds",encounter.absenceSeconds);
        row.putBoolean("Expiring",encounter.expiring);
        row.putLong("Expires",encounter.expires);
        row.putLong("AbsentSince",encounter.absentSince);
        row.putBoolean("AbsenceDue",encounter.absenceDue);
        var parts=new ListTag();
        encounter.bodies.forEach(idPart->parts.add(net.minecraft.nbt.NbtUtils.createUUID(idPart)));
        row.put("Bodies",parts);
        if(encounter.lastPosition!=null&&encounter.lastDimension!=null){
            row.putLong("LastPosition",encounter.lastPosition.asLong());row.putString("LastDimension",encounter.lastDimension.toString());
        }
        return row;
    }

    private static Encounter loadEncounter(CompoundTag row,int defaultAbsence){
        var encounter=new Encounter();
        encounter.absenceSeconds=row.contains("AbsenceSeconds")?Math.clamp(row.getInt("AbsenceSeconds"),0,86400):defaultAbsence;
        encounter.expiring=row.getBoolean("Expiring");
        encounter.expires=row.getLong("Expires");
        if(row.contains("AbsentSince"))encounter.absentSince=row.getLong("AbsentSince");
        encounter.absenceDue=row.getBoolean("AbsenceDue");
        if(row.contains("LastPosition")){
            encounter.lastPosition=BlockPos.of(row.getLong("LastPosition"));
            encounter.lastDimension=ResourceLocation.tryParse(row.getString("LastDimension"));
        }
        if(encounter.lastDimension!=null)for(var value:row.getList("Bodies",11))
            encounter.bodies.add(net.minecraft.nbt.NbtUtils.loadUUID(value));
        return encounter;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var rows = new ListTag();
        rules.forEach((key, rule) -> {
            var row = new CompoundTag();
            row.putString("Dimension", key.dimension().toString());
            row.putString("File", key.file());
            row.putString("SpawnAnnouncement",rule.announcement);
            row.putBoolean("SpawnAnnouncementCoordinates",rule.announcementCoordinates);
            row.putInt("Gap", rule.gap);
            row.putInt("Lifetime", rule.lifetime);
            row.putInt("Cooldown", rule.cooldown);
            row.merge(rule.density.save());
            row.putBoolean("Enabled", rule.enabled);
            row.putBoolean("ActivateCore",rule.activateCore);
            row.putUUID("Faction", rule.faction);
            row.put("Conditions",rule.conditions.save());
            row.putInt("MaxActive",rule.maxActive);
            row.putBoolean("Clearing",rule.clearing);
            row.putBoolean("ReleaseAfterSpawn",rule.releaseAfterSpawn);
            var active=new ListTag();
            rule.active.forEach((id,encounter)->active.add(saveEncounter(id,encounter)));
            row.put("Encounters",active);
            row.putLong("Next", rule.next);
            rows.add(row);
        });
        tag.put("Rules", rows);
        return tag;
    }

    public static WildEncounters load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new WildEncounters();
        for (var value : tag.getList("Rules", 10)) {
            if (result.rules.size() >= MAX_RULES) break;
            var row = (CompoundTag) value;
            var dimension = ResourceLocation.tryParse(row.getString("Dimension"));
            var file = row.getString("File");
            if (dimension == null || file.isBlank() || file.length() > 80) continue;
            var rule = new Rule();
            rule.announcement=cleanAnnouncement(row.getString("SpawnAnnouncement"));
            rule.announcementCoordinates=!rule.announcement.isEmpty()&&row.getBoolean("SpawnAnnouncementCoordinates");
            rule.gap = Math.clamp(row.getInt("Gap"), 0, 128);
            rule.lifetime = Math.clamp(row.getInt("Lifetime"), 0, 86400);
            rule.cooldown = Math.clamp(row.getInt("Cooldown"), 10, 86400);
            rule.density=Density.load(row);
            rule.enabled = row.getBoolean("Enabled");
            rule.activateCore=!row.contains("ActivateCore")||row.getBoolean("ActivateCore");
            if (row.hasUUID("Faction")) rule.faction = row.getUUID("Faction");
            rule.conditions=Conditions.load(row.getCompound("Conditions"));
            rule.maxActive=row.contains("MaxActive")?Math.clamp(row.getInt("MaxActive"),1,MAX_PER_RULE):1;
            rule.clearing=row.getBoolean("Clearing");
            rule.releaseAfterSpawn=row.getBoolean("ReleaseAfterSpawn");
            if(row.hasUUID("Active"))rule.active.put(row.getUUID("Active"),loadEncounter(row,rule.conditions.absenceSeconds()));
            for(var valueEntry:row.getList("Encounters",10)){
                if(rule.active.size()>=MAX_PER_RULE)break;
                var encounter=(CompoundTag)valueEntry;
                if(encounter.hasUUID("Id"))rule.active.put(encounter.getUUID("Id"),loadEncounter(encounter,rule.conditions.absenceSeconds()));
            }
            rule.next = row.getLong("Next");
            result.rules.put(new Key(dimension, file), rule);
        }
        return result;
    }
}
