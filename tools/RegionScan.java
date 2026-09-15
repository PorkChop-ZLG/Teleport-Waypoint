import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Standalone region-file scanner. Reads the location table, inflates every present chunk and reports
 * whether its NBT mentions the waypoint block, so we can tell whether the mod wrote anything into a
 * world that later froze.
 */
public final class RegionScan {

    public static void main(String[] args) throws IOException {
        Path file = Path.of(args[0]);
        int originChunkX = Integer.parseInt(args[1]);
        int originChunkZ = Integer.parseInt(args[2]);
        byte[] b = Files.readAllBytes(file);
        System.out.println("file=" + file.getFileName() + " bytes=" + b.length);

        int present = 0;
        for (int idx = 0; idx < 1024; idx++) {
            int off = ((b[idx * 4] & 0xFF) << 16) | ((b[idx * 4 + 1] & 0xFF) << 8) | (b[idx * 4 + 2] & 0xFF);
            int sectors = b[idx * 4 + 3] & 0xFF;
            if (off == 0) {
                continue;
            }
            present++;
            int byteOff = off * 4096;
            int len = ((b[byteOff] & 0xFF) << 24) | ((b[byteOff + 1] & 0xFF) << 16)
                    | ((b[byteOff + 2] & 0xFF) << 8) | (b[byteOff + 3] & 0xFF);
            int comp = b[byteOff + 4] & 0xFF;
            int chunkX = originChunkX + (idx % 32);
            int chunkZ = originChunkZ + (idx / 32);
            System.out.printf("  chunk(%d,%d) idx=%d off=%d sectors=%d len=%d comp=%d%n",
                    chunkX, chunkZ, idx, byteOff, sectors, len, comp);

            int dataStart = byteOff + 5;
            int dataLen = len - 1;
            if (dataLen <= 0 || dataStart + dataLen > b.length) {
                System.out.println("    -> TRUNCATED (declared data would run past end of file)");
                continue;
            }
            byte[] payload = new byte[dataLen];
            System.arraycopy(b, dataStart, payload, 0, dataLen);
            byte[] nbt;
            try {
                nbt = inflate(payload, comp);
            } catch (Exception e) {
                System.out.println("    -> INFLATE FAILED: " + e);
                continue;
            }
            System.out.println("    -> inflated " + nbt.length + " bytes");
            String text = new String(nbt, java.nio.charset.StandardCharsets.ISO_8859_1);
            System.out.println("    mentions teleportwaypoint=" + text.contains("teleportwaypoint")
                    + " waypoint=" + text.contains("waypoint")
                    + " ancient_city=" + text.contains("ancient_city")
                    + " structure_starts=" + text.contains("structure_starts")
                    + " block_entities=" + text.contains("block_entities"));
            int at = text.indexOf("waypoint");
            if (at >= 0) {
                int from = Math.max(0, at - 40);
                int to = Math.min(text.length(), at + 60);
                System.out.println("    context: " + text.substring(from, to).replaceAll("[^\\x20-\\x7e]", "."));
            }
        }
        System.out.println("present chunks: " + present);
    }

    private static byte[] inflate(byte[] payload, int comp) throws IOException {
        // Region chunks: 1 = gzip, 2 = zlib, 3 = uncompressed
        InputStream in = switch (comp) {
            case 1 -> new GZIPInputStream(new java.io.ByteArrayInputStream(payload));
            case 2 -> new InflaterInputStream(new java.io.ByteArrayInputStream(payload));
            case 3 -> new java.io.ByteArrayInputStream(payload);
            default -> throw new IOException("unknown compression " + comp);
        };
        try (DataInputStream dis = new DataInputStream(in);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            dis.transferTo(out);
            return out.toByteArray();
        }
    }
}
