# PhantasmBriefing

Minecraft 1.20.1 / Forge 47.4.10 的数据驱动剧情任务框架。

此分支保存本地较早的源码快照，版本为 `alpha-0.1.0+build.2`。它用于独立保留本地内容，**不代表 main 的升级版本**；main 中的旅者手册、阅读目标等后续功能不包含在此快照中。请勿将此分支直接合并到 main 或用于替换已有主线存档。

## 功能

- 分支对话、任务前置、多阶段任务、击杀／收集／交互／到达目标。
- 任务日志、任务树、追踪 HUD 和附近 NPC 提示。
- NPC 绑定、结构生成、默认 V 键对话及村民原生交易兼容。
- 钱包、商店、交易，以及可选 FTB Quests 和 Lightman's Currency 联动。
- JSON 外置内容包与 `/pbriefing reload` 内容重载。

## 构建

安装 JDK 17，将 `JAVA_HOME` 指向其安装目录。首次构建需要联网下载 Gradle、Forge 和 Maven 依赖。

Windows：

```powershell
.\gradlew.bat clean build
```

Linux / macOS：

```sh
sh ./gradlew clean build
```

产物：`build/libs/phantasmbriefing-alpha-0.1.0+build.2.jar`。构建只需要本仓库源码与声明的依赖。

## 安装与内容配置

1. 安装 Minecraft 1.20.1 与 Forge 47.4.10，把构建出的 JAR 放入 `mods`。
2. 联机时客户端与服务端同时安装本分支的相同版本；此快照使用网络协议 16。
3. 将 `examples/briefing_pack` 中的内容复制到游戏目录的 `config/phantasmbriefing/briefing_pack`，按需编辑 JSON。
4. 管理员执行 `/pbriefing reload`，再使用 `/pbriefing setnpc <nodeId>` 绑定 NPC，或 `/pbriefing npc spawn <bindingId>` 生成 NPC。
5. 对准 NPC 按 V 对话；职业村民右键仍用于交易。

详见 [JSON 与功能说明](USAGE.md)、[内容示例](examples/briefing_pack/README.md)和[命名空间迁移说明](MIGRATION_GUIDE.md)。

源码采用 [GPL-3.0-only](LICENSE)。第三方代码与资源的许可见 [Third-party notices](src/main/resources/META-INF/PHANTASM_THIRD_PARTY_NOTICES.md)。

## English

PhantasmBriefing is a data-driven quest and dialogue framework for Minecraft 1.20.1 / Forge 47.4.10. This branch preserves an older local snapshot; it is not an upgrade to main and does not include later main-branch features such as traveler manuals and reading objectives.

Build with JDK 17 using `gradlew.bat clean build` on Windows or `sh ./gradlew clean build` on Linux/macOS. The first build downloads dependencies. Install the output JAR on both client and server with matching versions. Copy the example content pack to `config/phantasmbriefing/briefing_pack`, edit its JSON files, and run `/pbriefing reload` as an administrator. Press V while looking at a bound NPC to start a dialogue.
