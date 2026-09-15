# Xaero 联动后续代码详细审查报告

**日期：** 2026-08-18

**审查范围：** `f0a4c59..HEAD`

**基准提交：** `f0a4c59`「小肥鱼做的Xaero兼容，初版」

**当前分支：** `ds_flash`

**处理方式：** 仅记录审查结果，本次不修改业务代码、不修复问题

## 审查范围

本次覆盖基准提交之后的 8 个提交：

- `6a954b7` 小肥鱼第二版，一些BUG修复
- `42ab54f` 小肥鱼第四版，优化图标
- `bb182ad` 小肥鱼第三版，功能基本完成！
- `951786b` 小肥鱼第五版，配置文件新功能
- `0d00bdb` 小肥鱼第六版，优化配置文件
- `b86e5c3` 小肥鱼第七版，整体审查和优化
- `37007e6` 小肥鱼第七版，优化完毕
- `d6fd40f` 优化传送音效

变更共涉及 28 个文件，主要审查内容包括：

- Xaero World Map / Minimap 可选依赖和类加载边界；
- 世界地图元素、右键菜单和地图传送；
- 小地图 custom waypoint 注入和 ID 管理；
- 全量锚点同步、激活状态同步及删除/改名传播；
- NeoForge / Xaero 配置优先级和显示范围过滤；
- 构建脚本、README、设计文档和验证结果。

## 总结

整体架构方向合理：Xaero 代码按 World Map 与 Minimap 拆分，服务端仍是传送权限的最终裁决者，地图请求只携带目标 UUID，构建脚本也已经移除初版中的本机绝对路径依赖。

当前最严重的问题是锚点被破坏后全量状态不会广播，导致未激活该锚点的在线客户端持续显示幽灵标记。除此之外，配置源遮蔽、无界同步包、地图传送无频率限制、小地图 ID 冲突和默认范围偏差都需要在后续修复或明确接受风险。

## Critical 问题

### C1. 破坏锚点后不会广播删除，客户端持续显示幽灵标记

**位置：**

- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java:66`
- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java:67`
- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java:108`
- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java:110`

`unregister()` 先调用 `registry.removeIfAt(...)` 删除注册表记录，再调用 `removeWaypoint(...)`。`removeWaypoint()` 内部再次调用 `registry.remove(uid)`，此时删除结果必然为 `false`，因此不会执行 `broadcastAll(server)`。

**影响：**

- 未激活该锚点的在线玩家不会收到任何全量状态更新；
- Xaero 世界地图和小地图继续显示已经破坏的锚点；
- 客户端的 `ClientWaypointState` 会保留不存在的记录，直到重新收到完整同步或重新连接。

这是 `37007e6` 中“仅在实际删除时广播”改动与现有 `unregister()` 双重删除逻辑组合后产生的确定性回归。

**建议：** 让注册表只删除一次，并确保一次成功注销操作同时触发全量同步和受影响玩家的激活状态同步。本次审查不实施该建议。

## Important 问题

### I1. Xaero 配置注册成功后会遮蔽 NeoForge 总开关

**位置：** `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroWorldMapIntegration.java:35`

`showWaypoints()` 在 Xaero World Map 配置选项注册成功后优先读取 Xaero 自身的配置；只有注册失败时才回退到 `Config.SHOW_WAYPOINTS`。小地图也通过该结果进行过滤。

**影响：** 安装 World Map 且配置注册成功时，用户在本模组 NeoForge 配置界面中关闭 `showWaypoints`，可能不会关闭地图标记。项目 README 又将配置描述为本模组“Xaero 的地图联动”分组，实际配置来源因此不透明。

**建议：** 建立唯一配置源，或让 Xaero 设置项代理并同步本模组配置值。本次不调整配置实现。

### I2. 全量同步包没有条目上限或分页

**位置：**

- `src/main/java/com/zonlong/teleportwaypoint/network/SyncAllWaypointsPayload.java:22`
- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java:167`
- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java:183`

`SyncAllWaypointsPayload` 使用无界集合 codec，`syncAllTo()` 每次将整个注册表编码为单个包，并在锚点增删改时向所有在线玩家广播。

**影响：** 锚点数量较大时，登录同步或一次改名/放置操作可能造成较大的网络包、瞬时内存压力，甚至触发客户端断线。当前普通锚点和口袋锚点名称虽有长度限制，但集合数量没有限制。

**建议：** 增加解码和条目数量上限，或改为登录分页同步、运行时增量 add/update/remove 包。本次不改变网络协议。

### I3. 地图传送没有服务端频率限制

**位置：**

- `src/main/java/com/zonlong/teleportwaypoint/network/ModNetwork.java:71`
- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointTeleporter.java:66`
- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointTeleporter.java:76`
- `src/main/java/com/zonlong/teleportwaypoint/core/WaypointTeleporter.java:87`

每个 `MapTeleportRequestPayload` 都会进入 `teleportTo()`。请求会访问目标区块，执行传送，并发送 128 个传送粒子；服务端没有按玩家设置冷却、令牌桶或其他限频措施。

**影响：** 修改客户端后，玩家可以连续发送合法的已激活目标 UUID，反复触发跨维度传送、区块访问、声音和粒子处理，形成可被滥用的服务端资源消耗路径。

**建议：** 在区块访问前加入服务端冷却或令牌桶，并让普通 GUI 传送和地图传送共用限频策略。本次不增加限频逻辑。

### I4. 小地图整数 ID 可能覆盖其他集成的 custom waypoint

**位置：**

- `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java:40`
- `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java:234`
- `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java:269`

本模组从 `1` 开始顺序分配整数 ID，并直接写入 Xaero 的共享 custom waypoint map，没有检查该 ID 是否已被其他集成占用。清理时也会按本地记录直接删除该 ID。

**影响：** 与其他使用同一 custom waypoint map 的集成同时运行时，可能覆盖其他标记；后续清理还可能误删其他集成的标记。

**建议：** 使用保留 ID 区间或哈希加冲突探测，写入前检查冲突，删除前确认对象属于本模组。本次不改 ID 分配策略。

### I5. 显示范围默认值偏离最终设计与 README

**位置：**

- `src/main/java/com/zonlong/teleportwaypoint/Config.java:36`
- `src/main/java/com/zonlong/teleportwaypoint/Config.java:48`
- `docs/plans/2026-08-17-xaero-config-filter-design.md:20`
- `README.md:165`

当前代码将传送锚点和口袋锚点的默认 `range` 都设为 `256`，而最终设计文档和 README 均明确写为 `128`。该偏差混入了只描述“优化传送音效”的 `d6fd40f`。

**影响：** 新生成配置的地图过滤半径会比设计预期扩大一倍；已有配置文件中的显式值不受默认值变化影响，进一步增加了行为不一致的可能。

**建议：** 先确认产品默认值，再统一代码、文档和提交说明。本次不改变默认值。

## Suggestions

### S1. 可选依赖版本范围过宽

**位置：**

- `src/main/templates/META-INF/neoforge.mods.toml:95`
- `src/main/templates/META-INF/neoforge.mods.toml:103`

Xaero 两个可选依赖都声明为 `[1,)`，但实现依赖特定的内部 API，且当前编译已经报告 `XaeroMinimapIntegration.java` 使用或覆盖了已过时 API。

建议声明已验证的兼容版本范围，避免未来加载到 API 不兼容的 Xaero 版本后整套联动失效。本次不调整元数据。

### S2. README 仍包含已过期描述

**位置：**

- `README.md:30` 写着“暂无实际可调选项”；
- `README.md:173` 仍写 Xaero 联动计划作为后续可选集成。

这两处与当前已经实现的配置和 Xaero 联动功能矛盾，容易误导使用者和后续维护者。本次只记录，不修改 README。

## 做得较好的部分

- World Map 与 Minimap 集成已经拆分到独立类，降低了只安装一个 Xaero 模组时的类加载风险；
- 地图传送请求只携带目标 UUID，服务端重新校验激活状态、注册记录、目标维度和方块实体 UID；
- 初版构建脚本中的本机绝对路径已替换为从 Xaero mod jar 提取嵌套 `xaerolib`；
- 小地图的反向 ID 映射和增量更新优化已经减少了原有线性查找及重复对象创建问题；
- 世界地图右键菜单、状态颜色、名称本地化和自定义贴图已按当前设计拆分实现。

## 验证记录

本次审查使用以下只读或构建验证命令：

- `gradlew.bat compileJava --offline --rerun-tasks --console=plain`：成功；
- `gradlew.bat build --offline --rerun-tasks --console=plain`：成功；
- `gradlew.bat prepareClientRun --offline --rerun-tasks --console=plain`：成功；
- `git diff --check f0a4c59..HEAD`：通过；
- 写入本报告前，业务代码工作区无未提交变更；写入后仅新增本审查文档。

构建输出包含 Xaero 小地图弃用 API 警告；测试任务为 `NO-SOURCE`，项目没有自动化测试覆盖 Xaero 单模组/双模组加载、多人删除同步、配置优先级、custom waypoint ID 冲突或网络滥用场景。

## 结论

本次没有修复任何问题，也没有修改业务代码。审查结果为：**Critical 1，Important 5，Suggestion 2**。其中 C1 会直接造成在线客户端的幽灵锚点，应作为后续修复的第一优先级。
