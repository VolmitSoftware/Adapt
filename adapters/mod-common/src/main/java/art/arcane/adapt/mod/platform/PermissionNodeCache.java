package art.arcane.adapt.mod.platform;

import art.arcane.adapt.mod.AdaptModIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The Adapt node list of the previous start, for permission APIs that gather nodes before Adapt's skills exist
 * (NeoForge and Forge). A node first registered this start is gathered from the next restart on.
 */
public final class PermissionNodeCache {
    private static final String FILE = "permission-nodes.txt";
    private static final Pattern NODE = Pattern.compile(AdaptModIdentity.ID + "(\\.[a-z0-9_]+)+");
    private static final List<String> BUILT_IN = List.of(AdaptModIdentity.ID + ".main");

    private PermissionNodeCache() {
    }

    /** Built-in nodes plus the cached ones, each a valid {@code adapt.<path>} node name. */
    public static Set<String> nodes(Path configDirectory) {
        Set<String> names = new TreeSet<>(BUILT_IN);
        Path file = configDirectory.resolve(AdaptModIdentity.ID).resolve(FILE);
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    names.add(line.trim());
                }
            } catch (IOException failure) {
                ModPlatform.log().warn("Could not read " + FILE + ": " + failure.getMessage());
            }
        }
        names.removeIf((String name) -> !NODE.matcher(name).matches());
        return names;
    }

    /** Path below the namespace, as PermissionNode takes it: {@code adapt.use.x} is {@code use.x}. */
    public static String path(String node) {
        return node.substring(AdaptModIdentity.ID.length() + 1);
    }

    /** Writes the nodes registered by this start. */
    public static void remember(Path configDirectory) {
        List<String> names = new ArrayList<>(new TreeSet<>(ModPermissionHost.registeredNodes()));
        names.removeIf((String name) -> !NODE.matcher(name).matches());
        Path directory = configDirectory.resolve(AdaptModIdentity.ID);
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(FILE), names, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            ModPlatform.log().warn("Could not write " + FILE + ": " + failure.getMessage());
        }
    }
}
