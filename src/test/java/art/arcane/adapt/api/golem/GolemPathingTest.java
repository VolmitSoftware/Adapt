package art.arcane.adapt.api.golem;

import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

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
  private World world;
  private Pathfinder pathfinder;

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    pathfinder = mock(Pathfinder.class);
  }

  @Test
  void newTargetStopsTheOldPathBeforeMoving() {
    Mob mob = mob(IronGolem.class);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    Location target = at(10, 64, 0);

    pathing.moveTo(target, 1.2D);
    pathing.tick();

    InOrder order = inOrder(pathfinder);
    order.verify(pathfinder).stopPathfinding();
    order.verify(pathfinder).moveTo(target, 1.2D);
  }

  @Test
  void sameTargetIsReissuedEveryIntervalWithoutStopping() {
    Mob mob = mob(IronGolem.class);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(9);
    pathing.tick();
    verify(pathfinder, never()).moveTo(any(Location.class), anyDouble());

    ticks.addAndGet(1);
    pathing.tick();
    verify(pathfinder).moveTo(at(10, 64, 0), 1D);
    verify(pathfinder, never()).stopPathfinding();
  }

  @Test
  void goalDrivenBodiesKeepWalkingTheirCurrentPathInsteadOfReissuingIt() {
    Mob mob = mob(IronGolem.class);
    when(pathfinder.hasPath()).thenReturn(true);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, false);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(10);
    pathing.tick();
    ticks.addAndGet(10);
    pathing.tick();

    verify(pathfinder, never()).moveTo(any(Location.class), anyDouble());
    verify(pathfinder, never()).stopPathfinding();
  }

  @Test
  void goalDrivenBodiesReissueOnceTheirPathEndsOrTheTargetMoves() {
    Mob mob = mob(IronGolem.class);
    when(pathfinder.hasPath()).thenReturn(false);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, false);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(10);
    pathing.tick();
    verify(pathfinder).moveTo(at(10, 64, 0), 1D);

    when(pathfinder.hasPath()).thenReturn(true);
    ticks.addAndGet(10);
    pathing.moveTo(at(20, 64, 0), 1D);
    pathing.tick();
    verify(pathfinder).stopPathfinding();
    verify(pathfinder).moveTo(at(20, 64, 0), 1D);
  }

  @Test
  void failedPathIsRetriedOnTheNextInterval() {
    Mob mob = mob(CopperGolem.class);
    when(pathfinder.moveTo(any(Location.class), anyDouble())).thenReturn(false);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);

    pathing.tick();
    ticks.addAndGet(10);
    pathing.tick();

    verify(pathfinder, times(2)).moveTo(at(10, 64, 0), 1D);
  }

  @Test
  void smallTargetDriftKeepsTheCurrentPath() {
    Mob mob = mob(IronGolem.class);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    pathing.moveTo(at(10.9D, 64, 0), 1D);
    ticks.addAndGet(10);
    pathing.tick();

    verify(pathfinder, never()).stopPathfinding();
    verify(pathfinder).moveTo(at(10, 64, 0), 1D);
  }

  @Test
  void movedTargetIsRetargetedNoMoreThanOncePerInterval() {
    Mob mob = mob(IronGolem.class);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    pathing.moveTo(at(14, 64, 0), 1D);
    ticks.addAndGet(5);
    pathing.tick();
    verify(pathfinder, never()).moveTo(any(Location.class), anyDouble());

    ticks.addAndGet(5);
    pathing.tick();
    InOrder order = inOrder(pathfinder);
    order.verify(pathfinder).stopPathfinding();
    order.verify(pathfinder).moveTo(at(14, 64, 0), 1D);
  }

  @Test
  void stopClearsTheTargetAndThePath() {
    Mob mob = mob(IronGolem.class);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    pathing.stop();
    ticks.addAndGet(20);
    pathing.tick();

    verify(pathfinder).stopPathfinding();
    verify(pathfinder, never()).moveTo(any(Location.class), anyDouble());
  }

  @Test
  void restartReissuesImmediatelyWithinTheInterval() {
    Mob mob = mob(CopperGolem.class);
    GolemPathing pathing = new GolemPathing(mob, () -> 10, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(1);
    pathing.restart();
    pathing.tick();

    InOrder order = inOrder(pathfinder);
    order.verify(pathfinder).stopPathfinding();
    order.verify(pathfinder).moveTo(at(10, 64, 0), 1D);
  }

  @Test
  void intervalFollowsTheSupplierOnEveryTick() {
    Mob mob = mob(CopperGolem.class);
    AtomicInteger interval = new AtomicInteger(10);
    GolemPathing pathing = new GolemPathing(mob, interval::get, true);
    pathing.moveTo(at(10, 64, 0), 1D);
    pathing.tick();
    clearInvocations(pathfinder);

    interval.set(4);
    ticks.addAndGet(4);
    pathing.tick();

    verify(pathfinder).moveTo(eq(at(10, 64, 0)), eq(1D));
  }

  private <T extends Mob> T mob(Class<T> type) {
    T mob = mock(type);
    when(mob.getPathfinder()).thenReturn(pathfinder);
    when(mob.getTicksLived()).thenAnswer(invocation -> ticks.get());
    return mob;
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x, y, z);
  }
}
