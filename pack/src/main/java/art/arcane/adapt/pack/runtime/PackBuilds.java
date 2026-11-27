package art.arcane.adapt.pack.runtime;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.engine.asset.AssetSubscription;
import art.arcane.adapt.engine.asset.InstalledBundle;
import art.arcane.adapt.engine.asset.SkillLook;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.bake.ArtPieces;
import art.arcane.adapt.pack.bake.BakeRequest;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.pack.bake.PackWriter;
import art.arcane.adapt.pack.link.PackArtifact;
import art.arcane.adapt.pack.link.PackSource;
import art.arcane.adapt.pack.manifest.PackManifest;
import art.arcane.adapt.pack.manifest.PackManifestReader;
import art.arcane.adapt.pack.manifest.PackManifests;
import art.arcane.adapt.platform.value.Id;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Keeps the server's pack built: on start and on every catalog revision it snapshots the catalog, serves a pack zip the
 * operator dropped in when it is exactly this pack (see {@link PackDrops}), else reuses the cached pack for that content
 * or bakes one off the calling thread, and publishes the zip as {@link PackSource} and its
 * manifest, read once, as {@link PackManifests}, both at once. A newer revision cancels a bake still running. A failed bake logs
 * one error and keeps whatever was published before, which at start is nothing, so menus stay chests.
 */
public final class PackBuilds {
    private final AssetCatalog catalog;
    private final String adapt;
    private final PackCache cache;
    private final PackDrops drops;
    private final Executor executor;
    private final int threads;
    private final Baker baker;
    private final AtomicLong generation = new AtomicLong();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final PackSource source = new Source();
    private final PackManifests manifests = new Manifests();
    private volatile Built built;
    private AssetSubscription subscription;
    private long scheduledRevision;
    private boolean running;

    /** What turns a request into a pack; {@link PackBake#build} outside tests. */
    @FunctionalInterface
    public interface Baker {
        PackBake.Result bake(BakeRequest request);
    }

    private record Built(PackArtifact artifact, PackManifest manifest, String key) {
    }

    public PackBuilds(AssetCatalog catalog, String adapt, Path cacheDirectory, PackDrops drops, Executor executor, int threads, Baker baker) {
        this.catalog = catalog;
        this.adapt = adapt;
        this.cache = new PackCache(cacheDirectory);
        this.drops = drops;
        this.executor = executor;
        this.threads = threads;
        this.baker = baker;
    }

    /**
     * Starts building the pack of {@code catalog} with Adapt {@code adapt} and installs it as the pack
     * source and manifest source. Pack zips are looked for in the data folder's {@code pack} folder, cached bakes in its
     * {@code pack/cache}. Returns null when the pack is off in the config.
     */
    public static PackBuilds install(AssetCatalog catalog, String adapt, Executor executor) {
        AdaptConfig.Pack config = AdaptConfig.get().getPack();
        if (!config.isEnabled()) {
            return null;
        }
        Path folder = AdaptEnvironment.dataDirectory().resolve("pack");
        PackBuilds builds = new PackBuilds(catalog, adapt, folder.resolve("cache"), new PackDrops(folder), executor, config.bakeThreadCount(),
                PackBake::build);
        AdaptPorts.install(PackSource.class, builds.source());
        AdaptPorts.install(PackManifests.class, builds.manifests());
        builds.start();
        return builds;
    }

    /** Stops building and removes the installed sources. */
    public void uninstall() {
        stop();
        AdaptPorts.uninstall(PackSource.class);
        AdaptPorts.uninstall(PackManifests.class);
    }

    public PackSource source() {
        return source;
    }

    public PackManifests manifests() {
        return manifests;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        scheduledRevision = Long.MIN_VALUE;
        subscription = catalog.subscribe(this::changed);
        changed(catalog);
    }

    public void stop() {
        boolean withdrawn;
        synchronized (this) {
            if (!running) {
                return;
            }
            running = false;
            generation.incrementAndGet();
            subscription.cancel();
            subscription = null;
            withdrawn = built != null;
            built = null;
        }
        if (withdrawn) {
            notifyListeners();
        }
    }

    private synchronized void changed(AssetCatalog changed) {
        if (!running) {
            return;
        }
        long revision = changed.revision();
        if (revision == scheduledRevision) {
            return;
        }
        scheduledRevision = revision;
        long ticket = generation.incrementAndGet();
        executor.execute(() -> build(ticket));
    }

    private void build(long ticket) {
        BooleanSupplier cancelled = () -> generation.get() != ticket;
        try {
            if (cancelled.getAsBoolean()) {
                return;
            }
            long start = System.nanoTime();
            CatalogSnapshot snapshot = CatalogSnapshot.of(catalog);
            if (!PackBake.hasBuiltinArt(snapshot)) {
                AdaptLog.error("The asset catalog holds no Adapt kit art at revision " + snapshot.revision()
                        + "; the pack is not built and menus stay chests.");
                return;
            }
            BakeRequest request = stamped(snapshot, adapt).withThreads(threads).withCancel(cancelled);
            String key = request.build().inputs();
            PackDrops.Choice choice = drops.choose(adapt, addons(snapshot), key);
            if (choice.drop().isPresent()) {
                PackDrops.Drop drop = choice.drop().get();
                PackArtifact artifact = PackArtifact.of(drop.zip(), PackWriter.MIN_FORMAT, PackWriter.MAX_FORMAT);
                PackManifest manifest = PackManifestReader.read(new StringReader(drop.manifest()));
                if (publish(ticket, new Built(artifact, manifest, key))) {
                    warn(choice, true);
                    AdaptLog.info(String.format(Locale.ROOT, "Using the Adapt pack zip %s, nothing baked: %.1f MB, sha1 %s.",
                            drop.file(), artifact.size() / 1_048_576.0, artifact.sha1Hex()));
                }
                return;
            }
            Optional<PackCache.Entry> cached = cache.load(key);
            PackCache.Entry entry;
            if (cached.isPresent()) {
                entry = cached.get();
            } else {
                PackBake.Result result = baker.bake(request);
                entry = new PackCache.Entry(result.zip(), result.manifest().toJson());
                store(key, entry);
            }
            PackArtifact artifact = PackArtifact.of(entry.zip(), PackWriter.MIN_FORMAT, PackWriter.MAX_FORMAT);
            PackManifest manifest = PackManifestReader.read(new StringReader(entry.manifest()));
            if (publish(ticket, new Built(artifact, manifest, key))) {
                warn(choice, false);
                AdaptLog.info(String.format(Locale.ROOT, "%s Adapt pack in %.1f s: %.1f MB, sha1 %s.",
                        cached.isPresent() ? "Loaded the cached" : "Baked the", (System.nanoTime() - start) / 1e9,
                        artifact.size() / 1_048_576.0, artifact.sha1Hex()));
            }
        } catch (CancellationException superseded) {
            AdaptLog.debug("An Adapt pack bake was superseded by a newer asset revision.");
        } catch (RuntimeException | OutOfMemoryError | LinkageError failure) {
            if (!cancelled.getAsBoolean()) {
                AdaptLog.error("Could not build the Adapt resource pack; players keep chest menus: " + failure, failure);
            }
        }
    }

    /** Warns once per zip and reason about every refused zip. */
    private void warn(PackDrops.Choice choice, boolean served) {
        for (PackDrops.Refusal refusal : choice.refused()) {
            String line = "Ignoring the Adapt pack zip " + refusal.file() + ": " + refusal.reason() + (served ? "." : "; Adapt makes its own pack instead.");
            if (warned.add(line)) {
                AdaptLog.warn(line);
            }
        }
    }

    /** The namespaces of addon art in {@code snapshot}: bundles and themes outside {@code adapt}. */
    static Set<String> addons(CatalogSnapshot snapshot) {
        Set<String> namespaces = new TreeSet<>();
        for (InstalledBundle bundle : snapshot.bundles()) {
            if (!Id.ADAPT.equals(bundle.namespace())) {
                namespaces.add(bundle.namespace());
            }
        }
        for (Id skill : snapshot.themes()) {
            if (!Id.ADAPT.equals(skill.namespace())) {
                namespaces.add(skill.namespace());
            }
        }
        return namespaces;
    }

    /** The bake of {@code snapshot}, recording Adapt {@code adapt} and the hash of its inputs. */
    static BakeRequest stamped(CatalogSnapshot snapshot, String adapt) {
        BakeRequest request = BakeRequest.of(snapshot).withBuild(adapt, "");
        return request.withBuild(adapt, key(snapshot, request));
    }

    private void store(String key, PackCache.Entry entry) {
        try {
            cache.store(key, entry);
        } catch (IOException failure) {
            AdaptLog.warn("Could not cache the Adapt pack in " + cache.directory() + ": " + failure.getMessage());
        }
    }

    private boolean publish(long ticket, Built next) {
        synchronized (this) {
            if (!running || generation.get() != ticket) {
                return false;
            }
            built = next;
        }
        cache.prune(next.key());
        notifyListeners();
        return true;
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                AdaptLog.warn("An Adapt pack listener failed", failure);
            }
        }
    }

    private Runnable listen(Runnable changed) {
        listeners.add(changed);
        return () -> listeners.remove(changed);
    }

    /**
     * SHA-1 over everything the bake reads: its version, the Adapt version it records, the design, the fixed art, the catalog's hash of
     * every bundle and of every theme, generated ones included, and each registered skill's look, which its tint and
     * generated emblem and trim follow.
     */
    static String key(CatalogSnapshot snapshot, BakeRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            text(digest, PackBake.VERSION);
            text(digest, request.build().adapt());
            text(digest, request.description());
            text(digest, request.design().hash());
            for (ArtPieces.Piece piece : request.art()) {
                text(digest, piece.path());
                text(digest, piece.role());
                digest.update(piece.png());
            }
            for (InstalledBundle bundle : snapshot.bundles()) {
                text(digest, bundle.namespace());
                text(digest, bundle.hash());
            }
            for (Id skill : snapshot.themes()) {
                text(digest, skill.toString());
                text(digest, snapshot.theme(skill).map(ThemeAssets::hash).orElseThrow());
                text(digest, snapshot.look(skill).map((SkillLook look) -> look.rgb() + " " + look.icon()).orElse(""));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-1 is unavailable", missing);
        }
    }

    private static void text(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private final class Source implements PackSource {
        @Override
        public Optional<PackArtifact> current() {
            Built now = built;
            return now == null ? Optional.empty() : Optional.of(now.artifact());
        }

        @Override
        public Runnable subscribe(Runnable changed) {
            return listen(changed);
        }
    }

    private final class Manifests implements PackManifests {
        @Override
        public Optional<PackManifest> current() {
            Built now = built;
            return now == null ? Optional.empty() : Optional.of(now.manifest());
        }

        @Override
        public Runnable subscribe(Runnable changed) {
            return listen(changed);
        }
    }
}
