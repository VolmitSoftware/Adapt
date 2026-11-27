package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemPathingTest {
  private final AtomicInteger ticks = new AtomicInteger(100);
  private WorldRef world;
  private MobRef pathfinder;

  @BeforeEach
  void setUp() {
    world = mock(WorldRef.class);
  }

  @Test
  void newTargetStopsTheOldPathBeforeMoving() {
    MobRef mob = mob();
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    Loc target = at(10, 64, 0);

    pathing.moveTo(target, 1.2D);
    pathing.tick();

    InOrder order = inOrder(pathfinder);
    order.verify(pathfinder).stopPathing();
    order.verify(pathfinder).pathTo(target, 1.2D);
  }

  @Test
  void sameTargetIsReissuedEveryIntervalWithoutStopping() {
    MobRef mob = mob();
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(9);
    pathing.tick();
    verify(pathfinder, never()).pathTo(any(Loc.class), anyDouble());

    ticks.addAndGet(1);
    pathing.tick();
    verify(pathfinder).pathTo(at(10, 64, 0), 1D);
    verify(pathfinder, never()).stopPathing();
  }

  @Test
  void goalDrivenBodiesKeepWalkingTheirCurrentPathInsteadOfReissuingIt() {
    MobRef mob = mob();
    when(mob.hasPath()).thenReturn(true);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, false);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(10);
    pathing.tick();
    ticks.addAndGet(10);
    pathing.tick();

    verify(pathfinder, never()).pathTo(any(Loc.class), anyDouble());
    verify(pathfinder, never()).stopPathing();
  }

  @Test
  void goalDrivenBodiesReissueOnceTheirPathEndsOrTheTargetMoves() {
    MobRef mob = mob();
    when(mob.hasPath()).thenReturn(false);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, false);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(10);
    pathing.tick();
    verify(pathfinder).pathTo(at(10, 64, 0), 1D);

    when(pathfinder.hasPath()).thenReturn(true);
    ticks.addAndGet(10);
    pathing.moveTo(at(20, 64, 0), 1D);
    pathing.tick();
    verify(pathfinder).stopPathing();
    verify(pathfinder).pathTo(at(20, 64, 0), 1D);
  }

  @Test
  void failedPathIsRetriedOnTheNextInterval() {
    MobRef mob = mob();
    when(mob.pathTo(any(Loc.class), anyDouble())).thenReturn(false);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);

    pathing.tick();
    ticks.addAndGet(10);
    pathing.tick();

    verify(pathfinder, times(2)).pathTo(at(10, 64, 0), 1D);
  }

  @Test
  void smallTargetDriftKeepsTheCurrentPath() {
    MobRef mob = mob();
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    pathing.moveTo(at(10.9D, 64, 0), 1D);
    ticks.addAndGet(10);
    pathing.tick();

    verify(pathfinder, never()).stopPathing();
    verify(pathfinder).pathTo(at(10, 64, 0), 1D);
  }

  @Test
  void movedTargetIsRetargetedNoMoreThanOncePerInterval() {
    MobRef mob = mob();
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    pathing.moveTo(at(14, 64, 0), 1D);
    ticks.addAndGet(5);
    pathing.tick();
    verify(pathfinder, never()).pathTo(any(Loc.class), anyDouble());

    ticks.addAndGet(5);
    pathing.tick();
    InOrder order = inOrder(pathfinder);
    order.verify(pathfinder).stopPathing();
    order.verify(pathfinder).pathTo(at(14, 64, 0), 1D);
  }

  @Test
  void stopClearsTheTargetAndThePath() {
    MobRef mob = mob();
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    pathing.stop();
    ticks.addAndGet(20);
    pathing.tick();

    verify(pathfinder).stopPathing();
    verify(pathfinder, never()).pathTo(any(Loc.class), anyDouble());
  }

  @Test
  void restartReissuesImmediatelyWithinTheInterval() {
    MobRef mob = mob();
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(1);
    pathing.restart();
    pathing.tick();

    InOrder order = inOrder(pathfinder);
    order.verify(pathfinder).stopPathing();
    order.verify(pathfinder).pathTo(at(10, 64, 0), 1D);
  }

  @Test
  void intervalFollowsTheSupplierOnEveryTick() {
    MobRef mob = mob();
    AtomicInteger interval = new AtomicInteger(10);
    GolemPathing pathing = new GolemPathing(mob, interval::get, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    interval.set(4);
    ticks.addAndGet(4);
    pathing.tick();

    verify(pathfinder).pathTo(eq(at(10, 64, 0)), eq(1D));
  }

  private MobRef mob() {
    MobRef mob = mock(MobRef.class);
    when(mob.uuid()).thenReturn(UUID.randomUUID());
    when(mob.ticksLived()).thenAnswer(invocation -> ticks.get());
    pathfinder = mob;
    return mob;
  }

  private Loc at(double x, double y, double z) {
    return new Loc(world, x, y, z);
  }
}
