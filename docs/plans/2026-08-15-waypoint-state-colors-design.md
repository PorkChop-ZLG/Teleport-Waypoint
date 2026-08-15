# 传送锚点红/蓝状态外观（Per-Player State Colors）设计文档

**日期：** 2026-08-15
**状态：** 已确认（Approved）
**方案：** A2 —— 红/青双模型组，BER 按玩家激活状态切换（默认青色零改动）

> **修订历史：** v1 方案 A（整体乘色染红/蓝）经游戏实测被否定——纯色覆盖石材导致细节丢失。
> 本版本修订为：已解锁保持现有青色设计原样，仅未解锁渲染红色主题发光体。

---

## 1. 问题陈述（Problem Statement）

传送锚点存在「解锁 / 未解锁」两种 per-player 状态，需要按玩家视角渲染区分。
设计约束：
- per-player 区分**必须走 BER**（blockstate tint 全局共享，无玩家概念）。
- 现有默认贴图已是青色/蓝色主题——**已解锁状态应保持现状（零改动）**。
- 简单乘色不可行：青色贴图乘红色会把青色像素压成暗黑（红色分量缺失），
  效果正是实测否定的"暗红黑"——红色必须用**红色主题贴图**。

## 2. 设计（Design）

### 2.1 架构（Architecture）

```
状态判定（每帧）：ClientWaypointState.isActivated(uid)
                ├─ 已解锁 → 渲染「青色组」（现有设计，零改动）
                └─ 未解锁 → 渲染「红色组」（红色主题贴图）

模型结构（拆分调整）：
  blockstate 模型 waypoint.json      静态层（底座+4支柱，本色石材，青色符文保留）
  ── BER 附加模型（全亮自发光）──
  青色组（已解锁）：caps / crystal / ring / orb     ← 现有贴图，caps 由静态层拆出
  红色组（未解锁）：caps_red / crystal_red / ring_red / orb_red   ← 新红色贴图

动画：红/青两组共用同一套变换（晶核自转、环反向旋转浮动、光球呼吸），仅模型引用切换。
```

### 2.2 组件（Components）

| 组件 | 文件 | 改动 |
|---|---|---|
| 红色贴图 ×3 | `textures/block/waypoint_{crystal,ring,orb}_red.png` | 新增（脚本按青色绘制逻辑改调色板生成） |
| 模型 JSON ×5 | `waypoint_caps.json`、`waypoint_{caps,crystal,ring,orb}_red.json` | 新增；`waypoint.json` 改为 5 元素（水晶拆出） |
| 生成脚本 | `blockbench/tools/generate_red_variant.mjs` | 新增（红贴图 + 模型拆分/生成 + 自检） |
| 渲染器 | `WaypointBlockEntityRenderer.java` | 按状态选红/青模型组；新增 5 个模型引用；清理诊断日志 |
| 客户端注册 | `TeleportWaypointClient.java` | 注册 5 个新附加模型；清理注册日志 |

### 2.3 数据流（Data Flow）

```
服务端：放置 → WaypointManager.activate → PlayerWaypointData 落盘
        → syncTo → SyncActivatedWaypointsPayload（S→C）
客户端：ClientWaypointState.setActivated 更新本地集合
渲染：  BER 每帧 → be.getExistingUid()
        → ClientWaypointState.isActivated(uid)？
          ├─ true  → 青色组（现有贴图，零改动）
          └─ false → 红色组
        两组共用同一套动画变换
```

激活/删除即时生效：服务端推送 → 客户端集合更新 → 下一帧切换红/青，无需重载区块。

### 2.4 错误处理（Error Handling）

| 场景 | 处理 |
|---|---|
| `getExistingUid()` 为 null（同步未完成） | 按未解锁处理 → 红色（保守默认） |
| 客户端激活集合为空（首次登录） | 同上，红色兜底 |
| 红色模型/贴图加载失败 | missing model 兜底（生成脚本自检保证文件存在） |
| 玩家切换（多人） | 每帧重新查询，天然正确 |

### 2.5 测试策略（Testing Strategy）

1. 默认观感回归：放置锚点（自动激活）→ 青色设计，与实施前逐像素一致。
2. 状态切换：删除激活记录 → 发光元素立即变红（柱顶水晶+晶核+环+光球），石材本色、青色符文保留 → 再激活 → 变回青色。
3. 动画回归：红/青两组动画行为一致。
4. 夜晚验证：未解锁时红色发光在夜晚清晰可见。
5. 多人验证：A 玩家已激活看青色、B 玩家未激活看红色。

### 2.6 性能（Performance）

- 每帧仅多做 4 次模型 map 查询（O(1)）；渲染面数与现在相同（同一几何，仅贴图差异）。
- 柱顶水晶移入 BER 后由 9 面/颗 → 全亮渲染，代价可忽略。

## 3. 决策记录（Decisions Made）

- **方案 A2（红/青双模型组）**：默认（已解锁）状态零改动，未解锁渲染红色主题贴图——避免乘色导致的暗黑效果，风格统一。
- **柱顶水晶拆入动态层**：是它们随状态变色的前提，同时获得全亮发光效果。
- **红色贴图 = 青色绘制逻辑的色相变体**：刻面渐变/符文刻度/径向辉光结构不变，仅调色板青→红。
- **caps 无动画**（静止），其余三个部件动画不变。

## 4. 非目标（Non-Goals）

- 口袋锚点（pocket_waypoint）的状态外观——本期不做。
- 石材底座/支柱的符文随状态变色（保持青色装饰）。
- 红色模型的动画参数与青色不同——本期不做差异化。

## 5. 后续步骤（Next Steps）

1. 生成红色贴图与模型 JSON（脚本）。
2. 修改 `WaypointBlockEntityRenderer`（状态选组 + caps 渲染 + 清理日志）。
3. 修改 `TeleportWaypointClient`（注册新附加模型）。
4. 构建并在游戏中实测状态切换与动画回归。
