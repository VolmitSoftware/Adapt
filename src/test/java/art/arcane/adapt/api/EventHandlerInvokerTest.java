package art.arcane.adapt.api;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class EventHandlerInvokerTest extends AdaptTestBase {

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

    public static class PickupListener implements Listener {
        final AtomicInteger hits = new AtomicInteger();

        public void onPickup(EntityPickupItemEvent event) {
            hits.incrementAndGet();
        }
    }

    public static class BreakListener implements Listener {
        final AtomicInteger hits = new AtomicInteger();

        public void onBreak(BlockBreakEvent event) {
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

    public abstract static class MeasuredAdaptationListener implements Listener, Adaptation<Object> {
        public void onTest(TestEvent event) {
        }

        @Override
        public String getName() {
            return "measured-listener";
        }
    }

    public abstract static class MoveAdaptationListener implements Listener, Adaptation<Object> {
        static final AtomicInteger HITS = new AtomicInteger();

        public void onMove(PlayerMoveEvent event) {
            HITS.incrementAndGet();
        }

        @Override
        public String getName() {
            return "gated-move-listener";
        }
    }

    public abstract static class ExemptMoveAdaptationListener implements Listener, Adaptation<Object> {
        static final AtomicInteger HITS = new AtomicInteger();

        @RunsWithoutLearnedAdaptation
        public void onMove(PlayerMoveEvent event) {
            HITS.incrementAndGet();
        }

        @Override
        public String getName() {
            return "exempt-move-listener";
        }
    }

    public abstract static class MoveSkillListener implements Listener, Skill<Object> {
        public void onMove(PlayerMoveEvent event) {
        }

        @Override
        public String getName() {
            return "gated-move-skill";
        }
    }

    @AfterEach
    void clearTelemetry() {
        AbilityCheckTelemetry.clear();
        MoveAdaptationListener.HITS.set(0);
        ExemptMoveAdaptationListener.HITS.set(0);
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

    private Method pickupHandler() throws NoSuchMethodException {
        Method method = PickupListener.class.getDeclaredMethod("onPickup", EntityPickupItemEvent.class);
        method.setAccessible(true);
        return method;
    }

    private Method breakHandler() throws NoSuchMethodException {
        Method method = BreakListener.class.getDeclaredMethod("onBreak", BlockBreakEvent.class);
        method.setAccessible(true);
        return method;
    }

    private PlayerInteractEvent airClick() {
        return new PlayerInteractEvent(mock(Player.class), Action.LEFT_CLICK_AIR, null, null, BlockFace.SELF, EquipmentSlot.HAND);
    }

    private PlayerInteractEvent blockClick() {
        return new PlayerInteractEvent(mock(Player.class), Action.RIGHT_CLICK_BLOCK, null, mock(Block.class), BlockFace.UP, EquipmentSlot.HAND);
    }

    private Method moveHandler(Class<?> type) throws NoSuchMethodException {
        Method m = type.getDeclaredMethod("onMove", PlayerMoveEvent.class);
        m.setAccessible(true);
        return m;
    }

    private PlayerMoveEvent move(Player player) {
        return new PlayerMoveEvent(player, new Location(null, 0, 64, 0), new Location(null, 0, 64, 1));
    }

    private Player learner(UUID playerId) {
        Player player = mock(Player.class);
        lenient().when(player.getUniqueId()).thenReturn(playerId);
        return player;
    }

    private AdaptServer installServer() {
        AdaptServer server = mock(AdaptServer.class);
        when(plugin.getAdaptServer()).thenReturn(server);
        return server;
    }

    @Test
    @DisplayName("createExecutor dispatches to a public handler method")
    void publicDispatch() throws Exception {
        PublicListener l = new PublicListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(l, handler(PublicListener.class), TestEvent.class, false);
        ex.execute(l, new TestEvent());
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("createExecutor dispatches to a private handler method")
    void privateDispatch() throws Exception {
        PrivateListener l = new PrivateListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(l, handler(PrivateListener.class), TestEvent.class, false);
        ex.execute(l, new TestEvent());
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("createExecutor records complete adaptation handler execution")
    void adaptationExecutionMeasured() throws Exception {
        MeasuredAdaptationListener listener = mock(MeasuredAdaptationListener.class, CALLS_REAL_METHODS);
        EventExecutor executor = EventHandlerInvoker.createExecutor(listener, handler(MeasuredAdaptationListener.class), TestEvent.class, false);

        executor.execute(listener, new TestEvent());

        AbilityCheckTelemetry.AbilitySnapshot snapshot = AbilityCheckTelemetry
            .abilitySnapshots(System.currentTimeMillis())
            .get("measured-listener");
        assertThat(snapshot.executionOps()).isEqualTo(1L);
        assertThat(snapshot.executionTimingMillis()).isGreaterThan(0D);
    }

    @Test
    @DisplayName("createExecutor ignores events of the wrong type")
    void typeMismatchSkipped() throws Exception {
        PublicListener l = new PublicListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(l, handler(PublicListener.class), TestEvent.class, false);
        ex.execute(l, new OtherEvent());
        assertThat(l.hits.get()).isZero();
    }

    @Test
    @DisplayName("createExecutor wraps handler exceptions in EventException")
    void exceptionWrapped() throws Exception {
        ThrowingListener l = new ThrowingListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(l, handler(ThrowingListener.class), TestEvent.class, false);
        assertThatThrownBy(() -> ex.execute(l, new TestEvent())).isInstanceOf(EventException.class);
    }

    @Test
    @DisplayName("interaction validity gate delivers born-denied air clicks")
    void gateDeliversAirClicks() throws Exception {
        InteractListener l = new InteractListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(l, interactHandler(), PlayerInteractEvent.class, true);
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
        EventExecutor ex = EventHandlerInvoker.createExecutor(l, interactHandler(), PlayerInteractEvent.class, true);
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
        EventExecutor ex = EventHandlerInvoker.createExecutor(l, interactHandler(), PlayerInteractEvent.class, true);
        ex.execute(l, blockClick());
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("interaction validity gate skips vetoed block clicks")
    void gateSkipsVetoedBlockClicks() throws Exception {
        InteractListener l = new InteractListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(l, interactHandler(), PlayerInteractEvent.class, true);
        PlayerInteractEvent event = blockClick();
        event.setUseInteractedBlock(Event.Result.DENY);
        ex.execute(l, event);
        assertThat(l.hits.get()).isZero();
    }

    @Test
    @DisplayName("unenforced executor delivers vetoed block clicks")
    void unenforcedDeliversVetoedBlockClicks() throws Exception {
        InteractListener l = new InteractListener();
        EventExecutor ex = EventHandlerInvoker.createExecutor(l, interactHandler(), PlayerInteractEvent.class, false);
        PlayerInteractEvent event = blockClick();
        event.setUseInteractedBlock(Event.Result.DENY);
        ex.execute(l, event);
        assertThat(l.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("protection probes skip Adapt component handlers")
    void protectionProbeSkipsComponentHandlers() throws Exception {
        InteractListener listener = new InteractListener();
        EventExecutor executor = EventHandlerInvoker.createExecutor(
            listener, interactHandler(), PlayerInteractEvent.class, false);
        PlayerInteractEvent event = blockClick();
        PluginManager pluginManager = mock(PluginManager.class);
        doAnswer(invocation -> {
            executor.execute(listener, invocation.getArgument(0));
            return null;
        }).when(pluginManager).callEvent(event);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            ProtectionEventProbe.dispatch(event);
        }

        assertThat(listener.hits.get()).isZero();
        executor.execute(listener, event);
        assertThat(listener.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("pickup protection probes still reach Adapt component handlers")
    void pickupProtectionProbeReachesComponentHandlers() throws Exception {
        PickupListener listener = new PickupListener();
        EventExecutor executor = EventHandlerInvoker.createExecutor(
            listener, pickupHandler(), EntityPickupItemEvent.class, false);
        EntityPickupItemEvent event = new EntityPickupItemEvent(mock(Player.class), mock(Item.class), 0);
        PluginManager pluginManager = mock(PluginManager.class);
        doAnswer(invocation -> {
            executor.execute(listener, invocation.getArgument(0));
            return null;
        }).when(pluginManager).callEvent(event);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            ProtectionEventProbe.dispatch(event);
        }

        assertThat(listener.hits.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("synthetic block authorization probes skip Adapt component handlers")
    void blockProtectionProbeSkipsComponentHandlers() throws Exception {
        BreakListener listener = new BreakListener();
        EventExecutor executor = EventHandlerInvoker.createExecutor(
            listener, breakHandler(), BlockBreakEvent.class, false);
        BlockBreakEvent event = new BlockBreakEvent(mock(Block.class), mock(Player.class));
        PluginManager pluginManager = mock(PluginManager.class);
        doAnswer(invocation -> {
            executor.execute(listener, invocation.getArgument(0));
            return null;
        }).when(pluginManager).callEvent(event);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            ProtectionEventProbe.dispatch(event);
        }

        assertThat(listener.hits.get()).isZero();
        executor.execute(listener, event);
        assertThat(listener.hits.get()).isEqualTo(1);
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

    @Test
    @DisplayName("learner gate applies to adaptation move handlers only")
    void learnerGateScope() throws Exception {
        MoveAdaptationListener adaptation = mock(MoveAdaptationListener.class, CALLS_REAL_METHODS);
        ExemptMoveAdaptationListener exempt = mock(ExemptMoveAdaptationListener.class, CALLS_REAL_METHODS);
        MoveSkillListener skill = mock(MoveSkillListener.class, CALLS_REAL_METHODS);
        PublicListener plain = new PublicListener();

        assertThat(EventHandlerInvoker.gatesOnLearnedAdaptation(adaptation, moveHandler(MoveAdaptationListener.class), PlayerMoveEvent.class)).isTrue();
        assertThat(EventHandlerInvoker.gatesOnLearnedAdaptation(exempt, moveHandler(ExemptMoveAdaptationListener.class), PlayerMoveEvent.class)).isFalse();
        assertThat(EventHandlerInvoker.gatesOnLearnedAdaptation(skill, moveHandler(MoveSkillListener.class), PlayerMoveEvent.class)).isFalse();
        assertThat(EventHandlerInvoker.gatesOnLearnedAdaptation(plain, handler(PublicListener.class), TestEvent.class)).isFalse();
        assertThat(EventHandlerInvoker.gatesOnLearnedAdaptation(adaptation, handler(MeasuredAdaptationListener.class), TestEvent.class)).isFalse();
    }

    @Test
    @DisplayName("gated move handler is skipped for a player who has not learned the adaptation")
    void gatedMoveSkippedForNonLearner() throws Exception {
        MoveAdaptationListener listener = mock(MoveAdaptationListener.class, CALLS_REAL_METHODS);
        AdaptServer server = installServer();
        UUID playerId = UUID.randomUUID();
        when(server.hasOnlineLearner(playerId, "gated-move-listener")).thenReturn(false);
        EventExecutor executor = EventHandlerInvoker.createExecutor(listener, moveHandler(MoveAdaptationListener.class), PlayerMoveEvent.class, false);

        executor.execute(listener, move(learner(playerId)));

        assertThat(MoveAdaptationListener.HITS.get()).isZero();
    }

    @Test
    @DisplayName("gated move handler runs for a player who has learned the adaptation")
    void gatedMoveDispatchedForLearner() throws Exception {
        MoveAdaptationListener listener = mock(MoveAdaptationListener.class, CALLS_REAL_METHODS);
        AdaptServer server = installServer();
        UUID playerId = UUID.randomUUID();
        when(server.hasOnlineLearner(playerId, "gated-move-listener")).thenReturn(true);
        EventExecutor executor = EventHandlerInvoker.createExecutor(listener, moveHandler(MoveAdaptationListener.class), PlayerMoveEvent.class, false);

        executor.execute(listener, move(learner(playerId)));

        assertThat(MoveAdaptationListener.HITS.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("RunsWithoutLearnedAdaptation move handler runs for a non-learner")
    void exemptMoveDispatchedForNonLearner() throws Exception {
        ExemptMoveAdaptationListener listener = mock(ExemptMoveAdaptationListener.class, CALLS_REAL_METHODS);
        AdaptServer server = installServer();
        UUID playerId = UUID.randomUUID();
        lenient().when(server.hasOnlineLearner(playerId, "exempt-move-listener")).thenReturn(false);
        EventExecutor executor = EventHandlerInvoker.createExecutor(listener, moveHandler(ExemptMoveAdaptationListener.class), PlayerMoveEvent.class, false);

        executor.execute(listener, move(learner(playerId)));

        assertThat(ExemptMoveAdaptationListener.HITS.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("learner gate fails open when the Adapt server is not ready")
    void gatedMoveDispatchedWithoutServer() throws Exception {
        MoveAdaptationListener listener = mock(MoveAdaptationListener.class, CALLS_REAL_METHODS);
        when(plugin.getAdaptServer()).thenReturn(null);
        EventExecutor executor = EventHandlerInvoker.createExecutor(listener, moveHandler(MoveAdaptationListener.class), PlayerMoveEvent.class, false);

        executor.execute(listener, move(learner(UUID.randomUUID())));

        assertThat(MoveAdaptationListener.HITS.get()).isEqualTo(1);
    }
}
