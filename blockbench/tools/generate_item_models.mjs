/**
 * 物品完整模型生成器
 * ------------------------------------------------------------
 * 物品栏/手持显示方块的 3D 完整模型（BER 无法在物品渲染中工作，
 * 因此把静态层 + 动态部件合并为单一静态模型）：
 *   - waypoint_full.json：传送锚点完整模型（16 元素，青色默认组）
 *   - waypoint_full_green.json：口袋锚点完整模型（16 元素，绿色组）
 * 元素 = 静态层（底座+4支柱，5） + 悬浮水晶（4，y12-14）
 *       + 晶核（2） + 能量环（4） + 核心光球（1）
 *
 * 运行：node tools/generate_item_models.mjs（幂等）
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = dirname(fileURLToPath(import.meta.url));
const MODELS = join(TOOL_DIR, '..', '..', 'src', 'main', 'resources', 'assets', 'teleportwaypoint', 'models', 'block');
const ITEMS = join(TOOL_DIR, '..', '..', 'src', 'main', 'resources', 'assets', 'teleportwaypoint', 'models', 'item');

const read = (f) => JSON.parse(readFileSync(join(MODELS, f), 'utf8'));
const write = (p, obj) => writeFileSync(p, JSON.stringify(obj, null, 2) + '\n');

const errors = [];
const check = (ok, msg) => { if (!ok) errors.push(msg); };

/** 合并完整模型：静态层 + 4 悬浮水晶 + 晶核 + 环 + 光球 */
function buildFullModel(staticJson, capsSuffix, gradSuffix) {
  const elements = [...staticJson.elements];
  for (const pos of ['nw', 'ne', 'sw', 'se']) {
    const caps = read(`waypoint_caps_${pos}${capsSuffix}.json`);
    elements.push(...caps.elements);
  }
  for (const part of ['crystal', 'ring', 'orb']) {
    const m = read(`waypoint_${part}${gradSuffix}.json`);
    elements.push(...m.elements);
  }
  // textures：合并全部引用键
  const textures = { particle: 'teleportwaypoint:block/waypoint_base' };
  for (const el of elements) {
    for (const fc of Object.values(el.faces)) {
      const key = fc.texture.slice(1);
      textures[key] = `teleportwaypoint:block/${key}`;
    }
  }
  return {
    credit: 'Made with Blockbench',
    ambientocclusion: false,
    textures,
    elements,
  };
}

mkdirSync(ITEMS, { recursive: true });

// 1) 完整模型（青色 / 绿色）
const staticJson = read('waypoint.json');
const fullCyan = buildFullModel(staticJson, '', '');
const fullGreen = buildFullModel(staticJson, '_green', '_green');
write(join(MODELS, 'waypoint_full.json'), fullCyan);
write(join(MODELS, 'waypoint_full_green.json'), fullGreen);
console.log(`✅ waypoint_full.json（${fullCyan.elements.length} 元素，青色）`);
console.log(`✅ waypoint_full_green.json（${fullGreen.elements.length} 元素，绿色）`);

// 2) 物品模型：parent 完整模型 + display（物品栏 3D 展示）
const DISPLAY = {
  thirdperson_righthand: { translation: [0, 2.5, 0], scale: [0.5, 0.5, 0.5] },
  thirdperson_lefthand: { translation: [0, 2.5, 0], scale: [0.5, 0.5, 0.5] },
  firstperson_righthand: { translation: [0, 2.5, 0], scale: [0.5, 0.5, 0.5] },
  firstperson_lefthand: { translation: [0, 2.5, 0], scale: [0.5, 0.5, 0.5] },
  ground: { translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
  gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.55, 0.55, 0.55] },
  fixed: { translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
};
const itemWaypoint = { parent: 'teleportwaypoint:block/waypoint_full', display: DISPLAY };
const itemPocket = { parent: 'teleportwaypoint:block/waypoint_full_green', display: DISPLAY };
write(join(ITEMS, 'waypoint.json'), itemWaypoint);
write(join(ITEMS, 'pocket_waypoint.json'), itemPocket);
console.log('✅ 物品模型：waypoint → waypoint_full（青），pocket_waypoint → waypoint_full_green（绿）');

/* ---- 自检 ---- */
for (const f of ['waypoint_full.json', 'waypoint_full_green.json']) {
  const m = read(f);
  check(m.elements.length === 16, `${f}: 应为 16 元素，实际 ${m.elements.length}`);
  for (const el of m.elements) {
    for (const fc of Object.values(el.faces)) {
      check(fc.texture.startsWith('#') && m.textures[fc.texture.slice(1)], `${f}: 纹理引用 ${fc.texture} 无法解析`);
      check(Array.isArray(fc.uv) && fc.uv.length === 4, `${f}: uv 非法`);
    }
    if (el.rotation) check([0, 22.5, -22.5, 45, -45].includes(el.rotation.angle), `${f}: 旋转角非法`);
  }
  // 关键部件存在性：悬浮水晶（y12-14）、光球（y14.25-15.75）
  const capsCount = m.elements.filter((e) => e.from[1] === 12 && e.to[1] === 14).length;
  check(capsCount === 4, `${f}: 应有 4 颗悬浮水晶，实际 ${capsCount}`);
  const orb = m.elements.find((e) => e.to[0] - e.from[0] === 1.5);
  check(orb && orb.from[1] === 14.25, `${f}: 光球应位于 y14.25`);
}
for (const f of ['waypoint.json', 'pocket_waypoint.json']) {
  const m = JSON.parse(readFileSync(join(ITEMS, f), 'utf8'));
  check(typeof m.parent === 'string' && m.parent.startsWith('teleportwaypoint:block/waypoint_full'), `${f}: parent 应为完整模型`);
  check(m.display?.gui !== undefined, `${f}: 应有 gui display`);
}
// 绿色版纹理检查
{
  const g = read('waypoint_full_green.json');
  const texKeys = new Set(Object.keys(g.textures));
  check(texKeys.has('waypoint_caps_green') && texKeys.has('waypoint_crystal_green')
    && texKeys.has('waypoint_ring_green') && texKeys.has('waypoint_orb_green'), '绿色版应引用全部 _green 贴图');
}

if (errors.length) {
  console.error('❌ 校验失败：');
  for (const e of errors) console.error('   - ' + e);
  process.exit(1);
}
console.log('✅ 全部校验通过');
