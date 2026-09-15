# Xaero 配置调整实施计划

**日期：** 2026-08-18
**状态：** Ready for execution
**依据：** `docs/plans/2026-08-18-xaero-config-adjust-design.md`

---

## Phase 1：修改配置类

### Task 1.1 `XaeroMinimapConfig`
- 删除字段：`SHOW_WAYPOINT_NAMES`、`SHOW_INACTIVE_POCKET_WAYPOINTS`、`SHOW_ACTIVE_POCKET_WAYPOINTS`、`POCKET_WAYPOINT_RANGE`
- 新增顶层字段：`RANGE`
- `waypoint.range` 与顶层 `range` 默认均改为 256
- 删除 `pocketWaypoint` 节
- 保留翻译键：`xaerominimap.showWaypoints`、`xaerominimap.range`、`xaerominimap.waypoint.showInactive/showActive`

### Task 1.2 `XaeroWorldMapConfig`
- 删除字段：`SHOW_WAYPOINT_NAMES`、`WAYPOINT_RANGE`、`POCKET_WAYPOINT_RANGE`、`SHOW_INACTIVE_POCKET_WAYPOINTS`、`SHOW_ACTIVE_POCKET_WAYPOINTS`
- 删除 `pocketWaypoint` 节
- 保留 `showWaypoints` 与 `waypoint.showInactive/showActive`
- 保留翻译键：`xaeroworldmap.showWaypoints`、`xaeroworldmap.waypoint.showInactive/showActive`

**验收：** 编译通过；配置类无已删除字段。

---

## Phase 2：更新消费方

### Task 2.1 `XaeroMinimapIntegration`
- 删除 `showWaypointNames` 读取，名称始终传入 `displayName`
- `shouldShow()`：
  - pocket 且未激活 → false
  - pocket 且已激活 → true
- 距离过滤统一使用 `XaeroMinimapConfig.RANGE`

### Task 2.2 `TeleportWaypointWorldProvider`
- `shouldShow()`：
  - pocket 且未激活 → false
  - pocket 且已激活 → true
- 删除对 range 的任何引用

### Task 2.3 `XaeroWorldMapIntegration`
- 删除 `showWaypointNames()` 方法
- 删除 `xaeroShowWaypointNames` 相关注册/镜像
- 删除 range 相关逻辑

**验收：** 无 `SHOW_WAYPOINT_NAMES`、`WAYPOINT_RANGE`、`POCKET_WAYPOINT_RANGE` 残留引用。

---

## Phase 3：语言文件

### Task 3.1 `en_us.json` / `zh_cn.json`
- 删除 `xaerominimap.showWaypointNames`、`xaerominimap.pocketWaypoint.*` 相关键
- 删除 `xaeroworldmap.showWaypointNames`、`xaeroworldmap.range`、`xaeroworldmap.pocketWaypoint.*` 相关键
- 新增 `xaerominimap.range` 与 tooltip
- 默认值描述统一为 256

**验收：** JSON 合法，键集合与配置类一致。

---

## Phase 4：文档同步

### Task 4.1 `README.md`
- 默认范围改为 256
- 小地图：统一 range；世界地图：无范围配置
- 删除 `showWaypointNames` 相关描述
- 增加“口袋锚点仅激活后显示”说明

### Task 4.2 设计文档
- `docs/plans/2026-08-18-xaero-config-adjust-design.md` 已是最新
- 如 `2026-08-17-xaero-config-filter-design.md` 有冲突描述，一并同步

**验收：** 文档与代码一致。

---

## Phase 5：构建与验证

### Task 5.1 构建
```
gradlew.bat build --offline --console=plain
gradlew.bat prepareClientRun --offline
```

### Task 5.2 启动验证
- 启动开发服务器确认 `server.toml` 不受影响
- 启动开发客户端确认：
  - `xaero-minimap.toml` 只有 `showWaypoints`、`range`、`waypoint.showInactive/showActive`
  - `xaero-worldmap.toml` 只有 `showWaypoints`、`waypoint.showInactive/showActive`
  - range 默认 256

### Task 5.3 人工验证
- [ ] 小地图统一 range 生效
- [ ] 世界地图无 range 配置且不报错
- [ ] 口袋锚点未激活不显示，激活后显示
- [ ] 名称始终显示
- [ ] 传送锚点 inactive/active 开关正常
- [ ] 回归：改名、登录、C1
