# Xaero 渲染调整设计（世界地图 hover 名称 + 小地图名称字号）

**日期：** 2026-08-18
**状态：** Approved
**方案：** 模仿原生 Waypoint 渲染 + Xaero 全局字号同步

## Problem Statement

1. 世界地图上，鼠标悬停到本模组传送锚点图标时，名称显示在鼠标右下角，位置偏离；需要像原生 Waypoint 一样紧贴图标正上方显示。
2. 小地图/世界内悬浮名称文字太小；需要与 Xaero 的 Waypoint 距离文本同字号，并避免重叠。

## Design

### 世界地图 hover 名称

- 修改 `TeleportWaypointWorldRenderer.renderElement()`：
  - 先绘制现有图标。
  - 当 `hovered == true` 时，在图标正上方绘制名称：
    - 内容：`element.info().displayName().getString()`
    - 水平居中：按 `font.width(name)` 计算
    - 缩放：参考原生 Waypoint `scale(3.0)`
    - 背景：半透明深色/主题色背景
- 修改 `TeleportWaypointWorldReader.getTooltip()`：
  - 返回 `null`，避免 Xaero 再在鼠标位置绘制重复 Tooltip。

### 小地图/世界内名称字号

- 在 `XaeroMinimapIntegration` 中：
  - 读取 Xaero `WAYPOINT_DISTANCE_SCALE_IN_WORLD`
  - 将 `WAYPOINT_NAME_SCALE_IN_WORLD` 同步为相同值
  - 若设置失败，捕获异常并记录 debug 日志
- 该同步影响所有 Xaero 锚点的世界内名称字号（用户已确认接受）。

### 不重叠

- 世界地图不再叠加 Tooltip，只保留图标上方名称。
- 小地图/世界内名称由 Xaero 原生布局堆叠，字号一致后间距按比例放大，不会重叠。

## 组件改动

| 文件 | 改动 |
|---|---|
| `client/xaero/TeleportWaypointWorldRenderer.java` | 新增 hover 名称绘制 |
| `client/xaero/TeleportWaypointWorldReader.java` | `getTooltip()` 返回 null |
| `client/xaero/XaeroMinimapIntegration.java` | 同步名称字号到距离字号 |

## 错误处理

- Xaero 配置只读/API 不可用：捕获异常，跳过同步，debug 日志。
- 名称过长：按字体宽度自适应背景，不做截断。
- 屏幕边缘：可能被裁切，可接受。
- 鼠标移出：`hovered` 变 false，名称消失。

## 测试策略

- 构建：`gradlew build --offline`、`gradlew prepareClientRun --offline`
- 人工验证：
  - 世界地图悬停锚点，名称紧贴图标正上方，且无鼠标 Tooltip 重复
  - 世界内名称字号与距离文本一致
  - 名称不重叠
  - 回归：其他 Xaero 功能正常

## Decisions Made

- 世界地图名称采用自绘，模仿原生 Waypoint。
- 小地图名称字号采用 Xaero 全局配置同步。
- 跳过代码审查（用户指定）。

## Non-Goals

- 不实现屏幕边缘 clamp。
- 不实现按锚点单独设置字号。
- 不自定义 Xaero 小地图渲染器。

## Next Steps

转入 planning 技能生成详细实施计划。
