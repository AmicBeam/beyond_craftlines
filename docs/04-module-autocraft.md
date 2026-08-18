# 04 · 模块设计：自动合成链（JEI / BD）

## 1. 模块职责

负责：

- 配方索引
- 工作树预览
- 一键执行
- 进度与取消
- 图纸节点嵌套调用入口

不负责：结构捕获、沙盒、驱动器 GUI（只调用其黑盒执行器）。

## 2. 子模块

| 子模块 | 类建议 | 职责 |
| --- | --- | --- |
| RecipeFamily | `recipe/family/*` | 定义可执行族与匹配规则 |
| RecipeIndex | `recipe/index/RecipeIndex` | 扫描/缓存配方，支持失效 |
| PlanBuilder | `plan/PlanBuilder` | 生成 CraftPlan |
| PlanOptimizer | `plan/PlanOptimizer` | 剪枝、合并重复需求、防环 |
| GraphScheduler | `plan/GraphScheduler` | 运行时调度 |
| MaterialLedger | `ledger/MaterialLedger` | 预留/抽取/退款 |
| AutocraftController | `plan/AutocraftController` | 玩家会话生命周期 |
| JeiBridge / EmiBridge | `integrate/jei|emi` | UI 入口 |

## 3. 默认可执行族（最终态）

1. `crafting`：原版有序/无序；虚拟执行，不要求世界中有工作台方块。
2. `smelting`：对接 BD `NetFurnace`（可配允许原版熔炉绑定执行）。
3. `blasting`：`NetBlastFurnace`
4. `smoking`：`NetSmoker`
5. `blueprint_blackbox`：已编译图纸
6. 扩展族：数据包/`RecipeFamilyProvider`（默认仍受总闸配置）

## 4. 核心链路：预览

```text
PreviewRequest
  net = NetAccessService.resolve(player, hint)
  snapshot = StorageSnapshot.from(net) + playerInv + knownBlueprints
  root = RecipeIndex.findCandidates(target)
  plan = PlanBuilder.build(root, count, snapshot, limits)
  validate no cycle / depth / timeout
  return PlanSummaryDTO
```

`PlanBuilder` 伪代码：

```java
PlanNode build(Goal goal, Snapshot snap, Context ctx) {
  if (snap.covers(goal)) return networkNode(goal);
  if (ctx.depth > maxDepth) return missing(goal);
  Optional<CompiledBlueprint> bp = blueprintIndex.findProducer(goal);
  List<RecipeRecord> recipes = index.find(goal);
  // 默认优先：network > native recipes > blueprint > missing
  RecipeRecord chosen = select(recipes, bp, ctx.preferences);
  if (chosen == null) return missing(goal);
  List<PlanNode> deps = new ArrayList<>();
  for (IngredientNeed need : expand(chosen, goal.count)) {
    deps.add(build(need.toGoal(), snap.minusReserved(), ctx.child()));
  }
  return craftNode(chosen, deps);
}
```

## 5. 核心链路：执行

```text
ExecuteRequest(planId or rebuildHash)
  rebuild/validate plan against fresh snapshot
  ledger.reserveAll(plan.totalExtracts)
  scheduler.submit(plan)
  each ready node:
    executor = hub.route(node.family)
    prep = executor.prepare(bindings, inputs)
    if prep.rejected -> refund & fail node
    tick until complete
    insert outputs (and remainders) via ledger
  on cancel/timeout -> best-effort refund
```

### 5.1 MaterialLedger 语义

来源优先级：

1. BD `UnifiedStorage`
2. 玩家背包（可配关闭）
3. （可选）已绑定共振式扩展库存 —— 本模组默认不做 RS

操作：

- `reserve(spec)`
- `extract(token)`
- `insert(stack)`
- `refund(token)`

必须记录 provenance，避免“模拟插入也算任务完成”之类问题。

### 5.2 DeviceExecutor 契约

```java
interface DeviceExecutor {
  RecipeFamily family();
  boolean supports(RecipeRecord recipe, BindingView binding);
  PrepareResult prepare(ExecContext ctx);
  TickResult tick(ExecContext ctx);
  void abort(ExecContext ctx, AbortReason reason);
}
```

`CraftingVirtualExecutor`：直接在服务端用配方匹配合成，写入网络。  
`NetFurnaceExecutor`：向绑定网络熔炉投放输入与燃料，轮询输出槽/进度。

## 6. UI 细节（最终态，EMI 风格）

配方树界面以 EMI `BoMScreen` 为主要参考，不再以“左树右表卡片”为主布局。详见 `15-ui-recipe-tree-emi-style.md`。

- 中央：可拖拽/缩放的层级节点画布
- 顶部：目标、批次 `xN`、模式/帮助
- 底部：原材料成本条与缺口
- 节点：折叠/展开、替代配方解析、状态着色
- 执行区：去向、重新规划、执行、取消
- 侧附信息（可折叠）：设备占用、警告列表

进度：

- HUD：目标、百分比、当前节点、设备状态
- 界面：可取消、可隐藏 HUD

## 7. 需要的 UT

1. `PlanBuilderTest`：简单链、多输出、标签原料、替代配方
2. `CycleDetectionTest`
3. `BlueprintNestingTest`：图纸嵌套与防环
4. `LedgerReserveRefundTest`
5. `SchedulerParallelismTest`：无依赖节点并行
6. `VariantPinningTest`：同槽不混变体
7. `PermissionDeniedTest`
8. `PlanCacheInvalidationTest`：库存变化后旧计划失效

## 8. 版本适配点

- JEI API 在 1.20.1 / 1.21+ 包名差异 → versions 源集
- BD GUI 类名/菜单类型差异 → `NetAccessService` 版本实现
- 配方 `RecipeHolder` vs 旧 `Recipe` → recipe adapter
