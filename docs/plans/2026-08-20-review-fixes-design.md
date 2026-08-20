# 代码审查遗留问题修复设计

**日期:** 2026-08-20
**状态:** Approved
**方式:** Approach A（逐项最小修复）

## 问题范围

- 修复：M-1、M-3、M-4、L-1、L-2、L-5
- 文档更新：H-3/H-4 已修复状态
- 不修复：H-1、H-2、M-2、L-3、L-4

## 设计

### 架构

保持现有架构，不新增类、不做大重构。所有改动均为局部独立修改。

### 组件变更

#### M-1：Xaero 配置不再每 tick 写入
- `XaeroMinimapIntegration`：新增 `lastAppliedNameScale` 缓存，仅当计算出的 name scale 变化时调用 `profile.set`。
- `XaeroWorldMapIntegration`：新增 `lastMirroredShowWaypoints` 缓存，仅当配置值变化时调用 `profile.set`。

#### M-3：SavedData DataFixTypes 改为 null
- `WaypointRegistryData.get()`、`PlayerWaypointData.get()` 的 `SavedData.Factory` 改为 `null` DataFixTypes。
- 存档文件名与 NBT 结构不变，不做迁移。

#### M-4：SavedData 读取容错
- 两个 SavedData 的 `read()` 对损坏条目 try/catch 并跳过，记录 warn。

#### L-1：Xaero 名称使用原始翻译键 / RightClickOption 参数
- `XaeroMinimapIntegration.addOrUpdate()`：普通锚点传 `teleportwaypoint.waypoint.<id>`，口袋锚点传字面名。
- `TeleportWaypointWorldReader.getRightClickOptions()`：名字/坐标行改用基础翻译键 + `setNameFormatArgs`。

#### L-2：customWaypoints key 修复确认
- 已随 H-4 修复改为单一 `MINIMAP_KEY`，文档标记为已修复。

#### L-5：README 更新
- 更新版本号、配置文件名、功能列表、数据存储、兼容性、已知限制等。

### 文档更新
- `docs/review/code-review-report.md`：标记 H-3/H-4 已修复，更新各项状态。
- `docs/review/code-review-issues.csv`：同步状态。

## 非目标
- 不修复 H-1、H-2、M-2、L-3、L-4。
- 不做 Xaero 同步层大重构。
- 不做旧档迁移。

## 下一步
按设计实施代码与文档修改，然后构建验证。
