# Iron’s Spells 伤害接口审查

日期：2026-10-07。Sable Life：0.1.174。本次只审查兼容性，不修改伤害逻辑或客户端模组。

结论：当前通用方块伤害桥接不能完整接收 Iron’s Spells 的法术伤害。名字含 Projectile、Arrow 或 Explosion 不代表走原版对应接口。不能将偶尔扣血视为全部适配。

## 审查依据

- 官方 NeoForge 1.21.1 发布包：`irons_spellbooks-1.21.1-3.16.3.jar`，Modrinth version `slKLosTb`。仅下载到 `.dev/iron-spells-review`，未加入客户端。
- SHA512：`fd782f98c6c59b193c4832f33775291d2a7e639e1e23dd47510bfb494d99d182bec1253566b9394f96c733a0d0108be34bd729614b82dc29e55e096fdeb96f5b`，与发布元数据一致。
- 官方源码分支1.21固定审查提交：`e4056af90302d37eb1739f5ff05020b020e6e252`。基础弹体、范围、锥形、火球及冰霜射线关键路径另用发布JAR的javap字节码核对。
- 未进行装入实际游戏后的法术实测；不宣称运行兼容。发布JAR声明GeckoLib、Player Animator、Curios、Iron’s Lib必需依赖。

## 各伤害路径

| 类别 | 实际调用 | 当前覆盖 |
|---|---|---|
| 魔法飞弹、魔法箭、火焰箭、小火球等AbstractMagicProjectile子类 | `handleHitDetection`只对ENTITY命中发送ProjectileImpactEvent；BLOCK命中直接onHit | 有getDamage可读，但缺少方块事件入口，不能自动接收 |
| 火球MagicFireball | onHit查询实体范围并以damage×距离衰减施加法术伤害；SPELL_GREIFING开时另外建立原版Explosion | 法术范围伤害没覆盖；原版爆炸桥接可能扣血，但走TNT公式且依赖破坏配置，不能当成正确法术伤害 |
| WitherSkull等专属爆炸 | 实体范围伤害与视觉爆炸分开，WitherSkull的原版爆炸强度为0 | 不能依靠Explosion事件拿到实际法术范围伤害 |
| AoeEntity及Sunbeam等 | checkHits查LivingEntity，调用子类applyEffect，使用reapplicationDelay | 方块主体不在实体查询中，未覆盖；不能用一次命中去重模拟持续伤害 |
| AbstractConeProjectile及吐息／锥形 | 通过ConePart包围盒获取实体，再调用onHitEntity | 未覆盖；需保留锥形几何、遮挡与原命中节奏 |
| RayOfFrost等瞬时射线 | RaycastBuilder后只对ENTITY分支applyDamage，BLOCK分支发特效 | 未覆盖；射线碰到生命体方块仍被当作普通方块 |

共同的`DamageSources.applyDamage`以已有Entity为目标。仅在它旁边监听伤害事件不能解决方块主体未被选中的问题。没有实体代理时，必须在几何命中/选取阶段将真实方块生命体接入。

## 后续适配要求

1. 普通魔法弹体在真实BLOCK命中入口转接，读取原法术伤害，排除纯控制／视觉弹体；尊重移除、穿透及重复命中语义。
2. 爆炸法术单独用原法术范围／衰减接入。其附带原版地形爆炸需明确去重，避免额外TNT伤害或破坏开关改变法术伤害。
3. 持续范围和锥形法术接入实际体素，不以整个空包围盒代替；按原reapplicationDelay重置命中窗口，保留圆形／高度／遮挡条件。
4. 瞬时射线在BLOCK结果分支补转接，取原等级和施法者倍率计算的伤害。
5. 仍不创建实体代理。所有伤害最终经核心damageAt，保持装甲、自适应减伤、Hurt、死亡逻辑与红石核心无血量规则。

实测应包含静止/移动主体、多个肢体、主体空隙、隔墙、护甲、连续法术及法术破坏配置开关；以上为适配建议，尚未实施。

## 官方来源

- [基础魔法弹体](https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/entity/spells/AbstractMagicProjectile.java)
- [范围法术基类](https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/entity/spells/AoeEntity.java)
- [火球](https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/entity/spells/fireball/MagicFireball.java)
- [冰霜射线](https://github.com/iron431/irons-spells-n-spellbooks/blob/e4056af90302d37eb1739f5ff05020b020e6e252/src/main/java/io/redspace/ironsspellbooks/spells/ice/RayOfFrostSpell.java)
- [官方发布包](https://modrinth.com/mod/irons-spells-n-spellbooks/version/slKLosTb)
