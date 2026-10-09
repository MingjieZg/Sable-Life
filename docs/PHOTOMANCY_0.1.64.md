# Photomancy 蓝图兼容（0.1.64）

## 核查依据与支持范围

核查 Photomancy 官方源码 https://github.com/Rew1nd-dev/sable-schematic-api ，并以官方 1.0.1 JAR 的真实接口编译。其 block-entity mapper 在保存前和加载前提供 NBT 转换，并通过 `BlueprintBlockRef`、`mapBlock`、`placedSubLevel` 提供新旧身体映射。

Photomancy 原生会保存 Heart 方块实体的完整 NBT，因此模块参数可保留。但 Sable Life 的 marks 由旧身体 UUID + plot offset 构成，直接照搬会失效或引用原 Boss。现在注册可选 Heart mapper：保存时转换为蓝图部件编号和部件内位置，放置时还原成新身体 UUID、新 plot offset。方向维持部件局部方向。

运行条件：Photomancy 1.0.1 或具备相同 API 的后续版本；需按 Photomancy 自身要求安装 LDLib2。未安装 Photomancy 时 Sable Life 仍可独立加载，不打包或强制安装它。开发依赖仅 compileOnly，由 setup-mods.ps1 和 lock 恢复并校验 SHA-512。

## 继承与重置

- 保留核心数值、名字、保护开关、复制材质；保留全部模块参数、模块名称、启用状态、药水/粒子、发射物、掉落模板、声音引用、trigger、频道和 wireless 设置。
- 两个范围端点、脚步/导航/免伤/肢体掉落等身体 marks 都重映射到复制体。旧实体模块导入来源也重映射。
- 场地固定中心相对核心保存，复制后随新机构位置和姿态变换，并改为目标维度，不再回旧场地。
- 新核心和模块使用新 UUID，不继承旧 Boss 的战斗/BGM/掉落所有权、死亡状态和 wireless 播放进度。
- 复制体满血、休眠，Heart ACTIVE 状态复位；激活后成为独立 Boss。原 Boss 不被修改。

## 使用

1. 更新 Sable Life 至 0.1.64，并安装上述 Photomancy 及其依赖。
2. 蓝图选择必须覆盖 Heart 和所有被 mark 的身体部件/方块。
3. 重新保存蓝图，再放置复制体；打开 Heart 确认 marks 有效，随后激活。

旧蓝图没有新增的蓝图内部引用，不能可靠复原旧 plot 坐标，需在新版本下重新保存。遗漏、已移除或没有保存的 mark 部件不会继续引用原 Boss，需要重新标记。自定义 OGG 文件是外部客户端资源，不随 NBT 蓝图打包；跨电脑/服务器使用时另行复制 config/sablelife/sounds。

## 验证与待游戏检查

assemble 编译通过，使用官方 1.0.1 编译包并已校验 SHA-512。当前开发客户端没有安装 Photomancy/LDLib2，尚未进行实际蓝图保存/放置测试，没有重启客户端。

待检查：

1. 配好跨身体两端范围、脚底/导航 marks 及无线频道，保存完整蓝图，在不同位置放两份；分别打开 Heart，检查参数、标记和物品模板保留。
2. 分别激活两份复制品，攻击、移除原 Boss；复制品仍独立工作，伤害/掉落/BGM 不串到原 Boss。
3. 场地模块预览中心跟随复制位置，而非旧世界中心。
4. 保存已受伤/死亡的 Boss，复制品应满血休眠；重新加载世界后标记仍有效。
5. 检查生存打印和旋转摆放；确认 Photomancy 的打印策略没有剥离自定义数据，连接约束完成后所有 mark 指向正确部件。
6. 不安装 Photomancy 的环境启动 Sable Life；可选依赖没有导致类加载错误。
