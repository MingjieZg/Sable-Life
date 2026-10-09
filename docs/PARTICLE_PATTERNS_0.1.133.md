# Particle 样式与标记（0.1.133）

稳定版 0.1.132 的标签和备份保留，本次是新增开发版本。

## 标记

Area 使用同一结构上两点长方体，沿用旧默认行为。Point、Line、Ring、Sphere 共用单点标记，以点击面外侧0.55格为起点。Area与单点分别保存，切换可找回标记；未标记模式禁用Preview。进入Mark后潜行对空气右键只清除当前模式，Esc/潜行对方块右键仍只取消。

## 参数

| 参数 | 用途 |
|---|---|
| Particle pattern | Area、Point、Line、Ring、Sphere |
| Start distance | 0–128格，沿Direction整体偏移，不改变范围大小 |
| Direction | 结构局部方向，随机器旋转；Marked face使用当前模式第一个标记面 |
| Pattern radius | Ring/Sphere半径，0.05–32格 |
| Line length | Line长度，0.1–128格 |
| Particle distribution | Random/Evenly spaced，控制位置分布；球内随机按体积均匀分布 |
| Sphere fill | Inside sphere/Sphere surface，球内或球面 |
| Line emission | Display along line/Spray from origin，整线显示或起点喷射 |
| Spray speed | 0.01–4格/tick，喷射独有 |

直线喷射寿命上限为长度÷速度（最多60秒），用户指定更短粒子寿命优先。保留原生粒子的阻力、上浮等行为，因此实际可见距离可能短于设定长度。

Interval、Particles per type、Motion trail、Only while moving、Drift speed和每种粒子的Life/Size延续原功能。密度是每种粒子的基础数，拖尾可增加数量，单次总生成数最多256。Preview显示轮廓和选定粒子，仅发送给预览玩家。界面隐藏当前样式无用参数，但保留其配置。

## 实例：球内随机粒子云

1. 新增Particle，样式选Sphere，Mark origin选择结构上的一个面。
2. 半径设3，填充选Inside sphere，分布选Random。
3. Selected particles添加portal或electric_spark，设置每种粒子的寿命和大小。
4. 密度设8–16，间隔0.1–0.2秒，Drift设0让云较集中。
5. Start distance设2，球心移到标记面前方2格；Preview查看位置与效果。
6. 激活生命体，以所选Trigger控制实际播放。

专项自动测试覆盖独立标记、存档、清除、旧配置兼容、球内分布、形状边界、六面方向、直线端点、喷射寿命、旋转与整体偏移。实机清单见PLAYTEST_CURRENT.md。
