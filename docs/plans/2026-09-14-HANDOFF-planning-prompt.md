# 交接提示词 —— 区块补锚实施计划（planning）

**用途：** 把本任务交给新会话。上一会话已完成调研与设计，上下文即将耗尽。新会话只需产出**实施计划**，不要再重新讨论设计。

---

## 直接复制给新会话的提示词

```
继续一个 Minecraft 1.21.1 NeoForge 模组的开发任务。上一会话已完成设计阶段，现在需要你调用 planning 技能，产出实施计划。

项目：D:\Minecraft\Teleport-Waypoint-ds_flash （模组 Teleport Waypoint，mod id teleportwaypoint，v0.3.0）

## 第一步：先读这三份文档，它们是唯一权威来源

1. docs/plans/2026-09-14-chunk-load-waypoint-injection-design.md
   ← 「区块补锚」方案的完整设计，已定案（14 章，决策摘要 D1–D10，讨论记录 Q1–Q16）
2. docs/plans/2026-09-14-structure-waypoint-injection-design.md
   ← 总体路线对比 + 全部引擎源码事实的出处（状态：待评审，作为参考资料）
3. memory/ 下的 project-context.md、decisions-log.md、learned-patterns.md
   ← 项目背景、历史决策、踩过的坑（memory/ 在 .gitignore 中）

读完再动手。不要重新讨论设计，设计已定案。

## 任务

为「区块补锚」方案产出实施计划（文件写到 docs/plans/2026-09-14-chunk-load-waypoint-injection-implementation.md）。

## 方案一句话

区块造好正式启用时会广播一个信号，模组在这个信号里读取「本区块含哪些结构」，若该结构通过黑白名单筛选，就按一套统一规则找个位置补放一个传送锚点方块。放置后，模组原有的登记/同步/渲染/地图链路完全复用。

## 已定案的 10 条决策（不要推翻，直接落进计划）

D1  触发：只用「首次生成」这一个门（区块加载信号里的"是否刚生成"标记）。
    不碰旧区块；锚点被玩家拆除后不会重生；**零持久化存储、零去重名单**。
    推论：存量存档完全不补放，也不需要 backfill 配置项。
D2  落点：**两层候选**（不是四层）。L1 结构内 → L2 区块兜底。
    锚点方块支持含水，所以没有"干燥位/水中位"之分，直接允许放水里。**岩浆一律排除。**
D3  waypoint_id = `<namespace>.<path>`（如 minecraft.end_city），由结构注册 ID 直接派生，**不需要映射表**。
D4  旧存档兼容：保留旧键作别名，不改玩家存档、不写迁移逻辑。
D5  旧的两个 NBT 覆盖数据包：**保留**，但把两个 defaultEnable*StructureWaypoints 默认值改为 false。
D6  黑白名单：**用数据包标签**（不是配置项），标签名硬编码，用本模组命名空间。
    - `data/teleportwaypoint/tags/worldgen/structure/waypoint_whitelist.json` —— 12 条
    - `data/teleportwaypoint/tags/worldgen/structure/waypoint_blacklist.json` —— 22 个结构，压成 9 条
    - 两者都必须 `"replace": false`（否则整合包与其他模组无法追加条目）
    - 配置只保留 `mode = "WHITELIST" | "BLACKLIST"`，默认 WHITELIST
D7  新增配置 `debugMode`（默认 false）：本模组**所有** debug 日志走该开关，
    且 **debug 文本一律英文**（防日志乱码）。配置注释也用英文。
D8  落点规则 C：自上而下 + 屋顶判据，N = 8。
    屋顶判据是**偏好不是硬约束** —— 每层内跑两遍（第 1 遍要求"上方 8 格内有实心顶"，第 2 遍退化不加）。
D9  piece 按包围盒体积降序**逐个试，成功即停**（不取并集一次算完）。
    结构锚点列**仅作排序权重，不作硬约束**。
D10 锚点显示名翻译键前缀改为 **`tpwp.`**（如 `tpwp.minecraft.end_city`，空名回退 `tpwp.empty`）。
    **仅锚点显示名用此前缀**，其余 teleportwaypoint.* 键（配置/GUI/聊天/数据包/itemGroup）保持不变。
    waypoint_id 本身不含前缀 ⇒ **线格式无改动、无协议变更**。

## 遍历顺序（必须与设计文档 §6.6.3 一致）

严格为 **层 → 遍 → piece → 列 → y**：

for layer in (L1 结构内, L2 区块兜底):
    for pass in (第1遍 有顶, 第2遍 无顶):
        for piece in 体积降序:            // L2 无 piece，直接扫区块列
            for column in 确定性排序:
                for y 自上而下:
                    if 合格 and (第2遍 or roofed(p, 8)):
                        放置（水中则显式 WATERLOGGED=true）; return

确定性由 5 元排序键保证全序：piece 体积降序 → 贴近 piece 中心 → 贴近结构锚点列 → x → z。
全序成立 ⇒ 同一结构必然落到同一格（可被 GameTest 断言）。

## 计划必须覆盖的改动清单

**新增（建议包 com.zonlong.teleportwaypoint.structure/）**
- 区块加载监听器（编排 + 强制早退顺序 + 重入守卫 + try/catch (Throwable)）
- 纯几何扫描器（参数类型收窄为 LevelChunk，让会死锁的 API 不在作用域内）
- 放置器（显式设置 WATERLOGGED、排除岩浆）
- 命名工具类（统一 tpwp. 前缀，取代现在散落 4 处的硬编码）

**修改**
- WaypointBlockEntity.isValidId：当前模式 `[a-z0-9_]+`（:36,117-119），**必须放宽为允许 `.`**
- humanize 回退函数：把 `.` 与 `_` 都当分隔符（minecraft.end_city → Minecraft End City）
- 4 处客户端前缀拼接点：client/ClientWaypointInfo.java:25、network/ActivatedWaypointInfo.java:23、
  block/entity/WaypointBlockEntity.java:114（空名回退键）、client/xaero/XaeroMinimapIntegration.java:318
- CommonConfig：新增 structureWaypoints 分组（mode + debugMode）
- 两个 defaultEnable*StructureWaypoints 默认值 → false
- 语言文件：锚点显示名改写为 tpwp.*，保留 14 个旧键作别名；新增配置项翻译键
- README：新机制说明、迁移步骤、已知限制、两套名单语义

**新增数据文件**
- waypoint_whitelist.json（12 条）、waypoint_blacklist.json（9 条 → 展开 22 个结构），均 "replace": false

## 强制纪律（写进计划的实现约束，评审必查）

1. 处理器/扫描器/放置器**全部无状态**（世界生成线程不参与，但保持无状态便于重入守卫）
2. 区块加载处理器内**禁止**跨区块访问：level.getBlockState(BlockPos)/getChunkAt/getChunk
   以及**全部** StructureManager 查询 —— 会经 managedBlock 自我阻塞并重入事件
3. 落点**严格限制在事件区块自身的 16×16 列内**
4. **整个处理体包 try/catch (Throwable)** —— 异常逃逸会让区块 future 异常完成并崩溃服务器
5. 判定 BlockEntity 时**先判 state.hasBlockEntity()**，再取 BlockEntity（否则会触发 pending BE 惰性反序列化）
6. 数据包标签：**缺失**记 WARN 一次；**为空是合法配置，不记日志**（白名单空=都不放，黑名单空=都放）
7. 所有 debug 日志走 debugMode 且文本英文

## 三个待实测决定的数值（计划里给出初值 + 实测项）

- 每区块结构数上限：建议 2
- 总探测预算（跨层累计的 isValidPlacement 调用次数）：建议 2000，超限放弃该结构并记 debug
- L2 兜底层的排序键：该层无 piece，需另行定义

## 项目验证方式（没有自动化测试框架）

- 项目**没有 src/test**，历史上多次决定不引入测试框架
- build.gradle 已有 runs.gameTestServer + neoforge.enabledGameTestNamespaces=teleportwaypoint，
  GameTest 可用但当前无任何测试源码
- 构建命令：gradlew.bat build --offline --console=plain
- 最廉价的完整 codec 测试：gradlew.bat runServer --offline（数据包注册表在世界创建前加载，
  畸形 JSON 会以 DFU 错误点名文件并中止启动）
- 建议的 GameTest 覆盖：纯几何选点断言、命名派生断言、isNewChunk 守卫、名单筛选、幂等性

## 注意

- 上游总体文档（2026-09-14-structure-waypoint-injection-design.md）状态是「待评审」，**保持原样不要改**。
  如果发现它与区块补锚文档冲突，以区块补锚文档（已定案）为准，并在实施计划里记一笔。
- 有一处设计文档自己标注的诚实边界：缩短翻译键前缀**不解决截断问题** ——
  真正的 64 字符上限在 waypoint_id 上（WaypointBlockEntity.MAX_TEXT_LENGTH），
  翻译键无显式上限。防截断要落在 waypoint_id 的派生逻辑里。
- 设计文档 §13 列的是「待实测决定」的数值，不是未决的设计问题。
```

---

## 给用户自己的备忘（不用复制）

**本会话已完成：**
- 调研：6 路子代理并行（两条路线完整设计 + 命名 + 代码库审计 + 对抗性验证 + 线程验证）
- 设计：两份文档，`docs/plans/2026-09-14-chunk-load-waypoint-injection-design.md` 已定案（D1–D10、Q1–Q16）
- 决策：全部开放问题已收口，只剩三个实现期待实测数值
- memory 三文件已同步；`memory/` 在 `.gitignore` 中

**未做：** 没有写任何生产代码，没有改现有源码。项目 git 状态只多出两份 docs 文件（untracked）。

**工作区工具：** 5 个 NBT 解析脚本 + 1 个语言键盘点脚本在 `D:\Minecraft\__pycache__\`：
- `nbt_dump.py`（基础解析器，被其他几个 import）
- `nbt_report.py`（全量数据包 NBT 汇总）
- `nbt_diff.py`（与原版 client.jar 逐方块 diff）
- `nbt_summary.py`（diff 汇总表）
- `nbt_context.py`（锚点周边 3×3 与上方净空检查）
- `lang_audit.py`（语言键盘点）

**权威参考源（下个会话可能要用）：**
- NeoForge 1.21.1 反编译源码：`D:\Minecraft\BeLoong-Core\build\nf-src`
- 原版结构清单与标签：`~\.gradle\caches\ng_execute\b6182136...\client-extra.jar`
  内 `data/minecraft/worldgen/structure/*.json`（34 个）与 `data/minecraft/tags/worldgen/structure/*.json`（13 个）
