# 02 · 仓库结构与多版本构建

对齐天穹物流（Sky Logistics）的 `common + versions/*` 方案。

## 1. 目标版本矩阵

| 目录 | 加载器 | Java | Minecraft |
| --- | --- | --- | --- |
| `versions/1.20.1` | Forge 47+ | 17 | 1.20.1 |
| `versions/1.21.1` | NeoForge | 21 | 1.21.1 |
| `versions/26.1.2` | NeoForge | 21 | 26.1.2 |

每个版本目录都是**可独立构建的 Gradle 工程**；共享逻辑放 `common/`。

## 2. 推荐仓库树

```text
beyond-craftlines/
  README.md
  docs/                          # 本设计文档组
  common/
    src/main/java/...            # 跨版本业务核心（尽量纯逻辑）
    src/main/resources/...       # 共享资源（lang 可共享；模型按需）
    src/test/java/...            # 纯单元测试（不依赖加载器）
  versions/
    1.20.1/
      build.gradle
      settings.gradle
      gradle.properties
      src/main/java/...          # Forge 适配、mixin、平台注册
      src/main/resources/...     # mods.toml / mixins / 版本专有资源
      src/jei/java/...           # 可选 JEI 兼容源集
      src/jade/java/...
      src/test/java/...          # 版本集成测试
    1.21.1/ ...
    26.1.2/ ...
  gradle/
    version-consistency.gradle   # 三版本一致性校验
  scripts/
    build_all_versions.sh
    check_docs_links.sh
  assets/                        # 设计稿、原始贴图、GUI 参考（不进 jar）
  wiki/                          # 可选玩家文档
```

## 3. sourceSet 接入约定（与 SL 同款）

每个 `versions/<ver>/build.gradle`：

```gradle
sourceSets.main.java.srcDir '../../common/src/main/java'
sourceSets.main.resources.srcDir '../../common/src/main/resources'
sourceSets.test.java.srcDir '../../common/src/test/java'
```

并额外按需：

```gradle
sourceSets.main.java.srcDir 'src/jei/java'
sourceSets.main.java.srcDir 'src/jade/java'
```

规则：

1. **玩法语义进 common**。
2. **注册、网络封包编解码差异、mixin 目标、mods.toml/neoforge.mods.toml 进 versions**。
3. 若某 API 在三版本签名不同，common 只依赖 `platform/` 抽象，由版本模块实现。

## 4. Gradle 属性建议

`versions/<ver>/gradle.properties`：

```properties
mod_id=beyond_craftlines
mod_name=Beyond: Craftlines
mod_group_id=com.beyondcraftlines
mod_version=1.0.0
minecraft_version=...
# forge_version / neo_version
beyonddimensions_version=...
skylogistics_version=...
jei_version=...
```

产物名：

```text
beyond_craftlines-<mod_version>+<minecraft_version>.jar
```

## 5. 依赖策略

### 5.1 硬依赖

- Beyond Dimensions：`mandatory=true`，`ordering=AFTER`

### 5.2 依赖

- JEI：必需依赖（无 EMI）
- Sky Logistics / Jade：可选依赖
- 兼容代码放独立源集或 `integrate/*` 包，类加载用 mod presence 守卫

### 5.3 本地/离线构建

沿用 SL 思路，支持：

- `-Dbeyondcraftlines.offlineRepo=...`
- `-Dbeyondcraftlines.jeiApiJar=...`
- `-Dbeyondcraftlines.jadeApiJar=...`

## 6. 平台抽象层

common 中定义：

```text
platform/
  PlatformNet.java          # 找 DimensionsNet / 权限
  PlatformRegistry.java     # 物品方块菜单注册桥
  PlatformNetwork.java      # C2S/S2C
  PlatformCapabilities.java # 物品/流体/能量探测
  PlatformPlayer.java       # 观察者模式切换等
```

versions 实现：

- `forge/Forge*`（1.20.1）
- `neoforge/Neo*`（1.21.1 / 26.1.2）

禁止在 common 直接 import `net.minecraftforge.*` 或 `net.neoforged.*`（测试可用架构守护 UT）。

## 7. 版本一致性校验

`gradle/version-consistency.gradle` 检查：

1. 三版本 `mod_id` / 显示名一致
2. 关键配置键在三版本默认值一致（允许 loader 段差异）
3. 方块/物品注册名集合一致
4. 语言键缺失率（zh_cn/en_us）为 0
5. mixin 配置存在且 package 匹配

`scripts/build_all_versions.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail
for v in 1.20.1 1.21.1 26.1.2; do
  (cd "versions/$v" && ./gradlew clean test build --no-daemon)
done
```

## 8. 代码归属决策表

| 改动类型 | 放哪 |
| --- | --- |
| 工作树规划算法 | `common` |
| 材料账本 | `common` |
| 图纸状态机 | `common` |
| Forge/Neo 注册 | `versions/*` |
| JEI 插件 | `versions/*/src/jei` + 薄 common 接口 |
| 天穹跨维 mixin | `versions/*`（各版本目标类可能不同） |
| 语言文件 | 优先 `common`，版本专有覆盖放 versions |

## 9. 开发工作流

1. 新功能先写 common 接口 + UT。
2. 再补 1.21.1 适配（通常最新 API）。
3. 回港 1.20.1 / 26.1.2，处理映射与事件总线差异。
4. 跑 `build_all_versions.sh`。
5. 更新 `docs/00-feature-catalog.md` 若引入新玩家可见功能。
