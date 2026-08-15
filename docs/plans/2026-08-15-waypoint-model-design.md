# Waypoint 方块模型（悬浮水晶球）设计文档

**日期：** 2026-08-15
**状态：** 已确认（Approved）
**方案：** A —— 双轴十字球（两张互相垂直的竖直圆面）

---

## 1. 问题陈述

为 `teleportwaypoint:waypoint` 方块从零建立 Minecraft 1.21.1 可用的方块模型，并同时提供 Blockbench 工程文件（`.bbmodel`）。

需求约束：
1. 一个悬浮的水晶球；
2. 静态模型不含发光，发光由后续代码实现；
3. 不使用方块体搭建，直接用面片拼成；
4. 水晶球位于方块空间正中央。

---

## 2. 关键决策

- **双轴十字球**：两张 10×10 的竖直正方形面片，分别位于 `z=8` 与 `x=8` 平面，沿中央竖直轴正交相交。
- **不用旋转**：Minecraft 1.21.1 方块模型元素旋转角只允许 `-45/-22.5/0/22.5/45` 度（已验证 1.21.1 客户端 jar 源码校验逻辑）。0°/90° 面片直接用零厚度轴向元素表达，完全规避角度限制。
- **圆球直径 10 像素**：球心 `(8,8,8)`，坐标范围 3~13。贴图为 16×16 全直径圆，UV `[0,0,16,16]` 映射到 10×10 面片。
- **`shade: false`**：正反面亮度一致，颜色由贴图控制，便于后续代码发光。
- **贴图新建**：`textures/block/waypoint_crystal.png`，青蓝配色，左上高光、右下阴影，无任何发光像素，同时作为 `particle`。
- 不修改任何 Java 代码，不修改 `blockstates/waypoint.json`（已指向 `block/waypoint`）。

---

## 3. 交付文件

| 文件 | 内容 |
|---|---|
| `src/main/resources/assets/teleportwaypoint/models/block/waypoint.json` | 2 个零厚度元素、4 个面（north/south/east/west）、UV `[0,0,16,16]`、`shade:false` |
| `src/main/resources/assets/teleportwaypoint/textures/block/waypoint_crystal.png` | 16×16 RGBA 青蓝圆形水晶贴图 |
| `blockbench/waypoint.bbmodel` | Blockbench 工程文件（`format_version: 4.10`、`model_format: java_block`），内嵌贴图 |

---

## 4. 验收标准

1. 模型 JSON 可被游戏资源加载器解析（坐标合法、无非法旋转、纹理引用存在）。
2. 引用链完整：`blockstate → model → textures(#crystal / particle)`。
3. `.bbmodel` 为合法 JSON，内嵌贴图可解码为 16×16 PNG，几何与导出 JSON 一致。
4. 游戏内：方块中央出现直径 10 像素悬浮青蓝水晶球，正对方向完整圆形，不被相邻方块遮挡。
5. 贴图无发光像素。

---

## 5. 非目标

- 口袋锚点（`pocket_waypoint`）的模型。
- 发光、红/蓝激活外观（BER 后续代码实现）。
- 物品模型与合成/掉落表。

---

## 6. 后续步骤

按本设计生成三个交付文件并逐条验收。
