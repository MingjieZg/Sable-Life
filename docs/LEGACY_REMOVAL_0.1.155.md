# 旧实体模块完整移除（0.1.155）

用户明确选择完整删除，不保留旧实体模块注册。移除近战、喷火、箭矢、范围脉冲、目标感应、声音、部位标记共7种实体方块/物品/BlockEntity及旧属性菜单、CONFIG_CHEST注册、28份专属blockstates/模型/掉落表、专属翻译和LegacyModuleItem。删除虚拟与实体发射器去重用的ModuleCombat.overrides，以及Sound mix的旧实体编辑分支。

## 保留范围

- 生命核心、红石核心注册和共享HeartBlockEntity存档ID仍为sablelife:heart。
- 27种当前生命核心虚拟模块、9种红石核心模块，以及其参数/声音/粒子/标记/Move set逻辑保留。
- LOOT_CHEST和SableChestScreen保留，服务当前Whole-body loot与虚拟肢体战利品。
- ContactBox、ShotTrajectory、ModulePower、SensorGeometry、LootSelection及声音公共工具保留。
- 旧虚拟数据版本迁移与Move dispatcher兼容仍保留；ModuleDispatch条件函数被当前Move set使用，这次不删除虚拟模块功能。
- 不再兼容7种旧实体模块的世界方块、库存物品或旧蓝图；这与当前核心内虚拟模块存档不同。

## 产物

从1,083,708字节降为975,051字节，减少108,657字节（约108.7KB，10.0%）。干净构建后检查原审计57个旧class/资源条目全部不在JAR中。测试类和第三方JAR仍不打包。

## 验证

- clean build及所有测试源码编译通过。
- 当前专用集125/125通过：注册清理、两个核心全部可用虚拟模块重载、Arena、爆炸、控制、Core UI、减伤、朝向、生命周期、标记、近战、粒子、地震波、红石核心、声音。
- 存档比较遵循既有VirtualModule.load的schema/default迁移规则，同时检查模块数量、UUID及名称不丢失。
- 独立客户端启动通过，紫色核心/伪装模型正常，TaCZ/CBC/RPL与现有Create/Sable共同加载，未开启玩家世界。
- 原实体模块专项测试被移除；两个虚拟发射器测试改为普通铁块标记。模块容量测试改用当前HeartModules.LIMIT。旧测试共享假玩家与物理并行干扰已隔离，但早期sablelife_tests集仍有15项失败。

## 仍需审查的早期集成测试

部分断言仍调用已更换的Core槽位菜单、旧频道配置或即时喷火伤害；其他失败未完整归因，不能断言全部仅因测试过时，也不能据125项通过宣称全套测试通过。未为凑通过而改变运行逻辑。发布前继续审查。

- virtualmeleeappliesindependentdamageeffectsandstopswhendisabled: Independent attacks/debuff bypass failed
- virtualflameusesordinarymarkedblockandsingledamage: Virtual flame did not apply one damage hit
- virtualtriggerreadsexistingwirelesschannelandturnsoffwithheart: Virtual module did not read original wireless channel 16
- virtualarrowusesnativewirelessscatterandcannotbepickedup: Wireless scatter damage/direction/pickup failed
- virtualflameobeyswallsandburnsunblockedtargets: Open flame did not damage and ignite
- explosiondestroysbodywithoutmaterials: Explosion did not destroy the body block
- realcreatereceiveracceptsmovingbodyheartandstopsondeath: Create receiver did not match Heart's Sable plot frequencies/world range
- ownershipsurvivescoreremovalandserialization: New block inherited stale ownership
- independentmenucopiessamplesandrejectslockedorremoteedits: Frequency sampler consumed or duplicated inventory items
- nameandbossbarpersistandremaincreativeonly: Naming or Boss Bar toggle failed, or consumed the Name Tag
- naturalbreaksplitswithownershipintact: Chest did not naturally split into a new body
- sharedfrequencypreservesotherheartandunloadclearsownoutput: Same frequency maximum failed
- corecontrolspersistandonlybreakingchangesafteractivation: Core menu controls did not change defaults
- dashboardvalidatesrevisionspermissionsandpreserveswireless: Unified core lost modules or existing wireless configuration
- externalreceivermatchescreatelinkandneverrunsownwaveform: External Create transmitter did not gate virtual module

日志：verify-wave155-complete.log、verify-wave155-isolated.log、cleanup-client155.log。
