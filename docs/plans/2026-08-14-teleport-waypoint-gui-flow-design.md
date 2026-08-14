# 传送锚点 GUI 打开流程调整设计文档

**日期：** 2026-08-14
**状态：** 已确认（Approved）
**参考：** Waystones `WaystoneBlockBase.setPlacedBy` / `handleActivation` / `handleEditActions`

---

## 1. 目标

调整锚点交互的界面打开流程，使其更贴近 Waystones：

1. 只有**初次放置**时自动弹出对应命名界面。
2. 此后交互（无论名字是否为空）一律打开传送列表。
3. 改名入口 = 放置时打开的界面、传送列表第二行名字点击、shift-click。
4. 权限：改名/开门 —— 传送锚点仅创造；口袋锚点 = 创造或拥有者。

## 2. 交互流程

| 场景 | 行为 |
|---|---|
| 放置（`setPlacedBy`，仅玩家放置） | `activate` + 打开命名界面 |
| 交互 · 未激活 | `activate` + 打开传送列表 |
| 交互 · 已激活 | 打开传送列表 |
| shift-click 交互 | 有权限 → 命名界面；无权限 → 传送列表 |
| 传送列表第二行点击 | 有权限 → 命名界面 |

## 3. 权限规则

| 操作 | 传送锚点 | 口袋锚点 |
|---|---|---|
| 改名 / 打开命名界面 | 仅创造 | 创造 **或** 拥有者 |

## 4. 代码改动

1. `WaypointBlockEntity`：删除 `needsNaming()`、`openInitialScreen()`；保留 `openRenameScreen()`、`openListScreen()`。
2. `WaypointBlock` / `PocketWaypointBlock.setPlacedBy`：`openInitialScreen` → `openRenameScreen`。
3. `WaypointManager.onUse`：改为「未激活则 activate，然后 openListScreen」；新增 `canRename(Player, WaypointBlockEntity)`；shift-click 检测交给方块层。
4. `WaypointBlock` / `PocketWaypointBlock.useWithoutItem`：检测 `player.isShiftKeyDown()`，有权限 → `openRenameScreen`，否则走 `onUse`。
5. `ModNetwork.handleOpenRename`：口袋锚点权限补充「创造模式」。
6. 客户端命名/列表界面：无需改动（已支持）。

## 5. 待办实施清单

- [ ] 改 `WaypointBlockEntity`
- [ ] 改 `WaypointBlock` / `PocketWaypointBlock`
- [ ] 改 `WaypointManager`
- [ ] 改 `ModNetwork.handleOpenRename`
- [ ] `gradlew build` 验证
