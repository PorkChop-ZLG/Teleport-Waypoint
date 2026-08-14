# 传送锚点 GUI 与模型修复方案

**日期：** 2026-08-14
**状态：** 待批准
**参考：** Waystones `RemoveWaystoneMessage` / `ManageWaystonesList` / `WaystoneBlockEntityBase`

---

## 问题与方案

### 1. 方块模型/贴图应用
你已提供 `models/block/*.json`、`models/item/*.json` 与 `textures/**/*.png`，但缺 `blockstates/*.json`（方块状态→模型映射），导致日志 `missing model for variant`。
**方案**：新建 `blockstates/waypoint.json` 与 `blockstates/pocket_waypoint.json`，各指向对应 block 模型。

### 2. 标题文字被模糊
根因：`Screen.render` 默认先调 `renderBackground()`（模糊），我的 `render` 里 `super.render` 再次触发模糊，覆盖标题。
**方案**：在 `AbstractWaypointScreen` override `renderBackground` 画半透明矩形（不再模糊）；`AbstractRenameScreen`/`WaypointListScreen` 的 `render` 改为先 `super.render`（背景+widget）再画标题/名字。

### 3. 放置后命名界面无法输入
根因：`setPlacedBy` 设 `owner` 后立即 `openRenameScreen`，方块实体数据（owner）未同步到客户端，`init` 读到 `owner==null` → `canEdit=false`。
**方案**：`setPlacedBy` 中在 `openRenameScreen` 前 `level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL)`。

### 4. 传送列表名字空白
根因：改名后未更新 `WaypointRegistryData` 与客户端 `ClientWaypointState`。
**方案**：`handleRename` 改名后调用 `WaypointManager.register(be)` + `WaypointManager.syncTo(player)`。

### 5. 传送后 GUI 未关闭
**方案**：`WaypointList` 增加 `onTeleport` 回调，传送请求发出后 `onClose()`。

### 6. 删除按钮
参考 Waystones：新增 `DeleteWaypointPayload(UUID)`；服务端从玩家激活集合移除 + `syncTo`；列表每项右侧加删除按钮（点击删除该锚点的激活记录）。

---

## 实施清单

- [ ] 新建 2 个 blockstates 文件
- [ ] `AbstractWaypointScreen` override `renderBackground`
- [ ] `AbstractRenameScreen`/`WaypointListScreen` 调整 render 顺序
- [ ] `WaypointBlock`/`PocketWaypointBlock.setPlacedBy` 加 sendBlockUpdated
- [ ] `ModNetwork.handleRename` 加 register + syncTo
- [ ] `WaypointList` 加 onTeleport/onDelete 回调 + 删除按钮
- [ ] 新增 `DeleteWaypointPayload` + `WaypointManager.deactivate` + `ModNetwork.handleDelete`
- [ ] `WaypointManager.syncTo` 保持，`deactivate` 新增
- [ ] lang 补充删除按钮提示（可选）
- [ ] `gradlew build` 验证

## 待确认
1. 删除按钮的语义：从玩家「已解锁列表」移除（deactivate，不破坏方块），对吗？还是「删除整个锚点（破坏方块）」？
2. 方案确认后立即实施。
