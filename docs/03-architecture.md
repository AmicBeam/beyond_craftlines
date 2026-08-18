# 03 · 总体架构

## 1. 逻辑视图

```text
Client
  ├─ JEI Plugin
  ├─ Screens (Anchor/Drive/Duplicator/Plan/Report)
  └─ HUD (progress / sandbox)

Network Packets
  ├─ C2S: preview/execute/cancel/bind/test/compile/copy
  └─ S2C: plan summary/progress/report/sandbox state

Server Runtime
  ├─ NetAccessService          # BD 网络解析与权限
  ├─ RecipeIndex               # 可执行配方族索引
  ├─ PlanBuilder               # 工作树生成
  ├─ GraphScheduler            # 并行调度
  ├─ MaterialLedger            # 预留/扣费/退款
  ├─ DeviceBindingRegistry     # 绑定
  ├─ DeviceExecutorHub         # 执行器路由
  ├─ BlueprintLibrary          # 草稿/编译库
  ├─ CaptureService            # 框选捕获
  ├─ SandboxManager            # 测试维槽位
  ├─ MeterService              # 计量
  ├─ CompileService            # 报告→编译
  ├─ DriveRuntime              # 合成链驱动核
  └─ CopyService               # 图纸复制

Integrations
  ├─ BeyondDimensions bridge
  ├─ SkyLogistics bridge
  └─ RecipeFamily providers（数据包/附属）
```

## 2. 包结构（common）

```text
com.beyondcraftlines
  api/
  platform/
  netaccess/
  recipe/
    family/
    index/
  plan/
  ledger/
  binding/
  exec/
    crafting/
    furnace/
    blueprint/
  blueprint/
    capture/
    model/
    compile/
    runtime/
    copy/
  sandbox/
  meter/
  integrate/
    bd/
    skylogistics/
    jei/
  client/          # 仅无 loader 强依赖的客户端逻辑；否则放 versions
  network/
  registry/        # 逻辑 ID 常量，不直接 DeferredRegister
  config/
  util/
```

## 3. 核心对象

### 3.1 ResourceRef

统一资源引用：

- `ITEM`（物品 + 匹配模式：忽略耐久 / 精确 NBT）
- `FLUID`
- `ENERGY`
- 可选扩展：`CHEMICAL` / `MANA` / `SOURCE`

与 BD 的 key 体系对齐，转换器放 `integrate/bd`。

### 3.2 CraftPlan / PlanNode

- 目标、次数、去向
- 节点：配方、设备族、输入输出、来源类型
- 边：产物依赖
- 诊断：缺料、环、超时

### 3.3 BindingRecord

- owner、netId、GlobalPos、deviceType、families、meta

### 3.4 BlueprintRecord

状态：`DRAFT / TESTING / REPORT_READY / COMPILED / FAILED / ABORTED`

字段分组：

- 结构引用与哈希
- CAPEX
- OPEX in/out
- cycleTicks
- meter summary

## 4. 核心链路（端到端）

### 4.1 JEI 一键合成

```text
JEI button
  -> C2S PreviewRequest(recipeId, count, netHint)
  -> PlanBuilder.build(snapshot)
  -> S2C PlanSummary
  -> player confirm Execute
  -> MaterialLedger.reserve
  -> GraphScheduler.run
      -> DeviceExecutorHub.execute(node)
      -> outputs insert UnifiedStorage
  -> commit/refund
  -> Progress HUD
```

### 4.2 产线编译

```text
Anchor SAVE
  -> CaptureService.snapshot
  -> BlueprintLibrary.createDraft
  -> item unstable_schematic (ref id)

Start TEST
  -> SandboxManager.allocate
  -> paste structure + barrier
  -> MeterService.start
  -> spectator enter

Stop/Stabilize
  -> MeterService.finalize
  -> Report GUI
  -> player confirm
  -> CompileService.toCompiled
  -> item stabilized_schematic
```

### 4.3 图纸驱动

```text
Drive tick/redstone
  -> load compiled
  -> check inputs in net
  -> reserve/extract
  -> wait cycleTicks
  -> insert outputs
  -> stats++
```

## 5. 线程与预算

- 规划：可异步（服务器工作线程）但执行提交必须回主线程
- 主线程预算：
  - 每 tick 最大执行节点数
  - 每 tick 最大粘贴方块数
  - 每玩家活跃合成链数
- 沙盒：空闲超时卸载；硬顶并发测试会话数

## 6. 客户端配方树

玩家可见的计划预览必须采用 **EMI BoM 风格配方树**（见 `15-ui-recipe-tree-emi-style.md`，仅风格参考）。
服务端仍输出 `CraftPlan` DTO；客户端负责 TreeVolume 布局与交互，不在本地重算权威计划。
入口仅来自 BD 维度网络界面中的 JEI 按钮或鼠标中键，不使用快捷键，不做 EMI 联动。

## 7. 错误模型

统一 `CraftErrorCode` / `BlueprintErrorCode`：

- `MISSING_MATERIALS`
- `NO_NETWORK_PERMISSION`
- `NO_DEVICE`
- `PLAN_TIMEOUT`
- `CYCLE_DETECTED`
- `SANDBOX_FULL`
- `DANGEROUS_BLOCK`
- `METER_UNSTABLE`
- `OUTPUT_REJECTED`

客户端只显示翻译键，不直接丢堆栈。
