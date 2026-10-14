package dev.sablelife.ownership;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-owned death jobs continue after the core is destroyed; completed IDs prevent duplicate settlement. */
public final class LifeLifecycle extends SavedData {
    private final Map<UUID, Death> deaths = new HashMap<>();
    private record Completion(UUID parent,String partName) {}
    private final Map<UUID,Completion> completed = new HashMap<>();
    private int cleanupTurn;
    public int pendingCount(){return deaths.size();}
    public int completedCount(){return completed.size();}
    private boolean recorded(UUID id){return deaths.containsKey(id)||completed.containsKey(id);}
    private void complete(UUID id,Death death){
        completed.put(id,new Completion(death.parent,death.partName));deaths.remove(id);
        unbreakable.remove(id);explosionProtected.remove(id);interactionAllowed.remove(id);setDirty();
    }
    private final Set<UUID> unbreakable = new HashSet<>();
    private final Set<UUID> explosionProtected = new HashSet<>();
    private final Set<UUID> interactionAllowed = new HashSet<>();
    public void setInteractionAllowed(UUID id,boolean enabled){if(enabled?interactionAllowed.add(id):interactionAllowed.remove(id))setDirty();}
    public boolean interactionAllowed(UUID id){return interactionAllowed.contains(id);}
    public static LifeLifecycle get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(LifeLifecycle::new, LifeLifecycle::load), "sablelife_lifecycle");
    }
    public void setBreakable(UUID id, boolean enabled) {
        if (enabled ? unbreakable.remove(id) : unbreakable.add(id)) setDirty();
    }
    public boolean breakable(UUID id) { return !unbreakable.contains(id); }
    public void setExplodable(UUID id, boolean enabled) {
        if (enabled ? explosionProtected.remove(id) : explosionProtected.add(id)) setDirty();
    }
    public boolean explodable(UUID id) { return !explosionProtected.contains(id); }
    public void cancel(UUID id) { deaths.entrySet().removeIf(e -> e.getKey().equals(id) || id.equals(e.getValue().parent)); completed.entrySet().removeIf(e->e.getKey().equals(id)||id.equals(e.getValue().parent)); unbreakable.remove(id); explosionProtected.remove(id);interactionAllowed.remove(id); setDirty(); }
    public boolean isDying(UUID id) { return recorded(id); }
    public boolean finished(UUID id){return completed.containsKey(id)&&deaths.values().stream().noneMatch(d->id.equals(d.parent));}
    public boolean deathDue(UUID id){var job=deaths.get(id);return job!=null&&!job.cleaned&&job.remaining==0;}
    public Vec3 rewardPoint(UUID id,ResourceLocation dimension){var job=deaths.get(id);return job!=null&&!job.rewarded&&job.dimension.equals(dimension)?job.point:null;}
    public void deathLoading(UUID id,boolean enabled){var job=deaths.get(id);if(job!=null&&job.forceLoading!=enabled){job.forceLoading=enabled;setDirty();}}
    public Map<UUID,Vec3> loadingRewards(ResourceLocation dimension){var result=new HashMap<UUID,Vec3>();deaths.forEach((id,job)->{if(job.forceLoading&&!job.rewarded&&job.dimension.equals(dimension))result.put(id,job.point);});return result;}
    public boolean lostPart(UUID parent, String name) {
        return deaths.values().stream().anyMatch(d -> parent.equals(d.parent) && name.equals(d.partName)) || completed.values().stream().anyMatch(d->parent.equals(d.parent)&&name.equals(d.partName));
    }
    public String lostParts(UUID parent) {
        return java.util.stream.Stream.concat(deaths.values().stream().filter(d -> parent.equals(d.parent)).map(d -> d.partName),completed.values().stream().filter(d->parent.equals(d.parent)).map(Completion::partName)).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(", "));
    }
    public boolean schedulePart(ServerLevel level, UUID id, UUID parent, String name, Vec3 point, int delay, ResourceLocation loot, int experience) {
        return schedulePart(level, id, parent, name, point, delay, loot, experience, null);
    }
    public boolean schedulePart(ServerLevel level, UUID id, UUID parent, String name, Vec3 point, int delay, ResourceLocation loot, int experience,
                                java.util.List<net.minecraft.world.item.ItemStack> templates) {
        return schedulePart(level, id, parent, name, point, delay, loot, experience, templates, null, null, false, 1);
    }
    public boolean schedulePart(ServerLevel level, UUID id, UUID parent, String name, Vec3 point, int delay, ResourceLocation loot, int experience,
                                java.util.List<net.minecraft.world.item.ItemStack> templates, int[] chances, int[] weights,
                                boolean weighted, int rolls) {
        if (recorded(id) || recorded(parent)) return false;
        var death = new Death(level.dimension().location(), point, delay, loot, experience, false);
        death.parent = parent; death.partName = name;
        if (templates != null) death.templates = templates.stream().limit(27).map(net.minecraft.world.item.ItemStack::copy).toList();
        if (templates != null) {
            if (chances != null) death.chances = chances.clone();
            if (weights != null) death.weights = weights.clone();
            death.weighted = weighted; death.rolls = Math.clamp(rolls, 1, 27);
        }
        deaths.put(id, death); setDirty(); return true;
    }
    public void schedule(HeartBlockEntity heart) {
        if (recorded(heart.creatureId())) return;
        var level = (ServerLevel) heart.getLevel();
        Vec3 point = SableBodyProbe.toWorld(level, Vec3.atCenterOf(heart.getBlockPos()));
        var death = new Death(level.dimension().location(), point,
                heart.health().remainingDeathTicks(), heart.deathLoot(), heart.deathExperience(), false);
        death.experienceLevels=heart.deathExperienceLevels();
        if (heart.deathTemplateMode()) {
            death.templates = heart.deathTemplates(); death.chances=heart.chances(); death.weights=heart.weights();
            death.weighted=heart.weighted(); death.rolls=heart.rolls();
        }
        deaths.put(heart.creatureId(), death);
        ArenaRespawns.death(heart);
        death.forceLoading=heart.forceChunkLoading();
        death.presentation=heart.removalPresentation().save();
        setDirty();
        heart.deathPresentation().play(heart);
    }
    public static void tick(ServerTickEvent.Post event) {
        for (var level : event.getServer().getAllLevels()) BodyOwnership.get(level).flushRemoved(level);
        get(event.getServer()).tickDeaths(event.getServer());
    }
    public void tickDeaths(MinecraftServer server) {
        int remainingBlocks=dev.sablelife.SableLifeServerConfig.deathCleanupBlocksPerTick(),remainingVisits=16384,wholeSlots=1;
        var jobs=new java.util.ArrayList<>(deaths.entrySet());
        if(!jobs.isEmpty()){java.util.Collections.rotate(jobs,-Math.floorMod(cleanupTurn++,jobs.size()));}
        for (var entry : jobs) {
            var death = entry.getValue();
            if (death.cleaned && death.rewarded) {complete(entry.getKey(),death);continue;}
            if (death.remaining > 0) { death.remaining--; setDirty(); continue; }
            var effect=death.effect;if(effect==null){effect=new dev.sablelife.heart.DeathPresentation(true);effect.load(death.presentation);death.effect=effect;}
            if(!death.presentationPlayed) {
                var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,death.dimension));
                if(level!=null&&level.hasChunkAt(BlockPos.containing(death.point))) {
                    effect.playSound(level,death.point,entry.getKey());death.presentationPlayed=true;setDirty();
                }
            }
            // Never force-load chunks. Their registered blocks stay protected and are retried on later ticks.
            if(!death.cleaned&&!death.wholeFallback&&wholeSlots>0&&server.overworld().getGameTime()>=death.nextWholeAttempt){
                wholeSlots--;
                var result=WholeLifeDeletion.attempt(server,entry.getKey(),death.parent!=null,death.parent==null?effect:null);
                if(result==WholeLifeDeletion.Outcome.DONE){death.cleaned=true;setDirty();}
                else if(result==WholeLifeDeletion.Outcome.FALLBACK){death.wholeFallback=true;setDirty();}
                else death.nextWholeAttempt=server.overworld().getGameTime()+20;
            }
            if (!death.cleaned&&death.wholeFallback) {
                boolean pending = false;
                for (var level : server.getAllLevels()) {
                    var ownership = BodyOwnership.get(level);
                    if(remainingBlocks>0&&remainingVisits>0){
                        var work=ownership.cleanBudgeted(level,entry.getKey(),remainingBlocks,remainingVisits,death.parent!=null,death.parent==null?effect:null,true);
                        remainingBlocks-=work.removed();remainingVisits-=work.visited();
                    }
                    pending |= death.parent == null ? ownership.hasOwned(entry.getKey()) : ownership.hasPart(entry.getKey());
                }
                if (!pending) { death.cleaned = true; setDirty(); }
            }
            if (death.rewarded) {if(death.cleaned)complete(entry.getKey(),death);continue;}
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, death.dimension));
            if (level == null || !level.hasChunkAt(BlockPos.containing(death.point))) continue;
            death.rewarded = true;
            setDirty();
            var loot = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, death.loot));
            var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, death.point).create(LootContextParamSets.CHEST);
            for (var stack : death.templates == null ? loot.getRandomItems(params)
                    : dev.sablelife.part.LootSelection.draw(death.templates, death.chances, death.weights,
                            death.weighted, death.rolls, level.random)) {
                if (stack.isEmpty()) continue;
                var item = new ItemEntity(level, death.point.x, death.point.y, death.point.z, stack.copy());
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
            if(death.experience>0){if(death.experienceLevels)dev.sablelife.heart.LevelRewardOrb.award(level,death.point,dev.sablelife.heart.ExperienceUnits.levels(death.experience));else ExperienceOrb.award(level,death.point,death.experience);}
            if(death.cleaned)complete(entry.getKey(),death);
        }
    }
    private static final class Death {
        final ResourceLocation dimension, loot;
        final Vec3 point;
        final int experience;
        int remaining;
        boolean rewarded;
        boolean cleaned;
        boolean wholeFallback;
        transient long nextWholeAttempt;
        boolean forceLoading;
        boolean experienceLevels;
        CompoundTag presentation=new CompoundTag();
        dev.sablelife.heart.DeathPresentation effect;
        boolean presentationPlayed;
        UUID parent;
        String partName = "";
        java.util.List<net.minecraft.world.item.ItemStack> templates;
        int[] chances = new int[27], weights = new int[27];
        boolean weighted;
        int rolls = 1;
        Death(ResourceLocation dimension, Vec3 point, int remaining, ResourceLocation loot, int experience, boolean rewarded) {
            this.dimension = dimension; this.point = point; this.remaining = remaining;
            this.loot = loot; this.experience = experience; this.rewarded = rewarded;
            java.util.Arrays.fill(chances, 100); java.util.Arrays.fill(weights, 100);
        }
    }
    public static LifeLifecycle load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new LifeLifecycle();
        var jobs = tag.getList("Deaths", 10);
        for (int i = 0; i < jobs.size(); i++) {
            var job = jobs.getCompound(i);
            var dimension = ResourceLocation.tryParse(job.getString("Dimension"));
            var loot = ResourceLocation.tryParse(job.getString("Loot"));
            if (job.hasUUID("Id") && dimension != null && loot != null) {
                var death = new Death(dimension, new Vec3(job.getDouble("X"), job.getDouble("Y"), job.getDouble("Z")),
                        Math.clamp(job.getInt("Remaining"), 0, job.hasUUID("Parent")?12000:dev.sablelife.heart.HeartHealth.MAX_DEATH_DELAY), loot, Math.clamp(job.getInt("Experience"), 0, job.hasUUID("Parent")?10000:dev.sablelife.heart.ExperienceUnits.MAX_POINTS), job.getBoolean("Rewarded"));
                death.wholeFallback=job.getBoolean("WholeFallback");death.cleaned = job.getBoolean("Cleaned");death.experienceLevels=job.getBoolean("ExperienceLevels");
                death.forceLoading=job.getBoolean("ForceLoading");
                death.presentation=job.getCompound("Presentation").copy();death.presentationPlayed=job.getBoolean("PresentationPlayed");
                death.parent = job.hasUUID("Parent") ? job.getUUID("Parent") : null;
                death.partName = job.getString("PartName");
                if (job.contains("LootTemplates")) {
                    var items = net.minecraft.core.NonNullList.withSize(27, net.minecraft.world.item.ItemStack.EMPTY);
                    net.minecraft.world.ContainerHelper.loadAllItems(job.getCompound("LootTemplates"), items, registries);
                    death.templates = items;
                    var savedChances = job.getIntArray("LootChances"); var savedWeights = job.getIntArray("LootWeights");
                    for (int slot = 0; slot < 27; slot++) {
                        if (slot < savedChances.length) death.chances[slot] = Math.clamp(savedChances[slot], 0, 100);
                        if (slot < savedWeights.length) death.weights[slot] = Math.clamp(savedWeights[slot], 0, 100);
                    }
                    death.weighted = job.getBoolean("WeightedLoot");
                    death.rolls = job.contains("LootRolls") ? Math.clamp(job.getInt("LootRolls"), 1, 27) : 1;
                }
                if(death.cleaned&&death.rewarded)data.completed.put(job.getUUID("Id"),new Completion(death.parent,death.partName));else data.deaths.put(job.getUUID("Id"), death);
            }
        }
        for(var value:tag.getList("CompletedDeaths",10)){
            var job=(CompoundTag)value;if(!job.hasUUID("Id"))continue;
            data.completed.put(job.getUUID("Id"),new Completion(job.hasUUID("Parent")?job.getUUID("Parent"):null,job.getString("PartName")));
            data.deaths.remove(job.getUUID("Id"));
        }
        for(var value:tag.getList("InteractionAllowed",10)){var entry=(CompoundTag)value;if(entry.hasUUID("Id"))data.interactionAllowed.add(entry.getUUID("Id"));}
        var locked = tag.getList("Unbreakable", 10);
        for (int i = 0; i < locked.size(); i++) if (locked.getCompound(i).hasUUID("Id")) data.unbreakable.add(locked.getCompound(i).getUUID("Id"));
        var explosions = tag.getList("ExplosionProtected", 10);
        for (int i = 0; i < explosions.size(); i++) if (explosions.getCompound(i).hasUUID("Id")) data.explosionProtected.add(explosions.getCompound(i).getUUID("Id"));
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var jobs = new ListTag();
        deaths.forEach((id, death) -> {
            var job = new CompoundTag(); job.putUUID("Id", id);
            job.putString("Dimension", death.dimension.toString()); job.putString("Loot", death.loot.toString());
            job.putDouble("X", death.point.x); job.putDouble("Y", death.point.y); job.putDouble("Z", death.point.z);
            job.putInt("Remaining", death.remaining); job.putInt("Experience", death.experience);job.putBoolean("ExperienceLevels",death.experienceLevels); job.putBoolean("Rewarded", death.rewarded);
            job.putBoolean("WholeFallback",death.wholeFallback);job.putBoolean("Cleaned", death.cleaned);job.putBoolean("ForceLoading",death.forceLoading);
            job.put("Presentation",death.presentation.copy());job.putBoolean("PresentationPlayed",death.presentationPlayed);
            if (death.parent != null) { job.putUUID("Parent", death.parent); job.putString("PartName", death.partName); }
            if (death.templates != null) {
                var items = net.minecraft.core.NonNullList.withSize(27, net.minecraft.world.item.ItemStack.EMPTY);
                for (int i = 0; i < Math.min(27, death.templates.size()); i++) items.set(i, death.templates.get(i).copy());
                var saved = new CompoundTag(); net.minecraft.world.ContainerHelper.saveAllItems(saved, items, registries); job.put("LootTemplates", saved);
                job.putIntArray("LootChances", death.chances); job.putIntArray("LootWeights", death.weights);
                job.putBoolean("WeightedLoot", death.weighted); job.putInt("LootRolls", death.rolls);
            }
            jobs.add(job);
        });
        tag.put("Deaths", jobs);
        var settled=new ListTag();completed.forEach((id,death)->{var job=new CompoundTag();job.putUUID("Id",id);if(death.parent!=null)job.putUUID("Parent",death.parent);if(!death.partName.isEmpty())job.putString("PartName",death.partName);settled.add(job);});tag.put("CompletedDeaths",settled);
        var locked = new ListTag();
        unbreakable.forEach(id -> { var value = new CompoundTag(); value.putUUID("Id", id); locked.add(value); });
        tag.put("Unbreakable", locked);
        var explosions = new ListTag();
        explosionProtected.forEach(id -> { var value = new CompoundTag(); value.putUUID("Id", id); explosions.add(value); });
        tag.put("ExplosionProtected", explosions);
        var interactions=new ListTag();interactionAllowed.forEach(id->{var entry=new CompoundTag();entry.putUUID("Id",id);interactions.add(entry);});tag.put("InteractionAllowed",interactions);
        return tag;
    }
}
