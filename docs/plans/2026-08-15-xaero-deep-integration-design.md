# 传送锚点与 Xaero 地图深度联动 — 详细设计文档

**日期：** 2026-08-15
**状态：** 实现完成，构建与启动验证通过；待人工游戏内视觉验证
**目标版本：** Minecraft 1.21.1 / NeoForge 21.1.236+

## 实现与验证记录

- M1-M4 代码已落地。
- `gradlew build --offline` 通过。
- 有 Xaero 客户端启动成功，日志确认 World Map / Minimap 集成注册。
- 无 Xaero 客户端启动成功，未加载 Xaero 类。
- 修复 `ElementReader.isInteractable` 默认 false 导致锚点不可悬停/右键的问题。
- Xaero ConfigChannel 已只读，配置选项回退到本模组 `Config`。

---

## 1. 背景与目标

本模组（Teleport Waypoint）目前已有完整的“传送锚点 / 口袋锚点”玩法，但锚点只能通过游戏内 GUI 查看和传送。本文设计如何与闭源模组 **Xaero's Minimap**、**Xaero's World Map** 做深度联动：

- 在 Xaero 世界地图上显示全部传送锚点（含未激活 / 已激活两种状态）。
- 在 Xaero 小地图上显示传送锚点图标与名称。
- 悬停显示锚点名称与坐标。
- 点击已激活锚点弹出“传送”选项，并调用本模组自身的传送逻辑（禁止使用 Xaero 自带传送）。
- 使用 Xaero 配置系统提供“是否显示传送锚点 / 是否显示传送锚点名称”两个开关。
- 未安装 Xaero 时，本模组原有功能完全不受影响（可选联动）。

## 2. 现状分析

### 2.1 本模组现有结构

```
src/main/java/com/zonlong/teleportwaypoint/
├── core/
│   ├── WaypointRegistryData      # 全局锚点索引 SavedData
│   ├── PlayerWaypointData        # 玩家激活状态 SavedData
│   ├── WaypointManager           # 激活/注册/同步门面
│   └── WaypointTeleporter        # 传送落点解析 + 执行
├── network/                      # NeoForge Payload 网络
└── client/
    ├── ClientWaypointState       # 客户端已激活列表
    └── gui/                      # 游戏内 GUI
```

- 服务端权威：`WaypointRegistryData` 保存所有锚点（uid、维度、坐标、类型、名称）。
- `SyncActivatedWaypointsPayload` 只同步“当前玩家已激活的锚点”，客户端不知道未激活锚点。
- 传送目前要求：源锚点与目标锚点均已激活，且玩家站在源锚点 8 格内。

### 2.2 Xaero 反编译关键结论

本地 Xaero 文件为解压后的 class 目录 + jar：

- `D:\Minecraft\Xaero-Minimap`
- `D:\Minecraft\Xaero-Worldmap`
- `META-INF/jarjar/xaerolib-neoforge-1.21.1-1.0.45.jar`

通过 `javap` 分析得到以下可扩展点：

#### 世界地图（World Map）

- `xaero.map.WorldMap.mapElementRenderHandler` 是 `public static` 字段，类型 `MapElementRenderHandler`。
- `MapElementRenderHandler.add(ElementRenderer<?,?,?>)` 允许第三方运行时注册自定义地图元素。
- 元素渲染框架：
  - `ElementRenderer<E,C,R>`：自定义元素渲染器，需实现 `preRender / postRender / renderElement / renderElementShadow / shouldRender`。
  - `ElementReader<E,C,R>`：负责坐标、碰撞盒、隐藏、右键菜单、Tooltip。
  - `ElementRenderProvider<E,C>`：负责迭代元素。
  - `RightClickOption`：可自定义右键菜单项。
- 因此**不修改 Xaero jar** 即可注册我们自己的地图元素，并完全控制右键菜单（只保留“传送”）。

#### 小地图（Minimap）

- `MinimapSession.getWorldManager()` 返回 `MinimapWorldManager`。
- `MinimapWorldManager.getCustomWaypoints(ResourceLocation)` 返回 `Int2ObjectMap<Waypoint>`。
- `WaypointCollector.collect()` 会收集这些 custom waypoints 并交给小地图渲染。
- `xaero.common.minimap.waypoints.Waypoint` 支持名称、颜色、符号（symbol）、坐标，天然支持悬停显示名称。
- 因此小地图可以采用“向 Xaero custom waypoints 注入原生 Waypoint 对象”的方式，工作量小且稳定。

#### 配置系统（Xaero lib）

- `xaero.lib.common.config.channel.ConfigChannel` + `ConfigOptionManager` + `BooleanConfigOption.Builder` 可注册布尔配置。
- 风险：`ConfigOptionManager` 可能在 Xaero 加载阶段 `freeze()`，第三方若注册太晚会失败。
- 备选：使用本模组自身 `ModConfigSpec`，或通过 Mixin 在 Xaero 配置 channel 冻结前注入。

## 3. 设计目标与非目标

### 3.1 目标

1. 服务端向客户端同步**全部锚点**（而不只是已激活）。
2. 世界地图自定义元素显示全部锚点：
   - 未激活：灰色图标，仅名称 + 坐标，点击无效果。
   - 已激活：彩色图标，点击弹“传送”选项。
3. 小地图显示锚点图标与名称，状态颜色与激活状态一致。
4. 地图传送：允许玩家在任意位置点击已激活锚点直接传送；服务端严格校验。
5. 可选联动：Xaero 不存在时，不加载任何 Xaero 相关类。
6. 提供两个配置项：显示锚点、显示锚点名称。

### 3.2 非目标（本期不做）

- 不实现 Xaero 地图的“传送点编辑 / 删除 / 分享”等额外功能。
- 不做锚点图标纹理的精细美术设计（先用颜色 + 字符符号，必要时再扩展自定义纹理）。
- 不做服务器端 Xaero 协议集成（Xaero 服务端 waypoint 同步不走）。
- 不修改 Xaero 的任何 class / jar。

## 4. 总体架构

```
服务端
  WaypointRegistryData / PlayerWaypointData
        │
        ├─ SyncAllWaypointsPayload（新增，S→C，全量锚点）
        └─ SyncActivatedWaypointsPayload（已有，S→C，激活状态）

客户端
  ClientWaypointState（扩展：全部锚点 + 激活集合）
        │
        ├─ Xaero World Map 自定义 ElementRenderer（仅装 Xaero 时反射加载）
        └─ Xaero Minimap customWaypoints 注入（仅装 Xaero 时反射加载）

传送
  世界地图右键“传送”
        → MapTeleportRequestPayload（新增，C→S，只带 targetUid）
        → 服务端校验激活 + 目标存在
        → WaypointTeleporter.teleportTo(player, targetUid)
```

### 4.1 可选联动加载策略

- 所有直接引用 Xaero 类的代码放在 `com.zonlong.teleportwaypoint.client.xaero` 包。
- 主客户端事件类只通过 `ModList.get().isLoaded("xaeroworldmap")` / `isLoaded("xaerominimap")` 判断，再用 `Class.forName(...)` 反射调用 `XaeroIntegration.init()`。
- 这样即使 Xaero 未安装，JVM 也不会加载引用 Xaero 类型的类，避免 `NoClassDefFoundError`。

### 4.2 构建依赖调整

当前 `build.gradle`：

```groovy
implementation "curse.maven:xaeros-minimap-263420:7412188"
implementation "curse.maven:xaeros-world-map-317780:7416972"
```

改为：

```groovy
compileOnly "curse.maven:xaeros-minimap-263420:7412188"
compileOnly "curse.maven:xaeros-world-map-317780:7416972"
localRuntime "curse.maven:xaeros-minimap-263420:7412188"
localRuntime "curse.maven:xaeros-world-map-317780:7416972"
```

- `compileOnly`：编译期可访问 Xaero API，但不会进入运行时 classpath。
- `localRuntime`：仅开发环境运行客户端时带上 Xaero，方便测试。
- 生产环境 jar 不打包 Xaero，配合 `neoforge.mods.toml` 已有的 optional dependency 声明。

## 5. 数据同步设计

### 5.1 新增数据结构

```java
// 网络层
public record WaypointSyncInfo(
    UUID uid,
    ResourceKey<Level> dimension,
    BlockPos pos,
    boolean pocket,
    String name
) { /* StreamCodec */ }

public record SyncAllWaypointsPayload(List<WaypointSyncInfo> waypoints) { /* StreamCodec */ }

public record MapTeleportRequestPayload(UUID target) { /* StreamCodec */ }
```

### 5.2 服务端发送时机

| 时机 | 动作 |
|---|---|
| 玩家登录 | `WaypointManager.syncAllTo(player)` + 原有 `syncTo(player)` |
| 锚点放置 / 破坏 / 改名 | `WaypointManager.broadcastAll()` 向所有在线玩家发送全量列表 |
| 玩家激活 / 取消激活 | 仍走原有 `SyncActivatedWaypointsPayload`，不重复发送全量 |

> 说明：`WaypointRegistryData` 是 SavedData，首次读取即从磁盘加载全部已保存锚点，因此登录时即可拿到全量数据，不依赖区块加载。

### 5.3 客户端状态扩展

```java
public final class ClientWaypointState {
    private static Map<UUID, ClientWaypointInfo> waypoints = Map.of();
    private static Set<UUID> activated = Set.of();

    public static boolean isActivated(UUID uid);
    public static List<ClientWaypointInfo> getWaypointsIn(ResourceKey<Level> dim);
    public static void setAllWaypoints(List<WaypointSyncInfo> infos);
    public static void removeWaypoint(UUID uid);
    // ...
}
```

- `ClientWaypointInfo`：uid、dimension、pos、pocket、name。
- 激活状态继续用 `Set<UUID>`，O(1) 查询，避免现有 List 线性查找的性能问题。

## 6. Xaero 世界地图集成

### 6.1 自定义元素类型

```java
public final class TeleportWaypointElement {
    public final UUID uid;
    public final ResourceKey<Level> dimension;
    public final int x, y, z;
    public final String name;
    public final boolean pocket;
    public final boolean activated;
}
```

### 6.2 上下文

```java
public final class TeleportWaypointContext {
    public ResourceKey<Level> mapDimension;
    public boolean showWaypoints = true;
    public boolean showNames = true;
}
```

### 6.3 Provider

- `begin()`：从 `ClientWaypointState` 取 `mapDimension` 对应锚点，过滤 `showWaypoints`。
- `hasNext() / getNext() / end()`：标准迭代。

### 6.4 Reader

实现 `ElementReader<TeleportWaypointElement, TeleportWaypointContext, TeleportWaypointRenderer>`：

- `isHidden()`：由 Provider 已过滤，返回 `false`。
- `getRenderX/Z()`：返回 `x + 0.5` / `z + 0.5`。
- 交互/渲染盒：以图标中心生成约 16×16 的 box。
- `getMenuName()` / `getFilterName()`：返回锚点显示名。
- `getRightClickOptions()`：
  - 未激活：返回空列表（`isRightClickValid` 为 `false`）。
  - 已激活：返回仅含“传送”的 `TeleportRightClickOption`。
- `getTooltip()`：返回“名称 + X/Y/Z 坐标”的 Xaero Tooltip（或标准 `Component`）。
- `isRightClickValid()`：`element.activated`。

### 6.5 右键“传送”选项

```java
class TeleportRightClickOption extends RightClickOption {
    private final UUID target;
    @Override public void onAction(Screen screen) {
        PacketDistributor.sendToServer(new MapTeleportRequestPayload(target));
    }
}
```

- 使用 Xaero 内置 `RightClickOption` 组件，符合“优先使用 Xaero 内置 lib UI 组件”的要求。
- 点击后关闭地图并发送我们的传送请求。

### 6.6 Renderer

实现 `ElementRenderer<TeleportWaypointElement, TeleportWaypointContext, TeleportWaypointRenderer>`：

- `shouldRender()`：仅 `WORLD_MAP`（如需要可包含 `WORLD_MAP_MENU`）。
- `preRender / postRender`：同步 `context.mapDimension` 等信息。
- `renderElement()`：
  - 用 `GuiGraphics` 绘制小方块/圆形作为图标；
  - 未激活：灰色（如 `0xFF9E9E9E`）；
  - 已激活：传送锚点青色、口袋锚点绿色；
  - 若 `showNames`，在图标旁绘制名称。
- `getOrder()`：建议 `201`（排在 Xaero 原生 waypoint 之后，避免遮挡）。

### 6.7 注册时机

- 在客户端 Tick 或 `RenderLevelStageEvent` 中惰性检测：
  - `ModList.get().isLoaded("xaeroworldmap")`
  - `WorldMap.INSTANCE != null`
  - `WorldMap.mapElementRenderHandler != null`
- 满足后调用 `mapElementRenderHandler.add(renderer)`，并置 `registered = true` 防止重复添加。

## 7. Xaero 小地图集成

### 7.1 注入方式

使用 `MinimapWorldManager.getCustomWaypoints(dimension).put(id, waypoint)`：

```java
int id = idMap.computeIfAbsent(uid, k -> nextId++);
Waypoint wp = new Waypoint(x, y, z, displayName, symbol, color, 0, false);
wp.setTemporary(true); // 不写入用户 waypoint 文件
minimapWorldManager.getCustomWaypoints(dim).put(id, wp);
```

- `symbol`：传送锚点用 `"W"`，口袋锚点用 `"P"`。
- `color`：未激活灰色；已激活按类型使用青色/绿色。
- 客户端收到激活状态变化时，更新对应 Waypoint 的 `color`。
- 锚点移除时 `remove(id)` 并清理 idMap。

### 7.2 为什么小地图不直接复用世界地图 ElementRenderer

- Xaero 小地图与 World Map 的元素框架是两套独立 API。
- 注入原生 `Waypoint` 可以免费获得小地图的悬停名称、距离过滤、图标绘制，代码量最小。
- 小地图需求不包含右键“传送”菜单，因此原生 Waypoint 足够。

## 8. 配置系统设计

### 8.1 目标配置项

| 配置 ID | 默认值 | 含义 |
|---|---|---|
| `teleportwaypoint.show_waypoints` | `true` | 是否在地图上显示传送锚点 |
| `teleportwaypoint.show_waypoint_names` | `true` | 是否显示锚点名称 |

### 8.2 优先方案：注册进 Xaero ConfigChannel

- 使用 `xaero.lib.common.config.option.BooleanConfigOption.Builder.begin()` 创建选项。
- 在 Xaero 配置 channel 冻结前，通过 `ConfigOptionManager.register(...)` 注册。
- 需要进一步确认 Xaero `ConfigChannel` 的冻结时机；若能在 `WorldMap.loadClient()` 早期通过 Mixin 注入则采用。

### 8.3 备选方案

- 如果 Xaero 配置 channel 已冻结，回退到本模组 `Config.java`（`ModConfigSpec`）提供同样两个开关。
- 同时尝试在 Xaero 设置界面注册入口（若 API 允许）。
- 渲染层统一读取“配置提供者”接口，避免业务代码依赖具体配置来源。

## 9. 传送设计（地图传送）

### 9.1 新服务端逻辑

新增 `WaypointTeleporter.teleportTo(ServerPlayer player, UUID target)`：

1. 校验 `WaypointManager.isActivated(player, target)`。
2. 从 `WaypointRegistryData` 取目标记录，不存在则清理并提示。
3. 目标维度存在，force-load 目标 chunk。
4. 目标方块实体仍存在且 uid 匹配，否则清理并提示。
5. 复用现有 `findLanding()` 计算落点。
6. 执行 `player.teleportTo(...)`、传送音效、粒子。

> 该逻辑是现有 `teleport(source, target)` 的“无源锚点版”，仍属于本模组自身传送逻辑，不调用 Xaero 传送。

### 9.2 网络

```java
// C→S
public record MapTeleportRequestPayload(UUID target) implements CustomPacketPayload { ... }
```

服务端 handler：

```java
if (context.player() instanceof ServerPlayer serverPlayer) {
    WaypointTeleporter.teleportTo(serverPlayer, payload.target());
}
```

### 9.3 安全

- 所有校验在服务端完成。
- 客户端只能提交目标 uid，不能提交坐标 / 维度。
- 未激活锚点不会收到“传送”右键项，即使伪造请求也会被服务端拒绝。
- 跨维度传送沿用现有 force-load chunk 逻辑，避免未加载区块问题。

## 10. 性能设计

- 登录全量同步一次；运行中仅锚点增删改时广播全量（锚点数量通常较小）。
- 客户端使用 `HashMap<UUID, ...>` 与 `Set<UUID>`，查询 O(1)。
- 世界地图 Provider 只迭代当前维度锚点；渲染图标数量有限。
- 小地图 customWaypoints 按维度分桶，每次变更只更新对应锚点。
- 如未来锚点数量极大，可再改为分维度/分区块同步，本期不做。

## 11. 实施步骤（里程碑）

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M1 | 数据同步 + 客户端状态扩展 | 登录/放置/破坏/改名后客户端能拿到全量锚点；未装 Xaero 原功能正常 |
| M2 | 世界地图自定义元素 + 地图传送 | 世界地图显示锚点、状态颜色、悬停名称、右键“传送”可传送 |
| M3 | 小地图注入 | 小地图显示锚点图标与名称，激活状态颜色正确 |
| M4 | 配置系统 | 两个配置项生效，并尽量接入 Xaero 配置界面 |
| M5 | 可选联动加固 + 文档 | 无 Xaero 可启动；有 Xaero 正常联动；更新 README |

## 12. 测试与验收

### 12.1 无 Xaero 环境

- 模组正常启动，无 `NoClassDefFoundError`。
- 游戏内放置、激活、改名、删除、传送全部正常。

### 12.2 有 Xaero 环境

- 世界地图显示未激活（灰）与已激活（彩）锚点。
- 悬停显示名称与坐标。
- 点击未激活无反应；点击已激活弹出“传送”，点击后传送到目标锚点。
- 小地图显示对应图标与名称，颜色随激活状态变化。
- 关闭“显示传送锚点”后图标消失；关闭“显示名称”后名称不显示。

### 12.3 多人 / 安全

- 玩家 A 激活、玩家 B 未激活时，双方看到的状态不同。
- 伪造 `MapTeleportRequestPayload` 无法传送未激活锚点。

## 13. 风险与备选

| 风险 | 影响 | 备选 |
|---|---|---|
| Xaero 类为内部 API，版本升级可能变动 | 联动失效 | 锁定当前版本；封装隔离层，升级时只改 `client.xaero` 包 |
| Xaero ConfigChannel 已冻结，无法注册配置 | 配置无法进 Xaero 界面 | 回退到本模组配置；或 Mixin 提前注入 |
| 自定义图标纹理需求高 | 当前仅颜色+字符 | 后续扩展 `WaypointSymbolCreator` 或自定义纹理渲染 |
| 世界地图 ElementRenderer API 复杂 | 开发成本高 | 已确认 `ElementRenderer` 直接继承可行；必要时参考 `WaypointRenderer` 字节码 |

## 14. 开放问题

1. Xaero 配置 channel 冻结时机需在实现 M4 时用运行时验证确认。
2. 世界地图图标是否必须为图片纹理，还是颜色 + 字符符号可接受（当前按后者设计）。
3. 小地图是否也需要点击“传送”（本期按不需要设计）。
