package dev.sablelife.module;

import net.minecraft.nbt.CompoundTag;
import java.util.List;
import java.util.UUID;

/** Portable configuration, without source-body positions or other modules' identities. */
public final class ModulePreset {
    public static final int MAX_TEXT=65536;
    public static CompoundTag portable(CompoundTag source){
        var tag=source.copy();
        for(String key:List.of("Id","CopySourceId","GyroDown","Anchors","ParticleAreaAnchors","ParticlePointAnchors","ArenaCenter","ImportSource","ImportData","RewardOwner","Invalidated","Detached"))tag.remove(key);
        for(var move:tag.getList("Moves",10))((CompoundTag)move).remove("Actions");
        return tag;
    }
    public static VirtualModule create(CompoundTag source){
        if (!AuthoringLimits.compound(source, AuthoringLimits.MAX_PRESET_BYTES)) throw new IllegalArgumentException("Preset exceeds authoring limits");
        var tag=portable(source);tag.putUUID("Id",UUID.randomUUID());var module=VirtualModule.load(tag);module.sanitizeImportedAuthoring();return module;
    }
    private ModulePreset(){}
}
