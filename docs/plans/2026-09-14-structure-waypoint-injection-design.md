# 传送锚点：结构生成机制替代 NBT 覆盖 设计文档

**日期:** 2026-09-14
**状态:** 待评审
**Mod ID:** `teleportwaypoint`
**Minecraft / NeoForge:** 1.21.1 / NeoForge 21.1.236+
**前序文档:**
- `docs/plans/2026-08-19-vanilla-structure-waypoint-datapack-design.md`
- `docs/plans/2026-08-19-yung-structure-waypoint-datapack-design.md`

---

## 1. Problem Statement

### 1.1 现状

本模组当前通过「内置可选数据包整份覆盖结构模板 NBT」把 `teleportwaypoint:waypoint` 方块植入结构：

| 项目 | 位置 / 数值 |
|---|---|
| 数据包目录 | `src/main/resources/data/teleportwaypoint/datapacks/{vanilla,yung}_structure_waypoints/` |
| 注册入口 | `src/main/java/com/zonlong/teleportwaypoint/datapack/DatapackRegistration.java:26-63` |
| 开关 | `config/CommonConfig.java:11-12,23-36` 的 `optionalDataPacks` 分组 |
| 规模 | 30 个 NBT（原版 12 + YUNG 18，跨 6 个 namespace） |
| 体积 | 549.6 KB 原始 / 251.5 KB 压缩，占 440.3 KB jar 的 **57%** |

### 1.2 问题

逐方块与原版 client.jar 比对（2026-09-14 实测）后确认三类问题：

1. **工作量随结构数量线性增长**：每新增一个结构都要手工制作完整模板。
2. **模板被大规模重建而非最小修改**：12 个原版模板中 10 个存在与锚点无关的改动（`ancient_city/city_center_1` 414 处、`trial_chambers/corridor/end_2` 180 处、`bastion/bridge/starting_pieces/entrance_base` 31 处等），方块总数与原版不符；仅 `igloo/top` 与 `woodland_mansion/entrance` 是「原版 + 1 个锚点」的纯净改法。
3. **普适性低**：因使用 `Pack.Position.TOP`（`DatapackRegistration.java:62`），任何其他修改同一模板的数据包都会被完全覆盖；对纯硬编码结构无 NBT 可覆盖，只能绕道 YUNG 兼容包。

另有两个附带缺陷：

- 模板内嵌**固定 `uid`**（30 个 NBT 全部如此），导致每个结构实例都触发 `ensureUniqueUid` 的冲突重roll（`WaypointManager.java:339-346`），该逻辑存在的唯一原因就是这个设计缺陷。
- `PackSource.BUILT_IN` 会在**既有存档**中自动启用（`MinecraftServer.java:1581`，`PackSource.java:19-31`），玩家无法预防。

### 1.3 目标

| # | 目标 |
|---|---|
| 1 | 能往结构里添加本模组锚点方块，覆盖原版 / 模组 / 尽可能含硬编码结构 |
| 2 | 放置位置可控，采用「统一规则 + 少量配置」而非逐结构手填坐标 |
| 3 | 锚点名称由结构 ID 推导到翻译键，翻译键人工填写 |
| 4 | 对其他模组结构的兼容必须比 NBT 覆盖简单 |
| 5 | 零 Mixin |

### 1.4 非目标

- 不引入 Mixin、AccessTransformer。
- 不修改原版传送/战斗机制。
- 不做旧存档结构回溯（沿用 `2026-08-19-vanilla-structure-waypoint-datapack-design.md:231` 的既有非目标），但提供可选开关。

---

## 2. 已确认的引擎事实（NeoForge 21.1.236 / MC 1.21.1）

源码树：`D:\Minecraft\BeLoong-Core\build\nf-src`。

### 2.1 不存在可用的「结构生成事件」

- NeoForge 1.21.1 **没有任何结构放置/生成事件**。`net/neoforged/neoforge/event` 下唯一与结构同名的是 `RegisterStructureConversionsEvent`（1.18.2 之前存档升级的 datafixer）。
- `StructureModifier` **只能**改 4 个字段（`StructureSettingsBuilder.java:26-29`：`biomes` / `spawnOverrides` / `step` / `terrainAdaptation`），`modify(Holder<Structure>, Phase, StructureInfo.Builder)`（`StructureModifier.java:65`）签名中无 `Level`、无坐标，**结构性地不可能放方块**，且只在服务端启动时执行一次（`ServerLifecycleHooks.java:214-216`）。该路线已排除。
- `PostPlacementProcessor` 在 1.21.1 是死代码（零引用）。`IStructure`、`Structure#getStructureModifier()` 均不存在。

### 2.2 两个真实可用的注入点

#### 注入点 1：`StructureProcessor`（数据包驱动）

- `StructureProcessor.java:29` `protected abstract StructureProcessorType<?> getType()`；`:31-40` `finalizeProcessing(...)`；`:42-45` 7 参 `process(...)`。
- `StructureTemplate.processBlockInfos`（`:414-443`）：先对模板调色板做单遍 `process`（`:418-436`，返回 `null` 即丢弃），随后每个处理器调用一次 `finalizeProcessing`（`:438-440`），返回值被 `placeInWorld` **原样消费**（`:248`）。**无任何校验、去重、排序、包围盒过滤。**
- `process` **无法新增位置**（输入恒为模板已有条目）；`finalizeProcessing` **可以追加新位置**。
- `placeInWorld`（`:248-278`）逐条放置：仅当 `boundingbox == null || boundingbox.isInside(blockpos)` 才放置（`:252`）；状态在此处**再次**镜像+旋转（`:254`）；`nbt != null` 时先 `Clearable.tryClear` + 置 `BARRIER`（`:255-259`），`setBlock` 后 `BlockEntity.loadWithComponents(nbt, registryAccess())`（`:269-277`）。
- 包围盒 = **当前区块可写区域**（`ChunkGenerator.getWritableArea:415-423`，`new BoundingBox(x, minBuildHeight+1, z, x+15, maxBuildHeight-1, z+15)`），在 `SinglePoolElement.java:176` 设置。
- 运行次数 = **每 piece × 每个相交区块 × 2**（`SinglePoolElement.place:164-166` 会为数据标记再跑一遍）。
- 追加方块会使下游 `capped` 处理器失效（`CappedProcessor.java:47-54` 检测到 list 尺寸变化即跳过，并经 `Util.logAndPauseIfInIde` **在调试器下暂停游戏**）→ 追加型处理器必须排在最后。
- 数据包注入面**仅**模板池元素的 `processors` 字段（`SinglePoolElement.java:50,60-62`，`getSettings:174-190` 经 `:187` 生效）。硬编码结构在 Java 内自建放置设置（`EndCityPieces.java:364`、`IglooPieces.java:82`、`NetherFossilPieces.java:57`、`OceanRuinPieces.java:286-288`、`RuinedPortalPiece.java:130-136`、`ShipwreckPieces.java:114`、`WoodlandMansionPieces.java:1295`），**无数据包钩子**。
- **处理器拿不到结构 ID**：7 参 `process` 的 `StructureTemplate` 只持有 `palettes`/`entityInfoList`/`author`（`StructureTemplate.java:66-67,79`）；`StructurePlaceSettings.java:14-27` 亦无结构标识；`finalizeProcessing` 连 template 都拿不到。

#### 注入点 2：`ChunkEvent.Load`（NeoForge 事件）

- `ChunkEvent.java:55-74`：`getChunk()` 返回 `ChunkAccess`（`:38`）；`isNewChunk()`（`:71`）仅对新生成区块为 `true`（磁盘加载的 full 区块是 `ImposterProtoChunk`，`ChunkSerializer.java:249`；客户端恒为 `false`，`:67`）。
- 触发点 `ChunkStatusTasks.java:215`，位于 `CompletableFuture.supplyAsync(..., p -> worldGenContext.mainThreadMailBox().tell(...))`（`:191,:221`）→ 经 `ChunkMap.java:192,200` → `ServerChunkCache.MainThreadExecutor`（`:83,88-94`）→ `MinecraftServer.pollTaskInternal`（`:857-877`）**服务端主线程**。
- 关键时序（`:204-218`）：`setLoaded(true)`（`:210`）→ `currentlyLoading = levelchunk`（Neo 补丁，注释「bypass the future chain when getChunk is called, this prevents deadlocks」）→ `registerAllBlockEntitiesAfterLevelLoad()`（`:213`）→ `registerTickContainerInLevel()`（`:214`）→ 发事件（`:215`）。
- **`LevelChunk` 构造时复制结构数据**：`LevelChunk.java:139-140` `setAllStarts(chunk.getAllStarts())` / `setAllReferences(chunk.getAllReferences())`。
- **`getAllStarts()` 只包含「起始区块」**：`ChunkGenerator.java:555-559` 只在放置选中的区块调用 `setStartForStructure`。结构跨越的其他区块只有 `getAllReferences()`（`ChunkGenerator.java:571-604`，±8 区块扫描，值为**起始区块**的 `ChunkPos` long）。
- 事件 javadoc（`:47`）明确警告：事件可能在 `LevelChunk` 升级为 `ChunkStatus.FULL` 之前触发，**不当的 level 交互会导致区块加载死锁**；事件亦在客户端触发（`ClientChunkCache.java:127`）。

### 2.3 `BlockEntity.onLoad()` 只在主线程（一处重要更正）

- `BlockEntity.onLoad()` 在整个 `net/minecraft` 树中**只有一个调用点**：`Level.java:581`，位于 `tickBlockEntities()`（`:568-585`），由服务端主线程每 tick 调用。
- `LevelChunk.registerAllBlockEntitiesAfterLevelLoad()`（`:624-633`）只做 `level.addFreshBlockEntities(...)` + 更新 ticker + 注册游戏事件监听，**不调用 `onLoad()`**；`LevelChunk.setBlockEntity`（`:384-409`）也不调用。
- **结论**：两条路线下 `WaypointBlockEntity.onLoad()` → `WaypointManager.register()` → 网络广播，**都发生在服务端主线程的方块实体 tick 阶段**。引擎天然把 `onLoad` 延迟到主线程，不存在「世界生成线程发网络包」的隐患。
- 推论：**不要在世界生成期直接调用 `register()`**——世界生成期创建的 BE `level == null`（`ProtoChunk.setBlockEntity:171-173` 不设置 level），`register` 会在 `WaypointManager.java:36-38` 直接返回。正确做法是只写方块 + NBT，交给一 tick 后的 `onLoad`。

### 2.4 其他决定性事实

| 事实 | 证据 |
|---|---|
| 内建注册表可被模组 `DeferredRegister` 注册 | `DeferredRegister.create(Registry<T>, String)` 存在（`DeferredRegister.java:97`）；`BuiltInRegistries.STRUCTURE_PROCESSOR` 是普通 `Registry`（`:247-249`）；NeoForge 对每个内建注册表都触发 `RegisterEvent`（`CommonModLoader.java:51-57`、`GameData.java:63-66,80-96`） |
| 数据包注册表解析失败是**硬失败** | `RegistryDataLoader.java:152-154` `throw new IllegalStateException("Failed to load registries due to above errors")` |
| 当前 NBT 覆盖路径失败是**静默**的 | `StructureTemplateManager.java:102-114` 吞异常，`getOrCreate:88-90` 返回空模板 |
| `PROCESSOR_LIST` 不下发客户端 | 不在 `SYNCHRONIZED_REGISTRIES`（`RegistryDataLoader.java:103-115`） |
| 原版 `ProcessorRule` 可挂 BE NBT | `ProcessorRule.java:32,71`；`AppendStatic.java:11-23` 提供 `{"type":"minecraft:append_static","data":{...}}` |
| 结构 ID → 原版翻译键有现成 API | `Util.makeDescriptionId("structure", id)`（`Util.java:127-131`）→ `structure.<ns>.<path>` |
| `ServerLevel.getStructureManager()` 返回的是 **StructureTemplateManager**，不是 `StructureManager` | `ServerLevel.java:1254-1256`；`StructureManager` 在 `ServerLevel.java:329-331` |
| 结构 ID 在世界生成期**短暂可得**，但被私有化 | `ChunkGenerator.java:351-357` `level.setCurrentlyGenerating(...)` → `WorldGenRegion.currentlyGenerating`（`:77,102-103`），私有、无 getter，仅用于错误信息（`:269`）⇒ 只能靠反射/AT/Mixin |

### 2.5 本模组的渲染净空（一处更正）

外勤组曾按「约 6 格」传递需求，实测**不成立**：

| 文件 | 最高元素 | 折算 |
|---|---|---|
| `models/block/waypoint_orb.json` | `to[1] = 15.75` | 0.984 |
| `models/block/waypoint_caps_*.json` | `to[1] = 14` | 0.875 |
| `models/block/waypoint_crystal.json` | `to[1] = 13` | 0.8125 |
| `models/block/waypoint.json`（静态层） | `to[1] = 10` | 0.625 |

BER 动画变换（`WaypointBlockEntityRenderer.java:147-152`）：`translate(0.5, 0.5±0.125, 0.5) · scale(≤1.08) · translate(-0.5,-0.5,-0.5)` ⇒ `y' = 0.5 + 0.125 + 1.08·(0.984375−0.5) = 1.148`。

**结论：整个锚点视觉高度约 1.15 格，恰好占一个方块。** 选点净空取 2 格（可配置 0–6）已足够且有裕量。

---

## 3. 路线对比与结论

### 3.1 覆盖率矩阵（34 个原版结构 JSON，枚举自 client-extra jar）

| 类别 | 数量 | 成员 | 路线甲（处理器） | 路线乙（ChunkEvent.Load） |
|---|---|---|---|---|
| **Jigsaw** | 10 | `ancient_city`、`bastion_remnant`、`pillager_outpost`、`trail_ruins`、`trial_chambers`、`village_{desert,plains,savanna,snowy,taiga}` | ✅ 可达 | ✅ 可达 |
| **模板型硬编码**（处理器链烧死在 Java） | 15 | `end_city`、`igloo`、`mansion`、`nether_fossil`、`ocean_ruin`×2、`ruined_portal`×7、`shipwreck`×2 | ❌ **数据包够不着** | ✅ 可达 |
| **纯硬编码**（无模板） | 9 | `buried_treasure`、`desert_pyramid`、`fortress`、`jungle_pyramid`、`mineshaft`×2、`monument`、`stronghold`、`swamp_hut` | ❌ 不可达 | ✅ 可达 |
| **模组 Jigsaw 结构** | — | YUNG 系列等 | ⚠️ 需逐结构配置 | ✅ 可达，零配置 |

### 3.2 对抗性验证揭示的三个关键问题

**问题 1（最严重）：路线甲单独实施是覆盖回退，不是超集。**

现数据包的 `vanilla_structure_waypoints` 覆盖 6 个结构族，其中 **`end_city`、`igloo`、`woodland_mansion` 3 族是 `TemplateStructurePiece` 型，没有模板池 JSON**，数据包无法附着处理器 ⇒ **路线甲无法复现当前已覆盖的 3/6**。

**问题 2：路线甲的选点必须用 `pos` 锚点，不能用包围盒。**

`place()` 每（piece × 相交区块）调用一次，**每次包围盒都不同**，任何由 `settings.getBoundingBox()` 推导的位置（`getCenter()`、`minX+8`…）会在每个重叠区块列产出**不同的绝对位置** ⇒ 一个 2×2 区块的房屋最多 4 个锚点。按 piece 锚定则会得到每 piece 一个 ⇒ 数十个。

**可用的确定性锚点已经存在**：`StructureStart.placeInChunk:86-88` 由 `list.get(0)` 计算
```java
BlockPos blockpos1 = new BlockPos(firstPiece.bbox.centerX, firstPiece.bbox.minY, firstPiece.bbox.centerZ);
```
并作为 `pos` 参数贯穿传入 `StructureProcessor.process(...)` / `processBlockInfos(...)`。它**对同一结构的每个 piece、每次区块遍历都完全相同**，且位于第一个 piece 的包围盒内 ⇒ 无条件在该位置追加，恰好产出每结构一个方块，且不会被丢弃。

**问题 3：两条路线与现有数据包会重复放置，且模组数据模型没有「结构实例」标识。**

`WaypointRecord` 是 `(uid, dimension, pos, pocket, name)`（`WaypointRecord.java:13`），`WaypointRegistryData` 是 `Map<UUID, WaypointRecord>`，`PlayerWaypointData` 是玩家→uid 集合。**无处存放「这个结构实例已处理」。** 而 `PackSource.BUILT_IN` 会在既有存档自动启用旧数据包 ⇒ 两套机制同时生效即双份锚点。

**解法：用锚点方块自身作为去重标记**，并在对方即将放置的位置做邻近检查 —— 见 §5.4。

### 3.3 结论

**推荐架构 = 路线乙为主 + 路线甲可选 + 保留少量 NBT 覆盖。**

| 层 | 机制 | 覆盖 |
|---|---|---|
| **主干** | 路线乙：`ChunkEvent.Load` + 起始区块放置 | 全部结构（原版 / 模组 / 硬编码），模组结构**零配置** |
| **可选精修** | 路线甲：自定义 `StructureProcessor` + 处理器列表数据包 | 允许为精选结构指定精确位置，并为模组包作者提供 3 行 JSON 的接入方式 |
| **遗留兜底** | 保留 `end_city` / `igloo` / `woodland_mansion` 三个 NBT 覆盖（或接受路线乙的通用规则） | 见 §9.3 |

理由：

- 需求 1（含硬编码）与需求 4（模组结构尽量简单）**只有路线乙能同时满足**，且是零逐结构工作量。
- 需求 2 已明确选择「统一规则 + 少量配置」，路线乙的规则化选点正好符合；路线甲作为精修补充。
- 需求 3 的命名，路线乙可自动推导到原版翻译键（`Util.makeDescriptionId`），路线甲必须由 JSON 显式提供 —— 两者最终汇合到同一个 `waypoint_id` 字段，作者只需写一份翻译键。
- 唯一代价是路线乙的**性能纪律**（每个区块加载都触发）与**只能在起始区块内落点**，两者都有明确对策（§5.3、§7.4）。

---

## 4. 路线甲：自定义 `StructureProcessor`（可选精修层）

### 4.1 可行性判定

| 能力 | 结论 |
|---|---|
| 追加全新方块位置 | ✅ 仅 `finalizeProcessing` 可做到；返回值被原样消费 |
| 学习结构身份 | ❌ API 不可达 ⇒ `waypoint_id` 必须由 JSON 提供 |
| 覆盖面 | ⚠️ 仅 Jigsaw 结构（10/34 原版 + 全部模组 jigsaw） |
| 触碰模板型硬编码 | ❌ 无数据包钩子 |
| 触碰纯硬编码 | ❌ 无 |

### 4.2 组件

| 文件 | 作用 |
|---|---|
| `worldgen/WaypointStructureProcessor.java`（新） | `extends StructureProcessor`，实现 `getType()`，`process` 直通，`finalizeProcessing` 追加 |
| `worldgen/ModStructureProcessors.java`（新） | `DeferredRegister<StructureProcessorType<?>>` on `BuiltInRegistries.STRUCTURE_PROCESSOR` |
| `data/teleportwaypoint/worldgen/processor_list/waypoint_<family>.json`（新，**常驻**） | 本模组自己的处理器列表 |
| `data/teleportwaypoint/datapacks/<pack>/data/<ns>/worldgen/processor_list/*.json`（新，可选开关） | Tier-1 遮蔽 |
| `data/teleportwaypoint/datapacks/<pack>/data/<ns>/worldgen/template_pool/*.json`（新，可选开关） | Tier-2 池覆盖 |
| `TeleportWaypoint.java:27-31` 附近（改） | `ModStructureProcessors.PROCESSORS.register(modEventBus);` |

注册形状（沿用项目既有 `DeferredRegister` 风格）：

```java
public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
        DeferredRegister.create(BuiltInRegistries.STRUCTURE_PROCESSOR, TeleportWaypoint.MODID);
public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<WaypointStructureProcessor>> WAYPOINT =
        PROCESSORS.register("waypoint", () -> WaypointStructureProcessor.CODEC);
```

`MapCodec`（`RecordCodecBuilder.mapCodec`，全部字段可选，故 `{"processor_type":"teleportwaypoint:waypoint"}` 合法）：

| 字段 | 类型/范围 | 默认 |
|---|---|---|
| `waypoint_id` | string | `"empty"` |
| `search_radius` | int 0–6 | 2 |
| `vertical_clearance` | int 0–6 | **2**（依 §2.5 更正） |
| `allow_water` | bool | false |
| `require_solid_floor` | bool | true |
| `y_offset` | int −4–8 | 0 |
| `log_rejections` | bool | false |

**作者面向的全部接口**就是一个对象：

```json
{ "processor_type": "teleportwaypoint:waypoint", "waypoint_id": "betterdeserttemples" }
```

### 4.3 选点算法（必须以 `pos` 为锚）

```
finalizeProcessing(level, offset, pos, original, processed, settings):
  if (processed.isEmpty()) return processed;          // 门 0：数据标记遍为空（§4.5 G1）

  R = searchRadius; xs = pos.getX(); zs = pos.getZ(); floors = []
  for (info : processed) {
      if (|info.x - xs| > R || |info.z - zs| > R) continue
      st = info.state
      if (st 是 air/STRUCTURE_VOID/JIGSAW/STRUCTURE_BLOCK/LIGHT/BARRIER) continue
      if (!st.getFluidState().isEmpty()) continue
      if (requireSolidFloor && !st.isFaceSturdy(level, info.pos, UP)) continue
      floors.add(info)
  }
  if (floors.isEmpty()) { 记 DEBUG; return processed }
  floors.sort(by y desc, then x asc, then z asc)       // 全序：抗单条差异导致的重排

  bbox = settings.getBoundingBox()
  for (f : floors) {
      c = (f.x, f.y + 1 + yOffset, f.z)
      if (bbox != null && !bbox.isInside(c)) continue  // == placeInWorld:252，保证落在本区块
      if (c.y <= level.getMinBuildHeight() || c.y >= level.getMaxBuildHeight()) continue
      if (containsPos(processed, c)) continue            // 单次调用内幂等
      if (requireSolidFloor && !level.getBlockState(c.below()).isFaceSturdy(...)) continue
      s0 = level.getBlockState(c)
      if (!(s0.isAir() || s0.canBeReplaced())) continue
      if (!allowWater && !s0.getFluidState().isEmpty()) continue
      for (k = 1..verticalClearance)
          if (第 k 格不满足 air/replaceable 或含水) 放弃该候选
      if (该位置已是 teleportwaypoint:waypoint) continue // 兜底
      tag = { "waypoint_id": waypointId }
      return processed + [StructureBlockInfo(c, waypoint, tag)]
  }
  记 DEBUG; return processed
```

**关键纪律**

1. **必须无条件使用 `pos` 作为锚点**（§3.2 问题 2）。这是「每结构恰好一个」的唯一来源，无需任何跨调用状态。
2. **不许写 `uid`**。`loadAdditional:199-201` 会读它，随后 `ensureUniqueUid:339-346` 会为每个后续实例重 roll。省略即可让 `getUid():54-60` 惰性生成唯一 UUID。
3. **不许写 `id`/`name`/`owner`/`x`/`y`/`z`**。`id` 不被 `loadAdditional` 读取（`BlockEntity.java:77-88`）；`name`/`owner` 是口袋锚点专用（`WaypointBlockEntity.java:182-188,202-207`）。
4. **绝不抛异常**。世界生成期抛出会变成 `CrashReport`/`ReportedException` "Feature placement"（`ChunkGenerator.java:359-363`）。所有失败路径返回 `processed` 原值。
5. **绝不调用任何触发 `setChanged()` 的 API**（`getUid`/`setId`/`setName`/`setOwner`/`regenerateUid`）。`BlockEntity.setChanged()`（`BlockEntity.java:191-202`）→ `Level.blockEntityChanged`（`Level.java:984-988`）→ `getChunkAt` → `WorldGenRegion.getChunk` 会**抛异常**（`WorldGenRegion.java:129-143`）。
6. **处理器必须无状态**。它是注册表单例，被并发的世界生成工作线程共享，且每 piece 每区块调用 2 次。不得缓存、不得有可变字段。
7. **不许在世界生成期读当前区块外的方块**。中心区块处于 FEATURES，而 `ChunkPyramid.java:35-41` 只保证半径 1 有 CARVERS、半径 8 有 STRUCTURE_STARTS ⇒ 半径 ≥2 的区块**没有地形**，读取会触发 `"Requested chunk unavailable during world generation"`。
8. **处理器必须排在列表最后**（§2.2 的 `capped` 约束）。

### 4.4 数据流（服务端 / 世界生成工作线程 / FEATURES）

```
ChunkGenerator.applyBiomeDecoration:321-358
└─ StructureStart.placeInChunk:81-98            （box = getWritableArea:415-423）
   └─ PoolElementStructurePiece.postProcess → SinglePoolElement.place:145-172
      └─ getSettings:174-190  构建 [BlockIgnore, JigsawReplacement?, …数据包列表…, …projection…]
         └─ StructureTemplate.placeInWorld:226-292
            ├─ processBlockInfos:414-443   →  我们的 finalizeProcessing 追加条目
            ├─ :252 包围盒过滤（保证落本区块）
            ├─ :254 再次镜像+旋转（锚点方块无方向属性 ⇒ 无影响）
            ├─ :255-259 Clearable.tryClear + 置 BARRIER
            ├─ :261 WorldGenRegion.setBlock:276-312
            │        区块为 PROTOCHUNK ⇒ 只写 {"id":"DUMMY",x,y,z}（:294-301）
            └─ :270 getBlockEntity → WorldGenRegion:210-241 DUMMY 路径（:218-232）构造真实 BE
                      → :276 loadWithComponents → WaypointBlockEntity.loadAdditional → 写入 waypoint_id

（随后 FEATURES → LIGHT → SPAWN → FULL，主线程）
ChunkStatusTasks.full:193-213  →  LevelChunk 构造（BE 复制 :129-131）→ registerAllBlockEntitiesAfterLevelLoad:624-633
下一 tick：Level.tickBlockEntities:568-585 → onLoad → WaypointManager.register
```

**结论：追加条目的 NBT 能正确抵达 BE 的 `waypoint_id`**，因为 `loadWithComponents` 发生在 `onLoad` 之前。

### 4.5 双重放置的三重保险

- **G1（确定，非启发式）**：数据标记遍必然为空。`SinglePoolElement.place:164-166` 第二次调用的输入只有 `Blocks.STRUCTURE_BLOCK` 的 DATA 标记（`getDataMarkers:94-114`），而链首的 `BlockIgnoreProcessor.STRUCTURE_BLOCK`（`:180`，实现 `:20,39`）会在 `process` 阶段删除全部标记 ⇒ `finalizeProcessing` 收到空列表 ⇒ 门 0 命中。且该遍的返回值只喂给 `handleDataMarker`（`:167`），而 `StructurePoolElement.handleDataMarker:69-77` 是空方法且 1.21.1 **无任何覆写**。
- **G2**：每次（piece × 相交区块）都算出**同一个绝对位置**，包围盒门（对应 `:252`）保证只有一个区块写它。
- **G3**：若同结构另一 piece 覆盖了锚点列，它追加的是**同一位置**，前一个 piece 已写入（pieces 顺序处理），`is(WAYPOINT)` 检查丢弃。
- 最坏情况（三重全失效）：同位置写两次 → 仍是一个方块，第二次重跑 BARRIER/`loadWithComponents` 且 NBT 相同，`onLoad` 只触发一次。

### 4.6 作者接入成本（三层，逐层降级）

**Tier 1（最优 —— 1 个文件，不改对方任何文件）**：遮蔽目标**已经引用**的 `worldgen/processor_list`。已验证的原版起始池引用：

| 结构 | 起始池 | 引用的处理器列表 | 引用数 |
|---|---|---|---|
| `ancient_city` | `ancient_city/city_center` | `ancient_city_start_degradation` | 3，且**仅该文件引用** |
| `bastion_remnant` | `bastion/starts` | `bastion_generic_degradation` | 9 |
| `trail_ruins` | `trail_ruins/tower` | `trail_ruins_houses_archaeology` | 72 |
| `trial_chambers` | `trial_chambers/chamber/end` | `trial_chambers_copper_bulb_degradation` | 44 |
| `village/plains` | `village/plains/town_centers` | `mossify_20_percent`（仅该文件）、`mossify_70_percent`、`zombie_plains` | — |
| `village/{desert,savanna,snowy}` | `…/town_centers` | 多为**内联** `{"processors":[]}` | — |
| `pillager_outpost` | `pillager_outpost/base_plates` | **纯内联** ⇒ Tier 1 不可行 | — |

Tier-1 文件内容 = 原版列表**逐字**复制 + 我们的处理器**追加在最后**：
```json
{ "processors": [
  { "processor_type": "minecraft:rule", "rules": [ /* 3 条原版规则，逐字 */ ] },
  { "processor_type": "minecraft:protected_blocks", "value": "#minecraft:features_cannot_replace" },
  { "processor_type": "teleportwaypoint:waypoint", "waypoint_id": "ancient_city" }
] }
```
⇒ **一个 JSON 覆盖全部远古城市，替代 3 个 300 KB 的 NBT**。代价：注册表条目是**整体替换、无合并语义**（`RegistryDataLoader.java:82`），需跟踪原版漂移，并与其他做同样遮蔽的数据包冲突。

**Tier 2（池覆盖）**：`RegistryFileCodec` 接受引用**或内联对象**（`StructureProcessorType.java:16-17`；原版实例见 `pillager_outpost/base_plates.json`）⇒ 掠夺者前哨只需一行，无需 processor_list 文件：
```json
"processors": { "processors": [ { "processor_type": "teleportwaypoint:waypoint", "waypoint_id": "pillager_outpost" } ] }
```

**Tier 3**：对不支持的族继续用 NBT 覆盖（§9.3）。

**已否证的做法**：`minecraft:empty` 在原版中**从未**被用作池元素的 `processors` 值（186 个池文件 0 命中）⇒ 「全局劫持 `minecraft:empty`」不可行。

---

## 5. 路线乙：`ChunkEvent.Load` 区块补扫（主干）

### 5.1 可行性判定

| 问题 | 结论 |
|---|---|
| 事件在主线程？ | ✅ 已确认（§2.2 注入点 2 的调用链） |
| 事件时区块可写？ | ✅ `setLoaded(true)`（`:210`）在事件前完成，`currentlyLoading` 全程持有 ⇒ `ServerChunkCache` 短路（`:154-155,191`） |
| 结构数据可用？ | ✅ starts 与 references 均随 `LevelChunk` 构造复制（`:139-140`） |
| 覆盖硬编码与模组结构？ | ✅ 数据源是 `StructureStart`（按注册表 ID 序列化），与「方块来自模板还是 Java 代码」无关 |
| 能放 BE 与 NBT？ | ✅ 同区块内 `setBlock` → `LevelChunk.setBlockState:239-309` → 创建 BE + `addFreshBlockEntities` |

### 5.2 组件

| 文件 | 作用 |
|---|---|
| `structure/StructureWaypointHandler.java`（新） | `ChunkEvent.Load` 监听器，编排、try/catch、工作量预算 |
| `structure/StructureWaypointScanner.java`（新） | 纯逻辑：给定 `LevelChunk` + `StructureStart`，产出**确定性排序**的候选 `BlockPos` 列表；不碰 level |
| `structure/StructureWaypointPlacer.java`（新） | 施加单个候选：校验、`setBlock`、写 `waypoint_id` |
| `structure/StructureWaypointNaming.java`（新） | 结构注册表 ID → `waypoint_id` |
| `structure/StructureWaypointDebug.java`（新，可选推荐） | `/teleportwaypoint debug structures`，把验证从考古变成一行命令 |
| `config/CommonConfig.java`（改） | 新增 `structureWaypoints` 分组 |
| `TeleportWaypoint.java:26-38`（改） | `NeoForge.EVENT_BUS.addListener(StructureWaypointHandler::onChunkLoad);` |
| `lang/en_us.json`、`lang/zh_cn.json`（改） | 新增配置翻译键 |

**扫描器参数类型刻意收窄为 `LevelChunk` 而非 `Level`** —— 让 `getBlockState`/`getChunkAt`/`structureManager()` 这些会死锁的 API 根本不在作用域内，从结构上杜绝 §5.3 的违规。

### 5.3 线程与死锁纪律

**必做**

1. 第一道门：`event.getLevel() instanceof ServerLevel`（客户端事件 `newChunk` 恒为 false，`ClientChunkCache.java:127`）。
2. 第二道门：`event.getChunk() instanceof LevelChunk`（`ChunkEvent.getChunk()` 声明为 `ChunkAccess`，`ChunkEvent.java:38`）。
3. 第三道门（性价比最高的一行）：`chunk.getAllStarts().isEmpty() && chunk.getAllReferences().isEmpty()` → 直接返回。两个 `Map.isEmpty()`，绝大多数区块在此出局。
4. 区块内读方块只用 `LevelChunk.getBlockState(pos)`（纯数组读）。
5. 任何**其他**区块只经 `level.getChunkSource().getChunkNow(x, z)` + null 检查（`:173-195`，不阻塞、不强制生成）。
6. 放置**严格限制在事件区块自身的 16×16 列内**。
7. 每区块整个流程包 `try/catch (Throwable)` → `LOGGER.warn`。**绝不外抛**（§7.6）。

**禁止**

1. `level.getBlockState(BlockPos)` / `level.getFluidState(BlockPos)` / `getChunkAt` / `getChunk(x,z)` / `getChunkSource().getChunk(...)` / `managedBlock` —— 都会走到 `ServerChunkCache.getChunk:158-160` 的 `mainThreadProcessor.managedBlock(...)`，而补全由**主线程自己**的邮箱投递 ⇒ 自我阻塞（javadoc 警告的正是这个）。
2. **任何 `StructureManager` 查询**：`startsForStructure`（`:50,64`）、`getStructureAt`（`:100`）、`getStructureWithPieceAt`（`:110-132`）、`getAllStructuresAt`（`:157`）、`fillStartsForStructure`（`:71`）—— 全部经 `level.getChunk(..., STRUCTURE_REFERENCES)`，且 `requireChunk=true`（`LevelReader.java:136-137`）⇒ 强制同步加载 + 永久 `TicketType.UNKNOWN` 票 + `runDistanceManagerUpdates()`。
3. 别搞混 `ServerLevel.structureManager()`（`StructureManager`，`:329-331`）与 `ServerLevel.getStructureManager()`（`StructureTemplateManager`，`:1254-1256`）。本路线两者都不需要。
4. 不要把 `Structure.afterPlace` 当替代品：`DesertPyramidStructure.java:34` 与 `WoodlandMansionStructure.java:44` **覆写它且不调 `super()`**，父类钩子会静默漏掉这两个结构。且它在**工作线程**上以 ProtoChunk 运行，BE 只能写 `DUMMY` 占位（`WorldGenRegion.java:294-301`）。
5. 不要为了扩大覆盖去加载/生成区块。漏掉一个结构可以接受，卡死服务器不行。
6. 不要写 `StructureStart`/`StructurePiece`/区块的结构映射。写 `structureStarts` 会置 `unsaved`（`ChunkAccess.java:211/221`）并使区块与其持久化结构数据不同步。

**关于「结构包围盒跨出当前区块」**

- 落点恒在本区块内 ⇒ 读到的是**最终地形**（事件只在 FULL 阶段触发，`ChunkPyramid.java:45`，FEATURES/LIGHT/SPAWN 均已完成）。
- 包围盒/各 piece 跨区块的比较是纯几何运算，不访问 level。
- 目标结构的起始区块在别的区块 ⇒ 走 `getChunkNow`；不可用则**跳过**，起始区块自己触发 `ChunkEvent.Load` 时必然兜底。

**内联 vs 延迟**

**内联**。理由：内联可证明安全且地形已完成；延迟到下一 tick 反而引入新失败模式（区块可在事件与 drain 之间被卸载，而没有任何 ticket 保护它），届时仍需 `getChunkNow` 校验。唯一需要「延迟」的场景是起始区块尚未驻留，那是**重试**而非延迟，且**不需要**队列 —— 起始区块自身的 `ChunkEvent.Load` 就是保证的重入点。

### 5.4 去重与幂等

**去重键（自然键）**：`(dimension, structure registry id, origin ChunkPos)`。三项都在事件时可得：起始区块位置来自 `StructureStart.getChunkPos():122-124`；对于引用路径，`LongSet` 里的 `long` **就是**起始区块位置。

**推荐机制：以锚点方块自身为标记 + 邻近检查。**

| 机制 | 多区块 | 跨会话 | 世界重载 | 模组安装前生成的区块 | 存档增长 | 锚点被破坏后 |
|---|---|---|---|---|---|---|
| 内存 `HashSet` | ✅ | ✅ | ⚠️ 重启丢失 | ❌ | 无 | ❌ **会重新长出** |
| **锚点方块自身作标记** | ✅ | ✅ | ✅ | ✅（就该没锚点，与「已检查但没找到位置」语义一致） | **无** | ✅ 构造性幂等 |
| 新增 SavedData | ✅ | ✅ | ✅ | ✅ | O(结构数)，约 60 B/条 | 需显式清理策略 |
| 扩展 `WaypointRegistryData` | ⚠️ | ✅ | ✅ | ⚠️ 无法记录「检查过但无合适位置」 | O(锚点数) | 自动 |

**选择「锚点方块自身作标记」**：成本为零、无存档增长、构造性幂等，且是唯一与路线甲天然协同的方案（甲的精确落点会被乙的邻近检查识别为「已处理」）。

**负结果的代价与接受**：无法记录「检查过但没找到位置」，因此被跳过的结构（海洋神殿、废弃矿井、埋藏的宝藏等内部是水/空腔的）会在**每次区块加载**重跑选点。对策：把选点成本压到极低（§7.4），并接受这一重复成本 —— 相比引入一份永久增长的 SavedData，这是更好的权衡。

**邻近检查的实现纪律**：检查范围限定在**结构起始区块内的锚点候选列附近**，只经 `LevelChunk.getBlockState`，不得为了找标记而扫描整个包围盒或跨区块。

**四个场景的结论**

| 场景 | 结果 |
|---|---|
| 多区块结构 | 只在起始区块放置 ⇒ 天然一次 |
| 同会话区块重载 | 标记已在 ⇒ 跳过 |
| 世界重载 | 标记随区块 NBT 持久化 ⇒ 跳过 |
| 模组安装前已生成的区块 | 标记不存在；默认**不动**（`backfillExistingChunks=false`）；开启后首次加载该区块时补放 |
| 锚点被破坏 | 标记消失 ⇒ **会重新放置**。这是本机制唯一的语义代价。对策：`WaypointBlock` 破坏时在**原位**留下一个不可见的「已处理」标记？—— **不做**。接受重放，并在 README 说明；若不可接受，则必须引入 §5.4 表中的 SavedData 方案（记入 §12 待决项 D3）。 |

### 5.5 选点算法

输入：`LevelChunk chunk`、`StructureStart start`、配置。

1. `if (!start.isValid()) return;`（`StructureStart.java:118-120`；同时防 `structure == null`，`INVALID_START` 在 `:24`）。
2. `structureId = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure())`，null ⇒ 跳过。
3. 候选 piece：`start.getPieces()` 中**包围盒与本区块 16×16 列相交**者（`piece.getBoundingBox().intersects(minX, minZ, minX+15, minZ+15)`，与原版 `createReferences` 同谓词，`ChunkGenerator.java:586`）。丢弃退化包围盒（`maxX < minX`）。
4. 全序排序（确定性，与哪个区块在问无关）：
   1. piece 按包围盒**体积降序**（大 piece 更可能是房间而非连接件）；
   2. piece 内列按**到 piece XZ 中心距离升序**，再 `x` 升序、`z` 升序；
   3. 列内**最高**的合格 `y` 优先。
5. 每列的合格判据（全部满足）：
   - 下方为实心且非液体、无 BlockEntity；
   - 本格 `isAir() || canBeReplaced()` 且**非液体**（显式拒绝水，避免海洋神殿/淹没矿井里放水里）；
   - 上方连续 `verticalClearance`（默认 2）格满足同样条件；
   - 本格及上方两格均**无 BlockEntity**（排除箱子/刷怪笼/vault/讲台等）；
   - `y` 在 `[minBuildHeight+1, maxBuildHeight-1]`。
6. 兜底：piece 全败则用结构自身 `BoundingBox` 与本区块的交集跑同样规则。
7. 全败 ⇒ 记 DEBUG，结束（无负缓存，见 §5.4）。

### 5.6 命名

```java
String vanillaKey = Util.makeDescriptionId("structure", structureId);  // structure.<ns>.<path>
String modKey     = "teleportwaypoint.waypoint." + waypointId(structureId);
```

`waypoint_id` 由结构的**路径**规范化而来（小写、`[^a-z0-9_]` → `_`、去首尾 `_`、截断 64），并提供**配置覆盖表**以复现作者既有的语义映射：

| 结构注册表 ID | 朴素推导 | 作者既有 id | 需覆盖 |
|---|---|---|---|
| `minecraft:igloo` | `igloo` | `igloo` | 否 |
| `minecraft:ancient_city` | `ancient_city` | `ancient_city` | 否 |
| `minecraft:end_city` | `end_city` | `end_city` | 否 |
| `minecraft:trial_chambers` | `trial_chambers` | `trial_chambers` | 否 |
| `minecraft:bastion` | `bastion` | **`bastion_remnant`** | **是** |
| `minecraft:mansion` | `mansion` | **`woodland_mansion`** | **是** |
| `minecraft:fortress` | `fortress` | **`nether_fortress`** | **是** |

⇒ 默认值中内置这三条映射，其余走路径推导。**命名空间不进入 `waypoint_id`**（沿用既有约定，使 YUNG 结构共享原版翻译键），但**进入去重键**。作者对某个结构不满意自动名时，可加一条覆盖或补一个翻译键。

**必须保持有效的历史 id**（既有存档的 `waypoint_id` 永不迁移）：`ancient_city`、`bastion_remnant`、`end_city`、`igloo`、`trial_chambers`、`woodland_mansion`、`desert_pyramid`、`nether_fortress`、`jungle_temple`、`ocean_monument`、`swamp_hut`、`village`、`empty`。

> 现有 14 个 `teleportwaypoint.waypoint.*` 翻译键必须永不删除：激活记录按 uid 索引（`PlayerWaypointData.java:27`），但显示名按 id 解析。

### 5.7 数据流（标注侧与线程）

```
服务端 / 世界生成工作线程
 1. ChunkStatusTasks.full:191  supplyAsync（工作线程）
 2. new LevelChunk(serverlevel, protochunk, ...)  :199   [复制 BE :129-131，starts/refs :139-140]
 3. currentlyLoading = levelchunk  :205/:212
 4. setLoaded(true)  :210
 5. registerAllBlockEntitiesAfterLevelLoad / tick container  :213-214
 ── 续体经 mainThreadMailBox().tell(...) 交回主线程  :221 ──

服务端主线程
 6. NeoForge.EVENT_BUS.post(new ChunkEvent.Load(levelchunk, isNew))  :215
 7. StructureWaypointHandler.onChunkLoad
      ├─ ServerLevel + LevelChunk 守卫
      ├─ starts/refs 双空早退
      ├─ structureId ⇒ waypoint_id（含覆盖表）
      ├─ 邻近锚点标记检查（只读本区块）
      ├─ StructureWaypointScanner：纯几何 + LevelChunk.getBlockState
      └─ StructureWaypointPlacer：
            level.setBlock(pos, waypoint, UPDATE_ALL)   → LevelChunk.setBlockState:239
                                                        → newBlockEntity + addAndRegisterBlockEntity
            be.setId(waypointId)（校验后再调）
 8. currentlyLoading = null  :217

服务端主线程，下一 tick
 9. Level.tickBlockEntities:568-585 → onLoad:581
10. WaypointBlockEntity.onLoad:216-222 → WaypointManager.register
11. register:35-64 → ensureUniqueUid → getUid():54-60 惰性生成 uuid
      → WaypointRegistryData.put（主线程 SavedData）→ broadcastAdd:268-282

客户端
12. AddWaypointPayload → ClientWaypointState.applyAdd（ModNetwork.java:88-90）
13. WaypointBlockEntityRenderer:109,112-122 按激活状态着色；Xaero 读同一状态
14. 后登录玩家由 WaypointEvents.java:17-29 的 syncTo/syncDimensionTo 补齐
```

**注意**：`setPlacedBy`（玩家放置路径，`WaypointBlock.java:88-102`）不会被 `Level.setBlock` 触发，所以处理器/放置器必须自己写 BE 数据。

---

## 6. 命名子系统（两路线统一）

### 6.1 现状关键事实：`waypoint_id` 就是翻译键后缀

| 环节 | 位置 | 传递内容 |
|---|---|---|
| 存储 | `WaypointBlockEntity.java:39,209-212` | 裸 id（如 `end_city`） |
| 校验 | `:30,36,117-119` | `[a-z0-9_]+`，≤64 |
| 服务端记录 | `WaypointManager.java:47-53` | 裸 id 存入 `WaypointRecord.name` |
| 持久化 | `WaypointRegistryData.java:79` | 裸 id |
| 线格式 | `WaypointSyncInfo:16-24`、`ActivatedWaypointInfo:14-20` | `ByteBufCodecs.STRING_UTF8` 裸 id |
| 客户端渲染 | `ActivatedWaypointInfo:23`、`ClientWaypointInfo:25`、`XaeroMinimapIntegration:318` | 各自拼 `"teleportwaypoint.waypoint." + name` |

**推论**：任何 `waypoint_id` 就是每个客户端的翻译键后缀，**零协议改动**；同时意味着**客户端必须有对应语言文件**。

### 6.2 缺失翻译的回退链

```
1. 配置覆盖名（服务端权威，字面量）
2. teleportwaypoint.waypoint.<id> 的 lang 条目（客户端语言）
3. 人工化 id：minecraft_trail_ruins → "Minecraft Trail Ruins"
4. 裸 id
```

分层 3/4 **必须在客户端渲染时计算，不能烘焙进 id**。硬约束：`isValidId`（`:117-119`）拒绝空格与大写，`loadAdditional`（`:209-212`）会把非法值静默改成 `"empty"` ⇒ 「把可读名写进 id」**不可实现**。此外烘焙会冻结进 `WaypointRegistryData`，日后改进算法无法修复既有存档。

实现用原版既有惯例：`Component.translatableWithFallback(key, humanize(id))`。

**专用服务器特例**：模组只有 `assets/teleportwaypoint/lang/`（客户端侧），专用服务器上 `Language.getDefault()` 恒返回键本身（`Language.java:48-49,137-138`）⇒ **服务端任何 `Component.translatable` 都会渲染成裸键**。这直接影响 `WaypointManager.java:114-117` 的激活聊天消息。因此服务端侧的日志与消息不得依赖 lang 查询。

**Xaero 例外**：`XaeroMinimapIntegration.java:316-318` 刻意把**原始键**交给 Xaero 以便其按当前客户端语言本地化。此处**不得**换成 `displayName(...).getString()`（会冻结字符串、丢失本地化）。仅当本地区无该键时才回退到人工化名，用 `Language.getInstance().has(key)` 判定（客户端专有）。这个不对称必须写进代码注释，否则会被「顺手简化」掉。

### 6.3 配置编码

`ModConfigSpec` **没有 map 型构建器**；`defineList` 的 4 参形式隐含 `ListValueSpec.NON_EMPTY`（`:1134`），**空列表会被判非法并替换为默认值** ⇒ 必须用 `defineListAllowEmpty`（`:501-592`）才能表达「默认无覆盖」。

```java
builder.translation("teleportwaypoint.configuration.common.structureWaypoints");
builder.push("structureWaypoints");

STRUCTURE_WAYPOINTS_ENABLED   = ...define("enabled", true);
BACKFILL_EXISTING_CHUNKS      = ...define("backfillExistingChunks", false);
VERTICAL_CLEARANCE            = ...defineInRange("verticalClearance", 2, 0, 6);
MAX_PLACEMENTS_PER_CHUNK      = ...defineInRange("maxPlacementsPerChunk", 1, 1, 4);
STRUCTURE_ID_OVERRIDES        = ...defineListAllowEmpty("structureIdOverrides", DEFAULT_OVERRIDES, () -> "", o -> o instanceof String);
STRUCTURE_FILTER              = ...defineListAllowEmpty("structureFilter", List.of(), () -> "", o -> o instanceof String);

builder.pop();
```

- `structureIdOverrides` 编码为 `"<registryId>=<waypointId>"` 字符串列表，默认含 §5.6 的三条映射。
- `structureFilter` 空 = 全部结构；支持 `#tag`。
- `enabled` 默认 `true`（与 `DEFAULT_ENABLE_STRUCTURE_WAYPOINTS = true` 的既有取向一致）。
- `backfillExistingChunks` 默认 **false**，沿用 `2026-08-19-…-design.md:231` 的既有非目标。

---

## 7. 错误处理

| 情况 | 处理 |
|---|---|
| 客户端区块事件 | `!(event.getLevel() instanceof ServerLevel)` → 返回 |
| `getChunk()` 非 `LevelChunk` | 返回 |
| 起始区块结构数据为空 | 双空早退（`getAllStarts` + `getAllReferences`） |
| `INVALID_START` / `structure == null` | 跳过（必须先 `isValid()`，`getBoundingBox():71-79` 会解引用 `structure`） |
| `registry.getKey(structure) == null` | 跳过 + DEBUG |
| 无合适位置（海洋神殿/矿井/埋藏宝藏内部为水或空腔） | DEBUG，结束；无负缓存 ⇒ 每次加载重试（§5.4 已接受） |
| 候选位置被其他 BE 占据 | 判据拒绝；若已是本模组锚点则跳过 |
| 路线甲 `waypoint_id` 非法 | `isValidId` 校验后拒绝写入 + WARN（点名 id；处理器无法得知结构名） |
| 路线甲 `capped` 排在后面 | `CappedProcessor.java:47-54` 会跳过并经 `Util.logAndPauseIfInIde`（`Util.java:530-535`）**在调试器下暂停游戏**。规则：我们的处理器永远最后；启动时遍历 `Registries.PROCESSOR_LIST`（`Registries.java:221`）校验，发现我们的实例之后有 `CAPPED` 则 ERROR |
| 处理器/处理器列表 JSON 依赖模组 | `RegistryDataLoader.java:152-154` **硬失败**（世界/启动加载中止）。故：本模组自己的 processor_list 必须**常驻**（`src/main/resources/data/teleportwaypoint/worldgen/`），不得放进可开关的数据包；所有 Tier-2/遮蔽包必须声明对模组的依赖 |
| 扫描器抛异常 | 每区块包 `catch (Throwable)` + WARN（维度 + ChunkPos + 堆栈）。**绝不外抛** —— 异常会从 `ChunkStatusTasks.full` 的续体传出，使该区块 future 异常完成 → `ServerChunkCache.getChunk:160` 的 `join()` 抛出 → `IllegalStateException("Chunk not there when requested")` → 崩溃 |
| 模组被移除 | 注册表条目「will be deleted from the save file on next save」（`RegistryManager.applySnapshot:130-163`，尤见 `:152-159`）⇒ 锚点方块消失；`teleportwaypoint_waypoints` 作为陈旧索引保留，由 `WaypointTeleporter.teleportTo:74-80` 惰性清理。README 需说明 |
| 功能中途关闭 | 监听器首行返回；不放置、不移除；已有锚点由独立的 BE/注册表路径维持 |
| `generateStructures=false` 的世界 | starts/refs 从不创建（`ChunkStatusTasks.java:39-48`）⇒ 双空早退零成本覆盖 |

## 8. 性能纪律（路线乙的首要风险）

事件对**每次区块加载**（含纯磁盘加载）触发，主线程内、计入 tick 预算（`MinecraftServer.java:867` 的 `haveTime()` 门控），客户端也每区块触发一次。按顺序设置早退：

1. `instanceof ServerLevel` —— 消除全部客户端开销。
2. `STRUCTURE_WAYPOINTS_ENABLED` —— 关闭时零开销。
3. **`getAllStarts().isEmpty() && getAllReferences().isEmpty()`** —— 两个 `Map.isEmpty()`，绝大多数区块在此出局（`ChunkAccess.java:214-215,222-223` 是轻量不可变视图）。
4. 每区块结构数上限、每区块放置上限。
5. 邻近标记检查只在本区块、只在候选列附近。
6. 默认关闭逐次放置日志（`debugLogging=false`）。

**安全的实现子集**：`isNewChunk()` + 服务端 + 只读 `getAllStarts()` + 起始区块内放置 + 不跨区块读写 + **不解析 references** + 不打日志。#3 之后任何额外工作都是直接的 TPS/加载时间成本。

> 权衡说明：跳过 references 解析意味着**一个结构只在其起始区块被处理**。对原版结构这是正确的（起始区块是结构锚点所在）。被引用的其他区块只用于定位跨区块的包围盒，而我们严格在起始区块内落点，因此不需要它们。

---

## 9. 与现有实现的集成

### 9.1 必须保留（向后兼容）

| 项 | 原因 |
|---|---|
| `WaypointManager.ensureUniqueUid` + `WaypointBlockEntity.regenerateUid` | 既有存档的区块数据里已固化冲突 uid；删除会在加载时污染注册表 |
| `WaypointBlockEntity.loadAdditional/saveAdditional` 的 `uid` 处理 | 读取既有区块里的历史 uid |
| `register` 的 `broadcastUpdate` 分支 | 区块重载重新注册时名字可能已变 |
| 两份 `SavedData` 的读取路径与容错 `try/catch` | 必须与既有 `.dat` 字节兼容；不得无版本迁移就换成 codec |
| 全部 14 个 `teleportwaypoint.waypoint.*` 键 | 既有存档的 `waypoint_id` 靠它们解析 |
| `DatapackRegistration` 框架 | 路线甲仍需它注册可开关的遮蔽/Tier-2 包（`Pack.Position.TOP`，`:62`） |

### 9.2 可删除

| 项 | 路由 |
|---|---|
| `vanilla_structure_waypoints` 中的 9 个 jigsaw 模板（3 ancient_city + 4 bastion + 2 trial_chambers） | 路线甲 Tier-1 用 3 个 JSON 替代 |
| `yung_structure_waypoints` 的 18 个 NBT | 路线乙全量替代；路线甲需逐 namespace 评估 |
| `DEFAULT_ENABLE_STRUCTURE_WAYPOINTS` / `..._YUNG_...` 及对应 lang 键 | 由新配置取代（**但见 §9.4 迁移风险**） |
| 预计 jar 体积 | 删两个包 ⇒ 约 189 KB（从 440 KB），接近 0.1.0 的 184 KB |

### 9.3 必须保留的 3 个 NBT 覆盖（覆盖回退的兜底）

`end_city/third_floor_1.nbt`、`igloo/top.nbt`、`woodland_mansion/entrance.nbt` —— 这三族是 `TemplateStructurePiece` 型，**路线甲够不着**。三个选项：

1. **删除并由路线乙的通用规则接管**（推荐）：路线乙能覆盖它们，位置由统一规则决定，不再精确到当前那一格。符合需求 2 的选择。
2. 保留这三个 NBT 覆盖作为精确落点，与路线乙并存（需 §9.4 的去重成立）。
3. 保留这三个 NBT 覆盖，且路线乙对它们**排除**（通过 `structureFilter`）。

推荐 **1**；若作者坚持保留当前精确落点，退到 **3**。

### 9.4 迁移与重复放置风险（必须处理）

现有数据包用 `PackSource.BUILT_IN`（默认 true）会在**既有存档自动启用**（`MinecraftServer.java:1581`）。因此：

- 同时保留旧包 + 启用新机制 ⇒ **每个结构两个锚点**。
- 移除旧包 ⇒ 加载不失败（`MinecraftServer.java:1568-1574` 警告后丢弃），但**没有迁移**，且外部数据包若引用 `teleportwaypoint:<processor>`/`<processor_list>` 会导致世界加载**硬失败**（§7）。

**对策（发布协调项，非代码项）**：

1. 同一版本内把 `defaultEnableStructureWaypoints` / `defaultEnableYungStructureWaypoints` 默认值改为 `false`，并在 README 给出迁移说明。
2. `StructureWaypointPlacer` 在放置前检查候选位置是否已是本模组锚点（§5.4），覆盖「旧包已放过」的部分重叠情况。
3. 对旧包已放置但新机制不会命中的位置（例如旧包放在非起始区块），**不做**主动清理 —— 记录为已知遗留。

### 9.5 死代码清理清单（路线乙为主时）

- 文件：`datapack/DatapackRegistration.java`（若完全放弃路线甲）、`vanilla_structure_waypoints/**`、`yung_structure_waypoints/**`、`TeleportWaypoint.java:33` 及其 import。
- 配置：`CommonConfig.java:11-12,23-36` 的 `optionalDataPacks` 分组整体塌缩。
- lang：`...common.optionalDataPacks{,.tooltip}`、`...common.defaultEnableStructureWaypoints{,.tooltip}`、`...common.defaultEnableYungStructureWaypoints{,.tooltip}`、`datapack.teleportwaypoint.*.{name,description}`。

---

## 10. 验证策略

项目**没有 `src/test`**（`src` 下只有 `main`），且历史上多次明确决定不引入测试框架（`memory/decisions-log.md:6,51,74`）。但 `build.gradle:75-78` **已有** `gameTestServer` 运行配置与 `neoforge.enabledGameTestNamespaces=teleportwaypoint`，可直接承载运行时验证。

### 10.1 构建与静态门

```bat
cd /d D:\Minecraft\Teleport-Waypoint-ds_flash
gradlew.bat build --offline --console=plain
gradlew.bat prepareClientRun --offline
gradlew.bat runServer --offline --console=plain
```
`runServer` 是最廉价的完整 codec 测试：数据包注册表在世界创建前加载，畸形 JSON 会以 DFU 错误点名文件并中止启动（`RegistryDataLoader.java:152-154`）。

建议新增 `checkLang` Gradle 任务（挂在 `processResources`）：解析两个 lang JSON，双向比对键集合，并 lint `teleportwaypoint.waypoint.*` 的后缀是否符合 `[a-z0-9_]+` 且 ≤64。一个逗号错误会让**整个**语言文件被丢弃（`ClientLanguage.java:52`），也就是一次拼写毁掉全部 14 个名字。

### 10.2 游戏内清单

1. 新世界，`structureWaypoints.enabled=true`、`backfillExistingChunks=false`，并把两个 `defaultEnable*StructureWaypoints` 置 `false`（迁移）。
2. `/locate structure` + `/tp` 逐一检查：`minecraft:igloo`、`village_plains`、`fortress`、`stronghold`、`ancient_city`、`trial_chambers`、`desert_pyramid`、`buried_treasure`、`mineshaft`、`ocean_monument`。
3. **每个结构实例恰好一个锚点**（含跨区块结构：村庄、要塞、矿井）。
4. 右键 → 激活消息使用正确的本地化名。
5. `/data get block <pos>` → `waypoint_id` 正确、`uid` 存在；同一结构的两个实例 **id 相同、uid 不同**。
6. F3 → `waterlogged=false`；水晶/光球不穿模（净空 2 格足够，§2.5）。
7. Xaero 小地图/世界地图显示同名锚点，且按维度隔离。
8. 破坏锚点 → 离开再回来 → **会重新放置**（记录为已知语义，§5.4）；世界重启 → 不重复。
9. `backfillExistingChunks=true` + 重启 → 旧结构首次加载时补放，且只有一个。
10. `runServer` + 原版客户端 → 行为一致；专用服务器上聊天消息**不得**显示裸键（§6.2）。
11. 路线甲：`log_rejections=true` 下每结构恰好一次追加，且**无** `CappedProcessor` ERROR。

### 10.3 日志观察点

- INFO：仅在 `debugLogging=true` 时逐次放置一条。
- WARN：扫描器异常（含维度/ChunkPos/堆栈）、`waypoint_id` 被拒、`getKey` 返回 null。
- **最重要的负面信号**：不得出现 `"Chunk not there when requested"`、watchdog "A single server tick took …"、或任何指向 worldgen 的 `CrashReport`。死锁表现为主线程停在 `ServerChunkCache.getChunk → managedBlock`。

### 10.4 调试命令（推荐的最高性价比工具）

`/teleportwaypoint debug structures [radius]`（op，权限级 2）：

- 打印当前维度的锚点标记命中情况与去重判定；
- 对玩家当前区块跑一遍扫描，打印 `getAllStarts()` 中的结构、注册表 id、包围盒、候选数与最终落点；
- 打印可直接粘贴的语言文件行：`"teleportwaypoint.waypoint.<id>": "<Name>"`；
- `clear` 子命令清标记（测试用）。

这把 §9.2 的第 3、8、9 步和 id 覆盖表（§5.6）的确定都变成可脚本化的一行命令。

### 10.5 后续可加的 GameTest

利用现成的 `gameTestServer`：

1. `StructureWaypointScanner` 纯几何断言（不需要真实世界）：给定人造 `StructureStart`/piece 包围盒与方块表，断言落点。
2. `StructureWaypointNaming` 的映射表断言，含 `bastion→bastion_remnant`、`mansion→woodland_mansion`、64 字符截断。
3. 幂等断言：同区块跑两次 `process`，恰好一个方块、一个标记。
4. 无死锁回归：在测试中构造 `ChunkEvent.Load` 调用处理器并断言其**返回**（gameTestServer 有超时，挂起即失败）。
5. 路线甲：`processBlockInfos` 断言恰好追加一条、位置与 tag 正确；再以「只含 DATA 模式 StructureBlock 的输入」断言原样返回（钉住 §4.5 G1）；用两个不同 `setBoundingBox` 断言只在包含该位置的盒子里追加。
6. 断言 `setId(derived)` 后 `getDisplayName()` **不等于** `Component.translatable(emptyKey())`（钉住「派生 id 不被静默降级为 empty」）。

---

## 11. 决策记录

| # | 决策 | 理由 |
|---|---|---|
| D1 | 主干采用路线乙（`ChunkEvent.Load`），路线甲作为可选精修 | 只有乙能覆盖硬编码与模组结构；甲单实施是覆盖回退（§3.2 问题 1） |
| D2 | 路线甲选点**必须**以 `pos` 参数为锚，禁止由包围盒推导 | 每次区块遍历的包围盒都不同 ⇒ 包围盒推导会产生每重叠区块一个锚点（§3.2 问题 2） |
| D3 | 去重用**锚点方块自身为标记**，不新增 SavedData | 零成本、无存档增长、构造性幂等；且与路线甲天然协同。代价：「锚点被破坏后会重放」被接受 |
| D4 | 两条路线都**不写 `uid`**，只写 `waypoint_id` | 固定 uid 是当前设计缺陷的根源；省略后 `getUid()` 惰性生成唯一值，`ensureUniqueUid` 退化为安全网 |
| D5 | 处理器/放置器**绝不**在世界生成期调用 `register()` 或任何触发 `setChanged()` 的 API | 世界生成期 BE `level == null`（`register` 直接返回）；`setChanged()` 会经 `WorldGenRegion.getChunk` 抛异常 |
| D6 | 命名保持单一扁平键空间 `teleportwaypoint.waypoint.<id>`，命名空间**不进 id** 但**进去重键** | id 是线上唯一的命名数据，三个客户端调用点无条件拼前缀；独立键空间需要协议改动而零额外安全收益 |
| D7 | 缺失翻译在**客户端渲染时**回退到人工化名，禁止烘焙进 id | `isValidId` 拒绝空格/大写，`loadAdditional` 会静默改成 `empty` ⇒ 烘焙不可实现；且烘焙会冻结进存档 |
| D8 | Xaero 走 `Language.has(key)` 特例，其余用 `translatableWithFallback` | Xaero 需要原始键才能按客户端语言本地化；这个不对称必须注释说明 |
| D9 | 配置覆盖用 `defineListAllowEmpty` + `"<id>=<value>"` 字符串列表 | `ModConfigSpec` 无 map 支持；`defineList` 的空列表会被判非法，无法表达「默认无覆盖」 |
| D10 | 本模组自己的 processor_list 必须**常驻**，不得放进可开关数据包 | 数据包注册表解析失败是硬失败，会导致世界无法加载（§7） |
| D11 | 保留 `end_city`/`igloo`/`woodland_mansion` 的 NBT 覆盖，或明确接受由路线乙通用规则接管 | 甲够不着这三族，删除而不接管会造成覆盖回退（§9.3） |
| D12 | `backfillExistingChunks` 默认 `false` | 沿用既有非目标（`2026-08-19-…-design.md:231`），避免在玩家已探索/已掠夺的结构里凭空出现锚点 |
| D13 | 既有 `waypoint_id` 与 14 个语言键**永不迁移/删除** | 激活按 uid 索引所以改名不丢锚点，但显示名按 id 解析；迁移会静默改变旧存档里的名字 |

---

## 12. 风险与对策（排序）

| # | 风险 | 证据 | 对策 |
|---|---|---|---|
| R1 | 性能：监听器对每次区块加载触发（含磁盘加载、含客户端） | `ChunkStatusTasks.java:215`、`ClientChunkCache.java:127`、`MinecraftServer.java:867` | §7.4 的早退顺序；第三道门（双空）承担绝大部分削减；默认关闭逐次日志 |
| R2 | 死锁/卡顿：主线程阻塞在 `managedBlock`；跨区块访问触发强制同步加载与**重入** `ChunkEvent.Load` | `ServerChunkCache.java:158-160,227-248`；`BlockableEventLoop.java:130-142`；`ChunkEvent.java:47` | §5.3 的禁止清单；扫描器参数收窄为 `LevelChunk`；放置严格限制在事件区块内 |
| R3 | 覆盖回退：甲够不着 `end_city`/`igloo`/`woodland_mansion` | `TemplateStructurePiece.java:88-90`；只有池元素持处理器列表（`SinglePoolElement.java:60-62`） | D11；以路线乙接管这三族 |
| R4 | 重复锚点：与旧数据包并存，且数据模型无结构实例标识 | `MinecraftServer.java:1581`；`WaypointRecord.java:13` | §9.4：同版本改默认值 + 放置前邻近标记检查 + README 迁移说明 |
| R5 | 数据包失败模式从**静默**变为**硬失败** | `RegistryDataLoader.java:152-154` vs `StructureTemplateManager.java:102-114` | D10；Tier-2 包声明模组依赖；README 说明恢复途径（安全模式/移除包） |
| R6 | 路线甲位置非确定性（若由包围盒推导） | `ChunkGenerator.java:357`、`StructureStart.java:86-88` | D2：唯一锚点 = `pos`；`search_radius ≤ 2` + 全序排序 |
| R7 | 路线甲处理器被并发世界生成线程共享 | `ChunkStatusTasks.java:191` | 强制无状态；不得有缓存/映射/「上次位置」 |
| R8 | 本模组自身的净空前提曾是错的（6 格 vs 实际 1.15） | §2.5 | `verticalClearance` 默认 2、可配置 0–6；已在本文档更正 |
| R9 | 锚点被破坏后会重放 | D3 的直接后果 | 接受并在 README 说明；如需消除必须改用 SavedData 方案（见 §12 D3） |
| R10 | 客户端同步时序：uid 在 `onLoad` 才生成，而区块包的 `getUpdateTag` 更早 ⇒ 渲染器可能短暂显示「未解锁」色 | `WaypointBlockEntity.java:225-229`；`WaypointBlockEntityRenderer.java:109` | **既有问题**（NBT 路线同样存在）；可选修复：`register` 后调一次 `chunkSource().blockChanged(pos)` |
| R11 | 海洋神殿/矿井/埋藏宝藏内部为水或空腔 ⇒ 永远找不到合适位置，且无负缓存 ⇒ 每次加载重跑 | §5.5 判据 | 接受重复成本；把选点压到极低；`structureFilter` 可显式排除这些结构 |

---

## 13. 待决项与未能验证的事项

**待决（需要作者决定）**

- **D3'**：「锚点被破坏后重新放置」是否可接受？若不可接受，必须改为「锚点方块标记 + 一份 `teleportwaypoint_structures` SavedData 负缓存」双重机制，代价是 O(结构数) 的存档增长与一套清理策略。
- **D11'**：§9.3 的三个选项选哪个（推荐 1，作者可能偏好 3）。
- **D14**：是否提供 `/teleportwaypoint debug structures`（推荐提供 —— 它是 id 覆盖表与验证的主力工具）。
- **D15**：路线甲是否本期一并实施，还是先只做路线乙并在下一版加入精修层。

**未能从源码验证（需实机或数据包 jar 确认）**

1. **原版 worldgen JSON 的完整清单**：`nf-src\data\minecraft` 只有 advancement/loot_table/recipe/tags，没有 worldgen。§3.1 的 34 个结构分类来自 client-extra jar 枚举，但「哪些池引用了 `minecraft:empty` vs 专用列表」需要在实机或数据包 jar 上确认，直接决定路线甲 Tier-1 的真实覆盖面。
2. **`TranslatableContents` 的 fallback 路径**在 1.21.1 的确切可用性（本设计不依赖它 —— `waypoint_id` 与渲染路径均不变，fallback 只是加分项）。
3. **`StructureTemplateManager.listTemplates()` 从世界生成工作线程调用是否安全**（资源重载期间）；这关系到路线甲「用模板实例反查结构身份」这条支路的可行性。
4. **`TicketType.UNKNOWN` 票是否会被释放**（已确认添加点 `ServerChunkCache.java:233`，未确认移除点）。
5. **模组被移除后，已存方块被重映射为 air 的确切路径**（可引用的只有 `RegistryManager.applySnapshot:152-159` 的「下次保存时删除」表述）。
6. **真实区块加载吞吐与处理器实测成本**：需要 spark 等 profiler 在真实世界上测量。源码只能界定机制（主线程、tick 预算、强制加载），无法给出数值。
7. **路线甲 `MapCodec` 的未知多余字段是否被 DFU 忽略**（未从源码确认）。

---

## 14. Next Steps

1. 评审本文档，确认 §12 的待决项（尤其 D3' 与 D11'）。
2. 转入 `planning` 技能生成实施计划。
3. 实施顺序建议：
   1. `StructureWaypointNaming` + 配置（无副作用，可先落地并被 GameTest 覆盖）；
   2. `StructureWaypointScanner`（纯几何，可先以 GameTest 覆盖）；
   3. `StructureWaypointHandler` + `Placer`（含 §7.4 早退与 §5.3 纪律）；
   4. 调试命令；
   5. 迁移（§9.4）与清理（§9.5）；
   6. 路线甲精修层（若本期实施）。
4. 更新 `memory/decisions-log.md`、`memory/learned-patterns.md`、`memory/project-context.md`。
