import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";

const htmlPath = new URL("../editor/PhantasmMaker-黑暗风格-FTB搜索选择版.html", import.meta.url);
const html = await readFile(htmlPath, "utf8");
const match = html.match(/<script>([\s\S]*?)<\/script>/);
assert.ok(match, "Editor script was not found");

const script = match[1].split('document.addEventListener("click"')[0];
const context = vm.createContext({
    console,
    TextEncoder,
    URL,
    Blob,
    setTimeout,
    clearTimeout,
    document: {},
    window: {},
    localStorage: {},
    CSS: { escape: value => String(value) }
});

const assertions = `
globalThis.__itemRewardTests = (() => {
    const plain = parseRewardItemInput("minecraft:emerald");
    if (!plain || plain.itemId !== "minecraft:emerald" || plain.nbt !== "") {
        throw new Error("plain reward item was not parsed");
    }

    const tagged = parseRewardItemInput("mmorpg:stat_soul/family/weapon/common{tier:1}");
    if (!tagged || tagged.itemId !== "mmorpg:stat_soul/family/weapon/common" || tagged.nbt !== "{tier:1}") {
        throw new Error("tagged reward item was not parsed");
    }

    const nested = parseRewardItemInput("minecraft:diamond{display:{Name:'Test'},tier:1}");
    if (!nested || nested.nbt !== "{display:{Name:'Test'},tier:1}") {
        throw new Error("nested reward NBT was not parsed");
    }

    if (parseRewardItemInput("minecraft:diamond{tier:1") !== null) {
        throw new Error("malformed reward NBT was accepted");
    }
    return true;
})();
`;

vm.runInContext(script + assertions, context, { filename: "PhantasmMaker.html" });
assert.ok(context.__itemRewardTests);
