# Xaero 联动最终深度审查报告

**日期：** 2026-08-17
**基线：** `bf740c3`
**当前 HEAD：** `0d00bdb`
**构建验证：** `gradlew build --offline` 通过

---

## 审查范围

- 涉及提交：`f0a4c59` → `0d00bdb`
- 变更规模：37 个文件，+1844 行
- 主要模块：
  - Xaero 世界地图 / 小地图联动
  - NeoForge 配置系统扩展
  - 全量/激活状态网络同步
  - 地图传送
  - 自定义贴图与右键菜单
  - 文档与构建脚本

---

## 总体评价

整体完成度较高，架构方向正确：

- Xaero 作为可选依赖，通过 `compileOnly` + 反射加载；
- 地图传送只传 `targetUid`，服务端校验激活状态；
- 配置系统支持按类型/状态/距离过滤；
- 世界地图自定义元素、右键菜单、四色贴图、小地图注入已完成；
- 构建脚本自动解出 Xaero lib，解决可移植性问题。

---

## 问题清单与处理决定

| # | 问题 | 决定 |
|---|------|------|
| 1 | 误提交 `.dsh-vision-router` 临时图片 | 修复 |
| 2 | README 与最终实现不一致 | 修复 |
| 3 | Xaero 配置注册可能遮蔽 NeoForge 配置 | 暂不修复 |
| 4 | `XaeroIntegration` 同时引用两个 Xaero 模组类 | 修复 |
| 5 | 小地图未激活颜色与世界地图不一致 | 修复 |
| 6 | 全量同步包大小无限制 | 暂不修复 |
| 7 | `UID_TO_ID` 泄漏 / `findUid` 线性查找 | 修复 |
| 8 | `ClientWaypointState` 死代码 | 修复 |
| 9 | `TeleportWaypointInfoOption` 注释与行为不符 | 修复 |
| 10 | `TeleportWaypointContext` 残留字段 | 修复 |
| 11 | 缺少最终设计文档 | 修复 |
| 12 | 无自动化测试 | 不修复 |
| 13 | 小地图范围刷新全量重建 | 修复 |
| 14 | `removeWaypoint` 无效广播 | 修复 |

---

## 详细问题

### 1. 误提交工具产物（高）
- 仓库包含 `.dsh-vision-router/artifacts/*.png`，属于 AI 图像预览临时文件。
- 处理：删除并加入 `.gitignore`。

### 2. README 过期（高）
- README 仍写“未激活灰色”“悬停显示名称与坐标”“showWaypointNames 配置项”。
- 实际：世界地图四色、悬停只显示名称、配置已分组并移除 `showWaypointNames`。

### 3. Xaero 配置遮蔽 NeoForge 配置（中）
- `tryRegisterXaeroConfig()` 注册成功后，`showWaypoints()` 优先读 Xaero 配置。
- 决定：暂不修复，保持现状。

### 4. `XaeroIntegration` 跨模组类引用（中）
- 类同时引用 `xaero.map.WorldMap` 与 `xaero.common.HudMod`。
- 处理：拆分为 `XaeroWorldMapIntegration`，并把 minimap 注册逻辑移入 `XaeroMinimapIntegration`。

### 5. 小地图未激活颜色不一致（中）
- 世界地图：未激活传送锚点红、口袋锚点黄。
- 小地图当前统一 `GRAY`。
- 处理：改为 `RED` / `YELLOW`。

### 6. 全量同步包大小（中）
- `SyncAllWaypointsPayload` 无分页/上限。
- 决定：暂不修复。

### 7. 小地图 ID 映射泄漏/线性查找（低）
- `UID_TO_ID` 整维度移除未清理；
- `findUid` 线性查找。
- 处理：增加反向 `Map<Integer, UUID>` 并清理。

### 8. 死代码（低）
- `ClientWaypointState.upsertWaypoint()` / `removeWaypoint()` 未被调用。
- 处理：删除。

### 9. 注释与行为不符（低）
- `TeleportWaypointInfoOption` 注释写“non-interactive”，但默认 `active=true`。
- 处理：修正注释。

### 10. 残留字段（低）
- `TeleportWaypointContext.showWaypoints` / `showNames` 已基本不使用。
- 处理：清理。

### 11. 缺少最终设计文档（低）
- 处理：补充最终设计文档。

### 12. 无自动化测试
- 决定：不修复，保持手工验证。

### 13. 小地图范围刷新性能（低）
- 每次移动 16 格全量重建。
- 处理：在 `addOrUpdate` 中跳过未变化的 Waypoint，减少对象重建。

### 14. `removeWaypoint` 无效广播（低）
- 对不存在的记录也调用 `broadcastAll`。
- 处理：仅在实际移除后广播。

---

## 验证方式

- `gradlew compileJava --offline`
- `gradlew build --offline`
- `gradlew prepareClientRun --offline`
