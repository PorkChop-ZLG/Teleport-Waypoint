/**
 * 悬浮柱顶水晶生成工具（v2：防穿模 + 纯色贴图）
 * ------------------------------------------------------------
 * 1. 生成 8 个悬浮水晶模型（4 位置 × 青/红）：
 *      2×2×2 规整立方体，悬浮于柱心上方（y12-14，柱顶 y10，间距 2 格）
 *      青色组引用纯色贴图 waypoint_caps，红色组引用 waypoint_caps_red
 * 2. 生成 2 张纯色贴图（16×16 无渐变）：
 *      waypoint_caps.png（#6fe4f7）/ waypoint_caps_red.png（#fa6e5a）
 * 3. 核心光球上移 1 格防穿模：waypoint_orb.json + waypoint_orb_red.json
 *      y 13.25-14.75 → 14.25-15.75（浮动 ±0.125 不变，最低 14.125 > 晶核顶 13）
 * 4. 静态层 4 根支柱补 up 面（waypoint_pillar_top，防悬浮后露空心）
 * 5. 删除废弃的旧 caps 单文件模型
 *
 * 运行：node tools/generate_float_caps.mjs（幂等）
 */
import { deflateSync, inflateSync } from 'node:zlib';
import { readFileSync, writeFileSync, unlinkSync, existsSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = dirname(fileURLToPath(import.meta.url));
const MODELS = join(TOOL_DIR, '..', '..', 'src', 'main', 'resources', 'assets', 'teleportwaypoint', 'models', 'block');
const TEXTURES = join(TOOL_DIR, '..', '..', 'src', 'main', 'resources', 'assets', 'teleportwaypoint', 'textures', 'block');

const read = (f) => JSON.parse(readFileSync(join(MODELS, f), 'utf8'));
const write = (f, obj) => writeFileSync(join(MODELS, f), JSON.stringify(obj, null, 2) + '\n');

const errors = [];
const check = (ok, msg) => { if (!ok) errors.push(msg); };

/* ============================ PNG 编码（纯色贴图） ============================ */
const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function pngChunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const t = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}
function solidPng([r, g, b]) {
  const w = 16, h = 16;
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) {
    raw[y * (1 + w * 4)] = 0;
    for (let x = 0; x < w; x++) {
      const i = y * (1 + w * 4) + 1 + x * 4;
      raw[i] = r; raw[i + 1] = g; raw[i + 2] = b; raw[i + 3] = 255;
    }
  }
  const idat = deflateSync(raw, { level: 9 });
  return Buffer.concat([sig, pngChunk('IHDR', ihdr), pngChunk('IDAT', idat), pngChunk('IEND', Buffer.alloc(0))]);
}
function decodePNG(buf) {
  let off = 8, w = 0, h = 0, idat = [];
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString('ascii', off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === 'IHDR') { w = data.readUInt32BE(0); h = data.readUInt32BE(4); }
    else if (type === 'IDAT') idat.push(data);
    off += 12 + len;
  }
  return { w, h, raw: inflateSync(Buffer.concat(idat)) };
}

/* ---- 1) 悬浮立方体元素（2×2×2，柱心上方 y12-14） ---- */
const CAPS = {
  nw: [1, 12, 1, 3, 14, 3],
  ne: [13, 12, 1, 15, 14, 3],
  sw: [1, 12, 13, 3, 14, 15],
  se: [13, 12, 13, 15, 14, 15],
};

function capsElement(from, texKey) {
  const [x1, y1, z1, x2, y2, z2] = from;
  const faces = {};
  for (const dir of ['down', 'up', 'north', 'south', 'west', 'east']) {
    faces[dir] = { uv: [7, 7, 9, 9], texture: `#${texKey}` };
  }
  return { from: [x1, y1, z1], to: [x2, y2, z2], faces, shade: false };
}

/* ---- 2) 纯色贴图生成（青/红/绿/黄） ---- */
mkdirSync(TEXTURES, { recursive: true });
const SOLID_COLORS = {
  'waypoint_caps.png': [111, 228, 247],          // #6fe4f7 亮青
  'waypoint_caps_red.png': [250, 110, 90],       // #fa6e5a 亮红
  'waypoint_caps_green.png': [159, 245, 168],    // #9ff5a8 淡绿
  'waypoint_caps_yellow.png': [247, 242, 160],   // #f7f2a0 淡黄
};
const pngs = {};
for (const [name, color] of Object.entries(SOLID_COLORS)) {
  const png = solidPng(color);
  pngs[name] = png;
  writeFileSync(join(TEXTURES, name), png);
  console.log(`✅ 纯色贴图 ${name}（rgb ${color.join(',')}）`);
}

/* ---- 3) caps 模型生成（引用纯色贴图，4 色调） ---- */
const CAPS_TONES = [
  ['', 'waypoint_caps'],
  ['_red', 'waypoint_caps_red'],
  ['_green', 'waypoint_caps_green'],
  ['_yellow', 'waypoint_caps_yellow'],
];
for (const [pos, from] of Object.entries(CAPS)) {
  for (const [suffix, texKey] of CAPS_TONES) {
    const model = {
      credit: 'Made with Blockbench',
      ambientocclusion: false,
      textures: {
        particle: `teleportwaypoint:block/${texKey}`,
        [texKey]: `teleportwaypoint:block/${texKey}`,
      },
      elements: [capsElement(from, texKey)],
    };
    write(`waypoint_caps_${pos}${suffix}.json`, model);
  }
  console.log(`✅ waypoint_caps_${pos}.json ×4 色调生成（${JSON.stringify(from)}，纯色贴图）`);
}

/* ---- 4) 核心光球上移 1 格（幂等：已是 14.25 则跳过；含全部色调变体） ---- */
for (const f of ['waypoint_orb.json', 'waypoint_orb_red.json', 'waypoint_orb_green.json', 'waypoint_orb_yellow.json']) {
  if (!existsSync(join(MODELS, f))) {
    console.log(`↪ ${f} 不存在，跳过（由变体生成器产出）`);
    continue;
  }
  const m = read(f);
  const el = m.elements[0];
  if (el.from[1] === 14.25 && el.to[1] === 15.75) {
    console.log(`↪ ${f} 已是上移态，跳过`);
  } else {
    check(el.from[1] === 13.25 && el.to[1] === 14.75, `${f}: 期望原位置 y13.25-14.75，实际 ${el.from[1]}-${el.to[1]}`);
    el.from[1] += 1;
    el.to[1] += 1;
    write(f, m);
    console.log(`✅ ${f} 上移 → y${el.from[1]}-${el.to[1]}`);
  }
}

/* ---- 5) 静态层支柱补 up 面（幂等） ---- */
const waypoint = read('waypoint.json');
check(waypoint.elements.length === 5, `waypoint.json 应为 5 元素，实际 ${waypoint.elements.length}`);
let capped = 0;
for (const el of waypoint.elements) {
  if (!el.faces.up && el.to[1] === 10 && el.from[1] === 5) {
    el.faces.up = { uv: [7, 7, 9, 9], texture: '#waypoint_pillar_top' };
    capped++;
  }
}
if (capped > 0) {
  waypoint.textures.waypoint_pillar_top = 'teleportwaypoint:block/waypoint_pillar_top';
  write('waypoint.json', waypoint);
}
console.log(`✅ waypoint.json 支柱补 up 面 ×${capped}`);

/* ---- 6) 删除废弃旧 caps 单文件模型 ---- */
for (const f of ['waypoint_caps.json', 'waypoint_caps_red.json']) {
  const p = join(MODELS, f);
  if (existsSync(p)) {
    unlinkSync(p);
    console.log(`🗑 删除废弃文件 ${f}`);
  }
}

/* ---- 自检 ---- */
// 纯色贴图：全像素一致 + 指定色值
for (const [name, color] of Object.entries(SOLID_COLORS)) {
  const { w, h, raw } = decodePNG(pngs[name]);
  check(w === 16 && h === 16, `${name}: 尺寸应为 16x16`);
  const first = [raw[1], raw[2], raw[3]];
  let allSame = true;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = y * (1 + w * 4) + 1 + x * 4;
      if (raw[i] !== first[0] || raw[i + 1] !== first[1] || raw[i + 2] !== first[2]) { allSame = false; break; }
    }
    if (!allSame) break;
  }
  check(allSame, `${name}: 应为纯色（无渐变）`);
  check(first.every((v, i) => Math.abs(v - color[i]) <= 1), `${name}: 色值应为 ${color.join(',')}，实际 ${first.join(',')}`);
}
// caps 模型：y12-14、6 面、纯色贴图引用（4 色调）
for (const [pos] of Object.entries(CAPS)) {
  for (const [suffix, texKey] of CAPS_TONES) {
    const m = read(`waypoint_caps_${pos}${suffix}.json`);
    const el = m.elements[0];
    check(el.from[1] === 12 && el.to[1] === 14, `caps_${pos}${suffix}: 应悬浮 y12-14`);
    check(Object.keys(el.faces).length === 6, `caps_${pos}${suffix}: 应有 6 面`);
    for (const fc of Object.values(el.faces)) {
      check(fc.texture === `#${texKey}`, `caps_${pos}${suffix}: 应引用纯色贴图 ${texKey}`);
    }
  }
}
// orb 上移 + 不穿模数学断言（含全部色调变体）
for (const f of ['waypoint_orb.json', 'waypoint_orb_red.json', 'waypoint_orb_green.json', 'waypoint_orb_yellow.json']) {
  if (!existsSync(join(MODELS, f))) continue;
  const m = read(f);
  const el = m.elements[0];
  check(el.from[1] === 14.25 && el.to[1] === 15.75, `${f}: 应上移至 y14.25-15.75`);
  const lowest = el.from[1] - 0.125; // 浮动幅度（渲染器）
  const highest = el.to[1] + 0.125;
  check(lowest > 13, `${f}: 最低 ${lowest} 应高于晶核顶 13（防穿模）`);
  check(highest < 16, `${f}: 最高 ${highest} 不应越出方块`);
}
// caps 不穿模断言
for (const [pos, from] of Object.entries(CAPS)) {
  const lowest = from[1] - 0.125; // 浮动幅度（渲染器）
  check(lowest > 10, `caps_${pos}: 最低 ${lowest} 应高于柱顶 10（防穿模）`);
}
// 支柱顶面
{
  const m = read('waypoint.json');
  check(m.elements.length === 5, 'waypoint.json 应为 5 元素');
  const pillars = m.elements.filter((e) => e.to[1] === 10 && e.from[1] === 5);
  check(pillars.length === 4 && pillars.every((e) => e.faces.up), '每根支柱都应有 up 面');
  for (const el of m.elements) {
    for (const fc of Object.values(el.faces)) {
      check(fc.texture.startsWith('#') && m.textures[fc.texture.slice(1)], `waypoint.json: 纹理引用 ${fc.texture} 无法解析`);
    }
  }
}
check(!existsSync(join(MODELS, 'waypoint_caps.json')), '废弃文件 waypoint_caps.json 应已删除');

if (errors.length) {
  console.error('❌ 校验失败：');
  for (const e of errors) console.error('   - ' + e);
  process.exit(1);
}
console.log('✅ 全部校验通过（caps y12-14 + 纯色贴图 + orb y14.25-15.75 + 不穿模断言）');
