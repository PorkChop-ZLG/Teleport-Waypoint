# 传送锚点（Teleport Waypoint）实施计划

**日期：** 2026-08-14
**状态：** 待批准（Pending Approval）
**目标：** 按已批准设计文档 `2026-08-14-teleport-waypoint-design.md` 实现模组本体（不含 Xaero 联动与模型）。
**架构：** NeoForge 原生自建——方块实体承载名字，独立 `SavedData`（全局索引 + per-player 激活），原生 Payload API 网络。
**方案：** A（已批准）。

---

## 0. NeoForge 1.21.1 API 参考（防写错速查）

> 已逐一核对 `neoforge-21.1.236-sources.jar`，以下为确认后的正确签名。

| 用途 | 正确写法 |
|---|---|
| 不可破坏方块 | `BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F)` |
| 方块实体保存 | `protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)`，**必须** `super.saveAdditional(tag, registries)` |
| 方块实体加载 | `protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)`，**必须** `super.loadAdditional(tag, registries)` |
| 客户端同步 tag | `public CompoundTag getUpdateTag(HolderLookup.Provider registries)` |
| 方块实体类型 | `BlockEntityType.Builder.of(WaypointBlockEntity::new, blocks...).build(null)` |
| SavedData 获取 | `server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Ctor::new, Ctor::read, DataFixTypes.SAVED_DATA_MAP_DATA), DATA_NAME)`（1.21.1 返回类型为 `DimensionDataStorage`） |
| SavedData 保存 | `public abstract CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)` + `setDirty()` |
| Payload 类型 | `new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "name"))`（⚠️ 勿用 `createType`，它落在 minecraft 命名空间） |
| Payload codec | `StreamCodec.composite(...)` 或 `StreamCodec.ofMember(enc, dec)` |
| Payload 注册 | `event.registrar("1").playToClient(type, codec, handler)` / `.playToServer(type, codec, handler)` |
| Payload 发送 | `PacketDistributor.sendToServer(payload)` / `PacketDistributor.sendToPlayer(player, payload)` |
| Payload 处理 | `IPayloadHandler.handle(payload, context)`，`context.player()` 取玩家 |
| 菜单构造 | `AbstractContainerMenu(MenuType<?>, int containerId)` |
| 菜单额外数据 | `IContainerFactory.create(windowId, inv, RegistryFriendlyByteBuf data)` + `player.openMenu(provider, buf -> buf.writeBlockPos(pos))` |
| 传送 | `ServerPlayer.teleportTo(ServerLevel, double x, double y, double z, Set.of(), float yRot, float xRot)`（`RelativeMovement` 在 `net.minecraft.world.entity`） |
| 文本 | `Component.literal(String)` / `Component.translatable(String, Object...)` |

---

## Task 1: 清理模板示例代码

**文件：**
- 修改：`src/main/java/com/zonlong/teleportwaypoint/TeleportWaypoint.java`
- 修改：`src/main/java/com/zonlong/teleportwaypoint/Config.java`
- 修改：`src/main/resources/assets/teleportwaypoint/lang/en_us.json`

**步骤：**
1. `TeleportWaypoint.java`：删除 `EXAMPLE_BLOCK`、`EXAMPLE_BLOCK_ITEM`、`EXAMPLE_ITEM`、`EXAMPLE_TAB`、`CREATIVE_MODE_TABS`、`addCreative()`、`commonSetup` 里的示例日志；保留 `MODID`、`LOGGER`、构造器骨架。
2. `Config.java`：清空示例项（`LOG_DIRT_BLOCK`/`MAGIC_NUMBER` 等），仅保留空的 `SPEC` 骨架，留待后续按需加配置。
3. `en_us.json`：删除 example 相关条目。

**验证：** `gradlew.bat compileJava` 编译通过。

---

## Task 2: 方块实体 WaypointBlockEntity

**文件：**
- 创建：`src/main/java/com/zonlong/teleportwaypoint/block/entity/WaypointBlockEntity.java`

**步骤：**
1. 继承 `BlockEntity`，构造 `(BlockPos, BlockState)`。
2. 字段：`UUID uid`、`String id`（传送锚点）、`String name`（口袋锚点）、`UUID owner`。
3. `saveAdditional` / `loadAdditional` 写读 `uid`（`NbtUtils.createUUID/loadUUID`）、`id`、`name`、`owner`；均先调 `super`。
4. `getUpdateTag` 返回含上述字段的 tag（供客户端同步）。
5. `onLoad()`：服务端且 `uid == null` 时生成 `UUID.randomUUID()` 并 `setChanged()`。
6. 静态方法 `getDisplayName(BlockState, WaypointBlockEntity)`：传送锚点返回 `Component.translatable("teleportwaypoint.waypoint." + id)`，口袋锚点返回 `Component.literal(name)`。
7. 静态方法 `isValidId(String)`：校验 `[a-z0-9]+`。

**验证：** `gradlew.bat compileJava` 编译通过。

---

## Task 3: 方块 WaypointBlock + PocketWaypointBlock

**文件：**
- 创建：`src/main/java/com/zonlong/teleportwaypoint/block/WaypointBlock.java`
- 创建：`src/main/java/com/zonlong/teleportwaypoint/block/PocketWaypointBlock.java`

**步骤：**
1. `WaypointBlock`：继承 `BaseEntityBlock`；`Properties.of().strength(-1.0F, 3600000.0F)`；`newBlockEntity` 返回 `WaypointBlockEntity`。
2. `PocketWaypointBlock`：继承 `BaseEntityBlock`；正常硬度；`newBlockEntity` 返回 `WaypointBlockEntity`；`onRemove`/掉落逻辑（掉落自身）。
3. 两者 `use()`：服务端查 `PlayerWaypointData` 是否已解锁该 uid → 未解锁则激活；已解锁则 `openMenu`（Task 9 完成后接入；本任务先留 TODO 或空实现）。

**验证：** `gradlew.bat compileJava` 编译通过。

---

## Task 4: 注册 ModBlocks / ModBlockEntities / ModItems

**文件：**
- 创建：`src/main/java/com/zonlong/teleportwaypoint/block/ModBlocks.java`
- 创建：`src/main/java/com/zonlong/teleportwaypoint/block/entity/ModBlockEntities.java`
- 创建：`src/main/java/com/zonlong/teleportwaypoint/item/ModItems.java`
- 修改：`src/main/java/com/zonlong/teleportwaypoint/TeleportWaypoint.java`（注册到总线）

**步骤：**
1. `ModBlocks`：`DeferredRegister.Blocks`，注册 `waypoint`、`pocket_waypoint`。
2. `ModBlockEntities`：`DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID)`，`BlockEntityType.Builder.of(WaypointBlockEntity::new, ModBlocks.WAYPOINT.get(), ModBlocks.POCKET_WAYPOINT.get()).build(null)`。
3. `ModItems`：口袋锚点 `BlockItem`（`registerSimpleBlockItem` 或 `register`）。
4. 主类构造器：注册 `BLOCKS`、`BLOCK_ENTITIES`、`ITEMS`。

**验证：** `gradlew.bat compileJava` 编译通过。

---

## Task 5: 数据记录 + SavedData（全局索引 + per-player 激活）

**文件：**
- 创建：`src/main/java/com/zonlong/teleportwaypoint/core/WaypointRecord.java`
- 创建：`src/main/java/com/zonlong/teleportwaypoint/core/WaypointRegistryData.java`
- 创建：`src/main/java/com/zonlong/teleportwaypoint/core/PlayerWaypointData.java`

**步骤：**
1. `WaypointRecord`：record `(UUID uid, ResourceKey<Level> dimension, BlockPos pos, boolean pocket)`。
2. `WaypointRegistryData extends SavedData`：`Map<UUID, WaypointRecord>`；`save/read`；静态 `get(MinecraftServer)` 用 `DimensionDataStorage.computeIfAbsent`；`register/unregister/get`。
3. `PlayerWaypointData extends SavedData`：`Map<UUID /*player*/, Set<UUID> /*waypoint*/>`；`activate/isActivated/deactivate/getActivated`；`save/read`；静态 `get(MinecraftServer)`。

**验证：** `gradlew.bat compileJava` 编译通过。

---

## Task 6: WaypointManager 门面

**文件：**
- 创建：`src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java`

**步骤：**
1. 统一入口：`register(WaypointBlockEntity)`、`unregister`、`activate(ServerPlayer, UUID)`、`isActivated(Player, UUID)`、`getActivated(Player)`、`getRecord(UUID)`。
2. 持有/读写两个 SavedData；激活后向客户端发 `SyncActivatedWaypointsPayload`（Task 7 接入）。

**验证：** `gradlew.bat compileJava` 编译通过。

---

## Task 7: 网络 Payload

**文件：**
- 创建：`src/main/java/com/zonlong/teleportwaypoint/network/ModNetwork.java`
- 创建：`.../network/SyncActivatedWaypointsPayload.java`
- 创建：`.../network/TeleportRequestPayload.java`
- 创建：`.../network/RenameWaypointPayload.java`
- 修改：主类/客户端类（注册事件）

**步骤：**
1. `ModNetwork`：`@EventBusSubscriber(modid, bus = MOD)` 订阅 `RegisterPayloadHandlersEvent`，`registrar("1")` 注册三类 payload（`playToClient` / `playToServer`）。
2. `SyncActivatedWaypointsPayload`：`List<UUID>`，S→C，客户端更新本地激活集合。
3. `TeleportRequestPayload`：`UUID source, UUID target`，C→S，服务端校验并执行传送。
4. `RenameWaypointPayload`：`BlockPos pos, String text`，C→S，服务端校验权限后改名。
5. 每个 payload 用 `StreamCodec.composite(...)`；⚠️ `Type` 用 `ResourceLocation.fromNamespaceAndPath(MODID, ...)`。

**验证：** `gradlew.bat compileJava` 编译通过。

---

## Task 8: 传送 WaypointTeleporter

**文件：**
- 创建：`src/main/java/com/zonlong/teleportwaypoint/core/WaypointTeleporter.java`

**步骤：**
1. `resolveDestination(ServerLevel, WaypointRecord)`：按设计 6.5 节落点算法（FACING 优先，依次东/西/南/北找可站立方向，中心对齐）。
2. `teleport(ServerPlayer, WaypointRecord)`：解析目标维度、校验方块实体仍有效，`teleportTo(...)`。
3. 失败路径返回错误 `Component`。

**验证：** `gradlew.bat compileJava` 编译通过。

---

## Task 9: 菜单 + 屏幕（GUI）

**文件：**
- 创建：`src/main/java/com/zonlong/teleportwaypoint/menu/ModMenus.java`
- 创建：`.../menu/WaypointMenu.java`
- 创建：`.../menu/PocketWaypointMenu.java`
- 创建：`src/main/java/com/zonlong/teleportwaypoint/client/gui/WaypointScreen.java`
- 创建：`.../client/gui/PocketWaypointScreen.java`
- 修改：`src/main/java/com/zonlong/teleportwaypoint/client/TeleportWaypointClient.java`

**步骤：**
1. `ModMenus`：`DeferredRegister.create(Registries.MENU, MODID)`，两个 `MenuType`（`IContainerFactory` 接收 `BlockPos` 额外数据）。
2. `WaypointMenu`：持有 `BlockPos`；`stillValid`；传送列表由客户端屏幕展示。
3. `WaypointScreen`：传送目标列表 + uid 只读 + `id` 编辑框（`player.isCreative()` 才可编辑，`EditBox` 只接收 `[a-z0-9]`）。
4. `PocketWaypointScreen`：传送目标列表 + `name` 编辑框（仅所有者可编辑）。
5. `TeleportWaypointClient`：`MenuScreens.register(...)`。
6. Task 3 的 `use()` 接入 `player.openMenu(provider, buf -> buf.writeBlockPos(pos))`。

**验证：** `gradlew.bat compileJava` 编译通过。

---

## Task 10: 语言文件 + 完整构建 + GameTest

**文件：**
- 创建：`src/main/resources/assets/teleportwaypoint/lang/zh_cn.json`
- 修改：`src/main/resources/assets/teleportwaypoint/lang/en_us.json`
- 创建：`src/main/java/com/zonlong/teleportwaypoint/WaypointGameTests.java`（GameTest）

**步骤：**
1. `en_us.json` / `zh_cn.json`：GUI 标题、按钮、提示语、示例翻译键 `teleportwaypoint.waypoint.village`。
2. GameTest：放置口袋锚点 → 交互解锁 → 断言激活集合含该 uid；传送锚点名字翻译键解析。
3. 全量构建。

**验证：**
- `gradlew.bat build` 通过
- `gradlew.bat runGameTestServer` 通过

---

## 验收标准（Definition of Done）

1. `gradlew.bat build` 无错。
2. 结构 NBT 里 `{id:"village"}` 的传送锚点生成后名字随结构保留（翻译键可解析）。
3. 口袋锚点可放置/破坏/掉落自身，仅所有者可改名。
4. 传送锚点生存不可破坏，创造可改 `id`（`[a-z0-9]+` 校验）。
5. per-player 解锁独立、存档落盘，红/蓝外观（模型到位后）按玩家区分。
6. 已解锁锚点间免费跨维度即时传送，落点站在锚点相邻一格、面向锚点。
7. GameTest 通过。

## 依赖顺序

`1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10`
（Task 3 的 `use()` 与 Task 6/9 有回填依赖，已在步骤中注明。）
