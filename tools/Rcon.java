import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal RCON client with diagnostics, used to drive a dedicated server from a script. */
public final class Rcon {

    public static void main(String[] args) throws IOException {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String password = args[2];

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(20000);
            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Vanilla/NeoForge constants: auth is type 3, command is type 2 (see RconClient).
            write(out, 3, 1, password);
            byte[] resp = readPacket(in);
            System.out.println("AUTH bytes=" + resp.length + " id=" + id(resp) + " type=" + type(resp)
                    + " body=[" + body(resp) + "]");

            int id = 2;
            for (int i = 3; i < args.length; i++) {
                System.out.println(">>> " + args[i]);
                write(out, 2, id++, args[i]);
                byte[] r = readPacket(in);
                System.out.println("<<< type=" + type(r) + " body=[" + body(r) + "]");
            }
        }
    }

    private static void write(OutputStream out, int type, int id, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(body.length + 14).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(body.length + 10);
        buf.putInt(id);
        buf.putInt(type);
        buf.put(body);
        buf.put((byte) 0).put((byte) 0);
        out.write(buf.array());
        out.flush();
    }

    private static byte[] readPacket(DataInputStream in) throws IOException {
        int len = readLeInt(in);
        byte[] packet = new byte[len];
        in.readFully(packet);
        return packet;
    }

    private static int id(byte[] p) {
        return leInt(p, 0);
    }

    private static int type(byte[] p) {
        return leInt(p, 4);
    }

    private static String body(byte[] p) {
        int len = p.length - 10;
        return len <= 0 ? "" : new String(p, 8, len, StandardCharsets.UTF_8);
    }

    private static int leInt(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static int readLeInt(DataInputStream in) throws IOException {
        return in.readUnsignedByte() | (in.readUnsignedByte() << 8) | (in.readUnsignedByte() << 16)
                | (in.readUnsignedByte() << 24);
    }
}
