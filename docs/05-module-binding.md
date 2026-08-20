# 05 · 模块设计：网络联结器与设备绑定

> 统一名称为 `network_linker` / 网络联结器。

## 1. 职责

把第三方模组机器直接登记为某 BD 网络可调度资源。BD 网络熔炉、网络高炉、网络烟熏炉已有原生能力，不进入绑定系统。

## 2. 数据模型

```java
BindingRecord {
  UUID bindingId;
  UUID ownerId;
  int netId;
  ResourceKey<Level> dim;
  BlockPos pos;
  DeviceType deviceType;
  Set<RecipeFamilyId> families;
  ResourceLocation lastBlockId;
  String nickname;
  boolean favorite;
  long boundGameTime;
}
```

持久化：`DeviceBindingSavedData`（服务器级），并维护：

- `byPlayer`
- `byNet`
- `byPos`

## 3. 核心链路

### 3.1 绑定

```text
Right-click a third-party machine with network_linker
  reject minecraft / beyonddimensions / beyond_craftlines blocks
  reject BD NetFurnace / NetBlastFurnace / NetSmoker explicitly
  resolve player's current BD net
  check permission + claim
  resolve JEI catalyst category -> loaded RecipeType family
  require a sided item capability
  create BindingRecord
  sync client binding frame
```

### 3.2 解绑

- 潜行右键已绑定的第三方机器
- 管理界面点解绑
- 方块破坏事件懒清理

### 3.3 附近扫描

```text
from linker UI action (no global order hotkey)
  select connector/tool context
  scan loaded chunks in radius (budgeted across ticks)
  skip already bound / denied / unsupported
  report counts: bound / skippedClaim / unsupported
```

## 4. 自动调度

1. 同一机器一次只分配一个订单。
2. 记录机器中目标产物的可抽取基线，避免回收绑定前已有库存。
3. 按各方向公开的 `IItemHandler` 模拟可插入量，只从 BD 网络抽取机器当前能接收的材料。
4. 分 tick 渐进投料；槽满或网络缺料时保持等待，未插入部分立即退回网络。
5. 机器真实加工后，仅抽取超过基线的目标产物，并在确认 BD 网络有空间后写回。
6. 订单的剩余输入、产物基线和已回收数量持久化，服务器重启后继续。

## 5. 与自动合成的关系

`GraphScheduler` 选设备：

1. 同 netId
2. family 匹配
3. 方块 ID 与绑定时一致
4. 区块已加载且物品能力仍可用
5. 未被其它订单占用

没有可用设备时：该节点失败或等待（可配）。

## 6. UT

1. BD 三种网络炉和非第三方方块拒绝绑定
2. 权限不足拒绝
3. 破坏方块后查询自动剔除
4. 同位置重复绑定覆盖策略
5. 部分投料不复制、不重复扣料
6. 只回收超过绑定任务基线的新产物
7. 机器忙碌时订单排队
