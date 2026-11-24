# 功能累计编号

2026-11-07 用户指定：后续编号按新增功能点累计，不按打包次数递增。

## 统计口径

- 基线为 0.1.320，该版本已有功能不重复计算。
- 按独立功能点归并：包括新增核心、模块、控制方式、管理与保护能力；不等于独立模块数量。
- 同一功能后续的修复、改名、配色、排版及收费数值微调不重复算新增功能。新增独立控制或管理员配置能力可单列。
- 以下清单以本地 0.1.327 累计清单、0.1.328 实现及后续已完成项为依据，每个功能点只记录一次。
- **0.1.404** 预发布时累计 85 项。**0.1.425** 最初按累计 105 项编号（320 + 105），目前交付的同版本本地测试包已包含第 106–113 项。
- 后续每新增一个独立功能点就在此登记。纯修复不计入新增功能，打包时不得覆盖已发布的同名文件。
- LittleTiles 渲染诊断附加包 1.0.0 是临时、独立的客户端排查工具，不纳入本体新增功能计数，也不改变 0.1.425 的版本或渲染逻辑。 / The standalone LittleTiles diagnostic addon is not counted as a new main-mod feature.
- LittleTiles 网格恢复兼容测试包 `1.0.0-test1` 属于独立修复候选，不新增本体功能编号。 / The standalone mesh recovery candidate is a compatibility fix and does not increment the main feature count.
- LittleTiles 网格恢复纳入本体时仍作为可选客户端兼容修复，不新增功能编号；保留配置开关、版本与接口检查及外置包优先处理。 / Integrating the optional client recovery adapter is a compatibility fix, with configuration and dependency guards, not a new feature count.
- 箭矢碰撞查询的世界／结构坐标统一属于修复，不新增功能编号或改变存档格式。 / Normalizing arrow collision query coordinates is a fix and does not change the feature count or save format.

## 已登记的功能点

推进与运动阻尼登记为第 117、118 项，推进世界方向为第 119 项，观察工具的生存合成为第 120 项。0.1.452 包含第 128、129 项。0.1.456 合入第 130–133 项。0.1.457 登记展开护盾为第 134 项。 / Version 0.1.452 adds entries 128 and 129. Version 0.1.456 includes entries 130–133. Deployable Shield is entry 134 in 0.1.457.

1. 核心玩家名字白名单，支持离线名字、忽略大小写及 All 目标。
2. 每核心目标优先级排序。
3. Owner 绑定，连接跟随、朝向、导航、感应、主人保护和击杀署名。
4. 服务器共享阵营、单阵营归属及同阵营友伤控制。
5. 普通生命核心开放生存编辑的管理员总开关。
6. 独立生存生命核心及其生存设置和合成配方。
7. 能源补充界面、材料目录、价值预览与确认入账。
8. 多种材料及材料块的服务器能源兑换目录。
9. 按已付生命保留余额、激活时仅补生命差额计费。
10. 按实际恢复生命量收费。
11. 余额不足时使用剩余生命激活，以及生命清空后自动解除。
12. 核心物品的一次性能源余额凭证，防止复制重复兑现。
13. 蓝图复制时重置能源与已付生命，避免复制资产。
14. 模块实时能源预估与计算公式提示。
15. 管理员按模块配置能源费率。
16. 攻击与强控制模块接入服务端能源结算。
17. 生存核心按减伤程度和开启时间收费的部位免伤。
18. Move Set 忽略目标／生命条件参与抽选。
19. 红石核心支持 Move Set。
20. 红石核心支持安全直线移动。
21. 可配置尺寸、偏移和标记部位的实体受击框。
22. Hybrid 结构与实体混合伤害入口、共享生命及可识别重复命中处理。
23. 管理员配置同时启用的实体受击框数量。
24. 生物受击反击时选择最近的有效受击框。
25. 实体框主动吸引敌对及带反击 AI 生物的可选行为。
26. 解除工具可经适用实体受击框解除生命化。
27. 可合成、仅解除生存核心的生存解除剪刀。
28. 方块右键交互模块，支持一次与重复发动。
29. 方块交互的世界／标记结构模式，以及范围和位置控制。
30. 随部件移动、支持染色的连续激光表现。
31. 激光垂直扫射角，可与水平扫射组合。
32. 自定义弹射物始终面向镜头。
33. 喷火扇形、开角及末端厚度。
34. 定向牵引范围。
35. 稳定吸入运动方式。
36. 屏幕震动频率调节。
37. 标记和实体框的连续轮廓预览。
38. 无线红石标记面的斜纹预览。
39. 自身死亡信号及可脱离本体持续的外部脉冲。
40. 玩家挖掘禁止／全时段／仅死亡后三种模式。
41. 玩家挖掘掉落开关。
42. 普通断肢自动清理及延时设置。
43. 结构被外部永久删除后的所有权、加载与队列清理。
44. 管理员世界残留扫描与清理命令。
45. 蓝图生成模块及核心检查。
46. 蓝图固定世界点／跟随标记生成模式。
47. 蓝图生成基准、起始距离、XYZ 偏移及旋转控制。
48. 蓝图模板选择跨解除生命化保留。
49. 蓝图实际模型投影预览。
50. 服务器可配置的蓝图投影规模上限。
51. 自然生成管理工具及逐模板规则。
52. 自然生成可选择是否激活核心，支持无核心普通结构。
53. 群系双面板选择、搜索及主世界群系集合（海洋类除外，海洋可单独添加）。
54. 自然生成的地表定位及离地间距。
55. 露天、边界障碍检查和可选清理重叠树木条件。
56. 自然生成阵营选择及隐藏的 Wilds 阵营。
57. 自然生成规则复制。
58. 一条规则跟踪多个对象、数量上限及逐对象寿命／无人清理记录。
59. 自然生成列表显示每个模板的当前对象数量。
60. 按模板清除当前所有对象，支持持久清理队列。
61. 生成后放生，脱离规则计数与清理。
62. 公开／私人阵营。
63. 基于玩家 UUID 的邀请、接受及成员管理，玩家可加入多个阵营。
64. 多个双向结盟、非传递关系，以及双方同意的盟友误伤。

65. 自动自然生成支持创造玩家，仍排除旁观者。

66. 自然生成按完整蓝图边界清除障碍及凸起地形，并保护不可破坏与容器等方块。
67. 自然生成蓝图方块、部件数、数据、占地尺寸、落点扫描和清障容量独立服务器配置。

68. 每蓝图刷新密集程度面板：成功冷却、失败重试、每轮搜点次数及自动刷新距离，保存到世界规则并在运行时生效。

69. 每条自然生成蓝图自定义出生全服公告：成功生成后广播，留空关闭，支持试生成和放生，世界重启后保留。

70. Visual Effects 径向模糊样式：以实际发射点世界投影为中心向外拉伸画面，支持强度、持续时间与中心清晰半径，保留旧圆环默认行为。

71. 自然生成出生公告可选附加实际生成坐标；每规则保存，空白公告自动禁用并取消选择。

72. 箭矢／枪武器／迫击炮自定义弹射物的局部 X/Y/Z 模型旋转，跟随飞行姿态并同步、持久保存。

73. 标记点血条显示模块：部件跟随、面向观看者、柔边红色血量，宽度／高度／初始距离可调，支持预览及生命状态清理。

74. 血条样式可切换长方形／半圆端头，并使用物品栏灰紫色细边框。

75. 血条可选始终显示／受伤后显示，并按最后一次实际掉血后的可调时间隐藏。

76. 每个血条模块自定义可见距离，默认 64 格、范围 1–256 格，按血条中心距离即时隐藏／恢复。

77. 自然生成工作台保护：默认水平半径 64 格，按整张蓝图占地边缘检查并覆盖所有高度；分批建立持久化区块索引，工作台变更直接维护缓存，未知区域等待，服务器可配置半径或关闭。

78. 激光水平／垂直扫射分别设置度/秒，非零角度时显示对应速度；默认自动兼容旧时序。连续光束逐帧平滑过渡。

79. 激光自选额外粒子开放沿光束间距，保持有界采样与共享显示预算。

80. 服务器按玩家设置可创建阵营数量，默认 1、0 不限制、权限等级 2+ 豁免；独立于加入多个阵营，保留原有阵营，失败原因明确提示。

81. 自然生成出生公告支持 & 颜色、粗体、斜体及其他标准格式与十六进制颜色；保存原始代码、广播解析样式，提示列出格式及 256 字符限制。

82. 标记点血条可在上方显示所属生命体名字，保留颜色和格式；名字独立大小与间距、显示开关，沿用血条触发、显示时机、距离与清理。

累计计算：320 + 82 = 402；下一新增功能：402 + 1 = 403。Loot 单次复制修复、手册标签简化和界面对齐不重复计为新增功能。

2026-11-08 修订包 0.1.402-fix1：移动／对齐预览、说明与默认值调整；累计功能尾号仍为 402，下一新增功能 403。

2026-11-08 当前已交付 0.1.402-fix4／协议 45／手册 194；之后源码恢复生存核心弹射物目标瞄准，手册 195，未打包。均为既有功能修正，仍为 82 项；下一独立功能 403。完整当前清单：[320 至当前](UPDATE_0.1.320_TO_CURRENT.md)。

83. 视觉特效新增聚能、机械刻度环、护盾碎裂、喷口焰流、平面能量裂纹、回声光环及符号；支持颜色、大小、时长、动画速度和跟随部件／固定位置。属于一个新增功能，源码版本进入 0.1.403；协议 46，手册 197。

84. 自定义模块扩展，支持 Java 附属注册，以及 JSON Datapack 定义与 `/reload`。包含标记点铃声与警报示例。

85. 生物类型白名单。可搜索原版和模组生物，并从自动索敌中排除选中的类型，支持全部目标。

第 84、85 项随 0.1.404 交付。药水效果免疫与接触伤害去重作为修复列入本版更新说明。

86. 断肢破坏模块，可跟随触发器或监听部位累计伤害，破坏单点或同一 Sable 子结构上的两点范围。招式沿用攻击标记失效判定。当前源码补充破坏音效，在方块实际破坏时播放一次。

87. 累积伤害模块可切换全生命体或单个 Sable 子结构监听，旧设置沿用全身监听。第 86、87 项登记在当前源码。
88. 枪武器、箭矢发射、迫击炮和悬浮追踪弹可选曳光拖尾，支持颜色、长度与宽度。旧设置继续使用粒子拖尾。
89. 实体受击框可开启反击白名单生物。造成实际伤害的个体在 15 秒内恢复目标资格，沿用原有锁定和索敌规则。
90. 生命核心和生存生命核心可自定义结构受击粒子，调整类型、总数和散布。旧核心默认保留暗红心。
91. 持续伤害区域可在生成后延迟爆破一次，沿用圆形或矩形范围。支持地形破坏、爆破粒子和音效。重复命中刷新区域时保留原倒计时和引爆状态。
92. 核心遥控器可换绑，打开同维度已加载核心的面板。普通生命核心只允许创造模式绑定和远程打开，服务器按当前操作者验权。
93. 生物白名单支持被动生物和中立生物预设，服务器可通过实体类型标签扩充。
94. 核心遥控器支持激活已绑定的普通或生存生命核心，沿用核心的激活规则和能量结算。
95. 模块列表支持多选和批量分组。右键分组标题可在原列表勾选要加入的模块、取消勾选要移出的成员，确认后一次保存。
96. 直线移动新增标记世界位置模式，按核心位置对齐终点，可由信号或招式触发。属性栏只显示当前移动和目标方式适用的设置。
97. 指挥棒可绑定一个核心，临时指定生物或世界坐标，供自动朝向、目标对准及直线移动使用。继续沿用模块自身的触发方式。最远可瞄准 128 格内已追踪的生物或已加载的地形，准星指向的已追踪生物可在原版实体渲染距离外显示光灵描边，地形绿框会被方块遮挡。左键可点中其他已激活生命体的结构方块。手动指令跳过模块索敌距离，目标对准的索敌上限提高到 256 格，与自动朝向和直线移动一致。操作手册说明临时目标和模块执行顺序。工作台配方沿右上至左下依次放入红石粉、紫水晶碎片和木棍。
98. 迫击炮的目标位置模式支持独立的视线遮挡开关，发射和预览使用同一筛选，旧模块默认关闭。
99. 统一内置攻击模块的索敌视线选项。箭矢、枪武器、斩击、激光、落地雷、地震波、球形爆破、范围冻结、冲击波、抓取／束缚和召唤武装新增开关。新建模块默认开启，已有设置和旧存档的索敌行为保留。开启时，激光目标移入遮挡后，已发射的光束沿最后的方向持续到本轮结束。
100. 招式新增「沿用选招目标」，直接安排的瞄准型攻击优先使用选招目标，多目标仍可补足其余名额。新招式默认开启，旧招式缺少设置时沿用关闭。手册修订 211。
101. 指挥棒可单独设置目标对准、自动朝向和直线移动是否接收指令。新旧指挥棒缺少设置时默认全开，时长滚轮步长为 5 秒。手册修订 212。
102. 指挥棒新增独立的「武器跟随左键目标」，默认关闭。支持的瞄准型攻击依序使用指挥棒目标、选招目标或自身索敌结果，仍遵循原有触发和武器目标限制。多目标武器照常补选。手册修订 213。
103. 场地边界新增独立的玩家死亡倒计时。新模块默认 5 秒，已有模块沿用原倒计时时长。手册修订 214。
104. 消息显示模块可通过触发信号顺序发送聊天消息、屏幕标题或操作栏消息。聊天消息带有生命体名称前缀。可设置消息间隔、接收半径和文本格式，标题支持副标题。普通生命核心可开启全服广播，需在创造模式下具备管理员权限才能编辑。归入功能分类，每条消息可设置发完后等待的时间。手册修订 218。
105. 粒子选择器新增五种动态符号（!、?、...、×、⚡）、金属碎屑和故障电火花。每种均支持 RGB 染色、大小和寿命调整。手册修订 221。
106. 生存生命核心的补能槽可由漏斗和兼容的物品管道供料。可开启自动补能，燃料随核心移动，并可在同一世界挖下后重新放置取回。手册修订 222。
107. 蓝图生成可接替首领进入第二阶段，或生成跟随场地约束的召唤物。场地追踪战斗和重置，手册修订 223。
108. 场地边界可单独设置刷新成功和被击败的全服公告。手册修订 223。
109. 场地边界支持跨阶段战斗限时，开启后默认 5 分钟，超时恢复第一阶段。手册修订 224。
110. 场地战斗计时支持可自定义的开战和剩余时间标题，预填中英文示例并支持格式码。手册修订 225。
111. 可选兼容 Let it Walk! 0.1.5，将仍连接的机械腿所受伤害传给车身的生命核心。同一发投射物或同一次爆炸按核心去重，涵盖多条腿和重叠的 Hybrid 受击框。
112. 部位伤害修正的每条阶段规则可按信号分别设置通用、近战和弹射物直击修正，适用于结构本体、实体受击框和 Hybrid。手册修订 227。
113. 无线红石可添加最多八个共用设置的输出面。第 1 面用底部的标记和预览按钮，其余各面在属性页独立标记、预览和移除，预览显示编号。手册修订 232。
114. 信号转向可在每次触发或招式动作开始时随机选取一个水平转向角度，范围可调，可搭配直线移动实现无目标巡游。手册修订 233。
115. 信号转向可选择与陀螺仪同时运行，支持方向信号和随机水平转向，默认关闭。手册修订 234。
116. 世界／服务端可允许未适配的领地接口使用核心遥控器与远程检查工具，默认关闭。已适配的实际权限及其他核心访问规则继续有效，拒绝时显示模组 ID 和配置项。手册修订 237。
117. 独立推进模块：单点爆发冲量或持续推力，方向角、持续时间、间隔和可选推进方向限速；原生施力并保留惯性。 / Propulsion: marked-point impulse or thrust with optional directional speed control.
118. 独立运动阻尼模块：世界或标记面六方向独立强度和速度阈值，重心制动，信号或定时生效。 / Motion Damping: six directional braking settings with world/local frames and timed or signal activation.
119. 推进可选择标记面或固定世界方向，世界模式提供北、南、东、西、上、下及角度偏移。旧设置默认沿用标记面，普通预览和观察工具保持一致。 / Propulsion supports marked-face or fixed world directions, with six world axes and angle offsets; existing settings retain marked-face behavior.
120. 观察工具可用1个望远镜和1个红石粉无序合成，获得望远镜后解锁配方书，并补充中英文手册获取与绑定步骤。 / The Inspection Tool is craftable with one spyglass and one redstone dust, shapeless, with recipe-book unlocking and bilingual crafting/binding instructions.

每个核心的默认模块上限提高至 256，服务器配置范围为 16–1024。大型核心按需同步模块详情，数据复制器压缩较大的配置。手册修订 230。

场地战斗计时提醒增加屏幕标题与聊天栏显示选项，旧设置默认沿用屏幕标题。归入第 110 项，手册修订 228。

0.1.404 中的视觉特效选择器补充页码显示和左右翻页，归入第 83 项。

0.1.404 中的服务器蓝图选择页改为三列，并可切换生成后是否激活核心。新模块默认开启，已有模块保留原值。归入第 45 项。手册修订 215。

0.1.404 中的新指挥棒默认开启「武器跟随左键目标」，旧指挥棒沿用原有设置。归入第 102 项。手册修订 216。

指挥棒在没有已保存开关时默认开启「武器跟随左键目标」。已保存的开关按原值读取。归入第 102 项。

消息显示可由自身死亡事件触发，剩余消息可在本体清理后继续发送。归入第 104 项，手册修订 219。

服务器现在可开启生存生命核心的消息显示，默认关闭。开启后接收者须在同维度且在设定范围内。归入第 104 项，手册修订 220。

Java 附属和 Datapack 模块支持按核心类型停用，已有全局禁用配置继续生效。归入第 84 项。

- 0.1.425 — Signal Logic gate picker and analog comparisons. Wireless Channel gains up to 64 individually marked output faces.
- 0.1.425 local prototype — The Inspection Tool binds to a core and displays selected module ranges, directions and marks with world labels. It uses the spyglass texture.
- 0.1.425 — Named wireless channel output faces and numbered in-world previews.
- 0.1.425 — Wireless Redstone module output faces expand from 8 to 64 per module. Existing marks retain their numbering. Manual revision 235.
- 0.1.425 — Auto facing can bind to a named Target sensor on the same core and follow its detected target. Bindings use module IDs across renaming, reordering, saves, full configuration copies and blueprints. Manual revision 236.

无线频道和无线红石输出面的新增上限默认16，可通过 maximumRedstoneOutputFaces 设为1–64。旧64面保留并继续运行，归入第113项。手册修订239。

运动阻尼六方向改为紧凑单页，归入第118项，不增加功能编号。 / Compact single-page damping editor remains part of feature 118.

观察工具换用用户自绘的16×16贴图及普通手持工具模型，不增加功能编号。 / The Inspection Tool uses the authored 16×16 texture and handheld item model, without adding a feature count.

121. 管理员蓝图生成指令：名字补全、默认地表定位、可选固定坐标与 true/false 激活；独立队列和失败清理。 / Administrator blueprint spawning with name completion, surface or exact placement, activation toggle and isolated jobs.
122. `/sablelife help` 与直接输入 `/sablelife` 显示中英文指令语法与简短用途。 / Bilingual command help lists syntax and concise purposes.

123. 新增独立「信号接触传感器」：可勾选地形、其他 Sable 结构、玩家、生物和弹射物，设置起始距离、局部 XYZ 尺寸和水平／垂直角度。支持 Move Set 激活窗口、持续输出和指定时长的首次接触脉冲，排除自身连接部件和弹体；中英文手册修订243。 / Added the independent Signal Contact Sensor: check terrain, other Sable structures, players, mobs and projectiles; configure Start distance, local XYZ size and angles. Supports Move Set windows, Hold and timed contact pulses, excluding own connected parts and projectiles. Bilingual manual revision 243.

0.1.444 修正第111项的 Let it Walk! 0.1.5 死亡清理：主体清理附属脚段及其中的独立核心；独立脚核心死亡不会反向清理主体。未扩大标记或普通伤害归属范围，不新增功能编号。 / Fixed Walker attachment death cleanup without changing marking scopes or independent damage ownership; remains feature 111.

0.1.444 手册修订245：移除游戏内 Let it Walk 关系说明，清理与伤害代码不变，不新增功能编号。 / Manual-only removal of Let it Walk relationship notes; no behavior or feature-count change.

124. 0.1.445：核心碰撞破坏支持草花等无碰撞体装饰物；保护预设补充常见自然植物。近战新增独立“破坏方块”开关，沿用保护清单与掉落偏好，旧设置默认关闭。 / Collision breaking includes outline-only decorations, the optional protection preset covers common plants, and Melee gains an independent terrain-breaking toggle with compatible Off defaults. Manual revision 246.

0.1.446：第124项改为每个近战模块独立的保护方块白名单；445已启用的模块一次性复制旧核心清单，编辑不再联动。手册247，不新增功能编号。 / Feature 124 now uses per-Melee protected lists with one-time 445 migration and independent edits. Manual 247; no new feature count.

125. 0.1.447：无线红石输出缓冲（0秒默认关闭），最终输出断电后保持最后强度指定时长；三个输出目的地一致，生命周期清理不延迟。 / Wireless redstone output buffer holds the last strength after output ends, defaults Off, and applies to all three destinations with immediate lifecycle cleanup. Manual248.

0.1.448：核心遥控器未适配领地开关的新配置默认值改为true；旧值保留，不自动迁移，实际已适配权限逻辑不变。手册249，不新增功能编号。 / New-config default true for unsupported-claim remote access; saved values and supported checks retain their behavior. Manual249; no new feature count.

126. 0.1.449：俄语本地化（原译 Bill-Fasber，PR #5）与按窗口适配、分页的语言选择器；维护者补齐新版文本，三语资源与手册引用校验。手册250。 / Russian localization credited to Bill-Fasber, adaptive paginated language selection and three-language resource/manual validation. Manual250.

0.1.449-fix1：添加目录按信号、攻击、功能稳定分组，接触传感器放在目标传感器旁边；保留个人预设顺序，不改能力位或执行顺序。纯界面修复，不新增功能编号，手册250。 / Client-only catalog grouping fix places Contact Sensor beside Target Sensor, retaining personal preset order and stable capability/execution order; no new feature count. Manual250.

127. 0.1.450：自动朝向的自身局部左右、上下小数瞄准偏移，默认0、滚轮0.1格；新模块碰撞检查默认关闭，旧设置与传感器绑定保留。无线红石缓冲滚轮调整属于配套修复，不另计功能编号。手册253。 / Decimal local left/up Auto Facing aim-origin offsets, default0 with a 0.1-block wheel step. New modules default collision Off while old settings and sensor bindings remain intact. Buffer scrolling is an accompanying fix, not a new feature entry. Manual253.

0.1.450 配套调整：近战破坏设置置于属性末尾，提供独立碎块／掉落开关与背包保护名单样本；扩展现有近战地形功能，不重复计入新增功能数。 / Melee breaking controls move to the end, with independent debris/drop switches and inventory protection samples; these extend existing terrain breaking without a separate feature count.

128. 0.1.452：生存生命核心的模块地形破坏可由服主统一关闭，涵盖近战、激光与爆破。 / Server owners can disable terrain damage from Survival Heart Core melee, lasers, and blasts.

129. 0.1.452：可设置失去视线后的目标保留时间，Target Alignment 和 Move Set 可以共用目标传感器的目标记录。 / Configurable target retention after losing sight, with shared Target Sensor records for Target Alignment and Move Set.

130. 0.1.452-review.4：Photomancy 蓝图携带阵营设置和直接盟友引用。玩家可在目标地图绑定、另建或跳过，确认的映射随目标存档保存。 / Photomancy blueprints carry faction settings and direct ally references, with permission-checked assignment and saved mappings in the destination world.

131. 实体穿透测试版：按标记或全身范围，为已触发的生命体关闭玩家和生物的移动碰撞。手册修订 256。 / Entity Pass-Through lets players and mobs move through a marked substructure or the whole connected body while triggered. Manual revision 256.

132. 近战破盾测试版：玩家成功格挡启用破盾的近战攻击后，盾牌进入原版 5 秒冷却。可选音效从被破盾玩家的位置播放给附近其他玩家。手册修订 258。 / Melee shield disabling applies the vanilla five-second cooldown when a player blocks a hit. An optional positional sound plays for nearby players when the shield is disabled. Manual revision 258.

133. 近战击退控制。额外击退为 0 时可编辑「原版受伤击退」，默认开启。额外击退大于 0 时原版击退同时生效，归零后恢复保存的选择。手册修订 259。 / Melee can disable Vanilla hurt knockback when Extra knockback is zero. It defaults to On. Positive Extra knockback enables both, and returning to zero restores the saved choice. Manual revision 259.

134. 0.1.457：视觉特效新增「展开护盾」。平面蜂巢从标记面中心展开，在触发期间保持，触发结束后向中心收拢。手册修订 261。 / Deployable Shield reveals a flat honeycomb from the center of the marked face, holds while triggered, and closes inward afterward. Manual revision 261.
