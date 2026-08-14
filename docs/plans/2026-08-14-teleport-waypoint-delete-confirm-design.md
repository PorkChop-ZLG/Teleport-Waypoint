# 删除锚点确认窗口设计文档

**日期：** 2026-08-14
**状态：** 已确认
**方式：** 客户端本地 Screen（`minecraft.setScreen` 覆盖，不走服务端 Menu）

---

## 目标
1. 删除按钮悬浮显示「删除锚点」提示。
2. 点击删除按钮弹出确认窗口（标题 + 说明 + 确认/取消）。
3. 当前锚点的删除按钮保持可用（传送按钮仍灰色禁用）。

## 确认窗口布局
```
是否确认删除该锚点？
只会取消该锚点的激活状态，从传送列表中移除，不会删除锚点方块本身。
      [ 确认 ]    [ 取消 ]
```

## 改动
- `WaypointList.java`：删除按钮 `setTooltip`；移除 `deleteButton.active = !isSelf`。
- `WaypointListScreen.java`：`onDelete` 回调改为打开确认窗口。
- 新建 `DeleteConfirmScreen.java`。
- `en_us.json` / `zh_cn.json` 新增翻译键。

## 翻译键
- `gui.teleportwaypoint.delete_waypoint` = 删除锚点
- `gui.teleportwaypoint.delete_confirm_title` = 是否确认删除该锚点？
- `gui.teleportwaypoint.delete_confirm_desc` = 只会取消该锚点的激活状态，从传送列表中移除，不会删除锚点方块本身。
- `gui.teleportwaypoint.confirm` = 确认
- `gui.teleportwaypoint.cancel` = 取消

## 流程
删除按钮 → `setScreen(DeleteConfirmScreen)` → 确认 = 发 payload + 刷新列表 + 回列表；取消 = 回列表。
