// 临时工具：将贴图渲染为 ASCII 预览（检查像素画效果）
import { readFileSync } from 'node:fs';
import { inflateSync } from 'node:zlib';
import { join } from 'node:path';

const dir = 'src/main/resources/assets/teleportwaypoint/textures/block';
const names = ['waypoint_base', 'waypoint_base_top', 'waypoint_pillar', 'waypoint_pillar_top', 'waypoint_crystal', 'waypoint_ring', 'waypoint_orb'];

function decode(buf) {
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

// 亮度字符映射
const CHARS = ' .:-=+*#%@';
function lum(r, g, b) { return 0.299 * r + 0.587 * g + 0.114 * b; }
function charOf(r, g, b) {
  const l = lum(r, g, b) / 255;
  return CHARS[Math.min(CHARS.length - 1, Math.floor(l * CHARS.length))];
}

for (const name of names) {
  const { w, h, raw } = decode(readFileSync(join(dir, `${name}.png`)));
  console.log(`\n=== ${name} (${w}x${h}) ===`);
  for (let y = 0; y < h; y++) {
    let row = '';
    for (let x = 0; x < w; x++) {
      const i = y * (1 + w * 4) + 1 + x * 4;
      row += charOf(raw[i], raw[i + 1], raw[i + 2]);
    }
    console.log(row);
  }
}
