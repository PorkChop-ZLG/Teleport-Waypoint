# Xaero 联动审查问题与修复记录

**日期：** 2026-08-17
**审查对象：** 提交 `f0a4c59`「小肥鱼做的Xaero兼容，初版」
**验证基线：** `gradlew build --offline` 通过

---

## 背景

对“传送锚点与 Xaero 地图深度联动”的初版实现进行了代码审查。该版本整体架构合理：可选依赖 + 反射加载、服务端权威传送校验、全量/激活状态同步分离。但审查发现若干会影响实际游戏表现或可移植性的问题，以下逐一记录并修复。

---

## 问题清单

### 问题 1（高）：小地图颜色传参错误，锚点可能全部显示为白色

- **位置：** `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`
- **现象：** 向 Xaero `Waypoint` 的 int 构造器传入 ARGB 颜色值（如 `0xFF9E9E9E`）。反编译确认该 int 被当作颜色索引传给 `WaypointColor.fromIndex()`，并会被 `clamp` 到 0–15，最终导致灰色/青色/绿色全部变成白色。
- **修复：** 改用 `xaero.hud.minimap.waypoint.WaypointColor` 枚举构造器，按状态选择 `GRAY` / `GREEN` / `AQUA`。

### 问题 2（高）：`build.gradle` 写死本机绝对路径，项目不可移植

- **位置：** `build.gradle`
- **现象：** `compileOnly files('D:/Minecraft/Xaero-Minimap/META-INF/jarjar/xaerolib-neoforge-1.21.1-1.0.45.jar')` 在其它机器 / CI 上不存在，编译直接失败。
- **修复：** 改为从 CurseMaven 依赖中自动解出 Xaero 自带的 `xaerolib` 嵌套 jar，并加入编译 classpath；不再依赖本机绝对路径。

### 问题 3（中）：小地图“显示锚点 / 显示名称”配置开关不会即时生效

- **位置：** `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`
- **现象：** `sync()` 仅以 `ClientWaypointState.getRevision()` 是否变化作为刷新条件；配置开关变化不会改变 revision，导致小地图残留旧状态，直到下次数据同步或重进世界。
- **修复：** 在刷新条件中同时比较上次的 `showWaypoints` / `showWaypointNames`，配置变化时强制重新同步。

### 问题 4（中）：小地图首次同步存在“吞 revision”的时序风险

- **位置：** `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`
- **现象：** 原实现先 `lastRevision = revision`，再检查 `session == null`；若首次调用时 Xaero session 尚未创建，本次 revision 被标记为已消费，之后可能一直不显示。
- **修复：** 仅当成功取得 `MinimapSession` / `MinimapWorldManager` 后才更新 `lastRevision`；session 未就绪时保持重试。

### 问题 5（中）：普通传送锚点在地图上显示原始 ID，未本地化

- **位置：**
  - `src/main/java/com/zonlong/teleportwaypoint/client/ClientWaypointInfo.java`
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/TeleportWaypointWorldReader.java`
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/TeleportWaypointWorldRenderer.java`
  - `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroMinimapIntegration.java`
- **现象：** 普通锚点（非口袋锚点）的 `name` 是原始 ID（如 `village`），地图直接显示该字符串；游戏内 GUI 则会翻译成“村庄”。
- **修复：** 为 `ClientWaypointInfo` 增加 `displayName()`，口袋锚点用字面量，普通锚点用 `Component.translatable("teleportwaypoint.waypoint." + name)`；地图 reader / renderer / minimap 统一使用该显示名。

### 问题 6（中）：只装一个 Xaero 模组时，`XaeroIntegration` 可能加载另一个模组的类

- **位置：** `src/main/java/com/zonlong/teleportwaypoint/client/xaero/XaeroIntegration.java`
- **现象：** `XaeroIntegration.tick()` 无条件调用 `XaeroMinimapIntegration.sync()`；只装 World Map 时仍可能触发 minimap 类加载，只装 Minimap 时 `XaeroIntegration` 类本身又引用 World Map 类。当前依赖 JVM 惰性解析才不崩，属于脆弱设计。
- **修复：** 在调用 minimap 同步前显式判断 `ModList.get().isLoaded("xaerominimap")`；World Map 相关方法保持原有 `isLoaded("xaeroworldmap")` 短路，避免加载不存在的模组类。

---

## 修复状态

| 问题 | 状态 | 说明 |
|---|---|---|
| 1 | 已修复 | 小地图颜色改用 `WaypointColor` 枚举 |
| 2 | 已修复 | Gradle 自动解出 `xaerolib`，移除绝对路径 |
| 3 | 已修复 | 配置变化触发小地图重新同步 |
| 4 | 已修复 | session 未就绪时不消费 revision |
| 5 | 已修复 | 普通锚点地图显示名本地化 |
| 6 | 已修复 | minimap 调用增加 mod 存在性判断 |
