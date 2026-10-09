# 0.1.65 开发客户端

- 已加入官方 Photomancy 1.0.1 和与其发布版一致的 LDLib2 2.2.17-all。下载包 SHA-512 已校验，依赖已锁定。
- Sound module 的 cooldown 最小 2 ticks（0.1 秒），新增模块默认 20 ticks（1 秒），已有模块保存值保留。Trigger Sound 的独立 cooldown 不变。
- runClient 编译及启动成功，日志确认 Heart blueprint mapper 注册，资源及渲染初始化完成。实际 Boss 蓝图保存/复制仍待用户游戏检查。
- 测试时重新保存包含 Heart 及全部 mark 部件的完整 Boss，放置两份，确认属性/marks，激活后检查两份攻击和掉落独立。复制品按设计满血休眠。

开发环境现在实际加载 Photomancy；这不改变 Sable Life 发布包的可选依赖属性。

2026-10-03 用户实测：Photomancy copy/paste 正常，复制后只需重新激活 Heart 即可使用。蓝图复制与继承已获用户验证。
