package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.entity.EntityPathfindEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CopperGolemControllerTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000301");

  private final AtomicInteger ticks = new AtomicInteger(200);
  private final WorldRef world = mock(WorldRef.class);
  private final WorldRef targetWorld = mock(WorldRef.class);
  private MobRef golem;
  private MobRef pathfinder;
  private Loc target;

  @BeforeEach
  void setUp() {
    golem = mock(MobRef.class);
    pathfinder = golem;
    lenient().when(golem.uuid()).thenReturn(UUID.randomUUID());
    when(golem.ticksLived()).thenAnswer(invocation -> ticks.get());
    when(golem.copperGolemState()).thenReturn(CopperGolemState.IDLE);
    lenient().when(world.uid()).thenReturn(WORLD);
    target = new Loc(targetWorld, 8, 64, 8);
  }

  @Test
  void combatChangesLeaveACopperGolemAlone() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);
    clearInvocations(golem);

    controller.setCombat(false);
    controller.setCombat(true);

    verifyNoMoreInteractions(golem);
  }

  @Test
  void moveTargetIsReissuedEveryRepathInterval() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);
    controller.moveTo(target, 1D);
    controller.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(5);
    controller.tick();
    verify(pathfinder, never()).pathTo(any(Loc.class), anyDouble());

    ticks.addAndGet(5);
    controller.tick();
    verify(pathfinder).pathTo(target, 1D);
  }

  @Test
  void vanillaTransportActionIsResetAndOurPathRestored() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);
    controller.moveTo(target, 1D);
    controller.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(2);
    when(golem.copperGolemState()).thenReturn(CopperGolemState.GETTING_ITEM);
    controller.tick();

    verify(golem).setCopperGolemState(CopperGolemState.IDLE);
    InOrder order = inOrder(pathfinder);
    order.verify(pathfinder).stopPathing();
    order.verify(pathfinder).pathTo(target, 1D);
  }

  @Test
  void idleGolemStateIsLeftAlone() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);

    controller.tick();

    verify(golem, never()).setCopperGolemState(any());
  }

  @Test
  void aContainerInteractionTheBrainStartedIsNotResetBeforeItsWindowEnds() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);
    BlockRef chest = block(3, 64, 3);
    controller.interact(chest, 20);
    when(golem.copperGolemState()).thenReturn(CopperGolemState.DROPPING_ITEM);

    ticks.addAndGet(10);
    controller.tick();
    verify(golem, never()).setCopperGolemState(CopperGolemState.IDLE);

    ticks.addAndGet(10);
    controller.tick();
    verify(golem).setCopperGolemState(CopperGolemState.IDLE);
  }

  @Test
  void theBrainsInteractionPlaysTheDropAnimation() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);

    controller.interact(block(3, 64, 3), 20);

    verify(golem).setCopperGolemState(CopperGolemState.DROPPING_ITEM);
  }

  @Test
  void stopAndReleaseClearTheMoveTarget() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);
    controller.moveTo(target, 1D);
    controller.tick();
    clearInvocations(pathfinder);

    controller.release();
    ticks.addAndGet(20);
    controller.tick();

    verify(pathfinder).stopPathing();
    verify(pathfinder, never()).pathTo(any(Loc.class), anyDouble());
  }

  @Test
  void onlyPathsToTheControllersDestinationPassTheCopperBrainGuard() {
    MobRef bound = boundCopper();
    CopperBrainGuard guard = new CopperBrainGuard();
    List<Boolean> issued = new ArrayList<>();
    when(pathfinder.pathTo(any(Loc.class), anyDouble())).thenAnswer(invocation -> {
      EntityPathfindEvent event = new EntityPathfindEvent(bound, invocation.getArgument(0), null);
      guard.on(event);
      issued.add(event.isCancelled());
      return !event.isCancelled();
    });
    CopperGolemController controller = new CopperGolemController(bound, () -> 10);

    controller.moveTo(target, 1D);
    controller.tick();
    EntityPathfindEvent recompute = new EntityPathfindEvent(bound, new Loc(targetWorld, 8, 64, 8), null);
    EntityPathfindEvent stroll = new EntityPathfindEvent(bound, new Loc(targetWorld, 2, 64, 2), null);
    guard.on(recompute);
    guard.on(stroll);
    controller.stop();
    EntityPathfindEvent afterStop = new EntityPathfindEvent(bound, new Loc(targetWorld, 8, 64, 8), null);
    guard.on(afterStop);

    assertThat(issued).containsExactly(false);
    assertThat(recompute.isCancelled()).isFalse();
    assertThat(stroll.isCancelled()).isTrue();
    assertThat(afterStop.isCancelled()).isTrue();
  }

  @Test
  void aPathTheNavigationLiftsOntoTheChestTopStillPassesTheCopperBrainGuard() {
    MobRef bound = boundCopper();
    CopperBrainGuard guard = new CopperBrainGuard();
    List<Boolean> issued = new ArrayList<>();
    when(pathfinder.pathTo(any(Loc.class), anyDouble())).thenAnswer(invocation -> {
      Loc requested = invocation.getArgument(0);
      Loc surface = new Loc(requested.world(), requested.blockX(), requested.blockY() + 1, requested.blockZ());
      EntityPathfindEvent event = new EntityPathfindEvent(bound, surface, null);
      guard.on(event);
      issued.add(event.isCancelled());
      return !event.isCancelled();
    });
    CopperGolemController controller = new CopperGolemController(bound, () -> 10);

    controller.moveTo(new Loc(targetWorld, 4.5D, 64.5D, 0.5D), 1D);
    controller.tick();
    EntityPathfindEvent recompute = new EntityPathfindEvent(bound, new Loc(targetWorld, 4, 65, 0), null);
    EntityPathfindEvent stroll = new EntityPathfindEvent(bound, new Loc(targetWorld, 2, 64, 2), null);
    guard.on(recompute);
    guard.on(stroll);
    controller.stop();
    EntityPathfindEvent afterStop = new EntityPathfindEvent(bound, new Loc(targetWorld, 4, 65, 0), null);
    guard.on(afterStop);

    assertThat(issued).containsExactly(false);
    assertThat(recompute.isCancelled()).isFalse();
    assertThat(stroll.isCancelled()).isTrue();
    assertThat(afterStop.isCancelled()).isTrue();
  }

  private MobRef boundCopper() {
    MobRef bound = GolemTestEntities.golem(GolemBody.COPPER);
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), GolemBody.COPPER, GolemCoreType.SORTER,
        GolemAnchor.of(WORLD, 0, 64, 0))).write(bound);
    pathfinder = bound;
    when(bound.ticksLived()).thenAnswer(invocation -> ticks.get());
    when(bound.copperGolemState()).thenReturn(CopperGolemState.IDLE);
    return bound;
  }

  private BlockRef block(int x, int y, int z) {
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.x()).thenReturn(x);
    lenient().when(block.y()).thenReturn(y);
    lenient().when(block.z()).thenReturn(z);
    lenient().when(block.world()).thenReturn(world);
    return block;
  }
}
