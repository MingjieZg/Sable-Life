# 0.1.197 Epic Fight 初步伤害适配

开发验证版；稳定记录仍0.1.193。当前适配完成后继续排查用户报告的生命体清除崩溃，不在这版混入未验证的清除逻辑更改。

## 已实现

只在官方Epic Fight21.17.3.1（Minecraft1.21.1 NeoForge）启用专用Mixin；不存在或其他版本不应用未验证注入。Epic Fight保持可选运行依赖，编译开发需要其API JAR。

读取原生单/多OBB动画采样，以世界坐标凸体与已加载核心附近实际归属方块的碰撞形状做相交检测，包含旋转身体的变换和视线遮挡。没有添加世界代理实体，连临时伤害目标实体也不创建；不以整个结构的大AABB当作实际命中。

原生每段清空命中列表时清空生命体UUID去重；同段碰到多个方块或在多个tick继续接触只计算一次。普通生物先经过Epic Fight原有路径，生命体使用剩余命中次数，二者共享本段上限。主手原生基础攻击伤害、通用附魔计算与阶段DAMAGE_MODIFIER进入现有damageAt，保留部位修正、自适应减伤、受伤触发及死亡流程。Epic战斗模式不再同时通过普通左键方块伤害重复扣血。

碰撞盒快照最多128，最多64生命体候选命中；单候选最多32,768方块访问，并消费现有世界碰撞预算。预算耗尽则停止该帧查询，不能保证极端负载下每次攻击都命中。未作真实多人压力测试。

## 本轮支持范围

主手OBB攻击阶段的普通挥砍/连招伤害。实测自动用例使用原生剑、斧、三叉戟动画。副手/双持、锁定/处决/眩晕/击飞、目标依赖额外伤害（如缺血百分比）、实体专用事件及技能命中充能暂未全面适配；不是对所有技能和第三方Epic Fight附加模组的兼容承诺。SlashBlade和Iron's Spells尚未开始。

## 验证

最终57项单元测试通过。未安装Epic Fight的272项完整GameTest通过，确认可选依赖不影响原有启动和功能。安装真实Epic Fight的33项原生/审计/防御组合测试通过（与默认回归有重复），其中4项原生测试验证剑/斧/三叉戟、阶段去重与重置、2倍阶段伤害、禁止普通方块伤害叠加、没有产生代理实体，以及普通生物和生命体共用上限。另有真实身体空隙/墙体和旋转凸体几何边界检查。

关键日志：verify197-no-epic-final.log、verify197-epic-verified.log，build/test-results/test。

测试用场景原先高度12受模板围墙遮挡，已移到开阔高度40；保持遮挡检查，没有通过放宽判定让测试通过。原生方法使用直接调用桥，避免专用服务器反射枚举客户端方法；可选API测试与发现入口隔离，避免未安装Epic Fight时测试类加载失败。早期失败日志保留，未计作通过。

## 使用与开发依赖

两端安装官方Epic Fight21.17.3.1并使用0.1.197 Sable Life开发包；切换Epic战斗模式，用主手武器测试。此次不改变协议6。未改用户客户端实例或世界。

官方版本：https://modrinth.com/mod/epic-fight/version/8HHhJt6i
开发API/运行包：https://cdn.modrinth.com/data/vu3NZ5Ma/versions/8HHhJt6i/epic-fight-21.17.3.1-mc1.21.1-neoforge.jar
开发路径：.dev/epicfight/epic-fight-21.17.3.1-mc1.21.1-neoforge.jar（不打入本MOD、不上传第三方JAR或素材）。SHA512：d39c1d4a2198e7f2080cdc98de890d26d658a02b44c8399a301c7c0005d9991ff97cc94881823a9daa8952f3b89eb3d9197c2f83cf0fbba9c7992c77921b2122。

测试：gradlew runGameTestServer -PepicFightTests=true -PgameTestNamespaces=sablelife_epicfight_tests,sablelife_audit_tests,sablelife_defense_tests -PgameTestDirectory=runs/gametest-epic
保留0.1.196导出与源码检查点backups/source-before-0.1.197.zip；回退时先保留世界备份。
