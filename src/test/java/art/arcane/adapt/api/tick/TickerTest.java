package art.arcane.adapt.api.tick;

import art.arcane.adapt.AdaptTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TickerTest extends AdaptTestBase {

    private static Method tickMethod() throws NoSuchMethodException {
        Method m = Ticker.class.getDeclaredMethod("tick");
        m.setAccessible(true);
        return m;
    }

    private static Ticked baseMock(String id) {
        Ticked t = mock(Ticked.class);
        lenient().when(t.getId()).thenReturn(id);
        lenient().when(t.getGroup()).thenReturn("test");
        lenient().when(t.getInterval()).thenReturn(0L);
        lenient().when(t.getLastTick()).thenReturn(0L);
        lenient().when(t.hasTickDemand()).thenReturn(true);
        return t;
    }

    @Test
    @DisplayName("an exception thrown by one ticked does not stop the others")
    void exceptionIsolation() throws Exception {
        Ticker t = new Ticker();
        AtomicInteger goodTicks = new AtomicInteger();
        Ticked good = baseMock("good");
        doAnswer(invocation -> {
            goodTicks.incrementAndGet();
            return null;
        }).when(good).tick();
        Ticked bad = baseMock("bad");
        doThrow(new RuntimeException("boom")).when(bad).tick();
        t.register(good);
        t.register(bad);
        Method tick = tickMethod();
        for (int i = 0; i < 4; i++) {
            tick.invoke(t);
        }
        assertThat(goodTicks.get()).isGreaterThan(0);
        t.clear();
    }

    @Test
    @DisplayName("a tick is due exactly at its configured interval")
    void exactIntervalBoundaryIsDue() {
        assertThat(Ticker.isDue(1_050L, 1_000L, 50L)).isTrue();
        assertThat(Ticker.isDue(1_049L, 1_000L, 50L)).isFalse();
        assertThat(Ticker.isDue(999L, 1_000L, 50L)).isFalse();
    }

    @Test
    @DisplayName("objects without tick demand stay out of the execution path")
    void noDemandSkipsTickExecution() throws Exception {
        Ticker ticker = new Ticker();
        Ticked idle = baseMock("idle");
        when(idle.hasTickDemand()).thenReturn(false);
        ticker.register(idle);

        Method tick = tickMethod();
        tick.invoke(ticker);
        tick.invoke(ticker);

        verify(idle, never()).tick();
        ticker.clear();
    }

    @Test
    @DisplayName("an exception from tick demand does not stop other objects")
    void demandExceptionIsolation() throws Exception {
        Ticker ticker = new Ticker();
        AtomicInteger goodTicks = new AtomicInteger();
        Ticked bad = baseMock("bad-demand");
        when(bad.hasTickDemand()).thenThrow(new RuntimeException("demand boom"));
        Ticked good = baseMock("good-demand");
        doAnswer(invocation -> {
            goodTicks.incrementAndGet();
            return null;
        }).when(good).tick();
        ticker.register(bad);
        ticker.register(good);

        Method tick = tickMethod();
        tick.invoke(ticker);
        tick.invoke(ticker);

        verify(bad, never()).tick();
        assertThat(goodTicks.get()).isEqualTo(1);
        ticker.clear();
    }

    @Test
    @DisplayName("registering and ticking many objects does not throw")
    void registerAndTickManyDoesNotThrow() throws Exception {
        Ticker t = new Ticker();
        for (int i = 0; i < 200; i++) {
            t.register(baseMock("t" + i));
        }
        Method tick = tickMethod();
        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                tick.invoke(t);
            }
        }).doesNotThrowAnyException();
        t.clear();
    }

    @Test
    @DisplayName("unregister removes only the requested object when ids match")
    void unregisterUsesObjectIdentity() throws Exception {
        Ticker ticker = new Ticker();
        EqualTicked first = new EqualTicked("shared");
        EqualTicked second = new EqualTicked("shared");

        ticker.register(first);
        ticker.register(second);
        Method tick = tickMethod();
        tick.invoke(ticker);
        tick.invoke(ticker);
        ticker.unregister(first);
        tick.invoke(ticker);
        tick.invoke(ticker);

        assertThat(first.tickCount()).isEqualTo(2);
        assertThat(second.tickCount()).isEqualTo(3);
        ticker.clear();
    }

    private static final class EqualTicked implements Ticked {
        private final String id;
        private final AtomicInteger ticks = new AtomicInteger();

        private EqualTicked(String id) {
            this.id = id;
        }

        private int tickCount() {
            return ticks.get();
        }

        @Override
        public void unregister() {
        }

        @Override
        public boolean isBursting() {
            return false;
        }

        @Override
        public boolean isSkipping() {
            return false;
        }

        @Override
        public void stopBursting() {
        }

        @Override
        public void stopSkipping() {
        }

        @Override
        public long getAge() {
            return 0L;
        }

        @Override
        public void burst(int ticks) {
        }

        @Override
        public void skip(int ticks) {
        }

        @Override
        public long getLastTick() {
            return 0L;
        }

        @Override
        public long getInterval() {
            return 0L;
        }

        @Override
        public void setInterval(long ms) {
        }

        @Override
        public void tick() {
            ticks.incrementAndGet();
        }

        @Override
        public String getGroup() {
            return "test";
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualTicked ticked && id.equals(ticked.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
