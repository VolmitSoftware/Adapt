package art.arcane.adapt.pack.bake;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;

/**
 * Writes {@link Languages#RESOURCE} from Minecraft asset indexes: every locale any of the given clients' indexes lists
 * under {@code minecraft/lang/}, and {@code en_us}, which the client jar carries itself and every locale falls back to.
 * <p>Usage: {@code LanguageIndex <out file> <asset index json> ...}, one index per 26.x client the pack is for.
 */
public final class LanguageIndex {
    private static final String LANG = "minecraft/lang/";
    private static final String JAR_LOCALE = "en_us";

    private LanguageIndex() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: LanguageIndex <out file> <asset index json> ...");
        }
        TreeSet<String> found = new TreeSet<>();
        for (int index = 1; index < args.length; index++) {
            found.addAll(locales(Path.of(args[index])));
        }
        JsonArray list = new JsonArray();
        found.forEach(list::add);
        JsonObject root = new JsonObject();
        root.addProperty("note", "Written by LanguageIndex from the 26.x clients' asset indexes; do not edit by hand.");
        root.add("locales", list);
        Files.writeString(Path.of(args[0]), new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(root) + "\n", StandardCharsets.UTF_8);
    }
    private static TreeSet<String> locales(Path index) throws IOException {
        JsonObject objects = JsonParser.parseString(Files.readString(index, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("objects");
        TreeSet<String> locales = new TreeSet<>();
        locales.add(JAR_LOCALE);
        for (String path : objects.keySet()) {
            if (path.startsWith(LANG) && path.endsWith(".json")) {
                locales.add(path.substring(LANG.length(), path.length() - ".json".length()));
            }
        }
        return locales;
    }
}
