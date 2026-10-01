# 外置内容包示例

该目录演示 `PhantasmBriefing` 的外置内容包结构。

把本目录中的子文件夹复制到 Minecraft 实例目录下的：

```text
config/phantasmbriefing/briefing_pack/
```

然后在游戏内执行：

```mcfunction
/reload
```

## 目录说明

- `phantasm_dialogues/`
  - 外置剧情节点
  - 可覆盖模组内置节点，也可新增全新节点
- `phantasm_npc_bindings/`
  - 外置 NPC 绑定规则
  - 可直接把某类实体绑定到指定 `questNodeId`
  - 可用 `questId` 关联本模组任务，并用 `prerequisiteLockedText` 自定义前置未完成时的 NPC 提示
  - 结构生成默认通过 `snapToSurface` 寻找安全地表；需要生成在地下结构内部时可关闭
- `phantasm_shops/`
  - 外置商店定义
  - 可被 `open_shop` 动作直接打开
- `phantasm_trades/`
  - 外置独立交易定义
  - 可被 `open_shop` 或 `open_trade` 动作按相同 ID 打开
- `phantasm_quests/`
  - 外置任务日志定义
  - 可被任务日志界面读取

## 当前示例

- `phantasm_dialogues/arcadia_intro.json`
  - 覆盖内置 `arcadia_intro`
- `phantasm_npc_bindings/arcadia_guildmaster_farmer.json`
  - 将“自定义名称为 `公会长` 的农民村民”绑定到 `arcadia_intro`
- `phantasm_shops/guild_supply_shop.json`
  - 外置商店示例，可被 `open_shop` 动作打开
- `phantasm_trades/guild_supply_trade.json`
  - 外置独立交易示例，可直接复用原版交易界面
- `phantasm_quests/guild_supply.json`
  - 外置任务日志条目示例

## 推荐测试步骤

1. 复制本示例到 `config/phantasmbriefing/briefing_pack/`
2. 给一个农民村民命名为 `公会长`
3. 执行 `/reload`
4. 对准该村民，按默认对话键 `V`（可在 Minecraft 控制设置中修改）
5. 观察是否直接触发 `arcadia_intro`
