# 0.1.203 开发验证版

稳定版仍为 0.1.200，未自动提升。

## Arena 预览

红圈：固定标记的场地中心与 arenaRadius。黄圈：ModuleArena.corePosition 的当前世界位置与 warningRange，即玩家警告和范围 Buff 的水平检测范围。没有有效场地中心时仍可预览黄色玩家范围。两种圈只发送给请求预览的玩家，每圈 128 个点；不新增常驻每 tick 广播。

## 铁魔法公共射线坐标

核对 Iron’s Spells 1.21.1-3.16.3 的 RaycastBuilder.performRaycast：Level.clip 可返回 Sable 的 plot 坐标，但原工具直接将该位置用于 expandTowards 和实体搜索。这会把一个短距离法术的查询范围扩展到远处 plot。新增精确版本门控 Mixin，在实体查询前把 BLOCK 命中位置转换成世界坐标，保留 BlockPos、方向和 inside。普通方块与 MISS 不变；没有代理实体。

真实 GameTest 确认原始 plot 命中距施法者超过 10,000 格，转换后命中在 8 格内；原生射线、Ray of Siphoning.onCast 正常返回，前方生物与墙壁仍优先。未找到用户本次吸血射线的新异常栈，附带的 10 月 9 日日志属于旧清除问题，因此不能将用户本次崩溃最终归因认定为已证实，需原整合包回测。

本轮不加入吸血射线对生命体伤害或吸血。它是持续射线，不属于已有直接弹体接口。

## “所有带伤害碰撞箱的飞行物”审视结果

不能只检查实体带碰撞箱或 getDamage 就自动扣血，更不能持续接触每 tick 都扣血：

- BloodSlashProjectile 覆盖 handleHitDetection，以弹体当前包围盒收集生物，victims 列表令每个目标一生只受击一次；未经过已适配的 base onHit 路径。要接入需按原生包围盒与真实身体方块相交，按生命体 UUID 去重，而非每个方块或每 tick 重复伤害。
- MagicArrowProjectile 是原生穿透弹，已接入每弹体/每生命体一次（包括免疫接触消耗窗口）。
- Firebolt / Icicle 的伤害存在 onHitEntity，附带原生额外行为不能由一个 damage 字段自动推出。
- AbstractConeProjectile 用多部分锥形实体和自己的 tick 命中，形状及攻击窗口均不同。
- 火球/火焰箭已经独立处理原生范围伤害与爆炸，通用接触扣血会叠加或改变伤害。

可共用：身体几何/归属查询、部位修正、防御与有界预算；每种原生命中路径仍需确认伤害公式、范围、穿透、重击间隔和额外效果。维持明确接口/已核对家族继承，未来附属沿相同路径即可复用；不承诺所有带碰撞箱的附属自动兼容。

源码依据：官方提交 e4056af90302d37eb1739f5ff05020b020e6e252；对应 RaycastBuilder、BloodSlashProjectile、MagicArrowProjectile、FireboltProjectile、IcicleProjectile、AbstractConeProjectile。仅坐标修复和 Arena 预览已实现，审视清单不表示新增这些法术的伤害支持。

## 验证

54 项真实铁魔法、Arena、审计与防御 GameTest；包含 15 项铁魔法测试。57 单元测试与无铁魔法默认构建通过。新增回归验证远处 plot 坐标、前方实体和墙壁。新黄色预览的用户场景观感及原整合包崩溃需要回测。
