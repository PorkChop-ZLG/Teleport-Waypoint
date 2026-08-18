# Xaero 联动修复完整设计（方案 B）

**日期：** 2026-08-18
**状态：** Approved
**方案：** B —— 重构网络同步 + 全面加固

## Problem Statement

根据 `docs/review/2026-08-18-xaero-post-integration-review.md`，当前 `Teleport-Waypoint-ds_flash` 存在 8 项问题：

- C1：破坏锚点后不广播删除，客户端残留幽灵锚点（Critical）
- I1：Xaero 配置注册后遮蔽 NeoForge 总开关
- I2：全量同步包无条目上限或分页
- I3：地图传送无服务端频率限制
- I4：小地图 custom waypoint 整数 ID 可能与其他集成冲突
- I5：显示范围默认值 256 与文档 128 不一致
- S1：Xaero 可选依赖版本范围过宽
- S2：README 存在过期描述

本设计采用方案 B，彻底重构网络同步为增量同步，并统一配置源、加入限频与 ID 冲突防护。

## Design

### Architecture

- **网络同步：** 登录/重连时发送全量快照（分页/上限）；运行期所有锚点增删改与激活状态变化发送增量包。
- **配置源：** `Config` 为唯一权威配置；Xaero 配置项仅作为镜像，不再反向影响显示逻辑。
- **限频：** 新增 `TeleportRateLimiter`，普通传送与地图传送共用每玩家冷却。
- **小地图 ID：** 使用模组专属 `ResourceLocation` key + UUID 稳定哈希 ID + 写入/删除归属校验。

### Components

| 编号 | 问题 | 主要改动文件 | 改动性质 |
|---|---|---|---|
| C1 | 破坏锚点不广播 | `core/WaypointManager.java` | 逻辑修复 + 接入 Remove 增量包 |
| I1 | Xaero 配置遮蔽模组配置 | `Config.java`、`client/xaero/XaeroIntegration.java`、`XaeroWorldMapIntegration.java` | 唯一配置源 + 镜像同步 |
| I2 | 全量同步包无上限 | 新增多个增量 Payload、`network/ModNetwork.java`、`core/WaypointManager.java`、`client/ClientWaypointState.java` | 增量同步 + 快照分页/上限 |
| I3 | 地图传送无频率限制 | `core/TeleportRateLimiter.java`（新）、`network/ModNetwork.java`、`core/WaypointTeleporter.java` | 每玩家冷却/令牌桶 |
| I4 | 小地图 ID 冲突 | `client/xaero/XaeroMinimapIntegration.java` | 模组命名空间 + 哈希 ID + 归属校验 |
| I5 | 默认范围不一致 | `Config.java`、`README.md`、`docs/plans/2026-08-17-xaero-config-filter-design.md` | 默认值统一为 128 |
| S1 | Xaero 依赖范围过宽 | `src/main/templates/META-INF/neoforge.mods.toml` | 版本范围收窄 |
| S2 | README 过期描述 | `README.md` | 文档更新 |

### Data Flow

#### 登录 / 重连
```
ServerPlayer 登录
  → WaypointManager.syncAllTo(player) 改为发送 WaypointSnapshotPayload（全量，分页/上限）
  → 再发送 SyncActivatedWaypointsPayload（该玩家激活快照）
```

#### 运行期锚点新增
```
方块放置 / onLoad 注册成功
  → WaypointManager.register()
  → 若该 UID 是“新记录”：
      → 向所有在线玩家发送 AddWaypointPayload(record)
      → 向放置玩家发送 ActivatedWaypointAddPayload（如果自动激活）
```

#### 运行期锚点改名 / 更新
```
改名成功
  → 更新 BlockEntity + WaypointRegistryData
  → 向所有在线玩家发送 UpdateWaypointPayload(record)
  → 向相关玩家发送激活列表增量（若名称影响激活列表显示）
```

#### 运行期锚点销毁（C1 修复）
```
方块被破坏 / unregister
  → 从 WaypointRegistryData 删除记录（只删一次）
  → 从 PlayerWaypointData 清除所有玩家对该 UID 的激活
  → 向所有在线玩家发送 RemoveWaypointPayload(uid)
  → 向受影响的玩家发送 ActivatedWaypointRemovePayload(uid)
```

#### 客户端状态更新
```
ClientWaypointState
  + applyAdd(AddWaypointPayload)
  + applyUpdate(UpdateWaypointPayload)
  + applyRemove(RemoveWaypointPayload)
  + applyActivatedAdd/Remove
  每次更新 revision++
  Xaero 集成通过 revision 变化触发重绘
```

### Error Handling

- 客户端收到重复增量包：幂等处理（Add 已存在则更新，Remove 不存在则忽略）。
- 未初始化前收到增量包：暂存队列，快照完成后按序应用。
- 分页快照中断：客户端标记未初始化，下次登录重新全量同步。
- 锚点数量超过安全上限：服务端记录错误并停止发送全量；增量包仍可工作。
- Xaero 未安装或配置注册失败：回退到 `Config`，日志记录。
- 配置双向同步冲突：以 `Config` 为最终权威。
- 玩家登出：清理限频状态，防止 Map 泄漏。
- 小地图 ID 冲突：写入前探测空位；删除前校验归属，避免误删他人标记。
- 客户端状态不一致：不崩溃，忽略并 debug 日志。

### Testing Strategy

- 构建验证：`gradlew.bat build --offline --rerun-tasks --console=plain`、`gradlew.bat prepareClientRun --offline`
- 人工游戏内验证清单：
  - C1：双客户端在线，B 破坏锚点，A/B 均不再显示幽灵锚点。
  - I2：放置/改名/破坏锚点时观察日志/抓包，只出现增量包，不再全量广播。
  - I1：模组配置关闭 `showWaypoints` 后，World Map / Minimap 标记消失。
  - I3：连续地图传送第 1 次成功，后续被冷却拦截。
  - I4：与其他占用 Xaero custom waypoint 的 mod 同装，不覆盖/误删对方标记。
  - 登录同步：登录后全量快照 + 激活快照完整。
- 不新增自动化测试框架。

## Decisions Made

- 采用方案 B：重构网络同步 + 全面加固。
- I2 采用“登录全量分页 + 运行期增量包”，彻底替代全量广播。
- I1 以 `Config` 为唯一权威；Xaero 配置作为镜像。
- I3 新增 `TeleportRateLimiter`，普通传送与地图传送共用。
- I4 使用模组专属 key + UUID 哈希 ID + 归属校验。
- I5 默认范围统一为 128。
- S1 将 Xaero 依赖范围收窄到已验证版本（Minimap `25.3.5`、World Map `1.40.6`）。
- 验证以构建 + 人工游戏内验证为主。

## Non-Goals

- 不引入自动化测试框架/GameTest。
- 不重写 Xaero 集成整体架构（仍保留 World Map / Minimap 拆分）。
- 不实现管理命令或手动全量刷新命令（可选后续）。

## Next Steps

转入 planning 技能生成详细实施计划。
