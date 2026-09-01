# 熔炉配方绑定与下单说明（0.4.0 / 0.5.0）

本文专门说明原版 `smelting`、`blasting`、`smoking` 配方如何由 Beyond: Craftlines
交给 Beyond Dimensions（下称 BD）的网络熔炉、网络高炉和网络烟熏炉执行，并记录
`0.4.0` 与 `0.5.0` 的最终行为。`0.4.0` 以 tag `7e4275c` 为准；`0.5.0` 以
功能审计基线 `afd9522` 及本文档提交为准。截至本文编写时仓库尚未创建 `0.5.0` Git tag。

支持范围同时覆盖 Minecraft 1.20.1 Forge、1.21.1 NeoForge 和 26.1.2 NeoForge。

## 结论速查

| 问题 | 结论 |
| --- | --- |
| BD 网络熔炉、网络高炉、网络烟熏炉是否要用网络连接器绑定？ | 不需要，而且 Craftlines 明确拒绝重复绑定。它们依靠自身 BD `netId` 自动加入运行时索引。 |
| 原版普通熔炉、高炉、烟熏炉能否替代 BD 网络炉？ | 不能作为本文所述原生执行路径的替代品。通用机器绑定流程可能接受具备自动化能力的原版方块，但其记录的是原始 JEI UID，不等于规范化后的 `smelting` / `blasting` / `smoking` 执行族。 |
| 有对应 BD 网络炉时，JEI 配方按钮能否下单？ | 能。客户端上传当前 JEI 配方，服务端确认同一 BD 网络存在已加载、炉型匹配的网络炉后才接受。 |
| 没有对应 BD 网络炉时，JEI 配方按钮能否下单？ | 不能可靠进入该固定炉类配方；按钮可能因整个网络的通用可用性而显示，但服务端会拒绝不具备执行端点的分类。 |
| JEI 槽、列表、书签或 BD 网络槽的悬浮下单入口能否使用炉类配方？ | 可以打开通用目标规划；只有对应执行族可用时炉类配方才会进入候选。目标另有工作台等配方时，仍可能改走其他可用候选。 |
| `0.4.0` 与 `0.5.0` 是否改变了“需要绑定”这一结论？ | 没有。两者都无需且禁止重复绑定 BD 网络炉。 |

## 两类“熔炉”必须区分

### BD 原生网络炉

| BD 方块 | 方块实体族 | Craftlines 执行族 |
| --- | --- | --- |
| 网络熔炉 | `NetFurnaceBlockEntity` | `smelting` |
| 网络高炉 | `NetBlastFurnaceBlockEntity` | `blasting` |
| 网络烟熏炉 | `NetSmokerBlockEntity` | `smoking` |

它们由 `NativeFurnaceRegistry` 根据 BD 自身的网络编号自动发现，不写入
Craftlines 的 `BindingSavedData`。用网络连接器点击这些方块时，
`NetworkLinkerItem` 会返回失败并显示：

> BD 网络熔炉、网络高炉和网络烟熏炉已有原生能力，不允许重复绑定。

这不是“缺少兼容”，而是刻意避免同一台炉既作为 BD 原生设备、又作为普通外部机器
被登记两次。

### 原版普通炉

`minecraft:furnace`、`minecraft:blast_furnace`、`minecraft:smoker` 不属于 BD 网络组件，
因此不会被 `DeviceType.isBindableMachine` 按命名空间直接拒绝；如果方块暴露了
Craftlines 可使用的自动化能力，通用机器绑定流程可能显示绑定成功。

但这类绑定不能据此视为 BD 原生炉兼容：

1. 普通机器绑定保存的是 JEI 原始分类 UID，例如 `minecraft:furnace` 或
   `minecraft:smelting`；
2. 炉类 JEI 配方在进入订单协议前会被规范化为 `smelting`、`blasting` 或
   `smoking`；
3. 原始 UID 与规范化执行族不是同一个字符串，不能为 BD 原生炉配方提供对应端点；
4. `RecipeOrderService` 对这三个执行族优先选择 BD 原生网络炉。

因此，当前正式支持和验收的炉类执行端只有同一 BD 网络中的网络熔炉、网络高炉和
网络烟熏炉。不要以“原版熔炉绑定成功”作为炉类订单可执行的判断依据。

## JEI 分类到执行族的映射

| JEI 分类 UID | 执行族 | 用途 |
| --- | --- | --- |
| `minecraft:furnace` | `smelting` | JEI 1.20.1 的熔炉分类别名，也是 `0.4.0` 最后补齐的兼容点 |
| `minecraft:smelting` | `smelting` | 熔炼规范名称 |
| `minecraft:blasting` | `blasting` | 高炉配方 |
| `minecraft:smoking` | `smoking` | 烟熏炉配方 |

映射由 `NativeFurnaceRecipeFamilies.executionFamily` 统一完成。未命中的 JEI UID
保持原值，不会被误当成原生炉配方。

### 1.20.1 为什么有两个熔炼名称

原生炉支持最初在提交 `4b6bb39` 中接入，但当时熔炼只覆盖
`minecraft:smelting`。`0.4.0` 标签所在提交 `7e4275c` 增加了
`minecraft:furnace → smelting`，并在 `NativeFurnaceRecipeFamiliesTest` 中固定该行为。
因此应以 `0.4.0` 标签本身为准：三个 Minecraft 版本都接受两种熔炼 UID；
其中 `minecraft:furnace` 主要用于保证 1.20.1 JEI 的配方按钮能够通过服务端校验。

## 不同入口的下单条件

### JEI 配方页的 Craftlines 按钮

这个入口固定当前看到的具体配方。客户端捕获 JEI 输出、输入槽和分类 UID，先把分类
规范化为执行族，再交给服务端。服务端仅在以下任一条件成立时接受虚拟配方分类：

- 当前 BD 网络有对应执行族的已加载原生网络炉；或
- 存在 JEI UID 与执行族都精确匹配的其他有效执行端点。

对本文的原版炉类分类，正式支持的条件是第一项。若只有一台普通原版熔炉的原始 UID
绑定，不能满足规范化后的执行族校验。

JEI 按钮的显示/激活状态使用网络级通用可用性缓存，因此“按钮可见”不等于当前炉型
一定可执行。最终结论始终以服务端分类校验和订单规划结果为准。

### 悬浮下单入口与 BD 网络槽

`0.4.0` 使用硬编码中键；当前 `0.5.0` 在 Minecraft「按键控制」的
“Beyond: Craftlines”分类中注册“下单悬浮资源”，默认仍为中键，并允许改为其他
鼠标键或键盘键。

这类入口只指定目标资源，不固定当前炉类配方。打开订单页后：

- 有对应 BD 网络炉时，`smelting` / `blasting` / `smoking` 配方可以参与规划；
- 没有对应炉型时，该执行族不会成为可用候选；
- 如果同一目标还有工作台或其他已绑定机器配方，规划仍可能选择那些候选。

所以“通用订单页能打开”和“指定炉类配方能提交”是两个不同结论。

## 网络炉如何被发现

`NativeFurnaceRegistry` 维护纯运行时索引，不额外保存一份炉列表：

1. BD 网络绑定/解绑事件会登记或移除网络炉；
2. 新放置的网络炉在后续服务端 tick 延迟登记，避免方块实体尚未完成网络初始化；
3. 区块加载时扫描其中的方块实体；区块或维度卸载时移除索引；
4. 只有 `getNetId() >= 0` 且类名能映射到三个已知炉型之一的设备会进入索引；
5. 每次使用前重新确认维度存在、区块已加载、方块实体仍是对应网络炉、`netId` 和炉型
   未改变。

查找键是 `(BD networkId, execution family)`。代码不要求设备与玩家同维度，但要求目标
维度存在、目标区块保持加载，并且炉的 BD `netId` 与订单网络完全相同。若有多台同族
网络炉，当前实现按维度 ID 和方块坐标确定性排序，从第一台有效设备开始；多炉负载均衡
不是当前承诺。

## 投料、加工与产物回收

炉类步骤由 `RecipeOrderService` 的原生炉分支执行：

1. 按订单网络和执行族寻找网络炉；
2. 通过订单运行时索引独占该机器位置，避免两个步骤同时向同一台炉投料；
3. 记录炉输出基线、网络输出基线、期望产量和尚未投送的输入；
4. 先模拟输入容量，再从 BD 网络事务性取料并写入炉的公开输入存储；写入不足的部分退回网络；
5. 等待 BD 网络炉自行加工。Craftlines 不替网络炉管理燃料；
6. 同时观察产物已经回到 BD 网络的增量，以及仍停留在炉输出存储中的产物；
7. 炉输出可提取时直接转回订单网络，网络拒收的余量写回炉输出存储；
8. 收齐当前批次后推进订单；阻挡模式或自增种子配方按批次继续下一轮。

`ExternalWait` 会把机器维度、坐标、产物资源键、`nativeFurnace` 标记、基线、已回收量、
剩余输入和占用机器写入订单 SavedData，因此正常的服务器保存/重启不会把等待状态降级成
普通外部机器等待。

## 等待与错误状态

| 状态键 | 含义 | 是否会自动继续 |
| --- | --- | --- |
| `native_furnace_unavailable` | 当前订单网络没有已加载、炉型匹配的 BD 网络炉 | 会等待；设备重新可用后可继续尝试 |
| `native_furnace_busy` | 目标机器位置已被更早的步骤占用 | 会等待 |
| `blocking_native_furnace_input` | 阻挡模式下，炉的输入、输出、燃料或燃料返还存储仍含本配方输入物品 | 清空后继续 |
| `native_furnace_output_clear` | 预留前炉输出存储已有本步骤目标产物，无法安全区分归属 | 清空后继续 |
| `native_furnace_preparing` / `feeding_native_furnace` | 已预留炉，正在检查容量或分批投料 | 会继续 |
| `native_furnace_processing` | 已投料，正在等待并回收目标产物 | 会继续 |
| `native_furnace_removed` | 等待中的炉被拆除、从网络解绑，或所在位置不再是网络炉 | 订单进入错误 |
| `native_furnace_type_changed` | 等待中的设备不再属于原执行族 | 订单进入错误 |

阻挡模式只改变批次和残留输入检查，不改变绑定策略：仍然无需手动绑定 BD 网络炉。

## 0.4.0 与 0.5.0 对照

| 项目 | 0.4.0（tag `7e4275c`） | 0.5.0（审计基线 `afd9522`） |
| --- | --- | --- |
| BD 网络炉绑定 | 无需绑定，且拒绝重复绑定 | 相同 |
| JEI UID 映射 | 已支持 `minecraft:furnace`、`minecraft:smelting`、`minecraft:blasting`、`minecraft:smoking` | 相同 |
| JEI 配方按钮 | 有匹配网络炉时可固定当前配方并下单 | 相同；虚拟输入与资源身份校验更严格 |
| JEI/网络槽悬浮入口 | 固定中键 | 统一可配置“下单悬浮资源”，默认中键 |
| 网络炉发现、投料、回收 | `NativeFurnaceRegistry` / `NativeFurnaceAutomation` / `RecipeOrderService` 原生路径 | 核心路径相同 |
| 规划资源身份 | 当时版本的虚拟输入与物品选择规则 | `0.5.0` 保留完整 NBT/Data Components 身份并统一树、汇总和服务端固定复算 |

从 `0.4.0` 到当前 `0.5.0`，`NativeFurnaceRecipeFamilies`、
`NativeFurnaceRegistry`、`NativeFurnaceAutomation` 的核心语义没有再次改变。期间
`bc37a19` 对 JEI 规划语义的尝试已由 `a4c5122` 完整回滚，不应写入最终炉类兼容结论。
`0.5.0` 对炉类订单真正保留下来的外围变化主要是虚拟输入协议、组件精确资源选择、
规划结果一致性以及可配置下单键。

## 三版本手工验收矩阵

以下每一项都应分别在 1.20.1 Forge、1.21.1 NeoForge、26.1.2 NeoForge 执行；比较版本
行为时，再分别使用 `0.4.0` JAR 与当前 `0.5.0` JAR。

建议配方：熔炼使用铁矿石或生铁 → 铁锭，高炉使用生铁 → 铁锭，烟熏使用生猪排 → 熟猪排。

| 编号 | 前置与操作 | 0.4.0 预期 | 0.5.0 预期 |
| ---: | --- | --- | --- |
| 1 | 用网络连接器点击同网络的 BD 网络熔炉、网络高炉、网络烟熏炉 | 三者均拒绝重复绑定并显示原生支持提示 | 相同 |
| 2 | 同网络放置并加载对应炉型，清空输入/输出；从 JEI 对三类具体配方点击 Craftlines 按钮 | 均能打开固定配方订单树、提交并创建订单 | 相同 |
| 3 | 1.20.1 检查熔炉 JEI 分类并从按钮下单 | 即使 UID 为 `minecraft:furnace` 也必须通过校验 | 相同 |
| 4 | 从 JEI 配方槽、物品列表、书签和 BD 网络槽悬浮目标后下单 | 使用固定中键，炉类仅在对应网络炉可用时参与规划 | 使用“下单悬浮资源”绑定；默认中键，重绑键盘键后也应相同 |
| 5 | 移除对应 BD 网络炉，仅保留其他炉型 | 固定炉类 JEI 入口不得创建订单；通用目标页不得规划到缺失炉型 | 相同 |
| 6 | 尝试绑定普通原版熔炉，然后在没有 BD 网络熔炉时提交 `smelting` | 不得把普通绑定视为 BD 原生执行端；不得成功创建可执行的原生炉订单 | 相同 |
| 7 | 保持炉空闲、网络有足量原料并提交多份产物 | 原料进入正确网络炉，真实加工，产物最终回到同一 BD 网络 | 相同 |
| 8 | 开启阻挡模式，在炉相关存储预留本配方输入物品 | 显示阻挡等待；清空后按一批一批投料 | 相同 |
| 9 | 下单前在输出存储放入同种目标产物 | 显示等待清空已有输出，避免把旧产物计入订单 | 相同 |
| 10 | 加工中卸载区块、拆炉、换网或替换炉型 | 不得静默完成；显示移除或炉型变化错误 | 相同 |
| 11 | 同网络提交两个 `smelting` 订单，再提交不同炉族订单 | 同族后单等待机器/网络族冲突；不同族可在并发上限内推进 | 相同 |
| 12 | 正在加工时保存并重启服务器 | 恢复后仍按原生炉等待状态继续或明确报设备失效，不得改走普通绑定机器 | 相同 |

## 自动测试覆盖与仍需手工确认的部分

已有单元测试覆盖：

- `NativeFurnaceRecipeFamiliesTest`：四个 JEI UID 的映射及执行族可用性；
- `NativeFurnaceFamilyTest`：三个 BD 方块实体类到执行族的映射；
- `DeviceTypeTest`：三个 BD 网络炉被识别为原生设备、BD 网络组件不可普通绑定；
- `RuntimeOrderIndexTest`：同网络同执行族的冲突与不同网络/执行族的并发规则；
- `JeiRecipeExecutionSourceTest`：`smelting` 不误走普通服务端工作台配方执行源。

当前仍没有覆盖真实 BD 网络炉方块实体的端到端 GameTest，以下结论必须保留手工验收：

- 各 JEI 版本运行时实际暴露的分类 UID，特别是 1.20.1 的 `minecraft:furnace`；
- 三种网络炉的真实输入容量、加工与产物回网；
- 阻挡模式、已有输出、区块卸载、换网、重启恢复；
- 多台同族网络炉的选择和吞吐行为。

## 代码依据

- `NativeFurnaceRecipeFamilies`：JEI UID → 执行族；
- `JeiVirtualRecipeLayouts`：把捕获的 JEI 炉类布局注册为规范化执行族；
- `OpenOrderMenuPayload`：固定 JEI 配方入口的服务端分类校验；
- `NativeFurnaceFamily` / `NativeFurnaceRegistry`：炉型识别、网络与加载状态索引；
- `NetworkLinkerItem` / `DeviceType`：BD 原生网络炉拒绝重复绑定；
- `RecipeOrderService.reserveNativeFurnace` / `tickNativeFurnace`：预留、投料、等待和回收；
- `NativeFurnaceAutomation`：BD 网络炉公开存储的容量、插入、提取和恢复；
- `RecipeOrderSavedData`：原生炉外部等待状态持久化。
