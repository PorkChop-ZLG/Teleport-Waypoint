# 传送锚点：YUNG 结构兼容数据包 实施计划

**日期：** 2026-08-19
**状态：** Ready for execution
**依据：** `docs/plans/2026-08-19-yung-structure-waypoint-datapack-design.md`

---

## Phase 1：配置与注册

### Task 1.1 扩展 `CommonConfig`

- 在 `CommonConfig` 中新增：
  ```java
  DEFAULT_ENABLE_YUNG_STRUCTURE_WAYPOINTS = builder
      .comment("Whether new worlds enable the YUNG structure compatibility datapack by default.")
      .translation("teleportwaypoint.configuration.common.defaultEnableYungStructureWaypoints")
      .define("defaultEnableYungStructureWaypoints", true);
  ```

**验收：**
- 编译通过；
- `common.toml` 会包含该选项。

### Task 1.2 扩展 `DatapackRegistration`

- 新增第二个数据包注册：
  - ResourceLocation：`teleportwaypoint:data/teleportwaypoint/datapacks/yung_structure_waypoints`
  - 名称翻译键：`datapack.teleportwaypoint.yung_structure_waypoints.name`
  - `PackSource` 由 `CommonConfig.DEFAULT_ENABLE_YUNG_STRUCTURE_WAYPOINTS` 决定
  - `alwaysActive = false`
  - `Pack.Position.TOP`

**验收：**
- 编译通过；
- 数据包列表中能看到 YUNG 兼容数据包。

### Task 1.3 更新语言文件

`en_us.json` / `zh_cn.json` 新增：

- `datapack.teleportwaypoint.yung_structure_waypoints.name`
- `datapack.teleportwaypoint.yung_structure_waypoints.description`
- `teleportwaypoint.configuration.common.defaultEnableYungStructureWaypoints`
- `teleportwaypoint.configuration.common.defaultEnableYungStructureWaypoints.tooltip`

**验收：**
- JSON 合法；
- 中英文齐全。

### Task 1.4 创建数据包基础目录

- 新建：
  ```
  src/main/resources/data/teleportwaypoint/datapacks/yung_structure_waypoints/
  ```
- 新建 `pack.mcmeta`：
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

**验收：**
- 目录与 `pack.mcmeta` 存在。

---

## Phase 2：复制 YUNG 入口 NBT

### Task 2.1 从 jar 复制入口 NBT

从 `run/mods` 中对应 YUNG jar 复制以下文件到数据包：

| 源 jar | 源 NBT 路径 | 目标数据包路径 |
|---|---|---|
| YungsBetterDesertTemples | `data/betterdeserttemples/structure/starts/center.nbt` | `data/betterdeserttemples/structure/starts/center.nbt` |
| YungsBetterJungleTemples | `data/betterjungletemples/structure/start/start_0.nbt` | 同左 |
| YungsBetterJungleTemples | `data/betterjungletemples/structure/start/start_1.nbt` | 同左 |
| YungsBetterJungleTemples | `data/betterjungletemples/structure/start/start_2.nbt` | 同左 |
| YungsBetterNetherFortresses | `data/betterfortresses/structure/keep.nbt` | 同左 |
| YungsBetterNetherFortresses | `data/betterfortresses/structure/mod_integration/keep_create.nbt` | 同左 |
| YungsBetterOceanMonuments | `data/betteroceanmonuments/structure/start.nbt` | 同左 |
| YungsBetterStrongholds | `data/betterstrongholds/structure/starts/junction_lg.nbt` | 同左 |

**验收：**
- 数据包内出现以上文件；
- 文件与 jar 内原始 NBT 一致。

---

## Phase 3：构建与验证

### Task 3.1 构建

```bash
gradlew.bat build --offline --console=plain
gradlew.bat prepareClientRun --offline
```

**验收：**
- BUILD SUCCESSFUL。

### Task 3.2 jar 内容验证

- `jar tf` 检查：
  - `data/teleportwaypoint/datapacks/yung_structure_waypoints/pack.mcmeta`
  - 各 YUNG namespace 下的 NBT 文件
  - `CommonConfig.class`
  - `DatapackRegistration.class`

### Task 3.3 数据包界面验证

- 数据包列表显示“传送锚点：YUNG 结构兼容”；
- 默认启用行为符合 `defaultEnableYungStructureWaypoints`。

### Task 3.4 配置验证

- `common.toml` 自动生成并包含 `defaultEnableYungStructureWaypoints`。

### Task 3.5 游戏内验证（需 YUNG 模组）

- 安装 5 个 YUNG 模组 + YungsApi；
- 新建世界，启用数据包；
- 验证 5 个 YUNG 结构入口出现传送锚点；
- 每个结构固定 1 个，名称正确；
- Xaero 地图能显示锚点；
- 未安装 YUNG 模组时无副作用；
- 原有功能回归正常。

---

## 执行顺序建议

1. Phase 1（配置/注册/语言/目录）
2. Phase 2（复制 NBT）
3. Phase 3（构建 + 人工验证）
