# Unified horizontal / vertical navigation — 0.1.50-dev

Navigation always uses Turn left / right now; the alternate Directional strength mode and its selector are removed. Old bearing-mode settings load into the retained turn behavior. The four existing horizontal outputs remain: left/right are turn corrections, front/back are alignment strengths.

The same marked output block now supports local UP/DOWN faces for pitch corrections. Vertical tracking defaults to On and may be disabled to use the module for horizontal steering only. Horizontal and vertical dead zones are independently configurable from 0 to 45 degrees, both initially 3 degrees. Outside a dead zone, correction power rises from 1 to 15 with angle error and saturates at 90 degrees. Only one direction in each opposing correction pair is powered at a time. Correctly aligned axes output zero, and all six faces output zero with no target.

The target delta is transformed by the inverse current orientation of the marked body. Vertical error is atan2(local height, local horizontal distance); thus an elevated target causes local UP output, which returns to zero as the body pitches to face it. This is angular aiming, not a command to fly to the target's altitude. Directly overhead/below targets still produce vertical correction without an arbitrary yaw command. Existing target filters, locking, behind-target turn stability and lifecycle signal cleanup remain in use.

Use one Navigation module and wire its faces separately to left/right turning and up/down pitch controls. The module supplies analog redstone commands; actual motion still requires the appropriate mechanical controls. Preview adds green UP and purple DOWN rays. Dashboard status displays L/R/U/D powers.

Validation: assemble and static review. No client launch or gameplay tests. Pending playtest: targets above/below, pitch toward alignment, independent yaw/pitch dead zones, vertical toggle, six face wiring, and disabling/releasing the core.
