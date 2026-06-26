package art.arcane.adapt.api;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventHandlerInvokerTest {

    public static class TestEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    public static class OtherEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    public static class PublicListener implements Listener {
        final AtomicInteger hits = new AtomicInteger();

        public void onTest(TestEvent e) {
            hits.incrementAndGet();
        }
    }

    public static class PrivateListener implements Listener {
        final AtomicInteger hits = new AtomicInteger();

        private void onTest(TestEvent e) {
            hits.incrementAndGet();
        }
    }

    public static class ThrowingListener implements Listener {
        public void onTest(TestEvent e) {
            throw new IllegalStateException("boom");
        }
    }

    private Method handler(Class<?> type) throws NoSuchMethodException {
        Method m = type.getDeclaredMethod("onTest", TestEvent.class);
        m.setAccessible(true);
        return m;
    }

    @Test
    @DisplayName("createExecutor dispatches to a public handler method")
    void publicDispatch() throws Exception {
        PublicListener l = new PublicListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(handler(PublicListener.class), TestEvent.class);
        ex.execute(l, new TestEvent());
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("createExecutor dispatches to a private handler method")
    void privateDispatch() throws Exception {
        PrivateListener l = new PrivateListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(handler(PrivateListener.class), TestEvent.class);
        ex.execute(l, new TestEvent());
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("createExecutor ignores events of the wrong type")
    void typeMismatchSkipped() throws Exception {
        PublicListener l = new PublicListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(handler(PublicListener.class), TestEvent.class);
        ex.execute(l, new OtherEvent());
        assertThat(l.hits.get()).isZero();
    }

    @Test
    @DisplayName("createExecutor wraps handler exceptions in EventException")
    void exceptionWrapped() throws Exception {
        ThrowingListener l = new ThrowingListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(handler(ThrowingListener.class), TestEvent.class);
        assertThatThrownBy(() -> ex.execute(l, new TestEvent())).isInstanceOf(EventException.class);
    }
}
