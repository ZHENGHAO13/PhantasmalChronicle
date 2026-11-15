# PhantasmBriefing 命名空间迁移说明

## 1. 目的

本说明用于指导现有内容包作者，将旧条件命名空间：

```text
chronicle_engine:*
```

迁移到当前默认命名空间：

```text
phantasmbriefing:*
```

## 2. 当前兼容策略

当前版本的解析逻辑如下：

- 新写法：`phantasmbriefing:*`
- 旧写法：`chronicle_engine:*`
- 运行时行为：两者都会被解析，但文档、示例与新内容应统一使用 `phantasmbriefing:*`

这意味着：

- 旧内容包目前不会立刻失效
- 新内容不建议继续写旧命名空间

## 3. 迁移映射

以下条件类型可直接一一替换：

| 旧写法 | 新写法 |
|---|---|
| `chronicle_engine:always` | `phantasmbriefing:always` |
| `chronicle_engine:has_flag` | `phantasmbriefing:has_flag` |
| `chronicle_engine:quest_accepted` | `phantasmbriefing:quest_accepted` |
| `chronicle_engine:quest_available` | `phantasmbriefing:quest_available` |
| `chronicle_engine:quest_ready_to_turn_in` | `phantasmbriefing:quest_ready_to_turn_in` |
| `chronicle_engine:quest_completed` | `phantasmbriefing:quest_completed` |
| `chronicle_engine:quest_not_started` | `phantasmbriefing:quest_not_started` |
| `chronicle_engine:quest_phase` | `phantasmbriefing:quest_phase` |
| `chronicle_engine:quest_objective_completed` | `phantasmbriefing:quest_objective_completed` |
| `chronicle_engine:and` | `phantasmbriefing:and` |
| `chronicle_engine:or` | `phantasmbriefing:or` |
| `chronicle_engine:not` | `phantasmbriefing:not` |
| `chronicle_engine:entity_name` | `phantasmbriefing:entity_name` |
| `chronicle_engine:villager_profession` | `phantasmbriefing:villager_profession` |

## 4. 迁移示例

### 4.1 简单条件

旧写法：

```json
{
  "condition": "chronicle_engine:has_flag",
  "flag": "guild_access"
}
```

新写法：

```json
{
  "condition": "phantasmbriefing:has_flag",
  "flag": "guild_access"
}
```

### 4.2 复合条件

旧写法：

```json
{
  "condition": "chronicle_engine:and",
  "conditions": [
    {
      "condition": "chronicle_engine:quest_completed",
      "questId": "arcadia_demo_quest"
    },
    {
      "condition": "chronicle_engine:has_flag",
      "flag": "guild_access"
    }
  ]
}
```

新写法：

```json
{
  "condition": "phantasmbriefing:and",
  "conditions": [
    {
      "condition": "phantasmbriefing:quest_completed",
      "questId": "arcadia_demo_quest"
    },
    {
      "condition": "phantasmbriefing:has_flag",
      "flag": "guild_access"
    }
  ]
}
```

## 5. 推荐迁移范围

建议优先检查以下目录：

- `phantasm_dialogues`
- `phantasm_quests`
- `phantasm_shops`
- `phantasm_trades`
- 外置 `briefing_pack`

尤其要重点检查：

- `conditions`
- `openCondition`
- `visibleCondition`
- 对话选项中的复合条件
- 任务 objective / phase / unlock 条件

## 6. 迁移建议流程

建议按以下顺序操作：

1. 先批量将 `chronicle_engine:` 替换为 `phantasmbriefing:`
2. 运行 `/pbriefing reload`
3. 检查日志是否存在解析错误
4. 重点测试带条件的对话、任务和商店
5. 再提交内容包修改

## 7. 兼容移除建议

当前项目仍保留旧命名空间兼容，但建议后续版本按以下节奏收束：

1. 当前版本：继续兼容旧写法
2. 下一个内容迁移阶段：文档明确标注旧写法为“弃用”
3. 未来正式版本：视内容包迁移完成度决定是否移除兼容

## 8. 注意事项

- 本次迁移只改变条件命名空间，不改变字段结构
- `questId / phaseId / objectiveId / flag` 等字段写法不需要修改
- 若你的内容包此前混用了旧写法与新写法，建议统一为 `phantasmbriefing:*`
