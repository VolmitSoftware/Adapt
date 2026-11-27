package art.arcane.adapt.pack.runtime;

import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.pack.bake.PackManifest;
import art.arcane.adapt.pack.bake.PackValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Pack zips an operator put straight into the server's pack folder ({@code plugins/Adapt/pack/}). The first zip by file
 * name that is exactly the pack this server would bake is served in place of a bake: same bake version, the same Adapt
 * version, no addon art on the server, the same bake inputs, and a pack that validates. Every other zip is refused with
 * one reason.
 */
public final class PackDrops {
    private static final String SUFFIX = ".zip";

    private final Path directory;

    public PackDrops(Path directory) {
        this.directory = directory;
    }

    /** A zip chosen to serve: its file, its bytes and its manifest JSON. */
    public record Drop(Path file, byte[] zip, String manifest) {
    }

    /** A zip not served and why. */
    public record Refusal(Path file, String reason) {
    }

    /** The zip chosen, if any, and every zip refused, in file name order. */
    public record Choice(Optional<Drop> drop, List<Refusal> refused) {
        public Choice {
            refused = List.copyOf(refused);
        }
    }

    public Path directory() {
        return directory;
    }

    /**
     * Chooses the zip a server with Adapt {@code adapt} serves, given the namespaces of addon art in its catalog and the
     * input hash it would bake with.
     */
    public Choice choose(String adapt, Set<String> addons, String inputs) {
        List<Refusal> refused = new ArrayList<>();
        for (Path file : zips()) {
            try {
                Drop drop = read(file, adapt, addons, inputs);
                return new Choice(Optional.of(drop), refused);
            } catch (Refused refusal) {
                refused.add(new Refusal(file, refusal.getMessage()));
            }
        }
        return new Choice(Optional.empty(), refused);
    }

    private List<Path> zips() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter((Path file) -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(SUFFIX))
                    .sorted(Comparator.comparing((Path file) -> file.getFileName().toString()))
                    .toList();
        } catch (IOException failure) {
            return List.of();
        }
    }

    private static Drop read(Path file, String adapt, Set<String> addons, String inputs) throws Refused {
        String json = manifestJson(file);
        PackManifest manifest;
        try {
            manifest = PackManifest.fromJson(json);
        } catch (RuntimeException unreadable) {
            throw new Refused("its " + PackManifest.PATH + " is not a manifest this Adapt reads");
        }
        PackManifest.Build build = manifest.build();
        if (build == null) {
            throw new Refused("it records no pack bake version");
        }
        if (!PackBake.VERSION.equals(build.bake())) {
            throw new Refused("it was made by pack bake " + build.bake() + " and this Adapt bakes version " + PackBake.VERSION);
        }
        if (!adapt.equals(build.adapt())) {
            throw new Refused("it was made for Adapt " + build.adapt() + " and this server runs Adapt " + adapt);
        }
        if (!addons.isEmpty()) {
            throw new Refused("it holds the built-in art only and this server has addon art (" + String.join(", ", addons) + ")");
        }
        if (!inputs.equals(build.inputs())) {
            throw new Refused("it was made from other built-in content than this server's skills and art");
        }
        byte[] zip;
        List<String> problems;
        try {
            zip = Files.readAllBytes(file);
            problems = PackValidator.check(PackCache.unzip(zip));
        } catch (IOException failure) {
            throw new Refused("it cannot be read: " + failure.getMessage());
        }
        if (!problems.isEmpty()) {
            throw new Refused("it fails validation: " + problems.get(0));
        }
        return new Drop(file, zip, json);
    }

    private static String manifestJson(Path file) throws Refused {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry(PackManifest.PATH);
            if (entry == null) {
                throw new Refused("it is not an Adapt pack (no " + PackManifest.PATH + ")");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException failure) {
            throw new Refused("it cannot be read as a zip: " + failure.getMessage());
        }
    }

    private static final class Refused extends Exception {
        Refused(String reason) {
            super(reason, null, false, false);
        }
    }
}
