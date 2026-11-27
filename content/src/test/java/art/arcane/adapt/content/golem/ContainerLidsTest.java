package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.LidRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class ContainerLidsTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000b64");
  private static final long TICK = 50L;

  private final FakeSpace space = new FakeSpace();
  private WorldRef world;
  private long now = 1_000_000L;
  private ContainerLids lids;

  @BeforeEach
  void setUp() {
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    lids = new ContainerLids(space, () -> now);
  }

  @Test
  void aVisitOpensTheLidOnceHoweverManyGolemsArrive() {
    BlockRef chest = space.chest(3, 64, 3);

    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);

    assertThat(space.lidOf(chest).opens).isEqualTo(1);
    assertThat(space.lidOf(chest).closes).isZero();
  }

  @Test
  void theLidClosesOnceFifteenTicksAfterTheVisit() {
    BlockRef chest = space.chest(3, 64, 3);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);

    advance(14);
    assertThat(space.lidOf(chest).closes).isZero();

    advance(1);
    assertThat(space.lidOf(chest).closes).isEqualTo(1);

    advance(100);
    assertThat(space.lidOf(chest).opens).isEqualTo(1);
    assertThat(space.lidOf(chest).closes).isEqualTo(1);
    assertThat(space.scheduled).isEmpty();
  }

  @Test
  void anOverlappingVisitExtendsTheCloseInsteadOfStackingAnotherPair() {
    BlockRef chest = space.chest(3, 64, 3);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    advance(10);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);

    advance(14);
    assertThat(space.lidOf(chest).closes).isZero();

    advance(1);
    assertThat(space.lidOf(chest).opens).isEqualTo(1);
    assertThat(space.lidOf(chest).closes).isEqualTo(1);
    assertThat(space.scheduledTotal).isLessThanOrEqualTo(2);
  }

  @Test
  void aLongerHoldKeepsTheLidOpenForThatLongAndNoVisitShortensIt() {
    BlockRef chest = space.chest(3, 64, 3);
    lids.visit(chest, 45);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);

    advance(44);
    assertThat(space.lidOf(chest).closes).isZero();

    advance(1);
    assertThat(space.lidOf(chest).closes).isEqualTo(1);
  }

  @Test
  void aVisitAfterTheCloseOpensTheLidAgain() {
    BlockRef chest = space.chest(3, 64, 3);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    advance(15);

    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    advance(15);

    assertThat(space.lidOf(chest).opens).isEqualTo(2);
    assertThat(space.lidOf(chest).closes).isEqualTo(2);
  }

  @Test
  void aContainerThePlayersHaveOpenIsNeitherOpenedNorClosed() {
    BlockRef chest = space.chest(3, 64, 3);
    space.lidOf(chest).viewed = true;

    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    advance(100);

    assertThat(space.lidOf(chest).opens).isZero();
    assertThat(space.lidOf(chest).closes).isZero();
    assertThat(space.scheduledTotal).isZero();
  }

  @Test
  void aLidSomeoneElseAlreadyHoldsOpenIsLeftAlone() {
    BlockRef chest = space.chest(3, 64, 3);
    space.lidOf(chest).open = true;

    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    advance(100);

    assertThat(space.lidOf(chest).opens).isZero();
    assertThat(space.lidOf(chest).closes).isZero();
    assertThat(space.scheduledTotal).isZero();
  }

  @Test
  void aPlayerWhoOpensTheContainerDuringTheVisitKeepsItOpenUntilTheyLeave() {
    BlockRef chest = space.chest(3, 64, 3);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    advance(5);
    space.lidOf(chest).viewed = true;

    advance(10);
    advance(30);
    assertThat(space.lidOf(chest).closes).isZero();

    space.lidOf(chest).viewed = false;
    advance(15);

    assertThat(space.lidOf(chest).opens).isEqualTo(1);
    assertThat(space.lidOf(chest).closes).isEqualTo(1);
  }

  @Test
  void aContainerWithoutALidIsSkipped() {
    BlockRef hopper = space.hopper(3, 64, 3);

    lids.visit(hopper, ContainerLids.CLOSE_TICKS);
    advance(100);

    assertThat(space.scheduledTotal).isZero();
  }

  @Test
  void aDoubleChestAnimatesBothHalvesWithOneCloseEach() {
    BlockRef left = space.block(3, 64, 3);
    BlockRef right = space.block(4, 64, 3);
    space.doubleChest(left, right);

    lids.visit(right, ContainerLids.CLOSE_TICKS);
    lids.visit(left, ContainerLids.CLOSE_TICKS);
    advance(15);

    assertThat(space.lidOf(left).opens).isEqualTo(1);
    assertThat(space.lidOf(right).opens).isEqualTo(1);
    assertThat(space.lidOf(left).closes).isEqualTo(1);
    assertThat(space.lidOf(right).closes).isEqualTo(1);
  }

  @Test
  void unloadingTheChunkCancelsThePendingCloseAndTheNextVisitStartsFresh() {
    BlockRef chest = space.chest(3, 64, 3);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);

    lids.on(unload(0, 0));
    advance(100);
    assertThat(space.lidOf(chest).closes).isZero();

    space.lidOf(chest).open = false;
    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    advance(15);

    assertThat(space.lidOf(chest).opens).isEqualTo(2);
    assertThat(space.lidOf(chest).closes).isEqualTo(1);
  }

  @Test
  void unloadingAnotherChunkLeavesThePendingCloseAlone() {
    BlockRef chest = space.chest(3, 64, 3);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);

    lids.on(unload(1, 0));
    advance(15);

    assertThat(space.lidOf(chest).closes).isEqualTo(1);
  }

  @Test
  void breakingTheBlockCancelsThePendingClose() {
    BlockRef chest = space.chest(3, 64, 3);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    BlockBreakEvent broken = mock(BlockBreakEvent.class);
    lenient().when(broken.block()).thenReturn(chest);

    lids.on(broken);
    advance(100);

    assertThat(space.lidOf(chest).closes).isZero();
  }

  @Test
  void aBlockThatLostItsLidBeforeTheCloseIsDroppedWithoutClosing() {
    BlockRef chest = space.chest(3, 64, 3);
    lids.visit(chest, ContainerLids.CLOSE_TICKS);
    space.remove(chest);

    advance(15);

    assertThat(space.lidOf(chest).closes).isZero();
    assertThat(space.scheduled).isEmpty();
  }

  @Test
  void aSchedulerThatRefusesTheCloseClosesTheLidAtOnce() {
    BlockRef chest = space.chest(3, 64, 3);
    space.refusing = true;

    lids.visit(chest, ContainerLids.CLOSE_TICKS);

    assertThat(space.lidOf(chest).opens).isEqualTo(1);
    assertThat(space.lidOf(chest).closes).isEqualTo(1);
  }

  @Test
  void shuttingDownClosesEveryLidTheGolemsLeftOpen() {
    BlockRef first = space.chest(3, 64, 3);
    BlockRef second = space.chest(20, 64, 3);
    lids.visit(first, ContainerLids.CLOSE_TICKS);
    lids.visit(second, ContainerLids.CLOSE_TICKS);

    lids.shutdown();
    advance(100);

    assertThat(space.lidOf(first).closes).isEqualTo(1);
    assertThat(space.lidOf(second).closes).isEqualTo(1);
  }

  private ChunkEvent unload(int chunkX, int chunkZ) {
    return new ChunkEvent(world, new ChunkPos(chunkX, chunkZ), ChunkEvent.Phase.UNLOAD, false);
  }

  private void advance(int ticks) {
    for (int tick = 0; tick < ticks; tick++) {
      now += TICK;
      space.runDue(now);
    }
  }

  private final class FakeSpace implements LidSpace {
    private final Map<BlockRef, FakeLid> lids = new HashMap<>();
    private final Map<BlockRef, List<BlockRef>> doubles = new HashMap<>();
    private final List<BlockRef> hoppers = new ArrayList<>();
    private final List<BlockRef> removed = new ArrayList<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private int scheduledTotal;
    private boolean refusing;

    private BlockRef block(int x, int y, int z) {
      BlockRef block = mock(BlockRef.class);
      lenient().when(block.world()).thenReturn(world);
      lenient().when(block.x()).thenReturn(x);
      lenient().when(block.y()).thenReturn(y);
      lenient().when(block.z()).thenReturn(z);
      return block;
    }

    private BlockRef chest(int x, int y, int z) {
      BlockRef block = block(x, y, z);
      lids.put(block, new FakeLid());
      return block;
    }

    private BlockRef hopper(int x, int y, int z) {
      BlockRef block = block(x, y, z);
      hoppers.add(block);
      return block;
    }

    private void doubleChest(BlockRef left, BlockRef right) {
      lids.put(left, new FakeLid());
      lids.put(right, new FakeLid());
      doubles.put(left, List.of(left, right));
      doubles.put(right, List.of(left, right));
    }

    private FakeLid lidOf(BlockRef block) {
      return lids.get(block);
    }

    private void remove(BlockRef block) {
      removed.add(block);
    }

    private void runDue(long clock) {
      List<Scheduled> due = new ArrayList<>();
      for (Scheduled task : scheduled) {
        if (task.dueAt <= clock) {
          due.add(task);
        }
      }
      scheduled.removeAll(due);
      for (Scheduled task : due) {
        task.task.run();
      }
    }

    @Override
    public List<BlockRef> halves(BlockRef container) {
      if (hoppers.contains(container) || !lids.containsKey(container)) {
        return List.of();
      }
      return doubles.getOrDefault(container, List.of(container));
    }

    @Override
    public Optional<LidRef> lid(BlockRef half) {
      return removed.contains(half) ? Optional.empty() : Optional.ofNullable(lids.get(half));
    }

    @Override
    public boolean runAt(BlockRef half, Runnable task, int delayTicks) {
      if (refusing) {
        return false;
      }
      scheduledTotal++;
      scheduled.add(new Scheduled(now + delayTicks * TICK, task));
      return true;
    }
  }

  private static final class FakeLid implements LidRef {
    private boolean viewed;
    private boolean open;
    private int opens;
    private int closes;

    @Override
    public boolean viewed() {
      return viewed;
    }

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public void open() {
      opens++;
      open = true;
    }

    @Override
    public void close() {
      closes++;
      open = false;
    }
  }

  private static final class Scheduled {
    private final long dueAt;
    private final Runnable task;

    private Scheduled(long dueAt, Runnable task) {
      this.dueAt = dueAt;
      this.task = task;
    }
  }
}
