# PhantasmBriefing 使用说明

> 本文件是完整字段与高级功能参考。第一次使用请先阅读 [中文快速教程](QUICK_START.zh-CN.md)；English readers should start with the [English Quick Start](QUICK_START.en-US.md).

## 1. 模组简介

`PhantasmBriefing` 是一个基于 Minecraft Forge `1.20.1` 的剧情交互模组，核心目标是为整合包或剧情地图提供一套稳固、耐用、数据驱动的任务 NPC 与剧情演出系统。

当前版本已实现以下能力：

- 双轨制任务 NPC 系统
- 高精度像素风简报 UI
- 剧情演出模式与打字机文本
- 自定义高初速高阻力悬浮粒子特效
- 基于 Datapack 的 JSON 剧情节点加载
- 与 FTB Quests 的安全桥接

---

## 2. 运行环境

- Minecraft: `1.20.1`
- Forge: `47.4.10`
- Mod ID: `phantasmbriefing`
- Java: `17`

FTB Quests 当前已声明为可选依赖：

- 若环境中存在 `ftbquests`，模组可读取并静默推进任务进度
- 若环境中不存在 `ftbquests`，模组仍可正常加载，但相关任务联动功能会自动降级

---

## 3. 主要功能概览

### 3.1 双轨制任务 NPC

模组支持两种任务 NPC 来源：

1. 专有实体模式
2. 原版转化模式

两者都会被统一识别为“任务 NPC”，并具有以下特性：

- 无敌
- 不可推动
- 使用独立且可改键的对话入口；原实体普通右键能力保持可用，职业村民继续打开交易
- 可绑定 `questNodeId`

### 3.2 剧情节点驱动

所有剧情文本与跳转逻辑均通过 JSON 文件定义，而不是硬编码在 Java 中。

模组当前支持两类内容来源：

- 内置 Datapack 资源
- 外置 `config` 内容包覆盖

### 3.3 两种前端呈现模式

一个剧情节点可以指定两种表现形式：

- `briefing`：打开行动简报面板
- `cinematic`：进入电影黑边演出模式

### 3.4 FTB Quests 联动

当一个剧情节点播放完成后，模组可在服务端直接调用 FTB Quests API：

- 检查前置任务是否完成
- 静默完成指定任务
- 自动触发奖励发放逻辑

---

## 4. 常用管理员命令

### 4.0 重载内容包

```mcfunction
/pbriefing reload
```

说明：

- 该命令会触发服务器资源重载
- 会一并重读 `dialogues / npc bindings / shops / quests / wallet`
- 该命令用于重载本模组的全部数据驱动内容

### 4.1 将生物标记为任务 NPC

先将准星对准一个非玩家 `LivingEntity`，再执行：

```mcfunction
/pbriefing setnpc arcadia_intro
/pbriefing removenpc
```

当 `/pbriefing setnpc` 的目标是职业村民时，PB 仅附加任务 NPC Capability，不替换实体或报价数据：

- 普通右键：进入原版村民交易，兼容第三方“自定义村民交易”注入的报价
- 对准村民按“开始与 NPC 对话”键：打开 PhantasmBriefing 任务对话（默认 `V`）
- 村民职业、等级、交易经验、补货以及升级后新增报价均由原村民逻辑继续维护

说明：

- `arcadia_intro` 是剧情节点 ID
- 适用于原版生物与绝大多数模组生物
- `setnpc` 不会替换实体类型，而是将当前生物转换为任务 NPC
- 转换后，对准任意任务 NPC 按“开始与 NPC 对话”键进入对话；该按键可在“选项 → 控制 → 按键绑定 → PhantasmBriefing”中修改
- 转换期间实体原本的数据与原生能力会被保留
- `removenpc` 用于将该转化型任务 NPC 恢复为普通生物，并恢复记录的原始重力状态

### 4.1.1 按 NPC 绑定生成完整 NPC

```mcfunction
/pbriefing npc spawn <bindingId>
/pbriefing npc spawn <bindingId> <x> <y> <z>
```

不填写坐标时，NPC 会生成在执行指令玩家前方两格；坐标支持 `~` 相对写法。`bindingId` 提供自动补全。生成结果会继承该 NPC 绑定的实体类型、任务节点、身份、自定义名称和村民职业；如果绑定包含结构生成规则，还会使用第一条规则中的 AI、名称显示和村民等级设置。该指令每执行一次都会生成一个新 NPC，不会写入结构自动生成的去重记录。

### 4.3 生成专有任务 NPC 刷怪蛋

```mcfunction
/give @p phantasmbriefing:static_quest_npc_spawn_egg
```

说明：

- 该刷怪蛋生成的是模组专有静态任务 NPC
- 生成后可通过后续交互或指令绑定剧情节点

### 4.4 打开钱包总览

```mcfunction
/pbriefing wallet
```

说明：

- 打开当前玩家的货币总览界面
- 当前“余额”会根据 `phantasm_wallet` 中定义的可接受物品实时统计
- 若安装 Lightman's Currency `1.20.1-2.2.4.0c`，且钱包的 `iconItemId` 是有效 LC 硬币，则余额与扣款自动改用 LC 玩家可用资金；这会包含 LC 装备钱包及其 Curios 钱包槽支持
- 例如内置的 `guild_supply_token` 默认统计背包中的绿宝石总数

### 4.5 直接打开数据驱动商店

```mcfunction
/pbriefing shop open phantasmbriefing:guild_supply_token_shop
```

说明：

- 可直接测试普通物品商店与钱包商店
- 当前同一命令也会尝试解析本模组的独立 `trades` 定义
- 当商店 JSON 定义了 `walletId` 时，会打开自定义钱包商店界面
- 当商店 JSON 未定义 `walletId` 时，仍走原版 `MerchantMenu`

例如也可以直接打开独立交易定义：

```mcfunction
/pbriefing shop open phantasmbriefing:guild_supply_trade
```

也可对指定玩家批量打开：

```mcfunction
/pbriefing shop open @a phantasmbriefing:guild_supply_token_shop
```

### 4.6 直接启动对话节点

```mcfunction
/pbriefing dialogue start @a arcadia_demo_dialogue
```

说明：

- 当前参数已经对应独立的 `dialogueId`
- 服务端会先解析 `startNodeId`，再展开并打开该对话的起始节点
- 适合绕过 NPC，直接测试本模组的对话容器是否能成功打开

### 4.7 切换任务追踪 HUD

### 4.7 任务管理命令

```mcfunction
/pbriefing quest give @a phantasmbriefing:arcadia_demo_quest
/pbriefing quest complete @a phantasmbriefing:arcadia_demo_quest
/pbriefing quest reset @a phantasmbriefing:arcadia_demo_quest
/pbriefing quest reset_all
/pbriefing quest reset_all @a
```

说明：

- `give` 会直接把指定任务写入为进行中，适合测试本模组任务链
- `complete` 会直接把指定任务写为已完成，并继续执行任务完成动作与后续任务推荐追踪
- `reset` 会清空指定任务在 `PhantasmBriefing` 内部保存的状态、阶段、目标完成记录与追踪信息
- `reset_all` 在玩家执行时默认只重置自己；在控制台执行时默认重置全部在线玩家
- 若任务填写了 `ftbQuestId`，以上 `reset / reset_all` 仅回滚本模组内部状态，不会回滚外部 FTB 已完成态

### 4.8 切换任务追踪 HUD

```mcfunction
/pbriefing quest tracker_hud on
/pbriefing quest tracker_hud off
```

说明：

- `on` 会重新显示当前左上角任务追踪面板
- `off` 会关闭该面板，减少画面 UI 堆叠
- 关闭后仍会保留附近任务实体的感叹号指示
- 该开关现在会写入玩家持久化数据，重进游戏后仍会保持上次选择

### 4.9 客户端配置菜单

配置菜单采用与交易终端一致的深色卷宗风格，可以通过以下入口打开：

- Minecraft 模组列表中选择 `PhantasmBriefing`，点击“配置”
- 打开任务日志后，点击底部“设置”

菜单分为三组：

- `任务追踪`：任务追踪总开关、面板开关、显示全部目标、左上/右上位置
- `目标指引`：世界目标指引、NPC 任务标记、距离显示
- `对话操作`：在“底部对话栏”“头像气泡”“头顶气泡”之间循环选择，并设置 ESC 是直接退出当前对白，还是跳过并推进到下一节点

所有选项保存到实例的客户端配置文件：

```text
config/phantasmbriefing-client.toml
```

配置菜单中的“任务追踪总开关”是纯客户端总开关，关闭后会同时隐藏任务面板、世界目标与 NPC 指引；命令 `/pbriefing quest tracker_hud off` 则是服务端保存的玩家面板开关，两者互不覆盖。

玩家可以选择两种需要注视 NPC 的气泡风格：

- NPC 对话仍是 HUD，不会锁定鼠标、移动、跳跃或战斗输入
- `头像气泡`：由当前生物模型的头部头像与从头像右侧伸出的文字框组成，可保留村民职业帽子和实体外观差异
- `头顶气泡`：参考车万女仆的表现，气泡绑定在当前 NPC 头顶、随实体移动并始终朝向镜头，尾巴指向说话实体；选项仍以清晰的数字键气泡显示在 HUD 上
- 三种布局统一使用原始对话栏的深色半透明背景、青色标题与强调线、浅色正文和金色选项
- 只有准星直视当前说话 NPC、距离不超过 6 格且中间没有方块遮挡时，才显示对白和选项
- 移开视角时气泡立即隐藏，逐字播放暂停；重新看回同一个 NPC 后从离开前的文字和选项状态继续
- 移开视角期间 Enter 与数字键不会推进对白或误选选项
- 没有 NPC 上下文的命令测试对话会自动回退到底部对话栏，避免会话无法操作

---

## 5. 任务 NPC 使用流程

### 5.1 基础流程

1. 通过刷怪蛋生成专有 NPC，或将原版生物转化为任务 NPC
2. 给该 NPC 绑定一个 `questNodeId`
3. 在 Datapack 中编写与该 `questNodeId` 对应的 JSON 节点
4. 玩家对准六格内 NPC，按“开始与 NPC 对话”键（默认 `V`）
5. 服务端读取节点数据并下发到客户端
6. 客户端按玩家设置显示底部对话栏、头像气泡，或绑定在 NPC 头顶的世界空间气泡
7. 节点结束后，客户端向服务端发送完成回执
8. 服务端根据节点配置推进 FTB Quests 或跳转下一节点

### 5.2 交互闭环特性

该流程具备以下优点：

- 不依赖后台指令通信
- 不依赖 `tellraw` 或命令权限
- 不会因为命令格式修改导致整条链路失效
- 服务端统一裁决进度，安全性更高

### 5.3 附近任务实体感叹号

当前服务端会持续判定玩家附近的任务实体，并把有效目标同步到客户端：

- 若附近存在可识别的任务 NPC，会在屏幕上方生成一个 `!` 图标
- 图标会持续朝向当前优先级最高、距离合适的任务实体
- 该提示是轻量替代方案，用来替代过重的常驻任务气泡
- 当前实现由服务端判定“该实体此刻是否真的能为这个玩家触发任务对话”，只有有效目标才会同步到客户端

当前三态表现为：

- `可接`：黄色，表示该实体当前可给玩家接取任务
- `推进`：蓝色，表示该实体当前对应玩家某条 `进行中` 任务的下一推进节点
- `可交`：绿色，表示该实体当前对应玩家某条 `可交` 任务的交付节点
- 服务端现在会先按任务运行时状态 `可交 > 进行中 > 可接` 计算实体提示，再在同状态下优先选择已被追踪的任务实体
- 若任务 JSON 定义了 `phases[].objectives[].targetNodeIds + hintType`，服务端会优先按“当前未完成 objective”绑定的节点参与状态提示判定
- 当当前阶段没有 objectives 时，才回退到 `phases[].targetNodeIds + hintType`
- 若连阶段目标都未定义，最后才回退到对话引用推断

---

## 6. JSON 剧情配置

### 6.1 文件位置

内置剧情定义请放入以下目录：

```text
data/phantasmbriefing/phantasm_dialogues/
```

例如：

```text
src/main/resources/data/phantasmbriefing/phantasm_dialogues/arcadia_intro.json
```

外置剧情定义请放入 Minecraft 实例的配置目录：

```text
config/phantasmbriefing/briefing_pack/phantasm_dialogues/
```

例如：

```text
config/phantasmbriefing/briefing_pack/phantasm_dialogues/arcadia_intro.json
```

### 6.2 加载优先级与覆盖规则

加载顺序如下：

1. 先加载内置 Datapack 节点
2. 再加载外置 `briefing_pack` 节点

覆盖规则如下：

- 若外置文件的 `nodeId` 或 `dialogueId` 与内置资源相同，则外置资源整体覆盖内置资源
- 当前版本采用“整资源覆盖”，不做字段级合并
- 若外置节点或对话容器是全新 ID，则会作为新增资源直接加入运行时注册表
- 外置目录中的非法 JSON 不会导致游戏崩溃，但会在日志中报错

### 6.3 对话容器示例

```json
{
  "id": "arcadia_demo_dialogue",
  "defaultNpc": {
    "mode": "literal",
    "value": "引导员"
  },
  "startNodeId": "root",
  "nodes": [
    {
      "nodeId": "root",
      "presentation": "briefing",
      "text": {
        "mode": "literal",
        "value": "测试文本"
      },
      "choices": [
        {
          "choiceId": "continue_memory_echo",
          "text": {
            "mode": "literal",
            "value": "继续前往记忆回响"
          },
          "nextNodeId": "memory_echo"
        }
      ]
    }
  ]
}
```

### 6.4 字段说明

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `id` | `String` | 是 | 对话容器唯一 ID |
| `defaultNpc` | `Object` | 否 | 默认 NPC 名称，当前主要支持 `literal.value` |
| `startNodeId` | `String` | 否 | 起始本地节点 ID，默认 `root` |
| `nodes` | `List<Object>` | 是 | 当前对话容器内部的节点列表 |

单个 `node` 常用字段：

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `nodeId` | `String` | 是 | 对话容器内部节点 ID |
| `presentation` | `String` | 否 | 呈现模式，可选 `briefing` 或 `cinematic` |
| `text` | `Object` | 否 | 文本对象，当前主要支持 `literal.value` |
| `lines` | `List<String>` | 否 | 兼容旧格式的多行文本；若填写则优先使用 |
| `nextNodeId` | `String` | 否 | 无选项时的默认下一节点 |
| `triggerVFX` | `Boolean` | 否 | 是否触发粒子特效 |
| `conditions` | `List<Object>` | 否 | 节点可见条件列表 |
| `choices` | `List<Object>` | 否 | 选项列表 |

### 6.5 `choices` 与 `actions` 字段说明

单个 `choice` 结构如下：

```json
{
  "choiceId": "accept_briefing",
  "text": {
    "mode": "literal",
    "value": "继续前往记忆回响"
  },
  "nextNodeId": "memory_echo",
  "actions": [
    {
      "type": "open_shop",
      "shopId": "guild_supply_shop"
    }
  ]
}
```

字段说明：

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `choiceId` | `String` | 是 | 选项唯一 ID |
| `text` | `Object` | 是 | 选项显示文本，当前主要支持 `literal.value` |
| `nextNodeId` | `String` | 否 | 选中后跳转到当前对话容器内的另一个节点 |
| `conditions` | `List<Object>` | 否 | 选项条件列表 |
| `actions` | `List<Object>` | 否 | 选项执行的动作列表 |

当前支持的动作类型：

| `type` | 是否需要 `targetId` | 说明 |
|---|---|---|
| `open_node` | 是 | 打开另一个节点 |
| `start_quest` | 是 | 接取一个本模组独立任务 |
| `complete_objective` | 是 | 完成指定任务的一个 objective |
| `set_quest_phase` | 是 | 将指定任务切换到某个阶段 |
| `complete_quest` | 是 | 优先完成同名本模组任务，找不到时回退为 FTB 任务完成 |
| `open_shop` | 是 | 打开指定 `shopId` 的数据驱动商店；若未命中商店，也会尝试按同 ID 打开独立 `trade` |
| `open_trade` | 是 | `open_shop` 的同义写法 |
| `set_flag` | 是 | 给当前玩家写入一个任务/状态旗标 |
| `close` | 否 | `close_dialogue` 的简写同义写法 |
| `close_dialogue` | 否 | 关闭当前对话 |

说明：

- 旧格式单节点 JSON 仍然可用
- 新格式 `dialogueId + startNodeId + nodes[]` 会在加载时展开成运行时节点图
- `choice.nextNodeId` 会自动重写为当前对话容器内部的实际下一节点
- `complete_objective` 需同时填写 `questId` 与 `objectiveId`
- `set_quest_phase` 需同时填写 `questId` 与 `phaseId`
- 当前版本已兼容常用的 `close / open_shop / open_trade / start_quest`

### 6.6 条件系统

当前已接入的条件类型：

| `condition` | 说明 |
|---|---|
| `phantasmbriefing:always` | 恒为真 |
| `phantasmbriefing:has_flag` | 检查玩家是否拥有指定旗标 |
| `phantasmbriefing:quest_accepted` | 检查任务是否已进入进行中 |
| `phantasmbriefing:quest_available` | 检查任务当前是否可接取 |
| `phantasmbriefing:quest_ready_to_turn_in` | 检查任务当前是否处于可交状态 |
| `phantasmbriefing:quest_completed` | 检查任务是否已完成 |
| `phantasmbriefing:quest_not_started` | 检查任务是否尚未接取且未完成 |
| `phantasmbriefing:quest_phase` | 检查任务当前阶段 ID |
| `phantasmbriefing:quest_objective_completed` | 检查任务目标是否已完成 |
| `phantasmbriefing:and` | 所有子条件都为真 |
| `phantasmbriefing:or` | 任意子条件为真 |
| `phantasmbriefing:not` | 反转内部条件 |
| `phantasmbriefing:entity_name` | 检查当前交互实体名称 |
| `phantasmbriefing:villager_profession` | 检查当前交互村民职业 |

示例：

```json
{
  "condition": "phantasmbriefing:has_flag",
  "flag": "arcadia_briefed"
}
```

复合条件示例：

```json
{
  "condition": "phantasmbriefing:and",
  "conditions": [
    {
      "condition": "phantasmbriefing:has_flag",
      "flag": "guild_access"
    },
    {
      "condition": "phantasmbriefing:villager_profession",
      "profession": "minecraft:librarian"
    }
  ]
}
```

任务完成条件示例：

```json
{
  "condition": "phantasmbriefing:quest_completed",
  "questId": "arcadia_demo_quest"
}
```

任务可交条件示例：

```json
{
  "condition": "phantasmbriefing:quest_ready_to_turn_in",
  "questId": "arcadia_demo_quest"
}
```

说明：

- 条件判定完全在服务端执行
- 当前交互实体条件主要在“通过 NPC 打开对话”时生效
- `quest_available / quest_accepted / quest_ready_to_turn_in / quest_completed / quest_not_started / quest_phase / quest_objective_completed` 已接入 `PhantasmBriefing` 的独立任务状态层
- 当前示例默认使用 `phantasmbriefing:*` 作为本项目命名空间；旧的 `chronicle_engine:*` 写法仍保留兼容

### 6.6 `presentation` 字段说明

#### `briefing`

用于打开第二阶段的简报 UI，适合：

- 行动目标说明
- 引导文本
- 任务接取前说明

#### `cinematic`

用于打开第三阶段的演出 Overlay，适合：

- 剧情对白
- 记忆回响
- 强模态沉浸式提示

---

## 7. 外置 NPC 绑定配置

### 7.1 文件位置

内置 NPC 绑定规则可放入：

```text
data/phantasmbriefing/phantasm_npc_bindings/
```

外置 NPC 绑定规则可放入：

```text
config/phantasmbriefing/briefing_pack/phantasm_npc_bindings/
```

### 7.2 作用说明

该配置用于把“某类实体”直接绑定到一个 `questNodeId`，不再要求你先执行：

```mcfunction
/pbriefing setnpc <nodeId>
```

适用场景：

- 整合包中固定职业的村民 NPC
- 带自定义名称的剧情角色
- 希望通过内容包直接批量配置的原版实体

### 7.3 基本示例

```json
{
  "bindingId": "arcadia_guildmaster_farmer",
  "entityType": "minecraft:villager",
  "profession": "minecraft:farmer",
  "customName": "公会长",
  "questNodeId": "arcadia_intro",
  "questId": "arcadia_demo_quest",
  "prerequisiteLockedText": "现在还不到时候。先完成前面的委托，再来找我。"
}
```

### 7.4 字段说明

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `bindingId` | `String` | 是 | 绑定规则唯一 ID |
| `entityType` | `String` | 是 | 实体类型，如 `minecraft:villager` |
| `profession` | `String` | 否 | 村民职业，如 `minecraft:farmer` |
| `customName` | `String` | 否 | 实体自定义名称，要求精确匹配 |
| `questNodeId` | `String` | 是 | 命中的实体应触发的剧情节点 ID |
| `questId` | `String` | 否 | 该 NPC 对应的本模组任务 ID；用于读取该任务的 PB/FTB 前置 |
| `prerequisiteLockedText` | `String` | 否 | 任一前置任务未完成时显示的专用对白；支持换行，留空使用默认提示 |
| `spawn` | `Object` | 否 | 单条结构自动生成规则 |
| `spawns` | `Array<Object>` | 否 | 多条结构自动生成规则；可让同一 NPC 定义出现在多类结构中 |

当 `questId` 对应的任务包含 `requiredQuestId` 或 `parentQuestIds` 时，玩家按对话键后，服务端会先逐项检查前置完成状态：本模组任务读取 PB 玩家进度；未命中本模组任务的 ID 自动按 FTB Quests 任务检查。任一前置未完成时，只向当前 NPC 显示 `prerequisiteLockedText`，不进入原对话、不执行接取动作，也不把此次按键计为一次成功的任务交互。全部前置完成后自动恢复 `questNodeId` 对应的正式对话。未填写 `questId` 的旧 NPC 绑定保持原行为。

### 7.5 结构自动生成

NPC 绑定可额外声明 `spawn` 或 `spawns`。当服务端加载到匹配结构的区块时，PB 会使用 `entityType` 创建真实生物实体，再写入该绑定的 NPC 身份和 `questNodeId`：

```json
{
  "bindingId": "my_story:archivist_elai",
  "entityType": "minecraft:villager",
  "profession": "minecraft:librarian",
  "customName": "档案管理员伊莱",
  "questNodeId": "my_story:archivist_elai_dialogue::root",
  "spawns": [
    {
      "ruleId": "archive_spawn",
      "structureId": "minecraft:stronghold",
      "dimension": "minecraft:overworld",
      "anchor": "bottom_center",
      "offset": [0, 1, 0],
      "snapToSurface": false,
      "yaw": 180,
      "noAi": false,
      "nameVisible": true,
      "villagerLevel": 1
    }
  ]
}
```

生成规则字段：

| 字段名 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ruleId` | `String` | 自动生成 | 生成规则稳定 ID |
| `structureId` | `String` | 无 | 必填，目标结构注册 ID |
| `dimension` | `String` | 空 | 留空允许所有维度，否则只在指定维度生成 |
| `anchor` | `String` | `bottom_center` | 可选 `bottom_center / center / min / max / start_chunk` |
| `offset` | `[x,y,z]` | `[0,1,0]` | 在结构锚点基础上增加的方块偏移 |
| `snapToSurface` | `Boolean` | `true` | 在锚点附近寻找安全地表，避免生成到地下、墙内、液体中或普通建筑屋顶；地下结构内部生成时设为 `false` |
| `yaw` | `Number` | `0` | NPC 初始水平朝向 |
| `noAi` | `Boolean` | `false` | 是否禁用生物 AI；职业村民建议保持 `false`，以保留工作、补货和交易升级 |
| `nameVisible` | `Boolean` | `true` | 是否显示 `customName` 名牌 |
| `villagerLevel` | `Integer` | `1` | 村民初始交易等级，范围 1–5 |

说明：

- `entityType` 决定实际生成的生物及模型，不是外观替换；也可填写已注册的其他模组生物 ID，但必须是生物实体。
- `snapToSurface = true` 时，Y 偏移以地表支撑方块为基准，默认 `offsetY = 1` 正好落在地面上；只检查已经完整加载的区块，不会为了寻找位置递归加载新区块。
- 从旧版本升级后，已由旧算法生成在地下的 NPC 会在其结构再次加载时迁移一次到安全地表；设置了 `snapToSurface = false` 的 NPC 不会迁移。
- PB 会在世界存档中记录“结构实例 + NPC 绑定 + 规则”，同一结构反复加载不会重复生成。
- 生成的职业村民仍是原生 `Villager`，因此会继续使用原版或“自定义村民交易”模组提供的报价、补货、经验和升级流程。
- 多个实例只共享 NPC 定义，不保存实体私有任务阶段。任务、目标、旗标与对话条件始终读取玩家自己的持久化数据，所以同一玩家与任意一个“档案管理员伊莱”交互时看到的是同一份进度；不同玩家仍各自独立。

### 7.6 匹配规则

当前版本按以下顺序判断：

1. 先检查实体自身是否已经写入 `questNodeId`
2. 若没有，再匹配外置或内置 NPC 绑定规则

匹配条件说明：

- `entityType` 必须命中
- `profession` 若填写，则仅对村民生效且必须命中
- `customName` 若填写，则实体必须拥有完全一致的自定义名称

若规则命中，该实体会被视为任务 NPC：

- 对准六格内任务 NPC 按自定义对话键进入剧情；职业村民普通右键始终保留交易
- 伤害会被拦截
- 对非专有实体会执行位置锁定，作为稳固交互锚点

---

## 8. 节点跳转与任务推进

### 7.1 下一节点跳转

若当前节点配置了：

```json
"nextNodeId": "arcadia_memory_echo"
```

则当前节点结束后，服务端会自动查找并下发下一个节点。

若使用 `options`，也可以通过：

```json
{
  "type": "open_node",
  "targetId": "arcadia_memory_echo"
}
```

实现分支跳转。

### 7.2 接取任务

若使用 `options`，可以通过：

```json
{
  "type": "start_quest",
  "questId": "arcadia_demo_quest"
}
```

直接接取一个本模组独立任务。

任务接取后当前会自动：

1. 将任务状态写为 `active`
2. 写入 `initialPhaseId`
3. 将该任务加入当前玩家的追踪候选
4. 写入 `flagsToSetOnAccept`
5. 执行 `acceptActions`
6. 刷新任务追踪 HUD

若任务定义了：

```json
{
  "questId": "arcadia_demo_quest",
  "nextQuestIds": [
    "arcadia_followup_quest"
  ]
}
```

则当前任务完成后，服务端会尝试把这些后续任务加入追踪候选。

若在对话动作中使用：

```json
{
  "type": "complete_objective",
  "questId": "arcadia_demo_quest",
  "objectiveId": "experience_memory_echo"
}
```

则服务端会将该 objective 标记为已完成。

若该 objective 所在阶段定义了：

```json
{
  "phaseId": "accepted",
  "nextPhaseId": "ready_to_turn_in"
}
```

且当前阶段所有 objectives 均已完成，则服务端会自动切换到下一阶段。

若该阶段同时定义了：

```json
{
  "phaseId": "ready_to_turn_in",
  "actions": [
    {
      "type": "set_flag",
      "flag": "arcadia_ready_to_turn_in"
    }
  ]
}
```

则玩家进入该阶段时，服务端会立刻执行这些阶段动作。

若阶段改为：

```json
{
  "phaseId": "ready_to_turn_in",
  "completeQuestOnObjectivesCompleted": true
}
```

则当前阶段所有 objectives 完成后，服务端会直接自动完成该任务。

若继续使用：

```json
{
  "type": "set_quest_phase",
  "questId": "arcadia_demo_quest",
  "phaseId": "ready_to_turn_in"
}
```

则服务端会切换该任务的当前阶段，并立即刷新：

- 左上角任务追踪目标文本
- 任务日志中的阶段目标文本
- 附近任务实体的 `推进 / 可交` 提示类型

### 7.3 自动完成任务

若节点配置了：

```json
"ftbQuestIdToComplete": "1B766784FBBE7D51"
```

则在玩家完成该节点后，服务端会尝试：

1. 读取 FTB 任务对象
2. 将该任务标记为完成
3. 自动触发奖励发放逻辑

若使用 `options`，也可以通过：

```json
{
  "type": "complete_quest",
  "targetId": "1B766784FBBE7D51"
}
```

把任务推进挂到具体选项上。

说明：

- 若 `targetId` 命中的是 `phantasm_quests` 中定义的 `questId`，会优先完成本模组独立任务
- 若未命中内置任务定义，则回退为旧的 FTB 任务完成逻辑

### 7.4 前置任务校验

若节点配置了：

```json
"requiredQuestId": "5A8F81F26C0A1234"
```

则只有在玩家已完成该前置任务时，节点才会被允许打开。

说明：

- `requiredQuestId` 现在优先检查本模组独立任务完成态
- 若未命中内置任务定义，则兼容按 FTB 任务 ID 检查

### 7.5 打开商店

若使用 `options`，可以通过：

```json
{
  "type": "open_shop",
  "targetId": "guild_supply_shop"
}
```

直接打开一个数据驱动商店。

---

## 9. 商店配置

### 9.1 文件位置

内置商店定义可放入：

```text
data/phantasmbriefing/phantasm_shops/
```

外置商店定义可放入：

```text
config/phantasmbriefing/briefing_pack/phantasm_shops/
```

### 9.2 基本示例

```json
{
  "shopId": "guild_supply_shop",
  "title": "公会补给",
  "offers": [
    {
      "costA": {
        "item": "minecraft:emerald",
        "count": 2
      },
      "result": {
        "item": "minecraft:bread",
        "count": 4
      }
    },
    {
      "costA": {
        "item": "minecraft:emerald",
        "count": 4
      },
      "costB": {
        "item": "minecraft:stick",
        "count": 2
      },
      "result": {
        "item": "minecraft:stone_sword",
        "count": 1
      }
    }
  ]
}
```

### 9.3 字段说明

顶层字段：

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `shopId` | `String` | 是 | 商店唯一 ID |
| `title` | `String` | 否 | 商店标题 |
| `walletId` | `String` | 否 | 钱包货币 ID；填写后商店切换为钱包商店模式 |
| `openCondition` | `Object` | 否 | 商店开启条件 |
| `offers` | `List<Object>` | 是 | 报价列表 |

单个报价字段：

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `costA` | `Object` | 是 | 第一成本物品 |
| `costB` | `Object` | 否 | 第二成本物品 |
| `result` | `Object` | 是 | 输出物品 |
| `walletCost` | `int` | 否 | 钱包货币价格；仅在填写 `walletId` 的商店中生效 |
| `requiredQuestId` | `String` | 否 | 该报价的前置 FTB 任务 ID |
| `visibleCondition` | `Object` | 否 | 商品可见条件 |
| `hideUntilUnlocked` | `Boolean` | 否 | 未解锁时是否隐藏，默认 `false` |
| `maxUses` | `int` | 否 | 最大使用次数，默认 `9999` |
| `villagerXp` | `int` | 否 | 交易经验，默认 `0` |
| `priceMultiplier` | `float` | 否 | 价格浮动系数，默认 `0.0` |

说明：

- 未填写 `walletId` 时，商店必须使用 `costA / costB` 走原版物品交易
- 填写 `walletId` 时，商店进入钱包商店路径，报价应使用 `walletCost`
- 当前实现不支持同一商店混用“物品成本”和“钱包成本”
- `openCondition` 和 `visibleCondition` 复用第 `6.6` 节中的条件语法

物品字段：

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `item` | `String` | 是 | 物品 ID |
| `count` | `int` | 否 | 数量，默认 `1` |

### 9.4 报价前置规则

若某个报价填写：

```json
{
  "requiredQuestId": "1B766784FBBE7D51",
  "hideUntilUnlocked": false
}
```

则行为如下：

- 玩家已完成该任务：报价正常可购买
- 玩家未完成该任务且 `hideUntilUnlocked = false`：报价会显示在交易列表中，但处于锁定不可购买状态
- 玩家未完成该任务且 `hideUntilUnlocked = true`：报价会直接隐藏，不显示在交易列表中

### 9.5 钱包商店示例

```json
{
  "shopId": "guild_supply_token_shop",
  "title": "公会军需处",
  "walletId": "guild_supply_token",
  "offers": [
    {
      "result": {
        "item": "minecraft:bread",
        "count": 4
      },
      "walletCost": 1
    },
    {
      "result": {
        "item": "minecraft:iron_sword",
        "count": 1
      },
      "walletCost": 5,
      "requiredQuestId": "1B766784FBBE7D51",
      "hideUntilUnlocked": false
    }
  ]
}
```

说明：

- `walletId = "guild_supply_token"` 表示该商店使用 `phantasm_wallet/guild_supply_token.json` 中定义的货币
- 当前内置示例把 `minecraft:emerald` 作为该货币的可接受物品
- 玩家购买时，服务端会直接从背包中扣除对应物品数量，而不是使用独立数值账户

### 9.6 独立交易配置

内置交易定义可放入：

```text
data/phantasmbriefing/phantasm_trades/
```

外置交易定义可放入：

```text
config/phantasmbriefing/briefing_pack/phantasm_trades/
```

基本示例：

```json
{
  "tradeId": "phantasmbriefing:guild_supply_trade",
  "displayName": {
    "mode": "literal",
    "value": "公会补给交易"
  },
  "categories": [
    {
      "categoryId": "supplies",
      "displayName": {
        "mode": "literal",
        "value": "基础补给"
      }
    }
  ],
  "entries": {
    "bread_bundle": {
      "entryId": "bread_bundle",
      "category": "supplies",
      "costs": [
        {
          "type": "item",
          "itemId": "minecraft:emerald",
          "count": 1
        }
      ],
      "rewards": [
        {
          "type": "item",
          "itemId": "minecraft:bread",
          "count": 4
        }
      ]
    }
  }
}
```

说明：

- `tradeId` 与 `shopId` 一样需要全局唯一，建议使用 `namespace:path`
- 当前实现保留 `categories / entries / costs / rewards` 这套作者向结构
- `entries` 当前最多支持 `2` 个物品成本和 `1` 个物品奖励，并复用原版 `MerchantMenu`
- `displayName` 支持 `literal` 与 `translate` 两种最常见写法
- `open_shop` 与 `open_trade` 都可以打开这里定义的交易
- `costs / rewards` 当前仅支持 `type = item`

### 9.7 钱包货币定义

内置钱包定义可放入：

```text
data/phantasmbriefing/phantasm_wallet/
```

外置钱包定义可放入：

```text
config/phantasmbriefing/briefing_pack/phantasm_wallet/
```

基本示例：

```json
{
  "currencyId": "guild_supply_token",
  "itemId": "minecraft:emerald",
  "displayName": {
    "mode": "literal",
    "value": "公会补给券"
  },
  "sortOrder": 0,
  "itemIds": [
    "minecraft:emerald"
  ]
}
```

字段说明：

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `currencyId` | `String` | 是 | 钱包货币唯一 ID |
| `itemId` | `String` | 是 | 展示用主物品 ID |
| `displayName` | `Object` | 否 | 当前主要支持 `literal.value` 作为显示名 |
| `sortOrder` | `int` | 否 | 钱包总览中的排序权重 |
| `itemIds` | `List<String>` | 否 | 该货币接受的全部物品 ID；不填时退化为 `itemId` |

---

## 10. 简报模式说明

当 `presentation = "briefing"` 时：

- 屏幕中央显示深色半透明面板
- 顶部显示标题
- 中间显示多行简报文本
- 底部显示“好的，已知晓”按钮

玩家点击按钮后：

1. 客户端发送节点完成回执
2. 服务端推进任务或下一节点

---

## 11. 任务日志

### 11.1 文件位置

内置任务日志定义可放入：

```text
data/phantasmbriefing/phantasm_quests/
```

外置任务日志定义可放入：

```text
config/phantasmbriefing/briefing_pack/phantasm_quests/
```

### 11.2 基本示例

```json
{
  "questId": "arcadia_demo_quest",
  "title": "引导委托",
  "description": "这是一条用于演示本模组独立任务状态的任务。",
  "objectiveLines": [
    "通过对话接取任务。",
    "打开任务日志或任务追踪确认状态已切换为进行中。"
  ],
  "repeatable": false,
  "initialPhaseId": "accepted",
  "phases": [
    {
      "phaseId": "accepted",
      "objectiveLines": [
        "推进引导委托。"
      ],
      "nextPhaseId": "ready_to_turn_in",
      "objectives": [
        {
          "objectiveId": "experience_memory_echo",
          "objectiveLines": [
            "回到引导员处继续对话，进入记忆回响。"
          ],
          "targetNodeIds": [
            "arcadia_demo_dialogue::root"
          ],
          "hintType": "advance"
        },
        {
          "objectiveId": "confirm_memory_trace",
          "objectiveLines": [
            "再次与引导员交谈，确认回响已经稳定。"
          ],
          "targetNodeIds": [
            "arcadia_demo_dialogue::root"
          ],
          "conditions": [
            {
              "condition": "phantasmbriefing:quest_objective_completed",
              "questId": "arcadia_demo_quest",
              "objectiveId": "experience_memory_echo"
            }
          ],
          "actions": [
            {
              "type": "set_flag",
              "flag": "arcadia_memory_trace_confirmed"
            }
          ],
          "hintType": "advance"
        }
      ]
    },
    {
      "phaseId": "ready_to_turn_in",
      "objectiveLines": [
        "准备交付当前委托。"
      ],
      "actions": [
        {
          "type": "set_flag",
          "flag": "arcadia_ready_to_turn_in"
        }
      ],
      "completeQuestOnObjectivesCompleted": true,
      "objectives": [
        {
          "objectiveId": "turn_in_to_guide",
          "objectiveLines": [
            "返回引导员处交付当前委托。"
          ],
          "targetNodeIds": [
            "arcadia_demo_dialogue::root"
          ],
          "conditions": [
            {
              "condition": "phantasmbriefing:has_flag",
              "flag": "arcadia_memory_trace_confirmed"
            },
            {
              "condition": "phantasmbriefing:has_flag",
              "flag": "arcadia_ready_to_turn_in"
            }
          ],
          "hintType": "complete"
        }
      ],
      "hintType": "complete"
    }
  ],
  "unlockConditions": [
    {
      "condition": "phantasmbriefing:always"
    }
  ],
  "nextQuestIds": [
    "arcadia_followup_quest"
  ],
  "flagsToSetOnAccept": [
    "arcadia_demo_accepted"
  ],
  "acceptActions": [
    {
      "type": "set_flag",
      "flag": "arcadia_demo_accept_actions_ran"
    }
  ],
  "completeActions": [
    {
      "type": "set_flag",
      "flag": "arcadia_demo_finished"
    }
  ],
  "trackerTitle": "接受引导委托"
}
```

### 11.3 字段说明

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `questId` | `String` | 是 | 任务条目唯一 ID |
| `title` | `String` | 是 | 任务标题 |
| `description` | `String` | 否 | 任务描述 |
| `objectiveLines` | `List<String>` | 否 | 任务目标文本 |
| `repeatable` | `Boolean` | 否 | 是否允许重复接取，默认 `false` |
| `initialPhaseId` | `String` | 否 | 初始阶段 ID |
| `phases` | `List<Object>` | 否 | 阶段定义；可覆盖当前阶段目标文本与实体提示语义 |
| `unlockConditions` | `List<Object>` | 否 | 任务解锁条件 |
| `nextQuestIds` | `List<String>` | 否 | 当前任务完成后优先加入追踪候选的后续任务 ID 列表 |
| `flagsToSetOnAccept` | `List<String>` | 否 | 接取任务时写入的旗标 |
| `acceptActions` | `List<Object>` | 否 | 任务进入 `active` 后、初始阶段应用前执行的服务端动作 |
| `completeActions` | `List<Object>` | 否 | 任务完成并取消追踪后执行的服务端动作 |
| `trackerTitle` | `String` | 否 | HUD 任务追踪标题 |
| `marker` | `Object` | 否 | 目标标记定义 |
| `ftbQuestId` | `String` | 否 | 对应 FTB 任务 ID；任务完成时可继续桥接到 FTB |
| `requiredQuestId` | `String` | 否 | 旧版前置字段；当前优先按本模组独立任务完成态判断 |

`marker` 字段结构：

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `label` | `String` | 否 | 标记标签 |
| `dimension` | `String` | 否 | 维度 ID，默认 `minecraft:overworld` |
| `x` | `double` | 是 | 世界坐标 X |
| `y` | `double` | 否 | 世界坐标 Y，默认 `64.0` |
| `z` | `double` | 是 | 世界坐标 Z |

`phases` 中单个阶段结构：

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `phaseId` | `String` | 是 | 阶段唯一 ID |
| `title` | `String` | 否 | 阶段标题，当前主要用于内容可读性 |
| `objectiveLines` | `List<String>` | 否 | 该阶段激活时覆盖任务日志与追踪 HUD 的目标文本 |
| `targetNodeIds` | `List<String>` | 否 | 与该阶段相关的任务节点 ID；用于附近任务实体提示 |
| `nextPhaseId` | `String` | 否 | 当前阶段全部 objectives 完成后自动切换到的下一阶段 ID |
| `completeQuestOnObjectivesCompleted` | `Boolean` | 否 | 当前阶段全部 objectives 完成后是否自动完成任务，默认 `false` |
| `actions` | `List<Object>` | 否 | 进入该阶段后立即执行的服务端动作 |
| `hintType` | `String` | 否 | 命中该阶段目标节点时的提示类型，可选 `advance / complete / available / none` |
| `objectives` | `List<Object>` | 否 | 阶段内的目标列表；存在时优先于阶段本身参与追踪与提示判定 |

`objectives` 中单个目标结构：

| 字段名 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| `objectiveId` | `String` | 是 | 目标唯一 ID |
| `title` | `String` | 否 | 目标标题，当前主要用于内容可读性 |
| `objectiveLines` | `List<String>` | 否 | 当前目标激活时优先显示在任务日志与追踪 HUD |
| `targetNodeIds` | `List<String>` | 否 | 与该目标相关的任务节点 ID；用于附近任务实体提示 |
| `type` | `String` | 否 | 目标推进类型；可选 `manual / interact / talk_to / collect / kill / visit`，默认 `manual` |
| `targetId` | `String` | 否 | 事件型目标的目标 ID，如 `arcadia_intro`、`minecraft:emerald`、`minecraft:zombie` |
| `requiredCount` | `int` | 否 | 事件型目标所需次数或数量，默认 `1` |
| `marker` | `Object` | 否 | 该目标的独立坐标标记；当前主要供 `visit` objective 与追踪 HUD 使用 |
| `structureId` | `String` | 否 | 结构搜索目标 ID；当玩家通过 Explorer's Compass 一类模组搜到这个结构后，会写入动态任务指引坐标 |
| `structureLabel` | `String` | 否 | 结构动态指引显示名称；未填写时回退到 objective 的 `title` |
| `radius` | `double` | 否 | `visit` objective 的判定半径，默认 `4.0` |
| `conditions` | `List<Object>` | 否 | 目标可见条件；不满足时该 objective 不会成为当前追踪目标 |
| `actions` | `List<Object>` | 否 | 该 objective 完成后执行的服务端动作 |
| `hintType` | `String` | 否 | 命中该目标节点时的提示类型，可选 `advance / complete / available / none` |

说明：

- 运行时状态当前按 `可接 / 进行中 / 可交 / 已完成` 计算，不额外写入玩家存档
- `可交` 当前由任务当前 objective 或 phase 的 `hintType = complete` 驱动，适合与实体提示和对话交付入口保持一致
- 附近任务实体感叹号会复用这套运行时状态语义，并映射为 `可接 / 推进 / 可交`
- `manual` 保持现有行为，通常通过 `complete_objective` 或对话动作推进
- `interact` 会在玩家右键实体或使用对话键成功开启 NPC 对话时推进；`targetId` 既可写实体类型 ID，也可直接写任务 NPC 的 `questNodeId`
- `talk_to` 是 `interact` 的同义写法
- `collect` 当前每秒在服务端扫描一次玩家背包；当持有数量达到 `requiredCount` 时自动完成
- `kill` 当前会在玩家或其抛射物击杀实体时推进
- `visit` 当前每秒在服务端检查玩家是否进入目标坐标半径；优先使用 objective 自己的 `marker`，否则回退到任务级 `marker`
- `structure / locate_structure / find_structure` 当前会按 `visit` 语义处理；推荐与 `structureId` 搭配使用
- 当当前 objective 定义了 `structureId`，服务端会每秒扫描玩家背包中的 Explorer's Compass 搜索结果；一旦发现匹配结构，就会把搜索到的坐标固化为该 objective 的运行时 marker
- 事件型 objective 激活时，任务日志与任务追踪 HUD 会自动在第一行后追加进度后缀，例如 `击杀 1 只僵尸 (0/1)`
- 若当前 objective 定义了独立 `marker`，左上角追踪 HUD 会优先显示该目标的方向，而不是任务级总标记
- `flagsToSetOnAccept` 仍保留兼容，等价于一组简单的 `set_flag`
- `requiredQuestId` 负责表达“谁是硬前置”，`unlockConditions` 负责补充更复杂的解锁判定，`nextQuestIds` 负责表达“完成后优先推荐追踪哪些后续任务”
- `nextQuestIds` 不会自动替玩家接取任务；它只会在后续任务当前可接取或已处于进行中时，把它加入追踪候选
- `acceptActions` 适合放“任务接取瞬间”就要发生的副作用；其执行时机早于初始 `phase.actions`
- `completeActions` 适合放“任务正式完成后”的副作用；其执行时机会晚于完成态写入与取消追踪
- `acceptActions / completeActions` 当前已接入安全子集：`set_flag / start_quest / complete_quest / open_shop`
- `completeActions` 不建议对同一个 `questId` 再次使用 `complete_quest`；当前服务端会忽略这种递归配置
- 当前阶段中，服务端会从上到下挑选“第一个未完成且条件满足”的 objective 作为当前目标
- `phases[].actions` 会在玩家进入该阶段时立刻执行，包括任务初次接取、objective 驱动的自动切阶段，以及对话动作 `set_quest_phase`
- 若某个 objective 的 `conditions` 不满足，它不会显示在 HUD/日志当前目标里，也不会参与附近任务实体提示判定
- `conditions` 复用第 `6.6` 节中的条件语法，可直接使用 `quest_objective_completed`
- `phases[].actions` 当前已接入安全子集：`set_flag / start_quest / complete_quest / open_shop`
- `actions` 当前已接入安全子集：`set_flag / start_quest / complete_quest / open_shop`
- 目前不建议在 objective 动作里使用 `open_node / close_dialogue / set_quest_phase`，这些仍更适合留在对话层

事件型 objective 示例：

```json
{
  "objectiveId": "kill_zombie",
  "type": "kill",
  "targetId": "minecraft:zombie",
  "requiredCount": 1,
  "objectiveLines": [
    "击杀 1 只僵尸"
  ]
}
```

`visit` objective 示例：

```json
{
  "objectiveId": "visit_training_ground",
  "type": "visit",
  "radius": 5.0,
  "marker": {
    "label": "训练场",
    "dimension": "minecraft:overworld",
    "x": 0.0,
    "y": 64.0,
    "z": 0.0
  },
  "objectiveLines": [
    "前往训练场坐标点一次"
  ]
}
```

结构搜索 objective 示例：

```json
{
  "objectiveId": "find_arcane_tower",
  "type": "structure",
  "structureId": "minecraft:pillager_outpost",
  "structureLabel": "哨塔方位",
  "radius": 12.0,
  "objectiveLines": [
    "使用结构搜索指南针锁定目标建筑",
    "锁定后按 HUD 指引前往目标结构"
  ]
}
```

### 11.4 打开方式

执行：

```mcfunction
/pbriefing quest journal
```

或执行：

```mcfunction
/pbriefing quest tracker
```

可强制刷新当前 HUD 追踪目标。

当前界面会按玩家状态展示：

- `[可接]` 条目
- `[进行中]` 条目
- `[可交]` 条目
- `[已完成]` 条目

任务状态当前优先使用本模组独立任务状态；若填写 `ftbQuestId`，仍会兼容 FTB 完成态桥接。

若任务定义了 `trackerTitle` 与 `marker`：

- 客户端 HUD 左上角会显示当前追踪任务
- 屏幕上方会显示方向标记与距离

---

## 12. 演出模式说明

当 `presentation = "cinematic"` 时：

- 屏幕上下出现电影黑边
- 底部显示打字机文本
- 玩家输入会被锁定
- 视角与移动会被强制冻结
- 若 `triggerVFX = true`，会播放自定义悬浮粒子特效

推进规则：

- 左键或空格
- 若当前文本未播完，则瞬间补全
- 若当前文本已播完，则进入下一句
- 若所有句子已结束且没有 `options`，则向服务端发送完成回执
- 若所有句子已结束且存在 `options`，则底部显示分支列表，按数字键 `1-9` 选择

---

## 13. 热重载说明

本模组支持通过 Minecraft 原生资源重载机制刷新剧情节点数据。

`/reload` 不仅会重读内置 Datapack，也会重新扫描：

```text
config/phantasmbriefing/briefing_pack/phantasm_dialogues/
```

以及：

```text
config/phantasmbriefing/briefing_pack/phantasm_npc_bindings/
```

以及：

```text
config/phantasmbriefing/briefing_pack/phantasm_shops/
```

以及：

```text
config/phantasmbriefing/briefing_pack/phantasm_quests/
```

以及：

```text
config/phantasmbriefing/briefing_pack/phantasm_wallet/
```

修改 JSON 后执行：

```mcfunction
/reload
```

重载完成后：

- 新增节点会立即生效
- 修改后的文本会立即被使用
- 节点跳转关系会立即更新

---

## 14. 推荐开发流程

### 14.1 编写新剧情

推荐按以下流程操作：

1. 先确定一个 `nodeId`
2. 优先在 `config/phantasmbriefing/briefing_pack/phantasm_dialogues/` 新建对应 JSON
3. 根据需求选择 `briefing` 或 `cinematic`
4. 写入 `lines`
5. 如需跳转，则设置 `nextNodeId`
6. 如需分支选项，则填写 `options`
7. 如需联动 FTB 任务，则填写 `ftbQuestIdToComplete` 或在 `actions` 中使用 `complete_quest`
8. 执行 `/reload`
9. 通过 `/pbriefing setnpc <nodeId>` 绑定到 NPC
10. 进游戏测试交互

### 14.2 推荐内容工作流

推荐将内置资源与外置内容分工如下：

- 内置资源：放默认示例、保底节点、发行时的基础内容
- 外置内容包：放整合包专属剧情、测试节点、临时迭代版本，以及 NPC 绑定规则、商店定义和任务日志定义
- 正式发包前，再决定是否将成熟的外置节点回收进内置资源

### 14.3 推荐节点命名规范

建议使用小写加下划线：

```text
main_village_intro
guildmaster_first_meeting
memory_echo_01
chapter1_briefing
```

---

## 15. 故障排查

### 15.1 按对话键没有反应

请检查：

- 该实体是否已经被标记为任务 NPC
- 是否将准星对准 NPC，且与 NPC 的距离不超过六格、中间没有方块遮挡
- “开始与 NPC 对话”按键是否与其他模组冲突；可在“选项 → 控制 → 按键绑定 → PhantasmBriefing”中修改
- `questNodeId` 是否存在对应 JSON
- JSON 是否在正确目录下
- 是否执行过 `/reload`
- 若使用外置内容包，是否放在 `config/phantasmbriefing/briefing_pack/phantasm_dialogues/`
- 若使用外置 NPC 绑定，`entityType / profession / customName` 是否确实命中目标实体

### 15.2 节点无法跳转

请检查：

- `nextNodeId` 是否拼写正确
- 下一个节点 JSON 是否存在
- 节点 ID 是否重复

### 15.3 FTB 任务没有推进

请检查：

- 运行环境中是否安装了 `ftbquests`
- `ftbQuestIdToComplete` 是否为有效任务 ID
- 对应任务是否已经完成
- FTB 版本是否与整合包环境匹配

### 15.4 前置任务始终无法通过

请检查：

- `requiredQuestId` 是否填写正确
- 玩家是否确实完成了该任务
- 当前服务器的 FTB Teams / FTB Quests 数据是否正常

### 15.5 商店无法打开

请检查：

- `targetId` 是否对应真实存在的 `shopId`
- 商店 JSON 是否位于 `phantasm_shops` 正确目录
- 是否执行过 `/reload`
- 报价中的 `item` 是否拼写正确并存在于注册表中

### 15.6 任务日志为空

请检查：

- `phantasm_quests` 目录下是否存在有效 JSON
- 是否执行过 `/reload`
- 若条目填写了 `requiredQuestId`，该前置是否已满足
- 若条目填写了 `ftbQuestId`，FTB 任务 ID 是否正确

---

## 16. 当前示例节点

当前项目已内置两个示例节点：

- `arcadia_intro`
- `arcadia_memory_echo`

建议测试方式：

```mcfunction
/pbriefing setnpc arcadia_intro
```

然后对准六格内的目标 NPC，按“开始与 NPC 对话”键（默认 `V`），即可体验：

1. 简报面板
2. 自动跳转到演出节点
3. 演出结束后闭环完成

另外，仓库根目录还提供了可复制的外置示例包：

```text
examples/briefing_pack/
```

其中包含：

- 对 `arcadia_intro` 的外置覆盖示例
- 一个“农民村民 + 自定义名称公会长”到 `arcadia_intro` 的 NPC 绑定示例
- 一个 `guild_supply_shop` 的外置商店示例
- 一个 `guild_supply_token_shop` 的外置钱包商店示例
- 一个 `guild_supply` 的外置任务日志示例
- 一个 `guild_supply_token` 的外置钱包货币示例

---

## 17. 后续可扩展方向

当前架构已经适合继续扩展以下内容：

- 多分支剧情树
- UI 坐标与主题皮肤进一步数据驱动
- 更复杂的镜头朝向与 VFX 曲线控制
- 与其他任务系统或剧情系统继续桥接

---

## 17. 维护建议

为了保证整套系统长期稳固、耐用，建议遵循以下原则：

- 只在 JSON 中维护剧情数据，不在业务代码里硬编码文本
- 所有任务推进尽量走 API，不走后台指令
- 节点 ID 命名保持统一规范
- 每次修改 Datapack 后都执行一次 `/reload` 验证
- 在整合包发布前，检查所有 `nextNodeId` 与 FTB 任务 ID 是否有效

如需继续扩展，我建议下一步新增：

- `opennode` 调试命令
- 默认拒绝对话节点
- 更完整的剧情分支条件系统
