# 区块补锚（Chunk-Load Waypoint Injection）设计文档

**日期:** 2026-09-14
**状态:** 已定案，待转 planning
**归属:** 本文件是「区块补锚」方案的专属深入设计。
**上游文档:** `docs/plans/2026-09-14-structure-waypoint-injection-design.md`（保持原样不动，作为总体路线对比与引擎事实的出处）

---

## 0. 名称

**中文名：区块补锚**
**英文副名：** Chunk-Load Waypoint Injection

命名理由（备忘）：

- 「区块」= 触发时机与作用域：只在**当前正在加载的那一个区块**内工作，不做后台遍历。
- 「补」= 动作性质：结构**造好之后**再补一个上去，而不是重建结构自身的数据。
- 「锚」= 产物：传送锚点方块。
- 刻意**不叫**「自动放置」：那是"效果"而非"机制"，无法与另一条路线（处理器／图纸注入）区分。
- 刻意**不叫**「区块补扫」：本方案**不做扫描**，叫「补扫」会让人误以为有后台遍历任务，从而误判性能成本。

---

## 1. 一句话机制

> 游戏自己记着「哪个区块里有什么结构」。区块造好正式启用时会广播一个信号，模组就在这个信号里翻开这本账；若发现结构，就按一套统一规则找个合适的位置补放一个传送锚点。放进方块后，模组原有的登记／同步／渲染／地图链路完全复用，无需改动。

---

## 2. 决策摘要

| # | 决策 | 一句话 |
|---|---|---|
| D1 | **只用「首次生成」这一个门** | 不碰旧区块，锚点可拆且不重生，零存储 |
| D2 | **落点 = 两层候选** | 结构内优先，失败回退区块内；水可放，岩浆不可；无水/干燥之分 |
| D3 | **命名 = `<namespace>.<path>`** | 直接拆注册 ID，取消映射表 |
| D4 | **旧存档保留裸值键作别名** | 不改存档、旧锚点名字不变 |
| D5 | **保留旧数据包，默认值改为 false** | 不做排除名单，配置面不增加 |
| D6 | **黑白名单用数据包标签，默认白名单模式** | `teleportwaypoint:waypoint_whitelist` / `waypoint_blacklist`，均 `"replace": false`；村庄归黑名单 |
| D7 | **新增「调试模式」配置** | 所有 debug 日志走该开关，且**文本一律英文**（防日志乱码） |
| D8 | **层内两遍：先要"有顶"，再退化** | 规则 C，N = 8；屋顶判据是偏好不是硬约束 |
| D9 | **piece 按体积降序逐个试，成功即停** | 锚点列仅作排序权重，不作硬约束 |
| D10 | **锚点显示名键前缀改为 `tpwp.`** | `tpwp.minecraft.end_city`；仅锚点显示名用该前缀，其余键不变 |

---

## 3. 与本方案绑定的既有约束（不再重开）

以下结论已在总体设计文档中经源码验证并定案，本文件不再重复论证：

| 约束 | 结论 | 出处 |
|---|---|---|
| 触发点 | `ChunkEvent.Load`，服务端主线程 | 总体文档 §2.2 注入点 2 |
| 结构数据来源 | `LevelChunk` 构造时复制的 starts / references | `LevelChunk.java:139-140` |
| 只处理「主区块」 | starts 只在主区块存在，其他区块只有 references | `ChunkGenerator.java:555-559`、`:571-604` |
| 禁止跨区块访问 | 会经 `managedBlock` 自我阻塞并重入事件 | 总体文档 §5.3 禁止清单 |
| 落点必须在本区块内 | 硬性安全要求 | 同上 |
| 登记与网络广播落在主线程 | `BlockEntity.onLoad()` 全树唯一调用点 | `Level.java:581` |
| 只写 `waypoint_id`，不写 `uid` | 让 `getUid()` 惰性生成唯一值 | 总体文档 D4 |
| 绝不触发 `setChanged()` | `WorldGenRegion.getChunk` 会抛异常 | 总体文档 §4.3 纪律 5 |
| 视觉净空 ≈1.15 格 | 取 2 格即可 | 总体文档 §2.5 |
| 锚点支持含水 | `SimpleWaterloggedBlock`，`WATERLOGGED` 属性 | `WaypointBlock.java:37,40`；`PocketWaypointBlock.java:36,39` |

---

## 4. 触发与去重机制（D1）

### 4.1 只用「首次生成」这一个门

引擎在区块加载时区分两种来源：

- **刚生成**（`isNewChunk() == true`）：区块地形由本次世界生成管线造出；
- **从存档读出**（`isNewChunk() == false`）：区块来自磁盘上的区域文件，包括世界重启与玩家走远再回来。

区块正式启用时广播的信号里带着这个标记。本方案**只在标记为「刚生成」时工作**，其余一律立即返回。

### 4.2 为什么这一个门就够了（去重被整体消掉）

- **每个结构只有一个主区块**：结构生成时，它的记录写在放置选中的那一个区块上；本方案只在主区块里工作。
- **主区块只会「刚生成」一次**：首次生成时补锚；此后无论世界重启还是玩家走远再回来，该区块都从存档读出（标记为 false），直接跳过。
- **因此不需要任何持久化名单**：不新增存档文件，不记录"已处理过的结构"。
- **因此不需要读周围方块判断"这里是否已有锚点"**：不存在第二次机会。
- **锚点被玩家拆除后不会重生**：拆除不改变区块的生成状态。
- **旧存档完全不受影响**：既有区块要么早已存在（读自存档），要么玩家从未去过。

**存储与去重成本：零。**

### 4.3 这个门的边界

| 边界 | 说明 | 处置 |
|---|---|---|
| 主区块已存在、结构仅向**新生成**的相邻区块延伸 | 主区块不是"刚生成" ⇒ 该结构无锚点 | 接受。判定依据是「结构生成的那一刻」 |
| 不适合放置的结构（结构内一个合格位置都没有） | 主区块只新生成一次，错过即永远没有 | 接受，记 DEBUG |
| 玩家拆除锚点后区块被强制重新生成 | 会再次补锚 | 不处理。区块重新生成本身会重建其全部内容，补锚与之一致 |

### 4.4 工作流

```
区块加载信号
  └─ 是服务端？                否 → 返回
       └─ 是「刚生成」？        否 → 返回      ← 这一行同时承担「不碰旧区块」与「不重生」
            └─ 本区块有结构记录？
                 ├─ 无 → 返回（绝大多数区块在此出局）
                 └─ 有 → 过黑白名单筛选（§5）
                      └─ 通过 → 按两层候选选点（§6）
                           ├─ 找到 → 放置锚点（登记/同步/渲染由既有链路接手）
                           └─ 找不到 → 记 DEBUG，结束
```

---

## 5. 结构筛选：黑白名单系统（D6）

### 5.1 名单用数据包标签，不用配置项

两个名单**不是配置项，而是本模组提供的两个数据包标签**：

| 标签 | 文件 |
|---|---|
| 白名单 | `data/teleportwaypoint/tags/worldgen/structure/waypoint_whitelist.json` |
| 黑名单 | `data/teleportwaypoint/tags/worldgen/structure/waypoint_blacklist.json` |

**为什么改用标签：**

1. **整合包作者改标签是本能**：标签是数据包的原生机制，作者本来就在写 `data/<ns>/tags/**`；配一个 JSON 数组字符串反而要额外学一遍。
2. **可被其他数据包扩展**：别的包只要写同名标签文件并置 `"replace": false`，就能在**不覆盖本模组文件**的前提下追加条目。配置项做不到这一点。
3. **能安全引用未安装模组的结构**：标签条目支持 `{"id": "...", "required": false}`，NeoForge 会忽略缺失的条目，因此可以预先把某模组的结构写进标签，等模组装上就自动生效。
4. **模式开关仍走配置**：只有「用哪张名单」需要玩家选择，名单内容本身不需要。

**理由 4 的直接推论：标签名硬编码在代码里**（`waypoint_whitelist` / `waypoint_blacklist`），不额外暴露为配置项 —— 少一个可以配错的地方。

配置只保留模式与调试开关：

```toml
[structureWaypoints]
    # "WHITELIST" = only place in structures listed by teleportwaypoint:waypoint_whitelist
    # "BLACKLIST" = place in every structure EXCEPT those listed by teleportwaypoint:waypoint_blacklist
    mode = "WHITELIST"

    # When true, this mod writes DEBUG-level log lines. All debug output is gated by this
    # option and is written in English only, so log files never contain mojibake.
    debugMode = false
```

**标签缺失与标签为空是两件不同的事：**

| 情况 | 行为 | 日志 |
|---|---|---|
| 标签**不存在**（数据包被禁用／文件缺失） | 白名单模式 ⇒ 一个都不放；黑名单模式 ⇒ 全部放 | **WARN 一次**（提示检查数据包） |
| 标签**存在但为空** | 白名单模式 ⇒ 一个都不放；黑名单模式 ⇒ 全部放 | **不记日志** —— 这是**合法配置**，不是错误 |
| 标签存在且有内容 | 正常筛选 | — |

> 关键点：空的 `values: []` 是**有意为之的合法配置**（"谁都不放"或"全都放"都是有效诉求），因此不能报错也不能告警。只有"文件根本不存在"才值得 WARN，因为那通常意味着数据包被玩家关掉了。

### 5.2 两个默认标签的内容

标签文件位置为 `src/main/resources/data/teleportwaypoint/tags/worldgen/structure/`。

#### `waypoint_whitelist.json` —— 12 个已覆盖结构（与现有数据包等价）

```json
{
  "replace": false,
  "values": [
    "minecraft:ancient_city",
    "minecraft:bastion_remnant",
    "minecraft:desert_pyramid",
    "minecraft:end_city",
    "minecraft:fortress",
    "minecraft:igloo",
    "minecraft:jungle_pyramid",
    "minecraft:mansion",
    "minecraft:monument",
    "minecraft:stronghold",
    "minecraft:swamp_hut",
    "minecraft:trial_chambers"
  ]
}
```

> **村庄已从白名单移除**（作者确认：语言文件里的村庄是测试用的，实际未适配、也不需要适配）。因此白名单为 12 条，而非 16 条。
>
> 这里的 12 个注册表 ID 与现有数据包覆盖的结构**一一对应** —— 虽然旧的 `waypoint_id` 是作者自定的名字（`jungle_temple`、`ocean_monument`、`woodland_mansion`、`nether_fortress`），但结构本身是这些。

#### `waypoint_blacklist.json` —— 22 个未覆盖结构（压成 9 条）

```json
{
  "replace": false,
  "values": [
    "minecraft:buried_treasure",
    "minecraft:mineshaft",
    "minecraft:nether_fossil",
    "minecraft:pillager_outpost",
    "minecraft:trail_ruins",
    "#minecraft:village",
    "#minecraft:ruined_portal",
    "#minecraft:shipwreck",
    "#minecraft:ocean_ruin"
  ]
}
```

**展开后共 22 个结构，与 `34 − 12 = 22` 完全吻合：**

| 标签 / 条目 | 展开 |
|---|---|
| `#minecraft:ruined_portal` | 7 |
| `#minecraft:village` | 5 |
| `#minecraft:shipwreck` | 2 |
| `#minecraft:ocean_ruin` | 2 |
| `minecraft:mineshaft`（标签，含 `mineshaft_mesa`） | 2 |
| `minecraft:buried_treasure` | 1 |
| `minecraft:nether_fossil` | 1 |
| `minecraft:pillager_outpost` | 1 |
| `minecraft:trail_ruins` | 1 |
| **合计** | **22** |

> **注意标签的两层语义**：`waypoint_blacklist.json` 里的 `#minecraft:village` 是**引用了原版的村庄标签**，而不是定义它。这是标签的「引用其他标签」用法。本模组自己的标签命名空间是 `teleportwaypoint`，引用原版标签时仍写 `#minecraft:...`。

### 5.3 作者如何为新模组结构接入

三步，全部在数据包侧，不改本模组任何文件：

**第 1 步：确认该模组结构的注册表 ID。**
用 `/teleportwaypoint debug structures` 打印（推荐），或查该模组 jar 内 `data/<ns>/worldgen/structure/*.json` 的文件名。

**第 2 步：把 ID 加入白名单标签。**
在**自己的**数据包里新建同名标签文件，注意 `"replace": false`：

```
你的数据包/data/teleportwaypoint/tags/worldgen/structure/waypoint_whitelist.json
```

```json
{
  "replace": false,
  "values": [
    { "id": "betterwitchhuts:witch_hut", "required": false }
  ]
}
```

`"required": false` 的含义是「这个模组没装就跳过这一条，不要让标签加载失败」。**预先写进去、等模组装上就自动生效**，这是标签机制相对配置项最大的优势。

**第 3 步：补翻译键。**
新键为 `tpwp.betterwitchhuts.witch_hut`（`<namespace>.<path>`，见 §7）。不补也不会出问题 —— 会回退到人工化名（如 `Betterwitchhuts Witch Hut`）。给整合包作者的建议是直接写进资源包的同名语言文件。

| 步骤 | 相对现有 NBT 覆盖方案的对比 |
|---|---|
| 找结构 ID | 一次调试命令 / 看 jar 内文件名 |
| 加一条标签 | **一行 JSON** |
| 补翻译键 | 两行 lang |
| 对比 | 旧方案需要手工重做整份模板 NBT（10–60 分钟且有大面积副作用） |

### 5.4 匹配语义

| 项 | 规则 |
|---|---|
| 获取 `Holder` | `getAllStarts()` 给出 `Structure` 实例，用 `Registry.wrapAsHolder(structure)` 转成 `Holder<Structure>`（`Registry.java:151`） |
| 标签判定 | `holder.is(TagKey.create(Registries.STRUCTURE, <标签 id>))`（`Holder.java:25`、`TagKey.java:35`） |
| 标签 id | `ResourceLocation.fromNamespaceAndPath("teleportwaypoint", "waypoint_whitelist")` 等，硬编码 |
| 白名单模式 | 不在白名单标签内 ⇒ 不放置 |
| 黑名单模式 | 在黑名单标签内 ⇒ 不放置 |
| 标签读取时机 | 结构标签属于 `WORLDGEN_REGISTRIES`（`RegistryDataLoader.java:80`），随世界数据包加载；本模组自己的标签常驻，不放进可开关的数据包 |
| 匹配成本 | 原版 `HolderSet` 内部处理，O(1) 级 |
| `"replace"` | 两个内置标签均为 **`false`**，使其他数据包／模组能追加条目（`true` 会整体替换，禁止使用） |
| 调试日志 | 仅当 `debugMode = true` 时输出，且**文本为英文** |

### 5.5 作者既有中文显示名 ↔ 注册表 ID 对照

白名单用注册表 ID，新翻译键（`tpwp.<namespace>.<path>`）由它派生。作者旧中文名与注册表 ID 的对应关系如下（"旧裸值键"一列即 §7.3 要保留的别名）：

| 注册表 ID | 派生的 `waypoint_id` | 作者既有中文名 | 旧裸值键（保留为别名） |
|---|---|---|---|
| `minecraft:ancient_city` | `minecraft.ancient_city` | 远古城市 | `ancient_city` |
| `minecraft:bastion_remnant` | `minecraft.bastion_remnant` | 堡垒遗迹 | `bastion_remnant` |
| `minecraft:desert_pyramid` | `minecraft.desert_pyramid` | 沙漠神殿 | `desert_pyramid` |
| `minecraft:end_city` | `minecraft.end_city` | 末地城 | `end_city` |
| `minecraft:fortress` | `minecraft.fortress` | 下界要塞 | `nether_fortress` |
| `minecraft:igloo` | `minecraft.igloo` | 雪屋 | `igloo` |
| `minecraft:jungle_pyramid` | `minecraft.jungle_pyramid` | 丛林神庙 | `jungle_temple` |
| `minecraft:mansion` | `minecraft.mansion` | 林地府邸 | `woodland_mansion` |
| `minecraft:monument` | `minecraft.monument` | 海底神殿 | `ocean_monument` |
| `minecraft:stronghold` | `minecraft.stronghold` | 要塞 | `stronghold` |
| `minecraft:swamp_hut` | `minecraft.swamp_hut` | 沼泽小屋 | `swamp_hut` |
| `minecraft:trial_chambers` | `minecraft.trial_chambers` | 试炼密室 | `trial_chambers` |

**新键中需要新中文名的有 4 个**：`minecraft.fortress`（下界要塞）、`minecraft.jungle_pyramid`（丛林神庙）、`minecraft.mansion`（林地府邸）、`minecraft.monument`（海底神殿）。旧键作为别名保留，因此旧存档的显示名不受影响。

**村庄的 5 个新键不需要补**：村庄已在黑名单里，不会被自动放置。语言文件中既有的 `village` 键属于测试遗留，可以删除或保留（保留无害）。

### 5.6 下沉到 planning 的细节

1. 标签不存在／为空时的 WARN 只记一次（用静态布尔去重，避免刷屏）；
2. `Registry.wrapAsHolder` 在 `getAllStarts()` 的键上是否总能成功（键来自注册表，应当可以）；
3. 两个内置标签使用 `"replace": false`（**已定案**）。`false` 的语义是"与已有条目**合并**"，这正是让整合包与其他模组能追加条目的机制；`true` 会整体替换掉其他包的贡献，因此**必须**用 `false`。
4. 所有 debug 日志必须走 `debugMode` 开关，且**文本一律英文**（见 §5.1 与 §11）。
## 6. 落点判据（D2）

### 6.1 两层候选

**锚点方块支持含水，因此"干燥位"不需要单独成层** —— 直接允许放在水中，候选层从四层降为两层：

| 层 | 范围 | 液体 | 用途 |
|---|---|---|---|
| **L1 结构内** | 结构 piece 包围盒与本区块的交集 | 允许水、**排除岩浆** | 首选：在结构体积内 |
| **L2 区块兜底** | 整个区块列 | 允许水、**排除岩浆** | L1 全败时的退路 |

分层顺序严格串行：**L1 全败才试 L2**。

> 这一简化直接砍掉一半的搜索轮次（原设计每层还要按"干燥/含水"各跑一遍），是本方案性能模型里最实在的一项优化。

### 6.2 「合格位置」的定义

一个位置 `p` 合格，当且仅当：

1. `p.y` 在 `[minBuildHeight+1, maxBuildHeight-1]` 内；
2. **下方**是实心方块、非流体、且无 BlockEntity（防止悬空）；
3. **本格**是空气或可替换方块，且无 BlockEntity（排除箱子／刷怪笼／vault／讲台等）；
4. **上方连续 2 格**同样满足第 3 条（对应约 1.15 格的视觉净空）；
5. **液体规则**：`p`、`above(1)`、`above(2)` 三格的流体**只能是水或空**，**任何一格是岩浆即淘汰**。

### 6.3 放置时的实现要点

- **必须显式设置 `WATERLOGGED`**：方块状态默认是 `waterlogged=false`，在水中会形成空腔。放置器在检测到目标位置是水时，要主动写 `WATERLOGGED=true`（`WaypointBlock.WATERLOGGED`，`WaypointBlock.java:40`）。由于两层候选都允许水，这条在**每一层**都适用。
- **岩浆显式排除**：锚点只实现 `SimpleWaterloggedBlock`，不支持含岩浆；放进岩浆会导致方块被破坏，出现"补了又没了"的困惑。
- **BlockEntity 判定的成本优化**：先判 `state.hasBlockEntity()`，为 false 时直接跳过，不要直接调 `chunk.getBlockEntity(pos)`。

### 6.4 工作量上限

**L2（区块兜底）的向下扫描设人为上限 64 格。**

- 扫描本身天然有界（单个区块列，最高 384 格），不会变成无界遍历；64 格是为了杜绝极端情况下的多余开销。
- 上限只作用于 L2；L1 的范围由 piece 包围盒与本区块的交集自然界定。

### 6.5 下沉到 planning 的细节

1. piece 包围盒全部落在本区块之外的退化情形；
2. piece 包围盒退化为零体积（旋转／镜像产生）时的处置；
3. 多层候选的排序与并列打破规则（保证确定性）。

---

### 6.6 落点算法的具体化

本节把 §6.1–§6.4 展开到可实现的程度。

#### 6.6.1 搜索范围的定义

| 符号 | 定义 |
|---|---|
| **区块列** | 当前事件区块的 16×16 列，加可建造高度区间 `[minBuildHeight+1, maxBuildHeight-1]` |
| **锚点列** | 结构 `pos` 参数的 XZ（即首个 piece 包围盒的中心），见总体文档"唯一确定性锚点" |
| **piece 范围** | `piece.getBoundingBox()` 与**区块列**的交集（必须求交，因为 piece 可跨多个区块） |

**L1 的搜索范围 = 所有与本区块相交的 piece 的范围之并。**
**L2 的搜索范围 = 整个区块列。**

#### 6.6.2 「合格位置」的逐项判定

对位置 `p`，按顺序判定，任一项不过即淘汰：

| 序号 | 判定 | 说明 |
|---|---|---|
| 1 | `p.y` 在 `[minBuildHeight+1, maxBuildHeight-1]` | 越界直接淘汰 |
| 2 | `down = getBlockState(p.below())` 满足：非空气、非流体、`down.isFaceSturdy(level, p.below(), UP)` | 必须有地板 |
| 3 | `down` 无 BlockEntity（先判 `down.hasBlockEntity()`） | 不把锚点架在箱子上 |
| 4 | `at = getBlockState(p)` 满足 `at.isAir() || at.canBeReplaced()` | 可放置 |
| 5 | `at` 无 BlockEntity | 不覆盖箱子／刷怪笼／vault 等 |
| 6 | `p.above(1)` 与 `p.above(2)` 均满足 `isAir() || canBeReplaced()`，且均无 BlockEntity | 对应 ≈1.15 格的视觉净空 |
| 7 | **液体**：`p`、`above(1)`、`above(2)` 三格的流体**只能是水或空**；**任何一格是岩浆即淘汰**。`at` 为水时放置器须置 `WATERLOGGED=true` | 见 §6.2 与 §6.3 |

> 判定的调用顺序按上表序号执行，让最便宜的检查最先短路。第 3/5 步必须先判 `hasBlockEntity()` 再取 BlockEntity，避免 `getBlockEntity` 触发 pending BE 的惰性反序列化。

#### 6.6.3 层内的两遍策略与 y 方向枚举顺序

**每一层（L1/L2）内部再分两遍**：

| 遍 | 条件 | 说明 |
|---|---|---|
| **第 1 遍** | 候选额外满足「**上方 N 格内有实心顶**」 | 近似"在室内"。N = **8**（已定） |
| **第 2 遍** | 不加屋顶条件 | 第 1 遍全败时的退化，保证露天结构仍能放置 |

**屋顶判据的精确定义**（对候选位置 `p`）：

```
roofed(p):
    for k in 1..N:                       // N = 8
        b = getBlockState(p.above(k))
        if b.isAir() or b 的流体非空: continue   // 空气/流体不构成顶，继续向上找
        return b.isFaceSturdy(level, p.above(k), DOWN)   // 第一个非空气块就是顶
    return false                          // N 格内没找到任何非空气块
```

这样定义的效果：

| 位置 | 判定 | 结果 |
|---|---|---|
| 屋顶**上方**的空气（村庄屋顶之上） | 向上 8 格内无任何非空气块 | `roofed = false` → 落入第 2 遍 |
| 屋内**地面**（上方是屋顶） | 第一个非空气块是屋顶 | `roofed = true` → 第 1 遍即命中 |
| 屋内**半空**（上方 2 格空气、第 3 格屋顶） | 同上 | `roofed = true`，且 y 更高 ⇒ 优先于地面 |
| 洞穴内 | 第一个非空气块是岩层 | `roofed = true` |
| 露天地面 | 向上 8 格皆空 | `roofed = false` → 第 2 遍 |
| **水下**（海洋神殿内部） | 向上第一个非空气块若是海底岩层 | `roofed = true` |

**y 枚举方向**：第 1 遍与第 2 遍内均为**自上而下**，取第一个合格位置。

**piece 的遍历**：每层内**按包围盒体积降序逐个 piece 尝试，成功即停**（已定）。完整枚举顺序：

```
for layer in (L1 结构内, L2 区块兜底):
    for pass in (第1遍 有顶, 第2遍 无顶):
        for piece in 体积降序:            // L2 无 piece，直接扫区块列
            for column in 确定性排序:
                for y 自上而下:
                    if 合格 and (第2遍 or roofed(p, 8)):
                        放置（水中则 WATERLOGGED=true）; return
```

#### 6.6.4 列的确定性排序

同一层内可能有多个合格列，必须给出**全序**，否则同一结构在不同机器上会落到不同位置。

对每个候选列计算 5 元排序键，**升序取第一个**：

| 优先级 | 键 | 目的 |
|---|---|---|
| 1 | `pieceIndex` | 大 piece 优先（房间 > 连接件） |
| 2 | `chebyshev(列, piece 包围盒中心)` | 贴近 piece 中心，落到房间中部的概率更高 |
| 3 | `chebyshev(列, 锚点列)` | 贴近结构锚点 |
| 4 | `x` | 打破并列 |
| 5 | `z` | 打破并列 |

**piece 之间**：按包围盒**体积降序**；体积相同按包围盒 `minX`、`minZ` 升序打破并列。

**piece 内部**：`chebyshev(列, piece 包围盒中心)` 升序。

全序成立 ⇒ 同一结构在同一版本、同一数据包集合下**必然落到同一格**。这是可验证的（GameTest 可以用固定输入断言落点）。

#### 6.6.5 伪代码

遍历顺序严格为 **层 → 遍 → piece → 列 → y**：

```
handle(level, chunk, start):
    if !start.isValid(): return
    sid = registry.getKey(start.getStructure()) ?? return
    if !tagAllows(sid): return

    chunkMinX = chunk.getPos().getMinBlockX(); chunkMinZ = chunk.getPos().getMinBlockZ()
    chunkMaxX = chunkMinX + 15;                chunkMaxZ = chunkMinZ + 15
    anchorXZ  = (start.pos.getX(), start.pos.getZ())        // 结构锚点列

    pieces = [p for p in start.getPieces()
              if intersects(p.bbox, chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ)]
    sortDescendingByVolume(pieces)

    // ---- L1 结构内 ----
    for requireRoof in (true, false):
        for piece in pieces:                          // 体积降序，逐个试，成功即停
            cols = clampToChunk(intersect(piece.bbox, chunkColumn))
            for (x, z) in columnOrder(cols, piece.bbox.centerXZ, anchorXZ):
                for y from min(piece.bbox.maxY, maxBuild-1) down to max(piece.bbox.minY, minBuild+1):
                    if budgetExhausted(): goto FALLBACK
                    if isValidPlacement(level, (x,y,z))
                       and (!requireRoof or roofed(level, (x,y,z), 8)):
                        place(level, (x,y,z)); return

    // ---- L2 区块兜底 ----
    FALLBACK:
    for requireRoof in (true, false):
        for (x, z) in columnOrder(chunkColumn, chunkCenterXZ, anchorXZ):
            for y from maxBuild-1 down to minBuild+1, at most 64 steps:
                if budgetExhausted(): break
                if isValidPlacement(level, (x,y,z))
                   and (!requireRoof or roofed(level, (x,y,z), 8)):
                    place(level, (x,y,z)); return

    debug("no valid placement: structure=%s chunk=%s", sid, chunk.getPos())

isValidPlacement(level, p):
    if p.y <= minBuild or p.y >= maxBuild: return false
    down = level.getBlockState(p.below())
    if down.isAir() or !down.getFluidState().isEmpty(): return false
    if !down.isFaceSturdy(level, p.below(), UP): return false
    if down.hasBlockEntity(): return false
    for k in 0..2:                                   // 本格 + 上方 2 格
        b = level.getBlockState(p.above(k))
        if !(b.isAir() or b.canBeReplaced()): return false
        if b.hasBlockEntity(): return false
        if isLava(b): return false                   // 岩浆一律排除
    return true

roofed(level, p, n):
    for k in 1..n:
        b = level.getBlockState(p.above(k))
        if b.isAir() or !b.getFluidState().isEmpty(): continue
        return b.isFaceSturdy(level, p.above(k), DOWN)
    return false

place(level, p):
    state = WAYPOINT.defaultBlockState()
    if level.getFluidState(p).getType() == Fluids.WATER:
        state = state.setValue(WATERLOGGED, true)    // 否则水中会形成空腔
    level.setBlock(p, state, UPDATE_ALL)
    // BE 的 waypoint_id 由 StructureBlockInfo 的 NBT 或 setBlock 后显式设置
```

> 所有 `getBlockState` 的接收者都是**事件区块自身**（`LevelChunk`），绝不跨区块 —— 见 §3 的禁止清单。

#### 6.6.6 探测预算

§6.4 已定 L2 的 y 扫描上限 64 格。另加一个**总探测预算**（跨层累计的 `isValidPlacement` 调用次数），建议初值 **2000 次**，超限即放弃该结构并记 debug。

理由：极端地形（放大化、超平坦上的巨构）下的候选数可能远超预期；预算给出一个硬上界，代价是极少数结构没有锚点。

#### 6.6.7 下沉到 planning 的待定细节

1. **层内 y 方向**：当前选"自上而下"（结构内取最高的合格点）。对露天结构这意味着锚点落在屋顶上方；若要落到屋内，需要额外的屋顶判据（见 §6.7）。
2. **piece 的选取**：当前是"所有与本区块相交的 piece 取并，按体积降序逐个试"。是否应改为"只取本区块内**体积最大**的那个 piece"以减少搜索面。
3. **锚点列的作用**：当前仅用于排序权重（第 3 键）。是否需要把它提升为"必须落在锚点列半径 R 内"的硬约束。
4. L2 兜底层是否需要 `pieceIndex` 之外的额外排序键。

### 6.7 结构内落点层的选择（已定案：规则 C）

以**村庄房屋**为例（地面 y=64、屋顶 y=67–68），三种规则的差异：

| 规则 | 落点 | 玩家体验 |
|---|---|---|
| A. 自上而下 | 屋顶上方或屋顶内侧 | 一眼可见、可直达；但"不在结构里" |
| B. 自下而上 | 屋内地面 | 真在内部；但要找门／挖进去，可能被墙挡视线 |
| **C. 自上而下 + 屋顶判据** ✅ | **屋内上层空间**（要求上方 N 格内有实心顶） | 兼顾"在屋内"与"有遮蔽" |

**定案：规则 C，N = 8。**

- 屋顶判据只在同一层内作为**第 1 遍偏好**，不是硬约束；第 1 遍全败则走第 2 遍，因此露天结构仍能放置（详见 §6.6.3）。
- N 取 8：能覆盖村庄房屋、雪屋、废弃传送门等常见层高，同时不会把塔楼／中庭的上层错认为当前房间的顶。
- 对洞穴型结构（矿井、要塞、试炼密室）三者差异本就不大，因为"最高"本来就在内部。

## 7. 命名规则（D3 + D4）

### 7.1 新规则

**翻译键前缀从 `teleportwaypoint.waypoint.` 缩短为 `tpwp.`，并去掉 `waypoint` 这一段。**
该前缀**仅用于传送锚点的显示名**，其余翻译键（配置／GUI／聊天／数据包／物品组）保持 `teleportwaypoint.*` 不变。

`waypoint_id` 仍由结构注册 ID 派生：`<namespace>.<path>`。**`waypoint_id` 本身不含前缀**，前缀只在客户端拼接时加上。

| 项 | 旧规则 | 新规则 |
|---|---|---|
| `waypoint_id`（存储／网络传输） | `end_city` | `minecraft.end_city` |
| 翻译键 | `teleportwaypoint.waypoint.end_city` | **`tpwp.minecraft.end_city`** |
| 模组结构示例（id / 键） | `swamp_hut` / `teleportwaypoint.waypoint.swamp_hut` | `betterwitchhuts.witch_hut` / **`tpwp.betterwitchhuts.witch_hut`** |
| 空名回退键 | `teleportwaypoint.waypoint.empty` | **`tpwp.empty`** |
| 映射表 | 需要 | **不需要** |

**关于映射表：** 权威 ID 清单确认后，原先设想的三条映射里只有 `minecraft:mansion → woodland_mansion` 是真实的（`bastion_remnant` 与 `fortress` 的注册表 ID 本就和作者既有 id 一致）。且该映射**已不需要** —— 旧键保留为别名（§7.3）后，新旧命名各自解析到各自的键。

### 7.2 对现有实现的连带影响

**需要改动的客户端拼接点共 4 处**（前缀字符串硬编码在这四处）：

| 文件 | 行 | 改动 |
|---|---|---|
| `client/ClientWaypointInfo.java` | `:25` | `"teleportwaypoint.waypoint." + name` → `"tpwp." + name` |
| `network/ActivatedWaypointInfo.java` | `:23` | 同上 |
| `block/entity/WaypointBlockEntity.java` | `:114` | 同上；空名回退键改为 `"tpwp.empty"` |
| `client/xaero/XaeroMinimapIntegration.java` | `:318` | 同上；Xaero 的"键是否存在"检查也改用新前缀（见总体文档 §6.2 的 Xaero 特例） |

**建议把这四处统一到一个 `Naming` 工具类**（如 `Naming.key(id)`），避免前缀散落四处、将来再改又漏掉一处。

其余影响：

| 项 | 影响 |
|---|---|
| `WaypointBlockEntity.isValidId` | 当前模式 `[a-z0-9_]+`（`:36,117-119`），**必须放宽为允许 `.`** |
| 既有 14 个锚点翻译键 | 改写为 `tpwp.*`（村庄键可不加，见 §5.5） |
| 人工化回退函数 `humanize` | 需同时把 `.` 与 `_` 视为分隔符：`minecraft.end_city` → `Minecraft End City` |
| 线格式（`WaypointSyncInfo` / `ActivatedWaypointInfo` 的 `name` 字段） | **不变** —— 传输的仍是裸 `waypoint_id`，前缀只在客户端加。**无协议改动** |
| Xaero 世界地图／小地图 | 逻辑不变，只是前缀字符串变了 |

### 7.2.1 键长度与「防止被截断」的关系（一处需要说清的边界）

缩短键前缀**只影响翻译键**，而**真正有长度上限的是 `waypoint_id`，不是翻译键**：

| 项 | 上限 | 说明 |
|---|---|---|
| `waypoint_id` | **64 字符**（`WaypointBlockEntity.MAX_TEXT_LENGTH`，`:30`） | 唯一的硬约束。它同时进入方块实体 NBT、`WaypointRecord`、SavedData 与三个网络 payload |
| 翻译键 | 无显式上限 | MC 的 `TranslatableContents` 不校验长度；拉长只会略微增加语言文件体积与查找成本 |

**实测数据：**

| id | `waypoint_id` 长度 | `tpwp.` 键长度 |
|---|---|---|
| `minecraft.ancient_city` | 22 | 27 |
| `betteroceanmonuments.ocean_monument` | **35** | 40 |
| `towns_and_towers.village_plains` | 31 | 36 |

最长的现实样例是 35 字符，距 64 有近一倍余量。**因此"防止被截断"的实际保护点是 `waypoint_id` 派生时的 64 字符截断，而不是键前缀。** 缩短键的真正收益是：键更短更易读、语言文件更小、以及人工化回退名更短。

> 结论：`tpwp.` 这个命名规范值得做，但**不能把它当作防截断的手段**；防截断必须落在 `waypoint_id` 的派生逻辑里。

### 7.3 旧存档：保留裸值键作别名（D4）

**结论：** 新键（`tpwp.<id>`）为主，同时**保留旧键作为别名**。旧键有两种，都必须保留：

| 旧键形态 | 例子 | 为什么必须保留 |
|---|---|---|
| `teleportwaypoint.waypoint.<裸值id>` | `teleportwaypoint.waypoint.end_city` | 旧存档的 `waypoint_id` 是裸值（`end_city`），而**客户端旧版本代码会用原长前缀拼接**；同时旧的 NBT 数据包仍可能被启用 |
| `teleportwaypoint.waypoint.empty` | 同上 | 空名回退 |

- 旧存档中已存在的锚点，其 `waypoint_id` 仍是 `end_city` 等裸值 ⇒ 继续解析到保留的旧键，**名字不变**。
- **不改动玩家存档**（不写迁移逻辑），**不写映射表**。
- 新生成的结构使用新键。
- 两套键在同一份语言文件中共存；后续版本可在确认无旧存档依赖后删除旧键。

**保留的旧键清单**（来自现数据包与语言文件的交集）：
`ancient_city`、`bastion_remnant`、`desert_pyramid`、`end_city`、`igloo`、`jungle_temple`、`nether_fortress`、`ocean_monument`、`stronghold`、`swamp_hut`、`trial_chambers`、`woodland_mansion`、`village`、`empty`。

---

## 8. 与旧数据包的关系（D5）

**结论：保留旧数据包，但把两个 `defaultEnable*StructureWaypoints` 的默认值改为 `false`。**

实现时需注意：

- 本方案在旧存档完全不工作（既有区块都不是"刚生成"），所以双份锚点只可能出现在**升级后新生成的地形**里。
- 旧数据包已在既有存档中被启用的情况下，改默认值**不会自动把它关掉**；玩家需手动关闭，或接受"旧包 + 新区块里新机制"的并存。README 需给出这一步。
- 因此**不需要**「排除名单」，配置面不增加。

---

## 9. 性能开销分析

### 9.1 成本模型

触发频次取决于「新区块生成速率」，而不是「区块加载速率」——这是本方案在性能上最关键的性质。**磁盘加载的区块全在第 4 道门出局。**

| 阶段 | 工作内容 | 复杂度 |
|---|---|---|
| 门 1 | `event.getLevel() instanceof ServerLevel` | O(1) |
| 门 2 | 功能开关 + 配置读取 | O(1)（`ModConfigSpec` 的 `get()` 是字段读，不需额外缓存） |
| 门 3 | `isNewChunk()` | O(1) |
| 门 4 | `getAllStarts().isEmpty() && getAllReferences().isEmpty()` | O(1)，两个 `Map.isEmpty()` |
| 5 | 遍历 starts（通常 0–2 个）取结构 ID | O(#starts) |
| 6 | 黑白名单筛选 | O(1)（`HashSet`）/ 标签由原版 `HolderSet` 处理 |
| 7 | 收集与本区块相交的 piece | O(#pieces)，实际通常 1–5 个 |
| 8 | 逐候选读方块状态 | 每候选约 4–10 次 `getBlockState`（含 `roofed` 最多 8 次向上探测）；通常第 1–2 个候选就命中或全败 |
| 9 | 放置 | O(1) 方块写 + BE 构造 + **光照更新** |

### 9.2 分档估算

| 场景 | 估计 | 依据 |
|---|---|---|
| **无结构的区块**（绝大多数） | **约 5 次布尔/映射查询，微秒级** | 门 1–4 全部 O(1) |
| 有结构但被名单排除 | 上者 + 一次集合查询 | — |
| 有结构且需放置 | 上者 + 数十次 `getBlockState` + 1 次放置 | 候选数通常 < 10 |
| 新区块生成速率（正常探索） | 数十/秒 | 需要实测 |
| 新区块生成速率（高速飞行） | 1–2 百/秒 | 需要实测 |

### 9.3 三个真实的成本点

1. **光照更新（最值得实测的一项）**：锚点发光等级 14（`ModBlocks.java:22`），放置会触发光照传播，可能扩散到半径 14。原版在生成期放置发光方块是常规操作，但它确实是本方案单次工作里最重的一块。**建议实测。**
2. **`piece.getBoundingBox()`**：`StructureStart` 的包围盒被 `cachedBoundingBox` 记忆化，但 `StructurePiece` 的没有。建议在扫描器内按需取一次并缓存到局部变量，不要重复调用。
3. **`chunk.getBlockEntity(pos)` 会触发 pending BE 的惰性初始化**（反序列化 NBT）。因此 §6.2/§6.6.2 要求先判 `state.hasBlockEntity()`。否则每次候选判定都可能反序列化一次 NBT —— 这是本方案最容易踩的性能陷阱。

### 9.4 结论

- **结构性成本是安全的**：不存在按区块体积或按世界大小增长的工作。
- **候选层已从四层降为两层**（取消"干燥/含水"之分），搜索轮次减半 —— 这是本方案最实在的一项性能优化。
- **平均成本极低**，因为磁盘加载与无结构区块都在常数时间内出局。
- **峰值成本集中于新地形生成时**，与地形生成本身的开销同阶。
- **方向：可接受，但建议实测后再定稿。** 建议在实现后用一个真实世界 + profiler（spark）测量「新区块生成速率」与「单次放置耗时」，再决定是否需要进一步收紧候选数上限。

---

## 10. 并发与崩溃风险分析

### 10.1 并发：本方案不引入跨线程共享

| 环节 | 所在线程 |
|---|---|
| 区块加载信号 | **服务端主线程** |
| 读区块结构数据、读方块状态 | 服务端主线程 |
| 放置方块（`setBlock`） | 服务端主线程 |
| 构造并注册 BlockEntity | 服务端主线程 |
| `onLoad` → 登记 → 网络广播 | 服务端主线程（下一 tick） |

**这条链上没有一处跨线程共享。** 因此**不存在数据竞争**。

**实现纪律（唯一要求）：** 处理器、扫描器、放置器**全部无状态**——不设静态可变字段、不缓存"上次位置"、不共享可变集合。所有中间数据用局部变量或方法参数传递。

### 10.2 唯一的并发类风险是「重入」，而不是「数据竞争」

**机制**：如果处理器内部触发了区块加载，主线程会泵送任务队列、完成其他区块的 FULL 阶段，从而**再次触发本监听器**，形成递归。

**本方案已从设计上消除**：§3 的禁止清单明确不允许任何会强制加载区块的调用。

**建议额外加一道显式重入守卫作为安全网**：一个布尔标志，若检测到重入则跳过并记录 WARN。这道守卫的作用**不是修复已存在的行为，而是在未来有人误引入强制加载时立刻暴露出来**——因为一旦重入发生，被跳过的那次不会再有第二次机会。

### 10.3 崩溃向量审计

| # | 向量 | 评估 | 对策 |
|---|---|---|---|
| 1 | **处理器抛异常**（最真实的崩溃方式） | 异常会从区块加载的 future 传出 ⇒ future 异常完成 ⇒ `ServerChunkCache.getChunk` 的 `join()` 抛 `IllegalStateException` ⇒ 崩溃 | **整个处理体包 `try/catch (Throwable)`**，记 WARN，绝不让异常逃逸 |
| 2 | 自动放置的方块在区块升级时崩溃 | 该方块与其 BlockEntity 都已在本模组注册，不存在"未知方块/未知 BE" | 无需处理 |
| 3 | 在世界生成工作线程读本区块并崩溃 | 事件时区块已达 FULL 阶段，世界生成已结束；相邻区块读本区块是安全的并发读 | 无需处理 |
| 4 | 与本区块的并发写 | 服务端主线程独占写方块 | 无需处理 |
| 5 | 触发错误事件导致区块损坏 | 只走标准方块放置路径，不碰结构数据本身 | 无需处理 |
| 6 | 在世界生成线程调 `setChanged()` 抛异常 | **本方案不在世界生成线程上工作**，该风险属于另一条路线（图纸注入） | 无需处理 |
| 7 | 在事件内强制加载区块导致卡死 | 禁止清单 + 重入守卫 | 已覆盖 |
| 8 | 名单 JSON 配置损坏 | 宽容解析：坏条目跳过并 WARN | §5.1 |
| 9 | 扫描误漏早退导致每区块全扫 | 强制早退顺序；无结构记录的区块只花两次 `Map.isEmpty()` | §9.1 |

### 10.4 结论

**崩溃风险不来自并发，全部集中在两处，且都有明确对策：**

1. **异常逃逸** ⇒ `try/catch (Throwable)` 是**强制项**，不是可选优化。
2. **误用强制加载** ⇒ 禁止清单 + 重入守卫。

在遵守这两条的前提下，**本方案不会增加游戏的崩溃面**。反过来说，这两条也正是实现评审时的必查项。

---

## 11. 性能预算

| 项 | 上限 |
|---|---|
| 每区块处理的结构数 | 待 planning 确定（建议 2） |
| 每区块放置数 | 1 |
| L2 向下扫描 | 64 格 |
| 逐次放置日志 | 默认关闭 |

---

## 12. 讨论记录

| 问题 | 结论 | 日期 |
|---|---|---|
| Q1 落点策略 | **两层候选**：结构内优先，失败回退区块内 | 2026-09-14 |
| Q2 去重语义 | **只在首次生成时补锚**；不补旧区块；锚点可拆且不重生；零存储 | 2026-09-14 |
| Q3 无法放置的结构 | **允许水中放置**；**岩浆显式排除** | 2026-09-14 |
| Q4 存量存档 | **不补放，取消开关**（由 Q2 直接决定） | 2026-09-14 |
| Q5 性能预算 | L2 扫描上限 **64 格** | 2026-09-14 |
| Q6 旧数据包关系 | **保留数据包，两个默认值改为 `false`** | 2026-09-14 |
| Q7 命名规则 | **`<namespace>.<path>`**；旧裸值键**保留为别名** | 2026-09-14 |
| Q8 结构筛选 | **黑白名单用本模组命名空间的数据包标签**；默认白名单 12 条；黑名单 22 条（含村庄） | 2026-09-14 |
| Q9 村庄的归属 | **从白名单移除、加入黑名单**；语言文件里的村庄键是测试遗留，不需适配 | 2026-09-14 |
| Q10 标签的 `replace` | **必须为 `false`**，否则整合包与其他模组无法追加 | 2026-09-14 |
| Q11 空标签与缺失标签 | **空标签合法、不记日志**；**缺失记 WARN 一次** | 2026-09-14 |
| Q12 调试模式 | **新增 `debugMode` 配置**；所有 debug 日志走该开关，**文本一律英文** | 2026-09-14 |
| Q13 结构内落点层 | **规则 C**：自上而下 + 屋顶判据（偏好，非硬约束），N = 8 | 2026-09-14 |
| Q14 piece 搜索范围 | **按体积降序逐个 piece 试，成功即停**（不取并集一次算完） | 2026-09-14 |
| Q15 锚点列的约束力 | **仅作排序权重**，不作硬约束 | 2026-09-14 |
| Q16 候选层数 | **四层降为两层**：锚点可含水，取消"干燥/含水"之分；岩浆仍排除 | 2026-09-14 |

---

## 13. 待确认

| # | 事项 | 说明 |
|---|---|---|
| P1 | 每区块结构数上限 | 建议 2 |
| P2 | 总探测预算初值 | 建议 2000 次 `isValidPlacement` 调用 |
| P3 | L2 兜底层是否需要 `pieceIndex` 之外的排序键 | 兜底层无 piece，排序键需另行定义 |

（原 P1「`"replace"` 选 true 还是 false」、P2「标签缺失的日志级别」已由 2026-09-14 的修改解决；原 P1「落点算法细节」已由 §6.6/§6.7 定案解决。）

---

## 14. 后续

设计已定案。下一步转入 `planning` 技能生成实施计划，需覆盖：

- `WaypointBlockEntity.isValidId` 放宽以允许 `.`；
- `humanize` 支持 `.` 分隔；
- 结构注册 ID → `waypoint_id` 的派生函数；
- 语言文件改写为新键 + 保留 14 个旧键作别名；
- 黑白名单配置项、JSON 解析与 `#tag` 匹配；
- `ChunkEvent.Load` 监听器、强制早退顺序、重入守卫、`try/catch (Throwable)`；
- 两层候选选点（含 `hasBlockEntity()` 前置判断、`piece` 包围盒局部缓存）；
- 放置器的 `WATERLOGGED` 显式设置与岩浆排除；
- 两个 `defaultEnable*StructureWaypoints` 默认值改为 `false`；
- README 更新（新机制说明、迁移步骤、已知限制、两套名单的语义）；
- GameTest 覆盖（纯几何选点、命名派生、`isNewChunk` 守卫、名单筛选）；
- 实测项：新区块生成速率、单次放置耗时（含光照）、profiler 数据后再定候选数上限。
