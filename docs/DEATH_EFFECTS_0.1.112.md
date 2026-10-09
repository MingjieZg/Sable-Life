# Death effects — 0.1.112

Core Settings now exposes the two effects previously fixed in the death lifecycle.

- Death instant: firework and flash particles, purple sparks, blast and twinkle sounds by default. Choose Firework burst, Sphere, Horizontal ring, Vertical star, Spiral, or Scatter / smoke. Spread controls pattern size.
- After death delay: poof particles at disappearing blocks by default. Select different particles and sounds independently from the instant effect. Particles retain the existing limit of 16 locations per cleanup tick; sounds play once per death job.

Both pages use the module particle picker and sound controls, with individual particle duration/scale, volume, pitch, range, a secondary sound, and a separate Preview. Preview commits the visible numeric fields before playing at the core without damage or block removal.

Delayed effects are snapshotted in the world death job, preserving settings through core removal and world reload. Existing custom instant settings remain; old empty settings migrate to the formerly hard-coded firework default. Explicitly cleared effects saved by this version stay cleared.
