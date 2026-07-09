package art.arcane.adapt.api;

import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.EventExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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

    public static class CancellableEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancel) {
            this.cancelled = cancel;
        }

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

    public static class InteractListener implements Listener {
        final AtomicInteger hits = new AtomicInteger();

        public void onInteract(PlayerInteractEvent e) {
            hits.incrementAndGet();
        }
    }

    public static class PolicyListener implements Listener {
        @EventHandler
        public void onInteract(PlayerInteractEvent e) {
        }

        @EventHandler
        @ReceiveCancelledEvents
        public void onInteractRaw(PlayerInteractEvent e) {
        }

        @EventHandler
        public void onCancellable(CancellableEvent e) {
        }

        @EventHandler
        @ReceiveCancelledEvents
        public void onCancellableRaw(CancellableEvent e) {
        }

        @EventHandler(ignoreCancelled = true)
        public void onPlain(TestEvent e) {
        }
    }

    private Method handler(Class<?> type) throws NoSuchMethodException {
        Method m = type.getDeclaredMethod("onTest", TestEvent.class);
        m.setAccessible(true);
        return m;
    }

    private Method interactHandler() throws NoSuchMethodException {
        Method m = InteractListener.class.getDeclaredMethod("onInteract", PlayerInteractEvent.class);
        m.setAccessible(true);
        return m;
    }

    private Method policyHandler(String name, Class<? extends Event> eventType) throws NoSuchMethodException {
        Method m = PolicyListener.class.getDeclaredMethod(name, eventType);
        m.setAccessible(true);
        return m;
    }

    private PlayerInteractEvent airClick() {
        return new PlayerInteractEvent(mock(Player.class), Action.LEFT_CLICK_AIR, null, null, BlockFace.SELF, EquipmentSlot.HAND);
    }

    private PlayerInteractEvent blockClick() {
        return new PlayerInteractEvent(mock(Player.class), Action.RIGHT_CLICK_BLOCK, null, mock(Block.class), BlockFace.UP, EquipmentSlot.HAND);
    }

    @Test
    @DisplayName("createExecutor dispatches to a public handler method")
    void publicDispatch() throws Exception {
        PublicListener l = new PublicListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(handler(PublicListener.class), TestEvent.class, false);
        ex.execute(l, new TestEvent());
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("createExecutor dispatches to a private handler method")
    void privateDispatch() throws Exception {
        PrivateListener l = new PrivateListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(handler(PrivateListener.class), TestEvent.class, false);
        ex.execute(l, new TestEvent());
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("createExecutor ignores events of the wrong type")
    void typeMismatchSkipped() throws Exception {
        PublicListener l = new PublicListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(handler(PublicListener.class), TestEvent.class, false);
        ex.execute(l, new OtherEvent());
        assertThat(l.hits.get()).isZero();
    }

    @Test
    @DisplayName("createExecutor wraps handler exceptions in EventException")
    void exceptionWrapped() throws Exception {
        ThrowingListener l = new ThrowingListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(handler(ThrowingListener.class), TestEvent.class, false);
        assertThatThrownBy(() -> ex.execute(l, new TestEvent())).isInstanceOf(EventException.class);
    }

    @Test
    @DisplayName("interaction validity gate delivers born-denied air clicks")
    void gateDeliversAirClicks() throws Exception {
        InteractListener l = new InteractListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(interactHandler(), PlayerInteractEvent.class, true);
        PlayerInteractEvent event = airClick();
        assertThat(event.useInteractedBlock()).isEqualTo(Event.Result.DENY);
        assertThat(event.isCancelled()).isTrue();
        ex.execute(l, event);
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("interaction validity gate skips plugin-cancelled air clicks")
    void gateSkipsCancelledAirClicks() throws Exception {
        InteractListener l = new InteractListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(interactHandler(), PlayerInteractEvent.class, true);
        PlayerInteractEvent event = airClick();
        event.setCancelled(true);
        assertThat(event.useItemInHand()).isEqualTo(Event.Result.DENY);
        ex.execute(l, event);
        assertThat(l.hits.get()).isZero();
    }

    @Test
    @DisplayName("interaction validity gate delivers allowed block clicks")
    void gateDeliversAllowedBlockClicks() throws Exception {
        InteractListener l = new InteractListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(interactHandler(), PlayerInteractEvent.class, true);
        ex.execute(l, blockClick());
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("interaction validity gate skips vetoed block clicks")
    void gateSkipsVetoedBlockClicks() throws Exception {
        InteractListener l = new InteractListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(interactHandler(), PlayerInteractEvent.class, true);
        PlayerInteractEvent event = blockClick();
        event.setUseInteractedBlock(Event.Result.DENY);
        ex.execute(l, event);
        assertThat(l.hits.get()).isZero();
    }

    @Test
    @DisplayName("unenforced executor delivers vetoed block clicks")
    void unenforcedDeliversVetoedBlockClicks() throws Exception {
        InteractListener l = new InteractListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(interactHandler(), PlayerInteractEvent.class, false);
        PlayerInteractEvent event = blockClick();
        event.setUseInteractedBlock(Event.Result.DENY);
        ex.execute(l, event);
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("shouldIgnoreCancelled registers PlayerInteractEvent with ignoreCancelled=false")
    void interactRegistersWithoutIgnoreCancelled() throws Exception {
        Method m = policyHandler("onInteract", PlayerInteractEvent.class);
        assertThat(EventHandlerInvoker.shouldIgnoreCancelled(m, m.getAnnotation(EventHandler.class), PlayerInteractEvent.class)).isFalse();
    }

    @Test
    @DisplayName("shouldIgnoreCancelled forces ignoreCancelled=true for other cancellable events")
    void cancellableForcedTrue() throws Exception {
        Method m = policyHandler("onCancellable", CancellableEvent.class);
        assertThat(EventHandlerInvoker.shouldIgnoreCancelled(m, m.getAnnotation(EventHandler.class), CancellableEvent.class)).isTrue();
    }

    @Test
    @DisplayName("shouldIgnoreCancelled honors ReceiveCancelledEvents on cancellable events")
    void receiveCancelledOptsOut() throws Exception {
        Method m = policyHandler("onCancellableRaw", CancellableEvent.class);
        assertThat(EventHandlerInvoker.shouldIgnoreCancelled(m, m.getAnnotation(EventHandler.class), CancellableEvent.class)).isFalse();
    }

    @Test
    @DisplayName("shouldIgnoreCancelled passes the annotation through for non-cancellable events")
    void nonCancellableUsesAnnotation() throws Exception {
        Method m = policyHandler("onPlain", TestEvent.class);
        assertThat(EventHandlerInvoker.shouldIgnoreCancelled(m, m.getAnnotation(EventHandler.class), TestEvent.class)).isTrue();
    }

    @Test
    @DisplayName("enforcesInteractionValidity applies only to PlayerInteractEvent without ReceiveCancelledEvents")
    void enforcementScope() throws Exception {
        assertThat(EventHandlerInvoker.enforcesInteractionValidity(policyHandler("onInteract", PlayerInteractEvent.class), PlayerInteractEvent.class)).isTrue();
        assertThat(EventHandlerInvoker.enforcesInteractionValidity(policyHandler("onInteractRaw", PlayerInteractEvent.class), PlayerInteractEvent.class)).isFalse();
        assertThat(EventHandlerInvoker.enforcesInteractionValidity(policyHandler("onCancellable", CancellableEvent.class), CancellableEvent.class)).isFalse();
    }
}
