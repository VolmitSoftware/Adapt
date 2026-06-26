package art.arcane.adapt;

import art.arcane.adapt.api.tick.Ticked;
import art.arcane.adapt.api.tick.Ticker;
import art.arcane.adapt.api.world.PlayerData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class ConcurrencyStressTest extends AdaptTestBase {

    @Test
    @DisplayName("concurrent addStat on one player loses no updates")
    void concurrentAddStatNoLostUpdates() throws Exception {
        PlayerData data = new PlayerData();
        int threads = 16;
        int perThread = 5000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < perThread; j++) {
                    data.addStat("blocks", 1.0);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdownNow();
        assertThat(data.getStat("blocks")).isEqualTo((double) threads * perThread);
    }

    @Test
    @DisplayName("concurrent register, unregister and tick on the Ticker stays consistent")
    void concurrentTickerAccessIsSafe() throws Exception {
        Ticker ticker = new Ticker();
        Method tick = Ticker.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        List<Ticked> tickeds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Ticked m = mock(Ticked.class);
            lenient().when(m.shouldTick()).thenReturn(true);
            lenient().when(m.getId()).thenReturn("t" + i);
            lenient().when(m.getGroup()).thenReturn("g");
            lenient().when(m.getInterval()).thenReturn(0L);
            lenient().when(m.getLastTick()).thenReturn(0L);
            tickeds.add(m);
        }
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();
        ExecutorService ex = Executors.newFixedThreadPool(6);
        ex.submit(() -> {
            try {
                while (!stop.get()) {
                    tick.invoke(ticker);
                }
            } catch (Throwable e) {
                error.set(e);
            }
        });
        List<Future<?>> futures = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            futures.add(ex.submit(() -> {
                try {
                    for (int j = 0; j < 3000; j++) {
                        Ticked m = tickeds.get(j % tickeds.size());
                        ticker.register(m);
                        ticker.unregister(m);
                    }
                } catch (Throwable e) {
                    error.set(e);
                }
                return null;
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        stop.set(true);
        ex.shutdownNow();
        ex.awaitTermination(5, TimeUnit.SECONDS);
        ticker.clear();
        assertThat(error.get()).isNull();
    }
}
