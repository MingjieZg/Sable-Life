# 开发记录

## 来源与版本

- NeoForge MDK: https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle
  - commit: `4e1be6e906e1b32a753e3580af4ea1bcc3dbc79e`
  - 保留原模板 `TEMPLATE_LICENSE.txt`；采用其 Gradle wrapper 和开发插件版本。
- NeoForge 1.21.1 文档: https://docs.neoforged.net/docs/1.21.1/gettingstarted/
- Sable 源码: https://github.com/ryanhcode/sable
  - 参考 commit: `6f2b321e9fa01f35e47b2258914e3e1a2490a608`
  - 该 commit 的 `gradle.properties` 声明版本 2.0.5。
- Sable 开发文档: https://github.com/ryanhcode/sable/wiki
- 正式模组依赖按 `mod-dependencies.lock.json` 校验，不重新发布第三方源码或模组。

## 已查证的接口

- `Sable.HELPER.getContaining(level, BlockPos)` 查询方块所属物理结构。
- `SubLevel.getUniqueId()` 提供结构 UUID。
- `SubLevelHelper.getConnectedChain(root)` 遍历 actor 的 `sable$getConnectionDependencies()`。
- `getLoadingDependencyChain()` 会额外包含相交结构，**不能用作生命体归属判据**。
- `SubLevelObserver` 目前公开的是 add/remove/tick；不能假设有一个完整的 split 事件可直接订阅。
- `BlockSubLevelAssemblyListener` 提供移动前后回调；结构搬移期间旧块尚未删除。核心 `onRemove` 不能简单地一律视为死亡。
- `BlockEntitySubLevelActor` 有游戏 tick 和物理 tick 回调。
- `SubLevel.logicalPose().transformPosition()` 将 Sable 存储坐标转换为世界坐标，用于视线校验和命中反馈。
- Sable 为 `Level.clip()` 接入实际结构方块射线检测，并为 `Player.canInteractWithBlock()` 转换交互距离。不能用结构外包围盒代替方块命中。

`SubLevelHelper` 标注为内部 API，因此当前适配器锁定 Sable 2.0.x，升级时必须重新验证。这些发现来自源码阅读；所有实际关节的连接传播和断裂时序仍需游戏中测试。

## 代码边界

- `heart/`: 生命状态机、核心方块和 NBT 持久化。
- `compat/sable/`: Sable 结构查询、当前连接中的核心查找和世界坐标转换；后续接入持久化所有权及移动／分裂生命周期。
- `combat/`: 玩家近战、箭的实际方块命中转发、服务端扣血和反馈。
- `item/`: 创造解除工具，恢复普通结构和默认未激活核心。
- `command/`: 需要管理员权限的开发命令。
- 后续添加 `body/`, `attack/`, `loot/`, `ai/`, `client/`，当前不创建空壳模块。

## 0.1.1-dev 交互与受伤

空手右键核心仅查看状态；潜行空手右键尝试激活。已激活时单独提示当前血量，不重置生命值。死亡核心仍不能重新激活。

`BodyCombat` 订阅 NeoForge 的 `PlayerInteractEvent.LeftClickBlock` 和 `ProjectileImpactEvent`。伤害仅由服务端计算：

- 近战只处理 `START`，检查交互距离、世界交互权限与实际方块视线，遮挡或超距命中不扣血。空心形状的方块中心未命中时，再沿玩家视线检测。
- 空手、剑和其他非采掘物品命中已激活身体时，两侧取消挖掘事件，服务端补发方块状态，避免创造模式直接删块。镐、斧、铲、锄保持挖掘行为；这不是完整的材料保护。
- 近战伤害取攻击属性并应用蓄力倍率；成功命中后重置攻击蓄力。同一玩家 4 tick 内的重复开始包不会重复扣血。
- 箭在实际 `BlockHitResult` 上按速度和基础伤害计算扣血；三叉戟走独立的投射物命中路径，按原版基础伤害 8 点处理。箭和三叉戟的 `SableLifeHitCreatures` 持久化数据记录已命中的生命体 UUID，避免嵌入、重载或再次移动后重复扣血；最多记录 64 个生命体。
- 箭命中事件保持未取消，让 Sable 继续执行嵌入与物理冲量。声音、粒子使用转换后的世界命中点，攻击者收到扣血与剩余血量反馈。

每次命中都重新查询 `getConnectedChain()` 内的核心；普通地形、休眠核心、无核心的断开碎片不转发伤害。发现多个核心时拒绝选择归属。当前只有连接查询，没有持久化部位所有权，因此尚不提供断肢战利品和重接后的奖励去重。

这条方块受伤路径尚未接入原版实体战斗的全部附魔、药水／着火效果和爆炸伤害。实际测试结果和剩余限制以 `STATUS.md` 为准。

## 0.1.2-dev 创造解除

`CreativeReleaseToolItem` 使用 NeoForge 的 `onItemUseFirst`，优先于核心查看和机器自身交互；客户端仅返回交互结果，服务端检查创造模式、距离与 `mayInteract` 后执行。无效目标或非创造玩家返回 `FAIL`，避免继续触发核心激活。

`findConnectedHearts` 收集当前连接图中的全部核心，包括死亡和多核心冲突状态；普通战斗仍只接受唯一已激活核心。解除工具也能直接处理拆装到地面的核心。

`HeartBlockEntity.resetForCreative` 再次检查创造模式与服务端归属，替换为默认 `DORMANT` 生命状态、生成新生命 UUID、清除 `BoundBody` 和死亡倒计时、将脉冲和 `ACTIVE` 归零，再同步 NBT、方块状态与红石邻居。此操作不经过死亡或掉落流程。未激活核心不参与 `BodyCombat` 的破坏拦截，因此结构恢复普通创造拆改。

这是作者使用工具进行的明确重置；普通右键、游戏内死亡和 NBT 重载仍遵守单向生命状态。未来增加持久化身体、部位及奖励登记时，需要将相应登记注销接入同一解除流程。

## 0.1.3-dev 三叉戟与生命结构扩建

`BodyCombat` 不再排除 `ThrownTrident`。三叉戟命中真实身体方块时按 8 点原版基础伤害扣除共享血量，仍使用投射物 NBT 做生命体去重，并保留 Sable 的嵌入和物理冲量。

Sable 默认会通过 `BlockPlaceContext` 和 `BlockItem.canPlace` 的物理遮挡检查拒绝在子世界实体上放置方块。`LivingBlockPlaceMixin` 和 `LivingBlockItemPlacementMixin` 只为创造模式下“当前命中方块连接到唯一且存活的 Heart”放开外露面扩建，同时保留可替换位置、方块生存和玩家交互检查；生存模式的方块右键会在交互事件阶段被拒绝。死亡、死亡中、休眠或多核心结构不会获得这个通道，需先使用创造解除工具。

## 0.1.4-dev 近战附魔与建造权限

近战伤害沿用原版的攻击蓄力倍率，并通过 `EnchantmentHelper.modifyDamage` 应用主手武器的伤害附魔，例如锋利 IV；附魔额外伤害也会随攻击蓄力缩放。弓的拉弦伤害和三叉戟的 8 点基础伤害路径保持不变。

生命结构扩建现在只对创造模式开放。除创造放置的专用通道外，生存玩家手持方块右键存活生命结构会被 `RightClickBlock` 事件取消，避免绕过 Sable 的放置限制。

## 0.1.5-dev 暴击与死亡结构建造锁

近战在满蓄力且满足原版空中攻击条件时按暴击处理：基础蓄力伤害乘以 1.5，并发送 `CRIT` 粒子和暴击音效；冲刺、攀爬、入水、失明、骑乘或未达到满蓄力时不会触发。武器伤害附魔仍沿用原版附加伤害路径。

生命结构的生存放置拦截覆盖存活、死亡中和已死亡状态，避免死亡后绕过保护继续搭建。死亡结构仍需创造解除工具才能恢复普通拆改；解除前创造模式也不能直接拆除其方块。

## 下一阶段必须解决

0.1.6-dev 已完成归属和材料保护基础：`BodyOwnership` 按维度持久化方块坐标到 Creature UUID；激活和已激活核心首次加载时登记当前连接图。`BodyAssemblyMixin` 包装 Sable `moveBlocks`，在邻居回调前保护新位置，完成后清除已移走的旧位置；Sable 复用存储位置时先清除旧记录。`BodyReplacementMixin` 防止新方块继承旧位置的保护。

方块战利品入口覆盖挖掘和爆炸，`BlockDropsEvent` 取消挖掘掉落及经验；`Containers.dropItemStack` 拦截原版库存散落。核心解除清除该 UUID 的登记；无核心碎片解除仅覆盖其当前相连结构。自然分裂、重复组装、核心移除、保存往返和爆炸掉落均有集成测试。死亡状态、奖励结算和核心破坏处理还需迁移到世界级生命周期记录。

1. 所有权保存在世界级持久化登记中，核心被破坏后仍能执行清理。
2. 区分组装搬移、拆装、卸载、分裂与真正销毁；找出分裂片段来源。
3. 验证所有受支持关节的连通规则，避免只检查同一 sublevel UUID。
4. 同步材料保护到分裂残骸，覆盖挖掘、爆炸、库存内容和机械拆装。
5. 扩展伤害来源、附魔与效果；单结构旋转／移动命中已有回归测试，继续验证不同关节下的命中和连接变化。
6. 扩展真实 assembled 结构的生命周期集成测试，再接攻击模块和部位奖励；补上死亡清理及 Part Marker。

## 独立测试实例

开发客户端通过 `runClient` 启动，配置和世界在 `runs/client`。普通开发服务端在 `runs/server`；`runGameTestServer` 在 `runs/gametest` 的独立世界运行集成测试。不读写原游戏存档。

Java 21 与 IntelliJ 为本机已有；Gradle 和 NeoForge 缓存位于本项目，未修改系统默认 Java。

## 0.1.7 世界级死亡任务与破坏设置

`LifeLifecycle` 保存在主世界 SavedData 中，以 Creature UUID 保存死亡倒计时、清理/结算标记、奖励坐标、战利品表、经验及不可破坏集合。核心致命受伤立即登记任务；`HeartBlock.onRemove` 在非搬移且真正换块时转入相同流程。`BodyOwnership.MOVING` 区分 Sable 搬移与销毁。

ServerTick.Post 执行任务，每任务每维度最多删除 256 个已加载的归属方块；删除期间保持归属来抑制容器散落，删除成功后去登记。未加载方块保留待处理。普通战利品不参与清理奖励，指定战利品通过 chest 上下文直接生成物品实体并发放经验。完成 UUID 留作去重记录，已清理的记录不再扫描身体。结算标记与物品实体保存并非跨文件事务，突发断电的一致性仍是后续工作。

`options` 命令只允许未激活核心配置 allowBreaking、DeathLoot、DeathExperience，保存进核心 NBT。激活时将开关发布到世界级登记，以便核心移除、断肢后仍生效。BreakEvent、ExplosionEvent.Detonate 和 Level.destroyBlock 拦截标准破坏；死亡内部 removeBlock 不受此开关影响。重置会取消死亡任务、恢复配置默认值并清除归属。GUI、独立死亡信号和部位奖励尚未实现。

## 0.1.8 死亡反馈

LifeLifecycle 在奖励结算标记写入后发送 FLASH、FIREWORK、紫色 Dust、POOF 和烟花声音，使用原版服务端粒子同步。沿用 rewarded 标记去重，无额外实体或伤害。


## 0.1.9 当前实现（替代上节死亡反馈时序）

LifeLifecycle.schedule 首次建立死亡任务时发送烟花，已有任务不重播；BodyOwnership.cleanLoaded 实际移除方块时发送 POOF，每个清理批次最多 16 处烟雾。默认延迟仍是 60 tick。

MeleeModuleBlockEntity 通过同归属、同 sublevel 方块的碰撞形状判断接触，ContactBox 使用 OBB/AABB 分离轴测试。相邻 tick 的平移和旋转插值采样，红石攻击窗口内按目标 UUID 去重并保存。失去存活核心立即停止；仅在原核心仍加载时锁定脱离，避免把核心卸载误判为断肢。当前无阵营过滤、GUI 或 Part Marker 奖励。断开后在模块获得 tick 前立刻重新连接的边界尚未覆盖。

## 0.1.10 持续范围攻击

移除通电窗口 Victims 黑名单和存档字段，旧存档字段自动忽略。红石开启时每 tick 对完整旋转立方体进行接触判定，包含空格；每目标每次扫描最多调用一次 hurt，保留原版受伤免疫间隔。关闭红石立即停止并重置扫击历史，断肢/死亡停攻不变。

## 0.1.11 Part Marker

PartMarkerBlockEntity 在 BodyOwnership.capture 激活阶段绑定，避免首次 ticker 前拆分漏记。断开后要求原始已加载核心仍存活、没有整只死亡任务，才认领选中 Sable connected chain 中同 Creature 的归属方块。BodyOwnership 为这些方块增加 Part UUID；Creature UUID 不变。BodyAssemblyMixin 同时搬移两种归属，故后续分裂、标记销毁不会丢掉清理范围。

LifeLifecycle 扩展死亡任务记录为可选 Parent UUID、PartName 的部位任务：按 Part UUID 清理，独立保存延迟、奖励及完成去重。主体 reset 取消所有子任务；选中残骸完全解除后取消其任务。SableBodyProbe 拒绝已认领断肢转发受伤/恢复攻击。核心显示已丢失名称，part_lost 命令可供条件命令链查询。

当前一个断开连接分量只接受一个 marker，多个 marker 时最先 ticker 的配置生效。整体死亡不为仍连接 marker 创建额外部位奖励。标记在断开检测之前被击碎不产生该奖励；后续可加入独立于标记实体的预登记部位定义。

## 0.1.12 掉落模板菜单

PartLootMenu 使用 vanilla GENERIC_9x3 ChestMenu/屏幕，上方 27 格是独立 SimpleContainer 显示副本。服务端拦截模板点击和 Shift 背包点击，只复制 ItemStack；禁用模板的拖动、双击收集及提取行为。点击后全量同步槽位和光标，修正 vanilla 客户端的普通箱子操作预测。广播时刷新模板支持多查看者，菜单每次编辑校验距离、实体仍存在、创造身份和未生命化状态。部位方块的空手和持物右键均打开菜单；创造解除工具仍保持优先处理。

PartMarkerBlockEntity 保存 TemplateMode 与 LootTemplates（ContainerHelper/物品组件）。首次编辑切换模板模式；part 命令切回 loot table 模式。断肢登记时复制整个奖励快照到 LifeLifecycle 并持久化，因此后续标记移除、重载不影响物品组件或数量，已结算任务不重复发放。模板没有物品 capability，不暴露给漏斗，也不在方块移除时散落。

## 0.1.13 权限与右键增量

openLootMenu 在创建菜单/发送槽位前拒绝非创造玩家；PartLootMenu.stillValid 同样检查创造模式，覆盖打开后切换模式。右键按 ItemStack.isSameItemSameComponents 判断是否累加，遵守 getMaxStackSize 上限，不同组件替换为 1 件，原光标物品不变。

## 0.1.14 近战设置与预览

MeleeSettingsMenu 沿用 vanilla 9×3 屏幕，服务端图标槽位调用 MeleeModuleBlockEntity.adjust，不允许提取/拖动/双击收集图标。每次操作检查创造模式、距离、当前块实体、BodyOwnership 与绑定状态；已激活只读。效果选择为内置常用效果列表，原命令仍可指定其他已注册效果。

预览保存在块实体临时 UUID→60 tick 映射，每 5 tick 向指定在线创造玩家发送紫色 Dust 边框，使用实际方块坐标经 Sable pose 变换；远离、换维度、切换模式或倒计时到期自动停止。不保存预览状态，不产生攻击。

## 0.1.15 声音模块

SoundLibrary 启动时扫描 config/sablelife/sounds 顶层 .ogg，跳过符号链接、非 Ogg 头、超限文件，按文件名确定 UUID 音频 ID；生成独立 generated_sound_pack 的 sounds.json 与资源。CustomSoundPack 在客户端 AddPackFindersEvent 注册必选资源包；专用服务端不加载客户端类。导入仅做文件头检查，Vorbis 解码交由 Minecraft。

声音模块绑定原 Creature UUID，靠近扫描 server.players 与变换后的世界中心；进入集合+独立冷却阻止循环播放。HeartBlockEntity.damage 成功后通知连接图中的声音模块，受伤音效同样冷却。绑定、拆分与部位归属限制阻止残骸继续发声。声音通过变量范围 SoundEvent 发送，试听使用 playNotifySound 仅发送操作者。开始播放的声音不主动中止。

SoundSettingsMenu 是创造专属图标界面，激活只读；自定义文件显示名称与声音 ID 分开，配置持久化。游戏重载时进入集合与冷却重置，玩家重新进入世界可能触发一次靠近声音。

### 空 Sable 存储区归属清理修复

BodyReplacementMixin 观察非搬移情况下非空→air 的真正删除，调用 noteRemoved。BodyOwnership 将待清除标记保存进归属 NBT，仍保留归属直到本 tick 所有普通掉落回调结束，LifeLifecycle 的 ServerTick.Post 入口统一 flushRemoved。若原空存储区已消失，无需强制加载即可清除已确认删除的坐标。bind/forget 撤销排队标记，避免复用坐标的新方块被误清理。真实未加载但没有观察到删除的方块仍继续等待加载。

## 0.1.16 喷火模块

FlameModuleBlock 使用六向 FACING 方块状态，界面在未激活时可切换朝向。服务器 tick 要求原 Creature UUID、仍连接核心且 ALIVE、未登记部位、持续红石供电；断肢状态保存在 NBT。发射点、方向由 Sable logicalPose 转世界坐标，近场范围为渐宽火束，每个目标分别做方块射线检测。Sable clip 命中存储区时把结果投影回世界坐标；粒子采样步数上限 64。攻击用原版火焰伤害与点燃判定，保留原版受伤无敌帧和抗火效果。参数在创造菜单服务端调整，激活后锁定。

## 0.1.17 部位战利品规则

PartMarkerBlockEntity 持有 27 个模板、独立概率与权重数组、加权模式及抽取次数；旧 NBT 缺失时概率和权重均为 100。PartLootMenu 改为 vanilla 9×6，模板仍在前 27 格，后 27 格只显示控制图标；服务端拦截图标和模板槽位所有提取方式，库存起始槽位 54。模板展示副本附加概率/权重 lore，奖励 ItemStack 自身的组件不被修改。

断肢登记时 LifeLifecycle 复制全部物品和规则；世界存档的 Death 任务持久化这些字段。LootSelection 在奖励结算时按每格独立概率，或按有放回权重抽取生成物品副本。Rewarded 标记沿用一次性结算控制。旧任务未保存新字段时使用 100% 独立概率及 1 次默认抽取，避免升级后旧奖励消失。

## 0.1.18 核心菜单与整体奖励

HeartSettingsMenu 用 vanilla 9×3 图标槽位配置 HeartHealth、死亡经验及允许挖掘开关。普通设置只允许 DORMANT 创造玩家编辑；挖掘总开关允许 DORMANT/ALIVE 创造玩家切换，ALIVE 时立即更新 LifeLifecycle 的 unbreakable 集合。菜单每次操作校验玩家模式、距离及块实体身份，图标不可拖取或移入背包。

HeartLootMenu 的 27 格为整体奖励模板副本，编辑转入模板模式；资源表模式由原 options 命令切回。HeartBlockEntity NBT 保存模板模式和完整 ItemStack 组件。Heart 达到零生命时，LifeLifecycle 在创建死亡任务时复制模板，之后即使核心被移除，结算仍使用世界存档中的奖励快照。创造解除清空模板并恢复原有默认数值。

## 0.1.19 Jade、Boss 血条与箭矢模块

Jade 15.10.6+neoforge 作为开发运行依赖安装到 `.dev/mods`，在 `mod-dependencies.lock.json` 中固定官方 Modrinth URL 与 SHA-512。它在准星处显示方块和生物资料；本模组不直接依赖 Jade API。

生命核心菜单增加 Boss 血条开关与名称格。创造玩家可将铁砧改名的命名牌拿在光标上点击名称格复制名称，不消耗命名牌；空光标右键恢复默认名称。名称限制 64 个 Unicode 字符并拒绝控制符。存活时仍可修改这两项及身体挖掘开关；其他数值保持锁定。Boss 血条默认关闭，仅存活且距离核心 64 格内的非旁观玩家可见；血量和名称跟随核心更新，死亡、关闭、卸载及创造解除时移除。名称与开关写入核心 NBT。

箭矢发射模块是下一阶段投射攻击：六向放置，创造菜单调整基础伤害（1–1000）、间隔（2–200 tick）、飞行速度（0.5–5）和朝向。连接存活核心且通电时发射原版真实箭矢；断电、死亡或脱离主体停止，箭矢不可拾取。发射点与方向通过 Sable pose 转换到世界坐标，生成到主世界层。已发射箭矢遵循原版轨迹与碰撞。

## 0.1.20 范围脉冲

PulseModuleBlockEntity 在 BodyOwnership.capture 时绑定原核心 UUID，避免模块首次 ticker 前拆下造成误攻。仅当仍连接存活核心且持续通电时，按可配置间隔释放球形冲击；断电重置间隔，下次通电立即发出。目标用世界空间中心距离筛选，并从发射器外侧到目标做 COLLIDER 射线检测，墙体阻挡伤害。伤害使用原版 generic 来源和无敌帧；击退沿离开模块的水平向量。服务端同步 Sonic Boom 中心与 Electric Spark 圆环粒子，不改动地形。

默认半径 5、伤害 6、击退 1、间隔 40 tick。创造菜单限未生命化时修改，半径 1–16、伤害 1–1000、击退 0–100、间隔 5–200 tick；NBT 读取夹取边界。已登记断肢以及原核心仍加载却失联的模块停止攻击，并保存 detached 锁存。箭矢模块同样补充了 capture 时的预绑定。

## 0.1.21 目标感应模块

TargetSensorBlockEntity 在激活 capture 时绑定原 Creature UUID。服务器 tick 首先检查是否仍连接存活核心；不满足立即将红石信号设为 0，并通知邻居。活跃时每 5 tick 在世界姿态转换后的中心附近扫描 LivingEntity；默认只选择非创造、非旁观玩家，可切换其他生物或全部。半径按目标碰撞箱中心判断，可选 COLLIDER 射线视线检查；起点移出模块自身碰撞箱。检测到目标输出 15 级弱红石，供相邻攻击模块的 `hasNeighborSignal` 使用。死亡、断肢立即熄灭；输出状态不写存档，重载后安全地从 0 恢复扫描。

创造菜单配置半径 1–32、目标模式与视线遮挡；激活后只读。尚无路径规划或自动旋转，此模块是 AI 阶段的感知与攻击触发基础。

## 0.1.23 Heart 无线控制

HeartWireless 的每个频道实现 Create IRedstoneLinkable，返回 Heart 原始 plot BlockPos，复用 Sable Create 范围转换。频道时序按共同已加载游戏刻推进，配置与计时持久化；死亡／解除／移除／卸载注销发射端。Create 为明确必需依赖。

HeartWirelessMenu 同步 16 × 6 设置、输出与运行时间；双频率槽为不可取出的复制样本。服务器校验创造模式、核心距离、生命状态与参数范围。独立 HeartWirelessScreen 使用侧栏、输入框、波形和可展开玩家物品栏，不沿用 ChestMenu 的格子按钮布局。客户端通过原版 ContainerButtonClick VAR_INT 传送频道／字段／值，服务器夹取数值。

新增四项 GameTest：波形／保存、菜单权限／复制样本、真实 Create 接收器／重载／死亡、同频率其他 Heart／解除／卸载。实际客户端布局与移动关节联动仍需人工验证。

## 0.1.24 统一核心与标记基础

新增 module 包：VirtualModule（UUID、类型化通用参数、未知类型/新版本保留）、HeartModules（32 个实例与 revision）、HeartDashboardMenu（仅打开菜单玩家的不可提取快照槽）、ModuleNetworking（有长度限制的 Action payload，校验 container id/revision/创造/距离/休眠），以及独立 HeartDashboardScreen。

暂时仅构建配置数据；所有虚拟类型明确标注 authoring only，没有攻击、声音或 Loot 执行器。原实体模块不迁移、不删除。Core/无线/整体 Loot 旧配置入口整合到统一导航，原无线模块数据没有另存副本。HeartModules.triggered 直接读取 HeartWireless 当前输出或锚点附近 ModulePower，为下一阶段执行器共用。

ModuleAnchor 保存所属刚体 UUID、相对 plot 中心的块偏移和局部面法线。两点范围选区包含端点块；标记限制同一个刚体且每轴不超过 32 格。MarkingSessions 为玩家创建 120 秒临时右键会话，服务端射线/距离/连通/修订号校验，左键防误拆，Esc 或潜行右键取消，完成原子替换。已保存标记可向该创造玩家预览 5 秒。

AnchorTransfers 复用 BodyAssemblyMixin.moveBlocks 的准确块迁移记录：在搬移前记录每个锚点来源，搬移后定位 Heart 与模块 UUID，更新到实际目的结构、局部块与旋转后的方向；失败时锁定失效。BodyReplacementMixin 在非 Sable 搬移的方块替换时锁定标记失效，原位补回不能恢复。此版仅定位标记点，实际近战选区占用与扫掠属于下一阶段。

模块数据保存到 Heart NBT，但不加入周期性的 BE 更新标签；菜单仅向当前查看者同步。能力激活期间只读；解除保留虚拟模块和无线配置。标记会话不进存档、玩家退出自动回收。

已加入重复实例/NBT/未知版本、菜单权限/修订/无线保存、标记有效性与拆除、物理移动旋转、取消/冲突/原子提交、真实 Sable 拆分迁移、内置无线频道触发的 GameTest。独立客户端 UI 与接收面朝向仍待人工测试。

## 0.1.25 专属参数和输入保护

`ModuleFields` 定义各模块字段、范围、默认值、枚举和 tick／秒显示换算；`VirtualModule.Settings` 保存类型专属配置，同时读取 0.1.24 的顶层通用字段。Channel 修改必须使用无线触发模式，读取旧配置不受该编辑限制。

`HeartDashboardScreen` 每页显示三个专属参数。聚焦 EditBox 后在父容器快捷键之前接收所有非 Esc 按键，避免背包绑定关闭菜单。`HeartWirelessScreen` 使用相同输入保护。`VirtualLootMenu` 复用已有战利品主题界面及复制工作流，保存模板而不消费光标物品；仍属于 authoring 数据，不参与奖励结算。

验证：89 项 GameTest 通过。客户端输入与视觉布局仍需按 PLAYTEST_0.1.25.md 人工确认。

## 0.1.26 攻击运行时

HeartModules 持有每核心独立的 ModuleCombat，在 HeartWireless 更新后执行。状态包含模块冷却、近战逐目标命中冷却及前一刻中心／朝向；不保存运动历史，停用、卸载、解除及读取 NBT 时清理，避免跨传送或停用间隔扫掠。

近战角点区域含端点方块，ContactBox 以肢体四元数执行旋转盒碰撞。候选实体查询使用扫掠中心 AABB，加半径包络；平移与旋转之间最多 64 次采样。位移超过 8 格视为传送，避免横扫整个路径。两点各轴最大 32 格，加载后的设置也验证该限制。

远程能力使用标记表面或配置局部方向，经所属肢体 pose 转成世界坐标；火焰和脉冲用 Sable 支持的 clip 校验实体／地形遮挡。箭矢复用现有 ModuleArrow，其基础伤害仍按原版箭速度结算，散射半径定义在前方 8 格平面。

断肢 Detached 标志存入模块配置。未加载不锁存，重新标记清除失效和断肢标志。现有实体方块保持兼容，未自动导入或删除，作者应避免在同位置同时布置实体与虚拟攻击。

验证：build 成功；94 项 GameTest 通过，新增五项虚拟战斗集成测试。

## 0.1.27 接收与断肢结算

WirelessChannel 增加 Receiving NBT；HeartWireless 用 IRedstoneLinkable receiver 注册真实 Create 网络监听器，与该频道的 transmitter 互斥。菜单保留原数据索引，追加 RX mode / input 同步字段；菜单操作 9 修改频道模式。模块 trigger=2 为内部 output，trigger=3 为外部 input；channel 只允许两种无线模式编辑。死亡、卸载、解除和重载均移除网络监听。

ModuleLoot 每核心检查绑定锚点所在连接组，确认不包含核心、仍属于当前 Creature UUID 后声明 part 清理组。任务 UUID 根据 Creature UUID + Module UUID 确定，模板快照交给 LifeLifecycle.schedulePart；module.RewardOwner 防止重载再次注册。已登记任务继续独立清理；同一个片段只允许首次成功声明的奖励组。当前只支持仍存在的锚点，标记时应选择断肢后保留的方块；未加载不结算，直接移除唯一锚点不猜测附近断肢。

DashboardSelection 使用弱引用核心键保存玩家当前会话的选择，快照 Ready/Selected 让客户端在首个有效快照恢复选择。浏览不增加配置修订号，仍验证创造权限和距离。

ModuleCombat.overrides 检查同类型标记指向的实体发射点，避免同时执行实体与虚拟攻击；不是整体旧配置导入。ModulePower.hasControlSignal 不读取邻接核心的心跳。

验证：99 项 GameTest 全部通过；断肢重载测试独立批次，防止其主动替换世界 SavedData 与其他测试串扰。

## 0.1.28 感应与事件音效

HeartModules 在 combat 前运行 ModuleEvents，感应结果同 tick 可驱动虚拟攻击。ModuleSignals 为弱引用核心/世界的瞬时位置输出表；ModuleSignalMixin 合并 BlockStateBase.getSignal/getDirectSignal 原返回值，不替换标记方块，也不降低其原有红石。变化调用邻居更新。ModulePower 的对角邻接校验仍要求同一 Creature UUID。

ModuleEvents 的声音状态按 Module UUID 保存进入范围的玩家集合及 Approach/Hurt 两个冷却时刻。配置不变，运动点由当前锚点解析；清理运行时不保存集合和冷却，不跨卸载扫描扫过路径。受伤通知在核心扣血成功后执行。声音与感应断肢停用锁存沿用战斗模块规则。

旧声音/感应方块仅在同类型虚拟模块占用该点时停用自身执行；并未自动导入参数。环形保持世界水平，前方使用肢体四元数变换后的标记面。104 项 GameTest 全部通过，新增五项集成测试。

## 0.1.29 侦测几何与音效事件

shape=0 / RING 存档枚举保留，但几何改为球形距离，UI 显示 Omnidirectional。原先固定高度带已移除。内半径仍可作为球心空白范围，零表示完整球。

SensorGeometry.sightStart 根据肢体逆旋转计算离开整个标记体素的距离；visible 最多跳过 128 个归属本 Creature 的体素，每次沿原射线越过旋转体素边界。地形和其他 Creature 第一次命中即遮挡。实体及虚拟感应复用此检查。

ModuleEvents.Sound State 增加 AmbientDue、ContactDue、Touching。环境音先等待随机 min/max 间隔，每次播放后独立抽取下一间隔；脚底探测从标记块下表面外沿，随当前肢体 orientation 旋转，接触 false->true 时才按冷却发音。停机仍清空瞬时运行状态；新增字段不改变旧模块默认声音。

实体感应及世界 SavedData 替换测试改为独立批次，避免其他并行测试的红石、物理对象或重载影响断肢奖励断言。
