# Xaero 配置调整设计

**日期：** 2026-08-18
**状态：** Approved
**方案：** A —— 直接修改现有配置与渲染逻辑

## Problem Statement

对当前 Xaero 配置做以下调整：

1. 传送锚点与口袋锚点的显示范围默认改为 256 格，并同步 README 与设计文档。
2. 删除 `showWaypointNames` 配置项，由 `showWaypoints` 统一管理；开启时始终显示名称。
3. 口袋锚点改为仅玩家激活后才在 Xaero 地图上显示，防止泄露个人基地坐标。
4. 实测 `range` 对 Xaero 世界地图无效，因此从世界地图配置中删除。
5. 小地图配置中的两个 `range` 合并为一个顶层 `range`，统一控制传送锚点与口袋锚点显示距离。

## Design

### 配置结构变化

#### `xaero-minimap.toml`
```toml
showWaypoints = true
range = 256

[waypoint]
showInactive = true
showActive = true
```

- 删除 `showWaypointNames`
- 删除 `pocketWaypoint.showInactive` / `showActive` / `range`
- 两个 range 合并为顶层 `range`，默认 256
- 口袋锚点仅激活后显示，由代码逻辑保证

#### `xaero-worldmap.toml`
```toml
showWaypoints = true

[waypoint]
showInactive = true
showActive = true
```

- 删除 `showWaypointNames`
- 删除 `range`
- 删除整个 `pocketWaypoint` 节
- 口袋锚点仅激活后显示

### 组件改动

| 文件 | 改动 |
|---|---|
| `config/XaeroMinimapConfig.java` | 删除 `SHOW_WAYPOINT_NAMES`、`SHOW_INACTIVE_POCKET_WAYPOINTS`、`SHOW_ACTIVE_POCKET_WAYPOINTS`、`POCKET_WAYPOINT_RANGE`；新增顶层 `RANGE`，默认 256 |
| `config/XaeroWorldMapConfig.java` | 删除 `SHOW_WAYPOINT_NAMES`、`WAYPOINT_RANGE`、`POCKET_WAYPOINT_RANGE`、`SHOW_INACTIVE_POCKET_WAYPOINTS`、`SHOW_ACTIVE_POCKET_WAYPOINTS`；删除 `pocketWaypoint` 节 |
| `client/xaero/XaeroMinimapIntegration.java` | 名称始终显示；口袋锚点仅激活显示；距离统一用顶层 `RANGE` |
| `client/xaero/TeleportWaypointWorldProvider.java` | 口袋锚点仅激活显示；不再读取 range |
| `client/xaero/XaeroWorldMapIntegration.java` | 删除 `showWaypointNames`、range 相关逻辑 |
| 语言文件 | 删除已移除翻译键，保留并更新 range 键 |
| README / 设计文档 | 同步默认 256 与行为说明 |

### 数据流

#### 小地图
```
showWaypoints = false → 不显示
showWaypoints = true →
  传送锚点：未激活受 waypoint.showInactive 控制；已激活受 waypoint.showActive 控制
  口袋锚点：未激活不显示；已激活显示
  距离：统一使用顶层 range（0=不限制）
  名称：始终显示
```

#### 世界地图
```
showWaypoints = false → 不显示
showWaypoints = true →
  传送锚点：未激活受 waypoint.showInactive 控制；已激活受 waypoint.showActive 控制
  口袋锚点：未激活不显示；已激活显示
  距离：不应用 range
  名称：hover / 右键菜单始终显示
```

### 错误处理与迁移

- 旧配置中的多余键被 NeoForge 忽略。
- 缺失的新键由 NeoForge 自动补默认值。
- 不自动迁移旧配置，文档说明。
- 口袋锚点未激活时不在任何 Xaero 地图显示，避免泄露坐标。
- 删除激活后地图标记立即消失（依赖现有增量同步）。

### 测试策略

- 构建：`gradlew build --offline`、`gradlew prepareClientRun --offline`
- 启动客户端/服务端确认配置生成与内容
- 人工验证：
  - 小地图统一 range 生效
  - 世界地图无 range 配置
  - 口袋锚点未激活不显示，激活后显示
  - 名称始终显示
  - 传送锚点 inactive/active 开关正常
  - 回归：改名、登录、C1

## Decisions Made

- 默认 range 256。
- 世界地图删除 range。
- 小地图 range 合并为一个顶层项。
- 删除 `showWaypointNames`，由 `showWaypoints` 统一管理。
- 口袋锚点仅激活显示，删除相关开关。
- 采用方案 A 直接修改，不引入公共基类。

## Non-Goals

- 不实现世界地图距离过滤。
- 不自动迁移旧配置文件。
- 不引入自动化测试框架。

## Next Steps

转入 planning 技能生成详细实施计划。
