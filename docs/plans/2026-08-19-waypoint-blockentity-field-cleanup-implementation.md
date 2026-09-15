# 方块实体无用 NBT 字段清理 实施计划

**日期：** 2026-08-19
**状态：** Ready for execution
**依据：** `docs/plans/2026-08-19-waypoint-blockentity-field-cleanup-design.md`

---

## Phase 1：修改 `WaypointBlockEntity`

### Task 1.1 修改 `saveAdditional()`

- 根据 `isPocketWaypoint()` 分支：
  - 普通锚点：只写 `uid` + `waypoint_id`
  - 口袋锚点：只写 `uid` + `name` + `owner`

**验收：**
- 普通锚点不再写入 `name`
- 口袋锚点不再写入 `waypoint_id`

### Task 1.2 修改 `loadAdditional()`

- 根据 `isPocketWaypoint()` 分支：
  - 普通锚点：只读 `waypoint_id`
  - 口袋锚点：只读 `name` / `owner`
- 不主动 `remove` 旧字段

**验收：**
- 旧 NBT 中的无用字段被忽略
- 下次保存时无用字段不再写入

---

## Phase 2：构建与验证

### Task 2.1 构建

```bash
gradlew.bat build --offline --console=plain
```

**验收：**
- BUILD SUCCESSFUL

### Task 2.2 游戏内验证

- 放置普通传送锚点，检查 NBT 只包含 `uid` + `waypoint_id`
- 放置口袋锚点，检查 NBT 只包含 `uid` + `name` + `owner`
- 旧存档普通锚点带 `name`：显示/改名正常，下次保存后 `name` 消失
- 旧存档口袋锚点带 `waypoint_id`：显示/改名正常，下次保存后 `waypoint_id` 消失
- 回归传送、激活、改名、Xaero 显示

---

## 执行顺序

1. Phase 1 修改 `WaypointBlockEntity`
2. Phase 2 构建 + 人工验证
