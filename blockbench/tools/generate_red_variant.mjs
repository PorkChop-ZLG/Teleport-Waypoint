/**
 * 红色变体生成工具：未解锁状态的红色主题资源
 * ------------------------------------------------------------
 * 1. 绘制 3 张红色主题贴图（沿用青色版的绘制逻辑，仅调色板青→红）：
 *      textures/block/waypoint_crystal_red.png / waypoint_ring_red.png / waypoint_orb_red.png
 * 2. 拆分模型：waypoint.json（9 元素）→ 静态层 5 元素（底座+4支柱）
 *    + waypoint_caps.json（4 颗柱顶水晶，青色 crystal 贴图，BER 渲染）
 * 3. 生成红色模型：waypoint_{caps,crystal,ring,orb}_red.json（纹理引用 _red）
 *
 * 运行：node tools/generate_red_variant.mjs
 */
import { deflateSync, inflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = dirname(fileURLToPath(import.meta.url));
const MODELS = join(TOOL_DIR, '..', '..', 'src', 'main', 'resources', 'assets', 'teleportwaypoint', 'models', 'block');
const TEXTURES = join(TOOL_DIR, '..', '..', 'src', 'main', 'resources', 'assets', 'teleportwaypoint', 'textures', 'block');

/* ============================ 画布与 PNG 编码 ============================ */
class Canvas {
  constructor(w = 16, h = 16) {
    this.w = w; this.h = h;
    this.buf = new Uint8Array(w * h * 4);
  }
  set(x, y, [r, g, b], a = 255) {
    x = Math.round(x); y = Math.round(y);
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4;
    this.buf[i] = r; this.buf[i + 1] = g; this.buf[i + 2] = b; this.buf[i + 3] = a;
  }
  fill([r, g, b]) {
    for (let i = 0; i < this.buf.length; i += 4) {
      this.buf[i] = r; this.buf[i + 1] = g; this.buf[i + 2] = b; this.buf[i + 3] = 255;
    }
  }
  rect(x1, y1, x2, y2, c) {
    for (let y = y1; y <= y2; y++) for (let x = x1; x <= x2; x++) this.set(x, y, c);
  }
  hline(y, x1, x2, c) { for (let x = x1; x <= x2; x++) this.set(x, y, c); }
  vline(x, y1, y2, c) { for (let y = y1; y <= y2; y++) this.set(x, y, c); }
  line(x0, y0, x1, y1, c) {
    x0 = Math.round(x0); y0 = Math.round(y0); x1 = Math.round(x1); y1 = Math.round(y1);
    const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
    const sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    let err = dx + dy;
    for (;;) {
      this.set(x0, y0, c);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 >= dy) { err += dy; x0 += sx; }
      if (e2 <= dx) { err += dx; y0 += sy; }
    }
  }
  radial(cx, cy, stops, dither = null) {
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
      for (const [maxD, c] of stops) {
        if (d < maxD) { this.set(x, y, dither && dither(x, y) ? dither(x, y) : c); break; }
      }
    }
  }
  speckle(seed, c, density) {
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      if ((x * 7 + y * 13 + seed * 31) % density === 0 && !(x === 0 && y === 0)) this.set(x, y, c);
    }
  }
}

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
function encodePNG(canvas) {
  const { w, h } = canvas;
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) {
    raw[y * (1 + w * 4)] = 0;
    raw.set(canvas.buf.subarray(y * w * 4, (y + 1) * w * 4), y * (1 + w * 4) + 1);
  }
  const idat = deflateSync(raw, { level: 9 });
  return Buffer.concat([sig, pngChunk('IHDR', ihdr), pngChunk('IDAT', idat), pngChunk('IEND', Buffer.alloc(0))]);
}

/* ============================ 红色调色板（青 → 红 色相变体） ============================ */
const RED = {
  bandBg:   [62, 20, 12],    // 符文带底色
  cyDeep:   [143, 30, 18],   // 深红
  cyDark:   [110, 22, 12],   // 暗红
  cyMid:    [184, 42, 26],   // 中红
  cy:       [232, 60, 40],   // 主红
  cyHi:     [250, 110, 90],  // 亮红
  cyBright: [255, 160, 140], // 亮粉红
  cyHot:    [255, 215, 205], // 白红
  facDark:  [122, 20, 10],   // 刻面暗线
  facDark2: [100, 16, 8],    // 刻面最暗
  facLight: [255, 210, 200], // 刻面亮
  facLite2: [255, 175, 155], // 刻面较亮
  facMid:   [250, 120, 95],  // 刻面中
  white:    [255, 255, 255],
};

/* ============================ 红色贴图绘制（与青色版同构） ============================ */

/** 红色晶核/水晶：刻面红宝石，顶部最亮 */
function drawCrystalRed(c) {
  const rows = [
    [2, RED.facLight], [4, RED.facLite2], [6, RED.facMid],
    [8, RED.cy], [10, RED.cyMid], [12, RED.cyDeep],
    [14, RED.cyDark], [16, RED.facDark2],
  ];
  let ri = 0;
  for (let y = 0; y < 16; y++) {
    while (ri < rows.length && y >= rows[ri][0]) ri++;
    c.hline(y, 0, 15, rows[Math.min(ri, rows.length - 1)][1]);
  }
  c.line(0, 5, 11, 16, RED.facDark);
  c.line(5, 0, 16, 11, RED.facDark);
  c.line(0, 11, 5, 16, RED.facDark);
  c.line(11, 0, 16, 5, RED.facDark);
  for (let i = 0; i < 7; i++) {
    c.set(i, i, i < 4 ? RED.white : RED.facLight);
    c.set(i + 1, i, RED.facLight);
  }
  for (const [x, y] of [[5, 3], [9, 5], [3, 7], [11, 9]]) c.set(x, y, RED.white);
  c.set(10, 2, RED.cyHot); c.set(6, 4, RED.cyHot);
  c.hline(14, 0, 15, RED.facDark2);
  c.hline(15, 0, 15, RED.facDark);
  c.speckle(6, RED.cyDeep, 29);
}

/** 红色能量环：发光带 + 黑曜石边框 */
function drawRingRed(c) {
  c.fill([10, 12, 18]);
  c.speckle(7, [22, 27, 38], 15);
  for (const [x, y] of [[3, 2], [12, 13], [5, 14], [10, 1], [1, 12], [14, 3]]) c.set(x, y, RED.cyDeep);
  c.hline(6, 0, 15, RED.bandBg);
  c.hline(7, 0, 15, RED.cyDeep);
  c.hline(9, 0, 15, RED.cyMid);
  c.hline(10, 0, 15, RED.bandBg);
  for (let x = 0; x < 16; x++) {
    c.set(x, 8, x % 4 === 2 ? RED.cyBright : RED.cyHi);
  }
  c.set(4, 8, RED.cyHot); c.set(9, 8, RED.cyHot); c.set(14, 8, RED.cyHot);
  for (const x of [3, 8, 13]) {
    c.set(x, 7, RED.cy); c.set(x, 9, RED.cy);
    c.set(x, 8, RED.cyHot);
  }
}

/** 红色核心光球：径向辉光 */
function drawOrbRed(c) {
  c.fill([10, 12, 18]);
  c.radial(8, 8, [
    [1.0, RED.white],
    [2.0, RED.cyHot],
    [3.0, RED.cyBright],
    [4.0, RED.cyHi],
    [5.0, RED.cy],
    [6.0, RED.cyMid],
    [7.0, RED.cyDeep],
    [99, [10, 12, 18]],
  ], (x, y) => (Math.hypot(x + 0.5 - 8, y + 0.5 - 8) >= 6 && (x + y) % 2 === 0) ? RED.cyDark : null);
}

/* ============================ 模型拆分与红色变体 ============================ */

const read = (f) => JSON.parse(readFileSync(join(MODELS, f), 'utf8'));
const write = (f, obj) => writeFileSync(join(MODELS, f), JSON.stringify(obj, null, 2) + '\n');

const errors = [];
const check = (ok, msg) => { if (!ok) errors.push(msg); };

// 1) waypoint.json：9 元素 → 5 元素（底座 + 4 支柱），水晶拆出（幂等：已是 5 元素则跳过）
const waypoint = read('waypoint.json');
let caps;
if (waypoint.elements.length === 9) {
  caps = waypoint.elements.slice(5, 9); // 4 颗柱顶水晶
  waypoint.elements = waypoint.elements.slice(0, 5);
  // 重建 textures（只保留用到的 + particle）
  const usedStatic = new Set(['waypoint_base']);
  for (const el of waypoint.elements) {
    for (const f of Object.values(el.faces)) usedStatic.add(f.texture.slice(1));
  }
  const staticTex = { particle: 'teleportwaypoint:block/waypoint_base' };
  for (const t of [...usedStatic].sort()) staticTex[t] = `teleportwaypoint:block/${t}`;
  waypoint.textures = staticTex;
  write('waypoint.json', waypoint);
  console.log(`✅ waypoint.json → 5 元素（底座+支柱），textures: ${[...usedStatic].join(', ')}`);
} else {
  check(waypoint.elements.length === 5, `waypoint.json 应为 5 或 9 元素，实际 ${waypoint.elements.length}`);
  caps = read('waypoint_caps.json').elements;
  check(caps.length === 4, 'waypoint_caps.json 应为 4 元素');
  console.log('↪ waypoint.json 已是拆分态，跳过拆分');
}

// 2) waypoint_caps.json：4 颗柱顶水晶（青色 crystal 贴图）
const capsModel = {
  credit: 'Made with Blockbench',
  ambientocclusion: false,
  textures: {
    particle: 'teleportwaypoint:block/waypoint_crystal',
    waypoint_crystal: 'teleportwaypoint:block/waypoint_crystal',
  },
  elements: caps,
};
write('waypoint_caps.json', capsModel);
console.log('✅ waypoint_caps.json → 4 元素（柱顶水晶，青色）');

// 3) 红色模型变体：caps / crystal / ring / orb（纹理引用 _red）
const RED_VARIANTS = {
  'waypoint_caps_red.json': ['waypoint_caps.json', 'waypoint_crystal'],
  'waypoint_crystal_red.json': ['waypoint_crystal.json', 'waypoint_crystal'],
  'waypoint_ring_red.json': ['waypoint_ring.json', 'waypoint_ring'],
  'waypoint_orb_red.json': ['waypoint_orb.json', 'waypoint_orb'],
};
for (const [outFile, [srcFile, texBase]] of Object.entries(RED_VARIANTS)) {
  const m = read(srcFile);
  const redTex = `${texBase}_red`;
  const textures = { particle: `teleportwaypoint:block/${redTex}` };
  textures[redTex] = `teleportwaypoint:block/${redTex}`;
  m.textures = textures;
  // 元素面的纹理引用同步替换为红色版（#waypoint_crystal → #waypoint_crystal_red）
  for (const el of m.elements) {
    for (const fc of Object.values(el.faces)) {
      if (fc.texture === `#${texBase}`) fc.texture = `#${redTex}`;
    }
  }
  write(outFile, m);
  console.log(`✅ ${outFile} → 纹理 ${redTex}`);
}

// 4) 红色贴图绘制
mkdirSync(TEXTURES, { recursive: true });
const draws = {
  'waypoint_crystal_red.png': drawCrystalRed,
  'waypoint_ring_red.png': drawRingRed,
  'waypoint_orb_red.png': drawOrbRed,
};
const pngs = {};
for (const [name, draw] of Object.entries(draws)) {
  const c = new Canvas(16, 16);
  draw(c);
  const png = encodePNG(c);
  pngs[name] = { png, canvas: c };
  writeFileSync(join(TEXTURES, name), png);
}
console.log(`✅ 红色贴图：${Object.keys(pngs).join(', ')}`);

/* ============================ 自检 ============================ */
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
for (const [name, { png, canvas }] of Object.entries(pngs)) {
  const { w, h, raw } = decodePNG(png);
  check(w === 16 && h === 16, `${name}: 尺寸应为 16x16`);
  check(raw.length === h * (1 + w * 4), `${name}: 解码长度不符`);
  let same = true;
  for (let y = 0; y < h && same; y++) {
    for (let x = 0; x < w; x++) {
      const i = y * (1 + w * 4) + 1 + x * 4;
      const j = (y * w + x) * 4;
      if (raw[i] !== canvas.buf[j] || raw[i + 1] !== canvas.buf[j + 1] || raw[i + 2] !== canvas.buf[j + 2]) { same = false; break; }
    }
  }
  check(same, `${name}: 解码像素与画布不一致`);
}
// 关键像素：红色主题
const px = (name, x, y) => {
  const { canvas } = pngs[name];
  const i = (y * 16 + x) * 4;
  return [canvas.buf[i], canvas.buf[i + 1], canvas.buf[i + 2]];
};
const near = (a, b, tol = 60) => a.every((v, i) => Math.abs(v - b[i]) <= tol);
check(near(px('waypoint_crystal_red.png', 1, 1), RED.white, 10), 'crystal_red 高光应为白');
check(px('waypoint_crystal_red.png', 6, 6)[0] > px('waypoint_crystal_red.png', 6, 6)[2], 'crystal_red 主体应为红色调');
check(px('waypoint_ring_red.png', 7, 8)[0] > px('waypoint_ring_red.png', 7, 8)[2], 'ring_red 主线应为红色调');
check(px('waypoint_orb_red.png', 4, 4)[0] > 150 && px('waypoint_orb_red.png', 4, 4)[2] < 150, 'orb_red 应为红色辉光');

// 模型校验：所有引用的纹理都能解析
for (const f of ['waypoint.json', 'waypoint_caps.json', 'waypoint_caps_red.json', 'waypoint_crystal_red.json', 'waypoint_ring_red.json', 'waypoint_orb_red.json']) {
  const m = read(f);
  check(m.elements.length > 0, `${f}: 不应为空模型`);
  for (const el of m.elements) {
    for (const fc of Object.values(el.faces)) {
      check(fc.texture.startsWith('#') && m.textures[fc.texture.slice(1)], `${f}: 纹理引用 ${fc.texture} 无法解析`);
      check(Array.isArray(fc.uv) && fc.uv.length === 4 && fc.uv.every((v) => v >= 0 && v <= 16), `${f}: uv 越界`);
    }
    if (el.rotation) check([0, 22.5, -22.5, 45, -45].includes(el.rotation.angle), `${f}: 旋转角非法`);
  }
}
// 元素总数：5 + 4 + 2 + 4 + 1 = 16
const counts = [read('waypoint.json').elements.length, caps.length,
  read('waypoint_crystal.json').elements.length, read('waypoint_ring.json').elements.length,
  read('waypoint_orb.json').elements.length];
check(counts.reduce((a, b) => a + b, 0) === 16, `元素总数应为 16，实际 ${counts.join('+')}`);

if (errors.length) {
  console.error('❌ 校验失败：');
  for (const e of errors) console.error('   - ' + e);
  process.exit(1);
}
console.log(`✅ 全部校验通过（元素分布 ${counts.join(' + ')} = 16）`);
