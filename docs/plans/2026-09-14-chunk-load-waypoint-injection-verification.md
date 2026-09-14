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
→ All 18 required tests passed :)
```

连续两次运行均通过（用于排除偶发）。覆盖的 18 个测试：

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
