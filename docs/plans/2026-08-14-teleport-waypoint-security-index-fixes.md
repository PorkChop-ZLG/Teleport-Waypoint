# 传送锚点：服务端校验与索引修复计划

**日期：** 2026-08-14  
**状态：** 已批准  
**目标：** 修复服务端传送校验、全局索引生命周期、UID 唯一性、重连同步、索引删除和名称长度限制。

## 方案选择

采用“服务端权威 + 已登记实例优先”方案。

- 传送请求中的 `source` 和 `target` 仅作为引用。服务端必须确认玩家已激活两端、当前站在源锚点附近、源锚点和目标锚点的方块实体 UID 与索引记录一致。
- 方块实体在服务端 `onLoad()` 时生成缺失 UID 并登记索引。若 UID 已在另一个位置登记，则为当前后加载的副本生成新 UID，防止结构复制或 NBT 复制覆盖已有记录。
- 删除索引时同步移除所有玩家的对应激活记录，并向在线受影响玩家重新同步列表。

备选方案是用短期菜单令牌绑定每次传送请求。它可进一步收紧权限，但需要增加会话状态和失效规则；本次以距离、源方块实体和激活状态验证解决已报告的绕过问题，不扩大范围。

## 任务

### 1. 统一实例身份与索引生命周期

**文件：**
- 修改 `src/main/java/com/zonlong/teleportwaypoint/block/entity/WaypointBlockEntity.java`
- 修改 `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java`
- 修改 `src/main/java/com/zonlong/teleportwaypoint/core/WaypointRegistryData.java`

**步骤：**
1. 为方块实体增加服务端加载登记和受控 UID 重生方法。
2. 登记前比较已有记录的维度与坐标；冲突时重生当前实例 UID。
3. 删除时仅移除与当前方块实体位置一致的记录。

**验收：** 同一 UID 的两个不同位置实例不会互相覆盖或互相删除；结构锚点加载后进入索引。

### 2. 服务端传送校验与失效数据清理

**文件：**
- 修改 `src/main/java/com/zonlong/teleportwaypoint/core/WaypointManager.java`
- 修改 `src/main/java/com/zonlong/teleportwaypoint/core/WaypointTeleporter.java`
- 修改 `src/main/java/com/zonlong/teleportwaypoint/core/PlayerWaypointData.java`
- 修改 `src/main/java/com/zonlong/teleportwaypoint/network/ModNetwork.java`

**步骤：**
1. 在服务端验证源/目标均为该玩家已激活的不同 UID。
2. 验证玩家在源锚点交互距离内，源和目标的方块实体 UID 与索引一致。
3. 目标失效、方块删除时，移除所有玩家的失效 UID 并同步在线玩家。

**验收：** 伪造 UID、未解锁目标、远离源锚点的请求均不会传送。

### 3. 重连同步和名称输入限制

**文件：**
- 新建 `src/main/java/com/zonlong/teleportwaypoint/WaypointEvents.java`
- 修改 `src/main/java/com/zonlong/teleportwaypoint/block/entity/WaypointBlockEntity.java`
- 修改 `src/main/java/com/zonlong/teleportwaypoint/network/ModNetwork.java`

**步骤：**
1. 在服务器玩家登录事件中发送当前激活列表。
2. 将 `id` 和口袋锚点 `name` 的服务端长度限制为 64，并在改名请求处理前拒绝超长值。

**验收：** 重连后客户端收到已解锁列表；任何客户端发送的超长名称或 ID 均不会写入方块实体。

### 4. 文档记录与编译

**文件：**
- 本计划文档

**步骤：**
1. 记录暂缓项。
2. 使用 IDEA 项目的 JDK 21/Gradle 配置执行 `compileJava`，不执行 GameTest。

## 暂缓项

以下问题按用户要求不在本次修复：

1. 传送落点安全与朝向规则（原审查问题 5）。
2. 改名/菜单的距离与会话绑定（原审查问题 6）。
3. BER 红蓝外观（原审查问题 9），待后续模型和渲染工作实现。
4. 不新增或运行 GameTests（原审查问题 10）；本次仅做编译验证，详细行为由手工测试完成。

## 手工测试范围

完成编译后，手工验证：未解锁/伪造传送被拒绝、靠近已激活源锚点的正常传送、结构锚点加载后的索引、复制 UID 的自动重签、删除锚点后的列表清理、重连同步和 64 字符边界。
