# 外部枪炮测试环境

本地客户端路径：runs/client/mods。当前开发版本0.1.173。

| 模组 | 安装版本 | 发布来源 |
|---|---|---|
| TaCZ NeoForge社区移植 | 1.1.8-hotfix-r7 | https://github.com/MUKSC/TACZ-1.21.1/releases/tag/neoforge-1.1.8-hotfix-r7 |
| Create Big Cannons | 5.11.7（MC1.21.1） | https://modrinth.com/mod/create-big-cannons |
| Ritchie’s Projectile Library | 2.1.2（NeoForge1.21.1） | https://modrinth.com/mod/rpl |

现有环境：Minecraft1.21.1、NeoForge21.1.251、Create6.0.10、Sable2.0.5、Aeronautics1.3.2。
CBC声明Create范围[6.0.7,6.1.0)，需要RPL>=2.1.2。TaCZ使用1.21.1社区移植，发布包包含默认枪包，首次启动导出到当前运行目录的tacz文件夹。

下载锁定于test-mod-dependencies.lock.json，CBC/RPL比对Modrinth SHA512，TaCZ比对GitHub发布资产SHA256。恢复命令：powershell -ExecutionPolicy Bypass -File scripts/setup-test-mods.ps1。仅将三份可选测试JAR安装到本地客户端，不给Sable Life产物增加必需依赖。

独立客户端启动验证通过（crossmod-smoke152.log），实际载入TaCZ/CBC/RPL并完成资源与模型加载，使用runs/redstone-preview，未打开玩家世界；实际枪弹/炮弹破坏测试见PLAYTEST_CURRENT.md，尚未宣称伤害兼容。


## 0.1.173 方块伤害桥接

不创建实体代理。TaCZ 1.1.8-hotfix-r7在原生AmmoHitBlockEvent通过后读取getDamage实际弹伤；其ProjectileExplosion读取原power/radius。CBC 5.11.7在大炮／机炮calculateBlockPenetration读取damage字段，冲击爆炸独立计入。弹体以生命体ID去重，爆炸每生命体一次，实际体素距离与遮挡检查。核心护甲／自适应减伤正常。可选Mixin无需武器模组也能运行。

verify-wave173-checked.log：实际模组65项相关测试全通过，含TaCZ原生命中、原生爆炸与CBC原生穿透回调。verify-wave173-regression.log：不带这些可选模组166项现代模块回归通过。

覆盖不等于所有武器／魔法自动兼容：不发ProjectileImpact、不暴露伤害或使用专属碎片接口的弹体仍需适配。当前未验证CBC碎片burst专用命中。最终实机射击与移动主体命中由用户验收。
