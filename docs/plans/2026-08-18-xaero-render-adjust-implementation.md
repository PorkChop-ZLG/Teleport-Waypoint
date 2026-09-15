# Xaero 渲染调整实施计划

**日期：** 2026-08-18
**状态：** Ready for execution
**依据：** `docs/plans/2026-08-18-xaero-render-adjust-design.md`
**代码审查：** 跳过（用户指定）

---

## Phase 1：世界地图 hover 名称自绘

### Task 1.1 `TeleportWaypointWorldRenderer`
- 在 `renderElement()` 中，当 `hovered == true` 时，在图标正上方绘制名称：
  - 获取 `Minecraft.getInstance().font`
  - 名称 = `element.info().displayName().getString()`
  - 计算 `nameWidth = font.width(name)`
  - 使用 `PoseStack` 在图标上方平移并 `scale(3.0)`（参考原生 `WaypointRenderer`）
  - 绘制半透明背景 + 居中文字
- 保持 `hovered == false` 时只画图标。

### Task 1.2 `TeleportWaypointWorldReader`
- `getTooltip()` 返回 `null`，避免 Xaero 再绘制鼠标 Tooltip。

**验收：** 悬停时名称紧贴图标正上方，无鼠标 Tooltip 重复。

---

## Phase 2：小地图/世界内名称字号同步

### Task 2.1 `XaeroMinimapIntegration`
- 在 `sync()` 或独立方法中：
  - 获取 Xaero `HudMod.INSTANCE.getHudConfigs().getClientConfigManager()`
  - 读取 `WAYPOINT_DISTANCE_SCALE_IN_WORLD`
  - 将 `WAYPOINT_NAME_SCALE_IN_WORLD` 设为相同值
  - 若 API 只读/失败，捕获异常并 debug 日志

**验收：** 世界内名称字号与距离文本一致。

---

## Phase 3：构建与验证

### Task 3.1 构建
```
gradlew.bat build --offline --console=plain
gradlew.bat prepareClientRun --offline
```

### Task 3.2 启动验证
- 启动开发客户端，确认无编译/加载错误。

### Task 3.3 人工验证
- [ ] 世界地图悬停锚点，名称紧贴图标正上方
- [ ] 不再出现鼠标右下角 Tooltip
- [ ] 世界内名称字号与距离文本一致
- [ ] 名称不重叠
- [ ] 回归：其他 Xaero 功能正常
