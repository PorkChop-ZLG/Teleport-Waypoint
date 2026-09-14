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
→ All 20 required tests passed :)
```

连续多次运行均通过（用于排除偶发）。覆盖的 20 个测试：

| 分组 | 测试 |
|---|---|
| 命名（6） | `naming_vanillaIds`、`naming_modStructures`、`naming_truncation`、`naming_humanize`、`naming_keys`、`displayName_notEmptyFallback` |
| id 合法性（1） | `idPattern_acceptsDot` |
| 扫描几何（5） | `scanner_picksHighestRoofedSpot`、`scanner_fallsBackToUnroofedPass`、`scanner_chunkFallbackStaysInChunk`、`scanner_invalidStartIsIgnored`、`scanner_excludesBlockEntities` |
| 水体放置（1） | `scanner_allowsWater` |
| 标签（4） | `tags_whitelistIsLoadedAndComplete`、`tags_blacklistCoversMineshaftMesa`、`tags_partitionEveryVanillaStructure`、`tagFilter_doesNotThrowInEitherMode` |
| 端到端 id（1） | `placedWaypointKeepsStructuredId` |

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
| 是否死锁 | ✅ 无 `Chunk not there when requested`、无 `A single server tick took …`、无 `CrashReport` |

验证后已**移除**临时日志并删除 scratch 世界（保留的 debug 输出仅为 `noPlacement` / `placed` / `skippedByTag` / `budgetExhausted` / `limitReached` 这些每结构至多一次的记录）。

---

## 2. 设计纪律核对（14 条）

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

### 3.6 【严重】放置用 `UPDATE_ALL` 导致主线程自死锁（已修复）

**症状：** 游玩一段时间后服务器**突然完全冻结**，不崩溃，`latest.log` / `debug.log` 里没有任何有价值的信息，`crash-reports` 为空。

**根因：** `StructureWaypointPlacer` 用 `level.setBlock(pos, state, Block.UPDATE_ALL)` 放置锚点。`UPDATE_ALL` 含 `UPDATE_NEIGHBORS`，会触发**邻居更新级联**，而级联会**同步读取相邻区块的方块**。相邻区块在 FULL 阶段不保证已加载，于是进入 `ServerChunkCache` 的 `managedBlock`，等待一个只有主线程自己才能完成的 future —— 而主线程正卡在处理器里。**自死锁。**

**完整调用链（可复核）：**

```
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
```

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

**修复（方案 A）：** 改用 `Block.UPDATE_CLIENTS`，即去掉 `UPDATE_NEIGHBORS`，邻居级联不再发生。该方块不依赖邻居通知（无红石、无形状逻辑，`SimpleWaterloggedBlock` 的流体状态来自方块状态），光照与客户端更新由 `LevelChunk.setBlockState` 独立排队，不受影响。

**修复位置：** `structure/StructureWaypointPlacer.java:83`。

**这个坑为什么之前没被发现：** 实施计划 §0.5 的 E3 断言「事件期间对本区块调 `level.setBlock` 会短路返回，不阻塞」——**该断言本身正确，但只覆盖了 `setBlock` 的第一次区块查找**（`getChunkAt(pos)`，pos 在事件区块内）。它没有覆盖 `setBlock` **派生出的邻居更新级联**，而级联读的是**其他**区块。E3 已就地更正并新增 E3a，禁止清单新增 F6，风险登记新增 R17，评审清单新增第 15/16 条长期钉住。

### 3.7 遗留观察（未修改，供后续评估）

L2 兜底扫描的起点是「结构最高 piece 顶部 + 8」，配合 64 格上限，意味着当某列在 `[结构顶 + 8 - 64, 结构顶 + 8]` 区间内没有可用地板时 L2 就会放弃该列。对于「piece 包围盒明显低于其落点」的结构（例如雪屋这类结构顶在地表之上、piece 盒顶却低于地表），L2 可能够不到地表。本轮未观察到实际影响（游戏中 4 次放置全部成功），但值得后续用实测确认。


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
