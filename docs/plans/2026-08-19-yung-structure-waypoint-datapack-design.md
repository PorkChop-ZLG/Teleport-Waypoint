# 传送锚点：YUNG 结构兼容数据包 设计文档

**日期：** 2026-08-19
**状态：** Approved
**方案：** 独立可选数据包覆盖 YUNG 结构优化模组的入口 NBT

## Problem Statement

原版下界要塞、海底神殿、要塞、沙漠神殿、丛林神庙均为 Java 硬编码生成，没有可覆盖的原版结构 NBT 文件，因此无法用“原版结构 NBT 覆盖数据包”植入传送锚点。

本设计改为兼容 YUNG 的结构优化模组：

- 不再尝试修改原版硬编码结构；
- 通过独立数据包覆盖 YUNG 模组的入口 NBT 文件；
- 不引入 YUNG 模组作为 mod 依赖；
- 只有玩家安装了对应 YUNG 模组时，数据包才实际生效。

## Design

### 数据包 ID 与目录

- 数据包 ID（ResourceLocation）：`teleportwaypoint:data/teleportwaypoint/datapacks/yung_structure_waypoints`
- 数据包目录：
  ```
  src/main/resources/data/teleportwaypoint/datapacks/yung_structure_waypoints/
  ```

### 数据包命名与描述

| 键 | 中文 | English |
|---|---|---|
| `datapack.teleportwaypoint.yung_structure_waypoints.name` | 传送锚点：YUNG 结构兼容 | Teleport Waypoint: YUNG Structure Compatibility |
| `datapack.teleportwaypoint.yung_structure_waypoints.description` | 为 YUNG 的结构优化模组添加传送锚点 | Adds teleport waypoints to YUNG's structure enhancement mods |

### 配置

新增 Common 配置：

| 选项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `defaultEnableYungStructureWaypoints` | boolean | `true` | 新世界是否默认启用 YUNG 结构兼容数据包 |

### 覆盖的 YUNG 模组与入口 NBT

通过结构 JSON 的 `start_pool` 定位到对应 `template_pool`，再从 pool 中取得入口 NBT。

| YUNG 模组 | namespace | 入口 NBT 文件 |
|---|---|---|
| Better Desert Temples | `betterdeserttemples` | `data/betterdeserttemples/structure/starts/center.nbt` |
| Better Jungle Temples | `betterjungletemples` | `data/betterjungletemples/structure/start/start_0.nbt`<br>`data/betterjungletemples/structure/start/start_1.nbt`<br>`data/betterjungletemples/structure/start/start_2.nbt` |
| Better Nether Fortresses | `betterfortresses` | `data/betterfortresses/structure/keep.nbt`<br>`data/betterfortresses/structure/mod_integration/keep_create.nbt` |
| Better Ocean Monuments | `betteroceanmonuments` | `data/betteroceanmonuments/structure/start.nbt` |
| Better Strongholds | `betterstrongholds` | `data/betterstrongholds/structure/starts/junction_lg.nbt` |

> 以上 NBT 路径已在对应 jar 中逐一确认存在。

### 数据包内部结构

```
src/main/resources/data/teleportwaypoint/datapacks/yung_structure_waypoints/
├── pack.mcmeta
└── data/
    ├── betterdeserttemples/
    │   └── structure/starts/center.nbt
    ├── betterjungletemples/
    │   └── structure/start/start_0.nbt
    │   └── structure/start/start_1.nbt
    │   └── structure/start/start_2.nbt
    ├── betterfortresses/
    │   └── structure/keep.nbt
    │   └── structure/mod_integration/keep_create.nbt
    ├── betteroceanmonuments/
    │   └── structure/start.nbt
    └── betterstrongholds/
        └── structure/starts/junction_lg.nbt
```

`pack.mcmeta` 示例：

```json
{
  "pack": {
    "description": {
      "translate": "datapack.teleportwaypoint.yung_structure_waypoints.description"
    },
    "pack_format": 48
  }
}
```

### 注册逻辑

扩展 `DatapackRegistration`：

- 保留现有“原版结构覆盖数据包”注册；
- 新增“YUNG 结构兼容数据包”注册；
- `alwaysActive = false`；
- 由 `CommonConfig.DEFAULT_ENABLE_YUNG_STRUCTURE_WAYPOINTS` 决定 `PackSource.BUILT_IN` 或 `PackSource.FEATURE`。

### 语言文件新增

- `datapack.teleportwaypoint.yung_structure_waypoints.name`
- `datapack.teleportwaypoint.yung_structure_waypoints.description`
- `teleportwaypoint.configuration.common.defaultEnableYungStructureWaypoints`
- `teleportwaypoint.configuration.common.defaultEnableYungStructureWaypoints.tooltip`

## Data Flow

### 配置加载

```
游戏启动 / 模组构造
  └─ TeleportWaypoint
       ├─ registerConfig(COMMON, CommonConfig.SPEC, "teleportwaypoint/common.toml")
       └─ modEventBus.addListener(DatapackRegistration::onAddPackFinders)
```

### 数据包注册

```
AddPackFindersEvent(SERVER_DATA)
  └─ DatapackRegistration
       ├─ 注册原版结构覆盖数据包
       └─ 注册 YUNG 结构兼容数据包
```

### 数据包启用后

```
安装 YUNG 模组 + 启用 YUNG 兼容数据包
  └─ 覆盖 data/<yung_namespace>/structure/... 入口 NBT
       └─ YUNG 结构生成时使用被覆盖的 NBT
            ├─ NBT 带 teleportwaypoint:waypoint 方块实体
            ├─ 自动生成 uid 并注册
            └─ Xaero 地图显示锚点
```

## Error Handling

- YUNG 模组未安装：数据包无效果、不报错；
- YUNG 版本不匹配：可能无法覆盖到对应 NBT，文档注明针对 1.21.1 NeoForge 版本；
- 数据包优先级冲突：后加载者生效，文档提示；
- 多个 YUNG 模组：独立 namespace，互不干扰；
- 配置读取异常：回退默认值 `true`；
- 旧存档：只影响新生成区块；
- 停用数据包：新生成结构不再包含锚点，已生成锚点保留。

## Testing Strategy

- `gradlew build --offline --console=plain`
- `gradlew prepareClientRun --offline`
- 数据包列表可见名称/描述，默认启用行为符合配置；
- `common.toml` 包含 `defaultEnableYungStructureWaypoints`；
- jar 内包含 YUNG 兼容数据包及 NBT 文件；
- 安装 5 个 YUNG 模组 + YungsApi 后逐一验证结构入口出现锚点；
- 每个结构固定 1 个锚点，名称正确；
- Xaero 地图联动显示；
- 未安装 YUNG 模组时无副作用；
- 原有功能回归正常。

## Decisions Made

- 放弃直接处理原版硬编码结构；
- 改为兼容 YUNG 结构优化模组；
- 新增独立 YUNG 兼容数据包；
- 新增独立 Common 配置 `defaultEnableYungStructureWaypoints`；
- 通过 `start_pool` 定位入口 NBT；
- 不引入 YUNG 模组作为依赖。

## Non-Goals

- 不修改原版硬编码结构生成代码；
- 不引入 Mixin；
- 不兼容除 YUNG 以外的结构优化模组；
- 不自动迁移旧存档已生成结构。

## Next Steps

转入 planning 技能生成详细实施计划。
