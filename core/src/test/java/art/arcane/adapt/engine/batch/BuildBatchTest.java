package art.arcane.adapt.engine.batch;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/** Build batches place and break for real, pay honestly, stay under the tick cap, and undo without duplicating. */
class BuildBatchTest {
  private static final Id STONE = Id.minecraft("stone");
  private static final Id OAK = Id.minecraft("oak_planks");
  private static final String OWNER = "adapt:architect/upgrade/master_builder";
  private static final long MINUTE = 60_000L;

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Mason", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class, SkillPickaxes.class));
    adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void placesPacedRealBlocksPaidFromTheHandThenTheInventoryAndMarksThemPlaced() {
    player.holding(new FakeItem(STONE, 3));
    player.inventory().setItem(20, new FakeItem(STONE, 5));
    AtomicReference<BuildResult> result = new AtomicReference<>();

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class)) {
      BuildBatches.get().start(row(6, 0).pace(2).onDone(result::set).build());
      platform.scheduler().advance(1);
      assertThat(placed(row())).isEqualTo(2);
      assertThat(player.inventory().mainHand().amount()).isEqualTo(1);
      platform.scheduler().advance(5);
      for (BlockRef block : row()) {
        provenance.verify(() -> XpProvenance.recordPlacement(block));
      }
    }

    assertThat(result.get()).isEqualTo(new BuildResult(6, 0, 0, 0, BuildResult.Stop.DONE, row()));
    assertThat(player.inventory().mainHand().isEmpty()).isTrue();
    assertThat(player.inventory().item(20).amount()).isEqualTo(2);
    assertThat(count(STONE)).isEqualTo(2);
    for (BlockRef block : row()) {
      assertThat(block.type()).isEqualTo(STONE);
    }
  }

  @Test
  void stopsWhenTheItemsRunOutAndNeverPlacesForFree() {
    player.holding(new FakeItem(STONE, 2));
    AtomicReference<BuildResult> result = new AtomicReference<>();

    BuildBatches.get().start(row(6, 0).pace(6).onDone(result::set).build());
    platform.scheduler().advance(2);

    assertThat(result.get().placed()).isEqualTo(2);
    assertThat(result.get().stop()).isEqualTo(BuildResult.Stop.NO_ITEMS);
    assertThat(placed(row())).isEqualTo(2);
  }

  @Test
  void stopsAtTheFirstProtectedBlockAndKeepsTheItem() {
    player.holding(new FakeItem(STONE, 6));
    platform.protection().deny(true);
    AtomicReference<BuildResult> result = new AtomicReference<>();

    BuildBatches.get().start(row(6, 0).onDone(result::set).build());
    platform.scheduler().advance(2);

    assertThat(result.get().stop()).isEqualTo(BuildResult.Stop.PROTECTED);
    assertThat(placed(row())).isZero();
    assertThat(count(STONE)).isEqualTo(6);
  }

  @Test
  void theLimitStopsTheBatch() {
    player.holding(new FakeItem(STONE, 6));
    AtomicReference<BuildResult> result = new AtomicReference<>();

    BuildBatches.get().start(row(6, 0).limit(4).pace(8).onDone(result::set).build());
    platform.scheduler().advance(2);

    assertThat(result.get()).isEqualTo(new BuildResult(4, 0, 0, 0, BuildResult.Stop.LIMIT, row().subList(0, 4)));
  }

  @Test
  void allBatchesTogetherStayUnderTheServerCapPerTick() {
    FakePlayer other = platform.player("Other", 0.5D, 61D, 4.5D);
    adapt.join(other);
    player.holding(new FakeItem(STONE, 64));
    other.holding(new FakeItem(STONE, 64));
    int cap = 64;

    BuildBatches.get().start(batch(player, 61).pace(50).build());
    BuildBatches.get().start(batch(other, 62).pace(50).build());
    platform.scheduler().advance(1);

    assertThat(placed(layer(61)) + placed(layer(62))).isEqualTo(cap);
    platform.scheduler().advance(1);
    assertThat(placed(layer(61)) + placed(layer(62))).isEqualTo(100);
  }

  @Test
  void onePlayerRunsOneBatchAtATime() {
    player.holding(new FakeItem(STONE, 12));

    assertThat(BuildBatches.get().start(row(6, 0).pace(1).build())).isTrue();
    assertThat(BuildBatches.get().start(row(6, 3).pace(1).build())).isFalse();
  }

  @Test
  void breaksAreRealBreaksWithTheHeldTool() {
    for (BlockRef block : row()) {
      platform.world().set(block.x(), block.y(), block.z(), "stone");
    }
    player.holding(FakeItem.tool("iron_pickaxe", 250));
    BuildBatch.Builder batch = BuildBatch.of(owner(), player).pace(8);
    for (BlockRef block : row()) {
      batch.breakBlock(block, 1);
    }
    AtomicReference<BuildResult> result = new AtomicReference<>();

    BuildBatches.get().start(batch.onDone(result::set).build());
    platform.scheduler().advance(2);

    assertThat(result.get().broken()).isEqualTo(6);
    assertThat(platform.protection().broken()).hasSize(6);
    assertThat(placed(row())).isZero();
  }

  @Test
  void aBreakNeedingAKindOfToolStopsWhenTheHeldItemIsNotOne() {
    for (BlockRef block : row()) {
      platform.world().set(block.x(), block.y(), block.z(), "stone");
    }
    player.holding(FakeItem.tool("iron_pickaxe", 250));
    BuildBatch.Builder batch = BuildBatch.of(owner(), player).pace(2);
    for (BlockRef block : row()) {
      batch.breakWith(block, 1, (ItemRef held) -> held.type().path().endsWith("_pickaxe"));
    }
    AtomicReference<BuildResult> result = new AtomicReference<>();

    BuildBatches.get().start(batch.onDone(result::set).build());
    platform.scheduler().advance(1);
    player.holding(FakeItem.of("stick"));
    platform.scheduler().advance(5);

    assertThat(result.get().broken()).isEqualTo(2);
    assertThat(result.get().stop()).isEqualTo(BuildResult.Stop.TOOL_WORN);
    assertThat(platform.protection().broken()).hasSize(2);
  }

  @Test
  void undoGivesBackExactlyWhatEachBlockCostAndOnlyOnce() {
    ItemRef paid = new FakeItem(STONE, 6);
    player.holding(paid);
    BuildBatch.Builder batch = BuildBatch.of(owner(), player);
    BlockStateRef stone = AdaptCore.platform().host().items().blockState(STONE);
    for (BlockRef block : row()) {
      batch.place(block, stone, paid);
    }
    build(batch.undoable(3));
    assertThat(count(STONE)).isZero();

    AtomicReference<BuildResult> undone = new AtomicReference<>();
    assertThat(BuildBatches.get().undo(player, Set.of(OWNER), MINUTE, 8, undone::set)).isTrue();
    platform.scheduler().advance(2);

    assertThat(undone.get().undone()).isEqualTo(6);
    assertThat(placed(row())).isZero();
    assertThat(count(STONE)).isEqualTo(6);
    assertThat(BuildBatches.get().undo(player, Set.of(OWNER), MINUTE, 8, undone::set)).isFalse();
    assertThat(count(STONE)).isEqualTo(6);
  }

  @Test
  void undoSkipsBlocksThatChangedSoBreakingThemFirstGainsNothing() {
    player.holding(new FakeItem(STONE, 6));
    build(row(6, 0).undoable(1));
    List<BlockRef> blocks = row();
    platform.world().set(blocks.get(0).x(), 61, 0, "air");
    platform.world().set(blocks.get(1).x(), 61, 0, "oak_planks");
    player.inventory().addItem(new FakeItem(STONE, 1));

    AtomicReference<BuildResult> undone = new AtomicReference<>();
    BuildBatches.get().undo(player, Set.of(OWNER), MINUTE, 8, undone::set);
    platform.scheduler().advance(2);

    assertThat(undone.get().undone()).isEqualTo(4);
    assertThat(undone.get().skipped()).isEqualTo(2);
    assertThat(count(STONE)).isEqualTo(5);
    assertThat(blocks.get(1).type()).isEqualTo(OAK);
  }

  @Test
  void undoLeavesAChestThatHoldsItems() {
    Id chest = Id.minecraft("chest");
    player.holding(new FakeItem(chest, 2));
    BlockStateRef state = AdaptCore.platform().host().items().blockState(chest);
    BlockRef full = platform.world().blockAt(1, 61, 0);
    BlockRef empty = platform.world().blockAt(2, 61, 0);
    build(BuildBatch.of(owner(), player).place(full, state).place(empty, state).undoable(1));
    full.container().addItem(new FakeItem(Id.minecraft("diamond"), 4));

    AtomicReference<BuildResult> undone = new AtomicReference<>();
    BuildBatches.get().undo(player, Set.of(OWNER), MINUTE, 8, undone::set);
    platform.scheduler().advance(2);

    assertThat(undone.get().undone()).isEqualTo(1);
    assertThat(undone.get().blocks()).containsExactly(empty);
    assertThat(full.type()).isEqualTo(chest);
    assertThat(full.container().contains(Id.minecraft("diamond"), 4)).isTrue();
    assertThat(count(chest)).isEqualTo(1);
  }

  @Test
  void undoSkipsABlockThatGrewMoreItemsSinceItWasPlaced() {
    Id slab = Id.minecraft("oak_slab");
    player.holding(new FakeItem(slab, 2));
    BlockStateRef single = new FakeBlockState(slab, Map.of("type", "bottom"));
    BlockRef grown = platform.world().blockAt(1, 61, 0);
    BlockRef kept = platform.world().blockAt(2, 61, 0);
    build(BuildBatch.of(owner(), player).place(grown, single, new FakeItem(slab, 1))
        .place(kept, single, new FakeItem(slab, 1)).undoable(1));
    platform.world().set(grown.pos(), new FakeBlockState(slab, Map.of("type", "double")));

    AtomicReference<BuildResult> undone = new AtomicReference<>();
    BuildBatches.get().undo(player, Set.of(OWNER), MINUTE, 8, undone::set);
    platform.scheduler().advance(2);

    assertThat(undone.get().undone()).isEqualTo(1);
    assertThat(grown.state().property("type")).isEqualTo("double");
    assertThat(kept.isAir()).isTrue();
    assertThat(count(slab)).isEqualTo(1);
  }

  @Test
  void aBlockBrokenAndPlacedAgainIsReturnedOnceAndTheWorldLosesIt() {
    player.holding(new FakeItem(STONE, 6));
    build(row(6, 0).undoable(1));
    platform.world().set(1, 61, 0, "air");
    player.inventory().addItem(new FakeItem(STONE, 1));
    platform.world().set(1, 61, 0, "stone");
    player.inventory().removeItem(STONE, 1);
    int blocksAndItemsBefore = placed(row()) + count(STONE);

    BuildBatches.get().undo(player, Set.of(OWNER), MINUTE, 8, (BuildResult ignored) -> {
    });
    platform.scheduler().advance(2);

    assertThat(placed(row()) + count(STONE)).isEqualTo(blocksAndItemsBefore);
  }

  @Test
  void undoWaitsForItsWindowAndIgnoresOtherOwners() {
    player.holding(new FakeItem(STONE, 6));
    build(row(6, 0).undoable(1));

    assertThat(BuildBatches.get().undo(player, Set.of("adapt:architect/upgrade/skyhook"), MINUTE, 8, (BuildResult r) -> {
    })).isFalse();
    assertThat(BuildBatches.get().undo(player, Set.of(OWNER), -1L, 8, (BuildResult r) -> {
    })).isFalse();
    assertThat(BuildBatches.get().undoable(player)).isEqualTo(1);
  }

  @Test
  void aNewBatchBeyondTheKeptCountForgetsTheOldest() {
    player.holding(new FakeItem(STONE, 18));
    build(row(6, 0).undoable(2));
    build(row(6, 3).undoable(2));
    build(row(6, 4).undoable(2));

    assertThat(BuildBatches.get().undoable(player)).isEqualTo(2);
  }

  @Test
  void theJournalSurvivesARestartAndStillReturnsOnlyOnce() {
    player.holding(new FakeItem(STONE, 6));
    build(row(6, 0).undoable(1));
    adapt.close();
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class, SkillPickaxes.class));
    adapt.join(player);

    AtomicReference<BuildResult> undone = new AtomicReference<>();
    assertThat(BuildBatches.get().undo(player, Set.of(OWNER), MINUTE, 8, undone::set)).isTrue();
    platform.scheduler().advance(2);

    assertThat(undone.get().undone()).isEqualTo(6);
    assertThat(count(STONE)).isEqualTo(6);
    adapt.close();
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class, SkillPickaxes.class));
    adapt.join(player);
    assertThat(BuildBatches.get().undo(player, Set.of(OWNER), MINUTE, 8, undone::set)).isFalse();
  }

  private void build(BuildBatch.Builder batch) {
    AtomicReference<BuildResult> result = new AtomicReference<>();
    assertThat(BuildBatches.get().start(batch.pace(8).onDone(result::set).build())).isTrue();
    platform.scheduler().advance(2);
    assertThat(result.get()).isNotNull();
  }

  private Adaptation<?> owner() {
    return adapt.core().getAdaptServer().getSkillRegistry().getAdaptation(Id.parse(OWNER));
  }

  private BuildBatch.Builder row(int length, int z) {
    BlockStateRef stone = AdaptCore.platform().host().items().blockState(STONE);
    BuildBatch.Builder batch = BuildBatch.of(owner(), player);
    for (int x = 1; x <= length; x++) {
      batch.place(platform.world().blockAt(x, 61, z), stone);
    }
    return batch;
  }

  private BuildBatch.Builder batch(FakePlayer builder, int y) {
    BlockStateRef stone = AdaptCore.platform().host().items().blockState(STONE);
    BuildBatch.Builder batch = BuildBatch.of(owner(), builder);
    for (BlockRef block : layer(y)) {
      batch.place(block, stone);
    }
    return batch;
  }

  private List<BlockRef> row() {
    List<BlockRef> blocks = new ArrayList<>();
    for (int x = 1; x <= 6; x++) {
      blocks.add(platform.world().blockAt(x, 61, 0));
    }
    return blocks;
  }

  private List<BlockRef> layer(int y) {
    List<BlockRef> blocks = new ArrayList<>();
    for (int x = 0; x < 10; x++) {
      for (int z = 0; z < 5; z++) {
        blocks.add(platform.world().blockAt(x + 10, y, z + (y - 61) * 10));
      }
    }
    return blocks;
  }

  private static int placed(List<BlockRef> blocks) {
    int placed = 0;
    for (BlockRef block : blocks) {
      if (!block.isAir()) {
        placed++;
      }
    }
    return placed;
  }

  private int count(Id type) {
    int count = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && !item.isEmpty() && item.type().equals(type)) {
        count += item.amount();
      }
    }
    return count;
  }
}
