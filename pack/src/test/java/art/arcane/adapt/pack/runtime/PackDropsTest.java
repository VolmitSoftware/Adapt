package art.arcane.adapt.pack.runtime;

import art.arcane.adapt.engine.asset.AdaptAssetCatalog;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.bake.BakeRequest;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.pack.bake.PackManifest;
import art.arcane.adapt.pack.bake.PackWriter;
import art.arcane.adapt.pack.bake.PackValidator;
import art.arcane.adapt.pack.link.PackArtifact;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackDropsTest {
    private static final String ADAPT = "3.0.0";
    private static AdaptAssetCatalog catalog;
    private static Map.Entry<Path, String> baked;
    private static Path zip;

    @TempDir
    static Path out;

    @TempDir
    Path folder;

    @BeforeAll
    static void bakeZip() throws IOException {
        catalog = TestCatalogs.builtin(List.of(), Set.of("swords"));
        baked = PackZips.write(catalog, out, ADAPT, 4);
        zip = baked.getKey();
    }

    @Test
    void bakeZipIsOneNamedValidZipAndTheSameEveryTime(@TempDir Path again) throws IOException {
        assertEquals("Adapt Pack v3.0.0.zip", zip.getFileName().toString());
        try (Stream<Path> files = Files.list(out)) {
            assertEquals(List.of(zip), files.toList());
        }
        assertEquals(baked.getValue(), PackZips.write(catalog, again, ADAPT, 1).getValue());
        assertEquals(PackArtifact.read(zip, 0, 0).sha1Hex(), baked.getValue());
        Map<String, byte[]> files = PackCache.unzip(Files.readAllBytes(zip));
        assertEquals(List.of(), PackValidator.check(files));
        PackManifest.Build build = PackManifest.fromJson(new String(files.get(PackManifest.PATH), StandardCharsets.UTF_8)).build();
        assertEquals(new PackManifest.Build(PackBake.VERSION, ADAPT, live()), build);
    }

    @Test
    void matchingZipIsServedWithoutABake() throws IOException {
        Files.copy(zip, folder.resolve(zip.getFileName()));
        AtomicInteger bakes = new AtomicInteger();
        PackBuilds builds = builds(bakes);
        builds.start();

        PackArtifact artifact = builds.source().current().orElseThrow();
        assertEquals(0, bakes.get());
        assertEquals(baked.getValue(), artifact.sha1Hex());
        assertEquals(PackWriter.MIN_FORMAT, artifact.minFormat());
        assertEquals(PackWriter.MAX_FORMAT, artifact.maxFormat());
        assertFalse(builds.manifests().current().orElseThrow().pages().isEmpty());
        builds.stop();
    }

    @Test
    void droppedZipIsTheSamePackTheServerBakes() throws IOException {
        AtomicInteger bakes = new AtomicInteger();
        PackBuilds builds = builds(bakes);
        builds.start();
        assertEquals(1, bakes.get());
        assertEquals(baked.getValue(), builds.source().current().orElseThrow().sha1Hex());
        builds.stop();
    }

    @Test
    void severalMatchingZipsServeTheFirstByName() throws IOException {
        Files.copy(zip, folder.resolve("b.zip"));
        Files.copy(zip, folder.resolve("a.zip"));
        Files.writeString(folder.resolve("0-notes.zip"), "not a zip");
        PackDrops.Choice choice = new PackDrops(folder).choose(ADAPT, Set.of(), live());
        assertEquals("a.zip", choice.drop().orElseThrow().file().getFileName().toString());
        assertEquals(List.of("0-notes.zip"), choice.refused().stream().map((PackDrops.Refusal refusal) -> refusal.file().getFileName().toString()).toList());
    }

    @Test
    void refusalsNameTheReason() throws IOException {
        Files.writeString(folder.resolve("garbage.zip"), "not a zip");
        Files.write(folder.resolve("other.zip"), zip(Map.of("pack.mcmeta", "{}".getBytes(StandardCharsets.UTF_8))));
        Files.write(folder.resolve("bake.zip"), edited((JsonObject build) -> build.addProperty("bake", "1")));
        Files.write(folder.resolve("adapt.zip"), edited((JsonObject build) -> build.addProperty("adapt", "2.9.0")));
        Files.write(folder.resolve("inputs.zip"), edited((JsonObject build) -> build.addProperty("inputs", "0")));
        Map<String, byte[]> broken = PackCache.unzip(Files.readAllBytes(zip));
        broken.remove("pack.mcmeta");
        Files.write(folder.resolve("valid.zip"), zip(broken));

        Map<String, PackDrops.Refusal> refused = refused(new PackDrops(folder).choose(ADAPT, Set.of(), live()));

        assertEquals(6, refused.size());
        assertTrue(refused.get("garbage.zip").reason().startsWith("it cannot be read as a zip"), refused.get("garbage.zip").reason());
        assertEquals("it is not an Adapt pack (no adapt_manifest.json)", refused.get("other.zip").reason());
        assertEquals("it was made by pack bake 1 and this Adapt bakes version " + PackBake.VERSION, refused.get("bake.zip").reason());
        assertEquals("it was made for Adapt 2.9.0 and this server runs Adapt 3.0.0", refused.get("adapt.zip").reason());
        assertEquals("it was made from other built-in content than this server's skills and art", refused.get("inputs.zip").reason());
        assertTrue(refused.get("valid.zip").reason().startsWith("it fails validation: "), refused.get("valid.zip").reason());
    }

    @Test
    void mismatchedZipFallsBackToTheBake() throws IOException {
        Files.write(folder.resolve("stale.zip"), edited((JsonObject build) -> build.addProperty("adapt", "2.9.0")));
        AtomicInteger bakes = new AtomicInteger();
        PackBuilds builds = builds(bakes);
        builds.start();
        assertEquals(1, bakes.get());
        assertEquals(baked.getValue(), builds.source().current().orElseThrow().sha1Hex());
        builds.stop();
    }

    @Test
    void addonArtIsRefusedAndBakedWithTheBuiltins() throws IOException {
        Files.copy(zip, folder.resolve(zip.getFileName()));
        AdaptAssetCatalog withAddon = TestCatalogs.builtin(List.of(), Set.of("swords"));
        withAddon.install(TestCatalogs.memory("orbit", Map.of("textures/gui/skill/comet.png", Files.readAllBytes(
                TestCatalogs.builtinRoot().resolve("assets/adapt/textures/gui/skill/swords.png")))), "orbit");
        CatalogSnapshot snapshot = CatalogSnapshot.of(withAddon);
        assertEquals(Set.of("orbit"), PackBuilds.addons(snapshot));
        PackDrops.Choice choice = new PackDrops(folder).choose(ADAPT, PackBuilds.addons(snapshot), PackBuilds.stamped(snapshot, ADAPT).build().inputs());
        assertTrue(choice.drop().isEmpty());
        assertEquals("it holds the built-in art only and this server has addon art (orbit)", choice.refused().get(0).reason());

        AtomicInteger bakes = new AtomicInteger();
        PackBuilds builds = new PackBuilds(withAddon, ADAPT, folder.resolve("cache"), new PackDrops(folder), Runnable::run, 4,
                (BakeRequest request) -> {
                    bakes.incrementAndGet();
                    return PackBake.build(request);
                });
        builds.start();
        assertEquals(1, bakes.get());
        Map<String, byte[]> pack = PackCache.unzip(builds.source().current().orElseThrow().open().readAllBytes());
        assertNotEquals(baked.getValue(), builds.source().current().orElseThrow().sha1Hex());
        assertTrue(pack.containsKey("assets/orbit/textures/gui/skill/comet.png"));
        builds.stop();
    }

    private PackBuilds builds(AtomicInteger bakes) {
        return new PackBuilds(catalog, ADAPT, folder.resolve("cache"), new PackDrops(folder), Runnable::run, 4, (BakeRequest request) -> {
            bakes.incrementAndGet();
            return PackBake.build(request);
        });
    }

    private static String live() {
        return PackBuilds.stamped(CatalogSnapshot.of(catalog), ADAPT).build().inputs();
    }

    private static Map<String, PackDrops.Refusal> refused(PackDrops.Choice choice) {
        assertTrue(choice.drop().isEmpty());
        Map<String, PackDrops.Refusal> byName = new TreeMap<>();
        for (PackDrops.Refusal refusal : choice.refused()) {
            byName.put(refusal.file().getFileName().toString(), refusal);
        }
        return byName;
    }

    /** The zip with its manifest's build record changed by {@code edit}. */
    private static byte[] edited(Consumer<JsonObject> edit) throws IOException {
        Map<String, byte[]> files = PackCache.unzip(Files.readAllBytes(zip));
        JsonObject manifest = JsonParser.parseString(new String(files.get(PackManifest.PATH), StandardCharsets.UTF_8)).getAsJsonObject();
        edit.accept(manifest.getAsJsonObject("build"));
        files.put(PackManifest.PATH, manifest.toString().getBytes(StandardCharsets.UTF_8));
        return zip(files);
    }

    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : new TreeMap<>(files).entrySet()) {
                out.putNextEntry(new ZipEntry(file.getKey()));
                out.write(file.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
