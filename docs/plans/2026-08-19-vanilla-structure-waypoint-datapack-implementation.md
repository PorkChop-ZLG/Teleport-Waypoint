# 传送锚点：原版结构覆盖可选数据包 实施计划

**日期：** 2026-08-19
**状态：** Ready for execution
**依据：** `docs/plans/2026-08-19-vanilla-structure-waypoint-datapack-design.md`

---

## Phase 0：配置系统迁移

### Task 0.1 新增 `CommonConfig`

- 新建 `src/main/java/com/zonlong/teleportwaypoint/config/CommonConfig.java`
- 选项：
  - `defaultEnableStructureWaypoints`：boolean，默认 `true`
  - `teleportCooldownTicks`：int，默认 `20`，范围 `0 ~ 72000`
- 使用 `ModConfigSpec.Builder`，并设置完整 translation 键。

**验收：**
- `CommonConfig.SPEC` 可编译；
- 两个选项存在且默认值正确。

### Task 0.2 删除旧配置类

- 删除 `src/main/java/com/zonlong/teleportwaypoint/config/ClientConfig.java`
- 删除 `src/main/java/com/zonlong/teleportwaypoint/config/ServerConfig.java`

**验收：**
- 两个文件已删除；
- 没有残留 import 引用。

### Task 0.3 更新 `TeleportWaypoint`

- 移除 `ClientConfig` / `ServerConfig` 的 import 与注册；
- 注册 `CommonConfig.SPEC`：
  ```java
  modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC, "teleportwaypoint/common.toml");
  ```
- 注册 `DatapackRegistration`：
  ```java
  modEventBus.addListener(DatapackRegistration::onAddPackFinders);
  ```

**验收：**
- 编译通过；
- `TeleportWaypoint` 只注册 `COMMON` 与现有 Xaero 客户端配置。

### Task 0.4 更新 `TeleportRateLimiter`

- 将 `ServerConfig.TELEPORT_COOLDOWN_TICKS` 改为 `CommonConfig.TELEPORT_COOLDOWN_TICKS`

**验收：**
- 编译通过；
- 传送冷却读取 Common 配置。

---

## Phase 1：数据包注册

### Task 1.1 新增 `DatapackRegistration`

- 新建 `src/main/java/com/zonlong/teleportwaypoint/datapack/DatapackRegistration.java`
- 方法：`public static void onAddPackFinders(AddPackFindersEvent event)`
- 逻辑：
  - 仅处理 `PackType.SERVER_DATA`
  - 读取 `CommonConfig.DEFAULT_ENABLE_STRUCTURE_WAYPOINTS.get()`
  - `true` → `PackSource.BUILT_IN`
  - `false` → `PackSource.FEATURE`
  - 调用 `event.addPackFinders(...)`
- ResourceLocation：
  ```java
  ResourceLocation.fromNamespaceAndPath(
      TeleportWaypoint.MODID,
      "data/teleportwaypoint/datapacks/vanilla_structure_waypoints"
  )
  ```
- 名称：`Component.translatable("datapack.teleportwaypoint.vanilla_structure_waypoints.name")`
- `alwaysActive = false`
- `Pack.Position.TOP`

**验收：**
- 编译通过；
- 数据包出现在服务端数据包列表中。

### Task 1.2 创建数据包基础目录

- 新建：
  ```
  src/main/resources/data/teleportwaypoint/datapacks/vanilla_structure_waypoints/
  ```
- 新建 `pack.mcmeta`：
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

**验收：**
- 目录结构存在；
- `pack.mcmeta` JSON 合法。

---

## Phase 2：语言文件

### Task 2.1 更新 `en_us.json` / `zh_cn.json`

新增键：

- 数据包：
  - `datapack.teleportwaypoint.vanilla_structure_waypoints.name`
  - `datapack.teleportwaypoint.vanilla_structure_waypoints.description`
- Common 配置：
  - `teleportwaypoint.configuration.section.teleportwaypoint.common.toml`
  - `teleportwaypoint.configuration.section.teleportwaypoint.common.toml.title`
  - `teleportwaypoint.configuration.common.defaultEnableStructureWaypoints`
  - `teleportwaypoint.configuration.common.defaultEnableStructureWaypoints.tooltip`
  - `teleportwaypoint.configuration.common.teleportCooldownTicks`
  - `teleportwaypoint.configuration.common.teleportCooldownTicks.tooltip`
- 结构锚点名称：
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

删除键：

- `teleportwaypoint.configuration.client.placeholder`
- `teleportwaypoint.configuration.client.placeholder.tooltip`
- `teleportwaypoint.configuration.section.teleportwaypoint.client.toml`
- `teleportwaypoint.configuration.section.teleportwaypoint.client.toml.title`

**验收：**
- 两个语言文件 JSON 合法；
- 中英文翻译完整；
- 无残留 client placeholder 键。

---

## Phase 3：迁移试炼密室 NBT

### Task 3.1 移动现有 NBT 覆盖

- 将现有：
  ```
  src/main/resources/data/minecraft/structure/trial_chambers/
  ```
  移动到：
  ```
  src/main/resources/data/teleportwaypoint/datapacks/vanilla_structure_waypoints/data/minecraft/structure/trial_chambers/
  ```

**验收：**
- 模组默认资源中不再有 `data/minecraft/structure/trial_chambers/`；
- 数据包内包含完整试炼密室覆盖文件。

---

## Phase 4：新增其他结构 NBT 覆盖

### Task 4.1 结构覆盖调研

- 对以下结构逐个确认“标志性位置”对应的原版 NBT 文件：
  - `ancient_city`
  - `bastion_remnant`
  - `end_city`
  - `nether_fortress`
  - `woodland_mansion`
  - `ocean_monument`
  - `stronghold`
  - `desert_pyramid`
  - `igloo`
  - `jungle_temple`
- 输出每个结构的：
  - 覆盖文件路径
  - 锚点放置坐标/偏移
  - 锚点 `id`（与翻译键一致）
  - 是否已有现成 BlockBench/NBT 可复用

**验收：**
- 每个结构有明确的 NBT 文件与放置方案。

### Task 4.2 逐个生成 NBT 覆盖

- 参考试炼密室现有 NBT 格式，为每个结构创建覆盖文件；
- 每个结构实例固定 1 个传送锚点；
- 锚点方块实体 NBT 写入对应 `id`。

**验收：**
- 每个结构的数据包文件存在；
- 结构生成后可看到 1 个传送锚点；
- 锚点名称正确。

---

## Phase 5：构建与验证

### Task 5.1 构建

```bash
gradlew.bat build --offline --console=plain
gradlew.bat prepareClientRun --offline
```

**验收：**
- BUILD SUCCESSFUL；
- 无编译错误。

### Task 5.2 数据包界面验证

- 启动开发客户端/服务端；
- 数据包列表显示：
  - 名称：传送锚点：原版结构覆盖
  - 描述：为Minecraft原版的部分结构添加传送锚点
- `defaultEnableStructureWaypoints = true` 时新世界默认启用；
- `false` 时新世界默认不启用，可手动开启。

### Task 5.3 配置验证

- `config/teleportwaypoint/common.toml` 自动生成；
- 包含 `defaultEnableStructureWaypoints` 与 `teleportCooldownTicks`；
- 进入存档前可修改；
- 修改 `teleportCooldownTicks` 后传送冷却生效。

### Task 5.4 结构生成验证

- 新建世界，启用数据包；
- 逐一验证 11 个结构出现传送锚点；
- 每个结构实例固定 1 个；
- 锚点名称正确。

### Task 5.5 Xaero 联动回归

- 安装 Xaero 世界地图/小地图；
- 地图上能显示结构锚点；
- 停用数据包后新生成结构不再出现新锚点。

### Task 5.6 原有功能回归

- 未启用数据包时原版结构不变；
- 传送、激活、改名、删除、跨维度、限频等原有功能正常。

---

## 执行顺序建议

1. Phase 0 → Phase 1 → Phase 2 → Phase 3（先让框架可运行）
2. Phase 4（内容制作，可分批：先 2~3 个结构验证，再补齐其余）
3. Phase 5（全量验证）
