package art.arcane.adapt.client.ui;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Resource packs as a map of id to bytes; {@link #reload()} stands for the client reloading its packs. */
public final class FakeResources implements UiResources {
    private final Map<String, byte[]> files = new HashMap<>();
    private int revision = 1;
    private int opens;

    public static FakeResources of(String id, byte[] content) {
        return new FakeResources().put(id, content);
    }

    /** The first bytes of a PNG of this size: signature and IHDR. */
    public static byte[] png(int width, int height) {
        byte[] header = new byte[33];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        System.arraycopy(signature, 0, header, 0, signature.length);
        writeInt(header, 16, width);
        writeInt(header, 20, height);
        header[24] = 8;
        header[25] = 6;
        return header;
    }

    public static byte[] text(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public FakeResources put(String id, byte[] content) {
        files.put(id, content);
        return this;
    }

    public FakeResources remove(String id) {
        files.remove(id);
        return this;
    }

    public void reload() {
        revision++;
    }

    public int opens() {
        return opens;
    }

    @Override
    public InputStream open(String id) {
        opens++;
        byte[] content = files.get(id);
        return content == null ? null : new ByteArrayInputStream(content);
    }

    @Override
    public int revision() {
        return revision;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
