# 传送锚点 GUI 优化设计文档

**日期：** 2026-08-14
**状态：** 待批准（Pending Approval）
**参考：** Waystones `WaystoneEditScreen` / `WaystoneSelectionScreenBase` / `WaystoneBlockBase.setPlacedBy`

---

## 1. 问题陈述（Problem Statement）

现有 GUI 只有一个简陋的单界面（传送列表 + 改名混在一起），体验差。需参考 Waystones 重构为**三个独立界面**：命名传送锚点、命名口袋锚点、共享的传送列表，并支持放置自动交互、界面跳转、搜索与滚轮翻页。

## 2. 目标与非目标

### 目标
1. 放置锚点时自动与之交互（参考 Waystones `setPlacedBy`）。
2. 三个界面：**命名传送锚点**、**命名口袋锚点**、**传送列表**（共享）。
3. 按锚点名字是否为空，决定打开「命名」还是「传送列表」。
4. 命名界面保存后跳转到「传送列表」。
5. 传送列表第二行锚点名字可点击跳转到命名界面。
6. 传送列表带搜索栏与右侧滚轮翻页列表。
7. 所有界面文本走翻译键，禁止硬编码。

### 非目标
- 排序、分组、可见性、删除/管理等功能（Waystones 的附加功能）。
- 模型/贴图（仍由用户自供）。
- Xaero 联动。

## 3. 架构

三个 `MenuType` + 三个 `Screen`，跳转统一走**服务端 openMenu**（客户端发请求 payload，服务端校验权限后 `openMenu`）。

```
menu/
├── RenameWaypointMenu.java        # 命名传送锚点（持有 BlockPos）
├── RenamePocketWaypointMenu.java  # 命名口袋锚点（持有 BlockPos）
└── WaypointListMenu.java          # 传送列表（共享，持有 BlockPos）
client/gui/
├── RenameWaypointScreen.java      # 命名传送锚点界面
├── RenamePocketWaypointScreen.java# 命名口袋锚点界面
└── WaypointListScreen.java        # 传送列表界面
```

- 删除旧的 `WaypointScreen` / `PocketWaypointScreen`（被三个新界面取代）。
- `WaypointBlockEntity.openMenu()` 改为按「名字是否为空」选择打开命名或列表界面。

## 4. 界面布局

### 4.1 命名传送锚点 / 命名口袋锚点（结构相同）
```
┌─────────────────────────────┐
│      命名传送锚点 / 命名口袋锚点   │ ← 标题（翻译键）
│  ┌───────────────────────┐  │
│  │  id / name 输入框      │  │ ← EditBox，仅创造/所有者可编辑
│  └───────────────────────┘  │
│          [ 保存 ]           │ ← 保存按钮
└─────────────────────────────┘
```
- 输入框不可编辑时（无权限），按钮文案改为「关闭」。
- 保存 → 发送改名 payload；服务端改名后 `openMenu` 传送列表。

### 4.2 传送列表
```
┌───────────────────────────────────┐
│             传送列表               │ ← 第 1 行：标题（翻译键）
│         [当前锚点名字] ✎           │ ← 第 2 行：可点击，跳命名界面
│  ┌───────────────────────────┐    │
│  │ 搜索框（搜索锚点名字）      │    │ ← EditBox 实时过滤
│  └───────────────────────────┘    │
│  ┌───────────────────────────┐ ┌┐ │
│  │ 锚点 A（已解锁）            │ │ │ │ ← 滚动列表，点击传送
│  │ 锚点 B（已解锁）            │ │ │ │    （ContainerObjectSelectionList，
│  │ ...                       │ ││ │      自带右侧滚动条 + 滚轮）
│  └───────────────────────────┘ └┘ │
└───────────────────────────────────┘
```
- 第 2 行锚点名字：hover 显示下划线/图标，点击 → 发「打开命名界面」请求。
- 列表项 = 当前玩家已解锁的锚点（排除自身），点击 → 发传送请求。

## 5. 数据流

### 5.1 放置自动交互（`WaypointBlock` / `PocketWaypointBlock.setPlacedBy`）
1. 服务端放置：口袋锚点记录 `owner`。
2. 服务端：`activate()` 激活该锚点（首次放置即解锁）。
3. 服务端：`openMenu` 打开对应界面（名字空 → 命名；非空 → 传送列表）。

### 5.2 右键已激活锚点（`useWithoutItem` → `onUse`）
- 名字空 → `openMenu` 命名界面；非空 → `openMenu` 传送列表。

### 5.3 打开规则（按方块类型 + 名字）
| 方块 | 名字为空 | 名字非空 |
|---|---|---|
| 传送锚点 | 命名传送锚点 | 传送列表 |
| 口袋锚点 | 命名口袋锚点 | 传送列表 |

### 5.4 保存跳转
1. 命名界面保存 → 客户端发 `RenameWaypointPayload(pos, text)`。
2. 服务端校验权限（传送锚点=创造；口袋锚点=所有者）→ 改名。
3. 服务端改名后 `openMenu` 传送列表。

### 5.5 列表 → 命名跳转
1. 传送列表点击第 2 行锚点名字 → 客户端发 `OpenRenameScreenPayload(pos)`。
2. 服务端校验权限 → `openMenu` 对应命名界面。

### 5.6 传送
1. 传送列表点击某锚点 → 发 `TeleportRequestPayload(source, target)`。
2. 服务端校验已解锁 → `WaypointTeleporter.teleport`。

## 6. 权限规则

| 操作 | 传送锚点 | 口袋锚点 |
|---|---|---|
| 改名字 | 仅创造模式 | 仅所有者 |
| 点击第 2 行跳命名界面 | 仅创造模式 | 仅所有者（否则第 2 行不可点击） |
| 打开传送列表 | 所有人 | 所有人 |
| 传送 | 所有人（已解锁） | 所有人（已解锁） |

## 7. 网络 Payload

| Payload | 方向 | 内容 | 用途 |
|---|---|---|---|
| `RenameWaypointPayload` | C→S | `BlockPos, String` | 改名；服务端改名后 openMenu 列表 |
| `OpenRenameScreenPayload` | C→S | `BlockPos` | 请求打开命名界面 |
| `TeleportRequestPayload` | C→S | `UUID source, UUID target` | 传送（已有） |
| `SyncActivatedWaypointsPayload` | S→C | `List<ActivatedWaypointInfo>` | 同步已解锁列表（已有） |

## 8. 翻译键清单（新增）

| 翻译键 | 中文 | 英文 |
|---|---|---|
| `gui.teleportwaypoint.rename_waypoint` | 命名传送锚点 | Name Waypoint |
| `gui.teleportwaypoint.rename_pocket_waypoint` | 命名口袋锚点 | Name Pocket Waypoint |
| `gui.teleportwaypoint.waypoint_list` | 传送列表 | Waypoint List |
| `gui.teleportwaypoint.save` | 保存 | Save |
| `gui.teleportwaypoint.close` | 关闭 | Close |
| `gui.teleportwaypoint.search` | 搜索… | Search… |
| `gui.teleportwaypoint.current_name` | 锚点名字 | Waypoint Name |
| `teleportwaypoint.waypoint.empty` | 未命名传送锚点 | Unnamed Waypoint |
| `teleportwaypoint.pocket_waypoint.empty` | 未命名口袋锚点 | Unnamed Pocket Waypoint |
| `gui.teleportwaypoint.no_waypoints` | 暂无已解锁的锚点 | No unlocked waypoints |

## 9. 决策记录

- **跳转机制**：服务端 openMenu（方案 A）。
- **三界面**：命名传送锚点、命名口袋锚点、传送列表（共享）。
- **口袋锚点命名独立界面**，标题「命名口袋锚点」。
- **口袋锚点空名翻译键** `teleportwaypoint.pocket_waypoint.empty`。
- **滚动列表**：使用原版 `ContainerObjectSelectionList`（自带右侧滚动条 + 滚轮，满足"类似创造模式物品栏翻页"）。
- **所有文本翻译键，禁止硬编码**。

## 10. 实施步骤（简要）

1. 重构菜单：新增 `RenameWaypointMenu`、`RenamePocketWaypointMenu`、`WaypointListMenu`，注册到 `ModMenus`。
2. 重构屏幕：新增三个 Screen，删除旧的两个。
3. `WaypointBlockEntity.openMenu()` 按名字决定打开命名或列表。
4. 方块 `setPlacedBy` 实现放置自动交互。
5. 新增 `OpenRenameScreenPayload`，改造 `RenameWaypointPayload` 处理后跳转。
6. 传送列表实现搜索 + 滚动列表 + 第 2 行可点击。
7. 语言文件补翻译键。
8. `gradlew build` 验证。

## 11. 待确认点

1. 命名界面保存按钮：无权限时按钮文案为「关闭」（关闭界面），有权限时为「保存」（保存并跳转列表）——是否 OK？
2. 传送列表第 2 行「锚点名字」对无权限玩家：不可点击（无下划线/图标）——是否 OK？
3. 传送列表滚动列表项只显示「名字」，不显示维度/距离（MVP 简化）——是否 OK？
