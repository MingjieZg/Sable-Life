# 0.1.44 Core settings and wireless output

Core settings now use a custom form with creature name, maximum health, death delay in seconds, XP, and explicit On/Off buttons for block breaking and the boss bar. Numeric inputs support wheel changes (0.1 for health/time, 1 for XP), bounds hints, Enter and apply buttons. Name and toggles retain live edit permissions; health, delay and XP require a dormant core. Input focus consumes inventory shortcuts.

Combat BGM fields are ordered: audio, start mode, range, fade-out, volume.

Wireless channel modes: internal repeat (core-only waveform), external receive (Create RX), external output (Create TX waveform plus core output). External output uses the same red/blue frequency samples and the existing delay, on/off durations, repeat switch and strength. Activation, master disable, mode/frequency edits, deletion, release and unloading retain network cleanup. Legacy transmitter channels migrate to explicit external output to preserve behavior; empty trailing legacy rows still trim.

Validation: assemble passed. No automated or in-game tests were run; client was not launched at the user's request.
