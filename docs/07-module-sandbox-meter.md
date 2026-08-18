# 07 · 模块设计：沙盒测试维与计量报告

## 1. 职责

把草稿图纸实例化到隔离环境，统计其对外 IO，形成可确认的产线报告。

本模块是与 ModuleWorks「压缩空间试产」最接近的对照点：**参考其“试产验证”直觉，但不采用缩厂方块方案**；本设计固定为测试维 + **原版屏障** + 观察者 + 计量。

## 2. 子模块

| 子模块 | 职责 |
| --- | --- |
| SandboxManager | 维度存在性、槽位分配/回收 |
| PasteEngine | 限速粘贴结构与屏障 |
| SpectatorSession | 玩家进入/退出/状态还原 |
| MeterService | 内部差分 + 跨界流量 |
| StabilityDetector | 稳态窗口建议 |
| ReportBuilder | 生成报告 DTO / 报告单物品 |

## 3. 测试维方案（最终态）

- 维度 ID：`beyond_craftlines:sandbox`
- **单维度多槽位**：按网格分配原点（例如每槽 512 间隔）
- 属性：无生物生成、固定时间、无天气
- 围挡：直接放置原版 `minecraft:barrier`（含缓冲层）；**不新增**自定义屏障方块
- 并发上限与空闲清理可配

## 4. 核心链路：启动测试

```text
startTest(draftId, player)
  draft = library.get(draftId)
  slot = sandbox.allocate(player)
  pasteEngine.paste(draft.structure, slot.origin)   # budgeted
  barrier.boxAround(slot.bounds + padding)
  mark sky nodes sandboxCrossDim=true
  meter.start(sessionId, slot, draft.initialTotals)
  spectator.enter(player, slot.observePos)
  session.state = TESTING
```

## 5. 核心链路：计量

### 5.1 内部快照

每隔 N tick 或结束时扫描：

- 所有 item handler totals
- fluid totals
- energy totals

### 5.2 跨界流量（天穹）

桥接层监听测试维节点与原维同 line 的传输：

```text
onSkyTransfer(session, resource, amount, IN|OUT)
  meter.addExternal(resource, amount, direction)
```

### 5.3 报告推导

```text
CAPEX = draft.blocks + draft.initialContents
Inputs  = external_in  normalized (+ 可选内部净消耗)
Outputs = external_out normalized (+ 可选内部净产出且非 CAPEX)
EnergyNet = energy_in - energy_out
cycleTicks = StabilityDetector.estimate() or window length
warnings = [no_output, leak_suspected, unloaded_node, negative_buffer...]
```

玩家可在报告 GUI 勾选噪声项，确认后进入编译。

## 6. 结束条件

1. 玩家手动停止
2. 超时
3. 稳态：连续 `stableWindowTicks` 外部 IO 速率方差低于阈值且无调度积压

## 7. 观察者退出规则与安全

- 进入强制观察者模式查看试产现场
- **不提供“退出观察”专用按钮**（避免 GUI/模式切换复杂度）
- 若观察者持续处于产线包围盒外 **5 秒**，自动：
  1. 结束观察会话
  2. 还原原游戏模式
  3. 送回进入前坐标（或锚点旁安全点）
- “停止试产”属于测试会话控制，不等于观察者退出按钮
- 禁止把沙盒创造物带出作为编译产出
- 粘贴黑名单再次校验

## 8. UT / 集成测试

1. 槽位分配不重叠
2. 粘贴预算跨 tick 完成
3. 屏障包围正确
4. 内部分差计算
5. 跨界 in/out 记账
6. 稳态检测器
7. 退出还原玩家状态
8. 会话超时回收
9. 无天穹软依赖时降级路径
