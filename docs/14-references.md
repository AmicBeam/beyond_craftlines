# 14 · 参考资料（不改变本设计）

## 1. RS Integration（共振存储）

可参考：

- JEI 工作树 UX
- 绑定与远程设备管理
- 材料账本/退款
- 进度 HUD

差异：本模组底座是 Beyond Dimensions；默认可执行族收敛；另有产线沙盒编译闭环。

## 2. ModuleWorks（ggg_moduleworks）

CurseForge：[`ggg-moduleworks`](https://www.curseforge.com/minecraft/mc-mods/ggg-moduleworks)

公开摘要取向：

> Run production trials in compressed space—shrink your factory into a block and save room!

可参考点：

- “先试产，再固化产线能力”的产品叙事
- 自动化/ Skyblock 场景下的空间压缩试产直觉

本模组刻意不同处：

- 不走“工厂缩进单方块”作为主方案
- 采用 **测试维沙盒 + 屏障 + 观察者 + 天穹跨界计量 + 编译图纸**
- 与 BD 合成链（JEI 一键）统一到同一模组

> 参考其方向，**不修改** Beyond: Craftlines 既有设计决策。

## 3. Sky Logistics

参考：

- `common + versions/{1.20.1,1.21.1,26.1.2}` 仓库布局
- 跨维升级与线路模型
- 软依赖源集组织

## 4. Beyond Dimensions

参考：

- `DimensionsNet` / `UnifiedStorage`
- 网络熔炉与终端
- 现有 JEI/EMI fill 转移（本模组在其上增加计划执行）

## 5. 内部前稿

早期草稿曾用名 Dimension Lineworks；正式名称已定为 **Beyond: Craftlines / 超越维度：合成链**，以本目录文档组为准。
