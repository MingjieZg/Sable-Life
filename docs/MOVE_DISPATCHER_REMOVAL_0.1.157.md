# Move set独立化与dispatcher移除

MoveRules独立管理招式UUID、名称、启用、条件和节奏；MoveChoice管理纯权重选择；ModuleMoveSet管理优先级、冷却/休息和直接动作时间线。

完整删除旧ModuleDispatch、DispatchChoice类、dispatch虚拟模块类型、服务器add_move/move_order操作及Dashboard旧招式池页/高级属性按钮。旧频道/强度/启动脉冲参数不再属于Move set条件。需要频道程序时，用Move set动作调度Pulse sequence或Wireless redstone。

现有Move set保存的Config会读取并转为move_rules，保留UUID/名称/启用、权重/优先、条件和节奏，以及Actions模块引用、Start和Duration。已有dispatchWeight等内部保存字段名不改，避免损坏旧招式数据；这些字段由MoveRules管理，不依赖旧dispatcher。旧独立dispatch模块条目在读取时忽略，不继续输出。

验证：构建与55项单测通过，现行专用服务端126/126通过；迁移/条件筛选/复制排序/损坏整招失效/Hurt延时/Priority/冷却及红石核心行为覆盖。真实客户端GUI scale2/3的动作、条件及模块选择页截图检查通过。JAR中旧两类均不存在，当前大小972872字节。

这不表示此前15项早期sablelife_tests集成失败已解决；旧记录仍见LEGACY_REMOVAL_0.1.155.md。
