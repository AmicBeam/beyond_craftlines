# 04 · 模块设计：自动合成链（JEI / BD）

## 0. 入口约束（硬性）

1. Craftlines 的 JEI 额外按钮**仅在 BD 维度网络界面打开时**出现。
2. 在维度网络界面中，对物品使用**鼠标中键**可进入该物品的配方树/下单界面。
3. 不提供快捷键打开下单界面。
4. 不实现 EMI 联动；只学习 EMI 配方树视觉与交互。

## 1. 模块职责

负责：

- 配方索引
- 配方树预览（EMI 视觉风格）
- BD 维度网络界面内的下单执行
- 进度与取消
- 稳态构象图节点嵌套调用入口
- 网络物品“可由构象图产出”的标记数据

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
| JeiBridge | `integrate/jei` | 仅在 BD 维度网络界面打开时的 JEI 入口 |

## 3. 默认可执行族（最终态）

1. `crafting`：原版有序/无序；虚拟执行，不要求世界中有工作台方块。
2. `smelting`：对接 BD `NetFurnace`（可配允许原版熔炉绑定执行）。
3. `blasting`：`NetBlastFurnace`
4. `smoking`：`NetSmoker`
5. `blueprint_blackbox`：稳态构象图
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

## 8. 网络界面标记

`NetworkProductMarkerService`：

- 输入：当前网络可见物品列表 + 玩家可见稳态构象图库
- 输出：可被构象图生产的 item key 集合
- 客户端在 BD 网络格子上画角标；中键命中后打开对应目标的配方树

## 9. 版本适配点

- JEI API 在 1.20.1 / 1.21+ 包名差异 → versions 源集
- BD GUI 类名/菜单类型差异 → `NetAccessService` 版本实现
- 配方 `RecipeHolder` vs 旧 `Recipe` → recipe adapter
- 中键事件需挂在 BD 网络界面物品区，而不是全局键位
