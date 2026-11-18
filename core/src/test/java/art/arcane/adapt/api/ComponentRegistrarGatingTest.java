package art.arcane.adapt.api;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.AnnotatedHandlers;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.HandlerMethod;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Loc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComponentRegistrarGatingTest {

  public static class TestEvent extends AdaptEvent {
  }

  public static class OtherEvent extends AdaptEvent {
  }

  public static class TestCancellableEvent extends CancellableEvent {
  }

  public static class PublicListener {
    final AtomicInteger hits = new AtomicInteger();

    @Handle
    public void onTest(TestEvent e) {
      hits.incrementAndGet();
    }
  }

  public static class PrivateListener {
    final AtomicInteger hits = new AtomicInteger();

    @Handle
    private void onTest(TestEvent e) {
      hits.incrementAndGet();
    }
  }

  public static class ThrowingListener {
    @Handle
    public void onTest(TestEvent e) {
      throw new IllegalStateException("boom");
    }
  }

  public abstract static class InteractAdaptation implements Adaptation<Object> {
    static final AtomicInteger HITS = new AtomicInteger();

    @Handle
    public void onInteract(PlayerInteractEvent e) {
      HITS.incrementAndGet();
    }

    @Override
    public String getName() {
      return "interact-adaptation";
    }
  }

  public abstract static class RawInteractAdaptation implements Adaptation<Object> {
    static final AtomicInteger HITS = new AtomicInteger();

    @Handle
    @ReceiveCancelledEvents
    public void onInteract(PlayerInteractEvent e) {
      HITS.incrementAndGet();
    }

    @Override
    public String getName() {
      return "raw-interact-adaptation";
    }
  }

  public static class PolicyHandlers {
    @Handle
    public void onInteract(PlayerInteractEvent e) {
    }

    @Handle
    @ReceiveCancelledEvents
    public void onInteractRaw(PlayerInteractEvent e) {
    }

    @Handle(ignoreCancelled = false)
    public void onCancellable(TestCancellableEvent e) {
    }

    @Handle
    @ReceiveCancelledEvents
    public void onCancellableRaw(TestCancellableEvent e) {
    }

    @Handle(ignoreCancelled = true)
    public void onPlain(TestEvent e) {
    }
  }

  public abstract static class MeasuredAdaptationListener implements Adaptation<Object> {
    @Handle
    public void onTest(TestEvent event) {
    }

    @Override
    public String getName() {
      return "measured-listener";
    }
  }

  public abstract static class MoveAdaptationListener implements Adaptation<Object> {
    static final AtomicInteger HITS = new AtomicInteger();

    @Handle
    public void onMove(PlayerMoveEvent event) {
      HITS.incrementAndGet();
    }

    @Override
    public String getName() {
      return "gated-move-listener";
    }
  }

  public abstract static class ExemptMoveAdaptationListener implements Adaptation<Object> {
    static final AtomicInteger HITS = new AtomicInteger();

    @Handle
    @RunsWithoutLearnedAdaptation
    public void onMove(PlayerMoveEvent event) {
      HITS.incrementAndGet();
    }

    @Override
    public String getName() {
      return "exempt-move-listener";
    }
  }

  public abstract static class MoveSkillListener implements Skill<Object> {
    @Handle
    public void onMove(PlayerMoveEvent event) {
    }

    @Override
    public String getName() {
      return "gated-move-skill";
    }
  }

  private record Failure(Object owner, AdaptEvent event, Throwable error) {
  }

  private final List<Failure> failures = new ArrayList<>();
  private AdaptEventBus bus;
  private ComponentRegistrar registrar;
  private AdaptCore core;

  @BeforeEach
  void setUp() {
    core = TestCores.install();
    bus = new AdaptEventBus((owner, event, error) -> failures.add(new Failure(owner, event, error)));
    registrar = new ComponentRegistrar(bus);
  }

  @AfterEach
  void tearDown() {
    registrar.unregisterAll();
    TestCores.clear();
    AbilityCheckTelemetry.clear();
    InteractAdaptation.HITS.set(0);
    RawInteractAdaptation.HITS.set(0);
    MoveAdaptationListener.HITS.set(0);
    ExemptMoveAdaptationListener.HITS.set(0);
    assertTrue(failures.isEmpty(), failures::toString);
  }

  private static HandlerMethod handler(Class<?> type, String name) {
    for (HandlerMethod method : AnnotatedHandlers.of(type)) {
      if (method.method().getName().equals(name)) {
        return method;
      }
    }
    throw new AssertionError(type.getName() + "#" + name);
  }

  private static PlayerInteractEvent airClick() {
    return new PlayerInteractEvent(mock(PlayerRef.class), InteractAction.LEFT_CLICK_AIR, null, null, null, null, null,
        UseResult.DENY, UseResult.DEFAULT);
  }

  private static PlayerInteractEvent blockClick() {
    return new PlayerInteractEvent(mock(PlayerRef.class), InteractAction.RIGHT_CLICK_BLOCK, null, null,
        mock(BlockRef.class), null, null, UseResult.DEFAULT, UseResult.DEFAULT);
  }

  private static PlayerMoveEvent move(PlayerRef player) {
    WorldRef world = mock(WorldRef.class);
    return new PlayerMoveEvent(player, new Loc(world, 0, 64, 0, 0F, 0F), new Loc(world, 0, 64, 1, 0F, 0F));
  }

  private static PlayerRef learner(UUID playerId) {
    PlayerRef player = mock(PlayerRef.class);
    lenient().when(player.uuid()).thenReturn(playerId);
    return player;
  }

  private AdaptServer installServer() {
    AdaptServer server = mock(AdaptServer.class);
    TestCores.setField(core, "adaptServer", server);
    return server;
  }

  @Test
  @DisplayName("the bus dispatches to a public handler method")
  void publicDispatch() {
    PublicListener l = new PublicListener();
    assertTrue(registrar.register(l));
    bus.post(new TestEvent());
    assertEquals(1, l.hits.get());
    assertTrue(failures.isEmpty());
  }

  @Test
  @DisplayName("the bus dispatches to a private handler method")
  void privateDispatch() {
    PrivateListener l = new PrivateListener();
    assertTrue(registrar.register(l));
    bus.post(new TestEvent());
    assertEquals(1, l.hits.get());
    assertTrue(failures.isEmpty());
  }

  @Test
  @DisplayName("component registration records complete adaptation handler execution")
  void adaptationExecutionMeasured() {
    MeasuredAdaptationListener listener = mock(MeasuredAdaptationListener.class, CALLS_REAL_METHODS);
    assertTrue(registrar.register(listener));

    bus.post(new TestEvent());

    AbilityCheckTelemetry.AbilitySnapshot snapshot = AbilityCheckTelemetry
        .abilitySnapshots(System.currentTimeMillis())
        .get("measured-listener");
    assertEquals(1L, snapshot.executionOps());
    assertTrue(snapshot.executionTimingMillis() > 0D);
  }

  @Test
  @DisplayName("the bus never delivers events of the wrong type")
  void typeMismatchSkipped() {
    PublicListener l = new PublicListener();
    registrar.register(l);
    bus.post(new OtherEvent());
    assertEquals(0, l.hits.get());
  }

  @Test
  @DisplayName("handler exceptions go to the bus error handler")
  void exceptionWrapped() {
    ThrowingListener l = new ThrowingListener();
    registrar.register(l);
    TestEvent event = new TestEvent();

    bus.post(event);

    assertEquals(1, failures.size());
    Failure failure = failures.getFirst();
    assertSame(l, failure.owner());
    assertSame(event, failure.event());
    IllegalStateException error = assertInstanceOf(IllegalStateException.class, failure.error());
    assertEquals("boom", error.getMessage());
    failures.clear();
  }

  @Test
  @DisplayName("interaction validity gate delivers born-denied air clicks")
  void gateDeliversAirClicks() {
    registrar.register(mock(InteractAdaptation.class, CALLS_REAL_METHODS));
    PlayerInteractEvent event = airClick();
    assertEquals(UseResult.DENY, event.useBlock());
    assertTrue(event.isCancelled());
    bus.post(event);
    assertEquals(1, InteractAdaptation.HITS.get());
  }

  @Test
  @DisplayName("interaction validity gate skips plugin-cancelled air clicks")
  void gateSkipsCancelledAirClicks() {
    registrar.register(mock(InteractAdaptation.class, CALLS_REAL_METHODS));
    PlayerInteractEvent event = airClick();
    event.setCancelled(true);
    assertEquals(UseResult.DENY, event.useItem());
    bus.post(event);
    assertEquals(0, InteractAdaptation.HITS.get());
  }

  @Test
  @DisplayName("interaction validity gate delivers allowed block clicks")
  void gateDeliversAllowedBlockClicks() {
    registrar.register(mock(InteractAdaptation.class, CALLS_REAL_METHODS));
    bus.post(blockClick());
    assertEquals(1, InteractAdaptation.HITS.get());
  }

  @Test
  @DisplayName("interaction validity gate skips vetoed block clicks")
  void gateSkipsVetoedBlockClicks() {
    registrar.register(mock(InteractAdaptation.class, CALLS_REAL_METHODS));
    PlayerInteractEvent event = blockClick();
    event.setUseBlock(UseResult.DENY);
    bus.post(event);
    assertEquals(0, InteractAdaptation.HITS.get());
  }

  @Test
  @DisplayName("ReceiveCancelledEvents interact handler delivers vetoed block clicks")
  void unenforcedDeliversVetoedBlockClicks() {
    registrar.register(mock(RawInteractAdaptation.class, CALLS_REAL_METHODS));
    PlayerInteractEvent event = blockClick();
    event.setUseBlock(UseResult.DENY);
    bus.post(event);
    assertEquals(1, RawInteractAdaptation.HITS.get());
  }

  @Test
  @DisplayName("interact handlers register with ignoreCancelled=false")
  void interactRegistersWithoutIgnoreCancelled() {
    assertFalse(ComponentRegistrar.ignoresCancelled(handler(PolicyHandlers.class, "onInteract")));
  }

  @Test
  @DisplayName("component handlers of other cancellable events skip cancelled events")
  void cancellableForcedTrue() {
    HandlerMethod method = handler(PolicyHandlers.class, "onCancellable");
    assertFalse(method.ignoreCancelled());
    assertTrue(ComponentRegistrar.ignoresCancelled(method));
  }

  @Test
  @DisplayName("ReceiveCancelledEvents opts a cancellable handler into cancelled events")
  void receiveCancelledOptsOut() {
    assertFalse(ComponentRegistrar.ignoresCancelled(handler(PolicyHandlers.class, "onCancellableRaw")));
  }

  @Test
  @DisplayName("non-cancellable events use the Handle ignoreCancelled flag")
  void nonCancellableUsesAnnotation() {
    assertTrue(ComponentRegistrar.ignoresCancelled(handler(PolicyHandlers.class, "onPlain")));
  }

  @Test
  @DisplayName("interaction validity applies only to PlayerInteractEvent without ReceiveCancelledEvents")
  void enforcementScope() {
    assertTrue(ComponentRegistrar.enforcesInteractionValidity(handler(PolicyHandlers.class, "onInteract")));
    assertFalse(ComponentRegistrar.enforcesInteractionValidity(handler(PolicyHandlers.class, "onInteractRaw")));
    assertFalse(ComponentRegistrar.enforcesInteractionValidity(handler(PolicyHandlers.class, "onCancellable")));
  }

  @Test
  @DisplayName("learner gate applies to adaptation move handlers only")
  void learnerGateScope() {
    MoveAdaptationListener adaptation = mock(MoveAdaptationListener.class, CALLS_REAL_METHODS);
    ExemptMoveAdaptationListener exempt = mock(ExemptMoveAdaptationListener.class, CALLS_REAL_METHODS);
    MoveSkillListener skill = mock(MoveSkillListener.class, CALLS_REAL_METHODS);
    PublicListener plain = new PublicListener();

    assertTrue(ComponentRegistrar.gatesOnLearner(adaptation, handler(MoveAdaptationListener.class, "onMove")));
    assertFalse(ComponentRegistrar.gatesOnLearner(exempt, handler(ExemptMoveAdaptationListener.class, "onMove")));
    assertFalse(ComponentRegistrar.gatesOnLearner(skill, handler(MoveSkillListener.class, "onMove")));
    assertFalse(ComponentRegistrar.gatesOnLearner(plain, handler(PublicListener.class, "onTest")));
    assertFalse(ComponentRegistrar.gatesOnLearner(adaptation, handler(MeasuredAdaptationListener.class, "onTest")));
  }

  @Test
  @DisplayName("gated move handler is skipped for a player who has not learned the adaptation")
  void gatedMoveSkippedForNonLearner() {
    registrar.register(mock(MoveAdaptationListener.class, CALLS_REAL_METHODS));
    AdaptServer server = installServer();
    UUID playerId = UUID.randomUUID();
    when(server.hasOnlineLearner(playerId, "gated-move-listener")).thenReturn(false);

    bus.post(move(learner(playerId)));

    assertEquals(0, MoveAdaptationListener.HITS.get());
  }

  @Test
  @DisplayName("gated move handler runs for a player who has learned the adaptation")
  void gatedMoveDispatchedForLearner() {
    registrar.register(mock(MoveAdaptationListener.class, CALLS_REAL_METHODS));
    AdaptServer server = installServer();
    UUID playerId = UUID.randomUUID();
    when(server.hasOnlineLearner(playerId, "gated-move-listener")).thenReturn(true);

    bus.post(move(learner(playerId)));

    assertEquals(1, MoveAdaptationListener.HITS.get());
  }

  @Test
  @DisplayName("RunsWithoutLearnedAdaptation move handler runs for a non-learner")
  void exemptMoveDispatchedForNonLearner() {
    registrar.register(mock(ExemptMoveAdaptationListener.class, CALLS_REAL_METHODS));
    AdaptServer server = installServer();
    UUID playerId = UUID.randomUUID();
    lenient().when(server.hasOnlineLearner(playerId, "exempt-move-listener")).thenReturn(false);

    bus.post(move(learner(playerId)));

    assertEquals(1, ExemptMoveAdaptationListener.HITS.get());
  }

  @Test
  @DisplayName("learner gate fails open when the Adapt server is not ready")
  void gatedMoveDispatchedWithoutServer() {
    registrar.register(mock(MoveAdaptationListener.class, CALLS_REAL_METHODS));
    TestCores.setField(core, "adaptServer", null);

    bus.post(move(learner(UUID.randomUUID())));

    assertEquals(1, MoveAdaptationListener.HITS.get());
  }
}
