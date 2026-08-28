---
name: beyond-craftlines-recipe-compat
description: 为 Beyond Craftlines 添加第三方模组配方类型兼容：查阅模组官方源码或官方发布仓库，核对 JEI 分类、服务端 RecipeType 与配方字段，添加共享 JSON 映射或 IO profile，测试后用 Java 17/21/25 构建三个 Minecraft 版本并核验 JAR。用户说“给某模组的某配方类型加映射/预设/兼容/JSON”或提供模组名与机器、祭坛、仪式等配方类型时使用；不用于发布 tag 或 GitHub Release。
---

# Beyond Craftlines Recipe Compatibility

在当前 Beyond Craftlines 仓库中完成数据驱动的第三方配方兼容。覆盖 Minecraft 1.20.1 Forge、1.21.1 NeoForge 和 26.1.2 NeoForge；不得把其中一个版本当作默认版本。

## 先确认上游事实

- 检查工作区，保留用户已有改动；只修改本次模组兼容所需文件。
- 优先查模组的官方 GitHub/GitLab 源码、官方 source JAR 或官方发布仓库。按适用的 Minecraft 分支、tag 或发布版本分别核对，不用搜索摘要、非官方 Wiki 或名称猜测替代源码证据。
- 至少确认：
  - 模组 ID；
  - JEI recipe category UID；
  - 服务端实际 `RecipeType` 注册 ID；
  - 老版本未注册 `RecipeType` 时 Craftlines 会回退到的 `RecipeSerializer` ID；
  - 配方类继承关系、输入/输出的 public accessor 或 public 字段、计数语义；
  - 对应 JEI catalyst 的方块或物品 ID（用于确认分类对应的真实机器或结构）。
- 若不同 Minecraft 版本使用不同 family ID，在同一个映射中列出全部真实 ID；Craftlines 会按当前已加载 family 过滤。若某个上游版本不存在，不得虚构 ID。
- 找不到可信源码证据时，启用 `debugRecipeTypeMappings` 获取游戏内输出，或向用户索要该输出；不得提交猜测映射。

## 选择 JSON

默认保持无模组专用 Java 分支：

- JEI UID 与服务端 family 不同：新增或更新
  `src/main/resources/data/beyond_craftlines/recipe_type_aliases/<mod_id>.json`。
- `Recipe#getIngredients()`、标准输出或已有默认反射词表不能完整发现输入输出：再新增或更新
  `src/main/resources/data/beyond_craftlines/recipe_io_profiles/<mod_id>.json`。
- 先读同目录现有 JSON 和 `docs/DESIGN.md` 的 schema，再选择 `recipe_type`/`recipe_types`、`recipe_classes`、`recipe_class_prefixes`、`input_fields`、`output_fields`、wrapper、count semantics、direction 或 multiplier；只声明上游源码证明需要的字段。
- profile 的 selector 使用服务端 family，不使用 JEI UID。只有实际产生新的输入组名时才补中英文 GUI 语言键。
- 所有通用资源只放根目录 `src/main/resources`。不要复制到 `versions/1.20.1`、`versions/1.21.1` 或 `versions/26.1.2`；三个构建已共享或转换根资源。

单条 alias 的常用结构：

```json
{
  "jei_type": "example:machine_category",
  "recipe_types": [
    "example:legacy_family",
    "example:modern_family"
  ]
}
```

## 测试

- 为新增资源添加最小回归测试：alias 要验证每个真实版本 family 都能单独解析；IO profile 要验证 selector、输入输出字段和特殊计数规则。
- 资源格式测试尽量直接用 Gson 读取测试 classpath 中的 JSON，再调用纯映射逻辑。不要仅为解析 JSON 而加载依赖 Minecraft runtime 日志类的 registry loader；部分 1.20.1/1.21.1 单测 classpath 不含 `com.mojang.logging.LogUtils`。
- 运行 `git diff --check`，确认没有格式问题或无关修改。

## 三版本构建

普通兼容改动运行 `build`，不要默认 `clean`。显式使用本机 JDK：

```bash
# 1.20.1 Forge / Java 17，在 versions/1.20.1 运行
env JAVA_HOME=/Users/bytedance/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.19+10/Contents/Home \
  PATH=/Users/bytedance/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.19+10/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ../../gradlew --no-daemon build \
  -Dorg.gradle.java.installations.auto-download=false \
  -Dorg.gradle.java.installations.paths=/Users/bytedance/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.19+10/Contents/Home

# 1.21.1 NeoForge / Java 21，在仓库根目录运行
env JAVA_HOME=/Users/bytedance/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.9+10/Contents/Home \
  PATH=/Users/bytedance/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.9+10/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ./gradlew --no-daemon build \
  -Dorg.gradle.java.installations.auto-download=false \
  -Dorg.gradle.java.installations.paths=/Users/bytedance/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.9+10/Contents/Home

# 26.1.2 NeoForge / Java 25，在 versions/26.1.2 运行
env JAVA_HOME=/Users/bytedance/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x/jdk-25.0.3+9/Contents/Home \
  PATH=/Users/bytedance/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x/jdk-25.0.3+9/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ../../gradlew --no-daemon build \
  -Dorg.gradle.java.installations.auto-download=false \
  -Dorg.gradle.java.installations.paths=/Users/bytedance/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x/jdk-25.0.3+9/Contents/Home
```

三个构建必须全部成功。列出本次三个公共 JAR，并用 `unzip -l <jar>` 确认新增 alias/profile 已进入每个 JAR：

```text
versions/1.20.1/build/libs/beyond_craftlines-<version>+1.20.1.jar
build/libs/beyond_craftlines-<version>+1.21.1.jar
versions/26.1.2/build/libs/beyond_craftlines-<version>+26.1.2.jar
```

若构建因沙箱无法写入 `~/.gradle`，按正常审批流程以同一命令重试。依赖下载失败时只做最小诊断，不改变仓库依赖或 Gradle 配置来绕过。

## 交付

报告上游证据链接及所核对的分支/tag、JEI UID、各版本 family ID、是否需要 IO profile、测试结果、三个 JAR 的绝对路径和 jar 内资源核验结果。不要提交、推送、创建 tag 或 Release，除非用户另行明确要求。
