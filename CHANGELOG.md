# Changelog / 版本变更

## alpha-0.1.0+build.1 — 2026-10-07

### 中文

- 重新整理完整源码交付，保留模组功能、资源、外置内容示例、Gradle Wrapper 和许可证；移除共享范围中的自用检查与内部文档。
- 清理没有调用入口的旧教程界面及网络包，保留任务日志中的旅者手册和阅读目标实现，修复旧源码的编译阻塞。
- 纳入本地已有的任务追踪显示、物品交付动作、NPC 生成落点与客户端网络处理更新。
- 网络协议更新至 `15`，与已有任务追踪和日志数据结构变化匹配。升级时客户端和服务器需同时更新。
- 独立源码副本的编译与打包验证不依赖内部辅助工具；尚未完成全面游戏内与联机验收。

### English

- Reorganized the complete source distribution, retaining mod functionality, resources, external content examples, the Gradle Wrapper, and licenses. Internal checks and collaboration documents are excluded.
- Removed unreachable legacy tutorial screens and packets while retaining the journal's traveler manual and reading objectives, resolving the old compilation blockers.
- Included existing local updates to quest tracking, item-delivery actions, NPC spawn placement, and client-side packet handling.
- Bumped the network protocol to `15` to match the changed quest-tracking and journal payloads. Update clients and servers together.
- Compilation and packaging of the standalone source copy do not depend on internal helper tools. Comprehensive gameplay and multiplayer validation remains pending.
