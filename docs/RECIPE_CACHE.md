# 配方缓存与 `/craftlines reload`

适用于 Minecraft 1.20.1 Forge、1.21.1 NeoForge 和 26.1.2 NeoForge，命令自 Craftlines 0.6.0 起提供。

## 怎样执行

先进入单人世界或多人服务器，在**自己的客户端聊天框**输入：

```text
/craftlines reload
```

这是客户端命令，普通玩家无需 OP 权限。服务器控制台不能替玩家执行本地缓存刷新。

## 会刷新什么

命令清除当前存档／服务器范围的 **JEI 执行描述缓存和规划目录缓存**，取消旧的内存加载任务，刷新 JEI 分类、输入分组与配方描述，并按当前预热配置重新准备索引。此前仍在写入的旧缓存不会在清除后覆盖新结果。

其他存档／服务器的缓存保留。命令不删除世界配方、机器绑定、订单、网络物品或已保存的配方／原料偏好；失效偏好仍按正常规则忽略。

## 什么时候需要执行

- 修改了配方、KubeJS、标签、IO profile 或输入分组，已经让相应数据生效，但配方 ID 未变。
- 展示配方没有可靠的原生注册 ID，修改后实现类和配方数量也没变。
- 怀疑客户端仍在使用旧描述，需要主动重新捕获当前范围的配方。

日常重进世界不需要每次执行。原生配方 ID 增删会增量更新；JEI 分类来源发生可识别变化时只更新对应分类。执行本命令会主动放弃当前范围的缓存，大型整合包随后需要重新加载。

## 与服务端 `/reload` 的区别

| 命令 | 执行位置 | 作用 |
| --- | --- | --- |
| `/reload` | 单人／服务器的命令系统，权限按 Minecraft 和服务器设置 | 让服务端重新读取数据包中的配方、标签、IO profile 等数据 |
| `/craftlines reload` | 每位玩家自己的客户端聊天框，无需 OP | 清除该客户端当前范围的 JEI 描述和规划缓存，并重新准备索引 |

修改服务端数据包或 IO profile 后，先由有权限的玩家／管理员执行 `/reload`，或按整合包要求重启服务器，让数据实际生效；随后需要刷新缓存的玩家分别执行 `/craftlines reload`。KubeJS 或客户端资源包等其他来源的改动，也先按对应加载流程生效，再刷新 Craftlines 缓存。

## 重载后加载哪些类型

客户端配置位于实例的 `config/beyond_craftlines-client.toml`：

```toml
[planning]
preloadAllRecipeTypes = true
```

- 默认 `true`：预热全部受支持配方类型，之后由各网络已启用类型限制实际可执行范围。
- `false`：根据当前有效网络的类型快照按需预热。取得网络快照或打开相应网络／合成树入口后，才有完整的按需范围。

命令按当前已生效的配置执行，本身不负责替加载器重新读取所有配置文件。

## 怎样确认完成和保存

命令提示成功表示缓存清除已完成、重新加载已安排，**不表示索引或落盘已经结束**。界面会分别显示检查 JEI 来源、读取缓存、载入类型、补充配方索引与建立查询索引；读缓存也需要解压和还原资源。

在游戏实例的 `logs/latest.log` 或 `logs/debug.log` 中查看：

| 日志关键字 | 含义 |
| --- | --- |
| `client planning catalog ready` | 内存规划目录及查询索引已可用 |
| `client JEI cache save result=INSTALLED` | JEI 描述文件已成功保存 |
| `client planning cache saved` | 规划目录文件已成功保存 |
| `client JEI cache restored` / `client planning cache restored` | 从对应缓存恢复成功 |
| `client JEI source scan progress` / `client JEI materialization progress` | 来源校验／实际布局加载进度 |
| `slow recipe index` | 达到 20ms 的慢操作或汇总 |

重新加载后，等待两份缓存的保存成功记录再完全退出游戏，才能确认下次进入可读取已安装的新文件。若命令提示无法清除缓存，查看日志中的具体异常。

缓存文件分别位于实例的 `config/beyond_craftlines-jei-recipes-v1/` 和 `config/beyond_craftlines-planning-catalog-v4/`，文件名按游戏版本和存档／服务器范围隔离。通常使用命令即可，无需手动逐个查找文件。
