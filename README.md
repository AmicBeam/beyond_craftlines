# Beyond: Craftlines / 超越维度：合成链

| 字段 | 值 |
| --- | --- |
| 英文名 | Beyond: Craftlines |
| 中文名 | 超越维度：合成链 |
| `mod_id` | `beyond_craftlines` |
| 硬依赖 | Beyond Dimensions（超越维度） |
| 软依赖 | Sky Logistics（天穹物流）、JEI、EMI、Jade |
| 目标版本 | `1.20.1` Forge / `1.21.1` NeoForge / `26.1.2` NeoForge |

本目录是**最终设计文档组**（非 MVP 切片）。实现前以本文档组为准。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [docs/00-feature-catalog.md](docs/00-feature-catalog.md) | **全部功能一览** |
| [docs/01-product-vision.md](docs/01-product-vision.md) | 产品愿景、边界、用户旅程 |
| [docs/02-repository-and-build.md](docs/02-repository-and-build.md) | 多版本仓库与构建（对齐天穹物流） |
| [docs/03-architecture.md](docs/03-architecture.md) | 总体架构、包结构、核心运行时 |
| [docs/04-module-autocraft.md](docs/04-module-autocraft.md) | JEI/BD 工作树与一键合成执行 |
| [docs/05-module-binding.md](docs/05-module-binding.md) | 维度工具与设备绑定 |
| [docs/06-module-blueprint-capture.md](docs/06-module-blueprint-capture.md) | 框选、草稿图纸、结构捕获 |
| [docs/07-module-sandbox-meter.md](docs/07-module-sandbox-meter.md) | 测试维、屏障、计量与报告 |
| [docs/08-module-compile-runtime.md](docs/08-module-compile-runtime.md) | 编译图纸、驱动器、复制、JEI 图纸源 |
| [docs/09-integrations-upstream.md](docs/09-integrations-upstream.md) | 对 BD / 天穹物流的反向需求 |
| [docs/10-data-model-and-network.md](docs/10-data-model-and-network.md) | 数据模型、持久化、网络协议 |
| [docs/11-config.md](docs/11-config.md) | 配置项全集 |
| [docs/12-assets-localization.md](docs/12-assets-localization.md) | 美术素材与本地化 |
| [docs/13-testing-strategy.md](docs/13-testing-strategy.md) | 单元测试 / 集成测试 / 游戏内验收 |
| [docs/14-references.md](docs/14-references.md) | RSI / ModuleWorks 等参考点（不改变本设计） |
| [docs/15-ui-recipe-tree-emi-style.md](docs/15-ui-recipe-tree-emi-style.md) | 配方树界面（EMI BoM 风格） |

## 一句话

在超越维度网络上同时提供：

1. **合成链**：JEI 工作树预览 + 一键递归合成（默认 BD 自有设备族）
2. **产线链**：框选真实产线 → 沙盒测试 → 编译图纸 → 自动量产 / 复制 / 作为缺料来源
