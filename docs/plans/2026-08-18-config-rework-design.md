# 配置系统重构设计（多 TOML 分文件）

**日期：** 2026-08-18
**状态：** Approved
**方案：** A —— 独立配置类 + NeoForge ModConfigSpec

## Problem Statement

当前模组只有一份 NeoForge `COMMON` 配置（`config/teleportwaypoint-common.toml`），所有配置项混在一起。需要：

1. 将配置文件放到 `config/teleportwaypoint/` 子文件夹。
2. 拆分为客户端、服务端、Xaero 小地图、Xaero 世界地图四份独立 toml。
3. 传送冷却单位从毫秒改为 tick，默认 20 tick。
4. Xaero 小地图与世界地图各自拥有完整独立的显示配置。

## Design

### 配置文件布局

```
config/
└── teleportwaypoint/
    ├── client.toml            # 客户端通用配置（当前仅占位）
    ├── server.toml            # 服务端配置（传送冷却，单位 tick）
    ├── xaero-minimap.toml     # Xaero 小地图联动配置（独立完整一套）
    └── xaero-worldmap.toml    # Xaero 世界地图联动配置（独立完整一套）
```

### 配置类

| 类 | 对应文件 | 配置项 |
|---|---|---|
| `ClientConfig`（新） | `client.toml` | `placeholder`（占位，默认 true） |
| `ServerConfig`（新） | `server.toml` | `teleportCooldownTicks`（默认 20，范围 0~72000，0=关闭冷却） |
| `XaeroMinimapConfig`（新） | `xaero-minimap.toml` | `showWaypoints`、`showWaypointNames`、`waypoint.showInactive/showActive/range`、`pocketWaypoint.showInactive/showActive/range` |
| `XaeroWorldMapConfig`（新） | `xaero-worldmap.toml` | 同上，独立一套 |

### 注册方式

```java
modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "teleportwaypoint/client.toml");
modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, "teleportwaypoint/server.toml");
modContainer.registerConfig(ModConfig.Type.CLIENT, XaeroMinimapConfig.SPEC, "teleportwaypoint/xaero-minimap.toml");
modContainer.registerConfig(ModConfig.Type.CLIENT, XaeroWorldMapConfig.SPEC, "teleportwaypoint/xaero-worldmap.toml");
```

### 消费方数据流

| 消费方 | 原来读 | 改为读 |
|---|---|---|
| `TeleportRateLimiter` | `Config.TELEPORT_COOLDOWN_MS` | `ServerConfig.TELEPORT_COOLDOWN_TICKS.get() * 50L` |
| `XaeroMinimapIntegration` | `Config.*` / `XaeroIntegration.*` | `XaeroMinimapConfig.*` |
| `TeleportWaypointWorldProvider` | `XaeroIntegration.shouldShow(...)` | `XaeroWorldMapConfig` + 本地判断 |
| `XaeroWorldMapIntegration` | `Config.*` / `XaeroIntegration.*` | `XaeroWorldMapConfig.*` |

### `XaeroIntegration` 精简

保留为反射分派器：

```java
public final class XaeroIntegration {
    public static void tick() {
        if (ModList.get().isLoaded("xaeroworldmap")) XaeroWorldMapIntegration.tick();
        if (ModList.get().isLoaded("xaerominimap")) {
            XaeroMinimapIntegration.tick();
            XaeroMinimapIntegration.sync();
        }
    }
}
```

删除 `showWaypoints()`、`showWaypointNames()`、`shouldShow()`、`getDisplayRange()`。

### 翻译键格式

```
teleportwaypoint.configuration.<fileBase>.<section...>.<option>
```

每个配置项通过 `.translation(...)` 显式设置，避免 NeoForge 默认叶子键冲突。

示例：
- `teleportwaypoint.configuration.client.placeholder`
- `teleportwaypoint.configuration.server.teleportCooldownTicks`
- `teleportwaypoint.configuration.xaerominimap.showWaypoints`
- `teleportwaypoint.configuration.xaerominimap.waypoint.showInactive`
- `teleportwaypoint.configuration.xaeroworldmap.pocketWaypoint.range`

### 错误处理

- FML 自动创建 `config/teleportwaypoint/` 父目录。
- NeoForge 自动备份/修正损坏配置。
- `defineInRange` 限制数值范围。
- 未安装 Xaero 时 Xaero 配置仍生成但不被读取，无副作用。
- 热重载由 NeoForge 文件监听 + 各集成 tick 检测处理。
- 旧 `config/teleportwaypoint-common.toml` 不自动迁移，文档说明。

### 测试策略

- 构建：`gradlew build --offline`、`gradlew prepareClientRun --offline`
- 验证 4 个配置文件生成与内容
- 验证 NeoForge 配置界面分组与翻译键
- 回归：传送冷却、小地图/世界地图独立控制、范围独立、改名同步、登录同步、C1 幽灵锚点

## Decisions Made

- 采用 NeoForge `ModConfigSpec` + `registerConfig(type, spec, fileName)`。
- 采用 4 个独立配置类。
- 冷却单位改为 tick，默认 20。
- Xaero 小地图/世界地图各自独立完整配置。
- `XaeroIntegration` 保留为精简反射分派器。
- 删除旧 `Config.java`。
- 不自动迁移旧配置文件。

## Non-Goals

- 不引入自定义 TOML 加载。
- 不实现旧配置自动迁移。
- 不新增自动化测试框架。

## Next Steps

转入 planning 技能生成详细实施计划。
