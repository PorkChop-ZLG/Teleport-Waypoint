# 代码审查问题修复设计

**日期：** 2026-08-21
**状态：** Approved
**方案：** Approach A — 逐项最小修复

## Problem Statement

根据 `docs/review/2026-08-21-detailed-code-review.md`，需要修复以下 6 项问题：

- H-1：激活锚点元数据快照未分页，可能超过网络帧容量
- M-2：一个损坏的 UUID 会丢弃该玩家全部有效激活记录
- M-3：已打开的传送列表不会响应服务端增量更新
- L-2：取消最后一个激活记录后会保留空玩家条目
- L-3：README 对首次激活行为的描述互相矛盾
- L-4：Xaero 小地图集成使用已弃用 API

暂不修复：M-1、L-1。

## Design

### 架构

保持现有分层架构不变，不做大重构。每项修复落在原有模块：

| 问题 | 改动位置 |
|---|---|
| H-1 | `SyncActivatedWaypointsPayload`、`WaypointManager.syncTo()`、`ClientWaypointState`、`ModNetwork` |
| M-2 | `PlayerWaypointData.read()` |
| M-3 | `WaypointListScreen` |
| L-2 | `PlayerWaypointData.deactivate()` / `read()` |
| L-3 | `README.md` |
| L-4 | `XaeroMinimapIntegration` |

### Components

#### H-1：激活元数据分页

- `SyncActivatedWaypointsPayload` 改为 `record(List<ActivatedWaypointInfo> waypoints, int page, boolean done)`。
- 新增 `MAX_PAGE_SIZE = 500`，codec 集合上限从 `100_000` 改为 `500`。
- `WaypointManager.syncTo()` 复用维度快照的分页逻辑，按 500 条分页发送。
- `ClientWaypointState` 新增 `applyActivatedSnapshot(infos, page, done)`：
  - `page 0` 重置临时累积快照，不清空已发布列表；
  - 累积每页条目；
  - `done=true` 时一次性发布到 `activated` / `activatedUids` 并 `revision++`；
  - 分页期间到达的 `applyActivatedAdd/Remove` 进入暂存队列，快照完成后重放。
- `ModNetwork.handleSyncActivated()` 改为调用 `applyActivatedSnapshot(...)`。
- 若无其他调用点，删除 `ClientWaypointState.setActivated()`。

#### M-2：存档读取逐条容错

- `PlayerWaypointData.read()` 外层 `try` 只解析玩家 UUID。
- 内层对每个 UUID 条目单独 `try/catch`，坏 UUID 只跳过该条。
- 有跳过时 `setDirty()`，下次保存写入清理后的数据。

#### M-3：传送列表响应增量更新

- `WaypointListScreen` 增加 `lastRevision` 字段。
- `init()` 时记录 `ClientWaypointState.getRevision()`。
- 重写 `tick()`，检测 revision 变化后调用 `updateList()`。
- 保留搜索和排序，不保留滚动位置。

#### L-2：空玩家记录清理

- `PlayerWaypointData.deactivate()` 移除最后一条后删除玩家键。
- `PlayerWaypointData.read()` 解析出空集合时不写入，并 `setDirty()` 清理历史空记录。

#### L-3：README 修正

- 将 README 第 55 行改为：
  > 右键点击未激活的锚点 → 自动激活（提示 + 音效），不打开传送列表；再次右键已激活的锚点才打开传送列表。

#### L-4：Xaero 弃用 API

- `XaeroMinimapIntegration.addOrUpdate()` 将 `existing.getSymbol()` 替换为 `existing.getInitials()`。
- 不改版本范围、不加反射降级。

### Data Flow

#### H-1 登录 / 改名时的激活快照

```
服务端 syncTo()
  → 收集全部已激活 ActivatedWaypointInfo
  → 按 500 条分页
  → 依次发送 SyncActivatedWaypointsPayload(page, done)

客户端 applyActivatedSnapshot()
  → page 0：重置临时累积列表
  → 累积每页条目
  → done=true：发布到 activated / activatedUids，revision++
  → 冲刷快照期间暂存的增量包
```

#### M-2 / L-2 存档读取

```
read(tag)
  → 遍历 players 键
      → 玩家 UUID 无效：跳过该玩家
      → 玩家 UUID 有效：遍历 UUID 列表
          → 单条 UUID 损坏：跳过该条，标记 dirty
      → 集合为空：不写入内存，标记 dirty
  → dirty=true 时 setDirty()
```

#### M-3 列表刷新

```
WaypointListScreen.tick()
  → 比较 revision 与 lastRevision
  → 不一致：更新 lastRevision，调用 updateList()
```

### Error Handling

- H-1：网络包有序到达；`page 0` 只重置临时快照，不清空已发布列表；断开时 `reset()` 清理状态。
- M-2：玩家 UUID 无效跳过玩家；单条 UUID 无效跳过该条；有跳过时 `setDirty()`。
- L-2：`deactivate()` 删除空玩家键；读取时空列表跳过并 `setDirty()`。
- M-3：revision 比较简单可靠，溢出回绕风险可忽略。
- L-4：`getInitials()` 可能返回 null，但现有 `Objects.equals` 比较可安全处理。

### Testing Strategy

1. `./gradlew build --offline --console=plain` 成功。
2. L-4 使用 `-Xlint:deprecation` 确认无 Xaero 弃用提示。
3. H-1：激活 500+ 锚点后登录/改名，确认列表完整、不断线。
4. M-2：构造含损坏 UUID 的存档，确认只跳过坏条且保存后清理。
5. L-2：取消最后激活记录后确认存档无空玩家条目。
6. M-3：打开列表时由其他玩家改名/删除，确认列表自动刷新且保留搜索/排序。
7. L-3：检查 README 两处描述一致。
8. L-4：安装 Xaero 后确认小地图锚点显示正常。

## Decisions Made

- 采用 Approach A：逐项最小修复，不做协议层统一重构，不新增测试框架。
- H-1 复用现有 `page/done` 分页模式，每页 500 条，与维度快照一致。
- M-2 采用“逐条容错 + setDirty 主动清理”。
- M-3 保留搜索/排序，不保留滚动位置。
- L-2 同时清理运行期空记录和历史存档空记录。
- L-3 统一为“首次右键只激活，再次右键才打开列表”。
- L-4 仅替换 `getSymbol()` → `getInitials()`。

## Non-Goals

- 不修复 M-1、L-1。
- 不新增自动化测试框架。
- 不重构网络同步协议整体结构。
- 不调整 Xaero 依赖版本范围。

## Next Steps

调用 planning 技能生成详细实施计划。
