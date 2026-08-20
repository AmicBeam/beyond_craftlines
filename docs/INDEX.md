# Beyond: Craftlines 设计文档组目录

> 模组英文名：Beyond: Craftlines  
> 中文名：超越维度：合成链  
> modid：`beyond_craftlines`

## 阅读顺序

1. [00-feature-catalog.md](00-feature-catalog.md) — **全部功能一览**（先看这个）
2. [01-product-vision.md](01-product-vision.md) — 愿景与边界
3. [02-repository-and-build.md](02-repository-and-build.md) — 多版本仓库/构建
4. [03-architecture.md](03-architecture.md) — 架构与核心链路
5. 模块详设：
   - [04-module-autocraft.md](04-module-autocraft.md)
   - [05-module-binding.md](05-module-binding.md)
   - [06-module-blueprint-capture.md](06-module-blueprint-capture.md)
   - [07-module-sandbox-meter.md](07-module-sandbox-meter.md)
   - [08-module-compile-runtime.md](08-module-compile-runtime.md)
6. [09-integrations-upstream.md](09-integrations-upstream.md) — BD/天穹反向需求
7. [10-data-model-and-network.md](10-data-model-and-network.md)
8. [11-config.md](11-config.md)
9. [12-assets-localization.md](12-assets-localization.md)
10. [13-testing-strategy.md](13-testing-strategy.md)
11. [14-references.md](14-references.md) — RSI / ModuleWorks 参考（不改设计）
12. [15-ui-recipe-tree-emi-style.md](15-ui-recipe-tree-emi-style.md) — 配方树 UI（学习 EMI 风格，非联动）
13. [16-content-naming.md](16-content-naming.md) — 超维风内容命名

## 本文档组覆盖检查

- [x] 全部功能一览
- [x] 各模块设计点与核心链路
- [x] 需要的模块切分
- [x] UT / 集成 / 验收
- [x] 对天穹物流与超越维度的反向支持建议
- [x] 美术素材清单
- [x] 本地化键规划
- [x] 配置全集
- [x] 最终态设计（非 MVP）
- [x] 1.20.1 Forge / 1.21.1 NeoForge / 26.1.2 NeoForge + common/versions 构建方式
- [x] 配方树界面学习 EMI 风格（无 EMI 联动）
- [x] BD 网络界面下单入口 + 中键 + 稳态产物标记
- [x] 原版屏障围挡；观察者出界 5 秒退出
- [x] 超维风命名（网络联结器/构象图等）
# 当前边界提示

构象捕获、沙盒试产、报告、稳定构象图和产线执行已迁移到独立的 `Sky Logistics: Linefold`。本目录旧章节中描述构象域的部分仅保留为拆分前设计历史；当前 `beyond_craftlines` 只实现 JEI/BD 下单、配方树、第三方机器绑定与供给器。详见 [18-linefold-split.md](18-linefold-split.md)。

完整人工验收流程见 [manual-acceptance.md](manual-acceptance.md)。
