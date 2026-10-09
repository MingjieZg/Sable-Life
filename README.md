# Sable Life — stable 0.1.200

Minecraft 1.21.1, Java 21, minimum NeoForge 21.1.235, Create 6.0.10 and Sable 2.0.5. Optional Epic Fight compatibility is verified for 21.17.3.1 only. Client/server protocol 6.

See [release notes](docs/RELEASE_0.1.200.md), [stable metadata](docs/STABLE_VERSION.json) and [current status](docs/STATUS.md). The release adds protected-body tool combat animations, native Melee guard/parry support, release-sound naming for Slash and seven vanilla Boss bar colors. 57 unit tests, 282 default GameTests, 81 combined compatibility tests and a real Epic client UI check passed. Original-scene visual retesting remains distinct from automated validation.

Old exports and repository history are retained. The following sections are historical development notes.

---

# Current development: 0.1.198

Includes the optional Epic Fight 21.17.3.1 mainhand melee bridge and death-cleanup fixes using validated whole-plot removal and removed-source heatmap guards. Stable remains 0.1.193. See [0.1.198 release notes](docs/RELEASE_0.1.198.md) and [Epic Fight scope](docs/EPIC_FIGHT_COMPAT_0.1.197.md).

Before compiling the new optional API integration, run `powershell -File scripts/setup-epicfight.ps1` to download the pinned, checksum-verified API JAR. It is compile-only, not bundled or required at runtime. Use `-PepicFightTests=true` and/or `-PcleanupCompatTests=true` for isolated compatibility runs; the latter expects the locally provided Copycats/Toolgun files.

# Current development: 0.1.196

Follow-up fixes for Copycat material drops, assembled CBC cannon cleanup, Sphere Blast self-protection, an independent wireless frequency picker, the 100% adaptive-defense cap and regeneration wheel increments. Client/server protocol 6. Stable remains 0.1.193. See [0.1.196 release notes](docs/RELEASE_0.1.196.md) for validation and limitations.

# Sable Life — current development 0.1.195

Minecraft 1.21.1, Java 21, minimum NeoForge 21.1.235. The last user-accepted stable release is 0.1.193; 0.1.194 is retained as the pre-audit checkpoint.

Read [current status](docs/STATUS.md), [audit changes and validation](docs/AUDIT_FIXES_0.1.195.md), and [current playtest checklist](docs/PLAYTEST_CURRENT.md) first. The following sections are historical development notes; current versions and behavior are defined by gradle.properties and the current documentation.

Build with scripts/dev.ps1 -Task build. Run the supported integration suite with scripts/dev.ps1 -Task runGameTestServer; its default namespace list now covers current modules, native body combat and lifecycle checks. Optional real weapon tests require scripts/setup-test-mods.ps1 and -PweaponCompatTests=true.

The repository can remain private. Uploading source does not grant an open-source license; the mod metadata remains All Rights Reserved. TEMPLATE_LICENSE.txt covers template material.

---

# Sable Life

用于 Create / Sable / Aeronautics 机械生命体的 NeoForge 模组，当前为 **0.1.78-dev 开发原型**。全部模块通过 Heart 统一创建和配置，实体模块已退役；旧注册仅用于存档兼容。

本轮统一 None 开关、标注频道用途，增加警告音量／半径与飞行物轨迹双列表，并修复迫击炮范围伤害。详见 [0.1.78 清单](docs/PROJECTILE_SOUND_0.1.78.md)。

## 固定开发环境

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| Java | 21（本机已有 JDK 21.0.6） |
| NeoForge | 21.1.251 |
| Gradle / ModDevGradle | 9.2.1 / 2.0.147 |
| Create | 6.0.10 |
| Sable | 2.0.5 |
| Create Aeronautics | 1.3.2 |

模组下载地址及 SHA-512 记录在 `mod-dependencies.lock.json`。第三方模组不会打包进本模组发行 JAR。

## 构建与运行

在项目目录打开 PowerShell：

```powershell
# 首次恢复已锁定的模组依赖；已有文件会先校验，不重复下载。
.\scripts\setup-mods.ps1

# 编译并运行单元测试，产物位于 build/libs。
.\scripts\dev.ps1 -Task build

# 启动独立开发游戏，存档位于 runs/client/saves。
.\scripts\dev.ps1 -Task runClient

# 在独立世界运行真实 Sable 组装与 Heart 集成测试。
.\scripts\dev.ps1 -Task runGameTestServer
```

脚本只为当前进程选择 Java 21，并将 Gradle 缓存放在本项目 `.gradle-user-home` 中，不修改系统默认 Java。现有 CurseForge 实例不会被修改。

IntelliJ IDEA 可打开本目录的 `build.gradle`；Project SDK 和 Gradle JVM 选择 Java 21。如果旧版 IDEA 不支持当前 Gradle，可直接使用上述构建脚本。

## 当前原型的操作

1. 在创造模式的 **Sable Life · 生命体** 栏取出“生命核心”，或执行 `/give @s sablelife:heart`。
2. 空手右键 Heart 查看状态、血量及相连的 Sable 结构数量。
3. 用 Aeronautics 将带 Heart 的结构组装起来。
4. 潜行并空手右键 Heart 激活。再次这样操作会明确提示“已激活”，不会重置血量。普通地面上的 Heart 不会把地形当成身体。
5. 激活后，空手、剑或其他非采掘工具左键命中身体方块，或用箭、三叉戟射中身体，都可扣除核心的共享血量。近战会按攻击蓄力、武器附魔和原版空中暴击条件计算；暴击会显示粒子和声音反馈。创造模式下这类近战不会直接删除方块；三叉戟按原版基础伤害 8 点处理。
6. 镐、斧、铲和锄仍按挖掘处理，不作为本版的近战攻击。已登记身体可以挖掘或炸毁，但不掉普通组成材料；脱离核心连接的碎片不会继续向主体传递伤害，仍保留禁止材料掉落的归属。
7. 激活的 Heart 每秒输出一次心跳脉冲（10 tick 开、10 tick 关）；比较器输出 1–15 表示血量。生命归零后输出归零。
8. 可用管理员调试命令配置或扣血；移动结构的命令坐标是 Sable 的存储坐标，常规操作优先右键。

创造模式下，存活且只连接一个生命核心的结构允许继续在外露方块面上放置方块。生存模式禁止在生命结构上放置；死亡倒计时期间也保持限制，随后自动清理。需要保留建筑时请在倒计时内使用创造解除工具。

```text
/sablelife inspect <x> <y> <z>
/sablelife configure <x> <y> <z> <最大血量> <死亡延迟tick>
/sablelife damage <x> <y> <z> <伤害>
/sablelife options <x> <y> <z> <允许破坏true或false> <死亡战利品表> <经验0到10000>
```

配置仅限未激活核心。死亡核心不能通过普通激活复活。默认血量 100，死亡状态延迟 60 tick。

## 0.1.7：死亡流程与破坏开关

生命归零或激活核心被真正破坏，都会启动世界级死亡倒计时。默认约 3 秒后无材料掉落地清理该生命体的主体和已登记残骸。核心被移除后流程仍继续；组装、拆装和结构搬移不会当作死亡。未加载区块会等加载后再清理，每只生命体每维度每 tick 最多清理 256 个已加载方块。

核心的“允许破坏身体方块”默认 `true`。设为 `false` 时阻止玩家挖掘、标准 `destroyBlock` 破坏和爆炸对已登记身体（包括核心、残骸）的方块破坏；攻击仍正常扣生命，死亡清理也仍正常进行。管理员替换方块和绕过标准破坏接口的第三方机器尚不在保证范围内。GUI 后续加入，目前在激活前配置；建议先在地面放核心、用普通坐标设置，再组装。

例如：禁止拆块、指定死亡掉一颗紫水晶碎片并给予 5 点经验：

```text
/sablelife options <核心x> <核心y> <核心z> false sablelife:entities/construct 5
```

默认死亡战利品是 `minecraft:empty`、经验 0；可指定数据包中的战利品表，当前采用 `chest` 上下文（提供死亡起点坐标，不提供攻击者或实体参数）。指定物品与经验在死亡起点结算一次，结算标记与倒计时持久化。创造解除核心会取消待执行死亡任务并恢复默认开关、空战利品与 0 经验。升级前已经死亡的核心在加载后也会进入清理流程。

## 创造解除工具

在 **Sable Life · 生命体** 创造物品栏取出“创造解除工具”（发光剪刀图标），或执行：

```text
/give @s sablelife:creative_release_tool
```

创造模式下，手持工具右键生命结构的任意方块，无需潜行。工具会解除当前相连结构中所有核心的生命绑定，保留建筑方块和机械连接，恢复普通 Sable 结构行为。随后空手创造破坏也可正常拆块。

存活、死亡中和已死亡的核心均可解除；核心恢复为默认未激活状态（100 生命值、60 tick 死亡延迟），清除旧生命身份、绑定和死亡倒计时，关闭心跳与血量信号。可以继续编辑，或空手潜行右键重新激活。解除过程不触发死亡战利品，不影响其他独立结构。直接右键拆装后的核心也能重置。

工具仅限创造模式使用，无生存合成配方。即使生存玩家拿到工具，也无法解除生命绑定。

0.1.6-dev 起，解除核心也会清除该生命体已登记残骸的材料保护。单独右键无核心残骸只解除选中残骸的相连结构，不会重置远处主体。箱子破坏时的普通库存散落被禁止；通过界面或管道取物的权限尚未限制。

## 明确的开发边界

当前原型包含 Heart 方块、状态持久化、信号、Sable 连接查询，以及近战、箭和三叉戟对相连身体方块的共享扣血。近战按攻击属性、攻击蓄力和武器附魔计算伤害；同一玩家至少间隔 4 tick 才会再次扣血；同一支箭或三叉戟对同一生命体只扣血一次，去重记录随投射物保存。

已实现按维度保存的方块归属记录，Sable 搬移、自然分裂及再次组装会迁移归属；核心被移除后材料保护仍然存在。旧存档中已激活核心首次加载时补登记当前连接的身体，升级前已脱离的无核心碎片无法追溯。

**尚未实现远程攻击模块、AI 和完整配置 GUI；部位掉落物品已有箱子式界面。** 完整附魔、药水／着火效果和爆炸对共享生命的伤害也尚未接入。第三方机器自定义掉落、运行时新增关节连接的归属、跨维度和完整退出重进仍需进一步验证。保存记录的正常重载去重已测试；突发断电时跨存档文件的原子结算尚未保证。

已激活 Heart 本身不掉方块物品；未激活 Heart 正常用镐采集。贴图暂时使用原版方块纹理组合。

当前验证结果详见 `docs/STATUS.md`。测试专用模组位于 `src/gameTest`，不会进入发行 JAR。

- 完整需求：`docs/SPEC.md`
- 接口发现与后续工作：`docs/DEVELOPMENT.md`
- 验证记录：`docs/STATUS.md`

## 0.1.9 死亡粒子时序

生命归零时立即在死亡起点播放闪光、烟花火星、紫色光尘和烟花音效。默认 60 tick（约 3 秒）后清理身体，在方块实际消失的位置播放烟雾；移动身体和残骸也随清理位置显示。死亡任务正常重载不会重播烟花，创造解除不触发死亡特效。仅为粒子与声音，不产生爆炸伤害。

## 0.1.9 红石近战模块

创造栏取出“近战攻击模块”，或 `/give @s sablelife:melee_module`。先在地面配置，再把模块和身体一起组装、激活核心。模块收到红石信号时，整个配置范围成为持续攻击区域（包含空格）；默认半径 1，即 3×3×3 方块范围，伤害 4、击退 0.4。范围内空白位置也会造成伤害。空手右键查看状态。

```text
/sablelife melee <模块x> <模块y> <模块z> <半径0到3> <伤害0.1到1000> <击退0到100>
/sablelife melee_effect <模块x> <模块y> <模块z> minecraft:slowness 60 0
```

效果持续时间单位为 tick，等级 0 表示 I，持续时间 0 表示关闭。配置仅在未绑定生命体时允许。通电期间每 tick 检查范围内目标，按原版受伤间隔持续扣血；断电立即停止，无需反复切换红石。判定为以模块为中心、随所在 Sable 结构移动和旋转的完整立方范围，默认 3×3×3。断开核心连接后停攻，检测到断肢后保持停用；主体死亡立即停攻。创造解除后可以重新组装并激活。

当前不区分敌友，排除创造及旁观玩家，遵守原版受伤无敌时间。高速扫击采用最多 32 次插值采样，单 tick 位移超过 8 格按传送处理，极端速度仍需实测。近战配置与范围预览现已加入（见 0.1.14）。



## 0.1.11 部位战利品模块

创造栏取出“部位战利品模块”（黄色方块），或 `/give @s sablelife:part_marker`。在计划脱落的手、尾巴等部位各放一个标记；可与近战攻击模块放在同一部位。先在地面配置，再组装并激活核心。右键标记打开掉落模板界面（见 0.1.12）；名称、延迟和经验仍可用下方命令配置。

```text
/sablelife part <标记x> <标记y> <标记z> left_hand sablelife:entities/construct 60 3
/sablelife part_lost <核心x> <核心y> <核心z> left_hand
```

示例设定为部位名 left_hand，脱离后等待 60 tick（约 3 秒），无普通材料掉落地清理残骸，并在断开时的位置产出一颗紫水晶碎片和 3 点经验。默认战利品为空、经验 0、延迟 60 tick，可使用数据包的 chest 上下文战利品表。配置仅限激活前，或创造解除后。移动结构中的命令坐标是 Sable 存储坐标，建议地面上完成配置。

标记与存活核心真正失去连接才登记一次断肢任务，不要求击碎标记。已登记部位再次碎裂仍归同一任务；登记后标记被破坏也不会丢失任务。残骸保留 Creature UUID、材料保护和破坏总开关，并单独保存部位清理归属；已登记的断肢即使再连接也不能重新攻击或向核心转发伤害。核心暂不可用时等待确认，不因缺少已加载核心立即生成奖励。

核心右键状态显示 Lost parts。`part_lost` 在指定名称已丢失时命令成功、返回 1，否则失败，供条件命令链使用；专用红石接口和 GUI 后续加入。推荐每个可独立脱落部位一个标记、不同名称；同一相连残骸多个标记只接受首先检测到的一份配置。

整体死亡不额外触发仍连接部位的奖励；此前已登记的断肢任务仍各结算一次。创造解除主体会取消其所有待执行部位任务；独立解除残骸会保留选中的残骸，全部残片解除后取消该部位待执行奖励。正常存档重载有去重记录，突发断电仍不保证跨文件原子结算。

边界：标记必须保留到断开被检测时；若先破坏标记，则没有该部位奖励。无标记断肢仍沿用普通生命残骸行为。检测前瞬间断开又接回、跨维度与不同机械关节的实际表现仍需进一步实测。


## 0.1.12 箱子式掉落配置

无需坐标：创造模式下右键部位战利品模块，打开原版箱子式界面。上方 27 格是掉落模板，下方是玩家背包；可以拿着物品右键打开。生命体激活后只读，先用创造解除工具还原再编辑。

- 在背包拿起想掉落的物品，左键点上方格子复制整叠，右键每次累加 1 个同类物品；原物品不消耗。
- Shift 点击背包物品，将整叠复制到第一个空模板格。
- 鼠标空手时，左键模板清空该格，右键减少 1 个。
- 各格按其物品和数量掉落，保留附魔、自定义名称等物品组件；重复格的奖励累加。
- 修改即时保存，关闭界面即可。编辑模板后替代旧战利品表，二者不叠加；清空所有模板代表不掉物品。

这是配置界面，不是储物箱。模板不能取出、丢弃或经漏斗提取；仅创造模式可以打开，生存、冒险和旁观模式不能查看内容。原有部位名、解体延迟与经验不受模板编辑影响，新模块默认 3 秒延迟、0 经验。`part` 命令仍可配置这些数值，同时切回指定战利品表模式；打开界面会提示旧表是否仍在使用。


## 0.1.13 界面权限与数量

仅创造模式能打开战利品界面；激活的生命体仍允许创造玩家查看，但禁止编辑。打开后切换为非创造模式会使菜单失效并关闭。右键相同物品且组件一致时每次 +1，最多达到该物品的堆叠上限；不同物品或不同附魔/名称则替换为 1 个新模板。空光标右键仍为 -1。

概率和权重配置见下文 0.1.17。


## 0.1.14 近战模块设置界面

创造模式右键近战攻击模块，直接使用图标按钮配置，无需输入坐标。鼠标悬停图标查看当前数值与操作说明：左键增加、右键减少；Shift 点击对伤害、击退、持续时间使用大步调整。所有改动即时保存。生命体激活后界面只读，创造解除后可继续编辑；非创造模式不能打开。

- 指南针：半径 0–3，对应 1×1×1 到 7×7×7 的完整攻击体积。
- 铁剑：伤害，普通步长 1、Shift 步长 10，范围 0.1–1000。
- 羽毛：击退，普通步长 0.1、Shift 步长 0.5，范围 0–100。
- 药水：循环选择无、缓慢、虚弱、中毒、凋零、失明、挖掘疲劳、饥饿、发光、漂浮。
- 时钟：效果时长，普通步长 1 秒、Shift 步长 5 秒，范围 0–60 秒；0 禁用附带效果。
- 萤石粉：效果等级 I–V。
- 望远镜：关闭界面并显示约 3 秒紫色范围边框，仅操作者可见。预览跟随结构移动与旋转，不造成伤害。

图标不是物品，不能拿走。范围设置不改变原有通电持续攻击、断电停止和断肢停攻规则。界面不控制机械挥手动作本身。

补充确认：已激活但尚未登记断肢奖励时，直接击碎 loot 方块不会掉落模板，也不会登记部位奖励；其余身体不会因此整体死亡。若已登记断肢任务，之后再打掉标记仍按已保存的奖励结算。关闭核心允许破坏开关时，普通挖掘会被阻止。

后续需求：声音模块（靠近/受伤触发、自定义音频文件夹选择）已记录，留待后续设计。


## 0.1.15 声音模块与数值上限

近战伤害上限保持 1000，击退上限改为 100；界面、命令、NBT 读取使用相同限制。

创造物品栏取出蓝色“声音模块”，或 `/give @s sablelife:sound_module`。创造模式右键配置两个音效：靠近和受伤。左键向后选择，右键向前选择，None 关闭对应音效；绿色/红色按钮分别试听，仅操作者听见。激活生命体后只读，创造解除后可改。

- 靠近触发：生命体存活且模块连接核心时，玩家进入设定半径播放一次，停留不会循环；离开再进入可再次触发。创造玩家也可触发，旁观玩家不触发。
- 受伤触发：核心实际扣血时，从仍连接的模块位置发声，包括致命一击；无效攻击不触发。
- 两种触发各自使用冷却时间，默认 2 秒，可调 1–60 秒；冷却内的新触发被忽略，不排队。
- 靠近半径默认 16，可调 1–64 格；音量 0–4，音调 0.5–2。音量 0 静音。实际听觉衰减仍受原版声音规则影响，触发距离不等于听觉距离。
- 断肢、核心死亡后不再产生新的靠近声；已播放的音频会自然播完。本版不持续循环，也不停止已经播出的长音频。

自定义声音目录：

```text
<project>/runs/client/config/sablelife/sounds
```

把 Ogg Vorbis 格式的 `.ogg` 放入此目录，重启客户端后即可在模块界面循环选择文件名。建议单声道，以便正确体现方位与距离。支持中文文件名，仅扫描目录第一层，最多 128 个文件、单文件不超过 16 MiB。没有自定义文件也能选择内置原版声音。

模组在启动时自动生成并启用本地资源包，保留原音频文件不修改。更换文件内容需重启；删除文件后，已有模块需重新选择可用声音。本版是本地开发实例流程：多人服务器不会自动把文件传给玩家，客户端需安装匹配资源包/音频文件。

## 0.1.16 喷火攻击模块

- 创造物品栏新增喷火模块（铜色机壳、亮炉口为喷射方向），也可 `/give @s sablelife:flame_module`。
- 未激活时创造模式右键配置：距离 1–32 格、每次伤害 1–1000、燃烧 0–30 秒、伤害间隔 1–100 游戏刻、末端半径 0.25–4 格、六向朝向。左键增加、右键减少，Shift 大步；方向逐个切换。激活后只读，生存不能打开。
- 激活生命核心并给模块红石信号后持续向前喷火。默认距离 6、伤害 2、燃烧 3 秒、间隔 10 游戏刻、末端半径 0.75。发射口在方块外，范围随 Sable 姿态变换，目标中心必须进入渐宽火束，墙体及结构实体方块遮挡射线。
- 每次伤害保留原版受伤无敌帧，间隔过短不保证每次都扣血；抗火和免疫火焰的生物受原版规则保护，创造及旁观玩家免伤。
- 断电、核心死亡、断肢后停止新增攻击；目标已受到的点燃不会因断电立刻消失。不会生成地面火焰、烧毁建筑或破坏方块。核心绑定与断肢锁定持久化，创造解除后可重新编辑激活。
- 声音模块仍可独立用于靠近及受伤发声，自定义 .ogg 文件夹保持 config/sablelife/sounds。
- 提示：核心自带心跳红石脉冲，喷火模块紧贴核心时也会受到供电；若希望只由拉杆控制，请与核心隔开至少一格，避免红石信号传入。


## 0.1.17 掉落概率与权重

创造模式下右键部位战利品模块，打开 6 行配置界面。最上方 27 格仍是物品模板，物品和数量的普通左键／右键操作保持不变。鼠标悬停模板会显示当前概率或权重；**Shift 左键 +5，Shift 右键 -5**，范围 0–100。下方“模式”按钮切换两种规则：“抽取次数”按钮仅用于加权模式，左键 +1、右键 -1，Shift 调整 5 次，范围 1–27。

- **每格独立概率（默认）**：每个已放入物品的格子单独判定。0% 不掉，100% 必掉。旧模板和旧存档默认都是 100%，维持原有掉落。重复格独立判定，掉落整叠模板物品。
- **按权重抽取**：每次从权重大于 0 的已填格子中按比例选 1 格，产出该格设置的整叠物品。例如权重 20 与 80，单次抽中比例约 1:4；抽取次数大于 1 时可以重复选中同一格。所有权重为 0 则不掉物品。抽取不影响经验值。

模板、两组数值、模式与抽取次数会一起保存。断肢时复制奖励规则快照，因此断肢后移除模块或重载存档不会改变这次结算。激活后界面只读，生存模式无法打开；旧战利品表模式不受模板概率影响。

## 0.1.18 生命核心设置界面

创造模式下**空手普通右键生命核心**，会显示当前状态并打开设置界面；空手潜行右键仍用于激活。界面中可调整最大生命值（1–1,000,000）、死亡清理延迟（0–12,000 游戏刻）、整体死亡经验（0–10,000）及“允许挖掘身体”总开关。左键加、右键减，Shift 大步调整。用箱子图标进入整体死亡奖励模板：上方 27 格可像部位掉落模块一样复制物品和设置数量，物品不从背包消耗；编辑模板后取代命令指定的整体战利品表。

生命核心激活后，生命值、延迟、经验和奖励模板锁定；**创造模式仍可在核心界面切换允许挖掘开关**，立即影响主体及其登记残骸。生存模式普通右键仍只查看状态，无法打开配置。整体生命归零时，奖励模板复制进死亡任务，后续核心被移除或存档重载也按快照结算一次。创造解除会把核心及配置恢复默认，包含清空整体奖励模板。

## 0.1.19–0.1.20 信息显示与新攻击模块

独立开发实例已安装 Jade：准星对准方块和生物可查看名称；用户实测 Jade 生物血量未显示，本轮暂不处理。创造模式空手普通右键核心，在设置界面点击末影之眼图标可开启/关闭 Boss 血条；血条会显示给存活核心附近 64 格的玩家，并随生命值更新。生命体取名时，先用铁砧给命名牌改名，再把命名牌拿在光标上点击核心界面的命名牌图标；原命名牌不消耗。空光标右键该图标恢复默认名。创造模式可在激活后继续修改名称、Boss 血条和允许挖掘开关。

箭矢发射模块（`/give @s sablelife:arrow_module`）在连接存活核心并通电时按设定间隔发射箭矢。创造模式未激活时右键可用箱子式配置界面设置伤害 1–1000、间隔 2–200 tick、速度 0.5–5、每轮 1–9 支箭、8 格处散射半径 0–8 和六向朝向；射出的箭不可拾取。断电、死亡或断肢停止发射，已射出的箭继续飞行。

范围脉冲模块（`/give @s sablelife:pulse_module`）在通电时周期释放球形冲击。默认半径 5、伤害 6、击退 1、间隔 40 tick；创造模式未激活时右键可用箱子式配置界面设置半径 1–16、伤害 1–1000、击退 0–100、间隔 5–200 tick，并从 `config/sablelife/sounds` 的自定义 OGG 和内置音效中选择脉冲音效。界面可预览球形范围。墙体阻挡伤害，创造及旁观玩家不会受伤。断电、死亡或断肢停止后续脉冲；重新通电立即触发一次。

## 0.1.21 目标感应模块

创造物品栏取出“目标感应模块”，或 `/give @s sablelife:target_sensor`。未激活时右键可用箱子式配置界面设置感应半径 1–32 格、目标类型（非创造玩家／其他生物／全部）、视线要求、环形/前方侦测、环形内半径、前方末端半径及朝向。环形默认内半径 0，水平侦测且高度为中心上下 2.5 格；前方侦测是随 Sable 姿态转动的渐宽锥形。界面可预览范围。模块与存活核心连接后自动扫描；发现目标时向六面输出 15 级红石，同一身体内斜角相邻攻击模块也能接收感应供电。目标离开后最多约 5 tick 熄灭，核心死亡或断肢则立即熄灭。感应模块本身不造成伤害，也不会驱动结构移动。


## Heart 无线时序控制

创造模式右键核心，点击中继器图标进入独立控制面板。16 个频道可配置 Create 双物品频率、延迟、通断时长、强度与单次／循环，激活后自主发射以驱动接收器连接的关节。操作与实机检查见 docs/HEART_WIRELESS_DESIGN.md 和 docs/PLAYTEST_0.1.23.md。





## 0.1.28 核心内置感应与声音

Heart 内的 Target sensor 和 Sound 模块现在实际运行。感应结果在标记方块处输出六向 15 级红石，同体攻击支持对角邻接；环形内外半径、前方范围、目标类别与墙体检查沿用现有参数。Preview 显示真实形状；面板区分正在侦测／已发现目标。

声音读取原有音频库与 config/sablelife/sounds 自定义 .ogg，分别响应靠近和受伤，独立模块冷却，靠近范围内停留不循环播放。两个模块均支持原有四种触发条件、肢体移动及断开停机；同点实体模块被虚拟模块接管，避免重复行为。

本轮 104 项 GameTest 全部通过。客户端测试步骤见 docs/PLAYTEST_0.1.28.md。

## 0.1.29 全向球形感应与更多声音事件

按最新要求，旧 Ring / 环形模式改为全向球形，半径按三维距离计算，覆盖上下和对角；前方模式保留。旧形状标识不改以兼容存档。视线检查跳过本生命体的身体方块，保留外部地形及其他身体的遮挡；预览改为内外球面。

Sound 新增 Ambient 随机间隔环境音，及脚底 Ground contact 接触音。设置接触模式后，用现有标记绑定脚底方块，沿其局部下方向探测外部地形；首次接触播放，持续贴地不重复，抬脚再落地后按独立冷却播放。音效仍来自原有音频库和 .ogg 文件夹。旧声音模块默认环境音与接触音关闭。

客户端测试说明见 docs/PLAYTEST_0.1.29.md。
