# 08 · 模块设计：编译、驱动、复制、JEI 图纸源

## 1. 编译

```text
confirmReport(reportId, selectedIn, selectedOut, cycleTicks)
  validate session owner/permission
  compiled = CompileService.from(report, selection)
  library.storeCompiled(compiled)
  give/update stabilized_schematic item
  state = COMPILED
```

`CompiledBlueprint` 必含：

- structureRef + hash
- capex ResourceList
- inputs ResourceList
- outputs ResourceList
- energyNet
- cycleTicks
- version / schemaVersion

## 2. 构象执行器（Schematic Executor）

### 2.1 行为

- 绑定 BD 网络
- 放入 1 张稳态构象图
- 模式：
  - `TIMED`：按 cycleTicks
  - `GATED`：红石脉冲一轮
  - `INSTANT`：创意/调试瞬发（默认仅权限或配置开启）
- 并行度：基础 1，可用升级提高

### 2.2 核心链路

```text
tryRun()
  if busy or disabled return
  if !net.canExtractAll(inputs * parallelism) return wait
  tokens = ledger.reserveAndExtract(inputs * parallelism)
  schedule finishAt = now + cycleTicks
  onFinish:
    inserted = net.insert(outputs * parallelism)
    if incomplete:
      buffer leftovers / pause / refund policy (config)
    else commit
```

输出策略（最终可配，默认）：

1. 优先插入 BD 网络
2. 驱动器内部缓冲槽
3. 缓冲满则暂停下一轮，不继续扣输入

## 3. 构象复制台

```text
copy(compiled)
  cost = compiled.capex
  if net.extract(cost):
     output one additional stabilized_schematic (same id or clone id)
```

原件不消耗。可配置是否允许跨玩家网络复制（默认仅有权限网络）。

## 4. JEI 图纸源

`BlueprintRecipeFamily`：

- 查询：输出匹配 goal 的稳态构象图（玩家可见库：自己的 / 网络共享库）
- 规划：把图纸 inputs 作为子需求展开
- 执行：走 `BlueprintBlackboxExecutor`（可直接扣 IO，不必真粘贴结构）

防环：

- DFS 着色检测
- `maxBlueprintNesting`

## 5. UT

1. 编译字段完整性
2. 驱动器 TIMED/GATED 行为
3. 输出缓冲满暂停
4. 并行倍增扣费
5. 复制台 CAPEX 扣费
6. JEI/Plan 图纸节点展开
7. 图纸互相依赖环检测
8. hash 不匹配拒绝执行（结构被篡改）
