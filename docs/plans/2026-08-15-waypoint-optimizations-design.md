# 传送锚点优化（悬浮水晶 / 交互逻辑 / 激活音效）设计文档

**日期：** 2026-08-15
**状态：** 已确认（Approved）
**方案：** A —— caps 拆分为 4 颗独立模型分相位浮动；onUse 未激活仅激活；激活播经验球音效

---

## 1. 问题陈述（Problem Statement）

三项优化：
1. **悬浮水晶**：四根柱子上的柱顶水晶改为悬浮的规整立方体（2×2×2），类似末地水晶上下移动。
2. **交互逻辑**：玩家与未解锁的传送锚点/口袋锚点交互时不再弹出 GUI；只有已解锁激活后交互才打开 GUI。
3. **激活音效**：激活传送锚点后播放"经验的声音"。

## 2. 设计（Design）

### 2.1 架构（Architecture）

```
需求 1：悬浮水晶
  静态层 waypoint.json（5 元素）→ 4 根支柱补顶面（waypoint_pillar_top 贴图，防悬浮后露空心）
  动态层（BER，全亮）：
    青色组：caps_nw/ne/sw/se（4 颗独立 2×2×2 立方体，悬浮柱顶上方 y11-13）
           + crystal / ring / orb（既有，不变）
    红色组：caps_{nw,ne,sw,se}_red + crystal_red / ring_red / orb_red
  动画：四颗水晶各自 y = 0.25·sin(t·0.1 + φᵢ) 格浮动（φᵢ = 0/90/180/270 错开相位）
      中央晶核/能量环/光球动画不变
  （旧 caps 单文件模型废弃删除）

需求 2+3：交互与音效
  WaypointManager.onUse（两方块共用）：
    右键未激活 → activate（聊天提示）+ 播放 EXPERIENCE_ORB_PICKUP（方块位置）
               → return（不弹 GUI）
    右键已激活 → openListScreen（传送列表 GUI）
    潜行+右键（可改名）→ 改名 GUI（不变）
```

### 2.2 组件（Components）

| 组件 | 文件 | 改动 |
|---|---|---|
| 水晶模型 ×8 | `waypoint_caps_{nw,ne,sw,se}.json` + `_red` 版 | 新增（2×2×2 立方体，柱心上方 y11-13；生成脚本产出） |
| 静态层 | `models/block/waypoint.json` | 4 根支柱补 `up` 面（`waypoint_pillar_top` 贴图） |
| 生成脚本 | `blockbench/tools/generate_float_caps.mjs` | 新增（幂等：立方体 caps 生成 + 支柱补面 + 旧 caps 清理 + 自检） |
| 渲染器 | `WaypointBlockEntityRenderer.java` | caps 常量 ×8（4 位置 × 红/青）；分相位浮动渲染 4 颗水晶 |
| 客户端注册 | `TeleportWaypointClient.java` | 附加模型注册 → 14 个 |
| 交互/音效 | `WaypointManager.java` | `onUse`：未激活仅激活不弹 GUI；`activate`：播放经验球拾取音 |

### 2.3 数据流（Data Flow）

```
需求 2：右键未解锁锚点 → onUse（服务端）→ activate（落盘 + syncTo + 聊天提示）
      → 播放 EXPERIENCE_ORB_PICKUP（方块位置）→ return（不弹 GUI）
      → 下一帧 ClientWaypointState 更新 → 渲染切青色
需求 2b：右键已解锁锚点 → openListScreen（不变）
需求 1：BER 每帧 t → 4 颗水晶 y = 0.25·sin(t·0.1 + φᵢ)，φᵢ = 0/90/180/270
      → 与红/青组选色叠加
```

### 2.4 错误处理（Error Handling）

| 场景 | 处理 |
|---|---|
| 水晶模型加载失败 | missing model 兜底（生成脚本自检） |
| 激活音效播放失败 | 无副作用（MC 音效系统容错） |
| `activate` 时 uid 为 null | 既有 return 逻辑不变 |
| 客户端激活集合未同步 | 红色兜底（既有） |

### 2.5 测试策略（Testing Strategy）

1. 悬浮：四颗水晶离开柱顶（柱顶面可见），错开相位上下浮动，夜晚全亮；红/青两组一致。
2. 交互：右键未解锁 → 提示+音效+变青，无 GUI；再右键 → 列表 GUI；潜行+右键改名可用。
3. 口袋锚点：共用 onUse，同样不弹 GUI；已激活后右键 → 列表 GUI。
4. 回归：传送、改名、红/青切换、中央动画正常。
5. 多人：A 已激活（弹 GUI）、B 未激活（仅激活）。

### 2.6 性能（Performance）

- 每帧多渲染 4 颗 6 面立方体，可忽略；模型 map 查询 +4 次（O(1)）。
- 服务端激活路径新增一次音效调用，无额外网络包。

## 3. 决策记录（Decisions Made）

- **caps 拆分 4 颗独立模型**：四颗错开相位浮动的前提（单模型只能整体变换）。
- **规整 2×2×2 立方体**：悬浮柱顶上方 1 格（y11-13），浮动幅度 ±0.25 格不触碰柱顶。
- **支柱补顶面**：悬浮后柱顶露出，启用备用的 waypoint_pillar_top 贴图。
- **未解锁右键仅激活**：提示 + 音效 + 状态切换，不弹 GUI；已解锁才开列表。
- **音效 = EXPERIENCE_ORB_PICKUP**（经验球拾取音），方块位置播放，无条件（放置/右键激活均触发）。

## 4. 非目标（Non-Goals）

- 口袋锚点的悬浮水晶（其静态模型缺失问题另行处理）。
- 音效的差异化（未解锁/已解锁不同音效）——本期不做。
- 激活提示消息样式调整。

## 5. 后续步骤（Next Steps）

1. 生成脚本：8 个 caps 模型 + 支柱补面 + 清理旧 caps + 自检。
2. 渲染器：caps 常量与分相位渲染。
3. 客户端注册 14 个模型。
4. WaypointManager：onUse 与 activate 音效。
5. 构建实测。
