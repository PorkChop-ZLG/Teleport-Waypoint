# Xaero 配置分组与显示过滤最终设计

**日期：** 2026-08-17
**状态：** 已实现

## 目标

- 将 Xaero 联动配置分组为“Xaero 的地图联动”。
- 按类型（传送锚点 / 口袋锚点）和状态（未激活 / 已激活）分别控制显示。
- 支持按距离过滤小地图和世界内 3D 显示。

## 配置结构

```
Xaero 的地图联动 (xaeroMapIntegration)
├── showWaypoints               // 总开关，默认 true
├── waypoint                    // 传送锚点
│   ├── showInactive            // 默认 true
│   ├── showActive              // 默认 true
│   └── range                   // 默认 128，0=不限制
└── pocketWaypoint              // 口袋锚点
    ├── showInactive            // 默认 true
    ├── showActive              // 默认 true
    └── range                   // 默认 128，0=不限制
```

## 显示规则

- 主开关 `showWaypoints` 优先。
- 再按类型/状态过滤：
  - 传送锚点：激活看 `waypoint.showActive`，未激活看 `waypoint.showInactive`；
  - 口袋锚点：激活看 `pocketWaypoint.showActive`，未激活看 `pocketWaypoint.showInactive`。
- 距离过滤：
  - 仅影响小地图 2D 图标和 Xaero 世界中 3D Waypoint；
  - 不影响世界地图；
  - 仅对玩家当前维度生效；
  - `range > 0` 时超出距离不显示；
  - `range = 0` 表示不限制；
  - 玩家移动超过 16 格或切换维度时自动重新同步。

## 世界地图图标

- 传送锚点：未激活红 / 已激活青。
- 口袋锚点：未激活黄 / 已激活绿。
- 悬停只显示名字。
- 右键菜单三行：名字、坐标（展示）、传送（激活才可用）。

## 小地图颜色

- 传送锚点：未激活红 / 已激活青。
- 口袋锚点：未激活黄 / 已激活绿。

## 实现文件

- `Config.java`
- `XaeroIntegration.java`
- `XaeroWorldMapIntegration.java`
- `XaeroMinimapIntegration.java`
- `TeleportWaypointWorldProvider.java`
- `TeleportWaypointWorldReader.java`
- `TeleportWaypointWorldRenderer.java`
- `TeleportWaypointContext.java`
- `TeleportWaypointInfoOption.java`
- `TeleportRightClickOption.java`
- `ClientWaypointInfo.java`
- `ClientWaypointState.java`
- 语言文件 `en_us.json` / `zh_cn.json`

## 验证

- `gradlew compileJava --offline`
- `gradlew build --offline`
- `gradlew prepareClientRun --offline`
