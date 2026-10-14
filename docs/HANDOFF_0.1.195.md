# 当前稳定版 0.1.243（2026-10-14）

用户明确授权稳定化、备份和上传；悬停242及两个健康模块已确认功能完整，生命值调整量滚轮±5已重打包。354默认/90控制、16组合界面、手册89通过，最终数值修改不另跑回归，JUnit未重跑、完整包深层测试未完成。协议13，客户端服务器一起更新。旧218稳定备份保留，原实例/世界与本地.git不修改。当前备份和上传正在执行，完成回执见backups。

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
