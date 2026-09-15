# Xaero 配置分组与显示过滤最终设计

**日期：** 2026-08-17
**状态：** 已实现；已被 `2026-08-18-xaero-config-adjust-design.md` 取代

## 目标

- 将 Xaero 联动配置拆分为小地图与世界地图两份独立配置。
- 传送锚点按状态（未激活 / 已激活）分别控制显示。
- 口袋锚点仅玩家激活后显示，防止泄露个人基地坐标。
- 小地图按统一距离过滤；世界地图不按距离过滤。

## 配置结构

### `xaero-minimap.toml`

```toml
showWaypoints = true
range = 256

[waypoint]
showInactive = true
showActive = true
```

### `xaero-worldmap.toml`

```toml
showWaypoints = true

[waypoint]
showInactive = true
showActive = true
```

说明：
- 没有 `showWaypointNames`：名称由 `showWaypoints` 统一管理，开启时始终显示。
- 口袋锚点没有显示开关：仅激活后显示。
- 世界地图没有 `range`：世界地图为全局视角，不应用距离过滤。

## 显示规则

- 主开关 `showWaypoints` 优先。
- 传送锚点：激活看 `waypoint.showActive`，未激活看 `waypoint.showInactive`。
- 口袋锚点：未激活不显示；激活后显示。
- 小地图距离过滤：统一使用顶层 `range`，默认 256，`0` 表示不限制；仅对玩家当前维度生效。
- 世界地图距离过滤：不应用。

## 世界地图图标

- 传送锚点：未激活红 / 已激活青。
- 口袋锚点：已激活绿（未激活不显示）。
- 悬停显示名字。
- 右键菜单三行：名字、坐标（展示）、传送（激活才可用）。

## 小地图颜色

- 传送锚点：未激活红 / 已激活青。
- 口袋锚点：已激活绿（未激活不显示）。

## 实现文件

- `config/XaeroMinimapConfig.java`
- `config/XaeroWorldMapConfig.java`
- `client/xaero/XaeroMinimapIntegration.java`
- `client/xaero/XaeroWorldMapIntegration.java`
- `client/xaero/TeleportWaypointWorldProvider.java`
- `client/xaero/TeleportWaypointWorldReader.java`
- `client/xaero/TeleportWaypointWorldRenderer.java`
- 语言文件 `en_us.json` / `zh_cn.json`

## 验证

- `gradlew compileJava --offline`
- `gradlew build --offline`
- `gradlew prepareClientRun --offline`
