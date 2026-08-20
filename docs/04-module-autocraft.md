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
- 第三方机器直连后的自动投料、真实加工等待与产物回收

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
5. 由网络联结器直接绑定的第三方 `RecipeType`；自动投料，等待真实加工并回收产物
6. 扩展族：数据包/`RecipeFamilyProvider`（默认仍受总闸配置）

## 4. 核心链路：预览

```text
PreviewRequest
  net = NetAccessService.resolve(player, hint)
  snapshot = StorageSnapshot.from(net) + playerInv
  root = RecipeIndex.findCandidates(target)
  plan = PlanBuilder.build(root, manufacturingCount, snapshot.withoutTargetStock(), limits)
  validate no cycle / depth / timeout
  return PlanSummaryDTO
```

`PlanBuilder` 伪代码：

```java
PlanNode build(Goal goal, Snapshot snap, Context ctx) {
  if (snap.covers(goal)) return networkNode(goal);
  if (ctx.depth > maxDepth) return missing(goal);
  List<RecipeRecord> recipes = index.find(goal);
  // 默认优先：network > native/bound recipes > missing
  RecipeRecord chosen = select(recipes, ctx.preferences);
  if (chosen == null) return missing(goal);
  List<PlanNode> deps = new ArrayList<>();
  for (IngredientNeed need : expand(chosen, goal.count)) {
    deps.add(build(need.toGoal(), snap.minusReserved(), ctx.child()));
  }
  return craftNode(chosen, deps);
}
```

根目标的数量始终表示“本次额外制造多少”，并在界面中另行显示服务端查询到的网络现有数量。现有目标成品不参与抵扣；递归展开后的原料和中间产物仍优先使用网络库存。例如网络已有 5 个目标物、输入 10，下单完成后网络应有 15 个（不考虑玩家同时取用）。

配方批次不可按比例拆分。若请求数量小于单次配方产出，`crafts = ceil(requested / outputPerCraft)`，仍执行一次完整配方并保留全部实际产物。例如祭坛配方固定消耗 8 份供品并产出 8 个目标物时，请求 1 也必须交付完整一批原料并实际产出 8；多出的 7 个是合法批次余量，不能销毁。

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

`CraftingVirtualExecutor`：从网络中按 `Ingredient.test` 选择保留完整 Data Components 的实际输入，每次构造一次 `CraftingInput`，依次调用原配方的 `matches`、`assemble` 与 `getRemainingItems`，并以事务方式把动态产物和返还物写回网络。定制 serializer、升级时复制组件、耐久工具及容器返还物不走“物品 ID 扣料后直接变产物”的捷径。
`NetFurnaceExecutor`：通过 BD 已公开的网络方块事件维护已加载炉索引，向对应网络炉输入存储投料，等待产物回网；不创建联结器绑定记录，也不要求 BD 提供订单 API。
`BoundMachineExecutor`：通过第三方机器公开的带方向物品能力渐进投料、轮询并回收本订单新增产物。

当前订单事务由 Craftlines 自己按 BD 网络严格 FIFO 调度；提取不足时暂停，机器拒收的余量立即退回网络。BD 只提供存储和机器能力，不负责附属订单预留。

普通合成按实际合成次数限速。每到一个允许的执行时点，原子执行一次原配方模拟；同一节点的超大批次保留为 `long` 剩余次数并逐次推进。节点间隔由服务端配置 `crafting.virtualCraftingNodeIntervalTicks` 控制，默认 `20` tick。这种执行方式确保上一轮返还的受损工具或带组件容器会成为下一轮的真实输入。

订单数量、配方次数、材料需求和网络计数均使用正 `long`。界面与网络包允许超过 `Integer.MAX_VALUE`，上限为 `Long.MAX_VALUE`；规划中的加法、乘法与向上取整采用饱和算术，超出存储计量范围时钳制为 `Long.MAX_VALUE`，不得回绕为负数或抛出算术溢出。

下单界面的阻挡模式沿用 AE2 Pattern Provider 语义，默认关闭。开启后，每次只向目标机器推送一次配方所需的输入，必须等该批产物完成并回收后才推送下一批；若目标机器事先存在当前配方的任一输入材料，首批同样等待。关闭后按机器可接收容量投放当前步骤的全部批次。检查范围是目标机器，不是整个 BD 网络。

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
3. `LedgerReserveRefundTest`
4. `SchedulerParallelismTest`：无依赖节点并行
5. `VariantPinningTest`：同槽不混变体
6. `PermissionDeniedTest`
7. `PlanCacheInvalidationTest`：库存变化后旧计划失效

## 8. 版本适配点

- JEI API 在 1.20.1 / 1.21+ 包名差异 → versions 源集
- BD GUI 类名/菜单类型差异 → `NetAccessService` 版本实现
- 配方 `RecipeHolder` vs 旧 `Recipe` → recipe adapter
- 中键事件需挂在 BD 网络界面物品区，而不是全局键位
