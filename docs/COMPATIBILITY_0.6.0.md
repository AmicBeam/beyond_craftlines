# Beyond: Craftlines 0.6.0 模组配方兼容检查

检查日期：2026-09-07。范围：用户两批模组清单（重复项合并）、封包所对应原模组，以及 Create、Mekanism、IE、Ender IO、AE2、Tech Reborn、Industrial Foregoing、GTCEu、PneumaticCraft。

## 如何读这份表

**不能宣称全部支持。** 本次完成的是通用采集/规划/回收修复和有源码依据的 profile 扩展，不是为所有世界事件、玩家动作和机器内部工艺提供执行器。

项目代码和共享资源同时覆盖 **Minecraft 1.20.1 Forge / Java 17、1.21.1 NeoForge / Java 21、26.1.2 NeoForge / Java 25**。表中“上游证据”仅指实际读取的源码分支；没有核对的其他版本均为**未验证**，不推断其不存在，也不承诺同一模组跨版本 API 相同。源码分支存在不等于对应发行构件已完成整合包验收。

- **常规**：标准工作台/锻造等 Recipe 复用现有服务端真实装配路径，仍须原配方匹配成功。
- **条件 / 部分**：可使用部分通用能力，但要求 JEI 完整表达、BD 支持所有资源键、网络具备真实执行端点及必要设施。不是“整个模组全兼容”。
- **需适配**：存在展示约束、随机/动态结果、GUI 操作、实体、世界事件或分阶段工艺，单加分组 profile 无法补齐。
- **规则 / 加载器 / 版本 / 待核验**：分别表示非制造玩法、平台不匹配、历史版本证据或资料不足，不等同于已支持。

所有运行场景仍需游戏内验证。本次没有启动装有全部目标模组的客户端或专用服务器。

## 本次实现

1. JEI 采集保留显式 focus links，联动输入输出一起展开；不能证明关联的多候选输出会拒绝。完整输入上限 256 槽、每槽 256 候选、总候选 8192；超限或未知资源不再静默丢弃。81 格只是协议/模型能力，不是新增 9×9 自动工作台代理。
2. 同一次加工的其他产物随描述、两个上传入口、客户端目录、服务端计划和订单存档保存。确定副产物抵扣后续需求，依赖等待其生产步骤；实际回收到的副产物进入订单预留，未用完时随订单结束/取消返还。
3. `output_probability_rules` 可声明概率容器字段。内置 Create `ProcessingRecipe.getRollableResults() → getStack()/getChance()`（scale=1）规则；已识别的随机输出不作为保证交付的目标或副产物信用，但可实际回收。没有实现随机目标的自动补投/材料上限策略；其他模组的概率元数据仍需对应规则或适配器。
4. `ignored_output_fields` 过滤实际配方暴露的说明产物。Occultism 的 `getRitualDummy()` 不再作为可制造物品或副产物。
5. 输入分组继续由 `assets/beyond_craftlines/jei_input_group_profiles` 及资源包提供；不新增玩家配置页面、不迁移到服务端。新增六个分组 profile，Goety 补充动态 `ritual_` 分类前缀。新增 `fixed` cardinality 用于源码明确展示、但没有独立成员的固定槽；支持已声明配方父类。
6. 缓存格式升至 v3，并按资源会话隔离。配方同步、JEI 重建、profile 同步或退出世界使旧会话无效，避免 KubeJS/数据包保持 ID 却改内容时读到旧表。仍保留同资源会话内的目录复用；**不再跨登录/跨进程复用仅按 ID 验证的磁盘目录**，完整内容指纹尚未实现。
7. 网络协议升至 26；三个版本均启用 JEI 布局关联桥接。26.1.2 只保留 JEI Mixin，不引入其尚未支持的 EMI 前端。

## 模组逐项检查

| 模组 | 结论 | 配方范围 | 具体限制与本次处理 | 上游证据 |
|---|---|---|---|---|
| Goety | 部分 | 祭坛仪式、诅咒灌注、火盆、釜、粉碎、灵魂吸收等 | 既有分组扩展到 goety:ritual_*；召唤、仪式结构、灵魂和触发条件仍需真实设施。 | [1.20](https://github.com/Polarice3/Goety-2/blob/1.20/src/main/java/com/Polarice3/Goety/compat/jei/CursedInfuserCategory.java) |
| Crock Pot | 需适配 | crockpot:crock_pot_cooking 与若干说明分类 | 食物类别、阈值、优先级/权重不是独立 OR 槽；不能把示例食物当作完整配料约束。 | [1.20.x](https://github.com/SihenZhang/CrockPot/blob/1.20.x/src/main/java/com/sihenzhang/crockpot/integration/jei/CrockPotCookingRecipeCategory.java) |
| Forbidden and Arcanus | 部分 | Hephaestus 与 Clibano 分类 | 所核对 26.1 分支含 26.1.2；炉类 I/O 与仪式、等级、精华、增强物必须分别判断，未提供全仪式执行器。 | [26.1](https://github.com/stal111/Forbidden-Arcanus/blob/26.1/neoforge/src/main/java/com/stal111/forbidden_arcanus/common/integration/ForbiddenArcanusJEIPlugin.java) |
| Embers Rekindled | 部分 | alchemy、stamping、melting、mixing 等 | 新增炼金中心/外围输入分组；aspectus、模具、灰烬与启动条件不等于普通消耗槽。 | [rekindled](https://github.com/RCXcrafter/EmbersRekindled/blob/rekindled/src/main/java/com/rekindled/embers/compat/jei/AlchemyCategory.java) |
| Eidolon | 版本/适配 | 坩埚与工作台 | 所给原仓库为历史实现；分阶段投料和搅拌需要执行器，不能据此宣称现代移植版兼容。 | [master](https://github.com/elucent/eidolon/blob/master/src/main/java/elucent/eidolon/gui/jei/CrucibleCategory.java) |
| Botania | 部分 | petals、runic_altar、mana infusion、terra plate、brewery 等 | 新增花药台与符文祭坛分组；水、种子/活石、魔力与丢物/激活仍需自动化设施。世界方块转化不属于通用机器 I/O。 | [1.20.x](https://github.com/VazkiiMods/Botania/blob/1.20.x/Xplat/src/main/java/vazkii/botania/client/integration/jei/BreweryRecipeCategory.java) |
| AetherWorks | 版本/适配 | 金属加工与世界设施 | 所给仓库为旧版实现；没有证明其可直接用于三个目标版本。现代复刻需按具体实现核对。 | [master](https://github.com/V0idWa1k3r/Aetherworks/blob/master/src/main/java/v0id/aw/compat/jei/AWPlugin.java) |
| Iron Furnaces | 条件 | 熔炼/高炉/烟熏类 | 所核对 1.20.1 实现；须有 JEI catalyst 与可投料/抽取能力，仍需要燃料或能量。 | [1.20.1](https://github.com/Qelifern/IronFurnaces/blob/1.20.1/src/main/java/ironfurnaces/jei/IronFurnacesJEIPlugin.java) |
| Ars Nouveau | 部分 | 附魔装置及其他魔艺配方 | 保留 reagent/pedestal_items 分组；Source、装置结构、进度或玩家触发不由分组 profile 代办。 | [main](https://github.com/baileyholl/Ars-Nouveau/blob/main/src/main/java/com/hollingsworth/arsnouveau/client/jei/AlakarkinosRecipeCategory.java) |
| Malum | 部分 | spirit_infusion 等 | 保留 spirits/extra_items/input 分组和跨版本成员别名；灵魂资源、祭坛执行条件仍需满足。 | [1.20.1](https://github.com/SammySemicolon/Malum-Mod/blob/1.20.1/src/main/java/com/sammy/malum/compability/jei/HiddenRecipeSet.java) |
| Wizard Terra Curios | 待核验 | 未取得可用配方源码 | 已定位百科关联仓库，但未成功取得源码；不能推断所有配方只是普通合成。 | 未取得可核验源码 |
| Touhou Little Maid | 需适配 | touhou_little_maid:altar | 祭坛含 Power、实体/世界行为；物品布局不代表 Craftlines 可代替玩家和女仆执行。 | [1.20](https://github.com/TartaricAcid/TouhouLittleMaid/blob/1.20/src/main/java/com/github/tartaricacid/touhoulittlemaid/compat/jei/MaidPlugin.java) |
| The Aether | 部分 | enchanting、freezing、incubation、repairing 与世界说明分类 | 使用现代 1.21.1-develop 核对；附魔/冷冻需设施与燃料，禁放规则/方块转化/孵化实体不是普通产品。 | [1.21.1-develop](https://github.com/The-Aether-Team/The-Aether/blob/1.21.1-develop/src/main/java/com/aetherteam/aether/integration/jei/AetherJEIPlugin.java) |
| Farmers Delight | 部分 | 烹饪锅、砧板、普通合成 | 现有 getTool/getOutputContainer 处理刀具与容器；锅需热源，砧板需要能触发切割的真实自动化。 | [1.20](https://github.com/vectorwing/FarmersDelight/blob/1.20/src/main/java/vectorwing/farmersdelight/integration/jei/FDRecipeTypes.java) |
| Youkais Homecoming | 部分 | FD 扩展与自有食物加工 | 继承 FD 的普通配方走相同路径；自有加工、容器和特殊食材要按分类验证，未宣称全部工艺支持。 | [main](https://github.com/Minecraft-LightLand/Youkai-Homecoming/blob/main/src/main/java/dev/xkmc/youkaishomecoming/compat/jei/AbstractCookingCategory.java) |
| Sophisticated Backpacks | 常规 | 原版合成/锻造扩展 | 走真实服务端 Recipe，保留背包组件；物品内部状态和自定义装配结果需要实际输入验证。 | [1.21.x](https://github.com/P3pp3rF1y/SophisticatedBackpacks/blob/1.21.x/src/main/java/net/p3pp3rf1y/sophisticatedbackpacks/compat/recipeviewers/jei/BackpackJeiPlugin.java) |
| Immortalers Delight | 部分 | enchantal_cooler、hot_spring、FD 烹饪 | 冷却器/温泉等需真实设备；继承 FD 的类型不需要另造 JEI UID。 | [master](https://github.com/Renyigesai/immortalers_delight/blob/master/src/main/java/com/renyigesai/immortalers_delight/integration/jei/JEIImmortalersDelightPlugin.java) |
| Apotheosis | 需适配 | salvaging、gem_cutting、smithing，以及重铸玩法 | 已补核对 1.20 源码；随机/区间产物、宝石/词缀与玩家成本不能按固定产量统一承诺。 | [1.20](https://github.com/Shadows-of-Fire/Apotheosis/blob/1.20/src/main/java/dev/shadowsoffire/apotheosis/adventure/compat/AdventureJEIPlugin.java) |
| TaCZ | 需适配 | 动态 gun_smith_table/<blockId>、attachment_query | 枪匠台是 GUI 制造入口，配件查询不是制造配方；不能只绑定一条固定 UID，也不能假定工作台具备自动执行能力。 | [1.20.1](https://github.com/MCModderAnchor/TACZ/blob/1.20.1/src/main/java/com/tacz/guns/compat/jei/GunModPlugin.java) |
| Avaritia | 版本/适配 | 原版极限合成/压缩；另核对 Re-Avaritia | 0.6.0 协议可完整表达 81 个输入槽；不等于已新增 9×9 工作台代理。所给旧原版与 Re-Avaritia 必须区分。 | [master](https://github.com/SpitefulFox/Avaritia/blob/master/README.md) |
| SlashBlade | 部分 | 真实工作台动态组件配方与铁砧等 | 保留已有动态输出 profile 和服务端真实装配；本次所给原仓库默认分支较旧，现代分支/JAR 仍须针对验证。 | [master](https://github.com/flammpfeil/SlashBlade/tree/master) |
| Confluence | 部分 | alchemy table、altar、cooking pot、crystal ball、dye vat 等 | 1.21.1 源码含多种世界台/GUI 类别；armor_set_bonus 等说明分类不能视为可制造产物。 | [neoforge/1.21.1](https://github.com/Magic-team-jvav/confluence/blob/neoforge/1.21.1/ConfluenceOtherworld/src/main/java/org/confluence/mod/integration/jei/ConfluenceScreenHandler.java) |
| Distant Worlds | 待核验 | 取得的仓库仅 README/说明 | 没有足够配方实现证据，不能给出全支持结论。 | [main](https://github.com/TANC57/DistantWorlds/blob/main/README.md) |
| Lychee | 需适配 | item_burning、item_inside、block_interacting 等世界事件 | 需要世界事件、位置、条件与动作执行；本次默认源为 26.1.2 Fabric，不等同于项目的 26.1.2 NeoForge。 | [26.1-fabric](https://github.com/Snownee/Lychee/blob/26.1-fabric/src/main/java/snownee/lychee/compat/recipeviewer/jei/LycheeJEIPlugin.java) |
| Farming for Blockheads | 需适配 | 市场交易目录 | 已补核对 1.20.1；货币、交易动作和 GUI 不应伪装成固定机器合成。 | [1.20.1](https://github.com/TwelveIterations/FarmingForBlockheads/blob/1.20.1/common/src/main/java/net/blay09/mods/farmingforblockheads/compat/jei/JEIAddon.java) |
| Project MMO | 规则 | 技能/经验/合成门控 | 不是额外制造机；不能借自动化绕过玩家权限、等级、进度要求。现代仓库资料也未证明所有门控已接入。 | [master](https://github.com/Harmonised7/project_mmo/tree/master) |
| Crabbers Delight | 常规/部分 | 普通合成及 FD 相关食物加工 | 所核对 NeoForge 1.21.1 分支；继承类型复用对应路径，自有捕蟹/获取行为不视为合成保证。 | [NeoForge-1.21.1](https://github.com/AlabasterLeking/Crabbers-Delight/blob/NeoForge-1.21.1/src/main/java/alabaster/crabbersdelight/integration/jei/CrabTrapRecipeWrapper.java) |
| Metal Barrels | 常规/版本 | 储物桶与普通方块配方 | 所给默认源为 1.16.x；标准合成路径可以复用，但未核对现代移植构件。 | [1.16.x](https://github.com/Tfarcenim/MetalBarrels/tree/1.16.x) |
| Ancient Reforging | 需适配 | Apotheosis 重铸/分解集成 | 复用上游重铸语义，不能靠分组补齐随机词缀或操作成本。 | [1.21.1](https://github.com/ianm1647/ancientreforging/tree/1.21.1) |
| FTB Quests | 规则 | 任务/奖励/进度 | 不作为加工配方源；任务奖励与解锁条件不能被当作机器产物和免费材料。 | [main](https://github.com/FTBTeam/FTB-Quests/blob/main/README.md) |
| FTB Teams | 规则 | 团队基础设施 | 没有需要另建的制造执行器；联网/权限边界仍由相应系统负责。 | [main](https://github.com/FTBTeam/FTB-Teams/blob/main/README.md) |
| Brick Furnace | 条件/版本 | 炉类加工 | 核对到 26.2 源码，不能据此宣称 26.1.2 构件可用；目标版本存在时仍需炉类 I/O 与燃料。 | [26.x](https://github.com/cech12/BrickFurnace/blob/26.x/fabric/src/main/java/de/cech12/brickfurnace/jei/BrickBlastingCategory.java) |
| Majrusz Accessories | 常规/部分 | 饰品及配方相关代码 | 所核对 1.20.X；普通配方可复用，掉落、强化或玩家效果不属于合成链自动生成。 | [1.20.X](https://github.com/Majrusz/MajruszsAccessories/blob/1.20.X/README.md) |
| Reliquary | 部分 | alkahestry 充能/复制、药剂等 | 已补核对 1.21.x；典籍充能/返还、容器与非普通成本需按真实配方或适配器验证。 | [1.21.x](https://github.com/P3pp3rF1y/Reliquary/blob/1.21.x/src/main/java/reliquary/compat/jei/ComponentSubtypeInterpreter.java) |
| MoonStone | 待核验 | 取得 README/少量资料 | 同名模组及历史版本较多；现有证据不足以核验所有工艺。 | [master](https://github.com/YTGLD/MoonsTeams/blob/master/src/main/java/com/moonstone/moonstonemod/event/JeiText.java) |
| Enigmatic Legacy | 部分 | 隐藏/特殊工作台配方 | 1.20.X 中存在 HiddenRecipe；不能把 JEI 可见性或配方存在视为玩家已满足进度/诅咒条件。 | [1.20.X](https://github.com/Extegral/Enigmatic-Legacy/blob/1.20.X/src/main/java/com/aizistral/enigmaticlegacy/handlers/EnigmaticJEIPlugin.java) |
| Enigmatic Addons | 常规/部分 | 特殊合成、酿造/铁砧扩展 | 普通 Recipe 可复用；组件改变与玩家条件仍由原实现决定。 | [main](https://github.com/Auviotre/Enigmatic-Addons/blob/main/src/main/java/auviotre/enigmatic/addon/handlers/AddonJEIPlugin.java) |
| Spice of Life Carrot Edition | 规则 | 饮食多样性/玩家收益 | 不新增通用制造接口；其可合成物品按原配方处理。 | [1.21](https://github.com/Cazsius/Spice-of-Life-Carrot-Edition/blob/1.21/README.md) |
| Diet | 规则 | 营养与玩家状态 | 不把营养状态或食用结果当作合成产物。 | [1.20.x](https://github.com/TheIllusiveC4/Diet/blob/1.20.x/README.md) |
| Mutant Creatures | 版本/适配 | 生物、掉落与转化玩法 | 所给条目不能自动等同于现代 Mutant Monsters；没有核验目标三个版本的原项目制造接口。 | 未取得可核验源码 |
| Thirst Mod | 待核验/版本 | 饮水玩法 | 百科关联旧 Thirst 项目，未取得可用源码；不能替换为同名现代模组后宣称已兼容。 | 未取得可核验源码 |
| Natures Aura | 部分 | tree_ritual、altar、offering、animal_spawner | 已核对 1.21.1；灵气、结构、世界操作和生成实体需要专门设施，不是单纯物品槽。 | [main](https://github.com/Ellpeck/NaturesAura/blob/main/src/main/java/de/ellpeck/naturesaura/compat/jei/AltarCategory.java) |
| Occultism | 部分 | ritual、spirit_fire、crushing、miner 等 | 新增祭坛分组并过滤 getRitualDummy 说明图标；献祭、法阵、召唤与实体工作仍不是通用 I/O。 | [version/1.20.1](https://github.com/klikli-dev/occultism/blob/version/1.20.1/src/main/java/com/klikli_dev/occultism/integration/jei/JeiAccess.java) |
| Ars Ocultas | 部分/待核验 | Ars 与 Occultism 扩展 | 取得 1.21.x 源码；继承配方按父模组处理，未证明新增玩法均可自动执行。 | [1.21.x](https://github.com/mystchonky/Ars-Ocultas/blob/1.21.x/README.md) |
| Irons Spells Spellbooks | 部分/需适配 | 卷轴/墨水/法术相关特殊制作 | 取得 1.21 源码；需区分真实配方、随机输出、玩家法术与 GUI 操作。 | [1.21](https://github.com/iron431/Irons-Spells-n-Spellbooks/blob/1.21/src/main/java/io/redspace/ironsspellbooks/jei/AlchemistCauldronAdvancedHandler.java) |
| Reliquified Artifacts | 规则/常规 | Relics/Artifacts 兼容 | 取得 1.21.1 的兼容代码资料；未发现需要统一新增的制造接口，不代表掉落可合成。 | [1.21.1](https://github.com/Octo-Studios/rar-compat/tree/1.21.1) |
| Potions Master | 部分 | 药剂/材料与配方扩展 | 普通合成/酿造路径复用；现代目标版本及特殊成本仍需对应构件验证。 | [master](https://github.com/thevortex/PotionsMaster/tree/master) |
| Relics | 规则/部分 | 遗物行为、升级与获取 | 普通配方可复用；经验升级、战利品与玩家能力不能被视为固定物品 I/O。 | [1.21.0](https://github.com/SSKirillSS/relics/blob/1.21.0/README.md) |
| Ars Additions | 部分 | Ars 装置/配方扩展 | 取得 1.21.0 源；沿用父模组执行边界，新增分类需实际布局与资源完整性校验。 | [1.21.0](https://github.com/Jarva/Ars-Additions/blob/1.21.0/src/main/java/com/github/jarva/arsadditions/client/jei/CharmChargingRecipeCategory.java) |
| Mahou Tsukai | 待核验/需适配 | 玩家施法、术式等 | 没有取得作者可用配方源码；不能承诺玩家法术可由供给器自动执行。 | 未取得可核验源码 |
| Theurgy | 部分 | incubation、reformation、liquefaction、distillation 等 | 新增孵化汞/硫/盐分组；多输出候选、目标样本、Mercury Flux 与世界阵列仍有专用语义。 | [version/1.20.1](https://github.com/klikli-dev/theurgy/blob/version/1.20.1/src/main/java/com/klikli_dev/theurgy/integration/jei/AlchemicalSulfurSubtypeInterpreter.java) |
| Corail Tombstone | 规则/待核验 | 死亡、灵魂与墓碑行为 | 关联仓库的源码资料有限；普通配方之外的玩家/死亡机制不作为机器自动合成。 | [master](https://github.com/Corail31/tombstone_lite/blob/master/README.md) |
| Ars Creo | 部分 | Ars/Create 联动 | 父模组配方与设备能力可以复用，不代表所有生物/动力行为是配方。 | [main](https://github.com/baileyholl/Ars-Creo/blob/main/README.md) |
| Ars Elemental | 部分 | 魔艺附魔装置升级及法术扩展 | 所核对 1.21.1：有独立 deferred recipe type，不能猜成 Ars 主 UID；未新增猜测映射。 | [1.21](https://github.com/Alexthw46/Ars-Elemental/blob/1.21/src/main/java/alexthw/ars_elemental/recipe/jei/JeiElementalPlugin.java) |
| EvilCraft | 部分/版本 | Blood Infuser、Environmental Accumulator 等 | 所取得 master-1.20 实际为 1.20.4；血液、等级、天气等需满足，不能直接作为 1.20.1 验收证据。 | [master-1.20](https://github.com/CyclopsMC/EvilCraft/blob/master-1.20/README.md) |
| StarbuncleMania | 部分 | Fluid Sourcelink 与 Ars 物流/工人联动 | 1.21 源；父配方路径复用，工人行为、Source 转换须真实设施执行。 | [1.21](https://github.com/Alexthw46/StarbuncleMania/blob/1.21/src/main/java/alexthw/starbunclemania/jei/FluidLinkRecipeCategory.java) |
| Roots Classic | 需适配 | mortar、ritual | 所核对 trunk/1.20；研钵/仪式涉及动作与世界条件，不能把配料槽直接等同于自动执行器。 | [trunk/1.20](https://github.com/Lothrazar/RootsClassic/blob/trunk/1.20/src/main/java/elucent/rootsclassic/compat/jei/JEIPlugin.java) |
| Create | 部分 | 常规加工、多产物与概率副产物 | 核对 1.20.1/1.21.1 ProcessingRecipe；新增概率规则。确定主产物可规划，随机副产物仅回收；动力、热源、序列装配等仍需设施/适配。 | [1.20.1 / 1.21.1 源码](https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/src/main/java/com/simibubi/create/content/processing/recipe/ProcessingRecipe.java) |
| Mekanism | 部分 | 物品/流体/化学品机加工 | 1.20.x 源；BD 必须注册相应化学品 key。逐 tick 消耗、双向反应与随机副产物不能靠展示量统一推算。 | [1.20.x](https://github.com/mekanism/Mekanism/blob/1.20.x/src/api/java/mekanism/api/integration/jei/IMekanismJEIHelper.java) |
| Immersive Engineering | 部分 | 焦炉及多种机加工 | 保留焦炉单位批量/杂酚油映射，新增联合产物能力；能量、多方块与模具成本仍需设备满足。 | [1.20.1](https://github.com/BluSunrize/ImmersiveEngineering/blob/1.20.1/src/api/java/blusunrize/immersiveengineering/api/crafting/IJEIRecipe.java) |
| Ender IO | 部分 | 机器加工、合金/粉碎，以及 fire_crafting 等 | 1.21.1 源；概率、催化剂加成和世界火工艺要区别于确定的机器 I/O。 | [1.21.1](https://github.com/Team-EnderIO/EnderIO/blob/1.21.1/enderio/src/main/java/com/enderio/enderio/compat/jei/EnderIOJEI.java) |
| AE2 | 部分 | inscriber、charger、condenser 与 transform/entropy 等 | 压印器/充能器需真实能力及预置工具；世界转化、熵变工具和物质炮不是普通机器配方。 | [forge/1.20.1](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/1.20.1/src/main/java/appeng/api/integrations/jei/IngredientConverter.java) |
| Tech Reborn | 加载器 | 多种科技机器 | 所核对 26.2 源为 Fabric，与三个目标构建加载器不匹配；其他移植另行核验。 | [26.2](https://github.com/TechReborn/TechReborn/blob/26.2/src/client/java/techreborn/client/compat/jei/JEIPlugin.java) |
| Industrial Foregoing | 部分 | dissolution、laser、fluid extraction 等 | 新增溶解室物品/流体分组；能量、激光权重、环境和生物机器不能统称固定配方。 | [1.20](https://github.com/InnovativeOnlineIndustries/Industrial-Foregoing/blob/1.20/src/main/java/com/buuz135/industrial/plugin/jei/CustomJeiDrawable.java) |
| GTCEu Modern | 需适配 | 能力式 I/O、LDLib/自定义配方布局、多种机加工 | 核对 GregTech-Modern 1.20.1；电路/模具非消耗、概率 Content、电压/等级/洁净室等需专用语义，不能宣称 GT 全配方已支持。 | [1.20.1](https://github.com/GregTechCEu/GregTech-Modern/blob/1.20.1/src/main/java/com/gregtechceu/gtceu/core/mixins/jei/FluidHelperMixin.java) |
| PneumaticCraft | 部分 | pressure_chamber、assembly、fluid_mixer、refinery、thermo_plant 等 | 普通槽位可读，但压力/温度/程序/多机器装配与 Amadron 交易须单独处理。 | [1.20.1](https://github.com/TeamPneumatic/pnc-repressurized/blob/1.20.1/src/main/java/me/desht/pneumaticcraft/common/thirdparty/jei/AbstractPNCCategory.java) |

## 封包扩展对应的原模组

此处核对原配方，不把封包机当作新的配方 family 来源，也没有新增失效的服务端 alias 表。

| 封包/扩展 | 对应原模组/系统 | 结论与边界 | 对照源码 |
|---|---|---|---|
| PackagedExCrafting | Extended Crafting | 大网格桌、Combination、Ender/Flux 等；协议容量已扩大，桌面形状与实际自动执行能力仍需原设备/适配。 | [1.20](https://github.com/TheLMiffy1111/PackagedExCrafting/blob/1.20/README.md) |
| PackagedAvaritia | Avaritia / 对应现代移植 | 不能把包内 extreme 配方 UID 当作所有 Avaritia 版本的原生 UID；另核对 Re-Avaritia 1.20.1。 | [1.18.2](https://github.com/TheLMiffy1111/PackagedAvaritia/blob/1.18.2/README.md) |
| Dynamistics | AE2 + PackagedAuto 的动态样板/过程展示 | 它不是某个额外魔法模组的封包版；玩家样板与包装过程不自动成为权威机器配方。 | [master](https://github.com/eutro/dynamistics/blob/master/src/main/java/eutros/dynamistics/DynamisticsJEIPlugin.java) |
| PackagedAstral | Astral Sorcery | 取得封包 1.12、原模组 1.16-indev 源；不在项目三个 MC 版本中，星能/星座/仪式还需执行器。 | [1.12](https://github.com/TheLMiffy1111/PackagedAstral/blob/1.12/README.md) |
| PackagedDraconic | Draconic Evolution | 另核对原模组 1.21 融合配方；核心、注入器、能量和等级需真实自动化，不支持仅凭封包名称映射。 | [1.12](https://github.com/TheLMiffy1111/PackagedDraconic/blob/1.12/README.md) |
| PackagedMekemicals | Mekanism 化学品 | 核对 1.20 封包源；核心问题是 BD 化学品资源键与真实机器能力，包内容展示不等于化学反应配方。 | [1.20](https://github.com/TheLMiffy1111/PackagedMekemicals/blob/1.20/src/main/java/thelm/packagedmekemicals/integration/jei/ChemicalPackageContentsCategory.java) |
| PackagedThaumic | Thaumcraft | 封包源为 1.12；原版研究、要素、注魔等不是三个当前 MC 构建的现成支持范围。 | [1.12](https://github.com/TheLMiffy1111/PackagedThaumic/blob/1.12/README.md) |
| PackagedBotania | Botania | 原料花药台、符文祭坛、魔力池、精灵交易、酿造台、泰拉凝聚板；继承 Botania 对应限制。 | [main](https://github.com/NNYYOONNIIOO/PackagedBotania/blob/main/src/main/java/nyonio/packagedbotania/integration/jei/PackagedBotaniaJEIPlugin.java) |
| Packaged Fluid Crafting | AE2 Fluid Crafting | README 明确指向 AE2 Fluid Crafting，不是一个泛称 Fluid Crafting 的新模组；所核对封包为 1.12.2。 | [1.12.2](https://github.com/TheLMiffy1111/PackagedFluidCrafting/blob/1.12.2/README.md) |

Extended Crafting 原版工艺另见 [1.20 源码](https://github.com/BlakeBr0/ExtendedCrafting/tree/1.20)，Draconic Evolution 另见 [1.21 源码](https://github.com/Draconic-Inc/Draconic-Evolution/tree/1.21)，Astral Sorcery 另见 [1.16-indev 源码](https://github.com/HellFirePvP/AstralSorcery/tree/1.16-indev)。这不构成上述模组三个目标 MC 版本全部有发行构件的证明。

## 新增分组的核对依据

| JEI UID | 配方类 / 实际 INPUT 顺序 | 配置文件 | 核对源码 |
|---|---|---|---|
| `embers:alchemy` | `com.rekindled.embers.recipe.AlchemyRecipeBase` 的实现；`getCenterInput()` 单槽，然后 `getInputs()` 集合 | `embers_alchemy.json` | [AlchemyCategory](https://github.com/RCXcrafter/EmbersRekindled/blob/rekindled/src/main/java/com/rekindled/embers/compat/jei/AlchemyCategory.java) |
| `occultism:ritual` | `com.klikli_dev.occultism.crafting.recipe.RitualRecipe`；`getActivationItem()`，然后 `getIngredients()` | `occultism_ritual.json` | [RitualRecipeCategory](https://github.com/klikli-dev/occultism/blob/version/1.20.1/src/main/java/com/klikli_dev/occultism/integration/jei/recipes/RitualRecipeCategory.java) |
| `botania:petals` | `vazkii.botania.common.crafting.PetalsRecipe`；固定水槽、`getReagent()`、`getIngredients()` | `botania_petals.json` | [PetalApothecaryRecipeCategory](https://github.com/VazkiiMods/Botania/blob/1.20.x/Xplat/src/main/java/vazkii/botania/client/integration/jei/PetalApothecaryRecipeCategory.java) |
| `botania:runic_altar` | `vazkii.botania.common.crafting.RunicAltarRecipe`；固定活石槽，然后 `getIngredients()` | `botania_runic_altar.json` | [RunicAltarRecipeCategory](https://github.com/VazkiiMods/Botania/blob/1.20.x/Xplat/src/main/java/vazkii/botania/client/integration/jei/RunicAltarRecipeCategory.java) |
| `theurgy:incubation` | `com.klikli_dev.theurgy.content.recipe.IncubationRecipe`；`getMercury()`、`getSulfur()`、`getSalt()` | `theurgy_incubation.json` | [IncubationCategory](https://github.com/klikli-dev/theurgy/blob/version/1.20.1/src/main/java/com/klikli_dev/theurgy/integration/jei/recipes/IncubationCategory.java) |
| `industrialforegoing:dissolution` | `com.buuz135.industrial.recipe.DissolutionChamberRecipe`；`input` 数组，然后存在时的 `inputFluid` | `industrialforegoing_dissolution.json` | [DissolutionChamberCategory](https://github.com/InnovativeOnlineIndustries/Industrial-Foregoing/blob/1.20/src/main/java/com/buuz135/industrial/plugin/jei/category/DissolutionChamberCategory.java) |

分组不额外生成祭坛条件、魔力、压力或实体。Embers 的 aspectus、Occultism 的献祭/激活动作、Botania 的魔力/水与完成动作仍需真实装置处理。Theurgy 输出若是没有 focus link 的多候选列表，会按通用完整性规则拒绝，不因存在 profile 而放宽。

## 整合包作者扩展

资源包继续覆盖同路径文件。`jei_type` 为精确 UID，`jei_type_prefixes` 是可选的明确前缀列表；不要用宽泛前缀混合不同槽位布局。`recipe_classes` 支持声明的父类，INPUT 槽数量必须与各 section 总数相符。CATALYST/CRAFTING_STATION 与 RENDER_ONLY 不计入普通 INPUT sections。

```json
{
  "jei_type": "example:altar",
  "recipe_classes": ["example.recipes.AltarRecipe"],
  "input_sections": [
    {"group": "activation_item", "cardinality": "fixed", "count": 1},
    {"group": "offerings", "cardinality": "collection", "members": ["getOfferings"]}
  ]
}
```

只有上游源码证明存在该固定槽时才使用 `fixed`，不能依据视觉坐标猜材料用途。概率与说明产物策略位于服务端数据包 `data/beyond_craftlines/recipe_io_profiles`，仍经现有 profile 同步下发；这不改变输入分组位于 assets 的约定。

## 验证范围

自动回归覆盖联动槽组合/重叠/预算、81 槽完整性、概率单位/配置、分组实际创建顺序、联合固体/流体需求、概率副产物不计入保证需求、部分批次保留副产物、相同 ID 的资源会话失效。Minecraft 1.20.1 和 1.21.1 的纯单测环境不包含完整游戏运行时，少数真实 MC 类型测试按已有约定跳过；26.1.2 的 NeoForge 单测运行时执行额外目录/联合规划测试。

游戏内仍需按具体整合包验证：设备投料面、拒收/回滚、同单联合产物、取消返还、重启恢复、区块卸载、网络容量不足、热源/压力/魔力缺失、非消耗工具、随机产物与 GUI/世界动作。没有把这些尚未运行的场景标成通过。

本次最终构建结果（离线，分别使用 JDK 17 / 21 / 25）：

| Minecraft / 加载器 | 构建 | 自动测试 | 跳过 | JAR |
|---|---|---:|---:|---|
| 1.20.1 Forge | 成功 | 212 通过 | 3 | `versions/1.20.1/build/libs/beyond_craftlines-0.6.0+1.20.1.jar` |
| 1.21.1 NeoForge | 成功 | 212 通过 | 3 | `build/libs/beyond_craftlines-0.6.0+1.21.1.jar` |
| 26.1.2 NeoForge | 成功 | 220 通过 | 0 | `versions/26.1.2/build/libs/beyond_craftlines-0.6.0+26.1.2.jar` |

三个 JAR 均已检查六个新增输入分组、Create 概率/Occultism 展示过滤配置、JEI Mixin 类及加载声明；26.1.2 的配置仅含 JEI 桥接，不含 EMI。
