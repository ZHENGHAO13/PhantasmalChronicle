# Phantasm Maker

[简体中文](#简体中文) · [English](#english)

## 简体中文

Phantasm Maker 是 PhantasmBriefing 的本地网页内容编辑器，提供中英界面。HTML、样式与脚本包含在单个 [index.html](index.html) 中，无需安装网页开发依赖。

### 使用

1. 下载并解压仓库，用支持 `showDirectoryPicker` 的浏览器打开本地 `tools/phantasm_maker/index.html`。可使用 Chrome 或 Edge；目录访问能力取决于浏览器与本机策略。
2. 点击“选择项目”，选择同时包含 `config` 和 `mods` 的整合包根目录，并允许访问目录。
3. 编辑任务、旅者手册、对话、NPC、钱包、商店或物品交易。任务编辑包含讨伐生物分类；手册可组织分组、教程与图文页面，并关联阅读目标。
4. 进入“检查与写入”，处理校验错误，核对生成的 JSON，然后点击“写入并热加载”。

内容写入 `config/phantasmbriefing/briefing_pack/` 下对应的 `phantasm_quests`、`phantasm_manuals`、`phantasm_dialogues`、`phantasm_npc_bindings`、`phantasm_wallet`、`phantasm_shops` 和 `phantasm_trades` 目录。开始修改已有内容前，请备份内容包。

写入后，编辑器会尝试通过本机 `127.0.0.1:38471` 的模组接口重载游戏内容。游戏需已加载 PhantasmBriefing 并进入世界；游戏未运行或请求失败时，以页面提示判断结果，也可在游戏中执行 `/pbriefing reload`。编辑远程服务器内容时，需要自行同步内容包并由管理员重载。

页面也可通过本机接口读取游戏资源候选项。除这些本机请求外，页面不依赖外部 CDN。GitHub 文件预览不会运行 HTML，请下载后在本地打开。若目录选择不可用，请使用支持目录访问的浏览器，或参考[项目示例](../../examples/briefing_pack)手动编辑 JSON。

### 验证范围

本次交付检查脚本语法与内容导出；实际浏览器目录授权、文件写入及游戏热加载仍需在使用环境中验收。模组版本及构建说明见[项目首页](../../README.md)。

## English

Phantasm Maker is the local web content editor for PhantasmBriefing, with Chinese and English interfaces. Its HTML, styles, and scripts are bundled in one [index.html](index.html); no web development dependencies are required.

### Usage

1. Download and extract the repository. Open the local `tools/phantasm_maker/index.html` in a browser supporting `showDirectoryPicker`, such as Chrome or Edge. Directory access depends on your browser and local policies.
2. Click **Select Project**, choose the modpack root containing both `config` and `mods`, and grant directory access.
3. Edit quests, traveler manuals, dialogues, NPCs, wallets, shops, or item trades. Quest editing includes kill-target categories; manuals organize groups, lessons, and illustrated pages linked to reading objectives.
4. Open the validation and write view, resolve validation errors, review the generated JSON, and use the write and hot-reload action.

Content is written to the corresponding `phantasm_quests`, `phantasm_manuals`, `phantasm_dialogues`, `phantasm_npc_bindings`, `phantasm_wallet`, `phantasm_shops`, and `phantasm_trades` directories under `config/phantasmbriefing/briefing_pack/`. Back up existing content before editing it.

After writing, the editor attempts to reload game content through the mod's local interface at `127.0.0.1:38471`. The game must have PhantasmBriefing loaded and a world open. If the game is unavailable or the request fails, check the editor's result message; you can also run `/pbriefing reload` in game. For a remote server, synchronize the content pack yourself and ask a server administrator to reload it.

The page can also read game resource suggestions through the local interface. It requires no external CDN. GitHub's file preview does not execute HTML; download the page and open it locally. If directory selection is unavailable, use a browser supporting directory access or edit JSON manually using the [project examples](../../examples/briefing_pack).

### Validation scope

This distribution checks script syntax and content export. Browser directory permissions, actual file writes, and in-game hot reload still require validation in your environment. See the [project README](../../README.md) for the mod version and build instructions.

## License

GPL-3.0-only. See the repository [LICENSE](../../LICENSE).
