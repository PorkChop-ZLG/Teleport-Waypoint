# 项目审查处理实施计划

**Goal:** 处理审查清单 A1/A2/A3/A4/A5/A7，并记录其余未处理项
**Architecture:** 口袋锚点复用传送锚点模型架构（静态层 blockstate + 动态层 BER），新增绿/黄双模型组；清理过时工具与模板；修复 deprecated API
**Approach:** 用户选定——口袋锚点绿/黄双模型组（非乘色）；其余按审查清单逐项处理

---

### Task 1: 生成口袋锚点绿/黄变体资源（贴图 + 模型）

**Files:**
- Create: `blockbench/tools/generate_gradient_variants.mjs`（参数化色调生成器：渐变贴图变体 + 模型 JSON）
- Modify: `blockbench/tools/generate_float_caps.mjs`（caps 模型与纯色贴图支持 green/yellow 色调）
- Create: `src/main/resources/assets/teleportwaypoint/textures/block/waypoint_crystal_green.png`、`waypoint_crystal_yellow.png`、`waypoint_ring_green.png`、`waypoint_ring_yellow.png`、`waypoint_orb_green.png`、`waypoint_orb_yellow.png`、`waypoint_caps_green.png`、`waypoint_caps_yellow.png`
- Create: `src/main/resources/assets/teleportwaypoint/models/block/waypoint_caps_{nw,ne,sw,se}_{green,yellow}.json`（×8）、`waypoint_{crystal,ring,orb}_{green,yellow}.json`（×6）

**Steps:**
1. 扩展 `generate_float_caps.mjs`：caps 模型生成支持 4 色调（青/红/绿/黄），纯色贴图生成 `waypoint_caps_green.png`（淡绿 #9ff5a8）/ `waypoint_caps_yellow.png`（淡黄 #f7f2a0）
2. 新建 `generate_gradient_variants.mjs`：参数化调色板（红/绿/黄）复用现有绘制逻辑生成 crystal/ring/orb 渐变变体贴图 + 对应模型 JSON（引用变体贴图）；自检含纹理引用/UV/色值断言
3. 运行两个脚本，确认全部校验通过

**Verification:** `node blockbench/tools/generate_float_caps.mjs && node blockbench/tools/generate_gradient_variants.mjs` 输出"全部校验通过"

---

### Task 2: 渲染器三态改造（waypoint 青/红，pocket 绿/黄）

**Files:**
- Modify: `src/main/java/com/zonlong/teleportwaypoint/client/render/WaypointBlockEntityRenderer.java`

**Steps:**
1. 去掉 `render()` 中的 `isPocketWaypoint()` 跳过
2. 新增模型常量：绿色组（`CAPS_GREEN_MODELS` ×4 + `CRYSTAL_GREEN_MODEL`/`RING_GREEN_MODEL`/`ORB_GREEN_MODEL`）、黄色组（同 ×4）
3. `render()` 按方块类型选择模型组：waypoint → 已激活青组/未激活红组；pocket → 已激活绿组/未激活黄组
4. 静态层仍由 blockstate 渲染（两方块均 RenderShape.MODEL，不改）

**Verification:** `gradle build` 编译通过（含 Task 5 一并验证）

---

### Task 3: 客户端注册绿/黄附加模型 + 修复 pocket blockstate

**Files:**
- Modify: `src/main/java/com/zonlong/teleportwaypoint/TeleportWaypointClient.java`（注册 +14 个模型：caps×8 + crystal/ring/orb×6）
- Modify: `src/main/resources/assets/teleportwaypoint/blockstates/pocket_waypoint.json`（模型引用改为 `teleportwaypoint:block/waypoint`，消除缺失模型警告）

**Steps:**
1. `onRegisterAdditionalModels` 增加绿/黄组注册（沿用数组循环模式）
2. pocket blockstate 引用静态层模型文件

**Verification:** `gradle build` 通过；`node -e` 校验 blockstate 引用的模型文件存在

---

### Task 4: 修复 deprecated API（A2）

**Files:**
- Modify: `src/main/java/com/zonlong/teleportwaypoint/client/render/WaypointBlockEntityRenderer.java`

**Steps:**
1. `renderBakedModel` 中 `model.getQuads(null, direction, random)` → `model.getQuads(null, direction, random, ModelData.EMPTY, null)`（两处）
2. 新增 import：`net.neoforged.neoforge.client.model.data.ModelData`、`net.minecraft.client.renderer.RenderType`（RenderType 已有）

**Verification:** 构建输出不再有 deprecation 警告

---

### Task 5: 清理无用的历史脚本（A3）

**Files:**
- Delete: `scripts/generate_waypoint_model.mjs`、`scripts/_preview_textures.mjs`
- Delete: `blockbench/tools/split_model.mjs`、`blockbench/tools/generate_red_variant.mjs`
- Delete: `scripts/` 目录（若清空）

**Steps:** 删除四个过时脚本（红色变体生成能力已并入 Task 1 的参数化生成器）；删除空 scripts/ 目录

**Verification:** 文件不存在；`node blockbench/tools/generate_float_caps.mjs && node blockbench/tools/generate_gradient_variants.mjs` 仍可运行

---

### Task 6: 更新 README（A4）

**Files:**
- Rewrite: `blockbench/README.md`（当前结构：静态层 5 元素 + 8 悬浮 caps + 红/青/绿/黄变体 + 工具说明）
- Modify: `README.md`（功能条目：口袋锚点绿/黄状态外观与动态动画；已知限制：移除模型相关遗留描述）

**Verification:** 通读两份 README，内容与当前实现一致

---

### Task 7: 清理残留模板（A5）

**Files:**
- Modify: `src/main/java/com/zonlong/teleportwaypoint/Config.java`（删除 MDK 模板注释，保留空配置类）
- Modify: `src/main/java/com/zonlong/teleportwaypoint/TeleportWaypoint.java`（删除模板注释）
- Delete: `TEMPLATE_LICENSE.txt`（许可信息已存在于 gradle.properties `mod_license=MIT`）

**Verification:** `gradle build` 通过

---

### Task 8: 删除空目录（A7）

**Files:**
- Delete: `src/main/resources/data/teleportwaypoint/structure/`（空目录）

**Verification:** 目录不存在

---

### Task 9: 构建验证 + 审查记录文档

**Files:**
- Create: `docs/plans/2026-08-15-project-audit-notes.md`（记录：A6 性能项、未处理项——Xaero 联动、口袋锚点合成配方/掉落表、试炼密室调整、红/青/绿/黄贴图再生成说明）

**Steps:**
1. `gradle build`（Start-Process 方式）确认 BUILD SUCCESSFUL
2. 验证构建产物含全部新资源（build/resources 检查）
3. 写审查记录文档

**Verification:** BUILD SUCCESSFUL；`docs/plans/2026-08-15-project-audit-notes.md` 存在

---

## 明确不做（记录进文档）

- A6 性能项（isActivated O(n)）——记录，不修
- Xaero 地图联动、口袋锚点合成配方/掉落表、试炼密室结构调整——记录，不处理
