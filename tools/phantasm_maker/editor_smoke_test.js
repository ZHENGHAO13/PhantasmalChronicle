const fs = require("fs");
const path = require("path");
const vm = require("vm");

const editorPath = path.join(__dirname, "index.html");
const html = fs.readFileSync(editorPath, "utf8");
const match = html.match(/<script>([\s\S]*?)<\/script>/);

if (!match) {
  throw new Error("Editor script was not found in index.html");
}

const initSection = match[1].slice(match[1].indexOf("function init()"), match[1].indexOf("function wireStaticEvents()"));
if (initSection.includes("loadExample")) {
  throw new Error("Editor startup must not silently load demo content");
}
if (!html.includes("body.beginner-mode") || !html.includes("beginnerModeToggle")) {
  throw new Error("Beginner mode controls are missing");
}
if (!html.includes("127.0.0.1:38471/phantasmbriefing/reload") || !html.includes("requestGameHotReload")) {
  throw new Error("Editor hot reload bridge is missing");
}

const staticMarkup = html.slice(0, html.indexOf("<script>"));
const staticIds = [...staticMarkup.matchAll(/\sid="([^"]+)"/g)].map((entry) => entry[1]);
const duplicateIds = staticIds.filter((id, index) => staticIds.indexOf(id) !== index);
if (duplicateIds.length) {
  throw new Error("Duplicate static element IDs: " + [...new Set(duplicateIds)].join(", "));
}
const referencedIds = [...match[1].matchAll(/\$\("#([^"]+)"\)/g)].map((entry) => entry[1]);
const missingIds = [...new Set(referencedIds.filter((id) => !html.includes(`id="${id}"`)))];
if (missingIds.length) {
  throw new Error("Script references missing static element IDs: " + missingIds.join(", "));
}

const assertions = String.raw`
  markDirty = () => { state.ui.dirty = true; };
  renderAll = () => {};
  showMessages = (messages) => { globalThis.repairMessages = messages; };

  state.ui.language = "en";
  if (tl("一键修复常见格式") !== "Repair Common Formatting"
      || tl("自动贴合安全地表") !== "Snap to Safe Surface"
      || tl("外部 FTB 前置任务") !== "External FTB Required Quest") {
    throw new Error("English editor guidance translations are incomplete");
  }
  state.ui.language = "zh";

  loadExample();
  const exampleErrors = validateData().filter((message) => message.startsWith("错误"));
  if (exampleErrors.length) {
    throw new Error("Bundled example is invalid: " + exampleErrors.join(" | "));
  }
  const exampleFiles = buildFiles();
  const exampleFileCount = exampleFiles.length;
  const exampleNpcFile = exampleFiles.find(([filePath]) => filePath.startsWith("phantasm_npc_bindings/"));
  const exampleNpcJson = exampleNpcFile ? JSON.parse(exampleNpcFile[1]) : null;
  if (!exampleNpcJson
      || exampleNpcJson.questId !== "phantasmbriefing:arcadia_intro_quest"
      || !exampleNpcJson.prerequisiteLockedText) {
    throw new Error("NPC prerequisite quest and locked text were not exported");
  }
  const defaultSpawnRule = makeNpcSpawnRule({ bindingId: "phantasmbriefing:test_npc" }, 0);
  if (defaultSpawnRule.snapToSurface !== true) {
    throw new Error("New NPC structure rules must snap to a safe surface by default");
  }
  const importedLegacyNpc = parseNpc({
    bindingId: "phantasmbriefing:legacy_npc",
    entityType: "minecraft:villager",
    questNodeId: "phantasmbriefing:arcadia_intro_dialogue::root",
    spawns: [{ structureId: "minecraft:village_plains", offset: [0, 1, 0] }]
  });
  if (importedLegacyNpc.spawns[0].snapToSurface !== true) {
    throw new Error("Legacy NPC structure rules did not inherit safe surface placement");
  }

  state.settings.namespace = "my_story";
  state.data = {
    quests: [],
    dialogues: [],
    npcs: [],
    wallets: [{
      walletId: "Wallet Name",
      title: "Test Wallet",
      currencyName: "Coin",
      iconItemId: "EMERALD",
      itemIds: ["Diamond"],
      sortOrder: 0
    }],
    shops: [],
    trades: []
  };

  const walletShop = makeShop("item shop");
  if (walletShop.walletId !== "Wallet Name"
      || walletShop.offers[0].costA !== null
      || walletShop.offers[0].walletCost !== 1) {
    throw new Error("New wallet shop does not start with valid wallet pricing");
  }

  state.data.shops = [{
    shopId: "Shop Name",
    title: "Test Shop",
    walletId: "Wallet Name",
    openCondition: {},
    categories: [],
    offers: [{
      offerId: "",
      title: "Offer",
      categoryId: "",
      requiredQuestId: "",
      costA: { itemId: "EMERALD", count: 0 },
      costB: null,
      result: null,
      walletCost: 0
    }]
  }];
  state.data.trades = [{
    tradeId: "Trade Name",
    title: "Test Trade",
    categories: [],
    entries: [{
      entryId: "",
      title: "Entry",
      categoryId: "",
      requiredQuestId: "",
      costs: [],
      rewards: []
    }]
  }];
  state.selected = {
    quests: "",
    dialogues: "",
    npcs: "",
    wallets: "Wallet Name",
    shops: "Shop Name",
    trades: "Trade Name"
  };

  repairCommonFormatting();
  const repairedErrors = validateData().filter((message) => message.startsWith("错误"));
  if (repairedErrors.length) {
    throw new Error("One-click repair left blocking errors: " + repairedErrors.join(" | "));
  }

  const fakeRoot = {
    innerHTML: "",
    querySelector() { return null; },
    querySelectorAll() { return []; }
  };
  document.querySelector = () => fakeRoot;
  document.querySelectorAll = () => [];
  renderWalletEditor();
  if (!fakeRoot.innerHTML.includes("beginner-callout") || !fakeRoot.innerHTML.includes("data-resource-namespace")) {
    throw new Error("Wallet beginner guidance or item suggestions are missing");
  }
  renderShopEditor();
  if (!fakeRoot.innerHTML.includes("制作商店只要三步") || !fakeRoot.innerHTML.includes("advanced-only")) {
    throw new Error("Shop beginner layout is missing");
  }
  state.data.npcs = [{ bindingId: "my_story:archivist", customName: "档案管理员伊莱" }];
  const npcPicker = objectiveTargetField({ type: "talk_to", targetId: "my_story:archivist" }, "phases.0.objectives.0");
  if (!npcPicker.includes("档案管理员伊莱") || !npcPicker.includes("<select")) {
    throw new Error("Talk-to objective does not render a direct NPC picker");
  }

  globalThis.testResult = {
    exampleFileCount,
    repairedWalletId: state.data.wallets[0].walletId,
    repairedShopId: state.data.shops[0].shopId,
    repairedTradeId: state.data.trades[0].tradeId,
    repairSummary: globalThis.repairMessages[0]
  };
`;

const context = {
  console,
  structuredClone,
  document: { addEventListener() {} }
};

vm.createContext(context);
vm.runInContext(`${match[1]}\n${assertions}`, context, { filename: editorPath });
console.log("Editor smoke test OK");
console.log(JSON.stringify(context.testResult, null, 2));
