# 项目审查记录（2026-08-15）

**状态：** 已处理项见 `2026-08-15-project-audit-fix-plan.md`；本文记录暂不处理项与决策。

## 已处理（本次）

| 项 | 处理 |
|---|---|
| A1 口袋锚点模型 | 复用传送锚点模型架构；绿/黄双模型组（未解锁黄 / 解锁淡绿），含动态动画 |
| A2 deprecated API | `getQuads` 迁移到带 `ModelData` 的重载，构建无警告 |
| A3 历史脚本 | 删除 `scripts/`（2 个）与 `blockbench/tools/split_model.mjs`、`generate_red_variant.mjs`；生成能力并入 `generate_gradient_variants.mjs`（参数化红/绿/黄） |
| A4 README | `blockbench/README.md` 重写为当前结构；项目 README 功能条目更新 |
| A5 模板残留 | `Config.java`/`TeleportWaypoint.java` 注释清理；删除 `TEMPLATE_LICENSE.txt` |
| A7 空目录 | 删除 `src/main/resources/data/teleportwaypoint/structure/` |

## 暂不处理项（记录，未实施）

- **A6 性能**：`ClientWaypointState.isActivated` 为 List 线性查找 O(n)，每方块每帧调用。
  当前激活锚点量级小，无感知；若未来锚点数量大，可换 `Set<UUID>` 索引（数据源
  `SyncActivatedWaypointsPayload` 同步时重建）。
- **Xaero 世界地图/小地图联动**：主设计文档后续计划，需模组本体稳定后作为可选前置集成。
- **口袋锚点合成配方与掉落表**：生存模式暂无法获取口袋锚点（README 已知限制）。
- **试炼密室结构替换**：当前为测试用途（`data/minecraft/structure/trial_chambers/`），
  生成规模与方式后续可能调整。
- **口袋锚点放置行为**：放置即自动激活并弹改名界面（现有设计，未变更）。

## 资源再生成说明

- 悬浮水晶模型与纯色贴图：`blockbench/tools/generate_float_caps.mjs`（青/红/绿/黄 4 色调，幂等）。
- 渐变贴图变体（crystal/ring/orb 的红/绿/黄）：`blockbench/tools/generate_gradient_variants.mjs`（幂等）。
- 已删除的历史脚本不再提供再生能力，如需调整色调请修改上述脚本的调色板常量。
