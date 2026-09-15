import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Inflates one chunk out of a region file and reports top-level NBT key names plus a few scalars, so a
 * partial (proto) chunk can be told apart from a complete LevelChunk.
 */
public final class ChunkKeys {

    public static void main(String[] args) throws IOException {
        Path file = Path.of(args[0]);
        int idx = Integer.parseInt(args[1]);
        byte[] b = Files.readAllBytes(file);
        int off = ((b[idx * 4] & 0xFF) << 16) | ((b[idx * 4 + 1] & 0xFF) << 8) | (b[idx * 4 + 2] & 0xFF);
        if (off == 0) {
            System.out.println("empty slot");
            return;
        }
        int byteOff = off * 4096;
        int len = ((b[byteOff] & 0xFF) << 24) | ((b[byteOff + 1] & 0xFF) << 16)
                | ((b[byteOff + 2] & 0xFF) << 8) | (b[byteOff + 3] & 0xFF);
        int comp = b[byteOff + 4] & 0xFF;
        byte[] payload = new byte[len - 1];
        System.arraycopy(b, byteOff + 5, payload, 0, len - 1);
        InputStream in = switch (comp) {
            case 1 -> new GZIPInputStream(new java.io.ByteArrayInputStream(payload));
            case 2 -> new InflaterInputStream(new java.io.ByteArrayInputStream(payload));
            default -> new java.io.ByteArrayInputStream(payload);
        };
        byte[] nbt;
        try (DataInputStream dis = new DataInputStream(in);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            dis.transferTo(out);
            nbt = out.toByteArray();
        }
        System.out.println("inflated=" + nbt.length + " comp=" + comp);
        if (nbt.length == 0) {
            System.out.println("EMPTY PAYLOAD");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int[] pos = {0};
        // root compound
        int rootType = nbt[pos[0]++] & 0xFF;
        if (rootType != 10) {
            System.out.println("root is not a compound: type=" + rootType);
            return;
        }
        String rootName = readString(nbt, pos);
        System.out.println("root type=10 name='" + rootName + "'");
        while (true) {
            int t = nbt[pos[0]] & 0xFF;
            if (t == 0) {
                break;
            }
            pos[0]++;
            String name = readString(nbt, pos);
            sb.append(name).append('(').append(t).append(") ");
            skip(nbt, pos, t);
        }
        System.out.println("top-level keys: " + sb);
    }

    private static String readString(byte[] b, int[] p) {
        int l = ((b[p[0]] & 0xFF) << 8) | (b[p[0] + 1] & 0xFF);
        p[0] += 2;
        String s = new String(b, p[0], l, java.nio.charset.StandardCharsets.UTF_8);
        p[0] += l;
        return s;
    }

    private static void skip(byte[] b, int[] p, int type) {
        switch (type) {
            case 1 -> p[0] += 1;
            case 2 -> {
                int l = ((b[p[0]] & 0xFF) << 8) | (b[p[0] + 1] & 0xFF);
                p[0] += 2 + l;
            }
            case 3 -> p[0] += 4;
            case 4 -> p[0] += 8;
            case 5 -> p[0] += 4;
            case 6 -> p[0] += 8;
            case 7 -> {
                int l = ((b[p[0]] & 0xFF) << 24) | ((b[p[0] + 1] & 0xFF) << 16) | ((b[p[0] + 2] & 0xFF) << 8) | (b[p[0] + 3] & 0xFF);
                p[0] += 4 + l;
            }
            case 8 -> {
                int l = ((b[p[0]] & 0xFF) << 8) | (b[p[0] + 1] & 0xFF);
                p[0] += 2 + l;
            }
            case 9 -> {
                int inner = b[p[0]] & 0xFF;
                int l = ((b[p[0] + 1] & 0xFF) << 24) | ((b[p[0] + 2] & 0xFF) << 16) | ((b[p[0] + 3] & 0xFF) << 8) | (b[p[0] + 4] & 0xFF);
                p[0] += 5;
                for (int i = 0; i < l; i++) {
                    skip(b, p, inner);
                }
            }
            case 10 -> {
                while ((b[p[0]] & 0xFF) != 0) {
                    int t = b[p[0]] & 0xFF;
                    p[0]++;
                    readString(b, p);
                    skip(b, p, t);
                }
                p[0]++;
            }
            case 11 -> {
                int l = ((b[p[0]] & 0xFF) << 24) | ((b[p[0] + 1] & 0xFF) << 16) | ((b[p[0] + 2] & 0xFF) << 8) | (b[p[0] + 3] & 0xFF);
                p[0] += 4 + 4 * l;
            }
            case 12 -> {
                int l = ((b[p[0]] & 0xFF) << 24) | ((b[p[0] + 1] & 0xFF) << 16) | ((b[p[0] + 2] & 0xFF) << 8) | (b[p[0] + 3] & 0xFF);
                p[0] += 4 + 8 * l;
            }
            default -> throw new IllegalStateException("bad tag type " + type + " at " + p[0]);
        }
    }
}
