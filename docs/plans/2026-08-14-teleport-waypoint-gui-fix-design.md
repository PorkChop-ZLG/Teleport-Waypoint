# 传送锚点 GUI 修复设计文档（Screen + MenuAccess）

**日期：** 2026-08-14
**状态：** 待批准（Pending Approval）
**方案：** B —— 界面继承 `Screen` + 实现 `MenuAccess`（效仿 Balm），不再继承 `AbstractContainerScreen`

---

## 1. 问题陈述

现有 GUI 继承 `AbstractContainerScreen`，引入了三个问题：
1. 口袋锚点无法输入中文，中文在列表里显示异常。
2. 界面漏出原版容器 GUI 的默认标签「物品栏」「傻子」。
3. 命名/搜索框聚焦时按 E 键（物品栏键）会直接关闭界面。

根因：
- 问题 2：`AbstractContainerScreen.renderLabels` 默认渲染 `title` + `playerInventoryTitle`（"物品栏"）及容器默认标签。
- 问题 3：`EditBox.keyPressed` 不处理字母键（字母走 `charTyped`），E 键落到 `Screen.keyPressed` 的「物品栏键」分支触发 `onClose`。
- 问题 1：中文依赖 IME 的 `charTyped`，被 E 键关闭与容器默认渲染干扰。

## 2. 目标与非目标

### 目标
1. 三个界面（命名传送锚点 / 命名口袋锚点 / 传送列表）不再出现「物品栏」「傻子」等原版容器标签。
2. 命名/搜索框聚焦时可按 E 键正常输入，不被关闭。
3. 口袋锚点可正常输入并显示中文。

### 非目标
- 不改动菜单（Menu）、网络、数据层。
- 不引入 Balm 依赖（只效仿其「Screen + MenuAccess」做法）。

## 3. 架构（方案 B）

界面从 `AbstractContainerScreen<T>` 改为：
```java
class XxxScreen extends Screen implements MenuAccess<XxxMenu>
```
- `Screen`：无容器默认渲染，只有 widget 渲染与 `renderBackground`（游戏模糊背景）。
- `MenuAccess<XxxMenu>`：实现 `getMenu()` 返回菜单，满足 `RegisterMenuScreensEvent.register` 的约束。

## 4. 组件改造

### 4.1 `AbstractRenameScreen<T extends AbstractWaypointMenu>`
- 继承 `Screen`，实现 `MenuAccess<T>`。
- 字段：`T menu`、`BlockPos pos`、`EditBox textEdit`、`boolean canEdit`。
- `getMenu()` 返回 `menu`。
- `init()`：居中放置 `EditBox`（可编辑性由 `canEdit` 控制）+ 保存/关闭按钮。
- `render()`：`super.render(...)` 渲染 widget，再画标题；`renderBackground` 用默认模糊背景或半透明矩形。
- `keyPressed()`（关键）：当 `textEdit` 聚焦时，先让 `textEdit.keyPressed(...)` 处理，或 `textEdit.isFocused()` 时直接消费按键，**不再走 `super.keyPressed`**（避免 E 键关闭）；ESC/Enter 才 `onClose`。

### 4.2 `WaypointListScreen`
- 继承 `Screen`，实现 `MenuAccess<WaypointListMenu>`。
- 布局（绝对坐标，居中）：
  - 第 1 行标题「传送列表」；
  - 第 2 行当前锚点名字（可点击，跳命名界面）；
  - 搜索框 `EditBox`；
  - 滚动列表 `WaypointList`（保留现有 `ContainerObjectSelectionList`，它不依赖容器屏）。
- `keyPressed()`：搜索框聚焦时消费按键（同 4.1）。

### 4.3 保持不变的组件
- `WaypointList`（`ContainerObjectSelectionList`）：仅依赖 `Screen` widget 体系，无需改动。
- `RenameWaypointScreen` / `RenamePocketWaypointScreen`：仅调整基类，`canEdit`/`getCurrentText` 逻辑不变。

## 5. 数据流（keyPressed / charTyped）

- `EditBox` 聚焦 → `keyPressed` 被 override：`textEdit.keyPressed(...)` 或 `textEdit.isFocused()` → 消费按键 → E 键不再触发关闭。
- 字母/中文字符走 `charTyped`：`Screen.charTyped` 默认路由到 `getFocused().charTyped(...)`（`EditBox.charTyped`），IME 中文输入正常工作。
- ESC / Enter 在命名界面触发 `onClose`（保存逻辑不变，仍走 `RenameWaypointPayload`）。

## 6. 权限规则（不变）

| 操作 | 传送锚点 | 口袋锚点 |
|---|---|---|
| 改名 | 仅创造 | 仅所有者 |
| 跳命名界面 | 仅创造 | 仅所有者 |

## 7. 决策记录

- **方案 B**：`Screen` + `MenuAccess`，效仿 Balm，彻底避开 `AbstractContainerScreen` 默认渲染。
- **keyPressed override**：EditBox 聚焦消费按键（参考 Waystones `WaystoneEditScreen`）。
- **中文**：依赖原版 `EditBox` + unifont 字体与 `Component`，无需额外处理。
- **列表组件**：保留 `ContainerObjectSelectionList`（不影响问题 2，因为它不是容器屏默认渲染的一部分）。

## 8. 实施步骤

1. 改造 `AbstractRenameScreen`：改基类 + 实现 `MenuAccess` + `keyPressed` + `render`/`renderBackground`。
2. 改造 `WaypointListScreen`：同上，布局用绝对坐标。
3. `RenameWaypointScreen` / `RenamePocketWaypointScreen` 调整基类签名。
4. `gradlew build` 验证。

## 9. 待确认点

1. 背景样式：命名/列表界面用「游戏模糊背景（Screen 默认）」还是「半透明深色矩形（现状）」？（推荐后者，视觉一致）
2. 其余不变，确认后开始实施。
