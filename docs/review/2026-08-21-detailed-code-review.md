# Teleport Waypoint 项目详细代码审查报告

**审查日期：** 2026-08-21

**审查分支：** `ds_flash`

**基准提交：** `8a77d95`（所有结构适配完成，传送锚点可发光，配置文件优化）

**规格基线：** `docs/design/teleport-waypoint-final-design.md`

**处理方式：** 仅记录审查结果；本次不修改业务代码、不修复问题

## 1. 审查结论

本次共确认 8 项问题或风险：

| 严重级别 | 数量 | 概要 |
|---|---:|---|
| 高 | 1 | 激活锚点元数据快照未分页，可能形成超大网络包并导致登录失败 |
| 中 | 3 | 传送落点不安全、局部损坏导致整名玩家数据丢失、已打开列表不响应增量同步 |
| 低 | 4 | 菜单有效性不足、空玩家记录残留、README 行为矛盾、Xaero 弃用 API 风险 |

整体架构方向合理。服务端是传送、激活、改名和删除操作的最终裁决者；客户端状态按“当前维度完整数据 + 跨维度激活元数据”拆分；口袋锚点坐标同步遵守当前隐私设计；Xaero 联动也通过反射入口和可选依赖与核心功能隔离。

当前最需要优先处理的是激活元数据快照的分页问题。维度完整数据已经按 500 条分页，但登录时的激活元数据仍以单包发送，规模增大后会成为确定的协议和内存风险。

## 2. 审查范围

本次审查覆盖以下内容：

- 模组入口、事件注册与客户端初始化；
- 锚点和口袋锚点方块、方块实体及菜单；
- `WaypointManager`、全局锚点索引、玩家激活数据和传送实现；
- 全部自定义 C2S / S2C Payload、处理器和分页同步流程；
- 客户端锚点状态、传送列表、改名和删除确认界面；
- Xaero Minimap / World Map 可选集成；
- NeoForge 配置、构建脚本、模组元数据和 GitHub Actions；
- JSON、语言文件、模型、战利品表、配方和结构 NBT 资源；
- 现有设计文档、README 和历史审查记录。

本次没有执行真实 Minecraft 客户端或专用服务端的多人游戏测试，也没有使用依赖 CVE 扫描器。因此，实际渲染效果、跨模组运行时兼容性和第三方依赖漏洞不在已验证结论范围内。

## 3. 详细问题

### H-1 激活锚点元数据快照未分页，可能超过网络帧容量

**位置：**

- `src/main/java/com/zonlong/teleportwaypoint/network/SyncActivatedWaypointsPayload.java:15`
- `src/main/java/com/zonlong/teleportwaypoint/network/SyncActivatedWaypointsPayload.java:22`
- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java:176`
- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java:186`

`SyncActivatedWaypointsPayload` 允许一个集合包含最多 `100_000` 条记录，`WaypointManager.syncTo()` 在玩家登录和改名后把全部激活元数据一次性发送给客户端。该上限只是集合解码条目数限制，不是网络包字节数限制。

每条 `ActivatedWaypointInfo` 至少包含 16 字节 UUID、1 字节布尔值、字符串长度和名称内容。名称允许最长 64 个 Java 字符；按 UTF-8 编码时，单条记录的最坏体积远高于固定字段。`100_000` 条记录可能产生数十 MB 的编码数据，足以超过 Minecraft 使用的三字节帧长度范围，并造成编码缓冲区和客户端解码内存压力。

**影响：**

- 激活锚点数量较多的玩家可能无法登录；
- 改名触发 `syncTo()` 时也可能导致断线；
- 服务端构造完整列表和编码单包时会产生瞬时内存压力；
- 当前 `MAX_ACTIVATED_SYNC` 容易给维护者造成“包大小已受保护”的错误印象。

**建议：** 为激活元数据增加与维度快照一致的 `page` / `done` 分页协议，并以保守的编码字节预算控制每页，而不只按条目数控制。客户端应在最后一页到达前保留旧快照或构建临时快照，避免显示半成品状态。

### M-1 传送落点没有验证玩家能否安全站立

**位置：** `src/main/java/com/zonlong/teleportwaypoint/core/WaypointTeleporter.java:98`

`findLanding()` 依次检查锚点四周的位置，`hasSpace()` 仅判断目标格和上方方块是否 `isSuffocating`。该条件不能等价于“玩家碰撞箱无碰撞且脚下安全”。所有候选位置失败时，代码还会直接回退到锚点方块所在位置，而不会再次验证。

当前逻辑没有检查：

- 脚下是否有可站立的坚固表面；
- 玩家包围盒是否与非窒息碰撞体相交；
- 水、岩浆和其他危险流体或方块；
- 虚空、世界高度和世界边界；
- 回退位置是否可用。

**影响：** 玩家可能被传送到悬空位置、危险流体、碰撞体内部或锚点自身位置，造成窒息、跌落、燃烧甚至死亡。README 所称“可站立位置”与实际校验强度不一致。

**建议：** 使用玩家包围盒执行 `noCollision` 一类的碰撞检查，验证脚下支撑面、危险流体、构建高度和世界边界；在有限半径内搜索安全落点，找不到时取消传送并向玩家返回明确提示。

该问题已作为历史记录 `L-4` 标记为“暂不修复”，本报告仍将其列为当前存在的玩法安全风险。

### M-2 一个损坏的 UUID 会丢弃该玩家全部有效激活记录

**位置：** `src/main/java/com/zonlong/teleportwaypoint/core/PlayerWaypointData.java:84`

`PlayerWaypointData.read()` 的 `try/catch` 覆盖整名玩家的 UUID 列表。若列表中某一个 `TAG_INT_ARRAY` 不是有效 UUID，`NbtUtils.loadUUID()` 抛出异常后，代码会跳过整名玩家，之前已经成功解析的 UUID 也不会写入 `activated`。

**影响：** 存档发生局部损坏时，受影响玩家会失去全部激活记录，而不是只失去损坏条目。这与最终设计中“读取时对损坏数据做容错跳过”的目标不完全一致。

**建议：** 玩家键解析和列表条目解析分层捕获异常。玩家 UUID 无效时跳过玩家；单个锚点 UUID 无效时只跳过该条目，并保留同一玩家的其他有效记录。

### M-3 已打开的传送列表不会响应服务端增量更新

**位置：**

- `src/main/java/com/zonlong/teleportwaypoint/client/gui/WaypointListScreen.java:113`
- `src/main/java/com/zonlong/teleportwaypoint/client/ClientWaypointState.java:37`

`ClientWaypointState` 在新增、更新、删除和激活状态变化时都会递增 `revision`。Xaero 小地图会观察该 revision，但 `WaypointListScreen` 没有观察它。列表只在界面初始化、搜索、排序和本地删除时调用 `updateList()`。

**影响：** 界面保持打开期间，其他玩家改名或破坏锚点后，客户端底层状态虽然已经更新，列表仍会显示旧名称或已经不存在的条目，直到玩家重新打开界面。点击幽灵条目最终会被服务端拒绝，但用户体验不一致。

**建议：** 界面初始化时记录 `ClientWaypointState.getRevision()`，在界面 tick 中发现 revision 变化后重建列表，同时保留搜索内容、排序方式和合理的滚动位置。

### L-1 菜单有效性没有距离和维度约束

**位置：** `src/main/java/com/zonlong/teleportwaypoint/menu/AbstractWaypointMenu.java:29`

`stillValid()` 只检查玩家当前世界的相同坐标处是否存在任意 `WaypointBlockEntity`，没有检查距离，也没有绑定打开菜单时的维度或具体锚点 UID。

**影响：** 玩家被命令、其他模组或游戏机制移动后，菜单可能长期保持打开。传送请求另有服务端 8 格距离校验，因此这不是传送权限绕过，但会形成失效界面，并允许满足改名权限的玩家在非预期距离提交改名操作。

**建议：** 使用标准容器距离校验，并验证维度、方块位置和锚点 UID 与打开菜单时一致。

### L-2 取消最后一个激活记录后会保留空玩家条目

**位置：** `src/main/java/com/zonlong/teleportwaypoint/core/PlayerWaypointData.java:45`

`deactivate()` 从集合中移除最后一个 UUID 后不会从 `activated` Map 删除玩家键。`save()` 随后仍会把该玩家写成空列表。只有全局 `deactivateAll()` 会清理空集合。

**影响：** 长期运行且玩家数量较多的服务器会积累没有实际数据的玩家条目，增加少量常驻内存和存档噪声。

**建议：** 删除成功后若集合为空，同时移除玩家键。

### L-3 README 对首次激活行为的描述互相矛盾

**位置：** `README.md:31`、`README.md:55`

第 31 行和最终设计均说明“未解锁锚点仅激活，不弹 GUI”，实际代码也执行该行为；第 55 行却写成“自动激活并打开传送列表”。

**影响：** 新玩家和测试人员会按照错误预期判断首次右键行为，可能把正确实现误认为缺陷。

**建议：** 将快速上手部分统一为“首次右键只激活，再次右键打开传送列表”。

### L-4 Xaero 小地图集成使用已弃用 API

**位置：**

- `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`
- `src/main/templates/META-INF/neoforge.mods.toml:96`
- `src/main/templates/META-INF/neoforge.mods.toml:104`

当前编译器明确报告 `XaeroMinimapIntegration.java` 使用或覆盖了已弃用 API。模组元数据同时把 Xaero Minimap 和 World Map 声明为只设最低版本、不设上限的可选依赖。

**影响：** 当前已验证版本可以编译，但未来 Xaero 删除弃用接口后，集成可能在类加载或运行时失败，而元数据仍会把该版本视为兼容。

**建议：** 确认具体弃用成员并迁移到替代 API；发布前至少对声明支持的最低版本和最新版本执行客户端启动及地图交互测试。若无法保证向后兼容，应收窄版本范围或在反射边界补充 `LinkageError` 降级保护。

## 4. 测试与持续集成缺口

仓库存在 `.github/workflows/build.yml`，push 和 pull request 会在 JDK 21 上执行 `./gradlew build`。这可以验证代码能够编译和打包，但当前不存在 `src/test`，本地构建中的 `compileTestJava` 和 `test` 均为 `NO-SOURCE`。

以下核心行为没有自动化回归保护：

- SavedData 正常读取、局部损坏恢复和空记录清理；
- 激活快照及维度快照的分页、顺序和规模边界；
- 传送、改名和删除 Payload 的服务端权限校验；
- 安全落点选择及找不到落点时的失败行为；
- 多人在线时的新增、改名、破坏和取消激活同步；
- Xaero 单独安装 Minimap、单独安装 World Map、同时安装和未安装时的类加载；
- 登录、跨维度和登出重连后的客户端状态清理。

建议优先为纯数据和协议逻辑提取可测试边界，再增加 NeoForge GameTest 或专用服务端集成测试。网络规模问题应至少包含接近页面上限、超长名称和大量随机 UUID 的编码测试。

## 5. 安全审查结果

本次没有发现 Critical 级权限绕过或未激活口袋锚点坐标泄露。已确认的防护包括：

- 所有 C2S 处理器使用连接上下文中的 `ServerPlayer`，不接受客户端提供玩家身份；
- GUI 传送重新验证源、目标激活状态、源位置距离、方块实体 UID 和冷却；
- 世界地图传送虽然不要求靠近源锚点，但仍重新验证目标激活状态、目标记录和方块实体；
- 改名重新验证创造模式或口袋锚点所有者权限，并验证名称或 ID；
- 删除请求只能修改调用者自己的激活集合；
- 未激活口袋锚点的完整位置不会通过自定义同步发送给客户端。

仍可进一步强化的低优先级事项包括：限制改名 Payload 的字符串 codec 长度、过滤控制字符、为改名请求增加距离或菜单上下文验证，以及对重复无效请求增加适度限频。

密码、SQL 注入、SSRF、CORS 和加密存储等常规 Web 风险不适用于本 Minecraft 模组。本次没有执行第三方依赖 CVE 扫描，因此不对依赖漏洞状态作结论。

## 6. 经复核未列为缺陷的事项

### 方块实体损坏 NBT 不会直接导致整个区块加载崩溃

`WaypointBlockEntity.loadAdditional()` 对格式错误的 UUID 没有局部捕获，但 Minecraft `BlockEntity.loadStatic()` 外层会捕获加载异常、记录错误并丢弃该方块实体。因此其后果是损坏实体无法加载，而不是异常直接穿透并使整个区块加载崩溃。本报告没有将其列为独立高严重级别问题。

### Xaero Minimap 登出 reset 不清理旧 manager Map 不构成已确认幽灵标记

Xaero 每次连接会创建新的 `MinimapSession` / `MinimapWorldManager`，登出时旧 session 会关闭。当前 `reset()` 清理本模组映射状态并允许新 session 重新注册，因此没有足够证据把旧 manager 中的对象认定为跨服务器泄漏。

### 世界地图 Tooltip 位置属于后续明确设计决定

历史提交已经明确把世界地图 hover 改回 Xaero Tooltip 并显示在鼠标上方，最终设计也没有要求必须使用图标上方的自绘提示。本报告不把该实现列为回归。

### 口袋锚点掉落不保留 owner、name 和 uid 属于已确认设计行为

最终设计和历史问题清单均明确说明口袋锚点破坏后掉落普通物品，不保留锚点身份数据。本次不重复列为数据丢失缺陷。

## 7. 做得较好的部分

- 维度完整数据已经采用分页快照，并在客户端完成前避免渲染部分状态；
- 运行时新增、更新和删除使用增量 Payload，避免频繁广播完整维度快照；
- 普通锚点和口袋锚点采用不同的同步可见性策略，符合隐私目标；
- 锚点 UID 冲突会在服务端注册时重新生成，适配结构复制和粘贴场景；
- `WaypointRegistryData` 对损坏记录逐条捕获，比玩家数据当前的捕获粒度更合理；
- 传送冷却由 GUI 与地图传送共用，避免两条入口行为分叉；
- Xaero World Map 与 Minimap 实现分离，核心模组通过反射入口避免硬加载可选类；
- 中英文语言文件键集合一致，资源文件组织清晰；
- GitHub Actions 已覆盖基础构建，Gradle Wrapper、JDK 和 NeoForge 版本均有明确配置。

## 8. 验证记录

本次审查及报告写入前完成了以下验证：

- 完整离线 Gradle 构建：成功，`BUILD SUCCESSFUL`；
- `compileJava`：成功，但报告 Xaero 小地图弃用 API 警告；
- `compileTestJava` / `test`：`NO-SOURCE`；
- 所有 JSON 和 MCMeta 文件：可解析；
- 中英文语言文件：键集合一致；
- 27 个结构 NBT：均可 gzip 解压，且包含 `teleportwaypoint:waypoint` 和 `waypoint_id`；
- `git diff --check`：通过；
- 写入报告前业务代码工作区干净。

首次通过 Wrapper 执行离线构建时，Wrapper 默认用户目录缺少 Gradle 发行包并尝试联网下载，因沙箱网络限制失败；随后使用项目 `build/gradle-home` 中已有的 Gradle 9.2.1 发行包和离线缓存完成构建。该首次失败发生在项目编译开始之前，不是代码编译失败。

## 9. 建议处理顺序

1. 为激活元数据快照增加分页和编码规模测试；
2. 修正传送安全落点算法，并明确找不到落点时的失败行为；
3. 将玩家 SavedData 容错粒度调整为单条 UUID；
4. 让打开的传送列表观察客户端 revision；
5. 增加核心数据、协议和权限自动化测试；
6. 处理菜单有效性、空记录和 README 矛盾；
7. 跟踪 Xaero 弃用 API 并建立版本兼容测试矩阵。

## 10. 最终说明

本报告只记录审查结果和修复建议，没有实施任何修复，也没有修改 Java、资源、配置或构建代码。后续若处理这些问题，应为每项修复单独补充验证，尤其避免在修改网络协议时破坏旧客户端兼容性或口袋锚点隐私边界。
