---
name: beyond-craftlines-release
description: 发布 Beyond Craftlines 新版本：同步版本号，使用 Java 17/21/25 构建三个 Minecraft 版本，整理净变更日志，创建并核验 tag 与 GitHub Release。用户说“发布更新”“发版”“重新发布”或要求创建版本 tag/Release 时使用。
---

# Beyond Craftlines Release

发布覆盖 Minecraft 1.20.1 Forge、1.21.1 NeoForge 和 26.1.2 NeoForge。默认创建 tag 和 GitHub Release，但不上传构建附件；用户明确要求 JAR 或附件时才上传。

## 发布前

- 检查工作区，保留并排除与本次发布无关的改动，不清理或覆盖用户的未提交内容。
- 读取以下三个文件的 `mod_version`，要求完全一致；该值就是版本号与 tag，tag 不加 `v`：
  - `versions/1.20.1/gradle.properties`
  - `gradle.properties`（Minecraft 1.21.1）
  - `versions/26.1.2/gradle.properties`
- 检查本地与远端同名 tag、GitHub Release 是否已存在。若存在，停止并说明现状；只有用户明确要求同版本重发时才允许替换。
- 找到上一条语义化版本 tag，以 `上一版本..HEAD` 的最终差异和提交记录整理净变更日志。忽略已回滚且没有最终效果的提交。
- 发布所需改动必须先单独提交并推送，不得混入无关文件。

## 三版本构建

在准备发布的同一提交上执行 `clean build`。显式指定对应 JDK，不依赖系统 Java：

```bash
# Minecraft 1.20.1 / Forge / Java 17
cd versions/1.20.1
env JAVA_HOME=/Users/bytedance/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.19+10/Contents/Home \
  PATH=/Users/bytedance/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.19+10/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ../../gradlew --no-daemon clean build

# Minecraft 1.21.1 / NeoForge / Java 21（项目根目录）
env JAVA_HOME=/Users/bytedance/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.9+10/Contents/Home \
  PATH=/Users/bytedance/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.9+10/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ./gradlew --no-daemon clean build

# Minecraft 26.1.2 / NeoForge / Java 25
cd versions/26.1.2
env JAVA_HOME=/Users/bytedance/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x/jdk-25.0.3+9/Contents/Home \
  PATH=/Users/bytedance/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x/jdk-25.0.3+9/Contents/Home/bin:/usr/bin:/bin:/usr/sbin:/sbin \
  ../../gradlew --no-daemon clean build
```

三个构建必须全部成功。核对并报告以下公共 JAR，文件名中的 `<version>` 使用当前 `mod_version`：

```text
versions/1.20.1/build/libs/beyond_craftlines-<version>+1.20.1.jar
build/libs/beyond_craftlines-<version>+1.21.1.jar
versions/26.1.2/build/libs/beyond_craftlines-<version>+26.1.2.jar
```

使用 `unzip -t` 检查压缩结构并计算 SHA-256。只有用户要求导出或上传时，才将三个最终 JAR 复制到 `dist/`；不要选择 sources、dev 或测试产物。

## Tag

- 在发布提交上创建 annotated tag：tag 名为版本号，说明为 `Beyond Craftlines <版本号>`。
- 推送 tag 后读取远端 peeled tag，确认其指向预期提交。

## GitHub Release

- 使用同名 tag，标题为 `Beyond Craftlines <版本号>`。
- 发布说明先写简短版本概述，再以 `### 主要更新` 汇总用户可感知的净变化。
- 固定包含以下支持版本：
  - Minecraft 1.20.1 / Forge
  - Minecraft 1.21.1 / NeoForge
  - Minecraft 26.1.2 / NeoForge
- 默认不传附件，并在末尾写：`本 Release 按发布要求不附带 JAR 或其他构建附件。`
- 用户明确要求附件时，上传三个已验收 JAR，并将末尾改为说明三个版本附件均已提供；不得只上传其中一个版本。
- 使用 notes file 创建 Release。创建后读取 tag、标题、正文、草稿状态、预发布状态、附件列表和 URL，确认正式发布；附件状态必须与用户要求一致。

## 同版本重发

仅当用户明确要求“重新发布”或等价操作时执行：

1. 提交并推送修正，重新完成三个版本的 `clean build` 与 JAR 验收。
2. 确认旧 tag、Release 及其目标提交。
3. 删除旧 GitHub Release，再删除远端和本地旧 tag。
4. 在新提交上重建 annotated tag、推送并核对远端 peeled tag。
5. 使用更新后的净发布说明重建正式 Release，并按用户要求处理三个附件。

任何删除或重指向操作都必须限定为已确认的精确版本号，不得使用通配符。

完成后报告 tag、Release 链接、发布提交、三套构建结果、三个 JAR 路径与 SHA-256，以及附件验收结果。
