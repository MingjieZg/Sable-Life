# 0.1.284 Sable schematic持续范围伤害引用修复（开发版，2026-10-28）

## 原因与修复

Sable schematic放置时会通过原生BlueprintPlaceSession为每个模块分配新的UUID。此前已转换模块自身ID、Move Set动作和命中计数来源，却遗漏Settings.ImpactFieldId，导致持续范围伤害模板依然存在，攻击模块却持有原生命体的旧模板ID，界面显示“区域已删除，请重新选择”。

HeartBlueprintMapper.beforeLoadBlockEntity现在将ImpactFieldId通过同一次放置的allocateMappedUuid转换，与模板的新ID一致。无论模板位于攻击之前或之后，多攻击共享、多个模板、重复放置，以及放置后再次导出均保持正确的本地引用。适用于Mortar、Flamethrower、Gun、Arrow、Slash、Laser beam、Ground strike和Homing共八类命中后持续范围伤害引用。没有配置引用的攻击保持为空。

旧schematic保存的源模块及引用ID仍可由修复后的放置流程正确转换，无须先重新保存文件。已经由旧版本放置且引用失效的世界生命体，无法仅凭丢失映射后的数据确定原模板，需手动重新选择区域，或从原schematic重新放置。不会按名称或列表顺序猜测恢复。

## 验证

62项持续范围伤害、安全审计及原生蓝图放置/plot复用相关GameTests通过。新增测试走真实导出、解码和原生放置API，覆盖八类攻击、前后顺序的两个模板、多次放置的ID隔离、重新捕获再放置、形状参数保留、空引用、源配置不被修改，并通过实际命中入口验证恢复后的斩击能生成区域。

Jar内容与手册120校验通过。未重复跑完整套件、JUnit、GUI或原整合包人工复现。此改动只修复蓝图引用转换，没有界面变更。

网络协议21，稳定仍0.1.243，移动观察/对照仍0.1.273/0.1.272。未修改原Minecraft实例，未上传GitHub/Drive。
