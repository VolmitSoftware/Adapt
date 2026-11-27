package art.arcane.adapt.pack.bake;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The design's fixed art shipped with the pack project ({@code art/manifest.json} beside the PNGs): controls, pips, seal,
 * HUD cooldown steps, the ledger pages and the plain tome tooltip. Each piece keeps its role text; a piece
 * drawn off its slot names its offset in that text as {@code at (x,y)}.
 */
public final class ArtPieces {
    public static final String ROOT = "/art/arcane/adapt/pack/art/";
    private static final Pattern OFFSET = Pattern.compile("at \\((-?\\d+),\\s*(-?\\d+)\\)");

    private ArtPieces() {
    }

    /** One piece: its path under the art folder, its size, its role, its offset from its slot, and its PNG. */
    public record Piece(String path, int width, int height, String role, int offsetX, int offsetY, byte[] png) {
        /** The path without {@code .png}. */
        public String name() {
            return path.substring(0, path.length() - 4);
        }
    }

    public static List<Piece> builtin() {
        JsonObject manifest = JsonParser.parseString(new String(read("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        List<Piece> pieces = new ArrayList<>();
        for (JsonElement element : manifest.getAsJsonArray("files")) {
            JsonObject file = element.getAsJsonObject();
            String path = file.get("file").getAsString().substring("art/".length());
            String role = file.get("role").getAsString();
            Matcher offset = OFFSET.matcher(role);
            boolean placed = offset.find();
            pieces.add(new Piece(path, file.get("w").getAsInt(), file.get("h").getAsInt(), role, placed ? Integer.parseInt(offset.group(1)) : 0,
                    placed ? Integer.parseInt(offset.group(2)) : 0, read(path)));
        }
        pieces.sort((Piece a, Piece b) -> a.path().compareTo(b.path()));
        return pieces;
    }

    private static byte[] read(String path) {
        try (InputStream in = ArtPieces.class.getResourceAsStream(ROOT + path)) {
            if (in == null) {
                throw new IllegalStateException("missing art " + path);
            }
            return in.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
