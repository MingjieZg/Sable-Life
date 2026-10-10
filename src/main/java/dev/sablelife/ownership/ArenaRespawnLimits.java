package dev.sablelife.ownership;

/** Limits apply to trusted administrator templates, never client packet data. */
public final class ArenaRespawnLimits {
 public static final int PARTS=32, SCAN_VOXELS=262144, BLOCKS=32768, BLOCK_ENTITIES=4096, ENTITIES=256;
 public static final int TEMPLATE_BYTES=8*1024*1024, WORLD_TEMPLATE_BYTES=32*1024*1024;
 public static final class Exceeded extends IllegalArgumentException {
  public final String kind; public final long actual,maximum;
  private Exceeded(String kind,long actual,long maximum){super("limit_"+kind);this.kind=kind;this.actual=actual;this.maximum=maximum;}
 }
 public static void check(String kind,long actual,long maximum){if(actual>maximum)throw new Exceeded(kind,actual,maximum);}
 private ArenaRespawnLimits(){}
}
