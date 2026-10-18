# Minecraft 1.21.1 — Beyond Dimensions 0.8 专用构建

模组 ID 仍为 `beyond_craftlines`，模组版本仍为 `0.7.0`。成品名为
`beyond_craftlines-0.7.0+1.21.1-for-bd0.8.jar`，仅支持本体 `[0.8.0,0.9)`。
用此 jar 替换普通 1.21.1 版本，不要同时安装两个 Craftlines jar。

前置：Minecraft 1.21.1、NeoForge、本体 0.8.0、JEI、CompixelUI 0.1.12 或以上。
Kotlin 使用两种安装方式之一：普通 CompixelUI + Kotlin for Forge，或自带 Kotlin 的
CompixelUI `-with-kotlin` 版；不要同时安装 `-with-kotlin` 与 Kotlin for Forge。

## 适配内容

- 仪表盘与供应器通过本体的 `saveClientData` / `loadClientData` / `syncToClient` 同步显示数据，
  配方树、缓存资源和无线连接等服务端数据保留在存档中。
- 移除对旧 `DimensionsNetGUI` 的 Mixin。新的存储界面左侧增加 Craftlines 页签，
  其中提供订单状态和仪表盘状态入口。
- 供应器覆盖新增的带接收过滤条件的存入接口，保持仅订单可存入的限制。
- 网络绑定调用改用 `getPrimaryNetFromPlayer`。
- 悬停下单快捷键通过 `InventoryScreenAccess` 获取 Compose 槽位的可见区域，支持缩放与裁剪。

## 构建

使用 JDK 21，在本目录执行 `../../gradlew --no-daemon build`，输出在本目录 `build/libs`。
Gradle 从官方 wintercogs Maven 解析本体和 CompixelUI。开发运行使用 CompixelUI 内置的 Kotlin，
不再额外加载 Kotlin for Forge。

本目录通过生成源码目录复用根目录实现，本目录的同名 Java 文件覆盖共享源码；
资源也独立生成。根目录普通 1.21.1、1.20.1、26.1.2、26.2 和 26.3 的构建配置与源码保持原样。
