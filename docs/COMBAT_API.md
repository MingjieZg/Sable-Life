# Body combat API for addon authors

This guide describes the experimental API shipped with Sable Life **0.1.313** on Minecraft **1.21.1 / NeoForge / Java 21**. Pin the version you compile against and retest upgrades; these entry points are not yet a stable, version-independent API.

The implementation is [`dev.sablelife.api.BodyCombatApi`](../src/main/java/dev/sablelife/api/BodyCombatApi.java). Bodies are real Sable-owned blocks, not proxy living entities. The API applies damage through the Life Core's damage handling, including its configured damage modifiers. It does not create a replacement entity hitbox.

## Dependency and execution

Add the Sable Life JAR to your addon's compile classpath and declare it as a required dependency if your addon always uses this API. An addon with optional support must gate calls and isolate classes that reference Sable Life when the mod is absent. Do not bundle Sable Life inside your addon.

Call these methods on the **logical server's tick thread**, using a source entity in the same server level as the body. Never trust client-supplied damage, hit positions or timing. The caller owns attack eligibility, range checks and cadence; this is an integration API, not a network packet handler.

## Coordinates

- A `BlockHitResult` must retain the actual hit body's **Sable storage/plot `BlockPos`**, as provided by a Sable-aware block collision/raycast callback. Ownership is looked up using this position.
- Its hit location must be a finite point in the coordinate space returned by that callback. Do not replace the storage `BlockPos` with `BlockPos.containing(worldHitLocation)`; a moving body's visible world position is different from its stored blocks.
- For `sphere`, prefer world-space `center` and `visibilityOrigin`. The implementation also converts Sable plot-space points to world space when they belong to a sublevel. Do not manually transform a point twice.
- `visibilityOrigin` is the point from which obstruction is tested, such as the explosion origin or caster's eye position. Terrain can block a sphere attack.

## Entry points

| Method | Intended use | Return value |
| --- | --- | --- |
| `projectile(Projectile source, BlockHitResult hit, float damage)` | A projectile's block-hit callback | Whether a body hit was accepted; not the damage amount |
| `contact(Entity source, BlockHitResult hit, float damage)` | A continuous or repeated attack's validated contact | Actual Life Core health removed, or `0` |
| `sphere(Entity source, Vec3 center, Vec3 visibilityOrigin, double radius, DoubleUnaryOperator damageAtDistance)` | One radial attack window | Number of body damage applications accepted; not total damage |
| `sphere(..., Set<UUID> excluded)` | The same radial attack with excluded creature IDs | Same count, excluding those creatures |

Use positive finite damage. Invalid, inactive, detached or otherwise ineligible targets may be rejected. A valid hit can remove zero health because of damage modifiers, so a boolean/count result must not be treated as proof of positive health loss.

### Projectile block hit

Call once in your projectile's existing server-side, Sable-aware block-hit pipeline. `calculatedDamage` should already include your mod's normal weapon/spell scaling:

```java
import dev.sablelife.api.BodyCombatApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;

static boolean forwardBodyHit(Projectile projectile, BlockHitResult hit,
                              float calculatedDamage) {
    if (!(projectile.level() instanceof ServerLevel)) return false;
    return BodyCombatApi.projectile(projectile, hit, calculatedDamage);
}
```

The bridge remembers accepted creature IDs in the source entity's persistent data: one source projectile can damage each creature only once through this entry point, with at most **64 remembered creatures**. This survives ticks and is not a per-tick reset. Do not clear those internal tags to force repeated damage. For beams or repeated contact attacks, use `contact` and implement your own interval.

Do not forward the same attack twice through an existing Sable Life compatibility bridge and your own callback. A successful body transfer does not define your projectile's removal, visuals, terrain interaction or ordinary entity damage; keep those decisions in your native attack pipeline.

### Continuous contact

At your attack's existing damage interval:

```java
float healthRemoved = BodyCombatApi.contact(sourceEntity, validatedBlockHit,
                                            calculatedDamage);
```

Each call is a separate damage application. There is no built-in interval or per-target immunity timer here. Gate repeated callbacks yourself, normally by attack instance and creature, so contacting multiple blocks of one body does not multiply damage unintentionally. Do not call it every render frame.

### Radial attack

```java
int acceptedBodies = BodyCombatApi.sphere(
    sourceEntity, explosionCenter, visibilityOrigin, 6.0,
    distance -> 20.0 * Math.max(0.0, 1.0 - distance / 6.0)
);
```

The distance supplied to the function is the world-space distance to a selected visible body collision box, not simply the Life Core block's position. One invocation applies at most one hit per returned creature. A later invocation is a new attack window; the caller must control repetition and any cross-window hit limits.

The optional exclusion set contains **Life Core creature UUIDs**, not projectile UUIDs, sublevel UUIDs or marked module IDs. It can exclude a creature already handled by a direct-hit path when that matches your attack's intended direct/area damage behavior.

Sphere queries require `0 < radius <= 256` and a center-to-visibility-origin distance no greater than 256 blocks. They use loaded bodies, loaded terrain paths and a shared collision-work budget, with at most 64 returned bodies. In very large or busy scenes, a budget-limited query may return fewer targets; do not interpret the count as an exhaustive list of every body in range.

## Integration checks

Before releasing an addon, verify a stationary and moving body, an independent detached fragment, terrain occlusion, whole-body/part damage modifiers, repeated callbacks, and dedicated-server startup. Test the ordinary entity-hit path too, to ensure the body bridge has not doubled or suppressed your original damage.

The API applies body damage; it is not a general promise of native entity hurt events, item durability, spell rewards, module hit-counter credit or every third-party combat side effect. Those behaviors require their own supported integration and validation.
