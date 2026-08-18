# 06 · 模块设计：框选与草稿捕获

## 1. 职责

把世界中一段区域变成 `DRAFT` 图纸，不直接编译。

## 2. 锚点方块行为

模式：

- `CORNER`：设对角
- `SIZE`：设相对尺寸
- `SAVE`：捕获
- `TEST`：对已有草稿启动测试
- `DATA`：查看元数据/报告入口

显示：边界粒子框、体积、超限警告。

## 3. 捕获内容

`StructureSnapshot`：

1. 相对坐标方块调色板 + 状态
2. BlockEntity NBT（过滤私有 UUID/监听器等）
3. 初始库存 totals（item/fluid/energy）
4. Sky node 配置快照（软依赖）
5. bounds / origin / hash

### 3.1 黑名单（最终默认）

- 命令方块、结构方块、jigsaw
- 传送门传送门框
- 玩家头颅等可滥用实体方块（可配）
- 超大 inventoy BE 可改为只存 totals + 关键槽位

## 4. 存储策略

- 服务器 `BlueprintLibrarySavedData` 存完整结构
- 物品 `unstable_schematic` 仅存 `blueprintId` + 摘要
- 超大结构分片写入（文件或分段 NBT）

## 5. 核心链路

```text
Anchor SAVE
  validate volume <= max
  validate no dangerous blocks
  snapshot = CaptureService.capture(world, box)
  id = library.putDraft(snapshot, owner, name)
  give unstable_schematic(id)
```

## 6. UT

1. 角点换算 bounds
2. 体积超限
3. 黑名单拦截
4. hash 稳定性（同结构同 hash）
5. BE NBT 过滤敏感字段
6. 物品只含引用不含大体量结构
