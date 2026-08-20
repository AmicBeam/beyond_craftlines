# Beyond: Craftlines / 超越维度：合成链

Minecraft 1.21.1 / NeoForge 的 Beyond Dimensions 下单附属。

- `mod_id`: `beyond_craftlines`
- 必需依赖：Beyond Dimensions、JEI
- Java：21

本模组只负责下单系统：JEI 配方页入口、EMI 风格递归配方树、AE2/RS 风格订单确认、BD 网络扣料与合成、第三方机器配方类型绑定、订单持久化/状态/取消，以及无 GUI 的合成链供给器。

构象捕获、沙盒试产、报告、稳态产线与图纸复制已经迁移到独立模组 `Sky Logistics: Linefold`，本模组不再注册任何构象方块、物品、命令、维度或 SavedData。

## 构建

```bash
./gradlew build
```

产物：`build/libs/beyond_craftlines-1.21.1-neoforge-0.1.0.jar`

## 使用概要

1. 在 JEI 配方查询页点击 Craftlines 入口，确认递归配方树与数量后下单。
2. 网络联结器右击第三方机器进行绑定，潜行右击解绑；配方类型来自 JEI 正式催化剂 category UID 与服务端同 ID `RecipeType` 的确定性映射。
3. 外部机器按真实能力投料和收取产物，不内置 Mekanism 或其他具体模组分支。
4. BD 网络熔炉、网络高炉和网络烟熏炉无需绑定；订单直接向同网络的空闲对应炉型投料，并等待真实产物回网。
5. 同一 BD 网络的订单由 Craftlines 按 FIFO 串行提交，机器拒收的余量自动退回网络。
6. 下单时可开启 AE2 风格阻挡模式：每次只推送一次配方输入，上一批完成并回收后才发送下一批；目标机器预存本配方输入时也会等待。
7. 合成链供给器只接受订单产生的资源，不能由管道写入；玩家空手交互或外部管道均可提取库存。
8. 工作台配方统一通过服务端原配方逐次模拟，保留动态组件、耐久工具、容器返还物和模组自定义 `assemble/getRemainingItems` 行为；订单数量使用正 `long`，最大支持 `Long.MAX_VALUE`。

## 人工验收

完整逐步验收脚本见 [`docs/manual-acceptance.md`](docs/manual-acceptance.md)，包含原版活塞与 BD 网络熔炉、Mekanism 冶金灌注机、天穹物流供奉祭坛三个真实案例。
