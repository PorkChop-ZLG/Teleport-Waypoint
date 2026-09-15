# Xaero 联动修复实施计划（方案 B）

**日期：** 2026-08-18
**状态：** Ready for execution
**依据：** `docs/plans/2026-08-18-xaero-fix-full-design.md`
**范围：** 审查报告全部 8 项（C1、I1-I5、S1-S2）

---

## Phase 0：源码准备与熟悉

**目标：** 确认参考源码可用，阅读关键 Xaero API，避免实施时踩坑。

### Task 0.1 确认源码位置
- [x] Xaero Minimap：`D:\Minecraft\Teleport-Waypoint-ds_pro\build\decompiled\xaero-minimap`
- [x] Xaero World Map：`D:\Minecraft\Teleport-Waypoint-ds_pro\build\decompiled\xaero-worldmap`
- [x] Xaero Lib：`D:\Minecraft\Teleport-Waypoint-ds_pro\build\decompiled\xaerolib`
- [ ] Minecraft/NeoForge 源码参考：`D:\Minecraft\Teleport-Waypoint\build\neoforge-src-ref` 或 `build/neoForm/.../unzipSources`

### Task 0.2 阅读关键 Xaero 源码
- [ ] `xaero/hud/minimap/world/MinimapWorldManager.java`：确认 `getCustomWaypoints(ResourceLocation)` 语义（key 是 modId，不是维度）
- [ ] `xaero/common/minimap/waypoints/Waypoint.java`：确认字段与构造器，用于归属校验
- [ ] `xaero/lib/common/config/option/ConfigOptionManager.java`：确认 Xaero 配置注册/读取 API
- [ ] `xaero/map/WorldMap.java`（Worldmap）：确认配置读取路径

**验收：** 能回答“I4 应该用哪个 key、如何校验归属”和“I1 如何镜像配置”。

---

## Phase 1：C1 + 网络增量同步（I2/C1）

**目标：** 将全量广播重构为“登录快照 + 运行期增量”，并修复幽灵锚点。

### Task 1.1 新增/改造网络 Payload
文件：
- `src/main/java/com/zonlong/teleportwaypoint/network/SyncAllWaypointsPayload.java`（改为快照或新增 `WaypointSnapshotPayload`）
- 新增 `AddWaypointPayload.java`
- 新增 `UpdateWaypointPayload.java`
- 新增 `RemoveWaypointPayload.java`
- 新增 `ActivatedWaypointAddPayload.java`
- 新增 `ActivatedWaypointRemovePayload.java`

要求：
- 每个 Payload 使用 `StreamCodec`，字段包含 UID、必要记录数据、`page`/`done`（快照用）。
- 解码端对集合使用 `ByteBufCodecs.collection(..., MAX_PAGE_SIZE)` 上限。

### Task 1.2 注册新 Payload
文件：`src/main/java/com/zonlong/teleportwaypoint/network/ModNetwork.java`
- 注册所有新 `playToClient` 消息。
- 保留 `SyncActivatedWaypointsPayload` 作为登录激活快照。

### Task 1.3 客户端状态支持增量
文件：`src/main/java/com/zonlong/teleportwaypoint/client/ClientWaypointState.java`
- 增加 `applyAdd`、`applyUpdate`、`applyRemove`、`applyActivatedAdd`、`applyActivatedRemove`。
- 增加“未初始化”状态与待处理增量队列；快照完成后按序应用。
- 每次变更 `revision++`。

### Task 1.4 服务端同步逻辑改造
文件：`src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java`
- `register()`：区分“新记录”与“已存在”，新记录发送 `AddWaypointPayload`；自动激活时向放置玩家发 `ActivatedWaypointAddPayload`。
- 改名/更新路径：发送 `UpdateWaypointPayload`。
- `unregister()`：单次删除注册表记录，成功后发送 `RemoveWaypointPayload` 给所有在线玩家，并向受影响玩家发送 `ActivatedWaypointRemovePayload`。
- `removeWaypoint()`：重构为“删除 + 广播”单一职责，避免二次删除导致不广播。
- `syncAllTo()`：改为分页快照（每页 500 条，`done` 标记）。
- 删除或改造 `broadcastAll()`：不再用于运行期全量广播；仅保留给快照/调试。

### Task 1.5 登录同步
文件：`src/main/java/com/zonlong/teleportwaypoint/WaypointEvents.java`
- 登录仍调用 `syncAllTo(player)` + `syncTo(player)`，但底层已是快照 + 激活快照。

**验收：**
- 放置/改名/破坏锚点时，不再发送全量 `SyncAllWaypointsPayload` 广播。
- 破坏锚点后所有在线客户端地图/列表不再显示幽灵锚点。
- 登录时能收到完整快照。

---

## Phase 2：配置统一（I1）

**目标：** `Config` 为唯一权威，Xaero 配置仅作镜像。

### Task 2.1 修改 World Map 集成
文件：`src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroWorldMapIntegration.java`
- `showWaypoints()` / `showWaypointNames()` 一律读 `Config`。
- 保留或移除 Xaero 配置注册；若保留，则在 `tick()` 中将 `Config` 值单向同步到 Xaero 配置。
- 如需双向：检测 Xaero 配置变化后写回 `Config`，但最终显示仍以 `Config` 为准。

### Task 2.2 修改统一入口
文件：`src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroIntegration.java`
- `showWaypoints()` 不再优先读 World Map 配置，统一走 `Config`（或镜像同步后的同一值）。
- `shouldShow()` / `getDisplayRange()` 保持签名，内部只读 `Config`。

**验收：**
- 在模组配置界面关闭 `showWaypoints` 后，World Map 与小地图标记都消失。
- Xaero UI 中的开关不会遮蔽模组配置。

---

## Phase 3：传送限频（I3）

**目标：** 防止地图传送被滥用。

### Task 3.1 新增限频器
文件：新增 `src/main/java/com/zonlong/teleportwaypoint/core/TeleportRateLimiter.java`
- 每玩家冷却，默认 1000ms。
- 提供 `tryAcquire(UUID playerId)` 与 `remove(UUID playerId)`。
- 冷却时间可从 `Config` 读取（新增配置项）。

### Task 3.2 接入传送入口
文件：`src/main/java/com/zonlong/teleportwaypoint/network/ModNetwork.java`、`core/WaypointTeleporter.java`
- `handleTeleportRequest` 与 `handleMapTeleportRequest` 都先检查限频。
- 被限频时返回冷却提示（新增翻译键 `chat.teleportwaypoint.teleport_cooldown`），提示频率需限制，避免刷屏。

### Task 3.3 清理玩家状态
文件：`src/main/java/com/zonlong/teleportwaypoint/WaypointEvents.java` 或新增事件监听
- 玩家登出时调用 `TeleportRateLimiter.remove(playerUUID)`。

**验收：**
- 连续地图传送第 1 次成功，后续被拦截。
- 普通 GUI 传送与地图传送共用冷却。

---

## Phase 4：小地图 ID 冲突修复（I4）

**目标：** 消除与其他 Xaero custom waypoint 集成的 ID 冲突与误删。

### Task 4.1 使用模组专属 key
文件：`src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`
- 将 `manager.getCustomWaypoints(dimension)` 改为 `manager.getCustomWaypoints(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "minimap"))`。
- 根据反编译源码确认是否需要按维度区分；若需要，在 key 中追加维度或通过 Waypoint 坐标区分。

### Task 4.2 改为稳定哈希 ID + 冲突探测
- ID 生成：`Math.floorMod(uid.hashCode(), 2_000_000_000) + 1`。
- 写入前检查 `map.get(id)`：
  - 空 → 使用；
  - 属于本模组（`ID_TO_UID` 命中）→ 使用；
  - 被其他占用 → 线性探测下一个空位。
- 维护 `UID_TO_ID` / `ID_TO_UID` 映射。

### Task 4.3 删除前归属校验
- 删除时只删除 `ID_TO_UID` 中存在、且 `map.get(id)` 的坐标/名称/颜色/符号与本地记录一致的条目。
- 不一致时视为被其他集成覆盖，不删除。

**验收：**
- 与另一个使用 Xaero custom waypoint 的 mod 同装时，不覆盖对方标记。
- 删除本模组标记时不影响对方标记。

---

## Phase 5：默认值与文档（I5/S1/S2）

### Task 5.1 统一默认范围
文件：`src/main/java/com/zonlong/teleportwaypoint/Config.java`
- `WAYPOINT_RANGE` 与 `POCKET_WAYPOINT_RANGE` 默认值改为 `128`。

### Task 5.2 收窄 Xaero 依赖范围
文件：`src/main/templates/META-INF/neoforge.mods.toml`
- `xaerominimap`：`versionRange="[25.3.5,)"`
- `xaeroworldmap`：`versionRange="[1.40.6,)"`
（如实施时确认有更精确兼容范围，可再调整。）

### Task 5.3 更新 README
文件：`README.md`
- 删除“NeoForge 配置界面入口（暂无实际可调选项）”等过期描述。
- 将 Xaero 联动从“后续计划”改为“已实现”。
- 同步默认范围描述为 128。

### Task 5.4 更新设计文档
文件：`docs/plans/2026-08-17-xaero-config-filter-design.md`
- 若默认值描述仍为 128，则无需改；若不一致，统一为 128。

**验收：**
- 代码、README、设计文档三处默认值一致。
- `neoforge.mods.toml` 依赖范围收窄。

---

## Phase 6：构建与人工验证

### Task 6.1 构建
```
gradlew.bat build --offline --rerun-tasks --console=plain
gradlew.bat prepareClientRun --offline
```

### Task 6.2 人工游戏内验证清单
- [ ] C1：双客户端在线，B 破坏锚点，A/B 不再显示幽灵锚点。
- [ ] I2：放置/改名/破坏锚点只产生增量包，不产生全量广播。
- [ ] I1：模组配置关闭 `showWaypoints` 后，World Map / Minimap 标记消失。
- [ ] I3：连续地图传送第 1 次成功，后续被冷却拦截。
- [ ] I4：与其他 Xaero custom waypoint 集成同装，不覆盖/误删对方标记。
- [ ] 登录：登录后全量快照 + 激活快照完整。
- [ ] 回归：两种锚点放置、改名、激活、传送、删除激活、破坏方块均正常。

### Task 6.3 修复发现的问题
- 如构建或人工验证发现问题，记录并修复，重复验证。

---

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| 增量同步顺序/幂等 | 客户端 apply 幂等 + 未初始化暂存队列 |
| Xaero custom waypoint 跨维度语义不明 | 实施时以反编译源码为准，必要时保留按维度分 key |
| 配置双向同步复杂 | 先做 Config → Xaero 单向镜像，双向作为可选项 |
| 网络协议改动导致旧客户端不兼容 | 本 mod 为整体更新，协议版本号 `registrar("1")` 可升级为 `"2"` |
| 限频误伤正常玩家 | 默认 1000ms，可在 Config 调整 |
