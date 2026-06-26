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

class TickerTest extends AdaptTestBase {

    private static Method tickMethod() throws NoSuchMethodException {
        Method m = Ticker.class.getDeclaredMethod("tick");
        m.setAccessible(true);
        return m;
    }

    private static Ticked baseMock(String id) {
        Ticked t = mock(Ticked.class);
        lenient().when(t.shouldTick()).thenReturn(true);
        lenient().when(t.getId()).thenReturn(id);
        lenient().when(t.getGroup()).thenReturn("test");
        lenient().when(t.getInterval()).thenReturn(0L);
        lenient().when(t.getLastTick()).thenReturn(0L);
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
}
