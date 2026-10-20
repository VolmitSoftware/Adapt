package art.arcane.adapt.api.golem;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
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
  private final World world = mock(World.class);
  private final World targetWorld = mock(World.class);
  private CopperGolem golem;
  private Pathfinder pathfinder;
  private Location target;

  @BeforeEach
  void setUp() {
    golem = mock(CopperGolem.class);
    pathfinder = mock(Pathfinder.class);
    when(golem.getPathfinder()).thenReturn(pathfinder);
    when(golem.getTicksLived()).thenAnswer(invocation -> ticks.get());
    when(golem.getGolemState()).thenReturn(CopperGolem.State.IDLE);
    lenient().when(world.getUID()).thenReturn(WORLD);
    target = new Location(targetWorld, 8, 64, 8);
  }

  @Test
  void combatChangesLeaveACopperGolemAlone() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10, lids);
    clearInvocations(golem, pathfinder);

    controller.setCombat(false);
    controller.setCombat(true);

    verifyNoMoreInteractions(golem, pathfinder);
  }

  @Test
  void moveTargetIsReissuedEveryRepathInterval() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);
    controller.moveTo(target, 1D);
    controller.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(5);
    controller.tick();
    verify(pathfinder, never()).moveTo(any(Location.class), anyDouble());

    ticks.addAndGet(5);
    controller.tick();
    verify(pathfinder).moveTo(target, 1D);
  }

  @Test
  void vanillaTransportActionIsResetAndOurPathRestored() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);
    controller.moveTo(target, 1D);
    controller.tick();
    clearInvocations(pathfinder);

    ticks.addAndGet(2);
    when(golem.getGolemState()).thenReturn(CopperGolem.State.GETTING_ITEM);
    controller.tick();

    verify(golem).setGolemState(CopperGolem.State.IDLE);
    InOrder order = inOrder(pathfinder);
    order.verify(pathfinder).stopPathfinding();
    order.verify(pathfinder).moveTo(target, 1D);
  }

  @Test
  void idleGolemStateIsLeftAlone() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);

    controller.tick();

    verify(golem, never()).setGolemState(any());
  }

  @Test
  void aContainerInteractionTheBrainStartedIsNotResetBeforeItsWindowEnds() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);
    Block chest = block(3, 64, 3);
    controller.interact(chest, 20);
    when(golem.getGolemState()).thenReturn(CopperGolem.State.DROPPING_ITEM);

    ticks.addAndGet(10);
    controller.tick();
    verify(golem, never()).setGolemState(CopperGolem.State.IDLE);

    ticks.addAndGet(10);
    controller.tick();
    verify(golem).setGolemState(CopperGolem.State.IDLE);
  }

  @Test
  void theBrainsInteractionPlaysTheDropAnimation() {
    CopperGolemController controller = new CopperGolemController(golem, () -> 10);

    controller.interact(block(3, 64, 3), 20);

    verify(golem).setGolemState(CopperGolem.State.DROPPING_ITEM);
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

    verify(pathfinder).stopPathfinding();
    verify(pathfinder, never()).moveTo(any(Location.class), anyDouble());
  }

  @Test
  void onlyPathsToTheControllersDestinationPassTheCopperBrainGuard() {
    CopperGolem bound = boundCopper();
    CopperBrainGuard guard = new CopperBrainGuard();
    List<Boolean> issued = new ArrayList<>();
    when(pathfinder.moveTo(any(Location.class), anyDouble())).thenAnswer(invocation -> {
      EntityPathfindEvent event = new EntityPathfindEvent(bound, invocation.getArgument(0), null);
      guard.on(event);
      issued.add(event.isCancelled());
      return !event.isCancelled();
    });
    CopperGolemController controller = new CopperGolemController(bound, () -> 10);

    controller.moveTo(target, 1D);
    controller.tick();
    EntityPathfindEvent recompute = new EntityPathfindEvent(bound, new Location(targetWorld, 8, 64, 8), null);
    EntityPathfindEvent stroll = new EntityPathfindEvent(bound, new Location(targetWorld, 2, 64, 2), null);
    guard.on(recompute);
    guard.on(stroll);
    controller.stop();
    EntityPathfindEvent afterStop = new EntityPathfindEvent(bound, new Location(targetWorld, 8, 64, 8), null);
    guard.on(afterStop);

    assertThat(issued).containsExactly(false);
    assertThat(recompute.isCancelled()).isFalse();
    assertThat(stroll.isCancelled()).isTrue();
    assertThat(afterStop.isCancelled()).isTrue();
  }

  @Test
  void aPathTheNavigationLiftsOntoTheChestTopStillPassesTheCopperBrainGuard() {
    CopperGolem bound = boundCopper();
    CopperBrainGuard guard = new CopperBrainGuard();
    List<Boolean> issued = new ArrayList<>();
    when(pathfinder.moveTo(any(Location.class), anyDouble())).thenAnswer(invocation -> {
      Location requested = invocation.getArgument(0);
      Location surface = new Location(requested.getWorld(), requested.getBlockX(), requested.getBlockY() + 1, requested.getBlockZ());
      EntityPathfindEvent event = new EntityPathfindEvent(bound, surface, null);
      guard.on(event);
      issued.add(event.isCancelled());
      return !event.isCancelled();
    });
    CopperGolemController controller = new CopperGolemController(bound, () -> 10);

    controller.moveTo(new Location(targetWorld, 4.5D, 64.5D, 0.5D), 1D);
    controller.tick();
    EntityPathfindEvent recompute = new EntityPathfindEvent(bound, new Location(targetWorld, 4, 65, 0), null);
    EntityPathfindEvent stroll = new EntityPathfindEvent(bound, new Location(targetWorld, 2, 64, 2), null);
    guard.on(recompute);
    guard.on(stroll);
    controller.stop();
    EntityPathfindEvent afterStop = new EntityPathfindEvent(bound, new Location(targetWorld, 4, 65, 0), null);
    guard.on(afterStop);

    assertThat(issued).containsExactly(false);
    assertThat(recompute.isCancelled()).isFalse();
    assertThat(stroll.isCancelled()).isTrue();
    assertThat(afterStop.isCancelled()).isTrue();
  }

  private CopperGolem boundCopper() {
    CopperGolem bound = GolemTestEntities.entity(CopperGolem.class);
    GolemState.create(new GolemState.Seed(UUID.randomUUID(), UUID.randomUUID(), GolemBody.COPPER, GolemCoreType.SORTER,
        GolemState.Anchor.of(WORLD, 0, 64, 0))).write(bound);
    when(bound.getPathfinder()).thenReturn(pathfinder);
    when(bound.getTicksLived()).thenAnswer(invocation -> ticks.get());
    when(bound.getGolemState()).thenReturn(CopperGolem.State.IDLE);
    return bound;
  }

  private Block block(int x, int y, int z) {
    Block block = mock(Block.class);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getY()).thenReturn(y);
    lenient().when(block.getZ()).thenReturn(z);
    lenient().when(block.getWorld()).thenReturn(world);
    return block;
  }
}
