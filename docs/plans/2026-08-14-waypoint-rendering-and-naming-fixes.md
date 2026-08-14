# 传送锚点：渲染与首次命名修复计划

**目标：** 修复两个锚点方块的遮挡与破坏粒子渲染，使首次放置时命名输入框为空，并将命名标题靠近输入框。

**方案：** 保持现有 JSON 方块模型，给部分形状方块关闭完整遮挡计算并声明模型粒子纹理；复用 Waystones 编辑界面中标题到输入框约 16 像素的垂直间距。新放置不预填名称；空白提交继续由服务端现有回退逻辑转换为默认名称/ID。

## 任务 1：方块模型渲染

**文件：**
- 修改：`src/main/java/com/zonlong/teleportwaypoint/block/ModBlocks.java`
- 修改：`src/main/resources/assets/teleportwaypoint/models/block/waypoint.json`
- 修改：`src/main/resources/assets/teleportwaypoint/models/block/pocket_waypoint.json`

**步骤：**
1. 为两个部分形状方块禁用完整遮挡。
2. 为两个模型指定已存在的方块粒子纹理。

**验证：** `compileJava`；手动检查相邻方块面与破坏粒子。

## 任务 2：命名界面间距

**文件：**
- 修改：`src/main/java/com/zonlong/teleportwaypoint/client/gui/AbstractRenameScreen.java`

**步骤：**
1. 将标题移动至输入框上方约 16 像素处，保持按钮和输入框现有位置。

**验证：** `compileJava`；手动打开两种命名界面。

## 任务 3：首次命名默认值

**文件：**
- 修改：`src/main/java/com/zonlong/teleportwaypoint/block/entity/WaypointBlockEntity.java`
- 修改：`src/main/java/com/zonlong/teleportwaypoint/block/PocketWaypointBlock.java`

**步骤：**
1. 新普通锚点不预填 `empty`。
2. 新口袋锚点不预填默认名称。
3. 保留保存空输入时服务端既有的默认名称/ID 回退。

**验证：** `compileJava`；手动测试空输入初始状态及空白保存后的默认值。

**测试范围：** 按用户要求不新增或运行 GameTests，仅做编译和手动验证。
