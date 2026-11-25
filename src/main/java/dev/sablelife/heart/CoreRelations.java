package dev.sablelife.heart;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import dev.sablelife.ownership.LoadedCoreIndex;

/** Player exclusions use case-insensitive login names; faction identities use UUIDs. Exclusions affect acquisition only. */
public final class CoreRelations {
    public static final int LIMIT=256;
    public static final int MOB_RULE_TEXT_LIMIT = 256;
    private static final String NAMESPACE_SUFFIX = ":*";
    public enum MobGroup {
        PASSIVE("passive"), NEUTRAL("neutral");

        private final String name;
        private final TagKey<EntityType<?>> tag;

        MobGroup(String name) {
            this.name=name;
            tag=TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("sablelife", "mob_group/"+name));
        }

        public String key(){return "mob_group_"+name;}
        public ResourceLocation id(){return tag.location();}
    }

    public static final List<MobGroup> MOB_GROUPS=List.of(MobGroup.values());

    public static MobGroup mobGroup(ResourceLocation id) {
        for(var group:MOB_GROUPS)if(group.id().equals(id))return group;
        return null;
    }
    private final Map<String,String> excluded=new LinkedHashMap<>();
    private final MobRules excludedMobs = new MobRules(true);
    private final MobRules priorityMobs = new MobRules(false);
    private UUID faction;
    private BlueprintFaction blueprintFaction;
    private int priority;
    private String ownerName="";
    public String ownerName(){return ownerName;}
    public boolean ownerName(String value){if(value==null)return false;value=value.strip();if(!value.isEmpty()&&!value.matches("[A-Za-z0-9_]{1,16}"))return false;ownerName=value;return true;}
    public boolean isOwner(LivingEntity target){return target instanceof Player p&&!ownerName.isEmpty()&&ownerName.equalsIgnoreCase(p.getGameProfile().getName());}
    public int priority(){return priority;}
    public void priority(int value){priority=Math.clamp(value,0,3);}
    public UUID faction(){return faction;}
    public void faction(UUID id) {
        faction = id;
        blueprintFaction = null;
    }
    public BlueprintFaction blueprintFaction() { return blueprintFaction; }
    public boolean hasPendingBlueprintFaction() { return blueprintFaction != null && faction == null; }
    public void blueprintFaction(BlueprintFaction source) { blueprintFaction = source; }
    public void bindBlueprintFaction(UUID id) { faction = id; }
    public void clearBlueprintFaction() { faction(null); }
    private static String key(String name){return name.strip().toLowerCase(Locale.ROOT);}
    public boolean excludes(String name){return name!=null&&excluded.containsKey(key(name));}
    public boolean exclude(String name){if(name==null)return false;name=name.strip();if(!name.matches("[A-Za-z0-9_]{1,16}"))return false;String key=key(name);if(!excluded.containsKey(key)&&excluded.size()>=LIMIT)return false;excluded.put(key,name);return true;}
    public boolean remove(String name){return name!=null&&excluded.remove(key(name))!=null;}
    /** Attribute registration identifies living types, including modded MISC types, without spawning them. */
    public static boolean selectableMob(EntityType<?> type){return type!=null&&type!=EntityType.PLAYER&&type!=dev.sablelife.SableLife.ENTITY_HITBOX.get()&&DefaultAttributes.hasSupplier(type);}
    public static List<ResourceLocation> mobTypes(){return BuiltInRegistries.ENTITY_TYPE.stream().filter(CoreRelations::selectableMob).map(BuiltInRegistries.ENTITY_TYPE::getKey).sorted().toList();}
    public static List<String> mobNamespaces() {
        return mobTypes().stream().map(ResourceLocation::getNamespace).distinct().sorted().toList();
    }

    public boolean excludesMob(EntityType<?> type) {
        return type != null && !excludedMobs.isEmpty()
                && excludesMob(type, BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    private boolean excludesMob(EntityType<?> type, ResourceLocation id) {
        if (excludedMobs.matches(id)) return true;
        for (var group : MOB_GROUPS) if (excludedMobs.ids.contains(group.id()) && type.is(group.tag)) return true;
        return false;
    }

    public boolean excludeMob(String text) { return excludedMobs.add(text, true); }
    public boolean removeMob(String text) { return excludedMobs.remove(text); }
    public boolean prioritizeMob(String text) { return priorityMobs.add(text, true); }
    public boolean removePriorityMob(String text) { return priorityMobs.remove(text); }
    public boolean hasPriorityMobs() { return !priorityMobs.isEmpty(); }
    public ListTag savePriorityMobs() { return priorityMobs.save(); }

    public boolean prioritizesMob(EntityType<?> type) {
        if (type == null || !hasPriorityMobs()) return false;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return priorityMobs.matches(id) && (excludedMobs.isEmpty() || !excludesMob(type, id));
    }

    private static final class MobRules {
        private final boolean allowGroups;
        private final Set<ResourceLocation> ids = new LinkedHashSet<>();
        private final Set<String> namespaces = new LinkedHashSet<>();

        private MobRules(boolean allowGroups) { this.allowGroups = allowGroups; }

        private int size() { return ids.size() + namespaces.size(); }
        private boolean isEmpty() { return ids.isEmpty() && namespaces.isEmpty(); }
        private boolean matches(ResourceLocation id) {
            return id != null && (ids.contains(id) || namespaces.contains(id.getNamespace()));
        }

        private static String namespace(String entry) {
            var value = entry.substring(0, entry.length() - NAMESPACE_SUFFIX.length());
            return !value.isEmpty() && ResourceLocation.isValidNamespace(value) ? value : null;
        }

        private boolean add(String text, boolean registeredOnly) {
            if (text == null || text.length() > MOB_RULE_TEXT_LIMIT) return false;
            var entry = text.strip();
            var registry = BuiltInRegistries.ENTITY_TYPE;
            if (entry.endsWith(NAMESPACE_SUFFIX)) {
                var namespace = namespace(entry);
                if (namespace == null || (size() >= LIMIT && !namespaces.contains(namespace))) return false;
                if (registeredOnly && registry.stream().noneMatch(type ->
                        registry.getKey(type).getNamespace().equals(namespace) && selectableMob(type))) return false;
                namespaces.add(namespace);
                return true;
            }

            var id = ResourceLocation.tryParse(entry);
            if (id == null || (size() >= LIMIT && !ids.contains(id))) return false;
            if (mobGroup(id) != null) {
                if (!allowGroups) return false;
            } else if (registeredOnly && (!registry.containsKey(id) || !selectableMob(registry.get(id)))) {
                return false;
            }
            ids.add(id);
            return true;
        }

        private boolean remove(String text) {
            if (text == null || text.length() > MOB_RULE_TEXT_LIMIT) return false;
            var entry = text.strip();
            return entry.endsWith(NAMESPACE_SUFFIX) ? namespaces.remove(namespace(entry))
                    : ids.remove(ResourceLocation.tryParse(entry));
        }

        private ListTag save() {
            var entries = new ListTag();
            ids.forEach(id -> entries.add(StringTag.valueOf(id.toString())));
            namespaces.forEach(namespace -> entries.add(StringTag.valueOf(namespace + NAMESPACE_SUFFIX)));
            return entries;
        }

        private void load(ListTag entries) {
            ids.clear();
            namespaces.clear();
            // Preserve well-formed entries from absent mods when a blueprint moves between servers.
            for (var entry : entries) {
                if (size() >= LIMIT) break;
                add(entry.getAsString(), false);
            }
        }
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("TargetPriority", priority);
        tag.putString("OwnerName", ownerName);
        if (faction != null) tag.putUUID("Faction", faction);
        if (blueprintFaction != null) tag.put(BlueprintFaction.TAG, blueprintFaction.save());
        var players = new ListTag();
        excluded.values().forEach(name -> players.add(StringTag.valueOf(name)));
        tag.put("ExcludedNames", players);
        tag.put("ExcludedMobs", excludedMobs.save());
        if (hasPriorityMobs()) tag.put("PriorityMobs", savePriorityMobs());
        return tag;
    }

    public void load(CompoundTag tag) {
        excluded.clear();
        ownerName = "";
        ownerName(tag.getString("OwnerName"));
        priority(tag.getInt("TargetPriority"));
        faction = tag.hasUUID("Faction") ? tag.getUUID("Faction") : null;
        blueprintFaction = BlueprintFaction.read(tag.getCompound(BlueprintFaction.TAG));
        for (var name : tag.getList("ExcludedNames", Tag.TAG_STRING)) exclude(name.getAsString());
        for (var entry : tag.getList("Excluded", Tag.TAG_COMPOUND)) exclude(((CompoundTag) entry).getString("Name"));
        excludedMobs.load(tag.getList("ExcludedMobs", Tag.TAG_STRING));
        priorityMobs.load(tag.getList("PriorityMobs", Tag.TAG_STRING));
    }
    public static boolean allies(HeartBlockEntity a,HeartBlockEntity b){return a!=null&&b!=null&&a.getLevel() instanceof ServerLevel level&&FactionRegistry.get(level.getServer()).allied(a.relations().faction,b.relations().faction);}
    public static boolean targetAllowed(ServerLevel level, UUID owner, LivingEntity target) {
        var core = LoadedCoreIndex.find(level, owner);
        if (core == null) return true;
        var relations = core.relations();
        if (target instanceof Player player) return !relations.isOwner(player) && !relations.excludes(player.getGameProfile().getName());
        if (target instanceof dev.sablelife.module.ModuleTargets.LifeTarget life) return !allies(core, life.core());
        return !relations.excludesMob(target.getType()) || core.modules().damageBoxes.retaliatesAgainst(core, target);
    }
    public static boolean damageAllowed(HeartBlockEntity victim,UUID attacker){if(!(victim.getLevel() instanceof ServerLevel level))return true;var source=LoadedCoreIndex.find(level,attacker);return source==null||FactionRegistry.get(level.getServer()).damageAllowed(source.relations().faction,victim.relations().faction);}
}
