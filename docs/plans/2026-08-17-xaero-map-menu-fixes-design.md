# Xaero 世界地图菜单与传送关闭修复设计

**日期：** 2026-08-17
**状态：** 已确认并实现
**关联：** `docs/review/2026-08-17-xaero-integration-review.md`

## 问题

1. 世界地图上显示了传送锚点名字，但需求是只显示图标。
2. 从世界地图点击“传送”后，Xaero 世界地图界面不会关闭。
3. 右键菜单第一行显示的是坐标，应该显示传送锚点名字。
4. 右键菜单坐标行带有状态色背景且可点击，需求是信息行样式。

## 设计

### 1. 世界地图不显示名字
- `TeleportWaypointWorldRenderer.renderElement()` 不再绘制名字。
- 悬停 Tooltip 仍只显示名字。

### 2. 传送后关闭世界地图
- `TeleportRightClickOption.onAction(Screen screen)`：
  - 发送 `MapTeleportRequestPayload`；
  - 调用 `screen.onClose()` 关闭地图。

### 3. 右键菜单三行
- 模仿 Xaero 空白地图右键菜单的样式：
  1. `名字：<名称>` —— 第一行，灰色高亮（类似 `Choose an Option` 占位行），点击无动作；
  2. `坐标：x,y,z` —— 半透明黑底，普通文字，点击无动作；
  3. `传送` —— 半透明黑底，已激活可点击，未激活灰色不可用。

### 4. 菜单背景色
- `getRightClickTitleBackgroundColor()` 返回中性灰 `0xFF6B6B6B`。
- 第一行名字作为高亮行显示灰色背景，其它行保持半透明黑底。

## 实现文件
- `TeleportWaypointWorldRenderer.java`
- `TeleportRightClickOption.java`
- `TeleportWaypointWorldReader.java`
- `TeleportWaypointInfoOption.java`

## 验证
- `gradlew compileJava --offline`
- `gradlew build --offline`
- `gradlew prepareClientRun --offline`
