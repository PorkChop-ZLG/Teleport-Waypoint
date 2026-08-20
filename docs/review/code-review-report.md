# Teleport Waypoint 代码审查报告

- 审查对象：`Teleport-Waypoint-ds_flash`
- 审查日期：2026-08-20
- 构建状态：`./gradlew build` 成功（使用工作区本地 `GRADLE_USER_HOME`）
- 上游源码基线：
  - Minecraft 1.21.1 / NeoForge 21.1.236 源码
  - Xaero Minimap / World Map / XaeroLib 反编译源码

---

## 严重级别说明

- **高**：会导致数据丢失、隐私泄露、明显功能错误或可被利用的问题。
- **中**：影响正确性/性能/健壮性，或可被绕过，但影响范围有限。
- **低**：代码质量、文档、边界情况或维护性问题。

---

## 一、高严重级别

### H-1 口袋锚点被破坏掉落时丢失 owner/name/uid

- **状态**：设计如此，不修复（用户确认）

- **位置**：
  - `src/main/resources/data/teleportwaypoint/loot_table/blocks/pocket_waypoint.json`
  - `src/main/java/com/zonlong/teleportwaypoint/block/PocketWaypointBlock.java`
  - `src/main/java/com/zonlong/teleportwaypoint/block/entity/WaypointBlockEntity.java`
- **现象**：
  - 口袋锚点的战利品表只是简单掉落 `teleportwaypoint:pocket_waypoint` 物品，没有 `copy_components` / `copy_nbt`。
  - `WaypointBlockEntity` 没有重写 `saveToItem` / `collectImplicitComponents` / `applyImplicitComponents`，`PocketWaypointBlock` 也没有重写 `getCloneItemStack` / `getDrops`。
  - 在 1.21.1 中，方块掉落数据必须通过 `copy_components` 从 `BLOCK_ENTITY` 复制组件；自定义 NBT 字段不会自动进入掉落物。
- **后果**：
  - 玩家用镐破坏口袋锚点后，掉落的物品是全新的“Pocket Waypoint”，`owner`、`name`、`uid` 全部丢失。
  - 重新放置后会生成新 UID、失去所有者绑定，与 README 中“口袋锚点可破坏、掉落自身、绑定放置者”的预期不符。
- **上游依据**：
  - `Block.getDrops()` 只把 `BlockEntity` 放入 loot context；
  - `CopyComponentsFunction.Source.BLOCK_ENTITY.get()` 返回 `blockEntity.collectComponents()`；
  - 默认 `BlockEntity.collectComponents()` 不包含自定义 NBT 字段。
- **建议修复**：
  - 在 `PocketWaypointBlock` 中重写 `getCloneItemStack` / `getDrops`，使用 `WaypointBlockEntity.saveToItem` 生成带 `BLOCK_ENTITY_DATA` 的物品；或
  - 将口袋锚点数据迁移到 DataComponent，并在战利品表中用 `copy_components` 复制；或
  - 至少重写 `getDrops` 手动调用 `BlockEntity.saveToItem` 并 `popResource`。

---

### H-2 所有客户端都会收到全部口袋锚点数据（隐私泄露）

- **状态**：暂不修复，后续对数据同步做更深入优化

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java`
    - `syncAllTo()` 约 172-192 行：登录时把 `WaypointRegistryData.getAll()` 全量发给每个玩家；
    - `broadcastAdd()` / `broadcastUpdate()` / `broadcastRemove()` 约 221-239 行：新增/更新/删除都广播给所有在线玩家。
- **现象**：
  - `WaypointSyncInfo` 包含所有口袋锚点的 UID、维度、坐标、名称。
  - 客户端虽然只在 Xaero/GUI 中“按激活状态”显示口袋锚点，但数据本身已经下发到所有客户端。
  - 修改过的客户端、抓包或内存读取都能拿到未激活口袋锚点的坐标。
- **后果**：
  - 与“口袋锚点未激活不显示，防止泄露个人基地坐标”的设计目标冲突，属于隐私泄露。
- **建议修复**：
  - 全量同步时只下发非口袋锚点；
  - 口袋锚点只发送给已激活该锚点的玩家（或所有者）；
  - 对应的 `AddWaypointPayload` / `UpdateWaypointPayload` / `RemoveWaypointPayload` 也要按可见性定向发送，而不是全局广播。

---

### H-3 Xaero 世界地图渲染器缺少 `shouldBeDimScaled() = false`，下界/末地坐标会偏移

- **状态**：已修复

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/TeleportWaypointWorldRenderer.java`
- **现象**：
  - `TeleportWaypointWorldRenderer` 没有重写 `shouldBeDimScaled()`。
  - 上游 `ElementRenderer.shouldBeDimScaled()` 默认返回 `true`，`MapElementRenderHandler` 会把 `reader.getRenderX/Z()` 再除以 `playerDimDiv`。
  - 本项目的 `TeleportWaypointWorldReader.getRenderX/Z()` 返回的是原始方块坐标 `+0.5`，没有像原生 `Waypoint.getRenderX()` 那样先除以 `dimDiv`。
- **后果**：
  - 在坐标缩放系数不为 1 的维度（如下界、末地）中，世界地图上的传送锚点图标会出现在错误位置。
- **上游依据**：
  - `xaero.map.element.render.ElementRenderer#shouldBeDimScaled()` 默认 `true`；
  - 原生 `xaero.map.mods.gui.WaypointRenderer#shouldBeDimScaled()` 返回 `false`；
  - 原生 `WaypointReader#getRenderX()` 内部先做 `dimDiv` 换算。
- **建议修复**：
  - 在 `TeleportWaypointWorldRenderer` 中增加：
    ```java
    @Override
    public boolean shouldBeDimScaled() {
        return false;
    }
    ```

---

### H-4 Xaero 小地图未按当前维度隔离，跨维度仍显示主世界锚点

- **状态**：已修复

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`
    - `sync()` 约 125-139 行。
- **现象**：
  - 玩家进入下界/末地后，主世界的传送锚点仍然出现在 Xaero 小地图（以及小地图提供的 world-in-waypoint 标签）上。
  - `sync()` 构建 `desired` 时遍历了 `ClientWaypointState.getWaypoints()` 的全部锚点；
  - 当前代码只在“锚点与玩家同维度”时才做 range 过滤，却没有对“不同维度”执行 `continue`，因此跨维度锚点会被无条件加入 `desired`。
- **上游依据**：
  - Xaero `WaypointCollector.collect()` 会把 `MinimapWorldManager.getCustomWaypoints()` 中的所有 custom waypoint 收集起来，不做维度过滤；
  - 因此只要本模组把主世界锚点注册进 Xaero custom waypoint，玩家在下界/末地也会被渲染。
- **后果**：
  - 维度隔离失效，跨维度显示错误位置的主世界锚点，破坏玩法与地图信息正确性。
- **建议修复**：
  - 在 `sync()` 中显式跳过与当前玩家维度不同的锚点：
    ```java
    Player player = Minecraft.getInstance().player;
    if (player == null) {
        continue; // 或清空 desired，避免无玩家时错误添加
    }
    if (!info.dimension().equals(player.level().dimension())) {
        continue;
    }
    ```
  - 保留现有 per-dimension custom key 与 `removeStale` 逻辑；玩家切换维度时 `shouldRefreshForRange()` 会触发重建，旧维度条目会被移除。
  - **优化（推荐）**：由于 Xaero 的 custom waypoint 本身是“全局、不感知维度”的，过滤后同一时刻只会注册当前维度锚点，因此可以把 `customKey()` 从“按维度拼接”简化为单一 mod key（如 `teleportwaypoint:minimap`）。这更符合 Xaero `MinimapWorldManager.getCustomWaypoints(ResourceLocation modId)` 的“modId 命名空间”语义，也顺带消除原报告 L-2 的隐患。

---

## 二、中严重级别

### M-1 Xaero 配置在每次客户端 tick 都被写入

- **状态**：已修复

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`
    - `sync()` 中无条件调用 `syncWaypointNameScaleToDistanceScale()`（约 92 行）；
    - `syncWaypointNameScaleToDistanceScale()` 内部每个 tick 执行 `profile.set(...)`。
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroWorldMapIntegration.java`
    - `tick()` 中每个 tick 调用 `syncConfigToXaero()`；
    - `syncConfigToXaero()` 内部每个 tick 执行 `profile.set(xaeroShowWaypoints, ...)`。
- **现象**：
  - 即使配置值和 revision 没有变化，也会反复写入 Xaero 配置。
- **后果**：
  - 不必要的配置写入/监听器触发；在低配机器或大量配置项时可能造成可感知开销。
- **建议修复**：
  - 缓存上次写入的值，仅在值变化时 `set`；
  - 将小地图 name scale 同步移入“revision 或配置发生变化”的分支，而不是每次 tick 都执行。

---

### M-2 传送冷却可通过退出/重进绕过

- **状态**：设计如此，不修复（退出重进已降低触发频率）

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/WaypointEvents.java`
    - `onPlayerLoggedOut()` 调用 `TeleportRateLimiter.remove(player.getUUID())`。
  - `src/main/java/com/zonlong/teleportwaypoint/core/TeleportRateLimiter.java`
- **现象**：
  - 冷却状态只保存在内存中，玩家登出即清除。
- **后果**：
  - 玩家传送后立刻断开重连，可绕过传送冷却。
- **建议修复**：
  - 登出时不移除冷却记录，或把上次传送时间持久化到玩家数据/附件中。

---

### M-3 自定义 SavedData 使用了 `DataFixTypes.SAVED_DATA_MAP_DATA`

- **状态**：已修复（DataFixTypes 改为 null，保持文件名/NBT 不变）

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/core/WaypointRegistryData.java`
    - `SavedData.Factory<>(..., DataFixTypes.SAVED_DATA_MAP_DATA)`
  - `src/main/java/com/zonlong/teleportwaypoint/core/PlayerWaypointData.java`
    - `SavedData.Factory<>(..., DataFixTypes.SAVED_DATA_MAP_DATA)`
- **现象**：
  - 自定义数据使用了原版地图数据的 datafixer 类型。
- **后果**：
  - 数据升级时可能对自定义 NBT 运行不匹配的 datafixer；虽然多数情况下可能无操作，但属于不安全的用法。
- **上游依据**：
  - `net.minecraft.world.level.saveddata.SavedData.Factory` 的 `DataFixTypes` 参数在 NeoForge 中可为 `null`，自定义数据应传 `null` 或使用专用 datafixer。
- **建议修复**：
  - 改为 `new SavedData.Factory<>(..., ..., null)` 或使用两参构造 `new SavedData.Factory<>(..., ...)`。

---

### M-4 SavedData 读取对损坏数据不健壮

- **状态**：已修复

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/core/PlayerWaypointData.java#read`
  - `src/main/java/com/zonlong/teleportwaypoint/core/WaypointRegistryData.java#read`
- **现象**：
  - `UUID.fromString(key)`、`ResourceLocation.parse(...)` 没有 try/catch；遇到损坏/手改存档会抛异常导致世界加载失败。
- **建议修复**：
  - 解析失败时跳过该条目并记录 warn，而不是让整个世界存档崩溃。

---

## 三、低严重级别

### L-1 Xaero 名称传入已解析文本而非原始翻译键

- **状态**：已修复

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`
    - `String displayName = info.displayName().getString();`
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/TeleportWaypointWorldReader.java`
    - `getMenuName()` / `getFilterName()` / 右键菜单中均使用 `displayName().getString()`。
- **现象**：
  - 普通传送锚点在 Xaero 中显示的是“已经翻译好的字符串”，而不是原始翻译键。
  - 切换语言后 Xaero 上的名字不会跟随更新；若口袋锚点名称恰好匹配某个 lang key，Xaero 的 `getLocalizedName()` 还会二次翻译。
- **建议修复**：
  - 普通锚点传给 Xaero 的 name 使用 `"teleportwaypoint.waypoint." + info.name()`；
  - 口袋锚点使用字面 `info.name()`。

---

### L-2 Xaero 小地图 customWaypoints key 使用维度子命名空间

- **状态**：已修复（随 H-4 改为单一 `MINIMAP_KEY`）

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java#customKey`
- **说明**：
  - 上游 `MinimapWorldManager.getCustomWaypoints(ResourceLocation)` 参数语义是“modId / 命名空间”，项目把它当“按维度分桶”使用。
  - 当前 Xaero 渲染逻辑遍历所有 custom waypoint map，因此功能上可用；但这不是 API 的标准用法，Xaero 升级后存在语义变化风险。
- **建议**：
  - 在代码中明确注释该设计；如有可能，改为单一 key + 全局唯一 ID，或继续保留但纳入回归验证。

---

### L-3 `TeleportWaypointTooltip` 依赖 `super.drawBox` 的内部偏移

- **状态**：暂不修复

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/TeleportWaypointTooltip.java`
- **说明**：
  - 通过 `-12` / `-10` 补偿 `super.drawBox()` 的内部偏移，属于对 Xaero 内部实现的脆弱依赖。
  - 在不同 GUI 缩放、多行文本、屏幕边缘时可能偏移不准确。
- **建议**：
  - 对照 Xaero `Tooltip` 基类实现做一次适配，或改用 Xaero 提供的定位 API。

---

### L-4 `WaypointTeleporter.findLanding` 不检查岩浆/虚空/世界边界

- **状态**：暂不修复

- **位置**：
  - `src/main/java/com/zonlong/teleportwaypoint/core/WaypointTeleporter.java`
- **说明**：
  - 只检查候选方块及其上方是否 `isSuffocating`，没有排除流体、虚空、世界边界。
  - 若锚点周围只有岩浆或虚空，玩家可能被传送到危险位置。
- **建议**：
  - 增加对安全落点（非流体、在世界边界内、有地面）的检查，或至少回退到锚点上方安全位置。

---

### L-5 README / 版本信息不一致

- **状态**：已修复

- **位置**：
  - `README.md`
- **现象**：
  - README 顶部写版本 `0.0.1`，`gradle.properties` / `neoforge.mods.toml` 为 `0.2.0`；
  - README 配置文件名仍写 `server.toml` / `client.toml`，实际为 `common.toml` / `xaero-minimap.toml` / `xaero-worldmap.toml`。
- **建议**：
  - 同步更新 README。

---

## 四、低风险观察（不一定要改）

- `RenameWaypointPayload` / `ActivatedWaypointInfo` / `WaypointSyncInfo` 使用无显式长度上限的 `STRING_UTF8`；默认上限 32767，服务端会在读取后校验长度，风险有限。
- `ModMenus` 从服务端 buffer 读取 `readUtf()` 未指定上限；该数据来自服务端，风险有限。
- `XaeroWorldMapIntegration` 向 Xaero 配置管理器注册自定义 option，存在与其他 mod 同 ID 冲突或 Xaero 版本升级后 API 变化的风险。
- `WaypointManager.syncAllTo` 的 `getAll()` 来自 `HashMap.values()`，分页顺序不固定，不影响正确性但不利于稳定复现。

---

## 五、审查结论

项目整体结构清晰，服务端权威校验、网络同步、Xaero 反射隔离、数据包注册等做得比较规范。

当前状态：

- **已修复**：H-3、H-4、M-1、M-3、M-4、L-1、L-2、L-5
- **设计如此，不修复**：H-1、M-2
- **暂不修复**：H-2、L-3、L-4

建议后续优先处理 **H-2**（数据同步隐私优化），其余暂缓项可根据实际需要再评估。
