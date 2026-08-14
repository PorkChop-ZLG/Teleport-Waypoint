# 传送锚点（Teleport Waypoint）技术设计文档

**日期：** 2026-08-14
**状态：** 已确认（Approved）
**方案：** A —— NeoForge 原生自建架构（无 Balm / Waystones 前置依赖）

---

## 1. 问题陈述（Problem Statement）

为 Minecraft 1.21.1 NeoForge 提供一个"传送锚点"模组：在世界中放置可互传的传送点，支持在特定结构里预置**带固定名字**的传送锚点，并与 Xaero 世界地图联动。

核心要解决的既有痛点：Waystones 把石碑名字存在存档级 `SavedData`（以 UUID 为键），方块实体只存 UUID，导致**结构模板 NBT 里预置的名字在新世界生成时丢失**。本模组通过"名字直接存进方块实体 NBT"来根治该问题。

## 2. 目标与非目标（Goals & Non-Goals）

### 目标
1. 提供两种独立方块：**传送锚点（waypoint）** 与 **口袋锚点（pocket_waypoint）**。
2. 传送锚点：翻译键名字、不可破坏、仅创造模式可改 `id`。
3. 口袋锚点：直显名字（支持中文）、可破坏、可编辑名字（仅所有者）。
4. 名字存于方块实体 NBT，结构模板可完整携带名字。
5. per-player 独立解锁状态，存于服务器管理的独立存档文件。
6. 解锁后可在 GUI 中选择传送目标；免费、即时、跨维度、无冷却。
7. 红/蓝外观按当前玩家解锁状态区分（BER 实现，模型到位后落地）。

### 非目标（本期不做）
- Xaero 地图联动（世界地图 / 小地图）——延后到模组本体完成后，作为可选前置。
- 方块模型 / 贴图——由用户自行提供，本期以占位方块实现。
- 经验/物品消耗、传送冷却、权限系统。
- Waystones 的可见性、分组、分享石、传送板等附加功能。
- Balm / Waystones 作为前置依赖（仅参考其代码）。

## 3. 架构（Architecture）

采用**原生 NeoForge 自建**：方块实体承载名字，独立 `SavedData` 承载全局索引与 per-player 激活，原生 Payload API 做网络。

### 模块划分（包结构）

```
com.zonlong.teleportwaypoint
├── TeleportWaypoint.java            # 主类（@Mod 入口，注册）
├── Config.java                      # 模组配置（替换模板示例项）
├── block/
│   ├── WaypointBlock.java           # 传送锚点：不可破坏，翻译键名字
│   ├── PocketWaypointBlock.java     # 口袋锚点：可破坏，直显名字，绑定所有者
│   ├── ModBlocks.java               # 方块 DeferredRegister
│   └── entity/
│       ├── WaypointBlockEntity.java # 方块实体：uid + (id | name)，save/load
│       └── ModBlockEntities.java    # 方块实体注册
├── item/
│   └── ModItems.java                # 物品注册（口袋锚点 BlockItem）
├── core/
│   ├── WaypointRecord.java          # 轻量记录：uid, dimension, pos, type
│   ├── WaypointRegistryData.java    # SavedData：全局索引 uid -> WaypointRecord
│   ├── PlayerWaypointData.java      # SavedData：player UUID -> Set<waypoint uid>
│   ├── WaypointManager.java         # 门面：读写数据、激活/传送逻辑
│   └── WaypointTeleporter.java      # 传送落点解析 + 执行（参考 Waystones）
├── network/
│   ├── ModNetwork.java              # Payload 注册
│   ├── SyncActivatedWaypointsPayload.java  # S->C：推送已解锁 uid 集合
│   ├── TeleportRequestPayload.java  # C->S：请求传送
│   └── RenameWaypointPayload.java   # C->S：改名（口袋锚点 name / 传送锚点 id）
├── menu/
│   ├── WaypointMenu.java            # 传送锚点 GUI 菜单
│   └── PocketWaypointMenu.java      # 口袋锚点 GUI 菜单
└── client/
    ├── TeleportWaypointClient.java  # 客户端入口
    ├── render/
    │   └── WaypointRenderer.java    # BER：按当前玩家解锁状态渲染红/蓝（模型到位后）
    └── gui/
        ├── WaypointScreen.java      # 传送锚点界面
        └── PocketWaypointScreen.java# 口袋锚点界面
```

### 关键决策
1. **名字绑方块**：`WaypointBlockEntity` 的 `saveAdditional()/loadAdditional()` 直接写名字字段，结构 NBT 原样携带。
2. **两个独立 `SavedData`**：`WaypointRegistryData`（全局索引）+ `PlayerWaypointData`（per-player 激活），服务器权威。
3. **原生 Payload API**，不引入 Balm。
4. **uid 实例唯一、自动生成**：结构模板只写 `id`，方块实体首次加载时生成 uid。

## 4. 组件（Components）

### 4.1 方块（Block）

| 方块 | 注册 id | 特性 |
|---|---|---|
| 传送锚点 | `teleportwaypoint:waypoint` | 不可破坏（注册期 `strength(-1)`）；翻译键名字；仅创造可改 id |
| 口袋锚点 | `teleportwaypoint:pocket_waypoint` | 正常硬度，可破坏掉落自身；直显名字；绑定所有者 |

- 传送锚点不可破坏：`BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F)`（等价基岩硬度）。创造模式仍可破坏（管理行为，符合需求）。
- 两者均为 `BaseEntityBlock`，共享 `WaypointBlockEntity`。
- 交互统一在 `use()`：服务端判断"是否已解锁"→ 未解锁则激活；已解锁则 `openMenu`。

### 4.2 方块实体（WaypointBlockEntity）

NBT 字段：

**传送锚点**
```
{ uid: UUID, id: "village" }
```
- `id`：字符串，仅创造模式可改，校验 `[a-z0-9]+`。
- 完整翻译键 = `"teleportwaypoint.waypoint." + id`。

**口袋锚点**
```
{ uid: UUID, name: "我的家", owner: UUID }
```
- `name`：字符串，直显名，支持中文，仅所有者可编辑。
- `owner`：UUID，放置时记录放置者；用于改名权限校验。

- `uid`：UUID，实例唯一，`onLoad()` 时若无则自动生成并 `setChanged()` 落盘；GUI 只读显示。
- `getDisplayName()`：传送锚点 → `Component.translatable(...)`；口袋锚点 → `Component.literal(...)`。
- 通过方块类型判断读 `id` 还是 `name`。

### 4.3 数据存储（SavedData）

#### WaypointRegistryData（全局索引）
- 文件名：`data/teleportwaypoint_waypoints.dat`
- 结构：`Map<UUID, WaypointRecord>`，`WaypointRecord(uid, dimension, pos, type)`。
- 方块实体加载/位置变化时注册，破坏时移除。
- 用途：跨维度传送目标解析；为 Xaero 联动提供"遍历所有已加载锚点"。

#### PlayerWaypointData（per-player 激活）
- 文件名：`data/teleportwaypoint_players.dat`
- 结构：`Map<UUID /*player*/, Set<UUID> /*waypoint uid*/>`。
- 服务器权威；客户端仅持有"自己已解锁 uid 集合"副本用于渲染与 GUI。

### 4.4 网络（Network，原生 Payload）

| Payload | 方向 | 内容 | 用途 |
|---|---|---|---|
| `SyncActivatedWaypointsPayload` | S→C | `List<UUID>` | 推送该玩家已解锁 uid 集合 |
| `TeleportRequestPayload` | C→S | `sourceUid, targetUid` | 请求传送 |
| `RenameWaypointPayload` | C→S | `BlockPos, newText` | 口袋锚点改名 / 传送锚点改 id（服务端校验） |

### 4.5 GUI（Menu + Screen）

- **传送锚点 GUI**：传送目标列表 + uid（只读显示）+ `id` 输入框（仅创造模式可编辑）。
- **口袋锚点 GUI**：传送目标列表（所有玩家可打开）+ `name` 输入框（仅所有者可编辑）。
- 传送列表仅展示当前玩家**已解锁**的锚点（排除自身）。

## 5. 数据模型（Data Model）

### 方块实体 NBT 对照

| 字段 | 传送锚点 | 口袋锚点 | 说明 |
|---|---|---|---|
| `uid` | ✔ | ✔ | UUID，自动生成，只读 |
| `id` | ✔ | ✘ | 翻译键后缀，`[a-z0-9]+`，仅创造可改 |
| `name` | ✘ | ✔ | 直显名，支持中文，仅所有者可改 |
| `owner` | ✘ | ✔ | 所有者 UUID，放置时记录 |
| `localized` | ✘ | ✘ | 不需要（类型已决定名字形式） |
| `locked` | ✘ | ✘ | 不需要（类型已决定锁定行为） |
| `origin` | ✘ | ✘ | 不需要 |

### 结构模板写入（示例）

传送锚点在结构 `.nbt` 中：
```
{ id: "village" }
```
加载时自动补 `uid`。口袋锚点一般不入结构（如需，写 `{ name: "..." }`）。

## 6. 数据流（Data Flow）

### 6.1 结构生成 / 放置
1. 结构放置传送锚点，方块实体 `loadAdditional` 读到 `id`（名字随结构保留）。
2. `onLoad()` 若无 `uid` → 生成新 uid 并 `setChanged()`。
3. 向 `WaypointRegistryData` 注册 `uid -> (dimension, pos, waypoint)`。
4. 口袋锚点被玩家放置时记录 `owner`。

### 6.2 解锁（激活）
1. 玩家右键未解锁锚点 → 服务端 `use()` 判断未解锁。
2. `PlayerWaypointData` 记录 `player -> uid`。
3. 服务端 → 客户端 `SyncActivatedWaypointsPayload`。
4. 客户端更新本地激活集合 → 该锚点外观变蓝（BER 按玩家渲染）。

### 6.3 传送
1. 玩家右键已解锁锚点 → 打开 GUI，选目标。
2. 客户端发送 `TeleportRequestPayload(sourceUid, targetUid)`。
3. 服务端校验：玩家已解锁 `targetUid`，且目标在 `WaypointRegistryData` 存在、目标方块实体仍有效。
4. 校验通过 → `WaypointTeleporter` 解析落点并 `teleportTo(...)` 跨维度传送。
5. 失败则返回错误提示。

### 6.4 改名
1. GUI 编辑后发送 `RenameWaypointPayload`。
2. 服务端校验：
   - 口袋锚点 → 仅 `owner` 可改 `name`；
   - 传送锚点 → 仅创造模式可改 `id`，且校验 `[a-z0-9]+`。
3. 更新方块实体 NBT 并同步。

### 6.5 传送落点（参考 Waystones `resolveDefaultDestination`）
1. 取目标锚点 `pos`，读 `FACING`（无则 NORTH）作首选方向。
2. 依次尝试 `[FACING, EAST, WEST, SOUTH, NORTH]`，选第一个"该格及其上方都不 suffocating（可站立）"的方向。
3. 落点 = `pos.relative(dir)` 中心 `(x+0.5, y+0.5, z+0.5)`；朝向 = 该方向（面向锚点）。
4. 无可站立方向则退化为锚点本身中心。
5. 执行 `ServerPlayer.teleportTo(targetLevel, x, y, z, Set.of(), yaw, pitch)`。

## 7. 错误处理（Error Handling）

| 场景 | 处理 |
|---|---|
| 传送目标已移除 / 方块实体不存在 | 提示"目标不存在"，从激活集合清理 |
| 目标维度不存在 | 提示错误，取消传送 |
| 改名 id 非法（非 `[a-z0-9]+`） | 拒绝并提示 |
| 非创造模式改传送锚点 id | 拒绝并提示 |
| 非所有者改口袋锚点 name | 拒绝并提示 |
| 客户端伪造传送请求 | 服务端校验"已解锁"与目标有效性 |

## 8. 测试策略（Testing Strategy）

- **单元测试**：翻译键拼接、`id` 校验正则、SavedData 序列化往返。
- **GameTest**（NeoForge `gameTestServer` 已配置）：放置锚点 → 解锁 → 传送；结构 NBT 名字保留。
- **手工测试**：结构生成后名字保留；红/蓝外观按玩家区分；跨维度传送；口袋锚点破坏掉落；改名权限（所有者 vs 非所有者）。

## 9. 决策记录（Decisions Made）

- **方案 A（原生自建）**：不引入 Balm/Waystones 前置，原生 Payload + SavedData。
- **两种独立方块**：传送锚点（翻译键、锁定、创造改 id）/ 口袋锚点（直显名、可破坏、仅所有者改名）。
- **名字绑方块实体 NBT**：根治结构生成后无名问题。
- **uid 实例唯一、自动生成**：结构模板只写 `id`；每实例独立解锁。
- **per-player 激活独立文件**：`PlayerWaypointData` SavedData，服务器权威。
- **传送免费即时跨维度无冷却**。
- **不可破坏**：传送锚点注册期 `strength(-1)`；创造仍可破坏。
- **红/蓝 per-player 外观**：BER 渲染，查当前玩家解锁状态（参考 Waystones `WaystoneRenderer`）；模型到位后落地。
- **传送落点**：参考 Waystones `resolveDefaultDestination`，站在锚点相邻一格、面向锚点。
- **模型/贴图用户自供，本期占位**。

## 10. 后续步骤（Next Steps）

1. 通过 `planning` 技能生成详细实施计划（任务分解 + 验收标准）。
2. 清理模板示例代码，按计划实施。
3. 模组本体完成后，进入 Xaero 地图联动阶段。
