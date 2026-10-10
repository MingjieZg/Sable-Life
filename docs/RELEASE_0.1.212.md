# 0.1.212 — Neo 紧凑炮台及巨构记录预算

截图实际方块63994，超过旧32768。默认块数131072、方块实体16384、模板32MiB、世界总模板128MiB；均由服务器配置arenaCaptureBlocks、arenaCaptureBlockEntities、arenaTemplateBytes、arenaWorldTemplateBytes控制。保存加载和捕获使用相同现行数据预算。32部件、256实体、扫描默认4194304及最小重生5秒/滚轮±1秒不变。模板依然只存服务端；大型记录保存/加载及原生蓝图最终提交可能更耗时，没有恒定耗时保证。

残留炮体的重要缺口：CBC Neo Warfare0.0.6v CompactCannonMountBlockEntity直接继承Create KineticBlockEntity，而非CBC炮台，旧识别完全漏掉。该控制器也可挂载Advanced Technology0.1.4c炮体。新增已核对的紧凑炮台边界及disassemble钩子；仍要求死亡清理上下文和登记owner/part死亡任务。只处理控制器直接引用，无附近实体搜索。炮体discard后清空mountedContraption、running、reassemble，阻止之后再拆回方块。CBC两个已核对基类同样清空这些字段；普通拆卸保留。

新测试使用真正COMPACT_MOUNT和钢制炮膛5段（breech、bearing或recoil spring、barrel等），按原生紧凑炮台侧面装配点放置，调用原生CannonBehavior建立邻接连接，再由炮台assemble产生真实世界PitchOrientedContraptionEntity。检查真实Neo旋转炮、AT双管及重型类及完整方块数；死亡后实体消失、挂载引用为空，再次disassemble和后续5tick都无方块重建。覆盖整plot与逐块fallback，不再以玻璃最小载荷代替新增实物验证。已安装的CBCAT修复模组也纳入此隔离profile。

新增实际40×40×40、64000方块原生Photomancy导出/记录及保存加载检查；对应截图块数大于原上限。数据总量边界测试同步新预算，仍检查超额拒绝。手册64。开发0.1.212，稳定0.1.200；用户原世界不操作，旧残件不会靠范围搜索自动删除。

验证：58单元、43组合（含11真实挂载＋64000块模板，与Toolgun/Copycats分支）、无Photomancy40项、默认完整290項GameTest、手册64及构建通过。完整炮体测试起初装配点错误及缺少连接而失败；核对原生侧面位置并建立原生CannonBehavior连接后保留完整5段/实际实体/后续不恢复断言，不跳过校验。用户原结构仍需实测。
