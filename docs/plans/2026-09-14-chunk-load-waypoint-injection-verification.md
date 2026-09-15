# 区块补锚 实施验证记录

**日期:** 2026-09-14
**版本:** 0.4.0
**实施计划:** `docs/plans/2026-09-14-chunk-load-waypoint-injection-implementation.md`
**设计依据:** `docs/plans/2026-09-14-chunk-load-waypoint-injection-design.md`（已定案）

---

## 1. 自动化验证结果

### 1.1 构建

```
gradlew.bat clean build --offline --console=plain
→ BUILD SUCCESSFUL
→ build/libs/teleportwaypoint-0.4.0.jar (484,159 bytes)
```

### 1.2 GameTest

```
gradlew.bat runGameTestServer --offline --console=plain
→ All 22 required tests passed :)
```

连续多次运行均通过（用于排除偶发）。覆盖的 22 个测试：

| 分组 | 测试 |
|---|---|
| 命名（7） | `naming_vanillaIds`、`naming_modStructures`、`naming_truncation`、`naming_humanize`、`naming_keys`、`naming_legacyIdsStillNameThemselves`、`displayName_notEmptyFallback` |
| id 合法性（1） | `idPattern_acceptsDot` |
| 扫描几何（5） | `scanner_picksHighestRoofedSpot`、`scanner_fallsBackToUnroofedPass`、`scanner_chunkFallbackStaysInChunk`、`scanner_invalidStartIsIgnored`、`scanner_excludesBlockEntities` |
| 水体放置（1） | `scanner_allowsWater` |
| 标签（4） | `tags_whitelistIsLoadedAndComplete`、`tags_blacklistCoversMineshaftMesa`、`tags_partitionEveryVanillaStructure`、`tagFilter_doesNotThrowInEitherMode` |
| 放置器（3） | `placer_writesWaypointWithoutNeighbourUpdates`、`placer_setsWaterloggedInWater`、`placer_idIsSetAndPersisted` |
| 端到端 id（1） | `placedWaypointKeepsStructuredId` |

> **重要：这 21 个测试全绿 _不_ 构成死锁已修复的证据。** GameTest 世界超平坦且无结构（处理器在第 4 道门就退出）、所有邻居满加载（构造不出「边界列 + 界外邻居未达 FULL」这个前提），而且主线程卡死时 `tickInternal` 停止推进 ⇒ **超时永不触发，`runGameTestServer` 会永久挂起而不是失败**。死锁的判别力验证只能用 §3.6「修复与验证证据」里的专用服务器 + RCON 对照步骤。

### 1.3 数据包 codec 门

```
gradlew.bat runServer --offline --console=plain
→ Done (1.569s)! For help, type "help"
```

无 DFU / `RegistryDataLoader` 错误，无标签缺失 WARN。

### 1.4 真实新区块端到端验证（关键项）

临时在处理器入口与 `StructureWaypointDebug` 加可观测日志，清空 `run/world`，`debugMode=true`，用 `/forceload add -256 -256` 与 `/forceload add 256 256` 强制生成大量新区块，观察日志：

```
[Server thread/DEBUG] [co.zo.te.TeleportWaypoint/]: structure waypoint: handled chunk=[0, -1] starts=1
[Server thread/DEBUG] [co.zo.te.TeleportWaypoint/]: structure waypoint: skipped by tag structure=minecraft:mineshaft mode=WHITELIST
[Server thread/DEBUG] [co.zo.te.TeleportWaypoint/]: structure waypoint: handled chunk=[-1, -1] starts=0
```

结论：

| 项 | 结果 |
|---|---|
| 监听器在**服务端主线程**被调用 | ✅ 日志线程名为 `Server thread` |
| `isNewChunk()` 门对新区块放行 | ✅ `handled` 行出现在真实新生成区块上 |
| 无结构区块走廉价早退 | ✅ 大量 `starts=0` 行 |
| 名单筛选在真实环境中生效 | ✅ `minecraft:mineshaft` 被白名单模式拒绝（`skipped by tag`） |
| 是否存在异常逃逸 | ✅ 无 `structure waypoint: handler failed` |
| 是否死锁 | **❌ 此前的判定无效（2026-09-15 审查更正）** —— 见下方说明 |

> **为什么这一行不能判定「无死锁」（2026-09-15 审查结论）：**
> 本行的依据是"三个标记都没出现"。但对本次真正发生的死锁，**这三个标记一个都不可能产生**：
> - `Chunk not there when requested`（`ServerChunkCache.getChunk:163`）位于 `:159` 的 `managedBlock` **之后**，卡在 `managedBlock` 时永远走不到；
> - watchdog 自己也需要主线程推进才能报警，主线程一旦卡在 `managedBlock`，它一起被冻住；
> - 因此该死锁的表现就是「静止 + 无日志 + 无 crash-report」——**用"没看到这三样"来判定"没有死锁"，逻辑上是循环的**。
>
> 本节 §1.4 的窗口也**从未触及白名单结构**（本节 §4 第 1 项已自述）：临时日志里唯一的结构是 `minecraft:mineshaft`，而它在白名单模式下被拒绝，`StructureWaypointPlacer.place()` 根本不会执行。所以这次验证**在原理上无法覆盖**死锁路径。

验证后已**移除**临时日志并删除 scratch 世界（保留的 debug 输出仅为 `noPlacement` / `placed` / `skippedByTag` / `budgetExhausted` / `limitReached` 这些每结构至多一次的记录）。

---

## 2. 设计纪律核对（16 条）

| # | 项 | 结果 |
|---|---|---|
| 1 | Java 源码中无 `teleportwaypoint.waypoint.` 硬编码 | ✅ 0 处 |
| 2 | 两语言文件各保留 14 个旧键 | ✅ 各 14 |
| 3 | 两语言文件各新增 13 个 `tpwp.*` 键 | ✅ 各 13 |
| 4 | 禁止的跨区块 API（`level.getBlockState`/`getChunkAt`/`getChunk`/`StructureManager` 等） | ✅ 0 处（出现的匹配全在 javadoc） |
| 5 | `isNewChunk` 门唯一 | ✅ 1 处 |
| 6 | `catch (Throwable)` 唯一且在最外层 | ✅ 1 处 |
| 7 | 无可变静态字段（除 3 个日志去重/重入布尔） | ✅ `whitelistMissingWarned`、`blacklistMissingWarned`、`inHandler` |
| 8 | 结构包内无 `setChanged` / `WaypointManager.register` | ✅ 0 处 |
| 9 | 两标签均为 `"replace": false` | ✅ 各 1 处 |
| 10 | `isValidId` 正则允许 `.` | ✅ `[a-z0-9_]+(?:\.[a-z0-9_]+)*` |
| 11 | 无 `start.getPos()`（1.21.1 不存在该方法） | ✅ 0 处 |
| 12 | 无 `start.getBoundingBox()`（会 `inflatedBy(12)`） | ✅ 0 处 |
| 13 | 无 `getOrCreateTag` 误用 | ✅ 0 处 |
| 14 | 所有 `pos.above(k)` 处有 `maxBuildHeight` 边界保护 | ✅ 2 处均有 |
| **15** | `setChanged` / `blockEntityChanged` / `updateNeighbourForOutputSignal` 在 structure/ 内（排除注释） | ✅ **0 处命中代码**（路径 C：`setChanged()` 会经 `Level.updateNeighbourForOutputSignal` 读邻区块，见 §3.7） |
| **16** | `level.setBlock` / `level.getBlockState` / `level.getChunkAt` / `level.getChunk(` / `level.getFluidState` / `level.getBlockEntity` 在 structure/ 内（排除注释） | ✅ **0 处命中代码**（路径 A/B；`Level` 的区块读写 API 在该包内已写不出来） |

---

## 3. 实施期发现并修正的问题

### 3.1 黑名单 `mineshaft` 必须写成标签引用（设计更正 C10）

设计文档 §5.2 写 `"minecraft:mineshaft"` 并称其为标签。实测**不成立**：`mineshaft` 同时是结构 ID 与标签名，裸 ID 只匹配单个结构，展开为 **21 而非 22**，`mineshaft_mesa` 落进名单缝隙（白名单不含、黑名单不含），在 BLACKLIST 模式下会被误放锚点。

已改为 `"#minecraft:mineshaft"`，并用 `client-extra.jar` 核对 `12 + 22 = 34` 精确成立、无重复无遗漏。该更正已就地写入区块补锚设计文档 §5.2，并由 GameTest `tags_partitionEveryVanillaStructure` 长期钉住。

### 3.2 L2 兜底扫描起点（实现修正，非设计变更）

原按设计文档伪代码从**世界天花板**向下扫，与 64 格上限组合后，该层实际只能覆盖 `y ≥ maxBuildHeight - 64`（主世界即 `y ≥ 256`），对海平面附近（y≈63）的一切结构与下界全部失效——即 L2 兜底层实际上是**死代码**。

已改为从**结构自身的最高 piece 顶部 + 8** 起向下扫，仍受 64 格上限约束。这保持了设计意图（兜底锚点靠近结构、工作量有界），并让该层真正可用。

### 3.3 扫描器必须把水当作可放目标

初版 `isValidPlacement` 要求候选格 `isAir() || canBeReplaced()`。水不满足两者，导致「允许水中放置」（D2）实际不成立。已改为 `isAir() || canBeReplaced() || 是水`，岩浆仍单独排除。由 GameTest `scanner_allowsWater` 钉住。

### 3.4 `humanize("")` 返回 "Empty" 而非 "Unnamed Waypoint"

`humanize(EMPTY_ID)` 会把 `empty` 人工化成 `Empty`，与语言文件的 `tpwp.empty` = "Unnamed Waypoint" 不一致。已新增 `Naming.EMPTY_FALLBACK_NAME`。由 GameTest `naming_humanize` 钉住。

### 3.5 上游「待评审」文档的 10 处冲突

见实施计划 §0.3（C1–C10）。全部以区块补锚文档为准；上游文档**未修改**。

### 3.6 【严重·已修复】放置经 `Level#setBlock` 派生的邻居通知导致主线程自死锁

> **2026-09-15 状态更正：本节原写「（已修复）」，该结论不成立。** 承载那段文字的提交本身就是 `6c97b5e「尝试修复死锁，失败」`；修复上线后死锁**依然复现**。根因分析只覆盖了两条同级路径中的**一条**。
>
> **2026-09-15 二次更正：已按「方案 B」修复并完成复现对照验证，结论见本节末尾「修复与验证证据」。**

**症状：** 游玩一段时间后服务器**突然完全冻结**，不崩溃，`latest.log` / `debug.log` 里没有任何有价值的信息，`crash-reports` 为空。

**根因（2026-09-15 补全）：** `StructureWaypointPlacer` 通过 `Level#setBlock` 放置锚点，而 `Level#setBlock` 会派生**邻居通知**；邻居通知**同步读写相邻区块的方块**。相邻区块在 FULL 阶段不保证已加载，于是进入 `ServerChunkCache` 的 `managedBlock`，等待一个只有主线程自己才能完成的 future —— 而主线程正卡在处理器里。**自死锁。**

**关键更正：这样的派生路径有两条，而不是一条。** 它们由**不同的标志位**把守：

| 路径 | 门槛 | `UPDATE_ALL`(3) | `UPDATE_CLIENTS`(2) | 抑制方法 |
|---|---|---|---|---|
| **A** 红石：`blockUpdated` → `updateNeighborsAt` → `MultiNeighborUpdate.runNext` → `getBlockState(邻居)` | `flags & 1` | 跑 | 跳过 | 去掉 `UPDATE_NEIGHBORS` |
| **B** 形状级联：`updateNeighbourShapes` → `neighborShapeChanged` → `CollectingNeighborUpdater.shapeUpdate` → `NeighborUpdater.executeShapeUpdate` → `getBlockState(邻居)` | **`(flags & 16) == 0`** | **跑** | **照跑** | 只有第 **16** 位（`UPDATE_KNOWN_SHAPE`）能抑制 |

`Block.java:77-86`：`UPDATE_NEIGHBORS=1`、`UPDATE_CLIENTS=2`、`UPDATE_KNOWN_SHAPE=16`、`UPDATE_ALL=3`。**两个值都不含第 16 位**，且 `Level.java:294` 的 `flags & -34` 会把第 1 位与第 32 位一并清掉 —— 因此在路径 B 上 **`UPDATE_ALL` 与 `UPDATE_CLIENTS` 行为完全相同**。这就是「改成 `UPDATE_CLIENTS` 后依然死锁」的确切原因。

**完整调用链（可复核）：**

```
【路径 A】—— 6c97b5e 修掉的那条
Level.setBlock:261          markAndNotifyBlock(...)
Level.markAndNotifyBlock:286  if ((flags & 1) != 0)          // UPDATE_NEIGHBORS = 1
Level.markAndNotifyBlock:287      this.blockUpdated(pos, block)
ServerLevel.blockUpdated:1591     → updateNeighborsAt(pos, block)
ServerLevel.updateNeighborsAt:1115  → neighborUpdater.updateNeighborsAtExceptFromFacing(...)
CollectingNeighborUpdater:48       → addAndRun(...) → runUpdates()   // 同一调用栈内同步执行
MultiNeighborUpdate.runNext:122-123   BlockState bs = level.getBlockState(邻居)
Level.getBlockState:410               → getChunk(..., ChunkStatus.FULL)   // requireChunk = true
Level.getChunk:202                    → ServerChunkCache.getChunk(x, z, FULL, true)
ServerChunkCache.getChunk:159         this.mainThreadProcessor.managedBlock(f::isDone);   // ★ 永久阻塞

【路径 B】—— 6c97b5e 未触及，因此死锁依旧
Level.markAndNotifyBlock:293  if ((flags & 16) == 0 && recursionLeft > 0)   // UPDATE_KNOWN_SHAPE = 16
Level.markAndNotifyBlock:294      int i = flags & -34;                     // 清掉第 1 位与第 32 位
Level.markAndNotifyBlock:296      state.updateNeighbourShapes(this, pos, i, recursionLeft - 1)
BlockBehaviour:781-788            for (6 个方向) level.neighborShapeChanged(...)
Level.neighborShapeChanged:378-380  → neighborUpdater.shapeUpdate(...)
CollectingNeighborUpdater:29-33      → ShapeUpdate(pos = 邻居)
CollectingNeighborUpdater:137-144 ShapeUpdate.runNext → NeighborUpdater.executeShapeUpdate
NeighborUpdater:36                BlockState bs = level.getBlockState(邻居)   // ← 同级路径，同一阻塞点
Level.getBlockState:405-413            → getChunk(..., ChunkStatus.FULL)     // requireChunk = true
ServerChunkCache.getChunk:158-159      this.mainThreadProcessor.managedBlock(f::isDone);   // ★ 同一个永久阻塞
```

**同级还有两条次要路径（同属 `Level` 级写入派生）：**

- **形状级联会跨边界「写」**：`NeighborUpdater:37-38` → `Block.updateOrDestroy` → `level.setBlock(邻居坐标, ...)`。锚点是完整碰撞体（`ModBlocks.java:16-22` 设了 `noOcclusion()` 但**没有** `noCollission()`），因此旁边的栅栏/墙/铁栏杆/玻璃板会来连接它 —— 在村庄、林地府邸、海底神殿里都很现实，于是再次回到同一条阻塞路径。
- **旧方块的 `onRemove` 会触发邻居更新**：`LevelChunk.setBlockState:274` → `BlockBehaviour#onRemove:193-197` → `Level.removeBlockEntity:822-827` 结尾调用 `updateNeighbourForOutputSignal` → `MultiNeighborUpdate.runNext:123 getBlockState(邻居)`。目前只靠扫描器的 `hasBlockEntity()` 判据挡住，`place()` 自身没有复查。

**为什么相邻区块可能没到 FULL：** `ChunkPyramid.GENERATION_PYRAMID` 的 `FULL` 步骤（`:45`）**自身没有任何 requirement**，继承最近一次声明 —— `LIGHT` 的 `addRequirement(ChunkStatus.INITIALIZE_LIGHT, 1)`（`:43`）。因此区块在跑 FULL（也就是 `ChunkEvent.Load` 触发的时刻）时，其 8 个水平邻居**只保证到 `INITIALIZE_LIGHT`**。

**为什么偶发：** 需两个条件同时成立 —— ① 落点落在区块边界列（x/z 为 0 或 15，扫描器不排除边界）；② 该列外侧的相邻区块当时未到 FULL。玩家用旁观模式高速飞行时条件②概率被放大。

**为什么日志里没有线索：**
- `ServerChunkCache.getChunk:163` 的 `IllegalStateException("Chunk not there when requested")` 在 `:159` 的 `managedBlock` **之后**，永远到不了；
- watchdog 本身也需要主线程推进才能报警，一旦卡在这里连它一起冻住；
- 所以表现就是「静止 + 无日志 + 无 crash-report」。

**本次事故的日志指纹（`run/logs`）：**
- `debug.log` 最后一条服务端主线程记录是 `21:51:31.890` 的结构锚点日志，之后主线程再无任何输出（连每 15 秒的自动存档都停），而渲染线程仍在活动（`21:52:18` 仍有 JEI 日志）⇒ 只有服务端线程卡住；
- `21:52:05`、`21:53:05` 两条 `spark: Timed out waiting for world statistics` ⇒ spark 也拿不到 tick 统计；
- 会话期间 `crash-reports` 一份都没有。

**修复（2026-09-15 最终采用：方案 B）：**

```java
// 方案 B（已采用）：绕开 Level，直接写已持有的 LevelChunk。
//   引擎自己的世界生成路径就是这么做的：WorldGenRegion.java:275-284 用 chunk.setBlockState(pos, state, false)。
BlockState previous = chunk.setBlockState(pos, state, false);
if (previous == null) {
    return false;                       // 状态未变，按失败处理
}
level.onBlockStateChange(pos, previous, state);   // 镜像 Level#setBlock 唯一有价值的副作用
```

```java
// 方案 A'（未采用，仅作记录）：显式抑制路径 B
//   18 = UPDATE_CLIENTS(2) | UPDATE_KNOWN_SHAPE(16)
level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
```

**为什么采用方案 B：** 它一次性把上述**三条**路径（读级联、跨边界写、`onRemove` 邻居更新）全部从结构上消除，而不是靠标志位逐个堵；同时顺带消掉 `waypoint.setId()` → `setChanged()` → `Level.blockEntityChanged:984-988` → `getChunkAt` 这条同样派生自 `Level` 的传递性入口。方案 A' 只能堵路径 B，堵不住另外两条，而且把「安全」重新变成需要人来推理的标志位问题。

**伴随改动：**
- `place()` 的形参由 `Level`/`ServerLevel` 收窄用途：只保留 `ServerLevel` 供 `onBlockStateChange` 这一次调用，并在 javadoc 里写明它**不用于方块访问**——`Level` 的写读 API（`setBlock`/`getBlockState`/`getChunkAt`/`getChunk`/`getFluidState`/`getBlockEntity`）与整个 `StructureManager` 接口在这个包里已经写不出来。
- 写入前复查前置条件（目标格无 BE 且为空气/可替换/水），使 `place()` 不再依赖调用方。
- 修正了原先「`pendingBlockEntities` 里不可能有该位置」的错误注释：该 map 由 `ProtoChunk` 灌入，直到 `postProcessGeneration()` 才清空，事件时刻**可能仍有条目**；真实理由是 `setBlockState` 刚把新 BE 注册进 `blockEntities`，查询先命中该 map，走不到 pending 提升路径。

**必须接受的语义代价：** `chunk.setBlockState` 不做邻居通知，因此锚点旁边原有的栅栏/墙/玻璃板/铁栏杆**不会来连接它**。对当前锚点方块完全无害（完整方块、无红石逻辑、无形状依赖，`SimpleWaterloggedBlock` 的流体状态来自方块自身状态）。**若将来给锚点加 direction(facing) 或连接性属性，必须重新评估这一条。**

**修复位置：** `structure/StructureWaypointPlacer.java`（唯一的 `setBlock` 调用点已不存在）。

---

#### 修复与验证证据（2026-09-15）

**验证方式：** 专用服务器 + RCON 驱动，`debugMode = true`，新世界。这是唯一有判别力的验证 —— GameTest 抓不到这个 bug（超平坦无结构、邻居全满加载、且主线程卡死时 `tickInternal` 停止推进导致超时永不触发，`runGameTestServer` 会永久挂起而不是失败）。

**复现步骤与实测输出（修复后）：**

```
RCON> locate structure minecraft:ancient_city
      The nearest minecraft:ancient_city is at [-3024, ~, 2480]
RCON> forceload add -3040 2464 -2992 2512
      Marked 16 chunks in Overworld from [-190, 154] to [-187, 157] to be force loaded

[Server thread/DEBUG] structure waypoint: placed structure=minecraft:ancient_city pos=BlockPos{x=-3022, y=-35, z=2480} waterlogged=false
[Server thread/INFO]  [LocateCommand]: Locating element minecraft:ancient_city took 357 ms
[Server thread/INFO]  [MinecraftServer]: [Rcon: Marked 16 chunks ... to be force loaded]
[Server thread/WARN]  [MinecraftServer]: Can't keep up! Is the server overloaded? Running 5755ms or 115 ticks behind
```

关键点：**`placed` 那行之后服务端线程仍在处理命令**（`Marked 16 chunks` 是 RCON 回应，`Can't keep up` 是 tick 落后告警而不是冻结）。修复前同样的场景里，服务端线程会在 `placed` 附近静默停止且再无输出。

**继续加压，确认不复现：**

| 结构 | 结果 |
|---|---|
| `minecraft:ancient_city` | `placed pos=(-3022, -35, 2480) waterlogged=false` |
| `minecraft:mansion`（林地府邸，三个历史事故之一） | `placed pos=(-6060, 76, -9861) waterlogged=false` |
| `minecraft:monument`（海底神殿） | `placed pos=(-384, 52, -496) waterlogged=true`（水中，含水标记正确） |
| `minecraft:trial_chambers` | `probe budget exhausted ... calls=2000` → `no valid placement`（符合预算设计，非冻结） |
| `minecraft:mineshaft` | `skipped by tag ... mode=WHITELIST`（名单生效） |

**存活证明（主线程仍在推进 tick）：**

```
RCON> time query gametime   →  The time is 3257
（15 秒后）
RCON> time query gametime   →  The time is 3560      // 推进 303 tick ≈ 15 s，tick 循环正常
```

**线程栈证明（无 `managedBlock` 自阻塞）：** `jstack` 抓到 Server thread 处于
`MinecraftServer.waitUntilNextTick → waitForTasks → LockSupport.parkNanos`
—— 这是**正常的两 tick 之间空转**，不是 `ServerChunkCache.getChunk → managedBlock` 那种自等待。

**落盘证明（锚点真的写进存档了）：** `save-all flush` 后用区域文件扫描工具读回：

```
chunk(-189,155) idx=867 inflated 115776 bytes
mentions teleportwaypoint=true waypoint=true ancient_city=true block_entities=true
```

> 对照：用户冻结存档里的远古城市区块（75,−144）扫描结果是 `teleportwaypoint=false waypoint=false`
> —— 注入尝试了但没写成，正是死锁的痕迹。修复后同一类区块（−189,155）里锚点**正常存在**。

**结论：** 路径 B（以及另外两条同级路径）已从结构上消除；上面三类结构全部成功注入且服务器全程可响应、tick 持续推进、锚点正确落盘。

**这个坑为什么之前没被发现（技术层面）：** 实施计划 §0.5 的 E3 断言「事件期间对本区块调 `level.setBlock` 会短路返回，不阻塞」——**该断言本身正确，但只覆盖了 `setBlock` 的第一次区块查找**（`getChunkAt(pos)`，pos 在事件区块内）。它没有覆盖 `setBlock` **派生出的邻居通知**，而邻居通知读/写的是**其他**区块。E3a 补充时又只补了路径 A（`UPDATE_NEIGHBORS`），**路径 B 自始至终没被分析到**。

**为什么没被发现（流程层面，同样重要）：**

1. **错误的修复被文档背书。** `6c97b5e` 不只改了代码，还把实施计划的**允许清单 A2** 从 `UPDATE_ALL` 改成 `UPDATE_CLIENTS`、新增 F6/R17、并加了评审第 15/16 条 —— 等于用"改规范"去迁就一个错误的修复。第 16 条尤其讽刺：它点了 `UPDATE_KNOWN_SHAPE` 的名字，却只要求该 token **不出现**，于是检查通过而 bug 存活。
2. **新增的两个回归测试对该 bug 零效力。** `placer_writesWaypointWithoutNeighbourUpdates` 与 `placer_setsWaterloggedInWater` 在 `ChunkEvent.Load` **之外**直接调 `place()`，且只断言"放置成功"，从不检查标志位、邻居或邻接性 —— 把实现改回 `UPDATE_ALL` 它们照样全绿。名字里写着 `WithoutNeighbourUpdates`，却不检验邻居更新，反而在 javadoc 里断言了一个**对 `UPDATE_CLIENTS` 不成立的前提**。
3. **GameTest 在原理上无法发现此类死锁。** 超时基于 `tickCount`（`GameTestInfo.tickInternal`），而主线程卡死时 tick 循环停止 ⇒ `tickCount` 永不增长 ⇒ **超时永不触发**，`runGameTestServer` 是**永久挂起**而非失败，且 `build.gradle` 没有给 run 任务设超时。GameTest 世界还是**超平坦且无结构**的，`getAllStarts()` 恒为空，处理器在第 4 道门就退出；而测试自身的写入用的是 `helper.setBlock`（`UPDATE_ALL`）、邻居全部满加载 —— 生产前置条件（边界列 + 界外邻居未达 FULL）在该环境里**构造不出来**。
4. **「日志里没有 `placed` ⇒ 放置没执行」是无效推理。** `placed`/`noPlacement`/`skippedByTag` 全部经 `StructureWaypointDebug.debug()` 受 `debugMode` 门控（`StructureWaypointDebug.java:23-27`），而 `common.toml` 当前是 `debugMode = false`；该配置在多轮运行之间被切换过，所以"没有日志行"对多数运行**不构成证据**。真正可靠的只有**不受开关管**的两个 WARN：`reentered` 与 `failed` —— 它们 0 命中，才说明模组从未抛异常、从未重入。

**已验证发生的死锁现场（`run/logs`，三例）：**

| 日志 | 世界 | 指纹 |
|---|---|---|
| `2026-09-14-7.log.gz` | 大肥鱼的结构测试 | `21:43:58` 激活「林地府邸」锚点 → 服务端线程最后一行 21:45:04 → `21:46:05 spark: Timed out waiting for world statistics` → 之后无声 |
| `2026-09-14-6.log.gz` | 传送锚点测试1 | 21:49:51 / 21:50:31 激活锚点 → `21:51:25` 切旁观者（飞向新地形）→ `21:52:05`、`21:53:05` spark 超时 → 无 `Stopping server`、无 crash-report |
| `2026-09-15-3.log.gz` | 传送锚点测试2 | `00:38:48 LocateCommand: Locating element minecraft:ancient_city`（白名单结构）→ `00:39:05`、`00:40:05` spark 超时；渲染线程仍在工作、客户端退出时**服务端没有 Saving chunks / Stopping server** |

三例共同特征：**渲染线程存活、服务端线程哑掉、spark 报「等待世界统计超时」、无 crash-report** —— 与主线程卡在 `managedBlock` 的机制完全吻合。

**顺带排除的三个假设（省得后人重查）：**

- **光照引擎不会死锁**：`LevelChunk.setBlockState:260,268` → `ThreadedLevelLightEngine.addTask` 只是往邮箱投递；引擎内唯一的区块查询 `LightEngine.getChunk` → `ServerChunkCache.getChunkForLighting:267-271` 是纯 map 查找，不阻塞。
- **扫描器在结构上不可能跨区块**：`LevelChunk.getBlockState:178-211` 用 `x & 15` / `z & 15` 取址，越界只会**别名到本区块另一列**；`y` 越界直接返回 AIR。它手里根本没有 `Level`（`ChunkAccess` 只持有 `levelHeightAccessor`）。
- **`isFaceSturdy(chunk, ...)` 对原版方块取缓存路径**，不触碰传入的 BlockGetter（只有脚手架/竹子/潜影盒等 `dynamicShape()` 方块才走实时形状），且传入的 BlockGetter 就是我们给的 `LevelChunk`。

### 3.7 【严重·已修复】路径 C：`BlockEntity.setChanged()` → `Level.updateNeighbourForOutputSignal`

> **2026-09-15 第三次事故。** §3.6 的方案 B（`chunk.setBlockState`）上线后，**沙漠神殿处依旧冻结**。根因是同一类死锁的**第三条派生路径**，与更新标志位完全无关，因此 §3.6 的两条路径被切断并不足以消除它。

**症状与判定：** 与 §3.6 完全相同（服务端线程静默停止、无 crash-report、spark 报 world statistics 超时、存档无法保存只能强杀）。日志指纹：

```
[13:41:15] LocateCommand: Locating element minecraft:desert_pyramid took 2164 ms
[13:41:15] WARN  Can't keep up! ... Running 2144ms or 42 ticks behind
[13:41:19] DEBUG structure waypoint: placed structure=minecraft:desert_pyramid pos=BlockPos{x=3609, y=67, z=2313}
[13:41:19] INFO  [Dev: 已将Dev传送至3600.500000, 110.501546, 2304.500000]
          ← 服务端线程此后无任何输出
[13:42:05] spark: Timed out waiting for world statistics
```

注意 `placed` 行**在 `setId` 之前打印**（`placed` 是 `place()` 的倒数第二句），所以日志看着"放置成功"，卡死其实发生在随后的 `setId` 内部。

**根因：** `StructureWaypointPlacer.place()` 用 `waypoint.setId(waypointId)` 写 id，而 `setId` 内部调 `setChanged()`：

```
StructureWaypointPlacer.place()          waypoint.setId(waypointId)
WaypointBlockEntity.setId()                  setChanged()
BlockEntity.setChanged():193                 setChanged(this.level, this.worldPosition, this.blockState)
BlockEntity.setChanged(l,p,s):200            level.updateNeighbourForOutputSignal(pos, state.getBlock())
Level.updateNeighbourForOutputSignal:1125    for (Direction direction : Direction.values()) {
                                  :1127        if (this.hasChunkAt(blockpos)) {
                                  :1128            BlockState bs = this.getBlockState(blockpos);   ★
                                  :1130            if (bs.isRedstoneConductor(...)) {
                                  :1132                bs = this.getBlockState(blockpos);           ★ 第二跳无守卫
Level.getBlockState:410                          → getChunk(x, z, ChunkStatus.FULL)   // requireChunk = true
ServerChunkCache.getChunk:158-159                this.mainThreadProcessor.managedBlock(f::isDone);  // ★ 永久阻塞
```

**为什么 `hasChunkAt` 拦不住（关键）：**

```java
// ServerChunkCache:251-252
private boolean chunkAbsent(ChunkHolder h, int status) {
    return h == null || h.getTicketLevel() > status;
}
```

`hasChunkAt` 只比较**票级够不够到 FULL**，**不检查该区块是否已跑完 FULL**。一个正在生成、票级已达标的邻区块会**通过**守卫，紧接着 `getBlockState` 就发起 `requireChunk = true` 的同步补全，落到 `managedBlock` —— 等主线程自己的邮箱，而主线程正在处理器里。`:1132` 的第二跳更是**完全没有守卫**。

**为什么与更新标志位无关：** 这条链走 `BlockEntity` → `Level.updateNeighbourForOutputSignal`，**不经过 `Level#markAndNotifyBlock`**，所以 `UPDATE_ALL` / `UPDATE_CLIENTS` 的区别、乃至改用 `chunk.setBlockState` 都影响不到它。这就是 §3.6 的方案 B 修不掉它的原因。

**为什么偏偏是沙漠神殿、为什么前三轮没复现：** 需要「落点所在列的水平邻居中，存在票级达标但 FULL 未完成的区块」这个精确时序。沙漠神殿是 21×21 的 `SinglePieceStructure`，piece 盒跨多区块，而扫描器第一排序键是「贴近 piece 中心」，落点被推到贴近区块边界；再叠加 `/locate` 刚造成的 2.1 秒生成积压，界外邻居处于该窗口的概率最高。**验证时的宽域 forceload 会把邻居提前加载好，从而永远碰不到这个窗口** —— 这正是 §3.6 的验证方式没能发现它的原因（教训）。

**修复（方案 A）：** 让注入路径不再触发 `setChanged()`。`setChanged()` 做两件事，只有一件是需要的：

| `setChanged()` 的动作 | 处理 |
|---|---|
| `Level.blockEntityChanged` → `getChunkAt` → `setUnsaved(true)`（标脏） | 换成 `ChunkAccess#setUnsaved(boolean)`（public，`ChunkAccess.java:269`），作用在**已持有的 chunk** 上，`Level` 不参与 |
| `Level.updateNeighbourForOutputSignal`（通知邻居红石输出变化） | **整个去掉** —— 锚点方块没有红石输出，邻居无可观察 |

新增 `WaypointBlockEntity.setIdWithoutNeighbourUpdate(String)`（保留 `isValidId` 校验，不置脏），`setId` 改为调用它后再 `setChanged()`，校验逻辑仍只有一份。放置器改用新方法并显式 `chunk.setUnsaved(true)`。

**参照系：** `WorldGenRegion.setBlock:275-284` 就是 `chunkaccess.setBlockState(...)` + `level.onBlockStateChange(...)`，全程不调 `setChanged()`。也就是说"生成期不置脏"正是引擎自己的做法，方案 A 是贴合引擎而非绕开它。

**语义代价（必须接受）：** `updateNeighbourForOutputSignal` 被去掉。对当前锚点方块无影响。**若将来给锚点加红石/比较器可读输出，此决定必须重新评估。**

**修复位置：** `block/entity/WaypointBlockEntity.java`（新增方法 + `setId` 改一行）、`structure/StructureWaypointPlacer.java`（尾部写入改为新方法 + `chunk.setUnsaved(true)`）。

#### 修复与验证证据（2026-09-15，方案 A）

**结构性核对（本次的核心防线）：**

```
rg 'setChanged|blockEntityChanged|updateNeighbourForOutputSignal|level\.setBlock|level\.getBlockState|
    level\.getChunkAt|level\.getChunk\(|level\.getFluidState|level\.getBlockEntity'  structure/
（排除注释行）→ 0 处命中
```

`structure/` 包内已经不存在任何能经 `Level` 访问区块的调用 —— 三条路径（A/B/C）从同一处一起消失，而不是逐个堵。

**运行时对照（窄域，这是唯一有判别力的方式）：**

```
RCON> locate structure minecraft:desert_pyramid   →  [1136, ~, -7584]
       （该次 locate 造成 16242 ms 卡顿 + "Can't keep up! Running 16237ms or 324 ticks behind"
         —— 正是用户现场那个前置条件）
RCON> forceload add 1136 -7584 1136 -7584        →  Marked chunk [71, -474]
[Server thread/DEBUG] structure waypoint: placed structure=minecraft:desert_pyramid pos=BlockPos{x=1145, y=63, z=-7575}
RCON> forceload add 1120 -7600 1152 -7568        →  Marked 8 chunks   ← placed 之后服务端继续处理命令
RCON> time query gametime                        →  727 → 1030 → 1531   （持续推进）
```

**再叠加 4 轮「卸载 / 存档 / 重新加载」循环**（模拟玩家离开再回来）：

```
cycle 1  unload → save → load 24 chunks → gametime 2410
cycle 2  ...                          → gametime 3059
cycle 3  ...                          → gametime 3705
cycle 4  ...                          → gametime 4350
```

全程无冻结，`time query gametime` 稳定推进（每轮约 640 tick / 27 s，符合 20 TPS）。

**落盘证明（修复后 id 真的持久化了）：**

```
save-all flush 后扫描 r.2.-15.mca：
chunk(71,-474) inflated=7071  mentions teleportwaypoint=true waypoint=true block_entities=true
```

**新增回归测试（22 个 GameTest 全绿）：**
- `placer_idIsSetAndPersisted` —— 断言 id 已写入 BE、**chunk 处于 unsaved 状态**（否则 id 不会落盘）、以及 `saveWithoutMetadata` 的 NBT 里确实带 `waypoint_id`；同时断言非法 id 仍被拒绝且不覆盖已存 id。
- `placer_setsWaterloggedInWater` —— 修正 fixture：原先"水面下方是空气"的水源会在测试跑动前流走（实测偶发失败），改为**贴地的一格深水盆**，并加一条 fixture 自检断言。

### 3.8 【严重·已修复】`Naming.key()` 无条件拼 `tpwp.` 导致旧存档锚点名全部损坏

**症状：** 0.4.0 里所有**旧存档**的锚点显示名都变成 "Unnamed Waypoint"（或裸键），即设计文档 §7.3 与验收表承诺的「旧锚点名字不变」实际是**假的**。

**根因：** `Naming.key()` 无条件返回 `"tpwp." + id`。旧存档的 `waypoint_id` 是**裸值**（`end_city`、`jungle_temple`、`nether_fortress`、`ocean_monument`、`woodland_mansion` …），于是解析成 `tpwp.end_city` 这类**语言文件里根本不存在**的键：

| 旧 `waypoint_id` | `key()` 曾得到 | 语言文件里有吗 |
|---|---|---|
| `end_city` | `tpwp.end_city` | ❌ |
| `ancient_city` | `tpwp.ancient_city` | ❌ |
| `jungle_temple` | `tpwp.jungle_temple` | ❌ |
| …（13 个旧 id 全部如此） | | ❌ |

后果连锁：语言文件里保留的 14 个 `teleportwaypoint.waypoint.*` 旧键**没有任何代码路径能到达**，成了死字符串；而 `humanize()` 回退**从未被生产代码调用**（只有 GameTest 用）。

**修复（三级回退）：**

1. `tpwp.<id>` —— 当前语言有该键则用；
2. 否则 `teleportwaypoint.waypoint.<id>` —— 激活保留的旧键，旧存档名字恢复；
3. 都没有 —— `Component.translatableWithFallback(key, humanize(id))`，渲染为人工化名（如 `End City`）而不是裸键。

**实现要点：**
- 新增 `Naming.modernKey(id)` / `Naming.legacyKey(id)`（**不查语言表**，处处稳定）与 `Naming.hasTranslation(key)`。
- `displayName()` 改为 `Component.translatableWithFallback(key(id), humanize(id))`，让 `humanize()` 真正进入生产路径。
- 语义细节：**`key()` 的选择在渲染时按客户端的语言表决定**；服务端构造的组件只是携带「键 + 人工化回退文本」过线，客户端解析。因此服务端调用 `displayName()` 也不会写死错误的键 —— `TranslatableContents` 在客户端按 key 查表，查不到才用 fallback。
- Xaero 路径（`XaeroMinimapIntegration`）之前也**没有**实现设计文档 §5.6 要求的「键是否存在」检查，会把裸键当名字显示。新增 `resolveMapName(id)` 做同样三级回退，返回字符串。

**验证：** 新增 GameTest `naming_legacyIdsStillNameThemselves` —— 13 个旧裸 id 逐个断言 `modernKey`/`legacyKey` 形态，并断言 `displayName()` 的回退文本**不得**退化为 `EMPTY_FALLBACK_NAME` 且必须等于 `humanize(id)`。修复前该断言会在 13 个 id 上全部失败。

### 3.9 设计调整：新增总开关 + 黑白名单改版（2026-09-15）

**决策反转记录：** 设计文档 §5.2 / Q9 曾明确「村庄从白名单移除，不需要适配」。本次按作者要求**反转**：村庄与掠夺者前哨站**加入白名单**，同时从黑名单移除。`memory/decisions-log.md` 已同步。

**① 新增总开关 `structureWaypoints.enabled`（默认 `true`）**

放在 `StructureWaypointHandler.onChunkLoad` 的**最前面**（在 `instanceof ServerLevel` 之前），因此关闭后区块加载路径上只剩「事件派发 + 一次配置布尔读取」。`ModConfigSpec.ConfigValue.get()` 本身是缓存字段读，无需自建缓存。

代价（已写进配置注释与 README）：**修改后需重启服务器才生效**。

**② 名单改版**

| | 改前 | 改后 |
|---|---|---|
| 白名单 | 12 条 → 12 个结构 | **13 条 → 18 个结构**（+`minecraft:pillager_outpost`，+`#minecraft:village` 覆盖 5 变体） |
| 黑名单 | 9 条 → 22 个结构 | **7 条 → 16 个结构**（移除 `#minecraft:village` 与 `minecraft:pillager_outpost`） |

两名单保持**不相交**；34 个原版结构中 18 + 16 = 34，即**每种结构都至少被一种模式放行**。

新增 5 个中文翻译键：`tpwp.minecraft.village_{plains,desert,savanna,snowy,taiga}` → 平原/沙漠/热带草原/雪原/针叶林村庄。旧键 `teleportwaypoint.waypoint.village` 仍保留为别名。

**③ 测试更新**

- `tags_partitionEveryVanillaStructure` → 重命名为 `tags_listsMatchTheirIntendedSemantics`。原断言「白名单 + 黑名单 = 34 精确划分」在新语义下不成立，改为：白名单 18、黑名单 16、两名单不相交、**34 个结构全部可达**（`inWhite || inBlack`），以及村庄/前哨站既在白名单又不在黑名单。
- `tags_whitelistIsLoadedAndComplete` —— 反转村庄断言（5 个变体必须经 `#minecraft:village` 命中），并新增「`buried_treasure` 不得进白名单」。
- `tags_blacklistCoversMineshaftMesa` —— 移除村庄/前哨站条目，并新增「它们不得出现在黑名单」。
- `placer_setsWaterloggedInWater` —— 再次加固 fixture：改为**贴地、四周有石墙的 9×9 水池**。先前 3×3 水盆仍会偶发流干（实测 `found Block{minecraft:air}`）；现在 fixture 自带前置断言，若再流干会直接报告 fixture 问题而不是误报放置失败。

**④ 运行时验证（开关对照，新世界 + RCON）**

| 步骤 | `enabled = true` | `enabled = false` |
|---|---|---|
| `locate structure minecraft:desert_pyramid` | 沙漠神殿 ✓ | 沙漠神殿 ✓ |
| `forceload` 生成该区域 | 9 区块 | 9 区块 |
| 处理器日志 | `placed structure=minecraft:desert_pyramid pos={585,63,-839}` | **完全没有 structure waypoint 日志** |
| 落盘区块内容 | 含 `teleportwaypoint` | `mentions teleportwaypoint=false waypoint=false`（区块已生成、有 block_entities，但无锚点） |

两次运行都使用全新世界、`debugMode=true`，因此"没有日志"是门生效而不是日志被开关挡掉。

### 3.10 遗留观察（未修改，供后续评估）

L2 兜底扫描的起点是「结构最高 piece 顶部 + 8」，配合 64 格上限，意味着当某列在 `[结构顶 + 8 - 64, 结构顶 + 8]` 区间内没有可用地板时 L2 就会放弃该列。对于「piece 包围盒明显低于其落点」的结构（例如雪屋这类结构顶在地表之上、piece 盒顶却低于地表），L2 可能够不到地表。本轮未观察到实际影响（游戏中多次放置全部成功），但值得后续用实测确认。


---

## 4. 尚未验证的项

| # | 项 | 原因 | 建议 |
|---|---|---|---|
| 1 | **锚点实际放置到真实结构里** | 受控世界生成难以在无玩家干预下命中白名单结构。已验证到「处理器在新区块上运行 + 名单筛选生效 + 无异常」这一步；`findPlacement` → `place` 的链路由 GameTest 覆盖 | 见 §5 游戏内清单第 1–5 步 |
| 2 | 玩家可见的显示名与聊天消息 | 需要客户端 | 见 §5 第 6 步 |
| 3 | Xaero 地图显示新锚点 | 需要客户端 | 见 §5 第 10 步 |
| 4 | 性能实测 M1–M4 | 需要 spark profiler 与真实游玩 | 见 §5 第 11 步 |
| 5 | 旧存档升级后的双份锚点手动关闭 | 需要真实旧存档 | 见 §5 第 8 步 |

---

## 5. 游戏内验收清单（待人工执行）

前置：用开发客户端 `gradlew.bat runClient`，新世界，`debugMode=true`。

| # | 步骤 | 期望 |
|---|---|---|
| 1 | 飞向未探索区域 | 日志出现 `structure waypoint: placed structure=… pos=… waterlogged=…` |
| 2 | `/locate structure minecraft:igloo` 后传送 | 雪屋内有**恰好 1 个**锚点 |
| 3 | 海洋神殿 / 水下结构 | 锚点在水里，F3 看方块状态为 `waterlogged=true` |
| 4 | 村庄 | **没有**锚点（白名单模式，村庄在黑名单里） |
| 5 | 下界要塞 / 要塞 / 远古城市 / 试炼密室 / 沙漠神殿 | 各有**恰好 1 个**锚点 |
| 6 | 右键锚点 | 激活消息显示正确本地化名（如「远古城市」） |
| 7 | `/data get block <pos>` | `waypoint_id` = `minecraft.ancient_city`（带点）；`uid` 存在 |
| 8 | **旧存档**（0.3.0 建的，含旧数据包锚点） | 旧锚点名不变；新区块不补锚；**手动关闭两个数据包后**新区块无双份锚点 |
| 9 | 同一结构的两个实例 | `waypoint_id` **相同**、`uid` **不同** |
| 10 | **破坏**锚点 → 离开区块再回来 | **不再生成**（D1 核心语义） |
| 11 | 世界重启 → 回到同一地点 | 已有锚点仍在，**不重复** |
| 12 | Xaero 小地图 / 世界地图 | 显示新锚点，名字与新键一致，按维度隔离 |
| 13 | `debugMode=false` 重启 | 日志中**无**任何 `structure waypoint:` 行 |
| 14 | 专用服务器 + 原版客户端 | 聊天消息**不显示裸键** |
| 15 | 负面信号 | 日志无 `"Chunk not there when requested"`、无 watchdog 超时、无 worldgen `CrashReport` |
| 16 | 性能（spark） | 记录新区块生成速率与单次放置耗时（含光照），据实施计划 §3.2 回填 `MAX_STRUCTURES_PER_CHUNK` / `PROBE_BUDGET` |

---

## 6. 交付物

| 类型 | 内容 |
|---|---|
| 新增 Java（6+1+1） | `structure/{StructureWaypointHandler, StructureWaypointScanner, StructureWaypointPlacer, StructureTagFilter, StructureWaypointNaming, StructureWaypointDebug}.java`、`util/Naming.java`、`StructureWaypointGameTests.java` |
| 新增资源（3） | `data/teleportwaypoint/tags/worldgen/structure/waypoint_{whitelist,blacklist}.json`、`data/teleportwaypoint/structure/structurewaypointgametests.nbt` |
| 修改 Java（7） | `block/entity/WaypointBlockEntity.java`、`config/CommonConfig.java`、`config/StructureWaypointMode.java`（新增）、`client/ClientWaypointInfo.java`、`network/ActivatedWaypointInfo.java`、`client/xaero/XaeroMinimapIntegration.java`、`TeleportWaypoint.java` |
| 修改资源（2） | `assets/teleportwaypoint/lang/{en_us,zh_cn}.json` |
| 修改文档（3） | `README.md`、`gradle.properties`、区块补锚设计文档 §5.2（就地更正 C10） |
| **未修改** | `docs/plans/2026-09-14-structure-waypoint-injection-design.md`（保持「待评审」原样）、两份内置数据包、全部既有网络 payload |

**线格式与协议：无改动。** `waypoint_id` 不含 `tpwp.` 前缀，前缀只在客户端拼接显示名时添加。
