/**
 * 模型拆分工具：把完整 waypoint.json（16 元素）拆分为静态层 + 3 个动态层模型
 * ------------------------------------------------------------
 * 背景：为「传送锚点」实现动态效果（BER 动画），需要把模型拆成静态层与动态层：
 *   - models/block/waypoint.json          静态层：底座 + 四支柱 + 四柱顶水晶（9 元素，blockstate 仍引用它）
 *   - models/block/waypoint_crystal.json  动态层：晶核（双层 45° 菱形棱柱，BER 中绕 Y 轴自转）
 *   - models/block/waypoint_ring.json     动态层：能量环（四根环梁，BER 中反向旋转 + 上下浮动）
 *   - models/block/waypoint_orb.json      动态层：核心光球（BER 中上下浮动 + 脉冲缩放）
 *
 * 拆分依据：waypoint.json 的元素顺序（生成时固定：0 底座 / 1-4 支柱 / 5-8 柱顶水晶 /
 * 9-12 能量环 / 13-14 晶核 / 15 光球），并带几何断言防止顺序漂移。
 *
 * 运行：node tools/split_model.mjs
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = dirname(fileURLToPath(import.meta.url));
const SRC_MODEL = join(TOOL_DIR, '..', '..', 'src', 'main', 'resources', 'assets', 'teleportwaypoint', 'models', 'block', 'waypoint.json');
const OUT_DIR = join(TOOL_DIR, '..', '..', 'src', 'main', 'resources', 'assets', 'teleportwaypoint', 'models', 'block');

const FULL = JSON.parse(readFileSync(SRC_MODEL, 'utf8'));
const errors = [];
const check = (ok, msg) => { if (!ok) errors.push(msg); };

check(FULL.elements.length === 16, `完整模型应有 16 元素，实际 ${FULL.elements.length}`);

// ---- 顺序断言（防止模型顺序漂移导致拆分错误） ----
const [base, ...rest] = FULL.elements;
check(JSON.stringify(base.from) === '[0,0,0]' && JSON.stringify(base.to) === '[16,5,16]', '索引 0 应为底座 [0,0,0]-[16,5,16]');
rest.slice(0, 4).forEach((el, i) => {
  check(JSON.stringify(el.to) === '[3,10,3]' || JSON.stringify(el.to) === '[15,10,3]' ||
        JSON.stringify(el.to) === '[3,10,15]' || JSON.stringify(el.to) === '[15,10,15]', `索引 ${i + 1} 应为支柱`);
});
rest.slice(4, 8).forEach((el, i) => {
  check(el.to[1] === 12 && el.shade === false, `索引 ${i + 5} 应为柱顶水晶`);
});
rest.slice(8, 12).forEach((el, i) => {
  check(el.from[1] === 8.5 && el.to[1] === 9.5, `索引 ${i + 9} 应为能量环`);
});
[rest[12], rest[13]].forEach((el, i) => {
  check(el.rotation?.axis === 'y' && el.rotation?.angle === 45, `索引 ${i + 13} 应为 45° 旋转晶核`);
});
check(rest[14].to[0] - rest[14].from[0] === 1.5 && rest[14].shade === false, '索引 15 应为核心光球');

// ---- 拆分定义 ----
const SPLITS = [
  { file: 'waypoint.json', indices: [0, 1, 2, 3, 4, 5, 6, 7, 8], particle: '#waypoint_base' },
  { file: 'waypoint_crystal.json', indices: [13, 14], particle: '#waypoint_crystal' },
  { file: 'waypoint_ring.json', indices: [9, 10, 11, 12], particle: '#waypoint_ring' },
  { file: 'waypoint_orb.json', indices: [15], particle: '#waypoint_orb' },
];

for (const split of SPLITS) {
  const elements = split.indices.map((i) => FULL.elements[i]);
  const usedTex = new Set([split.particle.slice(1)]);
  for (const el of elements) {
    for (const f of Object.values(el.faces)) usedTex.add(f.texture.slice(1));
  }
  const textures = { particle: FULL.textures[split.particle] };
  for (const t of [...usedTex].sort()) textures[t] = FULL.textures[t];

  const model = {
    credit: 'Made with Blockbench',
    ambientocclusion: false,
    textures,
    elements,
  };

  mkdirSync(OUT_DIR, { recursive: true });
  const outPath = join(OUT_DIR, split.file);
  writeFileSync(outPath, JSON.stringify(model, null, 2) + '\n');
  console.log(`✅ ${split.file}: ${elements.length} 元素 → ${[...usedTex].join(', ')}`);

  // 回读自检
  const back = JSON.parse(readFileSync(outPath, 'utf8'));
  check(back.elements.length === elements.length, `${split.file}: 回读元素数不一致`);
  for (const el of back.elements) {
    for (const f of Object.values(el.faces)) {
      check(f.texture.startsWith('#') && back.textures[f.texture.slice(1)], `${split.file}: 纹理引用 ${f.texture} 无法解析`);
    }
  }
}

if (errors.length) {
  console.error('❌ 校验失败：');
  for (const e of errors) console.error('   - ' + e);
  process.exit(1);
}
console.log('✅ 拆分完成（9 + 2 + 4 + 1 = 16），全部校验通过');
