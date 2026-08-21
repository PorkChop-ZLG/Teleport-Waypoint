# 代码审查问题修复实施计划

**Goal:** 修复 H-1、M-2、M-3、L-2、L-3、L-4 六项代码审查问题。
**Architecture:** 保持现有架构，逐项最小修复。
**Approach:** Approach A — 逐项最小修复，不新增测试框架、不做协议大重构。

---

### Task 1: H-1 激活元数据快照分页

**Files:**
- Modify: `src/main/java/com/zonlong/teleportwaypoint/network/SyncActivatedWaypointsPayload.java`
- Modify: `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java`
- Modify: `src/main/java/com/zonlong/teleportwaypoint/client/ClientWaypointState.java`
- Modify: `src/main/java/com/zonlong/teleportwaypoint/network/ModNetwork.java`

**Steps:**
1. `SyncActivatedWaypointsPayload` 改为 `record(List<ActivatedWaypointInfo> waypoints, int page, boolean done)`。
2. 将 `MAX_ACTIVATED_SYNC = 100_000` 改为 `MAX_PAGE_SIZE = 500`，codec 集合上限使用 `MAX_PAGE_SIZE`，并新增 `ByteBufCodecs.VAR_INT` 与 `ByteBufCodecs.BOOL` 编解码 `page`/`done`。
3. `WaypointManager.syncTo()` 仿照 `syncDimensionTo()` 按 `MAX_PAGE_SIZE` 分页发送。
4. `ClientWaypointState` 新增激活快照累积状态：
   - `private static List<ActivatedWaypointInfo> activatedSnapshot = List.of();`
   - `private static boolean activatedSnapshotInProgress;`
   - `private static final List<Runnable> pendingActivated = new ArrayList<>();`
5. 新增 `applyActivatedSnapshot(List<ActivatedWaypointInfo> infos, int page, boolean done)`：
   - `page == 0` 时重置临时快照并置 `activatedSnapshotInProgress = true`；
   - 累积每页条目；
   - `done == true` 时发布到 `activated` / `activatedUids`、清空临时状态、`revision++`、冲刷 `pendingActivated`。
6. 修改 `applyActivatedAdd` / `applyActivatedRemove`：当 `activatedSnapshotInProgress` 为 true 时，将操作加入 `pendingActivated` 而不是直接修改 `activated`。
7. `ModNetwork.handleSyncActivated()` 改为调用 `applyActivatedSnapshot(payload.waypoints(), payload.page(), payload.done())`。
8. 若 `setActivated()` 无其他调用点，删除该方法。
9. `ClientWaypointState.reset()` 中清空 `activatedSnapshot`、`activatedSnapshotInProgress`、`pendingActivated`。

**Verification:**
- `./gradlew.bat compileJava --offline --console=plain` 成功。
- 手动：激活 500+ 锚点后登录/改名，确认列表完整、不断线。

---

### Task 2: M-2 存档读取逐条容错

**Files:**
- Modify: `src/main/java/com/zonlong/teleportwaypoint/core/PlayerWaypointData.java`

**Steps:**
1. 重构 `read()`：外层 `try` 只负责 `UUID.fromString(key)`。
2. 内层对每个 `NbtUtils.loadUUID(entry)` 单独 `try/catch`，坏 UUID 跳过该条并记录 warn。
3. 只要有跳过，记录 `dirty = true`。
4. 若最终集合为空，不写入 `activated`，并标记 `dirty = true`（与 L-2 配合清理历史空记录）。
5. 循环结束后若 `dirty`，调用 `data.setDirty()`。

**Verification:**
- `./gradlew.bat compileJava --offline --console=plain` 成功。
- 手动：构造含一个合法 UUID 和一个损坏 `TAG_INT_ARRAY` 的玩家列表，加载后确认合法记录保留、损坏条目被跳过，保存后存档被清理。

---

### Task 3: M-3 传送列表响应增量更新

**Files:**
- Modify: `src/main/java/com/zonlong/teleportwaypoint/client/gui/WaypointListScreen.java`

**Steps:**
1. 增加字段 `private int lastRevision = -1;`。
2. 在 `init()` 末尾设置 `lastRevision = ClientWaypointState.getRevision();`。
3. 重写 `tick()`：
   - 若 `ClientWaypointState.getRevision() != lastRevision`，更新 `lastRevision` 并调用 `updateList()`。
4. 不额外保存滚动位置；`updateList()` 继续基于 `searchText` / `sortByName` 重建列表。

**Verification:**
- `./gradlew.bat compileJava --offline --console=plain` 成功。
- 手动：打开传送列表不关闭，由另一名玩家改名/删除锚点，确认列表自动刷新且搜索/排序保留。

---

### Task 4: L-2 空玩家记录清理

**Files:**
- Modify: `src/main/java/com/zonlong/teleportwaypoint/core/PlayerWaypointData.java`

**Steps:**
1. `deactivate()` 中 `set.remove(waypoint)` 成功后，若 `set.isEmpty()`，调用 `activated.remove(player)`。
2. `read()` 中解析出的集合为空时不写入 `activated`，并标记 `dirty = true`。
3. 确保 `setDirty()` 在有空记录被清理时被调用。

**Verification:**
- `./gradlew.bat compileJava --offline --console=plain` 成功。
- 手动：取消玩家最后一个激活记录后，检查内存和保存后的存档中不再出现空玩家条目；加载含历史空列表的存档后确认被清理。

---

### Task 5: L-3 README 描述统一

**Files:**
- Modify: `README.md`

**Steps:**
1. 将第 55 行改为：
   > 右键点击未激活的锚点 → 自动激活（提示 + 音效），不打开传送列表；再次右键已激活的锚点才打开传送列表。
2. 检查第 31 行与修改后第 55 行语义一致。

**Verification:**
- 阅读 `README.md` 第 28–60 行确认无矛盾。

---

### Task 6: L-4 Xaero 弃用 API

**Files:**
- Modify: `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`

**Steps:**
1. 在 `addOrUpdate()` 中，将 `existing.getSymbol()` 替换为 `existing.getInitials()`。
2. 不修改 `neoforge.mods.toml` 中的 Xaero 版本范围，不增加反射降级。

**Verification:**
- `./gradlew.bat build --offline --console=plain` 成功。
- 检查 `build/reports/problems/problems-report.html` 中不再出现 `XaeroMinimapIntegration.java使用或覆盖了已过时的 API`。
- 手动：安装 Xaero Minimap，确认小地图锚点显示正常。

---

## 执行顺序

1. Task 1（H-1，改动最大）
2. Task 2（M-2）
3. Task 4（L-2，与 Task 2 同文件，可合并提交）
4. Task 3（M-3）
5. Task 5（L-3）
6. Task 6（L-4）

## 验证汇总

- 每个 Task 后运行 `./gradlew.bat compileJava --offline --console=plain`。
- 全部完成后运行 `./gradlew.bat build --offline --console=plain`。
- 按设计文档中的手动验证清单逐项确认。
