package art.arcane.adapt.pack.runtime;

import art.arcane.adapt.engine.asset.AdaptAssetCatalog;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.bake.BakeRequest;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.pack.bake.PackManifest;
import art.arcane.adapt.pack.bake.PackValidator;
import art.arcane.adapt.pack.bake.PackWriter;
import art.arcane.adapt.pack.link.PackArtifact;
import art.arcane.adapt.pack.manifest.PackManifestReader;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackBuildsTest {
    private static final Executor DIRECT = Runnable::run;

    @TempDir
    Path cache;

    @Test
    void startBakesOnceAndPublishesArtifactWithManifest() {
        FakeCatalog catalog = new FakeCatalog();
        CountingBaker baker = new CountingBaker();
        PackBuilds builds = new PackBuilds(catalog.catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 1, baker);
        AtomicInteger notified = new AtomicInteger();
        builds.source().subscribe(notified::incrementAndGet);

        builds.start();

        assertEquals(1, baker.count.get());
        assertEquals(1, notified.get());
        PackArtifact artifact = builds.source().current().orElseThrow();
        assertEquals(PackWriter.MIN_FORMAT, artifact.minFormat());
        assertEquals(PackWriter.MAX_FORMAT, artifact.maxFormat());
        assertEquals(PackManifestReader.read(new StringReader(baker.lastManifest)), builds.manifests().current().orElseThrow());
    }

    @Test
    void changedContentRebakesOnceAndSameContentLoadsFromCache() {
        FakeCatalog catalog = new FakeCatalog();
        CountingBaker baker = new CountingBaker();
        PackBuilds builds = new PackBuilds(catalog.catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 1, baker);
        builds.start();
        String first = builds.source().current().orElseThrow().sha1Hex();

        catalog.change(new byte[]{1, 2, 3});
        assertEquals(1, baker.count.get());
        assertEquals(first, builds.source().current().orElseThrow().sha1Hex());

        catalog.change(new byte[]{9, 9, 9});
        assertEquals(2, baker.count.get());
        assertNotEquals(first, builds.source().current().orElseThrow().sha1Hex());
    }

    @Test
    void sameContentIsReadFromCacheWithoutBaking() {
        FakeCatalog catalog = new FakeCatalog();
        PackBuilds first = new PackBuilds(catalog.catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 1, new CountingBaker());
        first.start();
        String sha1 = first.source().current().orElseThrow().sha1Hex();
        art.arcane.adapt.pack.manifest.PackManifest manifest = first.manifests().current().orElseThrow();
        first.stop();

        CountingBaker baker = new CountingBaker();
        PackBuilds restarted = new PackBuilds(new FakeCatalog().catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 1, baker);
        restarted.start();

        assertEquals(0, baker.count.get());
        assertEquals(sha1, restarted.source().current().orElseThrow().sha1Hex());
        assertEquals(manifest, restarted.manifests().current().orElseThrow());
    }

    @Test
    void failedBakeLeavesNoArtifactAndNoCache() throws IOException {
        AtomicInteger notified = new AtomicInteger();
        PackBuilds builds = new PackBuilds(new FakeCatalog().catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 1, (BakeRequest request) -> {
            throw new IllegalStateException("pack failed validation: [broken]");
        });
        builds.source().subscribe(notified::incrementAndGet);

        builds.start();

        assertTrue(builds.source().current().isEmpty());
        assertTrue(builds.manifests().current().isEmpty());
        assertEquals(0, notified.get());
        assertEquals(0, folders(cache).size());
    }

    @Test
    void failedRebuildKeepsThePublishedPack() {
        FakeCatalog catalog = new FakeCatalog();
        CountingBaker baker = new CountingBaker();
        PackBuilds builds = new PackBuilds(catalog.catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 1, baker);
        builds.start();
        String sha1 = builds.source().current().orElseThrow().sha1Hex();

        baker.fail = true;
        catalog.change(new byte[]{4});

        assertEquals(sha1, builds.source().current().orElseThrow().sha1Hex());
    }

    @Test
    void supersededBakeIsNeitherRunNorPublished() {
        FakeCatalog catalog = new FakeCatalog();
        CountingBaker baker = new CountingBaker();
        Deque<Runnable> queued = new ArrayDeque<>();
        PackBuilds builds = new PackBuilds(catalog.catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), queued::add, 1, baker);
        builds.start();
        catalog.change(new byte[]{7});

        assertEquals(2, queued.size());
        queued.removeFirst().run();
        assertEquals(0, baker.count.get());
        assertTrue(builds.source().current().isEmpty());

        queued.removeFirst().run();
        assertEquals(1, baker.count.get());
        assertTrue(builds.source().current().isPresent());
    }

    @Test
    void rebuildPrunesTheOldCacheEntry() throws IOException {
        FakeCatalog catalog = new FakeCatalog();
        PackBuilds builds = new PackBuilds(catalog.catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 1, new CountingBaker());
        builds.start();
        List<Path> before = folders(cache);
        catalog.change(new byte[]{5});

        List<Path> after = folders(cache);
        assertEquals(1, before.size());
        assertEquals(1, after.size());
        assertNotEquals(before.getFirst(), after.getFirst());
    }

    @Test
    void brokenCacheEntryIsBakedAgain() throws IOException {
        FakeCatalog catalog = new FakeCatalog();
        PackBuilds first = new PackBuilds(catalog.catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 1, new CountingBaker());
        first.start();
        first.stop();
        Files.write(folders(cache).getFirst().resolve(PackCache.ZIP), new byte[]{1, 2, 3});

        CountingBaker baker = new CountingBaker();
        PackBuilds restarted = new PackBuilds(new FakeCatalog().catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 1, baker);
        restarted.start();

        assertEquals(1, baker.count.get());
        assertTrue(restarted.source().current().isPresent());
    }

    @Test
    void stopWithdrawsThePack() {
        PackBuilds builds = new PackBuilds(new FakeCatalog().catalog(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 1, new CountingBaker());
        builds.start();
        AtomicInteger notified = new AtomicInteger();
        builds.manifests().subscribe(notified::incrementAndGet);

        builds.stop();

        assertEquals(1, notified.get());
        assertTrue(builds.source().current().isEmpty());
        assertTrue(builds.manifests().current().isEmpty());
    }

    @Test
    void builtinArtBakesValidatesAndIsReusedAfterRestart() throws IOException {
        AssetCatalog builtin = TestCatalogs.builtin();
        AtomicInteger bakes = new AtomicInteger();
        PackBuilds.Baker counted = (BakeRequest request) -> {
            bakes.incrementAndGet();
            return PackBake.build(request);
        };
        PackBuilds first = new PackBuilds(builtin, "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 4, counted);
        first.start();
        PackArtifact artifact = first.source().current().orElseThrow();
        first.stop();

        Map<String, byte[]> files = PackCache.unzip(artifact.open().readAllBytes());
        assertEquals(List.of(), PackValidator.check(files));
        assertEquals(new String(files.get(PackManifest.PATH), StandardCharsets.UTF_8).strip(),
                Files.readString(folders(cache).getFirst().resolve(PackCache.MANIFEST)).strip());

        PackBuilds restarted = new PackBuilds(builtin, "3.0.0", cache, new PackDrops(cache.resolve("drops")), DIRECT, 4, counted);
        restarted.start();
        assertEquals(1, bakes.get());
        assertEquals(artifact.sha1Hex(), restarted.source().current().orElseThrow().sha1Hex());
        assertFalse(restarted.manifests().current().orElseThrow().pages().isEmpty());
    }

    private static List<Path> folders(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(directory)) {
            return list.toList();
        }
    }

    /** A pack that passes validation, whose bytes follow the catalog's one sprite. */
    private static final class CountingBaker implements PackBuilds.Baker {
        private final AtomicInteger count = new AtomicInteger();
        private String lastManifest;
        private boolean fail;

        @Override
        public PackBake.Result bake(BakeRequest request) {
            if (fail) {
                throw new IllegalStateException("bake failed");
            }
            count.incrementAndGet();
            byte[] sprite = request.catalog().sprite(FakeCatalog.SPRITE).orElseThrow();
            PackManifest manifest = new PackManifest(PackManifest.SCHEMA, "adapt:ui/space", Map.of(0xE001, 1, 0xE002, -1), List.of(), List.of(),
                    Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, String.valueOf(sprite.length + sprite[0]), null,
                    new PackManifest.Build(PackBake.VERSION, "", ""));
            lastManifest = manifest.toJson();
            String mcmeta = "{\"pack\":{\"description\":\"test\",\"min_format\":" + PackWriter.MIN_FORMAT + ",\"max_format\":" + PackWriter.MAX_FORMAT + "}}";
            byte[] zip = zip(Map.of("pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8), "assets/adapt/sprite.bin", sprite,
                    PackManifest.PATH, lastManifest.getBytes(StandardCharsets.UTF_8)));
            return new PackBake.Result(zip, manifest, "unused");
        }

        private static byte[] zip(Map<String, byte[]> files) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream out = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> file : new TreeMap<>(files).entrySet()) {
                    ZipEntry entry = new ZipEntry(file.getKey());
                    entry.setTime(0L);
                    out.putNextEntry(entry);
                    out.write(file.getValue());
                    out.closeEntry();
                }
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
            return bytes.toByteArray();
        }
    }

    /** A real catalog holding one {@code adapt} bundle of the two kit pieces a bake needs; {@link #change} reinstalls it. */
    private static final class FakeCatalog {
        static final Id SPRITE = Id.adapt("gui/kit/tile");
        private final AdaptAssetCatalog catalog = TestCatalogs.empty(List.of());

        FakeCatalog() {
            change(new byte[]{1, 2, 3});
        }

        AdaptAssetCatalog catalog() {
            return catalog;
        }

        void change(byte[] tile) {
            Map<String, byte[]> files = new TreeMap<>();
            files.put("textures/gui/kit/tile.png", tile);
            files.put("textures/gui/kit/tooltip.png", new byte[]{1});
            catalog.install(TestCatalogs.memory(Id.ADAPT, files), Id.ADAPT);
        }
    }
}
