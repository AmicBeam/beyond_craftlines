# 11 · 配置全集

三文件：`common` / `server` / `client`。键名跨版本一致。

## 1. common.toml（双端玩法开关）

```toml
[features]
enableAutocraft = true
enableBinding = true
enableBlueprintCapture = true
enableSandbox = true
enableDrive = true
enableDuplicator = true
enableJeiButtons = true          # 仅 BD 维度网络界面打开时生效
enableNetworkMiddleClickOrder = true
enableCompiledProductMarker = true

[recipeFamilies]
enableCrafting = true
enableSmelting = true
enableBlasting = true
enableSmoking = true
enableBlueprintBlackbox = true
enableExternalExecutors = false
allowVanillaFurnaceExecution = false

[planning]
preferNetworkStock = true
preferNativeRecipesOverBlueprint = true
maxDepth = 64
maxNodes = 4096
maxBlueprintNesting = 4

[capture]
maxVolumeBlocks = 32768   # e.g. 32*32*32
hardMaxVolumeBlocks = 262144
particleBounds = true

[sandbox]
forceSpectator = true
spectatorAutoExitOutsideSeconds = 5
defaultTimeoutTicks = 120000   # 100 minutes @20tps
stableWindowTicks = 1200
maxConcurrentSessionsPerServer = 8
slotSpacing = 512
useVanillaBarrier = true

[drive]
defaultMode = "TIMED"          # TIMED|GATED|INSTANT
allowInstantMode = false
internalBufferSlots = 27
```

## 2. server.toml（性能与安全）

```toml
[budgets]
planSoftTimeoutMs = 1500
planHardTimeoutMs = 5000
mainThreadExecNodesPerTick = 16
pasteBlocksPerTick = 2048
maxActiveChainsPerPlayer = 3

[security]
dangerousBlockBlacklist = [
  "minecraft:command_block",
  "minecraft:chain_command_block",
  "minecraft:repeating_command_block",
  "minecraft:structure_block",
  "minecraft:jigsaw"
]
requireNetPermissionForAllActions = true
enableClaimChecks = true

[skyLogistics]
enableTransferProbe = true
forceSandboxCrossDim = true
fallbackToInternalDeltaOnly = true

[diagnostics]
verboseLogging = false
retainFinishedReports = 32
```

## 3. client.toml

```toml
[ui]
showJeiButtons = true            # still requires BD network GUI context
showCompiledProductMarker = true
middleClickOpensOrderTree = true
rememberOutputDestination = true
planZoomSensitivity = 1.0

[hud]
showAutocraftHud = true
showSandboxHud = true
hudX = 8
hudY = 8

[anchor]
showBoundaryParticles = true
```

## 4. 配置原则

1. 新键必须有默认值，不覆盖用户旧文件缺失键以外的内容。
2. 列表型黑名单变更需在更新说明中提示手动合并。
3. 三版本默认值由 `version-consistency` 校验。
