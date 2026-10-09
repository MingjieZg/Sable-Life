# 0.1.118 虚拟命令方块

新增独立紫色模块 **Virtual command block**，不需要 Mark，也没有 Preview 执行按钮。通过 Edit commands 打开左右两栏的命令列表面板。最多 32 条，每条最多 2048 字符；支持添加、复制、删除、上下排序、独立启用和保存。

## 三种类型

- Impulse（脉冲）：模块信号从关闭变为开启时执行一次。
- Repeat（循环）：信号持续时按 Repeat interval 执行，范围 0.05–600 秒。
- Chain（连锁）：跟随紧邻上一条的执行流程。首条连锁没有来源，不会自行执行。
- Conditional（有条件）：要求紧邻上一条在同一轮成功执行。无条件连锁可以继续经过前一条条件失败的连锁；禁用条目会中断连锁。

模块使用现有全局 Trigger，支持 Always、红石、内部／外部无线、Move set 和 Hurt。Move set 中绑定它后，动作开启触发脉冲；动作持续期间循环执行，动作结束后停止。Hurt / Once 每次有效受伤执行一轮，Repeat 也只执行一次；Timed / Stay active 下按正常信号规则执行。

指令以核心的当前世界位置为原点，跟随机械移动。默认无执行实体（`@s` 不会自动代表玩家），朝向为默认世界朝向；需要目标或朝向时用 `execute as / at / rotated` 明确指定。`~` 使用核心位置，`^` 使用默认执行朝向。指令可省略开头 `/`。

添加模块和编辑命令条目需要创造模式及管理员权限等级 2，服务器端校验权限与面板修订号；运行使用权限等级 2，并遵循服务器 enable-command-block 设置。运行中的结果与循环计时不写入蓝图。未来生存精简版不开放本模块。

## 三条命令示例

1. 创建模块，进入 Edit commands，添加三条。
2. 第 1 条设 Impulse / Unconditional：`summon minecraft:armor_stand ~ ~1 ~ {Tags:["sable_demo"],NoGravity:1b}`。
3. 第 2 条设 Chain / Conditional：`execute positioned ~ ~1 ~ run particle minecraft:happy_villager ~ ~ ~ 0.3 0.3 0.3 0 10`。
4. 第 3 条设 Repeat / Unconditional，间隔 1 秒：`particle minecraft:end_rod ~ ~1 ~ 0.2 0.2 0.2 0 5`。
5. 保存每条。保存不会执行指令。将模块触发设为内部无线，绑定测试频道并激活生命体。
6. 信号开启应召唤一个盔甲架并播放第 2 条粒子，第 3 条每秒播放；信号持续不会反复召唤盔甲架。关闭再开启信号，应再召唤一个。
7. 清理示例实体：`kill @e[type=minecraft:armor_stand,tag=sable_demo]`。

## 待人工检查

- [ ] 中英文双栏面板显示、命令长文本水平滚动、保存按钮与 Enter 保存正常。
- [ ] 类型和条件按钮支持左键下一项、右键上一项；仅 Repeat 可编辑循环间隔。
- [ ] 添加、复制、排序、删除、禁用条目及最近成功数显示正确。
- [ ] 上述三条示例正常；首条 Chain 不执行，条件失败时 Conditional 不执行。
- [ ] Move set 绑定命令模块，延迟动作开启时执行脉冲，动作结束时循环停止。
- [ ] Hurt / Once、Timed、Stay active 与重置后的执行表现正确。
- [ ] 核心移动后，相对坐标随核心位置变化；不在 Sable 储存区执行。
- [ ] 总开关关闭、模块禁用、Release 和死亡后停止执行。
- [ ] 保存世界、重新载入、蓝图复制保留指令、类型、条件、间隔与顺序；运行结果不复制。
- [ ] 多人服务器非管理员不能添加／编辑命令；关闭 enable-command-block 后不执行并显示说明。

自动验证：当前 67 项 GameTest 已通过，覆盖三种类型、条件失败后的无条件连锁、循环时长、关闭清理、Hurt 单次及世界坐标、保存复制排序、参数限制和非管理员权限。UI 与机械移动的实际表现待人工验证。
