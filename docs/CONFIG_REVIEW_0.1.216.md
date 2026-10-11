# 0.1.216 — 原生一次性重生后的配置检查

检查结论：现有11个 Sable Life 服务端配置都有实际用途，不移除，不改变已有世界的数值。原128方块/16方块实体/8实体/2ms每步建造预算是代码常量，并非现有server config；只留给旧存档中已开始的分批任务恢复，新任务不使用。

| 配置 | 保留原因 |
| --- | --- |
| maximumWirelessChannels | 新频道数量，独立于重生 |
| particlePointsPerPlayerTick | 玩家粒子预算，独立于重生 |
| deathCleanupBlocksPerTick | 逐块后备清理的共享预算；整段原生删除可能一次超过该值，不能当作重生预算删除 |
| maximumProjectilesPerWorld / maximumProjectilesPerCreature | 新飞行物数量，独立于重生 |
| collisionChecksPerTick | 移动/转向碰撞预算，独立于重生 |
| arenaCaptureScanVoxels | 记录时同步原生导出的包围盒扫描量，含空气 |
| arenaCaptureBlocks | 新模板实际方块数量，约束记录及之后一次还原的工作规模 |
| arenaCaptureBlockEntities | 新模板方块实体数量，约束NBT恢复工作规模 |
| arenaTemplateBytes | 每份模板估计内存大小；记录和SavedData读取时检查，仍需要 |
| arenaWorldTemplateBytes | 整个世界模板合计估计大小，仍需要 |

arenaCaptureBlocks / arenaCaptureBlockEntities 的数值不会截断已经保存的结构；更改新记录的限额不等于修改旧记录的方块数量。模板和世界字节限制在SavedData读取时仍实施。注释修正为同步一次放置的用途，数值和键名保持兼容。Photomancy 自己的建造炮配置属于另一个模组，不会因 Sable Life 改变重生接口而移除。

一次放置仍需完整的结构数据和足够加载区域；不能用移除预算保证任意结构零停顿。0.1.215 用户已实测原生重生外形和功能完整。

0.1.217：新增整台准备验证与同tick批量删除，没有增加或移除config键。后备方块预算仅用于不满足整台条件的清理。整台同步路径上限1048576登记坐标、全服务器每tick最多一个尝试；未加载等待20tick重试。
