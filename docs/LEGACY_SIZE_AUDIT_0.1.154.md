# 旧实体模块与产物大小审计（0.1.154）

本次仅检查，不修改运行代码或注册项。

## 结论

旧方块未完全移除。SableLife仍注册7种实体模块及其BlockEntity/Item：近战、喷火、箭矢、范围脉冲、目标感应、声音、部位标记。它们退出创造栏，LegacyModuleItem禁止放置；方块newBlockEntity/getTicker/交互入口停用（无法按旧方块方式运行），但BE内的执行/编辑代码和旧设置菜单仍编译进JAR。

保留理由：0.1.32起明确用于旧存档兼容。直接删注册项会使旧世界/库存/蓝图出现缺失ID，旧实体模块的设置也可能丢失。

## 实测大小

当前JAR：1,083,708字节，约1.08 MB。

| 明确识别的遗留内容 | ZIP压缩内容字节 | 条目数 |
|---|---:|---:|
| 旧Block/BlockEntity/7个设置菜单/LegacyModuleItem及内嵌类 | 92,405 | 29 |
| 旧blockstates、block/item模型、掉落表 | 1,727 | 28 |
| 合计 | 94,132 | 57 |

约占JAR 8.7%。统计为已明确分类的ZIP压缩内容，不含ZIP目录开销、共用注册类中的字节或混在语言文件中的旧标签；不是保证可删除的全部字节数。

产物没有打包开发GameTest类或第三方模组JAR。build/libs历史版本只占本地磁盘，不会嵌入当前JAR。最大单项为红石核心生成纹理，压缩179,089字节（约179KB），比这些旧内容总和还大。

## 清理边界

1. 建议保留7组旧注册ID、禁用的方块/物品及最小NBT兼容壳；不再保留旧执行逻辑与旧属性菜单。
2. SoundTimelineMenu仍有旧实体声音编辑分支，删旧SoundModuleBlockEntity前需要拆除此分支。旧方块专项GameTest亦需改为旧ID/保存兼容及禁用行为验证。
3. CONFIG_CHEST只被旧属性菜单使用，可随旧菜单清理；LOOT_CHEST及SableChestScreen仍用于现有HeartLootMenu/VirtualLootMenu，不可一起删除。
4. ContactBox、ShotTrajectory、ModulePower、SensorGeometry、LootSelection、SoundLibrary、SoundTimeline等仍被虚拟模块或生命核心使用，不能因为所在目录有旧模块就删除整个attack/sense/sound/part目录。
5. 下架的Move dispatcher仍有独立ModuleDispatch运行与旧数据兼容；ModuleDispatch.targetPresent是当前Move set的条件检查，不能整类删除。可拆条件辅助后再决定旧调度兼容政策。
6. 若决定彻底不兼容旧实体模块，则还需处理旧存档/库存/蓝图缺失ID；这与单纯删除无用菜单不同，应明确版本迁移策略。

明细：LEGACY_SIZE_AUDIT_0.1.154.json。
