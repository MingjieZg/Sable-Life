# 0.1.209 — 重建时序与失败提示清除

用户报告重生后散架并留下失败悬浮提示。只读解析实际失败任务：9部件模板、Synaxis关节侧数据，原生进度FAILED。旧桥接只返回status，丢掉Photomancy失败原因。隔离副本加载同版Synaxis1.5.0、AeroWorks1.5.0、Copycats3.0.9及Design-n-Decor2.2b后重放，获得真实错误：RedstoneLinkBlockEntity.read访问null link.newPosition，发生在IncrementalPlacer.loadBlockEntity的完整NBT提交阶段。

Create SmartBlockEntity.initialize会消耗firstNbtRead，而红石链接延迟behaviour创建依赖首次read。分批放置后方块实体先tick，最后统一加载NBT时link尚未创建，导致提交中断，关节恢复不完成。普通单part/铁方块回归此前未覆盖此路径。

新增仅针对Arena新分配身体UUID的tick防护。分批步骤间暂停这些身体的方块实体tick，提交完整NBT/关节后释放，其他结构及普通蓝图不受影响；世界保存恢复在ServerTick.Pre重新挂起未完成及失败任务。常态无保护身体时只有空集合查询，不遍历世界实体；取消时释放防护。物理锁仍由原生增量建造负责。改变的是NBT提交前时序，不删除或重写第三方关节。

Photomancy失败信息现在保留，并记录原始异常堆栈，仍在失败后停止，不无限重试。取消任务除了UUID查找，还按任务标记移除所有已加载的同任务悬浮提示；没有记录但有孤立提示时，命令清除64格内最近任务的提示副本。核心消失后可靠近执行 /sablelife_respawn cancel，管理员权限2。未加载的孤立提示在实体重载时仍受原有校验清除。只处理Sable Life重生标记，不清理其他模组TextDisplay。失败提示直接展示命令。

原世界只读，失败模板留在本地.dev隔离目录，未上传GitHub，也不导入模组包。可选arenaAddonTests测试profile加载本地附属JAR和私有模板。重放修正后完整原生提交通过，等待20tick确认连接链包含全部9part，再激活、登记归属、死亡，所有实际身体均清除。不是所有第三方机器的兼容保证。

新增普通炉子真实tick隔离/恢复及无关身体照常运行回归、孤立重复提示清除且保留无关TextDisplay回归。既有重生保存恢复/多part死亡清除继续测试。手册61，开发版0.1.209，稳定仍0.1.200。
