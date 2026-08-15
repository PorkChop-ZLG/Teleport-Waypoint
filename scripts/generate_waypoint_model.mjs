/**
 * 传送锚点（Teleport Waypoint）方块模型生成器
 * ------------------------------------------------------------
 * 一次生成三套交付物，保证 UV 完全一致：
 *   1. Minecraft 1.21.1 方块模型 JSON（models/block/waypoint.json）
 *   2. 7 张 16x16 手工像素贴图（textures/block/waypoint_*.png）
 *   3. Blockbench 工程文件（blockbench/waypoint.bbmodel + 贴图副本）
 *
 * 格式依据（均来自本工作区反编译的 1.21.1 源码）：
 *   - BlockModel.java          —— 模型顶层结构（textures/elements/ambientocclusion）
 *   - BlockElement.java        —— from/to/rotation/shade/faces 与 UV 自动填充规则
 *   - BlockElementFace.java    —— cullface/tintindex/texture/uv
 *   - BlockFaceUV.java         —— uv 数组 [u1,v1,u2,v2] 与顶点映射
 *   - FaceInfo.java            —— 各面顶点顺序（决定贴图方向）
 *
 * 运行：node scripts/generate_waypoint_model.mjs
 */
import { deflateSync, inflateSync } from 'node:zlib';
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync, readFileSync, copyFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const ASSETS = join(ROOT, 'src', 'main', 'resources', 'assets', 'teleportwaypoint');
const MODEL_JSON = join(ASSETS, 'models', 'block', 'waypoint.json');
const TEXTURE_DIR = join(ASSETS, 'textures', 'block');
const BB_DIR = join(ROOT, 'blockbench');
const BB_TEXTURE_DIR = join(BB_DIR, 'textures', 'block');

/* ============================ 调色板 ============================ */
const PAL = {
  obsidian:   [10, 12, 18],    // #0a0c12 最深黑曜石
  stoneDark:  [22, 27, 38],    // #161b26
  stoneMid:   [26, 32, 44],    // #1a202c
  stoneMid2:  [29, 37, 49],    // #1d2531
  stoneLight: [35, 42, 56],    // #232a38
  stoneHi:    [46, 55, 72],    // #2e3747
  mortar:     [13, 17, 25],    // #0d1119
  gold:       [217, 164, 65],  // #d9a441
  goldDark:   [138, 100, 32],  // #8a6420
  goldHi:     [240, 199, 106], // #f0c76a
  bandBg:     [14, 42, 56],    // #0e2a38 符文带底色
  cyDeep:     [11, 111, 143],  // #0b6f8f
  cyDark:     [10, 94, 122],   // #0a5e7a
  cyMid:      [20, 150, 184],  // #1496b8
  cy:         [35, 201, 232],  // #23c9e8
  cyHi:       [111, 228, 247], // #6fe4f7
  cyBright:   [185, 245, 255], // #b9f5ff
  cyHot:      [234, 254, 255], // #eafeff
  facDark:    [7, 90, 114],    // #075a72 刻面暗线
  facDark2:   [8, 74, 99],     // #084a63 刻面最暗
  facLight:   [200, 248, 255], // #c8f8ff
  facLite2:   [143, 240, 255], // #8ff0ff
  facMid:     [74, 220, 245],  // #4adcf5
  white:      [255, 255, 255],
};

/* ============================ 画布工具 ============================ */
class Canvas {
  constructor(w = 16, h = 16) {
    this.w = w;
    this.h = h;
    this.buf = new Uint8Array(w * h * 4); // RGBA，默认全 0（透明黑）
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
  /** Bresenham 直线（含两端点） */
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
  /** 切比雪夫（方形）环带：|dx|/|dy| 最大距离 == r */
  chebRing(cx, cy, r, c) {
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      const d = Math.max(Math.abs(x + 0.5 - cx), Math.abs(y + 0.5 - cy));
      if (d >= r && d < r + 1) this.set(x, y, c);
    }
  }
  /** 欧氏距离圆环 */
  eucRing(cx, cy, r, c) {
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
      if (d >= r && d < r + 1) this.set(x, y, c);
    }
  }
  /** 中心距离渐变填充 */
  radial(cx, cy, stops, dither = null) {
    // stops: [[maxDist, color], ...] 按距离从小到大，取第一个满足的
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
      for (const [maxD, c] of stops) {
        if (d < maxD) { this.set(x, y, dither && dither(x, y) ? dither(x, y) : c); break; }
      }
    }
  }
  /** 确定性伪随机点 */
  speckle(seed, c, density = 17) {
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      if ((x * 7 + y * 13 + seed * 31) % density === 0) this.set(x, y, c);
    }
  }
  pixels() { return this.buf; }
}

/* ============================ PNG 编码 ============================ */
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
  ihdr[8] = 8;  // bit depth
  ihdr[9] = 6;  // color type: RGBA
  const raw = Buffer.alloc(h * (1 + w * 4));
  for (let y = 0; y < h; y++) {
    raw[y * (1 + w * 4)] = 0; // filter: none
    raw.set(canvas.buf.subarray(y * w * 4, (y + 1) * w * 4), y * (1 + w * 4) + 1);
  }
  const idat = deflateSync(raw, { level: 9 });
  return Buffer.concat([sig, pngChunk('IHDR', ihdr), pngChunk('IDAT', idat), pngChunk('IEND', Buffer.alloc(0))]);
}

/* ============================ 贴图绘制 ============================ */

/** 底座侧面：上 5 行（行 11-15）为可见符文带，其余为砖石延续 */
function drawBase(c) {
  c.fill(PAL.stoneLight);
  // 不可见区（行 0-10）：砖石
  for (const y of [0, 3, 7]) {
    c.hline(y, 0, 15, PAL.mortar);
    const off = y % 2 === 0 ? 4 : 2;
    for (let x = off; x < 16; x += 4) c.set(x, y, PAL.mortar);
  }
  c.speckle(1, PAL.obsidian, 23);
  c.speckle(2, PAL.stoneHi, 29);
  // 行 11：发光青线（可见带顶部）
  for (let x = 0; x < 16; x++) {
    c.set(x, 11, x % 4 === 1 ? PAL.cyHi : PAL.cy);
  }
  c.set(0, 11, PAL.cyDeep); c.set(15, 11, PAL.cyDeep);
  // 行 12-13：四个 2x2 符文
  c.hline(12, 0, 15, PAL.bandBg);
  c.hline(13, 0, 15, PAL.bandBg);
  for (const gx of [2, 6, 10, 14]) {
    c.set(gx, 12, PAL.cyBright); c.set(gx + 1, 12, PAL.cy);
    c.set(gx, 13, PAL.cy);       c.set(gx + 1, 13, PAL.cyDeep);
  }
  // 行 14：金色饰线
  c.hline(14, 0, 15, PAL.gold);
  for (const x of [3, 7, 11]) c.set(x, 14, PAL.goldHi);
  c.set(0, 14, PAL.goldDark); c.set(15, 14, PAL.goldDark);
  // 行 15：砖石底行
  for (let x = 0; x < 16; x++) c.set(x, 15, x % 4 === 0 ? PAL.mortar : PAL.stoneLight);
  for (const x of [2, 9, 13]) c.set(x, 15, PAL.obsidian);
  for (const x of [6, 11]) c.set(x, 15, PAL.stoneHi);
}

/** 底座顶面：同心符文圆阵 */
function drawBaseTop(c) {
  c.fill(PAL.obsidian);
  c.speckle(3, PAL.stoneDark, 19);
  // 同心方形渐变
  c.radial(8, 8, [
    [1.0, PAL.cyHot],
    [2.0, PAL.cyHi],
    [3.0, PAL.cy],
    [4.0, PAL.cyDeep],
    [5.0, PAL.bandBg],
    [6.0, PAL.stoneDark],
    [7.0, PAL.stoneLight],
    [99, PAL.obsidian],
  ], (x, y) => ((x + y) % 2 === 0 && Math.hypot(x + 0.5 - 8, y + 0.5 - 8) >= 4 && Math.hypot(x + 0.5 - 8, y + 0.5 - 8) < 5) ? PAL.stoneDark : null);
  // 外环描边（r≈6.5）与金色角钉
  c.chebRing(8, 8, 6, PAL.stoneLight);
  c.chebRing(8, 8, 7, PAL.obsidian);
  for (const [gx, gy] of [[1, 1], [14, 1], [1, 14], [14, 14]]) {
    c.rect(gx, gy, gx + 1, gy + 1, PAL.gold);
    c.set(gx, gy, PAL.goldHi);
    c.set(gx + 1, gy + 1, PAL.goldDark);
  }
  // 符文环（r≈4.5 带上的 8 个符文点）
  c.chebRing(8, 8, 4, PAL.cyDeep);
  c.chebRing(8, 8, 5, PAL.bandBg);
  for (const [px, py] of [[10, 10], [10, 5], [5, 10], [5, 5]]) {
    c.rect(px, py, px + 1, py + 1, PAL.cyBright);
    c.set(px, py, PAL.cyHot);
  }
  for (const [px, py] of [[8, 3], [8, 12], [3, 8], [12, 8]]) {
    c.vline(px, py, py + 1, PAL.cyHi);
  }
  // 内环
  c.chebRing(8, 8, 2, PAL.cy);
}

/** 支柱侧面：中央符文柱（可见区 cols 7-8, rows 6-11） */
function drawPillar(c) {
  c.fill(PAL.stoneMid);
  // 竖条柱面纹理
  for (let x = 0; x < 16; x += 4) c.vline(x, 0, 15, PAL.stoneDark);
  for (let x = 2; x < 16; x += 4) c.vline(x, 0, 15, PAL.mortar);
  c.speckle(4, PAL.stoneMid2, 23);
  c.hline(0, 0, 15, PAL.mortar);      // 柱顶暗边
  c.hline(15, 0, 15, PAL.obsidian);   // 柱底暗边
  // 符文面板（cols 6-9 面板，可见核心 cols 7-8）
  c.rect(6, 6, 9, 11, PAL.bandBg);
  c.rect(6, 6, 9, 6, PAL.cyDeep);
  c.rect(6, 11, 9, 11, PAL.cyDeep);
  c.set(6, 7, PAL.cyDeep); c.set(9, 7, PAL.cyDeep);
  c.set(6, 10, PAL.cyDeep); c.set(9, 10, PAL.cyDeep);
  // 竖向符文轴（rows 6-9）
  c.vline(7, 6, 9, PAL.cy);
  c.vline(8, 6, 9, PAL.cyHi);
  c.set(7, 6, PAL.cyBright); c.set(8, 6, PAL.cyBright);
  // 底部字形（rows 10-11）
  c.set(6, 10, PAL.cyMid); c.set(9, 10, PAL.cyMid);
  c.set(7, 10, PAL.cyHi);  c.set(8, 10, PAL.cyHi);
  c.set(6, 11, PAL.cyDeep); c.set(9, 11, PAL.cyDeep);
  c.set(7, 11, PAL.cy);     c.set(8, 11, PAL.cy);
  // 面板外零星小符文（装饰，BB 中可见）
  for (const [x, y] of [[2, 3], [12, 2], [2, 13], [13, 12]]) {
    c.set(x, y, PAL.cyDeep);
  }
}

/** 支柱顶：中心光点 */
function drawPillarTop(c) {
  c.fill(PAL.stoneDark);
  c.speckle(5, PAL.stoneMid, 17);
  c.rect(7, 7, 8, 8, PAL.cyHot); // 2x2 核心
  c.chebRing(8, 8, 1, PAL.cyHi);  // 紧贴核心的光环
  c.chebRing(8, 8, 2, PAL.cyMid);
  c.chebRing(8, 8, 3, PAL.cyDeep);
}

/** 水晶：刻面青色宝石，顶部最亮 */
function drawCrystal(c) {
  const rows = [
    [2, PAL.facLight], [4, PAL.facLite2], [6, PAL.facMid],
    [8, PAL.cy], [10, PAL.cyMid], [12, PAL.cyDeep],
    [14, PAL.cyDark], [16, PAL.facDark2],
  ];
  let ri = 0;
  for (let y = 0; y < 16; y++) {
    while (ri < rows.length && y >= rows[ri][0]) ri++;
    c.hline(y, 0, 15, rows[Math.min(ri, rows.length - 1)][1]);
  }
  // 刻面边界（深色对角线）
  c.line(0, 5, 11, 16, PAL.facDark);
  c.line(5, 0, 16, 11, PAL.facDark);
  c.line(0, 11, 5, 16, PAL.facDark);
  c.line(11, 0, 16, 5, PAL.facDark);
  // 顶部高光斜带（左上）
  for (let i = 0; i < 7; i++) {
    c.set(i, i, i < 4 ? PAL.white : PAL.facLight);
    c.set(i + 1, i, PAL.facLight);
  }
  // 星星闪光
  for (const [x, y] of [[5, 3], [9, 5], [3, 7], [11, 9]]) c.set(x, y, PAL.white);
  c.set(10, 2, PAL.cyHot); c.set(6, 4, PAL.cyHot);
  // 底部暗行
  c.hline(14, 0, 15, PAL.facDark2);
  c.hline(15, 0, 15, PAL.facDark);
  c.speckle(6, PAL.cyDeep, 29);
}

/** 能量环：中央发光带（rows 7-9）+ 黑曜石边框 */
function drawRing(c) {
  c.fill(PAL.obsidian);
  // 边框上的稀疏符文
  c.speckle(7, PAL.stoneDark, 15);
  for (const [x, y] of [[3, 2], [12, 13], [5, 14], [10, 1], [1, 12], [14, 3]]) c.set(x, y, PAL.cyDeep);
  // 发光带
  c.hline(6, 0, 15, PAL.bandBg);
  c.hline(7, 0, 15, PAL.cyDeep);
  c.hline(9, 0, 15, PAL.cyMid);
  c.hline(10, 0, 15, PAL.bandBg);
  for (let x = 0; x < 16; x++) {
    c.set(x, 8, x % 4 === 2 ? PAL.cyBright : PAL.cyHi);
  }
  c.set(4, 8, PAL.cyHot); c.set(9, 8, PAL.cyHot); c.set(14, 8, PAL.cyHot);
  // 竖向符文刻度（穿过光带）
  for (const x of [3, 8, 13]) {
    c.set(x, 7, PAL.cy); c.set(x, 9, PAL.cy);
    c.set(x, 8, PAL.cyHot);
  }
}

/** 核心光球：径向辉光 */
function drawOrb(c) {
  c.fill(PAL.obsidian);
  c.radial(8, 8, [
    [1.0, PAL.white],
    [2.0, PAL.cyHot],
    [3.0, PAL.cyBright],
    [4.0, PAL.cyHi],
    [5.0, PAL.cy],
    [6.0, PAL.cyMid],
    [7.0, PAL.cyDeep],
    [99, PAL.obsidian],
  ], (x, y) => (Math.hypot(x + 0.5 - 8, y + 0.5 - 8) >= 6 && (x + y) % 2 === 0) ? PAL.cyDark : null);
}

/* ============================ 元素与 UV ============================ */

/** Blockbench 默认 UV（与 vanilla uvsByFace 侧面一致，顶/底用 BB 公式；全部显式写出） */
function defaultUV(face, from, to) {
  switch (face) {
    case 'down':  return [from[0], from[2], to[0], to[2]];
    case 'up':    return [from[0], 16 - to[2], to[0], 16 - from[2]];
    case 'north': return [16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]];
    case 'south': return [from[0], 16 - to[1], to[0], 16 - from[1]];
    case 'west':  return [from[2], 16 - to[1], to[2], 16 - from[1]];
    case 'east':  return [16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]];
  }
}

const TEX = {
  base: 'waypoint_base',
  baseTop: 'waypoint_base_top',
  pillar: 'waypoint_pillar',
  pillarTop: 'waypoint_pillar_top',
  crystal: 'waypoint_crystal',
  ring: 'waypoint_ring',
  orb: 'waypoint_orb',
};

const SIDES = ['north', 'south', 'west', 'east'];

/**
 * 元素定义
 * faces: { dir: { tex, uv?, cull? } }  —— uv 缺省时用 defaultUV
 */
const ELEMENTS = [
  // 底座
  {
    name: '底座', from: [0, 0, 0], to: [16, 5, 16], shade: true,
    faces: {
      down:  { tex: TEX.base, cull: 'down' },
      up:    { tex: TEX.baseTop },
      north: { tex: TEX.base, cull: 'north' },
      south: { tex: TEX.base, cull: 'south' },
      west:  { tex: TEX.base, cull: 'west' },
      east:  { tex: TEX.base, cull: 'east' },
    },
  },
  // 四根支柱（顶/底隐藏：顶被水晶压住，底贴底座）
  ...[
    [[1, 5, 1], [3, 10, 3], '支柱-西北'],
    [[13, 5, 1], [15, 10, 3], '支柱-东北'],
    [[1, 5, 13], [3, 10, 15], '支柱-西南'],
    [[13, 5, 13], [15, 10, 15], '支柱-东南'],
  ].map(([from, to, name]) => ({
    name, from, to, shade: true,
    faces: Object.fromEntries(SIDES.map((d) => [d, { tex: TEX.pillar, uv: [7, 6, 9, 11] }])),
  })),
  // 四颗柱顶水晶
  ...[
    [[0.75, 10, 0.75], [3.25, 12, 3.25], '柱顶水晶-西北'],
    [[12.75, 10, 0.75], [15.25, 12, 3.25], '柱顶水晶-东北'],
    [[0.75, 10, 12.75], [3.25, 12, 15.25], '柱顶水晶-西南'],
    [[12.75, 10, 12.75], [15.25, 12, 15.25], '柱顶水晶-东南'],
  ].map(([from, to, name]) => ({
    name, from, to, shade: false,
    faces: {
      ...Object.fromEntries(SIDES.map((d) => [d, { tex: TEX.crystal, uv: [6, 2, 10, 4] }])),
      up: { tex: TEX.crystal, uv: [6.5, 0.5, 9.5, 3.5] },
    },
  })),
  // 能量环（四根浮空环梁）
  {
    name: '能量环-北', from: [3.5, 8.5, 3], to: [12.5, 9.5, 4], shade: false,
    faces: {
      up: { tex: TEX.ring, uv: [3.5, 7, 12.5, 9] },
      down: { tex: TEX.ring, uv: [3.5, 7, 12.5, 9] },
      north: { tex: TEX.ring, uv: [3.5, 7, 12.5, 9] },
      south: { tex: TEX.ring, uv: [3.5, 7, 12.5, 9] },
      west: { tex: TEX.ring, uv: [12, 7, 13, 9] },
      east: { tex: TEX.ring, uv: [12, 7, 13, 9] },
    },
  },
  {
    name: '能量环-南', from: [3.5, 8.5, 12], to: [12.5, 9.5, 13], shade: false,
    faces: {
      up: { tex: TEX.ring, uv: [3.5, 7, 12.5, 9] },
      down: { tex: TEX.ring, uv: [3.5, 7, 12.5, 9] },
      north: { tex: TEX.ring, uv: [3.5, 7, 12.5, 9] },
      south: { tex: TEX.ring, uv: [3.5, 7, 12.5, 9] },
      west: { tex: TEX.ring, uv: [12, 7, 13, 9] },
      east: { tex: TEX.ring, uv: [12, 7, 13, 9] },
    },
  },
  {
    name: '能量环-西', from: [3, 8.5, 3.5], to: [4, 9.5, 12.5], shade: false,
    faces: {
      up: { tex: TEX.ring, uv: [7, 3.5, 9, 12.5] },
      down: { tex: TEX.ring, uv: [7, 3.5, 9, 12.5] },
      west: { tex: TEX.ring, uv: [7, 3.5, 9, 12.5] },
      east: { tex: TEX.ring, uv: [7, 3.5, 9, 12.5] },
      north: { tex: TEX.ring, uv: [12, 7, 13, 9] },
      south: { tex: TEX.ring, uv: [12, 7, 13, 9] },
    },
  },
  {
    name: '能量环-东', from: [12, 8.5, 3.5], to: [13, 9.5, 12.5], shade: false,
    faces: {
      up: { tex: TEX.ring, uv: [7, 3.5, 9, 12.5] },
      down: { tex: TEX.ring, uv: [7, 3.5, 9, 12.5] },
      east: { tex: TEX.ring, uv: [7, 3.5, 9, 12.5] },
      west: { tex: TEX.ring, uv: [7, 3.5, 9, 12.5] },
      north: { tex: TEX.ring, uv: [12, 7, 13, 9] },
      south: { tex: TEX.ring, uv: [12, 7, 13, 9] },
    },
  },
  // 中央晶核：下部大棱柱（45° 旋转）——顶/底之间留出上部棱柱位置
  {
    name: '晶核-下', from: [6, 7, 6], to: [10, 11, 10], rot: { axis: 'y', angle: 45 }, shade: false,
    faces: {
      ...Object.fromEntries(SIDES.map((d) => [d, { tex: TEX.crystal, uv: [6, 0, 10, 4] }])),
      up: { tex: TEX.crystal, uv: [6, 0, 10, 4] },
      down: { tex: TEX.crystal, uv: [6, 12, 10, 16] },
    },
  },
  {
    name: '晶核-上', from: [7, 11, 7], to: [9, 13, 9], rot: { axis: 'y', angle: 45 }, shade: false,
    faces: {
      ...Object.fromEntries(SIDES.map((d) => [d, { tex: TEX.crystal, uv: [7, 0, 9, 2] }])),
      up: { tex: TEX.crystal, uv: [7, 0, 9, 2] },
      // down 省略：与晶核-下顶面共面，避免 z-fighting
    },
  },
  // 顶部悬浮核心光球
  {
    name: '核心光球', from: [7.25, 13.25, 7.25], to: [8.75, 14.75, 8.75], shade: false,
    faces: Object.fromEntries(
      ['down', 'up', ...SIDES].map((d) => [d, { tex: TEX.orb, uv: [7, 7, 9, 9] }]),
    ),
  },
];

/* ============================ 生成模型 JSON ============================ */

function toJsonElement(el) {
  const faces = {};
  for (const [dir, f] of Object.entries(el.faces)) {
    faces[dir] = {
      uv: f.uv ?? defaultUV(dir, el.from, el.to),
      texture: `#${f.tex}`,
    };
    if (f.cull) faces[dir].cullface = f.cull;
  }
  const out = { from: el.from, to: el.to, faces };
  if (el.rot) out.rotation = { origin: [8, 8, 8], axis: el.rot.axis, angle: el.rot.angle, rescale: false };
  out.shade = el.shade;
  return out;
}

const textureMap = {};
for (const key of Object.values(TEX)) textureMap[key] = `teleportwaypoint:block/${key}`;

const modelJson = {
  credit: 'Made with Blockbench',
  ambientocclusion: false,
  textures: {
    particle: textureMap[TEX.base],
    ...textureMap,
  },
  elements: ELEMENTS.map(toJsonElement),
};

/* ============================ 生成 Blockbench 工程 ============================ */

const texList = Object.values(TEX);
const texIndex = Object.fromEntries(texList.map((t, i) => [t, i]));
const uuids = {}; // element name -> uuid（outliner 引用）

const bbElements = ELEMENTS.map((el) => {
  const uuid = randomUUID();
  uuids[el.name] = uuid;
  const faces = {};
  for (const [dir, f] of Object.entries(el.faces)) {
    faces[dir] = { uv: f.uv ?? defaultUV(dir, el.from, el.to), texture: texIndex[f.tex] };
  }
  return {
    name: el.name,
    box_uv: false,
    resolved: true,
    from: el.from,
    to: el.to,
    rotation: {
      origin: [8, 8, 8],
      axis: el.rot ? el.rot.axis : 'y',
      angle: el.rot ? el.rot.angle : 0,
      rescale: false,
    },
    faces,
    type: 'cube',
    uuid,
  };
});

const GROUP_COLORS = {
  '底座': 'FFD9A441',
  '支柱': 'FF8A6420',
  '能量环': 'FF23C9E8',
  '晶核': 'FF6FE4F7',
};
const groupOf = (name) =>
  name.startsWith('底座') ? '底座' : name.startsWith('支柱') || name.startsWith('柱顶') ? '支柱' : name.startsWith('能量环') ? '能量环' : '晶核';

const groupUuid = {};
const outlinerGroups = {};
for (const el of ELEMENTS) {
  const g = groupOf(el.name);
  if (!outlinerGroups[g]) {
    groupUuid[g] = randomUUID();
    outlinerGroups[g] = {
      name: g,
      origin: [8, 8, 8],
      color: parseInt(GROUP_COLORS[g], 16),
      uuid: groupUuid[g],
      children: [],
      export: true,
    };
  }
  outlinerGroups[g].children.push({ name: el.name, uuid: uuids[el.name], export: true });
}

const bbTextures = texList.map((t) => ({
  path: `textures/block/${t}.png`,
  name: t,
  source: 'file',
  uv_width: 16,
  uv_height: 16,
  particles: false,
  render_mode: 'default',
  culling: false,
  visibility: true,
}));

const bbModel = {
  meta: { format_version: '4.10', model_format: 'java_block', box_uv: false },
  name: 'waypoint',
  model_identifier: 'teleportwaypoint:block/waypoint',
  resolution: { width: 16, height: 16 },
  elements: bbElements,
  outliner: Object.values(outlinerGroups),
  textures: bbTextures,
  animations: [],
};

/* ============================ 写入文件 ============================ */

const canvases = {
  [TEX.base]: drawBase,
  [TEX.baseTop]: drawBaseTop,
  [TEX.pillar]: drawPillar,
  [TEX.pillarTop]: drawPillarTop,
  [TEX.crystal]: drawCrystal,
  [TEX.ring]: drawRing,
  [TEX.orb]: drawOrb,
};

mkdirSync(TEXTURE_DIR, { recursive: true });
mkdirSync(BB_TEXTURE_DIR, { recursive: true });
mkdirSync(dirname(MODEL_JSON), { recursive: true });

const pngs = {};
for (const [name, draw] of Object.entries(canvases)) {
  const c = new Canvas(16, 16);
  draw(c);
  const png = encodePNG(c);
  pngs[name] = { png, canvas: c };
  writeFileSync(join(TEXTURE_DIR, `${name}.png`), png);
  writeFileSync(join(BB_TEXTURE_DIR, `${name}.png`), png);
}
writeFileSync(MODEL_JSON, JSON.stringify(modelJson, null, 2) + '\n');
writeFileSync(join(BB_DIR, 'waypoint.bbmodel'), JSON.stringify(bbModel, null, 2) + '\n');

/* ============================ 自检 ============================ */

const errors = [];
const check = (ok, msg) => { if (!ok) errors.push(msg); };

// 1) 模型 JSON 结构校验
check(modelJson.elements.length === 16, `模型应有 16 个元素，实际 ${modelJson.elements.length}`);
for (const el of modelJson.elements) {
  check(Array.isArray(el.from) && el.from.length === 3 && Array.isArray(el.to) && el.to.length === 3,
    `${el.name}: from/to 必须为 3 元数组`);
  for (let i = 0; i < 3; i++) {
    check(el.from[i] >= -16 && el.from[i] <= 32 && el.to[i] >= -16 && el.to[i] <= 32,
      `${el.name}: 坐标越界 (-16..32)`);
    check(el.from[i] < el.to[i], `${el.name}: from 必须小于 to（轴 ${i}）`);
  }
  const faceCount = Object.keys(el.faces).length;
  check(faceCount >= 1 && faceCount <= 6, `${el.name}: 面数必须 1..6，实际 ${faceCount}`);
  for (const [dir, f] of Object.entries(el.faces)) {
    check(['down', 'up', 'north', 'south', 'west', 'east'].includes(dir), `${el.name}: 未知面 ${dir}`);
    check(f.texture.startsWith('#') && textureMap[f.texture.slice(1)], `${el.name}.${dir}: 纹理引用 ${f.texture} 无效`);
    check(Array.isArray(f.uv) && f.uv.length === 4, `${el.name}.${dir}: uv 必须为 4 元数组`);
    for (const v of f.uv) check(v >= 0 && v <= 16, `${el.name}.${dir}: uv 值 ${v} 越界 (0..16)`);
  }
  if (el.rotation) {
    check([0, 22.5, -22.5, 45, -45].includes(el.rotation.angle), `${el.name}: 旋转角 ${el.rotation.angle} 非法`);
    check(['x', 'y', 'z'].includes(el.rotation.axis), `${el.name}: 旋转轴非法`);
    check(Array.isArray(el.rotation.origin) && el.rotation.origin.length === 3, `${el.name}: rotation.origin 非法`);
  }
}
// 2) bbmodel 校验
check(bbModel.meta.model_format === 'java_block', 'bbmodel: model_format 必须为 java_block');
const uuidSet = new Set(bbElements.map((e) => e.uuid));
check(uuidSet.size === bbElements.length, 'bbmodel: 元素 uuid 必须唯一');
for (const el of bbElements) {
  for (const [dir, f] of Object.entries(el.faces)) {
    check(Number.isInteger(f.texture) && f.texture >= 0 && f.texture < bbTextures.length,
      `bbmodel ${el.name}.${dir}: 纹理索引 ${f.texture} 越界`);
  }
}
const outlinerRefs = bbModel.outliner.flatMap((g) => g.children.map((c) => c.uuid));
check(outlinerRefs.every((u) => uuidSet.has(u)), 'bbmodel: outliner 引用了不存在的元素 uuid');
check(bbTextures.every((t) => t.uv_width === 16 && t.uv_height === 16), 'bbmodel: 纹理应为 16x16');

// 3) PNG 回读校验
function decodePNG(buf) {
  check(buf.readUInt32BE(0) === 0x89504e47, 'PNG 魔数错误');
  let off = 8;
  let width = 0, height = 0, idat = [];
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString('ascii', off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0); height = data.readUInt32BE(4);
      check(data[8] === 8 && data[9] === 6, 'PNG 必须为 8bit RGBA');
    } else if (type === 'IDAT') idat.push(data);
    off += 12 + len;
  }
  check(width === 16 && height === 16, `PNG 尺寸应为 16x16，实际 ${width}x${height}`);
  return { width, height, raw: inflateSync(Buffer.concat(idat)) };
}
for (const [name, { png, canvas }] of Object.entries(pngs)) {
  const { width, height, raw } = decodePNG(png);
  check(raw.length === height * (1 + width * 4), `${name}.png: 解码数据长度不符`);
  // 与画布逐像素比对
  let same = true;
  for (let i = 0; i < canvas.buf.length; i++) {
    if (raw[i + Math.floor(i / (width * 4)) + 1] !== canvas.buf[i]) { same = false; break; }
  }
  check(same, `${name}.png: 解码像素与画布不一致`);
}
// 关键像素断言（贴图内容抽查）
const px = (name, x, y) => {
  const { canvas } = pngs[name];
  const i = (y * 16 + x) * 4;
  return [canvas.buf[i], canvas.buf[i + 1], canvas.buf[i + 2]];
};
const near = (a, b, tol = 60) => a.every((v, i) => Math.abs(v - b[i]) <= tol);
check(near(px(TEX.orb, 7, 7), PAL.white, 10), 'orb 中心应为白色');
check(near(px(TEX.baseTop, 7, 7), PAL.cyHot, 40), 'baseTop 中心应为亮青');
check(near(px(TEX.crystal, 1, 1), PAL.white, 30), 'crystal 左上应为白色高光');
check(near(px(TEX.base, 7, 11), PAL.cy, 40), 'base 行 11 应为青色发光线');
check(near(px(TEX.ring, 7, 8), PAL.cyHi, 40), 'ring 行 8 应为发光主线');

if (errors.length) {
  console.error('❌ 校验失败：');
  for (const e of errors) console.error('   - ' + e);
  process.exit(1);
}

console.log('✅ 全部校验通过');
console.log(`   模型 JSON : ${MODEL_JSON}`);
console.log(`   贴图      : ${TEXTURE_DIR}\\waypoint_*.png（${Object.keys(pngs).length} 张）`);
console.log(`   Blockbench: ${join(BB_DIR, 'waypoint.bbmodel')}`);
console.log(`   贴图副本  : ${BB_TEXTURE_DIR}`);
