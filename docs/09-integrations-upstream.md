# 09 · 上游反向需求（Beyond Dimensions / Sky Logistics）

原则：能在本模组侧用桥接/mixin 完成的先完成；**仅当收益显著、可稳定复用**时，才建议上游增加正式 API。以下均标注优先级。

## 1. Beyond Dimensions（建议）

### P0（强烈建议）

1. **网络操作审计友好的批量 extract/insert 回调或事件**
   - 用途：材料账本、驱动器、复制台统一监听成败
   - 现状：可直接调 `UnifiedStorage`；有事件可减少反射/包装
2. **稳定的“当前打开 GUI 对应 DimensionsNet”查询 API**
   - 用途：JEI 按钮判断网络上下文
   - 现状：多菜单各自持有 storage；建议 BD 提供 `NetGuiContext.get(player)`

### P1（很值得）

3. **网络熔炉执行进度/可投放性正式 API**
   - 用途：`NetFurnaceExecutor` 不必依赖 accessor mixin
4. **资源键规范化工具公开化**（item/fluid/energy 比较策略）
   - 用途：报告与规划共用同一匹配语义
5. **JEI transfer SPI / BD GUI 上下文 API**（除 fill 外允许第三方挂“计划执行/下单”按钮，且能判断当前是否是维度网络界面）
   - 用途：准确实现“仅网络界面显示下单按钮”

### P2（可选）

6. 网络侧“虚拟合成桥”方块或服务：允许官方认可的无世界工作台合成记账  
7. 图纸/外部黑盒配方的能力注册表（若 BD 想原生吸收该玩法）

> 无以上支持时，本模组仍可落地；P0/P1 主要是降 mixin 率与提升版本稳定性。

## 2. Sky Logistics（建议）

### P0（强烈建议）

1. **正式 Transfer Event / Probe API**
   ```java
   // 伪 API
   SkyTransferEvents.AFTER_TRANSFER.register((ctx) -> {
     // lineId, fromPos/toPos, dimFrom/dimTo, resource, amount, face
   });
   ```
   - 用途：沙盒跨界 IO 精确定账，避免字节码挂钩
2. **Sandbox / Temporary Cross-Dim Allowlist**
   - 允许按节点 UUID 或 lineId+dim 临时授权跨维，无需真实插入 dimension upgrade 物品
   - 用途：测试维节点“默认允许跨测试维与原维传输”

### P1（很值得）

3. **节点运行时标记 API**（`putAttachment("beyond_craftlines:sandbox", true)`）  
4. **Line 统计查询**：某时间窗 in/out totals（可直接喂报告）  
5. **只读节点配置导出**（lineId、face mode、filter、upgrades）便于捕获进草稿

### P2（可选）

6. 官方“测试模式线路”概念：忽略区块卸载策略的受限调试线路  
7. （可选）观察者玩家交互限制钩子；Craftlines 默认用出界 5 秒退出，不依赖退出按钮

> 若短期无上游支持：本模组在 versions 内做受限 mixin，并在文档声明脆弱点；一旦上游事件落地即删除 mixin。

## 3. 不建议上游做的事

- 不要求 BD 实现完整工作树规划（应留在 Craftlines）
- 不要求天穹实现图纸编译/沙盒维（应留在 Craftlines）
- 不把本模组玩法硬塞进对方创意标签

## 4. 协作建议

向 BD / SL 提 issue 时附：

1. 用例（JEI 执行账本 / 沙盒跨界计量）
2. 期望 API 草签
3. 无 API 时的临时 mixin 位置
4. 三版本兼容诉求（1.20.1 / 1.21.1 / 26.1.2）
