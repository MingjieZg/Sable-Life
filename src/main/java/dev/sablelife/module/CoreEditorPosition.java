package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import java.util.*;

/** Per-viewer/core session navigation; never serialized with gameplay configuration. */
public final class CoreEditorPosition {
    public enum Section { MODULES, SETTINGS, WIRELESS, LOOT }
    public record Position(Section section,int page) {}
    private static final Map<HeartBlockEntity,Map<UUID,Position>> positions=new WeakHashMap<>();
    public static Position get(HeartBlockEntity heart,UUID viewer){return positions.getOrDefault(heart,Map.of()).getOrDefault(viewer,new Position(Section.MODULES,0));}
    public static void remember(HeartBlockEntity heart,UUID viewer,Section section,int page){positions.computeIfAbsent(heart,h->new HashMap<>()).put(viewer,new Position(section,section==Section.SETTINGS?Math.clamp(page,0,6):get(heart,viewer).page()));}
    private CoreEditorPosition(){}
}
