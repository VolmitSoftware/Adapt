package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.pack.TestCatalogs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackBakeTest {
    /** The built-in bundle with only two kits' themes listed, to keep the bake short. */
    private static final AssetCatalog TWO_KITS = TestCatalogs.builtin(List.of(), Set.of("herbalism", "hunter"));

    @Test
    void parallelAndSerialBakesGiveTheSameZip() {
        PackBake.Result serial = PackBake.build(BakeRequest.of(TWO_KITS));
        PackBake.Result parallel = PackBake.build(BakeRequest.of(TWO_KITS).withThreads(4));
        assertArrayEquals(serial.zip(), parallel.zip());
        assertEquals(serial.sha1(), PackJson.sha1(parallel.zip()));
        assertEquals(2, serial.manifest().kits().size());
    }

    @Test
    void cachedKitsAreNotBakedAgain() {
        Map<String, KitBake.Result> kept = new ConcurrentHashMap<>();
        AtomicInteger baked = new AtomicInteger();
        BakeRequest.KitCache cache = new BakeRequest.KitCache() {
            @Override
            public KitBake.Result get(String key) {
                return kept.get(key);
            }

            @Override
            public void put(String key, KitBake.Result result) {
                baked.incrementAndGet();
                kept.put(key, result);
            }
        };
        PackBake.Result first = PackBake.build(BakeRequest.of(TWO_KITS).withCache(cache));
        PackBake.Result second = PackBake.build(BakeRequest.of(TWO_KITS).withCache(cache));
        assertEquals(2, baked.get());
        assertArrayEquals(first.zip(), second.zip());
    }

    @Test
    void cancellingStopsTheBake() {
        assertThrows(CancellationException.class, () -> PackBake.build(BakeRequest.of(TWO_KITS).withCancel(() -> true)));
    }
}
