package dev.sablelife.combat;

import net.minecraft.world.phys.AABB;

/** Per entity-section storage; expands section lookup, never the actual hit predicate. */
public final class HitboxQueryBounds {
    private int largeReceivers;

    public void addedLargeReceiver(){largeReceivers++;}
    public void removedLargeReceiver(){largeReceivers--;}

    public AABB sectionsFor(AABB query){
        if(largeReceivers==0)return query;
        // Vanilla searches +/-2 horizontally and 4 below. Receivers are at most
        // 16 wide/deep/high, with their entity position at bottom center.
        return new AABB(query.minX-6,query.minY-12,query.minZ-6,query.maxX+6,query.maxY,query.maxZ+6);
    }

    public interface Section {
        void sablelife$queryBounds(HitboxQueryBounds bounds);
    }
}
