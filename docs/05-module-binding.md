# 05 · 模块设计：时空联结器与设备绑定

> 旧称“维度工具”已废弃，统一为 `spacetime_linker` / 时空联结器。

## 1. 职责

把“世界上的方块设备”登记为某 BD 网络可调度资源。

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
Shift-right-click block with spacetime_linker
  resolve net from tool/player primary net
  check permission + claim
  detect DeviceType via DeviceProbe
  create BindingRecord
  sync to client binding list
```

### 3.2 解绑

- 再潜行右键同一方块
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

## 4. DeviceProbe

```java
interface DeviceProbe {
  Optional<DeviceType> probe(Level level, BlockPos pos, BlockState state, BlockEntity be);
}
```

内置：

- BD NetFurnace / Blast / Smoker
- 通用 `EXTERNAL_GUI_ONLY`
- （扩展）其它模组执行器注册时升级为 `EXTERNAL_EXECUTABLE`

## 5. 与自动合成的关系

`GraphScheduler` 选设备：

1. 同 netId
2. family 匹配
3. 已加载区块
4. 未忙碌
5. 负载均衡（最少队列）

没有可用设备时：该节点失败或等待（可配）。

## 6. UT

1. 绑定/解绑幂等
2. 权限不足拒绝
3. 破坏方块后查询自动剔除
4. 同位置重复绑定覆盖策略
5. 扫描预算不超时（分 tick）
6. 设备选择负载均衡
