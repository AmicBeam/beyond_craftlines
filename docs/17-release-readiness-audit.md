# 17 · 1.21.1 发布就绪审计（2026-08-20）

## 结论

当前代码是**可构建的功能原型**，不是设计文档所定义的完整可交付模组。它可以注册内容、打开基础订单界面、持久化订单、向真实 BD/第三方机器投料、捕获/粘贴结构并运行基本黑盒执行，但 Sky Logistics 精确计量、服务端权威配方树以及多数内容 GUI 尚未完成。

在本次审计中已立即修复：沙盒完成后每 tick 重贴、稀疏槽位碰撞、容器返还物被吞、反射失败错误放行、伪外部机器执行、缺少独立创意标签、原版占位贴图，以及沙盒维度误触发实验性世界生命周期。

## 主体完成度

| 主体 | 状态 | 已有 | 缺口 |
| --- | --- | --- | --- |
| BD 界面下单 | 部分 | JEI 配方页上下文按钮、从当前菜单存储解析真实网络、订单菜单、目标预选、搜索、数量、状态、取消 | 无网络物品中键、无构象产物角标 |
| 配方树 | 原型 | 有限深度文字缩进预览、服务器重新规划 | 客户端预览自行扫描配方且与服务端计划可能不同；无服务端 DTO、成本/缺口、替代配方、折叠、拖拽、缩放、图布局、配方余留物执行 |
| BD 设备 | 已实现核心 | 三种 BD 网络炉无需绑定；按网络与炉型发现已加载机器、空闲预检、真实投料、等待加工和产物回网 | 无进度条与 GameTest；依赖目标区块保持加载 |
| 外部设备 | 部分 | 第三方机器直接绑定、公开 RecipeType 探测、带方向物品能力自动投料、真实加工等待、产物回收、单机器互斥 | 无管理 GUI、附近扫描、流体/化学品/能量专用资源适配器 |
| 构象捕获 | 部分 | 范围、体积/危险方块校验、结构与 BE NBT、哈希、草稿物品 | BE NBT 未按模组清洗，可能复制库存、所有者、网络 ID 或机器唯一 ID；CAPEX 只统计方块物品，不含初始内容 |
| 沙盒 | 部分 | 稳定的运行时 void 维度、分批粘贴、屏障、观察者、出界退出、状态恢复 | 无超时/并发配置、崩服恢复审计、测试控制 GUI |
| SL 联动 | 未完成 | 能发现 Sky Dimension Interface 及其 BD 网络 ID | 没有临时跨维授权；没有传输事件，计量只是整个 BD 网络前后差值，会混入其他玩家/机器流量 |
| 报告/编译 | 部分 | 报告物品、所有权与哈希校验、确认后发稳定图纸 | 无报告 GUI、噪声筛选、警告/稳态检测；能量产出不建模 |
| 常驻/按单 | 基础 | 构象执行器定时循环、命令队列、复制台 CAPEX；图纸按产品要求不进入配方树 | 无执行器/复制台 GUI、红石门控、内部输出缓冲、升级并行度 |
| 内容与美术 | 部分 | 方块物品、配方、掉落、挖掘标签、双语、独立创意标签、专用像素纹理；新增合成链供给器及可复现 SVG 母版 | 方块无朝向/工作状态模型；GUI 仍为程序绘制平面，固定 470px 宽，小窗口会裁切；无节点/按钮/进度素材、音效、粒子 |
| 测试 | 不足 | 36 项纯 Java 测试（含槽位恢复与外部产物基线等待测试） | 无规划/账本/绑定集成测试，无 GameTest、无 BD/SL 联机冒烟、无 GUI 尺寸/交互测试 |
| 版本 | 单版本 | 1.21.1 NeoForge 可构建 | 文档声称的 1.20.1 Forge 与 26.1.2 NeoForge 源集不存在 |

## 阻止正式发布的问题

### C-2：SL 计量不是产线边界计量

当前 `TrialNetworkMeasurement` 比较整个 BD 网络的起止库存。测试窗口内任意无关存取都会被写入图纸 OPEX，并永久变成黑盒输入/输出。

发布条件：按 SL transfer event 的 `from/to dimension + position + face + line + resource + amount` 记账，或由 SL 提供带时间窗的线路统计。

### C-3：客户端展示的不是服务器权威计划

GUI 在客户端重新扫描 recipes，固定选择每种 Ingredient 的第一个物品；提交时只发送产物 ID 和数量，服务端再独立选一次。玩家看到的分支、缺料与最终执行可能不同。

发布条件：新增 `PreviewPlanC2S/PlanSummaryS2C`，客户端只渲染含 recipe ID、选定变体、库存覆盖、缺口和节点状态的 DTO；执行携带 plan hash，并在库存变化后重建/确认。

### C-4：结构捕获会原样复制方块实体数据

`saveWithFullMetadata` 的数据除坐标外全部保留并在沙盒 `loadWithComponents`。通用机器库存、拥有者、安全列表、网络身份、频率或全局 UUID 都可能被克隆。CAPEX 却只统计方块本身。

发布条件：建立 `BlockEntitySnapshotAdapter` 白名单；分离“配置 NBT”和“初始资源”，对未知 BE 默认去库存/身份或拒绝捕获；初始资源进入 CAPEX。

### C-5：配方执行模型不完整

计划按 item ID 聚合，无法保持数据组件；不调用真实 `Recipe.assemble/getRemainingItems`，也没有催化剂、副产物、概率与多资源类型。当前已安全排除带组件和 crafting remainder 的配方，但因此不能称为通用配方树。

发布条件：用 BD `IStackKey`/组件补丁保存精确变体，RecipeFamily 自己负责展开、余留物和执行，不能由通用 Ingredient 列表推断所有配方。

## 重要质量问题

1. GUI 固定 `470×224`，无窄屏重排；树最多显示 7 行，既无节点连线也无拖拽缩放。JEI 入口现已使用官方配方侧按钮 API，不再向 BD 屏幕右上角注入绝对坐标按钮。
2. `visibleRecipes()` 在搜索输入和递归节点中反复全量扫描/排序，较大整合包会卡 UI；需要 reload-aware 缓存。
3. 规划只有深度 48，没有节点数/时间预算；分支配方可能造成主线程长停顿。
4. 完成/取消订单永久保留在 SavedData，无 TTL/数量上限；长期世界会持续膨胀。
5. 订单取消只取消最新活动任务，UI 不能选择具体订单；错误原因没有完整本地化。
6. 锚点、联结器、报告、执行器和复制台都缺少设计中的菜单；多数操作依赖命令或聊天消息。
7. 沙盒已改为运行时注入的空 flat 世界，并从原版 LevelStem 生命周期检查中仅排除本模组键；其他自定义维度仍可正常标记实验性。
8. 捕获/粘贴错误以及 SavedData 解码多处静默吞异常，服务器管理员无法诊断坏档或不兼容数据。
9. 没有配置系统，设计文档中的预算、并发、外部执行总闸和 UI 选项均不可配置。
10. 缺少 schema migration；字段升级只能靠默认值或丢弃记录。

## Beyond Dimensions 反向支持请求

核心订单不需要 BD 反向支持。Craftlines 使用现有公开存储、菜单存储所属网络、网络方块事件和网络炉存储完成集成；订单事务由附属按网络 FIFO、模拟、提交和余量退款处理。

### BD-P1：第三方终端动作 SPI

允许模组向 BD 终端注册物品槽中键动作、角标和 tooltip，并提供当前槽位的 `IStackKey`。JEI 配方侧按钮已不再依赖 BD 屏幕注入，但中键与库存标记仍需要该扩展点。

### BD-P1：带来源的存储变更事件

现有 storage delta 订阅缺少 actor/cause/session。建议在变更事件中携带事务或调用来源，便于 Craftlines 排除无关网络流量并做审计。

### BD-P1：公开规范化 ResourceKey codec

Craftlines 需要跨网络、NBT 和 packet 保留 item/fluid/energy 及数据组件的精确语义，避免退化为 registry ID。

## Sky Logistics 反向支持请求

### SL-P0：传输事件/Probe API

建议 AFTER_TRANSFER 事件至少包含：lineId、source/target dimension、node position、face、resource kind/key、实际 amount、gameTime。Craftlines 用 session 包围盒过滤后即可精确生成试产 IO。

### SL-P0：临时沙盒跨维授权

当前跨维路由依赖 dimension upgrade。建议服务端令牌式 API：

```java
AutoCloseable allowTemporaryCrossDimension(UUID sessionId, Set<EndpointRef> endpoints,
                                           ResourceKey<Level> sandbox, long expiresAt);
```

令牌只允许指定端点与维度，结束/崩服恢复时自动撤销，不能把永久升级写回捕获结构。

### SL-P1：只读端点快照

稳定返回 lineId、endpoint direction、资源开关、filters、priority、upgrades、外部网络绑定。当前反射公共 BE 方法可工作，但无法得到完整线路与过滤配置。

### SL-P1：线路时间窗统计

若不提供逐次事件，可提供 line/endpoint 的单调累计计数器，Craftlines 记录起止值；统计应区分方向和资源 key。

### SL-P1：安全导入/克隆节点配置

提供剥离运行时身份与缓存的配置 codec，避免 Craftlines 复制完整 BE NBT 后克隆线路缓存或唯一标识。

## 推荐交付顺序

1. 先完成服务端权威 Plan DTO、精确资源变体和 MaterialLedger。
2. 为已完成的 BD/第三方真实机器执行补 GameTest、进度 UI 与异常恢复测试。
3. 接 SL 临时跨维授权与 transfer event，替换整个网络差分。
4. 做 BE snapshot adapters、CAPEX 初始内容和报告 GUI。
5. 重做 EMI 风格 TreeCanvas 与 BD/JEI 入口。
6. 补齐全部内容 GUI、配置、GameTest 和真实整合包验收后再标记 1.0。
