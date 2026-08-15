# blockbench 资源目录说明

本目录存放「传送锚点」模型的生成工具与说明。
游戏实际使用的模型与贴图位于 `src/main/resources/assets/teleportwaypoint/` 下。

## 模型结构（当前）

传送锚点在游戏中的渲染 = **静态层（blockstate）+ 动态层（BER 动画）**：

| 文件（`models/block/`） | 层 | 内容 | 动画 |
|---|---|---|---|
| `waypoint.json` | 静态 | 底座 + 4 支柱（5 元素，青色符文石材） | 无（blockstate 渲染，waypoint 与 pocket_waypoint 共用） |
| `waypoint_caps_{nw,ne,sw,se}.json` | 动态 | 四颗悬浮 2×2×2 水晶（y12-14） | 各自错相上下浮动（±0.125 格） |
| `waypoint_crystal.json` | 动态 | 晶核下/上（双层 45° 菱形棱柱） | 绕 Y 轴自转，360°/12s |
| `waypoint_ring.json` | 动态 | 能量环（4 根环梁） | 反向慢转 360°/24s + 上下浮动 |
| `waypoint_orb.json` | 动态 | 核心光球（y14.25-15.75） | 上下浮动 + 呼吸式脉冲缩放 |

### 配色组（per-player 状态外观）

| 方块 | 未解锁 | 已解锁 |
|---|---|---|
| 传送锚点（waypoint） | 红色组（`_red` 后缀） | 青色组（默认） |
| 口袋锚点（pocket_waypoint） | 黄色组（`_yellow` 后缀） | 绿色组（`_green` 后缀） |

每个配色组包含：caps ×4 + crystal + ring + orb 共 6 个模型文件。
动态层由 `WaypointBlockEntityRenderer`（BER）叠加渲染，**全亮自发光**
（`LightTexture.FULL_BRIGHT`），夜晚清晰可见。

## 工具

- `tools/generate_float_caps.mjs` —— 悬浮水晶模型（4 位置 × 4 色调：青/红/绿/黄）、
  纯色贴图（`waypoint_caps*.png`）、支柱顶面补齐、光球上移（防穿模）、自检。
  运行：`node tools/generate_float_caps.mjs`
- `tools/generate_gradient_variants.mjs` —— 渐变贴图变体（红/绿/黄 × crystal/ring/orb）
  及对应模型 JSON，几何/UV 与青色版一致。运行：`node tools/generate_gradient_variants.mjs`

两者均幂等可复跑，含不穿模数学断言（caps 最低 y11.875 > 柱顶 10；光球最低 y14.125 > 晶核顶 13）。
动画参数集中在 `WaypointBlockEntityRenderer.java` 的 `render()` 中。
