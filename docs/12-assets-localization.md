# 12 · 美术素材与本地化

## 1. 美术素材清单

### 1.1 物品贴图（32x32 或模组统一像素规范）

- `spacetime_linker`（时空联结器）
- `unstable_schematic`（未稳构象图）
- `stabilized_schematic`（稳态构象图）
- `trial_report`（试产报告）

### 1.2 方块贴图

- `craftline_anchor`（六面 + 模式覆盖层）
- `craftline_drive`（开关态/工作态）
- `schematic_duplicator`
- 无自定义屏障贴图（使用原版 barrier）
- 网络界面“稳态构象图可产出”角标图标

### 1.3 GUI

- 配方树（EMI 风格）节点底图、连线、成本条、批次控件、help 图标
- 通用面板九宫
- 按钮：普通/悬停/禁用/选中
- 工作树节点底图、缺料警告标、图纸节点标
- 进度条、搜索框、标签页
- 报告表头图标：item/fluid/energy/warning

### 1.4 粒子 / 特效

- 框选边界粒子
- 测试传送过渡（可选）
- 驱动器工作粒子（可选）

### 1.5 Logo / 手册

- `beyond_craftlines_logo.png`
- Patchouli/Guide 封面（若做）

### 1.6 设计源文件存放

`assets/art/`（不进 jar）：

- PSD/Aseprite 源
- 导出尺寸规范
- GUI 间距参考（可借鉴天穹物流 docs）

## 2. 本地化

最低：`en_us` + `zh_cn`。键分组：

### 2.1 内容名

- `item.beyond_craftlines.*`
- `block.beyond_craftlines.*`
- `itemGroup.beyond_craftlines`

### 2.2 GUI

- `gui.beyond_craftlines.plan.*`
- `gui.beyond_craftlines.anchor.*`
- `gui.beyond_craftlines.report.*`
- `gui.beyond_craftlines.drive.*`
- `gui.beyond_craftlines.duplicator.*`
- `gui.beyond_craftlines.binding.*`

### 2.3 消息 / 错误

- `message.beyond_craftlines.*`
- `error.beyond_craftlines.*`（对应 ErrorCode）

### 2.4 工具提示

- 图纸摘要（hash 短码、输入输出数量、状态）
- 时空联结器绑定信息（勿再出现“维度工具”文案）

### 2.5 JEI / 网络界面

- 仅 BD 维度网络界面上下文下的按钮 tooltip：`预览配方树` / `下单`
- 中键下单提示
- 稳态构象图产物标记 tooltip
- 分类名：`Beyond Craftlines` / `超越维度：合成链`
- 构象图黑盒配方类别名

### 2.6 配置注释

- 英文注释写在 toml comment
- 中文说明可放 wiki；代码 comment 保持中英至少一种完整

### 2.7 命令反馈

- 取消、测试开始/结束、权限拒绝等

## 3. 质量门槛

1. `en_us`/`zh_cn` 键集合一致（CI 检查）
2. 禁止直接把 ErrorCode 枚举名显示给玩家
3. 长文本 GUI 需可换行，中英都过一遍
