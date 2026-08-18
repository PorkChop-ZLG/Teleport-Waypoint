# 配置系统重构实施计划

**日期：** 2026-08-18
**状态：** Ready for execution
**依据：** `docs/plans/2026-08-18-config-rework-design.md`

---

## Phase 1：新增 4 个配置类

### Task 1.1 `ClientConfig`
- 文件：`src/main/java/com/zonlong/teleportwaypoint/config/ClientConfig.java`
- 配置项：
  - `placeholder`：boolean，默认 true，`.translation("teleportwaypoint.configuration.client.placeholder")`
- 提供 `public static final ModConfigSpec SPEC`

### Task 1.2 `ServerConfig`
- 文件：`src/main/java/com/zonlong/teleportwaypoint/config/ServerConfig.java`
- 配置项：
  - `teleportCooldownTicks`：int，默认 20，范围 0~72000，`.translation("teleportwaypoint.configuration.server.teleportCooldownTicks")`
- 提供 `public static final ModConfigSpec SPEC`

### Task 1.3 `XaeroMinimapConfig`
- 文件：`src/main/java/com/zonlong/teleportwaypoint/config/XaeroMinimapConfig.java`
- 配置项：
  - `showWaypoints`
  - `showWaypointNames`
  - `waypoint.showInactive`
  - `waypoint.showActive`
  - `waypoint.range`（默认 128，范围 0~100000）
  - `pocketWaypoint.showInactive`
  - `pocketWaypoint.showActive`
  - `pocketWaypoint.range`（默认 128，范围 0~100000）
- 所有项使用 `.translation("teleportwaypoint.configuration.xaerominimap....")`

### Task 1.4 `XaeroWorldMapConfig`
- 文件：`src/main/java/com/zonlong/teleportwaypoint/config/XaeroWorldMapConfig.java`
- 配置项与 `XaeroMinimapConfig` 相同，翻译键基名改为 `xaeroworldmap`

**验收：** 4 个类编译通过。

---

## Phase 2：注册与删除旧 Config

### Task 2.1 注册新配置
- 文件：`src/main/java/com/zonlong/teleportwaypoint/TeleportWaypoint.java`
- 替换 `modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC)` 为 4 条注册：
  - CLIENT + `ClientConfig.SPEC` + `"teleportwaypoint/client.toml"`
  - SERVER + `ServerConfig.SPEC` + `"teleportwaypoint/server.toml"`
  - CLIENT + `XaeroMinimapConfig.SPEC` + `"teleportwaypoint/xaero-minimap.toml"`
  - CLIENT + `XaeroWorldMapConfig.SPEC` + `"teleportwaypoint/xaero-worldmap.toml"`

### Task 2.2 删除旧 Config
- 删除 `src/main/java/com/zonlong/teleportwaypoint/Config.java`
- 全局替换所有 `Config.` 引用为对应新配置类

**验收：** 无 `Config` 残留引用。

---

## Phase 3：更新消费方

### Task 3.1 `TeleportRateLimiter`
- 文件：`src/main/java/com/zonlong/teleportwaypoint/core/TeleportRateLimiter.java`
- 读取 `ServerConfig.TELEPORT_COOLDOWN_TICKS.get()`
- 内部换算：`cooldownMs = ticks * 50L`

### Task 3.2 `XaeroMinimapIntegration`
- 文件：`src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`
- 将 `Config.*` / `XaeroIntegration.*` 替换为 `XaeroMinimapConfig.*`
- 删除对 `XaeroIntegration.showWaypoints/showWaypointNames/shouldShow/getDisplayRange` 的调用

### Task 3.3 `XaeroWorldMapIntegration`
- 文件：`src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroWorldMapIntegration.java`
- 将 `Config.*` / `XaeroIntegration.*` 替换为 `XaeroWorldMapConfig.*`
- `showWaypoints()` / `showWaypointNames()` 改为读 `XaeroWorldMapConfig`
- `syncConfigToXaero()` 改为镜像 `XaeroWorldMapConfig` 的值

### Task 3.4 `TeleportWaypointWorldProvider`
- 文件：`src/main/java/com/zonlong/teleportwaypoint/client/xaero/TeleportWaypointWorldProvider.java`
- 将 `XaeroIntegration.shouldShow(...)` 替换为基于 `XaeroWorldMapConfig` 的本地判断

### Task 3.5 精简 `XaeroIntegration`
- 文件：`src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroIntegration.java`
- 仅保留 `tick()` 分派逻辑
- 删除 `showWaypoints()`、`showWaypointNames()`、`shouldShow()`、`getDisplayRange()`

**验收：** 编译通过；无 `XaeroIntegration.showWaypoints` 等旧调用。

---

## Phase 4：语言文件

### Task 4.1 `en_us.json`
- 新增 4 组配置翻译键（client/server/xaerominimap/xaeroworldmap）
- 删除旧 `teleportwaypoint.configuration.*` 中已废弃的键（保留文件级 section 键）

### Task 4.2 `zh_cn.json`
- 同步新增/删除

**验收：** 配置界面显示正常，无缺失翻译警告。

---

## Phase 5：构建与验证

### Task 5.1 构建
```
gradlew.bat build --offline --console=plain
gradlew.bat prepareClientRun --offline
```

### Task 5.2 人工验证
- [ ] 启动后生成 `config/teleportwaypoint/` 下 4 个文件
- [ ] `server.toml` 含 `teleportCooldownTicks = 20`
- [ ] `xaero-minimap.toml` 与 `xaero-worldmap.toml` 各自包含完整独立选项
- [ ] NeoForge 配置界面显示 4 个分组且翻译正常
- [ ] 传送冷却默认 20 tick 生效，改为 0 后无冷却
- [ ] 小地图与世界地图可独立开关、独立范围
- [ ] 回归：改名同步、登录同步、C1 幽灵锚点
