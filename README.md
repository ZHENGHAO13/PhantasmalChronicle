# PhantasmalChronicle

**用任务、对话与 NPC 串起你的 Minecraft 冒险。**

**Build connected Minecraft adventures with quests, dialogue, and NPCs.**

[简体中文](#简体中文) · [English](#english)

Minecraft **1.20.1** · Forge **47.4.10** · Java **17** · **Alpha**

## 简体中文

PhantasmalChronicle 是一个面向 Minecraft 整合包与剧情内容创作者的项目，包含 **PhantasmBriefing 模组**和 **Phantasm Maker 网页编辑器**。通过外置 JSON 内容包，把任务进度、NPC 对话、探索目标、旅者手册和商店交易连接起来，让剧情内容可以独立于模组代码迭代。

仓库名称为 `PhantasmalChronicle`，游戏内模组名称为 `PhantasmBriefing`，模组 ID 为 `phantasmbriefing`。

> **开发状态：** 当前配置版本为 `0.1.0-alpha.1`。本仓库是开发中的源码快照；当前提交存在已知构建阻塞，尚不能直接构建为可用发行版。详见[开发与已知限制](#开发与已知限制)。

### 功能概览

- **任务与进度：** 多阶段任务、前置条件、顺序或自由推进、任务日志、任务树和 HUD 追踪；目标类型涵盖击杀、收集、制作、交互、到达地点、FTB 任务及手册阅读。
- **NPC 与对话：** 分支选项、逐字显示、世界内对话气泡；通过对话接取任务、推进目标、设置旗标、发放物品或打开商店。可将现有实体绑定为任务 NPC，并配置结构内生成规则。
- **旅者手册：** 以章节与页面组织说明和剧情内容，关联任务解锁与阅读目标。
- **钱包与交易：** 配置物品货币、钱包商店和独立交易。默认余额来自玩家持有的对应物品；另有 Lightman's Currency 兼容实现。
- **外置内容与编辑器：** 在网页中编写任务、对话、NPC 绑定、钱包、商店和交易，校验后写入内容包；本机运行游戏时可触发热加载。

以上描述对应源码中的功能模块；当前构建和运行验证状态见下文。

### 环境与可选联动

| 项目 | 当前开发配置 |
| --- | --- |
| Minecraft | Java Edition 1.20.1 |
| 模组加载器 | Forge 47.4.10 |
| Java | JDK 17 |
| Gradle | 使用仓库自带的 Wrapper，版本 8.8 |
| 编辑器 | 本地 HTML 页面；目录读写建议使用支持该功能的 Chrome / Edge |

源码包含以下可选联动；启用相应功能时，需要另行安装兼容版本及其依赖：

| 模组 | 联动内容 |
| --- | --- |
| FTB Quests | 外部任务前置、完成状态与任务完成动作 |
| Explorer's Compass | 结构定位结果与探索目标衔接 |
| Lightman's Currency | 钱币余额读取与支付 |

这些第三方模组的 JAR 不随本仓库分发。当前构建配置中的 FTB / Architectury 依赖为 `compileOnly`，不会自动打包进本模组。

### 安装与内容入门

取得**通过构建和运行验证的模组 JAR** 后：

1. 准备 Minecraft 1.20.1 + Forge 实例，将 JAR 放入实例的 `mods/`。多人游戏的客户端和服务器均应安装相同版本。
2. 在实例中创建 `config/phantasmbriefing/briefing_pack/`，放入自己编写的内容；也可参考 [examples/briefing_pack](examples/briefing_pack)。示例用于了解格式，使用前需核对任务与对话之间的引用。
3. 进入世界后，由管理员执行 `/pbriefing reload` 或 `/reload` 加载内容。
4. 对准已绑定的 NPC，按 **V** 开始对话；按 **J** 打开任务日志。按键可在 Minecraft 的控制设置中修改。

内容目录示意（按需创建）：

```text
config/phantasmbriefing/briefing_pack/
├── phantasm_quests/         # 任务与阶段目标
├── phantasm_dialogues/      # 对话与选项
├── phantasm_npc_bindings/   # 实体绑定与生成规则
├── phantasm_manuals/        # 旅者手册
├── phantasm_wallet/         # 钱包与货币定义
├── phantasm_shops/          # 商店与报价
├── phantasm_trades/         # 独立交易
└── dialogue_voices/         # 对话音效元数据
```

也可通过数据包的 `data/<namespace>/<目录名>/` 提供 JSON。外置内容按对应定义的 ID 覆盖已加载条目。对话音效元数据与音频文件分开管理，实际声音资源需由客户端资源包提供。

### 使用 Phantasm Maker

1. 下载或克隆本仓库，用浏览器打开 [tools/phantasm_maker/index.html](tools/phantasm_maker/index.html) 对应的**本地文件**。它是单文件编辑器，无需安装 Node.js 或启动网页服务器。
2. 点击“选择整合包”，选择包含 `config/` 与 `mods/` 的 Minecraft 实例根目录，并允许浏览器访问该目录。
3. 从“新建任务 + 对话 + NPC”开始，或导入已有内容。新手模式会隐藏部分高级字段。
4. 检查数据与引用，然后写入 `briefing_pack`。本机游戏服务器已启动时，可使用编辑器的热加载功能。

热加载桥接仅监听本机 `127.0.0.1:38471`。编辑远程服务器内容时，需要将内容包同步到服务器，再由管理员重载；本地编辑器不会自动连接远程服务器。

详细操作见 [Phantasm Maker 说明](tools/phantasm_maker/README.md)。`可视化网页编辑器/` 中还保留了另一份编辑器页面与创作资料；不同页面的功能覆盖有差异，请核对导出格式与当前运行时代码。

### 常用管理命令

以下 `/pbriefing` 命令需要权限等级 2。`<...>` 是需要替换的参数；`@s` 表示执行命令的玩家。

| 命令 | 用途 |
| --- | --- |
| `/pbriefing reload` | 重载内容 |
| `/pbriefing dialogue start @s <dialogueId>` | 启动指定对话 |
| `/pbriefing quest give @s <questId>` | 发放任务 |
| `/pbriefing quest journal` | 打开任务日志 |
| `/pbriefing quest tree` | 打开任务树 |
| `/pbriefing npc spawn <bindingId>` | 按绑定定义生成 NPC |
| `/pbriefing wallet` | 打开钱包总览 |
| `/pbriefing shop open <shopId>` | 打开指定商店 |

完整命令以游戏内补全及 [PBriefingCommands.java](src/main/java/com/phantasm/briefing/command/PBriefingCommands.java) 为准。

### 开发与已知限制

将 `JAVA_HOME` 指向 JDK 17，在仓库根目录运行：

```powershell
# Windows PowerShell
.\gradlew.bat build
.\gradlew.bat runClient
```

```sh
# macOS / Linux
sh ./gradlew build
sh ./gradlew runClient
```

首次构建需要联网下载 Gradle、Forge 和依赖。成功构建后的 JAR 位于 `build/libs/`。`releaseMod` 任务用于清理后重建，并包含对话音效打包校验。

**当前源码快照的限制：**

- 在首次上传版本 `bc8409c` 上执行 `build` 时，Java 编译报告 6 处错误，涉及旧教程界面对 `TUTORIAL`、`tutorialPages()`、`tutorialAccessible()` 及已缺失运行时方法的引用。需要完成教程与手册代码的衔接后，才能验证后续构建步骤。
- `build.gradle` 的音效打包校验要求 `sounds.json`、`dialogue_text_tick.ogg` 和 `dialogue_villager.ogg`，但当前受版本控制的资源中尚未包含这些文件。打包规则与资源交付方式仍需统一。
- 编辑器、示例和运行时正在迭代，部分旧字段或示例引用可能不一致。新增内容应以当前 Java 数据解析代码为准。
- 网页编辑器基础检查 `node tools/phantasm_maker/editor_smoke_test.js` 在首次上传前通过；这不代表模组完整构建或游戏内验证通过。执行该检查需要 Node.js。

提交问题时，请附上模组版本或提交号、复现步骤、相关日志，以及最小可复现的内容包。可在 [Issues](https://github.com/ZHENGHAO13/PhantasmalChronicle/issues) 中反馈。

### 目录与许可

| 路径 | 内容 |
| --- | --- |
| `src/main/java/` | 模组源码 |
| `src/main/resources/` | 模组元数据、语言文件与资源 |
| `src/test/`、`tests/` | Java 与编辑器检查代码 |
| `examples/briefing_pack/` | 外置内容示例 |
| `tools/phantasm_maker/` | 网页编辑器、说明与基础检查 |
| `tools/verify_*.py` | 功能回归检查脚本，部分对应历史实现 |
| `gradle/`、`gradlew*` | Gradle Wrapper |

构建缓存、游戏运行目录、临时依赖和重复备份已通过 `.gitignore` 排除。

项目当前在 `gradle.properties` 中声明 **All Rights Reserved**。第三方代码与素材按各自声明保留许可信息，详见 [Third-party notices](src/main/resources/META-INF/PHANTASM_THIRD_PARTY_NOTICES.md)。

---

## English

PhantasmalChronicle is a project for Minecraft modpack and story creators. It includes the **PhantasmBriefing mod** and the **Phantasm Maker browser editor**, connecting quest progression, NPC dialogue, exploration objectives, traveler manuals, and trading through external JSON content packs. Story content can be developed independently of the mod's Java code.

The repository is named `PhantasmalChronicle`; the in-game mod name is `PhantasmBriefing`, and its mod ID is `phantasmbriefing`.

> **Development status:** The configured version is `0.1.0-alpha.1`. This repository contains a development snapshot with known build blockers and cannot currently be built directly into a usable release. See [Development and known limitations](#development-and-known-limitations).

### Features

- **Quests and progression:** Multi-phase quests, prerequisites, sequential or free progression, a quest journal, a quest tree, and HUD tracking. Objective types cover kills, collection, crafting, interaction, location visits, FTB quests, and manual reading.
- **NPCs and dialogue:** Branching choices, typewriter text, and in-world dialogue bubbles. Dialogue actions can start quests, advance objectives, set flags, grant items, or open shops. Existing entities can be bound to quest dialogue, with configurable structure-based spawning.
- **Traveler manuals:** Organize instructions and story content into sections and pages, with quest-linked unlocks and reading objectives.
- **Wallets and trading:** Define item currencies, wallet shops, and standalone trades. By default, balances come from matching items held by the player; the source also includes Lightman's Currency integration.
- **External content and editing:** Create quests, dialogue, NPC bindings, wallets, shops, and trades in a browser, validate them, and write a content pack. A locally running game can receive a reload request from the editor.

These descriptions reflect modules present in the source. Build and runtime validation limits are listed below.

### Requirements and optional integrations

| Component | Current development configuration |
| --- | --- |
| Minecraft | Java Edition 1.20.1 |
| Mod loader | Forge 47.4.10 |
| Java | JDK 17 |
| Gradle | Bundled Wrapper, version 8.8 |
| Editor | Local HTML file; use Chrome / Edge with directory-access support for direct file writing |

The source includes these optional integrations. Install compatible versions and their dependencies separately when using the corresponding features:

| Mod | Integration |
| --- | --- |
| FTB Quests | External prerequisites, completion state, and quest-completion actions |
| Explorer's Compass | Structure-location results for exploration objectives |
| Lightman's Currency | Coin balance lookup and payment |

Third-party mod JARs are not distributed with this repository. The FTB / Architectury dependencies in the build configuration are `compileOnly` and are not bundled into the mod.

### Installation and first content pack

Once you have a mod JAR that has **passed build and runtime validation**:

1. Prepare a Minecraft 1.20.1 + Forge instance and place the JAR in `mods/`. Multiplayer clients and the server should use the same mod version.
2. Create `config/phantasmbriefing/briefing_pack/` inside the instance and add your content. Use [examples/briefing_pack](examples/briefing_pack) as a format reference, checking quest and dialogue references before use.
3. Enter a world and have an administrator run `/pbriefing reload` or `/reload` to load the content.
4. Look at a bound NPC and press **V** to start dialogue. Press **J** to open the quest journal. Both keys can be changed in Minecraft's control settings.

Create the content directories you need:

```text
config/phantasmbriefing/briefing_pack/
├── phantasm_quests/         # Quests, phases, and objectives
├── phantasm_dialogues/      # Dialogue and choices
├── phantasm_npc_bindings/   # Entity bindings and spawn rules
├── phantasm_manuals/        # Traveler manuals
├── phantasm_wallet/         # Wallet and currency definitions
├── phantasm_shops/          # Shops and offers
├── phantasm_trades/         # Standalone trades
└── dialogue_voices/         # Dialogue sound metadata
```

JSON can also be supplied through a datapack under `data/<namespace>/<directory>/`. External definitions override loaded entries by their respective IDs. Dialogue sound metadata is separate from audio files; actual sound resources must be provided through client resource packs.

### Using Phantasm Maker

1. Download or clone the repository and open the **local copy** of [tools/phantasm_maker/index.html](tools/phantasm_maker/index.html) in your browser. The editor is a single HTML file; using it requires neither Node.js nor a web server.
2. Choose your Minecraft instance directory containing `config/` and `mods/`, and grant the browser access to that directory.
3. Start with the quest + dialogue + NPC creation flow, or import existing content. Beginner mode hides some advanced fields.
4. Validate the data and references, then write the content to `briefing_pack`. Use the reload feature when a game server is running on the same machine.

The reload bridge listens only on `127.0.0.1:38471`. For a remote server, transfer the content pack to that server and have an administrator reload it; the local editor does not automatically connect to remote servers.

See the [Phantasm Maker guide](tools/phantasm_maker/README.md) for detailed instructions, currently in Chinese. `可视化网页编辑器/` also contains another editor page and authoring notes. Feature coverage differs between pages, so check exported data against the current runtime.

### Common administrative commands

The following `/pbriefing` commands require permission level 2. Replace `<...>` with the appropriate ID; `@s` targets the player running the command.

| Command | Purpose |
| --- | --- |
| `/pbriefing reload` | Reload content |
| `/pbriefing dialogue start @s <dialogueId>` | Start a dialogue |
| `/pbriefing quest give @s <questId>` | Assign a quest |
| `/pbriefing quest journal` | Open the quest journal |
| `/pbriefing quest tree` | Open the quest tree |
| `/pbriefing npc spawn <bindingId>` | Spawn an NPC from a binding |
| `/pbriefing wallet` | Open the wallet overview |
| `/pbriefing shop open <shopId>` | Open a shop |

Use in-game completion or [PBriefingCommands.java](src/main/java/com/phantasm/briefing/command/PBriefingCommands.java) for the complete command tree.

### Development and known limitations

Set `JAVA_HOME` to a JDK 17 installation and run these commands from the repository root:

```powershell
# Windows PowerShell
.\gradlew.bat build
.\gradlew.bat runClient
```

```sh
# macOS / Linux
sh ./gradlew build
sh ./gradlew runClient
```

The first build requires network access to download Gradle, Forge, and dependencies. Successful builds produce JARs in `build/libs/`. The `releaseMod` task performs a clean rebuild and includes dialogue-sound packaging checks.

**Limitations of the current source snapshot:**

- Running `build` on the initial upload, `bc8409c`, reported six Java compilation errors. Older tutorial screens and packets still reference `TUTORIAL`, `tutorialPages()`, `tutorialAccessible()`, and missing runtime methods. The tutorial-to-manual transition needs to be completed before later build stages can be verified.
- The sound packaging checks in `build.gradle` require `sounds.json`, `dialogue_text_tick.ogg`, and `dialogue_villager.ogg`, which are absent from the currently tracked resources. Packaging requirements and resource delivery still need to be reconciled.
- The editor, examples, and runtime are evolving; some older fields or example references may be inconsistent. Use the current Java data parsers as the source of truth for new content.
- The editor smoke check, `node tools/phantasm_maker/editor_smoke_test.js`, passed before the initial upload. This does not establish a successful full mod build or in-game validation. Node.js is required to run this check.

When reporting a problem, include the mod version or commit, reproduction steps, relevant logs, and a minimal content pack. Report problems through [Issues](https://github.com/ZHENGHAO13/PhantasmalChronicle/issues).

### Repository layout and licensing

| Path | Contents |
| --- | --- |
| `src/main/java/` | Mod source code |
| `src/main/resources/` | Mod metadata, translations, and assets |
| `src/test/`, `tests/` | Java and editor checks |
| `examples/briefing_pack/` | Example external content |
| `tools/phantasm_maker/` | Browser editor, guide, and smoke check |
| `tools/verify_*.py` | Regression checks, some targeting historical implementations |
| `gradle/`, `gradlew*` | Gradle Wrapper |

Build caches, game runtime directories, temporary dependencies, and duplicate backups are excluded through `.gitignore`.

The project currently declares **All Rights Reserved** in `gradle.properties`. Third-party code and assets retain their individual license notices; see [Third-party notices](src/main/resources/META-INF/PHANTASM_THIRD_PARTY_NOTICES.md).
