package art.arcane.adapt.pack;

import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.asset.AssetEntry;
import art.arcane.adapt.engine.asset.AdaptAssetCatalog;
import art.arcane.adapt.engine.asset.SkillLook;
import art.arcane.adapt.engine.asset.SkillLooks;
import art.arcane.adapt.platform.value.Id;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Real engine catalogs for tests, fed from folders laid out like a jar or from files held in memory. */
public final class TestCatalogs {
    private TestCatalogs() {
    }

    /** The resources folder the build hands the tests as {@code adapt.pack.assets}. */
    public static Path builtinRoot() {
        String root = System.getProperty("adapt.pack.assets");
        if (root == null) {
            throw new IllegalStateException("adapt.pack.assets is not set");
        }
        return Path.of(root);
    }

    /** A catalog with no registered skills holding the built-in {@code adapt} bundle. */
    public static AdaptAssetCatalog builtin() {
        return builtin(List.of());
    }

    /** A catalog with {@code skills} registered holding the built-in {@code adapt} bundle. */
    public static AdaptAssetCatalog builtin(List<SkillLook> skills) {
        AdaptAssetCatalog catalog = empty(skills);
        catalog.install(folder(builtinRoot().resolve("assets").resolve(Id.ADAPT)), Id.ADAPT);
        return catalog;
    }

    /**
     * A catalog with {@code skills} registered holding the built-in {@code adapt} bundle with only the theme kits and
     * atlases of {@code themes}, to keep bakes short.
     */
    public static AdaptAssetCatalog builtin(List<SkillLook> skills, Set<String> themes) {
        AdaptAssetCatalog catalog = empty(skills);
        catalog.install(builtinBundle(themes), Id.ADAPT);
        return catalog;
    }

    /** The built-in {@code adapt} bundle with only the theme kits and atlases of {@code themes}. */
    public static AssetBundle builtinBundle(Set<String> themes) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> file : read(builtinRoot().resolve("assets").resolve(Id.ADAPT)).entrySet()) {
            String path = file.getKey();
            String theme = path.startsWith("themes/") ? path.substring("themes/".length())
                    : path.startsWith("textures/gui/theme/") ? path.substring("textures/gui/theme/".length()) : null;
            if (theme == null || themes.contains(theme.substring(0, theme.lastIndexOf('.')))) {
                files.put(path, file.getValue());
            }
        }
        return memory(Id.ADAPT, files);
    }

    /** A catalog with {@code skills} registered and nothing installed; a skipped entry fails the test. */
    public static AdaptAssetCatalog empty(List<SkillLook> skills) {
        return new AdaptAssetCatalog(looks(skills), (String problem) -> {
            throw new IllegalStateException(problem);
        });
    }

    /** The files below {@code root}, a folder named after the bundle's namespace. */
    public static AssetBundle folder(Path root) {
        return memory(root.getFileName().toString(), read(root));
    }

    private static Map<String, byte[]> read(Path root) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                files.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return files;
    }

    /** A bundle of {@code files} by their path below {@code assets/<namespace>/}. */
    public static AssetBundle memory(String namespace, Map<String, byte[]> files) {
        Map<String, byte[]> held = new LinkedHashMap<>(files);
        List<AssetEntry> entries = new ArrayList<>();
        for (String path : held.keySet()) {
            entries.add(AssetBundle.classify(namespace, path));
        }
        List<AssetEntry> listed = List.copyOf(entries);
        return new AssetBundle() {
            @Override
            public String namespace() {
                return namespace;
            }

            @Override
            public List<AssetEntry> entries() {
                return listed;
            }

            @Override
            public byte[] read(AssetEntry entry) {
                byte[] bytes = held.get(entry.path());
                if (bytes == null) {
                    throw new IllegalArgumentException("no file " + entry.path());
                }
                return bytes;
            }
        };
    }

    private static SkillLooks looks(List<SkillLook> skills) {
        List<SkillLook> registered = List.copyOf(skills);
        return new SkillLooks() {
            @Override
            public List<Id> ids() {
                return registered.stream().map(SkillLook::skill).toList();
            }

            @Override
            public Optional<SkillLook> look(Id skill) {
                return registered.stream().filter((SkillLook look) -> look.skill().equals(skill)).findFirst();
            }
        };
    }
}
