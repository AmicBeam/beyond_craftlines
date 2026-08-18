# 10 · 数据模型与网络协议

## 1. 持久化

| SavedData | 内容 |
| --- | --- |
| `DeviceBindingSavedData` | 绑定记录索引 |
| `BlueprintLibrarySavedData` | 草稿/测试中/报告/编译图纸元数据 |
| `SandboxAllocatorSavedData` | 槽位占用、回收队列 |
| `AutocraftSessionSavedData`（可选） | 重启后可恢复的执行链摘要 |

大体量结构体：

- 默认写入 `world/data/beyond_craftlines/structures/<uuid>.bin`（或分段 NBT）
- 物品只存 UUID + hash + 名称摘要

## 2. 关键 schema

### 2.1 ResourceAmount

```json
{
  "type": "item|fluid|energy|...",
  "id": "minecraft:iron_ingot",
  "amount": 64,
  "match": "strict_nbt|ignore_damage|component_patch?",
  "data": {}
}
```

### 2.2 BlueprintDraft

- id, owner, name, createdAt
- bounds, hash, structureRef
- initialTotals[]
- skyNodeSnapshots[]
- status

### 2.3 BlueprintCompiled

- draft fields +
- capex[]
- inputs[]
- outputs[]
- energyNet
- cycleTicks
- reportId
- schemaVersion

## 3. 网络包（逻辑名）

### C2S

- `PreviewPlanC2S`
- `ExecutePlanC2S`
- `CancelAutocraftC2S`
- `BindDeviceC2S` / `UnbindDeviceC2S`
- `StartTestC2S` / `StopTestC2S`
- `ConfirmCompileC2S`
- `DriveUpdateC2S`
- `CopyBlueprintC2S`

### S2C

- `PlanSummaryS2C`（可分页）
- `AutocraftProgressS2C`
- `BindingListS2C`
- `TestSessionS2C`
- `ReportS2C`
- `ToastErrorS2C`

## 4. 同步策略

1. 计划预览：服务端计算，客户端只渲染 DTO。
2. 大报告：分页或按资源类型拆包。
3. 结构本体：不进常规包，只传 ID。
4. 所有写操作服务端权威校验权限。

## 5. 兼容与版本

- `schemaVersion` 升级时提供迁移器
- 跨 MC 版本世界存档不承诺自动迁移结构二进制；至少保证拒绝损坏数据而不是崩服
