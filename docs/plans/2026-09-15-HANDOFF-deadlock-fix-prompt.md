# 交接提示词 —— 修复「区块补锚」的主线程自死锁

**用途：** 交给负责修复的工作会话。本提示词自包含：不需要读上一会话的对话，只需要读下面点名的文件。
**日期：** 2026-09-15
**性质：** 阻塞性缺陷修复（服务器静默冻结）

---

## 直接复制给新会话的提示词

```
任务：修复一个 Minecraft 1.21.1 NeoForge 模组里会导致服务器永久冻结的主线程自死锁。
这是一个已经定位完成的 bug，不需要你重新排查，请直接实施修复并验证。

项目：D:\Minecraft\Teleport-Waypoint-ds_flash
分支：ds_flash    当前 HEAD：6c97b5e「尝试修复死锁，失败」
NeoForge 1.21.1 反编译源码（唯一权威依据）：D:\Minecraft\BeLoong-Core\build\nf-src
   注意：这是本工作区里可用的 NeoForge/MC 源码树，行号引用都以它为准。

=====================================================================
一、症状
=====================================================================
游玩/探索一段时间后服务器**永久冻结**：
- 不崩溃，无 crash-report；
- 日志里没有任何有价值的信息（没有 exception、没有 watchdog 报告）；
- 渲染线程/客户端仍活着，只有服务端线程哑掉；
- spark 会报 "Timed out waiting for world statistics"。

已在 run/logs 里找到三例确凿现场（都是「传送到白名单结构附近」触发的）：
- 2026-09-14-7.log.gz：21:43:58 激活「林地府邸」锚点 → 服务端线程最后一行 21:45:04 → 21:46:05 spark 超时
- 2026-09-14-6.log.gz：21:49-21:51 激活锚点 → 21:51:25 切旁观者飞向新地形 → 21:52:05 / 21:53:05 spark 超时
- 2026-09-15-3.log.gz：00:38:48 `/locate structure minecraft:ancient_city` → 00:39:05 / 00:40:05 spark 超时

=====================================================================
二、根因（已核实到源码行，不要再自行推翻）
=====================================================================
StructureWaypointPlacer.place() 通过 Level#setBlock 放置锚点。
Level#setBlock 会派生**邻居通知**，而邻居通知会**同步读写相邻区块的方块**。

关键：这样的派生路径有**两条**，由**不同的标志位**把守 —— 这是上一个修复
（6c97b5e 把 UPDATE_ALL 改成 UPDATE_CLIENTS）失败的原因：

  路径 A  红石：Level.java:286-291      门槛 flags & 1  (UPDATE_NEIGHBORS)
  路径 B  形状级联：Level.java:293-298  门槛 (flags & 16) == 0  (UPDATE_KNOWN_SHAPE)

Block.java:77-86：UPDATE_NEIGHBORS=1  UPDATE_CLIENTS=2
                  UPDATE_KNOWN_SHAPE=16  UPDATE_ALL=3
**UPDATE_ALL(3) 与 UPDATE_CLIENTS(2) 都不含第 16 位**，而 Level.java:294 的
`flags & -34` 会把第 1 位与第 32 位一并清掉 ⇒ 在路径 B 上这两个值的**行为完全相同**。
所以改标志位无效。

路径 B 的完整链条：
  Level.markAndNotifyBlock:296  state.updateNeighbourShapes(this, pos, flags & -34, 510)
  BlockBehaviour.java:781-788   for (6 个方向) level.neighborShapeChanged(...)
  Level.java:378-380            → CollectingNeighborUpdater.shapeUpdate:29-33
  CollectingNeighborUpdater:137-144 → NeighborUpdater.executeShapeUpdate
  NeighborUpdater.java:36       BlockState bs = level.getBlockState(邻居)   // 接收者是 ServerLevel
  Level.java:405-413            → getChunk(x, z, ChunkStatus.FULL)          // requireChunk = true
  ServerChunkCache.java:158-159   this.mainThreadProcessor.managedBlock(f::isDone);   // ★ 永久阻塞

为什么会永久卡死：这个补全任务由**主线程自己的邮箱**投递
（ChunkStatusTasks.full:191,221），所以主线程在等自己。
ServerChunkCache.java:162-163 的 IllegalStateException 在 managedBlock **之后**，
永远到不了；watchdog 也需要主线程推进，于是连它一起冻住 ⇒ 就是"无日志的静默冻结"。

为什么只偶发：需要两个条件同时成立
  ① 落点在某条区块边界列（x&15 或 z&15 为 0/15）——扫描器不排除边界，
     而且 L1 的第一排序键是"到 piece 中心的距离"，当 piece 中心在隔壁区块时，
     最近的列恰好就是边界列；
  ② 界外那个邻居当时未达 FULL（ChunkPyramid 的 FULL 步骤自身无 requirement，
     继承 LIGHT 的 addRequirement(INITIALIZE_LIGHT, 1)）。
ServerChunkCache.java:153-155 的 currentlyLoading 短路**只管事件自己那个区块**，
覆盖不到派生出的相邻坐标。

另外两条同级路径（同属 Level 级写入派生，修法一并覆盖）：
  - 形状级联会跨边界**写**：NeighborUpdater.java:37-38 → Block.updateOrDestroy
    → level.setBlock(邻居坐标, ...)。锚点是完整碰撞体（ModBlocks.java:16-22 设了
    noOcclusion() 但没有 noCollission()），旁边的栅栏/墙/铁栏杆/玻璃板会来连接它。
  - 旧方块的 onRemove 会触发邻居更新：LevelChunk.setBlockState:274 →
    BlockBehaviour#onRemove:193-197 → Level.removeBlockEntity:822-827 结尾调用
    updateNeighbourForOutputSignal → MultiNeighborUpdate.runNext:123 getBlockState(邻居)。

=====================================================================
三、修复方案（就这一个，不要改成"再加一个标志位"）
=====================================================================
核心原则：**不要用任何标志位去堵路径，而是把 Level 的写入 API 从作用域里移除。**

把 StructureWaypointPlacer.place() 里的
    level.setBlock(pos, state, Block.UPDATE_CLIENTS)
换成对**已经持有的 LevelChunk** 直接写入：
    chunk.setBlockState(pos, state, false)

这是引擎自己的世界生成写入路径：WorldGenRegion.java:275-284 用的就是
    chunkaccess.setBlockState(pos, state, false)   // flags 同样是 2 的语义

为什么这样是**结构性**安全而不是"更小心"：
  - LevelChunk#setBlockState 自己做区块写入、四个高度图、光照排队、以及通过
    addAndRegisterBlockEntity 创建并注册方块实体（LevelChunk.java:239-309）；
  - 但它**不做任何邻居通知** ⇒ 上面三条派生路径一条都不会发生；
  - 它内部唯一的 level 交互是同坐标的（onRemove/onPlace 传的是 this.level 但只针对
    本位置），没有跨区块查询。

具体改动清单：

(1) StructureWaypointPlacer.java:83 —— 唯一的死锁点
    删除 `if (!level.setBlock(pos, state, Block.UPDATE_CLIENTS)) { return false; }`
    替换为：
        BlockState previous = chunk.setBlockState(pos, state, false);
        if (previous == null) {
            return false;                       // 状态未变，按失败处理
        }
        level.onBlockStateChange(pos, previous, state);   // 镜像 Level#setBlock 唯一有价值的副作用
    注意：`onBlockStateChange` 是必须补的——Level#setBlock 会调它（Level.java:300），
    跳过它会让 POI/区块状态钩子行为不一致。它本身安全（PoiTypes 查表 +
    ServerLevel 内部用 getServer().execute 延后），可照抄 WorldGenRegion.java:283 的写法。

(2) 顺手把 place() 的签名收窄，让这个 bug 无法复发：
        由 `place(ServerLevel level, LevelChunk chunk, ...)`
        改为 `place(LevelChunk chunk, ...)`（如果 onBlockStateChange 需要 level，
         则改为只传 `ServerLevel` 用于这一个调用，但**不要再传 Level 类型的形参**，
         以免后续有人顺手写 level.setBlock）。
    同时同步改 StructureWaypointHandler.java:135 的调用点。
    这样 Level#setBlock / getBlockState / getChunkAt / getChunk / getFluidState /
    getBlockEntity 以及整个 StructureManager 接口，在这个包里**根本写不出来**。

(3) 修正 StructureWaypointPlacer.java:87-88 那条错误注释。
    现在写的是：
        // Safe to look up: the position was checked to hold no block entity before placement, so it
        // cannot be sitting in pendingBlockEntities and no lazy NBT deserialization can happen.
    这个理由是**错的**：pendingBlockEntities 是 LevelChunk 构造时从 ProtoChunk 灌进来的活映射
    （LevelChunk.java:133），并且只在 postProcessGeneration()（LevelChunk.java:555-559）才清空，
    而那是 ChunkMap.prepareTickingChunk（ChunkMap.java:704-705）在 FULL 之后才跑的
    ⇒ 事件时刻这个 map 里**可能仍有条目**。
    真实的理由是：setBlockState:286-296 刚刚创建并注册了 BE 放进 blockEntities，
    所以 :330 的映射查询先命中，不会走 pending 提升路径。
    请把注释改成这个真实理由。

(4) 顺带在写入前再断言一次前置条件（防御性，成本几乎为零）：
        BlockState existing = chunk.getBlockState(pos);
        if (existing.hasBlockEntity()
                || !(existing.isAir() || existing.canBeReplaced()
                     || existing.getFluidState().is(Fluids.WATER))) {
            return false;
        }

(5) structureId 的导入、以及 StructureWaypointDebug.placed(...) 的调用保持不变。

=====================================================================
四、必须接受的语义代价（写进提交说明）
=====================================================================
chunk.setBlockState 不做邻居通知，因此锚点旁边原有的栅栏/墙/玻璃板/铁栏杆
**不会来连接它**。对当前锚点方块完全无害：完整方块、无红石逻辑、无形状依赖，
且 SimpleWaterloggedBlock 的流体状态来自方块自身状态（WaypointBlock.java:75-77）。
**但如果将来给锚点加了方向性（facing）或连接性属性，必须重新评估这一条。**

=====================================================================
五、验证要求（重要：不能用 GameTest 的通过来判定）
=====================================================================
**先说清楚为什么 GameTest 抓不到这个 bug，不要在这上面浪费时间：**
  - GameTest 世界是超平坦且**无结构**的，处理器在第 4 道门（getAllStarts 为空）就退出；
  - GameTest 世界所有邻居都满加载，生产前置条件（边界列 + 界外邻居未达 FULL）**构造不出来**；
  - GameTest 的超时基于 tickCount（GameTestInfo.tickInternal），而主线程卡死时 tick 循环
    停止 ⇒ **超时永不触发**，runGameTestServer 是永久挂起而不是失败；
  - 现有的 placer_writesWaypointWithoutNeighbourUpdates / placer_setsWaterloggedInWater
    在 ChunkEvent.Load **之外**调用 place()，且只断言"放置成功"，对标志位/邻居/邻接性
    **零断言** —— 把实现改回 UPDATE_ALL 它们照样全绿，对这个问题**没有检测能力**。

**因此请用下面这个复现步骤做修复前后的对照（这是唯一有判别力的验证）：**

```
1. 编辑 run/config/teleportwaypoint/common.toml，设 debugMode = true
   （否则 placed/skippedByTag/noPlacement 这些日志都被开关挡掉，你会什么都看不到）
2. 启动专用服务器：  gradlew.bat runServer --offline --console=plain
3. 等 "Done (...s)! For help, type help"
4. RCON 或控制台依次执行：
       /locate structure minecraft:ancient_city
       /tp @s <那个坐标>
   （远古城市在白名单里。用旁观模式高速飞行进入新地形同样有效）
5. 观察：
   - 修复前：服务端线程会静默停止输出，spark 报 world statistics 超时，无 crash-report
   - 修复后：应看到 'structure waypoint: placed structure=minecraft:ancient_city pos=...'
     且服务端**持续正常输出**（自动存档日志继续出现、/spark tps 或 /list 有响应）
6. 把 debugMode 改回 false
```

**另外必须跑的：**
- `gradlew.bat build --offline --console=plain` 必须成功。
  （注意：本机沙箱可能不允许 gradle wrapper 写 ~/.gradle 下的 .lck 文件，
   若报 FileNotFoundException 权限拒绝，那是环境问题，请在允许写入的环境执行。）
- `gradlew.bat runGameTestServer --offline --console=plain` —— 20 个测试应仍全通过。
  **但这只证明你没弄坏别的东西，不证明死锁修好了**，不要把它当成交付依据。

**判定修复成功的唯一标准：** 上面第 5 步的复现场景下服务器不再冻结，
并且能稳定看到 `placed` 日志 + 服务端持续活跃。

=====================================================================
六、另外一个独立缺陷（请一并处理，但它不是死锁，可以分开提交）
=====================================================================
`util/Naming.key()`（Naming.java:42-43）**无条件**拼 `"tpwp." + id`，导致旧存档的锚点
名字全部损坏。已实测（比对语言文件）：

    legacy waypoint_id      Naming.key(id) 得到        语言文件里有吗
    end_city                tpwp.end_city               ❌ 没有
    ancient_city            tpwp.ancient_city           ❌ 没有
    ...（共 13 个旧 id 全部如此，只有 empty 正常）

而语言文件里保留的 14 个 `teleportwaypoint.waypoint.*` 旧键**没有任何代码路径能到达**，
成了死字符串；同时 `humanize()` 回退**从未被生产代码调用**（只有 GameTest 用）。
设计文档 §7.3 与验收表承诺的"旧锚点名字不变"在 0.4.0 里是**假的**。

修法：让 key() 做三级回退 ——
    ① 若 `tpwp.<id>` 在语言文件里存在则用它；
    ② 否则退回 `teleportwaypoint.waypoint.<id>`（激活那 14 个旧键）；
    ③ 都不行再用 `Component.translatableWithFallback(key, humanize(id))`。
另外 Xaero 那条路径（XaeroMinimapIntegration.java:317-319）设计上要求检查
`Language.getInstance().has(key)`，目前也没有实现。

=====================================================================
七、注意事项
=====================================================================
- **不要**用"给 UPDATE_CLIENTS 再加 UPDATE_KNOWN_SHAPE"来替代上面的修复。
  它能堵住路径 B，但堵不住第三节最后那两条同级路径（跨边界写、onRemove 邻居更新），
  而且它把"安全"重新变成需要人来推理的标志位问题。
- 不要动 StructureWaypointScanner.java：它只经 LevelChunk 读，结构上不可能跨区块
  （LevelChunk.getBlockState 用 x&15/z&15 取址，越界只会别名到本区块另一列，
   y 越界直接返回 AIR；它手里根本没有 Level）。它在这件事上没有问题。
- 不要动 StructureWaypointHandler 的触发门（isNewChunk）。那是**去重**门，不是**安全**门，
  与本次死锁无关。换触发时机不会降低任何风险。
- 已经有一份完整的事故分析写在文档里，遇到疑问请先读：
    docs/plans/2026-09-14-structure-waypoint-injection-design.md  §5.3.1（本次新增，含完整链条）
    docs/plans/2026-09-14-chunk-load-waypoint-injection-verification.md  §3.6（已更正）
    docs/plans/2026-09-14-chunk-load-waypoint-injection-implementation.md  E3a / F6 / A2 / T17
  以及 memory/learned-patterns.md 里的「【致命教训】Level.setBlock 有两条跨区块读取路径」一节。
  注意：这些文档刚被更正过，git 里是已修改未提交状态（4 个 docs/plans 文件），不要覆盖它们。
- 修完后请更新 docs/plans/2026-09-14-chunk-load-waypoint-injection-verification.md：
  把 §3.6 的状态从"待修"改为"已修（方案 B：chunk.setBlockState）"，并附上你的复现证据。
- 提交信息建议：
    fix: 放置改用 chunk.setBlockState，消除 Level#setBlock 派生的跨区块邻居通知（修复主线程自死锁）
```

---

## 附：本次修复的最小 diff 预览（供你快速核对，不是让你照抄）

```diff
--- a/src/main/java/com/zonlong/teleportwaypoint/structure/StructureWaypointPlacer.java
+++ b/src/main/java/com/zonlong/teleportwaypoint/structure/StructureWaypointPlacer.java
@@
-        // UPDATE_CLIENTS only -- deliberately NOT Block.UPDATE_ALL.
-        //
-        // UPDATE_ALL includes UPDATE_NEIGHBORS, which makes the engine walk the six neighbours of this
-        // position and read each of their block states through Level#getBlockState. ...（长注释）
-        if (!level.setBlock(pos, state, Block.UPDATE_CLIENTS)) {
+        // Write straight into the chunk we already hold. Never through Level#setBlock: it derives
+        // neighbour notifications, and the SHAPE cascade (gated on flags & 16, not flags & 1) reads and
+        // writes adjacent chunks under every update flag, which self-deadlocks the main thread inside
+        // ChunkEvent.Load. LevelChunk#setBlockState does the section write, heightmaps, light queueing
+        // and block entity registration itself, and derives no neighbour work at all.
+        BlockState previous = chunk.setBlockState(pos, state, false);
+        if (previous == null) {
             return false;
         }
+        level.onBlockStateChange(pos, previous, state);
@@
-        // Safe to look up: the position was checked to hold no block entity before placement, so it
-        // cannot be sitting in pendingBlockEntities and no lazy NBT deserialization can happen.
+        // Safe to look up: setBlockState just created and registered the block entity, so the lookup
+        // hits blockEntities before it can reach the pending-NBT promotion path.
         if (!(chunk.getBlockEntity(pos) instanceof WaypointBlockEntity waypoint)) {
```

**改动的判断依据（一句话）：** 修完之后，这份事故分析里关于「两条路径 / `flags & -34` / 第 16 位 / 边界列触发条件」的**全部内容都会失效** —— 因为 `Level` 的写入 API 已经不在作用域内。这就是「结构性修复」与「再加一个标志位」的区别。
