package art.arcane.adapt.engine.xp;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.engine.data.WorldDataPort.Placement;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.event.block.BlockFromToEvent;
import art.arcane.adapt.platform.event.block.BlockGrowEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.block.StructureGrowEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class XpProvenanceTest extends AdaptTestBase {
  private static final int TWO_DAYS_SECONDS = 172_800;
  private static final int TEN_SECONDS = 10;

  @Test
  void permanentPlacementEvidenceDoesNotExpireWithTheXpTtl() {
    assertThat(XpProvenance.hasPermanentPlacementRecord(null)).isFalse();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new Placement(0, 0, 0))).isFalse();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new Placement(1, 0, 0))).isTrue();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new Placement(0, 1, 0))).isTrue();
  }

  @Test
  void dragonEggMovementProvenanceRunsAfterCommittedMovement() throws Exception {
    assertCommittedMovementHandler(BlockFromToEvent.class);
    assertCommittedMovementHandler(EntityChangeBlockEvent.class);
  }

  @Test
  void treeGrowthProvenanceRunsAfterTheGrowthIsCommitted() throws Exception {
    assertCommittedMovementHandler(StructureGrowEvent.class);
  }

  @Test
  void blockGrowthProvenanceRunsAfterTheGrowthIsCommitted() throws Exception {
    assertCommittedMovementHandler(BlockGrowEvent.class);
  }

  @Test
  void aBreakForgetsAPlacementWhateverItsAgeAndMarksOnlyRecentPlacementsForReplaceDenial() {
    BlockRef old = block("stone", 1, 64, 0);
    BlockRef recent = block("stone", 2, 64, 0);
    BlockRef natural = block("stone", 3, 64, 0);
    try (PlacementStore placements = new PlacementStore()) {
      placements.put(old, new Placement(nowSeconds() - TWO_DAYS_SECONDS, 0, 7));
      placements.put(recent, new Placement(nowSeconds() - TEN_SECONDS, 0, 0));
      XpProvenanceListener listener = new XpProvenanceListener();

      listener.on(new BlockBreakEvent(mock(PlayerRef.class), old, true, 0));
      listener.on(new BlockBreakEvent(mock(PlayerRef.class), recent, true, 0));
      listener.on(new BlockBreakEvent(mock(PlayerRef.class), natural, true, 0));

      assertThat(XpProvenance.wasPlaced(old)).isFalse();
      assertThat(placements.get(old).brokenAt()).isZero();
      assertThat(placements.get(old).bonemealedAt()).isEqualTo(7);
      assertThat(XpProvenance.wasPlaced(recent)).isFalse();
      assertThat(placements.get(recent).brokenAt()).isPositive();
      assertThat(placements.get(natural)).isNull();
    }
  }

  @Test
  void growingIntoANewBlockForgetsAPlacementButAgeingInPlaceKeepsIt() {
    BlockRef torchflower = block("torchflower_crop", 1, 64, 0);
    BlockRef fruitSpot = block("air", 2, 64, 0);
    BlockRef caneTop = block("air", 3, 65, 0);
    BlockRef wheat = block("wheat", 4, 64, 0);
    try (PlacementStore placements = new PlacementStore()) {
      for (BlockRef placed : List.of(torchflower, fruitSpot, caneTop, wheat)) {
        placements.put(placed, new Placement(nowSeconds() - TWO_DAYS_SECONDS, 0, 0));
      }
      XpProvenanceListener listener = new XpProvenanceListener();

      listener.on(new BlockGrowEvent(torchflower, state("torchflower")));
      listener.on(new BlockGrowEvent(fruitSpot, state("pumpkin")));
      listener.on(new BlockGrowEvent(caneTop, state("sugar_cane")));
      listener.on(new BlockGrowEvent(wheat, state("wheat")));

      assertThat(XpProvenance.wasPlaced(torchflower)).isFalse();
      assertThat(XpProvenance.wasPlaced(fruitSpot)).isFalse();
      assertThat(XpProvenance.wasPlaced(caneTop)).isFalse();
      assertThat(XpProvenance.wasPlaced(wheat)).isTrue();
    }
  }

  @Test
  void aPlacementRecordLastsWhateverItsAgeAndIgnoresBreakMarks() {
    BlockRef unrecorded = block("stone", 1, 64, 0);
    BlockRef broken = block("stone", 2, 64, 0);
    BlockRef ancient = block("stone", 3, 64, 0);
    try (PlacementStore placements = new PlacementStore()) {
      placements.put(broken, new Placement(0, 5, 7));
      placements.put(ancient, new Placement(1, 0, 0));

      assertThat(XpProvenance.wasPlaced(unrecorded)).isFalse();
      assertThat(XpProvenance.wasPlaced(broken)).isFalse();
      assertThat(XpProvenance.wasPlaced(ancient)).isTrue();
    }
  }

  @Test
  void onlyTreePartsAndSaplingsResetOnGrowth() {
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("oak_log"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("oak_wood"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("crimson_stem"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("mushroom_stem"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("warped_hyphae"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("oak_leaves"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("oak_sapling"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("mangrove_propagule"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("nether_wart_block"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("warped_wart_block"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("shroomlight"))).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("podzol"))).isFalse();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("dirt"))).isFalse();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("rooted_dirt"))).isFalse();
    assertThat(XpProvenance.resetsOnGrowth(Id.minecraft("moss_carpet"))).isFalse();
  }

  @Test
  void treeGrowthResetsTreePartsAndTheSaplingSpotButNeverTheGround() {
    BlockChange log = grown("oak_log", 0, 65, 0);
    BlockChange leaves = grown("oak_leaves", 1, 66, 0);
    BlockChange podzol = grown("podzol", 0, 63, 0);
    BlockChange sapling = grown("rooted_dirt", 4, 64, 4);
    StructureGrowEvent event = new StructureGrowEvent(block("oak_sapling", 4, 64, 4), null, false,
        List.of(log, leaves, podzol, sapling));

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.forgetPlacement(any(BlockRef.class))).thenReturn(true);

      new XpProvenanceListener().on(event);

      provenance.verify(() -> XpProvenance.forgetPlacement(log.block()));
      provenance.verify(() -> XpProvenance.forgetPlacement(leaves.block()));
      provenance.verify(() -> XpProvenance.forgetPlacement(sapling.block()));
      provenance.verify(() -> XpProvenance.forgetPlacement(podzol.block()), never());
    }
  }

  @Test
  void forgettingAPlacementKeepsTheBreakAndBonemealMarks() {
    BlockRef unrecorded = block("stone", 1, 64, 0);
    BlockRef broken = block("stone", 2, 64, 0);
    BlockRef placed = block("stone", 3, 64, 0);
    Placement brokenStamp = new Placement(0, 5, 7);
    try (PlacementStore placements = new PlacementStore()) {
      placements.put(broken, brokenStamp);
      placements.put(placed, new Placement(9, 5, 7));

      assertThat(XpProvenance.forgetPlacement(unrecorded)).isFalse();
      assertThat(XpProvenance.forgetPlacement(broken)).isFalse();
      assertThat(XpProvenance.forgetPlacement(placed)).isTrue();

      assertThat(placements.get(unrecorded)).isNull();
      assertThat(placements.get(broken)).isSameAs(brokenStamp);
      assertThat(placements.get(placed).placedAt()).isZero();
      assertThat(placements.get(placed).brokenAt()).isEqualTo(5);
      assertThat(placements.get(placed).bonemealedAt()).isEqualTo(7);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"sand", "red_sand", "gravel", "anvil", "white_concrete_powder", "dragon_egg"})
  void fallingPlacedBlocksRemainIneligibleForBreakXp(String type) {
    BlockRef source = block(type, 1, 70, 0);
    BlockRef destination = block(type, 1, 64, 0);
    EntityRef falling = fallingBlock();
    XpProvenanceListener listener = new XpProvenanceListener();
    try (PlacementStore placements = new PlacementStore()) {
      placements.put(source, new Placement(nowSeconds(), 0, 0));
      listener.on(change(falling, source, "air"));
      assertThat(placements.get(source)).isNull();
      listener.on(change(falling, destination, type));
      assertThat(XpProvenance.isPlayerPlaced(destination)).isTrue();
      assertThat(XpProvenance.breakXpMultiplier(destination)).isZero();
    }
  }

  @Test
  void naturallyFallingBlocksRemainEligibleForBreakXp() {
    BlockRef source = block("sand", 1, 70, 0);
    BlockRef destination = block("sand", 1, 64, 0);
    EntityRef falling = fallingBlock();
    XpProvenanceListener listener = new XpProvenanceListener();
    try (PlacementStore placements = new PlacementStore()) {
      listener.on(change(falling, source, "air"));
      listener.on(change(falling, destination, "sand"));
      assertThat(placements.get(destination)).isNull();
      assertThat(XpProvenance.breakXpMultiplier(destination)).isEqualTo(1D);
    }
  }

  @SuppressWarnings("unchecked")
  private static EntityRef fallingBlock() {
    EntityRef falling = mock(EntityRef.class);
    DataHolder data = mock(DataHolder.class);
    AtomicBoolean placed = new AtomicBoolean();
    when(falling.type()).thenReturn(Id.minecraft("falling_block"));
    when(falling.data()).thenReturn(data);
    doAnswer(invocation -> {
      placed.set(true);
      return null;
    }).when(data).set(any(DataKey.class), anyByte());
    doAnswer(invocation -> {
      placed.set(false);
      return null;
    }).when(data).remove(any(DataKey.class));
    when(data.has(any(DataKey.class))).thenAnswer(invocation -> placed.get());
    return falling;
  }

  private static EntityChangeBlockEvent change(EntityRef falling, BlockRef block, String to) {
    return new EntityChangeBlockEvent(falling, block, state(to));
  }

  private static BlockRef block(String type, int x, int y, int z) {
    BlockRef block = mock(BlockRef.class);
    when(block.type()).thenReturn(Id.minecraft(type));
    when(block.pos()).thenReturn(new BlockPos(x, y, z));
    when(block.x()).thenReturn(x);
    when(block.y()).thenReturn(y);
    when(block.z()).thenReturn(z);
    return block;
  }

  private static BlockStateRef state(String type) {
    BlockStateRef state = mock(BlockStateRef.class);
    when(state.type()).thenReturn(Id.minecraft(type));
    return state;
  }

  private static int nowSeconds() {
    return (int) (System.currentTimeMillis() / 1000L);
  }

  private static BlockChange grown(String type, int x, int y, int z) {
    return new BlockChange(block(type, x, y, z), state(type));
  }

  private static void assertCommittedMovementHandler(Class<?> eventType) throws Exception {
    Method handler = XpProvenanceListener.class.getDeclaredMethod("on", eventType);
    Handle annotation = handler.getAnnotation(Handle.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }
}
