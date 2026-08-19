# 传送锚点：原版结构覆盖可选数据包 设计文档

**日期：** 2026-08-19
**状态：** Approved
**方案：** 模组内置可选数据包 + Common 配置

## Problem Statement

本模组希望将传送锚点自然生成到 Minecraft 原版的多种结构中，让玩家安装 Xaero 地图后可以直接在地图上发现并探索这些结构。

之前的实现方式是直接在模组默认资源中覆盖 `data/minecraft/structure/...` NBT 文件。代码审查认为这种方式过于侵入，不适合作为模组默认行为。

因此需要一种方式：

- 不再默认修改原版结构；
- 玩家可以自由选择是否启用；
- 启用后仍能保证结构内出现固定位置、固定数量的传送锚点；
- 支持中英文显示与配置。

## Design

### 总体方案

采用“模组内置可选数据包”：

- 数据包随模组分发，但默认不直接覆盖原版结构；
- 数据包默认启用，玩家仍可手动禁用；
- 数据包内部继续使用“覆盖原版结构 NBT”的方式放置传送锚点；
- 新增 Common 配置控制新世界是否默认启用该数据包。

### 数据包 ID 与目录

- 数据包 ID（ResourceLocation）：`teleportwaypoint:data/teleportwaypoint/datapacks/vanilla_structure_waypoints`
- 数据包目录：
  ```
  src/main/resources/data/teleportwaypoint/datapacks/vanilla_structure_waypoints/
  ```

### 数据包命名与描述

| 键 | 中文 | English |
|---|---|---|
| `datapack.teleportwaypoint.vanilla_structure_waypoints.name` | 传送锚点：原版结构覆盖 | Teleport Waypoint: Vanilla Structure Overrides |
| `datapack.teleportwaypoint.vanilla_structure_waypoints.description` | 为Minecraft原版的部分结构添加传送锚点 | Adds teleport waypoints to selected vanilla structures |

### 覆盖结构列表

1. 远古城市 `ancient_city`
2. 堡垒遗迹 `bastion_remnant`
3. 末地城 `end_city`
4. 下界要塞 `nether_fortress`
5. 林地府邸 `woodland_mansion`
6. 海底神殿 `ocean_monument`
7. 要塞 `stronghold`
8. 沙漠神殿 `desert_pyramid`
9. 雪屋 `igloo`
10. 丛林神庙 `jungle_temple`
11. 试炼密室 `trial_chambers`（已完成，作为模板迁移）

> `village` 已有翻译键，但不在本次覆盖列表内；保留作为格式参考。

### 配置调整

新增 `CommonConfig`，文件 `config/teleportwaypoint/common.toml`：

| 选项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `defaultEnableStructureWaypoints` | boolean | `true` | 新世界是否默认启用原版结构覆盖数据包 |
| `teleportCooldownTicks` | int | `20` | 传送冷却，从原 `ServerConfig` 迁移而来 |

删除：

- `ClientConfig.java`（当前仅占位，无实际内容）
- `ServerConfig.java`（迁移到 `CommonConfig`）

更新：

- `TeleportWaypoint`：注册 `CommonConfig`，不再注册 `ClientConfig` / `ServerConfig`
- `TeleportRateLimiter`：改用 `CommonConfig.TELEPORT_COOLDOWN_TICKS`

### 数据包注册

新增独立类 `DatapackRegistration`：

位置：`src/main/java/com/zonlong/teleportwaypoint/datapack/DatapackRegistration.java`

职责：

- 监听 `AddPackFindersEvent`；
- 仅处理 `PackType.SERVER_DATA`；
- 根据 `CommonConfig.DEFAULT_ENABLE_STRUCTURE_WAYPOINTS` 选择 `PackSource`：
  - `true` → `PackSource.BUILT_IN`（新世界自动启用）
  - `false` → `PackSource.FEATURE`（新世界不自动启用）
- `alwaysActive = false`，保证玩家可手动开关。

在 `TeleportWaypoint` 中注册：

```java
modEventBus.addListener(DatapackRegistration::onAddPackFinders);
```

### 数据包内部结构

```
src/main/resources/data/teleportwaypoint/datapacks/vanilla_structure_waypoints/
├── pack.mcmeta
└── data/
    └── minecraft/
        └── structure/
            ├── ancient_city/...
            ├── bastion_remnant/...
            ├── end_city/...
            ├── nether_fortress/...
            ├── woodland_mansion/...
            ├── ocean_monument/...
            ├── stronghold/...
            ├── desert_pyramid/...
            ├── igloo/...
            ├── jungle_temple/...
            └── trial_chambers/...   # 从模组默认资源迁移
```

`pack.mcmeta` 示例：

```json
{
  "pack": {
    "description": {
      "translate": "datapack.teleportwaypoint.vanilla_structure_waypoints.description"
    },
    "pack_format": 48
  }
}
```

### 语言文件

`en_us.json` / `zh_cn.json` 新增：

- 数据包名称/描述翻译键；
- Common 配置分组与选项翻译键；
- 结构锚点名称翻译键：
  - `teleportwaypoint.waypoint.ancient_city`
  - `teleportwaypoint.waypoint.bastion_remnant`
  - `teleportwaypoint.waypoint.end_city`
  - `teleportwaypoint.waypoint.nether_fortress`
  - `teleportwaypoint.waypoint.woodland_mansion`
  - `teleportwaypoint.waypoint.ocean_monument`
  - `teleportwaypoint.waypoint.stronghold`
  - `teleportwaypoint.waypoint.desert_pyramid`
  - `teleportwaypoint.waypoint.igloo`
  - `teleportwaypoint.waypoint.jungle_temple`
  - `teleportwaypoint.waypoint.trial_chambers`（已有，保留）

删除：

- `teleportwaypoint.configuration.client.placeholder`
- `teleportwaypoint.configuration.client.placeholder.tooltip`
- `teleportwaypoint.configuration.section.teleportwaypoint.client.toml`
- `teleportwaypoint.configuration.section.teleportwaypoint.client.toml.title`

## Data Flow

### Common 配置加载

```
游戏启动 / 模组构造
  └─ TeleportWaypoint
       ├─ registerConfig(COMMON, CommonConfig.SPEC, "teleportwaypoint/common.toml")
       └─ modEventBus.addListener(DatapackRegistration::onAddPackFinders)
```

### 数据包注册

```
创建/加载服务端数据包仓库
  └─ AddPackFindersEvent(SERVER_DATA)
       └─ DatapackRegistration
            ├─ 读取 CommonConfig.DEFAULT_ENABLE_STRUCTURE_WAYPOINTS
            ├─ true  → PackSource.BUILT_IN
            └─ false → PackSource.FEATURE
```

### 数据包启用后

```
数据包启用
  └─ 覆盖 data/minecraft/structure/...
       └─ 结构生成时放置 teleportwaypoint:waypoint + 方块实体
            ├─ 方块实体 NBT 携带 id
            ├─ 自动生成 uid 并注册到 WaypointRegistryData
            └─ Xaero 地图显示锚点
```

## Error Handling

- 配置读取异常：回退默认值 `true`；
- `pack.mcmeta` 缺失/格式错误：数据包不会出现在列表中，开发阶段通过日志确认；
- 与其他结构覆盖数据包冲突：后加载者生效，文档提示；
- 旧配置不自动迁移：用户需手动设置 `common.toml`；
- 多人服务器：数据包为服务端/世界数据包，客户端单独启用无效；
- 旧存档：只影响新生成区块；
- 停用数据包：新生成结构不再包含锚点，已生成锚点保留。

## Testing Strategy

- `gradlew build --offline --console=plain`
- `gradlew prepareClientRun --offline`
- 数据包界面可见名称/描述，且默认启用行为符合配置；
- `common.toml` 自动生成且选项正确；
- 新建世界逐一验证 11 个结构；
- 每个结构实例出现 1 个传送锚点，名称正确；
- Xaero 地图联动显示；
- 未启用数据包时原版结构不变；
- 回归传送、激活、改名、删除、跨维度、Xaero 功能；
- 专用服务器数据包启用验证。

## Decisions Made

- 采用模组内置可选数据包，避免默认覆盖原版结构；
- 数据包目录使用 `data/teleportwaypoint/datapacks/vanilla_structure_waypoints`；
- 数据包默认启用，但通过 Common 配置控制默认行为；
- `alwaysActive = false`，玩家始终可手动开关；
- `ServerConfig` 迁移到 `CommonConfig`；
- 删除无实际内容的 `ClientConfig`；
- 数据包注册使用独立类 `DatapackRegistration`。

## Non-Goals

- 不覆盖非 Jigsaw/非列表中的结构（如村庄本次不加入）；
- 不做旧存档结构回溯；
- 不实现 per-world 独立传送冷却；
- 不自动迁移旧 `server.toml` 配置值。

## Next Steps

转入 planning 技能生成详细实施计划。
