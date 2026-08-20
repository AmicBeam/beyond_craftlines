# 09 · 上游反向需求（Beyond Dimensions / Sky Logistics）

原则：能在本模组侧用桥接/mixin 完成的先完成；**仅当收益显著、可稳定复用**时，才建议上游增加正式 API。以下均标注优先级。

## 1. Beyond Dimensions（核心功能无反向依赖）

Craftlines 已直接使用 BD 公开的 `UnifiedStorage`、`UnifiedStorage.getNet()`、网络方块绑定事件和网络炉输入/输出存储。真实网络炉投料、等待回网以及订单 FIFO 事务均由附属实现，**不要求 BD 提供网络炉作业 API 或订单事务 API**。

### P1（可选体验增强）

1. **资源键规范化工具公开化**（item/fluid/energy 比较策略）
   - 用途：报告与规划共用同一匹配语义
2. **JEI transfer SPI / BD GUI 上下文 API**（除 fill 外允许第三方挂“计划执行/下单”按钮）
   - 用途：准确实现“仅网络界面显示下单按钮”
3. **带调用来源的存储变更事件**
   - 用途：试产审计时区分 Craftlines、玩家和其他机器流量；不用于订单事务

### P2（可选）

4. 网络侧“虚拟合成桥”方块或服务：允许官方认可的无世界工作台合成记账
5. 外部 `RecipeType` 到只出不进供给端点的能力注册表（若 BD 想原生吸收该玩法）

> 无以上支持时不影响 Craftlines 核心订单与机器执行。

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
