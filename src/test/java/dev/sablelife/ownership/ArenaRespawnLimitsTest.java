package dev.sablelife.ownership;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ArenaRespawnLimitsTest {
 @Test void ReportsExactReasonAndCountsWhileAcceptingBoundary(){
  assertDoesNotThrow(()->ArenaRespawnLimits.check("scan",262144,ArenaRespawnLimits.SCAN_VOXELS));
  var failure=assertThrows(ArenaRespawnLimits.Exceeded.class,()->ArenaRespawnLimits.check("scan",262145,ArenaRespawnLimits.SCAN_VOXELS));
  assertEquals("scan",failure.kind);assertEquals(262145,failure.actual);assertEquals(262144,failure.maximum);
  assertDoesNotThrow(()->ArenaRespawnLimits.check("parts",20,ArenaRespawnLimits.PARTS));assertThrows(ArenaRespawnLimits.Exceeded.class,()->ArenaRespawnLimits.check("parts",33,ArenaRespawnLimits.PARTS));
 }
}
