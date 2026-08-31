# Beyond Craftlines 优化优先级

本路线只从本仓库现有架构、已复现问题和三版本约束推导；外部 ARR 仓库仅用于行为调研，未复制其代码或结构。

## P0：正确性闭环

1. 组件感知身份贯穿候选去重、ingredient 选择协议、库存扣除和树节点合并；物品级 `isSame` 只用于配方发现。
2. 规划器输出唯一的选材与节点分配结果，左侧树和右侧缺料/提取摘要共同渲染该结果，不再各自重算。
3. 将 `NO_RECIPE`、`MISSING_MATERIALS`、`BUDGET_EXHAUSTED` 和运行时不可用拆成独立状态；无配方定向查询不等待全量 JEI 预热。
4. 完成 `CONSUMED / REUSABLE / DURABILITY` 与容器组的真实机器回归矩阵。

## P1：规划性能与可诊断性

1. 用增量事务日志替换候选分支的完整 State/Map 复制，并为回滚、深层嵌套和历史压缩建立纯 Java 测试。
2. 给配方目录增加可缓存的下界成本与失败分支 memo，候选排序仍以可制造性和玩家偏好为最高优先级。
3. 将客户端规划执行器改为有界队列和每屏单活动任务；暴露取消、队列繁忙、节点/时间预算的独立诊断。
4. 对“可见树叶缺料汇总 vs 规划缺料”增加 invariant 日志和差量采样，先诊断再优化。

## P2：前端与兼容层

1. 抽出 viewer-neutral 下单入口，逐步把 `SHOW_JEI_ORDER_BUTTON_EVERYWHERE` 等命名迁移为 recipe-viewer 中立名称。
2. 保持 JEI/EMI 只负责展示与选择，机器执行语义由有界协议和显式 profile/accessor 决定；不支持的 viewer-only 配方不猜测执行方式。
3. 建立 1.20.1 NBT、1.21.1/26.1.2 Data Component、耐久工具、容器、流体和化学品的跨版本契约测试。
4. 在不扩大运行时扫描的前提下增加配方布局/偏好负缓存，并以配方 reload/runtime generation 精确失效。
