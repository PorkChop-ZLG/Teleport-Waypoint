# 传送列表布局照抄 Waystones + 滚动条修复

**日期：** 2026-08-14
**状态：** 已确认
**参考：** Waystones `WaystoneSelectionScreenBase` / `AbstractWaystoneList`

## 目标
1. 传送列表 GUI 布局与 Waystones 一一对应（标题、锚点名字、搜索框、排序按钮、列表的位置与尺寸）。
2. 修复滚动条无法拖动（只能滚轮）。

## 布局参数（照抄 Waystones）
| 项 | 值 |
|---|---|
| IMAGE_WIDTH | 270 |
| IMAGE_HEIGHT | 200 |
| HEADER_HEIGHT | 64 |
| FOOTER_HEIGHT | 25 |
| ENTRY_WIDTH（getRowWidth） | 220 |
| 标题 y | topPos + 0 |
| 锚点名字 y | topPos + 20 |
| 搜索框 | x=width/2-110, y=topPos+40, 宽=198 |
| 排序按钮 | 搜索框右侧 20×20 |
| 列表 | x=leftPos, y=topPos+64, 宽=270, 高=111 |

## 滚动条修复
`getRowWidth()` 返回 `220`（列表宽 270），滚动条位置 `leftPos+255`（列表内），拖动由 `AbstractSelectionList` 内置处理。

## 实施
- `WaypointListScreen` 常量与 init/render 照抄上表。
- `WaypointList.getRowWidth()` 返回 220；构造宽度参数改为 270。
- `gradlew build` 验证。
