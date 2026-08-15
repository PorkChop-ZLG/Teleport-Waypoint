/**
 * 渐变贴图变体生成器（红/绿/黄 + 对应模型 JSON）
 * ------------------------------------------------------------
 * 以现有青色绘制逻辑为基础，按色调参数化生成：
 *   - 贴图：waypoint_{crystal,ring,orb}_{red,green,yellow}.png
 *   - 模型：waypoint_{crystal,ring,orb}_{red,green,yellow}.json
 *     （几何/UV 与青色版完全一致，仅纹理引用切换）
 *   - orb 变体生成后直接上移（y14.25-15.75，与青色版一致防穿模）
 *
 * 运行：node tools/generate_gradient_variants.mjs（幂等）
 */
import { deflateSync, inflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = dirname(fileURLToPath(import.meta.url));
const MODELS = join(TOOL_DIR, '..', '..', 'src', 'main', 'resources', 'assets', 'teleportwaypoint', 'models', 'block');
const TEXTURES = join(TOOL_DIR, '..', '..', 'src', 'main', 'resources', 'assets', 'teleportwaypoint', 'textures', 'block');

const read = (f) => JSON.parse(readFileSync(join(MODELS, f), 'utf8'));
const write = (f, obj) => writeFileSync(join(MODELS, f), JSON.stringify(obj, null, 2) + '\n');

const errors = [];
const check = (ok, msg) => { if (!ok) errors.push(msg); };

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
  hline(y, x1, x2, c) { for (let x = x1; x <= x2; x++) this.set(x, y, c); }
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

/* ============================ 色调调色板（与青色同构） ============================ */
const TONES = {
  red: {
    label: '红',
    bandBg: [62, 20, 12], cyDeep: [143, 30, 18], cyDark: [110, 22, 12], cyMid: [184, 42, 26],
    cy: [232, 60, 40], cyHi: [250, 110, 90], cyBright: [255, 160, 140], cyHot: [255, 215, 205],
    facDark: [122, 20, 10], facDark2: [100, 16, 8], facLight: [255, 210, 200], facLite2: [255, 175, 155], facMid: [250, 120, 95],
    white: [255, 255, 255],
  },
  green: {
    label: '绿',
    bandBg: [14, 52, 22], cyDeep: [28, 122, 50], cyDark: [20, 92, 38], cyMid: [48, 172, 72],
    cy: [80, 220, 100], cyHi: [140, 240, 155], cyBright: [192, 250, 200], cyHot: [226, 255, 230],
    facDark: [18, 100, 36], facDark2: [14, 80, 28], facLight: [226, 255, 230], facLite2: [192, 250, 200], facMid: [140, 240, 155],
    white: [255, 255, 255],
  },
  yellow: {
    label: '黄',
    bandBg: [58, 50, 12], cyDeep: [150, 130, 20], cyDark: [116, 100, 16], cyMid: [192, 168, 42],
    cy: [235, 210, 70], cyHi: [250, 235, 130], cyBright: [255, 246, 185], cyHot: [255, 253, 228],
    facDark: [130, 112, 18], facDark2: [104, 90, 14], facLight: [255, 253, 228], facLite2: [255, 246, 185], facMid: [250, 235, 130],
    white: [255, 255, 255],
  },
};

/* ============================ 绘制函数（色调参数化） ============================ */
function drawCrystal(c, P) {
  const rows = [
    [2, P.facLight], [4, P.facLite2], [6, P.facMid],
    [8, P.cy], [10, P.cyMid], [12, P.cyDeep],
    [14, P.cyDark], [16, P.facDark2],
  ];
  let ri = 0;
  for (let y = 0; y < 16; y++) {
    while (ri < rows.length && y >= rows[ri][0]) ri++;
    c.hline(y, 0, 15, rows[Math.min(ri, rows.length - 1)][1]);
  }
  c.line(0, 5, 11, 16, P.facDark);
  c.line(5, 0, 16, 11, P.facDark);
  c.line(0, 11, 5, 16, P.facDark);
  c.line(11, 0, 16, 5, P.facDark);
  for (let i = 0; i < 7; i++) {
    c.set(i, i, i < 4 ? P.white : P.facLight);
    c.set(i + 1, i, P.facLight);
  }
  for (const [x, y] of [[5, 3], [9, 5], [3, 7], [11, 9]]) c.set(x, y, P.white);
  c.set(10, 2, P.cyHot); c.set(6, 4, P.cyHot);
  c.hline(14, 0, 15, P.facDark2);
  c.hline(15, 0, 15, P.facDark);
  c.speckle(6, P.cyDeep, 29);
}

function drawRing(c, P) {
  c.fill([10, 12, 18]);
  c.speckle(7, [22, 27, 38], 15);
  for (const [x, y] of [[3, 2], [12, 13], [5, 14], [10, 1], [1, 12], [14, 3]]) c.set(x, y, P.cyDeep);
  c.hline(6, 0, 15, P.bandBg);
  c.hline(7, 0, 15, P.cyDeep);
  c.hline(9, 0, 15, P.cyMid);
  c.hline(10, 0, 15, P.bandBg);
  for (let x = 0; x < 16; x++) c.set(x, 8, x % 4 === 2 ? P.cyBright : P.cyHi);
  c.set(4, 8, P.cyHot); c.set(9, 8, P.cyHot); c.set(14, 8, P.cyHot);
  for (const x of [3, 8, 13]) {
    c.set(x, 7, P.cy); c.set(x, 9, P.cy);
    c.set(x, 8, P.cyHot);
  }
}

function drawOrb(c, P) {
  c.fill([10, 12, 18]);
  c.radial(8, 8, [
    [1.0, P.white],
    [2.0, P.cyHot],
    [3.0, P.cyBright],
    [4.0, P.cyHi],
    [5.0, P.cy],
    [6.0, P.cyMid],
    [7.0, P.cyDeep],
    [99, [10, 12, 18]],
  ], (x, y) => (Math.hypot(x + 0.5 - 8, y + 0.5 - 8) >= 6 && (x + y) % 2 === 0) ? P.cyDark : null);
}

/* ============================ 主流程 ============================ */
mkdirSync(TEXTURES, { recursive: true });
const pngs = {};
const draws = { crystal: drawCrystal, ring: drawRing, orb: drawOrb };
const modelSrcs = { crystal: 'waypoint_crystal.json', ring: 'waypoint_ring.json', orb: 'waypoint_orb.json' };

for (const [tone, P] of Object.entries(TONES)) {
  for (const [part, draw] of Object.entries(draws)) {
    // 贴图
    const c = new Canvas(16, 16);
    draw(c, P);
    const png = encodePNG(c);
    const texName = `waypoint_${part}_${tone}`;
    pngs[texName] = { png, canvas: c };
    writeFileSync(join(TEXTURES, `${texName}.png`), png);
    // 模型（几何/UV 来自青色版，仅换纹理引用）
    const m = read(modelSrcs[part]);
    const textures = { particle: `teleportwaypoint:block/${texName}` };
    textures[texName] = `teleportwaypoint:block/${texName}`;
    m.textures = textures;
    for (const el of m.elements) {
      for (const fc of Object.values(el.faces)) {
        if (fc.texture.startsWith('#waypoint_')) fc.texture = `#${texName}`;
      }
    }
    // orb 变体直接上移（与青色版一致防穿模）
    if (part === 'orb' && m.elements[0].from[1] === 13.25) {
      m.elements[0].from[1] += 1;
      m.elements[0].to[1] += 1;
    }
    write(`${texName}.json`, m);
    console.log(`✅ ${texName}（${P.label}）贴图 + 模型`);
  }
}

/* ============================ 自检 ============================ */
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
// 色调断言：绿 G 分量最高，黄 R+G 高 B 低
const px = (name, x, y) => {
  const { canvas } = pngs[name];
  const i = (y * 16 + x) * 4;
  return [canvas.buf[i], canvas.buf[i + 1], canvas.buf[i + 2]];
};
const g = px('waypoint_crystal_green', 6, 6);
check(g[1] > g[0] && g[1] > g[2], `crystal_green 主体应为绿色调（rgb ${g.join(',')}）`);
const y = px('waypoint_crystal_yellow', 8, 8);
check(y[0] > 150 && y[1] > 120 && y[2] < 100, `crystal_yellow 主体应为黄色调（rgb ${y.join(',')}）`);
const r = px('waypoint_crystal_red', 6, 6);
check(r[0] > r[2], `crystal_red 主体应为红色调（rgb ${r.join(',')}）`);
// 模型校验
for (const [tone] of Object.entries(TONES)) {
  for (const part of ['crystal', 'ring', 'orb']) {
    const m = read(`waypoint_${part}_${tone}.json`);
    check(m.elements.length > 0, `waypoint_${part}_${tone}: 不应为空模型`);
    const tex = `waypoint_${part}_${tone}`;
    for (const el of m.elements) {
      for (const fc of Object.values(el.faces)) {
        check(fc.texture === `#${tex}`, `waypoint_${part}_${tone}: 纹理引用应为 ${tex}`);
      }
    }
    if (part === 'orb') {
      check(m.elements[0].from[1] === 14.25 && m.elements[0].to[1] === 15.75, `waypoint_orb_${tone}: 应上移至 y14.25-15.75`);
    }
  }
}

if (errors.length) {
  console.error('❌ 校验失败：');
  for (const e of errors) console.error('   - ' + e);
  process.exit(1);
}
console.log('✅ 全部校验通过（红/绿/黄 × crystal/ring/orb）');
