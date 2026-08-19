# 方块实体无用 NBT 字段清理 设计文档

**日期：** 2026-08-19
**状态：** Approved
**方案：** 按方块类型条件读写字段（方案 A）

## Problem Statement

当前 `WaypointBlockEntity` 的 `saveAdditional()` / `loadAdditional()` 会无条件处理 `waypoint_id` 和 `name` 两个字段：

- 普通传送锚点不需要 `name`，但旧逻辑会默认写入 `name = "Pocket Waypoint"`；
- 口袋锚点不需要 `waypoint_id`，但旧逻辑会默认写入 `waypoint_id = "empty"`。

这导致两种锚点的方块实体 NBT 中都出现无用字段。

## Design

### 架构

保持单个 `WaypointBlockEntity` 不变，在保存/加载时根据 `isPocketWaypoint()` 分支处理字段。

### `saveAdditional()`

- 普通传送锚点：只保存 `uid` + `waypoint_id`
- 口袋锚点：只保存 `uid` + `name` + `owner`
- 不再写入对方类型的无用字段

### `loadAdditional()`

- 普通传送锚点：只读取 `waypoint_id`
- 口袋锚点：只读取 `name` / `owner`
- 忽略旧 NBT 中对方类型的无用字段

### 旧存档处理

- 不主动 `remove` 旧字段；
- 下次保存时不再写入无用字段，旧字段自然消失。

## Data Flow

### 普通传送锚点保存

```
saveAdditional()
  ├─ uid
  ├─ waypoint_id
  └─ name 不写入
```

### 口袋锚点保存

```
saveAdditional()
  ├─ uid
  ├─ name
  ├─ owner
  └─ waypoint_id 不写入
```

## Error Handling / 边界情况

- 普通锚点旧 NBT 带 `name`：忽略，不影响显示/改名/同步；
- 口袋锚点旧 NBT 带 `waypoint_id`：忽略，不影响显示/改名/同步；
- 复制/粘贴方块实体：仍按类型只保留有效字段；
- 不修改网络协议、SavedData、Xaero 数据结构。

## Testing Strategy

- `gradlew build --offline --console=plain`
- 放置普通传送锚点，确认 NBT 只有 `uid` + `waypoint_id`
- 放置口袋锚点，确认 NBT 只有 `uid` + `name` + `owner`
- 旧存档普通锚点带 `name`：加载后显示/改名正常，下次保存后 `name` 消失
- 旧存档口袋锚点带 `waypoint_id`：加载后显示/改名正常，下次保存后 `waypoint_id` 消失
- 回归传送、激活、改名、Xaero 显示

## Decisions Made

- 采用方案 A：按类型条件读写字段；
- 不拆分方块实体类；
- 不主动清理旧字段，依赖下次保存自然清除。

## Non-Goals

- 不修改网络协议与 SavedData 结构；
- 不做旧数据迁移脚本；
- 不改变业务行为。

## Next Steps

转入 planning 技能生成详细实施计划。
