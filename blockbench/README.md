# blockbench 资源目录说明

本目录存放「传送锚点」相关的 Blockbench 工具与说明。

> ⚠️ 注意：此前目录中的 `waypoint.bbmodel`、`大肥鱼做的传送锚点.bbmodel`、
> `小龙娘做的传送锚点.bbmodel` 及贴图副本已被外部清理（不在本目录中）。
> 游戏实际使用的模型与贴图位于
> `src/main/resources/assets/teleportwaypoint/` 下，不受影响。

## 模型拆分（动态效果）

传送锚点在游戏中的渲染 = **静态层 + 动态层（BER 动画）**：

| 文件（`models/block/`） | 层 | 内容 | 动画 |
|---|---|---|---|
| `waypoint.json` | 静态 | 底座 + 4 支柱 + 4 柱顶水晶（9 元素） | 无（blockstate 渲染） |
| `waypoint_crystal.json` | 动态 | 晶核下/上（双层 45° 菱形棱柱） | 绕 Y 轴自转，360°/12s |
| `waypoint_ring.json` | 动态 | 能量环（4 根环梁） | 反向慢转 360°/24s + 上下浮动 |
| `waypoint_orb.json` | 动态 | 核心光球 | 上下浮动 + 呼吸式脉冲缩放 |

- 动态层由 `WaypointBlockEntityRenderer`（BER）叠加渲染，**全亮自发光**
  （`LightTexture.FULL_BRIGHT`），夜晚也能看到青色能量核心。
- 口袋锚点（pocket_waypoint）暂不叠加动态层。
- 三个动态模型经 `ModelEvent.RegisterAdditional` 注册为附加模型。

## 工具

- `tools/split_model.mjs` —— 把完整 16 元素模型拆分为静态层 + 3 个动态层 JSON
  （依据元素顺序拆分，带几何断言防漂移）。运行：`node tools/split_model.mjs`
- 动画参数集中在 `WaypointBlockEntityRenderer.java` 的 `render()` 中，改数值即可调速。
