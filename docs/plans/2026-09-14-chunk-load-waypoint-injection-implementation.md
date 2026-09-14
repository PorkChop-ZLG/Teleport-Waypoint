# 区块补锚（Chunk-Load Waypoint Injection）实施计划

**日期:** 2026-09-14
**目标版本:** 0.3.0 → 0.4.0
**设计依据:** `docs/plans/2026-09-14-chunk-load-waypoint-injection-design.md`（**已定案**，本计划不推翻其中任何一条决策）
**状态:** 待评审

---

## 0. 本计划的范围与前提

### 0.1 一句话目标

让每一个**首次生成**的区块在正式启用时检查自己是否含结构，若该结构通过数据包标签黑白名单筛选，就按一套确定性规则在**本区块内**补放一个传送锚点方块，随后完全复用既有的登记／同步／渲染／地图链路。

### 0.2 已定案决策 → 本计划落点对照

| 决策 | 内容 | 落在本文档 |
|---|---|---|
| D1 | 只用 `isNewChunk()` 一个门；零存储、零去重名单 | T15（门 3）、T16（禁用/禁用/禁用） |
| D2 | 两层候选 L1→L2；允许水；岩浆一律排除 | T11（`isValidPlacement`）、T12（放置器） |
| D3 | `waypoint_id = <namespace>.<path>`，无映射表 | T2（`deriveId`） |
| D4 | 旧键保留为别名，不改玩家存档 | T6（14 个旧键原样保留） |
| D5 | 保留旧数据包，两个默认值改 `false` | T4 |
| D6 | 黑白名单用数据包标签，名硬编码，`"replace": false` | T7 + T8（枚举）、T14（筛选）、T5（`mode` 配置） |
| D7 | 新增 `debugMode`，全部 debug 日志走它且文本英文 | T5（配置）、T9（`Debug` 工具类） |
| D8 | 规则 C：自上而下 + 屋顶判据 N=8，偏好非硬约束 | T10（`roofed`）、T11（两遍循环） |
| D9 | piece 体积降序逐个试、成功即停；锚点列仅排序权重 | T10（`orderPieces`）、T11（外层循环） |
| D10 | 锚点显示名前缀 `tpwp.`；仅显示名，线格式不变 | T3（`Naming` 工具类）+ T6 |

### 0.3 与上游「待评审」文档的冲突记录（按区块补锚文档为准）

上游 `2026-09-14-structure-waypoint-injection-design.md` 保持原样不改。以下冲突以本轮已定案文档为准，实现时**不要**照抄上游的对应段落：

| # | 上游（待评审）的写法 | 本计划采用（已定案） | 上游出处 |
|---|---|---|---|
| C1 | 去重 = 「锚点方块自身为标记 + 邻近检查」，接受「锚点被拆后重生」 | **零去重**：只认 `isNewChunk()`，锚点被拆**不重生**，不做邻近检查 | §5.4 |
| C2 | `backfillExistingChunks` 配置项 | **取消该配置项**（D1 的直接推论），配置面无此项 | §5.4、§10.2 |
| C3 | 落点四层 L1 结构内干燥→L2 结构内水中→L3 区块干燥→L4 区块水中；**显式拒绝水** | **两层** L1 结构内 → L2 区块兜底；**允许水**（放置器显式写 `WATERLOGGED=true`），只排除岩浆 | §5.5 第 5 条 |
| C4 | `waypoint_id` 由路径规范化 + **三条手工映射表**；命名空间**不进** id；键前缀 `teleportwaypoint.waypoint.` | `waypoint_id = <namespace>.<path>`，**无映射表**；键前缀 `tpwp.` | §5.6、§6.1 |
| C5 | 结构筛选走配置 `structureFilter` | 走**数据包标签**（D6），配置只留 `mode` | §5.2、§6.3 |
| C6 | 路线甲（自定义 `StructureProcessor`）作为「可选精修层」随本版本落地 | **本版本不做**。旧数据包按 D5 保留但默认关闭，路线甲整体推迟 | §3.3、§9.3 |
| C7 | 调试命令 `/teleportwaypoint debug structures` 列为推荐项 | **v0.4.0 不做**，列为可选后续（见 §9 O1） | §10.4 |
| C8 | 日志开关叫 `debugLogging`、逐次放置记 INFO | 开关叫 `debugMode`（D7）；逐次放置记 **DEBUG**，默认关闭 | §10.3 |
| C9 | 幂等性断言「同区块跑两次，恰好一个方块 + 一个标记」 | 改断言为「**第二次不再调用处理器**」（`isNewChunk == false` 即跳过）；见 T16 `handler_isNewChunkGuard` | §10.5 第 3 条 |
| **C10** | 黑名单写裸 `"minecraft:mineshaft"`，并称其为「标签」 | 实测**不成立**：`mineshaft` 同时是结构 ID 与标签名，裸 ID 只匹配单个结构 ⇒ 展开 **21 而非 22**，`mineshaft_mesa` 落进名单缝隙。**T7 改用 `"#minecraft:mineshaft"`**；已在区块补锚设计文档 §5.2 就地标注「实现期更正」 | §5.2 |

> 上游非冲突部分仍然有效并被本计划引用：§5.3 死锁纪律、§2.5 净空 1.15 格、§9.1 必须保留清单、§10.1 构建命令。这些是**引擎事实**，不随路线变化。

### 0.4 唯一权威来源

- NeoForge 1.21.1 反编译源码：`D:\Minecraft\BeLoong-Core\build\nf-src`
- 原版结构清单与标签：`C:\Users\D_Ink\.gradle\caches\ng_execute\b6182136...\client-extra.jar` 内 `data/minecraft/worldgen/structure/*.json`（34 个）与 `data/minecraft/tags/worldgen/structure/*.json`（13 个）

### 0.5 本轮新增的已核实引擎事实（补充证据，实现时按这些写）

下列事实由本轮直接读反编译源码确认，纠正/加强了设计文档的表述。**写代码时以本节为准。**

| # | 事实 | 出处 |
|---|---|---|
| E1 | `ChunkEvent.Load` 的 post 点被 **`currentlyLoading` 的 try/finally 包住**（signal 行本身在 try 块内），因此事件期间 `chunkHolder.currentlyLoading == 本区块` | `ChunkStatusTasks.java:211-218` |
| E2 | `ServerChunkCache.getChunk` 的第一道短路是 `chunkholder.currentlyLoading != null → return`，**位于 `managedBlock` 之前** | `ServerChunkCache.java:153-159` |
| E3 | 由 E1+E2：事件期间对本区块调 `level.setBlock(...)`（内部 → `getChunkAt` → `getChunk`）**会短路返回，不阻塞**。所以「禁止跨区块访问」是**只针对其他区块**的纪律，本区块放置是安全的 | E1+E2 |
| **E3a** | **⚠ E3 只覆盖 `setBlock` 的第一次区块查找，不足以保证放置安全。** `UPDATE_ALL` 含 `UPDATE_NEIGHBORS` ⇒ `CollectingNeighborUpdater.MultiNeighborUpdate.runNext:123` 会同步 `level.getBlockState(邻居)`；边界列的邻居落在相邻区块，而相邻区块在 FULL 阶段**只保证到 `INITIALIZE_LIGHT`**（`ChunkPyramid` 的 `FULL` 步骤自身无 requirement，继承 `LIGHT` 的 `addRequirement(INITIALIZE_LIGHT, 1)`）⇒ 走到 `ServerChunkCache.getChunk:159` 的 `managedBlock`，等待主线程自己的邮箱 ⇒ **自死锁**。**结论：处理器内写方块必须用 `Block.UPDATE_CLIENTS`，不得用 `UPDATE_ALL`** | `Level.java:287`、`CollectingNeighborUpdater.java:122-123`、`Level.java:406-411`、`ServerChunkCache.java:153-159`、`ChunkPyramid.java:43-45` |
| E4 | 但**其他**区块若未加载，`managedBlock` 会等主线程自己的邮箱 ⇒ 死锁。**跨区块访问仍然是硬禁止** | `ServerChunkCache.java:158-160` |
| E5 | `LevelChunk.setBlockState` 在成功路径上**已经置 `unsaved = true`**（`:304`） | `LevelChunk.java:304` |
| E6 | `Level.blockEntityChanged` 的实现是 `if (hasChunkAt(pos)) getChunkAt(pos).setUnsaved(true)` —— 与 E5 等效 | `Level.java:984-988` |
| E7 | **由 E5+E6：放置器不因 `setChanged()` 而引入任何额外持久化行为**（区块本来就要存）。禁用清单里「绝不触发 `setChanged()`」的原始理由是 `WorldGenRegion`（FEATURES 阶段），**本方案不在该线程/该阶段** | 推论 |
| E8 | `isNewChunk` 的真值直接来自 `!(protochunk instanceof ImposterProtoChunk)`（`:215`）；磁盘读出的区块是 `ImposterProtoChunk` ⇒ `false` | `ChunkStatusTasks.java:196-215` |
| E9 | GET 的 `hasBlockEntity()` 必须先判：`LevelChunk.getBlockEntity(pos)` 会查 `pendingBlockEntities` 并 `promotePendingBlockEntity` ⇒ **惰性反序列化 NBT** | `LevelChunk.java:329-343` |
| E10 | `addAndRegisterBlockEntity` 只是把 BE 塞进 `level.addFreshBlockEntities(...)`；`onLoad()` 的**唯一调用点**是下一 tick 的 `Level.tickBlockEntities` | `LevelChunk.java:357-367`、`Level.java:576-585` |
| E11 | 由 E10：**放置器不需要手动调 `WaypointManager.register()`**，既有 `onLoad()` 链路会自动接手（这正符合设计承诺的「登记/同步/渲染完全复用」） | 推论 |
| E12 | `isOutsideBuildHeight(y)` 是 `y < minBuildHeight \|\| y >= maxBuildHeight` ⇒ **上界是排他的**。合法 y 区间是 `[minBuildHeight+1, maxBuildHeight-1]`，与设计文档一致 | `LevelHeightAccessor.java:31-33` |
| E13 | `ChunkAccess.getAllStarts()` 的键是 `Structure`（不是 `Holder`）⇒ 必须先 `registry.wrapAsHolder(structure)` 才能 `holder.is(TagKey)` | `ChunkAccess.java:79,214`、`Registry.java:151`、`Holder.java:25` |
| E14 | `BoundingBox` 确有 `intersects(int minX, int minZ, int maxX, int maxZ)` 四参重载（与 `ChunkGenerator.createReferences` 同谓词），且 `getCenter()` 返回 `BlockPos` | `BoundingBox.java:154,292` |
| E15 | `ChunkEvent.Load` 是 `LevelEvent`，在 `NeoForge.EVENT_BUS` 上；`isNewChunk()` 的 javadoc 明写「只会在逻辑服务端返回 true」 | `ChunkEvent.java:23,55-73` |
| **E16** | **`StructureStart` 没有 `getPos()`。** 「唯一确定性锚点」的**唯一**正确取法是 `start.getPieces().get(0).getBoundingBox().getCenter()` —— 这正是 `placeInChunk` 内部算 `pos` 参数的方式 | `StructureStart.java:86-88`、`:146-148` |
| **E17** | **`StructureStart.getBoundingBox()` 不是 piece 的并集**：它是 `structure.adjustBoundingBox(pieces.calculateBoundingBox())`，而 `adjustBoundingBox` 在 `terrainAdaptation != NONE` 时会 **`inflatedBy(12)`** | `StructureStart.java:71-79`、`Structure.java:77-79` |
| **E18** | 由 E17：**绝不能用 `start.getBoundingBox()` 推导锚点列**（偏移 12 格且被记忆化），也**不要**用它当 L1 的搜索包围盒（会多搜 12 格）。锚点列一律走 E16 的取法 | 推论 |
| E19 | `Registry.getTag(TagKey)` 返回 **`Optional<HolderSet.Named<T>>`**；另有 `getTagOrEmpty` / `getTagNames` / `getTags`。**「缺失的标签」根本不进 `getTags()` 流** ⇒ `Optional.empty()` 就是「缺失」，`Optional.of(空 HolderSet)` 就是「存在但为空」 | `Registry.java:159-173` |
| E20 | `ResourceLocation.fromNamespaceAndPath(String, String)` 在 1.21.1 存在（构造器已私有），`getNamespace()` / `getPath()` 可读 | `ResourceLocation.java:61,135,139` |
| **E21** | **GameTest 必须有模板结构文件。** `@GameTest.template()` 默认 `""` ⇒ 结构名退化为测试类名的小写形式（`PrefixGameTestTemplate` 可改）；`StructureUtils.prepareTestStructure` 会 `getOrCreate(ResourceLocation.parse(gameTestInfo.getStructureName()))` ⇒ **模板缺失时该测试拿不到结构**。模板从 `data/<ns>/structure/<name>.nbt` 取 | `GameTest.java:23-28`、`GameTestInfo.java:292`、`StructureUtils.java:105-107`、`GameTestHolder.java:22-25` |
| **E22** | `@GameTestHolder` 的 javadoc 明写它会**自动注册**类中所有 `@GameTest` / `@GameTestGenerator` 方法 ⇒ **不需要**手写 `RegisterGameTestsEvent` 注册代码 | `GameTestHolder.java:15-21` |
| **E23** | `@GameTest` 方法的形状：被 `turnMethodIntoConsumer` 包成 `Consumer<GameTestHelper>` ⇒ 方法签名是 `public void name(GameTestHelper helper)` | `GameTestRegistry.java:159` |

---

## 1. 架构

### 1.1 组件图

```
NeoForge.EVENT_BUS
      │  ChunkEvent.Load(chunk, isNewChunk)
      ▼
┌─────────────────────────────────────────────────────────────┐
│ StructureWaypointHandler            （新增，无状态）         │
│  门1 ServerLevel  门2 LevelChunk  门3 isNewChunk            │
│  门4 starts/references 双空       门5 重入守卫               │
│  门6 每区块结构数上限                                        │
│  try/catch (Throwable) 包住整个处理体                        │
└────────┬──────────────────────────┬─────────────────────────┘
         │                          │
         ▼                          ▼
┌──────────────────────┐   ┌──────────────────────────────────┐
│ StructureTagFilter   │   │ StructureWaypointScanner          │
│  （新增，无状态）     │   │  （新增，无状态，纯几何）          │
│  标签存在性+空判定    │   │  参数类型只有 LevelChunk           │
│  缺失 WARN 一次      │   │  层→遍→piece→列→y 遍历             │
│  空 不记日志         │   │  返回 Optional<BlockPos>           │
└──────────────────────┘   └──────────────┬───────────────────┘
                                          │
                                          ▼
                             ┌──────────────────────────────────┐
                             │ StructureWaypointPlacer           │
                             │  （新增，无状态）                  │
                             │  显式 WATERLOGGED、写 waypoint_id  │
                             └──────────────┬───────────────────┘
                                            │  setBlock(...)
                                            ▼
                        既有链路（0 改动）：LevelChunk.setBlockState
                            → addFreshBlockEntities
                            → 下一 tick Level.tickBlockEntities
                            → BlockEntity.onLoad()
                            → WaypointManager.register()
                            → WaypointRegistryData + 网络广播
                            → 客户端渲染 / Xaero
```

### 1.2 新增包与文件

包 `com.zonlong.teleportwaypoint.structure`（新增）：

| 文件 | 职责 | 状态 |
|---|---|---|
| `StructureWaypointHandler.java` | `ChunkEvent.Load` 监听器：早退门、重入守卫、预算配置、`try/catch (Throwable)`、编排 | 新增 |
| `StructureWaypointScanner.java` | 纯几何选点：层→遍→piece→列→y | 新增 |
| `StructureWaypointPlacer.java` | 单次放置：`WATERLOGGED` 显式设置、写 `waypoint_id` | 新增 |
| `StructureTagFilter.java` | 黑白名单标签判定 + 缺失/为空语义 + WARN 一次 | 新增 |
| `StructureWaypointNaming.java` | `waypoint_id` 派生（含 64 字符防截断） | 新增 |
| `StructureWaypointDebug.java` | `debugMode` 门控的英文 debug 日志 | 新增 |

`WaypointBlockEntity` 的**静态** `isValidId` 被 handler 与 naming 共用；为避免 `structure/` 包反向依赖 `block/entity/`，本计划把「id 合法性」的唯一真源**留在 `WaypointBlockEntity.isValidId`**，由 `Naming` 调用它做自检（方向是 `structure → block.entity`，与既有 `core → block.entity` 一致，无环）。

### 1.3 客户端命名工具（跨包共用）

`Naming` 工具类放在 `com.zonlong.teleportwaypoint.util`（新包），因为它的 4 个调用点分布在 `client/`、`network/`、`block/entity/`、`client/xaero/`。放 `structure/` 会让客户端依赖服务端包，语义不清。

| 文件 | 内容 |
|---|---|
| `util/Naming.java`（新增） | `KEY_PREFIX = "tpwp."`、`key(String id)`、`emptyKey()`、`displayName(String id)`、`deriveId(ResourceLocation)`、`humanize(String id)`、`MAX_ID_LENGTH = 64` |

---

## 2. 实现约束（评审必查项）

> 本节是设计文档 §10.4「两条强制项」的工程化展开。**每一条都必须在代码评审时逐条核对。**

### 2.1 无状态

| 编号 | 规则 |
|---|---|
| S1 | `StructureWaypointHandler` / `Scanner` / `Placer` / `StructureTagFilter` / `Naming` **不得有非 `static final` 的实例字段，不得有可变的 `static` 字段**。全部中间数据走局部变量与方法参数 |
| S2 | 唯一的 `static` 可变状态是两处**日志去重布尔**：`StructureTagFilter` 的「已 WARN 过缺失」与 handler 的「重入已 WARN 过」。二者都是 `static volatile boolean`，**只允许单向从 `false` 置 `true`**，不参与任何逻辑判定 |
| S3 | 探测预算计数器**必须是局部变量**（`int[] budget = {2000}` 或循环内 `int`），不得做成字段——否则区块之间会互相消耗预算 |

### 2.2 禁止清单（`ChunkEvent.Load` 处理器内，作用于**非事件区块**）

| 编号 | 禁止项 | 理由 |
|---|---|---|
| F1 | `level.getBlockState(BlockPos)` / `level.getFluidState(BlockPos)` | 内部 `getChunkAt` → `getChunk` → **E4 死锁** |
| F2 | `level.getChunkAt(...)` / `level.getChunk(x,z)` / `getChunkSource().getChunk(...)` | 同上 |
| F3 | **全部** `StructureManager` 查询：`startsForStructure`、`getStructureAt`、`getStructureWithPieceAt`、`getAllStructuresAt`、`fillStartsForStructure` | 全部经 `level.getChunk(..., STRUCTURE_REFERENCES)` 且 `requireChunk=true` ⇒ 强制同步加载 + 永久 `TicketType.UNKNOWN` 票 |
| F4 | 写 `StructureStart` / `StructurePiece` / 区块的结构映射 | 会置 `unsaved` 并使区块与其持久化结构数据不同步 |
| F5 | 遍历 piece 包围盒范围内的**其他**区块 | 落点必须在本区块内（约束 S4） |
| **F6** | **`level.setBlock(pos, state, Block.UPDATE_ALL)`（或任何含 `UPDATE_NEIGHBORS` 的标志）** | **间接**跨区块访问：`UPDATE_NEIGHBORS` → `blockUpdated` → `CollectingNeighborUpdater.MultiNeighborUpdate.runNext:123` 同步 `level.getBlockState(邻居)`，边界列的邻居在相邻区块，而相邻区块在 FULL 阶段只保证到 `INITIALIZE_LIGHT` ⇒ `managedBlock` **自死锁**。用 `Block.UPDATE_CLIENTS`（见 E3a） |

**允许且必须使用：**

| 编号 | 允许项 | 理由 |
|---|---|---|
| A1 | `LevelChunk.getBlockState(pos)`（事件区块自身） | 纯数组读，无区块查找 |
| A2 | `level.setBlock(pos, state, Block.UPDATE_CLIENTS)`，`pos` 在事件区块内。**必须去掉 `UPDATE_NEIGHBORS`** —— `UPDATE_ALL` 会经邻居更新级联去读相邻区块并自死锁（E3a） | E1+E2+E3a |
| A3 | `chunk.getBlockEntity(pos)`，`pos` 是本帧刚放置的位置 | E10：此时 `pendingBlockEntities` 里绝无该 pos 的条目（该 pos 放置前已确认非 BE） |

### 2.3 其余强制纪律

| 编号 | 规则 |
|---|---|
| S4 | 落点 `p` 必须满足 `p.getX() >> 4 == chunk.getPos().x && p.getZ() >> 4 == chunk.getPos().z`。**扫描器出口处加断言式校验**（`assert` 不启用时用 `if` 早退 + WARN），越界即放弃 |
| S5 | **整个处理体包 `try/catch (Throwable)`**。异常逃逸 ⇒ 区块 future 异常完成 ⇒ `ServerChunkCache.getChunk` 的 `join()` 抛 `IllegalStateException` ⇒ 服务器崩溃 |
| S6 | 判定 BE 时**先 `state.hasBlockEntity()`**，为 `true` 才 `getBlockEntity`（E9） |
| S7 | 数据包标签：**缺失 → WARN 一次**；**为空 → 合法配置，不记任何日志** |
| S8 | **所有** debug 日志走 `CommonConfig.DEBUG_MODE`，且**文本一律英文** |
| S9 | 处理器内不得调用 `WaypointManager.register()`。登记由既有 `onLoad()` 链路负责（E11） |

---

## 3. 待实测数值：初值与实测项

> 设计文档 §13 的三个「待实测决定」数值。本节给出**可直接编码的初值**与**实测方法**。三者都是 `static final` 常量（不暴露为配置项），实测后可改一处。

| # | 常量 | 初值 | 位置 | 超限行为 |
|---|---|---|---|---|
| P1 | `MAX_STRUCTURES_PER_CHUNK` | **2** | `StructureWaypointHandler` | 第 3 个及以后的结构直接跳过，记 DEBUG（英文） |
| P2 | `PROBE_BUDGET` | **2000** | `StructureWaypointScanner` | 放弃该结构，记 DEBUG（英文），**不**回退到 L2 |
| P3 | `L2_SCAN_LIMIT` | **64**（设计已定，非待定） | `StructureWaypointScanner` | 该列扫描停止，换下一列 |
| P4 | `ROOF_SCAN_DEPTH` | **8**（设计已定） | `StructureWaypointScanner` | — |

### 3.1 P3 的正式定义：L2 排序键（本计划新定义，设计文档未定）

L2 无 piece，故 5 元键的前两元退化。**L2 排序键定为 4 元**：

| 优先级 | 键 | 目的 |
|---|---|---|
| 1 | `chebyshev(列, 区块中心列)` 升序 | 兜底锚点尽量落在区块中部，远离未加载的邻区块边缘 |
| 2 | `chebyshev(列, 锚点列)` 升序 | 保留 D9 的「贴近结构锚点列」权重 |
| 3 | `x` 升序 | 打破并列 |
| 4 | `z` 升序 | 打破并列 |

- 区块中心列 = `(chunkMinX + 8, chunkMinZ + 8)`。
- **不做第二遍 piece 循环**（L2 无 piece），但仍做第 1 遍（要求 `roofed`）与第 2 遍（不要求）。
- 全序成立 ⇒ L2 落点同样确定。

### 3.2 实测项

| # | 实测项 | 方法 | 判定 |
|---|---|---|---|
| M1 | 新区块生成速率 | 真实世界 + spark profiler，正常探索与高速飞行（鞘翅 + 烟花）各 2 分钟 | 记录「区块/秒」峰值；若 >200/秒，复核 P1 |
| M2 | 单次放置耗时（含光照传播） | spark 中定位 `StructureWaypointPlacer` / `LevelChunk.setBlockState` 的调用；或临时在 debug 中打 `System.nanoTime` 差值 | 目标 < 1 ms/次；若 > 5 ms，把 P2 下调到 500 并复核光照代价 |
| M3 | 每区块结构数实际分布 | `debugMode=true` 跑 10 分钟新地形，统计超过 2 个结构的区块比例 | 若 >0.1%，把 P1 上调到 4 |
| M4 | 探测预算实际消耗 | 同上，统计超预算 debug 行出现频率 | 若 >0.5%，把 P2 上调到 4000 |

---

## 4. 任务清单

**顺序原则**：先做无依赖的纯函数（T1–T3），再做配置与数据（T4–T7），最后做运行时组件（T8–T16）；T17 之后是验证与文档。每个任务 2–5 分钟可完成并单独编译通过。

---

### T1 — 放宽 `isValidId` 以允许 `.`

**文件：**
- 修改：`src/main/java/com/zonlong/teleportwaypoint/block/entity/WaypointBlockEntity.java`

**变更：**
- `:36` `private static final String ID_PATTERN = "[a-z0-9_]+";` → `"[a-z0-9_.]+"`
- 在 `:118` 的 `isValidId` 上加一条显式约束：**`.` 不得出现在首尾、不得连续出现**（防止 `..`、`.foo`、`foo.` 这类脏值）。实现建议：正则改为 `"[a-z0-9_]+(?:\\.[a-z0-9_]+)*"`，一并覆盖上述两条。
- `MAX_TEXT_LENGTH`（`:30`）保持 64 不变，`isValidId` 继续做长度检查。

**理由：** `waypoint_id` 现在是 `<namespace>.<path>`（D3），旧模式会静默拒绝全部新 id，`setId` 直接返回 ⇒ 锚点名退化为 `empty`。这是**整个方案的第一个隐性失败点**。

**验证：**
```bat
cd /d D:\Minecraft\Teleport-Waypoint-ds_flash
gradlew.bat build --offline --console=plain
```
编译通过。人工核对：`"minecraft.end_city"` → `true`；`"end_city"` → `true`（旧存档兼容）；`"a..b"`、`".a"`、`"a."` → `false`。

**风险：** 旧存档里若存在非法 id，`loadAdditional` 会把它降级为 `"empty"`（既有行为，不变）。

---

### T2 — 新增 `util/Naming.java`

**文件：**
- 新增：`src/main/java/com/zonlong/teleportwaypoint/util/Naming.java`

**内容：**

| 成员 | 签名 | 行为 |
|---|---|---|
| `KEY_PREFIX` | `public static final String` | `"tpwp."` |
| `EMPTY_ID` | `public static final String` | `"empty"` |
| `MAX_ID_LENGTH` | `public static final int` | `64`（与 `WaypointBlockEntity.MAX_TEXT_LENGTH` 一致，加单测断言二者相等） |
| `key(String id)` | `public static String` | `id` 为 null/空白 → `KEY_PREFIX + EMPTY_ID`；否则 `KEY_PREFIX + id` |
| `emptyKey()` | `public static String` | `KEY_PREFIX + EMPTY_ID` |
| `displayName(String id)` | `public static Component` | `Component.translatable(key(id))` |
| `deriveId(ResourceLocation structureId)` | `public static String` | 见下 |
| `humanize(String id)` | `public static String` | 把 `.` 与 `_` **都当分隔符**，逐段首字母大写，其余小写；`minecraft.end_city` → `"Minecraft End City"` |

**`deriveId` 的规则（防截断落在这一层，D3 + 设计文档 §7.2.1）：**

```
deriveId(id):
    s = id.getNamespace() + "." + id.getPath()
    若 s.length() <= MAX_ID_LENGTH 且 WaypointBlockEntity.isValidId(s)
        → return s
    否则 → 硬截断到 MAX_ID_LENGTH，再从尾部回退掉悬挂的 '.' 或 '_'
         → 若结果仍不合法（空/无字母）→ return EMPTY_ID
```

**必须写进代码注释的一句**：前缀 `tpwp.` 只影响翻译键，**不解决截断问题**；唯一有 64 字符硬上限的是 `waypoint_id`，因此防截断必须在 `deriveId` 里做。

**验证：** `gradlew.bat build --offline --console=plain` 通过。
人工核对映射：`minecraft:end_city` → `minecraft.end_city`；`minecraft:jungle_pyramid` → `minecraft.jungle_pyramid`（**不是** `jungle_temple`）；`minecraft:monument` → `minecraft.monument`（**不是** `ocean_monument`）；`minecraft:mansion` → `minecraft.mansion`（**不是** `woodland_mansion`）。

---

### T3 — 把 4 处硬编码前缀收敛到 `Naming`

**文件：**
- 修改：`src/main/java/com/zonlong/teleportwaypoint/client/ClientWaypointInfo.java`（`:25`）
- 修改：`src/main/java/com/zonlong/teleportwaypoint/network/ActivatedWaypointInfo.java`（`:23`）
- 修改：`src/main/java/com/zonlong/teleportwaypoint/block/entity/WaypointBlockEntity.java`（`:114`）
- 修改：`src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`（`:318`）

**逐处改法：**

| 文件:行 | 旧 | 新 |
|---|---|---|
| `ClientWaypointInfo.java:25` | `Component.translatable("teleportwaypoint.waypoint." + name)` | `Naming.displayName(name)` |
| `ActivatedWaypointInfo.java:23` | 同上 | `Naming.displayName(name)` |
| `WaypointBlockEntity.java:114` | `Component.translatable("teleportwaypoint.waypoint.empty")` / `...waypoint." + id` | `Naming.displayName(id)`（`id` 非法或空时由 `Naming.key` 自动回退 `tpwp.empty`） |
| `XaeroMinimapIntegration.java:318` | `"teleportwaypoint.waypoint." + info.name()` | `Naming.key(info.name())` |

**注意：**
- `XaeroMinimapIntegration` 传的是**翻译键字符串**（Xaero 自己本地化），所以用 `Naming.key`，**不是** `displayName`。
- `WaypointBlockEntity:114` 的旧判断 `(id.isEmpty() || !isValidId(id))` 可以从调用处删掉 —— `Naming.key` 内部已处理 null/空；但**保留** `!isValidId(id)` 的语义等价物（`Naming.key` 对非法 id 也要回退 `tpwp.empty`）。实现时在 `Naming.key` 里加 `!WaypointBlockEntity.isValidId(id)` 检查。
- **线格式零改动**：这些改动只碰「显示时拼前缀」，`waypoint_id` 的传输内容不变。

**验证：** `gradlew.bat build --offline --console=plain` 通过；`rg "teleportwaypoint\.waypoint\." src/main/java` 返回 **0 行**。

---

### T4 — CommonConfig：新增 `structureWaypoints` 分组 + 两个默认值改 `false`

**文件：**
- 修改：`src/main/java/com/zonlong/teleportwaypoint/config/CommonConfig.java`
- 新增：`src/main/java/com/zonlong/teleportwaypoint/config/StructureWaypointMode.java`

**变更：**

1. 新增字段：
```java
public static final ModConfigSpec.EnumValue<StructureWaypointMode> STRUCTURE_WAYPOINT_MODE;
public static final ModConfigSpec.BooleanValue DEBUG_MODE;
```
2. 新增独立枚举文件 `config/StructureWaypointMode.java`（**独立文件**，便于 `StructureTagFilter` 与测试直接 import，不必嵌套访问）：
```java
public enum StructureWaypointMode { WHITELIST, BLACKLIST }
```
3. 在 `:36` 的 `builder.pop()` **之后**新增分组（分组顺序：`teleportCooldownTicks` → `optionalDataPacks` → `structureWaypoints`，与设计文档 §5.1 的示例一致）：
```java
builder.translation("teleportwaypoint.configuration.common.structureWaypoints");
builder.push("structureWaypoints");

STRUCTURE_WAYPOINT_MODE = builder
        .comment("\"WHITELIST\" = only place in structures listed by teleportwaypoint:waypoint_whitelist",
                 "\"BLACKLIST\" = place in every structure EXCEPT those listed by teleportwaypoint:waypoint_blacklist")
        .translation("teleportwaypoint.configuration.common.structureWaypoints.mode")
        .defineEnum("mode", StructureWaypointMode.WHITELIST);

DEBUG_MODE = builder
        .comment("When true, this mod writes DEBUG-level log lines. All debug output is gated by this",
                 "option and is written in English only, so log files never contain mojibake.")
        .translation("teleportwaypoint.configuration.common.structureWaypoints.debugMode")
        .define("debugMode", false);

builder.pop();
```
4. `:29` `DEFAULT_ENABLE_STRUCTURE_WAYPOINTS` 的 `define(..., true)` → `false`。
5. `:34` `DEFAULT_ENABLE_YUNG_STRUCTURE_WAYPOINTS` 的 `define(..., true)` → `false`。

**理由（D5）：** 不改默认值 ⇒ 老存档里已启用的旧数据包与新机制并存 ⇒ 每个结构两个锚点。改默认值**不会自动关掉已启用的包**，所以 README 必须给迁移步骤（T20）。

**验证：** `gradlew.bat build --offline --console=plain` 通过；`gradlew.bat runServer --offline --console=plain` 启动后在 `run/config/teleportwaypoint/common.toml` 中肉眼确认 `mode = "WHITELIST"`、`debugMode = false`、两个 `defaultEnable* = false`。

---

### T5 — 语言文件：新增配置项翻译键

**文件：**
- 修改：`src/main/resources/assets/teleportwaypoint/lang/en_us.json`
- 修改：`src/main/resources/assets/teleportwaypoint/lang/zh_cn.json`

**新增键（两文件键集合必须完全一致）：**

```
teleportwaypoint.configuration.common.structureWaypoints
teleportwaypoint.configuration.common.structureWaypoints.tooltip
teleportwaypoint.configuration.common.structureWaypoints.mode
teleportwaypoint.configuration.common.structureWaypoints.mode.tooltip
teleportwaypoint.configuration.common.structureWaypoints.debugMode
teleportwaypoint.configuration.common.structureWaypoints.debugMode.tooltip
```

**内容约定：**
- `en_us.json` 全部英文；`zh_cn.json` 全部中文。
- `.mode.tooltip` 必须说明两套名单的语义与「名单用数据包标签、标签名固定」这一事实。
- `.debugMode.tooltip` 说明「仅影响日志、日志为英文」。

**验证：** `gradlew.bat build --offline --console=plain`；再用既有脚本 `D:\Minecraft\__pycache__\lang_audit.py` 做键集合双向比对（无脚本则手工比对 JSON 键排序后的行集合）。

---

### T6 — 语言文件：锚点显示名改写为 `tpwp.*` + 保留 14 个旧键

**文件：**
- 修改：`src/main/resources/assets/teleportwaypoint/lang/en_us.json`
- 修改：`src/main/resources/assets/teleportwaypoint/lang/zh_cn.json`

**新增 13 个 `tpwp.*` 键**（12 白名单结构 + `empty`）：

| 新键 | en_us | zh_cn | 备注 |
|---|---|---|---|
| `tpwp.empty` | Unnamed Waypoint | 未命名锚点 | 空名回退 |
| `tpwp.minecraft.ancient_city` | Ancient City | 远古城市 | |
| `tpwp.minecraft.bastion_remnant` | Bastion Remnant | 堡垒遗迹 | |
| `tpwp.minecraft.desert_pyramid` | Desert Pyramid | 沙漠神殿 | |
| `tpwp.minecraft.end_city` | End City | 末地城 | |
| `tpwp.minecraft.fortress` | Nether Fortress | 下界要塞 | **新中文名** |
| `tpwp.minecraft.igloo` | Igloo | 雪屋 | |
| `tpwp.minecraft.jungle_pyramid` | Jungle Temple | 丛林神庙 | **新中文名**（注册表 ID 是 `jungle_pyramid`，显示名沿用作者既有的「丛林神庙」） |
| `tpwp.minecraft.mansion` | Woodland Mansion | 林地府邸 | **新中文名** |
| `tpwp.minecraft.monument` | Ocean Monument | 海底神殿 | **新中文名** |
| `tpwp.minecraft.stronghold` | Stronghold | 要塞 | |
| `tpwp.minecraft.swamp_hut` | Swamp Hut | 沼泽小屋 | |
| `tpwp.minecraft.trial_chambers` | Trial Chambers | 试炼密室 | |

**保留 14 个旧键，一字不改，位置上移到紧邻区块**：

`teleportwaypoint.waypoint.empty`、`.village`、`.trial_chambers`、`.ancient_city`、`.bastion_remnant`、`.end_city`、`.nether_fortress`、`.woodland_mansion`、`.ocean_monument`、`.stronghold`、`.desert_pyramid`、`.igloo`、`.jungle_temple`、`.swamp_hut`。

**不要删 `.village`**（D5/Q9 说可删可留 —— **选择保留**，删它没有任何收益，只增加一次风险）。

**不要新增 5 个村庄的 `tpwp.minecraft.village_*` 键**：村庄在黑名单里，不会被自动放置。

**验证：**
- `gradlew.bat build --offline --console=plain`
- JSON 合法性：`Get-Content ...\en_us.json | ConvertFrom-Json` 不报错
- **两文件的旧 14 键必须都还在**：`rg -c "teleportwaypoint\.waypoint\." src/main/resources/assets/teleportwaypoint/lang/` 两文件都是 **14**
- 新增计数：`rg -c '"tpwp\.' src/main/resources/assets/teleportwaypoint/lang/` 两文件都是 **13**

> 一个逗号错误会让**整个**语言文件被丢弃（`ClientLanguage`），一次拼写毁掉全部名字 —— 这一步必须查 JSON 合法性。

---

### T7 — 两个内置标签 JSON

**文件：**
- 新增：`src/main/resources/data/teleportwaypoint/tags/worldgen/structure/waypoint_whitelist.json`
- 新增：`src/main/resources/data/teleportwaypoint/tags/worldgen/structure/waypoint_blacklist.json`

> **位置纪律：** 直接放 `data/teleportwaypoint/tags/...`，**不要**放进 `data/teleportwaypoint/datapacks/`。标签必须常驻，不随可选数据包开关。

**`waypoint_whitelist.json` —— 12 条：**

```json
{
  "replace": false,
  "values": [
    "minecraft:ancient_city",
    "minecraft:bastion_remnant",
    "minecraft:desert_pyramid",
    "minecraft:end_city",
    "minecraft:fortress",
    "minecraft:igloo",
    "minecraft:jungle_pyramid",
    "minecraft:mansion",
    "minecraft:monument",
    "minecraft:stronghold",
    "minecraft:swamp_hut",
    "minecraft:trial_chambers"
  ]
}
```

**`waypoint_blacklist.json` —— 9 条，展开 22 个结构：**

```json
{
  "replace": false,
  "values": [
    "minecraft:buried_treasure",
    "#minecraft:mineshaft",
    "minecraft:nether_fossil",
    "minecraft:pillager_outpost",
    "minecraft:trail_ruins",
    "#minecraft:village",
    "#minecraft:ruined_portal",
    "#minecraft:shipwreck",
    "#minecraft:ocean_ruin"
  ]
}
```

**两条硬要求：**
1. **两文件都必须 `"replace": false`**（D6/J10）。`true` 会整体替换掉整合包与其他模组的贡献，**禁止使用**。
2. **`#minecraft:mineshaft` 必须带 `#`（C10，实测更正）。** `mineshaft` 在原版同时是结构 ID 与标签名；写裸 `"minecraft:mineshaft"` 只匹配 `mineshaft` 本身，丢掉 `mineshaft_mesa`，展开数变成 **21 而非 22**，34 个原版结构漏掉 1 个。带上 `#` 才展开为 2 条。

**为什么写裸 ID 而不写 `{"id": "...", "required": false}`：** 这 12 个原版结构必然存在，不需要 optional 包装。`required: false` 的用法留给**整合包作者**在自己的数据包里追加模组结构（README 里给示例）。

**验证（最廉价也最强的一道）：**
```bat
cd /d D:\Minecraft\Teleport-Waypoint-ds_flash
gradlew.bat runServer --offline --console=plain
```
数据包注册表（含 `WORLDGEN_REGISTRIES` 的结构标签）在世界创建前加载，畸形 JSON 或非法注册表 ID 会以 DFU 错误**点名文件**并中止启动。启动到 "Done" 且无 `teleportwaypoint` 相关报错即通过。

**交叉核对（可脚本化）：** 从 `client-extra.jar` 解出 `data/minecraft/worldgen/structure/*.json`（34 个）取文件名集合，断言：
- 白名单 12 条全部 ∈ 该集合
- 黑名单的 5 个裸 ID 全部 ∈ 该集合
- 4 个 `#minecraft:*` 引用全部 ∈ 该 jar 的 `data/minecraft/tags/worldgen/structure/*.json`（13 个）文件名集合
- 白名单 12 ∪ 黑名单展开 22 = 34（无重复、无遗漏）

---

### T8 — `StructureWaypointNaming`：结构 ID → `waypoint_id`

**文件：**
- 新增：`src/main/java/com/zonlong/teleportwaypoint/structure/StructureWaypointNaming.java`

**内容：** 薄封装，把「从 `StructureStart` 拿注册表 ID」这一步隔离出来，让 handler 保持线性。

```java
/** @return the derived waypoint id, or empty if the structure is not in the registry. */
public static Optional<String> derive(Registry<Structure> structures, StructureStart start)
```
- 内部：`ResourceLocation id = structures.getKey(start.getStructure()); if (id == null) return Optional.empty(); return Optional.of(Naming.deriveId(id));`
- **不查标签**（那是 T14 的职责），**不做日志**（那是 T9 的职责）。

**验证：** `gradlew.bat build --offline --console=plain` 通过。

---

### T9 — `StructureWaypointDebug`：`debugMode` 门控的英文日志

**文件：**
- 新增：`src/main/java/com/zonlong/teleportwaypoint/structure/StructureWaypointDebug.java`

**内容：**

| 方法 | 级别 | 文本（**英文，写死**） |
|---|---|---|
| `debug(String fmt, Object... args)` | DEBUG | 仅当 `CommonConfig.DEBUG_MODE.get()` 为 true 时输出 |
| `noPlacement(ResourceLocation sid, ChunkPos pos)` | DEBUG | `"structure waypoint: no valid placement structure=%s chunk=%s"` |
| `budgetExhausted(ResourceLocation sid, ChunkPos pos, int calls)` | DEBUG | `"structure waypoint: probe budget exhausted structure=%s chunk=%s calls=%d"` |
| `placed(ResourceLocation sid, BlockPos pos, boolean waterlogged)` | DEBUG | `"structure waypoint: placed structure=%s pos=%s waterlogged=%s"` |
| `skippedByTag(ResourceLocation sid, boolean whitelistMode)` | DEBUG | `"structure waypoint: skipped by tag structure=%s mode=%s"` |
| `limitReached(ChunkPos pos, int limit)` | DEBUG | `"structure waypoint: per-chunk structure limit reached chunk=%s limit=%d"` |
| `reentered(ChunkPos pos)` | WARN | `"structure waypoint: re-entrant chunk load detected, skipping chunk=%s"`（**WARN 不受 debugMode 门控**，因为它是安全网信号） |
| `failed(ChunkPos pos, Throwable t)` | WARN | `"structure waypoint: handler failed, chunk left untouched chunk=%s"` + 异常 |

**纪律：**
- 全部文本英文。**面向玩家的文本**（语言文件）不受此约束，仍双语。
- `LOGGER` 复用 `TeleportWaypoint.LOGGER`，或本类自建 `LogUtils.getLogger()`。
- **不使用** `String.format` 拼中文；参数只放标识符与数字（`ResourceLocation`、`BlockPos`、`ChunkPos`、`boolean`、`int`）。

**验证：** `gradlew.bat build --offline --console=plain`；`rg -n "LOGGER\.(debug|info|warn|error)" src/main/java/com/zonlong/teleportwaypoint/` 确认除本类与既有非 debug 日志外无新增的无门控日志。

---

### T10 — `StructureWaypointScanner`（一）：类型、常量、排序原语

**文件：**
- 新增：`src/main/java/com/zonlong/teleportwaypoint/structure/StructureWaypointScanner.java`

**签名（参数类型刻意收窄，让 F1–F3 的 API 不在作用域内 —— 这是结构性防线，不是注释）：**

```java
public final class StructureWaypointScanner {
    public static final int ROOF_SCAN_DEPTH = 8;
    public static final int L2_SCAN_LIMIT = 64;
    public static final int PROBE_BUDGET = 2000;

    /** @return the chosen position, or empty when every candidate fails or the budget runs out. */
    public static Optional<BlockPos> findPlacement(LevelChunk chunk, StructureStart start);
}
```

**私有辅助（全部 `private static`，无字段）：**

| 方法 | 说明 |
|---|---|
| `record Probe(LevelChunk chunk, int[] calls)` | 预算计数器用 `int[]` 传引用，保证是局部状态（S3） |
| `boolean isValidPlacement(LevelChunk chunk, BlockPos p)` | 见 T11 |
| `boolean roofed(LevelChunk chunk, BlockPos p, int n)` | 见 T11 |
| `List<StructurePiece> orderPieces(List<StructurePiece> pieces)` | 体积降序；体积相同按 `minX`、`minZ` 升序（D9 + §6.6.4） |
| `List<Column> orderColumns(...)` | 见下 |
| `record Column(int x, int z)` | 列载体 |

**`orderPieces` 的体积定义：** `long vol = (long) bbox.getXSpan() * bbox.getYSpan() * bbox.getZSpan();`（用 `long` 防溢出）。
**退化包围盒**（`maxX < minX || maxY < minY || maxZ < minZ`，旋转/镜像可能产生）**直接丢弃**，不参与排序 —— 对应设计文档 §6.5 待定项 2。

**`orderColumns`（L1，5 元键）：**

| 优先级 | 键 |
|---|---|
| 1 | `chebyshev(列, piece 包围盒中心 XZ)` 升序（`BoundingBox.getCenter()`，E14） |
| 2 | `chebyshev(列, 锚点列)` 升序 |
| 3 | `x` 升序 |
| 4 | `z` 升序 |

**锚点列的唯一正确取法（E16/E17/E18 —— 这是本计划纠正设计文档的一处）：**

```java
// 正确：与 StructureStart.placeInChunk 内部算法完全一致（StructureStart.java:86-88）
BlockPos anchor = start.getPieces().get(0).getBoundingBox().getCenter();
```

- **不要写 `start.getPos()`** —— 该方法在 1.21.1 **不存在**（`StructureStart` 的公开方法只有 `getBoundingBox`、`getChunkPos`、`getPieces`、`getStructure`、`isValid`、`getReferences`、`canBeReferenced`）。
- **不要写 `start.getBoundingBox().getCenter()`** —— `getBoundingBox()` 走 `structure.adjustBoundingBox(...)`，`terrainAdaptation != NONE` 时会 **`inflatedBy(12)`**（`Structure.java:78`）。这会让锚点列偏 12 格，且该值被 `cachedBoundingBox` 记忆化、难以在测试里发现。
- 同理：L1 的 piece 过滤与逐 piece 搜索都只围绕**单个 piece 的 `getBoundingBox()`** 展开，**从不**触碰 `start.getBoundingBox()`。

> `getPieces()` 的顺序即 `PiecesContainer` 的插入顺序，由生成过程决定，跨次读盘稳定；因此 `get(0)` 满足确定性要求。

**`orderColumns`（L2，4 元键）：** 见 §3.1。

> **`pieceIndex` 为什么不在 `orderColumns` 里：** 设计文档 §6.6.4 的 5 元键第 1 元是 `pieceIndex`，但 D9 已把 piece 选取改为「逐个试、成功即停」，piece 因而成了 `orderColumns` 的**外层循环**。列排序只需后三键 + `x`/`z`，`pieceIndex` 由循环位置隐式体现。**这是「层→遍→piece→列→y」严格顺序的直接结果，不是漏实现。**

**`chebyshev(a, b)`** = `Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z))`。

**验证：** 编译通过；本任务只加结构，`findPlacement` 暂返回 `Optional.empty()`（后续任务填充），因此编译可过、行为中性。

---

### T11 — `StructureWaypointScanner`（二）：合格判定、屋顶判据、遍历主体

**文件：**
- 修改：`src/main/java/com/zonlong/teleportwaypoint/structure/StructureWaypointScanner.java`

**`isValidPlacement(chunk, p)` —— 逐项判定，顺序即短路顺序：**

| 序 | 判定 |
|---|---|
| 1 | `p.getY() > chunk.getMinBuildHeight() && p.getY() < chunk.getMaxBuildHeight()`（E12：上界排他） |
| 2 | `down = chunk.getBlockState(p.below())`；`!down.isAir()`、`down.getFluidState().isEmpty()`、`down.isFaceSturdy(chunk, p.below(), Direction.UP)` |
| 3 | `!down.hasBlockEntity()`（**先判 state**，S6/E9） |
| 4 | 对 `k ∈ {0,1,2}`：`b = chunk.getBlockState(p.above(k))`；`b.isAir() \|\| b.canBeReplaced()` |
| 5 | 对同一 `b`：`!b.hasBlockEntity()`（先判 state，S6） |
| 6 | 对同一 `b`：**液体只能是水或空** —— `FluidState f = b.getFluidState()`；`!f.isEmpty() && !f.is(Fluids.WATER)` ⇒ 淘汰（**岩浆一律排除**，D2） |

> `p.above(1)` / `p.above(2)` 可能越出可建造高度上界。第 1 条只检查 `p` 自己，所以 k 循环里要加 `p.getY() + k < chunk.getMaxBuildHeight()` 保护，否则 `getBlockState` 越界。**这是设计文档伪代码没有覆盖的一个边界，本计划补上。**

**`roofed(chunk, p, n)`：**

```
for k in 1..n:
    if p.y + k >= maxBuildHeight: return false
    b = chunk.getBlockState(p.above(k))
    if b.isAir() or !b.getFluidState().isEmpty(): continue
    return b.isFaceSturdy(chunk, p.above(k), Direction.DOWN)
return false
```

**遍历主体（严格层→遍→piece→列→y）：**

```
findPlacement(chunk, start):
    if !start.isValid(): return empty                      // §6.6.5
    List<StructurePiece> raw = start.getPieces()
    if raw.isEmpty(): return empty                         // 防御（isValid 已保证，但别依赖它）
    chunkMinX = chunk.getPos().getMinBlockX(); chunkMinZ = chunk.getPos().getMinBlockZ()
    chunkMaxX = chunkMinX + 15;               chunkMaxZ = chunkMinZ + 15
    anchor   = raw.get(0).getBoundingBox().getCenter()     // 结构锚点列：唯一的正确取法（E16）
                                                           // 仅排序权重（D9）；绝不用 start.getBoundingBox()（E17/E18）

    pieces = orderPieces(raw filtered by bbox.intersects(chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ))

    int[] calls = {0}                                       // S3：局部预算

    // ---- L1 结构内 ----
    for (boolean requireRoof : new boolean[]{true, false}) {
      for (StructurePiece piece : pieces) {                 // 体积降序，逐个试，成功即停（D9）
        BoundingBox bb = piece.getBoundingBox()             // 局部缓存，只取一次（设计 §9.3 成本点 2）
        cols = orderColumnsL1(intersect(bb, chunkColumn), bb.getCenter(), anchor)
        int yTop = min(bb.maxY(), chunk.getMaxBuildHeight() - 1)
        int yBot = max(bb.minY(), chunk.getMinBuildHeight() + 1)
        for (Column c : cols)
          for (int y = yTop; y >= yBot; y--) {              // 自上而下
            if (calls[0] >= PROBE_BUDGET) { debug(budgetExhausted); return empty }
            calls[0]++
            BlockPos p = new BlockPos(c.x(), y, c.z())
            if (isValidPlacement(chunk, p) && (!requireRoof || roofed(chunk, p, ROOF_SCAN_DEPTH)))
                return Optional.of(p)
          }
      }
    }

    // ---- L2 区块兜底 ----
    cols = orderColumnsL2(chunkColumn, chunkCenter, anchor)
    for (boolean requireRoof : new boolean[]{true, false}) {
      for (Column c : cols) {
        int scanned = 0
        for (int y = chunk.getMaxBuildHeight() - 1; y >= chunk.getMinBuildHeight() + 1; y--) {
          if (++scanned > L2_SCAN_LIMIT) break            // §6.4
          if (calls[0] >= PROBE_BUDGET) { debug(budgetExhausted); return empty }
          calls[0]++
          BlockPos p = new BlockPos(c.x(), y, c.z())
          if (isValidPlacement(chunk, p) && (!requireRoof || roofed(chunk, p, ROOF_SCAN_DEPTH)))
              return Optional.of(p)
        }
      }
    }
    return empty
```

**四条必须核对的设计一致性：**
1. 预算是**跨层累计**的（`calls` 在 L1 和 L2 之间不重置）—— 设计 §6.6.6 明确要求。
2. 预算耗尽**立即放弃整个结构**，**不回退 L2**（设计 §6.6.6「超限即放弃该结构」）。
3. L2 的 `64` 是**每列**的 y 步数上限（`scanned`），不是全局。
4. L1 的 y 范围由 piece 包围盒与本区块可建造高度求交自然界定，无额外上限。

**出口校验（S4）：**
```java
if (!(p.getX() >> 4 == chunk.getPos().x && p.getZ() >> 4 == chunk.getPos().z)) {
    StructureWaypointDebug.debug("structure waypoint: scanner produced out-of-chunk pos %s for chunk %s", p, chunk.getPos());
    return Optional.empty();
}
```
放在 `return Optional.of(p)` **之前**，L1 与 L2 两个出口都要。

**验证：** `gradlew.bat build --offline --console=plain`。

---

### T12 — `StructureWaypointPlacer`

**文件：**
- 新增：`src/main/java/com/zonlong/teleportwaypoint/structure/StructureWaypointPlacer.java`

**签名：**
```java
/** @return true when the block was placed and its waypoint id assigned. */
public static boolean place(ServerLevel level, LevelChunk chunk, BlockPos pos, String waypointId);
```

**步骤：**

1. **越界防线（S4）**：`pos` 必须落在 `chunk` 的 16×16 列内，否则 return false。**这不是重复校验** —— 放置器是唯一真正写世界的地方，值得在最后一米再拦一次。
2. 组装状态：
```java
BlockState state = ModBlocks.WAYPOINT.get().defaultBlockState();
FluidState fluid = chunk.getBlockState(pos).getFluidState();
if (fluid.getType() == Fluids.WATER) {
    state = state.setValue(WaypointBlock.WATERLOGGED, true);   // 必须显式写，默认是 false
}
```
   - **读 `chunk.getBlockState` 而不是 `level.getFluidState`**（F1）。
   - 岩浆不可能到这一步（T11 第 6 条已排除），但仍**不要**给 `WATERLOGGED` 一个「否则」分支去放岩浆 —— 只认 `WATER`，其余一律 `false`。
3. `level.setBlock(pos, state, Block.UPDATE_CLIENTS)`。返回 false ⇒ 放弃。**不要用 `UPDATE_ALL`**：`UPDATE_NEIGHBORS` 引发的邻居更新级联会 `level.getBlockState(相邻区块)`，而相邻区块在 FULL 阶段只保证到 `INITIALIZE_LIGHT`，会自死锁（E3a）。`UPDATE_CLIENTS` = 2，仍会向客户端同步方块与光照。
4. **写 `waypoint_id`**：
```java
if (chunk.getBlockEntity(pos) instanceof WaypointBlockEntity be) {
    be.setId(waypointId);          // 内部做 isValidId 校验，非法则保持 "empty"
} else {
    StructureWaypointDebug.debug("structure waypoint: no block entity after placement pos=%s", pos);
    return false;
}
```
   - `chunk.getBlockEntity(pos)` 此刻是安全的（A3）：该 pos 放置前已确认无 BE ⇒ `pendingBlockEntities` 里不可能有该 pos，不会触发惰性反序列化。
   - **不写 `uid`**（上游 D4）：让 `getUid()` 惰性生成唯一值。
   - **不调用 `WaypointManager.register()`**（S9/E11）：下一 tick 的 `onLoad()` 会接手。
5. 返回 true。

**关于 `setChanged()`：** `setId` 内部会调它，进而 `Level.blockEntityChanged` → `getChunkAt`（E3 安全）→ `setUnsaved(true)`（E5/E6 说明该区块本来就会置 `unsaved`）。**这是无害的**，不需要额外绕开。设计文档「绝不触发 `setChanged()`」的原始理由是 `WorldGenRegion` 在 FEATURES 阶段会抛异常，**本方案不在那个线程/阶段**（见 E5–E7）。本计划在此明确记录这一判断，以免实现者误以为需要反射或 NBT 预写。

**验证：** `gradlew.bat build --offline --console=plain`。

**待实测（M2）：** 这一步是单次工作里最重的一块（`setBlock` + 光照传播，锚点发光等级 14）。

---

### T13 — `StructureTagFilter`：黑白名单标签判定

**文件：**
- 新增：`src/main/java/com/zonlong/teleportwaypoint/structure/StructureTagFilter.java`

**标签 ID（硬编码，D6「标签名硬编码在代码里，不额外暴露为配置项」）：**
```java
private static final TagKey<Structure> WHITELIST = TagKey.create(Registries.STRUCTURE,
        ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "waypoint_whitelist"));
private static final TagKey<Structure> BLACKLIST = TagKey.create(Registries.STRUCTURE,
        ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "waypoint_blacklist"));
```

**唯一可变状态（S2）：**
```java
private static volatile boolean whitelistMissingWarned = false;
private static volatile boolean blacklistMissingWarned = false;
```
两个**分开**的去重布尔，因为两个标签的缺失是两件事。

**签名：**
```java
public static boolean allows(Registry<Structure> structures, Structure structure);
```

**判定逻辑：**

| 步骤 | 行为 |
|---|---|
| 1 | `Holder<Structure> holder = structures.wrapAsHolder(structure)`（E13） |
| 2 | 读 `mode = CommonConfig.STRUCTURE_WAYPOINT_MODE.get()` |
| 3 | 白名单模式：`holder.is(WHITELIST)` |
| 4 | 黑名单模式：`!holder.is(BLACKLIST)` |

**「缺失」与「为空」的区分（S7 —— 本任务最容易做错的一点）：**

`Holder.is(TagKey)` 对**缺失**与**为空**都返回 `false`，二者不能靠它区分。必须显式查标签表。**已核实（E19）：`Registry.getTag(TagKey)` 返回 `Optional<HolderSet.Named<T>>`，且缺失的标签根本不进 `getTags()` 流** —— 因此 `Optional.empty()` 精确对应「缺失」：

```java
TagKey<Structure> target = (mode == StructureWaypointMode.WHITELIST) ? WHITELIST : BLACKLIST;
boolean present = structures.getTag(target).isPresent();   // E19：empty() == 标签文件不存在
if (!present) {
    if (mode == StructureWaypointMode.WHITELIST && !whitelistMissingWarned) {
        whitelistMissingWarned = true;
        LOGGER.warn("structure waypoint: tag teleportwaypoint:waypoint_whitelist is missing; no waypoints will be placed. Check that the mod's data pack is enabled.");
    } else if (mode == StructureWaypointMode.BLACKLIST && !blacklistMissingWarned) {
        blacklistMissingWarned = true;
        LOGGER.warn("structure waypoint: tag teleportwaypoint:waypoint_blacklist is missing; every structure will receive a waypoint.");
    }
    // 缺失时的筛选结果与「为空」一致（白名单 ⇒ 谁都不放；黑名单 ⇒ 全都放），因此不改变下面的返回值
}
return (mode == StructureWaypointMode.WHITELIST) ? holder.is(target) : !holder.is(target);
```

> **WARN 文本必须英文**（S8 的一致性要求；且这是服务器运维看的日志）。
> **为空标签绝不记任何日志** —— `"values": []` 是合法配置（白名单空 = 都不放；黑名单空 = 都放）。
>
> **一个必须避免的坑：** 不要用 `structures.getOrCreateTag(target)` 来判存在性 —— 它会给**缺失**的标签凭空创建一个空 `HolderSet`（`Registry.java:169`），从而把「缺失」永久伪装成「为空」，WARN 永远不会出现。

**验证：** `gradlew.bat build --offline --console=plain`；`gradlew.bat runServer --offline --console=plain` 启动后**不得**出现上述两条 WARN（证明两个标签被正确加载）。

---

### T14 — `StructureWaypointHandler`

**文件：**
- 新增：`src/main/java/com/zonlong/teleportwaypoint/structure/StructureWaypointHandler.java`

**签名与注册：**
```java
public static void onChunkLoad(ChunkEvent.Load event)
```

**强制早退顺序（顺序即成本模型，不得调整）：**

| 门 | 判定 | 失败时 |
|---|---|---|
| 1 | `event.getLevel() instanceof ServerLevel level` | return（客户端） |
| 2 | `event.getChunk() instanceof LevelChunk chunk` | return（`getChunk()` 声明为 `ChunkAccess`） |
| 3 | `event.isNewChunk()` | return（**D1 的唯一门**；磁盘加载、世界重启、玩家走远再回来全在这里出局） |
| 4 | `chunk.getAllStarts().isEmpty() && chunk.getAllReferences().isEmpty()` | return（绝大多数区块在此出局，两次 `Map.isEmpty()`） |
| 5 | 重入守卫：`if (IN_HANDLER) { Debug.reentered(chunk.getPos()); return; } IN_HANDLER = true;` | return |
| 6 | `CommonConfig.STRUCTURE_WAYPOINT_MODE` 可读（配置已加载） | — |

**门 5 的安全网语义（写进注释）：** 这道守卫**不是修复已存在的行为**，而是在未来有人误引入强制加载（F1–F3）时立刻暴露出来 —— 一旦重入发生，被跳过的那次不会再有第二次机会（D1）。因此它记 **WARN 而非 DEBUG**。

**处理体（`try/catch (Throwable)` 必须是真正的最外层，S5）：**

```java
if (IN_HANDLER) { Debug.reentered(chunk.getPos()); return; }
IN_HANDLER = true;
try {
    ServerLevel level = (ServerLevel) event.getLevel();
    LevelChunk chunk = (LevelChunk) event.getChunk();

    Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    int handled = 0;
    for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
        if (handled >= MAX_STRUCTURES_PER_CHUNK) { Debug.limitReached(chunk.getPos(), MAX_STRUCTURES_PER_CHUNK); break; }
        StructureStart start = e.getValue();
        if (start == null || !start.isValid()) continue;

        Optional<String> id = StructureWaypointNaming.derive(structures, start);
        if (id.isEmpty()) continue;

        if (!StructureTagFilter.allows(structures, e.getKey())) { Debug.skippedByTag(...); continue; }
        handled++;

        Optional<BlockPos> pos = StructureWaypointScanner.findPlacement(chunk, start);
        if (pos.isEmpty()) { Debug.noPlacement(structures.getKey(e.getKey()), chunk.getPos()); continue; }

        if (StructureWaypointPlacer.place(level, chunk, pos.get(), id.get())) {
            Debug.placed(structures.getKey(e.getKey()), pos.get(),
                         chunk.getBlockState(pos.get()).getValue(WaypointBlock.WATERLOGGED));
        }
    }
} catch (Throwable t) {
    Debug.failed(event.getChunk().getPos(), t);   // WARN，绝不外抛
} finally {
    IN_HANDLER = false;                            // ← 必须在 finally
}
```

**六点核对：**
1. `IN_HANDLER = false` 在 `finally` 里 —— 否则一次异常会永久关闭本机制。
2. `handled` 在**标签筛选之后**自增 ⇒ 被名单排除的结构不占配额（否则一个黑名单结构会挤掉一个合法结构）。
3. `debugMode` **不**作为早退门。debug 关闭时仍要放置，只是不打日志。
4. **禁止**在本方法内调用 `level.getBlockState(...)`（F1）。上表 `Debug.placed` 里的 `chunk.getBlockState` 用的是 `chunk`，合法。
5. `MAX_STRUCTURES_PER_CHUNK = 2`（§3 P1）是 `private static final int`。
6. `IN_HANDLER` 是 `private static volatile boolean`（S2）。

**注册（修改 `TeleportWaypoint.java`）：**

在 `:33`（`modEventBus.addListener(DatapackRegistration::onAddPackFinders);`）之后新增：
```java
net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(StructureWaypointHandler::onChunkLoad);
```
> 注意：`ChunkEvent.Load` 在 **`NeoForge.EVENT_BUS`**（游戏事件总线），**不是** `modEventBus`（E15）。写成 `modEventBus.addListener` 会静默不工作 —— 这是本任务最可能的低级错误。

**验证：**
- `gradlew.bat build --offline --console=plain` 通过
- `gradlew.bat runServer --offline --console=plain` 能启动到 "Done"（注册本身不崩）
- 新世界 + `debugMode=true` 后走进未探索区域 ⇒ 日志出现 `structure waypoint: placed ...`

---

### T15 — GameTest 骨架 + 纯逻辑断言

**文件：**
- 新增：`src/main/java/com/zonlong/teleportwaypoint/StructureWaypointGameTests.java`
- 新增：`src/main/resources/data/teleportwaypoint/structure/structurewaypointgametests.nbt`（**空模板**，1×1×1 无方块）

**为什么放 `main` 而不是 `src/test`：** 项目**没有 `src/test`**，且历史上多次明确决定不引入测试框架（`memory/decisions-log.md`）。NeoForge 的 GameTest 只要类在 mod 的 source set 里并带 `@GameTestHolder`，就会被 `neoforge.enabledGameTestNamespaces=teleportwaypoint` 扫到，**不需要**新 source set、不需要新依赖。这条路把「可用但空置」的 `runs.gameTestServer` 用起来，而不改变「不引入测试框架」这一决定。

**类骨架（E21/E22/E23 已核实）：**

```java
@GameTestHolder(TeleportWaypoint.MODID)      // E22：自动注册本类全部 @GameTest 方法
public final class StructureWaypointGameTests {

    @GameTest(template = "structurewaypointgametests", templateNamespace = TeleportWaypoint.MODID)
    public void naming_vanillaIds(GameTestHelper helper) { ... }   // E23：签名就是 (GameTestHelper)
}
```

**模板文件是必需的（E21），别漏：**
- 路径：`src/main/resources/data/teleportwaypoint/structure/structurewaypointgametests.nbt`
- 内容：一个 1×1×1 的**空**结构（`size:[1,1,1]`、`palette` 只含 air、`blocks` 为空列表、`entities` 为空列表）。
- `@GameTest.template()` 默认 `""` ⇒ 结构名退化为类名的小写形式；本计划显式给出 `template` 与 `templateNamespace`，以免依赖那条隐式规则。
- **不要凭空手写这个 NBT。** 最稳的生成办法：在游戏里用结构方块把一个 1×1×1 空区域保存到 `run/gameteststructures/`，再拷到上面路径。
- 本轮已在 `client-extra.jar` 内搜过 `structure/empty`，**未找到** ⇒ 不要指望 `@GameTest(template = "empty", templateNamespace = "minecraft")`。
- 无论走哪条，**先只加 1 个最简单的测试跑一次 `runGameTestServer`**，确认模板能被解析，再批量加下面的断言。这是本任务唯一的资源文件风险点。

**注册：** 依 E22，**先不加**任何 `RegisterGameTestsEvent` 代码，只靠 `@GameTestHolder`。若 `runGameTestServer` 报告找不到任何测试，再在 `TeleportWaypoint` 构造函数里补显式注册。

**T15 覆盖的断言（纯逻辑，除 `helper` 参数外不读写 level）：**

| 测试方法 | 断言 |
|---|---|
| `naming_vanillaIds` | `minecraft:end_city` → `minecraft.end_city`；`minecraft:jungle_pyramid` → `minecraft.jungle_pyramid`；`minecraft:monument` → `minecraft.monument`；`minecraft:mansion` → `minecraft.mansion`；`minecraft:fortress` → `minecraft.fortress` |
| `naming_truncation` | 构造一个 namespace+path 超过 64 字符的 `ResourceLocation`，断言 `deriveId` 结果 **`length() <= 64`** 且 **`WaypointBlockEntity.isValidId(...)` 为 true** 且**不以 `.` 或 `_` 结尾**（钉住设计 §7.2.1 的诚实边界：防截断在这里，不在键前缀） |
| `naming_modStructure` | `betteroceanmonuments:ocean_monument` → `betteroceanmonuments.ocean_monument`（35 字符，设计文档实测样例） |
| `naming_humanize` | `humanize("minecraft.end_city")` → `"Minecraft End City"`（`.` 与 `_` 都当分隔符） |
| `naming_keys` | `Naming.key("minecraft.end_city")` → `"tpwp.minecraft.end_city"`；`Naming.key(null)`、`Naming.key("")`、`Naming.key("BAD ID")` → `"tpwp.empty"` |
| `idPattern_acceptsDot` | `isValidId("minecraft.end_city")` 与 `isValidId("end_city")` 为 true；`isValidId("a..b")`、`isValidId(".a")`、`isValidId("a.")` 为 false |
| `idPattern_rejectsLegacyRegression` | `isValidId("空")`、`isValidId("A")`、`isValidId(null)` 为 false |
| `maxLength_consistency` | `Naming.MAX_ID_LENGTH == WaypointBlockEntity.MAX_TEXT_LENGTH` |
| `displayName_notEmptyFallback` | 对白名单 12 个结构的派生 id，断言 `Naming.displayName(id)` **不等于** `Component.translatable("tpwp.empty")`（钉住「派生 id 不被静默降级为 empty」，替代上游 §10.5 第 6 条） |

**验证：**
```bat
cd /d D:\Minecraft\Teleport-Waypoint-ds_flash
gradlew.bat runGameTestServer --offline --console=plain
```
全部通过。**注意**：`gameTestServer` 默认会在**没有** gametest 时崩溃（`build.gradle:73` 的注释明写），所以本任务同时把「空置的测试基建」变成「有内容的测试基建」。

---

### T16 — GameTest：真实世界的几何与名单断言

**文件：**
- 修改：`src/main/java/com/zonlong/teleportwaypoint/StructureWaypointGameTests.java`

**前置：** T15 的空模板已能跑通。

**依赖：** T10–T14。

**测试方法：**

| 方法 | 做法 | 断言 |
|---|---|---|
| `scanner_l1_highestRoofedColumn` | 搭一个 7×7 平台（地板）+ 四面墙 + 顶（高 5），用 `StructurePiece` 的匿名子类给出与该区域相同的包围盒，构造 `StructureStart`，调 `StructureWaypointScanner.findPlacement(realChunk, start)` | 落点 = **屋内最高合格格**，且 `roofed(p, 8)` 为 true；**同一个 start 跑两次得到同一格**（确定性/全序） |
| `scanner_l2_fallback` | 用一个包围盒**完全落在本区块之外**的 piece（这样 L1 过滤后为空，但 `isValid()` 仍为 true），外加露天平坦地形 | 落点在本区块 16×16 内，且等于手工按 §3.1 的 4 元键算出的列与「自上而下第一个合格 y」 |
| `scanner_invalidStartNoop` | `new StructureStart(structure, chunkPos, 0, new PiecesContainer(List.of()))` ⇒ `isValid() == false` | `findPlacement` 返回 `Optional.empty()`，**且完全不进入 L2**（钉住第一行的早退） |
| `scanner_excludesLava` | 在候选格/上方两格放岩浆，其余合规 | **不**把该格判为合格（`isValidPlacement` 私有 → 通过 `findPlacement` 的落点不在岩浆格上间接断言） |
| `scanner_allowsWater` | 候选格是水源方块，下方实心、上方两格水 | 落点可以是该格；放置后 `getValue(WATERLOGGED) == true` |
| `scanner_inChunkOnly` | 让 piece 包围盒大幅跨出本区块 | 落点必在本区块内（S4） |
| `tagFilter_whitelistContainsAll12` | 从 `level.registryAccess()` 取 `Registries.STRUCTURE`，`structures.getTag(waypoint_whitelist)` | 存在的 12 个条目全部 `is(...)` 为 true；`minecraft:village_plains` 为 **false** |
| `tagFilter_blacklistContainsVillage` | 同上，黑名单标签 | `#minecraft:village` 的 5 个成员全部命中；`minecraft:end_city` 为 **false** |
| `handler_placesAndRegisters` | 在 GameTest 里放一个锚点方块（模拟放置器输出），构造 BE，断言 `setId("minecraft.end_city")` 后 `getId()` 未被降级 | 钉住 T1 放宽后的端到端效果 |
| `handler_isNewChunkGuard` | 直接调 `StructureWaypointHandler` 的处理体（把「门 3」的参数化以便测试，或断言 `ChunkEvent.Load` 的构造在 `newChunk=false` 时处理器早退） | `newChunk=false` ⇒ **零方块变化**（钉住 D1 的核心保证） |

**实现要点 / 已知困难：**
1. `StructureStart` 的构造是 `public StructureStart(Structure structure, ChunkPos chunkPos, int references, PiecesContainer pieceContainer)`（`StructureStart.java:33`）—— `StructurePiece` 可以匿名子类化，`PiecesContainer` 可由 `List<StructurePiece>` 构造。
2. **`isValid()` 就是 `!pieceContainer.isEmpty()`**（`StructureStart.java:118-120`）⇒ **用空 piece 列表构造的 `StructureStart` 必然 `isValid() == false`，会在第一行早退，走不到 L2。** 要测 L2 必须给一个**包围盒完全在本区块之外**的 piece。这是本任务最容易写错的一条。
3. **不要**为了测试去写真实的结构到模板池；用匿名 `StructurePiece` 给出人造包围盒即可，这正符合「纯几何断言」的设计意图。
4. 若要断言 `handler_isNewChunkGuard`，**推荐做法**是把门 3 之后的部分抽成一个 `static void handleLoad(ServerLevel, LevelChunk, boolean isNewChunk)`，测试直接调它并传 `false`。这样不必伪造 engine 的 `newChunk` 标记（那是不可伪造的），同时把门 3 的语义钉在测试里。
5. GameTest 有默认超时（`@GameTest.timeoutTicks()` 默认 100）；若某测试挂起即为死锁回归 —— 这是**唯一**能自动发现 F1–F3 违规的机制，务必保留一个「跑完整 handler 并返回」的测试。

**验证：**
```bat
gradlew.bat runGameTestServer --offline --console=plain
```
全部通过，且**无超时/挂起**。

---

### T17 — 清单核对：find-all 强制纪律

**文件：** 无新文件（本任务是评审关卡）

**命令与预期：**

| # | 命令 | 预期 |
|---|---|---|
| 1 | `rg "teleportwaypoint\.waypoint\." src/main/java` | **0 行**（T3 完成） |
| 2 | `rg "teleportwaypoint\.waypoint\." src/main/resources/assets/teleportwaypoint/lang/` | 两文件各 **14 行**（T6 旧键保留） |
| 3 | `rg -c '"tpwp\.' src/main/resources/assets/teleportwaypoint/lang/` | 两文件各 **13** |
| 4 | `rg "level\.getBlockState|level\.getFluidState|getChunkAt|getChunk\(|structureManager\(\)|startsForStructure|getStructureAt|getAllStructuresAt" src/main/java/com/zonlong/teleportwaypoint/structure/` | **0 行**（F1–F3） |
| 5 | `rg "newChunk\|isNewChunk" src/main/java/com/zonlong/teleportwaypoint/structure/` | 恰好 1 处（门 3） |
| 6 | `rg "catch \(Throwable" src/main/java/com/zonlong/teleportwaypoint/structure/` | 恰好 1 处，且在 `StructureWaypointHandler` |
| 7 | `rg -n "static (?!final)" src/main/java/com/zonlong/teleportwaypoint/structure/` | 只有两处日志去重布尔 + `IN_HANDLER`（S2） |
| 8 | `rg "setChanged|WaypointManager\.register" src/main/java/com/zonlong/teleportwaypoint/structure/` | **0 行**（S9；`setChanged` 只在 `setId` 内部被间接调用） |
| 9 | 两处标签 JSON 都含 `"replace": false` | 各 1 处 |
| 10 | `WaypointBlockEntity.isValidId` 的正则含 `\.` | 是 |
| 11 | `rg "start\.getPos\(\)" src/main/java/com/zonlong/teleportwaypoint/structure/` | **0 行**（E16：该方法不存在，写了根本编译不过） |
| 12 | `rg "start\.getBoundingBox\(\)" src/main/java/com/zonlong/teleportwaypoint/structure/` | **0 行**（E17/E18：会把锚点列与搜索盒偏移 12 格） |
| 13 | `rg "getOrCreateTag" src/main/java/com/zonlong/teleportwaypoint/` | **0 行**（R15） |
| 14 | `rg -n "\.above\(1\)\|\.above\(2\)\|p\.above\(k\)" src/main/java/com/zonlong/teleportwaypoint/structure/` | 每处上方都有 `maxBuildHeight` 边界保护（R8/R16） |
| **15** | `rg "level\.setBlock" src/main/java/com/zonlong/teleportwaypoint/structure/` | 恰好 1 行，且**唯一标志是 `Block.UPDATE_CLIENTS`**（F6/E3a） |
| **16** | `rg "UPDATE_NEIGHBORS\|UPDATE_ALL\|UPDATE_KNOWN_SHAPE\|UPDATE_SUPPRESS_DROPS\|UPDATE_MOVE_BY_PISTON" src/main/java/com/zonlong/teleportwaypoint/structure/`，再排除掉 `StructureWaypointPlacer` 里那段**解释性注释** | **0 行命中代码**（注释必须提到 `UPDATE_ALL` 以警示，故按「非注释行」判定） |

**验证：** 上表 10 条全部符合。任一条不符即回到对应任务修。

---

### T18 — 构建与运行门

**文件：** 无新文件

**步骤与预期：**

```bat
cd /d D:\Minecraft\Teleport-Waypoint-ds_flash
gradlew.bat build --offline --console=plain
gradlew.bat runServer --offline --console=plain
gradlew.bat runGameTestServer --offline --console=plain
```

| 命令 | 通过判据 |
|---|---|
| `build` | `BUILD SUCCESSFUL`，jar 产出在 `build/libs/teleportwaypoint-0.4.0.jar` |
| `runServer` | 启动到 "Done"，**无** DFU/`RegistryDataLoader` 错误（点名 tag 文件），**无** 两条标签缺失 WARN |
| `runGameTestServer` | 全部测试通过，无超时 |

**在 `runServer` 里额外做一次迁移确认：** 打开新建世界的 `run/config/teleportwaypoint/common.toml`，确认 `[structureWaypoints]` 节的 `mode = "WHITELIST"`、`debugMode = false`，且 `[optionalDataPacks]` 两项都是 `false`。

---

### T19 — 游戏内验收（人工）

**文件：** 无新文件

**清单（顺序执行，每步记录结果）：**

| # | 场景 | 期望 |
|---|---|---|
| 1 | 新世界，`debugMode=true`，飞到未探索区域 | 日志出现 `structure waypoint: placed structure=minecraft:... pos=... waterlogged=...` |
| 2 | `/locate structure minecraft:igloo` + 传送过去 | 雪屋内有 **恰好 1 个**锚点 |
| 3 | 海洋神殿 / 海底 | 锚点落在**水里**且 `WATERLOGGED=true`（F3 查看方块状态） |
| 4 | 村庄 | **没有**锚点（白名单模式，村庄不在白名单里） |
| 5 | 下界要塞 / 要塞 / 远古城市 / 试炼密室 / 沙漠神殿 | 各有 **恰好 1 个**锚点 |
| 6 | 右键锚点 | 激活消息显示**正确的本地化名**（如 "远古城市"） |
| 7 | `/data get block <pos>` | `waypoint_id` = `minecraft.ancient_city`（带点）；`uid` 存在 |
| 8 | 同一结构的两个实例 | `waypoint_id` **相同**、`uid` **不同** |
| 9 | **破坏**锚点 → 离开区块再回来 | **不再生成**（D1 核心语义 —— 与上游 §5.4 的旧设计**相反**，本方案是「不重生」） |
| 10 | 世界重启 → 回到同一地点 | 已有锚点仍在，**不重复** |
| 11 | 打开**旧存档**（0.3.0 建的，有旧数据包锚点） | 旧锚点名**不变**（走保留的旧键），新区块里不补锚 |
| 12 | Xaero 小地图 / 世界地图 | 显示新锚点，名字与新键一致；按维度隔离 |
| 13 | `debugMode=false` 重启 | 日志中**无**任何 `structure waypoint:` 行 |
| 14 | 专用服务器 + 原版客户端 | 聊天消息**不显示裸键** |
| 15 | **负面信号检查** | 日志中**不得**出现 `"Chunk not there when requested"`、watchdog "A single server tick took…"、任何指向 worldgen 的 `CrashReport`。死锁表现为主线程停在 `ServerChunkCache.getChunk → managedBlock` |

**M1–M4 实测（§3.2）：** 在第 1 步期间挂 spark profiler，按 §3.2 的四项采样并记录数值。

---

### T20 — README 更新

**文件：**
- 修改：`README.md`

**必须覆盖的四块：**

| 块 | 内容 |
|---|---|
| **新机制说明** | 「区块补锚」是什么、触发条件（**只在首次生成的区块**）、落点规则（两层候选：结构内 → 区块兜底；允许水中放置；排除岩浆）、命名规则（`waypoint_id = <namespace>.<path>`，显示名键 `tpwp.<id>`） |
| **迁移步骤** | ① 两个 `defaultEnable*StructureWaypoints` 现在默认 `false`（**仅为新世界生效**）；② **既有存档必须手动关闭**两个内置数据包，否则新区块里会出现**双份锚点**；③ 旧锚点名字不受影响（旧键保留为别名）；④ **存量存档不补锚**，这是设计意图不是 bug |
| **两套名单的语义** | 用**数据包标签**（不是配置项）：`teleportwaypoint:waypoint_whitelist`（12 条，默认模式）与 `teleportwaypoint:waypoint_blacklist`（22 个结构，压成 9 条）；`mode` 配置只切换用哪张；**标签缺失**记 WARN 一次，**标签为空是合法配置**（白名单空 = 都不放；黑名单空 = 都放）；给整合包作者追加条目的三步示例（含 `{"id": "...", "required": false}`） |
| **已知限制** | ① 存量存档完全不补放，无 backfill 开关；② 锚点被拆除后**不会**重生；③ 主区块已存在、结构只向新生成邻区块延伸的极少数情形会漏；④ 结构内一个合格位置都没有时**永远**没有锚点；⑤ 每区块最多 2 个结构、每结构 1 个锚点；⑥ 探测预算 2000 次超限即放弃；⑦ 路线甲（处理器精修层）本版本未做 |

**验证：** 构建通过；对照 T19 的实际观察结果，确认 README 描述与实测一致（尤其第 9、11 步）。

---

### T21 — 版本与设计文档状态更新

**文件：**
- 修改：`gradle.properties`（`mod_version=0.3.0` → `0.4.0`）
- 新增：`docs/plans/2026-09-14-chunk-load-waypoint-injection-verification.md`（把 T18/T19/T20 的实测记录写进去）
- **不改** `docs/plans/2026-09-14-structure-waypoint-injection-design.md`（保持「待评审」原样，冲突已记录在本计划 §0.3）
- 可选：在区块补锚设计文档 `docs/plans/2026-09-14-chunk-load-waypoint-injection-design.md` 的状态行加一句「实施计划已产出：`...-implementation.md`」

**验证：** `gradlew.bat build --offline --console=plain` 产出 `teleportwaypoint-0.4.0.jar`。

---

## 5. 任务依赖图

```
T1 ─┬─→ T2 ──→ T3 ────────────────┐
    │        │                      │
T4 ─┼─→ T5   │                      │
    │        │                      │
T6 ─┴────────┴──────────────────────┤
T7 ────────────────────────────────-┤
                                    ▼
                      T8 ──→ T9 ──→ T10 ──→ T11 ──→ T12
                                                │
                                    T13 ────────┤
                                                ▼
                                               T14
                                                │
                          T15 ──────────────────┤
                                                ▼
                                               T16
                                                │
                                    T17 ─→ T18 ─→ T19 ─→ T20 ─→ T21
```

**可并行**：{T1,T2,T3} / {T4,T5} / {T6,T7} 三组之间无依赖；T9 与 T10 可并行；T12 与 T13 可并行。

---

## 6. 风险登记

| # | 风险 | 概率 | 影响 | 对策 |
|---|---|---|---|---|
| R1 | `ChunkEvent.Load` 处理器抛异常 ⇒ 区块 future 异常完成 ⇒ **服务器崩溃** | 中 | **致命** | T14 的 `try/catch (Throwable)` 是最外层且 `finally` 复位守卫；T16 留一个「跑完整 handler 并返回」的 GameTest 作死锁/异常回归 |
| R2 | 误用跨区块访问 ⇒ 主线程自我阻塞 ⇒ 服务器卡死 | 中 | **致命** | Scanner 参数类型收窄为 `LevelChunk`（结构性防线）；T17 第 4 条 find-all 必须 0 行；重入守卫作安全网并记 WARN |
| R3 | `WaypointBlockEntity.isValidId` 未放宽 ⇒ 所有新 id 被静默拒绝 ⇒ 锚点名全变 "empty" | **高** | 高 | T1 是**第一个**任务；T15 有 `idPattern_acceptsDot` 与 `displayName_notEmptyFallback` 两个断言 |
| R4 | 两个标签 JSON 语法错 ⇒ 数据包注册表硬失败 ⇒ 世界无法加载 | 中 | 高 | T7 用 `runServer` 做完整 codec 测试（会点名文件）；两文件都用 `ConvertFrom-Json` 预检 |
| R5 | lang JSON 一个逗号错 ⇒ **整个语言文件被丢弃** ⇒ 全部名字变裸键 | 中 | 中 | T6 用 `ConvertFrom-Json` + `rg -c` 双计数核对 |
| R6 | 忘了改 `defaultEnable*` 默认值 ⇒ 老存档双份锚点 | 中 | 中 | T4 明确列为变更项；T20 README 给手动关闭步骤 |
| R7 | 事件注册写成 `modEventBus.addListener` ⇒ 静默不工作 | 中 | 中 | T14 显式标注 `NeoForge.EVENT_BUS`（E15）；T19 第 1 步是端到端探针 |
| R8 | `p.above(1/2)` 越出可建造高度上界 ⇒ `getBlockState` 越界 | 中 | 中 | T11 的 k 循环加 `p.getY() + k < maxBuildHeight` 保护（设计伪代码未覆盖） |
| R9 | `maxBuildHeight` 是排他上界，误当成含端点 ⇒ 最后一格越界 | 低 | 中 | E12 已核实；T11 第 1 条写 `< getMaxBuildHeight()` |
| R10 | 光照传播（发光等级 14）成为热点 | 低 | 中 | §3.2 M2 实测；超 5 ms 则下调 `PROBE_BUDGET` 到 500 |
| R11 | 白名单/黑名单实际展开数与「12 + 22 = 34」不符 | 低 | 低 | T7 的交叉核对脚本断言三处集合关系 |
| R12 | `Registry.getTag` 无法区分「缺失」与「为空」 | **已排除** | 低 | E19 已核实：`getTag` 返回 `Optional`，缺失的标签不进 `getTags()` 流 ⇒ `empty()` 精确对应缺失 |
| R13 | GameTest 模板 NBT 缺失或格式不对 ⇒ 全部测试拿不到结构 | **中** | 中 | T15 明确「先只加 1 个最简单测试跑通再批量加」；模板必须由结构方块实际保存生成，不手写（E21） |
| R14 | 误用 `start.getBoundingBox()` 当锚点列或 L1 搜索盒 ⇒ 偏 12 格且被记忆化，测试难以发现 | **中** | 中 | E16–E18 已核实并写入 T10 的显式「不要写」清单；T16 的 `scanner_l1_highestRoofedColumn` 用不含 terrainAdaptation 的匿名 piece，能暴露偏差 |
| R15 | 用 `structures.getOrCreateTag(...)` 判标签存在性 ⇒ 把「缺失」永久伪装成「为空」⇒ WARN 永不出现 | 中 | 低 | T13 显式列为「必须避免的坑」 |
| R16 | `p.above(k)` 越界（除 R8 的高度上界外，还有 `roofed` 里的同类问题） | 中 | 中 | T11 的 `roofed` 循环首行就加 `p.getY() + k >= maxBuildHeight → return false` |
| **R17** | **放置用 `UPDATE_ALL` ⇒ 邻居更新级联读相邻区块 ⇒ 主线程自死锁（已实际发生）** | **高** | **致命** | 改用 `Block.UPDATE_CLIENTS`（F6/E3a）；T17 第 15/16 条 find-all 长期钉住。**表现：服务器冻结、无崩溃、无日志、watchdog 也不报**——因为它本身就卡在 `managedBlock` 里 |

---

## 7. 完成定义（Definition of Done）

- [ ] T1–T21 全部完成
- [ ] `gradlew.bat build --offline --console=plain` → `BUILD SUCCESSFUL`，产出 `teleportwaypoint-0.4.0.jar`
- [ ] `gradlew.bat runServer --offline --console=plain` → 启动到 "Done"，无 DFU 错误、无标签缺失 WARN
- [ ] `gradlew.bat runGameTestServer --offline --console=plain` → 全部通过、无超时
- [ ] T17 的 **14 条** find-all 全部符合
- [ ] T19 的 15 步游戏内验收全部通过，且**第 9 步（拆除不重生）**与**第 11 步（旧存档名字不变）**经人工确认
- [ ] 日志中无 `"Chunk not there when requested"`、无 watchdog 超时
- [ ] README 四块内容齐备
- [ ] `docs/plans/2026-09-14-structure-waypoint-injection-design.md` **未被修改**（`git diff` 确认）
- [ ] M1–M4 实测数值已记录，且 P1/P2 的最终值已按实测回填或明确保留初值

---

## 8. 明确不做（Non-Goals）

| 不做 | 原因 |
|---|---|
| 存量存档 backfill / `backfillExistingChunks` 配置项 | D1 的直接推论（设计 Q4） |
| 任何形式的持久化去重名单（SavedData / 内存 Set / 邻近方块检查） | D1：`isNewChunk` 一个门就够了（对上游 §5.4 的有意取代） |
| 路线甲：自定义 `StructureProcessor` + 数据包处理器列表 | 本版本不做（对上游 §3.3 的推迟）；旧数据包按 D5 保留但默认关闭 |
| 删除旧的两个 NBT 覆盖数据包 | D5：保留 |
| 「排除名单」配置项 | D5：配置面不增加 |
| 锚点显示名翻译键的旧键清理 | D4：永久保留为别名（本版本） |
| 把标签名或探测预算暴露为配置项 | D6：标签名硬编码；§3：预算是常量 |
| 调试命令 `/teleportwaypoint debug structures` | 对上游 §10.4 的推迟；列为可选后续 |
| 引入 `src/test`、JUnit、或任何新测试依赖 | 项目历史决定；GameTest 已在 source set 内可用（T15） |
| 修改上游「待评审」设计文档 | 用户明确要求保持原样；冲突已记在本文档 §0.3 |
| 协议／线格式改动 | D10：`waypoint_id` 不含前缀，前缀只在客户端拼 |

---

## 9. 可选后续（本版本不做，做完 T19 再评估）

| # | 项 | 价值 |
|---|---|---|
| O1 | `/teleportwaypoint debug structures` 调试命令 | 把「查结构 ID」「验证落点」「生成可直接粘贴的 lang 行」从考古变成一行命令（上游 §10.4） |
| O2 | 为整合包作者预置常用模组结构的白名单条目（`{"id":..., "required": false}`） | 装上即生效（D6 理由 3 的直接应用） |
| O3 | 结构落点的「可视性」偏好（偏向结构入口方向） | 玩家更容易发现锚点 |
| O4 | `checkLang` Gradle 任务（挂 `processResources`），双向比对两个 lang 文件的键集合 | 一个逗号错毁掉全部名字（R5）的结构性防御（上游 §10.1） |
| O5 | 结构探索进度统计（只读，不放置） | 给玩家反馈，与去重零存储原则不冲突 |

---

## 10. 交付物清单

| 类型 | 路径 |
|---|---|
| 本实施计划 | `docs/plans/2026-09-14-chunk-load-waypoint-injection-implementation.md` |
| 新增 Java（6） | `structure/StructureWaypointHandler.java`、`structure/StructureWaypointScanner.java`、`structure/StructureWaypointPlacer.java`、`structure/StructureTagFilter.java`、`structure/StructureWaypointNaming.java`、`structure/StructureWaypointDebug.java` |
| 新增 Java（工具） | `util/Naming.java` |
| 新增 Java（测试） | `StructureWaypointGameTests.java` |
| 新增资源（2，标签） | `data/teleportwaypoint/tags/worldgen/structure/waypoint_whitelist.json`、`.../waypoint_blacklist.json` |
| 新增资源（1，GameTest 模板） | `data/teleportwaypoint/structure/structurewaypointgametests.nbt`（1×1×1 空结构，由结构方块实际保存生成） |
| 修改 Java（7） | `block/entity/WaypointBlockEntity.java`、`config/CommonConfig.java`、`config/StructureWaypointMode.java`（新增枚举）、`client/ClientWaypointInfo.java`、`network/ActivatedWaypointInfo.java`、`client/xaero/XaeroMinimapIntegration.java`、`TeleportWaypoint.java` |
| 修改资源（2） | `assets/teleportwaypoint/lang/en_us.json`、`assets/teleportwaypoint/lang/zh_cn.json` |
| 修改文档（2） | `README.md`、`gradle.properties` |
| 新增文档（1） | `docs/plans/2026-09-14-chunk-load-waypoint-injection-verification.md` |
| **不改** | `docs/plans/2026-09-14-structure-waypoint-injection-design.md`、两份内置数据包、全部既有网络 payload |
