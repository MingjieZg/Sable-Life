# Move Set panel

Version 0.1.108 adds a separate `Move set` module. Existing Move dispatcher and Pulse sequence modules remain available and keep their channel based behavior.

Open the module list, add `Move set`, then open `Edit move set`. Each move has a name, enable switch, weight, cooldown, rest and target conditions. Its action list contains existing attack and effect modules. Adding an action automatically changes that referenced module's trigger to `move set`; the action timing is then controlled by the move table. Overlapping action windows run together. The action up/down buttons change their order in the table, while the timeline shows their overlap.

The Move set scheduler does not create wireless channels. A table is started by its own trigger (for example Always or internal wireless) and can run alongside legacy dispatchers. Referenced actions use `Trigger: move set`. The table still requires a live, marked referenced module. Editing a core module cancels a running table so a changed configuration cannot continue with stale timing.

For choice buttons, left click advances and right click goes back. Numeric fields accept direct entry and scrolling; selection weight uses whole number steps. Since 0.1.111, Wireless redstone has its own red/blue item frequency slots for external Create signals. These do not use a numbered wireless channel. Leave both empty for Mark-only output; filling them also allows simultaneous local and external output. Click the slots to open the inventory sampler; samples are copied without consuming items.

Version 0.1.111 also adds module type and function tooltips to module lists, including renamed entries. The Move Set action rows are 17 pixels apart, with compact adjacent ordering controls and up to 10 rows per page, depending on screen height.

Since 0.1.112, the module frequency row reads External Wireless, shows its red/blue samples, and provides a Frequency Item button. Ignore it when only using Mark. During Mark area/origin, sneak-right-click into air clears the current module's old marks (including an arena center), stops its preview, and returns it to the unmarked state. Escape still cancels without changing the previous marks. The server verifies the active marking session and air ray before clearing.

Rollback points:

- Git tag `stable-0.1.107-before-move-set` points to the pre-panel source snapshot.
- Branch `codex/move-set-panel` contains the 0.1.108 panel work.
- The standalone project archive is `backups/Sable-Life-0.1.107-dev-20261004-193405.zip` with SHA-256 `17700e64e03e90d9cc4c16e7c04d5032ab7675df132e9cc278a95e7b517def03`.

The first panel pass uses buttons and pagination; timeline dragging and future mechanical action types are intentionally left for a later pass.

## 当前已确定的招式失效规则（2026-10-06）

用户决定保留当前规则：任一引用动作模块失效，整条Move停用并退出候选，不跳过损坏动作运行剩余动作。其他完整Move继续；运行中的失效Move取消剩余调度，普通模式经过Rest后继续选择，Hurt Once等待下次受伤。模块是否需要Mark依自身规则，Wireless redstone与虚拟命令无需Mark的合法使用不算失效。完整说明与手册实例见[MOVE_SET_FAILURE_RULES.md](MOVE_SET_FAILURE_RULES.md)。
