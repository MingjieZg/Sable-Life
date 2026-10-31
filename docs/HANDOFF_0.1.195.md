0.1.308 upload: GitHub main / Google Drive; stable 0.1.243 retained. See UPLOAD_0.1.308.md.

# 最新开发版 0.1.308（2026-10-31）

召唤武装预览改用与实际相同的原生ItemDisplayRenderer，补齐原生Y轴180度/模型枢轴变换；目标模式预览采用新生成武装的首tick转向。四个生成/消失媒体属性移至列表末尾。实际客户端10武装及发射器/剑的预览位置、XYZ旋转、大小与显示模式对比通过；召唤原生测试和手册141通过。协议25。详见DEVELOPMENT_0.1.308.md。未上传或改原实例。

交付偏好：用户要求以后不再生成patch；保留JAR、源码快照和验证记录。

# 开发版 0.1.307（2026-10-31）

召唤武装改为单次激活独立持续、重复激活追加组，信号结束保留部署队列。新增生成/消失媒体、默认发射器/星芒/烟花发射声、攻击前摇及真实物品和彩色发射口/方向预览；引用新增斩击、落地雷、地震波、牵引、冻结、冲击波、抓取。每核心最多256件运行/待部署武装。282项相关原生测试、16组GUI、实际客户端和手册140通过。协议25。详见DEVELOPMENT_0.1.307.md。未上传或改原实例。

交付偏好：用户要求以后不再生成patch；保留JAR、源码快照和验证记录。

# 开发版 0.1.306（2026-10-31）

召唤武装同组共享目标池，正常5 tick刷新空间搜索，每件继续逐tick独立转向和攻击。死亡/移除/超距及时失效，超出搜索覆盖立即刷新，稀疏阵型分区查询。206项原生测试、实际客户端八武装追踪/位置/清理和双语手册139通过，截图已检查。协议24。详见DEVELOPMENT_0.1.306.md。未上传或改原实例。

交付偏好：用户要求以后不再生成patch；保留JAR、源码快照和验证记录。

# 开发版 0.1.305（2026-10-31）

召唤武装支持1–32件及悬浮追踪八种阵型、半径/间距、平面/水平旋转、逐个生成；每件独立寿命、瞄准、攻击和声音。默认1件保留原行为。239项原生测试、16组GUI、实际客户端八武装阵型/跟随/清理及双语手册138验证通过，截图已检查。协议24。详见DEVELOPMENT_0.1.305.md。未上传或改原实例。

交付偏好：用户要求以后不再生成patch；保留JAR、源码快照和验证记录。

# 开发版 0.1.304（2026-10-30）

新增召唤武装：物品显示、XYZ/缩放、跟随/固定世界、目标追踪与角度、独立发射口及存在时长，引用Gun/Arrow/Flame/Laser，独立计时并保留原攻击命中归属。230项相关原生测试、16组GUI和双语手册137通过；实际客户端显示、位置模式及死亡清理通过，截图已检查。协议24。详见DEVELOPMENT_0.1.304.md。未上传或改原实例。

# 开发版 0.1.303（2026-10-30）

Mortar／Ground strike新增默认爆炸/自选落地粒子、独立Color/Life/Size、跟随/独立铺开半径和总数量，伤害及预警圈不变。50项原生测试、16组GUI和双语手册136验证通过，截图已检查。含限时伤害滚轮±5/±4秒。协议24，详见DEVELOPMENT_0.1.303.md。未上传或改原实例。

# 开发版 0.1.302（2026-10-30）

累计伤害触发新增累计/限时模式面板，保留反转、内外频道、强度及脉冲；限时结束后比较实际扣血，支持单次/循环，提前关闭取消。134项相关原生测试、16组GUI及双语手册135验证通过，截图已检查。协议24。详见DEVELOPMENT_0.1.302.md。未上传或改原实例。

# 开发版 0.1.301（2026-10-30）

Ground strike下落物品新增固定XYZ旋转和独立0.1–32格显示大小，支持剑尖朝下的巨剑打击；轨迹/落点半径不变。46项原生测试、三把原生客户端下落剑、16组界面检查通过，截图已检查。手册134，协议24，需两端同时更新。详见DEVELOPMENT_0.1.301.md。未上传或改原实例。

# 开发版 0.1.300（2026-10-29）

移除战斗事件的断肢规则；击杀/格挡新增1–9999累计次数，达到后清零循环，并修正复活玩家再次击杀累计。命中计数新增外部Create红蓝频率，支持外部独立和内外同步、预设保存。182项原生测试、8组规则GUI及16组比例密度界面检查通过。手册133，协议23。详见DEVELOPMENT_0.1.300.md。未上传或改原实例。

# 开发版 0.1.299（2026-10-29）

圆点/光环/月牙/柔雾/星芒改完整单面片；保留固定星芒并新增随机角度星芒。保护列表改图标网格，悬停名称和完整ID、右键移除。17项原生测试、11种粒子22实例、GPU三视角32重叠像素检查和12组GUI验证通过，最终截图已检查。手册132，协议23，需两端同版。详见DEVELOPMENT_0.1.299.md。未上传或改原实例。

# 开发版 0.1.298（2026-10-29）

保护列表新增20种地表方块预设合并和非消耗物品栏样本登记，确认后添加方块ID；修复整身/部件战利品样本右键按下与松开重复计数。49项原生测试、4组战利品和12组地形/保护/样本GUI验证通过，截图检查通过。手册131，协议23。详见DEVELOPMENT_0.1.298.md。未上传或改原实例。

# 开发版 0.1.297（2026-10-29）

能量光点改成可RGB调色的圆点，所有自带粒子显示名前缀统一Sable life并保留可调色后缀。实际客户端十种粒子/20个实例验证和截图检查通过。详见DEVELOPMENT_0.1.297.md。协议23、手册130；未上传或改原实例。

# 开发版 0.1.296（2026-10-29）

新增粒子统一低透明度截断通道；雷电平滑换形与淡入淡出、较清楚主线和较窄光晕，星芒固定朝向并缩小柔光。实际客户端10种粒子/20个实例及雷电渲染连续性、生命周期检查通过，截图已检查。详见DEVELOPMENT_0.1.296.md。协议23，手册130；未上传或改原实例。

# 开发版 0.1.295（2026-10-29）

修复连接器残留引用已移除部件时，战斗事件全部部件/Mark指定部件漏发；连接集合排除已移除结构，保留去重。18项信号事件原生测试通过，含真实组装结构移除和断开连接回归（连接器actor为夹具）。详见DEVELOPMENT_0.1.295.md。协议23，手册130；未上传或改原实例。

# 开发版 0.1.294（2026-10-29）

范围冻结 On target 新增 Multiple target 勾选，默认单目标，开启最多64个目标范围，共用时钟并去重捕获。战斗事件类型左键下一项、右键上一项循环。81项相关原生测试及14组冻结界面检查通过；右键改动编译通过。手册130，协议23。详见DEVELOPMENT_0.1.294.md。未上传或改原实例，GitHub284。

# 开发版 0.1.293（2026-10-29）

范围冻结新增玩家/生物/两者筛选，On target按同一筛选取最近对象，Sable生命体排除。生物仅固定捕获位置，原AI、索敌/攻击/转头/移动尝试照常，不改NoAI/重力标志。独立冻结租约与抓取兼容，释放抓取后保留剩余冻结。208项相关原生测试、猪/僵尸实际客户端范围冻结/到期恢复、14组GUI通过，手册129。详见DEVELOPMENT_0.1.293.md。协议23同版，稳定243、移动273/272；未上传或改原实例，GitHub284。

# 开发版 0.1.292（2026-10-29）

范围脉冲改名冲击波；修复冻结/抓取粒子入口旧白名单，共用粒子支持判断。补齐抓取对象、信号逻辑AND/OR及冻结铺设choice_文字。战斗事件/信号条件改同行紧凑布局，事件七项同页且隐藏无用右侧分页。89项相关原生测试、38组界面组合及所有相关选项中英文解析验证通过，手册128。详见DEVELOPMENT_0.1.292.md。协议23同版；稳定243、移动273/272。未上传或改原实例，GitHub仍284。

# 开发版 0.1.291（2026-10-29）

抓取对象新增玩家/普通生物/两者，默认玩家；Sable生命体/装饰实体排除。按用户最终要求普通生物AI和travel照常执行，只约束位置/位移，不改NoAI/重力属性。201项相关原生测试、猪/僵尸真实客户端同步与释放、14组界面验证通过；手册127。详见DEVELOPMENT_0.1.291.md。协议23同版，稳定243、移动273/272；未上传或改原实例，GitHub仍284。特殊附属生物和多人压力未全测。

# 开发版 0.1.290（2026-10-29）

冻结加入圆柱/旋转长方形、高度、长宽/角度/铺设、Mark方向/起始距离，判定和独立可见预览共用ControlVolume。新增grab抓取/束缚：最近一名玩家，身体中心跟随Mark部件平移旋转，保留视角/攻击/盾牌，安全路径碰墙释放，独立租约与其他冻结兼容。195项相关原生测试、真实客户端动态跟随/恢复及空粒子范围预览、12组GUI通过。手册126（56章节43模块）、协议23同版。详见DEVELOPMENT_0.1.290.md。稳定243、移动273/272；未上传或改原实例，GitHub仍284。完整Boss多人抓取未实测。

# 开发版 0.1.289（2026-10-29）

范围冻结freeze：Mark point/On target固定预警中心，默认雪花冰晶可自选，预警结束才捕获范围玩家。锁位置/跳跃/击退但保留视角/攻击/盾牌；独立租约与到期/停用/卸载/死亡/换维度清理，悬空防飞行误判。188项相关原生测试、真实客户端输入/网络/盾牌/攻击/恢复及4组GUI验证通过。手册125，协议23同版。详见DEVELOPMENT_0.1.289.md。稳定243、移动273/272；未上传或改原实例，GitHub仍284。抓取/咬住跟随Mark模块仅讨论，未实现。

# 开发版 0.1.288（2026-10-29）

粒子新增动态分叉雷电、细长火花、扩散/收缩光环、月牙光片、柔和雾团、星芒，普通电弧也每2刻变形。现有列表搜索sable，全部共用Color/Life/Size；专用柔和粒子着色器，无全屏或伤害改动。17项粒子原生测试、十种粒子真实世界网络/参数/动画/到期验证、GUI比例2/3通过，手册124。详见DEVELOPMENT_0.1.288.md。稳定243、移动273/272；协议22同版使用。未上传或改原实例，GitHub仍284。

# 开发版 0.1.287（2026-10-29）

自带可调色电弧/烟雾/能量光点，现有粒子列表搜索sable，复用Color/Life/Size与保存/预设。三种纯视觉provider，无实体或攻击行为。手册123、协议22，客户端服务端同版；17项粒子原生测试及隔离世界/GUI验证，详见DEVELOPMENT_0.1.287.md。稳定243、移动273/272；未上传或改原实例，GitHub仍284。

# 开发版 0.1.286（2026-10-29）

Core settings新增第6页碰撞破坏：默认关，零延迟、脚底保护、128项方块ID/#标签保护列表、原版掉落及纯视觉方向碎块。实际旋转方块形状检测，不擦除空隙；Linear Travel先破坏后跨阶，清理旧爬升状态。配置复制/蓝图保留，接触计时不保存。252项相关原生测试、8组GUI及真实隔离世界24粒子方向/到期/无实体验证通过，手册122。协议22，客户端/服务器同版。稳定243、移动273/272不变；未改原实例或上传，GitHub仍284。详见DEVELOPMENT_0.1.286.md / exports/development-0.1.286/manifest.json。

# 开发版 0.1.285（2026-10-29）

新增signal_logic与combat_event信号模块：16行AND/OR/NOT条件+统一逐刻采样，64条击杀/部件脱离摧毁/被格挡独立规则，来源筛选、脉冲和冷却。原生盾牌和既有Epic Fight近战/Slash防御接入，取消死亡不计，运行状态不保存。规则菜单分页及权限验证，蓝图/整份配置复制转换来源UUID，预设解除绑定。263项相关原生测试+9项Epic Fight、24组GUI及手册121通过。详见DEVELOPMENT_0.1.285.md / exports/development-0.1.285/manifest.json。完整轴承断肢实机场景未测；稳定243和移动273/272不变，未上传或修改原实例。上次GitHub同步284。

# 开发版 0.1.284（2026-10-28）

修复Sable schematic放置后攻击的持续范围伤害引用显示区域已删除：HeartBlueprintMapper把Settings.ImpactFieldId加入原生放置UUID映射，与模板新ID一致。八类攻击、模板前后顺序、重复放置/再导出隔离均验证，实际命中可生成区域。62项相关原生GameTests通过，手册仍120。原schematic可重新放置；已失效世界副本需重选引用或重放。见 DEVELOPMENT_0.1.284.md / exports/development-0.1.284/manifest.json。稳定243、移动273/272不变，未上传或修改原实例。

# 开发版 0.1.283（2026-10-28）

Slash新增“可被格挡”勾选，默认关闭以保留原无法格挡行为。开启后按飞行来袭方向允许正面原版盾牌及Epic Fight三种原生防御/招架，背面照常命中；成功格挡不触发点燃/药水/命中区域或计数，零伤害效果斩击同样受控。在途快照、单目标一次判定保留。78项攻击/持续范围与9项Epic Fight原生测试、16组GUI及手册120通过。见 DEVELOPMENT_0.1.283.md / exports/development-0.1.283/manifest.json。稳定243、移动273/272不变，未上传或修改原实例。

# 开发版 0.1.282（2026-10-28）

surface_field显示持续范围伤害，成为纯命中模板：固定fieldRelease=0，无Mark/Preview/独立Trigger/目标释放/直接Move Set，删除运行时自主释放。旧Mark状态忽略，ID/引用保留。四项滚轮步长1（尺寸格、持续时间秒）。homing加入IMPACT_ATTACKS，真实命中走既有入口，同来源模板同点只刷新、不叠伤/重置伤害时钟。Slash追加slashIgnite勾选与burn秒数（默认关、3秒），独立于区域燃烧。206项相关GameTests、32组GUI、手册119通过。详见 DEVELOPMENT_0.1.282.md / exports/development-0.1.282/manifest.json。稳定243和移动273/272不变，未上传或改原实例。

# 开发版 0.1.281（2026-10-28）

命中区域扩展到Gun/Arrow/Slash/Laser/GroundStrike，共七种；ModuleSurfaceField.IMPACT_ATTACKS为共享白名单，SurfaceFieldImpact适配实际地形面与实体向下地面投射。箭快照/消耗状态随实体保存，爆炸箭事件取消插地前也触发；激光仅发射接触、斩击真实命中、地面落击时生成。不改原无引用攻击行为。202项GameTests、16组UI、手册118通过，详见 DEVELOPMENT_0.1.281.md / exports/development-0.1.281/manifest.json。稳定243和移动273/272保留，未上传或修改原实例。

# 开发版 0.1.280（2026-10-28）

持续区域与残留火焰已合并。攻击模块仅impactArea引用，模板fieldBurn勾选控制burn（离开后燃烧秒数，默认3）；每刻接触刷新先于命中冷却，直接伤害沿用绕过普通无敌帧的ModuleDamage，燃烧伤害不吞区域伤害。移除impactFire字段和独立火焰运行分支。详见 DEVELOPMENT_0.1.280.md，58项相关GameTests、16组GUI、手册117及Jar验证通过。稳定243和移动273/272保留，未上传或修改原实例。

# 开发版 0.1.279（2026-10-28）

Mortar / Flamethrower 命中残留火焰和持续区域为两个独立设置；后者选择 surface_field 模板（UUID引用）。范围支持圆形/旋转长方形，贴合静态地面/墙面，区域独立计时，重叠伤害共享时钟，附近命中刷新，最多64个/核心。总开关、死亡、卸载及来源/模板删除或停用清理；不改地形。详见 DEVELOPMENT_0.1.279.md 和 exports/development-0.1.279/manifest.json。185项相关GameTests、16组GUI、最终13项区域保存回归通过，手册116。稳定243与移动273/272观察保持原状。未上传、未动原实例。

# 开发版快照 0.1.278（2026-10-19）

本次备份并同步0.1.278源码及Jar，包含粒子单点/双点范围扩展、模块搜索/Copy、Slash发动/持续飞行音效、多目标范围释放，以及前序开发变更。最新测试记录见 releases/development-0.1.278/manifest.json。

交付Jar：releases/development-0.1.278/sablelife-0.1.278.jar；SHA256：9d33b6f018aaffed9463793973854cf85d0021ec3a2452af0ffef57d32275fe4。

新增最终验收约束见 FINAL_ACCEPTANCE_DAMAGE_API.md：公共伤害桥接契约、结果/去重/归属、文档、可编译附属示例及验证。此项仍待最终实现/验收，不视为实验API已经稳定。

本次没有提升稳定版：稳定仍0.1.243。0.1.273移动联动继续观察，以0.1.272为对照。本次只备份和同步，不修改运行行为、不修改原游戏实例或用户世界。

完整恢复备份保存于本地backups目录，含清单、校验及最新开发导出；第三方运行依赖和世界文件不纳入GitHub源码仓库。本次未请求Google Drive上传。

# 待最终验收：附属 Mod 统一伤害接口（2026-10-19）

用户要求最终交付明确的公共伤害桥接接口及接入说明，供其他作者开发附属。规范入口、命中资料/坐标、归属、攻击窗口去重、结构化结果与统一伤害结算责任；附属不直接改核心血量。必须提供可编译示例与接口级验证，区分已支持/未支持能力。详见 [最终验收要求](FINAL_ACCEPTANCE_DAMAGE_API.md)。现有 COMPATIBILITY_API.md 中的实验接口不等于最终稳定契约；本次仅记录，不改代码、Jar或稳定状态。

# 0.1.278 单点/双点粒子范围扩展（开发版，2026-10-18）

Particle effects 的 Area（双点）及 Point（单点）新增额外长度、宽度、高度，范围0–128格，默认0。沿用Melee的部件本地forward/right/up尺寸扩展：长度沿选定方向向前增加，中心前移额外长度的一半；宽/高对称扩展，不改变中心。坐标随标记部件旋转。双点以原标记盒为基础，单点以原表面外0.55格的发射点为基础，可扩为线、面或盒状。默认值保留原几何，单点默认仍严格在同一点产生。

预览盒、实际随机/均匀采样及移动拖尾半径共享扩展范围，修改尺寸时清理旧拖尾基线，避免桥接旧几何。扩展单点开放分布选择，未扩展单点保持旧隐藏状态。线、圆环/圆柱及球体隐藏这些字段并继续使用自身尺寸，不受保留的扩展值影响。粒子总数量仍由Density和既有预算限制，不随体积自动增加；仅改变视觉发射范围，不新增伤害或碰撞。新值随模块保存、预设及本地Copy自动保存。

验证：146项粒子/控制原生GameTests通过。新增覆盖双点/单点在六个面的默认几何、向前延伸及对称尺寸、随机/均匀采样边界、超出原范围、拖尾范围、保存恢复、单点默认及分布可见性和其他形状字段隔离。16组GUI缩放/密度检查通过，覆盖单点/双点默认和扩展四种状态，三个数值框可编辑，分布选项显示正确；已检查扩展单点截图。双语手册115与Jar内容校验通过。

本版没有修改战斗、移动控制或着色器。协议仍21，稳定版仍0.1.243，保留0.1.273移动观察点及0.1.272对照。未跑完整套件/JUnit/攻击/GPU/原整合包回归；未上传、未修改原实例。

# 0.1.277 模块列表搜索与复制（开发版，2026-10-18）

用搜索框替换 Virtual modules 标题，保留原列表高度。按自定义名称、当前语言模块类型、模块ID名称及原编号跨页筛选；#12精确匹配原编号，普通关键词不区分大小写，可用空白分隔多个关键词。过滤不改模块顺序、编号或UUID，清空恢复全部。无结果隐藏全部条目并提示。搜索编辑保留键盘焦点，输入E等背包绑定字符不会关闭界面；密度/尺寸重建保留查询。过滤结果只在数据、查询或布局语言刷新时重建，避免每个条目重复扫描全列表。

底部同一行排列“＋ Add（宽）｜Copy（窄）｜↑｜↓”，Add和Copy分配剩余空间的约60%/40%，两者始终不重叠。达到模块上限或不可编辑时两者禁用；当前模块未匹配搜索或不可复制时Copy禁用。排序仍按全列表相邻位置执行，过滤后的分页跟随选择。

Copy在服务器上保存并深拷贝当前模块，分配新模块UUID，插在原模块之后并自动选中。保留名称、启用状态、Mark、粒子/音效、规则、频道、Move Set动作引用等配置；不复制运行状态、不移动或删除原模块，也不将副本自动加入原模块被引用的招式。独立编辑副本不影响原配置。复制后的编号若不匹配现有搜索会清空查询以显示新副本。沿用服务器编辑权限、版本校验、128默认容量及控制核心类型限制；命令模块复制仍需管理权限。未知或已不提供的类型不复制。

验证：129项原生控制GameTests全部通过；新增覆盖中英文类型/名称/#编号搜索、完整Mark与装甲规则复制、配置独立性、插入顺序、Move Set保留稳定目标引用、模块容量限制、过期版本拒绝和复制后服务器选择同步。16组真实GUI缩放/密度检查全部通过，以60模块验证跨页搜索、搜索输入焦点与背包键、原编号、无结果、复制响应选择与编号过滤清空、容量禁用、Add大于Copy及不重叠。已检查正常及小比例截图。手册114双语和Jar内容校验通过。

本版只修改模块管理与界面，不修改移动、战斗、斩击或视觉特效。协议仍21，稳定版仍0.1.243，继续保留0.1.273移动观察点及0.1.272对照。未跑完整套件/JUnit/攻击/GPU/原整合包回归，未上传、未修改原实例。

# 0.1.276 发动/飞行音效分离与多目标范围释放（开发版，2026-10-18）

Slash 原 flightSound 配置恢复为发动音效（Release sound），每批发射在Mark处播放一次，不随散射数量、目标数增加。新增独立 slashFlightSound（Flight sound）声音组合，默认留空；每道斩击自身携带声源、随飞行位置更新并循环播放。行时长0循环到斩击消失；显式延迟、时长、淡出仍有效。地形阻挡、到达射程、未加载区块及核心停止均分别停止对应飞行声并取消延迟行。复用0.1.275独立声源包，协议仍21。原发动声配置不迁移到新增飞行声。

Sphere Blast 和 Attraction 增加 Multiple target 勾选；Area Pulse 增加 Release position（Marked / At target）、独立搜索距离及 Multiple target。标记释放沿用旧行为，多目标开关在标记模式置灰。目标释放无需Mark：关闭Multiple只取最近目标，开启最多取64个符合筛选条件的目标，各自生成范围效果。无目标不释放，也不消耗新的释放间隔。球形爆破和牵引在蓄力/预警开始时冻结整批点位，目标移动不会拖走预警；范围脉冲每次释放重新采样。每批共享一个冷却，作用半径与搜索距离独立。预览覆盖全部选中点位。

Attraction 的多中心捕获共用预警/持续时钟，同一受害者若被多个中心覆盖，只归属最近的一个捕获中心，每刻只施加一次牵引，避免叠加速度。Sphere Blast / Area Pulse 各中心独立结算自身范围效果。目标中央的实体可被目标点脉冲命中，修正原标记脉冲排除零距离中心目标的条件在该新模式下不适用的问题。

验证：168项原生控制/攻击GameTests通过，包括多中心球爆、多中心冻结牵引、无Mark目标脉冲及中心目标命中、共享冷却、单目标选择、字段保存、每批一次发动声和每道独立飞行声。16组真实GUI缩放/密度检查通过，覆盖三个范围模块标记/目标模式的Multiple勾选启用状态，以及Slash两项声音编辑入口。已查看目标脉冲界面截图。真实隔离世界中验证原生音效实例循环标志、移动位置更新、发动声不循环；同时原空间波网络/世界渲染/镜头恢复回归通过。手册113双语、Jar版本与新增字段/名称校验通过。

验证过程：最初在主菜单检查声音实例时，没有玩家会按设计停止声音，因此将此项迁到真实隔离世界检查；一次客户端启动遇到Create/Registrate注册回调错误，独立重跑后全部界面检查通过。未改注册代码。未做原整合包听感测试，未跑完整套件/JUnit；未重跑无改动的拖尾GPU测试。

没有更改移动物理，继续保留0.1.273移动观察点及0.1.272对照。稳定版仍0.1.243，未上传、未改原游戏实例。

# 0.1.275 连续剑气拖尾与 Slash 移动声源（开发版，2026-10-18）

Distortion trail 改为连接相邻姿态的带状空气折射。Mark 面仍朝向剑根到剑尖，Length 覆盖剑身；宽度/高度控制厚度。超过速度阈值后，每个移动刻保留首尾并细分旋转曲线，采样间距不再控制是否输出。旧轨迹逐渐收细、淡出；停止重启与超过64格的传送不会桥接。保留最多8点/刻，128点/模块、256点/客户端；分批渲染完整保留的路径，不再抽样丢弃中间段。没有新增实体、碰撞或伤害。

Slash 的飞行音效从发射点播放一次改为每道飞行斩击携带独立移动声源。每刻更新位置与距离衰减，多目标/扇形/重叠批次互不覆盖。飞行范围结束、地形阻挡、未加载区块或核心停止会停止对应声音并取消延迟音效。丢失标记或外层触发结束仍允许已释放斩击及其声音完成。音效行沿用延迟、音量、音高、半径、时长及淡出；时长0自然播放，不自动循环。新进入可听范围的玩家按已飞行时间接入剩余音效行，不能从原生音频中途寻址。客户端断开/换世界清理，失去更新超过40刻清理，移动声源上限2048。界面名称恢复为飞行音效。

验证：164项控制/攻击GameTests全部通过，包括实际旋转拖尾、首尾连续采样、传送/停止边界、保存/包编解码，以及两道飞行斩击独立声源、位置变化、丢失Mark后继续飞行和飞行结束分别停止。真实GPU检查通过：局部带状折射、收细淡出、多批渲染、不同轨迹不桥接、背后/过期/关闭剔除、渲染状态恢复、尺寸变化；原空间波显卡回归通过。已查看直线与弧形预览图。手册112双语及Jar内容校验通过。未做原整合包实机听音、完整套件/JUnit/UI重测。

移动逻辑未改，继续保留0.1.273观察点和0.1.272对照版。协议21（客户端/服务端需同版），稳定版仍0.1.243，未上传、未改原实例。

# 0.1.274 全生命体共用阶段伤害规则（开发版，2026-10-17）

Part damage modifier 的 Whole body 现在只切换生效范围。关闭时用Mark确定独立部件；开启时无需Mark，对本生命体所有相连部件使用同一套 Phase Rules，不受旧Mark位置或失效标志影响。两种范围都可以打开和编辑多行规则，切换不清空或改写行。移除全身单条修正值、来源、频道和生效条件四个属性，统一由每行独立配置。外层Trigger/Move Set动作窗口、行启用状态、内部/外部信号、频道和有/无信号条件仍需满足；多个匹配值仍取最低值，不叠加。外来生命体不受保护。

用户明确要求不做旧单条配置迁移：旧Whole Body单条数值不继承，需自行在Phase Rules内重设；现有Phase Rules保留。频道删除解绑及便携预设解绑改为统一使用规则本身的绑定机制。

144项原生防御/控制GameTests通过，覆盖多行全身规则切换、重叠优先、无信号生效/有信号暂停、无绑定/不存在频道、外层触发、禁用、外来身体排除、范围切换、规则保存/预设/频道删除、服务器规则编辑及Move Set动作窗口。16组GUI缩放密度检查通过：Whole Body开关两种状态均只显示范围开关与可用规则按钮；已检查全身开启截图。手册111双语和Jar内容校验通过。未重跑完整套件/JUnit/GPU/原整合包。

没有修改移动逻辑，继续继承0.1.273移动联动观察状态，相关问题仍与0.1.272对照；不要将本版视为已稳定。协议仍20，稳定版仍0.1.243，未上传、未修改原实例。

# 移动联动观察点：0.1.273（2026-10-17）

用户要求先记住此版本：本版引入 Travel 与 Auto Facing / Signal Turn 同时运行，移动行为改动较多，需继续实机观察，不能仅凭164项测试通过就认定整体稳定。后续出现移动、转向、跨阶、距离或结束速度问题，优先核对本版联动变更。

当前观察版：0.1.273；Jar：exports/development-0.1.273/sablelife-0.1.273.jar；SHA256：fd986539fe22873387f890ab1330690317daf5c2a564e3f96c8951036d04aac5。
改动前对照版：0.1.272，仍保留在exports/development-0.1.272。正式稳定版仍0.1.243。

后续关注（待观察，不是已确认故障）：转弯时速度突变或侧滑、后退/横移方向、累计距离与完成时机、强制位移回拉、目标终点偏移、跨阶后高度/路线、重力及陀螺仪联动、停止后残留推力、碰撞/多关节抖动、Move Set窗口结束及模块切换。必要时与0.1.272对照复现；不要未经用户确认将0.1.273提升为稳定或覆盖稳定版。

本次仅记录观察点，未继续修改运行代码、Jar或原游戏实例，未上传。

# 0.1.273 连续移动同步转向（开发版，2026-10-17）

Linear Movement 的 Travel 模式现在可与 Auto Facing 或 Signal Turn 同时运行，普通、强制、重力移动均支持。固定方向模式每刻重新读取标记部件朝向与角度偏移，后退/横移会保持身体相对方向；转弯后累计路程继续，完成距离不重置，也不再用起终点直线距离判断。重力模式按实际核心的水平前进累计，排除单纯旋转、抬升造成的基点移动；原地顶墙不产生进度。推进后的转向立即调整当帧水平速度，保留速度大小、相对部件运动与重力垂直速度。结束/取消后的制动不会被转向从上一物理刻的旧速度覆盖。

普通/强制路线的位置基准与累计偏移随整体转向同步变换，保留相对部件布局，避免回拉旧位置。强制目标点模式从计划中的移动基点继续朝记录终点推进，避免转向把目的地一同旋走；普通和重力目标点移动仍以蓄力时记录的终点为目的地。Teleport 从蓄力到完成当刻仍暂停转向。Signal Turn 有净输入时优先于 Auto Facing；Gyroscope/悬停锚点的原有优先级保持，重力+跨阶+陀螺仪组合回归通过。

164项原生控制/朝向GameTests全部通过。新增7个真实物理场景覆盖普通路线转弯、强制后退、横移、重力转向速度/刹车、普通与重力自动朝向，以及强制目标终点不随转向偏移。检查弯曲路线按设定路程结束，非净位移累积；原Teleport完成刻优先、移动阻碍、跨阶、陀螺仪、悬停锚点、手动/自动转向和其他控制回归通过。新增测试初版遗漏移动触发、转向频道配置，随后把运行信号移到生命体激活清理之后；修正测试场景后验证全部通过。手册110中英文和Jar版本/实际说明校验通过。未改UI布局、渲染或网络格式，协议仍20；未重跑UI/GPU/完整套件/JUnit/原整合包。大型关节机械仍需实机检验。

无需新增勾选，Travel模式直接适用；预览仅显示当前朝向下初始路线，不预测后续转弯。稳定版仍0.1.243，未上传、未修改原实例。

# 0.1.272 目标对准说明修正（开发版，2026-10-17）

目标对准 Navigation 的模块简介原文误写为直接控制生命体移动。核对 TargetNavigation 实现后，中英文均改为根据目标相对方向输出红石信号，供转向与移动机构使用，并明确不会直接移动或旋转生命体。只修改简介文案，运行逻辑不变。

手册109双语覆盖与Jar版本/实际文本校验通过，构建成功；未重跑游戏测试。网络协议仍20。稳定版仍0.1.243，未上传、未修改原实例。

# 0.1.271 扭曲拖尾模块（开发版，2026-10-17）

新增 Distortion trail / 扭曲拖尾模块，生命核心与红石控制核心均可使用，支持Always、无线及Move Set等原有触发。单点Mark，标记面决定长度方向，长度/宽度/高度可设0.1–64方块，另有沿标记面偏移、移动阈值、拖尾寿命、扭曲强度、采样间距与可见距离。预览显示随部件朝向旋转的范围框；实际为范围内柔边椭球折射。模块激活只开启运动检测，首次记录基线，移动或纯旋转超过阈值后才沿经过的世界位置/姿态采样；旧姿态不跟随部件，停下或信号结束后逐渐消散。受伤触发一次模式提供一个寿命长度的检测窗口。只改视觉，不改碰撞、伤害、FOV或镜头角度。

位置插值与四元数插值共同覆盖挥动，间距不足时跨刻累计。每刻至多8点、每模块缓存32点、客户端总缓存128点、每帧至多32点，发送使用现有玩家视觉预算；只发给范围内玩家。大于64格的位置跳变不连接，换Mark/修改范围尺寸/偏移会重建基线；停用、失标、总开关、死亡或卸载清理运行跟踪。客户端离开/切换世界清理缓存与帧缓冲。屏幕效果比例可关闭，场景后处理不影响GUI。与现有空间波一样是屏幕投影折射，可能扭曲投影区域的前景；不是带完整场景深度遮挡的体积烟雾。

125项控制GameTests通过，新增运动阈值/静止、纯旋转、插值/采样上限/瞬移、累计间距、保存/属性限幅/协议编解码、客户端缓存/淡出/清理、三维范围投影、真实悬停部件旋转发包、距离筛选、停用、受伤检测窗口与无伤害验证。真实GPU检查通过局部折射、淡出、背向/过期/关闭拒绝、渲染状态恢复和帧缓冲缩放；原空间波GPU回归也通过。16组GUI缩放/密度下九个属性与输入分页检查通过，包含空白加载和空模块界面；修正新提示在空白加载时的空引用。手册108双语和Jar内容校验通过。网络协议20，联机两端需同步更新。未跑完整套件/JUnit/原整合包，未进行大型机械的长时间实机测试。

默认长度3、宽高1、阈值0.5方块/秒、寿命0.5秒、强度0.35、采样间距0.35、可见距离64。可先以Always+剑/尾巴Mark试用，再挂到Move Set挥动时段。稳定版仍0.1.243，未上传，未修改原实例。

# 0.1.270 悬停锚点与陀螺仪共同运行（开发版，2026-10-17）

Hover Anchor 旋转自由时不再阻止 Gyroscope。锚点继续管理位置、各轴限制、弹性及阻尼；陀螺仪按原倾斜阈值与拉正速度恢复姿态。拉正绕当前标记点进行，避免标记远离核心时因绕核心旋转而拉偏锚点。弹性和未固定轴已有位移保持，以当前标记位置为支点。开启锁定旋转（包括XYZ全固定）时仍由锚点接管姿态，陀螺仪暂停；取消旋转锁后下一刻恢复正常判断。生命核心和红石控制核心均适用。直线运动、信号转向和自动朝向的悬停优先级不变。

152项原生朝向/控制GameTests通过。新增偏心标记硬固定+陀螺仪、只固定Y、弹性锚点、红石控制核心和旋转锁切换测试，检查实际拉正、标记位置/高度与原生约束保留；原锚点生命周期、姿态控制和移动回归通过。首次测试中同一刻编辑后立即读取陀螺仪状态受每刻去重保护，已改为后续刻验证解锁恢复。双语手册及Jar内容校验通过，手册107、网络协议19（未改网络格式）。未重跑界面/GPU/完整套件/JUnit/原整合包；大型关节机械仍需实机检验。

稳定版仍0.1.243；未上传，未修改原实例。

# 0.1.269 地震波对生命体的贴地伤害修复（开发版，2026-10-17）

地震波原本已经接入生命体伤害入口，但贴地探测从目标脚底上方向下发射时，会把对方自身方块当成地面，导致贴地条件不成立、目标在扣血前被排除。现为地震波目标地面探测排除攻击者及目标自身的所有已归属方块；目标脚下起波中心、预警位置和实际命中共用同一探测。穿过已排除方块时沿实际旋转体素边界前进，避免固定下降1.01格跨过紧邻地面。

新增原生测试先复现站立生命体HP保持100，修复后同一环形波正常扣至94。53项地震波/防御GameTests全部通过，覆盖贴地生命体伤害、悬空躲避、关闭贴地检查后的垂直范围、目标脚下区域波、生命体伤害开关、自身排除与目标适配器不生成实体，原抗击退和防御测试通过。初版新增悬空测试误将预警设为0（最低1刻），已改为真实一刻预警后验证各阶段。手册106双语及Jar版本校验通过。网络协议仍19，未改包格式。未运行完整测试套件/JUnit/原整合包。

稳定版仍0.1.243；未上传，未修改原实例。目标类型仍需选择“生命体”或“全部”，被攻击方需允许生命体伤害。

# 0.1.268 Repeat 缩放保持与伤害修正 Move Set 接入（开发版，2026-10-17）

修复 Visual Shockwave 在重复模式每波恢复再缩放的问题：非接触触发 Repeat 的缩放现在由每个模块的显式开始/保持/结束状态控制，波纹包不再重复附带缩放。信号持续时保持目标缩放，Repeat interval 不影响恢复；信号结束后按 Zoom recovery time 平滑恢复。启用时3刻平滑进入，恢复途中重新触发从当前值接续，不叠加同一模块。总开关关闭、模块停用/移除、清理运行时、离开范围会释放。状态每10刻更新位置与保持信息，60刻未收到发送者续期则自动恢复，避免卸载/失联留下视野锁定。不同模块仍按原有距离衰减、屏幕效果比例与总缩放范围合并。单次、方块接触及预览保留原瞬时缩放。

Part damage modifier 原先未列入 MoveSet.TARGETS，导致客户端列表与服务器 action_add 均排除；现已接入分部件和全生命体两种范围。选择后设为 Move Set 触发，仅在对应动作时段生效；内部频道、原阶段规则/全局单条设置依旧需要满足，结束时自动关闭。不是取消内部信号条件。

网络新增独立缩放状态，协议19，联机客户端/服务端需同步更新；手册106。139项原生控制/防御GameTests通过，覆盖信号末端恢复、长重复间隔保持、正负缩放连续、更新不叠加、重新触发、失联回收、编解码，以及两种伤害修正在Move Set动作窗口内生效/结束后关闭。手册双语与Jar版本校验通过。本次未改界面布局或shader，未重跑界面/GPU/全套/JUnit/原整合包。稳定版仍0.1.243；未上传，未修改原实例。

# 0.1.267 Visual Shockwave 反转（开发版，2026-10-17）

Visual Shockwave 新增反转勾选项，默认关闭。开启后波纹半径从设定最大半径连续收缩至标记中心，耗时仍由扩散时间决定；关闭仍从中心向外扩散。仅改变波纹传播方向，震屏、视角缩放、距离衰减、触发和接触触发逻辑均保持原样。设置保存到模块及预设，预览也使用相同方向。

网络事件新增反转位，协议从17升为18，联机客户端与服务端需同步更新。手册105。

115项原生控制GameTests通过，新增默认兼容、反转保存/预设/网络编解码及半径端点/单调收缩测试。真实GPU两时刻检查确认由外向内收缩，原有局部扭曲范围、连续释放、背向/禁用/过期、渲染状态和窗口缩放回归通过。16组GUI缩放密度下反转勾选框可见/启用/勾选状态检查通过。手册双语及Jar内容校验通过。未跑全套/JUnit/原整合包。稳定版仍0.1.243；未上传，未修改原实例。

# 0.1.266 伤害修正全生命体模式（开发版，2026-10-17）

伤害修正属性新增 Whole body / 全生命体 勾选，默认关闭。开启后 Phase rules 置灰并在服务器拒绝打开/修改；旧规则保留但不参与计算，取消勾选后恢复使用。开启时直接显示一组伤害修正（-100至1000%，滚轮±5）、输入信号来源（内部无线/外部接收）、输入频道（0无，滚轮±1）和生效条件（有信号生效/无信号生效）。无需Mark，已有Mark的位置、损坏/断开标志不影响全生命体模式；关闭后恢复原标记约束。

全生命体模式只修正本核心相连结构的命中，不保护其他生命体。仍受外层模块启用/触发条件限制，输入频道0或缺失不生效；重叠修正仍取最强保护，不叠加。全局配置和原阶段规则独立保存。便携预设取消全局频道绑定，删除频道会正确移位或解除绑定。协议17，手册104。

19项原生防御GameTests通过，覆盖新模式信号/作用范围、原规则停用与恢复、旧Mark标志、重叠规则、保存/预设/频道删除及规则编辑器禁用。16组GUI缩放/密度通过勾选状态、属性显示、规则按钮置灰和数字输入焦点检查；全局模式截图目视检查通过。手册双语及Jar版本校验通过。未跑全套/JUnit/原整合包。稳定版仍0.1.243；未上传，未修改原实例。

# 0.1.265 Attraction 球面向内汇聚预警（开发版，2026-10-17）

替换三条相互垂直的大圆和一次性平面收缩粒子：捕获范围现在由64个均匀球面小点表示，粒子流每4刻从整个半径外壳向内汇聚，每批32条；出生方向逐批错开，避免固定经线。预警开始立即出一批。预警期间汇向固定捕获球心，牵引期间汇向实际牵引点，仍保留中心十字和停靠小圈。

已选粒子用于汇聚流，保留RGB、大小与自定义寿命；没有选择时使用预警颜色粉尘。旅行时间6–24刻，预警粒子寿命受剩余预警时长限制。预览复用同一效果。牵引捕获范围、筛选、预警/持续时间、逃脱/视线、拉力/速度均未修改。协议17，手册103。

53项粒子/武器原生GameTests在标准分组通过；新增球面起点、连续批次、全高度分布、颜色/寿命截断测试。客户端原生粒子引擎检查确认所有球面方向汇到中心，已有前后向/范围脉冲/喷火控制及调色界面两种缩放检查通过。手册和Jar版本校验通过。首次额外合并地震命名空间的运行出现旧双目标斩击测试一项失败，恢复标准粒子/武器分组后全部通过，未据此断言失败原因。未跑全套/JUnit/原整合包，未拍摄真实世界新特效截图。稳定版仍0.1.243；未上传、未修改原实例。

# 0.1.264 粒子逐条调色与 Color / Life / Size 编辑栏（开发版，2026-10-17）

粒子编辑器底部改为 Color / Life / Size 三列，颜色按钮打开已有RGB调色板；右侧选中粒子后修改，每条独立保存。调色板“预设”恢复原色。effect、instant_effect、entity_effect、dust 在左右列表名称后显示“（可调色）/ (Colorable)”；其他类型颜色按钮置灰。entity_effect、dust 显式支持原生颜色参数，因此现在可选；方块、物品及双颜色过渡参数类型仍未开放。未设置颜色保持原有外观，颜色不附加药水状态效果。

颜色保存到粒子属性，并贯穿普通发射、移动/扩散/范围脉冲、球爆、喷火、斩击、弹道与死亡特效共享路径。修改寿命/大小保留颜色，预设/存档也保留。默认旧属性无颜色覆盖。关闭调色板返回保留当前粒子和搜索文本。粒子网络包新增颜色字段，协议从16升为17，联机客户端与服务端均需更新。

51项原生粒子/武器测试通过，覆盖保存/预设、校验、原生颜色选项、普通/旅行/喷火网络编解码、扩散波颜色。原生客户端SpellParticle实际RGB应用校验通过。两种GUI缩放下Color/Life/Size布局断言、可调色名称和截图检查通过。手册102双语与Jar内部版本/翻译校验通过。未跑全套/JUnit/原整合包。稳定版仍0.1.243；未上传，未修改原实例。

# 0.1.263 核心击退抗性（开发版，2026-10-17）

Core settings → 防御设置新增击退抗性（%）：0–100，默认0，滚轮±5%。与自适应防御开关独立，可在激活期间由创造作者编辑。保存、重新读档、核心配置复制及重生模板保留设置；旧配置默认0。

近战和范围脉冲等调用生命体击退接口的攻击按比例缩放最终速度变化，100%不施加攻击冲量，保留已有速度；地震推力和抛起使用同一抗性。伤害不变。牵引、直接移动、重力和Sable原生结构接触碰撞不缩放；本设置不等于原生物理碰撞免推挤。

48项原生防御/地震GameTests通过，覆盖0/50/100%实际速度、原运动保留、普通实体不受核心设置影响、伤害不变、非法数值/权限、保存/复制/旧值默认。防御页2种GUI缩放目视检查通过。中英文说明与手册101、Jar内部版本/手册/翻译校验通过。未跑全套/JUnit/原整合包。协议16，稳定版仍0.1.243；未上传、未修改原游戏实例。

# 0.1.262 Attraction 移除重复偏移属性（开发版，2026-10-17）

移除 Offset from marked face / 沿标记面向外偏移的定义、界面、运行时叠加和双语标签，只保留 Start distance / 起始距离控制标记面外的牵引中心。目标身上释放仍直接使用目标中心，不使用起始距离。旧保存值不再参与计算，未迁移叠加至起始距离；需要保留原中心位置的结构请自行增加对应起始距离。

双语帮助和手册同步修改，手册100校验通过；Jar编译、内部版本与手册校验通过。未运行游戏测试、界面或全套测试。稳定版仍0.1.243，协议16；未上传、未修改原游戏实例。

# 0.1.261 关闭重力后的后脚续跨修复（开发版，2026-10-17）

普通 Travel 开启跨阶后，遇到水平阻挡会等待跨阶或冷却，不再立即结束移动；保持抬升高度的路线允许后续较低部件在脚底有空隙时继续探测台阶。首次跨阶仍需地面支撑，后续续跨要求自上次跨阶起实际向前位移至少 0.25 格，并满足共享冷却；无位移的墙面接触不能重复抬升。所有部件仍同步抬升，保持原有重力 On 的跨阶与陀螺仪行为。持续无法通过时受路线超时限制。

114 项原生控制 GameTests 通过，新增后脚悬隙接触、位移和冷却限制、停滞接触不重新抬升、固定阻挡等待与超时场景。已有重力/普通/强制 Travel 越障回归通过。Jar 版本校验通过。界面、全套/JUnit和原整合包未重跑；未声称复现用户完整多足结构。协议16，手册99，稳定版仍0.1.243；未上传，未修改原游戏实例。

# 0.1.260 Travel跨阶独立于重力（开发版，2026-10-17）

按用户确认，现有“重力+跨阶+15°陀螺仪”组合无需调整。跨阶开关、高度和冷却现在在Travel中常驻，不再依赖受重力影响开关；Teleport仍隐藏且不执行跨阶，跨阶默认值不变。

重力On继续使用原有水平驱动+同步整机抬升；重力Off的普通/强制Travel也可执行同一6刻整机抬升。普通Travel抬升期间遇水平阻挡会等待抬升，不立即取消；抬升完成后将新高度保留到本次路线运动结束，避免高度缓慢下沉又碰回台阶。三维目标和强制路线同步叠加抬升，不把竖直跨阶计入路线进度，完成后恢复原生物理。共享冷却和顶墙防重复保留。

跨阶制动垂直速度读取引擎当前值，避免路线对齐已经清零速度后，再按上一物理帧缓存反向增加上冲。普通Travel预览注明只展示基础路线，执行时遇障追加跨阶。

112项控制原生GameTests通过（新增普通Travel关闭重力越障、强制Travel抬升保持、路线对齐后跨阶无旧下坠速度反冲），原重力/整机同步/顶墙/陀螺仪及旧控制回归通过。16组界面缩放密度通过，覆盖重力Off+跨阶On可见/可编辑和输入焦点。手册99、双语提示、Jar内容校验通过。未重跑全套/JUnit/原整合包。协议16，稳定版仍0.1.243，未上传。

# 0.1.259 跨阶改为整机同步抬升（开发版，2026-10-17）

按用户反馈替换逐part向上助力：任一接地部件前下方发现合适地形障碍后，整条相连结构在6刻（0.3秒）内分步同步上移，每步对全部部件施加相同的竖直位移。保持当时相对位置和旋转，不再给脚/尾部分别施加向上冲量；抬升期间仅制动垂直速度，水平推进保留，结束后立即释放高度，回到原生重力。

整机共用一个跨阶状态与冷却，不同部件不能叠加起跳。沿用高度、冷却和开关，无需重配。预算、加载区域和世界边界保护保留，不增加头顶碰撞预判；原生碰撞/陀螺仪仍可影响姿态，并不锁定身体水平。实际螃蟹结构仍需用户验收。

109项控制原生GameTests通过：改写逐part测试验证全部部件同幅位移及下一物理帧上移，跨平台落地/gyro、顶墙防重复/超时、下坠/旋转/取消/完成和原有控制回归通过。手册98与中英文提示同步更新；Jar内容校验通过。UI布局不变，未重跑UI/全套/JUnit。协议16，稳定版仍0.1.243，未上传或修改原游戏实例。

# 0.1.258 Travel 重力与逐part跨阶助力（开发版，2026-10-17）

Linear Movement 的 Travel 新增“受重力影响”，默认关闭。开启后共同修正相连部件的水平速度，保留垂直速度、部件相对运动和原生旋转；按真实水平位移完成，长时间受阻会结束。完成/取消只制动水平速度，不保持高度。忽略垂直瞄准、垂直角和Force movement，UI同步禁用冲突属性；Teleport及重力Off沿用原行为。

重力On时可启用“跨阶助力”，默认Off，高度默认1格（0.1–2）、冷却默认0.6秒（0.2–10）。每个part接地且前下方有合适地形障碍时单独触发，短时补充向上速度，到达设定抬升量或最多1秒后停止。持续顶墙不重复起跳，需离开障碍并恢复清晰接地后重新允许。不瞬移，不预测头顶净空，不保证复杂结构越障。探测为有预算的地形/part边界探测，稀疏或倾斜结构较保守；关节自然传递受力，实际螃蟹步态尚需用户结构验收。

陀螺仪可与重力Travel配合限倾角，并保留当前帧已施加的推进/跨阶速度；Hover Anchor仍优先。预览标注只示意水平路线，不预测落地位置。运行接触/助力状态不保存。

同时修正0.1.257新增Gun/Arrow/Slash的Multiple target勾选可能被旧的可用性条件禁用，目标模式现在可操作。

验证：109项控制原生GameTests通过，其中7项新增重力/跨阶测试，包含真实下坠/旋转、取消/完成、独立part上抬、顶墙不无限爬升、配合gyro跨平台后落地。单次冲量不足以克服碰撞的问题已改为短时助力；跨平台测试路径移入12格测试场景边界内。16组重力页面和16组目标勾选页面缩放/密度检查通过。手册97、Jar内容和打包验证通过。未重跑全套/JUnit/原整合包。协议16，稳定版仍0.1.243，未上传或修改原游戏实例。

# 0.1.257 Gun / Arrow / Slash 目标点位射击（开发版）

三模块新增瞄准方式（默认直线）、目标类型、多目标勾选和搜索距离（默认64，1–256格）。每次释放采样可见目标当前位置，单目标选最近，多目标最多64个，每个目标各发射原有完整散射/扇形。没有合适目标不发射、不启动释放冷却；仍需Mark。Gun/Arrow的标记发射位置与Start distance保持原意，Slash角度/扇形仍生效，飞行剑气总量上限256保留。预览使用同一目标方向选择。

目标点位射击不是追踪；不预判移动，不补偿箭重力。搜索距离独立于弹体飞行射程。原始直线行为和已保存配置默认不变。

47项原生武器/近战测试通过（5项新增目标瞄准测试，含实际枪箭弹组方向、归属与两侧剑气伤害）。筛选测试缩小场景范围后避免相邻测试遗留生物干扰；最初测试资源命名问题已修正。16组界面缩放/密度通过，截图已检查；手册96验证和Jar通过。未运行全套/JUnit/原整合包。协议16，稳定版仍0.1.243。

# 0.1.256 Move Set 失效模块诊断（开发版）

招式状态旁新增小问号，悬停列出失效模块名称、模块列表编号、首次引用的动作序号及原因。诊断来自服务器，覆盖禁用、未标记、标记损坏/脱离/未加载、触发方式不是Move Set、已删除/不支持类型；重复引用同一模块只列一次。模块本体或招式停用也分别说明。修复后实时更新，无问题时问号隐藏；动作页及规则页均可查看。

102控制原生GameTests通过；隔离客户端两种GUI缩放验证名称/编号/原因及修复后隐藏，手册95和Jar通过。未重跑全套/JUnit，稳定版保留0.1.243。协议16，未改动原游戏实例。

# 0.1.255 Linear Movement 输入修复（开发版）

先完成按移动/瞄准模式筛选，再比较最终属性列表，避免每次刷新清除输入焦点及草稿。Travel speed滚轮步进调整为±1，原有0.1–40范围和手动小数输入不变。

隔离真实客户端16组GUI缩放/密度组合通过，覆盖瞬移/移动、固定/目标模式的全部可见数值输入：连续刷新保持焦点、草稿和字符输入；Travel speed步进断言通过。Jar通过，未重跑原生战斗/全套/JUnit。协议16、手册94，稳定版仍0.1.243，原游戏实例未修改。

# 0.1.254 Melee 判定扩展（开发版）

新增长度、宽度、高度扩展，默认0、范围0–128格。长度沿第一个Mark所选面仅向前延伸；宽度、高度为增加的总尺寸，向两侧均分。Start distance移动整个框，偏移角旋转扩展框。实际扫掠与Preview共用最终几何范围，跟随所属独立part；物品显示大小仍独立配置。

9项原生近战测试通过（六面、角度、后边界、保存与旧默认、剑尖实际伤害、原有扫掠与命中次数/间隔/Move Set回归）。手册94验证和Jar通过；未重跑全套、UI/GPU、JUnit。协议16，联机双方建议更新，稳定版保留0.1.243。未修改原游戏实例。

# 0.1.253 地震Ring圆周显示修正（开发版）

整圈先采地表再均匀分配剩余显示名额，修正192名额按角度耗尽导致单侧断续；采样四向对称、最多256点。32地震专项和jar通过，新增剩余13/17/31名额的真实地表/实体分布与不改地形检查。协议16，exports/development-0.1.253；稳定243保留。未重跑全套/GPU/UI/JUnit。

# 0.1.252 核心首页布局

将击杀玩家回满血行与Manual/Defense settings按钮行交换：回血开关位于上方，手册/防御按钮位于下方；标签同步移动。协议16，稳定版仍0.1.243。仅布局调整，jar编译通过，不重跑游戏测试。

# 0.1.251 击杀玩家满血与模块扩容（开发版）

核心首页新增默认Off的击杀玩家回满血，使用真实死亡事件和核心UUID归属，保留近战类型，支持模块弹射物；图腾/取消/无关或生物死亡不触发，死核心不复活。模块默认128、配置16–256、保存读取256，来源编号和客户端新增按钮同步。370默认原生GameTests、16扩容面板及核心设置截图、jar/手册93通过；JUnit未重跑。协议16双方更新，exports/development-0.1.251；稳定243保留。

# 0.1.250 Wireless锁存（开发版）

频道输出新增默认Off的Latch与重置频道（0无），短脉冲保持、重置优先且需新上升沿重新置位。支持内部波形/Sequence及外部TX，RX不锁存。删除编号同步重置引用，运行状态不保存；停用/配置修改/死亡/解除/重载清空。365默认原生GameTests、16Wireless界面组合、jar和手册92通过，JUnit未重跑。协议15双方更新；exports/development-0.1.250，稳定243保留。

# 0.1.249 空间波Mark预览修正（开发版）

Preview显示已标记方块轮廓并刷新5秒，接触模式附方向/距离线；重新Mark也显示旧位置。波纹只播放一次，标记刷新不重复发波。96控制专项、jar和双语手册91通过；未重跑全套/GPU/UI/JUnit。协议14，exports/development-0.1.249；稳定243保留。

# 0.1.248 空间波接触触发（开发版）

新增默认Off的Block contact trigger，方向/距离/冷却复用接触音探测，信号作为总开关，实际碰撞点发波，贴地不重复、离地重触发，冷却内不补发。关闭时维持单次/重复信号模式。双语及手册91同步，359默认原生GameTests、jar和16界面组合通过；未重跑GPU算法/整合包/JUnit。协议14，exports/development-0.1.248，稳定243保留。

# 0.1.247 快速连续波纹中心消失修正（开发版）

根因：保留8事件但仅4渲染槽，同位置旧波占槽，新波显示时已扩散到外圈。改8槽覆盖全部保留事件、新波优先、超8淘汰最旧。正值缩放单项/叠加上限回退30，负值-100保留。94控制、真实GPU同位置8波并发间隔0.05/0.1/0.25/0.45秒中心可见、jar和手册90通过。未重跑全套/原整合包/JUnit；协议14。exports/development-0.1.247，稳定243保留。

# 0.1.246 空间波中心可见性与缩放上限（开发版）

早期小圆环立即可见、环宽随初始半径限制、单道折射、末20%淡出；线性从中心扩散到最终半径。真实GPU半径30长短时间对比通过，长时间更慢，环外0像素变化；94控制、jar和手册90通过。缩放-100至100，协议14。exports/development-0.1.246；稳定243保留。未重跑全套/原整合包/JUnit。

# 0.1.245 牵引描述与镜头缩放修正（开发版）

牵引中英文描述移除推离；空间波缩放下限-100，输入/网络/客户端叠加限制同步；补充半径为最终半径、波纹从中心扩散且中途最强的提示。94控制专项、jar和双语手册90通过；未重跑全套/GPU/JUnit。稳定243保留，协议14。导出exports/development-0.1.245。

# 0.1.244 空间冲击波（开发版，待原整合包观感验收）

Mark世界出生点投影的局部圆环折射/模糊，加可独立关闭震屏和正负FOV缩放；Move Set/信号单次或周期，Preview本人一次。8事件/4可见环、距离衰减、背后不画环、失真效果设置控制、渲染状态恢复/异常停用。358默认、94控制、GPU环内5245/环外0、真实集成世界网络与593次渲染/FOV恢复/过期清空、16面板组合及手册90通过，JUnit未重跑。协议14双方更新，第三方光影未验收、无深度遮挡。exports/development-0.1.244含jar/截图/说明。稳定243及已上传备份保持不变，原实例与世界未操作。

# 当前稳定版 0.1.243（2026-10-14）

用户明确授权稳定化、备份和上传；悬停242及两个健康模块已确认功能完整，生命值调整量滚轮±5已重打包。354默认/90控制、16组合界面、手册89通过，最终数值修改不另跑回归，JUnit未重跑、完整包深层测试未完成。协议13，客户端服务器一起更新。旧218稳定备份保留，原实例/世界与本地.git不修改。完整备份已完成（11,755文件、577,048,821字节、6分卷），Drive13文件元数据核验通过，GitHub621源码文件哈希及main读回核验通过。回执见docs/STABLE_UPLOAD_0.1.243.json与backups。

# 0.1.243 命中计数+生命值调整（待实机验收）

计数按真实HP损失，固定UUID来源筛选，内部Sequence频道脉冲、间隔、进度；回血/扣血支持固定HP/百分比、单次/周期、Move Set每窗口代价、致死开关。自调整不触发受击/计分。各本模组攻击及机械目标路径已接入；原生外部武器/后续状态伤害不计，弹丸归属按索引查询，无世界扫描。354默认/90控制专项、16界面组合、手册89通过，协议13，JUnit未重跑。exports/development-0.1.243含jar/说明/截图，稳定218不变，原实例未修改。悬停242用户已确认功能完整ok。

# 0.1.242 悬停锚点 XYZ/弹性/旋转锁定（待实机验收）

世界XYZ独立开关，硬固定/弹性固定，拉回强度0–1000默认20，阻尼0–100默认0，每轴最大偏移0–16默认2；锁定旋转保持激活姿态。默认保持用户已验收的241效果。世界为约束首帧，旋转不会改变固定轴。弹性拉回/限幅只作用开启轴；阻尼同时作用弹性平移及自由旋转。XYZ+硬固定+旋转锁定固定标记部件，独立机械关节仍可运动。347默认原生测试通过，手册88，协议13。16界面组合完成并修复固定模式翻译键，最终复查与打包通过，exports/development-0.1.242含jar、说明和截图。JUnit未重跑，完整模组包待用户验收。

0.1.241原始悬停锚点已获用户“完全ok”实机确认；稳定218仍未升级，原游戏实例未修改。

# 0.1.241 悬停锚点（待实机验收）

Mark结构方块中心，激活时用Sable原生GenericConstraint锁线性轴，旋转自由；关闭/死亡/失标/卸载释放，重载重新取当前位置。首个触发锚点优先，悬停暂停姿态/平移控制器，支持Move Set。343默认原生测试、16界面组合、手册87通过，协议13，JUnit未重跑。大型关节机械与模组包退出待用户实测。exports/development-0.1.241含jar/说明/截图，稳定218不变，原实例未修改。

# 0.1.240 WRS 内部输出频道（待用户验收）

新增Internal output channel（0 none），沿用强度/Hold/Pulse/反转，无Mark/频率也能输出本核心Sequence频道，三路并行。独立来源max合并/清理，保存及频道删除、预设解绑已接入。Move Set直接WRS→内部频道→免伤rule2联动与结束清零已验证。340默认、96控制红石专项、16客户端面板、手册86及jar通过，协议13，JUnit/可选兼容专项未重跑。exports/development-0.1.240含jar说明截图，稳定218不变，原实例未修改。

# 0.1.239 Pulse自选中心/波纹特效（待观感验收）

两组选择器分别控制中心释放与扩散/固定边界粒子，支持大小和寿命，清空关闭，默认保持Sonic boom/Electric spark。保存/预设保留显式空选择，外扩寿命至少覆盖扩散时间+2tick，不改伤害音效。335默认、16客户端面板与已有外扩粒子引擎检查通过，实际菜单权限已验证。协议13/手册85，JUnit未重跑，观感待验收。exports/development-0.1.239含jar/说明/截图，稳定218不变。

# 0.1.238 Area Pulse 中心外扩特效（待观感验收）

样式可选原固定环或中心向外扩散球形，扩散时间0.05–10秒默认0.5秒。64点粒子运动复用Travel/budget，伤害与击退仍瞬间判定，动画不重复命中。333默认、34粒子审计、客户端64方向1/10/200tick运动及16面板组合通过，新增字段截图已检查，手册84/协议13。世界内观感待用户验收，JUnit未重跑。exports/development-0.1.238含jar/说明/截图，稳定218不变，原实例未修改。

# 0.1.237 聚焦掉落物输入框滚轮同步

滚轮/增减请求同时更新输入文本，聚焦时基于合法草稿值继续增减，确认不回滚。数量、概率/权重、Draws实际客户端连续滚轮+Enter请求捕获回归通过，jar已导出exports/development-0.1.237。客户端修改，未重跑服务端整套/JUnit（236曾331通过），协议13/手册83不变，稳定218不变。

# 0.1.236 新掉落物编辑页 + Radius 64（待用户验收）

全身/部位统一27样本+背包+右侧数量/规则/模式/抽取次数/清除。Shift左右明确编辑规则，空手左键只选中，物品和组件不消耗，背包Shift复制到首个空样本。旧数据与掉落算法保留。331默认、59掉落物审计专项、新界面实际GUI与手册83/jar通过，协议13；整合包实际Shift鼠标验收待用户，JUnit未重跑。Sensor Radius上限64默认12，按用户要求直接改，无新增64格专项。exports/development-0.1.236含jar/说明/截图，稳定218不变，原实例未修改。

# 0.1.235 Health Phase 多行阶段规则（待用户验收）

最多64行各自HP范围、频道、强度、Hold/Enter和脉冲；外层Trigger总门控，重叠并行，同频道取最大。独立行时钟，排序不重启，修改某行不影响其他行。旧单行转第一行；保存/预设解绑/删除频道与编辑权限已接入。328默认、90控制防御专项、规则页实际GUI、手册82及jar通过，协议12。原有罕见waiting未复现，不宣称修复；JUnit/全部可选专项未重跑。exports/development-0.1.235含jar、说明与截图，稳定218不变，原世界未修改。

# 0.1.234 规则时机与核心返回按钮

每行Armor恢复有信号/无信号生效时机，信号结束会恢复，无效频道不免伤；外层Trigger总门控不变。13防御专项、规则页实际GUI及jar通过，协议11/手册81。Core Settings删除左侧模块、右侧X改Back返回模块面板，按用户要求无额外测试。默认套件/JUnit未重跑，233曾321默认通过。exports/development-0.1.234已导出，稳定218不变。

# 0.1.233 多阶段伤害规则（待用户验收）

Armor改为最多64行规则：启用/内部或外部输入/频道/−100%至+1000%修正。外层Trigger为总门控；匹配取最低值，无匹配正常承伤。旧数值按授权不迁移。保存、预设解绑、删除频道编号处理、服务器权限及revision验证已接入。321默认、12防御专项、手册80和客户端编辑页检查通过，协议11，exports/development-0.1.233已打包。可选模组专项/JUnit未重跑。包含陀螺仪碰撞短注释。用户已确认232退出世界与陀螺仪正常，稳定218不变。

# 0.1.232 退出世界原生崩溃修复候选

用户ZIP确认Rapier getPose缺失刚体ID，直接Rust abort。发现Sable先移除刚体再卸载核心，核心清理标记信号可能触发方块姿态重算；卸载改为静默退注册输出，服务器停止时不通知方块，正常运行关闭信号规则不变。101专项、全新测试世界317默认回归及保存退出通过；旧测试世界一次6失败根因未定，JUnit未重跑。原机器退出验收待用户确认，稳定218不变。包含231右键修复；exports/development-0.1.232含jar和说明，原世界未修改。

# 0.1.231 核心界面断线修复

补上 HeartDashboardScreen.sync 的 sensor 提示空值判断；0.1.230 在打开菜单包先于模块快照时会空指针并断线。16组客户端缩放/密度均验证同步前、空列表及陀螺仪列表初始化通过，jar已导出 exports/development-0.1.231。协议10/手册79；未重跑GameTests/JUnit，稳定218不变。退出世界崩溃尚未定位：实例23:03:46结束于Sable保存子结构，没有新crash-report/JVM报告；未改原世界。

# 0.1.230 陀螺仪第一版（待实际机器验收）

新增Gyroscope：标固定参考部件→摆希望姿态→记录当前姿态为水平→激活；保存局部向下方向，普通解除/重载不用重录。默认20°/90°每秒/完全扶正/碰撞On，支持范围内回正与预览。Linear Movement优先；扶正暂停Signal Turning/Auto Facing；无恢复需求可自由转向，物理平移保留，不提供悬浮。改参考/新body转移及便携配置需重校准。316默认、20旋转专项、16面板组合通过；手册79，协议10。实际多关节/马达机器稳定性待验收，未宣称已验证。exports/development-0.1.230含jar/说明/截图，稳定218不变；罕见Phase waiting未复现、旧6项根因待验证、旧世界失败搁置。

# 0.1.229 开发验证

Arrow独立落地Sound Mix：默认原版、自定义替换、空组合静音，发射快照/箭NBT保留；命中生物不替换，有效爆炸方块命中也播配置组合。原有箭速和Start distance不改。包含Melee burn0–30秒默认0以及Sound Mix Range±8。311默认、11声音、8 Epic Fight专项通过，手册78、构建通过。Epic Fight普通Guard原生不挡弹射物，原版箭与模块箭对照相同；Parrying均可挡，未改其规则。协议10，exports/development-0.1.229含jar与说明，等待听感验收。稳定218不变；Phase waiting未复现/未修复、旧6项根因待验证、旧世界失败搁置。

# 0.1.228 开发验证

无线页扩大并自适应视口，Back/名称/频率分行，列表跟随Modules密度动态分页（最多64行）。服务器验证页长，选中频道、名称和状态对应保留。协议10，服务端与客户端须同版。309默认回归、76手册、两个窗口尺寸×16 GUI/密度组合（共32）实际客户端检查通过，已查看三张截图，较紧凑界面隐藏只读时序区避免重叠。exports/development-0.1.228含jar、说明及截图；等待用户验收。稳定218不变；罕见Phase waiting未复现/未修复，旧6项失败根因待验证，旧世界失败搁置。

# 0.1.227 开发验证

无线频道新增改名框（Enter/✓，空白清除），列表保留编号. 状态 名称，保存/复制保留，改名不重启信号。名称同步仅当前页和选中频道，隐藏只读槽不暴露玩家物品栏；协议9，服务端/客户端须同版。包括Sensor Mark选中面预览及新Trigger Sound默认组合清空。309默认回归、手册75及jar通过；实际GUI待验收。Health Phase罕见waiting未复现/未修复，旧扩展6项失败根因待验证，旧世界失败搁置，稳定218不变。详见exports/development-0.1.227/RELEASE_0.1.227.md。

## 2026-10-12 Health Phase罕见waiting：未复现，未修复

用户描述Sensor检测输出内部频道1，其他频道1模块已触发，但Health Phase Hold在血量区间内仍waiting；Phase输出频道2→Particles频道2，删除重建Phase后恢复。补SensorHealthPhaseGameTests原生装配链路，覆盖Hold持续、模块保存加载、模块重排、无线总开关暂停恢复、目标离开重入、Enter切回Hold及clearRuntime恢复。71控制回归通过，verify226-phase-final.log。当前无证据证明用户罕见状态根因，未修改Health Phase运行逻辑，不宣称修复。运行顺序为Phase先Sensor后，正常新输入存在最多一tick传播延迟，不能解释持续waiting。初次测试Particle误设单锚点区域模式，后期测试目标未随物理移动结构移动而离开范围；已修正测试条件，保留日志，无生产逻辑修改。

待下次现场复现，应保留出问题的Phase配置，查看TriggerPowered、PhaseActive、PhaseChannelStatus、生命百分比与上下界、输入频道实际数值；当前故障模块已被用户删除重建，缺少故障时状态证据。

另外已直接修改源码：Sensor Preview同时显示Wireless redstone样式的选中面绿色边框和原检测范围；新建Trigger Sound不再默认带minecraft:entity.ravager.roar，已有显式音效配置保留。尚未重打包，exports/development-0.1.226中的jar仍是上述源码小改之前版本，稳定218不变。

# 0.1.226 开发验证

Target Sensor改为通过选中面给Mark方块15级弱红石充能，标记灯自身会亮，取消原全部面发射；Internal/External与反转保留。307默认回归、手册74、构建通过，原生灯及六面/停用/死亡清零覆盖。主动启用的旧sablelife_tests有6项无关失败，改动前对照同样失败，详见exports/development-0.1.226/RELEASE_0.1.226.md。实际GUI/整合包待验收；稳定218不变，旧世界蓝图失败搁置。

# 0.1.225 开发验证

目标感应新增内部输出频道、信号强度、红蓝频率与反转信号。Mark自带15普通红石输出；反转在有效触发时无目标输出、有目标断开，停用/死亡仍断开。68控制与305默认GameTest通过，手册73通过；实际GUI和整合包路由待验收。稳定218不变，旧世界失败继续搁置。详见exports/development-0.1.225/RELEASE_0.1.225.md。

# 2026-10-11 0.1.224 interaction switch

User identifies bearing/rightclick shutdown vulnerability and explicitly requestscoreonoff because someauthors operatecreatures. ImplementedAllowSurvivalInteraction defaultfalse,Coreprotectionpage2togglekey survivalInteraction,strictedit0/1 creativeCanToggleBreaking,CoreNBT/load/defaultmissingoff/copyConfiguration/updateTags/respawntemplate. WorldLifeLifecycleinteractionAllowedset persisted;setonactivation/respawn/reloadfirstcapture andliveedits;cancel/completeclearIDs. BodyInteraction.allowed Creativebypass,Spectatorfalse,ownedpos usesloadedcoreflag orpersistedID,unownedconnectedactiveLifeCoreflags;excludesindependentControlCore/DORMANT. canAccessEditor forcontrolcores nowchecksbodypolicy soembeddedcontrolsnotbackdoor.

HIGHESTRigthClickBlock blocksblock/itemuse/cancelPASS preservingairuse;LifeCore normalstatusreadonly. LegacyBodyCombat blockitemplacementguard whenallowed onlyUseItemFALSE(noeventcancel) letsenabledfrequencyblockusewhileLivingBlockPlaceMixin stilldeniesplacement. EntityInteract/Specific forAbstractContraptionEntity checksassemblyanchor+entitypos,ItemFrameactualsupport. CreateBodyConfigurationMixin handleHEAD plusCreateBodyContraptionInteractionMixin C2Shandle rejectclosed. OrdinaryMenuProvider rightclickpositiontracked2ticks,Open/Close/Postplayertick autoscloseslocked menus;ServerGamePacketListenerImpl handleContainerClick/ButtonHEADmainthread guard. Weakmaps valueonlypos/dimension/containerId(noindirectplayerretention),Close serveronly;secondaryusewithhelditemsnotassociatedtoitemmenus. Unknownthirdpartyremoteprotocolsnotguaranteed.

CoreUI6toggle rows,void159label163,chunkstatus183,manual/defensebuttons195;defaultpanel246fits,newGUIuserplaytestpending. Manual72 bilingualgenerated/validated;paragraphmarker neededupdate afterprevioussoundwheelcopyeditedsourcewording. Corefield labels retainedsoundsenabled已启用/已禁用.

Tests302default verify224-full-final,114C2ME/AT/Neo/Toolgun control/redstone/respawn/mount verify224-native-verified,80NoPhoto verify224-nophoto. New3BodyInteractionGameTests includesrawhand/wrench/samples,permission/NaN2,world/corecopy/save,release,actualLever+Createpacket,actualChestproviderafterfakeoverride,oldmenupolicy,nonemptyControlledcontraptionnativepacket,ItemFrame. FakePlayer normallyopenMenuempty overriddenforprovider+Openevent. Anonymouspayloadvariable contraption hidinheritedfield yieldingnullunderSynaxis;renamedassemblyandrealpayloadfixed. MountedfullTwinGun mixedsuite failednewrecycledspringposoldowner(dyingfalse),isolated16passed;diagnosticprovedstalefixtureownership atpos,onlynewfixturepositionsforgetbeforeplacing,allassembly/deathassertsretained. Temporaryforcedchunk/autoadsemblyexperimentsremoved;no deathproductionchanges. Productionsecondaryusefilteraddedafter302run;finalnative114andNoPhoto80coveredfinalcode,jarassembledfinal. unitsnotrun(earlierSoundFadeTestexpectationsstale,usernosoundtests).

Dev224exportmanifestSHA. Stable218preserved,noGitHubDriveupload. OldNewWorldcaseexplicitlyshelvedstillunresolved;doNOTresumeunlessuserasks. Usertestnextcorepage2off/onbearings/frequencies/chests;keep leftcombat andblockplacementbreakseparate. No requiredimplementationworkpending.

# 旧世界蓝图失败：未解决，用户要求暂时搁置

2026-10-11：用户测试0.1.223后，New World放置a life蓝图仍提示“Loaded blueprint sub-level 3 has empty plot bounds”。因此不能将223读取一致性补充标记为已恢复这个旧世界，也不能仅凭截图认定整个存档损坏。隔离回归已通过的结果保留，但不替代旧世界实测。

用户明确要求这个case先放一边，未来地图继续人工观察。停止该案例的进一步调查、代码修改和存档恢复操作；不创建自动监控。保留先前日志、截图及复现记录，后续只有用户要求再继续。用户已确认的新地图重生测试结果仍有效，Heavy Autocannon的实际验收未在本条消息确认。稳定版仍0.1.218，开发版0.1.223。

# 2026-10-11 0.1.223 read consistency + CBC AT heavy

Useraccepted222respawns outsidechunks/killed/rejoinmidcountdown. OldNewWorldstillPhoto redfail, screenshotemptybounds9,current222logfail3airBEs. Originala life.nbt READONLYparts3/9 solid7/8 confirmsnotemptytemplate. Can'tclaimwholeworldcorrupt. Added deterministicPlotReadConsistencyGameTests:injectvanilla4cacheviareflection afterplotcreation =>222failsStaleemptycacheoverridesloadedplot,223passesloadedidentity+stateandretiredcacheairafterremove. NewAuthoritativePlotChunkReadMixin priority2000 getChunkHEAD:mainserverthread,FULLonly,container.inBoundsregion;returnregisteredactualchunk oroneperlevelnative-equivalentEmptyLevelChunk. Terrain/otherthreadsunchanged,nochunkforcing,noresetoccupancy,nooriginalworldwrites. ActualNewWorld223playtestpending; donotclaimguaranteedrecovered.

SecondaryuserHeavyAutocannonreportresolvednativeclassdifference:ATAbstractHeavyAutocannonProjectileextendsCBCAbstractCannonProjectiledirectly,owncalculateBlockPenetration,previousCannonBodyDamageMixinonlyBig+normalAutocannon. AddedthirdATtarget@Pseudo optional,sharednativefielddamagebridge+dedup. HeavyCannonGameTests mountednamespace3ammo HA_AP/HA_APDSFS/HA_HE nativeprotectedcallback23damageandrepeatnochangepass. Scopefutureoverridefamilystillrequiresreview.

C2MEjarread/extracted.dev/cache223fromuseroriginalreadonly,profilesplotCacheCompatTestsaddsprivatecopyfullC2ME.jar. SourceinspectionC2ME doesn'tproveitspecificrootcause(no accusation). AddednativeC2ME/AT/Toolgun/dragonReplayprofiles63pass verify223-compat;299default verify223-full;7NoPhoto(noCBCAT)verify223-nophoto-finalpass. FirstNoPhotoaudittargetcontainsnativeBlueprintPlaceSessiontestneedsPhoto =>failmissingclass; movednewpureSable readtest tonewnamespace sablelife_plot_read_tests withcombatfixture/defaultenabled;noassertionweakening. Finalsourceunchangedafterfullrun(excepttestnamespace/profile),jarassembledNoPhotoFinalsameproduction. unitsnotrerun(earlieraudioexpectationsstale). Dev223exportSHAmanifest,Stable218preserved/noGithubDrive. Userneedsrestart223,trya lifeblueprintinNewWorld,thenheavyweapon. Ifoldworldstillfailscapturelatestreasonnotblinddeleteworld.

# 2026-10-11 0.1.222 flame fix; old-world placement still pending

User authorized flamebodyfix and asksoldNewWorldcannotplace. Confirmed221installedandrestartedstillredgenericfailure. Originalcurrentlatestlogs221startup/userplayingotherworld, no concrete placement exception. OnlyreadNewWorldsmallNBT viaexisting.dev/read_blueprint_nbt.py:occupancy exists,numerousbits(notproofcorruption);arena1recordready; nooriginalworldwrites. Do notclaimrecovered; do notresetoccupancy/deletesublevels. NativePhotoBlueprintToolService.loadBytes catchesexceptions returningreason but serverUI onlygenericred. AddedPhoto1.0.1-gated BlueprintToolFailureDiagnosticsMixin RETURN loadBytes logsreason,andBlueprintToolFailureMessageMixin handleLoadBlueprint beforefirstBlueprintToolResult.success call captureslocalresult printsfailedmessageplayer. NativeOPfake test withinvalidgzip verifiesboth actualplayer messageandlog NotinGZIPformat. Userneedsinstall222,restart,tryoldNewWorldblueprint once,sendnewredFailed to load Sable blueprint reason; canreadcurrentlatestlogs. Safe repair blockedonconcretereason,notcompleted.

Flame fixdone: ModuleCombat.flame skipsLifeTargetoldcentrepath; BodySphereQuery.querySweep scansnearbyloadedALIVEcores,acceptsLifeAttack gates.self; boundednearest voxels,weightedSegmentBoxContactclosest exactsegment/rotatedscaledcollisionAABB,terrainLOS,damageAtactualblock,UUIDstate.hits interval. Existingnativeentityfire behaviorunchanged. AddedFlameBodyGameTests oncontrolnamespace2 tests:offcenter7blockwidebody93HPattacker100,interval20after12ticksstill93;actualsurfacehit,self/empty/terrain/optoutnohit. Sharedcollisionbudgetspend8/shape. Legacyquerysphere/volumes nullSweep unchanged. No unitrun(soundsstaleexpectedearlierusernosoundtests); no claimunitpass.

62control,299(default298+optionalcache1)full,21respawn/cache/toolfailure details,63NoPhoto control+optionalabsence passed;build222export. Restoredbuild.gradledefaultnamespace toexcludeoptionalPhoto-dependentplot_cache_tests; previous221intendedrollbackdidnotexecutebecausePowerShellcommaargumentparserrejectedwholecell. Sourcecurrentdefault298. Testssetup initiallyJava varcompound/release nonexistent/virtualenabledsetter nonexistent fixedtouseknownresetForCreative; noassertionweakening. Stable218unchanged,noGitHub/Driveupdate. Finalhandoffoldworldissuependinguserdetails.

# 2026-10-11 0.1.221 plot cache fix

User reports after world rejoin respawn parts missing, cancel then manual Photo fails near area perceived invisible blocker. Read original instance logs only: previous2026-10-11-7.log.gz14:42:25 air-state BE warnings then Photomancy skipped a recorded block;14:42:45cancel;14:43:04 manual placement air warnings. Latest restartedlog has missing holding dependency diagnostics; no proven physicsghost root cause. Native source Sable LevelPlot holder lifecycle doesn't invalidate vanilla recentchunk cache; getChunkFutureMainThread interception bypassed on cachehit. Added deterministic PlotChunkCacheGameTests: deletebody,warmemptychunk,decode savedtemplate, reallocate sameplot => baseline valid fixture fails emptyplotbounds, fixpasses both respawn+manual afterremoval. Earlier test setup attempts missingnamespace and qualifiedtemplate rejected byGameTest namespaceprefix; corrected dedicatedcombatfixture. Added ServerChunkCacheAccess.clearCache invoker, PlotChunkCacheMixin addChunkHolder RETURN and onRemove RETURN(serveronly). Changes event-driven,no tickscan,no radiusdeletion. Operator cancelPartial regression now runs native manual placement afteractualcancel. Native physicalremove observer alreadyremovesphysics; no speculative physics deletion added.

297(default296+cache1) pass verify221-regression;21 native+dragonuserblueprint/Toolgun profiles pass verify221-native-final;NoPhoto optional+whole+decor suitepass verify221-nophoto. Defaultnamespace list reverted toprevious(defaultdoesn'trequirePhoto), cachetest optionalnamespace sablelife_plot_cache_tests. assemble221exported. Userworldunchanged,no automaticrepair of existingcorruption; actual fullinstance restartretast pending. Stable218 remains; noGithub/Driveupdate. Includeslatest220fadewithinlimit/wheel/status source changes; existingSoundFadeTest expectations still describeearlier semantics (userrequested no tests forthosechanges); unitsnotrerun, don'tclaim221unitpass. May update audio tests whenauthorized furtheraudio testing. Currentissue implementation complete; usertestnext.

# 2026-10-11 0.1.220 layer fade

Completed SoundTimeline.Row fade ticks default0/clamp12000, edit seconds0..600 finite validation, copy/enabled/persistence preserve, missing NBT Fade0. ModuleSound packet adds fade and protocol8; delayed queue retains fade, ModuleAudio sends fade. SoundFade pure envelope used by ModuleSoundClient RadiusSound and BattleMusicClient MusicVoice; after limit linear fade to zero, stopAfter duration+fade, duration0 natural/BGM loop. Manual stop/release unchanged. GUI sixth field at second-row third column; preview selected/all carry fade, wheel1, timeline fade tail shaded. Bilingual tooltips/manual71. 61 units,296 fresh-world defaults,9 final sound tests pass. First reused runs/gametest had5 death cleanup failures; fresh296 passes, don't claim separately proven root cause. Real listening/UI playtest pending. Dev220 export manifest/SHA, stable218 untouched, noDrive/GitHub upload. No required coding remaining.

# 2026-10-11 0.1.219 contact audio move

User explicitly revoked legacy migration: direct move, they accept other consequences. No migration code added. ModuleFields.sound removed5contact properties/uiorder; trigger_sound receives them. ModuleEvents normalTriggerSound branch nowalso contactSound state, contact follows powered signal +switch, independentdue, touchentryonly; oldSound contactbranch removed. Directionlookup nowtrigger_sound. Dashboardmark/MarkingSessionspreview move toTS; contactruntimecaption; manuals70 andzhpolish updated. TS default stillmarkedredstone andravager ordinarycue; clearordinarymix forcontactonly, manualexplains. OldSound contactfields noteditable/executed, noautoconversion. Stable218 preserved, mainGithubstill218, noDrivebackupnewdev.

Tests: finaldefault296+58units/manual70/build pass. Active ContactSoundGameTests wrappers use native originalcontactfixtures nowTS, including6faces/reload/reentry/continuous; newpowered test+normalcue independence aftersignal edge/cooldown. RedstoneCore sound mix permission test movedcontactkey toTS, retainsdisabledcombat assertions. Oldlegacy namespace35run had6attack/wire failures unrelatedpaths (baseline218notrerun, don'tclaimformalpreexistence); noattackassertionweakening. Newnormalcue test selectedbellnotindefaultlibrary causingeditfalse; useknownroar andasserteditingtrue, floorfixturesstable. Fulldefaultpassesall.

Export219 1368590bytes SHA256 9181b7e1d7c8da162c8cd802833caa821b6ae0ff05151371fb436d8a9d243236. UserwillacceptactualUI/audio; no furtherrequiredwork.

Release218 complete: final local backup backups/Sable-Life-0.1.218-stable-20261011-124307.zip 565921710bytes SHA2568ef5451a5fc12a2e2036e429c0665539678306d098eff5381631cec6014f457f. All10307 archived file SHA256s verified, six90MiB parts joined hash verified. Drive15files (jar,stablejson,release,SHA256SUMS,6parts,archivehash,partsjson,restore,joinscript,githubreceipt) readback names/sizes/parentverified; normalizedDrive lackscontenthash. Receipt backups/drive-stable-0.1.218.json. Folder unchanged Sable LifeBackups1O28g5nJqjPLvpt6s99kOqeTMi5h7wG7f. Jar Drive1ET3qQrK13H8g0JIohFcT7hcmg0NDAwwO. GitHub main281d6fafd600b9d9c707de519cdce4fb1d58bdfe all571sourceblobhash/mode verified private. Previous200 backups untouched. No requiredworkpending. This operational handoff update occurs after immutablebackup, sourcecode/stablemetadata unchanged.

# 2026-10-11 stable 0.1.218

User accepted217native whole-life cleanup and authorized stable after final fixes. Final218: arena record label red re-record-after-release note (en/zh, adaptivewrap); BodyCombat feedback red onDYING/DEAD elsewhite; BodyDecorationCleanup discards HangingEntity by exactsupport ownership/death/body before native plot removal or budgetedblock removal, including painting/item/glowframes, no global deletion. Native Sable kickAllEntities relocates untagged entities into world, causeofsky paintings. New painting/frame/outsider/fallback/nodrop tests+fatalactionbar test pass. Testsamepackagecombat initiallycausedJPMS splitpackage; moved to testing + reflective existinghelper (no productionAPIfor tests). No renderer rewrite. Userworld untouched, existing old sky decorations not scanned. NativeGUIstyle visually stilluser-retestpossible, serverfeedbackasserted.

Final291default+26specialty+21compatuserblueprintToolgunmounteddecor+40NoPhoto+58units pass, manual69, build. STABLE_VERSION218 previous200 retained. Jar exports/stable-0.1.218/sablelife-0.1.218.jar 1367997 bytes SHA256 d50a63b22d641bbba8e1a04ed95a3332c53e5ee8535d0c9d80d1513bcd97f944. PrivateGitHubmain now281d6fafd600b9d9c707de519cdce4fb1d58bdfe tree49f0f934668e648b8e67548e641dd784eac93891, all571blobhash/modeverified, leaseparent72e1e... (214). Original.git unchanged. backUps github218-receipt.json. Backup script nowincludes optionalreplay/private runtimefixtures onlyinlocal/privateDrive archive, neverGitHub/Jar. Intermediatebackup123655done, finalbackupaftermetadatarefreshpending. Driveexisting rootSable LifeBackups1O28g5nJqjPLvpt6s99kOqeTMi5h7wG7f; uploadnew218parts/jar/manifest/release/restore and verifyreadback. Do notdeletepreviousstable200or193/194backups. Userrequestedworkstopafterreleasecomplete.

# 2026-10-11 0.1.217 whole life removal

User authorized trial: allparts afterdeathdelay in one tick. WholeLifeDeletion prepares alllevels/group no mutation, validates loadedownedcoords+exactbody and allsolidcount via palettes, rejects mixed/worldblocks. Allocatedchunks only, no bboxhole mistakenunloaded. One attempt/server/tick rotated, MAX1048576registeredcoords thenfallback; WAIT noforcedload/20tick retry. FALLBACK flag persisted, old saves defaulttrywhole. removeAll pre-detaches all eligible mounted payloads before anybody removed; nativeSable or verifiedToolgun0.3.6collectionbatch. Runtimefail catches+logs, retires onlyremovedbody records, preservesremainder; no transactional rollback. Airrecord retirement rechecksAir. Maps records bybody avoid quadraticforgetloops; cleanupcursors retired whenempty. Same whole-stage effectsampling, single rewards retained. NativePlot maps physicallychunkloading checked. StateonlynewDeath.bool+transientretry no clientprotocol change. Config keys/values unchanged, manual68withexact limits/exceptions.

Tests: default290+50specialty, userDragon+Toolgun4pass; units58; Final38 NoPhoto pass. New WholeLifeCleanupGameTests large32cube32768+2limbs same tick, unrelated sentinel; mixedforeignfallback; unloadedrecord wait/reload/forgetunknownretry noforcedload. Fixture corrections: force only isolated normalworldcolumns to prevent naturalSable unload, mixedowner assignment mustforget beforebind becausebind.putIfAbsent, no productionweakening. Dragon onEachTick registration inside runAfterDelay caused repeatedinit callback incombinedsuite; explicitinit guard, assertionskept. Primarynative tests allpassed. Export217 1365157bytes SHA256 02f3dab1c71d1913bd623c7d9bcda9154c65c46d6219f7ab3181f42a884b9fcf. Jar/manifest/RELEASE/SHA256SUMS in exports/development-0.1.217. Stable200, actualworldtestpending; privateGitHubmain still214 (no215-217upload). Userworld/sourceblueprint untouched.

# 2026-10-11 0.1.216 death-countdown cannon fix

User gave private Sable-Schematics/c-21 dragon v2 test.nbt. Source preserved, local copy .dev/dragon-template.nbt and optional -PdragonReplayTests=true profile + required arenaAddonTests/mountedCleanupTests. Blueprint33parts, main6810blocks,7Fixedcontrollers,8CBCcannonentities and propellers total17contraptionentities. No LifeHeart: inject only one test core on main body. Old Mianbao launcher palette missing in current mods; native tool placer reports diagnostics; replay focuses actual supported CBC data. Initial world chunk availability false (all plots unload on next tick); isolated loaded area fixes fixture (no production force-load change). Native direct death clears17 entities. Power loss during death reproduced old cannon.disassembled true/unowned restoration; fix gate dyingOwned(controller), cancel native mount.disassemble before CLEANING but preserve visual, then discard during CLEANING. Register LifeLifecycle.schedule before stopping signals to cover synchronous callback. Living fixed disassembly retained. No global scan or deletion of old orphans.

Final290 default +54 specialty +1actual blueprint power-loss pass, units58, buildpassed; Final35 NoPhoto pass. Export216 1356042bytes SHA256 4595111cf452e5f5267e0d70697805ea3d00ba3619f4dd1b67fcbbf0b4c6a0a9.11config keys/ranges/values preserved, commentscorrected, CONFIG_REVIEW_0.1.216.md explains everykey. No public source upload for215/216 yet; privateGithubmain remains214. Stable200 unchanged. User confirmed215actual fullappearance/functions restored; no stablepromotion. Currentremaining actualworldcannonretest. Deathwholeatomic path remains asbefore; giant/unloaded/ownership validation fallbackstillperblock.

User follow-up: residual cannon controller is CBC Fixed Cannon Mount, reportedly all deaths. Added full native fixed rotary/twin/heavy regressions, corrected initial test geometry that overwrote the mount; final14 mounted cleanup pass (verify215-fixed3.log). No production cannon/death deletion changes, actual failure still unreproduced; async asks existing Photomancy blueprint name/path to reproduce. Primary215 export complete, Github not synced for215 yet.

# 2026-10-11: 0.1.215 native direct respawn

User preferred Photomancy blueprint tool placement over incremental respawn. New builds call SableBlueprintPlacer.place synchronously under existing trusted template scope; scoped native beforeBlocks event captures exact session mappings for all part origins and module UUIDs. Direct records persist completion/mappings and do not repeat; failed saved direct builds reject retry. Native diagnostics or missing recorded parts/nonair blocks stop activation. Legacy nonempty progress lacking SableLifeDirect stays incremental, preserving old partial jobs and cancellation/tick guard. No direct-path custom per-tick physics stabilization. Native pose semantics retained, core may be offset from Arena template basis center. UI building text before call, no smooth progress possible during synchronous work. Manual67 and tips included. Death cleanup unchanged; user reports actual Neo/AT cannon leftovers; standalone native mounted regressions insufficient proof for giant machine. Actual instance latest.log 0.214 confirms incremental commit “placed sublevel no longer loaded”. No user world modified. Stable0.200 unchanged. Export215 completed: 1356113 bytes, SHA256 f332672c489b2ee9dac31d20a48a3438f5261df7536a0e339339685fc3620b4a. Final50 native/mounted/Arena/cleanup pass,20 addon replay/respawn pass,35 NoPhoto pass, manual67 validated. Github sync status not yet changed.

# Latest0.1.214 ground countdown / manual notes

User high Arena anchor needed for large template spawn but timer floats too high; wants standable center ground and manual precautions. Added RespawnDisplayPlacement common helper: centerXZ; loaded column only; motion-blocking heightmap downscan; collision top coveringXZ,2clear blocks/no fluids; nearest belowanchor preferred, otherwise lowest above, voidfallback anchor+2.5. Timer only computes/updates when displayed phase/second/bar changes, no per-tick ground scan. Actual display moves tosurface+2.5; spawn center and plan unchanged. Cancel64range now min distance toanchor or own marked TextDisplay, not merely anchor; orphan behavior unchanged.

Important source correction: Photomancy Exporter.coordinateFrame ignores suppliedArena center for nonemptyframes, chooses portable basisAnchorPose (largest storagevolume basis). Legacy plan adds Arena center to normalized relativeposes. Thus previous manual claim relativeArena recordedposes wrong; corrected to relativeparts/native normalizedanchor. Placement anchor notnecessarilybottom ofentiretemplate; lowarena maygroundoverlap pushout/jointstrain. ManualUsage explicit paragraph notes sufficient fulltemplate space/highanchor/no automaticgrounding/changingcenter changesdestination; Settings groundeddisplay andcancelpoint. Manual65.

36 native respawn/Arena/lifecycle/cleanup pass incl highanchor+ceiling/slab/void/unloadedsurface and actual countdown150blocks above terrain, Yanchor unchanged, cancel fromgrounddisplay>64fromanchor. Integration fixture initiallyfound otherGameTest terrain at -47 insteadcustomfloor-55; explicitly cleared onecolumn aboveknownfloor, not mirrorimplementationexpected.40actualNoPhoto pass;buildpassed. No user world modifications, no full new290 rerun justified for limited displaychange. Lastfull290from212. Stable0.1.200 unchanged.

Export development-0.1.214/sablelife-0.1.214.jar1352027bytes SHA2565c5ea1070dc47ab5cc6e94e79562ea4f55215641a7dc570f8233b7e6e5f15a26. PrivateGitHubmain72e1e30da87eb7bfc0f78508d45dafa768234c8d,561blobsverified. Receipt backups/github214-receipt.json. Awaitactualworlddisplay/manualfeedback.

--- Previous ---

# Latest0.1.213 UI note only

User requested config limit note next to record current structure. HeartDashboardScreen.renderLabels adds gray second line only arenaRespawnRecord: 方块/数据上限随服务器config设置 / Block/data limits follow server config. Existing wrap/row height fitting reused. zh/en+chinese-polish generator key synced. No functional limits/lifecycle changes. Build passed, no new redundant tests/full GameTests. Manual revision64 unchanged. Export development-0.1.213/sablelife-0.1.213.jar1349015bytes SHA256881040f6da3c740a82fa9de1671763f4fb667f1f6c17bee1e9f80f10fca9990c. PrivateGitHubmain39b630dba5a97538af0876687be6995c3c1f51e9,558blobs verified. Stable0.1.200 unchanged. Await user feedback.

--- Previous ---

# Latest 0.1.212 development validation

User actual63994blocks>32768 and AT/Neo barrels still fall after death. Found Neo0.0.6v CompactCannonMountBlockEntity directly extends KineticBlockEntity, not CBC base. Add known compact controller recognition and pseudo disassemble hook; direct mounted reference discard also clears mountedContraption/running/reassemble on inspected CBC/Fixed/Compact classes to prevent stale-reference disassembly. Neo new hook requires existing permitted owned death/part context. No area entity search. Ordinary disassembly preserved. CBCAT1.0.1fix included native test profile alongside CBC/AT/Neo/Radar/RPL.

Real full gun tests:5actualsteel blocks per Neo rotary/AT twin/heavy, correct compact mount side (horizontalFacing.clockwise), root at bearing/recoil spring; original setBlockAndUpdate alone lacked connections, native assembly errors retained until setting native CannonBehavior neighbor faces. Native mount assembles and adds actual Pitch entity to world; checks type/count==5, death wholeplot/fallback, removed/notdisassembled/refnull, then disassemble again and wait5ticks no returned blocks. All11mounted tests pass (8prior+3actualfull). Earlier glass-type tests insufficient user actual compact case, now explicitly covered.

Capture budgets defaults131072blocks/16384BEs/32MiBtemplate/128MiBworld. New server config arenaCaptureBlocks(1024..1048576),arenaCaptureBlockEntities(256..131072),arenaTemplateBytes(1..128MiB),arenaWorldTemplateBytes(1..512MiB). Capture/load use same live server budgets;parts32/entities256/scan4M unchanged. Actual64000iron/core cube exports and saves/loads exact64000 via nativePhoto and record, not just constant assertion. Config can still reject very large addon data; no promise constant latency for huge captures/finalization.

58units,43combined native mounted11/respawn+Arena+lifecycle+cleanup including64000blockrecord,Toolgun/Copycats deletion path,40trueNoPhoto,290fullDefault pass. Manual64. Build/export verified. Defaultminimum5s/wheel±1 unchanged. Originalworld untouched, oldfallenremnants not retroactively area-cleaned. Private replay/modJARs excludedsource.

Export exports/development-0.1.212/sablelife-0.1.212.jar1348907bytes SHA256da473ee10d8b07f4043c6437cbfc410a247bf5e516ecbfadcc634ab4fcc3edca. Private GitHubmain761100a6f22fd6db04a4ef5b71086bbc81ced584,557blob hashes verified;receipt backups/github212-receipt.json. Stable0.1.200/localDrive backup preserved, .git untouched. Await giant63994 fresh record/respawn and Neo/AT gun death playtest.

--- Previous handoff ---

# Latest 0.1.211 development validation

Two user reports: giant capture now errors unloaded; death leaves addon cannon/radar entities. Capture fixed: validate actually allocated plot chunks from getLoadedChunks, not whole bbox rectangle. Sable hasChunk false on unallocated holes but getChunkFuture returns native EmptyLevelChunk. Native exporter supports holes. Sparse actual244-block2MiB fixture bbox expanded with definite absent chunk; exports/saves/loads allblocks/data and hole remains absent, allocated chunks count unchanged. Real giant still needs user fresh capture.

Death mounted cleanup: StructureRemovalApi scans only this exact body's controllers; permitted requires CLEANING>0,persisted owner,LifeLifecycle.isDying(owner or part), and exact Sable containing-body match in plot path. Fallback same guard. Direct controller references only; no nearby entities. CBC recognition walks superclass; bearing mixin clears movedContraption/running/assembleNextTick and discards without reconstructing blocks. Ordinary disassembly retained. Radar Bearing is real registered MechanicalBearing subclass. Radar SmartMount class is real CBC subclass but no registry refs found in this JAR; cannot assert screenshot actualCBC cause is this class. Test explicitly constructs real Smart class over CBC block/type for inheritance boundary, not pretend registered factory. CBC5.11.7/AT0.1.4c heavy/twin contraption classes plus minimal glass cargo used; no full actual user's gun payload fixture. User existing orphan leftovers not auto area-cleaned.

Auto-review rejected initial unfiltered controller scan because insufficient ownership proof and irreversible independent structure deletion risk; command never executed. Safer death-only owner/part proof accepted. Explain this separate final paragraph. New access interface initially in mixin package failed runtime class loading; moved to compat. Missing RPL startup failure not counted as tests. Same-tick loop creating/removing/reusing plots showed second-heavy cannon assertion failure; separated per-payload batches with native attach identity assertions all pass. Exact underlying same-tick cause remains needs verification; do not claim proven native bug. User arena min5s remains.

Verification:58units;39 combined(31respawn/Arena/lifecycle/cleanup+8real mounted);39 on Toolgun0.3.6/Copycats branch;40 real-no-Photo;290full default; manual63 and build passed. Strictly check required tests strings, Gradle startup failure can exit0. Optional mountedCleanupTests .dev/mounted-cleanup-mods CBC/Radar/AT/RPL profile. All addon JARs/private world fixtures excluded upload/export. User world untouched.

Export exports/development-0.1.211/sablelife-0.1.211.jar 1347775bytes SHA2567fc90548e288c02aa7335b5d47b9d3de7db9234deed661d38cdc3f72d50d59fa. Private GitHub main22da2c8f3260fbe922ce6f74d3c18966b55da230,556blobs verified;receipt backups/github211-receipt.json. Stable0.1.200 preserved/.git untouched. Await original giant capture/respawn and cannon/radar death cleanup playtest.

--- Previous handoff ---

# Latest 0.1.210 development validation

User giant22parts rejected scan533288>262144; requests native Photomancy placement, cooldown wheel±1s/minimum5s. Clarified existing placement already native Photomancy incremental API; screenshot rejection is our pre-export scanner. Default scan4,194,304; server config arenaCaptureScanVoxels min262144/max16,777,216; blocks32768/BE4096/parts32/template8MiB/world32MiB unchanged. Capture is synchronous, larger budgets may increase one-time record pause. Giant actual model still needs fresh capture/playtest; no guarantee other limits not reached.

Cooldown min100ticks everywhere ModuleFields, server record/death and savedDelay load; wheel exactly±1 displayed second, max3600/default60 unchanged. Saved Remaining stays elapsed rather than reset on reload. Existing native tests updated to5sec and two-generation timeouts360. Sparse capture fixture82cubed/551368cells > screenshot533288,244blocks plus2MiB chest data exports and saves/loads. New minimum test checks module edit clamp, death job100ticks, savedDelay migration and no respawn by30ticks.31 native respawn/Arena/lifecycle/cleanup tests,58units,40 true-no-Photomancy tests and build/manual62 passed. Native placement/tick guard/joints unchanged from209; no additional private-template replay needed this change.

Export exports/development-0.1.210/sablelife-0.1.210.jar 1343906bytes SHA2569715eecd9b7727de1d95b5dad47e4fa32ffaca7b286e6df7c557db8484f92c3a. Private GitHub main3682fc72927b2c0753a25b3621039ec5d6e8b9c8,550blob hashes verified,receipt backups/github210-receipt.json. Stable0.1.200 preserved, no user world/client modifications, local.git untouched. Await giant fresh record/respawn feedback.

--- Previous handoff ---

# Latest 0.1.209 development validation

User respawn scattering and failed floating text. Read-only real world saved job:9parts template, Synaxis joints, FAILED native commit; no error log because original bridge discarded message. Isolated real private template with same Synaxis1.5.0/AeroWorks1.5.0/Copycats3.0.9/Design-n-Decor2.2b reproduced Create RedstoneLinkBlockEntity.read null link.newPosition. SmartBE initializes before deferred full NBT, consumes firstNbtRead, does not create deferred link behaviour. Fix suspends vanilla bound BlockEntity ticker only for newly allocated partial Arena body UUIDs until full NBT/constraint commit, then releases. BeforeTick Pre hydrates pending/failed newBodies guard on saved-data load; cancellation releases it. No partial world-wide scan on ordinary tick. Blueprint native physics locks unchanged. Actual template native commit passes; after20ticks chain==9, activation succeeds, ownership==9, subsequent death cleans all9.

Failure message + native full exception stack now retained. Cancel searches nearest job within64, removes owned partial bodies and all same-record displays even stale UUID; orphan display without job also cancellable, unrelated text retained. Failure text includes command. Core-less user can stand nearby /sablelife_respawn cancel (OP2). Old FAILED job is not auto-resumed; cancel then re-record original complete machine. User world/client never modified/launched.

Tests:58units,30 native respawn/Arena/lifecycle/cleanup including real furnace tick pause/unrelated furnace runs/resume, duplicate orphan display cancel, multipart second death;40 actually absent Photomancy;1 real addon private template replay as above. Build/manual61 passed. Private replay fixture.dev/arena-replay/template.nbt and addon JARs are not source/upload/export. Source optional arenaAddonTests profile accepts private fixture path; must supply local fixture to rerun replay. Copycats physics tag warning and Design-n-Decor own recipe/loot warnings existed; original template completion/connection/cleanup assertions passed.

Export exports/development-0.1.209/sablelife-0.1.209.jar (1343487bytes) SHA2564c9ea123d0c2117b669d5fc039c7abeb9dc48a08cff78f4be430af11051091de. Private GitHub main5171a901fdb278cc2dc102fb7db48f5572ca4f40;549blobs verified. Receipt backups/github209-receipt.json. Stable0.1.200/localDrive backups retained, .git untouched. Await user original-world retest.

--- Previous handoff ---

# Latest 0.1.208 development validation

User: respawned life death only removes core part. Confirmed missing coverage: activateAssembled captures only current connected chain once; native blueprint connection actor may not be ready/restored. Fix Build.captureOwnership after successful activation uses actual placed UUIDs plus blueprint block positions, checks actual containing body identity and refuses other creature ownership. All reconstructed nonair blocks registered persistently. Death oldBodies includes registered bodyIds, not just connected chain. No world AABB takeover and no change to independent rope structures.

New real-native two-body blueprint fixture without connection actor: both parts become owned by fresh creature, ownership SavedData reload then second death; both oldBodies in wait set, both clean, unrelated gold-body survives. 28 respawn/Arena/lifecycle/cleanup GameTests, 58 units, 40 actually no-Photomancy GameTests, final build and manual60 passed. Real third-party joint restoration remains unverified; this fixes cleanup ownership, not necessarily physical joint connectivity. Existing orphan leftovers from older births are not retroactively identified; user must clear old remnants before testing fresh complete-template respawn. User world/client untouched.

Export exports/development-0.1.208/sablelife-0.1.208.jar (1336834 bytes), SHA256 1328d0f8c576fbd3228481d8752328df4f111ac0e0f526acfa56fd45cb2e7a2e. Private GitHub main4fc109b99d0655bd0c8fc168d080ab826f3fe7ca, 543blobs verified. Receipt backups/github208-receipt.json. Stable0.1.200 remains; .git untouched. Await user fresh multipart respawn/death playtest.

--- Previous handoff ---

# Latest 0.1.207 development validation

User 20-part structure hit generic record limit. Exact triggering metric unknown from screenshot. Raised bounding scan32768→262144, block16384→32768, BE1024→4096, template1→8MiB; retain32parts/256entities/32records. Add totalworld32MiB template guard, matching save/load budgets, structured precise limit messages with actual/max and server log. Rope other side independent: no expansion of cleanup or connected-chain capture. Cross-boundary rope hook behavior still unverified.

58 unit tests, 27 native respawn/Arena/lifecycle/cleanup tests, 40 actual-no-Photomancy tests passed; build/manual59 passed. Sparse100-block continuous L frame bounding34cubed with2MiB chest persistent data records and loads; saved byte budget checks pass. First disconnected fixture NPE repaired by using legitimate continuous frame; no production checks bypassed.

Export exports/development-0.1.207/sablelife-0.1.207.jar (1335699 bytes); SHA256 b4d4db66fb1520af01bb4025d6325459a8594f63136c4130565b9eb814c45efc. Private GitHub main b1c138ec10a0ae59b8cfa48def6b4316792a05ef, 542blob hash verified; receipt backups/github207-receipt.json. Local .git untouched. Stable0.1.200 and backups remain. Await user original20part capture/rebirth/rope feedback; no client or user-world operation performed.

--- Previous handoff ---

# Latest handoff — 0.1.206 development validation

Completed optional Arena respawn via Photomancy schematic API 1.0.1 (not hard dependency). Server-only creative OP structure capture, persistent countdown/build job, center TextDisplay progress, unloaded-region pause, wait for death cleanup, native incremental reconstruction, full-health automatic activation, UUID/module mark migration and trusted capture inventory restoration. Record/clear controls, cancellation command and manual revision58 added. Four Iron Spells physical paths: Creeper mortar, Divine Smite, Evoker Fangs, falling Ice Block. No synthetic body entity targets or designated-target spell adaptation.

Final build successful. 57 unit tests; 290 default GameTests; 96 combined native Iron/respawn/Arena/audit/defense checks including 51 Iron tests; 25 respawn/lifecycle checks; 40 with actual Photomancy runtime removed; 16 isolated GUI scale/density combinations. Mid-build actual SavedData reload tested; full fresh-process server restart not separately tested. Complex user-pack joints/ropes/addon cannon parts and center countdown appearance need manual playtest. Native blueprint finalization may do bulk work despite per-step placement budgets; documented.

Export: exports/development-0.1.206/sablelife-0.1.206.jar, 1333183 bytes, SHA256 78be20155b8908fe437a121a6e870223d553e8dab9b53002f0fe3d661a38a831. Manifest/checksums/release notes beside JAR. Private GitHub main 492526eda03dbd68ede8b27626a4e788d6ced3eb, recursive 539 blobs verified. Original local .git untouched. Receipt backups/github206-receipt.json. Stable remains 0.1.200, existing local/Drive backups preserved; no new stable promotion or Drive upload. User world/client not launched or modified. User latest request continue now fulfilled; await playtest feedback.

--- Previous handoff ---

## Latest 0.1.205 — 2026-10-10

User confirmed 0.1.204 siphoning works. Reported Shadow slash hangs and other AoE no damage; examples: Wither skull, Starfall, Arrow volley, Sunbeam, Earthquake. Completed experimental support and exported 0.1.205, stable 0.1.200 unchanged.

ShadowSlash native old Utils.raycastForBlock endpoint fed raw plot location into expandTowards, impulse and particles. IronUtilsWorldRayMixin now normalizes Utils.raycastForBlock and 4-arg moveToRelativeGroundLevel Level.clip BLOCK returns (world point + storage BlockPos); earlier RaycastBuilder and base magic clip fix remain. Deprecated entity ray wrappers delegate to the already fixed builder. Shadow adapter preserves native damage formula/box and uses cast-local shared UUID dedup, handles actual body contact plus native damageBox, leaves ordinary entity/parry/impulse behavior native.

Aoe checkHits explicitly supports FireField, PoisonCloud, EarthquakeAoe and SunbeamEntity. Native checkHits cadence and post-hit radius/duration run once; eligible immune contact still sets native hit (void applyEffect semantics). queryRegion combines native sphere radius and box height with real owned collision shapes, loaded LOS and budgets. HealingAoe/FrostField positive fields do not become damage. Wither/Comet onHit overrides get coordinate and impact-body adapters; Wither direct full plus nearby falloff using scaled contact voxel width, Comet flat area damage. SmallMagicArrow direct profile; ArrowVolleyEntity stays a controller. Sunbeam keeps 15-tick warmup/one burst, not continuous ray DPS. Unsupported other wave/cloud/status/event families remain explicit. No proxy entities.

Tests: 76 combined native Iron/audit/defense, 44 native Iron, 290 default no-Iron, 57 unit, manual revision 57 (45 chapters/32 modules), final build passed. Full native onCast tests cover Starfall comet spawning, volley controller+real arrows, Sunbeam target damage. Source test manual tick needed tickCount advance to match native tickNonPassenger; casting data needed explicit SyncedSpellData initialization. Body/mob mixed hit, circular empty corner, immune post-hit tests passed. Native modpack freeze has no new report; original instance latest report is still old Oct9 cleanup; user needs retest. No user world/client started or edited.

Export exports/development-0.1.205/sablelife-0.1.205.jar, 1297815 bytes, SHA256 5bef7d02f4a7752dcf0246d17904ecce8dc6f9a7affd6e228bfb85222c2d042a. Latest red/purple PNGs from 0.1.204 verified unchanged in jar. Private GitHub main e63e860ecf6999af6d2386f5d2c15066d5d117f0, tree 3dc4500276f1350c1f14b7d86a81ee7e1da11447, 521 blob SHA/modes verified. Source snapshot backups/source-before-0.1.205.zip before edits; prior stable 0.1.200 local/Drive backups unchanged. Original .git not written. See RELEASE_0.1.205.md and upload receipt for details.

## Latest 0.1.204 — 2026-10-10

Iron first contact-policy experiment implemented: 6 native direct profiles (missile, arrow, smallfireball, firebolt, icicle, lightninglance), native blast paths unchanged; direct profiles consume body hits at base onHit TAIL even immune. BloodSlash own current AABB contact and own block hook damage once/consume, native15% actual-damage lifesteal. Fire/Cold/Poison cone hit windows hook base getSubEntityCollisions invocation only while dealDamageActive; original part AABBs union exact SAT real body voxels via BodySphereQuery.queryVolumes, dedup per source/UUID/gameTime weak map. Siphoning onCast nativeRay hit adds contact damage+100% actual life loss healing; normal entities untouched; closer wall/entity remains. FireBreath tick normalizes raw Sable clip point before unchecked shield query, preserving BlockPos. Contact API validates server source/state/damage, damageAt and feedback; caller owns cadence. No proxies, no LivingEntity status/event simulation. Unknown families still unsupported; explicit opt-in APIs retained. Stable200 unchanged, protocol7.

57 units, 65 nativeIron/Arena/audit/defense combo, 290 default no-Iron, final26 nativeIron after15% lifesteal added, manual56/45chapters/32modules validation, final build passed. First2 test failures were fixture split from hole deletion (now sparse initial assembly) and core physics move during second siphon pulse (reaim current world position). Original Aeronautic2 modpack siphoning crash lacks fresh stack, user retest stillpending.

User also supplied new32x32RGBA heartcore.png and redstonecore_model.png, both copied exactly to existing heart_crystal/redstone_crystal PNGs; sixface refs/alpha preserved. Jar zip exact-byte verified: red9df14ff8db082b81948318cccd0ac53a9fa33783e93b2fce0d3e588c2c9a04ae, purpled12ed1c1cba13177f7f2dbd35b242afee53dc7aed1e4b75b0e8c6f9cd78a511c.

Export exports/development-0.1.204/sablelife-0.1.204.jar SHA25610f4c505af736cfe3da7f207391f0a3802aad8da3a96250faf1c221a495e628f,1287243bytes. GitHub maina8826b56703c2176a4bc34a087599aaace4b65da,tree65e72b7f3e3947464ed659828d64bd67fc1f9f38,515blobs SHA/modes verified/private. Backups/source-before-0.1.204.zip fullsrc/scripts/docs snapshot beforechanges; no new Drive stablebackup. Prior stable200 full local/Drive unchanged. Original.git neverwritten. No user client or worldstarted/edited. Further client launch only if userasks CLIENT.

## Latest 0.1.203 — 2026-10-10

Arena preview now adds yellow player range at ModuleArena.corePosition/current warningRange; red fixed arena ring retained. Iron RaycastBuilder normalizes BLOCK location before native entity AABB/query; retains storage BlockPos. Pinned 3.16.3 only, no proxies, no siphoning damage added. 54 targeted GameTests, 15 final native Iron tests, 57 unit and default build passed. Modpack siphoning crash has no new report; underlying remote plot query hazard verified, actual user crash attribution awaits retest. Review of generic damage projectiles is in RELEASE_0.1.203.md: BloodSlash needs own AABB contact/once-per-victim adapter, not base onHit or every tick damage. No new BloodSlash damage implementation this turn.

Export exports/development-0.1.203/sablelife-0.1.203.jar SHA256 febf3b0298467bc80096680be70f68336c68a415654955910248041a5d4fdd86. GitHub main 4db13f0ad39ff18021dab68c92c23eeb32cd9cb9; tree 841578eb7b9618d4ccdb2ca705f8b77f87b8a7f1, 509 blob SHA/modes verified. Stable 0.1.200 unchanged. Backup delta before change backups/source-before-0.1.203.zip, prior full stable local/Drive unchanged.

最新开发 0.1.202：铁魔法 3.16.3 第一批伤害桥接已实现，三种直接弹、火球、火焰箭、Shockwave；无代理，实验 API 与附属登记说明见 docs/COMPATIBILITY_API.md。57 单元/290 默认/103 全组合/44 铁魔法相关/12 原生Iron用例和含Iron+Epic客户端启动UI通过。导出 exports/development-0.1.202/sablelife-0.1.202.jar。源码 638d694b7d7510e5f91732a653ac34e4be040bab。稳定仍 0.1.200，协议7。原用户客户端实例未改。接下来优先周期雷球/法阵、锥形/地面波/射线、最后锁定；状态效果和LivingEntity技能事件仍未兼容，不能宣称全铁魔法或全部附属自动支持。报告 docs/IRON_SPELLS_COMPAT_0.1.202.md。

最新开发 0.1.201：Arena 分组范围 Buff 已完成，每秒刷新、2 秒自然结束，协议 7。57 单元/287 默认/88 组合及 GUI 1–4 检查通过。稳定仍 0.1.200。导出 exports/development-0.1.201/sablelife-0.1.201.jar，源码 0bda152301dca0ee1ca5e68fc153d14a80e02a37。报告 docs/RELEASE_0.1.201.md，含首次 Create 初始化失败需验证记录。用户当前询问镜头/FOV大招演出原理，未授权添加镜头模块。

当前稳定版 0.1.200：工具 Epic 战斗动画修复、近战原生 Guard/Parrying/ImpactGuard 接入、斩击释放音效文案、Boss 原版七色。57 单元/282 默认/81 组合及真实 Epic 客户端检查通过。用户授权通过后立稳定；原场景新动作观感未手动复测。完整本地 9351 文件备份 Sable-Life-0.1.200-stable-20261010-064535.zip；12 云端文件 metadata 读回验证。GitHub c2b91f75775b235500af4f995ea6a78ea4c30be3，私有且194分支保留。见 docs/RELEASE_0.1.200.md、backups/drive-stable-0.1.200.json。CBC 附属炮膛继续暂不改。

最新开发版 0.1.199：充能/保护工具攻击/整身死亡中心与效果半径。57 单元、281 默认、71 组合、客户端预览通过。导出 exports/development-0.1.199，源码 GitHub 5b1bf16df14004e61169fb104e5d2ba9dc9a6dc5。报告 docs/RELEASE_0.1.199.md。稳定仍 0.1.193。CBC 附属炮膛按用户要求暂未改；原场景长按工具、HUD 和外围粒子待用户回测。

# Sable Life 会话交接 — 2026-10-09

最新停点：0.1.198两项任务完成，先读docs/RELEASE_0.1.198.md与docs/EPIC_FIGHT_COMPAT_0.1.197.md。279默认、46合并兼容、57单元通过；用户原整合包需回测，未启动/改动其世界。稳定仍0.1.193。0.1.197独立源码历史与导出保留。

最新：0.1.197 Epic Fight初步适配已验证，先读docs/EPIC_FIGHT_COMPAT_0.1.197.md。用户要求完成Epic后修复生命体消失崩溃，参考Photomancy/工具枪结构删除；崩溃日志目录由用户提供：Desktop/Minecraft/.minecraft/versions/Create Aeronautic 2/crash-reports（取最新）。不得以Epic测试通过推断清除崩溃已修复。

更新：当前源码/最新导出已到0.1.196开发版，先读docs/RELEASE_0.1.196.md。0.1.195记录为历史；最新JAR位于exports/development-0.1.196，协议6，稳定版仍0.1.193。等待用户回测，本轮未启动用户世界。269默认GameTest、125真实武器组合、57单元和独立频率页预览通过。

## 当前停点

0.1.194审计后的修复已完成，当前源码与导出版本为0.1.195开发验证版。已交付结果，等待用户确认/实测；不得自动提升稳定版或继续扩展功能。正式稳定记录仍为0.1.193（docs/STABLE_VERSION.json）。用户现在要求转到下一个会话，避免长历史影响。

工作目录：C:/Users/mingj/Documents/ChatGPT/Create Sable_ Life；PowerShell；Java21。

## 先读这些文件

- docs/AUDIT_FIXES_0.1.195.md：原因、结果、迁移和验证限制。
- docs/STATUS.md、docs/PLAYTEST_CURRENT.md：当前开发说明。
- exports/development-0.1.195/manifest.json：导出哈希及测试汇总。
- backups/github-audit195-upload-receipt.json：远程核验回执。
- 如要还原，先读backups/drive-pre-audit-0.1.194.json及恢复说明，恢复到新目录。

## 备份已完成

稳定0.1.193本地完整ZIP和JAR的校验复核；Drive13文件名称、大小及父目录匹配。稳定JAR SHA256：fa925014a0f105d86b6593dcfba0f4869878e16b67f6b7c0ec720b79127c7af2。

修改前0.1.194备份：backups/Sable-Life-0.1.194-20261009-112937.zip，8,890文件、473,653,647字节；SHA256 a1ffbbada54ca8f4b5edd35371950414517d2beaaeacb4498916882d94d82801。Drive已上传六分卷+清单/校验/恢复说明/脚本并复核元数据；Join脚本文件名已修正并重新上传。

Drive目录：https://drive.google.com/drive/folders/1O28g5nJqjPLvpt6s99kOqeTMi5h7wG7f

## GitHub

私有仓库：https://github.com/Mingjieng/Sable-Life
main提交：dd115ca1228a79f30de2b8baa06aeaedfb1e9b59
树：dafa1876caf0f09452aa7873b690a3d1320605a8
修改前分支：codex/pre-audit-0.1.194，提交52c2c251018173af60494b1ceecb41b5c142dc8f。
本次72个文件变化；远程444个文件SHA/mode全部匹配，本次令牌/私钥/个人路径扫描无命中。未上传缓存、世界、日志、附件、备份或导出JAR。交接文件在该提交后本地创建，尚未同步远程。

本地.git为只读，原工作区有历史未提交修改；不要reset/stage/commit、不要清理其他用户文件。远程更新通过GitHub连接器完成，不是本地Git提交。更新远程时先读main，再expected_sha且force=false。

## 当前导出及验证

JAR：exports/development-0.1.195/sablelife-0.1.195.jar
1,182,616字节，SHA256 cb9ea5c8fff55840d009cf3316a080368583035a0e55c778b4011db03ae87da8。
Minecraft1.21.1，NeoForge最低21.1.235，保留现有必需依赖。

57单元测试（无失败/跳过）；266默认GameTest（含15新增审计测试）；真实TaCZ/CBC依赖下57项组合测试（与默认有重复，不可相加作为独立覆盖数）。16组GUI缩放/密度和核心设置/物品显示/手册预览通过。手册revision51、45章节、32模块、4标签、两种语言结构检查通过。JAR无GameTest类。

证据：audit195-final-verified.log、audit195-weapons-verified.log、audit195-ui.log、build/test-results/test。
早期实验/失败日志保留，不要混淆最终通过结果。

## 主要改变

管理员命令导入/编辑边界；蓝图无玩家身份时命令待确认；SNBT深度/字节/节点限制及请求令牌桶；预设清除无关隐藏组。
LoadedCoreIndex及同tick连接图缓存；owner/part反向索引；精简CompletedDeaths去重历史；有界轮转死亡清理；稀疏无线频道和位置/面信号索引。
共享碰撞预算（不足时位移等待，不当作取消）；每玩家粒子预算；新弹射物配额；健康更新精简、大面板选中详情保留；字段/GUI缓存；声音批量到期处理；区块票据降低重复刷新；两套粒子标记迁移/蓝图动作UUID重映射及退出清理。

默认服务器限制：新无线频道256，粒子8192点/玩家/tick，死亡清理256方块/服务器tick，弹射物8192/维度与2048/生命体，碰撞8388608检查/维度tick。文件world/serverconfig/sablelife-server.toml。旧频道不截断；加载中的旧飞行物不因限额删除。

协议4→5：客户端和服务器一起更新。CompletedDeaths为新格式，回退时还原备份。不得宣称旧版本能无损读取新格式。

## 待验证，不能宣称已解决

真实长时间多人TPS、断电退出奖励原子性、外部框架部分组装失败回滚、激活后接入未归属身体、原始NBT重复UUID策略、未来第三方版本兼容。
Sphere Blast自身身体破坏风险：实验测试无法建立原生射线命中前置条件，已删除无效实验测试并明确记录风险；没有据此改动爆炸行为。
不增加实体代理；不为LOC拆大类；稳定及行为兼容优先。保留已设定滚轮步长、损坏使招式不可用、动作抽选/伤害规则。

## 工作注意

用户不喜欢反复确认；授权内可继续，但当前停点为等待新指示。不要擅自启动用户客户端或碰用户世界；测试使用隔离runs/gametest195-*。
如运行Gradle：JAVA_HOME=C:/Program Files/Java/jdk-21；GRADLE_USER_HOME=<workspace>/.gradle-user-home；.\gradlew.bat。环境通常需要require_escalated审批，按实际动作说明。无正在运行的测试或待完成上传。
不要因为长会话自动重做已完成任务；先读这份记录和最终报告，再根据用户的新要求行动。
