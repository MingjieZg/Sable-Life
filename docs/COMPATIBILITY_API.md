# 0.1.204 API additions and behavior

`BodyCombatApi.contact(Entity source, BlockHitResult hit, float damage)` validates a server source and actual body block and returns actual health removed. One invocation is one attack window, without lifetime projectile deduplication. Callers must own native cadence and hit validation; do not expose it to unchecked client payloads. Part modifiers, immunity, own-life attack filtering and death cleanup still apply.

`BodySphereQuery.queryVolumes(ServerLevel level, Vec3 visibilityOrigin, List<AttackVolume> volumes)` supports up to 16 exact boxes, a loaded-chunk visibility path and the existing collision budget. Bodies are narrowed to actual collision shapes, including logical-pose rotation/scale; multiple boxes yield at most one hit per creature. It is not a generic replacement for native cone/beam semantics: pass the actual native attack parts and activation window.

The pinned Iron bridge now consumes supported direct projectiles after native body impact, even when body immunity prevents damage. Registered addon direct profiles inherit this body-stop behavior. Native attacks on ordinary entities are unchanged. Additional direct profiles: Firebolt, Icicle and Lightning lance; Blood slash has a separate collision-box adapter. Fire/Cold/Poison breath and Siphoning have explicit native-window adapters. Unsupported families still require an adapter. Native LivingEntity-only status effects and events are not manufactured. See [current support](RELEASE_0.1.204.md).

The following 0.1.202 API description is retained as historical context; its direct projectile piercing behavior and support list are superseded above.

# Experimental body damage API (0.1.202)

This API is experimental, server-only and geometry-based. It does not create a LivingEntity target or a proxy hitbox. Keep mod-specific checks and casting permissions in the caller. Do not forward unvalidated client hits into it.

## Direct projectiles

```java
// Use a real server projectile and its actual BLOCK collision, before native discard.
boolean damaged = BodyCombatApi.projectile(projectile, blockHit, baseDamage);
```

Import `dev.sablelife.api.BodyCombatApi`. The callback finds the activated core owning the hit body block, rejects non-living control cores and invalid damage, and enters the existing `damageAt` path. Part modifiers, adaptive defense, hurt triggers and death cleanup remain effective. Successful hits are deduplicated by creature UUID for that projectile, not per body block. Persistent/repeating projectiles need a dedicated cadence adapter; do not reset the cache each tick to simulate one.

Hit BlockPos identifies the actual storage voxel. Hit location may be world or Sable storage coordinates; native spell code that sorts entities or emits particles must normalize the location to world space while retaining BlockPos. The built-in Iron bridge does this before truncating the entity ray and in impact callbacks.

## One-shot spherical attacks

```java
int damagedBodies = BodyCombatApi.sphere(
    caster, center, visibilityOrigin, radius,
    distance -> damage * Math.max(0, 1 - distance * distance / (radius * radius))
);
```

One call is one attack window and each eligible creature is damaged once. The distance is to the nearest real owned collision shape inside the sphere, not the core center or empty enclosing bounds. An overload accepts a Set of creature UUIDs to exclude a separately applied direct-hit victim. Keep the original caster/level formula and line-of-sight origin. State-dependent damage can instead use `BodySphereQuery.query` hits and the existing validated core damage entry; do not manufacture an Entity target.

Current bounds: radius >0 and <=256 blocks, visibility origin within 256 blocks of center, at most 64 body hits, at most 32,768 voxel visits per candidate and a shared world collision budget. A clip is performed only across loaded chunks. Exhaustion or missing geometry fails closed and may omit a hit under extreme load. No true multiplayer stress result is claimed.

This sphere API does not express cones, ground height tests, moving rings or per-target attack immunity. Add those profiles explicitly rather than using an enclosing box as damage geometry. It does not publish native LivingEntity damage events, apply school resistance attributes, status effects, lifesteal or target-specific skill hooks. These need separate semantics for bodies.

## Iron’s Spells 1.21.1-3.16.3 addon registration

The inspected built-in direct profiles are MagicMissileProjectile, MagicArrowProjectile and SmallMagicFireball. Subclasses that keep the base collision/damage behavior inherit them. Unknown AbstractMagicProjectile descendants are intentionally not assumed damaging: some are control, visual, explosive or repeated-area entities.

```java
// In common setup, with both Iron's Spells and Sable Life present:
IronSpellsBodyBridge.registerDirectProjectile(
    MyDirectBolt.class, bolt -> bolt.getDamage()
);
```

Import `dev.sablelife.compat.ironspells.IronSpellsBodyBridge`. This opt-in is experimental and depends on the pinned Iron API. The concrete class must use the original `handleHitDetection` / `onHit` path and positive direct-damage semantics. Registration is limited to 128 classes; abstract blanket profiles are rejected. A custom path can call the generic BodyCombatApi itself instead. Choose one route. Repeated sphere calls are separate attacks and are not automatically deduplicated.

The pinned bridge also supports MagicFireball, FireArrowProjectile and ShockwaveSpell. Fireball uses native spell falloff; Fire Arrow applies full direct damage to a collided body and half-strength splash to other bodies. Their native terrain explosions are identity-marked to avoid additional TNT-formula core damage, while retaining terrain and ordinary entity behavior. Block destruction still respects the existing core protection configuration.

Lock-on, chains, curses, freezing, DOT clouds, homing target acquisition and arbitrary addons are not covered by this initial bridge. Unknown Iron versions do not receive unverified Mixins. Review hook signatures and run real native callbacks before extending the version gate.

## Restore / tests

Run `powershell -File scripts/setup-iron-spells.ps1` for checksum-pinned public artifacts. Use `-PironSpellTests=true` for isolated GameTests, or `-PironSpellRuntime=true` for an optional normal development client/server runtime. These flags do not add a hard runtime dependency or bundle third-party JARs in Sable Life. Test classes with optional API references are kept outside annotated discovery wrappers.

All required dependency filenames, official download URLs and SHA512 values are in `scripts/iron-spells-dependencies.json`.
