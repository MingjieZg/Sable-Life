# 0.1.55: Trigger sound / 触发音效

New virtual module for roars, charge sounds and action cues. Mark one position, such as the mouth; it supplies the positional sound origin. Default: ravager roar, anchor redstone trigger, once on signal start, volume/pitch 1, cooldown 1 second, repeat interval 3 seconds.

Sound selection supports existing Minecraft/custom OGG entries, left-click next/right-click previous, and both Minecraft/custom folders. Properties are sound, playback mode, volume, pitch, cooldown and repeat interval. Settings, marker and trigger are saved and included in the data copier.

- Once on signal start plays on an off-to-on transition; holding power does not replay. A transition occurring during cooldown is ignored, not queued for later while power remains high.
- Repeat while signaled starts immediately when permitted, then repeats at the longer of repeat interval and cooldown. Removing power prevents new repeats.
- Always + Once plays once when the module becomes eligible/active, including world reload; Always + Repeat repeats without an external signal.
- Anchor, internal wireless and external RX use the same signal conditions as other modules.
- Removing power lets the already-playing sound finish, so a short redstone pulse can play a complete roar. Disabling/deleting/unloading/releasing/dying stops that module's audio. Cooldown survives power-off transitions while the module remains valid and active, preventing rapid-toggle spam.
- Trigger sounds are positional HOSTILE-category effects. They use separate module audio handles from BGM and other sounds, allowing overlap without restarting music. Long overlapping repeats remain bounded by the existing client voice limit.

The existing Sound module remains unchanged: approach, hurt, ambient and contact/footstep sounds are automatic feedback.

## Pending in-game checks

- [ ] New module appears; marking the mouth and preview show the expected sound origin.
- [ ] Short anchor redstone pulse plays a full roar; holding power does not replay Once mode.
- [ ] Power off/on outside cooldown replays; rapid toggling during cooldown does not spam.
- [ ] Repeat mode follows interval/cooldown and stops starting new sounds after signal off.
- [ ] Always, internal repeat and external RX work according to the behavior above.
- [ ] Minecraft/custom OGG selection, backwards cycling, volume, pitch and both folder buttons work.
- [ ] Roars overlap BGM without restarting it; disabling/deleting/releasing stops only the relevant audio.
- [ ] Rejoining and data copying preserve configuration; disconnected or destroyed markers cannot emit.
- [ ] Existing approach/hurt/contact/ambient sounds still work.

Validation: assemble only; no client launch or gameplay tests.
