package art.arcane.adapt.pack.runtime;

import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Bakes the pack zip an operator drops into the server's pack folder, from the built-in content registered the way a
 * server registers it and the built-in art read the way the server's asset index lists it, so the zip records the same
 * inputs as the server's own bake.
 * <p>Usage: {@code PackZips <resources folder> <out folder> <adapt version> [threads]}.
 */
public final class PackZips {
    private PackZips() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("usage: PackZips <resources folder> <out folder> <adapt version> [threads]");
        }
        Path resources = Path.of(args[0]);
        Path out = Path.of(args[1]);
        String adapt = args[2];
        int threads = args.length == 4 ? Integer.parseInt(args[3]) : Runtime.getRuntime().availableProcessors();
        Path data = Files.createTempDirectory("adapt-pack-zips");
        try (TestAdapt server = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon())) {
            server.core().assetInstaller().install(builtinBundle(resources), Id.ADAPT);
            Map.Entry<Path, String> zip = write(server.core().assets(), out, adapt, threads);
            System.out.println(zip.getKey().getFileName() + " sha1 " + zip.getValue());
        } finally {
            delete(data);
        }
    }

    /** The zip name: {@code Adapt Pack v3.0.0.zip}. */
    static String name(String adapt) {
        return "Adapt Pack v" + adapt + ".zip";
    }

    /** Writes the zip of {@code catalog} into {@code out}, replacing older zips there, and returns the file with its SHA-1. */
    static Map.Entry<Path, String> write(AssetCatalog catalog, Path out, String adapt, int threads) throws IOException {
        Files.createDirectories(out);
        try (Stream<Path> old = Files.list(out)) {
            for (Path file : old.filter((Path file) -> file.getFileName().toString().startsWith("Adapt Pack v")).toList()) {
                Files.delete(file);
            }
        }
        CatalogSnapshot snapshot = CatalogSnapshot.of(catalog);
        PackBake.Result result = PackBake.build(PackBuilds.stamped(snapshot, adapt).withThreads(threads));
        Path file = out.resolve(name(adapt));
        Files.write(file, result.zip());
        return Map.entry(file, result.sha1());
    }

    /** The built-in {@code adapt} bundle below {@code resources}, holding the files the server's asset index lists, in its order. */
    static AssetBundle builtinBundle(Path resources) throws IOException {
        Path root = resources.resolve("assets").resolve(Id.ADAPT);
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String path = root.relativize(file).toString().replace('\\', '/');
                if (path.startsWith("textures/gui/") || path.startsWith("themes/")) {
                    files.put(path, Files.readAllBytes(file));
                }
            }
        }
        return TestCatalogs.memory(Id.ADAPT, files);
    }

    private static void delete(Path folder) {
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
