# 16 · 内容命名（超维风格）

命名对齐超越维度语境（时空 / 构象 / 稳态 / 合成链），避免“维度工具”等直译名。

## 方块

| ID | 中文 | 英文 | 说明 |
| --- | --- | --- | --- |
| `schematic_anchor` | 构象锚点 | Schematic Anchor | 框选、保存草稿、发起试产 |
| `schematic_executor` | 构象执行器 | Schematic Executor | 放入稳态构象图自动执行 IO |
| `schematic_duplicator` | 构象复制台 | Schematic Duplicator | 按耗材复制稳态构象图 |
| `craftline_provisioner` | 合成链供给器 | Craftline Provisioner | 独立网络备料缓存；不参与第三方机器绑定 |

> 测试区围挡**不注册新方块**，直接使用原版 `minecraft:barrier`。

## 物品

| ID | 中文 | 英文 | 说明 |
| --- | --- | --- | --- |
| `network_linker` | 网络联结器 | Network Linker | 绑定/解绑第三方机器并将其接入当前 BD 网络 |
| `unstable_schematic` | 未稳构象图 | Unstable Schematic | 未测试草稿 |
| `stabilized_schematic` | 稳态构象图 | Stabilized Schematic | 已编译可执行图纸 |
| `trial_report` | 试产报告 | Trial Report | 试产报告实体化（可选） |

## 文案语气

- 优先使用：合成链、构象、未稳/稳态、试产、时空联结、网络下单
- 避免：维度工具、自定义测试屏障、EMI 联动入口、快捷键打开下单
