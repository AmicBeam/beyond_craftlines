# 15 · 配方树界面设计（学习 EMI 风格，非 EMI 联动）

最终态要求：**Craftlines 配方树界面在信息架构与交互气质上对齐 EMI 的 Recipe Tree / BoMScreen**，而不是 RSI 的卡片列表优先风格。  
**只学习其视觉与交互，不实现 EMI 模组联动/插件/入口。** GUI 完全独立。

参考源码（工作区 `emi/`）：

- `dev.emi.emi.screen.BoMScreen`
- `dev.emi.emi.bom.MaterialTree` / `MaterialNode` / `TreeCost`
- `dev.emi.emi.api.EmiApi.viewRecipeTree()`

---

## 1. 设计目标

1. **树状空间画布**：节点按层级自动排布，可拖拽平移、滚轮缩放。
2. **目标在上、成本在下**：顶部是目标产物与批次；底部是汇总原材料成本条。
3. **节点可折叠/展开**，可替换解析配方（resolution）。
4. **库存进度着色**：已有 / 可合成 / 缺料状态一眼可辨。
5. **与执行系统接通**：树不仅是查看，还能一键按树执行（Craftlines 增量能力）。

---

## 2. 屏幕布局（对齐 EMI BoM）

```text
┌──────────────────────────────────────────────────────────────┐
│ Title: screen.beyond_craftlines.recipe_tree                  │
│ [批次 xN] [模式]                              [?] help       │
│                                                              │
│                     (可平移/缩放的树画布)                      │
│                          [目标节点]                           │
│                        /     |     \                          │
│                     [A]     [B]     [C]                       │
│                    /  \             |                         │
│                 ...                ...                        │
│                                                              │
│ ──────────── 原材料成本 / 缺口汇总 ────────────               │
│ [iron] [quartz] [redstone] ...                               │
│                                                              │
│ [返回] [重新规划] [执行] [取消]   去向: 网络/背包              │
└──────────────────────────────────────────────────────────────┘
```

关键尺寸常量（初始值对齐 EMI，可在 client config 微调）：

- `NODE_WIDTH = 30`
- `NODE_HORIZONTAL_SPACING = 8`
- `NODE_VERTICAL_SPACING = 20`
- `COST_HORIZONTAL_SPACING = 8`

---

## 3. 交互细节（必须具备）

### 3.1 画布

- 鼠标拖拽空白处平移（offX/offY）
- 滚轮缩放（zoom 级数，文本与节点同步缩放）
- 初始定位：有树时目标大致在屏幕上方 1/3 处（参考 EMI `height / -3`）

### 3.2 节点

每个节点显示：

- 物品/流体图标
- 数量（如 `x16`）
- 配方类别小图标（熔炉/合成/图纸等）
- 状态徽标：`NETWORK` / `CRAFT` / `BLUEPRINT` / `MISSING`

操作：

- **左键节点**：
  - 若可折叠：切换 expand/fold（对齐 `FoldState`）
  - Shift+左键：查看该节点配方（打开 JEI 配方页，软依赖）
- **右键节点**：
  - 打开“解析/替代配方”菜单（对齐 EMI resolution）
  - 可固定某条配方分支
- **中键**（可选）：收藏缺料到 JEI

### 3.3 批次

- 目标节点旁 `xN` 批次控件（对齐 EMI `batches`）
- 滚轮/按钮调整；变更后触发 `recalculateTree`

### 3.4 成本条

- 底部列出叶子原材料（Flat cost）
- 排序稳定（按注册序或名称）
- 显示：需要量、网络已有量、缺口
- 概率/机会产出成本若存在，单独样式（EMI 有 `ChanceMaterialCost`；BD 原生链可简化，但 API 预留）

### 3.5 执行区（Craftlines 扩展，EMI 无对等物）

- `执行`：按当前树提交服务端执行
- `重新规划`：用最新网络快照重建
- `取消`：取消进行中的链
- 产物去向切换：BD 网络 / 玩家背包（记忆）

---

## 4. 视觉风格

1. **深色半透明面板 + 清晰节点底**，避免花哨渐变。
2. 连线：父子节点垂直/折线，颜色随状态变化（缺料偏红，完成偏绿）。
3. 图标渲染可批处理（EMI `StackBatcher` 思路），保证大树流畅。
4. Tooltip：
   - 物品本体信息
   - 配方摘要组件（类似 `RecipeTooltipComponent`）
   - 来源说明（网络/合成/图纸）
5. Help 按钮提供操作说明浮层（拖拽/缩放/批次/中键入口说明；无下单快捷键）。

---

## 5. 数据映射：EMI BoM → Craftlines

| EMI | Craftlines |
| --- | --- |
| `MaterialTree` | `CraftPlan` |
| `MaterialNode` | `PlanNode` |
| `TreeCost` | `PlanCostSheet` |
| `FoldState` | `NodeFoldState` |
| `ProgressState` | `NodeProgress` |
| `BoM.tree.batches` | `plan.batches` |
| `EmiPlayerInventory` | `StorageSnapshot`（网络+背包） |
| resolution recipe | `AlternativeRecipeChoice` |
| catalyst / remainder | ledger remainders / reusable inputs |

注意：服务端权威仍是 Craftlines `PlanBuilder`；客户端树是 DTO 可视化。不要在客户端单独算一套可能不一致的树。

---

## 6. 与 JEI 入口关系

- JEI 配方页按钮：`预览配方树` / `执行`
- EMI 若存在：
  - 可提供并列按钮，或在 EMI 配方界面旁挂 Craftlines 动作
  - **不劫持** EMI 原生 `viewRecipeTree()`；两者可共存
- 当 EMI 与 JEI 同时存在时，按钮文案统一为 Craftlines，避免玩家以为在看 EMI 原生树却点出执行

---

## 7. 客户端模块切分

```text
client/ui/tree/
  RecipeTreeScreen
  TreeCanvas
  TreeLayoutEngine          # 参考 EMI TreeVolume 思想
  TreeNodeWidget
  CostBarWidget
  BatchControlWidget
  TreeTooltipAssembler
client/ui/theme/
  CraftlinesTextures       # 尽量复用 EMI 气质的九宫/图标规格
```

`TreeLayoutEngine` 算法要点（对齐 EMI `addNewNodes`）：

1. 递归布局子树
2. 计算子树左右边界，避免重叠
3. 水平居中父节点于子树
4. fold 的节点不分配子树宽度

---

## 8. 配置（client）

```toml
[recipeTree]
learnEmiLayout = true
nodeWidth = 30
nodeHorizontalSpacing = 8
nodeVerticalSpacing = 20
scrollZoomSensitivity = 1.0
showCostBar = true
showCategoryIcons = true
rememberCanvasPerGoal = true
```

---

## 9. 本地化键（补充）

- `screen.beyond_craftlines.recipe_tree`
- `gui.beyond_craftlines.tree.batches`
- `gui.beyond_craftlines.tree.cost`
- `gui.beyond_craftlines.tree.fold`
- `gui.beyond_craftlines.tree.expand`
- `gui.beyond_craftlines.tree.resolve`
- `gui.beyond_craftlines.tree.missing`
- `gui.beyond_craftlines.tree.execute`
- `gui.beyond_craftlines.tree.help.*`

---

## 10. UT / 客户端测试

1. `TreeLayoutNoOverlapTest`：随机树无重叠
2. `FoldRelayoutTest`
3. `BatchRecalcTest`
4. `CostBarAggregationTest`
5. 手测：100+ 节点拖拽缩放流畅；缺料着色正确；右键换配方后成本刷新
